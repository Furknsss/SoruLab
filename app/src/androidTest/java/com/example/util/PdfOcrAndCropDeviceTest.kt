package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Android ML Kit inference + real bitmap cropper, not Robolectric mocks.
 * Entire test image is generated from scratch at runtime, no textbook media.
 *
 * This smoke test must never be presented as proof that every real PDF is good.
 */
@RunWith(AndroidJUnit4::class)
class PdfOcrAndCropDeviceTest {
    @Test
    fun embeddedMlKitReadsTwoColumnQuestionsThenCropperProducesBothImages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val width = 1600
        val height = 2000
        val page = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        page.eraseColor(Color.WHITE)
        val canvas = Canvas(page)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 51f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        fun text(x: Float, y: Float, value: String) {
            canvas.drawText(value, x, y, paint)
        }
        try {
            text(120f, 260f, "1. FIRST QUESTION")
            text(160f, 350f, "A) ONE")
            text(160f, 420f, "B) TWO")
            text(160f, 490f, "C) THREE")
            text(160f, 560f, "D) FOUR")
            text(160f, 630f, "E) FIVE")

            text(850f, 260f, "2. NEXT QUESTION")
            text(890f, 350f, "A) ONE")
            text(890f, 420f, "B) TWO")
            text(890f, 490f, "C) THREE")
            text(890f, 560f, "D) FOUR")
            text(890f, 630f, "E) FIVE")

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val recognized = try {
                Tasks.await(
                    recognizer.process(InputImage.fromBitmap(page, 0)),
                    90, TimeUnit.SECONDS
                )
            } finally {
                recognizer.close()
            }
            val lines = recognized.textBlocks.flatMap { it.lines }
            assertTrue("ML Kit returned no OCR text on real Android", lines.isNotEmpty())

            val numbers = lines.mapNotNull { line ->
                val n = PdfOcrQuestionMarker.numberAtStart(line.text)
                    ?: return@mapNotNull null
                val b = line.boundingBox ?: return@mapNotNull null
                QuestionNumberAnchor(n, b.left, b.top, b.right, b.bottom)
            }.filter { it.number in setOf(1, 2) }
            assertEquals(
                "On-device OCR must detect each actual printed question, not merely any text",
                setOf(1, 2), numbers.map { it.number }.toSet()
            )

            val regex = Regex("""^\s*([A-E])\s*[).:]""")
            val options = lines.mapNotNull { line ->
                val label = regex.find(line.text)?.groupValues?.get(1)
                    ?: return@mapNotNull null
                val b = line.boundingBox ?: return@mapNotNull null
                QuestionOptionAnchor(label, b.left, b.top, b.right, b.bottom)
            }
            assertTrue(
                "OCR must find at least 3 distinct choice letters per printed column",
                listOf(120f, 850f).all { x ->
                    options.filter { kotlin.math.abs(it.left - x) < 160f }
                        .map { it.option }.distinct().size >= 3
                }
            )
            val crops = StudioImageAndPdfEngine.cropRenderedPageLocalDualColumn(
                context = context,
                pageBitmap = page,
                pageIndex = 0,
                totalPages = 1,
                reusablePixelsRef = arrayOfNulls<IntArray>(1),
                questionNumberAnchors = numbers,
                optionAnchors = options
            )
            try {
                assertEquals("Both original printed questions must survive OCR + crop", 2, crops.size)
                assertEquals(setOf(1, 2), crops.map { it.questionOrderInPage }.toSet())
                assertTrue(crops.all { File(it.croppedImagePath).isFile })
                assertTrue(crops.all {
                    PdfCropGeometryAudit.issues(
                        PdfQuestionCropEvidence(it.questionOrderInPage, it.boxNormalized),
                        numbers, width, height
                    ).isEmpty()
                })
            } finally {
                crops.forEach { File(it.croppedImagePath).delete() }
            }
        } finally {
            page.recycle()
        }
    }

    @Test
    fun embeddedMlKitReadsAnswerGridAndBindsOnlyPrintedTestIdentity() {
        // Fully original large-print answer table so the Android OCR model
        // (rather than a mocked OCR string) supplies all parser input.
        val width = 1600
        val height = 1100
        val page = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        page.eraseColor(Color.WHITE)
        val canvas = Canvas(page)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        fun text(x: Float, y: Float, value: String) {
            canvas.drawText(value, x, y, paint)
        }

        try {
            text(100f, 175f, "SINIR SISTEMI")
            text(100f, 335f, "TEST 1")
            text(105f, 505f, "1.A")
            text(325f, 505f, "2.B")
            text(545f, 505f, "3.C")
            text(765f, 505f, "4.D")
            text(985f, 505f, "5.E")

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val recognized = try {
                Tasks.await(
                    recognizer.process(InputImage.fromBitmap(page, 0)),
                    90, TimeUnit.SECONDS
                )
            } finally {
                recognizer.close()
            }
            val cells = recognized.textBlocks.flatMap { block -> block.lines }
                .mapNotNull { line ->
                    val b = line.boundingBox ?: return@mapNotNull null
                    PdfOcrTextCell(line.text, b.left, b.top, b.right, b.bottom)
                }
            val regions = PdfAdaptiveAnswerRows.regions(cells, width)
            val answers = PdfScopedAnswerKeys(setOf("sinir sistemi"))
            regions.forEach(answers::addOcrRows)
            val scope = PdfAnswerScope("sinir sistemi", 1, false)
            assertEquals(
                "On-device OCR + answer-table parser must preserve printed A-E identity",
                listOf("A", "B", "C", "D", "E"),
                (1..5).map { answers.get(scope, it) }
            )
            // No previous-page or unrelated test may inherit this key.
            assertEquals(null, answers.get(PdfAnswerScope("sinir sistemi", 2, false), 1))
        } finally {
            page.recycle()
        }
    }

}
