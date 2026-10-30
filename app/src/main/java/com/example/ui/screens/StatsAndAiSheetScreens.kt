package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TestResultRecord
import com.example.data.YksQuestion
import com.example.ui.theme.*
import com.example.viewmodel.AiChatMessage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun IstatistiklerimScreen(
    testResults: List<TestResultRecord>,
    customQuestionsCount: Int,
    onInspectResult: (TestResultRecord) -> Unit
) {
    // Strict 0-Hallucination / 0-Lie policy: 100% real user metrics, zero fake numbers
    val totalTests = testResults.size
    val totalCorrect = testResults.sumOf { it.correctCount }
    val totalWrong = testResults.sumOf { it.wrongCount }
    val totalEmpty = testResults.sumOf { it.emptyCount }
    val totalUngraded = testResults.sumOf { it.ungradedCount }
    val totalQuestions = totalCorrect + totalWrong + totalEmpty + totalUngraded
    val totalGradedQuestions = totalCorrect + totalWrong + totalEmpty
    val totalNet = totalCorrect - (totalWrong * 0.25)
    val accuracyRate = if (totalGradedQuestions > 0) {
        ((totalCorrect.toFloat() / totalGradedQuestions.toFloat()) * 100f).toInt().coerceIn(0, 100)
    } else {
        0
    }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.forLanguageTag("tr-TR")) }

    val subjectStats = remember(testResults) {
        val defs = listOf(
            Triple("Matematik (TYT & AYT)", listOf("tyt_mat", "ayt_mat"), SoruLabCobaltBlue),
            Triple("Türkçe & Edebiyat", listOf("tyt_turkce", "ayt_edebiyat"), SoruLabCoralRed),
            Triple("Geometri (TYT & AYT)", listOf("tyt_geo", "ayt_geo"), SoruLabEmerald),
            Triple("Fizik (TYT & AYT)", listOf("tyt_fizik", "ayt_fizik"), SoruLabAmber),
            Triple("Kimya & Biyoloji", listOf("tyt_kimya", "ayt_kimya", "tyt_biyoloji", "ayt_biyoloji"), SoruLabViolet)
        )
        defs.map { (title, ids, color) ->
            val matching = testResults.filter { it.subjectId in ids }
            val c = matching.sumOf { it.correctCount }
            val q = matching.sumOf { it.correctCount + it.wrongCount + it.emptyCount }
            val ratio = if (q > 0) (c.toFloat() / q.toFloat()).coerceIn(0f, 1f) else 0f
            SubjectRealStat(
                title = title,
                solvedTestsCount = matching.size,
                ratio = ratio,
                color = color
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SoruLabBackground)
            .testTag("istatistiklerim_list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column {
                Text(
                    text = "İstatistiklerim & Başarı Durumu",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = SoruLabInkBlack
                )
                Text(
                    text = "Gerçek çözülen testlerine dayalı net gelişimin ve konu hakimiyetin",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoruLabSlateMuted
                )
            }
        }

        // Top KPI Summary Hero Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = SoruLabCobaltBlue.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SoruLabSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SoruLabBorderLight, RoundedCornerShape(24.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gerçek YKS Performans Özeti",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = SoruLabInkBlack
                            )
                            Text(
                                text = "$totalTests Çözülen Test • $totalQuestions Soru • $customQuestionsCount Havuz Sorusu",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SoruLabSlateMuted
                            )
                        }
                        Surface(
                            color = if (totalTests > 0) SoruLabEmeraldLight else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "%$accuracyRate İsabet",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (totalTests > 0) Color(0xFF065F46) else SoruLabSlateMuted,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatMetricBox(value = "$totalCorrect", label = "Doğru", color = SoruLabEmerald)
                        StatMetricBox(value = "$totalWrong", label = "Yanlış", color = SoruLabCoralRed)
                        StatMetricBox(value = "$totalEmpty", label = "Boş", color = SoruLabSlateMuted)
                        StatMetricBox(
                            value = String.format(Locale.US, "%.2f", totalNet),
                            label = "Toplam Net",
                            color = SoruLabCobaltBlue
                        )
                    }
                }
            }
        }

        // Real Subject Mastery Bars
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SoruLabSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SoruLabBorderLight, RoundedCornerShape(20.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Branş Bazlı Gerçek Doğruluk Analizi",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = SoruLabInkBlack
                    )
                    subjectStats.forEach { stat ->
                        SubjectProgressRow(
                            title = stat.title,
                            solvedTestsCount = stat.solvedTestsCount,
                            ratio = stat.ratio,
                            color = stat.color
                        )
                    }
                }
            }
        }

        // Solved Tests History
        item {
            Text(
                text = "Çözülen Testler & Deneme Geçmişi",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = SoruLabInkBlack
            )
        }

        if (testResults.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SoruLabSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SoruLabBorderLight, RoundedCornerShape(20.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Analytics,
                            contentDescription = null,
                            tint = SoruLabCobaltBlue,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Henüz Tamamlanmış Test Bulunmuyor",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = SoruLabInkBlack
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Soru Pratiği veya Denemeler bölümünden çözdüğün tüm gerçek testler ve optik form sonuçları anında burada listelenir.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoruLabSlateMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(testResults, key = { it.id }) { res ->
                val dateStr = remember(res) {
                    val ms = res.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    dateFormat.format(Date(ms))
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onInspectResult(res) }
                        .testTag("stats_result_card_${res.id}"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SoruLabSurface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SoruLabBorderLight, RoundedCornerShape(18.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${res.examType} • ${res.subjectName}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = SoruLabCobaltBlue
                            )
                            Text(
                                text = res.testTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = SoruLabInkBlack
                            )
                            Text(
                                text = "$dateStr • ${res.correctCount}D ${res.wrongCount}Y ${res.emptyCount}B ${res.ungradedCount}P (${String.format(Locale.US, "%.2f", res.netScore)} Net)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SoruLabSlateMuted
                            )
                        }
                        OutlinedButton(
                            onClick = { onInspectResult(res) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Sonucu İncele", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

private data class SubjectRealStat(
    val title: String,
    val solvedTestsCount: Int,
    val ratio: Float,
    val color: Color
)

@Composable
private fun StatMetricBox(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f))
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = SoruLabInkBlack
        )
    }
}

@Composable
private fun SubjectProgressRow(
    title: String,
    solvedTestsCount: Int,
    ratio: Float,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = SoruLabInkBlack
            )
            Text(
                text = if (solvedTestsCount > 0) "$solvedTestsCount Test • %${(ratio * 100).toInt()}" else "Henüz Çözülmedi (%0)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (solvedTestsCount > 0) color else SoruLabSlateMuted
            )
        }
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50)),
            color = color,
            trackColor = SoruLabSurfaceVariant
        )
    }
}

/**
 * Animated Floating Assistant Chat Window ("SoruLab AI Asistan")
 * Opens with a dynamic spring pop-up & slide animation when clicking the AI button.
 */
@Composable
fun SoruLabAiAssistantDialog(
    visible: Boolean = true,
    focusedQuestion: YksQuestion?,
    messages: List<AiChatMessage>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onAskQuestionSolution: (YksQuestion, String) -> Unit,
    onSendFreeMessage: (String) -> Unit,
    onClearHistory: () -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Trigger entry animation smoothly on mount
    var animateIn by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            animateIn = true
        } else {
            animateIn = false
        }
    }

    LaunchedEffect(focusedQuestion, visible) {
        if (visible && focusedQuestion != null) {
            onAskQuestionSolution(focusedQuestion, "")
        }
    }

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            delay(60L)
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val backdropAlpha by animateFloatAsState(
        targetValue = if (animateIn && visible) 0.48f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ai_chat_backdrop_alpha"
    )

    if (!visible && !animateIn) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backdropAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .padding(horizontal = 14.dp, vertical = 18.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = animateIn && visible,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight / 2 },
                animationSpec = spring(
                    dampingRatio = 0.76f,
                    stiffness = 320f
                )
            ) + scaleIn(
                initialScale = 0.86f,
                transformOrigin = TransformOrigin(0.85f, 0.95f),
                animationSpec = spring(
                    dampingRatio = 0.76f,
                    stiffness = 320f
                )
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight / 2 }
            ) + scaleOut(targetScale = 0.88f) + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.84f)
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(28.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* consume click inside chat window */ }
                    .testTag("ai_assistant_dialog"),
                color = SoruLabSurface,
                shadowElevation = 24.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Animated Assistant Window Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF0B132B),
                                        Color(0xFF1B3A9E),
                                        Color(0xFF2563EB)
                                    )
                                )
                            )
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.16f))
                                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "SoruLab AI Asistan",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 17.sp
                                            ),
                                            color = Color.White
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF4ADE80))
                                        )
                                    }
                                    Text(
                                        text = if (focusedQuestion != null) {
                                            "${focusedQuestion.topicHeader} • Soru ${focusedQuestion.number} İnceleniyor"
                                        } else {
                                            "Canlı YKS Soru Çözüm & Akademik Sohbet Penceresi"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.82f)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onClearHistory,
                                    modifier = Modifier.testTag("clear_ai_chat_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteSweep,
                                        contentDescription = "Sohbeti Temizle",
                                        tint = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.testTag("close_ai_dialog_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Kapat",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Horizontal Scrollable Quick Assistant Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                if (focusedQuestion != null) {
                                    onAskQuestionSolution(focusedQuestion, "Bu soruyu en hızlı ÖSYM kısayoluyla nasıl çözerim?")
                                } else {
                                    onSendFreeMessage("TYT Matematik temel kavramlar ve tek-çift sayı sorularında en pratik ÖSYM kısayolları nelerdir?")
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Bolt,
                                    contentDescription = null,
                                    tint = SoruLabCobaltBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("ÖSYM Kısayolu") }
                        )
                        AssistChip(
                            onClick = {
                                if (focusedQuestion != null) {
                                    onAskQuestionSolution(focusedQuestion, "Öncülleri tek tek inceleyip neden doğru veya yanlış olduğunu açıklar mısın?")
                                } else {
                                    onSendFreeMessage("AYT Fonksiyonlar ve Polinomlar konularında sınavda en çok çıkan soru kalıpları hangileridir?")
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Checklist,
                                    contentDescription = null,
                                    tint = SoruLabViolet,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Adım Adım Öncül Analizi") }
                        )
                        AssistChip(
                            onClick = {
                                onSendFreeMessage("Bugün 3 saatlik verimli bir TYT-AYT soru çözüm rutini nasıl planlamalıyım?")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = SoruLabEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Çalışma Planı Öner") }
                        )
                    }

                    HorizontalDivider(color = SoruLabBorderLight)

                    // Chat Conversation List
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFFF3F6FB))
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                if (!msg.isUser) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(SoruLabRoyalBlue, SoruLabCobaltBlue)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Surface(
                                    color = if (msg.isUser) SoruLabCobaltBlue else Color.White,
                                    shape = RoundedCornerShape(
                                        topStart = 20.dp,
                                        topEnd = 20.dp,
                                        bottomStart = if (msg.isUser) 20.dp else 4.dp,
                                        bottomEnd = if (msg.isUser) 4.dp else 20.dp
                                    ),
                                    shadowElevation = if (msg.isUser) 3.dp else 2.dp,
                                    modifier = Modifier
                                        .widthIn(max = 340.dp)
                                        .border(
                                            width = if (msg.isUser) 0.dp else 1.dp,
                                            color = if (msg.isUser) Color.Transparent else SoruLabBorderLight,
                                            shape = RoundedCornerShape(
                                                topStart = 20.dp,
                                                topEnd = 20.dp,
                                                bottomStart = if (msg.isUser) 20.dp else 4.dp,
                                                bottomEnd = if (msg.isUser) 4.dp else 20.dp
                                            )
                                        )
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                        if (msg.attachedQuestionTitle != null) {
                                            Surface(
                                                color = if (msg.isUser) {
                                                    Color.White.copy(alpha = 0.2f)
                                                } else {
                                                    SoruLabSkyTint
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = msg.attachedQuestionTitle,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = if (msg.isUser) Color.White else SoruLabRoyalBlue,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                lineHeight = 20.sp
                                            ),
                                            color = if (msg.isUser) Color.White else SoruLabInkBlack
                                        )
                                    }
                                }
                            }
                        }

                        if (isLoading) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .border(1.dp, SoruLabBorderLight, RoundedCornerShape(16.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = SoruLabCobaltBlue,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "SoruLab AI yanıt yazıyor...",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = SoruLabCobaltBlue
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Chat Input Bar
                    Surface(
                        color = Color.White,
                        shadowElevation = 10.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = {
                                    Text(
                                        "SoruLab AI'ya sorunuzu yazın...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SoruLabSlateMuted
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_chat_input"),
                                shape = RoundedCornerShape(24.dp),
                                maxLines = 3
                            )
                            FloatingActionButton(
                                onClick = {
                                    if (inputText.isNotBlank() && !isLoading) {
                                        onSendFreeMessage(inputText)
                                        inputText = ""
                                    }
                                },
                                containerColor = SoruLabCobaltBlue,
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("ai_chat_send_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Gönder",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
