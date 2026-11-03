package com.example.util

import kotlin.math.abs

/**
 * OCR geometry for an arbitrary publisher's answer-key layout.
 * No known publisher, page index, page count, or number of columns is required.
 */
internal data class PdfOcrTextCell(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val middleX: Int get() = (left + right) / 2
    val middleY: Int get() = (top + bottom) / 2
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

internal object PdfAdaptiveAnswerRows {
    private val testHeading = Regex(
        "(?i)\\b(?:osym\\s+(?:tadinda\\s+)?)?(?:test|analiz|deneme|uygulama|etkinlik)\\s*[-.:]?\\s*[0-9]{0,2}\\b"
    )

    /**
     * A page can contain multiple independent answer tables side by side.
     * Mixing cells solely by their Y coordinate concatenates different tests
     * (and sometimes different chapters). Split only where an independent
     * test-label column is actually observed, then read top to bottom in each.
     */
    fun regions(cells: List<PdfOcrTextCell>, pageWidth: Int): List<List<String>> {
        val valid = cells.filter { it.text.isNotBlank() && it.right > it.left && it.bottom > it.top }
        if (valid.isEmpty()) return emptyList()
        val markerXs = valid.filter { testHeading.containsMatchIn(PdfScopedAnswerKeys.normalize(it.text)) }
            .map { it.left }.sorted()
        val threshold = (pageWidth * 0.22f).toInt().coerceAtLeast(35)
        val clusters = mutableListOf<MutableList<Int>>()
        for (x in markerXs) {
            val cluster = clusters.lastOrNull()
            if (cluster == null || abs(x - cluster.average()) > threshold) {
                clusters += mutableListOf(x)
            } else {
                cluster += x
            }
        }
        if (clusters.size < 2) return listOf(rows(valid))

        val centers = clusters.map { it.average() }
        val boundaries = centers.zipWithNext { a, b -> ((a + b) / 2).toInt() }
        return (0..boundaries.size).map { lane ->
            val laneCells = valid.filter { cell ->
                val index = boundaries.indexOfFirst { cell.middleX < it }
                    .takeIf { it >= 0 } ?: boundaries.size
                index == lane
            }
            rows(laneCells)
        }.filter { it.isNotEmpty() }
    }

    private fun rows(cells: List<PdfOcrTextCell>): List<String> {
        val positioned = cells.sortedWith(compareBy({ it.middleY }, { it.left }))
        val groups = mutableListOf<MutableList<PdfOcrTextCell>>()
        for (cell in positioned) {
            val last = groups.lastOrNull()
            val y = last?.map { it.middleY }?.average()
            val h = last?.map { it.height }?.average()
            if (last != null && y != null && abs(cell.middleY - y) <= maxOf(6.0, (h ?: 0.0) * 0.55)) {
                last += cell
            } else {
                groups += mutableListOf(cell)
            }
        }
        return groups.map { group ->
            group.sortedBy { it.left }.joinToString(" ") { it.text.trim() }
        }.filter(String::isNotBlank)
    }
}
