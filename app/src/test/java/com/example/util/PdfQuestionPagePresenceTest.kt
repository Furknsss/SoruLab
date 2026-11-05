package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfQuestionPagePresenceTest {
    private fun n(number: Int, x: Int = 80, y: Int = 200) =
        QuestionNumberAnchor(number, x, y, x + 18, y + 20)
    private fun o(letter: String, x: Int = 110, y: Int = 600) =
        QuestionOptionAnchor(letter, x, y, x + 20, y + 18)

    @Test fun chapterDividerAndContentsAreNotIncompleteQuestionPages() {
        // A chapter cover may print "1. ÜNİTE" with no actual A-E choices.
        assertFalse(PdfQuestionPagePresence.needsCropReview(
            listOf(n(1)), emptyList(), false
        ))
        // Contents tables may contain several numeric rows but no choices.
        assertFalse(PdfQuestionPagePresence.needsCropReview(
            listOf(n(1), n(2, y = 340), n(3, y = 480)), emptyList(), false
        ))
        assertFalse(PdfQuestionPagePresence.needsCropReview(
            emptyList(), emptyList(), false
        ))
    }

    @Test fun actualBookPageWithoutRecognizedNumberButFiveChoicesStaysSuspect() {
        assertTrue(PdfQuestionPagePresence.needsCropReview(
            emptyList(),
            listOf(o("A"), o("B"), o("C"), o("D"), o("E")),
            false
        ))
        assertTrue(PdfQuestionPagePresence.needsCropReview(
            listOf(n(6)), listOf(o("A"), o("B"), o("C")), false
        ))
    }

    @Test fun independentlyGroundedNativeQuestionStartsMustNotBeSkipped() {
        assertTrue(PdfQuestionPagePresence.needsCropReview(
            listOf(n(1), n(2, y = 550)), emptyList(), true
        ))
        assertFalse(PdfQuestionPagePresence.needsCropReview(
            listOf(n(1)), emptyList(), true
        ))
    }
}
