package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

/** Free on-device official Paddle PP-OCRv6_small; not server-side PaddleOCR-VL. */
internal data class PdfPaddlePageEvidence(
    val questionNumberAnchors: List<QuestionNumberAnchor>,
    val optionAnchors: List<QuestionOptionAnchor>,
    val textCells: List<PdfOcrTextCell>,
    val lineCount: Int,
    val inferenceMs: Long
)

internal object PdfPaddleMobileOcr {
    private val mutex = Mutex()
    private var engine: PaddleOCR? = null
    private val optionPrefix = Regex("""^\s*([A-E])\s*[).:]""")

    suspend fun readPage(context: Context, bitmap: Bitmap): PdfPaddlePageEvidence {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            "PP-OCRv6 mobile SDK needs Android 8; ML Kit remains on Android 7"
        }
        require(!bitmap.isRecycled && bitmap.width >= 120 && bitmap.height >= 120)
        // Reuse native model across hundreds of scanned pages, never race buffers.
        return mutex.withLock {
            val model = engine ?: run {
                check(OpenCVUtils.init(context.applicationContext)) {
                    "PP-OCRv6 OpenCV native initialization failed"
                }
                PaddleOCR.create(context.applicationContext).also { engine = it }
            }
            val result = model.recognize(bitmap)
            val width = bitmap.width
            val height = bitmap.height
            val cells = result.results.mapNotNull { line ->
                if (line.confidence < 0.28f) return@mapNotNull null
                val left = line.box.points.minOf { it.x }.roundToInt().coerceIn(0, width-1)
                val top = line.box.points.minOf { it.y }.roundToInt().coerceIn(0, height-1)
                val right = line.box.points.maxOf { it.x }.roundToInt().coerceIn(left+1, width)
                val bottom = line.box.points.maxOf { it.y }.roundToInt().coerceIn(top+1, height)
                PdfOcrTextCell(line.text.trim(), left, top, right, bottom)
            }
            val starts = cells.mapNotNull { cell ->
                val number = PdfOcrQuestionMarker.numberAtStart(cell.text)
                    ?: return@mapNotNull null
                QuestionNumberAnchor(number, cell.left, cell.top, cell.right, cell.bottom)
            }
            val options = cells.mapNotNull { cell ->
                val option = optionPrefix.find(cell.text)?.groupValues?.getOrNull(1)
                    ?: return@mapNotNull null
                QuestionOptionAnchor(option, cell.left, cell.top, cell.right, cell.bottom)
            }
            Log.i("SoruLabPaddle", "PP-OCRv6 lines=" + cells.size +
                " starts=" + starts.size + " elapsed=" + result.totalTimeMs + "ms")
            PdfPaddlePageEvidence(starts, options, cells, cells.size, result.totalTimeMs)
        }
    }
}
