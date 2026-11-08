#!/usr/bin/env python3
"""Model-agnostic SoruLab PDF crop benchmark. All coordinates normalized 0..1000."""
import argparse,json
from pathlib import Path
def score(ref,pred):
    output=dict(anchors=0,hits=0,ink_points=0,ink_hits=0,missing_pages=0,bad_pages=0,details=[])
    for name,doc in ref["documents"].items():
        pages={p["index"]:p for p in pred.get("documents",{}).get(name,{}).get("pages",[])}
        for expected in doc["pages"]:
            p=pages.get(expected["index"])
            boxes=p.get("questions",[]) if p else []
            source=expected["printed_numbers"]
            points=expected.get("last_ink_points",[])
            if not p:output["missing_pages"]+=1
            numbers=[x.get("number") for x in boxes]
            ok=bool(p and boxes) and len(numbers)==len(set(numbers))
            for q in boxes:
                l,t,r,b=(q.get(k,-1) for k in ["left","top","right","bottom"])
                if not all(isinstance(x,int) for x in [l,t,r,b]) or not (0<=l<r<=1000 and 0<=t<b<=1000):ok=False
            if ok:
                for i,a in enumerate(boxes):
                    for z in boxes[i+1:]:
                        intersection=max(0,min(a["right"],z["right"])-max(a["left"],z["left"]))*max(0,min(a["bottom"],z["bottom"])-max(a["top"],z["top"]))
                        smallest=min((a["right"]-a["left"])*(a["bottom"]-a["top"]),(z["right"]-z["left"])*(z["bottom"]-z["top"]))
                        if intersection>0.03*smallest:ok=False
            def includes(k):
                return sum(q.get("number")==k["n"] and q["left"]<=k["x"]<=q["right"] and q["top"]<=k["y"]<=q["bottom"] for q in boxes)==1
            hits=sum(includes(x) for x in source) if ok else 0
            ink_hits=sum(includes(x) for x in points) if ok else 0
            if set(numbers)!={x["n"] for x in source} or hits!=len(source) or ink_hits!=len(points):ok=False
            output["bad_pages"]+=not ok
            output["anchors"]+=len(source);output["hits"]+=hits
            output["ink_points"]+=len(points);output["ink_hits"]+=ink_hits
            output["details"].append(dict(document=name,page=expected["index"]+1,accepted=bool(ok),expected=len(source),predicted=len(boxes),number_hits=hits,ink_hits=ink_hits))
    output["accepted"]=output["bad_pages"]==0 and output["missing_pages"]==0 and output["hits"]==output["anchors"] and output["ink_hits"]==output["ink_points"]
    output["anchor_recall"]=round(output["hits"]/max(output["anchors"],1),4)
    output["ink_spot_recall"]=round(output["ink_hits"]/max(output["ink_points"],1),4) if output["ink_points"] else None
    return output
if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--reference",required=True,type=Path)
    p.add_argument("--predictions",required=True,type=Path)
    p.add_argument("--report",type=Path)
    p.add_argument("--strict",action="store_true")
    a=p.parse_args()
    report=score(json.loads(a.reference.read_text()),json.loads(a.predictions.read_text()))
    s=json.dumps(report,ensure_ascii=False,indent=2)
    print(s)
    if a.report:a.report.write_text(s)
    if a.strict and not report["accepted"]:raise SystemExit(2)
