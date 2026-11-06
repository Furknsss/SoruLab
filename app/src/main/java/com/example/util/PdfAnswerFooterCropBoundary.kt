package com.example.util

/**
 * The first digit of a printed bottom answer grid is a hard, image-backed
 * exclusion boundary. A fixed seven-pixel gap accidentally amputates the
 * last E) option on dense real textbook pages.
 *
 * Stop a tiny raster gap BEFORE the number's bounding box, not 7 pixels
 * before it. No answer-grid pixels are intentionally included in the crop.
 * The source geometry audit still rejects any merged or uncertain crop.
 */
internal object PdfAnswerFooterCropBoundary {
    fun lastQuestionBottomExclusive(
        firstPrintedAnswerTop: Int,
        pageHeight: Int
    ): Int {
        if (pageHeight <= 0) return 0
        val gap = maxOf(1, kotlin.math.round(pageHeight * .001f).toInt())
        return (firstPrintedAnswerTop - gap).coerceIn(0, pageHeight)
    }
}
