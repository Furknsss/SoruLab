#!/usr/bin/env python3
"""Verify that the existing SoruLab release key matches Firebase Android OAuth.

The preview must be signed with the SAME identity as production. Never use
a randomly generated debug key for user-facing previews.
"""
import hashlib
import os
import subprocess
import sys
from pathlib import Path

from check_debug_oauth_signing import PACKAGE, registered_sha1

ROOT = Path(__file__).resolve().parents[1]


def main():
    keystore = ROOT / "my-upload-key.jks"
    password = os.environ.get("STORE_PASSWORD", "")
    if not password or not keystore.is_file():
        print("::error::Stable release signing key or STORE_PASSWORD is missing.", file=sys.stderr)
        return 1
    cert = subprocess.check_output([
        "keytool", "-exportcert", "-alias", "upload",
        "-keystore", str(keystore), "-storepass", password
    ])
    actual = hashlib.sha1(cert).hexdigest().lower()
    allowed = registered_sha1()
    if actual not in allowed:
        printable = ":".join(actual[i:i + 2].upper() for i in range(0, 40, 2))
        print("::error::Release signing certificate " + printable +
              " is not registered for the Firebase Android app " + PACKAGE,
              file=sys.stderr)
        return 1
    print("Release signing identity matches Firebase's Android OAuth configuration.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
