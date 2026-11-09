package com.example.viewmodel

import com.example.data.YksQuestion
import java.util.Locale
import kotlin.random.Random

/**
 * Random-looking, reproducible publisher mixing for an already chosen set
 * of verified questions. Does not invent questions or alter difficulty quotas.
 *
 * Publisher choice is exact: ID when present, otherwise normalized full
 * publisher name. Never filter publishers by substring matches.
 */
internal object PublisherMixingPolicy {
    private val tr = Locale.forLanguageTag("tr-TR")

    fun key(id: String, name: String): String = if (id.isNotBlank()) {
        "id:" + id.trim().lowercase(tr)
    } else {
        "name:" + name.trim().lowercase(tr)
    }

    fun key(question: YksQuestion): String =
        key(question.publisherId, question.publisherName)

    /**
     * Weighted, seeded random sampling deliberately avoids rigid two-by-two
     * import order and artificial A-B-A-B round-robin ordering.
     *
     * Adjacent publishers may occasionally repeat naturally, but a third
     * consecutive match is avoided if another publisher still has questions.
     * More questions in a source means a proportionally higher pick chance.
     */
    fun <T> mix(
        questions: List<T>,
        seed: Int,
        publisherKey: (T) -> String
    ): List<T> {
        if (questions.size <= 1) return questions.toList()
        val rng = Random(seed)
        val buckets = questions.groupBy(publisherKey).mapValues { (_, values) ->
            values.shuffled(rng).toMutableList()
        }.toMutableMap()

        val result = ArrayList<T>(questions.size)
        var previousKey: String? = null
        var streak = 0
        while (result.size < questions.size) {
            val remaining = buckets.filterValues { it.isNotEmpty() }
            if (remaining.isEmpty()) break
            val choices = remaining.entries.filter {
                it.key != previousKey || streak < 2 || remaining.size == 1
            }
            val weightedTotal = choices.sumOf { (key, bucket) ->
                bucket.size * if (key == previousKey) 0.42 else 1.0
            }
            var ticket = rng.nextDouble(weightedTotal)
            val pickedKey = (choices.firstOrNull { (key, bucket) ->
                ticket -= bucket.size * if (key == previousKey) 0.42 else 1.0
                ticket < 0.0
            } ?: choices.last()).key
            result += buckets.getValue(pickedKey).removeAt(0)
            streak = if (pickedKey == previousKey) streak + 1 else 1
            previousKey = pickedKey
        }
        return result
    }

    fun onlySelected(
        questions: List<YksQuestion>,
        selectedPublisherKey: String?
    ): List<YksQuestion> = if (selectedPublisherKey == null) {
        questions
    } else {
        questions.filter { key(it) == selectedPublisherKey }
    }

    fun mix(questions: List<YksQuestion>, seed: Int): List<YksQuestion> =
        mix(questions, seed, ::key)
}

internal data class TestPublisherChoice(val key: String, val name: String) {
    companion object {
        fun fromQuestions(questions: List<YksQuestion>): List<TestPublisherChoice> =
            questions.filter { it.publisherName.isNotBlank() }
                .groupBy(PublisherMixingPolicy::key)
                .map { (key, values) ->
                    TestPublisherChoice(key, values.first().publisherName.trim())
                }.sortedBy { it.name.lowercase(Locale.forLanguageTag("tr-TR")) }
    }
}
