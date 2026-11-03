package com.example.util

import kotlin.math.abs
import kotlin.math.max

/**
 * Reads horizontal two-row answer tables from a PDF's EXISTING text glyph
 * coordinates, independently of the order returned by PDFTextStripper.
 *
 * A label like "52" in the page margin is not an answer index. Require
 * a consecutive 1..N number row and an exactly aligned A-E letter row.
 * Ambiguous/partial alignments are rejected instead of completing by guess.
 */
internal object PdfVectorAnswerStrip {
    private data class Cell(
        val text: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    ) {
        val midX: Float get() = x + width / 2f
        val midY: Float get() = y + height / 2f
    }
    private val number = Regex("""^[0-9]{1,2}$""")
    private val letter = Regex("""^[A-E]$""")

    fun read(
        glyphs: List<PdfSourceGlyph>,
        pageWidth: Float,
        pageHeight: Float
    ): Map<Int, String> {
        if (pageWidth <= 0f || pageHeight <= 0f) return emptyMap()
        // A two-row table below the question area is safer to infer without
        // an explicit "cevap anahtari" heading than a row inside the question.
        val cells = splitRowsIntoCells(glyphs.filter {
            it.y > pageHeight * .65f && it.y < pageHeight * .99f &&
                it.x > pageWidth * .035f && it.x < pageWidth * .97f
        })
        if (cells.isEmpty()) return emptyMap()
        val yRows = groupByRow(cells)
        var best = emptyMap<Int, String>()

        for (numRow in yRows) {
            val numTokens = numRow.filter { number.matches(it.text) }
                .sortedBy { it.midX }
            if (numTokens.size < 4) continue
            // A printed footer may have a page number on either side; only
            // a strictly consecutive 1..N run can identify the key.
            for (start in numTokens.indices) {
                if (numTokens[start].text != "1") continue
                val sequence = mutableListOf<Cell>()
                var expected = 1
                for (candidate in numTokens.drop(start)) {
                    if (candidate.text.toIntOrNull() != expected) break
                    sequence.add(candidate)
                    expected++
                }
                if (sequence.size < 4) continue
                val medianGap = sequence.zipWithNext { a, b -> b.midX - a.midX }
                    .sorted().let { it[it.size / 2] }
                if (medianGap <= 0f || sequence.last().midX - sequence.first().midX <
                    pageWidth * .13f) continue
                val y = sequence.map { it.midY }.average().toFloat()
                val averageHeight = sequence.map { it.height }.average().toFloat()
                val rowOptions = yRows.filter { row ->
                    val candidateY = row.map { it.midY }.average().toFloat()
                    candidateY > y + averageHeight * .45f &&
                        candidateY < y + max(26f, averageHeight * 4f)
                }
                for (optionRow in rowOptions) {
                    val options = optionRow.filter { letter.matches(it.text.uppercase()) }
                    if (options.size < sequence.size) continue
                    val used = mutableSetOf<Cell>()
                    val map = linkedMapOf<Int, String>()
                    var valid = true
                    for ((i, slot) in sequence.withIndex()) {
                        val closest = options.filterNot { it in used }
                            .minByOrNull { abs(it.midX - slot.midX) }
                        if (closest == null ||
                            abs(closest.midX - slot.midX) >= medianGap * .39f) {
                            valid = false
                            break
                        }
                        used += closest
                        map[i + 1] = closest.text.uppercase()
                    }
                    if (valid && map.size == sequence.size && map.size > best.size) {
                        best = map
                    }
                }
            }
        }
        return best
    }

    private fun groupByRow(cells: List<Cell>): List<List<Cell>> {
        val rows = mutableListOf<MutableList<Cell>>()
        for (cell in cells.sortedBy { it.midY }) {
            val last = rows.lastOrNull()
            val meanY = last?.map { it.midY }?.average()?.toFloat()
            if (meanY != null && abs(cell.midY - meanY) <=
                max(2.8f, cell.height * .45f)) {
                last.add(cell)
            } else rows.add(mutableListOf(cell))
        }
        return rows
    }

    private fun splitRowsIntoCells(glyphs: List<PdfSourceGlyph>): List<Cell> {
        val words = mutableListOf<Cell>()
        for (row in groupByRow(glyphs.map {
            Cell(it.text, it.x, it.y, it.width, it.height)
        }.filter { it.width > 0f && it.height > 0f })) {
            val current = mutableListOf<Cell>()
            fun flush() {
                if (current.isEmpty()) return
                words.add(Cell(
                    text = current.joinToString("") { it.text },
                    x = current.minOf { it.x },
                    y = current.minOf { it.y },
                    width = current.maxOf { it.x + it.width } -
                        current.minOf { it.x },
                    height = current.maxOf { it.y + it.height } -
                        current.minOf { it.y }
                ))
                current.clear()
            }
            for (cell in row.sortedBy { it.x }) {
                val previous = current.lastOrNull()
                if (previous != null &&
                    cell.x - (previous.x + previous.width) >
                        max(1.8f, previous.height * .40f)) flush()
                current.add(cell)
            }
            flush()
        }
        return words
    }
}
