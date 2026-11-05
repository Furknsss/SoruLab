package com.example.util

/**
 * Screens textbook pages that might have been skipped after OCR failed to
 * produce a safe crop. A chapter cover with only a printed "1. ÜNİTE" is NOT
 * a missing question page. Conversely, a printed A-E choice group or a
 * confirmed native question sequence remains a fail-closed review item.
 *
 * A single isolated numeral is insufficient: contents tables, chapter
 * dividers and page decorations have those as well.
 */
internal object PdfQuestionPagePresence {
    fun needsCropReview(
        numberedStarts: List<QuestionNumberAnchor>,
        choiceAnchors: List<QuestionOptionAnchor>,
        independentlyConfirmedNativeQuestionStarts: Boolean
    ): Boolean {
        if (independentlyConfirmedNativeQuestionStarts &&
            numberedStarts.size >= 2) return true
        val choices = choiceAnchors
            .map { it.option.uppercase() }
            .filter { it in listOf("A", "B", "C", "D", "E") }
            .distinct().size
        return choices >= 3 || (numberedStarts.size >= 2 && choices >= 2)
    }
}
