package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfMarginRecoveryPolicyTest {
    private fun start(number: Int, x: Int = if (number < 3) 100 else 900, y: Int = 120 + number * 210) =
        QuestionNumberAnchor(number, x, y, x + 44, y + 46)

    @Test fun independentCompleteRunSkipsRedundantMarginReaders() {
        val primary = (1..5).map { start(it) }
        val paddle = (1..5).map { n ->
            val it = start(n)
            it.copy(left = it.left + 8, top = it.top + 10)
        }
        assertTrue(PdfMarginRecoveryPolicy.canSkipMarginPass(primary, paddle, 1600, 2200, (1..5).toSet()))
    }

    @Test fun missingLastQuestionDoesNotSkipIndependentRecovery() {
        assertFalse(PdfMarginRecoveryPolicy.canSkipMarginPass(
            (1..4).map { start(it) }, (1..3).map { start(it) }, 1600, 2200, (1..4).toSet()
        ))
    }

    @Test fun duplicateOrDiagramNumberDoesNotSkipRecovery() {
        assertFalse(PdfMarginRecoveryPolicy.canSkipMarginPass(
            (1..4).map { start(it) } + start(2, 700, 700),
            (1..5).map { start(it) }, 1600, 2200, (1..5).toSet()
        ))
    }

    @Test fun differentPhysicalLocationsKeepThirdReader() {
        assertFalse(PdfMarginRecoveryPolicy.canSkipMarginPass(
            (1..4).map { start(it) },
            (1..4).map { n -> start(n, x = 700) },
            1600, 2200, (1..4).toSet()
        ))
    }

    @Test fun ambiguousShortOrUnusualNumberingNeedsRecovery() {
        assertFalse(PdfMarginRecoveryPolicy.canSkipMarginPass(
            (1..3).map { start(it) }, (1..3).map { start(it) }, 1600, 2200, (1..3).toSet()
        ))
        assertFalse(PdfMarginRecoveryPolicy.canSkipMarginPass(
            (5..8).map { start(it) }, (5..8).map { start(it) }, 1600, 2200, (1..4).toSet()
        ))
    }

    @Test fun invalidImageBoundsPreventFastPath() {
        assertFalse(PdfMarginRecoveryPolicy.canSkipMarginPass(
            (1..4).map { start(it) },
            (1..4).map { n -> start(n, x = -5) },
            1600, 2200, (1..4).toSet()
        ))
    }

    @Test fun noIndependentPrintedAnswerStripKeepsThirdReader() {
        val anchors = (1..4).map { start(it) }
        assertFalse(PdfMarginRecoveryPolicy.canSkipMarginPass(
            anchors, anchors, 1600, 2200, emptySet()
        ))
    }
}
