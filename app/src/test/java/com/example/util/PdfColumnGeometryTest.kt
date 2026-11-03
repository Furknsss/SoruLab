package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfColumnGeometryTest {
    private fun q(n: Int, x: Int, y: Int) =
        QuestionNumberAnchor(n, x, y, x + 14, y + 16)

    @Test
    fun `right-column starts near fifty percent must include printed number`() {
        val anchors = listOf(q(1, 57, 82), q(2, 57, 288), q(3, 57, 607),
            q(4, 315, 82), q(5, 315, 508))
        val columns = PdfColumnGeometry.columns(
            623, gutterX = 312, gutterMargin = 11, outerMargin = 13,
            isDualColumn = true, anchors = anchors, bodyTop = 55, bodyBottom = 753
        )
        assertEquals(2, columns.size)
        assertTrue(columns[1].left <= 315)
        assertTrue(columns[1].right > 315)
        assertTrue(columns[0].right < columns[1].left)
        assertTrue(315 - columns[1].left < (columns[1].right - columns[1].left) / 4)
    }

    @Test
    fun `continuation pages with shifted number margins also include both columns`() {
        val anchors = listOf(q(6, 71, 82), q(7, 71, 300), q(8, 71, 525),
            q(9, 330, 82), q(10, 330, 314))
        val columns = PdfColumnGeometry.columns(
            623, gutterX = 321, gutterMargin = 11, outerMargin = 13,
            isDualColumn = true, anchors = anchors, bodyTop = 50, bodyBottom = 750
        )
        assertEquals(2, columns.size)
        assertTrue(columns[1].left <= 330)
        assertTrue(columns[0].left <= 71)
        assertTrue(columns[0].right < columns[1].left)
    }

    @Test
    fun `image only PDF falls back to pixel detected gutter without inventing questions`() {
        val columns = PdfColumnGeometry.columns(
            1100, gutterX = 556, gutterMargin = 14, outerMargin = 12,
            isDualColumn = true, anchors = emptyList(), bodyTop = 60, bodyBottom = 900
        )
        assertEquals(2, columns.size)
        assertEquals(570, columns[1].left)
    }

    @Test
    fun `single column never splits body even with graph labels`() {
        val columns = PdfColumnGeometry.columns(
            623, 311, 11, 13, isDualColumn = false,
            anchors = listOf(q(1, 310, 200)), bodyTop = 40, bodyBottom = 750
        )
        assertEquals(1, columns.size)
        assertEquals(13, columns[0].left)
    }
    @Test
    fun `off-center right column in a wide book keeps the first question number`() {
        val anchors = listOf(q(11, 110, 100), q(12, 110, 390),
            q(13, 720, 110), q(14, 720, 465))
        val columns = PdfColumnGeometry.columns(
            1200, gutterX = 705, gutterMargin = 19, outerMargin = 20,
            isDualColumn = true, anchors = anchors, bodyTop = 55, bodyBottom = 790
        )
        assertEquals(2, columns.size)
        assertTrue(columns[1].left <= 720)
        assertTrue(columns[1].left > 600)
        assertTrue(columns[0].right < columns[1].left)
    }

    @Test
    fun `very narrow paper does not create overlapping columns`() {
        val columns = PdfColumnGeometry.columns(
            300, gutterX = 155, gutterMargin = 8, outerMargin = 35,
            isDualColumn = true, anchors = emptyList(), bodyTop = 20, bodyBottom = 450
        )
        assertEquals(1, columns.size)
        assertTrue(columns.single().right > columns.single().left)
    }

}
