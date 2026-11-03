package com.example.util

import kotlin.math.abs
import kotlin.math.max

/**
 * Recover numbered question starts from two *independent* OCR passes.
 *
 * This is layout evidence, not a publisher template: numbered question starts
 * form a vertically aligned, strictly increasing run in each reading column.
 * Small labels from graph axes and page footers are excluded if they do not
 * belong to the strongest ordered sequence. Never manufacture a number.
 */
internal object PdfPrintedAnchorConsensus {
    fun reconcile(
        primary: List<QuestionNumberAnchor>,
        secondPass: List<QuestionNumberAnchor>,
        pageWidth: Int,
        pageHeight: Int
    ): List<QuestionNumberAnchor> {
        if (pageWidth <= 0 || pageHeight <= 0 || secondPass.isEmpty()) return primary
        val candidates = (primary + secondPass)
            .filter { it.number in 1..200 && it.left in 0 until pageWidth &&
                it.top in 0 until pageHeight && it.bottom > it.top }
            .sortedWith(compareBy(QuestionNumberAnchor::left, QuestionNumberAnchor::top))
            .fold(mutableListOf<QuestionNumberAnchor>()) { out, anchor ->
                val exists = out.any {
                    it.number == anchor.number &&
                        abs(it.left - anchor.left) < pageWidth * .03f &&
                        abs(it.top - anchor.top) < pageHeight * .018f
                }
                if (!exists) out.add(anchor)
                out
            }
        if (candidates.size < 2) return primary

        val groups = mutableListOf<MutableList<QuestionNumberAnchor>>()
        val xTolerance = max(6, (pageWidth * .035f).toInt())
        candidates.forEach { anchor ->
            val group = groups.lastOrNull()
            if (group != null && abs(anchor.left - group.map { it.left }.average()) <= xTolerance) {
                group.add(anchor)
            } else groups.add(mutableListOf(anchor))
        }

        val minVerticalGap = max(12, (pageHeight * .027f).toInt())
        fun runs(items: List<QuestionNumberAnchor>): List<List<QuestionNumberAnchor>> {
            val sorted = items.sortedBy { it.top }
            val paths = sorted.indices.map { i -> listOf(sorted[i]) }.toMutableList()
            for (i in sorted.indices) for (j in 0 until i) {
                if (sorted[i].number == sorted[j].number + 1 &&
                    sorted[i].top - sorted[j].top >= minVerticalGap &&
                    paths[j].size + 1 > paths[i].size
                ) paths[i] = paths[j] + sorted[i]
            }
            return paths.sortedByDescending { it.size }.take(4)
        }

        data class Candidate(val list: List<QuestionNumberAnchor>, val columns: Int)
        val aligned = groups.map(::runs)
        val alternatives = mutableListOf<Candidate>()
        for (i in groups.indices) {
            for (left in aligned[i]) {
                if (left.size >= 2) alternatives.add(Candidate(left, 1))
                for (j in i + 1 until groups.size) {
                    val separation = groups[j].first().left - groups[i].first().left
                    if (separation < pageWidth * .20f) continue
                    for (right in aligned[j]) {
                        if (right.first().number == left.last().number + 1 &&
                            left.size + right.size >= 3
                        ) alternatives.add(Candidate(left + right, 2))
                    }
                }
            }
        }
        val best = alternatives.maxWithOrNull(
            compareBy<Candidate> { it.list.size + if (it.columns == 2) 2 else 0 }
                .thenBy { it.list.size }
                .thenBy { -it.list.first().left }
        )?.list ?: return primary
        val primaryValidated = primary.map { it.number }.toSet()
        val recoveredValidated = best.map { it.number }.toSet()
        // Do not discard an equally good primary result, or claim that a
        // single confirmed question has disappeared because of another pass.
        if (best.size < 2 || (best.size <= primary.size &&
                    !recoveredValidated.containsAll(primaryValidated))) return primary
        return best
    }
}
