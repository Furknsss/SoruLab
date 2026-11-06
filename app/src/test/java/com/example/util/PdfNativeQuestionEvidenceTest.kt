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

    private fun printed(n: Int, x: Int, y: Int) =
        QuestionNumberAnchor(n, x, y, x + 16, y + 19)

    @Test fun imageCorroboratedNativeStartsSurviveExtraOCRGraphNumbers() {
        val native = listOf(
            printed(1, 95, 160), printed(2, 95, 650),
            printed(3, 95, 1170), printed(4, 95, 1680)
        )
        val noisy = listOf(native[0], native[1], native[2]) +
            (1..7).map { printed(it, 395, 730 + it * 80) }
        assertTrue(PdfNativeQuestionEvidence.hasIndependentSpatialSupport(
            native, noisy, 1800, 2400
        ))
    }

    @Test fun oneMatchingGraphLabelCannotCertifyEntireNativeRun() {
        val native = listOf(
            printed(1, 95, 160), printed(2, 95, 650),
            printed(3, 95, 1170), printed(4, 95, 1680)
        )
        val suspicious = listOf(native.first()) +
            (1..7).map { printed(it, 450, 800 + it * 60) }
        assertFalse(PdfNativeQuestionEvidence.hasIndependentSpatialSupport(
            native, suspicious, 1800, 2400
        ))
    }

    @Test fun repeatedNativeQuestionNumbersAndFarOffLabelsAreNotTrusted() {
        val repeated = listOf(
            printed(1, 82, 190), printed(2, 82, 740),
            printed(1, 920, 195), printed(2, 920, 730)
        )
        assertFalse(PdfNativeQuestionEvidence.hasIndependentSpatialSupport(
            repeated, repeated, 1800, 2400
        ))
        val native = repeated.take(2) + printed(3, 82, 1200)
        val shifted = native.map { it.copy(left = it.left + 250, right = it.right + 250) }
        assertFalse(PdfNativeQuestionEvidence.hasIndependentSpatialSupport(
            native, shifted, 1800, 2400
        ))
    }

    @Test fun knownNativeStartsSkipRepeatedFullPagePaddleAndMarginOcr() {
        val native = listOf(
            printed(1, 95, 160), printed(2, 95, 650),
            printed(3, 95, 1170), printed(4, 95, 1680)
        )
        val primary = native.take(3) +
            (1..6).map { printed(it, 430, 800 + it * 70) }
        assertTrue(
            PdfNativeQuestionEvidence.canSkipDetailedPageReaders(
                sourceBackedPage = false,
                nativeAnchors = native,
                primaryOcrAnchors = primary,
                pageWidth = 1800, pageHeight = 2400,
                needsIndependentAnswerVerification = false
            )
        )
    }

    @Test fun nativePrintedKeyWithoutImageVoteMustStillRunIndependentReader() {
        val native = listOf(
            printed(1, 95, 160), printed(2, 95, 650), printed(3, 95, 1170)
        )
        assertFalse(PdfNativeQuestionEvidence.canSkipDetailedPageReaders(
            sourceBackedPage = true, nativeAnchors = native,
            primaryOcrAnchors = native,
            pageWidth = 1800, pageHeight = 2400,
            needsIndependentAnswerVerification = true
        ))
    }

    @Test fun scannedPagesAndUnconfirmedNativeGeometryKeepDetailedOcr() {
        val native = listOf(
            printed(1, 95, 160), printed(2, 95, 650),
            printed(3, 95, 1170), printed(4, 95, 1680)
        )
        assertFalse(PdfNativeQuestionEvidence.canSkipDetailedPageReaders(
            sourceBackedPage = false, nativeAnchors = emptyList(),
            primaryOcrAnchors = native,
            pageWidth = 1800, pageHeight = 2400,
            needsIndependentAnswerVerification = false
        ))
        assertFalse(PdfNativeQuestionEvidence.canSkipDetailedPageReaders(
            sourceBackedPage = false, nativeAnchors = native,
            primaryOcrAnchors = listOf(printed(1, 440, 164)),
            pageWidth = 1800, pageHeight = 2400,
            needsIndependentAnswerVerification = false
        ))
    }
}
