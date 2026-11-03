package com.example.util

/**
 * Independent OCR/native-PDF answer extraction channels are complementary.
 * Some PDFBox renderers find only the first 8 of a 12-cell footer; a complete
 * strictly numbered text-layer grid can recover the remaining four cells.
 * Preserve all non-conflicting evidence and quarantine disagreements.
 */
internal object PdfAnswerSourceConsensus {
    fun combine(
        geometric: Map<Int, String>,
        linearText: Map<Int, String>
    ): Map<Int, String> {
        if (geometric.isEmpty()) return linearText
        if (linearText.isEmpty()) return geometric
        val result = linkedMapOf<Int, String>()
        for (key in (geometric.keys + linearText.keys).sorted()) {
            val geometricAnswer = geometric[key]?.uppercase()
            val linearAnswer = linearText[key]?.uppercase()
            val finalAnswer = when {
                geometricAnswer != null && linearAnswer != null &&
                    geometricAnswer != linearAnswer -> null
                else -> geometricAnswer ?: linearAnswer
            }
            if (finalAnswer != null && finalAnswer in setOf("A", "B", "C", "D", "E")) {
                result[key] = finalAnswer
            }
        }
        return result
    }
}
