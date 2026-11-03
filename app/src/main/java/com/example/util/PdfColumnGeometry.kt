package com.example.util

import kotlin.math.min
import kotlin.math.roundToInt

/** Pixel rectangle for one page column, in actual rendered bitmap coordinates. */
internal data class PdfCropColumn(val left: Int, val right: Int, val index: Int)

/**
 * Builds column crop bounds from this page's printed question numbers and
 * measured whitespace gutter. No publisher/title-specific X coordinates.
 *
 * A number may start slightly to the LEFT of a pixel-based gutter. Recover
 * the entire question number first, then keep the two crop areas disjoint.
 * For scans where there are no validated vector/OCR runs, fall back to the
 * measured gutter; do not create imaginary question numbers.
 */
internal object PdfColumnGeometry {
    fun columns(
        width: Int,
        gutterX: Int,
        gutterMargin: Int,
        outerMargin: Int,
        isDualColumn: Boolean,
        anchors: List<QuestionNumberAnchor>,
        bodyTop: Int,
        bodyBottom: Int
    ): List<PdfCropColumn> {
        if (width < 300) return emptyList()
        val outside = outerMargin.coerceIn(0, width / 8)
        if (!isDualColumn) return listOf(PdfCropColumn(outside, width - outside, 0))

        // The cropper requires at least 120 pixels per column; on unusually
        // narrow pages do not manufacture two invalid overlapping crops.
        if (width - 2 * outside < 244) {
            return listOf(PdfCropColumn(outside, width - outside, 0))
        }
        val safeGutter = gutterX.coerceIn(outside + 124, width - outside - 120)
        val margin = gutterMargin.coerceIn(2, width / 12)
        val validatedColumns = PdfColumnLayoutEvidence.findTwoPrintedColumnStarts(
            anchors, width, bodyTop, bodyBottom
        )
        val validatedRightStart = validatedColumns?.second
        val edgePadding = (width * .012f).roundToInt().coerceAtLeast(6)
        val inkAwareRight = validatedRightStart?.minus(edgePadding)
        val rightStart = min(
            safeGutter + margin,
            inkAwareRight ?: (safeGutter + margin)
        ).coerceIn(outside + 124, width - outside - 120)
        val leftEnd = min(
            safeGutter - margin,
            rightStart - 6
        ).coerceIn(outside + 120, rightStart - 4)

        return listOf(
            PdfCropColumn(outside, leftEnd, 0),
            PdfCropColumn(rightStart, width - outside, 1)
        )
    }
}
