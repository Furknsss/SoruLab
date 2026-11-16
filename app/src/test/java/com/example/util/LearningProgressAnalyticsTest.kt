package com.example.util

import com.example.data.*
import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LearningProgressAnalyticsTest {
    private val topic = "Canlıların Temel Bileşenleri"
    private fun q(n: Int, verified: Boolean = true) = YksQuestion(
        id = "source-$n", number = n, subject = "Biyoloji", topicHeader = topic,
        stemTop = "", stemBottom = "", options = mapOf("A" to "a", "B" to "b"),
        correctOption = "A", answerKeyVerified = verified, stepByStepSolution = "",
        learningOutcome = "Enzimler", bookId = "book")
    private fun record(id: String, questions: List<YksQuestion>, answers: String = "{}") =
        TestResultRecord(id = id, examType = "TYT", subjectId = "tyt_biyoloji",
            subjectName = "Biyoloji", topicName = topic, testId = "same-test",
            // Deliberately stale aggregates: the snapshot must win.
            correctCount = 999, wrongCount = 999,
            answersMapJson = answers, testSnapshotJson = TestSnapshotCodec.encode(
                YksTestItem("test", "", "Test", "Test", DifficultyLevel.ORTA, 0.5f,
                    questions = questions), includeImageFallback = false))

    @Test fun verifiedAnswersAreRecomputedAndUnknownKeysNeverLowerAccuracy() {
        val report = LearningProgressAnalytics.calculate(listOf(record("a",
            listOf(q(1), q(2), q(3), q(4, false)),
            """{"1":"A","2":"B","4":"B"}""")), ExamType.TYT)
        assertEquals(ProgressCounts(1, 1, 1, 1), report.counts)
        assertEquals(33, report.counts.accuracyPercent)
        assertEquals(50, report.counts.answeredAccuracyPercent)
        assertEquals(0.75, report.counts.net!!, 0.001)
        assertEquals(1, report.attemptCount)
    }

    @Test fun mixedDenemeUsesEachQuestionSubjectAndCorrectExamScope() {
        val math = SoruLabCatalog.allSubjects.first { it.id == "tyt_mat" }
        val mixed = record("mixed", listOf(q(1), q(2).copy(
            subject = math.name, topicHeader = math.topics.first().name,
            learningOutcome = "")), """{"1":"A","2":"B"}""")
            .copy(subjectId = "", subjectName = "TYT Deneme", topicName = "TYT Deneme")
        val ayt = mixed.copy(id = "ayt", examType = "AYT")
        val report = LearningProgressAnalytics.calculate(listOf(mixed, ayt), ExamType.TYT)
        assertEquals(2, report.counts.total)
        assertEquals(setOf("tyt_biyoloji", "tyt_mat"), report.subjects.map { it.id }.toSet())
        assertEquals(1, report.subjects.single { it.id == "tyt_biyoloji" }.counts.correct)
        assertEquals(1, report.subjects.single { it.id == "tyt_mat" }.counts.wrong)
    }

    @Test fun typedOutcomeAndPublisherQuestionTypeResolveWithinTheirOwnTopic() {
        val explicit = q(1).copy(learningOutcome = "", learningOutcomeId = "tyt_biyoloji-enzymes",
            learningOutcomeSource = "manual", learningOutcomeConfidence = 1.0)
        val publisher = q(2).copy(learningOutcome = "", questionType = "Enzimler")
        val report = LearningProgressAnalytics.calculate(listOf(record("a", listOf(explicit, publisher),
            """{"1":"A","2":"A"}""")), ExamType.TYT)
        val outcome = report.subjects.single().children.single().children.single()
        assertEquals("tyt_biyoloji-enzymes", outcome.id)
        assertEquals(2, outcome.counts.correct)
    }

    @Test fun invalidOrClearedOutcomeDoesNotBecomeAnInventedSkill() {
        val invalid = q(1).copy(learningOutcomeId = "chain-rule",
            learningOutcomeSource = "manual", learningOutcomeConfidence = 1.0,
            analyticsText = "enzim")
        val cleared = q(2).copy(learningOutcomeSource = "manual")
        val unknown = q(3).copy(learningOutcome = "Publisher unmapped label", questionType = "")
        val report = LearningProgressAnalytics.calculate(
            listOf(record("a", listOf(invalid, cleared, unknown))), ExamType.TYT)
        assertEquals(3, report.unknownOutcomeCount)
        assertFalse(report.subjects.single().children.single().children.single().classified)
    }

    @Test fun duplicateCloudCacheRecordIsCountedOnceButAnotherSolveIsPractice() {
        val first = record("attempt-1", listOf(q(1)), """{"1":"A"}""")
        val second = first.copy(id = "attempt-2", answersMapJson = """{"1":"B"}""")
        val report = LearningProgressAnalytics.calculate(listOf(first, first, second), ExamType.TYT)
        assertEquals(2, report.attemptCount)
        assertEquals(1, report.uniqueQuestionCount)
        assertEquals(ProgressCounts(1, 1, 0, 0), report.counts)
        assertEquals(1, report.duplicateRecordCount)
    }

    @Test fun repeatedSingleQuestionNeverBecomesEvidenceOfBroadMastery() {
        val records = (1..20).map { record("attempt-$it", listOf(q(1)), """{"1":"A"}""") }
        val report = LearningProgressAnalytics.calculate(records, ExamType.TYT)
        assertEquals(20, report.counts.correct)
        assertEquals(1, report.uniqueQuestionCount)
        assertEquals(ProgressEvidence.INSUFFICIENT, report.subjects.single().evidence)
    }

    @Test fun strongAndWeakSignalsRequireEnoughGradedDistinctQuestions() {
        val questions = (1..10).map { q(it) }
        val answers = (1..10).joinToString(",", "{", "}") { "\"$it\":\"A\"" }
        val strong = LearningProgressAnalytics.calculate(
            listOf(record("strong", questions, answers)), ExamType.TYT)
        assertEquals(ProgressEvidence.STRONG, strong.subjects.single().children.single().children.single().evidence)
        val weak = LearningProgressAnalytics.calculate(
            listOf(record("weak", questions)), ExamType.TYT)
        assertEquals(ProgressEvidence.NEEDS_PRACTICE, weak.subjects.single().evidence)
        assertEquals(10, weak.counts.blank)
    }

    @Test fun malformedOrMissingDetailsAreVisibleButNotScoredAsLegacyTotals() {
        val valid = record("valid", listOf(q(1)), """{"1":"A"}""")
        val records = listOf(valid, valid.copy(id = "snapshot", testSnapshotJson = ""),
            valid.copy(id = "answers", answersMapJson = "not-json"),
            record("ambiguous-numbers", listOf(q(1), q(1))))
        val report = LearningProgressAnalytics.calculate(records, ExamType.TYT)
        assertEquals(1, report.counts.correct)
        assertEquals(3, report.excludedAttemptCount)
        assertEquals(4, report.recentResults.size)
    }

    @Test fun generatedTestsAreNotSolvedEvenWithWhitespaceInStatusJson() {
        val pending = record("generated", listOf(q(1)), """{ "_status": "GENERATED" }""")
        val report = LearningProgressAnalytics.calculate(listOf(pending), ExamType.TYT)
        assertEquals(0, report.attemptCount)
        assertEquals(0, report.excludedAttemptCount)
        assertTrue(report.recentResults.isEmpty())
    }

    @Test fun missingOrWrongSubjectIsNotSilentlyWrittenToHeaderSubject() {
        val qUnknown = q(1).copy(subject = "Unmapped course")
        val report = LearningProgressAnalytics.calculate(listOf(record("a", listOf(qUnknown),
            """{"1":"A"}""")), ExamType.TYT)
        assertEquals(1, report.counts.correct)
        assertEquals(1, report.unscopedQuestionCount)
        assertTrue(report.subjects.isEmpty())
    }

    @Test fun unmappedPublisherOutcomeCanUseScopedQuestionTypeWithoutInventingLabel() {
        val question = q(1).copy(learningOutcome = "Unmapped publisher label", questionType = "Enzimler")
        val report = LearningProgressAnalytics.calculate(
            listOf(record("a", listOf(question), """{"1":"A"}""")), ExamType.TYT)
        assertEquals("tyt_biyoloji-enzymes", report.subjects.single().children.single().children.single().id)
    }

    @Test fun nonemptyMalformedChoicesAreUngradedWhileMissingNullAndEmptyAreBlank() {
        val report = LearningProgressAnalytics.calculate(listOf(record("a",
            (1..7).map { q(it) },
            """{"1":"Z","2":3,"3":{},"4":null,"5":"","6":" A "}""")), ExamType.TYT)
        assertEquals(ProgressCounts(1, 0, 3, 3), report.counts)
        assertEquals(25, report.counts.accuracyPercent)
    }

    @Test fun ungradedDistinctQuestionsCannotInflateEvidenceThreshold() {
        val attempts = (1..10).map { record("attempt-$it", listOf(q(1)), """{"1":"A"}""") }
        val extra = record("ungraded", (2..5).map { q(it, verified = false) })
        val report = LearningProgressAnalytics.calculate(attempts + extra, ExamType.TYT)
        val subject = report.subjects.single()
        assertEquals(5, subject.uniqueQuestionCount)
        assertEquals(1, subject.uniqueGradedQuestionCount)
        assertEquals(ProgressEvidence.INSUFFICIENT, subject.evidence)
    }

    @Test fun dateWindowIsInclusiveAndUndatedAttemptsStayOnlyInAllTime() {
        val dated = record("dated", listOf(q(1)), """{"1":"A"}""")
            .copy(createdAt = Timestamp(Date(1000L)))
        val old = dated.copy(id = "old", createdAt = Timestamp(Date(999L)))
        val undated = dated.copy(id = "undated", createdAt = null)
        assertEquals(1, LearningProgressAnalytics.calculate(listOf(dated, old, undated),
            ExamType.TYT, sinceMillis = 1000L).attemptCount)
        assertEquals(3, LearningProgressAnalytics.calculate(listOf(dated, old, undated),
            ExamType.TYT).attemptCount)
    }
}
