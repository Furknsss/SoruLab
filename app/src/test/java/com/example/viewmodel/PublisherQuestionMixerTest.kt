package com.example.viewmodel

import com.example.data.DifficultyLevel
import com.example.data.QUESTION_DIFFICULTY_BANDS
import com.example.data.TopicTestLevel
import com.example.data.YksQuestion
import org.junit.Assert.*
import org.junit.Test

class PublisherQuestionMixerTest {
    private fun question(
        publisher: String,
        difficulty: DifficultyLevel = DifficultyLevel.OSYM,
        number: Int
    ): YksQuestion = YksQuestion(
        id = publisher + "_" + difficulty.name + "_" + number,
        sourceQuestionId = publisher + "_" + difficulty.name + "_" + number,
        number = number,
        publisherName = publisher,
        publisherStamp = publisher,
        topicHeader = "Fonksiyonlar",
        stemTop = "Test sorusu",
        stemBottom = "Kaçtır?",
        options = mapOf("A" to "1", "B" to "2", "C" to "3", "D" to "4", "E" to "5"),
        correctOption = "B",
        difficulty = difficulty,
        stepByStepSolution = "Doğrulanmış cevap açıklaması"
    )

    private fun pool(count: Int = 30): List<YksQuestion> =
        listOf("3D Yayınları", "Biyotik", "Orijinal").flatMap { publisher ->
            (1..count).map { question(publisher, number = it) }
        }

    @Test fun drBiyolojiVariantsAreTheSamePublisherAndNeverIncludeBiyotik() {
        val source = listOf(
            question("Dr. Biyoloji", number = 1),
            question("Dr Biyoloji Yayınları", number = 2),
            question("DRBIYOLOJI", number = 3),
            question("Biyotik", number = 4),
            question("Biyoloji", number = 5)
        )
        val canonicalKey = PublisherQuestionMixer.key(source.first())
        assertEquals("drbiyoloji", canonicalKey)
        val chosen = PublisherQuestionMixer.filter(source, canonicalKey)
        assertEquals(3, chosen.size)
        assertEquals(listOf(1, 2, 3), chosen.map { it.number })
        val options = PublisherQuestionMixer.options(source)
        assertEquals(3, options.size)
        assertEquals(3, options.single { it.key == "drbiyoloji" }.availableQuestions)
    }

    @Test fun onePublisherFilterIsStrict() {
        val pool = pool(12)
        val selected = PublisherQuestionMixer.options(pool)
            .single { it.title == "Biyotik" }
        val filtered = PublisherQuestionMixer.filter(pool, selected.key)
        assertEquals(12, filtered.size)
        assertTrue(filtered.all { it.publisherName == "Biyotik" })
        assertEquals(36, PublisherQuestionMixer.filter(pool, null).size)
        assertTrue(PublisherQuestionMixer.filter(pool, "missing-publisher").isEmpty())
    }

    @Test fun canonicalPublisherNameAvoidsSeparate3DYayinlariOption() {
        val a = question("3D Yayınları", number = 1)
        val b = question("3D", number = 2)
        assertEquals(PublisherQuestionMixer.key(a), PublisherQuestionMixer.key(b))
        val options = PublisherQuestionMixer.options(listOf(a, b))
        assertEquals(1, options.size)
        assertEquals(2, options.first().availableQuestions)
    }

    @Test fun mixedTwelveQuestionsAreFairAcrossThreePublishers() {
        val selected = LevelQuestionPolicy.select(
            level = TopicTestLevel.OSYM,
            sourceQuestions = pool(),
            count = 12,
            mixSeed = 12456
        )
        assertEquals(12, selected.size)
        assertEquals(3, selected.map { PublisherQuestionMixer.key(it) }.distinct().size)
        val counts = selected.groupingBy { PublisherQuestionMixer.key(it) }.eachCount()
        assertTrue(counts.values.max() - counts.values.min() <= 1)
    }

    @Test fun differentTestNumbersProduceDifferentNaturalQuestionOrders() {
        val selectedA = LevelQuestionPolicy.select(
            TopicTestLevel.OSYM, pool(), 12, mixSeed = 112
        )
        val selectedB = LevelQuestionPolicy.select(
            TopicTestLevel.OSYM, pool(), 12, mixSeed = 113
        )
        assertNotEquals(selectedA.map { it.id }, selectedB.map { it.id })
        assertNotEquals(
            selectedA.map { PublisherQuestionMixer.key(it) },
            selectedB.map { PublisherQuestionMixer.key(it) }
        )
    }

    @Test fun naturalMixAvoidsThreeIdenticalPublisherQuestionsTogether() {
        for (seed in 1..18) {
            val selected = LevelQuestionPolicy.select(
                TopicTestLevel.OSYM, pool(), 12, mixSeed = seed
            )
            val runLengthThree = selected.windowed(3).any { window ->
                window.map { PublisherQuestionMixer.key(it) }.distinct().size == 1
            }
            assertFalse("Three consecutive questions from one publisher (seed=$seed)", runLengthThree)
        }
    }

    @Test fun scarcePublisherIsIncludedButNotForcedBeyondItsInventory() {
        val source = listOf(question("3D", number = 1), question("3D", number = 2)) +
            (1..30).flatMap { n ->
                listOf(question("Biyotik", number = n), question("Orijinal", number = n))
            }
        val selected = LevelQuestionPolicy.select(
            TopicTestLevel.OSYM, source, 12, mixSeed = 18
        )
        val counts = selected.groupingBy { PublisherQuestionMixer.key(it) }.eachCount()
        assertEquals(2, counts["3d"])
        assertEquals(5, counts["biyotik"])
        assertEquals(5, counts["orijinal"])
    }

    @Test fun unusedQuestionsAreChosenBeforeRepeatingPreviouslyGeneratedItems() {
        val source = pool(12)
        val used = source.filter { it.number <= 8 }
            .mapTo(mutableSetOf()) { LevelQuestionPolicy.stableId(it) }
        val selected = LevelQuestionPolicy.select(
            TopicTestLevel.OSYM, source, 12, previouslyUsed = used, mixSeed = 75
        )
        assertTrue(selected.none { LevelQuestionPolicy.stableId(it) in used })
    }

    @Test fun publisherMixKeepsDiagnosticDifficultyBalance() {
        val source = QUESTION_DIFFICULTY_BANDS.flatMap { difficulty ->
            listOf("3D", "Biyotik", "Orijinal").flatMap { publisher ->
                (1..10).map { question(publisher, difficulty, it) }
            }
        }
        val selected = LevelQuestionPolicy.select(
            TopicTestLevel.SEVIYE_TESPIT, source, 12, mixSeed = 182
        )
        assertEquals(12, selected.size)
        val counts = selected.groupingBy { it.difficulty }.eachCount()
        assertEquals(4, counts.size)
        assertTrue(counts.values.max() - counts.values.min() <= 1)
    }

    @Test fun selectedPublisherStillObeysOsymOnlyAndDegreeDifficultyRules() {
        val source = DifficultyLevel.entries.flatMap { difficulty ->
            (1..10).map { question("3D", difficulty, it) }
        } + (1..40).map { question("Biyotik", number = it) }
        val key = PublisherQuestionMixer.key(question("3D", number = 999))
        val scoped = PublisherQuestionMixer.filter(source, key)
        val osym = LevelQuestionPolicy.select(TopicTestLevel.OSYM, scoped, 10, mixSeed = 12)
        val degree = LevelQuestionPolicy.select(TopicTestLevel.DERECE, scoped, 10, mixSeed = 14)
        assertEquals(10, osym.size)
        assertTrue(osym.all { it.publisherName == "3D" && it.difficulty == DifficultyLevel.OSYM })
        assertTrue(degree.all {
            it.publisherName == "3D" &&
                it.difficulty in setOf(DifficultyLevel.ZOR, DifficultyLevel.COK_ZOR)
        })
    }
}
