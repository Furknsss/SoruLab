package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.DifficultyLevel
import com.example.data.ExamType
import com.example.data.YksQuestion
import com.example.data.YksTestItem
import com.example.ui.screens.DenemelerHubScreen
import com.example.ui.screens.LiveSolvingBoardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ActiveSolvingSession
import com.example.viewmodel.BoardViewMode
import com.example.viewmodel.DrawingTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Interactive real Android Compose UI coverage for the exam and solving flows.
 * Screenshots contain ONLY invented test questions, never publisher pages.
 * Real S-Pen hardware/pressure recognition still needs a physical tablet.
 */
@RunWith(AndroidJUnit4::class)
class SoruLabDeviceUiSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun denemelerScreenExposesPdfStudioAndFiltersWithoutLeavingEmptyView() {
        var pdfOpens = 0
        var selected by mutableStateOf<ExamType?>(null)
        compose.setContent {
            MyApplicationTheme {
                DenemelerHubScreen(
                    selectedFilter = selected,
                    mockExams = emptyList(),
                    solvedRecords = emptyList(),
                    customQuestionsCount = 7,
                    onSelectFilter = { selected = it },
                    onStartExam = {},
                    onReviewExam = {},
                    onOpenPdfStudio = { pdfOpens++ }
                )
            }
        }
        compose.onNodeWithTag("denemeler_screen").assertIsDisplayed()
        compose.onNodeWithTag("denemeler_open_pdf_studio").assertIsDisplayed()
        compose.onNodeWithTag("denemeler_open_pdf_studio").performClick()
        compose.runOnIdle { assertEquals(1, pdfOpens) }
        compose.onNodeWithTag("denemeler_filter_tyt").performScrollTo()
        compose.onNodeWithTag("denemeler_filter_tyt").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(ExamType.TYT, selected) }
        compose.onNodeWithTag("denemeler_filter_ayt").performScrollTo()
        compose.onNodeWithTag("denemeler_filter_ayt").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(ExamType.AYT, selected) }
        captureOnlySyntheticScreen("denemeler-phone.png")
    }

    @Test
    fun boardAllowsOpticalAnswerSelectionPenModeAndSafeFinishCancellation() {
        var confirmedFinishes = 0
        var latestAnswers = emptyMap<Int, String>()
        var smartPenToggles = 0
        var viewToggles = 0
        var lastSelectedTool = DrawingTool.HAND_PAN
        var lastBrushSize = -1
        compose.setContent {
            var session by remember { mutableStateOf(fixtureSession()) }
            var tool by remember { mutableStateOf(DrawingTool.HAND_PAN) }
            var currentBrushSize by remember { mutableStateOf(1) }
            MyApplicationTheme {
                LiveSolvingBoardScreen(
                    session = session,
                    strokes = emptyList(),
                    currentCanvasKey = "device_qa_page",
                    activeTool = tool,
                    selectedStrokeSizeIndex = currentBrushSize,
                    selectedPenColorHex = 0xFF1D4ED8L,
                    onExitSession = {},
                    onToggleViewMode = {
                        viewToggles++
                        session = session.copy(
                            viewMode = if (session.viewMode == BoardViewMode.FULL_PAGE)
                                BoardViewMode.SINGLE_QUESTION else BoardViewMode.FULL_PAGE
                        )
                    },
                    onSelectTool = {
                        tool = it
                        lastSelectedTool = it
                        if (it == DrawingTool.PEN) smartPenToggles++
                    },
                    onSelectStrokeSize = {
                        currentBrushSize = it
                        lastBrushSize = it
                    },
                    onSelectPenColor = {},
                    onAddStroke = { _, _, _ -> },
                    onBookPageChanged = {},
                    onClearCanvas = {},
                    onSelectQuestionIndex = { index ->
                        session = session.copy(currentQuestionIndex = index)
                    },
                    onSelectOption = { questionNumber, option ->
                        val answers = session.selectedAnswers + (questionNumber to option)
                        session = session.copy(selectedAnswers = answers)
                        latestAnswers = answers
                    },
                    onToggleOpticalForm = {
                        session = session.copy(
                            isOpticalFormDrawerOpen = !session.isOpticalFormDrawerOpen
                        )
                    },
                    onToggleStatsDrawer = {
                        session = session.copy(
                            isStatsDrawerOpen = !session.isStatsDrawerOpen
                        )
                    },
                    onToggleQuestionMap = {},
                    onFinishTest = {
                        confirmedFinishes++
                        session = session.copy(isReviewMode = true)
                    },
                    onGoToStatsTab = {},
                    onAskAiForQuestion = {}
                )
            }
        }
        compose.onNodeWithTag("live_solving_board").assertIsDisplayed()
        compose.onNodeWithTag("book_horizontal_pager").assertIsDisplayed()

        compose.onNodeWithTag("optical_form_tab").performClick()
        compose.onNodeWithTag("optic_q2_E").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("E", latestAnswers[2]) }
        compose.onNodeWithTag("optic_q1_B").performClick()
        compose.runOnIdle { assertEquals(mapOf(1 to "B", 2 to "E"), latestAnswers) }
        // Visual acceptance evidence: capture the drawer OPEN, not merely the
        // underlying book page after closing it.
        val panel = compose.onNodeWithTag("optical_form_drawer").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Two-question optical form expands into excessive unused tablet height",
            panel.height < viewport.height * 0.60f
        )
        captureOnlySyntheticScreen("optical-form-open.png")
        compose.onNodeWithTag("optical_form_tab").performClick()
        compose.onNodeWithTag("optical_form_drawer").assertDoesNotExist()

        // Premium toolbar has only one pencil: smart S Pen.
        // The deprecated finger-draw pencil must no longer occupy space.
        compose.onNodeWithTag("finger_draw_mode").assertDoesNotExist()
        compose.onNodeWithTag("smart_spen_mode").performClick()
        compose.onNodeWithTag("tool_eraser_btn").performClick()
        compose.runOnIdle { assertEquals(DrawingTool.ERASER, lastSelectedTool) }
        compose.onNodeWithTag("brush_size_2", useUnmergedTree = true)
            .performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, lastBrushSize) }
        compose.onNodeWithTag("smart_spen_mode").performClick()
        compose.runOnIdle { assertTrue(smartPenToggles >= 2) }
        captureOnlySyntheticScreen("solving-board-phone.png")

        compose.onNodeWithTag("toggle_view_mode_btn").performClick()
        compose.runOnIdle { assertEquals(1, viewToggles) }

        compose.onNodeWithTag("top_finish_test_btn").performClick()
        compose.onNodeWithTag("finish_confirmation_dialog").assertIsDisplayed()
        compose.onNodeWithTag("cancel_finish_test_btn").performClick()
        compose.runOnIdle { assertEquals(0, confirmedFinishes) }
        compose.onNodeWithTag("top_finish_test_btn").performClick()
        compose.onNodeWithTag("confirm_finish_test_btn").performClick()
        compose.runOnIdle { assertEquals(1, confirmedFinishes) }
        // Archive actual pixels of the report and stats on phone + tablet.
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("full_screen_report_card")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("full_screen_report_card").assertIsDisplayed()
        compose.onNodeWithTag("result_topic_success_card").assertExists()
        captureOnlySyntheticScreen("test-results-open.png")
        compose.onNodeWithTag("show_in_book_btn").performClick()
        compose.onNodeWithTag("stats_drawer_tab").assertIsDisplayed().performClick()
        compose.onNodeWithTag("stats_drawer_panel").assertIsDisplayed()
        captureOnlySyntheticScreen("test-statistics-open.png")
    }

    private fun captureOnlySyntheticScreen(filename: String) {
        saveSyntheticQaScreenshot(compose.onRoot().captureToImage().asAndroidBitmap(), filename)
    }

    private fun fixtureSession(): ActiveSolvingSession {
        val questions = (1..2).map { n ->
            YksQuestion(
                id = "original_qa_$n",
                number = n,
                topicHeader = "Fonksiyonlar",
                stemTop = "f(x) = 2x + $n olduğuna göre",
                stemBottom = "f(2) kaçtır?",
                options = mapOf("A" to "3", "B" to "4", "C" to "5",
                    "D" to "6", "E" to "7"),
                correctOption = if (n == 1) "C" else "D",
                stepByStepSolution = "Yerine koyma yöntemi",
                publisherName = "",
                publisherStamp = ""
            )
        }
        return ActiveSolvingSession(
            testItem = YksTestItem(
                id = "device_screen_qa_test",
                topicId = "fonksiyonlar",
                title = "Test 1",
                fullTitle = "Fonksiyonlar - Test 1",
                difficulty = DifficultyLevel.ORTA,
                difficultyPosition = .5f,
                questions = questions
            ),
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicId = "fonksiyonlar",
            topicName = "Fonksiyonlar"
        )
    }
}
