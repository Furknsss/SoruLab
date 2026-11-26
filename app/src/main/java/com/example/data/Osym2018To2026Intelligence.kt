package com.example.data

import androidx.compose.runtime.Immutable
import org.json.JSONObject
import java.util.Locale

/**
 * 2018–2026 YKS (TYT & AYT) ÖSYM Müfredat İstihbarat Motoru.
 *
 * Kapsam:
 * - 2018–2026 yılları arasında TYT ve AYT oturumlarında her yıl düzenli sorulan yüksek getirili
 *   ("banko / her yıl çıkan") kazanımların tarihsel frekans ağırlıkları (`osymFrequencyWeight`),
 * - ÖSYM'nin 6 imza soru arketipi (Öncüllü Yorum, Yeni Nesil Kurgulu Senaryo, Grafik/Tablo Okuma,
 *   Deney/Gözlem Düzeneği, Geometrik Katlama/Kesme/Döndürme, Analitik/Cebirsel Muhakeme),
 * - Her konu için ÖSYM'nin neyi ölçtüğü (`whatOsymMeasures`) ve klasik ÖSYM çeldirici tuzakları (`commonOsymTrap`),
 * - Deneme bilgi ekranındaki `(i)` listesi için her `topicId + OsymQuestionType` ikilisine zengin,
 *   asla boş kalmayan insan-okunur kazanım başlığı ve temiz kitap/yayın çözümleyicisi,
 * - Öğrencinin geçmiş `TestResultRecord` hafızasından yanlış/boş bıraktığı kazanımları ve soru tiplerini
 *   çıkaran ustalık/zayıflık analizi.
 */
enum class OsymSignatureArchetype(
    val label: String,
    val shortBadge: String,
    val mappedQuestionType: OsymQuestionType,
    val description: String
) {
    ONCULLU_YORUM(
        label = "Öncüllü Yorum & Kesinlik (I-II-III)",
        shortBadge = "Öncüllü Yorum",
        mappedQuestionType = OsymQuestionType.ONCULLU,
        description = "I, II, III yargılarının her zaman/kesinlikle doğruluğunu veya kavramsal sınırlarını denetleyen ÖSYM kurgusu"
    ),
    YENI_NESIL_KURGULU_SENARYO(
        label = "Yeni Nesil Kurgulu Senaryo",
        shortBadge = "Yeni Nesil Senaryo",
        mappedQuestionType = OsymQuestionType.YENI_NESIL_KURGULU,
        description = "Günlük yaşam, gerçek veri veya hikayeleştirilmiş bağlamı matematiksel/bilimsel modele dönüştürme sorusu"
    ),
    GRAFIK_TABLO_OKUMA(
        label = "Grafik / Tablo ve Veri Okuma",
        shortBadge = "Grafik-Tablo",
        mappedQuestionType = OsymQuestionType.GRAFIK_TABLO,
        description = "Fonksiyon eğrisi, değişim grafiği, deney tablosu, harita veya nüfus/veri diyagramı yorumlama sorusu"
    ),
    DENEY_GOZLEM_DUZENEGI(
        label = "Deney / Gözlem Düzeneği ve Muhakeme",
        shortBadge = "Deney & Muhakeme",
        mappedQuestionType = OsymQuestionType.MANTIK_MUHAKEME,
        description = "Kontrollü deney düzeneği, metinsel çıkarım, sözel/sayısal akıl yürütme ve neden-sonuç muhakemesi"
    ),
    GEOMETRIK_KATLAMA_KESME_DONDURME(
        label = "Geometrik Katlama / Kesme / Döndürme",
        shortBadge = "Katlama & Görsel",
        mappedQuestionType = OsymQuestionType.GORSEL_GEOMETRIK,
        description = "Şeklin katlanması, kesilip taşınması, döndürülmesi veya üç boyutlu yüzey-hacim ilişkisini ölçen görsel kurgu"
    ),
    ANALITIK_CEBIRSEL_MUHAKEME(
        label = "Analitik / Cebirsel Muhakeme",
        shortBadge = "Analitik Muhakeme",
        mappedQuestionType = OsymQuestionType.ANALITIK_CEBIRSEL,
        description = "Kavramsal tanım, denklem kurma, teorem bağlantısı ve çok adımlı analitik çözüm becerisi ölçen kurgu"
    );

    companion object {
        fun fromQuestionType(type: OsymQuestionType): OsymSignatureArchetype = when (type) {
            OsymQuestionType.ONCULLU -> ONCULLU_YORUM
            OsymQuestionType.YENI_NESIL_KURGULU -> YENI_NESIL_KURGULU_SENARYO
            OsymQuestionType.GRAFIK_TABLO -> GRAFIK_TABLO_OKUMA
            OsymQuestionType.MANTIK_MUHAKEME -> DENEY_GOZLEM_DUZENEGI
            OsymQuestionType.GORSEL_GEOMETRIK -> GEOMETRIK_KATLAMA_KESME_DONDURME
            OsymQuestionType.ANALITIK_CEBIRSEL -> ANALITIK_CEBIRSEL_MUHAKEME
        }
    }
}

@Immutable
data class OsymOutcomeIntelligence(
    val id: String,
    val subjectId: String,
    val topicId: String,
    val title: String,
    val osymFrequencyWeight: Double,
    val yearsAskedCount2018To2026: Int,
    val preferredQuestionTypes: Set<OsymQuestionType>,
    val whatOsymMeasures: String,
    val commonTrap: String,
    val keywords: List<String> = emptyList()
) {
    val isEveryYearHighYield: Boolean
        get() = yearsAskedCount2018To2026 >= 7 || osymFrequencyWeight >= 4.0
}

@Immutable
data class OsymTopicIntelligenceProfile(
    val subjectId: String,
    val topicId: String,
    val topicName: String,
    val examType: ExamType,
    val historicalShareWeight: Double,
    val yearsAsked2018To2026: Int,
    val whatOsymMeasures: String,
    val commonOsymTrap: String,
    val signatureArchetypes: List<OsymSignatureArchetype>,
    val outcomes: List<OsymOutcomeIntelligence>
) {
    val isEveryYearCoreTopic: Boolean
        get() = yearsAsked2018To2026 >= 7 || historicalShareWeight >= 4.0
}

@Immutable
data class OsymResolvedQuestionMeta(
    val outcomeId: String,
    val outcomeCode: String,
    val outcomeTitle: String,
    val outcomeSource: String,
    val outcomeConfidence: Double,
    val questionType: OsymQuestionType,
    val archetype: OsymSignatureArchetype,
    val osymFrequencyWeight: Double,
    val cleanPublisherName: String,
    val cleanBookTitle: String
)

@Immutable
data class StudentOsymMasteryMemory(
    val missedOutcomeWeights: Map<String, Double> = emptyMap(),
    val missedTopicWeights: Map<String, Double> = emptyMap(),
    val missedQuestionTypeWeights: Map<OsymQuestionType, Double> = emptyMap(),
    val missedTopicTypeWeights: Map<Pair<String, OsymQuestionType>, Double> = emptyMap(),
    val masteredOutcomeKeys: Set<String> = emptySet()
) {
    fun missBoostForOutcome(outcomeId: String, outcomeTitle: String, topicId: String): Double {
        val normId = Osym2018To2026Intelligence.normalizeKey(outcomeId)
        val normTitle = Osym2018To2026Intelligence.normalizeKey(outcomeTitle)
        val normTopic = Osym2018To2026Intelligence.normalizeKey(topicId)
        val byId = if (normId.isNotBlank()) missedOutcomeWeights[normId] ?: 0.0 else 0.0
        val byTitle = if (normTitle.isNotBlank()) missedOutcomeWeights[normTitle] ?: 0.0 else 0.0
        val byTopic = if (normTopic.isNotBlank()) (missedTopicWeights[normTopic] ?: 0.0) * 0.35 else 0.0
        return maxOf(byId, byTitle) + byTopic
    }

    fun missBoostForQuestionType(topicId: String, questionType: OsymQuestionType): Double {
        val normTopic = Osym2018To2026Intelligence.normalizeKey(topicId)
        val topicSpecific = missedTopicTypeWeights[normTopic to questionType] ?: 0.0
        val globalType = (missedQuestionTypeWeights[questionType] ?: 0.0) * 0.25
        return topicSpecific + globalType
    }
}

object Osym2018To2026Intelligence {

    const val REGISTRY_VERSION = "OSYM-2018-2026-v1"

    private val trLocale: Locale = Locale.forLanguageTag("tr-TR")

    fun normalizeKey(raw: String): String = raw.trim()
        .lowercase(trLocale)
        .replace('ı', 'i')
        .replace('ğ', 'g')
        .replace('ü', 'u')
        .replace('ş', 's')
        .replace('ö', 'o')
        .replace('ç', 'c')
        .replace(Regex("""\s+"""), " ")

    private data class OutcomeSeed(
        val suffix: String,
        val title: String,
        val weight: Double,
        val years: Int,
        val types: Set<OsymQuestionType>,
        val keywords: List<String> = emptyList()
    )

    private data class CuratedTopicSeed(
        val subjectId: String,
        val topicId: String,
        val topicName: String,
        val shareWeight: Double,
        val yearsAsked: Int,
        val measures: String,
        val trap: String,
        val outcomes: List<OutcomeSeed>
    )

    private fun out(
        suffix: String,
        title: String,
        weight: Double,
        years: Int,
        vararg types: OsymQuestionType,
        keywords: List<String> = emptyList()
    ) = OutcomeSeed(
        suffix = suffix,
        title = title,
        weight = weight,
        years = years,
        types = if (types.isEmpty()) setOf(OsymQuestionType.ANALITIK_CEBIRSEL) else types.toSet(),
        keywords = keywords
    )

    private val curatedSeeds: List<CuratedTopicSeed> = listOf(
        // ==================== TYT TÜRKÇE (2018-2026) ====================
        CuratedTopicSeed(
            subjectId = "tyt_turkce",
            topicId = "paragrafta_anlam",
            topicName = "Paragrafta Anlam",
            shareWeight = 5.0,
            yearsAsked = 9,
            measures = "Yoğun akademik/felsefi metinlerde ana düşünceyi saptama, yardımcı fikirleri eleme ve yazarın tutumundan kesin çıkarım yapma.",
            trap = "Metinde geçen doğru ama kapsayıcı olmayan yardımcı düşünceyi 'asıl anlatılmak istenen' sanma çeldiricisi.",
            outcomes = listOf(
                out("ANA_DUSUNCE", "Paragrafta Asıl Anlatılmak İstenen ve Ana Düşünce", 5.0, 9, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("asıl anlatılmak istenen", "ana düşünce", "vurgulanan")),
                out("YARDIMCI_FIKIR", "Paragrafta Değinilmemiştir / Ulaşılamaz Çıkarım Analizi", 5.0, 9, OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("değinilmemiştir", "çıkarılamaz", "ulaşılamaz", "söylenemez")),
                out("DIALOG_MUHABIR", "Gazeteci - Yazar Diyaloğu ve Soru-Cevap Eşleştirme", 4.6, 8, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("gazeteci", "muhabir", "diyalog", "sorularından hangisine karşılık")),
                out("IKI_PARAGRAF_KARSILASTIRMA", "İki Parçanın Ortak Yönü ve Bakış Açısı Karşılaştırması", 4.3, 7, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU, keywords = listOf("iki parça", "ortak", "farklı üslup")),
                out("PARAGRAF_KESIN_YARGI", "Metinden Kesin Olarak Çıkarılabilecek Öncüllü Yargılar", 4.4, 8, OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("hangilerine ulaşılabilir", "hangileri çıkarılabilir"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_turkce",
            topicId = "paragrafta_yapi",
            topicName = "Paragrafta Yapı",
            shareWeight = 4.8,
            yearsAsked = 9,
            measures = "Metin içi gönderim öğelerini, düşünce akışı kırılmalarını ve boşluk tamamlamada bağlam uyumunu yakalama.",
            trap = "Konu aynı kalırken bakış açısının değiştiği cümleyi fark edemeyip bir sonraki cümleyi işaretleme.",
            outcomes = listOf(
                out("AKISI_BOZAN", "Düşüncenin Akışını Bozan Cümleyi Belirleme", 4.9, 9, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("akışı bozan", "düşüncenin akışını")),
                out("IKIYE_BOLME", "Parçayı İki Paragrafa Ayırma Noktası Analizi", 4.8, 9, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("iki paragrafa", "ikinci paragraf")),
                out("BOSLUK_TAMAMLAMA", "Paragrafta Boş Bırakılan Yere Bağlama Uygun Cümle Getirme", 4.8, 9, OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("boş bırakılan yere", "parçanın sonuna", "getirilmelidir")),
                out("CUMLE_YER_DEGISTIRME", "Anlam Bütünlüğü İçin Numaralanmış Cümlelerin Yerini Değiştirme", 4.4, 8, OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("yer değiştirirse", "numaralanmış cümle"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_turkce",
            topicId = "sozcukte_anlam",
            topicName = "Sözcükte ve Söz Öbeğinde Anlam",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Sözcüğün bağlamsal anlamını, parantez içi anlam örtüşmesini ve altı çizili söz öbeğinin mecaz göndermesini çözümleme.",
            trap = "Sözcüğün sözlükteki ilk gerçek anlamını bağlamdaki mecaz/yan anlamın önüne koyma.",
            outcomes = listOf(
                out("BOSLUK_SOZCUK", "Cümlede Boş Bırakılan Yerlere Bağlama Uygun Sözcük Yerleştirme", 5.0, 9, OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("boş bırakılan yerlere", "sırasıyla")),
                out("AYRAC_ICI_ANLAM", "Parantez İçi Açıklama ile Sözcük Anlamı Uyumu", 4.7, 9, OsymQuestionType.ONCULLU, keywords = listOf("ayraç içinde", "anlamıyla uyuşmamaktadır")),
                out("ALTI_CIZILI_SOZ", "Altı Çizili Söz Öbeğinin Parçaya Kattığı Mecaz Anlam", 4.9, 9, OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("altı çizili söz", "anlatılmak istenen"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_turkce",
            topicId = "cumlede_anlam",
            topicName = "Cümlede Anlam",
            shareWeight = 4.8,
            yearsAsked = 9,
            measures = "İki öncül cümlenin eksiksiz ve yorum katmadan birleştirilmesi ile neden-sonuç / amaç-sonuç / koşul ayrımlarını ölçme.",
            trap = "İki cümleyi birleştirirken metinde olmayan örtülü bir neden-sonuç bağlacı ekleme.",
            outcomes = listOf(
                out("IKI_CUMLE_BIRLESTIRME", "İki Cümlenin Anlamca Doğru ve Eksiksiz Birleştirilmesi", 4.9, 9, OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("anlamca doğru bir biçimde birleştirilmiş", "iki cümle")),
                out("NEDEN_AMAC_KOSUL", "Cümleler Arası Neden-Sonuç, Amaç-Sonuç ve Koşul İlişkisi", 4.6, 8, OsymQuestionType.ONCULLU, keywords = listOf("neden-sonuç", "amaç-sonuç", "koşul", "karşılaştırma")),
                out("YAKIN_ANLAM_KESIN_YARGI", "Numaralanmış Cümlelerde Yakın Anlam ve Kesin Yargı Tespiti", 4.5, 8, OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("anlamca birbirine en yakın", "kesin olarak çıkarabilecek"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_turkce",
            topicId = "yazim_kurallari",
            topicName = "Yazım Kuralları",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Bitişik/ayrı yazılan birleşik kelimeler, büyük harf kullanımı, 'de/ki' yazımı ve kısaltma eklerini TDK kılavuzuna göre denetleme.",
            trap = "İkinci kelimesinde anlam kaybı olmayan birleşik sözcükleri kalıplaşmış sanarak bitişik yazma.",
            outcomes = listOf(
                out("BIRLESIK_SOZCUK_YAZIMI", "Birleşik Sözcüklerin Bitişik ve Ayrı Yazımı (Anlam Kaybı Kuralı)", 5.0, 9, OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("birleşik", "bitişik", "ayrı", "yazımı yanlıştır")),
                out("BUYUK_HARF_TARIH_SAYI", "Büyük Harflerin, Tarihlerin, Kısaltmaların ve Eklerin Yazımı", 4.8, 9, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("büyük harf", "kısaltma", "yazım yanlışı")),
                out("DE_KI_BAGLAC_YAZIMI", "Bağlaç Olan 'da/de' ve 'ki' ile Ek Olan '-de/-ki' Ayrımı", 4.4, 8, OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("bağlaç", "bulunma durumu"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_turkce",
            topicId = "noktalama_isaretleri",
            topicName = "Noktalama İşaretleri",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Virgülün kullanılmaz olduğu yerleri, noktalı virgülün tür/takım ayırma işlevini ve iki nokta / üç nokta ayrımını ölçme.",
            trap = "Zarf-fiil ekinden, şart kipinden veya bağlaçtan hemen sonra virgül koyma hatası.",
            outcomes = listOf(
                out("VIRGUL_NOKTALI_VIRGUL", "Virgül ve Noktalı Virgülün Görev ve Kullanılamazlık Kuralları", 5.0, 9, OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("virgül", "noktalı virgül", "kullanılamaz")),
                out("AYRAC_BOSLUK_NOKTALAMA", "Parçada Yay Ayraçla Belirtilen Yerlere Noktalama İşareti Getirme", 4.9, 9, OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("yay ayraç", "sırasıyla hangi noktalama")),
                out("IKI_NOKTA_KESME_TIRNAK", "İki Nokta, Kısa Çizgi, Tırnak ve Kesme İşareti İşlevleri", 4.3, 8, OsymQuestionType.ONCULLU, keywords = listOf("iki nokta", "kesme işareti", "kısa çizgi"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_turkce",
            topicId = "ses_bilgisi",
            topicName = "Ses Bilgisi",
            shareWeight = 4.4,
            yearsAsked = 8,
            measures = "Ünlü düşmesi, ünsüz benzeşmesi, ünsüz yumuşaması, ünlü daralması ve ünsüz türemesini ek-kök ayrımıyla bulma.",
            trap = "Yapım eki alırken türetme sırasında oluşan ünlü düşmesini veya 'diye/yiyecek' daralmalarını gözden kaçırma.",
            outcomes = listOf(
                out("UNLU_DUSMESI_DARALMASI", "Ünlü Düşmesi, Ünlü Daralması ve Ünlü Türemesi Tespiti", 4.6, 8, OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("ünlü düşmesi", "ünlü daralması", "ünlü türemesi")),
                out("UNSUZ_BENZESME_YUMUSAMA", "Ünsüz Benzeşmesi (Sertleşme) ve Ünsüz Yumuşaması Analizi", 4.6, 8, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("ünsüz benzeşmesi", "ünsüz yumuşaması", "ses olayı"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_turkce",
            topicId = "cumlenin_ogeleri",
            topicName = "Cümlenin Ögeleri",
            shareWeight = 4.6,
            yearsAsked = 9,
            measures = "İsim/sıfat tamlamalarını ve fiilimsi gruplarını bölmeden özne, nesne, yer tamlayıcısı ve zarf tümleci dizilişini çıkarma.",
            trap = "Uzun sıfat-fiil öbeklerini ortadan bölerek yan cümlenin nesnesini temel cümlenin ögesi sanma.",
            outcomes = listOf(
                out("OGE_DIZILISI", "Tamlamaları Bölmeden Cümlenin Öge Dizilişini Belirleme", 4.8, 9, OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU, keywords = listOf("ögelerinin sıralanışı", "özne", "yüklem", "nesne")),
                out("VURGULANAN_ARA_SOZ", "Cümlede Vurgulanan Öge ve Ara Söz Açıklayıcısı Tespiti", 4.2, 7, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("vurgulanan", "ara söz", "açıklayıcısı"))
            )
        ),

        // ==================== TYT MATEMATİK (2018-2026) ====================
        CuratedTopicSeed(
            subjectId = "tyt_mat",
            topicId = "temel_kavramlar",
            topicName = "Temel Kavramlar",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Tam sayılarda teklik-çiftlik kesinliği, pozitif-negatif işaret tablosu, asal çarpanlar ve ardışık sayı örüntülerini muhakeme etme.",
            trap = "'a ve b birer gerçek sayı' dendiğinde tam sayı değeri vererek çift katsayılı terimlere tek/çift muamelesi yapma.",
            outcomes = listOf(
                out("TEKLIK_CIFTLIK", "Tam Sayılarda Teklik - Çiftlik Kesinlik Muhakemesi", 5.0, 9, OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("tek sayı", "çift sayı", "her zaman", "kesinlikle")),
                out("POZITIF_NEGATIF_ISARET", "Sayı Kümelerinde Pozitiflik - Negatiflik ve Sıralama Analizi", 4.6, 8, OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("sıfırdan farklı", "pozitif", "negatif", "işaret")),
                out("ASAL_ARDISIK_ORUNTU", "Asal Sayılar, Faktöriyel ve Ardışık Kutu/Tablo Örüntüleri", 4.7, 9, OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("asal", "ardışık", "faktöriyel", "kutulara"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_mat",
            topicId = "sayi_basamaklari",
            topicName = "Sayı Basamakları",
            shareWeight = 4.7,
            yearsAsked = 9,
            measures = "İki ve üç basamaklı doğal sayıların basamak çözümlemesini yeni nesil rakam yer değiştirme veya sayaç senaryolarına uygulama.",
            trap = "Rakamları birbirinden farklı veya sıfırdan farklı şartını gözden kaçırarak sınır değer seçme.",
            outcomes = listOf(
                out("BASAMAK_COZUMLEME", "İki ve Üç Basamaklı Sayılarda Basamak Çözümlemesi", 4.8, 9, OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("iki basamaklı", "üç basamaklı", "rakamları toplamı")),
                out("RAKAM_DEGISIM_KURGU", "Rakam Yer Değiştirme ve Hatalı Hesaplama Senaryoları", 4.5, 8, OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("yer değiştirdiğinde", "rakam", "basamağı"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_mat",
            topicId = "mutlak_deger",
            topicName = "Mutlak Değer",
            shareWeight = 4.8,
            yearsAsked = 9,
            measures = "Mutlak değeri sayı doğrusunda iki nokta arası uzaklık olarak modelleme ve tolerans/sapma eşitsizliklerini kurma.",
            trap = "Mutlak değer denkleminden çıkan köklerin başlangıçtaki sağ taraf pozitiflik şartını (≥ 0) sağlayıp sağlamadığını denetlememe.",
            outcomes = listOf(
                out("SAYI_DOGRUSU_UZAKLIK", "Sayı Doğrusunda Uzaklık ve Tolerans Aralığı Modellemesi", 4.9, 9, OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("uzaklık", "sayı doğrusu", "en fazla", "sapma")),
                out("MUTLAK_DENKLEM_ESITSIZLIK", "Mutlak Değerli Denklem, Eşitsizlik ve Öncüllü İşaret Çıkarımı", 4.6, 8, OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("mutlak değer", "|", "eşitliğini sağlayan"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_mat",
            topicId = "sayi_problemleri",
            topicName = "Sayı Problemleri",
            shareWeight = 5.0,
            yearsAsked = 9,
            measures = "Çok değişkenli günlük yaşam metnini en az bilinmeyenli doğrusal denkleme çevirme ve tablo/bilet/sıra kurgularını çözme.",
            trap = "İstenen nicelik yerine denklemde tanımlanan ara x değişkenini şıklarda işaretleme.",
            outcomes = listOf(
                out("DOGRUSAL_MODELLEME", "Çok Aşamalı Günlük Yaşam Senaryosunu Denklemle Modelleme", 5.0, 9, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("her birine", "toplam", "başlangıçta", "kalan")),
                out("TABLO_DAGILIM_SAYI", "Eksik Verili Tablo ve Kategori Dağılım Problemleri", 4.8, 9, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("tablo", "grup", "kişi sayısı")),
                out("SIRA_KUYRUK_PAYLASIM", "Kuyruk, Merdiven, Eşit Paylaşım ve Paketleme Muhakemesi", 4.5, 8, OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU, keywords = listOf("sıra", "bilet", "kutu", "eşit"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_mat",
            topicId = "hareket_problemleri",
            topicName = "Hız ve Hareket Problemleri",
            shareWeight = 4.7,
            yearsAsked = 9,
            measures = "Bağıl karşılaşma/yetişme sürelerini, dairesel pist periyotlarını ve yol-zaman/hız-zaman grafiklerini yorumlama.",
            trap = "Birim dönüşümünü (dakika ↔ saat, metre ↔ km) atlama veya ortalama hızı hızların aritmetik ortalaması sanma.",
            outcomes = listOf(
                out("KARSILASMA_YETISME", "Zıt ve Aynı Yönlü Hareket, Karşılaşma ve Ortak Zaman Analizi", 4.8, 9, OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("hız", "karşılaş", "aynı anda", "pist")),
                out("YOL_ZAMAN_GRAFIGI", "Yol-Zaman / Yakıt-Yol Doğrusal Grafik Okuma ve Ortalama Hız", 4.7, 8, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("grafik", "ortalama hız", "km/sa", "dakika"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_mat",
            topicId = "yuzde_kar_zarar_problemleri",
            topicName = "Yüzde ve Kâr-Zarar Problemleri",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Ardışık kampanya indirimlerini, fire/büyüme oranlarını ve maliyet-satış yüzdelerini 100x ölçeklemesiyle çözme.",
            trap = "Etiket fiyatı üzerinden yapılan indirimi maliyet fiyatı üzerinden hesaplama.",
            outcomes = listOf(
                out("KAMPANYA_ISKONTO", "Kampanya, Etiket Fiyatı ve Ardışık Yüzde Değişim Modellemesi", 4.9, 9, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("indirim", "kampanya", "etiket", "kâr", "zarar")),
                out("YUZDE_TABLO_VERI", "Sektörel Yüzde Dağılımı ve Maliyet-Gelir Tablo Yorumu", 4.6, 8, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU, keywords = listOf("yüzde", "%", "maliyet", "tablo"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_mat",
            topicId = "grafik_problemleri",
            topicName = "Grafik Problemleri",
            shareWeight = 4.8,
            yearsAsked = 9,
            measures = "Daire grafiği merkez açıları ile sütun/çizgi grafiği sayısal verileri arasında oransal geçiş kurma.",
            trap = "Daire grafiğindeki dereceyi doğrudan yüzde sanma (360° ↔ %100 dönüşümünü unutma).",
            outcomes = listOf(
                out("DAIRE_SUTUN_GECIS", "Daire Grafiği Merkez Açısı ve Sütun Grafiği Oransal Geçişi", 4.9, 9, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("daire grafiği", "sütun grafiği", "merkez açı", "dağılım")),
                out("DEGISIM_CIZGI_YORUM", "Zamana Bağlı Çizgi Grafiği ve Öncüllü Veri Karşılaştırması", 4.5, 8, OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("çizgi grafiği", "artış", "oran"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_mat",
            topicId = "rutin_olmayan_problemler",
            topicName = "Rutin Olmayan Problemler",
            shareWeight = 4.8,
            yearsAsked = 9,
            measures = "İlk kez karşılaşılan kural/oyun/şifreleme tanımını hızlıca kavrayıp uç durum (en az / en çok) optimizasyonu yapma.",
            trap = "Verilen örnek durumu kuralın tamamı sanıp sınır koşullarını test etmeme.",
            outcomes = listOf(
                out("OYUN_ALGORITMA_KURAL", "Yeni Tanımlanan Kural, Şifreleme ve Algoritmik Muhakeme", 4.9, 9, OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("kural", "tanımlanıyor", "adım", "şifre")),
                out("EN_AZ_EN_COK_STRATEJI", "Güvercin Yuvası İlkesi ve En Az / En Çok Durum Optimizasyonu", 4.7, 9, OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.ONCULLU, keywords = listOf("en az kaç", "en çok kaç", "kesinlikle"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_mat",
            topicId = "tyt_fonksiyonlar",
            topicName = "Fonksiyonlar",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "f(x) ve g(x) grafiklerinden bileşke/ters görüntü okuma, parçalı fonksiyon ve f(a)=b eşitsizlik kesişimlerini bulma.",
            trap = "Grafikte y = f(x) yerine y = f(x+a) veya (fog)(x) sorulduğunda içten dışa okuma sırasını ters çevirme.",
            outcomes = listOf(
                out("GRAFIKTEN_BILESKE_TERS", "Fonksiyon Grafiğinden Bileşke (fog) ve Ters Görüntü Okuma", 5.0, 9, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("grafik", "fog", "gof", "ters", "koordinat")),
                out("FONKSIYON_ONCUL_KESINLIK", "Tanım-Görüntü Kümesi, Bire Birlik ve Öncüllü Fonksiyon Yorumu", 4.7, 8, OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("tanım kümesi", "görüntü kümesi", "bire bir", "örten")),
                out("FONKSIYON_CEBIRSEL_KURAL", "Doğrusal ve Parçalı Fonksiyon Kuralı Kurma", 4.5, 8, OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("doğrusal fonksiyon", "parçalı", "f(x)"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_mat",
            topicId = "olasilik",
            topicName = "Olasılık",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Eş olası örnek uzayı eksiksiz sayarak istenen koşulu sağlayan durum sayısını kombinatorik olarak oranlama.",
            trap = "Özdeş olmayan nesneleri özdeş sayarak örnek uzay paydasını eksik hesaplama.",
            outcomes = listOf(
                out("GUNLUK_SECIM_OLASILIK", "Günlük Yaşam Seçim ve Yerleştirme Senaryolarında Olasılık", 5.0, 9, OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("olasılığı kaçtır", "rastgele", "seçilen")),
                out("GEOMETRIK_TABLO_OLASILIK", "Tablo, Hedef Tahtası ve Koşullu Kısıtlamalı Olasılık", 4.6, 8, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("tablo", "boyalı", "koşuluyla"))
            )
        ),

        // ==================== AYT MATEMATİK (2018-2026) ====================
        CuratedTopicSeed(
            subjectId = "ayt_mat",
            topicId = "polinomlar",
            topicName = "Polinomlar",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Kökleri, teğetlik noktaları veya kalan koşulları verilen n. dereceden P(x) polinomunun denklemini sıfırdan inşa etme.",
            trap = "Çift katlı kök (x-eksenine teğetlik) durumunda (x-a)² çarpanını unutma veya başkatsayıyı 1 varsayma.",
            outcomes = listOf(
                out("POLINOM_DENKLEM_KURMA", "Kök ve Kalan Şartlarından P(x) Polinom Denklemi İnşa Etme", 5.0, 9, OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("başkatsayı", "dereceden", "p(x)", "tam bölün")),
                out("POLINOM_GRAFIK_KOK", "Polinom Grafiği Kesişimleri ve Çift Katlı Kök Yorumu", 4.7, 8, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU, keywords = listOf("grafik", "eksen", "sabit terim", "katsayılar toplamı")),
                out("KALAN_TEOREMI_BOLME", "Polinomlarda Bölme Özdeşliği P(x)=B(x)·Q(x)+K(x) Uygulamaları", 4.5, 8, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("bölümünden kalan", "kalan teoremi"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_mat",
            topicId = "parabol",
            topicName = "Parabol",
            shareWeight = 4.7,
            yearsAsked = 9,
            measures = "Parabolün simetri ekseni r = -b/(2a), tepe noktası T(r,k) ve doğru-parabol kesişim diskriminantını analitik düzlemde birleştirme.",
            trap = "Parabol içine çizilen dikdörtgen/üçgen köşelerinin simetri eksenine eşit uzaklıkta olduğunu gözden kaçırma.",
            outcomes = listOf(
                out("TEPE_SIMETRI_ANALITIK", "Parabolde Tepe Noktası T(r,k) ve Simetri Ekseni Muhakemesi", 4.8, 9, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("tepe noktası", "simetri ekseni", "parabol")),
                out("PARABOL_DOGRU_ALAN", "Parabol - Doğru Kesişimi ve Koordinat Düzleminde Geometrik Alan", 4.6, 8, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU, keywords = listOf("teğet", "kesiş", "dikdörtgen", "alan")),
                out("PARABOL_MODELLEME", "İkinci Dereceden Fonksiyonla Yörünge / Köprü / Gelir Modellemesi", 4.3, 7, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("en yüksek", "köprü", "yörünge"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_mat",
            topicId = "ikinci_derece_esitsizlikler",
            topicName = "2. Dereceden Eşitsizlikler",
            shareWeight = 4.7,
            yearsAsked = 9,
            measures = "Grafikle veya cebirsel çarpım/bölüm olarak verilen eşitsizlik sistemlerinde çift katlı kök ve payda köklerini işaret tablosunda ayırma.",
            trap = "≤ veya ≥ eşitsizliğinde paydayı sıfır yapan kökü veya çift katlı kökü işaret değiştirerek tabloya alma.",
            outcomes = listOf(
                out("GRAFIKLI_ISARET_TABLOSU", "Fonksiyon Grafiğinden Çarpım/Bölüm Eşitsizliği Çözüm Kümesi", 4.9, 9, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("grafik", "çözüm kümesi", "eşitsizlik")),
                out("ESITSIZLIK_SISTEMI_TAM_SAYI", "İkinci Dereceden Eşitsizlik Sistemleri ve Tam Sayı Kök Analizi", 4.6, 8, OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("eşitsizlik sistemi", "kaç farklı tam sayı", "işaret tablosu"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_mat",
            topicId = "trigonometri_ayt_mat",
            topicName = "Trigonometri",
            shareWeight = 5.0,
            yearsAsked = 9,
            measures = "Birim çemberde trigonometrik uzunluk/alan yazma, özdeşlik sadeleştirme, toplam-fark ve yarım açı dönüşümleri ile kök sayma.",
            trap = "Trigonometrik denklemde sadeleştirme yaparken sıfır olan çarpanın köklerini kaybetme veya tan(x) tanımsızlık noktalarını silmeme.",
            outcomes = listOf(
                out("BIRIM_CEMBER_ALAN_UZUNLUK", "Birim Çember Üzerinde Trigonometrik Uzunluk ve Bölge Alanı İfade Etme", 5.0, 9, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("birim çember", "boyalı bölge", "alan", "merkez açı")),
                out("TOPLAM_FARK_YARIM_ACI", "Toplam-Fark ve Yarım Açı Formülleriyle Cebirsel/Geometrik Çözüm", 5.0, 9, OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("toplam", "yarım açı", "sin(2x)", "cos(2x)", "tan")),
                out("TRIGONOMETRIK_OZDESLIK_SIRALAMA", "Trigonometrik Özdeşlik Sadeleştirme ve İşaret/Sıralama Öncülleri", 4.8, 9, OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("sadeleştirilmiş", "sıralama", "hangileri doğrudur")),
                out("TRIGONOMETRIK_DENKLEM", "Belirli Aralıkta Trigonometrik Denklem Kök Sayısı ve Kökler Toplamı", 4.8, 9, OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("[0, 2π]", "kök", "denklemini sağlayan"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_mat",
            topicId = "logaritma",
            topicName = "Logaritma",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Taban değiştirme, logaritmik eşitsizliklerde tabanın (0<a<1 vs a>1) yön etkisi ve cetvel/ölçek yeni nesil kurgularını çözme.",
            trap = "Logaritmik eşitsizlikte argümanın kesin pozitiflik şartını (f(x) > 0) çözüm kümesiyle kesiştirmeyi unutma.",
            outcomes = listOf(
                out("LOGARITMA_TABAN_DENKLEM", "Logaritma Özellikleri, Taban Değiştirme ve Denklem Çözümü", 4.9, 9, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("log", "ln", "taban", "eşitliğini sağlayan")),
                out("CETVEL_OLCEK_LOGARITMA", "Logaritmik Cetvel, Ses Şiddeti ve Üstel Büyüme Modellemesi", 4.8, 9, OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("cetvel", "uzunluk", "ölçek", "büyüme")),
                out("LOGARITMIK_ESITSIZLIK_GRAFIK", "Üstel-Logaritmik Fonksiyon Grafiği ve Tanım Kümesi Eşitsizliği", 4.5, 8, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU, keywords = listOf("grafik", "tanım kümesi", "eşitsizlik"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_mat",
            topicId = "diziler",
            topicName = "Diziler",
            shareWeight = 4.7,
            yearsAsked = 9,
            measures = "Aritmetik ve geometrik dizilerin ortak fark/çarpan bağıntılarını ve parçalı/indirgemeli dizi terim toplamlarını hesaplama.",
            trap = "Geometrik dizide çift kuvvetten gelen ortak çarpanın negatif değer olasılığını göz ardı etme.",
            outcomes = listOf(
                out("ARITMETIK_GEOMETRIK_ORTAK", "Aritmetik ve Geometrik Dizilerde Genel Terim ve İlk n Terim Toplamı", 4.9, 9, OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU, keywords = listOf("aritmetik dizi", "geometrik dizi", "ortak fark", "ortak çarpan")),
                out("INDIRGEMELI_PARCALI_DIZI", "İndirgemeli ve Parçalı Tanımlanan Dizilerde Periyot ve Muhakeme", 4.6, 8, OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("indirgeme", "her n pozitif", "terim"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_mat",
            topicId = "limit_ve_sureklilik",
            topicName = "Limit ve Süreklilik",
            shareWeight = 4.9,
            yearsAsked = 8,
            measures = "Grafikli bileşke fonksiyonlarda sağdan/soldan yaklaşma yönünü izleme ve parçalı fonksiyonda kritik nokta sürekliliğini kurma.",
            trap = "(fog)(x) limitinde iç fonksiyon g(x) sabit bir c değerine üstten/alttan yaklaşırken f(c+) ile f(c-) ayrımını kaçırma.",
            outcomes = listOf(
                out("GRAFIKTE_SAG_SOL_BILESKE_LIMIT", "Fonksiyon Grafiğinde Sağdan-Soldan ve Bileşke Limit Yorumu", 5.0, 8, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU, keywords = listOf("sağdan", "soldan", "grafik", "lim")),
                out("PARCALI_SUREKLILIK", "Parçalı Fonksiyonlarda Kritik Noktalarda Süreklilik Parametre Analizi", 4.9, 8, OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU, keywords = listOf("sürekli", "gerçek sayılar kümesinde", "kritik")),
                out("BELIRSIZLIK_SADELESTIRME", "0/0 Belirsizliği Çarpanlara Ayırma ve Köklü Eşlenik Limitleri", 4.4, 7, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("belirsizlik", "lim"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_mat",
            topicId = "turev_alma_kurallari",
            topicName = "Türev Alma Kuralları",
            shareWeight = 5.0,
            yearsAsked = 8,
            measures = "Çarpım, bölüm ve bileşke (zincir kuralı) türevini mutlak değer/kırılma noktası türevlenebilirlik şartlarıyla birleştirme.",
            trap = "Sürekli olmayan bir noktada sağ ve sol türev kurallarını türev alıp eşitleyerek türev var sanma (önce süreklilik şart!).",
            outcomes = listOf(
                out("ZINCIR_CARPIM_BOLUM", "Çarpım, Bölüm ve Bileşke Fonksiyon (Zincir Kuralı) Türevi", 5.0, 8, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("zincir kuralı", "çarpım", "bölüm", "f'(g(x))", "f'(x)")),
                out("TUREV_SUREKLILIK_MUTLAK", "Limit Tanımıyla Türev, Kırılma Noktası ve Mutlak Değer Türevlenebilirliği", 4.8, 8, OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("türevlenebilir", "sürekli", "mutlak", "sağdan türev"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_mat",
            topicId = "turev_uygulamalari",
            topicName = "Türevin Geometrik ve Fiziksel Uygulamaları",
            shareWeight = 5.0,
            yearsAsked = 8,
            measures = "Teğet eğimi f'(a) ile doğru denklemini birleştirme, f'(x) grafiğinden f(x) ekstremumlarını okuma ve alan/hacim optimizasyonu yapma.",
            trap = "Verilen grafiğin f(x)'e mi yoksa f'(x) türev fonksiyonuna mı ait olduğuna dikkat etmeden tepe noktalarını ekstremum alma!",
            outcomes = listOf(
                out("TEGET_EGIM_GEOMETRIK", "Türevin Geometrik Yorumu ve Teğet Doğrusu Denklemi", 5.0, 8, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("teğet", "eğim", "değme noktası")),
                out("TUREV_GRAFIGI_EKSTREMUM", "f'(x) Türev Grafiğinden Artan-Azalan Aralık ve Yerel Ekstremum Okuma", 5.0, 8, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU, keywords = listOf("ekstremum", "yerel maksimum", "yerel minimum", "artan", "azalan")),
                out("MAKSIMUM_MINIMUM_OPTIMIZASYON", "Geometrik ve Gerçek Yaşam Maksimum - Minimum Optimizasyon Problemleri", 4.9, 8, OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("en çok", "en az", "maksimum", "en büyük")),
                out("POLINOM_TUREV_KATSAYI", "Polinom Fonksiyonlarda f(x) ve f'(x) Katsayı / Kök İlişkisi", 4.6, 7, OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("polinom", "f'(x)", "kritik nokta"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_mat",
            topicId = "belirli_integral_alan",
            topicName = "Belirli İntegral ve Alan Hesabı",
            shareWeight = 5.0,
            yearsAsked = 8,
            measures = "Değişken değiştirme sınır dönüşümü, iki eğri arasında kalan boyalı alan, Riemann alt-üst toplamları ve türev-integral grafik geçişi.",
            trap = "x-ekseninin altında kalan bölgede belirli integralin negatif çıktığını unutup alan ile integrali işaretçe karıştırma.",
            outcomes = listOf(
                out("IKI_EGRI_ARASI_ALAN", "İki Eğri ve Doğru Arasında Kalan Boyalı Bölgenin İntegralle Alanı", 5.0, 8, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("boyalı bölgenin alanı", "eğri", "birimkare")),
                out("DEGISKEN_DEGISTIRME_SINIR", "Belirli İntegralde Değişken Değiştirme ve Sınır Dönüşümü", 4.9, 8, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("değişken değiştirme", "dx", "integral")),
                out("RIEMANN_ALT_UST_TOPLAM", "Riemann Toplamı ve Dikdörtgenlerle Alan Yaklaşımı", 4.6, 7, OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("riemann", "eşit aralık", "dikdörtgen")),
                out("TUREV_INTEGRAL_GRAFIK_GECISI", "f'(x) Grafiğinin Altındaki Alanla f(b)-f(a) Değişimini Hesaplama", 4.9, 8, OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("f'(x)", "alan", "f("))
            )
        ),

        // ==================== TYT & AYT GEOMETRİ (2018-2026) ====================
        CuratedTopicSeed(
            subjectId = "tyt_geo",
            topicId = "ucgende_acilar",
            topicName = "Üçgende Açılar",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Kağıt katlama sonucunda açıortay ve eş kenar oluşumunu fark etme, döndürme açısıyla ikizkenar üçgen yakalama.",
            trap = "Katlama çizgisinin açıortay olduğunu ve üst üste binen kenarların eşitliğini şekil üzerine taşımama.",
            outcomes = listOf(
                out("KATLAMA_ACIORTAY", "Üçgende Kağıt Katlama ve Açıortay Simetrisi", 5.0, 9, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("katlandığında", "katlama", "üst üste")),
                out("DONDURME_IKIZKENAR_ACI", "Nokta Etrafında Döndürme ile Oluşan İkizkenar Üçgen Açıları", 4.8, 9, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("döndürüldüğünde", "saat yönünde", "derece"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_geo",
            topicId = "dik_ucgen",
            topicName = "Dik Üçgen ve Pisagor",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Merdiven kayması, direk kırılması, sarkaç veya ekran yerleşimi gibi günlük hayat kurgularında Pisagor ve özel dik üçgenleri kurma.",
            trap = "Kayma sorularında merdivenin/ip boyunun (hipotenüsün) sabit kaldığını denkleme yazmama.",
            outcomes = listOf(
                out("YENI_NESIL_PISAGOR", "Günlük Yaşam Uzunluk Kurgularında Pisagor ve Özel Dik Üçgenler", 5.0, 9, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("dik üçgen", "pisagor", "uzunluk", "zemine")),
                out("OKLID_MUHTESEM_UCLU", "Öklid Bağıntıları ve Hipotenüse Ait Kenarortay (Muhteşem Üçlü)", 4.6, 8, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("öklid", "hipotenüs", "diklik"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_geo",
            topicId = "ucgende_eslik_benzerlik",
            topicName = "Üçgende Eşlik ve Benzerlik",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Gölge boyu, ayna/ışık yansıması, raf veya katlama modellerinde Thales, kelebek benzerliği ve alanlar oranı k² ilkesini uygulama.",
            trap = "Benzerlik oranı k iken çevre oranını k, alanlar oranını ise k² yerine k olarak alma.",
            outcomes = listOf(
                out("THALES_KELEBEK_BENZERLIK", "Thales, Temel Orantı ve Kelebek Benzerliği Uzunluk Modelleri", 5.0, 9, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("paralel", "benzerlik", "oran", "gölge")),
                out("ESLIK_ALAN_ORANI", "Açı-Kenar-Açı Eşliği ve Benzer Üçgenlerde Alanlar Oranı (k²)", 4.7, 8, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU, keywords = listOf("eş", "alan oranı", "boyalı"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_geo",
            topicId = "dogrunun_analitigi",
            topicName = "Doğrunun Analitik İncelenmesi",
            shareWeight = 5.0,
            yearsAsked = 9,
            measures = "Dik kesişen doğruların eğimleri çarpımı (m₁·m₂ = -1), doğrunun şekli eşit alanlı iki bölgeye ayırması ve noktanın doğruya uzaklığı.",
            trap = "Genel denklem ax+by+c=0 verildiğinde eğimi -a/b yerine a/b olarak alma.",
            outcomes = listOf(
                out("EGIM_DIK_KESISIM", "Dik Kesişen Doğrular, Eğim ve Koordinat Eksenleriyle Oluşan Alan", 5.0, 9, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("dik kesiş", "eğim", "koordinat düzleminde", "alan")),
                out("ESIT_ALAN_BOLEN_DOGRU", "Geometrik Şekli Eşit Alanlı İki Bölgeye Ayıran Doğru Denklemi", 4.8, 8, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("eşit alanlı", "iki bölgeye", "doğrusu")),
                out("NOKTANIN_DOGRUYA_UZAKLIGI", "Noktanın Doğruya Uzaklığı ve Paralel Doğrular Arası Mesafe", 4.5, 8, OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU, keywords = listOf("uzaklık", "paralel", "birim"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_geo",
            topicId = "cemberin_analitigi",
            topicName = "Çemberin Analitik İncelenmesi",
            shareWeight = 4.8,
            yearsAsked = 9,
            measures = "Merkez M(a,b) ve yarıçap r ile standart çember denklemi kurma, eksenlere/doğruya teğetlik koşulunu geometrik diklikle çözme.",
            trap = "Çemberin doğruya teğet olduğu sorularda merkezden teğete indirilen yarıçapın dik (r = d) olduğunu kullanmak yerine uzun cebirsel denklem açma.",
            outcomes = listOf(
                out("STANDART_CEMBER_TEGET", "Standart Çember Denklemi ve Eksenlere / Doğruya Teğetlik Analizi", 4.9, 9, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("çember", "merkez", "yarıçap", "teğet")),
                out("CEMBER_KIRIS_ANALITIK", "Analitik Düzlemde Çember - Doğru Kesişimi ve Kiriş Uzunluğu", 4.6, 8, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU, keywords = listOf("kiriş", "kesim noktası", "denklemi"))
            )
        ),

        // ==================== TYT & AYT FİZİK (2018-2026) ====================
        CuratedTopicSeed(
            subjectId = "tyt_fizik",
            topicId = "basinc",
            topicName = "Basınç",
            shareWeight = 4.8,
            yearsAsked = 9,
            measures = "Katı, durgun sıvı (h·d·g) ve açık hava/gaz basıncı ile Bernoulli akışkanlar ilkesini günlük yaşam deneyleriyle yorumlama.",
            trap = "Sıvı basıncını kabın şekline veya toplam sıvı ağırlığına bağlı sanma (yalnız derinlik h ve özkütle d belirler).",
            outcomes = listOf(
                out("KATI_SIVI_BASINC_DENEY", "Katı ve Durgun Sıvı Basıncı Değişimlerinin Öncüllü Yorumu", 4.9, 9, OsymQuestionType.ONCULLU, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("katı basıncı", "sıvı basıncı", "taban", "kesit")),
                out("BERNOULLI_AKISKAN_GAZ", "Akışkanların Basıncı (Bernoulli İlkesi) ve Açık Hava Basıncı Senaryoları", 4.6, 8, OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("akışkan", "açık hava", "hız", "basınç farkı"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_fizik",
            topicId = "kaldirma_kuvveti",
            topicName = "Kaldırma Kuvveti",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Yüzen, askıda kalan ve batan cisimlerde Fk - G dengesini, taşırma kaplarında ağırlaşma şartını ve buz erimesi su seviyesini analiz etme.",
            trap = "Yüzen ve askıda kalan cisimlerin farklı hacimde sıvı taşırdığı için kaldırma kuvvetlerini farklı sanma (her ikisinde de Fk = G'dir!).",
            outcomes = listOf(
                out("YUZME_ASKIDA_BATMA", "Yüzen, Askıda Kalan ve Batan Cisimlerde Kaldırma Kuvveti - Ağırlık Dengesi", 5.0, 9, OsymQuestionType.ONCULLU, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("yüzmekte", "askıda", "kaldırma kuvveti", "özkütle")),
                out("TASIRMA_KABI_AGIRLASMA", "Taşırma Kapları, İp Gerilmesi ve Sıvı Yüksekliği Değişimi Deneyleri", 4.7, 8, OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("taşırma", "ağırlaşma", "seviye"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_fizik",
            topicId = "elektrik_akimi_devreler",
            topicName = "Elektrik Akımı ve Devreler",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Seri-paralel bağlı özdeş lambaların parlaklık karşılaştırmasını, anahtar açma/kapama etkisini ve ev içi sigorta/güç güvenliğini ölçme.",
            trap = "Anahtar kapatıldığında eşdeğer direncin değişmesiyle ana kol akımının değiştiğini unutup sabit akım varsayma.",
            outcomes = listOf(
                out("LAMBA_PARLAKLIGI_ANAHTAR", "Özdeş Lambaların Parlaklığı ve Anahtar Kapatma Devre Analizi", 5.0, 9, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU, keywords = listOf("lamba", "parlaklık", "anahtar", "devre")),
                out("ELEKTRIKSEL_GUC_ENERJI", "Elektriksel Güç, Tüketilen Enerji ve Ev İçi Paralel Tesisat Yorumu", 4.6, 8, OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("güç", "enerji", "direnç", "voltmetre", "ampermetre"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_fizik",
            topicId = "atislar",
            topicName = "Atışlar",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Yatay ve eğik atışta yatay hızın sabitliğini, düşey ivmenin g sabitliğini ve hava dirençli limit hız durumunu vektörel ayırma.",
            trap = "Eğik atışın maksimum yüksekliğinde cismin hızını ve ivmesini sıfır sanma (yatay hız Vx ≠ 0 ve ivme daima aşağı g'dir!).",
            outcomes = listOf(
                out("YATAY_EGIK_ATIS_MENZIL", "Yatay ve Eğik Atışta Uçuş Süresi, Maksimum Yükseklik ve Menzil Analizi", 5.0, 9, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("yatay atış", "eğik atış", "menzil", "maksimum yükseklik")),
                out("HAVA_DIRENCI_LIMIT_HIZ", "Hava Direnci Kuvveti, Limit Hız ve Hız-Zaman Grafiği Yorumu", 4.6, 8, OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("hava direnci", "limit hız", "serbest düşme"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_fizik",
            topicId = "itme_momentum",
            topicName = "İtme ve Çizgisel Momentum",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Net kuvvet-zaman grafiği altındaki alandan momentum değişimini bulma ve esnek/esnek olmayan çarpışma-patlama korunumunu uygulama.",
            trap = "Momentumun vektörel olduğunu unutup ters yönde dönen cismin momentum değişimini |P₂ - P₁| yerine skaler fark alma!",
            outcomes = listOf(
                out("ITME_KUVVET_ZAMAN", "İtme - Momentum Değişimi (I = ΔP) ve Kuvvet-Zaman Grafiği", 4.9, 9, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("itme", "momentum değişimi", "kuvvet-zaman")),
                out("CARPISMA_PATLAMA_KORUNUM", "Bir ve İki Boyutta Çarpışmalar, İç Patlama ve Momentum Korunumu", 4.9, 9, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU, keywords = listOf("çarpışma", "esnek", "patlama", "momentum korunumu"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_fizik",
            topicId = "cembersel_hareket",
            topicName = "Düzgün Çembersel Hareket",
            shareWeight = 5.0,
            yearsAsked = 9,
            measures = "Merkezcil kuvvetin ayrı bir kuvvet olmayıp net kuvvet olduğunu (ip, sürtünme, tepki) gösterme ve dış tork sıfırken açısal momentum korunumu (L = I·ω).",
            trap = "Serbest cisim diyagramında merkezcil kuvveti dışarı doğru ayrı bir kuvvet vektörü olarak çizme!",
            outcomes = listOf(
                out("MERKEZCIL_VIRAJ_SILINDIR", "Yatay/Eğimli Viraj, Düşey Çembersel Hareket ve Merkezcil Net Kuvvet", 5.0, 9, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU, keywords = listOf("merkezcil", "viraj", "açısal hız", "ip gerilmesi")),
                out("ACISAL_MOMENTUM_EYLEMSIZLIK", "Eylemsizlik Momenti Değişimi ve Açısal Momentum Korunumu (L = I·ω)", 5.0, 9, OsymQuestionType.ONCULLU, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("açısal momentum", "eylemsizlik momenti", "kollarını kapat"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_fizik",
            topicId = "manyetizma_induksiyon",
            topicName = "Manyetizma ve Elektromanyetik İndüksiyon",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Yüklü parçacığa etkiyen manyetik kuvvet yönü (sağ el kuralı) ile manyetik akı değişimi ve Lenz yasası indüksiyon akım yönünü belirleme.",
            trap = "Manyetik alan şiddetinin büyük olmasını indüksiyon EMK'sı için yeterli sanma (akı DEĞİŞİMİ ΔΦ/Δt şarttır!).",
            outcomes = listOf(
                out("MANYETIK_KUVVET_SAG_EL", "Akım Geçen Tele ve Yüklü Parçacığa Etkiyen Manyetik Kuvvet Yörüngesi", 4.9, 9, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU, keywords = listOf("manyetik alan", "manyetik kuvvet", "yüklü parçacık")),
                out("LENZ_INDUKSIYON_AKI", "Faraday - Lenz Yasası, Manyetik Akı Değişimi ve İndüksiyon EMK Yönü", 5.0, 9, OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("indüksiyon", "lenz", "manyetik akı", "halka"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_fizik",
            topicId = "modern_fizik",
            topicName = "Modern Fizik",
            shareWeight = 5.0,
            yearsAsked = 8,
            measures = "Fotoelektrik olayda foton enerjisi, bağlanma enerjisi, kesme potansiyeli ve ışık şiddeti etkilerini birbirinden kesin olarak ayırma.",
            trap = "Işık şiddetini artırmanın sökülen fotoelektronların maksimum kinetik enerjisini veya kesme potansiyelini artırdığını sanma!",
            outcomes = listOf(
                out("FOTOELEKTRIK_KESME_POTANSIYEL", "Fotoelektrik Denklem, Eşik Frekansı ve Kesme Potansiyeli Grafiği", 5.0, 8, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU, keywords = listOf("fotoelektrik", "kesme potansiyeli", "eşik", "fotosel")),
                out("COMPTON_DE_BROGLIE_GORELILIK", "Compton Saçılması, De Broglie Dalga Boyu ve Özel Görelilik İlkeleri", 4.8, 8, OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("compton", "de broglie", "foton", "görelilik"))
            )
        ),

        // ==================== TYT & AYT KİMYA (2018-2026) ====================
        CuratedTopicSeed(
            subjectId = "tyt_kimya",
            topicId = "periyodik_sistem",
            topicName = "Periyodik Sistem",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Atom yarıçapı, birinci iyonlaşma enerjisi (3 küresel simetri istisnası: 2A>3A, 5A>6A), elektronegatiflik ve metalik-ametalik eğilimleri karşılaştırma.",
            trap = "Aynı periyotta soldan sağa iyonlaşma enerjisinin istisnasız arttığını sanıp 2A-3A ve 5A-6A küresel simetri sırasını kaçırma.",
            outcomes = listOf(
                out("CAP_IYONLASMA_ENERJISI", "Atom Yarıçapı ve İyonlaşma Enerjisi Periyodik Değişim / Tablo Analizi", 5.0, 9, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU, keywords = listOf("atom yarıçapı", "iyonlaşma enerjisi", "periyot", "grup")),
                out("ELEKTRONEGATIFLIK_GRUP_OZELLIK", "Baş Grup Elementlerinin Katman Dizilimi ve Elektronegatiflik Eğilimleri", 4.7, 9, OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("elektronegatiflik", "katman", "soygaz", "halojen"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "tyt_kimya",
            topicId = "kimyasal_turler_etkilesim",
            topicName = "Kimyasal Türler Arası Etkileşimler",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Lewis elektron nokta yapısı, molekül polarlığı ve güçlü (iyonik/kovalent/metalik) ile zayıf (hidrojen bağı, dipol, London) etkileşim ayrımı.",
            trap = "Su kaynarken kovalent bağların koptuğunu sanma (hal değişiminde yalnız zayıf moleküller arası etkileşimler kopar!).",
            outcomes = listOf(
                out("LEWIS_POLAR_APOLAR", "Lewis Nokta Yapısı, İyonik/Kovalent Bağ ve Molekül Polarlığı", 5.0, 9, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU, keywords = listOf("lewis", "polar", "apolar", "kovalent", "iyonik")),
                out("HIDROJEN_ZAYIF_ETKILESIM", "Hidrojen Bağı, Van der Waals Etkileşimleri ve Kaynama Noktası Karşılaştırması", 4.8, 9, OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("hidrojen bağı", "london", "dipol", "kaynama noktası"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_kimya",
            topicId = "gazlar",
            topicName = "Gazlar",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "İdeal gaz denklemi (P·V = n·R·T), Dalton kısmi basınçları, pistonlu kaplarda gaz karıştırma ve kinetik teori difüzyon hızını uygulama.",
            trap = "Sıcaklık değerini Celsius (°C) olarak bırakıp mutlak sıcaklık Kelvin'e (T = t + 273) çevirmeyi unutma!",
            outcomes = listOf(
                out("IDEAL_GAZ_PISTON_KISMI", "İdeal Gaz Yasası, İdeal Pistonlu Kaplar ve Kısmi Basınç Hesaplamaları", 5.0, 9, OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("ideal gaz", "piston", "kısmi basınç", "atm", "kelvin")),
                out("KINETIK_DIFUZYON_GERCEK_GAZ", "Kinetik Teori Difüzyon Hızı ve Gerçek Gazların İdeallikten Sapması", 4.6, 8, OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("difüzyon", "kinetik", "gerçek gaz"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_kimya",
            topicId = "kimyasal_hiz",
            topicName = "Kimyasal Tepkimelerde Hız",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Başlangıç derişimleri deney tablosundan hız bağıntısı ve hız sabiti k birimini çıkarma; potansiyel enerji diyagramında katalizör etkisini okuma.",
            trap = "Kademeli tepkimede hız bağıntısını net tepkimeye göre yazma (hızı daima EN YAVAŞ adımın girenleri belirler!).",
            outcomes = listOf(
                out("DENEY_TABLOSU_HIZ_BAGINTISI", "Deney Tablosundan Tepkime Hız Bağıntısı, Derecesi ve k Sabiti Bulma", 5.0, 9, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("deney", "başlangıç hızı", "hız bağıntısı", "derişim")),
                out("AKTIFLESME_KATALIZOR_ETKISI", "Aktifleşme Enerjisi, Katalizör ve Hıza Etki Eden Faktörler", 4.7, 8, OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("katalizör", "aktifleşme enerjisi", "yavaş adım"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_kimya",
            topicId = "kimyasal_denge",
            topicName = "Kimyasal Tepkimelerde Denge",
            shareWeight = 5.0,
            yearsAsked = 9,
            measures = "Denge kesri Qc ile denge yönünü belirleme, Kc-Kp geçişi ve Le Chatelier ilkesiyle sıcaklık/hacim/madde ekleme etkilerini analiz etme.",
            trap = "Katı ve saf sıvıları denge bağıntısına yazma veya hacim/madde değişiminin Kc denge sabitini değiştirdiğini sanma (Kc yalnız sıcaklıkla değişir!).",
            outcomes = listOf(
                out("LE_CHATELIER_DENGE_ETKI", "Le Chatelier İlkesi: Sıcaklık, Hacim ve Derişim Değişimi Denge Yorumu", 5.0, 9, OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("le chatelier", "denge", "sıcaklık", "hacim", "kc")),
                out("DENGE_SABITI_KC_KP", "Derişimler ve Kısmi Basınçlar Cinsinden Denge Sabiti (Kc - Kp) Hesabı", 4.8, 9, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("kp", "kc", "dengede", "mol"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_kimya",
            topicId = "kimya_ve_elektrik",
            topicName = "Kimya ve Elektrik",
            shareWeight = 5.0,
            yearsAsked = 9,
            measures = "İndirgenme potansiyellerinden anot-katot tespiti, standart pil potansiyeli E°pil, Nernst derişim etkisi ve Faraday seri elektroliz hesaplamaları.",
            trap = "Yarı tepkimeyi katsayıyla çarptığında standart elektrot potansiyeli E° değerini de o katsayıyla çarpma (E° madde miktarına bağlı değildir!).",
            outcomes = listOf(
                out("GALVANIK_PIL_ANOT_KATOT", "Galvanik Hücrelerde Anot-Katot Tespiti, Tuz Köprüsü ve E°pil Hesabı", 5.0, 9, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU, keywords = listOf("anot", "katot", "pil potansiyeli", "tuz köprüsü", "elektrot")),
                out("FARADAY_ELEKTROLIZ_AKTIFLIK", "Faraday Elektroliz Yasaları, Seri Kaplar ve Metalik Aktiflik Sıralaması", 4.8, 9, OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("elektroliz", "faraday", "aktiflik", "coulomb"))
            )
        ),

        // ==================== TYT & AYT BİYOLOJİ (2018-2026) ====================
        CuratedTopicSeed(
            subjectId = "tyt_biyoloji",
            topicId = "kalitim",
            topicName = "Kalıtım ve Biyoçeşitlilik",
            shareWeight = 5.0,
            yearsAsked = 9,
            measures = "Soyağacı şemalarında otozomal/gonozomal çekinik-baskın kalıtım modellerini test etme, kan grupları ve bağımsız gen çaprazlama olasılığı.",
            trap = "X'e bağlı çekinik kalıtımda hasta kız çocuğun babasının kesinlikle hasta (XʳY) olması gerektiğini soyağacında denetlememe.",
            outcomes = listOf(
                out("SOYAGACI_GONOZOMAL_OTOZOMAL", "Soyağacı Analizinde Otozomal ve Eşeye Bağlı (X/Y) Kalıtım Modelleri", 5.0, 9, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU, keywords = listOf("soyağacı", "otozomal", "x kromozomu", "renk körü")),
                out("CAPRAZLAMA_KAN_GRUPLARI", "Mendel Çaprazlamaları, Eş Baskınlık ve AB0-Rh Kan Grubu Olasılıkları", 4.8, 9, OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("çaprazlama", "heterozigot", "kan grubu", "fenotip"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_biyoloji",
            topicId = "sinir_sistemi",
            topicName = "Sinir Sistemi",
            shareWeight = 4.9,
            yearsAsked = 9,
            measures = "Nöronda impuls iletim hızını etkileyen yapısal faktörleri, sinapsta kimyasal seçici direnci ve beyin bölümlerinin homeostatik görevlerini ölçme.",
            trap = "Uyaran şiddeti arttığında tek nörondaki impuls iletim HIZININ arttığını sanma (yalnız impuls frekansı/sayısı artar!).",
            outcomes = listOf(
                out("IMPULS_SINAPS_ILETIMI", "Nöronda İmpuls Oluşumu, Miyelin Kılıf Hızı ve Sinaptik Kimyasal İletim", 5.0, 9, OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("nöron", "impuls", "sinaps", "miyelin", "eşik değer")),
                out("MERKEZI_CEVRESEL_BEYIN", "Hipotalamus, Beyincik, Omurilik Soğanı ve Refleks Yayı Analizi", 4.7, 8, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("hipotalamus", "omurilik", "refleks", "beyincik"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_biyoloji",
            topicId = "genden_proteine",
            topicName = "Genden Proteine (Nükleik Asitler)",
            shareWeight = 5.0,
            yearsAsked = 9,
            measures = "DNA yarı korunumlu eşlenme enzimlerini (helikaz, polimeraz, ligaz), mRNA kodon - tRNA antikodon şifresini ve genetik mühendisliği uygulamalarını ayırma.",
            trap = "Durdurucu kodonların (UAA, UAG, UGA) karşılığında amino asit taşıyan bir tRNA bulunduğunu sanma!",
            outcomes = listOf(
                out("REPLIKASYON_ENZIMLER", "DNA Yarı Korunumlu Eşlenmesi ve Helikaz-Polimeraz-Ligaz Görevleri", 5.0, 9, OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("replikasyon", "helikaz", "ligaz", "polimeraz", "dna")),
                out("TRANSKRIPSIYON_PROTEIN_SENTEZI", "Transkripsiyon, Translasyon, Kodon-Antikodon ve Polizom Mekanizması", 5.0, 9, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU, keywords = listOf("kodon", "antikodon", "mrna", "trna", "ribozom", "protein sentezi")),
                out("BIYOTEKNOLOJI_KLONLAMA", "Modern Biyoteknoloji, Gen Klonlaması, PCR ve CRISPR Uygulamaları", 4.6, 8, OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("biyoteknoloji", "plazmit", "klonlama", "gen aktarımı"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_biyoloji",
            topicId = "canlilarda_enerji_donusumleri",
            topicName = "Fotosentez ve Hücresel Solunum",
            shareWeight = 5.0,
            yearsAsked = 9,
            measures = "Kloroplastta ışığa bağımlı / ışıktan bağımsız evre girdi-çıktılarını, kemosentez farkını ve oksijenli solunum (glikoliz, Krebs, ETS) basamaklarını karşılaştırma.",
            trap = "Fotosentezde atmosfere verilen oksijenin karbondioksitten (CO₂) geldiğini sanma (suyun H₂O fotolizinden gelir!).",
            outcomes = listOf(
                out("FOTOSENTEZ_EVRELERI_DENEY", "Fotosentez Evreleri, Fotoliz ve Fotosentez Hızını Etkileyen Faktör Grafikleri", 5.0, 9, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU, keywords = listOf("fotosentez", "kloroplast", "stroma", "granalar", "nadph")),
                out("OKSIJENLI_SOLUNUM_FERMANTASYON", "Glikoliz, Krebs Döngüsü, ETS Kemiozmoz ve Fermantasyon Karşılaştırması", 5.0, 9, OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("glikoliz", "krebs", "ets", "fermantasyon", "mitokondri", "atp"))
            )
        ),
        CuratedTopicSeed(
            subjectId = "ayt_biyoloji",
            topicId = "bitki_biyolojisi",
            topicName = "Bitki Biyolojisi",
            shareWeight = 4.9,
            yearsAsked = 8,
            measures = "Bitkisel dokular, ksilem-floem taşıma yönü, stoma açılıp kapanma turgor mekanizması, oksin fototropizma deneyleri ve çift döllenmeyi ölçme.",
            trap = "Ksilem borularında madde taşınmasının ATP harcanarak çift yönlü yapıldığını sanma (ksilem ölü hücredir, tek yönlü fiziksel taşır!).",
            outcomes = listOf(
                out("STOMA_KSILEM_FLOEM", "Stoma Açılıp Kapanma Mekanizması ve Ksilem - Floem Madde Taşınması", 4.9, 8, OsymQuestionType.ONCULLU, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("stoma", "ksilem", "floem", "terleme", "kök basıncı")),
                out("TROPIZMA_BITKI_HORMONLARI", "Koleoptil Fototropizma Deneyleri ve Bitkisel Hormonlar (Oksin, Sitokinin, Etilen)", 4.8, 8, OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("oksin", "koleoptil", "tropizma", "nasti", "hormon")),
                out("CICEKLI_BITKI_CIFT_DOLLENME", "Çiçekli Bitkilerde Üreme, Polen-Embriyo Kesesi ve Çift Döllenme", 4.6, 8, OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("polen", "endosperm", "çift döllenme", "tohum"))
            )
        )
    )

    private val curatedByTopicKey: Map<String, CuratedTopicSeed> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        curatedSeeds.associateBy { "${it.subjectId}:${it.topicId}" }
    }

    private val curatedByTopicIdOnly: Map<String, CuratedTopicSeed> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        curatedSeeds.groupBy { it.topicId }
            .mapValues { (_, list) -> list.first() }
    }

    /**
     * Katalogdaki her konu için zengin 2018–2026 ÖSYM profilini döndürür.
     * Doğrudan küratörlü profil varsa onu, yoksa ders disiplinine özgü (Matematik, Geometri,
     * Fizik, Kimya, Biyoloji, Türkçe, Edebiyat, Tarih, Coğrafya, Felsefe, Din) otantik profil üretir.
     */
    fun profileForTopic(
        subjectId: String,
        topicId: String,
        topicName: String = ""
    ): OsymTopicIntelligenceProfile {
        val cleanSubjectId = subjectId.trim()
        val cleanTopicId = topicId.trim()
        val catalogSubject = SoruLabCatalog.allSubjects.firstOrNull { it.id.equals(cleanSubjectId, ignoreCase = true) }
            ?: SoruLabCatalog.allSubjects.firstOrNull { s -> s.topics.any { it.id.equals(cleanTopicId, ignoreCase = true) } }
        val catalogTopic = catalogSubject?.topics?.firstOrNull { it.id.equals(cleanTopicId, ignoreCase = true) }
            ?: SoruLabCatalog.resolveTopicFast(cleanTopicId, topicName)

        val resolvedSubjectId = catalogSubject?.id ?: cleanSubjectId.ifBlank { "tyt_mat" }
        val resolvedTopicId = catalogTopic?.id ?: cleanTopicId.ifBlank { "genel_konu" }
        val resolvedTopicName = catalogTopic?.name ?: topicName.ifBlank { "ÖSYM Müfredat Konusu" }
        val resolvedExamType = catalogSubject?.examType
            ?: if (resolvedSubjectId.startsWith("ayt_", ignoreCase = true)) ExamType.AYT else ExamType.TYT

        val seed = curatedByTopicKey["$resolvedSubjectId:$resolvedTopicId"]
            ?: curatedByTopicIdOnly[resolvedTopicId]?.takeIf { it.subjectId.substringAfter('_') == resolvedSubjectId.substringAfter('_') }

        if (seed != null) {
            val builtOutcomes = seed.outcomes.map { o ->
                OsymOutcomeIntelligence(
                    id = "OSYM_${resolvedSubjectId.uppercase(Locale.ROOT)}_${resolvedTopicId.uppercase(Locale.ROOT)}_${o.suffix}",
                    subjectId = resolvedSubjectId,
                    topicId = resolvedTopicId,
                    title = o.title,
                    osymFrequencyWeight = o.weight,
                    yearsAskedCount2018To2026 = o.years,
                    preferredQuestionTypes = o.types,
                    whatOsymMeasures = seed.measures,
                    commonTrap = seed.trap,
                    keywords = o.keywords
                )
            }
            val archetypes = builtOutcomes
                .flatMap { it.preferredQuestionTypes }
                .distinct()
                .map { OsymSignatureArchetype.fromQuestionType(it) }
                .ifEmpty { listOf(OsymSignatureArchetype.ANALITIK_CEBIRSEL_MUHAKEME) }

            return OsymTopicIntelligenceProfile(
                subjectId = resolvedSubjectId,
                topicId = resolvedTopicId,
                topicName = resolvedTopicName,
                examType = resolvedExamType,
                historicalShareWeight = seed.shareWeight,
                yearsAsked2018To2026 = seed.yearsAsked,
                whatOsymMeasures = seed.measures,
                commonOsymTrap = seed.trap,
                signatureArchetypes = archetypes,
                outcomes = builtOutcomes
            )
        }

        return synthesizeDisciplineProfile(
            subjectId = resolvedSubjectId,
            topicId = resolvedTopicId,
            topicName = resolvedTopicName,
            examType = resolvedExamType
        )
    }

    fun outcomesForTopic(
        subjectId: String,
        topicId: String,
        topicName: String = ""
    ): List<OsymOutcomeIntelligence> = profileForTopic(subjectId, topicId, topicName).outcomes

    /**
     * `(i)` bilgi ekranı için her `topicId + OsymQuestionType` kombinasyonuna karşılık gelen
     * zengin, anlaşılır ve ÖSYM diline uygun kazanım başlığını döndürür. Asla boş dönmez.
     */
    fun fallbackKazanimTitle(
        subjectId: String,
        topicId: String,
        topicName: String = "",
        questionType: OsymQuestionType,
        deterministicSeed: Int = 0
    ): String {
        val profile = profileForTopic(subjectId, topicId, topicName)
        val generatedCatalogMatches = LearningOutcomeCatalog.candidates(profile.subjectId, profile.topicId)
        val matchingOutcomes = profile.outcomes.filter { questionType in it.preferredQuestionTypes }
        if (matchingOutcomes.isNotEmpty()) {
            val idx = (deterministicSeed and Int.MAX_VALUE) % matchingOutcomes.size
            return matchingOutcomes[idx].title
        }
        if (generatedCatalogMatches.isNotEmpty()) {
            val base = generatedCatalogMatches[(deterministicSeed and Int.MAX_VALUE) % generatedCatalogMatches.size].name
            val suffix = OsymSignatureArchetype.fromQuestionType(questionType).shortBadge
            return "${profile.topicName}: $base ($suffix)"
        }
        if (profile.outcomes.isNotEmpty()) {
            val idx = (deterministicSeed and Int.MAX_VALUE) % profile.outcomes.size
            return profile.outcomes[idx].title
        }
        return "${profile.topicName} • ${OsymSignatureArchetype.fromQuestionType(questionType).label}"
    }

    /**
     * `CustomQuestionRecord` kaydı için eksiksiz kazanım, soru tipi, ÖSYM frekans ağırlığı
     * ve temiz yayın/kitap bilgisini deterministik olarak çözümler.
     */
    fun resolveOutcomeForRecord(record: CustomQuestionRecord): OsymResolvedQuestionMeta {
        val profile = profileForTopic(record.subjectId, record.topicId, record.topicName)
        val classifiedType = OsymQuestionType.classifyCustomQuestion(record)
        val deterministicSeed = record.id.hashCode().ushr(1) + record.questionNumberInBook.coerceAtLeast(0)
        val (cleanPublisher, cleanBook) = cleanBookSourceLabel(record)

        // 1. Doğrulanmış / katalogdan eşleşmiş kazanım varsa koru ve frekansını bağla
        val verified = record.resolvedLearningOutcome
        if (verified.name.isNotBlank()) {
            val normVerified = normalizeKey(verified.name)
            val matchedIntel = profile.outcomes.firstOrNull {
                normalizeKey(it.title).contains(normVerified) ||
                    it.keywords.any { kw -> normVerified.contains(normalizeKey(kw)) }
            }
            val code = record.learningOutcome.ifBlank { verified.id.ifBlank { profile.topicId.uppercase(Locale.ROOT) } }
            return OsymResolvedQuestionMeta(
                outcomeId = verified.id.ifBlank { matchedIntel?.id ?: "OSYM_${profile.topicId.uppercase(Locale.ROOT)}" },
                outcomeCode = code,
                outcomeTitle = verified.name.trim(),
                outcomeSource = verified.source.ifBlank { "verified" },
                outcomeConfidence = verified.confidence.takeIf { it > 0.0 } ?: 0.95,
                questionType = classifiedType,
                archetype = OsymSignatureArchetype.fromQuestionType(classifiedType),
                osymFrequencyWeight = matchedIntel?.osymFrequencyWeight ?: profile.historicalShareWeight,
                cleanPublisherName = cleanPublisher,
                cleanBookTitle = cleanBook
            )
        }

        // 2. Yayınevinin ham `learningOutcome` metni anlamlıysa değerlendir
        val rawOutcome = record.learningOutcome.trim()
        if (rawOutcome.length >= 5 && rawOutcome.any { it.isLetter() } && !rawOutcome.matches(Regex("""^[A-Z0-9._-]+$"""))) {
            val normRaw = normalizeKey(rawOutcome)
            val matchedIntel = profile.outcomes.firstOrNull {
                normalizeKey(it.title).contains(normRaw) ||
                    it.keywords.any { kw -> normRaw.contains(normalizeKey(kw)) }
            }
            return OsymResolvedQuestionMeta(
                outcomeId = matchedIntel?.id ?: "PUB_${profile.topicId.uppercase(Locale.ROOT)}_${normRaw.hashCode().ushr(1) % 997}",
                outcomeCode = matchedIntel?.id ?: profile.topicId.uppercase(Locale.ROOT),
                outcomeTitle = rawOutcome,
                outcomeSource = "publisher_label",
                outcomeConfidence = 0.9,
                questionType = classifiedType,
                archetype = OsymSignatureArchetype.fromQuestionType(classifiedType),
                osymFrequencyWeight = matchedIntel?.osymFrequencyWeight ?: profile.historicalShareWeight,
                cleanPublisherName = cleanPublisher,
                cleanBookTitle = cleanBook
            )
        }

        // 3. Soru metni + çözüm metni üzerinden 2018–2026 ÖSYM kazanım anahtar kelime ve arketip eşleştirmesi
        val searchCorpus = normalizeKey("${record.questionText} ${record.cleanSolutionSummary} ${record.questionType}")
        val scoredMatch = profile.outcomes.map { outcome ->
            val keywordHits = outcome.keywords.count { kw ->
                val normKw = normalizeKey(kw)
                normKw.isNotBlank() && searchCorpus.contains(normKw)
            }
            val typeMatchScore = if (classifiedType in outcome.preferredQuestionTypes) 2 else 0
            outcome to (keywordHits * 4 + typeMatchScore)
        }.maxWithOrNull(
            compareBy<Pair<OsymOutcomeIntelligence, Int>> { it.second }
                .thenBy { it.first.osymFrequencyWeight }
        )

        val chosenOutcome = if (scoredMatch != null && scoredMatch.second >= 4) {
            scoredMatch.first
        } else {
            val byType = profile.outcomes.filter { classifiedType in it.preferredQuestionTypes }
                .ifEmpty { profile.outcomes }
            byType[(deterministicSeed and Int.MAX_VALUE) % byType.size]
        }

        // Eğer soru saf görsel/sentetik kısa metin ise ve birden fazla soru aynı tipe düşüyorsa,
        // kazanım çeşitliliğini korumak için konu kazanımları üzerinde deterministik dağılım sağla
        val effectiveQuestionType = if (record.questionType.isNotBlank()) {
            OsymQuestionType.fromRawLabel(record.questionType) ?: classifiedType
        } else if (record.questionText.trim().length < 35 && scoredMatch != null && scoredMatch.second < 4) {
            chosenOutcome.preferredQuestionTypes.firstOrNull()
                ?: OsymQuestionType.entries[(deterministicSeed and Int.MAX_VALUE) % OsymQuestionType.entries.size]
        } else {
            classifiedType
        }

        return OsymResolvedQuestionMeta(
            outcomeId = chosenOutcome.id,
            outcomeCode = record.learningOutcome.ifBlank { chosenOutcome.id },
            outcomeTitle = chosenOutcome.title,
            outcomeSource = "osym_2018_2026_intelligence",
            outcomeConfidence = if (scoredMatch != null && scoredMatch.second >= 4) 0.88 else 0.75,
            questionType = effectiveQuestionType,
            archetype = OsymSignatureArchetype.fromQuestionType(effectiveQuestionType),
            osymFrequencyWeight = chosenOutcome.osymFrequencyWeight,
            cleanPublisherName = cleanPublisher,
            cleanBookTitle = cleanBook
        )
    }

    /**
     * `(i)` bilgi kartında `Kitap / Yayın` alanının asla boş, dosya uzantılı veya
     * `Kaynak Belirtilmedi` çöpüyle kalmamasını sağlayan temizleyici.
     */
    fun cleanBookSourceLabel(record: CustomQuestionRecord): Pair<String, String> {
        val rawPublisher = record.publisherTag.trim()
        val cleanPublisher = rawPublisher
            .takeIf { it.isNotBlank() && !it.equals("Kaynak Belirtilmedi", ignoreCase = true) }
            ?: "Özel Soru Kitaplığım"

        val rawBookCandidate = record.sourceBookTitle.trim()
            .ifBlank { record.bookOrTestName.trim() }
            .replace(Regex("""\.(pdf|zip|json|png|jpg|jpeg)$""", RegexOption.IGNORE_CASE), "")
            .replace('_', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()

        val cleanBook = when {
            rawBookCandidate.isNotBlank() -> rawBookCandidate
            cleanPublisher != "Özel Soru Kitaplığım" -> "$cleanPublisher Soru Bankası"
            else -> "Kişisel PDF Soru Bankası"
        }
        return cleanPublisher to cleanBook
    }

    /**
     * Öğrencinin geçmiş tüm çözülmüş deneme ve konu testlerini (`TestResultRecord`) tarayarak
     * yanlış yapılan veya boş bırakılan kazanımları, konuları ve ÖSYM soru tiplerini ağırlıklandırır.
     */
    fun analyzeStudentWeaknesses(previous: List<TestResultRecord>): StudentOsymMasteryMemory {
        if (previous.isEmpty()) return StudentOsymMasteryMemory()

        val missedOutcomes = HashMap<String, Double>()
        val missedTopics = HashMap<String, Double>()
        val missedTypes = HashMap<OsymQuestionType, Double>()
        val missedTopicTypes = HashMap<Pair<String, OsymQuestionType>, Double>()
        val correctOutcomes = HashMap<String, Int>()

        val gradedRecords = previous
            .filter { rec ->
                rec.isSolvedAttempt && (
                    rec.correctCount + rec.wrongCount + rec.emptyCount > 0 ||
                        (rec.answersMapJson.length > 2 && rec.answersMapJson != "{}")
                    )
            }
            .sortedByDescending { it.createdAt?.seconds ?: 0L }

        gradedRecords.forEachIndexed { recIdx, record ->
            val recencyFactor = (1.0 / (1.0 + recIdx * 0.12)).coerceAtLeast(0.35)
            val snapshot = TestSnapshotCodec.decode(record.testSnapshotJson)
            val answers = runCatching { JSONObject(record.answersMapJson) }.getOrNull() ?: JSONObject()

            if (snapshot != null && snapshot.questions.isNotEmpty() && answers.length() > 0) {
                for (q in snapshot.questions) {
                    val userAns = answers.optString(q.number.toString(), "").trim().uppercase(Locale.ROOT)
                    if (userAns == "_STATUS") continue
                    val correctAns = q.correctOption.trim().uppercase(Locale.ROOT)
                    val qType = q.resolvedOsymQuestionType
                    val normTopic = normalizeKey(q.topicHeader.ifBlank { snapshot.topicId })
                    val outcomeIdKey = normalizeKey(q.learningOutcomeId.ifBlank { q.learningOutcome })
                    val outcomeTitleKey = normalizeKey(q.learningOutcomeName)

                    when {
                        userAns.isEmpty() -> {
                            val weight = 1.6 * recencyFactor
                            if (outcomeIdKey.isNotBlank()) missedOutcomes[outcomeIdKey] = (missedOutcomes[outcomeIdKey] ?: 0.0) + weight
                            if (outcomeTitleKey.isNotBlank()) missedOutcomes[outcomeTitleKey] = (missedOutcomes[outcomeTitleKey] ?: 0.0) + weight
                            if (normTopic.isNotBlank()) {
                                missedTopics[normTopic] = (missedTopics[normTopic] ?: 0.0) + weight
                                val pair = normTopic to qType
                                missedTopicTypes[pair] = (missedTopicTypes[pair] ?: 0.0) + weight
                            }
                            missedTypes[qType] = (missedTypes[qType] ?: 0.0) + weight
                        }
                        userAns != correctAns -> {
                            val weight = 2.4 * recencyFactor
                            if (outcomeIdKey.isNotBlank()) missedOutcomes[outcomeIdKey] = (missedOutcomes[outcomeIdKey] ?: 0.0) + weight
                            if (outcomeTitleKey.isNotBlank()) missedOutcomes[outcomeTitleKey] = (missedOutcomes[outcomeTitleKey] ?: 0.0) + weight
                            if (normTopic.isNotBlank()) {
                                missedTopics[normTopic] = (missedTopics[normTopic] ?: 0.0) + weight
                                val pair = normTopic to qType
                                missedTopicTypes[pair] = (missedTopicTypes[pair] ?: 0.0) + weight
                            }
                            missedTypes[qType] = (missedTypes[qType] ?: 0.0) + weight
                        }
                        else -> {
                            if (outcomeIdKey.isNotBlank()) correctOutcomes[outcomeIdKey] = (correctOutcomes[outcomeIdKey] ?: 0) + 1
                            if (outcomeTitleKey.isNotBlank()) correctOutcomes[outcomeTitleKey] = (correctOutcomes[outcomeTitleKey] ?: 0) + 1
                        }
                    }
                }
            } else if (record.wrongCount > 0 || record.emptyCount > 0) {
                val lossWeight = (record.wrongCount * 1.25 + record.emptyCount * 0.8) * recencyFactor
                val topicKey = normalizeKey(record.topicId.ifBlank { record.topicName })
                if (topicKey.isNotBlank()) {
                    missedTopics[topicKey] = (missedTopics[topicKey] ?: 0.0) + lossWeight
                }
            }
        }

        val mastered = correctOutcomes.filter { (key, count) ->
            count >= 2 && (missedOutcomes[key] ?: 0.0) < 0.8
        }.keys.toSet()

        return StudentOsymMasteryMemory(
            missedOutcomeWeights = missedOutcomes,
            missedTopicWeights = missedTopics,
            missedQuestionTypeWeights = missedTypes,
            missedTopicTypeWeights = missedTopicTypes,
            masteredOutcomeKeys = mastered
        )
    }

    private fun synthesizeDisciplineProfile(
        subjectId: String,
        topicId: String,
        topicName: String,
        examType: ExamType
    ): OsymTopicIntelligenceProfile {
        val generatedOutcomes = LearningOutcomeCatalog.candidates(subjectId, topicId)
        val discipline = subjectId.substringAfter('_').lowercase(Locale.ROOT)
        val prefix = "OSYM_${subjectId.uppercase(Locale.ROOT)}_${topicId.uppercase(Locale.ROOT)}"

        val (measures, trap, baseShare, yearsAsked, templates) = when (discipline) {
            "geo" -> Quintuple(
                "$topicName konusunda katlama-kesme simetrisini, yardımcı dikme/paralel çizimini ve alan-uzunluk oranlarını ölçer.",
                "Şeklin özel bir durum (eşkenar/dik) olduğunu kanıtlamadan göz kararı açı veya kenar eşitliği varsayma.",
                4.3,
                8,
                listOf(
                    out("KATLAMA_GORSEL", "$topicName: Katlama, Kesme ve Simetri Muhakemesi", 4.6, 8, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("katlandığında", "simetri", "kesilerek")),
                    out("YENI_NESIL_MODEL", "$topicName: Günlük Yaşam Uzunluk ve Alan Modellemesi", 4.4, 8, OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("uzunluk", "alan", "zemin")),
                    out("ANALITIK_BAGINTI", "$topicName: Açı, Uzunluk ve Alan Teorem Uygulamaları", 4.2, 7, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("birim", "kaç derecedir", "oran")),
                    out("ONCULLU_KESINLIK", "$topicName: Öncüllü (I-II-III) Geometrik Kesinlik Yorumu", 3.9, 6, OsymQuestionType.ONCULLU, keywords = listOf("hangileri doğrudur", "her zaman")),
                    out("KOORDINAT_TABLO", "$topicName: Birim Kareli Zemin / Koordinat Düzlemi Yorumu", 3.8, 6, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("birim kare", "koordinat", "tablo")),
                    out("STRATEJIK_CIKARIM", "$topicName: Adım Adım Çizim Kuralı ve Geometrik Muhakeme", 3.9, 6, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("çiziliyor", "kural", "en kısa"))
                )
            )
            "fizik" -> Quintuple(
                "$topicName konusunda fiziksel yasaların günlük yaşam deney düzeneğindeki neden-sonuç ilişkilerini, vektörel yönleri ve grafik eğim/alanlarını ölçer.",
                "Vektörel büyüklüklerde (hız, ivme, kuvvet, alan) yön değişimini göz ardı ederek yalnız skaler büyüklüğü karşılaştırma.",
                4.4,
                8,
                listOf(
                    out("ONCULLU_YASA", "$topicName: Fiziksel İlkeler ve Öncüllü (I-II-III) Kesinlik Analizi", 4.7, 8, OsymQuestionType.ONCULLU, keywords = listOf("hangileri doğrudur", "kesinlikle", "artar", "azalır")),
                    out("DENEY_DUZENEGI", "$topicName: Kontrollü Deney Düzeneği ve Gözlem Muhakemesi", 4.6, 8, OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("deney", "düzenek", "gözlem")),
                    out("GRAFIK_EGIM_ALAN", "$topicName: Değişim Grafiği Eğim ve Alan Yorumu", 4.5, 8, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("grafik", "tablo", "zaman")),
                    out("GORSEL_VEKTOR_SISTEM", "$topicName: Şekilli Sistem ve Vektörel Denge Çözümlemesi", 4.3, 7, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("şekildeki", "yön", "vektör")),
                    out("GUNLUK_YASAM_FIZIK", "$topicName: Günlük Yaşam Teknolojisi ve Fiziksel Modelleme", 4.2, 7, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("günlük", "araç", "ortam")),
                    out("MATEMATIKSEL_BAGINTI", "$topicName: Temel Fiziksel Bağıntı ve Oransal Hesaplama", 4.0, 7, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("oranı kaçtır", "kaç"))
                )
            )
            "kimya" -> Quintuple(
                "$topicName konusunda tanecik boyutu (atom/iyon/molekül) etkileşimlerini, stokiyometrik oranları, deney verilerini ve grafik okumayı ölçer.",
                "Fiziksel değişim ile kimyasal değişimi ya da yoğunluk/derişim ile toplam mol sayısını birbirine karıştırma.",
                4.4,
                8,
                listOf(
                    out("ONCULLU_KIMYASAL_YORUM", "$topicName: Tanecik Yapısı ve Öncüllü (I-II-III) Kavram Analizi", 4.7, 8, OsymQuestionType.ONCULLU, keywords = listOf("hangileri doğrudur", "tanecik", "molekül")),
                    out("GRAFIK_TABLO_VERI", "$topicName: Deney Tablosu ve Derişim/Sıcaklık Grafiği Okuma", 4.5, 8, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("grafik", "tablo", "sıcaklık", "derişim")),
                    out("HESAPLAMA_ORAN", "$topicName: Kimyasal Bağıntı, Mol ve Nicel Hesaplama", 4.4, 8, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("mol", "gram", "kütlece", "oran")),
                    out("LABORATUVAR_DENEY", "$topicName: Laboratuvar Düzeneği ve Tepkime Muhakemesi", 4.2, 7, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("kap", "çözelti", "eklendiğinde")),
                    out("GUNLUK_KIMYA_SENARYO", "$topicName: Endüstriyel / Günlük Yaşam Kimyası Senaryosu", 4.0, 7, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("günlük", "etiket", "kullanım")),
                    out("MOLEKUL_SISTEM_SEMA", "$topicName: Kap / Hücre Şeması ve Tanecik Modeli Görseli", 4.0, 7, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("şekildeki", "model"))
                )
            )
            "biyoloji" -> Quintuple(
                "$topicName konusunda hücresel/fizyolojik mekanizmaların sırasını, homeostatik geri bildirimleri ve deneysel grafik yorumlarını ölçer.",
                "Korelasyon gösteren iki biyolojik olayda neden-sonuç yönünü veya prokaryot-ökaryot hücresel sınırları karıştırma.",
                4.5,
                8,
                listOf(
                    out("ONCULLU_MEKANIZMA", "$topicName: Biyolojik Süreç ve Öncüllü (I-II-III) Doğruluk Analizi", 4.8, 9, OsymQuestionType.ONCULLU, keywords = listOf("hangileri doğrudur", "ortak", "gerçekleşir")),
                    out("GRAFIK_METABOLIZMA", "$topicName: Fizyolojik / Ekolojik Değişim Grafiği ve Tablo Yorumu", 4.6, 8, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("grafik", "tablo", "zamanla", "miktarı")),
                    out("DENEY_KONTROLLU_MUHAKEME", "$topicName: Kontrollü Biyoloji Deneyi ve Nedensel Çıkarım", 4.5, 8, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("deney", "ortam", "araştırmacı")),
                    out("ANATOMIK_SEMA_MODEL", "$topicName: Şematik Yapı, Organel / Sistem Görseli Çözümlemesi", 4.3, 7, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("şemada", "numaralanmış", "yapı")),
                    out("GUNCEL_BIYOLOJIK_SENARYO", "$topicName: Güncel Sağlık / Çevre Vaka Senaryosu", 4.1, 7, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("vaka", "birey", "hastal")),
                    out("KAVRAMSAL_ESLESTIRME", "$topicName: Temel Kavram, Enzim-Hormon-Yapı Eşleştirmesi", 4.1, 7, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("eşleştirme", "sırasıyla"))
                )
            )
            "turkce", "edebiyat" -> Quintuple(
                "$topicName konusunda metin/şiir bağlamı üzerinden dil ve anlatım inceliklerini, edebi dönem/tür ayırt edicilerini ve kavramsal çıkarımı ölçer.",
                "Metin bağlamından kopuk ezber tanım kullanarak dönem/sanat/ek işlevini yanlış eşleştirme.",
                4.3,
                8,
                listOf(
                    out("METIN_MUHAKEME", "$topicName: Metin / Beyit Üzerinden Anlam ve Çıkarım Muhakemesi", 4.6, 8, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("parçada", "dizelerde", "söylenemez", "çıkarılabilir")),
                    out("ONCULLU_DIL_EDEBIYAT", "$topicName: Numaralanmış Cümle / Öncül (I-II-III) İncelemesi", 4.5, 8, OsymQuestionType.ONCULLU, keywords = listOf("numaralanmış", "hangileri", "öncül")),
                    out("KAVRAMSAL_COZUMLEME", "$topicName: Tür, Biçim, Ek-Kök ve Kavramsal Yapı Çözümlemesi", 4.4, 8, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("hangisinde", "türü", "örnek")),
                    out("DIYALOG_KURGULU_METIN", "$topicName: Eleştirmen / Yazar Kurgulu Diyalog ve Bağlam Sorusu", 4.1, 7, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("yazar", "eleştirmen", "eser")),
                    out("TABLO_DONEM_KARSILASTIRMA", "$topicName: Dönem / Eser / Özellik Eşleştirme Tablosu", 3.9, 6, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("tablo", "eşleştirme")),
                    out("SEMATIK_YAPI", "$topicName: Kavram Haritası ve Metin Kurgusu Analizi", 3.8, 6, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("şema", "bölüm"))
                )
            )
            "tarih", "cografya", "tarih_cografya", "felsefe_din", "felsefe_grubu", "din" -> Quintuple(
                "$topicName konusunda tarihsel/felsefi metin yorumunu, harita ve istatistik dağılış okuryazarlığını ve kavram-olgu eşleştirmesini ölçer.",
                "'Yalnız bu bilgiye göre' kökünde kendi dış bilgisini katarak metinde yer almayan doğru bir bilgiyi işaretleme!",
                4.3,
                8,
                listOf(
                    out("METIN_KAYNAK_CIKARIM", "$topicName: Metin / Belge Üzerinden 'Yalnız Bu Bilgiye Göre' Çıkarımı", 4.7, 9, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("bu parçaya göre", "yalnız bu bilgiye", "ulaşılabilir")),
                    out("ONCULLU_SOSYAL_YORUM", "$topicName: Öncüllü (I-II-III) Neden-Sonuç ve Kavram Analizi", 4.5, 8, OsymQuestionType.ONCULLU, keywords = listOf("hangileri", "savunulabilir", "gösterilebilir")),
                    out("HARITA_GRAFIK_DAGILIS", "$topicName: Harita, İklim/Nüfus Grafiği ve Tablo Yorumlama", 4.4, 8, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("harita", "grafik", "tablo", "numaralanmış yerler")),
                    out("VAKA_GUNLUK_SENARYO", "$topicName: Toplumsal / Felsefi / Coğrafi Örnek Olay Senaryosu", 4.1, 7, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("örnek", "durum", "uygulama")),
                    out("KAVRAM_ILKE_TANIM", "$topicName: Temel Kavram, İlke ve Dönem Eşleştirmesi", 4.2, 8, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("kavram", "ilke", "aşağıdakilerden hangisi")),
                    out("MEKANSAL_GORSEL_ANALIZ", "$topicName: Mekânsal Dağılış ve Şematik Model Okuma", 4.0, 7, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("şekil", "bölge", "kuşak"))
                )
            )
            else -> Quintuple(
                "$topicName konusunda kavramsal tanım hakimiyetini, çok adımlı cebirsel/analitik işlemi ve yeni nesil modelleme becerisini ölçer.",
                "Tanım kümesi kısıtlarını (payda ≠ 0, tam sayı / gerçek sayı ayrımı) kontrol etmeden işlem sonucunu işaretleme.",
                4.4,
                8,
                listOf(
                    out("ANALITIK_ISLEM", "$topicName: Temel Tanım ve Cebirsel / Analitik Çözümleme", 4.6, 8, OsymQuestionType.ANALITIK_CEBIRSEL, keywords = listOf("eşitliğini sağlayan", "değeri kaçtır", "toplamı")),
                    out("YENI_NESIL_MODELLEME", "$topicName: Yeni Nesil Günlük Yaşam Senaryosu ve Modelleme", 4.6, 8, OsymQuestionType.YENI_NESIL_KURGULU, keywords = listOf("model", "günlük", "kutu", "mağaza")),
                    out("ONCULLU_KESINLIK", "$topicName: Öncüllü (I-II-III) Kesinlik ve İşaret Yorumu", 4.5, 8, OsymQuestionType.ONCULLU, keywords = listOf("hangileri doğrudur", "her zaman", "kesinlikle")),
                    out("GRAFIK_TABLO_YORUM", "$topicName: Fonksiyon Grafiği ve Değişim Tablosu Okuma", 4.3, 7, OsymQuestionType.GRAFIK_TABLO, keywords = listOf("grafik", "tablo", "koordinat")),
                    out("GORSEL_SEKIL_KURGU", "$topicName: Şekilli / Geometrik Temsil ve Örüntü Kurgusu", 4.1, 7, OsymQuestionType.GORSEL_GEOMETRIK, keywords = listOf("şekildeki", "uzunluk", "alan")),
                    out("MANTIKSAL_CIKARIM", "$topicName: Koşullu Kural ve Sayısal Muhakeme", 4.2, 7, OsymQuestionType.MANTIK_MUHAKEME, keywords = listOf("kural", "koşul", "en az", "en çok"))
                )
            )
        }

        // Eğer GeneratedLearningOutcomes içinde bu konuya ait özel alt başlıklar varsa onları da yüksek ağırlıklı kazanım olarak ekle
        val catalogDerived = generatedOutcomes.mapIndexed { idx, lo ->
            val rotatedType = OsymQuestionType.entries[idx % OsymQuestionType.entries.size]
            OsymOutcomeIntelligence(
                id = lo.id,
                subjectId = subjectId,
                topicId = topicId,
                title = "${topicName}: ${lo.name}",
                osymFrequencyWeight = (4.6 - (idx * 0.12)).coerceIn(3.6, 4.8),
                yearsAskedCount2018To2026 = 8,
                preferredQuestionTypes = setOf(rotatedType, OsymQuestionType.ONCULLU),
                whatOsymMeasures = measures,
                commonTrap = trap,
                keywords = lo.phrases + listOf(lo.name)
            )
        }

        val templateDerived = templates.map { o ->
            OsymOutcomeIntelligence(
                id = "${prefix}_${o.suffix}",
                subjectId = subjectId,
                topicId = topicId,
                title = o.title,
                osymFrequencyWeight = o.weight,
                yearsAskedCount2018To2026 = o.years,
                preferredQuestionTypes = o.types,
                whatOsymMeasures = measures,
                commonTrap = trap,
                keywords = o.keywords
            )
        }

        val combinedOutcomes = (catalogDerived + templateDerived).distinctBy { normalizeKey(it.title) }

        return OsymTopicIntelligenceProfile(
            subjectId = subjectId,
            topicId = topicId,
            topicName = topicName,
            examType = examType,
            historicalShareWeight = baseShare,
            yearsAsked2018To2026 = yearsAsked,
            whatOsymMeasures = measures,
            commonOsymTrap = trap,
            signatureArchetypes = OsymSignatureArchetype.entries,
            outcomes = combinedOutcomes
        )
    }

    private data class Quintuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )
}
