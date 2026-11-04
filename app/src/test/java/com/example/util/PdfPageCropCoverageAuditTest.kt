package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageCropCoverageAuditTest {
    private fun anchor(number: Int, x: Int, y: Int) =
        QuestionNumberAnchor(number, x, y, x + 16, y + 20)
    private fun crop(number: Int, t: Float, l: Float, b: Float, r: Float) =
        PdfQuestionCropEvidence(number, listOf(t, l, b, r))
    private val width = 1000
    private val height = 2000

    @Test fun exactlyOneCorrectCropPerPrintedStartPasses() {
        val result = PdfPageCropCoverageAudit.assess(
            listOf(anchor(1, 90, 160), anchor(2, 90, 820),
                anchor(3, 580, 160)),
            listOf(crop(1, .06f, .05f, .3f, .45f),
                crop(2, .38f, .05f, .72f, .45f),
                crop(3, .06f, .54f, .7f, .94f)),
            width, height
        )
        assertTrue(result.isComplete)
        assertEquals(3, result.capturedStartCount)
        assertEquals(0, result.missingStartCount)
        assertTrue(result.unsafeCropIndexes.isEmpty())
    }

    @Test fun missingPrintedQuestionIsNotHiddenByValidNeighboringCrops() {
        val result = PdfPageCropCoverageAudit.assess(
            listOf(anchor(1, 90, 160), anchor(2, 90, 820),
                anchor(3, 580, 160)),
            listOf(crop(1, .06f, .05f, .3f, .45f),
                crop(3, .06f, .54f, .7f, .94f)),
            width, height
        )
        assertEquals(1, result.missingStartCount)
        assertEquals(2, result.capturedStartCount)
        assertFalse(result.isComplete)
    }

    @Test fun twoCropsOfSamePhysicalQuestionCannotPretendToBeTwo() {
        val result = PdfPageCropCoverageAudit.assess(
            listOf(anchor(1, 90, 160)),
            listOf(crop(1, .06f, .05f, .24f, .46f),
                crop(1, .07f, .06f, .28f, .44f)),
            width, height
        )
        assertEquals(1, result.duplicateStartCount)
        assertEquals(setOf(0, 1), result.unsafeCropIndexes)
        assertFalse(result.isComplete)
    }

    @Test fun twoTestsCanBothPrintQuestionOneOnSamePage() {
        val result = PdfPageCropCoverageAudit.assess(
            listOf(anchor(1, 90, 160), anchor(1, 90, 980)),
            listOf(crop(1, .06f, .05f, .28f, .46f),
                crop(1, .46f, .05f, .76f, .46f)),
            width, height
        )
        assertEquals(2, result.sourceStartCount)
        assertTrue(result.isComplete)
    }

    @Test fun repeatedOcrReadingSameGlyphDoesNotManufactureAnExtraStart() {
        val result = PdfPageCropCoverageAudit.assess(
            listOf(anchor(7, 90, 160), anchor(7, 94, 164)),
            listOf(crop(7, .06f, .05f, .28f, .46f)),
            width, height
        )
        assertEquals(1, result.sourceStartCount)
        assertTrue(result.isComplete)
    }

    @Test fun mergedOrMisnumberedCropIsUnsafeEvenIfCountsAppearCorrect() {
        val result = PdfPageCropCoverageAudit.assess(
            listOf(anchor(3, 90, 160), anchor(4, 90, 820)),
            listOf(crop(3, .06f, .05f, .57f, .46f),
                crop(4, .55f, .05f, .9f, .46f)),
            width, height
        )
        assertTrue(0 in result.unsafeCropIndexes)
        assertFalse(result.isComplete)

        val wrong = PdfPageCropCoverageAudit.assess(
            listOf(anchor(3, 90, 160)),
            listOf(crop(9, .06f, .05f, .28f, .46f)),
            width, height
        )
        assertEquals(1, wrong.missingStartCount)
        assertEquals(setOf(0), wrong.unsafeCropIndexes)
    }

    @Test fun zeroSourceAnchorsIsUnknownNotOneHundredPercentComplete() {
        val result = PdfPageCropCoverageAudit.assess(
            emptyList(), listOf(crop(1, .06f, .05f, .28f, .46f)),
            width, height
        )
        assertFalse(result.sourceEvidenceAvailable)
        assertFalse(result.isComplete)
    }

    @Test fun malformedCropBoundsAreUnsafeAndDoNotCaptureQuestion() {
        val result = PdfPageCropCoverageAudit.assess(
            listOf(anchor(1, 90, 160)),
            listOf(crop(1, .8f, .05f, .2f, .46f)),
            width, height
        )
        assertEquals(1, result.missingStartCount)
        assertEquals(setOf(0), result.unsafeCropIndexes)
        assertFalse(result.isComplete)
    }
}
