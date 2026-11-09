package com.example.viewmodel

import org.junit.Assert.*
import org.junit.Test

/** Proof that merely sharing the same topic cannot pass the twin gate. */
class TwinQuestionQualityGateTest {
    private val original = TwinSourceAnalysis(
        transcript = "Bir f(x)=2x+3 fonksiyonu için f(4) değeri kaçtır? A)9 B)10 C)11 D)12 E)13",
        subSkill = "Doğrusal fonksiyonda yerine koyma",
        exactQuestionPattern = "ax+b biçimindeki fonksiyonda verilen x için f(x) hesaplama",
        orderedSolutionOperations = "verilen x sayısını ax+b ifadesine yerleştir, çarp, sabiti topla",
        independentlySolvedOriginalOption = "C",
        legible = true,
        canWriteStandaloneTextTwin = true
    )
    private val twin = TwinQuestionDraft(
        stem = "Bir g(x)=3x+5 fonksiyonu veriliyor. Buna göre g(4) değeri kaçtır?",
        options = mapOf("A" to "12", "B" to "14", "C" to "16", "D" to "17", "E" to "19"),
        correctOption = "D",
        explanation = "g(4)=3·4+5=12+5=17. Bu nedenle doğru cevap D seçeneğidir.",
        transformationLog = "Katsayı 2 yerine 3 ve sabit 3 yerine 5 seçildi.",
        originalPatternKept = "ax+b doğrusal fonksiyonuna değer yerleştirme",
        orderedOperationsKept = "x değerini yerine yaz, çarp, sabiti topla"
    )
    private val reviewed = TwinIndependentAudit(
        solvedOptionWithoutAnswerKey = "D",
        sameSpecificSubSkill = true,
        sameProblemArchitecture = true,
        sameOrderedReasoning = true,
        transformedMeaningfulParameters = true,
        independentOfUnavailableDiagram = true,
        singleUnambiguousAnswer = true,
        sufficientAndConsistentInformation = true,
        notSourceCopy = true
    )

    @Test fun acceptsOnlyStructurallyVerifiedAnalogue() {
        assertNull(TwinQuestionQualityGate.checkSource(original, "C"))
        assertNull(TwinQuestionQualityGate.checkDraft(original, twin))
        assertNull(TwinQuestionQualityGate.checkIndependentAudit(twin, reviewed))
    }

    @Test fun rejectsSameTopicDifferentReasoning() {
        assertNotNull(TwinQuestionQualityGate.checkIndependentAudit(
            twin, reviewed.copy(sameOrderedReasoning = false)
        ))
        assertNotNull(TwinQuestionQualityGate.checkIndependentAudit(
            twin, reviewed.copy(sameProblemArchitecture = false)
        ))
        assertNotNull(TwinQuestionQualityGate.checkIndependentAudit(
            twin, reviewed.copy(sameSpecificSubSkill = false)
        ))
    }

    @Test fun rejectsWrongAnswerEvenIfEveryOtherFlagIsYes() {
        assertNotNull(TwinQuestionQualityGate.checkIndependentAudit(
            twin, reviewed.copy(solvedOptionWithoutAnswerKey = "A")
        ))
    }

    @Test fun rejectsDuplicateOptionsAndMissingVisual() {
        assertNotNull(TwinQuestionQualityGate.checkDraft(
            original, twin.copy(options = twin.options + ("E" to "17"))
        ))
        assertNotNull(TwinQuestionQualityGate.checkDraft(
            original, twin.copy(stem = "Yukarıdaki şekle göre g(4) değeri kaçtır?")
        ))
        assertNotNull(TwinQuestionQualityGate.checkIndependentAudit(
            twin, reviewed.copy(independentOfUnavailableDiagram = false)
        ))
    }

    @Test fun rejectsUnchangedSourceAndUnverifiedOriginal() {
        assertNotNull(TwinQuestionQualityGate.checkDraft(
            original, twin.copy(stem = original.transcript)
        ))
        assertNotNull(TwinQuestionQualityGate.checkSource(original, "B"))
        assertNotNull(TwinQuestionQualityGate.checkSource(
            original.copy(canWriteStandaloneTextTwin = false), null
        ))
        assertNotNull(TwinQuestionQualityGate.checkIndependentAudit(
            twin, reviewed.copy(singleUnambiguousAnswer = false)
        ))
    }

    @Test fun rejectsMissingMeaningfulParameterChangeAndAmbiguity() {
        assertNotNull(TwinQuestionQualityGate.checkIndependentAudit(
            twin, reviewed.copy(transformedMeaningfulParameters = false)
        ))
        assertNotNull(TwinQuestionQualityGate.checkIndependentAudit(
            twin, reviewed.copy(sufficientAndConsistentInformation = false)
        ))
        assertNotNull(TwinQuestionQualityGate.checkDraft(
            original, twin.copy(stem = "Bir f(x)=2x+3 fonksiyonu için f(4) değeri kaçtır?")
        ))
    }
}
