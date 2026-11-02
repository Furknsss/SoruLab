package com.example.util

/**
 * Conservative boundaries for printed-number-anchored question crops.
 *
 * A source question must not inherit whitespace before its printed number
 * nor the next question below its A-E options. Coordinates are PDF-render pixels.
 */
internal object PdfQuestionCropBounds {
    data class Band(val top: Int, val bottom: Int)

    fun forAnchor(
        anchorTop: Int,
        nextAnchorTop: Int?,
        lastOptionBottom: Int?,
        bodyTop: Int,
        bodyBottom: Int,
        pageHeight: Int
    ): Band? {
        if (anchorTop !in bodyTop until bodyBottom || bodyBottom <= bodyTop + 1) return null
        val beforeNumber = (pageHeight * 0.005f).toInt().coerceIn(6, 18)
        val afterOptions = (pageHeight * 0.010f).toInt().coerceIn(10, 35)
        val top = (anchorTop - beforeNumber).coerceAtLeast(bodyTop)
        val nextLimit = nextAnchorTop?.minus(beforeNumber)
            ?.coerceAtMost(bodyBottom)
            ?: bodyBottom
        // When OCR has found the options, they mark the actual end of the
        // question, even if OCR missed the following printed question number.
        val end = if (lastOptionBottom != null && lastOptionBottom >= anchorTop) {
            minOf(nextLimit, lastOptionBottom + afterOptions)
        } else {
            nextLimit
        }.coerceAtMost(bodyBottom)
        return Band(top, end).takeIf { it.bottom - it.top >= 55 }
    }
}
