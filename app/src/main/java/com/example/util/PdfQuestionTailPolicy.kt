package com.example.util

/**
 * A short last A-E option is sometimes discarded from the pixel projection
 * as noise. Do not let an absent OCR E) turn into a permanently amputated
 * question picture: preserve the whole safe column band.
 *
 * hardEnd is ALREADY bounded by the next printed question OR a verified answer
 * strip. This policy must never increase that hard boundary.
 */
internal object PdfQuestionTailPolicy {
    fun endExclusive(
        hasNextPrintedQuestion: Boolean,
        completeOptionsLocated: Boolean,
        proposedEnd: Int,
        hardEnd: Int
    ): Int {
        if (hardEnd <= 0) return 0
        return if (hasNextPrintedQuestion || !completeOptionsLocated) {
            hardEnd
        } else proposedEnd.coerceIn(1, hardEnd)
    }
}
