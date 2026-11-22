package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicLectureVideoCatalogTest {

    private fun buildQuestion(
        id: String,
        subject: String,
        topicHeader: String,
        stemTop: String,
        stemBottom: String = "Buna göre aşağıdakilerden hangisi doğrudur?",
        bulletsOrSteps: List<String> = emptyList(),
        visualType: QuestionVisualType = QuestionVisualType.NONE,
        kazanimId: String = "",
        kazanimCode: String = "",
        kazanimTitle: String = "",
        questionType: OsymQuestionType? = null
    ): YksQuestion {
        return YksQuestion(
            id = id,
            number = 1,
            topicHeader = topicHeader,
            stemTop = stemTop,
            bulletsOrSteps = bulletsOrSteps,
            stemBottom = stemBottom,
            options = mapOf(
                "A" to "Seçenek A",
                "B" to "Seçenek B",
                "C" to "Seçenek C",
                "D" to "Seçenek D",
                "E" to "Seçenek E"
            ),
            correctOption = "C",
            difficulty = DifficultyLevel.OSYM,
            visualType = visualType,
            stepByStepSolution = "Örnek çözüm adımı.",
            subject = subject,
            learningOutcomeId = kazanimId,
            learningOutcome = kazanimCode,
            learningOutcomeName = kazanimTitle,
            questionType = questionType?.name.orEmpty()
        )
    }

    private fun assertValidYoutubeUrls(
        watchUrl: String,
        embedUrl: String,
        hasDirectEmbed: Boolean,
        expectedStartSeconds: Int
    ) {
        assertTrue("Watch URL boş olmamalı", watchUrl.isNotBlank())
        assertTrue("Embed URL boş olmamalı", embedUrl.isNotBlank())
        assertFalse("Watch URL boşluk içermemeli: $watchUrl", watchUrl.contains(" "))
        assertFalse("Embed URL boşluk içermemeli: $embedUrl", embedUrl.contains(" "))

        if (hasDirectEmbed) {
            val watchRegex = Regex("^https://www\\.youtube\\.com/watch\\?v=[A-Za-z0-9_-]{11}&t=\\d+s$")
            val embedRegex = Regex("^https://www\\.youtube\\.com/embed/[A-Za-z0-9_-]{11}\\?start=\\d+&autoplay=1&rel=0$")
            assertTrue("Geçersiz watch URL formatı: $watchUrl", watchRegex.matches(watchUrl))
            assertTrue("Geçersiz embed URL formatı: $embedUrl", embedRegex.matches(embedUrl))
            assertTrue("Watch URL beklenen saniyeyi (&t=${expectedStartSeconds}s) içermeli: $watchUrl", watchUrl.endsWith("&t=${expectedStartSeconds}s"))
            assertTrue("Embed URL beklenen saniyeyi (?start=$expectedStartSeconds) içermeli: $embedUrl", embedUrl.contains("?start=$expectedStartSeconds&"))
        } else {
            val searchWatchRegex = Regex("^https://www\\.youtube\\.com/results\\?search_query=\\S+$")
            val searchEmbedRegex = Regex("^https://m\\.youtube\\.com/results\\?search_query=\\S+$")
            assertTrue("Geçersiz fallback watch URL formatı: $watchUrl", searchWatchRegex.matches(watchUrl))
            assertTrue("Geçersiz fallback embed URL formatı: $embedUrl", searchEmbedRegex.matches(embedUrl))
        }
    }

    @Test
    fun helperFunctions_validateYoutubeIdsAndFormatTimestampsCorrectly() {
        assertTrue(isValidYoutubeVideoId("Lt_Ls3EgbLw"))
        assertTrue(isValidYoutubeVideoId("m-ZpmEGABho"))
        assertTrue(isValidYoutubeVideoId("  IQo3-4IVuOE  "))
        assertFalse(isValidYoutubeVideoId(""))
        assertFalse(isValidYoutubeVideoId("short123"))
        assertFalse(isValidYoutubeVideoId("too_long_video_id_12345"))
        assertFalse(isValidYoutubeVideoId("bad!char@12"))

        assertEquals("00:00", formatLectureTimestamp(0))
        assertEquals("00:00", formatLectureTimestamp(-15))
        assertEquals("03:05", formatLectureTimestamp(185))
        assertEquals("22:20", formatLectureTimestamp(1340))
        assertEquals("1:05:20", formatLectureTimestamp(3920))
    }

    @Test
    fun popularTeachers_areResolvedAccuratelyAcrossAllYksSubjects() {
        // 1. AYT Matematik — Türev -> Eyüp B.
        val turevRec = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_turev",
                subject = "Matematik",
                topicHeader = "Türevin Uygulamaları",
                stemTop = "y = f(x) eğrisine x = 2 apsisli noktasından çizilen teğet doğrusunun eğimi kaçtır?",
                kazanimId = "KAZ_TUREV_03",
                kazanimCode = "MAT.12.5.3.1",
                questionType = OsymQuestionType.GRAFIK_TABLO
            ),
            examType = ExamType.AYT,
            subjectName = "Matematik",
            topicName = "Türevin Uygulamaları"
        )
        assertEquals("Eyüp B.", turevRec.primaryVideo.teacherName)
        assertTrue(turevRec.hasDirectEmbedVideo)

        // 2. AYT Matematik — Fonksiyon -> Eyüp B.
        val fonkRec = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_fonk",
                subject = "Matematik",
                topicHeader = "Fonksiyonlar",
                stemTop = "Gerçel sayılarda tanımlı bire bir ve örten f ve g fonksiyonları için (f∘g)⁻¹(3) değeri kaçtır?",
                kazanimId = "KAZ_FONK_03",
                kazanimCode = "MAT.10.2.1.3",
                questionType = OsymQuestionType.ANALITIK_CEBIRSEL
            ),
            examType = ExamType.AYT,
            subjectName = "Matematik",
            topicName = "Fonksiyonlar"
        )
        assertEquals("Eyüp B.", fonkRec.primaryVideo.teacherName)
        assertTrue(fonkRec.hasDirectEmbedVideo)

        // 3. AYT Matematik — Limit -> Eyüp B.
        val limitRec = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_limit",
                subject = "Matematik",
                topicHeader = "Limit ve Süreklilik",
                stemTop = "x → 2 için 0/0 belirsizliği içeren rasyonel fonksiyonun limiti ve sağdan-soldan sürekliliği inceleniyor.",
                kazanimCode = "MAT.12.5.1.1",
                questionType = OsymQuestionType.ANALITIK_CEBIRSEL
            ),
            examType = ExamType.AYT,
            subjectName = "Matematik",
            topicName = "Limit ve Süreklilik"
        )
        assertTrue(
            "AYT Limit için Eyüp B. veya Mert Hoca beklenirdi; gelen: ${limitRec.primaryVideo.teacherName}",
            limitRec.primaryVideo.teacherName in setOf("Eyüp B.", "Mert Hoca")
        )
        assertTrue(limitRec.hasDirectEmbedVideo)

        // 4. AYT Matematik — İntegral -> Eyüp B.
        val integralRec = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_integral",
                subject = "Matematik",
                topicHeader = "İntegral",
                stemTop = "Belirli integral ve değişken değiştirme yöntemi kullanılarak y = x² eğrisi ile x ekseni arasındaki alan hesaplanıyor.",
                questionType = OsymQuestionType.GRAFIK_TABLO
            ),
            examType = ExamType.AYT,
            subjectName = "Matematik",
            topicName = "İntegral"
        )
        assertEquals("Eyüp B.", integralRec.primaryVideo.teacherName)
        assertTrue(integralRec.hasDirectEmbedVideo)

        // 5. AYT Matematik — Trigonometri -> Eyüp B.
        val trigoRec = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_trigo",
                subject = "Matematik",
                topicHeader = "Trigonometri",
                stemTop = "Birim çember üzerinde sin(2x) yarım açı ve toplam-fark formülleriyle trigonometrik denklem çözülüyor.",
                questionType = OsymQuestionType.ANALITIK_CEBIRSEL
            ),
            examType = ExamType.AYT,
            subjectName = "Matematik",
            topicName = "Trigonometri"
        )
        assertTrue(
            "AYT Trigonometri için Eyüp B. veya Mert Hoca beklenirdi; gelen: ${trigoRec.primaryVideo.teacherName}",
            trigoRec.primaryVideo.teacherName in setOf("Eyüp B.", "Mert Hoca")
        )
        assertTrue(trigoRec.hasDirectEmbedVideo)

        // 5b. AYT Matematik — Trigonometrik Sıralama Sorusu -> Eyüp B. 09:15 (555s)
        val trigoSiralamaRec = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_trigo_siralama",
                subject = "Matematik",
                topicHeader = "Trigonometri",
                stemTop = "x in (pi/4, pi/2) olmak üzere a = sinx, b = cosx, c = tanx değerlerinin küçükten büyüğe doğru sıralanışı aşağıdakilerden hangisidir?",
                questionType = OsymQuestionType.MANTIK_MUHAKEME
            ),
            examType = ExamType.AYT,
            subjectName = "Matematik",
            topicName = "Trigonometri"
        )
        assertEquals("Eyüp B.", trigoSiralamaRec.primaryVideo.teacherName)
        assertEquals("eyupb_trigo_siralama", trigoSiralamaRec.primaryChapter.id)
        assertEquals(555, trigoSiralamaRec.primaryChapter.startSeconds)
        assertEquals("09:15", trigoSiralamaRec.formattedTimestamp)

        // 6. TYT Matematik — Problemler -> Mert Hoca veya Rehber Matematik
        val problemRec = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_prob",
                subject = "Matematik",
                topicHeader = "Sayı ve Kesir Problemleri",
                stemTop = "Bir mağazada iki farklı ürünün satış adedi, yaş farkı ve hareket hızı üzerinden kurgulanan yeni nesil sayı problemi.",
                questionType = OsymQuestionType.YENI_NESIL_KURGULU
            ),
            examType = ExamType.TYT,
            subjectName = "Matematik",
            topicName = "Problemler"
        )
        assertTrue(
            "TYT Problemler için Mert Hoca, Rehber Matematik veya Eyüp B. beklenirdi; gelen: ${problemRec.primaryVideo.teacherName}",
            problemRec.primaryVideo.teacherName in setOf("Mert Hoca", "Rehber Matematik", "Eyüp B.")
        )

        // 7. TYT Matematik — Temel Kavramlar -> Mert Hoca, Eyüp B., Rehber Matematik veya Bıyıklı Matematik
        val temelRec = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_temel",
                subject = "Matematik",
                topicHeader = "Temel Kavramlar",
                stemTop = "Ardışık tek tam sayılar, asal çarpanlar ve bölünebilme kurallarına göre en küçük değer kaçtır?",
                questionType = OsymQuestionType.ANALITIK_CEBIRSEL
            ),
            examType = ExamType.TYT,
            subjectName = "Matematik",
            topicName = "Temel Kavramlar"
        )
        assertTrue(
            "TYT Temel Kavramlar için popüler TYT Matematik hocası beklenirdi; gelen: ${temelRec.primaryVideo.teacherName}",
            temelRec.primaryVideo.teacherName in setOf("Mert Hoca", "Eyüp B.", "Rehber Matematik", "Bıyıklı Matematik")
        )

        // 8. Geometri — Üçgenler -> Kenan Kara
        val geoRec = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_geo",
                subject = "Geometri",
                topicHeader = "Üçgende Eşlik ve Benzerlik",
                stemTop = "ABC dik üçgeninde DE // BC olmak üzere açı-açı benzerliği ve katlama sonucu oluşan üçgenin alanı kaç cm²'dir?",
                visualType = QuestionVisualType.GEOMETRY_TRIANGLE,
                questionType = OsymQuestionType.GORSEL_GEOMETRIK
            ),
            examType = ExamType.TYT,
            subjectName = "Geometri",
            topicName = "Üçgende Eşlik ve Benzerlik"
        )
        assertTrue(
            "Geometri için Kenan Kara veya Eyüp B. beklenirdi; gelen: ${geoRec.primaryVideo.teacherName}",
            geoRec.primaryVideo.teacherName in setOf("Kenan Kara", "Eyüp B.")
        )
        assertTrue(geoRec.hasDirectEmbedVideo)

        // 9. TYT Türkçe — Paragraf -> Rüştü Hoca / Kadir Gümüş / Deniz Hoca
        val turkceRec = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_paragraf",
                subject = "Türkçe",
                topicHeader = "Paragrafta Anlam",
                stemTop = "Bu parçada asıl anlatılmak istenen ana düşünce aşağıdakilerden hangisidir?",
                questionType = OsymQuestionType.MANTIK_MUHAKEME
            ),
            examType = ExamType.TYT,
            subjectName = "Türkçe",
            topicName = "Paragrafta Anlam"
        )
        assertTrue(
            "TYT Türkçe Paragraf için popüler Türkçe hocası beklenirdi; gelen: ${turkceRec.primaryVideo.teacherName}",
            turkceRec.primaryVideo.teacherName in setOf("Rüştü Hoca", "Kadir Gümüş", "Deniz Hoca")
        )
        assertTrue(turkceRec.hasDirectEmbedVideo)

        // 10. Fizik -> Özcan Aykın / Altuğ Güneş / VIP Fizik
        val fizikRec = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_fizik",
                subject = "Fizik",
                topicHeader = "Kuvvet ve Hareket",
                stemTop = "Sürtünmesiz yatay düzlemde F net kuvveti etkisindeki cismin ivme-zaman ve hız-zaman grafiği verilmiştir.",
                questionType = OsymQuestionType.GRAFIK_TABLO
            ),
            examType = ExamType.TYT,
            subjectName = "Fizik",
            topicName = "Kuvvet ve Hareket"
        )
        assertTrue(
            "TYT Fizik için popüler Fizik hocası beklenirdi; gelen: ${fizikRec.primaryVideo.teacherName}",
            fizikRec.primaryVideo.teacherName in setOf("Özcan Aykın", "Altuğ Güneş", "VIP Fizik")
        )
        assertTrue(fizikRec.hasDirectEmbedVideo)

        // 11. Kimya -> Görkem Şahin / Ferrum
        val kimyaRec = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_kimya",
                subject = "Kimya",
                topicHeader = "Mol Kavramı ve Kimyasal Hesaplamalar",
                stemTop = "Avogadro sayısı, mol kütlesi ve tepkime denklemi üzerinden artan madde hesaplaması yapılıyor.",
                questionType = OsymQuestionType.ANALITIK_CEBIRSEL
            ),
            examType = ExamType.TYT,
            subjectName = "Kimya",
            topicName = "Mol Kavramı"
        )
        assertTrue(
            "TYT Kimya için popüler Kimya hocası beklenirdi; gelen: ${kimyaRec.primaryVideo.teacherName}",
            kimyaRec.primaryVideo.teacherName in setOf("Görkem Şahin", "Ferrum")
        )
        assertTrue(kimyaRec.hasDirectEmbedVideo)

        // 12. Biyoloji -> Dr. Biyoloji / Biosem / Selin Hoca
        val biyoRec = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_biyo",
                subject = "Biyoloji",
                topicHeader = "Hücre ve Organeller",
                stemTop = "Ökaryot hücrede mitokondri, ribozom, endoplazmik retikulum ve hücre zarından aktif taşıma süreçleri verilmiştir.",
                questionType = OsymQuestionType.ONCULLU
            ),
            examType = ExamType.TYT,
            subjectName = "Biyoloji",
            topicName = "Hücre"
        )
        assertTrue(
            "TYT Biyoloji için popüler Biyoloji hocası beklenirdi; gelen: ${biyoRec.primaryVideo.teacherName}",
            biyoRec.primaryVideo.teacherName in setOf("Dr. Biyoloji", "Biosem", "Selin Hoca")
        )
        assertTrue(biyoRec.hasDirectEmbedVideo)

        // 13. AYT Edebiyat -> Kadir Gümüş / Rüştü Hoca / Deniz Hoca
        val edebiyatRec = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_edebiyat",
                subject = "Edebiyat",
                topicHeader = "Divan Edebiyatı",
                stemTop = "Divan şiirinde gazel, kaside ve mesnevi nazım biçimlerinin kafiye düzeni ve beyit özellikleri inceleniyor.",
                questionType = OsymQuestionType.ONCULLU
            ),
            examType = ExamType.AYT,
            subjectName = "Edebiyat",
            topicName = "Divan Edebiyatı"
        )
        assertTrue(
            "AYT Edebiyat için popüler Edebiyat hocası beklenirdi; gelen: ${edebiyatRec.primaryVideo.teacherName}",
            edebiyatRec.primaryVideo.teacherName in setOf("Kadir Gümüş", "Rüştü Hoca", "Deniz Hoca")
        )
        assertTrue(edebiyatRec.hasDirectEmbedVideo)

        // 14. TYT Tarih -> Ramazan Yetgin
        val tarihRec = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_tarih",
                subject = "Tarih",
                topicHeader = "Milli Mücadele",
                stemTop = "Amasya Genelgesi, Erzurum ve Sivas Kongreleri kararlarının Kurtuluş Savaşı sürecine etkisi aşağıdakilerden hangisidir?",
                questionType = OsymQuestionType.MANTIK_MUHAKEME
            ),
            examType = ExamType.TYT,
            subjectName = "Tarih",
            topicName = "Milli Mücadele"
        )
        assertEquals("Ramazan Yetgin", tarihRec.primaryVideo.teacherName)
        assertTrue(tarihRec.hasDirectEmbedVideo)

        // 15. TYT Coğrafya -> Coğrafyanın Kodları
        val cografyaRec = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_cog",
                subject = "Coğrafya",
                topicHeader = "Harita Bilgisi ve İklim",
                stemTop = "Verilen izohips haritasında vadi, sırt ve eğim özellikleri ile iklim grafiği yorumlanmaktadır.",
                questionType = OsymQuestionType.GRAFIK_TABLO
            ),
            examType = ExamType.TYT,
            subjectName = "Coğrafya",
            topicName = "Harita Bilgisi"
        )
        assertEquals("Coğrafyanın Kodları", cografyaRec.primaryVideo.teacherName)
        assertTrue(cografyaRec.hasDirectEmbedVideo)
    }

    @Test
    fun criticalTimestampVerification_differentSubTopicsJumpToDifferentNonZeroTimestamps() {
        // A) AYT Türev: Geometrik Yorum vs Ekstremum vs Maksimum-Minimum Optimizasyon
        val turevGeometrik = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_turev_geo",
                subject = "Matematik",
                topicHeader = "Türevin Uygulamaları",
                stemTop = "y = x³ - 3x eğrisine x = 2 apsisli değme noktasından çizilen teğet ve normal doğrusunun eğimi kaçtır?",
                kazanimId = "KAZ_TUREV_03",
                kazanimCode = "MAT.12.5.3.1",
                questionType = OsymQuestionType.GORSEL_GEOMETRIK
            ),
            examType = ExamType.AYT,
            subjectName = "Matematik",
            topicName = "Türevin Uygulamaları"
        )

        val turevEkstremum = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_turev_eks",
                subject = "Matematik",
                topicHeader = "Türevin Uygulamaları",
                stemTop = "f'(x) türev grafiği verilen fonksiyonun yerel maksimum, yerel minimum ve ekstremum noktalarının apsisleri toplamı kaçtır?",
                kazanimId = "KAZ_TUREV_05",
                kazanimCode = "MAT.12.5.3.3",
                questionType = OsymQuestionType.GRAFIK_TABLO
            ),
            examType = ExamType.AYT,
            subjectName = "Matematik",
            topicName = "Türevin Uygulamaları"
        )

        val turevOptimizasyon = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_turev_opt",
                subject = "Matematik",
                topicHeader = "Türevin Uygulamaları",
                stemTop = "Bir parabol içine çizilebilecek dikdörtgenin alanı en çok kaç birimkare olur? Maksimum alan optimizasyonu.",
                kazanimId = "KAZ_TUREV_06",
                kazanimCode = "MAT.12.5.3.4",
                questionType = OsymQuestionType.YENI_NESIL_KURGULU
            ),
            examType = ExamType.AYT,
            subjectName = "Matematik",
            topicName = "Türevin Uygulamaları"
        )

        assertTrue("Türev Geometrik Yorum 0. saniyeden başlamamalı", turevGeometrik.primaryChapter.startSeconds > 0)
        assertTrue("Türev Ekstremum 0. saniyeden başlamamalı", turevEkstremum.primaryChapter.startSeconds > 0)
        assertTrue("Türev Optimizasyon 0. saniyeden başlamamalı", turevOptimizasyon.primaryChapter.startSeconds > 0)

        assertNotEquals(
            "Geometrik Yorum ile Ekstremum farklı dakikalardan başlamalı",
            turevGeometrik.primaryChapter.startSeconds,
            turevEkstremum.primaryChapter.startSeconds
        )
        assertNotEquals(
            "Ekstremum ile Optimizasyon farklı dakikalardan başlamalı",
            turevEkstremum.primaryChapter.startSeconds,
            turevOptimizasyon.primaryChapter.startSeconds
        )
        assertNotEquals(
            "Geometrik Yorum ile Optimizasyon farklı dakikalardan başlamalı",
            turevGeometrik.primaryChapter.startSeconds,
            turevOptimizasyon.primaryChapter.startSeconds
        )

        // B) TYT Türkçe Paragraf: Ana Düşünce vs Akışı Bozan Cümle
        val paragrafAnaDusunce = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_par_ana",
                subject = "Türkçe",
                topicHeader = "Paragrafta Anlam",
                stemTop = "Bu parçada yazarın asıl anlatılmak istenen ana düşüncesi ve vurguladığı mesaj aşağıdakilerden hangisidir?",
                questionType = OsymQuestionType.MANTIK_MUHAKEME
            ),
            examType = ExamType.TYT,
            subjectName = "Türkçe",
            topicName = "Paragrafta Anlam"
        )

        val paragrafAkisiBozan = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_par_akis",
                subject = "Türkçe",
                topicHeader = "Paragrafta Anlam",
                stemTop = "(I) ... (II) ... (III) ... (IV) ... (V) ... Numaralanmış cümlelerden hangisi düşüncenin akışını bozmaktadır ve parça iki paragrafa bölünmek istense hangisiyle başlar?",
                questionType = OsymQuestionType.ONCULLU
            ),
            examType = ExamType.TYT,
            subjectName = "Türkçe",
            topicName = "Paragrafta Anlam"
        )

        assertTrue("Paragraf Ana Düşünce 0. saniyeden başlamamalı", paragrafAnaDusunce.primaryChapter.startSeconds > 0)
        assertTrue("Paragraf Akışı Bozan 0. saniyeden başlamamalı", paragrafAkisiBozan.primaryChapter.startSeconds > 0)
        assertNotEquals(
            "Paragraf Ana Düşünce ile Akışı Bozan farklı dakikalardan başlamalı",
            paragrafAnaDusunce.primaryChapter.startSeconds,
            paragrafAkisiBozan.primaryChapter.startSeconds
        )

        // C) Fonksiyonlar: Bileşke/Ters vs Öteleme/Simetri
        val fonkBileske = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_fonk_bil",
                subject = "Matematik",
                topicHeader = "Fonksiyonlar",
                stemTop = "f(x) = 2x - 1 ve g(x) = x + 4 için (f∘g)⁻¹(7) bileşke ve ters fonksiyon değeri kaçtır?",
                kazanimId = "KAZ_FONK_03",
                kazanimCode = "MAT.10.2.1.3",
                questionType = OsymQuestionType.ANALITIK_CEBIRSEL
            ),
            examType = ExamType.AYT,
            subjectName = "Matematik",
            topicName = "Fonksiyonlar"
        )

        val fonkOteleme = TopicLectureResolver.resolveForQuestion(
            question = buildQuestion(
                id = "q_fonk_otel",
                subject = "Matematik",
                topicHeader = "Fonksiyonlar",
                stemTop = "y = f(x) grafiği 3 birim sağa ve 2 birim yukarı öteleme ile |f(x)| simetri dönüşümü uygulanırsa hangisi elde edilir?",
                kazanimId = "KAZ_FONK_05",
                kazanimCode = "MAT.11.3.2.1",
                questionType = OsymQuestionType.GRAFIK_TABLO
            ),
            examType = ExamType.AYT,
            subjectName = "Matematik",
            topicName = "Fonksiyonlar"
        )

        assertTrue(fonkBileske.primaryChapter.startSeconds > 0)
        assertTrue(fonkOteleme.primaryChapter.startSeconds > 0)
        assertNotEquals(
            "Fonksiyon Bileşke/Ters ile Öteleme/Simetri farklı dakikalardan başlamalı",
            fonkBileske.primaryChapter.startSeconds,
            fonkOteleme.primaryChapter.startSeconds
        )
    }

    @Test
    fun allCatalogVideosAndChapters_produceValidYoutubeUrlsAndNonZeroTimestamps() {
        assertTrue("Katalog boş olmamalı", TopicLectureVideoCatalog.allVideos.isNotEmpty())

        val verifiedVideos = TopicLectureVideoCatalog.allVideos.filter { it.hasVerifiedVideoId }
        val verifiedRatio = verifiedVideos.size.toDouble() / TopicLectureVideoCatalog.allVideos.size.toDouble()
        assertTrue(
            "Doğrulanmış YouTube videoId oranı en az %95 olmalı (mevcut: ${verifiedVideos.size}/${TopicLectureVideoCatalog.allVideos.size})",
            verifiedRatio >= 0.95
        )
        assertEquals(
            "Katalogdaki tüm videolar geçerli 11 karakterlik YouTube videoId içermeli",
            TopicLectureVideoCatalog.allVideos.size,
            verifiedVideos.size
        )

        val uniqueVideoIds = verifiedVideos.map { it.videoId.trim() }.toSet()
        assertEquals(
            "Tüm doğrulanmış YouTube videoId değerleri benzersiz olmalı",
            verifiedVideos.size,
            uniqueVideoIds.size
        )

        for (video in TopicLectureVideoCatalog.allVideos) {
            assertTrue("Video ID benzersiz ve boş olmamalı: ${video.id}", video.id.isNotBlank())
            assertTrue("Hoca adı boş olmamalı: ${video.id}", video.teacherName.isNotBlank())
            assertTrue("Video en az 1 chapter içermeli: ${video.id}", video.chapters.isNotEmpty())
            if (video.popularityRank == 1) {
                assertTrue("Ana hoca videosu en az 2 chapter içermeli: ${video.id}", video.chapters.size >= 2)
            }

            assertTrue(
                "videoId geçerli 11 karakterlik YouTube ID olmalı (${video.id} -> '${video.videoId}')",
                isValidYoutubeVideoId(video.videoId)
            )

            for (chapter in video.chapters) {
                assertTrue(
                    "Chapter startSeconds > 0 olmalı (${video.id}/${chapter.id})",
                    chapter.startSeconds > 0
                )
                chapter.endSeconds?.let { endSec ->
                    assertTrue(
                        "Chapter endSeconds > startSeconds olmalı (${video.id}/${chapter.id}: ${chapter.startSeconds}..$endSec)",
                        endSec > chapter.startSeconds
                    )
                }
                assertTrue("Chapter başlığı boş olmamalı", chapter.chapterTitle.isNotBlank())
                assertTrue("Formül özeti boş olmamalı", chapter.keyFormulaSummary.isNotBlank())
                assertTrue("ÖSYM ipucu boş olmamalı", chapter.osymTip.isNotBlank())

                val watchUrl = video.watchUrlAt(chapter.startSeconds, chapter.chapterTitle)
                val embedUrl = video.embedUrlAt(chapter.startSeconds, chapter.chapterTitle)
                assertValidYoutubeUrls(
                    watchUrl = watchUrl,
                    embedUrl = embedUrl,
                    hasDirectEmbed = video.hasVerifiedVideoId,
                    expectedStartSeconds = chapter.startSeconds
                )
            }
        }
    }

    @Test
    fun newlyEnrichedSubjects_haveDirectEmbedVideosAndWorkingChapterJumps() {
        val sampleQueries = listOf(
            Triple(ExamType.TYT, "Biyoloji", "Hücre ve Organeller"),
            Triple(ExamType.AYT, "Edebiyat", "Divan Edebiyatı"),
            Triple(ExamType.TYT, "Tarih", "Milli Mücadele"),
            Triple(ExamType.TYT, "Coğrafya", "Harita Bilgisi"),
            Triple(ExamType.AYT, "Fizik", "Elektrik ve Manyetizma"),
            Triple(ExamType.AYT, "Kimya", "Kimyasal Denge")
        )

        for ((examType, subject, topic) in sampleQueries) {
            val rec = TopicLectureResolver.resolveForQuestion(
                question = buildQuestion(
                    id = "q_enrich_${subject}_$topic",
                    subject = subject,
                    topicHeader = topic,
                    stemTop = "$topic konusunda ÖSYM tarzı analiz sorusu."
                ),
                examType = examType,
                subjectName = subject,
                topicName = topic
            )
            assertTrue(
                "$subject / $topic doğrudan gömülü YouTube videoId içermeli (video=${rec.primaryVideo.id})",
                rec.hasDirectEmbedVideo
            )
            assertTrue(
                "$subject / $topic videoId 11 karakterlik geçerli formatta olmalı",
                isValidYoutubeVideoId(rec.primaryVideo.videoId)
            )
            assertTrue(
                "$subject / $topic chapter startSeconds > 0 olmalı",
                rec.primaryChapter.startSeconds > 0
            )
            assertValidYoutubeUrls(
                watchUrl = rec.youtubeWatchUrlWithTimestamp,
                embedUrl = rec.youtubeEmbedUrlWithTimestamp,
                hasDirectEmbed = true,
                expectedStartSeconds = rec.primaryChapter.startSeconds
            )
            if (rec.relatedChapters.size >= 2) {
                val otherChapter = rec.relatedChapters.first { it.id != rec.primaryChapter.id }
                val jumped = rec.selectChapter(otherChapter)
                assertEquals(otherChapter.startSeconds, jumped.primaryChapter.startSeconds)
                assertTrue(
                    "Dakika atlaması embed URL'ye yansımalı",
                    jumped.youtubeEmbedUrlWithTimestamp.contains("?start=${otherChapter.startSeconds}&")
                )
            }
        }
    }

    @Test
    fun relatedChapters_alternativeTeachers_determinism_andInteractiveSwitchingWorkCorrectly() {
        val sampleQuestion = buildQuestion(
            id = "q_det_turev",
            subject = "Matematik",
            topicHeader = "Türevin Uygulamaları",
            stemTop = "y = f(x) eğrisine x = 1 noktasından çizilen teğet doğrusunun eğimi ve geometrik yorumu nedir?",
            kazanimId = "KAZ_TUREV_03",
            kazanimCode = "MAT.12.5.3.1",
            questionType = OsymQuestionType.GRAFIK_TABLO
        )

        val firstRun = TopicLectureResolver.resolveForQuestion(
            question = sampleQuestion,
            examType = ExamType.AYT,
            subjectName = "Matematik",
            topicName = "Türevin Uygulamaları"
        )
        val secondRun = TopicLectureResolver.resolveForQuestion(
            question = sampleQuestion,
            examType = ExamType.AYT,
            subjectName = "Matematik",
            topicName = "Türevin Uygulamaları"
        )

        // Determinizm kontrolü
        assertEquals(firstRun, secondRun)

        // URL doğrulaması
        assertValidYoutubeUrls(
            watchUrl = firstRun.youtubeWatchUrlWithTimestamp,
            embedUrl = firstRun.youtubeEmbedUrlWithTimestamp,
            hasDirectEmbed = firstRun.hasDirectEmbedVideo,
            expectedStartSeconds = firstRun.primaryChapter.startSeconds
        )

        // relatedChapters boş olmamalı ve kronolojik sıralı olmalı
        assertTrue("relatedChapters boş olmamalı", firstRun.relatedChapters.size >= 2)
        val timestamps = firstRun.relatedChapters.map { it.startSeconds }
        assertEquals("relatedChapters kronolojik sıralı olmalı", timestamps.sorted(), timestamps)

        // alternativeTeachers boş olmamalı ve ana hocayı tekrar içermemeli
        assertTrue("alternativeTeachers boş olmamalı", firstRun.alternativeTeachers.isNotEmpty())
        assertTrue(
            "Alternatif hocalar arasında ana hoca olmamalı",
            firstRun.alternativeTeachers.none { it.video.teacherName == firstRun.primaryVideo.teacherName }
        )
        for (alt in firstRun.alternativeTeachers) {
            assertValidYoutubeUrls(
                watchUrl = alt.youtubeWatchUrlWithTimestamp,
                embedUrl = alt.youtubeEmbedUrlWithTimestamp,
                hasDirectEmbed = alt.hasDirectEmbedVideo,
                expectedStartSeconds = alt.chapter.startSeconds
            )
        }

        // Bölüm (dakika) atlama çipi etkileşimi: selectChapter
        val anotherChapter = firstRun.relatedChapters.first { it.id != firstRun.primaryChapter.id }
        val switchedChapterRec = firstRun.selectChapter(anotherChapter)
        assertEquals(anotherChapter.id, switchedChapterRec.primaryChapter.id)
        assertEquals(anotherChapter.formattedTimestamp, switchedChapterRec.formattedTimestamp)
        assertNotEquals(firstRun.youtubeEmbedUrlWithTimestamp, switchedChapterRec.youtubeEmbedUrlWithTimestamp)
        assertValidYoutubeUrls(
            watchUrl = switchedChapterRec.youtubeWatchUrlWithTimestamp,
            embedUrl = switchedChapterRec.youtubeEmbedUrlWithTimestamp,
            hasDirectEmbed = switchedChapterRec.hasDirectEmbedVideo,
            expectedStartSeconds = anotherChapter.startSeconds
        )

        // Alternatif hoca değiştirici etkileşimi: selectTeacher
        val alternativeTeacher = firstRun.alternativeTeachers.first()
        val switchedTeacherRec = firstRun.selectTeacher(alternativeTeacher)
        assertEquals(alternativeTeacher.video.id, switchedTeacherRec.primaryVideo.id)
        assertEquals(alternativeTeacher.chapter.id, switchedTeacherRec.primaryChapter.id)
        assertTrue(
            "Önceki ana hoca artık alternatifler listesinde yer almalı",
            switchedTeacherRec.alternativeTeachers.any { it.video.teacherName == firstRun.primaryVideo.teacherName }
        )
        assertTrue(switchedTeacherRec.relatedChapters.isNotEmpty())
        assertValidYoutubeUrls(
            watchUrl = switchedTeacherRec.youtubeWatchUrlWithTimestamp,
            embedUrl = switchedTeacherRec.youtubeEmbedUrlWithTimestamp,
            hasDirectEmbed = switchedTeacherRec.hasDirectEmbedVideo,
            expectedStartSeconds = alternativeTeacher.chapter.startSeconds
        )
    }
}
