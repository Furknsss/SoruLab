package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ExamType
import com.example.data.LectureChapterSegment
import com.example.data.ResolvedLectureOption
import com.example.data.ResolvedLectureRecommendation
import com.example.data.TopicLectureResolver
import com.example.data.YksQuestion
import com.example.ui.theme.SoruLabAmber
import com.example.ui.theme.SoruLabAmberLight
import com.example.ui.theme.SoruLabBorderLight
import com.example.ui.theme.SoruLabCobaltBlue
import com.example.ui.theme.SoruLabEmerald
import com.example.ui.theme.SoruLabEmeraldLight
import com.example.ui.theme.SoruLabInkBlack
import com.example.ui.theme.SoruLabRoyalBlue
import com.example.ui.theme.SoruLabSkyTint
import com.example.ui.theme.SoruLabSlateDark
import com.example.ui.theme.SoruLabSlateMuted
import com.example.ui.theme.SoruLabViolet
import com.example.ui.theme.SoruLabVioletLight

/**
 * Akıllı Konu Anlatım Modalı & Gömülü YouTube Oynatıcı:
 * - Sorunun kazanımına (`OsymKazanim`) ve soru tipine (`OsymQuestionType`) göre en popüler YKS hocasını seçer.
 * - Videoyu baştan değil, doğrudan ilgili alt başlığın/kazanımın saniyesinden (`startSeconds`) başlatır.
 * - İnteraktif bölüm (dakika) atlama çipleri, alternatif hoca değiştirici ve kazanım özeti + ÖSYM ipucu kartı sunar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicLecturePlayerModal(
    question: YksQuestion,
    examType: ExamType,
    subjectName: String,
    topicName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var recommendation by remember(question.id, examType, subjectName, topicName) {
        mutableStateOf(
            TopicLectureResolver.resolveForQuestion(
                question = question,
                examType = examType,
                subjectName = subjectName,
                topicName = topicName
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(min = 340.dp, max = 760.dp)
                .heightIn(max = 840.dp)
                .testTag("topic_lecture_player_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. HOCA & DAKİKA BAŞLIĞI
                LectureModalHeader(
                    recommendation = recommendation,
                    examType = examType,
                    subjectName = subjectName,
                    onDismiss = onDismiss
                )

                // Kazanım Rozeti + Soru Tipi Rozeti + Parlak "Direkt MM:SS'tan Başlıyor" Etiketi
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Parlak Dakika Başlangıç Etiketi
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = SoruLabEmerald,
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (recommendation.hasDirectEmbedVideo) {
                                    "Direkt ${recommendation.formattedTimestamp}'tan Başlıyor"
                                } else {
                                    "Hedef Bölüm: ${recommendation.formattedTimestamp}"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Kazanım Kodu Rozeti (örn. MAT.12.5.2.3)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = SoruLabSkyTint,
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = SoruLabRoyalBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = recommendation.matchedKazanim.code.ifBlank { topicName },
                                color = SoruLabRoyalBlue,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    // Soru Tipi Rozeti (örn. Grafik / Tablo Yorumu)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = SoruLabVioletLight,
                        border = BorderStroke(1.dp, Color(0xFFDDD6FE))
                    ) {
                        Text(
                            text = recommendation.detectedQuestionType.label,
                            color = SoruLabViolet,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        )
                    }
                }

                // 2. GÖMÜLÜ YOUTUBE OYNATICI (Resmi YouTube IFrame Player JS API) + TEK TIKLA YOUTUBE'DA AÇ BUTONU
                EmbeddedYouTubeLecturePlayer(
                    videoId = recommendation.primaryVideo.videoId.trim(),
                    startSeconds = recommendation.primaryChapter.startSeconds.coerceAtLeast(0),
                    embedUrl = recommendation.youtubeEmbedUrlWithTimestamp,
                    formattedTimestamp = recommendation.formattedTimestamp,
                    hasDirectEmbedVideo = recommendation.hasDirectEmbedVideo,
                    onOpenExternalYoutube = {
                        runCatching {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(recommendation.youtubeWatchUrlWithTimestamp)
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                )

                // 3. EŞLEŞME NEDENİ BANDI
                LectureMatchReasonBanner(recommendation = recommendation)

                // 4. İNTERAKTİF BÖLÜM (DAKİKA) ATLAMA ÇİPLERİ
                if (recommendation.relatedChapters.isNotEmpty()) {
                    LectureChapterChipsSection(
                        chapters = recommendation.relatedChapters,
                        activeChapterId = recommendation.primaryChapter.id,
                        onSelectChapter = { chapter ->
                            recommendation = recommendation.selectChapter(chapter)
                        }
                    )
                }

                // 5. ALTERNATİF HOCA DEĞİŞTİRİCİ
                if (recommendation.alternativeTeachers.isNotEmpty()) {
                    LectureTeacherSwitcherSection(
                        recommendation = recommendation,
                        onSelectTeacher = { option ->
                            recommendation = recommendation.selectTeacher(option)
                        }
                    )
                }

                // 6. KAZANIM ÖZETİ & ÖSYM İPUCU KARTI
                LectureKazanimSummaryAndTipCard(
                    recommendation = recommendation,
                    difficultyLabel = question.difficulty.label
                )

                HorizontalDivider(color = SoruLabBorderLight)

                // ALT KAPATMA SATIRI
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${question.number}. Soru • ${recommendation.primaryVideo.teacherName}",
                        color = SoruLabSlateMuted,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = SoruLabCobaltBlue)
                    ) {
                        Text(
                            text = "Soruya Dön",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LectureModalHeader(
    recommendation: ResolvedLectureRecommendation,
    examType: ExamType,
    subjectName: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFDC2626), Color(0xFFEF4444))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = recommendation.primaryVideo.channelName.ifBlank {
                            recommendation.primaryVideo.teacherName
                        },
                        color = SoruLabInkBlack,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${examType.name} $subjectName • ${recommendation.primaryVideo.teacherBadge}",
                    color = SoruLabCobaltBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = recommendation.primaryVideo.title,
                    color = SoruLabSlateMuted,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Kapat",
                tint = SoruLabSlateMuted
            )
        }
    }
}

private const val YOUTUBE_IFRAME_BASE_URL = "https://www.youtube.com"
private const val YOUTUBE_JS_BRIDGE_NAME = "SoruLabPlayerBridge"

private enum class PlayerRenderMode {
    IFRAME_API,
    SEARCH_FALLBACK
}

private data class PlayerWebViewState(
    val mode: PlayerRenderMode,
    val videoId: String,
    val startSeconds: Int,
    val fallbackUrl: String
)

private class YouTubePlayerJsBridge(
    private val mainHandler: Handler,
    private val isActive: () -> Boolean,
    private val onReady: () -> Unit,
    private val onError: (Int) -> Unit
) {
    @JavascriptInterface
    fun onPlayerReady() {
        mainHandler.post {
            if (isActive()) {
                onReady()
            }
        }
    }

    @JavascriptInterface
    fun onPlayerError(errorCode: Int) {
        mainHandler.post {
            if (isActive()) {
                onError(errorCode)
            }
        }
    }
}

private fun sanitizeVideoIdForJs(rawVideoId: String): String =
    rawVideoId.trim().filter { it.isLetterOrDigit() || it == '_' || it == '-' }

private fun buildYouTubeIFrameHtml(
    initialVideoId: String,
    initialStartSeconds: Int
): String {
    val safeVideoId = sanitizeVideoIdForJs(initialVideoId)
    val safeStart = initialStartSeconds.coerceAtLeast(0)
    return """
        <!DOCTYPE html>
        <html lang="tr">
        <head>
            <meta charset="UTF-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <style>
                html, body, #player {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    background-color: #0F172A;
                }
                iframe {
                    width: 100%;
                    height: 100%;
                    border: 0;
                }
            </style>
        </head>
        <body>
            <div id="player"></div>
            <script>
                var player = null;
                var isPlayerReady = false;
                var pendingVideoId = '$safeVideoId';
                var pendingStartSeconds = $safeStart;
                var hasPendingLoad = false;
                var hasPendingSeek = false;
                var readyNotified = false;

                function notifyReady() {
                    if (readyNotified) return;
                    readyNotified = true;
                    if (window.$YOUTUBE_JS_BRIDGE_NAME && window.$YOUTUBE_JS_BRIDGE_NAME.onPlayerReady) {
                        window.$YOUTUBE_JS_BRIDGE_NAME.onPlayerReady();
                    }
                }

                function notifyError(code) {
                    if (window.$YOUTUBE_JS_BRIDGE_NAME && window.$YOUTUBE_JS_BRIDGE_NAME.onPlayerError) {
                        window.$YOUTUBE_JS_BRIDGE_NAME.onPlayerError(code);
                    }
                }

                window.sorulabSeekTo = function(seconds) {
                    var target = Math.max(0, parseInt(seconds, 10) || 0);
                    pendingStartSeconds = target;
                    if (player && isPlayerReady && typeof player.seekTo === 'function') {
                        try {
                            player.seekTo(target, true);
                            if (typeof player.playVideo === 'function') {
                                player.playVideo();
                            }
                        } catch (e) {
                            hasPendingSeek = true;
                        }
                    } else {
                        hasPendingSeek = true;
                    }
                };

                window.sorulabLoadVideo = function(videoId, startSeconds) {
                    var target = Math.max(0, parseInt(startSeconds, 10) || 0);
                    pendingVideoId = String(videoId || '').trim();
                    pendingStartSeconds = target;
                    if (player && typeof player.loadVideoById === 'function') {
                        try {
                            player.loadVideoById({
                                videoId: pendingVideoId,
                                startSeconds: target
                            });
                            hasPendingLoad = false;
                            hasPendingSeek = false;
                        } catch (e) {
                            hasPendingLoad = true;
                        }
                    } else {
                        hasPendingLoad = true;
                    }
                };

                window.onYouTubeIframeAPIReady = function() {
                    try {
                        player = new YT.Player('player', {
                            width: '100%',
                            height: '100%',
                            videoId: pendingVideoId,
                            playerVars: {
                                autoplay: 1,
                                playsinline: 1,
                                rel: 0,
                                start: pendingStartSeconds,
                                origin: '$YOUTUBE_IFRAME_BASE_URL',
                                enablejsapi: 1,
                                modestbranding: 1,
                                fs: 1
                            },
                            events: {
                                onReady: function(event) {
                                    isPlayerReady = true;
                                    notifyReady();
                                    if (hasPendingLoad) {
                                        hasPendingLoad = false;
                                        hasPendingSeek = false;
                                        event.target.loadVideoById({
                                            videoId: pendingVideoId,
                                            startSeconds: pendingStartSeconds
                                        });
                                    } else if (hasPendingSeek) {
                                        hasPendingSeek = false;
                                        event.target.seekTo(pendingStartSeconds, true);
                                        event.target.playVideo();
                                    } else {
                                        event.target.playVideo();
                                    }
                                },
                                onError: function(event) {
                                    var code = (event && typeof event.data === 'number') ? event.data : 150;
                                    notifyReady();
                                    notifyError(code);
                                }
                            }
                        });
                    } catch (e) {
                        notifyReady();
                        notifyError(153);
                    }
                };

                // Yükleme göstergesinin sonsuza dek asılı kalmaması için güvenlik zamanlayıcısı
                setTimeout(function() {
                    if (!readyNotified) {
                        notifyReady();
                    }
                }, 8000);

                var apiScript = document.createElement('script');
                apiScript.src = 'https://www.youtube.com/iframe_api';
                apiScript.async = true;
                apiScript.onerror = function() {
                    notifyReady();
                    notifyError(153);
                };
                document.head.appendChild(apiScript);
            </script>
        </body>
        </html>
    """.trimIndent()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbeddedYouTubeLecturePlayer(
    videoId: String,
    startSeconds: Int,
    embedUrl: String,
    formattedTimestamp: String,
    hasDirectEmbedVideo: Boolean,
    onOpenExternalYoutube: () -> Unit
) {
    var isPageLoading by remember { mutableStateOf(true) }
    var embedErrorCode by remember { mutableStateOf<Int?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isActive by remember { mutableStateOf(true) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(Unit) {
        isActive = true
        onDispose {
            isActive = false
            mainHandler.removeCallbacksAndMessages(null)
            webViewRef?.apply {
                removeJavascriptInterface(YOUTUBE_JS_BRIDGE_NAME)
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webViewRef = null
        }
    }

    val safeVideoId = remember(videoId) { sanitizeVideoIdForJs(videoId) }
    val safeStartSeconds = startSeconds.coerceAtLeast(0)
    val desiredMode = if (hasDirectEmbedVideo && safeVideoId.isNotBlank()) {
        PlayerRenderMode.IFRAME_API
    } else {
        PlayerRenderMode.SEARCH_FALLBACK
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 16:9 Gömülü YouTube IFrame API WebView Çerçevesi
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0F172A),
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .testTag("lecture_embedded_player")
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val bridge = YouTubePlayerJsBridge(
                            mainHandler = mainHandler,
                            isActive = { isActive },
                            onReady = {
                                isPageLoading = false
                            },
                            onError = { code ->
                                isPageLoading = false
                                embedErrorCode = code
                            }
                        )
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.parseColor("#0F172A"))
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            addJavascriptInterface(bridge, YOUTUBE_JS_BRIDGE_NAME)
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    val state = view?.tag as? PlayerWebViewState
                                    if (state?.mode == PlayerRenderMode.SEARCH_FALLBACK) {
                                        isPageLoading = false
                                    }
                                }
                            }
                            if (desiredMode == PlayerRenderMode.IFRAME_API) {
                                tag = PlayerWebViewState(
                                    mode = PlayerRenderMode.IFRAME_API,
                                    videoId = safeVideoId,
                                    startSeconds = safeStartSeconds,
                                    fallbackUrl = embedUrl
                                )
                                loadDataWithBaseURL(
                                    YOUTUBE_IFRAME_BASE_URL,
                                    buildYouTubeIFrameHtml(safeVideoId, safeStartSeconds),
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            } else {
                                tag = PlayerWebViewState(
                                    mode = PlayerRenderMode.SEARCH_FALLBACK,
                                    videoId = "",
                                    startSeconds = safeStartSeconds,
                                    fallbackUrl = embedUrl
                                )
                                loadUrl(embedUrl, mapOf("Referer" to "$YOUTUBE_IFRAME_BASE_URL/"))
                            }
                            webViewRef = this
                        }
                    },
                    update = { webView ->
                        val previous = webView.tag as? PlayerWebViewState
                        val nextState = PlayerWebViewState(
                            mode = desiredMode,
                            videoId = if (desiredMode == PlayerRenderMode.IFRAME_API) safeVideoId else "",
                            startSeconds = safeStartSeconds,
                            fallbackUrl = embedUrl
                        )

                        if (previous == null || previous.mode != nextState.mode) {
                            embedErrorCode = null
                            isPageLoading = true
                            webView.tag = nextState
                            if (nextState.mode == PlayerRenderMode.IFRAME_API) {
                                webView.loadDataWithBaseURL(
                                    YOUTUBE_IFRAME_BASE_URL,
                                    buildYouTubeIFrameHtml(nextState.videoId, nextState.startSeconds),
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            } else {
                                webView.loadUrl(
                                    nextState.fallbackUrl,
                                    mapOf("Referer" to "$YOUTUBE_IFRAME_BASE_URL/")
                                )
                            }
                        } else if (nextState.mode == PlayerRenderMode.IFRAME_API) {
                            when {
                                // Farklı hoca / farklı video -> Sayfayı yenilemeden akıcı geçiş
                                previous.videoId != nextState.videoId -> {
                                    val needsFullReload = embedErrorCode == 153
                                    embedErrorCode = null
                                    webView.tag = nextState
                                    if (needsFullReload) {
                                        isPageLoading = true
                                        webView.loadDataWithBaseURL(
                                            YOUTUBE_IFRAME_BASE_URL,
                                            buildYouTubeIFrameHtml(nextState.videoId, nextState.startSeconds),
                                            "text/html",
                                            "UTF-8",
                                            null
                                        )
                                    } else {
                                        webView.evaluateJavascript(
                                            "window.sorulabLoadVideo('${nextState.videoId}', ${nextState.startSeconds});",
                                            null
                                        )
                                    }
                                }
                                // Aynı video, farklı bölüm çipi -> Sıfır-reload anında dakika atlama
                                previous.startSeconds != nextState.startSeconds -> {
                                    webView.tag = nextState
                                    if (embedErrorCode == null) {
                                        webView.evaluateJavascript(
                                            "window.sorulabSeekTo(${nextState.startSeconds});",
                                            null
                                        )
                                    }
                                }
                            }
                        } else if (previous.fallbackUrl != nextState.fallbackUrl) {
                            // Doğrulanmış videoId olmayan nadir arama fallback'i
                            embedErrorCode = null
                            isPageLoading = true
                            webView.tag = nextState
                            webView.loadUrl(
                                nextState.fallbackUrl,
                                mapOf("Referer" to "$YOUTUBE_IFRAME_BASE_URL/")
                            )
                        }
                    }
                )

                // Embed Hatası (100/101/150/153 vb.) Otomatik Kurtarma Kartı
                val activeErrorCode = embedErrorCode
                if (desiredMode == PlayerRenderMode.IFRAME_API && activeErrorCode != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("lecture_embed_error_card"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFDC2626).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircleOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFFCA5A5),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Bu video gömülü oynatmaya kapalı — YouTube'da Tam Dakikadan ($formattedTimestamp) Aç",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "YouTube embed kısıtı algılandı (Kod $activeErrorCode) • Video tam seçili saniyeden açılır",
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.5.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Button(
                                onClick = onOpenExternalYoutube,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircleOutline,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "YouTube'da Aç ($formattedTimestamp)",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                } else if (isPageLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xCC0F172A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = if (hasDirectEmbedVideo) {
                                    "Video $formattedTimestamp dakikasından hazırlanıyor..."
                                } else {
                                    "YouTube konu anlatımı yükleniyor..."
                                },
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Tek Tıkla YouTube Uygulamasında / Tarayıcıda Tam Dakikadan Aç Butonu
        Surface(
            onClick = onOpenExternalYoutube,
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFDC2626),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lecture_open_youtube_btn")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (hasDirectEmbedVideo) {
                        "YouTube'da Tam Dakikadan Aç ($formattedTimestamp)"
                    } else {
                        "YouTube'da Konu Anlatımını Aç"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun LectureMatchReasonBanner(
    recommendation: ResolvedLectureRecommendation
) {
    val bannerMessage = remember(
        recommendation.matchedKazanim.code,
        recommendation.matchedKazanim.title,
        recommendation.detectedQuestionType,
        recommendation.formattedTimestamp,
        recommendation.primaryChapter.chapterTitle,
        recommendation.matchReason
    ) {
        val kazanimLabel = recommendation.matchedKazanim.title.ifBlank {
            recommendation.matchedKazanim.code
        }
        "Bu soru $kazanimLabel & ${recommendation.detectedQuestionType.label} odaklıdır → " +
            "Videonun ${recommendation.formattedTimestamp} dakikasındaki " +
            "'${recommendation.primaryChapter.chapterTitle}' kısmından başlatıldı."
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFEFF6FF),
        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = SoruLabCobaltBlue.copy(alpha = 0.14f),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = SoruLabRoyalBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "Akıllı Dakika Eşleşmesi",
                    color = SoruLabRoyalBlue,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
                Text(
                    text = bannerMessage,
                    color = SoruLabSlateDark,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun LectureChapterChipsSection(
    chapters: List<LectureChapterSegment>,
    activeChapterId: String,
    onSelectChapter: (LectureChapterSegment) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bölüm & Dakika Atla",
                color = SoruLabInkBlack,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.5.sp
            )
            Text(
                text = "${chapters.size} alt bölüm",
                color = SoruLabSlateMuted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp
            )
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lecture_chapters_row"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(chapters, key = { it.id }) { chapter ->
                val isSelected = chapter.id == activeChapterId
                Surface(
                    onClick = { onSelectChapter(chapter) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) SoruLabCobaltBlue else Color(0xFFF8FAFC),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) SoruLabRoyalBlue else SoruLabBorderLight
                    ),
                    shadowElevation = if (isSelected) 2.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) Color.White.copy(alpha = 0.2f) else SoruLabSkyTint
                        ) {
                            Text(
                                text = chapter.formattedTimestamp,
                                color = if (isSelected) Color.White else SoruLabRoyalBlue,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = chapter.chapterTitle,
                            color = if (isSelected) Color.White else SoruLabSlateDark,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LectureTeacherSwitcherSection(
    recommendation: ResolvedLectureRecommendation,
    onSelectTeacher: (ResolvedLectureOption) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Popüler Hoca Değiştir",
            color = SoruLabInkBlack,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.5.sp
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lecture_teachers_row"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            // Aktif Hoca Kartı
            item(key = "active_${recommendation.primaryVideo.id}") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SoruLabEmeraldLight.copy(alpha = 0.65f),
                    border = BorderStroke(1.5.dp, SoruLabEmerald)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SoruLabEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = recommendation.primaryVideo.teacherName,
                                color = SoruLabInkBlack,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "${recommendation.formattedTimestamp} • Aktif",
                                color = Color(0xFF047857),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Alternatif Popüler Hocalar
            items(
                items = recommendation.alternativeTeachers,
                key = { "${it.video.id}_${it.chapter.id}" }
            ) { option ->
                Surface(
                    onClick = { onSelectTeacher(option) },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, SoruLabBorderLight)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SoruLabSkyTint,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = SoruLabRoyalBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = option.video.teacherName,
                                color = SoruLabInkBlack,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "${option.formattedTimestamp} • ${option.video.channelName}",
                                color = SoruLabSlateMuted,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LectureKazanimSummaryAndTipCard(
    recommendation: ResolvedLectureRecommendation,
    difficultyLabel: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Kazanım Özeti & Kritik Formül Kartı
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, SoruLabBorderLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = SoruLabRoyalBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Kazanım Özeti & Kritik Formül",
                        color = SoruLabRoyalBlue,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = recommendation.primaryChapter.keyFormulaSummary,
                    color = SoruLabSlateDark,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                if (recommendation.matchedKazanim.description.isNotBlank()) {
                    Text(
                        text = "${recommendation.matchedKazanim.code}: ${recommendation.matchedKazanim.description}",
                        color = SoruLabSlateMuted,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // 2. ÖSYM İpucu & Yaklaşım Notu Kartı
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SoruLabAmberLight.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, SoruLabAmber.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TipsAndUpdates,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "ÖSYM İpucu & Soru Yaklaşımı",
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.85f)
                    ) {
                        Text(
                            text = "$difficultyLabel Seviye",
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.5.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = recommendation.primaryChapter.osymTip,
                    color = Color(0xFF78350F),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
