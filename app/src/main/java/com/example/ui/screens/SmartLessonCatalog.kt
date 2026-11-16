package com.example.ui.screens

import com.example.data.SoruLabCatalog
import com.example.data.YksQuestion
import java.net.URLEncoder
import java.util.Locale

internal fun lessonNormalized(value: String): String =
    value.trim().lowercase(Locale.forLanguageTag("tr-TR"))
        .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u')
        .replace('ş', 's').replace('ö', 'o').replace('ç', 'c')
        .replace(Regex("\\s+"), " ")

internal data class LessonVideo(
    val teacher: String,
    val title: String,
    val videoId: String,
    val startSeconds: Int,
    val detail: String,
    val sourceOfTimestamp: String,
    val sourceUrl: String,
    val reviewedOn: String,
    val outcomeId: String = "",
    val endSeconds: Int? = null
) {
    init {
        require(Regex("[A-Za-z0-9_-]{11}").matches(videoId))
        require(startSeconds >= 0 && (endSeconds == null || endSeconds > startSeconds))
        require(sourceUrl == "https://www.youtube.com/watch?v=$videoId")
        require(teacher.isNotBlank() && title.isNotBlank() && sourceOfTimestamp.isNotBlank())
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(reviewedOn))
    }
    val watchUrl: String get() = "$sourceUrl&t=${startSeconds}s"
}

internal data class LessonEntry(
    val subjectId: String,
    val topicId: String,
    val video: LessonVideo,
    val phrases: List<String> = emptyList()
)

/** The curriculum, not a second handwritten list, defines supported exam/subject/topic scope. */
internal object SmartLessonCatalog {
    fun subject(exam: String, name: String) = SoruLabCatalog.allSubjects.firstOrNull {
        lessonNormalized(it.examType.label) == lessonNormalized(exam) &&
            (lessonNormalized(it.name) == lessonNormalized(name) || it.id == name)
    }

    fun find(question: YksQuestion, examType: String, subjectName: String,
             fallbackTopic: String): List<LessonVideo> {
        val subject = subject(examType, subjectName.ifBlank { question.subject }) ?: return emptyList()
        val topicName = question.topicHeader.ifBlank { fallbackTopic }
        val topic = SoruLabCatalog.resolveTopicInSubject(subject.id, "", topicName) ?: return emptyList()
        val stem = lessonNormalized(listOf(question.stemTop, question.stemBottom,
            question.bulletsOrSteps.joinToString(" ")).joinToString(" "))
        val entries = GeneratedLessonContent.entries.filter {
            it.subjectId == subject.id && it.topicId == topic.id &&
                (it.phrases.isEmpty() || it.phrases.any { phrase ->
                    Regex("(?<![a-z0-9])" + Regex.escape(lessonNormalized(phrase)) + "(?![a-z0-9])")
                        .containsMatchIn(stem)
                })
        }
        // Skill matches precede general lessons; keep one best entry per teacher.
        return entries.sortedByDescending { it.phrases.isNotEmpty() }
            .map { it.video }.distinctBy { it.teacher }
    }

    fun select(videos: List<LessonVideo>, preferred: String): LessonVideo? =
        videos.firstOrNull { it.teacher == preferred } ?: videos.firstOrNull()

    fun preferenceKey(examType: String, subjectName: String, topicName: String): String {
        val subject = subject(examType, subjectName)
        val topic = subject?.let { SoruLabCatalog.resolveTopicInSubject(it.id, "", topicName) }
        return listOf(lessonNormalized(examType), subject?.id ?: lessonNormalized(subjectName),
            topic?.id ?: lessonNormalized(topicName)).joinToString("|")
    }

    fun searchUrl(exam: String, subject: String, topic: String, teacher: String = ""): String {
        // Never transmit the question text, image, answer, or user ID.
        val query = listOf(exam, subject, topic, teacher.takeUnless { it == "Otomatik" }.orEmpty(),
            "konu anlatımı").filter { it.isNotBlank() }.joinToString(" ")
        return "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")
    }
}
