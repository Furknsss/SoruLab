package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextFooterKeysTest {
    @Test
    fun pairedPageFooterHasExactlyOneAnswerForEachPrintedNumber() {
        val text = """
            ANALİZ
            1
            Sinir Sistemi
            8. Soru gövdesi
            9. Soru gövdesi
            10     1     2     3     4     5     6     7     8     9     10
                   C     A     B     E     D     E     C     B     A     D
        """.trimIndent()
        val keys = PdfTextFooterKeys.read(text)
        assertEquals(10, keys.size)
        assertEquals("C", keys[1])
        assertEquals("D", keys[10])
        assertEquals(null, keys[11])
    }

    @Test
    fun acceptsVariableTestLengthsAndRejectsPartialRows() {
        assertEquals(5, PdfTextFooterKeys.read("""
            272    1 2 3 4 5
                   A E B D C
        """.trimIndent()).size)
        assertTrue(PdfTextFooterKeys.read("""
            17 1 2 3 4 5
               A E B D
        """.trimIndent()).isEmpty())
    }

    @Test
    fun proseOrAnswerChoiceLinesCannotBecomeAnAnswerKey() {
        assertTrue(PdfTextFooterKeys.read("""
            1 2 3 4
            A) B) C) D)
        """.trimIndent()).isEmpty())
        assertTrue(PdfTextFooterKeys.read("""
            1 2 3 4 5
            A B C D E F
        """.trimIndent()).isEmpty())
    }
    @Test
    fun verticalizedNumberThenLetterGridIsRecoveredAnywhereOnPage() {
        val answers = listOf("E","B","E","B","E","C","A","E","D","C")
        val source = listOf("42", "B", "İ", "Y", "O", "L", "O", "J", "İ",
            "Sinir Sistemi", "ANALİZ") +
            (1..answers.size).map(Int::toString) + answers +
            listOf("TEST 1", "6.", "Aşağıdaki soruyu çözünüz", "A) Yanlış", "B) Yanlış")
        val actual = PdfTextFooterKeys.read(source.joinToString("\n"))
        assertEquals(10, actual.size)
        assertEquals("E", actual[1])
        assertEquals("C", actual[6])
        assertEquals("C", actual[10])
    }

    @Test
    fun questionOptionListCannotImpersonateNumberAndAnswerGrid() {
        val invalid = listOf("1", "2", "3", "4", "A", "B", "C",
            "Çözümü açıklayınız", "D")
        assertTrue(PdfTextFooterKeys.read(invalid.joinToString("\n")).isEmpty())
    }

    @Test
    fun unrelatedNumbersOrAmbiguousCellsDoNotProduceGuessedAnswers() {
        val wrong = listOf("1", "2", "4", "5", "A", "C", "E", "D")
        assertTrue(PdfTextFooterKeys.read(wrong.joinToString("\n")).isEmpty())
    }

}
