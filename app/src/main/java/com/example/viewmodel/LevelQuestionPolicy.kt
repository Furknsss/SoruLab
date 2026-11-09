package com.example.viewmodel

import com.example.data.DifficultyLevel
import com.example.data.TopicTestLevel
import com.example.data.YksQuestion

/**
 * Published difficulty labels, not question-length guesses, determine levels.
 * It is better to offer fewer questions than silently fill the wrong level.
 */
internal object LevelQuestionPolicy {
    private val diagnosticOrder = listOf(
        DifficultyLevel.KOLAY, DifficultyLevel.ORTA, DifficultyLevel.OSYM,
        DifficultyLevel.ZOR, DifficultyLevel.COK_ZOR
    )

    fun select(
        level: TopicTestLevel,
        sourceQuestions: List<YksQuestion>,
        count: Int,
        previouslyUsed: Set<String> = emptySet()
    ): List<YksQuestion> {
        if (count < 1) return emptyList()
        val eligible = sourceQuestions.filter { it.answerKeyVerified }
            .distinctBy { stableId(it) }
        if (level == TopicTestLevel.IKIZ) {
            // For IKIZ the caller must supply ONLY questions previously marked
            // wrong; preserve recency/attempt order, never refill from the pool.
            return eligible.take(count)
        }
        val byLevel = eligible.groupBy { it.difficulty }
            .mapValues { (_, items) ->
                items.sortedWith(compareBy<YksQuestion> {
                    if (stableId(it) in previouslyUsed) 1 else 0
                }.thenBy { stableId(it) }).toMutableList()
            }
        if (level == TopicTestLevel.SEVIYE_TESPIT &&
            (count < 5 || diagnosticOrder.any { byLevel[it].isNullOrEmpty() })
        ) return emptyList()

        val slots = when (level) {
            TopicTestLevel.SEVIYE_TESPIT -> diagnosticOrder
            TopicTestLevel.BASLANGIC -> {
                val medium = when {
                    count < 4 -> 0
                    count <= 8 -> 1
                    else -> 2
                }
                List((count - medium).coerceAtLeast(0)) { DifficultyLevel.KOLAY } +
                    List(minOf(count, medium)) { DifficultyLevel.ORTA }
            }
            TopicTestLevel.PEKISTIRME -> {
                val extras = if (count >= 8) listOf(DifficultyLevel.KOLAY, DifficultyLevel.ZOR)
                    else if (count >= 4) listOf(DifficultyLevel.KOLAY) else emptyList()
                List((count - extras.size).coerceAtLeast(0)) { DifficultyLevel.ORTA } + extras
            }
            TopicTestLevel.OSYM -> listOf(DifficultyLevel.OSYM)
            TopicTestLevel.DERECE -> {
                val veryHard = when {
                    count < 4 -> 0
                    count <= 8 -> 1
                    else -> 2
                }
                List((count - veryHard).coerceAtLeast(0)) { DifficultyLevel.ZOR } +
                    List(minOf(count, veryHard)) { DifficultyLevel.COK_ZOR }
            }
            TopicTestLevel.IKIZ -> emptyList()
        }
        val selected = mutableListOf<YksQuestion>()
        if (level == TopicTestLevel.SEVIYE_TESPIT) {
            // Fairness is a hard constraint, not a best-effort average.
            // If one band runs out, stop before another band exceeds it by
            // more than one question. Do not claim a skewed test is balanced.
            val pickedByBand = diagnosticOrder.associateWith { 0 }.toMutableMap()
            while (selected.size < count) {
                var added = false
                for (difficulty in slots) {
                    if (selected.size == count) break
                    val bucket = byLevel[difficulty] ?: continue
                    val minimum = pickedByBand.values.minOrNull() ?: 0
                    if (bucket.isNotEmpty() && (pickedByBand[difficulty] ?: 0) <= minimum) {
                        selected += bucket.removeAt(0)
                        pickedByBand[difficulty] = (pickedByBand[difficulty] ?: 0) + 1
                        added = true
                    }
                }
                if (!added) break
            }
        } else if (level == TopicTestLevel.OSYM) {
            selected.addAll(byLevel[DifficultyLevel.OSYM].orEmpty().take(count))
        } else {
            // Only the permitted one/two secondary questions, never spill
            // into unrequested categories as a "nearest difficulty" fallback.
            val quotas = slots.groupingBy { it }.eachCount()
            for ((difficulty, number) in quotas) {
                selected.addAll(byLevel[difficulty].orEmpty().take(number))
            }
            // A level without its dominant band must NOT be filled by
            // only secondary questions. Ensure strict primary majority.
            val main = selected.filter { it.difficulty == slots.first() }
            val secondary = selected.filter { it.difficulty != slots.first() }
                .take((main.size - 1).coerceAtLeast(0))
            selected.clear()
            selected.addAll(main.take((main.size + 1) / 2))
            selected.addAll(secondary)
            selected.addAll(main.drop((main.size + 1) / 2))
        }
        return selected.take(count)
    }

    /** Maximum full-sized test possible without breaking the stage's quotas. */
    fun maximumConstructibleCount(
        level: TopicTestLevel,
        sourceQuestions: List<YksQuestion>,
        cap: Int = 12
    ): Int = (cap.coerceAtLeast(0) downTo 1).firstOrNull {
        select(level, sourceQuestions, it).size == it
    } ?: 0

    fun stableId(q: YksQuestion): String =
        q.sourceQuestionId.ifBlank { q.croppedImagePath.ifBlank { q.id } }
}

internal data class LevelPlacement(val recommended: TopicTestLevel, val note: String)

/** The results screen only shows a placement after a graded, five-band diagnostic. */
internal object LevelPlacementAdvisor {
    fun evaluate(questions: List<YksQuestion>, chosenAnswers: Map<Int, String>): LevelPlacement? {
        val verified = questions.filter { it.answerKeyVerified &&
            it.correctOption in setOf("A", "B", "C", "D", "E") }
        if (verified.size < 5) return null
        val groups = verified.groupBy { it.difficulty }
        if (DifficultyLevel.entries.any { groups[it].isNullOrEmpty() }) return null
        fun accuracy(level: DifficultyLevel): Double {
            val items = groups[level].orEmpty()
            return items.count {
                chosenAnswers[it.number]?.uppercase() == it.correctOption
            }.toDouble() / items.size
        }
        val recommendation = when {
            accuracy(DifficultyLevel.KOLAY) < 0.70 -> TopicTestLevel.BASLANGIC
            accuracy(DifficultyLevel.ORTA) < 0.70 -> TopicTestLevel.PEKISTIRME
            accuracy(DifficultyLevel.OSYM) < 0.60 -> TopicTestLevel.OSYM
            else -> TopicTestLevel.DERECE
        }
        val note = when (recommendation) {
            TopicTestLevel.BASLANGIC -> "Önce kolay sorulardaki temel kazanımları sağlamlaştır."
            TopicTestLevel.PEKISTIRME -> "Orta seviye soru pratiğini artırarak eksikleri kapat."
            TopicTestLevel.OSYM -> "Artık ÖSYM etiketli sorularda doğruluğunu ve hızını geliştir."
            else -> "Temel ve ÖSYM aşamalarını geçtin; zor sorularla ilerle."
        }
        return LevelPlacement(recommendation, note)
    }
}
