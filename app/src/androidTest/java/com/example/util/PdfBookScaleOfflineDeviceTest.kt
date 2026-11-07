package com.example.util

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.viewmodel.AiSolverViewModel
import com.example.viewmodel.ExtractedPdfQuestionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Source-free multi-test Android regression for whole-book import.
 * Every page changes test identity and has its own printed answer strip.
 * It exercises the actual app ViewModel, PdfRenderer, ML Kit and Paddle path
 * without uploading publisher-owned books or protected answer keys.
 */
@RunWith(AndroidJUnit4::class)
class PdfBookScaleOfflineDeviceTest {
    @Test fun twelveRealRenderedPagesHaveEveryQuestionAndPrintedAnswer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Opt-in scale QA runs only once on the offline emulator",
            InstrumentationRegistry.getArguments().getString("sorulab.bookScale") == "true")
        val context = instrumentation.targetContext
        val count = 12
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        for (testNumber in 1..count) {
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(1600, 2200, testNumber).create()
            )
            val canvas = page.canvas
            fun text(value: String, x: Float, y: Float) =
                canvas.drawText(value, x, y, paint)
            text("SINIR SISTEMI - TEST $testNumber", 100f, 100f)
            for (number in 1..4) {
                val x = if (number <= 2) 120f else 860f
                val y = if (number % 2 == 1) 260f else 1080f
                text("$number. Noronda uyari nasil iletilir?", x, y)
                for ((index, option) in listOf("A", "B", "C", "D", "E").withIndex()) {
                    text("$option) Secenek ${index + 1}", x + 30f, y + 110f + index * 100f)
                }
            }
            for (number in 1..4) {
                val x = 320f + (number - 1) * 300f
                text("$number", x, 1990f)
                text("ABCD"[number - 1].toString(), x, 2070f)
            }
            document.finishPage(page)
        }
        val file = File(context.cacheDir, "Sorulab-Twelve-Test-Offline-QA.pdf")
        file.outputStream().use(document::writeTo)
        document.close()

        var viewModel: AiSolverViewModel? = null
        var result: List<ExtractedPdfQuestionItem>? = null
        val done = CountDownLatch(1)
        val started = SystemClock.elapsedRealtime()
        try {
            instrumentation.runOnMainSync {
                viewModel = AiSolverViewModel()
                viewModel!!.extractFullPdfFastParallel(
                    context, Uri.fromFile(file), file.name, "QA", ""
                ) { rows, _, _ ->
                    result = rows
                    done.countDown()
                }
            }
            assertTrue("12-page offline import exceeded 12 minutes",
                done.await(12, TimeUnit.MINUTES))
            val elapsed = SystemClock.elapsedRealtime() - started
            android.util.Log.i(
                "SoruLabPdfScaleQA", "syntheticPages=$count" +
                    " elapsedMs=$elapsed reportedQuestions=${result?.size ?: 0}"
            )
            assertNull("12-page import failure: ${viewModel!!.batchErrorText.value} " +
                " details: ${viewModel!!.batchWarningText.value}",
                viewModel!!.batchErrorText.value)
            val questions = result
            assertNotNull("Import callback did not return questions", questions)
            questions!!
            assertEquals("Each of 12 tests must contain four printed questions",
                count * 4, questions.size)
            for (pageIndex in 0 until count) {
                val onPage = questions.filter { it.pageIndex == pageIndex }
                assertEquals("Missing crop on PDF page ${pageIndex + 1}", 4, onPage.size)
                assertEquals("Wrong or duplicate number on PDF page ${pageIndex + 1}",
                    setOf(1, 2, 3, 4), onPage.map { it.questionNumberInTest }.toSet())
                for (item in onPage) {
                    assertEquals("Wrong printed answer on PDF page ${pageIndex + 1}",
                        "ABCD"[item.questionNumberInTest - 1].toString(),
                        item.correctOption)
                    assertTrue("Answer must be independently confirmed",
                        item.answerKeyMatched)
                    assertTrue("Every crop image must exist", File(item.croppedImagePath).let {
                        it.isFile && it.length() > 0L
                    })
                    assertTrue("A-E option cannot be clipped",
                        item.normBottom > item.normTop && item.normRight > item.normLeft)
                    assertTrue("No duplicate Base64 JPEG for persisted crop",
                        item.imageBase64.isBlank())
                    assertFalse("Every printed test identity must be known",
                        item.sourceTestId.isBlank())
                }
            }
        } finally {
            result.orEmpty().forEach {
                if (it.croppedImagePath.isNotBlank()) File(it.croppedImagePath).delete()
            }
            file.delete()
        }
    }
}
