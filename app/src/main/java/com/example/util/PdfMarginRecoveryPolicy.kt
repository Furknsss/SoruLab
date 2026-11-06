package com.example.util

import kotlin.math.abs

/**
 * Some scanned pages already have the same complete, numbered question run
 * independently recognized by ML Kit and Paddle. Reading two enlarged margin
 * crops for a third time is redundant on those pages.
 *
 * Only the unambiguous 1..N sequence with four or more starts may take this
 * fast path. All partial, divergent, duplicated, or unusual numbering keeps
 * the original three-reader recovery. This never changes crop or answer rules.
 */
internal object PdfMarginRecoveryPolicy {
    fun canSkipMarginPass(
        primary: List<QuestionNumberAnchor>,
        paddle: List<QuestionNumberAnchor>,
        pageWidth: Int,
        pageHeight: Int
    ): Boolean {
        if (pageWidth <= 0 || pageHeight <= 0 ||
            primary.size < 4 || primary.size != paddle.size ||
            primary.size > 30) return false
        val expectedNumbers = (1..primary.size).toList()
        if (primary.map { it.number }.sorted() != expectedNumbers ||
            paddle.map { it.number }.sorted() != expectedNumbers) return false

        val maxXDelta = maxOf(8f, pageWidth * 0.025f)
        val maxYDelta = maxOf(12f, pageHeight * 0.018f)
        val byPaddleNumber = paddle.associateBy { it.number }
        return primary.all { anchor ->
            val witness = byPaddleNumber[anchor.number] ?: return@all false
            anchor.left >= 0 && anchor.right <= pageWidth &&
                anchor.top >= 0 && anchor.bottom <= pageHeight &&
                witness.left >= 0 && witness.right <= pageWidth &&
                witness.top >= 0 && witness.bottom <= pageHeight &&
                anchor.right > anchor.left && anchor.bottom > anchor.top &&
                witness.right > witness.left && witness.bottom > witness.top &&
                abs(anchor.left - witness.left) <= maxXDelta &&
                abs(anchor.top - witness.top) <= maxYDelta
        }
    }
}
