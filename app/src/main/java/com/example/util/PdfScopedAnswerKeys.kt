package com.example.util

import java.util.Locale

/** A printed answer belongs to a section and a test, not just a question number. */
internal data class PdfAnswerScope(
    val section: String,
    val testNumber: Int,
    val isOsymTest: Boolean,
    val testKind: String = "test"
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
            // ML Kit may merge several horizontally printed tests into a single
            // line. The first label alone must not consume the next test's cells.
            val tests = testLabel.findAll(clean).toList()
            val firstTest = tests.firstOrNull()
            val sectionHeading = if (firstTest != null) {
                identifySection(clean.substring(0, firstTest.range.first))
            } else identifySection(clean)
            if (sectionHeading != null) {
                currentSection = sectionHeading
                lastRecognizedSection = sectionHeading
            }
            if (tests.isEmpty()) continue

            for ((labelIndex, test) in tests.withIndex()) {
                if (labelIndex > 0) {
                    // The same OCR line may also cross a chapter boundary.
                    // If text other than answer cells precedes this test label,
                    // require an independently recognized new section; otherwise
                    // never attribute those cells to the prior chapter.
                    val between = clean.substring(
                        tests[labelIndex - 1].range.last + 1, test.range.first
                    )
                    val remainder = questionAnswer.replace(between, " ").trim()
                    if (normalize(remainder).isNotBlank()) {
                        val newSection = identifySection(remainder)
                        if (newSection == null) {
                            // Unknown chapter text must invalidate the prior
                            // section even for subsequent tests on this row.
                            currentSection = null
                            lastRecognizedSection = null
                            continue
                        }
                        currentSection = newSection
                        lastRecognizedSection = newSection
                    }
                }
                val section = currentSection ?: continue
                val testNum = test.groupValues[2].toIntOrNull()?.takeIf { it in 1..200 } ?: continue
                val isOsym = normalize(test.groupValues[1]).contains("osym")
                val nextTest = tests.getOrNull(labelIndex + 1)
                val tail = clean.substring(
                    test.range.last + 1, nextTest?.range?.first ?: clean.length
                )

                // A label and its cells can occupy adjacent OCR lines. Only
                // the LAST test on this line may borrow that next line.
                val adjacent = if (nextTest == null) {
                    rows.getOrNull(rowIndex + 1).orEmpty().trim()
                } else ""
                val continuation = adjacent.takeIf { next ->
                    !testLabel.containsMatchIn(next) &&
                        detectSectionHeading(next) == null &&
                        questionAnswer.findAll(next).count() >= 2
                }.orEmpty()
                val answerCells = if (questionAnswer.findAll(tail).count() >= 2) {
                    tail
                } else "$tail $continuation"
                val pairs = questionAnswer.findAll(answerCells).mapNotNull { match ->
                    val n = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                    if (n !in 1..200) return@mapNotNull null
                    n to match.groupValues[2].uppercase(Locale.ROOT)
                }.toList()
                if (pairs.size < 2) continue // Reject isolated OCR fragments.
                val kind = questionScopeNumber.find(normalize(test.value))
                    ?.groupValues?.get(1) ?: "test"
                addPairs(PdfAnswerScope(section, testNum, isOsym, kind), pairs)
            }
        }
    }

    /** Trusted OCR rows or spatial answer grids for exactly one printed test. */
    fun addPairs(scope: PdfAnswerScope?, pairs: List<Pair<Int, String>>) {
        if (scope == null) return
        for ((number, rawAnswer) in pairs) {
            val option = rawAnswer.uppercase(Locale.ROOT)
            if (number !in 1..200 || option !in listOf("A", "B", "C", "D", "E")) continue
            val id = PdfAnswerId(scope, number)
            if (id in ambiguous) continue
            val previous = answers.putIfAbsent(id, option)
            if (previous != null && previous != option) {
                answers.remove(id)
                ambiguous.add(id)
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

    /** Distinguish unread answer-key scopes from missing cells inside a known scope. */
    fun hasRecognizedScope(scope: PdfAnswerScope?): Boolean =
        scope != null && (answers.keys.any { it.scope == scope } ||
            ambiguous.any { it.scope == scope })

    /** Conflicting OCR/key evidence cannot be graded but can be reported. */
    fun hasConflictingAnswer(scope: PdfAnswerScope?, questionNumber: Int?): Boolean =
        scope != null && questionNumber != null &&
            PdfAnswerId(scope, questionNumber) in ambiguous

    companion object {
        private val testLabel = Regex(
            "(?i)(?:^|\\s)((?:ÖSYM|OSYM|O[SŞ]YM)(?:\\s+TADINDA)?\\s*)?(?:TEST|ANAL[İI]Z|SENTEZ|DENEME|UYGULAMA|ETK[İI]NL[İI]K)\\s*[-.:–]?\\s*([0-9]{1,3})(?=\\s|$|[.:])"
        )
        private val questionAnswer = Regex("(?<![0-9])([0-9]{1,3})\\s*[.\\-:)]?\\s*([A-E])(?=\\s|$|[,;|.]|[0-9])", RegexOption.IGNORE_CASE)
        private val questionScopeNumber = Regex(
            "(?i)\\b(TEST|ANALIZ|SENTEZ|DENEME|UYGULAMA|ETKINLIK)\\s*[-.:–]?\\s*([0-9]{1,3})\\b"
        )
        private val trailingPageNumber = Regex("\\s+[0-9]{1,3}\\s*$")
        // Generic document/branding words, not a list of known publishers.
        private val nonSectionWords = listOf(
            "cevap", "anahtar", "soru bankasi", "test", "osym",
            "tadinda", "yayin", "kitap", "www", "pdf", "instagram",
            "analiz", "sentez", "deneme", "uygulama", "etkinlik"
        )

        // Reject layout words only as complete words/phrases: "sentez" is
        // NOT a standalone label inside a legitimate chapter "fotosentez".
        private fun containsNonSectionWord(normalized: String): Boolean {
            val padded = " $normalized "
            return nonSectionWords.any { word -> " $word " in padded }
        }

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
            if (containsNonSectionWord(normalized)) return null
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
            val normalizedHeader = normalize(headerText)
            val matchedTest = questionScopeNumber.find(normalizedHeader)
            // Some books print ANALİZ/SENTEZ above the chapter and a small
            // test number at the far edge. ML Kit can read those out of order:
            // "ANALİZ\\nSinir Sistemi\\n1". Accept this only from the
            // short heading area supplied by the page reader, never question
            // body text, and never guess a number when none was printed.
            val splitHeaderTest = if (matchedTest == null) {
                Regex("""\b(analiz|sentez)\b(?:\s+[a-z]{2,}(?:\s+[a-z]{2,}){0,8})?\s+([0-9]{1,2})(?=\s|$)""")
                    .find(normalizedHeader)
            } else null
            val match = matchedTest ?: splitHeaderTest ?: return null
            val testNum = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..200 } ?: return null
            val kind = match.groupValues[1].lowercase(Locale.ROOT)
            val isOsym = "osym" in normalizedHeader
            val hint = normalize(topicHint)
            val headerLines = headerText.lines()
            val testLineIndex = headerLines.indexOfFirst { line ->
                Regex("""\b(test|analiz|sentez|deneme|uygulama|etkinlik)\b""")
                    .containsMatchIn(normalize(line))
            }
            val headings = headerLines.mapIndexedNotNull { index, raw ->
                if (raw.isBlank() || raw.contains('@') ||
                    raw.contains("http", ignoreCase = true)
                ) return@mapIndexedNotNull null
                var heading = normalize(raw.replace(trailingPageNumber, ""))
                // OCR may join the printed test number to the topic:
                // "Sinir Sistemi1" and "Sinir Sistemi 1" are the same heading.
                val suffix = testNum.toString()
                if (heading.length > suffix.length + 4 && heading.endsWith(suffix) &&
                    heading[heading.length - suffix.length - 1].isLetter()
                ) heading = heading.dropLast(suffix.length)
                val chapterHeading = Regex("""(?:^|\s)(?:unite|bolum|chapter|sinif)(?:\s|$)""")
                    .containsMatchIn(heading)
                if (heading.length !in 5..65 || !heading.any(Char::isLetter) ||
                    chapterHeading || containsNonSectionWord(heading) ||
                    heading.startsWith("www") ||
                    heading in listOf(
                        "biyoloji", "matematik", "fizik", "kimya",
                        "geometri", "turkce", "tarih", "cografya"
                    )
                ) null else index to heading
            }
            // Only accept a topic hint when the heading visibly supports it.
            val matchedHint = hint.takeIf { it.isNotBlank() && headings.any { (_, text) ->
                text == it || text.startsWith(it) &&
                    text.removePrefix(it).matches(Regex("""\s*[0-9]{1,2}"""))
            } }
            // When a test number is printed beside its chapter label, prefer
            // the specific topic following that label; running unit headings
            // and publisher headers must never replace the actual chapter.
            val nearestAfter = headings.filter { it.first > testLineIndex }
                .minByOrNull { it.first }?.second
            val nearestBefore = headings.filter { it.first < testLineIndex }
                .maxByOrNull { it.first }?.second
            val section = matchedHint ?: nearestAfter ?: nearestBefore
                ?: headings.lastOrNull()?.second ?: hint.takeIf(String::isNotBlank)
            if (section.isNullOrBlank()) return null
            return PdfAnswerScope(section, testNum, isOsym, kind)
        }
    }
}
