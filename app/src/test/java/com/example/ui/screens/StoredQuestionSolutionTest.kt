package com.example.ui.screens

import com.example.data.YksQuestion
import org.junit.Assert.*
import org.junit.Test

class StoredQuestionSolutionTest {
    private fun question(text: String = "", verified: Boolean = true, answer: String = "C") = YksQuestion(
        id = "stored-q", number = 1, stemTop = "", stemBottom = "", options = emptyMap(),
        correctOption = answer, answerKeyVerified = verified, stepByStepSolution = text
    )

    @Test fun verifiedAnswerAloneNeverBecomesAnExplanation() {
        val content = StoredQuestionSolution.from(question(), "")
        assertEquals("C", content.verifiedAnswer)
        assertFalse(content.hasSolution)
        assertTrue(content.sections.isEmpty())
    }

    @Test fun unverifiedOrInvalidAnswerIsNeverPresentedAsCorrect() {
        for (source in listOf(question("Kaynak açıklaması.", false), question(answer = "?"))) {
            val content = StoredQuestionSolution.from(source, "")
            assertNull(content.verifiedAnswer)
            assertTrue(content.answerNotice.contains("doğrulanmadı"))
        }
    }

    @Test fun plainSourceTextDoesNotAcquireInventedTheoremsOrTrapNotes() {
        val content = StoredQuestionSolution.from(question("x = 2 için f(2) = 5 bulunur."), "")
        assertEquals(listOf(StoredSolutionSection("Kayıtlı çözüm", "x = 2 için f(2) = 5 bulunur.")), content.sections)
    }

    @Test fun metadataPrefixIsHiddenAndMetadataAloneIsEmpty() {
        val content = StoredQuestionSolution.from(question("[META:ak=1|img=private.png] Açıklama."), "")
        assertEquals("Açıklama.", content.sections.single().body)
        assertFalse(StoredQuestionSolution.from(question("[META:ak=1]"), "[META:ak=1]").hasSolution)
        assertFalse(StoredQuestionSolution.from(question("[META:img=private.pdf"), "").hasSolution)
        assertEquals("Metin.", StoredQuestionSolution.from(question(), "[META:a=1][META:b=2] Metin.")
            .sections.single().body)
    }

    @Test fun headingsWithoutEmojiKeepSectionsSeparateAndPreserveMath() {
        val text = "Kritik Bilgi & Teorem: f(x)=x²\n" +
            "Adım Adım Çözüm: 1) f'(x)=2x\n2) f'(2)=4\n" +
            "ÖSYM Tuzak Notu: x=0 noktasını ayrıca kontrol et."
        val sections = StoredQuestionSolution.from(question(text), "").sections
        assertEquals(3, sections.size)
        assertEquals("f(x)=x²", sections[0].body)
        assertEquals("1) f'(x)=2x\n2) f'(2)=4", sections[1].body)
        assertEquals("x=0 noktasını ayrıca kontrol et.", sections[2].body)
    }

    @Test fun partialStructuredSourceShowsOnlyExistingSections() {
        val content = StoredQuestionSolution.from(question("📌 Kritik Bilgi & Teorem: Alan = taban × yükseklik / 2"), "")
        assertEquals(1, content.sections.size)
        assertEquals("Alan = taban × yükseklik / 2", content.sections.single().body)
    }

    @Test fun offlineMissingMessageIsAStatusNotASolution() {
        val message = "Bu soru için kayıtlı çözüm bulunmuyor. Ücretsiz sürümde bulutla çözüm üretilmez."
        assertFalse(StoredQuestionSolution.from(question(), message).hasSolution)
        assertEquals("Kaynak açıklaması.", StoredQuestionSolution.from(question("Kaynak açıklaması."), message)
            .sections.single().body)
    }

    @Test fun returnedMetadataCannotLeakThroughTheCallback() {
        assertEquals("İşlem.", StoredQuestionSolution.from(question(), "[META:source=private] İşlem.")
            .sections.single().body)
    }

    @Test fun closedOrRetriedRequestRejectsOldCallbackEvenForSameQuestion() {
        val gate = StoredSolutionRequestGate()
        val first = gate.begin("q")
        gate.cancel()
        assertFalse(gate.accepts(first, "q"))
        val second = gate.begin("q")
        assertFalse(gate.accepts(first, "q"))
        assertTrue(gate.accepts(second, "q"))
        assertFalse(gate.accepts(second, "another-q"))
        gate.begin("another-q")
        assertFalse(gate.accepts(second, "q"))
    }
}
