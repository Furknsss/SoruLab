package com.example.ui.screens

import com.example.data.LearningOutcomeAssignment
import com.example.data.LearningOutcomeCatalog
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
        val subject = subject(examType, question.subject)
            ?: subject(examType, subjectName) ?: return emptyList()
        val topicName = question.topicHeader.ifBlank { fallbackTopic }
        val topic = SoruLabCatalog.resolveTopicInSubject(subject.id, "", topicName) ?: return emptyList()
        val stored = LearningOutcomeCatalog.validated(subject.id, topic.id,
            LearningOutcomeAssignment(question.learningOutcomeId, question.learningOutcomeName,
                question.learningOutcomeSource, question.learningOutcomeConfidence))
        val publisher = LearningOutcomeCatalog.fromLabel(subject.id, topic.id, question.learningOutcome)
            .takeIf { it.id.isNotBlank() }
            ?: LearningOutcomeCatalog.fromLabel(subject.id, topic.id, question.questionType)
        val outcome = if (question.learningOutcomeId.isNotBlank() || question.learningOutcomeSource == "manual") stored
            else if (question.learningOutcome.isNotBlank() || question.questionType.isNotBlank()) publisher
            else LearningOutcomeCatalog.infer(subject.id, topic.id,
                    listOf(question.stemTop, question.stemBottom, question.analyticsText,
                        question.bulletsOrSteps.joinToString(" ")).joinToString(" "))
        val exact = if (outcome.confidence >= 0.9) outcome.id else ""
        // A topic-level video cannot stand in for an unverified question-level chapter.
        if (exact.isBlank()) return emptyList()
        return GeneratedLessonContent.entries.filter {
            it.subjectId == subject.id && it.topicId == topic.id &&
                it.video.outcomeId == exact
        }.sortedByDescending { it.video.outcomeId.isNotBlank() }
            .map { it.video }.distinctBy { it.teacher }
    }

    fun select(videos: List<LessonVideo>, preferred: String): LessonVideo? {
        val exact = videos.filter { it.outcomeId.isNotBlank() }
        val eligible = exact.ifEmpty { videos }
        return eligible.firstOrNull { it.teacher == preferred } ?: eligible.firstOrNull()
    }

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

/** null means no user override; an empty ID is an explicit manual clear. */
internal fun lessonQuestionWithOverride(
    question: YksQuestion, overrideId: String?, overrideName: String
): YksQuestion = if (overrideId == null) question else question.copy(
    learningOutcomeId = overrideId, learningOutcomeName = overrideName,
    learningOutcomeSource = "manual", learningOutcomeConfidence = 1.0
)
