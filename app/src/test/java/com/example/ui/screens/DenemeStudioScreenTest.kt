package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
@Config(sdk = [34], qualifiers = "w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DenemeStudioScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun bank(): List<CustomQuestionRecord> {
        val subject = SoruLabCatalog.allSubjects.first { it.id == "tyt_fizik" }
        return subject.topics.flatMap { topic ->
            (1..4).map { n ->
                CustomQuestionRecord(
                    id = topic.id + n,
                    examType = "TYT",
                    subjectId = subject.id,
                    subjectName = subject.name,
                    topicId = topic.id,
                    topicName = topic.name,
                    difficulty = "Orta",
                    correctOption = "A",
                    questionText = "Örnek fizik muhakeme sorusu $n (${topic.name})",
                    learningOutcome = "${topic.name} Temel ÖSYM Kazanımı",
                    publisherTag = "3D Yayınları",
                    sourceBookTitle = "3D TYT Fizik Soru Bankası",
                    aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta("A", answerKeyVerified = true)
                )
            }
        }
    }

    @Test fun phoneCanCreatePhysicsExamAndStrictDifficultyDisablesStart() {
        var request: DenemeRequest? = null
        compose.setContent {
            MaterialTheme {
                DenemeStudioScreen(
                    null,
                    emptyList(),
                    emptyList(),
                    bank(),
                    {},
                    { request = it },
                    {},
                    {},
                    {}
                )
            }
        }
        compose.onNodeWithTag("create_deneme_button").assertIsDisplayed()
        capture("deneme-phone-home")
        compose.onNodeWithTag("create_deneme_button").performClick()
        compose.onNodeWithTag("deneme_branch_selector").performScrollTo().performClick()
        compose.onNodeWithText("TYT Fizik • 7 soru").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Hazır • 7 doğrulanmış soru").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Konu dağılımını gör").performScrollTo().performClick()
        compose.onNodeWithText("Optik").performScrollTo()
        capture("deneme-phone-distribution")
        compose.onNodeWithTag("deneme_difficulty_row").performScrollTo()
        compose.onNodeWithText("Zor", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.waitForIdle()
        capture("deneme-phone-after-zor")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Soru havuzunda eksikler var").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("deneme_confirm_create").assertIsNotEnabled()
        compose.onNodeWithTag("deneme_difficulty_row").performScrollTo()
        compose.onNodeWithText("Orta", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Hazır • 7 doğrulanmış soru").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("deneme_confirm_create").assertIsEnabled().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("deneme_ready_start_btn").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("deneme_ready_start_btn").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("tyt_fizik", request?.blueprintId)
            assertEquals(DenemeDifficulty.ORTA, request?.difficulty)
            assertEquals(DenemeTimerMode.COUNTDOWN, request?.timerMode)
        }
    }

    @Test
    fun twoStageCreationStopwatchReadyInfoAndPreparedStartFlow() {
        var startedReadyPlan: DenemePlan.Ready? = null
        compose.setContent {
            MaterialTheme {
                DenemeStudioScreen(
                    selectedFilter = null,
                    mockExams = emptyList(),
                    solvedRecords = emptyList(),
                    customQuestions = bank(),
                    onSelectFilter = {},
                    onCreateDeneme = {},
                    onStartExam = {},
                    onReviewExam = {},
                    onOpenPdfStudio = {},
                    onStartPreparedDeneme = { startedReadyPlan = it }
                )
            }
        }
        compose.onNodeWithTag("create_deneme_button").performClick()
        compose.onNodeWithTag("deneme_branch_selector").performScrollTo().performClick()
        compose.onNodeWithText("TYT Fizik • 7 soru").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Hazır • 7 doğrulanmış soru").fetchSemanticsNodes().isNotEmpty()
        }

        // Select Kronometre (0'dan Başla)
        compose.onNodeWithTag("deneme_timer_mode_stopwatch").performScrollTo().performClick()
        compose.onNodeWithText("Sayaç 00:00'dan ileri doğru sayar.", substring = true).performScrollTo().assertIsDisplayed()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("deneme_confirm_create") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }

        // Inspect Stage 1: "Denemeniz Oluşturuluyor..."
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("deneme_confirm_create").assertIsEnabled().performClick()
        compose.mainClock.advanceTimeBy(300L)
        compose.onNodeWithTag("deneme_generating_state").assertIsDisplayed()
        compose.onNodeWithText("Denemeniz Oluşturuluyor...").assertIsDisplayed()
        compose.onNodeWithText("Kötü kırpılmış sorular eleniyor").assertIsDisplayed()
        compose.onNodeWithText("2018–2026 ÖSYM kazanımları ve soru tipleri eşleştiriliyor").assertIsDisplayed()
        compose.onNodeWithText("Kitapçık sırası oluşturuluyor").assertIsDisplayed()
        capture("deneme-generating-stage")

        // Advance to Stage 2: "Denemeniz Oluşturuldu"
        compose.mainClock.advanceTimeBy(950L)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("deneme_ready_state").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Denemeniz Oluşturuldu").assertIsDisplayed()
        compose.onNodeWithText("00:00 Kronometre").assertIsDisplayed()
        capture("deneme-ready-stage")

        // Tap top-right ⓘ button and verify compact question -> konu -> kazanım -> kitap rows
        compose.onNodeWithTag("deneme_ready_info_btn").assertIsDisplayed().performClick()
        compose.onNodeWithTag("deneme_questions_info_list").assertIsDisplayed()
        compose.onNodeWithTag("deneme_info_row_1").assertIsDisplayed()
        compose.onAllNodesWithText("1. Soru —", substring = true).onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("Kazanım:", substring = true).onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("3D TYT Fizik Soru Bankası", substring = true)
            .onFirst()
            .assertIsDisplayed()
        capture("deneme-ready-info-dialog")
        compose.onNodeWithTag("deneme_questions_info_close_btn").performClick()

        // Start prepared deneme directly
        compose.onNodeWithTag("deneme_ready_start_btn").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertNotNull(startedReadyPlan)
            assertEquals(7, startedReadyPlan?.test?.questions?.size)
            assertEquals(0, startedReadyPlan?.minutes)
        }
    }

    @Test
    fun mainScreenKeepsInlineListsClutterFreeAtThreeAndOpensFullSheets() {
        val exams = (1..5).map { idx ->
            val ready = DenemePlanner.build(
                DenemeRequest(DenemeKind.BRANS, ExamType.TYT, "tyt_fizik"),
                bank(),
                sequence = 1_700_000_000_000L + idx
            ) as DenemePlan.Ready
            DenemeBlueprints.fromSnapshot(ready.test)!!
        }
        val records = exams.mapIndexed { idx, exam ->
            TestResultRecord(
                id = "rec_$idx",
                testId = exam.linkedTest.id,
                testTitle = exam.title,
                correctCount = 5,
                wrongCount = 1,
                emptyCount = 1,
                answersMapJson = "{\"1\":\"A\"}",
                testSnapshotJson = TestSnapshotCodec.encode(exam.linkedTest),
                durationSeconds = 420
            )
        }
        compose.setContent {
            MaterialTheme {
                DenemeStudioScreen(
                    selectedFilter = null,
                    mockExams = exams,
                    solvedRecords = records,
                    customQuestions = bank(),
                    onSelectFilter = {},
                    onCreateDeneme = {},
                    onStartExam = {},
                    onReviewExam = {},
                    onOpenPdfStudio = {}
                )
            }
        }
        // Saved card ⓘ button opens question mapping sheet directly
        val newestExam = exams.maxBy { it.id }
        compose.onNodeWithTag("deneme_info_${newestExam.id}").performScrollTo().performClick()
        compose.onNodeWithTag("deneme_questions_info_list").assertIsDisplayed()
        compose.onNodeWithTag("deneme_questions_info_close_btn").performClick()
        compose.waitForIdle()

        // Oldest 2 exams are not dumped inline; "Tüm Denemelerim (5)" button is shown
        val oldestExam = exams.minBy { it.id }
        compose.onAllNodesWithTag("deneme_card_${oldestExam.id}").assertCountEquals(0)
        compose.onNodeWithTag("denemeler_screen").performScrollToNode(hasTestTag("show_all_denemeler_btn"))
        compose.onNodeWithTag("show_all_denemeler_btn").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("all_saved_denemeler_list").assertIsDisplayed()
        compose.onNodeWithTag("all_saved_denemeler_close_btn").performClick()
        compose.waitForIdle()

        // History is also capped at 3 inline with "Tüm Sonuç Geçmişi (5)" button
        compose.onNodeWithTag("denemeler_screen").performScrollToNode(hasTestTag("show_all_histories_btn"))
        compose.onNodeWithTag("show_all_histories_btn").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("all_histories_list").assertIsDisplayed()
        compose.onNodeWithTag("all_histories_close_btn").performClick()
        compose.waitForIdle()
    }

    @Test @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun tabletShowsSavedExamAndOpensItsExactResult() {
        val ready = DenemePlanner.build(
            DenemeRequest(DenemeKind.BRANS, ExamType.TYT, "tyt_fizik"),
            bank(),
            sequence = 77
        ) as DenemePlan.Ready
        val record = TestResultRecord(
            id = "attempt",
            testId = ready.test.id,
            testTitle = ready.test.title,
            correctCount = 1,
            wrongCount = 1,
            emptyCount = 5,
            answersMapJson = "{\"1\":\"A\",\"2\":\"B\"}",
            testSnapshotJson = TestSnapshotCodec.encode(ready.test),
            durationSeconds = 360
        )
        var reviewed: TestResultRecord? = null
        compose.setContent {
            MaterialTheme {
                DenemeStudioScreen(
                    null,
                    listOf(DenemeBlueprints.fromSnapshot(ready.test)!!),
                    listOf(record),
                    bank(),
                    {},
                    {},
                    {},
                    { reviewed = it },
                    {}
                )
            }
        }
        capture("deneme-tablet-home")
        compose.onNodeWithText("Karnem").performClick()
        compose.onNodeWithText("0,75 NET").assertIsDisplayed()
        capture("deneme-tablet-result")
        compose.onNodeWithText("Sorularımı İncele").performClick()
        compose.runOnIdle { assertEquals(record, reviewed) }
    }

    private fun capture(name: String) {
        var captured: Bitmap? = null
        compose.runOnIdle {
            val view = org.robolectric.shadows.ShadowDialog.getLatestDialog()
                ?.takeIf { it.isShowing }?.window?.decorView ?: compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            captured = bitmap
        }
        val bitmap = requireNotNull(captured)
        val file = File("build/reports/test-layout/$name.png")
        requireNotNull(file.parentFile).mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
