package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSourceTestGroupingTest {
    private fun question(number: Int, scope: String): CustomQuestionRecord =
        CustomQuestionRecord(
            id = "q_${scope}_${number}",
            difficulty = "Orta",
            aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta(
                solutionText = "Test answer explanation",
                questionNumberInBook = number,
                sourceTestId = scope
            ),
            questionNumberInTest = number
        )

    @Test
    fun `a ten-question source test must not borrow questions from the next test`() {
        val input = (1..10).map { question(it, "sinir sistemi~normal~1") } +
            (1..2).map { question(it, "sinir sistemi~normal~2") }
        val chunks = automaticBookTestChunks(input, TopicTestLevel.OSYM)
        assertEquals(listOf(10, 2), chunks.map { it.size })
        assertEquals(1, chunks[0][0].questionNumberInTest)
        assertEquals(1, chunks[1][0].questionNumberInTest)
        assertTrue(chunks[0].all { it.sourceTestId == "sinir sistemi~normal~1" })
        assertTrue(chunks[1].all { it.sourceTestId == "sinir sistemi~normal~2" })
    }

    @Test
    fun `printed OSYM test is not mixed with ordinary test of same number`() {
        val input = listOf(
            question(1, "sinir sistemi~normal~6"),
            question(1, "sinir sistemi~osym~6")
        )
        assertEquals(listOf(1, 1),
            automaticBookTestChunks(input, TopicTestLevel.OSYM).map { it.size })
    }

    @Test
    fun `older imports without source-test metadata still build twelve-question chunks`() {
        val legacy = (1..13).map { question(it, "") }
        assertEquals(listOf(12, 1),
            automaticBookTestChunks(legacy, TopicTestLevel.OSYM).map { it.size })
    }

    @Test
    fun `repacking solution metadata preserves source-test identity`() {
        val initial = question(4, "sinir sistemi~normal~1")
        val repacked = initial.copy(aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta(
            solutionText = initial.cleanSolutionSummary,
            questionNumberInBook = initial.questionNumberInBook,
            sourceTestId = initial.sourceTestId,
            isInKumbara = true
        ))
        assertEquals(initial.sourceTestId, repacked.sourceTestId)
        assertTrue(repacked.isInKumbara)
    }

    @Test
    fun sourceScopeCannotMixPrintedDuyuOrganlariWithSinirSistemi() {
        val records = listOf(
            question(10, "printed~analiz~21").copy(topicName = "Duyu Organları"),
            question(1, "printed~analiz~21").copy(topicName = "Sinir Sistemi")
        )
        val chunks = automaticBookTestChunks(records, TopicTestLevel.OSYM)
        assertEquals(listOf(1, 1), chunks.map { it.size })
        assertEquals(listOf("Duyu Organları", "Sinir Sistemi"),
            chunks.map { it.first().topicName })
    }

    @Test
    fun unidentifiedPdfPagesNeverMixEvenWhenTopicIsSame() {
        val records = listOf(
            question(1, "").copy(pageIndex = 9, topicName = "Sinir Sistemi"),
            question(2, "").copy(pageIndex = 9, topicName = "Sinir Sistemi"),
            question(1, "").copy(pageIndex = 49, topicName = "Sinir Sistemi")
        )
        assertEquals(listOf(2, 1),
            automaticBookTestChunks(records, TopicTestLevel.OSYM).map { it.size })
    }

    @Test
    fun printedNumberRestartSplitsAnIncorrectlyReusedScope() {
        val records = listOf(
            question(8, "sinir sistemi~analiz~2").copy(topicName = "Sinir Sistemi"),
            question(1, "sinir sistemi~analiz~2").copy(topicName = "Sinir Sistemi")
        )
        assertEquals(listOf(1, 1),
            automaticBookTestChunks(records, TopicTestLevel.OSYM).map { it.size })
    }

}
