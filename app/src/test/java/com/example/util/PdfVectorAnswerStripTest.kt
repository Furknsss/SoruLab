package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfVectorAnswerStripTest {
    private fun glyph(text: String, x: Float, y: Float, width: Float = 6f) =
        PdfSourceGlyph(text, x, y, width, 9f)

    @Test
    fun pageNumberBeforeAnswerGridIsNotTreatedAsQuestionOne() {
        val letters = listOf("E", "B", "C", "A", "D", "E", "C", "A", "B", "D")
        val glyphs = mutableListOf(glyph("10", 68f, 754f))
        for (i in letters.indices) {
            val x = 190f + i * 28f
            glyphs += glyph("${i + 1}", x, 758f)
            glyphs += glyph(letters[i], x, 771f)
        }
        val keys = PdfVectorAnswerStrip.read(glyphs, 595f, 792f)
        assertEquals(10, keys.size)
        assertEquals(letters, (1..10).map(keys::get))
        assertEquals(null, keys[11])
    }

    @Test
    fun optionLettersMustAlignWithConsecutiveNumberCells() {
        val glyphs = mutableListOf<PdfSourceGlyph>()
        for (i in 0 until 5) {
            glyphs += glyph("${i + 1}", 160f + i * 35f, 756f)
            glyphs += glyph("A", 240f + i * 35f, 770f)
        }
        assertTrue(PdfVectorAnswerStrip.read(glyphs, 595f, 792f).isEmpty())
    }
}
