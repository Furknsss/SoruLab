package com.example.viewmodel

import com.example.data.QuestionVisualType
import com.example.data.YksQuestion
import java.util.Random

/**
 * Strict no-network assessment-style parameter engine.
 * Unknown wording/constraints are not modified: the checked AI path handles them.
 */
internal object TwinLocalTemplateEngine {
    private val functionPattern = Regex(
        """^\s*(?:Bir\s+)?([fg])\s*\(\s*x\s*\)\s*=\s*([+-]?\d{1,2})\s*x\s*([+-]\s*\d{1,2})\s+(?:fonksiyonu\s+için|fonksiyonu\s+veriliyor\.\s*Buna\s+göre|olduğuna\s+göre)\s+\1\s*\(\s*([+-]?\d{1,2})\s*\)\s+değeri\s+kaçtır\??\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val labels = listOf("A", "B", "C", "D", "E")
    private val deviations = listOf(-7, -4, -2, -1, 1, 2, 4, 7)

    fun generate(question: YksQuestion): TwinQuestionModalData? {
        // A crop/PDF may contain essential constraints that OCR text omitted.
        // Never apply a text-only numeric template to an uninspected source image.
        // Multimodal analysis must handle original drawings and scanned figures.
        if (question.croppedImagePath.isNotBlank() ||
            question.imageBase64.isNotBlank() ||
            question.pdfFilePath.isNotBlank()
        ) return null
        if (!question.answerKeyVerified || question.visualType != QuestionVisualType.NONE) return null
        val original = (question.stemTop + " " + question.stemBottom)
            .replace(Regex("""\s+"""), " ").trim()
        val match = functionPattern.matchEntire(original) ?: return null
        val fn = match.groupValues[1].lowercase()
        val oldA = match.groupValues[2].toIntOrNull() ?: return null
        val oldB = match.groupValues[3].replace(" ", "").toIntOrNull() ?: return null
        val oldX = match.groupValues[4].toIntOrNull() ?: return null
        val oldAnswer = oldA * oldX + oldB
        val originalChoice = question.correctOption.trim().uppercase()
        if (originalChoice !in labels || question.options.size != 5) return null
        if (question.options[originalChoice]?.trim()?.toIntOrNull() != oldAnswer ||
            question.options.values.count { it.trim().toIntOrNull() == oldAnswer } != 1
        ) return null

        val random = Random(question.id.hashCode().toLong() xor (oldAnswer.toLong() shl 16))
        val newA = ((2 + random.nextInt(7)) * if (oldA < 0) -1 else 1)
            .let { if (it == oldA) it + (if (it < 0) -1 else 1) else it }
        val newB = ((2 + random.nextInt(11)) * if (oldB < 0) -1 else 1)
            .let { if (it == oldB) it + (if (it < 0) -1 else 1) else it }
        val newX = (2 + random.nextInt(8)).let { if (it == oldX) it + 1 else it }
        val answer = newA * newX + newB
        val answerIndex = random.nextInt(5)
        val distractors = deviations.shuffled(kotlin.random.Random(random.nextLong()))
            .take(4).map { answer + it }.toMutableList()
        distractors.add(answerIndex, answer)
        val options = labels.mapIndexed { index, label -> label to distractors[index].toString() }.toMap()
        val letter = labels[answerIndex]
        val bFormatted = if (newB < 0) " - " + (-newB) else " + " + newB
        val stem = "Bir " + fn + "(x) = " + newA + "x" + bFormatted +
            " fonksiyonu için " + fn + "(" + newX + ") değeri kaçtır?"
        val explanation = fn + "(" + newX + ") = " + newA + " × " + newX + " " +
            (if (newB < 0) "- " + (-newB) else "+ " + newB) +
            " = " + answer + ". Önce x yerine " + newX +
            " yazılır, ardından çarpma ve toplama/çıkarma yapılır."
        return TwinQuestionModalData(
            topicTitle = question.topicHeader,
            difficultyLabel = question.difficulty.label,
            questionStem = stem,
            options = options,
            correctOption = letter,
            solutionExplanation = explanation,
            sourceDescription = "Cihazda üretildi • aynı işlem sırası • tam sayı hesabıyla doğrulandı"
        )
    }
}
