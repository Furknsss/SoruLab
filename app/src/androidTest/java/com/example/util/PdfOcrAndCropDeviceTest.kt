package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.FileOutputStream
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


    @Test
    fun generatedTwoPagePdfRendersThenOcrCropsAndMatchesPrintedAnswerKey() {
        // Test the PDF-to-bitmap step missing from our earlier smoke tests.
        // Everything is original test art; no user's copyrighted book is
        // uploaded to GitHub or published as an Actions artifact.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pdf = File(context.cacheDir, "qa-two-page-${System.nanoTime()}.pdf")
        val width = 1600
        val height = 2000

        try {
            val document = PdfDocument()
            try {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 51f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val questions = document.startPage(
                    PdfDocument.PageInfo.Builder(width, height, 1).create()
                )
                val pageCanvas = questions.canvas
                pageCanvas.drawColor(Color.WHITE)
                fun question(x: Float, number: Int, title: String) {
                    pageCanvas.drawText("$number. $title", x, 260f, paint)
                    listOf("A) ONE", "B) TWO", "C) THREE", "D) FOUR", "E) FIVE")
                        .forEachIndexed { index, option ->
                            pageCanvas.drawText(option, x + 40f, 350f + index * 70f, paint)
                        }
                }
                question(120f, 1, "FIRST QUESTION")
                question(850f, 2, "NEXT QUESTION")
                document.finishPage(questions)

                val keyPage = document.startPage(
                    PdfDocument.PageInfo.Builder(width, 1100, 2).create()
                )
                keyPage.canvas.drawColor(Color.WHITE)
                paint.textSize = 64f
                keyPage.canvas.drawText("SINIR SISTEMI", 100f, 175f, paint)
                keyPage.canvas.drawText("TEST 1", 100f, 335f, paint)
                listOf("1.A", "2.B", "3.C", "4.D", "5.E")
                    .forEachIndexed { index, answer ->
                        keyPage.canvas.drawText(answer, 105f + index * 220f, 505f, paint)
                    }
                document.finishPage(keyPage)
                FileOutputStream(pdf).use { document.writeTo(it) }
            } finally {
                document.close()
            }
            assertTrue("Generated PDF must exist", pdf.isFile && pdf.length() > 0)

            ParcelFileDescriptor.open(
                pdf, ParcelFileDescriptor.MODE_READ_ONLY
            ).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertEquals("Must read both question and answer pages", 2, renderer.pageCount)
                    fun render(index: Int): Bitmap {
                        renderer.openPage(index).use { page ->
                            val rendered = Bitmap.createBitmap(
                                page.width, page.height, Bitmap.Config.ARGB_8888
                            )
                            rendered.eraseColor(Color.WHITE)
                            page.render(
                                rendered, null, null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                            )
                            return rendered
                        }
                    }
                    val questionBitmap = render(0)
                    val answerBitmap = render(1)
                    try {
                        val recognizer = TextRecognition.getClient(
                            TextRecognizerOptions.DEFAULT_OPTIONS
                        )
                        val questionText: com.google.mlkit.vision.text.Text
                        val answerText: com.google.mlkit.vision.text.Text
                        try {
                            questionText = Tasks.await(
                                recognizer.process(
                                    InputImage.fromBitmap(questionBitmap, 0)
                                ), 90, TimeUnit.SECONDS
                            )
                            answerText = Tasks.await(
                                recognizer.process(InputImage.fromBitmap(answerBitmap, 0)),
                                90, TimeUnit.SECONDS
                            )
                        } finally {
                            recognizer.close()
                        }
                        val numberAnchors = questionText.textBlocks
                            .flatMap { it.lines }
                            .mapNotNull { line ->
                                val n = PdfOcrQuestionMarker.numberAtStart(line.text)
                                    ?: return@mapNotNull null
                                val b = line.boundingBox ?: return@mapNotNull null
                                QuestionNumberAnchor(n, b.left, b.top, b.right, b.bottom)
                            }.filter { it.number in 1..2 }
                        assertEquals("PDF-rendered question numbering lost",
                            setOf(1, 2), numberAnchors.map { it.number }.toSet())

                        val optionRegex = Regex("""^\s*([A-E])\s*[).:]""")
                        val options = questionText.textBlocks.flatMap { it.lines }
                            .mapNotNull { line ->
                                val label = optionRegex.find(line.text)
                                    ?.groupValues?.get(1) ?: return@mapNotNull null
                                val b = line.boundingBox ?: return@mapNotNull null
                                QuestionOptionAnchor(label, b.left, b.top, b.right, b.bottom)
                            }
                        val crops = StudioImageAndPdfEngine.cropRenderedPageLocalDualColumn(
                            context = context,
                            pageBitmap = questionBitmap,
                            pageIndex = 0,
                            totalPages = 2,
                            reusablePixelsRef = arrayOfNulls<IntArray>(1),
                            questionNumberAnchors = numberAnchors,
                            optionAnchors = options
                        )
                        try {
                            assertEquals(
                                "PDF -> renderer -> OCR -> crop must not drop a question",
                                2, crops.size
                            )
                            assertEquals(setOf(1, 2),
                                crops.map { it.questionOrderInPage }.toSet())
                            assertTrue(crops.all { File(it.croppedImagePath).isFile })
                        } finally {
                            crops.forEach { File(it.croppedImagePath).delete() }
                        }

                        val cells = answerText.textBlocks.flatMap { it.lines }
                            .mapNotNull { line ->
                                val b = line.boundingBox ?: return@mapNotNull null
                                PdfOcrTextCell(
                                    line.text, b.left, b.top, b.right, b.bottom
                                )
                            }
                        val scopedKeys = PdfScopedAnswerKeys(setOf("sinir sistemi"))
                        PdfAdaptiveAnswerRows.regions(cells, answerBitmap.width)
                            .forEach(scopedKeys::addOcrRows)
                        assertEquals(
                            "PDF answer page OCR must bind 5 separate printed answers",
                            listOf("A", "B", "C", "D", "E"),
                            (1..5).map {
                                scopedKeys.get(PdfAnswerScope("sinir sistemi", 1, false), it)
                            }
                        )
                        assertEquals(null,
                            scopedKeys.get(PdfAnswerScope("sinir sistemi", 2, false), 1))
                    } finally {
                        questionBitmap.recycle()
                        answerBitmap.recycle()
                    }
                }
            }
        } finally {
            pdf.delete()
        }
    }

}
