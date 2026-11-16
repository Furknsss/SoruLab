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
        assertEquals("affPznU8gu0", videos.first().videoId)
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
    fun chapterTimestampIsOnlyFromPublishedChapterList() {
        val videos = SmartLessonCatalog.find(
            question("Türev Alma Kuralları", "Köklü ifadelerin türevi"),
            "AYT", "Matematik", "Türev Alma Kuralları"
        )
        assertEquals(1773, videos.first().startSeconds)
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
}
