package com.example.util

/**
 * Reuses the OCR text already read from the entire rendered PDF page.
 *
 * Import previously re-opened every cropped JPEG and ran ML Kit for every
 * question, even where the page OCR had already read the stem and all choices.
 * This safe fast path avoids that duplicate work for complete, readable
 * question regions. Weak/missing text MUST take the existing per-crop OCR path.
 *
 * This supplies classification text only; it NEVER assigns a question number,
 * section, test, answer key or crop confidence.
 */
/**
 * Selectable PDF text already decoded by PDFBox once for the blueprint.
 * Coordinates are in the original PDF page units, not rendered pixels.
 * A crop is still required for the visible original artwork and equations.
 */
data class PdfSourceTextFragment(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

internal object PdfPageQuestionTextReuse {
    /**
     * PDFBox decodes searchable question text without model inference.
     * Reuse it only when every original A-E choice is inside the crop.
     * If layout/text extraction is weak, the existing independent ML Kit
     * question OCR is still required.
     */
    fun readNativeIfComplete(
        fragments: List<PdfSourceTextFragment>,
        normalizedCrop: List<Float>,
        pdfPageWidth: Float,
        pdfPageHeight: Float
    ): String? {
        if (pdfPageWidth <= 0f || pdfPageHeight <= 0f ||
            fragments.isEmpty()) return null
        // Fixed precision retains actual PDF geometry: rendering resolution
        // should not affect whether a fragment belongs to a question.
        val pageWidth = (pdfPageWidth * 100f).toInt()
        val pageHeight = (pdfPageHeight * 100f).toInt()
        if (pageWidth <= 0 || pageHeight <= 0) return null
        val cells = fragments.filter { f ->
            f.text.isNotBlank() && f.text.length <= 800 &&
                f.left >= 0f && f.top >= 0f &&
                f.right <= pdfPageWidth && f.bottom <= pdfPageHeight &&
                f.right > f.left && f.bottom > f.top
        }.map { f ->
            PdfOcrTextCell(
                text = f.text,
                left = (f.left * 100f).toInt(),
                top = (f.top * 100f).toInt(),
                right = (f.right * 100f).toInt(),
                bottom = (f.bottom * 100f).toInt()
            )
        }
        return readIfComplete(cells, normalizedCrop, pageWidth, pageHeight)
    }

    private val printedOption = Regex(
        """(?i)(?<![A-ZÇĞİÖŞÜ])([A-E])\s*[).:]"""
    )

    fun readIfComplete(
        lines: List<PdfOcrTextCell>,
        normalizedCrop: List<Float>,
        pageWidth: Int,
        pageHeight: Int
    ): String? {
        if (pageWidth <= 0 || pageHeight <= 0 || normalizedCrop.size != 4 ||
            !normalizedCrop.all { it in 0f..1f } ||
            normalizedCrop[2] <= normalizedCrop[0] ||
            normalizedCrop[3] <= normalizedCrop[1]
        ) return null
        val left = normalizedCrop[1] * pageWidth
        val right = normalizedCrop[3] * pageWidth
        val top = normalizedCrop[0] * pageHeight
        val bottom = normalizedCrop[2] * pageHeight

        // Require the full OCR line inside the *same* crop. On two-column
        // pages, OCR may erroneously join text across the gutter: never borrow
        // the neighbouring question or printed answer table.
        val inside = lines.filter { cell ->
            cell.text.isNotBlank() && cell.right > cell.left &&
                cell.bottom > cell.top &&
                cell.left >= left - 3f && cell.right <= right + 3f &&
                cell.top >= top - 3f && cell.bottom <= bottom + 3f
        }.sortedWith(compareBy(PdfOcrTextCell::top, PdfOcrTextCell::left))

        if (inside.size < 5) return null
        val text = inside.joinToString("\n") { it.text.trim() }
        if (text.length < 90 ||
            text.count(Char::isLetter) < 56 ||
            inside.count { it.text.count(Char::isLetter) >= 8 } < 2
        ) return null

        // Evidence of the full printed choice set. When a long mathematical
        // formula or figure makes whole-page OCR partial, use the existing
        // high-detail crop OCR instead of silently accepting incomplete text.
        val choices = printedOption.findAll(text)
            .map { it.groupValues[1].uppercase() }.toSet()
        if (!choices.containsAll(setOf("A", "B", "C", "D", "E"))) return null
        return text
    }
}
