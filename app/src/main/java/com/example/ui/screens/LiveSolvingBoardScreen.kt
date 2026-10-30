package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.data.*
import com.example.util.StudioImageAndPdfEngine
import com.example.viewmodel.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 345 All Star 1-to-1 Test Çözüm, Optik Form, Test Sonu Fırça İzi (`✓`/`✗`),
 * `⋮` (Üç Nokta) Popover Menüsü, Yan Yana İkiz Soru Ekranı ve Başarı Durumu (Karne) Sayfası.
 */
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
    onAskAiForQuestion: (YksQuestion) -> Unit,
    onGenerateTwinQuestion: (YksQuestion, (TwinQuestionModalData) -> Unit) -> Unit = { _, _ -> },
    onGenerateSolutionForQuestion: (YksQuestion, (String) -> Unit) -> Unit = { _, _ -> },
    onAddQuestionToKumbara: (YksQuestion) -> Unit = {},
    customPublishers: List<YksPublisherInfo> = emptyList()
) {
    val questions = session.testItem.questions
    val totalQuestions = questions.size.coerceAtLeast(1)

    // 4 questions per 2-column book page (Left column: Q1 & Q2, Right column: Q3 & Q4)
    val questionsPerPage = 4
    val totalBookPages = ((totalQuestions + questionsPerPage - 1) / questionsPerPage).coerceAtLeast(1)
    val initialBookPage = (session.currentQuestionIndex / questionsPerPage).coerceIn(0, totalBookPages - 1)
    val bookPagerState = rememberPagerState(
        initialPage = initialBookPage,
        pageCount = { totalBookPages }
    )
    val singleQuestionPagerState = rememberPagerState(
        initialPage = session.currentQuestionIndex.coerceIn(0, totalQuestions - 1),
        pageCount = { totalQuestions }
    )
    val currentBookPageIndex = bookPagerState.currentPage
    val pagerScope = rememberCoroutineScope()

    // Keep HorizontalPager synced when currentQuestionIndex is updated externally
    LaunchedEffect(session.currentQuestionIndex, session.viewMode) {
        if (session.viewMode == BoardViewMode.FULL_PAGE) {
            val targetPage = (session.currentQuestionIndex / questionsPerPage).coerceIn(0, totalBookPages - 1)
            if (targetPage != bookPagerState.currentPage && !bookPagerState.isScrollInProgress) {
                bookPagerState.scrollToPage(targetPage)
            }
        } else {
            val targetQ = session.currentQuestionIndex.coerceIn(0, totalQuestions - 1)
            if (targetQ != singleQuestionPagerState.currentPage && !singleQuestionPagerState.isScrollInProgress) {
                singleQuestionPagerState.scrollToPage(targetQ)
            }
        }
    }

    // Update ViewModel question index when the user swipes horizontally between book pages
    LaunchedEffect(bookPagerState.settledPage, session.viewMode) {
        if (session.viewMode == BoardViewMode.FULL_PAGE) {
            val expectedFirstQ = (bookPagerState.settledPage * questionsPerPage).coerceIn(0, totalQuestions - 1)
            if (session.currentQuestionIndex / questionsPerPage != bookPagerState.settledPage) {
                onSelectQuestionIndex(expectedFirstQ)
            }
        }
    }

    LaunchedEffect(singleQuestionPagerState.settledPage, session.viewMode) {
        if (session.viewMode == BoardViewMode.SINGLE_QUESTION) {
            val settledQ = singleQuestionPagerState.settledPage.coerceIn(0, totalQuestions - 1)
            if (session.currentQuestionIndex != settledQ) {
                onSelectQuestionIndex(settledQ)
            }
        }
    }

    // Sub-screens inside LiveSolvingBoardScreen:
    // 1. Full-Screen Report Card ("▸ Başarı Durumuna Git" / Karne)
    var showFullReportCardScreen by remember(session.testItem.id) { mutableStateOf(false) }

    // 2. Full-Screen Side-by-Side "İkiz Soru" Comparison Screen
    var activeTwinScreenQuestion by remember { mutableStateOf<YksQuestion?>(null) }
    var activeTwinScreenData by remember { mutableStateOf<TwinQuestionModalData?>(null) }
    var isGeneratingTwin by remember { mutableStateOf(false) }

    // 3. `⋮` (Three-Dot) Popover active question number
    var openThreeDotQuestionNumber by remember { mutableStateOf<Int?>(null) }

    // 4. On-demand "Çözüm İzle" & "Konu Anlatımı" Modals
    var activeSolutionModalQuestion by remember { mutableStateOf<YksQuestion?>(null) }
    var activeSolutionText by remember { mutableStateOf("") }
    var isGeneratingSolution by remember { mutableStateOf(false) }
    var activeTopicLectureQuestion by remember { mutableStateOf<YksQuestion?>(null) }

    // Zoom & Pan state for the book page
    var isZoomMagnifierEnabled by remember { mutableStateOf(false) }
    var zoomScale by remember(currentBookPageIndex, session.viewMode) { mutableFloatStateOf(1f) }
    var panOffset by remember(currentBookPageIndex, session.viewMode) { mutableStateOf(Offset.Zero) }

    BackHandler {
        when {
            activeTwinScreenQuestion != null -> {
                activeTwinScreenQuestion = null
                activeTwinScreenData = null
            }
            showFullReportCardScreen -> {
                showFullReportCardScreen = false
            }
            openThreeDotQuestionNumber != null -> {
                openThreeDotQuestionNumber = null
            }
            else -> onExitSession()
        }
    }

    // Compute Doğru / Yanlış / Boş counts for Review Mode & Karne
    val correctCount = remember(questions, session.selectedAnswers) {
        questions.count { q -> q.answerKeyVerified && session.selectedAnswers[q.number] == q.correctOption }
    }
    val wrongCount = remember(questions, session.selectedAnswers) {
        questions.count { q ->
            val chosen = session.selectedAnswers[q.number]
            q.answerKeyVerified && !chosen.isNullOrBlank() && chosen != q.correctOption
        }
    }
    val ungradedCount = remember(questions, session.selectedAnswers) {
        questions.count { q -> !q.answerKeyVerified }
    }
    val emptyCount = remember(questions, session.selectedAnswers) {
        questions.count { q -> q.answerKeyVerified && session.selectedAnswers[q.number].isNullOrBlank() }
    }

    // =========================================================================
    // ALT EKRAN 1: TAM EKRAN YAN YANA "İKİZ SORU" EKRANI (345 ALL STAR BİREBİR)
    // =========================================================================
    val twinTargetQ = activeTwinScreenQuestion
    if (twinTargetQ != null) {
        FullScreenTwinQuestionComparisonView(
            session = session,
            originalQuestion = twinTargetQ,
            twinData = activeTwinScreenData,
            isLoadingTwin = isGeneratingTwin,
            studentAnswer = session.selectedAnswers[twinTargetQ.number],
            activeTool = activeTool,
            selectedStrokeSizeIndex = selectedStrokeSizeIndex,
            selectedPenColorHex = selectedPenColorHex,
            onSelectTool = onSelectTool,
            onSelectStrokeSize = onSelectStrokeSize,
            onSelectPenColor = onSelectPenColor,
            onBackToBookReview = {
                activeTwinScreenQuestion = null
                activeTwinScreenData = null
            },
            onOpenReportCard = {
                activeTwinScreenQuestion = null
                activeTwinScreenData = null
                showFullReportCardScreen = true
            }
        )
        return
    }

    // =========================================================================
    // ALT EKRAN 2: TAM EKRAN "▸ BAŞARI DURUMUNA GİT" KARNE & ANALİZ EKRANI
    // =========================================================================
    if (showFullReportCardScreen && session.isReviewMode) {
        FullScreenSuccessReportCardView(
            session = session,
            correctCount = correctCount,
            wrongCount = wrongCount,
            emptyCount = emptyCount,
            ungradedCount = ungradedCount,
            showTwinSuggestion = wrongCount > 0,
            onBackToBookPage = {
                showFullReportCardScreen = false
            },
            onSolveTwinTestForWrongQuestions = {
                val firstWrong = questions.firstOrNull { q ->
                    q.answerKeyVerified && session.selectedAnswers[q.number].let { !it.isNullOrBlank() && it != q.correctOption }
                }
                if (firstWrong != null) {
                    showFullReportCardScreen = false
                    activeTwinScreenQuestion = firstWrong
                    activeTwinScreenData = null
                    isGeneratingTwin = true
                    onGenerateTwinQuestion(firstWrong) { generated ->
                        activeTwinScreenData = generated
                        isGeneratingTwin = false
                    }
                }
            },
            onWatchQuestionSolution = { q ->
                showFullReportCardScreen = false
                activeSolutionModalQuestion = q
                activeSolutionText = ""
                isGeneratingSolution = true
                onGenerateSolutionForQuestion(q) { text ->
                    activeSolutionText = text
                    isGeneratingSolution = false
                }
            },
            onOpenTwinForQuestion = { q ->
                showFullReportCardScreen = false
                activeTwinScreenQuestion = q
                activeTwinScreenData = null
                isGeneratingTwin = true
                onGenerateTwinQuestion(q) { generated ->
                    activeTwinScreenData = generated
                    isGeneratingTwin = false
                }
            }
        )
        return
    }

    // =========================================================================
    // ANA EKRAN: ÜST BEYAZ BAR + ÜST KOYU LACİVERT ÇİZİM BARI + TAM EKRAN 2 SÜTUNLU KİTAP SAYFASI + SAĞ ÇEKMECELER
    // =========================================================================
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE2E8F0))
            .testTag("live_solving_board")
    ) {
        // 1. ÜST BEYAZ BAR: Solda Geri Oku, Ortada Test Başlığı, Sağda Mavi Hap Buton
        BookPageTopBar345(
            testTitle = session.testItem.fullTitle.ifBlank { "${session.topicName} - Test 1" },
            isReviewMode = session.isReviewMode,
            onBack = onExitSession,
            onFinishTest = {
                openThreeDotQuestionNumber = null
                onFinishTest()
            },
            onOpenReportCard = {
                openThreeDotQuestionNumber = null
                showFullReportCardScreen = true
            }
        )

        // 2. ÜST KOYU LACİVERT (`#1E295B`) ÇİZİM VE ARAÇ BARI (Üst Beyaz Barın Hemen Altında!)
        Box(modifier = Modifier.zIndex(40f)) {
            BottomDarkNavyDrawingBar345(
                viewMode = session.viewMode,
                activeTool = activeTool,
                isZoomMagnifierEnabled = isZoomMagnifierEnabled,
                selectedStrokeSizeIndex = selectedStrokeSizeIndex,
                selectedPenColorHex = selectedPenColorHex,
                elapsedSeconds = session.elapsedSeconds,
                onToggleViewMode = onToggleViewMode,
                onSelectTool = { tool ->
                    isZoomMagnifierEnabled = false
                    onSelectTool(tool)
                },
                onToggleZoom = {
                    isZoomMagnifierEnabled = !isZoomMagnifierEnabled
                    if (isZoomMagnifierEnabled && zoomScale < 1.35f) {
                        zoomScale = 1.45f
                    } else if (!isZoomMagnifierEnabled) {
                        zoomScale = 1f
                        panOffset = Offset.Zero
                    }
                },
                onSelectStrokeSize = onSelectStrokeSize,
                onSelectPenColor = onSelectPenColor,
                onClearCanvas = onClearCanvas
            )
        }

        // 3. TAM EKRAN KİTAP SAYFASI ALANI VE SAĞDAN AÇILIR ÇEKMECELER (OPTİK & İSTATİSTİKLER)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val strokeWidths = listOf(4.5f, 8.0f, 13.0f)
            val currentStrokePx = strokeWidths.getOrElse(selectedStrokeSizeIndex) { 8.0f }

            // Zero-latency raw path buffer (mutated directly during S-Pen/Mouse drag without recomposing UI tree!)
            val liveRawPath = remember { Path() }
            val liveRawPoints = remember { mutableListOf<Offset>() }
            val liveDrawTick = remember { mutableIntStateOf(0) }

            // Book Page Container with Horizontal Finger/Hand Swipe (HorizontalPager) + Pinch-to-Zoom support
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFCBD5E1))
                    .shadow(2.dp, RoundedCornerShape(8.dp))
                    .pointerInput(isZoomMagnifierEnabled, zoomScale) {
                        if (isZoomMagnifierEnabled || zoomScale > 1.02f) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (zoomScale * zoom).coerceIn(1f, 3.0f)
                                zoomScale = newScale
                                panOffset = if (newScale > 1.01f) {
                                    Offset(
                                        x = (panOffset.x + pan.x).coerceIn(-900f, 900f),
                                        y = (panOffset.y + pan.y).coerceIn(-900f, 900f)
                                    )
                                } else {
                                    Offset.Zero
                                }
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    }
            ) {
                // 1. YATAY KAYDIRMALI (SWIPE / SCROLL-SNAP) GERÇEK KİTAP SAYFALARI (3D Sayfa Çevirme Hissiyle)
                if (session.viewMode == BoardViewMode.FULL_PAGE) {
                    HorizontalPager(
                        state = bookPagerState,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = zoomScale <= 1.02f && openThreeDotQuestionNumber == null,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("book_horizontal_pager")
                    ) { pageIdx ->
                        val pageStartQIdx = pageIdx * questionsPerPage
                        val pageQuestions = questions.drop(pageStartQIdx).take(questionsPerPage)
                        val pageOffset = (bookPagerState.currentPage - pageIdx) + bookPagerState.currentPageOffsetFraction

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    cameraDistance = 16f * density
                                    rotationY = (-pageOffset * 14f).coerceIn(-18f, 18f)
                                    val scale = (1f - (abs(pageOffset) * 0.04f)).coerceIn(0.94f, 1f)
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = (1f - (abs(pageOffset) * 0.15f)).coerceIn(0.8f, 1f)
                                }
                        ) {
                            RealTwoColumnBookPageSurface(
                                session = session,
                                pageQuestions = pageQuestions,
                                currentBookPageIndex = pageIdx,
                                totalBookPages = totalBookPages,
                                customPublishers = customPublishers,
                                openThreeDotQuestionNumber = openThreeDotQuestionNumber,
                                onDismissThreeDotMenu = {
                                    openThreeDotQuestionNumber = null
                                },
                                onToggleThreeDotMenu = { qNum ->
                                    openThreeDotQuestionNumber = if (openThreeDotQuestionNumber == qNum) null else qNum
                                },
                                onSelectOptionInBook = { qNum, opt ->
                                    onSelectOption(qNum, opt)
                                },
                                onOpenTopicLecture = { q ->
                                    openThreeDotQuestionNumber = null
                                    activeTopicLectureQuestion = q
                                },
                                onOpenSolutionModal = { q ->
                                    openThreeDotQuestionNumber = null
                                    activeSolutionModalQuestion = q
                                    activeSolutionText = ""
                                    isGeneratingSolution = true
                                    onGenerateSolutionForQuestion(q) { text ->
                                        activeSolutionText = text
                                        isGeneratingSolution = false
                                    }
                                },
                                onOpenTwinScreen = { q ->
                                    openThreeDotQuestionNumber = null
                                    activeTwinScreenQuestion = q
                                    activeTwinScreenData = null
                                    isGeneratingTwin = true
                                    onGenerateTwinQuestion(q) { generated ->
                                        activeTwinScreenData = generated
                                        isGeneratingTwin = false
                                    }
                                },
                                onAddToKumbara = { q ->
                                    openThreeDotQuestionNumber = null
                                    onAddQuestionToKumbara(q)
                                },
                                onPrevBookPage = {
                                    if (pageIdx > 0) {
                                        pagerScope.launch {
                                            bookPagerState.animateScrollToPage(pageIdx - 1)
                                        }
                                    }
                                },
                                onNextBookPage = {
                                    if (pageIdx < totalBookPages - 1) {
                                        pagerScope.launch {
                                            bookPagerState.animateScrollToPage(pageIdx + 1)
                                        }
                                    }
                                }
                            )
                        }
                    }
                } else {
                    HorizontalPager(
                        state = singleQuestionPagerState,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = zoomScale <= 1.02f && openThreeDotQuestionNumber == null,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("single_question_horizontal_pager")
                    ) { qIdx ->
                        val currentQuestion = questions.getOrNull(qIdx) ?: questions.first()
                        val pageOffset = (singleQuestionPagerState.currentPage - qIdx) + singleQuestionPagerState.currentPageOffsetFraction

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    cameraDistance = 16f * density
                                    rotationY = (-pageOffset * 14f).coerceIn(-18f, 18f)
                                    val scale = (1f - (abs(pageOffset) * 0.04f)).coerceIn(0.94f, 1f)
                                    scaleX = scale
                                    scaleY = scale
                                }
                        ) {
                            SingleQuestionFocusedPageSurface(
                                session = session,
                                question = currentQuestion,
                                customPublishers = customPublishers,
                                openThreeDotQuestionNumber = openThreeDotQuestionNumber,
                                onDismissThreeDotMenu = {
                                    openThreeDotQuestionNumber = null
                                },
                                onToggleThreeDotMenu = { qNum ->
                                    openThreeDotQuestionNumber = if (openThreeDotQuestionNumber == qNum) null else qNum
                                },
                                onSelectOption = { opt ->
                                    onSelectOption(currentQuestion.number, opt)
                                },
                                onOpenTopicLecture = { q ->
                                    openThreeDotQuestionNumber = null
                                    activeTopicLectureQuestion = q
                                },
                                onOpenSolutionModal = { q ->
                                    openThreeDotQuestionNumber = null
                                    activeSolutionModalQuestion = q
                                    activeSolutionText = ""
                                    isGeneratingSolution = true
                                    onGenerateSolutionForQuestion(q) { text ->
                                        activeSolutionText = text
                                        isGeneratingSolution = false
                                    }
                                },
                                onOpenTwinScreen = { q ->
                                    openThreeDotQuestionNumber = null
                                    activeTwinScreenQuestion = q
                                    activeTwinScreenData = null
                                    isGeneratingTwin = true
                                    onGenerateTwinQuestion(q) { generated ->
                                        activeTwinScreenData = generated
                                        isGeneratingTwin = false
                                    }
                                },
                                onAddToKumbara = { q ->
                                    openThreeDotQuestionNumber = null
                                    onAddQuestionToKumbara(q)
                                }
                            )
                        }
                    }
                }

                // 2. SIFIR GECİKMELİ (ZERO-LATENCY) S-PEN & FARE HAM ÇİZİM KATMANI + BLENDMODE.CLEAR (DESTINATION-OUT) SİLGİ
                // KATI KURAL:
                // - PointerType.Stylus / PointerType.Eraser -> Doğrudan çizer veya siler.
                // - PointerType.Touch (Parmak) -> Kesinlikle çizim yapmaz (`return`), parmakla yatay sayfa çevirme (swipe) ve dikey scroll çalışır!
                // - PointerType.Mouse (Emülatör) -> Sayfa kenarlarından/alt şeridinden yapılan yatay sayfa çevirme hareketlerini engellemez,
                //   soru üzerinde yapılan çizimleri sıfır gecikmeyle çizer.
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .then(
                            if ((activeTool == DrawingTool.PEN ||
                                    activeTool == DrawingTool.HIGHLIGHTER ||
                                    activeTool == DrawingTool.SMART_SHAPE ||
                                    activeTool == DrawingTool.ERASER) &&
                                openThreeDotQuestionNumber == null
                            ) {
                                Modifier.pointerInput(
                                    activeTool,
                                    selectedStrokeSizeIndex,
                                    selectedPenColorHex,
                                    currentBookPageIndex,
                                    session.viewMode
                                ) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        // Finger touches (PointerType.Touch) NEVER draw so HorizontalPager swipe & scroll work smoothly!
                                        if (down.type == PointerType.Touch) {
                                            return@awaitEachGesture
                                        }
                                        val isStylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                                        val isMouse = down.type == PointerType.Mouse
                                        if (!isStylus && !isMouse) {
                                            return@awaitEachGesture
                                        }

                                        // If Mouse starts in the outer page-turn margins or bottom book page bar, let HorizontalPager/buttons handle it!
                                        val edgeMarginPx = 54.dp.toPx()
                                        val bottomBarPx = 58.dp.toPx()
                                        if (isMouse && (
                                                down.position.x < edgeMarginPx ||
                                                    down.position.x > size.width - edgeMarginPx ||
                                                    down.position.y > size.height - bottomBarPx
                                                )
                                        ) {
                                            return@awaitEachGesture
                                        }

                                        var overSlopOffset = Offset.Zero
                                        val dragStartChange = awaitTouchSlopOrCancellation(down.id) { change, overSlop ->
                                            overSlopOffset = overSlop
                                            // If using Mouse in emulator and the user performs a strong horizontal page-swipe gesture,
                                            // do NOT consume it so HorizontalPager turns the book page!
                                            val isHorizontalPageSwipe = isMouse &&
                                                abs(overSlop.x) > abs(overSlop.y) * 2.4f &&
                                                abs(overSlop.x) > 14f
                                            if (!isHorizontalPageSwipe) {
                                                change.consume()
                                            }
                                        }
                                        val isMousePageSwipe = isMouse &&
                                            abs(overSlopOffset.x) > abs(overSlopOffset.y) * 2.4f &&
                                            abs(overSlopOffset.x) > 14f
                                        if (isMousePageSwipe) {
                                            return@awaitEachGesture
                                        }
                                        if (dragStartChange != null) {
                                            liveRawPoints.clear()
                                            liveRawPoints.add(down.position)
                                            liveRawPoints.add(dragStartChange.position)

                                            liveRawPath.reset()
                                            liveRawPath.moveTo(down.position.x, down.position.y)
                                            liveRawPath.lineTo(dragStartChange.position.x, dragStartChange.position.y)
                                            liveDrawTick.intValue++

                                            var prevPt = dragStartChange.position
                                            drag(dragStartChange.id) { change ->
                                                change.consume()
                                                val curPt = change.position
                                                liveRawPoints.add(curPt)
                                                val midX = (prevPt.x + curPt.x) * 0.5f
                                                val midY = (prevPt.y + curPt.y) * 0.5f
                                                liveRawPath.quadraticTo(prevPt.x, prevPt.y, midX, midY)
                                                prevPt = curPt
                                                // Draw-phase-only invalidation (zero Composable recomposition!)
                                                liveDrawTick.intValue++
                                            }

                                            // Commit stroke to ViewModel state ONLY on pointerUp!
                                            if (liveRawPoints.size >= 2) {
                                                onAddStroke(liveRawPoints.toList())
                                            }
                                            liveRawPoints.clear()
                                            liveRawPath.reset()
                                            liveDrawTick.intValue++
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    // Read liveDrawTick inside draw scope so only the Canvas draw layer invalidates during drag
                    val tick = liveDrawTick.intValue
                    strokes.forEach { stroke ->
                        drawSmoothStroke(stroke)
                    }
                    if (tick >= 0 && liveRawPoints.size >= 2) {
                        val isEraser = activeTool == DrawingTool.ERASER
                        val isHigh = activeTool == DrawingTool.HIGHLIGHTER
                        if (isEraser) {
                            drawPath(
                                path = liveRawPath,
                                color = Color.Transparent,
                                style = Stroke(
                                    width = 38f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                ),
                                blendMode = BlendMode.Clear
                            )
                        } else {
                            drawPath(
                                path = liveRawPath,
                                color = if (isHigh) Color(selectedPenColorHex).copy(alpha = 0.36f) else Color(selectedPenColorHex),
                                style = Stroke(
                                    width = if (isHigh) currentStrokePx * 2.6f else currentStrokePx,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
            }

            // 4. SAĞ KENAR DİKEY KULAKÇIK VE AÇILIR ÇEKMECELER
            // - Çözüm esnasında: kompakt "⠿ Optik"
            // - Test bittiğinde (Review Mode): "◔ İstatistikler"
            if (!session.isReviewMode) {
                RightSlidingOpticalFormDrawer345(
                    isOpen = session.isOpticalFormDrawerOpen,
                    questions = questions,
                    selectedAnswers = session.selectedAnswers,
                    onToggleDrawer = onToggleOpticalForm,
                    onSelectOption = onSelectOption,
                    onFinishTest = {
                        openThreeDotQuestionNumber = null
                        onFinishTest()
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .zIndex(35f)
                )
            } else {
                RightSlidingStatsDrawer345(
                    isOpen = session.isStatsDrawerOpen,
                    testTitle = session.testItem.fullTitle.ifBlank { "${session.topicName} - Test 1" },
                    correctCount = correctCount,
                    wrongCount = wrongCount,
                    emptyCount = emptyCount,
                    ungradedCount = ungradedCount,
                    onToggleDrawer = onToggleStatsDrawer,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .zIndex(35f)
                )
            }
        }
    }

    // =========================================================================
    // MODAL 1: "ÇÖZÜM İZLE" ADIM ADIM AI ÇÖZÜM PENCERESİ
    // =========================================================================
    val solutionQ = activeSolutionModalQuestion
    if (solutionQ != null) {
        Dialog(onDismissRequest = { activeSolutionModalQuestion = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 380.dp, max = 600.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
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
                                color = Color(0xFF3B82F6),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircleFilled,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "${solutionQ.number}. Soru • Adım Adım Çözüm",
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = if (solutionQ.answerKeyVerified) "${solutionQ.topicHeader} • Doğru Yanıt: ${solutionQ.correctOption}"
                                    else "${solutionQ.topicHeader} • Cevap anahtarı doğrulanmadı",
                                    color = if (solutionQ.answerKeyVerified) Color(0xFF16A34A) else Color(0xFFB45309),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                        IconButton(onClick = { activeSolutionModalQuestion = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color(0xFF64748B))
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    if (isGeneratingSolution) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = Color(0xFF2563EB))
                            Text(
                                text = "Soru görseli inceleniyor ve 3 aşamalı adım adım çözüm hazırlanıyor...",
                                color = Color(0xFF475569),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        val rawSol = activeSolutionText.ifBlank {
                            solutionQ.stepByStepSolution.ifBlank {
                                "Cevap anahtarına göre doğru yanıt ${solutionQ.correctOption} seçeneğidir."
                            }
                        }
                        val structuredSol = remember(rawSol) {
                            com.example.data.AiStructuredSolution.fromFormattedText(rawSol)
                        }
                        val solutionQuestionBitmap = StudioImageAndPdfEngine.rememberCachedBitmap(solutionQ.croppedImagePath)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 460.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Temizlenmiş Soru Görseli Üst Önizleme Kartı
                            if (solutionQuestionBitmap != null) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val aspect = (solutionQuestionBitmap.width.toFloat() / solutionQuestionBitmap.height.toFloat().coerceAtLeast(1f))
                                        .coerceIn(0.6f, 2.8f)
                                    Image(
                                        bitmap = solutionQuestionBitmap.asImageBitmap(),
                                        contentDescription = "Temizlenmiş Soru Görseli",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 175.dp)
                                            .aspectRatio(aspect)
                                            .padding(8.dp)
                                    )
                                }
                            }

                            // 1. AŞAMA: Kritik Bilgi & Teorem
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
                                        text = "1. Kritik Bilgi & Teorem",
                                        color = Color(0xFF1D4ED8),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp
                                    )
                                    Text(
                                        text = structuredSol.kritikBilgiTeorem.ifBlank {
                                            "${solutionQ.topicHeader} kazanımına ait temel bağıntı ve sınır koşulları uygulanır."
                                        },
                                        color = Color(0xFF1E293B),
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // 2. AŞAMA: Adım Adım İşlem
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF0FDF4),
                                border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "2. Adım Adım İşlem",
                                        color = Color(0xFF15803D),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp
                                    )
                                    Text(
                                        text = structuredSol.adimAdimCozum.ifBlank { rawSol },
                                        color = Color(0xFF1E293B),
                                        fontSize = 13.5.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // 3. AŞAMA: ÖSYM Tuzak Notu
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFFFF7ED),
                                border = BorderStroke(1.dp, Color(0xFFFED7AA)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "3. ÖSYM Tuzak Notu",
                                        color = Color(0xFFEA580C),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.5.sp
                                    )
                                    Text(
                                        text = structuredSol.osymTuzakNotu.ifBlank {
                                            "Öncül şartlarına, işaret değişimlerine ve birim dönüşümlerine dikkat edilmelidir."
                                        },
                                        color = Color(0xFF334155),
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { activeSolutionModalQuestion = null },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Anladım, Kapat", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // MODAL 2: "KONU ANLATIMI" ÖZET FORMÜL & KAZANIM KARTI
    // =========================================================================
    val lectureQ = activeTopicLectureQuestion
    if (lectureQ != null) {
        Dialog(onDismissRequest = { activeTopicLectureQuestion = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 380.dp, max = 560.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                color = Color(0xFFDBEAFE),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "${lectureQ.topicHeader} • Konu Özeti",
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.5.sp
                                )
                                Text(
                                    text = "${session.examType.name} ${session.subjectName} Kritik Kazanım Notları",
                                    color = Color(0xFF2563EB),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        IconButton(onClick = { activeTopicLectureQuestion = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color(0xFF64748B))
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "1. Temel Kural & Bağıntı",
                                color = Color(0xFF1E3A8A),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = "${lectureQ.topicHeader} sorularında verilen referans sistemine ve başlangıç koşullarına dikkat edilmelidir. İfadeler sadeleştirilirken tanım kümesi ve birim dönüşümleri korunur.",
                                color = Color(0xFF334155),
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "2. ÖSYM Soru Yaklaşımı (${lectureQ.difficulty.label} Seviye)",
                                color = Color(0xFF1E3A8A),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = "Öncüllü ve grafikli sorularda sınır değerleri (t = 0 anı, uç noktalar ve işaret değişimleri) mutlaka ayrı ayrı kontrol edilmelidir.",
                                color = Color(0xFF334155),
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { activeTopicLectureQuestion = null },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Kapat", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 1. ÜST BEYAZ BAR (345 ALL STAR BİREBİR):
 * - Solda Geri Oku (`←`)
 * - Ortada Test Başlığı (Örn: `Bağıl Hareket - Günlük Hayat` veya `Türev - Test 1`)
 * - Sağda:
 *   * Çözüm esnasında: Mavi hap buton `⚑ Testi Bitir`
 *   * İnceleme (Review) modunda: Mavi hap buton `▸ Başarı Durumuna Git`
 */
@Composable
private fun BookPageTopBar345(
    testTitle: String,
    isReviewMode: Boolean,
    onBack: () -> Unit,
    onFinishTest: () -> Unit,
    onOpenReportCard: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("board_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = Color(0xFF0F172A)
                )
            }

            Text(
                text = testTitle,
                color = Color(0xFF0F172A),
                fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )

            if (!isReviewMode) {
                Surface(
                    onClick = onFinishTest,
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF3B82F6),
                    shadowElevation = 2.dp,
                    modifier = Modifier.testTag("top_finish_test_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Testi Bitir",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                Surface(
                    onClick = onOpenReportCard,
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF3B82F6),
                    shadowElevation = 2.dp,
                    modifier = Modifier.testTag("go_to_report_card_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Başarı Durumuna Git",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * GERÇEK KİTAP SAYFASI DÜZENİ (ÇİFT SÜTUNLU SAYFA):
 * - Sorular ayrı ayrı kutu/kart içine KONULMAZ!
 * - Tek bir beyaz kitap sayfası zemini üzerinde ortadan ince dikey çizgiyle ayrılmış
 *   2 SÜTUNLU (Sol Sütun: 1 ve 2, Sağ Sütun: 3 ve 4) gerçek soru bankası sayfası düzeni.
 */
@Composable
private fun RealTwoColumnBookPageSurface(
    session: ActiveSolvingSession,
    pageQuestions: List<YksQuestion>,
    currentBookPageIndex: Int,
    totalBookPages: Int,
    customPublishers: List<YksPublisherInfo>,
    openThreeDotQuestionNumber: Int?,
    onDismissThreeDotMenu: () -> Unit,
    onToggleThreeDotMenu: (Int) -> Unit,
    onSelectOptionInBook: (Int, String) -> Unit,
    onOpenTopicLecture: (YksQuestion) -> Unit,
    onOpenSolutionModal: (YksQuestion) -> Unit,
    onOpenTwinScreen: (YksQuestion) -> Unit,
    onAddToKumbara: (YksQuestion) -> Unit,
    onPrevBookPage: () -> Unit = {},
    onNextBookPage: () -> Unit = {}
) {
    val leftColumnQuestions = remember(pageQuestions) { pageQuestions.take(2) }
    val rightColumnQuestions = remember(pageQuestions) { pageQuestions.drop(2).take(2) }
    val scrollState = rememberScrollState()

    // Tam Ekran Kitap Sayfası Deneyimi (Üstte gereksiz başlıklar kaldırıldı, alttaki boşluk sıfırlandı)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        val pageMinHeight = maxHeight

        // Dışarı tıklandığında açık olan `⋮` Popover menüsünü kapatan şeffaf arka plan katmanı
        if (openThreeDotQuestionNumber != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(15f)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissThreeDotMenu
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (openThreeDotQuestionNumber != null) 30f else 1f)
                .heightIn(min = pageMinHeight)
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // GERÇEK BASILI TEST KİTABI ÜST ŞERİDİ (Sayfa Başlığı & Konu - Test No)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
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
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF2563EB))
                        )
                        Text(
                            text = "${session.subjectName.uppercase()}  •  ${session.topicName.uppercase()}",
                            color = Color(0xFF334155),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp,
                            letterSpacing = 0.4.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                    ) {
                        Text(
                            text = session.testItem.title.uppercase(),
                            color = Color(0xFF1D4ED8),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.2.dp)
                        .background(Color(0xFFCBD5E1))
                )
            }

            // 2x2 YAPRAK TEST GÖRÜNÜMÜ: SATIR BAZLI ÜSTTEN HİZALAMA (Alignment.Top)
            // Sol ve sağ sütundaki soruların birinin yukarıda birinin aşağıda durmasını engeller
            val qTopLeft = pageQuestions.getOrNull(0)
            val qBottomLeft = pageQuestions.getOrNull(1)
            val qTopRight = pageQuestions.getOrNull(2)
            val qBottomRight = pageQuestions.getOrNull(3)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = (pageMinHeight - 76.dp).coerceAtLeast(360.dp))
            ) {
                // Ortadaki ince dikey kitap çizgisi (tüm sayfa yüksekliğince uzanır)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(Color(0xFFCBD5E1))
                        .align(Alignment.Center)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    // 1. SATIR: Soru 1 (Sol) ve Soru 3 (Sağ) - ÜSTTEN KESİN HİZALI (Alignment.Top)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            if (qTopLeft != null) {
                                BookPageQuestionItem345(
                                    question = qTopLeft,
                                    isReviewMode = session.isReviewMode,
                                    selectedOption = session.selectedAnswers[qTopLeft.number],
                                    isThreeDotMenuOpen = openThreeDotQuestionNumber == qTopLeft.number,
                                    customPublishers = customPublishers,
                                    onToggleThreeDotMenu = { onToggleThreeDotMenu(qTopLeft.number) },
                                    onSelectOption = { opt -> onSelectOptionInBook(qTopLeft.number, opt) },
                                    onOpenTopicLecture = { onOpenTopicLecture(qTopLeft) },
                                    onOpenSolutionModal = { onOpenSolutionModal(qTopLeft) },
                                    onOpenTwinScreen = { onOpenTwinScreen(qTopLeft) },
                                    onAddToKumbara = { onAddToKumbara(qTopLeft) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(1.dp))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            if (qTopRight != null) {
                                BookPageQuestionItem345(
                                    question = qTopRight,
                                    isReviewMode = session.isReviewMode,
                                    selectedOption = session.selectedAnswers[qTopRight.number],
                                    isThreeDotMenuOpen = openThreeDotQuestionNumber == qTopRight.number,
                                    customPublishers = customPublishers,
                                    onToggleThreeDotMenu = { onToggleThreeDotMenu(qTopRight.number) },
                                    onSelectOption = { opt -> onSelectOptionInBook(qTopRight.number, opt) },
                                    onOpenTopicLecture = { onOpenTopicLecture(qTopRight) },
                                    onOpenSolutionModal = { onOpenSolutionModal(qTopRight) },
                                    onOpenTwinScreen = { onOpenTwinScreen(qTopRight) },
                                    onAddToKumbara = { onAddToKumbara(qTopRight) }
                                )
                            }
                        }
                    }

                    // 2. SATIR: Soru 2 (Sol) ve Soru 4 (Sağ) - ÜSTTEN KESİN HİZALI (Alignment.Top)
                    if (qBottomLeft != null || qBottomRight != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 16.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                if (qBottomLeft != null) {
                                    BookPageQuestionItem345(
                                        question = qBottomLeft,
                                        isReviewMode = session.isReviewMode,
                                        selectedOption = session.selectedAnswers[qBottomLeft.number],
                                        isThreeDotMenuOpen = openThreeDotQuestionNumber == qBottomLeft.number,
                                        customPublishers = customPublishers,
                                        onToggleThreeDotMenu = { onToggleThreeDotMenu(qBottomLeft.number) },
                                        onSelectOption = { opt -> onSelectOptionInBook(qBottomLeft.number, opt) },
                                        onOpenTopicLecture = { onOpenTopicLecture(qBottomLeft) },
                                        onOpenSolutionModal = { onOpenSolutionModal(qBottomLeft) },
                                        onOpenTwinScreen = { onOpenTwinScreen(qBottomLeft) },
                                        onAddToKumbara = { onAddToKumbara(qBottomLeft) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(1.dp))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                if (qBottomRight != null) {
                                    BookPageQuestionItem345(
                                        question = qBottomRight,
                                        isReviewMode = session.isReviewMode,
                                        selectedOption = session.selectedAnswers[qBottomRight.number],
                                        isThreeDotMenuOpen = openThreeDotQuestionNumber == qBottomRight.number,
                                        customPublishers = customPublishers,
                                        onToggleThreeDotMenu = { onToggleThreeDotMenu(qBottomRight.number) },
                                        onSelectOption = { opt -> onSelectOptionInBook(qBottomRight.number, opt) },
                                        onOpenTopicLecture = { onOpenTopicLecture(qBottomRight) },
                                        onOpenSolutionModal = { onOpenSolutionModal(qBottomRight) },
                                        onOpenTwinScreen = { onOpenTwinScreen(qBottomRight) },
                                        onAddToKumbara = { onAddToKumbara(qBottomRight) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Alt Kitap Sayfası Bilgisi (Eski ← ve → sayfa geçiş okları tamamen kaldırıldı; geçiş sadece HorizontalPager swipe ile yapılır)
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sayfa ${currentBookPageIndex + 1} / $totalBookPages  •  Parmakla Sağa/Sola Kaydırın",
                        color = Color(0xFF475569),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * KİTAP SAYFASINDAKİ TEKİL SORU GÖRÜNÜMÜ (KUTUSUZ / KARTSIZ SAF KİTAP DÜZENİ):
 * - Sol başta: Uygulama soru numarası (`1.`, `2.`, `3.`, `4.`)
 * - Test Bittiğinde (`isReviewMode = true`):
 *   * Sol üst köşede (soru numarasının hemen üstünde/yanında):
 *     - Doğruysa: Yeşil Tik (`✓`, `#16A34A`)
 *     - Yanlışsa: Kırmızı Çarpı (`✗`, `#DC2626`)
 *     - Boş bırakıldıysa: Gri Yuvarlak / Eksi (`⊖`, `#94A3B8`) — ASLA kırmızı çarpı atılmaz!
 *   * Sağ üst köşede yüksek z-index'li (`zIndex(50f)`) mavi (`#60A5FA`) yuvarlak içinde beyaz `⋮` (Üç Nokta) butonu
 *     ve tıklanınca hemen altında açılan 5 kademeli zorluk barlı Popover Kartı!
 */
@Composable
private fun BookPageQuestionItem345(
    question: YksQuestion,
    isReviewMode: Boolean,
    selectedOption: String?,
    isThreeDotMenuOpen: Boolean,
    customPublishers: List<YksPublisherInfo>,
    onToggleThreeDotMenu: () -> Unit,
    onSelectOption: (String) -> Unit,
    onOpenTopicLecture: () -> Unit,
    onOpenSolutionModal: () -> Unit,
    onOpenTwinScreen: () -> Unit,
    onAddToKumbara: () -> Unit
) {
    val croppedBitmap = StudioImageAndPdfEngine.rememberCachedBitmap(question.croppedImagePath)
    val isEmpty = selectedOption.isNullOrBlank()
    val isUngraded = !question.answerKeyVerified
    val isCorrect = !isEmpty && question.answerKeyVerified && selectedOption == question.correctOption

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isThreeDotMenuOpen) 55f else 1f)
            .testTag("book_question_${question.number}")
    ) {
        // 1. SORU İÇERİĞİ: Sol başta soru numarası, sağında saf soru görseli
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sol kenarda yalnızca uygulamanın soru numarası gösterilir.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(30.dp)
            ) {
                Text(
                    text = "${question.number}.",
                    color = Color(0xFF2563EB),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }

            // Sağ Kısım: Numarasız Saf Kırpılmış Soru Görseli (veya Metin/Diyagram)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (croppedBitmap != null) {
                    val aspect = croppedBitmap.width.toFloat() / croppedBitmap.height.toFloat().coerceAtLeast(1f)
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopStart
                    ) {
                        // Gerçek PDF kitap sayfası orantısı ve ölçekleme:
                        // Kısa denklemli soruların tüm sütuna yayılıp devasa ve bulanık büyümesini kesin olarak engeller.
                        val maxDisplayHeight = 340.dp
                        val baseScaleFactor = 0.85f // Gerçek basılı kitap punto dengesi
                        val naturalWidthDp = with(LocalDensity.current) { (croppedBitmap.width * baseScaleFactor).toDp() }
                        val naturalHeightDp = with(LocalDensity.current) { (croppedBitmap.height * baseScaleFactor).toDp() }

                        val boundedHeight = minOf(naturalHeightDp, maxDisplayHeight)
                        val derivedWidthFromHeight = boundedHeight * aspect
                        val finalWidth = minOf(maxWidth, naturalWidthDp, derivedWidthFromHeight).coerceAtLeast(120.dp)

                        Image(
                            bitmap = croppedBitmap.asImageBitmap(),
                            contentDescription = "${question.number}. Soru",
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.TopStart,
                            modifier = Modifier
                                .width(finalWidth)
                                .heightIn(max = maxDisplayHeight)
                                .aspectRatio(aspect)
                                .background(Color.White)
                        )
                    }
                    // Kitap sayfasında her kırpılmış sorunun hemen altında hızlı şık işaretleme barı (A B C D E)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("A", "B", "C", "D", "E").forEach { optKey ->
                            val isChosen = selectedOption == optKey
                            val isRightOpt = isReviewMode && question.answerKeyVerified && optKey == question.correctOption
                            val isWrongChosen = isReviewMode && question.answerKeyVerified && isChosen && optKey != question.correctOption
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    isRightOpt -> Color(0xFFDCFCE7)
                                    isWrongChosen -> Color(0xFFFEE2E2)
                                    isChosen -> Color(0xFF2563EB)
                                    else -> Color(0xFFF8FAFC)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isRightOpt -> Color(0xFF16A34A)
                                        isWrongChosen -> Color(0xFFDC2626)
                                        isChosen -> Color(0xFF2563EB)
                                        else -> Color(0xFFCBD5E1)
                                    }
                                ),
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(enabled = !isReviewMode) { onSelectOption(optKey) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = optKey,
                                        color = when {
                                            isRightOpt -> Color(0xFF15803D)
                                            isWrongChosen -> Color(0xFFB91C1C)
                                            isChosen -> Color.White
                                            else -> Color(0xFF475569)
                                        },
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Fallback vector/text rendering if a catalog question has no bitmap
                    if (question.stemTop.isNotBlank()) {
                        Text(
                            text = question.stemTop,
                            color = Color(0xFF1E293B),
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (question.visualType != QuestionVisualType.NONE) {
                        QuestionVectorDiagram(
                            visualType = question.visualType,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(155.dp)
                        )
                    }
                    if (question.bulletsOrSteps.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            question.bulletsOrSteps.forEach { step ->
                                Text(
                                    text = step,
                                    color = Color(0xFF334155),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    if (question.stemBottom.isNotBlank()) {
                        Text(
                            text = question.stemBottom,
                            color = Color(0xFF0F172A),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Kitap tarzı yatay A) B) C) D) E) şıkları
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("A", "B", "C", "D", "E").forEach { optKey ->
                            val optVal = question.options[optKey] ?: optKey
                            val isChosen = selectedOption == optKey
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            isReviewMode && question.answerKeyVerified && optKey == question.correctOption -> Color(0xFFDCFCE7)
                                            isChosen -> Color(0xFFDBEAFE)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable(enabled = !isReviewMode) { onSelectOption(optKey) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (optVal == optKey) "$optKey)" else "$optKey) $optVal",
                                    color = Color(0xFF1E293B),
                                    fontWeight = if (isChosen) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. TEST SONU: SOL ÜST KÖŞEDE DURUM İKONU (`✓` YEŞİL TİK, `✗` KIRMIZI ÇARPI VEYA `⊖` GRİ BOŞ DAİRE)
        if (isReviewMode) {
            HandDrawnBrushMark345(
                isEmpty = isEmpty,
                isCorrect = isCorrect,
                isUngraded = isUngraded,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-6).dp, y = (-4).dp)
                    .size(48.dp)
                    .zIndex(40f)
            )
            if (isUngraded) {
                Text(
                    text = "?",
                    color = Color(0xFFB45309),
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 10.dp, y = 8.dp)
                )
            }

            // 3. SAĞ ÜST KÖŞEDE AÇIK MAVİ (`#60A5FA`) YUVARLAK `⋮` (ÜÇ NOKTA) BUTONU (Yüksek Z-Index + Popup ile Her Zaman Tıklanabilir!)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(65f)
            ) {
                Surface(
                    onClick = onToggleThreeDotMenu,
                    shape = CircleShape,
                    color = if (isThreeDotMenuOpen) Color(0xFF2563EB) else Color(0xFF60A5FA).copy(alpha = 0.94f),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("three_dot_btn_${question.number}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "${question.number}. Soru Seçenekleri",
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }

                // 4. `⋮` (ÜÇ NOKTA) POPOVER KARTI (Popup penceresi olarak en üst katmanda açılır, tıklamalar %100 çalışır)
                if (isThreeDotMenuOpen) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset = IntOffset(0, 108),
                        onDismissRequest = onToggleThreeDotMenu,
                        properties = PopupProperties(
                            focusable = true,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = true
                        )
                    ) {
                        ThreeDotQuestionPopoverCard345(
                            question = question,
                            selectedOption = selectedOption,
                            onOpenTopicLecture = onOpenTopicLecture,
                            onOpenSolutionModal = onOpenSolutionModal,
                            onOpenTwinScreen = onOpenTwinScreen,
                            onAddToKumbara = onAddToKumbara
                        )
                    }
                }
            }
        }
    }
}

/**
 * TEK SORU ODAKLI GÖRÜNÜM (Yatay Kaydırma / Swipe ile Sorular Arası Geçiş — Ok Butonları Yok)
 */
@Composable
private fun SingleQuestionFocusedPageSurface(
    session: ActiveSolvingSession,
    question: YksQuestion,
    customPublishers: List<YksPublisherInfo>,
    openThreeDotQuestionNumber: Int?,
    onDismissThreeDotMenu: () -> Unit,
    onToggleThreeDotMenu: (Int) -> Unit,
    onSelectOption: (String) -> Unit,
    onOpenTopicLecture: (YksQuestion) -> Unit,
    onOpenSolutionModal: (YksQuestion) -> Unit,
    onOpenTwinScreen: (YksQuestion) -> Unit,
    onAddToKumbara: (YksQuestion) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 32.dp, vertical = 20.dp)
    ) {
        if (openThreeDotQuestionNumber != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(15f)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissThreeDotMenu
                    )
            )
        }

        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .zIndex(if (openThreeDotQuestionNumber != null) 30f else 1f)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            BookPageQuestionItem345(
                question = question,
                isReviewMode = session.isReviewMode,
                selectedOption = session.selectedAnswers[question.number],
                isThreeDotMenuOpen = openThreeDotQuestionNumber == question.number,
                customPublishers = customPublishers,
                onToggleThreeDotMenu = { onToggleThreeDotMenu(question.number) },
                onSelectOption = onSelectOption,
                onOpenTopicLecture = { onOpenTopicLecture(question) },
                onOpenSolutionModal = { onOpenSolutionModal(question) },
                onOpenTwinScreen = { onOpenTwinScreen(question) },
                onAddToKumbara = { onAddToKumbara(question) }
            )
        }
    }
}

/**
 * SOL ÜST KÖŞEDE EL ÇİZİMİ FIRÇA DARBESİ (KURAL 7):
 * - Doğruysa: Organik fırça darbesi stilinde büyük Yeşil Tik (`✓`, `#16A34A`)
 * - Yanlış İşaretlendiyse: Organik fırça darbesi stilinde büyük Kırmızı Çarpı (`✗`, `#DC2626`)
 * - Boş Bırakıldıysa: İçinde hiçbir çizgi/tire olmayan temiz, içi boş gri çember (`○`, `#94A3B8`)
 */
@Composable
private fun HandDrawnBrushMark345(
    isEmpty: Boolean,
    isCorrect: Boolean,
    isUngraded: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        when {
            isCorrect -> {
                // Organik El Yapımı / Fırça Darbesi Büyük Yeşil Tik (`✓`, #16A34A)
                val checkPath = Path().apply {
                    moveTo(w * 0.12f, h * 0.54f)
                    cubicTo(
                        w * 0.22f, h * 0.66f,
                        w * 0.30f, h * 0.78f,
                        w * 0.38f, h * 0.86f
                    )
                    cubicTo(
                        w * 0.54f, h * 0.52f,
                        w * 0.72f, h * 0.26f,
                        w * 0.92f, h * 0.10f
                    )
                }
                drawPath(
                    path = checkPath,
                    color = Color(0xFF16A34A),
                    style = Stroke(
                        width = w * 0.125f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            isUngraded -> {
                drawCircle(
                    color = Color(0xFFB45309),
                    radius = min(w, h) * 0.33f,
                    center = Offset(w * 0.46f, h * 0.46f),
                    style = Stroke(width = w * 0.10f)
                )
            }
            isEmpty -> {
                // Boş Bırakılan Soru: İçinde hiçbir çizgi olmayan temiz, içi boş gri çember (`○`, #94A3B8)
                val center = Offset(w * 0.46f, h * 0.46f)
                val radius = min(w, h) * 0.33f
                drawCircle(
                    color = Color(0xFF94A3B8),
                    radius = radius,
                    center = center,
                    style = Stroke(width = w * 0.10f)
                )
            }
            else -> {
                // Yanlış İşaretlenen Soru: Organik Fırça Darbesi Büyük Kırmızı Çarpı (`✗`, #DC2626)
                val stroke1 = Path().apply {
                    moveTo(w * 0.16f, h * 0.15f)
                    quadraticTo(w * 0.50f, h * 0.48f, w * 0.84f, h * 0.85f)
                }
                val stroke2 = Path().apply {
                    moveTo(w * 0.82f, h * 0.15f)
                    quadraticTo(w * 0.48f, h * 0.48f, w * 0.16f, h * 0.85f)
                }
                drawPath(
                    path = stroke1,
                    color = Color(0xFFDC2626),
                    style = Stroke(
                        width = w * 0.115f,
                        cap = StrokeCap.Round
                    )
                )
                drawPath(
                    path = stroke2,
                    color = Color(0xFFDC2626),
                    style = Stroke(
                        width = w * 0.115f,
                        cap = StrokeCap.Round
                    )
                )
            }
        }
    }
}

/**
 * 3-C. SORUNUN SAĞ ÜSTÜNDEKİ `⋮` (ÜÇ NOKTA) POPOVER KARTI (345 All Star Birebir + 5'li Zorluk Barı):
 * 1. Üst Başlık: Kalın siyah `[X]. Soru` + sağında küçük yuvarlak durum ikonu (`✓` yeşil / `✕` kırmızı / `⊖` gri).
 * 2. 5 Kademeli Zorluk Seviyesi Barı: Turuncu (`#F97316`) + Gri (`#E2E8F0`), altında `Kolay - Orta - ÖSYM - Zor - Çok Zor`,
 *    üstünde siyah ters üçgen ok (`▼`).
 * 3. Durum Yazısı: `Doğru Yanıt`, `Yanlış Yanıt` veya `Boş Bırakıldı`.
 * 4. Alt Alta Oval Butonlar (`RoundedCornerShape(50)`):
 *    - `Konu Anlatımı`
 *    - `Çözüm İzle`
 *    - `İkiz Soru`
 *    - `Soru Kumbarama Ekle`
 */
@Composable
private fun ThreeDotQuestionPopoverCard345(
    question: YksQuestion,
    selectedOption: String?,
    onOpenTopicLecture: () -> Unit,
    onOpenSolutionModal: () -> Unit,
    onOpenTwinScreen: () -> Unit,
    onAddToKumbara: () -> Unit
) {
    val isEmpty = selectedOption.isNullOrBlank()
    val isCorrect = !isEmpty && question.answerKeyVerified && selectedOption == question.correctOption
    val statusText = when {
        !question.answerKeyVerified -> "Cevap anahtarı doğrulanmadı"
        isCorrect -> "Doğru Yanıt"
        isEmpty -> "Boş Bırakıldı"
        else -> "Yanlış Yanıt"
    }
    val diffFraction = when (question.difficulty) {
        DifficultyLevel.KOLAY -> 0.12f
        DifficultyLevel.ORTA -> 0.34f
        DifficultyLevel.OSYM -> 0.54f
        DifficultyLevel.ZOR -> 0.76f
        DifficultyLevel.COK_ZOR -> 0.94f
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 16.dp,
        modifier = Modifier
            .width(262.dp)
            .testTag("three_dot_popover_${question.number}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Üst Başlık: "[X]. Soru" + Durum Dairesi
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${question.number}. Soru",
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                if (isEmpty) {
                    // Boş bırakılan soru için içi boş gri çember (`○`, #94A3B8)
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .border(2.2.dp, Color(0xFF94A3B8), CircleShape)
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = if (isCorrect) Color(0xFF15803D) else Color(0xFFDC2626),
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isCorrect) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = statusText,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // 2. 5 Kademeli Zorluk Seviyesi Barı (Kolay - Orta - ÖSYM - Zor - Çok Zor + Üstte Siyah Ters Üçgen ▼)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                ) {
                    val barHeight = 7.dp.toPx()
                    val barTop = size.height - barHeight
                    val fillWidth = (size.width * diffFraction).coerceIn(14f, size.width)

                    // Gri arka çubuk
                    drawRoundRect(
                        color = Color(0xFFE2E8F0),
                        topLeft = Offset(0f, barTop),
                        size = Size(size.width, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2f, barHeight / 2f)
                    )
                    // Turuncu doluluk çubuğu
                    drawRoundRect(
                        color = Color(0xFFF97316),
                        topLeft = Offset(0f, barTop),
                        size = Size(fillWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2f, barHeight / 2f)
                    )
                    // Siyah ters üçgen ok (▼)
                    val triX = fillWidth.coerceIn(8f, size.width - 8f)
                    val triPath = Path().apply {
                        moveTo(triX - 5.dp.toPx(), 1.dp.toPx())
                        lineTo(triX + 5.dp.toPx(), 1.dp.toPx())
                        lineTo(triX, barTop - 1.dp.toPx())
                        close()
                    }
                    drawPath(path = triPath, color = Color(0xFF0F172A))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DifficultyLevel.entries.forEach { lvl ->
                        val isCurrent = question.difficulty == lvl
                        Text(
                            text = lvl.label,
                            color = if (isCurrent) Color(0xFFEA580C) else Color(0xFF475569),
                            fontSize = 9.5.sp,
                            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                }
            }

            // 3. Durum Yazısı ("Doğru Yanıt" / "Yanlış Yanıt" / "Boş Bırakıldı")
            Text(
                text = statusText,
                color = when {
                    isCorrect -> Color(0xFF15803D)
                    isEmpty -> Color(0xFF64748B)
                    else -> Color(0xFFDC2626)
                },
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 1.dp)
            )

            // 4. Alt Alta 4 Oval Buton (Konu Anlatımı, Çözüm İzle, İkiz Soru, Soru Kumbarama Ekle)
            PopoverPillButton345(
                label = "Konu Anlatımı",
                backgroundColor = Color(0xFFE2E8F0),
                textColor = Color(0xFF2563EB),
                onClick = onOpenTopicLecture
            )
            PopoverPillButton345(
                label = "Çözüm İzle",
                backgroundColor = Color(0xFF3B82F6),
                textColor = Color.White,
                onClick = onOpenSolutionModal
            )
            PopoverPillButton345(
                label = "İkiz Soru",
                backgroundColor = Color(0xFFE2E8F0),
                textColor = Color(0xFF2563EB),
                onClick = onOpenTwinScreen
            )
            PopoverPillButton345(
                label = "Soru Kumbarama Ekle",
                backgroundColor = Color(0xFFE2E8F0),
                textColor = Color(0xFF1E293B),
                onClick = onAddToKumbara
            )
        }
    }
}

@Composable
private fun PopoverPillButton345(
    label: String,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = backgroundColor,
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * 2. SAĞDAN AÇILIR "OPTİK FORM" ÇEKMECESİ (Screenshot 1 ile birebir aynı!)
 * - Ekranın sağ kenarında dikey mavi `⠿ Optik Form` kulakçığı bulunur.
 * - Tıklanınca sağdan beyaz yuvarlatılmış çekmece kayarak açılır ve mavi `⠿ Optik Form`
 *   kulakçığı çekmecenin sol dış kenarına yapışık kalır!
 * - İçinde: Ortada kalın `Optik Form` başlığı, altında `1. (A) (B) (C) (D) (E)` satırları,
 *   en altta tam genişlikli mavi hap `⚑ Testi Bitir` butonu.
 */
@Composable
private fun RightSlidingOpticalFormDrawer345(
    isOpen: Boolean,
    questions: List<YksQuestion>,
    selectedAnswers: Map<Int, String>,
    onToggleDrawer: () -> Unit,
    onSelectOption: (Int, String) -> Unit,
    onFinishTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sol dış kenara yapışık kompakt dikey mavi "⠿ Optik" kulakçığı
        VerticalBlueSideTab345(
            title = "Optik",
            isStatsIcon = false,
            onClick = onToggleDrawer,
            testTag = "optical_form_tab"
        )

        AnimatedVisibility(
            visible = isOpen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp),
                color = Color.White,
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .width(330.dp)
                    .heightIn(min = 380.dp, max = 560.dp)
                    .testTag("optical_form_drawer")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Optik Form",
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(questions, key = { it.number }) { q ->
                            val chosenOpt = selectedAnswers[q.number]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${q.number}.",
                                    color = Color(0xFF334155),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.width(30.dp)
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf("A", "B", "C", "D", "E").forEach { opt ->
                                        val isSelected = chosenOpt == opt
                                        Surface(
                                            onClick = { onSelectOption(q.number, opt) },
                                            shape = CircleShape,
                                            color = if (isSelected) Color(0xFF3B82F6) else Color.White,
                                            border = BorderStroke(
                                                width = 1.2.dp,
                                                color = if (isSelected) Color(0xFF3B82F6) else Color(0xFFCBD5E1)
                                            ),
                                            modifier = Modifier
                                                .size(38.dp)
                                                .testTag("optic_q${q.number}_$opt")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = opt,
                                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tam genişlikli mavi hap "⚑ Testi Bitir" butonu
                    Surface(
                        onClick = onFinishTest,
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF3B82F6),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("drawer_finish_test_btn")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Testi Bitir",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3-B. SAĞ KENAR `◔ İSTATİSTİKLER` ÇEKMECESİ (Screenshot 3 ile birebir aynı!)
 * - Sağdaki dikey mavi kulakçık `◔ İstatistikler` olur.
 * - Tıklanınca sağdan açılan beyaz çekmecede üstte kalın Test Adı, altında yan yana
 *   3 Büyük Renkli Daire:
 *   1) Mavi Daire (`#3B82F6`): Doğru sayısı + `Doğru`
 *   2) Kırmızı Daire (`#EF4444`): Yanlış sayısı + `Yanlış`
 *   3) Açık Gri Daire (`#CBD5E1`): Boş sayısı + `Boş`
 */
@Composable
private fun RightSlidingStatsDrawer345(
    isOpen: Boolean,
    testTitle: String,
    correctCount: Int,
    wrongCount: Int,
    emptyCount: Int,
    ungradedCount: Int,
    onToggleDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VerticalBlueSideTab345(
            title = "İstatistikler",
            isStatsIcon = true,
            onClick = onToggleDrawer,
            testTag = "stats_drawer_tab"
        )

        AnimatedVisibility(
            visible = isOpen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp),
                color = Color.White,
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .width(340.dp)
                    .testTag("stats_drawer_panel")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    Text(
                        text = testTitle,
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.5.sp,
                        textAlign = TextAlign.Center
                    )

                    ThreeStatCirclesRow345(
                        correctCount = correctCount,
                        wrongCount = wrongCount,
                        emptyCount = emptyCount,
                        circleSizeDp = 82
                    )
                    if (ungradedCount > 0) {
                        Text(
                            text = "Doğrulanmamış: $ungradedCount",
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 3 BÜYÜK RENKLİ DAİRE (Mavi `#3B82F6` Doğru | Kırmızı `#EF4444` Yanlış | Açık Gri `#CBD5E1` Boş)
 * (Hem Sağ `◔ İstatistikler` Çekmecesinde hem de Tam Ekran Karne Sayfasında kullanılır)
 */
@Composable
private fun ThreeStatCirclesRow345(
    correctCount: Int,
    wrongCount: Int,
    emptyCount: Int,
    circleSizeDp: Int = 86
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Mavi Daire: Doğru
        Surface(
            shape = CircleShape,
            color = Color(0xFF3B82F6),
            modifier = Modifier.size(circleSizeDp.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$correctCount",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (circleSizeDp * 0.25f).sp
                )
                Text(
                    text = "Doğru",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (circleSizeDp * 0.15f).sp
                )
            }
        }

        // 2. Kırmızı Daire: Yanlış
        Surface(
            shape = CircleShape,
            color = Color(0xFFEF4444),
            modifier = Modifier.size(circleSizeDp.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$wrongCount",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (circleSizeDp * 0.25f).sp
                )
                Text(
                    text = "Yanlış",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (circleSizeDp * 0.15f).sp
                )
            }
        }

        // 3. Açık Gri Daire: Boş
        Surface(
            shape = CircleShape,
            color = Color(0xFFCBD5E1),
            modifier = Modifier.size(circleSizeDp.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$emptyCount",
                    color = Color(0xFF1E293B),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (circleSizeDp * 0.25f).sp
                )
                Text(
                    text = "Boş",
                    color = Color(0xFF334155),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (circleSizeDp * 0.15f).sp
                )
            }
        }
    }
}

/**
 * SAĞ KENAR DİKEY MAVİ KULAKÇIK (`⠿ Optik Form` veya `◔ İstatistikler`)
 */
@Composable
private fun VerticalBlueSideTab345(
    title: String,
    isStatsIcon: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val tabHeight = if (isStatsIcon) 130.dp else 94.dp
    val innerWidth = if (isStatsIcon) 118.dp else 84.dp
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp),
        color = Color(0xFF2563EB),
        shadowElevation = 6.dp,
        modifier = Modifier
            .width(36.dp)
            .height(tabHeight)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .requiredWidth(innerWidth)
                    .graphicsLayer { rotationZ = -90f }
            ) {
                Icon(
                    imageVector = if (isStatsIcon) Icons.Default.PieChart else Icons.Default.Apps,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 1. ALT KOYU LACİVERT (`#1E295B`) ÇİZİM VE ARAÇ BARI (345 ALL STAR BİREBİR):
 * - Solda Kitap/Tek Soru görünüm ikonu
 * - Ortada: Kalem (aktifken mavi yuvarlak içinde), Büyüteç, El (Kaydırma), Silgi,
 *   3 boy nokta (kalem kalınlığı), Renk seçici ve Çöp Kutusu (Temizle)
 * - En sağda: Gri hap içinde Kronometre (`00:12`)
 */
@Composable
private fun BottomDarkNavyDrawingBar345(
    viewMode: BoardViewMode,
    activeTool: DrawingTool,
    isZoomMagnifierEnabled: Boolean,
    selectedStrokeSizeIndex: Int,
    selectedPenColorHex: Long,
    elapsedSeconds: Int,
    onToggleViewMode: () -> Unit,
    onSelectTool: (DrawingTool) -> Unit,
    onToggleZoom: () -> Unit,
    onSelectStrokeSize: (Int) -> Unit,
    onSelectPenColor: (Long) -> Unit,
    onClearCanvas: () -> Unit
) {
    var showColorPalettePopup by remember { mutableStateOf(false) }
    val penColors = listOf(
        0xFF0F172AL to "Siyah",
        0xFF1D4ED8L to "Mavi",
        0xFFDC2626L to "Kırmızı",
        0xFF16A34AL to "Yeşil",
        0xFFD97706L to "Turuncu",
        0xFF9333EAL to "Mor"
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = Color(0xFF1E295B),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // SOL: Kitap Sayfası / Tek Soru Görünüm Değiştirici İkonu
                IconButton(
                    onClick = onToggleViewMode,
                    modifier = Modifier.testTag("toggle_view_mode_btn")
                ) {
                    Icon(
                        imageVector = if (viewMode == BoardViewMode.FULL_PAGE) {
                            Icons.AutoMirrored.Filled.MenuBook
                        } else {
                            Icons.Default.CropPortrait
                        },
                        contentDescription = "Görünüm Değiştir",
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                }

                // ORTA: Çizim Araçları (Kalem [Tıklayınca Renk Değiştirir], Büyüteç, El, Silgi, 3 Kalınlık Noktası, Renk, Çöp Kutusu)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Kalem (Tıklandığında Kalemi Seçer VE Renk Seçici Paleti Açar/Kapatır — Varsayılan Renk: Siyah)
                    val isPenActive = activeTool == DrawingTool.PEN && !isZoomMagnifierEnabled
                    Surface(
                        onClick = {
                            onSelectTool(DrawingTool.PEN)
                            showColorPalettePopup = !showColorPalettePopup
                        },
                        shape = RoundedCornerShape(50),
                        color = if (isPenActive) Color(0xFF2563EB) else Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(
                            1.5.dp,
                            if (showColorPalettePopup) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("tool_pen_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Kalem ve Renk Seçimi",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                            // Kalem üzerindeki aktif renk göstergesi (Varsayılan: Siyah)
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(selectedPenColorHex))
                                    .border(1.5.dp, Color.White, CircleShape)
                            )
                        }
                    }

                    // Fosforlu Kalem (Highlighter)
                    val isHighlighterActive = activeTool == DrawingTool.HIGHLIGHTER && !isZoomMagnifierEnabled
                    Surface(
                        onClick = {
                            showColorPalettePopup = false
                            onSelectTool(DrawingTool.HIGHLIGHTER)
                        },
                        shape = CircleShape,
                        color = if (isHighlighterActive) Color(0xFF2563EB) else Color.Transparent,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("tool_highlighter_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.BorderColor,
                                contentDescription = "Fosforlu Kalem",
                                tint = if (isHighlighterActive) Color(0xFFFACC15) else Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Akıllı Şekil (Smart Shape)
                    val isSmartShapeActive = activeTool == DrawingTool.SMART_SHAPE && !isZoomMagnifierEnabled
                    Surface(
                        onClick = {
                            showColorPalettePopup = false
                            onSelectTool(DrawingTool.SMART_SHAPE)
                        },
                        shape = CircleShape,
                        color = if (isSmartShapeActive) Color(0xFF2563EB) else Color.Transparent,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("tool_smart_shape_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ChangeHistory,
                                contentDescription = "Akıllı Şekil",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Büyüteç (Zoom)
                    Surface(
                        onClick = {
                            showColorPalettePopup = false
                            onToggleZoom()
                        },
                        shape = CircleShape,
                        color = if (isZoomMagnifierEnabled) Color(0xFF2563EB) else Color.Transparent,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Yakınlaştır",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // El (Sayfa Kaydırma / Pan)
                    val isPanActive = activeTool == DrawingTool.HAND_PAN && !isZoomMagnifierEnabled
                    Surface(
                        onClick = {
                            showColorPalettePopup = false
                            onSelectTool(DrawingTool.HAND_PAN)
                        },
                        shape = CircleShape,
                        color = if (isPanActive) Color(0xFF2563EB) else Color.Transparent,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("tool_hand_pan_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PanTool,
                                contentDescription = "Sayfa Kaydır",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Silgi (Net İkon + "Silgi" Etiketi — BlendMode.Clear / destination-out)
                    val isEraserActive = activeTool == DrawingTool.ERASER && !isZoomMagnifierEnabled
                    Surface(
                        onClick = {
                            showColorPalettePopup = false
                            onSelectTool(DrawingTool.ERASER)
                        },
                        shape = RoundedCornerShape(50),
                        color = if (isEraserActive) Color(0xFF2563EB) else Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(
                            1.dp,
                            if (isEraserActive) Color(0xFF60A5FA) else Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("tool_eraser_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = "Silgi",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "Silgi",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }
                    }

                    // Dikey İnce Ayraç
                    Box(
                        modifier = Modifier
                            .height(22.dp)
                            .width(1.dp)
                            .background(Color.White.copy(alpha = 0.22f))
                    )

                    // 3 Boy Kalem Kalınlığı Noktası (Küçük / Orta / Büyük)
                    val dotSizes = listOf(7.dp, 11.dp, 15.dp)
                    dotSizes.forEachIndexed { idx, dotDp ->
                        val isSelectedSize = selectedStrokeSizeIndex == idx
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable { onSelectStrokeSize(idx) },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(dotDp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelectedSize) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.7f)
                                    )
                            )
                        }
                    }

                    // Renk Seçici Daire (Kalem butonuna basınca da aynı menü açılır)
                    Surface(
                        onClick = { showColorPalettePopup = !showColorPalettePopup },
                        shape = CircleShape,
                        color = Color(selectedPenColorHex),
                        border = BorderStroke(2.dp, Color.White),
                        modifier = Modifier
                            .size(26.dp)
                            .testTag("tool_color_circle_btn")
                    ) {}

                    // Çöp Kutusu (Sayfa Çizimlerini Temizle)
                    IconButton(
                        onClick = onClearCanvas,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Çizimleri Temizle",
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }

                // SAĞ: Gri Hap Kronometre ("00:12")
                val mins = (elapsedSeconds / 60).toString().padStart(2, '0')
                val secs = (elapsedSeconds % 60).toString().padStart(2, '0')
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFE2E8F0)
                ) {
                    Text(
                        text = "$mins:$secs",
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Açılır Kalem Rengi Seçici (Üst Araç Barında Kalem Butonuna veya Renk Dairesine Basınca Hemen Altında Açılır)
        if (showColorPalettePopup) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 54.dp)
                    .zIndex(70f)
                    .testTag("pen_color_palette_popup")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kalem Rengi:",
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                    penColors.forEach { (hex, colorName) ->
                        val isSel = selectedPenColorHex == hex
                        Surface(
                            onClick = {
                                onSelectPenColor(hex)
                                onSelectTool(DrawingTool.PEN)
                                showColorPalettePopup = false
                            },
                            shape = RoundedCornerShape(50),
                            color = if (isSel) Color.White.copy(alpha = 0.16f) else Color.Transparent,
                            border = BorderStroke(if (isSel) 1.5.dp else 0.dp, if (isSel) Color(0xFF38BDF8) else Color.Transparent),
                            modifier = Modifier.testTag("pen_color_option_$colorName")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (isSel) 22.dp else 18.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                        .border(1.5.dp, Color.White, CircleShape)
                                )
                                Text(
                                    text = colorName,
                                    color = if (isSel) Color(0xFF38BDF8) else Color.White,
                                    fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * =========================================================================
 * 4. TAM EKRAN YAN YANA "İKİZ SORU" EKRANI (Screenshot 5 ile birebir aynı!)
 * =========================================================================
 */
@Composable
private fun FullScreenTwinQuestionComparisonView(
    session: ActiveSolvingSession,
    originalQuestion: YksQuestion,
    twinData: TwinQuestionModalData?,
    isLoadingTwin: Boolean,
    studentAnswer: String?,
    activeTool: DrawingTool,
    selectedStrokeSizeIndex: Int,
    selectedPenColorHex: Long,
    onSelectTool: (DrawingTool) -> Unit,
    onSelectStrokeSize: (Int) -> Unit,
    onSelectPenColor: (Long) -> Unit,
    onBackToBookReview: () -> Unit,
    onOpenReportCard: () -> Unit
) {
    var twinSelectedOption by remember(originalQuestion.id) { mutableStateOf<String?>(null) }
    var isTwinAnswerRevealed by remember(originalQuestion.id) { mutableStateOf(false) }

    // Çizim Katmanı State'leri: Hem Test Sorusu (sol) hem de İkiz Soru (sağ) üzerinde bağımsız çizim yapılır
    var twinStrokes by remember(originalQuestion.id) { mutableStateOf<List<DrawStroke>>(emptyList()) }
    val liveTwinPath = remember { Path() }
    val liveTwinPoints = remember { mutableListOf<Offset>() }
    val liveTwinDrawTick = remember { mutableIntStateOf(0) }

    var testStrokes by remember(originalQuestion.id) { mutableStateOf<List<DrawStroke>>(emptyList()) }
    val liveTestPath = remember { Path() }
    val liveTestPoints = remember { mutableListOf<Offset>() }
    val liveTestDrawTick = remember { mutableIntStateOf(0) }

    val strokeWidths = listOf(4.5f, 8.0f, 13.0f)
    val currentStrokePx = strokeWidths.getOrElse(selectedStrokeSizeIndex) { 8.0f }

    val originalBitmap = StudioImageAndPdfEngine.rememberCachedBitmap(originalQuestion.croppedImagePath)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .testTag("full_screen_twin_comparison")
    ) {
        // 1. Üst Beyaz Bar: Solda Geri Oku, Ortada Test Başlığı, Sağda "▸ Başarı Durumuna Git"
        BookPageTopBar345(
            testTitle = session.testItem.fullTitle.ifBlank { "${session.topicName} - Test 1" },
            isReviewMode = true,
            onBack = onBackToBookReview,
            onFinishTest = {},
            onOpenReportCard = onOpenReportCard
        )

        // 2. Üst Koyu Lacivert Çizim Barı (Üst Beyaz Barın Hemen Altında)
        Box(modifier = Modifier.zIndex(40f)) {
            BottomDarkNavyDrawingBar345(
                viewMode = BoardViewMode.FULL_PAGE,
                activeTool = activeTool,
                isZoomMagnifierEnabled = false,
                selectedStrokeSizeIndex = selectedStrokeSizeIndex,
                selectedPenColorHex = selectedPenColorHex,
                elapsedSeconds = session.elapsedSeconds,
                onToggleViewMode = onBackToBookReview,
                onSelectTool = onSelectTool,
                onToggleZoom = {},
                onSelectStrokeSize = onSelectStrokeSize,
                onSelectPenColor = onSelectPenColor,
                onClearCanvas = {
                    twinStrokes = emptyList()
                    testStrokes = emptyList()
                }
            )
        }

        // KURAL 1: Yan yana görünümde SOL tarafa "Test Sorusu"nu, SAĞ tarafa "İkiz Sorusu"nu yerleştir ve her ikisinin üzerinde de S-Pen ile çizim yapılabilsin.
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==================== SOL TARAF: TEST SORUSU ====================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    color = Color(0xFF1E1B4B)
                ) {
                    Text(
                        text = "Test Sorusu",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Color(0xFF475569)),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                if (originalBitmap != null) {
                                    val aspect = originalBitmap.width.toFloat() /
                                        originalBitmap.height.toFloat().coerceAtLeast(1f)
                                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                        val nativeWidth = with(LocalDensity.current) { originalBitmap.width.toDp() }
                                        val imageWidth = minOf(maxWidth, 300.dp * aspect, nativeWidth)
                                        Image(
                                            bitmap = originalBitmap.asImageBitmap(),
                                            contentDescription = "Orijinal Test Sorusu",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .width(imageWidth)
                                                .aspectRatio(aspect)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = originalQuestion.stemTop.ifBlank { "${originalQuestion.number}. Soru" },
                                        color = Color(0xFF1E293B),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Doğru Cevap:",
                                        color = Color(0xFF334155),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = if (originalQuestion.answerKeyVerified) Color(0xFF15803D) else Color(0xFFB45309),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (originalQuestion.answerKeyVerified) originalQuestion.correctOption else "?",
                                                color = Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Senin Cevabın:",
                                        color = Color(0xFF334155),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp
                                    )
                                    val hasStudentAns = !studentAnswer.isNullOrBlank()
                                    val isStudentRight = originalQuestion.answerKeyVerified && studentAnswer == originalQuestion.correctOption
                                    Surface(
                                        shape = CircleShape,
                                        color = when {
                                            !hasStudentAns -> Color.White
                                            isStudentRight -> Color(0xFF15803D)
                                            !originalQuestion.answerKeyVerified -> Color(0xFFB45309)
                                            else -> Color(0xFFDC2626)
                                        },
                                        border = BorderStroke(
                                            1.2.dp,
                                            if (!hasStudentAns) Color(0xFFCBD5E1) else Color.Transparent
                                        ),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = studentAnswer?.ifBlank { "-" } ?: "-",
                                                color = if (!hasStudentAns) Color(0xFF64748B) else Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // TEST SORUSU ÜZERİNDE S-PEN & FARE ÇİZİM KATMANI
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .then(
                                    if (activeTool == DrawingTool.PEN ||
                                        activeTool == DrawingTool.HIGHLIGHTER ||
                                        activeTool == DrawingTool.SMART_SHAPE ||
                                        activeTool == DrawingTool.ERASER
                                    ) {
                                        Modifier.pointerInput(activeTool, selectedStrokeSizeIndex, selectedPenColorHex) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                val isStylusOrMouse = down.type == PointerType.Stylus ||
                                                    down.type == PointerType.Eraser ||
                                                    down.type == PointerType.Mouse
                                                if (!isStylusOrMouse) return@awaitEachGesture

                                                val dragStart = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                                                    change.consume()
                                                }
                                                if (dragStart != null) {
                                                    liveTestPoints.clear()
                                                    liveTestPoints.add(down.position)
                                                    liveTestPoints.add(dragStart.position)
                                                    liveTestPath.reset()
                                                    liveTestPath.moveTo(down.position.x, down.position.y)
                                                    liveTestPath.lineTo(dragStart.position.x, dragStart.position.y)
                                                    liveTestDrawTick.intValue++

                                                    var prevPt = dragStart.position
                                                    drag(dragStart.id) { change ->
                                                        change.consume()
                                                        val curPt = change.position
                                                        liveTestPoints.add(curPt)
                                                        val midX = (prevPt.x + curPt.x) * 0.5f
                                                        val midY = (prevPt.y + curPt.y) * 0.5f
                                                        liveTestPath.quadraticTo(prevPt.x, prevPt.y, midX, midY)
                                                        prevPt = curPt
                                                        liveTestDrawTick.intValue++
                                                    }
                                                    if (liveTestPoints.size >= 2) {
                                                        val isEr = activeTool == DrawingTool.ERASER
                                                        val isHigh = activeTool == DrawingTool.HIGHLIGHTER
                                                        val isShape = activeTool == DrawingTool.SMART_SHAPE
                                                        testStrokes = testStrokes + DrawStroke(
                                                            points = liveTestPoints.toList(),
                                                            colorHex = if (isEr) 0x00000000L else selectedPenColorHex,
                                                            strokeWidthPx = when {
                                                                isEr -> 38f
                                                                isHigh -> currentStrokePx * 2.6f
                                                                else -> currentStrokePx
                                                            },
                                                            isSmartShape = isShape,
                                                            isEraser = isEr,
                                                            isHighlighter = isHigh
                                                        )
                                                    }
                                                    liveTestPoints.clear()
                                                    liveTestPath.reset()
                                                    liveTestDrawTick.intValue++
                                                }
                                            }
                                        }
                                    } else Modifier
                                )
                        ) {
                            val tick = liveTestDrawTick.intValue
                            testStrokes.forEach { drawSmoothStroke(it) }
                            if (tick >= 0 && liveTestPoints.size >= 2) {
                                val isEr = activeTool == DrawingTool.ERASER
                                val isHigh = activeTool == DrawingTool.HIGHLIGHTER
                                drawPath(
                                    path = liveTestPath,
                                    color = when {
                                        isEr -> Color.Transparent
                                        isHigh -> Color(selectedPenColorHex).copy(alpha = 0.36f)
                                        else -> Color(selectedPenColorHex)
                                    },
                                    style = Stroke(
                                        width = when {
                                            isEr -> 38f
                                            isHigh -> currentStrokePx * 2.6f
                                            else -> currentStrokePx
                                        },
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    ),
                                    blendMode = if (isEr) BlendMode.Clear else BlendMode.SrcOver
                                )
                            }
                        }
                    }
                }
            }

            // ==================== SAĞ TARAF: İKİZ SORUSU ====================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    color = Color(0xFFEA580C)
                ) {
                    Text(
                        text = "İkiz Sorusu",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Color(0xFFEA580C)),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isLoadingTwin || twinData == null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFFEA580C))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "${originalQuestion.topicHeader} kazanımına uygun İkiz Soru hazırlanıyor...",
                                    color = Color(0xFF475569),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else if (twinData.errorMessage != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = twinData.errorMessage,
                                    color = Color(0xFFB91C1C),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Text(
                                        text = twinData.questionStem,
                                        color = Color(0xFF1E293B),
                                        fontSize = 14.5.sp,
                                        lineHeight = 21.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf("A", "B", "C", "D", "E").forEach { optKey ->
                                            val optVal = twinData.options[optKey] ?: optKey
                                            val isSelected = twinSelectedOption == optKey
                                            val isCorrectOpt = isTwinAnswerRevealed && optKey == twinData.correctOption
                                            Surface(
                                                onClick = { twinSelectedOption = optKey },
                                                shape = RoundedCornerShape(8.dp),
                                                color = when {
                                                    isCorrectOpt -> Color(0xFFDCFCE7)
                                                    isSelected -> Color(0xFFDBEAFE)
                                                    else -> Color.Transparent
                                                },
                                                border = if (isSelected || isCorrectOpt) {
                                                    BorderStroke(
                                                        1.dp,
                                                        if (isCorrectOpt) Color(0xFF16A34A) else Color(0xFF2563EB)
                                                    )
                                                } else null
                                            ) {
                                                Text(
                                                    text = "$optKey) $optVal",
                                                    color = Color(0xFF1E293B),
                                                    fontWeight = if (isSelected || isCorrectOpt) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    fontSize = 13.sp,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (isTwinAnswerRevealed) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFF0FDF4),
                                            border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "Doğru Cevap: ${twinData.correctOption}",
                                                    color = Color(0xFF15803D),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.5.sp
                                                )
                                                Text(
                                                    text = twinData.solutionExplanation,
                                                    color = Color(0xFF166534),
                                                    fontSize = 12.5.sp,
                                                    lineHeight = 18.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                        .zIndex(30f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        onClick = { isTwinAnswerRevealed = !isTwinAnswerRevealed },
                                        shape = RoundedCornerShape(50),
                                        color = Color(0xFF3B82F6),
                                        shadowElevation = 3.dp
                                    ) {
                                        Text(
                                            text = if (isTwinAnswerRevealed) "Cevabı Gizle" else "Cevabı Göster",
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // İKİZ SORU S-PEN & FARE SIFIR GECİKMELİ ÇİZİM KATMANI + BLENDMODE.CLEAR SİLGİ
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .then(
                                    if (activeTool == DrawingTool.PEN ||
                                        activeTool == DrawingTool.HIGHLIGHTER ||
                                        activeTool == DrawingTool.SMART_SHAPE ||
                                        activeTool == DrawingTool.ERASER
                                    ) {
                                        Modifier.pointerInput(activeTool, selectedStrokeSizeIndex, selectedPenColorHex) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                val isStylusOrMouse = down.type == PointerType.Stylus ||
                                                    down.type == PointerType.Eraser ||
                                                    down.type == PointerType.Mouse
                                                if (!isStylusOrMouse) return@awaitEachGesture

                                                val dragStart = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                                                    change.consume()
                                                }
                                                if (dragStart != null) {
                                                    liveTwinPoints.clear()
                                                    liveTwinPoints.add(down.position)
                                                    liveTwinPoints.add(dragStart.position)
                                                    liveTwinPath.reset()
                                                    liveTwinPath.moveTo(down.position.x, down.position.y)
                                                    liveTwinPath.lineTo(dragStart.position.x, dragStart.position.y)
                                                    liveTwinDrawTick.intValue++

                                                    var prevPt = dragStart.position
                                                    drag(dragStart.id) { change ->
                                                        change.consume()
                                                        val curPt = change.position
                                                        liveTwinPoints.add(curPt)
                                                        val midX = (prevPt.x + curPt.x) * 0.5f
                                                        val midY = (prevPt.y + curPt.y) * 0.5f
                                                        liveTwinPath.quadraticTo(prevPt.x, prevPt.y, midX, midY)
                                                        prevPt = curPt
                                                        liveTwinDrawTick.intValue++
                                                    }
                                                    if (liveTwinPoints.size >= 2) {
                                                        val isEr = activeTool == DrawingTool.ERASER
                                                        val isHigh = activeTool == DrawingTool.HIGHLIGHTER
                                                        val isShape = activeTool == DrawingTool.SMART_SHAPE
                                                        twinStrokes = twinStrokes + DrawStroke(
                                                            points = liveTwinPoints.toList(),
                                                            colorHex = if (isEr) 0x00000000L else selectedPenColorHex,
                                                            strokeWidthPx = when {
                                                                isEr -> 38f
                                                                isHigh -> currentStrokePx * 2.6f
                                                                else -> currentStrokePx
                                                            },
                                                            isSmartShape = isShape,
                                                            isEraser = isEr,
                                                            isHighlighter = isHigh
                                                        )
                                                    }
                                                    liveTwinPoints.clear()
                                                    liveTwinPath.reset()
                                                    liveTwinDrawTick.intValue++
                                                }
                                            }
                                        }
                                    } else Modifier
                                )
                        ) {
                            val tick = liveTwinDrawTick.intValue
                            twinStrokes.forEach { drawSmoothStroke(it) }
                            if (tick >= 0 && liveTwinPoints.size >= 2) {
                                val isEr = activeTool == DrawingTool.ERASER
                                val isHigh = activeTool == DrawingTool.HIGHLIGHTER
                                drawPath(
                                    path = liveTwinPath,
                                    color = when {
                                        isEr -> Color.Transparent
                                        isHigh -> Color(selectedPenColorHex).copy(alpha = 0.36f)
                                        else -> Color(selectedPenColorHex)
                                    },
                                    style = Stroke(
                                        width = when {
                                            isEr -> 38f
                                            isHigh -> currentStrokePx * 2.6f
                                            else -> currentStrokePx
                                        },
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    ),
                                    blendMode = if (isEr) BlendMode.Clear else BlendMode.SrcOver
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * =========================================================================
 * 5. TAM EKRAN "▸ BAŞARI DURUMUNA GİT" KARNE & ANALİZ SAYFASI
 * (Screenshots 6 & 7 ile birebir aynı!)
 * =========================================================================
 * - Üst Bar: Solda geri oku, ortada Test Başlığı, sağ üstte mavi hap `▸ Kitapta Göster` butonu.
 * - Orta Kutlama Alanı:
 *   * Konfeti patlatan kutlama illüstrasyonu (`🎉`), altında kalın başlık (`Çok yaklaştın!` / `Harika iş çıkardın!`),
 *     altında `"Bu testi tamamladın, şimdi birlikte sonuçlara yakından bakalım."` açıklaması.
 *   * Yan yana 3 Büyük Daire (`Doğru` Mavi `#3B82F6`, `Yanlış` Kırmızı `#EF4444`, `Boş` Gri `#CBD5E1`).
 * - İkiz Test Öneri Kutusu:
 *   * Açık gri (`#F1F5F9`) yuvarlatılmış kutu: Solda `"Bu testin bir ikizi var! Pekiştirmen için çözmeni öneriyoruz."`,
 *     sağda mavi hap buton `İkiz Testi Çöz`.
 * - Alt Bölüm — `Çözüm Videoları` ve 4 Sütunlu Soru Tablosu:
 *   * Yeşil hap etiket (`#84CC16` / `#65A30D`) içinde ders adı (Örn: `AYT Fizik`, `AYT Matematik`).
 *   * Sütun Başlıkları: `Soru No` | `Doğru Cevap` | `Öğrenci Cevabı` | `Zorluk Seviyesi`
 *   * Her soru satırı (`1. Soru`, `2. Soru`...): Doğru cevap harfi, Öğrenci cevabı (`-` veya harf),
 *     sağda 5 yıldız üzerinden mavi yıldızlı zorluk gösterimi (`★★☆☆☆`) ve aşağı açılır ok (`⌄`).
 */
@Composable
private fun FullScreenSuccessReportCardView(
    session: ActiveSolvingSession,
    correctCount: Int,
    wrongCount: Int,
    emptyCount: Int,
    ungradedCount: Int,
    showTwinSuggestion: Boolean,
    onBackToBookPage: () -> Unit,
    onSolveTwinTestForWrongQuestions: () -> Unit,
    onWatchQuestionSolution: (YksQuestion) -> Unit,
    onOpenTwinForQuestion: (YksQuestion) -> Unit
) {
    val questions = session.testItem.questions
    var expandedQuestionNumber by remember { mutableStateOf<Int?>(null) }

    val gradedQuestionCount = questions.count { it.answerKeyVerified }
    val headlineText = remember(correctCount, gradedQuestionCount) {
        val ratio = correctCount.toFloat() / gradedQuestionCount.coerceAtLeast(1).toFloat()
        if (ratio >= 0.7f) "Harika iş çıkardın!" else "Çok yaklaştın!"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("full_screen_report_card")
    ) {
        // ÜST BAR: Solda Geri Oku, Ortada Test Başlığı, Sağda "▸ Kitapta Göster"
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackToBookPage) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = Color(0xFF0F172A)
                    )
                }

                Text(
                    text = session.testItem.fullTitle.ifBlank { "${session.topicName} - Test 1" },
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    onClick = onBackToBookPage,
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF3B82F6),
                    shadowElevation = 2.dp,
                    modifier = Modifier.testTag("show_in_book_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Kitapta Göster",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // KAYDIRILABİLİR KARNE GÖVDESİ
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // 1. ORTA KUTLAMA ALANI (Konfeti + Başlık + 3 Büyük Daire)
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Konfeti İllüstrasyonu
                    Text(
                        text = "🎉",
                        fontSize = 54.sp
                    )
                    Text(
                        text = headlineText,
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 21.sp
                    )
                    Text(
                        text = "Bu testi tamamladın, şimdi birlikte sonuçlara yakından bakalım.",
                        color = Color(0xFF475569),
                        fontSize = 13.5.sp,
                        textAlign = TextAlign.Center
                    )

                    // Testin Genel 5 Kademeli Zorluk Yıldızı Gösterimi (`1★ Kolay` .. `5★ Çok Zor`)
                    val testDiff = session.testItem.difficulty
                    val testStarsFilled = when (testDiff) {
                        DifficultyLevel.KOLAY -> 1
                        DifficultyLevel.ORTA -> 2
                        DifficultyLevel.OSYM -> 3
                        DifficultyLevel.ZOR -> 4
                        DifficultyLevel.COK_ZOR -> 5
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                for (s in 1..5) {
                                    Icon(
                                        imageVector = if (s <= testStarsFilled) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Test Zorluğu: ${testDiff.label} ($testStarsFilled/5 ★)",
                                color = Color(0xFF1E3A8A),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    ThreeStatCirclesRow345(
                        correctCount = correctCount,
                        wrongCount = wrongCount,
                        emptyCount = emptyCount,
                        circleSizeDp = 92
                    )
                    if (ungradedCount > 0) {
                        Text(
                            text = "$ungradedCount soru cevap anahtarı doğrulanmadığı için puanlanmadı",
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 2. İKİZ TEST ÖNERİ KUTUSU (`#F1F5F9` yuvarlatılmış kutu + `İkiz Testi Çöz` mavi hap buton)
            if (showTwinSuggestion) item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Bu testin bir ikizi var!\nPekiştirmen için çözmeni öneriyoruz.",
                            color = Color(0xFF1E293B),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Surface(
                            onClick = onSolveTwinTestForWrongQuestions,
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF3B82F6),
                            shadowElevation = 2.dp
                        ) {
                            Text(
                                text = "İkiz Testi Çöz",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            // 3. ALT BÖLÜM: "Çözüm Videoları" Başlığı + Yeşil Ders Etiketi + 4 Sütunlu Tablo Başlığı
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Çözüm Videoları",
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )

                    // Yeşil Hap Etiket (Örn: "AYT Fizik", "AYT Matematik")
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF84CC16)
                    ) {
                        Text(
                            text = "${session.examType.name} ${session.subjectName}",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp)
                        )
                    }

                    // 4 Sütunlu Tablo Başlık Satırı
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Soru No",
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            modifier = Modifier.weight(1.1f)
                        )
                        Text(
                            text = "Doğru Cevap",
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1.1f)
                        )
                        Text(
                            text = "Öğrenci Cevabı",
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1.1f)
                        )
                        Text(
                            text = "Zorluk Seviyesi",
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.5f)
                        )
                        Spacer(modifier = Modifier.width(34.dp))
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))
                }
            }

            // 4. SORU BAZLI TABLO SATIRLARI (1. Soru, 2. Soru... + 5 Yıldızlı Mavi Zorluk + Açılır Ok `⌄`)
            items(questions, key = { it.number }) { q ->
                val studentOpt = session.selectedAnswers[q.number]?.ifBlank { "-" } ?: "-"
                val isExpanded = expandedQuestionNumber == q.number
                val starsFilled = when (q.difficulty) {
                    DifficultyLevel.KOLAY -> 1
                    DifficultyLevel.ORTA -> 2
                    DifficultyLevel.OSYM -> 3
                    DifficultyLevel.ZOR -> 4
                    DifficultyLevel.COK_ZOR -> 5
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedQuestionNumber = if (isExpanded) null else q.number
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${q.number}. Soru",
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1.1f)
                        )
                        Text(
                            text = if (q.answerKeyVerified) q.correctOption else "?",
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1.1f)
                        )
                        Text(
                            text = studentOpt,
                            color = when {
                                studentOpt == "-" -> Color(0xFF64748B)
                                !q.answerKeyVerified -> Color(0xFFB45309)
                                studentOpt == q.correctOption -> Color(0xFF16A34A)
                                else -> Color(0xFFDC2626)
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1.1f)
                        )

                        // 5 Yıldız Üzerinden Mavi Yıldızlı Zorluk Gösterimi + 5 Kademeli Zorluk Etiketi (`1★ Kolay` .. `5★ Çok Zor`)
                        Row(
                            modifier = Modifier.weight(1.5f),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(q.difficulty.colorHex).copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = q.difficulty.label,
                                    color = Color(q.difficulty.colorHex),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.5.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            for (s in 1..5) {
                                Icon(
                                    imageVector = if (s <= starsFilled) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = null,
                                    tint = Color(0xFF3B82F6),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                expandedQuestionNumber = if (isExpanded) null else q.number
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Detay",
                                tint = Color(0xFF334155)
                            )
                        }
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (q.answerKeyVerified) "${q.topicHeader} • Doğru Cevap: ${q.correctOption}"
                                    else "${q.topicHeader} • Cevap anahtarı doğrulanmadı",
                                    color = Color(0xFF334155),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Surface(
                                        onClick = { onOpenTwinForQuestion(q) },
                                        shape = RoundedCornerShape(50),
                                        color = Color(0xFFE2E8F0)
                                    ) {
                                        Text(
                                            text = "İkiz Soru",
                                            color = Color(0xFF2563EB),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                                        )
                                    }
                                    Surface(
                                        onClick = { onWatchQuestionSolution(q) },
                                        shape = RoundedCornerShape(50),
                                        color = Color(0xFF3B82F6)
                                    ) {
                                        Text(
                                            text = "Çözüm İzle",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))
                }
            }
        }
    }
}

/**
 * Vektörel çizim motoru (Kalem, Akıllı Şekil ve `BlendMode.Clear` / `destination-out` Silgi)
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSmoothStroke(stroke: DrawStroke) {
    val pts = stroke.points
    if (pts.size < 2) return
    val isEraser = stroke.isEraser || stroke.colorHex == 0x00000000L
    val strokeColor = when {
        isEraser -> Color.Transparent
        stroke.isHighlighter -> Color(stroke.colorHex).copy(alpha = 0.36f)
        else -> Color(stroke.colorHex)
    }
    val blendMode = if (isEraser) BlendMode.Clear else BlendMode.SrcOver

    if (!isEraser && stroke.isSmartShape && pts.size >= 4) {
        val first = pts.first()
        val last = pts.last()
        val dist = sqrt((last.x - first.x) * (last.x - first.x) + (last.y - first.y) * (last.y - first.y))
        if (dist > 36f) {
            drawLine(
                color = strokeColor,
                start = first,
                end = last,
                strokeWidth = stroke.strokeWidthPx,
                cap = StrokeCap.Round
            )
            return
        }
    }

    val path = Path().apply {
        moveTo(pts.first().x, pts.first().y)
        for (i in 1 until pts.size) {
            val prev = pts[i - 1]
            val cur = pts[i]
            val midX = (prev.x + cur.x) / 2f
            val midY = (prev.y + cur.y) / 2f
            quadraticTo(prev.x, prev.y, midX, midY)
        }
        lineTo(pts.last().x, pts.last().y)
    }
    drawPath(
        path = path,
        color = strokeColor,
        style = Stroke(
            width = stroke.strokeWidthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        blendMode = blendMode
    )
}

/**
 * Vektörel soru şekli çizici (Görselsiz katalog soruları için)
 */
@Composable
fun QuestionVectorDiagram(
    visualType: QuestionVisualType,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val primaryStroke = Color(0xFF1E3A8A)

        when (visualType) {
            QuestionVisualType.FUNCTION_GRAPH -> {
                drawLine(
                    color = Color(0xFF64748B),
                    start = Offset(w * 0.12f, cy + h * 0.2f),
                    end = Offset(w * 0.88f, cy + h * 0.2f),
                    strokeWidth = 2.5f
                )
                drawLine(
                    color = Color(0xFF64748B),
                    start = Offset(cx, h * 0.1f),
                    end = Offset(cx, h * 0.9f),
                    strokeWidth = 2.5f
                )
                val curve = Path().apply {
                    moveTo(w * 0.22f, h * 0.18f)
                    quadraticTo(cx, h * 0.88f, w * 0.78f, h * 0.18f)
                }
                drawPath(curve, color = primaryStroke, style = Stroke(width = 3.5f))
            }

            else -> {
                val tri = Path().apply {
                    moveTo(cx, h * 0.16f)
                    lineTo(w * 0.22f, h * 0.82f)
                    lineTo(w * 0.78f, h * 0.82f)
                    close()
                }
                drawPath(tri, color = primaryStroke, style = Stroke(width = 3.5f))
            }
        }
    }
}
