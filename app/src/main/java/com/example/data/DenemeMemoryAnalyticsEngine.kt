package com.example.data

import androidx.compose.runtime.Immutable
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

enum class PaceEfficiencyStatus(val label: String) {
    FAST("Hızlı Tempo"),
    IDEAL("İdeal ÖSYM Temposu"),
    SLOW("Süre Yönetimi Geliştirilmeli"),
    NO_DATA("Süre Verisi Bekleniyor")
}

@Immutable
data class DenemeAttemptPoint(
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val correctCount: Int,
    val wrongCount: Int,
    val blankCount: Int,
    val net: Double,
    val accuracyPercent: Int,
    val durationSeconds: Int,
    val secondsPerQuestion: Double?
)

@Immutable
data class DenemeNetTrendSummary(
    val examType: ExamType,
    val denemeCount: Int,
    val totalSolvedAttemptCount: Int,
    val lastFiveAverageNet: Double?,
    val highestNetRecord: Double?,
    val highestNetExamTitle: String,
    val firstDenemeNet: Double?,
    val latestDenemeNet: Double?,
    val netGainFromFirst: Double?,
    val formattedNetGain: String,
    val accuracyPercent: Int?,
    val denemeAccuracyPercent: Int?,
    val overallAccuracyPercent: Int?,
    val avgSecondsPerQuestion: Double?,
    val avgSecondsPerQuestionRounded: Int?,
    val paceStatus: PaceEfficiencyStatus,
    val recentFiveDenemes: List<DenemeAttemptPoint>
) {
    val hasTrendComparison: Boolean
        get() = denemeCount >= 2
}

@Immutable
data class CriticalOutcomeLoss(
    val outcomeKey: String,
    val outcomeTitle: String,
    val subjectId: String,
    val subjectName: String,
    val topicId: String,
    val topicName: String,
    val correctCount: Int,
    val wrongCount: Int,
    val blankCount: Int,
    val gradedCount: Int,
    val missedCount: Int,
    val potentialNetGain: Double,
    val currentNet: Double,
    val accuracyPercent: Int,
    val isCatalogVerified: Boolean
)

@Immutable
data class QuestionTypeMasteryStat(
    val type: OsymQuestionType,
    val label: String,
    val shortTag: String,
    val correctCount: Int,
    val wrongCount: Int,
    val blankCount: Int,
    val gradedCount: Int,
    val net: Double,
    val accuracyPercent: Int?,
    val poolQuestionCount: Int
)

@Immutable
data class TopicMasteryDetail(
    val subjectId: String,
    val subjectName: String,
    val topicId: String,
    val topicName: String,
    val solvedQuestionCount: Int,
    val uniqueSolvedQuestionCount: Int,
    val coveredOutcomeCount: Int,
    val expectedOutcomeCount: Int,
    val outcomeCoveragePercent: Int,
    val coveredQuestionTypeCount: Int,
    val expectedQuestionTypeCount: Int,
    val questionTypeCoveragePercent: Int,
    val accuracyPercent: Int,
    val familiarityScore: Int,
    val masteryScore: Int
)

@Immutable
data class TopicFamiliarityMasterySummary(
    val practicedTopicCount: Int,
    val totalCatalogTopicCount: Int,
    val coveredOutcomeCount: Int,
    val targetOutcomeCount: Int,
    val outcomeCoveragePercent: Int,
    val coveredQuestionTypeCount: Int,
    val totalQuestionTypeCount: Int,
    val globalQuestionTypeCoveragePercent: Int,
    val perTopicQuestionTypeCoveragePercent: Int,
    val familiarityScore: Int,
    val masteryScore: Int,
    val poolQuestionCount: Int,
    val topics: List<TopicMasteryDetail>
)

@Immutable
data class DenemeMemoryAnalyticsReport(
    val examType: ExamType,
    val backgroundRecordCount: Int,
    val netTrend: DenemeNetTrendSummary,
    val topMissedOutcomes: List<CriticalOutcomeLoss>,
    val questionTypeMastery: List<QuestionTypeMasteryStat>,
    val topicMastery: TopicFamiliarityMasterySummary
) {
    val practicedQuestionTypes: List<QuestionTypeMasteryStat>
        get() = questionTypeMastery.filter { it.gradedCount > 0 }
}

/**
 * Analyzes the complete background memory of solved [TestResultRecord] attempts
 * (both `deneme_v147_*` / `mock_*` mock exams and topic tests) alongside the
 * [CustomQuestionRecord] pool while emitting compact, executive-ready UI summaries.
 */
object DenemeMemoryAnalyticsEngine {

    private val validOptions = setOf("A", "B", "C", "D", "E")

    private enum class GradedStatus { CORRECT, WRONG, BLANK, UNGRADED }

    private data class ResolvedOutcomeMeta(
        val key: String,
        val title: String,
        val isCatalogVerified: Boolean
    )

    private data class QuestionObservation(
        val attemptId: String,
        val isDeneme: Boolean,
        val uniqueQuestionKey: String?,
        val status: GradedStatus,
        val subjectId: String,
        val subjectName: String,
        val topicId: String,
        val topicName: String,
        val outcomeKey: String,
        val outcomeTitle: String,
        val isOutcomeVerified: Boolean,
        val questionType: OsymQuestionType
    )

    private data class EvaluatedAttempt(
        val id: String,
        val record: TestResultRecord,
        val isDeneme: Boolean,
        val createdAtOrderKey: Long,
        val createdAtMillis: Long,
        val originalIndex: Int,
        val correct: Int,
        val wrong: Int,
        val blank: Int,
        val ungraded: Int,
        val durationSeconds: Int
    ) {
        val graded: Int get() = correct + wrong + blank
        val net: Double get() = correct - wrong * 0.25
        val accuracyPercent: Int
            get() = if (graded <= 0) 0 else ((correct * 100.0) / graded).roundToInt().coerceIn(0, 100)
        val secondsPerQuestion: Double?
            get() = if (durationSeconds > 0 && graded > 0) durationSeconds.toDouble() / graded.toDouble() else null
    }

    fun formatNet(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    fun formatSignedNet(delta: Double): String {
        val cleaned = if (kotlin.math.abs(delta) < 0.005) 0.0 else delta
        return String.format(Locale.US, "%+.2f Net", cleaned)
    }

    fun isDenemeAttempt(record: TestResultRecord, decodedSnapshot: YksTestItem? = null): Boolean {
        val testId = record.testId.trim()
        if (testId.startsWith("deneme_v147_", ignoreCase = true) ||
            testId.startsWith("mock_", ignoreCase = true)
        ) {
            return true
        }
        if (decodedSnapshot != null) {
            val snapId = decodedSnapshot.id.trim()
            if (snapId.startsWith("deneme_v147_", ignoreCase = true) ||
                snapId.startsWith("mock_", ignoreCase = true) ||
                decodedSnapshot.denemeProfileVersion.isNotBlank()
            ) {
                return true
            }
        }
        return record.subjectId.isBlank() &&
            record.testTitle.contains("deneme", ignoreCase = true)
    }

    fun analyze(
        records: List<TestResultRecord>,
        examType: ExamType,
        customQuestions: List<CustomQuestionRecord> = emptyList(),
        sinceMillis: Long? = null,
        customQuestionsCountHint: Int = 0
    ): DenemeMemoryAnalyticsReport {
        val subjects = SoruLabCatalog.allSubjects.filter { it.examType == examType }
        val matchingRecords = records.filter { record ->
            record.examType.equals(examType.name, ignoreCase = true) &&
                (sinceMillis == null || (record.createdAt?.toDate()?.time ?: Long.MIN_VALUE) >= sinceMillis)
        }

        // Deduplicate cloud/local cache replicas by document ID (or composite signature if blank).
        val deduplicated = matchingRecords.groupBy { record ->
            record.id.takeIf(String::isNotBlank) ?: listOf(
                record.testId,
                record.createdAt?.seconds ?: -1L,
                record.createdAt?.nanoseconds ?: -1,
                record.answersMapJson,
                record.testSnapshotJson
            ).joinToString("|")
        }.values.map { replicas ->
            replicas.maxByOrNull { replica ->
                replica.updatedAt?.toDate()?.time ?: replica.createdAt?.toDate()?.time ?: 0L
            }!!
        }

        val solvedRecords = deduplicated.filter { record ->
            val generatedPlaceholder = runCatching {
                JSONObject(record.answersMapJson).optString("_status").equals("GENERATED", ignoreCase = true)
            }.getOrDefault(!record.isSolvedAttempt)
            !generatedPlaceholder && record.isSolvedAttempt
        }

        val observations = mutableListOf<QuestionObservation>()
        val evaluatedAttempts = mutableListOf<EvaluatedAttempt>()

        solvedRecords.forEachIndexed { index, record ->
            val attemptId = record.id.ifBlank { "attempt_$index" }
            val snapshot = if (record.testSnapshotJson.isNotBlank()) {
                TestSnapshotCodec.decode(record.testSnapshotJson)
            } else {
                null
            }
            val answersJson = runCatching { JSONObject(record.answersMapJson) }.getOrNull()
            val hasValidSnapshot = snapshot != null &&
                snapshot.questions.isNotEmpty() &&
                answersJson != null &&
                snapshot.questions.map { it.number }.let { nums ->
                    nums.all { it > 0 } && nums.distinct().size == nums.size
                }

            val denemeFlag = isDenemeAttempt(record, snapshot)
            val createdMillis = record.createdAt?.toDate()?.time ?: Long.MIN_VALUE
            val orderKey = record.createdAt?.let { ts ->
                ts.seconds * 1_000_000_000L + ts.nanoseconds.toLong()
            } ?: (Long.MIN_VALUE + index)

            if (hasValidSnapshot && snapshot != null && answersJson != null) {
                val headerSubject = subjects.firstOrNull { it.id == record.subjectId }
                    ?: subjects.firstOrNull {
                        record.subjectId.isBlank() &&
                            normalize(it.name) == normalize(record.subjectName)
                    }

                var attemptCorrect = 0
                var attemptWrong = 0
                var attemptBlank = 0
                var attemptUngraded = 0

                for (q in snapshot.questions) {
                    val subject = if (q.subject.isBlank()) {
                        headerSubject
                    } else {
                        subjects.firstOrNull {
                            it.id == q.subject || normalize(it.name) == normalize(q.subject)
                        } ?: headerSubject?.takeIf { denemeFlag && subjects.size == 1 }
                    }

                    val topic = subject?.let { subj ->
                        val topicIdCandidate = if (headerSubject?.id == subj.id && q.topicHeader.isBlank()) {
                            record.topicId
                        } else {
                            ""
                        }
                        val topicNameCandidate = q.topicHeader.ifBlank {
                            if (headerSubject?.id == subj.id) record.topicName else ""
                        }
                        SoruLabCatalog.resolveTopicInSubject(subj.id, topicIdCandidate, topicNameCandidate)
                    }

                    val correctOpt = q.correctOption.trim().uppercase(Locale.ROOT)
                    val rawAnswer = answersJson.opt(q.number.toString())
                    val isAbsent = rawAnswer == null || rawAnswer === JSONObject.NULL
                    val normalizedAnswer = (rawAnswer as? String)?.trim()?.uppercase(Locale.ROOT)
                    val malformed = !isAbsent && (
                        rawAnswer !is String ||
                            (!normalizedAnswer.isNullOrEmpty() && normalizedAnswer !in validOptions)
                        )

                    val status = when {
                        !q.answerKeyVerified || correctOpt !in validOptions || malformed -> {
                            attemptUngraded++
                            GradedStatus.UNGRADED
                        }
                        isAbsent || normalizedAnswer.isNullOrEmpty() -> {
                            attemptBlank++
                            GradedStatus.BLANK
                        }
                        normalizedAnswer == correctOpt -> {
                            attemptCorrect++
                            GradedStatus.CORRECT
                        }
                        else -> {
                            attemptWrong++
                            GradedStatus.WRONG
                        }
                    }

                    val resolvedSubjectId = subject?.id ?: record.subjectId.ifBlank { "unscoped_${examType.name.lowercase(Locale.ROOT)}" }
                    val resolvedSubjectName = subject?.name
                        ?: q.subject.takeIf(String::isNotBlank)
                        ?: record.subjectName.takeIf(String::isNotBlank)
                        ?: examType.name
                    val resolvedTopicId = topic?.id ?: record.topicId.takeIf(String::isNotBlank)
                        ?: normalizeKey(q.topicHeader.ifBlank { record.topicName.ifBlank { "genel_konu" } })
                    val resolvedTopicName = topic?.name
                        ?: q.topicHeader.takeIf(String::isNotBlank)
                        ?: record.topicName.takeIf(String::isNotBlank)
                        ?: "Genel Konu"

                    val resolvedOutcome = resolveQuestionOutcome(
                        question = q,
                        subject = subject,
                        topic = topic,
                        fallbackSubjectId = resolvedSubjectId,
                        fallbackTopicId = resolvedTopicId,
                        fallbackTopicName = resolvedTopicName
                    )
                    val questionType = q.resolvedOsymQuestionType
                    val uniqueKey = (q.sourceQuestionId.ifBlank { q.id }).takeIf(String::isNotBlank)?.let { qId ->
                        "$resolvedSubjectId|${q.bookId.ifBlank { q.sourceBookTitle }}|$qId"
                    }

                    observations += QuestionObservation(
                        attemptId = attemptId,
                        isDeneme = denemeFlag,
                        uniqueQuestionKey = uniqueKey,
                        status = status,
                        subjectId = resolvedSubjectId,
                        subjectName = resolvedSubjectName,
                        topicId = resolvedTopicId,
                        topicName = resolvedTopicName,
                        outcomeKey = resolvedOutcome.key,
                        outcomeTitle = resolvedOutcome.title,
                        isOutcomeVerified = resolvedOutcome.isCatalogVerified,
                        questionType = questionType
                    )
                }

                evaluatedAttempts += EvaluatedAttempt(
                    id = attemptId,
                    record = record,
                    isDeneme = denemeFlag,
                    createdAtOrderKey = orderKey,
                    createdAtMillis = createdMillis,
                    originalIndex = index,
                    correct = attemptCorrect,
                    wrong = attemptWrong,
                    blank = attemptBlank,
                    ungraded = attemptUngraded,
                    durationSeconds = record.durationSeconds.coerceAtLeast(0)
                )
            } else if (record.testSnapshotJson.isBlank() &&
                (record.correctCount + record.wrongCount + record.emptyCount) > 0
            ) {
                // Header-only lightweight record fallback (only when snapshot JSON is absent, never when malformed).
                evaluatedAttempts += EvaluatedAttempt(
                    id = attemptId,
                    record = record,
                    isDeneme = denemeFlag,
                    createdAtOrderKey = orderKey,
                    createdAtMillis = createdMillis,
                    originalIndex = index,
                    correct = record.correctCount.coerceAtLeast(0),
                    wrong = record.wrongCount.coerceAtLeast(0),
                    blank = record.emptyCount.coerceAtLeast(0),
                    ungraded = record.ungradedCount.coerceAtLeast(0),
                    durationSeconds = record.durationSeconds.coerceAtLeast(0)
                )
            }
        }

        val examCustomPool = customQuestions
            .filter { it.examType.equals(examType.name, ignoreCase = true) }
            .distinctBy { it.id.ifBlank { "${it.subjectId}|${it.topicId}|${it.questionText.hashCode()}" } }

        val netTrend = buildNetTrendSummary(
            examType = examType,
            attempts = evaluatedAttempts
        )
        val topMissed = buildTopMissedOutcomes(observations)
        val typeMastery = buildQuestionTypeMastery(observations, examCustomPool)
        val topicMastery = buildTopicMasterySummary(
            examType = examType,
            subjects = subjects,
            observations = observations,
            examPool = examCustomPool,
            customQuestionsCountHint = customQuestionsCountHint
        )

        return DenemeMemoryAnalyticsReport(
            examType = examType,
            backgroundRecordCount = evaluatedAttempts.size,
            netTrend = netTrend,
            topMissedOutcomes = topMissed,
            questionTypeMastery = typeMastery,
            topicMastery = topicMastery
        )
    }

    private fun buildNetTrendSummary(
        examType: ExamType,
        attempts: List<EvaluatedAttempt>
    ): DenemeNetTrendSummary {
        val chronologicalAll = attempts
            .filter { it.graded > 0 }
            .sortedWith(
                compareBy<EvaluatedAttempt> { if (it.createdAtMillis == Long.MIN_VALUE) 0 else 1 }
                    .thenBy { it.createdAtOrderKey }
                    .thenBy { it.originalIndex }
            )

        val chronologicalDenemes = chronologicalAll.filter { it.isDeneme }
        val recentFive = chronologicalDenemes.takeLast(5)

        val lastFiveAvg = recentFive
            .takeIf { it.isNotEmpty() }
            ?.map { it.net }
            ?.average()

        val bestAttempt = chronologicalDenemes.maxWithOrNull(
            compareBy<EvaluatedAttempt> { it.net }.thenBy { it.accuracyPercent }
        )
        val firstNet = chronologicalDenemes.firstOrNull()?.net
        val latestNet = chronologicalDenemes.lastOrNull()?.net
        val netGain = if (firstNet != null && latestNet != null) {
            latestNet - firstNet
        } else {
            null
        }

        val denemeGradedTotal = chronologicalDenemes.sumOf { it.graded }
        val denemeCorrectTotal = chronologicalDenemes.sumOf { it.correct }
        val denemeAccuracy = if (denemeGradedTotal > 0) {
            ((denemeCorrectTotal * 100.0) / denemeGradedTotal).roundToInt().coerceIn(0, 100)
        } else {
            null
        }

        val overallGradedTotal = chronologicalAll.sumOf { it.graded }
        val overallCorrectTotal = chronologicalAll.sumOf { it.correct }
        val overallAccuracy = if (overallGradedTotal > 0) {
            ((overallCorrectTotal * 100.0) / overallGradedTotal).roundToInt().coerceIn(0, 100)
        } else {
            null
        }

        val timedDenemes = chronologicalDenemes.filter { it.durationSeconds > 0 && it.graded > 0 }
        val denemeSecPerQ = if (timedDenemes.isNotEmpty()) {
            timedDenemes.sumOf { it.durationSeconds }.toDouble() / timedDenemes.sumOf { it.graded }.toDouble()
        } else {
            null
        }

        val timedAll = chronologicalAll.filter { it.durationSeconds > 0 && it.graded > 0 }
        val overallSecPerQ = if (timedAll.isNotEmpty()) {
            timedAll.sumOf { it.durationSeconds }.toDouble() / timedAll.sumOf { it.graded }.toDouble()
        } else {
            null
        }

        val effectiveSecPerQ = denemeSecPerQ ?: overallSecPerQ
        val paceStatus = classifyPace(examType, effectiveSecPerQ)

        val points = recentFive.map { attempt ->
            DenemeAttemptPoint(
                id = attempt.id,
                title = attempt.record.testTitle.ifBlank { "${examType.name} Denemesi" },
                createdAtMillis = attempt.createdAtMillis,
                correctCount = attempt.correct,
                wrongCount = attempt.wrong,
                blankCount = attempt.blank,
                net = attempt.net,
                accuracyPercent = attempt.accuracyPercent,
                durationSeconds = attempt.durationSeconds,
                secondsPerQuestion = attempt.secondsPerQuestion
            )
        }

        return DenemeNetTrendSummary(
            examType = examType,
            denemeCount = chronologicalDenemes.size,
            totalSolvedAttemptCount = chronologicalAll.size,
            lastFiveAverageNet = lastFiveAvg,
            highestNetRecord = bestAttempt?.net,
            highestNetExamTitle = bestAttempt?.record?.testTitle.orEmpty(),
            firstDenemeNet = firstNet,
            latestDenemeNet = latestNet,
            netGainFromFirst = netGain,
            formattedNetGain = netGain?.let(::formatSignedNet) ?: "—",
            accuracyPercent = denemeAccuracy ?: overallAccuracy,
            denemeAccuracyPercent = denemeAccuracy,
            overallAccuracyPercent = overallAccuracy,
            avgSecondsPerQuestion = effectiveSecPerQ,
            avgSecondsPerQuestionRounded = effectiveSecPerQ?.roundToInt()?.coerceAtLeast(1),
            paceStatus = paceStatus,
            recentFiveDenemes = points
        )
    }

    private fun classifyPace(examType: ExamType, secondsPerQuestion: Double?): PaceEfficiencyStatus {
        if (secondsPerQuestion == null || !secondsPerQuestion.isFinite() || secondsPerQuestion <= 0.0) {
            return PaceEfficiencyStatus.NO_DATA
        }
        return when (examType) {
            ExamType.TYT -> when {
                secondsPerQuestion <= 48.0 -> PaceEfficiencyStatus.FAST
                secondsPerQuestion <= 85.0 -> PaceEfficiencyStatus.IDEAL
                else -> PaceEfficiencyStatus.SLOW
            }
            ExamType.AYT -> when {
                secondsPerQuestion <= 65.0 -> PaceEfficiencyStatus.FAST
                secondsPerQuestion <= 125.0 -> PaceEfficiencyStatus.IDEAL
                else -> PaceEfficiencyStatus.SLOW
            }
        }
    }

    private fun buildTopMissedOutcomes(
        observations: List<QuestionObservation>
    ): List<CriticalOutcomeLoss> {
        val graded = observations.filter { it.status != GradedStatus.UNGRADED }
        if (graded.isEmpty()) return emptyList()

        return graded
            .groupBy { "${it.subjectId}|${it.topicId}|${it.outcomeKey}" }
            .mapNotNull { (_, group) ->
                val correct = group.count { it.status == GradedStatus.CORRECT }
                val wrong = group.count { it.status == GradedStatus.WRONG }
                val blank = group.count { it.status == GradedStatus.BLANK }
                val missed = wrong + blank
                if (missed <= 0) return@mapNotNull null

                val total = correct + wrong + blank
                val representative = group.firstOrNull { it.isOutcomeVerified } ?: group.first()
                // Every wrong answer costs 1.25 net vs correct (+1.00 missed + 0.25 penalty); blank costs 1.00 net.
                val potentialNetGain = wrong * 1.25 + blank * 1.0
                val currentNet = correct - wrong * 0.25
                val accuracy = ((correct * 100.0) / total).roundToInt().coerceIn(0, 100)

                CriticalOutcomeLoss(
                    outcomeKey = representative.outcomeKey,
                    outcomeTitle = representative.outcomeTitle,
                    subjectId = representative.subjectId,
                    subjectName = representative.subjectName,
                    topicId = representative.topicId,
                    topicName = representative.topicName,
                    correctCount = correct,
                    wrongCount = wrong,
                    blankCount = blank,
                    gradedCount = total,
                    missedCount = missed,
                    potentialNetGain = potentialNetGain,
                    currentNet = currentNet,
                    accuracyPercent = accuracy,
                    isCatalogVerified = group.any { it.isOutcomeVerified }
                )
            }
            .sortedWith(
                compareByDescending<CriticalOutcomeLoss> { it.potentialNetGain }
                    .thenByDescending { it.wrongCount }
                    .thenByDescending { it.blankCount }
                    .thenBy { it.accuracyPercent }
                    .thenBy { it.outcomeTitle }
            )
            .take(5)
    }

    private fun buildQuestionTypeMastery(
        observations: List<QuestionObservation>,
        examPool: List<CustomQuestionRecord>
    ): List<QuestionTypeMasteryStat> {
        val gradedByType = observations
            .filter { it.status != GradedStatus.UNGRADED }
            .groupBy { it.questionType }

        val poolCountByType = examPool.groupingBy { record ->
            OsymQuestionType.classifyCustomQuestion(record)
        }.eachCount()

        return OsymQuestionType.entries.map { type ->
            val items = gradedByType[type].orEmpty()
            val correct = items.count { it.status == GradedStatus.CORRECT }
            val wrong = items.count { it.status == GradedStatus.WRONG }
            val blank = items.count { it.status == GradedStatus.BLANK }
            val graded = correct + wrong + blank
            val accuracy = if (graded > 0) {
                ((correct * 100.0) / graded).roundToInt().coerceIn(0, 100)
            } else {
                null
            }
            QuestionTypeMasteryStat(
                type = type,
                label = type.label,
                shortTag = type.shortTag,
                correctCount = correct,
                wrongCount = wrong,
                blankCount = blank,
                gradedCount = graded,
                net = correct - wrong * 0.25,
                accuracyPercent = accuracy,
                poolQuestionCount = poolCountByType[type] ?: 0
            )
        }.sortedWith(
            compareByDescending<QuestionTypeMasteryStat> { it.gradedCount > 0 }
                .thenByDescending { it.gradedCount }
                .thenBy { it.type.ordinal }
        )
    }

    private fun buildTopicMasterySummary(
        examType: ExamType,
        subjects: List<YksSubject>,
        observations: List<QuestionObservation>,
        examPool: List<CustomQuestionRecord>,
        customQuestionsCountHint: Int
    ): TopicFamiliarityMasterySummary {
        val totalCatalogTopics = subjects.sumOf { it.topics.size }
        val totalQuestionTypeCount = OsymQuestionType.entries.size
        val catalogSubjectIds = subjects.map { it.id }.toSet()

        val gradedObservations = observations.filter { it.status != GradedStatus.UNGRADED }
        if (gradedObservations.isEmpty()) {
            return TopicFamiliarityMasterySummary(
                practicedTopicCount = 0,
                totalCatalogTopicCount = totalCatalogTopics,
                coveredOutcomeCount = 0,
                targetOutcomeCount = 0,
                outcomeCoveragePercent = 0,
                coveredQuestionTypeCount = 0,
                totalQuestionTypeCount = totalQuestionTypeCount,
                globalQuestionTypeCoveragePercent = 0,
                perTopicQuestionTypeCoveragePercent = 0,
                familiarityScore = 0,
                masteryScore = 0,
                poolQuestionCount = examPool.size.takeIf { it > 0 } ?: customQuestionsCountHint.coerceAtLeast(0),
                topics = emptyList()
            )
        }

        val topicGroups = gradedObservations.groupBy { "${it.subjectId}|${it.topicId}" }
        val topicDetails = topicGroups.map { (_, items) ->
            val first = items.first()
            val subjectId = first.subjectId
            val topicId = first.topicId

            val catalogOutcomes = if (subjectId in catalogSubjectIds) {
                LearningOutcomeCatalog.candidates(subjectId, topicId).map { normalizeKey(it.id) }
            } else {
                emptyList()
            }
            val taxonomyOutcomes = OsymKazanimTaxonomy.allKazanimlar
                .filter { it.examType == examType && it.subjectId == subjectId && it.topicId == topicId }
                .map { normalizeKey(it.id) }
            val poolOutcomes = examPool
                .filter { it.subjectId == subjectId && it.topicId == topicId }
                .mapNotNull { rec ->
                    rec.resolvedLearningOutcome.id.ifBlank { rec.learningOutcome }
                        .takeIf(String::isNotBlank)
                        ?.let(::normalizeKey)
                }

            val observedOutcomeKeys = items.map { normalizeKey(it.outcomeKey) }.distinct()
            val knownUniverse = (catalogOutcomes + taxonomyOutcomes + poolOutcomes).distinct()
            val expectedOutcomeCount = maxOf(
                knownUniverse.size.coerceAtLeast(3),
                observedOutcomeKeys.size
            )
            val coveredOutcomeCount = observedOutcomeKeys.size.coerceAtMost(expectedOutcomeCount)
            val outcomeCoverageRatio = coveredOutcomeCount.toDouble() / expectedOutcomeCount.toDouble()
            val outcomeCoveragePct = (outcomeCoverageRatio * 100.0).roundToInt().coerceIn(0, 100)

            val distinctTypes = items.map { it.questionType }.distinct().size
            val typeCoverageRatio = (distinctTypes.toDouble() / totalQuestionTypeCount.toDouble()).coerceIn(0.0, 1.0)
            val typeCoveragePct = (typeCoverageRatio * 100.0).roundToInt().coerceIn(0, 100)

            val correct = items.count { it.status == GradedStatus.CORRECT }
            val accuracyRatio = (correct.toDouble() / items.size.toDouble()).coerceIn(0.0, 1.0)
            val accuracyPct = (accuracyRatio * 100.0).roundToInt().coerceIn(0, 100)

            val familiarity = ((outcomeCoverageRatio * 0.55 + typeCoverageRatio * 0.45) * 100.0)
                .roundToInt()
                .coerceIn(0, 100)
            val mastery = ((outcomeCoverageRatio * 0.35 + typeCoverageRatio * 0.25 + accuracyRatio * 0.40) * 100.0)
                .roundToInt()
                .coerceIn(0, 100)

            TopicMasteryDetail(
                subjectId = subjectId,
                subjectName = first.subjectName,
                topicId = topicId,
                topicName = first.topicName,
                solvedQuestionCount = items.size,
                uniqueSolvedQuestionCount = items.mapNotNull { it.uniqueQuestionKey }.distinct().size
                    .takeIf { it > 0 } ?: items.size,
                coveredOutcomeCount = coveredOutcomeCount,
                expectedOutcomeCount = expectedOutcomeCount,
                outcomeCoveragePercent = outcomeCoveragePct,
                coveredQuestionTypeCount = distinctTypes,
                expectedQuestionTypeCount = totalQuestionTypeCount,
                questionTypeCoveragePercent = typeCoveragePct,
                accuracyPercent = accuracyPct,
                familiarityScore = familiarity,
                masteryScore = mastery
            )
        }.sortedWith(
            compareByDescending<TopicMasteryDetail> { it.masteryScore }
                .thenByDescending { it.solvedQuestionCount }
                .thenBy { it.topicName }
        )

        val totalCoveredOutcomes = topicDetails.sumOf { it.coveredOutcomeCount }
        val totalExpectedOutcomes = topicDetails.sumOf { it.expectedOutcomeCount }.coerceAtLeast(1)
        val outcomeCoveragePct = ((totalCoveredOutcomes * 100.0) / totalExpectedOutcomes)
            .roundToInt()
            .coerceIn(0, 100)

        val globalDistinctTypes = gradedObservations.map { it.questionType }.distinct().size
        val globalTypePct = ((globalDistinctTypes * 100.0) / totalQuestionTypeCount)
            .roundToInt()
            .coerceIn(0, 100)
        val perTopicTypePct = topicDetails
            .map { it.questionTypeCoveragePercent }
            .average()
            .roundToInt()
            .coerceIn(0, 100)

        val avgFamiliarity = topicDetails.map { it.familiarityScore }.average().roundToInt().coerceIn(0, 100)
        val avgMastery = topicDetails.map { it.masteryScore }.average().roundToInt().coerceIn(0, 100)

        return TopicFamiliarityMasterySummary(
            practicedTopicCount = topicDetails.size,
            totalCatalogTopicCount = totalCatalogTopics,
            coveredOutcomeCount = totalCoveredOutcomes,
            targetOutcomeCount = totalExpectedOutcomes,
            outcomeCoveragePercent = outcomeCoveragePct,
            coveredQuestionTypeCount = globalDistinctTypes,
            totalQuestionTypeCount = totalQuestionTypeCount,
            globalQuestionTypeCoveragePercent = globalTypePct,
            perTopicQuestionTypeCoveragePercent = perTopicTypePct,
            familiarityScore = avgFamiliarity,
            masteryScore = avgMastery,
            poolQuestionCount = examPool.size.takeIf { it > 0 } ?: customQuestionsCountHint.coerceAtLeast(0),
            topics = topicDetails
        )
    }

    private fun resolveQuestionOutcome(
        question: YksQuestion,
        subject: YksSubject?,
        topic: YksTopic?,
        fallbackSubjectId: String,
        fallbackTopicId: String,
        fallbackTopicName: String
    ): ResolvedOutcomeMeta {
        // 1. Check OsymKazanimTaxonomy by ID, official code, or title
        val taxonomyMatch = OsymKazanimTaxonomy.allKazanimlar.firstOrNull { kaz ->
            (question.learningOutcomeId.isNotBlank() && kaz.id.equals(question.learningOutcomeId, ignoreCase = true)) ||
                (question.learningOutcome.isNotBlank() && kaz.code.equals(question.learningOutcome, ignoreCase = true)) ||
                (question.learningOutcomeName.isNotBlank() && normalize(kaz.title) == normalize(question.learningOutcomeName))
        }
        if (taxonomyMatch != null) {
            return ResolvedOutcomeMeta(
                key = taxonomyMatch.id,
                title = taxonomyMatch.title,
                isCatalogVerified = true
            )
        }

        // 2. Check LearningOutcomeCatalog within resolved subject & topic
        if (subject != null && topic != null) {
            val validated = if (question.learningOutcomeId.isNotBlank()) {
                LearningOutcomeCatalog.validated(
                    subject.id,
                    topic.id,
                    LearningOutcomeAssignment(
                        id = question.learningOutcomeId,
                        name = question.learningOutcomeName,
                        source = question.learningOutcomeSource.ifBlank { "publisher" },
                        confidence = question.learningOutcomeConfidence.takeIf { it > 0.0 } ?: 1.0
                    )
                ).takeIf { it.id.isNotBlank() }
            } else {
                null
            }

            val catalogMatch = validated
                ?: LearningOutcomeCatalog.fromLabel(subject.id, topic.id, question.learningOutcome)
                    .takeIf { it.id.isNotBlank() }
                ?: LearningOutcomeCatalog.fromLabel(subject.id, topic.id, question.learningOutcomeName)
                    .takeIf { it.id.isNotBlank() }
                ?: LearningOutcomeCatalog.fromLabel(subject.id, topic.id, question.questionType)
                    .takeIf { it.id.isNotBlank() }
                ?: LearningOutcomeCatalog.infer(
                    subject.id,
                    topic.id,
                    listOf(question.stemTop, question.analyticsText, question.stemBottom).joinToString(" ")
                ).takeIf { it.id.isNotBlank() }

            if (catalogMatch != null) {
                return ResolvedOutcomeMeta(
                    key = catalogMatch.id,
                    title = catalogMatch.name,
                    isCatalogVerified = true
                )
            }
        }

        // 3. Explicit human-readable outcome label supplied by Deneme engine or publisher metadata
        val cleanName = question.learningOutcomeName.trim()
        if (cleanName.isNotBlank() && !cleanName.equals("Alt kazanım belirlenmedi", ignoreCase = true)) {
            val key = question.learningOutcomeId.trim().ifBlank { normalizeKey(cleanName) }
            return ResolvedOutcomeMeta(
                key = key,
                title = cleanName,
                isCatalogVerified = true
            )
        }

        val cleanRawOutcome = question.learningOutcome.trim()
        if (cleanRawOutcome.isNotBlank() &&
            OsymQuestionType.fromRawLabel(cleanRawOutcome) == null &&
            !cleanRawOutcome.equals("Alt kazanım belirlenmedi", ignoreCase = true)
        ) {
            return ResolvedOutcomeMeta(
                key = question.learningOutcomeId.trim().ifBlank { normalizeKey(cleanRawOutcome) },
                title = cleanRawOutcome,
                isCatalogVerified = true
            )
        }

        // 4. Deterministic topic-level fallback so unannotated mistakes remain visible
        return ResolvedOutcomeMeta(
            key = "$fallbackSubjectId:$fallbackTopicId:core",
            title = "$fallbackTopicName • Temel ÖSYM Kazanımı",
            isCatalogVerified = false
        )
    }

    private fun normalize(value: String): String = LearningOutcomeCatalog.normalized(value)

    private fun normalizeKey(value: String): String =
        normalize(value).replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "outcome" }
}
