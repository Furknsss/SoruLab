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
        // A longer OCR sequence is not evidence that it belongs to printed
        // question starts: diagrams can also contain long numbered runs.
        // Never regress a first-pass start, even if the second pass has MORE
        // labels. Equal-length alternatives provide no additional coverage.
        if (best.size <= primary.size) return primary
        val matchX = max(8f, pageWidth * .035f)
        val matchY = max(12f, pageHeight * .025f)
        val primaryStarts = primary.filter {
            it.number in 1..200 && it.left in 0 until pageWidth &&
                it.top in 0 until pageHeight && it.bottom > it.top
        }
        if (primaryStarts.any { original ->
                best.none { candidate ->
                    candidate.number == original.number &&
                        abs(candidate.left - original.left) <= matchX &&
                        abs(candidate.top - original.top) <= matchY
                }
            }) return primary
        return best
    }

    /**
     * Three independently located OCR passes allow a recovery that ordinary
     * two-pass reconciliation cannot safely make: primary OCR occasionally
     * includes a graph number outside the actual 1..N question run. If both
     * independent passes concur about a LONGER physical run, we can discard
     * only that uncorroborated distractor without guessing an absent number.
     *
     * Never replace any primary anchor that has independent spatial support.
     * If evidence is insufficient, retain the conservative two-pass result.
     */
    fun reconcileThree(
        primary: List<QuestionNumberAnchor>,
        marginPass: List<QuestionNumberAnchor>,
        paddlePass: List<QuestionNumberAnchor>,
        pageWidth: Int,
        pageHeight: Int
    ): List<QuestionNumberAnchor> {
        val ordinary = reconcile(
            reconcile(primary, marginPass, pageWidth, pageHeight),
            paddlePass, pageWidth, pageHeight
        )
        if (pageWidth <= 0 || pageHeight <= 0 ||
            marginPass.isEmpty() || paddlePass.isEmpty()
        ) return ordinary

        val sources = listOf(primary, marginPass, paddlePass)
        val xTolerance = max(8f, pageWidth * .027f)
        val yTolerance = max(12f, pageHeight * .019f)
        fun samePhysical(a: QuestionNumberAnchor, b: QuestionNumberAnchor): Boolean =
            a.number == b.number &&
                abs(a.left - b.left) <= xTolerance &&
                abs(a.top - b.top) <= yTolerance

        fun support(anchor: QuestionNumberAnchor): Int =
            sources.count { pass -> pass.any { samePhysical(anchor, it) } }

        // Source order is significant only for geometry: preserve primary
        // coordinates when it agrees with the other readers.
        val corroborated = sources.flatten()
            .filter { it.number in 1..200 && it.left in 0 until pageWidth &&
                it.top in 0 until pageHeight && it.bottom > it.top &&
                support(it) >= 2 }
            .fold(mutableListOf<QuestionNumberAnchor>()) { out, anchor ->
                if (out.none { samePhysical(it, anchor) }) out.add(anchor)
                out
            }

        // The existing layout solver requires increasing, column-aligned
        // printed numbers; votes by themselves do not certify a question.
        val voted = reconcile(emptyList(), corroborated, pageWidth, pageHeight)
        if (voted.size < 3 || voted.size <= ordinary.size) return ordinary
        if (primary.filter { support(it) >= 2 }.any { original ->
                voted.none { samePhysical(original, it) }
            }) return ordinary
        return voted
    }
}
