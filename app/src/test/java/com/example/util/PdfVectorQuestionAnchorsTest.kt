package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfVectorQuestionAnchorsTest {
    @Test
    fun twoPrintedColumnsCanCarryUnevenQuestionCounts() {
        val glyphs = listOf(
            PdfSourceGlyph("1.", 56f, 110f, 12f, 9f),
            PdfSourceGlyph("2.", 56f, 330f, 12f, 9f),
            PdfSourceGlyph("3.", 310f, 105f, 12f, 9f),
            PdfSourceGlyph("4.", 310f, 290f, 12f, 9f),
            PdfSourceGlyph("5.", 310f, 490f, 12f, 9f),
            // This number belongs to a chart axis, not the printed margin.
            PdfSourceGlyph("7.", 440f, 260f, 12f, 9f)
        )
        val anchors = PdfVectorQuestionAnchors.find(glyphs, 595f, 792f)
        assertEquals(listOf(1, 2, 3, 4, 5), anchors.map { it.number })
        assertEquals(2, anchors.count { it.x < 200f })
        assertEquals(3, anchors.count { it.x > 200f })
    }

    @Test
    fun isolatedGraphNumbersCannotBeMistakenForQuestionBlocks() {
        val glyphs = listOf(
            PdfSourceGlyph("1.", 150f, 200f, 10f, 10f),
            PdfSourceGlyph("4.", 360f, 200f, 10f, 10f),
            PdfSourceGlyph("7.", 410f, 240f, 10f, 10f)
        )
        assertTrue(PdfVectorQuestionAnchors.find(glyphs, 595f, 792f).isEmpty())
    }
}
