package com.example.util

import java.util.Locale

/**
 * Recognize multiple-choice option labels, not publisher-specific typography.
 * Supports A), A., A:, (A) and spacing variants. Only standalone option
 * markers with following content count: a bare list "A. B. C." must not
 * make an advertisement or answer key look like a question page.
 */
internal object PdfOptionEvidence {
    private val optionToken = Regex(
        """(?<![A-Z0-9])(?:\(([A-E])\)|([A-E])[ \t]*[).:])(?=[ \t]*\S)(?![ \t]*[A-E][ \t]*[).:](?:[ \t]|$))"""
    )

    fun letters(pageText: String): Set<String> =
        optionToken.findAll(pageText.uppercase(Locale.ROOT))
            .map { match ->
                match.groupValues[1].ifBlank { match.groupValues[2] }
            }
            .filter(String::isNotBlank)
            .toSet()
}
