package com.example.ui.screens

import com.example.data.YksQuestion

internal data class StoredSolutionSection(val title: String, val body: String)

internal data class StoredQuestionSolution(
    val sections: List<StoredSolutionSection>,
    val verifiedAnswer: String?,
    val answerNotice: String
) {
    val hasSolution: Boolean get() = sections.isNotEmpty()

    companion object {
        private val heading = Regex(
            """(?m)^\s*(?:#{1,6}\s*)?(?:📌|✏️?|⚠️?)?\s*(?:\d+[.)]\s*)?(?:\*\*)?(Kritik Bilgi\s*(?:&|ve)\s*Teorem|Adım Adım (?:Çözüm|İşlem)|ÖSYM Tuzak Notu)\s*(?:\*\*)?\s*:\s*(?:\*\*)?""",
            RegexOption.IGNORE_CASE
        )
        private const val missingMessage = "Bu soru için kayıtlı çözüm bulunmuyor."

        fun from(question: YksQuestion, returnedText: String): StoredQuestionSolution {
            val returned = clean(returnedText)
            val stored = clean(question.stepByStepSolution)
            val raw = returned.takeUnless { it.isBlank() || it.startsWith(missingMessage) }
                ?: stored.takeUnless { it.startsWith(missingMessage) }.orEmpty()
            val answer = question.correctOption.trim().uppercase(java.util.Locale.ROOT)
                .takeIf { question.answerKeyVerified && it in setOf("A", "B", "C", "D", "E") }
            return StoredQuestionSolution(
                sections = parse(raw),
                verifiedAnswer = answer,
                answerNotice = if (answer != null) "Doğrulanmış cevap anahtarı: $answer"
                    else "Cevap anahtarı doğrulanmadı; doğru seçenek olarak sunulmaz."
            )
        }

        private fun clean(text: String): String {
            var body = text.trim()
            // Import metadata is not an explanation. Malformed headers must not leak private paths either.
            while (body.startsWith("[META:")) {
                val boundary = body.indexOf(']')
                if (boundary < 0) return ""
                body = body.substring(boundary + 1).trim()
            }
            return body
        }

        private fun parse(raw: String): List<StoredSolutionSection> {
            if (raw.isBlank()) return emptyList()
            val markers = heading.findAll(raw).toList()
            if (markers.isEmpty()) return listOf(StoredSolutionSection("Kayıtlı çözüm", raw))
            return buildList {
                val preamble = raw.substring(0, markers.first().range.first).trim()
                if (preamble.isNotBlank()) add(StoredSolutionSection("Kayıtlı açıklama", preamble))
                markers.forEachIndexed { index, match ->
                    val end = markers.getOrNull(index + 1)?.range?.first ?: raw.length
                    val body = raw.substring(match.range.last + 1, end).trim()
                    if (body.isNotBlank()) add(StoredSolutionSection(match.groupValues[1], body))
                }
            }
        }
    }
}

/** A closed/retried question cannot be overwritten by an older callback, even for the same ID. */
internal class StoredSolutionRequestGate {
    private var version = 0L
    private var questionId: String? = null
    fun begin(id: String): Long {
        questionId = id
        return ++version
    }
    fun accepts(token: Long, id: String): Boolean = token == version && questionId == id
    fun cancel() {
        questionId = null
        version++
    }
}
