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

    private fun assertValid(heights: List<Int>, pages: List<TestPagePacking>, available: Int, gap: Int) {
        assertEquals(heights.indices.toList(), pages.flatMap { it.columns.flatten() })
        pages.forEach { page -> page.columns.forEach { column ->
            val height = column.sumOf { heights[it] } + (column.size - 1).coerceAtLeast(0) * gap
            assertTrue("Column overflows: $column, height=$height", height <= available || column.size == 1)
        } }
    }
}
