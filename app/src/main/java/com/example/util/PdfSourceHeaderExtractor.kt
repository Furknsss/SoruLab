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
    private val printedQuestion = Regex("""^[1-9][0-9]?[.)]$""")

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
        return header.take(42).joinToString("\n")
    }
}
