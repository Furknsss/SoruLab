package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfHorizontalAnswerGridTest {
    @Test
    fun `compact footer pairs require a complete sequential row without prose`() {
        val footer = PdfOcrTextCell("1-A 2.C 3 B 4-D 5)E", 100, 1400, 800, 1420)
        val grids = PdfHorizontalAnswerGridReader.find(listOf(footer), 1000, 1500)
        assertEquals(mapOf(1 to "A", 2 to "C", 3 to "B", 4 to "D", 5 to "E"), grids.single().answers)
        assertTrue(PdfHorizontalAnswerGridReader.find(listOf(footer.copy(text = "Soru 1-A 2-C 3-B 4-D")), 1000, 1500).isEmpty())
        assertTrue(PdfHorizontalAnswerGridReader.find(listOf(footer.copy(text = "1-A 2-C 2-B 4-D")), 1000, 1500).isEmpty())
    }

    @Test
    fun `compact keys near page header and center work without publisher heuristics`() {
        for (top in listOf(40, 250, 800, 1350)) {
            val row = PdfOcrTextCell(
                "1-A 2.C 3 B 4-D 5)E", 120, top, 860, top + 24
            )
            val grids = PdfHorizontalAnswerGridReader.find(listOf(row), 1000, 1500)
            assertEquals("position=$top", 1, grids.size)
            assertEquals(mapOf(1 to "A", 2 to "C", 3 to "B", 4 to "D", 5 to "E"), grids.single().answers)
        }
    }

    @Test
    fun `ordinary numbered question prose is not interpreted as a compact answer strip`() {
        val ordinary = listOf(
            PdfOcrTextCell("Soru 1-A 2-B 3-C 4-D", 100, 100, 900, 130),
            PdfOcrTextCell("1-A 2-C 2-D 4-E", 100, 300, 900, 330),
            PdfOcrTextCell("1-A 2-B 3-C", 100, 500, 900, 530)
        )
        assertTrue(PdfHorizontalAnswerGridReader.find(ordinary, 1000, 1500).isEmpty())
    }

    @Test
    fun `analysis and regular tests with same number do not share keys`() {
        val keys = PdfScopedAnswerKeys(setOf("sinir sistemi"))
        keys.addOcrRows(listOf("Sinir Sistemi", "ANALİZ 1 1-A 2-B 3-C", "TEST 1 1-D 2-E 3-A"))
        assertEquals("A", keys.get(PdfAnswerScope("sinir sistemi", 1, false, "analiz"), 1))
        assertEquals("D", keys.get(PdfAnswerScope("sinir sistemi", 1, false), 1))
    }

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


    @Test
    fun `narrow four cell printed key on a wide page remains readable`() {
        // Matches the 1600 x 2200 Android offline import layout. A genuine
        // short 1..4 strip must not be mistaken for a missing answer key.
        val cells = listOf(
            PdfOcrTextCell("1", 360, 1990, 377, 2022),
            PdfOcrTextCell("2", 400, 1990, 417, 2022),
            PdfOcrTextCell("3", 440, 1990, 457, 2022),
            PdfOcrTextCell("4", 480, 1990, 497, 2022),
            PdfOcrTextCell("A", 360, 2070, 377, 2102),
            PdfOcrTextCell("B", 400, 2070, 417, 2102),
            PdfOcrTextCell("C", 440, 2070, 457, 2102),
            PdfOcrTextCell("D", 480, 2070, 497, 2102)
        )
        val actual = PdfHorizontalAnswerGridReader.find(cells, 1600, 2200)
        assertEquals(mapOf(1 to "A", 2 to "B", 3 to "C", 4 to "D"),
            actual.single().answers)
        assertTrue(PdfHorizontalAnswerGridReader.find(
            cells.filter { it.text != "D" }, 1600, 2200
        ).isEmpty())
    }

}
