package com.example.util

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Recover the printed chapter/test header from physical page coordinates.
 *
 * PDF text extraction can output a top-of-page topic AFTER a hundred answer
 * table/paragraph lines. A fixed number of first text lines loses that title.
 * Keep the source's real spaces and collect only glyphs ABOVE its first
 * verified printed question, independently of publisher or page dimensions.
 */
internal object PdfVectorPageHeader {
    fun read(
        glyphs: List<PdfSourceGlyph>,
        pageWidth: Float,
        pageHeight: Float,
        firstQuestionY: Float? = null
    ): String {
        if (pageWidth <= 0f || pageHeight <= 0f) return ""
        val headerBottom = min(
            pageHeight * .32f,
            firstQuestionY?.minus(max(3f, pageHeight * .004f))
                ?: pageHeight * .25f
        ).coerceAtLeast(pageHeight * .065f)
        val visible = glyphs.filter {
            it.width > 0f && it.height > 0f &&
                it.x >= 0f && it.x <= pageWidth &&
                it.y >= 0f && it.y < headerBottom
        }
        if (visible.isEmpty()) return ""
        val rows = mutableListOf<MutableList<PdfSourceGlyph>>()
        for (glyph in visible.sortedBy { it.y }) {
            val last = rows.lastOrNull()
            val average = last?.map { it.y }?.average()?.toFloat()
            if (average != null &&
                abs(glyph.y - average) <= max(2.8f, glyph.height * .17f)
            ) last.add(glyph) else rows.add(mutableListOf(glyph))
        }

        val lines = mutableListOf<String>()
        for (row in rows) {
            val text = StringBuilder()
            var previous: PdfSourceGlyph? = null
            for (glyph in row.sortedBy { it.x }) {
                val prev = previous
                if (prev != null &&
                    glyph.x - (prev.x + prev.width) > max(2.8f, prev.height * .35f) &&
                    text.isNotEmpty() && !text.last().isWhitespace()
                ) text.append(' ')
                text.append(glyph.text)
                previous = glyph
            }
            val line = text.toString().trim()
            if (line.isNotBlank() && line.length <= 180 &&
                !(line.length == 1 && line[0].isLetter()) &&
                !line.startsWith("@") &&
                !line.startsWith("http", ignoreCase = true)
            ) lines += line
        }
        return lines.take(32).joinToString("\n").take(3000)
    }
}
