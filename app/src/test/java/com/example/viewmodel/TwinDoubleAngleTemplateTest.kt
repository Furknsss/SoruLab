package com.example.viewmodel

import com.example.data.QuestionVisualType
import com.example.data.YksQuestion
import org.junit.Assert.*
import org.junit.Test

class TwinDoubleAngleTemplateTest {
    private val original = YksQuestion(
        id = "trig-verified-001",
        number = 1,
        stemTop = "Dar açı olan α için sinα=3/5 olduğuna göre cos2α kaçtır?",
        stemBottom = "",
        options = mapOf(
            "A" to "1/25", "B" to "3/25", "C" to "7/25",
            "D" to "11/25", "E" to "13/25"
        ),
        correctOption = "C",
        stepByStepSolution = "1-2*(3/5)^2 = 7/25",
        topicHeader = "Trigonometri",
        answerKeyVerified = true,
        visualType = QuestionVisualType.NONE
    )

    private fun value(text: String): Double {
        val pieces = text.split("/")
        return if (pieces.size == 2) {
            pieces[0].toDouble() / pieces[1].toDouble()
        } else pieces[0].toDouble()
    }

    @Test fun createsNewProblemWithOneVerifiedDoubleAngleValue() {
        val twin = requireNotNull(TwinDoubleAngleTemplate.generate(original))
        assertNotEquals(original.stemTop, twin.questionStem)
        assertEquals(5, twin.options.size)
        assertEquals(5, twin.options.values.map(::value).distinct().size)
        val m = requireNotNull(Regex("""sinα=(\d+)/(\d+)""").find(twin.questionStem))
        val p = m.groupValues[1].toDouble()
        val q = m.groupValues[2].toDouble()
        val expected = 1.0 - 2.0 * (p / q) * (p / q)
        assertEquals(expected, value(twin.options.getValue(twin.correctOption)), 1e-9)
        assertTrue(twin.solutionExplanation.contains("İki kat açı"))
    }

    @Test fun refusesContradictoryOrUncertainOriginal() {
        assertNull(TwinDoubleAngleTemplate.generate(original.copy(correctOption = "A")))
        assertNull(TwinDoubleAngleTemplate.generate(original.copy(answerKeyVerified = false)))
        assertNull(TwinDoubleAngleTemplate.generate(original.copy(pdfFilePath = "uninspected-book.pdf")))
        assertNull(TwinDoubleAngleTemplate.generate(
            original.copy(stemTop = original.stemTop + " Ayrıca tanα değeri de soruluyor.")
        ))
    }
}
