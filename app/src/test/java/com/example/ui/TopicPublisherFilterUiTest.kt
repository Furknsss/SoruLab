package com.example.ui.screens

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.PoolAvailabilityCheck
import com.example.viewmodel.PublisherChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-xhdpi")
class TopicPublisherFilterUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedDrBiyolojiIsPassedToAutomaticGeneratorWithoutExtraForm() {
        val choice = AtomicReference<String?>("unselected")
        val created = AtomicBoolean(false)
        showTopic(choice, created)
        compose.onNodeWithTag("topic_top_publisher_filter").assertIsDisplayed().performClick()
        compose.waitUntil(8000) {
            compose.onAllNodesWithTag("topic_publisher_option_drbiyoloji")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("topic_publisher_option_drbiyoloji").performClick()
        compose.onNodeWithTag("interactive_test_builder_dialog").assertDoesNotExist()
        compose.onNodeWithTag("topic_detail_olustur_button").performClick()
        compose.waitUntil(8000) { created.get() }
        assertEquals("drbiyoloji", choice.get())
    }

    @Test fun defaultPublisherSelectionUsesSmartMixWithoutOpeningForm() {
        val choice = AtomicReference<String?>("unselected")
        val created = AtomicBoolean(false)
        showTopic(choice, created)
        compose.onNodeWithTag("topic_detail_olustur_button").assertIsDisplayed().performClick()
        compose.waitUntil(8000) { created.get() }
        assertNull(choice.get())
        assertFalse(
            compose.onAllNodesWithTag("interactive_test_builder_dialog")
                .fetchSemanticsNodes().isNotEmpty()
        )
    }

    private fun showTopic(choice: AtomicReference<String?>, created: AtomicBoolean) {
        val topic = YksTopic(
            id = "sinir_sistemi",
            subjectId = "ayt_biyoloji",
            name = "Sinir Sistemi",
            questionCount = 20,
            completedTests = 0,
            totalTests = 0,
            badgeColorHex = 0xFFDB2777,
            iconType = "bio",
            tests = emptyList()
        )
        compose.setContent {
            MyApplicationTheme {
                TopicTestsDetailView(
                    topic = topic,
                    selectedTestLevel = TopicTestLevel.OSYM,
                    currentLevelTests = emptyList(),
                    isTestCompleted = { false },
                    onCheckPoolAvailability = {
                        PoolAvailabilityCheck(
                            canGenerate = true,
                            availableQuestionCount = 12,
                            reasonTitle = "Hazır",
                            reasonMessage = "12 doğrulanmış soru",
                            availablePublishers = listOf(
                                PublisherChoice("drbiyoloji", "Dr. Biyoloji", 12),
                                PublisherChoice("biyotik", "Biyotik", 8)
                            ),
                            questionCountForSelection = { _, _, publisher ->
                                if (publisher == "biyotik") 8 else 12
                            },
                            onGenerateCustomTest = { _, _, _, publisher ->
                                choice.set(publisher)
                                YksTestItem(
                                    id = "auto_generated_test",
                                    topicId = topic.id,
                                    title = "Sinir Sistemi - Test 1",
                                    fullTitle = "Sinir Sistemi - Test 1",
                                    difficulty = DifficultyLevel.OSYM,
                                    difficultyPosition = 0.6f,
                                    questions = emptyList()
                                )
                            }
                        )
                    },
                    onBack = {},
                    onSelectTestLevel = {},
                    onTestClicked = {},
                    onStartTestImmediately = { created.set(true) },
                    onDeleteTest = {},
                    onOpenAiAssistant = {},
                    onOpenPdfCropper = {}
                )
            }
        }
    }
}
