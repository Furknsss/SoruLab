package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfNativePageFastPathTest {
    private val positions = (1..6).map { n ->
        val x = if (n < 4) 120 else 900
        QuestionNumberAnchor(n, x, 160 + (n % 3) * 400, x + 28, 194 + (n % 3) * 400)
    }
    private val answers = (1..6).associateWith { "ABCDE"[(it - 1) % 5].toString() }
    private fun candidate(
        anchors: List<QuestionNumberAnchor> = positions,
        keys: Map<Int, String> = answers,
        choices: Boolean = true,
        testIdentity: Boolean = true,
        textLayer: Boolean = true,
        height: Int = 2200
    ) = PdfNativePageFastPath.canTry(
        anchors, keys, choices, testIdentity, textLayer, 1600, height
    )

    @Test fun strongNativePageWithCompleteFooterCanAttemptFasterImageVote() {
        assertTrue(candidate())
        assertTrue(PdfNativePageFastPath.independentFooterAgrees(answers, answers))
    }

    @Test fun noQuestionStartOrMissingAnswerPreventsShortcut() {
        assertFalse(candidate(anchors = positions.dropLast(1)))
        assertFalse(candidate(keys = answers - 6))
    }

    @Test fun graphNumbersOrDuplicatePrintNumbersPreventShortcut() {
        assertFalse(candidate(anchors = positions + positions.first().copy(top = 1800)))
        assertFalse(candidate(anchors = positions.mapIndexed { index, a ->
            if (index == 5) a.copy(number = 9) else a
        }))
    }

    @Test fun missingChoiceMarkerAndTextRejectRasterWatermark() {
        assertFalse(candidate(choices = false))
        assertFalse(candidate(testIdentity = false))
        assertFalse(candidate(textLayer = false))
    }

    @Test fun invalidGeometryPreventsShortcut() {
        assertFalse(candidate(anchors = positions.mapIndexed { index, a ->
            if (index == 0) a.copy(left = -2) else a
        }))
        assertFalse(candidate(height = 100))
    }

    @Test fun mismatchedImageReadingDoesNotCountAsVerified() {
        assertFalse(PdfNativePageFastPath.independentFooterAgrees(answers, answers + (1 to "B")))
        assertFalse(PdfNativePageFastPath.independentFooterAgrees(answers, answers - 6))
        assertFalse(PdfNativePageFastPath.independentFooterAgrees(answers, emptyMap()))
    }

    @Test fun invalidOptionLettersAreNotAccepted() {
        assertFalse(candidate(keys = answers + (1 to "F")))
    }
}
