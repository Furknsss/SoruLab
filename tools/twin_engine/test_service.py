"""Service-level acceptance/rejection tests with a controlled fake Ollama responder.

These tests cover the entire request -> two independent calculations -> accepted
response / rejection path. They are NOT evidence that a real Qwen model generated
a high-quality complex YKS question.
"""
import unittest
from unittest.mock import patch

from fastapi import HTTPException
import app


SOURCE = {
    "transcript": "Bir f(x)=2x+3 fonksiyonu için f(4) değeri kaçtır? A)9 B)10 C)11 D)12 E)13",
    "subSkill": "Doğrusal fonksiyonda yerine koyma",
    "exactQuestionPattern": "ax+b biçimindeki fonksiyonda verilen x için f(x) hesaplama",
    "orderedSolutionOperations": "verilen x sayısını ax+b ifadesine yerleştir, çarp, sabiti topla",
    "independentlySolvedOriginalOption": "C",
    "legible": "YES",
    "diagramFamily": "none",
}
DRAFT = {
    "questionStem": "Bir g(x)=3x+5 fonksiyonu veriliyor. Buna göre g(4) değeri kaçtır?",
    "options": {"A": "12", "B": "14", "C": "16", "D": "17", "E": "19"},
    "correctOption": "D",
    "solutionExplanation": "g(4)=3*4+5=17. Önce yerine koy, sonra çarpıp sabiti ekle; D doğrudur.",
    "answerExpression": "3*4+5",
    "transformationLog": "Fonksiyonun katsayısı 2 yerine 3, sabit 3 yerine 5 olarak değişti.",
    "diagramJson": "",
}
YES_FIELDS = (
    "sameSpecificSubSkill", "sameProblemArchitecture", "sameOrderedReasoning",
    "transformedMeaningfulParameters", "diagramMatchesQuestion",
    "singleUnambiguousAnswer", "sufficientAndConsistentInformation",
    "notSourceCopy", "independentOfUnavailableDiagram",
)
REVIEW = dict(
    solvedOptionWithoutAnswerKey="D",
    independentSolution="Yeni soruyu sıfırdan çözdüm: g(4)=3*4+5=17. D şıkkı 17 olduğu için cevap D.",
    independentAnswerExpression="12+5",
    **{field: "YES" for field in YES_FIELDS},
)


def question(**changes):
    data = {
        "questionId": "unit-001",
        "topic": "Fonksiyon Uygulamaları",
        "subject": "AYT Matematik",
        "difficulty": "Orta",
        "sourceText": SOURCE["transcript"],
        "verifiedOriginalOption": "C",
    }
    data.update(changes)
    return app.TwinRequest(**data)


def run_with_responses(responses, request=None):
    with patch.object(app, "ask", side_effect=responses) as fake:
        try:
            result = app.generate_twin(request or question())
        except HTTPException:
            assert fake.call_count <= 5
            raise
        assert fake.call_count == 3
        return result


class ServiceTests(unittest.TestCase):
    def test_accepts_with_two_independent_arithmetic_proofs(self):
        response = run_with_responses([SOURCE, DRAFT, REVIEW])
        self.assertTrue(response["accepted"])
        self.assertEqual(response["correctOption"], "D")
        self.assertEqual(response["options"]["D"], "17")
        self.assertEqual(response["diagramJson"], "")

    def test_wrong_source_key_rejected_before_any_draft(self):
        with patch.object(app, "ask", side_effect=[SOURCE]) as fake:
            with self.assertRaises(HTTPException) as ctx:
                app.generate_twin(question(verifiedOriginalOption="A"))
            self.assertEqual(ctx.exception.status_code, 422)
            self.assertEqual(fake.call_count, 1)

    def test_missing_original_key_rejected_before_model(self):
        with patch.object(app, "ask") as fake:
            with self.assertRaises(HTTPException):
                app.generate_twin(question(verifiedOriginalOption=""))
            fake.assert_not_called()

    def test_rejects_disagreeing_independent_calculation(self):
        bad = {**REVIEW, "independentAnswerExpression": "12+7"}
        with self.assertRaises(HTTPException):
            run_with_responses([SOURCE, DRAFT, bad, DRAFT, bad])

    def test_rejects_unsupported_figures(self):
        source = {**SOURCE, "diagramFamily": "unsupported"}
        with self.assertRaises(HTTPException):
            run_with_responses([source])

    def test_rejects_diagram_family_mismatch(self):
        source = {**SOURCE, "diagramFamily": "triangle"}
        with self.assertRaises(HTTPException):
            run_with_responses([source, DRAFT, DRAFT])

    def test_rejects_false_structural_approval(self):
        bad = {**REVIEW, "sameOrderedReasoning": "NO"}
        with self.assertRaises(HTTPException):
            run_with_responses([SOURCE, DRAFT, bad, DRAFT, bad])

    def test_rejects_duplicate_options_and_injection(self):
        invalid = {**DRAFT, "options": {**DRAFT["options"], "B": "17"}}
        with self.assertRaises(HTTPException):
            run_with_responses([SOURCE, invalid, invalid])
        injected = {**DRAFT, "answerExpression": "__import__('os')"}
        with self.assertRaises(HTTPException):
            run_with_responses([SOURCE, injected, injected])


if __name__ == "__main__":
    unittest.main()
