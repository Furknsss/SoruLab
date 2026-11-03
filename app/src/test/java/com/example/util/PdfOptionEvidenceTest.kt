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
        assertEquals(setOf("A", "C", "E"), PdfOptionEvidence.letters("a) B. c) D. e)"))
        assertTrue(PdfOptionEvidence.letters("A. B. C. D. E.").isEmpty())
        assertTrue(PdfOptionEvidence.letters("").isEmpty())
    }
}
