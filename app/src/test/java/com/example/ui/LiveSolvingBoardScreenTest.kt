package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.DifficultyLevel
import com.example.data.ExamType
import com.example.data.YksQuestion
import com.example.data.YksTestItem
import com.example.ui.screens.LiveSolvingBoardScreen
import com.example.ui.screens.scoreReportHeadline
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ActiveSolvingSession
import com.example.viewmodel.DrawingTool
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises real session callbacks rather than just checking static labels. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class LiveSolvingBoardScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun oversizedBookPrintIsDownscaledButSmallPrintIsNotShrunkFurther() {
        fun sample(fontPx: Float): android.graphics.Bitmap {
            val bitmap = android.graphics.Bitmap.createBitmap(
                420, 660, android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
            }
            // Explicit ink bands avoid Robolectric host-specific font
            // rendering while testing the exact glyph-row algorithm.
            repeat(8) { index ->
                val y = 35f + index * 73f
                canvas.drawRect(35f, y, 120f, y + fontPx, paint)
                canvas.drawRect(145f, y, 178f, y + fontPx, paint)
            }
            return bitmap
        }
        val normal = sample(14f)
        val huge = sample(54f)
        try {
            val standardScale = com.example.ui.screens.estimateBookTypographyScale(normal, 340f)
            val hugeScale = com.example.ui.screens.estimateBookTypographyScale(huge, 340f)
            org.junit.Assert.assertTrue("Oversized type must shrink more: normal=$standardScale huge=$hugeScale", hugeScale < standardScale)
            org.junit.Assert.assertTrue("Large Barış-style print needs real reduction",
                hugeScale < 0.70f)
            org.junit.Assert.assertTrue("Normal print should remain legible",
                standardScale >= 0.9f)
        } finally {
            normal.recycle()
            huge.recycle()
        }
    }

    @Test
    fun bookFontNormalizationMatchesSmallAndOversizedSourceGlyphs() {
        fun printedQuestion(height: Float): android.graphics.Bitmap {
            val bitmap = android.graphics.Bitmap.createBitmap(
                430, 900, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
            }
            repeat(12) { line ->
                repeat(4) { glyph ->
                    val left = 30f + glyph * 65
                    val top = 24f + line * 70
                    canvas.drawRect(left, top, left + 16f, top + height, paint)
                }
            }
            return bitmap
        }
        val normal = printedQuestion(11f)
        val oversized = printedQuestion(39f)
        try {
            val smallScale = com.example.ui.screens.estimateBookTypographyScale(normal, 190f)
            val hugeScale = com.example.ui.screens.estimateBookTypographyScale(oversized, 190f)
            org.junit.Assert.assertTrue("Normal-size lettering must keep its native width",
                smallScale >= 0.92f)
            org.junit.Assert.assertTrue(
                "Oversized lettering must be normalized toward smaller-print books: small=$smallScale large=$hugeScale",
                hugeScale <= 0.66f && hugeScale < smallScale
            )
        } finally {
            normal.recycle()
            oversized.recycle()
        }
    }

    @Test
    fun base64StoredQuestionImageGetsTheSameTypographyAndRatioCheckAsFileImages() {
        val bitmap = android.graphics.Bitmap.createBitmap(
            430, 640, android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
        }
        repeat(8) { line ->
            repeat(4) { glyph ->
                val x = 30f + glyph * 80
                val y = 30f + line * 72
                canvas.drawRect(x, y, x + 18f, y + 32f, paint)
            }
        }
        val bytes = java.io.ByteArrayOutputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        bitmap.recycle()
        val q = fixtureSession().testItem.questions.first().copy(
            id = "inline_source",
            croppedImagePath = "",
            imageBase64 = android.util.Base64.encodeToString(
                bytes, android.util.Base64.NO_WRAP
            )
        )
        val metrics = com.example.ui.screens.estimateBookImageMeasurements(
            q, java.io.File("/unused_book_file_dir"), 190f
        )
        org.junit.Assert.assertNotNull(metrics)
        // The new visual engine safely trims unused white pixels; its effective
        // aspect ratio must exactly match the same decoded image after trimming,
        // rather than the uncropped PNG's nominal width/height.
        val source = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val displayed = com.example.ui.screens.trimQuestionWhiteMargins(source)
        try {
            val expected = displayed.height.toFloat() / displayed.width
            org.junit.Assert.assertEquals(expected, metrics!!.heightOverWidth, 0.015f)
            org.junit.Assert.assertTrue(metrics.typographyScale < 0.9f)
        } finally {
            if (displayed !== source) displayed.recycle()
            source.recycle()
        }
    }

    @Test
    fun paperFontCalibrationMakesThreePublishersOpticallyComparable() {
        val measured = mapOf(
            "ergi" to com.example.ui.screens.BookImageMeasurements(1.4f, 1f, 7.0f),
            "baris" to com.example.ui.screens.BookImageMeasurements(1.2f, 1f, 18.0f),
            "ucdortbes" to com.example.ui.screens.BookImageMeasurements(1.0f, 1f, 8.4f)
        )
        val scales = com.example.ui.screens.calibratedBookTypographyScales(measured)
        val finalFonts = measured.map { (id, metrics) ->
            metrics.printedGlyphDp!! * scales.getValue(id)
        }
        val smallest = finalFonts.minOrNull()!!
        val largest = finalFonts.maxOrNull()!!
        org.junit.Assert.assertTrue("Mixed publisher glyphs still vary too much: $finalFonts",
            largest / smallest < 1.20f)
        org.junit.Assert.assertTrue("Overprinted Baris must shrink", scales.getValue("baris") < 0.66f)
    }

    @Test
    fun twoColumnPrintedFontDoesNotLeaveBarisTwiceErgiSize() {
        // Regression from an actual Android v1.3.8 screenshot: Ergi 10px,
        // Barış 21px. Since the smaller question already fills its column,
        // normalize the large print DOWN to the smaller reference.
        val measured = mapOf(
            "ergi" to com.example.ui.screens.BookImageMeasurements(1f, 1f, 5f),
            "baris" to com.example.ui.screens.BookImageMeasurements(1f, 1f, 10.5f),
            "ucdortbes" to com.example.ui.screens.BookImageMeasurements(1f, 1f, 5.5f)
        )
        val scales = com.example.ui.screens.calibratedBookTypographyScales(measured)
        val fontSizes = measured.map { (id, m) ->
            (m.printedGlyphDp ?: 0f) * scales.getValue(id)
        }
        org.junit.Assert.assertTrue(
            "Book fonts remain optically unbalanced: $fontSizes",
            fontSizes.maxOrNull()!! / fontSizes.minOrNull()!! < 1.20f
        )
    }

    @Test
    fun removingBlankMarginsNeverCropsBlackQuestionTextOrAnswers() {
        val image = android.graphics.Bitmap.createBitmap(
            800, 550, android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(image)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
        }
        canvas.drawRect(170f, 70f, 520f, 110f, paint)
        canvas.drawRect(170f, 310f, 610f, 345f, paint)
        canvas.drawRect(170f, 480f, 665f, 492f, paint) // Far-right option E
        val trimmed = com.example.ui.screens.trimQuestionWhiteMargins(image)
        try {
            org.junit.Assert.assertTrue("Pure blank width was not removed",
                trimmed.width < image.width)
            val dark = android.graphics.Color.BLACK
            var count = 0
            for (y in 0 until trimmed.height) {
                for (x in 0 until trimmed.width) {
                    if (trimmed.getPixel(x, y) == dark) count++
                }
            }
            // Robolectric can rasterize Canvas rectangles differently from
            // hardware. Compare ACTUAL dark source pixels, not nominal areas.
            var originalInk = 0
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    if (image.getPixel(x, y) == dark) originalInk++
                }
            }
            org.junit.Assert.assertTrue("The test image must contain actual ink", originalInk > 0)
            org.junit.Assert.assertEquals(
                "Every part of the answer choices must survive", originalInk, count
            )
        } finally {
            if (trimmed !== image) trimmed.recycle()
            image.recycle()
        }
    }

    @Test
    fun enlargedImageNeverReservesPhantomHeightBeyondItsRealColumnWidth() {
        val source = fixtureSession().testItem.questions.first().copy(
            id = "scaled_crop", croppedImagePath = "scaled.jpg"
        )
        val ratio = mapOf(source.id to 1.2f)
        val base = com.example.ui.screens.estimatedBookQuestionUnits(
            source, 350f, ratio, mapOf(source.id to 1f)
        )
        val enlarged = com.example.ui.screens.estimatedBookQuestionUnits(
            source, 350f, ratio, mapOf(source.id to 1.90f)
        )
        org.junit.Assert.assertEquals(
            "No blank 1.9x-height container when the image already fills its width",
            base, enlarged, .001f
        )
    }

    @Test
    fun narrowWhiteHeavyCropIsExpandedWithoutCuttingAnyPrintedInk() {
        val image = android.graphics.Bitmap.createBitmap(
            800, 1000, android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(image)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
        }
        // Simulate a narrow question embedded in a large PDF crop.
        canvas.drawRect(324f, 180f, 490f, 198f, paint)
        canvas.drawRect(320f, 770f, 498f, 792f, paint)
        val trimmed = com.example.ui.screens.trimQuestionWhiteMargins(image)
        try {
            org.junit.Assert.assertTrue(
                "The crop must remove much more than the old 30% whitespace clamp",
                trimmed.width < image.width * .62f
            )
            var beforeInk = 0
            for (y in 0 until image.height) for (x in 0 until image.width) {
                if (image.getPixel(x, y) == android.graphics.Color.BLACK) beforeInk++
            }
            var afterInk = 0
            for (y in 0 until trimmed.height) for (x in 0 until trimmed.width) {
                if (trimmed.getPixel(x, y) == android.graphics.Color.BLACK) afterInk++
            }
            org.junit.Assert.assertEquals("All original ink survives optical recrop",
                beforeInk, afterInk)
        } finally {
            if (trimmed !== image) trimmed.recycle()
            image.recycle()
        }
    }

    @Test
    fun smallerPrintedPublisherCanBeEnlargedInsteadOfShrunkToItsTinyFont() {
        val sources = mapOf(
            "tiny" to com.example.ui.screens.BookImageMeasurements(.8f, 1f, 5f),
            "typical" to com.example.ui.screens.BookImageMeasurements(1.1f, 1f, 10.5f),
            "giant" to com.example.ui.screens.BookImageMeasurements(1.3f, 1f, 18f)
        )
        val scales = com.example.ui.screens.calibratedBookTypographyScales(sources)
        org.junit.Assert.assertTrue("Tiny source must be actively enlarged",
            scales.getValue("tiny") > 1.5f)
        org.junit.Assert.assertTrue("Oversized source must be shrunk",
            scales.getValue("giant") < .75f)
        val opticalSize = sources.mapValues { (id, m) ->
            m.printedGlyphDp!! * scales.getValue(id)
        }
        org.junit.Assert.assertTrue("Final printed type sizes should converge: $opticalSize",
            opticalSize.values.maxOrNull()!! / opticalSize.values.minOrNull()!! < 1.20f)
    }

    @Test
    fun polynomialGoldenReferenceTwoTallQuestionsHaveBreathingRoom() {
        val layout = com.example.ui.screens.adaptiveBookColumnRhythm(
            listOf(272f, 288f), 686f
        )
        org.junit.Assert.assertTrue("Two real textbook questions need visible separation",
            layout.gapDp in 45f..62f)
        org.junit.Assert.assertTrue("Golden layout must never squash source print",
            layout.slotScale > .95f)
        org.junit.Assert.assertTrue("Natural top margin stays subtle",
            layout.topInsetDp in 0f..18f)
        val consumed = (272f + 288f) * layout.slotScale +
            layout.gapDp + layout.topInsetDp
        org.junit.Assert.assertTrue("No bottom clipping", consumed <= 686.01f)
    }

    @Test
    fun threeAndFourQuestionColumnsUseProgressivelySmallerGaps() {
        val paperHeight = 680f
        val three = com.example.ui.screens.adaptiveBookColumnRhythm(
            listOf(172f, 184f, 176f), paperHeight
        )
        val four = com.example.ui.screens.adaptiveBookColumnRhythm(
            listOf(148f, 152f, 148f, 150f), paperHeight
        )
        org.junit.Assert.assertTrue("Three-question rhythm must remain airy",
            three.gapDp in 20f..38f)
        org.junit.Assert.assertTrue("Four-question rhythm must never overlap",
            four.gapDp in 16f..26f)
        org.junit.Assert.assertTrue("Less crowded columns get more breathing room",
            three.gapDp > four.gapDp)
        listOf(
            listOf(172f, 184f, 176f) to three,
            listOf(148f, 152f, 148f, 150f) to four
        ).forEach { (heights, rhythm) ->
            val occupied = heights.sum() * rhythm.slotScale +
                (heights.size - 1) * rhythm.gapDp + rhythm.topInsetDp
            org.junit.Assert.assertTrue(
                "Must fit all full questions within page: $occupied",
                occupied <= paperHeight + .01f
            )
        }
    }

    @Test
    fun oversizedFigureShrinksOnlyEnoughToFitWithoutClipping() {
        val rhythm = com.example.ui.screens.adaptiveBookColumnRhythm(
            listOf(780f), 605f
        )
        org.junit.Assert.assertTrue(rhythm.slotScale < 1f)
        org.junit.Assert.assertTrue(
            780f * rhythm.slotScale + rhythm.topInsetDp <= 605.01f
        )
    }

    @Test
    fun naturalPaperFlowNeverPacksCrowdedColumnsEvenForCompactQuestions() {
        val original = fixtureSession().testItem.questions.first()
        val questions = (1..18).map { number ->
            original.copy(id = "airy_$number", number = number,
                croppedImagePath = "airy_$number.png")
        }
        val ratios = questions.associate { it.id to 0.38f }
        val pages = com.example.ui.screens.packQuestionsIntoTwoColumnPages(
            questions, columnCapacity = 3.8f, maxQuestionsPerColumn = 8,
            columnWidthDp = 240f, actualImageRatios = ratios
        )
        org.junit.Assert.assertTrue("Compact questions should use paper space efficiently",
            pages.size in 2..3)
        org.junit.Assert.assertTrue("Never pack more than six readable questions per column",
            pages.all { it.left.size <= 6 && it.right.size <= 6 })
        assertEquals(questions.map { it.id }, pages.flatMap { it.questions }.map { it.id })
    }

    @Test
    fun shortEquationsFillPageWithoutAnArtificialFourQuestionLimit() {
        val template = fixtureSession().testItem.questions.first()
        val short = (1..12).map { n ->
            template.copy(id = "compact_equation_$n", number = n,
                croppedImagePath = "compact_$n.png")
        }
        val ratios = short.associate { it.id to .35f }
        val pages = com.example.ui.screens.packQuestionsIntoTwoColumnPages(
            short, columnCapacity = 2.7f, maxQuestionsPerColumn = 4,
            columnWidthDp = 240f, actualImageRatios = ratios
        )
        assertEquals("A short math test must not strand questions on a blank final page",
            1, pages.size)
        assertEquals(6, pages.single().left.size)
        assertEquals(6, pages.single().right.size)
        assertEquals(short.map { it.id }, pages.flatMap { it.questions }.map { it.id })
    }

    @Test
    fun tallIllustrationsAreNotForcedIntoTwoByTwoOrIllegiblyShrunk() {
        val template = fixtureSession().testItem.questions.first()
        val questions = (1..9).map { n ->
            template.copy(id = "varying_$n", number = n,
                croppedImagePath = "figure_$n.png")
        }
        val ratios = questions.associate { question ->
            question.id to if (question.number % 3 == 1) 2.5f else .48f
        }
        val pages = com.example.ui.screens.packQuestionsIntoTwoColumnPages(
            questions, columnCapacity = 2.35f, maxQuestionsPerColumn = 4,
            columnWidthDp = 240f, actualImageRatios = ratios
        )
        assertEquals(questions.map { it.id }, pages.flatMap { it.questions }.map { it.id })
        org.junit.Assert.assertTrue("A biology diagram may legitimately have its own column",
            pages.any { it.left.size == 1 || it.right.size == 1 })
        org.junit.Assert.assertTrue(
            "Do not put two massive diagrams in the same column",
            pages.none { column ->
                listOf(column.left, column.right).any { group ->
                    group.count { ratios.getValue(it.id) > 2f } > 1
                }
            }
        )
    }

    @Test
    fun airyPaperLayoutsStillBalanceShortAndLongQuestionsInSourceOrder() {
        val original = fixtureSession().testItem.questions.first()
        val questions = (1..9).map { n ->
            original.copy(id = "mixed_height_$n", number = n,
                croppedImagePath = "mixed_$n.png")
        }
        val ratios = questions.associate { q ->
            q.id to if (q.number == 3 || q.number == 7) 2.2f else 0.48f
        }
        val pages = com.example.ui.screens.packQuestionsIntoTwoColumnPages(
            questions, columnCapacity = 2.3f, maxQuestionsPerColumn = 3,
            columnWidthDp = 240f, actualImageRatios = ratios
        )
        assertEquals(questions.map { it.id }, pages.flatMap { it.questions }.map { it.id })
        org.junit.Assert.assertTrue(pages.all { it.left.size <= 3 && it.right.size <= 3 })
        org.junit.Assert.assertTrue(pages.none { it.right.isEmpty() && it.questions.size > 1 })
    }

    @Test
    fun fullQuestionImageAlwaysFitsItsPaperSlotWithoutHorizontalOrVerticalClipping() {
        listOf(0.24f, 0.55f, 1f, 2.4f, 6f).forEach { widthOverHeight ->
            listOf(0.28f, 0.5f, 1f, 1.16f).forEach { printScale ->
                val availableWidth = 230f
                val availableHeight = 210f
                val (width, height) = com.example.ui.screens.fittedBookImageSizeDp(
                    availableWidth, availableHeight, widthOverHeight, printScale
                )
                org.junit.Assert.assertTrue("Image spills horizontally", width <= availableWidth + .001f)
                org.junit.Assert.assertTrue("Image spills vertically", height <= availableHeight + .001f)
                org.junit.Assert.assertEquals(widthOverHeight, width / height, .002f)
            }
        }
    }

    @Test
    fun ungradedTestNeverClaimsStudentFailedOrSucceeded() {
        assertEquals("Cevap anahtarı doğrulanmadı", scoreReportHeadline(0, 0))
        assertEquals("Pes etmek yok!", scoreReportHeadline(0, 5))
        assertEquals("Harika iş çıkardın!", scoreReportHeadline(4, 5))
    }

    @Test
    fun tabletSheetsAdaptQuestionCountToAvailableHeightWithoutLosingOrder() {
        val template = fixtureSession().testItem.questions.first()
        val questions = (1..12).map { number -> template.copy(id = "packed_$number", number = number,
            croppedImagePath = "fixture.png", normLeft = 0f, normRight = 0.45f, normTop = 0f, normBottom = 0.25f) }
        val compact = com.example.ui.screens.packQuestionsIntoTwoColumnPages(questions, 2.1f)
        val tall = com.example.ui.screens.packQuestionsIntoTwoColumnPages(questions, 4.2f)
        assertEquals(questions.map { it.id }, compact.flatMap { it.questions }.map { it.id })
        assertEquals(questions.map { it.id }, tall.flatMap { it.questions }.map { it.id })
        // The original hard cap of two is intentionally gone: short questions can
        // share the same column when the device offers sufficient vertical room.
        org.junit.Assert.assertTrue(tall.any { it.left.size > 2 || it.right.size > 2 })
        org.junit.Assert.assertTrue(tall.size <= compact.size)
        // A long question always remains whole, never split across columns.
        org.junit.Assert.assertEquals(questions.size, tall.flatMap { it.questions }.size)
    }

    @Test
    fun fourMixedSourceQuestionsUseBalancedTwoByTwoLayout() {
        val original = fixtureSession().testItem.questions.first()
        val source = (1..4).map { number ->
            original.copy(
                id = "balanced_$number",
                number = number,
                croppedImagePath = "book_" + number + ".png"
            )
        }
        val ratios = source.associate { it.id to 0.55f }
        val pages = com.example.ui.screens.packQuestionsIntoTwoColumnPages(
            source, columnCapacity = 1.55f, maxQuestionsPerColumn = 4,
            columnWidthDp = 240f, actualImageRatios = ratios
        )
        assertEquals(1, pages.size)
        assertEquals(listOf(1, 2), pages[0].left.map { it.number })
        assertEquals(listOf(3, 4), pages[0].right.map { it.number })
    }

    @Test
    fun veryTallDrBiyolojiCropNeverGetsMixedIntoAnOverfilledColumn() {
        val original = fixtureSession().testItem.questions.first()
        val source = (1..7).map { number ->
            original.copy(
                id = "height_$number",
                number = number,
                croppedImagePath = "book_" + number + ".png"
            )
        }
        val ratios = source.associate { question ->
            question.id to if (question.number == 1 || question.number == 5) 4.5f else 0.6f
        }
        val pages = com.example.ui.screens.packQuestionsIntoTwoColumnPages(
            source, columnCapacity = 2.15f, maxQuestionsPerColumn = 4,
            columnWidthDp = 240f, actualImageRatios = ratios
        )
        assertEquals(source.map { it.id }, pages.flatMap { it.questions }.map { it.id })
        org.junit.Assert.assertTrue(pages.size >= 2)
        val tallPage = pages.first { page -> page.questions.any { it.number == 1 } }
        assertEquals(listOf(1), tallPage.left.map { it.number })
        org.junit.Assert.assertTrue(pages.all { it.left.size <= 4 && it.right.size <= 4 })
    }

    @Test
    fun smallImageCropsUseRealImageRatiosRatherThanFixedTwoPerColumn() {
        val original = fixtureSession().testItem.questions.first()
        val source = (1..12).map { number ->
            original.copy(
                id = "compact_$number",
                number = number,
                croppedImagePath = "book_" + number + ".png"
            )
        }
        val ratios = source.associate { it.id to 0.35f }
        val pages = com.example.ui.screens.packQuestionsIntoTwoColumnPages(
            source, columnCapacity = 2.7f, maxQuestionsPerColumn = 4,
            columnWidthDp = 240f, actualImageRatios = ratios
        )
        assertEquals(source.map { it.id }, pages.flatMap { it.questions }.map { it.id })
        org.junit.Assert.assertTrue(pages.any { it.left.size > 2 || it.right.size > 2 })
        org.junit.Assert.assertTrue(pages.none { it.right.isEmpty() && it.questions.size > 1 })
    }

    @Test
    fun twinScreenUsesProductionCallbackAndAllowsRetryAfterFailure() {
        var calls = 0
        showBoard(review = true, onTwinRequested = { _, callback ->
            calls++
            if (calls == 1) callback(
                com.example.viewmodel.TwinQuestionModalData(
                    topicTitle = "Fonksiyonlar",
                    errorMessage = "Bağımsız doğrulama henüz tamamlanmadı."
                )
            ) else callback(
                com.example.viewmodel.TwinQuestionModalData(
                    topicTitle = "Fonksiyonlar",
                    questionStem = "f(x)=3x+2 için f(4) kaçtır?",
                    options = mapOf(
                        "A" to "11", "B" to "12", "C" to "13",
                        "D" to "14", "E" to "15"
                    ),
                    correctOption = "D",
                    solutionExplanation = "Önce dört yerine yazılır: 3×4+2=14."
                )
            )
        })
        compose.onNodeWithTag("three_dot_btn_1").performClick()
        compose.onNodeWithText("İkiz Soru").performClick()
        compose.onNodeWithTag("full_screen_twin_comparison").assertExists()
        compose.onNodeWithTag("twin_generation_error").assertExists()
        compose.onNodeWithTag("retry_twin_generation_btn").performClick()
        compose.onNodeWithTag("twin_answer_D").assertExists()
        compose.runOnIdle { assertEquals(2, calls) }
    }

    @Test
    fun cancelFinishKeepsTheSessionActive() {
        var finishCount = 0
        showBoard(onFinish = { finishCount++ })

        compose.onNodeWithTag("top_finish_test_btn").performClick()
        compose.onNodeWithTag("finish_confirmation_dialog").assertIsDisplayed()
        compose.onNodeWithTag("cancel_finish_test_btn").performClick()

        compose.onNodeWithTag("finish_confirmation_dialog").assertDoesNotExist()
        compose.onNodeWithTag("live_solving_board").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, finishCount) }
    }

    @Test
    fun confirmingFinishInvokesTheCallbackOnce() {
        var finishCount = 0
        showBoard(onFinish = { finishCount++ })

        compose.onNodeWithTag("top_finish_test_btn").performClick()
        compose.onNodeWithTag("confirm_finish_test_btn").performClick()

        compose.onNodeWithTag("finish_confirmation_dialog").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, finishCount) }
    }

    @Test
    fun opticalFormUpdatesTheCorrectQuestionAndCanBeClosedOnPhone() {
        var latestAnswers: Map<Int, String> = emptyMap()
        showBoard(onAnswersChanged = { latestAnswers = it })

        compose.onNodeWithTag("optical_form_tab").performClick()
        compose.onNodeWithTag("optical_form_drawer").assertIsDisplayed()
        compose.onNodeWithTag("optic_q2_E").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(mapOf(2 to "E"), latestAnswers) }

        compose.onNodeWithTag("optic_q1_B").performClick()
        compose.runOnIdle { assertEquals(mapOf(1 to "B", 2 to "E"), latestAnswers) }
        compose.onNodeWithTag("drawer_finish_test_btn").assertIsDisplayed()
        compose.onNodeWithTag("optical_form_tab").performClick()
        compose.onNodeWithTag("optical_form_drawer").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun tabletOpticalFormRetainsPageAndFinishControls() {
        showBoard()
        compose.onNodeWithTag("board_back_button").assertIsDisplayed()
        compose.onNodeWithTag("top_finish_test_btn").assertIsDisplayed()
        compose.onNodeWithTag("optical_form_tab").performClick()
        compose.onNodeWithTag("optic_q1_A").assertIsDisplayed()
        compose.onNodeWithTag("optic_q2_E").assertIsDisplayed()
        compose.onNodeWithTag("drawer_finish_test_btn").performClick()
        compose.onNodeWithTag("finish_confirmation_dialog").assertIsDisplayed()
    }

    private fun showBoard(
        onFinish: () -> Unit = {},
        onAnswersChanged: (Map<Int, String>) -> Unit = {},
        review: Boolean = false,
        onTwinRequested: (
            YksQuestion,
            (com.example.viewmodel.TwinQuestionModalData) -> Unit
        ) -> Unit = { _, _ -> }
    ) {
        compose.setContent {
            var session by androidx.compose.runtime.remember {
                mutableStateOf(fixtureSession().copy(isReviewMode = review))
            }
            MyApplicationTheme {
                LiveSolvingBoardScreen(
                    session = session,
                    strokes = emptyList(),
                    currentCanvasKey = "full_page_fixture",
                    activeTool = DrawingTool.HAND_PAN,
                    selectedStrokeSizeIndex = 1,
                    selectedPenColorHex = 0xFF1D4ED8,
                    onExitSession = {},
                    onToggleViewMode = {},
                    onSelectTool = {},
                    onSelectStrokeSize = {},
                    onSelectPenColor = {},
                    onAddStroke = { _, _, _ -> },
                    onBookPageChanged = {},
                    onClearCanvas = { _ -> },
                    onSelectQuestionIndex = { session = session.copy(currentQuestionIndex = it) },
                    onSelectOption = { question, answer ->
                        val answers = session.selectedAnswers + (question to answer)
                        session = session.copy(selectedAnswers = answers)
                        onAnswersChanged(answers)
                    },
                    onToggleOpticalForm = {
                        session = session.copy(isOpticalFormDrawerOpen = !session.isOpticalFormDrawerOpen)
                    },
                    onToggleStatsDrawer = {},
                    onToggleQuestionMap = {},
                    onFinishTest = onFinish,
                    onGoToStatsTab = {},
                    onAskAiForQuestion = {},
                    onGenerateTwinQuestion = onTwinRequested
                )
            }
        }
    }

    private fun fixtureSession(): ActiveSolvingSession {
        val questions = (1..2).map { number ->
            YksQuestion(
                id = "question_$number",
                number = number,
                topicHeader = "Fonksiyonlar",
                stemTop = "f(x) = 2x + $number olduğuna göre",
                stemBottom = "f(2) kaçtır?",
                options = mapOf("A" to "3", "B" to "4", "C" to "5", "D" to "6", "E" to "7"),
                correctOption = if (number == 1) "C" else "D",
                stepByStepSolution = "x yerine 2 yazılır.",
                publisherName = "",
                publisherStamp = ""
            )
        }
        return ActiveSolvingSession(
            testItem = YksTestItem(
                id = "functions_test",
                topicId = "functions",
                title = "ÖSYM 1",
                fullTitle = "Fonksiyonlar - ÖSYM 1",
                difficulty = DifficultyLevel.ORTA,
                difficultyPosition = 0.5f,
                questions = questions
            ),
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicId = "functions",
            topicName = "Fonksiyonlar"
        )
    }
}
