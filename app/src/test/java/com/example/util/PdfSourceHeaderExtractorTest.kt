package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSourceHeaderExtractorTest {
    @Test
    fun `vertical publisher mark cannot hide a printed test heading`() {
        val text = listOf(
            "B", "İ", "Y", "O", "T", "İ", "K", "9",
            "1. ÜNİTE: İNSAN FİZYOLOJİSİ", "ANALİZ", "1",
            "Sinir Sistemi", "1.", "Aksiyon potansiyeli sorusu"
        ).joinToString("\n")
        val header = PdfSourceHeaderExtractor.extract(text)
        assertTrue(header.contains("ANALİZ"))
        assertTrue(header.contains("Sinir Sistemi"))
        assertTrue(!header.contains("Aksiyon potansiyeli"))
        val scope = PdfScopedAnswerKeys.scopeFromQuestionHeader(header, "Sinir Sistemi")
        assertEquals(PdfAnswerScope("sinir sistemi", 1, false, "analiz"), scope)
    }

    @Test
    fun `footer answer rows do not displace the test scope`() {
        val text = listOf(
            "12", "Sinir Sistemi", "ANALİZ",
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
            "E", "B", "C", "D", "E", "C", "A", "B", "D", "E",
            "2", "6.", "Soru başlangıcı"
        ).joinToString("\n")
        val header = PdfSourceHeaderExtractor.extract(text)
        assertTrue(header.contains("ANALİZ"))
        assertTrue(header.contains("Sinir Sistemi"))
        assertTrue(!header.contains("Soru başlangıcı"))
        assertEquals(2, PdfScopedAnswerKeys.scopeFromQuestionHeader(header, "Sinir Sistemi")?.testNumber)
    }

    @Test
    fun `bare answer row numbers never masquerade as a test number`() {
        val source = listOf(
            "Konu Başlığı", "TEST", "1", "2", "3", "4", "5", "6.",
            "Gerçek soru metni"
        ).joinToString("\n")
        val header = PdfSourceHeaderExtractor.extract(source)
        val scope = PdfScopedAnswerKeys.scopeFromQuestionHeader(header, "Konu Başlığı")
        org.junit.Assert.assertNull(scope)
    }
}
