package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPrintedKeyCoverageAuditTest {
    private val scope = PdfAnswerScope("sinir sistemi", 1, false, "test")
    private val printed = listOf(
        scope to (1..5).map { it to listOf("A","B","C","D","E")[it-1] }
    )
    @Test
    fun completeFiveQuestionTestRequiresAllPrintedKeys() {
        val imported = (1..5).map { PdfAnswerId(scope, it) }
        val report = PdfPrintedKeyCoverageAudit.assess(printed, imported)
        assertTrue(report.complete)
        assertEquals(5, report.totalSourceNumbers)
    }
    @Test
    fun threeImportedWhenFivePrintedAreNotComplete() {
        val imported = (1..3).map { PdfAnswerId(scope, it) }
        val report = PdfPrintedKeyCoverageAudit.assess(printed, imported)
        assertFalse(report.complete)
        assertEquals(setOf(4,5), report.missingSourceQuestions.map { it.questionNumber }.toSet())
    }
    @Test
    fun duplicatePrintedIdentityCannotCountAsAdditionalQuestion() {
        val imported = (1..5).map { PdfAnswerId(scope, it) } + PdfAnswerId(scope, 4)
        assertFalse(PdfPrintedKeyCoverageAudit.assess(printed, imported).complete)
    }
    @Test
    fun otherTestNumberCannotSupplyThisTestsMissingQuestions() {
        val imported = (1..3).map { PdfAnswerId(scope, it) } +
            (4..5).map { PdfAnswerId(scope.copy(testNumber=2), it) }
        assertFalse(PdfPrintedKeyCoverageAudit.assess(printed, imported).complete)
    }
    @Test
    fun zeroIndependentPrintedCellsNeverMeansComplete() {
        assertFalse(PdfPrintedKeyCoverageAudit.assess(emptyList(), listOf(PdfAnswerId(scope,1))).complete)
    }
}
