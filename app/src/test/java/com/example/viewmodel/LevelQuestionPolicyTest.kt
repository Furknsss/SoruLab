package com.example.viewmodel

import com.example.data.DifficultyLevel
import com.example.data.QUESTION_DIFFICULTY_BANDS
import com.example.data.TopicTestLevel
import com.example.data.YksQuestion
import com.example.data.fourTier
import org.junit.Assert.*
import org.junit.Test

class LevelQuestionPolicyTest {
    private fun question(
        difficulty: DifficultyLevel, n: Int, osymType: Boolean = false,
        publisher: String = "Dr. Biyoloji"
    ): YksQuestion = YksQuestion(
        id = publisher + "_" + difficulty.name + "_" + n,
        number = n,
        sourceQuestionId = publisher + "_" + difficulty.name + "_" + n,
        publisherName = publisher,
        topicHeader = "Sinir Sistemi",
        stemTop = "Gerçek soru",
        stemBottom = "Kaçtır?",
        options = mapOf("A" to "1", "B" to "2", "C" to "3", "D" to "4", "E" to "5"),
        correctOption = "B",
        difficulty = difficulty,
        isOsymType = osymType,
        stepByStepSolution = "Kitabın doğrulanmış cevabı."
    )

    private fun pool(): List<YksQuestion> = QUESTION_DIFFICULTY_BANDS.flatMap { diff ->
        (1..20).map { question(diff, it, osymType = it % 3 == 0) }
    }
    private fun distribution(level: TopicTestLevel): Map<DifficultyLevel, Int> =
        LevelQuestionPolicy.select(level, pool(), 12)
            .groupingBy { it.difficulty.fourTier() }.eachCount()

    @Test fun wheelHasSixUserFacingStages() {
        assertEquals(
            listOf("SEVIYE_TESPIT", "BASLANGIC", "IKIZ", "PEKISTIRME", "OSYM", "DERECE"),
            TopicTestLevel.entries.map { it.name }
        )
        assertEquals(4, QUESTION_DIFFICULTY_BANDS.size)
        assertFalse(QUESTION_DIFFICULTY_BANDS.contains(DifficultyLevel.OSYM))
    }

    @Test fun diagnosticMixesFourAndOnlyFourDifficultyBands() {
        val selected = LevelQuestionPolicy.select(TopicTestLevel.SEVIYE_TESPIT, pool(), 12)
        val counts = selected.groupingBy { it.difficulty.fourTier() }.eachCount()
        assertEquals(12, selected.size)
        assertEquals(4, counts.size)
        assertEquals(3, counts.values.min())
        assertEquals(3, counts.values.max())
        // Style is intentionally independent; the diagnostic is balanced by
        // difficulty and may legitimately include any number of ÖSYM-type tags.
    }

    @Test fun incompleteDiagnosticNeverCreatesFabricatedPlacement() {
        val noVeryHard = pool().filter { it.difficulty != DifficultyLevel.COK_ZOR }
        val selected = LevelQuestionPolicy.select(TopicTestLevel.SEVIYE_TESPIT, noVeryHard, 12)
        assertTrue(selected.isNotEmpty())
        assertNull(LevelPlacementAdvisor.evaluate(selected, selected.associate { it.number to "B" }))
    }

    @Test fun scarceEasyBandNeverBlocksOtherRealDiagnosticQuestions() {
        val scarce = listOf(question(DifficultyLevel.KOLAY, 1)) +
            (1..20).flatMap { n ->
                listOf(
                    question(DifficultyLevel.ORTA, n),
                    question(DifficultyLevel.ZOR, n),
                    question(DifficultyLevel.COK_ZOR, n)
                )
            }
        val selected = LevelQuestionPolicy.select(TopicTestLevel.SEVIYE_TESPIT, scarce, 12)
        assertEquals(12, selected.size)
        assertEquals(1, selected.count { it.difficulty == DifficultyLevel.KOLAY })
        assertEquals(12, LevelQuestionPolicy.maximumConstructibleCount(
            TopicTestLevel.SEVIYE_TESPIT, scarce
        ))
    }

    @Test fun osymStyleDoesNotCreateArtificialFifthDifficulty() {
        val tagged = QUESTION_DIFFICULTY_BANDS.mapIndexed { index, difficulty ->
            question(difficulty, index + 1, osymType = true)
        }
        assertEquals(4, tagged.map { it.difficulty.fourTier() }.distinct().size)
        assertTrue(LevelQuestionPolicy.select(TopicTestLevel.OSYM, tagged, 12).all {
            it.isOsymType
        })
        assertTrue(LevelQuestionPolicy.select(TopicTestLevel.DERECE, tagged, 12).all {
            it.difficulty in setOf(DifficultyLevel.ZOR, DifficultyLevel.COK_ZOR)
        })
    }

    @Test fun beginningUsesOnlyEasyEvenIfOtherDifficultiesArePlentiful() {
        val result = LevelQuestionPolicy.select(TopicTestLevel.BASLANGIC, pool(), 12)
        assertEquals(12, result.size)
        assertTrue(result.all { it.difficulty == DifficultyLevel.KOLAY })
        assertEquals(
            0, LevelQuestionPolicy.maximumConstructibleCount(
                TopicTestLevel.BASLANGIC,
                (1..50).map { question(DifficultyLevel.ORTA, it) }
            )
        )
    }

    @Test fun reinforcementIsMediumMajorityWithSmallOptionalEasyAndHardMix() {
        val result = distribution(TopicTestLevel.PEKISTIRME)
        assertEquals(10, result[DifficultyLevel.ORTA])
        assertEquals(1, result[DifficultyLevel.KOLAY])
        assertEquals(1, result[DifficultyLevel.ZOR])
        val mediumOnly = (1..20).map { question(DifficultyLevel.ORTA, it) }
        assertEquals(12, LevelQuestionPolicy.maximumConstructibleCount(
            TopicTestLevel.PEKISTIRME, mediumOnly
        ))
        val insufficientMedium = listOf(question(DifficultyLevel.ORTA, 1)) +
            (1..20).map { question(DifficultyLevel.KOLAY, it) }
        assertEquals(1, LevelQuestionPolicy.maximumConstructibleCount(
            TopicTestLevel.PEKISTIRME, insufficientMedium
        ))
    }

    @Test fun osymStageIgnoresDifficultyButRequiresExplicitStyleMarker() {
        val source = listOf(
            question(DifficultyLevel.KOLAY, 1, true),
            question(DifficultyLevel.ORTA, 1, true),
            question(DifficultyLevel.ZOR, 1, true),
            question(DifficultyLevel.COK_ZOR, 1, true),
            question(DifficultyLevel.ZOR, 2, false),
            question(DifficultyLevel.ORTA, 2, false)
        )
        val picked = LevelQuestionPolicy.select(TopicTestLevel.OSYM, source, 12)
        assertEquals(4, picked.size)
        assertTrue(picked.all { it.isOsymType })
        assertEquals(4, picked.map { it.difficulty }.distinct().size)
        assertEquals(0, LevelQuestionPolicy.maximumConstructibleCount(
            TopicTestLevel.OSYM, source.filter { !it.isOsymType }
        ))
    }

    @Test fun degreeUsesOnlyHardAndVeryHardIncludingWhenOneIsMissing() {
        val selected = LevelQuestionPolicy.select(TopicTestLevel.DERECE, pool(), 12)
        assertEquals(12, selected.size)
        assertEquals(10, selected.count { it.difficulty == DifficultyLevel.ZOR })
        assertEquals(2, selected.count { it.difficulty == DifficultyLevel.COK_ZOR })
        val onlyVeryHard = (1..20).map { question(DifficultyLevel.COK_ZOR, it) }
        assertEquals(12, LevelQuestionPolicy.maximumConstructibleCount(
            TopicTestLevel.DERECE, onlyVeryHard
        ))
        assertTrue(LevelQuestionPolicy.select(
            TopicTestLevel.DERECE, onlyVeryHard, 12
        ).all { it.difficulty == DifficultyLevel.COK_ZOR })
    }

    @Test fun twoDifferentPublishersStaySeparateWhenOneIsFiltered() {
        val source = pool() + (1..30).map {
            question(DifficultyLevel.ORTA, it, true, publisher = "Biyotik")
        }
        val dr = PublisherQuestionMixer.filter(source, "drbiyoloji")
        val selected = LevelQuestionPolicy.select(TopicTestLevel.OSYM, dr, 12)
        assertEquals(12, selected.size)
        assertTrue(selected.all { it.isOsymType && it.publisherName == "Dr. Biyoloji" })
    }

    @Test fun legacyOsymDifficultyIsAcceptedAsStyleButNormalizedToMedium() {
        val legacy = question(DifficultyLevel.OSYM, 1)
        val chosen = LevelQuestionPolicy.select(TopicTestLevel.OSYM, listOf(legacy), 12)
        assertEquals(listOf(legacy), chosen)
        assertEquals(DifficultyLevel.ORTA, legacy.difficulty.fourTier())
    }

    @Test fun twinsNeverInventExtraQuestionsOrFilterOutWrongDifficulties() {
        val wrong = listOf(question(DifficultyLevel.KOLAY, 1),
            question(DifficultyLevel.COK_ZOR, 1))
        assertEquals(wrong.map { it.id }, LevelQuestionPolicy.select(
            TopicTestLevel.IKIZ, wrong, 12
        ).map { it.id })
    }

    @Test fun placementUsesFourDifficultyBandsAndOptionalOsymStylePerformance() {
        val questions = QUESTION_DIFFICULTY_BANDS.mapIndexed { index, diff ->
            question(diff, index + 1, osymType = diff == DifficultyLevel.ZOR)
        }
        val allCorrect = questions.associate { it.number to "B" }
        assertEquals(TopicTestLevel.DERECE,
            LevelPlacementAdvisor.evaluate(questions, allCorrect)?.recommended)
        assertEquals(TopicTestLevel.BASLANGIC,
            LevelPlacementAdvisor.evaluate(questions, allCorrect + (1 to "A"))?.recommended)
        assertEquals(TopicTestLevel.PEKISTIRME,
            LevelPlacementAdvisor.evaluate(questions, allCorrect + (2 to "A"))?.recommended)
        assertEquals(TopicTestLevel.OSYM,
            LevelPlacementAdvisor.evaluate(questions, allCorrect + (3 to "A"))?.recommended)
    }
}
