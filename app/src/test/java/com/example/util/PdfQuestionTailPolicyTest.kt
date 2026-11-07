package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfQuestionTailPolicyTest {
    @Test fun finalQuestionWithoutCompleteOptionsPreservesEInsideSafeBottom() {
        assertEquals(757, PdfQuestionTailPolicy.endExclusive(
            hasNextPrintedQuestion = false,
            completeOptionsLocated = false,
            proposedEnd = 736,
            hardEnd = 757
        ))
    }

    @Test fun aVerifiedCompleteOptionRunMayStopBeforeAnswerKey() {
        assertEquals(741, PdfQuestionTailPolicy.endExclusive(
            hasNextPrintedQuestion = false,
            completeOptionsLocated = true,
            proposedEnd = 741,
            hardEnd = 757
        ))
    }

    @Test fun nextPrintedQuestionIsHardUpperBound() {
        assertEquals(560, PdfQuestionTailPolicy.endExclusive(
            hasNextPrintedQuestion = true,
            completeOptionsLocated = false,
            proposedEnd = 500,
            hardEnd = 560
        ))
    }

    @Test fun projectedEndIsNeverAllowedPastPrintedKey() {
        assertEquals(757, PdfQuestionTailPolicy.endExclusive(
            hasNextPrintedQuestion = false,
            completeOptionsLocated = true,
            proposedEnd = 900,
            hardEnd = 757
        ))
    }

    @Test fun outOfBoundsPageCannotProduceNegativeBand() {
        assertEquals(0, PdfQuestionTailPolicy.endExclusive(
            hasNextPrintedQuestion = false,
            completeOptionsLocated = false,
            proposedEnd = 100,
            hardEnd = 0
        ))
    }
}
