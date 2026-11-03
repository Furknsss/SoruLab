package com.example.util

import kotlin.math.abs
import kotlin.math.max

/** A numbered answer row printed directly beneath the questions, or elsewhere in a PDF. */
internal data class PdfHorizontalAnswerGrid(
    val answers: Map<Int, String>,
    val top: Int,
    val bottom: Int
)

/**
 * Position-based extraction of two-line numbered answer tables:
 *
 *       1   2   3   4   5
 *       E   B   C   A   D
 *
 * A table needs at least four consecutive, horizontally aligned question numbers
 * and aligned letter cells underneath. This does not rely on a publisher, page
 * number, test length, or answer-key heading.
 */
internal object PdfHorizontalAnswerGridReader {
    private val numberToken = Regex("""^[0-9]{1,2}[.):-]?$""")
    private val optionToken = Regex("""^[A-Ea-e][.):-]?$""")

    fun find(
        cells: List<PdfOcrTextCell>,
        pageWidth: Int,
        pageHeight: Int
    ): List<PdfHorizontalAnswerGrid> {
        if (pageWidth <= 0 || pageHeight <= 0) return emptyList()
        val tokens = cells.flatMap(::expandSimpleRow)
            .filter { it.right > it.left && it.bottom > it.top }
        val numbers = tokens.filter { numberToken.matches(it.text.trim()) }
            .filter { it.text.trim().trim('.', ')', ':', '-').toIntOrNull() in 1..200 }
            .sortedWith(compareBy({ it.middleY }, { it.middleX }))
        val letters = tokens.filter { optionToken.matches(it.text.trim()) }
        if (numbers.size < 4 || letters.size < 4) return emptyList()

        val bands = mutableListOf<MutableList<PdfOcrTextCell>>()
        for (number in numbers) {
            val last = bands.lastOrNull()
            val y = last?.map { it.middleY }?.average()
            val averageHeight = last?.map { it.height }?.average() ?: number.height.toDouble()
            if (last != null && y != null &&
                abs(number.middleY - y) <= max(5.0, averageHeight * 0.75)
            ) last.add(number)
            else bands.add(mutableListOf(number))
        }

        val grids = mutableListOf<PdfHorizontalAnswerGrid>()
        for (band in bands) {
            val sorted = band.sortedBy { it.middleX }
            var run = mutableListOf<PdfOcrTextCell>()
            val runs = mutableListOf<List<PdfOcrTextCell>>()
            for (cell in sorted) {
                val n = parseNumber(cell)
                val prev = run.lastOrNull()?.let(::parseNumber)
                if (prev != null && n != prev + 1) {
                    if (run.size >= 4) runs.add(run.toList())
                    run = mutableListOf()
                }
                run.add(cell)
            }
            if (run.size >= 4) runs.add(run)

            for (sequence in runs) {
                val avgH = sequence.map { it.height }.average()
                val middleY = sequence.map { it.middleY }.average()
                val minX = sequence.minOf { it.middleX }
                val maxX = sequence.maxOf { it.middleX }
                if (maxX - minX < pageWidth * 0.15) continue
                val gap = sequence.zipWithNext { a, b -> b.middleX - a.middleX }
                    .filter { it > 0 }.sorted()
                    .let { if (it.isEmpty()) 0.0 else it[it.size / 2].toDouble() }
                if (gap <= 0.0) continue

                // Candidate letter rows must sit BELOW the number row and
                // within a few text heights, regardless of page position.
                val candidates = letters.filter {
                    val dy = it.middleY - middleY
                    dy > avgH * 0.35 && dy < max(20.0, avgH * 6.0) &&
                        it.middleX >= minX - gap * 0.55 &&
                        it.middleX <= maxX + gap * 0.55
                }
                // OCR boxes jitter by a few pixels; absolute Y buckets
                // can split one real answer row at a bucket boundary.
                val letterBands = mutableListOf<MutableList<PdfOcrTextCell>>()
                for (letter in candidates.sortedBy { it.middleY }) {
                    val current = letterBands.lastOrNull()
                    val rowMiddle = current?.map { it.middleY }?.average()
                    if (current != null && rowMiddle != null &&
                        abs(letter.middleY - rowMiddle) <= max(6.0, avgH * 0.85)
                    ) current.add(letter)
                    else letterBands.add(mutableListOf(letter))
                }
                for (answerBand in letterBands) {
                    val answers = linkedMapOf<Int, String>()
                    val usedLetters = mutableSetOf<PdfOcrTextCell>()
                    for (nCell in sequence) {
                        val closest = answerBand.filter { it !in usedLetters }
                            .minByOrNull { abs(it.middleX - nCell.middleX) } ?: continue
                        if (abs(closest.middleX - nCell.middleX) > gap * 0.49) continue
                        usedLetters.add(closest)
                        answers[parseNumber(nCell)] = closest.text.trim()
                            .trim('.', ')', ':', '-').uppercase()
                    }
                    if (answers.size >= 4 &&
                        answers.size >= (sequence.size * 0.75).toInt().coerceAtLeast(4) &&
                        answers.values.all { it in listOf("A", "B", "C", "D", "E") }
                    ) {
                        grids.add(
                            PdfHorizontalAnswerGrid(
                                answers = answers,
                                top = sequence.minOf { it.top },
                                bottom = answerBand.maxOf { it.bottom }
                            )
                        )
                        break
                    }
                }
            }
        }
        return grids.distinctBy { grid -> grid.top / 8 to grid.answers }
    }

    private fun parseNumber(cell: PdfOcrTextCell): Int =
        cell.text.trim().trim('.', ')', ':', '-').toIntOrNull() ?: -1

    // If ML Kit grouped an entire numbered row or answer row as one element,
    // recover the individual horizontal cell centers from the row bounds.
    private fun expandSimpleRow(cell: PdfOcrTextCell): List<PdfOcrTextCell> {
        val tokens = cell.text.trim().split(Regex("""\s+"""))
        if (tokens.size < 4 ||
            !(tokens.all { numberToken.matches(it) } ||
                tokens.all { optionToken.matches(it) })
        ) return listOf(cell)
        val tokenWidth = (cell.right - cell.left).toDouble() / tokens.size
        return tokens.mapIndexed { index, text ->
            PdfOcrTextCell(
                text = text,
                left = (cell.left + tokenWidth * index).toInt(),
                right = (cell.left + tokenWidth * (index + 1)).toInt(),
                top = cell.top,
                bottom = cell.bottom
            )
        }
    }
}
