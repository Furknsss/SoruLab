package com.example.viewmodel

import com.example.data.CustomQuestionRecord
import com.example.data.DifficultyLevel
import com.example.data.YksQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroundedTwinQuestionMatcherTest {

    private val original = YksQuestion(
        id = "source_test_q1",
        number = 1,
        topicHeader = "Fonksiyonlar",
        croppedImagePath = "/questions/original.png",
        sourceQuestionId = "original",
        sourceBookTitle = "Kitap 1",
        sourcePrintedQuestionNumber = 1,
        analyticsText = "f(x) fonksiyonunun değerlerini bulunuz.",
        stemTop = "",
        stemBottom = "",
        options = mapOf("A" to "1", "B" to "2", "C" to "3", "D" to "4", "E" to "5"),
        correctOption = "C",
        difficulty = DifficultyLevel.ORTA,
        stepByStepSolution = ""
    )

    private fun record(
        id: String,
        topic: String = "Fonksiyonlar",
        topicId: String = "fonksiyonlar",
        subjectId: String = "ayt_mat",
        answerKeyVerified: Boolean = true,
        imagePath: String = "/questions/$id.png",
        difficulty: String = "Orta",
        sourceBook: String = "Kitap 2",
        printedQuestion: Int = 2
    ) = CustomQuestionRecord(
        id = id, userId = "student",
        examType = "AYT", subjectId = subjectId, subjectName = "Matematik",
        topicId = topicId, topicName = topic,
        questionText = "Aşağıdaki ifadeye göre f(2) kaçtır? Soru: $id",
        difficulty = difficulty, correctOption = "B",
        sourceBookTitle = sourceBook,
        aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta(
            solutionText = "",
            croppedImagePath = imagePath,
            answerKeyVerified = answerKeyVerified,
            fiveTierDifficulty = difficulty,
            questionNumberInBook = printedQuestion
        )
    )

    private fun choose(pool: List<CustomQuestionRecord>, source: YksQuestion = original) =
        GroundedTwinQuestionMatcher.select(
            source = source,
            subjectId = "ayt_mat",
            topicId = "fonksiyonlar",
            topicName = "Fonksiyonlar",
            pool = pool,
            isUsable = { true }
        )

    @Test fun neverRepeatsOriginalOrAReimportedCopy() {
        assertNull(choose(listOf(
            record("original"),
            record("duplicate", imagePath = original.croppedImagePath),
            record("same_printed_question", sourceBook = "Kitap 1", printedQuestion = 1)
        )))
    }

    @Test fun rejectsOtherSubjectsTopicsAndUnverifiedAnswerKeys() {
        assertNull(choose(listOf(
            record("physics", subjectId = "ayt_fizik"),
            record("trigonometry", topic = "Trigonometri", topicId = "trigonometri"),
            record("unverified", answerKeyVerified = false)
        )))
    }

    @Test fun picksVerifiedAlternativeClosestToOriginalDifficulty() {
        val result = choose(listOf(
            record("hard", difficulty = "Çok Zor"),
            record("medium", difficulty = "Orta"),
            record("easy", difficulty = "Kolay")
        ))
        assertEquals("medium", result?.id)
    }

    @Test fun respectsAlreadyUsedTwinIdsAndUnavailableImages() {
        val result = GroundedTwinQuestionMatcher.select(
            source = original,
            subjectId = "ayt_mat", topicId = "fonksiyonlar", topicName = "Fonksiyonlar",
            pool = listOf(record("already_used"), record("missing_image"), record("valid")),
            excludedIds = setOf("already_used"),
            isUsable = { it.id != "missing_image" }
        )
        assertEquals("valid", result?.id)
    }

    @Test fun emptyPoolGivesNoInventedQuestion() {
        assertNull(choose(emptyList()))
    }
}
