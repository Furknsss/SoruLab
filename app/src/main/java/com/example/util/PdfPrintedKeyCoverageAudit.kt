package com.example.util

/** Whole-test coverage, independent from whether each JPEG is nonblank. */
internal data class PdfPrintedKeyCoverage(
    val missingSourceQuestions: Set<PdfAnswerId>,
    val duplicateImportedQuestions: Set<PdfAnswerId>,
    val totalSourceNumbers: Int
) {
    val complete: Boolean
        get() = totalSourceNumbers > 0 && missingSourceQuestions.isEmpty() &&
            duplicateImportedQuestions.isEmpty()
}

internal object PdfPrintedKeyCoverageAudit {
    fun assess(
        independentPrintedKeys: List<Pair<PdfAnswerScope, List<Pair<Int, String>>>>,
        extracted: List<PdfAnswerId>
    ): PdfPrintedKeyCoverage {
        fun normalized(id: PdfAnswerId): PdfAnswerId = id.copy(
            scope = id.scope.copy(
                section = PdfScopedAnswerKeys.normalize(id.scope.section),
                testKind = PdfScopedAnswerKeys.normalize(id.scope.testKind)
            )
        )
        val expected = independentPrintedKeys.flatMap { (scope, cells) ->
            cells.mapNotNull { (number, option) ->
                if (number !in 1..200 || option !in listOf("A", "B", "C", "D", "E")) null
                else normalized(PdfAnswerId(scope, number))
            }
        }.toSet()
        val observed = extracted.map(::normalized)
        val duplicates = observed.groupBy { it }.filterValues { it.size > 1 }.keys
        return PdfPrintedKeyCoverage(
            missingSourceQuestions = expected - observed.toSet(),
            duplicateImportedQuestions = duplicates,
            totalSourceNumbers = expected.size
        )
    }
}
