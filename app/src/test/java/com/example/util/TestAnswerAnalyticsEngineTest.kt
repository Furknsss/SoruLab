package com.example.util

import com.example.data.YksQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestAnswerAnalyticsEngineTest {
    private fun q(
        number: Int,
        lesson: String,
        topic: String,
        key: String,
        verified: Boolean = true,
        learning: String = "",
        type: String = "",
        body: String = ""
    ) = YksQuestion(
        id = "q_${number}", number = number, subject = lesson,
        topicHeader = topic, stemTop = body, stemBottom = "",
        options = mapOf("A" to "A", "B" to "B"),
        correctOption = key, stepByStepSolution = "",
        answerKeyVerified = verified, learningOutcome = learning,
        questionType = type
    )

    @Test
    fun unverifiedAnswersAreNeverCountedWrongOrBlank() {
        val questions = listOf(
            q(1, "Biyoloji", "Sinir Sistemi", "A", true,
                learning = "Sinaps", type = "Şekil"),
            q(2, "Biyoloji", "Sinir Sistemi", "B", true,
                learning = "Sinaps", type = "Şekil"),
            q(3, "Biyoloji", "Sinir Sistemi", "A", true,
                learning = "Nöron", type = "Bilgi"),
            q(4, "Biyoloji", "Sinir Sistemi", "D", false,
                learning = "Nöron", type = "Bilgi")
        )
        val result = TestAnswerAnalyticsEngine.calculate(
            questions, mapOf(1 to "A", 2 to "A", 4 to "B"),
            lessonHint = "Biyoloji", topicHint = "Sinir Sistemi"
        )
        assertEquals(4, result.total)
        assertEquals(1, result.correct)
        assertEquals(1, result.wrong)
        assertEquals(1, result.blank)
        assertEquals(1, result.ungraded)
        val byTopic = result.hierarchy.single().children.single()
        val sinaps = byTopic.children.first { it.label == "Sinaps" }
        assertEquals(1, sinaps.correct)
        assertEquals(1, sinaps.wrong)
        val neuron = byTopic.children.first { it.label == "Nöron" }
        assertEquals(1, neuron.blank)
        assertEquals(1, neuron.ungraded)
    }

    @Test
    fun mixedExamKeepsLessonsTopicsAndQuestionTypesIndependent() {
        val questions = listOf(
            q(1, "Biyoloji", "Sinir Sistemi", "A", body = "Sinapsta impuls iletimi"),
            q(2, "Matematik", "Trigonometri", "B", type = "Hesaplama"),
            q(3, "Biyoloji", "Endokrin Sistem", "C", learning = "Hormonlar")
        )
        val report = TestAnswerAnalyticsEngine.calculate(
            questions, mapOf(1 to "B", 2 to "B", 3 to "A"), "", ""
        )
        assertEquals(2, report.hierarchy.size)
        assertEquals(1, report.questionTypes.first { it.label == "Hesaplama" }.correct)
        assertTrue(report.hierarchy.any { it.label == "Matematik" })
        assertTrue(report.hierarchy.any { it.label == "Biyoloji" })
        assertTrue(report.inferredMetadataCount > 0)
    }

    @Test
    fun croppedPdfQuestionUsesHiddenOcrTextForSkillWithoutRenderingItTwice() {
        val imageQuestion = q(
            1, "Biyoloji", "Sinir Sistemi", "A",
            body = ""
        ).copy(
            croppedImagePath = "/test/image.png",
            analyticsText = "Sinaps bölgesinde nörotransmitter salgılanmasını gösteren şekil"
        )
        val report = TestAnswerAnalyticsEngine.calculate(
            listOf(imageQuestion), mapOf(1 to "B"), "Biyoloji", "Sinir Sistemi"
        )
        val outcome = report.hierarchy.single().children.single().children.single()
        assertEquals("Sinaps ve kimyasal iletim", outcome.label)
        assertEquals(1, outcome.wrong)
        assertTrue(report.inferredMetadataCount > 0)
    }

}
