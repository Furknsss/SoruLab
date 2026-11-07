package com.example.util

/**
 * Answer key printed inside the question pages themselves, as two table rows:
 *
 *     1 2 3 4 5 6 7 8 9 10
 *     E B E B E C A E D C
 *     1                      <- printed test number (optional)
 *
 * [answers] index 0 is question 1. Nothing is ever guessed: a row pair is accepted only
 * when the numbers run exactly 1..n and the letter row has exactly n letters A-E.
 */
internal data class InlineAnswerKey(
    val testNumber: Int?,
    val answers: List<String>
) {
    fun answerFor(questionNumber: Int): String? = answers.getOrNull(questionNumber - 1)
}

internal object PdfInlineAnswerKeys {
    private const val MIN_QUESTIONS = 4
    private const val MAX_QUESTIONS = 40
    private val letterToken = Regex("^[A-Ea-e]$")
    private val numberToken = Regex("^[0-9]{1,2}$")
    private val loneNumber = Regex("^[0-9]{1,3}$")

    /** Rows must be in top-to-bottom reading order (one string per horizontal text row). */
    fun parse(rows: List<String>): List<InlineAnswerKey> {
        val clean = rows.map { it.trim().replace(Regex("\\s+"), " ") }
        val found = mutableListOf<InlineAnswerKey>()
        var i = 0
        while (i < clean.size - 1) {
            val count = sequentialNumberRowSize(clean[i])
            if (count == null) {
                i++
                continue
            }
            val letters = letterRow(clean[i + 1])
            if (letters == null || letters.size != count) {
                i++
                continue
            }
            var testNumber: Int? = null
            for (offset in 2..4) {
                val candidate = clean.getOrNull(i + offset) ?: break
                if (loneNumber.matches(candidate)) {
                    testNumber = candidate.toIntOrNull()
                    break
                }
            }
            found += InlineAnswerKey(testNumber, letters)
            i += 2
        }
        return found
    }

    private fun sequentialNumberRowSize(row: String): Int? {
        val tokens = row.split(' ').filter { it.isNotEmpty() }
        if (tokens.size !in MIN_QUESTIONS..MAX_QUESTIONS) return null
        if (tokens.any { !numberToken.matches(it) }) return null
        for ((index, token) in tokens.withIndex()) {
            if (token.toInt() != index + 1) return null
        }
        return tokens.size
    }

    private fun letterRow(row: String): List<String>? {
        val tokens = row.split(' ').filter { it.isNotEmpty() }
        if (tokens.size !in MIN_QUESTIONS..MAX_QUESTIONS) return null
        if (tokens.any { !letterToken.matches(it) }) return null
        return tokens.map { it.uppercase() }
    }

    /**
     * Chooses the key for a question. A question uses the key on its own page; a page
     * without a key uses the next page's key only when numbering continues across the
     * two pages (e.g. questions 1-5 on one page, 6-10 on the next).
     */
    fun keyForQuestion(
        pageIndex: Int,
        questionNumber: Int,
        keysByPage: Map<Int, InlineAnswerKey>,
        minQuestionByPage: Map<Int, Int>,
        maxQuestionByPage: Map<Int, Int>
    ): String? {
        keysByPage[pageIndex]?.answerFor(questionNumber)?.let { return it }
        val next = keysByPage[pageIndex + 1] ?: return null
        if (keysByPage.containsKey(pageIndex)) return null
        val nextMin = minQuestionByPage[pageIndex + 1] ?: return null
        val thisMax = maxQuestionByPage[pageIndex] ?: return null
        if (nextMin <= thisMax) return null
        return next.answerFor(questionNumber)
    }
}
