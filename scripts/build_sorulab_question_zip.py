#!/usr/bin/env python3
"""Build a verified SoruLab v1 image-question ZIP from prepared crops.

This script NEVER guesses answer keys or difficulty. It verifies that every
referenced image exists, computes SHA-256, and writes the canonical manifest.
Example:
python3 scripts/build_sorulab_question_zip.py \
  --manifest /work/manifest.json --images-root /work --output /work/Sorular.zip
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

IMAGE = re.compile(r"^images/[a-zA-Z0-9_-]{1,100}\.(png|jpg|jpeg|webp)$")
COVER = re.compile(r"^cover\.(png|jpg|jpeg|webp)$")
BOOK_ID = re.compile(r"^[a-zA-Z0-9_-]{4,100}$")
MAX_QUESTIONS = 6000


def file_sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def build(source_manifest, root, output):
    manifest = json.loads(source_manifest.read_text(encoding="utf-8"))
    if not isinstance(manifest, dict):
        raise ValueError("manifest must be a JSON object")
    if manifest.get("format") != "sorulab.question-pack" or manifest.get("version") != 1:
        raise ValueError("format and version must be sorulab.question-pack / 1")
    book_id = manifest.get("bookId", "")
    if not BOOK_ID.fullmatch(book_id):
        raise ValueError("bookId is missing or unsafe")
    if manifest.get("examType") not in ("TYT", "AYT") or not manifest.get("subject"):
        raise ValueError("examType/subject missing")
    if not (2 <= len(manifest.get("bookTitle", "")) <= 160):
        raise ValueError("bookTitle missing")
    questions = manifest.get("questions")
    if not isinstance(questions, list) or not 1 <= len(questions) <= MAX_QUESTIONS:
        raise ValueError("question list is empty or oversized")
    if manifest.get("questionCount", len(questions)) != len(questions):
        raise ValueError("questionCount does not match questions")
    manifest["questionCount"] = len(questions)

    images = {}
    identities = set()
    for index, q in enumerate(questions, start=1):
        if not isinstance(q, dict):
            raise ValueError("invalid question at index " + str(index))
        image = q.get("image", "")
        if not IMAGE.fullmatch(image) or image in images:
            raise ValueError("duplicate or invalid image path: " + image)
        page = q.get("pageIndex")
        number = q.get("questionNumberInTest")
        test = q.get("sourceTestId", "")
        if (not isinstance(page, int) or not 0 <= page <= 9999
            or not isinstance(number, int) or not 1 <= number <= 9999
            or not isinstance(test, str) or not 1 <= len(test) <= 160):
            raise ValueError("invalid source identity at item " + str(index))
        identity = (page, test, number)
        if identity in identities:
            raise ValueError("duplicate source question identity")
        identities.add(identity)
        answer = str(q.get("answer", "")).strip().upper()
        if answer not in ("", "A", "B", "C", "D", "E"):
            raise ValueError("invalid answer at item " + str(index))
        if not isinstance(q.get("answerVerified", False), bool):
            raise ValueError("answerVerified must be a boolean")
        if q.get("answerVerified") and not answer:
            raise ValueError("verified answer cannot be empty")
        if not q.get("answerVerified"):
            answer = ""  # Unverified guesses must never become a displayed key.
        q["answer"] = answer
        q["answerVerified"] = bool(q.get("answerVerified", False))
        if "bounds" in q:
            box = q["bounds"]
            if (not isinstance(box, list) or len(box) != 4
                or any(not isinstance(v, (int, float)) or not 0 <= v <= 1 for v in box)
                or not (box[0] < box[2] and box[1] < box[3])):
                raise ValueError("invalid bounds for item " + str(index))
        images[image] = q

    cover = manifest.get("coverImage", "")
    if cover:
        if not isinstance(cover, str) or not COVER.fullmatch(cover):
            raise ValueError("coverImage must be cover.jpg/png/webp")
        images[cover] = manifest

    root = root.resolve()
    payload = {}
    for name, item in images.items():
        path = (root / name).resolve()
        if root not in path.parents:
            raise ValueError("image escapes images-root")
        if not path.is_file():
            raise ValueError("missing image: " + name)
        size = path.stat().st_size
        if not 1 <= size <= 12 * 1024 * 1024:
            raise ValueError("invalid image file size: " + name)
        sha = file_sha256(path)
        item["coverSha256" if name == cover else "sha256"] = sha
        payload[name] = path

    json_content = json.dumps(manifest, ensure_ascii=False, indent=2).encode("utf-8")
    if len(json_content) > 7 * 1024 * 1024:
        raise ValueError("manifest is too large")
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        archive.writestr("manifest.json", json_content)
        for name, path in payload.items():
            archive.write(path, arcname=name)
    if output.stat().st_size > 300 * 1024 * 1024:
        output.unlink(missing_ok=True)
        raise ValueError("ZIP exceeds 300 MiB")
    verified = sum(bool(q["answerVerified"]) for q in questions)
    print("Created:", output)
    print("Questions:", len(questions), "Verified answers:", verified,
          "Unresolved answers:", len(questions) - verified)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--images-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    build(args.manifest, args.images_root, args.output)


if __name__ == "__main__":
    main()
