package com.example.viewmodel

import com.example.data.CustomQuestionRecord
import com.example.data.YksQuestion
import java.util.Locale
import kotlin.math.abs

/**
 * Offline, evidence-based twin question selection.
 *
 * A twin is NEVER the original question with renumbered options. It must be
 * another locally available question in the same subject/topic with an actual
 * publisher answer key. Do not invent a question, solution, or sub-outcome.
 */
internal object GroundedTwinQuestionMatcher {
    private val options = setOf("A", "B", "C", "D", "E")

    private fun normalized(value: String): String =
        value.trim().lowercase(Locale.forLanguageTag("tr-TR"))
            .replace(Regex("\\s+"), " ")

    // A pool-based twin requires independently published source questions with
    // verifiable, very similar question architecture. Topic alone is never enough.
    // This numerical/variable mask compares question FORM, not the answer values.
    private fun questionShape(raw: String): String = normalized(raw)
        .replace(Regex("(?<![a-z])(?:f|g|h)\\(x\\)"), "k(x)")
        .replace(Regex("\\d+(?:[.,]\\d+)?"), "#")
        .replace(Regex("\\s+"), " ")
        .take(1000)

    private fun shapeSimilarity(first: String, second: String): Double {
        if (first.length < 40 || second.length < 40) return 0.0
        val lengthRatio = first.length.toDouble() / second.length.toDouble()
        if (lengthRatio !in 0.72..1.38) return 0.0
        fun grams(text: String): Set<String> =
            text.windowed(4, step = 1, partialWindows = false).toSet()
        val a = grams(first)
        val b = grams(second)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return 2.0 * a.intersect(b).size / (a.size + b.size)
    }

    fun select(
        source: YksQuestion,
        subjectId: String,
        topicId: String,
        topicName: String,
        pool: List<CustomQuestionRecord>,
        excludedIds: Set<String> = emptySet(),
        previousUseCounts: Map<String, Int> = emptyMap(),
        isUsable: (CustomQuestionRecord) -> Boolean
    ): CustomQuestionRecord? {
        val originalId = source.sourceQuestionId.ifBlank {
            source.id.removePrefix("custom_")
        }
        val originalText = normalized(
            source.analyticsText.ifBlank { source.stemTop }
        )
        val originalImage = source.croppedImagePath
        val sourceBook = normalized(source.sourceBookTitle)
        val verifiedOutcome = normalized(source.learningOutcome)
        val verifiedQuestionType = normalized(source.questionType)
        val sourceShape = questionShape(originalText)
        val originSubject = normalized(subjectId)
        val originTopicId = normalized(topicId)
        val originTopic = normalized(topicName.ifBlank { source.topicHeader })
        if (!source.answerKeyVerified || verifiedOutcome.isBlank() ||
            verifiedQuestionType.isBlank() || sourceShape.length < 40 ||
            originSubject.isBlank() || (originTopic.isBlank() && originTopicId.isBlank())) {
            return null
        }

        return pool.asSequence().filter { candidate ->
            candidate.id.isNotBlank() && candidate.id != originalId &&
                candidate.id !in excludedIds &&
                candidate.answerKeyVerified &&
                candidate.correctOption.uppercase(Locale.ROOT) in options &&
                normalized(candidate.subjectId) == originSubject &&
                // Identical verified sub-outcome AND exact question architecture.
                // Same topic with unrelated reasoning is strictly rejected.
                normalized(candidate.learningOutcome) == verifiedOutcome &&
                normalized(candidate.questionType) == verifiedQuestionType &&
                shapeSimilarity(sourceShape, questionShape(candidate.questionText)) >= 0.78 &&
                (
                    (originTopicId.isNotBlank() && normalized(candidate.topicId) == originTopicId) ||
                        (originTopic.isNotBlank() && normalized(candidate.topicName) == originTopic)
                    ) &&
                (candidate.croppedImagePath.isNotBlank() || candidate.imageBase64.isNotBlank()) &&
                (originalImage.isBlank() || candidate.croppedImagePath != originalImage) &&
                (source.imageBase64.isBlank() || candidate.imageBase64 != source.imageBase64) &&
                // If we have the original printed position, never select that
                // same source page/question under another import identifier.
                !(sourceBook.isNotBlank() &&
                    sourceBook == normalized(candidate.sourceBookTitle) &&
                    source.sourcePrintedQuestionNumber > 0 &&
                    source.sourcePrintedQuestionNumber == candidate.questionNumberInBook) &&
                // Meaningful near-identical OCR text is a duplicate, not a twin.
                !(originalText.length >= 30 &&
                    originalText == normalized(candidate.questionText))
        }.sortedWith(
            compareBy<CustomQuestionRecord>(
                // Reuse a previously generated twin only after unused
                // alternatives at comparable topic/skill are exhausted.
                { previousUseCounts[it.id] ?: 0 },
                { if (it.difficultyLevel5 == source.difficulty) 0 else 1 },
                { abs(it.difficultyLevel5.weight - source.difficulty.weight) },
                { if (sourceBook.isNotBlank() && normalized(it.sourceBookTitle) != sourceBook) 0 else 1 },
                { it.id }
            )
        ).firstOrNull(isUsable)
    }
}
