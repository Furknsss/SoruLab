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
    @Test
    fun `wide page with off center separator uses printed numbering not a 53 percent rule`() {
        val anchors = listOf(q(11, 110, 100), q(12, 110, 390),
            q(13, 720, 110), q(14, 720, 465))
        val actual = PdfColumnLayoutEvidence.findTwoPrintedColumnStarts(
            anchors, 1200, 55, 780
        )
        assertTrue(actual != null)
        assertTrue(actual!!.second > 1200 * .55f)
    }

    @Test
    fun `narrow printed columns with asymmetric margins are supported`() {
        val anchors = listOf(q(21, 34, 90), q(22, 34, 270),
            q(23, 241, 90), q(24, 241, 295))
        val actual = PdfColumnLayoutEvidence.findTwoPrintedColumnStarts(
            anchors, 460, 32, 560
        )
        assertTrue(actual != null)
        assertTrue(actual!!.second > actual.first)
    }

    @Test
    fun `nonconsecutive graphics labels never decide column geometry`() {
        val anchors = listOf(q(5, 90, 100), q(6, 90, 360),
            q(12, 720, 110), q(14, 720, 390))
        assertFalse(PdfColumnLayoutEvidence.hasTwoPrintedColumns(
            anchors, 1200, 55, 790
        ))
    }

    @Test
    fun `left footer number and chart labels must not invalidate actual columns`() {
        val anchors = listOf(
            q(1, 57, 82), q(2, 57, 288), q(3, 57, 607),
            q(4, 315, 82), q(5, 315, 508),
            q(1, 57, 755), // a footer number sharing the printed left margin
            q(8, 95, 298), q(9, 95, 313) // inset diagram labels
        )
        assertTrue(PdfColumnLayoutEvidence.hasTwoPrintedColumns(
            anchors, 623, 45, 775
        ))
    }

}
