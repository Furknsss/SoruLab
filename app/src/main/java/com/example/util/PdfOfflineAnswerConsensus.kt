package com.example.util

internal data class PdfOfflineAnswerVerdict(
    val confirmed: Map<PdfAnswerId, String>,
    val disputed: Set<PdfAnswerId>,
    val unsupported: Set<PdfAnswerId>
) {
    fun confirmedOption(scope: PdfAnswerScope?, number: Int?): String? {
        if (scope == null || number == null) return null
        return confirmed[PdfAnswerId(scope.copy(
            section = PdfScopedAnswerKeys.normalize(scope.section),
            testKind = PdfScopedAnswerKeys.normalize(scope.testKind)
        ), number)]
    }
}

/** ML Kit must agree with a genuinely separate native PDF/Paddle reader. */
internal object PdfOfflineAnswerConsensus {
    fun compare(first: PdfScopedAnswerKeys, second: PdfScopedAnswerKeys): PdfOfflineAnswerVerdict {
        fun flatten(reader: PdfScopedAnswerKeys) = reader.readings().flatMap { (scope, cells) ->
            cells.map { (number, letter) -> PdfAnswerId(scope, number) to letter }
        }.toMap()
        val a = flatten(first)
        val b = flatten(second)
        val conflicts = first.conflicts() + second.conflicts()
        val confirmed = mutableMapOf<PdfAnswerId, String>()
        val disputed = mutableSetOf<PdfAnswerId>()
        val unsupported = mutableSetOf<PdfAnswerId>()
        for (id in a.keys + b.keys + conflicts) {
            when {
                id in conflicts -> disputed.add(id)
                a[id] == null || b[id] == null -> unsupported.add(id)
                a[id] != b[id] -> disputed.add(id)
                else -> confirmed[id] = a.getValue(id)
            }
        }
        return PdfOfflineAnswerVerdict(confirmed, disputed, unsupported)
    }
}
