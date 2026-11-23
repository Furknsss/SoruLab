package com.example.data

import androidx.compose.runtime.Immutable
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * YouTube videosundan çekilen zaman damgalı tek bir konuşma satırı.
 */
@Immutable
data class TranscriptSegment(
    val startSeconds: Int,
    val text: String
)

/**
 * Dinamik transkript + semantik zaman damgası eşleştirme sonucu.
 */
@Immutable
data class GroundedTimestampResult(
    val startSeconds: Int,
    val endSeconds: Int,
    val matchedSnippet: String,
    val matchedKeywords: List<String>,
    val source: GroundingSource
) {
    val formattedTimestamp: String
        get() = formatLectureTimestamp(startSeconds)
}

enum class GroundingSource {
    /** YouTube Türkçe transkript + Gemini semantik doğrulama */
    TRANSCRIPT_GEMINI,
    /** YouTube Türkçe transkript + yerel kayan pencere kavram yoğunluğu motoru */
    TRANSCRIPT_LOCAL,
    /** Transkript erişilemediğinde veya eşleşme yetersizse katalog varsayılanı */
    CATALOG_FALLBACK
}

/**
 * Dinamik Video Zaman Damgası Motoru (v1.5.2):
 *
 * Herhangi bir YKS sorusu için videonun genel konu başlangıcı yerine, hocanın doğrudan
 * o sorunun alt kavramını / soru tipini anlatmaya başladığı tam saniyeyi bulur:
 * 1. YouTube InnerTube Android Player API üzerinden videonun Türkçe zaman damgalı
 *    altyazısını (TimedText XML / JSON3) sıfır maliyetle ve doğrudan cihazdan çeker,
 *    oturum boyunca videoId bazlı önbellekte saklar.
 * 2. Katalogda eşleşen ünite/bölüm penceresi içinde 35 saniyelik kayan pencere +
 *    Türkçe YKS kavram genişletme motoruyla en yüksek yoğunluklu ilk anlatım anını bulur.
 * 3. İsteğe bağlı olarak en iyi aday kesitleri Gemini Flash'a gönderip (4.5 sn timeout)
 *    hocanın tam anlatıma başladığı saniyeyi semantik olarak doğrular; ağ/AI yoksa
 *    yerel transkript sonucunu anında döner.
 */
object DynamicVideoTimestampGrounder {

    private val trLocale = Locale("tr", "TR")

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val transcriptCache = ConcurrentHashMap<String, List<TranscriptSegment>>()
    private val resultCache = ConcurrentHashMap<String, GroundedTimestampResult>()

    private val turkishStopwords = setOf(
        "ve", "veya", "ile", "icin", "gibi", "kadar", "gore", "olarak", "daha", "en",
        "bu", "su", "o", "bir", "iki", "uc", "dort", "bes", "her", "tum", "butun",
        "asagidakilerden", "hangisi", "hangileri", "yukaridaki", "yukarida", "verilen",
        "verilmistir", "verilebilir", "olabilir", "olamaz", "dogrudur", "yanlistir",
        "degildir", "kactir", "nedir", "nasil", "neden", "ornektir", "ornek", "madde",
        "maddelerden", "ifadelerden", "ifadelerinden", "yargilarindan", "yargilardan",
        "bilgilerden", "seceneklerden", "soru", "gore", "uzere", "olmak", "olan",
        "oldugu", "edilir", "yapilan", "yaptiklari", "calismalarin", "ozelligidir",
        "ozelliklerinden", "yalniz", "hem", "de", "da", "ki", "mi", "mu", "icin",
        "ancak", "fakat", "ama", "ise", "ile", "sonra", "once", "arasinda", "icinde"
    )

    /**
     * YKS soru metnindeki ipuçlarını hocanın derste kullandığı Türkçe terimlerle genişletir.
     * Örn: "heterojen katı-sıvı" -> "süspansiyon", "kolloit", "aerosol", "emülsiyon".
     */
    private val domainConceptExpansions: Map<String, List<String>> = mapOf(
        "heterojen" to listOf("heterojen", "suspansiyon", "emulsiyon", "kolloit", "kolloid", "aerosol", "adi karisim"),
        "kati sivi" to listOf("suspansiyon", "kati sivi", "kolloit", "kolloid"),
        "sivi sivi" to listOf("emulsiyon", "sivi sivi", "ayirma hunisi"),
        "kan" to listOf("kolloit", "kolloid", "suspansiyon", "kan"),
        "sis" to listOf("aerosol", "sis", "duman"),
        "duman" to listOf("aerosol", "duman", "sis"),
        "celik" to listOf("alasim", "homojen", "celik"),
        "homojen" to listOf("homojen", "cozelti", "cozunen", "cozucu", "alasim"),
        "simya" to listOf("simya", "simyaci", "felsefe tasi", "olumsuzluk", "deneme yanilma", "sinama yanilma"),
        "siralama" to listOf("siralama", "sirala", "buyuk", "kucuk", "birinci bolge", "artan", "azalan"),
        "teget" to listOf("teget", "egim", "geometrik yorum", "normal"),
        "ekstremum" to listOf("ekstremum", "maksimum", "minimum", "artan", "azalan", "isaret tablosu"),
        "belirsizlik" to listOf("belirsizlik", "sifir bolu sifir", "carpanlara ayirma", "l hospital"),
        "viraj" to listOf("viraj", "surtunme", "egimli", "merkezcil"),
        "sarkac" to listOf("sarkac", "basit harmonik", "periyot", "yay"),
        "induksiyon" to listOf("induksiyon", "lenz", "faraday", "manyetik aki"),
        "fotoelektrik" to listOf("fotoelektrik", "esik enerjisi", "baglanma enerjisi", "kesme potansiyeli"),
        "denge" to listOf("denge", "le chatelier", "kc", "kp", "denge sabiti"),
        "tampon" to listOf("tampon", "titrasyon", "hidroliz", "esdegerlik"),
        "elektroliz" to listOf("elektroliz", "anot", "katot", "faraday", "yukselgenme", "indirgenme"),
        "soyagaci" to listOf("soyagaci", "otozomal", "cekinik", "baskin", "hemofili", "renk korlugu"),
        "replikasyon" to listOf("replikasyon", "transkripsiyon", "translasyon", "mrna", "trna", "kodon"),
        "solunum" to listOf("glikoliz", "krebs", "ets", "fermantasyon", "oksijenli solunum")
    )

    private val introCuePhrases = listOf(
        "inceleyelim", "inceledi", "inceleyecegiz", "bes bolgede", "bes cesit", "dort cesit",
        "cesitleri", "basliyoruz", "gelelim", "gecelim", "nedir", "demektir", "birincisi",
        "tanimlayalim", "ayiriyoruz", "siniflandiralim", "konumuz", "baslik", "dikkat et",
        "soru tipi", "karsimiza cikar", "osym sorar"
    )

    /**
     * Verilen soru ve seçili hoca/bölüm için videodaki en kesin başlangıç saniyesini bulur.
     */
    suspend fun groundTimestampForQuestion(
        question: YksQuestion,
        video: TopicLectureVideo,
        chapter: LectureChapterSegment,
        enableGeminiRefinement: Boolean = true
    ): GroundedTimestampResult = withContext(Dispatchers.IO) {
        val fallbackStart = chapter.startSeconds.coerceAtLeast(0)
        val fallbackEnd = (chapter.endSeconds ?: (fallbackStart + 240)).coerceAtLeast(fallbackStart + 60)

        val videoId = video.videoId.trim()
        if (!isValidYoutubeVideoId(videoId)) {
            return@withContext GroundedTimestampResult(
                startSeconds = fallbackStart,
                endSeconds = fallbackEnd,
                matchedSnippet = chapter.chapterTitle,
                matchedKeywords = emptyList(),
                source = GroundingSource.CATALOG_FALLBACK
            )
        }

        val queryFingerprint = "${question.id}|${question.stemTop.hashCode()}|$videoId|${chapter.id}"
        resultCache[queryFingerprint]?.let { return@withContext it }

        val segments = fetchOrGetCachedTranscript(videoId)
        if (segments.isEmpty()) {
            return@withContext GroundedTimestampResult(
                startSeconds = fallbackStart,
                endSeconds = fallbackEnd,
                matchedSnippet = chapter.chapterTitle,
                matchedKeywords = emptyList(),
                source = GroundingSource.CATALOG_FALLBACK
            )
        }

        // Katalogda tanımlı ünite/bölüm aralığını arama penceresi olarak belirle
        val chapterWindowEnd = resolveChapterWindowEnd(video, chapter)
        val localResult = findBestTimestampInTranscript(
            question = question,
            segments = segments,
            windowStartSeconds = fallbackStart,
            windowEndSeconds = chapterWindowEnd,
            fallbackChapterTitle = chapter.chapterTitle
        )

        if (localResult == null) {
            return@withContext GroundedTimestampResult(
                startSeconds = fallbackStart,
                endSeconds = fallbackEnd,
                matchedSnippet = chapter.chapterTitle,
                matchedKeywords = emptyList(),
                source = GroundingSource.CATALOG_FALLBACK
            )
        }

        // İsteğe bağlı Layer 2: En iyi aday transkript kesitlerini Gemini Flash ile semantik doğrula
        val finalResult = if (enableGeminiRefinement) {
            refineWithGeminiIfAvailable(
                question = question,
                segments = segments,
                windowStartSeconds = fallbackStart,
                windowEndSeconds = chapterWindowEnd,
                localBest = localResult
            ) ?: localResult
        } else {
            localResult
        }

        resultCache[queryFingerprint] = finalResult
        finalResult
    }

    /**
     * Videodaki sonraki chapter başlangıcına göre mevcut chapter'ın gerçek bitiş sınırını bulur.
     */
    internal fun resolveChapterWindowEnd(
        video: TopicLectureVideo,
        chapter: LectureChapterSegment
    ): Int? {
        val explicit = chapter.endSeconds
        if (explicit != null && explicit > chapter.startSeconds + 30) return explicit
        val nextStart = video.chapters
            .map { it.startSeconds }
            .filter { it > chapter.startSeconds + 30 }
            .minOrNull()
        return nextStart
    }

    /**
     * YouTube InnerTube Android Player API üzerinden videonun Türkçe transkriptini çeker.
     */
    suspend fun fetchOrGetCachedTranscript(videoId: String): List<TranscriptSegment> = withContext(Dispatchers.IO) {
        transcriptCache[videoId]?.let { return@withContext it }

        val fetched = runCatching {
            val captionUrl = fetchTurkishCaptionUrlViaInnerTube(videoId) ?: return@runCatching emptyList()
            val rawTranscript = downloadCaptionPayload(captionUrl) ?: return@runCatching emptyList()
            parseTimedTranscript(rawTranscript)
        }.getOrDefault(emptyList())

        if (fetched.isNotEmpty()) {
            transcriptCache[videoId] = fetched
        }
        fetched
    }

    private fun fetchTurkishCaptionUrlViaInnerTube(videoId: String): String? {
        val payload = JSONObject().apply {
            put("videoId", videoId)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "ANDROID")
                    put("clientVersion", "20.10.38")
                    put("hl", "tr")
                    put("gl", "TR")
                })
            })
        }

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .header("User-Agent", "com.google.android.youtube/20.10.38 (Linux; U; Android 14; tr_TR)")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null

            val root = JSONObject(body)
            val tracks = root
                .optJSONObject("captions")
                ?.optJSONObject("playerCaptionsTracklistRenderer")
                ?.optJSONArray("captionTracks")
                ?: return null

            var manualTrUrl: String? = null
            var asrTrUrl: String? = null
            var anyUrl: String? = null

            for (i in 0 until tracks.length()) {
                val track = tracks.optJSONObject(i) ?: continue
                val lang = track.optString("languageCode", "")
                val kind = track.optString("kind", "")
                val baseUrl = track.optString("baseUrl", "")
                if (baseUrl.isBlank()) continue
                if (anyUrl == null) anyUrl = baseUrl
                if (lang.startsWith("tr", ignoreCase = true)) {
                    if (kind != "asr" && manualTrUrl == null) {
                        manualTrUrl = baseUrl
                    } else if (asrTrUrl == null) {
                        asrTrUrl = baseUrl
                    }
                }
            }
            return manualTrUrl ?: asrTrUrl ?: anyUrl
        }
    }

    private fun downloadCaptionPayload(captionUrl: String): String? {
        val request = Request.Builder()
            .url(captionUrl)
            .header("User-Agent", "com.google.android.youtube/20.10.38 (Linux; U; Android 14; tr_TR)")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    /**
     * YouTube TimedText XML (format="3" <p t="..."> veya klasik <text start="...">)
     * ve JSON3 ({"events":[...]}) formatlarını ayrıştırır.
     */
    internal fun parseTimedTranscript(raw: String): List<TranscriptSegment> {
        val trimmed = raw.trimStart()
        if (trimmed.isEmpty()) return emptyList()
        return if (trimmed.startsWith("{")) {
            parseJson3Transcript(trimmed)
        } else if (trimmed.startsWith("<")) {
            parseXmlTranscript(trimmed)
        } else {
            emptyList()
        }
    }

    private val json3EventPattern = Regex(
        """"tStartMs"\s*:\s*(\d+)(.*?)"segs"\s*:\s*\[(.*?)\]""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )
    private val json3Utf8Pattern = Regex(""""utf8"\s*:\s*"((?:\\.|[^"\\])*)"""")

    private fun parseJson3Transcript(raw: String): List<TranscriptSegment> {
        val result = mutableListOf<TranscriptSegment>()
        for (evMatch in json3EventPattern.findAll(raw)) {
            val startMs = evMatch.groupValues[1].toLongOrNull() ?: continue
            val segsBody = evMatch.groupValues[3]
            val sb = StringBuilder()
            for (uMatch in json3Utf8Pattern.findAll(segsBody)) {
                sb.append(unescapeJsonString(uMatch.groupValues[1]))
            }
            val cleaned = sb.toString().replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
            if (cleaned.isNotEmpty()) {
                result.add(TranscriptSegment(startSeconds = (startMs / 1000L).toInt().coerceAtLeast(0), text = cleaned))
            }
        }
        return result
    }

    private fun unescapeJsonString(raw: String): String = raw
        .replace("\\n", " ")
        .replace("\\r", " ")
        .replace("\\t", " ")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")

    private val xmlFormat3Pattern = Regex(
        """<p\b[^>]*\bt="(\d+)"[^>]*>(.*?)</p>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val xmlClassicPattern = Regex(
        """<text\b[^>]*\bstart="([\d.]+)"[^>]*>(.*?)</text>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val innerTagPattern = Regex("""<[^>]+>""")

    private fun parseXmlTranscript(raw: String): List<TranscriptSegment> {
        val result = mutableListOf<TranscriptSegment>()
        for (match in xmlFormat3Pattern.findAll(raw)) {
            val tMs = match.groupValues[1].toLongOrNull() ?: continue
            val innerText = innerTagPattern.replace(match.groupValues[2], "")
            val cleaned = decodeBasicHtmlEntities(innerText)
                .replace('\n', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleaned.isNotEmpty()) {
                result.add(TranscriptSegment(startSeconds = (tMs / 1000L).toInt().coerceAtLeast(0), text = cleaned))
            }
        }
        if (result.isNotEmpty()) return result

        for (match in xmlClassicPattern.findAll(raw)) {
            val startSec = match.groupValues[1].toDoubleOrNull()?.toInt()?.coerceAtLeast(0) ?: continue
            val innerText = innerTagPattern.replace(match.groupValues[2], "")
            val cleaned = decodeBasicHtmlEntities(innerText)
                .replace('\n', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleaned.isNotEmpty()) {
                result.add(TranscriptSegment(startSeconds = startSec, text = cleaned))
            }
        }
        return result
    }

    private fun decodeBasicHtmlEntities(text: String): String = text
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    /**
     * Soru metninden (kök + öncüller + şıklar) yüksek ayırt ediciliğe sahip Türkçe kavramları çıkarır.
     */
    internal fun extractConceptTerms(question: YksQuestion): Pair<List<String>, List<String>> {
        val rawText = buildString {
            append(question.stemTop).append(' ')
            append(question.bulletsOrSteps.joinToString(" ")).append(' ')
            append(question.stemBottom).append(' ')
            append(question.options.values.joinToString(" ")).append(' ')
            append(question.topicHeader)
        }
        val normalized = normalizeTurkishText(rawText)
        val tokens = normalized
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in turkishStopwords && !it.all(Char::isDigit) }
            .distinct()

        // İkili kavram öbekleri (örn. "kati sivi", "heterojen karisim", "felsefe tasi")
        val wordList = normalized.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        val bigrams = mutableListOf<String>()
        for (i in 0 until wordList.lastIndex) {
            val a = wordList[i]
            val b = wordList[i + 1]
            if (a.length >= 3 && b.length >= 3 && a !in turkishStopwords && b !in turkishStopwords) {
                bigrams.add("$a $b")
            }
        }

        // YKS alan kavram genişletmeleri
        val expanded = linkedSetOf<String>()
        expanded.addAll(tokens)
        val expandedPhrases = linkedSetOf<String>()
        expandedPhrases.addAll(bigrams)

        for ((trigger, synonyms) in domainConceptExpansions) {
            if (normalized.contains(trigger)) {
                synonyms.forEach { syn ->
                    if (' ' in syn) expandedPhrases.add(syn) else expanded.add(syn)
                }
            }
        }

        return Pair(expanded.toList(), expandedPhrases.toList())
    }

    /**
     * Transkript içinde 35 saniyelik kayan pencere ile sorunun alt kavramının anlatılmaya
     * başlandığı en doğru saniyeyi deterministik olarak bulur.
     */
    internal fun findBestTimestampInTranscript(
        question: YksQuestion,
        segments: List<TranscriptSegment>,
        windowStartSeconds: Int,
        windowEndSeconds: Int?,
        fallbackChapterTitle: String
    ): GroundedTimestampResult? {
        if (segments.isEmpty()) return null

        val (keywords, phrases) = extractConceptTerms(question)
        if (keywords.isEmpty() && phrases.isEmpty()) return null

        // Önce eşleşen ünite/chapter penceresi içinde ara
        val scopedSegments = segments.filter { seg ->
            seg.startSeconds >= (windowStartSeconds - 15).coerceAtLeast(0) &&
                (windowEndSeconds == null || seg.startSeconds <= windowEndSeconds + 30)
        }
        val activeSegments = if (scopedSegments.size >= 5) scopedSegments else segments

        val scoredWindows = scoreSlidingWindows(
            segments = activeSegments,
            keywords = keywords,
            phrases = phrases,
            windowSpanSeconds = 35
        )
        if (scoredWindows.isEmpty()) return null

        val maxScore = scoredWindows.maxOf { it.score }
        if (maxScore < 20) return null

        // En yüksek puanın %82'sine ulaşan EN ERKEN tepe noktasını seç:
        // Hoca konuyu ilk kez tahtaya yazıp anlatmaya başladığı an (ortası değil!)
        val topThreshold = (maxScore * 0.82).toInt().coerceAtLeast(20)
        val bestWindow = scoredWindows
            .filter { it.score >= topThreshold }
            .minByOrNull { it.startSeconds }
            ?: scoredWindows.maxByOrNull { it.score }
            ?: return null

        val leadInStart = (bestWindow.startSeconds - 3).coerceAtLeast(windowStartSeconds.coerceAtLeast(0))
        val suggestedEnd = (leadInStart + 180).let { end ->
            if (windowEndSeconds != null && windowEndSeconds > leadInStart + 30) {
                end.coerceAtMost(windowEndSeconds)
            } else {
                end
            }
        }

        return GroundedTimestampResult(
            startSeconds = leadInStart,
            endSeconds = suggestedEnd,
            matchedSnippet = bestWindow.snippet.ifBlank { fallbackChapterTitle },
            matchedKeywords = bestWindow.matchedTerms.take(5),
            source = GroundingSource.TRANSCRIPT_LOCAL
        )
    }

    private data class ScoredWindow(
        val startSeconds: Int,
        val score: Int,
        val matchedTerms: List<String>,
        val snippet: String
    )

    private fun scoreSlidingWindows(
        segments: List<TranscriptSegment>,
        keywords: List<String>,
        phrases: List<String>,
        windowSpanSeconds: Int
    ): List<ScoredWindow> {
        val normalizedSegments = segments.map { it to normalizeTurkishText(it.text) }
        val results = mutableListOf<ScoredWindow>()

        for (i in normalizedSegments.indices) {
            val anchor = normalizedSegments[i].first
            val windowEnd = anchor.startSeconds + windowSpanSeconds

            val combinedNorm = StringBuilder()
            val rawSnippet = StringBuilder()
            var j = i
            while (j < normalizedSegments.size && normalizedSegments[j].first.startSeconds <= windowEnd) {
                combinedNorm.append(normalizedSegments[j].second).append(' ')
                if (rawSnippet.length < 140) {
                    if (rawSnippet.isNotEmpty()) rawSnippet.append(' ')
                    rawSnippet.append(normalizedSegments[j].first.text)
                }
                j++
            }

            val windowText = combinedNorm.toString()
            val matchedTerms = mutableListOf<String>()
            var score = 0

            // 1. Öbek eşleşmeleri (yüksek ağırlık)
            for (phrase in phrases) {
                if (windowText.contains(phrase)) {
                    score += 32
                    matchedTerms.add(phrase)
                }
            }

            // 2. Tekil kavram eşleşmeleri (kök eşleşmesi: "suspansiyon" -> "suspansiyonu")
            for (kw in keywords) {
                val stem = if (kw.length >= 6) kw.take(kw.length - 1) else kw
                if (containsWordPrefix(windowText, stem)) {
                    score += if (kw.length >= 7) 18 else 12
                    matchedTerms.add(kw)
                }
            }

            if (matchedTerms.isEmpty()) continue

            // 3. Farklı kavramların aynı pencerede buluşma sinerjisi
            val distinctCount = matchedTerms.distinct().size
            if (distinctCount >= 2) score += distinctCount * 14

            // 4. Anlatım başlangıcı ipucu bonusu ("beş bölgede inceledi", "çeşitleri", "gelelim...")
            if (introCuePhrases.any { cue -> windowText.contains(cue) }) {
                score += 18
            }

            // İlk segmentin kendisi doğrudan bir kavram içeriyorsa keskin başlangıç bonusu
            val anchorNorm = normalizedSegments[i].second
            if (keywords.any { kw ->
                    val stem = if (kw.length >= 6) kw.take(kw.length - 1) else kw
                    containsWordPrefix(anchorNorm, stem)
                } || phrases.any { anchorNorm.contains(it) }
            ) {
                score += 14
            }

            results.add(
                ScoredWindow(
                    startSeconds = anchor.startSeconds,
                    score = score,
                    matchedTerms = matchedTerms.distinct(),
                    snippet = rawSnippet.toString().trim()
                )
            )
        }
        return results
    }

    private fun containsWordPrefix(haystack: String, prefix: String): Boolean {
        if (prefix.length < 3) return false
        var idx = haystack.indexOf(prefix)
        while (idx >= 0) {
            val boundaryBefore = idx == 0 || !haystack[idx - 1].isLetterOrDigit()
            if (boundaryBefore) return true
            idx = haystack.indexOf(prefix, idx + 1)
        }
        return false
    }

    /**
     * Layer 2: En yüksek puanlı 4 transkript kesitini Gemini 3.8 Flash'a göndererek
     * hocanın sorunun çözüm bilgisini anlatmaya başladığı tam saniyeyi seçtirir.
     */
    private suspend fun refineWithGeminiIfAvailable(
        question: YksQuestion,
        segments: List<TranscriptSegment>,
        windowStartSeconds: Int,
        windowEndSeconds: Int?,
        localBest: GroundedTimestampResult
    ): GroundedTimestampResult? {
        val (keywords, phrases) = extractConceptTerms(question)
        val scopedSegments = segments.filter { seg ->
            seg.startSeconds >= (windowStartSeconds - 15).coerceAtLeast(0) &&
                (windowEndSeconds == null || seg.startSeconds <= windowEndSeconds + 30)
        }
        val activeSegments = if (scopedSegments.size >= 5) scopedSegments else segments
        val topCandidates = scoreSlidingWindows(activeSegments, keywords, phrases, 40)
            .sortedByDescending { it.score }
            .fold(mutableListOf<ScoredWindow>()) { acc, cand ->
                if (acc.size < 4 && acc.none { kotlin.math.abs(it.startSeconds - cand.startSeconds) < 35 }) {
                    acc.add(cand)
                }
                acc
            }
            .sortedBy { it.startSeconds }

        if (topCandidates.size < 2) return null

        val prompt = buildString {
            appendLine("Sen bir YKS video zaman damgası uzmanısın.")
            appendLine("Soru: ${question.stemTop} ${question.bulletsOrSteps.joinToString(" ")} ${question.stemBottom} ${question.options.values.joinToString(" | ")}")
            appendLine("Aşağıda öğretmenin ders videosundan aday transkript kesitleri var:")
            topCandidates.forEach { cand ->
                appendLine("- [${cand.startSeconds}s] (${formatLectureTimestamp(cand.startSeconds)}): ${cand.snippet}")
            }
            appendLine("Öğretmenin bu soruyu çözmek için gereken alt kavramı anlatmaya BAŞLADIĞI en doğru saniyeyi seç.")
            appendLine("Sadece tek satır JSON dön: {\"startSeconds\": <tamsayi>, \"snippet\": \"<kisa 6-10 kelimelik ozet>\"}")
        }

        val responseText = withTimeoutOrNull(4500L) {
            runCatching {
                val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                    .generativeModel("gemini-3.8-flash")
                model.generateContent(prompt).text
            }.getOrNull()
        } ?: return null

        val jsonStart = responseText.indexOf('{')
        val jsonEnd = responseText.lastIndexOf('}')
        if (jsonStart < 0 || jsonEnd <= jsonStart) return null

        val parsed = runCatching { JSONObject(responseText.substring(jsonStart, jsonEnd + 1)) }.getOrNull()
            ?: return null
        val chosenSec = parsed.optInt("startSeconds", -1)
        if (chosenSec < 0) return null

        // Halüsinasyon koruması: Seçilen saniye aday kesitlerden birinin yakınında olmalı
        val validRange = topCandidates.any { kotlin.math.abs(it.startSeconds - chosenSec) <= 45 }
        if (!validRange) return null

        val refinedStart = (chosenSec - 2).coerceAtLeast(windowStartSeconds.coerceAtLeast(0))
        val snippet = parsed.optString("snippet", "").ifBlank { localBest.matchedSnippet }
        return localBest.copy(
            startSeconds = refinedStart,
            endSeconds = (refinedStart + 180).coerceAtLeast(refinedStart + 60),
            matchedSnippet = snippet,
            source = GroundingSource.TRANSCRIPT_GEMINI
        )
    }

    internal fun normalizeTurkishText(raw: String): String = raw
        .lowercase(trLocale)
        .replace('ç', 'c')
        .replace('ğ', 'g')
        .replace('ı', 'i')
        .replace('İ', 'i')
        .replace('ö', 'o')
        .replace('ş', 's')
        .replace('ü', 'u')
        .replace('â', 'a')
        .replace('î', 'i')
        .replace('û', 'u')
        .replace('-', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}
