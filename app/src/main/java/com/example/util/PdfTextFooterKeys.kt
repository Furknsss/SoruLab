package com.example.util

/**
 * Conservative second channel for a PDF's own selectable text layer.
 *
 * Many publishers print the key directly below the last question as two rows:
 *   25    1   2   3   4   5
 *         E   B   A   D   C
 *
 * The leading number is the printed PAGE, not a question. Recognize only a
 * consecutive 1..N run and an immediately following A-E-only row in the last
 * few lines. Never infer an absent letter, accept prose, or use page numbers
 * as the answer identity. Scanned PDFs fall back to geometric ML Kit OCR.
 */
internal object PdfTextFooterKeys {
    private val spacing = Regex("""\\s+""")
    private val option = Regex("""^[A-E]$""")

    fun read(text: String): Map<Int, String> {
        val lines = text.lineSequence().map(String::trim)
            .filter(String::isNotEmpty).toList().takeLast(10)
        for (rowIndex in (lines.lastIndex - 1) downTo 0) {
            val candidates = lines[rowIndex + 1].split(spacing)
                .map { it.trim().uppercase() }
            if (candidates.size !in 4..40 || candidates.any { !option.matches(it) }) continue
            val numbers = lines[rowIndex].split(spacing)
            for (start in numbers.indices) {
                val sequence = candidates.indices.all { offset ->
                    numbers.getOrNull(start + offset) == (offset + 1).toString()
                }
                if (!sequence) continue
                // Do not accept a partial answer row whose number sequence
                // continues beyond the visible A-E cells.
                if (numbers.getOrNull(start + candidates.size)?.toIntOrNull() ==
                    candidates.size + 1
                ) continue
                return candidates.mapIndexed { index, answer -> index + 1 to answer }.toMap()
            }
        }
        return emptyMap()
    }
}
