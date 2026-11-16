package com.example.data

import java.util.Locale

data class LearningOutcome(
    val subjectId: String,
    val topicId: String,
    val id: String,
    val name: String,
    val phrases: List<String> = emptyList()
)

data class LearningOutcomeAssignment(
    val id: String = "",
    val name: String = "",
    val source: String = "",
    val confidence: Double = 0.0
)

/** App-authored teaching labels; never presented as official MEB outcome codes. */
object LearningOutcomeCatalog {
    fun normalized(value: String): String = value.trim()
        .lowercase(Locale.forLanguageTag("tr-TR"))
        .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u')
        .replace('ş', 's').replace('ö', 'o').replace('ç', 'c')
        .replace(Regex("\\s+"), " ")

    fun candidates(subjectId: String, topicId: String): List<LearningOutcome> =
        GeneratedLearningOutcomes.entries.filter { it.subjectId == subjectId && it.topicId == topicId }

    fun resolve(subjectId: String, topicId: String, idOrName: String): LearningOutcome? =
        candidates(subjectId, topicId).firstOrNull {
            it.id == idOrName || normalized(it.name) == normalized(idOrName)
        }

    fun validated(subjectId: String, topicId: String, assignment: LearningOutcomeAssignment): LearningOutcomeAssignment {
        val match = resolve(subjectId, topicId, assignment.id)
            ?: return LearningOutcomeAssignment()
        if (!assignment.confidence.isFinite() || assignment.confidence !in 0.0..1.0 ||
            assignment.source !in setOf("manual", "ocr", "model", "publisher")) return LearningOutcomeAssignment()
        return assignment.copy(id = match.id, name = match.name)
    }

    fun infer(subjectId: String, topicId: String, text: String): LearningOutcomeAssignment {
        val normalizedText = normalized(text)
        if (normalizedText.isBlank()) return LearningOutcomeAssignment()
        val matches = candidates(subjectId, topicId).filter { outcome ->
            outcome.phrases.any { phrase ->
                Regex("(?<![a-z0-9])" + Regex.escape(normalized(phrase)) + "(?![a-z0-9])")
                    .containsMatchIn(normalizedText)
            }
        }
        // Uncertain and multiple-skill questions must not acquire an arbitrary exact timestamp.
        return matches.singleOrNull()?.let {
            LearningOutcomeAssignment(it.id, it.name, "ocr", 0.9)
        } ?: LearningOutcomeAssignment()
    }

    fun fromLabel(subjectId: String, topicId: String, label: String): LearningOutcomeAssignment =
        resolve(subjectId, topicId, label)?.let {
            LearningOutcomeAssignment(it.id, it.name, "publisher", 1.0)
        } ?: LearningOutcomeAssignment()

    fun fromMetadata(meta: Map<String, String>): LearningOutcomeAssignment = LearningOutcomeAssignment(
        meta["outcome"].orEmpty(), meta["outcomeName"].orEmpty(),
        meta["outcomeSource"].orEmpty(),
        meta["outcomeConfidence"]?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
    )
}
