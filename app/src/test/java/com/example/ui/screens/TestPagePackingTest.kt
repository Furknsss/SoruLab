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

    @Test fun `nearFitScaleAdjustments eliminates 40 percent bottom void when next image overflows by 7 percent`() {
        // Available = 900px, gap = 16px. Q0 = 510px (56% of column -> 44% bottom void!), Q1 = 435px.
        // 510 + 16 + 435 = 961px (only 6.7% over 900px). Near-fit trims both by ~0.93f so they pack onto 1 column!
        val heights = listOf(510, 435, 420, 410)
        val scalable = listOf(true, true, true, true)
        val initial = packMeasuredTestPages(heights, 900, 16, 2)
        val trims = computeNearFitScaleAdjustments(heights, scalable, initial, 900, 16)
        assertTrue("Near-fit must compute a crisp >= 0.87 trim factor", trims[0] in 0.87f..0.96f)
        val adjustedHeights = heights.mapIndexed { i, h -> (h * trims[i]).toInt() }
        val repacked = packMeasuredTestPages(adjustedHeights, 900, 16, 2)
        assertEquals("All 4 questions now fit cleanly on 1 balanced 2-column page without bottom voids", 1, repacked.size)
        assertValid(adjustedHeights, repacked, 900, 16)
    }

    @Test fun `booklet345 never crams 3 medium math questions into one column and leaves natural solving space`() {
        // Exact Trigonometry test proportions where old packing crammed 3+3 onto 1 page with zero solving space
        val heights = listOf(185, 175, 360, 410, 195, 170)
        val available = 880
        val pages = packBookletPages345(heights, available, 2)
        assertEquals(heights.indices.toList(), pages.flatMap { it.columns.flatten() })
        assertTrue("Must not cram all 6 math questions onto 1 cramped sheet", pages.size == 2)
        pages.forEach { page ->
            page.columns.filter { it.isNotEmpty() }.forEach { col ->
                assertTrue("Math columns must hold at most 2 questions, got ${col.size}", col.size <= 2)
                if (col.size >= 2) {
                    val ink = col.sumOf { heights[it] }
                    assertTrue(
                        "Multi-question column must reserve >= 20% natural solving workspace: ink=$ink available=$available",
                        ink <= (available * 0.80).toInt()
                    )
                }
            }
        }
    }

    @Test fun `booklet345 pairs tall diagram question with short question like 345 All Star reference`() {
        // Exact ÜçDörtBeş All Star reference page: Q5 (360) + Q6 (310) on Left; Q7 tall graph (500) + Q8 short (130) on Right
        val heights = listOf(360, 310, 500, 130)
        val pages = packBookletPages345(heights, 880, 2)
        assertEquals(1, pages.size)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), pages.single().columns)
    }

    @Test fun `booklet345 random mixed tests preserve reading order and solving workspace`() {
        val random = Random(345)
        repeat(100) {
            val heights = List(random.nextInt(1, 45)) { random.nextInt(80, 1150) }
            val available = random.nextInt(550, 1150)
            for (columns in 1..2) {
                val pages = packBookletPages345(heights, available, columns)
                assertEquals(heights.indices.toList(), pages.flatMap { it.columns.flatten() })
                pages.forEach { page ->
                    page.columns.forEach { col ->
                        assertTrue("Booklet column never exceeds 3 items", col.size <= 3)
                        if (col.size >= 2) {
                            val ink = col.sumOf { heights[it] }
                            assertTrue("Multi-item column reserves solving space", ink <= available)
                        }
                    }
                }
            }
        }
    }

    private fun assertValid(heights: List<Int>, pages: List<TestPagePacking>, available: Int, gap: Int) {
        assertEquals(heights.indices.toList(), pages.flatMap { it.columns.flatten() })
        pages.forEach { page -> page.columns.forEach { column ->
            val height = column.sumOf { heights[it] } + (column.size - 1).coerceAtLeast(0) * gap
            assertTrue("Column overflows: $column, height=$height", height <= available || column.size == 1)
        } }
    }
}
