package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.YksQuestion
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
class QuestionSolutionDialogTest {
    @get:Rule val compose = createComposeRule()
    private fun question(text: String = "", verified: Boolean = false) = YksQuestion(
        id = "source", number = 2, topicHeader = "Fonksiyonlar", stemTop = "", stemBottom = "",
        options = emptyMap(), correctOption = "C", answerKeyVerified = verified, stepByStepSolution = text
    )

    @Test fun narrowPhoneShowsHonestEmptyStateAndCanReturnToQuestion() {
        var closed = 0
        compose.setContent {
            MyApplicationTheme {
                QuestionSolutionDialog(question(), "", false, false, {}, { closed++ })
            }
        }
        compose.onNodeWithText("2. Soru • Soru Çözümü").assertIsDisplayed()
        compose.onNodeWithTag("stored_solution_empty").assertIsDisplayed()
        compose.onNodeWithText("Doğrulanmış cevap anahtarı: C").assertDoesNotExist()
        compose.onNodeWithText("1. Kritik Bilgi & Teorem").assertDoesNotExist()
        compose.onNodeWithText("Soruma dön").assertIsDisplayed().performClick()
        assertEquals(1, closed)
    }

    @Test fun loadFailureOffersRetryWithoutShowingAnAnswerAsASolution() {
        var retried = 0
        compose.setContent {
            MyApplicationTheme {
                QuestionSolutionDialog(question(verified = true), "", false, true, { retried++ }, {})
            }
        }
        compose.onNodeWithTag("stored_solution_error").assertIsDisplayed()
        compose.onNodeWithText("Tekrar dene").performClick()
        assertEquals(1, retried)
        compose.onNodeWithText("Kayıtlı çözüm").assertDoesNotExist()
    }

    @Test fun longStoredExplanationScrollsWhileReturnButtonStaysReachable() {
        val content = (1..45).joinToString("\n") { "Adım $it: f(x) = x + $it" } + "\nSon matematik satırı"
        compose.setContent {
            MyApplicationTheme {
                QuestionSolutionDialog(question(content), "", false, false, {}, {})
            }
        }
        compose.onNodeWithText(content).performScrollTo()
        compose.onNodeWithText("Soruma dön").assertIsDisplayed()
        compose.onNodeWithText("Temel YKS kazanım ve muhakeme ilkesi.").assertDoesNotExist()
    }
}
