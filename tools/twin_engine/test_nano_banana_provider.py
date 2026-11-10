"""Regression tests for image provider opt-in, safe bytes and visual auditing."""
import base64
import io
import os
import unittest
from unittest.mock import patch

from PIL import Image

import app
from math_gate import VerificationError
from nano_banana_provider import configured, normalize_illustration


def tiny_science_image():
    out = io.BytesIO()
    picture = Image.new("RGB", (700, 500), "white")
    picture.save(out, format="PNG")
    return base64.b64encode(out.getvalue()).decode("ascii")


class NanoBananaSafetyTest(unittest.TestCase):
    def test_provider_is_off_without_explicit_opt_in(self):
        with patch.dict(os.environ, {
            "SORULAB_NANO_BANANA_ENABLE": "0",
            "GEMINI_API_KEY": "test-never-sent",
        }):
            self.assertFalse(configured())
        with patch.dict(os.environ, {
            "SORULAB_NANO_BANANA_ENABLE": "1",
            "SORULAB_IMAGE_MODEL": "unreviewed-costly-model",
            "GEMINI_API_KEY": "test-never-sent",
        }):
            self.assertFalse(configured())

    def test_generated_image_recompressed_and_validated(self):
        normalized = normalize_illustration(tiny_science_image())
        raw = base64.b64decode(normalized)
        self.assertTrue(raw.startswith(b"\xff\xd8"))
        self.assertLess(len(raw), 1_100_000)
        with Image.open(io.BytesIO(raw)) as img:
            self.assertEqual(img.size, (700, 500))

    def test_invalid_and_malformed_image_rejected(self):
        for raw in ("evil", base64.b64encode(b"<script>hi</script>").decode()):
            with self.assertRaises(VerificationError):
                normalize_illustration(raw)

    def test_illustration_sends_pixels_to_both_blind_verifiers(self):
        from test_service import SOURCE, DRAFT, REVIEW, question
        illustrated_source = {
            **SOURCE,
            "diagramFamily": "illustration",
        }
        draft = {
            **DRAFT,
            "answerMode": "conceptual",
            "answerExpression": "",
            "options": {
                "A": "Kütikula tabakası",
                "B": "Ksilem dokusu",
                "C": "Kloroplast granaları",
                "D": "Stoma hücreleri",
                "E": "Floem iletim demetleri",
            },
            "illustrationPrompt": (
                "Cross-section of a plant leaf with a labelled upper epidermis, "
                "mesophyll, stomata and arrows indicating gas transport."
            ),
            "requiredVisualLabels": "epidermis, mesophyll, stomata, gas arrow",
        }
        reviewer = {
            **REVIEW,
            "independentAnswerExpression": "",
        }
        image = normalize_illustration(tiny_science_image())
        with patch.object(app, "image_provider_available", return_value=True), \
             patch.object(app, "generate_science_illustration", return_value=image) as generator, \
             patch.object(app, "ask", side_effect=[
                 illustrated_source, draft, reviewer, reviewer
             ]) as model:
            result = app.generate_twin(
                question(subject="AYT Biyoloji", topic="Bitkilerde Gaz Alışverişi")
            )
        self.assertEqual(result["imageBase64"], image)
        self.assertEqual(result["diagramJson"], "")
        self.assertEqual(model.call_count, 4)
        self.assertEqual(model.call_args_list[2].args[1], image)
        self.assertEqual(model.call_args_list[3].args[1], image)
        generator.assert_called_once()

    def test_image_unavailable_fails_before_any_paid_call(self):
        from test_service import SOURCE, question
        illustrated_source = {**SOURCE, "diagramFamily": "illustration"}
        with patch.object(app, "image_provider_available", return_value=False), \
             patch.object(app, "ask", side_effect=[illustrated_source]) as llm, \
             patch.object(app, "generate_science_illustration") as generator:
            with self.assertRaises(app.HTTPException) as err:
                app.generate_twin(question())
        self.assertEqual(err.exception.status_code, 422)
        self.assertEqual(llm.call_count, 1)
        generator.assert_not_called()


if __name__ == "__main__":
    unittest.main()
