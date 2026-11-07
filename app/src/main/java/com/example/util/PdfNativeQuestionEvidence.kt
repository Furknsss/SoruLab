package com.example.util

/**
 * A coherent printed two-column 1..N question sequence in the native PDF is
 * stronger evidence than loosely recognized choice letters.
 *
 * Many PDFBox text layers emit A, B, C, D, E choices as separate, re-ordered
 * glyph lines; requiring three recognized option labels suppressed genuine
 * questions in otherwise fully selectable books.
 */
internal object PdfNativeQuestionEvidence {
    fun trustNativeQuestionStarts(
        nativeAnchorCount: Int,
        wholePageOcrAnchorCount: Int,
        hasTwoPrintedColumns: Boolean,
        hasNativeChoiceEvidence: Boolean
    ): Boolean =
        nativeAnchorCount >= 2 && (
            hasTwoPrintedColumns ||
                (hasNativeChoiceEvidence &&
                    nativeAnchorCount >= wholePageOcrAnchorCount)
            )

    /**
     * Avoid expensive independent page OCR when a selectable PDF's printed
     * question run already has geometrically matching image OCR evidence.
     * Printed answer strips still require the separate image reader when the
     * primary reader did not recognize them. Never skip scanned pages.
     */
    fun canSkipDetailedPageReaders(
        sourceBackedPage: Boolean,
        nativeAnchors: List<QuestionNumberAnchor>,
        primaryOcrAnchors: List<QuestionNumberAnchor>,
        pageWidth: Int,
        pageHeight: Int,
        needsIndependentAnswerVerification: Boolean
    ): Boolean = !needsIndependentAnswerVerification && (
        sourceBackedPage || hasIndependentSpatialSupport(
            nativeAnchors, primaryOcrAnchors, pageWidth, pageHeight
        )
    )

    /**
     * Selectable PDFs sometimes contain an exact printed question run while
     * ML Kit adds unrelated chart numbers. Native evidence may be rejected
     * by the old raw-count comparison (4 real native vs 7 noisy OCR).
     *
     * Use the native run ONLY after spatially confirming at least two
     * distinct printed numbers in the independent image reading and at least
     * half of the native starts. A PDF text layer alone is not sufficient.
     */
    fun hasIndependentSpatialSupport(
        native: List<QuestionNumberAnchor>,
        imageOcr: List<QuestionNumberAnchor>,
        pageWidth: Int,
        pageHeight: Int
    ): Boolean {
        if (pageWidth <= 0 || pageHeight <= 0 ||
            native.size < 3 || imageOcr.size < 2 ||
            native.map { it.number }.distinct().size != native.size
        ) return false
        val xTolerance = maxOf(8f, pageWidth * .026f)
        val yTolerance = maxOf(11f, pageHeight * .018f)
        val supported = native.count { original ->
            original.number in 1..200 &&
                original.left in 0 until pageWidth &&
                original.top in 0 until pageHeight &&
                imageOcr.any { ocr ->
                    ocr.number == original.number &&
                        kotlin.math.abs(ocr.left - original.left) <= xTolerance &&
                        kotlin.math.abs(ocr.top - original.top) <= yTolerance
                }
        }
        return supported >= 2 && supported * 2 >= native.size
    }
}
