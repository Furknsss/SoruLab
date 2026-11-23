package com.example.ui.screens

/** Contiguous reading order: down the left column, then down the right. */
internal data class TestPagePacking(val columns: List<List<Int>>)

/**
 * Packs measured content, never crop coordinates or a fixed question count.
 * Among plans with the fewest pages, prefer balanced full pages (front-loaded so any
 * unavoidable remainder lands on the final page) and avoid 1-question sparse pages.
 * Moderately tall single questions (up to 1.45x column height) occupy their own column
 * alongside normal questions in the other column; only truly oversized questions
 * (> 1.45x column height) stand alone on a dedicated scrollable page.
 */
internal fun packMeasuredTestPages(
    heights: List<Int>,
    availableHeight: Int,
    gap: Int,
    columnCount: Int
): List<TestPagePacking> {
    require(availableHeight > 0 && gap >= 0 && columnCount in 1..2)
    require(heights.all { it >= 0 })
    if (heights.isEmpty()) return listOf(TestPagePacking(List(columnCount) { emptyList() }))

    val prefix = LongArray(heights.size + 1)
    heights.forEachIndexed { index, height -> prefix[index + 1] = prefix[index] + height }
    fun height(start: Int, end: Int): Long =
        if (start == end) 0 else prefix[end] - prefix[start] + (end - start - 1L) * gap

    // A single question up to 1.45x viewport height can occupy its own column on a 2-column
    // page without forcing the opposite column to be completely empty. Only truly huge
    // questions (> 1.45x) get their own dedicated solo scrollable page.
    val soloPageThreshold = (availableHeight * 1.45).toLong()
    fun columnFits(start: Int, end: Int, isSoloPage: Boolean): Boolean {
        val count = end - start
        if (count <= 0) return true
        val h = height(start, end)
        if (count == 1) return isSoloPage || h <= soloPageThreshold
        return h <= availableHeight
    }

    data class Plan(val pages: List<TestPagePacking>, val penalty: Double)
    val plans = arrayOfNulls<Plan>(heights.size + 1)
    plans[heights.size] = Plan(emptyList(), 0.0)
    for (start in heights.lastIndex downTo 0) {
        var best: Plan? = null
        for (end in start + 1..heights.size) {
            val isSoloPage = end - start == 1
            val isLastPage = end == heights.size
            var bestColumns: List<List<Int>>? = null
            var bestPenalty = Double.POSITIVE_INFINITY
            val splits = if (columnCount == 1) end..end else
                if (isSoloPage) end..end else start + 1 until end
            for (split in splits) {
                if (!columnFits(start, split, isSoloPage) || !columnFits(split, end, isSoloPage)) continue

                val leftRaw = height(start, split).toDouble() / availableHeight
                val rightRaw = if (columnCount == 2) height(split, end).toDouble() / availableHeight else leftRaw
                val leftClamped = leftRaw.coerceAtMost(1.0)
                val rightClamped = rightRaw.coerceAtMost(1.0)

                val fillWeight = if (isLastPage) {
                    0.85
                } else {
                    1.0 + 0.02 * (heights.size - end).toDouble() / heights.size
                }
                val fillDeficit = fillWeight * (
                    (1.0 - leftClamped).let { it * it } +
                        (1.0 - rightClamped).let { it * it }
                )
                val imbalance = if (columnCount == 2) {
                    1.2 * (leftClamped - rightClamped).let { it * it }
                } else {
                    0.0
                }
                val mildOverflow = (leftRaw - 1.0).coerceAtLeast(0.0).let { it * it * 0.5 } +
                    (rightRaw - 1.0).coerceAtLeast(0.0).let { it * it * 0.5 }

                val emptyColumnPenalty = if (columnCount == 2 && split == end) {
                    if (isLastPage) 3.5 else 50.0
                } else {
                    0.0
                }
                val sparseNonFinalPenalty = if (!isLastPage && columnCount == 2 &&
                    leftClamped < 0.45 && rightClamped < 0.45
                ) {
                    8.0
                } else {
                    0.0
                }

                val penalty = fillDeficit + imbalance + mildOverflow + emptyColumnPenalty + sparseNonFinalPenalty
                if (penalty < bestPenalty) {
                    bestPenalty = penalty
                    bestColumns = if (columnCount == 1) listOf((start until end).toList()) else
                        listOf((start until split).toList(), (split until end).toList())
                }
            }
            if (bestColumns == null) {
                // Adding more positive-height content cannot restore a fit.
                break
            }
            val tail = plans[end] ?: continue
            val candidate = Plan(listOf(TestPagePacking(bestColumns)) + tail.pages, bestPenalty + tail.penalty)
            val previous = best
            if (previous == null || candidate.pages.size < previous.pages.size ||
                (candidate.pages.size == previous.pages.size && candidate.penalty < previous.penalty)) {
                best = candidate
            }
        }
        plans[start] = best
    }
    return checkNotNull(plans[0]).pages
}
