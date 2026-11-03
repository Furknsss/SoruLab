package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfOptionEvidenceTest {
    @Test
    fun `opening a real question page cannot throw PatternSyntaxException`() {
        // Typical question choices from any publisher, not a Biyotik template.
        val text = "1. Soru\\nA) Seçenek 1\\nB) Seçenek 2\\nC) Seçenek 3\\nD) Seçenek 4\\nE) Seçenek 5"
            .replace("\\n", "\n")
        assertEquals(setOf("A", "B", "C", "D", "E"), PdfOptionEvidence.letters(text))
    }

    @Test
    fun `case and missing PDF choice markers are handled safely`() {
        assertEquals(setOf("A", "C", "E"), PdfOptionEvidence.letters("a) İlk açıklama B. c) İkinci açıklama D. e) Son açıklama"))
        assertTrue(PdfOptionEvidence.letters("A. B. C. D. E.").isEmpty())
        assertTrue(PdfOptionEvidence.letters("").isEmpty())
    }
    @Test
    fun `different publishers may print dotted colon or parenthesized choices`() {
        val source = listOf(
            "A. Doğru seçenek",
            "B: Açıklama",
            "(C) Grafik yorumlama",
            "D ) Şekildeki değer",
            "E) Sonuç"
        ).joinToString("\n")
        assertEquals(setOf("A", "B", "C", "D", "E"), PdfOptionEvidence.letters(source))
    }

    @Test
    fun `inline abbreviations and empty option labels are not evidence`() {
        assertTrue(PdfOptionEvidence.letters("DNA RNA ATP").isEmpty())
        assertTrue(PdfOptionEvidence.letters("A. B. C. D. E.").isEmpty())
        assertTrue(PdfOptionEvidence.letters("A: B: C: D: E:").isEmpty())
    }

    @Test
    fun `a different publisher may place each option label on its own line`() {
        val source = "A)\nBirinci cevap\nB)\nİkinci cevap\nC)\nÜçüncü cevap\nD)\nDördüncü cevap\nE)\nBeşinci cevap"
        assertEquals(setOf("A", "B", "C", "D", "E"), PdfOptionEvidence.letters(source))
    }

    @Test
    fun `a naked row of option letters is not a question page`() {
        assertTrue(PdfOptionEvidence.letters("A)\nB)\nC)\nD)\nE)").isEmpty())
    }

}
