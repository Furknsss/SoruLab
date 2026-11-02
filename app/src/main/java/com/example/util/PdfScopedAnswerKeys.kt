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
internal class PdfScopedAnswerKeys(
    private val observedSections: Set<String> = emptySet()
) {
    private val answers = mutableMapOf<PdfAnswerId, String>()
    private val ambiguous = mutableSetOf<PdfAnswerId>()
    private var lastRecognizedSection: String? = null

    val answerCount: Int get() = answers.size

    fun addOcrRows(rows: List<String>) {
        // A table may continue onto the next answer-key page without a repeated heading.
        var currentSection: String? = lastRecognizedSection
        for ((rowIndex, row) in rows.withIndex()) {
            val clean = row.trim().replace(Regex("\\s+"), " ")
            if (clean.isEmpty()) continue
            val detectedSection = identifySection(clean)
            if (detectedSection != null) {
                currentSection = detectedSection
                lastRecognizedSection = detectedSection
                continue
            }
            val test = testLabel.find(clean) ?: continue
            // Some publishers print the chapter and test on one line:
            // "Sinir Sistemi  TEST-1  1.B 2.C". Resolve against the
            // actual headings discovered in THIS PDF, not a brand preset.
            val inlineSection = identifySection(clean.substring(0, test.range.first))
            if (inlineSection != null) {
                currentSection = inlineSection
                lastRecognizedSection = inlineSection
            }
            val section = currentSection ?: continue // Never guess the section.
            val testNum = test.groupValues[2].toIntOrNull() ?: continue
            val isOsym = normalize(test.groupValues[1]).contains("osym")
            val tail = clean.substring(test.range.last + 1)
            // ML Kit often places the dark-purple 'Test 1' label and the
            // adjacent pale answer cells on DIFFERENT text rows. Allow a
            // strictly adjacent row of answer cells; never borrow answers
            // from the next test or the next chapter.
            val adjacent = rows.getOrNull(rowIndex + 1).orEmpty().trim()
            val continuation = adjacent.takeIf { next ->
                !testLabel.containsMatchIn(next) &&
                    detectSectionHeading(next) == null &&
                    questionAnswer.findAll(next).count() >= 2
            }.orEmpty()
            val answerCells = if (questionAnswer.findAll(tail).count() >= 2) tail
                else "$tail $continuation"
            val pairs = questionAnswer.findAll(answerCells).mapNotNull { match ->
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

    private fun identifySection(text: String): String? {
        if (observedSections.isEmpty()) return detectSectionHeading(text)
        val heading = normalize(text)
            .replace(Regex("\\s+[0-9]{1,2}$"), "")
        if (heading.length < 5) return null
        val matches = observedSections.filter { section ->
            val expected = normalize(section)
            heading == expected ||
                heading.startsWith("$expected ") ||
                heading.endsWith(" $expected") ||
                expected.startsWith("$heading ")
        }
        return matches.singleOrNull()
    }

    fun get(scope: PdfAnswerScope?, questionNumber: Int?): String? {
        if (scope == null || questionNumber == null || questionNumber <= 0) return null
        return answers[PdfAnswerId(scope, questionNumber)]
    }

    companion object {
        private val testLabel = Regex(
            "(?i)(?:^|\\s)((?:ÖSYM|OSYM|O[SŞ]YM)(?:\\s+TADINDA)?\\s*)?TEST\\s*[-.:–]?\\s*([0-9]{1,2})(?=\\s|$|[.:])"
        )
        private val questionAnswer = Regex("(?<![0-9])([0-9]{1,3})\\s*[.\\-:)]?\\s*([A-E])(?=\\s|$|[,;|.]|[0-9])", RegexOption.IGNORE_CASE)
        private val questionScopeNumber = Regex("(?i)\\bTEST\\s*([0-9]{1,2})\\b")
        private val trailingPageNumber = Regex("\\s+[0-9]{1,2}\\s*$")
        // Generic document/branding words, not a list of known publishers.
        private val nonSectionWords = listOf(
            "cevap", "anahtar", "soru bankasi", "test", "osym",
            "tadinda", "yayin", "kitap", "www", "pdf", "instagram"
        )

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
            val pairedTestRows = rows.mapIndexed { index, row ->
                if (!testLabel.containsMatchIn(row)) return@mapIndexed 0
                val next = rows.getOrNull(index + 1).orEmpty()
                val text = if (testLabel.containsMatchIn(next)) row else "$row $next"
                questionAnswer.findAll(text).count()
            }
            val strongRows = pairedTestRows.count { it >= 3 }
            val shortRows = pairedTestRows.count { it >= 2 }
            return "cevap anahtar" in normalized ||
                strongRows >= 2 || shortRows >= 3 ||
                (strongRows >= 1 && rows.any { detectSectionHeading(it) != null })
        }

        /** Missing test/section evidence is deliberately left unmatched. */
        fun scopeFromQuestionHeader(headerText: String, topicHint: String): PdfAnswerScope? {
            val testNum = questionScopeNumber.find(normalize(headerText))?.groupValues?.get(1)?.toIntOrNull()
                ?: return null
            val normalizedHeader = normalize(headerText)
            val isOsym = "osym" in normalizedHeader
            val hint = normalize(topicHint)
            val headings = headerText.lines().map { it.trim() }
                .filter { original ->
                    original.isNotBlank() &&
                        !original.contains('@') &&
                        !original.contains("http", ignoreCase = true) &&
                        !normalize(original).contains("test")
                }
                .map { normalize(it.replace(trailingPageNumber, "")) }
                .filter { line ->
                    line.length in 5..65 && line.any(Char::isLetter) &&
                        nonSectionWords.none { it in line } &&
                        !line.startsWith("www") &&
                        line !in listOf(
                            "biyoloji", "matematik", "fizik", "kimya",
                            "geometri", "turkce", "tarih", "cografya"
                        )
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
