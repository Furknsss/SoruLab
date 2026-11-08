#!/usr/bin/env python3
"""REAL local PaddleOCR-VL-1.6 inference smoke; sends NO publisher material.

Run only after starting the official PaddleOCR HPS GPU service.
Checks /health/ready and invokes /layout-parsing against a self-authored PNG.
Do not call this proof of question-boundary accuracy.
"""
import argparse
import base64
import json
import struct
import sys
import urllib.request
import zlib

# Hand-coded 5x7 synthetic glyphs, no system fonts/Pillow required.
GLYPHS = {
    "T": ("11111","00100","00100","00100","00100","00100","00100"),
    "E": ("11111","10000","10000","11110","10000","10000","11111"),
    "S": ("01111","10000","10000","01110","00001","00001","11110"),
    "A": ("01110","10001","10001","11111","10001","10001","10001"),
    "B": ("11110","10001","10001","11110","10001","10001","11110"),
    "1": ("00100","01100","00100","00100","00100","00100","01110"),
    "2": ("01110","10001","00001","00010","00100","01000","11111"),
    ".": ("00000","00000","00000","00000","00000","01100","01100"),
}
def demo_png():
    width, height, scale = 640, 400, 7
    rows = [bytearray([255,255,255] * width) for _ in range(height)]
    def glyph(x, y, char):
        for yi, bits in enumerate(GLYPHS.get(char, ())):
            for xi, value in enumerate(bits):
                if value == "1":
                    for oy in range(scale):
                        for ox in range(scale):
                            py, px = y + yi*scale + oy, x + xi*scale + ox
                            if 0 <= px < width and 0 <= py < height:
                                k=px*3
                                rows[py][k:k+3] = b"\x00\x00\x00"
    for label, yy in [("TEST 1", 45), ("1. A B", 160), ("2. A B", 265)]:
        x=65
        for char in label:
            glyph(x, yy, char)
            x += 7*scale
    raw=b"".join(b"\x00"+bytes(row) for row in rows)
    def chunk(kind, data):
        return struct.pack(">I", len(data))+kind+data+struct.pack(">I",zlib.crc32(kind+data)&0xFFFFFFFF)
    return b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",width,height,8,2,0,0,0))+chunk(b"IDAT",zlib.compress(raw))+chunk(b"IEND",b"")

def validate_live_response(payload):
    """Official HPS JSON schema; do not accept arbitrary HTTP 200 bodies."""
    result = payload["result"]["layoutParsingResults"]
    if not isinstance(result,list) or len(result)!=1:
        raise ValueError("Expected exactly one rendered page")
    pruned=result[0]["prunedResult"]
    if not isinstance(pruned,dict) or not isinstance(pruned.get("parsing_res_list"),list):
        raise ValueError("Missing PaddleOCR-VL prunedResult.parsing_res_list")
    for block in pruned["parsing_res_list"]:
        bbox=block.get("block_bbox")
        if not isinstance(bbox,list) or len(bbox)!=4 or any(
            not isinstance(v,(int,float)) for v in bbox
        ) or not isinstance(block.get("block_label"),str):
            raise ValueError("Invalid layout block geometry/label")
    return len(pruned["parsing_res_list"])

def run(base_url):
    base=base_url.rstrip("/")
    if not base.startswith("http://127.0.0.1:") and not base.startswith("http://localhost:"):
        raise ValueError("Only local unexposed PaddleOCR gateway is allowed by this test")
    with urllib.request.urlopen(base+"/health/ready",timeout=10) as response:
        if response.status!=200: raise ValueError("Paddle HPS readiness failed")
    body=json.dumps({"file":base64.b64encode(demo_png()).decode("ascii"),"fileType":1})
    request=urllib.request.Request(
        base+"/layout-parsing",data=body.encode("utf-8"),method="POST",
        headers={"Content-Type":"application/json"}
    )
    with urllib.request.urlopen(request,timeout=300) as response:
        if response.status!=200: raise ValueError("Paddle inference HTTP failed")
        raw=response.read(48*1024*1024+1)
    if len(raw)>48*1024*1024: raise ValueError("Paddle inference response was too large")
    parsed=json.loads(raw)
    blocks=validate_live_response(parsed)
    print("PaddleOCR-VL-1.6 inference OK: 1 synthetic page, %d parsed layout blocks" % blocks)

if __name__ == "__main__":
    ap=argparse.ArgumentParser()
    ap.add_argument("--url", default="http://127.0.0.1:8080")
    args=ap.parse_args()
    try: run(args.url)
    except Exception as exc:
        print("PADDLE INFERENCE NOT VERIFIED: %s" % exc,file=sys.stderr)
        sys.exit(1)
