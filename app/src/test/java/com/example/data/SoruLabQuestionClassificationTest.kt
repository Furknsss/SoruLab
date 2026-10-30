package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SoruLabQuestionClassificationTest {
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
