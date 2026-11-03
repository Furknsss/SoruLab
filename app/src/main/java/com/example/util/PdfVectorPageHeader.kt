package com.example.util

import kotlin.math.abs
import kotlin.math.max

/**
 * Reads ONLY the small printed chapter/test heading in the upper page band.
 * It does not borrow question numbers or answer-table numbers as a test ID.
 * Returns null on uncertain page geometry, leaving ML Kit as fallback.
 */
internal object PdfVectorPageHeader {
    private data class Word(
        val text: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    ) {
        val middleX: Float get() = x + width / 2f
        val middleY: Float get() = y + height / 2f
    }
    private val kinds = setOf("test", "analiz", "sentez", "deneme", "uygulama", "etkinlik")
    private val number = Regex("""^[0-9]{1,2}$""")

    fun read(
        glyphs: List<PdfSourceGlyph>,
        pageWidth: Float,
        pageHeight: Float
    ): String? {
        if (pageWidth <= 0f || pageHeight <= 0f) return null
        val words = tokenize(glyphs.filter {
            it.y > pageHeight * .025f &&
                it.y < pageHeight * .108f &&
                it.x > pageWidth * .035f &&
                it.x < pageWidth * .96f
        })
        val title = words.firstOrNull {
            PdfScopedAnswerKeys.normalize(it.text) in kinds
        } ?: return null
        val kind = PdfScopedAnswerKeys.normalize(title.text)
        val numberWord = words.filter {
            number.matches(it.text.trim()) &&
                it.middleY > title.middleY + 1f &&
                it.middleY < title.middleY + max(33f, pageHeight * .05f) &&
                abs(it.middleX - title.middleX) < pageWidth * .085f
        }.minByOrNull { abs(it.middleX - title.middleX) } ?: return null

        // The section name usually sits close to the page center, on a single
        // header baseline. It must be independently visible, never inferred
        // from the book name or from a stale page's chapter.
        val sectionWords = words.filter {
            it !== title && it !== numberWord &&
                it.middleX > pageWidth * .16f &&
                it.middleX < pageWidth * .84f &&
                it.middleY > title.middleY - 6f &&
                it.middleY < title.middleY + max(42f, pageHeight * .055f) &&
                it.text.count(Char::isLetter) >= 2 &&
                PdfScopedAnswerKeys.normalize(it.text) !in kinds
        }
        val lines = mutableListOf<MutableList<Word>>()
        for (word in sectionWords.sortedBy { it.middleY }) {
            val last = lines.lastOrNull()
            val mean = last?.map { it.middleY }?.average()?.toFloat()
            if (mean != null && abs(word.middleY - mean) <=
                max(2.8f, word.height * .40f)
            ) last.add(word) else lines.add(mutableListOf(word))
        }
        val section = lines.map { row ->
            row.sortedBy { it.x }.joinToString(" ") { it.text }.trim()
        }.filter { line ->
            val normalized = PdfScopedAnswerKeys.normalize(line)
            normalized.length >= 5 && normalized.any(Char::isLetter) &&
                !normalized.contains("www") && !normalized.contains("yayin")
        }.maxByOrNull { it.length } ?: return null

        return "$kind ${numberWord.text.trim()}\n$section"
    }

    private fun tokenize(glyphs: List<PdfSourceGlyph>): List<Word> {
        val words = mutableListOf<Word>()
        val rows = mutableListOf<MutableList<PdfSourceGlyph>>()
        for (glyph in glyphs.filter {
            it.text.isNotBlank() && it.width > 0f && it.height > 0f
        }.sortedBy { it.y + it.height / 2f }) {
            val last = rows.lastOrNull()
            val mean = last?.map { it.y + it.height / 2f }?.average()?.toFloat()
            if (mean != null && abs(glyph.y + glyph.height / 2f - mean) <=
                max(2.6f, glyph.height * .42f)
            ) last.add(glyph) else rows.add(mutableListOf(glyph))
        }
        for (row in rows) {
            val word = mutableListOf<PdfSourceGlyph>()
            fun flush() {
                if (word.isNotEmpty()) {
                    val x = word.minOf { it.x }
                    val y = word.minOf { it.y }
                    words.add(Word(
                        word.joinToString("") { it.text },
                        x, y,
                        word.maxOf { it.x + it.width } - x,
                        word.maxOf { it.y + it.height } - y
                    ))
                    word.clear()
                }
            }
            for (glyph in row.sortedBy { it.x }) {
                val previous = word.lastOrNull()
                if (previous != null &&
                    glyph.x - previous.x - previous.width >
                    max(1.8f, previous.height * .40f)) flush()
                word.add(glyph)
            }
            flush()
        }
        return words
    }
}
