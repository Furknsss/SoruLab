package com.example.util

import kotlin.math.abs
import kotlin.math.max

/** Page-local printed test identity, obtained from glyph positions, not OCR text order. */
data class PdfPrintedTestMarker(
    val testKind: String,
    val testNumber: Int,
    val isOsymTest: Boolean
)

/**
 * Some PDF text layers emit the footer answer table's "1 2 3 ..." immediately
 * after the word "ANALİZ", even when the actual test number is separately
 * printed beside that title at the top of the page. Reading the first number
 * after the word silently attributes whole answer keys to the wrong test.
 *
 * Pair a printed test label with a spatially adjacent number. No book title,
 * publisher name, known test count, or fixed X position is used.
 */
internal object PdfVectorTestMarker {
    private data class Token(
        val value: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    ) {
        val midX get() = x + width / 2f
    }

    private val allowedKinds = setOf(
        "analiz", "sentez", "test", "deneme", "uygulama", "etkinlik"
    )
    private val numeric = Regex("""^[1-9][0-9]{0,2}$""")

    fun find(
        glyphs: List<PdfSourceGlyph>,
        pageWidth: Float,
        pageHeight: Float
    ): PdfPrintedTestMarker? {
        if (pageWidth <= 0f || pageHeight <= 0f) return null
        val tokens = tokenize(glyphs.filter {
            it.text.isNotBlank() && it.width > 0f && it.height > 0f &&
                it.x in 0f..pageWidth && it.y > 0f &&
                it.y < pageHeight * .27f
        })
        val labels = tokens.filter {
            PdfScopedAnswerKeys.normalize(it.value) in allowedKinds
        }
        val numbers = tokens.filter {
            numeric.matches(it.value) &&
                (it.value.toIntOrNull()?.let { n -> n in 1..200 } == true)
        }
        var best: PdfPrintedTestMarker? = null
        var bestScore = Float.POSITIVE_INFINITY
        for (label in labels) {
            for (number in numbers) {
                val deltaY = number.y - label.y
                val deltaX = abs(number.midX - label.midX)
                if (deltaY < -label.height * 1.5f ||
                    deltaY > max(48f, label.height * 5f) ||
                    deltaX > max(label.width * 1.3f, pageWidth * .12f)
                ) continue
                val score = abs(deltaY - max(8f, label.height * .7f)) +
                    deltaX * .3f
                if (score >= bestScore) continue
                val osymNearby = tokens.any {
                    PdfScopedAnswerKeys.normalize(it.value) == "osym" &&
                        abs(it.y - label.y) < max(48f, label.height * 5f) &&
                        abs(it.midX - label.midX) < pageWidth * .40f
                }
                bestScore = score
                best = PdfPrintedTestMarker(
                    testKind = PdfScopedAnswerKeys.normalize(label.value),
                    testNumber = number.value.toInt(),
                    isOsymTest = osymNearby
                )
            }
        }
        return best
    }

    private fun tokenize(glyphs: List<PdfSourceGlyph>): List<Token> {
        val rows = mutableListOf<MutableList<PdfSourceGlyph>>()
        for (glyph in glyphs.sortedBy { it.y }) {
            val last = rows.lastOrNull()
            val average = last?.map { it.y }?.average()?.toFloat()
            if (average != null &&
                abs(glyph.y - average) <= max(2.5f, glyph.height * .36f)
            ) last.add(glyph) else rows.add(mutableListOf(glyph))
        }
        val tokens = mutableListOf<Token>()
        for (row in rows) {
            val current = mutableListOf<PdfSourceGlyph>()
            fun flush() {
                if (current.isEmpty()) return
                val left = current.minOf { it.x }
                val top = current.minOf { it.y }
                tokens += Token(
                    value = current.joinToString("") { it.text },
                    x = left,
                    y = top,
                    width = current.maxOf { it.x + it.width } - left,
                    height = current.maxOf { it.y + it.height } - top
                )
                current.clear()
            }
            for (glyph in row.sortedBy { it.x }) {
                val previous = current.lastOrNull()
                if (previous != null &&
                    glyph.x - (previous.x + previous.width) >
                    max(1.7f, previous.height * .40f)
                ) flush()
                current.add(glyph)
            }
            flush()
        }
        return tokens
    }
}
