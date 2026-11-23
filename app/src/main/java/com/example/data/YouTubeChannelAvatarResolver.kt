package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * YouTube Kanal Profil Fotoğrafı (PP) Çözümleyici:
 * - Katalogdaki tüm YKS hocalarının doğrulanmış HD (`s176`) YouTube kanal PP URL'lerini anında döner.
 * - Yeni eklenen veya haritada olmayan herhangi bir video/hoca için YouTube InnerTube `next`
 *   uç noktasından canlı olarak gerçek `yt3.ggpht.com` PP URL'sini çeker ve önbelleğe alır.
 */
object YouTubeChannelAvatarResolver {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private val verifiedAvatarsByTeacher: Map<String, String> = mapOf(
        "altug gunes" to "https://yt3.ggpht.com/TECBetql1-UG3CJpe4FqIeDCFB2ZoJ5JSrAzRI5f4_kQ4pjEqzzudnvF5lDF2BzxA8ijqGw6AUg=s176-c-k-c0x00ffffff-no-rj",
        "biosem" to "https://yt3.ggpht.com/ktoIZ-DIskx0X-w3FXJ8KqL7HdQKG4btOUJ0qzimEf6lglLEuqCX74yzaOCOsJwWGCaprj8bkA=s176-c-k-c0x00ffffff-no-rj",
        "biyikli matematik" to "https://yt3.ggpht.com/TiM7pPVRfiGx39zP2J--H4sLIROpn3xWh9JNmm1wsuXmfLoiIgXmHLZDPv-GcjTUb5w8_FN8sQ=s176-c-k-c0x00ffffff-no-rj",
        "cebrail hocam" to "https://yt3.ggpht.com/jWL-3wcySCTPWgt9EsZDr1TMjVF-7xQPeklhJ3s6pdM5EVNS3zEokXoJykDTGHKBkukgSm596kk=s176-c-k-c0x00ffffff-no-rj",
        "cografyanin kodlari" to "https://yt3.ggpht.com/8HECFQ1K1Md3I4fWtDdwf9ZTsBzCzT87GiT4e1MfeUyu6jQzpTt2gDcbfTJE176lQMZMVvjxMg=s176-c-k-c0x00ffffff-no-rj",
        "deniz hoca" to "https://yt3.ggpht.com/eqGG093Jn29z-oQxtOvcc6zJmDN8Qoa1lNsqb7wpf99CtghXA0aJ7e_-rV7f_wDYWb1wMhmURA=s176-c-k-c0x00ffffff-no-rj",
        "dr. biyoloji" to "https://yt3.ggpht.com/1QeP7NChkvO-i9DD0DB_14BRNkbkFC0XN2qlExLvR6vPxY_qTSi1ZPWs9Z0-amWe4-ZwQXfKxYk=s176-c-k-c0x00ffffff-no-rj",
        "erdal aydemir" to "https://yt3.ggpht.com/3ADvJIBt1pWTW_CYN7FPx99qTc3eITLxK1oQFE-Wl4O1YILObXkTlVfIXGDytGeF-muC8_oB=s176-c-k-c0x00ffffff-no-rj",
        "eyup b." to "https://yt3.ggpht.com/ytc/AIdro_nZ2lVDYFVKnWJ99k32qGrZCrKJw_niAEBor4gyg9Wed8I=s176-c-k-c0x00ffffff-no-rj",
        "ferrum" to "https://yt3.ggpht.com/xmxB4_1gNWyBSJuPWWD3EoZMeEfszg9BUJ8XpQoBaT4K844b_VezNftHsZEA1d_Dyg74MJ7s=s176-c-k-c0x00ffffff-no-rj",
        "gorkem sahin" to "https://yt3.ggpht.com/ytc/AIdro_m3T1Hoop3lIH6Wy-ly4ub9bZYWVdU3bwYwfY_eaQ1L2mQ=s176-c-k-c0x00ffffff-no-rj",
        "kadir gumus" to "https://yt3.ggpht.com/ytc/AIdro_m3T1Hoop3lIH6Wy-ly4ub9bZYWVdU3bwYwfY_eaQ1L2mQ=s176-c-k-c0x00ffffff-no-rj",
        "kenan kara" to "https://yt3.ggpht.com/PGES5-HtQvRU3VfPcR0B2V1w_UNIC9w4GjHpRKH6vs5OmWwVfHC0JEBgd8ahgb74QdeIh9nq=s176-c-k-c0x00ffffff-no-rj",
        "mert hoca" to "https://yt3.ggpht.com/EzEGAWYieFxMA_qTfcHv6CcjFjUprxWHLsyW66nHf6Ltn3O_VJdibTmO8pbQvNGZbJUjKdHpkg=s176-c-k-c0x00ffffff-no-rj",
        "ramazan yetgin" to "https://yt3.ggpht.com/ytc/AIdro_m3T1Hoop3lIH6Wy-ly4ub9bZYWVdU3bwYwfY_eaQ1L2mQ=s176-c-k-c0x00ffffff-no-rj",
        "rehber matematik" to "https://yt3.ggpht.com/ytc/AIdro_lmysUJY0XWZkvtrl-jM30EqzWbXyvMbOdrSE8eHTxImS0=s176-c-k-c0x00ffffff-no-rj",
        "rustu hoca" to "https://yt3.ggpht.com/xLR7kPDgQoTltFfRpiMMayqK-JN3N132IPNlVFdfZ_EgauiAriAMpmUGwrghpRiITKpbjWq3rg=s176-c-k-c0x00ffffff-no-rj",
        "selin hoca" to "https://yt3.ggpht.com/BWzUb3MGdmAHIChH9uRLfWxzL9STeVMttQw-roK7V-WQqtwUHqP5HJ2w083fFbWCfZio_RixDw=s176-c-k-c0x00ffffff-no-rj",
        "vip fizik" to "https://yt3.ggpht.com/EZh9EO88rZHH6_v161_9t54vS6L7b2w0JrUZQd1MJkoVOv2CNPqUBHU12AeCW-IIagOFi1vLuJc=s176-c-k-c0x00ffffff-no-rj",
        "ozcan aykin" to "https://yt3.ggpht.com/VKK2LqJkN8786kyU1ZJfc0cPKa2-llqbPBU6xJiyAb-gvVVfwwaUQqxw4M6UQCO-Sxt9jwTEjQ=s176-c-k-c0x00ffffff-no-rj"
    )

    private val dynamicAvatarCache = ConcurrentHashMap<String, String>()

    private val ownerAvatarPattern = Regex(
        """"videoOwnerRenderer".*?"thumbnails":\[\{"url":"(https://yt3\.[^"]+)""""
    )
    private val fallbackAvatarPattern = Regex(
        """"(https://yt3\.ggpht\.com/[^"]+)""""
    )

    /**
     * Önbellekte veya doğrulanmış listede varsa senkron olarak anında URL döner.
     */
    fun instantAvatarUrl(teacherName: String, videoId: String): String? {
        val key = DynamicVideoTimestampGrounder.normalizeTurkishText(teacherName)
        verifiedAvatarsByTeacher[key]?.let { return it }
        val cleanVid = videoId.trim()
        if (cleanVid.isNotBlank()) {
            dynamicAvatarCache[cleanVid]?.let { return it }
        }
        return null
    }

    /**
     * Doğrulanmış listede olmayan yeni bir hoca/video için YouTube'dan canlı PP URL'si çeker.
     */
    suspend fun resolveAvatarUrl(teacherName: String, videoId: String): String? = withContext(Dispatchers.IO) {
        instantAvatarUrl(teacherName, videoId)?.let { return@withContext it }

        val cleanVid = videoId.trim()
        if (!isValidYoutubeVideoId(cleanVid)) return@withContext null

        val fetched = runCatching {
            val payload = JSONObject().apply {
                put("videoId", cleanVid)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20250101.00.00")
                        put("hl", "tr")
                        put("gl", "TR")
                    })
                })
            }
            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/next?prettyPrint=false")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val raw = resp.body?.string().orEmpty()
                val rawUrl = ownerAvatarPattern.find(raw)?.groupValues?.get(1)
                    ?: fallbackAvatarPattern.find(raw)?.groupValues?.get(1)
                    ?: return@use null
                rawUrl
                    .replace("\\u0026", "&")
                    .replace("=s48-", "=s176-")
                    .replace("=s68-", "=s176-")
                    .replace("=s88-", "=s176-")
            }
        }.getOrNull()

        if (!fetched.isNullOrBlank()) {
            dynamicAvatarCache[cleanVid] = fetched
        }
        fetched
    }
}
