package com.example.util

/**
 * A column begins at its PRINTED question numbers, not at a fixed percentage
 * of the paper width. In common exam layouts the right margin starts around
 * 0.50–0.55 of the page; requiring x >= 0.56 dropped that entire column.
 *
 * Never infer two columns merely from graph/axis numbers. For strong evidence
 * both x clusters must contain vertically ordered, globally consecutive
 * question starts. Other pages can still fall back to pixel gutter analysis.
 */
internal object PdfColumnLayoutEvidence {
    fun hasTwoPrintedColumns(
        anchors: List<QuestionNumberAnchor>,
        pageWidth: Int,
        bodyTop: Int,
        bodyBottom: Int
    ): Boolean {
        if (pageWidth < 100 || anchors.size < 3 || bodyBottom <= bodyTop) return false
        val left = anchors.filter {
            it.top in bodyTop until bodyBottom &&
                it.left in (pageWidth * 0.055f).toInt()..(pageWidth * 0.26f).toInt()
        }.sortedBy { it.top }
        val right = anchors.filter {
            it.top in bodyTop until bodyBottom &&
                it.left in (pageWidth * 0.40f).toInt()..(pageWidth * 0.80f).toInt()
        }.sortedBy { it.top }
        if (left.isEmpty() || right.isEmpty() || left.size + right.size < 3) return false

        fun runsInOrder(list: List<QuestionNumberAnchor>) =
            list.zipWithNext().all { (first, next) ->
                first.number > 0 && next.number == first.number + 1 &&
                    next.top > first.top
            }
        return runsInOrder(left) && runsInOrder(right) &&
            right.first().number == left.last().number + 1 &&
            right.first().left - left.first().left > pageWidth * .23f
    }
}
