package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfScopedAnswerKeysTest {
    @Test
    fun `real biology book test one answer row is matched in the correct test`() {
        val index = PdfScopedAnswerKeys()
        index.addOcrRows(listOf(
            "SİNİR SİSTEMİ",
            "Test 1 1.E 2.B 3.A 4.A 5.E 6.D 7.B 8.A 9.D 10.C",
            "Test 2 1.B 2.E 3.C 4.D 5.C 6.E 7.B 8.A 9.B 10.E",
            "ÖSYM Test 6 1.E 2.D 3.A 4.C"
        ))
        val firstTest = PdfScopedAnswerKeys.scopeFromQuestionHeader(
            "TEST 1\nSinir Sistemi 1", "Sinir Sistemi"
        )
        val secondTest = PdfScopedAnswerKeys.scopeFromQuestionHeader(
            "TEST 2\nSinir Sistemi 2", "Sinir Sistemi"
        )
        val osymTest = PdfScopedAnswerKeys.scopeFromQuestionHeader(
            "ÖSYM TADINDA\nTEST 6\nSinir Sistemi", "Sinir Sistemi"
        )
        assertEquals(listOf("E", "B", "A", "A", "E", "D", "B", "A", "D", "C"),
            (1..10).map { index.get(firstTest, it) })
        assertEquals("B", index.get(secondTest, 1))
        assertEquals("E", index.get(osymTest, 1))
        assertNull(index.get(secondTest, 12))
    }

    @Test
    fun `same question number in different sections must not collide`() {
        val index = PdfScopedAnswerKeys()
        index.addOcrRows(listOf(
            "SİNİR SİSTEMİ", "Test 1 1.E 2.B",
            "ENDOKRİN SİSTEM - HOMEOSTAZİ", "Test 1 1.C 2.E"
        ))
        val nerve = PdfScopedAnswerKeys.scopeFromQuestionHeader(
            "TEST 1\nSinir Sistemi 1", "Sinir Sistemi")
        val endocrine = PdfScopedAnswerKeys.scopeFromQuestionHeader(
            "TEST 1\nEndokrin Sistem - Homeostazi 1", "Endokrin Sistem - Homeostazi")
        assertEquals("E", index.get(nerve, 1))
        assertEquals("C", index.get(endocrine, 1))
    }

    @Test
    fun `missing identity and conflicting answer never produce a guessed option`() {
        val index = PdfScopedAnswerKeys()
        index.addOcrRows(listOf(
            "SİNİR SİSTEMİ",
            "Test 1 1.E 2.B",
            "Test 1 1.A 2.B"
        ))
        val scope = PdfScopedAnswerKeys.scopeFromQuestionHeader(
            "TEST 1\nSinir Sistemi 1", "Sinir Sistemi")
        assertNull(index.get(scope, 1))
        assertEquals("B", index.get(scope, 2))
        assertNull(index.get(scope, 3))
        assertNull(index.get(null, 2))
        assertNull(PdfScopedAnswerKeys.scopeFromQuestionHeader("Sinir Sistemi", "Sinir Sistemi"))
    }

    @Test
    fun `answer table detection requires multiple table rows or a key heading`() {
        assertFalse(PdfScopedAnswerKeys.looksLikeAnswerPage(
            listOf("TEST 1 Sinir Sistemi", "1. Sinir sisteminde impuls ile ilgili hangisi doğrudur?")
        ))
        assertTrue(PdfScopedAnswerKeys.looksLikeAnswerPage(listOf(
            "SİNİR SİSTEMİ", "Test 1 1.E 2.B", "Test 2 1.C 2.A", "ÖSYM Test 6 1.E 2.B"
        )))
    }

    @Test
    fun `ocr compact cells without punctuation remain readable`() {
        val index = PdfScopedAnswerKeys()
        index.addOcrRows(listOf(
            "SINIR SISTEMI",
            "Test1 1.E 2B 3A 4A 5.E 6D 7B 8A 9.D 10.C"
        ))
        val scope = PdfScopedAnswerKeys.scopeFromQuestionHeader(
            "TEST 1\nSinir Sistemi 1", "Sinir Sistemi"
        )
        assertEquals(listOf("E", "B", "A", "A", "E", "D", "B", "A", "D", "C"),
            (1..10).map { index.get(scope, it) })
    }


    @Test
    fun `compact section number and publisher header do not confuse answer matching`() {
        val keys = PdfScopedAnswerKeys()
        keys.addOcrRows(listOf(
            "SİNİR SİSTEMİ",
            "Test 1 1.E 2.B 3.A 4.A 5.E 6.D 7.B 8.A 9.D 10.C"
        ))
        val scope = PdfScopedAnswerKeys.scopeFromQuestionHeader(
            "@KuasarNemesis\\nDR BIYOLOJI\\nTEST\\n1\\nSinir Sistemi1",
            "Sinir Sistemi"
        )
        assertEquals("E", keys.get(scope, 1))
        assertEquals("C", keys.get(scope, 10))
        assertEquals("sinir sistemi", scope?.section)
    }

    @Test
    fun `unrelated topic must not be silently rewritten by catalog hint`() {
        val scope = PdfScopedAnswerKeys.scopeFromQuestionHeader(
            "TEST 2\\nENDOKRIN SISTEM HOMEOSTAZI 2", "Sinir Sistemi"
        )
        assertEquals("endokrin sistem homeostazi", scope?.section)
    }

}
