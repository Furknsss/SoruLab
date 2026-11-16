import copy
import datetime
import pathlib
import subprocess
import sys
import unittest

from import_lesson_chapters import make_candidates, parse_chapters, seconds


class PublisherChapterImportTest(unittest.TestCase):
    # Synthetic fixtures only. These IDs/labels are never production content.
    text = "Publisher introduction\n00:00 Introduction\n02:30 Exact skill\n06:20 Next chapter\n"
    outcomes = [dict(subjectId="tyt_fixture", topicId="topic", id="skill", name="Test skill", phrases=[])]
    mapping = dict(videoId="TESTVIDEO01", sourceUrl="https://www.youtube.com/watch?v=TESTVIDEO01",
        teacher="Fixture publisher", title="Synthetic fixture", durationSeconds=600,
        mappings=[dict(subjectId="tyt_fixture", topicId="topic", outcomeId="skill",
                       chapterLabel="Exact skill")])

    def make(self, text=None, mapping=None, date="2026-11-16"):
        return make_candidates(text or self.text, mapping or self.mapping, self.outcomes,
                               date, today=datetime.date(2026, 11, 16))

    def test_exact_publisher_label_produces_start_and_next_chapter_end(self):
        result = self.make()
        video = result["entries"][0]["video"]
        self.assertEqual((150, 380), (video["startSeconds"], video["endSeconds"]))
        self.assertTrue(result["reviewRequired"])
        self.assertEqual(64, len(result["provenance"]["sourceTextSha256"]))
        self.assertEqual([], result["entries"][0]["phrases"])

    def test_unmatched_or_ambiguous_label_never_guesses_a_timestamp(self):
        for label in ("Similar skill", "exact skill"):
            mapping = copy.deepcopy(self.mapping)
            mapping["mappings"][0]["chapterLabel"] = label
            with self.assertRaises(ValueError):
                self.make(mapping=mapping)
        with self.assertRaises(ValueError):
            self.make(text=self.text + "08:00 Exact skill\n")

    def test_wrong_exam_topic_or_outcome_is_rejected(self):
        for field in ("subjectId", "topicId", "outcomeId"):
            mapping = copy.deepcopy(self.mapping)
            mapping["mappings"][0][field] = "wrong"
            with self.assertRaises(ValueError):
                self.make(mapping=mapping)

    def test_invalid_timestamps_or_chapter_order_are_rejected(self):
        for text in ("00:01 Intro\n01:00 A\n02:00 B",
                     "00:00 Intro\n00:09 A\n02:00 B",
                     "00:00 Intro\n02:00 A\n01:00 B",
                     "00:00 Intro\n01:70 A\n02:00 B"):
            with self.assertRaises(ValueError):
                parse_chapters(text)

    def test_hours_and_unknown_last_end_are_preserved_without_invention(self):
        self.assertEqual(8407, seconds("2:20:07"))
        mapping = copy.deepcopy(self.mapping)
        mapping.pop("durationSeconds")
        mapping["mappings"][0]["chapterLabel"] = "Next chapter"
        self.assertIsNone(self.make(mapping=mapping)["entries"][0]["video"]["endSeconds"])

    def test_mismatched_source_future_review_and_invalid_duration_are_rejected(self):
        mapping = copy.deepcopy(self.mapping)
        mapping["sourceUrl"] += "wrong"
        with self.assertRaises(ValueError):
            self.make(mapping=mapping)
        with self.assertRaises(ValueError):
            self.make(date="2026-11-17")
        for duration in (380, 385, True, "600"):
            mapping = copy.deepcopy(self.mapping)
            mapping["durationSeconds"] = duration
            with self.assertRaises(ValueError):
                self.make(mapping=mapping)

    def test_repeated_mapping_is_rejected(self):
        mapping = copy.deepcopy(self.mapping)
        mapping["mappings"] *= 2
        with self.assertRaises(ValueError):
            self.make(mapping=mapping)

    def test_malformed_timestamp_line_cannot_silently_change_chapter_boundaries(self):
        with self.assertRaises(ValueError):
            parse_chapters(self.text + "08:7 malformed")

    def test_cli_refuses_to_overwrite_production_catalog_before_reading_inputs(self):
        root = pathlib.Path(__file__).resolve().parents[1]
        target = root / "content/lessons.json"
        before = target.read_bytes()
        result = subprocess.run([sys.executable, str(root / "tools/import_lesson_chapters.py"),
            "--chapters", "does-not-exist", "--mapping", "does-not-exist",
            "--reviewed-on", "2026-11-16", "--output", str(target)], capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("production metadata", result.stderr)
        self.assertEqual(before, target.read_bytes())


if __name__ == "__main__":
    unittest.main()
