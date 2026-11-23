package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicVideoTimestampGrounderTest {

    @Test
    fun parseTimedTranscript_parsesYoutubeFormat3XmlAndClassicXmlAndJson3() {
        val format3Xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <timedtext format="3">
              <body>
                <p t="26194000" d="4000"><s>Karışımlara başlıyoruz</s><s> homojen karışımlar</s></p>
                <p t="26862000" d="4000">der ki bize heterojen karışımları beş bölgede inceledi</p>
              </body>
            </timedtext>
        """.trimIndent()

        val parsedXml3 = DynamicVideoTimestampGrounder.parseTimedTranscript(format3Xml)
        assertEquals(2, parsedXml3.size)
        assertEquals(26194, parsedXml3[0].startSeconds)
        assertTrue(parsedXml3[0].text.contains("Karışımlara"))
        assertEquals(26862, parsedXml3[1].startSeconds)
        assertTrue(parsedXml3[1].text.contains("heterojen"))

        val classicXml = """
            <transcript>
              <text start="120.4" dur="3.2">Birinci bölgede sıralama &amp; karşılaştırma</text>
            </transcript>
        """.trimIndent()
        val parsedClassic = DynamicVideoTimestampGrounder.parseTimedTranscript(classicXml)
        assertEquals(1, parsedClassic.size)
        assertEquals(120, parsedClassic[0].startSeconds)
        assertEquals("Birinci bölgede sıralama & karşılaştırma", parsedClassic[0].text)

        val json3 = """
            {"events":[{"tStartMs":555000,"segs":[{"utf8":"Trigonometrik sıralama "},{"utf8":"sorularında"}]}]}
        """.trimIndent()
        val parsedJson = DynamicVideoTimestampGrounder.parseTimedTranscript(json3)
        assertEquals(1, parsedJson.size)
        assertEquals(555, parsedJson[0].startSeconds)
        assertEquals("Trigonometrik sıralama sorularında", parsedJson[0].text)
    }

    @Test
    fun findBestTimestampInTranscript_skipsHomojenAndPinpointsHeterojenSuspansiyonStart() {
        val question = YksQuestion(
            id = "q_heterojen_kati_sivi",
            number = 1,
            subject = "Kimya",
            topicHeader = "Karışımlar",
            stemTop = "Heterojen katı-sıvı karışımlara,",
            bulletsOrSteps = listOf("I. Kan", "II. Çelik", "III. Sis"),
            stemBottom = "yukarıdaki maddelerden hangileri örnek verilebilir?",
            options = mapOf("A" to "I, II ve III", "B" to "I ve II", "C" to "II ve III", "D" to "I ve III", "E" to "Yalnız I"),
            correctOption = "E",
            difficulty = DifficultyLevel.ORTA,
            stepByStepSolution = "Kan katı-sıvı heterojen (kolloidal süspansiyon), çelik homojen alaşım, sis sıvı-gaz aerosoldür.",
            questionType = OsymQuestionType.ONCULLU.name
        )

        val transcript = listOf(
            TranscriptSegment(26194, "Evet arkadaşlar 4. gün karışımlar konusuna başlıyoruz."),
            TranscriptSegment(26220, "Önce homojen karışımları yani çözeltileri inceleyelim."),
            TranscriptSegment(26310, "Tuzlu su, şekerli su ve alaşımlar yani çelik homojen karışımdır."),
            TranscriptSegment(26500, "Çözünen ve çözücü arasındaki etkileşimlere bakalım."),
            TranscriptSegment(26811, "Heterojen, karışık bir bölgeden oluşur."),
            TranscriptSegment(26862, "der ki bize heterojen karışımları beş bölgede inceledi."),
            TranscriptSegment(26865, "Bir süspansiyon."),
            TranscriptSegment(26878, "Katı sıvı birbirinin içinde çözüldü mü? Hayır."),
            TranscriptSegment(26885, "Katı sıvı birbiri içinde çözülmeyen karışımlara süspansiyon adını verdim."),
            TranscriptSegment(26907, "Peki hocam ayran kan örnek verilebilir mi?"),
            TranscriptSegment(26951, "Bak süspansiyon katı sıvı. Emülsiyon sıvı sıvı heterojen."),
            TranscriptSegment(26976, "Bulut, sis, duman bunların hepsi aerosol."),
            TranscriptSegment(27075, "Kan süspansiyon mu kolloit mi? Kan kolloidal süspansiyondur.")
        )

        val grounded = DynamicVideoTimestampGrounder.findBestTimestampInTranscript(
            question = question,
            segments = transcript,
            windowStartSeconds = 26194,
            windowEndSeconds = 30207,
            fallbackChapterTitle = "Karışımlar"
        )

        assertNotNull("Transkript içinde heterojen karışım saniyesi bulunmalıydı", grounded)
        assertEquals(GroundingSource.TRANSCRIPT_LOCAL, grounded!!.source)
        // Homojen başlangıcı (26194s) yerine doğrudan heterojen/süspansiyon anlatımının başladığı ~26859-26885s aralığına atlamalı
        assertTrue(
            "Beklenen aralık 26850..26910 sn; gelen: ${grounded.startSeconds} (${grounded.formattedTimestamp})",
            grounded.startSeconds in 26850..26910
        )
    }

    @Test
    fun findBestTimestampInTranscript_staysInsideChapterWindowOnLongMultiUnitVideos() {
        val question = YksQuestion(
            id = "q_fotoelektrik_esik",
            number = 2,
            subject = "Fizik",
            topicHeader = "Fotoelektrik Olay",
            stemTop = "Bir metale düşürülen fotonların oluşturduğu fotoelektrik olayda eşik enerjisi ve kesme potansiyeli nasıl değişir?",
            stemBottom = "",
            options = mapOf("A" to "Artar", "B" to "Azalır", "C" to "Değişmez", "D" to "Önce artar", "E" to "Sıfır olur"),
            correctOption = "A",
            difficulty = DifficultyLevel.ORTA,
            stepByStepSolution = "Eşik enerjisi metalin cinsine bağlıdır.",
            questionType = OsymQuestionType.ONCULLU.name
        )

        val transcript = listOf(
            // Başka ünitede geçen alakasız kelime
            TranscriptSegment(120, "İleride fotoelektrik olayda da enerji korunumu göreceğiz."),
            // Gerçek Fotoelektrik ünitesi penceresi (1200..2400)
            TranscriptSegment(1200, "Modern fizik konusuna giriş yapıyoruz."),
            TranscriptSegment(1450, "Gelelim fotoelektrik olayda eşik enerjisi ve bağlanma enerjisi kavramlarına."),
            TranscriptSegment(1460, "Kesme potansiyeli fotonun enerjisi ile eşik enerjisi farkına bağlıdır.")
        )

        val grounded = DynamicVideoTimestampGrounder.findBestTimestampInTranscript(
            question = question,
            segments = transcript,
            windowStartSeconds = 1200,
            windowEndSeconds = 2400,
            fallbackChapterTitle = "Fotoelektrik Olay"
        )

        assertNotNull(grounded)
        assertTrue(
            "Bölüm penceresi içinde 1445..1465 sn aralığı beklenirdi; gelen: ${grounded!!.startSeconds}",
            grounded.startSeconds in 1445..1465
        )
    }
}
