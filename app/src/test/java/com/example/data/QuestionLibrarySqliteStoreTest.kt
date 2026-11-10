package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class QuestionLibrarySqliteStoreTest {
    @Test fun thousandsOfQuestionsStayQueryableAcrossReopenAndReplace() {
        val ctx = RuntimeEnvironment.getApplication()
        val uid = "large_library_test"
        ctx.deleteDatabase("sorulab_library_${uid}.db")
        try {
            val index = QuestionLibrarySqliteStore(ctx, uid)
            assertNull(index.read()) // Existing JSON still controls before migration.
            val questions = (1..1500).map { number ->
                CustomQuestionRecord(
                    id = "q_$number",
                    userId = uid,
                    bookId = "book_${number / 500}",
                    topicId = "topic_1",
                    questionText = "Soru $number",
                    difficulty = "Orta",
                    correctOption = "C",
                    aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta(
                        solutionText = "Cevap C",
                        answerKeyVerified = true,
                        fiveTierDifficulty = "ÖSYM"
                    )
                )
            }
            index.save(questions, replace = false)
            val reopened = QuestionLibrarySqliteStore(ctx, uid)
            assertEquals(1500, reopened.count())
            assertEquals(1500, reopened.read()?.size)
            assertNotNull(reopened.read()?.firstOrNull { it.id == "q_1300" })
            assertEquals(DifficultyLevel.OSYM,
                reopened.read()?.firstOrNull { it.id == "q_1300" }?.difficultyLevel5)

            reopened.save(listOf(questions.first()), replace = true)
            assertEquals(1, reopened.count())
            reopened.save(emptyList(), replace = true)
            assertEquals(0, reopened.count())
            assertFalse(reopened.read() == null) // Empty is authoritative: no resurrection.
        } finally {
            ctx.deleteDatabase("sorulab_library_${uid}.db")
        }
    }
}
