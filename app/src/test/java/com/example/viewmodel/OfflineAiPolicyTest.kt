package com.example.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.YksQuestion
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineAiPolicyTest {
    @Test fun explicitTwinEngineIsAvailableButOrdinarySolutionsStayLocal() {
        assertNotNull(Class.forName("com.google.firebase.ai.GenerativeModel"))
    }

    @Test fun solutionControlsReturnOnlyStoredSolutionsWithoutFirebaseInitialization() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val q = YksQuestion(
            id = "local", number = 1, stemTop = "Soru", stemBottom = "",
            options = mapOf("A" to "1", "B" to "2"), correctOption = "B",
            stepByStepSolution = "Kaynakta kayıtlı çözüm."
        )
        val model = AiSolverViewModel()
        var solution = ""
        model.generateQuestionSolutionOnDemand(q, context) { solution = it }
        assertEquals(q.stepByStepSolution, solution)
        model.askAboutQuestion(q, context)
        assertEquals(q.stepByStepSolution, model.messages.value.last().text)
        model.sendFreeMessage("Soruyu çöz")
        assertTrue(model.messages.value.last().text.contains("bulut AI çağrısı yapılmaz"))
        model.generateQuestionSolutionOnDemand(q.copy(stepByStepSolution = ""), context) {
            assertTrue(it.contains("kayıtlı çözüm bulunmuyor"))
            assertFalse(it.contains("doğru yanıt B"))
        }
    }
}
