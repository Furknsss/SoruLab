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

    data class Plan(val pages: List<TestPagePacking>, val penalty: Double, val firstPageFill: Double)
    val plans = arrayOfNulls<Plan>(heights.size + 1)
    plans[heights.size] = Plan(emptyList(), 0.0, -1.0)
    val maxTwoColRawSum = soloPageThreshold * 2L
    for (start in heights.lastIndex downTo 0) {
        var best: Plan? = null
        for (end in start + 1..heights.size) {
            val isSoloPage = end - start == 1
            if (columnCount == 1 && !columnFits(start, end, isSoloPage)) break
            if (columnCount == 2 && !isSoloPage && (prefix[end] - prefix[start]) > maxTwoColRawSum) break

            val isLastPage = end == heights.size
            var bestColumns: List<List<Int>>? = null
            var bestPenalty = Double.POSITIVE_INFINITY
            var bestAvgFill = 0.0
            val splits = if (columnCount == 1) end..end else
                if (isSoloPage) end..end else start + 1 until end
            for (split in splits) {
                if (!columnFits(start, split, isSoloPage) || !columnFits(split, end, isSoloPage)) continue

                val leftRaw = height(start, split).toDouble() / availableHeight
                val rightRaw = if (columnCount == 2) height(split, end).toDouble() / availableHeight else leftRaw
                val leftClamped = leftRaw.coerceAtMost(1.0)
                val rightClamped = rightRaw.coerceAtMost(1.0)
                val pageAvgFill = if (columnCount == 2) (leftClamped + rightClamped) * 0.5 else leftClamped

                val fillWeight = if (isLastPage) {
                    0.90
                } else {
                    1.05 + 0.02 * (heights.size - end).toDouble() / heights.size
                }
                val fillDeficit = fillWeight * (
                    (1.0 - leftClamped).let { it * it } +
                        (1.0 - rightClamped).let { it * it }
                )
                val imbalance = if (columnCount == 2) {
                    1.45 * (leftClamped - rightClamped).let { it * it }
                } else {
                    0.0
                }
                val mildOverflow = (leftRaw - 1.0).coerceAtLeast(0.0).let { it * it * 0.5 } +
                    (rightRaw - 1.0).coerceAtLeast(0.0).let { it * it * 0.5 }

                val emptyColumnPenalty = if (columnCount == 2 && split == end) {
                    if (isLastPage) 4.0 else 50.0
                } else {
                    0.0
                }
                val sparseNonFinalPenalty = if (!isLastPage && columnCount == 2 &&
                    leftClamped < 0.48 && rightClamped < 0.48
                ) {
                    8.5
                } else {
                    0.0
                }
                // Discourage single-column bottom voids (< 52% filled) when rebalancing across pages is possible.
                val bottomVoidPenalty = (0.52 - leftClamped).coerceAtLeast(0.0).let { it * it * 2.2 } +
                    (if (columnCount == 2 && split < end) (0.52 - rightClamped).coerceAtLeast(0.0).let { it * it * 2.2 } else 0.0)

                val penalty = fillDeficit + imbalance + mildOverflow + emptyColumnPenalty +
                    sparseNonFinalPenalty + bottomVoidPenalty
                if (penalty < bestPenalty) {
                    bestPenalty = penalty
                    bestColumns = if (columnCount == 1) listOf((start until end).toList()) else
                        listOf((start until split).toList(), (split until end).toList())
                    bestAvgFill = pageAvgFill
                }
            }
            if (bestColumns == null) {
                // Adding more positive-height content cannot restore a fit.
                break
            }
            val tail = plans[end] ?: continue
            // Smooth page-to-page density transitions so Page 1 isn't 98% jammed while Page 2 is 35% empty.
            val crossPageJumpPenalty = if (tail.firstPageFill >= 0.0) {
                0.35 * (bestAvgFill - tail.firstPageFill).let { it * it }
            } else {
                0.0
            }
            val totalPenalty = bestPenalty + crossPageJumpPenalty + tail.penalty
            val candidate = Plan(listOf(TestPagePacking(bestColumns)) + tail.pages, totalPenalty, bestAvgFill)
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

/**
 * Optical near-fit calibration: when a column sits under-filled (< 66% height, leaving >34% blank
 * bottom void) solely because including the adjacent cropped image question would overflow the
 * column by <= 13%, compute a gentle uniform width/height scale trim (0.87..0.97) for those
 * questions so they pack cleanly together without sacrificing crisp legibility.
 */
internal fun computeNearFitScaleAdjustments(
    heights: List<Int>,
    scalableFlags: List<Boolean>,
    initialPages: List<TestPagePacking>,
    availableHeight: Int,
    gap: Int
): FloatArray {
    val adjustments = FloatArray(heights.size) { 1f }
    if (heights.size < 2 || availableHeight <= 0) return adjustments

    val flatColumns = initialPages.flatMap { it.columns }.filter { it.isNotEmpty() }
    for (colIdx in 0 until flatColumns.lastIndex) {
        val col = flatColumns[colIdx]
        val nextCol = flatColumns[colIdx + 1]
        val candidateNext = nextCol.firstOrNull() ?: continue
        if (candidateNext != col.last() + 1) continue

        val currentSum = col.sumOf { heights[it] } + (col.size - 1) * gap
        if (currentSum > (availableHeight * 0.66f).toInt()) continue

        val candidateGroup = col + candidateNext
        if (candidateGroup.none { scalableFlags.getOrElse(it) { false } }) continue

        val totalGap = (candidateGroup.size - 1) * gap
        val combinedHeight = currentSum + gap + heights[candidateNext]
        if (combinedHeight <= availableHeight || combinedHeight > (availableHeight * 1.13f).toInt()) continue

        val fixedPart = candidateGroup.filterNot { scalableFlags.getOrElse(it) { false } }.sumOf { heights[it] }
        val scalablePart = candidateGroup.filter { scalableFlags.getOrElse(it) { false } }.sumOf { heights[it] }
        if (scalablePart <= 0) continue

        val targetScalableBudget = availableHeight - totalGap - fixedPart - 4
        val neededFactor = targetScalableBudget.toFloat() / scalablePart.toFloat()
        if (neededFactor in 0.87f..0.985f) {
            for (qIdx in candidateGroup) {
                if (scalableFlags.getOrElse(qIdx) { false }) {
                    adjustments[qIdx] = minOf(adjustments[qIdx], neededFactor)
                }
            }
        }
    }
    return adjustments
}
