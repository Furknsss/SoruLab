package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real ONNX/OpenCV inference using original synthetic question text. */
@RunWith(AndroidJUnit4::class)
class PdfPaddleMobileOcrDeviceTest {
    @Test fun officialPaddleOnnxReadsSyntheticPrintedText() = runBlocking {
        val image = Bitmap.createBitmap(1280, 1280, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.WHITE)
        val canvas = Canvas(image)
        val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 59f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        try {
            canvas.drawText("SINIR SISTEMI - TEST 1",75f,125f,pen)
            canvas.drawText("1. BIRINCI SORU",100f,310f,pen)
            canvas.drawText("A) BIR",145f,410f,pen)
            canvas.drawText("B) IKI",145f,510f,pen)
            canvas.drawText("C) UC",145f,610f,pen)
            canvas.drawText("2. IKINCI SORU",100f,850f,pen)
            canvas.drawText("A) DORT",145f,950f,pen)
            canvas.drawText("B) BES",145f,1050f,pen)
            val result = PdfPaddleMobileOcr.readPage(
                InstrumentationRegistry.getInstrumentation().targetContext,image
            )
            assertTrue("ONNX model returned no real OCR text",result.lineCount>=2)
            assertTrue("No source coordinates returned",result.textCells.isNotEmpty())
        } finally {
            image.recycle()
        }
    }
}
