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
        val originSubject = normalized(subjectId)
        val originTopicId = normalized(topicId)
        val originTopic = normalized(topicName.ifBlank { source.topicHeader })
        if (originSubject.isBlank() || (originTopic.isBlank() && originTopicId.isBlank())) {
            return null
        }

        return pool.asSequence().filter { candidate ->
            candidate.id.isNotBlank() && candidate.id != originalId &&
                candidate.id !in excludedIds &&
                candidate.answerKeyVerified &&
                candidate.correctOption.uppercase(Locale.ROOT) in options &&
                normalized(candidate.subjectId) == originSubject &&
                // If original has a verified sub-outcome, only an identical
                // publisher/ZIP outcome tag is allowed; no guessed mapping.
                (verifiedOutcome.isBlank() ||
                    normalized(candidate.learningOutcome) == verifiedOutcome) &&
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
