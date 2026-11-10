package com.example.viewmodel

import com.example.data.QuestionVisualType
import com.example.data.YksQuestion
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random
import kotlin.math.sqrt

/** Fully calculated right-triangle twins with geometrically scaled NEW drawings. */
internal object TwinRightTriangleTemplate {
    private val pattern = Regex(
        """^ABC\s+dik\s+üçgeninde\s+A\s+açısı\s+90°\s+ve\s+AB\s*=\s*(\d{1,2})\s*cm,\s*AC\s*=\s*(\d{1,2})\s*cm\s+olduğuna\s+göre\s+BC\s+kaç\s+cm(?:'dir|dir)?\??$""",
        RegexOption.IGNORE_CASE
    )
    private val labels = listOf("A", "B", "C", "D", "E")
    private val triples = listOf(
        Triple(3, 4, 5), Triple(5, 12, 13),
        Triple(8, 15, 17), Triple(7, 24, 25),
        Triple(9, 40, 41), Triple(6, 8, 10)
    )

    fun generate(question: YksQuestion): TwinQuestionModalData? {
        // A crop/PDF may contain essential constraints that OCR text omitted.
        // Never apply a text-only numeric template to an uninspected source image.
        // Multimodal analysis must handle original drawings and scanned figures.
        if (question.croppedImagePath.isNotBlank() ||
            question.imageBase64.isNotBlank() ||
            question.pdfFilePath.isNotBlank()
        ) return null
        if (!question.answerKeyVerified || question.visualType !in
            setOf(QuestionVisualType.NONE, QuestionVisualType.GEOMETRY_TRIANGLE)) return null
        val text = (question.stemTop + " " + question.stemBottom)
            .replace(Regex("""\s+"""), " ").trim()
        val matched = pattern.matchEntire(text) ?: return null
        val originalA = matched.groupValues[1].toIntOrNull() ?: return null
        val originalB = matched.groupValues[2].toIntOrNull() ?: return null
        val originalC = sqrt((originalA * originalA + originalB * originalB).toDouble()).toInt()
        if (originalC * originalC != originalA * originalA + originalB * originalB) return null
        val sourceAnswer = question.correctOption.trim().uppercase()
        if (sourceAnswer !in labels || question.options.size != 5 ||
            question.options[sourceAnswer]?.trim()?.toIntOrNull() != originalC ||
            question.options.values.count { it.trim().toIntOrNull() == originalC } != 1
        ) return null

        val random = Random(question.id.hashCode().toLong() xor originalC.toLong())
        val choices = triples.filterNot {
            it.first == originalA && it.second == originalB ||
                it.first == originalB && it.second == originalA
        }
        val triple = choices[random.nextInt(choices.size)]
        val legA = triple.first
        val legB = triple.second
        val hyp = triple.third
        val correctIndex = random.nextInt(5)
        val distractors = listOf(hyp - 3, hyp - 2, hyp - 1, hyp + 1, hyp + 2, hyp + 3)
            .shuffled(kotlin.random.Random(random.nextLong())).take(4).toMutableList()
        distractors.add(correctIndex, hyp)
        val options = labels.mapIndexed { idx, k -> k to distractors[idx].toString() }.toMap()
        val stem = "ABC dik üçgeninde A açısı 90° ve AB=" + legA + " cm, AC=" +
            legB + " cm olduğuna göre BC kaç cm'dir?"
        val explanation = "A açısı 90° olduğu için Pisagor uygulanır: BC² = AB² + AC² = " +
            legA + "² + " + legB + "² = " + (legA * legA) + " + " +
            (legB * legB) + " = " + (hyp * hyp) + ". Dolayısıyla BC = " + hyp + " cm."
        val diagram = createDiagram(legA, legB)
        if (TwinDiagramSpec.parse(diagram) == null) return null
        return TwinQuestionModalData(
            topicTitle = question.topicHeader,
            difficultyLabel = question.difficulty.label,
            questionStem = stem,
            options = options,
            correctOption = labels[correctIndex],
            solutionExplanation = explanation,
            diagramJson = diagram,
            sourceDescription = "Cihazda üretildi • Pisagor ve orantılı yeni şekil hesaplandı"
        )
    }

    private fun createDiagram(ab: Int, ac: Int): String {
        val long = maxOf(ab, ac).toDouble()
        val ax = .16
        val ay = .82
        val bx = ax + .64 * ab / long
        val cy = ay - .64 * ac / long
        fun line(x1: Double, y1: Double, x2: Double, y2: Double) =
            JSONObject().put("x1", x1).put("y1", y1).put("x2", x2).put("y2", y2)
        fun label(x: Double, y: Double, text: String) =
            JSONObject().put("x", x).put("y", y).put("text", text)
        val lines = JSONArray()
            .put(line(ax, ay, bx, ay))
            .put(line(ax, ay, ax, cy))
            .put(line(bx, ay, ax, cy))
            .put(line(ax + .055, ay, ax + .055, ay - .055))
            .put(line(ax + .055, ay - .055, ax, ay - .055))
        val labels = JSONArray()
            .put(label(ax - .05, ay + .04, "A"))
            .put(label(bx + .04, ay + .04, "B"))
            .put(label(ax - .05, cy - .04, "C"))
            .put(label((ax + bx) / 2, ay + .06, ab.toString()))
            .put(label(ax - .075, (ay + cy) / 2, ac.toString()))
            .put(label((ax + bx) / 2 + .045, (ay + cy) / 2 - .04, "?"))
        return JSONObject()
            .put("kind", "triangle")
            .put("lines", lines)
            .put("circles", JSONArray())
            .put("labels", labels)
            .toString()
    }
}
