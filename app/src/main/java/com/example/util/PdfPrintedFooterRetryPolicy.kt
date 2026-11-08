package com.example.util

/**
 * A second high-resolution image OCR of a printed answer strip is useful
 * ONLY when the first image read has not already independently confirmed
 * the complete selectable-PDF answer grid.
 *
 * This never trusts the PDF vector text alone: the primary image reader must
 * match every printed (number, letter) pair. Conflicts, missing cells and
 * image-only books continue through the existing detailed OCR fallback.
 */
internal object PdfPrintedFooterRetryPolicy {
    fun shouldRetry(
        nativeKeys: Map<Int, String>,
        primaryImageGrids: List<PdfHorizontalAnswerGrid>,
        primaryHasChoiceText: Boolean
    ): Boolean {
        val nativeTrusted = nativeKeys.size >= 4 &&
            nativeKeys.keys == (1..nativeKeys.size).toSet() &&
            nativeKeys.values.all { it in listOf("A", "B", "C", "D", "E") }
        val independentExactMatch = nativeTrusted && primaryImageGrids.isNotEmpty() &&
            primaryImageGrids.all { it.answers == nativeKeys }
        if (independentExactMatch) return false
        return primaryImageGrids.isNotEmpty() || primaryHasChoiceText
    }
}
