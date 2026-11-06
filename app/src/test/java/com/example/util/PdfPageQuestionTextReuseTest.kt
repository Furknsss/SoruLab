package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageQuestionTextReuseTest {
    private fun line(s: String, x: Int, y: Int, w: Int = 350) =
        PdfOcrTextCell(s, x, y, x + w, y + 28)

    private val question = listOf(
        line("Aşağıdaki şekilde verilen biyolojik olayda", 80, 220),
        line("hücre zarından madde geçişini açıklayan ifade hangisidir?", 80, 264),
        line("A) Enerji tüketimini gerektirir.", 80, 325),
        line("B) Yalnızca canlı hücrelerde görülür.", 80, 365),
        line("C) Taşıyıcı protein kullanılması zorunludur.", 80, 405),
        line("D) Madde derişimi sürekli aynı kalır.", 80, 445),
        line("E) Her zaman çok yüksek sıcaklıkta gerçekleşir.", 80, 485)
    )
    private val box = listOf(.115f, .07f, .335f, .47f)

    @Test fun wholePageOcrTextCanSkipASecondPerQuestionRecognition() {
        val selected = PdfPageQuestionTextReuse.readIfComplete(
            question + listOf(
                line("1. Kitabın sayfa numarası", 700, 220, 245),
                line("2. Sonraki sütunun sorusu", 700, 400, 245)
            ), box, 1000, 1600
        )
        assertTrue(selected!!.contains("Aşağıdaki şekilde"))
        assertTrue(selected.contains("E) Her zaman"))
        assertTrue(!selected.contains("Sonraki sütunun"))
    }

    @Test fun missingEChoiceForcesExistingHighDetailCropOcr() {
        val answerIncomplete = question.filterNot { it.text.startsWith("E)") }
        assertNull(PdfPageQuestionTextReuse.readIfComplete(
            answerIncomplete, box, 1000, 1600
        ))
    }

    @Test fun tooLittleTextOrWideCrossColumnOcrCannotBeReused() {
        assertNull(PdfPageQuestionTextReuse.readIfComplete(
            question.take(2), box, 1000, 1600
        ))
        assertNull(PdfPageQuestionTextReuse.readIfComplete(
            question.map { it.copy(right = 780) }, box, 1000, 1600
        ))
        assertNull(PdfPageQuestionTextReuse.readIfComplete(
            question, listOf(.1f, .8f, .1f, .95f), 1000, 1600
        ))
    }

    @Test fun sourceCoordinatesMustBelongToExactlyOnePhysicalCrop() {
        val source = PdfPageQuestionTextReuse.readIfComplete(
            question, box, 1000, 1600
        )
        val next = PdfPageQuestionTextReuse.readIfComplete(
            question, listOf(.115f, .52f, .335f, .93f), 1000, 1600
        )
        assertTrue(source!!.contains("hücre zarından"))
        assertEquals(null, next)
    }

    private fun vectorLines() = question.map { ocr ->
        PdfSourceTextFragment(
            text = ocr.text,
            left = ocr.left / 2f, top = ocr.top / 2f,
            right = ocr.right / 2f, bottom = ocr.bottom / 2f
        )
    }

    @Test fun nativePdfTextCanReplaceAllRedundantQuestionCropOcr() {
        val native = vectorLines()
        val selected = PdfPageQuestionTextReuse.readNativeIfComplete(
            fragments = native + PdfSourceTextFragment(
                "3. Başka sütunun sorusu", 360f, 120f, 480f, 130f
            ),
            normalizedCrop = box,
            pdfPageWidth = 500f, pdfPageHeight = 800f
        )
        assertTrue("PDFBox source text must be reused", selected != null)
        assertTrue(selected!!.contains("Aşağıdaki şekilde"))
        assertTrue(selected.contains("E) Her zaman"))
        assertTrue(!selected.contains("Başka sütunun"))
    }

    @Test fun nativeTextWithMissingChoiceOrWrongCropCannotBypassOcr() {
        assertNull(PdfPageQuestionTextReuse.readNativeIfComplete(
            vectorLines().dropLast(1), box, 500f, 800f
        ))
        assertNull(PdfPageQuestionTextReuse.readNativeIfComplete(
            vectorLines(), listOf(.115f, .07f, .29f, .47f), 500f, 800f
        ))
        assertNull(PdfPageQuestionTextReuse.readNativeIfComplete(
            vectorLines(), listOf(.115f, .52f, .335f, .93f), 500f, 800f
        ))
    }

    @Test fun nativeTextOverlappingTwoColumnsIsNotAcceptedAsOneQuestion() {
        val bad = vectorLines().map {
            it.copy(right = 460f)
        }
        assertNull(PdfPageQuestionTextReuse.readNativeIfComplete(
            bad, box, 500f, 800f
        ))
    }
}
