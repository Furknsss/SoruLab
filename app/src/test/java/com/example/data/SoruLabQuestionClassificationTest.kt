package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoruLabQuestionClassificationTest {
    @Test
    fun automaticBookTestsUseWheelDifficultyAndAtMostTwelveQuestions() {
        val questions = buildList {
            repeat(13) { index ->
                add(bookQuestion("hard_$index", "Zor", index + 1))
            }
            add(bookQuestion("osym", "ÖSYM", 14))
            add(bookQuestion("medium", "Orta", 15))
        }

        val chunks = automaticBookTestChunks(questions, TopicTestLevel.DERECE)

        assertEquals(listOf(12, 2), chunks.map { it.size })
        assertTrue(chunks.flatten().all { it.difficultyLevel5 in TopicTestLevel.DERECE.recommendedDifficultyLevels() })
        assertEquals(listOf(1), automaticBookTestChunks(questions, TopicTestLevel.BASLANGIC).map { it.size })
    }

    private fun bookQuestion(id: String, difficulty: String, number: Int) = CustomQuestionRecord(
        id = id,
        aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta(
            solutionText = "",
            questionNumberInBook = number,
            fiveTierDifficulty = difficulty
        )
    )

    @Test
    fun publisherPickerContainsOnlyUserAddedPublishers() {
        val publisher = YksPublisherInfo(
            id = "custom_apotemi",
            name = "Apotemi",
            shortCode = "APO",
            primaryColorHex = 0xFF1D4ED8,
            secondaryColorHex = 0xFF0F172A,
            customLogoPath = "/logos/apotemi.png"
        )

        assertTrue(SoruLabPublisherPresets.defaultPublishers.isEmpty())
        assertEquals(listOf(publisher), SoruLabPublisherPresets.mergedPublishers(listOf(publisher)))
        assertEquals("Apotemi", SoruLabPublisherPresets.resolvePublisher("Apotemi", listOf(publisher)).name)
    }

    @Test
    fun polynomialSymbolsOverrideSuggestedTopic() {
        val result = SoruLabCatalog.classifyQuestionTopicFromText(
            examTypeHint = "AYT",
            subjectNameHint = "Matematik",
            questionText = "P(x) polinomunun derecesi 4 olduğuna göre bölümünden kalan kaçtır?",
            suggestedTopicName = "Limit ve Süreklilik"
        )

        assertNotNull(result)
        assertEquals("Polinomlar", result?.second?.name)
    }

    @Test
    fun suggestedPageTopicIsUsedWhenQuestionTextHasNoTopicSignal() {
        val result = SoruLabCatalog.classifyQuestionTopicFromText(
            examTypeHint = "AYT",
            subjectNameHint = "Matematik",
            questionText = "Verilen koşullara göre doğru seçeneği bulunuz.",
            suggestedTopicName = "Polinomlar"
        )

        assertNotNull(result)
        assertEquals("AYT", result?.first?.examType?.name)
        assertEquals("Matematik", result?.first?.name)
        assertEquals("Polinomlar", result?.second?.name)
    }

    @Test
    fun complexNumberKeywordsSelectComplexNumbers() {
        val result = SoruLabCatalog.classifyQuestionTopicFromText(
            examTypeHint = "AYT",
            subjectNameHint = "Matematik",
            questionText = "z karmaşık sayısının eşleniği aşağıdakilerden hangisidir?"
        )

        assertNotNull(result)
        assertEquals("Karmaşık Sayılar", result?.second?.name)
    }

    @Test
    fun parabolKeywordsSelectParabola() {
        val result = SoruLabCatalog.classifyQuestionTopicFromText(
            examTypeHint = "AYT",
            subjectNameHint = "Matematik",
            questionText = "Parabolün tepe noktası ve simetri ekseni verilmiştir."
        )

        assertNotNull(result)
        assertEquals("Parabol", result?.second?.name)
    }

    @Test
    fun derivativeKeywordsCannotBeClassifiedAsLimit() {
        val result = SoruLabCatalog.classifyQuestionTopicFromText(
            examTypeHint = "AYT",
            subjectNameHint = "Matematik",
            questionText = "f'(x) = lim(h→0) [f(x+h)-f(x)]/h olduğuna göre türev kaçtır?",
            suggestedTopicName = "Limit ve Süreklilik"
        )

        assertNotNull(result)
        assertEquals("Türev Alma Kuralları", result?.second?.name)
    }

    @Test
    fun limitWithoutDerivativeSelectsLimitAndContinuity() {
        val result = SoruLabCatalog.classifyQuestionTopicFromText(
            examTypeHint = "AYT",
            subjectNameHint = "Matematik",
            questionText = "lim x→0 iken f(x) fonksiyonunun sürekliliğini inceleyiniz."
        )

        assertNotNull(result)
        assertEquals("Limit ve Süreklilik", result?.second?.name)
    }
}
