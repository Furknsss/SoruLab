package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfVisionAnswerConsensusTest {
    private val scope = PdfAnswerScope("sinir sistemi", 3, false, "analiz")

    @Test
    fun identicalPrintedCellReadByTwoEnginesIsConfirmed() {
        val independent = PdfScopedAnswerKeys()
        independent.addPairs(scope, listOf(1 to "B", 2 to "E"))
        val result = PdfVisionAnswerConsensus.compare(
            independent, listOf(scope to listOf(1 to "B", 2 to "E"))
        )
        assertEquals("B", result.confirmedOption(scope, 1))
        assertEquals("E", result.confirmedOption(scope, 2))
        assertTrue(result.disputed.isEmpty())
    }

    @Test
    fun modelOnlyAnswerIsNotVerified() {
        val independent = PdfScopedAnswerKeys()
        val result = PdfVisionAnswerConsensus.compare(
            independent, listOf(scope to listOf(1 to "B", 2 to "E"))
        )
        assertNull(result.confirmedOption(scope, 1))
        assertEquals(2, result.unsupported.size)
    }

    @Test
    fun disagreeingReadersNeverGradeQuestion() {
        val independent = PdfScopedAnswerKeys()
        independent.addPairs(scope, listOf(1 to "A"))
        val result = PdfVisionAnswerConsensus.compare(
            independent, listOf(scope to listOf(1 to "E"))
        )
        assertNull(result.confirmedOption(scope, 1))
        assertEquals(1, result.disputed.size)
    }

    @Test
    fun conflictingVisionReadingsCannotWinByMajority() {
        val independent = PdfScopedAnswerKeys()
        independent.addPairs(scope, listOf(1 to "B"))
        val readings = listOf(
            scope to listOf(1 to "B"),
            scope to listOf(1 to "E"),
            scope to listOf(1 to "B")
        )
        val result = PdfVisionAnswerConsensus.compare(independent, readings)
        assertNull(result.confirmedOption(scope, 1))
        assertEquals(1, result.disputed.size)
    }

    @Test
    fun sameQuestionNumberInDifferentTestsNeverCrossMatches() {
        val independent = PdfScopedAnswerKeys()
        independent.addPairs(scope, listOf(1 to "B"))
        val otherScope = scope.copy(testNumber = 4)
        val result = PdfVisionAnswerConsensus.compare(
            independent, listOf(otherScope to listOf(1 to "B"))
        )
        assertNull(result.confirmedOption(scope, 1))
        assertNull(result.confirmedOption(otherScope, 1))
    }
}
