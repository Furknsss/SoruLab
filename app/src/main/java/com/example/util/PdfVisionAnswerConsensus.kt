package com.example.util

/**
 * The vision model is never allowed to declare a correct answer on its own.
 * A question is gradeable only when its literal printed key, read by Gemini,
 * agrees with an independent OCR/PDF-vector answer reader for the SAME
 * (section, kind, test number, question number).
 */
internal data class PdfVisionAnswerVerdict(
    val confirmed: Map<PdfAnswerId, String>,
    val disputed: Set<PdfAnswerId>,
    val unsupported: Set<PdfAnswerId>
) {
    fun confirmedOption(scope: PdfAnswerScope?, number: Int?): String? {
        if (scope == null || number == null) return null
        val normalizedScope = scope.copy(
            section = PdfScopedAnswerKeys.normalize(scope.section),
            testKind = PdfScopedAnswerKeys.normalize(scope.testKind)
        )
        return confirmed[PdfAnswerId(normalizedScope, number)]
    }
}

internal object PdfVisionAnswerConsensus {
    fun compare(
        independentReader: PdfScopedAnswerKeys,
        visionReadings: List<Pair<PdfAnswerScope, List<Pair<Int, String>>>>
    ): PdfVisionAnswerVerdict {
        val allClaims = mutableMapOf<PdfAnswerId, MutableSet<String>>()
        for ((scope, cells) in visionReadings) {
            val normalizedScope = PdfAnswerScope(
                section = PdfScopedAnswerKeys.normalize(scope.section),
                testNumber = scope.testNumber,
                isOsymTest = scope.isOsymTest,
                testKind = PdfScopedAnswerKeys.normalize(scope.testKind)
            )
            for ((number, letter) in cells) {
                if (number !in 1..200 || letter !in listOf("A", "B", "C", "D", "E")) continue
                val id = PdfAnswerId(normalizedScope, number)
                allClaims.getOrPut(id) { linkedSetOf() }.add(letter)
            }
        }
        val confirmed = mutableMapOf<PdfAnswerId, String>()
        val disputed = mutableSetOf<PdfAnswerId>()
        val unsupported = mutableSetOf<PdfAnswerId>()
        for ((id, modelClaims) in allClaims) {
            val independent = independentReader.get(id.scope, id.questionNumber)
            when {
                modelClaims.size > 1 -> disputed.add(id)
                independent == null -> unsupported.add(id)
                modelClaims.single() != independent -> disputed.add(id)
                else -> confirmed[id] = independent
            }
        }
        return PdfVisionAnswerVerdict(confirmed, disputed, unsupported)
    }
}
