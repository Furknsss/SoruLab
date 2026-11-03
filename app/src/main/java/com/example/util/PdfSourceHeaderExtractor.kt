package com.example.util

/**
 * A PDF text layer is not in visual reading order. Vertical logos, watermarks,
 * running page numbers and answer strips often precede the actual test title.
 * Taking only the first eight lines lost many source test scopes.
 *
 * This extracts a bounded header without any publisher-specific branding.
 */
internal object PdfSourceHeaderExtractor {
    private val testHeading = Regex(
        """(?i)\b(analiz|sentez|deneme|test|uygulama|etkinlik|osym|ö sym|ösym)\b"""
    )
    private val printedQuestion = Regex("""^[1-9][0-9]{0,2}[.)]$""")
    private val standaloneNumber = Regex("""^[1-9][0-9]?$""")
    private val titledNumber = Regex(
        """(?i)\b(?:analiz|sentez|deneme|test|uygulama|etkinlik|osym|ösym)\s+[1-9][0-9]?\b"""
    )

    fun extract(text: String): String {
        val nonempty = text.lineSequence().map(String::trim)
            .filter(String::isNotBlank)
            .filterNot { it.length == 1 && it[0].isLetter() } // vertical logo
            .filterNot { it.startsWith("@") || it.startsWith("http", true) }
            .take(72).toList()
        val header = mutableListOf<String>()
        var sawTestHeading = false
        for (line in nonempty) {
            if (sawTestHeading && printedQuestion.matches(line)) break
            header += line
            if (testHeading.containsMatchIn(PdfScopedAnswerKeys.normalize(line))) {
                sawTestHeading = true
            }
            if (header.size >= 42) break
        }
        // PDFs with an inline answer strip can put "ANALİZ 1 2 3 ... 2"
        // before the next question, where the FINAL 2 is the test number.
        // Picking the first 1 would silently link this page to Test 1.
        val headingIndex = header.indexOfFirst {
            testHeading.containsMatchIn(PdfScopedAnswerKeys.normalize(it))
        }
        if (headingIndex >= 0 && !titledNumber.containsMatchIn(
                PdfScopedAnswerKeys.normalize(header[headingIndex])
            )
        ) {
            val rawNumbers = header.drop(headingIndex + 1)
                .filter { standaloneNumber.matches(it) }
                .mapNotNull(String::toIntOrNull)
            val last = rawNumbers.lastOrNull()
            val beforeLast = rawNumbers.dropLast(1)
            val tableIsSequential = beforeLast.size >= 3 &&
                beforeLast.withIndex().all { (index, number) -> number == index + 1 }
            val confident = rawNumbers.size == 1 ||
                (tableIsSequential && last != beforeLast.size + 1)
            if (last != null && confident) {
                header[headingIndex] = "${header[headingIndex]} $last"
            }
            // Remove standalone table-cell numbers after the heading so the
            // scoped parser cannot accidentally bind to the first answer row.
            val firstAfter = headingIndex + 1
            val cleanedTail = header.drop(firstAfter)
                .filterNot { standaloneNumber.matches(it) }
            return (header.take(firstAfter) + cleanedTail)
                .take(42).joinToString("\n")
        }
        return header.take(42).joinToString("\n")
    }
}
