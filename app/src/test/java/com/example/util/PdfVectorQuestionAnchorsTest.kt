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
    @Test
    fun sparseMixedColumnLayoutsAreNotLost() {
        val glyphs = listOf(
            PdfSourceGlyph("1.", 42f, 90f, 10f, 9f),
            PdfSourceGlyph("2.", 250f, 90f, 10f, 9f),
            PdfSourceGlyph("3.", 250f, 285f, 10f, 9f)
        )
        val anchors = PdfVectorQuestionAnchors.find(glyphs, 460f, 600f)
        assertEquals(listOf(1, 2, 3), anchors.map { it.number })
    }

    @Test
    fun nonstandardNarrowMarginsAreStillProcessed() {
        val glyphs = listOf(
            PdfSourceGlyph("1.", 15f, 35f, 12f, 9f),
            PdfSourceGlyph("2.", 15f, 280f, 12f, 9f),
            PdfSourceGlyph("3.", 235f, 35f, 12f, 9f)
        )
        val anchors = PdfVectorQuestionAnchors.find(glyphs, 460f, 600f)
        assertEquals(listOf(1, 2, 3), anchors.map { it.number })
    }

    @Test
    fun multiplePrintedNumberStylesWorkWithoutPublisherSpecificTemplates() {
        val glyphs = listOf(
            PdfSourceGlyph("1:", 42f, 95f, 13f, 9f),
            PdfSourceGlyph("2-", 42f, 312f, 13f, 9f),
            PdfSourceGlyph("3)", 247f, 90f, 13f, 9f),
            PdfSourceGlyph("4.", 247f, 301f, 13f, 9f)
        )
        val result = PdfVectorQuestionAnchors.find(glyphs, 460f, 600f)
        assertEquals(listOf(1, 2, 3, 4), result.map { it.number })
    }

    @Test
    fun printedFooterAndDiagramNumbersCannotEraseAValidQuestionSequence() {
        val glyphs = listOf(
            PdfSourceGlyph("1.", 57f, 82f, 12f, 9f),
            PdfSourceGlyph("2.", 57f, 288f, 12f, 9f),
            PdfSourceGlyph("3.", 57f, 607f, 12f, 9f),
            PdfSourceGlyph("4.", 315f, 82f, 12f, 9f),
            PdfSourceGlyph("5.", 315f, 508f, 12f, 9f),
            // Page footer happens to be printed at the left margin.
            PdfSourceGlyph("1.", 57f, 755f, 12f, 9f),
            // Diagram labels within a question should not win.
            PdfSourceGlyph("8.", 93f, 315f, 12f, 9f),
            PdfSourceGlyph("9.", 93f, 330f, 12f, 9f)
        )
        val anchors = PdfVectorQuestionAnchors.find(glyphs, 623f, 807f)
        assertEquals(listOf(1, 2, 3, 4, 5), anchors.map { it.number })
        assertEquals(5, anchors.size)
    }

    @Test
    fun anInterleavedSpuriousNumberIsSkippedInsteadOfResettingTheColumn() {
        val glyphs = listOf(
            PdfSourceGlyph("1.", 71f, 90f, 12f, 9f),
            PdfSourceGlyph("2.", 71f, 290f, 12f, 9f),
            PdfSourceGlyph("11.", 71f, 354f, 12f, 9f),
            PdfSourceGlyph("3.", 71f, 490f, 12f, 9f),
            PdfSourceGlyph("4.", 330f, 90f, 12f, 9f),
            PdfSourceGlyph("5.", 330f, 390f, 12f, 9f)
        )
        assertEquals(
            listOf(1, 2, 3, 4, 5),
            PdfVectorQuestionAnchors.find(glyphs, 623f, 807f).map { it.number }
        )
    }

    @Test
    fun sourcePdfMarkersBeyondQuestionNinetyNineAreKept() {
        val glyphs = listOf(
            PdfSourceGlyph("118.", 53f, 90f, 20f, 9f),
            PdfSourceGlyph("119)", 53f, 320f, 20f, 9f),
            PdfSourceGlyph("120:", 53f, 540f, 20f, 9f)
        )
        assertEquals(
            listOf(118, 119, 120),
            PdfVectorQuestionAnchors.find(glyphs, 623f, 807f).map { it.number }
        )
    }

}
