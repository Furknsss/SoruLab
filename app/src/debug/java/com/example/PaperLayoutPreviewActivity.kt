package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.example.data.DifficultyLevel
import com.example.data.ExamType
import com.example.data.YksQuestion
import com.example.data.YksTestItem
import com.example.ui.screens.LiveSolvingBoardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ActiveSolvingSession
import com.example.viewmodel.DrawingTool
import java.io.ByteArrayOutputStream

/**
 * DEBUG ONLY. Real Compose paper-layout visual acceptance fixture.
 * Not present in release APK. Exercise mixed printed typography, diagrams,
 * long and short questions in the same screen without login or cloud data.
 */
class PaperLayoutPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.BLACK
        val questions = (1..6).map { index ->
            val publisher = when (index) {
                2, 5 -> "Barış"
                4 -> "ÜçDörtBeş"
                else -> "Ergi"
            }
            val printSize = when (index) {
                2, 5 -> 52f
                4 -> 29f
                else -> 24f
            }
            val imageHeight = when(index) {
                1 -> 300
                2 -> 380
                3 -> 250
                4 -> 520
                5 -> 315
                else -> 270
            }
            YksQuestion(
                id = "paper_preview_$index",
                number = index,
                topicHeader = "Polinomlar",
                publisherName = publisher,
                publisherStamp = publisher,
                stemTop = "Polinom sorusu $index",
                stemBottom = "Aşağıdaki ifadelerden hangisi doğrudur?",
                options = mapOf("A" to "1", "B" to "2", "C" to "3", "D" to "4", "E" to "5"),
                correctOption = "B",
                stepByStepSolution = "Görsel düzen örneği",
                imageBase64 = makeQuestionImage(index, printSize, imageHeight)
            )
        }
        val test = YksTestItem(
            id = "paper_visual_qa",
            topicId = "polinomlar",
            title = "Polinomlar - Test 4",
            fullTitle = "Polinomlar - Test 4",
            difficulty = DifficultyLevel.ZOR,
            difficultyPosition = 0.8f,
            questions = questions
        )
        setContent {
            val session = remember {
                ActiveSolvingSession(
                    testItem = test,
                    examType = ExamType.AYT,
                    subjectId = "ayt_mat",
                    subjectName = "Matematik",
                    topicId = "polinomlar",
                    topicName = "Polinomlar"
                )
            }
            MyApplicationTheme {
                LiveSolvingBoardScreen(
                    session = session,
                    strokes = emptyList(),
                    currentCanvasKey = "paper_visual_qa",
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
                    onSelectQuestionIndex = {},
                    onSelectOption = { _, _ -> },
                    onToggleOpticalForm = {},
                    onToggleStatsDrawer = {},
                    onToggleQuestionMap = {},
                    onFinishTest = {},
                    onGoToStatsTab = {},
                    onAskAiForQuestion = {}
                )
            }
        }
    }

    private fun makeQuestionImage(index: Int, fontSize: Float, height: Int): String {
        val bitmap = Bitmap.createBitmap(780, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(22, 27, 37)
            textSize = fontSize
        }
        val medium = Paint(ink).apply { textSize = fontSize * 0.75f }
        var baseline = fontSize + 16f
        canvas.drawText("P(x) = x² + 2x + 1", 18f, baseline, ink)
        baseline += fontSize * 1.5f
        canvas.drawText("P(x) polinomu için", 18f, baseline, medium)
        baseline += fontSize * 1.45f
        if (index == 4) {
            val shape = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(48, 76, 116)
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            val left = 170f
            val top = baseline + 16
            canvas.drawLine(left, top + 142, left + 280, top + 142, shape)
            canvas.drawLine(left + 130, top, left + 130, top + 200, shape)
            canvas.drawLine(left + 30, top + 125, left + 235, top + 30, shape)
            canvas.drawLine(left + 30, top + 30, left + 235, top + 125, shape)
            baseline = top + 228
        }
        if (baseline + fontSize * 2.7f < height) {
            canvas.drawText("ifadelerin toplamı kaçtır?", 18f, baseline, medium)
            baseline += fontSize * 1.85f
        }
        if (baseline + medium.textSize < height) {
            canvas.drawText("A) 1     B) 2     C) 3     D) 4     E) 5", 18f, baseline, medium)
        }
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
