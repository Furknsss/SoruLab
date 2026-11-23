package com.example.ui.screens

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class TestPagePackingTest {
    @Test fun `all twelve questions fit without an arbitrary four per column limit`() {
        val pages = packMeasuredTestPages(List(12) { 110 }, 900, 24, 2)
        assertEquals(1, pages.size)
        assertEquals(listOf(6, 6), pages.single().columns.map { it.size })
    }

    @Test fun `mixed heights balance both columns and pages instead of leaving a sparse last page`() {
        val heights = listOf(300, 180, 140, 260, 120, 210, 170, 130, 320, 180, 220, 140)
        val pages = packMeasuredTestPages(heights, 800, 24, 2)
        assertEquals(2, pages.size)
        assertTrue(pages.all { it.columns.all { column -> column.size >= 2 } })
        assertValid(heights, pages, 800, 24)
    }

    @Test fun `oversized question stays intact on its own scrollable page`() {
        val heights = listOf(100, 1600, 100)
        val pages = packMeasuredTestPages(heights, 700, 24, 2)
        assertTrue(pages.any { it.columns.flatten() == listOf(1) })
        assertValid(heights, pages, 700, 24)
    }

    @Test fun `single column never creates an empty right hand column`() {
        val heights = List(11) { 160 }
        val pages = packMeasuredTestPages(heights, 750, 24, 1)
        assertEquals(3, pages.size)
        assertTrue(pages.all { it.columns.size == 1 })
        assertValid(heights, pages, 750, 24)
    }

    @Test fun `random mixed tests preserve reading order and never overflow a column`() {
        val random = Random(42)
        repeat(100) {
            val heights = List(random.nextInt(1, 50)) { random.nextInt(50, 1200) }
            val available = random.nextInt(400, 1100)
            for (columns in 1..2) {
                assertValid(heights, packMeasuredTestPages(heights, available, 24, columns), available, 24)
            }
        }
    }

    @Test fun `empty test has a stable empty page`() {
        assertTrue(packMeasuredTestPages(emptyList(), 700, 24, 2).single().columns.flatten().isEmpty())
    }

    @Test fun `non final pages never have a single orphan question with empty right column`() {
        // 5 questions that fit 1 per column (450 + 12 + 450 > 800) require 3 pages:
        // Pages 1 and 2 must be full 2-column pages (2, 2), and any remainder lands ONLY on the final page (1).
        val heights = List(5) { 450 }
        val pages = packMeasuredTestPages(heights, 800, 12, 2)
        assertEquals(3, pages.size)
        assertEquals(listOf(2, 2, 1), pages.map { it.columns.flatten().size })
        assertTrue("Page 1 must populate both columns", pages[0].columns.all { it.size == 1 })
        assertTrue("Page 2 must populate both columns", pages[1].columns.all { it.size == 1 })
        assertValid(heights, pages, 800, 12)
    }

    @Test fun `orphan last page rebalances with previous page instead of leaving lone question`() {
        // 5 questions of 350px (2 per column fit in 800px) -> must rebalance to 3 + 2, not 4 + 1.
        val heights = List(5) { 350 }
        val pages = packMeasuredTestPages(heights, 800, 12, 2)
        assertEquals(2, pages.size)
        assertTrue("No page should have an empty right column", pages.all { it.columns.all { col -> col.isNotEmpty() } })
        assertTrue("Last page must have at least 2 questions", pages.last().columns.flatten().size >= 2)
        assertValid(heights, pages, 800, 12)
    }

    @Test fun `moderately tall question occupies its own column instead of leaving opposite column blank`() {
        // Q2 is 740px (1.06x of 700px available) — should occupy its own column alongside normal questions
        // in the opposite column rather than creating a 1-question page with a blank right column.
        val heights = listOf(300, 300, 740, 300, 300, 300)
        val pages = packMeasuredTestPages(heights, 700, 12, 2)
        assertEquals(2, pages.size)
        assertTrue("Every page must populate both columns", pages.all { it.columns.all { col -> col.isNotEmpty() } })
        assertValid(heights, pages, 700, 12)
    }

    @Test fun `typical YKS test with mixed questions packs densely with balanced columns`() {
        val heights = listOf(220, 260, 190, 310, 240, 210, 280, 195, 250, 230)
        val pages = packMeasuredTestPages(heights, 760, 12, 2)
        assertEquals(2, pages.size)
        assertTrue("Both pages should be well filled with >= 2 questions per column",
            pages.all { it.columns.all { col -> col.size >= 2 } })
        assertValid(heights, pages, 760, 12)
    }

    private fun assertValid(heights: List<Int>, pages: List<TestPagePacking>, available: Int, gap: Int) {
        assertEquals(heights.indices.toList(), pages.flatMap { it.columns.flatten() })
        pages.forEach { page -> page.columns.forEach { column ->
            val height = column.sumOf { heights[it] } + (column.size - 1).coerceAtLeast(0) * gap
            assertTrue("Column overflows: $column, height=$height", height <= available || column.size == 1)
        } }
    }
}
