package com.example.util

import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt

/** Pixel rectangle for one page column, in actual rendered bitmap coordinates. */
internal data class PdfCropColumn(val left: Int, val right: Int, val index: Int)

/**
 * A textbook's right printed question number often sits immediately beside
 * the center dividing line. A gutter guessed from pixels may therefore start
 * AFTER the number, silently discarding every question in that column.
 *
 * Recover the column start from printed-number geometry whenever possible.
 * Neither page number nor publisher name is used; image-only scans retain the
 * conservative pixel-gutter fallback.
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
        val safeGutter = gutterX.coerceIn((width * .44f).roundToInt(),
            (width * .56f).roundToInt())
        val margin = gutterMargin.coerceIn(2, width / 12)
        val leftEnd = (safeGutter - margin).coerceAtLeast(outside + 120)

        val rightPrintedNumbers = anchors.filter { anchor ->
            anchor.top in bodyTop until bodyBottom &&
                anchor.number in 1..200 &&
                anchor.left in (width * .46f).roundToInt()..
                    (width * .74f).roundToInt()
        }
        val nearestPrintedRight = rightPrintedNumbers
            .minByOrNull { it.left }?.left
        val startFromNumbers = nearestPrintedRight?.minus(
            (width * .012f).roundToInt().coerceAtLeast(6)
        )
        val rightStart = min(
            safeGutter + margin,
            startFromNumbers ?: (safeGutter + margin)
        ).coerceAtLeast(leftEnd + 4)
            .coerceAtMost(width - outside - 120)

        return listOf(
            PdfCropColumn(outside, min(leftEnd, rightStart - 4), 0),
            PdfCropColumn(rightStart, width - outside, 1)
        )
    }
}
