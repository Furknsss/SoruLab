package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAnswerFooterCropBoundaryTest {
    @Test fun actualSourceFinalChoiceSurvivesWithoutLeakingAnswerIndex() {
        // Independent PDF-source geometry measured on a textbook page:
        // last D/E choice bottom=753.12; first key numeral top=758.46.
        val bottom = PdfAnswerFooterCropBoundary.lastQuestionBottomExclusive(
            firstPrintedAnswerTop = 758, pageHeight = 807
        )
        assertTrue("The final E) glyph must remain inside", bottom > 754)
        assertTrue("The answer index must remain outside", bottom < 758)
        assertEquals(757, bottom)
    }

    @Test fun boundaryScalesToRenderedAndroidBitmap() {
        val bottom = PdfAnswerFooterCropBoundary.lastQuestionBottomExclusive(
            firstPrintedAnswerTop = 2070, pageHeight = 2200
        )
        assertTrue(bottom in 2067..2069)
        assertTrue(bottom < 2070)
    }

    @Test fun noNegativeOrOutOfBoundsRectangles() {
        assertEquals(0, PdfAnswerFooterCropBoundary.lastQuestionBottomExclusive(0, 807))
        assertEquals(807, PdfAnswerFooterCropBoundary.lastQuestionBottomExclusive(10000, 807))
        assertEquals(0, PdfAnswerFooterCropBoundary.lastQuestionBottomExclusive(7, 0))
    }
}
