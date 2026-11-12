package com.example.ui.screens

/** Contiguous reading order: down the left column, then down the right. */
internal data class TestPagePacking(val columns: List<List<Int>>)

/**
 * Packs measured content, never crop coordinates or a fixed question count.
 * Among plans with the fewest pages, prefer evenly filled pages and columns.
 * An unusually tall question gets a scrollable page instead of being shrunk.
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
    fun fits(start: Int, end: Int) = end - start <= 1 || height(start, end) <= availableHeight

    data class Plan(val pages: List<TestPagePacking>, val penalty: Double)
    val plans = arrayOfNulls<Plan>(heights.size + 1)
    plans[heights.size] = Plan(emptyList(), 0.0)
    for (start in heights.lastIndex downTo 0) {
        var best: Plan? = null
        for (end in start + 1..heights.size) {
            var bestColumns: List<List<Int>>? = null
            var bestPenalty = Double.POSITIVE_INFINITY
            val splits = if (columnCount == 1) end..end else
                if (end - start == 1) end..end else start + 1 until end
            for (split in splits) {
                if (!fits(start, split) || !fits(split, end)) continue
                // Tall questions must stand alone, with vertical scrolling available.
                if (end - start > 1 &&
                    (height(start, split) > availableHeight || height(split, end) > availableHeight)) continue
                val left = height(start, split).toDouble() / availableHeight
                val right = if (columnCount == 2) height(split, end).toDouble() / availableHeight else left
                val penalty = (1 - left).let { it * it } +
                    (1 - right).let { it * it } + (left - right).let { it * it }
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
