package com.example.viewmodel

import com.example.data.DifficultyLevel
import com.example.data.QUESTION_DIFFICULTY_BANDS
import com.example.data.TopicTestLevel
import com.example.data.YksQuestion
import com.example.data.fourTier
import com.example.data.isEligibleForLevel

/**
 * Strict policy: four difficulty bands AND a separate ÖSYM-style tag.
 *
 * SEVIYE_TESPIT = fair mixture of the four difficulty levels.
 * BASLANGIC     = only Kolay.
 * PEKISTIRME    = majority Orta, optional small Kolay/Zor exposure.
 * OSYM          = only questions explicitly tagged ÖSYM tipi, any difficulty.
 * DERECE        = only Zor or Çok Zor, without requiring both.
 * IKIZ          = supplied wrong questions only.
 */
internal object LevelQuestionPolicy {
    private val diagnosticOrder = QUESTION_DIFFICULTY_BANDS

    fun select(
        level: TopicTestLevel,
        sourceQuestions: List<YksQuestion>,
        count: Int,
        previouslyUsed: Set<String> = emptySet(),
        mixSeed: Int = 0
    ): List<YksQuestion> {
        if (count < 1) return emptyList()
        val eligible = sourceQuestions.asSequence()
            .filter { it.answerKeyVerified && it.isEligibleForLevel(level) }
            .distinctBy(::stableId).toList()
        if (level == TopicTestLevel.IKIZ) {
            return eligible.take(count) // caller supplies wrong answers in recency order
        }
        val byLevel = eligible.groupBy { it.difficulty.fourTier() }
            .mapValues { (diff, items) ->
                PublisherQuestionMixer.orderFreshFair(
                    items, previouslyUsed, mixSeed xor ((diff.ordinal + 1) * 1499)
                ).toMutableList()
            }
        val selected = mutableListOf<YksQuestion>()
        when (level) {
            TopicTestLevel.SEVIYE_TESPIT -> {
                // Keep counts balanced; do not fill scarce difficulty with easier questions.
                val available = diagnosticOrder.filter { !byLevel[it].isNullOrEmpty() }
                val counts = available.associateWith { 0 }.toMutableMap()
                while (selected.size < count) {
                    var added = false
                    for (diff in available) {
                        if (selected.size >= count) break
                        val bucket = byLevel.getValue(diff)
                        // An exhausted scarce band must not block other real
                        // questions from entering a mixed diagnostic test.
                        val minimum = available.filter { !byLevel[it].isNullOrEmpty() }
                            .minOfOrNull { counts[it] ?: 0 } ?: 0
                        if (bucket.isNotEmpty() && (counts[diff] ?: 0) <= minimum) {
                            selected += bucket.removeAt(0)
                            counts[diff] = (counts[diff] ?: 0) + 1
                            added = true
                        }
                    }
                    if (!added) break
                }
            }
            TopicTestLevel.BASLANGIC ->
                selected += byLevel[DifficultyLevel.KOLAY].orEmpty().take(count)
            TopicTestLevel.PEKISTIRME -> {
                val medium = byLevel[DifficultyLevel.ORTA].orEmpty()
                val main = medium.take(count)
                if (main.isNotEmpty()) {
                    val allowedSecondary = when {
                        count < 4 -> 0
                        count < 8 -> 1
                        else -> 2
                    }.coerceAtMost((main.size - 1).coerceAtLeast(0))
                    val secondary = mutableListOf<YksQuestion>()
                    if (allowedSecondary > 0) {
                        byLevel[DifficultyLevel.KOLAY]?.firstOrNull()?.let(secondary::add)
                    }
                    if (allowedSecondary > secondary.size) {
                        byLevel[DifficultyLevel.ZOR]?.firstOrNull()?.let(secondary::add)
                    }
                    val chosenSecondary = secondary.take(allowedSecondary)
                    val chosenMedium = main.take(count - chosenSecondary.size)
                    selected.addAll(chosenMedium)
                    selected.addAll(chosenSecondary)
                }
            }
            TopicTestLevel.OSYM -> {
                // Style, not difficulty: include Kolay+ÖSYM, Orta+ÖSYM, etc.
                // Use fair publishers, not a fake fifth band.
                val allTagged = eligible.filter { it.isOsymType || it.difficulty == DifficultyLevel.OSYM }
                selected += PublisherQuestionMixer.orderFreshFair(
                    allTagged, previouslyUsed, mixSeed
                ).take(count)
            }
            TopicTestLevel.DERECE -> {
                val hard = byLevel[DifficultyLevel.ZOR].orEmpty()
                val veryHard = byLevel[DifficultyLevel.COK_ZOR].orEmpty()
                val idealVeryHard = when {
                    count < 4 -> 0
                    count <= 8 -> 1
                    else -> 2
                }
                val hardQuota = minOf(hard.size, count - idealVeryHard)
                val requiredVeryHard = minOf(veryHard.size, count - hardQuota)
                selected.addAll(hard.take(hardQuota))
                selected.addAll(veryHard.take(requiredVeryHard))
                val remaining = count - selected.size
                if (remaining > 0) selected.addAll(hard.drop(hardQuota).take(remaining))
                val stillRemaining = count - selected.size
                if (stillRemaining > 0) selected.addAll(veryHard.drop(requiredVeryHard).take(stillRemaining))
            }
            TopicTestLevel.IKIZ -> Unit
        }
        return PublisherQuestionMixer.shuffleNatural(selected.take(count), mixSeed)
    }

    fun maximumConstructibleCount(
        level: TopicTestLevel,
        sourceQuestions: List<YksQuestion>,
        cap: Int = 12
    ): Int {
        if (cap <= 0) return 0
        // Counting a huge library must be O(N) and avoid constructing twelve
        // complete shuffled test proposals just to enable the top filter.
        val distinct = HashSet<String>()
        var matching = 0
        for (q in sourceQuestions) {
            if (q.answerKeyVerified && q.isEligibleForLevel(level) &&
                distinct.add(stableId(q))) {
                // Pekiştirme is controlled by the main Orta band and may
                // include only a couple of secondary Kolay/Zor questions.
                if (level != TopicTestLevel.PEKISTIRME ||
                    q.difficulty.fourTier() == DifficultyLevel.ORTA) matching++
                if (matching >= cap) return cap
            }
        }
        return matching
    }

    fun stableId(question: YksQuestion): String =
        question.sourceQuestionId.ifBlank {
            question.croppedImagePath.ifBlank { question.id }
        }
}

internal data class LevelPlacement(val recommended: TopicTestLevel, val note: String)

/** Four-difficulty diagnostic; OSYM-type accuracy is an independent optional signal. */
internal object LevelPlacementAdvisor {
    fun evaluate(questions: List<YksQuestion>, chosenAnswers: Map<Int, String>): LevelPlacement? {
        val verified = questions.filter {
            it.answerKeyVerified && it.correctOption in setOf("A", "B", "C", "D", "E")
        }
        if (verified.size < 4) return null
        val groups = verified.groupBy { it.difficulty.fourTier() }
        if (QUESTION_DIFFICULTY_BANDS.any { groups[it].isNullOrEmpty() }) return null
        fun accuracy(items: List<YksQuestion>): Double =
            items.count { chosenAnswers[it.number]?.uppercase() == it.correctOption }
                .toDouble() / items.size
        val osymQuestions = verified.filter {
            it.isOsymType || it.difficulty == DifficultyLevel.OSYM
        }
        val recommendation = when {
            accuracy(groups.getValue(DifficultyLevel.KOLAY)) < 0.70 -> TopicTestLevel.BASLANGIC
            accuracy(groups.getValue(DifficultyLevel.ORTA)) < 0.70 -> TopicTestLevel.PEKISTIRME
            osymQuestions.isNotEmpty() && accuracy(osymQuestions) < 0.60 -> TopicTestLevel.OSYM
            else -> TopicTestLevel.DERECE
        }
        val note = when (recommendation) {
            TopicTestLevel.BASLANGIC -> "Kolay sorularda temel kazanımları sağlamlaştır."
            TopicTestLevel.PEKISTIRME -> "Orta seviye pratikte doğruluğunu yükselt."
            TopicTestLevel.OSYM -> "ÖSYM tipi etiketli sorularda doğruluğunu ve hızını geliştir."
            else -> "Zor ve Çok Zor sorularla ilerle."
        }
        return LevelPlacement(recommendation, note)
    }
}
