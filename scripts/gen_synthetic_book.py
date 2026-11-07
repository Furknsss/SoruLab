#!/usr/bin/env python3
"""Synthetic question-bank PDF that mimics the layout of a Turkish YKS book.

Every test spans two pages. The first page holds questions 1..k, the second page
k+1..n and the printed answer row (numbers row + letters row + test number).
No real book content is used. A ground-truth JSON is written next to the PDF.

usage: gen_synthetic_book.py OUT.pdf OUT.json [--key-pos top|bottom] [--seed N]
"""
import argparse
import json
import random

from reportlab.lib.colors import HexColor, black, white
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

FONT_DIRS = ["/usr/share/fonts/truetype/dejavu/", "/usr/share/fonts/dejavu/"]
W, H = 595.0, 842.0
MARGIN_L, MARGIN_R = 46.0, 30.0
COL_GAP = 22.0
TOP_BAND = 64.0   # header area
FOOTER = 40.0

TOPICS = [
    ("Sinir Sistemi", "ÜNİTE 1: İNSAN FİZYOLOJİSİ"),
    ("Endokrin Sistem", "ÜNİTE 1: İNSAN FİZYOLOJİSİ"),
    ("Dolaşım Sistemi", "ÜNİTE 1: İNSAN FİZYOLOJİSİ"),
    ("Üreme Sistemi ve Embriyonik Gelişim", "ÜNİTE 1: İNSAN FİZYOLOJİSİ"),
    ("Bitkisel Hormonlar ve Bitkilerde Hareket", "ÜNİTE 4: BİTKİ BİYOLOJİSİ"),
    ("Canlılar ve Çevre", "ÜNİTE 5: KOMÜNİTE VE POPÜLASYON EKOLOJİSİ"),
]
STEMS = [
    "Bir deney düzeneğinde farklı koşullarda gözlenen değişimler aşağıda verilmiştir.",
    "Aşağıdaki tabloda bir hücrenin farklı evrelerdeki özellikleri gösterilmiştir.",
    "Bir araştırmacı, canlılarda görülen bir olayı incelemek için üç ayrı grup oluşturmuştur.",
    "Verilen şemada numaralandırılmış yapılar ve bunların görevleri belirtilmiştir.",
    "Bir öğrenci, laboratuvarda yaptığı gözlemleri aşağıdaki gibi kaydetmiştir.",
]
ASKS = [
    "Buna göre numaralanmış boşluklara hangisi gelmelidir?",
    "Yukarıda verilen bilgilere göre aşağıdakilerden hangisi söylenemez?",
    "Bu durumla ilgili aşağıdaki yargılardan hangileri doğrudur?",
    "Buna göre hangi sonuca ulaşılabilir?",
]
OPTS = ["Yalnız I", "Yalnız II", "I ve II", "I ve III", "II ve III", "I, II ve III",
        "Yalnız III", "I, II ve IV"]


def register_fonts():
    import os
    for d in FONT_DIRS:
        reg, bold = d + "DejaVuSans.ttf", d + "DejaVuSans-Bold.ttf"
        if os.path.exists(reg):
            pdfmetrics.registerFont(TTFont("Body", reg))
            pdfmetrics.registerFont(TTFont("BodyBold", bold))
            return
    raise SystemExit("DejaVu fonts not found")


def wrap(text, font, size, width):
    words, lines, cur = text.split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if pdfmetrics.stringWidth(t, font, size) <= width:
            cur = t
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def make_question(rng, number, with_figure):
    """Return a list of drawing ops: ('text', str, bold) / ('fig', h) / ('gap', h)."""
    ops = [("text", f"{number}. " + rng.choice(STEMS), False)]
    if rng.random() < 0.5:
        for roman in ("I", "II", "III")[: rng.choice([2, 3])]:
            ops.append(("text", f"{roman}. Bir olay gözlenir ve sonuç kaydedilir,", False))
    if with_figure:
        ops.append(("fig", rng.choice([54, 72, 96])))
    ops.append(("text", rng.choice(ASKS), False))
    letters = "ABCDE"
    choices = rng.sample(OPTS, 5)
    for letter, c in zip(letters, choices):
        ops.append(("text", f"{letter}) {c}", False))
    return ops


def op_height(op, col_w, size, leading):
    kind = op[0]
    if kind == "fig":
        return op[1] + 6
    if kind == "gap":
        return op[1]
    return len(wrap(op[1], "Body", size, col_w)) * leading


def draw_question(c, ops, x, y_top, col_w, size, leading):
    y = y_top
    for op in ops:
        if op[0] == "fig":
            c.setLineWidth(0.8)
            c.rect(x + 14, y - op[1], col_w - 40, op[1], stroke=1, fill=0)
            c.setFont("Body", 8)
            c.drawString(x + 22, y - op[1] / 2, "Şekil: yapılar ve oklar")
            y -= op[1] + 6
        else:
            c.setFont("Body", size)
            for line in wrap(op[1], "Body", size, col_w):
                y -= leading
                c.drawString(x, y + 2, line)
    return y


def draw_frame(c, page_no, unit_label, topic, kind, test_no, first_page):
    c.setFillColor(HexColor("#0E7C86"))
    c.setFont("BodyBold", 8)
    for i, ch in enumerate("BİYOTİK"):
        c.drawString(14, H - 120 - i * 14, ch)
    c.setFillColor(black)
    c.setFont("Body", 8)
    c.drawRightString(W - MARGIN_R, H - 24, f"{unit_label} {page_no}")
    c.setFont("BodyBold", 11)
    c.drawString(MARGIN_L, H - 30, kind)
    c.setFont("BodyBold", 13)
    label = f"{topic} {test_no}" if first_page else topic
    c.drawString(MARGIN_L, H - 48, label)
    if not first_page:
        c.setFont("BodyBold", 13)
        c.drawRightString(W - MARGIN_R, H - 48, str(test_no))
    c.setStrokeColor(HexColor("#0E7C86"))
    c.setLineWidth(1.2)
    c.line(MARGIN_L, H - 56, W - MARGIN_R, H - 56)
    c.setStrokeColor(black)
    c.setFont("Body", 6)
    c.drawString(MARGIN_L, 14, "@KuasarNemesis @YildizlarLigi_2025PDF @M.G.")


def draw_key(c, answers, key_pos):
    n = len(answers)
    cell = 22.0
    x0 = MARGIN_L
    if key_pos == "top":
        y_num = H - TOP_BAND - 14
    else:
        y_num = FOOTER + 30
    c.setFont("BodyBold", 9)
    for i in range(n):
        c.drawCentredString(x0 + i * cell + 8, y_num, str(i + 1))
        c.drawCentredString(x0 + i * cell + 8, y_num - 13, answers[i])
    c.setLineWidth(0.6)
    c.rect(x0 - 6, y_num - 18, n * cell + 6, 30, stroke=1, fill=0)
    return y_num + 14 if key_pos == "bottom" else y_num - 22


def build(out_pdf, out_json, key_pos, seed):
    register_fonts()
    rng = random.Random(seed)
    c = canvas.Canvas(out_pdf, pagesize=(W, H))
    truth, page_index = [], 0
    pdf_page_no = 1

    # three front-matter pages: cover, copyright, table of contents
    for title in ("AYT Soru Bankası", "Hakları saklıdır. Kurgusal test kitabı.", "İÇİNDEKİLER"):
        c.setFont("BodyBold", 22)
        c.drawString(MARGIN_L, H - 200, title)
        if title == "İÇİNDEKİLER":
            c.setFont("Body", 11)
            for i, (t, _) in enumerate(TOPICS):
                c.drawString(MARGIN_L, H - 260 - i * 18, f"{t} {'.' * 40} {9 + i * 20}")
        c.showPage()
        page_index += 1
        pdf_page_no += 1

    col_w = (W - MARGIN_L - MARGIN_R - COL_GAP) / 2
    size, leading = 9.2, 11.4
    test_global = 0
    for topic, unit in TOPICS:
        for t in range(1, 5):
            kind = "SENTEZ" if t == 4 else "ANALİZ"
            test_global += 1
            n = rng.choice([10, 11, 12])
            k = n // 2
            answers = [rng.choice("ABCDE") for _ in range(n)]
            questions = [make_question(rng, i + 1, rng.random() < 0.3) for i in range(n)]
            for part, (lo, hi) in enumerate(((0, k), (k, n))):
                first = part == 0
                draw_frame(c, pdf_page_no, unit, topic, kind, t, first)
                top = H - TOP_BAND
                bottom = FOOTER
                if not first:
                    edge = draw_key(c, answers, key_pos)
                    if key_pos == "top":
                        top = edge
                    else:
                        bottom = edge
                cols_x = [MARGIN_L, MARGIN_L + col_w + COL_GAP]
                col, y = 0, top
                for qi in range(lo, hi):
                    ops = questions[qi]
                    qh = sum(op_height(o, col_w, size, leading) for o in ops) + 14
                    if y - qh < bottom and col == 0:
                        col, y = 1, top
                    if y - qh < bottom:
                        raise SystemExit(f"page overflow: test {test_global} q{qi + 1}")
                    draw_question(c, ops, cols_x[col], y, col_w, size, leading)
                    truth.append({
                        "pageIndex": page_index, "number": qi + 1, "answer": answers[qi],
                        "topic": topic, "kind": kind, "test": t, "testGlobal": test_global,
                    })
                    y -= qh
                c.showPage()
                page_index += 1
                pdf_page_no += 1
    c.save()
    with open(out_json, "w", encoding="utf-8") as fh:
        json.dump({"pages": page_index, "questions": truth, "keyPos": key_pos}, fh,
                  ensure_ascii=False)
    print(f"pages={page_index} questions={len(truth)} tests={test_global} key={key_pos}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("pdf")
    ap.add_argument("json")
    ap.add_argument("--key-pos", default="bottom", choices=["top", "bottom"])
    ap.add_argument("--seed", type=int, default=7)
    a = ap.parse_args()
    build(a.pdf, a.json, a.key_pos, a.seed)
