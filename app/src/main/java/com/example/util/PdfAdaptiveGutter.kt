package com.example.util

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Page-relative pixel search region for the actual between-column white gutter. */
internal data class PdfGutterSearchWindow(
    val start: Int,
    val end: Int,
    val preferredX: Int
)

/**
 * Do not hard-code the gutter at 50% of page width. For a trustworthy printed
 * two-column number sequence, the right-hand question margin provides a strong
 * local search hint. On image-only/scanned pages search a wider central region.
 * All dimensions are normalized to the page, not a book or publisher.
 */
internal object PdfAdaptiveGutter {
    fun window(
        width: Int,
        anchors: List<QuestionNumberAnchor>,
        bodyTop: Int,
        bodyBottom: Int
    ): PdfGutterSearchWindow {
        val safeWidth = width.coerceAtLeast(100)
        val verified = PdfColumnLayoutEvidence.findTwoPrintedColumnStarts(
            anchors, safeWidth, bodyTop, bodyBottom
        )
        val preferred = (verified?.second?.minus((safeWidth * .025f).roundToInt())
            ?: safeWidth / 2).coerceIn(
                (safeWidth * .24f).roundToInt(),
                (safeWidth * .80f).roundToInt()
            )
        val radius = (safeWidth * (if (verified != null) .11f else .18f))
            .roundToInt().coerceAtLeast(8)
        val start = max((safeWidth * .20f).roundToInt(), preferred - radius)
        val end = min((safeWidth * .84f).roundToInt(), preferred + radius)
        return PdfGutterSearchWindow(start, max(start + 8, end), preferred)
    }
}
