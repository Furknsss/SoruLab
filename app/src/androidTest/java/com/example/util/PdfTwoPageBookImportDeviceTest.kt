package com.example.util

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.viewmodel.AiSolverViewModel
import com.example.viewmodel.ExtractedPdfQuestionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Offline Android regression for the real two-page test SHAPE:
 *   page 1: printed 1..7, NO answers
 *   page 2: printed 8..14, bottom strip 1..14 with A-E
 *
 * All graphics/text are original QA fixtures, NOT pages from a publisher.
 * This catches an important gap in prior one-page-only green tests.
 * A pass is NOT approval of the original book; that requires the
 * PdfRealBookAcceptanceDeviceTest with the private full PDF.
 */
@RunWith(AndroidJUnit4::class)
class PdfTwoPageBookImportDeviceTest {
    @Test fun twoPagesPerTestMustAssociateAllPrintedQuestionsWithTheirOwnKeys() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Offline two-page fixture runs only in QA",
            InstrumentationRegistry.getArguments().getString("sorulab.twoPageQA") == "true")
        val app = instrumentation.targetContext
        val pdf = File(app.cacheDir, "AYT-Biyoloji-TwoPage-Scope-QA.pdf")
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 37f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val expected = mutableMapOf<Pair<Int, Int>, String>()
        try {
            for (test in 1..2) {
                val fullKey = (1..14).associateWith { n ->
                    "ABCDE"[(n + test - 2) % 5].toString()
                }
                for (half in 0..1) {
                    val pageIndex = (test - 1) * 2 + half
                    val p = document.startPage(
                        PdfDocument.PageInfo.Builder(1600, 2200, pageIndex + 1).create()
                    )
                    val canvas = p.canvas
                    fun draw(text: String, x: Float, y: Float) =
                        canvas.drawText(text, x, y, paint)
                    draw("SINIR SISTEMI - ANALIZ $test", 90f, 110f)
                    val first = if (half == 0) 1 else 8
                    for (index in 0 until 7) {
                        val number = first + index
                        val col = if (index < 3) 0 else 1
                        val row = if (col == 0) index else index - 3
                        val x = if (col == 0) 120f else 865f
                        val y = 260f + row * 445f
                        draw("$number. Noronun temel gorevi nedir?", x, y)
                        for ((choice, option) in listOf("A","B","C","D","E").withIndex()) {
                            draw("$option) Secenek ${choice + 1} - test $test", x + 28f,
                                y + 85f + choice * 52f)
                        }
                        expected[pageIndex to number] = fullKey.getValue(number)
                    }
                    if (half == 1) {
                        for (number in 1..14) {
                            val x = 155f + (number - 1) * 94f
                            draw(number.toString(), x, 2025f)
                            draw(fullKey.getValue(number), x, 2100f)
                        }
                    }
                    document.finishPage(p)
                }
            }
            pdf.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }

        val result = arrayOfNulls<List<ExtractedPdfQuestionItem>>(1)
        val finished = CountDownLatch(1)
        val model = AiSolverViewModel()
        try {
            instrumentation.runOnMainSync {
                model.extractFullPdfFastParallel(
                    app, Uri.fromFile(pdf), pdf.name, "QA", ""
                ) { questions, _, _ ->
                    result[0] = questions
                    finished.countDown()
                }
            }
            assertTrue("Two-page test import timed out", finished.await(8, TimeUnit.MINUTES))
            assertNull(
                "Two-page book import rejected: ${model.batchErrorText.value}" +
                    " diagnostic=${model.batchWarningText.value}",
                model.batchErrorText.value
            )
            val rows = requireNotNull(result[0])
            assertEquals("Both full two-page tests must be imported", 28, rows.size)
            assertEquals("Every source number must occur exactly once",
                expected.keys, rows.map { it.pageIndex to it.questionNumberInTest }.toSet())
            for (row in rows) {
                assertEquals("Wrong scope/printed answer on PDF page ${row.pageIndex + 1}",
                    expected[row.pageIndex to row.questionNumberInTest], row.correctOption)
                assertTrue("Unverified answer", row.answerKeyMatched)
                assertTrue("Crop missing",
                    File(row.croppedImagePath).let { it.isFile && it.length() > 0L })
                assertTrue("Incorrect test identity", row.sourceTestId.isNotBlank())
            }
        } finally {
            result[0].orEmpty().forEach { File(it.croppedImagePath).delete() }
            pdf.delete()
        }
    }
}
