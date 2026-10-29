package com.example.viewmodel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CustomQuestionRecord
import com.example.data.DifficultyLevel
import com.example.data.ExamType
import com.example.data.MockExamItem
import com.example.data.QuestionVisualType
import com.example.data.RecognizedDeviceAccount
import com.example.data.SoruLabCatalog
import com.example.data.SoruLabPublisherPresets
import com.example.data.SoruLabRepository
import com.example.data.TestResultRecord
import com.example.data.TopicTestLevel
import com.example.data.YksPublisherInfo
import com.example.data.YksQuestion
import com.example.data.YksTestItem
import com.example.data.YksTopic
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class MainNavTab(val title: String) {
    ANA_SAYFA("Anasayfa"),
    SORU_PRATIGI("Soru Pratiği"),
    DENEMELER("Denemeler"),
    ISTATISTIKLERIM("İstatistiklerim")
}

enum class BoardViewMode {
    FULL_PAGE,
    SINGLE_QUESTION
}

enum class DrawingTool {
    PEN,
    SMART_SHAPE,
    HAND_PAN,
    ERASER
}

@Immutable
data class PoolAvailabilityCheck(
    val canGenerate: Boolean,
    val availableQuestionCount: Int,
    val reasonTitle: String,
    val reasonMessage: String
)

@Immutable
data class DrawStroke(
    val points: List<Offset>,
    val colorHex: Long,
    val strokeWidthPx: Float,
    val isSmartShape: Boolean = false
)

@Immutable
data class ActiveSolvingSession(
    val testItem: YksTestItem,
    val examType: ExamType,
    val subjectId: String,
    val subjectName: String,
    val topicId: String,
    val topicName: String,
    val viewMode: BoardViewMode = BoardViewMode.FULL_PAGE,
    val isReviewMode: Boolean = false,
    val isOpticalFormDrawerOpen: Boolean = false,
    val isStatsDrawerOpen: Boolean = false,
    val isQuestionMapDrawerOpen: Boolean = false,
    val currentQuestionIndex: Int = 0,
    val selectedAnswers: Map<Int, String> = emptyMap(),
    val elapsedSeconds: Int = 0
)

class SoruLabViewModel(
    private val repository: SoruLabRepository
) : ViewModel() {

    constructor(context: Context) : this(SoruLabRepository(context.applicationContext))

    private val auth = runCatching { Firebase.auth }.getOrNull()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth?.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _recognizedDeviceAccount = MutableStateFlow<RecognizedDeviceAccount?>(
        repository.loadRecognizedDeviceAccount()
    )
    val recognizedDeviceAccount: StateFlow<RecognizedDeviceAccount?> = _recognizedDeviceAccount.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // App opens directly on Anasayfa (MainNavTab.ANA_SAYFA) per user requirement
    private val _selectedTab = MutableStateFlow(MainNavTab.ANA_SAYFA)
    val selectedTab: StateFlow<MainNavTab> = _selectedTab.asStateFlow()

    private val _selectedExamType = MutableStateFlow(ExamType.TYT)
    val selectedExamType: StateFlow<ExamType> = _selectedExamType.asStateFlow()

    private val _selectedTopic = MutableStateFlow<YksTopic?>(null)
    val selectedTopic: StateFlow<YksTopic?> = _selectedTopic.asStateFlow()

    private val _selectedTestLevel = MutableStateFlow(TopicTestLevel.OSYM)
    val selectedTestLevel: StateFlow<TopicTestLevel> = _selectedTestLevel.asStateFlow()

    private val _generatedTopicTests = MutableStateFlow<Map<String, List<YksTestItem>>>(emptyMap())
    val generatedTopicTests: StateFlow<Map<String, List<YksTestItem>>> = _generatedTopicTests.asStateFlow()

    private val _dialogTestItem = MutableStateFlow<Pair<YksTopic, YksTestItem>?>(null)
    val dialogTestItem: StateFlow<Pair<YksTopic, YksTestItem>?> = _dialogTestItem.asStateFlow()

    private val _activeSession = MutableStateFlow<ActiveSolvingSession?>(null)
    val activeSession: StateFlow<ActiveSolvingSession?> = _activeSession.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _boardStrokes = MutableStateFlow<Map<String, List<DrawStroke>>>(emptyMap())
    val boardStrokes: StateFlow<Map<String, List<DrawStroke>>> = _boardStrokes.asStateFlow()

    private val _activeTool = MutableStateFlow(DrawingTool.PEN)
    val activeTool: StateFlow<DrawingTool> = _activeTool.asStateFlow()

    private val _selectedStrokeSizeIndex = MutableStateFlow(1)
    val selectedStrokeSizeIndex: StateFlow<Int> = _selectedStrokeSizeIndex.asStateFlow()

    private val _selectedPenColorHex = MutableStateFlow(0xFF1D4ED8L)
    val selectedPenColorHex: StateFlow<Long> = _selectedPenColorHex.asStateFlow()

    private val _userTestResults = MutableStateFlow<List<TestResultRecord>>(emptyList())
    val userTestResults: StateFlow<List<TestResultRecord>> = _userTestResults.asStateFlow()

    private var allCloudTestRecords: List<TestResultRecord> = emptyList()

    private val _userCustomQuestions = MutableStateFlow<List<CustomQuestionRecord>>(emptyList())
    val userCustomQuestions: StateFlow<List<CustomQuestionRecord>> = _userCustomQuestions.asStateFlow()

    private val _customPublishers = MutableStateFlow<List<YksPublisherInfo>>(repository.loadCustomPublishers())
    val customPublishers: StateFlow<List<YksPublisherInfo>> = _customPublishers.asStateFlow()

    private val _showPdfCropperStudio = MutableStateFlow(false)
    val showPdfCropperStudio: StateFlow<Boolean> = _showPdfCropperStudio.asStateFlow()

    private val _showAiAssistantModal = MutableStateFlow(false)
    val showAiAssistantModal: StateFlow<Boolean> = _showAiAssistantModal.asStateFlow()

    private val _aiFocusedQuestion = MutableStateFlow<YksQuestion?>(null)
    val aiFocusedQuestion: StateFlow<YksQuestion?> = _aiFocusedQuestion.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private var timerJob: Job? = null
    private var resultsListenerJob: Job? = null
    private var customQuestionsListenerJob: Job? = null

    private val allTopicsById: Map<String, YksTopic> by lazy {
        SoruLabCatalog.allSubjects.flatMap { it.topics }.associateBy { it.id }
    }

    private fun effectiveUserId(): String? {
        return _currentUser.value?.uid ?: _recognizedDeviceAccount.value?.uid
    }

    private val authStateListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _currentUser.value = user
        if (user != null) {
            val name = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")
                ?: "SoruLab Öğrencisi"
            val email = user.email.orEmpty()
            repository.saveRecognizedDeviceAccount(user.uid, name, email)
            _recognizedDeviceAccount.value = RecognizedDeviceAccount(
                uid = user.uid,
                displayName = name,
                email = email
            )
            startFirestoreListeners(user.uid)
        } else {
            resultsListenerJob?.cancel()
            customQuestionsListenerJob?.cancel()
            val recognized = repository.loadRecognizedDeviceAccount()
            _recognizedDeviceAccount.value = recognized
            if (recognized != null) {
                hydrateLocalSessionData(recognized.uid)
            } else {
                allCloudTestRecords = emptyList()
                _userTestResults.value = emptyList()
                _userCustomQuestions.value = emptyList()
            }
        }
    }

    private var shouldPurgeCloudDemoOnce = false

    init {
        repository.ensureDemoDataCleanedOnce()
        shouldPurgeCloudDemoOnce = repository.consumePendingCloudDemoPurge()
        auth?.addAuthStateListener(authStateListener)
        val current = auth?.currentUser
        if (current != null) {
            startFirestoreListeners(current.uid)
        } else {
            _recognizedDeviceAccount.value?.let { recognized ->
                hydrateLocalSessionData(recognized.uid)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        auth?.removeAuthStateListener(authStateListener)
        timerJob?.cancel()
        resultsListenerJob?.cancel()
        customQuestionsListenerJob?.cancel()
    }

    private fun isLegacyDemoQuestion(q: CustomQuestionRecord): Boolean {
        val hasValidCroppedImage = q.croppedImagePath.isNotBlank() &&
            runCatching { java.io.File(q.croppedImagePath).exists() }.getOrDefault(false)
        return !hasValidCroppedImage ||
            q.questionText.contains("a, b ve c tam sayıları için", ignoreCase = true) ||
            q.questionText.contains("sadece biri çift sayıdır", ignoreCase = true) ||
            q.id.startsWith("demo_")
    }

    private fun isLegacyDemoTestRecord(rec: TestResultRecord): Boolean {
        return rec.id.startsWith("initial_") ||
            rec.id.startsWith("demo_") ||
            rec.testId.startsWith("mock_") ||
            rec.testTitle.contains("Hazır Örnek", ignoreCase = true)
    }

    private fun hydrateLocalSessionData(uid: String) {
        _customPublishers.value = repository.loadCustomPublishers().filter {
            it.customLogoPath.isNotBlank() &&
                runCatching { java.io.File(it.customLogoPath).exists() }.getOrDefault(false)
        }
        val cachedCustomQuestions = repository.loadCachedCustomQuestions(uid)
            .filterNot { isLegacyDemoQuestion(it) }
        _userCustomQuestions.value = cachedCustomQuestions

        syncPdfPoolToPracticeTests(cachedCustomQuestions)
        val cachedEntries = repository.loadCachedGeneratedTestKeys(uid)
        if (cachedEntries.isNotEmpty() && cachedCustomQuestions.isNotEmpty()) {
            mergeGeneratedEntriesIntoState(uid, cachedEntries, persistCache = false)
        }
        val cachedResults = repository.loadCachedSolvedTestResults(uid)
            .filterNot { isLegacyDemoTestRecord(it) }
        _userTestResults.value = cachedResults
        val cachedStrokes = repository.loadCachedBoardStrokes(uid)
        if (cachedStrokes.isNotEmpty()) {
            _boardStrokes.value = cachedStrokes
        }
    }

    private fun startFirestoreListeners(uid: String) {
        resultsListenerJob?.cancel()
        customQuestionsListenerJob?.cancel()

        // 1. Instant 0ms local cache hydration for generated tests, custom questions & solved results
        hydrateLocalSessionData(uid)

        // 2. Real-time Firestore listener for both solved results & cloud-saved generated tests
        resultsListenerJob = viewModelScope.launch {
            repository.observeUserTestResults(uid)
                .catch { }
                .collect { records ->
                    if (shouldPurgeCloudDemoOnce) {
                        records.forEach { rec ->
                            if (rec.id.isNotBlank()) {
                                launch { runCatching { repository.deleteTestResult(rec.id) } }
                            }
                        }
                        allCloudTestRecords = emptyList()
                        _userTestResults.value = emptyList()
                        _generatedTopicTests.value = emptyMap()
                        repository.saveCachedSolvedTestResults(uid, emptyList())
                        return@collect
                    }

                    val demoRecords = records.filter { isLegacyDemoTestRecord(it) }
                    if (demoRecords.isNotEmpty()) {
                        demoRecords.forEach { demo ->
                            if (demo.id.isNotBlank()) {
                                launch { runCatching { repository.deleteTestResult(demo.id) } }
                            }
                        }
                    }
                    val cleanRecords = records.filterNot { isLegacyDemoTestRecord(it) }
                    allCloudTestRecords = cleanRecords
                    val solvedOnly = cleanRecords.filter { it.isSolvedAttempt }
                    _userTestResults.value = solvedOnly
                    repository.saveCachedSolvedTestResults(uid, solvedOnly)

                    val cloudGeneratedEntries = cleanRecords.mapNotNull { rec ->
                        parseGeneratedEntryFromRecord(rec)
                    }
                    if (cloudGeneratedEntries.isNotEmpty() && _userCustomQuestions.value.isNotEmpty()) {
                        mergeGeneratedEntriesIntoState(uid, cloudGeneratedEntries, persistCache = true)
                    }
                }
        }

        customQuestionsListenerJob = viewModelScope.launch {
            repository.observeUserCustomQuestions(uid)
                .catch { }
                .collect { cloudQuestions ->
                    if (shouldPurgeCloudDemoOnce) {
                        shouldPurgeCloudDemoOnce = false
                        cloudQuestions.forEach { cq ->
                            if (cq.id.isNotBlank()) {
                                launch { runCatching { repository.deleteCustomQuestion(cq.id) } }
                            }
                        }
                        _userCustomQuestions.value = emptyList()
                        repository.saveCachedCustomQuestions(uid, emptyList())
                        return@collect
                    }

                    val demoQuestions = cloudQuestions.filter { isLegacyDemoQuestion(it) }
                    if (demoQuestions.isNotEmpty()) {
                        demoQuestions.forEach { dq ->
                            if (dq.id.isNotBlank()) {
                                launch { runCatching { repository.deleteCustomQuestion(dq.id) } }
                            }
                        }
                    }
                    val cleanCloud = cloudQuestions.filterNot { isLegacyDemoQuestion(it) }
                    val localCached = repository.loadCachedCustomQuestions(uid)
                        .filterNot { isLegacyDemoQuestion(it) }
                    val merged = (cleanCloud + localCached).distinctBy { it.id }
                    _userCustomQuestions.value = merged
                    repository.saveCachedCustomQuestions(uid, merged)
                    syncPdfPoolToPracticeTests(merged)
                }
        }
    }

    /**
     * Automatically creates and syncs 345 All Star style homework/practice tests
     * (e.g., "3D Yayınları - Türev Ödev 1") under the corresponding Subject & Topic
     * whenever PDF questions exist in the pool.
     */
    private fun syncPdfPoolToPracticeTests(poolQuestions: List<CustomQuestionRecord>) {
        if (poolQuestions.isEmpty()) {
            _generatedTopicTests.value = emptyMap()
            return
        }

        val updatedMap = _generatedTopicTests.value.toMutableMap()
        // Remove previous auto_pdf entries before rebuilding
        val keysToClean = updatedMap.keys.toList()
        for (k in keysToClean) {
            val remaining = updatedMap[k].orEmpty().filterNot { it.id.startsWith("auto_pdf_") }
            if (remaining.isEmpty()) {
                updatedMap.remove(k)
            } else {
                updatedMap[k] = remaining
            }
        }

        // Group pool questions by matched topic (falling back to the first topic of the matching subject so ZERO questions are dropped)
        val groupedByTopic = poolQuestions.groupBy { cq ->
            val matchedSubject = SoruLabCatalog.allSubjects.firstOrNull {
                it.id.equals(cq.subjectId, ignoreCase = true) ||
                    (it.examType.name.equals(cq.examType, ignoreCase = true) && it.name.equals(cq.subjectName, ignoreCase = true))
            } ?: SoruLabCatalog.allSubjects.firstOrNull {
                it.name.equals(cq.subjectName, ignoreCase = true) ||
                    cq.subjectName.contains(it.name, ignoreCase = true)
            } ?: SoruLabCatalog.allSubjects.first()

            val matchedTopic = matchedSubject.topics.firstOrNull { t ->
                t.id.equals(cq.topicId, ignoreCase = true) ||
                    t.name.equals(cq.topicName, ignoreCase = true) ||
                    (cq.topicName.length >= 3 && t.name.contains(cq.topicName, ignoreCase = true)) ||
                    (t.name.length >= 3 && cq.topicName.contains(t.name, ignoreCase = true))
            } ?: SoruLabCatalog.allSubjects.flatMap { it.topics }.firstOrNull { t ->
                t.id.equals(cq.topicId, ignoreCase = true) ||
                    t.name.equals(cq.topicName, ignoreCase = true) ||
                    (cq.topicName.length >= 3 && t.name.contains(cq.topicName, ignoreCase = true)) ||
                    (t.name.length >= 3 && cq.topicName.contains(t.name, ignoreCase = true))
            } ?: matchedSubject.topics.first()

            matchedTopic.id
        }

        for ((topicId, topicQuestions) in groupedByTopic) {
            val topic = allTopicsById[topicId]
                ?: SoruLabCatalog.allSubjects.flatMap { it.topics }.firstOrNull {
                    it.id.equals(topicId, ignoreCase = true)
                }
                ?: SoruLabCatalog.allSubjects.first().topics.first()

            // Group within topic by publisherTag so each publisher's extracted PDF forms clean tests
            val byPublisher = topicQuestions
                .sortedBy { if (it.questionNumberInBook > 0) it.questionNumberInBook else Int.MAX_VALUE }
                .groupBy { it.publisherTag.trim().ifBlank { "3D Yayınları" } }

            val autoTestsForTopic = mutableListOf<YksTestItem>()

            for ((pubName, pubQuestions) in byPublisher) {
                val chunks = pubQuestions.chunked(12)
                chunks.forEachIndexed { chunkIdx, chunkRecords ->
                    val odevNo = chunkIdx + 1
                    val shortTitle = "$pubName - ${topic.name} Ödev $odevNo"
                    val bookTag = chunkRecords.firstOrNull { it.bookOrTestName.isNotBlank() }?.bookOrTestName.orEmpty()
                    val fullTitle = if (bookTag.isNotBlank() && !bookTag.equals(pubName, ignoreCase = true)) {
                        "$pubName • $bookTag - ${topic.name} Ödev $odevNo"
                    } else {
                        "$pubName - ${topic.name} Ödev $odevNo"
                    }
                    val safePubSlug = pubName.lowercase()
                        .replace(Regex("[^a-z0-9]"), "_")
                        .take(16)
                        .ifBlank { "yayin" }
                    val testId = "auto_pdf_${topic.id}_${safePubSlug}_odev_$odevNo"

                    val convertedQuestions = chunkRecords.mapIndexed { qIdx, custom ->
                        val diff = when (custom.difficulty.lowercase()) {
                            "kolay" -> DifficultyLevel.KOLAY
                            "zor" -> DifficultyLevel.ZOR
                            else -> DifficultyLevel.ORTA
                        }
                        val resolvedPub = SoruLabPublisherPresets.resolvePublisher(
                            custom.publisherTag,
                            _customPublishers.value
                        )
                        val logoUri = custom.publisherLogoUri.ifBlank { resolvedPub.customLogoPath }
                        val hasCroppedStemImage = custom.croppedImagePath.isNotBlank()

                        YksQuestion(
                            id = "custom_${custom.id.ifBlank { "${testId}_q${qIdx + 1}" }}",
                            number = qIdx + 1,
                            badgeTitle = shortTitle.uppercase(),
                            topicHeader = topic.name.uppercase(),
                            publisherStamp = pubName.uppercase(),
                            publisherName = pubName,
                            publisherLogoUri = logoUri,
                            croppedImagePath = custom.croppedImagePath,
                            answerKeyVerified = custom.answerKeyVerified,
                            stemTop = if (hasCroppedStemImage) "" else custom.questionText,
                            bulletsOrSteps = emptyList(),
                            stemBottom = if (hasCroppedStemImage) "" else "Buna göre doğru seçenek aşağıdakilerden hangisidir?",
                            options = mapOf(
                                "A" to "A",
                                "B" to "B",
                                "C" to "C",
                                "D" to "D",
                                "E" to "E"
                            ),
                            correctOption = custom.correctOption.takeIf { it in listOf("A", "B", "C", "D", "E") } ?: "C",
                            difficulty = diff,
                            visualType = QuestionVisualType.NONE,
                            stepByStepSolution = custom.cleanSolutionSummary.ifBlank {
                                "Kitap cevap anahtarına göre doğru yanıt ${custom.correctOption} seçeneğidir."
                            }
                        )
                    }

                    val avgWeight = convertedQuestions.map { it.difficulty.weight }.average().toFloat()
                        .coerceIn(0.18f, 0.92f)
                    val dominantDiff = when {
                        avgWeight < 0.36f -> DifficultyLevel.KOLAY
                        avgWeight < 0.72f -> DifficultyLevel.ORTA
                        else -> DifficultyLevel.ZOR
                    }
                    val mappedLevel = when (dominantDiff) {
                        DifficultyLevel.KOLAY -> TopicTestLevel.BASLANGIC
                        DifficultyLevel.ZOR -> TopicTestLevel.DERECE
                        DifficultyLevel.ORTA -> TopicTestLevel.OSYM
                    }

                    autoTestsForTopic.add(
                        YksTestItem(
                            id = testId,
                            topicId = topic.id,
                            title = shortTitle,
                            fullTitle = fullTitle,
                            difficulty = dominantDiff,
                            difficultyPosition = avgWeight,
                            defaultCompleted = false,
                            starRating = 0,
                            level = mappedLevel,
                            questions = convertedQuestions
                        )
                    )
                }
            }

            if (autoTestsForTopic.isNotEmpty()) {
                val autoKey = "${topic.id}_AUTO_PDF"
                updatedMap[autoKey] = autoTestsForTopic
            }
        }

        _generatedTopicTests.value = updatedMap
    }

    fun getTestsForTopicAndLevel(topic: YksTopic, level: TopicTestLevel): List<YksTestItem> {
        val map = _generatedTopicTests.value
        val levelSpecific = map["${topic.id}_${level.name}"].orEmpty()
        val autoPdfTests = map["${topic.id}_AUTO_PDF"].orEmpty()
        return (autoPdfTests + levelSpecific).distinctBy { it.id }
    }

    private fun parseGeneratedEntryFromRecord(rec: TestResultRecord): Triple<String, TopicTestLevel, Int>? {
        try {
            if (rec.answersMapJson.contains("\"level\"")) {
                val obj = JSONObject(rec.answersMapJson)
                val levelStr = obj.optString("level")
                val testNum = obj.optInt("testNumber", 0)
                val level = TopicTestLevel.entries.find { it.name.equals(levelStr, ignoreCase = true) }
                if (rec.topicId.isNotBlank() && level != null && testNum >= 1) {
                    return Triple(rec.topicId, level, testNum)
                }
            }
        } catch (_: Exception) {
        }
        for (level in TopicTestLevel.entries) {
            val token = "_${level.name.lowercase()}_test_"
            if (rec.testId.contains(token)) {
                val topicId = rec.testId.substringBefore(token).ifBlank { rec.topicId }
                val num = rec.testId.substringAfter(token).toIntOrNull() ?: 1
                if (topicId.isNotBlank()) {
                    return Triple(topicId, level, num)
                }
            }
        }
        return null
    }

    private fun mergeGeneratedEntriesIntoState(
        uid: String,
        entries: List<Triple<String, TopicTestLevel, Int>>,
        persistCache: Boolean
    ) {
        val updatedMap = _generatedTopicTests.value.toMutableMap()
        var changed = false
        for ((topicId, level, testNumber) in entries) {
            val topic = allTopicsById[topicId] ?: continue
            val key = "${topic.id}_${level.name}"
            val currentList = updatedMap[key].orEmpty()
            val expectedTestId = "${topic.id}_${level.name.lowercase()}_test_$testNumber"
            if (currentList.none { it.id == expectedTestId }) {
                val realQuestions = collectRealPoolQuestionsForTopicAndLevel(topic, level)
                if (realQuestions.isNotEmpty()) {
                    val shortTitle = "${level.displayTitle} Test-$testNumber"
                    val fullTitle = "${topic.name} ${level.displayTitle} Test-$testNumber"
                    val testItem = YksTestItem(
                        id = expectedTestId,
                        topicId = topic.id,
                        title = shortTitle,
                        fullTitle = fullTitle,
                        difficulty = level.defaultDifficulty,
                        difficultyPosition = level.difficultyPosition,
                        defaultCompleted = false,
                        starRating = 0,
                        level = level,
                        questions = realQuestions.mapIndexed { idx, q ->
                            q.copy(
                                id = "${expectedTestId}_q${idx + 1}",
                                number = idx + 1,
                                badgeTitle = shortTitle.uppercase(),
                                topicHeader = topic.name.uppercase()
                            )
                        }
                    )
                    updatedMap[key] = (currentList + testItem).sortedBy {
                        it.id.substringAfterLast("_test_").toIntOrNull() ?: 1
                    }
                    changed = true
                }
            }
        }
        if (changed) {
            _generatedTopicTests.value = updatedMap
            if (persistCache) {
                val allEntries = updatedMap.flatMap { (_, list) ->
                    list.map { item ->
                        val num = item.id.substringAfterLast("_test_").toIntOrNull() ?: 1
                        Triple(item.topicId, item.level, num)
                    }
                }
                repository.saveCachedGeneratedTestKeys(uid, allEntries)
            }
        }
    }

    /**
     * Collects ONLY real questions belonging to [topic] and [level] (0-hallucination policy):
     * 1. For IKIZ level: only questions the user actually answered wrong in solved tests of this topic.
     * 2. For standard levels: user's cropped/custom questions for this topic + real authored catalog questions
     *    strictly matching this topic (e.g. `temel_kavramlar`), calibrated to the requested 6-level wheel category.
     */
    private fun collectRealPoolQuestionsForTopicAndLevel(
        topic: YksTopic,
        level: TopicTestLevel
    ): List<YksQuestion> {
        if (level == TopicTestLevel.IKIZ) {
            val solvedInTopic = _userTestResults.value.filter { it.topicId == topic.id && it.isSolvedAttempt }
            if (solvedInTopic.isEmpty()) return emptyList()

            val baseTopicQuestions = buildRealBaseQuestionsForTopic(topic, null)
            if (baseTopicQuestions.isEmpty()) return emptyList()

            val wrongQuestionNumbers = mutableSetOf<Int>()
            for (record in solvedInTopic) {
                val answersMap = parseAnswersJson(record.answersMapJson).orEmpty()
                for (q in baseTopicQuestions) {
                    val chosen = answersMap[q.number]
                    if (chosen != null && chosen != q.correctOption) {
                        wrongQuestionNumbers.add(q.number)
                    }
                }
            }
            if (wrongQuestionNumbers.isEmpty()) return emptyList()

            return baseTopicQuestions.filter { it.number in wrongQuestionNumbers }
        }

        return buildRealBaseQuestionsForTopic(topic, level)
    }

    private fun buildRealBaseQuestionsForTopic(
        topic: YksTopic,
        levelFilter: TopicTestLevel?
    ): List<YksQuestion> {
        val topicPool = _userCustomQuestions.value.filter {
            it.topicId.equals(topic.id, ignoreCase = true) ||
                it.topicName.equals(topic.name, ignoreCase = true)
        }

        val filteredCustom = if (levelFilter == null) {
            topicPool
        } else {
            val exactLevelMatches = topicPool.filter {
                it.targetLevelName.equals(levelFilter.name, ignoreCase = true)
            }
            val difficultyMatches = topicPool.filter { cq ->
                when (levelFilter) {
                    TopicTestLevel.BASLANGIC -> cq.difficulty.equals("Kolay", true) || cq.difficulty.equals("Orta", true)
                    TopicTestLevel.PEKISTIRME -> true
                    TopicTestLevel.GELISIYORUM -> cq.difficulty.equals("Orta", true) || cq.difficulty.equals("Zor", true)
                    TopicTestLevel.OSYM -> cq.difficulty.equals("Orta", true) || cq.difficulty.equals("Zor", true)
                    TopicTestLevel.DERECE -> cq.difficulty.equals("Zor", true)
                    TopicTestLevel.IKIZ -> true
                }
            }
            (exactLevelMatches + difficultyMatches).distinctBy { it.id }.ifEmpty { topicPool }
        }

        val customMatches = filteredCustom.mapIndexed { index, custom ->
            val diff = when (custom.difficulty.lowercase()) {
                "kolay" -> DifficultyLevel.KOLAY
                "zor" -> DifficultyLevel.ZOR
                else -> DifficultyLevel.ORTA
            }
            val resolvedPub = SoruLabPublisherPresets.resolvePublisher(
                custom.publisherTag,
                _customPublishers.value
            )
            val logoUri = custom.publisherLogoUri.ifBlank { resolvedPub.customLogoPath }
            val hasCroppedStemImage = custom.croppedImagePath.isNotBlank()

            YksQuestion(
                id = "custom_${custom.id.ifBlank { index.toString() }}",
                number = index + 1,
                badgeTitle = custom.publisherTag.uppercase(),
                topicHeader = topic.name.uppercase(),
                publisherStamp = custom.publisherTag.uppercase(),
                publisherName = custom.publisherTag.ifBlank { resolvedPub.name },
                publisherLogoUri = logoUri,
                croppedImagePath = custom.croppedImagePath,
                answerKeyVerified = custom.answerKeyVerified,
                stemTop = if (hasCroppedStemImage) "" else custom.questionText,
                bulletsOrSteps = emptyList(),
                stemBottom = if (hasCroppedStemImage) "" else "Buna göre doğru seçenek aşağıdakilerden hangisidir?",
                options = mapOf(
                    "A" to "A",
                    "B" to "B",
                    "C" to "C",
                    "D" to "D",
                    "E" to "E"
                ),
                correctOption = custom.correctOption.takeIf { it in listOf("A", "B", "C", "D", "E") } ?: "D",
                difficulty = diff,
                visualType = QuestionVisualType.NONE,
                stepByStepSolution = custom.cleanSolutionSummary.ifBlank {
                    "Kitap cevap anahtarına göre doğru yanıt ${custom.correctOption} seçeneğidir."
                }
            )
        }

        return customMatches.mapIndexed { idx, q ->
            q.copy(number = idx + 1)
        }
    }

    /**
     * Strict 0-Hallucination check before generating a test.
     * If no real unseen questions exist in the pool for this topic & level, returns canGenerate = false.
     */
    fun checkPoolAvailability(topic: YksTopic, level: TopicTestLevel): PoolAvailabilityCheck {
        val key = "${topic.id}_${level.name}"
        val existingTestsForLevel = _generatedTopicTests.value[key].orEmpty()
        val realCandidates = collectRealPoolQuestionsForTopicAndLevel(topic, level)

        if (level == TopicTestLevel.IKIZ) {
            if (realCandidates.isEmpty()) {
                return PoolAvailabilityCheck(
                    canGenerate = false,
                    availableQuestionCount = 0,
                    reasonTitle = "Soru Bulunamadı",
                    reasonMessage = "'${topic.name}' konusunda henüz yanlış yaptığınız bir soru kaydı bulunamadı. İkiz test yalnızca çözdüğünüz testlerdeki gerçek yanlışlarınızdan oluşturulur."
                )
            }
            if (existingTestsForLevel.isNotEmpty() && existingTestsForLevel.last().questions.size >= realCandidates.size) {
                return PoolAvailabilityCheck(
                    canGenerate = false,
                    availableQuestionCount = 0,
                    reasonTitle = "Yeni İkiz Soru Bulunamadı",
                    reasonMessage = "'${topic.name}' konusundaki mevcut yanlış sorularınız için İkiz test zaten oluşturuldu. Yeni yanlış sorular tespit edildiğinde yeni İkiz test oluşturabilirsiniz."
                )
            }
            return PoolAvailabilityCheck(
                canGenerate = true,
                availableQuestionCount = realCandidates.size,
                reasonTitle = "İkiz Sorular Hazır",
                reasonMessage = "Bu konuda yanlış yaptığınız ${realCandidates.size} gerçek sorudan İkiz Test oluşturuluyor."
            )
        }

        val alreadyUsedQuestionCount = existingTestsForLevel.sumOf { it.questions.size }
        val remainingRealQuestions = realCandidates.drop(alreadyUsedQuestionCount)

        if (remainingRealQuestions.isEmpty()) {
            return PoolAvailabilityCheck(
                canGenerate = false,
                availableQuestionCount = 0,
                reasonTitle = "Soru Bulunamadı",
                reasonMessage = "'${topic.name}' (${level.displayTitle}) için soru havuzunda uygun soru bulunamadı. Sağ üstteki Profil -> Soru Havuzu bölümünden yayın PDF'i yükleyerek bu konuya otomatik soru ekleyebilirsiniz."
            )
        }

        return PoolAvailabilityCheck(
            canGenerate = true,
            availableQuestionCount = remainingRealQuestions.size,
            reasonTitle = "Soru Havuzu Uygun",
            reasonMessage = "Soru havuzunda '${topic.name}' (${level.displayTitle}) için ${remainingRealQuestions.size} gerçek soru bulundu."
        )
    }

    fun setAuthError(msg: String?) {
        _authError.value = msg
    }

    fun recognizeCurrentDevice(displayName: String = "YKS Öğrencisi", email: String = "ogrenci@sorulab.app") {
        val currentAuth = auth?.currentUser
        val uid = currentAuth?.uid ?: "device_yks_student_tr"
        val finalName = currentAuth?.displayName?.takeIf { it.isNotBlank() } ?: displayName
        val finalEmail = currentAuth?.email?.takeIf { it.isNotBlank() } ?: email
        repository.saveRecognizedDeviceAccount(uid, finalName, finalEmail)
        _recognizedDeviceAccount.value = RecognizedDeviceAccount(
            uid = uid,
            displayName = finalName,
            email = finalEmail
        )
        hydrateLocalSessionData(uid)
        if (currentAuth != null) {
            startFirestoreListeners(currentAuth.uid)
        }
    }

    fun signOut() {
        repository.clearRecognizedDeviceAccountOnSignOut()
        _recognizedDeviceAccount.value = null
        auth?.signOut()
    }

    fun selectTab(tab: MainNavTab) {
        _selectedTab.value = tab
        _selectedTopic.value = null
        _showPdfCropperStudio.value = false
    }

    fun selectExamType(examType: ExamType) {
        _selectedExamType.value = examType
    }

    fun openTopic(topic: YksTopic) {
        _selectedTopic.value = topic
    }

    fun closeTopic() {
        _selectedTopic.value = null
    }

    fun selectTestLevel(level: TopicTestLevel) {
        _selectedTestLevel.value = level
    }

    fun generateNewAiLevelTest(topic: YksTopic, level: TopicTestLevel): YksTestItem? {
        val availability = checkPoolAvailability(topic, level)
        if (!availability.canGenerate) {
            return null
        }

        val key = "${topic.id}_${level.name}"
        val existingList = _generatedTopicTests.value[key].orEmpty()
        val nextTestNumber = existingList.size + 1

        val allRealForTopic = collectRealPoolQuestionsForTopicAndLevel(topic, level)
        val questionsToUse = if (level == TopicTestLevel.IKIZ) {
            allRealForTopic
        } else {
            val usedCount = existingList.sumOf { it.questions.size }
            allRealForTopic.drop(usedCount).take(12)
        }

        if (questionsToUse.isEmpty()) return null

        val shortTitle = "${level.displayTitle} Test-$nextTestNumber"
        val fullTitle = "${topic.name} ${level.displayTitle} Test-$nextTestNumber"
        val expectedTestId = "${topic.id}_${level.name.lowercase()}_test_$nextTestNumber"

        val generatedTest = YksTestItem(
            id = expectedTestId,
            topicId = topic.id,
            title = shortTitle,
            fullTitle = fullTitle,
            difficulty = level.defaultDifficulty,
            difficultyPosition = level.difficultyPosition,
            defaultCompleted = false,
            starRating = 0,
            level = level,
            questions = questionsToUse.mapIndexed { idx, q ->
                q.copy(
                    id = "${expectedTestId}_q${idx + 1}",
                    number = idx + 1,
                    badgeTitle = shortTitle.uppercase(),
                    topicHeader = topic.name.uppercase()
                )
            }
        )

        val updatedMap = _generatedTopicTests.value + (key to (existingList + generatedTest))
        _generatedTopicTests.value = updatedMap

        val uid = effectiveUserId() ?: "device_yks_student_tr"
        val allEntries = updatedMap.flatMap { (_, list) ->
            list.map { item ->
                val num = item.id.substringAfterLast("_test_").toIntOrNull() ?: 1
                Triple(item.topicId, item.level, num)
            }
        }
        repository.saveCachedGeneratedTestKeys(uid, allEntries)

        val firebaseUid = _currentUser.value?.uid
        if (firebaseUid != null) {
            val subject = SoruLabCatalog.allSubjects.find { it.id == topic.subjectId }
            val examTypeStr = subject?.examType?.name ?: _selectedExamType.value.name
            val subjectName = subject?.name ?: "Matematik"
            val docId = "${firebaseUid.take(8)}_${generatedTest.id}".take(78)
            val existingCloud = allCloudTestRecords.firstOrNull { it.id == docId || it.testId == generatedTest.id }

            if (existingCloud == null) {
                viewModelScope.launch {
                    try {
                        val cloudRecord = TestResultRecord(
                            id = docId,
                            userId = firebaseUid,
                            examType = examTypeStr,
                            subjectId = topic.subjectId,
                            subjectName = subjectName,
                            topicId = topic.id,
                            topicName = topic.name,
                            testId = generatedTest.id,
                            testTitle = generatedTest.fullTitle,
                            correctCount = 0,
                            wrongCount = 0,
                            emptyCount = generatedTest.questions.size,
                            durationSeconds = 0,
                            answersMapJson = """{"_status":"GENERATED","level":"${level.name}","testNumber":$nextTestNumber}"""
                        )
                        repository.saveTestResult(cloudRecord)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        return generatedTest
    }

    fun onTestItemClicked(topic: YksTopic, testItem: YksTestItem) {
        _dialogTestItem.value = topic to testItem
    }

    fun dismissTestDialog() {
        _dialogTestItem.value = null
    }

    fun isTestCompleted(testItem: YksTestItem): Boolean {
        return _userTestResults.value.any { it.testId == testItem.id && it.isSolvedAttempt }
    }

    fun startSolvingSession(
        topic: YksTopic,
        testItem: YksTestItem,
        initialViewMode: BoardViewMode = BoardViewMode.FULL_PAGE,
        isReviewMode: Boolean = false,
        openOpticalFormFirst: Boolean = false,
        preloadedAnswers: Map<Int, String>? = null,
        initialQuestionIndex: Int = 0
    ) {
        _dialogTestItem.value = null
        val subject = SoruLabCatalog.allSubjects.find { it.id == topic.subjectId }
        val examType = subject?.examType ?: _selectedExamType.value
        val subjectName = subject?.name ?: "Matematik"

        val existingRecord = _userTestResults.value.firstOrNull { it.testId == testItem.id }
        val parsedAnswers = preloadedAnswers ?: if (isReviewMode) {
            parseAnswersJson(existingRecord?.answersMapJson).orEmpty()
        } else {
            emptyMap()
        }
        val initialSeconds = if (isReviewMode) (existingRecord?.durationSeconds ?: 0) else 0
        _elapsedSeconds.value = initialSeconds

        val safeQuestionIdx = initialQuestionIndex.coerceIn(0, (testItem.questions.size - 1).coerceAtLeast(0))

        _activeSession.value = ActiveSolvingSession(
            testItem = testItem,
            examType = examType,
            subjectId = topic.subjectId,
            subjectName = subjectName,
            topicId = topic.id,
            topicName = topic.name,
            viewMode = initialViewMode,
            isReviewMode = isReviewMode,
            isOpticalFormDrawerOpen = openOpticalFormFirst,
            isStatsDrawerOpen = isReviewMode,
            isQuestionMapDrawerOpen = false,
            currentQuestionIndex = safeQuestionIdx,
            selectedAnswers = parsedAnswers,
            elapsedSeconds = initialSeconds
        )

        timerJob?.cancel()
        if (!isReviewMode) {
            timerJob = viewModelScope.launch {
                while (true) {
                    delay(1000L)
                    val cur = _activeSession.value ?: break
                    if (!cur.isReviewMode) {
                        _elapsedSeconds.value += 1
                    }
                }
            }
        }
    }

    fun solveCustomQuestionInPractice(record: CustomQuestionRecord) {
        syncPdfPoolToPracticeTests(_userCustomQuestions.value)
        val matchedSubject = SoruLabCatalog.allSubjects.find { it.id == record.subjectId }
            ?: SoruLabCatalog.allSubjects.find { it.name.equals(record.subjectName, ignoreCase = true) }
            ?: SoruLabCatalog.allSubjects.first()
        val matchedTopic = matchedSubject.topics.find { it.id == record.topicId }
            ?: matchedSubject.topics.find { it.name.equals(record.topicName, ignoreCase = true) }
            ?: matchedSubject.topics.first()

        val allTopicTests = _generatedTopicTests.value.entries
            .filter { (key, _) -> key.startsWith("${matchedTopic.id}_") || key == matchedTopic.id }
            .flatMap { it.value }
            .ifEmpty { _generatedTopicTests.value.values.flatten() }
        val targetTest = allTopicTests.firstOrNull { test ->
            test.questions.any { q ->
                (record.id.isNotBlank() && q.id.contains(record.id)) ||
                    (record.croppedImagePath.isNotBlank() && q.croppedImagePath == record.croppedImagePath)
            }
        } ?: run {
            val resolvedPub = SoruLabPublisherPresets.resolvePublisher(record.publisherTag, _customPublishers.value)
            val diffLevel = when (record.difficulty) {
                "Kolay" -> DifficultyLevel.KOLAY
                "Zor" -> DifficultyLevel.ZOR
                else -> DifficultyLevel.ORTA
            }
            val hasCroppedImg = record.croppedImagePath.isNotBlank()
            val fallbackQuestion = YksQuestion(
                id = "single_${record.id}",
                number = 1,
                badgeTitle = "${resolvedPub.name} • Ödev 1",
                topicHeader = matchedTopic.name.uppercase(),
                publisherStamp = resolvedPub.name.uppercase(),
                publisherName = resolvedPub.name,
                publisherLogoUri = record.publisherLogoUri.ifBlank { resolvedPub.customLogoPath },
                croppedImagePath = record.croppedImagePath,
                answerKeyVerified = record.answerKeyVerified,
                stemTop = if (hasCroppedImg) "" else record.questionText,
                bulletsOrSteps = emptyList(),
                stemBottom = if (hasCroppedImg) "" else "Doğru seçeneği işaretleyiniz.",
                options = mapOf(
                    "A" to "A",
                    "B" to "B",
                    "C" to "C",
                    "D" to "D",
                    "E" to "E"
                ),
                correctOption = record.correctOption,
                difficulty = diffLevel,
                visualType = QuestionVisualType.NONE,
                stepByStepSolution = record.cleanSolutionSummary.ifBlank {
                    "Doğru cevap ${record.correctOption} seçeneğidir."
                }
            )
            YksTestItem(
                id = "single_test_${record.id}",
                topicId = matchedTopic.id,
                title = "${resolvedPub.shortCode} Ödev 1",
                fullTitle = "${resolvedPub.name} - ${matchedTopic.name} Ödev 1",
                difficulty = diffLevel,
                difficultyPosition = diffLevel.weight,
                defaultCompleted = false,
                starRating = 0,
                level = TopicTestLevel.OSYM,
                questions = listOf(fallbackQuestion)
            )
        }

        val questionIndex = targetTest.questions.indexOfFirst {
            it.id.contains(record.id) || (record.croppedImagePath.isNotBlank() && it.croppedImagePath == record.croppedImagePath)
        }.coerceAtLeast(0)

        _showPdfCropperStudio.value = false
        startSolvingSession(
            topic = matchedTopic,
            testItem = targetTest,
            initialViewMode = BoardViewMode.FULL_PAGE,
            isReviewMode = false,
            initialQuestionIndex = questionIndex
        )
    }

    fun solveBookQuestionsInPractice(bookTitle: String, records: List<CustomQuestionRecord>) {
        if (records.isEmpty()) return
        val firstRecord = records.first()
        val matchedSubject = SoruLabCatalog.allSubjects.find { it.id == firstRecord.subjectId }
            ?: SoruLabCatalog.allSubjects.find { it.name.equals(firstRecord.subjectName, ignoreCase = true) }
            ?: SoruLabCatalog.allSubjects.first()
        val matchedTopic = matchedSubject.topics.find { it.id == firstRecord.topicId }
            ?: matchedSubject.topics.find { it.name.equals(firstRecord.topicName, ignoreCase = true) }
            ?: matchedSubject.topics.first()

        val sortedRecords = records.sortedBy {
            if (it.questionNumberInBook > 0) it.questionNumberInBook else Int.MAX_VALUE
        }

        val testQuestions = sortedRecords.mapIndexed { idx, record ->
            val resolvedPub = SoruLabPublisherPresets.resolvePublisher(record.publisherTag, _customPublishers.value)
            val diffLevel = when (record.difficulty) {
                "Kolay" -> DifficultyLevel.KOLAY
                "Zor" -> DifficultyLevel.ZOR
                else -> DifficultyLevel.ORTA
            }
            val hasCroppedImg = record.croppedImagePath.isNotBlank()
            YksQuestion(
                id = "book_${record.id}",
                number = idx + 1,
                badgeTitle = "${resolvedPub.name} • ${idx + 1}. Soru",
                topicHeader = record.topicName.ifBlank { matchedTopic.name }.uppercase(),
                publisherStamp = resolvedPub.name.uppercase(),
                publisherName = resolvedPub.name,
                publisherLogoUri = record.publisherLogoUri.ifBlank { resolvedPub.customLogoPath },
                croppedImagePath = record.croppedImagePath,
                answerKeyVerified = record.answerKeyVerified,
                stemTop = if (hasCroppedImg) "" else record.questionText,
                bulletsOrSteps = emptyList(),
                stemBottom = if (hasCroppedImg) "" else "Doğru seçeneği işaretleyiniz.",
                options = mapOf(
                    "A" to "A",
                    "B" to "B",
                    "C" to "C",
                    "D" to "D",
                    "E" to "E"
                ),
                correctOption = record.correctOption,
                difficulty = diffLevel,
                visualType = QuestionVisualType.NONE,
                stepByStepSolution = record.cleanSolutionSummary.ifBlank {
                    "Doğru cevap ${record.correctOption} seçeneğidir."
                }
            )
        }

        val cleanTitle = bookTitle.ifBlank { firstRecord.publisherTag }
        val bookTest = YksTestItem(
            id = "book_test_${cleanTitle.lowercase().replace(" ", "_").take(28)}",
            topicId = matchedTopic.id,
            title = cleanTitle.take(28),
            fullTitle = cleanTitle,
            difficulty = DifficultyLevel.ORTA,
            difficultyPosition = DifficultyLevel.ORTA.weight,
            defaultCompleted = false,
            starRating = 0,
            level = TopicTestLevel.OSYM,
            questions = testQuestions
        )

        _showPdfCropperStudio.value = false
        startSolvingSession(
            topic = matchedTopic,
            testItem = bookTest,
            initialViewMode = BoardViewMode.FULL_PAGE,
            isReviewMode = false,
            initialQuestionIndex = 0
        )
    }

    fun startMockExam(mockExam: MockExamItem, reviewOnly: Boolean = false) {
        val mathTopic = SoruLabCatalog.allSubjects.first { it.examType == mockExam.examType }.topics.first()
        startSolvingSession(
            topic = mathTopic.copy(name = mockExam.publisher),
            testItem = mockExam.linkedTest,
            initialViewMode = BoardViewMode.FULL_PAGE,
            isReviewMode = reviewOnly
        )
    }

    fun openRecentResultForReview(record: TestResultRecord) {
        val matchingSubject = SoruLabCatalog.allSubjects.find { it.id == record.subjectId }
            ?: SoruLabCatalog.allSubjects.first { it.id == "tyt_mat" }
        val matchingTopic = matchingSubject.topics.find { it.id == record.topicId }
            ?: matchingSubject.topics.first()
        val matchingTest = _generatedTopicTests.value.values.flatten().find { it.id == record.testId }
            ?: matchingTopic.tests.find { it.id == record.testId }
            ?: SoruLabCatalog.buildDefaultPracticeTest(
                topicId = matchingTopic.id,
                topicName = matchingTopic.name,
                suffix = "review",
                shortTitle = record.testTitle.substringAfter("- ").ifBlank { "Test 1" },
                pos = 0.52f,
                completed = true
            ).copy(id = record.testId, fullTitle = record.testTitle)

        startSolvingSession(
            topic = matchingTopic,
            testItem = matchingTest,
            initialViewMode = BoardViewMode.FULL_PAGE,
            isReviewMode = true,
            preloadedAnswers = parseAnswersJson(record.answersMapJson).orEmpty()
        )
    }

    fun exitSolvingSession() {
        timerJob?.cancel()
        _activeSession.value = null
    }

    fun toggleBoardViewMode() {
        val cur = _activeSession.value ?: return
        val nextMode = if (cur.viewMode == BoardViewMode.FULL_PAGE) {
            BoardViewMode.SINGLE_QUESTION
        } else {
            BoardViewMode.FULL_PAGE
        }
        _activeSession.value = cur.copy(
            viewMode = nextMode,
            isOpticalFormDrawerOpen = false,
            isQuestionMapDrawerOpen = false
        )
    }

    fun selectQuestionIndex(index: Int) {
        val cur = _activeSession.value ?: return
        val safeIdx = index.coerceIn(0, cur.testItem.questions.lastIndex)
        _activeSession.value = cur.copy(currentQuestionIndex = safeIdx)
    }

    fun selectOptionForQuestion(questionNumber: Int, option: String) {
        val cur = _activeSession.value ?: return
        if (cur.isReviewMode) return
        val updated = cur.selectedAnswers.toMutableMap()
        if (updated[questionNumber] == option) {
            updated.remove(questionNumber)
        } else {
            updated[questionNumber] = option
        }
        _activeSession.value = cur.copy(selectedAnswers = updated)
    }

    fun toggleOpticalFormDrawer() {
        val cur = _activeSession.value ?: return
        _activeSession.value = cur.copy(
            isOpticalFormDrawerOpen = !cur.isOpticalFormDrawerOpen,
            isStatsDrawerOpen = false
        )
    }

    fun toggleStatsDrawer() {
        val cur = _activeSession.value ?: return
        _activeSession.value = cur.copy(
            isStatsDrawerOpen = !cur.isStatsDrawerOpen,
            isOpticalFormDrawerOpen = false
        )
    }

    fun toggleQuestionMapDrawer() {
        val cur = _activeSession.value ?: return
        _activeSession.value = cur.copy(
            isQuestionMapDrawerOpen = !cur.isQuestionMapDrawerOpen
        )
    }

    fun finishActiveTestAndReview() {
        val cur = _activeSession.value ?: return
        timerJob?.cancel()
        val finalDuration = _elapsedSeconds.value.coerceAtLeast(1)

        var correct = 0
        var wrong = 0
        var empty = 0
        val jsonObj = JSONObject()

        cur.testItem.questions.forEach { q ->
            val chosen = cur.selectedAnswers[q.number]
            if (chosen.isNullOrBlank()) {
                empty++
            } else {
                jsonObj.put(q.number.toString(), chosen)
                if (chosen == q.correctOption) {
                    correct++
                } else {
                    wrong++
                }
            }
        }

        val testNumber = cur.testItem.id.substringAfterLast("_test_").toIntOrNull()
        if (testNumber != null) {
            jsonObj.put("level", cur.testItem.level.name)
            jsonObj.put("testNumber", testNumber)
        }

        _activeSession.value = cur.copy(
            isReviewMode = true,
            isOpticalFormDrawerOpen = false,
            isStatsDrawerOpen = false,
            elapsedSeconds = finalDuration
        )

        val uid = effectiveUserId() ?: "device_yks_student_tr"
        val docId = "${uid.take(8)}_${cur.testItem.id}".take(78)
        val existingCloud = allCloudTestRecords.firstOrNull { it.id == docId || it.testId == cur.testItem.id }
        val record = TestResultRecord(
            id = existingCloud?.id ?: docId,
            userId = uid,
            examType = cur.examType.name,
            subjectId = cur.subjectId,
            subjectName = cur.subjectName,
            topicId = cur.topicId,
            topicName = cur.topicName,
            testId = cur.testItem.id,
            testTitle = cur.testItem.fullTitle,
            correctCount = correct,
            wrongCount = wrong,
            emptyCount = empty,
            durationSeconds = finalDuration,
            answersMapJson = jsonObj.toString(),
            createdAt = existingCloud?.createdAt
        )

        val updatedSolved = listOf(record) + _userTestResults.value.filterNot { it.testId == record.testId }
        _userTestResults.value = updatedSolved
        repository.saveCachedSolvedTestResults(uid, updatedSolved)

        val firebaseUid = _currentUser.value?.uid
        if (firebaseUid != null) {
            viewModelScope.launch {
                try {
                    repository.saveTestResult(record.copy(userId = firebaseUid))
                    showBanner("Test tamamlandı: ${correct}D • ${wrong}Y • ${empty}B")
                } catch (_: Exception) {
                    showBanner("Test tamamlandı: ${correct}D • ${wrong}Y • ${empty}B")
                }
            }
        } else {
            showBanner("Test tamamlandı: ${correct}D • ${wrong}Y • ${empty}B")
        }
    }

    fun navigateFromReviewToStats() {
        timerJob?.cancel()
        _activeSession.value = null
        _selectedTopic.value = null
        _selectedTab.value = MainNavTab.ISTATISTIKLERIM
    }

    fun selectDrawingTool(tool: DrawingTool) {
        _activeTool.value = tool
    }

    fun selectStrokeSize(index: Int) {
        _selectedStrokeSizeIndex.value = index.coerceIn(0, 2)
    }

    fun selectPenColor(colorHex: Long) {
        _selectedPenColorHex.value = colorHex
        if (_activeTool.value == DrawingTool.ERASER || _activeTool.value == DrawingTool.HAND_PAN) {
            _activeTool.value = DrawingTool.PEN
        }
    }

    fun currentCanvasKey(): String {
        val cur = _activeSession.value ?: return "default"
        return if (cur.viewMode == BoardViewMode.FULL_PAGE) {
            "full_page_${cur.testItem.id}"
        } else {
            val q = cur.testItem.questions.getOrNull(cur.currentQuestionIndex)
            "q_${q?.id ?: cur.currentQuestionIndex}"
        }
    }

    fun addStrokeToCurrentCanvas(points: List<Offset>) {
        if (points.size < 2) return
        val key = currentCanvasKey()
        val tool = _activeTool.value
        val currentList = _boardStrokes.value[key].orEmpty()

        if (tool == DrawingTool.ERASER) {
            val eraseRadius = 42f
            val remaining = currentList.filter { stroke ->
                stroke.points.none { pt ->
                    points.any { erasePt -> (pt - erasePt).getDistance() < eraseRadius }
                }
            }
            val updatedStrokes = _boardStrokes.value + (key to remaining)
            _boardStrokes.value = updatedStrokes
            repository.saveCachedBoardStrokes(effectiveUserId() ?: "device_yks_student_tr", updatedStrokes)
            return
        }

        val widthPx = when (_selectedStrokeSizeIndex.value) {
            0 -> 4.5f
            1 -> 8.0f
            else -> 13.0f
        }

        val finalPoints = if (tool == DrawingTool.SMART_SHAPE && points.size >= 3) {
            val first = points.first()
            val last = points.last()
            if ((first - last).getDistance() < 65f) {
                val minX = points.minOf { it.x }
                val maxX = points.maxOf { it.x }
                val minY = points.minOf { it.y }
                val maxY = points.maxOf { it.y }
                listOf(
                    Offset(minX, minY),
                    Offset(maxX, minY),
                    Offset(maxX, maxY),
                    Offset(minX, maxY),
                    Offset(minX, minY)
                )
            } else {
                listOf(first, last)
            }
        } else {
            points
        }

        val newStroke = DrawStroke(
            points = finalPoints,
            colorHex = _selectedPenColorHex.value,
            strokeWidthPx = widthPx,
            isSmartShape = tool == DrawingTool.SMART_SHAPE
        )
        val updatedStrokes = _boardStrokes.value + (key to (currentList + newStroke))
        _boardStrokes.value = updatedStrokes
        repository.saveCachedBoardStrokes(effectiveUserId() ?: "device_yks_student_tr", updatedStrokes)
    }

    fun clearCurrentCanvasStrokes() {
        val key = currentCanvasKey()
        val updatedStrokes = _boardStrokes.value - key
        _boardStrokes.value = updatedStrokes
        repository.saveCachedBoardStrokes(effectiveUserId() ?: "device_yks_student_tr", updatedStrokes)
    }

    fun openPdfCropperStudio() {
        _showPdfCropperStudio.value = true
    }

    fun closePdfCropperStudio() {
        _showPdfCropperStudio.value = false
    }

    fun addCustomPublisher(
        name: String,
        shortCode: String,
        customLogoPath: String
    ): YksPublisherInfo {
        val cleanName = name.trim().ifBlank { "Özel Yayın" }
        val cleanCode = shortCode.trim().uppercase().take(4).ifBlank {
            cleanName.take(3).uppercase()
        }
        val newPub = YksPublisherInfo(
            id = "custom_${System.currentTimeMillis()}",
            name = cleanName,
            shortCode = cleanCode,
            primaryColorHex = 0xFF1D4ED8,
            secondaryColorHex = 0xFF0F172A,
            customLogoPath = customLogoPath
        )
        val updated = listOf(newPub) + _customPublishers.value.filterNot {
            it.name.equals(cleanName, ignoreCase = true)
        }
        _customPublishers.value = updated
        repository.saveCustomPublishers(updated)
        showBanner("'$cleanName' yayını ve logosu sisteme eklendi!")
        return newPub
    }

    fun saveCroppedQuestionToCloud(
        examType: String,
        subjectName: String,
        topicName: String,
        difficulty: String,
        correctOption: String,
        questionText: String,
        publisherTag: String,
        aiSolutionSummary: String,
        cropAspectRatio: Double,
        targetLevel: String = "OSYM",
        croppedImagePath: String = "",
        publisherLogoUri: String = "",
        answerKeyVerified: Boolean = true,
        questionNumberInBook: Int = 0,
        bookOrTestName: String = "",
        bookCoverPath: String = ""
    ) {
        val uid = effectiveUserId() ?: "device_yks_student_tr"
        val matchedSubject = SoruLabCatalog.allSubjects.firstOrNull {
            it.examType.name.equals(examType, ignoreCase = true) &&
                it.name.equals(subjectName, ignoreCase = true)
        } ?: SoruLabCatalog.allSubjects.firstOrNull {
            it.name.equals(subjectName, ignoreCase = true)
        }
        val matchedTopic = matchedSubject?.topics?.firstOrNull {
            it.name.equals(topicName, ignoreCase = true) ||
                topicName.contains(it.name, ignoreCase = true) ||
                it.name.contains(topicName, ignoreCase = true)
        } ?: SoruLabCatalog.allSubjects.flatMap { it.topics }.firstOrNull {
            it.name.equals(topicName, ignoreCase = true) ||
                topicName.contains(it.name, ignoreCase = true)
        } ?: matchedSubject?.topics?.firstOrNull()

        val resolvedExamType = matchedSubject?.examType?.name ?: if (examType.equals("AYT", true)) "AYT" else "TYT"
        val subjectId = matchedSubject?.id ?: if (resolvedExamType == "AYT") "ayt_mat" else "tyt_mat"
        val resolvedSubjectName = matchedSubject?.name ?: subjectName.ifBlank { "Matematik" }
        val topicId = matchedTopic?.id ?: topicName.lowercase().replace(" ", "_").take(32).ifBlank { "temel_kavramlar" }
        val resolvedTopicName = matchedTopic?.name ?: topicName.ifBlank { "Temel Kavramlar" }

        val resolvedPublisher = SoruLabPublisherPresets.resolvePublisher(publisherTag, _customPublishers.value)
        val finalLogoUri = publisherLogoUri.ifBlank { resolvedPublisher.customLogoPath }

        val packedSolution = CustomQuestionRecord.packSolutionWithMeta(
            solutionText = aiSolutionSummary,
            targetLevel = targetLevel,
            croppedImagePath = croppedImagePath,
            publisherLogoUri = finalLogoUri,
            answerKeyVerified = answerKeyVerified,
            questionNumberInBook = questionNumberInBook,
            bookOrTestName = bookOrTestName,
            bookCoverPath = bookCoverPath
        )

        val record = CustomQuestionRecord(
            id = "cq_${System.currentTimeMillis()}_${(100..999).random()}",
            userId = uid,
            examType = resolvedExamType,
            subjectId = subjectId,
            subjectName = resolvedSubjectName,
            topicId = topicId,
            topicName = resolvedTopicName,
            difficulty = difficulty.takeIf { it in listOf("Kolay", "Orta", "Zor") } ?: "Orta",
            correctOption = correctOption.uppercase().takeIf { it in listOf("A", "B", "C", "D", "E") } ?: "D",
            questionText = questionText.ifBlank { "PDF'ten kırpılan YKS sorusu" }.take(1800),
            publisherTag = publisherTag.ifBlank { resolvedPublisher.name }.take(100),
            aiSolutionSummary = packedSolution,
            cropAspectRatio = cropAspectRatio.coerceIn(0.3, 4.5)
        )

        val updatedList = listOf(record) + _userCustomQuestions.value
        _userCustomQuestions.value = updatedList
        repository.saveCachedCustomQuestions(uid, updatedList)
        syncPdfPoolToPracticeTests(updatedList)

        val firebaseUid = _currentUser.value?.uid
        if (firebaseUid != null) {
            viewModelScope.launch {
                try {
                    repository.saveCustomQuestion(record.copy(userId = firebaseUid))
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Atomically saves all extracted questions from a PDF in a single batch update
     * so "KİTAPLIĞIM" and "Soru Pratiği" update immediately without lag or race conditions.
     */
    fun saveExtractedPdfQuestionsBatch(
        extractedItems: List<ExtractedPdfQuestionItem>,
        publisherName: String,
        publisherLogoUri: String,
        bookOrTestName: String,
        bookCoverPath: String
    ) {
        if (extractedItems.isEmpty()) return
        val uid = effectiveUserId() ?: "device_yks_student_tr"
        val resolvedPublisher = SoruLabPublisherPresets.resolvePublisher(publisherName, _customPublishers.value)
        val finalPubName = publisherName.ifBlank { resolvedPublisher.name }.take(100)
        val finalLogoUri = publisherLogoUri.ifBlank { resolvedPublisher.customLogoPath }

        val newRecords = extractedItems.mapIndexed { idx, item ->
            val matchedSubject = SoruLabCatalog.allSubjects.firstOrNull {
                it.examType.name.equals(item.examType, ignoreCase = true) &&
                    it.name.equals(item.subjectName, ignoreCase = true)
            } ?: SoruLabCatalog.allSubjects.firstOrNull {
                it.name.equals(item.subjectName, ignoreCase = true) ||
                    item.subjectName.contains(it.name, ignoreCase = true)
            } ?: SoruLabCatalog.allSubjects.first()

            val matchedTopic = matchedSubject.topics.firstOrNull {
                it.name.equals(item.topicName, ignoreCase = true) ||
                    item.topicName.contains(it.name, ignoreCase = true) ||
                    it.name.contains(item.topicName, ignoreCase = true)
            } ?: SoruLabCatalog.allSubjects.flatMap { it.topics }.firstOrNull {
                it.name.equals(item.topicName, ignoreCase = true) ||
                    item.topicName.contains(it.name, ignoreCase = true)
            } ?: matchedSubject.topics.first()

            val resolvedExamType = matchedSubject.examType.name
            val subjectId = matchedSubject.id
            val resolvedSubjectName = matchedSubject.name
            val topicId = matchedTopic.id
            val resolvedTopicName = matchedTopic.name

            val effectiveCover = bookCoverPath.ifBlank { item.bookCoverPath }
            val packedSolution = CustomQuestionRecord.packSolutionWithMeta(
                solutionText = item.solutionSummary,
                targetLevel = item.targetLevel,
                croppedImagePath = item.croppedImagePath,
                publisherLogoUri = finalLogoUri,
                answerKeyVerified = item.answerKeyMatched,
                questionNumberInBook = item.questionNumber,
                bookOrTestName = bookOrTestName,
                bookCoverPath = effectiveCover
            )

            CustomQuestionRecord(
                id = "cq_${System.currentTimeMillis()}_${idx}_${(100..999).random()}",
                userId = uid,
                examType = resolvedExamType,
                subjectId = subjectId,
                subjectName = resolvedSubjectName,
                topicId = topicId,
                topicName = resolvedTopicName,
                difficulty = item.difficulty.takeIf { it in listOf("Kolay", "Orta", "Zor") } ?: "Orta",
                correctOption = item.correctOption.uppercase().takeIf { it in listOf("A", "B", "C", "D", "E") } ?: "C",
                questionText = item.questionText.ifBlank { "$resolvedSubjectName • $resolvedTopicName" }.take(1800),
                publisherTag = finalPubName,
                aiSolutionSummary = packedSolution,
                cropAspectRatio = item.cropAspectRatio.coerceIn(0.3, 4.5)
            )
        }

        val updatedList = newRecords + _userCustomQuestions.value
        _userCustomQuestions.value = updatedList
        repository.saveCachedCustomQuestions(uid, updatedList)
        syncPdfPoolToPracticeTests(updatedList)
        showBanner("'$bookOrTestName' kaynağından ${newRecords.size} soru Kitaplığınıza ve Soru Pratiğine eklendi!")

        val firebaseUid = _currentUser.value?.uid
        if (firebaseUid != null) {
            viewModelScope.launch {
                newRecords.forEach { rec ->
                    runCatching { repository.saveCustomQuestion(rec.copy(userId = firebaseUid)) }
                }
            }
        }
    }

    fun addQuestionToKumbara(question: YksQuestion) {
        val uid = effectiveUserId() ?: "device_yks_student_tr"
        val currentQuestions = _userCustomQuestions.value
        val existingIdx = currentQuestions.indexOfFirst { cq ->
            question.id.contains(cq.id) ||
                (cq.croppedImagePath.isNotBlank() && cq.croppedImagePath == question.croppedImagePath)
        }
        if (existingIdx >= 0) {
            val target = currentQuestions[existingIdx]
            val updatedPacked = CustomQuestionRecord.packSolutionWithMeta(
                solutionText = target.cleanSolutionSummary,
                targetLevel = target.targetLevelName,
                croppedImagePath = target.croppedImagePath,
                publisherLogoUri = target.publisherLogoUri,
                answerKeyVerified = target.answerKeyVerified,
                questionNumberInBook = target.questionNumberInBook,
                bookOrTestName = target.bookOrTestName,
                bookCoverPath = target.bookCoverPath,
                isInKumbara = true
            )
            val updatedRecord = target.copy(aiSolutionSummary = updatedPacked)
            val mutable = currentQuestions.toMutableList()
            mutable[existingIdx] = updatedRecord
            _userCustomQuestions.value = mutable
            repository.saveCachedCustomQuestions(uid, mutable)
            val firebaseUid = _currentUser.value?.uid
            if (firebaseUid != null) {
                viewModelScope.launch {
                    runCatching { repository.saveCustomQuestion(updatedRecord.copy(userId = firebaseUid)) }
                }
            }
        }
        showBanner("Soru Kumbarana eklendi! (${question.topicHeader} • ${question.number}. Soru)")
    }

    fun deleteLibraryBookQuestions(questionIds: List<String>) {
        if (questionIds.isEmpty()) return
        val idSet = questionIds.toSet()
        val uid = effectiveUserId() ?: "device_yks_student_tr"
        val updated = _userCustomQuestions.value.filterNot { it.id in idSet }
        _userCustomQuestions.value = updated
        repository.saveCachedCustomQuestions(uid, updated)
        syncPdfPoolToPracticeTests(updated)
        viewModelScope.launch {
            questionIds.forEach { qid ->
                runCatching { repository.deleteCustomQuestion(qid) }
            }
            showBanner("Kaynak kitap ve soruları Kitaplığınızdan kaldırıldı.")
        }
    }

    fun deleteCustomQuestion(questionId: String) {
        val uid = effectiveUserId() ?: "device_yks_student_tr"
        val updated = _userCustomQuestions.value.filterNot { it.id == questionId }
        _userCustomQuestions.value = updated
        repository.saveCachedCustomQuestions(uid, updated)
        syncPdfPoolToPracticeTests(updated)
        viewModelScope.launch {
            try {
                repository.deleteCustomQuestion(questionId)
                showBanner("Soru havuzdan silindi.")
            } catch (_: Exception) {
            }
        }
    }

    fun clearAllUserData() {
        val uid = effectiveUserId() ?: "device_yks_student_tr"
        val oldResults = allCloudTestRecords.toList()
        val oldCustomQuestions = _userCustomQuestions.value.toList()

        allCloudTestRecords = emptyList()
        _generatedTopicTests.value = emptyMap()
        _userTestResults.value = emptyList()
        _userCustomQuestions.value = emptyList()
        _boardStrokes.value = emptyMap()
        repository.clearAllLocalUserData(uid)

        viewModelScope.launch {
            oldResults.forEach { rec ->
                if (rec.id.isNotBlank()) {
                    runCatching { repository.deleteTestResult(rec.id) }
                }
            }
            oldCustomQuestions.forEach { q ->
                if (q.id.isNotBlank()) {
                    runCatching { repository.deleteCustomQuestion(q.id) }
                }
            }
        }
        showBanner("Tüm demo ve geçmiş test/havuz verileri temizlendi!")
    }

    fun openAiAssistant(question: YksQuestion? = null) {
        _aiFocusedQuestion.value = question
        _showAiAssistantModal.value = true
    }

    fun closeAiAssistant() {
        _showAiAssistantModal.value = false
    }

    fun showBanner(message: String) {
        _statusBannerMessage.value = message
        viewModelScope.launch {
            delay(3500L)
            if (_statusBannerMessage.value == message) {
                _statusBannerMessage.value = null
            }
        }
    }

    private fun parseAnswersJson(jsonStr: String?): Map<Int, String>? {
        if (jsonStr.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(jsonStr)
            val map = mutableMapOf<Int, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val qNum = k.toIntOrNull()
                val opt = obj.optString(k)
                if (qNum != null && opt.isNotBlank()) {
                    map[qNum] = opt
                }
            }
            map.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}
