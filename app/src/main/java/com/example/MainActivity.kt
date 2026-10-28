package com.example

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ExamType
import com.example.data.SoruLabCatalog
import com.example.ui.screens.AnaSayfaScreen
import com.example.ui.screens.DenemelerHubScreen
import com.example.ui.screens.FloatingCircularBottomNav
import com.example.ui.screens.IstatistiklerimScreen
import com.example.ui.screens.LiveSolvingBoardScreen
import com.example.ui.screens.SoruHavuzuAndPdfStudioView
import com.example.ui.screens.SoruLabAiAssistantDialog
import com.example.ui.screens.SoruPratigiScreen
import com.example.ui.theme.SoruLabBackground
import com.example.ui.theme.SoruLabBorderLight
import com.example.ui.theme.SoruLabCobaltBlue
import com.example.ui.theme.SoruLabCoralLight
import com.example.ui.theme.SoruLabCoralRed
import com.example.ui.theme.SoruLabEmerald
import com.example.ui.theme.SoruLabInkBlack
import com.example.ui.theme.SoruLabRoyalBlue
import com.example.ui.theme.SoruLabSkyTint
import com.example.ui.theme.SoruLabSlateMuted
import com.example.ui.theme.SoruLabSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SoruLabViolet
import com.example.util.configureAppCheck
import com.example.viewmodel.AiSolverViewModel
import com.example.viewmodel.BatchPdfCropState
import com.example.viewmodel.BoardViewMode
import com.example.viewmodel.MainNavTab
import com.example.viewmodel.SoruLabViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SoruLabAppRoot()
            }
        }
    }
}

@Composable
fun SoruLabAppRoot() {
    val context = LocalContext.current
    val mainViewModel: SoruLabViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SoruLabViewModel(context.applicationContext) as T
            }
        }
    )
    val aiViewModel: AiSolverViewModel = viewModel()

    val currentUser by mainViewModel.currentUser.collectAsStateWithLifecycle()
    val recognizedDeviceAccount by mainViewModel.recognizedDeviceAccount.collectAsStateWithLifecycle()
    val authError by mainViewModel.authError.collectAsStateWithLifecycle()

    // Background silent Google account sync when device is recognized but Firebase Auth token was reset by preview reload
    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            trySilentGoogleAutoSignIn(context)
        }
    }

    if (currentUser == null && recognizedDeviceAccount == null) {
        SoruLabSignInGate(
            errorMessage = authError,
            onErrorMessage = { mainViewModel.setAuthError(it) },
            onRecognizeDeviceQuickStart = {
                mainViewModel.recognizeCurrentDevice()
            }
        )
        return
    }

    val activeSession by mainViewModel.activeSession.collectAsStateWithLifecycle()
    if (activeSession != null) {
        ActiveSolvingSessionHost(
            mainViewModel = mainViewModel,
            aiViewModel = aiViewModel
        )
        return
    }

    val selectedTab by mainViewModel.selectedTab.collectAsStateWithLifecycle()
    val displayName = remember(currentUser, recognizedDeviceAccount) {
        currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: currentUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: recognizedDeviceAccount?.displayName?.takeIf { it.isNotBlank() }
            ?: "YKS Öğrencisi"
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(SoruLabBackground),
        containerColor = SoruLabBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Box(modifier = Modifier.navigationBarsPadding()) {
                FloatingCircularBottomNav(
                    selectedTab = selectedTab,
                    onSelectTab = { mainViewModel.selectTab(it) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                MainNavTab.ANA_SAYFA -> {
                    AnaSayfaTabHost(
                        displayName = displayName,
                        mainViewModel = mainViewModel
                    )
                }

                MainNavTab.SORU_PRATIGI -> {
                    SoruPratigiTabHost(
                        mainViewModel = mainViewModel
                    )
                }

                MainNavTab.DENEMELER -> {
                    DenemelerTabHost(
                        mainViewModel = mainViewModel,
                        aiViewModel = aiViewModel
                    )
                }

                MainNavTab.ISTATISTIKLERIM -> {
                    IstatistiklerimTabHost(
                        mainViewModel = mainViewModel
                    )
                }
            }

            AiAssistantAndBannerHost(
                mainViewModel = mainViewModel,
                aiViewModel = aiViewModel
            )
        }
    }
}

@Composable
private fun ActiveSolvingSessionHost(
    mainViewModel: SoruLabViewModel,
    aiViewModel: AiSolverViewModel
) {
    val session by mainViewModel.activeSession.collectAsStateWithLifecycle()
    val elapsedSeconds by mainViewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val boardStrokes by mainViewModel.boardStrokes.collectAsStateWithLifecycle()
    val activeTool by mainViewModel.activeTool.collectAsStateWithLifecycle()
    val selectedStrokeSizeIndex by mainViewModel.selectedStrokeSizeIndex.collectAsStateWithLifecycle()
    val selectedPenColorHex by mainViewModel.selectedPenColorHex.collectAsStateWithLifecycle()
    val customPublishers by mainViewModel.customPublishers.collectAsStateWithLifecycle()

    val curSession = session ?: return
    val canvasKey = remember(curSession.viewMode, curSession.testItem.id, curSession.currentQuestionIndex) {
        mainViewModel.currentCanvasKey()
    }
    val strokesForCurrent = boardStrokes[canvasKey].orEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        LiveSolvingBoardScreen(
            session = curSession.copy(elapsedSeconds = elapsedSeconds),
            strokes = strokesForCurrent,
            activeTool = activeTool,
            selectedStrokeSizeIndex = selectedStrokeSizeIndex,
            selectedPenColorHex = selectedPenColorHex,
            onExitSession = { mainViewModel.exitSolvingSession() },
            onToggleViewMode = { mainViewModel.toggleBoardViewMode() },
            onSelectTool = { mainViewModel.selectDrawingTool(it) },
            onSelectStrokeSize = { mainViewModel.selectStrokeSize(it) },
            onSelectPenColor = { mainViewModel.selectPenColor(it) },
            onAddStroke = { mainViewModel.addStrokeToCurrentCanvas(it) },
            onClearCanvas = { mainViewModel.clearCurrentCanvasStrokes() },
            onSelectQuestionIndex = { mainViewModel.selectQuestionIndex(it) },
            onSelectOption = { qNum, opt -> mainViewModel.selectOptionForQuestion(qNum, opt) },
            onToggleOpticalForm = { mainViewModel.toggleOpticalFormDrawer() },
            onToggleStatsDrawer = { mainViewModel.toggleStatsDrawer() },
            onToggleQuestionMap = { mainViewModel.toggleQuestionMapDrawer() },
            onFinishTest = { mainViewModel.finishActiveTestAndReview() },
            onGoToStatsTab = { mainViewModel.navigateFromReviewToStats() },
            onAskAiForQuestion = { q -> mainViewModel.openAiAssistant(q) },
            onGenerateTwinQuestion = { q, cb -> aiViewModel.generateTwinQuestionOnDemand(q, cb) },
            onGenerateSolutionForQuestion = { q, cb -> aiViewModel.generateQuestionSolutionOnDemand(q, cb) },
            onAddQuestionToKumbara = { q -> mainViewModel.addQuestionToKumbara(q) },
            customPublishers = customPublishers
        )

        AiAssistantAndBannerHost(
            mainViewModel = mainViewModel,
            aiViewModel = aiViewModel
        )
    }
}

@Composable
private fun AnaSayfaTabHost(
    displayName: String,
    mainViewModel: SoruLabViewModel
) {
    val currentUser by mainViewModel.currentUser.collectAsStateWithLifecycle()
    val recognizedDeviceAccount by mainViewModel.recognizedDeviceAccount.collectAsStateWithLifecycle()
    val userTestResults by mainViewModel.userTestResults.collectAsStateWithLifecycle()
    val userCustomQuestions by mainViewModel.userCustomQuestions.collectAsStateWithLifecycle()

    val userEmail = remember(currentUser, recognizedDeviceAccount) {
        currentUser?.email?.takeIf { it.isNotBlank() }
            ?: recognizedDeviceAccount?.email?.takeIf { it.isNotBlank() }
            ?: ""
    }

    AnaSayfaScreen(
        userName = displayName,
        userEmail = userEmail,
        allTestResults = userTestResults,
        recentResult = userTestResults.firstOrNull(),
        totalSolvedTests = userTestResults.size,
        customQuestionsCount = userCustomQuestions.size,
        onInspectRecentResult = { record ->
            mainViewModel.openRecentResultForReview(record)
        },
        onStartDefaultTest = {
            mainViewModel.selectTab(MainNavTab.SORU_PRATIGI)
        },
        onNavigateTab = { mainViewModel.selectTab(it) },
        onOpenPdfCropper = {
            mainViewModel.selectTab(MainNavTab.DENEMELER)
            mainViewModel.openPdfCropperStudio()
        },
        onOpenAiAssistant = {
            mainViewModel.openAiAssistant(null)
        },
        onSignOut = { mainViewModel.signOut() },
        onResetAllUserData = { mainViewModel.clearAllUserData() }
    )
}

@Composable
private fun SoruPratigiTabHost(
    mainViewModel: SoruLabViewModel
) {
    val selectedExamType by mainViewModel.selectedExamType.collectAsStateWithLifecycle()
    val selectedTopic by mainViewModel.selectedTopic.collectAsStateWithLifecycle()
    val selectedTestLevel by mainViewModel.selectedTestLevel.collectAsStateWithLifecycle()
    val generatedTopicTests by mainViewModel.generatedTopicTests.collectAsStateWithLifecycle()
    val dialogTestItem by mainViewModel.dialogTestItem.collectAsStateWithLifecycle()
    val userTestResults by mainViewModel.userTestResults.collectAsStateWithLifecycle()

    val currentTopicTests = remember(selectedTopic, selectedTestLevel, generatedTopicTests) {
        selectedTopic?.let { topic ->
            mainViewModel.getTestsForTopicAndLevel(topic, selectedTestLevel)
        }.orEmpty()
    }
    val completedTestIds = remember(userTestResults) {
        userTestResults.filter { it.isSolvedAttempt }.map { it.testId }.toSet()
    }

    SoruPratigiScreen(
        selectedExamType = selectedExamType,
        selectedTopic = selectedTopic,
        selectedTestLevel = selectedTestLevel,
        currentLevelTests = currentTopicTests,
        dialogTestPair = dialogTestItem,
        isTestCompleted = { testItem ->
            completedTestIds.contains(testItem.id)
        },
        onCheckPoolAvailability = { topic, level ->
            mainViewModel.checkPoolAvailability(topic, level)
        },
        onSelectExamType = { mainViewModel.selectExamType(it) },
        onSelectTopic = { mainViewModel.openTopic(it) },
        onCloseTopic = { mainViewModel.closeTopic() },
        onSelectTestLevel = { mainViewModel.selectTestLevel(it) },
        onGenerateAiLevelTest = { topic, level ->
            mainViewModel.generateNewAiLevelTest(topic, level)
        },
        onTestClicked = { topic, test -> mainViewModel.onTestItemClicked(topic, test) },
        onDismissDialog = { mainViewModel.dismissTestDialog() },
        onStartTestAction = { topic, test, isReview, openOpticalFirst ->
            mainViewModel.startSolvingSession(
                topic = topic,
                testItem = test,
                initialViewMode = BoardViewMode.FULL_PAGE,
                isReviewMode = isReview,
                openOpticalFormFirst = openOpticalFirst
            )
        },
        onOpenAiAssistant = { mainViewModel.openAiAssistant(null) },
        onOpenPdfCropper = {
            mainViewModel.selectTab(MainNavTab.DENEMELER)
            mainViewModel.openPdfCropperStudio()
        }
    )
}

@Composable
private fun DenemelerTabHost(
    mainViewModel: SoruLabViewModel,
    aiViewModel: AiSolverViewModel
) {
    val context = LocalContext.current
    val showPdfCropperStudio by mainViewModel.showPdfCropperStudio.collectAsStateWithLifecycle()
    val userCustomQuestions by mainViewModel.userCustomQuestions.collectAsStateWithLifecycle()
    val customPublishers by mainViewModel.customPublishers.collectAsStateWithLifecycle()
    val userTestResults by mainViewModel.userTestResults.collectAsStateWithLifecycle()
    val isAutoTagging by aiViewModel.isAutoTagging.collectAsStateWithLifecycle()
    val batchStatusText by aiViewModel.batchStatusText.collectAsStateWithLifecycle()
    val batchProgressPercent by aiViewModel.batchProgressPercent.collectAsStateWithLifecycle()
    val batchErrorText by aiViewModel.batchErrorText.collectAsStateWithLifecycle()

    var selectedExamFilter by remember { mutableStateOf<ExamType?>(null) }

    if (showPdfCropperStudio) {
        val batchState = remember(isAutoTagging, batchStatusText, batchProgressPercent, batchErrorText) {
            BatchPdfCropState(
                isAnalyzing = isAutoTagging,
                progressPercent = batchProgressPercent,
                progressMessage = batchStatusText.orEmpty(),
                errorMessage = batchErrorText
            )
        }
        val selectedPubLogoLookup = remember(customPublishers) {
            customPublishers.associateBy({ it.name.lowercase() }, { it.customLogoPath })
        }

        SoruHavuzuAndPdfStudioView(
            customQuestions = userCustomQuestions,
            customPublishers = customPublishers,
            batchCropState = batchState,
            onStartFastParallelPdfExtraction = { pdfUri, fileNameHint, publisher, onComplete ->
                val logoUri = publisher.customLogoPath.ifBlank {
                    selectedPubLogoLookup[publisher.name.lowercase()].orEmpty()
                }
                aiViewModel.extractFullPdfFastParallel(
                    context = context,
                    pdfUri = pdfUri,
                    fileNameHint = fileNameHint,
                    publisherName = publisher.name,
                    publisherLogoUri = logoUri,
                    customPublishers = customPublishers,
                    onComplete = { extractedList, coverPath, detectedBookTitle ->
                        onComplete(extractedList, coverPath, detectedBookTitle)
                    }
                )
            },
            onAddCustomPublisher = { name, shortCode, logoPath ->
                mainViewModel.addCustomPublisher(name, shortCode, logoPath)
            },
            onSaveExtractedPdfBatch = { extractedItems, publisherName, publisherLogoUri, bookTitle, coverPath ->
                mainViewModel.saveExtractedPdfQuestionsBatch(
                    extractedItems = extractedItems,
                    publisherName = publisherName,
                    publisherLogoUri = publisherLogoUri,
                    bookOrTestName = bookTitle,
                    bookCoverPath = coverPath
                )
            },
            onDismissError = {
                aiViewModel.clearBatchError()
            },
            onDeleteLibraryBook = { questionIds -> mainViewModel.deleteLibraryBookQuestions(questionIds) },
            onClearAllData = { mainViewModel.clearAllUserData() },
            onBack = { mainViewModel.closePdfCropperStudio() },
            onSolveBookInPractice = { firstRecord ->
                val bookName = firstRecord.bookOrTestName.ifBlank { "${firstRecord.publisherTag} Soru Bankası" }
                val bookQuestions = userCustomQuestions.filter {
                    it.bookOrTestName.ifBlank { "${it.publisherTag} Soru Bankası" } == bookName
                }.ifEmpty { listOf(firstRecord) }
                mainViewModel.solveBookQuestionsInPractice(bookName, bookQuestions)
            }
        )
    } else {
        DenemelerHubScreen(
            selectedFilter = selectedExamFilter,
            mockExams = SoruLabCatalog.mockExams,
            solvedRecords = userTestResults,
            customQuestionsCount = userCustomQuestions.size,
            onSelectFilter = { selectedExamFilter = it },
            onStartExam = { mockExam ->
                mainViewModel.startMockExam(mockExam, reviewOnly = false)
            },
            onReviewExam = { mockExam ->
                mainViewModel.startMockExam(mockExam, reviewOnly = true)
            },
            onOpenPdfStudio = { mainViewModel.openPdfCropperStudio() }
        )
    }
}

@Composable
private fun IstatistiklerimTabHost(
    mainViewModel: SoruLabViewModel
) {
    val userTestResults by mainViewModel.userTestResults.collectAsStateWithLifecycle()
    val userCustomQuestions by mainViewModel.userCustomQuestions.collectAsStateWithLifecycle()

    IstatistiklerimScreen(
        testResults = userTestResults,
        customQuestionsCount = userCustomQuestions.size,
        onInspectResult = { record ->
            mainViewModel.openRecentResultForReview(record)
        }
    )
}

@Composable
private fun AiAssistantAndBannerHost(
    mainViewModel: SoruLabViewModel,
    aiViewModel: AiSolverViewModel
) {
    val showAiAssistantModal by mainViewModel.showAiAssistantModal.collectAsStateWithLifecycle()
    val aiFocusedQuestion by mainViewModel.aiFocusedQuestion.collectAsStateWithLifecycle()
    val statusBannerMessage by mainViewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val aiMessages by aiViewModel.messages.collectAsStateWithLifecycle()
    val aiLoading by aiViewModel.isLoading.collectAsStateWithLifecycle()

    SoruLabAiAssistantDialog(
        visible = showAiAssistantModal,
        focusedQuestion = aiFocusedQuestion,
        messages = aiMessages,
        isLoading = aiLoading,
        onDismiss = { mainViewModel.closeAiAssistant() },
        onAskQuestionSolution = { q, prompt -> aiViewModel.askAboutQuestion(q, prompt) },
        onSendFreeMessage = { text -> aiViewModel.sendFreeMessage(text) },
        onClearHistory = { aiViewModel.clearChatHistory() }
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = statusBannerMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Surface(
                color = SoruLabCobaltBlue,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = statusBannerMessage.orEmpty(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

private suspend fun trySilentGoogleAutoSignIn(context: android.content.Context): Boolean {
    if (Firebase.auth.currentUser != null) return true
    val webClientId = runCatching { context.getString(R.string.default_web_client_id) }.getOrDefault("")
    if (webClientId.isBlank()) return false
    val credentialManager = CredentialManager.create(context)

    // Stage 1: Authorized accounts silent sign-in
    try {
        val authorizedOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .build()
        val req1 = GetCredentialRequest.Builder()
            .addCredentialOption(authorizedOption)
            .build()
        val res1 = credentialManager.getCredential(context, req1)
        signInWithGoogleCredential(res1.credential)
        return true
    } catch (_: NoCredentialException) {
        // Proceed to Stage 2
    } catch (_: GetCredentialCancellationException) {
        return false
    } catch (e: Exception) {
        Log.d("SoruLabAuth", "Stage 1 silent sign-in skipped: ${e.message}")
    }

    // Stage 2: Any Google account on device with auto-select enabled
    try {
        val anyAccountOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .build()
        val req2 = GetCredentialRequest.Builder()
            .addCredentialOption(anyAccountOption)
            .build()
        val res2 = credentialManager.getCredential(context, req2)
        signInWithGoogleCredential(res2.credential)
        return true
    } catch (_: Exception) {
        return false
    }
}

@Composable
private fun SoruLabSignInGate(
    errorMessage: String?,
    onErrorMessage: (String?) -> Unit,
    onRecognizeDeviceQuickStart: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        trySilentGoogleAutoSignIn(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFEFF6FF), SoruLabBackground, Color.White)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(SoruLabRoyalBlue, SoruLabCobaltBlue)))
                    .border(2.dp, SoruLabSkyTint, RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.School,
                    contentDescription = "SoruLab Logo",
                    tint = Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = SoruLabSkyTint,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "YKS 2026 • TYT & AYT AKILLI SORU LABORATUVARI",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp
                    ),
                    color = SoruLabRoyalBlue,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "SoruLab",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = SoruLabInkBlack
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tahta kalitesinde canlı soru çözüm ekranı, PDF'ten akıllı soru kırpma & etiketleme stüdyosu ve Kunduz tarzı SoruLab AI çözüm asistanı.",
                style = MaterialTheme.typography.bodyMedium,
                color = SoruLabSlateMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = SoruLabSurface,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .border(1.dp, SoruLabBorderLight, RoundedCornerShape(24.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SignInHighlightRow(
                        icon = Icons.Filled.Draw,
                        tint = SoruLabCobaltBlue,
                        title = "Çift Modlu Canlı Tahta & Optik Form",
                        subtitle = "Tam sayfa yaprak test veya tekli soru modunda kalemle çözün, optik forma işleyin."
                    )
                    SignInHighlightRow(
                        icon = Icons.Filled.Crop,
                        tint = Color(0xFF0891B2),
                        title = "PDF Soru Kırpma & Otomatik Etiketleme",
                        subtitle = "Kaliteli yayınların PDF'lerinden soruyu kolayca kırpın, ders/konu/zorluk etiketleyip havuza aktarın."
                    )
                    SignInHighlightRow(
                        icon = Icons.Filled.AutoAwesome,
                        tint = SoruLabViolet,
                        title = "SoruLab AI Çözüm Asistanı",
                        subtitle = "Takıldığınız her soruda adım adım YKS çözüm stratejisini anında öğrenin."
                    )
                    SignInHighlightRow(
                        icon = Icons.Filled.CheckCircle,
                        tint = SoruLabEmerald,
                        title = "Cihaz Tanıma & Bulut Senkronizasyonu",
                        subtitle = "Bu cihaz otomatik tanınır; tüm çözdüğünüz testler ve oluşturduğunuz testler kaybolmaz."
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SoruLabCoralLight,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = SoruLabCoralRed,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        onErrorMessage(null)
                        try {
                            val webClientId = context.getString(R.string.default_web_client_id)
                            val credentialManager = CredentialManager.create(context)
                            val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(signInOption)
                                .build()
                            val result = credentialManager.getCredential(context, request)
                            signInWithGoogleCredential(result.credential)
                        } catch (_: GetCredentialCancellationException) {
                            onErrorMessage(null)
                        } catch (e: GetCredentialException) {
                            Log.e("SoruLabAuth", "Google sign-in failed, recognizing device", e)
                            onRecognizeDeviceQuickStart()
                        } catch (e: Exception) {
                            Log.e("SoruLabAuth", "Sign-in exception, recognizing device", e)
                            onRecognizeDeviceQuickStart()
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("google_sign_in_button"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SoruLabCobaltBlue,
                    contentColor = Color.White
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Google Hesabı Bağlanıyor...", fontWeight = FontWeight.Bold)
                } else {
                    Text(
                        text = "Google ile Giriş Yap (Cihazı Tanı)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            androidx.compose.material3.OutlinedButton(
                onClick = onRecognizeDeviceQuickStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("recognize_device_quick_start_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Tanınan Cihaz Oturumu ile Devam Et",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = SoruLabCobaltBlue
                )
            }
        }
    }
}

private suspend fun signInWithGoogleCredential(credential: androidx.credentials.Credential) {
    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
        Firebase.auth.signInWithCredential(firebaseCredential).await()
    }
}

@Composable
private fun SignInHighlightRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = SoruLabInkBlack
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SoruLabSlateMuted
            )
        }
    }
}
