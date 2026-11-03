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

    // Some publishers put the option letter and its text on separate lines.
    // An isolated A) is evidence only if the next nonempty line contains text
    // rather than another bare option marker.
    private val bareOptionLine = Regex(
        """^[ \t]*(?:\(([A-E])\)|([A-E])[ \t]*[).:])[ \t]*$"""
    )
    private val nextOptionLine = Regex(
        """^[ \t]*(?:\([A-E]\)|[A-E][ \t]*[).:])"""
    )

    fun letters(pageText: String): Set<String> {
        val upper = pageText.uppercase(Locale.ROOT)
        val inlineOptions = optionToken.findAll(upper)
            .map { match ->
                match.groupValues[1].ifBlank { match.groupValues[2] }
            }
            .filter(String::isNotBlank)
            .toSet()
        val lines = upper.lines()
        val splitOptions = lines.indices.mapNotNull { index ->
            val match = bareOptionLine.matchEntire(lines[index])
                ?: return@mapNotNull null
            val next = lines.drop(index + 1).firstOrNull(String::isNotBlank)
                ?: return@mapNotNull null
            if (nextOptionLine.containsMatchIn(next)) return@mapNotNull null
            match.groupValues[1].ifBlank { match.groupValues[2] }
        }.filter(String::isNotBlank).toSet()
        return inlineOptions + splitOptions
    }
}
