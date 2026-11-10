package com.example.viewmodel

import com.example.data.QuestionVisualType
import com.example.data.YksQuestion
import java.util.Random
import kotlin.math.abs

/**
 * Verified no-network double-angle trigonometry template. Only a tightly
 * recognized acute-angle sin(theta)=p/q -> cos(2 theta) form is supported.
 * Additional conditions and other solution patterns fall through to AI review.
 */
internal object TwinDoubleAngleTemplate {
    private val pattern = Regex(
        """^\s*Dar\s+açı\s+olan\s+([αθ])\s+için\s+sin\s*\1\s*=\s*(\d{1,2})\s*/\s*(\d{1,2})\s+olduğuna\s+göre\s+cos\s*2\s*\1\s+kaçtır\s*\?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val labels = listOf("A", "B", "C", "D", "E")
    private val rationalSines = listOf(
        3 to 5, 4 to 5, 5 to 13, 12 to 13,
        8 to 17, 15 to 17, 7 to 25, 24 to 25
    )

    private fun gcd(a: Int, b: Int): Int {
        var x = abs(a)
        var y = abs(b)
        while (y != 0) {
            val t = x % y
            x = y
            y = t
        }
        return x.coerceAtLeast(1)
    }

    private fun normalized(numerator: Int, denominator: Int): Pair<Int, Int> {
        if (denominator <= 0) return numerator to denominator
        val div = gcd(numerator, denominator)
        return numerator / div to denominator / div
    }

    private fun rationalText(n: Int, d: Int): String {
        val (num, den) = normalized(n, d)
        return if (den == 1) num.toString() else "$num/$den"
    }

    private fun fraction(text: String): Pair<Int, Int>? {
        val cleaned = text.trim().replace(" ", "")
        val parts = Regex("""^(-?\d{1,4})(?:/(\d{1,4}))?$""")
            .matchEntire(cleaned) ?: return null
        val n = parts.groupValues[1].toIntOrNull() ?: return null
        val d = parts.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 1
        if (d == 0) return null
        return normalized(n, d)
    }

    fun generate(question: YksQuestion): TwinQuestionModalData? {
        if (!question.answerKeyVerified || question.visualType != QuestionVisualType.NONE) {
            return null
        }
        val original = (question.stemTop + " " + question.stemBottom)
            .replace(Regex("""\s+"""), " ").trim()
        val match = pattern.matchEntire(original) ?: return null
        val letter = match.groupValues[1]
        val p = match.groupValues[2].toIntOrNull() ?: return null
        val q = match.groupValues[3].toIntOrNull() ?: return null
        if (p <= 0 || q <= p || q > 25) return null
        val oldNumerator = q * q - 2 * p * p
        val oldDenominator = q * q
        val originalChoice = question.correctOption.trim().uppercase()
        val oldValue = normalized(oldNumerator, oldDenominator)
        if (originalChoice !in labels || question.options.size != 5 ||
            fraction(question.options[originalChoice].orEmpty()) != oldValue ||
            question.options.values.count { fraction(it) == oldValue } != 1
        ) return null

        val random = Random(question.id.hashCode().toLong() xor oldNumerator.toLong())
        val chosen = rationalSines.filterNot { it.first == p && it.second == q }
            .let { it[random.nextInt(it.size)] }
        val newP = chosen.first
        val newQ = chosen.second
        val denominator = newQ * newQ
        val numerator = denominator - 2 * newP * newP
        val correctIndex = random.nextInt(5)
        // Cosine must lie between -1 and 1. Exclude trivially
        // impossible >1 or <-1 distractors even if they reflect a mistake.
        val incorrectNumerators = listOf(
            denominator - newP * newP,
            2 * newP * newP - denominator,
            denominator - 4 * newP * newP,
            denominator - 3 * newP * newP,
            newP * newP,
            2 * newP * newP,
            numerator + newQ,
            numerator - newQ,
            numerator + newP,
            numerator - newP
        ).filter { abs(it) <= denominator }
            .filter { normalized(it, denominator) != normalized(numerator, denominator) }
            .map { normalized(it, denominator) }.distinct()
            .shuffled(kotlin.random.Random(random.nextLong()))
            .take(4)
        if (incorrectNumerators.size != 4) return null
        val all = incorrectNumerators.toMutableList()
        all.add(correctIndex, normalized(numerator, denominator))
        val choices = labels.mapIndexed { index, key ->
            key to rationalText(all[index].first, all[index].second)
        }.toMap()
        val stem = "Dar açı olan $letter için sin$letter=$newP/$newQ olduğuna göre " +
            "cos2$letter kaçtır?"
        val explanation = "İki kat açı özdeşliği: cos2$letter = 1 - 2sin²$letter. " +
            "sin$letter=$newP/$newQ yazılır: 1 - 2 × ($newP/$newQ)² " +
            "= (" + denominator + " - " + (2 * newP * newP) + ")/" + denominator +
            " = " + rationalText(numerator, denominator) +
            ". Diğer şıklar bu işlemi sağlamaz."
        return TwinQuestionModalData(
            topicTitle = question.topicHeader,
            difficultyLabel = question.difficulty.label,
            questionStem = stem,
            options = choices,
            correctOption = labels[correctIndex],
            solutionExplanation = explanation,
            sourceDescription = "Cihazda üretildi • iki kat açı özdeşliği ve kesir sonucu doğrulandı"
        )
    }
}
