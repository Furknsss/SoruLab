package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.DifficultyLevel
import com.example.data.ExamType
import com.example.data.YksQuestion
import com.example.data.YksTestItem
import com.example.ui.screens.LiveSolvingBoardScreen
import com.example.ui.screens.scoreReportHeadline
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ActiveSolvingSession
import com.example.viewmodel.DrawingTool
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises real session callbacks rather than just checking static labels. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class LiveSolvingBoardScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun ungradedTestNeverClaimsStudentFailedOrSucceeded() {
        assertEquals("Cevap anahtarı doğrulanmadı", scoreReportHeadline(0, 0))
        assertEquals("Pes etmek yok!", scoreReportHeadline(0, 5))
        assertEquals("Harika iş çıkardın!", scoreReportHeadline(4, 5))
    }

    @Test
    fun tabletSheetsAdaptQuestionCountToAvailableHeightWithoutLosingOrder() {
        val template = fixtureSession().testItem.questions.first()
        val questions = (1..12).map { number -> template.copy(id = "packed_$number", number = number,
            croppedImagePath = "fixture.png", normLeft = 0f, normRight = 0.45f, normTop = 0f, normBottom = 0.25f) }
        val compact = com.example.ui.screens.packQuestionsIntoTwoColumnPages(questions, 2.1f)
        val tall = com.example.ui.screens.packQuestionsIntoTwoColumnPages(questions, 4.2f)
        assertEquals(questions.map { it.id }, compact.flatMap { it.questions }.map { it.id })
        assertEquals(questions.map { it.id }, tall.flatMap { it.questions }.map { it.id })
        // The original hard cap of two is intentionally gone: short questions can
        // share the same column when the device offers sufficient vertical room.
        org.junit.Assert.assertTrue(tall.any { it.left.size > 2 || it.right.size > 2 })
        org.junit.Assert.assertTrue(tall.size <= compact.size)
        // A long question always remains whole, never split across columns.
        org.junit.Assert.assertEquals(questions.size, tall.flatMap { it.questions }.size)
    }

    @Test
    fun fourMixedSourceQuestionsUseBalancedTwoByTwoLayout() {
        val original = fixtureSession().testItem.questions.first()
        val source = (1..4).map { number ->
            original.copy(
                id = "balanced_$number",
                number = number,
                croppedImagePath = "book_" + number + ".png"
            )
        }
        val ratios = source.associate { it.id to 0.55f }
        val pages = com.example.ui.screens.packQuestionsIntoTwoColumnPages(
            source, columnCapacity = 1.55f, maxQuestionsPerColumn = 4,
            columnWidthDp = 240f, actualImageRatios = ratios
        )
        assertEquals(1, pages.size)
        assertEquals(listOf(1, 2), pages[0].left.map { it.number })
        assertEquals(listOf(3, 4), pages[0].right.map { it.number })
    }

    @Test
    fun veryTallDrBiyolojiCropNeverGetsMixedIntoAnOverfilledColumn() {
        val original = fixtureSession().testItem.questions.first()
        val source = (1..7).map { number ->
            original.copy(
                id = "height_$number",
                number = number,
                croppedImagePath = "book_" + number + ".png"
            )
        }
        val ratios = source.associate { question ->
            question.id to if (question.number == 1 || question.number == 5) 4.5f else 0.6f
        }
        val pages = com.example.ui.screens.packQuestionsIntoTwoColumnPages(
            source, columnCapacity = 2.15f, maxQuestionsPerColumn = 4,
            columnWidthDp = 240f, actualImageRatios = ratios
        )
        assertEquals(source.map { it.id }, pages.flatMap { it.questions }.map { it.id })
        org.junit.Assert.assertTrue(pages.size >= 2)
        val tallPage = pages.first { page -> page.questions.any { it.number == 1 } }
        assertEquals(listOf(1), tallPage.left.map { it.number })
        org.junit.Assert.assertTrue(pages.all { it.left.size <= 4 && it.right.size <= 4 })
    }

    @Test
    fun smallImageCropsUseRealImageRatiosRatherThanFixedTwoPerColumn() {
        val original = fixtureSession().testItem.questions.first()
        val source = (1..12).map { number ->
            original.copy(
                id = "compact_$number",
                number = number,
                croppedImagePath = "book_" + number + ".png"
            )
        }
        val ratios = source.associate { it.id to 0.35f }
        val pages = com.example.ui.screens.packQuestionsIntoTwoColumnPages(
            source, columnCapacity = 2.7f, maxQuestionsPerColumn = 4,
            columnWidthDp = 240f, actualImageRatios = ratios
        )
        assertEquals(source.map { it.id }, pages.flatMap { it.questions }.map { it.id })
        org.junit.Assert.assertTrue(pages.any { it.left.size > 2 || it.right.size > 2 })
        org.junit.Assert.assertTrue(pages.none { it.right.isEmpty() && it.questions.size > 1 })
    }

    @Test
    fun cancelFinishKeepsTheSessionActive() {
        var finishCount = 0
        showBoard(onFinish = { finishCount++ })

        compose.onNodeWithTag("top_finish_test_btn").performClick()
        compose.onNodeWithTag("finish_confirmation_dialog").assertIsDisplayed()
        compose.onNodeWithTag("cancel_finish_test_btn").performClick()

        compose.onNodeWithTag("finish_confirmation_dialog").assertDoesNotExist()
        compose.onNodeWithTag("live_solving_board").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, finishCount) }
    }

    @Test
    fun confirmingFinishInvokesTheCallbackOnce() {
        var finishCount = 0
        showBoard(onFinish = { finishCount++ })

        compose.onNodeWithTag("top_finish_test_btn").performClick()
        compose.onNodeWithTag("confirm_finish_test_btn").performClick()

        compose.onNodeWithTag("finish_confirmation_dialog").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, finishCount) }
    }

    @Test
    fun opticalFormUpdatesTheCorrectQuestionAndCanBeClosedOnPhone() {
        var latestAnswers: Map<Int, String> = emptyMap()
        showBoard(onAnswersChanged = { latestAnswers = it })

        compose.onNodeWithTag("optical_form_tab").performClick()
        compose.onNodeWithTag("optical_form_drawer").assertIsDisplayed()
        compose.onNodeWithTag("optic_q2_E").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(mapOf(2 to "E"), latestAnswers) }

        compose.onNodeWithTag("optic_q1_B").performClick()
        compose.runOnIdle { assertEquals(mapOf(1 to "B", 2 to "E"), latestAnswers) }
        compose.onNodeWithTag("drawer_finish_test_btn").assertIsDisplayed()
        compose.onNodeWithTag("optical_form_tab").performClick()
        compose.onNodeWithTag("optical_form_drawer").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun tabletOpticalFormRetainsPageAndFinishControls() {
        showBoard()
        compose.onNodeWithTag("board_back_button").assertIsDisplayed()
        compose.onNodeWithTag("top_finish_test_btn").assertIsDisplayed()
        compose.onNodeWithTag("optical_form_tab").performClick()
        compose.onNodeWithTag("optic_q1_A").assertIsDisplayed()
        compose.onNodeWithTag("optic_q2_E").assertIsDisplayed()
        compose.onNodeWithTag("drawer_finish_test_btn").performClick()
        compose.onNodeWithTag("finish_confirmation_dialog").assertIsDisplayed()
    }

    private fun showBoard(
        onFinish: () -> Unit = {},
        onAnswersChanged: (Map<Int, String>) -> Unit = {}
    ) {
        compose.setContent {
            var session by androidx.compose.runtime.remember { mutableStateOf(fixtureSession()) }
            MyApplicationTheme {
                LiveSolvingBoardScreen(
                    session = session,
                    strokes = emptyList(),
                    currentCanvasKey = "full_page_fixture",
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
                    onSelectQuestionIndex = { session = session.copy(currentQuestionIndex = it) },
                    onSelectOption = { question, answer ->
                        val answers = session.selectedAnswers + (question to answer)
                        session = session.copy(selectedAnswers = answers)
                        onAnswersChanged(answers)
                    },
                    onToggleOpticalForm = {
                        session = session.copy(isOpticalFormDrawerOpen = !session.isOpticalFormDrawerOpen)
                    },
                    onToggleStatsDrawer = {},
                    onToggleQuestionMap = {},
                    onFinishTest = onFinish,
                    onGoToStatsTab = {},
                    onAskAiForQuestion = {}
                )
            }
        }
    }

    private fun fixtureSession(): ActiveSolvingSession {
        val questions = (1..2).map { number ->
            YksQuestion(
                id = "question_$number",
                number = number,
                topicHeader = "Fonksiyonlar",
                stemTop = "f(x) = 2x + $number olduğuna göre",
                stemBottom = "f(2) kaçtır?",
                options = mapOf("A" to "3", "B" to "4", "C" to "5", "D" to "6", "E" to "7"),
                correctOption = if (number == 1) "C" else "D",
                stepByStepSolution = "x yerine 2 yazılır.",
                publisherName = "",
                publisherStamp = ""
            )
        }
        return ActiveSolvingSession(
            testItem = YksTestItem(
                id = "functions_test",
                topicId = "functions",
                title = "ÖSYM 1",
                fullTitle = "Fonksiyonlar - ÖSYM 1",
                difficulty = DifficultyLevel.ORTA,
                difficultyPosition = 0.5f,
                questions = questions
            ),
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicId = "functions",
            topicName = "Fonksiyonlar"
        )
    }
}
