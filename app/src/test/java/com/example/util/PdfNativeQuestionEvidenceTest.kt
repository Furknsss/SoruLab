package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfNativeQuestionEvidenceTest {
    @Test fun printedColumnsOverrideMissingOptionTextOnSelectablePdf() {
        assertTrue(PdfNativeQuestionEvidence.trustNativeQuestionStarts(
            nativeAnchorCount = 5, wholePageOcrAnchorCount = 1,
            hasTwoPrintedColumns = true, hasNativeChoiceEvidence = false
        ))
        assertTrue(PdfNativeQuestionEvidence.trustNativeQuestionStarts(
            nativeAnchorCount = 6, wholePageOcrAnchorCount = 8,
            hasTwoPrintedColumns = true, hasNativeChoiceEvidence = false
        ))
    }

    @Test fun tableOfContentsWithNumberedParagraphsCannotOverrideNoChoices() {
        assertFalse(PdfNativeQuestionEvidence.trustNativeQuestionStarts(
            nativeAnchorCount = 12, wholePageOcrAnchorCount = 1,
            hasTwoPrintedColumns = false, hasNativeChoiceEvidence = false
        ))
    }

    @Test fun scannedPdfStaysOnImageOcrFallback() {
        assertFalse(PdfNativeQuestionEvidence.trustNativeQuestionStarts(
            nativeAnchorCount = 0, wholePageOcrAnchorCount = 5,
            hasTwoPrintedColumns = false, hasNativeChoiceEvidence = true
        ))
    }

    @Test fun nativeOneColumnRequiresMatchingChoiceEvidence() {
        assertTrue(PdfNativeQuestionEvidence.trustNativeQuestionStarts(
            nativeAnchorCount = 4, wholePageOcrAnchorCount = 2,
            hasTwoPrintedColumns = false, hasNativeChoiceEvidence = true
        ))
    }
}
