package com.example.util

import kotlin.math.abs
import kotlin.math.max

/**
 * Independent whole-page coverage audit: every physically printed question
 * must be represented by exactly ONE crop with the same printed identity.
 *
 * A locally valid crop is insufficient: two overlapping crops can both contain
 * the same printed question while another question is entirely missing.
 *
 * Only call with reliable native-PDF or two-pass OCR source anchors. No source
 * anchors => no coverage claim; do not treat an empty source as proof of 100%.
 */
internal data class PdfPageCropCoverage(
    val sourceStartCount: Int,
    val capturedStartCount: Int,
    val missingStartCount: Int,
    val duplicateStartCount: Int,
    val unsafeCropIndexes: Set<Int>,
    val sourceEvidenceAvailable: Boolean
) {
    val isComplete: Boolean get() = sourceEvidenceAvailable &&
        sourceStartCount > 0 && missingStartCount == 0 &&
        duplicateStartCount == 0 && unsafeCropIndexes.isEmpty() &&
        capturedStartCount == sourceStartCount
}

internal object PdfPageCropCoverageAudit {
    fun assess(
        sourceNumbers: List<QuestionNumberAnchor>,
        crops: List<PdfQuestionCropEvidence>,
        pageWidth: Int,
        pageHeight: Int
    ): PdfPageCropCoverage {
        if (sourceNumbers.isEmpty() || pageWidth <= 0 || pageHeight <= 0) {
            return PdfPageCropCoverage(
                0, 0, 0, 0, emptySet(), false
            )
        }
        // A noisy OCR pass can report the same printed digit twice. Compare
        // physical positions, but DO NOT merge repeated question numbers
        // printed at different spots (two tests may both start with "1").
        val xTolerance = max(4f, pageWidth * .016f)
        val yTolerance = max(6f, pageHeight * .012f)
        val distinct = mutableListOf<QuestionNumberAnchor>()
        for (anchor in sourceNumbers.sortedWith(compareBy({ it.top }, { it.left }))) {
            if (anchor.number !in 1..200 || anchor.right <= anchor.left ||
                anchor.bottom <= anchor.top
            ) continue
            if (distinct.none { known ->
                    known.number == anchor.number &&
                        abs(known.left - anchor.left) < xTolerance &&
                        abs(known.top - anchor.top) < yTolerance
                }
            ) distinct.add(anchor)
        }
        if (distinct.isEmpty()) {
            return PdfPageCropCoverage(0, 0, 0, 0, emptySet(), false)
        }

        fun bounds(crop: PdfQuestionCropEvidence): List<Float>? {
            if (crop.normalizedBounds.size != 4) return null
            val b = crop.normalizedBounds
            if (!b.all { it in 0f..1f } || b[2] <= b[0] || b[3] <= b[1])
                return null
            return b
        }
        fun inside(anchor: QuestionNumberAnchor, b: List<Float>): Boolean {
            val centerX = (anchor.left + anchor.right) / 2f / pageWidth
            val centerY = (anchor.top + anchor.bottom) / 2f / pageHeight
            return centerX >= b[1] && centerX <= b[3] &&
                centerY >= b[0] && centerY <= b[2]
        }
        val validBoxes = crops.map(::bounds)
        val unsafe = mutableSetOf<Int>()
        crops.indices.filterTo(unsafe) { validBoxes[it] == null }

        // Local crop check and full-page check are complementary. If a crop
        // contains two source starts it is merged; if it contains zero it
        // cannot be trusted or graded as an identified question.
        for (i in crops.indices) {
            val b = validBoxes[i] ?: continue
            val physicalStarts = distinct.filter { inside(it, b) }
            if (physicalStarts.size != 1 ||
                physicalStarts.firstOrNull()?.number != crops[i].printedNumber
            ) unsafe.add(i)
        }
        var captured = 0
        var missing = 0
        var duplicate = 0
        for (source in distinct) {
            val matches = crops.indices.filter { i ->
                val b = validBoxes[i] ?: return@filter false
                crops[i].printedNumber == source.number && inside(source, b)
            }
            when (matches.size) {
                0 -> missing++
                1 -> captured++
                else -> {
                    duplicate++
                    unsafe.addAll(matches)
                }
            }
        }
        return PdfPageCropCoverage(distinct.size, captured, missing,
            duplicate, unsafe, true)
    }
}
