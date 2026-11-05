package com.example.util

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.viewmodel.AiSolverViewModel
import com.example.viewmodel.ExtractedPdfQuestionItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PdfOfflineImportDeviceTest {
    @Test fun completePdfImportNeedsNoNetworkFirebaseOrPaidModel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (InstrumentationRegistry.getArguments().getString("sorulab.requireOffline") == "true") {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            assertFalse("Offline acceptance test still has an internet-capable network",
                manager.getNetworkCapabilities(manager.activeNetwork)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
        }
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.google.firebase.ai.GenerativeModel")
        }
        val pdf = File(context.cacheDir, "AYT-Biyoloji-Offline-QA.pdf")
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(1600, 2200, 1).create())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        fun text(value: String, x: Float, y: Float) = page.canvas.drawText(value, x, y, paint)
        text("SINIR SISTEMI - TEST 1", 100f, 100f)
        for (number in 1..4) {
            val x = if (number <= 2) 120f else 860f
            val y = if (number % 2 == 1) 260f else 1080f
            text("$number. Noronda uyari nasil iletilir?", x, y)
            for ((i, letter) in listOf("A", "B", "C", "D", "E").withIndex()) {
                text("$letter) Secenek ${i + 1}", x + 30f, y + 110f + i * 100f)
            }
        }
        text("1    2    3    4", 360f, 1990f)
        text("A    B    C    D", 360f, 2070f)
        document.finishPage(page)
        pdf.outputStream().use(document::writeTo)
        document.close()
        var model: AiSolverViewModel? = null
        var imported: List<ExtractedPdfQuestionItem>? = null
        val done = CountDownLatch(1)
        try {
            instrumentation.runOnMainSync {
                model = AiSolverViewModel()
                model!!.extractFullPdfFastParallel(context, Uri.fromFile(pdf), pdf.name, "QA", "") { rows, _, _ ->
                    imported = rows; done.countDown()
                }
            }
            assertTrue("Local import timed out", done.await(5, TimeUnit.MINUTES))
            assertNull(
                "Import error: ${model!!.batchErrorText.value}\\n" +
                    "Source coverage and OCR diagnostics: ${model!!.batchWarningText.value}",
                model!!.batchErrorText.value
            )
            val rows = requireNotNull(imported)
            assertEquals(4, rows.size)
            assertEquals(listOf("A", "B", "C", "D"), rows.sortedBy { it.questionNumberInTest }.map { it.correctOption })
            assertTrue(rows.all { it.answerKeyMatched && File(it.croppedImagePath).isFile })
        } finally {
            imported.orEmpty().forEach { File(it.croppedImagePath).delete() }
            pdf.delete()
        }
    }
}
