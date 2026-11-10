package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.data.YksQuestion
import com.example.util.StudioImageAndPdfEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Optional HTTPS bridge to owner-hosted Qwen + SymPy service.
 * No secrets in APK. Configure via SORULAB_TWIN_ENGINE_URL at build time.
 * Keep this endpoint on a private authenticated network/reverse proxy.
 *
 * When configured, a failed server verification never silently falls back
 * to a weaker/generated answer or an unrelated database question.
 */
internal object TwinRemoteService {
    val configured: Boolean get() = BuildConfig.TWIN_ENGINE_URL.isNotBlank()

    suspend fun generate(question: YksQuestion, context: Context): TwinQuestionModalData {
        return withContext(Dispatchers.IO) {
            try {
                val endpoint = BuildConfig.TWIN_ENGINE_URL
                check(endpoint.startsWith("https://"))
                val image = encodeSourceImage(question, context)
                val source = buildString {
                    appendLine("Ders: " + question.subject)
                    appendLine("Konu: " + question.topicHeader)
                    appendLine("Alt kazanım: " + question.learningOutcome)
                    appendLine("Soru tipi: " + question.questionType)
                    appendLine("Soru kökü: " + question.stemTop)
                    appendLine("Öncüller: " + question.bulletsOrSteps.joinToString(" | "))
                    appendLine("Yönerge: " + question.stemBottom)
                    appendLine("Metin: " + question.analyticsText.take(6500))
                    appendLine("Seçenekler: " + question.options.entries.joinToString(" | ") {
                        it.key + ") " + it.value
                    })
                }.take(14000)
                if (image.isBlank() && question.stemTop.length + question.stemBottom.length < 40) {
                    return@withContext error(question, "Soru metni ve kaynak resmi eksik.")
                }
                val payload = JSONObject()
                    .put("questionId", question.id.take(160))
                    .put("subject", question.subject.take(120))
                    .put("topic", question.topicHeader.take(200))
                    .put("difficulty", question.difficulty.label.take(100))
                    .put("sourceText", source)
                    .put("verifiedOriginalOption", question.correctOption.takeIf {
                        question.answerKeyVerified && it in listOf("A", "B", "C", "D", "E")
                    }.orEmpty())
                    .put("imageBase64", image)
                val conn = URL(endpoint.trimEnd('/') + "/api/twin").openConnection()
                    as HttpsURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 15000
                    conn.readTimeout = 170000
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.setRequestProperty("Accept", "application/json")
                    conn.outputStream.use { out ->
                        out.write(payload.toString().toByteArray(Charsets.UTF_8))
                    }
                    val responseCode = conn.responseCode
                    val responseText = (
                        if (responseCode in 200..299) conn.inputStream
                        else conn.errorStream
                    )?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        val detail = runCatching {
                            JSONObject(responseText).optString("detail")
                        }.getOrDefault("Görsel, soru veya cevap denetimi reddedildi.")
                        return@withContext error(question, detail.take(180))
                    }
                    val data = JSONObject(responseText)
                    require(data.optBoolean("accepted"))
                    val stem = data.getString("questionStem").trim()
                    val allOptions = data.getJSONObject("options")
                    val labels = listOf("A", "B", "C", "D", "E")
                    val options = labels.associateWith { allOptions.getString(it).trim() }
                    val correct = data.getString("correctOption").trim()
                    val solution = data.getString("solutionExplanation").trim()
                    val diagram = data.optString("diagramJson").trim()
                    require(stem.length in 35..3000 && solution.length >= 40)
                    require(correct in labels && options.values.all { it.isNotBlank() })
                    require(options.values.map { it.trim().lowercase() }.distinct().size == 5)
                    if (diagram.isNotBlank()) require(TwinDiagramSpec.parse(diagram) != null)
                    TwinQuestionModalData(
                        topicTitle = question.topicHeader,
                        difficultyLabel = question.difficulty.label,
                        questionStem = stem,
                        options = options,
                        correctOption = correct,
                        solutionExplanation = solution,
                        diagramJson = diagram,
                        sourceDescription = data.optString("sourceDescription")
                            .take(160).ifBlank { "Qwen + matematiksel denetim" }
                    )
                } finally {
                    conn.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error(question, "Yerel Qwen/SymPy servisine ulaşılamadı veya yanıt reddedildi: " +
                    (e.localizedMessage ?: "bağlantı hatası").take(120))
            }
        }
    }

    private suspend fun encodeSourceImage(question: YksQuestion, context: Context): String {
        val bitmap = StudioImageAndPdfEngine.loadGuaranteedQuestionBitmap(
            context = context,
            questionId = question.id,
            imagePath = question.imagePath,
            imageBase64 = question.imageBase64,
            pdfFilePath = question.pdfFilePath,
            pageIndex = question.pageIndex,
            normLeft = question.normLeft,
            normTop = question.normTop,
            normRight = question.normRight,
            normBottom = question.normBottom
        )?.copy(Bitmap.Config.ARGB_8888, false) ?: return ""
        try {
            val maxDimension = maxOf(bitmap.width, bitmap.height)
            val scaled = if (maxDimension > 1400) {
                val fraction = 1400.0 / maxDimension
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * fraction).toInt().coerceAtLeast(1),
                    (bitmap.height * fraction).toInt().coerceAtLeast(1),
                    true
                )
            } else bitmap
            return try {
                val bytes = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 78, bytes)
                Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun error(question: YksQuestion, reason: String) = TwinQuestionModalData(
        topicTitle = question.topicHeader,
        difficultyLabel = question.difficulty.label,
        errorMessage = "Güvenilir ikiz soru oluşturulamadı. " + reason
    )
}
