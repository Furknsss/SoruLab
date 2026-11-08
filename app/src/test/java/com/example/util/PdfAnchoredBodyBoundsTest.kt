package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfAnchoredBodyBoundsTest {
    @Test
    fun `first printed question above old six percent margin remains visible`() {
        val first = QuestionNumberAnchor(1, 85, 44, 110, 64)
        assertEquals(32, PdfAnchoredBodyBounds.firstQuestionTop(1000, 65, listOf(first)))
        assertEquals(65, PdfAnchoredBodyBounds.firstQuestionTop(1000, 65, emptyList()))
    }

    @Test
    fun `last E choice below old bottom margin remains visible`() {
        val options = listOf(
            QuestionOptionAnchor("A", 100, 830, 135, 847),
            QuestionOptionAnchor("B", 100, 860, 135, 877),
            QuestionOptionAnchor("C", 100, 890, 135, 907),
            QuestionOptionAnchor("D", 100, 925, 135, 942),
            QuestionOptionAnchor("E", 100, 950, 135, 963)
        )
        assertEquals(971, PdfAnchoredBodyBounds.lastOptionBottom(1000, 945, options, null))
    }

    @Test
    fun `verified printed answer key remains a hard limit after final choice`() {
        val options = listOf(
            QuestionOptionAnchor("A", 100, 830, 135, 847),
            QuestionOptionAnchor("B", 100, 860, 135, 877),
            QuestionOptionAnchor("C", 100, 890, 135, 907),
            QuestionOptionAnchor("D", 100, 925, 135, 942),
            QuestionOptionAnchor("E", 100, 950, 135, 963)
        )
        assertEquals(969, PdfAnchoredBodyBounds.lastOptionBottom(1000, 945, options, 970))
        assertEquals(945, PdfAnchoredBodyBounds.lastOptionBottom(1000, 945, options, 930))
    }

    @Test
    fun `orphan E or faraway option cannot extend the crop into footer`() {
        val onlyE = listOf(QuestionOptionAnchor("E", 100, 950, 135, 963))
        assertEquals(945, PdfAnchoredBodyBounds.lastOptionBottom(1000, 945, onlyE, null))
        assertEquals(945, PdfAnchoredBodyBounds.lastOptionBottom(
            1000, 945,
            listOf(
                QuestionOptionAnchor("A", 100, 110, 135, 120),
                QuestionOptionAnchor("B", 100, 140, 135, 150),
                QuestionOptionAnchor("E", 100, 950, 135, 963)
            ),
            null
        ))
    }
}
