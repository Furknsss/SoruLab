package com.example.viewmodel

import com.example.data.YksQuestion

/**
 * Repetition-aware, deterministic practice picker. Prefer questions never
 * solved, then never generated. Reuse old material only once the pool is
 * exhausted, always favoring the least recently attempted alternatives.
 */
internal object FreshPracticeQuestionSelector {
    fun identity(question: YksQuestion): String {
        // Source IDs persist in all legacy saved snapshots even when
        // source book/test metadata was not serialized. Prefer this stable
        // account-scoped key to avoid losing repetition history on restart.
        if (question.sourceQuestionId.isNotBlank()) return "source:${question.sourceQuestionId}"
        // Fallback for catalog/legacy questions without a source record.
        if (question.sourceBookTitle.isNotBlank() &&
            question.sourcePrintedQuestionNumber > 0 &&
            question.sourceTestId.isNotBlank()
        ) {
            return "print:${question.sourceBookTitle.trim().lowercase()}|" +
                "${question.sourceTestId.trim().lowercase()}|" +
                question.sourcePrintedQuestionNumber
        }
        return question.croppedImagePath.takeIf { it.isNotBlank() }?.let { "image:$it" }
            ?: "id:${question.id}"
    }

    fun choose(
        candidates: List<YksQuestion>,
        generatedBefore: List<YksQuestion>,
        solvedBefore: List<YksQuestion>,
        justSeen: List<YksQuestion>,
        requestedCount: Int
    ): List<YksQuestion> {
        if (requestedCount <= 0) return emptyList()
        val currentIds = justSeen.mapTo(HashSet()) { identity(it) }
        val available = candidates.asSequence()
            .filter { it.answerKeyVerified && it.correctOption in listOf("A", "B", "C", "D", "E") }
            .distinctBy(::identity)
            .filterNot { identity(it) in currentIds }
            .toMutableList()
        if (available.isEmpty()) return emptyList()

        val generatedCounts = generatedBefore.groupingBy(::identity).eachCount()
        val solvedCounts = solvedBefore.groupingBy(::identity).eachCount()
        val mostRecentSolvedIndex = LinkedHashMap<String, Int>()
        solvedBefore.forEachIndexed { index, question ->
            mostRecentSolvedIndex.putIfAbsent(identity(question), index)
        }
        val selected = ArrayList<YksQuestion>(minOf(requestedCount, available.size))
        val pickedByPublisher = HashMap<String, Int>()
        while (selected.size < requestedCount && available.isNotEmpty()) {
            val best = available.minWithOrNull(
                compareBy<YksQuestion>(
                    { if ((solvedCounts[identity(it)] ?: 0) == 0) 0 else 1 },
                    { if ((generatedCounts[identity(it)] ?: 0) == 0) 0 else 1 },
                    { solvedCounts[identity(it)] ?: 0 },
                    { generatedCounts[identity(it)] ?: 0 },
                    { pickedByPublisher[it.publisherName] ?: 0 },
                    // Higher index means longer ago (solvedBefore is newest first).
                    { -(mostRecentSolvedIndex[identity(it)] ?: 0) },
                    { identity(it) }
                )
            ) ?: break
            available.remove(best)
            selected.add(best)
            pickedByPublisher[best.publisherName] =
                (pickedByPublisher[best.publisherName] ?: 0) + 1
        }
        return selected
    }
}
