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
    private var lastRecognizedSection: String? = null

    val answerCount: Int get() = answers.size

    fun expectedNumbersBySourceTestId(): Map<String, Set<Int>> =
        answers.keys.groupBy(
            keySelector = { id ->
                "${id.scope.section}~${if (id.scope.isOsymTest) "osym" else "normal"}~${id.scope.testNumber}"
            },
            valueTransform = { it.questionNumber }
        ).mapValues { (_, numbers) -> numbers.toSet() }

    fun addOcrRows(rows: List<String>) {
        var currentSection: String? = lastRecognizedSection
        var activeScope: PdfAnswerScope? = null
        val pendingPairs = mutableListOf<Pair<Int, String>>()

        fun commitActive() {
            val scope = activeScope
            if (scope != null && pendingPairs.size >= 2) {
                for ((number, option) in pendingPairs) {
                    val id = PdfAnswerId(scope, number)
                    if (id in ambiguous) continue
                    val previous = answers.putIfAbsent(id, option)
                    if (previous != null && previous != option) {
                        answers.remove(id)
                        ambiguous.add(id)
                    }
                }
            }
            pendingPairs.clear()
        }

        for (row in rows) {
            val clean = row.trim().replace(Regex("\\s+"), " ")
            if (clean.isEmpty()) continue
            val detectedSection = detectSectionHeading(clean)
            if (detectedSection != null) {
                commitActive()
                activeScope = null
                currentSection = detectedSection
                lastRecognizedSection = detectedSection
                continue
            }

            val test = testLabel.find(clean)
            val answerPart = if (test != null) {
                commitActive()
                val section = currentSection
                val number = test.groupValues[2].toIntOrNull()
                activeScope = if (section != null && number != null) {
                    PdfAnswerScope(
                        section, number,
                        normalize(test.groupValues[1]).contains("osym")
                    )
                } else null
                clean.substring(test.range.last + 1)
            } else clean

            if (activeScope == null) continue
            // A small answer table may be OCR'd into individual cells or into
            // one complete line. Accumulate both until the next Test/heading.
            questionAnswer.findAll(answerPart).forEach { match ->
                val n = match.groupValues[1].toIntOrNull()
                if (n != null && n in 1..200) {
                    pendingPairs += n to match.groupValues[2].uppercase(Locale.ROOT)
                }
            }
        }
        commitActive()
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
            val strongRows = rows.count {
                testLabel.containsMatchIn(it) && questionAnswer.findAll(it).count() >= 3
            }
            val shortRows = rows.count {
                testLabel.containsMatchIn(it) && questionAnswer.findAll(it).count() >= 2
            }
            val totalPairs = rows.sumOf { questionAnswer.findAll(it).count() }
            val testRows = rows.count { testLabel.containsMatchIn(it) }
            val hasSection = rows.any { detectSectionHeading(it) != null }
            return "cevap anahtar" in normalized ||
                strongRows >= 2 || shortRows >= 3 ||
                (strongRows >= 1 && hasSection) ||
                (testRows >= 1 && totalPairs >= 8 && hasSection)
        }

        /** Missing test/section evidence is deliberately left unmatched. */
        fun scopeFromQuestionHeader(headerText: String, topicHint: String): PdfAnswerScope? {
            val testNum = questionScopeNumber.find(normalize(headerText))?.groupValues?.get(1)?.toIntOrNull()
                ?: return null
            val normalizedHeader = normalize(headerText)
            val isOsym = "osym" in normalizedHeader
            val hint = normalize(topicHint)
            val headings = headerText.lines().map { it.trim() }
                .filter { it.isNotBlank() && !normalize(it).contains("test") }
                .map { normalize(it.replace(trailingPageNumber, "")) }
                .filter { line ->
                    line.length in 5..65 && line.any(Char::isLetter) &&
                        nonSectionWords.none { it in line } &&
                        !line.startsWith("www") && !line.startsWith("kuasar") &&
                        !line.startsWith("yildiz") && !line.startsWith("mg")
                }
            // Scanners sometimes recognize "Sinir Sistemi1" without the space
            // separating the printed unit suffix. Use the recognized topic only
            // when the visible heading matches it (with an optional 1-2 digit suffix).
            val matchedHint = hint.takeIf { it.isNotBlank() && headings.any { heading ->
                heading == it || heading.removePrefix(it).matches(Regex("[0-9]{1,2}"))
                    && heading.startsWith(it)
            } }
            val section = matchedHint ?: headings.firstOrNull() ?: hint.takeIf(String::isNotBlank)
            if (section.isNullOrBlank()) return null
            return PdfAnswerScope(section, testNum, isOsym)
        }
    }
}
