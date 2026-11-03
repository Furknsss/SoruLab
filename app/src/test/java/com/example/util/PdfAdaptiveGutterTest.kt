package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAdaptiveGutterTest {
    private fun q(number: Int, x: Int, y: Int) =
        QuestionNumberAnchor(number, x, y, x + 11, y + 12)

    @Test
    fun `symmetric dual column finds a near center gutter`() {
        val anchors = listOf(q(1, 55, 92), q(2, 55, 320),
            q(3, 315, 90), q(4, 315, 330))
        val window = PdfAdaptiveGutter.window(623, anchors, 35, 710)
        assertTrue(window.start < 310)
        assertTrue(window.end > 310)
        assertTrue(window.preferredX in window.start..window.end)
    }

    @Test
    fun `asymmetric wide layout does not search only fifty percent`() {
        val anchors = listOf(q(10, 112, 91), q(11, 112, 310),
            q(12, 738, 110), q(13, 738, 335))
        val window = PdfAdaptiveGutter.window(1200, anchors, 40, 790)
        assertTrue(window.preferredX > 600)
        assertTrue(window.start > 550)
        assertTrue(window.end < 1050)
    }

    @Test
    fun `image only source has a broad neutral search window`() {
        val window = PdfAdaptiveGutter.window(900, emptyList(), 20, 650)
        assertEquals(450, window.preferredX)
        assertTrue(window.start < 450 && window.end > 450)
    }

    @Test
    fun `nonconsecutive chart labels cannot move the gutter`() {
        val anchors = listOf(q(2, 110, 95), q(4, 110, 310),
            q(7, 740, 95), q(9, 740, 310))
        val window = PdfAdaptiveGutter.window(1200, anchors, 40, 790)
        assertEquals(600, window.preferredX)
    }
}
