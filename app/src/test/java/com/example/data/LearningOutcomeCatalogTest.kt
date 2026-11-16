package com.example.data

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearningOutcomeCatalogTest {
    @Test fun explicitOutcomeMetadataRoundTripsWithoutChangingFirestoreShape() {
        val assignment = LearningOutcomeAssignment("chain-rule", "Zincir kuralı", "manual", 1.0)
        val packed = CustomQuestionRecord.packSolutionWithMeta(
            "Çözüm", learningOutcome = assignment, sourceTestId = "test-7")
        val record = CustomQuestionRecord(subjectId = "ayt_mat", topicId = "turev_alma_kurallari",
            aiSolutionSummary = packed)
        assertEquals(assignment, record.learningOutcome)
        assertEquals(assignment, record.resolvedLearningOutcome)
        assertEquals("Çözüm", record.cleanSolutionSummary)
        assertEquals("test-7", record.sourceTestId)
    }

    @Test fun oldQuestionInfersFromPreservedTextEvenWhenDisplayedAsImage() {
        val old = CustomQuestionRecord(subjectId = "tyt_fizik", topicId = "hareket_tyt",
            questionText = "yer değiştirme", aiSolutionSummary = "Eski çözüm")
        assertEquals("displacement", old.resolvedLearningOutcome.id)
        assertEquals("", old.learningOutcome.id)
    }

    @Test fun wrongExamOrTopicRejectsStoredOutcome() {
        assertEquals("", LearningOutcomeCatalog.validated("tyt_mat", "temel_kavramlar",
            LearningOutcomeAssignment("chain-rule", "", "manual", 1.0)).id)
        assertEquals("", LearningOutcomeCatalog.validated("tyt_fizik", "hareket_tyt",
            LearningOutcomeAssignment("displacement", "", "model", Double.NaN)).id)
    }

    @Test fun ambiguousTextDoesNotPickFirstSkill() {
        assertEquals("", LearningOutcomeCatalog.infer("tyt_fizik", "hareket_tyt",
            "konum ve yer değiştirme").id)
        assertEquals("", LearningOutcomeCatalog.infer("tyt_fizik", "hareket_tyt", "").id)
    }

    @Test fun manualClearIsNotReplacedWithAutomaticGuess() {
        val packed = CustomQuestionRecord.packSolutionWithMeta("",
            learningOutcome = LearningOutcomeAssignment(source = "manual"))
        val record = CustomQuestionRecord(subjectId = "tyt_fizik", topicId = "hareket_tyt",
            questionText = "yer değiştirme", aiSolutionSummary = packed)
        assertEquals("", record.resolvedLearningOutcome.id)
    }

    @Test fun outcomeSurvivesTestSnapshotIncludingCloudSafeSnapshot() {
        val question = YksQuestion(id = "q", number = 1, stemTop = "", stemBottom = "",
            options = emptyMap(), correctOption = "", stepByStepSolution = "",
            learningOutcomeId = "chain-rule", learningOutcomeName = "Zincir kuralı",
            learningOutcomeSource = "manual", learningOutcomeConfidence = 1.0)
        val test = YksTestItem("t", "turev_alma_kurallari", "Test", "Test",
            DifficultyLevel.ORTA, 0.5f, questions = listOf(question))
        for (includeImage in listOf(true, false)) {
            val decoded = TestSnapshotCodec.decode(TestSnapshotCodec.encode(test, includeImage))!!
            assertEquals(question.learningOutcomeId, decoded.questions.first().learningOutcomeId)
            assertEquals(1.0, decoded.questions.first().learningOutcomeConfidence, 0.0)
        }
    }
}
