package com.example.data

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp

@Immutable
data class TestResultRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val examType: String = "TYT",
    val subjectId: String = "",
    val subjectName: String = "",
    val topicId: String = "",
    val topicName: String = "",
    val testId: String = "",
    val testTitle: String = "",
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val emptyCount: Int = 0,
    val durationSeconds: Int = 0,
    val answersMapJson: String = "{}",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val isSolvedAttempt: Boolean
        get() = !answersMapJson.contains("\"_status\":\"GENERATED\"")

    @get:Exclude
    val totalQuestions: Int
        get() = (correctCount + wrongCount + emptyCount).coerceAtLeast(1)

    @get:Exclude
    val netScore: Double
        get() = correctCount - (wrongCount * 0.25)

    @get:Exclude
    val accuracyPercent: Int
        get() = ((correctCount.toFloat() / totalQuestions.toFloat()) * 100f).toInt().coerceIn(0, 100)
}

@Immutable
data class YksPublisherInfo(
    val id: String,
    val name: String,
    val shortCode: String,
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val customLogoPath: String = ""
)

object SoruLabPublisherPresets {
    val defaultPublishers: List<YksPublisherInfo> = listOf(
        YksPublisherInfo("3d", "3D Yayınları", "3D", 0xFFDC2626, 0xFF991B1B),
        YksPublisherInfo("345", "ÜçDörtBeş Yayınları", "345", 0xFF1D4ED8, 0xFFEA580C),
        YksPublisherInfo("orijinal", "Orijinal Yayınları", "ORJ", 0xFF1E3A8A, 0xFFD97706),
        YksPublisherInfo("bilgi_sarmal", "Bilgi Sarmal", "BS", 0xFFB45309, 0xFF0F172A),
        YksPublisherInfo("apotemi", "Apotemi Yayınları", "APO", 0xFF0F766E, 0xFF047857),
        YksPublisherInfo("aydin", "Aydın Yayınları", "AYD", 0xFF6D28D9, 0xFF4C1D95),
        YksPublisherInfo("acil", "Acil Yayınları", "ACİL", 0xFFE11D48, 0xFF881337),
        YksPublisherInfo("karekok", "Karekök Yayınları", "√K", 0xFF15803D, 0xFF1E293B),
        YksPublisherInfo("toprak", "Toprak Yayınları", "TPR", 0xFFC2410C, 0xFF7C2D12),
        YksPublisherInfo("endemik", "Endemik Yayınları", "END", 0xFF0284C7, 0xFF0369A1),
        YksPublisherInfo("hiz_ve_renk", "Hız ve Renk", "H&R", 0xFF7C3AED, 0xFF2563EB),
        YksPublisherInfo("palme", "Palme Yayınları", "PLM", 0xFF059669, 0xFF065F46)
    )

    fun resolvePublisher(
        publisherTag: String,
        customPublishers: List<YksPublisherInfo> = emptyList()
    ): YksPublisherInfo {
        val all = customPublishers + defaultPublishers
        val trimmed = publisherTag.trim()
        return all.firstOrNull {
            it.name.equals(trimmed, ignoreCase = true) ||
                it.id.equals(trimmed, ignoreCase = true) ||
                trimmed.contains(it.name.substringBefore(" "), ignoreCase = true) ||
                trimmed.contains(it.shortCode, ignoreCase = true)
        } ?: YksPublisherInfo(
            id = "custom_${trimmed.lowercase().replace(" ", "_")}",
            name = trimmed.ifBlank { "ÜçDörtBeş Yayınları" },
            shortCode = trimmed.split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.take(2).uppercase() }
                .ifBlank { "YKS" }
                .take(4),
            primaryColorHex = 0xFF1D4ED8,
            secondaryColorHex = 0xFF0F172A
        )
    }
}

@Immutable
data class CustomQuestionRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val examType: String = "TYT",
    val subjectId: String = "",
    val subjectName: String = "",
    val topicId: String = "",
    val topicName: String = "",
    val difficulty: String = "Orta",
    val correctOption: String = "C",
    val questionText: String = "",
    val publisherTag: String = "ÜçDörtBeş Yayınları",
    val aiSolutionSummary: String = "",
    val cropAspectRatio: Double = 1.25,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    private val parsedMetaMap: Map<String, String>
        get() {
            if (!aiSolutionSummary.startsWith("[META:")) return emptyMap()
            val header = aiSolutionSummary.substringAfter("[META:").substringBefore("]")
            return header.split("|").mapNotNull { entry ->
                val idx = entry.indexOf('=')
                if (idx > 0) entry.substring(0, idx) to entry.substring(idx + 1) else null
            }.toMap()
        }

    @get:Exclude
    val targetLevelName: String
        get() = parsedMetaMap["level"].orEmpty()

    @get:Exclude
    val croppedImagePath: String
        get() = parsedMetaMap["img"].orEmpty()

    @get:Exclude
    val publisherLogoUri: String
        get() = parsedMetaMap["logo"].orEmpty()

    @get:Exclude
    val answerKeyVerified: Boolean
        get() = parsedMetaMap["ak"] != "0"

    @get:Exclude
    val questionNumberInBook: Int
        get() = parsedMetaMap["qnum"]?.toIntOrNull() ?: 0

    @get:Exclude
    val cleanSolutionSummary: String
        get() = if (aiSolutionSummary.startsWith("[META:")) {
            aiSolutionSummary.substringAfter("]").trimStart()
        } else {
            aiSolutionSummary
        }

    companion object {
        fun packSolutionWithMeta(
            solutionText: String,
            targetLevel: String = "",
            croppedImagePath: String = "",
            publisherLogoUri: String = "",
            answerKeyVerified: Boolean = true,
            questionNumberInBook: Int = 0
        ): String {
            val cleanSol = if (solutionText.startsWith("[META:")) {
                solutionText.substringAfter("]").trimStart()
            } else {
                solutionText.trim()
            }
            val metaParts = buildList {
                if (targetLevel.isNotBlank()) add("level=${targetLevel.replace("|", "")}")
                if (croppedImagePath.isNotBlank()) add("img=${croppedImagePath.replace("|", "")}")
                if (publisherLogoUri.isNotBlank()) add("logo=${publisherLogoUri.replace("|", "")}")
                add("ak=${if (answerKeyVerified) "1" else "0"}")
                if (questionNumberInBook > 0) add("qnum=$questionNumberInBook")
            }
            val prefix = "[META:${metaParts.joinToString("|")}] "
            val maxSolLen = (3800 - prefix.length).coerceAtLeast(200)
            return prefix + cleanSol.take(maxSolLen)
        }
    }
}

enum class ExamType(val label: String, val fullTitle: String) {
    TYT("TYT", "Temel Yeterlilik Testi"),
    AYT("AYT", "Alan Yeterlilik Testi")
}

enum class DifficultyLevel(val label: String, val weight: Float) {
    KOLAY("Kolay", 0.22f),
    ORTA("Orta", 0.52f),
    ZOR("Zor", 0.85f)
}

enum class TopicTestLevel(
    val wheelLabel: String,
    val displayTitle: String,
    val shortTag: String,
    val wedgeColorHex: Long,
    val textColorHex: Long,
    val difficultyPosition: Float,
    val defaultDifficulty: DifficultyLevel,
    val poolDistributionLabel: String
) {
    BASLANGIC(
        wheelLabel = "BAŞLANGIÇ",
        displayTitle = "Başlangıç Tarzı",
        shortTag = "Başlangıç",
        wedgeColorHex = 0xFFD4C585,
        textColorHex = 0xFF9C7214,
        difficultyPosition = 0.20f,
        defaultDifficulty = DifficultyLevel.KOLAY,
        poolDistributionLabel = "Temel kavrama ve kazanım odaklı (%80 Kolay • %20 Orta)"
    ),
    IKIZ(
        wheelLabel = "İKİZ",
        displayTitle = "İkiz Tarzı",
        shortTag = "İkiz",
        wedgeColorHex = 0xFFD3B484,
        textColorHex = 0xFFB86614,
        difficultyPosition = 0.48f,
        defaultDifficulty = DifficultyLevel.ORTA,
        poolDistributionLabel = "Çözdüğün testlerde yanlış yaptığın soruların birebir ikiz eşlenikleri"
    ),
    PEKISTIRME(
        wheelLabel = "PEKİŞTİRME",
        displayTitle = "Pekiştirme Tarzı",
        shortTag = "Pekiştirme",
        wedgeColorHex = 0xFFD99588,
        textColorHex = 0xFFC72C2C,
        difficultyPosition = 0.52f,
        defaultDifficulty = DifficultyLevel.ORTA,
        poolDistributionLabel = "Ağırlıklı orta, az kolay ve az zor etiketli (%20 Kolay • %60 Orta • %20 Zor)"
    ),
    GELISIYORUM(
        wheelLabel = "GELİŞİYORUM",
        displayTitle = "Gelişiyorum Tarzı",
        shortTag = "Gelişiyorum",
        wedgeColorHex = 0xFF1574E5,
        textColorHex = 0xFFEFF6FF,
        difficultyPosition = 0.64f,
        defaultDifficulty = DifficultyLevel.ORTA,
        poolDistributionLabel = "Orta ve ileri düzey yorum becerisi geliştiren sorular"
    ),
    OSYM(
        wheelLabel = "ÖSYM",
        displayTitle = "ÖSYM Tarzı",
        shortTag = "ÖSYM",
        wedgeColorHex = 0xFF6BA5CE,
        textColorHex = 0xFF0F3D7A,
        difficultyPosition = 0.68f,
        defaultDifficulty = DifficultyLevel.ORTA,
        poolDistributionLabel = "ÖSYM sınav formatında yeni nesil muhakeme etiketli sorular"
    ),
    DERECE(
        wheelLabel = "DERECE",
        displayTitle = "Derece Tarzı",
        shortTag = "Derece",
        wedgeColorHex = 0xFF7B88CC,
        textColorHex = 0xFF1D2268,
        difficultyPosition = 0.88f,
        defaultDifficulty = DifficultyLevel.ZOR,
        poolDistributionLabel = "İlk 1.000 hedefli seçici ve zor etiketli sorular"
    )
}

enum class QuestionVisualType {
    NONE,
    MARKET_RECEIPT,
    LEGO_BLOCKS,
    VENN_DIAGRAM,
    FUNCTION_GRAPH,
    GEOMETRY_TRIANGLE,
    PHYSICS_VECTORS
}

@Immutable
data class YksQuestion(
    val id: String,
    val number: Int,
    val badgeTitle: String = "GÜNLÜK ÖDEV 5",
    val topicHeader: String = "TEMEL KAVRAMLAR",
    val publisherStamp: String = "ÜÇDÖRTBEŞ YAYINLARI",
    val publisherName: String = "ÜçDörtBeş Yayınları",
    val publisherLogoUri: String = "",
    val croppedImagePath: String = "",
    val answerKeyVerified: Boolean = true,
    val stemTop: String,
    val bulletsOrSteps: List<String> = emptyList(),
    val stemBottom: String,
    val options: Map<String, String>,
    val correctOption: String,
    val difficulty: DifficultyLevel = DifficultyLevel.ORTA,
    val visualType: QuestionVisualType = QuestionVisualType.NONE,
    val stepByStepSolution: String
)

@Immutable
data class YksTestItem(
    val id: String,
    val topicId: String,
    val title: String,
    val fullTitle: String,
    val difficulty: DifficultyLevel,
    val difficultyPosition: Float,
    val defaultCompleted: Boolean = false,
    val starRating: Int = 0,
    val level: TopicTestLevel = TopicTestLevel.OSYM,
    val questions: List<YksQuestion>
)

@Immutable
data class YksTopic(
    val id: String,
    val subjectId: String,
    val name: String,
    val questionCount: Int,
    val completedTests: Int,
    val totalTests: Int,
    val badgeColorHex: Long,
    val iconType: String,
    val tests: List<YksTestItem>
)

@Immutable
data class YksSubject(
    val id: String,
    val examType: ExamType,
    val name: String,
    val accentColorHex: Long,
    val topics: List<YksTopic>
)

@Immutable
data class MockExamItem(
    val id: String,
    val title: String,
    val publisher: String,
    val examType: ExamType,
    val questionCount: Int,
    val durationMinutes: Int,
    val difficulty: DifficultyLevel,
    val tag: String,
    val isSolvedDefault: Boolean = false,
    val defaultNet: Double = 0.0,
    val linkedTest: YksTestItem
)

object SoruLabCatalog {

    val temelKavramlarOdev5Questions: List<YksQuestion> = emptyList()

    fun buildDefaultPracticeTest(
        topicId: String,
        topicName: String,
        suffix: String,
        shortTitle: String,
        pos: Float,
        completed: Boolean = false
    ): YksTestItem {
        val diff = when {
            pos < 0.35f -> DifficultyLevel.KOLAY
            pos < 0.68f -> DifficultyLevel.ORTA
            else -> DifficultyLevel.ZOR
        }
        return YksTestItem(
            id = "${topicId}_$suffix",
            topicId = topicId,
            title = shortTitle,
            fullTitle = "$topicName - $shortTitle",
            difficulty = diff,
            difficultyPosition = pos,
            defaultCompleted = completed,
            starRating = 0,
            level = TopicTestLevel.OSYM,
            questions = emptyList()
        )
    }

    private fun topicEntry(
        id: String,
        subjectId: String,
        name: String,
        completedTests: Int = 0,
        badgeColorHex: Long = 0xFFE05A47,
        iconType: String = "math"
    ): YksTopic = YksTopic(
        id = id,
        subjectId = subjectId,
        name = name,
        questionCount = 0,
        completedTests = 0,
        totalTests = 0,
        badgeColorHex = badgeColorHex,
        iconType = iconType,
        tests = emptyList()
    )

    val allSubjects: List<YksSubject> = listOf(
        // ==================== TYT DERSLERİ VE AYRI/TAM KONULARI ====================
        YksSubject(
            id = "tyt_turkce",
            examType = ExamType.TYT,
            name = "Türkçe",
            accentColorHex = 0xFFE5A024,
            topics = listOf(
                topicEntry("sozcukte_anlam", "tyt_turkce", "Sözcükte Anlam", 0, 0xFFE5A024, "book"),
                topicEntry("soz_obeginde_anlam", "tyt_turkce", "Söz Öbeğinde Anlam", 0, 0xFFE5A024, "book"),
                topicEntry("cumlede_anlam", "tyt_turkce", "Cümlede Anlam", 0, 0xFFE5A024, "book"),
                topicEntry("paragrafta_anlam", "tyt_turkce", "Paragrafta Anlam", 0, 0xFFE5A024, "book"),
                topicEntry("paragrafta_yapi", "tyt_turkce", "Paragrafta Yapı", 0, 0xFFE5A024, "book"),
                topicEntry("anlatim_bicimleri", "tyt_turkce", "Anlatım Biçimleri", 0, 0xFFE5A024, "book"),
                topicEntry("ses_bilgisi", "tyt_turkce", "Ses Bilgisi", 0, 0xFFE5A024, "book"),
                topicEntry("yazim_kurallari", "tyt_turkce", "Yazım Kuralları", 0, 0xFFE5A024, "book"),
                topicEntry("noktalama_isaretleri", "tyt_turkce", "Noktalama İşaretleri", 0, 0xFFE5A024, "book"),
                topicEntry("sozcukte_yapi", "tyt_turkce", "Sözcükte Yapı ve Ekler", 0, 0xFFE5A024, "book"),
                topicEntry("isimler", "tyt_turkce", "İsimler (Adlar)", 0, 0xFFE5A024, "book"),
                topicEntry("sifatlar", "tyt_turkce", "Sıfatlar (Ön Adlar)", 0, 0xFFE5A024, "book"),
                topicEntry("zamirler", "tyt_turkce", "Zamirler (Adıllar)", 0, 0xFFE5A024, "book"),
                topicEntry("zarflar", "tyt_turkce", "Zarflar (Belirteçler)", 0, 0xFFE5A024, "book"),
                topicEntry("edat_baglac_unlem", "tyt_turkce", "Edat, Bağlaç ve Ünlem", 0, 0xFFE5A024, "book"),
                topicEntry("fiiller", "tyt_turkce", "Fiiller (Eylemler)", 0, 0xFFE5A024, "book"),
                topicEntry("fiilimsiler", "tyt_turkce", "Fiilimsiler (Eylemsiler)", 0, 0xFFE5A024, "book"),
                topicEntry("fiilde_cati", "tyt_turkce", "Fiilde Çatı", 0, 0xFFE5A024, "book"),
                topicEntry("cumlenin_ogeleri", "tyt_turkce", "Cümlenin Ögeleri", 0, 0xFFE5A024, "book"),
                topicEntry("cumle_turleri", "tyt_turkce", "Cümle Türleri", 0, 0xFFE5A024, "book"),
                topicEntry("anlatim_bozukluklari", "tyt_turkce", "Anlatım Bozuklukları", 0, 0xFFE5A024, "book")
            )
        ),
        YksSubject(
            id = "tyt_mat",
            examType = ExamType.TYT,
            name = "Matematik",
            accentColorHex = 0xFFE05A47,
            topics = listOf(
                topicEntry("temel_kavramlar", "tyt_mat", "Temel Kavramlar", 0, 0xFFE05A47, "math"),
                topicEntry("sayi_basamaklari", "tyt_mat", "Sayı Basamakları", 0, 0xFFE05A47, "math"),
                topicEntry("bolme_bolunebilme", "tyt_mat", "Bölme ve Bölünebilme", 0, 0xFFE05A47, "math"),
                topicEntry("ebob_ekok", "tyt_mat", "EBOB - EKOK", 0, 0xFFE05A47, "math"),
                topicEntry("rasyonel_sayilar", "tyt_mat", "Rasyonel Sayılar", 0, 0xFFE05A47, "math"),
                topicEntry("ondalik_sayilar", "tyt_mat", "Ondalık Sayılar", 0, 0xFFE05A47, "math"),
                topicEntry("birinci_dereceden_denklemler", "tyt_mat", "1. Dereceden Denklemler", 0, 0xFFE05A47, "math"),
                topicEntry("basit_esitsizlikler", "tyt_mat", "Basit Eşitsizlikler", 0, 0xFFE05A47, "math"),
                topicEntry("mutlak_deger", "tyt_mat", "Mutlak Değer", 0, 0xFFE05A47, "math"),
                topicEntry("uslu_sayilar", "tyt_mat", "Üslü Sayılar", 0, 0xFFE05A47, "math"),
                topicEntry("koklu_sayilar", "tyt_mat", "Köklü Sayılar", 0, 0xFFE05A47, "math"),
                topicEntry("carpanlara_ayirma", "tyt_mat", "Çarpanlara Ayırma", 0, 0xFFE05A47, "math"),
                topicEntry("oran_oranti", "tyt_mat", "Oran - Orantı", 0, 0xFFE05A47, "math"),
                topicEntry("sayi_problemleri", "tyt_mat", "Sayı Problemleri", 0, 0xFFE05A47, "math"),
                topicEntry("kesir_problemleri", "tyt_mat", "Kesir Problemleri", 0, 0xFFE05A47, "math"),
                topicEntry("yas_problemleri", "tyt_mat", "Yaş Problemleri", 0, 0xFFE05A47, "math"),
                topicEntry("isci_problemleri", "tyt_mat", "İşçi Problemleri", 0, 0xFFE05A47, "math"),
                topicEntry("hareket_problemleri", "tyt_mat", "Hız ve Hareket Problemleri", 0, 0xFFE05A47, "math"),
                topicEntry("yuzde_kar_zarar_problemleri", "tyt_mat", "Yüzde ve Kâr-Zarar Problemleri", 0, 0xFFE05A47, "math"),
                topicEntry("karisim_problemleri", "tyt_mat", "Karışım Problemleri", 0, 0xFFE05A47, "math"),
                topicEntry("grafik_problemleri", "tyt_mat", "Grafik Problemleri", 0, 0xFFE05A47, "math"),
                topicEntry("rutin_olmayan_problemler", "tyt_mat", "Rutin Olmayan Problemler", 0, 0xFFE05A47, "math"),
                topicEntry("mantik", "tyt_mat", "Mantık", 0, 0xFFE05A47, "math"),
                topicEntry("kumeler", "tyt_mat", "Kümeler", 0, 0xFFE05A47, "math"),
                topicEntry("kartezyen_carpim", "tyt_mat", "Kartezyen Çarpım", 0, 0xFFE05A47, "math"),
                topicEntry("tyt_fonksiyonlar", "tyt_mat", "Fonksiyonlar", 0, 0xFFE05A47, "math"),
                topicEntry("tyt_polinomlar", "tyt_mat", "Polinomlar", 0, 0xFFE05A47, "math"),
                topicEntry("tyt_ikinci_derece_denklemler", "tyt_mat", "2. Dereceden Denklemler", 0, 0xFFE05A47, "math"),
                topicEntry("permutasyon", "tyt_mat", "Permütasyon", 0, 0xFFE05A47, "math"),
                topicEntry("kombinasyon", "tyt_mat", "Kombinasyon", 0, 0xFFE05A47, "math"),
                topicEntry("binom", "tyt_mat", "Binom Açılımı", 0, 0xFFE05A47, "math"),
                topicEntry("olasilik", "tyt_mat", "Olasılık", 0, 0xFFE05A47, "math"),
                topicEntry("veri_istatistik", "tyt_mat", "Veri ve İstatistik", 0, 0xFFE05A47, "math")
            )
        ),
        YksSubject(
            id = "tyt_geo",
            examType = ExamType.TYT,
            name = "Geometri",
            accentColorHex = 0xFF0EA5E9,
            topics = listOf(
                topicEntry("dogruda_acilar", "tyt_geo", "Doğruda Açılar", 0, 0xFF0EA5E9, "angle"),
                topicEntry("ucgende_acilar", "tyt_geo", "Üçgende Açılar", 0, 0xFF0EA5E9, "angle"),
                topicEntry("dik_ucgen", "tyt_geo", "Dik Üçgen ve Pisagor", 0, 0xFF0EA5E9, "angle"),
                topicEntry("ikizkenar_eskenar_ucgen", "tyt_geo", "İkizkenar ve Eşkenar Üçgen", 0, 0xFF0EA5E9, "angle"),
                topicEntry("ucgende_aciortay", "tyt_geo", "Üçgende Açıortay", 0, 0xFF0EA5E9, "angle"),
                topicEntry("ucgende_kenarortay", "tyt_geo", "Üçgende Kenarortay", 0, 0xFF0EA5E9, "angle"),
                topicEntry("ucgende_eslik_benzerlik", "tyt_geo", "Üçgende Eşlik ve Benzerlik", 0, 0xFF0EA5E9, "angle"),
                topicEntry("ucgende_alan", "tyt_geo", "Üçgende Alan", 0, 0xFF0EA5E9, "angle"),
                topicEntry("aci_kenar_bagintilari", "tyt_geo", "Açı - Kenar Bağıntıları", 0, 0xFF0EA5E9, "angle"),
                topicEntry("cokgenler", "tyt_geo", "Çokgenler", 0, 0xFF0EA5E9, "angle"),
                topicEntry("dortgenler", "tyt_geo", "Genel Dörtgenler", 0, 0xFF0EA5E9, "angle"),
                topicEntry("yamuk", "tyt_geo", "Yamuk", 0, 0xFF0EA5E9, "angle"),
                topicEntry("paralelkenar", "tyt_geo", "Paralelkenar", 0, 0xFF0EA5E9, "angle"),
                topicEntry("eskenar_dortgen_deltoid", "tyt_geo", "Eşkenar Dörtgen ve Deltoid", 0, 0xFF0EA5E9, "angle"),
                topicEntry("dikdortgen_kare", "tyt_geo", "Dikdörtgen ve Kare", 0, 0xFF0EA5E9, "angle"),
                topicEntry("cember_daire_tyt", "tyt_geo", "Çember ve Daire", 0, 0xFF0EA5E9, "angle"),
                topicEntry("prizmalar_tyt", "tyt_geo", "Prizmalar", 0, 0xFF0EA5E9, "angle"),
                topicEntry("piramit_koni_kure_tyt", "tyt_geo", "Piramit, Koni ve Küre", 0, 0xFF0EA5E9, "angle")
            )
        ),
        YksSubject(
            id = "tyt_fizik",
            examType = ExamType.TYT,
            name = "Fizik",
            accentColorHex = 0xFF10B981,
            topics = listOf(
                topicEntry("fizik_bilimine_giris", "tyt_fizik", "Fizik Bilimine Giriş", 0, 0xFF10B981, "vector"),
                topicEntry("madde_ve_ozellikleri", "tyt_fizik", "Madde ve Özellikleri", 0, 0xFF10B981, "vector"),
                topicEntry("hareket_tyt", "tyt_fizik", "Doğrusal Hareket", 0, 0xFF10B981, "vector"),
                topicEntry("kuvvet_newton_tyt", "tyt_fizik", "Kuvvet ve Newton Yasaları", 0, 0xFF10B981, "vector"),
                topicEntry("is_guc_enerji_tyt", "tyt_fizik", "İş, Güç ve Enerji", 0, 0xFF10B981, "vector"),
                topicEntry("isi_sicaklik", "tyt_fizik", "Isı ve Sıcaklık", 0, 0xFF10B981, "vector"),
                topicEntry("genlesme", "tyt_fizik", "Hal Değişimi ve Genleşme", 0, 0xFF10B981, "vector"),
                topicEntry("basinc", "tyt_fizik", "Basınç", 0, 0xFF10B981, "vector"),
                topicEntry("kaldirma_kuvveti", "tyt_fizik", "Kaldırma Kuvveti", 0, 0xFF10B981, "vector"),
                topicEntry("elektrostatik", "tyt_fizik", "Elektrostatik", 0, 0xFF10B981, "vector"),
                topicEntry("elektrik_akimi_devreler", "tyt_fizik", "Elektrik Akımı ve Devreler", 0, 0xFF10B981, "vector"),
                topicEntry("manyetizma_tyt", "tyt_fizik", "Mıknatıslar ve Manyetizma", 0, 0xFF10B981, "vector"),
                topicEntry("dalgalar_tyt", "tyt_fizik", "Yay, Su, Ses ve Deprem Dalgaları", 0, 0xFF10B981, "vector"),
                topicEntry("optik_aydinlanma_aynalar", "tyt_fizik", "Aydınlanma ve Aynalar", 0, 0xFF10B981, "vector"),
                topicEntry("optik_kirilma_mercekler", "tyt_fizik", "Kırılma, Mercekler ve Renk", 0, 0xFF10B981, "vector")
            )
        ),
        YksSubject(
            id = "tyt_kimya",
            examType = ExamType.TYT,
            name = "Kimya",
            accentColorHex = 0xFF8B5CF6,
            topics = listOf(
                topicEntry("kimya_bilimi", "tyt_kimya", "Kimya Bilimi", 0, 0xFF8B5CF6, "flask"),
                topicEntry("atom_modelleri", "tyt_kimya", "Atom ve Yapısı", 0, 0xFF8B5CF6, "flask"),
                topicEntry("periyodik_sistem", "tyt_kimya", "Periyodik Sistem", 0, 0xFF8B5CF6, "flask"),
                topicEntry("kimyasal_turler_etkilesim", "tyt_kimya", "Kimyasal Türler Arası Etkileşimler", 0, 0xFF8B5CF6, "flask"),
                topicEntry("maddenin_halleri", "tyt_kimya", "Maddenin Halleri", 0, 0xFF8B5CF6, "flask"),
                topicEntry("doga_ve_kimya", "tyt_kimya", "Doğa ve Kimya", 0, 0xFF8B5CF6, "flask"),
                topicEntry("kimyanin_temel_kanunlari", "tyt_kimya", "Kimyanın Temel Kanunları", 0, 0xFF8B5CF6, "flask"),
                topicEntry("mol_kavrami", "tyt_kimya", "Mol Kavramı", 0, 0xFF8B5CF6, "flask"),
                topicEntry("kimyasal_hesaplamalar", "tyt_kimya", "Kimyasal Tepkimeler ve Hesaplamalar", 0, 0xFF8B5CF6, "flask"),
                topicEntry("karisimlar", "tyt_kimya", "Karışımlar", 0, 0xFF8B5CF6, "flask"),
                topicEntry("asitler_bazlar_tuzlar", "tyt_kimya", "Asitler, Bazlar ve Tuzlar", 0, 0xFF8B5CF6, "flask"),
                topicEntry("kimya_her_yerde", "tyt_kimya", "Kimya Her Yerde", 0, 0xFF8B5CF6, "flask")
            )
        ),
        YksSubject(
            id = "tyt_biyoloji",
            examType = ExamType.TYT,
            name = "Biyoloji",
            accentColorHex = 0xFFEC4899,
            topics = listOf(
                topicEntry("canlilarin_ortak_ozellikleri", "tyt_biyoloji", "Canlıların Ortak Özellikleri", 0, 0xFFEC4899, "bio"),
                topicEntry("canlilarin_temel_bilesenleri", "tyt_biyoloji", "Canlıların Temel Bileşenleri", 0, 0xFFEC4899, "bio"),
                topicEntry("hucre_ve_organeller", "tyt_biyoloji", "Hücre ve Organeller", 0, 0xFFEC4899, "bio"),
                topicEntry("hucre_zarindan_madde_gecisi", "tyt_biyoloji", "Hücre Zarından Madde Geçişleri", 0, 0xFFEC4899, "bio"),
                topicEntry("canlilarin_siniflandirilmasi", "tyt_biyoloji", "Canlıların Sınıflandırılması", 0, 0xFFEC4899, "bio"),
                topicEntry("mitoz_ve_eseysiz_ureme", "tyt_biyoloji", "Mitoz ve Eşeysiz Üreme", 0, 0xFFEC4899, "bio"),
                topicEntry("mayoz_ve_eseyli_ureme", "tyt_biyoloji", "Mayoz ve Eşeyli Üreme", 0, 0xFFEC4899, "bio"),
                topicEntry("kalitim", "tyt_biyoloji", "Kalıtım ve Biyoçeşitlilik", 0, 0xFFEC4899, "bio"),
                topicEntry("ekosistem_ekolojisi", "tyt_biyoloji", "Ekosistem Ekolojisi", 0, 0xFFEC4899, "bio"),
                topicEntry("guncel_cevre_sorunlari", "tyt_biyoloji", "Güncel Çevre Sorunları", 0, 0xFFEC4899, "bio")
            )
        ),
        YksSubject(
            id = "tyt_tarih",
            examType = ExamType.TYT,
            name = "Tarih",
            accentColorHex = 0xFF2563EB,
            topics = listOf(
                topicEntry("tarih_ve_zaman_tyt", "tyt_tarih", "Tarih ve Zaman", 0, 0xFF2563EB, "book"),
                topicEntry("insanligin_ilk_donemleri", "tyt_tarih", "İnsanlığın İlk Dönemleri", 0, 0xFF2563EB, "book"),
                topicEntry("ilk_orta_cag_turk", "tyt_tarih", "İlk ve Orta Çağlarda Türk Dünyası", 0, 0xFF2563EB, "book"),
                topicEntry("islam_medeniyetinin_dogusu", "tyt_tarih", "İslam Medeniyetinin Doğuşu", 0, 0xFF2563EB, "book"),
                topicEntry("turklerin_islamiyeti_kabulu", "tyt_tarih", "Türklerin İslamiyet'i Kabulü", 0, 0xFF2563EB, "book"),
                topicEntry("beylikten_devlete_osmanli", "tyt_tarih", "Beylikten Devlete Osmanlı", 0, 0xFF2563EB, "book"),
                topicEntry("dunya_gucu_osmanli", "tyt_tarih", "Dünya Gücü Osmanlı", 0, 0xFF2563EB, "book"),
                topicEntry("kurtulus_savasi_hazirlik", "tyt_tarih", "Milli Mücadele Hazırlık Dönemi", 0, 0xFF2563EB, "book"),
                topicEntry("kurtulus_savasi_cepheler", "tyt_tarih", "Kurtuluş Savaşı Cepheler ve Antlaşmalar", 0, 0xFF2563EB, "book"),
                topicEntry("ataturk_ilke_inkilaplari", "tyt_tarih", "Atatürk İlke ve İnkılapları", 0, 0xFF2563EB, "book")
            )
        ),
        YksSubject(
            id = "tyt_cografya",
            examType = ExamType.TYT,
            name = "Coğrafya",
            accentColorHex = 0xFF0D9488,
            topics = listOf(
                topicEntry("doga_ve_insan", "tyt_cografya", "Doğa ve İnsan", 0, 0xFF0D9488, "book"),
                topicEntry("dunyanin_sekli_hareketleri", "tyt_cografya", "Dünya'nın Şekli ve Hareketleri", 0, 0xFF0D9488, "book"),
                topicEntry("cografi_konum", "tyt_cografya", "Coğrafi Konum", 0, 0xFF0D9488, "book"),
                topicEntry("harita_bilgisi", "tyt_cografya", "Harita Bilgisi", 0, 0xFF0D9488, "book"),
                topicEntry("atmosfer_ve_iklim", "tyt_cografya", "Atmosfer ve İklim Bilgisi", 0, 0xFF0D9488, "book"),
                topicEntry("ic_ve_dis_kuvvetler", "tyt_cografya", "İç ve Dış Kuvvetler", 0, 0xFF0D9488, "book"),
                topicEntry("su_toprak_bitki", "tyt_cografya", "Su, Toprak ve Bitki Varlığı", 0, 0xFF0D9488, "book"),
                topicEntry("nufus_ve_yerlesme", "tyt_cografya", "Nüfus ve Göç", 0, 0xFF0D9488, "book"),
                topicEntry("ekonomik_faaliyetler", "tyt_cografya", "Ekonomik Faaliyetler ve Bölgeler", 0, 0xFF0D9488, "book"),
                topicEntry("dogal_afetler", "tyt_cografya", "Doğal Afetler", 0, 0xFF0D9488, "book")
            )
        ),
        YksSubject(
            id = "tyt_felsefe_din",
            examType = ExamType.TYT,
            name = "Felsefe ve Din Kültürü",
            accentColorHex = 0xFF6366F1,
            topics = listOf(
                topicEntry("felsefeyi_tanima", "tyt_felsefe_din", "Felsefeyi Tanıma", 0, 0xFF6366F1, "book"),
                topicEntry("bilgi_felsefesi", "tyt_felsefe_din", "Bilgi Felsefesi", 0, 0xFF6366F1, "book"),
                topicEntry("varlik_felsefesi", "tyt_felsefe_din", "Varlık Felsefesi", 0, 0xFF6366F1, "book"),
                topicEntry("ahlak_felsefesi", "tyt_felsefe_din", "Ahlak Felsefesi", 0, 0xFF6366F1, "book"),
                topicEntry("bilgi_ve_inanc", "tyt_felsefe_din", "Bilgi ve İnanç", 0, 0xFF6366F1, "book"),
                topicEntry("din_ve_islam", "tyt_felsefe_din", "Din, İbadet ve Ahlak", 0, 0xFF6366F1, "book")
            )
        ),

        // ==================== AYT DERSLERİ VE AYRI/TAM KONULARI ====================
        YksSubject(
            id = "ayt_mat",
            examType = ExamType.AYT,
            name = "Matematik",
            accentColorHex = 0xFFE05A47,
            topics = listOf(
                topicEntry("fonksiyonlar", "ayt_mat", "Fonksiyonlarda Uygulamalar", 0, 0xFFE05A47, "math"),
                topicEntry("polinomlar", "ayt_mat", "Polinomlar", 0, 0xFFE05A47, "math"),
                topicEntry("ikinci_derece_denklemler", "ayt_mat", "2. Dereceden Denklemler", 0, 0xFFE05A47, "math"),
                topicEntry("karmasik_sayilar", "ayt_mat", "Karmaşık Sayılar", 0, 0xFFE05A47, "math"),
                topicEntry("parabol", "ayt_mat", "Parabol", 0, 0xFFE05A47, "math"),
                topicEntry("ikinci_derece_esitsizlikler", "ayt_mat", "2. Dereceden Eşitsizlikler", 0, 0xFFE05A47, "math"),
                topicEntry("trigonometri_ayt_mat", "ayt_mat", "Trigonometri", 0, 0xFFE05A47, "math"),
                topicEntry("logaritma", "ayt_mat", "Logaritma", 0, 0xFFE05A47, "math"),
                topicEntry("diziler", "ayt_mat", "Diziler", 0, 0xFFE05A47, "math"),
                topicEntry("limit_ve_sureklilik", "ayt_mat", "Limit ve Süreklilik", 0, 0xFFE05A47, "math"),
                topicEntry("turev_alma_kurallari", "ayt_mat", "Türev Alma Kuralları", 0, 0xFFE05A47, "math"),
                topicEntry("turev_uygulamalari", "ayt_mat", "Türevin Geometrik ve Fiziksel Uygulamaları", 0, 0xFFE05A47, "math"),
                topicEntry("belirsiz_integral", "ayt_mat", "Belirsiz İntegral", 0, 0xFFE05A47, "math"),
                topicEntry("belirli_integral_alan", "ayt_mat", "Belirli İntegral ve Alan Hesabı", 0, 0xFFE05A47, "math")
            )
        ),
        YksSubject(
            id = "ayt_geo",
            examType = ExamType.AYT,
            name = "Geometri",
            accentColorHex = 0xFF0EA5E9,
            topics = listOf(
                topicEntry("cemberde_aci", "ayt_geo", "Çemberde Açı", 0, 0xFF0EA5E9, "angle"),
                topicEntry("cemberde_uzunluk", "ayt_geo", "Çemberde Uzunluk", 0, 0xFF0EA5E9, "angle"),
                topicEntry("dairede_alan", "ayt_geo", "Dairede Çevre ve Alan", 0, 0xFF0EA5E9, "angle"),
                topicEntry("noktanin_analitigi", "ayt_geo", "Noktanın Analitik İncelenmesi", 0, 0xFF0EA5E9, "angle"),
                topicEntry("dogrunun_analitigi", "ayt_geo", "Doğrunun Analitik İncelenmesi", 0, 0xFF0EA5E9, "angle"),
                topicEntry("donusum_geometrisi", "ayt_geo", "Dönüşüm Geometrisi", 0, 0xFF0EA5E9, "angle"),
                topicEntry("cemberin_analitigi", "ayt_geo", "Çemberin Analitik İncelenmesi", 0, 0xFF0EA5E9, "angle"),
                topicEntry("kati_cisimler_ayt", "ayt_geo", "Silindir, Koni ve Küre", 0, 0xFF0EA5E9, "angle")
            )
        ),
        YksSubject(
            id = "ayt_fizik",
            examType = ExamType.AYT,
            name = "Fizik",
            accentColorHex = 0xFF10B981,
            topics = listOf(
                topicEntry("vektorler", "ayt_fizik", "Vektörler", 0, 0xFF10B981, "vector"),
                topicEntry("bagil_hareket", "ayt_fizik", "Bağıl Hareket", 0, 0xFF10B981, "vector"),
                topicEntry("newton_hareket_yasalari", "ayt_fizik", "Newton'un Hareket Yasaları", 0, 0xFF10B981, "vector"),
                topicEntry("bir_boyutta_sabit_ivmeli", "ayt_fizik", "Bir Boyutta Sabit İvmeli Hareket", 0, 0xFF10B981, "vector"),
                topicEntry("atislar", "ayt_fizik", "İki Boyutta Hareket (Atışlar)", 0, 0xFF10B981, "vector"),
                topicEntry("enerji_ve_hareket", "ayt_fizik", "İş, Enerji ve Güç", 0, 0xFF10B981, "vector"),
                topicEntry("itme_momentum", "ayt_fizik", "İtme ve Çizgisel Momentum", 0, 0xFF10B981, "vector"),
                topicEntry("tork_ve_denge", "ayt_fizik", "Tork ve Denge", 0, 0xFF10B981, "vector"),
                topicEntry("kutle_merkezi_basit_makine", "ayt_fizik", "Kütle Merkezi ve Basit Makineler", 0, 0xFF10B981, "vector"),
                topicEntry("elektrik_alan_potansiyel", "ayt_fizik", "Elektrik Alan ve Potansiyel", 0, 0xFF10B981, "vector"),
                topicEntry("manyetizma_induksiyon", "ayt_fizik", "Manyetizma ve Elektromanyetik İndüksiyon", 0, 0xFF10B981, "vector"),
                topicEntry("alternatif_akim_transformator", "ayt_fizik", "Alternatif Akım ve Transformatörler", 0, 0xFF10B981, "vector"),
                topicEntry("cembersel_hareket", "ayt_fizik", "Düzgün Çembersel Hareket", 0, 0xFF10B981, "vector"),
                topicEntry("basit_harmonik_hareket", "ayt_fizik", "Basit Harmonik Hareket", 0, 0xFF10B981, "vector"),
                topicEntry("dalga_mekanigi", "ayt_fizik", "Dalga Mekaniği", 0, 0xFF10B981, "vector"),
                topicEntry("atom_fizigi_radyoaktivite", "ayt_fizik", "Atom Fiziğine Giriş ve Radyoaktivite", 0, 0xFF10B981, "vector"),
                topicEntry("modern_fizik", "ayt_fizik", "Modern Fizik", 0, 0xFF10B981, "vector")
            )
        ),
        YksSubject(
            id = "ayt_kimya",
            examType = ExamType.AYT,
            name = "Kimya",
            accentColorHex = 0xFF8B5CF6,
            topics = listOf(
                topicEntry("modern_atom_teorisi", "ayt_kimya", "Modern Atom Teorisi", 0, 0xFF8B5CF6, "flask"),
                topicEntry("gazlar", "ayt_kimya", "Gazlar", 0, 0xFF8B5CF6, "flask"),
                topicEntry("sivi_cozeltiler", "ayt_kimya", "Sıvı Çözeltiler ve Çözünürlük", 0, 0xFF8B5CF6, "flask"),
                topicEntry("kimyasal_enerji", "ayt_kimya", "Kimyasal Tepkimelerde Enerji", 0, 0xFF8B5CF6, "flask"),
                topicEntry("kimyasal_hiz", "ayt_kimya", "Kimyasal Tepkimelerde Hız", 0, 0xFF8B5CF6, "flask"),
                topicEntry("kimyasal_denge", "ayt_kimya", "Kimyasal Tepkimelerde Denge", 0, 0xFF8B5CF6, "flask"),
                topicEntry("asit_baz_dengesi", "ayt_kimya", "Sulu Çözeltilerde Asit-Baz Dengesi", 0, 0xFF8B5CF6, "flask"),
                topicEntry("cozunurluk_dengesi", "ayt_kimya", "Çözünürlük Dengesi (Kçç)", 0, 0xFF8B5CF6, "flask"),
                topicEntry("kimya_ve_elektrik", "ayt_kimya", "Kimya ve Elektrik", 0, 0xFF8B5CF6, "flask"),
                topicEntry("karbon_kimyasina_giris", "ayt_kimya", "Karbon Kimyasına Giriş", 0, 0xFF8B5CF6, "flask"),
                topicEntry("organik_bilesikler", "ayt_kimya", "Organik Bileşikler", 0, 0xFF8B5CF6, "flask")
            )
        ),
        YksSubject(
            id = "ayt_biyoloji",
            examType = ExamType.AYT,
            name = "Biyoloji",
            accentColorHex = 0xFFEC4899,
            topics = listOf(
                topicEntry("sinir_sistemi", "ayt_biyoloji", "Sinir Sistemi", 0, 0xFFEC4899, "bio"),
                topicEntry("endokrin_sistem", "ayt_biyoloji", "Endokrin Sistem", 0, 0xFFEC4899, "bio"),
                topicEntry("duyu_organlari", "ayt_biyoloji", "Duyu Organları", 0, 0xFFEC4899, "bio"),
                topicEntry("destek_ve_hareket", "ayt_biyoloji", "Destek ve Hareket Sistemi", 0, 0xFFEC4899, "bio"),
                topicEntry("sindirim_sistemi", "ayt_biyoloji", "Sindirim Sistemi", 0, 0xFFEC4899, "bio"),
                topicEntry("dolasim_ve_bagisiklik", "ayt_biyoloji", "Dolaşım ve Bağışıklık Sistemi", 0, 0xFFEC4899, "bio"),
                topicEntry("solunum_sistemi", "ayt_biyoloji", "Solunum Sistemi", 0, 0xFFEC4899, "bio"),
                topicEntry("uriner_sistem", "ayt_biyoloji", "Üriner (Boşaltım) Sistem", 0, 0xFFEC4899, "bio"),
                topicEntry("ureme_sistemi_embriyonik", "ayt_biyoloji", "Üreme Sistemi ve Embriyonik Gelişim", 0, 0xFFEC4899, "bio"),
                topicEntry("komunite_ve_populasyon", "ayt_biyoloji", "Komünite ve Popülasyon Ekolojisi", 0, 0xFFEC4899, "bio"),
                topicEntry("genden_proteine", "ayt_biyoloji", "Genden Proteine (Nükleik Asitler)", 0, 0xFFEC4899, "bio"),
                topicEntry("canlilarda_enerji_donusumleri", "ayt_biyoloji", "Fotosentez ve Hücresel Solunum", 0, 0xFFEC4899, "bio"),
                topicEntry("bitki_biyolojisi", "ayt_biyoloji", "Bitki Biyolojisi", 0, 0xFFEC4899, "bio"),
                topicEntry("canlilar_ve_cevre", "ayt_biyoloji", "Canlılar ve Çevre", 0, 0xFFEC4899, "bio")
            )
        ),
        YksSubject(
            id = "ayt_edebiyat",
            examType = ExamType.AYT,
            name = "Edebiyat",
            accentColorHex = 0xFFE5A024,
            topics = listOf(
                topicEntry("guzel_sanatlar_ve_edebiyat", "ayt_edebiyat", "Güzel Sanatlar ve Edebiyat", 0, 0xFFE5A024, "book"),
                topicEntry("siir_bilgisi", "ayt_edebiyat", "Şiir Bilgisi", 0, 0xFFE5A024, "book"),
                topicEntry("edebi_sanatlar", "ayt_edebiyat", "Edebi Sanatlar (Söz Sanatları)", 0, 0xFFE5A024, "book"),
                topicEntry("islamiyet_oncesi_turk_edebiyati", "ayt_edebiyat", "İslamiyet Öncesi Türk Edebiyatı", 0, 0xFFE5A024, "book"),
                topicEntry("halk_edebiyati", "ayt_edebiyat", "Halk Edebiyatı", 0, 0xFFE5A024, "book"),
                topicEntry("divan_edebiyati", "ayt_edebiyat", "Divan Edebiyatı", 0, 0xFFE5A024, "book"),
                topicEntry("tanzimat_edebiyati", "ayt_edebiyat", "Tanzimat Dönemi Edebiyatı", 0, 0xFFE5A024, "book"),
                topicEntry("servetifunun_fecriati", "ayt_edebiyat", "Servetifünun ve Fecriati Edebiyatı", 0, 0xFFE5A024, "book"),
                topicEntry("milli_edebiyat", "ayt_edebiyat", "Milli Edebiyat Dönemi", 0, 0xFFE5A024, "book"),
                topicEntry("cumhuriyet_siir", "ayt_edebiyat", "Cumhuriyet Dönemi Şiir", 0, 0xFFE5A024, "book"),
                topicEntry("cumhuriyet_roman_hikaye", "ayt_edebiyat", "Cumhuriyet Dönemi Roman ve Hikâye", 0, 0xFFE5A024, "book"),
                topicEntry("edebi_akimlar", "ayt_edebiyat", "Edebi Akımlar", 0, 0xFFE5A024, "book")
            )
        ),
        YksSubject(
            id = "ayt_tarih_cografya",
            examType = ExamType.AYT,
            name = "Tarih ve Coğrafya",
            accentColorHex = 0xFF2563EB,
            topics = listOf(
                topicEntry("ayt_ilk_cag_uygarliklari", "ayt_tarih_cografya", "İlk ve Orta Çağ Uygarlıkları", 0, 0xFF2563EB, "book"),
                topicEntry("ayt_osmanli_kultur_medeniyet", "ayt_tarih_cografya", "Osmanlı Kültür ve Medeniyeti", 0, 0xFF2563EB, "book"),
                topicEntry("ayt_xx_yuzyil_osmanli", "ayt_tarih_cografya", "20. Yüzyıl Başlarında Osmanlı ve Dünya", 0, 0xFF2563EB, "book"),
                topicEntry("ayt_milli_mucadele", "ayt_tarih_cografya", "Milli Mücadele ve İnkılap Tarihi", 0, 0xFF2563EB, "book"),
                topicEntry("ayt_ekosistem_ve_biyoceşitlilik", "ayt_tarih_cografya", "Biyoçeşitlilik ve Ekosistem", 0, 0xFF0D9488, "book"),
                topicEntry("ayt_turkiye_ekonomisi", "ayt_tarih_cografya", "Türkiye'de Tarım, Madenler ve Sanayi", 0, 0xFF0D9488, "book"),
                topicEntry("ayt_kuresel_ortam_bolgeler", "ayt_tarih_cografya", "Küresel Ortam: Bölgeler ve Ülkeler", 0, 0xFF0D9488, "book")
            )
        )
    )

    val mockExams: List<MockExamItem> = emptyList()
}

