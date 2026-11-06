package com.example.util

/**
 * A strict, publisher-independent native-PDF candidate for avoiding expensive
 * whole-page raster OCR. It NEVER validates an answer by itself.
 *
 * If this page has a native answer strip, its rendered image must also be
 * read by ML Kit and agree exactly. Continuation pages without a footer are
 * allowed ONLY when their printed start positions independently match image
 * OCR and their explicit test identity/choices are available.
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
            count !in 3..20
        ) return false
        // A publisher's single test often spans TWO PDF pages:
        // first page: questions 1..7, no answer strip
        // second page: questions 8..14, full 1..14 answer strip.
        // Never assume every question page has a local 1..N key.
        val numbers = printedQuestionStarts.map { it.number }
        val start = numbers.minOrNull() ?: return false
        val expected = (start until start + count).toSet()
        if (start !in 1..200 || numbers.toSet() != expected ||
            numbers.size != numbers.distinct().size
        ) return false
        if (nativeFooterAnswers.isNotEmpty()) {
            val keyNumbers = (1..nativeFooterAnswers.size).toSet()
            if (nativeFooterAnswers.size < 4 ||
                nativeFooterAnswers.keys != keyNumbers ||
                !nativeFooterAnswers.keys.containsAll(expected) ||
                nativeFooterAnswers.values.any { it !in setOf("A", "B", "C", "D", "E") }
            ) return false
        }
        if (!printedQuestionStarts.all { anchor ->
                anchor.left >= 0 && anchor.right <= pageWidth &&
                    anchor.top >= (pageHeight * .04f).toInt() &&
                    anchor.bottom <= (pageHeight * .92f).toInt() &&
                    anchor.right > anchor.left && anchor.bottom > anchor.top
            }) return false
        val xTolerance = maxOf(4, (pageWidth * .016f).toInt())
        val yTolerance = maxOf(6, (pageHeight * .012f).toInt())
        return printedQuestionStarts.indices.all { i ->
            (i + 1 until printedQuestionStarts.size).none { j ->
                val a = printedQuestionStarts[i]
                val b = printedQuestionStarts[j]
                kotlin.math.abs(a.left - b.left) < xTolerance &&
                    kotlin.math.abs(a.top - b.top) < yTolerance
            }
        }
    }

    fun independentFooterAgrees(
        nativeFooterAnswers: Map<Int, String>,
        imageFooterAnswers: Map<Int, String>
    ): Boolean = nativeFooterAnswers.size >= 4 &&
        nativeFooterAnswers == imageFooterAnswers
}
