package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * REAL remote Firebase AI Logic/Gemini 3.8 Flash call.
 *
 * This smoke test must be explicitly opted into by a CI runner; default
 * Android device suites never silently consume API credits.
 * It sends ONLY a synthetic page authored by this test, never publisher PDF.
 * Success certifies connection, schema, and a tiny page response — NOT the
 * accuracy of any uploaded biology book.
 */
@RunWith(AndroidJUnit4::class)
class PdfVisionGeminiLiveDeviceTest {
    @Test
    fun actualFirebaseGeminiReturnsStructuredOriginalQuestionPage() = runBlocking {
        val optIn = InstrumentationRegistry.getArguments()
            .getString("sorulab.liveVision", "false") == "true"
        assumeTrue("Explicit opt-in is required for cloud model requests", optIn)
        val page = Bitmap.createBitmap(1400, 1600, Bitmap.Config.ARGB_8888)
        page.eraseColor(Color.WHITE)
        val canvas = Canvas(page)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        try {
            canvas.drawText("SINIR SISTEMI - TEST 1", 100f, 145f, paint)
            canvas.drawText("1. Bir noronda uyari nasil iletilir?", 100f, 280f, paint)
            canvas.drawText("A) DNA kopyalanmasi ile", 140f, 390f, paint)
            canvas.drawText("B) Zar potansiyeli ile", 140f, 480f, paint)
            canvas.drawText("C) Solunum ile", 140f, 570f, paint)
            canvas.drawText("D) Fotosentez ile", 140f, 660f, paint)
            canvas.drawText("E) Ribozom ile", 140f, 750f, paint)
            val result = PdfVisionGeminiEngine.readPage(
                page, pageNumber = 1, sourceHeader = "SINIR SISTEMI - TEST 1"
            )
            assertEquals("questions", result.pageType)
            assertEquals(1, result.questions.count { it.number == 1 })
            val bounding = result.questions.single { it.number == 1 }
            val anchors = listOf(QuestionNumberAnchor(1, 100, 234, 145, 284))
            val accepted = PdfVisionImportPolicy.validateQuestionBoxes(
                result, page.width, page.height, anchors,
                requireExactPrintedCoverage = true
            )
            assertEquals("Gemini JSON returned a geometry-inconsistent source question",
                1, accepted.size)
            assertTrue("Gemini cropped off the lower option rows",
                bounding.bottom >= 740 * 1000 / page.height)
            assertTrue("An ordinary question page cannot invent printed answer-key cells",
                PdfVisionImportPolicy.verifiedPrintedKeyCandidates(result).isEmpty())
        } finally {
            page.recycle()
        }
    }
}
