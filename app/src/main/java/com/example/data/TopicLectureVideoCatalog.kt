package com.example.data

import androidx.compose.runtime.Immutable
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

private val YOUTUBE_VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * Geçerli bir 11 karakterlik YouTube video kimliği olup olmadığını doğrular.
 */
fun isValidYoutubeVideoId(videoId: String): Boolean =
    YOUTUBE_VIDEO_ID_REGEX.matches(videoId.trim())

/**
 * Saniye değerini `MM:SS` veya `H:MM:SS` formatına çevirir.
 */
fun formatLectureTimestamp(totalSeconds: Int): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val seconds = safeSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun encodeYoutubeSearchQuery(query: String): String {
    val cleaned = query.trim().replace(Regex("\\s+"), " ")
    if (cleaned.isBlank()) return "YKS+konu+anlatimi"
    return URLEncoder.encode(cleaned, StandardCharsets.UTF_8.name())
}

private fun normalizeTurkish(raw: String): String {
    return raw
        .lowercase(Locale("tr", "TR"))
        .replace('ı', 'i')
        .replace('İ', 'i')
        .replace('ğ', 'g')
        .replace('ü', 'u')
        .replace('ş', 's')
        .replace('ö', 'o')
        .replace('ç', 'c')
        .replace('â', 'a')
        .replace('î', 'i')
        .replace('û', 'u')
        .trim()
}

/**
 * Bir konu anlatım videosunun içindeki alt kazanım / soru tipi dakika bölümü.
 * Öğrenci soruda takıldığında videonun başından değil, doğrudan ilgili [startSeconds] saniyesinden başlar.
 */
@Immutable
data class LectureChapterSegment(
    val id: String,
    val chapterTitle: String,
    val startSeconds: Int,
    val endSeconds: Int? = null,
    val kazanimIds: Set<String> = emptySet(),
    val kazanimCodes: Set<String> = emptySet(),
    val questionTypes: Set<OsymQuestionType> = emptySet(),
    val subTopicKeywords: List<String> = emptyList(),
    val keyFormulaSummary: String,
    val osymTip: String
) {
    val formattedTimestamp: String
        get() = formatLectureTimestamp(startSeconds)
}

/**
 * Popüler YKS hocalarına ait konu anlatım video kaydı.
 * Doğrulanmış 11 haneli YouTube kimliği (`videoId`) veya nadir alt konular için
 * `searchQueryFallback` üzerinden güvenli YouTube araması destekler.
 */
@Immutable
data class TopicLectureVideo(
    val id: String,
    val videoId: String,
    val searchQueryFallback: String = "",
    val title: String,
    val teacherName: String,
    val channelName: String,
    val teacherBadge: String = "En Popüler YKS Hocası",
    val popularityRank: Int = 1,
    val examType: ExamType,
    val subjectId: String,
    val subjectName: String,
    val topicIds: Set<String>,
    val topicKeywords: List<String>,
    val kazanimCodes: Set<String>,
    val chapters: List<LectureChapterSegment>
) {
    val hasVerifiedVideoId: Boolean
        get() = isValidYoutubeVideoId(videoId)

    fun effectiveSearchQuery(chapterHint: String = ""): String {
        val base = searchQueryFallback.ifBlank {
            "$teacherName $channelName ${examType.label} $subjectName $title"
        }
        return if (chapterHint.isNotBlank() && !base.contains(chapterHint, ignoreCase = true)) {
            "$base $chapterHint"
        } else {
            base
        }
    }

    fun watchUrlAt(startSeconds: Int, chapterHint: String = ""): String {
        val safeSec = startSeconds.coerceAtLeast(0)
        return if (hasVerifiedVideoId) {
            "https://www.youtube.com/watch?v=${videoId.trim()}&t=${safeSec}s"
        } else {
            val encoded = encodeYoutubeSearchQuery(effectiveSearchQuery(chapterHint))
            "https://www.youtube.com/results?search_query=$encoded"
        }
    }

    fun embedUrlAt(startSeconds: Int, chapterHint: String = ""): String {
        val safeSec = startSeconds.coerceAtLeast(0)
        return if (hasVerifiedVideoId) {
            "https://www.youtube.com/embed/${videoId.trim()}?start=$safeSec&autoplay=1&rel=0"
        } else {
            val encoded = encodeYoutubeSearchQuery(effectiveSearchQuery(chapterHint))
            "https://m.youtube.com/results?search_query=$encoded"
        }
    }
}

/**
 * Belirli bir hoca ve o hocanın soruya en uygun dakika segmenti.
 * Alternatif hoca değiştiricide tek tıkla geçiş için kullanılır.
 */
@Immutable
data class ResolvedLectureOption(
    val video: TopicLectureVideo,
    val chapter: LectureChapterSegment,
    val matchScore: Int,
    val youtubeWatchUrlWithTimestamp: String,
    val youtubeEmbedUrlWithTimestamp: String,
    val formattedTimestamp: String,
    val hasDirectEmbedVideo: Boolean
)

/**
 * Bir YKS sorusu için deterministik olarak çözümlenmiş akıllı konu anlatım önerisi.
 */
@Immutable
data class ResolvedLectureRecommendation(
    val primaryVideo: TopicLectureVideo,
    val primaryChapter: LectureChapterSegment,
    val matchedKazanim: OsymKazanim,
    val detectedQuestionType: OsymQuestionType,
    val youtubeWatchUrlWithTimestamp: String,
    val youtubeEmbedUrlWithTimestamp: String,
    val formattedTimestamp: String,
    val hasDirectEmbedVideo: Boolean,
    val matchReason: String,
    val relatedChapters: List<LectureChapterSegment>,
    val alternativeTeachers: List<ResolvedLectureOption>
) {
    /**
     * Kullanıcı aynı video içinde başka bir bölüm/dakika çipine tıkladığında oynatıcıyı o saniyeye taşır.
     */
    fun selectChapter(chapter: LectureChapterSegment): ResolvedLectureRecommendation {
        val watchUrl = primaryVideo.watchUrlAt(chapter.startSeconds, chapter.chapterTitle)
        val embedUrl = primaryVideo.embedUrlAt(chapter.startSeconds, chapter.chapterTitle)
        val ts = chapter.formattedTimestamp
        val reason = buildString {
            append("${primaryVideo.teacherName} (${primaryVideo.channelName}) anlatımında ")
            append("'$ts' dakikasındaki \"${chapter.chapterTitle}\" bölümüne geçildi.")
        }
        return copy(
            primaryChapter = chapter,
            youtubeWatchUrlWithTimestamp = watchUrl,
            youtubeEmbedUrlWithTimestamp = embedUrl,
            formattedTimestamp = ts,
            matchReason = reason
        )
    }

    /**
     * Kullanıcı alternatif bir hocaya geçiş yaptığında o hocanın ilgili bölümünü ve çiplerini aktif eder.
     */
    fun selectTeacher(option: ResolvedLectureOption): ResolvedLectureRecommendation {
        val previousPrimaryOption = ResolvedLectureOption(
            video = primaryVideo,
            chapter = primaryChapter,
            matchScore = option.matchScore,
            youtubeWatchUrlWithTimestamp = youtubeWatchUrlWithTimestamp,
            youtubeEmbedUrlWithTimestamp = youtubeEmbedUrlWithTimestamp,
            formattedTimestamp = formattedTimestamp,
            hasDirectEmbedVideo = hasDirectEmbedVideo
        )
        val updatedAlternatives = buildList {
            add(previousPrimaryOption)
            addAll(alternativeTeachers.filter { it.video.id != option.video.id })
        }.distinctBy { it.video.teacherName }

        val reason = buildMatchReason(
            kazanim = matchedKazanim,
            questionType = detectedQuestionType,
            video = option.video,
            chapter = option.chapter
        )

        return copy(
            primaryVideo = option.video,
            primaryChapter = option.chapter,
            youtubeWatchUrlWithTimestamp = option.youtubeWatchUrlWithTimestamp,
            youtubeEmbedUrlWithTimestamp = option.youtubeEmbedUrlWithTimestamp,
            formattedTimestamp = option.formattedTimestamp,
            hasDirectEmbedVideo = option.hasDirectEmbedVideo,
            matchReason = reason,
            relatedChapters = option.video.chapters.sortedBy { it.startSeconds },
            alternativeTeachers = updatedAlternatives
        )
    }
}

private fun buildMatchReason(
    kazanim: OsymKazanim,
    questionType: OsymQuestionType,
    video: TopicLectureVideo,
    chapter: LectureChapterSegment
): String {
    val actionNote = if (video.hasVerifiedVideoId) {
        "Videonun ${chapter.formattedTimestamp} dakikasındaki \"${chapter.chapterTitle}\" bölümünden başlatıldı."
    } else {
        "${video.teacherName} anlatımında \"${chapter.chapterTitle}\" (${chapter.formattedTimestamp}) bölümü için odaklanıldı."
    }
    return "Bu soru ${kazanim.title} (${kazanim.code}) & ${questionType.label} odaklıdır → $actionNote"
}

/**
 * Türkiye'nin en popüler YKS hocalarının kazanım ve soru tipi bazlı dakika segmentlerini barındıran
 * bellek içi akıllı konu anlatım kataloğu.
 */
object TopicLectureVideoCatalog {

    val allVideos: List<TopicLectureVideo> = listOf(
        // =====================================================================
        // 1. AYT MATEMATİK — TÜREV (Eyüp B. öncelikli, Mert Hoca, Bıyıklı Mat)
        // =====================================================================
        TopicLectureVideo(
            id = "eyupb_ayt_turev_tekrar",
            videoId = "4gpHnUOCWEQ",
            searchQueryFallback = "Eyüp B AYT Tekrar Türev 1 konu anlatımı",
            title = "AYT Tekrar Türev-1: Tanım, Türev Alma Kuralları ve Zincir Kuralı",
            teacherName = "Eyüp B.",
            channelName = "Eyüp B. Matematik Geometri",
            teacherBadge = "AYT Derece Favorisi",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicIds = setOf("turev_alma_kurallari", "turev_uygulamalari"),
            topicKeywords = listOf(
                "türev", "turev", "türev alma", "türev tanımı", "zincir kuralı", "çarpım türevi",
                "bölüm türevi", "süreklilik", "f'(x)", "dy/dx", "teğet", "ekstremum", "artan", "azalan"
            ),
            kazanimCodes = setOf("MAT.12.5.2.1", "MAT.12.5.2.2", "MAT.12.5.3.1", "MAT.12.5.3.2", "MAT.12.5.3.3", "MAT.12.5.3.4"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "eyupb_turev_tanim_sureklilik",
                    chapterTitle = "Türev Tanımı, Limit ve Süreklilik İlişkisi",
                    startSeconds = 120,
                    endSeconds = 660,
                    kazanimIds = setOf("KAZ_TUREV_01"),
                    kazanimCodes = setOf("MAT.12.5.2.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("türev tanımı", "türevlenebilir", "sürekli", "sağdan türev", "soldan türev", "f(x)-f(a)", "kırılma noktası", "lim"),
                    keyFormulaSummary = "f'(a) = lim(x→a) [f(x) - f(a)] / (x - a). Bir noktada türev varsa fonksiyon o noktada mutlaka süreklidir; tersi her zaman doğru değildir.",
                    osymTip = "Öncüllü sorularda sivri uçlarda (mutlak değer kökleri) süreklilik olmasına rağmen sağdan-soldan türev eşit olmadığı için türev yoktur."
                ),
                LectureChapterSegment(
                    id = "eyupb_turev_alma_zincir",
                    chapterTitle = "Çarpım, Bölüm ve Bileşke (Zincir Kuralı) Türevi",
                    startSeconds = 680,
                    endSeconds = 1240,
                    kazanimIds = setOf("KAZ_TUREV_02"),
                    kazanimCodes = setOf("MAT.12.5.2.2"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("zincir kuralı", "çarpım türevi", "bölüm türevi", "bileşke", "f'(g(x))", "dy/dx", "polinom türevi", "türev alma"),
                    keyFormulaSummary = "(f·g)' = f'·g + f·g', (f/g)' = (f'·g - f·g')/g², [f(g(x))]' = f'(g(x)) · g'(x).",
                    osymTip = "Bileşke fonksiyon türevinde içinin türevini (g'(x)) çarpmayı unutmak ÖSYM'nin en klasik çeldiricisidir."
                ),
                LectureChapterSegment(
                    id = "eyupb_turev_geometrik_yorum",
                    chapterTitle = "Türevin Geometrik Yorumu ve Teğet-Normal Denklemleri",
                    startSeconds = 1260,
                    endSeconds = 1840,
                    kazanimIds = setOf("KAZ_TUREV_03"),
                    kazanimCodes = setOf("MAT.12.5.3.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("teğet", "normal", "eğim", "değme noktası", "teğet doğrusu", "geometrik yorum", "m_teğet", "paralel teğet"),
                    keyFormulaSummary = "x = a noktasındaki teğetin eğimi m_T = f'(a). Teğet denklemi: y - f(a) = f'(a)·(x - a). Normal eğimi: m_N = -1 / f'(a).",
                    osymTip = "Teğet doğrusu eğriye (a, f(a)) noktasında değiyorsa hem f(a) hem f'(a) değeri doğrudan doğru denkleminden okunur."
                ),
                LectureChapterSegment(
                    id = "eyupb_turev_artan_azalan",
                    chapterTitle = "Artan-Azalan Fonksiyonlar ve İşaret Tablosu",
                    startSeconds = 1860,
                    endSeconds = 2420,
                    kazanimIds = setOf("KAZ_TUREV_04"),
                    kazanimCodes = setOf("MAT.12.5.3.2"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("artan", "azalan", "daima artan", "f'(x) > 0", "f'(x) < 0", "monoton", "işaret tablosu"),
                    keyFormulaSummary = "f'(x) > 0 olduğu aralıkta f artan, f'(x) < 0 olduğu aralıkta f azalandır. Daima artan 3. derece polinomda Δ(f') ≤ 0 olmalıdır.",
                    osymTip = "Grafiğin f(x)'e mi yoksa f'(x)'e mi ait olduğuna mutlaka bak! f'(x) grafiğinde x ekseninin üstü artan, altı azalan bölgedir."
                ),
                LectureChapterSegment(
                    id = "eyupb_turev_ekstremum",
                    chapterTitle = "Yerel ve Mutlak Ekstremum Noktaları Analizi",
                    startSeconds = 2440,
                    endSeconds = 3000,
                    kazanimIds = setOf("KAZ_TUREV_05"),
                    kazanimCodes = setOf("MAT.12.5.3.3"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("ekstremum", "yerel maksimum", "yerel minimum", "mutlak maksimum", "kritik nokta", "f'(x) = 0", "çift katlı kök"),
                    keyFormulaSummary = "Ekstremum için f'(x) işaret değiştirmelidir (+ → - yerel maks, - → + yerel min). Çift katlı köklerde işaret değişmediği için ekstremum oluşmaz.",
                    osymTip = "Bir noktada türev olmasa bile (sivri uç veya kapalı aralık uç noktası) fonksiyon orada yerel/mutlak ekstremum yapabilir."
                ),
                LectureChapterSegment(
                    id = "eyupb_turev_maks_min",
                    chapterTitle = "Maksimum-Minimum Optimizasyon Problemleri",
                    startSeconds = 3020,
                    endSeconds = 3580,
                    kazanimIds = setOf("KAZ_TUREV_06"),
                    kazanimCodes = setOf("MAT.12.5.3.4"),
                    questionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.GORSEL_GEOMETRIK),
                    subTopicKeywords = listOf("en çok", "en az", "maksimum alan", "maksimum hacim", "en büyük değer", "optimizasyon", "en kısa uzaklık"),
                    keyFormulaSummary = "İstenen büyüklüğü tek değişkene indirge → A(x) oluştur → A'(x) = 0 kökünü bul → sınır ve anlamlılık kontrolü yap.",
                    osymTip = "Geometrik maksimum alan sorularında benzerlik veya parabol simetrisiyle tek değişkene geçmek işlemi yarı yarıya kısaltır."
                )
            )
        ),
        TopicLectureVideo(
            id = "eyupb_ayt_turev_uygulamalari",
            videoId = "Ir3aHhJ7SB8",
            searchQueryFallback = "Eyüp B Türev uygulamaları geometrik yorum ekstremum",
            title = "Türev Konu Anlatımı & Grafik Yorumu (Geometrik Yorum - Ekstremum)",
            teacherName = "Eyüp B.",
            channelName = "Eyüp B. Matematik Geometri",
            teacherBadge = "Grafik & Yorum Ustası",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicIds = setOf("turev_uygulamalari"),
            topicKeywords = listOf("türev uygulamaları", "geometrik yorum", "teğet", "artan", "azalan", "ekstremum", "maksimum", "minimum", "grafik"),
            kazanimCodes = setOf("MAT.12.5.3.1", "MAT.12.5.3.2", "MAT.12.5.3.3", "MAT.12.5.3.4"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "eyupb_tuyg_geometrik",
                    chapterTitle = "Türev Geometrik Yorumu ve Eğri-Teğet İlişkisi",
                    startSeconds = 180,
                    endSeconds = 1040,
                    kazanimIds = setOf("KAZ_TUREV_03"),
                    kazanimCodes = setOf("MAT.12.5.3.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.GORSEL_GEOMETRIK),
                    subTopicKeywords = listOf("teğet", "normal", "eğim", "değme noktası", "geometrik yorum"),
                    keyFormulaSummary = "Doğru eğriye x₀ noktasında teğetse f(x₀) = g(x₀) ve f'(x₀) = g'(x₀) eşitlikleri aynı anda sağlanır.",
                    osymTip = "Eğriye dışındaki bir noktadan çizilen teğetlerde değme noktasına (a, f(a)) deyip iki nokta eğimini f'(a)'ya eşitle."
                ),
                LectureChapterSegment(
                    id = "eyupb_tuyg_artan_ekstremum",
                    chapterTitle = "f'(x) Grafiğinden Artan-Azalan ve Ekstremum Okuma",
                    startSeconds = 1060,
                    endSeconds = 1980,
                    kazanimIds = setOf("KAZ_TUREV_04", "KAZ_TUREV_05"),
                    kazanimCodes = setOf("MAT.12.5.3.2", "MAT.12.5.3.3"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("artan", "azalan", "ekstremum", "yerel maksimum", "yerel minimum", "grafik", "işaret"),
                    keyFormulaSummary = "f'(x) grafiği x eksenini kesip işaret değiştiriyorsa o apsis ekstremumdur; x eksenine teğet geçiyorsa ekstremum değildir.",
                    osymTip = "f'(x) grafiğinin kendi tepe noktaları f(x)'in değil, f''(x)=0 (dönüm) noktalarıdır; ekstremumla karıştırma!"
                ),
                LectureChapterSegment(
                    id = "eyupb_tuyg_optimizasyon",
                    chapterTitle = "Yeni Nesil Maksimum-Minimum Optimizasyon Kurguları",
                    startSeconds = 2000,
                    endSeconds = 2940,
                    kazanimIds = setOf("KAZ_TUREV_06"),
                    kazanimCodes = setOf("MAT.12.5.3.4"),
                    questionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.GORSEL_GEOMETRIK),
                    subTopicKeywords = listOf("maksimum", "minimum", "en çok", "en az", "alan", "hacim", "optimizasyon"),
                    keyFormulaSummary = "Hedef fonksiyonu tek değişkene yaz, türevini sıfırla ve bulunan kökün maksimum mu minimum mu olduğunu kontrol et.",
                    osymTip = "Kâr-maliyet sorularında toplam kâr = (birim kâr) × (satış adedi) çarpımının türevi sıfırlanır."
                )
            )
        ),
        TopicLectureVideo(
            id = "merthoca_ayt_turev",
            videoId = "13gevHZ_IuI",
            searchQueryFallback = "Mert Hoca AYT Matematik Türev tek video konu anlatımı",
            title = "Türev Tek Video Konu Anlatımı | AYT Matematik Kampı",
            teacherName = "Mert Hoca",
            channelName = "Mert Hoca",
            teacherBadge = "Kamp Lideri",
            popularityRank = 2,
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicIds = setOf("turev_alma_kurallari", "turev_uygulamalari"),
            topicKeywords = listOf("türev", "turev", "teğet", "ekstremum", "artan", "azalan", "maksimum", "minimum", "zincir kuralı"),
            kazanimCodes = setOf("MAT.12.5.2.1", "MAT.12.5.2.2", "MAT.12.5.3.1", "MAT.12.5.3.2", "MAT.12.5.3.3", "MAT.12.5.3.4"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "mert_turev_kurallar",
                    chapterTitle = "Türev Tanımı ve Türev Alma Kuralları",
                    startSeconds = 785,
                    endSeconds = 14644,
                    kazanimIds = setOf("KAZ_TUREV_01", "KAZ_TUREV_02"),
                    kazanimCodes = setOf("MAT.12.5.2.1", "MAT.12.5.2.2"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("türev tanımı", "türev alma", "çarpım", "bölüm", "zincir", "sürekli"),
                    keyFormulaSummary = "d/dx (xⁿ) = n·xⁿ⁻¹, bileşke fonksiyonlarda dıştan içe doğru türev alınarak çarpılır.",
                    osymTip = "Köklü ifadelerde √u türevi = u' / (2√u) kısa kuralını kullanmak hız kazandırır."
                ),
                LectureChapterSegment(
                    id = "mert_turev_geometrik",
                    chapterTitle = "Türevin Geometrik Yorumu ve Teğet Soruları",
                    startSeconds = 14644,
                    endSeconds = 23207,
                    kazanimIds = setOf("KAZ_TUREV_03"),
                    kazanimCodes = setOf("MAT.12.5.3.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.GORSEL_GEOMETRIK),
                    subTopicKeywords = listOf("teğet", "normal", "eğim", "geometrik yorum"),
                    keyFormulaSummary = "Teğet eğimi m = tan(α) = f'(x₀). x eksenine paralel teğetlerde f'(x₀) = 0.",
                    osymTip = "Açı genişse eğim negatif (tan α < 0), darsa pozitiftir; işaret hatasına dikkat!"
                ),
                LectureChapterSegment(
                    id = "mert_turev_ekstremum_maksmin",
                    chapterTitle = "Artan-Azalan, Ekstremum ve Maksimum-Minimum",
                    startSeconds = 23207,
                    endSeconds = 33324,
                    kazanimIds = setOf("KAZ_TUREV_04", "KAZ_TUREV_05", "KAZ_TUREV_06"),
                    kazanimCodes = setOf("MAT.12.5.3.2", "MAT.12.5.3.3", "MAT.12.5.3.4"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("artan", "azalan", "ekstremum", "maksimum", "minimum", "en çok", "en az"),
                    keyFormulaSummary = "f'(x) = 0 köklerinin işaret tablosu artanlık/azalanlık ve yerel ekstremumları tek seferde verir.",
                    osymTip = "Kapalı [a, b] aralığında mutlak ekstremum sorulursa uç noktalar f(a) ve f(b) mutlaka kontrol edilmelidir."
                )
            )
        ),
        TopicLectureVideo(
            id = "biyikli_ayt_turev",
            videoId = "JPg1ikFOLxk",
            searchQueryFallback = "Bıyıklı Matematik 80 Günde AYT Matematik Türev",
            title = "80 Günde AYT Matematik Kampı | Türev Konu Anlatımı",
            teacherName = "Bıyıklı Matematik",
            channelName = "Bıyıklı Matematik",
            teacherBadge = "Adım Adım Temel",
            popularityRank = 3,
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicIds = setOf("turev_alma_kurallari", "turev_uygulamalari"),
            topicKeywords = listOf("türev", "turev", "türev tanımı", "teğet", "ekstremum", "artan", "azalan"),
            kazanimCodes = setOf("MAT.12.5.2.1", "MAT.12.5.2.2", "MAT.12.5.3.1", "MAT.12.5.3.2", "MAT.12.5.3.3"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "biyikli_turev_tanim",
                    chapterTitle = "Türev Tanımı ve Temel Alma Kuralları",
                    startSeconds = 795,
                    endSeconds = 5880,
                    kazanimIds = setOf("KAZ_TUREV_01", "KAZ_TUREV_02"),
                    kazanimCodes = setOf("MAT.12.5.2.1", "MAT.12.5.2.2"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("türev tanımı", "türev alma", "zincir", "süreklilik"),
                    keyFormulaSummary = "Değişim oranı limitiyle türev elde edilir; parçalı fonksiyonlarda kritik noktada önce süreklilik sonra sağ-sol türev bakılır.",
                    osymTip = "Limit ifadesi [f(a+h)-f(a)]/h biçimindeyse doğrudan f'(a) demektir; L'Hospital'e gerek kalmadan çözülür."
                ),
                LectureChapterSegment(
                    id = "biyikli_turev_uyg",
                    chapterTitle = "Geometrik Yorum, Artan-Azalan ve Ekstremum",
                    startSeconds = 5880,
                    endSeconds = 13500,
                    kazanimIds = setOf("KAZ_TUREV_03", "KAZ_TUREV_04", "KAZ_TUREV_05", "KAZ_TUREV_06"),
                    kazanimCodes = setOf("MAT.12.5.3.1", "MAT.12.5.3.2", "MAT.12.5.3.3", "MAT.12.5.3.4"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("teğet", "artan", "azalan", "ekstremum", "maksimum", "minimum"),
                    keyFormulaSummary = "Teğet eğimi = f'(a), ekstremum = işaret değiştiren türev kökü.",
                    osymTip = "Soru metninde 'yerel ekstremumu yoktur' ifadesi 3. derece polinomda f'(x)'in Δ ≤ 0 olduğunu söyler."
                )
            )
        ),

        // =====================================================================
        // 2. TYT & AYT MATEMATİK — FONKSİYONLAR (Eyüp B., Mert Hoca, Rehber Mat)
        // =====================================================================
        TopicLectureVideo(
            id = "eyupb_fonksiyonlar_full",
            videoId = "31704PYQm90",
            searchQueryFallback = "Eyüp B Fonksiyonlar TYT AYT konu anlatımı",
            title = "Fonksiyonlar Konu Anlatımı & ÖSYM Yorumları (TYT - AYT)",
            teacherName = "Eyüp B.",
            channelName = "Eyüp B. Matematik Geometri",
            teacherBadge = "Fonksiyon & Grafik Lideri",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicIds = setOf("fonksiyonlar", "tyt_fonksiyonlar"),
            topicKeywords = listOf(
                "fonksiyon", "fonksiyonlar", "tanım kümesi", "görüntü kümesi", "bileşke", "ters fonksiyon",
                "bire bir", "örten", "tek fonksiyon", "çift fonksiyon", "öteleme", "simetri", "dönüşüm", "grafik"
            ),
            kazanimCodes = setOf("MAT.10.2.1.1", "MAT.10.2.1.2", "MAT.10.2.1.3", "MAT.11.3.1.1", "MAT.11.3.2.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "eyupb_fonk_tanim_goruntu",
                    chapterTitle = "Fonksiyon Kavramı, Tanım ve Görüntü Kümesi",
                    startSeconds = 210,
                    endSeconds = 820,
                    kazanimIds = setOf("KAZ_FONK_01"),
                    kazanimCodes = setOf("MAT.10.2.1.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("tanım kümesi", "görüntü kümesi", "değer kümesi", "fonksiyon belirtir", "en geniş tanım"),
                    keyFormulaSummary = "f: A → B fonksiyonunda tanım kümesi A'da açıkta eleman kalmaz ve her eleman B'de yalnızca bir görüntüye eşlenir.",
                    osymTip = "Düşey doğru testinde bir doğru grafiği birden fazla noktada kesiyorsa o bağıntı fonksiyon değildir."
                ),
                LectureChapterSegment(
                    id = "eyupb_fonk_turleri",
                    chapterTitle = "Bire Bir, Örten, Tek ve Çift Fonksiyonlar",
                    startSeconds = 845,
                    endSeconds = 1460,
                    kazanimIds = setOf("KAZ_FONK_02"),
                    kazanimCodes = setOf("MAT.10.2.1.2"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf("bire bir", "örten", "içine", "tek fonksiyon", "çift fonksiyon", "birim fonksiyon", "sabit fonksiyon"),
                    keyFormulaSummary = "Çift: f(-x) = f(x) (y eksenine simetrik). Tek: f(-x) = -f(x) (orijine simetrik). Yatay doğru testi bire birliği ölçer.",
                    osymTip = "Polinom fonksiyon çift ise tek dereceli terimlerin katsayısı 0, tek ise çift dereceli ve sabit terimin katsayısı 0'dır."
                ),
                LectureChapterSegment(
                    id = "eyupb_fonk_bileske_ters",
                    chapterTitle = "Bileşke Fonksiyon (f∘g) ve Ters Fonksiyon (f⁻¹)",
                    startSeconds = 1480,
                    endSeconds = 2090,
                    kazanimIds = setOf("KAZ_FONK_03"),
                    kazanimCodes = setOf("MAT.10.2.1.3"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("bileşke", "fog", "gof", "ters fonksiyon", "f^-1", "f⁻¹"),
                    keyFormulaSummary = "(f∘g)(x) = f(g(x)), (f∘g)⁻¹ = g⁻¹∘f⁻¹. f(a) = b ⇔ f⁻¹(b) = a.",
                    osymTip = "Ters fonksiyon bulmakla vakit kaybetme! f⁻¹(k) = x yerine f(x) = k denkleminden çözmek çok daha hızlıdır."
                ),
                LectureChapterSegment(
                    id = "eyupb_fonk_grafik_degisim",
                    chapterTitle = "Fonksiyon Grafikleri Okuma ve Ortalama Değişim Hızı",
                    startSeconds = 2115,
                    endSeconds = 2740,
                    kazanimIds = setOf("KAZ_FONK_04"),
                    kazanimCodes = setOf("MAT.11.3.1.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("grafik", "ortalama değişim", "eksen", "kesişim", "f(x)=0", "pozitif", "negatif"),
                    keyFormulaSummary = "[a, b] aralığında ortalama değişim hızı = [f(b) - f(a)] / (b - a). x eksenini kestiği noktalar f(x) = 0 kökleridir.",
                    osymTip = "f(x+2) grafiği verildiyse f(5) değerini bulmak için x yerine 3 yazmalısın; eksen üzerindeki sayı x'tir!"
                ),
                LectureChapterSegment(
                    id = "eyupb_fonk_oteleme_simetri",
                    chapterTitle = "Fonksiyonlarda Öteleme, Simetri ve |f(x)| Dönüşümleri",
                    startSeconds = 2760,
                    endSeconds = 3420,
                    kazanimIds = setOf("KAZ_FONK_05"),
                    kazanimCodes = setOf("MAT.11.3.2.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("öteleme", "simetri", "dönüşüm", "f(x+a)", "f(x-a)", "|f(x)|", "birim sağa", "birim yukarı"),
                    keyFormulaSummary = "f(x-a)+b: a birim sağa, b birim yukarı. -f(x): x eksenine göre simetri. f(-x): y eksenine göre simetri. |f(x)|: x ekseni altı yukarı katlanır.",
                    osymTip = "Parantez içi işlemler x ekseninde ters yönlü etki eder: f(x+3) grafiği 3 birim sola öteler."
                )
            )
        ),
        TopicLectureVideo(
            id = "merthoca_fonksiyonlar_kamp",
            videoId = "vYY8lmKe7tQ",
            searchQueryFallback = "Mert Hoca Fonksiyonlar Tek Video TYT AYT Matematik Kampı",
            title = "Fonksiyonlar Tek Video Konu Anlatımı | TYT-AYT Kampı",
            teacherName = "Mert Hoca",
            channelName = "Mert Hoca",
            teacherBadge = "70 Günde TYT / 90 Günde AYT",
            popularityRank = 2,
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            subjectName = "Matematik",
            topicIds = setOf("tyt_fonksiyonlar", "fonksiyonlar"),
            topicKeywords = listOf("fonksiyon", "tanım kümesi", "bileşke", "ters fonksiyon", "grafik", "öteleme"),
            kazanimCodes = setOf("MAT.10.2.1.1", "MAT.10.2.1.2", "MAT.10.2.1.3", "MAT.11.3.1.1", "MAT.11.3.2.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "mert_fonk_temel",
                    chapterTitle = "Fonksiyon Kavramı, Türleri ve Değer Bulma",
                    startSeconds = 245,
                    endSeconds = 13327,
                    kazanimIds = setOf("KAZ_FONK_01", "KAZ_FONK_02"),
                    kazanimCodes = setOf("MAT.10.2.1.1", "MAT.10.2.1.2"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("tanım kümesi", "görüntü", "bire bir", "örten", "tek", "çift"),
                    keyFormulaSummary = "Doğrusal fonksiyon f(x) = ax + b, sabit fonksiyon f(x) = c, birim fonksiyon f(x) = x.",
                    osymTip = "f(x+y) = f(x) + f(y) tipindeki fonksiyonel denklemlerde küçük değerlerden örüntü yakala."
                ),
                LectureChapterSegment(
                    id = "mert_fonk_bileske_grafik",
                    chapterTitle = "Bileşke, Ters Fonksiyon ve Grafik Okuma",
                    startSeconds = 13327,
                    endSeconds = 24728,
                    kazanimIds = setOf("KAZ_FONK_03", "KAZ_FONK_04", "KAZ_FONK_05"),
                    kazanimCodes = setOf("MAT.10.2.1.3", "MAT.11.3.1.1", "MAT.11.3.2.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("bileşke", "ters", "grafik", "öteleme", "simetri"),
                    keyFormulaSummary = "(f∘f⁻¹)(x) = x. Grafikten ters değer okurken y ekseninden grafiğe, oradan x eksenine inilir.",
                    osymTip = "Grafik kesişim noktalarında f(x) = g(x) eşitliği sağlanır."
                )
            )
        ),

        // =====================================================================
        // 3. AYT MATEMATİK — LİMİT VE SÜREKLİLİK (Eyüp B., Mert Hoca, Bıyıklı Mat)
        // =====================================================================
        TopicLectureVideo(
            id = "eyupb_ayt_limit",
            videoId = "PxNw6G1m7wk",
            searchQueryFallback = "Eyüp B Limit ve Süreklilik konu anlatımı",
            title = "Limit ve Süreklilik Konu Anlatımı | AYT Matematik",
            teacherName = "Eyüp B.",
            channelName = "Eyüp B. Matematik Geometri",
            teacherBadge = "LTI Uzmanı",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicIds = setOf("limit_ve_sureklilik"),
            topicKeywords = listOf("limit", "lim", "süreklilik", "sağdan limit", "soldan limit", "belirsizlik", "0/0", "parçalı"),
            kazanimCodes = setOf("MAT.12.5.1.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "eyupb_limit_sag_sol_grafik",
                    chapterTitle = "Sağdan-Soldan Limit ve Grafik Üzerinde Limit Okuma",
                    startSeconds = 320,
                    endSeconds = 1120,
                    kazanimIds = setOf("KAZ_AYT_MAT_LIMIT"),
                    kazanimCodes = setOf("MAT.12.5.1.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("sağdan limit", "soldan limit", "grafik", "limit vardır", "yaklaşma", "bileşke limit"),
                    keyFormulaSummary = "lim(x→a⁺) f(x) = lim(x→a⁻) f(x) = L ise x = a noktasında limit vardır ve L'dir. Noktanın içi boş olsa bile limit olabilir.",
                    osymTip = "Bileşke fonksiyon limitinde f(g(x)) iç fonksiyonun değere alttan (⁻) mı üstten (⁺) mi yaklaştığını mutlaka işaretle!"
                ),
                LectureChapterSegment(
                    id = "eyupb_limit_belirsizlik",
                    chapterTitle = "0/0 Belirsizliği, Çarpanlara Ayırma ve Trigonometrik Limit",
                    startSeconds = 1140,
                    endSeconds = 1920,
                    kazanimIds = setOf("KAZ_AYT_MAT_LIMIT"),
                    kazanimCodes = setOf("MAT.12.5.1.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("0/0", "belirsizlik", "çarpanlara ayırma", "eşlenik", "sinx/x", "sadeleştirme"),
                    keyFormulaSummary = "0/0 belirsizliğinde pay ve paydayı sıfır yapan (x - a) çarpanı sadeleştirilir; köklü ifadelerde eşlenikle çarpılır.",
                    osymTip = "Payda sıfır olurken limitin reel sayı çıkması için payın da o noktada mutlaka 0 olması gerekir."
                ),
                LectureChapterSegment(
                    id = "eyupb_limit_sureklilik",
                    chapterTitle = "Süreklilik Şartları ve Parçalı Fonksiyon Analizi",
                    startSeconds = 1950,
                    endSeconds = 2710,
                    kazanimIds = setOf("KAZ_AYT_MAT_LIMIT"),
                    kazanimCodes = setOf("MAT.12.5.1.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf("süreklilik", "sürekli", "süreksiz", "parçalı fonksiyon", "kritik nokta", "gerçel sayılarda sürekli"),
                    keyFormulaSummary = "Süreklilik şartı: lim(x→a⁺) f(x) = lim(x→a⁻) f(x) = f(a). Rasyonel fonksiyonlarda paydayı 0 yapan noktalarda süreklilik yoktur.",
                    osymTip = "Soru 'gerçel sayılarda sürekli' diyorsa rasyonel ifadenin paydasının reel kökü yoktur (Δ < 0)."
                )
            )
        ),
        TopicLectureVideo(
            id = "merthoca_ayt_limit",
            videoId = "3w3tjJbkJAM",
            searchQueryFallback = "Mert Hoca 90 Günde AYT Matematik Limit ve Süreklilik",
            title = "Limit ve Süreklilik Konu Anlatımı | 90 Günde AYT Kampı",
            teacherName = "Mert Hoca",
            channelName = "Mert Hoca",
            teacherBadge = "Kamp Lideri",
            popularityRank = 2,
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicIds = setOf("limit_ve_sureklilik"),
            topicKeywords = listOf("limit", "süreklilik", "belirsizlik", "sağdan", "soldan"),
            kazanimCodes = setOf("MAT.12.5.1.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "mert_limit_grafik",
                    chapterTitle = "Grafikte Limit ve Sağdan-Soldan Yaklaşım",
                    startSeconds = 822,
                    endSeconds = 13215,
                    kazanimIds = setOf("KAZ_AYT_MAT_LIMIT"),
                    kazanimCodes = setOf("MAT.12.5.1.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("limit", "grafik", "sağdan", "soldan"),
                    keyFormulaSummary = "Sıçrama (kopma) olan noktalarda limit yoktur; tek nokta boşluğunda limit vardır.",
                    osymTip = "f(x) ve g(x) tek başına limitsiz olsa bile toplam veya çarpımlarının limiti olabilir!"
                ),
                LectureChapterSegment(
                    id = "mert_limit_sureklilik",
                    chapterTitle = "0/0 Belirsizliği ve Süreklilik Problemleri",
                    startSeconds = 13215,
                    endSeconds = 24471,
                    kazanimIds = setOf("KAZ_AYT_MAT_LIMIT"),
                    kazanimCodes = setOf("MAT.12.5.1.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("belirsizlik", "0/0", "süreklilik", "parçalı"),
                    keyFormulaSummary = "Süreklilik = Limit varlığı + Noktadaki tanımlı değerle eşitlik.",
                    osymTip = "Mutlak değer içini sıfır yapan noktalarda sağdan ve soldan işaret değiştirerek çık."
                )
            )
        ),

        // =====================================================================
        // 4. AYT MATEMATİK — İNTEGRAL (Eyüp B., Mert Hoca, Bıyıklı Mat)
        // =====================================================================
        TopicLectureVideo(
            id = "eyupb_ayt_integral",
            videoId = "I3da1coBPRc",
            searchQueryFallback = "Eyüp B AYT Tekrar İntegral 1 İntegral Alma",
            title = "AYT Tekrar İntegral: İntegral Alma, Değişken Değiştirme ve Alan",
            teacherName = "Eyüp B.",
            channelName = "Eyüp B. Matematik Geometri",
            teacherBadge = "AYT Derece Favorisi",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicIds = setOf("belirsiz_integral", "belirli_integral_alan"),
            topicKeywords = listOf("integral", "belirsiz integral", "belirli integral", "riemann", "değişken değiştirme", "alan", "dx", "boyalı bölge"),
            kazanimCodes = setOf("MAT.12.6.2.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "eyupb_int_belirsiz",
                    chapterTitle = "Belirsiz İntegral ve Türev-İntegral Ters İlişkisi",
                    startSeconds = 150,
                    endSeconds = 840,
                    kazanimIds = setOf("KAZ_AYT_MAT_INTEGRAL"),
                    kazanimCodes = setOf("MAT.12.6.2.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("belirsiz integral", "diferansiyel", "dx", "türev-integral", "sabit c"),
                    keyFormulaSummary = "∫xⁿ dx = xⁿ⁺¹/(n+1) + c (n ≠ -1). d/dx [∫f(x) dx] = f(x) ve ∫d(f(x)) = f(x) + c.",
                    osymTip = "Diferansiyel d(f(x)) = f'(x)·dx dönüşümünü görmek birçok karmaşık integrali tek satırda çözer."
                ),
                LectureChapterSegment(
                    id = "eyupb_int_degisken",
                    chapterTitle = "Değişken Değiştirme Yöntemi ve Belirli İntegral Sınırları",
                    startSeconds = 860,
                    endSeconds = 1580,
                    kazanimIds = setOf("KAZ_AYT_MAT_INTEGRAL"),
                    kazanimCodes = setOf("MAT.12.6.2.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("değişken değiştirme", "u dönüşümü", "sınır değiştirme", "belirli integral", "tek-çift simetri"),
                    keyFormulaSummary = "u = g(x) ⇒ du = g'(x)dx. Belirli integralde u dönüşümü yapıldığında alt ve üst sınırlar da g(a), g(b) olarak güncellenir.",
                    osymTip = "Simetrik [-a, a] aralığında tek fonksiyonun belirli integrali 0, çift fonksiyonunki 2·∫₀ᵃ f(x)dx olur."
                ),
                LectureChapterSegment(
                    id = "eyupb_int_alan_riemann",
                    chapterTitle = "Riemann Toplamı ve Eğri Altında Kalan Alan Hesabı",
                    startSeconds = 1600,
                    endSeconds = 2320,
                    kazanimIds = setOf("KAZ_AYT_MAT_INTEGRAL"),
                    kazanimCodes = setOf("MAT.12.6.2.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.GORSEL_GEOMETRIK),
                    subTopicKeywords = listOf("alan", "boyalı bölgenin alanı", "iki eğri arası", "riemann", "grafik", "parabol alan"),
                    keyFormulaSummary = "Alan = ∫ₐᵇ (üstteki eğri - alttaki eğri) dx. x ekseni altındaki bölgelerde integral negatif çıktığından alan için eksiyle çarpılır.",
                    osymTip = "y = ax² parabolünde dikdörtgen içindeki alan oranı her zaman S ve 2S (1:2) kuralını sağlar!"
                )
            )
        ),
        TopicLectureVideo(
            id = "merthoca_ayt_integral",
            videoId = "2A267xG9NiY",
            searchQueryFallback = "Mert Hoca 90 Günde AYT Matematik İntegral konu anlatımı",
            title = "İntegral ve Alan Hesabı | 90 Günde AYT Matematik Kampı",
            teacherName = "Mert Hoca",
            channelName = "Mert Hoca",
            teacherBadge = "Kamp Lideri",
            popularityRank = 2,
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicIds = setOf("belirsiz_integral", "belirli_integral_alan"),
            topicKeywords = listOf("integral", "alan", "değişken değiştirme", "riemann"),
            kazanimCodes = setOf("MAT.12.6.2.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "mert_int_kurallar",
                    chapterTitle = "Belirsiz İntegral ve Değişken Değiştirme",
                    startSeconds = 847,
                    endSeconds = 18156,
                    kazanimIds = setOf("KAZ_AYT_MAT_INTEGRAL"),
                    kazanimCodes = setOf("MAT.12.6.2.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("belirsiz", "değişken değiştirme", "dx"),
                    keyFormulaSummary = "Türevi içeride duran ifadeye u diyerek integrali polinom formuna çevir.",
                    osymTip = "Sınırları eşit ∫ₐᵃ f(x)dx = 0 ve sınırlar yer değiştirirse integral işaret değiştirir."
                ),
                LectureChapterSegment(
                    id = "mert_int_alan",
                    chapterTitle = "Belirli İntegral, Grafik ve Alan Uygulamaları",
                    startSeconds = 18156,
                    endSeconds = 34000,
                    kazanimIds = setOf("KAZ_AYT_MAT_INTEGRAL"),
                    kazanimCodes = setOf("MAT.12.6.2.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.GORSEL_GEOMETRIK),
                    subTopicKeywords = listOf("alan", "boyalı", "riemann", "grafik"),
                    keyFormulaSummary = "f ve f⁻¹ grafiklerinin eksenlerle oluşturduğu dikdörtgen toplamı = b·f(b) - a·f(a).",
                    osymTip = "Parçalı veya mutlak değerli integrallerde kritik noktada integrali bölerek hesapla."
                )
            )
        ),

        // =====================================================================
        // 5. AYT MATEMATİK — TRİGONOMETRİ (Eyüp B., Mert Hoca, Rehber Mat)
        // =====================================================================
        TopicLectureVideo(
            id = "eyupb_ayt_trigonometri",
            videoId = "gJkrwUcOgbo",
            searchQueryFallback = "Eyüp B AYT Tekrar Trigonometri 1 konu anlatımı",
            title = "AYT Tekrar Trigonometri: Birim Çember, Toplam-Fark, Yarım Açı ve Denklemler",
            teacherName = "Eyüp B.",
            channelName = "Eyüp B. Matematik Geometri",
            teacherBadge = "AYT Derece Favorisi",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicIds = setOf("trigonometri_ayt_mat"),
            topicKeywords = listOf("trigonometri", "sin", "cos", "tan", "cot", "sec", "csc", "birim çember", "yarım açı", "toplam fark", "kosinüs teoremi"),
            kazanimCodes = setOf("MAT.12.3.1.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "eyupb_trigo_birim_ozdeslik",
                    chapterTitle = "Birim Çember, İndirgeme ve Trigonometrik Özdeşlikler",
                    startSeconds = 280,
                    endSeconds = 1070,
                    kazanimIds = setOf("KAZ_AYT_MAT_TRIGO"),
                    kazanimCodes = setOf("MAT.12.3.1.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GORSEL_GEOMETRIK),
                    subTopicKeywords = listOf("birim çember", "özdeşlik", "indirgeme", "sec", "csc", "sin²+cos²", "sıralama"),
                    keyFormulaSummary = "sin²x + cos²x = 1, tanx·cotx = 1, secx = 1/cosx, cscx = 1/sinx. π/2 ve 3π/2'de isim değişir.",
                    osymTip = "Birim çemberde uzunluk ve alan sorularında noktanın koordinatları (cosα, sinα), teğet kesimi tanα ve cotα'dır."
                ),
                LectureChapterSegment(
                    id = "eyupb_trigo_toplam_yarim",
                    chapterTitle = "Toplam-Fark ve Yarım Açı Formülleri (Geometrik Kurgular)",
                    startSeconds = 1090,
                    endSeconds = 1850,
                    kazanimIds = setOf("KAZ_AYT_MAT_TRIGO"),
                    kazanimCodes = setOf("MAT.12.3.1.1"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("toplam fark", "yarım açı", "sin2x", "cos2x", "tan(a+b)", "kosinüs teoremi", "sinüs teoremi"),
                    keyFormulaSummary = "sin2x = 2sinx·cosx; cos2x = cos²x - sin²x = 2cos²x - 1 = 1 - 2sin²x; tan(a±b) = (tana ± tanb)/(1 ∓ tana·tanb).",
                    osymTip = "1'i yok etmek için cos2x'in uygun açılımını seç; kareli/dikdörtgenli şekillerde açıyı α+β şeklinde parçala!"
                ),
                LectureChapterSegment(
                    id = "eyupb_trigo_denklemler",
                    chapterTitle = "Trigonometrik Denklemler ve Kök Sayısı Analizi",
                    startSeconds = 1860,
                    endSeconds = 2640,
                    kazanimIds = setOf("KAZ_AYT_MAT_TRIGO"),
                    kazanimCodes = setOf("MAT.12.3.1.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("trigonometrik denklem", "kök", "çözüm kümesi", "periyot", "aralıkta kaç kök"),
                    keyFormulaSummary = "sinx = sinα ⇒ x = α + 2kπ veya x = (π-α) + 2kπ. cosx = cosα ⇒ x = ±α + 2kπ. tanx = tanα ⇒ x = α + kπ.",
                    osymTip = "Denklemde sadeleştirme yaparken sadeleştirdiğin çarpanın sıfır olduğu kökleri kaybetme; paydayı sıfır yapan kökleri ise ele!"
                )
            )
        ),
        TopicLectureVideo(
            id = "merthoca_ayt_trigonometri",
            videoId = "avF94jdG-oA",
            searchQueryFallback = "Mert Hoca AYT Matematik Trigonometri Full Tekrar",
            title = "Trigonometri Full Tekrar | 90 Günde AYT Matematik",
            teacherName = "Mert Hoca",
            channelName = "Mert Hoca",
            teacherBadge = "Kamp Lideri",
            popularityRank = 2,
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicIds = setOf("trigonometri_ayt_mat"),
            topicKeywords = listOf("trigonometri", "sin", "cos", "tan", "yarım açı", "toplam fark", "birim çember"),
            kazanimCodes = setOf("MAT.12.3.1.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "mert_trigo_1",
                    chapterTitle = "Birim Çember, Kosinüs-Sinüs Teoremi ve Özdeşlikler",
                    startSeconds = 300,
                    endSeconds = 1400,
                    kazanimIds = setOf("KAZ_AYT_MAT_TRIGO"),
                    kazanimCodes = setOf("MAT.12.3.1.1"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("birim çember", "kosinüs", "sinüs", "özdeşlik"),
                    keyFormulaSummary = "a² = b² + c² - 2bc·cosA (Kosinüs Teoremi), a/sinA = 2R (Sinüs Teoremi).",
                    osymTip = "Bölge işaretini belirlerken her zaman ilk verilen fonksiyonun bölgesine göre işaret al."
                ),
                LectureChapterSegment(
                    id = "mert_trigo_2",
                    chapterTitle = "Toplam-Fark, Yarım Açı ve Denklemler",
                    startSeconds = 1440,
                    endSeconds = 2680,
                    kazanimIds = setOf("KAZ_AYT_MAT_TRIGO"),
                    kazanimCodes = setOf("MAT.12.3.1.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("toplam fark", "yarım açı", "denklem", "kök"),
                    keyFormulaSummary = "sin(a±b) = sina·cosb ± cosa·sinb; cos(a±b) = cosa·cosb ∓ sina·sinb.",
                    osymTip = "a·sinx + b·cosx = c denklemlerinde tanα = b/a dönüşümüyle tek fonksiyona indir."
                )
            )
        ),

        // =====================================================================
        // 6. AYT MATEMATİK — POLİNOM, PARABOL, EŞİTSİZLİK, LOGARİTMA, DİZİLER
        // =====================================================================
        TopicLectureVideo(
            id = "eyupb_ayt_polinom_parabol_esitsizlik",
            videoId = "0OtwomXXTZg",
            searchQueryFallback = "Eyüp B AYT Matematik Polinom Parabol Eşitsizlik konu anlatımı",
            title = "Polinomlar, Parabol ve 2. Dereceden Eşitsizlikler | AYT Matematik",
            teacherName = "Eyüp B.",
            channelName = "Eyüp B. Matematik Geometri",
            teacherBadge = "AYT Cebir Uzmanı",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicIds = setOf("polinomlar", "tyt_polinomlar", "parabol", "ikinci_derece_denklemler", "tyt_ikinci_derece_denklemler", "karmasik_sayilar", "ikinci_derece_esitsizlikler"),
            topicKeywords = listOf("polinom", "p(x)", "başkatsayı", "kalan", "parabol", "tepe noktası", "simetri ekseni", "eşitsizlik", "karmaşık sayı", "kökler toplamı"),
            kazanimCodes = setOf("MAT.10.3.1.1", "MAT.11.4.1.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "eyupb_polinom_bolme",
                    chapterTitle = "Polinom Kurma, Bölme-Kalan ve Katsayı İlişkileri",
                    startSeconds = 230,
                    endSeconds = 1120,
                    kazanimIds = setOf("KAZ_AYT_MAT_POLINOM_PARABOL"),
                    kazanimCodes = setOf("MAT.10.3.1.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("polinom", "p(x)", "başkatsayı", "kalan", "derece", "sabit terim", "katsayılar toplamı"),
                    keyFormulaSummary = "P(x) = a·(x-x₁)(x-x₂)... + K(x). Katsayılar toplamı P(1), sabit terim P(0). (x-a) ile bölümünden kalan P(a).",
                    osymTip = "ÖSYM'nin en sevdiği kalıp: P(a) = P(b) = c verilirse P(x) = k·(x-a)(x-b) + c şeklinde kur!"
                ),
                LectureChapterSegment(
                    id = "eyupb_parabol_tepe",
                    chapterTitle = "Parabol Grafiği, Tepe Noktası ve Simetri Ekseni",
                    startSeconds = 1150,
                    endSeconds = 2040,
                    kazanimIds = setOf("KAZ_AYT_MAT_POLINOM_PARABOL"),
                    kazanimCodes = setOf("MAT.10.3.1.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.GORSEL_GEOMETRIK),
                    subTopicKeywords = listOf("parabol", "tepe noktası", "simetri ekseni", "r = -b/2a", "kökler toplamı", "karmaşık sayı", "diskriminant"),
                    keyFormulaSummary = "Tepe noktası T(r, k): r = -b/(2a) = (x₁+x₂)/2, k = f(r). Parabol denklemi: y = a(x-r)² + k.",
                    osymTip = "Parabolde f(m) = f(n) ise simetri ekseni r = (m+n)/2 olur; geometri sorularında tepe noktası simetrisini kullan!"
                ),
                LectureChapterSegment(
                    id = "eyupb_esitsizlik_tablo",
                    chapterTitle = "2. Dereceden Eşitsizlik Sistemleri ve İşaret Tablosu",
                    startSeconds = 2070,
                    endSeconds = 2890,
                    kazanimIds = setOf("KAZ_AYT_MAT_ESITSIZLIK"),
                    kazanimCodes = setOf("MAT.11.4.1.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("eşitsizlik", "işaret tablosu", "çözüm kümesi", "çift katlı kök", "f(x).g(x)"),
                    keyFormulaSummary = "Çift katlı köklerde işaret değişmez; paydayı sıfır yapan kökler eşitsizlik '≤ / ≥' olsa bile çözüm kümesine alınmaz.",
                    osymTip = "Grafik üzerinden verilen eşitsizliklerde x eksenine teğet olunan noktalar çift katlı köktür!"
                )
            )
        ),
        TopicLectureVideo(
            id = "eyupb_ayt_logaritma_diziler",
            videoId = "A_EIE6mGxS4",
            searchQueryFallback = "Eyüp B AYT Matematik Logaritma ve Diziler konu anlatımı",
            title = "Logaritma ve Aritmetik-Geometrik Diziler | AYT Matematik",
            teacherName = "Eyüp B.",
            channelName = "Eyüp B. Matematik Geometri",
            teacherBadge = "AYT Derece Favorisi",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicIds = setOf("logaritma", "diziler"),
            topicKeywords = listOf("logaritma", "log", "ln", "üstel", "dizi", "aritmetik dizi", "geometrik dizi", "genel terim"),
            kazanimCodes = setOf("MAT.12.1.2.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "eyupb_log_ozellikler",
                    chapterTitle = "Logaritma Tanım Kümesi, Özellikleri ve Üstel Modelleme",
                    startSeconds = 250,
                    endSeconds = 1320,
                    kazanimIds = setOf("KAZ_AYT_MAT_LOG_DIZI"),
                    kazanimCodes = setOf("MAT.12.1.2.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("logaritma", "log", "ln", "taban değiştirme", "üstel", "tanım aralığı"),
                    keyFormulaSummary = "logₐ(b) tanım şartı: a > 0, a ≠ 1, b > 0. logₐ(x·y) = logₐx + logₐy, logₐb = lnb / lna.",
                    osymTip = "Logaritmik denklem çözdükten sonra bulduğun kökleri mutlaka tanım kümesine (taban > 0, ≠ 1 ve içi > 0) göre ele!"
                ),
                LectureChapterSegment(
                    id = "eyupb_diziler_ag",
                    chapterTitle = "Aritmetik ve Geometrik Diziler, İndirgeme Bağıntıları",
                    startSeconds = 1360,
                    endSeconds = 2460,
                    kazanimIds = setOf("KAZ_AYT_MAT_LOG_DIZI"),
                    kazanimCodes = setOf("MAT.12.1.2.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("dizi", "aritmetik dizi", "geometrik dizi", "ortak fark", "ortak çarpan", "genel terim", "fibonacci"),
                    keyFormulaSummary = "Aritmetik: aₙ = a₁ + (n-1)d, Sₙ = (n/2)(a₁+aₙ). Geometrik: aₙ = a₁·rⁿ⁻¹, ortanca terim aₖ² = aₖ₋ₚ·aₖ₊ₚ.",
                    osymTip = "Dizi tanım kümesi yalnızca pozitif tam sayılardır (ℤ⁺); n ≤ 0 yapan değerleri asla alma."
                )
            )
        ),

        // =====================================================================
        // 7. TYT MATEMATİK — PROBLEMLER & TEMEL CEBİR (Mert Hoca, Rehber Mat, Eyüp B.)
        // =====================================================================
        TopicLectureVideo(
            id = "merthoca_tyt_problemler",
            videoId = "kmEuRVMiy38",
            searchQueryFallback = "Mert Hoca 70 Günde TYT Matematik Problemler Part 1",
            title = "70 Günde TYT Matematik | Problemler Konu Anlatımı & Modelleme",
            teacherName = "Mert Hoca",
            channelName = "Mert Hoca",
            teacherBadge = "TYT Problem Favorisi",
            popularityRank = 1,
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            subjectName = "Matematik",
            topicIds = setOf(
                "sayi_problemleri", "kesir_problemleri", "yas_problemleri", "isci_problemleri",
                "hareket_problemleri", "yuzde_kar_zarar_problemleri", "karisim_problemleri",
                "grafik_problemleri", "rutin_olmayan_problemler"
            ),
            topicKeywords = listOf(
                "problem", "sayı problemi", "kesir", "yaş", "hız", "hareket", "yüzde", "kâr",
                "zarar", "indirim", "karışım", "daire grafiği", "rutin olmayan", "işçi"
            ),
            kazanimCodes = setOf("MAT.9.3.5.1", "MAT.9.3.5.2", "MAT.9.3.5.3", "MAT.9.3.5.4", "MAT.9.3.5.5", "MAT.9.3.5.6"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "mert_prob_sayi_kesir",
                    chapterTitle = "Sayı ve Kesir Problemlerinde Denklem Kurma",
                    startSeconds = 372,
                    endSeconds = 11888,
                    kazanimIds = setOf("KAZ_PROB_01"),
                    kazanimCodes = setOf("MAT.9.3.5.1"),
                    questionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("sayı problemi", "kesir", "parça", "kuyruk", "bilet", "kalan", "katı", "fazlası"),
                    keyFormulaSummary = "Kesir problemlerinde bütüne paydaların EKOK'u (örn. 12x, 20x) vererek kesirli işlemden kurtul.",
                    osymTip = "Kalanın 1/3'ü ile tümünün 1/3'ü arasındaki farkı soruyu okurken altını çizerek ayır."
                ),
                LectureChapterSegment(
                    id = "mert_prob_yas",
                    chapterTitle = "Yaş Problemleri ve Sabit Yaş Farkı Kuralı",
                    startSeconds = 17072,
                    endSeconds = 19500,
                    kazanimIds = setOf("KAZ_PROB_02"),
                    kazanimCodes = setOf("MAT.9.3.5.2"),
                    questionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("yaş", "yaşında", "doğduğunda", "yıl sonra", "yıl önce", "yaş ortalaması"),
                    keyFormulaSummary = "İki kişi arasındaki yaş farkı yıllar geçse de asla değişmez. n kişinin t yıl sonraki yaşları toplamı n·t artar.",
                    osymTip = "'Ben senin yaşındayken' cümlesinde tablo yap: (Şimdi) ve (O zaman) satırlarında yaş farkları eşittir."
                ),
                LectureChapterSegment(
                    id = "mert_prob_hiz_hareket",
                    chapterTitle = "Hız, Hareket, Dairesel Pist ve Ortalama Hız",
                    startSeconds = 11888,
                    endSeconds = 14348,
                    kazanimIds = setOf("KAZ_PROB_03"),
                    kazanimCodes = setOf("MAT.9.3.5.3"),
                    questionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("hız", "hareket", "pist", "km/sa", "karşılaşma", "ortalama hız", "tünel", "tren"),
                    keyFormulaSummary = "x = V·t. Zıt yönde t = x/(V₁+V₂), aynı yönde t = x/|V₁-V₂|. V_ort = Toplam Yol / Toplam Zaman.",
                    osymTip = "Ortalama hız asla (V₁+V₂)/2 değildir (süreler eşit değilse); daima Toplam Yol / Toplam Süre formülünü kullan!"
                ),
                LectureChapterSegment(
                    id = "mert_prob_yuzde_kar_zarar",
                    chapterTitle = "Yüzde, Kâr-Zarar, İndirim ve Karışım Problemleri",
                    startSeconds = 14348,
                    endSeconds = 17072,
                    kazanimIds = setOf("KAZ_PROB_04"),
                    kazanimCodes = setOf("MAT.9.3.5.4"),
                    questionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("yüzde", "kâr", "zarar", "indirim", "maliyet", "etiket", "karışım", "zam", "fire"),
                    keyFormulaSummary = "Maliyete 100x ver. %a kâr → (100+a)x; ardından %b indirim → (100+a)(100-b)/100·x. Karışım: m₁·y₁ + m₂·y₂ = (m₁+m₂)·y_son.",
                    osymTip = "Üst üste %20 zam ve %20 indirim başlangıç fiyatına dönmez; 100 → 120 → 96 (%4 zarar) olur!"
                ),
                LectureChapterSegment(
                    id = "mert_prob_grafik_rutin",
                    chapterTitle = "Grafik Okuma ve Rutin Olmayan Mantık Problemleri",
                    startSeconds = 19500,
                    endSeconds = 21800,
                    kazanimIds = setOf("KAZ_PROB_05", "KAZ_PROB_06"),
                    kazanimCodes = setOf("MAT.9.3.5.5", "MAT.9.3.5.6"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf("daire grafiği", "sütun grafiği", "merkez açı", "rutin olmayan", "en az kaç", "en çok kaç", "strateji", "tablo"),
                    keyFormulaSummary = "Daire grafiğinde 360° toplam miktara karşılık gelir; her dilimin oranı = Merkez Açı / 360°.",
                    osymTip = "Rutin olmayan sorularda 'en az kaç' için en kötü senaryoyu (güvercin yuvası ilkesi) düşün."
                )
            )
        ),
        TopicLectureVideo(
            id = "rehbermat_tyt_problemler",
            videoId = "fi2JGVy9alM",
            searchQueryFallback = "Rehber Matematik Problematiks Zor Problem Kampı",
            title = "Problematiks Kampı | Yeni Nesil TYT Problemler Çözüm Teknikleri",
            teacherName = "Rehber Matematik",
            channelName = "Rehber Matematik",
            teacherBadge = "Enerjik Anlatım",
            popularityRank = 2,
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            subjectName = "Matematik",
            topicIds = setOf(
                "sayi_problemleri", "kesir_problemleri", "yas_problemleri",
                "hareket_problemleri", "yuzde_kar_zarar_problemleri", "grafik_problemleri", "rutin_olmayan_problemler"
            ),
            topicKeywords = listOf("problem", "sayı", "kesir", "hız", "yüzde", "kâr", "grafik", "rutin olmayan"),
            kazanimCodes = setOf("MAT.9.3.5.1", "MAT.9.3.5.2", "MAT.9.3.5.3", "MAT.9.3.5.4", "MAT.9.3.5.5", "MAT.9.3.5.6"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "rehber_prob_sayi_yas",
                    chapterTitle = "Sayı-Kesir ve Yaş Problemleri Hızlı Kurgu",
                    startSeconds = 51,
                    endSeconds = 1481,
                    kazanimIds = setOf("KAZ_PROB_01", "KAZ_PROB_02"),
                    kazanimCodes = setOf("MAT.9.3.5.1", "MAT.9.3.5.2"),
                    questionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("sayı", "kesir", "yaş", "kuyruk", "bilet"),
                    keyFormulaSummary = "Bilinmeyen sayısını en azda tut; mümkünse tek değişkenle tüm cümleyi matematik diline çevir.",
                    osymTip = "Uzun paragraf şeklindeki TYT problemlerini cümle cümle bölüp her noktada denkleme dönüştür."
                ),
                LectureChapterSegment(
                    id = "rehber_prob_hiz_yuzde",
                    chapterTitle = "Hız-Hareket, Yüzde-Kâr-Zarar ve Grafik Yorumu",
                    startSeconds = 1481,
                    endSeconds = 2500,
                    kazanimIds = setOf("KAZ_PROB_03", "KAZ_PROB_04", "KAZ_PROB_05", "KAZ_PROB_06"),
                    kazanimCodes = setOf("MAT.9.3.5.3", "MAT.9.3.5.4", "MAT.9.3.5.5", "MAT.9.3.5.6"),
                    questionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf("hız", "hareket", "yüzde", "kâr", "zarar", "daire grafiği", "rutin olmayan"),
                    keyFormulaSummary = "Yol = Hız × Zaman; Yüzde değişimlerinde 100 bazlı tablo kur.",
                    osymTip = "Daire grafiğinde açıları 360° yerine en sade katlara (örn. 30° = k) indirgemek işlem hızını artırır."
                )
            )
        ),
        TopicLectureVideo(
            id = "eyupb_tyt_temel_cebir_olasilik",
            videoId = "NnAlgbgK_O4",
            searchQueryFallback = "Eyüp B TYT Matematik Temel Kavramlar EBOB EKOK Mutlak Değer Olasılık",
            title = "TYT Matematik: Temel Kavramlar, Bölünebilme, Mutlak Değer, Kümeler ve PKBO",
            teacherName = "Eyüp B.",
            channelName = "Eyüp B. Matematik Geometri",
            teacherBadge = "Yorum & Mantık Ustası",
            popularityRank = 1,
            examType = ExamType.TYT,
            subjectId = "tyt_mat",
            subjectName = "Matematik",
            topicIds = setOf(
                "temel_kavramlar", "bolme_bolunebilme", "rasyonel_sayilar", "sayi_basamaklari", "ebob_ekok",
                "ondalik_sayilar", "birinci_dereceden_denklemler", "basit_esitsizlikler", "mutlak_deger",
                "uslu_sayilar", "koklu_sayilar", "carpanlara_ayirma", "oran_oranti", "mantik",
                "kumeler", "kartezyen_carpim", "permutasyon", "kombinasyon", "binom", "olasilik", "veri_istatistik"
            ),
            topicKeywords = listOf(
                "temel kavramlar", "tek", "çift", "asal", "ardışık", "faktöriyel", "ebob", "ekok",
                "bölünebilme", "basamak", "mutlak değer", "eşitsizlik", "üslü", "köklü", "çarpanlara ayırma",
                "mantık", "önerme", "küme", "venn", "permütasyon", "kombinasyon", "binom", "olasılık", "medyan"
            ),
            kazanimCodes = setOf("MAT.9.2.1.1", "MAT.9.2.2.1", "MAT.9.3.2.1", "MAT.9.3.3.1", "MAT.9.1.1.1", "MAT.10.1.1.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "eyupb_tyt_temel_ebob",
                    chapterTitle = "Temel Kavramlar, Teklik-Çiftlik, Bölünebilme ve EBOB-EKOK",
                    startSeconds = 210,
                    endSeconds = 1040,
                    kazanimIds = setOf("KAZ_TYT_MAT_TEMEL", "KAZ_TYT_MAT_BOLME_EBOB"),
                    kazanimCodes = setOf("MAT.9.2.1.1", "MAT.9.2.2.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("tam sayı", "tek", "çift", "asal", "ardışık", "ebob", "ekok", "bölünebilme", "basamak", "kalan"),
                    keyFormulaSummary = "EBOB(a,b)·EKOK(a,b) = a·b. Aralarında asal sayıların EBOB'u 1'dir. 2 tek çift asal sayıdır.",
                    osymTip = "'Her zaman doğrudur' öncüllerinde a, b tam sayı mı yoksa reel sayı mı dikkat et; kesirli veya negatif değer dene!"
                ),
                LectureChapterSegment(
                    id = "eyupb_tyt_mutlak_uslu_koklu",
                    chapterTitle = "Basit Eşitsizlik, Mutlak Değer, Üslü ve Köklü İfadeler",
                    startSeconds = 1070,
                    endSeconds = 1940,
                    kazanimIds = setOf("KAZ_TYT_MAT_ESITSIZLIK_MUTLAK", "KAZ_TYT_MAT_USLU_KOKLU"),
                    kazanimCodes = setOf("MAT.9.3.2.1", "MAT.9.3.3.1"),
                    questionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("mutlak değer", "uzaklık", "sayı doğrusu", "eşitsizlik", "üslü", "köklü", "karekök", "çarpanlara ayırma"),
                    keyFormulaSummary = "|x - a| ≤ r ⇔ a - r ≤ x ≤ a + r (a'ya uzaklığı en çok r olan sayılar). √(x²) = |x|.",
                    osymTip = "Çift dereceli kök dışına ifade her zaman mutlak değerle çıkar; işaret incelemeden çıkarma!"
                ),
                LectureChapterSegment(
                    id = "eyupb_tyt_mantik_kume_pkbo",
                    chapterTitle = "Mantık, Kümeler ve Permütasyon-Kombinasyon-Olasılık",
                    startSeconds = 1980,
                    endSeconds = 3020,
                    kazanimIds = setOf("KAZ_TYT_MAT_MANTIK_KUME", "KAZ_TYT_MAT_PKBO_VERI"),
                    kazanimCodes = setOf("MAT.9.1.1.1", "MAT.10.1.1.1"),
                    questionTypes = setOf(OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("önerme", "küme", "venn", "kesişim", "birleşim", "permütasyon", "kombinasyon", "binom", "olasılık", "medyan", "standart sapma"),
                    keyFormulaSummary = "p ⇒ q ≡ p' ∨ q. P(A) = İstenen Durum / Tüm Durumlar. C(n,r) = n! / [r!(n-r)!].",
                    osymTip = "Olasılıkta 'en az biri' geçiyorsa 1 - P(hiçbiri) tümleyen yöntemi en temiz yoldur."
                )
            )
        ),

        // =====================================================================
        // 8. TYT & AYT GEOMETRİ (Kenan Kara öncelikli, Eyüp B. Geometri)
        // =====================================================================
        TopicLectureVideo(
            id = "kenankara_geo_ucgenler",
            videoId = "2EgYo8peOaU",
            searchQueryFallback = "Kenan Kara ile Geometri Üçgende Açılar Dik Üçgen Kampı",
            title = "TYT-AYT Geometri Kampı | Üçgende Açılar, Dik Üçgen ve Katlama",
            teacherName = "Kenan Kara",
            channelName = "Kenan Kara ile Geometri",
            teacherBadge = "Geometri 1 Numara",
            popularityRank = 1,
            examType = ExamType.TYT,
            subjectId = "tyt_geo",
            subjectName = "Geometri",
            topicIds = setOf("dogruda_acilar", "ucgende_acilar", "dik_ucgen", "ikizkenar_eskenar_ucgen", "ucgende_aciortay", "ucgende_kenarortay", "aci_kenar_bagintilari"),
            topicKeywords = listOf("üçgen", "üçgende açı", "katlandığında", "dik üçgen", "pisagor", "öklid", "hipotenüs", "ikizkenar", "eşkenar", "açıortay", "kenarortay"),
            kazanimCodes = setOf("GEO.9.4.1.1", "GEO.9.4.2.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "kenan_ucgen_acilar_katlama",
                    chapterTitle = "Doğruda ve Üçgende Açılar, Katlama & Döndürme Taktikleri",
                    startSeconds = 245,
                    endSeconds = 1080,
                    kazanimIds = setOf("KAZ_GEO_01"),
                    kazanimCodes = setOf("GEO.9.4.1.1"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("üçgende açı", "iç açı", "dış açı", "ikizkenar", "katlandığında", "döndürme", "muhteşem üçlü"),
                    keyFormulaSummary = "İki iç açının toplamı komşu olmayan bir dış açıya eşittir. Katlamada kat çizgisi açıortaydır ve üst üste binen kenarlar eşittir.",
                    osymTip = "Katlama sorularında şekli mutlaka eski haline aç; eşit kenar ve açıortayları aynı renk işaretle!"
                ),
                LectureChapterSegment(
                    id = "kenan_dik_ucgen_oklid",
                    chapterTitle = "Dik Üçgen, Pisagor, Öklid ve Özel Üçgenler (30-60-90)",
                    startSeconds = 1110,
                    endSeconds = 2040,
                    kazanimIds = setOf("KAZ_GEO_02"),
                    kazanimCodes = setOf("GEO.9.4.2.1"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("dik üçgen", "pisagor", "öklid", "hipotenüs", "30-60-90", "45-45-90", "özel üçgen"),
                    keyFormulaSummary = "a² + b² = c². Öklid: h² = p·k, b² = p·(p+k), a·b = c·h. Özel üçgenler: 3-4-5, 5-12-13, 8-15-17, 7-24-25.",
                    osymTip = "30°, 45°, 60° açılarının karşısına dik indirerek özel üçgen oluştur; geniş açılarda dıştan dik indir!"
                )
            )
        ),
        TopicLectureVideo(
            id = "kenankara_geo_benzerlik_cokgen",
            videoId = "miqCN7ivZW8",
            searchQueryFallback = "Kenan Kara ile Geometri Üçgende Eşlik Benzerlik Çokgenler Dörtgenler",
            title = "50 Günde TYT-AYT Geometri | Üçgende Eşlik-Benzerlik, Alan ve Dörtgenler",
            teacherName = "Kenan Kara",
            channelName = "Kenan Kara ile Geometri",
            teacherBadge = "Geometri 1 Numara",
            popularityRank = 1,
            examType = ExamType.TYT,
            subjectId = "tyt_geo",
            subjectName = "Geometri",
            topicIds = setOf("ucgende_eslik_benzerlik", "ucgende_alan", "cokgenler", "dortgenler", "yamuk", "paralelkenar", "eskenar_dortgen_deltoid", "dikdortgen_kare", "prizmalar_tyt", "piramit_koni_kure_tyt"),
            topicKeywords = listOf("benzerlik", "eşlik", "thales", "kelebek", "alan oranı", "çokgen", "altıgen", "beşgen", "yamuk", "paralelkenar", "kare", "dikdörtgen", "prizma", "hacim"),
            kazanimCodes = setOf("GEO.9.4.3.1", "GEO.10.5.1.1", "GEO.11.6.1.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "kenan_benzerlik_thales",
                    chapterTitle = "Üçgende Eşlik, Thales, Kelebek Benzerliği ve Alan Oranları",
                    startSeconds = 110,
                    endSeconds = 832,
                    kazanimIds = setOf("KAZ_GEO_03"),
                    kazanimCodes = setOf("GEO.9.4.3.1"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("benzerlik", "eşlik", "thales", "kelebek", "alan oranı", "benzerlik oranı", "k²"),
                    keyFormulaSummary = "Benzerlik oranı k ise çevreler oranı k, alanlar oranı k² olur. Paralel kesende temel orantı (Thales) geçerlidir.",
                    osymTip = "Dik üçgen içinde dik varsa α-β-90 açı taşıması yap; iki dik üçgen mutlaka A.A. benzerliği verir!"
                ),
                LectureChapterSegment(
                    id = "kenan_cokgen_dortgen",
                    chapterTitle = "Düzgün Çokgenler ve Özel Dörtgenler (Yamuk, Paralelkenar, Kare)",
                    startSeconds = 832,
                    endSeconds = 2124,
                    kazanimIds = setOf("KAZ_GEO_04"),
                    kazanimCodes = setOf("GEO.10.5.1.1"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("çokgen", "altıgen", "beşgen", "yamuk", "paralelkenar", "dikdörtgen", "kare", "deltoid", "köşegen"),
                    keyFormulaSummary = "n kenarlı düzgün çokgende dış açı = 360°/n. Yamuk alanı = [(a+c)/2]·h. Karede köşegenler dik kesişir ve açıortaydır.",
                    osymTip = "Düzgün altıgende en uzun köşegen kenarın 2 katıdır ve şekli 6 eşkenar üçgene böler."
                ),
                LectureChapterSegment(
                    id = "kenan_kati_cisimler",
                    chapterTitle = "Katı Cisimler: Prizma, Silindir, Koni ve Küre Hacim-Açınım",
                    startSeconds = 2124,
                    endSeconds = 3300,
                    kazanimIds = setOf("KAZ_GEO_07"),
                    kazanimCodes = setOf("GEO.11.6.1.1"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("prizma", "küp", "silindir", "koni", "küre", "hacim", "yüzey alanı", "karınca", "en kısa yol"),
                    keyFormulaSummary = "Prizma/Silindir V = Taban Alanı × h. Piramit/Koni V = (Taban Alanı × h)/3. Küre V = (4/3)πr³.",
                    osymTip = "Yüzeyden en kısa yol (karınca) sorularında cismi düzleme aç ve Pisagor uygula!"
                )
            )
        ),
        TopicLectureVideo(
            id = "kenankara_ayt_cember_analitik",
            videoId = "j8KawUxW2qU",
            searchQueryFallback = "Kenan Kara ile Geometri Çember Daire ve Analitik Geometri Kampı",
            title = "AYT Geometri | Çemberde Açı-Uzunluk, Dairede Alan ve Analitik Geometri",
            teacherName = "Kenan Kara",
            channelName = "Kenan Kara ile Geometri",
            teacherBadge = "AYT Geometri Lideri",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_geo",
            subjectName = "Geometri",
            topicIds = setOf("cember_daire_tyt", "cemberde_aci", "cemberde_uzunluk", "dairede_alan", "noktanin_analitigi", "dogrunun_analitigi", "donusum_geometrisi", "cemberin_analitigi", "kati_cisimler_ayt"),
            topicKeywords = listOf("çember", "daire", "yarıçap", "kiriş", "teğet", "daire dilimi", "analitik", "eğim", "doğru denklemi", "simetri", "çemberin analitiği"),
            kazanimCodes = setOf("GEO.11.5.1.1", "GEO.11.2.1.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "kenan_cember_daire",
                    chapterTitle = "Çemberde Açı, Teğet-Kiriş Uzunluğu ve Dairede Alan",
                    startSeconds = 54,
                    endSeconds = 1528,
                    kazanimIds = setOf("KAZ_GEO_05"),
                    kazanimCodes = setOf("GEO.11.5.1.1"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("çember", "daire", "yarıçap", "kiriş", "teğet", "merkez açı", "çevre açı", "daire dilimi"),
                    keyFormulaSummary = "Çevre açı gördüğü yayın yarısıdır; çapı gören çevre açı 90°'dir. Daire dilimi alanı = πr²·(α/360°).",
                    osymTip = "Merkezden teğet değme noktasına çizilen yarıçap her zaman diktir; kirişe inen dik kirişi iki eş parçaya böler."
                ),
                LectureChapterSegment(
                    id = "kenan_analitik_geometri",
                    chapterTitle = "Noktanın, Doğrunun ve Çemberin Analitik İncelenmesi",
                    startSeconds = 1528,
                    endSeconds = 3800,
                    kazanimIds = setOf("KAZ_GEO_06"),
                    kazanimCodes = setOf("GEO.11.2.1.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.GORSEL_GEOMETRIK),
                    subTopicKeywords = listOf("analitik düzlem", "doğrunun eğimi", "dik kesişen", "noktanın doğruya uzaklığı", "simetri", "çemberin analitiği"),
                    keyFormulaSummary = "Dik doğrular: m₁·m₂ = -1. Nokta-doğru uzaklığı: |Ax₀+By₀+C|/√(A²+B²). Çember: (x-a)² + (y-b)² = r².",
                    osymTip = "Analitik sorusunda şekil varsa mutlaka eksenlere dik inip benzerlik veya eş üçgen ara!"
                )
            )
        ),
        TopicLectureVideo(
            id = "eyupb_geometri_kamp",
            videoId = "THFBcbVXmW8",
            searchQueryFallback = "Eyüp B Geometri Üçgenler Benzerlik Çember Analitik konu anlatımı",
            title = "Eyüp B. Geometri Kampı | ÖSYM Tarzı Yeni Nesil Geometri Yorumları",
            teacherName = "Eyüp B.",
            channelName = "Eyüp B. Matematik Geometri",
            teacherBadge = "Derece Geometri",
            popularityRank = 2,
            examType = ExamType.TYT,
            subjectId = "tyt_geo",
            subjectName = "Geometri",
            topicIds = setOf("ucgende_acilar", "dik_ucgen", "ucgende_eslik_benzerlik", "cokgenler", "cember_daire_tyt", "dairede_alan", "dogrunun_analitigi", "prizmalar_tyt"),
            topicKeywords = listOf("geometri", "üçgen", "benzerlik", "çember", "daire", "analitik", "prizma"),
            kazanimCodes = setOf("GEO.9.4.1.1", "GEO.9.4.2.1", "GEO.9.4.3.1", "GEO.10.5.1.1", "GEO.11.5.1.1", "GEO.11.2.1.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "eyupb_geo_ucgen_benzerlik",
                    chapterTitle = "Üçgende Açı, Dik Üçgen ve Benzerlik Yorumları",
                    startSeconds = 290,
                    endSeconds = 1380,
                    kazanimIds = setOf("KAZ_GEO_01", "KAZ_GEO_02", "KAZ_GEO_03"),
                    kazanimCodes = setOf("GEO.9.4.1.1", "GEO.9.4.2.1", "GEO.9.4.3.1"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("üçgen", "dik üçgen", "benzerlik", "katlama"),
                    keyFormulaSummary = "Açıortay, kenarortay ve yükseklikten ikisi çakışıyorsa üçgen mutlaka ikizkenardır (DAKİ).",
                    osymTip = "Orta nokta gördüğün her yerde orta taban (paralel çekme) refleksini kullan."
                ),
                LectureChapterSegment(
                    id = "eyupb_geo_cember_analitik",
                    chapterTitle = "Çember-Daire ve Analitik Geometri Bakış Açısı",
                    startSeconds = 1420,
                    endSeconds = 2580,
                    kazanimIds = setOf("KAZ_GEO_05", "KAZ_GEO_06", "KAZ_GEO_04"),
                    kazanimCodes = setOf("GEO.11.5.1.1", "GEO.11.2.1.1", "GEO.10.5.1.1"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("çember", "daire", "analitik", "eğim", "çokgen", "dörtgen"),
                    keyFormulaSummary = "Çemberde eşit uzunlukta kirişler eşit yaylar ayırır; analitikte eğim = tan(α).",
                    osymTip = "Çemberin analitiğinde x²+y²+Dx+Ey+F=0 formunu tam kareye tamamlayarak merkez ve yarıçapı hızlıca bul."
                )
            )
        ),

        // =====================================================================
        // 9. TYT TÜRKÇE — PARAGRAF & DİL BİLGİSİ (Rüştü Hoca, Kadir Gümüş, Deniz Hoca)
        // =====================================================================
        TopicLectureVideo(
            id = "rustuhoca_tyt_turkce_paragraf_dil",
            videoId = "3QJl686CM_s",
            searchQueryFallback = "Rüştü Hoca ile Türkçe Paragraf ve Dil Bilgisi Full Tekrar",
            title = "TYT Türkçe Full Tekrar | Paragraf Taktikleri ve Dil Bilgisi Kampı",
            teacherName = "Rüştü Hoca",
            channelName = "Rüştü Hoca ile Türkçe",
            teacherBadge = "Paragraf Taktik Lideri",
            popularityRank = 1,
            examType = ExamType.TYT,
            subjectId = "tyt_turkce",
            subjectName = "Türkçe",
            topicIds = setOf(
                "paragrafta_anlam", "paragrafta_yapi", "anlatim_bicimleri",
                "sozcukte_anlam", "cumlede_anlam", "soz_obeginde_anlam",
                "ses_bilgisi", "yazim_kurallari", "noktalama_isaretleri",
                "sozcukte_yapi", "fiilimsiler", "cumlenin_ogeleri"
            ),
            topicKeywords = listOf(
                "paragraf", "ana düşünce", "asıl anlatılmak istenen", "değinilmemiştir", "çıkarılamaz",
                "akışı bozan", "iki paragrafa", "boş bırakılan", "cümlede anlam", "sözcükte anlam",
                "yazım", "noktalama", "ses olayı", "fiilimsi", "cümlenin ögeleri"
            ),
            kazanimCodes = setOf("TUR.TYT.PAR.01", "TUR.TYT.PAR.02", "TUR.TYT.PAR.03", "TUR.TYT.PAR.04", "TUR.TYT.ANL.01", "TUR.TYT.DIL.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "rustu_paragraf_ana_dusunce",
                    chapterTitle = "Paragrafta Ana Düşünce, Konu ve Vurgulanan İleti",
                    startSeconds = 310,
                    endSeconds = 1010,
                    kazanimIds = setOf("KAZ_PARAGRAF_01"),
                    kazanimCodes = setOf("TUR.TYT.PAR.01"),
                    questionTypes = setOf(OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("asıl anlatılmak istenen", "ana düşünce", "temel amaç", "vurgulanan", "konusu", "başlık"),
                    keyFormulaSummary = "Ana düşünce genellikle parçanın son 2 cümlesinde veya 'ama, fakat, oysa, kısacası, bu yüzden' bağlaçlarından hemen sonra yoğunlaşır.",
                    osymTip = "Kendi genel kültürünü değil, yalnızca yazarın metinde vurguladığı yargıyı seç; aşırı genelleme yapan şıklardan kaçın."
                ),
                LectureChapterSegment(
                    id = "rustu_paragraf_yardimci_deginilmemistir",
                    chapterTitle = "Yardımcı Düşünce (Değinilmemiştir / Ulaşılamaz) Taktikleri",
                    startSeconds = 1040,
                    endSeconds = 1730,
                    kazanimIds = setOf("KAZ_PARAGRAF_02"),
                    kazanimCodes = setOf("TUR.TYT.PAR.02"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf("değinilmemiştir", "çıkarılamaz", "ulaşılamaz", "söylenemez", "hangilerine ulaşılabilir", "yardımcı düşünce"),
                    keyFormulaSummary = "Olumsuz soru köklerinde önce şıklardaki anahtar kelimeleri (1-2 kelime) işaretle, sonra metni tek seferde tarayarak ele.",
                    osymTip = "Metni okuyup sonra her şık için tekrar başa dönmek dakikalarını çalar; şık anahtarlarını zihninde tutup metni 1 kez oku!"
                ),
                LectureChapterSegment(
                    id = "rustu_paragraf_akisi_bozan_yapi",
                    chapterTitle = "Paragrafta Akışı Bozan Cümle, İkiye Bölme ve Boşluk Tamamlama",
                    startSeconds = 1760,
                    endSeconds = 2480,
                    kazanimIds = setOf("KAZ_PARAGRAF_03", "KAZ_PARAGRAF_04"),
                    kazanimCodes = setOf("TUR.TYT.PAR.03", "TUR.TYT.PAR.04"),
                    questionTypes = setOf(OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf("akışı bozan", "iki paragrafa", "ikinci paragraf", "numaralanmış cümle", "boş bırakılan yere", "parçanın sonuna"),
                    keyFormulaSummary = "Akışı bozan cümleyi çıkarıp öncesi ve sonrasını art arda oku; anlam bütünlüğü cuk oturuyorsa doğru cümleyi buldun.",
                    osymTip = "İkinci paragrafın ilk cümlesi 'bu yüzden, ancak, bununla birlikte' gibi bağlantı ögeleriyle başlayamaz."
                ),
                LectureChapterSegment(
                    id = "rustu_dilbilgisi_yazim_noktalama",
                    chapterTitle = "Sözcük-Cümle Anlamı, Yazım Kuralları, Noktalama ve Ses Bilgisi",
                    startSeconds = 2520,
                    endSeconds = 3540,
                    kazanimIds = setOf("KAZ_TURKCE_SOZCUK_CUMLE", "KAZ_TURKCE_DILBILGISI"),
                    kazanimCodes = setOf("TUR.TYT.ANL.01", "TUR.TYT.DIL.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("yazım", "noktalama", "ses olayı", "fiilimsi", "cümlenin ögeleri", "özne", "yüklem", "neden-sonuç", "altı çizili"),
                    keyFormulaSummary = "Ayrı/bitişik yazımlarda ikinci kelime anlamını koruyorsa ayrı, ikisi de anlam kaybına uğradıysa bitişik yazılır (istisnalar hariç).",
                    osymTip = "Noktalı virgül (;) yalnızca içinde zaten virgül bulunan sıralı cümleleri veya tür-takım gruplarını ayırmak için kullanılır."
                )
            )
        ),
        TopicLectureVideo(
            id = "kadirgumus_tyt_turkce_paragraf",
            videoId = "uWt_GmMvft4",
            searchQueryFallback = "Kadir Gümüş Parla Paragraf Kampı TYT Türkçe",
            title = "41 Günde Parla Paragraf & TYT Türkçe Konu Anlatımı",
            teacherName = "Kadir Gümüş",
            channelName = "Benim Hocam / Kadir Gümüş",
            teacherBadge = "Anlam & Edebiyat Ustası",
            popularityRank = 2,
            examType = ExamType.TYT,
            subjectId = "tyt_turkce",
            subjectName = "Türkçe",
            topicIds = setOf("paragrafta_anlam", "paragrafta_yapi", "cumlede_anlam", "yazim_kurallari"),
            topicKeywords = listOf("paragraf", "ana düşünce", "değinilmemiştir", "akışı bozan", "yazım", "noktalama"),
            kazanimCodes = setOf("TUR.TYT.PAR.01", "TUR.TYT.PAR.02", "TUR.TYT.PAR.03", "TUR.TYT.PAR.04", "TUR.TYT.DIL.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "kadir_paragraf_anlam",
                    chapterTitle = "Paragrafın Altın Kuralları: Ana ve Yardımcı Düşünce",
                    startSeconds = 280,
                    endSeconds = 1320,
                    kazanimIds = setOf("KAZ_PARAGRAF_01", "KAZ_PARAGRAF_02"),
                    kazanimCodes = setOf("TUR.TYT.PAR.01", "TUR.TYT.PAR.02"),
                    questionTypes = setOf(OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("ana düşünce", "değinilmemiştir", "çıkarılamaz", "asıl anlatılmak istenen"),
                    keyFormulaSummary = "Her paragraf bir soru-cevap etrafında örülür; yazarın 'neye itiraz ettiğini' bulmak ana düşünceyi verir.",
                    osymTip = "Seçeneklerdeki 'sadece, tamamen, her zaman' gibi kesinlik bildiren zarflar genellikle çeldiricidir."
                ),
                LectureChapterSegment(
                    id = "kadir_paragraf_yapi_dil",
                    chapterTitle = "Paragraf Yapısı (Akışı Bozan) ve Dil Bilgisi İpuçları",
                    startSeconds = 1360,
                    endSeconds = 2440,
                    kazanimIds = setOf("KAZ_PARAGRAF_03", "KAZ_PARAGRAF_04", "KAZ_TURKCE_DILBILGISI"),
                    kazanimCodes = setOf("TUR.TYT.PAR.03", "TUR.TYT.PAR.04", "TUR.TYT.DIL.01"),
                    questionTypes = setOf(OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("akışı bozan", "boş bırakılan", "yazım", "noktalama", "ses"),
                    keyFormulaSummary = "Anahtar sözcük zincirini takip et; konunun açısının değiştiği cümle ikinci paragrafın başıdır.",
                    osymTip = "Cümlenin ögelerini bulurken söz öbeklerini (tamlama, fiilimsi grubu, ikileme) asla bölme!"
                )
            )
        ),
        TopicLectureVideo(
            id = "denizhoca_tyt_turkce",
            videoId = "j6vb9ay8kSk",
            searchQueryFallback = "Deniz Hoca TYT Türkçe Paragraf ve Dil Bilgisi Full Tekrar",
            title = "TYT Türkçe Paragraf & Dil Bilgisi Nokta Atışı Tekrar",
            teacherName = "Deniz Hoca",
            channelName = "Deniz Hoca",
            teacherBadge = "Hızlı & Net Tekrar",
            popularityRank = 3,
            examType = ExamType.TYT,
            subjectId = "tyt_turkce",
            subjectName = "Türkçe",
            topicIds = setOf("paragrafta_anlam", "paragrafta_yapi", "yazim_kurallari", "noktalama_isaretleri"),
            topicKeywords = listOf("paragraf", "yazım", "noktalama", "dil bilgisi"),
            kazanimCodes = setOf("TUR.TYT.PAR.01", "TUR.TYT.PAR.02", "TUR.TYT.DIL.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "deniz_turkce_paragraf",
                    chapterTitle = "Paragraf Soru Tipleri ve Hızlı Çözüm Stratejileri",
                    startSeconds = 90,
                    endSeconds = 2498,
                    kazanimIds = setOf("KAZ_PARAGRAF_01", "KAZ_PARAGRAF_02", "KAZ_PARAGRAF_03"),
                    kazanimCodes = setOf("TUR.TYT.PAR.01", "TUR.TYT.PAR.02", "TUR.TYT.PAR.03"),
                    questionTypes = setOf(OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("paragraf", "ana düşünce", "değinilmemiştir", "akışı bozan"),
                    keyFormulaSummary = "Soru kökünü önce oku, olumlu/olumsuz yönünü belirle ve odaklanarak metne geç.",
                    osymTip = "Çift sorulu paragraflarda ilk soruyu çözerken metnin yapısını çıkar, ikinci soruda zaman kazan."
                )
            )
        ),

        // =====================================================================
        // 10. TYT & AYT FİZİK (Özcan Aykın öncelikli, Altuğ Güneş, VIP Fizik)
        // =====================================================================
        TopicLectureVideo(
            id = "ozcanaykin_tyt_fizik",
            videoId = "Qp211SqOGJ4",
            searchQueryFallback = "Özcan Aykın 55 Günde TYT Fizik Kampı konu anlatımı",
            title = "55 Günde TYT Fizik Kampı | Hareket, Dinamik, Elektrik ve Optik",
            teacherName = "Özcan Aykın",
            channelName = "Özcan Aykın FİZİK",
            teacherBadge = "Simülasyonlu Fizik Lideri",
            popularityRank = 1,
            examType = ExamType.TYT,
            subjectId = "tyt_fizik",
            subjectName = "Fizik",
            topicIds = setOf(
                "fizik_bilimine_giris", "madde_ve_ozellikleri", "hareket_tyt", "kuvvet_newton_tyt",
                "is_guc_enerji_tyt", "isi_sicaklik", "genlesme", "basinc", "kaldirma_kuvveti",
                "elektrostatik", "elektrik_akimi_devreler", "manyetizma_tyt", "dalgalar_tyt",
                "optik_aydinlanma_aynalar", "optik_kirilma_mercekler"
            ),
            topicKeywords = listOf(
                "fizik", "hareket", "hız-zaman", "ivme", "kuvvet", "newton", "dinamik", "enerji",
                "basınç", "kaldırma kuvveti", "ısı", "sıcaklık", "elektrik", "direnç", "lamba", "optik", "ayna", "mercek", "dalga"
            ),
            kazanimCodes = setOf("FIZ.TYT.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "ozcan_tyt_hareket_grafik",
                    chapterTitle = "Doğrusal Hareket ve Konum-Hız-İvme Grafik Yorumu",
                    startSeconds = 140,
                    endSeconds = 640,
                    kazanimIds = setOf("KAZ_TYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("hareket", "hız-zaman", "konum-zaman", "ivme", "yer değiştirme", "grafik", "sürat"),
                    keyFormulaSummary = "Hız-zaman (v-t) grafiğinin altındaki alan yer değiştirmeyi (Δx), eğimi ise ivmeyi (a) verir.",
                    osymTip = "Alınan yol skalerdir (hep pozitif toplanır), yer değiştirme vektöreldir (yön değişiminde çıkarılır)."
                ),
                LectureChapterSegment(
                    id = "ozcan_tyt_dinamik_basinc",
                    chapterTitle = "Kuvvet, Newton Yasaları (Dinamik), İş-Enerji ve Basınç-Kaldırma",
                    startSeconds = 660,
                    endSeconds = 1180,
                    kazanimIds = setOf("KAZ_TYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("kuvvet", "newton", "sürtünme", "dinamik", "enerji", "basınç", "kaldırma kuvveti", "ısı", "sıcaklık"),
                    keyFormulaSummary = "F_net = m·a. Sıvı basıncı P = h·d·g. Kaldırma kuvveti F_k = V_batan·d_sıvı·g. Yüzen ve askıdaki cisimlerde F_k = G.",
                    osymTip = "Statik sürtünme kuvveti uygulanan kuvvete eşittir; hemen k·N maksimum değerini yazma!"
                ),
                LectureChapterSegment(
                    id = "ozcan_tyt_elektrik_devre",
                    chapterTitle = "Elektrik Akımı, Ohm Yasası ve Lamba Parlaklığı Devreleri",
                    startSeconds = 1200,
                    endSeconds = 1690,
                    kazanimIds = setOf("KAZ_TYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("elektrik", "akım", "direnç", "lamba", "parlaklık", "devre", "voltmetre", "ampermetre", "elektrostatik"),
                    keyFormulaSummary = "V = I·R. Lamba parlaklığı gücüyle orantılıdır: P = I²·R = V²/R. Paralel kollarda potansiyel fark (V) eşittir.",
                    osymTip = "Karışık devrelerde harflendirme metodunu kullan; ampermetreyi düz tel, voltmetreyi açık anahtar gibi düşün!"
                ),
                LectureChapterSegment(
                    id = "ozcan_tyt_optik_dalga",
                    chapterTitle = "Optik (Aynalar, Kırılma, Mercekler) ve Dalgalar",
                    startSeconds = 1710,
                    endSeconds = 2190,
                    kazanimIds = setOf("KAZ_TYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("optik", "ayna", "kırılma", "mercek", "gölge", "renk", "dalga", "frekans", "dalga boyu"),
                    keyFormulaSummary = "v = λ·f (hız sadece ortama, frekans sadece kaynağa bağlıdır!). Snell Yasası: n₁·sinθ₁ = n₂·sinθ₂.",
                    osymTip = "Tümsek ayna ve kalın kenarlı mercekte görüntü her zaman sanal, düz ve cisimden küçüktür."
                )
            )
        ),
        TopicLectureVideo(
            id = "ozcanaykin_ayt_fizik",
            videoId = "LtPiqUuUy90",
            searchQueryFallback = "Özcan Aykın 88 Günde AYT Fizik Kampı Atışlar Momentum Elektrik",
            title = "88 Günde AYT Fizik Kampı | Mekanik, Elektrik-Manyetizma ve Modern Fizik",
            teacherName = "Özcan Aykın",
            channelName = "Özcan Aykın FİZİK",
            teacherBadge = "AYT Fizik 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_fizik",
            subjectName = "Fizik",
            topicIds = setOf(
                "vektorler", "bagil_hareket", "newton_hareket_yasalari", "bir_boyutta_sabit_ivmeli",
                "atislar", "enerji_ve_hareket", "itme_momentum", "tork_ve_denge", "kutle_merkezi_basit_makine",
                "elektrik_alan_potansiyel", "manyetizma_induksiyon", "alternatif_akim_transformator",
                "cembersel_hareket", "basit_harmonik_hareket", "dalga_mekanigi", "atom_fizigi_radyoaktivite", "modern_fizik"
            ),
            topicKeywords = listOf(
                "vektör", "bağıl", "atış", "momentum", "itme", "tork", "denge", "çembersel",
                "harmonik", "elektrik alan", "manyetik", "indüksiyon", "transformatör", "modern fizik", "fotoelektrik"
            ),
            kazanimCodes = setOf("FIZ.AYT.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "ozcan_ayt_atislar_mekanik",
                    chapterTitle = "Vektörler, Bağıl Hareket, Newton ve Atışlar",
                    startSeconds = 120,
                    endSeconds = 720,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("vektör", "bağıl", "atış", "serbest düşme", "eğik atış", "newton", "ivme"),
                    keyFormulaSummary = "v_bağıl = v_gözlenen - v_gözlemci. Serbest düşme: h = ½gt² (5-15-25-35). Yatay atışta yatay hız sabittir.",
                    osymTip = "Eğik atışta hava direnci yoksa cismin çıkış süresi iniş süresine eşittir ve tepe noktasında hız 0 değil v_x'tir!"
                ),
                LectureChapterSegment(
                    id = "ozcan_ayt_momentum_tork_cembersel",
                    chapterTitle = "İtme-Momentum, Tork-Denge, Çembersel ve Harmonik Hareket",
                    startSeconds = 740,
                    endSeconds = 1360,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GORSEL_GEOMETRIK),
                    subTopicKeywords = listOf("momentum", "itme", "çarpışma", "tork", "denge", "çembersel", "merkezcil", "harmonik", "sarkaç"),
                    keyFormulaSummary = "I = F·Δt = ΔP. τ = F·d_dik. F_merkezcil = mv²/r = mω²r. Yay sarkacı T = 2π√(m/k), basit sarkaç T = 2π√(L/g).",
                    osymTip = "Esnek olmayan çarpışmalarda momentum korunur ancak kinetik enerji ısıya dönüştüğü için korunmaz!"
                ),
                LectureChapterSegment(
                    id = "ozcan_ayt_elektrik_manyetizma_modern",
                    chapterTitle = "Elektrik-Manyetizma, İndüksiyon ve Modern Fizik",
                    startSeconds = 1380,
                    endSeconds = 1980,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("elektrik alan", "potansiyel", "sığaç", "manyetik", "indüksiyon", "lenz", "fotoelektrik", "modern fizik", "compton"),
                    keyFormulaSummary = "ε = -ΔΦ/Δt (Faraday-Lenz). Fotoelektrik: E_foton = E_bağlanma + E_kmax. F_manyetik = B·I·L·sinθ.",
                    osymTip = "Fotoelektrikte kopan elektronların maksimum kinetik enerjisi ışık şiddetine değil, yalnızca fotonun frekansına bağlıdır!"
                )
            )
        ),
        TopicLectureVideo(
            id = "altuggunes_fizik",
            videoId = "1vW3AaHDZzM",
            searchQueryFallback = "Altuğ Güneş FİZİK TYT AYT Fizik Konu Anlatımı",
            title = "TYT-AYT Fizik Kavramsal & Yorum Odaklı Anlatım",
            teacherName = "Altuğ Güneş",
            channelName = "Altuğ Güneş FİZİK",
            teacherBadge = "Öncüllü Yorum Uzmanı",
            popularityRank = 2,
            examType = ExamType.TYT,
            subjectId = "tyt_fizik",
            subjectName = "Fizik",
            topicIds = setOf("hareket_tyt", "kuvvet_newton_tyt", "elektrik_akimi_devreler", "optik_aydinlanma_aynalar", "atislar", "itme_momentum", "manyetizma_induksiyon"),
            topicKeywords = listOf("fizik", "hareket", "newton", "elektrik", "optik", "atış", "momentum", "manyetik"),
            kazanimCodes = setOf("FIZ.TYT.01", "FIZ.AYT.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "altug_fizik_mekanik",
                    chapterTitle = "Hareket, Dinamik ve Mekanik Öncüllü Yorumlar",
                    startSeconds = 2037,
                    endSeconds = 5840,
                    kazanimIds = setOf("KAZ_TYT_FEN_FIZIK", "KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.TYT.01", "FIZ.AYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("hareket", "newton", "kuvvet", "atış", "momentum"),
                    keyFormulaSummary = "Net kuvvet sıfırsa cisim ya durur ya da sabit hızla hareketine devam eder (Eylemsizlik).",
                    osymTip = "Etki-tepki kuvvetleri her zaman farklı cisimler üzerine etki eder; bu yüzden birbirlerini sıfırlamazlar!"
                ),
                LectureChapterSegment(
                    id = "altug_fizik_elektrik_optik",
                    chapterTitle = "Elektrik, Manyetizma ve Optik Temel İlkeler",
                    startSeconds = 6958,
                    endSeconds = 11647,
                    kazanimIds = setOf("KAZ_TYT_FEN_FIZIK", "KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.TYT.01", "FIZ.AYT.01"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("elektrik", "optik", "ayna", "mercek", "manyetik"),
                    keyFormulaSummary = "Işık az yoğun ortamdan çok yoğun ortama geçerken normale yaklaşarak kırılır.",
                    osymTip = "Düzlem aynada görüş alanı çizerken gözün simetrisinden aynanın uçlarına ışın gönder."
                )
            )
        ),
        TopicLectureVideo(
            id = "vipfizik_tyt_ayt",
            videoId = "waw0yYsyCcU",
            searchQueryFallback = "VIP Fizik TYT AYT Fizik Full Tekrar",
            title = "TYT-AYT Fizik Full Tekrar | ÖSYM Soru Çözüm Taktikleri",
            teacherName = "VIP Fizik",
            channelName = "VIP Fizik",
            teacherBadge = "Hızlı Tekrar Favorisi",
            popularityRank = 3,
            examType = ExamType.AYT,
            subjectId = "ayt_fizik",
            subjectName = "Fizik",
            topicIds = setOf("hareket_tyt", "kuvvet_newton_tyt", "atislar", "itme_momentum", "cembersel_hareket", "elektrik_alan_potansiyel"),
            topicKeywords = listOf("fizik", "atış", "momentum", "çembersel", "elektrik", "optik"),
            kazanimCodes = setOf("FIZ.TYT.01", "FIZ.AYT.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "vip_fizik_ozet",
                    chapterTitle = "Mekanik ve Elektrik-Optik Kritik ÖSYM Kalıpları",
                    startSeconds = 141,
                    endSeconds = 1760,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK", "KAZ_TYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("atış", "momentum", "çembersel", "elektrik", "optik", "hareket"),
                    keyFormulaSummary = "Enerji korunumu: E_ilk = E_son + W_sürtünme.",
                    osymTip = "Birim analizini (N, J, W, V) kontrol etmek işlem hatalarını anında yakalatır."
                )
            )
        ),

        // =====================================================================
        // 11. TYT & AYT KİMYA (Görkem Şahin öncelikli, Ferrum)
        // =====================================================================
        TopicLectureVideo(
            id = "gorkemsahin_tyt_kimya",
            videoId = "PSWtyR8C7h4",
            searchQueryFallback = "Görkem Şahin Benim Hocam TYT Kimya Mol Kavramı Türler Asit Baz",
            title = "TYT Kimya: Atom, Kimyasal Türler, Mol Kavramı ve Asit-Baz (Sıfırdan Zirveye)",
            teacherName = "Görkem Şahin",
            channelName = "Benim Hocam / Görkem Şahin",
            teacherBadge = "Kimya 1 Numara",
            popularityRank = 1,
            examType = ExamType.TYT,
            subjectId = "tyt_kimya",
            subjectName = "Kimya",
            topicIds = setOf(
                "kimya_bilimi", "atom_modelleri", "periyodik_sistem", "kimyasal_turler_etkilesim",
                "maddenin_halleri", "kimyanin_temel_kanunlari", "mol_kavrami", "kimyasal_hesaplamalar",
                "karisimlar", "asitler_bazlar_tuzlar", "kimya_her_yerde"
            ),
            topicKeywords = listOf(
                "kimya", "atom", "periyodik", "iyonik", "kovalent", "hidrojen bağı", "mol", "avogadro",
                "karışım", "çözelti", "asit", "baz", "tuz", "ph"
            ),
            kazanimCodes = setOf("KIM.TYT.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "gorkem_tyt_atom_turler",
                    chapterTitle = "Atom, Periyodik Sistem ve Kimyasal Türler Arası Etkileşimler",
                    startSeconds = 152,
                    endSeconds = 17320,
                    kazanimIds = setOf("KAZ_TYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("atom", "periyodik", "iyonlaşma enerjisi", "iyonik", "kovalent", "hidrojen bağı", "etkileşim", "lewis"),
                    keyFormulaSummary = "İyonlaşma enerjisinde 2A > 3A ve 5A > 6A (küresel simetri) istisnası vardır. F, O, N'ye bağlı H atomu hidrojen bağı oluşturur.",
                    osymTip = "Bağ polarlığı ile molekül polarlığını karıştırma: CO₂ ve CCl₄'te bağlar polar, molekül apolardır!"
                ),
                LectureChapterSegment(
                    id = "gorkem_tyt_mol_hesaplama",
                    chapterTitle = "Mol Kavramı, Avogadro Sayısı ve Kimyasal Hesaplamalar",
                    startSeconds = 17321,
                    endSeconds = 26193,
                    kazanimIds = setOf("KAZ_TYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("mol", "avogadro", "nk", "normal koşul", "akb", "sınırlayıcı bileşen", "verim", "tepkime"),
                    keyFormulaSummary = "n = m / M_A = N / N_A = V(NŞA) / 22,4 L. Tepkime katsayıları doğrudan mol oranı verir.",
                    osymTip = "'1 mol atom içeren' ile '1 mol molekül' ifadelerine dikkat: 1 mol H₂O toplam 3 mol atom içerir!"
                ),
                LectureChapterSegment(
                    id = "gorkem_tyt_karisim_asitbaz",
                    chapterTitle = "Karışımlar, Ayırma Yöntemleri ve Asitler-Bazlar-Tuzlar",
                    startSeconds = 26194,
                    endSeconds = 32300,
                    kazanimIds = setOf("KAZ_TYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("karışım", "damıtma", "ayırma hunisi", "asit", "baz", "tuz", "nötralleşme", "amfoter", "yarı soy"),
                    keyFormulaSummary = "Yarı soy metaller (Cu, Hg, Ag) yalnızca oksijenli kuvvetli asitlerle (HNO₃, H₂SO₄) tepkime verir; H₂ gazı çıkarmazlar!",
                    osymTip = "Amfoter metaller (Al, Zn, Cr, Sn, Pb, Be) hem asitlerle hem kuvvetli bazlarla tepkimeye girip H₂ gazı açığa çıkarır."
                )
            )
        ),
        TopicLectureVideo(
            id = "gorkemsahin_ayt_kimya",
            videoId = "Sxi_dmFEF2o",
            searchQueryFallback = "Görkem Şahin Benim Hocam AYT Kimya Gazlar Denge Elektrokimya Organik",
            title = "AYT Kimya Konu Anlatımı | Gazlar, Hız, Denge, Elektrokimya ve Organik",
            teacherName = "Görkem Şahin",
            channelName = "Benim Hocam / Görkem Şahin",
            teacherBadge = "AYT Kimya 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_kimya",
            subjectName = "Kimya",
            topicIds = setOf(
                "modern_atom_teorisi", "gazlar", "sivi_cozeltiler", "kimyasal_enerji", "kimyasal_hiz",
                "kimyasal_denge", "asit_baz_dengesi", "cozunurluk_dengesi", "kimya_ve_elektrik",
                "karbon_kimyasina_giris", "organik_bilesikler"
            ),
            topicKeywords = listOf(
                "orbital", "kuantum", "gaz", "pv=nrt", "molarite", "entalpi", "hess", "tepkime hızı",
                "denge", "kc", "kp", "ph", "kçç", "pil", "elektroliz", "organik", "alkan", "alken", "alkol"
            ),
            kazanimCodes = setOf("KIM.AYT.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "gorkem_ayt_gaz_cozelti",
                    chapterTitle = "Modern Atom, İdeal Gazlar ve Sıvı Çözeltiler",
                    startSeconds = 310,
                    endSeconds = 1140,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("gaz", "pv=nrt", "kısmi basınç", "difüzyon", "çözünürlük", "molarite", "koligatif", "orbital"),
                    keyFormulaSummary = "P·V = n·R·T (T mutlaka Kelvin!). Graham Difüzyon: v₁/v₂ = √(M₂·T₁ / M₁·T₂). ΔT_k = K_k·m·i.",
                    osymTip = "Gaz sorularında sıcaklığı °C yerine Kelvin'e (T = t + 273) çevirmeyi asla unutma!"
                ),
                LectureChapterSegment(
                    id = "gorkem_ayt_hiz_denge",
                    chapterTitle = "Enerji (Entalpi), Tepkime Hızı ve Kimyasal-Asit-Baz Dengesi",
                    startSeconds = 1180,
                    endSeconds = 2120,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("entalpi", "tepkime hızı", "denge", "le chatelier", "kc", "kp", "ph", "kçç", "tampon"),
                    keyFormulaSummary = "K_p = K_c·(RT)^Δn. Denge sabiti (Kc, Ka, Kb, Kçç) YALNIZCA sıcaklıkla değişir; katalizör ve derişim K'yı değiştirmez!",
                    osymTip = "Mekanizmalı tepkimelerde hız bağıntısı daima YAVAŞ basamağın girenlerine göre yazılır."
                ),
                LectureChapterSegment(
                    id = "gorkem_ayt_elektro_organik",
                    chapterTitle = "Kimya ve Elektrik (Piller) ile Organik Kimya",
                    startSeconds = 2160,
                    endSeconds = 3240,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("elektrot", "pil", "anot", "katot", "elektroliz", "organik", "hibritleşme", "alkan", "alken", "alkin", "alkol", "aldehit", "keton", "ester"),
                    keyFormulaSummary = "Kimya Pili: Anotta yükseltgenme (aşınma), Katotta indirgenme (kütle artışı). Elektronlar dış devrede Anot → Katot akar.",
                    osymTip = "Organik adlandırmada en uzun karbon zinciri seçilir ve fonksiyonel gruba (veya çoklu bağa) en küçük numara verilir."
                )
            )
        ),
        TopicLectureVideo(
            id = "ferrum_kimya",
            videoId = "FhvigPh7I6o",
            searchQueryFallback = "Ferrum Kimya TYT AYT Kimya Konu Anlatımı",
            title = "Ferrum Kimya | TYT & AYT Kimya Nokta Atışı Anlatım",
            teacherName = "Ferrum",
            channelName = "Ferrum",
            teacherBadge = "Kısa & Öz Kimya",
            popularityRank = 2,
            examType = ExamType.TYT,
            subjectId = "tyt_kimya",
            subjectName = "Kimya",
            topicIds = setOf("kimyasal_turler_etkilesim", "mol_kavrami", "asitler_bazlar_tuzlar", "gazlar", "kimyasal_denge", "organik_bilesikler"),
            topicKeywords = listOf("kimya", "mol", "asit", "baz", "denge", "organik", "gaz"),
            kazanimCodes = setOf("KIM.TYT.01", "KIM.AYT.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "ferrum_tyt_temel",
                    chapterTitle = "Kimyasal Türler, Mol ve Asit-Baz Özeti",
                    startSeconds = 240,
                    endSeconds = 1280,
                    kazanimIds = setOf("KAZ_TYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("mol", "türler", "asit", "baz", "atom"),
                    keyFormulaSummary = "Güçlü etkileşimler (iyonik, kovalent, metalik) kimyasal; zayıf etkileşimler (Van der Waals, hidrojen) fiziksel özellikleri belirler.",
                    osymTip = "London kuvvetleri tüm moleküllerde bulunur ve elektron sayısı (mol kütlesi) arttıkça güçlenir."
                ),
                LectureChapterSegment(
                    id = "ferrum_ayt_denge_organik",
                    chapterTitle = "AYT Kimyasal Denge ve Organik Tepkimeler",
                    startSeconds = 1320,
                    endSeconds = 2460,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("denge", "organik", "gaz", "pil"),
                    keyFormulaSummary = "Markovnikov kuralı: Asimetrik alkene HX katılırken H, hidrojeni çok olan çift bağlı karbona bağlanır.",
                    osymTip = "Primer alkol → Aldehit → Karboksilik asit; Sekonder alkol → Keton yükseltgenir; Tersiyer alkol yükseltgenmez."
                )
            )
        ),

        // =====================================================================
        // 12. TYT & AYT BİYOLOJİ (Dr. Biyoloji öncelikli, Biosem, Selin Hoca)
        // =====================================================================
        TopicLectureVideo(
            id = "drbiyoloji_tyt_biyoloji",
            videoId = "5xxh0ixnMmk",
            searchQueryFallback = "Dr Biyoloji TYT Biyoloji Hücre Organeller Bölünmeler Kalıtım",
            title = "TYT Biyoloji Kampı | Hücre, Madde Geçişleri, Bölünmeler ve Kalıtım",
            teacherName = "Dr. Biyoloji",
            channelName = "Dr. Biyoloji (Barış Hoca)",
            teacherBadge = "MEB & YKS 1 Numara",
            popularityRank = 1,
            examType = ExamType.TYT,
            subjectId = "tyt_biyoloji",
            subjectName = "Biyoloji",
            topicIds = setOf(
                "canlilarin_ortak_ozellikleri", "canlilarin_temel_bilesenleri", "hucre_ve_organeller",
                "hucre_zarindan_madde_gecisi", "canlilarin_siniflandirilmasi", "mitoz_ve_eseysiz_ureme",
                "mayoz_ve_eseyli_ureme", "kalitim", "ekosistem_ekolojisi", "guncel_cevre_sorunlari"
            ),
            topicKeywords = listOf(
                "biyoloji", "hücre", "organel", "enzim", "difüzyon", "osmoz", "aktif taşıma",
                "mitoz", "mayoz", "kalıtım", "soyağacı", "ekosistem", "besin piramidi"
            ),
            kazanimCodes = setOf("BIY.TYT.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "drbiyo_tyt_hucre_enzim",
                    chapterTitle = "Temel Bileşenler, Enzimler, Hücre ve Organeller",
                    startSeconds = 90,
                    endSeconds = 3814,
                    kazanimIds = setOf("KAZ_TYT_FEN_BIYOLOJI"),
                    kazanimCodes = setOf("BIY.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("hücre", "organel", "ribozom", "mitokondri", "kloroplast", "enzim", "madde geçişi", "osmoz", "aktif taşıma"),
                    keyFormulaSummary = "Ribozom tüm canlılarda ortaktır (zarsız). Aktif taşıma, endositoz ve ekzositozda ATP harcanır; difüzyon ve osmozda harcanmaz.",
                    osymTip = "Çeperli hücreler (bitki, mantar, bakteri) endositoz yapamaz ama ekzositoz yapabilir!"
                ),
                LectureChapterSegment(
                    id = "drbiyo_tyt_bolunme_kalitim",
                    chapterTitle = "Mitoz-Mayoz Bölünmeler ve Kalıtım (Soyağacı Analizi)",
                    startSeconds = 5212,
                    endSeconds = 7670,
                    kazanimIds = setOf("KAZ_TYT_FEN_BIYOLOJI"),
                    kazanimCodes = setOf("BIY.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf("mitoz", "mayoz", "krossing over", "kalıtım", "soyağacı", "otozomal", "x'e bağlı", "kan grubu"),
                    keyFormulaSummary = "Mayoz I'de homolog kromozomlar ayrılır (2n → n), Mayoz II'de kardeş kromatitler ayrılır. DNA miktarı İnterfaz-S evresinde iki katına çıkar.",
                    osymTip = "Soyağacında taralı olmayan anne-babadan taralı çocuk doğuyorsa özellik kesinlikle çekiniktir!"
                ),
                LectureChapterSegment(
                    id = "drbiyo_tyt_ekosistem",
                    chapterTitle = "Canlıların Sınıflandırılması ve Ekosistem Ekolojisi",
                    startSeconds = 7671,
                    endSeconds = 8250,
                    kazanimIds = setOf("KAZ_TYT_FEN_BIYOLOJI"),
                    kazanimCodes = setOf("BIY.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("ekosistem", "besin piramidi", "azot döngüsü", "biyolojik birikim", "sınıflandırma", "omurgalı"),
                    keyFormulaSummary = "Besin piramidinde yukarı çıkıldıkça aktarılan enerji ve biyokütle azalır (%10 yasası), zehirli madde birikimi artar.",
                    osymTip = "Ayrıştırıcılar (saprofitler) besin piramidinin her basamağında görev yapar."
                )
            )
        ),
        TopicLectureVideo(
            id = "drbiyoloji_ayt_biyoloji",
            videoId = "qGCkt4pSiT8",
            searchQueryFallback = "Dr Biyoloji AYT Biyoloji Sinir Sistemi Sistemler Genden Proteine Fotosentez",
            title = "AYT Biyoloji Kampı | İnsan Fizyolojisi (Sistemler), Genden Proteine ve Enerji",
            teacherName = "Dr. Biyoloji",
            channelName = "Dr. Biyoloji (Barış Hoca)",
            teacherBadge = "AYT Biyoloji 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_biyoloji",
            subjectName = "Biyoloji",
            topicIds = setOf(
                "sinir_sistemi", "endokrin_sistem", "duyu_organlari", "destek_ve_hareket",
                "sindirim_sistemi", "dolasim_ve_bagisiklik", "solunum_sistemi", "uriner_sistem",
                "ureme_sistemi_embriyonik", "komunite_ve_populasyon", "genden_proteine",
                "canlilarda_enerji_donusumleri", "bitki_biyolojisi", "canlilar_ve_cevre"
            ),
            topicKeywords = listOf(
                "nöron", "sinir", "impuls", "hormon", "endokrin", "kas", "sindirim", "dolaşım",
                "nefron", "böbrek", "dna", "rna", "protein sentezi", "fotosentez", "solunum", "ksilem", "bitki"
            ),
            kazanimCodes = setOf("BIY.AYT.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "drbiyo_ayt_sinir_endokrin",
                    chapterTitle = "Sinir Sistemi (İmpuls & Sinaps) ve Endokrin Sistem",
                    startSeconds = 35,
                    endSeconds = 2413,
                    kazanimIds = setOf("KAZ_AYT_FEN_BIYOLOJI"),
                    kazanimCodes = setOf("BIY.AYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("nöron", "sinir", "impuls", "aksiyon potansiyeli", "sinaps", "hormon", "hipofiz", "endokrin", "insülin"),
                    keyFormulaSummary = "Akson boyunca impuls iletimi elektrokimyasal (hızlı), sinapsta kimyasaldır (yavaş, nörotransmitter). Miyelin kılıf ve akson çapı hızı artırır.",
                    osymTip = "Uyarı şiddetinin artması impuls hızını DEĞİL, impuls sayısını (frekansını) ve uyarılan nöron sayısını artırır!"
                ),
                LectureChapterSegment(
                    id = "drbiyo_ayt_sistemler_dolasim_uriner",
                    chapterTitle = "Destek-Hareket, Sindirim, Dolaşım, Solunum ve Üriner Sistem",
                    startSeconds = 2414,
                    endSeconds = 4775,
                    kazanimIds = setOf("KAZ_AYT_FEN_BIYOLOJI"),
                    kazanimCodes = setOf("BIY.AYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("kas", "sarkomer", "sindirim", "dolaşım", "kalp", "starling", "solunum", "hemoglobin", "nefron", "böbrek"),
                    keyFormulaSummary = "Nefronda: Süzülme (pasif, kan basıncıyla) → Geri Emilim (pasif+aktif) → Salgılama (aktif). Sağlıklı bireyde glikozun %100'ü proksimal tüpte geri emilir.",
                    osymTip = "Kas kasılmasında I bandı ve H bandı daralır, A bandının boyu ve kasın toplam hacmi DEĞİŞMEZ!"
                ),
                LectureChapterSegment(
                    id = "drbiyo_ayt_gen_enerji_bitki",
                    chapterTitle = "Genden Proteine, Fotosentez-Solunum ve Bitki Biyolojisi",
                    startSeconds = 5681,
                    endSeconds = 9024,
                    kazanimIds = setOf("KAZ_AYT_FEN_BIYOLOJI"),
                    kazanimCodes = setOf("BIY.AYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf("dna", "rna", "replikasyon", "transkripsiyon", "translasyon", "kodon", "fotosentez", "solunum", "krebs", "ksilem", "floem", "bitki"),
                    keyFormulaSummary = "Santral Dogma: DNA →(Transkripsiyon) mRNA →(Translasyon) Protein. Glikoliz tüm canlılarda sitoplazmada gerçekleşir.",
                    osymTip = "Fotosentezde üretilen O₂'nin kaynağı CO₂ değil, fotolize uğrayan H₂O'dur!"
                )
            )
        ),
        TopicLectureVideo(
            id = "biosem_biyoloji",
            videoId = "hLGlPmULuhQ",
            searchQueryFallback = "Biosem TYT AYT Biyoloji Kampı Hücre Sistemler",
            title = "39 Günde TYT & 42 Günde AYT Biyoloji Kampı",
            teacherName = "Biosem",
            channelName = "Biosem (Semih Hoca)",
            teacherBadge = "Şematik Anlatım",
            popularityRank = 2,
            examType = ExamType.TYT,
            subjectId = "tyt_biyoloji",
            subjectName = "Biyoloji",
            topicIds = setOf("hucre_ve_organeller", "kalitim", "sinir_sistemi", "dolasim_ve_bagisiklik", "genden_proteine", "canlilarda_enerji_donusumleri"),
            topicKeywords = listOf("biyoloji", "hücre", "kalıtım", "sinir", "sistemler", "dna", "fotosentez"),
            kazanimCodes = setOf("BIY.TYT.01", "BIY.AYT.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "biosem_tyt_hucre_kalitim",
                    chapterTitle = "Hücre, Bölünmeler ve Kalıtım Şematik Tekrar",
                    startSeconds = 240,
                    endSeconds = 1320,
                    kazanimIds = setOf("KAZ_TYT_FEN_BIYOLOJI"),
                    kazanimCodes = setOf("BIY.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("hücre", "organel", "kalıtım", "mitoz", "mayoz"),
                    keyFormulaSummary = "Prokaryotlarda zarlı organel ve çekirdek zarı yoktur; sadece ribozom bulunur.",
                    osymTip = "Eşeysiz üremede kalıtsal çeşitlilik yalnızca mutasyonla oluşur."
                ),
                LectureChapterSegment(
                    id = "biosem_ayt_sistemler",
                    chapterTitle = "İnsan Sistemleri ve Genden Proteine Özeti",
                    startSeconds = 1360,
                    endSeconds = 2480,
                    kazanimIds = setOf("KAZ_AYT_FEN_BIYOLOJI"),
                    kazanimCodes = setOf("BIY.AYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("sinir", "hormon", "sistem", "dna", "fotosentez"),
                    keyFormulaSummary = "AUG başlangıç kodonu (Metiyonin); UAA, UAG, UGA durdurma kodonlarıdır (amino asit şifrelemez).",
                    osymTip = "İnsanda hormonlar kanla taşınır ve yalnızca ilgili reseptöre sahip hedef dokuyu etkiler."
                )
            )
        ),
        TopicLectureVideo(
            id = "selinhoca_biyoloji",
            videoId = "c0QRuSu3oDg",
            searchQueryFallback = "Selin Hoca Biyoloji TYT AYT Full Tekrar",
            title = "Ezbersiz TYT-AYT Biyoloji Full Tekrar",
            teacherName = "Selin Hoca",
            channelName = "Selin Hoca Biyoloji",
            teacherBadge = "Ezbersiz Biyoloji",
            popularityRank = 3,
            examType = ExamType.AYT,
            subjectId = "ayt_biyoloji",
            subjectName = "Biyoloji",
            topicIds = setOf("hucre_ve_organeller", "kalitim", "sinir_sistemi", "genden_proteine", "bitki_biyolojisi"),
            topicKeywords = listOf("biyoloji", "hücre", "kalıtım", "sistem", "bitki"),
            kazanimCodes = setOf("BIY.TYT.01", "BIY.AYT.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "selin_biyo_ozet",
                    chapterTitle = "Hücre, Kalıtım ve İnsan Sistemleri Temel Mantığı",
                    startSeconds = 260,
                    endSeconds = 1520,
                    kazanimIds = setOf("KAZ_TYT_FEN_BIYOLOJI", "KAZ_AYT_FEN_BIYOLOJI"),
                    kazanimCodes = setOf("BIY.TYT.01", "BIY.AYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("hücre", "kalıtım", "sinir", "sistem", "bitki"),
                    keyFormulaSummary = "Homeostazi (iç denge), sinir ve endokrin sistemin koordineli çalışmasıyla sağlanır.",
                    osymTip = "Grafik sorularında bağımsız değişken (x ekseni) ve bağımlı değişken (y ekseni) ilişkisini dikkatle oku."
                )
            )
        ),

        // =====================================================================
        // 13. AYT EDEBİYAT & TYT/AYT SOSYAL (Kadir Gümüş, Ramazan Yetgin, Coğrafyanın Kodları)
        // =====================================================================
        TopicLectureVideo(
            id = "kadirgumus_ayt_edebiyat",
            videoId = "dFKIJQkOUkw",
            searchQueryFallback = "Kadir Gümüş Benim Hocam AYT Edebiyat Full Tekrar",
            title = "AYT Edebiyat Konu Anlatımı | Şiir Bilgisi, Divan, Tanzimat ve Cumhuriyet",
            teacherName = "Kadir Gümüş",
            channelName = "Benim Hocam / Kadir Gümüş",
            teacherBadge = "AYT Edebiyat 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_edebiyat",
            subjectName = "Edebiyat",
            topicIds = setOf(
                "guzel_sanatlar_ve_edebiyat", "siir_bilgisi", "edebi_sanatlar",
                "islamiyet_oncesi_turk_edebiyati", "halk_edebiyati", "divan_edebiyati",
                "tanzimat_edebiyati", "servetifunun_fecriati", "milli_edebiyat",
                "cumhuriyet_siir", "cumhuriyet_roman_hikaye", "edebi_akimlar"
            ),
            topicKeywords = listOf(
                "edebiyat", "şiir", "kafiye", "redif", "edebi sanat", "divan", "gazel", "kaside",
                "halk edebiyatı", "tanzimat", "servetifünun", "milli edebiyat", "cumhuriyet", "edebi akım"
            ),
            kazanimCodes = setOf("EDE.AYT.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "kadir_edebiyat_siir_sanat",
                    chapterTitle = "Şiir Bilgisi (Ölçü-Kafiye-Redif) ve Edebi Sanatlar",
                    startSeconds = 155,
                    endSeconds = 3482,
                    kazanimIds = emptySet(),
                    kazanimCodes = setOf("EDE.AYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf("şiir", "kafiye", "uyak", "redif", "edebi sanat", "teşbih", "istiare", "kinaye", "telmih"),
                    keyFormulaSummary = "Önce redifi (aynı görevdeki ek/sözcük) ayır, sonra köklerde ses benzerliğinden kafiyeyi bul. İstiare = benzeyen veya benzetilenden yalnızca biri var.",
                    osymTip = "Kinayede hem gerçek hem mecaz anlam mümkün olup asıl kastedilen mecazdır; tevriyede iki gerçek anlam vardır."
                ),
                LectureChapterSegment(
                    id = "kadir_edebiyat_divan_halk",
                    chapterTitle = "İslamiyet Öncesi, Halk Edebiyatı ve Divan Edebiyatı",
                    startSeconds = 7659,
                    endSeconds = 12078,
                    kazanimIds = emptySet(),
                    kazanimCodes = setOf("EDE.AYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf("divan", "gazel", "kaside", "mesnevi", "halk edebiyatı", "koşma", "semai", "islamiyet öncesi", "destan"),
                    keyFormulaSummary = "Gazel (aa-ba-ca, 5-15 beyit, aşk/şarap), Kaside (33-99 beyit, övgü), Mesnevi (aa-bb-cc, beyit sınırı yok). Koşma (11'li hece).",
                    osymTip = "Divan şiirinde şairin mahlası son beyitte (makta), halk şiirinde son dörtlükte (tapşırma) geçer."
                ),
                LectureChapterSegment(
                    id = "kadir_edebiyat_tanzimat_cumhuriyet",
                    chapterTitle = "Tanzimat, Servetifünun, Milli Edebiyat, Cumhuriyet ve Akımlar",
                    startSeconds = 12079,
                    endSeconds = 24000,
                    kazanimIds = emptySet(),
                    kazanimCodes = setOf("EDE.AYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf("tanzimat", "servetifünun", "fecriati", "milli edebiyat", "cumhuriyet", "roman", "edebi akım", "realizm", "romantizm"),
                    keyFormulaSummary = "Tanzimat I: Toplum için sanat (Romantizm). Tanzimat II & Servetifünun: Sanat için sanat (Realizm/Parnasizm/Sembolizm).",
                    osymTip = "Yazar-eser eşleştirmelerinde döneminin akımını ve dil özelliğini (sade dil vs ağır dil) ipucu olarak kullan."
                )
            )
        ),
        TopicLectureVideo(
            id = "ramazanyetgin_tarih",
            videoId = "Amk26bASq2s",
            searchQueryFallback = "Ramazan Yetgin Benim Hocam TYT AYT Tarih Konu Anlatımı",
            title = "TYT-AYT Tarih Konu Anlatımı | Türk Tarihi, Osmanlı ve Milli Mücadele",
            teacherName = "Ramazan Yetgin",
            channelName = "Benim Hocam / Ramazan Yetgin",
            teacherBadge = "Tarih 1 Numara",
            popularityRank = 1,
            examType = ExamType.TYT,
            subjectId = "tyt_tarih",
            subjectName = "Tarih",
            topicIds = setOf(
                "tarih_ve_zaman_tyt", "insanligin_ilk_donemleri", "ilk_orta_cag_turk",
                "islam_medeniyetinin_dogusu", "turklerin_islamiyeti_kabulu", "beylikten_devlete_osmanli",
                "dunya_gucu_osmanli", "kurtulus_savasi_hazirlik", "kurtulus_savasi_cepheler",
                "ataturk_ilke_inkilaplari", "ayt_ilk_cag_uygarliklari", "ayt_osmanli_kultur_medeniyet",
                "ayt_xx_yuzyil_osmanli", "ayt_milli_mucadele"
            ),
            topicKeywords = listOf(
                "tarih", "ilk çağ", "türk tarihi", "osmanlı", "divan", "tımar", "milli mücadele",
                "kurtuluş savaşı", "kongre", "amasya", "erzurum", "sivas", "lozan", "atatürk", "inkılap"
            ),
            kazanimCodes = setOf("SOS.TYT.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "ramazan_tarih_ilk_osmanli",
                    chapterTitle = "İlk-Orta Çağ Türk Tarihi ve Osmanlı Kültür-Medeniyeti",
                    startSeconds = 240,
                    endSeconds = 1140,
                    kazanimIds = setOf("KAZ_TYT_SOSYAL"),
                    kazanimCodes = setOf("SOS.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("ilk çağ", "orta çağ", "kut", "kurultay", "osmanlı", "tımar", "devşirme", "divan", "ıslahat"),
                    keyFormulaSummary = "Kut anlayışı (verasetin belirsizliği) taht kavgalarına yol açmıştır. Tımar sistemi hem hazine yükünü azaltmış hem taşra güvenliğini sağlamıştır.",
                    osymTip = "TYT Tarih yorum sorularında sadece paragrafta verilen bilgiden çıkarım yap; kronolojiyi bağlam kontrolü için kullan."
                ),
                LectureChapterSegment(
                    id = "ramazan_tarih_milli_mucadele",
                    chapterTitle = "Milli Mücadele Hazırlık, Cepheler ve Atatürk İlke-İnkılapları",
                    startSeconds = 1180,
                    endSeconds = 2280,
                    kazanimIds = setOf("KAZ_TYT_SOSYAL"),
                    kazanimCodes = setOf("SOS.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf("milli mücadele", "kurtuluş savaşı", "amasya", "erzurum", "sivas", "tbmm", "sakarya", "lozan", "inkılap", "cumhuriyetçilik", "laiklik"),
                    keyFormulaSummary = "Amasya Genelgesi: Kurtuluş Savaşı'nın amacı, gerekçesi ve yöntemi. Erzurum: Toplanış bölgesel, kararlar ulusal. Sivas: Tüm cemiyetler birleşti.",
                    osymTip = "Halkçılık = Eşitlik & Sosyal devlet; Milliyetçilik = Bağımsızlık & Ulusal kimlik; Devletçilik = Ekonomik kalkınma."
                )
            )
        ),
        TopicLectureVideo(
            id = "cografyaninkodlari_cografya",
            videoId = "A-gX2IlzmNA",
            searchQueryFallback = "Coğrafyanın Kodları TYT AYT Coğrafya Harita İklim Nüfus Full Tekrar",
            title = "Coğrafyanın Kodları | Harita, İklim, İç-Dış Kuvvetler ve Nüfus",
            teacherName = "Coğrafyanın Kodları",
            channelName = "Coğrafyanın Kodları (Yunus Turan)",
            teacherBadge = "Harita & Kodlama Lideri",
            popularityRank = 1,
            examType = ExamType.TYT,
            subjectId = "tyt_cografya",
            subjectName = "Coğrafya",
            topicIds = setOf(
                "doga_ve_insan", "dunyanin_sekli_hareketleri", "cografi_konum", "harita_bilgisi",
                "atmosfer_ve_iklim", "ic_ve_dis_kuvvetler", "su_toprak_bitki", "nufus_ve_yerlesme",
                "ekonomik_faaliyetler", "dogal_afetler", "ayt_ekosistem_ve_biyoceşitlilik",
                "ayt_turkiye_ekonomisi", "ayt_kuresel_ortam_bolgeler"
            ),
            topicKeywords = listOf(
                "coğrafya", "harita", "izohips", "iklim", "basınç", "rüzgar", "iç kuvvet", "dış kuvvet",
                "nüfus", "göç", "piramit", "doğal afet", "ekonomi", "tarım", "maden", "felsefe", "bilgi"
            ),
            kazanimCodes = setOf("SOS.TYT.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "cogkod_harita_iklim",
                    chapterTitle = "Harita Bilgisi (İzohips), Coğrafi Konum ve İklim Tipleri",
                    startSeconds = 230,
                    endSeconds = 1090,
                    kazanimIds = setOf("KAZ_TYT_SOSYAL"),
                    kazanimCodes = setOf("SOS.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("harita", "izohips", "ölçek", "enlem", "boylam", "iklim", "yağış", "sıcaklık", "eksen eğikliği"),
                    keyFormulaSummary = "İzohipsler sıklaşırsa eğim artar; V şeklinin ucu yüksek tarafa bakıyorsa vadi, alçak tarafa bakıyorsa sırttır.",
                    osymTip = "İklim grafiğinde Haziran-Temmuz-Ağustos en sıcaksa Kuzey Yarım Küre, Aralık-Ocak-Şubat en sıcaksa Güney Yarım Küre'dir!"
                ),
                LectureChapterSegment(
                    id = "cogkod_kuvvet_nufus_afet",
                    chapterTitle = "İç-Dış Kuvvetler, Nüfus Piramitleri ve Doğal Afetler",
                    startSeconds = 1120,
                    endSeconds = 2040,
                    kazanimIds = setOf("KAZ_TYT_SOSYAL"),
                    kazanimCodes = setOf("SOS.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf("iç kuvvet", "dış kuvvet", "fay", "deprem", "nüfus", "göç", "piramit", "doğal afet", "ekonomi", "felsefe"),
                    keyFormulaSummary = "Geniş tabanlı piramit yüksek doğum oranı (gelişmekte olan), dar tabanlı/arı kovanı piramit düşük doğum & yüksek yaşam süresi (gelişmiş).",
                    osymTip = "Deprem, volkanizma ve sıcak su kaynakları levha sınırlarıyla (Pasifik Ateş Çemberi, Alp-Himalaya) birebir örtüşür!"
                )
            )
        )
    )
}

/**
 * Soru metni, kazanım ve soru tipine göre en popüler hocayı ve videonun
 * tam ilgili dakika segmentini deterministik olarak çözümleyen motor.
 */
object TopicLectureResolver {

    private data class ScoredCandidate(
        val video: TopicLectureVideo,
        val chapter: LectureChapterSegment,
        val score: Int
    )

    fun resolveForQuestion(
        question: YksQuestion,
        examType: ExamType,
        subjectName: String,
        topicName: String
    ): ResolvedLectureRecommendation {
        val effectiveTopicName = topicName.ifBlank { question.topicHeader }
        val effectiveSubjectName = subjectName.ifBlank { question.subject }

        val catalogTopic = SoruLabCatalog.resolveTopicFast(
            topicId = effectiveTopicName,
            topicName = effectiveTopicName
        )
        val resolvedTopicId = catalogTopic?.id.orEmpty()
        val matchedKazanim = OsymKazanimTaxonomy.resolveKazanimForQuestion(
            question = question,
            fallbackTopicId = resolvedTopicId.ifBlank { effectiveTopicName }
        )
        val detectedQuestionType = OsymQuestionType.entries.firstOrNull {
            it.name.equals(question.questionType.trim(), ignoreCase = true) ||
                it.label.equals(question.questionType.trim(), ignoreCase = true)
        } ?: OsymQuestionType.classifyQuestion(question)

        val normSubject = normalizeSubjectBucket(effectiveSubjectName, effectiveTopicName, question)
        val normTopic = normalizeTurkish(effectiveTopicName)
        val questionStemNorm = normalizeTurkish(
            buildString {
                append(question.stemTop)
                append(' ')
                append(question.bulletsOrSteps.joinToString(" "))
                append(' ')
                append(question.stemBottom)
            }
        )
        val questionTextNorm = normalizeTurkish(
            buildString {
                append(questionStemNorm)
                append(' ')
                append(question.topicHeader)
                append(' ')
                append(effectiveTopicName)
                append(' ')
                append(matchedKazanim.title)
                append(' ')
                append(matchedKazanim.topicName)
            }
        )

        val scoredCandidates = TopicLectureVideoCatalog.allVideos.flatMap { video ->
            val videoBaseScore = scoreVideoMatch(
                video = video,
                examType = examType,
                normSubject = normSubject,
                resolvedTopicId = resolvedTopicId,
                normTopic = normTopic,
                questionTextNorm = questionTextNorm,
                matchedKazanim = matchedKazanim
            )
            video.chapters.map { chapter ->
                val chapterScore = scoreChapterMatch(
                    chapter = chapter,
                    matchedKazanim = matchedKazanim,
                    detectedQuestionType = detectedQuestionType,
                    normTopic = normTopic,
                    questionStemNorm = questionStemNorm,
                    questionTextNorm = questionTextNorm
                )
                ScoredCandidate(
                    video = video,
                    chapter = chapter,
                    score = videoBaseScore + chapterScore
                )
            }
        }.sortedWith(
            compareByDescending<ScoredCandidate> { it.score }
                .thenBy { it.video.popularityRank }
                .thenByDescending { if (it.video.hasVerifiedVideoId) 1 else 0 }
                .thenBy { it.chapter.startSeconds }
        )

        val best = scoredCandidates.first()
        val primaryVideo = best.video
        val primaryChapter = best.chapter

        // Aynı ders/konu için alternatif popüler hocaları (en iyi chapter ile) seç
        val sameBucketCandidates = scoredCandidates
            .filter {
                it.video.teacherName != primaryVideo.teacherName &&
                    normalizeSubjectBucket(it.video.subjectName, "", question) ==
                    normalizeSubjectBucket(primaryVideo.subjectName, "", question)
            }
            .groupBy { it.video.teacherName }
            .values
            .map { it.first() }
            .sortedWith(compareByDescending<ScoredCandidate> { it.score }.thenBy { it.video.popularityRank })
            .take(4)

        val fallbackAlternatives = if (sameBucketCandidates.isNotEmpty()) {
            sameBucketCandidates
        } else {
            scoredCandidates
                .filter { it.video.teacherName != primaryVideo.teacherName }
                .groupBy { it.video.teacherName }
                .values
                .map { it.first() }
                .take(3)
        }

        val alternativeOptions = fallbackAlternatives.map { cand ->
            ResolvedLectureOption(
                video = cand.video,
                chapter = cand.chapter,
                matchScore = cand.score,
                youtubeWatchUrlWithTimestamp = cand.video.watchUrlAt(cand.chapter.startSeconds, cand.chapter.chapterTitle),
                youtubeEmbedUrlWithTimestamp = cand.video.embedUrlAt(cand.chapter.startSeconds, cand.chapter.chapterTitle),
                formattedTimestamp = cand.chapter.formattedTimestamp,
                hasDirectEmbedVideo = cand.video.hasVerifiedVideoId
            )
        }

        val watchUrl = primaryVideo.watchUrlAt(primaryChapter.startSeconds, primaryChapter.chapterTitle)
        val embedUrl = primaryVideo.embedUrlAt(primaryChapter.startSeconds, primaryChapter.chapterTitle)
        val matchReason = buildMatchReason(
            kazanim = matchedKazanim,
            questionType = detectedQuestionType,
            video = primaryVideo,
            chapter = primaryChapter
        )

        return ResolvedLectureRecommendation(
            primaryVideo = primaryVideo,
            primaryChapter = primaryChapter,
            matchedKazanim = matchedKazanim,
            detectedQuestionType = detectedQuestionType,
            youtubeWatchUrlWithTimestamp = watchUrl,
            youtubeEmbedUrlWithTimestamp = embedUrl,
            formattedTimestamp = primaryChapter.formattedTimestamp,
            hasDirectEmbedVideo = primaryVideo.hasVerifiedVideoId,
            matchReason = matchReason,
            relatedChapters = primaryVideo.chapters.sortedBy { it.startSeconds },
            alternativeTeachers = alternativeOptions
        )
    }

    private fun normalizeSubjectBucket(
        subjectName: String,
        topicName: String,
        question: YksQuestion
    ): String {
        val combined = normalizeTurkish("$subjectName $topicName ${question.subject} ${question.topicHeader}")
        return when {
            combined.contains("geometri") || combined.contains("ucgen") || combined.contains("cember") ||
                combined.contains("cokgen") || combined.contains("prizma") || combined.contains("analitik") -> "geometri"
            combined.contains("edebiyat") || combined.contains("divan") || combined.contains("tanzimat") ||
                combined.contains("siir") -> "edebiyat"
            combined.contains("turkce") || combined.contains("paragraf") || combined.contains("yazim") ||
                combined.contains("noktalama") || combined.contains("dil bilgisi") -> "turkce"
            combined.contains("fizik") || combined.contains("atislar") || combined.contains("momentum") ||
                combined.contains("optik") || combined.contains("elektrik") -> "fizik"
            combined.contains("kimya") || combined.contains("mol") || combined.contains("asit") ||
                combined.contains("organik") || combined.contains("denge") -> "kimya"
            combined.contains("biyoloji") || combined.contains("hucre") || combined.contains("kalitim") ||
                combined.contains("sinir") || combined.contains("fotosentez") -> "biyoloji"
            combined.contains("tarih") || combined.contains("osmanli") || combined.contains("milli mucadele") ||
                combined.contains("inkilap") -> "tarih"
            combined.contains("cografya") || combined.contains("iklim") || combined.contains("harita") ||
                combined.contains("nufus") || combined.contains("felsefe") -> "cografya"
            combined.contains("matematik") || combined.contains("turev") || combined.contains("limit") ||
                combined.contains("integral") || combined.contains("trigonometri") ||
                combined.contains("fonksiyon") || combined.contains("problem") ||
                combined.contains("polinom") || combined.contains("logaritma") -> "matematik"
            else -> "matematik"
        }
    }

    private fun scoreVideoMatch(
        video: TopicLectureVideo,
        examType: ExamType,
        normSubject: String,
        resolvedTopicId: String,
        normTopic: String,
        questionTextNorm: String,
        matchedKazanim: OsymKazanim
    ): Int {
        var score = 0
        val videoBucket = normalizeTurkish(video.subjectName)
        if (videoBucket == normSubject) {
            score += 420
        } else if (
            (normSubject == "geometri" && videoBucket == "matematik") ||
            (normSubject == "edebiyat" && videoBucket == "turkce")
        ) {
            score += 40
        } else {
            score -= 300
        }

        if (video.examType == examType) {
            score += 85
        } else {
            score += 20
        }

        if (resolvedTopicId.isNotBlank() && resolvedTopicId in video.topicIds) {
            score += 240
        }
        if (matchedKazanim.topicId.isNotBlank() && matchedKazanim.topicId in video.topicIds) {
            score += 180
        }
        if (matchedKazanim.code.isNotBlank() && matchedKazanim.code in video.kazanimCodes) {
            score += 150
        }

        val topicKeywordHits = video.topicKeywords.count { kw ->
            val nkw = normalizeTurkish(kw)
            nkw.isNotBlank() && (normTopic.contains(nkw) || questionTextNorm.contains(nkw))
        }
        score += topicKeywordHits.coerceAtMost(8) * 32

        // Popüler hoca ve doğrulanmış video önceliği
        score += (6 - video.popularityRank.coerceIn(1, 5)) * 45
        if (video.hasVerifiedVideoId) {
            score += 90
        }

        return score
    }

    private fun scoreChapterMatch(
        chapter: LectureChapterSegment,
        matchedKazanim: OsymKazanim,
        detectedQuestionType: OsymQuestionType,
        normTopic: String,
        questionStemNorm: String,
        questionTextNorm: String
    ): Int {
        var score = 0

        if (matchedKazanim.id.isNotBlank() && matchedKazanim.id in chapter.kazanimIds) {
            score += 260
        }
        if (matchedKazanim.code.isNotBlank() && matchedKazanim.code in chapter.kazanimCodes) {
            score += 180
        }
        if (detectedQuestionType in chapter.questionTypes) {
            score += 80
        }

        var stemKeywordHits = 0
        var topicOnlyKeywordHits = 0
        for (kw in chapter.subTopicKeywords) {
            val nkw = normalizeTurkish(kw)
            if (nkw.isBlank()) continue
            when {
                questionStemNorm.contains(nkw) -> stemKeywordHits++
                normTopic.contains(nkw) -> topicOnlyKeywordHits++
            }
        }
        score += stemKeywordHits.coerceAtMost(10) * 60 + topicOnlyKeywordHits.coerceAtMost(4) * 15

        val chapterTitleWords = normalizeTurkish(chapter.chapterTitle)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 && it !in setOf("konu", "soru", "veya", "icin", "olan", "ozel") }
        val titleHits = chapterTitleWords.count { w -> questionStemNorm.contains(w) || questionTextNorm.contains(w) }
        score += titleHits.coerceAtMost(6) * 18

        return score
    }
}
