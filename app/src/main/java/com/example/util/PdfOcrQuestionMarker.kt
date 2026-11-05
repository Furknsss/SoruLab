package com.example.util

/**
 * Read printed questions from Android ML Kit text lines without assuming a
 * publisher-specific maximum of 39 questions per test. TYT/AYT practice sets
 * may number questions far beyond that threshold.
 */
internal object PdfOcrQuestionMarker {
    // ML Kit frequently fuses a printed "1." number and first stem word
    // into one element ("1.Soru" / "12.Aşağıdaki"). It is still a real
    // printed start, but "1.25" is a decimal and must never be accepted.
    // Keep the existing chapter/unit heading rejection below.
    private val atStart = Regex("""^\s*([1-9][0-9]{0,2})\s*[.):\-](?=\s|$|[A-Za-zÇĞİÖŞÜçğıöşü])""")
    private val standalone = Regex("""^\s*[1-9][0-9]{0,2}\s*[.):\-]?\s*$""")
    private val nonQuestionHeading = Regex(
        """(?i)^\s*(?:ÜNİTE|UNITE|BÖLÜM|BOLUM|SINIF|CHAPTER|KONU)\b"""
    )

    fun numberAtStart(text: String): Int? {
        val matched = atStart.find(text) ?: return null
        val number = matched.groupValues[1].toIntOrNull()
            ?.takeIf { it in 1..200 } ?: return null
        val trailing = text.substring(matched.range.last + 1)
        if (nonQuestionHeading.containsMatchIn(trailing)) return null
        return number
    }

    fun isStandaloneNumberElement(text: String): Boolean =
        standalone.matches(text) && (text.trim().takeWhile(Char::isDigit)
            .toIntOrNull()?.let { it in 1..200 } == true)
}
