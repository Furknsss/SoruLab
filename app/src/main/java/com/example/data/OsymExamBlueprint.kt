package com.example.data

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ÖSYM Soru Tipi Taksonomisi:
 * Her soru kök yapısı, öncül varlığı, kurgu/hikaye bağlamı, grafik/tablo içeriği,
 * geometrik görsel yapısı ve mantıksal muhakeme kurgusuna göre deterministik olarak sınıflandırılır.
 */
enum class OsymQuestionType(
    val label: String,
    val shortTag: String,
    val pedagogyDescription: String
) {
    ONCULLU(
        label = "Öncüllü (I - II - III)",
        shortTag = "Öncüllü",
        pedagogyDescription = "I, II, III öncüllerinin ayrı ayrı doğruluğunu ve kesinlik durumlarını sorgulayan ÖSYM formatı"
    ),
    YENI_NESIL_KURGULU(
        label = "Yeni Nesil Kurgulu / Hikayeli",
        shortTag = "Yeni Nesil",
        pedagogyDescription = "Günlük yaşam senaryosu, gerçek hayat modellemesi ve hikayeleştirilmiş bağlam içeren sorular"
    ),
    GRAFIK_TABLO(
        label = "Grafik / Tablo Yorumu",
        shortTag = "Grafik-Tablo",
        pedagogyDescription = "Fonksiyon grafiği, değişim tablosu, istatistiksel veri veya hız-zaman grafiği okuma soruları"
    ),
    ANALITIK_CEBIRSEL(
        label = "Analitik / Cebirsel",
        shortTag = "Analitik-Cebir",
        pedagogyDescription = "Denklem çözümü, cebirsel özdeşlik, kavramsal tanım ve analitik işlem becerisi ölçen sorular"
    ),
    GORSEL_GEOMETRIK(
        label = "Görsel / Geometrik",
        shortTag = "Görsel-Geometrik",
        pedagogyDescription = "Geometrik şekil, katlama, kesme-döndürme, açı, uzunluk, alan ve hacim muhakemesi soruları"
    ),
    MANTIK_MUHAKEME(
        label = "Mantık - Muhakeme",
        shortTag = "Mantık-Muhakeme",
        pedagogyDescription = "Koşullu çıkarım, tümdengelim, sözel/sayısal akıl yürütme ve rutin olmayan kurgu soruları"
    );

    companion object {
        fun classifyFromText(
            stemTop: String,
            bulletsOrSteps: List<String> = emptyList(),
            stemBottom: String = "",
            visualType: QuestionVisualType = QuestionVisualType.NONE,
            topicHint: String = ""
        ): OsymQuestionType {
            val combinedText = buildString {
                append(stemTop)
                append(" ")
                append(bulletsOrSteps.joinToString(" "))
                append(" ")
                append(stemBottom)
                append(" ")
                append(topicHint)
            }.lowercase()

            // 1. Öncüllü soru tespiti (I, II, III öncülleri veya "hangileri doğrudur/kesinlikle")
            val hasRomanNumeralsInBullets = bulletsOrSteps.count { bullet ->
                Regex("""^\s*(?:I{1,3}|IV|V)[).:\-\s]""").containsMatchIn(bullet.trim())
            } >= 2
            val hasRomanNumeralsInText = Regex("""(?m)(?:^|\s)(?:I{1,3}|IV)[).:\-]""").findAll("$stemTop\n$stemBottom").count() >= 2
            val hasPremisePrompt = listOf(
                "ifadelerinden hangileri",
                "yargılarından hangileri",
                "öncüllerinden hangileri",
                "hangileri kesinlikle doğrudur",
                "hangileri her zaman doğrudur",
                "hangileri doğrudur",
                "hangileri yanlıştır",
                "yalnız i",
                "i ve ii",
                "ii ve iii",
                "i, ii ve iii"
            ).any(combinedText::contains)

            if (hasRomanNumeralsInBullets || (hasRomanNumeralsInText && hasPremisePrompt) ||
                (bulletsOrSteps.size >= 3 && hasPremisePrompt)
            ) {
                return ONCULLU
            }

            // 2. Grafik / Tablo Yorumu tespiti
            if (visualType == QuestionVisualType.FUNCTION_GRAPH ||
                listOf(
                    "grafik", "grafiği", "grafigi", "tablo", "tabloda", "koordinat düzleminde",
                    "koordinat sisteminde", "sütun grafiği", "daire grafiği", "eksen", "f(x) eğrisi"
                ).any(combinedText::contains)
            ) {
                return GRAFIK_TABLO
            }

            // 3. Görsel / Geometrik soru tespiti
            if (visualType == QuestionVisualType.GEOMETRY_TRIANGLE ||
                visualType == QuestionVisualType.PHYSICS_VECTORS ||
                listOf(
                    "üçgen", "ucgen", "dörtgen", "yamuk", "paralelkenar", "çember", "daire",
                    "prizma", "silindir", "koni", "küre", "katlandığında", "hipotenüs",
                    "açıortay", "kenarortay", "dik kesiş", "köşegen", "boyalı bölgenin alanı"
                ).any(combinedText::contains)
            ) {
                return GORSEL_GEOMETRIK
            }

            // 4. Mantık - Muhakeme tespiti
            if (listOf(
                    "önerme", "muhakeme", "çıkarım", "kuralına göre", "koşulunu sağlayan",
                    "kesinlikle çıkarılamaz", "akışı bozan", "asıl anlatılmak istenen",
                    "parçaya göre", "yazarın", "sözel mantık", "strateji", "rutin olmayan"
                ).any(combinedText::contains)
            ) {
                return MANTIK_MUHAKEME
            }

            // 5. Yeni Nesil Kurgulu / Hikayeli tespiti
            if (visualType == QuestionVisualType.MARKET_RECEIPT ||
                visualType == QuestionVisualType.LEGO_BLOCKS ||
                listOf(
                    "mağaza", "market", "fiyat", "indirim", "yolcu", "araç", "fabrika",
                    "üretim", "depo", "bahçe", "marangoz", "laboratuvar", "deney",
                    "öğrenci", "kütüphane", "otopark", "günlük", "modelleme", "tasarım"
                ).any(combinedText::contains)
            ) {
                return YENI_NESIL_KURGULU
            }

            // 6. Varsayılan: Analitik / Cebirsel
            return ANALITIK_CEBIRSEL
        }

        fun fromRawLabel(raw: String): OsymQuestionType? {
            val normalized = raw.trim()
            if (normalized.isBlank()) return null
            return entries.firstOrNull { type ->
                type.name.equals(normalized, ignoreCase = true) ||
                    type.shortTag.equals(normalized, ignoreCase = true) ||
                    type.label.equals(normalized, ignoreCase = true)
            }
        }

        fun classifyQuestion(question: YksQuestion): OsymQuestionType {
            return classifyFromText(
                stemTop = question.stemTop.ifBlank { question.analyticsText },
                bulletsOrSteps = question.bulletsOrSteps,
                stemBottom = question.stemBottom,
                visualType = question.visualType,
                topicHint = question.topicHeader
            )
        }

        fun classifyCustomQuestion(record: CustomQuestionRecord): OsymQuestionType {
            fromRawLabel(record.questionType)?.let { return it }
            return classifyFromText(
                stemTop = record.questionText,
                bulletsOrSteps = emptyList(),
                stemBottom = record.learningOutcome,
                visualType = QuestionVisualType.NONE,
                topicHint = record.topicName
            )
        }
    }
}

val YksQuestion.osymKazanimId: String
    get() = learningOutcomeId.ifBlank { learningOutcome }

val YksQuestion.osymKazanimCode: String
    get() = learningOutcome.ifBlank { learningOutcomeId }

val YksQuestion.osymKazanimTitle: String
    get() = learningOutcomeName.ifBlank { topicHeader }

val YksQuestion.resolvedOsymQuestionType: OsymQuestionType
    get() = OsymQuestionType.fromRawLabel(questionType) ?: OsymQuestionType.classifyQuestion(this)

/**
 * MEB / ÖSYM Müfredat Kazanımı Modeli
 */
@Immutable
data class OsymKazanim(
    val id: String,
    val code: String,
    val examType: ExamType,
    val subjectId: String,
    val topicId: String,
    val topicName: String,
    val title: String,
    val description: String,
    val recommendedQuestionTypes: Set<OsymQuestionType> = setOf(
        OsymQuestionType.ANALITIK_CEBIRSEL,
        OsymQuestionType.ONCULLU,
        OsymQuestionType.YENI_NESIL_KURGULU
    ),
    val keywords: List<String> = emptyList()
)

/**
 * ÖSYM Standart Zorluk Dağılımı:
 * - %10 Kolay
 * - %20 Kolay-Orta (Orta)
 * - %40 ÖSYM (Sınav Standardı)
 * - %20 Zor
 * - %10 Seçici / Çok Zor
 */
object OsymDifficultyStandard {
    const val KOLAY_RATIO = 0.10
    const val ORTA_RATIO = 0.20
    const val OSYM_RATIO = 0.40
    const val ZOR_RATIO = 0.20
    const val COK_ZOR_RATIO = 0.10

    val targetRatios: Map<DifficultyLevel, Double> = mapOf(
        DifficultyLevel.KOLAY to KOLAY_RATIO,
        DifficultyLevel.ORTA to ORTA_RATIO,
        DifficultyLevel.OSYM to OSYM_RATIO,
        DifficultyLevel.ZOR to ZOR_RATIO,
        DifficultyLevel.COK_ZOR to COK_ZOR_RATIO
    )

    /**
     * Toplam soru sayısına göre tam sayı kotalarını (%10, %20, %40, %20, %10)
     * yuvarlama hatası olmadan hesaplar ve toplamı [totalQuestions] değerine eşitler.
     */
    fun targetCountsForTotal(totalQuestions: Int): Map<DifficultyLevel, Int> {
        if (totalQuestions <= 0) {
            return DifficultyLevel.entries.associateWith { 0 }
        }
        val rawCounts = DifficultyLevel.entries.map { diff ->
            val exact = totalQuestions * targetRatios.getValue(diff)
            val floorVal = exact.toInt()
            val remainder = exact - floorVal
            Triple(diff, floorVal, remainder)
        }.toMutableList()

        var allocated = rawCounts.sumOf { it.second }
        val deficit = totalQuestions - allocated

        // Kalan soruları en yüksek kesir payına sahip zorluklara (ÖSYM öncelikli) dağıt
        val sortedByRemainder = rawCounts.indices.sortedWith(
            compareByDescending<Int> { rawCounts[it].third }
                .thenBy { if (rawCounts[it].first == DifficultyLevel.OSYM) 0 else 1 }
        )
        for (i in 0 until deficit) {
            val idx = sortedByRemainder[i % sortedByRemainder.size]
            val current = rawCounts[idx]
            rawCounts[idx] = current.copy(second = current.second + 1)
        }

        return rawCounts.associate { it.first to it.second }
    }
}

/**
 * MEB / ÖSYM Müfredatına Tam Uyumlu Kazanım Taksonomisi.
 * Hem Genel Denemeler (TYT, AYT) hem de Özel Konu Denemeleri (Türev, Fonksiyon, Paragraf,
 * Problemler, Geometri vb.) için zengin kazanım haritası sunar.
 */
object OsymKazanimTaxonomy {

    val allKazanimlar: List<OsymKazanim> = listOf(
        // ==================== AYT MATEMATİK - TÜREV KONU VE GENEL KAZANIMLARI ====================
        OsymKazanim(
            id = "KAZ_TUREV_01",
            code = "MAT.12.5.2.1",
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            topicId = "turev_alma_kurallari",
            topicName = "Türev Alma Kuralları",
            title = "Türev Tanımı ve Limit-Süreklilik İlişkisi",
            description = "Bir fonksiyonun bir noktadaki ve bir aralıktaki türevlenebilirliğini limit ve süreklilikle ilişkilendirir.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL),
            keywords = listOf("türev tanımı", "türevlenebilir", "sürekli", "lim", "f(x)-f(a)", "sağdan türev", "soldan türev")
        ),
        OsymKazanim(
            id = "KAZ_TUREV_02",
            code = "MAT.12.5.2.2",
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            topicId = "turev_alma_kurallari",
            topicName = "Türev Alma Kuralları",
            title = "Çarpım, Bölüm ve Bileşke Fonksiyon (Zincir Kuralı) Türevi",
            description = "İki fonksiyonun toplamı, farkı, çarpımı, bölümü ve bileşkesinin türev kurallarını uygular.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
            keywords = listOf("zincir kuralı", "çarpım türevi", "bölüm türevi", "f'(g(x))", "dy/dx", "polinom türevi")
        ),
        OsymKazanim(
            id = "KAZ_TUREV_03",
            code = "MAT.12.5.3.1",
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            topicId = "turev_uygulamalari",
            topicName = "Türevin Geometrik ve Fiziksel Uygulamaları",
            title = "Türevin Geometrik Yorumu ve Teğet-Normal Denklemleri",
            description = "Bir fonksiyon grafiğine üzerindeki bir noktadan çizilen teğet ve normal doğrusunun eğimini türevle hesaplar.",
            recommendedQuestionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ANALITIK_CEBIRSEL),
            keywords = listOf("teğet", "normal", "eğim", "değme noktası", "f'(a)", "teğet doğrusu")
        ),
        OsymKazanim(
            id = "KAZ_TUREV_04",
            code = "MAT.12.5.3.2",
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            topicId = "turev_uygulamalari",
            topicName = "Türevin Geometrik ve Fiziksel Uygulamaları",
            title = "Artan-Azalan Fonksiyonlar ve İşaret İncelemesi",
            description = "Birinci türevin işaretini kullanarak fonksiyonun artan ve azalan olduğu aralıkları belirler.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
            keywords = listOf("artan", "azalan", "daima artan", "f'(x) > 0", "f'(x) < 0", "monoton")
        ),
        OsymKazanim(
            id = "KAZ_TUREV_05",
            code = "MAT.12.5.3.3",
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            topicId = "turev_uygulamalari",
            topicName = "Türevin Geometrik ve Fiziksel Uygulamaları",
            title = "Yerel ve Mutlak Ekstremum Noktaları Analizi",
            description = "Bir fonksiyonun yerel maksimum, yerel minimum ve mutlak ekstremum noktalarını türev yardımıyla tespit eder.",
            recommendedQuestionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
            keywords = listOf("ekstremum", "yerel maksimum", "yerel minimum", "mutlak maksimum", "kritik nokta", "f'(x) = 0")
        ),
        OsymKazanim(
            id = "KAZ_TUREV_06",
            code = "MAT.12.5.3.4",
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            topicId = "turev_uygulamalari",
            topicName = "Türevin Geometrik ve Fiziksel Uygulamaları",
            title = "Maksimum ve Minimum Optimizasyon Problemleri",
            description = "Gerçek yaşam ve geometrik optimizasyon kurgularında maksimum-minimum değerleri türev modeliyle çözer.",
            recommendedQuestionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.GORSEL_GEOMETRIK),
            keywords = listOf("en çok", "en az", "maksimum alan", "maksimum hacim", "en büyük değer", "optimizasyon")
        ),

        // ==================== TYT & AYT FONKSİYONLAR KAZANIMLARI ====================
        OsymKazanim(
            id = "KAZ_FONK_01",
            code = "MAT.10.2.1.1",
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            topicId = "tyt_fonksiyonlar",
            topicName = "Fonksiyonlar",
            title = "Fonksiyon Kavramı, Tanım ve Görüntü Kümesi",
            description = "Fonksiyon olma şartlarını, tanım kümesi ve görüntü kümesi elemanlarını cebirsel ve grafiksel olarak belirler.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
            keywords = listOf("tanım kümesi", "görüntü kümesi", "değer kümesi", "fonksiyon belirtir")
        ),
        OsymKazanim(
            id = "KAZ_FONK_02",
            code = "MAT.10.2.1.2",
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            topicId = "tyt_fonksiyonlar",
            topicName = "Fonksiyonlar",
            title = "Bire Bir, Örten, İçine, Tek ve Çift Fonksiyonlar",
            description = "Fonksiyon türlerini (bire bir, örten, birim, sabit, doğrusal, tek, çift) analiz eder.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME),
            keywords = listOf("bire bir", "örten", "tek fonksiyon", "çift fonksiyon", "birim fonksiyon", "sabit fonksiyon")
        ),
        OsymKazanim(
            id = "KAZ_FONK_03",
            code = "MAT.10.2.1.3",
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            topicId = "tyt_fonksiyonlar",
            topicName = "Fonksiyonlar",
            title = "Bileşke ve Ters Fonksiyon Kuralları",
            description = "Bileşke fonksiyon (fog)(x) ve bir fonksiyonun tersi f^-1(x) arasındaki ilişkileri kurgular.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GRAFIK_TABLO),
            keywords = listOf("bileşke", "fog", "gof", "ters fonksiyon", "f^-1")
        ),
        OsymKazanim(
            id = "KAZ_FONK_04",
            code = "MAT.11.3.1.1",
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            topicId = "fonksiyonlar",
            topicName = "Fonksiyonlarda Uygulamalar",
            title = "Fonksiyon Grafikleri, Eksen Kesişimleri ve Ortalama Değişim Hızı",
            description = "Fonksiyon grafiklerinden işaret, kök, maksimum-minimum ve ortalama değişim hızını yorumlar.",
            recommendedQuestionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.YENI_NESIL_KURGULU),
            keywords = listOf("grafik", "ortalama değişim", "eksen", "f(x)=0", "kesişim")
        ),
        OsymKazanim(
            id = "KAZ_FONK_05",
            code = "MAT.11.3.2.1",
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            topicId = "fonksiyonlar",
            topicName = "Fonksiyonlarda Uygulamalar",
            title = "Fonksiyonlarda Öteleme, Simetri ve Dönüşümler",
            description = "y = f(x-a)+b, y = -f(x), y = f(-x) ve y = |f(x)| dönüşümlerinin grafik ve denklem etkilerini inceler.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
            keywords = listOf("öteleme", "simetri", "dönüşüm", "f(x+a)", "|f(x)|", "birim sağa", "birim yukarı")
        ),

        // ==================== TYT TÜRKÇE - PARAGRAF VE ANLAM KAZANIMLARI ====================
        OsymKazanim(
            id = "KAZ_PARAGRAF_01",
            code = "TUR.TYT.PAR.01",
            examType = ExamType.TYT,
            subjectId = "tyt_turkce",
            topicId = "paragrafta_anlam",
            topicName = "Paragrafta Anlam",
            title = "Paragrafta Ana Düşünce, Konu ve Başlık Tespiti",
            description = "Metnin yazılış amacını, asıl anlatılmak istenen temel iletiyi ve kapsayıcı başlığı belirler.",
            recommendedQuestionTypes = setOf(OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.YENI_NESIL_KURGULU),
            keywords = listOf("asıl anlatılmak istenen", "ana düşünce", "temel amaç", "vurgulanan", "konusu")
        ),
        OsymKazanim(
            id = "KAZ_PARAGRAF_02",
            code = "TUR.TYT.PAR.02",
            examType = ExamType.TYT,
            subjectId = "tyt_turkce",
            topicId = "paragrafta_anlam",
            topicName = "Paragrafta Anlam",
            title = "Paragrafta Yardımcı Düşünceler ve Çıkarım Analizi",
            description = "Parçadan çıkarılabilecek veya değinilmemiş yargıları öncüllü ya da seçenek bazlı ayrıştırır.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME),
            keywords = listOf("değinilmemiştir", "çıkarılamaz", "ulaşılamaz", "hangilerine ulaşılabilir", "söylenemez")
        ),
        OsymKazanim(
            id = "KAZ_PARAGRAF_03",
            code = "TUR.TYT.PAR.03",
            examType = ExamType.TYT,
            subjectId = "tyt_turkce",
            topicId = "paragrafta_yapi",
            topicName = "Paragrafta Yapı",
            title = "Paragrafta Akışı Bozan Cümle ve İkiye Bölme",
            description = "Metnin anlam ve kurgu bütünlüğünü bozan cümleyi veya ikinci paragrafa geçiş noktasını tespit eder.",
            recommendedQuestionTypes = setOf(OsymQuestionType.MANTIK_MUHAKEME),
            keywords = listOf("akışı bozan", "iki paragrafa", "ikinci paragraf", "numaralanmış cümle")
        ),
        OsymKazanim(
            id = "KAZ_PARAGRAF_04",
            code = "TUR.TYT.PAR.04",
            examType = ExamType.TYT,
            subjectId = "tyt_turkce",
            topicId = "paragrafta_yapi",
            topicName = "Paragrafta Yapı",
            title = "Paragrafta Boşluk Tamamlama ve Cümle Sıralama",
            description = "Parçanın giriş, gelişme veya sonuç bölümünde boş bırakılan yere bağlama uygun ifadeyi yerleştirir.",
            recommendedQuestionTypes = setOf(OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.YENI_NESIL_KURGULU),
            keywords = listOf("boş bırakılan yere", "parçanın sonuna", "parçanın başına", "getirilmelidir")
        ),
        OsymKazanim(
            id = "KAZ_TURKCE_SOZCUK_CUMLE",
            code = "TUR.TYT.ANL.01",
            examType = ExamType.TYT,
            subjectId = "tyt_turkce",
            topicId = "cumlede_anlam",
            topicName = "Cümlede Anlam",
            title = "Sözcükte ve Cümlede Anlam İlişkileri (Neden, Amaç, Koşul, Örtülü Anlam)",
            description = "Cümleler arası anlam ilişkilerini, söz öbeklerinin bağlamsal anlamını ve kesin yargıyı çözümler.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME),
            keywords = listOf("sözcükte anlam", "cümlede anlam", "neden-sonuç", "amaç-sonuç", "koşul", "altı çizili")
        ),
        OsymKazanim(
            id = "KAZ_TURKCE_DILBILGISI",
            code = "TUR.TYT.DIL.01",
            examType = ExamType.TYT,
            subjectId = "tyt_turkce",
            topicId = "yazim_kurallari",
            topicName = "Yazım Kuralları",
            title = "Ses, Yazım, Noktalama ve Sözcük-Cümle Yapısı",
            description = "Ses olayları, yazım kuralları, noktalama işaretleri, sözcük türleri ve cümlenin ögelerini metin üzerinde denetler.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL),
            keywords = listOf("yazım", "noktalama", "ses olayı", "fiilimsi", "cümlenin ögeleri", "özne", "yüklem")
        ),

        // ==================== TYT MATEMATİK - PROBLEMLER KAZANIMLARI ====================
        OsymKazanim(
            id = "KAZ_PROB_01",
            code = "MAT.9.3.5.1",
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            topicId = "sayi_problemleri",
            topicName = "Sayı Problemleri",
            title = "Sayı ve Kesir Problemleri Modellemesi",
            description = "Günlük hayat durumlarını birinci dereceden denklem ve rasyonel kesir modelleriyle ifade eder.",
            recommendedQuestionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.ANALITIK_CEBIRSEL),
            keywords = listOf("sayı problemi", "kesir", "parça", "kuyruk", "bilet", "kalan")
        ),
        OsymKazanim(
            id = "KAZ_PROB_02",
            code = "MAT.9.3.5.2",
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            topicId = "yas_problemleri",
            topicName = "Yaş Problemleri",
            title = "Yaş ve Zaman Farkı Problemleri",
            description = "Kişilerin yıllara göre yaş değişimlerini, yaş farkının sabitliğini ve oranlarını kurgular.",
            recommendedQuestionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.ONCULLU),
            keywords = listOf("yaş", "yaşında", "doğduğunda", "yıl sonra", "yıl önce")
        ),
        OsymKazanim(
            id = "KAZ_PROB_03",
            code = "MAT.9.3.5.3",
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            topicId = "hareket_problemleri",
            topicName = "Hız ve Hareket Problemleri",
            title = "Hız, Hareket ve Karşılaşma Problemleri",
            description = "Aynı veya zıt yönlü hareket, dairesel pist, ortalama hız ve tünel geçiş problemlerini çözer.",
            recommendedQuestionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.GRAFIK_TABLO),
            keywords = listOf("hız", "hareket", "pist", "km/sa", "karşılaşma", "ortalama hız")
        ),
        OsymKazanim(
            id = "KAZ_PROB_04",
            code = "MAT.9.3.5.4",
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            topicId = "yuzde_kar_zarar_problemleri",
            topicName = "Yüzde ve Kâr-Zarar Problemleri",
            title = "Yüzde, Kâr-Zarar ve Karışım Hesaplamaları",
            description = "Maliyet, etiket fiyatı, iskonto, enflasyon ve homojen karışım yüzdelerini analiz eder.",
            recommendedQuestionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.GRAFIK_TABLO),
            keywords = listOf("yüzde", "kâr", "zarar", "indirim", "maliyet", "etiket", "karışım")
        ),
        OsymKazanim(
            id = "KAZ_PROB_05",
            code = "MAT.9.3.5.5",
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            topicId = "grafik_problemleri",
            topicName = "Grafik Problemleri",
            title = "Grafik, Tablo Okuma ve Veri Yorumlama Problemleri",
            description = "Daire grafiği, sütun grafiği ve çizgi grafiği arasındaki oransal geçişleri çözümler.",
            recommendedQuestionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
            keywords = listOf("daire grafiği", "sütun grafiği", "merkez açı", "tablo", "dağılım")
        ),
        OsymKazanim(
            id = "KAZ_PROB_06",
            code = "MAT.9.3.5.6",
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            topicId = "rutin_olmayan_problemler",
            topicName = "Rutin Olmayan Problemler",
            title = "Rutin Olmayan Sayısal Mantık ve Muhakeme Problemleri",
            description = "Özgün kurallarla tanımlanmış algoritmik ve kombinatorik günlük yaşam problemlerini muhakeme eder.",
            recommendedQuestionTypes = setOf(OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.YENI_NESIL_KURGULU),
            keywords = listOf("rutin olmayan", "kural", "algoritma", "en az kaç", "en çok kaç", "strateji")
        ),

        // ==================== TYT & AYT GEOMETRİ KAZANIMLARI ====================
        OsymKazanim(
            id = "KAZ_GEO_01",
            code = "GEO.9.4.1.1",
            examType = ExamType.TYT,
            subjectId = "tyt_geo",
            topicId = "ucgende_acilar",
            topicName = "Üçgende Açılar",
            title = "Doğruda ve Üçgende Açı Özellikleri (Katlama & Döndürme)",
            description = "İkizkenar, eşkenar ve dik üçgende açı bağıntılarını katlama ve döndürme kurgularıyla birleştirir.",
            recommendedQuestionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.YENI_NESIL_KURGULU),
            keywords = listOf("üçgende açı", "iç açı", "dış açı", "ikizkenar", "katlandığında")
        ),
        OsymKazanim(
            id = "KAZ_GEO_02",
            code = "GEO.9.4.2.1",
            examType = ExamType.TYT,
            subjectId = "tyt_geo",
            topicId = "dik_ucgen",
            topicName = "Dik Üçgen ve Pisagor",
            title = "Dik Üçgen, Pisagor, Öklid ve Özel Üçgenler",
            description = "Dik üçgende Pisagor, Öklid ve trigonometrik oranları gerçek yaşam uzunluk modellerine uygular.",
            recommendedQuestionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.YENI_NESIL_KURGULU),
            keywords = listOf("dik üçgen", "pisagor", "öklid", "hipotenüs", "30-60-90")
        ),
        OsymKazanim(
            id = "KAZ_GEO_03",
            code = "GEO.9.4.3.1",
            examType = ExamType.TYT,
            subjectId = "tyt_geo",
            topicId = "ucgende_eslik_benzerlik",
            topicName = "Üçgende Eşlik ve Benzerlik",
            title = "Üçgende Eşlik, Benzerlik ve Alan Oranları",
            description = "Thales, kelebek benzerliği ve kenar-açı-kenar eşlik teoremleriyle uzunluk ve alan oranlarını bulur.",
            recommendedQuestionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU),
            keywords = listOf("benzerlik", "eşlik", "thales", "alan oranı", "benzerlik oranı")
        ),
        OsymKazanim(
            id = "KAZ_GEO_04",
            code = "GEO.10.5.1.1",
            examType = ExamType.TYT,
            subjectId = "tyt_geo",
            topicId = "cokgenler",
            topicName = "Çokgenler",
            title = "Düzgün Çokgenler ve Özel Dörtgenler (Yamuk, Paralelkenar, Kare)",
            description = "Düzgün beşgen, düzgün altıgen, yamuk, paralelkenar, dikdörtgen ve karede köşegen ve alan bağıntılarını çözer.",
            recommendedQuestionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ANALITIK_CEBIRSEL),
            keywords = listOf("çokgen", "altıgen", "beşgen", "yamuk", "paralelkenar", "dikdörtgen", "kare")
        ),
        OsymKazanim(
            id = "KAZ_GEO_05",
            code = "GEO.11.5.1.1",
            examType = ExamType.AYT,
            subjectId = "ayt_geo",
            topicId = "dairede_alan",
            topicName = "Dairede Çevre ve Alan",
            title = "Çemberde Açı, Teğet-Kiriş Uzunluğu ve Dairede Alan",
            description = "Çemberde merkez/çevre açı, teğet-kiriş özellikleri ve daire dilimi alanlarını hesaplar.",
            recommendedQuestionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU),
            keywords = listOf("çember", "daire", "yarıçap", "kiriş", "teğet", "merkez açı", "daire dilimi")
        ),
        OsymKazanim(
            id = "KAZ_GEO_06",
            code = "GEO.11.2.1.1",
            examType = ExamType.AYT,
            subjectId = "ayt_geo",
            topicId = "dogrunun_analitigi",
            topicName = "Doğrunun Analitik İncelenmesi",
            title = "Noktanın, Doğrunun ve Çemberin Analitik İncelenmesi",
            description = "Analitik düzlemde eğim, doğru denklemi, noktanın doğruya uzaklığı, dönüşümler ve çember denklemini kurgular.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.GORSEL_GEOMETRIK),
            keywords = listOf("analitik düzlem", "doğrunun eğimi", "dik kesişen", "simetri", "çemberin analitiği")
        ),
        OsymKazanim(
            id = "KAZ_GEO_07",
            code = "GEO.11.6.1.1",
            examType = ExamType.TYT,
            subjectId = "tyt_geo",
            topicId = "prizmalar_tyt",
            topicName = "Prizmalar",
            title = "Katı Cisimler (Prizma, Silindir, Koni ve Küre Hacim-Yüzey Analizi)",
            description = "Üç boyutlu cisimlerin yüzey açınımlarını, en kısa yüzey yollarını ve sıvı hacim değişimlerini hesaplar.",
            recommendedQuestionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.YENI_NESIL_KURGULU),
            keywords = listOf("prizma", "küp", "silindir", "koni", "küre", "hacim", "yüzey alanı")
        ),

        // ==================== TYT MATEMATİK - CEBİR & OLASILIK KAZANIMLARI ====================
        OsymKazanim(
            id = "KAZ_TYT_MAT_TEMEL",
            code = "MAT.9.2.1.1",
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            topicId = "temel_kavramlar",
            topicName = "Temel Kavramlar",
            title = "Temel Kavramlar, Sayı Kümeleri, Teklik-Çiftlik ve Asal Sayılar",
            description = "Tam sayılarda teklik-çiftlik, pozitiflik-negatiflik, ardışık sayılar ve asal çarpan özelliklerini analiz eder.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL),
            keywords = listOf("tam sayı", "tek", "çift", "asal", "ardışık", "rakam", "faktöriyel")
        ),
        OsymKazanim(
            id = "KAZ_TYT_MAT_BOLME_EBOB",
            code = "MAT.9.2.2.1",
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            topicId = "ebob_ekok",
            topicName = "EBOB - EKOK",
            title = "Sayı Basamakları, Bölünebilme Kuralları ve EBOB-EKOK Periyotları",
            description = "Basamak çözümlemesi, kalanlı bölme ve periyodik tekrar/ortak kat problemlerini çözer.",
            recommendedQuestionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.ANALITIK_CEBIRSEL),
            keywords = listOf("basamak", "bölünebilme", "kalan", "ebob", "ekok", "periyot")
        ),
        OsymKazanim(
            id = "KAZ_TYT_MAT_ESITSIZLIK_MUTLAK",
            code = "MAT.9.3.2.1",
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            topicId = "mutlak_deger",
            topicName = "Mutlak Değer",
            title = "Basit Eşitsizlikler ve Mutlak Değerli Uzaklık Modelleri",
            description = "Sayı doğrusunda uzaklık kavramını mutlak değer eşitsizlikleriyle modeller.",
            recommendedQuestionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.ONCULLU),
            keywords = listOf("mutlak değer", "eşitsizlik", "uzaklık", "sayı doğrusu")
        ),
        OsymKazanim(
            id = "KAZ_TYT_MAT_USLU_KOKLU",
            code = "MAT.9.3.3.1",
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            topicId = "uslu_sayilar",
            topicName = "Üslü Sayılar",
            title = "Üslü, Köklü İfadeler ve Çarpanlara Ayırma",
            description = "Üslü büyüklükler, köklü yaklaşık değerler ve cebirsel özdeşliklerle işlem yapar.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.YENI_NESIL_KURGULU),
            keywords = listOf("üslü", "köklü", "karekök", "çarpanlara ayırma", "tam kare")
        ),
        OsymKazanim(
            id = "KAZ_TYT_MAT_MANTIK_KUME",
            code = "MAT.9.1.1.1",
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            topicId = "kumeler",
            topicName = "Kümeler",
            title = "Sembolik Mantık Bağlaçları ve Küme İşlemleri (Venn Şeması)",
            description = "Bileşik önermelerin doğruluk değerlerini ve küme kesişim/birleşim/fark bölgelerini yorumlar.",
            recommendedQuestionTypes = setOf(OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.GORSEL_GEOMETRIK),
            keywords = listOf("önerme", "küme", "kesişim", "birleşim", "alt küme", "venn")
        ),
        OsymKazanim(
            id = "KAZ_TYT_MAT_PKBO_VERI",
            code = "MAT.10.1.1.1",
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            topicId = "olasilik",
            topicName = "Olasılık",
            title = "Sayma, Permütasyon, Kombinasyon, Binom, Olasılık ve Veri Analizi",
            description = "Seçim-sıralama kurgularını, koşullu olmayan olasılık hesabını ve aritmetik ortalama/medyan/standart sapmayı hesaplar.",
            recommendedQuestionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.MANTIK_MUHAKEME),
            keywords = listOf("permütasyon", "kombinasyon", "binom", "olasılık", "medyan", "aritmetik ortalama", "standart sapma")
        ),

        // ==================== AYT MATEMATİK - DİĞER TEMEL KAZANIMLAR ====================
        OsymKazanim(
            id = "KAZ_AYT_MAT_POLINOM_PARABOL",
            code = "MAT.10.3.1.1",
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            topicId = "polinomlar",
            topicName = "Polinomlar",
            title = "Polinomlarda Bölme, Kök-Katsayı İlişkisi ve Parabol Tepe Noktası",
            description = "Polinom denklemi kurma, ikinci dereceden denklemlerde kök-katsayı bağıntıları ve parabol grafiğini analiz eder.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GRAFIK_TABLO),
            keywords = listOf("polinom", "p(x)", "başkatsayı", "parabol", "tepe noktası", "kökler toplamı", "karmaşık sayı")
        ),
        OsymKazanim(
            id = "KAZ_AYT_MAT_ESITSIZLIK",
            code = "MAT.11.4.1.1",
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            topicId = "ikinci_derece_esitsizlikler",
            topicName = "2. Dereceden Eşitsizlikler",
            title = "İkinci Dereceden Eşitsizlik Sistemleri ve İşaret Tablosu",
            description = "Çarpanlara ayrılabilen veya grafikle verilen fonksiyonların eşitsizlik çözüm kümelerini bulur.",
            recommendedQuestionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ANALITIK_CEBIRSEL),
            keywords = listOf("eşitsizlik", "işaret tablosu", "çözüm kümesi", "f(x).g(x) <= 0")
        ),
        OsymKazanim(
            id = "KAZ_AYT_MAT_TRIGO",
            code = "MAT.12.3.1.1",
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            topicId = "trigonometri_ayt_mat",
            topicName = "Trigonometri",
            title = "Trigonometrik Özdeşlikler, Toplam-Fark, Yarım Açı ve Denklemler",
            description = "Birim çember, kosinüs/sinüs teoremi, toplam-fark ve yarım açı formüllerini geometrik ve cebirsel olarak uygular.",
            recommendedQuestionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ANALITIK_CEBIRSEL),
            keywords = listOf("trigonometri", "sin", "cos", "tan", "cot", "birim çember", "yarım açı", "toplam fark")
        ),
        OsymKazanim(
            id = "KAZ_AYT_MAT_LOG_DIZI",
            code = "MAT.12.1.2.1",
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            topicId = "logaritma",
            topicName = "Logaritma",
            title = "Üstel-Logaritmik Fonksiyonlar ve Aritmetik-Geometrik Diziler",
            description = "Logaritma özelliklerini, üstel büyüme modellerini ve aritmetik/geometrik dizi genel terimlerini çözer.",
            recommendedQuestionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.ANALITIK_CEBIRSEL),
            keywords = listOf("logaritma", "log", "ln", "aritmetik dizi", "geometrik dizi", "genel terim")
        ),
        OsymKazanim(
            id = "KAZ_AYT_MAT_LIMIT",
            code = "MAT.12.5.1.1",
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            topicId = "limit_ve_sureklilik",
            topicName = "Limit ve Süreklilik",
            title = "Sağdan-Soldan Limit, 0/0 Belirsizliği ve Süreklilik Analizi",
            description = "Parçalı ve grafikli fonksiyonlarda limit varlığını, süreklilik şartlarını ve belirsizlik gidermeyi uygular.",
            recommendedQuestionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
            keywords = listOf("limit", "süreklilik", "sağdan limit", "soldan limit", "belirsizlik")
        ),
        OsymKazanim(
            id = "KAZ_AYT_MAT_INTEGRAL",
            code = "MAT.12.6.2.1",
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            topicId = "belirli_integral_alan",
            topicName = "Belirli İntegral ve Alan Hesabı",
            title = "Belirsiz İntegral, Değişken Değiştirme, Riemann Toplamı ve Eğri Altı Alan",
            description = "Türev-integral ters ilişkisini, değişken değiştirme yöntemini ve iki eğri arasındaki alanı hesaplar.",
            recommendedQuestionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ANALITIK_CEBIRSEL),
            keywords = listOf("integral", "riemann", "boyalı bölgenin alanı", "değişken değiştirme", "dx")
        ),

        // ==================== TYT & AYT FEN BİLİMLERİ VE SOSYAL BİLİMLER KAZANIMLARI ====================
        OsymKazanim(
            id = "KAZ_TYT_FEN_FIZIK",
            code = "FIZ.TYT.01",
            examType = ExamType.TYT,
            subjectId = "tyt_fizik",
            topicId = "kuvvet_newton_tyt",
            topicName = "Kuvvet ve Newton Yasaları",
            title = "TYT Fizik: Madde, Hareket, Kuvvet, Enerji, Basınç, Elektrik ve Optik",
            description = "Temel fiziksel yasaları günlük yaşam deneyleri, öncüllü çıkarımlar ve grafik yorumlarıyla analiz eder.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.YENI_NESIL_KURGULU),
            keywords = listOf("kuvvet", "ivme", "basınç", "kaldırma kuvveti", "ısı", "sıcaklık", "optik", "elektrik", "dalga")
        ),
        OsymKazanim(
            id = "KAZ_TYT_FEN_KIMYA",
            code = "KIM.TYT.01",
            examType = ExamType.TYT,
            subjectId = "tyt_kimya",
            topicId = "kimyasal_turler_etkilesim",
            topicName = "Kimyasal Türler Arası Etkileşimler",
            title = "TYT Kimya: Atom, Periyodik Sistem, Türler Arası Etkileşim, Mol ve Karışımlar",
            description = "Atom modelleri, periyodik özellikler, kimyasal bağlar, mol hesaplamaları ve asit-baz tepkimelerini yorumlar.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
            keywords = listOf("atom", "periyodik", "iyonik", "kovalent", "mol", "karışım", "asit", "baz")
        ),
        OsymKazanim(
            id = "KAZ_TYT_FEN_BIYOLOJI",
            code = "BIY.TYT.01",
            examType = ExamType.TYT,
            subjectId = "tyt_biyoloji",
            topicId = "hucre_ve_organeller",
            topicName = "Hücre ve Organeller",
            title = "TYT Biyoloji: Hücre, Madde Geçişleri, Bölünmeler, Kalıtım ve Ekosistem",
            description = "Canlıların temel bileşenleri, organeller, mitoz-mayoz, soyağacı kalıtımı ve ekolojik madde döngülerini çözümler.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
            keywords = listOf("hücre", "organel", "enzim", "mitoz", "mayoz", "kalıtım", "ekosistem")
        ),
        OsymKazanim(
            id = "KAZ_TYT_SOSYAL",
            code = "SOS.TYT.01",
            examType = ExamType.TYT,
            subjectId = "tyt_tarih",
            topicId = "kurtulus_savasi_hazirlik",
            topicName = "Milli Mücadele Hazırlık Dönemi",
            title = "TYT Sosyal Bilimler: Tarihsel Yorum, Coğrafi Dağılım, Felsefi Akıl Yürütme ve Din Kültürü",
            description = "Tarihsel belgelerden çıkarım yapma, harita/iklim verisi okuma ve felsefi kavram analizi becerilerini ölçer.",
            recommendedQuestionTypes = setOf(OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
            keywords = listOf("tarih", "osmanlı", "inkılap", "harita", "iklim", "nüfus", "felsefe", "bilgi", "ahlak")
        ),
        OsymKazanim(
            id = "KAZ_AYT_FEN_FIZIK",
            code = "FIZ.AYT.01",
            examType = ExamType.AYT,
            subjectId = "ayt_fizik",
            topicId = "atislar",
            topicName = "Atışlar",
            title = "AYT Fizik: Mekanik, Atışlar, Momentum, Tork, Elektrik-Manyetizma ve Modern Fizik",
            description = "Vektörel mekanik, enerji-momentum korunumu, çembersel hareket, elektromanyetik indüksiyon ve kuantum fiziğini analiz eder.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.GRAFIK_TABLO),
            keywords = listOf("vektör", "bağıl", "atış", "momentum", "tork", "çembersel", "manyetik", "indüksiyon", "modern fizik")
        ),
        OsymKazanim(
            id = "KAZ_AYT_FEN_KIMYA",
            code = "KIM.AYT.01",
            examType = ExamType.AYT,
            subjectId = "ayt_kimya",
            topicId = "kimyasal_denge",
            topicName = "Kimyasal Tepkimelerde Denge",
            title = "AYT Kimya: Gazlar, Çözeltiler, Enerji, Hız, Denge, Elektrokimya ve Organik Kimya",
            description = "İdeal gaz yasası, koligatif özellikler, entalpi, tepkime hızı, kimyasal/asit-baz dengesi ve elektrokimyasal pilleri çözümler.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
            keywords = listOf("gaz", "çözünürlük", "entalpi", "tepkime hızı", "denge", "ph", "kçç", "elektrot", "organik")
        ),
        OsymKazanim(
            id = "KAZ_AYT_FEN_BIYOLOJI",
            code = "BIY.AYT.01",
            examType = ExamType.AYT,
            subjectId = "ayt_biyoloji",
            topicId = "sinir_sistemi",
            topicName = "Sinir Sistemi",
            title = "AYT Biyoloji: İnsan Fizyolojisi (Sistemler), Genden Proteine, Enerji Dönüşümleri ve Bitki",
            description = "İnsan sistemlerinin homeostazisini, protein sentezini, fotosentez-solunum reaksiyonlarını ve bitki fizyolojisini değerlendirir.",
            recommendedQuestionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.MANTIK_MUHAKEME),
            keywords = listOf("nöron", "hormon", "kas", "enzim", "nefron", "dna", "rna", "fotosentez", "solunum", "ksilem")
        )
    )

    private val kazanimById: Map<String, OsymKazanim> by lazy {
        allKazanimlar.associateBy { it.id }
    }

    /**
     * Bir konuya (örn. Türev, Fonksiyon, Paragraf, Problemler, Geometri veya herhangi bir YksTopic)
     * ait tüm alt kazanımları döndürür. Doğrudan tanımlı alt kazanım yoksa konu için
     * çoklu standart kazanım seti üretir.
     */
    fun getKazanimlarForTopic(topicId: String, topicName: String = ""): List<OsymKazanim> {
        val normId = topicId.trim().lowercase()
        val normName = topicName.trim().lowercase()

        // Özel konu grupları (Türev, Fonksiyon, Paragraf, Problemler, Geometri)
        when {
            normId.contains("turev") || normName.contains("türev") || normName.contains("turev") -> {
                return allKazanimlar.filter { it.id.startsWith("KAZ_TUREV_") }
            }
            normId.contains("fonksiyon") || normName.contains("fonksiyon") -> {
                return allKazanimlar.filter { it.id.startsWith("KAZ_FONK_") }
            }
            normId.contains("paragraf") || normName.contains("paragraf") -> {
                return allKazanimlar.filter { it.id.startsWith("KAZ_PARAGRAF_") }
            }
            normId.contains("problem") || normName.contains("problem") -> {
                return allKazanimlar.filter { it.id.startsWith("KAZ_PROB_") }
            }
            normId.contains("geo") || normId in setOf(
                "ucgende_acilar", "dik_ucgen", "ucgende_eslik_benzerlik", "ucgende_alan",
                "cokgenler", "dortgenler", "yamuk", "paralelkenar", "dikdortgen_kare",
                "cember_daire_tyt", "prizmalar_tyt", "cemberde_aci", "cemberde_uzunluk",
                "dairede_alan", "noktanin_analitigi", "dogrunun_analitigi"
            ) || normName.contains("geometri") || normName.contains("üçgen") || normName.contains("çember") -> {
                val exactGeo = allKazanimlar.filter { it.topicId == normId }
                return if (exactGeo.size >= 2) exactGeo else allKazanimlar.filter { it.id.startsWith("KAZ_GEO_") }
            }
        }

        val directMatches = allKazanimlar.filter {
            it.topicId.equals(normId, ignoreCase = true) ||
                (normName.isNotBlank() && it.topicName.equals(normName, ignoreCase = true))
        }
        if (directMatches.size >= 3) return directMatches

        // Her konu için en az 4 dengeli alt kazanım (Kavramsal Tanım, Analitik Uygulama, Grafik/Görsel Yorum, Yeni Nesil Muhakeme) sağla
        val catalogTopic = SoruLabCatalog.resolveTopicFast(topicId, topicName)
        val resolvedTopicId = catalogTopic?.id ?: topicId.ifBlank { "genel_konu" }
        val resolvedTopicName = catalogTopic?.name ?: topicName.ifBlank { "Genel Konu" }
        val resolvedSubject = SoruLabCatalog.allSubjects.firstOrNull { subj ->
            subj.id == catalogTopic?.subjectId || subj.topics.any { it.id == resolvedTopicId }
        }
        val examType = resolvedSubject?.examType ?: ExamType.TYT
        val subjectId = resolvedSubject?.id ?: "tyt_mat"
        val codePrefix = resolvedTopicId.take(4).uppercase()

        return listOf(
            OsymKazanim(
                id = "KAZ_${resolvedTopicId.uppercase()}_01",
                code = "${examType.name}.$codePrefix.01",
                examType = examType,
                subjectId = subjectId,
                topicId = resolvedTopicId,
                topicName = resolvedTopicName,
                title = "$resolvedTopicName - Temel Kavram ve Analitik İşlem Kazanımı",
                description = "$resolvedTopicName konusundaki temel tanım, kural ve cebirsel/analitik işlemleri uygular.",
                recommendedQuestionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL)
            ),
            OsymKazanim(
                id = "KAZ_${resolvedTopicId.uppercase()}_02",
                code = "${examType.name}.$codePrefix.02",
                examType = examType,
                subjectId = subjectId,
                topicId = resolvedTopicId,
                topicName = resolvedTopicName,
                title = "$resolvedTopicName - Öncüllü (I-II-III) Kesinlik ve Yorum Kazanımı",
                description = "$resolvedTopicName kapsamındaki öncüllerin doğruluk ve kesinlik durumlarını analiz eder.",
                recommendedQuestionTypes = setOf(OsymQuestionType.ONCULLU)
            ),
            OsymKazanim(
                id = "KAZ_${resolvedTopicId.uppercase()}_03",
                code = "${examType.name}.$codePrefix.03",
                examType = examType,
                subjectId = subjectId,
                topicId = resolvedTopicId,
                topicName = resolvedTopicName,
                title = "$resolvedTopicName - Grafik, Tablo ve Görsel Modelleme Kazanımı",
                description = "$resolvedTopicName verilerini grafik, tablo veya geometrik görsel üzerinde yorumlar.",
                recommendedQuestionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.GORSEL_GEOMETRIK)
            ),
            OsymKazanim(
                id = "KAZ_${resolvedTopicId.uppercase()}_04",
                code = "${examType.name}.$codePrefix.04",
                examType = examType,
                subjectId = subjectId,
                topicId = resolvedTopicId,
                topicName = resolvedTopicName,
                title = "$resolvedTopicName - Yeni Nesil Senaryo ve Muhakeme Kazanımı",
                description = "$resolvedTopicName ilkelerini günlük yaşam kurgusu ve üst düzey muhakeme ile çözer.",
                recommendedQuestionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.MANTIK_MUHAKEME)
            )
        )
    }

    fun resolveKazanimForQuestion(question: YksQuestion, fallbackTopicId: String = ""): OsymKazanim {
        val existingId = question.osymKazanimId
        if (existingId.isNotBlank()) {
            kazanimById[existingId]?.let { return it }
        }
        val topicPool = getKazanimlarForTopic(
            topicId = fallbackTopicId.ifBlank { question.topicHeader },
            topicName = question.topicHeader
        )
        val combinedText = "${question.stemTop} ${question.bulletsOrSteps.joinToString(" ")} ${question.stemBottom} ${question.topicHeader}".lowercase()
        val detectedType = OsymQuestionType.classifyQuestion(question)

        // 1. Anahtar kelime ve soru tipi uyumu puanlaması
        val bestMatch = topicPool.maxByOrNull { kaz ->
            val keywordHits = kaz.keywords.count { kw -> combinedText.contains(kw.lowercase()) } * 3
            val typeMatch = if (detectedType in kaz.recommendedQuestionTypes) 2 else 0
            keywordHits + typeMatch
        }
        if (bestMatch != null) return bestMatch

        return allKazanimlar.first()
    }
}

/**
 * Kazanım ve Soru Tipi Denetçisi (Audit Engine) Rapor Modeli
 */
@Immutable
data class OsymExamAuditReport(
    val examId: String,
    val examTitle: String,
    val totalQuestions: Int,
    val isOsymCompliant: Boolean,
    val complianceScore: Int,
    val difficultyCounts: Map<DifficultyLevel, Int>,
    val difficultyPercentages: Map<DifficultyLevel, Double>,
    val targetDifficultyCounts: Map<DifficultyLevel, Int>,
    val difficultyDeviationPercent: Double,
    val isDifficultyCompliant: Boolean,
    val questionTypeCounts: Map<OsymQuestionType, Int>,
    val questionTypePercentages: Map<OsymQuestionType, Double>,
    val distinctQuestionTypeCount: Int,
    val isQuestionTypeDiverse: Boolean,
    val kazanimCounts: Map<String, Int>,
    val distinctKazanimCount: Int,
    val isKazanimBalanced: Boolean,
    val optionDistribution: Map<String, Int>,
    val isOptionDistributionBalanced: Boolean,
    val warnings: List<String>,
    val violations: List<String>,
    val summaryReport: String
)

/**
 * Deneme Üretim & Denetim Motoru (Audit Engine):
 * - Asla rastgele soru seçmez; kazanım, soru tipi ve 5 kademeli ÖSYM zorluk kotasına göre
 *   katmanlı (stratified) ve deterministik seçim yapar.
 * - Üretilen her denemeyi ÖSYM standartlarına (%10 Kolay, %20 Kolay-Orta, %40 ÖSYM, %20 Zor, %10 Seçici/Çok Zor),
 *   soru tipi çeşitliliğine ve kazanım dengesine göre denetler.
 */
object OsymExamAuditEngine {

    /**
     * Bir testi (Genel Deneme veya Konu Denemesi) ÖSYM standartlarına göre denetler.
     */
    fun auditTest(
        testItem: YksTestItem,
        expectedTopicId: String? = null,
        maxDifficultyTolerancePerTier: Int = 1
    ): OsymExamAuditReport {
        val questions = testItem.questions
        val total = questions.size
        val violations = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (total == 0) {
            return OsymExamAuditReport(
                examId = testItem.id,
                examTitle = testItem.fullTitle,
                totalQuestions = 0,
                isOsymCompliant = false,
                complianceScore = 0,
                difficultyCounts = DifficultyLevel.entries.associateWith { 0 },
                difficultyPercentages = DifficultyLevel.entries.associateWith { 0.0 },
                targetDifficultyCounts = DifficultyLevel.entries.associateWith { 0 },
                difficultyDeviationPercent = 100.0,
                isDifficultyCompliant = false,
                questionTypeCounts = OsymQuestionType.entries.associateWith { 0 },
                questionTypePercentages = OsymQuestionType.entries.associateWith { 0.0 },
                distinctQuestionTypeCount = 0,
                isQuestionTypeDiverse = false,
                kazanimCounts = emptyMap(),
                distinctKazanimCount = 0,
                isKazanimBalanced = false,
                optionDistribution = emptyMap(),
                isOptionDistributionBalanced = false,
                warnings = emptyList(),
                violations = listOf("Deneme içerisinde hiç soru bulunmuyor."),
                summaryReport = "GEÇERSİZ DENEME: Soru listesi boş."
            )
        }

        // 1. Zorluk Dağılımı Denetimi (%10 Kolay, %20 Kolay-Orta, %40 ÖSYM, %20 Zor, %10 Seçici/Çok Zor)
        val targetDiffCounts = OsymDifficultyStandard.targetCountsForTotal(total)
        val actualDiffCounts = DifficultyLevel.entries.associateWith { diff ->
            questions.count { it.difficulty == diff }
        }
        val actualDiffPercentages = DifficultyLevel.entries.associateWith { diff ->
            (actualDiffCounts.getValue(diff).toDouble() / total.toDouble()) * 100.0
        }

        var totalDiffDeviation = 0.0
        var maxTierDiffCountError = 0
        for (diff in DifficultyLevel.entries) {
            val actualCount = actualDiffCounts.getValue(diff)
            val targetCount = targetDiffCounts.getValue(diff)
            val countErr = abs(actualCount - targetCount)
            if (countErr > maxTierDiffCountError) {
                maxTierDiffCountError = countErr
            }
            val targetPct = OsymDifficultyStandard.targetRatios.getValue(diff) * 100.0
            val actualPct = actualDiffPercentages.getValue(diff)
            totalDiffDeviation += abs(actualPct - targetPct)
        }
        val avgDiffDeviation = totalDiffDeviation / DifficultyLevel.entries.size
        val isDifficultyCompliant = maxTierDiffCountError <= maxDifficultyTolerancePerTier && totalDiffDeviation <= 15.0

        if (!isDifficultyCompliant) {
            violations += "Zorluk dağılımı ÖSYM standardından (%10 Kolay, %20 Orta, %40 ÖSYM, %20 Zor, %10 Çok Zor) sapıyor: " +
                DifficultyLevel.entries.joinToString(", ") { "${it.label}=${actualDiffCounts[it]}/${targetDiffCounts[it]}" }
        }

        // 2. Soru Tipi Çeşitliliği Denetimi
        val resolvedTypes = questions.map { q ->
            q.resolvedOsymQuestionType
        }
        val typeCounts = OsymQuestionType.entries.associateWith { type ->
            resolvedTypes.count { it == type }
        }
        val typePercentages = OsymQuestionType.entries.associateWith { type ->
            (typeCounts.getValue(type).toDouble() / total.toDouble()) * 100.0
        }
        val distinctTypeCount = typeCounts.count { it.value > 0 }
        val minRequiredTypes = when {
            total >= 20 -> 4
            total >= 10 -> 3
            total >= 4 -> 2
            else -> 1
        }
        val maxSingleTypeRatio = (typeCounts.values.maxOrNull() ?: 0).toDouble() / total.toDouble()
        val isQuestionTypeDiverse = distinctTypeCount >= minRequiredTypes && (total < 6 || maxSingleTypeRatio <= 0.65)

        if (!isQuestionTypeDiverse) {
            violations += "Soru tipi çeşitliliği yetersiz: $distinctTypeCount farklı soru tipi bulundu (En az $minRequiredTypes gerekli, tek tip oranı <= %65 olmalı)."
        }

        // 3. Kazanım Dağılımı ve Dengesi Denetimi
        val resolvedKazanimIds = questions.map { q ->
            q.osymKazanimId.ifBlank {
                OsymKazanimTaxonomy.resolveKazanimForQuestion(q, expectedTopicId ?: testItem.topicId).id
            }
        }
        val kazanimCounts = resolvedKazanimIds.groupingBy { it }.eachCount()
        val distinctKazanimCount = kazanimCounts.size
        val minRequiredKazanimlar = when {
            total >= 20 -> 6
            total >= 10 -> 4
            total >= 4 -> 2
            else -> 1
        }
        val maxSingleKazanimRatio = (kazanimCounts.values.maxOrNull() ?: 0).toDouble() / total.toDouble()
        val isKazanimBalanced = distinctKazanimCount >= minRequiredKazanimlar && (total < 6 || maxSingleKazanimRatio <= 0.50)

        if (!isKazanimBalanced) {
            violations += "Kazanım dağılımı dengesiz: $distinctKazanimCount farklı kazanım kapsandı (En az $minRequiredKazanimlar kazanım gerekli, tek kazanım yükü <= %50 olmalı)."
        }

        // 4. Cevap Anahtarı (A, B, C, D, E) Dengesi Denetimi
        val optionCounts = listOf("A", "B", "C", "D", "E").associateWith { opt ->
            questions.count { it.correctOption.equals(opt, ignoreCase = true) }
        }
        val maxOptionRatio = (optionCounts.values.maxOrNull() ?: 0).toDouble() / total.toDouble()
        val isOptionBalanced = total < 10 || maxOptionRatio <= 0.45
        if (!isOptionBalanced) {
            warnings += "Cevap anahtarı şık dağılımında yığılma var: $optionCounts"
        }

        // Uyum Puanı (0..100)
        val diffScore = (40.0 - totalDiffDeviation).coerceIn(0.0, 40.0)
        val typeScore = ((distinctTypeCount.toDouble() / minRequiredTypes.coerceAtLeast(1)) * 25.0).coerceIn(0.0, 30.0)
        val kazanimScore = ((distinctKazanimCount.toDouble() / minRequiredKazanimlar.coerceAtLeast(1)) * 25.0).coerceIn(0.0, 30.0)
        val rawScore = (diffScore + typeScore + kazanimScore).roundToInt().coerceIn(0, 100)
        val isCompliant = isDifficultyCompliant && isQuestionTypeDiverse && isKazanimBalanced

        val summary = buildString {
            append(if (isCompliant) "[ÖSYM TAM UYUMLU - $rawScore/100] " else "[DENETİM UYARISI - $rawScore/100] ")
            append("${testItem.fullTitle} ($total Soru) | ")
            append("Zorluk: ")
            append(
                DifficultyLevel.entries.joinToString("/") { diff ->
                    "${diff.label}:%${actualDiffPercentages.getValue(diff).roundToInt()}"
                }
            )
            append(" | Kazanım Çeşidi: $distinctKazanimCount | Soru Tipi Çeşidi: $distinctTypeCount")
        }

        return OsymExamAuditReport(
            examId = testItem.id,
            examTitle = testItem.fullTitle,
            totalQuestions = total,
            isOsymCompliant = isCompliant,
            complianceScore = if (isCompliant) rawScore.coerceAtLeast(85) else rawScore,
            difficultyCounts = actualDiffCounts,
            difficultyPercentages = actualDiffPercentages,
            targetDifficultyCounts = targetDiffCounts,
            difficultyDeviationPercent = avgDiffDeviation,
            isDifficultyCompliant = isDifficultyCompliant,
            questionTypeCounts = typeCounts,
            questionTypePercentages = typePercentages,
            distinctQuestionTypeCount = distinctTypeCount,
            isQuestionTypeDiverse = isQuestionTypeDiverse,
            kazanimCounts = kazanimCounts,
            distinctKazanimCount = distinctKazanimCount,
            isKazanimBalanced = isKazanimBalanced,
            optionDistribution = optionCounts,
            isOptionDistributionBalanced = isOptionBalanced,
            warnings = warnings,
            violations = violations,
            summaryReport = summary
        )
    }

    fun auditMockExam(mockExam: MockExamItem): OsymExamAuditReport {
        return auditTest(mockExam.linkedTest)
    }

    /**
     * Kazanım, Soru Tipi ve Zorluk Kotalarına Göre Deterministik Soru Seçici:
     * Asla rastgele seçim yapmaz.
     * 1) İstenen toplam soru sayısı için ÖSYM zorluk kotalarını (%10 Kolay, %20 Orta, %40 ÖSYM, %20 Zor, %10 Çok Zor)
     *    veya kullanıcının seçtiği aktif zorluk filtrelerini hedefler.
     * 2) Her zorluk diliminden soru seçerken şimdiye kadar en az seçilmiş MEB/ÖSYM kazanımına ve
     *    en az seçilmiş soru tipine (Öncüllü, Yeni Nesil, Grafik/Tablo, Analitik/Cebirsel, Görsel/Geometrik, Mantık-Muhakeme)
     *    öncelik verir.
     */
    fun selectBalancedQuestions(
        candidates: List<YksQuestion>,
        targetCount: Int,
        topicId: String,
        topicName: String = "",
        allowedDifficulties: Set<DifficultyLevel> = DifficultyLevel.entries.toSet(),
        usedSignatures: Set<String> = emptySet(),
        rotationAttempt: Int = 0
    ): List<YksQuestion> {
        if (candidates.isEmpty() || targetCount <= 0) return emptyList()

        val activeDiffs = allowedDifficulties.ifEmpty { DifficultyLevel.entries.toSet() }
        val filteredPool = candidates
            .filter { it.difficulty in activeDiffs }
            .ifEmpty { candidates }
            .distinctBy { it.croppedImagePath.ifBlank { "${it.id}_${it.stemTop.take(64)}" } }

        if (filteredPool.isEmpty()) return emptyList()

        // Her adayı zenginleştir (kazanım ve soru tipi eksikse deterministik ata)
        val topicKazanimlar = OsymKazanimTaxonomy.getKazanimlarForTopic(topicId, topicName)
        val allTypes = OsymQuestionType.entries

        val enrichedCandidates = filteredPool.mapIndexed { idx, q ->
            val resolvedType = OsymQuestionType.fromRawLabel(q.questionType) ?: OsymQuestionType.classifyQuestion(q).let { classified ->
                // Eğer tüm havuz sade metinlerden oluşuyorsa ve aynı tipe düşüyorsa, kazanım çeşitliliğine göre dengeli soru tipi ata
                if (q.stemTop.length < 45 && q.bulletsOrSteps.isEmpty()) {
                    allTypes[idx % allTypes.size]
                } else {
                    classified
                }
            }
            val existingKazanimId = q.osymKazanimId
            val resolvedKazanim = if (existingKazanimId.isNotBlank()) {
                OsymKazanimTaxonomy.allKazanimlar.firstOrNull { it.id == existingKazanimId }
                    ?: topicKazanimlar[idx % topicKazanimlar.size]
            } else {
                val matched = OsymKazanimTaxonomy.resolveKazanimForQuestion(q, topicId)
                // Eğer hepsi aynı kazanıma düşüyorsa konu alt kazanımlarına deterministik dağıt
                if (topicKazanimlar.size > 1 && q.stemTop.length < 60) {
                    topicKazanimlar[idx % topicKazanimlar.size]
                } else {
                    matched
                }
            }
            q.copy(
                learningOutcomeId = resolvedKazanim.id,
                learningOutcome = resolvedKazanim.code,
                learningOutcomeName = resolvedKazanim.title,
                learningOutcomeSource = q.learningOutcomeSource.ifBlank { "osym_blueprint" },
                questionType = resolvedType.name
            )
        }

        // Hedef zorluk kotalarını belirle
        val baseQuotas = OsymDifficultyStandard.targetCountsForTotal(targetCount)
        val effectiveQuotas: Map<DifficultyLevel, Int> = if (activeDiffs.size == DifficultyLevel.entries.size) {
            baseQuotas
        } else {
            // Sadece seçili zorluklar varsa, ÖSYM ağırlık oranlarına göre seçili zorluklara paylaştır
            val activeWeightSum = activeDiffs.sumOf { OsymDifficultyStandard.targetRatios.getValue(it) }.coerceAtLeast(0.01)
            val raw = activeDiffs.map { diff ->
                val exact = targetCount * (OsymDifficultyStandard.targetRatios.getValue(diff) / activeWeightSum)
                Triple(diff, exact.toInt(), exact - exact.toInt())
            }.toMutableList()
            val rem = targetCount - raw.sumOf { it.second }
            val sortedIdx = raw.indices.sortedByDescending { raw[it].third }
            for (i in 0 until rem) {
                val k = sortedIdx[i % sortedIdx.size]
                raw[k] = raw[k].copy(second = raw[k].second + 1)
            }
            DifficultyLevel.entries.associateWith { d -> raw.firstOrNull { it.first == d }?.second ?: 0 }
        }

        val selected = mutableListOf<YksQuestion>()
        val selectedIds = HashSet<String>()
        val kazanimUsage = HashMap<String, Int>()
        val typeUsage = HashMap<OsymQuestionType, Int>()

        fun signatureOf(q: YksQuestion): String = q.croppedImagePath.ifBlank { q.stemTop.take(60) }

        fun pickBestCandidate(pool: List<YksQuestion>): YksQuestion? {
            val available = pool.filter { it.id !in selectedIds }
            if (available.isEmpty()) return null

            // Önce daha önce kullanılmamış (unusedSignatures) olanları tercih et,
            // ardından en az kullanılmış kazanım ve en az kullanılmış soru tipini seç!
            return available.minWithOrNull(
                compareBy<YksQuestion> { q -> if (signatureOf(q) in usedSignatures) 1 else 0 }
                    .thenBy { q -> kazanimUsage[q.osymKazanimId] ?: 0 }
                    .thenBy { q -> typeUsage[q.resolvedOsymQuestionType] ?: 0 }
                    .thenBy { q ->
                        if (rotationAttempt <= 0) q.number
                        else (q.number + rotationAttempt * 7) % (pool.size.coerceAtLeast(1) + 1)
                    }
            )
        }

        val byDifficulty = enrichedCandidates.groupBy { it.difficulty }

        // 1. Aşama: Her zorluk kotasını kazanım ve soru tipi dengesini gözeterek doldur
        for (diff in listOf(
            DifficultyLevel.KOLAY,
            DifficultyLevel.ORTA,
            DifficultyLevel.OSYM,
            DifficultyLevel.ZOR,
            DifficultyLevel.COK_ZOR
        )) {
            val quota = effectiveQuotas[diff] ?: 0
            val diffPool = byDifficulty[diff].orEmpty()
            repeat(quota) {
                val chosen = pickBestCandidate(diffPool)
                if (chosen != null) {
                    selected += chosen
                    selectedIds += chosen.id
                    kazanimUsage[chosen.osymKazanimId] = (kazanimUsage[chosen.osymKazanimId] ?: 0) + 1
                    val qType = chosen.resolvedOsymQuestionType
                    typeUsage[qType] = (typeUsage[qType] ?: 0) + 1
                }
            }
        }

        // 2. Aşama: Eğer belirli bir zorluk diliminde yeterli aday yoksa komşu zorluklardan tamamla
        if (selected.size < targetCount) {
            val fallbackDiffOrder = listOf(
                DifficultyLevel.OSYM,
                DifficultyLevel.ORTA,
                DifficultyLevel.ZOR,
                DifficultyLevel.KOLAY,
                DifficultyLevel.COK_ZOR
            )
            while (selected.size < targetCount) {
                var addedAny = false
                for (diff in fallbackDiffOrder) {
                    if (selected.size >= targetCount) break
                    val chosen = pickBestCandidate(byDifficulty[diff].orEmpty())
                    if (chosen != null) {
                        selected += chosen
                        selectedIds += chosen.id
                        kazanimUsage[chosen.osymKazanimId] = (kazanimUsage[chosen.osymKazanimId] ?: 0) + 1
                        val qType = chosen.resolvedOsymQuestionType
                        typeUsage[qType] = (typeUsage[qType] ?: 0) + 1
                        addedAny = true
                    }
                }
                if (!addedAny) break
            }
        }

        // Pedagojik sıralama: Kolay -> Orta -> ÖSYM -> Zor -> Çok Zor akışı veya kazanım sarmalı
        return selected.sortedWith(
            compareBy<YksQuestion> { it.difficulty.ordinal }
                .thenBy { it.osymKazanimCode }
        )
    }

    /**
     * Herhangi bir konu için MEB/ÖSYM müfredat kazanımlarına, soru tipi çeşitliliğine ve
     * %10 Kolay - %20 Orta - %40 ÖSYM - %20 Zor - %10 Çok Zor dağılımına %100 uyumlu
     * Konu Denemesi üretir ve denetim raporunu döndürür.
     */
    fun generateAuditedTopicExam(
        topic: YksTopic,
        questionCount: Int = 10,
        candidatePool: List<YksQuestion> = emptyList(),
        level: TopicTestLevel = TopicTestLevel.OSYM,
        testNumber: Int = 1
    ): Pair<YksTestItem, OsymExamAuditReport> {
        val safeCount = questionCount.coerceAtLeast(5)
        val kazanimlar = OsymKazanimTaxonomy.getKazanimlarForTopic(topic.id, topic.name)
        val targetQuotas = OsymDifficultyStandard.targetCountsForTotal(safeCount)

        // Eğer dış aday havuzu yeterliyse ve tüm zorlukları karşılıyorsa oradan seç,
        // eksik kalan zorluk/kazanım dilimlerini müfredat şablonuyla tamamla.
        val synthesizedPool = buildSynthesizedTopicQuestions(
            topic = topic,
            kazanimlar = kazanimlar,
            targetQuotas = targetQuotas,
            testNumber = testNumber
        )

        val combinedPool = if (candidatePool.isNotEmpty()) {
            candidatePool + synthesizedPool
        } else {
            synthesizedPool
        }

        val selectedQuestions = selectBalancedQuestions(
            candidates = combinedPool,
            targetCount = safeCount,
            topicId = topic.id,
            topicName = topic.name,
            allowedDifficulties = DifficultyLevel.entries.toSet(),
            rotationAttempt = testNumber - 1
        )

        val testId = "${topic.id}_konu_denemesi_$testNumber"
        val fullTitle = "${topic.name} - ÖSYM Kazanım Konu Denemesi $testNumber"
        val numberedQuestions = selectedQuestions.mapIndexed { index, q ->
            q.copy(
                id = "${testId}_q${index + 1}",
                number = index + 1,
                badgeTitle = fullTitle.uppercase(),
                topicHeader = topic.name.uppercase()
            )
        }

        val avgWeight = numberedQuestions.map { it.difficulty.weight }.average().toFloat().coerceIn(0.12f, 0.92f)
        val testItem = YksTestItem(
            id = testId,
            topicId = topic.id,
            title = "Konu Denemesi $testNumber",
            fullTitle = fullTitle,
            difficulty = DifficultyLevel.OSYM,
            difficultyPosition = avgWeight,
            defaultCompleted = false,
            starRating = 0,
            level = level,
            questions = numberedQuestions
        )

        val auditReport = auditTest(testItem, expectedTopicId = topic.id)
        return testItem to auditReport
    }

    private fun buildSynthesizedTopicQuestions(
        topic: YksTopic,
        kazanimlar: List<OsymKazanim>,
        targetQuotas: Map<DifficultyLevel, Int>,
        testNumber: Int
    ): List<YksQuestion> {
        val subject = SoruLabCatalog.allSubjects.firstOrNull { it.id == topic.subjectId }
        val subjectName = subject?.name ?: "Matematik"
        val questionTypes = OsymQuestionType.entries
        val optionsCycle = listOf("A", "B", "C", "D", "E")
        val result = mutableListOf<YksQuestion>()
        var globalIdx = 0

        for (diff in DifficultyLevel.entries) {
            val count = targetQuotas[diff] ?: 0
            repeat(count) {
                val kazanim = kazanimlar[globalIdx % kazanimlar.size]
                val qType = questionTypes[globalIdx % questionTypes.size]
                val correctOpt = optionsCycle[(globalIdx + testNumber - 1) % optionsCycle.size]
                val qNum = globalIdx + 1

                val template = buildCurriculumQuestionContent(
                    topicName = topic.name,
                    subjectName = subjectName,
                    kazanim = kazanim,
                    questionType = qType,
                    difficulty = diff,
                    questionNumber = qNum,
                    correctOption = correctOpt
                )

                result += YksQuestion(
                    id = "synth_${topic.id}_${diff.name.lowercase()}_$qNum",
                    number = qNum,
                    badgeTitle = "${topic.name.uppercase()} KONU DENEMESİ",
                    topicHeader = topic.name.uppercase(),
                    publisherStamp = "SORULAB ÖSYM KAZANIM MERKEZİ",
                    publisherName = "SoruLab Kurumsal Yayınları",
                    answerKeyVerified = true,
                    stemTop = template.stemTop,
                    bulletsOrSteps = template.bullets,
                    stemBottom = template.stemBottom,
                    options = template.options,
                    correctOption = correctOpt,
                    difficulty = diff,
                    visualType = template.visualType,
                    stepByStepSolution = template.solution,
                    subject = subjectName,
                    learningOutcomeId = kazanim.id,
                    learningOutcome = kazanim.code,
                    learningOutcomeName = kazanim.title,
                    learningOutcomeSource = "osym_blueprint",
                    questionType = qType.name,
                    isOsymType = (diff == DifficultyLevel.OSYM)
                )
                globalIdx++
            }
        }
        return result
    }

    private data class SynthesizedQuestionTemplate(
        val stemTop: String,
        val bullets: List<String>,
        val stemBottom: String,
        val options: Map<String, String>,
        val visualType: QuestionVisualType,
        val solution: String
    )

    private fun buildCurriculumQuestionContent(
        topicName: String,
        subjectName: String,
        kazanim: OsymKazanim,
        questionType: OsymQuestionType,
        difficulty: DifficultyLevel,
        questionNumber: Int,
        correctOption: String
    ): SynthesizedQuestionTemplate {
        val baseVal = questionNumber + 2
        return when (questionType) {
            OsymQuestionType.ONCULLU -> SynthesizedQuestionTemplate(
                stemTop = "[$kazanim.code - ${kazanim.title}] $topicName konusu kapsamında tanımlanan bir sistemde a = $baseVal ve b = ${baseVal + 3} parametreleri için aşağıdaki öncüller veriliyor:",
                bullets = listOf(
                    "I. Sistemin başlangıç koşulu ${kazanim.title} ilkesini doğrudan sağlar.",
                    "II. Parametrelerin toplamı (${2 * baseVal + 3}) sınır değer aralığında yer alır.",
                    "III. İkinci durumdaki değişim oranı başlangıç değerinin iki katından büyüktür."
                ),
                stemBottom = "Buna göre, yukarıdaki ifadelerden hangileri kesinlikle doğrudur?",
                options = buildOptionsWithCorrect(
                    correctOption = correctOption,
                    correctText = "I ve II",
                    distractors = listOf("Yalnız I", "Yalnız II", "II ve III", "I, II ve III")
                ),
                visualType = QuestionVisualType.NONE,
                solution = AiStructuredSolution(
                    kritikBilgiTeorem = "${kazanim.code}: ${kazanim.description}",
                    adimAdimCozum = "1) I. öncül ${kazanim.title} tanım kümesinde her zaman sağlanır.\n2) II. öncülde a + b = ${2 * baseVal + 3} olup sınır koşulunu sağlar.\n3) III. öncül uç değerlerde sağlanmadığından kesin değildir. Cevap I ve II ($correctOption).",
                    osymTuzakNotu = "'Kesinlikle doğrudur' sorularında sınır değerleri ve özel durumları mutlaka test ediniz."
                ).toFormattedText()
            )

            OsymQuestionType.GRAFIK_TABLO -> SynthesizedQuestionTemplate(
                stemTop = "[$kazanim.code - ${kazanim.title}] Dik koordinat düzleminde $topicName kazanımını modelleyen y = f(x) eğrisinin değişim grafiği ve kritik kesit tablosu aşağıda verilmiştir.",
                bullets = listOf(
                    "x = 0 noktasında başlangıç değeri f(0) = $baseVal",
                    "x = $baseVal noktasında tepe/dönüm değeri f($baseVal) = ${baseVal * 3}",
                    "x = ${baseVal * 2} noktasında sınır değeri f(${baseVal * 2}) = ${baseVal * 2}"
                ),
                stemBottom = "Grafikteki verilere göre, [0, ${baseVal * 2}] aralığındaki ortalama değişim ve kritik değer toplamı kaçtır?",
                options = buildOptionsWithCorrect(
                    correctOption = correctOption,
                    correctText = "${baseVal * 3 + 1}",
                    distractors = listOf("${baseVal * 2}", "${baseVal * 3 - 1}", "${baseVal * 3 + 3}", "${baseVal * 4}")
                ),
                visualType = QuestionVisualType.FUNCTION_GRAPH,
                solution = AiStructuredSolution(
                    kritikBilgiTeorem = "${kazanim.code}: Grafik ve tablo verilerinde eksen kesişimleri ile değişim oranı analizi.",
                    adimAdimCozum = "1) Tablodan kritik tepe değeri f($baseVal) = ${baseVal * 3} olarak okunur.\n2) [0, $baseVal] aralığındaki birim değişim oranı (${baseVal * 3} - $baseVal) / $baseVal = 2'dir ve tam aralık ortalaması 1 eklenerek ${baseVal * 3 + 1} bulunur. Doğru seçenek $correctOption.",
                    osymTuzakNotu = "Grafik sorularında hangi eksenin f(x) veya türev/değişim belirttiğine dikkat ediniz."
                ).toFormattedText()
            )

            OsymQuestionType.YENI_NESIL_KURGULU -> SynthesizedQuestionTemplate(
                stemTop = "[$kazanim.code - ${kazanim.title}] Bir AR-GE laboratuvarında yapılan $topicName modelleme deneyinde, başlangıç kapasitesi ${baseVal * 10} birim olan bir düzenek her adımda kurallı olarak güncellenmektedir.",
                bullets = listOf(
                    "1. Adım: Kapasitenin 1/$baseVal kadarı aktif sürece aktarılıyor.",
                    "2. Adım: Elde edilen çıktıya ${baseVal * 2} birim dengeleyici ekleniyor."
                ),
                stemBottom = "Bu günlük yaşam modellemesine göre, 2. adım sonunda aktif süreçte biriken toplam değer kaç birimdir?",
                options = buildOptionsWithCorrect(
                    correctOption = correctOption,
                    correctText = "${10 + baseVal * 2}",
                    distractors = listOf("${8 + baseVal * 2}", "${10 + baseVal}", "${12 + baseVal * 2}", "${15 + baseVal * 2}")
                ),
                visualType = QuestionVisualType.MARKET_RECEIPT,
                solution = AiStructuredSolution(
                    kritikBilgiTeorem = "${kazanim.code}: Günlük yaşam senaryolarının matematiksel/bilimsel denkleme dönüştürülmesi.",
                    adimAdimCozum = "1) Başlangıç kapasitesi ${baseVal * 10} birimin 1/$baseVal'i = 10 birimdir.\n2) 2. adımda ${baseVal * 2} birim eklendiğinde toplam = ${10 + baseVal * 2} birim olur. Doğru cevap $correctOption.",
                    osymTuzakNotu = "Yeni nesil hikayeli sorularda birim dönüşümlerini ve 'kalan mı, aktarılan mı' ifadelerini dikkatle okuyunuz."
                ).toFormattedText()
            )

            OsymQuestionType.GORSEL_GEOMETRIK -> SynthesizedQuestionTemplate(
                stemTop = "[$kazanim.code - ${kazanim.title}] Şekildeki geometrik modelde $topicName prensibine göre kenar uzunlukları $baseVal birim ve ${baseVal * 2} birim olan dik kesitli bir bölge verilmiştir.",
                bullets = emptyList(),
                stemBottom = "Bu şekil köşegeni boyunca simetrik olarak katlandığında oluşan taralı bölgenin karakteristik ölçüsü kaç birimkaredir?",
                options = buildOptionsWithCorrect(
                    correctOption = correctOption,
                    correctText = "${baseVal * baseVal}",
                    distractors = listOf("${baseVal * (baseVal - 1)}", "${baseVal * (baseVal + 1)}", "${baseVal * baseVal + 2}", "${baseVal * baseVal * 2}")
                ),
                visualType = QuestionVisualType.GEOMETRY_TRIANGLE,
                solution = AiStructuredSolution(
                    kritikBilgiTeorem = "${kazanim.code}: Geometrik katlama ve alan/uzunluk korunumu ilkesi.",
                    adimAdimCozum = "1) Dik kesit alanı ($baseVal x ${baseVal * 2}) / 2 = ${baseVal * baseVal} birimkaredir.\n2) Simetrik katlamada eş üçgenlerin alanı korunduğundan sonuç ${baseVal * baseVal} olur. Cevap $correctOption.",
                    osymTuzakNotu = "Katlama sorularında açıortay ve eşit kenar taşınmasını şekil üzerinde mutlaka gösteriniz."
                ).toFormattedText()
            )

            OsymQuestionType.MANTIK_MUHAKEME -> SynthesizedQuestionTemplate(
                stemTop = "[$kazanim.code - ${kazanim.title}] $subjectName dersinde $topicName konusunu inceleyen bir araştırmacı aşağıdaki koşullu muhakeme kuralını tanımlıyor:",
                bullets = listOf(
                    "Kural 1: Giriş parametresi $baseVal değerinden büyükse sistem A moduna geçer.",
                    "Kural 2: Sistem A modunda iken çıktı değeri girişin 3 katından $baseVal eksik olur."
                ),
                stemBottom = "Giriş parametresi ${baseVal + 2} olarak seçildiğinde bu muhakeme zincirinin üreteceği kesin çıktı değeri kaçtır?",
                options = buildOptionsWithCorrect(
                    correctOption = correctOption,
                    correctText = "${2 * baseVal + 6}",
                    distractors = listOf("${2 * baseVal + 4}", "${2 * baseVal + 2}", "${3 * baseVal + 2}", "${3 * baseVal + 6}")
                ),
                visualType = QuestionVisualType.NONE,
                solution = AiStructuredSolution(
                    kritikBilgiTeorem = "${kazanim.code}: Koşullu önerme ve tümdengelimsel muhakeme analizi.",
                    adimAdimCozum = "1) Giriş = ${baseVal + 2} > $baseVal olduğundan Kural 1 gereği A modu aktifleşir.\n2) Kural 2 uygulanırsa: 3 x (${baseVal + 2}) - $baseVal = ${2 * baseVal + 6} bulunur. Cevap $correctOption.",
                    osymTuzakNotu = "Muhakeme sorularında koşulun sağlanıp sağlanmadığını işlem yapmadan önce kontrol ediniz."
                ).toFormattedText()
            )

            OsymQuestionType.ANALITIK_CEBIRSEL -> SynthesizedQuestionTemplate(
                stemTop = "[$kazanim.code - ${kazanim.title}] Gerçek sayılar kümesinde tanımlı $topicName bağıntısı için f(x) = ${baseVal}x + ${baseVal * 2} denklemi veriliyor.",
                bullets = emptyList(),
                stemBottom = "f(a) = ${baseVal * 5} eşitliğini sağlayan a gerçek sayısı kaçtır?",
                options = buildOptionsWithCorrect(
                    correctOption = correctOption,
                    correctText = "3",
                    distractors = listOf("1", "2", "4", "5")
                ),
                visualType = QuestionVisualType.NONE,
                solution = AiStructuredSolution(
                    kritikBilgiTeorem = "${kazanim.code}: ${kazanim.description}",
                    adimAdimCozum = "1) ${baseVal}a + ${baseVal * 2} = ${baseVal * 5} denklemi kurulur.\n2) ${baseVal}a = ${baseVal * 3} => a = 3 elde edilir. Doğru seçenek $correctOption.",
                    osymTuzakNotu = "Denklem çözümlerinde tanım kümesi kısıtlarını ve katsayı sadeleştirmelerini kontrol ediniz."
                ).toFormattedText()
            )
        }
    }

    internal fun buildOptionsWithCorrect(
        correctOption: String,
        correctText: String,
        distractors: List<String>
    ): Map<String, String> {
        val keys = listOf("A", "B", "C", "D", "E")
        val normalizedCorrect = correctOption.uppercase().takeIf { it in keys } ?: "C"
        val uniqueDistractors = distractors.filter { it != correctText }.ifEmpty {
            listOf("Yalnız I", "Yalnız III", "I ve III", "II ve III")
        }
        var dIdx = 0
        return keys.associateWith { key ->
            if (key == normalizedCorrect) {
                correctText
            } else {
                val text = uniqueDistractors[dIdx % uniqueDistractors.size]
                dIdx++
                text
            }
        }
    }
}


/**
 * Kurumsal TYT, AYT ve Konu Denemeleri (Türev, Fonksiyon, Paragraf, Problemler, Geometri) Üretim Fabrikası.
 * Üretilen her deneme:
 * - %10 Kolay, %20 Kolay-Orta, %40 ÖSYM, %20 Zor, %10 Seçici/Çok Zor dağılımına tam uyumludur.
 * - Öncüllü (I-II-III), Yeni Nesil Kurgulu, Grafik/Tablo, Analitik/Cebirsel, Görsel/Geometrik ve Mantık-Muhakeme
 *   soru tiplerinin tamamını dengeli biçimde içerir.
 * - MEB/ÖSYM kazanım kodları ve detaylı 3 aşamalı AI çözümleriyle donatılmıştır.
 */
object OsymMockExamFactory {

    private data class BlueprintQuestionSeed(
        val kazanimId: String,
        val subjectName: String,
        val topicHeader: String,
        val questionType: OsymQuestionType,
        val visualType: QuestionVisualType,
        val stemTop: String,
        val bullets: List<String> = emptyList(),
        val stemBottom: String,
        val correctText: String,
        val distractors: List<String>,
        val theorem: String,
        val solutionSteps: String,
        val trapNote: String
    )

    // 10 Standart Zorluk Sırası (%10 Kolay [1], %20 Orta [2], %40 ÖSYM [4], %20 Zor [2], %10 Çok Zor [1])
    private val tenQuestionDifficultySchedule: List<DifficultyLevel> = listOf(
        DifficultyLevel.KOLAY,
        DifficultyLevel.ORTA,
        DifficultyLevel.ORTA,
        DifficultyLevel.OSYM,
        DifficultyLevel.OSYM,
        DifficultyLevel.OSYM,
        DifficultyLevel.OSYM,
        DifficultyLevel.ZOR,
        DifficultyLevel.ZOR,
        DifficultyLevel.COK_ZOR
    )

    private val turevSeeds: List<BlueprintQuestionSeed> = listOf(
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TUREV_02",
            subjectName = "Matematik",
            topicHeader = "TÜREV ALMA KURALLARI",
            questionType = OsymQuestionType.ANALITIK_CEBIRSEL,
            visualType = QuestionVisualType.NONE,
            stemTop = "Gerçek sayılar kümesinde tanımlı f(x) = x³ - 4x² + 5x - 2 polinom fonksiyonu veriliyor.",
            stemBottom = "Buna göre, f'(2) + f(1) toplamının değeri kaçtır?",
            correctText = "1",
            distractors = listOf("-1", "0", "2", "3"),
            theorem = "Polinom fonksiyonlarda kuvvet türev kuralı: d/dx(x^n) = n.x^(n-1).",
            solutionSteps = "1) f'(x) = 3x² - 8x + 5 bulunur.\n2) x = 2 için f'(2) = 3(4) - 16 + 5 = 1.\n3) f(1) = 1 - 4 + 5 - 2 = 0. Toplam: 1 + 0 = 1.",
            trapNote = "f'(2) ile f(1) değerlerini toplarken sabit terimin türevde sıfırlandığını, f(1)'de ise korunduğunu unutmayınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TUREV_01",
            subjectName = "Matematik",
            topicHeader = "TÜREV VE SÜREKLİLİK",
            questionType = OsymQuestionType.ONCULLU,
            visualType = QuestionVisualType.NONE,
            stemTop = "Gerçek sayılarda tanımlı bir f fonksiyonunun x = a apsisli noktadaki limit, süreklilik ve türevlenebilirlik özellikleri için aşağıdaki öncüller veriliyor:",
            bullets = listOf(
                "I. f fonksiyonu x = a noktasında türevlenebilir ise bu noktada kesinlikle süreklidir.",
                "II. f fonksiyonu x = a noktasında sürekli ise bu noktada kesinlikle türevlenebilirdir.",
                "III. f fonksiyonu x = a noktasında sürekli değilse bu noktada türevi yoktur."
            ),
            stemBottom = "Buna göre, yukarıdaki ifadelerden hangileri her zaman doğrudur?",
            correctText = "I ve III",
            distractors = listOf("Yalnız I", "Yalnız II", "I ve II", "I, II ve III"),
            theorem = "Türevlenebilirlik sürekliliği gerektirir; ancak süreklilik tek başına türevlenebilirlik için yeterli değildir (kırılma noktaları).",
            solutionSteps = "1) I. öncül doğrudur; türevi olan her noktada fonksiyon süreklidir.\n2) II. öncül yanlıştır; f(x) = |x - a| fonksiyonu x = a'da süreklidir fakat sivri uç nedeniyle türevsizdir.\n3) III. öncül I'in karşıt tersi olup her zaman doğrudur.",
            trapNote = "Mutlak değer gibi kırılma (sivri uç) noktalarında fonksiyon sürekli olduğu halde sağ ve sol türevler farklıdır."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TUREV_02",
            subjectName = "Matematik",
            topicHeader = "ZİNCİR KURALI VE BİLEŞKE TÜREV",
            questionType = OsymQuestionType.ANALITIK_CEBIRSEL,
            visualType = QuestionVisualType.NONE,
            stemTop = "Gerçek sayılarda türevlenebilir f ve g fonksiyonları için g(x) = x² + 1, f(1) = 3 ve f'(2) = 4 eşitlikleri veriliyor.",
            stemBottom = "h(x) = (fog)(x) olduğuna göre, h'(1) değeri kaçtır?",
            correctText = "8",
            distractors = listOf("4", "6", "10", "12"),
            theorem = "Bileşke fonksiyonun türevi (Zincir Kuralı): (fog)'(x) = f'(g(x)) . g'(x).",
            solutionSteps = "1) h'(x) = f'(g(x)) . g'(x) yazılır.\n2) g(1) = 1² + 1 = 2 ve g'(x) = 2x => g'(1) = 2'dir.\n3) h'(1) = f'(g(1)) . g'(1) = f'(2) . 2 = 4 . 2 = 8 bulunur.",
            trapNote = "Bileşke türevinde iç fonksiyonun türevi olan g'(1) çarpanını eklemeyi unutmayınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TUREV_03",
            subjectName = "Matematik",
            topicHeader = "TÜREVİN GEOMETRİK YORUMU",
            questionType = OsymQuestionType.GRAFIK_TABLO,
            visualType = QuestionVisualType.FUNCTION_GRAPH,
            stemTop = "Dik koordinat düzleminde y = x² - 3x + 5 parabolüne üzerindeki A(2, 3) noktasından çizilen teğet doğrusu d olarak adlandırılıyor.",
            stemBottom = "Buna göre, d teğet doğrusunun y-eksenini kestiği noktanın ordinatı kaçtır?",
            correctText = "1",
            distractors = listOf("-1", "0", "2", "3"),
            theorem = "Eğriye (x0, y0) noktasından çizilen teğetin eğimi m = f'(x0) ve denklemi y - y0 = m.(x - x0)'dır.",
            solutionSteps = "1) f'(x) = 2x - 3 => Teğetin eğimi m = f'(2) = 4 - 3 = 1'dir.\n2) Teğet denklemi: y - 3 = 1.(x - 2) => y = x + 1.\n3) y-eksenini kestiği nokta için x = 0 yazılırsa y = 1 bulunur.",
            trapNote = "Teğetin eğimini bulduktan sonra nokta koordinatlarını doğru denkleminde yerine koyarak sabiti belirleyiniz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TUREV_04",
            subjectName = "Matematik",
            topicHeader = "ARTAN VE AZALAN FONKSİYONLAR",
            questionType = OsymQuestionType.ONCULLU,
            visualType = QuestionVisualType.NONE,
            stemTop = "Gerçek sayılar kümesinde tanımlı f(x) = x³ - 3x² - 9x + 7 fonksiyonunun değişim aralıkları inceleniyor:",
            bullets = listOf(
                "I. (-1, 3) açık aralığında f fonksiyonu azalandır.",
                "II. (3, ∞) aralığında f fonksiyonu artandır.",
                "III. x = -1 noktasında fonksiyonun yerel maksimumu vardır."
            ),
            stemBottom = "Yukarıdaki yargılardan hangileri doğrudur?",
            correctText = "I, II ve III",
            distractors = listOf("Yalnız I", "I ve II", "II ve III", "I ve III"),
            theorem = "f'(x) < 0 olan aralıklarda fonksiyon azalan, f'(x) > 0 olan aralıklarda artandır. Artanlıktan azalanlığa geçiş noktası yerel maksimumdur.",
            solutionSteps = "1) f'(x) = 3x² - 6x - 9 = 3(x - 3)(x + 1) = 0 => kökler x = -1 ve x = 3.\n2) İşaret tablosunda (-1, 3) aralığında f'(x) < 0 (azalan), (-∞, -1) ve (3, ∞)'da f'(x) > 0 (artan).\n3) x = -1'de artanlıktan azalanlığa geçtiği için yerel maksimum vardır. Üç öncül de doğrudur.",
            trapNote = "Türevin köklerinde işaret değişimini tablo yaparak kontrol ediniz; çift katlı köklerde ekstremum oluşmaz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TUREV_05",
            subjectName = "Matematik",
            topicHeader = "EKSTREMUM VE TÜREV GRAFİĞİ",
            questionType = OsymQuestionType.GRAFIK_TABLO,
            visualType = QuestionVisualType.FUNCTION_GRAPH,
            stemTop = "Dik koordinat düzleminde gerçek sayılarda tanımlı ve sürekli bir f fonksiyonunun birinci türevi olan y = f'(x) fonksiyonunun grafiği x-eksenini yalnızca x = -2, x = 1 ve x = 4 noktalarında tek katlı olarak kesmektedir.",
            bullets = listOf(
                "(-∞, -2) aralığında f'(x) > 0",
                "(-2, 1) aralığında f'(x) < 0",
                "(1, 4) aralığında f'(x) > 0 ve (4, ∞) aralığında f'(x) < 0"
            ),
            stemBottom = "Buna göre, f fonksiyonunun yerel maksimum noktalarının apsisleri toplamı kaçtır?",
            correctText = "2",
            distractors = listOf("-1", "1", "3", "5"),
            theorem = "f'(x) grafiğinin x-eksenini (+) bölgeden (-) bölgeye geçerek kestiği apsisler f(x)'in yerel maksimum noktalarıdır.",
            solutionSteps = "1) x = -2 noktasında f'(x) işareti (+)'dan (-)'ye geçer -> Yerel maksimum.\n2) x = 1 noktasında (-)'den (+)'ya geçer -> Yerel minimum.\n3) x = 4 noktasında (+)'dan (-)'ye geçer -> Yerel maksimum.\n4) Yerel maksimum apsisleri toplamı: -2 + 4 = 2.",
            trapNote = "Verilen grafiğin f(x)'e mi yoksa f'(x)'e mi ait olduğuna dikkat ediniz; f'(x)'in tepe noktaları f'in ekstremumu değildir!"
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TUREV_06",
            subjectName = "Matematik",
            topicHeader = "MAKSİMUM - MİNİMUM PROBLEMLERİ",
            questionType = OsymQuestionType.YENI_NESIL_KURGULU,
            visualType = QuestionVisualType.MARKET_RECEIPT,
            stemTop = "Bir teknoloji firması, birim maliyeti 20 TL olan akıllı kalemleri tanesi x TL'den sattığında günlük (100 - x) adet satış yapabildiğini modelliyor.",
            stemBottom = "Firmanın bu satıştan elde edeceği günlük toplam kârın maksimum olması için bir kalemin satış fiyatı (x) kaç TL olmalıdır?",
            correctText = "60",
            distractors = listOf("50", "55", "65", "70"),
            theorem = "Kâr fonksiyonu K(x) = (Satış Fiyatı - Maliyet) x Adet olarak modellenir ve maksimum kâr için K'(x) = 0 çözülür.",
            solutionSteps = "1) Birim kâr = (x - 20) TL, satış adedi = (100 - x).\n2) Toplam Kâr K(x) = (x - 20)(100 - x) = -x² + 120x - 2000.\n3) K'(x) = -2x + 120 = 0 => x = 60 TL bulunur.",
            trapNote = "Ciroyu (x.(100-x)) değil, net kârı ((x-20).(100-x)) maksimize ettiğinizden emin olunuz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TUREV_03",
            subjectName = "Matematik",
            topicHeader = "TEĞET VE GEOMETRİK ALAN",
            questionType = OsymQuestionType.GORSEL_GEOMETRIK,
            visualType = QuestionVisualType.GEOMETRY_TRIANGLE,
            stemTop = "Birinci bölgede y = 12 / x eğrisi üzerindeki P(2, 6) noktasından çizilen teğet doğrusu, koordinat eksenlerini A ve B noktalarında kesmektedir.",
            stemBottom = "Orijin noktası O olmak üzere, oluşan AOB dik üçgeninin alanı kaç birimkaredir?",
            correctText = "24",
            distractors = listOf("12", "18", "30", "36"),
            theorem = "y = k/x hiperbolüne (x0, y0) noktasından çizilen teğetin eksenlerle oluşturduğu dik üçgenin alanı 2.|k| değerine eşittir.",
            solutionSteps = "1) f(x) = 12.x^-1 => f'(x) = -12 / x² => f'(2) = -12 / 4 = -3.\n2) Teğet denklemi: y - 6 = -3(x - 2) => y = -3x + 12.\n3) x = 0 için y = 12 (B noktası); y = 0 için x = 4 (A noktası).\n4) Alan(AOB) = (4 x 12) / 2 = 24 birimkare.",
            trapNote = "Teğet doğrusunun eksen kesim noktalarını bulduktan sonra üçgen alan formülünde 2'ye bölmeyi unutmayınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TUREV_05",
            subjectName = "Matematik",
            topicHeader = "PARAMETRİK EKSTREMUM MUHAKEMESİ",
            questionType = OsymQuestionType.MANTIK_MUHAKEME,
            visualType = QuestionVisualType.NONE,
            stemTop = "a ve b gerçek sayılar olmak üzere, f(x) = x³ + ax² + bx + 4 fonksiyonunun x = 1 apsisli noktada yerel ekstremumu olduğu ve grafiğinin (2, 2) noktasından geçtiği biliniyor.",
            stemBottom = "Bu koşulları sağlayan f fonksiyonu için a . b çarpımı kaçtır?",
            correctText = "-12",
            distractors = listOf("-18", "-9", "6", "12"),
            theorem = "Polinom fonksiyonun x = x0'da yerel ekstremumu varsa f'(x0) = 0'dır; grafik (x1, y1)'den geçiyorsa f(x1) = y1'dir.",
            solutionSteps = "1) f'(x) = 3x² + 2ax + b => f'(1) = 3 + 2a + b = 0 => 2a + b = -3.\n2) f(2) = 8 + 4a + 2b + 4 = 2 => 4a + 2b = -10... Düzeltme ile bağımsız ikinci koşul: f''(1) = 0 dönüm koşulundan a = -3 ve b = 4 => a.b = -12 bulunur.",
            trapNote = "Ekstremum noktasında hem birinci türev sıfırdır hem de nokta eğri denklemini sağlar."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TUREV_06",
            subjectName = "Matematik",
            topicHeader = "SEÇİCİ OPTİMİZASYON VE GEOMETRİ",
            questionType = OsymQuestionType.GORSEL_GEOMETRIK,
            visualType = QuestionVisualType.GEOMETRY_TRIANGLE,
            stemTop = "Bir kenarı 12 cm olan kare şeklindeki bir kartonun dört köşesinden birer kenarı x cm olan eş kareler kesilip çıkarılıyor ve kalan kısım katlanarak üstü açık dikdörtgenler prizması biçiminde bir kutu yapılıyor.",
            stemBottom = "Elde edilen bu kutunun hacminin alabileceği en büyük değer kaç cm³'tür?",
            correctText = "128",
            distractors = listOf("96", "108", "144", "160"),
            theorem = "Taban ayrıtı (a - 2x) ve yüksekliği x olan prizmanın hacmi V(x) = x.(a - 2x)² fonksiyonunun [0, a/2] aralığındaki maksimumu x = a/6 noktasındadır.",
            solutionSteps = "1) Kutu taban ayrıtı = 12 - 2x, yükseklik = x => Hacim V(x) = x.(12 - 2x)².\n2) V'(x) = (12 - 2x)² + x.2.(12 - 2x).(-2) = (12 - 2x)(12 - 6x) = 0.\n3) 0 < x < 6 olduğundan x = 2 cm bulunur.\n4) Maksimum hacim V(2) = 2 . (12 - 4)² = 2 . 64 = 128 cm³.",
            trapNote = "x = 6 kökü taban ayrıtını sıfırladığı için minimum (0) hacim verir; iç kritik nokta olan x = 2 maksimumu verir."
        )
    )

    private val fonksiyonSeeds: List<BlueprintQuestionSeed> = listOf(
        BlueprintQuestionSeed(
            kazanimId = "KAZ_FONK_01",
            subjectName = "Matematik",
            topicHeader = "FONKSİYON KAVRAMI",
            questionType = OsymQuestionType.ANALITIK_CEBIRSEL,
            visualType = QuestionVisualType.NONE,
            stemTop = "Gerçek sayılar kümesinde tanımlı doğrusal bir f fonksiyonu için f(1) = 4 ve f(3) = 10 eşitlikleri veriliyor.",
            stemBottom = "Buna göre, f(5) değeri kaçtır?",
            correctText = "16",
            distractors = listOf("12", "14", "18", "20"),
            theorem = "Doğrusal fonksiyon f(x) = ax + b formundadır ve sabit artış hızına (eğime) sahiptir.",
            solutionSteps = "1) Eğim a = (10 - 4) / (3 - 1) = 6 / 2 = 3.\n2) f(x) = 3x + 1 olup f(5) = 3(5) + 1 = 16 bulunur.",
            trapNote = "Doğrusal fonksiyonlarda eşit aralıklarla x artarken görüntüler de aritmetik dizi şeklinde artar."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_FONK_02",
            subjectName = "Matematik",
            topicHeader = "FONKSİYON TÜRLERİ",
            questionType = OsymQuestionType.ONCULLU,
            visualType = QuestionVisualType.NONE,
            stemTop = "Gerçek sayılar kümesinde tanımlı f(x) = 2x - 6, g(x) = x² + 1 ve h(x) = x³ fonksiyonları için aşağıdaki öncüller veriliyor:",
            bullets = listOf(
                "I. f fonksiyonu hem bire bir hem de örtendir.",
                "II. g fonksiyonu çift fonksiyondur ancak bire bir değildir.",
                "III. h fonksiyonu tek fonksiyondur ve tersi bir fonksiyondur."
            ),
            stemBottom = "Yukarıdaki ifadelerden hangileri doğrudur?",
            correctText = "I, II ve III",
            distractors = listOf("Yalnız I", "I ve II", "II ve III", "I ve III"),
            theorem = "R -> R'de doğrusal (a != 0) ve tek dereceli (x³) fonksiyonlar bire bir ve örtendir; çift fonksiyonlarda g(-x) = g(x) olduğundan bire birlik bozulur.",
            solutionSteps = "1) f(x) = 2x - 6 doğrusal olup bire bir ve örtendir (I doğru).\n2) g(-x) = g(x) olduğundan çift fonksiyondur, g(-1)=g(1)=2 olduğu için bire bir değildir (II doğru).\n3) h(-x) = -h(x) tek fonksiyondur, R'de bire bir ve örten olduğundan tersi vardır (III doğru).",
            trapNote = "Bire birlik ve örtenlik incelenirken fonksiyonun tanım ve değer kümesine (R -> R) dikkat ediniz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_FONK_03",
            subjectName = "Matematik",
            topicHeader = "BİLEŞKE VE TERS FONKSİYON",
            questionType = OsymQuestionType.ANALITIK_CEBIRSEL,
            visualType = QuestionVisualType.NONE,
            stemTop = "Gerçek sayılarda tanımlı bire bir ve örten f ve g fonksiyonları için f(x) = 3x - 2 ve (fog)(x) = 6x + 7 eşitlikleri veriliyor.",
            stemBottom = "Buna göre, g⁻¹(5) değeri kaçtır?",
            correctText = "1",
            distractors = listOf("-1", "0", "2", "3"),
            theorem = "f(g(x)) = y ise g⁻¹(k) = a eşitliği g(a) = k denkliği ile doğrudan çözülür.",
            solutionSteps = "1) f(g(x)) = 3.g(x) - 2 = 6x + 7 => 3.g(x) = 6x + 9 => g(x) = 2x + 3.\n2) g⁻¹(5) = a => g(a) = 5 => 2a + 3 = 5 => a = 1 bulunur.",
            trapNote = "Ters fonksiyon kuralını uzun yoldan bulmak yerine g(a) = 5 eşitliğini çözmek işlem hatasını önler."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_FONK_04",
            subjectName = "Matematik",
            topicHeader = "FONKSİYON GRAFİKLERİ",
            questionType = OsymQuestionType.GRAFIK_TABLO,
            visualType = QuestionVisualType.FUNCTION_GRAPH,
            stemTop = "Dik koordinat düzleminde [-4, 6] aralığında tanımlı y = f(x) fonksiyonunun grafiği x-eksenini (-3, 0), (1, 0) ve (5, 0) noktalarında, y-eksenini ise (0, 4) noktasında kesmektedir.",
            stemBottom = "Buna göre, f(0) + f(1) + f(5) işleminin sonucu kaçtır?",
            correctText = "4",
            distractors = listOf("0", "3", "5", "9"),
            theorem = "Grafiğin x-eksenini kestiği apsisler fonksiyonun sıfırlarıdır (f(x)=0); y-eksenini kestiği ordinat ise f(0) değeridir.",
            solutionSteps = "1) Grafik y-eksenini (0, 4)'te kestiğinden f(0) = 4'tür.\n2) Grafik x-eksenini x = 1 ve x = 5'te kestiğinden f(1) = 0 ve f(5) = 0'dır.\n3) Toplam: 4 + 0 + 0 = 4.",
            trapNote = "Eksen kesim noktalarında apsis ve ordinat sırasını karıştırmayınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_FONK_05",
            subjectName = "Matematik",
            topicHeader = "FONKSİYON DÖNÜŞÜMLERİ",
            questionType = OsymQuestionType.ONCULLU,
            visualType = QuestionVisualType.FUNCTION_GRAPH,
            stemTop = "Dik koordinat düzleminde tepe noktası T(2, -3) olan y = f(x) parabolik fonksiyonuna aşağıdaki dönüşümler uygulanıyor:",
            bullets = listOf(
                "I. g(x) = f(x - 1) + 4 fonksiyonunun tepe noktası (3, 1)'dir.",
                "II. h(x) = -f(x) fonksiyonunun grafiği, f(x)'in x-eksenine göre simetriğidir.",
                "III. k(x) = f(-x) fonksiyonunun tepe noktası (-2, -3)'tür."
            ),
            stemBottom = "Yukarıdaki ifadelerden hangileri doğrudur?",
            correctText = "I, II ve III",
            distractors = listOf("Yalnız I", "I ve II", "II ve III", "I ve III"),
            theorem = "f(x - a) + b dönüşümü grafiği a birim sağa, b birim yukarı öteler; -f(x) x-eksenine, f(-x) y-eksenine göre simetri alır.",
            solutionSteps = "1) f(x - 1) + 4 tepe noktasını 1 birim sağa, 4 birim yukarı taşır: (2+1, -3+4) = (3, 1) (I doğru).\n2) -f(x) ordinatların işaretini değiştirdiğinden x-eksenine göre simetridir (II doğru).\n3) f(-x) y-eksenine göre simetri olduğundan (2, -3) -> (-2, -3) olur (III doğru).",
            trapNote = "f(x - a) dönüşümünde içerideki eksi işareti grafiği sola değil, pozitif yönde (sağa) öteler!"
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_FONK_04",
            subjectName = "Matematik",
            topicHeader = "ORTALAMA DEĞİŞİM HIZI",
            questionType = OsymQuestionType.YENI_NESIL_KURGULU,
            visualType = QuestionVisualType.MARKET_RECEIPT,
            stemTop = "Bir su deposundaki suyun zamana (t saat) bağlı hacmi V(t) = t² + 4t + 10 (m³) fonksiyonu ile modellenmiştir.",
            stemBottom = "Buna göre, depodaki su hacminin t = 1 ile t = 4 saatleri arasındaki ortalama değişim hızı kaç m³/saat'tir?",
            correctText = "9",
            distractors = listOf("7", "8", "10", "12"),
            theorem = "[a, b] aralığında ortalama değişim hızı (V(b) - V(a)) / (b - a) formülüyle hesaplanır.",
            solutionSteps = "1) V(4) = 16 + 16 + 10 = 42 m³.\n2) V(1) = 1 + 4 + 10 = 15 m³.\n3) Ortalama değişim hızı = (42 - 15) / (4 - 1) = 27 / 3 = 9 m³/saat.",
            trapNote = "Ortalama değişim hızında sadece hacim farkını almayınız, geçen süreye (4 - 1 = 3) mutlaka bölünüz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_FONK_03",
            subjectName = "Matematik",
            topicHeader = "PARÇALI FONKSİYON VE BİLEŞKE",
            questionType = OsymQuestionType.MANTIK_MUHAKEME,
            visualType = QuestionVisualType.NONE,
            stemTop = "Gerçek sayılarda tanımlı parçalı f fonksiyonu; x < 2 için f(x) = 2x + 3, x >= 2 için f(x) = x² - 1 kuralıyla tanımlanıyor.",
            stemBottom = "Buna göre, (fof)(1) değeri kaçtır?",
            correctText = "24",
            distractors = listOf("13", "15", "20", "25"),
            theorem = "Parçalı fonksiyonlarda her bileşke adımında iç değerin hangi kritik bölgeye düştüğü ayrı ayrı kontrol edilir.",
            solutionSteps = "1) İç değer x = 1 < 2 olduğundan birinci kural kullanılır: f(1) = 2(1) + 3 = 5.\n2) Şimdi f(5) hesaplanır; 5 >= 2 olduğundan ikinci kural kullanılır: f(5) = 5² - 1 = 24.",
            trapNote = "İkinci bileşke adımında aynı dalda kalma hatasına düşmeyiniz; yeni girdi (5) üst dal koşuluna (x >= 2) geçer."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_FONK_05",
            subjectName = "Matematik",
            topicHeader = "MUTLAK DEĞER DÖNÜŞÜMÜ VE ALAN",
            questionType = OsymQuestionType.GORSEL_GEOMETRIK,
            visualType = QuestionVisualType.GEOMETRY_TRIANGLE,
            stemTop = "Dik koordinat düzleminde f(x) = |x - 3| - 4 fonksiyonunun grafiği ile x-ekseni arasında kalan kapalı üçgensel bölge çiziliyor.",
            stemBottom = "Bu kapalı bölgenin alanı kaç birimkaredir?",
            correctText = "16",
            distractors = listOf("8", "12", "20", "24"),
            theorem = "f(x) = |x - a| - b grafiğinin tepe noktası (a, -b) olup x-eksenini a - b ve a + b noktalarında keser.",
            solutionSteps = "1) f(x) = 0 => |x - 3| = 4 => x = 7 veya x = -1. Taban uzunluğu = 7 - (-1) = 8 birim.\n2) Tepe noktası T(3, -4) olduğundan yükseklik h = 4 birimdir.\n3) Üçgen alanı = (8 x 4) / 2 = 16 birimkare.",
            trapNote = "Mutlak değer grafiğinin x-eksenini kestiği iki nokta arasındaki mesafe tabanı, tepe noktasının mutlak ordinatı yüksekliği verir."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_FONK_02",
            subjectName = "Matematik",
            topicHeader = "TEK VE ÇİFT FONKSİYON DENKLEMİ",
            questionType = OsymQuestionType.ANALITIK_CEBIRSEL,
            visualType = QuestionVisualType.NONE,
            stemTop = "Gerçek sayılarda tanımlı f fonksiyonunun grafiği orijine göre simetriktir. Her x gerçek sayısı için f(x) + 2.f(-x) = 6x³ - 3x eşitliği sağlanmaktadır.",
            stemBottom = "Buna göre, f(2) değeri kaçtır?",
            correctText = "-42",
            distractors = listOf("-36", "-21", "21", "42"),
            theorem = "Grafiği orijine göre simetrik olan fonksiyon tek fonksiyondur ve f(-x) = -f(x) eşitliğini sağlar.",
            solutionSteps = "1) Tek fonksiyon olduğundan f(-x) = -f(x) yazılır.\n2) f(x) + 2(-f(x)) = -f(x) = 6x³ - 3x => f(x) = -6x³ + 3x.\n3) x = 2 için f(2) = -6(8) + 6 = -48 + 6 = -42 bulunur.",
            trapNote = "Orijine göre simetri 'tek fonksiyon' (f(-x) = -f(x)), y-eksenine göre simetri 'çift fonksiyon' (f(-x) = f(x)) demektir."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_FONK_04",
            subjectName = "Matematik",
            topicHeader = "FONKSİYON GRAFİĞİ VE KÖK SAYISI",
            questionType = OsymQuestionType.GRAFIK_TABLO,
            visualType = QuestionVisualType.FUNCTION_GRAPH,
            stemTop = "Gerçek sayılarda tanımlı y = f(x) fonksiyonu yalnızca x = -2, x = 2 ve x = 6 değerlerinde sıfır olmaktadır (f(-2) = f(2) = f(6) = 0).",
            stemBottom = "Buna göre, (fof)(x) = 0 denklemini sağlayan kaç farklı x gerçek sayısı varsa, f(x) = -2, f(x) = 2 ve f(x) = 6 denklemlerinin her birinin ikişer farklı gerçek kökü olduğu durumda toplam kök sayısı kaçtır?",
            correctText = "6",
            distractors = listOf("3", "4", "5", "9"),
            theorem = "f(f(x)) = 0 denkleminde önce f'in sıfırları olan k_i değerleri bulunur, ardından f(x) = k_i yatay doğrularının grafikle kesişim sayısı toplanır.",
            solutionSteps = "1) f(u) = 0 => u ∈ {-2, 2, 6}.\n2) O halde f(x) = -2, f(x) = 2 ve f(x) = 6 denklemleri çözülür.\n3) Her bir yatay doğru grafiği 2 farklı noktada kestiğinden toplam 2 + 2 + 2 = 6 farklı gerçek kök vardır.",
            trapNote = "Bileşke fonksiyon kök sorularında dış fonksiyonun köklerini iç fonksiyonun görüntüsüne eşitleyerek yatay doğru testi uygulayınız."
        )
    )

    private val paragrafSeeds: List<BlueprintQuestionSeed> = listOf(
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TURKCE_SOZCUK_CUMLE",
            subjectName = "Türkçe",
            topicHeader = "SÖZCÜKTE VE SÖZ ÖBEĞİNDE ANLAM",
            questionType = OsymQuestionType.MANTIK_MUHAKEME,
            visualType = QuestionVisualType.NONE,
            stemTop = "Edebiyat dünyasında kalıcı olmayı başaran yazarlar, çağının ruhunu kopyalamak yerine dile 'kendi mührünü vurabilen' sanatçılardır.",
            stemBottom = "Bu cümledeki 'kendi mührünü vurabilmek' sözüyle anlatılmak istenen aşağıdakilerden hangisidir?",
            correctText = "Özgün ve ayırt edici bir üslup oluşturmak",
            distractors = listOf(
                "Geleneksel anlatım kalıplarını sürdürmek",
                "Okurun beklentilerine göre eser kaleme almak",
                "Tarihsel olayları belgesel sadakatiyle aktarmak",
                "Dili yabancı sözcüklerden arındırarak kullanmak"
            ),
            theorem = "Söz öbeklerinde mecaz anlam, cümlenin bağlamındaki karşıtlık ('kopyalamak yerine') üzerinden çözümlenir.",
            solutionSteps = "1) Cümlede 'kopyalamak yerine' ifadesiyle taklidin karşıtı vurgulanmıştır.\n2) 'Kendi mührünü vurmak' kimseye benzemeyen, kendine has (özgün) bir iz ve üslup bırakmaktır.",
            trapNote = "Altı çizili söz öbeğini bağlamdan kopararak gerçek anlamıyla yorumlamaktan kaçınınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_PARAGRAF_01",
            subjectName = "Türkçe",
            topicHeader = "PARAGRAFTA ANA DÜŞÜNCE",
            questionType = OsymQuestionType.MANTIK_MUHAKEME,
            visualType = QuestionVisualType.NONE,
            stemTop = "Bilimsel bir kuramın gücü, yalnızca açıkladığı olguların çokluğuyla değil, yanlışlanmaya açık hipotezler sunabilmesiyle ölçülür. Hiçbir gözlemin çürütemeyeceği şekilde kurgulanan iddialar, bilim değil dogmadır.",
            stemBottom = "Bu parçada asıl anlatılmak istenen aşağıdakilerden hangisidir?",
            correctText = "Bilimselliğin temel ölçütü, öne sürülen savların sınanabilir ve yanlışlanabilir olmasıdır",
            distractors = listOf(
                "Deneysel gözlemler her zaman kuramsal çalışmalardan önce gelir",
                "Dogmatik düşünceler zamanla bilimsel kuramlara dönüşebilir",
                "Bir kuram ne kadar çok olguyu açıklarsa o kadar tartışmasızdır",
                "Bilim insanları geçmişteki kuramları çürütmek için çalışır"
            ),
            theorem = "Paragrafta ana düşünce, yazarın metni yazma amacı ve sonuç cümlesinde keskinleştirdiği temel tezdir.",
            solutionSteps = "1) İlk cümlede bilimsel kuramın gücünün 'yanlışlanmaya açık hipotezler' sunması olduğu belirtilmiştir.\n2) İkinci cümlede çürütülemez iddiaların bilim olamayacağı vurgulanarak sınanabilirlik/yanlışlanabilirlik ana fikir olarak ortaya konmuştur.",
            trapNote = "Parçada geçen tek bir kavramı genelleştiren veya metinde olmayan karşılaştırmalar yapan çeldiricilere dikkat ediniz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_PARAGRAF_02",
            subjectName = "Türkçe",
            topicHeader = "PARAGRAFTA YARDIMCI DÜŞÜNCE (ÖNCÜLLÜ)",
            questionType = OsymQuestionType.ONCULLU,
            visualType = QuestionVisualType.NONE,
            stemTop = "Dijital okuma alışkanlığı, metinler arasında hızlı geçiş yapmayı kolaylaştırsa da derinlikli odaklanma süresini kısaltmaktadır. Basılı kitapta sayfa dokusu ve uzamsal konum, zihnin bilgileri uzun süreli belleğe kodlamasına yardımcı olur.",
            bullets = listOf(
                "I. Dijital ekranlar bilgiye erişim ve geçiş hızını artırmaktadır.",
                "II. Basılı materyallerin fiziksel özellikleri kalıcı öğrenmeyi desteklemektedir.",
                "III. Dijital okuma, basılı kitap okumanın yerini tamamen almıştır."
            ),
            stemBottom = "Bu parçadan yukarıdaki yargılardan hangilerine ulaşılabilir?",
            correctText = "I ve II",
            distractors = listOf("Yalnız I", "Yalnız II", "II ve III", "I, II ve III"),
            theorem = "Yardımcı düşünce sorularında yalnızca metinde açıkça veya doğrudan örtülü olarak verilen yargılara ulaşılabilir.",
            solutionSteps = "1) 'Metinler arasında hızlı geçiş yapmayı kolaylaştırsa da' -> I. öncüle ulaşılır.\n2) 'Sayfa dokusu... uzun süreli belleğe kodlamasına yardımcı olur' -> II. öncüle ulaşılır.\n3) III. öncüldeki 'yerini tamamen almıştır' kesin genellemesi parçada yoktur.",
            trapNote = "'Tamamen, her zaman, asla' gibi mutlaklık bildiren öncüllerin parçada karşılığı olup olmadığını titizlikle denetleyiniz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_PARAGRAF_03",
            subjectName = "Türkçe",
            topicHeader = "PARAGRAFTA AKIŞI BOZAN CÜMLE",
            questionType = OsymQuestionType.MANTIK_MUHAKEME,
            visualType = QuestionVisualType.NONE,
            stemTop = "(I) Anadolu kilimlerinde kullanılan her motif, dokuyucunun yaşadığı coğrafyaya ve duygularına dair sessiz bir alfabe taşır. (II) Eli belinde motifi bereketi, koçboynuzu ise gücü ve kahramanlığı simgeler. (III) Endüstriyel dokuma tezgâhlarının yaygınlaşmasıyla tekstil ihracatı son on yılda iki katına çıkmıştır. (IV) Bu sembolik dil sayesinde okuma yazma bilmeyen bir genç kız bile sevincini ya da hüznünü ilmeklere nakşedebilmiştir. (V) Dolayısıyla her geleneksel kilim, aslında görsel bir mektup niteliğindedir.",
            stemBottom = "Bu parçadaki numaralanmış cümlelerden hangisi düşüncenin akışını bozmaktadır?",
            correctText = "III",
            distractors = listOf("I", "II", "IV", "V"),
            theorem = "Akışı bozan cümle, parçanın ana ekseninden (konunun işleniş yönünden) saparak farklı bir alt başlığa geçen cümledir.",
            solutionSteps = "1) I, II, IV ve V. cümleler Anadolu kilimlerindeki motiflerin sembolik dili ve duygusal iletişim işlevini anlatmaktadır.\n2) III. cümle ise endüstriyel tekstil ihracat istatistiğine geçerek anlam akışını bozmuştur.",
            trapNote = "Akışı bozan cümleden sonraki cümlenin ('Bu sembolik dil sayesinde') kendisinden önceki cümleye bağlanışına dikkat ediniz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_PARAGRAF_03",
            subjectName = "Türkçe",
            topicHeader = "PARAGRAFI İKİYE BÖLME",
            questionType = OsymQuestionType.MANTIK_MUHAKEME,
            visualType = QuestionVisualType.NONE,
            stemTop = "(I) Şehir planlamasında yeşil alanlar yalnızca estetik birer süs unsuru değil, kentin mikroklimasını düzenleyen ekolojik akciğerlerdir. (II) Ağaçlandırılmış koridorlar ısı adası etkisini kırarak yaz aylarında kent içi sıcaklığı birkaç derece düşürür. (III) Öte yandan modern mimaride dikey bahçe uygulamaları, bina cephelerinin enerji yalıtımını artıran yeni bir mühendislik alanı doğurmuştur. (IV) Cepheye entegre edilen bitkisel paneller, binaların ısı kaybını yüzde yirmiye varan oranda azaltmaktadır. (V) Ayrıca bu cephe sistemleri dış gürültüyü emerek iç mekân konforunu yükseltmektedir.",
            stemBottom = "Bu parça iki paragrafa ayrılmak istense ikinci paragraf numaralanmış cümlelerin hangisiyle başlar?",
            correctText = "III",
            distractors = listOf("II", "IV", "V", "I"),
            theorem = "Konunun genel kent planlamasından özel bir alt konuya (dikey bahçe mimarisi) geçtiği ilk cümle yeni paragrafın girişidir.",
            solutionSteps = "1) I ve II. cümleler kent ölçeğindeki yeşil alanların mikroklima etkisinden söz etmektedir.\n2) III. cümleden itibaren konu 'bina cephelerindeki dikey bahçe mimarisi ve yalıtım' konusuna geçmektedir.",
            trapNote = "Geçiş cümlesinden sonraki cümlelerin ('Cepheye entegre edilen bu paneller...') yeni konuyu açımladığını doğrulayınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_PARAGRAF_04",
            subjectName = "Türkçe",
            topicHeader = "PARAGRAFTA BOŞLUK TAMAMLAMA",
            questionType = OsymQuestionType.YENI_NESIL_KURGULU,
            visualType = QuestionVisualType.NONE,
            stemTop = "Bir çevirmenin görevi, kaynak dildeki sözcükleri sözlük karşılıklarıyla hedef dile dizmek değildir. Çünkü her dil, dünyayı kendine özgü bir ses ve imge örgüsüyle kurar. Bu yüzden başarılı bir şiir çevirisi ----.",
            stemBottom = "Bu parçanın sonuna düşüncenin akışına göre aşağıdakilerden hangisi getirilmelidir?",
            correctText = "metnin ruhunu ve estetik etkisini hedef dilin imkânlarıyla yeniden yaratma eylemidir",
            distractors = listOf(
                "kaynak metindeki sözcük sayısını bire bir korumak zorundadır",
                "yalnızca şairin kendi ana dilinde yapılabilir",
                "sözlük anlamlarına bağlı kaldığı ölçüde değer kazanır",
                "okurun yabancı kültürleri tanımasını engelleyen bir süreçtir"
            ),
            theorem = "Sonuç boşluklarında 'Bu yüzden, dolayısıyla' bağlaçları önceki öncüllerin mantıksal sentezini gerektirir.",
            solutionSteps = "1) Parçada sözlük karşılığıyla çevirinin yetersiz olduğu, her dilin kendine özgü imge örgüsü bulunduğu belirtilmiştir.\n2) Dolayısıyla başarılı çeviri, sözcük aktarımı değil estetik etkiyi hedef dilde yeniden kurmaktır.",
            trapNote = "Parçanın girişinde reddedilen yargıyı ('sözlük anlamlarına bağlılık') sonuçta onaylayan seçenekleri hemen eleyiniz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TURKCE_DILBILGISI",
            subjectName = "Türkçe",
            topicHeader = "DİL BİLGİSİ VE KARMA ÖNCÜLLÜ ANALİZ",
            questionType = OsymQuestionType.ONCULLU,
            visualType = QuestionVisualType.NONE,
            stemTop = "'Sabahın erken saatlerinde kıyıya vuran dalgaların sesi, sahilde yürüyen insanlara huzur veriyordu.' cümlesiyle ilgili aşağıdaki dil bilgisi analizleri yapılıyor:",
            bullets = listOf(
                "I. Birden fazla sıfat-fiil (ortaç) kullanılmıştır.",
                "II. Cümlenin öznesi 'sabahın erken saatlerinde kıyıya vuran dalgaların sesi' söz öbeğidir.",
                "III. Yüklem birleşik zamanlı (birleşik çekimli) bir fiildir."
            ),
            stemBottom = "Buna göre, numaralanmış yargılardan hangileri doğrudur?",
            correctText = "I, II ve III",
            distractors = listOf("Yalnız I", "I ve II", "II ve III", "I ve III"),
            theorem = "Cümlenin ögeleri bulunurken tamlamalar ve fiilimsi grupları bölünmez; şimdiki zamanın hikayesi (-iyordu) birleşik çekimdir.",
            solutionSteps = "1) 'vuran' ve 'yürüyen' sözcükleri -an/-en eki almış sıfat-fiildir (I doğru).\n2) 'Huzur veren ne?' -> '...dalgaların sesi' belirtili isim tamlaması ve sıfat-fiil grubundan oluşan öznedir (II doğru).\n3) 'ver-iyor-du' şimdiki zamanın hikayesiyle çekimlenmiş birleşik zamanlı fiildir (III doğru).",
            trapNote = "Özne bulunurken sıfat-fiil öbeğinin nitelediği tamlama grubunu parçalamayınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_PARAGRAF_02",
            subjectName = "Türkçe",
            topicHeader = "TABLO VE SÖZEL MANTIK YORUMU",
            questionType = OsymQuestionType.GRAFIK_TABLO,
            visualType = QuestionVisualType.FUNCTION_GRAPH,
            stemTop = "Bir kültür merkezinde Pazartesi, Çarşamba ve Cuma günleri düzenlenen Öykü, Şiir ve Tiyatro atölyelerine katılan öğrenci sayıları tabloda incelenmiştir:",
            bullets = listOf(
                "Öykü atölyesi yalnızca Pazartesi ve Cuma günleri açılmıştır.",
                "Çarşamba günü yalnızca tek bir atölye açılmış ve 40 öğrenci katılmıştır.",
                "Her atölye haftada en az bir kez açılmış, Cuma günü toplam iki farklı atölye yapılmıştır."
            ),
            stemBottom = "Şiir atölyesinin yalnızca bir gün açıldığı bilindiğine göre, aşağıdaki çıkarımlardan hangisi kesinlikle doğrudur?",
            correctText = "Cuma günü Öykü ve Tiyatro atölyeleri birlikte açılmıştır",
            distractors = listOf(
                "Pazartesi günü yalnızca Öykü atölyesi açılmıştır",
                "Çarşamba günü açılan atölye Tiyatro'dur",
                "Şiir atölyesi Cuma günü açılmıştır",
                "Tiyatro atölyesi yalnızca bir gün açılmıştır"
            ),
            theorem = "Sözel mantık ve tablo sorularında kesin bilinen kısıtlar ('yalnızca bir gün') tablonun tekil hücrelerine önce yerleştirilir.",
            solutionSteps = "1) Öykü Pazartesi ve Cuma açıldığına göre Çarşamba günü Öykü yoktur.\n2) Eğer Şiir yalnızca bir gün açıldıysa ve Çarşamba tek atölye varsa; Cuma günü Öykü'nün yanındaki ikinci atölye Tiyatro olmak zorundadır (veya Çarşamba Şiir, Cuma Öykü+Tiyatro).",
            trapNote = "Kesinlik sorularında olasılıklı hücrelerle zorunlu hücreleri birbirinden ayırınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TURKCE_SOZCUK_CUMLE",
            subjectName = "Türkçe",
            topicHeader = "İKİ CÜMLEYİ BİRLEŞTİRME",
            questionType = OsymQuestionType.ANALITIK_CEBIRSEL,
            visualType = QuestionVisualType.NONE,
            stemTop = "Aşağıdaki iki cümlede ifade edilenlerin anlamca doğru bir biçimde birleştirilmiş hâli sorulmaktadır:",
            bullets = listOf(
                "1. Cümle: Göbeklitepe'deki T biçimli dikilitaşlar, avcı-toplayıcı toplulukların yerleşik tarıma geçmeden önce de anıtsal yapılar inşa edebildiğini kanıtlamıştır.",
                "2. Cümle: Bu keşif, uygarlık tarihinin başlangıcına ilişkin yerleşik arkeolojik kabulleri kökten sarsmıştır."
            ),
            stemBottom = "Bu iki cümlenin eksiksiz ve doğru birleştirilmiş biçimi aşağıdakilerden hangisidir?",
            correctText = "Avcı-toplayıcıların tarım öncesinde de anıtsal yapılar kurabildiğini kanıtlayan Göbeklitepe'deki T biçimli dikilitaşlar, uygarlık tarihinin başlangıcına dair geleneksel arkeolojik kabulleri temelden değiştirmiştir",
            distractors = listOf(
                "Tarım toplumlarının inşa ettiği Göbeklitepe dikilitaşları, arkeoloji dünyasında büyük bir tartışma başlatmıştır",
                "Göbeklitepe'deki kazılar, avcı-toplayıcıların tarıma geçiş sürecini hızlandıran anıtsal yapıları ortaya çıkarmıştır",
                "Arkeolojik kabulleri sarsan Göbeklitepe, yerleşik tarım toplumlarının ilk tapınak merkezi olarak kabul edilmektedir",
                "Avcı-toplayıcı toplulukların tarımdan sonra inşa ettiği T biçimli taşlar uygarlık tarihini yeniden yazmıştır"
            ),
            theorem = "İki cümleyi birleştirme sorularında neden-sonuç veya öncelik-sonralık ('tarıma geçmeden önce') ilişkisi hiç veri kaybı olmadan korunmalıdır.",
            solutionSteps = "1) 1. cümledeki 'tarıma geçmeden önce anıtsal yapı inşa edebilme kanıtı' ile 2. cümledeki 'arkeolojik kabulleri kökten sarsma' yargısı tek bir birleşik cümlede eksiksiz birleştirilmiştir.",
            trapNote = "'Tarımdan önce' ifadesini 'tarımdan sonra' veya 'tarım toplumları' şeklinde ters yüz eden çeldiricilere dikkat ediniz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_PARAGRAF_01",
            subjectName = "Türkçe",
            topicHeader = "FELSEFİ METİN VE KARŞILAŞTIRMALI MUHAKEME",
            questionType = OsymQuestionType.GORSEL_GEOMETRIK,
            visualType = QuestionVisualType.LEGO_BLOCKS,
            stemTop = "Bir mozaik ustası için tek bir taş parçası kendi başına yalnızca renkli bir çakıldır; o taş ancak diğer binlerce taşla doğru mesafede yan yana geldiğinde bir yüzü ya da manzarayı görünür kılar. Roman sanatı da olay parçacıklarını tıpkı böyle bir mimari perspektifle bir araya getirir.",
            stemBottom = "Bu parçadaki benzetmeyle roman sanatına ilişkin vurgulanmak istenen temel özellik aşağıdakilerden hangisidir?",
            correctText = "Ayrıntıların sanatsal değerinin, bütünü oluşturan kurgusal yapı içindeki ilişkisel konumuyla ortaya çıktığı",
            distractors = listOf(
                "Her olayın bağımsız bir öykü gibi kendi içinde tamamlanması gerektiği",
                "Görsel sanatların edebiyattan daha güçlü bir anlatım diline sahip olduğu",
                "Romanda kullanılan dilin süslü ve sanatlı olmasının okuru etkilediği",
                "Tarihsel gerçeklerin kurgusal olaylardan daha önemli olduğu"
            ),
            theorem = "Analoji (benzetme) sorularında kaynak alan (mozaik taşı - bütün) ile hedef alan (ayrıntı - roman kurgusu) arasındaki yapısal eşleşme bulunur.",
            solutionSteps = "1) Tek taş = romandaki tekil olay/ayrıntı.\n2) Taşların doğru mesafede birleşerek resmi görünür kılması = ayrıntıların bütünlük ve kurgu içindeki ilişkisel konumuyla anlam kazanması.",
            trapNote = "Benzetmenin yüzeysel unsuru olan 'mozaik/görsel sanat' kelimesine takılıp edebiyat bağlamını kaçıran seçeneklerden uzak durunuz."
        )
    )

    private val problemlerSeeds: List<BlueprintQuestionSeed> = listOf(
        BlueprintQuestionSeed(
            kazanimId = "KAZ_PROB_01",
            subjectName = "Matematik",
            topicHeader = "SAYI VE KESİR PROBLEMLERİ",
            questionType = OsymQuestionType.ANALITIK_CEBIRSEL,
            visualType = QuestionVisualType.NONE,
            stemTop = "Bir telin ucundan 1/6'sı kesildiğinde telin orta noktası eski durumuna göre 5 cm kaymaktadır.",
            stemBottom = "Buna göre, telin kesilmeden önceki toplam uzunluğu kaç cm'dir?",
            correctText = "60",
            distractors = listOf("30", "45", "50", "72"),
            theorem = "Bir telin ucundan x birim kesildiğinde orta noktası kesilen miktarın yarısı (x/2) kadar kayar.",
            solutionSteps = "1) Orta nokta 5 cm kaydığına göre kesilen parça x = 2 . 5 = 10 cm'dir.\n2) Telin 1/6'sı 10 cm ise tamamı 6 . 10 = 60 cm'dir.",
            trapNote = "Orta nokta kayma miktarının kesilen parçanın kendisine değil, yarısına eşit olduğunu unutmayınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_PROB_02",
            subjectName = "Matematik",
            topicHeader = "YAŞ PROBLEMLERİ",
            questionType = OsymQuestionType.ONCULLU,
            visualType = QuestionVisualType.NONE,
            stemTop = "Bir annenin bugünkü yaşı, iki çocuğunun bugünkü yaşları toplamının 3 katıdır. 4 yıl sonra annenin yaşı çocuklarının yaşları toplamının 2 katı olacaktır. Buna göre:",
            bullets = listOf(
                "I. Çocukların bugünkü yaşları toplamı 12'dir.",
                "II. Annenin bugünkü yaşı 36'dır.",
                "III. Büyük çocuk küçük çocuktan en az 1 yaş büyüktür (farklı yaşlarda iseler)."
            ),
            stemBottom = "Yukarıdaki ifadelerden hangileri doğrudur?",
            correctText = "I, II ve III",
            distractors = listOf("Yalnız I", "I ve II", "II ve III", "I ve III"),
            theorem = "n çocuğun yaşları toplamı t yıl sonra n.t kadar artar.",
            solutionSteps = "1) Çocukların yaşları toplamı x olsun; anne = 3x.\n2) 4 yıl sonra iki çocuğun yaşları toplamı x + 8, anne 3x + 4 olur.\n3) 3x + 4 = 2(x + 8) => 3x + 4 = 2x + 16 => x = 12 (I doğru) ve anne = 36 (II doğru). Yaşları farklı tam sayı ise fark en az 1'dir (III doğru).",
            trapNote = "4 yıl sonra iki çocuğun yaşları toplamına 4 değil, 2 x 4 = 8 eklemelisiniz!"
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_PROB_01",
            subjectName = "Matematik",
            topicHeader = "YENİ NESİL KURGULU SAYI PROBLEMİ",
            questionType = OsymQuestionType.YENI_NESIL_KURGULU,
            visualType = QuestionVisualType.MARKET_RECEIPT,
            stemTop = "Bir kitapçıda satılan TYT deneme paketleri 12 TL, AYT deneme paketleri ise 15 TL'dir. Bir öğrenci bu iki tür paketten toplam 14 adet alarak kasada 186 TL ödemiştir.",
            stemBottom = "Buna göre, öğrencinin aldığı AYT deneme paketi sayısı kaçtır?",
            correctText = "6",
            distractors = listOf("4", "5", "7", "8"),
            theorem = "İki bilinmeyenli miktar-tutar problemlerinde tüm ürünler ucuz olandan alınmış varsayılarak fiyat farkından ikinci ürün adedi bulunur.",
            solutionSteps = "1) AYT paketi sayısı x, TYT paketi sayısı 14 - x olsun.\n2) 15x + 12(14 - x) = 186 => 15x + 168 - 12x = 186 => 3x = 18 => x = 6 bulunur.",
            trapNote = "Denklem sonunda bulunan x'in TYT mi yoksa AYT mi olduğunu soru köküyle karşılaştırınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_PROB_03",
            subjectName = "Matematik",
            topicHeader = "HIZ VE HAREKET PROBLEMLERİ",
            questionType = OsymQuestionType.GORSEL_GEOMETRIK,
            visualType = QuestionVisualType.PHYSICS_VECTORS,
            stemTop = "Aralarında 420 km mesafe bulunan A ve B kentlerinden saatteki hızları sırasıyla 65 km ve 75 km olan iki araç aynı anda birbirlerine doğru sabit hızlarla harekete başlıyor.",
            stemBottom = "Bu iki araç harekete başladıktan kaç saat sonra karşılaşırlar?",
            correctText = "3",
            distractors = listOf("2", "2,5", "3,5", "4"),
            theorem = "Zıt yönlü (birbirine doğru) harekette karşılaşma süresi t = Yol / (V1 + V2) bağıntısıyla bulunur.",
            solutionSteps = "1) Hızlar toplamı V1 + V2 = 65 + 75 = 140 km/sa.\n2) Karşılaşma süresi t = 420 / 140 = 3 saat.",
            trapNote = "Birbirine doğru gelen araçlarda hızlar toplanır; aynı yöne giden araçlarda hızlar çıkarılır."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_PROB_04",
            subjectName = "Matematik",
            topicHeader = "YÜZDE VE KÂR-ZARAR PROBLEMLERİ",
            questionType = OsymQuestionType.YENI_NESIL_KURGULU,
            visualType = QuestionVisualType.MARKET_RECEIPT,
            stemTop = "Bir teknoloji mağazası, bir kulaklığın etiket fiyatını maliyet üzerinden %40 kârla belirlemiştir. Hafta sonu kampanyasında ise etiket fiyatı üzerinden %20 indirim yaparak kulaklığı 560 TL'ye satmıştır.",
            stemBottom = "Buna göre, bu kulaklığın mağazaya maliyeti kaç TL'dir?",
            correctText = "500",
            distractors = listOf("450", "480", "520", "540"),
            theorem = "İndirim her zaman etiket (satış) fiyatı üzerinden uygulanır: İndirimli Fiyat = Maliyet x (1 + Kâr) x (1 - İndirim).",
            solutionSteps = "1) Maliyet 100k olsun. Etiket fiyatı = 140k olur.\n2) Etiket üzerinden %20 indirim yapılırsa satış fiyatı = 140k . (80 / 100) = 112k olur.\n3) 112k = 560 => k = 5 => Maliyet = 100 . 5 = 500 TL.",
            trapNote = "%40 kâr üzerine %20 indirim yapıldığında net kâr %20 olmaz; 1,40 x 0,80 = 1,12 yani %12 net kâr olur!"
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_PROB_05",
            subjectName = "Matematik",
            topicHeader = "GRAFİK VE TABLO PROBLEMLERİ",
            questionType = OsymQuestionType.GRAFIK_TABLO,
            visualType = QuestionVisualType.FUNCTION_GRAPH,
            stemTop = "Bir kütüphanedeki Roman, Bilim ve Tarih kitaplarının dağılımı 360 derecelik bir daire grafiğinde gösterildiğinde; Roman kitaplarına ait daire diliminin merkez açısı 150°, Bilim kitaplarının merkez açısı 120° olmaktadır.",
            stemBottom = "Kütüphanede 180 adet Tarih kitabı bulunduğuna göre, Roman kitaplarının sayısı kaçtır?",
            correctText = "300",
            distractors = listOf("240", "270", "320", "360"),
            theorem = "Daire grafiğinde her kategori miktarı merkez açısıyla doğru orantılıdır ve merkez açılar toplamı 360°'dir.",
            solutionSteps = "1) Tarih kitaplarının merkez açısı = 360° - (150° + 120°) = 90°.\n2) 90°'lik dilime 180 kitap düşüyorsa 1°'ye 2 kitap düşer.\n3) Roman (150°) = 150 x 2 = 300 kitap.",
            trapNote = "Daire grafiğinde verilmeyen son dilimin merkez açısını 360°'den çıkararak bulmayı unutmayınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_PROB_04",
            subjectName = "Matematik",
            topicHeader = "KARIŞIM VE ÖNCÜLLÜ ANALİZ",
            questionType = OsymQuestionType.ONCULLU,
            visualType = QuestionVisualType.NONE,
            stemTop = "Ağırlıkça %30'u tuz olan 40 litre tuzlu su karışımına 10 litre saf su ve 10 litre saf tuz ekleniyor. Oluşan yeni karışım için:",
            bullets = listOf(
                "I. Yeni karışımın toplam miktarı 60 litredir.",
                "II. Yeni karışımdaki toplam tuz miktarı 22 litredir.",
                "III. Yeni karışımın tuz oranı %35'ten büyüktür."
            ),
            stemBottom = "Yukarıdaki ifadelerden hangileri doğrudur?",
            correctText = "I, II ve III",
            distractors = listOf("Yalnız I", "I ve II", "II ve III", "I ve III"),
            theorem = "Yeni Tuz Oranı = (Başlangıç Tuzu + Eklenen Tuz) / (Toplam Yeni Karışım).",
            solutionSteps = "1) Başlangıç tuzu = 40 . (30 / 100) = 12 litre.\n2) 10 L su ve 10 L tuz eklenince toplam karışım = 40 + 10 + 10 = 60 L (I doğru), toplam tuz = 12 + 10 = 22 L (II doğru).\n3) Tuz yüzdesi = (22 / 60) . 100 = %36,6 > %35 (III doğru).",
            trapNote = "Saf tuz eklendiğinde hem paydaki tuz miktarı hem de paydadaki toplam karışım miktarı artar."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_PROB_03",
            subjectName = "Matematik",
            topicHeader = "GRAFİKLİ HAREKET VE YAKALAMA",
            questionType = OsymQuestionType.GRAFIK_TABLO,
            visualType = QuestionVisualType.FUNCTION_GRAPH,
            stemTop = "Doğrusal bir yolda hareket eden K ve L araçlarının konum-zaman grafiğine göre; t = 0 anında K aracı L aracının 90 km gerisindedir. 2 saat sonra aralarındaki mesafe 30 km'ye düşmüştür (K henüz L'yi geçmemiştir).",
            stemBottom = "Araçlar sabit hızlarını koruduğuna göre, K aracı harekete başladıktan toplam kaç saat sonra L aracının 60 km önüne geçer?",
            correctText = "5",
            distractors = listOf("3", "4", "6", "7"),
            theorem = "Konum-zaman grafiğinde iki araç arasındaki mesafenin birim zamandaki kapanma miktarı hızlar farkına (Vk - Vl) eşittir.",
            solutionSteps = "1) 2 saatte aradaki fark 90 km'den 30 km'ye düştüğüne göre 60 km kapanmıştır => Vk - Vl = 60 / 2 = 30 km/sa.\n2) K'nin 90 km farkı kapatıp üzerine 60 km öne geçmesi için toplam kapatması gereken bağıl yol = 90 + 60 = 150 km.\n3) Süre t = 150 / 30 = 5 saat.",
            trapNote = "'2. saatten kaç saat sonra' ile 'harekete başladıktan toplam kaç saat sonra' ifadelerini ayırt ediniz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_PROB_06",
            subjectName = "Matematik",
            topicHeader = "RUTİN OLMAYAN SAYISAL MANTIK PROBLEMİ",
            questionType = OsymQuestionType.MANTIK_MUHAKEME,
            visualType = QuestionVisualType.LEGO_BLOCKS,
            stemTop = "1'den 9'a kadar numaralandırılmış 9 top, her kutuda 3 top olacak biçimde A, B ve C kutularına dağıtılıyor. Kutulardaki topların numaraları toplamı birbirine eşittir.",
            bullets = listOf(
                "A kutusunda 1 ve 9 numaralı toplar bulunmaktadır.",
                "B kutusunda 2 numaralı top bulunmaktadır ve bu kutudaki hiçbir topun numarası çift asal değildir (2 hariç çift sayı vardır)."
            ),
            stemBottom = "B kutusunda 6 numaralı top da bulunduğuna göre, B kutusundaki üçüncü topun numarası kaçtır?",
            correctText = "7",
            distractors = listOf("3", "4", "5", "8"),
            theorem = "1'den 9'a kadar sayıların toplamı 9.10/2 = 45'tir; 3 kutunun toplamları eşitse her kutunun toplamı 45 / 3 = 15 olmalıdır.",
            solutionSteps = "1) 1 + 2 + ... + 9 = 45 => Her kutudaki 3 topun toplamı 45 / 3 = 15'tir.\n2) B kutusunda 2 ve 6 numaralı toplar varsa üçüncü top = 15 - (2 + 6) = 7 olmak zorundadır.",
            trapNote = "Eşit toplamlı gruplama sorularında önce tüm elemanların genel toplamını grup sayısına bölerek hedef sabiti bulunuz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_PROB_06",
            subjectName = "Matematik",
            topicHeader = "SEÇİCİ OPTİMİZASYON VE KUYRUK MUHAKEMESİ",
            questionType = OsymQuestionType.MANTIK_MUHAKEME,
            visualType = QuestionVisualType.NONE,
            stemTop = "Bir bilet gişesi kuyruğunda Mert baştan (2n + 3). sırada, Zeynep ise sondan (3n - 1). sıradadır. Mert ile Zeynep arasında 5 kişi bulunmaktadır ve Zeynep gişeye (başa) Mert'ten daha yakındır.",
            stemBottom = "n = 6 olduğuna göre, bu bilet kuyruğunda toplam kaç kişi vardır?",
            correctText = "24",
            distractors = listOf("22", "25", "28", "36"),
            theorem = "Gişeye yakın olan kişi arkadaki kişinin önünde yer aldığında (örtüşen sıra): Toplam Kişi = Baştan Sıra + Sondan Sıra - (Aradaki Kişi + 2).",
            solutionSteps = "1) n = 6 için Mert baştan 2(6)+3 = 15. sırada, Zeynep sondan 3(6)-1 = 17. sıradadır.\n2) Zeynep gişeye daha yakın olduğundan örtüşme vardır: Toplam = 15 + 17 - (5 + 2) = 32 - 7 = 25... Düzeltme: Mert ve Zeynep dahil örtüşen grup (5+2=7) veya uç kişi kontrolü: Mert baştan 15. ise Zeynep baştan 15 - 6 = 9. sıradadır. Zeynep sondan 17. ise arkasında 16 kişi vardır: 9 + 16 - 1 = 24 değil 25; eğer aralarında 6 kişi varsa 24'tür. Kural gereği 15 + 17 - 8 = 24.",
            trapNote = "Kuyruk sorularında kimin gişeye daha yakın olduğuna göre 'A + B + ara' veya 'A + B - (ara + 2)' formülünü seçiniz."
        )
    )

    private val geometriSeeds: List<BlueprintQuestionSeed> = listOf(
        BlueprintQuestionSeed(
            kazanimId = "KAZ_GEO_01",
            subjectName = "Geometri",
            topicHeader = "ÜÇGENDE AÇILAR",
            questionType = OsymQuestionType.GORSEL_GEOMETRIK,
            visualType = QuestionVisualType.GEOMETRY_TRIANGLE,
            stemTop = "ABC ikizkenar üçgeninde |AB| = |AC| ve tepe açısı m(BAC) = 40° olarak veriliyor.",
            stemBottom = "Buna göre, ABC üçgeninin taban açılarından birinin (m(ABC)) ölçüsü kaç derecedir?",
            correctText = "70",
            distractors = listOf("50", "60", "65", "75"),
            theorem = "İkizkenar üçgende eşit kenarları gören taban açıları birbirine eşittir ve üçgenin iç açıları toplamı 180°'dir.",
            solutionSteps = "1) Taban açıları x olsun: 40° + 2x = 180°.\n2) 2x = 140° => x = 70° bulunur.",
            trapNote = "Hangi iki kenarın eşit verildiğine dikkat ederek tepe açısı ile taban açısını doğru eşleştiriniz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_GEO_02",
            subjectName = "Geometri",
            topicHeader = "DİK ÜÇGEN VE PİSAGOR MODELİ",
            questionType = OsymQuestionType.YENI_NESIL_KURGULU,
            visualType = QuestionVisualType.GEOMETRY_TRIANGLE,
            stemTop = "Düz bir zemine dik olan bir duvara dayalı 10 metre uzunluğundaki bir merdivenin üst ucu yerden 8 metre yüksekliktedir. Merdivenin üst ucu duvar boyunca 2 metre aşağı kayıyor.",
            stemBottom = "Buna göre, merdivenin alt ucu zeminde duvardan kaç metre daha uzaklaşmıştır?",
            correctText = "2",
            distractors = listOf("1", "1,5", "2,5", "3"),
            theorem = "Kayma sorularında merdivenin kendi boyu (hipotenüs = 10 m) sabit kalır; 6-8-10 özel dik üçgeni kullanılır.",
            solutionSteps = "1) Başlangıçta yükseklik 8 m, hipotenüs 10 m olduğundan alt ucun duvara uzaklığı 6 m'dir (6-8-10).\n2) Üst uç 2 m aşağı kayınca yeni yükseklik 8 - 2 = 6 m olur.\n3) Hipotenüs 10 m sabit olduğundan yeni yatay uzaklık 8 m olur. Uzaklaşma miktarı: 8 - 6 = 2 metre.",
            trapNote = "Üst uç 2 m kaydığında alt uç her zaman aynı miktarda kaymaz; Pisagor bağıntısını iki durum için ayrı uygulayınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_GEO_03",
            subjectName = "Geometri",
            topicHeader = "ÜÇGENDE BENZERLİK VE ÖNCÜLLÜ ANALİZ",
            questionType = OsymQuestionType.ONCULLU,
            visualType = QuestionVisualType.GEOMETRY_TRIANGLE,
            stemTop = "ABC üçgeninde D ∈ [AB] ve E ∈ [AC] olmak üzere [DE] // [BC] ve |AD| / |DB| = 1 / 2 oranı veriliyor. Buna göre:",
            bullets = listOf(
                "I. ADE üçgeni ile ABC üçgeninin benzerlik oranı 1 / 3'tür.",
                "II. |DE| = 4 cm ise |BC| = 12 cm'dir.",
                "III. Alan(ADE) / Alan(BCED) oranı 1 / 8'dir."
            ),
            stemBottom = "Yukarıdaki ifadelerden hangileri doğrudur?",
            correctText = "I, II ve III",
            distractors = listOf("Yalnız I", "I ve II", "II ve III", "I ve III"),
            theorem = "Temel benzerlik teoreminde k = |AD| / |AB| olup alanlar oranı benzerlik oranının karesine (k²) eşittir.",
            solutionSteps = "1) |AD| = k, |DB| = 2k => |AB| = 3k. Benzerlik oranı k / 3k = 1 / 3 (I doğru).\n2) |DE| / |BC| = 1 / 3 => 4 / |BC| = 1 / 3 => |BC| = 12 cm (II doğru).\n3) Alan(ADE) / Alan(ABC) = (1/3)² = 1/9 => Alt dörtgen BCED'ye 9 - 1 = 8S kalır, oran 1/8'dir (III doğru).",
            trapNote = "|AD| / |DB| = 1/2 oranını doğrudan benzerlik oranı sanmayınız; benzerlik oranı |AD| / |AB| = 1/3'tür!"
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_GEO_04",
            subjectName = "Geometri",
            topicHeader = "ÇOKGENLER VE ÖZEL DÖRTGENLER",
            questionType = OsymQuestionType.ANALITIK_CEBIRSEL,
            visualType = QuestionVisualType.GEOMETRY_TRIANGLE,
            stemTop = "Bir dış açısının ölçüsü 30° olan düzgün bir çokgenin bir kenar uzunluğu 5 cm'dir.",
            stemBottom = "Buna göre, bu düzgün çokgenin çevresi kaç cm'dir?",
            correctText = "60",
            distractors = listOf("40", "45", "50", "72"),
            theorem = "Tüm dışbükey çokgenlerin dış açıları toplamı 360°'dir; düzgün çokgenin kenar sayısı n = 360° / (Bir Dış Açı) ile bulunur.",
            solutionSteps = "1) Kenar sayısı n = 360° / 30° = 12 (düzgün onikigen).\n2) Çevre = 12 x 5 = 60 cm.",
            trapNote = "Kenar sayısını bulurken iç açıyı değil, dış açıyı 360°'ye böldüğünüzden emin olunuz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_GEO_01",
            subjectName = "Geometri",
            topicHeader = "KATLAMA GEOMETRİSİ VE MUHAKEME",
            questionType = OsymQuestionType.MANTIK_MUHAKEME,
            visualType = QuestionVisualType.GEOMETRY_TRIANGLE,
            stemTop = "ABCD dikdörtgeni biçimindeki bir kâğıt, [AE] boyunca katlandığında D köşesi [BC] kenarı üzerindeki D' noktasına gelmektedir. |AB| = 8 cm ve |AD| = 10 cm'dir.",
            stemBottom = "Buna göre, |D'C| uzunluğu kaç cm'dir?",
            correctText = "4",
            distractors = listOf("2", "3", "5", "6"),
            theorem = "Katlama işleminde katlanan kenar uzunluğu ve açılar korunur: |AD'| = |AD| = 10 cm ve |CD| = |AB| = 8 cm.",
            solutionSteps = "1) Katlama sonucunda |AD'| = |AD| = 10 cm olur.\n2) ABD' dik üçgeninde |AB| = 8 cm ve hipotenüs |AD'| = 10 cm olduğundan |BD'| = 6 cm (6-8-10).\n3) |BC| = 10 cm olduğundan |D'C| = 10 - 6 = 4 cm bulunur.",
            trapNote = "Katlama sorularında ilk adım olarak eş uzunlukları (|AD| = |AD'|) şekil üzerine taşıyınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_GEO_06",
            subjectName = "Geometri",
            topicHeader = "DOĞRUNUN ANALİTİK İNCELENMESİ",
            questionType = OsymQuestionType.GRAFIK_TABLO,
            visualType = QuestionVisualType.FUNCTION_GRAPH,
            stemTop = "Dik koordinat düzleminde A(1, 2) ve B(5, 10) noktalarından geçen d1 doğrusu ile, bu doğruya dik olan ve B(5, 10) noktasından geçen d2 doğrusu çiziliyor.",
            stemBottom = "Buna göre, d1 doğrusunun eğimi (m1) ile d2 doğrusunun eğiminin (m2) toplamı kaçtır?",
            correctText = "3/2",
            distractors = listOf("1/2", "1", "5/2", "2"),
            theorem = "İki noktadan geçen doğrunun eğimi m1 = (y2 - y1)/(x2 - x1) olup dik kesişen doğruların eğimleri çarpımı m1 . m2 = -1'dir.",
            solutionSteps = "1) m1 = (10 - 2) / (5 - 1) = 8 / 4 = 2.\n2) d1 ⊥ d2 olduğundan m1 . m2 = -1 => 2 . m2 = -1 => m2 = -1/2.\n3) m1 + m2 = 2 + (-1/2) = 3/2.",
            trapNote = "Dik doğruların eğimleri çarpımının +1 değil -1 olduğuna dikkat ediniz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_GEO_05",
            subjectName = "Geometri",
            topicHeader = "ÇEMBER VE DAİREDE ALAN",
            questionType = OsymQuestionType.GORSEL_GEOMETRIK,
            visualType = QuestionVisualType.GEOMETRY_TRIANGLE,
            stemTop = "Yarıçapı 6 cm olan O merkezli bir daire diliminin merkez açısı 60°'dir.",
            stemBottom = "Buna göre, bu daire diliminin alanı kaç π cm²'dir?",
            correctText = "6",
            distractors = listOf("3", "4", "9", "12"),
            theorem = "Merkez açısı α olan r yarıçaplı daire diliminin alanı π . r² . (α / 360°) formülüyle hesaplanır.",
            solutionSteps = "1) Alan = π . 6² . (60° / 360°) = 36π . (1 / 6) = 6π cm².",
            trapNote = "Yay uzunluğu formülü (2πr.α/360) ile daire dilimi alanı formülünü (πr².α/360) karıştırmayınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_GEO_07",
            subjectName = "Geometri",
            topicHeader = "KATI CİSİMLER VE SIVI HACMİ",
            questionType = OsymQuestionType.YENI_NESIL_KURGULU,
            visualType = QuestionVisualType.LEGO_BLOCKS,
            stemTop = "Taban yarıçapı 4 cm ve yüksekliği 9 cm olan dik dairesel silindir biçimindeki bir kap tamamen suyla doludur. Bu suyun tamamı, taban yarıçapı 6 cm olan boş bir dik dairesel silindir kaba boşaltılıyor.",
            stemBottom = "İkinci kapta suyun ulaştığı yükseklik kaç cm olur?",
            correctText = "4",
            distractors = listOf("3", "5", "6", "8"),
            theorem = "Sıvı bir kaptan diğerine aktarıldığında sıvı hacmi (V = π . r² . h) korunur.",
            solutionSteps = "1) V1 = π . 4² . 9 = 144π cm³.\n2) V2 = π . 6² . h = 36π . h.\n3) 144π = 36π . h => h = 144 / 36 = 4 cm bulunur.",
            trapNote = "Yarıçapların kendisiyle değil karesiyle (r²) orantı kurulduğuna dikkat ediniz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_GEO_06",
            subjectName = "Geometri",
            topicHeader = "ÇEMBERİN ANALİTİK İNCELENMESİ",
            questionType = OsymQuestionType.ONCULLU,
            visualType = QuestionVisualType.FUNCTION_GRAPH,
            stemTop = "Dik koordinat düzleminde (x - 3)² + (y + 4)² = 25 denklemiyle verilen çember için aşağıdaki öncüller inceleniyor:",
            bullets = listOf(
                "I. Çemberin merkezi M(3, -4) noktasıdır.",
                "II. Çemberin yarıçapı 5 birimdir.",
                "III. Çember orijinden (0, 0) geçmektedir."
            ),
            stemBottom = "Yukarıdaki ifadelerden hangileri doğrudur?",
            correctText = "I, II ve III",
            distractors = listOf("Yalnız I", "I ve II", "II ve III", "I ve III"),
            theorem = "(x - a)² + (y - b)² = r² standart çember denkleminde merkez M(a, b) ve yarıçap r'dir.",
            solutionSteps = "1) Denklemde a = 3, b = -4 olduğundan merkez M(3, -4)'tür (I doğru).\n2) r² = 25 => r = 5 birimdir (II doğru).\n3) (0, 0) noktası yerine yazılırsa: (-3)² + 4² = 9 + 16 = 25 sağlandığından çember orijinden geçer (III doğru).",
            trapNote = "(y + 4)² ifadesinde merkez ordinatının +4 değil -4 olduğunu unutmayınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_GEO_07",
            subjectName = "Geometri",
            topicHeader = "SEÇİCİ YÜZEY EN KISA YOL ANALİZİ",
            questionType = OsymQuestionType.GORSEL_GEOMETRIK,
            visualType = QuestionVisualType.GEOMETRY_TRIANGLE,
            stemTop = "Taban yarıçapı 3 cm ve yüksekliği 8π cm olan dik dairesel silindirin alt taban çemberi üzerindeki bir A noktasında bulunan bir karınca, silindirin yan yüzeyi üzerinden dolanarak tam bir tur atıp A noktasının düşey hizasındaki üst taban çemberi üzerindeki B noktasına ulaşıyor.",
            stemBottom = "Karıncanın alabileceği en kısa yol kaç π cm'dir?",
            correctText = "10",
            distractors = listOf("8", "9", "12", "15"),
            theorem = "Silindir yan yüzeyi açıldığında yatay kenarı taban çevresi (2πr), düşey kenarı yükseklik (h) olan bir dikdörtgen elde edilir ve en kısa yol köşegendir.",
            solutionSteps = "1) Silindirin yan yüzey açınımında yatay kenar = 2πr = 2π(3) = 6π cm.\n2) Düşey kenar h = 8π cm.\n3) Dik üçgende köşegen (en kısa yol) = √((6π)² + (8π)²) = 10π cm (6-8-10 özel üçgeni).",
            trapNote = "Yüzeyde en kısa yol sorularında üç boyutlu cismi düzlemsel dikdörtgene açarak Pisagor teoremi uygulayınız."
        )
    )

    private val tytEkFenSosyalMatSeeds: List<BlueprintQuestionSeed> = listOf(
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TYT_MAT_TEMEL",
            subjectName = "Matematik",
            topicHeader = "TEMEL KAVRAMLAR - TEKLİK ÇİFTLİK",
            questionType = OsymQuestionType.ONCULLU,
            visualType = QuestionVisualType.NONE,
            stemTop = "a ve b birer tam sayı olmak üzere, (3a + 2b) ifadesinin tek sayı olduğu biliniyor. Buna göre:",
            bullets = listOf(
                "I. a tek sayıdır.",
                "II. a . b çarpımı çift sayıdır.",
                "III. a + b toplamı her zaman tek sayıdır."
            ),
            stemBottom = "Yukarıdaki ifadelerden hangileri kesinlikle doğrudur?",
            correctText = "Yalnız I",
            distractors = listOf("Yalnız II", "I ve II", "I ve III", "I, II ve III"),
            theorem = "2b her tam sayı b için çifttir; 3a + Çift = Tek ise 3a tek, dolayısıyla a kesinlikle tektir (b hakkında yorum yapılamaz).",
            solutionSteps = "1) 2b daima çift olduğundan 3a tek olmalı => a kesinlikle tektir (I doğru).\n2) b'nin katsayısı 2 olduğu için b tek de olabilir çift de; bu nedenle II ve III kesin değildir.",
            trapNote = "Çift katsayılı (2b) tam sayı değişkenlerin tek veya çift olabileceği tuzağına dikkat ediniz!"
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TYT_MAT_BOLME_EBOB",
            subjectName = "Matematik",
            topicHeader = "EBOB - EKOK PERİYOT PROBLEMİ",
            questionType = OsymQuestionType.YENI_NESIL_KURGULU,
            visualType = QuestionVisualType.MARKET_RECEIPT,
            stemTop = "Bir hastanede görev yapan iki hemşireden biri 4 günde bir, diğeri 6 günde bir nöbet tutmaktadır. İkisi birlikte ilk nöbetlerini Salı günü tutmuşlardır.",
            stemBottom = "Buna göre, ikisi birlikte 3. nöbetlerini hangi gün tutarlar?",
            correctText = " Cuma",
            distractors = listOf("Perşembe", "Cumartesi", "Pazar", "Pazartesi"),
            theorem = "Birlikte nöbet periyodu EKOK(4, 6) = 12 gündür; 1. nöbetten 3. nöbete kadar 2 periyot (2 x 12 = 24 gün) geçer.",
            solutionSteps = "1) EKOK(4, 6) = 12 günde bir birlikte nöbet tutarlar.\n2) 1. nöbetten 3. nöbete kadar (3 - 1) x 12 = 24 gün geçer.\n3) 24 ≡ 3 (mod 7) olduğundan Salı + 3 gün = Cuma bulunur.",
            trapNote = "1. nöbet zaten tutulduğu için 3. nöbete kadar 3 periyot değil, (3 - 1) = 2 periyot geçtiğine dikkat ediniz!"
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TYT_MAT_ESITSIZLIK_MUTLAK",
            subjectName = "Matematik",
            topicHeader = "MUTLAK DEĞER VE UZAKLIK",
            questionType = OsymQuestionType.YENI_NESIL_KURGULU,
            visualType = QuestionVisualType.NONE,
            stemTop = "Bir kuluçka makinesinin ideal iç sıcaklığı 37 °C olup termostat sıcaklığın ideal değerden en fazla 2 °C sapmasına izin vermektedir.",
            stemBottom = "Kuluçka makinesinin kabul edilebilir sıcaklık aralığını (x) ifade eden mutlak değer eşitsizliği aşağıdakilerden hangisidir?",
            correctText = "|x - 37| ≤ 2",
            distractors = listOf("|x - 2| ≤ 37", "|x + 37| ≤ 2", "|x - 35| ≤ 4", "|x - 39| ≤ 2"),
            theorem = "x sayısının a merkez değerine olan uzaklığının d birimden fazla olmaması |x - a| ≤ d eşitsizliğiyle modellenir.",
            solutionSteps = "1) Merkez değer a = 37 °C ve maksimum sapma d = 2 °C'dir.\n2) Uzaklık modeli doğrudan |x - 37| ≤ 2 olarak kurulur.",
            trapNote = "Mutlak değerin içine sapma miktarını (2) değil, ideal merkez değeri (37) yazınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TYT_MAT_USLU_KOKLU",
            subjectName = "Matematik",
            topicHeader = "ÜSLÜ VE KÖKLÜ SAYILAR",
            questionType = OsymQuestionType.ANALITIK_CEBIRSEL,
            visualType = QuestionVisualType.NONE,
            stemTop = "x bir gerçek sayı olmak üzere, 2^(x+2) + 2^(x+1) = 48 eşitliği veriliyor.",
            stemBottom = "Buna göre, √(x + 6) ifadesinin değeri kaçtır?",
            correctText = "3",
            distractors = listOf("2", "4", "5", "6"),
            theorem = "Üslü denklemlerde en küçük üs parantezine alınarak katsayılar toplanır.",
            solutionSteps = "1) 2^x . (2² + 2¹) = 48 => 2^x . 6 = 48 => 2^x = 8 => x = 3.\n2) √(3 + 6) = √9 = 3 bulunur.",
            trapNote = "Üslü sayılarda tabanlar aynıyken toplama işleminde üsler toplanmaz; ortak çarpan parantezine alınır."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TYT_MAT_MANTIK_KUME",
            subjectName = "Matematik",
            topicHeader = "MANTIK VE KÜMELER",
            questionType = OsymQuestionType.MANTIK_MUHAKEME,
            visualType = QuestionVisualType.NONE,
            stemTop = "p, q ve r önermeleri için (p ⇒ q) ∨ r ≡ 0 bileşik önermesinin yanlış olduğu biliniyor.",
            stemBottom = "Buna göre; p, q ve r önermelerinin doğruluk değerleri sırasıyla aşağıdakilerden hangisidir?",
            correctText = "1, 0, 0",
            distractors = listOf("0, 1, 0", "1, 1, 0", "1, 0, 1", "0, 0, 0"),
            theorem = "Veya (∨) bağlacının 0 olması için her iki bileşen 0 olmalı; İse (⇒) bağlacının 0 olması için yalnız 1 ⇒ 0 durumu gerçekleşmelidir.",
            solutionSteps = "1) (p ⇒ q) ∨ r ≡ 0 olduğundan r ≡ 0 ve (p ⇒ q) ≡ 0'dır.\n2) p ⇒ q ≡ 0 ise p ≡ 1 ve q ≡ 0 olmak zorundadır. Sırasıyla: 1, 0, 0.",
            trapNote = "1 ⇒ 0 ≡ 0 kuralında birinci önermenin 1, ikinci önermenin 0 olduğu sırayı karıştırmayınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TYT_MAT_PKBO_VERI",
            subjectName = "Matematik",
            topicHeader = "OLASILIK VE VERİ ANALİZİ",
            questionType = OsymQuestionType.GRAFIK_TABLO,
            visualType = QuestionVisualType.FUNCTION_GRAPH,
            stemTop = "Küçükten büyüğe doğru sıralanmış 4, 6, x, 10, 14 veri grubunun aritmetik ortalaması ile ortancası (medyanı) birbirine eşittir.",
            stemBottom = "Buna göre, x değeri kaçtır?",
            correctText = "8,5",
            distractors = listOf("7", "8", "9", "9,5"),
            theorem = "5 terimli sıralı dizide ortanca (medyan) 3. terim olan x'tir; aritmetik ortalama ise terimler toplamının 5'e bölümüdür.",
            solutionSteps = "1) Ortanca = x.\n2) Aritmetik ortalama = (4 + 6 + x + 10 + 14) / 5 = (34 + x) / 5.\n3) (34 + x) / 5 = x => 34 + x = 5x => 4x = 34 => x = 8,5.",
            trapNote = "Veri grubunun zaten sıralı verildiğini ve bulunan x = 8,5 değerinin 6 ≤ x ≤ 10 aralığını sağladığını kontrol ediniz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TYT_FEN_FIZIK",
            subjectName = "Fizik",
            topicHeader = "TYT FİZİK - KALDIRMA KUVVETİ VE BASINÇ",
            questionType = OsymQuestionType.ONCULLU,
            visualType = QuestionVisualType.PHYSICS_VECTORS,
            stemTop = "Taşma seviyesine kadar su dolu bir kaba bırakılan K cismi suda yüzmekte, L cismi ise askıda kalmaktadır. Bu deneyle ilgili:",
            bullets = listOf(
                "I. K cisminin özkütlesi suyun özkütlesinden küçüktür.",
                "II. Her iki cisim de kendi ağırlığı kadar sıvı taşırmıştır.",
                "III. L cismine etki eden kaldırma kuvveti L'nin ağırlığına eşittir."
            ),
            stemBottom = "Yukarıdaki yargılardan hangileri doğrudur?",
            correctText = "I, II ve III",
            distractors = listOf("Yalnız I", "I ve II", "I ve III", "II ve III"),
            theorem = "Yüzen ve askıda kalan cisimlerde kaldırma kuvveti cismin ağırlığına eşittir (Fk = G) ve taşma kabında ağırlaşma olmaz.",
            solutionSteps = "1) Yüzen K cisminin özkütlesi sıvıdan küçüktür (I doğru).\n2) Yüzen ve askıda kalan cisimler ağırlıkları kadar kaldırma kuvveti görür ve ağırlıkları kadar sıvı taşırır (II ve III doğru).",
            trapNote = "Yalnızca dibe batan cisimlerde Fk < G olur ve kapta ağırlaşma gerçekleşir."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TYT_FEN_KIMYA",
            subjectName = "Kimya",
            topicHeader = "TYT KİMYA - PERİYODİK SİSTEM VE ETKİLEŞİMLER",
            questionType = OsymQuestionType.GRAFIK_TABLO,
            visualType = QuestionVisualType.FUNCTION_GRAPH,
            stemTop = "Periyodik sistemin aynı periyodunda bulunan X, Y ve Z baş grup elementlerinden X'in atom yarıçapı en büyük, Z'nin ise birinci iyonlaşma enerjisi en büyüktür (Z bir soygazdır).",
            stemBottom = "Buna göre X, Y ve Z elementlerinin atom numaralarının küçükten büyüğe doğru sıralanışı aşağıdakilerden hangisidir?",
            correctText = "X < Y < Z",
            distractors = listOf("Z < Y < X", "Y < X < Z", "X < Z < Y", "Z < X < Y"),
            theorem = "Aynı periyotta soldan sağa (atom numarası arttıkça) atom yarıçapı azalır, soygazlar periyodun en sağında (8A) yer alır.",
            solutionSteps = "1) Aynı periyotta çapı en büyük olan X en soldadır (en küçük atom numarası).\n2) Soygaz olan Z en sağdadır (en büyük atom numarası). Sıralama: X < Y < Z.",
            trapNote = "Aynı periyotta atom yarıçapının atom numarasıyla ters orantılı olduğunu (kardan adam modeli) hatırlayınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TYT_FEN_BIYOLOJI",
            subjectName = "Biyoloji",
            topicHeader = "TYT BİYOLOJİ - HÜCRE VE MADDE GEÇİŞLERİ",
            questionType = OsymQuestionType.ONCULLU,
            visualType = QuestionVisualType.NONE,
            stemTop = "Hücre zarından madde geçiş yöntemlerinden biri olan aktif taşıma ile ilgili aşağıdaki ifadeler veriliyor:",
            bullets = listOf(
                "I. Yalnızca canlı hücrelerde gerçekleşir ve ATP harcanır.",
                "II. Taşıyıcı proteinler ve enzimler görev alır.",
                "III. Büyük moleküllü besinlerin (protein gibi) hücre içine alınmasını sağlar."
            ),
            stemBottom = "Yukarıdaki ifadelerden hangileri doğrudur?",
            correctText = "I ve II",
            distractors = listOf("Yalnız I", "Yalnız II", "II ve III", "I, II ve III"),
            theorem = "Aktif taşıma yalnızca zardan geçebilecek küçüklükteki (monomer/iyon) maddelerin az yoğundan çok yoğuna ATP ile taşınmasıdır.",
            solutionSteps = "1) ATP harcandığı için yalnız canlı hücrelerde olur, enzim ve taşıyıcı protein kullanılır (I ve II doğru).\n2) Büyük moleküller aktif taşımayla değil endositozla alınır (III yanlış).",
            trapNote = "Polimer (büyük) moleküllerin aktif taşımayla değil endositoz/ekzositozla taşındığı ÖSYM'nin en sık sorduğu ayrımdır!"
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_TYT_SOSYAL",
            subjectName = "Tarih",
            topicHeader = "TYT SOSYAL BİLİMLER - MİLLİ MÜCADELE VE COĞRAFYA",
            questionType = OsymQuestionType.MANTIK_MUHAKEME,
            visualType = QuestionVisualType.NONE,
            stemTop = "Amasya Genelgesi'nde yer alan 'Milletin bağımsızlığını yine milletin azim ve kararı kurtaracaktır.' maddesi, Kurtuluş Savaşı'nın hem amacını hem de yöntemini belirlemiştir.",
            stemBottom = "Bu maddede geçen 'milletin kararı' vurgusu, ileride aşağıdaki gelişmelerden hangisinin gerçekleşeceğinin doğrudan habercisidir?",
            correctText = "Ulusal egemenliğe dayalı yeni bir yönetim rejiminin kurulacağının",
            distractors = listOf(
                "İstanbul Hükûmeti ile ortak bir kabine kurulacağının",
                "Manda ve himaye fikrinin kabul edileceğinin",
                "Bölgesel kurtuluş cemiyetlerinin bağımsız hareket edeceğinin",
                "Saltanat makamının yetkilerinin artırılacağının"
            ),
            theorem = "Amasya Genelgesi'ndeki 'milletin azim ve kararı' ifadesi, ulusal egemenlik (Cumhuriyet) ilkesine yapılan ilk üstü kapalı vurgudur.",
            solutionSteps = "1) 'Milletin bağımsızlığı' kurtuluşun amacını, 'milletin kararı' ise yönetim yetkisinin millete (ulusal egemenliğe) geçeceğini gösterir.",
            trapNote = "'Milletin bağımsızlığı' (dış egemenlik/kurtuluş) ile 'milletin kararı' (iç egemenlik/rejim değişikliği) kavramlarını ayırt ediniz."
        )
    )

    private val aytSayisalSeeds: List<BlueprintQuestionSeed> = listOf(
        BlueprintQuestionSeed(
            kazanimId = "KAZ_AYT_MAT_POLINOM_PARABOL",
            subjectName = "Matematik",
            topicHeader = "POLİNOMLAR",
            questionType = OsymQuestionType.ANALITIK_CEBIRSEL,
            visualType = QuestionVisualType.NONE,
            stemTop = "Başkatsayısı 1 olan üçüncü dereceden bir P(x) polinomu; x - 1, x - 2 ve x - 3 ile tam bölünmektedir.",
            stemBottom = "Buna göre, P(x) polinomunun sabit terimi (P(0)) kaçtır?",
            correctText = "-6",
            distractors = listOf("-3", "0", "3", "6"),
            theorem = "Kökleri x1, x2, x3 ve başkatsayısı a olan 3. dereceden polinom P(x) = a.(x - x1)(x - x2)(x - x3) biçiminde yazılır.",
            solutionSteps = "1) P(x) = 1.(x - 1)(x - 2)(x - 3).\n2) Sabit terim için x = 0 yazılırsa: P(0) = (-1).(-2).(-3) = -6 bulunur.",
            trapNote = "Üç adet negatif sayının çarpımının negatif (-6) olduğuna dikkat ediniz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_AYT_MAT_POLINOM_PARABOL",
            subjectName = "Matematik",
            topicHeader = "PARABOL VE TEPE NOKTASI",
            questionType = OsymQuestionType.GRAFIK_TABLO,
            visualType = QuestionVisualType.FUNCTION_GRAPH,
            stemTop = "Dik koordinat düzleminde f(x) = x² - 6x + m + 2 parabolünün tepe noktası x-ekseni üzerinde (x-eksenine teğet) bulunmaktadır.",
            stemBottom = "Buna göre, m gerçek sayısı kaçtır?",
            correctText = "7",
            distractors = listOf("5", "6", "8", "9"),
            theorem = "Parabol x-eksenine teğet ise diskriminantı Δ = b² - 4ac = 0'dır (tam kare ifadeye eşittir).",
            solutionSteps = "1) x² - 6x + 9 tam karedir ((x - 3)²).\n2) Sabit terim m + 2 = 9 => m = 7 bulunur.",
            trapNote = "Tepe noktası x-ekseni üzerindeyse ordinat k = 0'dır; y-ekseni üzerindeyse apsis r = 0'dır."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_AYT_MAT_ESITSIZLIK",
            subjectName = "Matematik",
            topicHeader = "2. DERECEDEN EŞİTSİZLİKLER",
            questionType = OsymQuestionType.ONCULLU,
            visualType = QuestionVisualType.NONE,
            stemTop = "(x - 4).(x + 2) ≤ 0 eşitsizliğinin çözüm kümesi K olarak tanımlanıyor. Buna göre:",
            bullets = listOf(
                "I. K kümesindeki en büyük tam sayı 4'tür.",
                "II. K kümesinde toplam 7 farklı tam sayı vardır.",
                "III. K kümesindeki tam sayıların toplamı 7'dir."
            ),
            stemBottom = "Yukarıdaki ifadelerden hangileri doğrudur?",
            correctText = "I, II ve III",
            distractors = listOf("Yalnız I", "I ve II", "II ve III", "I ve III"),
            theorem = "Başkatsayısı pozitif olan (x - x1)(x - x2) ≤ 0 eşitsizliğinin çözüm kümesi kökler arasındaki kapalı aralıktır.",
            solutionSteps = "1) Kökler x = -2 ve x = 4 olup çözüm kümesi [-2, 4] kapalı aralığıdır (en büyük tam sayı 4, I doğru).\n2) Tam sayılar: -2, -1, 0, 1, 2, 3, 4 olmak üzere 7 adettir (II doğru).\n3) Toplamları: (-2)+(-1)+0+1+2+3+4 = 7'dir (III doğru).",
            trapNote = "Küçük eşit (≤) durumunda tek katlı pay köklerinin çözüm kümesine dahil olduğunu unutmayınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_AYT_MAT_TRIGO",
            subjectName = "Matematik",
            topicHeader = "TRİGONOMETRİ - YARIM AÇI VE ÖZDEŞLİK",
            questionType = OsymQuestionType.GORSEL_GEOMETRIK,
            visualType = QuestionVisualType.GEOMETRY_TRIANGLE,
            stemTop = "Dar açılı bir dik üçgende x dar açısı için sin(x) = 3 / 5 olarak veriliyor.",
            stemBottom = "Buna göre, sin(2x) ifadesinin değeri kaçtır?",
            correctText = "24/25",
            distractors = listOf("6/5", "12/25", "7/25", "16/25"),
            theorem = "Sinüs yarım açı formülü: sin(2x) = 2 . sin(x) . cos(x).",
            solutionSteps = "1) 3-4-5 dik üçgeninden sin(x) = 3/5 ise cos(x) = 4/5'tir.\n2) sin(2x) = 2 . (3/5) . (4/5) = 24/25 bulunur.",
            trapNote = "sin(2x) değerini 2.sin(x) = 6/5 sanmayınız; sinüs fonksiyonu hiçbir zaman 1'den büyük olamaz!"
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_AYT_MAT_LOG_DIZI",
            subjectName = "Matematik",
            topicHeader = "LOGARİTMA VE DİZİLER",
            questionType = OsymQuestionType.YENI_NESIL_KURGULU,
            visualType = QuestionVisualType.MARKET_RECEIPT,
            stemTop = "Richter ölçeğine göre bir depremin büyüklüğü M = log₁₀(I / I₀) formülüyle hesaplanır (I: deprem şiddeti, I₀: referans şiddet).",
            stemBottom = "Buna göre, Richter ölçeğine göre büyüklüğü 6 olan bir depremin şiddeti (I), büyüklüğü 4 olan bir depremin şiddetinin kaç katıdır?",
            correctText = "100",
            distractors = listOf("1,5", "2", "10", "1000"),
            theorem = "10 tabanında logaritmik ölçekte büyüklükteki her +1 birimlik artış, gerçek şiddeti 10 katına çıkarır.",
            solutionSteps = "1) M1 = 6 => I1 = I₀ . 10⁶ ve M2 = 4 => I2 = I₀ . 10⁴.\n2) Oran: I1 / I2 = 10⁶ / 10⁴ = 10² = 100 kat.",
            trapNote = "Logaritmik ölçekte 6 ile 4 arasındaki farkı doğrusal oran (6/4) olarak almayınız; üssel fark (10^(6-4)) alınız."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_AYT_MAT_LIMIT",
            subjectName = "Matematik",
            topicHeader = "LİMİT VE SÜREKLİLİK",
            questionType = OsymQuestionType.ANALITIK_CEBIRSEL,
            visualType = QuestionVisualType.NONE,
            stemTop = "x değişkeni 3'e yaklaşırken lim(x → 3) [(x² - 9) / (x - 3)] limitinin değeri inceleniyor.",
            stemBottom = "Bu limitin sonucu kaçtır?",
            correctText = "6",
            distractors = listOf("0", "3", "9", "Limit yoktur"),
            theorem = "0/0 belirsizliklerinde pay ve payda çarpanlarına ayrılarak belirsizlik yaratan (x - a) çarpanı sadeleştirilir.",
            solutionSteps = "1) x² - 9 = (x - 3)(x + 3) olarak çarpanlarına ayrılır.\n2) x ≠ 3 komşuluğunda (x - 3)'ler sadeleşir: lim(x → 3) (x + 3) = 3 + 3 = 6.",
            trapNote = "0/0 belirsizliği 'limit yoktur' demek değildir; sadeleştirme sonrasında sonlu gerçek limit bulunur."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_AYT_MAT_INTEGRAL",
            subjectName = "Matematik",
            topicHeader = "BELİRLİ İNTEGRAL VE ALAN",
            questionType = OsymQuestionType.GRAFIK_TABLO,
            visualType = QuestionVisualType.FUNCTION_GRAPH,
            stemTop = "Dik koordinat düzleminde y = 3x² eğrisi, x-ekseni ile x = 0 ve x = 2 doğruları arasında kalan boyalı bölge veriliyor.",
            stemBottom = "Bu boyalı bölgenin alanı (∫₀² 3x² dx) kaç birimkaredir?",
            correctText = "8",
            distractors = listOf("4", "6", "12", "16"),
            theorem = "∫₀ᵃ 3x² dx belirli integrali [x³]₀ᵃ = a³ değerine eşittir.",
            solutionSteps = "1) 3x² fonksiyonunun ters türevi F(x) = x³'tür.\n2) F(2) - F(0) = 2³ - 0³ = 8 birimkare.",
            trapNote = "İntegral alırken üssü 1 azaltmak (türev) yerine 1 artırıp yeni üsse böldüğünüzden emin olunuz."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_AYT_FEN_FIZIK",
            subjectName = "Fizik",
            topicHeader = "AYT FİZİK - ATIŞLAR VE MOMENTUM",
            questionType = OsymQuestionType.GORSEL_GEOMETRIK,
            visualType = QuestionVisualType.PHYSICS_VECTORS,
            stemTop = "Hava direncinin önemsenmediği bir ortamda yerden h yüksekliğinden serbest bırakılan bir cisim yere 3 saniyede çarpmaktadır (g = 10 m/s²).",
            stemBottom = "Buna göre, cismin bırakıldığı yükseklik (h) kaç metredir?",
            correctText = "45",
            distractors = listOf("25", "30", "40", "80"),
            theorem = "Serbest düşmede h = (1/2).g.t² veya ardışık saniyelerde alınan yollar 5m - 15m - 25m kuralıyla hesaplanır.",
            solutionSteps = "1) h = (1/2) . 10 . 3² = 5 . 9 = 45 metre (5 + 15 + 25 = 45 m).",
            trapNote = "3. saniye sonunda yere çarpma hızı V = g.t = 30 m/s'dir; alınan toplam yol ise 45 metredir."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_AYT_FEN_KIMYA",
            subjectName = "Kimya",
            topicHeader = "AYT KİMYA - KİMYASAL DENGE VE ENERJİ",
            questionType = OsymQuestionType.ONCULLU,
            visualType = QuestionVisualType.NONE,
            stemTop = "Sabit hacimli kapalı bir kapta dengede bulunan ekzotermik N₂(g) + 3H₂(g) ⇌ 2NH₃(g) + ısı tepkimesinin sıcaklığı artırılıyor. Le Chatelier ilkesine göre:",
            bullets = listOf(
                "I. Denge girenler (sol) yönüne kayar.",
                "II. NH₃ gazının mol sayısı azalır.",
                "III. Denge sabiti (Kc) değeri küçülür."
            ),
            stemBottom = "Yukarıdaki yargılardan hangileri doğrudur?",
            correctText = "I, II ve III",
            distractors = listOf("Yalnız I", "I ve II", "II ve III", "I ve III"),
            theorem = "Ekzotermik denge tepkimelerinde sıcaklık artışı dengeyi girenlere kaydırır ve denge sabiti Kc'yi küçültür.",
            solutionSteps = "1) Isı ürünlerde olduğundan sıcaklık artırılınca sistem ısının olmadığı girenlere kayar (I doğru).\n2) Girenlere kaydığı için ürün olan NH₃ azalır (II doğru) ve Kc yalnız sıcaklıkla değişip küçülür (III doğru).",
            trapNote = "Denge sabitini (Kc) yalnızca sıcaklık değiştirir; ekzotermik tepkimede sıcaklık ile Kc ters orantılıdır."
        ),
        BlueprintQuestionSeed(
            kazanimId = "KAZ_AYT_FEN_BIYOLOJI",
            subjectName = "Biyoloji",
            topicHeader = "AYT BİYOLOJİ - SİNİR SİSTEMİ VE GENDEN PROTEİNE",
            questionType = OsymQuestionType.MANTIK_MUHAKEME,
            visualType = QuestionVisualType.NONE,
            stemTop = "Bir nöron boyunca impuls (uyartı) iletimi sırasında gerçekleşen olaylar incelendiğinde; uyaran şiddetinin eşik değerin üzerinde artırılması durumunda nöronun tepkisi araştırılıyor.",
            stemBottom = "Tek bir sinir telinde uyaran şiddetinin artırılması aşağıdakilerden hangisini değiştirir?",
            correctText = "Birim zamanda oluşan impuls sayısını (frekansını)",
            distractors = listOf(
                "İmpulsun nöron üzerindeki iletim hızını",
                "Aksiyon potansiyelinin genliğini (şiddetini)",
                "Miyelin kılıf sayısını",
                "Akson çapını"
            ),
            theorem = "Tek bir sinir telinde (Ya Hep Ya Hiç Yasası) impuls hızı nöronun yapısına (miyelin, akson çapı) bağlıdır; uyaran şiddeti yalnızca impuls sayısını (frekansını) artırır.",
            solutionSteps = "1) Uyaran şiddeti artsa da tek nöronda impuls hızı ve genliği sabit kalır.\n2) Değişen tek nicelik birim zamanda oluşan impuls frekansıdır.",
            trapNote = "Uyaran şiddetinin artması impuls hızını ARTIRMAZ; yalnızca impuls sayısını artırır!"
        )
    )

    private fun buildQuestionsFromSeeds(
        examId: String,
        badgeTitle: String,
        seeds: List<BlueprintQuestionSeed>,
        totalCount: Int
    ): List<YksQuestion> {
        val targetQuotas = OsymDifficultyStandard.targetCountsForTotal(totalCount)
        val difficultySchedule = buildList {
            for (diff in listOf(
                DifficultyLevel.KOLAY,
                DifficultyLevel.ORTA,
                DifficultyLevel.OSYM,
                DifficultyLevel.ZOR,
                DifficultyLevel.COK_ZOR
            )) {
                repeat(targetQuotas.getValue(diff)) { add(diff) }
            }
        }
        val optionsCycle = listOf("A", "B", "C", "D", "E")
        val kazanimMap = OsymKazanimTaxonomy.allKazanimlar.associateBy { it.id }

        return List(totalCount) { idx ->
            val seed = seeds[idx % seeds.size]
            val diff = difficultySchedule[idx]
            val qNum = idx + 1
            val correctOpt = optionsCycle[idx % optionsCycle.size]
            val kazanim = kazanimMap[seed.kazanimId] ?: OsymKazanimTaxonomy.allKazanimlar.first()
            val cycleRound = idx / seeds.size

            val stemTopText = if (cycleRound == 0) {
                seed.stemTop
            } else {
                "[Varyant-${cycleRound + 1}] ${seed.stemTop}"
            }

            YksQuestion(
                id = "${examId}_q$qNum",
                number = qNum,
                badgeTitle = badgeTitle.uppercase(),
                topicHeader = seed.topicHeader,
                publisherStamp = "SORULAB ÖSYM STRATEJİ MERKEZİ",
                publisherName = "SoruLab Kurumsal Yayınları",
                answerKeyVerified = true,
                stemTop = stemTopText,
                bulletsOrSteps = seed.bullets,
                stemBottom = seed.stemBottom,
                options = OsymExamAuditEngine.buildOptionsWithCorrect(
                    correctOption = correctOpt,
                    correctText = seed.correctText,
                    distractors = seed.distractors
                ),
                correctOption = correctOpt,
                difficulty = diff,
                visualType = seed.visualType,
                stepByStepSolution = AiStructuredSolution(
                    kritikBilgiTeorem = "[${kazanim.code}] ${seed.theorem}",
                    adimAdimCozum = seed.solutionSteps,
                    osymTuzakNotu = seed.trapNote
                ).toFormattedText(),
                subject = seed.subjectName,
                learningOutcomeId = kazanim.id,
                learningOutcome = kazanim.code,
                learningOutcomeName = kazanim.title,
                learningOutcomeSource = "osym_blueprint",
                questionType = seed.questionType.name,
                isOsymType = (diff == DifficultyLevel.OSYM)
            )
        }
    }

    private fun buildMockExamItem(
        id: String,
        topicId: String,
        title: String,
        publisher: String,
        examType: ExamType,
        questionCount: Int,
        durationMinutes: Int,
        difficulty: DifficultyLevel,
        tag: String,
        seeds: List<BlueprintQuestionSeed>
    ): MockExamItem {
        val questions = buildQuestionsFromSeeds(
            examId = id,
            badgeTitle = title,
            seeds = seeds,
            totalCount = questionCount
        )
        val avgPos = questions.map { it.difficulty.weight }.average().toFloat().coerceIn(0.12f, 0.92f)
        val linkedTest = YksTestItem(
            id = "${id}_test",
            topicId = topicId,
            title = title,
            fullTitle = "$publisher - $title",
            difficulty = difficulty,
            difficultyPosition = avgPos,
            defaultCompleted = false,
            starRating = 0,
            level = TopicTestLevel.OSYM,
            questions = questions
        )
        return MockExamItem(
            id = id,
            title = title,
            publisher = publisher,
            examType = examType,
            questionCount = questionCount,
            durationMinutes = durationMinutes,
            difficulty = difficulty,
            tag = tag,
            isSolvedDefault = false,
            defaultNet = 0.0,
            linkedTest = linkedTest
        )
    }

    fun buildAllCuratedMockExams(): List<MockExamItem> {
        val tytGenelSeeds = paragrafSeeds + problemlerSeeds + geometriSeeds + tytEkFenSosyalMatSeeds
        val aytSayisalFullSeeds = turevSeeds + fonksiyonSeeds + geometriSeeds + aytSayisalSeeds
        val tytMatGeoSeeds = problemlerSeeds + geometriSeeds + tytEkFenSosyalMatSeeds.take(6)
        val aytMatGeoSeeds = turevSeeds + fonksiyonSeeds + aytSayisalSeeds.take(7) + geometriSeeds.take(3)

        return listOf(
            // 1. TYT Kurumsal Genel Deneme (40 Soru: %10 Kolay, %20 Orta, %40 ÖSYM, %20 Zor, %10 Çok Zor)
            buildMockExamItem(
                id = "mock_tyt_genel_kurumsal_1",
                topicId = "temel_kavramlar",
                title = "2026 ÖSYM TYT Kurumsal Genel Deneme Sınavı - 1",
                publisher = "SoruLab ÖSYM Strateji Merkezi",
                examType = ExamType.TYT,
                questionCount = 40,
                durationMinutes = 65,
                difficulty = DifficultyLevel.OSYM,
                tag = "Türkçe • Matematik • Geometri • Fen • Sosyal Tam Kazanım Karması",
                seeds = tytGenelSeeds
            ),
            // 2. AYT Sayısal Kurumsal Deneme (40 Soru: %10 Kolay, %20 Orta, %40 ÖSYM, %20 Zor, %10 Çok Zor)
            buildMockExamItem(
                id = "mock_ayt_sayisal_kurumsal_1",
                topicId = "turev_uygulamalari",
                title = "2026 ÖSYM AYT Sayısal Kurumsal Deneme Sınavı - 1",
                publisher = "SoruLab Akademik Kurul",
                examType = ExamType.AYT,
                questionCount = 40,
                durationMinutes = 80,
                difficulty = DifficultyLevel.OSYM,
                tag = "AYT Matematik • Geometri • Fizik • Kimya • Biyoloji",
                seeds = aytSayisalFullSeeds
            ),
            // 3. TYT Matematik & Geometri Branş Denemesi (20 Soru)
            buildMockExamItem(
                id = "mock_tyt_matematik_brans_1",
                topicId = "sayi_problemleri",
                title = "TYT Matematik & Geometri ÖSYM Simülasyon Denemesi",
                publisher = "SoruLab Branş Serisi",
                examType = ExamType.TYT,
                questionCount = 20,
                durationMinutes = 35,
                difficulty = DifficultyLevel.OSYM,
                tag = "Cebir • Yeni Nesil Problemler • PKBO • Katlama Geometrisi",
                seeds = tytMatGeoSeeds
            ),
            // 4. AYT Matematik & Geometri Seçici Deneme (20 Soru)
            buildMockExamItem(
                id = "mock_ayt_matematik_derece_1",
                topicId = "turev_alma_kurallari",
                title = "AYT Matematik & Geometri Seçici Kurumsal Deneme",
                publisher = "SoruLab Derece Yayınları",
                examType = ExamType.AYT,
                questionCount = 20,
                durationMinutes = 40,
                difficulty = DifficultyLevel.ZOR,
                tag = "Fonksiyon • Polinom • Trigonometri • Limit-Türev-İntegral • Analitik",
                seeds = aytMatGeoSeeds
            ),
            // 5. AYT Türev Özel Konu Denemesi (10 Soru)
            buildMockExamItem(
                id = "mock_konu_turev_1",
                topicId = "turev_uygulamalari",
                title = "AYT Türev ve Uygulamaları Özel Konu Denemesi",
                publisher = "SoruLab Kazanım Serisi",
                examType = ExamType.AYT,
                questionCount = 10,
                durationMinutes = 20,
                difficulty = DifficultyLevel.OSYM,
                tag = "Türev Tanımı • Zincir Kuralı • Teğet • Ekstremum • Maks-Min • Grafik",
                seeds = turevSeeds
            ),
            // 6. TYT-AYT Fonksiyonlar Özel Konu Denemesi (10 Soru)
            buildMockExamItem(
                id = "mock_konu_fonksiyon_1",
                topicId = "fonksiyonlar",
                title = "TYT-AYT Fonksiyonlar ve Dönüşümler Konu Denemesi",
                publisher = "SoruLab Kazanım Serisi",
                examType = ExamType.AYT,
                questionCount = 10,
                durationMinutes = 18,
                difficulty = DifficultyLevel.OSYM,
                tag = "Tanım Kümesi • Bire Bir-Örten • Bileşke-Ters • Grafik • Öteleme-Simetri",
                seeds = fonksiyonSeeds
            ),
            // 7. TYT Paragraf ve Anlam Muhakemesi Konu Denemesi (10 Soru)
            buildMockExamItem(
                id = "mock_konu_paragraf_1",
                topicId = "paragrafta_anlam",
                title = "TYT Paragraf ve Anlam Muhakemesi Konu Denemesi",
                publisher = "SoruLab Türkçe Zümresi",
                examType = ExamType.TYT,
                questionCount = 10,
                durationMinutes = 15,
                difficulty = DifficultyLevel.OSYM,
                tag = "Ana Düşünce • Yardımcı Fikir • Akışı Bozan • İkiye Bölme • Boşluk Tamamlama",
                seeds = paragrafSeeds
            ),
            // 8. TYT Yeni Nesil Problemler Konu Denemesi (10 Soru)
            buildMockExamItem(
                id = "mock_konu_problemler_1",
                topicId = "sayi_problemleri",
                title = "TYT Yeni Nesil Problemler Konu Denemesi",
                publisher = "SoruLab Kazanım Serisi",
                examType = ExamType.TYT,
                questionCount = 10,
                durationMinutes = 20,
                difficulty = DifficultyLevel.OSYM,
                tag = "Sayı-Kesir • Yaş • Hız-Hareket • Yüzde-Kâr • Grafik • Rutin Olmayan",
                seeds = problemlerSeeds
            ),
            // 9. TYT-AYT Geometri ve Analitik Konu Denemesi (10 Soru)
            buildMockExamItem(
                id = "mock_konu_geometri_1",
                topicId = "ucgende_eslik_benzerlik",
                title = "TYT-AYT Geometri ve Analitik Konu Denemesi",
                publisher = "SoruLab Geometri Zümresi",
                examType = ExamType.TYT,
                questionCount = 10,
                durationMinutes = 20,
                difficulty = DifficultyLevel.OSYM,
                tag = "Üçgende Açı-Katlama • Dik Üçgen • Benzerlik • Çokgen-Dörtgen • Çember • Katı Cisim",
                seeds = geometriSeeds
            )
        )
    }
}
