package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAdaptiveAnswerRowsTest {
    @Test
    fun `parallel answer key tables do not merge same-height test rows`() {
        val cells = listOf(
            PdfOcrTextCell("NÖRONLAR", 20, 15, 165, 38),
            PdfOcrTextCell("FOTOSENTEZ", 600, 15, 790, 38),
            PdfOcrTextCell("TEST 1", 25, 95, 125, 115),
            PdfOcrTextCell("1.A 2.B 3.C 4.D", 150, 95, 400, 115),
            PdfOcrTextCell("TEST 1", 605, 95, 705, 115),
            PdfOcrTextCell("1.E 2.D 3.C 4.B", 720, 95, 970, 115)
        )
        val regions = PdfAdaptiveAnswerRows.regions(cells, pageWidth = 1000)
        assertEquals(2, regions.size)
        assertTrue(regions[0].any { "NÖRONLAR" in it })
        assertTrue(regions[1].any { "FOTOSENTEZ" in it })
        val keys = PdfScopedAnswerKeys()
        for (region in regions) keys.addOcrRows(region)
        val neuron = PdfScopedAnswerKeys.scopeFromQuestionHeader(
            "TEST 1\nNöronlar", "Nöronlar"
        )
        val photo = PdfScopedAnswerKeys.scopeFromQuestionHeader(
            "TEST 1\nFotosentez", "Fotosentez"
        )
        assertEquals("A", keys.get(neuron, 1))
        assertEquals("E", keys.get(photo, 1))
        assertEquals("D", keys.get(neuron, 4))
        assertEquals("B", keys.get(photo, 4))
    }

    @Test
    fun `single wide table remains one reading region`() {
        val cells = listOf(
            PdfOcrTextCell("HÜCRE", 200, 20, 360, 45),
            PdfOcrTextCell("TEST 1", 30, 70, 130, 92),
            PdfOcrTextCell("1.B 2.C 3.D 4.E", 170, 70, 760, 92),
            PdfOcrTextCell("TEST 2", 30, 145, 130, 166),
            PdfOcrTextCell("1.A 2.B 3.C 4.D", 170, 145, 760, 166)
        )
        val regions = PdfAdaptiveAnswerRows.regions(cells, 1000)
        assertEquals(1, regions.size)
        val keys = PdfScopedAnswerKeys()
        keys.addOcrRows(regions.single())
        val test2 = PdfScopedAnswerKeys.scopeFromQuestionHeader("TEST 2\nHücre", "Hücre")
        assertEquals("A", keys.get(test2, 1))
    }

    @Test
    fun `answer key is independent of its position among question pages`() {
        val keys = PdfScopedAnswerKeys()
        // First answer-page of a book may precede the questions.
        keys.addOcrRows(listOf("GENETİK", "TEST 2 1.C 2.D 3.E"))
        // A second page might be placed between two tests.
        keys.addOcrRows(listOf("GENETİK", "TEST 3 1.A 2.E 3.B"))
        val second = PdfScopedAnswerKeys.scopeFromQuestionHeader("TEST 2\nGenetik", "Genetik")
        val third = PdfScopedAnswerKeys.scopeFromQuestionHeader("TEST 3\nGenetik", "Genetik")
        assertEquals("C", keys.get(second, 1))
        assertEquals("A", keys.get(third, 1))
    }
}
