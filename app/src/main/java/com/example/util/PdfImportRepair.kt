package com.example.util

import com.example.data.CustomQuestionRecord

/** Only unique source identities may replace existing questions during re-import. */
internal fun indexPdfQuestionsForRepair(records: List<CustomQuestionRecord>): Map<Triple<String, Int, Int>, CustomQuestionRecord> =
    records.filter { it.bookId.isNotBlank() && it.pageIndex >= 0 && it.questionNumberInTest > 0 }
        .groupBy { Triple(it.bookId, it.pageIndex, it.questionNumberInTest) }
        .mapNotNull { (identity, matches) -> matches.singleOrNull()?.let { identity to it } }
        .toMap()


/**
 * Only an OCR-confirmed printed number may be used to join a PDF crop to
 * an answer key or to an existing question during re-import.
 *
 * The visual order within a page is not necessarily the printed question
 * number: two-column pages and continuation pages routinely restart counts.
 */
internal fun verifiedPrintedQuestionNumber(number: Int?): Int =
    number?.takeIf { it in 1..200 } ?: 0
