package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedTextField
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
import com.example.data.LearningOutcomeCatalog
import android.webkit.ConsoleMessage
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.heightIn
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

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
    val scopeSubject = SmartLessonCatalog.subject(examType, subjectName.ifBlank { question.subject })
    val scopeTopic = scopeSubject?.let {
        com.example.data.SoruLabCatalog.resolveTopicInSubject(it.id, "", topicName)
    }
    val outcomeCandidates = if (scopeSubject != null && scopeTopic != null)
        LearningOutcomeCatalog.candidates(scopeSubject.id, scopeTopic.id) else emptyList()
    var selectedOutcome by rememberSaveable(question.id, topicKey) {
        mutableStateOf(prefs.getString("outcome:" + topicKey + ":" + question.id, null)
            ?: question.learningOutcomeId)
    }
    var showOutcomes by rememberSaveable(question.id) { mutableStateOf(false) }
    val selectedOutcomeName = outcomeCandidates.firstOrNull { it.id == selectedOutcome }?.name
        ?: question.learningOutcome
    val effectiveQuestion = if (selectedOutcome != question.learningOutcomeId)
        question.copy(learningOutcomeId = selectedOutcome, learningOutcomeName = selectedOutcomeName,
            learningOutcomeSource = "manual", learningOutcomeConfidence = 1.0) else question
    val lessonOptions = remember(effectiveQuestion, examType, subjectName, topicName) {
        SmartLessonCatalog.find(effectiveQuestion, examType, subjectName, fallbackTopic)
    }
    var teacher by rememberSaveable(question.id, subjectKey, topicKey) {
        mutableStateOf(
            prefs.getString("topic:" + topicKey, null)
                ?: prefs.getString("subject:" + subjectKey, null)
                ?: "Otomatik"
        )
    }
    var customTeacher by rememberSaveable(subjectKey, topicKey) { mutableStateOf("") }
    val active = SmartLessonCatalog.select(lessonOptions, teacher)
    var retry by remember { mutableStateOf(0) }
    var resumeSecond by rememberSaveable(question.id, active?.videoId, active?.startSeconds) {
        mutableStateOf(active?.startSeconds ?: 0)
    }
    var playerStatus by remember(active?.videoId, active?.startSeconds, retry) { mutableStateOf("loading") }
    var externalError by remember { mutableStateOf(false) }
    var savedPreference by remember { mutableStateOf(prefs.contains("topic:" + topicKey) ||
        prefs.contains("subject:" + subjectKey)) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                webView?.evaluateJavascript("if(window.player) player.pauseVideo();", null)
                webView?.onPause()
            } else if (event == Lifecycle.Event.ON_RESUME) webView?.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(active?.videoId, active?.startSeconds, retry) {
        delay(20000)
        if (playerStatus == "loading") playerStatus = "timeout"
    }
    fun openExternal(url: String) {
        externalError = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.isFailure
    }

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
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
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
                Text(if (selectedOutcomeName.isNotBlank()) "Alt kazanım: " + selectedOutcomeName
                    else "Alt kazanım kesinleşmedi; özel video dakikası seçilmez.", fontSize = 12.sp)
                if (outcomeCandidates.isNotEmpty()) {
                    TextButton(onClick = { showOutcomes = !showOutcomes }) { Text("Alt kazanımı seç / düzelt") }
                    if (showOutcomes) {
                        Column(modifier = Modifier.heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())) {
                            outcomeCandidates.forEach { outcome ->
                                TextButton(onClick = {
                                    selectedOutcome = outcome.id
                                    prefs.edit().putString("outcome:" + topicKey + ":" + question.id, outcome.id).apply()
                                    showOutcomes = false
                                }) { Text(outcome.name) }
                            }
                        }
                    }
                }

                if (active != null) {
                    if (teacher != "Otomatik" && teacher != active.teacher) {
                        Text("Tercih ettiğin hocanın bu kazanıma uygun bölümü yok; eşleşen anlatım seçildi.", fontSize = 12.sp)
                    }
                    Text(active.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    // key causes a new player to load when a different instructor is chosen.
                    key(active.videoId, active.startSeconds, retry) {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).heightIn(min = 200.dp),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    webView = this
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                            val value = message.message()
                                            if (value == "SORULAB_READY") playerStatus = "ready"
                                            if (value.startsWith("SORULAB_ERROR:")) playerStatus = value
                                            if (value == "SORULAB_BLOCKED") playerStatus = "blocked"
                                            if (value.startsWith("SORULAB_TIME:")) {
                                                val seconds = value.substringAfter(':').toDoubleOrNull()
                                                if (seconds != null && seconds.isFinite()) {
                                                    resumeSecond = seconds.toInt().coerceIn(active.startSeconds,
                                                        active.endSeconds?.minus(1) ?: 86400)
                                                }
                                            }
                                            return true
                                        }
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun onReceivedError(view: WebView, request: WebResourceRequest,
                                            error: WebResourceError) {
                                            if (request.isForMainFrame) playerStatus = "network"
                                        }
                                        override fun shouldOverrideUrlLoading(view: WebView,
                                            request: WebResourceRequest): Boolean {
                                            val uri = request.url
                                            return uri.scheme != "https" || uri.host !in setOf(
                                                "www.youtube.com", "www.youtube-nocookie.com",
                                                "github.com", "accounts.google.com")
                                        }
                                    }
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                    val html = buildYoutubeEmbedHtml(
                                        active.videoId, resumeSecond, active.endSeconds
                                    )
                                    // HTTPS base URL establishes a non-file origin and referrer.
                                    loadDataWithBaseURL(
                                        "https://github.com/Furknsss/SoruLab/",
                                        html, "text/html", "UTF-8", null
                                    )
                                }
                            },
                            onRelease = { if (webView === it) webView = null; it.onPause(); it.stopLoading(); it.loadUrl("about:blank"); it.destroy() }
                        )
                    }
                    if (playerStatus == "loading") Text("Oynatıcı yükleniyor…", fontSize = 12.sp)
                    if (playerStatus != "loading" && playerStatus != "ready" && playerStatus != "blocked") {
                        Text("Video yüklenemedi veya yerleştirmeye kapalı. Tekrar dene ya da YouTube'da aç.",
                            color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        TextButton(onClick = { retry++ }) { Text("Tekrar dene") }
                    }
                    Text(
                        active.teacher + " • " + active.detail,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(if (active.outcomeId.isBlank()) "Konu düzeyinde anlatım"
                        else "Alt kazanımla eşleşen bölüm; gerekirse alt kazanımı düzelt.",
                        fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(active.sourceOfTimestamp, fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(
                        "Otomatik oynatma engellenirse oynatıcının ▶ düğmesine dokun.",
                        fontSize = 11.sp, color = Color(0xFF64748B)
                    )
                    TextButton(
                        onClick = {
                            openExternal(active.watchUrl)
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
                            openExternal(SmartLessonCatalog.searchUrl(examType, subjectName,
                                (topicName + " " + selectedOutcomeName).trim(), teacher))
                        }
                    ) { Text("YouTube'da konu ara") }
                }

                run {
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
                    OutlinedTextField(
                        value = customTeacher,
                        onValueChange = { customTeacher = it.take(80) },
                        label = { Text("Arama için hoca adı (isteğe bağlı)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (customTeacher.isNotBlank()) {
                        TextButton(onClick = { teacher = customTeacher.trim(); customTeacher = "" }) {
                            Text("Bu hocayı kullan")
                        }
                    }
                    if (teacher != "Otomatik") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    prefs.edit().putString("subject:" + subjectKey, teacher).apply()
                                    savedPreference = true
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Ders için seç", fontSize = 11.sp) }
                            Button(
                                onClick = {
                                    prefs.edit().putString("topic:" + topicKey, teacher).apply()
                                    savedPreference = true
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2563EB)
                                )
                            ) { Text("Konu için seç", fontSize = 11.sp) }
                        }
                    }
                    if (savedPreference) {
                        TextButton(onClick = {
                            prefs.edit().remove("topic:" + topicKey)
                                .remove("subject:" + subjectKey).apply()
                            teacher = "Otomatik"
                            savedPreference = false
                        }) { Text("Kaydedilmiş hoca tercihini sıfırla") }
                    }
                }
                if (active != null) {
                    TextButton(onClick = {
                        openExternal(SmartLessonCatalog.searchUrl(examType, subjectName, (topicName + " " + selectedOutcomeName).trim(), teacher))
                    }) { Text("Başka anlatım ara") }
                }
                if (externalError) Text("Bağlantıyı açacak uygulama bulunamadı.",
                    color = MaterialTheme.colorScheme.error)
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

internal fun buildYoutubeEmbedHtml(videoId: String, startSeconds: Int, endSeconds: Int? = null): String {
    require(Regex("[A-Za-z0-9_-]{11}").matches(videoId))
    require(startSeconds >= 0 && (endSeconds == null || endSeconds > startSeconds))
    val end = endSeconds?.let { ",end:$it" }.orEmpty()
    return """
        <!doctype html><html lang="tr"><head>
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <meta name="referrer" content="strict-origin-when-cross-origin">
        <style>html,body,#player{margin:0;width:100%;height:100%;background:#000}</style>
        </head><body><div id="player"></div>
        <script src="https://www.youtube.com/iframe_api"></script>
        <script>
        var player;
        function onYouTubeIframeAPIReady(){
          player=new YT.Player('player',{
            width:'100%',height:'100%',videoId:'$videoId',
            playerVars:{playsinline:1,autoplay:1,start:$startSeconds$end,
              origin:'https://github.com',rel:0},
            events:{
              onReady:function(e){
                console.log('SORULAB_READY');e.target.playVideo();
                setInterval(function(){
                  if(player && player.getCurrentTime)
                    console.log('SORULAB_TIME:'+player.getCurrentTime());
                },3000);
              },
              onAutoplayBlocked:function(){console.log('SORULAB_BLOCKED');},
              onError:function(e){console.log('SORULAB_ERROR:'+e.data);}
            }
          });
        }
        </script></body></html>
    """.trimIndent()
}
