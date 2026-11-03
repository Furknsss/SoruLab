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
}
