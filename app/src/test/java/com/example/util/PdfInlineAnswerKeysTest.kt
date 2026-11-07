package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfInlineAnswerKeysTest {
    @Test
    fun `two row key with printed test number is read`() {
        val keys = PdfInlineAnswerKeys.parse(listOf(
            "Sinir Sistemi", "ANALİZ",
            "1 2 3 4 5 6 7 8 9 10", "E B E B E C A E D C", "1"
        ))
        assertEquals(1, keys.size)
        assertEquals(1, keys[0].testNumber)
        assertEquals(listOf("E", "B", "E", "B", "E", "C", "A", "E", "D", "C"), keys[0].answers)
        assertEquals("C", keys[0].answerFor(6))
        assertNull(keys[0].answerFor(11))
    }

    @Test
    fun `mismatched letter count is rejected instead of guessed`() {
        assertTrue(PdfInlineAnswerKeys.parse(listOf(
            "1 2 3 4 5 6 7 8 9 10", "E B E B E C A E D"
        )).isEmpty())
        assertTrue(PdfInlineAnswerKeys.parse(listOf(
            "1 2 4 5 6 7 8 9", "E B E B E C A E"
        )).isEmpty())
    }

    @Test
    fun `first page of a test uses the key on the next page only when numbering continues`() {
        val key = InlineAnswerKey(2, listOf("B", "E", "A", "D", "C", "E", "C", "C", "E", "B"))
        val keys = mapOf(11 to key)
        val min = mapOf(10 to 1, 11 to 6)
        val max = mapOf(10 to 5, 11 to 10)
        assertEquals("B", PdfInlineAnswerKeys.keyForQuestion(10, 1, keys, min, max))
        assertEquals("E", PdfInlineAnswerKeys.keyForQuestion(11, 6, keys, min, max))
        // Next page restarts at 1: it belongs to a different test, so no answer is guessed.
        assertNull(PdfInlineAnswerKeys.keyForQuestion(10, 1, keys, mapOf(10 to 1, 11 to 1), mapOf(10 to 5, 11 to 5)))
    }
}
