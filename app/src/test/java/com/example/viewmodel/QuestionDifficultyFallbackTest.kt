package com.example.viewmodel

import com.example.data.DifficultyLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionDifficultyFallbackTest {
    @Test
    fun selectedDifficultyComesFirstAndClosestLevelsFollow() {
        assertEquals(
            listOf(
                DifficultyLevel.OSYM,
                DifficultyLevel.ORTA,
                DifficultyLevel.ZOR,
                DifficultyLevel.KOLAY,
                DifficultyLevel.COK_ZOR
            ),
            difficultyFallbackOrder(setOf(DifficultyLevel.OSYM))
        )
    }

    @Test
    fun allSelectedDifficultiesDoNotAddOrDropLevels() {
        assertEquals(
            DifficultyLevel.entries,
            difficultyFallbackOrder(DifficultyLevel.entries.toSet())
        )
    }

    @Test
    fun emptySelectionSafelyUsesEveryDifficulty() {
        assertEquals(
            DifficultyLevel.entries,
            difficultyFallbackOrder(emptySet())
        )
    }
}
