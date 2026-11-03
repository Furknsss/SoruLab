#!/usr/bin/env python3
"""Fail closed if the APK's certificate is not the registered Android OAuth key.

Firebase/Google sign-in is bound to the exact (package, SHA-1) pair. A new
random debug keystore for every Actions run builds an installable APK with a
certificate that Google refuses after the user picks an account.
"""
import base64
import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.aistudio.sorulab.ykslab"


def registered_sha1():
    config = ROOT / "app" / "google-services.json"
    if config.exists():
        data = json.loads(config.read_text(encoding="utf-8"))
    else:
        encoded = ROOT / "google-services.json.base64"
        if not encoded.exists():
            raise RuntimeError("Missing Firebase Android config")
        data = json.loads(base64.b64decode(encoded.read_text()).decode("utf-8"))
    registered = set()
    for client in data.get("client", []):
        android = client.get("client_info", {}).get("android_client_info", {})
        if android.get("package_name") != PACKAGE:
            continue
        for option in client.get("oauth_client", []):
            info = option.get("android_info", {})
            if option.get("client_type") == 1 and info.get("package_name") == PACKAGE:
                sha1 = info.get("certificate_hash", "")
                if len(sha1) == 40:
                    registered.add(sha1.lower())
    if not registered:
        raise RuntimeError("Firebase config has no Android OAuth signing SHA-1")
    return registered


def main():
    keystore = ROOT / "debug.keystore"
    if not keystore.is_file():
        print("::error::No stable debug.keystore", file=sys.stderr)
        return 1
    cert = subprocess.check_output([
        "keytool", "-exportcert", "-alias", "androiddebugkey",
        "-keystore", str(keystore), "-storepass", "android"
    ])
    actual = hashlib.sha1(cert).hexdigest().lower()
    allowed = registered_sha1()
    if actual not in allowed:
        formatted = ":".join(actual[i:i+2].upper() for i in range(0, 40, 2))
        print("::error::Google OAuth signing mismatch for " + PACKAGE +
              ". SHA-1 " + formatted + " is NOT in the Firebase Android OAuth configuration.",
              file=sys.stderr)
        print("Register this stable debug certificate SHA-1 and SHA-256 in Firebase Console, " +
              "download the updated google-services.json, then rebuild. Do NOT re-sign " +
              "an existing installed app with a different key.", file=sys.stderr)
        return 1
    print("Google OAuth signing fingerprint matches Firebase configuration.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
