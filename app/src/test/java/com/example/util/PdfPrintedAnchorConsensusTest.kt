package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPrintedAnchorConsensusTest {
    private fun q(n: Int, x: Int, y: Int) =
        QuestionNumberAnchor(n, x, y, x + 18, y + 17)

    @Test fun missingRightColumnIsRecoveredFromIndependentPass() {
        val full = listOf(q(6, 82, 190), q(7, 82, 690), q(8, 82, 1240),
            q(9, 950, 190), q(10, 950, 990))
        val recovered = PdfPrintedAnchorConsensus.reconcile(
            primary = listOf(full[0], full[1], full[3]),
            secondPass = full, pageWidth = 1800, pageHeight = 2400
        )
        assertEquals(listOf(6, 7, 8, 9, 10), recovered.map { it.number })
    }

    @Test fun figureLabelsWithTightSpacingDoNotOutvoteRealQuestions() {
        val printed = listOf(q(1, 96, 180), q(2, 96, 820),
            q(3, 940, 180), q(4, 940, 1050))
        val figure = (1..7).map { q(it, 282, 650 + it * 34) }
        val recovered = PdfPrintedAnchorConsensus.reconcile(
            primary = printed,
            secondPass = printed + figure,
            pageWidth = 1800, pageHeight = 2400
        )
        assertEquals(listOf(1, 2, 3, 4), recovered.map { it.number })
    }

    @Test fun optionNumbersAndFooterFragmentsAreRejected() {
        val correct = listOf(q(20, 61, 190), q(21, 61, 800),
            q(22, 670, 200), q(23, 670, 970))
        val junk = listOf(q(1, 61, 2260), q(2, 61, 2310), q(5, 271, 1200))
        val recovered = PdfPrintedAnchorConsensus.reconcile(
            primary = correct, secondPass = correct + junk,
            pageWidth = 1200, pageHeight = 2400
        )
        assertEquals(listOf(20, 21, 22, 23), recovered.map { it.number })
    }

    @Test fun scansWithNoTrustworthySequenceStayUnverified() {
        val primary = listOf(q(9, 96, 180))
        val recovered = PdfPrintedAnchorConsensus.reconcile(
            primary, listOf(q(1, 300, 1200), q(7, 320, 1220)),
            1800, 2400
        )
        assertEquals(primary, recovered)
    }
}
