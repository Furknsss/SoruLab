package com.example.data

import org.junit.Assert.*
import org.junit.Test

class BookTopicIsolationTest {
    private fun question(
        id: String, topicId: String, topicName: String, confirmed: Boolean = true
    ) = CustomQuestionRecord(
        id = id, subjectId = "ayt_biyoloji", subjectName = "Biyoloji",
        topicId = topicId, topicName = topicName,
        questionText = "Gerçek kitap sorusu",
        difficulty = "Zor", correctOption = "B",
        aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta(
            solutionText = "B seçeneği", fiveTierDifficulty = "Zor",
            answerKeyVerified = confirmed
        ),
        sourceBookTitle = "AYT Biyoloji 2026"
    )

    @Test fun oldZipsWithoutSourceTestIdsNeverMixEndocrineAndPlantQuestions() {
        val questions = listOf(
            question("end_1", "endokrin_sistem", "Endokrin Sistem"),
            question("end_2", "endokrin_sistem", "Endokrin Sistem"),
            question("plant_1", "bitki_biyolojisi", "Bitki Biyolojisi"),
            question("plant_2", "bitki_biyolojisi", "Bitki Biyolojisi")
        )
        val chunks = automaticBookTestChunks(questions, TopicTestLevel.DERECE)
        assertEquals(2, chunks.size)
        assertEquals(listOf("end_1", "end_2"), chunks[0].map { it.id })
        assertEquals(listOf("plant_1", "plant_2"), chunks[1].map { it.id })
        assertTrue(chunks.all { chunk -> chunk.map { it.topicId }.distinct().size == 1 })
    }

    @Test fun bookShortcutsNeverOfferUnverifiedAnswerKeysOrArtificialTwins() {
        val source = listOf(
            question("verified", "endokrin_sistem", "Endokrin Sistem"),
            question("not_verified", "endokrin_sistem", "Endokrin Sistem", false)
        )
        val chunks = automaticBookTestChunks(source, TopicTestLevel.DERECE)
        assertEquals(listOf("verified"), chunks.flatten().map { it.id })
        assertTrue(automaticBookTestChunks(source, TopicTestLevel.IKIZ).isEmpty())
    }
}
