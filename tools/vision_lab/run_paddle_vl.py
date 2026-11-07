#!/usr/bin/env python3
"""PaddleOCR-VL-1.6 local benchmark: publisher PDFs stay on this machine."""
import argparse,json
from pathlib import Path
def select(raw,count):
 out=set()
 for part in raw.split(","):
  if "-" in part:
   start,end=[int(x) for x in part.split("-",1)]
   out.update(range(start-1,end))
  else:out.add(int(part)-1)
 if not out or min(out)<0 or max(out)>=count:raise ValueError("Invalid 1-based PDF page range")
 return sorted(out)
if __name__=="__main__":
 ap=argparse.ArgumentParser()
 ap.add_argument("--pdf",required=True,type=Path)
 ap.add_argument("--pages",default="4,5",help="1-based page numbers; e.g. 4,5 or 10-20")
 ap.add_argument("--out",type=Path,default=Path("private-vision-results"))
 ap.add_argument("--device",choices=["cpu","gpu"],default="cpu")
 opts=ap.parse_args()
 import fitz
 from paddleocr import PaddleOCRVL
 opts.out.mkdir(parents=True,exist_ok=True)
 engine=PaddleOCRVL(pipeline_version="v1.6",device=opts.device)
 with fitz.open(opts.pdf) as pdf:
  for index in select(opts.pages,len(pdf)):
   page=pdf[index];scale=min(2,1600/page.rect.width)
   img=opts.out/("page_%04d.png"%(index+1))
   page.get_pixmap(matrix=fitz.Matrix(scale,scale),alpha=False).save(img)
   dest=opts.out/("page_%04d"%(index+1));dest.mkdir(exist_ok=True)
   output=list(engine.predict(str(img)))
   for result in output:result.save_to_json(save_path=dest)
   print("PaddleOCR-VL-1.6 page",index+1,"raw layouts",len(output))
 (opts.out/"MODEL_USED.txt").write_text("PaddleOCR-VL-1.6 raw document layout. NOT independently verified YKS question boxes.\n")
