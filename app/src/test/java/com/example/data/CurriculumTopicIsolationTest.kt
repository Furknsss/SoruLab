package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumTopicIsolationTest {
    private fun subject(id: String): YksSubject =
        SoruLabCatalog.allSubjects.single { it.id == id }

    @Test
    fun allSubjectsHaveOnlyOwnedUniqueTopicsInLearningOrder() {
        val allIds = mutableSetOf<String>()
        SoruLabCatalog.allSubjects.forEach { subject ->
            assertTrue("Empty topic list for ${subject.id}", subject.topics.isNotEmpty())
            assertTrue(subject.topics.all { it.subjectId == subject.id })
            subject.topics.forEach { topic ->
                assertTrue("Duplicate topic ID ${topic.id}", allIds.add(topic.id))
            }
        }
        val tytMath = subject("tyt_mat").topics.map { it.name }
        assertEquals("Temel Kavramlar", tytMath.first())
        assertTrue(tytMath.indexOf("Sayı Basamakları") < tytMath.indexOf("Bölme ve Bölünebilme"))
        assertTrue(tytMath.indexOf("Bölme ve Bölünebilme") < tytMath.indexOf("EBOB - EKOK"))
        assertTrue(tytMath.indexOf("EBOB - EKOK") < tytMath.indexOf("Rasyonel Sayılar"))
    }

    @Test
    fun aytMathNeverDisplaysTYTTemelKavramlarCard() {
        val math = subject("ayt_mat")
        assertEquals("Fonksiyonlarda Uygulamalar", math.topics.first().name)
        assertFalse(math.topics.any { it.name == "Temel Kavramlar" })
        assertEquals("temel_kavramlar",
            SoruLabCatalog.resolveTopicForRecord("ayt_mat", "", "Temel Kavramlar")?.id)
        assertEquals("tyt_mat",
            SoruLabCatalog.resolveTopicForRecord("ayt_mat", "", "Temel Kavramlar")?.subjectId)
    }

    @Test
    fun aytGeometryStartsWithCircleAndDoesNotShowTYTGeometryTopics() {
        val ayt = subject("ayt_geo")
        assertEquals("Çemberde Açı", ayt.topics.first().name)
        assertFalse(ayt.topics.any { it.name == "Üçgende Açılar" })
        assertFalse(ayt.topics.any { it.name == "Prizmalar" })
        val wronglyTagged = SoruLabCatalog.resolveTopicForRecord(
            "ayt_geo", "kati_cisimler_ayt", "Üçgende Açılar"
        )
        assertEquals("ucgende_acilar", wronglyTagged?.id)
        assertEquals("tyt_geo", wronglyTagged?.subjectId)
        assertEquals("prizmalar_tyt",
            SoruLabCatalog.resolveTopicForRecord("ayt_geo", "", "Prizmalar")?.id)
    }

    @Test
    fun topicNameResolutionRequiresCorrectExamAndSubject() {
        assertNull(SoruLabCatalog.matchBestTopic("AYT", "Matematik", "Temel Kavramlar"))
        assertNull(SoruLabCatalog.matchBestTopic("AYT", "Geometri", "Üçgende Açılar"))
        assertEquals("ucgende_acilar",
            SoruLabCatalog.matchBestTopic("TYT", "Geometri", "Üçgende Açılar")?.second?.id)
        // A geometric degree measurement must not force "AYT Polinomlar".
        val triangle = SoruLabCatalog.classifyQuestionTopicFromText(
            examTypeHint = "TYT",
            subjectNameHint = "Geometri",
            questionText = "Üçgende açılar toplamı 180 derece. Dış açı kaçtır?",
            suggestedTopicName = "Üçgende Açılar",
            trustSuggestedTopic = true
        )
        assertEquals("tyt_geo", triangle?.first?.id)
        assertEquals("ucgende_acilar", triangle?.second?.id)
    }

    @Test
    fun ambiguousTopicNamesRequireSpecificSubject() {
        // "Polinomlar" is listed for both TYT and AYT.
        assertNull(SoruLabCatalog.resolveTopicFast("", "Polinomlar"))
        assertEquals("tyt_polinomlar",
            SoruLabCatalog.resolveTopicInSubject("tyt_mat", "", "Polinomlar")?.id)
        assertEquals("polinomlar",
            SoruLabCatalog.resolveTopicInSubject("ayt_mat", "", "Polinomlar")?.id)
    }

    @Test
    fun legacyTopicAliasesMustRemainInSameDiscipline() {
        assertEquals("dairede_alan",
            SoruLabCatalog.resolveTopicForRecord("ayt_geo", "", "Dairede Alan")?.id)
        assertEquals("noktanin_analitigi",
            SoruLabCatalog.resolveTopicForRecord("ayt_geo", "", "Nokta Analitiği")?.id)
        assertNull(SoruLabCatalog.resolveTopicForRecord("ayt_fizik", "", "Üçgende Açılar"))
    }
}
