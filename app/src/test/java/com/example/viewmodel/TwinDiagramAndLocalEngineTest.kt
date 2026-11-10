package com.example.viewmodel

import com.example.data.YksQuestion
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class TwinLocalTemplateEngineTest {
    private val source = YksQuestion(
        id = "verified-fx-001",
        number = 1,
        stemTop = "Bir f(x)=2x+3 fonksiyonu için f(4) değeri kaçtır?",
        stemBottom = "",
        options = mapOf("A" to "9", "B" to "10", "C" to "11", "D" to "12", "E" to "13"),
        correctOption = "C",
        stepByStepSolution = "f(4)=2*4+3=11",
        topicHeader = "Fonksiyon Uygulamaları",
        answerKeyVerified = true
    )

    @Test fun producesOfflineTwinWithOneNewCorrectAnswer() {
        val twin = requireNotNull(TwinLocalTemplateEngine.generate(source))
        assertNull(twin.errorMessage)
        assertNotEquals(source.stemTop, twin.questionStem)
        assertEquals(5, twin.options.size)
        assertEquals(5, twin.options.values.distinct().size)
        assertNotNull(twin.options[twin.correctOption])
        assertTrue(twin.solutionExplanation.length > 40)
        assertTrue(twin.sourceDescription.contains("Cihazda"))
    }

    @Test fun requiresIndependentlyVerifiedSourceOption() {
        assertNull(TwinLocalTemplateEngine.generate(source.copy(answerKeyVerified = false)))
        assertNull(TwinLocalTemplateEngine.generate(source.copy(imageBase64 = "uninspected-figure")))
        assertNull(TwinLocalTemplateEngine.generate(source.copy(correctOption = "B")))
        assertNull(TwinLocalTemplateEngine.generate(source.copy(options = source.options + ("C" to "12"))))
    }

    @Test fun unknownOrExpandedWordingDoesNotProduceAnUnsafeTwin() {
        assertNull(TwinLocalTemplateEngine.generate(
            source.copy(stemTop = source.stemTop + " Ayrıca f(x) artan olmalıdır.")
        ))
        assertNull(TwinLocalTemplateEngine.generate(
            source.copy(stemTop = "Aşağıdaki grafiğe göre fonksiyonun sıfırı kaçtır?")
        ))
    }
}

@RunWith(RobolectricTestRunner::class)
class TwinDiagramSpecTest {
    private val triangle = """{"kind":"triangle",
        "lines":[
          {"x1":0.1,"y1":0.8,"x2":0.8,"y2":0.8},
          {"x1":0.8,"y1":0.8,"x2":0.2,"y2":0.2},
          {"x1":0.2,"y1":0.2,"x2":0.1,"y2":0.8}],
        "circles":[],
        "labels":[{"x":0.1,"y":0.9,"text":"A"}]}"""

    @Test fun acceptsBoundedTriangleAndRejectsMissingLabels() {
        assertEquals("triangle", TwinDiagramSpec.parse(triangle)?.kind)
        assertNull(TwinDiagramSpec.parse(triangle.replace("\"labels\":[{\"x\":0.1,\"y\":0.9,\"text\":\"A\"}]", "\"labels\":[]")))
    }

    @Test fun rejectsOffCanvasFractionsAndMissingArrows() {
        assertNull(TwinDiagramSpec.parse(triangle.replace("\"x2\":0.8", "\"x2\":1.8")))
        assertNull(TwinDiagramSpec.parse("""{"kind":"vectors",
          "lines":[{"x1":0.1,"y1":0.1,"x2":0.8,"y2":0.8}],
          "labels":[{"x":0.1,"y":0.1,"text":"F"}]}"""))
        assertNull(TwinDiagramSpec.parse("""{"kind":"unsupported","lines":[],"labels":[]}"""))
    }

    @Test fun qualityGateRejectsMissingDiagramAndWrongFamily() {
        val source = TwinSourceAnalysis(
            transcript = "ABC üçgeninde AB=5, AC=12 ve A açısı 90 derece. BC uzunluğu nedir?",
            subSkill = "Dik üçgende Pisagor bağıntısı",
            exactQuestionPattern = "İki dik kenardan hipotenüsü hesaplama",
            orderedSolutionOperations = "dik kenarları karele ve topla, karekök al, eş şıkkı bul",
            independentlySolvedOriginalOption = "C",
            legible = true,
            canWriteStandaloneTextTwin = false,
            requiresDiagram = true,
            diagramFamily = "triangle"
        )
        assertNull(TwinQuestionQualityGate.checkSource(source, "C"))
        val draft = TwinQuestionDraft(
            stem = "Yeni ABC dik üçgeninde AB=8 ve AC=15 santimetredir. BC uzunluğu kaçtır?",
            options = mapOf("A" to "16", "B" to "18", "C" to "15", "D" to "17", "E" to "20"),
            correctOption = "D",
            explanation = "Pisagor bağıntısından BC²=8²+15²=64+225=289 olduğundan BC=17.",
            transformationLog = "5 ve 12 olan dik kenarlar 8 ve 15 olarak değiştirildi.",
            originalPatternKept = "iki dik kenardan hipotenüs hesaplama",
            orderedOperationsKept = "kareleri topla ve sonuçtan karekök al",
            diagramJson = triangle
        )
        assertNull(TwinQuestionQualityGate.checkDraft(source, draft))
        assertNotNull(TwinQuestionQualityGate.checkDraft(source, draft.copy(diagramJson = "")))
        assertNotNull(TwinQuestionQualityGate.checkDraft(source, draft.copy(diagramJson = triangle.replace("triangle", "graph"))))
        val audit = TwinIndependentAudit(
            solvedOptionWithoutAnswerKey = "D",
            sameSpecificSubSkill = true, sameProblemArchitecture = true,
            sameOrderedReasoning = true, transformedMeaningfulParameters = true,
            independentOfUnavailableDiagram = true, singleUnambiguousAnswer = true,
            sufficientAndConsistentInformation = true, notSourceCopy = true,
            diagramMatchesQuestion = false
        )
        assertNotNull(TwinQuestionQualityGate.checkIndependentAudit(draft, audit))
    }
}


@RunWith(RobolectricTestRunner::class)
class TwinRightTriangleTemplateTest {
    private val source = YksQuestion(
        id = "verified-345-triangle",
        number = 1,
        stemTop = "ABC dik üçgeninde A açısı 90° ve AB=3 cm, AC=4 cm olduğuna göre BC kaç cm'dir?",
        stemBottom = "",
        options = mapOf("A" to "3", "B" to "4", "C" to "5", "D" to "6", "E" to "7"),
        correctOption = "C",
        stepByStepSolution = "Pisagor bağıntısı: BC=5",
        topicHeader = "Dik Üçgenler",
        answerKeyVerified = true,
        visualType = com.example.data.QuestionVisualType.GEOMETRY_TRIANGLE
    )

    @Test fun createsNewNativeFigureAndRecomputedUniqueAnswer() {
        val twin = requireNotNull(TwinRightTriangleTemplate.generate(source))
        assertNotEquals(source.stemTop, twin.questionStem)
        assertNull(twin.errorMessage)
        assertEquals(5, twin.options.values.distinct().size)
        assertNotNull(TwinDiagramSpec.parse(twin.diagramJson))
        assertEquals("triangle", TwinDiagramSpec.parse(twin.diagramJson)?.kind)
        val values = Regex("""AB=(\d+)\s*cm,\s*AC=(\d+)\s*cm""")
            .find(twin.questionStem) ?: error("Missing twin leg lengths")
        val a = values.groupValues[1].toInt()
        val b = values.groupValues[2].toInt()
        val c = twin.options.getValue(twin.correctOption).toInt()
        assertEquals(a * a + b * b, c * c)
    }

    @Test fun rejectsFalseSourceAnswerOrUnknownWording() {
        assertNull(TwinRightTriangleTemplate.generate(source.copy(correctOption = "B")))
        assertNull(TwinRightTriangleTemplate.generate(source.copy(answerKeyVerified = false)))
        assertNull(TwinRightTriangleTemplate.generate(source.copy(croppedImagePath = "uninspected-source.png")))
        assertNull(TwinRightTriangleTemplate.generate(
            source.copy(stemTop = "Şekildeki üçgende bir açı bilinmiyor.")
        ))
    }
}
