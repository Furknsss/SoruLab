"""Live smoke of a *small* Qwen vision model, not a Qwen3.8 27B quality certification.

The model must respond through Ollama's real /api/chat with a JSON object;
one image is attached to exercise the multimodal connector. A full
source/draft/reviewer benchmark is independent of this connectivity smoke.
"""
from __future__ import annotations

import base64
import os
import struct
import zlib

from app import MODEL, ask


def png_diagonal(size=48) -> str:
    pixels = []
    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            black = abs(x - y) < 2
            row.extend((0, 0, 0) if black else (255, 255, 255))
        pixels.append(bytes(row))
    data = zlib.compress(b"".join(pixels))
    def chunk(name: bytes, body: bytes) -> bytes:
        return struct.pack(">I", len(body)) + name + body + struct.pack(
            ">I", zlib.crc32(name + body) & 0xFFFFFFFF
        )
    raw = (b"\x89PNG\r\n\x1a\n" +
        chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)) +
        chunk(b"IDAT", data) + chunk(b"IEND", b""))
    return base64.b64encode(raw).decode("ascii")


if __name__ == "__main__":
    assert MODEL == os.environ.get("OLLAMA_TWIN_MODEL")
    response = ask(
        'Return ONLY a JSON object with the exact key "ready" and boolean '
        'value true. This image is input testing, not a mathematical proof.',
        png_diagonal()
    )
    if response.get("ready") is not True:
        raise SystemExit("Ollama vision model did not produce expected JSON")
    print("Real Ollama Qwen multimodal JSON request: OK (" + MODEL + ")")
