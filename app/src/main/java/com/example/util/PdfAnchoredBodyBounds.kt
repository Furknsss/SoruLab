package com.example.util

import kotlin.math.roundToInt

/**
 * Visible OCR anchors outrank broad top/bottom margin guesses.
 * This is publisher-independent; printed answer-grid geometry remains a hard
 * exclusion boundary, so a recovered last E choice cannot swallow the key.
 */
internal object PdfAnchoredBodyBounds {
    fun firstQuestionTop(
        pageHeight: Int,
        defaultTop: Int,
        numbers: List<QuestionNumberAnchor>
    ): Int {
        if (pageHeight <= 0) return defaultTop
        val first = numbers.asSequence()
            .filter { it.number in 1..200 && it.bottom > it.top }
            .filter { it.top >= (pageHeight * 0.025f).roundToInt() && it.top < pageHeight }
            .minOfOrNull { it.top } ?: return defaultTop
        val padding = (pageHeight * 0.012f).roundToInt()
        return minOf(defaultTop, (first - padding).coerceAtLeast(0))
    }

    fun lastOptionBottom(
        pageHeight: Int,
        defaultBottom: Int,
        options: List<QuestionOptionAnchor>,
        answerGridTop: Int?
    ): Int {
        if (pageHeight <= 0) return defaultBottom
        val last = options.asSequence()
            .filter { it.option.equals("E", ignoreCase = true) && it.bottom > it.top }
            .filter { it.bottom > defaultBottom && it.bottom < pageHeight * 0.99f }
            .filter { e ->
                // An isolated E could be an answer-table cell. Require nearby
                // other printed choices before extending the question body.
                options.count { other ->
                    other.option.uppercase() in setOf("A", "B", "C", "D") &&
                        other.top <= e.top &&
                        other.bottom >= e.top - pageHeight * 0.30f
                } >= 2
            }
            .maxByOrNull { it.bottom } ?: return defaultBottom
        val printedKeyLimit = answerGridTop?.takeIf { it in 1 until pageHeight }
            ?.let { PdfAnswerFooterCropBoundary.lastQuestionBottomExclusive(it, pageHeight) }
            ?: pageHeight
        val extended = (last.bottom + (pageHeight * 0.008f).roundToInt())
            .coerceAtMost((pageHeight * 0.99f).roundToInt())
            .coerceAtMost(printedKeyLimit)
        return maxOf(defaultBottom, extended)
    }
}
