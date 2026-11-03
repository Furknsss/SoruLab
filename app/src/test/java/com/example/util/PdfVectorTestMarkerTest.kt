package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfVectorTestMarkerTest {
    private fun g(t: String, x: Float, y: Float, w: Float = 12f, h: Float = 12f) =
        PdfSourceGlyph(t, x, y, w, h)

    private fun letters(word: String, x: Float, y: Float): List<PdfSourceGlyph> =
        word.mapIndexed { i, ch ->
            g(ch.toString(), x + i * 6.2f, y, 6.2f, 12f)
        }

    @Test
    fun footerAnswerNumbersCannotStealPrintedTestNumber() {
        val glyphs = letters("ANALİZ", 64f, 41f) + listOf(
            g("12", 76f, 54f, 22f, 18f),
            g("1", 100f, 746f), g("2", 140f, 746f),
            g("3", 180f, 746f), g("4", 220f, 746f)
        )
        val actual = PdfVectorTestMarker.find(glyphs, 623f, 807f)
        assertEquals(12, actual?.testNumber)
        assertEquals("analiz", actual?.testKind)
    }

    @Test
    fun rightAlignedLayoutRequiresNoFixedX() {
        val glyphs = letters("SENTEZ", 518f, 41f) + listOf(
            g("9", 531f, 54f, 11f, 18f),
            g("1", 85f, 720f), g("2", 120f, 720f)
        )
        val actual = PdfVectorTestMarker.find(glyphs, 623f, 807f)
        assertEquals(9, actual?.testNumber)
        assertEquals("sentez", actual?.testKind)
    }

    @Test
    fun horizontalMarkerAndThreeDigitTestNumber() {
        val glyphs = letters("TEST", 100f, 88f) + listOf(
            g("120", 145f, 88f, 19f, 12f)
        )
        assertEquals(120, PdfVectorTestMarker.find(glyphs, 620f, 900f)?.testNumber)
    }

    @Test
    fun isolatedAnswerRowsAndMidpageFiguresAreNotTrusted() {
        val footer = listOf(
            g("1", 80f, 745f), g("2", 120f, 745f),
            g("3", 160f, 745f)
        )
        assertNull(PdfVectorTestMarker.find(footer, 623f, 807f))
        val chartLabel = letters("TEST", 90f, 390f) +
            listOf(g("1", 134f, 390f))
        assertNull(PdfVectorTestMarker.find(chartLabel, 623f, 807f))
    }

    @Test
    fun nearbyOsymLabelPreservesOfficialFlag() {
        val glyphs = letters("ÖSYM", 60f, 38f) +
            letters("TEST", 120f, 41f) +
            listOf(g("6", 162f, 54f, 11f, 18f))
        val actual = PdfVectorTestMarker.find(glyphs, 623f, 807f)
        assertEquals(6, actual?.testNumber)
        assertTrue(actual?.isOsymTest == true)
    }
}
