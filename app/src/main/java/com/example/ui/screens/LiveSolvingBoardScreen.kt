package com.example.ui.screens

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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.data.QuestionVisualType
import com.example.data.YksQuestion
import com.example.ui.theme.*
import com.example.viewmodel.ActiveSolvingSession
import com.example.viewmodel.BoardViewMode
import com.example.viewmodel.DrawStroke
import com.example.viewmodel.DrawingTool
import java.io.File
import java.util.Locale

@Composable
fun LiveSolvingBoardScreen(
    session: ActiveSolvingSession,
    strokes: List<DrawStroke>,
    activeTool: DrawingTool,
    selectedStrokeSizeIndex: Int,
    selectedPenColorHex: Long,
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
    onAskAiForQuestion: (YksQuestion) -> Unit
) {
    BackHandler { onExitSession() }

    var showColorPalettePopup by remember { mutableStateOf(false) }
    val currentQuestion = session.testItem.questions.getOrElse(session.currentQuestionIndex) {
        session.testItem.questions.first()
    }

    val formattedTimer = remember(session.elapsedSeconds) {
        val mins = session.elapsedSeconds / 60
        val secs = session.elapsedSeconds % 60
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F6FB))
            .testTag("live_solving_board_root")
    ) {
        // 1. TOP WHITE HEADER BAR (matches Reference Photos 5, 8, 10)
        Surface(
            color = SoruLabSurface,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                        tint = SoruLabCobaltBlue
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = session.testItem.fullTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = SoruLabInkBlack
                    )
                    if (session.viewMode == BoardViewMode.SINGLE_QUESTION) {
                        Text(
                            text = "Soru ${currentQuestion.number} / ${session.testItem.questions.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SoruLabSlateMuted
                        )
                    }
                }

                if (session.isReviewMode) {
                    Button(
                        onClick = onGoToStatsTab,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SoruLabCobaltBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("basari_durumuna_git_button")
                    ) {
                        Text("Başarı Durumuna Git", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    TextButton(
                        onClick = { onAskAiForQuestion(currentQuestion) },
                        modifier = Modifier.testTag("board_ask_ai_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "AI Çözüm",
                            tint = SoruLabCobaltBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AI Çözüm", style = MaterialTheme.typography.labelLarge, color = SoruLabCobaltBlue)
                    }
                }
            }
        }

        // 2. ROYAL BLUE DRAWING TOOLBAR (matches Reference Photos 5, 6, 7, 10, 11)
        Surface(
            color = SoruLabToolbarBlue,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: View Mode Toggle (Tam Sayfa Book vs Tekli Soru)
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

                Spacer(modifier = Modifier.width(12.dp))

                // Center Drawing Tools: Pen, Smart Shape Pen, Hand Pan/Zoom, Eraser, 3 Stroke Dots, Color Palette, Trash
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ToolbarIconButton(
                        icon = Icons.Filled.Edit,
                        contentDescription = "Kalem",
                        selected = activeTool == DrawingTool.PEN,
                        onClick = { onSelectTool(DrawingTool.PEN) },
                        tag = "tool_pen"
                    )
                    ToolbarIconButton(
                        icon = Icons.Filled.Timeline,
                        contentDescription = "Akıllı Şekil Kalemi",
                        selected = activeTool == DrawingTool.SMART_SHAPE,
                        onClick = { onSelectTool(DrawingTool.SMART_SHAPE) },
                        tag = "tool_smart_shape"
                    )
                    ToolbarIconButton(
                        icon = Icons.Filled.PanTool,
                        contentDescription = "Kaydır / Yakınlaştır",
                        selected = activeTool == DrawingTool.HAND_PAN,
                        onClick = { onSelectTool(DrawingTool.HAND_PAN) },
                        tag = "tool_hand_pan"
                    )
                    ToolbarIconButton(
                        icon = Icons.Filled.AutoFixNormal,
                        contentDescription = "Silgi",
                        selected = activeTool == DrawingTool.ERASER,
                        onClick = { onSelectTool(DrawingTool.ERASER) },
                        tag = "tool_eraser"
                    )

                    // 3 Stroke Width Dots
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

                    // Color Palette Button
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
                                0xFF1D4ED8L to "Kraliyet Mavisi",
                                0xFF0F172AL to "Mürekkep Siyahı",
                                0xFFEF4444L to "Kırmızı",
                                0xFF10B981L to "Zümrüt Yeşili",
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

                    // Trash / Clear All Strokes Button
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

                Spacer(modifier = Modifier.width(12.dp))

                // Right: Timer Pill + "Testi Bitir" Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = formattedTimer,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    if (!session.isReviewMode) {
                        Button(
                            onClick = onFinishTest,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3B82F6),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("toolbar_testi_bitir_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testi Bitir", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // 3. MAIN INTERACTIVE BOARD AREA (Full Page Worksheet OR Single Question Scratchpad + Side Drawers)
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
                    onAddStroke = onAddStroke,
                    onSelectOption = onSelectOption,
                    onOpenSingleQuestion = { idx ->
                        onSelectQuestionIndex(idx)
                        onToggleViewMode()
                    },
                    onAskAi = onAskAiForQuestion
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
                    onAskAi = { onAskAiForQuestion(currentQuestion) }
                )
            }

            // LEFT-EDGE VERTICAL PULL-OUT TAB: "Soru Haritası" (in Single Question Mode, matches Photos 10 & 11)
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
                            color = SoruLabSurface,
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
                                    color = SoruLabInkBlack
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
                                                    if (isCurrent) SoruLabSkyTint else SoruLabSurfaceVariant
                                                )
                                                .clickable { onSelectQuestionIndex(idx) }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${q.number}. Soru",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = SoruLabInkBlack
                                            )
                                            Surface(
                                                color = if (ans != null) SoruLabCobaltBlue else Color.White,
                                                shape = CircleShape
                                            ) {
                                                Text(
                                                    text = ans ?: "Boş",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = if (ans != null) Color.White else SoruLabSlateMuted,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Vertical Tab Handle ("Soru Haritası")
                    Surface(
                        color = SoruLabCobaltBlue,
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

            // RIGHT-EDGE VERTICAL PULL-OUT TAB & DRAWER: "Optik Form" (Solving Mode) OR "İstatistikler" (Review Mode)
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Vertical Pull-out Handle matching Photos 5, 6, 8, 9
                Surface(
                    color = SoruLabCobaltBlue,
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .clickable {
                            if (session.isReviewMode) onToggleStatsDrawer() else onToggleOpticalForm()
                        }
                        .testTag("right_drawer_handle")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (session.isReviewMode) Icons.Filled.PieChart else Icons.Filled.GridOn,
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
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    lineHeight = 11.sp
                                ),
                                color = Color.White
                            )
                        }
                    }
                }

                // Slide-out Optical Form Drawer (matches Reference Photo 6 & 7)
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

                // Slide-out Review Statistics Drawer (matches Reference Photo 9)
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
    onAddStroke: (List<Offset>) -> Unit,
    onSelectOption: (Int, String) -> Unit,
    onOpenSingleQuestion: (Int) -> Unit,
    onAskAi: (YksQuestion) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
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
                    scaleX = scale,
                    scaleY = scale,
                    translationX = panOffset.x,
                    translationY = panOffset.y
                )
                .then(
                    if (activeTool == DrawingTool.HAND_PAN) {
                        Modifier.pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 2.4f)
                                scale = newScale
                                panOffset = if (newScale == 1f) Offset.Zero else panOffset + pan
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
                // Multi-column YKS Worksheet matching Reference Photos 5, 6, 7, 8
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                ) {
                    // Worksheet Top Banner ("GÜNLÜK ÖDEV 5" + "TEMEL KAVRAMLAR")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = SoruLabSkyTint,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = questions.firstOrNull()?.badgeTitle ?: "GÜNLÜK ÖDEV 5",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = SoruLabRoyalBlue,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = questions.firstOrNull()?.topicHeader ?: "TEMEL KAVRAMLAR",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = SoruLabSlateMuted
                        )
                        Surface(
                            color = Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "ÖSYM TADINDA",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFB91C1C),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = SoruLabBorderLight)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Render questions in 2-column grid rows (like a real ÜçDörtBeş YKS test page)
                    val pairs = questions.chunked(2)
                    pairs.forEach { rowQuestions ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            rowQuestions.forEach { q ->
                                WorksheetQuestionCell(
                                    question = q,
                                    selectedOption = selectedAnswers[q.number],
                                    isReviewMode = isReviewMode,
                                    onSelectOption = { opt -> onSelectOption(q.number, opt) },
                                    onFocusSingleQuestion = {
                                        val idx = questions.indexOfFirst { it.id == q.id }.coerceAtLeast(0)
                                        onOpenSingleQuestion(idx)
                                    },
                                    onAskAi = { onAskAi(q) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowQuestions.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Transparent Ink Drawing Overlay Canvas on top of the worksheet when Pen/SmartShape/Eraser is selected
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

@Composable
private fun WorksheetQuestionCell(
    question: YksQuestion,
    selectedOption: String?,
    isReviewMode: Boolean,
    onSelectOption: (String) -> Unit,
    onFocusSingleQuestion: () -> Unit,
    onAskAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCorrect = selectedOption != null && selectedOption == question.correctOption
    val isWrong = selectedOption != null && selectedOption != question.correctOption

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    isReviewMode && isCorrect -> SoruLabEmeraldLight.copy(alpha = 0.25f)
                    isReviewMode && isWrong -> SoruLabCoralLight.copy(alpha = 0.25f)
                    else -> Color(0xFFFAFBFD)
                }
            )
            .border(
                width = 1.dp,
                color = when {
                    isReviewMode && isCorrect -> SoruLabEmerald
                    isReviewMode && isWrong -> SoruLabCoralRed
                    else -> SoruLabBorderLight
                },
                shape = RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
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

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Publisher Logo directly ABOVE the Question Number badge
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        PublisherLogoEmblem(
                            publisherName = question.publisherName.ifBlank { question.badgeTitle },
                            customLogoUri = question.publisherLogoUri,
                            sizeDp = 26.dp,
                            showNameAlongside = false
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(SoruLabCobaltBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${question.number}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = question.publisherName.ifBlank { question.badgeTitle },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            ),
                            color = SoruLabSlateMuted
                        )

                        // Review Mode Status Icon (matches large green checkmark ✓ or red ✗ in Reference Photos 8 & 9)
                        if (isReviewMode) {
                            Surface(
                                color = when {
                                    isCorrect -> SoruLabEmerald
                                    isWrong -> SoruLabCoralRed
                                    else -> SoruLabAmber
                                },
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = when {
                                        isCorrect -> "✓ Doğru"
                                        isWrong -> "✗ Yanlış (Cevap: ${question.correctOption})"
                                        else -> "○ Boş (Cevap: ${question.correctOption})"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onAskAi,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "AI Çözüm",
                            tint = SoruLabCobaltBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onFocusSingleQuestion,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInFull,
                            contentDescription = "Tekli Görünümde Aç",
                            tint = SoruLabSlateMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (croppedQuestionBitmap != null) {
                Image(
                    bitmap = croppedQuestionBitmap.asImageBitmap(),
                    contentDescription = "Soru Görseli",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, SoruLabBorderLight, RoundedCornerShape(10.dp))
                )
            }

            if (question.stemTop.isNotBlank()) {
                Text(
                    text = question.stemTop,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = SoruLabInkBlack
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
                            color = SoruLabSlateDark
                        )
                    }
                }
            }

            if (question.stemBottom.isNotBlank()) {
                Text(
                    text = question.stemBottom,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = SoruLabInkBlack
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Options A B C D E clickable pills
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                question.options.forEach { (optKey, optVal) ->
                    val isChosen = selectedOption == optKey
                    val isRightAnswer = isReviewMode && question.correctOption == optKey
                    val rowBg = when {
                        isRightAnswer -> SoruLabEmeraldLight
                        isChosen && isReviewMode && !isRightAnswer -> SoruLabCoralLight
                        isChosen -> SoruLabSkyTint
                        else -> Color.White
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(rowBg)
                            .border(
                                width = 1.dp,
                                color = if (isChosen || isRightAnswer) SoruLabCobaltBlue else SoruLabBorderLight,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = !isReviewMode) { onSelectOption(optKey) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$optKey) ",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isChosen) SoruLabRoyalBlue else SoruLabInkBlack
                        )
                        Text(
                            text = optVal,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoruLabInkBlack
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
    onAddStroke: (List<Offset>) -> Unit,
    onSelectOption: (String) -> Unit,
    onPrevQuestion: () -> Unit,
    onNextQuestion: () -> Unit,
    onAskAi: () -> Unit
) {
    val currentDragPoints = remember { mutableStateListOf<Offset>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Upper Question + Scratchpad Canvas Area (matches Reference Photos 10 & 11)
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
                // Left Pane: Crisp Question Card
                Column(
                    modifier = Modifier
                        .weight(0.48f)
                        .verticalScroll(rememberScrollState())
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Publisher Logo directly ABOVE Question Number in Single-Question view too
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                PublisherLogoEmblem(
                                    publisherName = question.publisherName.ifBlank { question.badgeTitle },
                                    customLogoUri = question.publisherLogoUri,
                                    sizeDp = 30.dp,
                                    showNameAlongside = false
                                )
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(SoruLabCobaltBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${question.number}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            }

                            Surface(
                                color = SoruLabSkyTint,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${question.publisherName.ifBlank { question.badgeTitle }} • SORU ${question.number}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = SoruLabRoyalBlue,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        AssistChip(
                            onClick = onAskAi,
                            label = { Text("SoruLab AI Çözümü") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = SoruLabCobaltBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    if (singleCroppedBitmap != null) {
                        Image(
                            bitmap = singleCroppedBitmap.asImageBitmap(),
                            contentDescription = "Soru Görseli",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, SoruLabBorderLight, RoundedCornerShape(12.dp))
                        )
                    }

                    if (question.stemTop.isNotBlank()) {
                        Text(
                            text = question.stemTop,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = SoruLabInkBlack
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(SoruLabSurfaceVariant)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            question.bulletsOrSteps.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = SoruLabSlateDark
                                )
                            }
                        }
                    }

                    if (question.stemBottom.isNotBlank()) {
                        Text(
                            text = question.stemBottom,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = SoruLabInkBlack
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        question.options.forEach { (k, v) ->
                            Text(
                                text = "$k)  $v",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (selectedOption == k) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (selectedOption == k) SoruLabRoyalBlue else SoruLabInkBlack
                            )
                        }
                    }

                    if (isReviewMode) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SoruLabEmeraldLight.copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Doğru Cevap: ${question.correctOption}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF065F46)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = question.stepByStepSolution,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SoruLabInkBlack
                                )
                            }
                        }
                    }
                }

                // Right Pane: Dedicated White Grid Scratchpad for Handwritten Calculations
                Box(
                    modifier = Modifier
                        .weight(0.52f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFAFBFD))
                        .border(1.dp, SoruLabBorderLight, RoundedCornerShape(16.dp))
                ) {
                    Text(
                        text = "Çözüm & İşlem Alanı (Kalemle Yazabilirsiniz)",
                        style = MaterialTheme.typography.labelSmall,
                        color = SoruLabSlateMuted.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    )
                }
            }

            // Full-width Interactive Drawing Canvas covering both question & scratchpad (like Photo 11)
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

        // Bottom Choice Bar `< (A) (B) (C) (D) (E) >` matching Reference Photos 10 & 11
        Surface(
            color = SoruLabSurface,
            shadowElevation = 10.dp
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
                        tint = if (questionIndex > 0) SoruLabCobaltBlue else SoruLabSlateMuted
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("A", "B", "C", "D", "E").forEach { opt ->
                        val isSelected = selectedOption == opt
                        val isCorrectOpt = isReviewMode && question.correctOption == opt
                        val bg = when {
                            isCorrectOpt -> SoruLabEmerald
                            isSelected && isReviewMode -> SoruLabCoralRed
                            isSelected -> SoruLabCobaltBlue
                            else -> Color.Transparent
                        }
                        val borderCol = when {
                            isCorrectOpt -> SoruLabEmerald
                            isSelected -> SoruLabCobaltBlue
                            else -> SoruLabCobaltBlue.copy(alpha = 0.6f)
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
                                color = if (isSelected || isCorrectOpt) Color.White else SoruLabCobaltBlue
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
                        tint = if (questionIndex < totalQuestions - 1) SoruLabCobaltBlue else SoruLabSlateMuted
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
        color = SoruLabSurface,
        shadowElevation = 16.dp,
        modifier = Modifier
            .width(265.dp)
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
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = SoruLabCobaltBlue
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "İşaretlenen: ${selectedAnswers.size} / ${questions.size}",
                style = MaterialTheme.typography.labelSmall,
                color = SoruLabSlateMuted
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
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = SoruLabCobaltBlue,
                            modifier = Modifier.width(28.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("A", "B", "C", "D", "E").forEach { opt ->
                                val active = chosen == opt
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (active) SoruLabCobaltBlue else Color.Transparent)
                                        .border(1.5.dp, SoruLabCobaltBlue, CircleShape)
                                        .clickable { onSelectOption(q.number, opt) }
                                        .testTag("optic_q${q.number}_$opt"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = opt,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = if (active) Color.White else SoruLabCobaltBlue
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
                colors = ButtonDefaults.buttonColors(containerColor = SoruLabCobaltBlue)
            ) {
                Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Testi Bitir", style = MaterialTheme.typography.titleSmall)
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

    Surface(
        color = SoruLabSurface,
        shadowElevation = 16.dp,
        modifier = Modifier
            .width(240.dp)
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
            Text(
                text = "Test Sonucu",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = SoruLabInkBlack
            )

            // Circular Doğru, Yanlış, Boş metrics matching Reference Photo 9
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ResultCircleStat(count = correct, label = "Doğru", color = SoruLabEmerald)
                ResultCircleStat(count = wrong, label = "Yanlış", color = SoruLabCoralRed)
                ResultCircleStat(count = empty, label = "Boş", color = SoruLabSlateMuted)
            }

            Button(
                onClick = onGoToStatsTab,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SoruLabCobaltBlue)
            ) {
                Text("İstatistiklerime Git", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ResultCircleStat(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .border(5.dp, color.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
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
