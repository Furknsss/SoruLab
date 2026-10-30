package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TestSnapshotCodecTest {
    @Test
    fun roundTrip_preservesTestAndQuestionContent() {
        val test = YksTestItem(
            id = "topic_osym_test_1",
            topicId = "topic",
            title = "Konu - Test 1",
            fullTitle = "Konu - Test 1",
            difficulty = DifficultyLevel.ZOR,
            difficultyPosition = 0.78f,
            level = TopicTestLevel.OSYM,
            questions = listOf(
                YksQuestion(
                    id = "question_1",
                    number = 1,
                    badgeTitle = "BAŞLIK",
                    topicHeader = "KONU",
                    publisherStamp = "YAYIN",
                    publisherName = "Yayın Adı",
                    publisherLogoUri = "/logo.png",
                    croppedImagePath = "/question.png",
                    answerKeyVerified = false,
                    stemTop = "Soru metni",
                    bulletsOrSteps = listOf("Adım 1", "Adım 2"),
                    stemBottom = "Alt metin",
                    options = mapOf("A" to "Bir", "B" to "İki"),
                    correctOption = "B",
                    difficulty = DifficultyLevel.ORTA,
                    visualType = QuestionVisualType.NONE,
                    stepByStepSolution = "Çözüm metni"
                )
            )
        )

        assertEquals(test, TestSnapshotCodec.decode(TestSnapshotCodec.encode(test)))
    }

    @Test
    fun decode_returnsNullForInvalidSnapshot() {
        assertNull(TestSnapshotCodec.decode("not-json"))
        assertNull(TestSnapshotCodec.decode("{}"))
    }

    @Test
    fun accuracyAndQuestionTotal_excludeUngradedQuestionsFromAccuracy() {
        val result = TestResultRecord(
            correctCount = 2,
            wrongCount = 1,
            emptyCount = 1,
            ungradedCount = 2
        )

        assertEquals(6, result.totalQuestions)
        assertEquals(50, result.accuracyPercent)
    }
}
