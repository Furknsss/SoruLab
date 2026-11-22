package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsymExamBlueprintTest {

    @Test
    fun osymQuestionTypeClassifier_accuratelyDetectsAllSixQuestionFormats() {
        val onculluType = OsymQuestionType.classifyFromText(
            stemTop = "Gerçek sayılarda tanımlı f fonksiyonu için aşağıdaki öncüller veriliyor:",
            bulletsOrSteps = listOf(
                "I. f fonksiyonu bire birdir.",
                "II. f fonksiyonu örtendir.",
                "III. f(0) = 2'dir."
            ),
            stemBottom = "Buna göre, yukarıdaki ifadelerden hangileri kesinlikle doğrudur?"
        )
        assertEquals(OsymQuestionType.ONCULLU, onculluType)

        val grafikType = OsymQuestionType.classifyFromText(
            stemTop = "Dik koordinat düzleminde y = f(x) fonksiyonunun grafiği ve değişim tablosu verilmiştir.",
            stemBottom = "Grafiğe göre f(2) kaçtır?",
            visualType = QuestionVisualType.FUNCTION_GRAPH
        )
        assertEquals(OsymQuestionType.GRAFIK_TABLO, grafikType)

        val geometrikType = OsymQuestionType.classifyFromText(
            stemTop = "ABC dik üçgeninde hipotenüs uzunluğu 10 cm ve dik kenarlardan biri 6 cm'dir.",
            stemBottom = "Üçgen köşegeni boyunca katlandığında boyalı bölgenin alanı kaç cm² olur?",
            visualType = QuestionVisualType.GEOMETRY_TRIANGLE
        )
        assertEquals(OsymQuestionType.GORSEL_GEOMETRIK, geometrikType)

        val mantikType = OsymQuestionType.classifyFromText(
            stemTop = "Bu parçada yazarın vurguladığı temel çıkarım ve asıl anlatılmak istenen düşünce aşağıdakilerden hangisidir?",
            stemBottom = "Parçaya göre hangisi kesinlikle çıkarılamaz?"
        )
        assertEquals(OsymQuestionType.MANTIK_MUHAKEME, mantikType)

        val yeniNesilType = OsymQuestionType.classifyFromText(
            stemTop = "Bir teknoloji mağazası hafta sonu kampanyasında etiket fiyatı üzerinden %20 indirim uygulayarak günlük satış modellemesi yapıyor.",
            stemBottom = "Mağazanın elde ettiği kâr kaç TL'dir?",
            visualType = QuestionVisualType.MARKET_RECEIPT
        )
        assertEquals(OsymQuestionType.YENI_NESIL_KURGULU, yeniNesilType)

        val cebirselType = OsymQuestionType.classifyFromText(
            stemTop = "x² - 5x + 6 = 0 ikinci dereceden denkleminin kökleri x1 ve x2'dir.",
            stemBottom = "Buna göre x1 + x2 toplamı kaçtır?"
        )
        assertEquals(OsymQuestionType.ANALITIK_CEBIRSEL, cebirselType)
    }

    @Test
    fun osymKazanimTaxonomy_coversGeneralAndCoreTopicCurricula() {
        assertTrue(OsymKazanimTaxonomy.allKazanimlar.size >= 25)

        val coreTopicQueries = listOf(
            "turev_uygulamalari" to "Türevin Geometrik ve Fiziksel Uygulamaları",
            "fonksiyonlar" to "Fonksiyonlarda Uygulamalar",
            "paragrafta_anlam" to "Paragrafta Anlam",
            "sayi_problemleri" to "Sayı Problemleri",
            "ucgende_eslik_benzerlik" to "Üçgende Eşlik ve Benzerlik"
        )

        for ((topicId, topicName) in coreTopicQueries) {
            val kazanimlar = OsymKazanimTaxonomy.getKazanimlarForTopic(topicId, topicName)
            assertTrue("Kazanım listesi $topicId için en az 4 alt kazanım içermeli", kazanimlar.size >= 4)
            assertTrue(kazanimlar.all { it.code.isNotBlank() && it.title.isNotBlank() && it.description.isNotBlank() })
        }

        // Katalogdaki her konu için kazanım çözümleyicinin boş dönmediğini doğrula
        for (subject in SoruLabCatalog.allSubjects) {
            for (topic in subject.topics) {
                val topicKazanimlar = OsymKazanimTaxonomy.getKazanimlarForTopic(topic.id, topic.name)
                assertTrue("${topic.name} konusu için kazanım haritası boş olmamalı", topicKazanimlar.size >= 4)
            }
        }
    }

    @Test
    fun osymDifficultyStandard_allocatesExactTenTwentyFortyTwentyTenQuotas() {
        val quotas10 = OsymDifficultyStandard.targetCountsForTotal(10)
        assertEquals(10, quotas10.values.sum())
        assertEquals(1, quotas10[DifficultyLevel.KOLAY])
        assertEquals(2, quotas10[DifficultyLevel.ORTA])
        assertEquals(4, quotas10[DifficultyLevel.OSYM])
        assertEquals(2, quotas10[DifficultyLevel.ZOR])
        assertEquals(1, quotas10[DifficultyLevel.COK_ZOR])

        val quotas20 = OsymDifficultyStandard.targetCountsForTotal(20)
        assertEquals(20, quotas20.values.sum())
        assertEquals(2, quotas20[DifficultyLevel.KOLAY])
        assertEquals(4, quotas20[DifficultyLevel.ORTA])
        assertEquals(8, quotas20[DifficultyLevel.OSYM])
        assertEquals(4, quotas20[DifficultyLevel.ZOR])
        assertEquals(2, quotas20[DifficultyLevel.COK_ZOR])

        val quotas40 = OsymDifficultyStandard.targetCountsForTotal(40)
        assertEquals(40, quotas40.values.sum())
        assertEquals(4, quotas40[DifficultyLevel.KOLAY])
        assertEquals(8, quotas40[DifficultyLevel.ORTA])
        assertEquals(16, quotas40[DifficultyLevel.OSYM])
        assertEquals(8, quotas40[DifficultyLevel.ZOR])
        assertEquals(4, quotas40[DifficultyLevel.COK_ZOR])

        val quotas12 = OsymDifficultyStandard.targetCountsForTotal(12)
        assertEquals(12, quotas12.values.sum())
        assertEquals(5, quotas12[DifficultyLevel.OSYM])
    }

    @Test
    fun auditEngine_rejectsUnbalancedSingleTypeOrRandomDifficultyTests() {
        val monotonousQuestions = List(10) { idx ->
            YksQuestion(
                id = "bad_q_${idx + 1}",
                number = idx + 1,
                stemTop = "2x + 4 = 10 denkleminde x kaçtır?",
                stemBottom = "x değerini bulunuz.",
                options = mapOf("A" to "1", "B" to "2", "C" to "3", "D" to "4", "E" to "5"),
                correctOption = "C",
                difficulty = DifficultyLevel.KOLAY,
                stepByStepSolution = "2x = 6 => x = 3",
                learningOutcomeId = "KAZ_TUREV_01",
                questionType = OsymQuestionType.ANALITIK_CEBIRSEL.name
            )
        }
        val badTest = YksTestItem(
            id = "bad_test",
            topicId = "turev_alma_kurallari",
            title = "Rastgele Dengesiz Test",
            fullTitle = "Rastgele Dengesiz Test",
            difficulty = DifficultyLevel.KOLAY,
            difficultyPosition = 0.12f,
            questions = monotonousQuestions
        )

        val report = OsymExamAuditEngine.auditTest(badTest)

        assertFalse("Tek zorluk ve tek kazanımlı test ÖSYM uyumlu olmamalı", report.isOsymCompliant)
        assertFalse("Sadece Kolay sorulardan oluşan test zorluk denetiminden geçmemeli", report.isDifficultyCompliant)
        assertFalse("Sadece tek soru tipi içeren test çeşitlilik denetiminden geçmemeli", report.isQuestionTypeDiverse)
        assertFalse("Sadece tek kazanım içeren test kazanım denetiminden geçmemeli", report.isKazanimBalanced)
        assertTrue("İhlal listesi dolu olmalı", report.violations.size >= 3)
    }

    @Test
    fun allCatalogMockExams_passOsymAuditEngineStandards() {
        val mockExams = OsymMockExamFactory.buildAllCuratedMockExams()
        assertTrue("OsymMockExamFactory en az 9 kurumsal genel ve konu denemesi içermeli", mockExams.size >= 9)

        val hasTytGeneral = mockExams.any { it.examType == ExamType.TYT && it.questionCount == 40 }
        val hasAytGeneral = mockExams.any { it.examType == ExamType.AYT && it.questionCount == 40 }
        val hasTurevTopicExam = mockExams.any { it.id.contains("turev") }
        val hasFonksiyonTopicExam = mockExams.any { it.id.contains("fonksiyon") }
        val hasParagrafTopicExam = mockExams.any { it.id.contains("paragraf") }
        val hasProblemlerTopicExam = mockExams.any { it.id.contains("problem") }
        val hasGeometriTopicExam = mockExams.any { it.id.contains("geometri") }

        assertTrue(hasTytGeneral)
        assertTrue(hasAytGeneral)
        assertTrue(hasTurevTopicExam)
        assertTrue(hasFonksiyonTopicExam)
        assertTrue(hasParagrafTopicExam)
        assertTrue(hasProblemlerTopicExam)
        assertTrue(hasGeometriTopicExam)

        for (mockExam in mockExams) {
            val report = OsymExamAuditEngine.auditMockExam(mockExam)
            assertTrue(
                "${mockExam.title} ÖSYM denetiminden geçemedi: ${report.violations}",
                report.isOsymCompliant
            )
            assertTrue(report.isDifficultyCompliant)
            assertTrue(report.isQuestionTypeDiverse)
            assertTrue(report.isKazanimBalanced)
            assertTrue(report.isOptionDistributionBalanced)
            assertEquals(mockExam.questionCount, report.totalQuestions)
            assertEquals(10.0, report.difficultyPercentages.getValue(DifficultyLevel.KOLAY), 0.01)
            assertEquals(20.0, report.difficultyPercentages.getValue(DifficultyLevel.ORTA), 0.01)
            assertEquals(40.0, report.difficultyPercentages.getValue(DifficultyLevel.OSYM), 0.01)
            assertEquals(20.0, report.difficultyPercentages.getValue(DifficultyLevel.ZOR), 0.01)
            assertEquals(10.0, report.difficultyPercentages.getValue(DifficultyLevel.COK_ZOR), 0.01)

            // Her sorunun şıklarını, kazanım kodunu ve 3 aşamalı çözüm yapısını doğrula
            for (question in mockExam.linkedTest.questions) {
                assertEquals(5, question.options.size)
                assertTrue(question.correctOption in listOf("A", "B", "C", "D", "E"))
                assertTrue(question.osymKazanimId.isNotBlank())
                assertTrue(question.osymKazanimCode.isNotBlank())
                assertNotNull(question.resolvedOsymQuestionType)
                assertTrue(question.stepByStepSolution.contains("Kritik Bilgi & Teorem"))
                assertTrue(question.stepByStepSolution.contains("Adım Adım Çözüm"))
                assertTrue(question.stepByStepSolution.contains("ÖSYM Tuzak Notu"))
            }
        }
    }

    @Test
    fun generateKonuDenemesi_producesBalancedAndAuditedExamsForCoreAndGeneralTopics() {
        val targetTopicIds = listOf(
            "turev_uygulamalari",
            "fonksiyonlar",
            "paragrafta_anlam",
            "sayi_problemleri",
            "ucgende_eslik_benzerlik",
            "limit_ve_sureklilik",
            "belirli_integral_alan",
            "kimyasal_denge",
            "sinir_sistemi"
        )

        for (topicId in targetTopicIds) {
            val topic = SoruLabCatalog.topicByIdMap[topicId]
            assertNotNull("Konu katalogda bulunmalı: $topicId", topic)
            val (generatedTest, report) = OsymExamAuditEngine.generateAuditedTopicExam(
                topic = topic!!,
                questionCount = 10,
                level = TopicTestLevel.OSYM,
                testNumber = 1
            )

            assertEquals(10, generatedTest.questions.size)
            assertTrue(
                "${topic.name} konu denemesi ÖSYM denetiminden geçmeli: ${report.violations}",
                report.isOsymCompliant
            )
            assertTrue(report.distinctKazanimCount >= 4)
            assertTrue(report.distinctQuestionTypeCount >= 4)
            assertEquals(1, report.difficultyCounts[DifficultyLevel.KOLAY])
            assertEquals(2, report.difficultyCounts[DifficultyLevel.ORTA])
            assertEquals(4, report.difficultyCounts[DifficultyLevel.OSYM])
            assertEquals(2, report.difficultyCounts[DifficultyLevel.ZOR])
            assertEquals(1, report.difficultyCounts[DifficultyLevel.COK_ZOR])
        }
    }

    @Test
    fun selectBalancedQuestions_enforcesStratifiedKazanimAndTypeDiversityOverCandidatePool() {
        val turevTopic = SoruLabCatalog.topicByIdMap.getValue("turev_uygulamalari")
        val kazanimlar = OsymKazanimTaxonomy.getKazanimlarForTopic(turevTopic.id, turevTopic.name)
        val types = OsymQuestionType.entries
        val diffs = DifficultyLevel.entries

        // 40 soruluk karma aday havuzu oluştur
        val candidatePool = List(40) { idx ->
            val kaz = kazanimlar[idx % kazanimlar.size]
            val qType = types[idx % types.size]
            val diff = diffs[idx % diffs.size]
            YksQuestion(
                id = "pool_q_${idx + 1}",
                number = idx + 1,
                topicHeader = turevTopic.name,
                stemTop = "[${kaz.code}] Türev aday sorusu #${idx + 1} - ${kaz.title}",
                bulletsOrSteps = if (qType == OsymQuestionType.ONCULLU) listOf("I. Öncül", "II. Öncül", "III. Öncül") else emptyList(),
                stemBottom = "Buna göre sonuç kaçtır?",
                options = mapOf("A" to "1", "B" to "2", "C" to "3", "D" to "4", "E" to "5"),
                correctOption = listOf("A", "B", "C", "D", "E")[idx % 5],
                difficulty = diff,
                stepByStepSolution = "Detaylı çözüm #${idx + 1}",
                learningOutcomeId = kaz.id,
                learningOutcome = kaz.code,
                learningOutcomeName = kaz.title,
                questionType = qType.name
            )
        }

        val selected = OsymExamAuditEngine.selectBalancedQuestions(
            candidates = candidatePool,
            targetCount = 10,
            topicId = turevTopic.id,
            topicName = turevTopic.name
        )

        assertEquals(10, selected.size)
        val audited = OsymExamAuditEngine.auditTest(
            YksTestItem(
                id = "stratified_turev_test",
                topicId = turevTopic.id,
                title = "Türev Dengeli Test",
                fullTitle = "Türev Dengeli Test",
                difficulty = DifficultyLevel.OSYM,
                difficultyPosition = 0.54f,
                questions = selected
            ),
            expectedTopicId = turevTopic.id
        )

        assertTrue("Seçilen sorular ÖSYM denetiminden tam puanla geçmeli: ${audited.violations}", audited.isOsymCompliant)
        assertEquals(1, audited.difficultyCounts[DifficultyLevel.KOLAY])
        assertEquals(2, audited.difficultyCounts[DifficultyLevel.ORTA])
        assertEquals(4, audited.difficultyCounts[DifficultyLevel.OSYM])
        assertEquals(2, audited.difficultyCounts[DifficultyLevel.ZOR])
        assertEquals(1, audited.difficultyCounts[DifficultyLevel.COK_ZOR])
        assertTrue(audited.distinctKazanimCount >= 5)
        assertTrue(audited.distinctQuestionTypeCount >= 4)
    }
}
