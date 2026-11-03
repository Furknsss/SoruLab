package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfVectorPageHeaderTest {
    private fun printedText(text: String, x: Float, y: Float) =
        text.mapIndexed { index, ch ->
            PdfSourceGlyph(ch.toString(), x + index * 5f, y, 5f, 11f)
        }

    @Test
    fun sourceTopicIsAvailableEvenWhenPdfTextOrderEmitsItAfterAnswers() {
        val glyphs = listOf(
            PdfSourceGlyph("SENTEZ", 64f, 41f, 40f, 12f),
            PdfSourceGlyph("16", 79f, 54f, 18f, 18f)
        ) + printedText("Endokrin Sistem Genel Tekrar", 280f, 59f) +
            listOf(
                PdfSourceGlyph("5.", 70f, 82f, 10f, 10f),
                PdfSourceGlyph("1", 79f, 730f, 10f, 10f),
                PdfSourceGlyph("2", 110f, 730f, 10f, 10f)
            )
        val header = PdfVectorPageHeader.read(glyphs, 623f, 807f, 82f)
        assertTrue(header.contains("Endokrin Sistem Genel Tekrar"))
        assertTrue(header.contains("SENTEZ"))
        assertFalse(header.contains("5."))
        assertFalse(header.contains("730"))
    }

    @Test
    fun chapterEvaluationIsARealTopicNotAnArbitraryUnitNumber() {
        val glyphs = listOf(
            PdfSourceGlyph("SENTEZ", 64f, 41f, 40f, 12f),
            PdfSourceGlyph("18", 79f, 54f, 20f, 18f)
        ) + printedText("Ünite Değerlendirme", 278f, 59f)
        val header = PdfVectorPageHeader.read(glyphs, 623f, 807f, 90f)
        assertTrue(header.contains("Ünite Değerlendirme"))
    }

    @Test
    fun noSourceTextLeavesPageReadyForOcrFallback() {
        assertTrue(PdfVectorPageHeader.read(emptyList(), 623f, 807f).isBlank())
        assertTrue(PdfVectorPageHeader.read(
            listOf(PdfSourceGlyph("QUESTION", 70f, 355f, 55f, 11f)),
            623f, 807f, 90f
        ).isBlank())
    }
}
