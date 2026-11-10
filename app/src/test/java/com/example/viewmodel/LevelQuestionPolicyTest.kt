package com.example.viewmodel

import com.example.data.DifficultyLevel
import com.example.data.TopicTestLevel
import com.example.data.YksQuestion
import org.junit.Assert.*
import org.junit.Test

class LevelQuestionPolicyTest {
    private fun question(diff: DifficultyLevel, n: Int): YksQuestion = YksQuestion(
        id = diff.name + "_" + n,
        number = n,
        sourceQuestionId = diff.name + "_" + n,
        topicHeader = "Fonksiyonlar",
        stemTop = "Fonksiyonların tanım kümesini bulun",
        stemBottom = "Kaçtır?",
        options = mapOf("A" to "1", "B" to "2", "C" to "3", "D" to "4", "E" to "5"),
        correctOption = "B",
        difficulty = diff,
        stepByStepSolution = "Kaynak cevap anahtarı."
    )

    private fun pool() = DifficultyLevel.entries.flatMap { d ->
        (1..20).map { question(d, it) }
    }

    private fun distribution(level: TopicTestLevel, count: Int = 12): Map<DifficultyLevel, Int> =
        LevelQuestionPolicy.select(level, pool(), count).groupingBy { it.difficulty }.eachCount()

    @Test fun wheelHasOnlySixLevelsInRequiredOrder() {
        assertEquals(
            listOf("SEVIYE_TESPIT", "BASLANGIC", "IKIZ", "PEKISTIRME", "OSYM", "DERECE"),
            TopicTestLevel.entries.map { it.name }
        )
    }

    @Test fun diagnosticBalancesAllFiveDifficultyBands() {
        val distribution = distribution(TopicTestLevel.SEVIYE_TESPIT)
        assertEquals(12, distribution.values.sum())
        assertEquals(5, distribution.size)
        assertTrue(distribution.values.max() - distribution.values.min() <= 1)
    }

    @Test fun diagnosticUsesAvailableLegacyBandsWithoutInventingMissingBand() {
        val missingVeryHard = pool().filter { it.difficulty != DifficultyLevel.COK_ZOR }
        val chosen = LevelQuestionPolicy.select(TopicTestLevel.SEVIYE_TESPIT, missingVeryHard, 12)
        assertEquals(12, chosen.size)
        assertEquals(4, chosen.map { it.difficulty }.distinct().size)
        // Partial diagnostic is useful practice but MUST NOT issue a placement.
        assertNull(LevelPlacementAdvisor.evaluate(chosen, chosen.associate { it.number to "B" }))
        assertEquals(4, LevelQuestionPolicy.select(TopicTestLevel.SEVIYE_TESPIT, pool(), 4).size)
    }

    @Test fun primaryOnlyBooksStillBuildFullTwelveQuestionTests() {
        val easyOnly = (1..20).map { question(DifficultyLevel.KOLAY, it) }
        val selected = LevelQuestionPolicy.select(TopicTestLevel.BASLANGIC, easyOnly, 12)
        assertEquals(12, selected.size)
        assertTrue(selected.all { it.difficulty == DifficultyLevel.KOLAY })
        assertEquals(12, LevelQuestionPolicy.maximumConstructibleCount(TopicTestLevel.BASLANGIC, easyOnly))
        val mediumOnly = (1..20).map { question(DifficultyLevel.ORTA, it) }
        assertEquals(12, LevelQuestionPolicy.maximumConstructibleCount(TopicTestLevel.PEKISTIRME, mediumOnly))
        val hardOnly = (1..20).map { question(DifficultyLevel.ZOR, it) }
        assertEquals(12, LevelQuestionPolicy.maximumConstructibleCount(TopicTestLevel.DERECE, hardOnly))
    }

    @Test fun beginningContainsTenEasyAndAtMostTwoMedium() {
        assertEquals(
            mapOf(DifficultyLevel.KOLAY to 10, DifficultyLevel.ORTA to 2),
            distribution(TopicTestLevel.BASLANGIC)
        )
    }

    @Test fun reinforcementIsPredominantlyMedium() {
        assertEquals(
            mapOf(DifficultyLevel.ORTA to 10, DifficultyLevel.KOLAY to 1, DifficultyLevel.ZOR to 1),
            distribution(TopicTestLevel.PEKISTIRME)
        )
    }

    @Test fun osymContainsOnlyOsymTaggedQuestions() {
        assertEquals(mapOf(DifficultyLevel.OSYM to 12), distribution(TopicTestLevel.OSYM))
    }

    @Test fun dereceIsPredominantlyHardNotOsym() {
        assertEquals(
            mapOf(DifficultyLevel.ZOR to 10, DifficultyLevel.COK_ZOR to 2),
            distribution(TopicTestLevel.DERECE)
        )
    }

    @Test fun doesNotFillUnavailableEasyLevelWithUnrelatedDifficulty() {
        val mostlyMedium = (1..2).map { question(DifficultyLevel.KOLAY, it) } +
            (1..30).map { question(DifficultyLevel.ORTA, it) }
        val selected = LevelQuestionPolicy.select(TopicTestLevel.BASLANGIC, mostlyMedium, 12)
        assertEquals(2, selected.count { it.difficulty == DifficultyLevel.KOLAY })
        assertTrue(selected.count { it.difficulty == DifficultyLevel.ORTA } <= 2)
    }

    @Test fun twinOnlyReturnsProvidedIncorrectSourceQuestions() {
        val wrong = listOf(question(DifficultyLevel.OSYM, 1), question(DifficultyLevel.ZOR, 1))
        assertEquals(wrong.map { it.id },
            LevelQuestionPolicy.select(TopicTestLevel.IKIZ, wrong, 12).map { it.id })
    }


    @Test fun scarceCategoryKeepsDiagnosticBalancedInsteadOfPaddingWithOtherBands() {
        val pool = DifficultyLevel.entries.flatMap { level ->
            (1..if (level == DifficultyLevel.COK_ZOR) 1 else 20).map { question(level, it) }
        }
        val chosen = LevelQuestionPolicy.select(TopicTestLevel.SEVIYE_TESPIT, pool, 12)
        val counts = chosen.groupingBy { it.difficulty }.eachCount()
        assertEquals(9, chosen.size)
        assertEquals(5, counts.size)
        assertTrue(counts.values.max() - counts.values.min() <= 1)
        assertEquals(9, LevelQuestionPolicy.maximumConstructibleCount(TopicTestLevel.SEVIYE_TESPIT, pool))
    }

    @Test fun dominantBandMustHaveStrictMajority() {
        val shortage = listOf(question(DifficultyLevel.KOLAY, 1)) +
            (1..20).map { question(DifficultyLevel.ORTA, it) }
        val chosen = LevelQuestionPolicy.select(TopicTestLevel.BASLANGIC, shortage, 12)
        assertTrue(chosen.count { it.difficulty == DifficultyLevel.KOLAY } > chosen.size / 2)
        assertEquals(1, LevelQuestionPolicy.maximumConstructibleCount(TopicTestLevel.BASLANGIC, shortage))
    }

    @Test fun placementRecommendsNextReasonableBand() {
        val questions = DifficultyLevel.entries.mapIndexed { index, d -> question(d, index + 1) }
        val correctAll = questions.associate { it.number to "B" }
        assertEquals(TopicTestLevel.DERECE,
            LevelPlacementAdvisor.evaluate(questions, correctAll)?.recommended)
        val weakEasy = correctAll + (questions.first().number to "A")
        assertEquals(TopicTestLevel.BASLANGIC,
            LevelPlacementAdvisor.evaluate(questions, weakEasy)?.recommended)
        val weakMedium = correctAll + (questions[1].number to "A")
        assertEquals(TopicTestLevel.PEKISTIRME,
            LevelPlacementAdvisor.evaluate(questions, weakMedium)?.recommended)
        val weakOsym = correctAll + (questions[2].number to "A")
        assertEquals(TopicTestLevel.OSYM,
            LevelPlacementAdvisor.evaluate(questions, weakOsym)?.recommended)
    }
}
