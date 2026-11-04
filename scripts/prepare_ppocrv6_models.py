#!/usr/bin/env python3
"""Download official Apache-2.0 PP-OCRv6_small ONNX assets locally.
Build fails if download is unavailable; no paid endpoint or hidden model
fallback. Never store private PDF content in the model cache.
"""
from pathlib import Path
from urllib.request import Request, urlopen
import hashlib

root=Path(__file__).resolve().parents[1]
target=root/"ppocr-sdk/src/main/assets/models"
assets=[
 ("PaddlePaddle/PP-OCRv6_small_det_onnx","inference.onnx","det/inference.onnx",1_000_000,60_000_000),
 ("PaddlePaddle/PP-OCRv6_small_rec_onnx","inference.onnx","rec/inference.onnx",1_000_000,70_000_000),
 ("PaddlePaddle/PP-OCRv6_small_rec_onnx","inference.yml","rec/inference.yml",1_000,3_000_000)
]
for repo,name,relative,minimum,maximum in assets:
    path=target/relative
    path.parent.mkdir(parents=True,exist_ok=True)
    if path.is_file() and minimum<=path.stat().st_size<=maximum:
        print("Reusing PP-OCRv6 asset",relative,path.stat().st_size)
        continue
    url="https://huggingface.co/"+repo+"/resolve/main/"+name+"?download=true"
    temporary=path.with_suffix(path.suffix+".download")
    try:
        h=hashlib.sha256()
        n=0
        request=Request(url,headers={"User-Agent":"SoruLab-PP-OCRv6-Android/1.0"})
        with urlopen(request,timeout=120) as response,open(temporary,"wb") as out:
            while True:
                chunk=response.read(1024*1024)
                if not chunk: break
                n+=len(chunk)
                if n>maximum: raise RuntimeError("Unexpected model size: "+relative)
                h.update(chunk)
                out.write(chunk)
        if n<minimum: raise RuntimeError("Empty/partial model: "+relative)
        temporary.replace(path)
        print("Downloaded PP-OCRv6",relative,n,"sha256",h.hexdigest())
    finally:
        temporary.unlink(missing_ok=True)
print("PP-OCRv6_small ready for on-device offline inference (no GPU server)")
