package com.example.util

import kotlin.math.abs
import kotlin.math.max

/**
 * Detect page-local two-column printed question number runs without knowing
 * its publisher, title, lesson, page size, or a fixed right-column X position.
 *
 * The printed numbers should align by X and increase in reading order within
 * each column (top-down left, then top-down right). Chart axis labels, scattered
 * paragraph numbering, and cover-page numbers usually fail this joint test.
 */
internal object PdfColumnLayoutEvidence {
    fun findTwoPrintedColumnStarts(
        anchors: List<QuestionNumberAnchor>,
        pageWidth: Int,
        bodyTop: Int,
        bodyBottom: Int
    ): Pair<Int, Int>? {
        if (pageWidth < 100 || bodyBottom <= bodyTop || anchors.size < 3) return null
        val candidates = anchors.filter {
            it.number in 1..200 && it.top in bodyTop until bodyBottom &&
                it.left > 0 && it.left < pageWidth &&
                it.bottom > it.top
        }.sortedWith(compareBy(QuestionNumberAnchor::left, QuestionNumberAnchor::top))
        if (candidates.size < 3) return null

        // Align starts with a scale-relative tolerance: works for A4, A5,
        // tablet scans and publisher-specific page margins.
        val alignTolerance = max(4, (pageWidth * .035f).toInt())
        val groups = mutableListOf<MutableList<QuestionNumberAnchor>>()
        for (candidate in candidates) {
            val group = groups.lastOrNull()
            val x = group?.map { it.left }?.average()
            if (x != null && abs(candidate.left - x) <= alignTolerance) {
                group.add(candidate)
            } else {
                groups.add(mutableListOf(candidate))
            }
        }

        fun strongestRuns(items: List<QuestionNumberAnchor>): List<List<QuestionNumberAnchor>> {
            val sorted = items.sortedBy { it.top }
            if (sorted.isEmpty()) return emptyList()
            val minGap = max(6, ((bodyBottom - bodyTop) * .025f).toInt())
            val paths = sorted.indices.map { index ->
                mutableListOf(sorted[index])
            }.toMutableList()
            for (i in sorted.indices) {
                for (j in 0 until i) {
                    if (sorted[i].number == sorted[j].number + 1 &&
                        sorted[i].top - sorted[j].top >= minGap &&
                        paths[j].size + 1 > paths[i].size
                    ) paths[i] = (paths[j] + sorted[i]).toMutableList()
                }
            }
            return paths.distinctBy { run ->
                run.joinToString("|") { "${it.number}@${it.top}" }
            }.sortedWith(
                compareByDescending<List<QuestionNumberAnchor>> { it.size }
                    .thenByDescending { it.last().top - it.first().top }
            ).take(3)
        }

        val runsByGroup = groups.map(::strongestRuns)
        var best: Pair<Int, Int>? = null
        var bestSupport = 0
        for (leftIdx in groups.indices) {
            for (left in runsByGroup[leftIdx]) {
                for (rightIdx in leftIdx + 1 until groups.size) {
                    for (right in runsByGroup[rightIdx]) {
                        if (left.size + right.size < 3) continue
                        val leftX = left.map { it.left }.average().toFloat()
                        val rightX = right.map { it.left }.average().toFloat()
                        if (rightX - leftX < pageWidth * .20f) continue
                        if (right.first().number != left.last().number + 1) continue
                        val support = left.size + right.size
                        if (support > bestSupport) {
                            bestSupport = support
                            best = left.minOf { it.left } to right.minOf { it.left }
                        }
                    }
                }
            }
        }
        return best
    }

    fun hasTwoPrintedColumns(
        anchors: List<QuestionNumberAnchor>,
        pageWidth: Int,
        bodyTop: Int,
        bodyBottom: Int
    ): Boolean = findTwoPrintedColumnStarts(anchors, pageWidth, bodyTop, bodyBottom) != null
}
