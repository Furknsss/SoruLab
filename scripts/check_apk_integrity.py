#!/usr/bin/env python3
"""Check APK bytes before delivery and again after a file-transfer round trip."""
import argparse
import hashlib
from pathlib import Path
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--expected-size", type=int)
    parser.add_argument("--expected-sha256")
    args = parser.parse_args()
    size = args.apk.stat().st_size
    if args.expected_size is not None and size != args.expected_size:
        raise SystemExit(f"Incomplete APK: expected {args.expected_size} bytes, received {size}")
    with args.apk.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    if args.expected_sha256 and digest != args.expected_sha256:
        raise SystemExit("APK SHA-256 differs from the verified signed build")
    try:
        with zipfile.ZipFile(args.apk) as archive:
            names = set(archive.namelist())
            if not {"AndroidManifest.xml", "classes.dex"} <= names:
                raise SystemExit("Not a complete standalone Android APK")
            bad_entry = archive.testzip()
            if bad_entry is not None:
                raise SystemExit(f"Corrupted APK entry: {bad_entry}")
    except zipfile.BadZipFile as error:
        raise SystemExit("Incomplete or invalid APK ZIP directory") from error
    print(f"APK integrity OK: {size} bytes; SHA-256 {digest}")


if __name__ == "__main__":
    main()
