package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.QuestionVisualType
import com.example.data.YksPublisherInfo
import com.example.data.YksQuestion
import com.example.ui.theme.*
import com.example.viewmodel.ActiveSolvingSession
import com.example.viewmodel.BoardViewMode
import com.example.viewmodel.DrawStroke
import com.example.viewmodel.DrawingTool
import com.example.viewmodel.TwinQuestionModalData
import java.io.File
import java.util.Locale

private val Navy345ToolbarColor = Color(0xFF16327A)

@Composable
fun LiveSolvingBoardScreen(
    session: ActiveSolvingSession,
    strokes: List<DrawStroke>,
    activeTool: DrawingTool,
    selectedStrokeSizeIndex: Int,
    selectedPenColorHex: Long,
    customPublishers: List<YksPublisherInfo> = emptyList(),
    onExitSession: () -> Unit,
    onToggleViewMode: () -> Unit,
    onSelectTool: (DrawingTool) -> Unit,
    onSelectStrokeSize: (Int) -> Unit,
    onSelectPenColor: (Long) -> Unit,
    onAddStroke: (List<Offset>) -> Unit,
    onClearCanvas: () -> Unit,
    onSelectQuestionIndex: (Int) -> Unit,
    onSelectOption: (Int, String) -> Unit,
    onToggleOpticalForm: () -> Unit,
    onToggleStatsDrawer: () -> Unit,
    onToggleQuestionMap: () -> Unit,
    onFinishTest: () -> Unit,
    onGoToStatsTab: () -> Unit,
    onAskAiForQuestion: (YksQuestion) -> Unit = {},
    onGenerateTwinQuestion: (YksQuestion, (TwinQuestionModalData) -> Unit) -> Unit = { _, _ -> },
    onGenerateSolutionForQuestion: (YksQuestion, (String) -> Unit) -> Unit = { _, _ -> },
    onAddQuestionToKumbara: (YksQuestion) -> Unit = {}
) {
    BackHandler { onExitSession() }

    var showColorPalettePopup by remember { mutableStateOf(false) }
    var boardZoomScale by remember { mutableFloatStateOf(1f) }
    var boardPanOffset by remember { mutableStateOf(Offset.Zero) }

    // Review Mode 3-Dot Menu ("İkiz Soru" & "Soru Çözümü") Modal States
    var activeSolutionModalQuestion by remember { mutableStateOf<YksQuestion?>(null) }
    var activeSolutionText by remember { mutableStateOf("") }
    var isSolutionLoading by remember { mutableStateOf(false) }

    var activeTwinModalQuestion by remember { mutableStateOf<YksQuestion?>(null) }
    var activeTwinData by remember { mutableStateOf<TwinQuestionModalData?>(null) }
    var isTwinLoading by remember { mutableStateOf(false) }

    val currentQuestion = session.testItem.questions.getOrElse(session.currentQuestionIndex) {
        session.testItem.questions.first()
    }

    val formattedTimer = remember(session.elapsedSeconds) {
        val mins = session.elapsedSeconds / 60
        val secs = session.elapsedSeconds % 60
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    fun openSolutionModal(question: YksQuestion) {
        activeSolutionModalQuestion = question
        val existingSol = question.stepByStepSolution.trim()
        val needsAiGeneration = existingSol.isBlank() ||
            existingSol.startsWith("Kitap cevap anahtarına göre") ||
            existingSol.startsWith("Doğru cevap")
        if (!needsAiGeneration) {
            activeSolutionText = existingSol
            isSolutionLoading = false
        } else {
            activeSolutionText = ""
            isSolutionLoading = true
            onGenerateSolutionForQuestion(question) { generatedText ->
                activeSolutionText = generatedText
                isSolutionLoading = false
            }
        }
    }

    fun openTwinQuestionModal(question: YksQuestion) {
        activeTwinModalQuestion = question
        activeTwinData = null
        isTwinLoading = true
        onGenerateTwinQuestion(question) { twinData ->
            activeTwinData = twinData
            isTwinLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F6FB))
            .testTag("live_solving_board_root")
    ) {
        // =======================================================================
        // 1. ÜST BEYAZ BAR:
        //    - Solda Geri oku
        //    - Ortada Test Başlığı
        //    - Sağda:
        //      * Aktif Test Modunda: mavi hap buton "⚑ Testi Bitir"
        //      * Değerlendirme Modunda: mavi hap buton "▸ Başarı Durumuna Git"
        // =======================================================================
        Surface(
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onExitSession,
                    modifier = Modifier.testTag("board_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = Color(0xFF16327A)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = session.testItem.fullTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color(0xFF0F172A),
                        maxLines = 1
                    )
                    if (session.viewMode == BoardViewMode.SINGLE_QUESTION) {
                        Text(
                            text = "Soru ${currentQuestion.number} / ${session.testItem.questions.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                if (session.isReviewMode) {
                    // Değerlendirme Modu: Sağ üstte mavi hap buton "▸ Başarı Durumuna Git"
                    Button(
                        onClick = onGoToStatsTab,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1D4ED8),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("basari_durumuna_git_button")
                    ) {
                        Text(
                            text = "▸ Başarı Durumuna Git",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                    }
                } else {
                    // Aktif Test Modu: Sağ üstte mavi hap buton "⚑ Testi Bitir"
                    Button(
                        onClick = onFinishTest,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1D4ED8),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("top_testi_bitir_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Flag,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Testi Bitir",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                    }
                }
            }
        }

        // =======================================================================
        // 2. ORTA TEST SAYFASI İÇERİĞİ + SAĞ KENAR DİKEY SEKME ("Optik Form" / "İstatistikler")
        // =======================================================================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (session.viewMode == BoardViewMode.FULL_PAGE) {
                FullPageWorksheetCanvas(
                    questions = session.testItem.questions,
                    selectedAnswers = session.selectedAnswers,
                    isReviewMode = session.isReviewMode,
                    strokes = strokes,
                    activeTool = activeTool,
                    selectedPenColorHex = selectedPenColorHex,
                    customPublishers = customPublishers,
                    zoomScale = boardZoomScale,
                    panOffset = boardPanOffset,
                    onUpdateZoomAndPan = { newScale, newPan ->
                        boardZoomScale = newScale
                        boardPanOffset = newPan
                    },
                    onAddStroke = onAddStroke,
                    onTwinQuestionClick = { q -> openTwinQuestionModal(q) },
                    onSolutionClick = { q -> openSolutionModal(q) },
                    onAddToKumbaraClick = { q -> onAddQuestionToKumbara(q) }
                )
            } else {
                SingleQuestionScratchpadView(
                    question = currentQuestion,
                    questionIndex = session.currentQuestionIndex,
                    totalQuestions = session.testItem.questions.size,
                    selectedOption = session.selectedAnswers[currentQuestion.number],
                    isReviewMode = session.isReviewMode,
                    strokes = strokes,
                    activeTool = activeTool,
                    selectedPenColorHex = selectedPenColorHex,
                    customPublishers = customPublishers,
                    onAddStroke = onAddStroke,
                    onSelectOption = { opt -> onSelectOption(currentQuestion.number, opt) },
                    onPrevQuestion = {
                        if (session.currentQuestionIndex > 0) {
                            onSelectQuestionIndex(session.currentQuestionIndex - 1)
                        }
                    },
                    onNextQuestion = {
                        if (session.currentQuestionIndex < session.testItem.questions.lastIndex) {
                            onSelectQuestionIndex(session.currentQuestionIndex + 1)
                        }
                    },
                    onTwinQuestionClick = { openTwinQuestionModal(currentQuestion) },
                    onSolutionClick = { openSolutionModal(currentQuestion) },
                    onAddToKumbaraClick = { onAddQuestionToKumbara(currentQuestion) }
                )
            }

            // Sol Kenar Sekmesi: Tekli Soru Modunda "Soru Haritası"
            if (session.viewMode == BoardViewMode.SINGLE_QUESTION) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedVisibility(
                        visible = session.isQuestionMapDrawerOpen,
                        enter = slideInHorizontally { -it },
                        exit = slideOutHorizontally { -it }
                    ) {
                        Surface(
                            color = Color.White,
                            shadowElevation = 12.dp,
                            shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                            modifier = Modifier
                                .width(230.dp)
                                .fillMaxHeight(0.82f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Soru Haritası",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(session.testItem.questions.size) { idx ->
                                        val q = session.testItem.questions[idx]
                                        val ans = session.selectedAnswers[q.number]
                                        val isCurrent = idx == session.currentQuestionIndex
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isCurrent) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
                                                )
                                                .clickable { onSelectQuestionIndex(idx) }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${q.number}. Soru",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color(0xFF0F172A)
                                            )
                                            Surface(
                                                color = if (ans != null) Color(0xFF1D4ED8) else Color.White,
                                                shape = CircleShape
                                            ) {
                                                Text(
                                                    text = ans ?: "Boş",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = if (ans != null) Color.White else Color(0xFF64748B),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        color = Color(0xFF1D4ED8),
                        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .clickable { onToggleQuestionMap() }
                            .testTag("soru_haritasi_tab")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Apps,
                                contentDescription = "Soru Haritası",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            "Soru Haritası".forEach { ch ->
                                Text(
                                    text = ch.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        lineHeight = 11.sp
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // ===================================================================
            // SAĞ KENAR DİKEY SEKME & ÇEKMECE:
            // - Aktif Test Modunda: Dikey mavi "⠿ Optik Form" kulakçığı
            // - Değerlendirme Modunda: Dikey mavi "◔ İstatistikler" kulakçığı
            // ===================================================================
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF1D4ED8),
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .clickable {
                            if (session.isReviewMode) onToggleStatsDrawer() else onToggleOpticalForm()
                        }
                        .testTag("right_drawer_handle")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (session.isReviewMode) Icons.Filled.PieChart else Icons.Filled.Apps,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val tabLabel = if (session.isReviewMode) "İstatistikler" else "Optik Form"
                        tabLabel.forEach { ch ->
                            Text(
                                text = ch.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp,
                                    lineHeight = 11.sp
                                ),
                                color = Color.White
                            )
                        }
                    }
                }

                // Optik Form Çekmecesi (Aktif Çözüm Modunda)
                AnimatedVisibility(
                    visible = !session.isReviewMode && session.isOpticalFormDrawerOpen,
                    enter = slideInHorizontally { it },
                    exit = slideOutHorizontally { it }
                ) {
                    OpticalFormDrawerContent(
                        questions = session.testItem.questions,
                        selectedAnswers = session.selectedAnswers,
                        onSelectOption = onSelectOption,
                        onFinishTest = onFinishTest
                    )
                }

                // İstatistikler Çekmecesi (Değerlendirme / İnceleme Modunda)
                AnimatedVisibility(
                    visible = session.isReviewMode && session.isStatsDrawerOpen,
                    enter = slideInHorizontally { it },
                    exit = slideOutHorizontally { it }
                ) {
                    ReviewStatsDrawerContent(
                        questions = session.testItem.questions,
                        selectedAnswers = session.selectedAnswers,
                        onGoToStatsTab = onGoToStatsTab
                    )
                }
            }
        }

        // =======================================================================
        // 3. ALT KOYU LACİVERT (#16327A) ARAÇ ÇUBUĞU:
        //    - Solda: Kitap / Tekli görünüm ikonu
        //    - Ortada: Kalem, Büyüteç (Zoom), El (Kaydırma), Silgi, 3 kalınlık noktası,
        //              Renk Paleti, Çöp Kutusu
        //    - En Sağda: Süre Sayacı
        // =======================================================================
        Surface(
            color = Navy345ToolbarColor,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Solda: Kitap / Tekli Görünüm İkonu
                IconButton(
                    onClick = onToggleViewMode,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .testTag("toggle_board_view_mode_button")
                ) {
                    Icon(
                        imageVector = if (session.viewMode == BoardViewMode.FULL_PAGE) {
                            Icons.Filled.MenuBook
                        } else {
                            Icons.Filled.CropPortrait
                        },
                        contentDescription = "Görünüm Değiştir",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Ortada: Kalem, Büyüteç (Zoom), El (Kaydırma), Silgi, 3 kalınlık noktası, Renk Paleti, Çöp Kutusu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Kalem
                    ToolbarIconButton(
                        icon = Icons.Filled.Edit,
                        contentDescription = "Kalem",
                        selected = activeTool == DrawingTool.PEN,
                        onClick = { onSelectTool(DrawingTool.PEN) },
                        tag = "tool_pen"
                    )

                    // Büyüteç (Zoom)
                    ToolbarIconButton(
                        icon = Icons.Filled.ZoomIn,
                        contentDescription = "Yakınlaştır",
                        selected = boardZoomScale > 1.05f,
                        onClick = {
                            if (boardZoomScale < 1.8f) {
                                boardZoomScale = (boardZoomScale + 0.35f).coerceAtMost(2.2f)
                            } else {
                                boardZoomScale = 1f
                                boardPanOffset = Offset.Zero
                            }
                            onSelectTool(DrawingTool.HAND_PAN)
                        },
                        tag = "tool_zoom"
                    )

                    // El (Kaydırma)
                    ToolbarIconButton(
                        icon = Icons.Filled.PanTool,
                        contentDescription = "Kaydır",
                        selected = activeTool == DrawingTool.HAND_PAN,
                        onClick = { onSelectTool(DrawingTool.HAND_PAN) },
                        tag = "tool_hand_pan"
                    )

                    // Silgi
                    ToolbarIconButton(
                        icon = Icons.Filled.AutoFixNormal,
                        contentDescription = "Silgi",
                        selected = activeTool == DrawingTool.ERASER,
                        onClick = { onSelectTool(DrawingTool.ERASER) },
                        tag = "tool_eraser"
                    )

                    // 3 Kalınlık Noktası
                    val dotSizes = listOf(6.dp, 9.dp, 13.dp)
                    dotSizes.forEachIndexed { idx, dotDp ->
                        val isSizeSelected = selectedStrokeSizeIndex == idx
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSizeSelected) Color.White.copy(alpha = 0.25f) else Color.Transparent
                                )
                                .clickable { onSelectStrokeSize(idx) }
                                .testTag("stroke_size_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(dotDp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }

                    // Renk Paleti
                    Box {
                        IconButton(
                            onClick = { showColorPalettePopup = !showColorPalettePopup },
                            modifier = Modifier.testTag("tool_color_palette")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Palette,
                                contentDescription = "Kalem Rengi",
                                tint = Color(selectedPenColorHex)
                            )
                        }
                        DropdownMenu(
                            expanded = showColorPalettePopup,
                            onDismissRequest = { showColorPalettePopup = false }
                        ) {
                            val palette = listOf(
                                0xFF1D4ED8L to "Mavi",
                                0xFF0F172AL to "Siyah",
                                0xFFEF4444L to "Kırmızı",
                                0xFF10B981L to "Yeşil",
                                0xFF7C3AEDL to "Mor",
                                0xFFF59E0BL to "Turuncu"
                            )
                            palette.forEach { (hex, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(Color(hex))
                                        )
                                    },
                                    onClick = {
                                        onSelectPenColor(hex)
                                        showColorPalettePopup = false
                                    }
                                )
                            }
                        }
                    }

                    // Çöp Kutusu
                    IconButton(
                        onClick = onClearCanvas,
                        modifier = Modifier.testTag("tool_clear_canvas")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Çizimleri Temizle",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // En Sağda: Süre Sayacı
                Surface(
                    color = Color.White.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("bottom_timer_counter")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = formattedTimer,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // "Soru Çözümü" Modalı (3 Nokta Menüsünden Tıklandığında Açılır)
    if (activeSolutionModalQuestion != null) {
        val q = activeSolutionModalQuestion!!
        Dialog(onDismissRequest = { activeSolutionModalQuestion = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFEFF6FF),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = Color(0xFF1D4ED8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "${q.number}. Soru Çözümü",
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "${q.publisherName} • ${q.topicHeader}",
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFDCFCE7)
                        ) {
                            Text(
                                text = "Doğru Cevap: ${q.correctOption}",
                                color = Color(0xFF15803D),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    if (isSolutionLoading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF1D4ED8),
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Soru çözümü hazırlanıyor...",
                                color = Color(0xFF1E3A8A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = activeSolutionText.ifBlank {
                                    "Cevap anahtarına göre doğru yanıt ${q.correctOption} seçeneğidir."
                                },
                                color = Color(0xFF1E293B),
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { activeSolutionModalQuestion = null },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8))
                        ) {
                            Text("Kapat", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // "İkiz Soru" Modalı (3 Nokta Menüsünden Tıklandığında Açılır)
    if (activeTwinModalQuestion != null) {
        val origQ = activeTwinModalQuestion!!
        var selectedTwinOpt by remember(origQ.id) { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { activeTwinModalQuestion = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 580.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFEFF6FF),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                        tint = Color(0xFF1D4ED8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "${origQ.number}. Sorunun İkiz Sorusu",
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "${origQ.topicHeader} • ${origQ.difficulty.label}",
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        IconButton(onClick = { activeTwinModalQuestion = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = Color(0xFF64748B))
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    if (isTwinLoading || activeTwinData == null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF1D4ED8),
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "İkiz soru hazırlanıyor...",
                                color = Color(0xFF1E3A8A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        val twin = activeTwinData!!
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = twin.questionStem,
                                color = Color(0xFF0F172A),
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("A", "B", "C", "D", "E").forEach { optKey ->
                                val optText = twin.options[optKey].orEmpty()
                                val isSelected = selectedTwinOpt == optKey
                                val isRight = selectedTwinOpt != null && optKey == twin.correctOption
                                val isWrong = isSelected && optKey != twin.correctOption

                                Surface(
                                    onClick = { selectedTwinOpt = optKey },
                                    shape = RoundedCornerShape(12.dp),
                                    color = when {
                                        isRight -> Color(0xFFDCFCE7)
                                        isWrong -> Color(0xFFFEE2E2)
                                        isSelected -> Color(0xFFEFF6FF)
                                        else -> Color.White
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            isRight -> Color(0xFF16A34A)
                                            isWrong -> Color(0xFFDC2626)
                                            isSelected -> Color(0xFF1D4ED8)
                                            else -> Color(0xFFE2E8F0)
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "$optKey)",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF1E3A8A),
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = optText,
                                            color = Color(0xFF1E293B),
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        if (selectedTwinOpt != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFEFF6FF),
                                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Doğru Seçenek: ${twin.correctOption}",
                                        color = Color(0xFF1E3A8A),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = twin.solutionExplanation,
                                        color = Color(0xFF1E293B),
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp
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

@Composable
private fun ToolbarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (selected) Color(0xFF3B82F6) else Color.Transparent)
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) Color.White else Color.Transparent,
                shape = CircleShape
            )
            .clickable { onClick() }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun FullPageWorksheetCanvas(
    questions: List<YksQuestion>,
    selectedAnswers: Map<Int, String>,
    isReviewMode: Boolean,
    strokes: List<DrawStroke>,
    activeTool: DrawingTool,
    selectedPenColorHex: Long,
    customPublishers: List<YksPublisherInfo>,
    zoomScale: Float,
    panOffset: Offset,
    onUpdateZoomAndPan: (Float, Offset) -> Unit,
    onAddStroke: (List<Offset>) -> Unit,
    onTwinQuestionClick: (YksQuestion) -> Unit,
    onSolutionClick: (YksQuestion) -> Unit,
    onAddToKumbaraClick: (YksQuestion) -> Unit
) {
    val currentDragPoints = remember { mutableStateListOf<Offset>() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEAEFF7))
            .padding(10.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoomScale,
                    scaleY = zoomScale,
                    translationX = panOffset.x,
                    translationY = panOffset.y
                )
                .then(
                    if (activeTool == DrawingTool.HAND_PAN) {
                        Modifier.pointerInput(zoomScale, panOffset) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (zoomScale * zoom).coerceIn(1f, 2.5f)
                                val newPan = if (newScale == 1f) Offset.Zero else panOffset + pan
                                onUpdateZoomAndPan(newScale, newPan)
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(18.dp)
                ) {
                    // 2 Sütunlu Gerçek 345 All Star Yaprak Test Düzeni
                    val pairs = questions.chunked(2)
                    pairs.forEach { rowQuestions ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            rowQuestions.forEach { q ->
                                WorksheetQuestionCell(
                                    question = q,
                                    selectedOption = selectedAnswers[q.number],
                                    isReviewMode = isReviewMode,
                                    customPublishers = customPublishers,
                                    onTwinQuestionClick = { onTwinQuestionClick(q) },
                                    onSolutionClick = { onSolutionClick(q) },
                                    onAddToKumbaraClick = { onAddToKumbaraClick(q) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowQuestions.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Çizim Katmanı
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .then(
                            if (activeTool != DrawingTool.HAND_PAN) {
                                Modifier.pointerInput(activeTool, selectedPenColorHex) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            currentDragPoints.clear()
                                            currentDragPoints.add(offset)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            currentDragPoints.add(change.position)
                                        },
                                        onDragEnd = {
                                            onAddStroke(currentDragPoints.toList())
                                            currentDragPoints.clear()
                                        },
                                        onDragCancel = {
                                            currentDragPoints.clear()
                                        }
                                    )
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    strokes.forEach { stroke ->
                        drawStrokePath(stroke.points, Color(stroke.colorHex), stroke.strokeWidthPx)
                    }
                    if (currentDragPoints.size >= 2 && activeTool != DrawingTool.ERASER) {
                        drawStrokePath(currentDragPoints, Color(selectedPenColorHex), 8f)
                    }
                }
            }
        }
    }
}

/**
 * 345 ALL STAR SORU HÜCRESİ:
 * - A. Aktif Test Modunda (`!isReviewMode`):
 *   * Sol sütunda şeffaf Yayınevi Logosu ve hemen altında dinamik sıra numarası (`1.`, `2.`, `3.`...)
 *   * Sağ sütunda numarasız saf soru görseli
 *   * Soruların üzerinde HİÇBİR buton veya doğru/yanlış ikonu YOKTUR!
 * - B. Değerlendirme / İnceleme Modunda (`isReviewMode`):
 *   * Her sorunun sağ üst köşesinde, kullanıcının optik formdaki cevabına göre:
 *     - Doğruysa: Yuvarlak Yeşil Tik (`✓`) ikonu
 *     - Yanlışsa (veya boşsa): Yuvarlak Kırmızı Çarpı (`✕`) ikonu
 *   * Bu doğru/yanlış ikonunun hemen ALTINDA yuvarlak mavi bir `⋮` (Üç Nokta) butonu belirir.
 *   * Tıklandığında açılan menüde SADECE şu 3 seçenek yer alır:
 *     1. İkiz Soru
 *     2. Soru Çözümü
 *     3. Soru Kumbarama Ekle
 */
@Composable
private fun WorksheetQuestionCell(
    question: YksQuestion,
    selectedOption: String?,
    isReviewMode: Boolean,
    customPublishers: List<YksPublisherInfo>,
    onTwinQuestionClick: () -> Unit,
    onSolutionClick: () -> Unit,
    onAddToKumbaraClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCorrect = selectedOption != null && selectedOption == question.correctOption
    var showThreeDotMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(
                width = 1.dp,
                color = Color(0xFFE2E8F0),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
            .testTag("worksheet_question_cell_${question.number}")
    ) {
        val croppedQuestionBitmap = remember(question.croppedImagePath) {
            if (question.croppedImagePath.isNotBlank()) {
                runCatching {
                    val file = File(question.croppedImagePath)
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                }.getOrNull()
            } else {
                null
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // SOL SÜTUN: Şeffaf Yayınevi Logosu + Hemen Altında Dinamik Sıra Numarası (1., 2., 3...)
            Column(
                modifier = Modifier.width(46.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PublisherLogoEmblem(
                    publisherName = question.publisherName.ifBlank { question.badgeTitle },
                    customLogoUri = question.publisherLogoUri,
                    customPublishers = customPublishers,
                    sizeDp = 38.dp,
                    showNameAlongside = false
                )

                Text(
                    text = "${question.number}.",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    ),
                    color = Color(0xFF0F172A)
                )
            }

            // SAĞ SÜTUN: Numarasız Saf Soru Görseli
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = if (isReviewMode) 42.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (croppedQuestionBitmap != null) {
                    Image(
                        bitmap = croppedQuestionBitmap.asImageBitmap(),
                        contentDescription = "Soru Görseli",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                    )
                } else {
                    if (question.stemTop.isNotBlank()) {
                        Text(
                            text = question.stemTop,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFF0F172A)
                        )
                    }

                    if (question.visualType == QuestionVisualType.MARKET_RECEIPT) {
                        MarketReceiptBox()
                    } else if (question.visualType == QuestionVisualType.LEGO_BLOCKS) {
                        LegoBlocksDiagram()
                    } else if (question.bulletsOrSteps.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            question.bulletsOrSteps.forEach { bullet ->
                                Text(
                                    text = bullet,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    ),
                                    color = Color(0xFF334155)
                                )
                            }
                        }
                    }

                    if (question.stemBottom.isNotBlank()) {
                        Text(
                            text = question.stemBottom,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0F172A)
                        )
                    }
                }
            }
        }

        // DEĞERLENDİRME MODU: SAĞ ÜST KÖŞEDE DOĞRU/YANLIŞ İKONU + HEMEN ALTINDA MAVİ "⋮" (ÜÇ NOKTA) MENÜSÜ
        if (isReviewMode) {
            Column(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Yuvarlak Yeşil Tik (✓) veya Yuvarlak Kırmızı Çarpı (✕)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isCorrect) Color(0xFF16A34A) else Color(0xFFDC2626))
                        .testTag("question_result_badge_${question.number}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCorrect) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = if (isCorrect) "Doğru" else "Yanlış",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Hemen Altında Yuvarlak Mavi "⋮" (Üç Nokta) Butonu ve Açılır Menüsü
                Box {
                    Surface(
                        onClick = { showThreeDotMenu = true },
                        shape = CircleShape,
                        color = Color(0xFF1D4ED8),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("question_three_dot_button_${question.number}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Soru Seçenekleri",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // SADECE 3 SEÇENEKLİ AÇILIR MENÜ: İkiz Soru, Soru Çözümü, Soru Kumbarama Ekle
                    DropdownMenu(
                        expanded = showThreeDotMenu,
                        onDismissRequest = { showThreeDotMenu = false },
                        modifier = Modifier
                            .background(Color.White)
                            .widthIn(min = 195.dp)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "İkiz Soru",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = null,
                                    tint = Color(0xFF1D4ED8),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showThreeDotMenu = false
                                onTwinQuestionClick()
                            },
                            modifier = Modifier.testTag("menu_ikiz_soru_${question.number}")
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Soru Çözümü",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = Color(0xFF1D4ED8),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showThreeDotMenu = false
                                onSolutionClick()
                            },
                            modifier = Modifier.testTag("menu_soru_cozumu_${question.number}")
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Soru Kumbarama Ekle",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.BookmarkAdd,
                                    contentDescription = null,
                                    tint = Color(0xFF1D4ED8),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showThreeDotMenu = false
                                onAddToKumbaraClick()
                            },
                            modifier = Modifier.testTag("menu_kumbarama_ekle_${question.number}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketReceiptBox() {
    Surface(
        color = Color(0xFFFFFBEB),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFD97706)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "MARKET FİŞİ",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = SoruLabInkBlack,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFD97706))
            Text("ÜRÜN          ADET   BİRİM FİYAT", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
            Text("Meyve Suyu     K         L", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
            Text("Bisküvi        L         K", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
            Text("Çikolata       M         M", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFFD97706))
            Text("Toplam Tutar: N TL", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun LegoBlocksDiagram() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, SoruLabBorderLight, RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val colors = listOf(Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFF3B82F6))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                colors.forEach { c ->
                    Box(modifier = Modifier.size(16.dp, 12.dp).background(c, RoundedCornerShape(2.dp)))
                }
            }
            Text("Şekil 1", style = MaterialTheme.typography.labelSmall)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                colors.drop(1).forEach { c ->
                    Box(modifier = Modifier.size(16.dp, 12.dp).background(c, RoundedCornerShape(2.dp)))
                }
            }
            Text("Şekil 2 (-a)", style = MaterialTheme.typography.labelSmall)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                colors.drop(2).forEach { c ->
                    Box(modifier = Modifier.size(16.dp, 12.dp).background(c, RoundedCornerShape(2.dp)))
                }
            }
            Text("Şekil 3 (-b)", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SingleQuestionScratchpadView(
    question: YksQuestion,
    questionIndex: Int,
    totalQuestions: Int,
    selectedOption: String?,
    isReviewMode: Boolean,
    strokes: List<DrawStroke>,
    activeTool: DrawingTool,
    selectedPenColorHex: Long,
    customPublishers: List<YksPublisherInfo>,
    onAddStroke: (List<Offset>) -> Unit,
    onSelectOption: (String) -> Unit,
    onPrevQuestion: () -> Unit,
    onNextQuestion: () -> Unit,
    onTwinQuestionClick: () -> Unit,
    onSolutionClick: () -> Unit,
    onAddToKumbaraClick: () -> Unit
) {
    val currentDragPoints = remember { mutableStateListOf<Offset>() }
    val isCorrect = selectedOption != null && selectedOption == question.correctOption
    var showThreeDotMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 42.dp, end = 42.dp, top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Sol Panel: Soru Gövdesi + Dinamik Sol Sütun + Değerlendirme Modunda Sağ Üst İkonlar
                Box(
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(end = if (isReviewMode) 44.dp else 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val singleCroppedBitmap = remember(question.croppedImagePath) {
                            if (question.croppedImagePath.isNotBlank()) {
                                runCatching {
                                    val file = File(question.croppedImagePath)
                                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                                }.getOrNull()
                            } else {
                                null
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.width(54.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PublisherLogoEmblem(
                                    publisherName = question.publisherName.ifBlank { question.badgeTitle },
                                    customLogoUri = question.publisherLogoUri,
                                    customPublishers = customPublishers,
                                    sizeDp = 44.dp,
                                    showNameAlongside = false
                                )
                                Text(
                                    text = "${question.number}.",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp
                                    ),
                                    color = Color(0xFF0F172A)
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (singleCroppedBitmap != null) {
                                    Image(
                                        bitmap = singleCroppedBitmap.asImageBitmap(),
                                        contentDescription = "Soru Görseli",
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White)
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                    )
                                } else {
                                    if (question.stemTop.isNotBlank()) {
                                        Text(
                                            text = question.stemTop,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                            color = Color(0xFF0F172A)
                                        )
                                    }
                                    if (question.stemBottom.isNotBlank()) {
                                        Text(
                                            text = question.stemBottom,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF0F172A)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isReviewMode) {
                        Column(
                            modifier = Modifier.align(Alignment.TopEnd),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isCorrect) Color(0xFF16A34A) else Color(0xFFDC2626)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isCorrect) Icons.Filled.Check else Icons.Filled.Close,
                                    contentDescription = if (isCorrect) "Doğru" else "Yanlış",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Box {
                                Surface(
                                    onClick = { showThreeDotMenu = true },
                                    shape = CircleShape,
                                    color = Color(0xFF1D4ED8),
                                    shadowElevation = 4.dp,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = "Soru Seçenekleri",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showThreeDotMenu,
                                    onDismissRequest = { showThreeDotMenu = false },
                                    modifier = Modifier
                                        .background(Color.White)
                                        .widthIn(min = 195.dp)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "İkiz Soru",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A),
                                                fontSize = 13.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.ContentCopy,
                                                contentDescription = null,
                                                tint = Color(0xFF1D4ED8),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            showThreeDotMenu = false
                                            onTwinQuestionClick()
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Soru Çözümü",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A),
                                                fontSize = 13.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.MenuBook,
                                                contentDescription = null,
                                                tint = Color(0xFF1D4ED8),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            showThreeDotMenu = false
                                            onSolutionClick()
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Soru Kumbarama Ekle",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A),
                                                fontSize = 13.sp
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.BookmarkAdd,
                                                contentDescription = null,
                                                tint = Color(0xFF1D4ED8),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            showThreeDotMenu = false
                                            onAddToKumbaraClick()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Sağ Panel: Çözüm & İşlem Alanı
                Box(
                    modifier = Modifier
                        .weight(0.52f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFAFBFD))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (activeTool != DrawingTool.HAND_PAN) {
                            Modifier.pointerInput(question.id, activeTool, selectedPenColorHex) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentDragPoints.clear()
                                        currentDragPoints.add(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentDragPoints.add(change.position)
                                    },
                                    onDragEnd = {
                                        onAddStroke(currentDragPoints.toList())
                                        currentDragPoints.clear()
                                    },
                                    onDragCancel = {
                                        currentDragPoints.clear()
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                strokes.forEach { stroke ->
                    drawStrokePath(stroke.points, Color(stroke.colorHex), stroke.strokeWidthPx)
                }
                if (currentDragPoints.size >= 2 && activeTool != DrawingTool.ERASER) {
                    drawStrokePath(currentDragPoints, Color(selectedPenColorHex), 8f)
                }
            }
        }

        // Tekli Mod Alt Şık Çubuğu `< (A) (B) (C) (D) (E) >`
        Surface(
            color = Color.White,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevQuestion,
                    enabled = questionIndex > 0,
                    modifier = Modifier.testTag("single_prev_question")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Önceki Soru",
                        tint = if (questionIndex > 0) Color(0xFF1D4ED8) else Color(0xFF94A3B8)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("A", "B", "C", "D", "E").forEach { opt ->
                        val isSelected = selectedOption == opt
                        val isCorrectOpt = isReviewMode && question.correctOption == opt
                        val bg = when {
                            isCorrectOpt -> Color(0xFF16A34A)
                            isSelected && isReviewMode -> Color(0xFFDC2626)
                            isSelected -> Color(0xFF1D4ED8)
                            else -> Color.Transparent
                        }
                        val borderCol = when {
                            isCorrectOpt -> Color(0xFF16A34A)
                            isSelected -> Color(0xFF1D4ED8)
                            else -> Color(0xFF1D4ED8).copy(alpha = 0.6f)
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(bg)
                                .border(2.dp, borderCol, CircleShape)
                                .clickable(enabled = !isReviewMode) { onSelectOption(opt) }
                                .testTag("single_opt_$opt"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = opt,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected || isCorrectOpt) Color.White else Color(0xFF1D4ED8)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                IconButton(
                    onClick = onNextQuestion,
                    enabled = questionIndex < totalQuestions - 1,
                    modifier = Modifier.testTag("single_next_question")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Sonraki Soru",
                        tint = if (questionIndex < totalQuestions - 1) Color(0xFF1D4ED8) else Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
private fun OpticalFormDrawerContent(
    questions: List<YksQuestion>,
    selectedAnswers: Map<Int, String>,
    onSelectOption: (Int, String) -> Unit,
    onFinishTest: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 16.dp,
        modifier = Modifier
            .width(275.dp)
            .fillMaxHeight()
            .testTag("optical_form_drawer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Optik Form",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = Color(0xFF1D4ED8)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "İşaretlenen: ${selectedAnswers.size} / ${questions.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(questions, key = { it.id }) { q ->
                    val chosen = selectedAnswers[q.number]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${q.number}.",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color(0xFF1D4ED8),
                            modifier = Modifier.width(28.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("A", "B", "C", "D", "E").forEach { opt ->
                                val active = chosen == opt
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (active) Color(0xFF1D4ED8) else Color.Transparent)
                                        .border(1.5.dp, Color(0xFF1D4ED8), CircleShape)
                                        .clickable { onSelectOption(q.number, opt) }
                                        .testTag("optic_q${q.number}_$opt"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = opt,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = if (active) Color.White else Color(0xFF1D4ED8)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onFinishTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("optical_drawer_testi_bitir"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8))
            ) {
                Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Testi Bitir",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ReviewStatsDrawerContent(
    questions: List<YksQuestion>,
    selectedAnswers: Map<Int, String>,
    onGoToStatsTab: () -> Unit
) {
    var correct = 0
    var wrong = 0
    var empty = 0
    questions.forEach { q ->
        val c = selectedAnswers[q.number]
        if (c.isNullOrBlank()) empty++
        else if (c == q.correctOption) correct++
        else wrong++
    }
    val net = correct - (wrong * 0.25)

    Surface(
        color = Color.White,
        shadowElevation = 16.dp,
        modifier = Modifier
            .width(250.dp)
            .fillMaxHeight()
            .testTag("review_stats_drawer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Test İstatistikleri",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFEFF6FF)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.2f NET", net),
                        color = Color(0xFF1D4ED8),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ResultCircleStat(count = correct, label = "Doğru", color = Color(0xFF16A34A))
                ResultCircleStat(count = wrong, label = "Yanlış", color = Color(0xFFDC2626))
                ResultCircleStat(count = empty, label = "Boş", color = Color(0xFF64748B))
            }

            Button(
                onClick = onGoToStatsTab,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8))
            ) {
                Text(
                    text = "▸ Başarı Durumuna Git",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ResultCircleStat(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .border(5.dp, color.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePath(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float
) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}
