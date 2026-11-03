package com.example.util

/** Normalized actual crop bounds, in [top, left, bottom, right] order. */
internal data class PdfQuestionCropEvidence(
    val printedNumber: Int,
    val normalizedBounds: List<Float>
)

internal enum class PdfCropIssue {
    MULTIPLE_PRINTED_QUESTIONS_IN_ONE_CROP,
    WRONG_OR_MISSING_PRINTED_START,
    INVALID_CROP_BOUNDS
}

/**
 * Compare a crop image's SOURCE rectangle with the independently extracted
 * printed question starts on the same PDF page. A picture containing question
 * 7 and question 8 MUST NOT be accepted as one "question 1" merely because
 * it has A-E options and is a nonblank bitmap.
 *
 * This is not a completeness certificate: missing text, cut-off diagrams and
 * raster-only source pages still require independent validation.
 */
internal object PdfCropGeometryAudit {
    fun issues(
        crop: PdfQuestionCropEvidence,
        sourceNumbers: List<QuestionNumberAnchor>,
        pageWidth: Int,
        pageHeight: Int
    ): Set<PdfCropIssue> {
        if (crop.normalizedBounds.size != 4 || pageWidth < 1 || pageHeight < 1)
            return setOf(PdfCropIssue.INVALID_CROP_BOUNDS)
        val (top, left, bottom, right) = crop.normalizedBounds
        if (!listOf(top, left, bottom, right).all { it in 0f..1f } ||
            bottom <= top || right <= left
        ) return setOf(PdfCropIssue.INVALID_CROP_BOUNDS)

        // Compare exact printed-number center points, not OCR bounding boxes
        // that may include adjacent question stems or diagrams.
        val printedInside = sourceNumbers.filter { anchor ->
            val midX = (anchor.left + anchor.right) / 2f / pageWidth
            val midY = (anchor.top + anchor.bottom) / 2f / pageHeight
            midX >= left && midX <= right && midY >= top && midY <= bottom
        }
        val issues = mutableSetOf<PdfCropIssue>()
        if (printedInside.map { it.number }.distinct().size > 1) {
            issues += PdfCropIssue.MULTIPLE_PRINTED_QUESTIONS_IN_ONE_CROP
        }
        if (crop.printedNumber > 0 && sourceNumbers.isNotEmpty() &&
            printedInside.none { it.number == crop.printedNumber }
        ) issues += PdfCropIssue.WRONG_OR_MISSING_PRINTED_START
        return issues
    }
}
