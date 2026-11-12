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
        val goldenReference = intent.getBooleanExtra("polynomial_reference", false)
        val questions = (1..(if (goldenReference) 4 else 12)).map { index ->
            val publisher = when (index % 3) {
                0 -> "ÜçDörtBeş"
                2 -> "Barış"
                else -> "Ergi"
            }
            val printSize = if (goldenReference) {
                when (index) { 1 -> 39f; 2 -> 37f; 3 -> 40f; else -> 38f }
            } else when (index % 3) {
                0 -> 29f
                2 -> 52f
                else -> 24f
            }
            val imageHeight = when {
                goldenReference -> 755
                index % 5 == 4 -> 520
                index % 4 == 2 -> 380
                index % 3 == 0 -> 315
                else -> 260
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
                imageBase64 = makeQuestionImage(index, printSize, imageHeight, goldenReference)
            )
        }
        val test = YksTestItem(
            id = if (goldenReference) "paper_polynomial_golden_qa" else "paper_visual_qa",
            topicId = "polinomlar",
            title = if (goldenReference) "Polinomlar - Referans Testi" else "Polinomlar - Test 4",
            fullTitle = if (goldenReference) "Polinomlar - Referans Testi" else "Polinomlar - Test 4",
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
                    topicName = "Polinomlar",
                    // Launch the final page separately in the visual-QA run.
                    currentQuestionIndex = intent.getIntExtra("start_question_index", 0)
                        .coerceIn(0, questions.lastIndex)
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

    private fun makeQuestionImage(index: Int, fontSize: Float, suggestedHeight: Int, goldenReference: Boolean = false): String {
        // Visual acceptance images MUST contain a complete prompt, graph (when
        // applicable) and all five alternatives. The previous fixture silently
        // omitted C/D/E when it ran out of bitmap height, falsely blaming the
        // paper layout for missing source content.
        val mediumSize = fontSize * 0.75f
        val hasDiagram = goldenReference || index % 5 == 4
        val statementY = fontSize + 16f
        val contextY = statementY + fontSize * 1.5f
        val diagramTop = contextY + fontSize * 1.45f + 16f
        val questionY = when {
            goldenReference -> diagramTop + 450f
            hasDiagram -> diagramTop + 228f
            else -> contextY + fontSize * 1.45f
        }
        val optionsY = questionY + fontSize * 1.85f
        val optionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(22, 27, 37)
            textSize = mediumSize
        }
        val fullOptions = "A) 1     B) 2     C) 3     D) 4     E) 5"
        val optionsNeedTwoLines = optionPaint.measureText(fullOptions) > 780f - 36f
        val requiredHeight = kotlin.math.ceil(
            optionsY + mediumSize * (if (optionsNeedTwoLines) 2.75f else 1.35f) + 18f
        ).toInt()
        // Never drop a question or answer to fit an artificially short crop.
        val actualHeight = maxOf(suggestedHeight, requiredHeight)
        val bitmap = Bitmap.createBitmap(780, actualHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(22, 27, 37)
            textSize = fontSize
        }
        val medium = Paint(ink).apply { textSize = mediumSize }
        canvas.drawText("P(x) = x² + 2x + 1", 18f, statementY, ink)
        canvas.drawText("P(x) polinomu için", 18f, contextY, medium)
        if (hasDiagram) {
            val shape = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(48, 76, 116)
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            val left = 170f
            canvas.drawLine(left, diagramTop + 142, left + 280, diagramTop + 142, shape)
            canvas.drawLine(left + 130, diagramTop, left + 130, diagramTop + 200, shape)
            canvas.drawLine(left + 30, diagramTop + 125, left + 235, diagramTop + 30, shape)
            canvas.drawLine(left + 30, diagramTop + 30, left + 235, diagramTop + 125, shape)
            if (goldenReference) {
                // Four complete, tall, diagram-heavy ORIGINAL fixture cards.
                // Their rendered crop proportions match the 10 Nov Polinomlar
                // reference much better than 12 little one-line equations.
                val mathText = Paint(medium).apply {
                    textSize = fontSize * .78f
                    color = Color.rgb(28, 46, 80)
                }
                canvas.drawRoundRect(
                    85f, diagramTop + 215f, 690f, diagramTop + 365f,
                    22f, 22f, shape
                )
                canvas.drawLine(130f, diagramTop + 280f, 645f, diagramTop + 280f, shape)
                canvas.drawLine(380f, diagramTop + 225f, 380f, diagramTop + 355f, shape)
                canvas.drawText("P(x)", 188f, diagramTop + 265f, mathText)
                canvas.drawText("B(x)", 490f, diagramTop + 265f, mathText)
                canvas.drawText("K(x)", 188f, diagramTop + 345f, mathText)
                canvas.drawText("R(x)", 490f, diagramTop + 345f, mathText)
                canvas.drawText("x² + 2x + 1", 255f, diagramTop + 410f, mathText)
            }
        }
        canvas.drawText("ifadelerin toplamı kaçtır?", 18f, questionY, medium)
        if (optionsNeedTwoLines) {
            canvas.drawText("A) 1     B) 2     C) 3", 18f, optionsY, medium)
            canvas.drawText("D) 4     E) 5", 18f, optionsY + mediumSize * 1.4f, medium)
        } else {
            canvas.drawText(fullOptions, 18f, optionsY, medium)
        }
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle()
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
