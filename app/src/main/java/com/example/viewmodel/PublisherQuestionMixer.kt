package com.example.viewmodel

import com.example.data.YksQuestion
import java.util.Locale
import kotlin.random.Random

/** One canonical publisher per user choice; never pass a fuzzy/partial string match. */
internal data class PublisherChoice(
    val key: String,
    val title: String,
    val availableQuestions: Int
)

/**
 * Publisher-aware question distribution.
 *
 * Previously tests used fixed publisher-by-publisher rounds; this yielded a
 * conspicuous 2-2-2 pattern. Now we first allocate fair quotas, prefer unseen
 * questions, and then vary the actual test order with a seeded natural shuffle.
 * The selected questions remain reproducible within their saved test snapshot.
 */
internal object PublisherQuestionMixer {
    private val tr = Locale.forLanguageTag("tr-TR")

    fun key(question: YksQuestion): String {
        val name = question.publisherName.trim()
        val stamp = question.publisherStamp.trim()
        val raw = when {
            name.isNotBlank() && !name.equals("Kaynak Belirtilmedi", ignoreCase = true) -> name
            stamp.isNotBlank() && !stamp.equals("Kaynak Belirtilmedi", ignoreCase = true) -> stamp
            question.publisherId.isNotBlank() -> question.publisherId
            else -> "Kaynak Belirtilmedi"
        }
        return raw.lowercase(tr)
            .replace(Regex("""\s+"""), " ")
            .removeSuffix(" yayınları")
            .removeSuffix(" yayıncılık")
            .trim()
    }

    fun options(questions: List<YksQuestion>): List<PublisherChoice> = questions
        .groupBy(::key)
        .map { (key, records) ->
            PublisherChoice(
                key = key,
                title = records.first().publisherName
                    .takeUnless { it.isBlank() || it.equals("Kaynak Belirtilmedi", true) }
                    ?: records.first().publisherStamp.takeUnless(String::isBlank)
                    ?: "Kaynak Belirtilmedi",
                availableQuestions = records.distinctBy(LevelQuestionPolicy::stableId).size
            )
        }
        .sortedBy { it.title.lowercase(tr) }

    /** Strict publisher filtering, applied before level quotas and question selection. */
    fun filter(questions: List<YksQuestion>, selectedKey: String?): List<YksQuestion> =
        if (selectedKey == null) questions else questions.filter { key(it) == selectedKey }

    /**
     * Round-robin fairness is used ONLY to allocate which questions enter the
     * candidate list; it does not determine their visible order.
     */
    private fun fairOrder(items: List<YksQuestion>, seed: Int): List<YksQuestion> {
        if (items.size <= 1) return items
        val random = Random(seed)
        val byPublisher = items.shuffled(random)
            .groupBy(::key)
            .mapValues { (_, grouped) -> grouped.toMutableList() }
        val counts = byPublisher.keys.associateWith { 0 }.toMutableMap()
        val result = ArrayList<YksQuestion>(items.size)
        while (result.size < items.size) {
            val remaining = byPublisher.keys.filter { !byPublisher[it].isNullOrEmpty() }
            if (remaining.isEmpty()) break
            val minimumCount = remaining.minOf { counts[it] ?: 0 }
            val contenders = remaining.filter { counts[it] == minimumCount }
            val chosen = contenders[random.nextInt(contenders.size)]
            result += byPublisher.getValue(chosen).removeAt(0)
            counts[chosen] = (counts[chosen] ?: 0) + 1
        }
        return result
    }

    fun orderFreshFair(
        questions: List<YksQuestion>,
        previouslyUsed: Set<String>,
        seed: Int
    ): List<YksQuestion> {
        val unseen = questions.filter { LevelQuestionPolicy.stableId(it) !in previouslyUsed }
        val repeated = questions.filter { LevelQuestionPolicy.stableId(it) in previouslyUsed }
        return fairOrder(unseen, seed) + fairOrder(repeated, seed xor 0x5A12C4)
    }

    /** Natural, seed-dependent order; avoid triple runs when another publisher exists. */
    fun shuffleNatural(questions: List<YksQuestion>, seed: Int): List<YksQuestion> {
        if (questions.size <= 1) return questions
        val mixed = questions.shuffled(Random(seed xor 0x739B12)).toMutableList()
        for (i in 2 until mixed.size) {
            val publisher = key(mixed[i])
            if (key(mixed[i - 1]) != publisher || key(mixed[i - 2]) != publisher) continue
            val swapIndex = (i + 1 until mixed.size).firstOrNull { j ->
                val alternative = key(mixed[j])
                alternative != publisher &&
                    (j == mixed.lastIndex || key(mixed[j + 1]) != publisher)
            } ?: (i + 1 until mixed.size).firstOrNull { key(mixed[it]) != publisher }
            if (swapIndex != null) {
                val old = mixed[i]
                mixed[i] = mixed[swapIndex]
                mixed[swapIndex] = old
            }
        }
        return mixed
    }
}
