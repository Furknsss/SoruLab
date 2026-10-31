package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoruLabQuestionClassificationTest {
    @Test
    fun packedQuestionMetadataSanitizesDelimitersInCropAndLogoPaths() {
        val packed = CustomQuestionRecord.packSolutionWithMeta(
            solutionText = "Çözüm",
            croppedImagePath = "/cache/page]|crop.png",
            publisherLogoUri = "/cache/logo]|mark.png",
            targetLevel = "ÖSYM]"
        )
        val record = CustomQuestionRecord(aiSolutionSummary = packed)

        assertEquals("/cache/pagecrop.png", record.croppedImagePath)
        assertEquals("/cache/logomark.png", record.publisherLogoUri)
        assertEquals("ÖSYM", record.targetLevelName)
    }

    @Test
    fun emptyTestResultHasZeroQuestions() {
        assertEquals(0, TestResultRecord().totalQuestions)
    }

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
    fun physicsQuestionWithDegreeDoesNotJumpToAytMathPolynomial() {
        val result = SoruLabCatalog.classifyQuestionTopicFromText(
            examTypeHint = "AYT",
            subjectNameHint = "Fizik",
            questionText = "Bir cisim 30 derece açıyla atılıyor. Menzili kaç metredir?",
            suggestedTopicName = "Atışlar"
        )

        assertNotNull(result)
        assertEquals("Fizik", result?.first?.name)
        assertEquals("Atışlar", result?.second?.name)
    }

    @Test
    fun suggestedPageTopicCannotOverrideMathSpecificSignal() {
        val result = SoruLabCatalog.classifyQuestionTopicFromText(
            examTypeHint = "AYT",
            subjectNameHint = "Matematik",
            questionText = "P(x) polinomunun derecesi 4 olduğuna göre kalan kaçtır?",
            suggestedTopicName = "Limit ve Süreklilik"
        )

        assertNotNull(result)
        assertEquals("Matematik", result?.first?.name)
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

    @Test
    fun aytMathQuestionWithPhysicsWordsNeverClassifiedAsPhysics() {
        val result = SoruLabCatalog.classifyQuestionTopicFromText(
            examTypeHint = "AYT",
            subjectNameHint = "Matematik",
            questionText = "f(t) bir hareketlinin t anındaki konum fonksiyonudur. f'(t) anlık hız fonksiyonu olduğuna göre türevi kaçtır?",
            suggestedTopicName = ""
        )

        assertNotNull(result)
        assertEquals("Matematik", result?.first?.name)
        assertTrue(result?.first?.name != "Fizik")
        assertEquals("Türev Alma Kuralları", result?.second?.name)
    }

    @Test
    fun uncertainQuestionWithoutSignalRoutesToIncelemeBekliyor() {
        val result = SoruLabCatalog.classifyQuestionTopicFromText(
            examTypeHint = "AYT",
            subjectNameHint = "Matematik",
            questionText = "Yukarıda verilen bilgilere göre x kaçtır?",
            suggestedTopicName = "Bilinmeyen Başlık"
        )

        assertNotNull(result)
        assertEquals("Matematik", result?.first?.name)
        assertEquals("İnceleme Bekliyor", result?.second?.name)
        assertEquals("inceleme_bekliyor", result?.second?.id)
    }

    @Test
    fun trustedCatalogTopicBeatsKeywordRules() {
        val result = SoruLabCatalog.classifyQuestionTopicFromText(
            examTypeHint = "AYT",
            subjectNameHint = "Matematik",
            questionText = "P(x) polinomunun derecesi 4 olduğuna göre kalan kaçtır?",
            suggestedTopicName = "Limit ve Süreklilik",
            trustSuggestedTopic = true
        )

        assertNotNull(result)
        assertEquals("Limit ve Süreklilik", result?.second?.name)
    }

    @Test
    fun unknownTopicOrUntrustedHintStillUsesKeywordRules() {
        val unknownTopic = SoruLabCatalog.classifyQuestionTopicFromText(
            examTypeHint = "AYT",
            subjectNameHint = "Matematik",
            questionText = "P(x) polinomunun derecesi 4 olduğuna göre kalan kaçtır?",
            suggestedTopicName = "Bilinmeyen Başlık",
            trustSuggestedTopic = true
        )
        val untrusted = SoruLabCatalog.classifyQuestionTopicFromText(
            examTypeHint = "AYT",
            subjectNameHint = "Matematik",
            questionText = "P(x) polinomunun derecesi 4 olduğuna göre kalan kaçtır?",
            suggestedTopicName = "Limit ve Süreklilik"
        )

        assertEquals("Polinomlar", unknownTopic?.second?.name)
        assertEquals("Polinomlar", untrusted?.second?.name)
    }

    @Test
    fun questionMetadataAndLogoPersistenceArePreserved() {
        val originalSolution = "Açıklamalı Çözüm Adımları"
        val originalCrop = "/data/crops/crop_101.png"
        val originalLogo = "/data/logos/3d_logo.png"
        val originalLevel = "ÖSYM"
        val originalBook = "3D AYT Matematik"
        val originalCover = "/data/covers/cover_101.png"
        val originalDiff = "Zor"

        val packed = CustomQuestionRecord.packSolutionWithMeta(
            solutionText = originalSolution,
            targetLevel = originalLevel,
            croppedImagePath = originalCrop,
            publisherLogoUri = originalLogo,
            answerKeyVerified = true,
            questionNumberInBook = 7,
            bookOrTestName = originalBook,
            bookCoverPath = originalCover,
            fiveTierDifficulty = originalDiff
        )

        val record = CustomQuestionRecord(
            id = "test_q1",
            aiSolutionSummary = packed,
            publisherTag = "3D Yayınları",
            correctOption = "B"
        )

        assertEquals(originalCrop, record.croppedImagePath)
        assertEquals(originalLogo, record.publisherLogoUri)
        assertEquals(originalBook, record.bookOrTestName)
        assertEquals(originalDiff, record.effectiveDifficultyLabel)
        assertEquals(7, record.questionNumberInBook)
        assertEquals(true, record.answerKeyVerified)
        assertEquals("Zor", record.difficultyLevel5.label)
    }
}
