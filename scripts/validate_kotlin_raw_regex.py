#!/usr/bin/env python3
"""Compile literal Kotlin raw-string Regex patterns with Java's regex engine.

Kotlin Regex uses java.util.regex.Pattern on Android. Kotlin/Gradle compilation
alone does not instantiate these patterns. An invalid raw regex previously
crashed PDF import at runtime despite an otherwise successful APK build.
"""
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app" / "src" / "main" / "java"
RAW_REGEX = re.compile(r'\bRegex\s*\(\s*"""(.*?)"""', re.DOTALL)
DYNAMIC = re.compile(r'\$\{|\$[A-Za-z_]')


def collect():
    results = []
    for path in sorted(SOURCE.rglob("*.kt")):
        source = path.read_text(encoding="utf-8")
        for match in RAW_REGEX.finditer(source):
            pattern = match.group(1)
            if DYNAMIC.search(pattern):
                continue  # A runtime interpolation must be checked by a dedicated unit test.
            line = source.count("\n", 0, match.start()) + 1
            results.append((f"{path.relative_to(ROOT)}:{line}", pattern))
    return results


def main():
    entries = collect()
    if not entries:
        print("ERROR: no literal Kotlin raw Regex expressions found", file=sys.stderr)
        return 1
    commands = []
    for source, pattern in entries:
        commands.append(
            "check(" + json.dumps(source, ensure_ascii=False) + ", "
            + json.dumps(pattern, ensure_ascii=False) + ");"
        )
    java_source = """
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
public final class KotlinRawRegexPreflight {
  static int errors = 0;
  static void check(String origin, String expression) {
    try { Pattern.compile(expression); }
    catch (PatternSyntaxException e) {
      System.err.println("INVALID_REGEX " + origin + ": " + e.getDescription());
      errors++;
    }
  }
  public static void main(String[] args) {
""" + "\n".join(commands) + """
    if (errors != 0) throw new AssertionError(errors + " invalid regex patterns");
    System.out.println("Validated " + """ + str(len(entries)) + """ + " Kotlin raw Regex patterns.");
  }
}
"""
    with tempfile.TemporaryDirectory(prefix="sorulab-regex-") as temp:
        src = Path(temp) / "KotlinRawRegexPreflight.java"
        src.write_text(java_source, encoding="utf-8")
        subprocess.run(["javac", "-encoding", "UTF-8", str(src)], check=True)
        return subprocess.run(["java", "-cp", temp, "KotlinRawRegexPreflight"]).returncode


if __name__ == "__main__":
    sys.exit(main())
