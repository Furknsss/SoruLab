package com.example.ui.screens

import com.example.data.YksQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartLessonCatalogTest {
    private fun question(topic: String, stem: String = "") = YksQuestion(
        id = "test-q1",
        number = 1,
        topicHeader = topic,
        stemTop = stem,
        stemBottom = "",
        options = emptyMap(),
        correctOption = "",
        stepByStepSolution = ""
    )

    @Test
    fun chainRuleSelectsSpecificTeachingVideoNotInventedTimestamp() {
        val videos = SmartLessonCatalog.find(
            question("Türev Alma Kuralları", "Bileşke fonksiyon için zincir kuralı"),
            "AYT", "Matematik", "Türev Alma Kuralları"
        )
        assertEquals("Bıyıklı Matematik", videos.first().teacher)
        assertEquals("EImXqB-nEqo", videos.first().videoId)
        assertEquals(0, videos.first().startSeconds)
    }

    @Test
    fun unspecifiedDerivativeSkillStartsGeneralVerifiedVideoFromBeginning() {
        val videos = SmartLessonCatalog.find(
            question("Türev Alma Kuralları"), "AYT", "Matematik", "Türev Alma Kuralları"
        )
        assertEquals("Rehber Matematik", videos.first().teacher)
        assertEquals("WICamyMziqQ", videos.first().videoId)
        assertEquals(0, videos.first().startSeconds)
    }

    @Test
    fun unverifiedLegacyChapterDoesNotClaimPrecision() {
        val videos = SmartLessonCatalog.find(
            question("Türev Alma Kuralları", "Köklü ifadelerin türevi"),
            "AYT", "Matematik", "Türev Alma Kuralları"
        )
        assertEquals(0, videos.first().startSeconds)
    }

    @Test
    fun unrelatedTopicNeverShowsAnUnrelatedAutoplayVideo() {
        assertTrue(SmartLessonCatalog.find(
            question("Trigonometri"), "AYT", "Matematik", "Trigonometri"
        ).isEmpty())
        assertTrue(SmartLessonCatalog.find(
            question("Türev Alma Kuralları"), "TYT", "Matematik", "Türev Alma Kuralları"
        ).isEmpty())
    }

    @Test fun publishedPhysicsChapterIsScopedAndHasEndBoundary() {
        val videos = SmartLessonCatalog.find(question("Doğrusal Hareket", "yer değiştirme"),
            "TYT", "Fizik", "")
        assertEquals(662, videos.first().startSeconds)
        assertEquals(831, videos.first().endSeconds)
        assertEquals("displacement", videos.first().outcomeId)
        assertTrue(SmartLessonCatalog.find(question("Doğrusal Hareket", "yer değiştirme"),
            "AYT", "Fizik", "").isEmpty())
    }

    @Test fun derivativeApplicationsDoNotOpenRulesVideo() {
        assertTrue(SmartLessonCatalog.find(question("Türevin Geometrik ve Fiziksel Uygulamaları"),
            "AYT", "Matematik", "").isEmpty())
        assertTrue(SmartLessonCatalog.find(question("Türev olmayan konu"),
            "AYT", "Matematik", "").isEmpty())
    }

    @Test fun unavailablePreferredTeacherFallsBackAndAvailablePreferenceWins() {
        val videos = SmartLessonCatalog.find(question("Türev Alma Kuralları"), "AYT", "Matematik", "")
        assertEquals(videos.first(), SmartLessonCatalog.select(videos, "Olmayan hoca"))
        assertEquals("Eyüp B", SmartLessonCatalog.select(videos, "Eyüp B")?.teacher)
        assertEquals(null, SmartLessonCatalog.select(emptyList(), "Eyüp B"))
    }

    @Test fun everyCurriculumTopicHasScopedSearchAndStablePreferenceKey() {
        com.example.data.SoruLabCatalog.allSubjects.forEach { subject ->
            subject.topics.forEach { topic ->
                val key = SmartLessonCatalog.preferenceKey(subject.examType.label, subject.name, topic.name)
                assertTrue(key.contains(subject.id))
                val url = java.net.URLDecoder.decode(
                    SmartLessonCatalog.searchUrl(subject.examType.label, subject.name, topic.name), "UTF-8")
                assertTrue(url.contains(subject.examType.label))
                assertTrue(url.contains(subject.name))
                assertTrue(url.contains(topic.name))
            }
        }
    }

    @Test fun generatedMetadataStaysInsideCurriculumAndHasEvidence() {
        GeneratedLessonContent.entries.forEach { entry ->
            val subject = com.example.data.SoruLabCatalog.allSubjects.single { it.id == entry.subjectId }
            assertTrue(subject.topics.any { it.id == entry.topicId })
            assertTrue(entry.video.sourceUrl.endsWith(entry.video.videoId))
            assertTrue(entry.video.reviewedOn.isNotBlank())
        }
    }

    @Test fun embedDisablesAutoplayAndRejectsInjectionAndInvalidSegments() {
        val html = buildYoutubeEmbedHtml("2i9_HZErHMU", 662, 831)
        assertTrue(html.contains("autoplay:0"))
        assertTrue(html.contains("end:831"))
        assertTrue(html.contains("onError"))
        for (invalid in listOf("bad", "'<script>")) {
            assertTrue(runCatching { buildYoutubeEmbedHtml(invalid, 0) }.isFailure)
        }
        assertTrue(runCatching { buildYoutubeEmbedHtml("2i9_HZErHMU", 10, 5) }.isFailure)
    }

    @Test fun ambiguousSkillsFallBackToGeneralLesson() {
        val videos = SmartLessonCatalog.find(question("Doğrusal Hareket", "konum ve yer değiştirme"),
            "TYT", "Fizik", "")
        assertEquals(0, videos.first().startSeconds)
        assertTrue(videos.first().outcomeId.isBlank())
    }
}
