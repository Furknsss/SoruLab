package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals(900, bounds.left)
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
}
