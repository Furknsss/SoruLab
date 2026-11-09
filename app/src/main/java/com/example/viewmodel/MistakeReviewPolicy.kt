package com.example.viewmodel

import com.example.data.YksQuestion

/** The original, verified mistakes; never replace them with same-topic items. */
internal object MistakeReviewPolicy {
    private val validAnswerOptions = setOf("A", "B", "C", "D", "E")

    fun wrongAnswersOnly(
        questions: List<YksQuestion>,
        answers: Map<Int, String>
    ): List<YksQuestion> = questions.filter { question ->
        val correct = question.correctOption.uppercase()
        val selected = answers[question.number]?.uppercase()?.trim()
        question.answerKeyVerified &&
            correct in validAnswerOptions &&
            selected in validAnswerOptions &&
            selected != correct
    }
}
