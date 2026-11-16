package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.YksQuestion
import java.util.Locale

/**
 * A manually checked lesson reference. startSeconds is NEVER guessed:
 * 0 means "start of this lesson"; nonzero values must be sourced from
 * a public chapter list or independently reviewed timestamp.
 */
internal data class LessonVideo(
    val teacher: String,
    val title: String,
    val videoId: String,
    val startSeconds: Int,
    val detail: String,
    val sourceOfTimestamp: String
)

/**
 * Conservative initial catalog. More topics require real video validation
 * before they can be opened automatically. Never insert fabricated video IDs.
 */
internal object SmartLessonCatalog {
    private fun normalized(value: String): String =
        value.lowercase(Locale.forLanguageTag("tr-TR"))
            .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u')
            .replace('ş', 's').replace('ö', 'o').replace('ç', 'c')

    fun find(
        question: YksQuestion,
        examType: String,
        subjectName: String,
        fallbackTopic: String
    ): List<LessonVideo> {
        val exam = normalized(examType)
        val subject = normalized(subjectName.ifBlank { question.subject })
        val topic = normalized(question.topicHeader.ifBlank { fallbackTopic })
        val stem = normalized(question.stemTop + " " + question.stemBottom + " " +
            question.bulletsOrSteps.joinToString(" "))
        if (!exam.contains("ayt") || !subject.contains("matematik") || !topic.contains("turev")) {
            return emptyList()
        }

        // Only use a precise rule when the question's actual text identifies it.
        // Images without transcribed text fall back to a general topic lesson.
        val rehber = when {
            stem.contains("koklu fonksiyon") || stem.contains("koklu ifade") ->
                LessonVideo("Rehber Matematik", "Köklü ifadelerin türevi",
                    "WICamyMziqQ", 1773, "Köklü ifadeler",
                    "Yayıncının video açıklamasındaki 29:33 bölümü")
            stem.contains("rasyonel ifade") || stem.contains("bolumun turevi") ->
                LessonVideo("Rehber Matematik", "Rasyonel ifadelerin türevi",
                    "WICamyMziqQ", 1325, "Rasyonel ifadeler",
                    "Yayıncının video açıklamasındaki 22:05 bölümü")
            stem.contains("dereceli fonksiyon") || stem.contains("kuvvet fonksiyonu") ->
                LessonVideo("Rehber Matematik", "Dereceli fonksiyonların türevi",
                    "WICamyMziqQ", 771, "Kuvvet kuralı",
                    "Yayıncının video açıklamasındaki 12:51 bölümü")
            else ->
                LessonVideo("Rehber Matematik", "Türev Alma Kuralları 1",
                    "WICamyMziqQ", 0, "Genel konu anlatımı",
                    "Videonun başı; kesin alt kazanım dakikası bilinmiyor")
        }

        val candidates = mutableListOf<LessonVideo>()
        // A dedicated video, not an invented timestamp inside a longer video.
        if (stem.contains("zincir kural") || stem.contains("bileske fonksiyon")) {
            candidates += LessonVideo("Bıyıklı Matematik", "Zincir Kuralı ile Türev",
                "affPznU8gu0", 0, "Zincir kuralı",
                "Bu videonun tamamı zincir kuralına ayrılmıştır")
        }
        candidates += rehber
        candidates += LessonVideo("Eyüp B", "AYT Tekrar Türev-1",
            "4gpHnUOCWEQ", 0, "Genel tekrar",
            "Videonun başı; soru tipine özel dakika doğrulanmadı")
        return candidates
    }

    fun preferenceKey(examType: String, subjectName: String, topicName: String): String =
        normalized(examType + "|" + subjectName + "|" + topicName)
}

/**
 * Replaces the generic, sometimes misleading formula summary with a real
 * YouTube player when a verified lesson exists. No random video or start time.
 */
@Composable
internal fun SmartTopicLectureDialog(
    question: YksQuestion,
    examType: String,
    subjectName: String,
    fallbackTopic: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("sorulab_smart_lesson_teachers", Context.MODE_PRIVATE)
    }
    val topicName = question.topicHeader.ifBlank { fallbackTopic }
    val subjectKey = SmartLessonCatalog.preferenceKey(examType, subjectName, "")
    val topicKey = SmartLessonCatalog.preferenceKey(examType, subjectName, topicName)
    val lessonOptions = remember(question.id, examType, subjectName, topicName) {
        SmartLessonCatalog.find(question, examType, subjectName, fallbackTopic)
    }
    var teacher by remember(question.id) {
        mutableStateOf(
            prefs.getString("topic:" + topicKey, null)
                ?: prefs.getString("subject:" + subjectKey, null)
                ?: "Otomatik"
        )
    }
    val active = if (teacher == "Otomatik") lessonOptions.firstOrNull()
        else lessonOptions.firstOrNull { it.teacher == teacher }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 760.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Konu Anlatımı", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            text = examType + " " + subjectName + " • " + topicName,
                            fontSize = 12.sp, color = Color(0xFF64748B)
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Kapat") }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                if (active != null) {
                    Text(active.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    // key causes a new player to load when a different instructor is chosen.
                    key(active.videoId, active.startSeconds) {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    webChromeClient = WebChromeClient()
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                    val html = buildYoutubeEmbedHtml(
                                        active.videoId, active.startSeconds
                                    )
                                    // HTTPS base URL establishes a non-file origin and referrer.
                                    loadDataWithBaseURL(
                                        "https://github.com/Furknsss/SoruLab/",
                                        html, "text/html", "UTF-8", null
                                    )
                                }
                            },
                            onRelease = { it.stopLoading(); it.loadUrl("about:blank"); it.destroy() }
                        )
                    }
                    Text(
                        active.teacher + " • " + active.detail,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(active.sourceOfTimestamp, fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(
                        "Otomatik oynatma cihazda engellenirse oynatıcının ▶ düğmesine dokun.",
                        fontSize = 11.sp, color = Color(0xFF64748B)
                    )
                    TextButton(
                        onClick = {
                            val url = "https://www.youtube.com/watch?v=" + active.videoId +
                                "&t=" + active.startSeconds + "s"
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    ) { Text("Oynatıcı açılmazsa YouTube'da izle") }
                } else {
                    Text(
                        if (lessonOptions.isEmpty())
                            "Bu konu için doğrulanmış bir video henüz eklenmemiş."
                        else
                            teacher + " için bu konuda doğrulanmış bir anlatım yok. Başka hoca seç.",
                        color = Color(0xFF475569), fontSize = 13.sp
                    )
                    TextButton(
                        onClick = {
                            val query = listOf(examType, subjectName, topicName, "konu anlatımı")
                                .filter { it.isNotBlank() }.joinToString(" ")
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://www.youtube.com/results?search_query=" +
                                            Uri.encode(query)))
                                )
                            }
                        }
                    ) { Text("YouTube'da konu ara") }
                }

                if (lessonOptions.isNotEmpty()) {
                    Text("Hoca seç", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (listOf("Otomatik") + lessonOptions.map { it.teacher }.distinct()).forEach {
                            name ->
                            FilterChip(
                                selected = teacher == name,
                                onClick = { teacher = name },
                                label = { Text(name, maxLines = 1) }
                            )
                        }
                    }
                    if (teacher != "Otomatik" && active != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    prefs.edit().putString("subject:" + subjectKey, teacher).apply()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Ders için seç", fontSize = 11.sp) }
                            Button(
                                onClick = {
                                    prefs.edit().putString("topic:" + topicKey, teacher).apply()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2563EB)
                                )
                            ) { Text("Konu için seç", fontSize = 11.sp) }
                        }
                    }
                    if (prefs.contains("topic:" + topicKey) ||
                        prefs.contains("subject:" + subjectKey)) {
                        TextButton(onClick = {
                            prefs.edit().remove("topic:" + topicKey)
                                .remove("subject:" + subjectKey).apply()
                            teacher = "Otomatik"
                        }) { Text("Kaydedilmiş hoca tercihini sıfırla") }
                    }
                }
                Text(
                    "Video kapatıldığında testte aynı soruda kalırsın. Her konu için " +
                        "doğrulanmış video eşleştirmesi aşamalı olarak genişletilir.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

private fun buildYoutubeEmbedHtml(videoId: String, startSeconds: Int): String {
    require(Regex("[A-Za-z0-9_-]{11}").matches(videoId)) { "Geçersiz YouTube ID" }
    val seconds = startSeconds.coerceAtLeast(0)
    val url = "https://www.youtube.com/embed/" + videoId +
        "?playsinline=1&autoplay=1&start=" + seconds + "&rel=0"
    return """
        <!doctype html>
        <html lang="tr">
          <head>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="referrer" content="strict-origin-when-cross-origin">
            <style>
              html,body{margin:0;padding:0;background:#000;width:100%;height:100%;overflow:hidden}
              iframe{border:0;width:100%;height:100%}
            </style>
          </head>
          <body>
            <iframe src="$url" allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
              referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>
          </body>
        </html>
    """.trimIndent()
}
