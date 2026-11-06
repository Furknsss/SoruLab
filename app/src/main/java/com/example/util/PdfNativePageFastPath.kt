package com.example.util

/**
 * A strict, publisher-independent native-PDF candidate for avoiding expensive
 * whole-page raster OCR. It NEVER validates an answer by itself.
 *
 * The real rendered footer must also be read by ML Kit and exactly agree with
 * the native key; otherwise the caller must do the original full OCR path.
 * Scanned/image-only pages have no trustworthy native text and stay unchanged.
 */
internal object PdfNativePageFastPath {
    fun canTry(
        printedQuestionStarts: List<QuestionNumberAnchor>,
        nativeFooterAnswers: Map<Int, String>,
        hasNativeChoices: Boolean,
        hasPrintedTestIdentity: Boolean,
        hasPageText: Boolean,
        pageWidth: Int,
        pageHeight: Int
    ): Boolean {
        val count = printedQuestionStarts.size
        if (!hasNativeChoices || !hasPrintedTestIdentity || !hasPageText ||
            pageWidth < 300 || pageHeight < 400 ||
            count !in 4..40 || nativeFooterAnswers.size != count
        ) return false
        val expected = (1..count).toSet()
        if (printedQuestionStarts.map { it.number }.toSet() != expected ||
            nativeFooterAnswers.keys != expected ||
            nativeFooterAnswers.values.any { it !in setOf("A", "B", "C", "D", "E") }
        ) return false
        return printedQuestionStarts.all { anchor ->
            anchor.left >= 0 && anchor.right <= pageWidth &&
                anchor.top >= (pageHeight * .04f).toInt() &&
                anchor.bottom <= (pageHeight * .92f).toInt() &&
                anchor.right > anchor.left && anchor.bottom > anchor.top
        }
    }

    fun independentFooterAgrees(
        nativeFooterAnswers: Map<Int, String>,
        imageFooterAnswers: Map<Int, String>
    ): Boolean = nativeFooterAnswers.size >= 4 &&
        nativeFooterAnswers == imageFooterAnswers
}
