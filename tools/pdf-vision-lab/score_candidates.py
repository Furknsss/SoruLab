#!/usr/bin/env python3
"""Compare question crop engines against manually reviewed source PDF truth.

JSON input:
{
  "pages": [{
    "page": 9,
    "expected": [{"id": "p9-q1", "number": 1, "bbox": [left,top,right,bottom],
                  "answer": "A", "scope": "Sinir Sistemi|analiz|1"}],
    "candidates": {
      "paddle-vl-1.6": [{"number":1,"bbox":[...],"answer":"A","scope":"..."}],
      "gemini": [...]
    }
  }]
}
Coordinates must be in the same coordinate system, preferably normalized
0..1000. Scoring is FAIL-CLOSED: unmatched, duplicate, or incorrectly scoped
questions are not counted as successful. A high IoU alone does not prove that
diagrams and answer choices survived; use reviewed gold rectangles.
"""
import argparse
import json
from collections import defaultdict

def iou(a,b):
    x0,y0,x1,y1=a; u0,v0,u1,v1=b
    inter=max(0,min(x1,u1)-max(x0,u0))*max(0,min(y1,v1)-max(y0,v0))
    area1=max(0,x1-x0)*max(0,y1-y0)
    area2=max(0,u1-u0)*max(0,v1-v0)
    union=area1+area2-inter
    return inter/union if union else 0.0

def score(pages, threshold):
    totals=defaultdict(lambda: defaultdict(int))
    for page in pages:
        expected=page["expected"]
        for engine, predictions in page["candidates"].items():
            out=totals[engine]
            out["expected"]+=len(expected)
            out["predicted"]+=len(predictions)
            used=set()
            for truth in expected:
                matches=[(idx,p) for idx,p in enumerate(predictions)
                         if p.get("number")==truth["number"]]
                if len(matches)!=1:
                    out["missing_or_ambiguous"]+=1
                    continue
                idx,p=matches[0]
                if idx in used:
                    out["duplicate"]+=1
                    continue
                used.add(idx)
                if iou(truth["bbox"],p["bbox"]) < threshold:
                    out["bad_geometry"]+=1
                    continue
                out["geometry_pass"]+=1
                if p.get("scope") != truth.get("scope"):
                    out["wrong_scope"]+=1
                    continue
                if p.get("answer") != truth.get("answer"):
                    out["wrong_answer"]+=1
                    continue
                out["fully_correct"]+=1
            out["extra"]+=len(predictions)-len(used)
    return {k:dict(v) for k,v in sorted(totals.items())}

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("reviewed_gold_and_predictions")
    parser.add_argument("--iou",type=float,default=0.90)
    args=parser.parse_args()
    with open(args.reviewed_gold_and_predictions,encoding="utf-8") as f:
        data=json.load(f)
    if not 0<args.iou<=1: parser.error("--iou must be in (0,1]")
    result=score(data["pages"],args.iou)
    print(json.dumps(result,indent=2,ensure_ascii=False))
    if not result or any(x["fully_correct"]!=x["expected"] or x["extra"] for x in result.values()):
        raise SystemExit(1)

if __name__=="__main__":
    main()
