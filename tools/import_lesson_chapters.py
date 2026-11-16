#!/usr/bin/env python3
"""Make review candidates from publisher chapter text, never guess timestamps.

No network, API keys, transcript scraping, or production-catalog writes.
Input text must be supplied legitimately by the publisher or a reviewer.
"""
import argparse
import datetime
import hashlib
import json
import pathlib
import re

STAMP = re.compile(r"^\s*((?:\d{1,3}:)?\d{1,2}:\d{2})\s*(?:[-–—|]\s*)?(.+?)\s*$")
VIDEO_ID = re.compile(r"[A-Za-z0-9_-]{11}")


def seconds(stamp):
    parts = [int(p) for p in stamp.split(":")]
    if len(parts) not in (2, 3) or any(p >= 60 for p in parts[1:]):
        raise ValueError("Invalid publisher timestamp: " + stamp)
    if len(parts) == 2:
        return parts[0] * 60 + parts[1]
    return parts[0] * 3600 + parts[1] * 60 + parts[2]


def parse_chapters(text, duration=None):
    chapters = []
    for line in text.splitlines():
        match = STAMP.fullmatch(line)
        if match:
            chapters.append((seconds(match[1]), match[2].strip()))
        elif re.match(r"^\s*\d{1,3}:", line):
            raise ValueError("Malformed publisher chapter line: " + line)
    if len(chapters) < 3 or chapters[0][0] != 0:
        raise ValueError("Need at least three publisher chapters beginning at 00:00")
    if any(b[0] - a[0] < 10 for a, b in zip(chapters, chapters[1:])):
        raise ValueError("Publisher chapters must increase and be at least ten seconds apart")
    if duration is not None:
        if type(duration) is not int or duration <= chapters[-1][0]:
            raise ValueError("Duration must exceed the final publisher timestamp")
        if duration - chapters[-1][0] < 10:
            raise ValueError("Final chapter is shorter than ten seconds")
    return chapters


def make_candidates(text, mapping, outcomes, reviewed_on, today=None):
    today = today or datetime.date.today()
    reviewed = datetime.date.fromisoformat(reviewed_on)
    if reviewed > today:
        raise ValueError("Review date cannot be in the future")
    video_id = mapping["videoId"]
    if not VIDEO_ID.fullmatch(video_id):
        raise ValueError("Invalid video ID")
    canonical = "https://www.youtube.com/watch?v=" + video_id
    if mapping["sourceUrl"] != canonical:
        raise ValueError("Publisher URL and video ID disagree")
    if not mapping["teacher"].strip() or not mapping["title"].strip():
        raise ValueError("Reviewer must identify the publisher and video")
    chapters = parse_chapters(text, mapping.get("durationSeconds"))
    registry = {(o["subjectId"], o["topicId"], o["id"]): o for o in outcomes}
    entries, used = [], set()
    for item in mapping["mappings"]:
        key = (item["subjectId"], item["topicId"], item["outcomeId"])
        if key not in registry:
            raise ValueError("Unregistered or wrong-scope outcome: " + repr(key))
        matches = [(i, start) for i, (start, label) in enumerate(chapters)
                   if label == item["chapterLabel"]]
        if len(matches) != 1:
            raise ValueError("Chapter label must match exactly once: " + item["chapterLabel"])
        index, start = matches[0]
        identity = (key[0], key[1], start)
        if identity in used:
            raise ValueError("Duplicate mapping for the same topic/chapter")
        used.add(identity)
        end = chapters[index + 1][0] if index + 1 < len(chapters) else mapping.get("durationSeconds")
        outcome = registry[key]
        entries.append(dict(subjectId=key[0], topicId=key[1], phrases=[],
            video=dict(teacher=mapping["teacher"].strip(), title=mapping["title"].strip(),
                videoId=video_id, startSeconds=start, endSeconds=end,
                detail=outcome["name"], sourceOfTimestamp="Yayıncı bölüm başlığı: " + item["chapterLabel"],
                sourceUrl=canonical, reviewedOn=reviewed_on, outcomeId=key[2])))
    if not entries:
        raise ValueError("No reviewed outcome mappings supplied")
    return dict(schemaVersion=1, reviewRequired=True, entries=entries,
        provenance=dict(kind="reviewer-supplied-publisher-chapters",
            sourceUrl=canonical, sourceTextSha256=hashlib.sha256(text.encode("utf-8")).hexdigest(),
            reviewedOn=reviewed_on, mappedChapters=len(entries)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--chapters", type=pathlib.Path, required=True)
    parser.add_argument("--mapping", type=pathlib.Path, required=True)
    parser.add_argument("--outcomes", type=pathlib.Path, default=pathlib.Path("content/outcomes.json"))
    parser.add_argument("--reviewed-on", required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    root = pathlib.Path(__file__).resolve().parents[1]
    protected = [root / "content/lessons.json", root / "content/outcomes.json", root / "content/coverage.json"]
    if (args.output.resolve() in [p.resolve() for p in protected]
            or args.output.resolve().is_relative_to((root / "app").resolve())):
        parser.error("Write a review candidate file; production metadata must be reviewed separately")
    result = make_candidates(args.chapters.read_text(encoding="utf-8"),
        json.loads(args.mapping.read_text(encoding="utf-8")),
        json.loads(args.outcomes.read_text(encoding="utf-8"))["outcomes"], args.reviewed_on)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(str(len(result["entries"])) + " review candidates; production catalog unchanged.")


if __name__ == "__main__":
    main()
