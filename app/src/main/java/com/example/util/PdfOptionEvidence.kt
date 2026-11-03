package com.example.util

import java.util.Locale

/**
 * Read A)-E) evidence from the PDF's own text layer.
 *
 * Keep this regex in one pure, unit-testable place: a previous raw-string
 * expression accidentally contained a double backslash before ')', causing
 * PatternSyntaxException on every book import reaching this step.
 */
internal object PdfOptionEvidence {
    private val optionToken = Regex("""[A-E]\)""")

    fun letters(pageText: String): Set<String> =
        optionToken.findAll(pageText.uppercase(Locale.ROOT))
            .map { it.value.take(1) }
            .toSet()
}
