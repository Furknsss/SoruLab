package com.example.viewmodel

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LearningOutcomeIntegrationTest {
    @Test fun latestPublisherLabelsRemainPrimaryAndCanResolveToVideoOutcome() {
        val record = CustomQuestionRecord(subjectId = "tyt_biyoloji",
            topicId = "canlilarin_temel_bilesenleri", learningOutcome = "Enzimler",
            questionType = "Kaynak soru tipi", questionText = "protein")
        assertEquals("tyt_biyoloji-enzymes", record.resolvedLearningOutcome.id)
        assertEquals("publisher", record.resolvedLearningOutcome.source)
        assertEquals("Kaynak soru tipi", record.questionType)
    }

    @Test fun latestCloudMediaMergePreservesTypedOutcomeAlongsidePublisherMetadata() {
        val assignment = LearningOutcomeAssignment("chain-rule", "Zincir kuralı", "manual", 1.0)
        val local = CustomQuestionRecord(id = "q", userId = "student",
            subjectId = "ayt_mat", topicId = "turev_alma_kurallari",
            learningOutcome = "Zincir kuralı", questionType = "Bileşke fonksiyon",
            aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta("",
                learningOutcomeAssignment = assignment))
        val remote = local.copy(learningOutcome = "", questionType = "", aiSolutionSummary = "Bulut çözüm")
        val merged = mergeCloudQuestionsWithLocalMedia("student", listOf(remote), listOf(local)).single()
        assertEquals("Zincir kuralı", merged.learningOutcome)
        assertEquals("Bileşke fonksiyon", merged.questionType)
        assertEquals(assignment, merged.learningOutcomeAssignment)
    }

    @Test fun unmappedPublisherOutcomeDoesNotBecomeAnUnrelatedTextGuess() {
        val record = CustomQuestionRecord(subjectId = "ayt_mat", topicId = "turev_alma_kurallari",
            learningOutcome = "Çarpımın türevi", questionText = "zincir kuralı")
        assertEquals("", record.resolvedLearningOutcome.id)
    }
}
