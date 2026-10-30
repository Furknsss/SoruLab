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
import com.example.data.ExamType
import com.example.data.SoruLabCatalog
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
    var selectedExam by remember { mutableStateOf(ExamType.TYT) }
    var selectedSubjectId by remember { mutableStateOf<String?>(null) }
    var expandedTopicId by remember(selectedExam, selectedSubjectId) { mutableStateOf<String?>(null) }
    val solvedResults = remember(testResults) { testResults.filter { it.isSolvedAttempt } }
    val examResults = remember(solvedResults, selectedExam) {
        solvedResults.filter { it.examType.equals(selectedExam.name, ignoreCase = true) }
    }
    val subjects = remember(selectedExam) {
        SoruLabCatalog.allSubjects.filter { it.examType == selectedExam }
    }
    val selectedSubject = subjects.firstOrNull { it.id == selectedSubjectId } ?: subjects.firstOrNull()
    val subjectStats = remember(examResults, subjects) {
        subjects.map { subject ->
            val attempts = examResults.filter { result ->
                result.subjectId == subject.id ||
                    (result.subjectId.isBlank() && result.subjectName.equals(subject.name, ignoreCase = true))
            }
            SubjectRealStat(
                subject = subject,
                solvedTestsCount = attempts.size,
                correctCount = attempts.sumOf { it.correctCount },
                wrongCount = attempts.sumOf { it.wrongCount },
                emptyCount = attempts.sumOf { it.emptyCount },
                ungradedCount = attempts.sumOf { it.ungradedCount }
            )
        }
    }
    val selectedSubjectStat = subjectStats.firstOrNull { it.subject.id == selectedSubject?.id }
    val topicStats = remember(examResults, selectedSubject?.id) {
        val topicResults = examResults.filter { result ->
            selectedSubject != null &&
                (result.subjectId == selectedSubject.id ||
                    (result.subjectId.isBlank() && result.subjectName.equals(selectedSubject.name, ignoreCase = true))) &&
                result.topicName.isNotBlank()
        }
        topicResults
            .groupBy { it.topicId.ifBlank { it.topicName } }
            .map { (topicId, attempts) ->
                TopicRealStat(
                    id = topicId,
                    topicName = attempts.first().topicName,
                    solvedTestsCount = attempts.size,
                    correctCount = attempts.sumOf { it.correctCount },
                    wrongCount = attempts.sumOf { it.wrongCount },
                    emptyCount = attempts.sumOf { it.emptyCount },
                    ungradedCount = attempts.sumOf { it.ungradedCount }
                )
            }
            .sortedWith(compareByDescending<TopicRealStat> { it.questionCount }.thenBy { it.topicName })
    }
    val totalCorrect = examResults.sumOf { it.correctCount }
    val totalWrong = examResults.sumOf { it.wrongCount }
    val totalEmpty = examResults.sumOf { it.emptyCount }
    val totalUngraded = examResults.sumOf { it.ungradedCount }
    val totalQuestions = totalCorrect + totalWrong + totalEmpty + totalUngraded
    val totalGradedQuestions = totalCorrect + totalWrong + totalEmpty
    val totalNet = totalCorrect - totalWrong * 0.25
    val totalAccuracy = accuracyRatio(totalCorrect, totalWrong, totalEmpty)
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.forLanguageTag("tr-TR")) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SoruLabBackground)
            .testTag("istatistiklerim_list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Genel İstatistik",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = SoruLabInkBlack
                )
                Text(
                    text = "Ders ve konu performansın, çözdüğün testlerden hesaplanır",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoruLabSlateMuted
                )
            }
        }

        item {
            ExamTypeSelector(
                selectedExam = selectedExam,
                onSelect = { exam ->
                    selectedExam = exam
                    selectedSubjectId = null
                }
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SoruLabSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SoruLabBorderLight, RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${selectedExam.name} GENEL",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = SoruLabCobaltBlue
                            )
                            Text(
                                text = "${examResults.size} test • $totalQuestions soru",
                                style = MaterialTheme.typography.bodySmall,
                                color = SoruLabSlateMuted
                            )
                            Text(
                                text = "$customQuestionsCount soru havuzda",
                                style = MaterialTheme.typography.labelSmall,
                                color = SoruLabSlateMuted
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (totalGradedQuestions > 0) "%$totalAccuracy" else "—",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = SoruLabCobaltBlue
                            )
                            Text("Doğruluk", style = MaterialTheme.typography.labelSmall, color = SoruLabSlateMuted)
                        }
                    }
                    HorizontalDivider(color = SoruLabBorderLight)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryValue("Doğru", "$totalCorrect", SoruLabEmerald)
                        SummaryValue("Yanlış", "$totalWrong", SoruLabCoralRed)
                        SummaryValue("Boş", "$totalEmpty", SoruLabSlateMuted)
                        SummaryValue(
                            "Net",
                            if (totalGradedQuestions > 0) {
                                String.format(Locale.forLanguageTag("tr-TR"), "%.2f", totalNet)
                            } else {
                                "—"
                            },
                            SoruLabCobaltBlue
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Ders Başarıları",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = SoruLabInkBlack
                        )
                        Text(
                            text = "Ders seçerek konu analizini aç",
                            style = MaterialTheme.typography.bodySmall,
                            color = SoruLabSlateMuted
                        )
                    }
                    Text(
                        text = "${subjects.size} ders",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = SoruLabSlateMuted
                    )
                }
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    subjectStats.forEach { stat ->
                        SubjectPerformanceCard(
                            stat = stat,
                            selected = stat.subject.id == selectedSubject?.id,
                            onClick = { selectedSubjectId = stat.subject.id }
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedSubject?.name ?: "Konu Analizi",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = SoruLabInkBlack
                        )
                        Text(
                            text = "${selectedSubjectStat?.solvedTestsCount ?: 0} test • ${selectedSubjectStat?.questionCount ?: 0} soru",
                            style = MaterialTheme.typography.bodySmall,
                            color = SoruLabSlateMuted
                        )
                    }
                    Text(
                        text = selectedSubjectStat?.accuracyPercent?.let { "%$it" } ?: "—",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = SoruLabCobaltBlue
                    )
                }

                if (selectedSubjectStat != null && selectedSubjectStat.solvedTestsCount > 0) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SoruLabSurface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SoruLabBorderLight, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SummaryValue("Doğru", "${selectedSubjectStat.correctCount}", SoruLabEmerald)
                            SummaryValue("Yanlış", "${selectedSubjectStat.wrongCount}", SoruLabCoralRed)
                            SummaryValue("Boş", "${selectedSubjectStat.emptyCount}", SoruLabSlateMuted)
                            SummaryValue(
                                "Net",
                                if (selectedSubjectStat.correctCount + selectedSubjectStat.wrongCount + selectedSubjectStat.emptyCount > 0) {
                                    String.format(Locale.forLanguageTag("tr-TR"), "%.2f", selectedSubjectStat.net)
                                } else {
                                    "—"
                                },
                                SoruLabCobaltBlue
                            )
                        }
                    }
                }

                Text(
                    text = "Konu Başarıları",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = SoruLabInkBlack
                )
                if (topicStats.isEmpty()) {
                    EmptyStatsMessage("Bu derste henüz tamamlanmış test bulunmuyor.")
                } else {
                    topicStats.forEach { stat ->
                        TopicAccordionCard(
                            stat = stat,
                            expanded = expandedTopicId == stat.id,
                            onClick = {
                                expandedTopicId = if (expandedTopicId == stat.id) null else stat.id
                            }
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "${selectedExam.name} Test Geçmişi",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = SoruLabInkBlack
            )
        }

        if (examResults.isEmpty()) {
            item {
                EmptyStatsMessage("${selectedExam.name} için henüz tamamlanmış test yok.")
            }
        } else {
            items(examResults, key = { "stats_result_card_${it.id}" }) { res ->
                val dateStr = res.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "Tarih bilgisi yok"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onInspectResult(res) }
                        .testTag("stats_result_card_${res.id}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SoruLabSurface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SoruLabBorderLight, RoundedCornerShape(14.dp))
                            .padding(14.dp),
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
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Sonucu incele",
                            tint = SoruLabSlateMuted
                        )
                    }
                }
            }
        }
    }
}

private data class SubjectRealStat(
    val subject: com.example.data.YksSubject,
    val solvedTestsCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val emptyCount: Int,
    val ungradedCount: Int
) {
    val questionCount: Int
        get() = correctCount + wrongCount + emptyCount + ungradedCount

    val net: Double
        get() = correctCount - wrongCount * 0.25

    val accuracyPercent: Int?
        get() = accuracyRatio(correctCount, wrongCount, emptyCount)
            .takeIf { correctCount + wrongCount + emptyCount > 0 }
}

private data class TopicRealStat(
    val id: String,
    val topicName: String,
    val solvedTestsCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val emptyCount: Int,
    val ungradedCount: Int
) {
    val questionCount: Int
        get() = correctCount + wrongCount + emptyCount + ungradedCount

    val gradedQuestionCount: Int
        get() = correctCount + wrongCount + emptyCount

    val net: Double
        get() = correctCount - wrongCount * 0.25

    val accuracyPercent: Int?
        get() = accuracyRatio(correctCount, wrongCount, emptyCount).takeIf { gradedQuestionCount > 0 }
}

private fun accuracyRatio(correct: Int, wrong: Int, empty: Int): Int {
    val total = correct + wrong + empty
    return if (total == 0) 0 else ((correct.toFloat() / total) * 100).toInt().coerceIn(0, 100)
}

@Composable
private fun ExamTypeSelector(selectedExam: ExamType, onSelect: (ExamType) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SoruLabSurfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ExamType.values().forEach { exam ->
            val selected = exam == selectedExam
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) SoruLabCobaltBlue else Color.Transparent)
                    .clickable { onSelect(exam) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = exam.name,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) Color.White else SoruLabSlateMuted
                )
            }
        }
    }
}

@Composable
private fun SubjectPerformanceCard(
    stat: SubjectRealStat,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = Color(stat.subject.accentColorHex)
    Card(
        modifier = Modifier
            .width(116.dp)
            .clickable(onClick = onClick)
            .testTag("stats_subject_${stat.subject.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFF8FAFF) else SoruLabSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(154.dp)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) SoruLabCobaltBlue else SoruLabBorderLight,
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 9.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stat.subject.name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = SoruLabInkBlack,
                maxLines = 1
            )
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(62.dp),
                    color = SoruLabSurfaceVariant,
                    strokeWidth = 6.dp
                )
                CircularProgressIndicator(
                    progress = { (stat.accuracyPercent ?: 0) / 100f },
                    modifier = Modifier.size(62.dp),
                    color = if (stat.accuracyPercent != null) accent else SoruLabBorderLight,
                    strokeWidth = 6.dp
                )
                Text(
                    text = stat.accuracyPercent?.let { "%$it" } ?: "—",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (stat.accuracyPercent != null) SoruLabInkBlack else SoruLabSlateMuted
                )
            }
            Text(
                text = if (stat.solvedTestsCount > 0) "${stat.solvedTestsCount} test" else "Veri yok",
                style = MaterialTheme.typography.labelSmall,
                color = SoruLabSlateMuted
            )
            Text(
                text = "Gözat",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) SoruLabCobaltBlue else SoruLabRoyalBlue.copy(alpha = 0.88f))
                    .padding(vertical = 6.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = SoruLabSlateMuted)
    }
}

@Composable
private fun EmptyStatsMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SoruLabSurface
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = SoruLabSlateMuted,
            modifier = Modifier
                .border(1.dp, SoruLabBorderLight, RoundedCornerShape(14.dp))
                .padding(16.dp)
        )
    }
}

@Composable
private fun TopicAccordionCard(
    stat: TopicRealStat,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SoruLabSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SoruLabBorderLight, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = stat.topicName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = SoruLabInkBlack
                    )
                    Text(
                        text = "${stat.solvedTestsCount} test • ${stat.questionCount} soru",
                        style = MaterialTheme.typography.labelSmall,
                        color = SoruLabSlateMuted
                    )
                }
                Text(
                    text = stat.accuracyPercent?.let { "%$it" } ?: "—",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = SoruLabCobaltBlue
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Konuyu daralt" else "Konu ayrıntısını aç",
                    tint = SoruLabSlateMuted,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (stat.accuracyPercent ?: 0) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = SoruLabCobaltBlue,
                trackColor = SoruLabSurfaceVariant
            )
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = SoruLabBorderLight)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryValue("Doğru", "${stat.correctCount}", SoruLabEmerald)
                        SummaryValue("Yanlış", "${stat.wrongCount}", SoruLabCoralRed)
                        SummaryValue("Boş", "${stat.emptyCount}", SoruLabSlateMuted)
                        SummaryValue(
                            "Net",
                            if (stat.gradedQuestionCount > 0) {
                                String.format(Locale.forLanguageTag("tr-TR"), "%.2f", stat.net)
                            } else {
                                "—"
                            },
                            SoruLabCobaltBlue
                        )
                    }
                }
            }
        }
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
