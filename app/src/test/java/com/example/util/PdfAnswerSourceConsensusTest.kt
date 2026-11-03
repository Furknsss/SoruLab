package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfAnswerSourceConsensusTest {
    @Test fun independentCompleteSourceRestoresMissingFooterCells() {
        val geometry = mapOf(1 to "E", 2 to "B", 3 to "C")
        val text = mapOf(1 to "E", 2 to "B", 3 to "C", 4 to "D", 5 to "A")
        val combined = PdfAnswerSourceConsensus.combine(geometry, text)
        assertEquals(5, combined.size)
        assertEquals("A", combined[5])
    }

    @Test fun contradictoryAnswerCannotBeMarkedVerified() {
        val combined = PdfAnswerSourceConsensus.combine(
            mapOf(1 to "A", 2 to "B", 3 to "C"),
            mapOf(1 to "D", 2 to "B", 3 to "C")
        )
        assertNull(combined[1])
        assertEquals("B", combined[2])
    }

    @Test fun independentChannelsCanEachHaveExclusiveVerifiedCells() {
        val combined = PdfAnswerSourceConsensus.combine(
            mapOf(1 to "A", 2 to "B"), mapOf(2 to "B", 3 to "D")
        )
        assertEquals(mapOf(1 to "A", 2 to "B", 3 to "D"), combined)
    }
}
