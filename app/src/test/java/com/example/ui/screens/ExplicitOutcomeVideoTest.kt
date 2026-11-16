package com.example.ui.screens

import com.example.data.YksQuestion
import org.junit.Assert.*
import org.junit.Test

class ExplicitOutcomeVideoTest {
    private fun question(id: String, confidence: Double = 1.0) = YksQuestion(
        id = "image-q", number = 1, topicHeader = "Canlıların Temel Bileşenleri",
        stemTop = "", stemBottom = "", options = emptyMap(), correctOption = "",
        stepByStepSolution = "", learningOutcomeId = id, learningOutcomeSource = "manual",
        learningOutcomeConfidence = confidence)

    @Test fun croppedImageWithNoVisibleTextUsesSavedOutcomeTimestamp() {
        val video = SmartLessonCatalog.find(question("tyt_biyoloji-enzymes"),
            "TYT", "Biyoloji", "").single()
        assertEquals("35lqUJGwj3Y", video.videoId)
        assertEquals(8407, video.startSeconds)
        assertEquals(10477, video.endSeconds)
    }

    @Test fun savedOutcomeTimestampReachesActualPlayerHtmlAndExternalFallback() {
        val video = SmartLessonCatalog.find(question("tyt_biyoloji-enzymes"),
            "TYT", "Biyoloji", "").single()
        val html = buildYoutubeEmbedHtml(video.videoId, video.startSeconds, video.endSeconds)
        assertTrue(html.contains("requestedStart=8407"))
        assertTrue(html.contains("start:8407,end:10477"))
        assertTrue(html.contains("seekTo(requestedStart,true)"))
        assertTrue(video.watchUrl.endsWith("&t=8407s"))
        assertEquals("2:20:07", formatLessonTimestamp(video.startSeconds))
    }

    @Test fun missingUnknownOrLowConfidenceOutcomeNeverOpensFirstChapter() {
        for (q in listOf(question(""), question("unknown"),
            question("tyt_biyoloji-enzymes", 0.4), question("chain-rule"))) {
            assertTrue(SmartLessonCatalog.find(q, "TYT", "Biyoloji", "").isEmpty())
        }
    }

    @Test fun latestDenemeContextUsesQuestionSubjectAndPublisherOutcomeLabel() {
        val source = question("").copy(subject = "Biyoloji", learningOutcome = "Enzimler",
            learningOutcomeSource = "", learningOutcomeConfidence = 0.0)
        val selected = SmartLessonCatalog.find(source, "TYT", "TYT Deneme", "").single()
        assertEquals("tyt_biyoloji-enzymes", selected.outcomeId)
        assertEquals(8407, selected.startSeconds)
    }

    @Test fun clearingPublisherOnlyOutcomeCannotFallBackToItsOriginalLabel() {
        val publisher = question("").copy(subject = "Biyoloji", learningOutcome = "Enzimler",
            learningOutcomeSource = "", learningOutcomeConfidence = 0.0)
        val cleared = lessonQuestionWithOverride(publisher, "", "")
        assertEquals("manual", cleared.learningOutcomeSource)
        assertTrue(SmartLessonCatalog.find(cleared, "TYT", "Biyoloji", "").isEmpty())
        assertEquals(publisher, lessonQuestionWithOverride(publisher, null, ""))
    }

    @Test fun reselectingAnExistingTypedOutcomeOverridesLowConfidence() {
        val existing = question("tyt_biyoloji-enzymes", 0.4)
        val selected = lessonQuestionWithOverride(existing, existing.learningOutcomeId, "Enzimler")
        assertEquals(8407, SmartLessonCatalog.find(selected, "TYT", "Biyoloji", "").single().startSeconds)
    }

    @Test fun explicitManualClearStillOverridesAnExistingPublisherLabel() {
        val cleared = question("").copy(subject = "Biyoloji", learningOutcome = "Enzimler")
        assertTrue(SmartLessonCatalog.find(cleared, "TYT", "TYT Deneme", "").isEmpty())
    }
}
