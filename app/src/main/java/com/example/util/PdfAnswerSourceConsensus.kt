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
        val result = linkedMapOf<Int, String>()
        // Apply the same validation when only ONE source is available.
        // Previously the early return could pass through lowercase, blank or
        // invalid OCR cells, inflating the independent completeness report.
        fun valid(raw: String?): String? = raw?.trim()?.uppercase()
            ?.takeIf { it in setOf("A", "B", "C", "D", "E") }
        for (key in (geometric.keys + linearText.keys).sorted()) {
            if (key !in 1..200) continue
            val geoRaw = geometric[key]
            val textRaw = linearText[key]
            val geometricAnswer = valid(geoRaw)
            val linearAnswer = valid(textRaw)
            // Two OCR channels reporting the same cell must AGREE.
            // Even an invalid character in either channel indicates that
            // the independent reads disagree; fail closed rather than
            // silently treating the other letter as independently verified.
            if (geoRaw != null && textRaw != null &&
                (geometricAnswer == null || linearAnswer == null ||
                    geometricAnswer != linearAnswer)) continue
            val finalAnswer = geometricAnswer ?: linearAnswer
            if (finalAnswer != null) result[key] = finalAnswer
        }
        return result
    }
}
