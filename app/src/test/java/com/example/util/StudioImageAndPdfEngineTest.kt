package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StudioImageAndPdfEngineTest {

    @Test
    fun `normalizes inverted and out of range crop coordinates`() {
        val bounds = normalizeCropBounds(
            ymin = 980,
            xmin = 990,
            ymax = -40,
            xmax = -20
        )

        assertEquals(100, bounds.top)
        assertEquals(70, bounds.left)
        assertEquals(900, bounds.bottom)
        assertEquals(930, bounds.right)
        assertTrue(bounds.right > bounds.left)
        assertTrue(bounds.bottom > bounds.top)
    }

    @Test
    fun `preserves valid crop coordinates`() {
        val bounds = normalizeCropBounds(
            ymin = 180,
            xmin = 120,
            ymax = 760,
            xmax = 840
        )

        assertEquals(NormalizedCropBounds(180, 120, 760, 840), bounds)
    }

    @Test
    fun `clamps zero or negative coordinates into valid margins`() {
        val bounds = normalizeCropBounds(
            ymin = 0,
            xmin = -50,
            ymax = 50,
            xmax = 30
        )

        assertEquals(100, bounds.top)
        assertEquals(70, bounds.left)
        assertTrue(bounds.right >= bounds.left + 60)
        assertTrue(bounds.bottom >= bounds.top + 60)
    }

    @Test
    fun `clamps coordinates exceeding page size to maximum margins`() {
        val bounds = normalizeCropBounds(
            ymin = 850,
            xmin = 880,
            ymax = 1200,
            xmax = 1500
        )

        assertTrue(bounds.right <= 930)
        assertTrue(bounds.bottom <= 900)
        assertTrue(bounds.left in 70..900)
        assertTrue(bounds.top in 100..860)
    }

    @Test
    fun `front matter is skipped only when question structure is absent`() {
        assertTrue(
            StudioImageAndPdfEngine.shouldSkipQuestionPage(
                text = "İÇİNDEKİLER\nBölüm 1 ........ 12",
                topHeaderText = "İÇİNDEKİLER",
                pageIndex = 0,
                totalPages = 120
            )
        )
        assertFalse(
            StudioImageAndPdfEngine.shouldSkipQuestionPage(
                text = "1. Aşağıdakilerden hangisi doğrudur?\nA) Bir\nB) İki\nC) Üç\nD) Dört\nE) Beş",
                topHeaderText = "ÜNİTE 1",
                pageIndex = 0,
                totalPages = 120
            )
        )
    }

    @Test
    fun `ellipsis in question text does not skip question page`() {
        assertFalse(
            StudioImageAndPdfEngine.shouldSkipQuestionPage(
                text = "1. Dizide ... ifadesinin değeri kaçtır?\nA) 1\nB) 2\nC) 3\nD) 4\nE) 5",
                topHeaderText = "Diziler",
                pageIndex = 2,
                totalPages = 120
            )
        )
    }
}
