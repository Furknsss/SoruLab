package com.example.viewmodel

import com.example.data.YksQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MistakeReviewPolicyTest {
    private fun q(number: Int, verified: Boolean = true) = YksQuestion(
        id = "source_" + number,
        number = number,
        topicHeader = "Fizik",
        stemTop = "Soru",
        stemBottom = "",
        options = mapOf("A" to "I", "B" to "II", "C" to "III", "D" to "IV", "E" to "V"),
        correctOption = "B",
        answerKeyVerified = verified,
        stepByStepSolution = "Kaynak çözüm"
    )

    @Test fun onlyWrongAnswersReappear() {
        val questions = (1..5).map { q(it) }
        val answers = mapOf(1 to "B", 2 to "A", 3 to "", 4 to "E", 5 to "B")
        assertEquals(listOf(2, 4),
            MistakeReviewPolicy.wrongAnswersOnly(questions, answers).map { it.number })
    }

    @Test fun unverifiedAndUnansweredAreNeverCalledWrong() {
        val questions = listOf(q(1, verified = false), q(2), q(3))
        val answers = mapOf(1 to "C", 2 to "-", 3 to " ")
        assertTrue(MistakeReviewPolicy.wrongAnswersOnly(questions, answers).isEmpty())
    }

    @Test fun correctOptionComparisonIsNormalized() {
        assertEquals(listOf(2),
            MistakeReviewPolicy.wrongAnswersOnly(listOf(q(1), q(2)),
                mapOf(1 to "b", 2 to "a")).map { it.number })
    }
}
