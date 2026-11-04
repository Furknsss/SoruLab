package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Anonymized spatial fixtures from independently audited book pages.
 * Only printed-number coordinates are used; no publisher art, prose, answers
 * or original PDF bytes are ever committed to the repository.
 *
 * These tests exercise the actual native PDF number detector rather than
 * a synthetic pre-filter that would silently remove graph axis labels.
 */
class PdfVectorQuestionAnchorsLayoutCorpusTest {
    private fun glyph(n: Int, x: Float, y: Float) =
        PdfSourceGlyph("$n.", x, y, if (n >= 10) 18f else 11f, 9f)

    private fun detect(glyphs: List<PdfSourceGlyph>): List<Int> =
        PdfVectorQuestionAnchors.find(glyphs, 623f, 807f).map { it.number }

    @Test fun realGeometryFigureFiveMustNotReplaceTheActualFifthQuestion() {
        val glyphs = listOf(
            glyph(1, 56.65f, 82.4f),
            glyph(2, 56.65f, 455.2f),
            glyph(5, 90.7f, 643.64f), // Number printed INSIDE a diagram
            glyph(3, 315.35f, 82.4f),
            glyph(4, 315.35f, 399.85f),
            glyph(5, 315.35f, 582.45f)
        )
        assertEquals(listOf(1, 2, 3, 4, 5), detect(glyphs))
    }

    @Test fun sevenGraphAxisLabelsCannotOutvoteFourActualPrintedQuestions() {
        val printed = listOf(
            glyph(1, 56.65f, 81.85f),
            glyph(2, 56.65f, 192.45f),
            glyph(3, 315.35f, 81.85f),
            glyph(4, 315.35f, 464.35f)
        )
        val diagramLabels = listOf(
            1 to 298.75f, 2 to 329.1f, 3 to 360.9f,
            4 to 389.8f, 5 to 417.45f, 6 to 443f, 7 to 481.65f
        ).map { (n, y) -> glyph(n, 90.7f, y) } +
            glyph(3, 90.7f, 715.94f)
        assertEquals(listOf(1, 2, 3, 4), detect(printed + diagramLabels))
    }

    @Test fun repeatedGraphOneMustNotEraseAnAsymmetricTwoColumnPage() {
        val glyphs = listOf(
            glyph(1, 56.65f, 81.81f),
            glyph(2, 56.65f, 451.96f),
            glyph(1, 90.7f, 587.33f), // Diagram label, NOT another test
            glyph(3, 315.35f, 81.81f),
            glyph(4, 315.35f, 368.86f),
            glyph(5, 315.35f, 587.91f)
        )
        assertEquals(listOf(1, 2, 3, 4, 5), detect(glyphs))
    }

    @Test fun continuationPageMayBeginWithSevenAndContainDoubleDigitNumbers() {
        val glyphs = listOf(
            glyph(7, 70.85f, 83.86f),
            glyph(8, 70.85f, 587.31f),
            glyph(9, 329.5f, 83.86f),
            glyph(10, 329.5f, 279.46f),
            glyph(11, 329.5f, 472.84f)
        )
        assertEquals(listOf(7, 8, 9, 10, 11), detect(glyphs))
    }
}
