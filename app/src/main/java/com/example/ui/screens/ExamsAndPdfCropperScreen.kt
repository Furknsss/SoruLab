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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.util.StudioImageAndPdfEngine
import com.example.util.SoruLabQuestionZipImporter
import com.example.viewmodel.BatchPdfCropState
import com.example.viewmodel.ExtractedPdfQuestionItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PublisherLogoEmblem(
    publisher: YksPublisherInfo,
    size: Dp = 36.dp,
    overrideLogoPath: String = "",
    modifier: Modifier = Modifier
) {
    // Imported logos may be stored as absolute paths or file:// URIs.
    // Never show a random publisher's artwork if this question's image is missing.
    val overrideFilePath = overrideLogoPath.removePrefix("file://")
    val publisherFilePath = publisher.customLogoPath.removePrefix("file://")
    val effectivePath = overrideFilePath
        .takeIf { it.isNotBlank() && File(it).isFile }
        ?: publisherFilePath.takeIf { it.isNotBlank() && File(it).isFile }
        .orEmpty()
    val customBitmap = StudioImageAndPdfEngine.rememberCachedBitmap(effectivePath)

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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFFDBEAFE))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "PDF Soru Stüdyosu",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A),
                            fontSize = 15.sp
                        )
                        Text(
                            "$customQuestionsCount kayıtlı soru · PDF'den soru kırp ve incele",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }
                    Button(
                        onClick = onOpenPdfStudio,
                        modifier = Modifier.testTag("denemeler_open_pdf_studio"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White
                        )
                    ) {
                        Text("PDF Aç", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            // Three exam filters exceed a phone's content width. A clipped
            // AYT chip was unreachable on narrow devices; scroll the strip
            // horizontally without shrinking its touch targets.
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("denemeler_filter_strip"),
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
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) Color(0xFF0F172A) else Color.White,
        border = BorderStroke(1.dp, if (selected) Color(0xFF0F172A) else Color(0xFFE2E8F0)),
        shadowElevation = if (selected) 3.dp else 0.dp,
        // Put click semantics on the actual Material3 Surface rather than
        // only an outer modifier. Compose tests and accessibility both need
        // an unambiguous, stable hit target for these exam filters.
        modifier = Modifier.testTag(
            when {
                label.startsWith("TYT") -> "denemeler_filter_tyt"
                label.startsWith("AYT") -> "denemeler_filter_ayt"
                else -> "denemeler_filter_all"
            }
        )
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
private data class PendingPdfQuestionImport(
    val items: List<ExtractedPdfQuestionItem>,
    val publisherName: String,
    val publisherLogoUri: String,
    val bookTitle: String,
    val bookCoverPath: String
)

@Composable
fun SoruHavuzuAndPdfStudioView(
    customQuestions: List<CustomQuestionRecord>,
    customPublishers: List<YksPublisherInfo>,
    selectedTestLevel: TopicTestLevel,
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
        bookCoverPath: String,
        onSaved: (Boolean, String) -> Unit
    ) -> Unit,
    onDeleteLibraryBook: (List<String>) -> Unit,
    onClearAllData: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onBack: () -> Unit,
    onSolveBookInPractice: (CustomQuestionRecord) -> Unit = {},
    onSolveBookChunkInPractice: (List<CustomQuestionRecord>) -> Unit = { questions ->
        questions.firstOrNull()?.let { onSolveBookInPractice(it) }
    }
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allPublishers = remember(customPublishers) {
        SoruLabPublisherPresets.mergedPublishers(customPublishers)
    }

    var selectedPublisher by remember(allPublishers) {
        mutableStateOf(allPublishers.firstOrNull())
    }
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdfFileName by remember { mutableStateOf("") }

    var totalPdfPages by remember { mutableIntStateOf(0) }
    var isReadingPdf by remember { mutableStateOf(false) }
    var pdfSelectionError by remember { mutableStateOf<String?>(null) }
    var pendingCropReview by remember { mutableStateOf<PendingCropReview?>(null) }
    var showCropReview by remember { mutableStateOf(false) }
    var isSavingImportedBook by remember { mutableStateOf(false) }
    var isImportingQuestionZip by remember { mutableStateOf(false) }
    var zipBatchProgress by remember { mutableStateOf("") }
    var hasSavedReview by remember { mutableStateOf(false) }
    var importSaveError by remember { mutableStateOf<String?>(null) }
    var importSaveSuccess by remember { mutableStateOf<String?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var pendingDeleteBook by remember { mutableStateOf<LibraryBookCardModel?>(null) }

    var showAddPublisherModal by remember { mutableStateOf(false) }
    var newPubName by remember { mutableStateOf("") }
    var newPubBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var newPubSavedPath by remember { mutableStateOf("") }
    var isProcessingLogo by remember { mutableStateOf(false) }
    var logoProcessingError by remember { mutableStateOf<String?>(null) }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingLogo = true
            logoProcessingError = null
            newPubBitmap = null
            newPubSavedPath = ""
            scope.launch {
                try {
                    val savedPath = withContext(Dispatchers.IO) {
                        StudioImageAndPdfEngine.processPublisherLogoFloodFillAndTrim(
                            context = context,
                            logoUri = uri
                        )
                    }
                    if (savedPath.isNotBlank() && File(savedPath).exists()) {
                        val decoded = withContext(Dispatchers.IO) {
                            BitmapFactory.decodeFile(savedPath)
                        }
                        if (decoded != null) {
                            newPubBitmap = decoded
                            newPubSavedPath = savedPath
                        } else {
                            logoProcessingError = "Logo görseli açılamadı. Başka bir görsel deneyin."
                        }
                    } else {
                        logoProcessingError = "Logo işlenemedi. Daha net bir görsel seçip tekrar deneyin."
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    logoProcessingError = "Logo işlenirken hata oluştu. Başka bir görsel deneyin."
                } finally {
                    isProcessingLogo = false
                }
            }
        }
    }

    var selectedBookForTestPanel by remember { mutableStateOf<LibraryBookCardModel?>(null) }


    // Multi-file picker is safe: unpack and commit one book at a time.
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty() && !isSavingImportedBook && !isImportingQuestionZip) {
            onDismissError()
            importSaveError = null
            importSaveSuccess = null
            pendingCropReview = null // Do not retain a second large review list.
            isImportingQuestionZip = true
            isSavingImportedBook = true
            scope.launch {
                val completed = mutableListOf<String>()
                val errors = mutableListOf<String>()
                val seenPackDirectories = hashSetOf<String>()
                val selectedBookIds = hashMapOf<String, Pair<String, String>>()
                val chosen = uris.distinct()
                try {
                    for ((index, uri) in chosen.withIndex()) {
                        zipBatchProgress = "${index + 1}/${chosen.size} ZIP kontrol ediliyor…"
                        try {
                            val pack = withContext(Dispatchers.IO) {
                                SoruLabQuestionZipImporter.import(context, uri)
                            }
                            val bookId = pack.questions.firstOrNull()?.bookId.orEmpty()
                            val identity = pack.bookTitle.trim().lowercase() to
                                pack.publisher.trim().lowercase()
                            require(bookId.isNotBlank()) { "ZIP kitap kimliği eksik." }
                            val existing = customQuestions.firstOrNull { it.bookId == bookId }
                            require(existing == null || (
                                existing.sourceBookTitle.trim().equals(pack.bookTitle.trim(), true) &&
                                existing.publisherTag.trim().equals(pack.publisher.trim(), true)
                            )) {
                                "Kitap kimliği başka kitaba ait: ${pack.bookTitle}."
                            }
                            val batchIdentity = selectedBookIds.putIfAbsent(bookId, identity)
                            require(batchIdentity == null || batchIdentity == identity) {
                                "Farklı kitapların ZIP kimlikleri aynı: $bookId."
                            }
                            if (!seenPackDirectories.add(pack.directory.name)) {
                                errors.add("${pack.bookTitle}: Aynı ZIP iki kez seçilmiş, tekrar yüklenmedi.")
                                continue
                            }
                            zipBatchProgress = "${index + 1}/${chosen.size} · ${pack.bookTitle} kaydediliyor…"
                            val pub = SoruLabPublisherPresets.resolvePublisher(
                                pack.publisher, customPublishers
                            )
                            val (saved, message) = suspendCancellableCoroutine<Pair<Boolean, String>> { waiter ->
                                onSaveExtractedPdfBatch(
                                    pack.questions, pack.publisher, pub.customLogoPath,
                                    pack.bookTitle, pack.bookCoverPath
                                ) { success, report ->
                                    if (waiter.isActive) waiter.resume(success to report)
                                }
                            }
                            if (saved) {
                                completed.add("${pack.bookTitle}: ${pack.questions.size} soru")
                            } else {
                                errors.add("${pack.bookTitle}: $message")
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errors.add("ZIP ${index + 1}: ${e.message?.take(160) ?: "Açılamadı."}")
                        }
                    }
                    hasSavedReview = completed.isNotEmpty()
                    if (completed.isNotEmpty()) {
                        importSaveSuccess = "${completed.size} kitap ayrı kaydedildi: " +
                            completed.joinToString(" · ")
                    }
                    if (errors.isNotEmpty()) {
                        importSaveError = "${errors.size} ZIP aktarılmadı: " +
                            errors.take(3).joinToString(" · ")
                    }
                } finally {
                    zipBatchProgress = ""
                    isImportingQuestionZip = false
                    isSavingImportedBook = false
                }
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onDismissError()
            // A newly selected PDF must not reuse the previous review.
            if (!isSavingImportedBook) {
                pendingCropReview = null
                hasSavedReview = false
                importSaveError = null
                importSaveSuccess = null
            }
            selectedPdfUri = uri
            selectedPdfFileName = "PDF okunuyor…"
            totalPdfPages = 0
            pdfSelectionError = null
            isReadingPdf = true
            scope.launch {
                try {
                    val (resolvedName, count) = withContext(Dispatchers.IO) {
                        StudioImageAndPdfEngine.resolvePdfDisplayName(context, uri) to
                            StudioImageAndPdfEngine.getPdfPageCount(context, uri)
                    }
                    if (selectedPdfUri == uri) {
                        selectedPdfFileName = if (resolvedName.endsWith(".pdf", ignoreCase = true)) resolvedName else "$resolvedName.pdf"
                        totalPdfPages = count
                        if (count <= 0) pdfSelectionError = "Bu PDF açılamadı. Şifre korumasını kontrol edin veya başka bir dosya seçin."
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    if (selectedPdfUri == uri) pdfSelectionError = "PDF okunamadı. Dosyayı yeniden seçip deneyin."
                } finally {
                    if (selectedPdfUri == uri) isReadingPdf = false
                }
            }
        }
    }

    // Group all questions in the pool into "KİTAPLIĞIM" Book Cards off the UI thread
    var libraryBooks by remember { mutableStateOf<List<LibraryBookCardModel>>(emptyList()) }
    var searchBookQuery by remember { mutableStateOf("") }
    var selectedTopicInBookModal by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(customQuestions, customPublishers) {
        if (customQuestions.isEmpty()) {
            libraryBooks = emptyList()
        } else {
            val computed = withContext(Dispatchers.Default) {
                val pubCache = HashMap<String, YksPublisherInfo>(32)
                customQuestions
                    .groupBy { q ->
                        val pub = q.publisherTag.trim().ifBlank { "3D Yayınları" }
                        val book = q.bookOrTestName.trim().ifBlank { "$pub Soru Bankası" }
                        "${pub.lowercase()}|${book.lowercase()}|${q.bookId}"
                    }
                    .map { (groupKey, questionsInBook) ->
                        val first = questionsInBook.first()
                        val pubName = first.publisherTag.trim().ifBlank { "3D Yayınları" }
                        val resolvedPub = pubCache.getOrPut(pubName) {
                            SoruLabPublisherPresets.resolvePublisher(pubName, customPublishers)
                        }
                        val bookTitle = first.bookOrTestName.trim().ifBlank { "$pubName Soru Bankası" }
                        val coverPath = questionsInBook.firstOrNull { it.bookCoverPath.isNotBlank() }?.bookCoverPath
                            ?: first.croppedImagePath
                        val distinctSubjects = questionsInBook
                            .asSequence()
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
            libraryBooks = computed
        }
    }

    val filteredLibraryBooks = remember(libraryBooks, searchBookQuery) {
        val q = searchBookQuery.trim().lowercase()
        if (q.isBlank()) {
            libraryBooks
        } else {
            libraryBooks.filter {
                it.bookTitle.lowercase().contains(q) ||
                    it.publisherName.lowercase().contains(q) ||
                    it.subjectsSummary.lowercase().contains(q)
            }
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (batchCropState.progressPercent.coerceIn(0, 100)) / 100f,
        animationSpec = tween(durationMillis = 300),
        label = "pdf_upload_progress"
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF0EFF7)).testTag("pdf_cropper_studio"),
        contentAlignment = Alignment.TopCenter
    ) {
        val compact = maxWidth < 600.dp
        val libraryColumns = when { maxWidth < 600.dp -> 1; maxWidth < 900.dp -> 2; else -> 3 }
        val pageInset = if (compact) 16.dp else 28.dp
        LazyColumn(
            modifier = Modifier.widthIn(max = 1024.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = pageInset, end = pageInset, top = 16.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalIconButton(onClick = onBack, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kitaplıktan geri dön", tint = Color(0xFF25205E))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PDF Stüdyosu", color = Color(0xFF25205E), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("Kitabını seç. Sorularını hazırla.", color = Color(0xFF77738F), fontSize = 13.sp)
                        }
                        if (customQuestions.isNotEmpty()) {
                            IconButton(onClick = { showClearConfirmation = true }, enabled = !batchCropState.isAnalyzing) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Kitaplık verilerini temizle", tint = Color(0xFF77738F))
                            }
                        }
                    }
                    Surface(color = Color(0xFF25205E), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFACA7F5), modifier = Modifier.size(28.dp))
                                Text("Kendi soru kütüphanen", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                            }
                            Text("PDF sayfalarını sorulara ayır, kırpmaları incele ve seçtiklerini kitaplığına ekle.", color = Color(0xFFD3D0EB), fontSize = 13.sp, lineHeight = 20.sp)
                            Text("${libraryBooks.size} kitap   ·   ${customQuestions.size} soru", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("total_active_questions_counter"))
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
                    border = BorderStroke(1.dp, Color(0xFFE5E2F0)),
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (compact) 16.dp else 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {

                        // ZIP packets already include image crops, test IDs, and
                        // verified answer information. No phone-side PDF OCR needed.
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Hazır soru paketini ZIP'ten yükle",
                                color = Color(0xFF25205E),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                            Surface(
                                onClick = {
                                    zipPickerLauncher.launch(arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/octet-stream"
                                    ))
                                },
                                enabled = !isImportingQuestionZip &&
                                    !isSavingImportedBook && !batchCropState.isAnalyzing,
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF0FDF4),
                                border = BorderStroke(1.5.dp, Color(0xFF86EFAC)),
                                modifier = Modifier.fillMaxWidth()
                                    .heightIn(min = 74.dp)
                                    .testTag("import_question_zip_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderZip,
                                        contentDescription = null,
                                        tint = Color(0xFF15803D),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            "Birden Fazla ZIP Ekle",
                                            color = Color(0xFF14532D),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            "Birden fazla ZIP seç; her kitap ayrı ve sırayla kaydedilir",
                                            color = Color(0xFF166534),
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                            if (isImportingQuestionZip) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF15803D)
                                )
                                Text(
                                    zipBatchProgress.ifBlank { "ZIP dosyaları sırayla kaydediliyor…" },
                                    color = Color(0xFF166534),
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                "ZIP paketi bu sohbetten hazırlanabilir. PDF işleme ve OCR telefonda çalışmaz.",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        }

                        HorizontalDivider(color = Color(0xFFE2E8F0))

                        // 1. YAYINEVİ / LOGO SEÇİMİ
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "01  Yayınını seç",
                                color = Color(0xFF25205E),
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
                                if (allPublishers.isEmpty()) {
                                    Text(
                                        text = "Henüz yayın logosu eklenmedi. Devam etmek için kendi logonu ekle.",
                                        color = Color(0xFF64748B),
                                        fontSize = 12.sp
                                    )
                                }
                                allPublishers.forEach { pub ->
                                    val isSelected = selectedPublisher?.id == pub.id
                                    Surface(
                                        onClick = { selectedPublisher = pub },
                                        enabled = !batchCropState.isAnalyzing,
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF7F6FB),
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF346CFA) else Color(0xFFE5E2F0)
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
                                                color = if (isSelected) Color(0xFF346CFA) else Color(0xFF334155),
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
                                            tint = Color(0xFF346CFA),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "+ Yeni Yayın & Logo Ekle",
                                            color = Color(0xFF346CFA),
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
                                text = "02  PDF dosyanı ekle",
                                color = Color(0xFF25205E),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )

                            Surface(
                                onClick = { pdfPickerLauncher.launch("application/pdf") },
                                enabled = !batchCropState.isAnalyzing && !isSavingImportedBook,
                                shape = RoundedCornerShape(16.dp),
                                color = if (selectedPdfUri != null) Color(0xFFEFF6FF) else Color(0xFFF7F6FB),
                                border = BorderStroke(
                                    width = 1.5.dp,
                                    color = if (selectedPdfUri != null) Color(0xFF346CFA) else Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 88.dp)
                                    .testTag("select_pdf_button")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
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
                                            tint = if (selectedPdfUri != null) Color(0xFF346CFA) else Color(0xFF475569),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column {
                                            Text(
                                                text = if (selectedPdfUri != null) {
                                                    selectedPdfFileName
                                                } else {
                                                    "PDF seçmek için dokun"
                                                },
                                                color = if (selectedPdfUri != null) Color(0xFF346CFA) else Color(0xFF334155),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (selectedPdfUri != null && totalPdfPages > 0) {
                                                Text(
                                                    text = "$totalPdfPages sayfa · Kırpmaya hazır",
                                                    color = Color(0xFF1D4ED8),
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 11.5.sp
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selectedPdfUri != null) Color(0xFFDBEAFE) else Color(0xFFE5E2F0)
                                    ) {
                                        Text(
                                            text = if (selectedPdfUri != null) "PDF Değiştir" else "PDF Seç",
                                            color = if (selectedPdfUri != null) Color(0xFF346CFA) else Color(0xFF334155),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            if (isReadingPdf) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF346CFA))
                            }
                            pdfSelectionError?.let { message ->
                                Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            }
                            pendingCropReview?.let { review ->
                                OutlinedButton(
                                    onClick = { showCropReview = true },
                                    enabled = !isSavingImportedBook,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (hasSavedReview)
                                        "${review.items.size} soru kitaplıkta · Kırpmaları düzelt"
                                    else "${review.items.size} kırpma hazır · Kaydetmeyi yeniden dene")
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Ücretsiz · Cihaz içinde çalışır. PDF sayfaları buluta gönderilmez; internet, API anahtarı ve kota gerekmez.",
                                modifier = Modifier.testTag("pdf_offline_processing_info").padding(vertical = 8.dp),
                                color = Color(0xFF475569), fontSize = 12.sp, lineHeight = 17.sp
                            )

                            // TAM GENİŞLİKLİ FERAH "BAŞLAT VE SİSTEME AKTAR" BUTONU (ASLA EZİLMEZ)
                            Button(
                                onClick = {
                                    val uri = selectedPdfUri ?: return@Button
                                    val publisher = selectedPublisher ?: return@Button
                                    importSaveError = null
                                    importSaveSuccess = null
                                    hasSavedReview = false
                                    onStartFastParallelPdfExtraction(
                                        uri,
                                        selectedPdfFileName,
                                        publisher
                                    ) { extractedItems, coverPath, autoDetectedBookTitle ->
                                        if (extractedItems.isNotEmpty()) {
                                            // Import is committed automatically; review is optional.
                                            // Do NOT claim the book was imported before disk read-back.
                                            val review = PendingCropReview(
                                                extractedItems, publisher, autoDetectedBookTitle, coverPath
                                            )
                                            pendingCropReview = review
                                            showCropReview = false
                                            isSavingImportedBook = true
                                            onSaveExtractedPdfBatch(
                                                extractedItems, publisher.name,
                                                publisher.customLogoPath, autoDetectedBookTitle,
                                                coverPath
                                            ) { succeeded, message ->
                                                isSavingImportedBook = false
                                                if (succeeded) {
                                                    hasSavedReview = true
                                                    importSaveSuccess = message
                                                    selectedPdfUri = null
                                                    selectedPdfFileName = ""
                                                    totalPdfPages = 0
                                                } else {
                                                    importSaveError = message
                                                }
                                            }
                                        } else {
                                            importSaveError = "Bu PDF'den soru aktarımı tamamlanmadı. Kitap kaydedilmedi."
                                        }
                                    }
                                },
                                enabled = selectedPdfUri != null &&
                                    selectedPublisher != null && totalPdfPages > 0 && !isReadingPdf &&
                                    !isSavingImportedBook && !batchCropState.isAnalyzing,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF346CFA),
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
                                    text = when {
                                        batchCropState.isAnalyzing -> "Sorular hazırlanıyor…"
                                        isSavingImportedBook -> "Kitaplığa kaydediliyor…"
                                        else -> "Soruları kırp ve kitaplığa ekle"
                                    },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        if (isSavingImportedBook) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().testTag("pdf_import_saving_progress"),
                                color = Color(0xFF346CFA)
                            )
                            Text("Tarama bitti. Sorular kalıcı kitaplığa yazılıyor ve tekrar okunarak doğrulanıyor…",
                                color = Color(0xFF1D4ED8), fontSize = 13.sp)
                        }
                        importSaveSuccess?.let { message ->
                            Text(message, color = Color(0xFF166534), fontSize = 13.sp,
                                modifier = Modifier.testTag("pdf_import_saved_success"))
                        }
                        importSaveError?.let { message ->
                            Text(message, color = Color(0xFFB91C1C), fontSize = 13.sp,
                                modifier = Modifier.testTag("pdf_import_save_error"))
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
                                                "PDF hazırlanıyor; sayfalar ve cevap anahtarı incelenecek."
                                            },
                                            color = Color(0xFF346CFA),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = Color(0xFF346CFA)
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
                                        color = Color(0xFF346CFA),
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
                        AnimatedVisibility(
                                visible = !batchCropState.warningMessage.isNullOrBlank(),
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFFFFBEB),
                                    border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pdf_warning_alert_box")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color(0xFFB45309),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = batchCropState.warningMessage.orEmpty(),
                                            color = Color(0xFF92400E),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = Color(0xFF346CFA),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Kitaplığım",
                                color = Color(0xFF25205E),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFFEFF6FF),
                                border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                            ) {
                                Text(
                                    text = "${libraryBooks.size} kitap",
                                    color = Color(0xFF346CFA),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (libraryBooks.size >= 2) {
                            OutlinedTextField(
                                value = searchBookQuery,
                                onValueChange = { searchBookQuery = it },
                                placeholder = { Text("Kitap, yayın veya konu ara...", fontSize = 12.sp) },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (searchBookQuery.isNotBlank()) {
                                        IconButton(onClick = { searchBookQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Temizle",
                                                tint = Color(0xFF64748B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                            )
                        }
                    }
                }
            }

            if (filteredLibraryBooks.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE5E2F0)),
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
                                        tint = Color(0xFF346CFA),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (libraryBooks.isEmpty()) "Kitaplığınızda Henüz Kaynak Bulunmuyor" else "Aramaya Uygun Kitap Bulunamadı",
                                    color = Color(0xFF25205E),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Yayınını ve PDF dosyanı seç. Kırpmaları inceleyip kaydettiğinde soruların burada görünecek.",
                                    color = Color(0xFF64748B),
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }
            } else {
                val bookRows = filteredLibraryBooks.chunked(libraryColumns)
                items(
                    items = bookRows,
                    key = { row -> row.joinToString("|") { it.key } },
                    contentType = { "library_book_row" }
                ) { rowBooks ->
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
                                        selectedTopicInBookModal = null
                                        selectedBookForTestPanel = book
                                    },
                                    onDeleteBook = {
                                        pendingDeleteBook = book
                                    }
                                )
                            }
                        }
                        repeat(libraryColumns - rowBooks.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    pendingCropReview?.let { review ->
        PdfCropReviewDialog(
            review = review,
            visible = showCropReview,
            onClose = { showCropReview = false },
            onSave = { selectedItems ->
                if (!isSavingImportedBook) {
                    isSavingImportedBook = true
                    importSaveError = null
                    onSaveExtractedPdfBatch(
                        selectedItems, review.publisher.name, review.publisher.customLogoPath,
                        review.bookTitle, review.coverPath
                    ) { succeeded, message ->
                        isSavingImportedBook = false
                        if (succeeded) {
                            hasSavedReview = true
                            importSaveSuccess = message
                            showCropReview = false
                            pendingCropReview = null
                            selectedPdfUri = null
                            selectedPdfFileName = ""
                            totalPdfPages = 0
                        } else {
                            importSaveError = message
                        }
                    }
                }
            }
        )
    }
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Kitaplık verileri temizlensin mi?") },
            text = { Text("Kitaplar, sorular ve test geçmişi kaldırılacak. Bu işlem geri alınamaz.") },
            confirmButton = { TextButton(onClick = { showClearConfirmation = false; onClearAllData() }) { Text("Verileri temizle", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showClearConfirmation = false }) { Text("Vazgeç") } }
        )
    }
    pendingDeleteBook?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDeleteBook = null },
            title = { Text("Kitap kaldırılsın mı?") },
            text = { Text("“${book.bookTitle}” ve içindeki ${book.questions.size} soru kitaplığından kaldırılacak.") },
            confirmButton = { TextButton(onClick = {
                pendingDeleteBook = null
                selectedBookForTestPanel = null
                onDeleteLibraryBook(book.questions.map { it.id })
            }) { Text("Kitabı kaldır", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingDeleteBook = null }) { Text("Vazgeç") } }
        )
    }

    // =======================================================================
    // KİTAP KARTINA TIKLANDIĞINDA AÇILAN "TEST OLUŞTURMA PANELİ"
    // - Kullanıcı testlerin kaç soruluk olacağını (8, 10, 12, 16, 20 veya Tümünü Al) seçer.
    // - Testler doğrudan "[Konu Adı] - Test 1", "[Konu Adı] - Test 2" olarak listelenir.
    // =======================================================================
    val activeBookPanel = selectedBookForTestPanel
    if (activeBookPanel != null) {
        val sortedBookQuestions = remember(activeBookPanel.questions) {
            activeBookPanel.questions.sortedBy {
                if (it.questionNumberInBook > 0) it.questionNumberInBook else Int.MAX_VALUE
            }
        }
        val topicsInBook = remember(sortedBookQuestions) {
            sortedBookQuestions
                .groupBy { it.topicName.ifBlank { "Konu belirlenmedi" } }
                .map { (tName, list) -> tName to list.size }
                .sortedByDescending { it.second }
        }
        val filteredQuestionsForChunks = remember(sortedBookQuestions, selectedTopicInBookModal) {
            if (selectedTopicInBookModal == null) {
                sortedBookQuestions
            } else {
                sortedBookQuestions.filter {
                    if (selectedTopicInBookModal == "Konu belirlenmedi") {
                        it.topicName.isBlank()
                    } else {
                        it.topicName == selectedTopicInBookModal
                    }
                }
            }
        }
        val testChunks = remember(filteredQuestionsForChunks, selectedTestLevel) {
            automaticBookTestChunks(filteredQuestionsForChunks, selectedTestLevel)
        }

        Dialog(onDismissRequest = { selectedBookForTestPanel = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE5E2F0)),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
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
                            PublisherLogoEmblem(
                                publisher = activeBookPanel.publisherInfo,
                                size = 44.dp
                            )
                            Column {
                                Text(
                                    text = activeBookPanel.bookTitle,
                                    color = Color(0xFF25205E),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${activeBookPanel.subjectsSummary} • Toplam ${sortedBookQuestions.size} Soru",
                                    color = Color(0xFF2563EB),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = {
                                    pendingDeleteBook = activeBookPanel
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFEF2F2),
                                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                                modifier = Modifier.testTag("modal_delete_book_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Kitabı Kaldır",
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Kitabı Kaldır",
                                        color = Color(0xFFDC2626),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                            IconButton(onClick = { selectedBookForTestPanel = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color(0xFF64748B))
                            }
                        }
                    }

                    // Konu Filtresi (Çok konulu büyük kitaplarda konulara göre anında filtreleme)
                    if (topicsInBook.size > 1) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Kitaptaki Konular (${topicsInBook.size} Konu)",
                                color = Color(0xFF25205E),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val allSelected = selectedTopicInBookModal == null
                                Surface(
                                    onClick = { selectedTopicInBookModal = null },
                                    shape = RoundedCornerShape(50),
                                    color = if (allSelected) Color(0xFF1D4ED8) else Color(0xFFF1F5F9),
                                    border = BorderStroke(1.dp, if (allSelected) Color(0xFF1D4ED8) else Color(0xFFCBD5E1))
                                ) {
                                    Text(
                                        text = "Tüm Konular (${sortedBookQuestions.size})",
                                        color = if (allSelected) Color.White else Color(0xFF334155),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                                topicsInBook.forEach { (topicName, count) ->
                                    val isTopicSel = selectedTopicInBookModal == topicName
                                    Surface(
                                        onClick = { selectedTopicInBookModal = topicName },
                                        shape = RoundedCornerShape(50),
                                        color = if (isTopicSel) Color(0xFF1D4ED8) else Color(0xFFF1F5F9),
                                        border = BorderStroke(1.dp, if (isTopicSel) Color(0xFF1D4ED8) else Color(0xFFCBD5E1))
                                    ) {
                                        Text(
                                            text = "$topicName ($count)",
                                            color = if (isTopicSel) Color.White else Color(0xFF334155),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE5E2F0))

                    Text(
                        text = "${selectedTestLevel.displayTitle} • Otomatik testler (en fazla $AUTOMATIC_TEST_QUESTION_COUNT soru)",
                        color = Color(0xFF25205E),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp
                    )
                    if (testChunks.isEmpty()) {
                        Text(
                            text = "Bu kitapta seçili seviyeye uygun soru bulunamadı.",
                            color = Color(0xFF64748B),
                            fontSize = 13.sp
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            count = testChunks.size,
                            key = { chunkIdx -> "chunk_${selectedTopicInBookModal ?: "all"}_$chunkIdx" },
                            contentType = { "book_test_chunk_row" }
                        ) { chunkIdx ->
                            val chunkQuestions = testChunks[chunkIdx]
                            val firstInChunk = chunkQuestions.first()
                            val verifiedAnswerCount = chunkQuestions.count { it.answerKeyVerified }
                            val previewBitmap = StudioImageAndPdfEngine.rememberCachedBitmap(firstInChunk.croppedImagePath)
                            val topicTitle = firstInChunk.topicName.ifBlank { "Konu belirlenmedi" }
                            val cleanTestName = "$topicTitle - Test ${chunkIdx + 1}"

                            Surface(
                                onClick = {
                                    selectedBookForTestPanel = null
                                    onSolveBookChunkInPractice(chunkQuestions)
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF7F6FB),
                                border = BorderStroke(1.dp, Color(0xFFE5E2F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (previewBitmap != null) {
                                        Image(
                                            bitmap = previewBitmap.asImageBitmap(),
                                            contentDescription = "$cleanTestName önizlemesi",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(width = 56.dp, height = 68.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = cleanTestName,
                                            color = Color(0xFF25205E),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.5.sp
                                        )
                                        Text(
                                            text = "${firstInChunk.examType} ${firstInChunk.subjectName} • ${chunkQuestions.size} Soru • $verifiedAnswerCount doğrulanmış anahtar",
                                            color = Color(0xFF64748B),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.5.sp
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color(0xFF2563EB)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "Testi Çöz",
                                                color = Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
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
                border = BorderStroke(1.dp, Color(0xFFE5E2F0)),
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
                            color = Color(0xFF25205E),
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
                        color = Color(0xFFF7F6FB),
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
                                    .border(1.dp, Color(0xFFE5E2F0), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isProcessingLogo) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF346CFA),
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
                                        tint = Color(0xFF346CFA),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (newPubBitmap != null) "Logo Hazır (Değiştirmek İçin Dokunun)" else "Logo Görseli Seçin",
                                    color = Color(0xFF25205E),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = logoProcessingError
                                        ?: "Logo arka planı otomatik temizlenir ve hesabına yedeklenir.",
                                    color = if (logoProcessingError != null) Color(0xFFDC2626) else Color(0xFF64748B),
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
                            enabled = newPubName.isNotBlank() &&
                                newPubBitmap != null &&
                                newPubSavedPath.isNotBlank() &&
                                !isProcessingLogo,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF346CFA))
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
    val coverBitmap = StudioImageAndPdfEngine.rememberCachedBitmap(book.coverImagePath)
    val primaryBrand = Color(book.publisherInfo.primaryColorHex)
    val secondaryBrand = Color(book.publisherInfo.secondaryColorHex)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE5E2F0)),
        shadowElevation = 0.dp,
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
                    .border(1.dp, Color(0xFFE5E2F0), RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenBookTest)
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
                        color = Color(0xFF346CFA),
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
                        color = Color(0xFFDC2626),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("delete_book_top_${book.key}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Kitabı Kaldır",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenBookTest),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = book.bookTitle,
                    color = Color(0xFF25205E),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${book.questions.count { it.answerKeyVerified }} / ${book.questions.size} cevap doğrulandı",
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Alt Aksiyon Butonları: Test Oluştur & Çöz + Kitabı Kaldır
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpenBookTest,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF346CFA),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Test Oluştur & Çöz",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    onClick = onDeleteBook,
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("delete_book_btn_${book.key}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Kitabı Kaldır",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = "Kaldır",
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

private data class PendingCropReview(
    val items: List<ExtractedPdfQuestionItem>,
    val publisher: YksPublisherInfo,
    val bookTitle: String,
    val coverPath: String
)

/** Review the actual extracted crops before they enter the student's library. */
@Composable
private fun PdfCropReviewDialog(
    review: PendingCropReview,
    visible: Boolean,
    onClose: () -> Unit,
    onSave: (List<ExtractedPdfQuestionItem>) -> Unit
) {
    var currentIndex by remember(review) { mutableIntStateOf(0) }
    var excludedIndices by remember(review) { mutableStateOf(emptySet<Int>()) }
    if (!visible || review.items.isEmpty()) return
    var answerOverrides by remember(review) { mutableStateOf(emptyMap<Int, String>()) }
    var cropOverrides by remember(review) { mutableStateOf(emptyMap<Int, ExtractedPdfQuestionItem>()) }
    var correctingCrop by remember { mutableStateOf(false) }
    val original = cropOverrides[currentIndex] ?: review.items[currentIndex]
    val current = answerOverrides[currentIndex]?.let { answer ->
        original.copy(correctOption = answer, answerKeyMatched = answer.isNotBlank(), solutionSummary = original.solutionSummary + " • Kullanıcı tarafından doğrulandı")
    } ?: original
    val preview = StudioImageAndPdfEngine.rememberCachedBitmap(current.croppedImagePath)
    val included = currentIndex !in excludedIndices
    val selectedCount = review.items.size - excludedIndices.size
    val verifiedCount = review.items.indices.count { index ->
        answerOverrides[index]?.isNotBlank() ?: review.items[index].answerKeyMatched
    }
    if (correctingCrop) PdfCropCorrectionDialog(original,
        onDismiss = { correctingCrop = false },
        onApply = { cropOverrides = cropOverrides + (currentIndex to it); correctingCrop = false })
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = Color(0xFFF0EFF7),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.padding(12.dp).widthIn(max = 880.dp).fillMaxWidth().fillMaxHeight(0.94f)
                .testTag("pdf_crop_review_dialog")
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Kırpmaları incele", color = Color(0xFF25205E), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text(review.bookTitle, color = Color(0xFF77738F), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "İncelemeyi kapat") }
                }
                Text("${review.items.size} soru bulundu · $verifiedCount cevap eşleşti", color = Color(0xFF77738F), fontSize = 12.sp)
                Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Soru ${current.questionNumber.takeIf { it > 0 } ?: currentIndex + 1}", color = Color(0xFF25205E), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (current.pageIndex >= 0) Text("Sayfa ${current.pageIndex + 1}", color = Color(0xFF77738F), fontSize = 12.sp)
                        }
                        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                            if (preview != null) {
                                Image(preview.asImageBitmap(), contentDescription = "${currentIndex + 1}. sorunun kırpılmış önizlemesi", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                            } else {
                                Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Görsel önizleme açılamadı", color = Color(0xFFB45309), fontWeight = FontWeight.SemiBold)
                                    Text(current.questionText.ifBlank { "Bu kırpmayı seçimden çıkarabilir veya PDF dosyasını yeniden işleyebilirsin." }, color = Color(0xFF25205E), fontSize = 14.sp)
                                }
                            }
                        }
                        Text(listOf(current.examType, current.subjectName, current.topicName).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Konu incelemesi gerekiyor" }, color = Color(0xFF77738F), fontSize = 12.sp, maxLines = 2)
                        Text(
                            if (current.answerKeyMatched) "Cevap anahtarı: ${current.correctOption}" else "Cevap anahtarı eşleşmedi · Doğrulanmamış soru",
                            color = if (current.answerKeyMatched) Color(0xFF15803D) else Color(0xFFB45309), fontSize = 12.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { currentIndex-- }, enabled = currentIndex > 0, modifier = Modifier.testTag("crop_review_previous")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Önceki kırpma")
                    }
                    Text("${currentIndex + 1} / ${review.items.size}", color = Color(0xFF25205E), fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { currentIndex++ }, enabled = currentIndex < review.items.lastIndex, modifier = Modifier.testTag("crop_review_next")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Sonraki kırpma")
                    }
                }
                TextButton(onClick = { correctingCrop = true }, enabled = current.pdfFilePath.isNotBlank()) {
                    Text("Kaynak sayfayı aç ve kırpmayı düzelt")
                }
                Text("Cevabı yalnızca kaynak cevap anahtarıyla karşılaştırdıktan sonra doğrula.", color = Color(0xFF77738F), fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("A", "B", "C", "D", "E", "").forEach { answer ->
                        FilterChip(selected = current.answerKeyMatched && current.correctOption == answer,
                            onClick = { answerOverrides = answerOverrides + (currentIndex to answer) },
                            label = { Text(answer.ifBlank { "Belirsiz" }) })
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = included, onCheckedChange = { checked -> excludedIndices = if (checked) excludedIndices - currentIndex else excludedIndices + currentIndex })
                    Text("Bu soruyu kitaplığıma ekle", modifier = Modifier.weight(1f), color = Color(0xFF25205E), fontSize = 13.sp)
                    TextButton(onClick = { excludedIndices = if (selectedCount == review.items.size) review.items.indices.toSet() else emptySet() }) {
                        Text(if (selectedCount == review.items.size) "Seçimi kaldır" else "Tümünü seç", fontSize = 12.sp)
                    }
                }
                Button(
                    onClick = { onSave(review.items.mapIndexed { index, source ->
                        val item = cropOverrides[index] ?: source
                        answerOverrides[index]?.let { answer -> item.copy(correctOption = answer, answerKeyMatched = answer.isNotBlank(), solutionSummary = item.solutionSummary + " • Kullanıcı tarafından doğrulandı") } ?: item
                    }.filterIndexed { index, _ -> index !in excludedIndices }) },
                    enabled = selectedCount > 0,
                    shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF346CFA)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("save_reviewed_crops")
                ) {
                    Icon(Icons.Default.LibraryAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("$selectedCount soruyu kitaplığıma ekle", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

