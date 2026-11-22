package com.example.ui.screens

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.YksQuestion
import com.example.ui.theme.*
import com.example.viewmodel.InteractiveSolutionPlan
import com.example.viewmodel.InteractiveSolutionStep
import com.example.viewmodel.SorulabAiQuality
import org.json.JSONObject
import java.util.Locale

private enum class InteractiveSolutionTab(val label: String) {
    STEPS("Adım Adım"),
    WRONG_OPTIONS("Diğer Şıklar"),
    OSYM_SHORTCUT("ÖSYM Pratik")
}

/**
 * Standalone modal wrapper for the Interactive Solution Board.
 */
@Composable
fun InteractiveSolutionBoardModal(
    visible: Boolean,
    question: YksQuestion?,
    plan: InteractiveSolutionPlan?,
    isLoading: Boolean,
    errorMessage: String?,
    simplerStepExplanations: Map<Int, String>,
    loadingSimplerStepNumber: Int?,
    onRefreshPlan: () -> Unit,
    onExplainStepSimpler: (InteractiveSolutionStep) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible || question == null) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(26.dp)),
            color = SoruLabSurface,
            shadowElevation = 20.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF0B132B), Color(0xFF1D4ED8))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "İnteraktif Çözüm Tahtası",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                        Text(
                            text = "${question.topicHeader.ifBlank { "YKS" }} • Soru ${question.number}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Kapat",
                            tint = Color.White
                        )
                    }
                }
                InteractiveSolutionBoard(
                    question = question,
                    plan = plan,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    simplerStepExplanations = simplerStepExplanations,
                    loadingSimplerStepNumber = loadingSimplerStepNumber,
                    onRefreshPlan = onRefreshPlan,
                    onExplainStepSimpler = onExplainStepSimpler,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Interactive "Better-than-Video" Solution Board with:
 * - KaTeX textbook-quality math rendering (with offline HTML/Unicode fallback)
 * - Controlled Step-by-Step progression + "Tüm Adımları Göster" toggle
 * - Turkish TextToSpeech Voice Teacher with active-step highlight & auto-advance
 * - Socratic "Neden bu adımı attık?" & "Daha Basit Anlat" per step
 * - Tabs: Adım Adım | Diğer Şıklar Neden Yanlış? | 15 Saniyelik ÖSYM Pratik Yolu
 */
@Composable
fun InteractiveSolutionBoard(
    question: YksQuestion,
    plan: InteractiveSolutionPlan?,
    isLoading: Boolean,
    errorMessage: String?,
    simplerStepExplanations: Map<Int, String>,
    loadingSimplerStepNumber: Int?,
    onRefreshPlan: () -> Unit,
    onExplainStepSimpler: (InteractiveSolutionStep) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var activeTab by remember(question.id) { mutableStateOf(InteractiveSolutionTab.STEPS) }
    var currentStepIndex by remember(question.id) { mutableIntStateOf(0) }
    var showAllSteps by remember(question.id) { mutableStateOf(false) }
    val expandedWhySteps = remember(question.id) { mutableStateMapOf<Int, Boolean>() }

    // Turkish TextToSpeech Voice Teacher state
    var ttsReady by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isAutoPlayingAll by remember { mutableStateOf(false) }
    var speakingStepNumber by remember { mutableStateOf<Int?>(null) }
    var speechRate by remember { mutableFloatStateOf(1.0f) }
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }

    val latestPlan by rememberUpdatedState(plan)
    val latestIsAutoPlaying by rememberUpdatedState(isAutoPlayingAll)
    val latestSpeechRate by rememberUpdatedState(speechRate)

    fun buildStepSpeechText(step: InteractiveSolutionStep): String = buildString {
        append("Adım ${step.stepNumber}. ${step.title}. ")
        append(step.explanation)
        if (step.mathExpression.isNotBlank()) {
            val spokenMath = SorulabAiQuality.latexToSpokenTurkish(step.mathExpression)
            if (spokenMath.isNotBlank()) {
                append(" İşlemimiz: $spokenMath.")
            }
        }
        if (step.whyThisStep.isNotBlank()) {
            append(" Neden bu adımı attık? ${step.whyThisStep}")
        }
    }

    fun speakText(text: String, utteranceId: String, stepNum: Int?) {
        val tts = ttsInstance ?: return
        if (!ttsReady || text.isBlank()) return
        tts.setSpeechRate(latestSpeechRate)
        speakingStepNumber = stepNum
        isSpeaking = true
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        ttsInstance?.stop()
        isSpeaking = false
        isAutoPlayingAll = false
        speakingStepNumber = null
    }

    DisposableEffect(context) {
        var createdTts: TextToSpeech? = null
        createdTts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val trLocale = Locale.forLanguageTag("tr-TR")
                val langResult = createdTts?.setLanguage(trLocale)
                val ready = langResult != TextToSpeech.LANG_MISSING_DATA &&
                    langResult != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ready) {
                    createdTts?.setLanguage(Locale.getDefault())
                }
                createdTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post { isSpeaking = true }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            val currentPlan = latestPlan
                            if (latestIsAutoPlaying && currentPlan != null && utteranceId != null && utteranceId.startsWith("step_")) {
                                val finishedIdx = utteranceId.removePrefix("step_").toIntOrNull()
                                if (finishedIdx != null && finishedIdx + 1 < currentPlan.steps.size) {
                                    val nextIdx = finishedIdx + 1
                                    currentStepIndex = nextIdx
                                    val nextStep = currentPlan.steps[nextIdx]
                                    speakText(
                                        text = buildStepSpeechText(nextStep),
                                        utteranceId = "step_$nextIdx",
                                        stepNum = nextStep.stepNumber
                                    )
                                    return@post
                                }
                            }
                            isSpeaking = false
                            isAutoPlayingAll = false
                            speakingStepNumber = null
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            isSpeaking = false
                            isAutoPlayingAll = false
                            speakingStepNumber = null
                        }
                    }
                })
                mainHandler.post {
                    ttsInstance = createdTts
                    ttsReady = true
                }
            }
        }
        onDispose {
            createdTts?.stop()
            createdTts?.shutdown()
            ttsInstance = null
            ttsReady = false
        }
    }

    // Stop speech when switching questions
    LaunchedEffect(question.id) {
        stopSpeaking()
        currentStepIndex = 0
    }

    if (isLoading && plan == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFF3F6FB))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(SoruLabSkyTint),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = SoruLabCobaltBlue,
                        strokeWidth = 3.5.dp,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Text(
                    text = "İnteraktif Çözüm Tahtası Hazırlanıyor...",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = SoruLabInkBlack
                )
                Text(
                    text = "Adım adım formüller, 'Neden?' açıklamaları, diğer şıkların analizi ve 15 saniyelik ÖSYM kısayolu çıkarılıyor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SoruLabSlateMuted
                )
            }
        }
        return
    }

    val activePlan = plan ?: return
    val steps = activePlan.steps
    val safeStepIdx = currentStepIndex.coerceIn(0, (steps.lastIndex).coerceAtLeast(0))

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF3F6FB))
            .testTag("interactive_solution_board")
    ) {
        // Voice Teacher + Tabs Bar
        Surface(
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Voice Teacher Control Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val voiceBg by animateColorAsState(
                        targetValue = if (isSpeaking) SoruLabCoralRed else SoruLabRoyalBlue,
                        label = "voice_btn_bg"
                    )
                    Button(
                        onClick = {
                            if (isSpeaking) {
                                stopSpeaking()
                            } else if (steps.isNotEmpty()) {
                                activeTab = InteractiveSolutionTab.STEPS
                                isAutoPlayingAll = true
                                val startIdx = if (showAllSteps) 0 else safeStepIdx
                                currentStepIndex = startIdx
                                val step = steps[startIdx]
                                speakText(
                                    text = buildStepSpeechText(step),
                                    utteranceId = "step_$startIdx",
                                    stepNum = step.stepNumber
                                )
                            }
                        },
                        enabled = ttsReady && steps.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = voiceBg,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("voice_teacher_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Filled.StopCircle else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSpeaking) "Anlatımı Durdur" else "Sesli Dinle",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    // Speech speed selector pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SoruLabSurfaceVariant,
                        modifier = Modifier
                            .height(36.dp)
                            .clickable {
                                speechRate = when {
                                    speechRate < 0.95f -> 1.0f
                                    speechRate < 1.15f -> 1.25f
                                    else -> 0.85f
                                }
                                ttsInstance?.setSpeechRate(speechRate)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = "Okuma Hızı",
                                tint = SoruLabSlateDark,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${speechRate}x",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = SoruLabSlateDark
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (isLoading) {
                        CircularProgressIndicator(
                            color = SoruLabCobaltBlue,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        IconButton(
                            onClick = {
                                stopSpeaking()
                                onRefreshPlan()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Çözümü Yenile",
                                tint = SoruLabCobaltBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 3 Rich Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoruLabSurfaceVariant)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    InteractiveSolutionTab.entries.forEach { tab ->
                        val selected = tab == activeTab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) Color.White else Color.Transparent)
                                .clickable { activeTab = tab }
                                .padding(vertical = 8.dp)
                                .testTag("solution_tab_${tab.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                                ),
                                color = if (selected) SoruLabRoyalBlue else SoruLabSlateMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Scrollable Body
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = SoruLabAmberLight,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SoruLabAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF92400E),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            when (activeTab) {
                InteractiveSolutionTab.STEPS -> {
                    // "Soruya İlk Bakış" Spotlight Card
                    if (activePlan.firstGlanceStrategy.isNotBlank()) {
                        FirstGlanceCard(
                            coreConcept = activePlan.coreConcept,
                            firstGlanceStrategy = activePlan.firstGlanceStrategy
                        )
                    }

                    // Progress + Stepper Header
                    if (steps.isNotEmpty()) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SoruLabBorderLight, RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (showAllSteps) {
                                            "Tüm Adımlar (${steps.size} Adım)"
                                        } else {
                                            "Adım ${safeStepIdx + 1} / ${steps.size}"
                                        },
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                        color = SoruLabInkBlack
                                    )
                                    TextButton(
                                        onClick = { showAllSteps = !showAllSteps },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .height(30.dp)
                                            .testTag("toggle_all_steps_button")
                                    ) {
                                        Icon(
                                            imageVector = if (showAllSteps) Icons.Filled.ViewCarousel else Icons.Filled.ViewAgenda,
                                            contentDescription = null,
                                            tint = SoruLabCobaltBlue,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (showAllSteps) "Tek Tek İlerle" else "Tüm Adımları Göster",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = SoruLabCobaltBlue
                                        )
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = {
                                        if (showAllSteps) 1f else (safeStepIdx + 1f) / steps.size.coerceAtLeast(1)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50)),
                                    color = SoruLabCobaltBlue,
                                    trackColor = SoruLabSkyTint
                                )

                                if (!showAllSteps && steps.size > 1) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        steps.forEachIndexed { idx, step ->
                                            val isCurrent = idx == safeStepIdx
                                            val isSpokenNow = speakingStepNumber == step.stepNumber
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = when {
                                                    isSpokenNow -> SoruLabEmerald
                                                    isCurrent -> SoruLabCobaltBlue
                                                    idx < safeStepIdx -> SoruLabSkyTint
                                                    else -> SoruLabSurfaceVariant
                                                },
                                                modifier = Modifier.clickable {
                                                    currentStepIndex = idx
                                                }
                                            ) {
                                                Text(
                                                    text = "${step.stepNumber}",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = when {
                                                        isSpokenNow || isCurrent -> Color.White
                                                        idx < safeStepIdx -> SoruLabRoyalBlue
                                                        else -> SoruLabSlateMuted
                                                    },
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Step Cards
                        val visibleSteps = if (showAllSteps) steps else listOf(steps[safeStepIdx])
                        visibleSteps.forEach { step ->
                            InteractiveStepCard(
                                step = step,
                                isBeingSpoken = speakingStepNumber == step.stepNumber,
                                isWhyExpanded = expandedWhySteps[step.stepNumber] == true,
                                simplerExplanation = simplerStepExplanations[step.stepNumber],
                                isSimplerLoading = loadingSimplerStepNumber == step.stepNumber,
                                ttsReady = ttsReady,
                                onToggleWhy = {
                                    expandedWhySteps[step.stepNumber] = !(expandedWhySteps[step.stepNumber] ?: false)
                                },
                                onExplainSimpler = { onExplainStepSimpler(step) },
                                onSpeakStep = {
                                    if (speakingStepNumber == step.stepNumber && isSpeaking) {
                                        stopSpeaking()
                                    } else {
                                        isAutoPlayingAll = false
                                        speakText(
                                            text = buildStepSpeechText(step),
                                            utteranceId = "single_${step.stepNumber}",
                                            stepNum = step.stepNumber
                                        )
                                    }
                                }
                            )
                        }

                        // Prev / Next Controls when in single-step mode
                        if (!showAllSteps && steps.size > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (safeStepIdx > 0) {
                                            currentStepIndex = safeStepIdx - 1
                                        }
                                    },
                                    enabled = safeStepIdx > 0,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("prev_step_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Önceki Adım", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        if (safeStepIdx < steps.lastIndex) {
                                            currentStepIndex = safeStepIdx + 1
                                        } else {
                                            activeTab = InteractiveSolutionTab.WRONG_OPTIONS
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SoruLabCobaltBlue,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("next_step_button")
                                ) {
                                    Text(
                                        text = if (safeStepIdx < steps.lastIndex) "Sonraki Adım" else "Diğer Şıkları Gör",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Final Answer + Verification Card (shown on last step or when showing all steps)
                    if (showAllSteps || safeStepIdx == steps.lastIndex || steps.isEmpty()) {
                        FinalAnswerVerificationCard(
                            finalAnswer = activePlan.finalAnswer,
                            verificationCheck = activePlan.verificationCheck
                        )
                    }
                }

                InteractiveSolutionTab.WRONG_OPTIONS -> {
                    // Correct answer spotlight
                    FinalAnswerVerificationCard(
                        finalAnswer = activePlan.finalAnswer,
                        verificationCheck = activePlan.verificationCheck
                    )

                    Text(
                        text = "Diğer Şıklar Neden Yanlış?",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = SoruLabInkBlack
                    )

                    if (activePlan.wrongOptionsAnalysis.isEmpty()) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, SoruLabBorderLight, RoundedCornerShape(14.dp))
                        ) {
                            Text(
                                text = "Bu soru için şık bazlı çeldirici analizi üretilmedi.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SoruLabSlateMuted,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        activePlan.wrongOptionsAnalysis.entries
                            .sortedBy { it.key }
                            .forEach { (optionLetter, reason) ->
                                WrongOptionAnalysisCard(
                                    optionLetter = optionLetter,
                                    optionText = question.options[optionLetter].orEmpty(),
                                    reason = reason
                                )
                            }
                    }
                }

                InteractiveSolutionTab.OSYM_SHORTCUT -> {
                    // 15-Second ÖSYM Shortcut Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, SoruLabViolet.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(SoruLabVioletLight.copy(alpha = 0.55f), Color.White)
                                    )
                                )
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
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SoruLabViolet),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Bolt,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "15 SANİYELİK ÖSYM PRATİK YOLU",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 0.6.sp
                                            ),
                                            color = SoruLabViolet
                                        )
                                        Text(
                                            text = "Sınavda Hız Kazandıran Taktik",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = SoruLabInkBlack
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        if (isSpeaking) {
                                            stopSpeaking()
                                        } else {
                                            isAutoPlayingAll = false
                                            speakText(
                                                text = "15 saniyelik ÖSYM pratik yolu. ${activePlan.osymShortcut}",
                                                utteranceId = "shortcut",
                                                stepNum = null
                                            )
                                        }
                                    },
                                    enabled = ttsReady
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Pratik Yolu Dinle",
                                        tint = SoruLabViolet
                                    )
                                }
                            }

                            Text(
                                text = activePlan.osymShortcut,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                color = SoruLabInkBlack
                            )
                        }
                    }

                    // Core Concept Summary
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SoruLabBorderLight, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.School,
                                    contentDescription = null,
                                    tint = SoruLabRoyalBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Temel Kazanım & Kilit Formül",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = SoruLabRoyalBlue
                                )
                            }
                            Text(
                                text = activePlan.coreConcept,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                                color = SoruLabInkBlack
                            )
                        }
                    }

                    FinalAnswerVerificationCard(
                        finalAnswer = activePlan.finalAnswer,
                        verificationCheck = activePlan.verificationCheck
                    )
                }
            }
        }
    }
}

@Composable
private fun FirstGlanceCard(
    coreConcept: String,
    firstGlanceStrategy: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SoruLabRoyalBlue.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFEFF6FF), Color.White)
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(SoruLabRoyalBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Text(
                    text = "Soruya İlk Bakış (İlk 5 Saniye)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = SoruLabRoyalBlue
                )
            }
            Text(
                text = firstGlanceStrategy,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                color = SoruLabInkBlack
            )
            if (coreConcept.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SoruLabSkyTint.copy(alpha = 0.65f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lightbulb,
                            contentDescription = null,
                            tint = SoruLabRoyalBlue,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(15.dp)
                        )
                        Text(
                            text = coreConcept,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 18.sp
                            ),
                            color = SoruLabSlateDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveStepCard(
    step: InteractiveSolutionStep,
    isBeingSpoken: Boolean,
    isWhyExpanded: Boolean,
    simplerExplanation: String?,
    isSimplerLoading: Boolean,
    ttsReady: Boolean,
    onToggleWhy: () -> Unit,
    onExplainSimpler: () -> Unit,
    onSpeakStep: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isBeingSpoken) SoruLabEmerald else SoruLabBorderLight,
        label = "step_border_color"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = if (isBeingSpoken) 6.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isBeingSpoken) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("solution_step_card_${step.stepNumber}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Step Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isBeingSpoken) SoruLabEmerald else SoruLabCobaltBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${step.stepNumber}",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = SoruLabInkBlack
                        )
                    }
                    if (isBeingSpoken) {
                        Text(
                            text = "Öğretmen bu adımı anlatıyor...",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SoruLabEmerald
                        )
                    }
                }
                IconButton(
                    onClick = onSpeakStep,
                    enabled = ttsReady,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (isBeingSpoken) Icons.Filled.StopCircle else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Bu adımı sesli dinle",
                        tint = if (isBeingSpoken) SoruLabEmerald else SoruLabCobaltBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Teacher Explanation
            Text(
                text = step.explanation,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = SoruLabInkBlack
            )

            // Textbook-quality KaTeX Math Render Box
            if (step.mathExpression.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SoruLabRoyalBlue.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                ) {
                    KaTeXMathView(
                        expression = step.mathExpression,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Trap Warning Callout
            if (step.trapWarning.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SoruLabAmberLight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SoruLabAmber.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(17.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Bu Adımda Dikkat!",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color(0xFFB45309)
                            )
                            Text(
                                text = step.trapWarning,
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                color = Color(0xFF78350F)
                            )
                        }
                    }
                }
            }

            // Socratic Action Buttons: "Neden?" & "Daha Basit Anlat"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (step.whyThisStep.isNotBlank()) {
                    FilterChip(
                        selected = isWhyExpanded,
                        onClick = onToggleWhy,
                        label = {
                            Text(
                                text = "Neden bu adım?",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.HelpOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SoruLabSkyTint,
                            selectedLabelColor = SoruLabRoyalBlue,
                            selectedLeadingIconColor = SoruLabRoyalBlue
                        ),
                        modifier = Modifier.testTag("why_step_button_${step.stepNumber}")
                    )
                }

                AssistChip(
                    onClick = onExplainSimpler,
                    enabled = !isSimplerLoading,
                    label = {
                        Text(
                            text = if (isSimplerLoading) "Basitleştiriliyor..." else "Daha Basit Anlat",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    leadingIcon = {
                        if (isSimplerLoading) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = SoruLabViolet,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AutoFixHigh,
                                contentDescription = null,
                                tint = SoruLabViolet,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    modifier = Modifier.testTag("simpler_step_button_${step.stepNumber}")
                )
            }

            // Expandable "Neden bu adımı attık?" Card
            AnimatedVisibility(
                visible = isWhyExpanded && step.whyThisStep.isNotBlank(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SoruLabSkyTint.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Neden Bu Adımı Attık?",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = SoruLabRoyalBlue
                        )
                        Text(
                            text = step.whyThisStep,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                            color = SoruLabSlateDark
                        )
                    }
                }
            }

            // On-Demand "Daha Basit Anlat" Result Card
            AnimatedVisibility(
                visible = !simplerExplanation.isNullOrBlank(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SoruLabVioletLight.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SoruLabViolet.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Psychology,
                                contentDescription = null,
                                tint = SoruLabViolet,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Öğretmenden Çok Daha Basit Anlatım",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = SoruLabViolet
                            )
                        }
                        Text(
                            text = simplerExplanation.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                            color = SoruLabInkBlack
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FinalAnswerVerificationCard(
    finalAnswer: String,
    verificationCheck: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SoruLabEmeraldLight.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SoruLabEmerald.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = null,
                    tint = Color(0xFF059669),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = finalAnswer.ifBlank { "Çözüm Doğrulandı" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color(0xFF065F46)
                )
            }
            if (verificationCheck.isNotBlank()) {
                Text(
                    text = "Sağlama: $verificationCheck",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = Color(0xFF064E3B)
                )
            }
        }
    }
}

@Composable
private fun WrongOptionAnalysisCard(
    optionLetter: String,
    optionText: String,
    reason: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SoruLabCoralRed.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(SoruLabCoralLight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = optionLetter,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = SoruLabCoralRed
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (optionText.isNotBlank()) {
                    Text(
                        text = optionText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = SoruLabInkBlack
                    )
                }
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = SoruLabSlateDark
                )
            }
        }
    }
}

/**
 * Renders LaTeX mathematical expressions with KaTeX in a lightweight transparent WebView,
 * with a built-in zero-dependency HTML/Unicode math fallback when offline.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KaTeXMathView(
    expression: String,
    modifier: Modifier = Modifier
) {
    val html = remember(expression) { buildKatexHtmlDocument(expression) }

    AndroidView(
        modifier = modifier.heightIn(min = 44.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    builtInZoomControls = false
                    displayZoomControls = false
                }
                tag = expression
                loadDataWithBaseURL("https://cdn.jsdelivr.net/", html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            if (webView.tag != expression) {
                webView.tag = expression
                webView.loadDataWithBaseURL("https://cdn.jsdelivr.net/", html, "text/html", "UTF-8", null)
            }
        }
    )
}

private fun buildKatexHtmlDocument(expression: String): String {
    val quotedExpr = JSONObject.quote(expression)
    return """
<!DOCTYPE html>
<html lang="tr">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css" />
<script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js" onload="renderKatex()" onerror="renderOffline()"></script>
<style>
  html, body {
    margin: 0;
    padding: 0;
    background: transparent;
    color: #0F172A;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  }
  #math-root {
    padding: 8px 6px;
    font-size: 17px;
    line-height: 1.5;
    overflow-x: auto;
    text-align: center;
    font-weight: 600;
  }
  .frac {
    display: inline-flex;
    flex-direction: column;
    vertical-align: middle;
    text-align: center;
    margin: 0 3px;
    font-size: 0.92em;
  }
  .frac .num {
    border-bottom: 1.5px solid #0F172A;
    padding: 0 4px 1px 4px;
  }
  .frac .den {
    padding: 1px 4px 0 4px;
  }
  .rad {
    border-top: 1.5px solid #0F172A;
    padding: 0 3px;
  }
</style>
</head>
<body>
<div id="math-root"></div>
<script>
  var rawExpr = $quotedExpr;
  function escapeHtml(str) {
    return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }
  function renderOffline() {
    var s = escapeHtml(rawExpr);
    for (var i = 0; i < 3; i++) {
      s = s.replace(/\\(?:d|t)?frac\{([^{}]+)\}\{([^{}]+)\}/g,
        '<span class="frac"><span class="num">$1</span><span class="den">$2</span></span>');
    }
    s = s.replace(/\\sqrt\{([^{}]+)\}/g, '&#8730;<span class="rad">$1</span>');
    s = s.replace(/\^\{([^{}]+)\}/g, '<sup>$1</sup>');
    s = s.replace(/\^([A-Za-z0-9\-])/g, '<sup>$1</sup>');
    s = s.replace(/_\{([^{}]+)\}/g, '<sub>$1</sub>');
    s = s.replace(/_([A-Za-z0-9])/g, '<sub>$1</sub>');
    var map = [
      [/\\cdot/g, ' &middot; '], [/\\times/g, ' &times; '], [/\\div/g, ' &divide; '],
      [/\\pm/g, ' &plusmn; '], [/\\leq?/g, ' &le; '], [/\\geq?/g, ' &ge; '],
      [/\\neq?/g, ' &ne; '], [/\\approx/g, ' &approx; '], [/\\infty/g, '&infin;'],
      [/\\pi/g, '&pi;'], [/\\alpha/g, '&alpha;'], [/\\beta/g, '&beta;'],
      [/\\theta/g, '&theta;'], [/\\Delta/g, '&Delta;'], [/\\sum/g, '&sum;'],
      [/\\int/g, '&int;'], [/\\Rightarrow/g, ' &rArr; '], [/\\rightarrow|\\to/g, ' &rarr; '],
      [/\\left|\\right/g, ''], [/\\,|\\;|\\quad/g, ' ']
    ];
    for (var j = 0; j < map.length; j++) {
      s = s.replace(map[j][0], map[j][1]);
    }
    document.getElementById("math-root").innerHTML = s;
  }
  function renderKatex() {
    try {
      if (window.katex) {
        window.katex.render(rawExpr, document.getElementById("math-root"), {
          displayMode: true,
          throwOnError: false
        });
        return;
      }
    } catch (e) {}
    renderOffline();
  }
  renderOffline();
  window.addEventListener("load", function() {
    if (window.katex) renderKatex();
  });
</script>
</body>
</html>
    """.trimIndent()
}
