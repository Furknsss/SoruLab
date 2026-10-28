package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.util.StudioImageAndPdfEngine
import com.example.viewmodel.BatchPdfCropState
import com.example.viewmodel.ExtractedPdfQuestionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PublisherLogoEmblem(
    publisher: YksPublisherInfo,
    size: Dp = 36.dp,
    overrideLogoPath: String = "",
    modifier: Modifier = Modifier
) {
    val effectivePath = overrideLogoPath.ifBlank { publisher.customLogoPath }
    val customBitmap = remember(effectivePath) {
        if (effectivePath.isNotBlank() && File(effectivePath).exists()) {
            runCatching { BitmapFactory.decodeFile(effectivePath) }.getOrNull()
        } else {
            null
        }
    }

    if (customBitmap != null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = customBitmap.asImageBitmap(),
                contentDescription = "${publisher.name} Logosu",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
            )
        }
    } else {
        val primaryColor = Color(publisher.primaryColorHex)
        val secondaryColor = Color(publisher.secondaryColorHex)
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(primaryColor, secondaryColor)
                    )
                )
                .border(1.dp, primaryColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = publisher.shortCode.take(4),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (size.value * 0.31f).sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun PublisherLogoEmblem(
    publisherName: String,
    customLogoUri: String = "",
    customPublishers: List<YksPublisherInfo> = emptyList(),
    sizeDp: Dp = 36.dp,
    showNameAlongside: Boolean = false
) {
    val resolved = remember(publisherName, customPublishers) {
        SoruLabPublisherPresets.resolvePublisher(publisherName, customPublishers)
    }
    if (showNameAlongside) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PublisherLogoEmblem(
                publisher = resolved,
                size = sizeDp,
                overrideLogoPath = customLogoUri
            )
            Text(
                text = resolved.name,
                color = Color(0xFF0F172A),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        PublisherLogoEmblem(
            publisher = resolved,
            size = sizeDp,
            overrideLogoPath = customLogoUri
        )
    }
}

@Composable
fun DenemelerHubScreen(
    selectedFilter: ExamType?,
    mockExams: List<MockExamItem>,
    solvedRecords: List<TestResultRecord>,
    customQuestionsCount: Int,
    onSelectFilter: (ExamType?) -> Unit,
    onStartExam: (MockExamItem) -> Unit,
    onReviewExam: (MockExamItem) -> Unit,
    onOpenPdfStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val solvedIds = remember(solvedRecords) { solvedRecords.map { it.testId }.toSet() }
    val filteredExams = remember(selectedFilter, mockExams) {
        if (selectedFilter == null) mockExams else mockExams.filter { it.examType == selectedFilter }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .testTag("denemeler_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0F172A),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF0F172A), Color(0xFF1E3A8A), Color(0xFF1D4ED8))
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFF38BDF8).copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WorkspacePremium,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "TÜRKİYE GENELİ KURUMSAL DENEME MERKEZİ",
                                        color = Color(0xFFE0F2FE),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "${solvedRecords.count { it.testId.startsWith("mock_") }} Çözüldü",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Text(
                            text = "Kurumsal TYT & AYT Deneme Sınavları",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        )
                        Text(
                            text = "Gerçek sınav süresi, optik kodlama ve konu bazlı net analizi ile kendinizi Türkiye genelinde test edin.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChipPill(
                    label = "Tüm Denemeler (${mockExams.size})",
                    selected = selectedFilter == null,
                    onClick = { onSelectFilter(null) }
                )
                FilterChipPill(
                    label = "TYT Denemeleri",
                    selected = selectedFilter == ExamType.TYT,
                    onClick = { onSelectFilter(ExamType.TYT) }
                )
                FilterChipPill(
                    label = "AYT Denemeleri",
                    selected = selectedFilter == ExamType.AYT,
                    onClick = { onSelectFilter(ExamType.AYT) }
                )
            }
        }

        items(filteredExams, key = { it.id }) { exam ->
            val isSolved = solvedIds.contains(exam.linkedTest.id)
            val record = solvedRecords.firstOrNull { it.testId == exam.linkedTest.id }
            MockExamCardItem(
                exam = exam,
                isSolved = isSolved,
                solvedRecord = record,
                onStart = { onStartExam(exam) },
                onReview = { onReviewExam(exam) }
            )
        }
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) Color(0xFF0F172A) else Color.White,
        border = BorderStroke(1.dp, if (selected) Color(0xFF0F172A) else Color(0xFFE2E8F0)),
        shadowElevation = if (selected) 3.dp else 0.dp,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFF475569),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun MockExamCardItem(
    exam: MockExamItem,
    isSolved: Boolean,
    solvedRecord: TestResultRecord?,
    onStart: () -> Unit,
    onReview: () -> Unit
) {
    val pubInfo = remember(exam.publisher) { SoruLabPublisherPresets.resolvePublisher(exam.publisher) }
    val badgeColor = Color(pubInfo.primaryColorHex)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, if (isSolved) Color(0xFFBBF7D0) else Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mock_exam_${exam.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    PublisherLogoEmblem(
                        publisher = pubInfo,
                        size = 50.dp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = badgeColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "${exam.examType.name} • ${exam.publisher}",
                                    color = badgeColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF1F5F9)
                            ) {
                                Text(
                                    text = exam.difficulty.label,
                                    color = Color(0xFF475569),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            text = exam.title,
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = exam.tag,
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }
                }

                if (isSolved && solvedRecord != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFDCFCE7)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "${String.format("%.2f", solvedRecord.netScore)} NET",
                                color = Color(0xFF15803D),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${solvedRecord.correctCount}D • ${solvedRecord.wrongCount}Y",
                                color = Color(0xFF166534),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${exam.durationMinutes} Dakika",
                            color = Color(0xFF334155),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatListNumbered,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${exam.questionCount} Soru",
                            color = Color(0xFF334155),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isSolved) {
                        OutlinedButton(
                            onClick = onReview,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            Text("Karnemi İncele", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = onStart,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = badgeColor)
                    ) {
                        Text(
                            text = if (isSolved) "Tekrar Çöz" else "Denemeye Başla",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * =======================================================================
 * BÖLÜM 1: YÜKSEK HIZLI TEK TIKLA PDF İŞLEME & SADECE "KİTAPLIĞIM" VİTRİNİ
 * =======================================================================
 * - Kullanıcı sadece Kitap Adı, Yayın/Logo ve PDF'i seçip "Başlat ve Sisteme Aktar" butonuna bir kez basar.
 * - Ekranda %0'dan %100'e ilerleyen akıcı bir Yüzde Çubuğu (% İlerleme Barı) görünür.
 * - Yükleme ekranının altında kırpılan yüzlerce soru ASLA listelenmez (kasmayı önler).
 * - Üstte "Sistemdeki Toplam Aktif Soru: [X]" sayacı bulunur.
 * - Altta sadece "KİTAPLIĞIM" vitrini yer alır: Her kaynak; Kitap Kapağı, Yayın Logosu,
 *   Kitap Adı ve içinde kaç aktif soru olduğu rozetiyle bir "Kitap Kartı" olarak dizilir.
 */
@Composable
fun SoruHavuzuAndPdfStudioView(
    customQuestions: List<CustomQuestionRecord>,
    customPublishers: List<YksPublisherInfo>,
    batchCropState: BatchPdfCropState,
    onStartFastParallelPdfExtraction: (
        pdfUri: Uri,
        fileNameHint: String,
        publisher: YksPublisherInfo,
        onComplete: (List<ExtractedPdfQuestionItem>, String, String) -> Unit
    ) -> Unit,
    onAddCustomPublisher: (name: String, shortCode: String, customLogoPath: String) -> YksPublisherInfo,
    onSaveExtractedPdfBatch: (
        extractedItems: List<ExtractedPdfQuestionItem>,
        publisherName: String,
        publisherLogoUri: String,
        bookOrTestName: String,
        bookCoverPath: String
    ) -> Unit,
    onDeleteLibraryBook: (List<String>) -> Unit,
    onClearAllData: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onBack: () -> Unit,
    onSolveBookInPractice: (CustomQuestionRecord) -> Unit = {}
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allPublishers = remember(customPublishers) {
        SoruLabPublisherPresets.mergedPublishers(customPublishers)
    }

    var selectedPublisher by remember(allPublishers) {
        mutableStateOf(allPublishers.first())
    }
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdfFileName by remember { mutableStateOf("") }
    var totalPdfPages by remember { mutableIntStateOf(0) }

    var showAddPublisherModal by remember { mutableStateOf(false) }
    var newPubName by remember { mutableStateOf("") }
    var newPubBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var newPubSavedPath by remember { mutableStateOf("") }
    var isProcessingLogo by remember { mutableStateOf(false) }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingLogo = true
            scope.launch {
                val savedPath = withContext(Dispatchers.IO) {
                    StudioImageAndPdfEngine.processPublisherLogoFloodFillAndTrim(
                        context = context,
                        logoUri = uri
                    )
                }
                if (savedPath.isNotBlank() && File(savedPath).exists()) {
                    val decoded = withContext(Dispatchers.IO) {
                        runCatching { BitmapFactory.decodeFile(savedPath) }.getOrNull()
                    }
                    newPubBitmap = decoded
                    newPubSavedPath = savedPath
                }
                isProcessingLogo = false
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onDismissError()
            selectedPdfUri = uri
            val lastSeg = uri.lastPathSegment?.substringAfterLast("/") ?: "Secilen_Kaynak.pdf"
            selectedPdfFileName = if (lastSeg.endsWith(".pdf", ignoreCase = true)) lastSeg else "$lastSeg.pdf"
            scope.launch {
                val count = withContext(Dispatchers.IO) {
                    StudioImageAndPdfEngine.getPdfPageCount(context, uri)
                }
                totalPdfPages = count
            }
        }
    }

    // Group all questions in the pool into "KİTAPLIĞIM" Book Cards
    val libraryBooks: List<LibraryBookCardModel> = remember(customQuestions, customPublishers) {
        if (customQuestions.isEmpty()) {
            emptyList()
        } else {
            customQuestions
                .groupBy { q ->
                    val pub = q.publisherTag.trim().ifBlank { "3D Yayınları" }
                    val book = q.bookOrTestName.trim().ifBlank { "$pub Soru Bankası" }
                    "${pub.lowercase()}|${book.lowercase()}"
                }
                .map { (groupKey, questionsInBook) ->
                    val first = questionsInBook.first()
                    val pubName = first.publisherTag.trim().ifBlank { "3D Yayınları" }
                    val resolvedPub = SoruLabPublisherPresets.resolvePublisher(pubName, customPublishers)
                    val bookTitle = first.bookOrTestName.trim().ifBlank { "$pubName Soru Bankası" }
                    val coverPath = questionsInBook.firstOrNull { it.bookCoverPath.isNotBlank() }?.bookCoverPath
                        ?: first.croppedImagePath
                    val distinctSubjects = questionsInBook
                        .map { "${it.examType} ${it.subjectName} • ${it.topicName}".trim() }
                        .distinct()
                        .take(2)
                        .joinToString(" | ")

                    LibraryBookCardModel(
                        key = groupKey,
                        bookTitle = bookTitle,
                        publisherName = resolvedPub.name,
                        publisherInfo = resolvedPub.copy(
                            customLogoPath = first.publisherLogoUri.ifBlank { resolvedPub.customLogoPath }
                        ),
                        coverImagePath = coverPath,
                        activeQuestionCount = questionsInBook.size,
                        subjectsSummary = distinctSubjects.ifBlank { "YKS Soru Bankası" },
                        questions = questionsInBook
                    )
                }
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (batchCropState.progressPercent.coerceIn(0, 100)) / 100f,
        animationSpec = tween(durationMillis = 300),
        label = "pdf_upload_progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .testTag("pdf_cropper_studio"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 1024.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ÜST BAR: KURUMSAL BAŞLIK + "Sistemdeki Toplam Aktif Soru: [X]" SAYACI
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            onClick = onBack,
                            shape = CircleShape,
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            shadowElevation = 2.dp,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Geri Dön",
                                    tint = Color(0xFF0F172A),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Kitaplığım & Akıllı PDF Stüdyosu",
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            )
                            Text(
                                text = "Yayınevini ve PDF dosyasını seçip başlatın; kitap adı, ders ve konular otomatik tespit edilir.",
                                color = Color(0xFF64748B),
                                fontSize = 12.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                            modifier = Modifier.testTag("total_active_questions_counter")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = Color(0xFF1E3A8A),
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = "Sistemdeki Toplam Aktif Soru: ${customQuestions.size}",
                                    color = Color(0xFF1E3A8A),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }

                        if (customQuestions.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onClearAllData,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF64748B)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Temizle",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // ====================================================================
            // YENİ KAYNAK & PDF YÜKLEME KARTI (KİTAP ADI OTOMATİK TESPİT EDİLİR)
            // ====================================================================
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // 1. YAYINEVİ / LOGO SEÇİMİ
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "1. Yayınevi & Logo Seçimi",
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                allPublishers.forEach { pub ->
                                    val isSelected = selectedPublisher.id == pub.id
                                    Surface(
                                        onClick = { selectedPublisher = pub },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFFE2E8F0)
                                        ),
                                        modifier = Modifier.height(52.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                                        ) {
                                            PublisherLogoEmblem(
                                                publisher = pub,
                                                size = 34.dp
                                            )
                                            Text(
                                                text = pub.name,
                                                color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF334155),
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    onClick = {
                                        newPubName = ""
                                        newPubBitmap = null
                                        newPubSavedPath = ""
                                        showAddPublisherModal = true
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                    modifier = Modifier
                                        .height(52.dp)
                                        .testTag("add_publisher_logo_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = Color(0xFF1E3A8A),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "+ Yeni Yayın & Logo Ekle",
                                            color = Color(0xFF1E3A8A),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        // 2. PDF DOSYASI SEÇİCİ VE TEK TIK "BAŞLAT VE SİSTEME AKTAR" BUTONU
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "2. PDF Dosyası Seçimi (Kitap Adı, Ders ve Konu PDF'ten Otomatik Okunur)",
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )

                            Surface(
                                onClick = { pdfPickerLauncher.launch("application/pdf") },
                                shape = RoundedCornerShape(16.dp),
                                color = if (selectedPdfUri != null) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                border = BorderStroke(
                                    width = 1.5.dp,
                                    color = if (selectedPdfUri != null) Color(0xFF1E3A8A) else Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .testTag("select_pdf_button")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (selectedPdfUri != null) Icons.Default.PictureAsPdf else Icons.Default.UploadFile,
                                            contentDescription = null,
                                            tint = if (selectedPdfUri != null) Color(0xFF1E3A8A) else Color(0xFF475569),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column {
                                            Text(
                                                text = if (selectedPdfUri != null) {
                                                    selectedPdfFileName
                                                } else {
                                                    "PDF Dosyası Seçmek İçin Dokunun"
                                                },
                                                color = if (selectedPdfUri != null) Color(0xFF1E3A8A) else Color(0xFF334155),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (selectedPdfUri != null && totalPdfPages > 0) {
                                                Text(
                                                    text = "PDF Açıldı: Toplam $totalPdfPages Sayfa • Hazır",
                                                    color = Color(0xFF1D4ED8),
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 11.5.sp
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selectedPdfUri != null) Color(0xFFDBEAFE) else Color(0xFFE2E8F0)
                                    ) {
                                        Text(
                                            text = if (selectedPdfUri != null) "PDF Değiştir" else "PDF Seç",
                                            color = if (selectedPdfUri != null) Color(0xFF1E3A8A) else Color(0xFF334155),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // TAM GENİŞLİKLİ FERAH "BAŞLAT VE SİSTEME AKTAR" BUTONU (ASLA EZİLMEZ)
                            Button(
                                onClick = {
                                    val uri = selectedPdfUri ?: return@Button
                                    onStartFastParallelPdfExtraction(
                                        uri,
                                        selectedPdfFileName,
                                        selectedPublisher
                                    ) { extractedItems, coverPath, autoDetectedBookTitle ->
                                        if (extractedItems.isNotEmpty()) {
                                            onSaveExtractedPdfBatch(
                                                extractedItems,
                                                selectedPublisher.name,
                                                selectedPublisher.customLogoPath,
                                                autoDetectedBookTitle,
                                                coverPath
                                            )
                                        }
                                    }
                                },
                                enabled = selectedPdfUri != null && !batchCropState.isAnalyzing,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E3A8A),
                                    contentColor = Color.White,
                                    disabledContainerColor = Color(0xFFCBD5E1),
                                    disabledContentColor = Color(0xFF64748B)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("start_and_import_pdf_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RocketLaunch,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (batchCropState.isAnalyzing) {
                                        "PDF Sayfaları Taranıyor ve Kırpılıyor..."
                                    } else {
                                        "Başlat ve Sisteme Aktar"
                                    },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        // ====================================================================
                        // %0'DAN %100'E İLERLEYEN GERÇEK DURUM VE YÜZDE ÇUBUĞU
                        // ====================================================================
                        AnimatedVisibility(
                            visible = batchCropState.isAnalyzing,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            val pct = batchCropState.progressPercent.coerceIn(0, 100)
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFFEFF6FF),
                                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pdf_percentage_progress_card")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = batchCropState.progressMessage.ifBlank {
                                                "PDF Açıldı: Toplam $totalPdfPages Sayfa. Sayfa 1/$totalPdfPages yüksek çözünürlükte taranıyor..."
                                            },
                                            color = Color(0xFF1E3A8A),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = Color(0xFF1E3A8A)
                                        ) {
                                            Text(
                                                text = "%$pct",
                                                color = Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        color = Color(0xFF1E3A8A),
                                        trackColor = Color(0xFFDBEAFE),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(50))
                                    )
                                }
                            }
                        }

                        // ====================================================================
                        // KIRMIZI HATA UYARI KUTUSU (HATA OLURSA ASLA GİZLEMEZ!)
                        // ====================================================================
                        AnimatedVisibility(
                            visible = !batchCropState.errorMessage.isNullOrBlank(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFFEF2F2),
                                border = BorderStroke(1.5.dp, Color(0xFFEF4444)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pdf_error_alert_box")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = batchCropState.errorMessage.orEmpty(),
                                            color = Color(0xFF991B1B),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    IconButton(onClick = onDismissError) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Kapat",
                                            tint = Color(0xFFDC2626)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ====================================================================
            // "KİTAPLIĞIM" VİTRİNİ (YÜKLENEN KİTAPLAR ANINDA BURADA GÖRÜNÜR)
            // ====================================================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = Color(0xFF1E3A8A),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "KİTAPLIĞIM",
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                        ) {
                            Text(
                                text = "${libraryBooks.size} Kaynak Kitap",
                                color = Color(0xFF1E3A8A),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (libraryBooks.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFEFF6FF),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AutoStories,
                                        contentDescription = null,
                                        tint = Color(0xFF1E3A8A),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Kitaplığınızda Henüz Kaynak Bulunmuyor",
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Üstten yayınevini ve PDF dosyanızı seçip 'Başlat ve Sisteme Aktar' butonuna bastığınızda kaynağınız kapağıyla birlikte anında burada listelenir.",
                                    color = Color(0xFF64748B),
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }
            } else {
                val bookRows = libraryBooks.chunked(3)
                items(bookRows) { rowBooks ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        rowBooks.forEach { book ->
                            Box(modifier = Modifier.weight(1f)) {
                                LibraryBookShowcaseCard(
                                    book = book,
                                    onOpenBookTest = {
                                        book.questions.firstOrNull()?.let { firstRecord ->
                                            onSolveBookInPractice(firstRecord)
                                        }
                                    },
                                    onDeleteBook = {
                                        onDeleteLibraryBook(book.questions.map { it.id })
                                    }
                                )
                            }
                        }
                        repeat(3 - rowBooks.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    if (showAddPublisherModal) {
        Dialog(onDismissRequest = { showAddPublisherModal = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shadowElevation = 10.dp,
                modifier = Modifier.widthIn(min = 380.dp, max = 480.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Yeni Yayın & Logo Ekle",
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        IconButton(onClick = { showAddPublisherModal = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color(0xFF64748B))
                        }
                    }

                    OutlinedTextField(
                        value = newPubName,
                        onValueChange = { newPubName = it },
                        label = { Text("Yayınevi Adı (Örn: Apotemi Yayınları)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        onClick = {
                            logoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isProcessingLogo) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF1E3A8A),
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else if (newPubBitmap != null) {
                                    Image(
                                        bitmap = newPubBitmap!!.asImageBitmap(),
                                        contentDescription = "Yayınevi Logosu",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = Color(0xFF1E3A8A),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (newPubBitmap != null) "Logo Hazır (Değiştirmek İçin Dokunun)" else "Logo Görseli Seçin",
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Seçilen logonun arka planı otomatik temizlenir ve kitaplarınıza eklenir.",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showAddPublisherModal = false }) {
                            Text("Vazgeç", color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val cleanTitle = newPubName.trim().ifBlank { "Özel Yayın" }
                                val code = cleanTitle.split(" ")
                                    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                                    .joinToString("")
                                    .take(3)
                                    .ifBlank { cleanTitle.take(3).uppercase() }
                                val created = onAddCustomPublisher(cleanTitle, code, newPubSavedPath)
                                selectedPublisher = created
                                showAddPublisherModal = false
                            },
                            enabled = newPubName.isNotBlank() && !isProcessingLogo,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A))
                        ) {
                            Text("Yayını Kaydet", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * KİTAPLIĞIM VİTRİNİ - KİTAP KARTI:
 * PDF'in ilk sayfasından (veya yayın logosu ve renginden) oluşturulan şık bir Kitap Kapağı,
 * Yayın Logosu, Kitap Adı ve içinde kaç aktif soru olduğu rozetiyle dizilir.
 */
@Composable
private fun LibraryBookShowcaseCard(
    book: LibraryBookCardModel,
    onOpenBookTest: () -> Unit,
    onDeleteBook: () -> Unit
) {
    val coverBitmap = remember(book.coverImagePath) {
        if (book.coverImagePath.isNotBlank() && File(book.coverImagePath).exists()) {
            runCatching { BitmapFactory.decodeFile(book.coverImagePath) }.getOrNull()
        } else {
            null
        }
    }
    val primaryBrand = Color(book.publisherInfo.primaryColorHex)
    val secondaryBrand = Color(book.publisherInfo.secondaryColorHex)

    Surface(
        onClick = onOpenBookTest,
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_book_card_${book.key}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Üst Kitap Kapağı Alanı (PDF 1. Sayfa Önizlemesi veya Yayınevi Kapak Tasarımı)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(primaryBrand, secondaryBrand)
                        )
                    )
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = book.bookTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.15f),
                                        Color.Black.copy(alpha = 0.55f)
                                    )
                                )
                            )
                    )
                }

                // Sol Üst: Yayınevi Logosu
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    PublisherLogoEmblem(
                        publisher = book.publisherInfo,
                        size = 42.dp
                    )
                }

                // Sağ Üst: Aktif Soru Sayısı Rozeti + Sil Butonu
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF1E3A8A),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${book.activeQuestionCount} Aktif Soru",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    Surface(
                        onClick = onDeleteBook,
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.35f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kitabı Kaldır",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Alt Kısım: Kitap Kapağı Üzerinde Yayınevi Etiketi
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = book.publisherName.uppercase(),
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = book.subjectsSummary,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Kitap Adı ve Yayınevi Bilgisi
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = book.bookTitle,
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${book.publisherName} • Cevap Anahtarı Eşleşmeli",
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Alt Aksiyon Butonu: Testi Çöz
            Button(
                onClick = onOpenBookTest,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E3A8A),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Kitap Testini Çöz",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
