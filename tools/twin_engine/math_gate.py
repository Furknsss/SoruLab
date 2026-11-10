"""Safe arithmetic and geometry payload gates for SoruLab's local Qwen service.

This proves arithmetic consistency for supported numeric-answer questions.
It is NOT a proof that the generated wording is semantically equivalent.
That still requires a separate blind solution and structural review.
"""
from __future__ import annotations

import ast
import json
import math
import re
from fractions import Fraction

import sympy as sp

MAX_EXPRESSION_LEN = 120
MAX_MAGNITUDE = 10**12


class VerificationError(ValueError):
    """Reject the candidate rather than guessing."""


def exact_arithmetic(expression: str) -> Fraction:
    """Evaluate a strictly bounded numeric arithmetic tree, never eval/sympify."""
    if not isinstance(expression, str) or len(expression) > MAX_EXPRESSION_LEN:
        raise VerificationError("Unsupported numeric expression")
    try:
        tree = ast.parse(expression.replace("−", "-").replace("^", "**"), mode="eval")
    except SyntaxError as exc:
        raise VerificationError("Invalid numeric expression") from exc

    def visit(node: ast.AST, depth: int = 0) -> Fraction:
        if depth > 12:
            raise VerificationError("Expression too deep")
        if isinstance(node, ast.Expression):
            return visit(node.body, depth + 1)
        if isinstance(node, ast.Constant) and type(node.value) in (int, float):
            return Fraction(str(node.value))
        if isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.UAdd, ast.USub)):
            x = visit(node.operand, depth + 1)
            return -x if isinstance(node.op, ast.USub) else x
        if isinstance(node, ast.BinOp):
            left = visit(node.left, depth + 1)
            right = visit(node.right, depth + 1)
            try:
                if isinstance(node.op, ast.Add):
                    result = left + right
                elif isinstance(node.op, ast.Sub):
                    result = left - right
                elif isinstance(node.op, ast.Mult):
                    result = left * right
                elif isinstance(node.op, ast.Div):
                    result = left / right
                elif isinstance(node.op, ast.Pow) and right.denominator == 1 and -6 <= right <= 6:
                    result = left ** int(right)
                else:
                    raise VerificationError("Unsupported operation")
            except (OverflowError, ZeroDivisionError) as exc:
                raise VerificationError("Invalid arithmetic") from exc
            if abs(result.numerator) > MAX_MAGNITUDE or result.denominator > MAX_MAGNITUDE:
                raise VerificationError("Expression overflow")
            return result
        raise VerificationError("Only basic arithmetic is accepted")

    return visit(tree)


def printed_number(text: str) -> Fraction:
    if not isinstance(text, str):
        raise VerificationError("Non-text option")
    normalized = text.strip().replace("−", "-").replace(" ", "")
    fraction = re.fullmatch(r"\\frac\{(-?\d+)\}\{(\d+)\}", normalized)
    if fraction:
        return Fraction(int(fraction.group(1)), int(fraction.group(2)))
    if not re.fullmatch(r"[+-]?\d+(?:\.\d+)?(?:/[+-]?\d+)?", normalized):
        raise VerificationError("Non-numeric option; unsupported numeric certificate")
    return exact_arithmetic(normalized)


def verify_numeric_choices(
    options: dict[str, str], correct_option: str, answer_expression: str
) -> Fraction:
    if set(options) != set("ABCDE") or correct_option not in "ABCDE":
        raise VerificationError("Expected five options and A-E answer")
    values = {key: printed_number(value) for key, value in options.items()}
    if len(set(values.values())) != 5:
        raise VerificationError("Duplicate numeric answers")
    proof_value = exact_arithmetic(answer_expression)
    matches = [letter for letter, value in values.items() if value == proof_value]
    if matches != [correct_option]:
        raise VerificationError("Computed value disagrees with options")
    # Second exact-arithmetic consistency check using SymPy. Never parse
    # model-supplied code with sympify: only validated Fraction numerators
    # and denominators enter the symbolic algebra engine.
    expected = sp.Rational(proof_value.numerator, proof_value.denominator)
    for label, value in values.items():
        option = sp.Rational(value.numerator, value.denominator)
        same = sp.simplify(expected - option) == 0
        if same != (label == correct_option):
            raise VerificationError("SymPy disagrees with numeric proof")
    return proof_value


def validate_diagram(raw: str, required_kind: str) -> dict | None:
    if required_kind == "none":
        if raw.strip():
            raise VerificationError("Unexpected diagram")
        return None
    if required_kind not in {"triangle", "circle", "graph", "vectors", "schematic", "table", "flowchart", "timeline"}:
        raise VerificationError("Unsupported diagram class")
    if not raw or len(raw) > 12000:
        raise VerificationError("Missing/oversize diagram")
    try:
        diagram = json.loads(raw)
    except (TypeError, ValueError) as exc:
        raise VerificationError("Invalid diagram JSON") from exc
    if not isinstance(diagram, dict) or diagram.get("kind") != required_kind:
        raise VerificationError("Wrong diagram class")
    lines, circles, labels = (diagram.get(k, []) for k in ("lines", "circles", "labels"))
    paths = diagram.get("paths", [])
    if not all(isinstance(v, list) for v in (lines, circles, labels, paths)):
        raise VerificationError("Invalid diagram arrays")
    if len(lines) > 28 or len(circles) > 12 or len(labels) > 24 or len(paths) > 8:
        raise VerificationError("Oversize diagram")
    def coord(item: dict, field: str) -> float:
        value = item.get(field)
        if type(value) not in (float, int) or not math.isfinite(value) or not 0 <= value <= 1:
            raise VerificationError("Invalid figure coordinate")
        return float(value)
    for line in lines:
        if not isinstance(line, dict):
            raise VerificationError("Invalid line")
        x1, y1, x2, y2 = (coord(line, k) for k in ("x1", "y1", "x2", "y2"))
        if math.hypot(x1 - x2, y1 - y2) <= .025:
            raise VerificationError("Zero-length line")
    for circle in circles:
        if not isinstance(circle, dict):
            raise VerificationError("Invalid circle")
        x, y, r = (coord(circle, k) for k in ("cx", "cy", "radius"))
        if not .015 <= r <= .42 or min(x-r, y-r) < 0 or max(x+r, y+r) > 1:
            raise VerificationError("Circle off canvas")
    for label in labels:
        if not isinstance(label, dict):
            raise VerificationError("Invalid label")
        coord(label, "x")
        coord(label, "y")
        txt = label.get("text")
        if not isinstance(txt, str) or not txt.strip() or len(txt) > 24:
            raise VerificationError("Invalid label text")
    for path in paths:
        if not isinstance(path, dict) or not isinstance(path.get("points"), list):
            raise VerificationError("Invalid curve")
        points = path["points"]
        if not 3 <= len(points) <= 64:
            raise VerificationError("Curve point count")
        positions = [(coord(p, "x"), coord(p, "y")) for p in points]
        if sum(math.hypot(x2-x1, y2-y1) > .004
               for ((x1, y1), (x2, y2)) in zip(positions, positions[1:])) < 2:
            raise VerificationError("Invisible or repeating curve")
    if not labels or len(lines) + len(circles) + len(paths) == 0:
        raise VerificationError("Figure is empty")
    if required_kind == "triangle" and len(lines) < 3:
        raise VerificationError("Incomplete triangle")
    if required_kind == "circle" and not circles:
        raise VerificationError("Missing circle")
    if required_kind == "graph" and len(lines) < 2:
        raise VerificationError("Missing graph axes")
    if required_kind == "vectors" and not any(line.get("arrow") is True for line in lines):
        raise VerificationError("Missing arrow")
    if required_kind == "schematic" and len(lines) + len(circles) + len(paths) < 2:
        raise VerificationError("Incomplete schematic")
    if required_kind == "table" and (len(lines) < 4 or len(labels) < 4):
        raise VerificationError("Incomplete table cells")
    if required_kind == "flowchart" and (
        len(lines) < 2 or len(labels) < 2 or
        not any(line.get("arrow") is True for line in lines)
    ):
        raise VerificationError("Incomplete flowchart")
    if required_kind == "timeline" and (len(lines) < 1 or len(labels) < 2):
        raise VerificationError("Incomplete timeline")
    return diagram
