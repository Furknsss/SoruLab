package com.example.util

import kotlin.math.abs
import kotlin.math.max

/** Minimal selectable-text glyph geometry; independent of PDFBox/Android APIs. */
internal data class PdfSourceGlyph(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)
data class PdfSourceQuestionAnchor(
    val number: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)
data class PdfSourcePageAnchors(
    val pageWidth: Float,
    val pageHeight: Float,
    val anchors: List<PdfSourceQuestionAnchor>
)

/**
 * Recover REAL printed question numbers from a PDF's own selectable text.
 * Supports two uneven columns; rejects inset numeric annotations or graph axes
 * when they do not form consecutive, vertically aligned question sequences.
 * For image-only PDFs this returns empty and the ML Kit OCR path is unchanged.
 */
internal object PdfVectorQuestionAnchors {
    private val printed = Regex("""^[1-9][0-9]{0,2}[.):\-]$""")
    private val glyphLetters = Regex("""^[0-9.):\-]+$""")

    fun find(
        glyphs: List<PdfSourceGlyph>,
        pageWidth: Float,
        pageHeight: Float
    ): List<PdfSourceQuestionAnchor> {
        if (pageWidth <= 0f || pageHeight <= 0f) return emptyList()
        val tokens = tokenize(glyphs)
            .filter {
                // Publisher margin sizes vary; use a wide physical safety
                // band, then reject false numbers by aligned sequence tests.
                it.x > pageWidth * .025f && it.x < pageWidth * .975f &&
                    // Bottom answer-key/page-number strips can repeat a real
                // question number (e.g. a genuine third question at 80%
                // and a printed "3." in the footer at 94%). They were
                // previously preferred because the run then spanned more
                // vertical space, silently discarding the real last crop.
                // A multiple-choice question cannot START within the last
                // 8% of a conventional book page and still hold its A-E
                // options. This is a generic geometry guard, not a
                // publisher-specific page number or crop coordinate.
                it.y > pageHeight * .04f && it.y < pageHeight * .92f
            }
        if (tokens.isEmpty()) return emptyList()
        // Align by printed number's LEFT edge, not by the x position of prose.
        val clusters = mutableListOf<MutableList<PdfSourceQuestionAnchor>>()
        val threshold = max(4f, pageWidth * .016f)
        for (token in tokens.sortedBy { it.x }) {
            val last = clusters.lastOrNull()
            val center = last?.map { it.x }?.average()?.toFloat()
            if (center != null && abs(token.x - center) <= threshold) {
                last.add(token)
            } else {
                clusters.add(mutableListOf(token))
            }
        }
        // Margin page numbers and graph labels can sit on the exact same X
        // alignment as a real question run. Find the best ordered subsequence
        // instead of requiring EVERY number in the X cluster to be consecutive.
        fun candidateRuns(cluster: List<PdfSourceQuestionAnchor>):
            List<List<PdfSourceQuestionAnchor>> {
            val ordered = cluster.sortedBy { it.y }
            if (ordered.isEmpty()) return emptyList()
            val minGap = max(6f, pageHeight * .025f)
            val paths = ordered.indices.map { i ->
                mutableListOf(ordered[i])
            }.toMutableList()
            for (i in ordered.indices) {
                for (j in 0 until i) {
                    if (ordered[i].number == ordered[j].number + 1 &&
                        ordered[i].y - ordered[j].y >= minGap &&
                        paths[j].size + 1 > paths[i].size
                    ) {
                        paths[i] = (paths[j] + ordered[i]).toMutableList()
                    }
                }
            }
            // A singleton is still useful for an asymmetric 1+2 column page.
            return paths.distinctBy { path ->
                path.joinToString("|") { "${it.number}@${it.y.toInt()}" }
            }.sortedWith(
                compareByDescending<List<PdfSourceQuestionAnchor>> { it.size }
                    .thenByDescending { it.last().y - it.first().y }
            ).take(3)
        }
        fun contiguous(items: List<PdfSourceQuestionAnchor>): Boolean =
            items.isNotEmpty() && items.zipWithNext().all { (a, b) ->
                b.number == a.number + 1
            }
        val runsByCluster = clusters.map(::candidateRuns)
        // A two-column run must increase through the left column and then
        // continue through the right column; labels within a chart cannot
        // override a stronger printed sequence.
        data class Candidate(
            val anchors: List<PdfSourceQuestionAnchor>,
            val columnEvidence: Int
        )
        val choices = mutableListOf<Candidate>()
        for (i in clusters.indices) {
            for (left in runsByCluster[i]) {
                if (left.size >= 2) choices += Candidate(left, 0)
                for (j in i + 1 until clusters.size) {
                    for (right in runsByCluster[j]) {
                        if (abs(left.first().x - right.first().x) < pageWidth * .20f) continue
                        val sequence = left + right
                        if (sequence.size >= 3 && contiguous(sequence)) {
                            // Prefer a geometrically coherent left/right page
                            // over a longer 1..7 sequence printed INSIDE a
                            // graph on a real four-question page.
                            val support = if (left.size >= 2 && right.size >= 2) 4 else 2
                            choices += Candidate(sequence, support)
                        }
                    }
                }
            }
        }
        val outermostPrintedX = tokens.minOf { it.x }
        return choices.maxWithOrNull(
            compareBy<Candidate> { candidate ->
                candidate.anchors.size + candidate.columnEvidence +
                    if (candidate.anchors.first().x <= outermostPrintedX +
                        pageWidth * .025f) 1 else 0
            }.thenBy { it.anchors.size }
                .thenBy { it.anchors.map(PdfSourceQuestionAnchor::number).distinct().size }
        )?.anchors.orEmpty()
    }

    private fun tokenize(glyphs: List<PdfSourceGlyph>): List<PdfSourceQuestionAnchor> {
        val candidates = glyphs.filter {
            it.text.isNotBlank() && glyphLetters.matches(it.text) &&
                it.width > 0f && it.height > 0f
        }.sortedWith(compareBy(PdfSourceGlyph::y, PdfSourceGlyph::x))
        val lines = mutableListOf<MutableList<PdfSourceGlyph>>()
        for (glyph in candidates) {
            val last = lines.lastOrNull()
            val y = last?.map { it.y }?.average()?.toFloat()
            if (y != null && abs(glyph.y - y) <= max(2.5f, glyph.height * .36f)) {
                last.add(glyph)
            } else lines.add(mutableListOf(glyph))
        }
        val output = mutableListOf<PdfSourceQuestionAnchor>()
        for (line in lines) {
            val current = mutableListOf<PdfSourceGlyph>()
            fun flush() {
                if (current.isEmpty()) return
                val word = current.joinToString("") { it.text }
                if (printed.matches(word)) {
                    val number = word.dropLast(1).toIntOrNull()
                    if (number != null && number in 1..200) {
                        output.add(
                            PdfSourceQuestionAnchor(
                                number, current.minOf { it.x },
                                current.minOf { it.y },
                                current.maxOf { it.x + it.width } - current.minOf { it.x },
                                current.maxOf { it.y + it.height } - current.minOf { it.y }
                            )
                        )
                    }
                }
                current.clear()
            }
            for (glyph in line.sortedBy { it.x }) {
                val previous = current.lastOrNull()
                val close = previous != null &&
                    glyph.x - (previous.x + previous.width) <=
                    max(1.7f, previous.height * .42f)
                if (!close) flush()
                current.add(glyph)
            }
            flush()
        }
        return output.distinctBy { Triple(it.number, (it.x * 2).toInt(), (it.y * 2).toInt()) }
    }
}
