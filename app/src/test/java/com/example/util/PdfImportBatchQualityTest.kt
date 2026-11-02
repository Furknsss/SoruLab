package com.example.util

import org.junit.Assert.*
import org.junit.Test

class PdfImportBatchQualityTest {
    private fun test(scope: String, numbers: IntRange) =
        numbers.map { PdfImportBatchQuality.Item(scope, it) }

    @Test fun `complete ten-question printed test passes`() {
        assertTrue(PdfImportBatchQuality.invalidScopes(
            test("sinir sistemi~normal~1", 1..10),
            mapOf("sinir sistemi~normal~1" to (1..10).toSet())
        ).isEmpty())
    }

    @Test fun `missing eighth question blocks entire test`() {
        val scope = "sinir sistemi~normal~1"
        val items = test(scope, 1..7) + test(scope, 9..10)
        assertEquals(setOf(scope), PdfImportBatchQuality.invalidScopes(
            items, mapOf(scope to (1..10).toSet())
        ))
    }

    @Test fun `missing final question also blocks even without a number gap`() {
        val scope = "sinir sistemi~normal~1"
        assertEquals(setOf(scope), PdfImportBatchQuality.invalidScopes(
            test(scope, 1..9), mapOf(scope to (1..10).toSet())
        ))
    }

    @Test fun `duplicate source number cannot be counted as an extra original`() {
        val scope = "sinir sistemi~normal~2"
        val items = test(scope, 1..10) + PdfImportBatchQuality.Item(scope, 7)
        assertEquals(setOf(scope), PdfImportBatchQuality.invalidScopes(
            items, mapOf(scope to (1..10).toSet())
        ))
    }
}
