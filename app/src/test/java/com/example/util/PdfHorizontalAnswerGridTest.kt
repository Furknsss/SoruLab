package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfHorizontalAnswerGridTest {
    private fun grid(
        options: String,
        numberTop: Int = 1370,
        xStart: Int = 280,
        columnWidth: Int = 42
    ): List<PdfOcrTextCell> {
        return options.mapIndexed { index, answer ->
            listOf(
                PdfOcrTextCell(
                    text = "${index + 1}",
                    left = xStart + index * columnWidth,
                    top = numberTop,
                    right = xStart + index * columnWidth + 14,
                    bottom = numberTop + 14
                ),
                PdfOcrTextCell(
                    text = "$answer",
                    left = xStart + index * columnWidth,
                    top = numberTop + 29,
                    right = xStart + index * columnWidth + 14,
                    bottom = numberTop + 43
                )
            )
        }.flatten()
    }

    @Test
    fun `answer grid directly underneath questions reads correct biology answers`() {
        // Ten answers visible in the user's real Biyotik screenshot;
        // this fixture tests spatial reading, not a publisher-specific rule.
        val ocr = grid("EBEBECAEDC")
        val result = PdfHorizontalAnswerGridReader.find(ocr, 1000, 1500)
        assertEquals(1, result.size)
        assertEquals("E", result.single().answers[1])
        assertEquals("B", result.single().answers[2])
        assertEquals("E", result.single().answers[3])
        assertEquals("C", result.single().answers[6])
        assertEquals("A", result.single().answers[7])
        assertEquals("C", result.single().answers[10])
        val scope = PdfScopedAnswerKeys.scopeFromQuestionHeader(
            "ANALİZ\n1\nSinir Sistemi", "Sinir Sistemi"
        )
        assertEquals("analiz", scope?.testKind)
        assertEquals(1, scope?.testNumber)
        assertEquals("sinir sistemi", scope?.section)
        val answers = PdfScopedAnswerKeys(setOf("sinir sistemi"))
        answers.addPairs(scope, result.single().answers.toList())
        assertEquals("E", answers.get(scope, 1))
        assertEquals("C", answers.get(scope, 10))
    }

    @Test
    fun `answer grid is recognized near top or middle without page offset presets`() {
        for (top in listOf(70, 380, 820)) {
            val result = PdfHorizontalAnswerGridReader.find(
                grid("ACDDB", numberTop = top), 1200, 1500
            )
            assertEquals("grid top=$top", 1, result.size)
            assertEquals("B", result.single().answers[5])
        }
    }

    @Test
    fun `aligned letters are required to avoid treating numbered question text as an answer table`() {
        val numbersOnly = grid("ABCDE").filter { it.text.first().isDigit() }
        assertTrue(PdfHorizontalAnswerGridReader.find(numbersOnly, 1000, 1500).isEmpty())
        val lettersAboveNumbers = grid("ABCDE").map { cell ->
            if (cell.text.first().isLetter()) cell.copy(top = 1320, bottom = 1334) else cell
        }
        assertTrue(PdfHorizontalAnswerGridReader.find(lettersAboveNumbers, 1000, 1500).isEmpty())
    }

    @Test
    fun `one OCR element can contain each full separated table row`() {
        val ocr = listOf(
            PdfOcrTextCell("1 2 3 4 5", 160, 500, 660, 517),
            PdfOcrTextCell("D A C B E", 160, 532, 660, 549)
        )
        val result = PdfHorizontalAnswerGridReader.find(ocr, 1000, 1200)
        assertEquals(1, result.size)
        assertEquals(mapOf(1 to "D", 2 to "A", 3 to "C", 4 to "B", 5 to "E"),
            result.single().answers)
    }

    @Test
    fun `conflicting answer rows remain unresolved rather than silently overwritten`() {
        val scope = PdfScopedAnswerKeys.scopeFromQuestionHeader(
            "ANALİZ 1\nSinir Sistemi", "Sinir Sistemi"
        )
        val keys = PdfScopedAnswerKeys(setOf("sinir sistemi"))
        keys.addPairs(scope, listOf(1 to "A", 2 to "B"))
        keys.addPairs(scope, listOf(1 to "E", 2 to "B"))
        assertNull(keys.get(scope, 1))
        assertEquals("B", keys.get(scope, 2))
    }

    @Test
    fun `small OCR bounding box jitter does not split the answer letter row`() {
        val cells = grid("EBEBECAEDC").mapIndexed { index, cell ->
            if (index % 2 == 1) cell.copy(
                top = cell.top + (index % 5) * 2,
                bottom = cell.bottom + (index % 5) * 2
            ) else cell
        }
        val detected = PdfHorizontalAnswerGridReader.find(cells, 1000, 1500)
        assertEquals(1, detected.size)
        assertEquals(10, detected.single().answers.size)
        assertEquals("C", detected.single().answers[10])
    }

}
