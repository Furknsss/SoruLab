package com.example.viewmodel

import com.example.data.CustomQuestionRecord
import com.example.data.YksTopic
import org.junit.Assert.*
import org.junit.Test

class TopicIntegrityPolicyTest {
    private val endocrine = YksTopic(
        id = "endokrin_sistem", subjectId = "ayt_biyoloji",
        name = "Endokrin Sistem", questionCount = 0, completedTests = 0,
        totalTests = 0, badgeColorHex = 0xFF0A55AAL, iconType = "bio",
        tests = emptyList()
    )

    private fun record(text: String, sourceTest: String = "") = CustomQuestionRecord(
        id = "q_1", subjectId = "ayt_biyoloji", subjectName = "Biyoloji",
        topicId = "endokrin_sistem", topicName = "Endokrin Sistem",
        questionText = text,
        aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta(
            solutionText = "", sourceTestId = sourceTest
        )
    )

    @Test fun yulafColeoptileProblemCannotEnterEndocrineTest() {
        assertFalse(TopicIntegrityPolicy.isSuitable(record(
            "Aşağıda yulaf koleoptillerine uygulanan işlemler ve " +
                "aydınlanma durumları gösterilmiştir. Filiz ucu kesilerek agar kullanılmıştır."
        ), endocrine))
        assertFalse(TopicIntegrityPolicy.isSuitable(record(
            "Bitki ışığa doğru yöneliyor, fototropizma gerçekleşiyor."
        ), endocrine))
    }

    @Test fun humanInsulinHormoneQuestionCanEnterEndocrineTest() {
        assertTrue(TopicIntegrityPolicy.isSuitable(record(
            "Pankreasın beta hücrelerinden salgılanan insülin " +
                "kan glikoz düzeyini nasıl düzenler?"
        ), endocrine))
    }

    @Test fun explicitlyPrintedPlantChapterBeatsStaleEndocrineZipLabel() {
        assertFalse(TopicIntegrityPolicy.isSuitable(record(
            "Aşağıdaki şekilde süreç gösterilmiştir",
            "Bitki Biyolojisi~Test 5"
        ), endocrine))
    }

    @Test fun mismatchedSubjectOrTopicIsAlwaysRejected() {
        assertFalse(TopicIntegrityPolicy.isSuitable(
            record("insülin").copy(subjectId = "ayt_matematik"), endocrine
        ))
        assertFalse(TopicIntegrityPolicy.isSuitable(
            record("insülin").copy(topicId = "sinir_sistemi"), endocrine
        ))
    }
}
