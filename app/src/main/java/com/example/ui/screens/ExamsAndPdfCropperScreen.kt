package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.*
import com.example.viewmodel.AutoTagSuggestion
import com.example.viewmodel.ExtractedPdfQuestionItem
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Reusable Publisher Logo Emblem rendered both in Soru Havuzu and directly ABOVE the question number
 * in LiveSolvingBoardScreen. Supports both custom uploaded logo images and built-in YKS publisher emblems.
 */
@Composable
fun PublisherLogoEmblem(
    publisherName: String,
    customLogoUri: String = "",
    customPublishers: List<YksPublisherInfo> = emptyList(),
    sizeDp: Dp = 28.dp,
    showNameAlongside: Boolean = false
) {
    val publisher = remember(publisherName, customLogoUri, customPublishers) {
        SoruLabPublisherPresets.resolvePublisher(publisherName, customPublishers)
    }
    val effectiveLogoPath = customLogoUri.ifBlank { publisher.customLogoPath }
    val loadedLogoBitmap = remember(effectiveLogoPath) {
        if (effectiveLogoPath.isNotBlank()) {
            runCatching {
                val file = File(effectiveLogoPath)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }.getOrNull()
        } else {
            null
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(sizeDp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(publisher.primaryColorHex),
                            Color(publisher.secondaryColorHex)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (loadedLogoBitmap != null) {
                Image(
                    bitmap = loadedLogoBitmap.asImageBitmap(),
                    contentDescription = publisher.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = publisher.shortCode,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = (sizeDp.value * 0.34f).sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = Color.White,
                    maxLines = 1
                )
            }
        }

        if (showNameAlongside) {
            Text(
                text = publisher.name,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.5.sp
                ),
                color = Color(publisher.primaryColorHex),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DenemelerAndPdfStudioScreen(
    showPdfCropper: Boolean,
    customQuestions: List<CustomQuestionRecord>,
    customPublishers: List<YksPublisherInfo> = emptyList(),
    isAutoTagging: Boolean,
    batchStatusText: String? = null,
    onOpenPdfCropper: () -> Unit,
    onClosePdfCropper: () -> Unit,
    onStartMockExam: (MockExamItem, Boolean) -> Unit,
    onAddCustomPublisher: (String, String, String) -> YksPublisherInfo = { n, c, p ->
        YksPublisherInfo("custom", n, c, 0xFF1D4ED8, 0xFF0F172A, p)
    },
    onAutoTagCroppedQuestion: (Bitmap?, String, (AutoTagSuggestion) -> Unit) -> Unit,
    onExtractBatchFromPdfPage: (
        pageBitmap: Bitmap?,
        answerKeyBitmaps: List<Bitmap>,
        manualAnswerKeyText: String,
        publisherName: String,
        publisherLogoUri: String,
        examType: String,
        subjectName: String,
        onComplete: (List<ExtractedPdfQuestionItem>, Map<Int, String>) -> Unit
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onSaveCroppedQuestion: (
        examType: String,
        subjectName: String,
        topicName: String,
        difficulty: String,
        correctOption: String,
        questionText: String,
        publisherTag: String,
        aiSolutionSummary: String,
        cropAspectRatio: Double,
        targetLevel: String,
        croppedImagePath: String,
        publisherLogoUri: String,
        answerKeyVerified: Boolean,
        questionNumberInBook: Int
    ) -> Unit,
    onDeleteCustomQuestion: (String) -> Unit
) {
    if (showPdfCropper) {
        BackHandler { onClosePdfCropper() }
        SoruHavuzuAndPdfStudioView(
            customQuestions = customQuestions,
            customPublishers = customPublishers,
            isAutoTagging = isAutoTagging,
            batchStatusText = batchStatusText,
            onBack = onClosePdfCropper,
            onAddCustomPublisher = onAddCustomPublisher,
            onAutoTag = onAutoTagCroppedQuestion,
            onExtractBatchFromPdfPage = onExtractBatchFromPdfPage,
            onSaveQuestion = onSaveCroppedQuestion,
            onDeleteCustomQuestion = onDeleteCustomQuestion
        )
    } else {
        var filterExamType by remember { mutableStateOf<ExamType?>(null) }
        val filteredExams = remember(filterExamType) {
            if (filterExamType == null) SoruLabCatalog.mockExams
            else SoruLabCatalog.mockExams.filter { it.examType == filterExamType }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SoruLabBackground)
                .testTag("denemeler_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Denemeler",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = SoruLabInkBlack
                    )
                    Surface(
                        color = SoruLabSkyTint,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "${filteredExams.size} Deneme",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = SoruLabRoyalBlue,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = filterExamType == null,
                        onClick = { filterExamType = null },
                        label = { Text("Tümü") }
                    )
                    FilterChip(
                        selected = filterExamType == ExamType.TYT,
                        onClick = { filterExamType = ExamType.TYT },
                        label = { Text("TYT") }
                    )
                    FilterChip(
                        selected = filterExamType == ExamType.AYT,
                        onClick = { filterExamType = ExamType.AYT },
                        label = { Text("AYT") }
                    )
                }
            }

            if (filteredExams.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SoruLabBorderLight, RoundedCornerShape(20.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = SoruLabCobaltBlue,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = "Henüz Deneme Eklenmedi",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = SoruLabInkBlack
                            )
                            Text(
                                text = "Sistemde sahte/demo deneme bulunmamaktadır. Soru Havuzu & PDF Stüdyosu üzerinden kendi yayın PDF'lerinizi yükleyebilirsiniz.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SoruLabSlateMuted
                            )
                            Button(
                                onClick = onOpenPdfCropper,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SoruLabCobaltBlue)
                            ) {
                                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Soru Havuzu & PDF Stüdyosunu Aç", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }

            items(filteredExams, key = { it.id }) { exam ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SoruLabSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SoruLabBorderLight, RoundedCornerShape(20.dp))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PublisherLogoEmblem(
                                    publisherName = exam.publisher,
                                    customPublishers = customPublishers,
                                    sizeDp = 26.dp
                                )
                                Surface(
                                    color = if (exam.examType == ExamType.TYT) SoruLabSkyTint else SoruLabVioletLight,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${exam.examType.label} • ${exam.publisher}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (exam.examType == ExamType.TYT) SoruLabRoyalBlue else SoruLabViolet,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            DifficultyMeterBar(
                                position = when (exam.difficulty) {
                                    DifficultyLevel.KOLAY -> 0.2f
                                    DifficultyLevel.ORTA -> 0.52f
                                    DifficultyLevel.ZOR -> 0.85f
                                }
                            )
                        }

                        Text(
                            text = exam.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = SoruLabInkBlack
                        )
                        Text(
                            text = "${exam.questionCount} Soru • ${exam.durationMinutes} Dakika",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoruLabSlateMuted
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onStartMockExam(exam, false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("start_mock_exam_${exam.id}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SoruLabCobaltBlue)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Denemeyi Çöz", style = MaterialTheme.typography.labelLarge)
                            }
                            if (exam.isSolvedDefault) {
                                OutlinedButton(
                                    onClick = { onStartMockExam(exam, true) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "İncele (${String.format(Locale.US, "%.2f", exam.defaultNet)} Net)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = SoruLabCobaltBlue
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

/**
 * Full-Screen "Soru Havuzu & Akıllı PDF Soru + Cevap Anahtarı Eşleştirme Stüdyosu"
 * Accessible directly from Profile -> Soru Havuzu.
 */
@Composable
fun SoruHavuzuAndPdfStudioView(
    customQuestions: List<CustomQuestionRecord>,
    customPublishers: List<YksPublisherInfo>,
    isAutoTagging: Boolean,
    batchStatusText: String?,
    onBack: () -> Unit,
    onAddCustomPublisher: (String, String, String) -> YksPublisherInfo,
    onAutoTag: (Bitmap?, String, (AutoTagSuggestion) -> Unit) -> Unit,
    onExtractBatchFromPdfPage: (
        pageBitmap: Bitmap?,
        answerKeyBitmaps: List<Bitmap>,
        manualAnswerKeyText: String,
        publisherName: String,
        publisherLogoUri: String,
        examType: String,
        subjectName: String,
        onComplete: (List<ExtractedPdfQuestionItem>, Map<Int, String>) -> Unit
    ) -> Unit,
    onSaveQuestion: (
        examType: String,
        subjectName: String,
        topicName: String,
        difficulty: String,
        correctOption: String,
        questionText: String,
        publisherTag: String,
        aiSolutionSummary: String,
        cropAspectRatio: Double,
        targetLevel: String,
        croppedImagePath: String,
        publisherLogoUri: String,
        answerKeyVerified: Boolean,
        questionNumberInBook: Int
    ) -> Unit,
    onDeleteCustomQuestion: (String) -> Unit
) {
    val context = LocalContext.current
    val allPublishers = remember(customPublishers) {
        customPublishers + SoruLabPublisherPresets.defaultPublishers
    }
    var selectedPublisher by remember(allPublishers) {
        mutableStateOf(allPublishers.firstOrNull { it.id == "345" } ?: allPublishers.first())
    }

    // Custom Publisher creation modal state
    var showAddPublisherModal by remember { mutableStateOf(false) }
    var newPubName by remember { mutableStateOf("") }
    var newPubShortCode by remember { mutableStateOf("") }
    var newPubLogoPath by remember { mutableStateOf("") }

    // PDF state: supports multi-page navigation + automatic back-of-book Answer Key scanning
    var currentPdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfPageCount by remember { mutableIntStateOf(1) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var answerKeyPageIndex by remember { mutableIntStateOf(0) }
    var renderedPdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var answerKeyPageBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedPdfName by remember { mutableStateOf("Henüz PDF seçilmedi") }

    // Answer key string (automatically filled from the back pages of the PDF or editable manually)
    var answerKeyText by remember { mutableStateOf("") }
    var lastExtractedBatchCount by remember { mutableIntStateOf(0) }

    // Normalized crop box [0f..1f] for single-question fine-tuning
    var cropLeft by remember { mutableFloatStateOf(0.05f) }
    var cropTop by remember { mutableFloatStateOf(0.10f) }
    var cropRight by remember { mutableFloatStateOf(0.95f) }
    var cropBottom by remember { mutableFloatStateOf(0.84f) }

    // Categorization fields
    var examType by remember { mutableStateOf("TYT") }
    val subjectsForExam = remember(examType) {
        SoruLabCatalog.allSubjects.filter { it.examType.name.equals(examType, true) }
    }
    var subjectName by remember(examType) {
        mutableStateOf(subjectsForExam.firstOrNull()?.name ?: "Matematik")
    }
    val topicsForSubject = remember(examType, subjectName) {
        subjectsForExam.firstOrNull { it.name.equals(subjectName, true) }?.topics
            ?: subjectsForExam.firstOrNull()?.topics.orEmpty()
    }
    var topicName by remember(subjectName, examType) {
        mutableStateOf(topicsForSubject.firstOrNull()?.name ?: "Temel Kavramlar")
    }
    var difficulty by remember { mutableStateOf("Orta") }
    var targetLevel by remember { mutableStateOf("OSYM") }
    var questionNumberInBook by remember { mutableIntStateOf(1) }
    var correctOption by remember { mutableStateOf("A") }
    var questionText by remember { mutableStateOf("") }
    var aiSolutionSummary by remember { mutableStateOf("") }

    // Whenever questionNumberInBook or answerKeyText changes, automatically sync correctOption from the Answer Key!
    LaunchedEffect(questionNumberInBook, answerKeyText) {
        val regex = Regex("""\b$questionNumberInBook\s*[-:.)]?\s*([ABCDEabcde])\b""")
        val match = regex.find(answerKeyText)
        if (match != null) {
            correctOption = match.groupValues[1].uppercase()
        }
    }

    fun renderPdfPageAt(uri: Uri, pageIdx: Int): Bitmap? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return null
                    val safeIdx = pageIdx.coerceIn(0, renderer.pageCount - 1)
                    renderer.openPage(safeIdx).use { page ->
                        val bmp = Bitmap.createBitmap(
                            (page.width * 2).coerceIn(900, 2200),
                            (page.height * 2).coerceIn(1200, 3000),
                            Bitmap.Config.ARGB_8888
                        )
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    // Photo Picker for custom publisher logo
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val logoDir = File(context.filesDir, "publisher_logos")
                if (!logoDir.exists()) logoDir.mkdirs()
                val outFile = File(logoDir, "pub_logo_${System.currentTimeMillis()}.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                newPubLogoPath = outFile.absolutePath
            } catch (_: Exception) {
            }
        }
    }

    // Native Android PDF File Picker + Automatic End-of-Book Answer Key Page Scanner
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val total = renderer.pageCount.coerceAtLeast(1)
                        currentPdfUri = uri
                        pdfPageCount = total
                        currentPageIndex = 0
                        answerKeyPageIndex = (total - 1).coerceAtLeast(0)

                        // Render first test page
                        renderer.openPage(0).use { page ->
                            val bmp = Bitmap.createBitmap(
                                (page.width * 2).coerceIn(900, 2200),
                                (page.height * 2).coerceIn(1200, 3000),
                                Bitmap.Config.ARGB_8888
                            )
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            renderedPdfBitmap = bmp
                        }

                        // Automatically render the last 1..3 pages of the PDF book where Answer Keys reside
                        val keyPages = mutableListOf<Bitmap>()
                        val startKeyPage = (total - 3).coerceAtLeast(0)
                        for (idx in (total - 1) downTo startKeyPage) {
                            renderer.openPage(idx).use { kp ->
                                val kBmp = Bitmap.createBitmap(
                                    (kp.width * 2).coerceIn(900, 2000),
                                    (kp.height * 2).coerceIn(1200, 2600),
                                    Bitmap.Config.ARGB_8888
                                )
                                kBmp.eraseColor(android.graphics.Color.WHITE)
                                kp.render(kBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                keyPages.add(kBmp)
                            }
                        }
                        answerKeyPageBitmaps = keyPages
                        selectedPdfName = "Yüklenen PDF ($total Sayfa • Son Sayfa Cevap Anahtarı Hazır)"
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SoruLabBackground)
    ) {
        // Top Header Bar
        Surface(color = SoruLabSurface, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("pdf_cropper_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = SoruLabCobaltBlue)
                    }
                    Column {
                        Text(
                            text = "Soru Havuzu & Akıllı PDF Stüdyosu",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = SoruLabInkBlack
                        )
                        Text(
                            text = "Havuzda ${customQuestions.size} Soru • Cevap Anahtarı Otomatik Eşleştirme",
                            style = MaterialTheme.typography.labelSmall,
                            color = SoruLabSlateMuted
                        )
                    }
                }

                Button(
                    onClick = { pdfLauncher.launch(arrayOf("application/pdf")) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SoruLabCobaltBlue),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("pick_pdf_file_button")
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PDF Yükle", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // STEP 1: YAYIN VE LOGO SEÇİMİ (Sorunun üstünde görünecek yayın logosu)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "1. Yayın & Logo Seçimi",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = SoruLabInkBlack
                                )
                                Text(
                                    text = "Seçilen yayının logosu testte soru numarasının tam üstünde yer alır",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SoruLabSlateMuted
                                )
                            }
                            TextButton(
                                onClick = { showAddPublisherModal = true },
                                modifier = Modifier.testTag("add_custom_publisher_button")
                            ) {
                                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Yayın/Logo Ekle", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(allPublishers, key = { it.id }) { pub ->
                                val isSel = selectedPublisher.id == pub.id
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSel) Color(pub.primaryColorHex).copy(alpha = 0.12f) else Color(0xFFF8FAFC),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            width = if (isSel) 2.dp else 1.dp,
                                            color = if (isSel) Color(pub.primaryColorHex) else Color(0xFFE2E8F0),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { selectedPublisher = pub }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        PublisherLogoEmblem(
                                            publisherName = pub.name,
                                            customLogoUri = pub.customLogoPath,
                                            customPublishers = customPublishers,
                                            sizeDp = 30.dp
                                        )
                                        Column {
                                            Text(
                                                text = pub.name,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                text = if (isSel) "✓ Aktif Logo" else "Seç",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSel) Color(pub.primaryColorHex) else Color(0xFF64748B)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // STEP 2: KİTAP SONU CEVAP ANAHTARI EŞLEŞTİRME PANELİ
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FactCheck,
                                    contentDescription = null,
                                    tint = SoruLabEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "2. PDF Son Sayfa Cevap Anahtarı Eşleştirme",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = SoruLabInkBlack
                                    )
                                    Text(
                                        text = "Kitabın son sayfalarındaki cevap anahtarı soru numaralarıyla birebir eşleştirilir",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SoruLabSlateMuted
                                    )
                                }
                            }
                            Surface(
                                color = SoruLabEmeraldLight,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "%100 Doğru Şık",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color(0xFF065F46),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (pdfPageCount > 1 && currentPdfUri != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Cevap Anahtarı Sayfası: Sayfa ${answerKeyPageIndex + 1} / $pdfPageCount",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = SoruLabInkBlack
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val nextIdx = (answerKeyPageIndex - 1).coerceAtLeast(0)
                                            answerKeyPageIndex = nextIdx
                                            currentPdfUri?.let { u ->
                                                renderPdfPageAt(u, nextIdx)?.let { bmp ->
                                                    answerKeyPageBitmaps = listOf(bmp)
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("-1 Sayfa", style = MaterialTheme.typography.labelSmall)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val lastIdx = (pdfPageCount - 1).coerceAtLeast(0)
                                            answerKeyPageIndex = lastIdx
                                            currentPdfUri?.let { u ->
                                                renderPdfPageAt(u, lastIdx)?.let { bmp ->
                                                    answerKeyPageBitmaps = listOf(bmp)
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("En Son Sayfa", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = answerKeyText,
                            onValueChange = { answerKeyText = it },
                            label = { Text("Cevap Anahtarı Tablosu (PDF Sonundan Otomatik veya Elle)") },
                            placeholder = { Text("Örn: 1-A 2-D 3-B 4-E 5-C 6-D ...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("answer_key_input_field"),
                            singleLine = false,
                            maxLines = 3
                        )
                    }
                }
            }

            // STEP 3: TEK TIKLA OTOMATİK SORU KIRPMA, ETİKETLEME VE HAVUZA AKTARMA
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "3. Test Sayfası Önizleme & Otomatik Kırpıcı",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = SoruLabInkBlack
                                )
                                Text(
                                    text = selectedPdfName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SoruLabCobaltBlue
                                )
                            }

                            if (pdfPageCount > 1 && currentPdfUri != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (currentPageIndex > 0) {
                                                currentPageIndex -= 1
                                                currentPdfUri?.let { u ->
                                                    renderPdfPageAt(u, currentPageIndex)?.let { renderedPdfBitmap = it }
                                                }
                                            }
                                        },
                                        enabled = currentPageIndex > 0
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Önceki Sayfa")
                                    }
                                    Text(
                                        text = "${currentPageIndex + 1}/$pdfPageCount",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    IconButton(
                                        onClick = {
                                            if (currentPageIndex < pdfPageCount - 1) {
                                                currentPageIndex += 1
                                                currentPdfUri?.let { u ->
                                                    renderPdfPageAt(u, currentPageIndex)?.let { renderedPdfBitmap = it }
                                                }
                                            }
                                        },
                                        enabled = currentPageIndex < pdfPageCount - 1
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Sonraki Sayfa")
                                    }
                                }
                            }
                        }

                        // Interactive PDF Page Preview + Crop Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(245.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, SoruLabBorderLight, RoundedCornerShape(14.dp))
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val dx = dragAmount.x / size.width.toFloat().coerceAtLeast(1f)
                                        val dy = dragAmount.y / size.height.toFloat().coerceAtLeast(1f)
                                        val posX = change.position.x / size.width.toFloat().coerceAtLeast(1f)
                                        val posY = change.position.y / size.height.toFloat().coerceAtLeast(1f)

                                        if (posX < (cropLeft + cropRight) / 2f) {
                                            cropLeft = (cropLeft + dx).coerceIn(0.02f, cropRight - 0.2f)
                                        } else {
                                            cropRight = (cropRight + dx).coerceIn(cropLeft + 0.2f, 0.98f)
                                        }
                                        if (posY < (cropTop + cropBottom) / 2f) {
                                            cropTop = (cropTop + dy).coerceIn(0.02f, cropBottom - 0.2f)
                                        } else {
                                            cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.2f, 0.98f)
                                        }
                                    }
                                }
                        ) {
                            if (renderedPdfBitmap != null) {
                                Image(
                                    bitmap = renderedPdfBitmap!!.asImageBitmap(),
                                    contentDescription = "PDF Sayfası",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val leftPx = cropLeft * size.width
                                    val topPx = cropTop * size.height
                                    val widthPx = (cropRight - cropLeft) * size.width
                                    val heightPx = (cropBottom - cropTop) * size.height

                                    drawRect(
                                        color = Color(0xFF2563EB).copy(alpha = 0.09f),
                                        topLeft = Offset(leftPx, topPx),
                                        size = Size(widthPx, heightPx)
                                    )
                                    drawRect(
                                        color = Color(0xFF2563EB),
                                        topLeft = Offset(leftPx, topPx),
                                        size = Size(widthPx, heightPx),
                                        style = Stroke(
                                            width = 2.4.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                                        )
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.UploadFile,
                                        contentDescription = null,
                                        tint = SoruLabCobaltBlue,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Henüz PDF Yüklenmedi",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = SoruLabInkBlack
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Sağ üstteki 'PDF Yükle' butonuna basarak yayın PDF'ini seçin. Son sayfadaki cevap anahtarı otomatik taranacaktır.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SoruLabSlateMuted
                                    )
                                }
                            }
                        }

                        // Primary Automated Batch Button: AI Crops All Questions on Page + Matches Answer Key + Saves to Pool
                        Button(
                            onClick = {
                                onExtractBatchFromPdfPage(
                                    renderedPdfBitmap,
                                    answerKeyPageBitmaps,
                                    answerKeyText,
                                    selectedPublisher.name,
                                    selectedPublisher.customLogoPath,
                                    examType,
                                    subjectName
                                ) { extractedItems, syncedAnswerKeyMap ->
                                    if (syncedAnswerKeyMap.isNotEmpty()) {
                                        answerKeyText = syncedAnswerKeyMap.entries
                                            .sortedBy { it.key }
                                            .joinToString(" ") { "${it.key}-${it.value}" }
                                    }
                                    extractedItems.forEach { item ->
                                        onSaveQuestion(
                                            item.examType,
                                            item.subjectName,
                                            item.topicName,
                                            item.difficulty,
                                            item.correctOption,
                                            item.questionText,
                                            item.publisherName,
                                            item.solutionSummary,
                                            item.cropAspectRatio,
                                            item.targetLevel,
                                            item.croppedImagePath,
                                            item.publisherLogoUri,
                                            item.answerKeyMatched,
                                            item.questionNumber
                                        )
                                    }
                                    lastExtractedBatchCount = extractedItems.size
                                    extractedItems.firstOrNull()?.let { first ->
                                        examType = first.examType
                                        subjectName = first.subjectName
                                        topicName = first.topicName
                                        difficulty = first.difficulty
                                        targetLevel = first.targetLevel
                                        correctOption = first.correctOption
                                        questionNumberInBook = first.questionNumber
                                        questionText = first.questionText
                                        aiSolutionSummary = first.solutionSummary
                                    }
                                }
                            },
                            enabled = !isAutoTagging && renderedPdfBitmap != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auto_extract_pdf_page_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                        ) {
                            if (isAutoTagging) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = batchStatusText ?: "AI Soruları Kırpıyor & Cevap Anahtarıyla Eşleştiriyor...",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            } else {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sayfadaki Soruları Otomatik Kırp, Cevap Anahtarıyla Eşleştir & Havuza Ekle",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
                                )
                            }
                        }

                        if (lastExtractedBatchCount > 0) {
                            Surface(
                                color = SoruLabEmeraldLight,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SoruLabEmerald, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "$lastExtractedBatchCount soru (${selectedPublisher.name} logosu ve cevap anahtarı eşleşmesiyle) arka plan soru havuzuna eklendi!",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF065F46)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // STEP 4: DETAYLI ETİKET KONTROLÜ & TEKLİ SORU EKLEME (Ders / Konu / Gerçek Seviye / Cevap Anahtarı Şıkkı)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "4. Etiket, Gerçek Seviye & Cevap Anahtarı Doğrulaması",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = SoruLabInkBlack
                        )

                        // TYT / AYT + Soru No
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("TYT", "AYT").forEach { ex ->
                                    FilterChip(
                                        selected = examType == ex,
                                        onClick = { examType = ex },
                                        label = { Text(ex) }
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Soru No:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                IconButton(
                                    onClick = { if (questionNumberInBook > 1) questionNumberInBook -= 1 },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Surface(
                                    color = SoruLabSkyTint,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "$questionNumberInBook",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                        color = SoruLabRoyalBlue,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { questionNumberInBook += 1 },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Subject Selector
                        Text("Ders:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(subjectsForExam, key = { it.id }) { subj ->
                                FilterChip(
                                    selected = subjectName.equals(subj.name, ignoreCase = true),
                                    onClick = { subjectName = subj.name },
                                    label = { Text(subj.name) }
                                )
                            }
                        }

                        // Topic Selector (All real curriculum topics of the selected subject)
                        Text("Konu (${topicsForSubject.size} Müfredat Konusu):", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(topicsForSubject, key = { it.id }) { top ->
                                FilterChip(
                                    selected = topicName.equals(top.name, ignoreCase = true),
                                    onClick = { topicName = top.name },
                                    label = { Text(top.name) }
                                )
                            }
                        }

                        // Real Difficulty & 6-Level Wheel Category
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Gerçek Zorluk:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Kolay", "Orta", "Zor").forEach { diff ->
                                    FilterChip(
                                        selected = difficulty == diff,
                                        onClick = { difficulty = diff },
                                        label = { Text(diff) }
                                    )
                                }
                            }
                        }

                        Text("Seviye Çarkı Kategorisi:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val wheelLevels = TopicTestLevel.entries.filter { it != TopicTestLevel.IKIZ }
                            items(wheelLevels, key = { it.name }) { lvl ->
                                FilterChip(
                                    selected = targetLevel == lvl.name,
                                    onClick = { targetLevel = lvl.name },
                                    label = { Text(lvl.shortTag) }
                                )
                            }
                        }

                        // Verified Answer Key Option A-E
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Doğru Şık (Cevap Anahtarı - Soru $questionNumberInBook):",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Kitap sonundaki cevap anahtarından otomatik eşleşti",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SoruLabEmerald
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("A", "B", "C", "D", "E").forEach { opt ->
                                    val active = correctOption == opt
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (active) SoruLabCobaltBlue else Color(0xFFF1F5F9))
                                            .clickable { correctOption = opt }
                                            .testTag("crop_opt_$opt"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = opt,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = if (active) Color.White else SoruLabInkBlack
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = questionText,
                            onValueChange = { questionText = it },
                            label = { Text("Soru Metni / Özeti") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("crop_question_text_input")
                        )

                        Button(
                            onClick = {
                                val wRatio = (cropRight - cropLeft).coerceAtLeast(0.2f)
                                val hRatio = (cropBottom - cropTop).coerceAtLeast(0.2f)
                                val savedPath = if (renderedPdfBitmap != null) {
                                    try {
                                        val dir = File(context.filesDir, "question_pool_crops")
                                        if (!dir.exists()) dir.mkdirs()
                                        val src = renderedPdfBitmap!!
                                        val x = (cropLeft * src.width).toInt().coerceIn(0, src.width - 10)
                                        val y = (cropTop * src.height).toInt().coerceIn(0, src.height - 10)
                                        val w = ((cropRight - cropLeft) * src.width).toInt().coerceIn(10, src.width - x)
                                        val h = ((cropBottom - cropTop) * src.height).toInt().coerceIn(10, src.height - y)
                                        val cropped = Bitmap.createBitmap(src, x, y, w, h)
                                        val f = File(dir, "crop_${System.currentTimeMillis()}_q$questionNumberInBook.png")
                                        FileOutputStream(f).use { fos ->
                                            cropped.compress(Bitmap.CompressFormat.PNG, 92, fos)
                                        }
                                        f.absolutePath
                                    } catch (_: Exception) {
                                        ""
                                    }
                                } else {
                                    ""
                                }

                                onSaveQuestion(
                                    examType,
                                    subjectName,
                                    topicName,
                                    difficulty,
                                    correctOption,
                                    questionText,
                                    selectedPublisher.name,
                                    aiSolutionSummary,
                                    (wRatio / hRatio).toDouble(),
                                    targetLevel,
                                    savedPath,
                                    selectedPublisher.customLogoPath,
                                    true,
                                    questionNumberInBook
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("save_cropped_question_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SoruLabCobaltBlue)
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Seçili Soruyu (${selectedPublisher.shortCode} Logosuyla) Soru Havuzuna Kaydet",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // COMPACT BACKGROUND POOL SUMMARY (Soruların tamamını listelemek yerine temiz havuz özeti)
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Arka Plan Soru Havuzu Durumu",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White
                            )
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.22f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "${customQuestions.size} Aktif Soru",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF6EE7B7),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (customQuestions.isEmpty()) {
                            Text(
                                text = "Havuzunuza eklediğiniz sorular ekran kalabalığı yapmadan arka planda ders, konu, gerçek zorluk, cevap anahtarı ve yayın logosuyla saklanır.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                        } else {
                            val byTopic = remember(customQuestions) {
                                customQuestions.groupBy { "${it.subjectName} • ${it.topicName}" }
                            }
                            byTopic.entries.take(6).forEach { (topicKey, list) ->
                                val pubSample = list.first().publisherTag
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        PublisherLogoEmblem(
                                            publisherName = pubSample,
                                            customPublishers = customPublishers,
                                            sizeDp = 22.dp
                                        )
                                        Text(
                                            text = topicKey,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "${list.size} Soru (Cevap Anahtarlı)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Custom Publisher & Logo Dialog
    if (showAddPublisherModal) {
        AlertDialog(
            onDismissRequest = { showAddPublisherModal = false },
            title = {
                Text(
                    text = "Yeni Yayın & Logo Ekle",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newPubName,
                        onValueChange = { newPubName = it },
                        label = { Text("Yayın Adı (Örn: Barış Yayınları)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPubShortCode,
                        onValueChange = { newPubShortCode = it.take(4) },
                        label = { Text("Kısa Rozet Kodu (Örn: BRŞ)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            logoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (newPubLogoPath.isNotBlank()) "✓ Logo Görseli Seçildi" else "Galeriden Yayın Logosu Seç"
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPubName.isNotBlank()) {
                            val created = onAddCustomPublisher(newPubName, newPubShortCode, newPubLogoPath)
                            selectedPublisher = created
                            newPubName = ""
                            newPubShortCode = ""
                            newPubLogoPath = ""
                            showAddPublisherModal = false
                        }
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPublisherModal = false }) {
                    Text("İptal")
                }
            }
        )
    }
}
