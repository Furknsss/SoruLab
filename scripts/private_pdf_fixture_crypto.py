#!/usr/bin/env python3
"""Per-run encrypted private PDF QA transport. Never prints keys or source text."""
import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from urllib.parse import quote
import zipfile

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def key_id(public_bytes):
    return hashlib.sha256(public_bytes).hexdigest()


def generate(private_path, public_dir):
    private_path.parent.mkdir(parents=True, exist_ok=True)
    public_dir.mkdir(parents=True, exist_ok=True)
    key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    with os.fdopen(os.open(private_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "wb") as stream:
        stream.write(key.private_bytes(serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    public = key.public_key().public_bytes(serialization.Encoding.PEM,
        serialization.PublicFormat.SubjectPublicKeyInfo)
    (public_dir / "public-key.pem").write_bytes(public)
    (public_dir / "key-id.txt").write_text(key_id(public))
    print("Public fixture key ready:", key_id(public))


def encrypt(public_path, input_zip, output_dir):
    public_bytes = public_path.read_bytes()
    public = serialization.load_pem_public_key(public_bytes)
    label = key_id(public_bytes)
    secret, nonce = os.urandom(32), os.urandom(12)
    plain = input_zip.read_bytes()
    if len(plain) > 45_000_000:
        raise SystemExit("Use representative private pages, not a full public book upload")
    wrapped = public.encrypt(secret, padding.OAEP(mgf=padding.MGF1(hashes.SHA256()),
        algorithm=hashes.SHA256(), label=None))
    encrypted = AESGCM(secret).encrypt(nonce, plain, label.encode())
    payload = {"key_id": label, "wrapped_key": base64.b64encode(wrapped).decode(),
        "nonce": base64.b64encode(nonce).decode(),
        "ciphertext": base64.b64encode(encrypted).decode()}
    output_dir.mkdir(parents=True, exist_ok=True)
    target = output_dir / (label + ".json")
    target.write_text(json.dumps(payload, separators=(",", ":")))
    print("Encrypted fixture ready:", target.name, "bytes:", target.stat().st_size)


def receive(private_path, public_path, destination, repository, branch):
    public_bytes = public_path.read_bytes()
    label = key_id(public_bytes)
    # The repository is PRIVATE: unauthenticated raw.githubusercontent.com
    # returns 404 even for a valid file. Use a read-only Actions token via
    # GitHub's official Contents API; never include the token in logs/URLs.
    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        raise SystemExit("Private QA transport requires GITHUB_TOKEN")
    url = (f"https://api.github.com/repos/{repository}/contents/"
        f".qa-encrypted/{label}.json?ref={quote(branch, safe='')}")
    deadline = time.monotonic() + 1200
    payload = None
    while time.monotonic() < deadline:
        try:
            request = Request(url, headers={
                "Authorization": f"Bearer {token}",
                "Accept": "application/vnd.github+json",
                "User-Agent": "SoruLab-private-fixture-acceptance",
            })
            with urlopen(request, timeout=25) as response:
                metadata = json.load(response)
            payload = json.loads(base64.b64decode(metadata["content"]))
            break
        except HTTPError as error:
            if error.code != 404:
                raise
        time.sleep(5)
    if payload is None:
        raise SystemExit("No encrypted fixture arrived for this ephemeral QA key")
    if payload["key_id"] != label:
        raise SystemExit("Fixture key identity mismatch")
    private = serialization.load_pem_private_key(private_path.read_bytes(), password=None)
    secret = private.decrypt(base64.b64decode(payload["wrapped_key"]),
        padding.OAEP(mgf=padding.MGF1(hashes.SHA256()), algorithm=hashes.SHA256(), label=None))
    plain = AESGCM(secret).decrypt(base64.b64decode(payload["nonce"]),
        base64.b64decode(payload["ciphertext"]), label.encode())
    destination.mkdir(parents=True, exist_ok=True)
    page_pair = {"native.pdf", "scanned.pdf", "reference.json"}
    full_book = {"full.pdf", "full-reference.json"}
    with zipfile.ZipFile(io.BytesIO(plain)) as archive:
        members = set(archive.namelist())
        if members not in (page_pair, full_book):
            raise SystemExit("Unexpected private fixture names")
        for name in members:
            data = archive.read(name)
            if len(data) > 45_000_000:
                raise SystemExit("Private fixture too large")
            (destination / name).write_bytes(data)
    print("Private source fixture received; plaintext is excluded from artifacts.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest="command", required=True)
    gen = subs.add_parser("generate")
    gen.add_argument("private_path", type=Path)
    gen.add_argument("public_dir", type=Path)
    enc = subs.add_parser("encrypt")
    enc.add_argument("public_path", type=Path)
    enc.add_argument("input_zip", type=Path)
    enc.add_argument("output_dir", type=Path)
    rec = subs.add_parser("receive")
    rec.add_argument("private_path", type=Path)
    rec.add_argument("public_path", type=Path)
    rec.add_argument("destination", type=Path)
    rec.add_argument("repository")
    rec.add_argument("branch")
    args = vars(parser.parse_args())
    command = args.pop("command")
    {"generate": generate, "encrypt": encrypt, "receive": receive}[command](**args)


if __name__ == "__main__":
    main()
