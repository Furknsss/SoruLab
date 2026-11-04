package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfCropGeometryAuditTest {
    private fun source(n: Int, x: Int, y: Int) =
        QuestionNumberAnchor(n, x, y, x + 16, y + 20)

    @Test fun twoPrintedStartsInSameCropIsRejected() {
        val anchors = listOf(source(7, 100, 140), source(8, 100, 790))
        val crop = PdfQuestionCropEvidence(7, listOf(.045f, .04f, .45f, .45f))
        val problems = PdfCropGeometryAudit.issues(crop, anchors, 1200, 2400)
        assertTrue(PdfCropIssue.MULTIPLE_PRINTED_QUESTIONS_IN_ONE_CROP in problems)
    }

    @Test fun misplacedQuestionIdentityIsRejected() {
        val anchors = listOf(source(7, 100, 140), source(8, 100, 790))
        val crop = PdfQuestionCropEvidence(1, listOf(.04f, .04f, .27f, .45f))
        val problems = PdfCropGeometryAudit.issues(crop, anchors, 1200, 2400)
        assertTrue(PdfCropIssue.WRONG_OR_MISSING_PRINTED_START in problems)
    }

    @Test fun oneCompleteNumberedCropPassesGeometryCheck() {
        val anchors = listOf(source(7, 100, 140), source(8, 100, 790))
        val crop = PdfQuestionCropEvidence(7, listOf(.045f, .04f, .31f, .45f))
        assertEquals(emptySet<PdfCropIssue>(),
            PdfCropGeometryAudit.issues(crop, anchors, 1200, 2400))
    }

    @Test fun reversedBoundsAreRejected() {
        val crop = PdfQuestionCropEvidence(1, listOf(.8f, .2f, .1f, .6f))
        assertEquals(setOf(PdfCropIssue.INVALID_CROP_BOUNDS),
            PdfCropGeometryAudit.issues(crop, emptyList(), 1200, 2400))
    }

    @Test fun twoPhysicalStartsWithSamePrintedNumberAreStillMerged() {
        // Two tests can restart at question 1 on the same page.
        val anchors = listOf(source(1, 100, 140), source(1, 100, 790))
        val crop = PdfQuestionCropEvidence(1, listOf(.045f, .04f, .45f, .45f))
        assertTrue(
            PdfCropIssue.MULTIPLE_PRINTED_QUESTIONS_IN_ONE_CROP in
                PdfCropGeometryAudit.issues(crop, anchors, 1200, 2400)
        )
    }

    @Test fun repeatedOcrReadingOfSamePrintedGlyphIsNotAnExtraQuestion() {
        val anchors = listOf(source(7, 100, 140), source(7, 105, 145))
        val crop = PdfQuestionCropEvidence(7, listOf(.045f, .04f, .31f, .45f))
        assertEquals(emptySet<PdfCropIssue>(),
            PdfCropGeometryAudit.issues(crop, anchors, 1200, 2400))
    }

    @Test fun unknownPrintedIdentityCannotPassWhenSourceNumberIsVisible() {
        val crop = PdfQuestionCropEvidence(0, listOf(.045f, .04f, .31f, .45f))
        val issues = PdfCropGeometryAudit.issues(
            crop, listOf(source(7, 100, 140)), 1200, 2400
        )
        assertTrue(PdfCropIssue.WRONG_OR_MISSING_PRINTED_START in issues)
    }

}
