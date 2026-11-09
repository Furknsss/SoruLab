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
        analyticsText = "f(x) = 2x + 3 fonksiyonu veriliyor. Buna göre f(4) değeri kaçtır?",
        learningOutcome = "Doğrusal fonksiyonda değer hesaplama",
        questionType = "Fonksiyon değer bulma",
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
        printedQuestion: Int = 2,
        learningOutcome: String = "Doğrusal fonksiyonda değer hesaplama"
    ) = CustomQuestionRecord(
        id = id, userId = "student",
        examType = "AYT", subjectId = subjectId, subjectName = "Matematik",
        topicId = topicId, topicName = topic,
        questionText = "g(x) = 3x + 5 fonksiyonu veriliyor. Buna göre g(4) değeri kaçtır? Soru: $id",
        questionType = "Fonksiyon değer bulma",
        learningOutcome = learningOutcome,
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

    @Test fun knownSubOutcomeRequiresSameVerifiedOutcome() {
        val taggedOriginal = original.copy(learningOutcome = "Fonksiyonların bileşkesi")
        assertEquals("matching", choose(listOf(
            record("different", learningOutcome = "Fonksiyonun görüntü kümesi"),
            record("unknown"),
            record("matching", learningOutcome = "Fonksiyonların bileşkesi")
        ), taggedOriginal)?.id)
    }

    @Test fun previouslyUnusedTwinIsPreferred() {
        val chosen = GroundedTwinQuestionMatcher.select(
            source = original, subjectId = "ayt_mat",
            topicId = "fonksiyonlar", topicName = "Fonksiyonlar",
            pool = listOf(record("first"), record("second")),
            previousUseCounts = mapOf("first" to 3),
            isUsable = { true }
        )
        assertEquals("second", chosen?.id)
    }

    @Test fun emptyPoolGivesNoInventedQuestion() {
        assertNull(choose(emptyList()))
    }

    @Test fun sameTopicButDifferentProblemStructureIsNotATwin() {
        val unrelated = record("topic_only").copy(
            questionText = "Fonksiyonların bileşkesinde (f o g)(x) ifadesinin tanım kümesini belirleyiniz."
        )
        assertNull(choose(listOf(unrelated)))
        assertNull(choose(listOf(record("missing_outcome", learningOutcome = ""))))
        assertNull(choose(listOf(record("wrong_question_type").copy(questionType = "Fonksiyon grafiği çizimi"))))
    }
}
