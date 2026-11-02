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
    fun `front matter markers are skipped and valid test pages are kept`() {
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
    fun `first five pages without question and option structure are skipped`() {
        assertTrue(
            StudioImageAndPdfEngine.shouldSkipQuestionPage(
                text = "Bölüm 1\nİçindekiler",
                topHeaderText = "Bölüm 1",
                pageIndex = 4,
                totalPages = 120
            )
        )
        assertFalse(
            StudioImageAndPdfEngine.shouldSkipQuestionPage(
                text = "1. Soru kökü\nA) Bir\nB) İki",
                topHeaderText = "Ünite 1",
                pageIndex = 4,
                totalPages = 120
            )
        )
    }

    @Test
    fun `answer key parser supports compact and separated formats`() {
        assertEquals(
            mapOf(1 to "A", 2 to "B", 3 to "C", 4 to "D"),
            StudioImageAndPdfEngine.parseAnswerKeyRegex("1A 2-B 3. C 4) D")
        )
    }

    @Test
    fun `answer key parser refuses conflicting repeated question numbers`() {
        assertEquals(
            mapOf(2 to "C"),
            StudioImageAndPdfEngine.parseAnswerKeyRegex("1A 2C test 1: B 2C")
        )
    }

    @Test
    fun `answer key merge invalidates conflicts across separate pages`() {
        val answers = mutableMapOf(1 to "A")
        val ambiguous = mutableSetOf<Int>()

        StudioImageAndPdfEngine.mergeAnswerKeyCandidates(
            answers,
            ambiguous,
            listOf(1 to "B", 2 to "C", 1 to "A")
        )

        assertEquals(mapOf(2 to "C"), answers)
        assertEquals(setOf(1), ambiguous)
    }

    @Test
    fun `answer key marker is required for document-wide answer extraction`() {
        assertTrue(StudioImageAndPdfEngine.hasAnswerKeyMarker("CEVAP ANAHTARI"))
        assertFalse(StudioImageAndPdfEngine.hasAnswerKeyMarker("1-A 2-C"))
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

    @Test
    fun `printed question number is masked but following stem is preserved`() {
        // OCR may accidentally return a bounding box including the beginning
        // of the stem, not only the printed 1. prefix.
        val rect = StudioImageAndPdfEngine.safePrintedQuestionNumberMask(
            anchorLeft = 75, anchorTop = 130,
            anchorRight = 260, anchorBottom = 151,
            cropLeft = 65, cropTop = 120,
            cropWidth = 400, cropHeight = 280
        )
        assertTrue(rect != null)
        assertTrue(rect!!.left <= 10f)
        assertTrue(rect.right < 65f) // Do not remove the word after "1."
        assertTrue(rect.bottom < 40f)
    }

    @Test
    fun `do not remove text mistaken for a number outside question prefix`() {
        val rect = StudioImageAndPdfEngine.safePrintedQuestionNumberMask(
            anchorLeft = 280, anchorTop = 290,
            anchorRight = 325, anchorBottom = 310,
            cropLeft = 65, cropTop = 120,
            cropWidth = 400, cropHeight = 280
        )
        assertTrue(rect == null)
    }


}
