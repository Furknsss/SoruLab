package com.example.viewmodel

import com.example.data.DifficultyLevel
import com.example.data.QuestionVisualType
import com.example.data.YksQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class InteractiveSolutionTest {

    private fun sampleQuestion(
        id: String = "q_turev_42",
        subject: String = "Matematik",
        topicHeader: String = "Türevin Uygulamaları",
        correctOption: String = "C",
        stepByStepSolution: String = ""
    ): YksQuestion = YksQuestion(
        id = id,
        number = 12,
        topicHeader = topicHeader,
        stemTop = "f(x) = x^3 - 3x^2 + 2 fonksiyonunun yerel minimum noktasının apsisi kaçtır?",
        bulletsOrSteps = emptyList(),
        stemBottom = "Buna göre yerel minimum noktasının apsisi aşağıdakilerden hangisidir?",
        options = mapOf(
            "A" to "-1",
            "B" to "0",
            "C" to "2",
            "D" to "3",
            "E" to "4"
        ),
        correctOption = correctOption,
        difficulty = DifficultyLevel.OSYM,
        visualType = QuestionVisualType.NONE,
        stepByStepSolution = stepByStepSolution,
        subject = subject
    )

    @Test
    fun parseInteractiveSolutionJson_parsesMarkdownFencedJsonWithAllFields() {
        val question = sampleQuestion()
        val rawJson = """
            ```json
            {
              "coreConcept": "Birinci türevin işaret tablosu ile yerel ekstremum noktalarının bulunması",
              "firstGlanceStrategy": "İlk 5 saniyede polinomun türevini alıp sıfıra eşitle, işaret değişimini kontrol et.",
              "steps": [
                {
                  "stepNumber": 2,
                  "title": "Kritik Noktaları Bulma",
                  "explanation": "Türevi sıfıra eşitleyip çarpanlarına ayıralım.",
                  "mathExpression": "${'$'}3x(x - 2) = 0 \Rightarrow x = 0, x = 2${'$'}",
                  "whyThisStep": "Yerel ekstremum noktalarında türev sıfır olur.",
                  "trapWarning": "x = 0 noktasını hemen minimum sanma, işaret tablosuna bak!"
                },
                {
                  "stepNumber": 1,
                  "title": "Birinci Türevi Alma",
                  "explanation": "Polinomun türevini kuvvet kuralıyla alalım.",
                  "mathExpression": "f'(x) = 3x^2 - 6x",
                  "whyThisStep": "Fonksiyonun artan ve azalan olduğu aralıkları görmek için türev gerekir.",
                  "trapWarning": "Sabit terim 2'nin türevi 0'dır."
                },
                {
                  "stepNumber": 3,
                  "title": "İşaret Tablosu ile Minimumu Seçme",
                  "explanation": "x = 2 noktasında türev eksiden artıya geçtiği için yerel minimumdur.",
                  "mathExpression": "f''(2) = 6(2) - 6 = 6 > 0",
                  "whyThisStep": "Azalanlıktan artanlığa geçiş yerel minimum verir.",
                  "trapWarning": "Soru apsis soruyor, ordinat f(2) = -2 ile karıştırma."
                }
              ],
              "wrongOptionsAnalysis": [
                {"option": "A", "reason": "x = -1 kritik nokta değildir, f'(-1) = 9 > 0."},
                {"option": "B", "reason": "x = 0 yerel maksimum noktasıdır, minimum değildir."},
                {"option": "D", "reason": "x = 3 noktasında fonksiyon artmaya devam eder."},
                {"option": "E", "reason": "Türev kökleriyle ilgisiz çeldirici değerdir."}
              ],
              "osymShortcut": "Baş katsayısı pozitif kübik polinomda küçük kök yerel maksimum, büyük kök yerel minimumdur: 0 < 2 olduğundan cevap direkt 2 (C)!",
              "finalAnswer": "C şıkkı • x = 2",
              "verificationCheck": "f'(2) = 12 - 12 = 0 ve f''(2) = 6 > 0 olduğundan x = 2 kesinlikle yerel minimumdur."
            }
            ```
        """.trimIndent()

        val plan = SorulabAiQuality.parseInteractiveSolutionJson(rawJson, question)
        assertNotNull("Geçerli JSON başarıyla ayrıştırılmalı", plan)
        plan!!

        assertFalse("JSON'dan gelen plan fallback olarak işaretlenmemeli", plan.isFromFallback)
        assertEquals(question.id, plan.questionId)
        assertEquals("Birinci türevin işaret tablosu ile yerel ekstremum noktalarının bulunması", plan.coreConcept)
        assertTrue(plan.firstGlanceStrategy.contains("5 saniyede"))

        // Adımlar stepNumber'a göre sıralanmış olmalı
        assertEquals(3, plan.steps.size)
        assertEquals(listOf(1, 2, 3), plan.steps.map { it.stepNumber })
        assertEquals("Birinci Türevi Alma", plan.steps[0].title)
        assertFalse(
            "mathExpression içindeki $ işaretleri temizlenmeli",
            plan.steps[1].mathExpression.contains("$")
        )
        assertTrue(plan.steps[1].mathExpression.contains("3x(x - 2) = 0"))
        assertTrue(plan.steps[1].trapWarning.contains("işaret tablosuna bak"))

        // wrongOptionsAnalysis ve osymShortcut kontrolleri
        assertEquals(4, plan.wrongOptionsAnalysis.size)
        assertFalse(plan.wrongOptionsAnalysis.containsKey("C"))
        assertTrue(plan.wrongOptionsAnalysis["B"]!!.contains("yerel maksimum"))
        assertTrue(plan.osymShortcut.contains("büyük kök yerel minimum"))
        assertEquals("C şıkkı • x = 2", plan.finalAnswer)
        assertTrue(plan.verificationCheck.contains("f''(2)"))
    }

    @Test
    fun parseInteractiveSolutionJson_supportsObjectWrongOptionsAndRejectsMalformedJson() {
        val question = sampleQuestion(correctOption = "B")
        val objectWrongOptionsJson = """
            {
              "coreConcept": "Limit belirsizliği",
              "firstGlanceStrategy": "Pay ve paydayı çarpanlarına ayır.",
              "steps": [
                {
                  "stepNumber": 1,
                  "title": "Çarpanlara Ayırma",
                  "explanation": "x - 2 çarpanlarını sadeleştir.",
                  "mathExpression": "\\frac{x^2 - 4}{x - 2} = x + 2"
                }
              ],
              "wrongOptionsAnalysis": {
                "A": "Sadeleştirme yapılmadan 0/0 bırakılmış.",
                "C": "İşaret hatası ile x - 2 alınmış."
              },
              "osymShortcut": "L'Hospital ile 2x/1 = 4 tek hamlede gelir.",
              "finalAnswer": "B şıkkı"
            }
        """.trimIndent()

        val plan = SorulabAiQuality.parseInteractiveSolutionJson(objectWrongOptionsJson, question)
        assertNotNull(plan)
        assertEquals(2, plan!!.wrongOptionsAnalysis.size)
        assertEquals("Sadeleştirme yapılmadan 0/0 bırakılmış.", plan.wrongOptionsAnalysis["A"])
        assertEquals("İşaret hatası ile x - 2 alınmış.", plan.wrongOptionsAnalysis["C"])
        assertTrue(plan.osymShortcut.contains("L'Hospital"))

        // Hatalı veya boş JSON null dönmeli
        assertNull(SorulabAiQuality.parseInteractiveSolutionJson("", question))
        assertNull(SorulabAiQuality.parseInteractiveSolutionJson("{not valid json", question))
        assertNull(SorulabAiQuality.parseInteractiveSolutionJson("""{"coreConcept":"Test","steps":[]}""", question))
    }

    @Test
    fun parseFallbackPlanFromQuestion_extractsNumberedStepsCoreConceptAndTrapNote() {
        val richSolution = """
            📌 Kritik Bilgi: Türevin sıfır olduğu ve işaret değiştirdiği noktalar ekstremumdur.
            ✏️ Adım Adım Çözüm:
            1. Adım: f'(x) = 3x^2 - 6x türevini alalım.
            2. Adım: 3x(x - 2) = 0 denkleminden x = 0 ve x = 2 köklerini bulalım.
            3. Adım: İşaret tablosunda x = 2 noktasında eksiden artıya geçildiği için cevap C şıkkıdır.
            ⚠️ ÖSYM Tuzak: Yerel maksimum noktası olan x = 0 ile minimumu karıştırma!
        """.trimIndent()

        val question = sampleQuestion(stepByStepSolution = richSolution)
        val plan = SorulabAiQuality.parseFallbackPlanFromQuestion(question)

        assertTrue("Fallback plan isFromFallback=true olmalı", plan.isFromFallback)
        assertEquals(question.id, plan.questionId)
        assertTrue(
            "Kritik Bilgi coreConcept olarak ayrıştırılmalı",
            plan.coreConcept.contains("ekstremumdur")
        )
        assertEquals("3 numaralı adım ayrıştırılmalı", 3, plan.steps.size)
        assertEquals(listOf(1, 2, 3), plan.steps.map { it.stepNumber })
        assertTrue(plan.steps[0].mathExpression.contains("f'(x) = 3x^2 - 6x"))
        assertTrue(
            "Son adımda ÖSYM tuzak notu yer almalı",
            plan.steps.last().trapWarning.contains("karıştırma")
        )

        // wrongOptionsAnalysis doğru şık dışındaki tüm şıkları kapsamalı
        assertEquals(setOf("A", "B", "D", "E"), plan.wrongOptionsAnalysis.keys)
        assertTrue(plan.wrongOptionsAnalysis["A"]!!.contains("-1"))
        assertTrue(plan.osymShortcut.isNotBlank())
        assertTrue(plan.finalAnswer.contains("C"))
    }

    @Test
    fun parseFallbackPlanFromQuestion_neverReturnsEmptyWhenSolutionIsBlank() {
        val emptyQuestion = sampleQuestion(
            id = "q_empty_sol",
            topicHeader = "Limit ve Süreklilik",
            correctOption = "D",
            stepByStepSolution = ""
        )

        val plan = SorulabAiQuality.parseFallbackPlanFromQuestion(emptyQuestion)

        assertTrue(plan.isFromFallback)
        assertEquals(3, plan.steps.size)
        assertTrue(plan.steps.all { it.title.isNotBlank() && it.explanation.isNotBlank() })
        assertEquals(setOf("A", "B", "C", "E"), plan.wrongOptionsAnalysis.keys)
        assertTrue(plan.osymShortcut.contains("D"))
        assertTrue(plan.coreConcept.contains("Limit ve Süreklilik"))
        assertTrue(plan.finalAnswer.contains("D"))
    }

    @Test
    fun latexToSpokenTurkish_convertsMathExpressionsToNaturalTurkishSpeech() {
        val spokenFrac = SorulabAiQuality.latexToSpokenTurkish("""\frac{x^2 - 4}{x - 2} = x + 2""")
        assertTrue("Kesir 'bölü' olarak okunmalı: $spokenFrac", spokenFrac.contains("bölü"))
        assertTrue("Üs '^2' 'kare' olarak okunmalı: $spokenFrac", spokenFrac.contains("kare"))
        assertTrue("'=' 'eşittir' olarak okunmalı: $spokenFrac", spokenFrac.contains("eşittir"))

        val spokenRoot = SorulabAiQuality.latexToSpokenTurkish("""\sqrt{16} + 3^3""")
        assertTrue("Karekök okunmalı: $spokenRoot", spokenRoot.contains("kök içinde 16"))
        assertTrue("Küp okunmalı: $spokenRoot", spokenRoot.contains("küp"))
    }
}
