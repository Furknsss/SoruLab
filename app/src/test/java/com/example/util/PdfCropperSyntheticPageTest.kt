package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Synthetic, originally authored page: no publisher material is stored in Git.
 * Checks the real crop engine when OCR has located printed numbers and options.
 */
@RunWith(RobolectricTestRunner::class)
class PdfCropperSyntheticPageTest {
    @Test
    fun `two column question page keeps three independent crop regions`() {
        val bmp = Bitmap.createBitmap(1400, 1800, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 30f
        }
        val canvas = Canvas(bmp)
        fun text(x: Float, y: Float, label: String) = canvas.drawText(label, x, y, paint)

        text(110f, 270f, "1. Sol sutun soru bir")
        text(155f, 320f, "A) Secenek A")
        text(155f, 368f, "B) Secenek B")
        text(110f, 890f, "2. Sol sutun soru iki")
        text(155f, 940f, "A) Secenek A")
        text(155f, 990f, "B) Secenek B")
        text(810f, 270f, "3. Sag sutun soru uc")
        text(855f, 320f, "A) Secenek A")
        text(855f, 368f, "B) Secenek B")

        // Render deterministic text-like strokes directly into the bitmap.
        // Robolectric may not rasterize Canvas.drawText the same way as a device,
        // so the pixel segmentation test must not accidentally analyze blank paper.
        fun strokes(x: Int, top: Int) {
            val ink = IntArray(450 * 7) { Color.BLACK }
            for (row in 0..6) {
                val y = top + row * 35
                bmp.setPixels(ink, 0, 450, x, y, 450, 7)
            }
        }
        strokes(160, 290)
        strokes(160, 900)
        strokes(855, 290)
        val pixelCheck = IntArray(1400 * 1800)
        bmp.getPixels(pixelCheck, 0, 1400, 0, 0, 1400, 1800)
        assertTrue("The synthetic page must visibly contain black pixels",
            pixelCheck.any { it == Color.BLACK })

        val numbers = listOf(
            QuestionNumberAnchor(1, 110, 235, 145, 270),
            QuestionNumberAnchor(2, 110, 855, 145, 890),
            QuestionNumberAnchor(3, 810, 235, 845, 270)
        )
        val opts = listOf(
            QuestionOptionAnchor("A", 155, 290, 175, 323),
            QuestionOptionAnchor("B", 155, 338, 175, 371),
            QuestionOptionAnchor("A", 155, 910, 175, 943),
            QuestionOptionAnchor("B", 155, 958, 175, 993),
            QuestionOptionAnchor("A", 855, 290, 875, 323),
            QuestionOptionAnchor("B", 855, 338, 875, 371)
        )

        val items = StudioImageAndPdfEngine.cropRenderedPageLocalDualColumn(
            context = RuntimeEnvironment.getApplication(),
            pageBitmap = bmp,
            pageIndex = 3,
            totalPages = 8,
            reusablePixelsRef = arrayOfNulls<IntArray>(1),
            questionNumberAnchors = numbers,
            optionAnchors = opts
        )
        try {
            assertEquals("Three independent questions must be returned", 3, items.size)
            assertEquals(setOf(1, 2, 3), items.map { it.questionOrderInPage }.toSet())
            assertEquals("Keep the left and right columns separate", setOf(0, 1),
                items.map { it.columnIndex }.toSet())
            val incompleteOptionsCrop = items.first { it.questionOrderInPage == 1 }
            assertTrue("Partial A/B OCR must retain the later stem and option pixels",
                incompleteOptionsCrop.boxNormalized[2] >= 500f / 1800f)
            assertTrue(items.all { it.croppedImagePath.isNotBlank() })
            assertTrue(items.all { File(it.croppedImagePath).isFile })
            // The second printed question starts at pixel 855. The first
            // crop must end BEFORE that number, even after ink expansion.
            val first = items.first { it.columnIndex == 0 && it.questionOrderInPage == 1 }
            val second = items.first { it.columnIndex == 0 && it.questionOrderInPage == 2 }
            assertTrue("First crop swallowed the next printed question",
                first.boxNormalized[2] < 855f / 1800f)
            assertTrue("Adjacent question crops overlap",
                first.boxNormalized[2] <= second.boxNormalized[0])
        } finally {
            items.forEach { File(it.croppedImagePath).delete() }
        }

        // OCR misses printed 2. The earlier question still must not swallow
        // all of question 2: its FIRST E answer option ends question 1.
        val fewerAnchors = numbers.filter { it.number != 2 }
        val withEndingOption = opts + listOf(
            QuestionOptionAnchor("C", 155, 375, 175, 390),
            QuestionOptionAnchor("D", 155, 390, 175, 405),
            QuestionOptionAnchor("E", 155, 405, 175, 430)
        )
        val conservative = StudioImageAndPdfEngine.cropRenderedPageLocalDualColumn(
            context = RuntimeEnvironment.getApplication(),
            pageBitmap = bmp,
            pageIndex = 3,
            totalPages = 8,
            reusablePixelsRef = arrayOfNulls<IntArray>(1),
            questionNumberAnchors = fewerAnchors,
            optionAnchors = withEndingOption
        )
        try {
            val first = conservative.first { it.questionOrderInPage == 1 }
            assertTrue("Missing next OCR anchor caused merged questions",
                first.boxNormalized[2] < 855f / 1800f)
        } finally {
            conservative.forEach { File(it.croppedImagePath).delete() }
            bmp.recycle()
        }
    }

    @Test
    fun scannedStylePageWithFiveUnevenQuestionsKeepsEveryRegion() {
        // Independently authored visual fixture: 3 questions on the left,
        // 2 on the right, with tall diagram-like strokes and wide blank gaps.
        // Geometry models a real scanned textbook layout, not publisher text.
        val width = 1300
        val height = 1700
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val ink = IntArray(360 * 6) { Color.BLACK }
        fun content(x: Int, rows: List<Int>) {
            rows.forEach { y -> bitmap.setPixels(ink, 0, 360, x, y, 360, 6) }
        }
        content(150, listOf(230, 290, 415, 470, 550, 616))
        content(150, listOf(860, 905, 950, 995, 1060))
        content(150, listOf(1300, 1345, 1390, 1450))
        content(720, listOf(230, 290, 390, 465, 520, 610))
        content(720, listOf(860, 905, 1080, 1160, 1260, 1370))

        val numbers = listOf(
            QuestionNumberAnchor(1, 105, 190, 133, 216),
            QuestionNumberAnchor(2, 105, 825, 133, 850),
            QuestionNumberAnchor(3, 105, 1265, 133, 1290),
            QuestionNumberAnchor(4, 680, 190, 708, 216),
            QuestionNumberAnchor(5, 680, 825, 708, 850)
        )
        val options = listOf(
            QuestionOptionAnchor("A", 150, 550, 170, 572),
            QuestionOptionAnchor("B", 150, 615, 170, 638),
            QuestionOptionAnchor("A", 150, 950, 170, 972),
            QuestionOptionAnchor("B", 150, 1058, 170, 1080),
            QuestionOptionAnchor("A", 150, 1380, 170, 1402),
            QuestionOptionAnchor("B", 150, 1447, 170, 1469),
            QuestionOptionAnchor("A", 720, 520, 740, 542),
            QuestionOptionAnchor("B", 720, 608, 740, 630),
            QuestionOptionAnchor("A", 720, 1260, 740, 1282),
            QuestionOptionAnchor("B", 720, 1368, 740, 1390)
        )
        val crops = StudioImageAndPdfEngine.cropRenderedPageLocalDualColumn(
            context = RuntimeEnvironment.getApplication(),
            pageBitmap = bitmap,
            pageIndex = 3,
            totalPages = 256,
            reusablePixelsRef = arrayOfNulls<IntArray>(1),
            questionNumberAnchors = numbers,
            optionAnchors = options
        )
        try {
            assertEquals("All five source questions must be cropped", 5, crops.size)
            assertEquals(listOf(1, 2, 3, 4, 5),
                crops.map { it.questionOrderInPage }.sorted())
            assertEquals(3, crops.count { it.columnIndex == 0 })
            assertEquals(2, crops.count { it.columnIndex == 1 })
            val left = crops.filter { it.columnIndex == 0 }
                .sortedBy { it.boxNormalized[0] }
            val right = crops.filter { it.columnIndex == 1 }
                .sortedBy { it.boxNormalized[0] }
            assertTrue("First diagram-like stroke must be retained",
                left[0].boxNormalized[2] > 620f / height)
            assertTrue("Left column crops must not overlap",
                left.zipWithNext().all { (a, b) ->
                    a.boxNormalized[2] <= b.boxNormalized[0]
                })
            assertTrue("Right column crops must not overlap",
                right.zipWithNext().all { (a, b) ->
                    a.boxNormalized[2] <= b.boxNormalized[0]
                })
            assertTrue(crops.all { File(it.croppedImagePath).isFile })
        } finally {
            crops.forEach { File(it.croppedImagePath).delete() }
            bitmap.recycle()
        }
    }

}
