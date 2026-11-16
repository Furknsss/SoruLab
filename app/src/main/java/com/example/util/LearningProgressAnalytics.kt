package com.example.util

import com.example.data.ExamType
import com.example.data.LearningOutcomeAssignment
import com.example.data.LearningOutcomeCatalog
import com.example.data.SoruLabCatalog
import com.example.data.TestResultRecord
import com.example.data.TestSnapshotCodec
import com.example.data.YksQuestion
import org.json.JSONObject
import java.util.Locale

internal data class ProgressCounts(
    val correct: Int = 0, val wrong: Int = 0, val blank: Int = 0, val ungraded: Int = 0
) {
    val total get() = correct + wrong + blank + ungraded
    val graded get() = correct + wrong + blank
    val accuracyPercent: Int? get() = if (graded == 0) null else (100.0 * correct / graded).toInt()
    val answeredAccuracyPercent: Int? get() = if (correct + wrong == 0) null
        else (100.0 * correct / (correct + wrong)).toInt()
    val net: Double? get() = if (graded == 0) null else correct - wrong * 0.25
}

internal enum class ProgressEvidence(val label: String) {
    INSUFFICIENT("Örneklem az"), STRONG("Güçlü görünüm"),
    NEEDS_PRACTICE("Tekrar önerilir"), DEVELOPING("Gelişiyor")
}

internal data class LearningProgressBucket(
    val id: String, val label: String, val counts: ProgressCounts,
    val attemptCount: Int, val uniqueQuestionCount: Int, val uniqueGradedQuestionCount: Int,
    val children: List<LearningProgressBucket> = emptyList(),
    val classified: Boolean = true
) {
    // Repeated answers to one question are practice, not evidence of broad mastery.
    val evidence: ProgressEvidence get() = when {
        !classified || counts.graded < 10 || uniqueGradedQuestionCount < 5 -> ProgressEvidence.INSUFFICIENT
        (counts.accuracyPercent ?: 0) >= 80 -> ProgressEvidence.STRONG
        (counts.accuracyPercent ?: 0) <= 50 -> ProgressEvidence.NEEDS_PRACTICE
        else -> ProgressEvidence.DEVELOPING
    }
}

internal data class LearningProgressAttempt(
    val id: String, val record: TestResultRecord, val counts: ProgressCounts?
)

internal data class LearningProgressReport(
    val counts: ProgressCounts,
    val attemptCount: Int,
    val uniqueQuestionCount: Int,
    val subjects: List<LearningProgressBucket>,
    val recentResults: List<TestResultRecord>,
    val recentAttempts: List<LearningProgressAttempt>,
    val excludedAttemptCount: Int,
    val unscopedQuestionCount: Int,
    val unknownOutcomeCount: Int,
    val duplicateRecordCount: Int
)

/** Rebuilds learning evidence from immutable question snapshots, never stale test-header totals. */
internal object LearningProgressAnalytics {
    private data class Item(
        val attempt: String, val identity: String?, val status: AnswerOutcome,
        val subjectId: String?, val subjectName: String,
        val topicId: String, val topicName: String,
        val outcomeId: String, val outcomeName: String
    )

    fun calculate(
        records: List<TestResultRecord>, exam: ExamType, sinceMillis: Long? = null
    ): LearningProgressReport {
        val matching = records.filter {
            it.examType.equals(exam.name, true) &&
                (sinceMillis == null || (it.createdAt?.toDate()?.time ?: Long.MIN_VALUE) >= sinceMillis)
        }
        // Cloud/cache replicas share a result ID; distinct solves have different UUIDs.
        val deduplicated = matching.groupBy { record ->
            record.id.takeIf(String::isNotBlank) ?: listOf(
                record.testId, record.createdAt?.toDate()?.time, record.answersMapJson,
                record.testSnapshotJson
            ).joinToString("|")
        }.values.map { copies ->
            copies.maxByOrNull { it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }!!
        }
        val solved = deduplicated.filterNot { result ->
            runCatching { JSONObject(result.answersMapJson).optString("_status").equals("GENERATED", true) }
                .getOrDefault(!result.isSolvedAttempt)
        }.sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MIN_VALUE }
        val subjects = SoruLabCatalog.allSubjects.filter { it.examType == exam }
        val items = mutableListOf<Item>()
        var excluded = 0
        var included = 0
        val recentAttempts = mutableListOf<LearningProgressAttempt>()
        for ((index, result) in solved.withIndex()) {
            val attempt = result.id.ifBlank { "legacy-attempt-$index" }
            val snapshot = TestSnapshotCodec.decode(result.testSnapshotJson)
            val answers = runCatching { JSONObject(result.answersMapJson) }.getOrNull()
            if (snapshot == null || snapshot.questions.isEmpty() || answers == null ||
                snapshot.questions.map { it.number }.let { numbers ->
                    numbers.any { it <= 0 } || numbers.distinct().size != numbers.size
                }) {
                excluded++
                recentAttempts += LearningProgressAttempt(attempt, result, null)
                continue
            }
            included++
            val startIndex = items.size
            val headerSubject = subjects.firstOrNull { it.id == result.subjectId }
                ?: subjects.firstOrNull { result.subjectId.isBlank() && normalize(it.name) == normalize(result.subjectName) }
            for (q in snapshot.questions) {
                val subject = if (q.subject.isBlank()) headerSubject else subjects.firstOrNull {
                    it.id == q.subject || normalize(it.name) == normalize(q.subject)
                }
                val topic = subject?.let {
                    SoruLabCatalog.resolveTopicInSubject(it.id, "", q.topicHeader.ifBlank {
                        if (headerSubject?.id == it.id) result.topicName else ""
                    })
                }
                val assignment = if (subject != null && topic != null) {
                    val typed = LearningOutcomeCatalog.validated(subject.id, topic.id,
                        LearningOutcomeAssignment(q.learningOutcomeId, q.learningOutcomeName,
                            q.learningOutcomeSource, q.learningOutcomeConfidence))
                    when {
                        q.learningOutcomeId.isNotBlank() -> typed.takeIf { it.confidence >= 0.9 }
                        q.learningOutcomeSource == "manual" -> null // explicit clearing remains cleared
                        else -> LearningOutcomeCatalog.fromLabel(subject.id, topic.id, q.learningOutcome)
                            .takeIf { it.id.isNotBlank() }
                            ?: LearningOutcomeCatalog.fromLabel(subject.id, topic.id, q.questionType)
                    }?.takeIf { it.id.isNotBlank() }
                } else null
                val correct = q.correctOption.trim().uppercase(Locale.ROOT)
                val rawAnswer = answers.opt(q.number.toString())
                val absent = rawAnswer == null || rawAnswer === JSONObject.NULL
                val answer = (rawAnswer as? String)?.trim()?.uppercase(Locale.ROOT)
                val malformedAnswer = !absent && (rawAnswer !is String ||
                    (!answer.isNullOrEmpty() && answer !in options))
                val status = when {
                    !q.answerKeyVerified || correct !in options || malformedAnswer -> AnswerOutcome.UNGRADED
                    absent || answer.isNullOrEmpty() -> AnswerOutcome.BLANK
                    answer == correct -> AnswerOutcome.CORRECT
                    else -> AnswerOutcome.WRONG
                }
                // IDs are scoped by subject and source book to avoid cross-book collisions.
                val identity = (q.sourceQuestionId.ifBlank { q.id }).takeIf(String::isNotBlank)?.let {
                    listOf(subject?.id.orEmpty(), q.bookId.ifBlank { q.sourceBookTitle }, it).joinToString("|")
                }
                items += Item(attempt, identity, status, subject?.id, subject?.name ?: "Ders belirlenmedi",
                    topic?.id ?: "__unknown_topic__", topic?.name ?: "Konu belirlenmedi",
                    assignment?.id ?: "__unknown_outcome__", assignment?.name ?: "Alt kazanım belirlenmedi")
            }
            recentAttempts += LearningProgressAttempt(attempt, result, counts(items.subList(startIndex, items.size)))
        }
        val buckets = subjects.mapNotNull { subject ->
            val scoped = items.filter { it.subjectId == subject.id }
            if (scoped.isEmpty()) null else bucket(subject.id, subject.name, scoped,
                scoped.groupBy { it.topicId }.map { (topicId, topicItems) ->
                    bucket(topicId, topicItems.first().topicName, topicItems,
                        topicItems.groupBy { it.outcomeId }.map { (id, outcomeItems) ->
                            bucket(id, outcomeItems.first().outcomeName, outcomeItems,
                                classified = id != "__unknown_outcome__")
                        }.sortedWith(compareByDescending<LearningProgressBucket> { it.counts.wrong + it.counts.blank }
                            .thenBy { it.label }),
                        classified = topicId != "__unknown_topic__")
                }.sortedWith(compareByDescending<LearningProgressBucket> { it.counts.total }.thenBy { it.label }))
        }
        return LearningProgressReport(
            counts(items), included, items.mapNotNull { it.identity }.distinct().size, buckets,
            solved, recentAttempts, excluded, items.count { it.subjectId == null },
            items.count { it.outcomeId == "__unknown_outcome__" },
            matching.size - deduplicated.size
        )
    }

    private fun bucket(
        id: String, label: String, items: List<Item>,
        children: List<LearningProgressBucket> = emptyList(), classified: Boolean = true
    ) = LearningProgressBucket(id, label, counts(items), items.map { it.attempt }.distinct().size,
        items.mapNotNull { it.identity }.distinct().size,
        items.filter { it.status != AnswerOutcome.UNGRADED }.mapNotNull { it.identity }.distinct().size,
        children, classified)

    private fun counts(items: List<Item>) = ProgressCounts(
        items.count { it.status == AnswerOutcome.CORRECT },
        items.count { it.status == AnswerOutcome.WRONG },
        items.count { it.status == AnswerOutcome.BLANK },
        items.count { it.status == AnswerOutcome.UNGRADED }
    )
    private val options = setOf("A", "B", "C", "D", "E")
    private fun normalize(value: String) = LearningOutcomeCatalog.normalized(value)
}
