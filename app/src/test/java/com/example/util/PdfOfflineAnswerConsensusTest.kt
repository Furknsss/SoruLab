package com.example.util

import org.junit.Assert.*
import org.junit.Test

class PdfOfflineAnswerConsensusTest {
    private val scope = PdfAnswerScope("Sinir Sistemi", 1, false)

    @Test fun matchingNativeAndOcrReadingsConfirmOnlyTheExactTest() {
        val a = PdfScopedAnswerKeys()
        val b = PdfScopedAnswerKeys()
        a.addPairs(scope, listOf(1 to "B", 2 to "E"))
        b.addPairs(scope.copy(section = "SİNİR SİSTEMİ"), listOf(1 to "B", 2 to "E"))
        val result = PdfOfflineAnswerConsensus.compare(a, b)
        assertEquals("B", result.confirmedOption(scope, 1))
        assertEquals("E", result.confirmedOption(scope, 2))
        assertNull(result.confirmedOption(scope.copy(testNumber = 2), 1))
    }

    @Test fun disagreementAndSingleReaderCellsRemainUnverified() {
        val a = PdfScopedAnswerKeys()
        val b = PdfScopedAnswerKeys()
        a.addPairs(scope, listOf(1 to "B", 2 to "E", 3 to "A"))
        b.addPairs(scope, listOf(1 to "C", 2 to "E"))
        val result = PdfOfflineAnswerConsensus.compare(a, b)
        assertNull(result.confirmedOption(scope, 1))
        assertEquals("E", result.confirmedOption(scope, 2))
        assertNull(result.confirmedOption(scope, 3))
        assertEquals(1, result.disputed.size)
        assertEquals(1, result.unsupported.size)
    }

    @Test fun conflictInsideEitherReaderCannotBeHiddenByASecondAgreeingCell() {
        val a = PdfScopedAnswerKeys()
        val b = PdfScopedAnswerKeys()
        a.addPairs(scope, listOf(1 to "B", 1 to "E"))
        b.addPairs(scope, listOf(1 to "B"))
        val result = PdfOfflineAnswerConsensus.compare(a, b)
        assertNull(result.confirmedOption(scope, 1))
        assertEquals(1, result.disputed.size)
        assertTrue(result.confirmed.isEmpty())
    }
}
