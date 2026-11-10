package com.example.viewmodel

import com.example.data.QuestionVisualType
import com.example.data.YksQuestion
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Deterministic stress suite: 900 different source IDs, no model/API costs. */
@RunWith(RobolectricTestRunner::class)
class TwinParametricStressTest {
    private fun original(
        text: String,
        answers: Map<String, String>,
        correct: String,
        type: QuestionVisualType = QuestionVisualType.NONE
    ) = YksQuestion(
        id = "seed-source", number = 1, topicHeader = "Kazanım",
        stemTop = text, stemBottom = "", options = answers,
        correctOption = correct, stepByStepSolution = "Matematiksel doğrulama",
        answerKeyVerified = true, visualType = type
    )

    private val functionSource = original(
        "Bir f(x)=2x+3 fonksiyonu için f(4) değeri kaçtır?",
        mapOf("A" to "9", "B" to "10", "C" to "11", "D" to "12", "E" to "13"),
        "C"
    )
    private val rightTriangleSource = original(
        "ABC dik üçgeninde A açısı 90° ve AB=3 cm, AC=4 cm olduğuna göre BC kaç cm'dir?",
        mapOf("A" to "3", "B" to "4", "C" to "5", "D" to "6", "E" to "7"),
        "C", QuestionVisualType.GEOMETRY_TRIANGLE
    )
    private val trigSource = original(
        "Dar açı olan α için sinα=3/5 olduğuna göre cos2α kaçtır?",
        mapOf("A" to "1/25", "B" to "3/25", "C" to "7/25",
              "D" to "11/25", "E" to "13/25"),
        "C"
    )

    @Test fun stressTestLinearFunctionsWithIndependentRecalculation() {
        val regex = Regex("""f\(\s*x\s*\)\s*=\s*(-?\d+)x\s*([+-])\s*(\d+).*f\((\d+)\)""")
        repeat(300) { i ->
            val twin = requireNotNull(TwinLocalTemplateEngine.generate(
                functionSource.copy(id = "function-stress-" + i)
            ))
            val details = requireNotNull(regex.find(twin.questionStem))
            val a = details.groupValues[1].toInt()
            val b = details.groupValues[3].toInt() *
                (if (details.groupValues[2] == "-") -1 else 1)
            val x = details.groupValues[4].toInt()
            val value = a * x + b
            assertEquals(value, twin.options.getValue(twin.correctOption).toInt())
            assertEquals(5, twin.options.values.distinct().size)
            assertEquals(1, twin.options.values.count { it.toInt() == value })
        }
    }

    @Test fun stressTestRightTrianglesAndNewFigure() {
        val re = Regex("""AB=(\d+)\s*cm,\s*AC=(\d+)\s*cm""")
        repeat(300) { i ->
            val twin = requireNotNull(TwinRightTriangleTemplate.generate(
                rightTriangleSource.copy(id = "triangle-stress-" + i)
            ))
            val m = requireNotNull(re.find(twin.questionStem))
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            val c = twin.options.getValue(twin.correctOption).toInt()
            assertEquals(a * a + b * b, c * c)
            assertEquals(5, twin.options.values.distinct().size)
            val diagram = requireNotNull(TwinDiagramSpec.parse(twin.diagramJson))
            assertEquals("triangle", diagram.kind)
            assertEquals(3, diagram.lines.take(3).size)
            assertTrue(diagram.labels.any { it.text == a.toString() })
            assertTrue(diagram.labels.any { it.text == b.toString() })
        }
    }

    @Test fun stressTestDoubleAngleFractions() {
        fun fraction(raw: String): Double {
            val split = raw.split("/")
            return if (split.size == 2) split[0].toDouble() / split[1].toDouble()
            else split[0].toDouble()
        }
        val re = Regex("""sinα=(\d+)/(\d+)""")
        repeat(300) { i ->
            val twin = requireNotNull(TwinDoubleAngleTemplate.generate(
                trigSource.copy(id = "trig-stress-" + i)
            ))
            val m = requireNotNull(re.find(twin.questionStem))
            val p = m.groupValues[1].toDouble()
            val q = m.groupValues[2].toDouble()
            val expected = 1.0 - 2.0 * (p / q) * (p / q)
            assertEquals(expected, fraction(twin.options.getValue(twin.correctOption)), 1e-10)
            assertEquals(5, twin.options.values.map(::fraction).distinct().size)
            assertTrue(twin.options.values.all { kotlin.math.abs(fraction(it)) <= 1.0 })
        }
    }

    @Test fun trickyNewConditionsMustNotBeMisreadAsTheSamePattern() {
        val extra = listOf(
            " Ayrıca f fonksiyonu tek fonksiyondur.",
            " x yalnızca pozitif sayı olarak seçilecektir.",
            " Grafik eksenlerde farklı kesişimler gösteriyor.",
            " Yukarıdaki tablodaki verilere göre hesaplayınız.",
            " Bu bilgi çözüme yeterli değildir."
        )
        extra.forEach { tail ->
            assertNull(TwinLocalTemplateEngine.generate(
                functionSource.copy(stemTop = functionSource.stemTop + tail)
            ))
            assertNull(TwinRightTriangleTemplate.generate(
                rightTriangleSource.copy(stemTop = rightTriangleSource.stemTop + tail)
            ))
            assertNull(TwinDoubleAngleTemplate.generate(
                trigSource.copy(stemTop = trigSource.stemTop + tail)
            ))
        }
    }
}
