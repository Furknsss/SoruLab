package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A printed test identity and its chapter can share one OCR text line.
 * The test's number, kind and chapter must all come from the page itself.
 */
class PdfInlinePrintedHeaderIntegrationTest {
    @Test
    fun `chapter preceding numbered test on one line keeps the full printed scope`() {
        val identity = PdfScopedAnswerKeys.scopeFromQuestionHeader(
            "SINIR SISTEMI - TEST 1", ""
        )
        assertEquals(PdfAnswerScope("sinir sistemi", 1, false, "test"), identity)
        val index = PdfScopedAnswerKeys(setOf("sinir sistemi"))
        index.addOcrRows(listOf("SINIR SISTEMI - TEST 1 1.A 2.B 3.C 4.D"))
        assertEquals("A", index.get(identity, 1))
        assertEquals("D", index.get(identity, 4))
        assertNull(index.get(PdfAnswerScope("sinir sistemi", 2, false), 1))
    }

    @Test
    fun `chapter following numbered test is evidence not an inferred topic hint`() {
        val identity = PdfScopedAnswerKeys.scopeFromQuestionHeader(
            "TEST 7 - FOTOSENTEZ", "previous chapter"
        )
        assertEquals(PdfAnswerScope("fotosentez", 7, false, "test"), identity)
        assertNull(PdfScopedAnswerKeys.scopeFromQuestionHeader("TEST 7", "fotosentez"))
    }

    @Test
    fun `same line analysis heading retains distinct test kind`() {
        val identity = PdfScopedAnswerKeys.scopeFromQuestionHeader(
            "ENDOKRIN SISTEM - ANALIZ 2", ""
        )
        assertEquals(PdfAnswerScope("endokrin sistem", 2, false, "analiz"), identity)
    }
}
