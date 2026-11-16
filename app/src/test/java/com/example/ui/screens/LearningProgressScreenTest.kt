package com.example.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.*
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h1280dp")
class LearningProgressScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun actualStatsScreenExpandsOutcomeAndPreservesResultNavigation() {
        val question = YksQuestion(id = "q1", number = 1, subject = "Biyoloji",
            topicHeader = "Canlıların Temel Bileşenleri", stemTop = "", stemBottom = "",
            options = mapOf("A" to "a"), correctOption = "A", stepByStepSolution = "",
            learningOutcome = "Enzimler")
        val result = TestResultRecord(id = "result-one", examType = "TYT",
            subjectId = "tyt_biyoloji", subjectName = "Biyoloji",
            answersMapJson = """{"1":"A"}""", createdAt = Timestamp.now(),
            testSnapshotJson = TestSnapshotCodec.encode(YksTestItem(
                "test", "", "Test", "Test", DifficultyLevel.ORTA, 0.5f,
                questions = listOf(question))))
        var inspected = ""
        compose.setContent {
            MaterialTheme {
                IstatistiklerimScreen(listOf(result), 1, onInspectResult = { inspected = it.id })
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("stats_recent_30").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("stats_recent_30").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("istatistiklerim_list").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("istatistiklerim_list")
            .performScrollToNode(hasText("Canlıların Temel Bileşenleri"))
        compose.onNodeWithText("Canlıların Temel Bileşenleri").performClick()
        compose.onNodeWithTag("stats_outcome_tyt_biyoloji-enzymes").assertExists()
        compose.onNodeWithText("Enzimler").assertExists()
        compose.onNodeWithTag("istatistiklerim_list")
            .performScrollToNode(hasTestTag("stats_result_card_result-one"))
        compose.onNodeWithTag("stats_result_card_result-one").performClick()
        assertEquals("result-one", inspected)
    }
}
