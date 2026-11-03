package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfColumnLayoutEvidenceTest {
    private fun q(n: Int, x: Int, y: Int) =
        QuestionNumberAnchor(number = n, left = x, top = y, right = x + 13, bottom = y + 11)

    @Test
    fun `right questions beginning at 53 percent are not discarded`() {
        // Coordinates approximate a generic 595x792 dual-column textbook.
        // No publisher names or page IDs are used in the production detector.
        val anchors = listOf(q(1, 57, 82), q(2, 57, 290), q(3, 57, 595),
            q(4, 315, 82), q(5, 315, 508))
        assertTrue(PdfColumnLayoutEvidence.hasTwoPrintedColumns(anchors, 595, 43, 748))
    }

    @Test
    fun `numbered chart labels cannot manufacture a right question column`() {
        val anchors = listOf(q(1, 57, 82), q(2, 57, 290),
            q(7, 315, 110), q(9, 315, 230))
        assertFalse(PdfColumnLayoutEvidence.hasTwoPrintedColumns(anchors, 595, 43, 748))
    }

    @Test
    fun `one printed column is kept as one column`() {
        val anchors = listOf(q(1, 58, 80), q(2, 58, 260), q(3, 58, 455))
        assertFalse(PdfColumnLayoutEvidence.hasTwoPrintedColumns(anchors, 595, 43, 748))
    }

    @Test
    fun `footer numbering cannot become printed question anchors`() {
        val anchors = listOf(q(1, 57, 82), q(2, 57, 260),
            q(3, 315, 755), q(4, 315, 780))
        assertFalse(PdfColumnLayoutEvidence.hasTwoPrintedColumns(anchors, 595, 43, 748))
    }
}
