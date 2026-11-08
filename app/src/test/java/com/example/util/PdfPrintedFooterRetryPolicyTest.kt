package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPrintedFooterRetryPolicyTest {
    private fun keys(letters: String) =
        letters.mapIndexed { index, char -> (index + 1) to char.toString() }.toMap()
    private fun image(letters: String) =
        PdfHorizontalAnswerGrid(keys(letters), top = 1900, bottom = 2100)

    @Test fun completeIndependentPrintedAndImageAgreementSkipsDuplicateFooterOcr() {
        assertFalse(PdfPrintedFooterRetryPolicy.shouldRetry(
            nativeKeys = keys("EACD"),
            primaryImageGrids = listOf(image("EACD")),
            primaryHasChoiceText = true
        ))
    }

    @Test fun oneWrongLetterRequiresHighResolutionSecondVote() {
        assertTrue(PdfPrintedFooterRetryPolicy.shouldRetry(
            nativeKeys = keys("EACD"),
            primaryImageGrids = listOf(image("EABD")),
            primaryHasChoiceText = true
        ))
    }

    @Test fun nativeTwelveCellsButFourImageCellsRequiresRecovery() {
        assertTrue(PdfPrintedFooterRetryPolicy.shouldRetry(
            nativeKeys = keys("ABCDEABCDEAB"),
            primaryImageGrids = listOf(image("ABCD")),
            primaryHasChoiceText = true
        ))
    }

    @Test fun noIndependentImageGridCannotCertifyNativeAnswerKeys() {
        assertTrue(PdfPrintedFooterRetryPolicy.shouldRetry(
            nativeKeys = keys("EACD"),
            primaryImageGrids = emptyList(),
            primaryHasChoiceText = true
        ))
    }

    @Test fun scannedPagesAndAmbiguousImageGridsKeepRecoveryPass() {
        assertTrue(PdfPrintedFooterRetryPolicy.shouldRetry(
            nativeKeys = emptyMap(),
            primaryImageGrids = listOf(image("EACD")),
            primaryHasChoiceText = true
        ))
        assertTrue(PdfPrintedFooterRetryPolicy.shouldRetry(
            nativeKeys = keys("EACD"),
            primaryImageGrids = listOf(image("EACD"), image("EBAB")),
            primaryHasChoiceText = true
        ))
    }
}
