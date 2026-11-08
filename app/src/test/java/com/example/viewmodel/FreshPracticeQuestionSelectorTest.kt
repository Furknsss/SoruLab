package com.example.viewmodel

import com.example.data.YksQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshPracticeQuestionSelectorTest {
    private fun q(id: String, book: String = "A") = YksQuestion(
        id = id, number = 1, stemTop = "Soru $id", stemBottom = "",
        options = mapOf("A" to "1", "B" to "2", "C" to "3", "D" to "4", "E" to "5"),
        correctOption = "A", stepByStepSolution = "",
        sourceQuestionId = id, croppedImagePath = "/test/$id.png",
        publisherName = book, answerKeyVerified = true
    )

    @Test fun neverRepeatsCurrentTestWhenFreshAlternativesExist() {
        val picked = FreshPracticeQuestionSelector.choose(
            candidates = listOf(q("old"), q("new-a"), q("new-b")),
            generatedBefore = emptyList(), solvedBefore = emptyList(),
            justSeen = listOf(q("old")), requestedCount = 2
        )
        assertEquals(setOf("new-a", "new-b"), picked.map { it.sourceQuestionId }.toSet())
    }

    @Test fun prioritizesUnsolvedAndNeverGeneratedQuestions() {
        val selected = FreshPracticeQuestionSelector.choose(
            candidates = listOf(q("solved"), q("shown"), q("fresh")),
            generatedBefore = listOf(q("solved"), q("shown")),
            solvedBefore = listOf(q("solved")),
            justSeen = emptyList(), requestedCount = 3
        )
        assertEquals(listOf("fresh", "shown", "solved"), selected.map { it.sourceQuestionId })
    }

    @Test fun repeatedStrengthenCallsRotateThroughPoolBeforeReuse() {
        val pool = (1..15).map { q("q$it") }
        val first = FreshPracticeQuestionSelector.choose(
            pool, emptyList(), emptyList(), emptyList(), 10
        )
        val second = FreshPracticeQuestionSelector.choose(
            pool, first, emptyList(), emptyList(), 10
        )
        assertEquals(10, first.size)
        assertEquals(10, second.size)
        assertTrue(second.take(5).none { it.sourceQuestionId in first.map { q -> q.sourceQuestionId } })
    }

    @Test fun neverTreatsUnknownKeyAsVerified() {
        val invalid = q("bad").copy(answerKeyVerified = false)
        val choice = FreshPracticeQuestionSelector.choose(
            listOf(invalid, q("good")), emptyList(), emptyList(), emptyList(), 2
        )
        assertEquals(listOf("good"), choice.map { it.sourceQuestionId })
    }

    @Test fun insufficientPoolCreatesSmallerTestWithoutDuplicatingQuestions() {
        val result = FreshPracticeQuestionSelector.choose(
            listOf(q("a"), q("a"), q("b")), emptyList(), emptyList(),
            emptyList(), requestedCount = 12
        )
        assertEquals(2, result.size)
    }
}
