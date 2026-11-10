import unittest
from fractions import Fraction

from math_gate import (
    VerificationError, exact_arithmetic, verify_numeric_choices, validate_diagram
)


class MathGateTests(unittest.TestCase):
    def test_exact_fraction_and_sympy_check(self):
        values = dict(A="2/3", B="7/25", C="119/169", D="8/25", E="1/2")
        self.assertEqual(
            verify_numeric_choices(values, "B", "1-2*(3/5)**2"), Fraction(7, 25)
        )

    def test_wrong_answer_or_duplicate_choices_fail(self):
        values = dict(A="2", B="3", C="4", D="5", E="6")
        with self.assertRaises(VerificationError):
            verify_numeric_choices(values, "B", "2+2")
        with self.assertRaises(VerificationError):
            verify_numeric_choices(dict(A="2", B="3", C="4", D="5", E="4"), "C", "2+2")

    def test_injection_and_expensive_exponents_fail(self):
        for raw in ("__import__('os').system('echo pwned')", "1**10000000", "open('/etc/passwd')", "x+2"):
            with self.assertRaises(VerificationError):
                exact_arithmetic(raw)
        self.assertEqual(exact_arithmetic("3*(4+5)"), Fraction(27, 1))

    def test_table_flowchart_and_timeline_supported(self):
        table = """{"kind":"table","lines":[
            {"x1":0.1,"y1":0.1,"x2":0.9,"y2":0.1},
            {"x1":0.1,"y1":0.5,"x2":0.9,"y2":0.5},
            {"x1":0.1,"y1":0.9,"x2":0.9,"y2":0.9},
            {"x1":0.1,"y1":0.1,"x2":0.1,"y2":0.9}],
            "labels":[
            {"x":0.3,"y":0.3,"text":"Sıcaklık"},
            {"x":0.7,"y":0.3,"text":"Hız"},
            {"x":0.3,"y":0.7,"text":"20"},
            {"x":0.7,"y":0.7,"text":"5"}]}"""
        self.assertEqual(validate_diagram(table, "table")["kind"], "table")
        flow = """{"kind":"flowchart","lines":[
            {"x1":0.2,"y1":0.2,"x2":0.5,"y2":0.5,"arrow":true},
            {"x1":0.5,"y1":0.5,"x2":0.8,"y2":0.8}],
            "labels":[
            {"x":0.2,"y":0.1,"text":"Başlangıç"},
            {"x":0.8,"y":0.9,"text":"Sonuç"}]}"""
        self.assertEqual(validate_diagram(flow, "flowchart")["kind"], "flowchart")
        self.assertEqual(validate_diagram(flow.replace("flowchart", "timeline"), "timeline")["kind"], "timeline")
        with self.assertRaises(VerificationError):
            validate_diagram(flow.replace('"arrow":true', '"arrow":false'), "flowchart")

    def test_graph_curves_are_bounded_and_not_zero_length(self):
        graph = """{"kind":"graph","lines":[
          {"x1":0.1,"y1":0.8,"x2":0.9,"y2":0.8},
          {"x1":0.1,"y1":0.8,"x2":0.1,"y2":0.1}],
          "paths":[{"points":[
            {"x":0.2,"y":0.7},{"x":0.4,"y":0.55},{"x":0.6,"y":0.7}]}],
          "labels":[{"x":0.9,"y":0.9,"text":"x"}]}"""
        self.assertEqual(len(validate_diagram(graph, "graph")["paths"]), 1)
        with self.assertRaises(VerificationError):
            validate_diagram(graph.replace('"x":0.4', '"x":1.4'), "graph")
        bad = graph.replace('{"x":0.4,"y":0.55},{"x":0.6,"y":0.7}',
                            '{"x":0.2,"y":0.7},{"x":0.2,"y":0.7}')
        with self.assertRaises(VerificationError):
            validate_diagram(bad, "graph")

    def test_valid_and_invalid_generated_figure(self):
        raw = """{"kind":"triangle","lines":[
            {"x1":0.1,"y1":0.8,"x2":0.8,"y2":0.8},
            {"x1":0.8,"y1":0.8,"x2":0.1,"y2":0.1},
            {"x1":0.1,"y1":0.1,"x2":0.1,"y2":0.8}
        ],"labels":[{"x":0.1,"y":0.9,"text":"A"}]}"""
        self.assertEqual(validate_diagram(raw, "triangle")["kind"], "triangle")
        with self.assertRaises(VerificationError):
            validate_diagram(raw, "vectors")
        with self.assertRaises(VerificationError):
            validate_diagram(raw.replace('"x2":0.8', '"x2":2.8'), "triangle")
        with self.assertRaises(VerificationError):
            validate_diagram("", "triangle")
        self.assertIsNone(validate_diagram("", "none"))


if __name__ == "__main__":
    unittest.main()
