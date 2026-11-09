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
     * Weighted random publisher selection prevents predictable 2+2 or
     * A-B-A-B round-robin patterns and avoids consecutive publisher repeats
     * whenever more than one distinct source still has questions.
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
        while (result.size < questions.size) {
            val remaining = buckets.filterValues { it.isNotEmpty() }
            if (remaining.isEmpty()) break
            val alternatives = remaining.filterKeys { it != previousKey }
            val candidates = if (alternatives.isNotEmpty()) alternatives else remaining

            val total = candidates.values.sumOf { it.size }
            var lottery = rng.nextInt(total)
            val pickedKey = candidates.entries.first { (_, items) ->
                lottery -= items.size
                lottery < 0
            }.key

            result += buckets.getValue(pickedKey).removeAt(0)
            previousKey = pickedKey
        }
        return result
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
