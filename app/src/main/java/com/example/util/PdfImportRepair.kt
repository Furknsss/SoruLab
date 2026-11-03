package com.example.util

import com.example.data.CustomQuestionRecord

/** Only unique source identities may replace existing questions during re-import. */
internal fun indexPdfQuestionsForRepair(records: List<CustomQuestionRecord>): Map<Triple<String, Int, Int>, CustomQuestionRecord> =
    records.filter { it.bookId.isNotBlank() && it.pageIndex >= 0 && it.questionNumberInTest > 0 }
        .groupBy { Triple(it.bookId, it.pageIndex, it.questionNumberInTest) }
        .mapNotNull { (identity, matches) -> matches.singleOrNull()?.let { identity to it } }
        .toMap()
