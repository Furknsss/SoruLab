package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import com.example.data.*
import com.example.viewmodel.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveSolvingBoardLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun `tablet has balanced readable columns visible footer and working navigation`() {
        showBoard(useImages = true)
        compose.onAllNodesWithTag("question_crop_image_1").assertCountEquals(1)
        compose.onNodeWithTag("question_crop_image_1").assertIsDisplayed()
        compose.onAllNodesWithTag("test_page_columns").onFirst().assertIsDisplayed()
        compose.onAllNodesWithTag("test_page_footer").onFirst().assertIsDisplayed()
        val first = compose.onAllNodesWithTag("book_question_1", useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot
        assertTrue("Tablet questions should use nearly half the screen", first.width > 340f)
        val footer = compose.onAllNodesWithTag("test_page_footer").fetchSemanticsNodes().first().boundsInRoot
        assertTrue("Footer must fit the viewport", footer.bottom <= 1280f)
        val lastImage = compose.onNodeWithTag("book_question_3").fetchSemanticsNode().boundsInRoot
        val logo = compose.onNodeWithTag("question_source_logo_1").fetchSemanticsNode().boundsInRoot
        val number = compose.onNodeWithText("1.").fetchSemanticsNode().boundsInRoot
        assertTrue("Publisher must sit above the question number", logo.bottom <= number.top)
        capture("test-tablet")
        compose.onNodeWithTag("optical_form_tab").performClick()
        compose.onNodeWithTag("optic_q1_A").assertIsDisplayed()
        capture("optical-form-reference")
    }

    @Test @Config(qualifiers = "w393dp-h851dp-mdpi")
    fun `phone uses a readable single column and answers still work`() {
        showBoard()
        val first = compose.onAllNodesWithTag("book_question_1", useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot
        assertTrue("Phone question must use full page width", first.width > 310f)
        compose.onAllNodesWithTag("test_page_footer").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("A) 11").onFirst().performClick()
        capture("test-phone")
    }

    private fun showBoard(useImages: Boolean = false) {
        val questions = (1..12).map { number ->
            YksQuestion(
                id = "layout_$number", number = number, publisherName = "SoruLab",
                stemTop = when (number % 3) {
                    0 -> "Bir geometrik dizinin ilk üç teriminin toplamı 21, ortak çarpanı 2’dir."
                    1 -> "24. terimi 89 ve ortak farkı 4 olan aritmetik bir dizi veriliyor."
                    else -> "Bir dizinin genel terimi aₙ = (n² + 4n + 48) / (n + 2) olarak tanımlanıyor."
                },
                stemBottom = when (number % 3) {
                    0 -> "Buna göre, dizinin ilk dört teriminin toplamı kaçtır?"
                    1 -> "Buna göre, bu dizinin 4. terimi kaçtır?"
                    else -> "Dizinin kaç terimi çift sayıdır?"
                },
                options = mapOf("A" to "11", "B" to "13", "C" to "15", "D" to "17", "E" to "19"),
                correctOption = "A", stepByStepSolution = ""
            )
        }
        val displayedQuestions = if (useImages) questions.map { q -> q.copy(imageBase64 = printedImage(q)) } else questions
        val test = YksTestItem("layout", "diziler", "Test 1", "Diziler · Test 1",
            DifficultyLevel.ORTA, 0.4f, questions = displayedQuestions)
        compose.setContent {
            var session by remember { mutableStateOf(ActiveSolvingSession(
                test, ExamType.AYT, "mat", "Matematik", "diziler", "Diziler"
            )) }
            MaterialTheme {
                LiveSolvingBoardScreen(
                    session = session, strokes = emptyList(), currentCanvasKey = "layout", activeTool = DrawingTool.HAND_PAN,
                    selectedStrokeSizeIndex = 1, selectedPenColorHex = 0xFF0F172A,
                    onExitSession = {}, onToggleViewMode = {
                        session = session.copy(viewMode = if (session.viewMode == BoardViewMode.FULL_PAGE)
                            BoardViewMode.SINGLE_QUESTION else BoardViewMode.FULL_PAGE)
                    },
                    onSelectTool = {}, onSelectStrokeSize = {}, onSelectPenColor = {},
                    onAddStroke = { _, _, _ -> }, onBookPageChanged = {}, onClearCanvas = {},
                    onSelectQuestionIndex = { session = session.copy(currentQuestionIndex = it) },
                    onSelectOption = { q, opt -> session = session.copy(selectedAnswers = session.selectedAnswers + (q to opt)) },
                    onToggleOpticalForm = { session = session.copy(isOpticalFormDrawerOpen = !session.isOpticalFormDrawerOpen) },
                    onToggleStatsDrawer = {}, onToggleQuestionMap = {}, onFinishTest = {},
                    onGoToStatsTab = {}, onAskAiForQuestion = {}
                )
            }
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("book_horizontal_pager").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun printedImage(q: YksQuestion): String {
        val bitmap = Bitmap.createBitmap(650, if (q.number % 3 == 0) 490 else 335, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(25, 30, 40)
            textSize = 24f
        }
        fun text(value: String, y: Float, bold: Boolean = false) {
            paint.isFakeBoldText = bold
            val layout = android.text.StaticLayout.Builder.obtain(value, 0, value.length, paint, 610)
                .setIncludePad(false).build()
            canvas.save()
            canvas.translate(20f, y)
            layout.draw(canvas)
            canvas.restore()
        }
        text(q.stemTop, 20f)
        text("aₙ = a₁ + (n − 1) · d", 103f)
        if (q.number % 3 == 0) {
            val pen = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(50, 70, 100)
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawLine(100f, 155f, 100f, 285f, pen)
            canvas.drawLine(70f, 265f, 540f, 265f, pen)
            canvas.drawLine(100f, 255f, 450f, 165f, pen)
        }
        val bottom = if (q.number % 3 == 0) 320f else 170f
        text(q.stemBottom, bottom, true)
        text("A) 11     B) 13     C) 15     D) 17     E) 19", bottom + 85f)
        val data = java.io.ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        bitmap.recycle()
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
    }

    private fun capture(name: String) {
        val output = File("build/reports/test-layout/$name.png")
        output.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
