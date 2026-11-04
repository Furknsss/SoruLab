package com.example.util

import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/**
 * Model-independent safety contract for the modern visual PDF import engine.
 *
 * The model may PROPOSE a question rectangle or transcribe a printed answer
 * key, but is never allowed to silently invent a numbered question, bridge
 * another question's printed number or grade from its own reasoning.
 *
 * All geometry is normalized to [0, 1000] and is validated in source pixels.
 */
internal data class PdfVisionQuestionBox(
    val number: Int,
    val top: Int,
    val left: Int,
    val bottom: Int,
    val right: Int
) {
    fun toNormalizedBox(): List<Int> = listOf(top, left, bottom, right)
}

internal data class PdfVisionKeyCell(val number: Int, val option: String)
internal data class PdfVisionKeyTest(
    val section: String,
    val kind: String,
    val testNumber: Int,
    val isOsym: Boolean,
    val cells: List<PdfVisionKeyCell>
)

internal data class PdfVisionPage(
    val pageType: String,
    val printedHeader: String,
    val questions: List<PdfVisionQuestionBox>,
    val answerTests: List<PdfVisionKeyTest>
)

internal object PdfVisionImportPolicy {
    private val printedKinds = setOf(
        "test", "analiz", "sentez", "deneme", "uygulama", "etkinlik"
    )

    fun parse(json: String): PdfVisionPage {
        val root = JSONObject(json)
        val pageType = root.getString("pageType").lowercase(java.util.Locale.ROOT)
        require(pageType in setOf("questions", "answer_key", "other")) {
            "Unknown visual page type"
        }
        val questions = root.getJSONArray("questions")
        val boxes = (0 until questions.length()).map { i ->
            val obj = questions.getJSONObject(i)
            PdfVisionQuestionBox(
                number = obj.getInt("number"),
                top = obj.getInt("top"),
                left = obj.getInt("left"),
                bottom = obj.getInt("bottom"),
                right = obj.getInt("right")
            )
        }
        val tests = root.getJSONArray("answerTests")
        val answerTests = (0 until tests.length()).map { i ->
            val test = tests.getJSONObject(i)
            val cells = test.getJSONArray("cells")
            PdfVisionKeyTest(
                section = test.getString("section").trim().take(80),
                kind = test.getString("kind").trim(),
                testNumber = test.getInt("testNumber"),
                isOsym = test.getBoolean("isOsym"),
                cells = (0 until cells.length()).map { j ->
                    val cell = cells.getJSONObject(j)
                    PdfVisionKeyCell(
                        number = cell.getInt("number"),
                        option = cell.getString("option").trim().uppercase(java.util.Locale.ROOT)
                    )
                }
            )
        }
        return PdfVisionPage(
            pageType = pageType,
            printedHeader = root.getString("printedHeader").trim().take(400),
            questions = boxes,
            answerTests = answerTests
        )
    }

    /**
     * Return NO candidates when a full page is inconsistent. A partially
     * plausible crop must not be presented as a successful PDF import.
     */
    fun validateQuestionBoxes(
        page: PdfVisionPage,
        pageWidth: Int,
        pageHeight: Int,
        independentPrintedAnchors: List<QuestionNumberAnchor>,
        requireExactPrintedCoverage: Boolean
    ): List<PdfVisionQuestionBox> {
        if (page.pageType != "questions" || pageWidth < 120 || pageHeight < 100 ||
            page.questions.isEmpty() || page.questions.size > 40
        ) return emptyList()
        val boxes = page.questions
        if (boxes.any { it.number !in 1..200 ||
                it.left !in 0..999 || it.top !in 0..999 ||
                it.right !in 1..1000 || it.bottom !in 1..1000 ||
                it.left >= it.right || it.top >= it.bottom ||
                (it.right - it.left) * pageWidth / 1000 < 120 ||
                (it.bottom - it.top) * pageHeight / 1000 < 80
            }) return emptyList()
        if (boxes.map { it.number }.distinct().size != boxes.size) return emptyList()

        val source = independentPrintedAnchors.filter {
            it.number in 1..200 && it.left >= 0 && it.top >= 0 &&
                it.right <= pageWidth && it.bottom <= pageHeight
        }
        if (source.map { it.number }.distinct().size != source.size) return emptyList()
        val sourceNumbers = source.map { it.number }.toSet()
        val detected = boxes.map { it.number }.toSet()
        if (requireExactPrintedCoverage && sourceNumbers != detected) return emptyList()
        if (!detected.containsAll(sourceNumbers)) return emptyList()

        fun containsAnchor(box: PdfVisionQuestionBox, anchor: QuestionNumberAnchor): Boolean {
            val left = box.left * pageWidth / 1000
            val right = box.right * pageWidth / 1000
            val top = box.top * pageHeight / 1000
            val bottom = box.bottom * pageHeight / 1000
            val x = (anchor.left + anchor.right) / 2
            val y = (anchor.top + anchor.bottom) / 2
            return x in (left - 12)..(right + 12) && y in (top - 12)..(bottom + 12)
        }
        for (anchor in source) {
            val owning = boxes.filter { containsAnchor(it, anchor) }
            if (owning.size != 1 || owning.single().number != anchor.number) return emptyList()
        }

        // Protect against a model merging the next printed question in the
        // same rectangle, even when it calls that rectangle question N.
        for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            val a = boxes[i]
            val b = boxes[j]
            val intersectionWidth = max(0, min(a.right, b.right) - max(a.left, b.left))
            val intersectionHeight = max(0, min(a.bottom, b.bottom) - max(a.top, b.top))
            val intersectionArea = intersectionWidth * intersectionHeight
            val smaller = min(
                (a.right - a.left) * (a.bottom - a.top),
                (b.right - b.left) * (b.bottom - b.top)
            )
            if (intersectionArea > smaller * 0.03f) return emptyList()
        }
        return boxes
    }

    /**
     * Transcribed cells are *not* the model's solution to any question.
     * Only a visible, numbered answer table with an explicit section/test
     * scope is eligible. Repeated/conflicting cells are quarantined.
     */
    fun verifiedPrintedKeyCandidates(page: PdfVisionPage):
        List<Pair<PdfAnswerScope, List<Pair<Int, String>>>> {
        // A real answer strip can be printed on the SAME page as numbered
        // questions. The independent OCR/PDF reader must corroborate every
        // proposed cell before the application can grade it.
        if (page.pageType !in setOf("answer_key", "questions")) return emptyList()
        return page.answerTests.mapNotNull { test ->
            val section = PdfScopedAnswerKeys.normalize(test.section)
            val kind = PdfScopedAnswerKeys.normalize(test.kind)
            if (section.length < 4 || kind !in printedKinds ||
                test.testNumber !in 1..200 || test.cells.size < 2
            ) return@mapNotNull null
            val byNumber = test.cells.groupBy { it.number }
            val valid = byNumber.mapNotNull { (number, answers) ->
                val distinct = answers.map { it.option }.distinct()
                if (number in 1..200 && distinct.size == 1 &&
                    distinct.single() in setOf("A", "B", "C", "D", "E")
                ) number to distinct.single() else null
            }
            if (valid.size < 2) return@mapNotNull null
            PdfAnswerScope(section, test.testNumber, test.isOsym, kind) to valid
        }
    }
}
