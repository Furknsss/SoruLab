package com.example.viewmodel

import com.example.data.DifficultyLevel
import com.example.data.QUESTION_DIFFICULTY_BANDS
import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionDifficultyFallbackTest {
    @Test fun selectedDifficultyComesFirstAndFourBandsFollow() {
        assertEquals(
            listOf(
                DifficultyLevel.ZOR, DifficultyLevel.COK_ZOR,
                DifficultyLevel.ORTA, DifficultyLevel.KOLAY
            ),
            difficultyFallbackOrder(setOf(DifficultyLevel.ZOR))
        )
    }

    @Test fun legacyOsymDifficultyIsNotAVisibleFifthChoice() {
        assertEquals(
            listOf(
                DifficultyLevel.ORTA, DifficultyLevel.KOLAY,
                DifficultyLevel.ZOR, DifficultyLevel.COK_ZOR
            ),
            difficultyFallbackOrder(setOf(DifficultyLevel.OSYM))
        )
    }

    @Test fun allSelectedDifficultiesDoNotAddOrDropLevels() {
        assertEquals(QUESTION_DIFFICULTY_BANDS,
            difficultyFallbackOrder(QUESTION_DIFFICULTY_BANDS.toSet()))
    }

    @Test fun emptySelectionSafelyUsesEveryRealDifficulty() {
        assertEquals(QUESTION_DIFFICULTY_BANDS, difficultyFallbackOrder(emptySet()))
    }
}
