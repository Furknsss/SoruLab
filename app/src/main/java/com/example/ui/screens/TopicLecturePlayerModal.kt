package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.ViewGroup
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.DynamicVideoTimestampGrounder
import com.example.data.ExamType
import com.example.data.GroundedTimestampResult
import com.example.data.GroundingSource
import com.example.data.LectureChapterSegment
import com.example.data.ResolvedLectureOption
import com.example.data.ResolvedLectureRecommendation
import com.example.data.TopicLectureResolver
import com.example.data.YksQuestion
import com.example.data.YouTubeChannelAvatarResolver
import com.example.data.formatLectureTimestamp
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
import kotlinx.coroutines.delay
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Akıllı Konu Anlatım Modalı & Yerleşik Native YouTube Oynatıcı (v1.5.1):
 * - Sorunun kazanımına (`OsymKazanim`) ve soru tipine (`OsymQuestionType`) göre en popüler YKS hocasını seçer.
 * - `com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.1` (`YouTubePlayerView`) ile yerleşik native
 *   oynatım, `LifecycleOwner` entegrasyonu, sıfır-reload `seekTo` dakika atlama, hız/sar/döngü kontrolleri
 *   ve otomatik embed hata kurtarma kartı sunar.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    var seekRequestNonce by remember(question.id) { mutableIntStateOf(0) }
    var manualChapterOverride by remember(question.id, recommendation.primaryVideo.videoId) {
        mutableStateOf(false)
    }
    var groundedResult by remember(
        question.id,
        recommendation.primaryVideo.videoId,
        recommendation.primaryChapter.id
    ) {
        mutableStateOf<GroundedTimestampResult?>(null)
    }
    var isGroundingTimestamp by remember(
        question.id,
        recommendation.primaryVideo.videoId,
        recommendation.primaryChapter.id
    ) {
        mutableStateOf(false)
    }

    // Dinamik Transkript + Semantik Saniye Motoru:
    // Videonun genel konu başı yerine hocanın doğrudan bu sorunun alt kavramını anlattığı saniyeyi bulur.
    LaunchedEffect(
        question.id,
        recommendation.primaryVideo.videoId,
        recommendation.primaryChapter.id,
        manualChapterOverride
    ) {
        if (manualChapterOverride || !recommendation.hasDirectEmbedVideo) {
            isGroundingTimestamp = false
            return@LaunchedEffect
        }
        isGroundingTimestamp = true
        val result = DynamicVideoTimestampGrounder.groundTimestampForQuestion(
            question = question,
            video = recommendation.primaryVideo,
            chapter = recommendation.primaryChapter
        )
        groundedResult = result
        isGroundingTimestamp = false
        if (result.source != GroundingSource.CATALOG_FALLBACK &&
            result.startSeconds != recommendation.primaryChapter.startSeconds
        ) {
            seekRequestNonce += 1
        }
    }

    // Bölüm döngüsü için etkin bitiş saniyesi (tanımlı endSeconds yoksa sonraki bölümün başlangıcı veya +180 sn)
    val effectiveLoopEndSeconds = remember(recommendation.primaryChapter, recommendation.relatedChapters) {
        val start = recommendation.primaryChapter.startSeconds.coerceAtLeast(0)
        val explicitEnd = recommendation.primaryChapter.endSeconds
        when {
            explicitEnd != null && explicitEnd > start -> explicitEnd
            else -> {
                val nextChapterStart = recommendation.relatedChapters
                    .map { it.startSeconds }
                    .filter { it > start }
                    .minOrNull()
                nextChapterStart ?: (start + 180)
            }
        }
    }

    val activeGrounded = groundedResult?.takeIf {
        !manualChapterOverride && it.source != GroundingSource.CATALOG_FALLBACK
    }
    val activeStartSeconds = activeGrounded?.startSeconds
        ?: recommendation.primaryChapter.startSeconds.coerceAtLeast(0)
    val activeLoopEndSeconds = activeGrounded?.endSeconds ?: effectiveLoopEndSeconds
    val activeFormattedTimestamp = formatLectureTimestamp(activeStartSeconds)
    val activeWatchUrl = recommendation.primaryVideo.watchUrlAt(
        activeStartSeconds,
        recommendation.primaryChapter.chapterTitle
    )

    val currentPrimaryOption = remember(recommendation.primaryVideo, recommendation.primaryChapter) {
        ResolvedLectureOption(
            video = recommendation.primaryVideo,
            chapter = recommendation.primaryChapter,
            matchScore = 9999,
            youtubeWatchUrlWithTimestamp = recommendation.youtubeWatchUrlWithTimestamp,
            youtubeEmbedUrlWithTimestamp = recommendation.youtubeEmbedUrlWithTimestamp,
            formattedTimestamp = recommendation.formattedTimestamp,
            hasDirectEmbedVideo = recommendation.hasDirectEmbedVideo
        )
    }

    val allTeacherOptions = remember(currentPrimaryOption, recommendation.alternativeTeachers) {
        val list = mutableListOf<ResolvedLectureOption>()
        list.add(currentPrimaryOption)
        recommendation.alternativeTeachers.forEach { alt ->
            if (list.none { it.video.teacherName == alt.video.teacherName }) {
                list.add(alt)
            }
        }
        list
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color(0xFFCBD5E1),
                height = 4.dp,
                width = 38.dp
            )
        },
        modifier = Modifier.testTag("topic_lecture_player_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Sade & Şık Üst Bar: Soru Numarası, Ders ve Kapat Butonu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Konu Anlatımı",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = SoruLabInkBlack
                    )
                    Text(
                        text = when {
                            isGroundingTimestamp ->
                                "${question.number}. Soru • Tam saniye aranıyor..."
                            activeGrounded != null ->
                                "${question.number}. Soru • $activeFormattedTimestamp (Soru noktası)"
                            else ->
                                "${question.number}. Soru • $subjectName • $topicName"
                        },
                        fontSize = 12.sp,
                        color = if (activeGrounded != null) SoruLabCobaltBlue else SoruLabSlateMuted,
                        fontWeight = if (activeGrounded != null) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = SoruLabSlateDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. Üstte Hoca Seçici: Yatay Kaydırılabilir PP + Kanal İsmi Kapsülleri (Fotoğraftaki Tasarım)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allTeacherOptions) { opt ->
                    val isSelected = opt.video.teacherName == recommendation.primaryVideo.teacherName
                    TeacherSelectionPill(
                        teacherName = opt.video.teacherName,
                        channelName = opt.video.channelName,
                        videoId = opt.video.videoId,
                        isSelected = isSelected,
                        onClick = {
                            if (!isSelected) {
                                manualChapterOverride = false
                                recommendation = recommendation.selectTeacher(opt)
                                seekRequestNonce += 1
                            }
                        }
                    )
                }
            }

            // 3. Altta Player: Yerleşik Native YouTube Player (16:9)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                NativeYouTubeLecturePlayer(
                    videoId = recommendation.primaryVideo.videoId.trim(),
                    startSeconds = activeStartSeconds,
                    loopEndSeconds = activeLoopEndSeconds,
                    seekRequestNonce = seekRequestNonce,
                    formattedTimestamp = activeFormattedTimestamp,
                    hasDirectEmbedVideo = recommendation.hasDirectEmbedVideo,
                    onOpenExternalYoutube = {
                        runCatching {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(activeWatchUrl)
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                )
            }

            // 4. İlgili Soru Tipi / Bölüm Atlama Çipleri (Varsa sade tek satır)
            if (recommendation.relatedChapters.size > 1) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recommendation.relatedChapters) { chapter ->
                        val isSelected = chapter.id == recommendation.primaryChapter.id
                        Surface(
                            onClick = {
                                if (!isSelected) {
                                    manualChapterOverride = true
                                    recommendation = recommendation.selectChapter(chapter)
                                    seekRequestNonce += 1
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) SoruLabSkyTint else Color(0xFFF8FAFC),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) SoruLabRoyalBlue.copy(alpha = 0.5f) else Color(0xFFE2E8F0)
                            )
                        ) {
                            Text(
                                text = "${chapter.formattedTimestamp} • ${chapter.chapterTitle}",
                                color = if (isSelected) SoruLabRoyalBlue else SoruLabSlateDark,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hoca Profil Fotoğrafı (PP) Avatarı:
 * YouTube kanalının gerçek HD profil fotoğrafını Coil ile yükler;
 * yüklenirken veya çevrimdışıyken renkli monogramı pürüzsüz yer tutucu olarak gösterir.
 */
@Composable
private fun TeacherAvatar(
    teacherName: String,
    videoId: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var avatarUrl by remember(teacherName, videoId) {
        mutableStateOf(YouTubeChannelAvatarResolver.instantAvatarUrl(teacherName, videoId))
    }

    LaunchedEffect(teacherName, videoId) {
        if (avatarUrl.isNullOrBlank()) {
            avatarUrl = YouTubeChannelAvatarResolver.resolveAvatarUrl(teacherName, videoId)
        }
    }

    val initials = remember(teacherName) {
        val parts = teacherName.trim().split(Regex("\\s+"))
        when {
            parts.size >= 2 -> "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
            parts.isNotEmpty() -> parts[0].take(2).uppercase()
            else -> "YC"
        }
    }
    val gradientColors = remember(teacherName) {
        when {
            teacherName.contains("Görkem", ignoreCase = true) -> listOf(Color(0xFFF97316), Color(0xFFEA580C))
            teacherName.contains("Eyüp", ignoreCase = true) -> listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
            teacherName.contains("Kenan", ignoreCase = true) -> listOf(Color(0xFF10B981), Color(0xFF047857))
            teacherName.contains("Özcan", ignoreCase = true) -> listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
            teacherName.contains("Mert", ignoreCase = true) -> listOf(Color(0xFFEF4444), Color(0xFFB91C1C))
            teacherName.contains("Rehber", ignoreCase = true) -> listOf(Color(0xFFF59E0B), Color(0xFFD97706))
            teacherName.contains("Dr.", ignoreCase = true) || teacherName.contains("Biyoloji", ignoreCase = true) ->
                listOf(Color(0xFF14B8A6), Color(0xFF0F766E))
            teacherName.contains("Rüştü", ignoreCase = true) -> listOf(Color(0xFFD97706), Color(0xFF92400E))
            teacherName.contains("Ferrum", ignoreCase = true) -> listOf(Color(0xFF64748B), Color(0xFF334155))
            teacherName.contains("Altuğ", ignoreCase = true) -> listOf(Color(0xFF6366F1), Color(0xFF4338CA))
            teacherName.contains("VIP", ignoreCase = true) -> listOf(Color(0xFF06B6D4), Color(0xFF0E7490))
            else -> listOf(Color(0xFF64748B), Color(0xFF475569))
        }
    }

    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(gradientColors))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) Color.White else Color(0x33CBD5E1),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 11.5.sp,
            letterSpacing = 0.5.sp
        )
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "$teacherName YouTube profil fotoğrafı",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
    }
}

/**
 * Fotoğraftaki Seçim Kapsülü:
 * Solunda hocanın gerçek YouTube PP'si, sağında hoca ve kanal ismi. Seçildiğinde koyu mavi kapsül ve beyaz yazı.
 */
@Composable
private fun TeacherSelectionPill(
    teacherName: String,
    channelName: String,
    videoId: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) Color(0xFF1E3A8A) else Color(0xFFF8FAFC)
    val borderColor = if (isSelected) Color(0xFF3B82F6) else Color(0xFFE2E8F0)
    val titleColor = if (isSelected) Color.White else SoruLabSlateDark
    val subtitleColor = if (isSelected) Color(0xFF93C5FD) else SoruLabSlateMuted

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = modifier.testTag("teacher_pill_$teacherName")
    ) {
        Row(
            modifier = Modifier.padding(start = 5.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TeacherAvatar(
                teacherName = teacherName,
                videoId = videoId,
                isSelected = isSelected
            )
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = teacherName,
                    color = titleColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = channelName,
                    color = subtitleColor,
                    fontWeight = FontWeight.Normal,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TeachingQuestionTypeBadge(
    questionTypeLabel: String,
    chapterTitle: String,
    formattedTimestamp: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SoruLabVioletLight,
        border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lecture_question_type_badge")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SoruLabViolet
            ) {
                Text(
                    text = "Bu Soru Tipi Öğretiliyor",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = questionTypeLabel,
                    color = SoruLabViolet,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$chapterTitle • $formattedTimestamp",
                    color = SoruLabSlateDark,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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

private data class PlaybackSpeedOption(
    val label: String,
    val rate: PlayerConstants.PlaybackRate
)

private val LECTURE_PLAYBACK_SPEEDS = listOf(
    PlaybackSpeedOption("1x", PlayerConstants.PlaybackRate.RATE_1),
    PlaybackSpeedOption("1.25x", PlayerConstants.PlaybackRate.RATE_1_25),
    PlaybackSpeedOption("1.5x", PlayerConstants.PlaybackRate.RATE_1_5),
    PlaybackSpeedOption("2x", PlayerConstants.PlaybackRate.RATE_2)
)

private fun sanitizeYoutubeVideoId(rawVideoId: String): String =
    rawVideoId.trim().filter { it.isLetterOrDigit() || it == '_' || it == '-' }

private fun describePlayerError(error: PlayerConstants.PlayerError): String = when (error) {
    PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER ->
        "Yayıncı gömülü oynatmayı kısıtlamış"
    PlayerConstants.PlayerError.VIDEO_NOT_FOUND ->
        "Video gömülü oynatıcıda bulunamadı"
    PlayerConstants.PlayerError.HTML_5_PLAYER ->
        "HTML5 oynatıcı kısıtı algılandı"
    PlayerConstants.PlayerError.INVALID_PARAMETER_IN_REQUEST ->
        "Video parametresi doğrulanamadı"
    else ->
        "Gömülü oynatma kısıtı algılandı (${error.name})"
}

@Composable
private fun NativeYouTubeLecturePlayer(
    videoId: String,
    startSeconds: Int,
    loopEndSeconds: Int,
    seekRequestNonce: Int,
    formattedTimestamp: String,
    hasDirectEmbedVideo: Boolean,
    onOpenExternalYoutube: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val safeVideoId = remember(videoId) { sanitizeYoutubeVideoId(videoId) }
    val safeStartSeconds = startSeconds.coerceAtLeast(0)
    val safeLoopEndSeconds = loopEndSeconds.coerceAtLeast(safeStartSeconds + 5)
    val canEmbedNatively = hasDirectEmbedVideo && safeVideoId.isNotBlank()

    var isPlayerLoading by remember { mutableStateOf(canEmbedNatively) }
    var embedError by remember { mutableStateOf<PlayerConstants.PlayerError?>(null) }
    var youTubePlayerRef by remember { mutableStateOf<YouTubePlayer?>(null) }
    var playerViewRef by remember { mutableStateOf<YouTubePlayerView?>(null) }
    var loadedVideoId by remember { mutableStateOf<String?>(null) }
    var loadedStartSeconds by remember { mutableIntStateOf(-1) }
    var currentSecond by remember { mutableFloatStateOf(safeStartSeconds.toFloat()) }
    var selectedPlaybackRate by remember { mutableStateOf(PlayerConstants.PlaybackRate.RATE_1) }
    var isLoopEnabled by remember { mutableStateOf(false) }
    var lastLoopSeekElapsedMs by remember { mutableLongStateOf(0L) }

    val latestVideoId by rememberUpdatedState(safeVideoId)
    val latestStartSeconds by rememberUpdatedState(safeStartSeconds)
    val latestLoopEndSeconds by rememberUpdatedState(safeLoopEndSeconds)
    val latestIsLoopEnabled by rememberUpdatedState(isLoopEnabled)
    val latestPlaybackRate by rememberUpdatedState(selectedPlaybackRate)

    // LifecycleOwner ile güvenli bağlama
    DisposableEffect(lifecycleOwner, playerViewRef) {
        val view = playerViewRef
        if (view != null) {
            lifecycleOwner.lifecycle.addObserver(view)
        }
        onDispose {
            if (view != null) {
                lifecycleOwner.lifecycle.removeObserver(view)
            }
        }
    }

    // Modal kapandığında playerView.release() ile tam bellek temizliği
    DisposableEffect(Unit) {
        onDispose {
            playerViewRef?.release()
            playerViewRef = null
            youTubePlayerRef = null
        }
    }

    // Yükleme zaman aşımı koruması: 7 saniye içinde onReady gelmezse koyu perdeyi kaldır, oynatıcıyı serbest bırak
    LaunchedEffect(safeVideoId, isPlayerLoading) {
        if (isPlayerLoading) {
            delay(7000L)
            if (isPlayerLoading) {
                isPlayerLoading = false
            }
        }
    }

    // Sıfır-Reload anında saniye atlama (seekTo) & farklı hocaya geçişte loadVideo
    LaunchedEffect(safeVideoId, safeStartSeconds, seekRequestNonce, youTubePlayerRef, canEmbedNatively) {
        val player = youTubePlayerRef ?: return@LaunchedEffect
        if (!canEmbedNatively) return@LaunchedEffect

        when {
            loadedVideoId != safeVideoId -> {
                embedError = null
                loadedVideoId = safeVideoId
                loadedStartSeconds = safeStartSeconds
                currentSecond = safeStartSeconds.toFloat()
                player.loadVideo(safeVideoId, safeStartSeconds.toFloat())
                if (selectedPlaybackRate != PlayerConstants.PlaybackRate.RATE_1) {
                    player.setPlaybackRate(selectedPlaybackRate)
                }
            }
            loadedStartSeconds != safeStartSeconds || seekRequestNonce > 0 -> {
                loadedStartSeconds = safeStartSeconds
                currentSecond = safeStartSeconds.toFloat()
                if (embedError == null) {
                    player.seekTo(safeStartSeconds.toFloat())
                    player.play()
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 16:9 Yerleşik Native YouTubePlayerView Çerçevesi
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
                if (canEmbedNatively) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val options = IFramePlayerOptions.Builder()
                                .controls(1)
                                .rel(0)
                                .ivLoadPolicy(3)
                                .ccLoadPolicy(0)
                                .origin("https://www.youtube.com")
                                .build()

                            YouTubePlayerView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                enableAutomaticInitialization = false
                                initialize(
                                    object : AbstractYouTubePlayerListener() {
                                        override fun onReady(youTubePlayer: YouTubePlayer) {
                                            youTubePlayerRef = youTubePlayer
                                            isPlayerLoading = false
                                            embedError = null
                                            val targetVideoId = latestVideoId
                                            val targetStart = latestStartSeconds
                                            loadedVideoId = targetVideoId
                                            loadedStartSeconds = targetStart
                                            currentSecond = targetStart.toFloat()
                                            youTubePlayer.loadVideo(targetVideoId, targetStart.toFloat())
                                            if (latestPlaybackRate != PlayerConstants.PlaybackRate.RATE_1) {
                                                youTubePlayer.setPlaybackRate(latestPlaybackRate)
                                            }
                                        }

                                        override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                                            currentSecond = second
                                            if (latestIsLoopEnabled && second >= latestLoopEndSeconds.toFloat()) {
                                                val now = SystemClock.elapsedRealtime()
                                                if (now - lastLoopSeekElapsedMs > 1500L) {
                                                    lastLoopSeekElapsedMs = now
                                                    youTubePlayer.seekTo(latestStartSeconds.toFloat())
                                                }
                                            }
                                        }

                                        override fun onPlaybackRateChange(
                                            youTubePlayer: YouTubePlayer,
                                            playbackRate: PlayerConstants.PlaybackRate
                                        ) {
                                            if (LECTURE_PLAYBACK_SPEEDS.any { it.rate == playbackRate }) {
                                                selectedPlaybackRate = playbackRate
                                            }
                                        }

                                        override fun onError(
                                            youTubePlayer: YouTubePlayer,
                                            error: PlayerConstants.PlayerError
                                        ) {
                                            isPlayerLoading = false
                                            embedError = error
                                        }
                                    },
                                    options
                                )
                                playerViewRef = this
                            }
                        }
                    )
                }

                // Embed Hatası veya Doğrulanmamış Video Otomatik Kurtarma Kartı
                val activeError = embedError
                if (!canEmbedNatively || activeError != null) {
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
                                text = "YouTube Uygulamasında Tam Saniyeden Aç ($formattedTimestamp)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (activeError != null) {
                                    "${describePlayerError(activeError)} • Video tam seçili saniyeden açılır"
                                } else {
                                    "Bu konu için doğrudan YouTube anlatımına geçin"
                                },
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.5.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
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
                                    text = "YouTube Uygulamasında Tam Saniyeden Aç ($formattedTimestamp)",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                } else if (isPlayerLoading) {
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
                                text = "Video $formattedTimestamp dakikasından hazırlanıyor...",
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Öğrenme Odaklı Player Altı Şık Kontrol Çubuğu (Hız, -10sn/+10sn, Soru Tipi Döngü)
        if (canEmbedNatively && embedError == null) {
            LearningPlayerControlBar(
                currentSecond = currentSecond.toInt(),
                startSeconds = safeStartSeconds,
                loopEndSeconds = safeLoopEndSeconds,
                selectedRate = selectedPlaybackRate,
                isLoopEnabled = isLoopEnabled,
                onRewind10 = {
                    val target = (currentSecond - 10f).coerceAtLeast(0f)
                    currentSecond = target
                    youTubePlayerRef?.seekTo(target)
                },
                onForward10 = {
                    val target = currentSecond + 10f
                    currentSecond = target
                    youTubePlayerRef?.seekTo(target)
                },
                onSelectRate = { rate ->
                    selectedPlaybackRate = rate
                    youTubePlayerRef?.setPlaybackRate(rate)
                },
                onToggleLoop = {
                    isLoopEnabled = !isLoopEnabled
                    if (isLoopEnabled && currentSecond >= safeLoopEndSeconds.toFloat()) {
                        youTubePlayerRef?.seekTo(safeStartSeconds.toFloat())
                    }
                }
            )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LearningPlayerControlBar(
    currentSecond: Int,
    startSeconds: Int,
    loopEndSeconds: Int,
    selectedRate: PlayerConstants.PlaybackRate,
    isLoopEnabled: Boolean,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onSelectRate: (PlayerConstants.PlaybackRate) -> Unit,
    onToggleLoop: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, SoruLabBorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lecture_learning_controls_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // Üst Satır: -10 sn / +10 sn + Oynatma Hızı (1x, 1.25x, 1.5x, 2x)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // -10 sn Geri Sar
                Surface(
                    onClick = onRewind10,
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, SoruLabBorderLight)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "10 saniye geri sar",
                            tint = SoruLabRoyalBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "-10 sn",
                            color = SoruLabInkBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }
                }

                // +10 sn İleri Sar
                Surface(
                    onClick = onForward10,
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, SoruLabBorderLight)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "10 saniye ileri sar",
                            tint = SoruLabRoyalBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "+10 sn",
                            color = SoruLabInkBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }
                }

                // Hız Seçici Pill'leri
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, SoruLabBorderLight)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Oynatma hızı",
                            tint = SoruLabSlateMuted,
                            modifier = Modifier
                                .padding(start = 3.dp, end = 2.dp)
                                .size(15.dp)
                        )
                        LECTURE_PLAYBACK_SPEEDS.forEach { option ->
                            val isActive = selectedRate == option.rate
                            Surface(
                                onClick = { onSelectRate(option.rate) },
                                shape = RoundedCornerShape(7.dp),
                                color = if (isActive) SoruLabCobaltBlue else Color.Transparent
                            ) {
                                Text(
                                    text = option.label,
                                    color = if (isActive) Color.White else SoruLabSlateDark,
                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Alt Satır: "Bu Soru Tipini Tekrarla (Döngü)" Butonu + Anlık Zaman Göstergesi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onToggleLoop,
                    shape = RoundedCornerShape(10.dp),
                    color = if (isLoopEnabled) SoruLabEmeraldLight else Color.White,
                    border = BorderStroke(
                        width = if (isLoopEnabled) 1.5.dp else 1.dp,
                        color = if (isLoopEnabled) SoruLabEmerald else SoruLabBorderLight
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lecture_loop_toggle_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Bu soru tipini tekrarla",
                            tint = if (isLoopEnabled) Color(0xFF047857) else SoruLabViolet,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isLoopEnabled) {
                                "Döngü Aktif (${formatLectureTimestamp(startSeconds)} – ${formatLectureTimestamp(loopEndSeconds)})"
                            } else {
                                "Bu Soru Tipini Tekrarla (Döngü)"
                            },
                            color = if (isLoopEnabled) Color(0xFF047857) else SoruLabSlateDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SoruLabSkyTint
                ) {
                    Text(
                        text = formatLectureTimestamp(currentSecond),
                        color = SoruLabRoyalBlue,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
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
