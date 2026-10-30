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
import com.example.data.QuestionSourceFilter
import com.example.data.QuestionVisualType
import com.example.data.RecognizedDeviceAccount
import com.example.data.SoruLabCatalog
import com.example.data.SoruLabPublisherPresets
import com.example.data.SoruLabRepository
import com.example.data.TestResultRecord
import com.example.data.TestSnapshotCodec
import com.example.data.TopicProgressSummary
import com.example.data.TopicTestLevel
import com.example.data.YksPublisherInfo
import com.example.data.YksQuestion
import com.example.data.YksTestItem
import com.example.data.YksTopic
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

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
    HIGHLIGHTER,
    SMART_SHAPE,
    HAND_PAN,
    ERASER
}

@Immutable
data class PoolAvailabilityCheck(
    val canGenerate: Boolean,
    val availableQuestionCount: Int,
    val reasonTitle: String,
    val reasonMessage: String,
    val countsByDifficulty: Map<DifficultyLevel, Int> = emptyMap(),
    val countsBySource: Map<QuestionSourceFilter, Int> = emptyMap(),
    val matchingQuestions: List<YksQuestion> = emptyList(),
    val onGenerateCustomTest: ((Set<DifficultyLevel>, QuestionSourceFilter, Int) -> YksTestItem?)? = null,
    val questionCountForSelection: ((Set<DifficultyLevel>, QuestionSourceFilter) -> Int)? = null
)

@Immutable
data class DrawStroke(
    val points: List<Offset>,
    val colorHex: Long,
    val strokeWidthPx: Float,
    val isSmartShape: Boolean = false,
    val isEraser: Boolean = false,
    val isHighlighter: Boolean = false
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

    private val _deletedTestIds = MutableStateFlow<Set<String>>(emptySet())
    val deletedTestIds: StateFlow<Set<String>> = _deletedTestIds.asStateFlow()

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
    private var poolSyncJob: Job? = null
    private var cacheSaveJob: Job? = null

    private val allTopicsById: Map<String, YksTopic> by lazy {
        SoruLabCatalog.topicByIdMap
    }

    private fun saveCustomQuestionsCacheAsync(uid: String, list: List<CustomQuestionRecord>) {
        cacheSaveJob?.cancel()
        cacheSaveJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.saveCachedCustomQuestions(uid, list)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showBanner("Sorular cihazdaki yerel depoya yazılamadı. Depolama alanını kontrol edip yeniden deneyin.")
                }
            }
        }
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
                resetUserScopedData()
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
        val hasValidCroppedImage = q.croppedImagePath.isNotBlank()
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
        viewModelScope.launch(Dispatchers.IO) {
            val delIds = repository.loadCachedDeletedTestIds(uid)
            val customPubs = repository.loadCustomPublishers().filter {
                it.customLogoPath.isNotBlank() &&
                    runCatching { java.io.File(it.customLogoPath).exists() }.getOrDefault(false)
            }
            val cachedCustomQuestions = repository.loadCachedCustomQuestions(uid)
                .filterNot { isLegacyDemoQuestion(it) }
            val cachedEntries = repository.loadCachedGeneratedTestKeys(uid)
            val cachedSnapshots = repository.loadCachedGeneratedTestSnapshots(uid)
            val cachedResults = repository.loadCachedSolvedTestResults(uid)
                .filterNot { isLegacyDemoTestRecord(it) }
            val cachedStrokes = repository.loadCachedBoardStrokes(uid)

            withContext(Dispatchers.Main) {
                _deletedTestIds.value = delIds
                _customPublishers.value = customPubs
                if (_userCustomQuestions.value.isEmpty() || cachedCustomQuestions.size >= _userCustomQuestions.value.size) {
                    _userCustomQuestions.value = cachedCustomQuestions
                }
                if (cachedResults.isNotEmpty() && _userTestResults.value.isEmpty()) {
                    _userTestResults.value = cachedResults
                }
                if (cachedStrokes.isNotEmpty() && _boardStrokes.value.isEmpty()) {
                    _boardStrokes.value = cachedStrokes
                }
                syncPdfPoolToPracticeTests(_userCustomQuestions.value)
                if (cachedEntries.isNotEmpty()) {
                    mergeGeneratedEntriesIntoState(
                        uid,
                        cachedEntries,
                        persistCache = false,
                        snapshotJsonByTestId = cachedSnapshots
                    )
                }
            }
        }
    }

    private fun startFirestoreListeners(uid: String) {
        resultsListenerJob?.cancel()
        customQuestionsListenerJob?.cancel()
        resetUserScopedData()

        // 1. Fast non-blocking background hydration for generated tests, custom questions & solved results
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
                        launch(Dispatchers.IO) { repository.saveCachedSolvedTestResults(uid, emptyList()) }
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
                    launch(Dispatchers.IO) { repository.saveCachedSolvedTestResults(uid, solvedOnly) }
                    refreshTopicProgressSummaries()

                    val cloudGeneratedEntries = cleanRecords.mapNotNull { rec ->
                        parseGeneratedEntryFromRecord(rec)
                    }
                    if (cloudGeneratedEntries.isNotEmpty()) {
                        mergeGeneratedEntriesIntoState(
                            uid,
                            cloudGeneratedEntries,
                            persistCache = true,
                            snapshotJsonByTestId = cleanRecords
                                .filter { it.testSnapshotJson.isNotBlank() }
                                .associate { it.testId to it.testSnapshotJson }
                        )
                    }
                }
        }

        customQuestionsListenerJob = viewModelScope.launch {
            repository.observeUserCustomQuestions(uid)
                .catch { }
                .collect { cloudQuestions ->
                    if (shouldPurgeCloudDemoOnce) {
                        shouldPurgeCloudDemoOnce = false
                        val idsToDelete = cloudQuestions.mapNotNull { it.id.takeIf { id -> id.isNotBlank() } }
                        if (idsToDelete.isNotEmpty()) {
                            launch(Dispatchers.IO) { repository.deleteCustomQuestionsBatch(idsToDelete) }
                        }
                        _userCustomQuestions.value = emptyList()
                        saveCustomQuestionsCacheAsync(uid, emptyList())
                        return@collect
                    }

                    val (cleanCloud, merged) = withContext(Dispatchers.Default) {
                        val demoIds = cloudQuestions.filter { isLegacyDemoQuestion(it) }.mapNotNull { it.id.takeIf { id -> id.isNotBlank() } }
                        if (demoIds.isNotEmpty()) {
                            launch(Dispatchers.IO) { repository.deleteCustomQuestionsBatch(demoIds) }
                        }
                        val validCloud = cloudQuestions.filterNot { isLegacyDemoQuestion(it) }
                        val currentInMemory = _userCustomQuestions.value
                        val baseLocal = if (currentInMemory.isNotEmpty()) {
                            currentInMemory
                        } else {
                            withContext(Dispatchers.IO) {
                                repository.loadCachedCustomQuestions(uid).filterNot { isLegacyDemoQuestion(it) }
                            }
                        }
                        val combined = ArrayList<CustomQuestionRecord>(validCloud.size + baseLocal.size)
                        val seenIds = HashSet<String>((validCloud.size + baseLocal.size) * 2)
                        for (q in validCloud) {
                            if (q.id.isBlank() || seenIds.add(q.id)) combined.add(q)
                        }
                        for (q in baseLocal) {
                            if (q.id.isBlank() || seenIds.add(q.id)) combined.add(q)
                        }
                        validCloud to combined
                    }

                    if (merged.size != _userCustomQuestions.value.size || cleanCloud.isNotEmpty()) {
                        _userCustomQuestions.value = merged
                        saveCustomQuestionsCacheAsync(uid, merged)
                        syncPdfPoolToPracticeTests(merged)
                    }
                }
        }
    }

    private fun resetUserScopedData() {
        allCloudTestRecords = emptyList()
        _userTestResults.value = emptyList()
        _userCustomQuestions.value = emptyList()
        _generatedTopicTests.value = emptyMap()
        _deletedTestIds.value = emptySet()
        _boardStrokes.value = emptyMap()
        _dialogTestItem.value = null
        _activeSession.value = null
    }

    /**
     * Automatically creates and syncs 345 All Star style homework/practice tests
     * (e.g., "3D Yayınları - Türev Ödev 1") under the corresponding Subject & Topic
     * whenever PDF questions exist in the pool.
     * Runs 100% off the UI thread on [Dispatchers.Default] with O(1) indexed topic lookup
     * so even 10,000+ questions across 50 books sync with zero frame drops.
     */
    /**
     * Updates topic question pool counts when PDF questions are added or loaded,
     * WITHOUT pre-creating automatic tests in Soru Pratiği before the user clicks "Test Oluştur".
     */
    private fun syncPdfPoolToPracticeTests(poolQuestions: List<CustomQuestionRecord>) {
        poolSyncJob?.cancel()
        if (poolQuestions.isEmpty()) {
            refreshTopicProgressSummaries()
            return
        }

        poolSyncJob = viewModelScope.launch(Dispatchers.Default) {
            val baseMap = _generatedTopicTests.value
            val updatedMap = HashMap<String, List<YksTestItem>>(baseMap.size)
            for ((k, v) in baseMap) {
                if (!k.endsWith("_AUTO_PDF")) {
                    val remaining = v.filterNot { it.id.startsWith("auto_pdf_") }
                    if (remaining.isNotEmpty()) {
                        updatedMap[k] = remaining
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (_generatedTopicTests.value != updatedMap) {
                    _generatedTopicTests.value = updatedMap
                }
                refreshTopicProgressSummaries()
            }
        }
    }

    private fun refreshTopicProgressSummaries() {
        viewModelScope.launch(Dispatchers.Default) {
            val pool = _userCustomQuestions.value
            val solvedResults = _userTestResults.value.filter { it.isSolvedAttempt }
            val genMap = _generatedTopicTests.value
            val deleted = _deletedTestIds.value

            // Pre-index counts by topicId & normalized topicName in O(N) instead of O(Topics * Questions)
            val poolCountByTopicId = HashMap<String, Int>(128)
            for (cq in pool) {
                val resolvedId = SoruLabCatalog.resolveTopicFast(cq.topicId, cq.topicName)?.id ?: cq.topicId
                if (resolvedId.isNotBlank()) {
                    poolCountByTopicId[resolvedId] = (poolCountByTopicId[resolvedId] ?: 0) + 1
                }
            }

            val solvedByTopicId = HashMap<String, MutableList<TestResultRecord>>(64)
            for (res in solvedResults) {
                val resolvedId = SoruLabCatalog.resolveTopicFast(res.topicId, res.topicName)?.id ?: res.topicId
                if (resolvedId.isNotBlank()) {
                    solvedByTopicId.getOrPut(resolvedId) { ArrayList() }.add(res)
                }
            }

            val newSummaries = HashMap<String, TopicProgressSummary>(160)
            for (subject in SoruLabCatalog.allSubjects) {
                for (topic in subject.topics) {
                    val topicPoolCount = poolCountByTopicId[topic.id] ?: 0
                    val topicTests = genMap.entries
                        .filter { (k, _) -> k.startsWith("${topic.id}_") || k == topic.id }
                        .flatMap { it.value }
                        .filter { it.id !in deleted }
                        .distinctBy { it.id }

                    val solvedTopicResults = solvedByTopicId[topic.id].orEmpty()
                    val solvedQuestionsCount = solvedTopicResults.sumOf { it.correctCount + it.wrongCount + it.ungradedCount }
                        .coerceAtMost(topicPoolCount.coerceAtLeast(solvedTopicResults.sumOf { it.totalQuestions }))
                    val totalQuestionsCount = maxOf(
                        topicPoolCount,
                        topicTests.sumOf { it.questions.size },
                        solvedQuestionsCount
                    )
                    val completedTestsCount = solvedTopicResults.map { it.testId }.distinct().size

                    newSummaries[topic.id] = TopicProgressSummary(
                        solvedQuestions = solvedQuestionsCount,
                        totalQuestions = totalQuestionsCount,
                        completedTests = completedTestsCount,
                        totalTests = maxOf(topicTests.size, completedTestsCount)
                    )
                }
            }

            withContext(Dispatchers.Main) {
                for ((tid, summary) in newSummaries) {
                    if (SoruLabCatalog.topicProgressState[tid] != summary) {
                        SoruLabCatalog.topicProgressState[tid] = summary
                    }
                }
            }
        }
    }

    fun getTestsForTopicAndLevel(topic: YksTopic, level: TopicTestLevel): List<YksTestItem> {
        val map = _generatedTopicTests.value
        val levelSpecific = map["${topic.id}_${level.name}"].orEmpty()
        val deleted = _deletedTestIds.value
        return levelSpecific
            .filter { it.id !in deleted }
            .distinctBy { it.id }
    }

    fun deleteTopicTest(topic: YksTopic, test: YksTestItem) {
        val updatedDeleted = _deletedTestIds.value + test.id
        _deletedTestIds.value = updatedDeleted
        val currentMap = _generatedTopicTests.value.toMutableMap()
        val key = "${topic.id}_${test.level.name}"
        if (currentMap.containsKey(key)) {
            currentMap[key] = currentMap[key].orEmpty().filter { it.id != test.id }
        }
        val autoKey = "${topic.id}_AUTO_PDF"
        if (currentMap.containsKey(autoKey)) {
            currentMap[autoKey] = currentMap[autoKey].orEmpty().filter { it.id != test.id }
        }
        _generatedTopicTests.value = currentMap

        val uid = effectiveUserId() ?: "device_yks_student_tr"
        repository.saveCachedDeletedTestIds(uid, updatedDeleted)
        repository.removeCachedGeneratedTestSnapshot(uid, test.id)
        val allEntries = currentMap.flatMap { (mapKey, list) ->
            if (mapKey.endsWith("_AUTO_PDF")) {
                emptyList()
            } else {
                list.map { item ->
                    val num = item.id.substringAfterLast("_test_").toIntOrNull() ?: 1
                    Triple(item.topicId, item.level, num)
                }
            }
        }
        repository.saveCachedGeneratedTestKeys(uid, allEntries)

        val cloudMatches = allCloudTestRecords.filter { it.testId == test.id && !it.isSolvedAttempt }
        if (cloudMatches.isNotEmpty()) {
            viewModelScope.launch {
                cloudMatches.forEach { rec ->
                    if (rec.id.isNotBlank()) {
                        runCatching { repository.deleteTestResult(rec.id) }
                    }
                }
            }
        }
        showBanner("Test başarıyla silindi.")
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
        persistCache: Boolean,
        snapshotJsonByTestId: Map<String, String> = emptyMap()
    ) {
        val updatedMap = _generatedTopicTests.value.toMutableMap()
        val deleted = _deletedTestIds.value
        val cachedSnapshots = repository.loadCachedGeneratedTestSnapshots(uid)
        var changed = false
        for ((topicId, level, testNumber) in entries) {
            val topic = allTopicsById[topicId] ?: continue
            val key = "${topic.id}_${level.name}"
            val currentList = updatedMap[key].orEmpty()
            val expectedTestId = "${topic.id}_${level.name.lowercase()}_test_$testNumber"
            if (expectedTestId !in deleted && currentList.none { it.id == expectedTestId }) {
                val snapshot = TestSnapshotCodec.decode(
                    snapshotJsonByTestId[expectedTestId] ?: cachedSnapshots[expectedTestId].orEmpty()
                )?.takeIf { it.id == expectedTestId }
                val realQuestions = if (snapshot == null) {
                    collectRealPoolQuestionsForTopicAndLevel(topic, level)
                } else {
                    emptyList()
                }
                if (snapshot != null || realQuestions.isNotEmpty()) {
                    val shortTitle = "${topic.name} - Test $testNumber"
                    val fullTitle = "${topic.name} - Test $testNumber"
                    val generatedFromPool = YksTestItem(
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
                                badgeTitle = fullTitle.uppercase(),
                                topicHeader = topic.name.uppercase()
                            )
                        }
                    )
                    val testItem = snapshot ?: generatedFromPool
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
                val allEntries = updatedMap.flatMap { (mapKey, list) ->
                    if (mapKey.endsWith("_AUTO_PDF")) {
                        emptyList()
                    } else {
                        list.map { item ->
                            val num = item.id.substringAfterLast("_test_").toIntOrNull() ?: 1
                            Triple(item.topicId, item.level, num)
                        }
                    }
                }
                repository.saveCachedGeneratedTestKeys(uid, allEntries)
            }
        }
    }

    /**
     * Collects ONLY real questions belonging to [topic] and [level] (0-hallucination policy):
     * 1. For IKIZ level: only questions the user actually answered wrong or left blank in solved tests of this topic.
     * 2. For standard levels: user's cropped/custom questions for this topic calibrated to the requested 6-level wheel category.
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

            val allKnownTests = _generatedTopicTests.value.values.flatten()
            val collectedTwinQuestions = mutableListOf<YksQuestion>()
            val seenIdentities = mutableSetOf<String>()

            for (record in solvedInTopic) {
                val answersMap = parseAnswersJson(record.answersMapJson).orEmpty()
                val sourceQuestions = allKnownTests.firstOrNull { it.id == record.testId }?.questions
                    ?: baseTopicQuestions

                for (q in sourceQuestions) {
                    val chosen = answersMap[q.number]
                    val isWrongOrBlank = chosen.isNullOrBlank() || chosen != q.correctOption
                    if (isWrongOrBlank) {
                        val identityKey = q.croppedImagePath.ifBlank { "${q.stemTop}|${q.number}" }
                        if (seenIdentities.add(identityKey)) {
                            collectedTwinQuestions.add(q)
                        }
                    }
                }
            }
            return collectedTwinQuestions.mapIndexed { idx, q ->
                q.copy(number = idx + 1)
            }
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
                val diff5 = cq.difficultyLevel5
                when (levelFilter) {
                    TopicTestLevel.BASLANGIC -> diff5 == DifficultyLevel.KOLAY || diff5 == DifficultyLevel.ORTA
                    TopicTestLevel.PEKISTIRME -> diff5 == DifficultyLevel.KOLAY || diff5 == DifficultyLevel.ORTA || diff5 == DifficultyLevel.OSYM
                    TopicTestLevel.GELISIYORUM -> diff5 == DifficultyLevel.ORTA || diff5 == DifficultyLevel.OSYM || diff5 == DifficultyLevel.ZOR
                    TopicTestLevel.OSYM -> diff5 == DifficultyLevel.OSYM || diff5 == DifficultyLevel.ORTA || diff5 == DifficultyLevel.ZOR
                    TopicTestLevel.DERECE -> diff5 == DifficultyLevel.ZOR || diff5 == DifficultyLevel.COK_ZOR || diff5 == DifficultyLevel.OSYM
                    TopicTestLevel.IKIZ -> true
                }
            }
            (exactLevelMatches + difficultyMatches).distinctBy { it.id }.ifEmpty { topicPool }
        }

        val customMatches = filteredCustom.mapIndexed { index, custom ->
            val diff = custom.difficultyLevel5
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
     * Collects all real questions belonging strictly to [topic] (`topic == selectedTopic`)
     * filtered by 5-tier difficulties and smart source filter (`Tüm Sorular`, `Çözülmemiş Sorular`,
     * `Yanlış Yaptıklarım`, `Boş Bıraktıklarım`, `Soru Kumbaram`).
     * NEVER uses `Math.random()` or unrelated topic questions.
     */
    private fun collectQuestionsByTopicSourceAndDifficulty(
        topic: YksTopic,
        selectedDifficulties: Set<DifficultyLevel>,
        sourceFilter: QuestionSourceFilter
    ): List<YksQuestion> {
        val strictTopicPool = _userCustomQuestions.value.filter { cq ->
            cq.topicId.equals(topic.id, ignoreCase = true) ||
                (cq.topicId.isBlank() && cq.subjectId.equals(topic.subjectId, ignoreCase = true) &&
                    cq.topicName.equals(topic.name, ignoreCase = true))
        }
        if (strictTopicPool.isEmpty()) return emptyList()

        val solvedRecordsForTopic = _userTestResults.value.filter {
            it.isSolvedAttempt && (it.topicId.equals(topic.id, ignoreCase = true) || it.topicName.equals(topic.name, ignoreCase = true))
        }
        val allKnownTests = _generatedTopicTests.value.values.flatten()

        val solvedImagePaths = mutableSetOf<String>()
        val wrongImagePaths = mutableSetOf<String>()
        val emptyImagePaths = mutableSetOf<String>()

        for (rec in solvedRecordsForTopic) {
            val answersMap = parseAnswersJson(rec.answersMapJson).orEmpty()
            val testQuestions = allKnownTests.firstOrNull { it.id == rec.testId }?.questions.orEmpty()
            for (q in testQuestions) {
                val chosen = answersMap[q.number]
                val idKey = q.croppedImagePath.ifBlank { q.id }
                if (chosen.isNullOrBlank()) {
                    emptyImagePaths.add(idKey)
                } else {
                    solvedImagePaths.add(idKey)
                    if (chosen != q.correctOption) {
                        wrongImagePaths.add(idKey)
                    }
                }
            }
        }

        val sourceFilteredPool = strictTopicPool.filter { cq ->
            val key = cq.croppedImagePath.ifBlank { "custom_${cq.id}" }
            when (sourceFilter) {
                QuestionSourceFilter.TUM_SORULAR -> true
                QuestionSourceFilter.COZULMEMIS_SORULAR -> key !in solvedImagePaths
                QuestionSourceFilter.YANLIS_YAPTIKLARIM -> key in wrongImagePaths
                QuestionSourceFilter.BOS_BIRAKTIKLARIM -> key in emptyImagePaths
                QuestionSourceFilter.SORU_KUMBARAM -> cq.isInKumbara
            }
        }

        val activeDiffs = selectedDifficulties.ifEmpty { DifficultyLevel.entries.toSet() }
        val difficultyFilteredPool = sourceFilteredPool.filter { cq ->
            cq.difficultyLevel5 in activeDiffs
        }

        return difficultyFilteredPool.mapIndexed { index, custom ->
            val diff = custom.difficultyLevel5
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
                correctOption = custom.correctOption.takeIf { it in listOf("A", "B", "C", "D", "E") } ?: "C",
                difficulty = diff,
                visualType = QuestionVisualType.NONE,
                stepByStepSolution = custom.cleanSolutionSummary.ifBlank {
                    "Kitap cevap anahtarına göre doğru yanıt ${custom.correctOption} seçeneğidir."
                }
            )
        }
    }

    /**
     * Strict 0-Hallucination check before generating a test.
     * Computes exact per-difficulty (`Kolay`, `Orta`, `ÖSYM`, `Zor`, `Çok Zor`) and per-source counts
     * for the Interactive Test Creation Panel.
     */
    fun checkPoolAvailability(topic: YksTopic, level: TopicTestLevel): PoolAvailabilityCheck {
        val strictTopicPool = _userCustomQuestions.value.filter { cq ->
            cq.topicId.equals(topic.id, ignoreCase = true) ||
                (cq.topicId.isBlank() && cq.subjectId.equals(topic.subjectId, ignoreCase = true) &&
                    cq.topicName.equals(topic.name, ignoreCase = true))
        }
        val countsByDiff = DifficultyLevel.entries.associateWith { diff ->
            strictTopicPool.count { it.difficultyLevel5 == diff }
        }
        val countsBySource = QuestionSourceFilter.entries.associateWith { src ->
            collectQuestionsByTopicSourceAndDifficulty(topic, DifficultyLevel.entries.toSet(), src).size
        }
        val allTopicQuestions = collectQuestionsByTopicSourceAndDifficulty(
            topic = topic,
            selectedDifficulties = DifficultyLevel.entries.toSet(),
            sourceFilter = QuestionSourceFilter.TUM_SORULAR
        )

        val customBuilder: (Set<DifficultyLevel>, QuestionSourceFilter, Int) -> YksTestItem? = { diffs, srcFilter, reqCount ->
            generateCustomFilteredTopicTest(
                topic = topic,
                level = level,
                selectedDifficulties = diffs,
                sourceFilter = srcFilter,
                requestedQuestionCount = reqCount
            )
        }
        val questionCountForSelection: (Set<DifficultyLevel>, QuestionSourceFilter) -> Int = { diffs, source ->
            if (level == TopicTestLevel.IKIZ && source == QuestionSourceFilter.YANLIS_YAPTIKLARIM) {
                collectRealPoolQuestionsForTopicAndLevel(topic, level).count { it.difficulty in diffs }
            } else {
                collectQuestionsByTopicSourceAndDifficulty(topic, diffs, source).size
            }
        }

        if (level == TopicTestLevel.IKIZ) {
            val realCandidates = collectRealPoolQuestionsForTopicAndLevel(topic, level)
            if (realCandidates.isEmpty() && allTopicQuestions.isEmpty()) {
                return PoolAvailabilityCheck(
                    canGenerate = false,
                    availableQuestionCount = 0,
                    reasonTitle = "Soru Bulunamadı",
                    reasonMessage = "'${topic.name}' konusunda henüz yanlış yaptığınız, boş bıraktığınız veya havuza eklenmiş bir soru kaydı bulunamadı.",
                    countsByDifficulty = countsByDiff,
                    countsBySource = countsBySource,
                    matchingQuestions = emptyList(),
                    onGenerateCustomTest = customBuilder,
                    questionCountForSelection = questionCountForSelection
                )
            }
            val effectiveList = realCandidates.ifEmpty { allTopicQuestions }
            return PoolAvailabilityCheck(
                canGenerate = true,
                availableQuestionCount = effectiveList.size,
                reasonTitle = "İkiz & Havuz Soruları Hazır",
                reasonMessage = "'${topic.name}' konusundaki ${effectiveList.size} gerçek soru ile test oluşturabilirsiniz.",
                countsByDifficulty = countsByDiff,
                countsBySource = countsBySource,
                matchingQuestions = effectiveList,
                onGenerateCustomTest = customBuilder,
                questionCountForSelection = questionCountForSelection
            )
        }

        if (allTopicQuestions.isEmpty()) {
            return PoolAvailabilityCheck(
                canGenerate = false,
                availableQuestionCount = 0,
                reasonTitle = "Soru Bulunamadı",
                reasonMessage = "'${topic.name}' için soru havuzunda uygun soru bulunamadı. Soru Havuzu & PDF Stüdyosu bölümünden yayın PDF'i yükleyerek bu konuya otomatik soru ekleyebilirsiniz.",
                countsByDifficulty = countsByDiff,
                countsBySource = countsBySource,
                matchingQuestions = emptyList(),
                onGenerateCustomTest = customBuilder,
                questionCountForSelection = questionCountForSelection
            )
        }

        return PoolAvailabilityCheck(
            canGenerate = true,
            availableQuestionCount = allTopicQuestions.size,
            reasonTitle = "Soru Havuzu Uygun",
            reasonMessage = "Soru havuzunda '${topic.name}' için ${allTopicQuestions.size} gerçek soru bulundu.",
            countsByDifficulty = countsByDiff,
            countsBySource = countsBySource,
            matchingQuestions = allTopicQuestions,
            onGenerateCustomTest = customBuilder,
            questionCountForSelection = questionCountForSelection
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

    fun generateCustomFilteredTopicTest(
        topic: YksTopic,
        level: TopicTestLevel,
        selectedDifficulties: Set<DifficultyLevel>,
        sourceFilter: QuestionSourceFilter,
        requestedQuestionCount: Int
    ): YksTestItem? {
        val strictCandidates = if (level == TopicTestLevel.IKIZ && sourceFilter == QuestionSourceFilter.YANLIS_YAPTIKLARIM) {
            collectRealPoolQuestionsForTopicAndLevel(topic, level)
                .filter { it.difficulty in selectedDifficulties }
        } else {
            collectQuestionsByTopicSourceAndDifficulty(
                topic = topic,
                selectedDifficulties = selectedDifficulties,
                sourceFilter = sourceFilter
            )
        }
        if (strictCandidates.isEmpty()) return null

        val key = "${topic.id}_${level.name}"
        val existingList = _generatedTopicTests.value[key].orEmpty()
        val allTestsInTopic = getTestsForTopicAndLevel(topic, level)
        val deletedTestNumbers = _deletedTestIds.value.mapNotNull { deletedId ->
            deletedId.takeIf { it.startsWith("${topic.id}_${level.name.lowercase()}_test_") }
                ?.substringAfterLast("_test_")
                ?.toIntOrNull()
        }
        val nextTestNumber = (
            (existingList + allTestsInTopic).mapNotNull { it.id.substringAfterLast("_test_").toIntOrNull() } +
                deletedTestNumbers
            ).maxOrNull()?.plus(1) ?: 1

        // Daha önceki testlerde kullanılmış soruları tespit et; yeni testlerde önce kullanılmamış soruları seç
        val usedSignatures = allTestsInTopic
            .flatMap { it.questions }
            .map { q -> q.croppedImagePath.ifBlank { q.stemTop.take(60) } }
            .toSet()

        val unusedCandidates = strictCandidates.filter { q ->
            val sig = q.croppedImagePath.ifBlank { q.stemTop.take(60) }
            sig !in usedSignatures
        }

        val orderedPool = if (unusedCandidates.size >= requestedQuestionCount) {
            unusedCandidates
        } else {
            val offset = ((nextTestNumber - 1) * requestedQuestionCount) % strictCandidates.size.coerceAtLeast(1)
            val rotated = strictCandidates.drop(offset) + strictCandidates.take(offset)
            (unusedCandidates + rotated).distinctBy { it.croppedImagePath.ifBlank { it.id } }
        }

        val safeCount = requestedQuestionCount.coerceIn(1, orderedPool.size)
        val questionsToUse = orderedPool.take(safeCount)
        if (questionsToUse.isEmpty()) return null

        // Otomatik İsimlendirme: "[Konu Adı] - Test [X]" (Kesinlikle kitap adı kullanılmaz)
        val shortTitle = "${topic.name} - Test $nextTestNumber"
        val fullTitle = "${topic.name} - Test $nextTestNumber"
        val expectedTestId = "${topic.id}_${level.name.lowercase()}_test_$nextTestNumber"

        val avgWeight = questionsToUse.map { it.difficulty.weight }.average().toFloat().coerceIn(0.12f, 0.94f)
        val dominantDiff = when {
            avgWeight < 0.25f -> DifficultyLevel.KOLAY
            avgWeight < 0.45f -> DifficultyLevel.ORTA
            avgWeight < 0.65f -> DifficultyLevel.OSYM
            avgWeight < 0.84f -> DifficultyLevel.ZOR
            else -> DifficultyLevel.COK_ZOR
        }

        val generatedTest = YksTestItem(
            id = expectedTestId,
            topicId = topic.id,
            title = shortTitle,
            fullTitle = fullTitle,
            difficulty = dominantDiff,
            difficultyPosition = avgWeight,
            defaultCompleted = false,
            starRating = 0,
            level = level,
            questions = questionsToUse.mapIndexed { idx, q ->
                q.copy(
                    id = "${expectedTestId}_q${idx + 1}",
                    number = idx + 1,
                    badgeTitle = fullTitle.uppercase(),
                    topicHeader = topic.name.uppercase()
                )
            }
        )

        val updatedMap = _generatedTopicTests.value + (key to (existingList + generatedTest))
        _generatedTopicTests.value = updatedMap
        refreshTopicProgressSummaries()

        val uid = effectiveUserId() ?: "device_yks_student_tr"
        repository.saveCachedGeneratedTestSnapshot(uid, generatedTest)
        val allEntries = updatedMap.flatMap { (mapKey, list) ->
            if (mapKey.endsWith("_AUTO_PDF")) {
                emptyList()
            } else {
                list.map { item ->
                    val num = item.id.substringAfterLast("_test_").toIntOrNull() ?: 1
                    Triple(item.topicId, item.level, num)
                }
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
                            answersMapJson = """{"_status":"GENERATED","level":"${level.name}","testNumber":$nextTestNumber}""",
                            testSnapshotJson = TestSnapshotCodec.encode(generatedTest)
                        )
                        repository.saveTestResult(cloudRecord)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        return generatedTest
    }

    fun generateNewAiLevelTest(topic: YksTopic, level: TopicTestLevel): YksTestItem? {
        val defaultDiffs = when (level) {
            TopicTestLevel.BASLANGIC -> setOf(DifficultyLevel.KOLAY, DifficultyLevel.ORTA)
            TopicTestLevel.PEKISTIRME -> setOf(DifficultyLevel.KOLAY, DifficultyLevel.ORTA, DifficultyLevel.OSYM)
            TopicTestLevel.GELISIYORUM -> setOf(DifficultyLevel.ORTA, DifficultyLevel.OSYM, DifficultyLevel.ZOR)
            TopicTestLevel.OSYM -> setOf(DifficultyLevel.OSYM, DifficultyLevel.ORTA, DifficultyLevel.ZOR)
            TopicTestLevel.DERECE -> setOf(DifficultyLevel.ZOR, DifficultyLevel.COK_ZOR, DifficultyLevel.OSYM)
            TopicTestLevel.IKIZ -> DifficultyLevel.entries.toSet()
        }
        val defaultSource = if (level == TopicTestLevel.IKIZ) {
            QuestionSourceFilter.YANLIS_YAPTIKLARIM
        } else {
            QuestionSourceFilter.TUM_SORULAR
        }
        return generateCustomFilteredTopicTest(
            topic = topic,
            level = level,
            selectedDifficulties = defaultDiffs,
            sourceFilter = defaultSource,
            requestedQuestionCount = 12
        )
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
        initialQuestionIndex: Int = 0,
        preloadedDurationSeconds: Int? = null
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
        val initialSeconds = if (isReviewMode) {
            preloadedDurationSeconds ?: existingRecord?.durationSeconds ?: 0
        } else {
            0
        }
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
            val diffLevel = record.difficultyLevel5
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

    fun solveBookQuestionsInPractice(
        bookTitle: String,
        records: List<CustomQuestionRecord>,
        questionsPerTest: Int = 0,
        selectedChunkIndex: Int = 0
    ) {
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

        val effectiveRecords = if (questionsPerTest > 0) {
            val chunks = sortedRecords.chunked(questionsPerTest)
            chunks.getOrNull(selectedChunkIndex) ?: chunks.firstOrNull() ?: sortedRecords
        } else {
            sortedRecords
        }

        val testQuestions = effectiveRecords.mapIndexed { idx, record ->
            val resolvedPub = SoruLabPublisherPresets.resolvePublisher(record.publisherTag, _customPublishers.value)
            val diffLevel = record.difficultyLevel5
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

        val avgBookWeight = testQuestions.map { it.difficulty.weight }.average().toFloat().coerceIn(0.18f, 0.94f)
        val dominantBookDiff = when {
            avgBookWeight < 0.29f -> DifficultyLevel.KOLAY
            avgBookWeight < 0.50f -> DifficultyLevel.ORTA
            avgBookWeight < 0.67f -> DifficultyLevel.OSYM
            avgBookWeight < 0.85f -> DifficultyLevel.ZOR
            else -> DifficultyLevel.COK_ZOR
        }
        val cleanTitle = "${matchedTopic.name} - Test ${selectedChunkIndex + 1}"
        val bookTest = YksTestItem(
            id = "book_test_${matchedTopic.id}_${selectedChunkIndex + 1}_${System.currentTimeMillis() % 100000}",
            topicId = matchedTopic.id,
            title = cleanTitle,
            fullTitle = cleanTitle,
            difficulty = dominantBookDiff,
            difficultyPosition = avgBookWeight,
            defaultCompleted = false,
            starRating = 0,
            level = TopicTestLevel.OSYM,
            questions = testQuestions.mapIndexed { idx, q ->
                q.copy(
                    number = idx + 1,
                    badgeTitle = cleanTitle.uppercase(),
                    topicHeader = matchedTopic.name.uppercase()
                )
            }
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
        val savedTest = TestSnapshotCodec.decode(record.testSnapshotJson)?.takeIf { it.id == record.testId }
        val isGeneratedResult = runCatching { JSONObject(record.answersMapJson).has("level") }.getOrDefault(false)
        val matchingTest = savedTest ?: if (isGeneratedResult) {
            null
        } else {
            _generatedTopicTests.value.values.flatten().find { it.id == record.testId }
                ?: matchingTopic.tests.find { it.id == record.testId }
                ?: SoruLabCatalog.mockExams.firstOrNull { it.linkedTest.id == record.testId }?.linkedTest
        }

        if (matchingTest == null) {
            showBanner("Bu sonuca ait test içeriği saklanmamış. Yanlış soru göstermemek için sonuç açılmadı.")
            return
        }

        startSolvingSession(
            topic = matchingTopic,
            testItem = matchingTest,
            initialViewMode = BoardViewMode.FULL_PAGE,
            isReviewMode = true,
            preloadedAnswers = parseAnswersJson(record.answersMapJson).orEmpty(),
            preloadedDurationSeconds = record.durationSeconds
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
        var ungraded = 0
        val jsonObj = JSONObject()

        cur.testItem.questions.forEach { q ->
            val chosen = cur.selectedAnswers[q.number]
            if (!q.answerKeyVerified) {
                ungraded++
                if (!chosen.isNullOrBlank()) jsonObj.put(q.number.toString(), chosen)
            } else if (chosen.isNullOrBlank()) {
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
        val docId = "${uid.take(8)}_${UUID.randomUUID()}".take(78)
        val record = TestResultRecord(
            id = docId,
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
            ungradedCount = ungraded,
            durationSeconds = finalDuration,
            answersMapJson = jsonObj.toString(),
            testSnapshotJson = TestSnapshotCodec.encode(cur.testItem),
            createdAt = com.google.firebase.Timestamp.now()
        )

        val updatedSolved = listOf(record) + _userTestResults.value
        _userTestResults.value = updatedSolved
        repository.saveCachedSolvedTestResults(uid, updatedSolved)
        refreshTopicProgressSummaries()

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
                stroke.isEraser || stroke.points.none { pt ->
                    points.any { erasePt -> (pt - erasePt).getDistance() < eraseRadius }
                }
            }
            val finalStrokes = if (remaining.any { !it.isEraser }) {
                val eraserStroke = DrawStroke(
                    points = points,
                    colorHex = 0x00000000L,
                    strokeWidthPx = 38f,
                    isSmartShape = false,
                    isEraser = true
                )
                remaining + eraserStroke
            } else {
                emptyList()
            }
            val updatedStrokes = _boardStrokes.value + (key to finalStrokes)
            _boardStrokes.value = updatedStrokes
            repository.saveCachedBoardStrokes(effectiveUserId() ?: "device_yks_student_tr", updatedStrokes)
            return
        }

        val baseWidthPx = when (_selectedStrokeSizeIndex.value) {
            0 -> 4.5f
            1 -> 8.0f
            else -> 13.0f
        }
        val isHighlighterTool = tool == DrawingTool.HIGHLIGHTER
        val widthPx = if (isHighlighterTool) baseWidthPx * 2.6f else baseWidthPx

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
            isSmartShape = tool == DrawingTool.SMART_SHAPE,
            isEraser = false,
            isHighlighter = isHighlighterTool
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
        val classifiedTopic = SoruLabCatalog.classifyQuestionTopicFromText(
            examTypeHint = examType,
            subjectNameHint = subjectName,
            questionText = questionText,
            suggestedTopicName = topicName
        )
        val matchedSubject = classifiedTopic?.first ?: SoruLabCatalog.allSubjects.firstOrNull {
            it.examType.name.equals(examType, ignoreCase = true) &&
                it.name.equals(subjectName, ignoreCase = true)
        } ?: SoruLabCatalog.allSubjects.firstOrNull {
            it.name.equals(subjectName, ignoreCase = true)
        }
        val matchedTopic = classifiedTopic?.second ?: matchedSubject?.topics?.firstOrNull {
            it.name.equals(topicName, ignoreCase = true) ||
                topicName.contains(it.name, ignoreCase = true) ||
                it.name.contains(topicName, ignoreCase = true)
        } ?: SoruLabCatalog.allSubjects.flatMap { it.topics }.firstOrNull {
            it.name.equals(topicName, ignoreCase = true) ||
                topicName.contains(it.name, ignoreCase = true)
        }
        if (matchedSubject == null || matchedTopic == null) {
            showBanner("Soru konusu müfredatla eşleştirilemedi; soru kaydedilmedi.")
            return
        }

        val resolvedExamType = matchedSubject?.examType?.name ?: if (examType.equals("AYT", true)) "AYT" else "TYT"
        val subjectId = matchedSubject?.id ?: if (resolvedExamType == "AYT") "ayt_mat" else "tyt_mat"
        val resolvedSubjectName = matchedSubject?.name ?: subjectName.ifBlank { "Matematik" }
        val topicId = matchedTopic?.id ?: topicName.lowercase().replace(" ", "_").take(32).ifBlank { "temel_kavramlar" }
        val resolvedTopicName = matchedTopic.name
        val resolvedDifficulty = if (questionText.length >= 25) {
            SoruLabCatalog.classifyDifficulty5FromText(
                rawDifficultyHint = "",
                headerOrQuestionText = questionText
            ).label
        } else {
            difficulty
        }

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
            bookCoverPath = bookCoverPath,
            fiveTierDifficulty = resolvedDifficulty
        )

        val firestoreCompatDiff = when {
            resolvedDifficulty.equals("Kolay", ignoreCase = true) -> "Kolay"
            resolvedDifficulty.equals("Zor", ignoreCase = true) || resolvedDifficulty.equals("Çok Zor", ignoreCase = true) -> "Zor"
            else -> "Orta"
        }

        val record = CustomQuestionRecord(
            id = "cq_${System.currentTimeMillis()}_${(100..999).random()}",
            userId = uid,
            examType = resolvedExamType,
            subjectId = subjectId,
            subjectName = resolvedSubjectName,
            topicId = topicId,
            topicName = resolvedTopicName,
            difficulty = firestoreCompatDiff,
            correctOption = correctOption.uppercase().takeIf { it in listOf("A", "B", "C", "D", "E") } ?: "D",
            questionText = questionText.ifBlank { "PDF'ten kırpılan YKS sorusu" }.take(1800),
            publisherTag = publisherTag.ifBlank { resolvedPublisher.name }.take(100),
            aiSolutionSummary = packedSolution,
            cropAspectRatio = cropAspectRatio.coerceIn(0.3, 4.5)
        )

        val updatedList = listOf(record) + _userCustomQuestions.value
        _userCustomQuestions.value = updatedList
        try {
            repository.saveCachedCustomQuestions(uid, updatedList)
        } catch (_: Exception) {
            showBanner("Soru oturumda eklendi ancak cihazdaki yerel depoya yazılamadı.")
        }
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

        viewModelScope.launch(Dispatchers.Default) {
            val subjectMemo = HashMap<String, com.example.data.YksSubject>(32)
            val batchTimestamp = System.currentTimeMillis()

            val newRecords = ArrayList<CustomQuestionRecord>(extractedItems.size)
            for ((idx, item) in extractedItems.withIndex()) {
                val classifiedTopic = SoruLabCatalog.classifyQuestionTopicFromText(
                    examTypeHint = item.examType,
                    subjectNameHint = item.subjectName,
                    questionText = item.questionText,
                    suggestedTopicName = item.topicName
                )
                val subKey = "${item.examType}|${item.subjectName}"
                val matchedSubject = classifiedTopic?.first ?: subjectMemo.getOrPut(subKey) {
                    SoruLabCatalog.allSubjects.firstOrNull {
                        it.examType.name.equals(item.examType, ignoreCase = true) &&
                            it.name.equals(item.subjectName, ignoreCase = true)
                    } ?: SoruLabCatalog.allSubjects.firstOrNull {
                        it.name.equals(item.subjectName, ignoreCase = true) ||
                            item.subjectName.contains(it.name, ignoreCase = true)
                    } ?: SoruLabCatalog.allSubjects.first()
                }

                val matchedTopic = classifiedTopic?.second
                    ?: SoruLabCatalog.resolveTopicFast("", item.topicName)?.takeIf {
                        it.subjectId == matchedSubject.id
                    }
                    ?: matchedSubject.topics.firstOrNull {
                        it.name.equals(item.topicName, ignoreCase = true) ||
                            item.topicName.contains(it.name, ignoreCase = true) ||
                            it.name.contains(item.topicName, ignoreCase = true)
                    }
                    ?: SoruLabCatalog.resolveTopicFast("", item.topicName)
                if (matchedTopic == null) {
                    withContext(Dispatchers.Main) {
                        showBanner("“${item.topicName}” konusu müfredatla eşleştirilemedi; PDF aktarımı durduruldu.")
                    }
                    return@launch
                }

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
                    bookCoverPath = effectiveCover,
                    fiveTierDifficulty = item.difficulty
                )

                val firestoreCompatDiff = when {
                    item.difficulty.equals("Kolay", ignoreCase = true) -> "Kolay"
                    item.difficulty.equals("Zor", ignoreCase = true) || item.difficulty.equals("Çok Zor", ignoreCase = true) -> "Zor"
                    else -> "Orta"
                }

                newRecords.add(
                    CustomQuestionRecord(
                        id = "cq_${batchTimestamp}_${idx}_${(100..999).random()}",
                        userId = uid,
                        examType = resolvedExamType,
                        subjectId = subjectId,
                        subjectName = resolvedSubjectName,
                        topicId = topicId,
                        topicName = resolvedTopicName,
                        difficulty = firestoreCompatDiff,
                        correctOption = item.correctOption.uppercase().takeIf { it in listOf("A", "B", "C", "D", "E") }.orEmpty(),
                        questionText = item.questionText.ifBlank { "$resolvedSubjectName • $resolvedTopicName" }.take(1800),
                        publisherTag = finalPubName,
                        aiSolutionSummary = packedSolution,
                        cropAspectRatio = item.cropAspectRatio.coerceIn(0.3, 4.5)
                    )
                )
            }

            val updatedList = ArrayList<CustomQuestionRecord>(newRecords.size + _userCustomQuestions.value.size)
            updatedList.addAll(newRecords)
            updatedList.addAll(_userCustomQuestions.value)

            var localCacheSaved = true
            try {
                withContext(Dispatchers.IO) {
                    repository.saveCachedCustomQuestions(uid, updatedList)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                localCacheSaved = false
            }

            withContext(Dispatchers.Main) {
                _userCustomQuestions.value = updatedList
                showBanner(
                    if (localCacheSaved) {
                        "'$bookOrTestName' kaynağından ${newRecords.size} soru cihazınıza eklendi."
                    } else {
                        "Sorular oturumda açıldı ancak cihaz depolamasına yazılamadı; uygulamayı kapatmayın ve depolama alanını kontrol edin."
                    }
                )
            }

            syncPdfPoolToPracticeTests(updatedList)

            val firebaseUid = _currentUser.value?.uid
            if (firebaseUid != null) {
                launch(Dispatchers.IO) {
                    val cloudBatch = newRecords.map { it.copy(userId = firebaseUid) }
                    try {
                        repository.saveCustomQuestionsBatch(cloudBatch)
                        withContext(Dispatchers.Main) {
                            showBanner(
                                if (localCacheSaved) {
                                    "'$bookOrTestName' soruları bulut hesabınızla eşitlendi."
                                } else {
                                    "Bulut eşitlemesi tamamlandı; cihaz önbelleği yazılamadı."
                                }
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            showBanner(
                                "Sorular cihazda ${if (localCacheSaved) "kaydedildi" else "oturumda tutuluyor"}; buluta eşitlenemedi. " +
                                    cloudSaveProblem(e)
                            )
                        }
                    }
                }
            } else if (localCacheSaved) {
                withContext(Dispatchers.Main) {
                    showBanner("Sorular cihazınıza kaydedildi. Bulut eşitlemesi için hesabınızla giriş yapın.")
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
                isInKumbara = true,
                fiveTierDifficulty = target.effectiveDifficultyLabel
            )
            val updatedRecord = target.copy(aiSolutionSummary = updatedPacked)
            val mutable = currentQuestions.toMutableList()
            mutable[existingIdx] = updatedRecord
            _userCustomQuestions.value = mutable
            saveCustomQuestionsCacheAsync(uid, mutable)
            val firebaseUid = _currentUser.value?.uid
            if (firebaseUid != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching { repository.saveCustomQuestion(updatedRecord.copy(userId = firebaseUid)) }
                }
            }
        }
        showBanner("Soru Kumbarana eklendi! (${question.topicHeader} • ${question.number}. Soru)")
    }

    fun deleteLibraryBookQuestions(questionIds: List<String>) {
        if (questionIds.isEmpty()) return
        val idSet = questionIds.toHashSet()
        val uid = effectiveUserId() ?: "device_yks_student_tr"
        val updated = _userCustomQuestions.value.filterNot { it.id in idSet }
        _userCustomQuestions.value = updated
        saveCustomQuestionsCacheAsync(uid, updated)
        syncPdfPoolToPracticeTests(updated)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.deleteCustomQuestionsBatch(questionIds) }
        }
        showBanner("Kaynak kitap ve soruları Kitaplığınızdan kaldırıldı.")
    }

    fun deleteCustomQuestion(questionId: String) {
        val uid = effectiveUserId() ?: "device_yks_student_tr"
        val updated = _userCustomQuestions.value.filterNot { it.id == questionId }
        _userCustomQuestions.value = updated
        saveCustomQuestionsCacheAsync(uid, updated)
        syncPdfPoolToPracticeTests(updated)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteCustomQuestion(questionId)
                withContext(Dispatchers.Main) {
                    showBanner("Soru havuzdan silindi.")
                }
            } catch (_: Exception) {
            }
        }
    }

    fun clearAllUserData() {
        val uid = effectiveUserId() ?: "device_yks_student_tr"
        val oldResults = allCloudTestRecords.toList()
        val oldCustomQuestionIds = _userCustomQuestions.value.mapNotNull { it.id.takeIf { id -> id.isNotBlank() } }

        allCloudTestRecords = emptyList()
        _generatedTopicTests.value = emptyMap()
        _deletedTestIds.value = emptySet()
        _userTestResults.value = emptyList()
        _userCustomQuestions.value = emptyList()
        _boardStrokes.value = emptyMap()

        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllLocalUserData(uid)
            oldResults.forEach { rec ->
                if (rec.id.isNotBlank()) {
                    runCatching { repository.deleteTestResult(rec.id) }
                }
            }
            if (oldCustomQuestionIds.isNotEmpty()) {
                runCatching { repository.deleteCustomQuestionsBatch(oldCustomQuestionIds) }
            }
        }
        refreshTopicProgressSummaries()
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

    private fun cloudSaveProblem(error: Exception): String {
        return when ((error as? com.google.firebase.firestore.FirebaseFirestoreException)?.code) {
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                "Oturum doğrulanamadı; yeniden giriş yapın."
            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Firebase erişim izni reddedildi; güvenlik kuralları ve hesap izinleri kontrol edilmeli."
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
            com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "Bağlantı kurulamadı; internetinizi kontrol edip eşitlemeyi yeniden deneyin."
            else -> "Eşitlemeyi yeniden deneyin veya uygulama desteğine başvurun."
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
