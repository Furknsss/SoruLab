package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfVisionImportPolicyTest {
    private val independent = listOf(
        QuestionNumberAnchor(1, 160, 240, 184, 270),
        QuestionNumberAnchor(2, 160, 930, 184, 960)
    )

    private fun page(vararg boxes: PdfVisionQuestionBox) =
        PdfVisionPage("questions", "Orijinal Test 1", boxes.toList(), emptyList())

    @Test
    fun sourcePrintedNumbersAndCompleteNonoverlappingBoundsAreAccepted() {
        val bounds = page(
            PdfVisionQuestionBox(1, 105, 95, 470, 460),
            PdfVisionQuestionBox(2, 505, 95, 895, 460)
        )
        val accepted = PdfVisionImportPolicy.validateQuestionBoxes(
            bounds, 1400, 1800, independent, requireExactPrintedCoverage = true
        )
        assertEquals(listOf(1, 2), accepted.map { it.number })
    }

    @Test
    fun missingQuestionIsRejectedRatherThanSavedAsComplete() {
        val result = PdfVisionImportPolicy.validateQuestionBoxes(
            page(PdfVisionQuestionBox(1, 105, 95, 470, 460)),
            1400, 1800, independent, requireExactPrintedCoverage = true
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun samePictureCannotPretendToContainTwoDifferentNumberedQuestions() {
        val bad = page(
            PdfVisionQuestionBox(1, 105, 95, 560, 460),
            PdfVisionQuestionBox(2, 500, 95, 895, 460)
        )
        assertTrue(PdfVisionImportPolicy.validateQuestionBoxes(
            bad, 1400, 1800, independent, true
        ).isEmpty())
    }

    @Test
    fun mergedDiagramContainingNextPrintedQuestionMustBeRejected() {
        val bad = page(
            PdfVisionQuestionBox(1, 100, 90, 800, 470),
            PdfVisionQuestionBox(2, 500, 90, 910, 470)
        )
        assertTrue(PdfVisionImportPolicy.validateQuestionBoxes(
            bad, 1400, 1800, independent, true
        ).isEmpty())
    }

    @Test
    fun duplicatedPrintedNumberIsNotMistakenForAnExtraQuestion() {
        val bad = page(
            PdfVisionQuestionBox(1, 105, 95, 460, 460),
            PdfVisionQuestionBox(1, 505, 95, 895, 460)
        )
        assertTrue(PdfVisionImportPolicy.validateQuestionBoxes(
            bad, 1400, 1800, independent, false
        ).isEmpty())
    }

    @Test
    fun cannotGravitateToWrongPrintedNumberEvenWhenModelUsesPlausibleBoxes() {
        val swapped = page(
            PdfVisionQuestionBox(2, 105, 95, 470, 460),
            PdfVisionQuestionBox(1, 505, 95, 895, 460)
        )
        assertTrue(PdfVisionImportPolicy.validateQuestionBoxes(
            swapped, 1400, 1800, independent, true
        ).isEmpty())
    }

    @Test
    fun structurallyValidVisionJsonHasSeparateQuestionsAndKeyScopes() {
        val raw = """
            {
              "pageType":"answer_key",
              "printedHeader":"CEVAP ANAHTARI",
              "questions":[],
              "answerTests":[{
                "section":"Sinir Sistemi","kind":"analiz","testNumber":3,
                "isOsym":false,
                "cells":[{"number":1,"option":"B"},{"number":2,"option":"E"}]
              }]
            }
        """.trimIndent()
        val result = PdfVisionImportPolicy.parse(raw)
        val keys = PdfVisionImportPolicy.verifiedPrintedKeyCandidates(result)
        assertEquals(1, keys.size)
        assertEquals(PdfAnswerScope("sinir sistemi", 3, false, "analiz"), keys[0].first)
        assertEquals(listOf(1 to "B", 2 to "E"), keys[0].second)
    }

    @Test
    fun modelCannotUseQuestionOptionsAsAnAnswerKey() {
        val fake = PdfVisionPage(
            "questions", "Sinir Sistemi", emptyList(),
            listOf(PdfVisionKeyTest(
                "Sinir Sistemi", "analiz", 3, false,
                listOf(PdfVisionKeyCell(1, "A"), PdfVisionKeyCell(2, "B"))
            ))
        )
        assertTrue(PdfVisionImportPolicy.verifiedPrintedKeyCandidates(fake).isEmpty())
    }

    @Test
    fun uncertainTableHeadingOrSingleAnswerNeverCreatesVerifiedKey() {
        val fake = PdfVisionPage(
            "answer_key", "Cevap", emptyList(),
            listOf(
                PdfVisionKeyTest("???", "analiz", 3, false,
                    listOf(PdfVisionKeyCell(1, "B"))),
                PdfVisionKeyTest("Sinir Sistemi", "unknown", 3, false,
                    listOf(PdfVisionKeyCell(1, "B"), PdfVisionKeyCell(2, "A")))
            )
        )
        assertTrue(PdfVisionImportPolicy.verifiedPrintedKeyCandidates(fake).isEmpty())
    }
}
