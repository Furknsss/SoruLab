package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.*
import com.example.ui.screens.LiveSolvingBoardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Compose hit testing with Android stylus/palm events, not physical S Pen certification. */
@RunWith(AndroidJUnit4::class)
class SoruLabStylusViewportDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var session by mutableStateOf(fixtureSession())
    private var ink by mutableStateOf(emptyMap<String, List<DrawStroke>>())
    private var tool by mutableStateOf(DrawingTool.PEN)
    private val captured = mutableListOf<Pair<String, List<Offset>>>()
    private val inkColor = 0xFFE11D48L

    @Test fun zoomedStylusStoresPageCoordinatesAndRendersAtContact() {
        showBoard()
        compose.onNodeWithTag("board_zoom_toggle").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("board_zoom_toggle").assertIsSelected()
        compose.onNodeWithTag("board_ink_viewport").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "145%")
        )
        val viewport = compose.onNodeWithTag("board_ink_viewport").fetchSemanticsNode().boundsInWindow
        val start = viewport.center + Offset(40f, 20f)
        val end = viewport.center + Offset(70f, 50f)
        stroke(start, end)
        compose.runOnIdle {
            assertEquals(1, captured.size)
            assertEquals("q_stylus_1", captured.single().first)
            val first = captured.single().second.first()
            assertEquals(viewport.width / 2f + 40f / 1.45f, first.x, 2f)
            assertEquals(viewport.height / 2f + 20f / 1.45f, first.y, 2f)
        }
        // Independent pixel evidence: the persisted line must appear at the
        // Android contact location after the real graphicsLayer transformation.
        val pixels = compose.onNodeWithTag("board_ink_viewport").captureToImage().toPixelMap()
        val x = (viewport.width / 2f + 55f).toInt()
        val y = (viewport.height / 2f + 35f).toInt()
        var colored = 0
        for (dx in -7..7) for (dy in -7..7) {
            val c = pixels[(x + dx).coerceIn(0, pixels.width - 1), (y + dy).coerceIn(0, pixels.height - 1)]
            if (c.red > .55f && c.green < .35f && c.blue < .6f) colored++
        }
        assertTrue("Persisted ink missed the stylus contact after zoom", colored > 4)
        screenshot("single-question-zoom-ink.png")
    }

    @Test fun palmFirstDoesNotBlockTwoSuccessivePenStrokes() {
        showBoard()
        val bounds = compose.onNodeWithTag("board_ink_viewport").fetchSemanticsNode().boundsInWindow
        val palm = bounds.center + Offset(70f, 90f)
        val pen = bounds.center - Offset(50f, 30f)
        val downTime = SystemClock.uptimeMillis()
        dispatch(MotionEvent.ACTION_DOWN, listOf(Contact(0, MotionEvent.TOOL_TYPE_FINGER, palm)), downTime)
        repeat(2) { index ->
            val start = pen + Offset(0f, index * 30f)
            dispatch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                listOf(Contact(0, MotionEvent.TOOL_TYPE_FINGER, palm), Contact(7, MotionEvent.TOOL_TYPE_STYLUS, start)), downTime)
            dispatch(MotionEvent.ACTION_MOVE,
                listOf(Contact(0, MotionEvent.TOOL_TYPE_FINGER, palm), Contact(7, MotionEvent.TOOL_TYPE_STYLUS, start + Offset(25f, 12f))), downTime)
            dispatch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                listOf(Contact(0, MotionEvent.TOOL_TYPE_FINGER, palm), Contact(7, MotionEvent.TOOL_TYPE_STYLUS, start + Offset(25f, 12f))), downTime)
        }
        dispatch(MotionEvent.ACTION_UP, listOf(Contact(0, MotionEvent.TOOL_TYPE_FINGER, palm)), downTime)
        compose.runOnIdle {
            assertEquals("A resting palm blocked a subsequent S Pen contact", 2, captured.size)
            assertTrue(captured.all { it.first == "q_stylus_1" })
        }
    }

    @Test fun scrolledSingleQuestionKeepsInkInDocumentCoordinatesAndAnswerSelectionWorks() {
        session = fixtureSession(longQuestion = true)
        showBoard()
        compose.onNodeWithTag("single_answer_1_D").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("D", session.selectedAnswers[1]) }
        val content = compose.onNodeWithTag("single_question_content_stylus_1").fetchSemanticsNode()
        val scrollY = content.config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("Long original question did not exercise scrolling", scrollY > 100f)
        val bounds = compose.onNodeWithTag("board_ink_viewport").fetchSemanticsNode().boundsInWindow
        stroke(bounds.center, bounds.center + Offset(30f, 20f))
        compose.runOnIdle {
            assertEquals(bounds.height / 2f + scrollY, captured.single().second.first().y, 2f)
        }
        screenshot("single-question-scrolled.png")
    }

    @Test fun changingFocusedQuestionResetsZoomAndDoesNotReuseInk() {
        showBoard()
        compose.onNodeWithTag("board_zoom_toggle").performScrollTo().assertIsDisplayed().performClick()
        val bounds = compose.onNodeWithTag("board_ink_viewport").fetchSemanticsNode().boundsInWindow
        stroke(bounds.center, bounds.center + Offset(30f, 20f))
        compose.runOnIdle { session = session.copy(currentQuestionIndex = 1) }
        compose.onNodeWithTag("single_answer_2_B").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("board_ink_viewport").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "100%")
        )
        compose.runOnIdle {
            assertEquals("B", session.selectedAnswers[2])
            assertEquals(1, ink["q_stylus_1"].orEmpty().size)
            assertTrue(ink["q_stylus_2"].isNullOrEmpty())
        }
        screenshot("single-question-phone.png")
    }

    @Test fun tallCroppedImageKeepsItsLastInkVisibleInFocusedView() {
        checkTallImage(BoardViewMode.SINGLE_QUESTION, "single-question-tall-image.png")
    }

    @Test fun focusedEAnswerIsUnobstructedAndOpticalGridStillWorks() {
        showBoard()
        compose.onNodeWithTag("single_answer_1_E").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("E", session.selectedAnswers[1]) }
        compose.onNodeWithTag("optical_form_tab").assertDoesNotExist()
        screenshot("single-question-e-answer.png")
        // Chrome stays outside the ink surface, so a real stylus tap can open
        // the grid without creating an accidental dot on the question.
        val button = compose.onNodeWithTag("single_optical_toggle").assertIsDisplayed()
            .fetchSemanticsNode().boundsInWindow
        val down = SystemClock.uptimeMillis()
        dispatch(MotionEvent.ACTION_DOWN, listOf(Contact(7, MotionEvent.TOOL_TYPE_STYLUS, button.center)), down)
        dispatch(MotionEvent.ACTION_UP, listOf(Contact(7, MotionEvent.TOOL_TYPE_STYLUS, button.center)), down)
        compose.onNodeWithTag("optical_form_drawer").assertIsDisplayed()
        compose.onNodeWithTag("optic_q1_A").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Optik formu kapat").performClick()
        compose.onNodeWithTag("optical_form_drawer").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("A", session.selectedAnswers[1])
            assertTrue("Stylus toolbar tap created an accidental stroke", captured.isEmpty())
        }
    }

    @Test fun tallCroppedImageKeepsItsLastInkVisibleOnBookPage() {
        checkTallImage(BoardViewMode.FULL_PAGE, "book-page-tall-image.png")
    }

    private fun checkTallImage(mode: BoardViewMode, snapshot: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "original-tall-question.png")
        val bitmap = Bitmap.createBitmap(800, 2400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = Color.MAGENTA }
        canvas.drawRect(0f, 0f, 800f, 100f, paint)
        paint.color = Color.rgb(0, 170, 170)
        canvas.drawRect(0f, 2300f, 800f, 2400f, paint)
        paint.color = Color.BLACK
        paint.textSize = 48f
        canvas.drawText("ORIGINAL QUESTION", 40f, 240f, paint)
        canvas.drawText("E) LAST PRINTED CHOICE", 40f, 2230f, paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            session = fixtureSession().let { fixture -> fixture.copy(
                viewMode = mode,
                testItem = fixture.testItem.copy(questions = fixture.testItem.questions.map {
                    it.copy(croppedImagePath = file.absolutePath)
                })
            ) }
            showBoard()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("1. Soru").fetchSemanticsNodes().isNotEmpty()
            }
            val image = compose.onNodeWithContentDescription("1. Soru").assertIsDisplayed()
                .captureToImage().toPixelMap()
            var topInk = 0
            var lastInk = 0
            for (y in 0 until image.height step 2) for (x in 0 until image.width step 2) {
                val color = image[x, y]
                if (color.red > .8f && color.blue > .8f && color.green < .15f) topInk++
                if (color.red < .15f && color.green > .5f && color.blue > .5f) lastInk++
            }
            assertTrue("The question's top was clipped", topInk > 3)
            assertTrue("The question's last printed choice/diagram was clipped", lastInk > 3)
            screenshot(snapshot)
        } finally {
            file.delete()
        }
    }

    private fun showBoard() {
        compose.setContent {
            val key = "q_${session.testItem.questions[session.currentQuestionIndex].id}"
            MyApplicationTheme {
                LiveSolvingBoardScreen(
                    session = session, strokes = ink[key].orEmpty(), currentCanvasKey = key,
                    activeTool = tool, selectedStrokeSizeIndex = 1, selectedPenColorHex = inkColor,
                    onExitSession = {}, onToggleViewMode = {}, onSelectTool = { tool = it },
                    onSelectStrokeSize = {}, onSelectPenColor = {},
                    onAddStroke = { canvas, points, pressures ->
                        captured.add(canvas to points)
                        ink = ink + (canvas to (ink[canvas].orEmpty() + DrawStroke(
                            points, inkColor, 8f, pressureSamples = pressures
                        )))
                    }, onBookPageChanged = {}, onClearCanvas = {},
                    onSelectQuestionIndex = { session = session.copy(currentQuestionIndex = it) },
                    onSelectOption = { n, answer -> session = session.copy(selectedAnswers = session.selectedAnswers + (n to answer)) },
                    onToggleOpticalForm = { session = session.copy(isOpticalFormDrawerOpen = !session.isOpticalFormDrawerOpen) },
                    onToggleStatsDrawer = {}, onToggleQuestionMap = {},
                    onFinishTest = {}, onGoToStatsTab = {}, onAskAiForQuestion = {}
                )
            }
        }
    }

    private data class Contact(val id: Int, val tool: Int, val at: Offset)

    private fun dispatch(action: Int, contacts: List<Contact>, downTime: Long) {
        val properties = contacts.map { contact -> MotionEvent.PointerProperties().apply {
            id = contact.id; toolType = contact.tool
        } }.toTypedArray()
        val positions = contacts.map { contact -> MotionEvent.PointerCoords().apply {
            x = contact.at.x; y = contact.at.y; pressure = .7f; size = .1f
        } }.toTypedArray()
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
            contacts.size, properties, positions, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { compose.runOnUiThread { compose.activity.dispatchTouchEvent(event) } }
        finally { event.recycle() }
        compose.waitForIdle()
    }

    private fun stroke(start: Offset, end: Offset) {
        val down = SystemClock.uptimeMillis()
        dispatch(MotionEvent.ACTION_DOWN, listOf(Contact(7, MotionEvent.TOOL_TYPE_STYLUS, start)), down)
        dispatch(MotionEvent.ACTION_MOVE, listOf(Contact(7, MotionEvent.TOOL_TYPE_STYLUS, end)), down)
        dispatch(MotionEvent.ACTION_UP, listOf(Contact(7, MotionEvent.TOOL_TYPE_STYLUS, end)), down)
    }

    private fun screenshot(name: String) {
        saveSyntheticQaScreenshot(compose.onRoot().captureToImage().asAndroidBitmap(), name)
    }

    private fun fixtureSession(longQuestion: Boolean = false): ActiveSolvingSession {
        val questions = (1..2).map { n -> YksQuestion(
            id = "stylus_$n", number = n, topicHeader = "Fonksiyonlar",
            stemTop = if (longQuestion) (1..45).joinToString("\n") { "Bu satır özgün kaydırma testi $it." } else "f(x) = 2x + $n olduğuna göre",
            stemBottom = "f(2) kaçtır?", options = mapOf("A" to "3", "B" to "4", "C" to "5", "D" to "6", "E" to "7"),
            correctOption = "C", stepByStepSolution = "Yerine koyma", publisherName = "", publisherStamp = ""
        ) }
        return ActiveSolvingSession(
            testItem = YksTestItem(id = "stylus_qa", topicId = "functions", title = "Test 1",
                fullTitle = "Fonksiyonlar • Test 1", difficulty = DifficultyLevel.ORTA, difficultyPosition = .5f, questions = questions),
            examType = ExamType.AYT, subjectId = "ayt_mat", subjectName = "Matematik",
            topicId = "functions", topicName = "Fonksiyonlar", viewMode = BoardViewMode.SINGLE_QUESTION
        )
    }
}
