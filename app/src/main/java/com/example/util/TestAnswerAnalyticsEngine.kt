package com.example.util

import com.example.data.YksQuestion
import java.util.Locale

/** The analytics layer never scores questions without a VERIFIED source key. */
internal enum class AnswerOutcome { CORRECT, WRONG, BLANK, UNGRADED }

internal data class AnswerAnalysisItem(
    val lesson: String,
    val topic: String,
    val learningOutcome: String,
    val questionType: String,
    val status: AnswerOutcome,
    val inferredLearningOutcome: Boolean = false,
    val inferredQuestionType: Boolean = false
)

internal data class AnswerAnalysisBucket(
    val label: String,
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    val ungraded: Int,
    val children: List<AnswerAnalysisBucket> = emptyList()
) {
    val total: Int get() = correct + wrong + blank + ungraded
    val graded: Int get() = correct + wrong + blank
}

internal data class TestAnswerAnalytics(
    val total: Int,
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    val ungraded: Int,
    val hierarchy: List<AnswerAnalysisBucket>,
    val questionTypes: List<AnswerAnalysisBucket>,
    val inferredMetadataCount: Int
) {
    val graded: Int get() = correct + wrong + blank
}

internal object TestAnswerAnalyticsEngine {
    private val unclassifiedOutcome = "Alt kazanım belirlenmedi"
    private val unclassifiedType = "Soru türü belirlenmedi"

    fun calculate(
        questions: List<YksQuestion>,
        selectedAnswers: Map<Int, String>,
        lessonHint: String,
        topicHint: String
    ): TestAnswerAnalytics {
        val items = questions.map { question ->
            val answer = selectedAnswers[question.number]?.trim()?.uppercase(Locale.ROOT)
            val correct = question.correctOption.trim().uppercase(Locale.ROOT)
            val status = when {
                !question.answerKeyVerified || correct !in listOf("A", "B", "C", "D", "E") ->
                    AnswerOutcome.UNGRADED
                answer !in listOf("A", "B", "C", "D", "E") -> AnswerOutcome.BLANK
                answer == correct -> AnswerOutcome.CORRECT
                else -> AnswerOutcome.WRONG
            }
            val text = listOf(question.analyticsText, question.stemTop, question.stemBottom)
                .plus(question.bulletsOrSteps).joinToString(" ")
            val lesson = question.subject.takeIf(String::isNotBlank)
                ?: lessonHint.takeIf(String::isNotBlank) ?: "Ders belirtilmedi"
            val topic = question.topicHeader.takeIf(String::isNotBlank)
                ?: topicHint.takeIf(String::isNotBlank) ?: "Konu belirtilmedi"
            val learning = question.learningOutcome.trim().takeIf(String::isNotBlank)
                ?: suggestLearningOutcome(topic, text) ?: unclassifiedOutcome
            val type = question.questionType.trim().takeIf(String::isNotBlank)
                ?: suggestQuestionType(text) ?: unclassifiedType
            AnswerAnalysisItem(
                lesson = lesson,
                topic = topic,
                learningOutcome = learning,
                questionType = type,
                status = status,
                inferredLearningOutcome = question.learningOutcome.isBlank() && learning != unclassifiedOutcome,
                inferredQuestionType = question.questionType.isBlank() && type != unclassifiedType
            )
        }
        val summary = tally("Toplam", items, emptyList())
        return TestAnswerAnalytics(
            total = items.size,
            correct = summary.correct, wrong = summary.wrong,
            blank = summary.blank, ungraded = summary.ungraded,
            hierarchy = group(items, listOf(
                AnswerAnalysisItem::lesson,
                AnswerAnalysisItem::topic,
                AnswerAnalysisItem::learningOutcome,
                AnswerAnalysisItem::questionType
            )),
            questionTypes = group(items, listOf(AnswerAnalysisItem::questionType)),
            inferredMetadataCount = items.count {
                it.inferredLearningOutcome || it.inferredQuestionType
            }
        )
    }

    private fun group(
        items: List<AnswerAnalysisItem>,
        dimensions: List<(AnswerAnalysisItem) -> String>
    ): List<AnswerAnalysisBucket> {
        if (items.isEmpty() || dimensions.isEmpty()) return emptyList()
        return items.groupBy(dimensions.first()).map { (name, records) ->
            tally(name, records, group(records, dimensions.drop(1)))
        }.sortedWith(
            compareByDescending<AnswerAnalysisBucket> { it.wrong }
                .thenByDescending { it.blank }
                .thenByDescending { it.total }
                .thenBy { it.label }
        )
    }

    private fun tally(
        title: String,
        items: List<AnswerAnalysisItem>,
        children: List<AnswerAnalysisBucket>
    ): AnswerAnalysisBucket = AnswerAnalysisBucket(
        label = title,
        correct = items.count { it.status == AnswerOutcome.CORRECT },
        wrong = items.count { it.status == AnswerOutcome.WRONG },
        blank = items.count { it.status == AnswerOutcome.BLANK },
        ungraded = items.count { it.status == AnswerOutcome.UNGRADED },
        children = children
    )

    // Keywords only suggest a skill; they do not claim an official MEB outcome.
    // Explicit reviewed question metadata always takes precedence.
    private fun suggestLearningOutcome(topic: String, text: String): String? {
        val body = normalize(text)
        if (body.isBlank()) return null
        if (normalize(topic).contains("sinir sistem")) {
            return when {
                listOf("sinaps", "norotransmitter", "sinaptik").any(body::contains) ->
                    "Sinaps ve kimyasal iletim"
                listOf("glia", "noroglia", "schwann").any(body::contains) ->
                    "Nöroglia ve miyelin"
                listOf("aksiyon potansiyeli", "impuls", "esik deger").any(body::contains) ->
                    "İmpuls ve aksiyon potansiyeli"
                listOf("refleks", "omurilik").any(body::contains) ->
                    "Refleks ve omurilik"
                listOf("hipotalamus", "beyincik", "beyin").any(body::contains) ->
                    "Merkezî sinir sistemi"
                listOf("noron", "dendrit", "akson").any(body::contains) ->
                    "Nöronun yapısı ve görevleri"
                else -> null
            }
        }
        return null
    }

    private fun suggestQuestionType(text: String): String? {
        val body = normalize(text)
        return when {
            "grafik" in body -> "Grafik yorumlama"
            "tablo" in body -> "Tablo yorumlama"
            "sekil" in body || "gorsel" in body -> "Görsel yorumlama"
            "deney" in body -> "Deney yorumlama"
            "eslestir" in body -> "Eşleştirme"
            "hesapla" in body || "kactir" in body -> "Hesaplama"
            else -> null
        }
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.forLanguageTag("tr-TR"))
            .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
            .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
}
