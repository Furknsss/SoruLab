package com.example.viewmodel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AUTOMATIC_TEST_QUESTION_COUNT
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
import com.example.data.recommendedDifficultyLevels
import com.example.util.PdfScopedAnswerKeys
import com.example.util.StudioImageAndPdfEngine
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

/** Cloud owns shared metadata; this device owns paths, source pages and crop coordinates. */
internal fun mergeCloudQuestionsWithLocalMedia(
    userId: String,
    cloud: List<CustomQuestionRecord>,
    local: List<CustomQuestionRecord>
): List<CustomQuestionRecord> {
    val ownedLocal = local.filter { it.userId == userId }
    val localById = ownedLocal.associateBy { it.id }
    val merged = cloud.filter { it.userId == userId }.map { remote ->
        val cached = localById[remote.id] ?: return@map remote
        val hasLocalPdf = cached.pdfFilePath.isNotBlank() && cached.pageIndex >= 0
        remote.copy(
            learningOutcome = remote.learningOutcome.ifBlank { cached.learningOutcome },
            questionType = remote.questionType.ifBlank { cached.questionType },
            bookId = cached.bookId.ifBlank { remote.bookId },
            imageBase64 = cached.imageBase64.ifBlank { remote.imageBase64 },
            pdfFilePath = if (hasLocalPdf) cached.pdfFilePath else remote.pdfFilePath,
            pageIndex = if (hasLocalPdf) cached.pageIndex else remote.pageIndex,
            questionNumberInTest = cached.questionNumberInTest.takeIf { it > 0 } ?: remote.questionNumberInTest,
            normLeft = if (hasLocalPdf) cached.normLeft else remote.normLeft,
            normTop = if (hasLocalPdf) cached.normTop else remote.normTop,
            normRight = if (hasLocalPdf) cached.normRight else remote.normRight,
            normBottom = if (hasLocalPdf) cached.normBottom else remote.normBottom,
            aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta(
                solutionText = remote.cleanSolutionSummary,
                targetLevel = remote.targetLevelName,
                croppedImagePath = cached.croppedImagePath.ifBlank { remote.croppedImagePath },
                publisherLogoUri = cached.publisherLogoUri.ifBlank { remote.publisherLogoUri },
                answerKeyVerified = remote.answerKeyVerified,
                questionNumberInBook = remote.questionNumberInBook,
                bookOrTestName = remote.bookOrTestName,
                bookCoverPath = cached.bookCoverPath.ifBlank { remote.bookCoverPath },
                isInKumbara = remote.isInKumbara,
                fiveTierDifficulty = remote.effectiveDifficultyLabel
            )
        )
    }
    val cloudIds = merged.mapTo(mutableSetOf()) { it.id }
    return merged + ownedLocal.filter { it.id !in cloudIds }
}

internal fun <T> interleaveByGroup(items: List<T>, groupKey: (T) -> String): List<T> {
    val groups = items
        .groupBy { groupKey(it).trim().lowercase() }
        .values
        .sortedBy { group -> group.firstOrNull()?.let(groupKey).orEmpty().lowercase() }
    return buildList {
        val maxGroupSize = groups.maxOfOrNull { it.size } ?: 0
        for (round in 0 until maxGroupSize) {
            groups.forEach { group -> group.getOrNull(round)?.let(::add) }
        }
    }
}

internal fun difficultyFallbackOrder(
    preferredDifficulties: Set<DifficultyLevel>
): List<DifficultyLevel> {
    val preferredOrder = DifficultyLevel.entries.filter { it in preferredDifficulties }
        .ifEmpty { DifficultyLevel.entries }
    val fallbackOrder = DifficultyLevel.entries
        .filterNot { it in preferredOrder }
        .sortedWith(
            compareBy<DifficultyLevel> { difficulty ->
                preferredOrder.minOf { preferred ->
                    kotlin.math.abs(preferred.ordinal - difficulty.ordinal)
                }
            }.thenBy { it.ordinal }
        )
    return preferredOrder + fallbackOrder
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
    val onGenerateCustomTest: ((Set<DifficultyLevel>, QuestionSourceFilter, Int, String?) -> YksTestItem?)? = null,
    val questionCountForSelection: ((Set<DifficultyLevel>, QuestionSourceFilter, String?) -> Int)? = null,
    val questionCountForDifficulty: ((DifficultyLevel, QuestionSourceFilter) -> Int)? = null,
    val availablePublishers: List<PublisherChoice> = emptyList()
)

@Immutable
data class DrawStroke(
    val points: List<Offset>,
    val colorHex: Long,
    val strokeWidthPx: Float,
    val isSmartShape: Boolean = false,
    val isEraser: Boolean = false,
    val isHighlighter: Boolean = false,
    val pressureSamples: List<Float> = emptyList()
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

    private val _selectedTestLevel = MutableStateFlow(TopicTestLevel.SEVIYE_TESPIT)
    val selectedTestLevel: StateFlow<TopicTestLevel> = _selectedTestLevel.asStateFlow()

    private val _generatedTopicTests = MutableStateFlow<Map<String, List<YksTestItem>>>(emptyMap())
    val generatedTopicTests: StateFlow<Map<String, List<YksTestItem>>> = _generatedTopicTests.asStateFlow()

    private val _deletedTestIds = MutableStateFlow<Set<String>>(emptySet())
    val deletedTestIds: StateFlow<Set<String>> = _deletedTestIds.asStateFlow()

    private val _dialogTestItem = MutableStateFlow<Pair<YksTopic, YksTestItem>?>(null)
    val dialogTestItem: StateFlow<Pair<YksTopic, YksTestItem>?> = _dialogTestItem.asStateFlow()

    private val _activeSession = MutableStateFlow<ActiveSolvingSession?>(null)
    val activeSession: StateFlow<ActiveSolvingSession?> = _activeSession.asStateFlow()

    private val _activeTestQuestions = MutableStateFlow<List<YksQuestion>>(emptyList())
    val activeTestQuestions: StateFlow<List<YksQuestion>> = _activeTestQuestions.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _boardStrokes = MutableStateFlow<Map<String, List<DrawStroke>>>(emptyMap())
    val boardStrokes: StateFlow<Map<String, List<DrawStroke>>> = _boardStrokes.asStateFlow()

    private val _currentBookPageIndex = MutableStateFlow(0)
    val currentBookPageIndex: StateFlow<Int> = _currentBookPageIndex.asStateFlow()

    private val _activeTool = MutableStateFlow(DrawingTool.PEN)
    val activeTool: StateFlow<DrawingTool> = _activeTool.asStateFlow()

    private val _selectedStrokeSizeIndex = MutableStateFlow(1)
    val selectedStrokeSizeIndex: StateFlow<Int> = _selectedStrokeSizeIndex.asStateFlow()
    // Pen thickness and eraser size are separate tools, not one shared value.
    private var penStrokeSizeIndex = 1
    private var eraserStrokeSizeIndex = 1

    private val _selectedPenColorHex = MutableStateFlow(0xFF111111L)
    val selectedPenColorHex: StateFlow<Long> = _selectedPenColorHex.asStateFlow()

    private val _userTestResults = MutableStateFlow<List<TestResultRecord>>(emptyList())
    val userTestResults: StateFlow<List<TestResultRecord>> = _userTestResults.asStateFlow()

    private var allCloudTestRecords: List<TestResultRecord> = emptyList()

    private val _userCustomQuestions = MutableStateFlow<List<CustomQuestionRecord>>(emptyList())
    val userCustomQuestions: StateFlow<List<CustomQuestionRecord>> = _userCustomQuestions.asStateFlow()

    private val _customPublishers = MutableStateFlow(
        repository.loadCustomPublishers(_currentUser.value?.uid ?: _recognizedDeviceAccount.value?.uid)
    )
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
    private var customPublishersListenerJob: Job? = null
    private var poolSyncJob: Job? = null
    private var cacheSaveJob: Job? = null

    private val allTopicsById: Map<String, YksTopic> by lazy {
        SoruLabCatalog.topicByIdMap
    }

    private fun saveCustomQuestionsCacheAsync(
        uid: String,
        list: List<CustomQuestionRecord>,
        allowRemovals: Boolean = false
    ) {
        cacheSaveJob?.cancel()
        cacheSaveJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val durable = repository.saveCachedCustomQuestions(uid, list, allowRemovals)
                withContext(Dispatchers.Main) {
                    if ((effectiveUserId() ?: com.example.util.PdfLibraryAccount.GUEST_CACHE_UID) == uid &&
                        durable.size > _userCustomQuestions.value.size
                    ) {
                        // A partial cloud listener must not shrink the visible bank.
                        _userCustomQuestions.value = durable
                        syncPdfPoolToPracticeTests(durable)
                    }
                }
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
            customPublishersListenerJob?.cancel()
            val recognized = repository.loadRecognizedDeviceAccount()
            _recognizedDeviceAccount.value = recognized
            if (recognized != null) {
                hydrateLocalSessionData(recognized.uid)
            } else {
                _customPublishers.value = repository.loadCustomPublishers()
                resetUserScopedData()
                // A student can import PDFs before Google sign-in. Those
                // books were written to the guest cache and MUST be reopened.
                hydrateLocalSessionData(com.example.util.PdfLibraryAccount.GUEST_CACHE_UID)
            }
        }
    }

    init {
        repository.ensureDemoDataCleanedOnce()
        auth?.addAuthStateListener(authStateListener)
        val current = auth?.currentUser
        if (current != null) {
            startFirestoreListeners(current.uid)
        } else {
            hydrateLocalSessionData(com.example.util.PdfLibraryAccount.cacheUid(
                _currentUser.value?.uid, _recognizedDeviceAccount.value?.uid
            ))
        }
    }

    override fun onCleared() {
        super.onCleared()
        auth?.removeAuthStateListener(authStateListener)
        timerJob?.cancel()
        resultsListenerJob?.cancel()
        customQuestionsListenerJob?.cancel()
        customPublishersListenerJob?.cancel()
    }

    private fun isLegacyDemoQuestion(q: CustomQuestionRecord): Boolean {
        // Missing images or familiar question text do not identify demo data.
        return q.id.startsWith("demo_")
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
            val customPubs = repository.loadCustomPublishers(uid).filter {
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
                if (com.example.util.PdfLibraryAccount.cacheUid(
                        _currentUser.value?.uid, _recognizedDeviceAccount.value?.uid
                    ) != uid) return@withContext
                _deletedTestIds.value = delIds
                _customPublishers.value = (customPubs + _customPublishers.value).distinctBy { it.id }
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
        customPublishersListenerJob?.cancel()
        _customPublishers.value = repository.loadCustomPublishers(uid)
        resetUserScopedData()

        // 1. Fast non-blocking background hydration for generated tests, custom questions & solved results
        hydrateLocalSessionData(uid)

        customPublishersListenerJob = viewModelScope.launch {
            repository.observeUserCustomPublishers(uid)
                .catch { error ->
                    if (error is CancellationException) throw error
                    showBanner("Yayın logoları buluttan eşitlenemedi. Firestore kurallarını ve internet bağlantısını kontrol edin.")
                }
                .collect { cloudPublishers ->
                    val localPublishers = withContext(Dispatchers.IO) {
                        repository.loadCustomPublishers(uid).filter {
                            it.customLogoPath.isNotBlank() &&
                                java.io.File(it.customLogoPath).isFile
                        }
                    }
                    val cloudIds = cloudPublishers.mapTo(mutableSetOf()) { it.id }
                    val cloudNames = cloudPublishers.mapTo(mutableSetOf()) { it.name.trim().lowercase() }
                    val localOnly = localPublishers.filter {
                        it.id !in cloudIds && it.name.trim().lowercase() !in cloudNames
                    }
                    val merged = (cloudPublishers + localOnly)
                        .distinctBy { it.name.trim().lowercase() }
                    _customPublishers.value = merged
                    withContext(Dispatchers.IO) {
                        repository.saveCustomPublishers(merged, uid)
                    }
                    localOnly.forEach { publisher ->
                        try {
                            repository.saveCloudCustomPublisher(uid, publisher)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            showBanner("‘${publisher.name}’ logosu cihazda kayıtlı; bulut yedeklemesi tamamlanamadı.")
                        }
                    }
                }
        }

        // 2. Real-time Firestore listener for both solved results & cloud-saved generated tests
        resultsListenerJob = viewModelScope.launch {
            repository.observeUserTestResults(uid)
                .catch { }
                .collect { records ->
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
                    val (cleanCloud, merged) = withContext(Dispatchers.Default) {
                        val validCloud = cloudQuestions.filterNot { isLegacyDemoQuestion(it) }
                        val currentInMemory = _userCustomQuestions.value
                        val baseLocal = if (currentInMemory.isNotEmpty()) {
                            currentInMemory
                        } else {
                            withContext(Dispatchers.IO) {
                                repository.loadCachedCustomQuestions(uid).filterNot { isLegacyDemoQuestion(it) }
                            }
                        }
                        validCloud to mergeCloudQuestionsWithLocalMedia(uid, validCloud, baseLocal)
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
        _activeTestQuestions.value = emptyList()
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
        // Previously generated GELISIYORUM tests remain visible after wheel rename.
        val legacy = if (level == TopicTestLevel.SEVIYE_TESPIT) {
            map["${topic.id}_GELISIYORUM"].orEmpty().map { it.copy(level = level) }
        } else emptyList()
        val deleted = _deletedTestIds.value
        return (levelSpecific + legacy)
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
                val level = TopicTestLevel.fromPersistedName(levelStr)
                if (rec.topicId.isNotBlank() && level != null && testNum >= 1) {
                    return Triple(rec.topicId, level, testNum)
                }
            }
        } catch (_: Exception) {
        }
        for (level in TopicTestLevel.entries) {
            val tokens = if (level == TopicTestLevel.SEVIYE_TESPIT) {
                listOf("_seviye_tespit_test_", "_gelisiyorum_test_")
            } else listOf("_${level.name.lowercase()}_test_")
            for (token in tokens) {
                if (rec.testId.contains(token)) {
                    val topicId = rec.testId.substringBefore(token).ifBlank { rec.topicId }
                    val num = rec.testId.substringAfter(token).toIntOrNull() ?: 1
                    if (topicId.isNotBlank()) return Triple(topicId, level, num)
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
            val generatedId = "${topic.id}_${level.name.lowercase()}_test_$testNumber"
            val oldId = "${topic.id}_gelisiyorum_test_$testNumber"
            val expectedTestId = if (level == TopicTestLevel.SEVIYE_TESPIT &&
                (oldId in cachedSnapshots || oldId in snapshotJsonByTestId)
            ) oldId else generatedId
            if (expectedTestId !in deleted && currentList.none { it.id == expectedTestId }) {
                val snapshot = TestSnapshotCodec.decode(
                    snapshotJsonByTestId[expectedTestId] ?: cachedSnapshots[expectedTestId].orEmpty()
                )?.takeIf { it.id == expectedTestId }
                    ?.let(::hydrateSnapshotQuestionImages)
                val realQuestions = if (snapshot == null) {
                    LevelQuestionPolicy.select(
                        level, collectRealPoolQuestionsForTopicAndLevel(topic, level),
                        AUTOMATIC_TEST_QUESTION_COUNT
                    )
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
     * 1. For IKIZ level: only verified questions the user actually answered incorrectly in this topic.
     * 2. For standard levels: user's cropped/custom questions for this topic calibrated to the requested 6-level wheel category.
     */
    private fun matchesTopic(
        recordSubjectId: String,
        recordTopicId: String,
        recordTopicName: String,
        topic: YksTopic
    ): Boolean {
        val sameSubject = recordSubjectId.equals(topic.subjectId, ignoreCase = true)
        val sameTopic = recordTopicId.equals(topic.id, ignoreCase = true) ||
            (recordTopicId.isBlank() && recordTopicName.equals(topic.name, ignoreCase = true))
        return sameSubject && sameTopic
    }

    private fun hydrateSnapshotQuestionImages(test: YksTestItem): YksTestItem {
        val sourceRecords = _userCustomQuestions.value.associateBy { it.id }
        return test.copy(
            questions = test.questions.map { question ->
                val source = sourceRecords[question.sourceQuestionId]
                    ?: return@map question
                question.copy(
                    croppedImagePath = source.croppedImagePath.ifBlank { question.croppedImagePath },
                    bookId = source.bookId.ifBlank { question.bookId },
                    sourceQuestionId = source.id,
                    publisherId = source.publisherId.ifBlank { question.publisherId },
                    publisherName = source.publisherTag.ifBlank { question.publisherName },
                    publisherLogoUri = source.publisherLogoUri.ifBlank { question.publisherLogoUri },
                    sourceBookTitle = source.sourceBookTitle.ifBlank { source.bookOrTestName.ifBlank { question.sourceBookTitle } },
                    sourceTestId = source.sourceTestId.ifBlank { question.sourceTestId },
                    sourcePageIndex = source.pageIndex.takeIf { it >= 0 } ?: question.sourcePageIndex,
                    sourcePrintedQuestionNumber = source.questionNumberInBook.takeIf { it > 0 } ?: question.sourcePrintedQuestionNumber,
                    subject = source.subjectName.ifBlank { question.subject },
                    imageBase64 = source.imageBase64.ifBlank { question.imageBase64 },
                    pdfFilePath = source.pdfFilePath.ifBlank { question.pdfFilePath },
                    pageIndex = source.pageIndex.takeIf { it >= 0 } ?: question.pageIndex,
                    questionNumberInTest = source.questionNumberInTest.takeIf { it > 0 }
                        ?: question.questionNumberInTest,
                    normLeft = source.normLeft,
                    normTop = source.normTop,
                    normRight = source.normRight,
                    normBottom = source.normBottom
                )
            }
        )
    }

    /** Return real incorrect answers from completed tests; never a same-topic substitute. */
    private fun collectWrongQuestionsFromResultsForTopic(topic: YksTopic): List<YksQuestion> {
        val knownTests = _generatedTopicTests.value.values.flatten()
        val errors = mutableListOf<YksQuestion>()
        val seen = mutableSetOf<String>()
        for (record in _userTestResults.value.filter {
            it.isSolvedAttempt && matchesTopic(it.subjectId, it.topicId, it.topicName, topic)
        }.asReversed()) {
            val questions = TestSnapshotCodec.decode(record.testSnapshotJson)?.questions
                ?: knownTests.firstOrNull { it.id == record.testId }?.questions
                ?: continue
            val answers = parseAnswersJson(record.answersMapJson).orEmpty()
            MistakeReviewPolicy.wrongAnswersOnly(questions, answers).forEach { q ->
                val key = LevelQuestionPolicy.stableId(q)
                if (seen.add(key)) errors.add(q)
            }
        }
        return errors.mapIndexed { index, q -> q.copy(number = index + 1) }
    }

    private fun collectRealPoolQuestionsForTopicAndLevel(
        topic: YksTopic,
        level: TopicTestLevel
    ): List<YksQuestion> {
        if (level == TopicTestLevel.IKIZ) {
            return collectWrongQuestionsFromResultsForTopic(topic)
        }

        return buildRealBaseQuestionsForTopic(topic, level)
    }

    private fun buildRealBaseQuestionsForTopic(
        topic: YksTopic,
        levelFilter: TopicTestLevel?
    ): List<YksQuestion> {
        val topicPool = _userCustomQuestions.value.filter { question ->
            matchesTopic(question.subjectId, question.topicId, question.topicName, topic) &&
                repository.hasRecoverableQuestionImage(question)
        }

        val filteredCustom = if (levelFilter == null) topicPool else {
            // Never let a legacy wheel-level tag override the verified question
            // difficulty. E.g. an Orta question tagged OSYM is still Orta.
            topicPool.filter { it.difficultyLevel5 in levelFilter.recommendedDifficultyLevels() }
        }

        val mixedCustom = interleaveByGroup(filteredCustom) { it.publisherTag }
        val customMatches = mixedCustom.mapIndexed { index, custom ->
            val diff = custom.difficultyLevel5
            val resolvedPub = SoruLabPublisherPresets.resolvePublisher(
                custom.publisherTag,
                _customPublishers.value
            )
            val logoUri = custom.publisherLogoUri.ifBlank { resolvedPub.customLogoPath }
            val hasCroppedStemImage = custom.croppedImagePath.isNotBlank()
            val verifiedCorrectOption =
                custom.correctOption.takeIf { it in listOf("A", "B", "C", "D", "E") }.orEmpty()
            val answerKeyVerified = custom.answerKeyVerified && verifiedCorrectOption.isNotBlank()

            YksQuestion(
                id = "custom_${custom.id.ifBlank { index.toString() }}",
                number = index + 1,
                badgeTitle = custom.publisherTag.uppercase(),
                topicHeader = topic.name.uppercase(),
                publisherStamp = custom.publisherTag.uppercase(),
                publisherName = custom.publisherTag.ifBlank { resolvedPub.name },
                publisherLogoUri = logoUri,
                croppedImagePath = custom.croppedImagePath,
                answerKeyVerified = answerKeyVerified,
                stemTop = if (hasCroppedStemImage) "" else custom.questionText,
                analyticsText = custom.questionText,
                learningOutcome = custom.learningOutcome,
                questionType = custom.questionType,
                bulletsOrSteps = emptyList(),
                stemBottom = if (hasCroppedStemImage) "" else "Buna göre doğru seçenek aşağıdakilerden hangisidir?",
                options = mapOf(
                    "A" to "A",
                    "B" to "B",
                    "C" to "C",
                    "D" to "D",
                    "E" to "E"
                ),
                correctOption = verifiedCorrectOption,
                difficulty = diff,
                visualType = QuestionVisualType.NONE,
                stepByStepSolution = custom.cleanSolutionSummary.ifBlank {
                    if (answerKeyVerified) {
                        "Kitap cevap anahtarına göre doğru yanıt $verifiedCorrectOption seçeneğidir."
                    } else {
                        "Bu sorunun cevap anahtarı doğrulanamadı."
                    }
                },
                bookId = custom.bookId,
                sourceQuestionId = custom.id,
                publisherId = custom.publisherId,
                sourceBookTitle = custom.sourceBookTitle.ifBlank { custom.bookOrTestName },
                sourceTestId = custom.sourceTestId,
                sourcePageIndex = custom.pageIndex,
                sourcePrintedQuestionNumber = custom.questionNumberInBook,
                subject = custom.subjectName,
                imageBase64 = custom.imageBase64,
                pdfFilePath = custom.pdfFilePath,
                pageIndex = custom.pageIndex,
                questionNumberInTest = custom.questionNumberInTest,
                normLeft = custom.normLeft,
                normTop = custom.normTop,
                normRight = custom.normRight,
                normBottom = custom.normBottom
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
            matchesTopic(cq.subjectId, cq.topicId, cq.topicName, topic) &&
                repository.hasRecoverableQuestionImage(cq)
        }
        if (strictTopicPool.isEmpty()) return emptyList()

        val solvedRecordsForTopic = _userTestResults.value.filter {
            it.isSolvedAttempt && matchesTopic(it.subjectId, it.topicId, it.topicName, topic)
        }
        val allKnownTests = _generatedTopicTests.value.values.flatten()

        val solvedImagePaths = mutableSetOf<String>()
        val wrongImagePaths = mutableSetOf<String>()
        val emptyImagePaths = mutableSetOf<String>()

        for (rec in solvedRecordsForTopic) {
            val answersMap = parseAnswersJson(rec.answersMapJson).orEmpty()
            val testQuestions = allKnownTests.firstOrNull {
                it.id == rec.testId && it.topicId.equals(topic.id, ignoreCase = true)
            }?.questions.orEmpty().filter {
                it.topicHeader.equals(topic.name, ignoreCase = true)
            }
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

        // Aynı konu testinde kaynak çeşitliliğini korumak için yayınevlerini tur tur karıştırır.
        val mixedPool = interleaveByGroup(difficultyFilteredPool) { it.publisherTag }

        return mixedPool.mapIndexed { index, custom ->
            val diff = custom.difficultyLevel5
            val resolvedPub = SoruLabPublisherPresets.resolvePublisher(
                custom.publisherTag,
                _customPublishers.value
            )
            val logoUri = custom.publisherLogoUri.ifBlank { resolvedPub.customLogoPath }
            val hasCroppedStemImage = custom.croppedImagePath.isNotBlank()
            val verifiedCorrectOption =
                custom.correctOption.takeIf { it in listOf("A", "B", "C", "D", "E") }.orEmpty()
            val answerKeyVerified = custom.answerKeyVerified && verifiedCorrectOption.isNotBlank()

            YksQuestion(
                id = "custom_${custom.id.ifBlank { index.toString() }}",
                number = index + 1,
                badgeTitle = custom.publisherTag.uppercase(),
                topicHeader = topic.name.uppercase(),
                publisherStamp = custom.publisherTag.uppercase(),
                publisherName = custom.publisherTag.ifBlank { resolvedPub.name },
                publisherLogoUri = logoUri,
                croppedImagePath = custom.croppedImagePath,
                answerKeyVerified = answerKeyVerified,
                stemTop = if (hasCroppedStemImage) "" else custom.questionText,
                analyticsText = custom.questionText,
                learningOutcome = custom.learningOutcome,
                questionType = custom.questionType,
                bulletsOrSteps = emptyList(),
                stemBottom = if (hasCroppedStemImage) "" else "Buna göre doğru seçenek aşağıdakilerden hangisidir?",
                options = mapOf(
                    "A" to "A",
                    "B" to "B",
                    "C" to "C",
                    "D" to "D",
                    "E" to "E"
                ),
                correctOption = verifiedCorrectOption,
                difficulty = diff,
                visualType = QuestionVisualType.NONE,
                stepByStepSolution = custom.cleanSolutionSummary.ifBlank {
                    if (answerKeyVerified) {
                        "Kitap cevap anahtarına göre doğru yanıt $verifiedCorrectOption seçeneğidir."
                    } else {
                        "Bu sorunun cevap anahtarı doğrulanamadı."
                    }
                },
                bookId = custom.bookId,
                sourceQuestionId = custom.id,
                publisherId = custom.publisherId,
                sourceBookTitle = custom.sourceBookTitle.ifBlank { custom.bookOrTestName },
                sourceTestId = custom.sourceTestId,
                sourcePageIndex = custom.pageIndex,
                sourcePrintedQuestionNumber = custom.questionNumberInBook,
                subject = custom.subjectName,
                imageBase64 = custom.imageBase64,
                pdfFilePath = custom.pdfFilePath,
                pageIndex = custom.pageIndex,
                questionNumberInTest = custom.questionNumberInTest,
                normLeft = custom.normLeft,
                normTop = custom.normTop,
                normRight = custom.normRight,
                normBottom = custom.normBottom
            )
        }
    }

    /**
     * Strict 0-Hallucination check before generating a test.
     * Computes exact per-difficulty counts and full source-filtered counts (for nearest-level
     * completion) for the Interactive Test Creation Panel.
     */
    fun checkPoolAvailability(topic: YksTopic, level: TopicTestLevel): PoolAvailabilityCheck {
        val strictTopicPool = _userCustomQuestions.value.filter { cq ->
            matchesTopic(cq.subjectId, cq.topicId, cq.topicName, topic) &&
                repository.hasRecoverableQuestionImage(cq)
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

        val customBuilder: (Set<DifficultyLevel>, QuestionSourceFilter, Int, String?) -> YksTestItem? =
            { diffs, srcFilter, reqCount, publisherKey ->
                generateCustomFilteredTopicTest(
                    topic = topic,
                    level = level,
                    selectedDifficulties = diffs,
                    sourceFilter = srcFilter,
                    requestedQuestionCount = reqCount,
                    selectedPublisherKey = publisherKey
                )
            }
        val eligibleForLevel = if (level == TopicTestLevel.IKIZ) {
            collectWrongQuestionsFromResultsForTopic(topic)
        } else {
            allTopicQuestions.filter {
                it.answerKeyVerified && it.difficulty in level.recommendedDifficultyLevels()
            }
        }
        val countsForLevel = DifficultyLevel.entries.associateWith { diff ->
            eligibleForLevel.count { it.difficulty == diff }
        }
        // Filter choices must not disappear just because a difficulty is empty.
        val publisherChoices = PublisherQuestionMixer.options(allTopicQuestions)
        val sourceCountsForLevel = QuestionSourceFilter.entries.associateWith { filter ->
            if (level == TopicTestLevel.IKIZ) eligibleForLevel.size
            else collectQuestionsByTopicSourceAndDifficulty(
                topic, level.recommendedDifficultyLevels(), filter
            ).count { it.answerKeyVerified }
        }
        val questionCountForSelection: (Set<DifficultyLevel>, QuestionSourceFilter, String?) -> Int =
            { _, source, selectedPublisherKey ->
                val fromSource = if (level == TopicTestLevel.IKIZ) eligibleForLevel else
                    collectQuestionsByTopicSourceAndDifficulty(
                        topic, level.recommendedDifficultyLevels(), source
                    )
                val fromPublisher = PublisherQuestionMixer.filter(fromSource, selectedPublisherKey)
                LevelQuestionPolicy.maximumConstructibleCount(
                    level = level,
                    sourceQuestions = fromPublisher,
                    cap = AUTOMATIC_TEST_QUESTION_COUNT
                )
            }
        val questionCountForDifficulty: (DifficultyLevel, QuestionSourceFilter) -> Int = { diff, source ->
            if (level == TopicTestLevel.IKIZ) countsForLevel[diff] ?: 0
            else collectQuestionsByTopicSourceAndDifficulty(topic, setOf(diff), source)
                .count { it.answerKeyVerified && it.difficulty in level.recommendedDifficultyLevels() }
        }
        if (level == TopicTestLevel.IKIZ) {
            val message = if (eligibleForLevel.isEmpty()) {
                "Bu konuda yanlış cevapladığın, kaynak cevabı doğrulanmış soru henüz yok. İkiz bu soruları tekrar önüne getirir."
            } else {
                "Bu konudaki geçmiş testlerinde yanlış yaptığın ${eligibleForLevel.size} soru tekrar çözülmeye hazır."
            }
            return PoolAvailabilityCheck(
                canGenerate = eligibleForLevel.isNotEmpty(),
                availableQuestionCount = eligibleForLevel.size,
                reasonTitle = if (eligibleForLevel.isEmpty()) "Tekrarlanacak Yanlış Yok" else "İkiz • Yanlışlarım",
                reasonMessage = message,
                countsByDifficulty = countsForLevel,
                countsBySource = sourceCountsForLevel,
                matchingQuestions = eligibleForLevel,
                onGenerateCustomTest = customBuilder,
                questionCountForSelection = questionCountForSelection,
                questionCountForDifficulty = questionCountForDifficulty,
                availablePublishers = publisherChoices
            )
        }
        val maximumPossible = LevelQuestionPolicy.maximumConstructibleCount(
            level, eligibleForLevel, AUTOMATIC_TEST_QUESTION_COUNT
        )
        if (maximumPossible == 0) {
            val topicRecordCount = _userCustomQuestions.value.count { cq ->
                matchesTopic(cq.subjectId, cq.topicId, cq.topicName, topic)
            }
            val failureDetails = when {
                topicRecordCount == 0 -> "Bu konuya eşleşmiş soru kaydı bulunamadı. Kitabın konu etiketlerini kontrol et."
                strictTopicPool.isEmpty() -> "$topicRecordCount kayıt var ancak soru görselleri cihazda bulunamadı. Kitabı silmeden görselleri geri yüklemek gerekiyor."
                allTopicQuestions.none { it.answerKeyVerified } -> "${strictTopicPool.size} soru görseli var ancak cevap anahtarları doğrulanmamış. Yanlış cevapla sınav oluşturulamaz."
                else -> "Bu seviyeye uygun doğrulanmış soru bulunmuyor. Mevcut etiketler: " +
                    countsByDiff.filterValues { it > 0 }
                        .entries.joinToString { "${it.key.label}: ${it.value}" }
            }
            return PoolAvailabilityCheck(
                canGenerate = false,
                availableQuestionCount = 0,
                reasonTitle = "Bu Seviyede Soru Bulunamadı",
                reasonMessage = failureDetails,
                countsByDifficulty = countsForLevel,
                countsBySource = sourceCountsForLevel,
                matchingQuestions = emptyList(),
                onGenerateCustomTest = customBuilder,
                questionCountForSelection = questionCountForSelection,
                questionCountForDifficulty = questionCountForDifficulty,
                availablePublishers = publisherChoices
            )
        }
        return PoolAvailabilityCheck(
            canGenerate = true,
            availableQuestionCount = maximumPossible,
            reasonTitle = "Seviyeye Uygun Sorular",
            reasonMessage = if (level == TopicTestLevel.SEVIYE_TESPIT &&
                DifficultyLevel.entries.any { countsForLevel[it] == 0 }
            ) "Eksik zorluk etiketleriyle mevcut sorulardan pratik yapılabilir; beş seviye tamamlanmadan kesin seviye karnesi hesaplanmaz."
            else level.poolDistributionLabel,
            countsByDifficulty = countsForLevel,
            countsBySource = sourceCountsForLevel,
            matchingQuestions = eligibleForLevel,
            onGenerateCustomTest = customBuilder,
            questionCountForSelection = questionCountForSelection,
            questionCountForDifficulty = questionCountForDifficulty,
            availablePublishers = publisherChoices
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
        requestedQuestionCount: Int,
        verifiedTwinCandidates: List<YksQuestion>? = null,
        customTestTitle: String = "",
        selectedPublisherKey: String? = null
    ): YksTestItem? {
        if (requestedQuestionCount <= 0) return null
        val enforcedDifficulties = level.recommendedDifficultyLevels()
        val sourceCandidates = when {
            verifiedTwinCandidates != null -> verifiedTwinCandidates.filter {
                it.answerKeyVerified && (level == TopicTestLevel.IKIZ ||
                    it.difficulty in enforcedDifficulties)
            }
            level == TopicTestLevel.IKIZ -> collectWrongQuestionsFromResultsForTopic(topic)
            else -> collectQuestionsByTopicSourceAndDifficulty(
                topic, enforcedDifficulties, sourceFilter
            ).filter { it.answerKeyVerified }
        }
        // Server-side selection is strict even if a UI callback sends a
        // stale key. A chosen publisher can never leak another publication.
        val publisherFiltered = PublisherQuestionMixer.filter(
            sourceCandidates, selectedPublisherKey
        )
        if (publisherFiltered.isEmpty()) return null
        val strictCandidates = publisherFiltered.distinctBy { LevelQuestionPolicy.stableId(it) }

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

        val usedSignatures = allTestsInTopic.flatMap { it.questions }
            .mapTo(mutableSetOf()) { LevelQuestionPolicy.stableId(it) }
        val questionsToUse = LevelQuestionPolicy.select(
            level = level,
            sourceQuestions = strictCandidates,
            count = requestedQuestionCount,
            previouslyUsed = usedSignatures,
            mixSeed = (topic.id + level.name + nextTestNumber + selectedPublisherKey.orEmpty()).hashCode()
        )
        if (questionsToUse.isEmpty()) return null

        // Otomatik İsimlendirme: "[Konu Adı] - Test [X]" (Kesinlikle kitap adı kullanılmaz)
        val label = customTestTitle.trim().take(72)
        val shortTitle = if (label.isNotBlank())
            "${topic.name} • $label - Test $nextTestNumber"
        else "${topic.name} - Test $nextTestNumber"
        val fullTitle = shortTitle
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.saveCachedGeneratedTestSnapshot(uid, generatedTest)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showBanner("Test oluşturuldu ancak cihazdaki yedeği kaydedilemedi.")
                }
            }
        }
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
            val subjectName = subject?.name ?: topic.subjectId.ifBlank { "Ders" }
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
                            testSnapshotJson = TestSnapshotCodec.encode(
                                generatedTest,
                                includeImageFallback = false
                            )
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
        val defaultDiffs = level.recommendedDifficultyLevels()
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

    /**
     * Offline single-question twin: strictly another locally stored and
     * answer-key-verified question from the exact same subject and topic.
     * The original crop is never relabeled or offered as a new twin.
     */
    private fun previousTwinUseCounts(): Map<String, Int> {
        val generated = _generatedTopicTests.value.values.flatten()
            .flatMap { it.questions }
        val solved = _userTestResults.value.filter { it.isSolvedAttempt }
            .mapNotNull { TestSnapshotCodec.decode(it.testSnapshotJson) }
            .flatMap { it.questions }
        return (generated + solved)
            .mapNotNull { it.sourceQuestionId.takeIf(String::isNotBlank) }
            .groupingBy { it }.eachCount()
    }

    fun findGroundedTwinQuestion(source: YksQuestion, session: ActiveSolvingSession): TwinQuestionModalData {
        val candidate = GroundedTwinQuestionMatcher.select(
            source = source,
            subjectId = session.subjectId,
            topicId = session.topicId,
            topicName = session.topicName,
            pool = _userCustomQuestions.value,
            excludedIds = session.testItem.questions.mapTo(HashSet()) { it.sourceQuestionId },
            previousUseCounts = previousTwinUseCounts(),
            isUsable = repository::hasRecoverableQuestionImage
        ) ?: return TwinQuestionModalData(
            errorMessage = "Aynı konuda farklı ve cevap anahtarı doğrulanmış bir soru henüz yok. İkiz oluşturmak için kitaplığa yeni ZIP soru paketleri ekleyebilirsin."
        )

        val sourceName = candidate.sourceBookTitle.ifBlank {
            candidate.bookOrTestName.ifBlank { candidate.publisherTag }
        }
        val answer = candidate.correctOption.uppercase(java.util.Locale.ROOT)
        return TwinQuestionModalData(
            topicTitle = candidate.topicName,
            difficultyLabel = candidate.effectiveDifficultyLabel,
            questionStem = "",
            croppedImagePath = candidate.croppedImagePath,
            imageBase64 = candidate.imageBase64,
            sourceQuestionId = candidate.id,
            sourceDescription = sourceName +
                if (source.learningOutcome.isNotBlank() && candidate.learningOutcome.isNotBlank())
                    " • Aynı alt kazanım" else " • Aynı konu",
            options = listOf("A", "B", "C", "D", "E").associateWith { it },
            correctOption = answer,
            solutionExplanation = candidate.cleanSolutionSummary.ifBlank {
                "Basılı cevap anahtarında doğru seçenek $answer olarak doğrulanmıştır. Ayrıntılı çözüm bu kaynakta kayıtlı değil."
            }
        )
    }

    /**
     * Opens a fresh practice test from the exact Hata Frekansı branch the
     * student chose. Uses only recoverable, answer-key-verified ZIP questions.
     * Never silently widens a tagged sub-outcome/type to an unrelated topic.
     */
    fun startStrengtheningTestFromSession(
        session: ActiveSolvingSession,
        target: com.example.util.FrequencyPracticeTarget
    ) {
        val subject = SoruLabCatalog.allSubjects.firstOrNull {
            it.id.equals(session.subjectId, ignoreCase = true)
        } ?: SoruLabCatalog.allSubjects.firstOrNull {
            it.name.equals(session.subjectName, ignoreCase = true)
        }
        val topicName = target.topicName.trim().ifBlank { session.topicName }
        val topic = subject?.topics?.firstOrNull {
            it.name.trim().equals(topicName, ignoreCase = true)
        } ?: subject?.topics?.firstOrNull {
            it.id.equals(session.topicId, ignoreCase = true) &&
                it.name.trim().equals(topicName, ignoreCase = true)
        }
        if (topic == null) {
            showBanner("Bu dalın konusu kitaplıkta doğrulanamadı. Başka bir kazanım seç.")
            return
        }

        fun matchesTag(actual: String, selected: String?): Boolean =
            selected == null || actual.trim().equals(selected.trim(), ignoreCase = true)

        val available = collectQuestionsByTopicSourceAndDifficulty(
            topic, DifficultyLevel.entries.toSet(), QuestionSourceFilter.TUM_SORULAR
        ).filter { question ->
            question.answerKeyVerified &&
                matchesTag(question.learningOutcome, target.learningOutcome) &&
                matchesTag(question.questionType, target.questionType)
        }

        if (available.isEmpty()) {
            val levelName = target.questionType ?: target.learningOutcome ?: topic.name
            showBanner("'$levelName' için cevap anahtarı doğrulanmış, etiketli soru yok. Bu kazanıma ait ZIP soruları ekle.")
            return
        }

        // Generated-test snapshots track exposure even before the student
        // finishes a test; solved snapshots track actual repetition.
        val previousGenerated = _generatedTopicTests.value.values.flatten()
            .flatMap { it.questions }
        val previousSolved = _userTestResults.value.filter { it.isSolvedAttempt }
            .mapNotNull { TestSnapshotCodec.decode(it.testSnapshotJson) }
            .flatMap { it.questions }
        val fresh = FreshPracticeQuestionSelector.choose(
            candidates = available,
            generatedBefore = previousGenerated,
            solvedBefore = previousSolved,
            justSeen = session.testItem.questions,
            requestedCount = AUTOMATIC_TEST_QUESTION_COUNT
        )
        if (fresh.isEmpty()) {
            showBanner("Bu daldaki bütün sorular mevcut testte kullanıldı. Yeni bir Güçlen testi için aynı kazanımdan başka ZIP soruları ekle.")
            return
        }

        val practiceLabel = when {
            target.questionType != null ->
                "${target.learningOutcome.orEmpty()} • ${target.questionType}"
            target.learningOutcome != null -> target.learningOutcome
            else -> "Güçlen"
        }
        val test = generateCustomFilteredTopicTest(
            topic = topic,
            level = TopicTestLevel.IKIZ,
            selectedDifficulties = DifficultyLevel.entries.toSet(),
            sourceFilter = QuestionSourceFilter.TUM_SORULAR,
            requestedQuestionCount = fresh.size,
            verifiedTwinCandidates = fresh,
            customTestTitle = practiceLabel
        )
        if (test == null) {
            showBanner("Güçlen testi oluşturulamadı; mevcut soruların korundu.")
            return
        }
        startSolvingSession(topic, test, BoardViewMode.FULL_PAGE, isReviewMode = false)
    }

    fun startTwinTestFromSession(session: ActiveSolvingSession) {
        val subject = SoruLabCatalog.allSubjects.firstOrNull {
            it.id.equals(session.subjectId, ignoreCase = true)
        } ?: SoruLabCatalog.allSubjects.firstOrNull {
            it.name.equals(session.subjectName, ignoreCase = true)
        }
        val topic = subject?.topics?.firstOrNull {
            it.id.equals(session.topicId, ignoreCase = true)
        } ?: subject?.topics?.firstOrNull {
            it.name.equals(session.topicName, ignoreCase = true)
        }
        if (topic == null) {
            showBanner("Bu testin konusuna uygun ikiz test bulunamadı.")
            return
        }

        // This test directly re-presents exactly the source test's WRONG answers.
        val wrongQuestions = MistakeReviewPolicy.wrongAnswersOnly(
            session.testItem.questions, session.selectedAnswers
        )
        if (wrongQuestions.isEmpty()) {
            showBanner("Bu testte tekrar çözülecek yanlış cevaplanmış soru yok.")
            return
        }
        val twinTest = generateCustomFilteredTopicTest(
            topic = topic,
            level = TopicTestLevel.IKIZ,
            selectedDifficulties = DifficultyLevel.entries.toSet(),
            sourceFilter = QuestionSourceFilter.YANLIS_YAPTIKLARIM,
            requestedQuestionCount = wrongQuestions.size,
            verifiedTwinCandidates = wrongQuestions,
            customTestTitle = "Bu Testin Yanlışları"
        )
        if (twinTest == null) {
            showBanner("Yanlış soruların görüntüsü veya cevap anahtarı doğrulanamadı.")
            return
        }

        startSolvingSession(
            topic = topic,
            testItem = twinTest,
            initialViewMode = BoardViewMode.FULL_PAGE,
            isReviewMode = false
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
        val resolvedTestItem = hydrateSnapshotQuestionImages(testItem)
        if (resolvedTestItem.questions.isEmpty()) {
            showBanner("Bu testte çözülecek soru bulunamadı. Başka bir test seçin.")
            return
        }
        _dialogTestItem.value = null
        val subject = SoruLabCatalog.allSubjects.find { it.id == topic.subjectId }
        val examType = subject?.examType ?: _selectedExamType.value
        val subjectName = subject?.name ?: topic.subjectId.ifBlank { "Ders" }

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

        val safeQuestionIdx = initialQuestionIndex.coerceIn(
            0,
            (resolvedTestItem.questions.size - 1).coerceAtLeast(0)
        )

        _currentBookPageIndex.value = 0
        _activeSession.value = ActiveSolvingSession(
            testItem = resolvedTestItem,
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
        _activeTestQuestions.value = resolvedTestItem.questions

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
            val verifiedCorrectOption =
                record.correctOption.takeIf { it in listOf("A", "B", "C", "D", "E") }.orEmpty()
            val answerKeyVerified = record.answerKeyVerified && verifiedCorrectOption.isNotBlank()
            val fallbackQuestion = YksQuestion(
                id = "single_${record.id}",
                number = 1,
                badgeTitle = "${resolvedPub.name} • Ödev 1",
                topicHeader = matchedTopic.name.uppercase(),
                publisherStamp = resolvedPub.name.uppercase(),
                publisherName = resolvedPub.name,
                publisherLogoUri = record.publisherLogoUri.ifBlank { resolvedPub.customLogoPath },
                croppedImagePath = record.croppedImagePath,
                answerKeyVerified = answerKeyVerified,
                stemTop = if (hasCroppedImg) "" else record.questionText,
                analyticsText = record.questionText,
                bulletsOrSteps = emptyList(),
                stemBottom = if (hasCroppedImg) "" else "Doğru seçeneği işaretleyiniz.",
                options = mapOf(
                    "A" to "A",
                    "B" to "B",
                    "C" to "C",
                    "D" to "D",
                    "E" to "E"
                ),
                correctOption = verifiedCorrectOption,
                difficulty = diffLevel,
                visualType = QuestionVisualType.NONE,
                stepByStepSolution = record.cleanSolutionSummary.ifBlank {
                    if (answerKeyVerified) {
                        "Doğru cevap $verifiedCorrectOption seçeneğidir."
                    } else {
                        "Bu sorunun cevap anahtarı doğrulanamadı."
                    }
                },
                bookId = record.bookId,
                sourceQuestionId = record.id,
                publisherId = record.publisherId,
                sourceBookTitle = record.sourceBookTitle.ifBlank { record.bookOrTestName },
                sourceTestId = record.sourceTestId,
                sourcePageIndex = record.pageIndex,
                sourcePrintedQuestionNumber = record.questionNumberInBook,
                subject = record.subjectName,
                imageBase64 = record.imageBase64,
                pdfFilePath = record.pdfFilePath,
                pageIndex = record.pageIndex,
                questionNumberInTest = record.questionNumberInTest,
                normLeft = record.normLeft,
                normTop = record.normTop,
                normRight = record.normRight,
                normBottom = record.normBottom
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
        val level = _selectedTestLevel.value
        val eligibleRecords = records
            .filter { it.difficultyLevel5 in level.recommendedDifficultyLevels() }
            .sortedBy {
                if (it.questionNumberInBook > 0) it.questionNumberInBook else Int.MAX_VALUE
            }
        if (eligibleRecords.isEmpty()) {
            showBanner("Seçili ${level.displayTitle} seviyesine uygun kitap sorusu bulunamadı.")
            return
        }
        val firstRecord = eligibleRecords.first()
        val matchedSubject = SoruLabCatalog.allSubjects.find { it.id == firstRecord.subjectId }
            ?: SoruLabCatalog.allSubjects.find { it.name.equals(firstRecord.subjectName, ignoreCase = true) }
            ?: SoruLabCatalog.allSubjects.first()
        val matchedTopic = matchedSubject.topics.find { it.id == firstRecord.topicId }
            ?: matchedSubject.topics.find { it.name.equals(firstRecord.topicName, ignoreCase = true) }
            ?: matchedSubject.topics.first()

        val effectiveRecords = if (questionsPerTest > 0) {
            val chunks = eligibleRecords.chunked(questionsPerTest)
            chunks.getOrNull(selectedChunkIndex) ?: chunks.firstOrNull().orEmpty()
        } else {
            eligibleRecords.take(AUTOMATIC_TEST_QUESTION_COUNT)
        }
        if (effectiveRecords.isEmpty()) {
            showBanner("Seçili seviyede bu test için uygun soru bulunamadı.")
            return
        }

        val testQuestions = effectiveRecords.mapIndexed { idx, record ->
            val resolvedPub = SoruLabPublisherPresets.resolvePublisher(record.publisherTag, _customPublishers.value)
            val diffLevel = record.difficultyLevel5
            val hasCroppedImg = record.croppedImagePath.isNotBlank()
            val verifiedCorrectOption =
                record.correctOption.takeIf { it in listOf("A", "B", "C", "D", "E") }.orEmpty()
            val answerKeyVerified = record.answerKeyVerified && verifiedCorrectOption.isNotBlank()
            YksQuestion(
                id = "book_${record.id}",
                number = idx + 1,
                badgeTitle = "${resolvedPub.name} • ${idx + 1}. Soru",
                topicHeader = record.topicName.ifBlank { matchedTopic.name }.uppercase(),
                publisherStamp = resolvedPub.name.uppercase(),
                publisherName = resolvedPub.name,
                publisherLogoUri = record.publisherLogoUri.ifBlank { resolvedPub.customLogoPath },
                croppedImagePath = record.croppedImagePath,
                answerKeyVerified = answerKeyVerified,
                stemTop = if (hasCroppedImg) "" else record.questionText,
                analyticsText = record.questionText,
                bulletsOrSteps = emptyList(),
                stemBottom = if (hasCroppedImg) "" else "Doğru seçeneği işaretleyiniz.",
                options = mapOf(
                    "A" to "A",
                    "B" to "B",
                    "C" to "C",
                    "D" to "D",
                    "E" to "E"
                ),
                correctOption = verifiedCorrectOption,
                difficulty = diffLevel,
                visualType = QuestionVisualType.NONE,
                stepByStepSolution = record.cleanSolutionSummary.ifBlank {
                    if (answerKeyVerified) {
                        "Doğru cevap $verifiedCorrectOption seçeneğidir."
                    } else {
                        "Bu sorunun cevap anahtarı doğrulanamadı."
                    }
                },
                bookId = record.bookId,
                sourceQuestionId = record.id,
                publisherId = record.publisherId,
                sourceBookTitle = record.sourceBookTitle.ifBlank { record.bookOrTestName },
                sourceTestId = record.sourceTestId,
                sourcePageIndex = record.pageIndex,
                sourcePrintedQuestionNumber = record.questionNumberInBook,
                subject = record.subjectName,
                imageBase64 = record.imageBase64,
                pdfFilePath = record.pdfFilePath,
                pageIndex = record.pageIndex,
                questionNumberInTest = record.questionNumberInTest,
                normLeft = record.normLeft,
                normTop = record.normTop,
                normRight = record.normRight,
                normBottom = record.normBottom
            )
        }

        val cleanTitle = "${matchedTopic.name} - Test ${selectedChunkIndex + 1}"
        val bookTest = YksTestItem(
            id = "book_test_${matchedTopic.id}_${selectedChunkIndex + 1}_${System.currentTimeMillis() % 100000}",
            topicId = matchedTopic.id,
            title = cleanTitle,
            fullTitle = cleanTitle,
            difficulty = level.defaultDifficulty,
            difficultyPosition = level.difficultyPosition,
            defaultCompleted = false,
            starRating = 0,
            level = level,
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
        val savedTest = TestSnapshotCodec.decode(record.testSnapshotJson)
            ?.takeIf { it.id == record.testId }
            ?.let(::hydrateSnapshotQuestionImages)
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
        _activeTestQuestions.value = emptyList()
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

    fun selectBookPageIndex(index: Int) {
        _currentBookPageIndex.value = index.coerceAtLeast(0)
    }

    fun selectQuestionIndex(index: Int) {
        val cur = _activeSession.value ?: return
        if (cur.testItem.questions.isEmpty()) return
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
            testSnapshotJson = TestSnapshotCodec.encode(
                cur.testItem,
                includeImageFallback = false
            ),
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
        _activeTestQuestions.value = emptyList()
        _selectedTopic.value = null
        _selectedTab.value = MainNavTab.ISTATISTIKLERIM
    }

    fun selectDrawingTool(tool: DrawingTool) {
        val oldTool = _activeTool.value
        if (oldTool != tool) {
            if (oldTool == DrawingTool.ERASER) {
                eraserStrokeSizeIndex = _selectedStrokeSizeIndex.value
            } else {
                penStrokeSizeIndex = _selectedStrokeSizeIndex.value
            }
            _selectedStrokeSizeIndex.value =
                if (tool == DrawingTool.ERASER) eraserStrokeSizeIndex else penStrokeSizeIndex
        }
        _activeTool.value = tool
    }

    fun selectStrokeSize(index: Int) {
        val safe = index.coerceIn(0, 2)
        _selectedStrokeSizeIndex.value = safe
        if (_activeTool.value == DrawingTool.ERASER) eraserStrokeSizeIndex = safe
        else penStrokeSizeIndex = safe
    }

    fun selectPenColor(colorHex: Long) {
        _selectedPenColorHex.value = colorHex
        if (_activeTool.value == DrawingTool.ERASER || _activeTool.value == DrawingTool.HAND_PAN) {
            selectDrawingTool(DrawingTool.PEN)
        }
    }

    fun currentCanvasKey(): String {
        val cur = _activeSession.value ?: return "default"
        return if (cur.viewMode == BoardViewMode.FULL_PAGE) {
            com.example.util.StylusInputPolicy.canvasKey(
                cur.testItem.id, _currentBookPageIndex.value
            )
        } else {
            val q = cur.testItem.questions.getOrNull(cur.currentQuestionIndex)
            "q_${q?.id ?: cur.currentQuestionIndex}"
        }
    }

    fun addStrokeToCurrentCanvas(
        points: List<Offset>,
        pressureSamples: List<Float> = emptyList()
    ) = addStrokeToCanvas(currentCanvasKey(), points, pressureSamples)

    fun addStrokeToCanvas(
        canvasKey: String,
        points: List<Offset>,
        pressureSamples: List<Float> = emptyList()
    ) {
        if (points.size < 2 || canvasKey.isBlank()) return
        val key = canvasKey
        val tool = _activeTool.value
        val currentList = _boardStrokes.value[key].orEmpty()

        if (tool == DrawingTool.ERASER) {
            val eraserWidthPx = listOf(18f, 38f, 68f)
                .getOrElse(_selectedStrokeSizeIndex.value) { 38f }
            val eraseRadius = eraserWidthPx / 2f + 4f
            val remaining = currentList.filter { stroke ->
                stroke.isEraser || stroke.points.none { pt ->
                    points.any { erasePt -> (pt - erasePt).getDistance() < eraseRadius }
                }
            }
            val finalStrokes = if (remaining.any { !it.isEraser }) {
                val eraserStroke = DrawStroke(
                    points = points,
                    colorHex = 0x00000000L,
                    strokeWidthPx = eraserWidthPx,
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
            isHighlighter = isHighlighterTool,
            pressureSamples = if (finalPoints.size == points.size &&
                pressureSamples.size == points.size
            ) {
                pressureSamples.map { if (it.isFinite()) it.coerceIn(0f, 1.5f) else 1f }
            } else emptyList()
        )
        val updatedStrokes = _boardStrokes.value + (key to (currentList + newStroke))
        _boardStrokes.value = updatedStrokes
        repository.saveCachedBoardStrokes(effectiveUserId() ?: "device_yks_student_tr", updatedStrokes)
    }

    fun clearCurrentCanvasStrokes() = clearCanvasStrokes(currentCanvasKey())

    fun clearCanvasStrokes(canvasKey: String) {
        if (canvasKey.isBlank()) return
        val updatedStrokes = _boardStrokes.value - canvasKey
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
        repository.saveCustomPublishers(updated, effectiveUserId())
        val signedInUid = _currentUser.value?.uid
        if (signedInUid == null) {
            showBanner("‘$cleanName’ yayını cihaza eklendi. Bulut yedeklemesi için Google hesabınla oturum aç.")
        } else {
            viewModelScope.launch {
                try {
                    repository.saveCloudCustomPublisher(signedInUid, newPub)
                    showBanner("‘$cleanName’ yayını ve logosu buluta yedeklendi.")
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    showBanner("‘$cleanName’ yayını cihaza kaydedildi; bulut yedeklemesi başarısız oldu.")
                }
            }
        }
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
        val requestedSubject = SoruLabCatalog.allSubjects.firstOrNull {
            it.examType.name.equals(examType, ignoreCase = true) &&
                it.name.equals(
                    subjectName
                        .replace("TYT ", "", ignoreCase = true)
                        .replace("AYT ", "", ignoreCase = true)
                        .trim(),
                    ignoreCase = true
                )
        }
        if (requestedSubject == null) {
            showBanner("Sorunun dersi müfredatla eşleştirilemedi; soru yanlış derse kaydedilmesin diye işlem durduruldu.")
            return
        }
        val explicitTopicName = StudioImageAndPdfEngine.detectTopicNameFromText(
            examType = examType,
            subjectName = requestedSubject.name,
            text = "$topicName\n$questionText"
        )
        val explicitTopic = explicitTopicName?.let { name ->
            requestedSubject.topics.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?.let { topic -> requestedSubject to topic }
        }
        val classifiedTopic = explicitTopic ?: SoruLabCatalog.classifyQuestionTopicFromText(
            examTypeHint = examType,
            subjectNameHint = requestedSubject.name,
            questionText = questionText,
            suggestedTopicName = topicName,
            trustSuggestedTopic = true
        )
        val matchedSubject = classifiedTopic?.first ?: requestedSubject
        val matchedTopic = classifiedTopic?.second ?: matchedSubject?.topics?.firstOrNull {
            topicName.isNotBlank() && (
                it.name.equals(topicName, ignoreCase = true) ||
                topicName.contains(it.name, ignoreCase = true) ||
                it.name.contains(topicName, ignoreCase = true)
            )
        }
        if (matchedSubject == null || matchedTopic == null) {
            showBanner("Soru konusu müfredatla eşleştirilemedi; soru kaydedilmedi.")
            return
        }

        val resolvedExamType = matchedSubject.examType.name
        val subjectId = matchedSubject.id
        val resolvedSubjectName = matchedSubject.name
        val topicId = matchedTopic.id
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
            correctOption = correctOption.uppercase().takeIf { it in listOf("A", "B", "C", "D", "E") }.orEmpty(),
            questionText = questionText.ifBlank { "PDF'ten kırpılan YKS sorusu" }.take(1800),
            publisherTag = publisherTag.ifBlank { resolvedPublisher.name }.take(100),
            publisherId = resolvedPublisher.id,
            sourceBookTitle = bookOrTestName,
            aiSolutionSummary = packedSolution,
            cropAspectRatio = cropAspectRatio.coerceIn(0.3, 4.5),
            questionNumberInTest = questionNumberInBook
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
        bookCoverPath: String,
        onSaved: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val cleanItems = extractedItems
            .filter { item ->
                item.croppedImagePath.isNotBlank() ||
                    item.imageBase64.isNotBlank() ||
                    item.questionText.trim().length >= 12
            }

        if (cleanItems.isEmpty() || cleanItems.size != extractedItems.size) {
            val msg = if (cleanItems.isEmpty()) "PDF içinde aktarılabilecek soru bulunamadı."
                else "${extractedItems.size - cleanItems.size} soru görsel veya metin içermiyor. Eksik kitabı kaydedilmiş göstermemek için aktarım durduruldu."
            showBanner(msg)
            onSaved(false, msg)
            return
        }

        val uid = effectiveUserId() ?: "device_yks_student_tr"
        val resolvedPublisher = SoruLabPublisherPresets.resolvePublisher(publisherName, _customPublishers.value)
        val finalPubName = publisherName.ifBlank { resolvedPublisher.name }.take(100)
        val finalLogoUri = publisherLogoUri.ifBlank { resolvedPublisher.customLogoPath }

        viewModelScope.launch(Dispatchers.Default) {
            try {
            val subjectMemo = HashMap<String, com.example.data.YksSubject>(32)
            val batchTimestamp = System.currentTimeMillis()

            val newRecords = ArrayList<CustomQuestionRecord>(cleanItems.size)
            var imageRekeyFailureCount = 0
            val existingBySource = com.example.util.indexPdfQuestionsForRepair(_userCustomQuestions.value)
            for ((idx, item) in cleanItems.withIndex()) {
                // Reprocessing improves geometry and source headings; neither is an
                // immutable identity. Reuse an unambiguous printed question's ID.
                // Two different tests may use the same question number on one
                // PDF page. Reuse an old record only when the printed test
                // identity agrees; otherwise ZIP import can overwrite a
                // question from an unrelated test.
                val previous = existingBySource[
                    Triple(item.bookId, item.pageIndex, item.questionNumberInTest)
                ]?.takeIf { it.sourceTestId == item.sourceTestId }
                val identity = "${item.bookId}|${item.sourceTestId}|${item.pageIndex}|${item.questionNumberInTest}|${item.boxNormalized}"
                val recordId = previous?.id ?: if (item.bookId.isNotBlank()) "cq_" + java.util.UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8)) else "cq_${batchTimestamp}_${idx}_${(100..999).random()}"
                // Page crops are already durable files under filesDir/questions.
                // Copying/renaming hundreds of JPEGs on every import causes a
                // long extra pass and can interrupt a large book save.
                val canonicalImagePath = item.croppedImagePath
                    .takeIf { path -> path.isNotBlank() && java.io.File(path).isFile }
                    .orEmpty()
                if (item.croppedImagePath.isNotBlank() && canonicalImagePath.isBlank()) {
                    imageRekeyFailureCount++
                }
                val requestedSubject = SoruLabCatalog.allSubjects.firstOrNull {
                    it.examType.name.equals(item.examType, ignoreCase = true) &&
                        it.name.equals(
                            item.subjectName
                                .replace("TYT ", "", ignoreCase = true)
                                .replace("AYT ", "", ignoreCase = true)
                                .trim(),
                            ignoreCase = true
                        )
                }
                // The PDF's printed chapter heading is authoritative.
                // Classifying the OCR of a whole scanned page can pick a word from
                // an unrelated question and silently place a Botany question into
                // Sinir Sistemi (or vice versa). For identified printed tests
                // never override their chapter with keyword-based classification.
                val sourceSection = item.sourceTestId.substringBefore('~')
                    .takeIf { item.sourceTestId.contains('~') }
                    .orEmpty()
                val sourceTopic = requestedSubject?.topics?.firstOrNull { topic ->
                    PdfScopedAnswerKeys.normalize(topic.name) ==
                        PdfScopedAnswerKeys.normalize(sourceSection)
                }
                val explicitTopicName = if (sourceTopic == null) {
                    StudioImageAndPdfEngine.detectTopicNameFromText(
                        examType = item.examType,
                        subjectName = requestedSubject?.name ?: item.subjectName,
                        text = "${item.topicName}\n${item.questionText}"
                    )
                } else null
                val explicitTopic = sourceTopic?.let { requestedSubject!! to it }
                    ?: explicitTopicName?.let { name ->
                        requestedSubject?.let { subject ->
                            subject.topics.firstOrNull {
                                it.name.equals(name, ignoreCase = true)
                            }?.let { topic -> subject to topic }
                        }
                    }
                val classifiedTopic = explicitTopic ?: requestedSubject?.let { subject ->
                    SoruLabCatalog.classifyQuestionTopicFromText(
                        examTypeHint = item.examType,
                        subjectNameHint = subject.name,
                        questionText = item.questionText,
                        suggestedTopicName = item.topicName,
                        trustSuggestedTopic = true
                    ) ?: SoruLabCatalog.classifyQuestionTopicFromText(
                        examTypeHint = item.examType,
                        subjectNameHint = subject.name,
                        questionText = item.questionText
                    )
                }
                val subKey = "${item.examType}|${item.subjectName}"
                val matchedSubject = classifiedTopic?.first ?: subjectMemo[subKey] ?: requestedSubject
                if (matchedSubject != null) {
                    subjectMemo[subKey] = matchedSubject
                }

                val matchedTopic = classifiedTopic?.second ?: matchedSubject?.topics?.firstOrNull {
                    item.topicName.isNotBlank() && (
                        it.name.equals(item.topicName, ignoreCase = true) ||
                        item.topicName.contains(it.name, ignoreCase = true) ||
                        it.name.contains(item.topicName, ignoreCase = true)
                    )
                }

                val resolvedExamType = matchedSubject?.examType?.name ?: item.examType
                val subjectId = matchedSubject?.id.orEmpty()
                // ZIP imports can carry authentic subject/topic labels for a
                // publisher's chapter even when that chapter is not an exact
                // match for the built-in catalog. Never silently erase them.
                val resolvedSubjectName = matchedSubject?.name
                    ?.takeIf { it.isNotBlank() } ?: item.subjectName
                val topicId = matchedTopic?.id.orEmpty()
                val resolvedTopicName = matchedTopic?.name
                    ?.takeIf { it.isNotBlank() } ?: item.topicName

                val effectiveCover = bookCoverPath.ifBlank { item.bookCoverPath }
                val packedSolution = CustomQuestionRecord.packSolutionWithMeta(
                    solutionText = item.solutionSummary,
                    targetLevel = item.targetLevel,
                    croppedImagePath = canonicalImagePath,
                    publisherLogoUri = finalLogoUri,
                    answerKeyVerified = item.answerKeyMatched,
                    questionNumberInBook = item.questionNumber,
                    bookOrTestName = bookOrTestName,
                    bookCoverPath = effectiveCover,
                    fiveTierDifficulty = item.difficulty,
                    sourceTestId = item.sourceTestId
                )

                val firestoreCompatDiff = when {
                    item.difficulty.equals("Kolay", ignoreCase = true) -> "Kolay"
                    item.difficulty.equals("Zor", ignoreCase = true) || item.difficulty.equals("Çok Zor", ignoreCase = true) -> "Zor"
                    else -> "Orta"
                }

                newRecords.add(
                    CustomQuestionRecord(
                        id = recordId,
                        userId = uid,
                        createdAt = previous?.createdAt,
                        examType = resolvedExamType,
                        subjectId = subjectId,
                        subjectName = resolvedSubjectName,
                        topicId = topicId,
                        topicName = resolvedTopicName,
                        difficulty = firestoreCompatDiff,
                        correctOption = item.correctOption.uppercase().takeIf { it in listOf("A", "B", "C", "D", "E") }.orEmpty(),
                        questionText = item.questionText.take(1800),
                        learningOutcome = item.learningOutcome,
                        questionType = item.questionType,
                        publisherTag = finalPubName,
                        publisherId = resolvedPublisher.id,
                        sourceBookTitle = bookOrTestName,
                        aiSolutionSummary = packedSolution,
                        cropAspectRatio = item.cropAspectRatio.coerceIn(0.3, 4.5),
                        bookId = item.bookId,
                        // Use the persisted crop rather than duplicating every
                        // JPEG as a base64 string in the local JSON and cloud.
                        imageBase64 = if (canonicalImagePath.isNotBlank() &&
                            java.io.File(canonicalImagePath).isFile
                        ) "" else item.imageBase64,
                        pdfFilePath = item.pdfFilePath,
                        pageIndex = item.pageIndex,
                        questionNumberInTest = item.questionNumberInTest,
                        normLeft = item.normLeft,
                        normTop = item.normTop,
                        normRight = item.normRight,
                        normBottom = item.normBottom
                    )
                )
            }

            // One HashSet membership check per existing record: adding the 50th
            // textbook must not compare every old question with every new one.
            // Snapshot the old pool once before constructing the merged list.
            val cachedBeforeImport = _userCustomQuestions.value
            val importedQuestionIds = newRecords.mapTo(HashSet<String>(newRecords.size)) { it.id }
            val updatedList = ArrayList<CustomQuestionRecord>(newRecords.size + cachedBeforeImport.size)
            updatedList.addAll(newRecords)
            updatedList.addAll(cachedBeforeImport.filterNot { it.id in importedQuestionIds })

            // A successful scan is not a successful import until an atomic
            // file write and read-back both succeed.
            val durableList = try {
                withContext(Dispatchers.IO) {
                    val restored = repository.saveCachedCustomQuestions(uid, updatedList)
                    val ids = restored.mapTo(HashSet(restored.size)) { it.id }
                    check(newRecords.all { it.id in ids }) {
                        "Yerel kitaplık yazıldı fakat doğrulanamadı."
                    }
                    restored
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SoruLabPdfImport", "Local PDF book commit failed", e)
                val msg = "Sorular bulundu fakat kalıcı kaydetme tamamlanamadı. " +
                    "Depolama alanını kontrol edip tekrar deneyin. Önizleme korunuyor."
                withContext(Dispatchers.Main) {
                    showBanner(msg)
                    onSaved(false, msg)
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                if ((effectiveUserId() ?: com.example.util.PdfLibraryAccount.GUEST_CACHE_UID) == uid) {
                    _userCustomQuestions.value = durableList
                    syncPdfPoolToPracticeTests(durableList)
                }
                val msg = "'$bookOrTestName' kitaplığa eklendi: ${newRecords.size} soru. " +
                    if (imageRekeyFailureCount == 0) "Yerel kayıt doğrulandı."
                    else "$imageRekeyFailureCount görselin dosya adı kontrol edilmeli."
                showBanner(msg)
                onSaved(true, msg)
            }

            val firebaseUid = _currentUser.value?.uid
            if (firebaseUid != null && firebaseUid == uid) {
                launch(Dispatchers.IO) {
                    val cloudBatch = newRecords.map { it.copy(userId = firebaseUid) }
                    try {
                        repository.saveCustomQuestionsBatch(cloudBatch)
                        withContext(Dispatchers.Main) {
                            showBanner(
                                "'$bookOrTestName' soruları bulut hesabınızla eşitlendi."
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            showBanner(
                                "Sorular cihazda kaydedildi; buluta eşitlenemedi. " +
                                    cloudSaveProblem(e)
                            )
                        }
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    showBanner("Sorular cihazınıza kaydedildi. Bulut eşitlemesi için hesabınızla giriş yapın.")
                }
            }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SoruLabPdfImport", "Unexpected PDF import commit failure", e)
                val msg = "Kitaplık kaydı tamamlanamadı: ${e.javaClass.simpleName}. Önizleme korunuyor."
                withContext(Dispatchers.Main) {
                    showBanner(msg)
                    onSaved(false, msg)
                }
            }
        }
    }

    /**
     * Explicit user-approved recovery: never silently expose the guest question
     * bank to a signed-in account. Existing cloud/local questions are kept.
     */
    fun recoverPreviousDeviceQuestions() {
        val uid = effectiveUserId() ?: com.example.util.PdfLibraryAccount.GUEST_CACHE_UID
        val visibleBefore = _userCustomQuestions.value.filter { it.userId == uid }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ownedCache = repository.loadCachedCustomQuestions(uid)
                val guestRecords = if (uid == com.example.util.PdfLibraryAccount.GUEST_CACHE_UID)
                    emptyList() else repository.loadCachedCustomQuestions(
                    com.example.util.PdfLibraryAccount.GUEST_CACHE_UID
                ).map { it.copy(userId = uid) }
                val recovered = (visibleBefore + ownedCache + guestRecords)
                    .distinctBy { it.id }
                if (recovered.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        showBanner("Bu cihazda kurtarılabilecek eski soru kaydı bulunamadı. Orijinal ZIP'leri tekrar aktarabilirsin.")
                    }
                    return@launch
                }
                val durable = repository.saveCachedCustomQuestions(uid, recovered)
                val newlyVisible = durable.size - visibleBefore.size
                withContext(Dispatchers.Main) {
                    if ((effectiveUserId() ?: com.example.util.PdfLibraryAccount.GUEST_CACHE_UID) == uid) {
                        _userCustomQuestions.value = durable
                        syncPdfPoolToPracticeTests(durable)
                        showBanner(
                            if (newlyVisible > 0)
                                "$newlyVisible soru cihazdaki önceki kayıtlardan kurtarıldı. Toplam: ${durable.size}."
                            else "Yerel kitaplık doğrulandı: ${durable.size} soru. Eksikse eski ZIP dosyaları gerekli."
                        )
                    }
                }
                val cloudUid = _currentUser.value?.uid
                if (cloudUid == uid) {
                    try {
                        val cloud = repository.getUserCustomQuestionsList(uid)
                        val cloudIds = cloud.mapTo(HashSet(cloud.size)) { it.id }
                        val missingFromCloud = durable.filter { it.id !in cloudIds }
                        if (missingFromCloud.isNotEmpty()) {
                            repository.saveCustomQuestionsBatch(missingFromCloud)
                        }
                        val confirmed = repository.getUserCustomQuestionsList(uid)
                            .mapTo(HashSet()) { it.id }
                        val missingCount = durable.count { it.id !in confirmed }
                        withContext(Dispatchers.Main) {
                            if (missingCount == 0) {
                                showBanner("${durable.size} soru yerelde doğrulandı; bulut soru kayıtları da eşitlendi. Görseller için orijinal ZIP'leri sakla.")
                            } else {
                                showBanner("$missingCount soru henüz bulutta doğrulanamadı; cihazdaki kopyaları korundu.")
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) {
                            showBanner("Sorular cihazda korundu; bulut eşitlemesi tamamlanamadı. İnterneti kontrol et.")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showBanner("Eski soru kayıtları incelenirken hata oluştu. Mevcut kitaplık değiştirilmedi.")
                }
            }
        }
    }

    /** Kullanıcının kaydedilmiş bir sorunun dersini veya konusunu düzeltmesini sağlar. */
    fun updateQuestionTopic(
        questionId: String,
        newSubjectId: String,
        newSubjectName: String,
        newTopicId: String,
        newTopicName: String
    ) {
        val current = _userCustomQuestions.value
        val updated = current.map { q ->
            if (q.id == questionId) {
                q.copy(
                    subjectId = newSubjectId,
                    subjectName = newSubjectName,
                    topicId = newTopicId,
                    topicName = newTopicName
                )
            } else q
        }
        _userCustomQuestions.value = updated
        val uid = effectiveUserId() ?: "device_yks_student_tr"
        saveCustomQuestionsCacheAsync(uid, updated)
        syncPdfPoolToPracticeTests(updated)
        showBanner("Soru konusu “$newTopicName” olarak güncellendi.")
        val firebaseUid = _currentUser.value?.uid ?: return
        val updatedRecord = updated.firstOrNull { it.id == questionId } ?: return
        viewModelScope.launch {
            try {
                repository.saveCustomQuestion(updatedRecord.copy(userId = firebaseUid))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                showBanner("Konu cihazda güncellendi; buluta kaydedilemedi. Bağlantınızı kontrol edip yeniden deneyin.")
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
                fiveTierDifficulty = target.effectiveDifficultyLabel,
                sourceTestId = target.sourceTestId
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
        val imagePaths = _userCustomQuestions.value
            .filter { it.id in idSet }
            .mapNotNull { it.croppedImagePath.takeIf(String::isNotBlank) }
        val removedBookIds = _userCustomQuestions.value
            .filter { it.id in idSet }
            .mapNotNull { it.bookId.takeIf(String::isNotBlank) }
            .toSet()
        val remainingBookIds = _userCustomQuestions.value
            .filterNot { it.id in idSet }
            .mapNotNull { it.bookId.takeIf(String::isNotBlank) }
            .toSet()
        val bookPdfPaths = _userCustomQuestions.value
            .filter { it.id in idSet && it.bookId in removedBookIds && it.bookId !in remainingBookIds }
            .mapNotNull { it.pdfFilePath.takeIf(String::isNotBlank) }
        val updated = _userCustomQuestions.value.filterNot { it.id in idSet }
        _userCustomQuestions.value = updated
        saveCustomQuestionsCacheAsync(uid, updated, allowRemovals = true)
        syncPdfPoolToPracticeTests(updated)
        viewModelScope.launch(Dispatchers.IO) {
            var failedImageDeletes = 0
            var failedBookDeletes = 0
            try {
                failedImageDeletes = repository.deleteLocalQuestionImages(imagePaths)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failedImageDeletes = imagePaths.size
            }
            try {
                failedBookDeletes = repository.deleteLocalBookPdfs(bookPdfPaths)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failedBookDeletes = bookPdfPaths.size
            }
            val cloudDeleteSucceeded = try {
                repository.deleteCustomQuestionsBatch(questionIds)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                when {
                    !cloudDeleteSucceeded ->
                        showBanner("Cihazdaki soru kayıtları kaldırıldı ancak bulut listesinden silinemedi.")
                    failedBookDeletes > 0 ->
                        showBanner("Sorular kaldırıldı ancak $failedBookDeletes kaynak PDF dosyası silinemedi.")
                    failedImageDeletes > 0 ->
                        showBanner("$failedImageDeletes soru görseli cihazdan silinemedi.")
                    else ->
                        showBanner("Kaynak kitap ve soruları Kitaplığınızdan kaldırıldı.")
                }
            }
        }
    }

    fun deleteCustomQuestion(questionId: String) {
        val uid = effectiveUserId() ?: "device_yks_student_tr"
        val imagePath = _userCustomQuestions.value
            .firstOrNull { it.id == questionId }
            ?.croppedImagePath
        val updated = _userCustomQuestions.value.filterNot { it.id == questionId }
        _userCustomQuestions.value = updated
        saveCustomQuestionsCacheAsync(uid, updated)
        syncPdfPoolToPracticeTests(updated)
        viewModelScope.launch(Dispatchers.IO) {
            var imageDeleteFailed = false
            try {
                if (!imagePath.isNullOrBlank()) {
                    imageDeleteFailed = repository.deleteLocalQuestionImages(listOf(imagePath)) > 0
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                imageDeleteFailed = true
            }
            try {
                repository.deleteCustomQuestion(questionId)
                withContext(Dispatchers.Main) {
                    showBanner(
                        if (imageDeleteFailed) {
                            "Soru havuzdan silindi ancak görsel dosyası cihazdan silinemedi."
                        } else {
                            "Soru havuzdan silindi."
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showBanner("Soru silinirken bir hata oluştu; tekrar deneyin.")
                }
            }
        }
    }

    fun clearAllUserData() {
        val uid = effectiveUserId() ?: "device_yks_student_tr"
        val oldResults = allCloudTestRecords.toList()
        val oldCustomQuestions = _userCustomQuestions.value
        val oldCustomQuestionIds = oldCustomQuestions.mapNotNull { it.id.takeIf { id -> id.isNotBlank() } }

        allCloudTestRecords = emptyList()
        _generatedTopicTests.value = emptyMap()
        _deletedTestIds.value = emptySet()
        _userTestResults.value = emptyList()
        _userCustomQuestions.value = emptyList()
        _customPublishers.value = emptyList()
        _boardStrokes.value = emptyMap()

        viewModelScope.launch(Dispatchers.IO) {
            val failedImageDeletes = repository.deleteAllLocalQuestionImages()
            val failedBookDeletes = repository.deleteAllLocalBookPdfs()
            if (failedImageDeletes > 0 || failedBookDeletes > 0) {
                withContext(Dispatchers.Main) {
                    showBanner(
                        "$failedImageDeletes soru görseli ve $failedBookDeletes PDF dosyası cihazdan silinemedi."
                    )
                }
            }
            repository.clearAllLocalUserData(uid)
            if (_currentUser.value != null) {
                try {
                    repository.deleteCloudCustomPublishers(uid)
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        showBanner("Cihazdaki veriler temizlendi ancak yayın logoları buluttan silinemedi.")
                    }
                }
            }
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
