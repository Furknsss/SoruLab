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
        compose.onNodeWithTag("question_source_logo_1").assertIsDisplayed()
        val firstQuestion = compose.onNodeWithTag("book_question_1").fetchSemanticsNode().boundsInRoot
        val secondQuestion = compose.onNodeWithTag("book_question_2").fetchSemanticsNode().boundsInRoot
        val thirdQuestion = compose.onNodeWithTag("book_question_3").fetchSemanticsNode().boundsInRoot
        val fourthQuestion = compose.onNodeWithTag("book_question_4").fetchSemanticsNode().boundsInRoot
        val logo = compose.onNodeWithTag("question_source_logo_1").fetchSemanticsNode().boundsInRoot
        val number = compose.onNodeWithText("1.").fetchSemanticsNode().boundsInRoot
        assertTrue("Publisher logo must sit directly above the question number",
            logo.bottom <= number.top + 1f)
        val leftWorkspace = secondQuestion.top - firstQuestion.bottom
        val rightWorkspace = fourthQuestion.top - thirdQuestion.bottom
        assertTrue("Both columns must align at the exact same top baseline",
            kotlin.math.abs(firstQuestion.top - thirdQuestion.top) < 2f)
        assertTrue("Left column must leave generous natural solving workspace below Q1: $leftWorkspace",
            leftWorkspace >= 40f)
        assertTrue("Right column must leave generous natural solving workspace below Q3: $rightWorkspace",
            rightWorkspace >= 40f)
        assertTrue("Questions must stay above the fixed footer",
            secondQuestion.bottom <= footer.top + 1f && fourthQuestion.bottom <= footer.top + 1f)
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
        compose.onNodeWithTag("tool_eraser_btn").assertIsDisplayed()
        compose.onNodeWithTag("tool_hand_pan_btn").assertIsDisplayed()
        capture("test-phone")
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun printedQuestionImagesStayBalancedAndReadableOnTablet() {
        showBoard(useImages = true)
        compose.onAllNodesWithTag("test_page_footer").onFirst().assertIsDisplayed()
        val first = compose.onNodeWithTag("book_question_1").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("book_question_2").fetchSemanticsNode().boundsInRoot
        assertTrue("Source print remains readable", first.width > 300f && first.height > 70f)
        assertTrue("Printed questions keep reading order without overlap", second.top >= first.bottom)
        assertTrue("345 booklet page reserves natural S-Pen solving workspace: gap=${second.top - first.bottom}",
            second.top - first.bottom in 40f..360f)
        capture("test-tablet-printed")
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun sparseTwoColumnPageStacksCompactlyFromTopWithoutDeadVoids() {
        showBoard(useImages = true, questionCount = 4)
        val q1 = compose.onNodeWithTag("book_question_1").fetchSemanticsNode().boundsInRoot
        val q2 = compose.onNodeWithTag("book_question_2").fetchSemanticsNode().boundsInRoot
        val q3 = compose.onNodeWithTag("book_question_3").fetchSemanticsNode().boundsInRoot
        val q4 = compose.onNodeWithTag("book_question_4").fetchSemanticsNode().boundsInRoot
        val footer = compose.onAllNodesWithTag("test_page_footer").fetchSemanticsNodes().first().boundsInRoot
        val leftGap = q2.top - q1.bottom
        val rightGap = q4.top - q3.bottom
        assertTrue("Left column provides balanced solving workspace without pushing Q2 to footer: leftGap=$leftGap",
            leftGap in 40f..340f && footer.top - q2.bottom >= 60f)
        assertTrue("Right column provides balanced solving workspace without pushing Q4 to footer: rightGap=$rightGap",
            rightGap in 40f..340f && footer.top - q4.bottom >= 60f)
        assertTrue("Both columns must align at the same top baseline", kotlin.math.abs(q1.top - q3.top) < 2f)
        capture("test-tablet-sparse-compact")
    }

    @Test @Config(qualifiers = "w393dp-h851dp-mdpi")
    fun printedQuestionImagesUseFullPhoneWidthWithoutClipping() {
        showBoard(useImages = true)
        val first = compose.onNodeWithTag("book_question_1").fetchSemanticsNode().boundsInRoot
        assertTrue("Phone source print uses available width", first.width > 310f)
        compose.onAllNodesWithTag("test_page_footer").onFirst().assertIsDisplayed()
        capture("test-phone-printed")
    }

    @Test @Config(qualifiers = "w393dp-h851dp-mdpi")
    fun veryLongPrintedQuestionKeepsReadableWidthAndCanScrollToItsEnd() {
        showBoard(useImages = true, longQuestion = true)
        val image = compose.onNodeWithTag("question_crop_image_1")
        val before = image.getUnclippedBoundsInRoot()
        assertTrue("Tall source print must not turn into a miniature image", (before.right - before.left).value > 220f)
        assertTrue("Full tall source must extend beyond the viewport", (before.bottom - before.top).value > 900f)
        capture("test-long-question-top")
        compose.onAllNodesWithTag("test_page_columns").onFirst().performTouchInput { swipeUp() }
        compose.waitForIdle()
        val after = image.getUnclippedBoundsInRoot()
        assertTrue("The question must scroll rather than hide its lower part", after.top.value < before.top.value - 40f)
        repeat(3) {
            compose.onAllNodesWithTag("test_page_columns").onFirst().performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        val end = image.getUnclippedBoundsInRoot()
        val footer = compose.onAllNodesWithTag("test_page_footer").onFirst().getUnclippedBoundsInRoot()
        assertTrue("The last printed answer must be reachable above the fixed footer",
            end.bottom.value <= footer.top.value + 1f)
        capture("test-long-question-scrolled")
    }

    @Test
    fun penSizeHasNoPxTextAndSamsungEraserPopoverSupportsStrokeAndAreaModes() {
        val strokeA = DrawStroke(
            points = listOf(androidx.compose.ui.geometry.Offset(40f, 100f), androidx.compose.ui.geometry.Offset(240f, 100f)),
            colorHex = 0xFF111111L,
            strokeWidthPx = 8f
        )
        val strokeB = DrawStroke(
            points = listOf(androidx.compose.ui.geometry.Offset(40f, 320f), androidx.compose.ui.geometry.Offset(240f, 320f)),
            colorHex = 0xFF111111L,
            strokeWidthPx = 8f
        )
        assertTrue(
            "Samsung Notes stroke eraser must detect crossing a drawn line",
            com.example.ui.drawing.doesEraserTouchStroke(
                strokeA,
                listOf(androidx.compose.ui.geometry.Offset(130f, 70f), androidx.compose.ui.geometry.Offset(130f, 130f)),
                36f
            )
        )
        assertFalse(
            "Untouched line must remain intact",
            com.example.ui.drawing.doesEraserTouchStroke(
                strokeB,
                listOf(androidx.compose.ui.geometry.Offset(130f, 70f), androidx.compose.ui.geometry.Offset(130f, 130f)),
                36f
            )
        )

        showBoard(useImages = false, questionCount = 4)
        compose.onNodeWithTag("tool_pen_size_btn").assertIsDisplayed()
        compose.onAllNodesWithText("px", substring = true).assertCountEquals(0)
        val penBounds = compose.onNodeWithTag("smart_spen_mode").fetchSemanticsNode().boundsInRoot
        val penSizeBounds = compose.onNodeWithTag("tool_pen_size_btn").fetchSemanticsNode().boundsInRoot
        assertTrue("Pen size button must sit immediately next to the pen button",
            penSizeBounds.left >= penBounds.right - 2f && penSizeBounds.left - penBounds.right <= 20f)

        compose.onNodeWithTag("tool_eraser_size_btn").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("eraser_settings_popover").assertIsDisplayed()
        compose.onNodeWithTag("eraser_mode_stroke").assertIsDisplayed()
        compose.onNodeWithTag("eraser_mode_area").assertIsDisplayed()
        compose.onNodeWithTag("eraser_size_slider").assertIsDisplayed()
    }

    @Test
    fun idealLevelCardAppearsOnlyWhenSeviyeTespitDifficultyIsSelected() {
        val questions = (1..6).map { number ->
            YksQuestion(
                id = "diag_$number",
                number = number,
                publisherName = "SoruLab",
                stemTop = "$number. Seviye tespit sorusu",
                stemBottom = "Doğru cevabı bulunuz.",
                options = mapOf("A" to "1", "B" to "2", "C" to "3", "D" to "4", "E" to "5"),
                correctOption = "A",
                difficulty = if (number <= 2) DifficultyLevel.KOLAY else DifficultyLevel.ORTA,
                stepByStepSolution = ""
            )
        }
        var currentLevel by mutableStateOf(TopicTestLevel.PEKISTIRME)
        compose.setContent {
            val test = YksTestItem(
                id = "diag_test",
                topicId = "diziler",
                title = "Test 1",
                fullTitle = "Diziler · Test 1",
                difficulty = DifficultyLevel.ORTA,
                difficultyPosition = 0.5f,
                level = currentLevel,
                questions = questions
            )
            val session = ActiveSolvingSession(
                testItem = test,
                examType = ExamType.AYT,
                subjectId = "mat",
                subjectName = "Matematik",
                topicId = "diziler",
                topicName = "Diziler",
                isReviewMode = true,
                selectedAnswers = mapOf(1 to "A", 2 to "A", 3 to "A", 4 to "A", 5 to "B", 6 to "B")
            )
            var activeTool by remember { mutableStateOf(DrawingTool.HAND_PAN) }
            MaterialTheme {
                LiveSolvingBoardScreen(
                    session = session,
                    strokes = emptyList(),
                    currentCanvasKey = "diag",
                    activeTool = activeTool,
                    selectedStrokeSizeIndex = 1,
                    selectedPenColorHex = 0xFF0F172A,
                    onExitSession = {},
                    onToggleViewMode = {},
                    onSelectTool = { activeTool = it },
                    onSelectStrokeSize = {},
                    onSelectPenColor = {},
                    onAddStroke = { _, _, _, _ -> },
                    onBookPageChanged = {},
                    onClearCanvas = {},
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
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("go_to_report_card_btn").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("go_to_report_card_btn").performClick()
        compose.waitForIdle()
        // Non-SEVIYE_TESPIT -> must NOT display "İdeal Seviyeniz"
        compose.onAllNodesWithTag("diagnostic_ideal_level_card").assertCountEquals(0)

        // Switch to SEVIYE_TESPIT -> MUST display "İdeal Seviyeniz" with recommended level!
        currentLevel = TopicTestLevel.SEVIYE_TESPIT
        compose.waitForIdle()
        compose.onNodeWithTag("diagnostic_ideal_level_card").assertIsDisplayed()
        compose.onNodeWithTag("diagnostic_recommended_level", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun showBoard(useImages: Boolean = false, longQuestion: Boolean = false, questionCount: Int = 12) {
        val questions = (1..questionCount).map { number ->
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
        val displayedQuestions = if (useImages) questions.map { q -> q.copy(imageBase64 = printedImage(q, longQuestion && q.number == 1)) } else questions
        val test = YksTestItem("layout", "diziler", "Test 1", "Diziler · Test 1",
            DifficultyLevel.ORTA, 0.4f, questions = displayedQuestions)
        compose.setContent {
            var session by remember { mutableStateOf(ActiveSolvingSession(
                test, ExamType.AYT, "mat", "Matematik", "diziler", "Diziler"
            )) }
            var tool by remember { mutableStateOf(DrawingTool.HAND_PAN) }
            MaterialTheme {
                LiveSolvingBoardScreen(
                    session = session, strokes = emptyList(), currentCanvasKey = "layout", activeTool = tool,
                    selectedStrokeSizeIndex = 1, selectedPenColorHex = 0xFF0F172A,
                    onExitSession = {}, onToggleViewMode = {
                        session = session.copy(viewMode = if (session.viewMode == BoardViewMode.FULL_PAGE)
                            BoardViewMode.SINGLE_QUESTION else BoardViewMode.FULL_PAGE)
                    },
                    onSelectTool = { tool = it }, onSelectStrokeSize = {}, onSelectPenColor = {},
                    onAddStroke = { _, _, _, _ -> }, onBookPageChanged = {}, onClearCanvas = {},
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

    private fun printedImage(q: YksQuestion, longQuestion: Boolean = false): String {
        val bitmap = Bitmap.createBitmap(650, if (longQuestion) 2600 else if (q.number % 3 == 0) 490 else 335, Bitmap.Config.ARGB_8888)
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
        if (longQuestion) {
            for (line in 0..59) text("Uzun soru satırı " + (line + 1) + ": x + y = 2, koşulları inceleyiniz.", 20f + line * 40f)
            text("E) SON ŞIK — sorunun sonu korunur", 2480f, true)
        } else text(q.stemTop, 20f)
        if (!longQuestion) {
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
        }
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
