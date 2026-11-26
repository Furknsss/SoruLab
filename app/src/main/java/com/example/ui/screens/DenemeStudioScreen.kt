package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.CustomQuestionRecord
import com.example.data.ExamType
import com.example.data.MockExamItem
import com.example.data.OsymExamAuditEngine
import com.example.data.OsymExamAuditReport
import com.example.data.SoruLabCatalog
import com.example.data.TestResultRecord
import com.example.data.TestSnapshotCodec
import com.example.data.YksTestItem
import com.example.util.TestPageLayoutConfig
import com.example.viewmodel.DenemeBlueprints
import com.example.viewmodel.DenemeDifficulty
import com.example.viewmodel.DenemeKind
import com.example.viewmodel.DenemePlan
import com.example.viewmodel.DenemePlanner
import com.example.viewmodel.DenemeRequest
import com.example.viewmodel.DenemeTimerMode
import com.example.viewmodel.DenemeTopicProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

private val navy = Color(0xFF211C59)
private val cobalt = Color(0xFF356CF3)
private val pale = Color(0xFFF4F6FD)
private val ink = Color(0xFF25335A)
private val grey = Color(0xFF66748C)
private val success = Color(0xFF13914D)

private const val MAX_INLINE_EXAMS = 3
private const val MAX_INLINE_HISTORIES = 3

private sealed class DenemeCreationStage {
    data object Setup : DenemeCreationStage()
    data class Generating(
        val request: DenemeRequest,
        val plan: DenemePlan.Ready
    ) : DenemeCreationStage()

    data class Ready(
        val request: DenemeRequest,
        val plan: DenemePlan.Ready
    ) : DenemeCreationStage()
}

@Composable
fun DenemeStudioScreen(
    selectedFilter: ExamType?,
    mockExams: List<MockExamItem>,
    solvedRecords: List<TestResultRecord>,
    customQuestions: List<CustomQuestionRecord>,
    onSelectFilter: (ExamType?) -> Unit,
    onCreateDeneme: (DenemeRequest) -> Unit,
    onStartExam: (MockExamItem) -> Unit,
    onReviewExam: (TestResultRecord) -> Unit,
    onOpenPdfStudio: () -> Unit,
    modifier: Modifier = Modifier,
    onDeleteExam: (MockExamItem) -> Unit = {},
    onStartPreparedDeneme: ((DenemePlan.Ready) -> Unit)? = null
) {
    var isCreating by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<TestResultRecord?>(null) }
    var pendingDeleteExam by remember { mutableStateOf<MockExamItem?>(null) }
    var infoQuestionsTest by remember { mutableStateOf<YksTestItem?>(null) }
    var showAllExamsSheet by remember { mutableStateOf(false) }
    var showAllHistoriesSheet by remember { mutableStateOf(false) }
    var layoutConfig by remember { mutableStateOf(TestPageLayoutConfig()) }
    var showLayoutSheet by remember { mutableStateOf(false) }

    val histories = remember(solvedRecords) {
        solvedRecords.filter { it.isSolvedAttempt && it.testId.startsWith("deneme_v147_") }
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
    }
    val allExams = remember(mockExams) { mockExams.distinctBy { it.id }.sortedByDescending { it.id } }
    val listed = remember(selectedFilter, allExams) {
        allExams.filter { selectedFilter == null || it.examType == selectedFilter }
    }
    val inlineExams = remember(listed) { listed.take(MAX_INLINE_EXAMS) }
    val inlineHistories = remember(histories) { histories.take(MAX_INLINE_HISTORIES) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("denemeler_screen"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(25.dp),
                color = navy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("deneme_studio_hero")
            ) {
                Column(
                    Modifier
                        .background(Brush.linearGradient(listOf(navy, Color(0xFF3156BA))))
                        .padding(21.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "DENEME STÜDYOSU",
                        color = Color(0xFFB7D2FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "Gerçek sınav düzeni.\nSana özel denemeler.",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        lineHeight = 29.sp
                    )
                    Text(
                        "2018–2026 ÖSYM kazanımları • Kırpma kalite filtresi • Doğrulanmış sorular",
                        fontSize = 12.sp,
                        color = Color(0xFFE0E9FF)
                    )
                    Button(
                        onClick = { isCreating = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = navy),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("create_deneme_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Yeni Deneme Oluştur", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExamNumberCard("Oluşturulan", allExams.size.toString(), Modifier.weight(1f), cobalt)
                ExamNumberCard("Çözülen", histories.size.toString(), Modifier.weight(1f), success)
                val latest = histories.firstOrNull()?.netScore
                ExamNumberCard(
                    "Son net",
                    latest?.let { String.format(Locale.forLanguageTag("tr-TR"), "%.2f", it) } ?: "—",
                    Modifier.weight(1f),
                    Color(0xFFEA8D3A)
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Denemelerim", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = navy)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        onClick = { showLayoutSheet = true },
                        shape = RoundedCornerShape(10.dp),
                        color = pale,
                        modifier = Modifier.testTag("deneme_page_layout_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = "Sayfa Düzeni", tint = cobalt, modifier = Modifier.size(14.dp))
                            Text(
                                "${layoutConfig.columnMode.shortLabel} • %${layoutConfig.scaleConfig.clampedScalePercent}",
                                color = navy,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    TextButton(onClick = onOpenPdfStudio) {
                        Text("Soru kitaplığım", color = cobalt, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null, ExamType.TYT, ExamType.AYT).forEach { choice ->
                    ChoiceChip(
                        text = choice?.name ?: "Tümü",
                        selected = choice == selectedFilter,
                        onClick = { onSelectFilter(choice) }
                    )
                }
            }
        }
        if (listed.isEmpty()) {
            item {
                Surface(
                    color = pale,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("İlk denemeni oluşturmaya hazır mısın?", fontWeight = FontWeight.Bold, color = navy)
                        Text(
                            "Branş ya da konu seç, süreli ya da kronometre modunu belirle. Sadece temiz kırpılmış doğrulanmış sorulardan gerçek bir deneme hazırlanır.",
                            fontSize = 13.sp,
                            color = grey
                        )
                        Text(
                            "${customQuestions.size} kayıtlı soru • Kaynak eksikse sistem sana bildirecek",
                            fontSize = 11.sp,
                            color = cobalt
                        )
                    }
                }
            }
        } else {
            items(inlineExams, key = { it.id }) { exam ->
                val last = histories.firstOrNull { it.testId == exam.linkedTest.id }
                SavedDenemeCard(
                    exam = exam,
                    lastRecord = last,
                    tagPrefix = "deneme_",
                    onOpenInfo = { infoQuestionsTest = exam.linkedTest },
                    onOpenReport = { last?.let { selectedRecord = it } },
                    onStart = { onStartExam(exam) },
                    onDelete = { pendingDeleteExam = exam }
                )
            }
            if (listed.size > MAX_INLINE_EXAMS) {
                item(key = "show_all_denemeler_item") {
                    OutlinedButton(
                        onClick = { showAllExamsSheet = true },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFD7E1F5)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = pale, contentColor = navy),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("show_all_denemeler_btn")
                    ) {
                        Text(
                            "Tüm Denemelerim (${listed.size})",
                            color = cobalt,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = cobalt
                        )
                    }
                }
            }
        }
        if (histories.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Deneme Geçmişim", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = navy)
                    if (histories.size > MAX_INLINE_HISTORIES) {
                        Text(
                            "Son $MAX_INLINE_HISTORIES / ${histories.size}",
                            color = grey,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            items(inlineHistories, key = { it.id }) { record ->
                DenemeHistoryRow(
                    record = record,
                    tagPrefix = "deneme_result_",
                    onClick = { selectedRecord = record }
                )
            }
            if (histories.size > MAX_INLINE_HISTORIES) {
                item(key = "show_all_histories_item") {
                    OutlinedButton(
                        onClick = { showAllHistoriesSheet = true },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFD7E1F5)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = navy),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("show_all_histories_btn")
                    ) {
                        Text(
                            "Tüm Sonuç Geçmişi (${histories.size})",
                            color = cobalt,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = cobalt
                        )
                    }
                }
            }
        }
    }

    if (isCreating) {
        DenemeCreatorSheet(
            questions = customQuestions,
            solvedRecords = solvedRecords,
            layoutConfig = layoutConfig,
            onOpenLayoutSettings = { showLayoutSheet = true },
            onOpenQuestionsInfo = { test -> infoQuestionsTest = test },
            onDismiss = { isCreating = false },
            onStartPrepared = { request, readyPlan ->
                isCreating = false
                if (onStartPreparedDeneme != null) {
                    onStartPreparedDeneme(readyPlan)
                } else {
                    onCreateDeneme(request)
                }
            }
        )
    }

    if (showAllExamsSheet) {
        AllSavedExamsBottomSheet(
            exams = listed,
            histories = histories,
            selectedFilter = selectedFilter,
            onSelectFilter = onSelectFilter,
            onDismiss = { showAllExamsSheet = false },
            onOpenInfo = { exam -> infoQuestionsTest = exam.linkedTest },
            onOpenReport = { record ->
                showAllExamsSheet = false
                selectedRecord = record
            },
            onStartExam = { exam ->
                showAllExamsSheet = false
                onStartExam(exam)
            },
            onDeleteExam = { exam -> pendingDeleteExam = exam }
        )
    }

    if (showAllHistoriesSheet) {
        AllHistoriesBottomSheet(
            histories = histories,
            onDismiss = { showAllHistoriesSheet = false },
            onSelectRecord = { record ->
                showAllHistoriesSheet = false
                selectedRecord = record
            }
        )
    }

    if (showLayoutSheet) {
        TestPageLayoutSettingsBottomSheet(
            config = layoutConfig,
            onConfigChange = { layoutConfig = it },
            onDismiss = { showLayoutSheet = false }
        )
    }

    selectedRecord?.let { record ->
        DenemeResultSheet(
            record = record,
            onDismiss = { selectedRecord = null },
            onReview = {
                selectedRecord = null
                onReviewExam(record)
            }
        )
    }

    infoQuestionsTest?.let { test ->
        DenemeQuestionsInfoDialog(
            test = test,
            onDismiss = { infoQuestionsTest = null }
        )
    }

    pendingDeleteExam?.let { examToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteExam = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Denemeyi Sil", fontWeight = FontWeight.Bold, color = navy) },
            text = {
                Text(
                    "\"${examToDelete.title}\" denemesi ve bu denemeye ait sonuç kayıtları silinecek. Devam edilsin mi?",
                    color = grey
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteExam = null
                        onDeleteExam(examToDelete)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) { Text("Sil", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteExam = null }) { Text("Vazgeç", color = navy) }
            }
        )
    }
}

@Composable
private fun SavedDenemeCard(
    exam: MockExamItem,
    lastRecord: TestResultRecord?,
    tagPrefix: String,
    onOpenInfo: () -> Unit,
    onOpenReport: () -> Unit,
    onStart: () -> Unit,
    onDelete: () -> Unit
) {
    val createdAt = exam.id.substringAfterLast("_").toLongOrNull()?.takeIf { it > 1_000_000_000_000L }
    val audit = remember(exam.linkedTest) {
        if (exam.linkedTest.questions.isNotEmpty()) OsymExamAuditEngine.auditMockExam(exam) else null
    }
    val durationLabel = if (exam.durationMinutes <= 0) "00:00 Kronometre" else "${exam.durationMinutes} dk"

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(19.dp),
        border = BorderStroke(1.dp, Color(0xFFE1E7F2)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tagPrefix + "card_" + exam.id)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        ColorTag(exam.examType.name, cobalt)
                        ColorTag(exam.difficulty.label, Color(0xFF8A5CF6))
                        audit?.let { report ->
                            ColorTag(
                                "ÖSYM ${report.complianceScore}/100",
                                if (report.isOsymCompliant) success else Color(0xFFD97706)
                            )
                        }
                    }
                    Text(
                        exam.title,
                        color = navy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${exam.questionCount} soru • $durationLabel • ${exam.tag}",
                        fontSize = 11.sp,
                        color = grey
                    )
                    audit?.let { report ->
                        Text(
                            "${report.distinctKazanimCount} kazanım • ${report.distinctQuestionTypeCount}/6 ÖSYM soru tipi",
                            fontSize = 11.sp,
                            color = cobalt,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onOpenInfo,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(pale)
                            .testTag(tagPrefix + "info_" + exam.id)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Soru ve kazanım bilgisi",
                            tint = cobalt,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (lastRecord != null) {
                        Text(
                            String.format(Locale.forLanguageTag("tr-TR"), "%.2f net", lastRecord.netScore),
                            color = success,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFF0F2F7))
            createdAt?.let {
                Text(
                    java.text.SimpleDateFormat("d MMM yyyy • HH:mm", Locale.forLanguageTag("tr-TR"))
                        .apply { timeZone = java.util.TimeZone.getTimeZone("Europe/Istanbul") }
                        .format(java.util.Date(it)),
                    color = grey,
                    fontSize = 11.sp
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag(tagPrefix + "delete_" + exam.id)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Denemeyi sil",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (lastRecord != null) {
                        TextButton(onClick = onOpenReport) {
                            Text("Karnem", color = cobalt, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = cobalt),
                        shape = RoundedCornerShape(11.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                        Text(if (lastRecord != null) "Tekrar Çöz" else "Başla", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DenemeHistoryRow(
    record: TestResultRecord,
    tagPrefix: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = pale,
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tagPrefix + record.id)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    record.testTitle,
                    color = navy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${record.correctCount}D • ${record.wrongCount}Y • ${record.emptyCount}B • ${record.durationSeconds / 60} dk",
                    color = grey,
                    fontSize = 11.sp
                )
            }
            Text(
                String.format(Locale.forLanguageTag("tr-TR"), "%.2f", record.netScore),
                color = cobalt,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = "Karneni aç",
                modifier = Modifier.size(16.dp),
                tint = cobalt
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllSavedExamsBottomSheet(
    exams: List<MockExamItem>,
    histories: List<TestResultRecord>,
    selectedFilter: ExamType?,
    onSelectFilter: (ExamType?) -> Unit,
    onDismiss: () -> Unit,
    onOpenInfo: (MockExamItem) -> Unit,
    onOpenReport: (TestResultRecord) -> Unit,
    onStartExam: (MockExamItem) -> Unit,
    onDeleteExam: (MockExamItem) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .heightIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Tüm Denemelerim (${exams.size})", color = navy, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text("Kayıtlı tüm deneme kitapçıkların", color = grey, fontSize = 12.sp)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("all_saved_denemeler_close_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null, ExamType.TYT, ExamType.AYT).forEach { choice ->
                    ChoiceChip(
                        text = choice?.name ?: "Tümü",
                        selected = choice == selectedFilter,
                        onClick = { onSelectFilter(choice) }
                    )
                }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("all_saved_denemeler_list"),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(exams, key = { it.id }) { exam ->
                    val last = histories.firstOrNull { it.testId == exam.linkedTest.id }
                    SavedDenemeCard(
                        exam = exam,
                        lastRecord = last,
                        tagPrefix = "all_deneme_",
                        onOpenInfo = { onOpenInfo(exam) },
                        onOpenReport = { last?.let(onOpenReport) },
                        onStart = { onStartExam(exam) },
                        onDelete = { onDeleteExam(exam) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllHistoriesBottomSheet(
    histories: List<TestResultRecord>,
    onDismiss: () -> Unit,
    onSelectRecord: (TestResultRecord) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .heightIn(max = 620.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Tüm Sonuç Geçmişi (${histories.size})", color = navy, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text("Çözdüğün tüm deneme kayıtları ve karnelerin", color = grey, fontSize = 12.sp)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("all_histories_close_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat")
                }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("all_histories_list"),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(histories, key = { it.id }) { record ->
                    DenemeHistoryRow(
                        record = record,
                        tagPrefix = "all_deneme_result_",
                        onClick = { onSelectRecord(record) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExamNumberCard(label: String, value: String, modifier: Modifier, color: Color) {
    Surface(modifier = modifier, color = pale, shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, fontSize = 21.sp, color = color, fontWeight = FontWeight.ExtraBold)
            Text(label, fontSize = 10.sp, color = grey)
        }
    }
}

@Composable
private fun ColorTag(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(7.dp), color = color.copy(alpha = .12f)) {
        Text(
            label,
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        color = if (selected) cobalt else pale,
        modifier = Modifier.heightIn(min = 38.dp)
    ) {
        Box(Modifier.padding(horizontal = 15.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(
                text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = if (selected) Color.White else ink
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DenemeCreatorSheet(
    questions: List<CustomQuestionRecord>,
    solvedRecords: List<TestResultRecord>,
    layoutConfig: TestPageLayoutConfig,
    onOpenLayoutSettings: () -> Unit,
    onOpenQuestionsInfo: (YksTestItem) -> Unit,
    onDismiss: () -> Unit,
    onStartPrepared: (DenemeRequest, DenemePlan.Ready) -> Unit
) {
    var kind by remember { mutableStateOf(DenemeKind.BRANS) }
    var examType by remember { mutableStateOf(ExamType.TYT) }
    var blueprintId by remember { mutableStateOf("tyt_turkce") }
    var subjectId by remember { mutableStateOf("tyt_turkce") }
    var topicId by remember { mutableStateOf("sozcukte_anlam") }
    var difficulty by remember { mutableStateOf(DenemeDifficulty.DENGELI) }
    var count by remember { mutableIntStateOf(12) }
    var duration by remember { mutableIntStateOf(0) }
    var timerMode by remember { mutableStateOf(DenemeTimerMode.COUNTDOWN) }
    var showDistribution by remember { mutableStateOf(false) }
    var branchMenu by remember { mutableStateOf(false) }
    var topicMenu by remember { mutableStateOf(false) }
    var creationStage by remember { mutableStateOf<DenemeCreationStage>(DenemeCreationStage.Setup) }
    var buildMissingOverride by remember { mutableStateOf<DenemePlan.Missing?>(null) }

    val uriHandler = LocalUriHandler.current
    val choices = remember(examType) { DenemeBlueprints.forType(examType) }
    val subjects = remember(examType) { SoruLabCatalog.allSubjects.filter { it.examType == examType } }
    val chosenSubject = subjects.firstOrNull { it.id == subjectId } ?: subjects.first()
    val chosenTopic = chosenSubject.topics.firstOrNull { it.id == topicId } ?: chosenSubject.topics.first()
    val blueprint = choices.firstOrNull { it.id == blueprintId } ?: choices.first()

    val effectiveDuration = if (timerMode == DenemeTimerMode.STOPWATCH) 0 else duration
    val request = DenemeRequest(
        kind = kind,
        examType = examType,
        blueprintId = blueprint.id,
        subjectId = chosenSubject.id,
        topicId = chosenTopic.id,
        count = count,
        difficulty = difficulty,
        durationMinutes = effectiveDuration,
        timerMode = timerMode
    )

    LaunchedEffect(
        request.kind,
        request.examType,
        request.blueprintId,
        request.subjectId,
        request.topicId,
        request.count,
        request.difficulty
    ) {
        buildMissingOverride = null
    }

    val contentKey = remember(
        request.kind,
        request.examType,
        request.blueprintId,
        request.subjectId,
        request.topicId,
        request.count,
        request.difficulty,
        request.durationMinutes,
        questions,
        solvedRecords
    ) {
        listOf<Any?>(
            request.kind,
            request.examType,
            request.blueprintId,
            request.subjectId,
            request.topicId,
            request.count,
            request.difficulty,
            request.durationMinutes,
            questions.size,
            solvedRecords.size
        )
    }

    val preview by key(contentKey) {
        produceState<DenemePlan?>(initialValue = null) {
            value = withContext(Dispatchers.Default) {
                DenemePlanner.build(request, questions, solvedRecords, sequence = 0L)
            }
        }
    }
    val effectivePreview = buildMissingOverride ?: preview
    val quota = if (kind == DenemeKind.BRANS) blueprint.questionCount else count

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        AnimatedContent(
            targetState = creationStage,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "deneme_creation_stage"
        ) { stage ->
            when (stage) {
                is DenemeCreationStage.Setup -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .navigationBarsPadding()
                            .heightIn(max = 670.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Deneme Oluştur", color = navy, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                                Text("2018–2026 ÖSYM kazanım planıyla • Kişisel kitaplığından", color = grey, fontSize = 12.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = onOpenLayoutSettings,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = "Sayfa Düzeni", tint = cobalt, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "${layoutConfig.columnMode.shortLabel} • ${layoutConfig.bookletType.code}",
                                        color = cobalt,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Default.Close, contentDescription = "Kapat")
                                }
                            }
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("Deneme türü", color = navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChoiceChip("Branş Denemesi", kind == DenemeKind.BRANS) { kind = DenemeKind.BRANS }
                                ChoiceChip("Konu Denemesi", kind == DenemeKind.KONU) { kind = DenemeKind.KONU }
                            }
                            Text("Oturum", color = navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExamType.entries.forEach { type ->
                                    ChoiceChip(type.name, examType == type) {
                                        examType = type
                                        blueprintId = DenemeBlueprints.forType(type).first().id
                                        val first = SoruLabCatalog.allSubjects.first { it.examType == type }
                                        subjectId = first.id
                                        topicId = first.topics.first().id
                                        duration = 0
                                    }
                                }
                            }
                            if (kind == DenemeKind.BRANS) {
                                Text("Branş seçimi", color = navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Box(Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { branchMenu = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("deneme_branch_selector"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(blueprint.label, Modifier.weight(1f), color = navy)
                                        Text("${blueprint.questionCount} soru ▾", color = cobalt, fontSize = 12.sp)
                                    }
                                    DropdownMenu(
                                        expanded = branchMenu,
                                        onDismissRequest = { branchMenu = false },
                                        modifier = Modifier.heightIn(max = 360.dp)
                                    ) {
                                        choices.forEach { value ->
                                            DropdownMenuItem(
                                                text = { Text("${value.label} • ${value.questionCount} soru") },
                                                onClick = {
                                                    blueprintId = value.id
                                                    duration = 0
                                                    branchMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "Kota: " + blueprint.sections.joinToString(" • ") { "${it.label} ${it.count}" },
                                    color = grey,
                                    fontSize = 11.sp
                                )
                            } else {
                                Text("Ders", color = navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    items(subjects, key = { it.id }) { subject ->
                                        ChoiceChip(subject.name, chosenSubject.id == subject.id) {
                                            subjectId = subject.id
                                            topicId = subject.topics.first().id
                                            duration = 0
                                        }
                                    }
                                }
                                Text("Konu", color = navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Box(Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { topicMenu = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(chosenTopic.name, Modifier.weight(1f), color = navy)
                                        Text("▾", color = cobalt)
                                    }
                                    DropdownMenu(
                                        expanded = topicMenu,
                                        onDismissRequest = { topicMenu = false },
                                        modifier = Modifier.heightIn(max = 360.dp)
                                    ) {
                                        chosenSubject.topics.forEach { topic ->
                                            DropdownMenuItem(
                                                text = { Text(topic.name) },
                                                onClick = {
                                                    topicId = topic.id
                                                    topicMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                                Text("Soru sayısı", color = navy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    items(listOf(5, 7, 8, 10, 12, 15, 16, 20, 30, 40)) { value ->
                                        ChoiceChip(value.toString(), count == value) {
                                            count = value
                                            duration = 0
                                        }
                                    }
                                }
                            }
                            if (kind == DenemeKind.BRANS) {
                                Surface(color = pale, shape = RoundedCornerShape(14.dp)) {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(13.dp),
                                        verticalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        TextButton(
                                            onClick = { showDistribution = !showDistribution },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text(
                                                if (showDistribution) "Konu dağılımını gizle" else "Konu dağılımını gör",
                                                fontWeight = FontWeight.Bold,
                                                color = cobalt
                                            )
                                        }
                                        if (showDistribution) {
                                            blueprint.sections.forEach { section ->
                                                Text(section.label, color = navy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                DenemeTopicProfiles.forSection(section).forEach { quotaItem ->
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text(quotaItem.label, Modifier.weight(1f), color = grey, fontSize = 12.sp)
                                                        val missing = (effectivePreview as? DenemePlan.Missing)?.shortage?.firstOrNull {
                                                            it.section == section.label + " • " + quotaItem.label
                                                        }
                                                        Text(
                                                            if (missing == null) "${quotaItem.count} soru"
                                                            else "${missing.available}/${quotaItem.count} uygun",
                                                            color = if (missing == null) navy else Color(0xFFAE5F12),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                                TextButton(
                                                    onClick = { uriHandler.openUri(DenemeTopicProfiles.sourceFor(section)) },
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("Geçmiş yılların dağılımı ↗", color = cobalt, fontSize = 11.sp)
                                                }
                                            }
                                            Text(DenemeTopicProfiles.NOTICE, color = grey, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                            Text("Zorluk", color = navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            LazyRow(
                                modifier = Modifier.testTag("deneme_difficulty_row"),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                items(DenemeDifficulty.entries) { d ->
                                    ChoiceChip(d.label, difficulty == d) { difficulty = d }
                                }
                            }

                            Text("Zamanlayıcı Modu", color = navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Surface(
                                color = pale,
                                shape = RoundedCornerShape(13.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TimerModeSegmentButton(
                                        title = "Süreli (Geri Sayım)",
                                        icon = Icons.Outlined.Schedule,
                                        selected = timerMode == DenemeTimerMode.COUNTDOWN,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("deneme_timer_mode_countdown"),
                                        onClick = { timerMode = DenemeTimerMode.COUNTDOWN }
                                    )
                                    TimerModeSegmentButton(
                                        title = "Kronometre (0'dan Başla)",
                                        icon = Icons.Outlined.Timer,
                                        selected = timerMode == DenemeTimerMode.STOPWATCH,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("deneme_timer_mode_stopwatch"),
                                        onClick = {
                                            timerMode = DenemeTimerMode.STOPWATCH
                                            duration = 0
                                        }
                                    )
                                }
                            }

                            if (timerMode == DenemeTimerMode.COUNTDOWN) {
                                val resolvedMinutes = duration.takeIf { it > 0 } ?: (
                                    if (kind == DenemeKind.BRANS) blueprint.minutes
                                    else (quota * 1.8f).toInt().coerceIn(5, 240)
                                )
                                Text(
                                    "Süre • $resolvedMinutes dakika",
                                    color = navy,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    items(listOf(0, 20, 30, 40, 50, 60, 75, 90, 120, 165)) { value ->
                                        ChoiceChip(
                                            if (value == 0) "Önerilen" else "$value dk",
                                            duration == value
                                        ) { duration = value }
                                    }
                                }
                            } else {
                                Surface(
                                    color = Color(0xFFEEF4FF),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Timer,
                                            contentDescription = null,
                                            tint = cobalt,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Sayaç 00:00'dan ileri doğru sayar. Süre baskısı olmadan çöz, gerçek çözüm hızın kaydedilsin.",
                                            color = ink,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            color = if (effectivePreview is DenemePlan.Ready) Color(0xFFEAF9F1) else Color(0xFFFFF6E6),
                            shape = RoundedCornerShape(13.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                when (val current = effectivePreview) {
                                    null -> {
                                        Text("Soru kitaplığın kontrol ediliyor…", color = grey, fontSize = 12.sp)
                                    }
                                    is DenemePlan.Ready -> {
                                        Text(
                                            "Hazır • $quota doğrulanmış soru",
                                            color = success,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            current.sectionSummary.joinToString(" • ") { "${it.first}: ${it.second}" },
                                            color = grey,
                                            fontSize = 11.sp
                                        )
                                        current.auditReport?.let { audit ->
                                            OsymAuditCompactRow(audit)
                                        }
                                    }
                                    is DenemePlan.Missing -> {
                                        Text(
                                            "Soru havuzunda eksikler var",
                                            color = Color(0xFFAE5F12),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (current.shortage.size > 4) {
                                            Text(
                                                "+${current.shortage.size - 4} konuda daha eksik var. Konu dağılımını kontrol et.",
                                                color = grey,
                                                fontSize = 11.sp
                                            )
                                        }
                                        current.shortage.take(4).forEach {
                                            Text(
                                                "${it.section}: ${it.available}/${it.needed} uygun soru",
                                                color = Color(0xFF9A5B18),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val built = DenemePlanner.build(
                                    request = request,
                                    pool = questions,
                                    previous = solvedRecords,
                                    sequence = System.currentTimeMillis()
                                )
                                when (built) {
                                    is DenemePlan.Missing -> buildMissingOverride = built
                                    is DenemePlan.Ready -> {
                                        buildMissingOverride = null
                                        creationStage = DenemeCreationStage.Generating(request, built)
                                    }
                                }
                            },
                            enabled = effectivePreview is DenemePlan.Ready,
                            colors = ButtonDefaults.buttonColors(containerColor = cobalt),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(51.dp)
                                .testTag("deneme_confirm_create"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Denemeyi Oluştur", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(7.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(17.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }

                is DenemeCreationStage.Generating -> {
                    DenemeGeneratingView(
                        plan = stage.plan,
                        onComplete = {
                            if (creationStage is DenemeCreationStage.Generating) {
                                creationStage = DenemeCreationStage.Ready(stage.request, stage.plan)
                            }
                        }
                    )
                }

                is DenemeCreationStage.Ready -> {
                    DenemeReadyView(
                        request = stage.request,
                        plan = stage.plan,
                        onOpenInfo = { onOpenQuestionsInfo(stage.plan.test) },
                        onBackToSetup = { creationStage = DenemeCreationStage.Setup },
                        onDismiss = onDismiss,
                        onStart = { onStartPrepared(stage.request, stage.plan) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerModeSegmentButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color.White else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, Color(0xFFD7E2F8)) else null,
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier = modifier.heightIn(min = 42.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) cobalt else grey,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) navy else grey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DenemeGeneratingView(
    plan: DenemePlan.Ready,
    onComplete: () -> Unit
) {
    val progress = remember(plan.test.id) { Animatable(0f) }

    LaunchedEffect(plan.test.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
        )
        onComplete()
    }

    val steps = listOf(
        "Kötü kırpılmış sorular eleniyor",
        "2018–2026 ÖSYM kazanımları ve soru tipleri eşleştiriliyor",
        "Kitapçık sırası oluşturuluyor"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 14.dp)
            .navigationBarsPadding()
            .testTag("deneme_generating_state"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "Denemeniz Oluşturuluyor...",
                    color = navy,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 21.sp
                )
                Text(
                    "${plan.test.questions.size} doğrulanmış soru ÖSYM kitapçık düzenine hazırlanıyor",
                    color = grey,
                    fontSize = 12.sp
                )
            }
            ColorTag("%${(progress.value * 100).toInt().coerceIn(10, 100)}", cobalt)
        }

        LinearProgressIndicator(
            progress = { progress.value.coerceIn(0.08f, 1f) },
            color = cobalt,
            trackColor = pale,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
        )

        Surface(
            color = pale,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                steps.forEachIndexed { index, stepText ->
                    val reached = progress.value >= (index * 0.31f)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (reached) success else Color(0xFFBFC8DC),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stepText,
                            color = if (reached) navy else grey,
                            fontWeight = if (reached) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = onComplete,
                modifier = Modifier.testTag("deneme_generating_skip_btn")
            ) {
                Text("Hemen Geç", color = cobalt, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DenemeReadyView(
    request: DenemeRequest,
    plan: DenemePlan.Ready,
    onOpenInfo: () -> Unit,
    onBackToSetup: () -> Unit,
    onDismiss: () -> Unit,
    onStart: () -> Unit
) {
    val timerSummary = if (request.timerMode == DenemeTimerMode.STOPWATCH || plan.minutes <= 0) {
        "00:00 Kronometre"
    } else {
        "${plan.minutes} dk Geri Sayım"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .testTag("deneme_ready_state"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = success,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        "Denemeniz Oluşturuldu",
                        color = navy,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 21.sp
                    )
                    Text(
                        "Kitapçık ve soru eşleştirmeleri sınava hazır",
                        color = grey,
                        fontSize = 12.sp
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onOpenInfo,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(pale)
                        .testTag("deneme_ready_info_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Soru, konu, kazanım ve kitap listesi",
                        tint = cobalt,
                        modifier = Modifier.size(21.dp)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat")
                }
            }
        }

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color(0xFFE1E7F2)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    plan.test.fullTitle,
                    color = navy,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorTag("${plan.test.questions.size} Soru", cobalt)
                    ColorTag(request.difficulty.label, Color(0xFF8A5CF6))
                    ColorTag(timerSummary, success)
                }
                if (plan.sectionSummary.isNotEmpty()) {
                    Text(
                        plan.sectionSummary.joinToString(" • ") { "${it.first}: ${it.second} soru" },
                        color = grey,
                        fontSize = 12.sp
                    )
                }
                plan.auditReport?.let { audit ->
                    HorizontalDivider(color = Color(0xFFF0F2F7))
                    OsymAuditCompactRow(audit)
                }
            }
        }

        Surface(
            onClick = onOpenInfo,
            color = pale,
            shape = RoundedCornerShape(13.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = cobalt, modifier = Modifier.size(18.dp))
                Text(
                    "Sağ üstteki ⓘ butonundan tüm soruların konu, ÖSYM kazanımı ve kitap kaynağını sırayla inceleyebilirsin.",
                    color = ink,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBackToSetup,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFD5DFF2)),
                modifier = Modifier.height(51.dp)
            ) {
                Text("Düzenle", color = navy, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = cobalt),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(51.dp)
                    .testTag("deneme_ready_start_btn")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(6.dp))
                Text("Denemeye Başla", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DenemeQuestionsInfoDialog(
    test: YksTestItem,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 580.dp)
                .testTag("deneme_questions_info_dialog")
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Soru & Kazanım Haritası",
                            color = navy,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(
                            "${test.fullTitle} • ${test.questions.size} Soru",
                            color = grey,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("deneme_questions_info_close_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = navy)
                    }
                }

                HorizontalDivider(color = Color(0xFFEEF1F8))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .testTag("deneme_questions_info_list"),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    itemsIndexed(test.questions, key = { idx, q -> q.id.ifBlank { "q_$idx" } }) { index, q ->
                        val qNumber = if (q.number > 0) q.number else (index + 1)
                        val topicText = q.topicHeader.ifBlank { q.subject.ifBlank { "Genel Konu" } }
                        val kazanimText = q.learningOutcomeName.ifBlank {
                            q.learningOutcome.ifBlank { "ÖSYM Kazanımı" }
                        }
                        val sourceText = q.sourceBookTitle.ifBlank {
                            q.publisherName.ifBlank { q.publisherStamp.ifBlank { "Soru Kitaplığı" } }
                        }

                        Surface(
                            color = pale,
                            shape = RoundedCornerShape(11.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("deneme_info_row_$qNumber")
                        ) {
                            Column(
                                Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "$qNumber. Soru — $topicText",
                                        color = navy,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = sourceText,
                                        color = cobalt,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "Kazanım: $kazanimText • Kaynak: $sourceText",
                                    color = grey,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class CourseScore(val name: String, val correct: Int, val wrong: Int, val blank: Int) {
    val net: Double get() = correct - wrong * .25
}

private fun courseScoreBreakdown(record: TestResultRecord): Pair<List<CourseScore>, List<Pair<String, Double>>> {
    val snapshot = TestSnapshotCodec.decode(record.testSnapshotJson) ?: return emptyList<CourseScore>() to emptyList()
    val answers = runCatching { JSONObject(record.answersMapJson) }.getOrNull() ?: JSONObject()
    val groups = snapshot.questions.groupBy { it.subject.ifBlank { "Ders" } }
    val courses = groups.map { (name, questions) ->
        var d = 0
        var y = 0
        var b = 0
        questions.forEach { q ->
            val marked = answers.optString(q.number.toString())
            if (marked.isEmpty()) b++
            else if (marked == q.correctOption) d++
            else y++
        }
        CourseScore(name, d, y, b)
    }
    val topicLoss = snapshot.questions.groupBy { it.subject + " • " + it.topicHeader.ifBlank { "Konu" } }
        .map { (topic, questions) ->
            topic to questions.sumOf { q ->
                val answer = answers.optString(q.number.toString())
                when {
                    answer.isEmpty() -> 1.0
                    answer == q.correctOption -> 0.0
                    else -> 1.25
                }
            }
        }
        .filter { it.second > 0.0 }
        .sortedByDescending { it.second }
        .take(6)
    return courses to topicLoss
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DenemeResultSheet(
    record: TestResultRecord,
    onDismiss: () -> Unit,
    onReview: () -> Unit
) {
    val breakdown = remember(record) { courseScoreBreakdown(record) }
    val snapshot = remember(record.testSnapshotJson) { TestSnapshotCodec.decode(record.testSnapshotJson) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .heightIn(max = 630.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Text("Deneme Karnem", color = navy, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Text(record.testTitle, color = grey, fontSize = 13.sp)
            Surface(shape = RoundedCornerShape(19.dp), color = navy, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        String.format(Locale.forLanguageTag("tr-TR"), "%.2f NET", record.netScore),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        "${record.correctCount} Doğru • ${record.wrongCount} Yanlış • ${record.emptyCount} Boş",
                        fontSize = 13.sp,
                        color = Color(0xFFCEDCFB)
                    )
                    Text(
                        "${record.durationSeconds / 60} dakika • ${record.totalQuestions} soru",
                        fontSize = 11.sp,
                        color = Color(0xFFB9C8F4)
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                snapshot?.takeIf { it.denemeProfileVersion.isNotBlank() }?.let { saved ->
                    val audit = remember(saved) { OsymExamAuditEngine.auditTest(saved) }
                    Text(
                        "${saved.denemeDifficultyLabel} • Plan ${saved.denemeProfileVersion}",
                        color = cobalt,
                        fontSize = 11.sp
                    )
                    OsymAuditCompactRow(audit)
                    Text(
                        if (saved.denemeReusedQuestionCount > 0)
                            "${saved.denemeReusedQuestionCount} soru son 6 denemende de vardı."
                        else "Son 6 denemenden tekrar soru kullanılmadı.",
                        color = grey,
                        fontSize = 11.sp
                    )
                    Text("Uygulanan konu planı", color = navy, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(saved.denemeDistributionSummary, color = grey, fontSize = 12.sp)
                }
                Text("Ders Bazlı Sonuçlar", color = navy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                breakdown.first.forEach { course ->
                    Surface(color = pale, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(course.name, color = navy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    String.format(Locale.forLanguageTag("tr-TR"), "%.2f net", course.net),
                                    color = cobalt,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                "${course.correct}D • ${course.wrong}Y • ${course.blank}B",
                                fontSize = 11.sp,
                                color = grey
                            )
                            LinearProgressIndicator(
                                progress = {
                                    if (course.correct + course.wrong + course.blank > 0)
                                        course.correct.toFloat() / (course.correct + course.wrong + course.blank)
                                    else 0f
                                },
                                color = success,
                                trackColor = Color(0xFFE0E5F2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                }
                Text("En Çok Net Kaybettiren Konular", color = navy, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (breakdown.second.isEmpty()) {
                    Text("Bu denemede konu kaybı bulunmadı.", color = grey, fontSize = 12.sp)
                }
                breakdown.second.forEach { (topic, loss) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(topic, color = grey, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(
                            String.format(Locale.forLanguageTag("tr-TR"), "%.2f net", loss),
                            color = Color(0xFFD4653A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Button(
                onClick = onReview,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(containerColor = cobalt)
            ) {
                Text("Sorularımı İncele", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun OsymAuditCompactRow(audit: OsymExamAuditReport) {
    val activeTypes = remember(audit.questionTypeCounts) {
        audit.questionTypeCounts.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .take(4)
            .joinToString(" • ") { "${it.key.shortTag} ${it.value}" }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "ÖSYM Denetim: ${audit.complianceScore}/100 • ${audit.distinctKazanimCount} kazanım • ${audit.distinctQuestionTypeCount}/6 soru tipi",
            color = if (audit.isOsymCompliant) success else cobalt,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (activeTypes.isNotBlank()) {
            Text(
                text = activeTypes,
                color = grey,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
