package com.example.util

import java.util.Locale

/** A printed answer belongs to a section and a test, not just a question number. */
internal data class PdfAnswerScope(
    val section: String,
    val testNumber: Int,
    val isOsymTest: Boolean
)

internal data class PdfAnswerId(
    val scope: PdfAnswerScope,
    val questionNumber: Int
)

/** Pure parser: callers supply OCR text rows in top-to-bottom reading order. */
internal class PdfScopedAnswerKeys {
    private val answers = mutableMapOf<PdfAnswerId, String>()
    private val ambiguous = mutableSetOf<PdfAnswerId>()

    val answerCount: Int get() = answers.size

    fun addOcrRows(rows: List<String>) {
        var currentSection: String? = null
        for (row in rows) {
            val clean = row.trim().replace(Regex("\\s+"), " ")
            if (clean.isEmpty()) continue
            val detectedSection = detectSectionHeading(clean)
            if (detectedSection != null) {
                currentSection = detectedSection
                continue
            }
            val test = testLabel.find(clean) ?: continue
            val section = currentSection ?: continue // Never guess the section.
            val testNum = test.groupValues[2].toIntOrNull() ?: continue
            val isOsym = normalize(test.groupValues[1]).contains("osym")
            val tail = clean.substring(test.range.last + 1)
            val pairs = questionAnswer.findAll(tail).mapNotNull { match ->
                val n = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                if (n !in 1..200) return@mapNotNull null
                n to match.groupValues[2].uppercase(Locale.ROOT)
            }.toList()
            if (pairs.size < 2) continue // Avoid hallucinated single-character OCR matches.
            for ((number, option) in pairs) {
                val id = PdfAnswerId(PdfAnswerScope(section, testNum, isOsym), number)
                if (id in ambiguous) continue
                val previous = answers.putIfAbsent(id, option)
                if (previous != null && previous != option) {
                    answers.remove(id)
                    ambiguous.add(id)
                }
            }
        }
    }

    fun get(scope: PdfAnswerScope?, questionNumber: Int?): String? {
        if (scope == null || questionNumber == null || questionNumber <= 0) return null
        return answers[PdfAnswerId(scope, questionNumber)]
    }

    companion object {
        private val testLabel = Regex("(?i)(?:^|\\s)((?:ÖSYM|OSYM|O[SŞ]YM)\\s*)?TEST\\s*([0-9]{1,2})(?=\\s|$|[.:])")
        private val questionAnswer = Regex("(?<![0-9])([0-9]{1,3})\\s*[.\\-:)]?\\s*([A-E])(?=\\s|$|[,;|.]|[0-9])", RegexOption.IGNORE_CASE)
        private val questionScopeNumber = Regex("(?i)\\bTEST\\s*([0-9]{1,2})\\b")
        private val trailingPageNumber = Regex("\\s+[0-9]{1,2}\\s*$")
        private val nonSectionWords = listOf("cevap", "anahtar", "soru bankasi", "test", "osym", "tadinda", "dr biyoloji", "yayin")

        fun normalize(text: String): String = text.lowercase(Locale.forLanguageTag("tr-TR"))
            .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
            .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ").trim()

        private fun detectSectionHeading(text: String): String? {
            val stripped = text.trim().trim('-', ':', '•')
            if (stripped.length !in 5..65 || stripped.any(Char::isDigit)) return null
            val letters = stripped.filter(Char::isLetter)
            if (letters.length < 5 || letters.count(Char::isUpperCase) < letters.length * 0.75) return null
            val normalized = normalize(stripped)
            if (nonSectionWords.any { it in normalized }) return null
            return normalized.takeIf { it.length >= 5 }
        }

        fun looksLikeAnswerPage(rows: List<String>): Boolean {
            val normalized = normalize(rows.joinToString(" "))
            return "cevap anahtar" in normalized ||
                rows.count { testLabel.containsMatchIn(it) && questionAnswer.findAll(it).count() >= 2 } >= 3
        }

        /** Missing test/section evidence is deliberately left unmatched. */
        fun scopeFromQuestionHeader(headerText: String, topicHint: String): PdfAnswerScope? {
            val testNum = questionScopeNumber.find(normalize(headerText))?.groupValues?.get(1)?.toIntOrNull()
                ?: return null
            val normalizedHeader = normalize(headerText)
            val isOsym = "osym" in normalizedHeader
            val detectedSection = headerText.lines().map { it.trim() }
                .filter { it.isNotBlank() && !normalize(it).contains("test") }
                .map { normalize(it.replace(trailingPageNumber, "")) }
                .firstOrNull { line ->
                    line.length in 5..65 && line.any(Char::isLetter) &&
                        nonSectionWords.none { it in line } &&
                        !line.startsWith("www") && !line.startsWith("kuasar") &&
                        !line.startsWith("yildiz") && !line.startsWith("mg")
                }
            val section = detectedSection ?: normalize(topicHint).takeIf { it.isNotBlank() }
            if (section.isNullOrBlank()) return null
            return PdfAnswerScope(section, testNum, isOsym)
        }
    }
}
