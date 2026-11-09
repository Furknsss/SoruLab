package com.example.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.AtomicFile
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import com.example.R
import com.example.util.ensureFirebaseInitialized
import com.example.util.StudioImageAndPdfEngine
import com.example.viewmodel.DrawStroke
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/** Crash-safe cache IO. AtomicFile restores the previous complete JSON after a failed write. */
internal object LocalQuestionCacheIO {
    // All repository instances share on-device question files.
    val questionWriteLock = Any()

    fun recoveryFile(file: File): File =
        File(file.parentFile, file.nameWithoutExtension + ".recovery.json")

    /** Keep the largest last-known-good snapshot for this owner only. */
    fun preserveLargerSnapshot(file: File, oldJson: String?) {
        if (oldJson.isNullOrBlank()) return
        val oldCount = runCatching { JSONArray(oldJson).length() }.getOrDefault(0)
        if (oldCount == 0) return
        val archive = recoveryFile(file)
        val archivedCount = runCatching {
            JSONArray(readJson(archive) ?: "[]").length()
        }.getOrDefault(0)
        if (oldCount > archivedCount) writeJson(archive, oldJson)
    }

    fun writeJson(file: File, json: String) {
        require(file.parentFile?.isDirectory == true) { "Question cache directory missing" }
        val atomic = AtomicFile(file)
        var out: FileOutputStream? = null
        try {
            out = atomic.startWrite()
            out.write(json.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(out)
        } catch (e: Exception) {
            out?.let(atomic::failWrite)
            throw e
        }
    }

    fun readJson(file: File): String? = try {
        AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (_: Exception) {
        null
    }

    fun firstValidJson(candidates: List<String?>): String? =
        candidates.filterNotNull().firstOrNull { raw ->
            raw.isNotBlank() && runCatching { JSONArray(raw) }.isSuccess
        }

    /**
     * The device-global fallback may have been written by another signed-in
     * account. Restore only when every record explicitly belongs to this user.
     */
    fun verifiedUserFallback(json: String?, userId: String): String? {
        if (json.isNullOrBlank() || userId.isBlank()) return null
        return runCatching {
            val arr = JSONArray(json)
            if ((0 until arr.length()).all { i ->
                arr.optJSONObject(i)?.optString("userId") == userId
            }) json else null
        }.getOrNull()
    }
}

@Immutable
data class RecognizedDeviceAccount(
    val uid: String,
    val displayName: String,
    val email: String,
    val recognizedAtMillis: Long = System.currentTimeMillis()
)

class SoruLabRepository(
    private val db: FirebaseFirestore,
    private val appContext: Context? = null
) {
    private val imageAvailabilityCache = ConcurrentHashMap<String, Boolean>()

    fun hasRecoverableQuestionImage(question: CustomQuestionRecord): Boolean {
        val cacheKey = "${question.id}|${question.croppedImagePath}|${question.pdfFilePath}|" +
            "${question.imageBase64.hashCode()}|${question.pageIndex}"
        imageAvailabilityCache[cacheKey]?.let { return it }
        val context = appContext
        val imagePaths = buildList {
            question.croppedImagePath.takeIf(String::isNotBlank)?.let { add(it) }
            if (context != null && question.id.isNotBlank()) {
                add(File(context.filesDir, "questions/q_${question.id}.jpg").absolutePath)
                add(File(context.filesDir, "questions/custom_${question.id}.jpg").absolutePath)
            }
        }
        val found = imagePaths.any { path ->
                val file = File(path)
                // The PDF/ZIP importer validates image encoding before persisting.
                // Reading every bitmap header on the UI thread freezes large libraries.
                file.isFile && file.length() >= 128L
            } ||
            (question.imageBase64.length >= 160)
        val pdfFile = question.pdfFilePath.takeIf(String::isNotBlank)?.let(::File)
        val recoverable = found || (pdfFile?.isFile == true &&
            pdfFile.length() > 0L &&
            question.pageIndex >= 0 &&
            question.normRight > question.normLeft &&
            question.normBottom > question.normTop)
        if (recoverable) imageAvailabilityCache[cacheKey] = true
        return recoverable
    }

    private fun hasBitmapBounds(source: String, isFile: Boolean = false): Boolean {
        if (source.length > 12_000_000) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return try {
            if (source.startsWith("data:", ignoreCase = true)) {
                val encoded = source.substringAfter(',', "")
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } else if (isFile) {
                BitmapFactory.decodeFile(source, options)
            } else {
                val encoded = source.substringAfter("base64,", source)
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
            options.outWidth >= 120 && options.outHeight >= 80
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: OutOfMemoryError) {
            false
        }
    }

    constructor(context: Context) : this(
        db = kotlin.run {
            check(ensureFirebaseInitialized(context.applicationContext)) {
                "Firebase yapılandırması eksik. google-services.json dosyasını ve Android uygulama kimliğini kontrol edin."
            }
            FirebaseFirestore.getInstance(
                FirebaseApp.getInstance(),
                context.applicationContext.getString(R.string.firestore_database_id)
            )
        },
        appContext = context.applicationContext
    )

    private val testResultsCollection = db.collection("test_results")
    private val customQuestionsCollection = db.collection("custom_questions")
    private val customPublishersCollection = db.collection("users")

    fun loadRecognizedDeviceAccount(): RecognizedDeviceAccount? {
        val ctx = appContext ?: return null
        return try {
            val prefs = ctx.getSharedPreferences("sorulab_device_auth_prefs", Context.MODE_PRIVATE)
            val explicitSignOut = prefs.getBoolean("explicit_sign_out", false)
            if (explicitSignOut) return null
            val uid = prefs.getString("recognized_uid", null)
            val name = prefs.getString("recognized_name", null)
            val email = prefs.getString("recognized_email", null)
            val confirmedDeviceSession = prefs.getBoolean("user_confirmed_device_session", false)
            if (!uid.isNullOrBlank() && (uid != "device_yks_student_tr" || confirmedDeviceSession)) {
                RecognizedDeviceAccount(
                    uid = uid,
                    displayName = name?.ifBlank { "SoruLab Öğrencisi" } ?: "SoruLab Öğrencisi",
                    email = email.orEmpty(),
                    recognizedAtMillis = prefs.getLong("recognized_at", System.currentTimeMillis())
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun saveRecognizedDeviceAccount(uid: String, displayName: String, email: String) {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences("sorulab_device_auth_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("explicit_sign_out", false)
                .putBoolean("user_confirmed_device_session", true)
                .putString("recognized_uid", uid)
                .putString("recognized_name", displayName.ifBlank { "YKS Öğrencisi" })
                .putString("recognized_email", email)
                .putLong("recognized_at", System.currentTimeMillis())
                .apply()
        } catch (_: Exception) {
        }
    }

    fun clearRecognizedDeviceAccountOnSignOut() {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences("sorulab_device_auth_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("explicit_sign_out", true)
                .putBoolean("user_confirmed_device_session", false)
                .remove("recognized_uid")
                .apply()
        } catch (_: Exception) {
        }
    }

    /** Retire the destructive legacy migration without deleting student data. */
    fun ensureDemoDataCleanedOnce() {
        val ctx = appContext ?: return
        ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("clean_demo_data_v6", true)
            .remove("pending_cloud_demo_purge_v6")
            .apply()
    }

    fun clearAllLocalUserData(userId: String) {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            val publisherLogoPaths = loadCustomPublishers(userId)
                .map { it.customLogoPath }
                .filter(String::isNotBlank)
            val editor = prefs.edit()
            prefs.all.keys.forEach { key ->
                if (key == "solved_results_$userId" ||
                    key == "gen_tests_$userId" ||
                    key == "gen_snapshots_$userId" ||
                    key.startsWith("gen_snapshot_${userId}_") ||
                    key == "deleted_tests_$userId" ||
                    key == "custom_questions_$userId" ||
                    key == customPublishersCacheKey(userId) ||
                    key == "board_strokes_$userId"
                ) {
                    editor.remove(key)
                }
            }
            editor.remove("solved_results_$userId")
            editor.remove("gen_tests_$userId")
            editor.remove("gen_snapshots_$userId")
            if (userId == com.example.util.PdfLibraryAccount.GUEST_CACHE_UID) {
                editor.remove("gen_tests_global_device")
                editor.remove("deleted_tests_global_device")
                editor.remove("custom_questions_global_device")
            }
            editor.remove("deleted_tests_$userId")
            editor.remove("custom_questions_$userId")
            editor.remove("board_strokes_$userId")
            editor.apply()
            val cacheDir = java.io.File(ctx.filesDir, "sorulab_question_cache")
            if (cacheDir.exists()) {
                val safeUid = userId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                val ownFile = File(cacheDir, "custom_questions_$safeUid.json")
                ownFile.delete()
                LocalQuestionCacheIO.recoveryFile(ownFile).delete()
                // No other signed-in account's question bank is ever erased.
            }
            val databaseId = userId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            ctx.deleteDatabase("sorulab_library_$databaseId.db")
            val snapshotDir = java.io.File(
                java.io.File(ctx.filesDir, "sorulab_test_snapshots"),
                userId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            )
            if (snapshotDir.exists()) {
                snapshotDir.listFiles()?.forEach { runCatching { it.delete() } }
                runCatching { snapshotDir.delete() }
            }
            publisherLogoPaths.forEach { File(it).delete() }
        } catch (_: Exception) {
        }
    }

    fun loadCachedSolvedTestResults(userId: String): List<TestResultRecord> {
        val ctx = appContext ?: return emptyList()
        return try {
            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            val raw = prefs.getString("solved_results_$userId", null) ?: return emptyList()
            val arr = JSONArray(raw)
            val list = mutableListOf<TestResultRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    TestResultRecord(
                        id = obj.optString("id"),
                        userId = obj.optString("userId", userId),
                        examType = obj.optString("examType", "TYT"),
                        subjectId = obj.optString("subjectId", ""),
                        subjectName = obj.optString("subjectName", ""),
                        topicId = obj.optString("topicId", ""),
                        topicName = obj.optString("topicName", ""),
                        testId = obj.optString("testId"),
                        testTitle = obj.optString("testTitle"),
                        correctCount = obj.optInt("correctCount", 0),
                        wrongCount = obj.optInt("wrongCount", 0),
                        emptyCount = obj.optInt("emptyCount", 0),
                        ungradedCount = obj.optInt("ungradedCount", 0),
                        durationSeconds = obj.optInt("durationSeconds", 0),
                        answersMapJson = obj.optString("answersMapJson", ""),
                        createdAt = if (obj.has("createdAtSeconds")) {
                            com.google.firebase.Timestamp(
                                obj.optLong("createdAtSeconds"),
                                obj.optInt("createdAtNanos", 0)
                            )
                        } else {
                            null
                        },
                        testSnapshotJson = obj.optString("testSnapshotJson", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCachedSolvedTestResults(userId: String, records: List<TestResultRecord>) {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            val arr = JSONArray()
            records.distinctBy { it.id.ifBlank { "${it.testId}_${it.createdAt?.seconds}" } }.forEach { rec ->
                val obj = JSONObject()
                obj.put("id", rec.id)
                obj.put("userId", rec.userId)
                obj.put("examType", rec.examType)
                obj.put("subjectId", rec.subjectId)
                obj.put("subjectName", rec.subjectName)
                obj.put("topicId", rec.topicId)
                obj.put("topicName", rec.topicName)
                obj.put("testId", rec.testId)
                obj.put("testTitle", rec.testTitle)
                obj.put("correctCount", rec.correctCount)
                obj.put("wrongCount", rec.wrongCount)
                obj.put("emptyCount", rec.emptyCount)
                obj.put("ungradedCount", rec.ungradedCount)
                obj.put("durationSeconds", rec.durationSeconds)
                obj.put("answersMapJson", rec.answersMapJson)
                obj.put("testSnapshotJson", rec.testSnapshotJson)
                rec.createdAt?.let {
                    obj.put("createdAtSeconds", it.seconds)
                    obj.put("createdAtNanos", it.nanoseconds)
                }
                arr.put(obj)
            }
            prefs.edit().putString("solved_results_$userId", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun loadCachedGeneratedTestKeys(userId: String): List<Triple<String, TopicTestLevel, Int>> {
        val ctx = appContext ?: return emptyList()
        return try {
            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            val raw = prefs.getString("gen_tests_$userId", null)
                ?: (if (userId == "device_yks_student_tr") prefs.getString("gen_tests_global_device", null) else null)
                ?: return emptyList()
            val arr = JSONArray(raw)
            val result = mutableListOf<Triple<String, TopicTestLevel, Int>>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val topicId = obj.optString("topicId")
                val levelName = obj.optString("level")
                val testNumber = obj.optInt("testNumber", 1)
                val level = TopicTestLevel.entries.find { it.name.equals(levelName, ignoreCase = true) }
                if (topicId.isNotBlank() && level != null && testNumber >= 1) {
                    result.add(Triple(topicId, level, testNumber))
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCachedGeneratedTestKeys(userId: String, entries: List<Triple<String, TopicTestLevel, Int>>) {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            val arr = JSONArray()
            entries.distinctBy { "${it.first}_${it.second.name}_${it.third}" }.forEach { (topicId, level, num) ->
                val obj = JSONObject()
                obj.put("topicId", topicId)
                obj.put("level", level.name)
                obj.put("testNumber", num)
                arr.put(obj)
            }
            val jsonStr = arr.toString()
            prefs.edit()
                .putString("gen_tests_$userId", jsonStr)
                .apply()
        } catch (_: Exception) {
        }
    }

    fun loadCachedGeneratedTestSnapshots(userId: String): Map<String, String> {
        val ctx = appContext ?: return emptyMap()
        return try {
            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            val prefix = "gen_snapshot_${userId}_"
            val snapshots = buildMap {
                prefs.all.forEach { (key, value) ->
                    if (key.startsWith(prefix) && value is String && value.isNotBlank()) {
                        put(key.removePrefix(prefix), value)
                    }
                }
            }
            val safeUserId = userId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val snapshotDir = java.io.File(java.io.File(ctx.filesDir, "sorulab_test_snapshots"), safeUserId)
            val diskSnapshots = snapshotDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".json") }
                .orEmpty()
                .mapNotNull { file ->
                    runCatching {
                        val raw = file.readText(Charsets.UTF_8)
                        val testId = JSONObject(raw).optString("id")
                        testId.takeIf(String::isNotBlank)?.let { it to raw }
                    }.getOrNull()
                }
                .toMap()
            snapshots + diskSnapshots
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveCachedGeneratedTestSnapshot(userId: String, test: YksTestItem) {
        val ctx = appContext ?: throw IllegalStateException("Test yedek deposu kullanılamıyor.")
        val safeUserId = userId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val snapshotDir = java.io.File(java.io.File(ctx.filesDir, "sorulab_test_snapshots"), safeUserId)
        if (!snapshotDir.exists() && !snapshotDir.mkdirs()) {
            throw java.io.IOException("Test yedeği klasörü oluşturulamadı.")
        }
        val safeTestId = test.id.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        java.io.File(snapshotDir, "snapshot_$safeTestId.json")
            .writeText(TestSnapshotCodec.encode(test), Charsets.UTF_8)
        ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            .edit().remove("gen_snapshot_${userId}_${test.id}").apply()
    }

    fun removeCachedGeneratedTestSnapshot(userId: String, testId: String) {
        val ctx = appContext ?: return
        try {
            ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
                .edit()
                .remove("gen_snapshot_${userId}_$testId")
                .apply()
            val safeUserId = userId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val safeTestId = testId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            java.io.File(
                java.io.File(java.io.File(ctx.filesDir, "sorulab_test_snapshots"), safeUserId),
                "snapshot_$safeTestId.json"
            ).delete()
        } catch (_: Exception) {
        }
    }

    fun loadCachedDeletedTestIds(userId: String): Set<String> {
        val ctx = appContext ?: return emptySet()
        return try {
            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            val raw = prefs.getString("deleted_tests_$userId", null)
                ?: (if (userId == "device_yks_student_tr") prefs.getString("deleted_tests_global_device", null) else null)
                ?: return emptySet()
            val arr = JSONArray(raw)
            val result = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val id = arr.optString(i)
                if (id.isNotBlank()) {
                    result.add(id)
                }
            }
            result
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun saveCachedDeletedTestIds(userId: String, deletedIds: Set<String>) {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            val arr = JSONArray()
            deletedIds.filter { it.isNotBlank() }.forEach { arr.put(it) }
            val jsonStr = arr.toString()
            prefs.edit()
                .putString("deleted_tests_$userId", jsonStr)
                .apply()
        } catch (_: Exception) {
        }
    }

    fun observeUserTestResults(userId: String): Flow<List<TestResultRecord>> = callbackFlow {
        val registration = testResultsCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val reportedError = try {
                        handleFirestoreError(error, OperationType.LIST, "test_results")
                        error
                    } catch (loggedError: RuntimeException) {
                        loggedError
                    }
                    close(reportedError)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val items = snapshot.toObjects(TestResultRecord::class.java)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                    trySend(items)
                }
            }
        awaitClose { registration.remove() }
    }

    fun observeUserCustomQuestions(userId: String): Flow<List<CustomQuestionRecord>> = callbackFlow {
        val registration = customQuestionsCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val reportedError = try {
                        handleFirestoreError(error, OperationType.LIST, "custom_questions")
                        error
                    } catch (loggedError: RuntimeException) {
                        loggedError
                    }
                    close(reportedError)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val items = snapshot.toObjects(CustomQuestionRecord::class.java)
                        .map(::normalizeLegacyBiologyQuestion)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                    trySend(items)
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun saveTestResult(record: TestResultRecord): String {
        val docRef = if (record.id.isNotBlank()) {
            testResultsCollection.document(record.id.take(120))
        } else {
            testResultsCollection.document()
        }
        try {
            val payload = mapOf(
                "userId" to record.userId,
                "examType" to if (record.examType.equals("AYT", ignoreCase = true)) "AYT" else "TYT",
                "subjectId" to record.subjectId.take(64),
                "subjectName" to record.subjectName.take(100),
                "topicId" to record.topicId.take(64),
                "topicName" to record.topicName.take(120),
                "testId" to record.testId.ifBlank { "test_1" }.take(64),
                "testTitle" to record.testTitle.ifBlank { "Test 1" }.take(150),
                "correctCount" to record.correctCount.coerceIn(0, 200),
                "wrongCount" to record.wrongCount.coerceIn(0, 200),
                "emptyCount" to record.emptyCount.coerceIn(0, 200),
                "ungradedCount" to record.ungradedCount.coerceIn(0, 200),
                "durationSeconds" to record.durationSeconds.coerceIn(0, 36000),
                "answersMapJson" to record.answersMapJson.take(4900),
                "testSnapshotJson" to record.testSnapshotJson,
                "createdAt" to (record.createdAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            return docRef.id
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "test_results/${docRef.id}")
            throw e
        }
    }

    suspend fun getTestResult(resultId: String): TestResultRecord? {
        try {
            val snap = testResultsCollection.document(resultId).get().await()
            return snap.toObject(TestResultRecord::class.java)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "test_results/$resultId")
            throw e
        }
    }

    suspend fun getUserTestResultsList(userId: String): List<TestResultRecord> {
        try {
            val snap = testResultsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()
            return snap.toObjects(TestResultRecord::class.java)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "test_results")
            throw e
        }
    }

    suspend fun updateTestResultCounts(
        resultId: String,
        correctCount: Int,
        wrongCount: Int,
        emptyCount: Int,
        answersMapJson: String,
        ungradedCount: Int = 0
    ) {
        try {
            testResultsCollection.document(resultId).update(
                mapOf(
                    "correctCount" to correctCount.coerceIn(0, 200),
                    "wrongCount" to wrongCount.coerceIn(0, 200),
                    "emptyCount" to emptyCount.coerceIn(0, 200),
                    "ungradedCount" to ungradedCount.coerceIn(0, 200),
                    "answersMapJson" to answersMapJson.take(4900),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "test_results/$resultId")
            throw e
        }
    }

    suspend fun deleteTestResult(resultId: String) {
        try {
            testResultsCollection.document(resultId).delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "test_results/$resultId")
            throw e
        }
    }

    suspend fun saveCustomQuestion(record: CustomQuestionRecord): String {
        val docRef = if (record.id.isNotBlank()) {
            customQuestionsCollection.document(record.id.take(120))
        } else {
            customQuestionsCollection.document()
        }
        try {
            val creationDates = existingQuestionCreationDates(listOf(record.copy(id = docRef.id)))
            val payload = customQuestionFirestorePayload(record, creationDates[docRef.id])
            docRef.set(payload).await()
            return docRef.id
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "custom_questions/${docRef.id}")
            throw e
        }
    }

    suspend fun getCustomQuestion(questionId: String): CustomQuestionRecord? {
        try {
            val snap = customQuestionsCollection.document(questionId).get().await()
            return snap.toObject(CustomQuestionRecord::class.java)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "custom_questions/$questionId")
            throw e
        }
    }

    suspend fun getUserCustomQuestionsList(userId: String): List<CustomQuestionRecord> {
        try {
            val snap = customQuestionsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()
            return snap.toObjects(CustomQuestionRecord::class.java)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "custom_questions")
            throw e
        }
    }

    suspend fun saveCustomQuestionsBatch(records: List<CustomQuestionRecord>) {
        if (records.isEmpty()) return
        // The old implementation downloaded the ENTIRE owner's cloud collection
        // once for every 350 new questions. For dozens of books, this becomes
        // increasingly expensive. Cache the original creation dates once for
        // the normal path; a retry re-queries that chunk after an uncertain write.
        var initialCreationDates: Map<String, Timestamp>? = null
        records.chunked(350).forEach { chunk ->
            val documentRefs = chunk.map { record ->
                if (record.id.isNotBlank()) {
                    customQuestionsCollection.document(record.id.take(120))
                } else {
                    customQuestionsCollection.document()
                }
            }
            var attempt = 0
            while (true) {
                try {
                    // A timed-out write may have committed: refresh the current
                    // chunk on retries to avoid overwriting its original createdAt.
                    val creationDates = if (attempt > 0) {
                        existingQuestionCreationDates(
                            chunk.mapIndexed { index, record ->
                                record.copy(id = documentRefs[index].id)
                            }
                        )
                    } else {
                        initialCreationDates ?: existingQuestionCreationDates(records).also {
                            initialCreationDates = it
                        }
                    }
                    val batch = db.batch()
                    chunk.forEachIndexed { index, record ->
                        batch.set(documentRefs[index], customQuestionFirestorePayload(record, creationDates[documentRefs[index].id]))
                    }
                    batch.commit().await()
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code
                    val retryable = code in setOf(
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED
                    )
                    if (retryable && attempt < 2) {
                        delay(500L shl attempt)
                        attempt++
                    } else {
                        handleFirestoreError(e, OperationType.CREATE, "custom_questions/batch")
                        throw e
                    }
                }
            }
        }
    }

    private suspend fun existingQuestionCreationDates(records: List<CustomQuestionRecord>): Map<String, Timestamp> {
        val dates = mutableMapOf<String, Timestamp>()
        // An owner-scoped query can safely return an empty result for a new ID. A direct
        // read of a missing document is denied by the ownership rules.
        records.groupBy { it.userId }.forEach { (uid, ownedRecords) ->
            val ids = ownedRecords.mapTo(hashSetOf()) { it.id.take(120) }
            // Do not constrain __name__ to IDs that may not exist yet. The emulator
            // evaluates those missing targets without resource data, rejecting the
            // entire list query under the owner-only rule. Query actual owner
            // records, then retain only the requested timestamps locally.
            val snapshot = customQuestionsCollection.whereEqualTo("userId", uid).get().await()
            snapshot.documents.filter { it.id in ids }.forEach { document ->
                document.getTimestamp("createdAt")?.let { dates[document.id] = it }
            }
        }
        return dates
    }

    private fun customQuestionFirestorePayload(
        record: CustomQuestionRecord,
        existingCreatedAt: Timestamp? = null
    ): Map<String, Any> {
        val safeRatio = if (record.cropAspectRatio.isFinite()) {
            record.cropAspectRatio.coerceIn(0.25, 4.8)
        } else {
            1.25
        }
        return mapOf(
            "userId" to record.userId,
            "examType" to if (record.examType.equals("AYT", ignoreCase = true)) "AYT" else "TYT",
            "subjectId" to record.subjectId.take(64),
            "subjectName" to record.subjectName.take(100),
            "topicId" to record.topicId.take(64),
            "topicName" to record.topicName.take(120),
            "difficulty" to (record.difficulty.takeIf { it in listOf("Kolay", "Orta", "Zor") } ?: "Orta"),
            "correctOption" to (record.correctOption.uppercase().takeIf { it in listOf("A", "B", "C", "D", "E") }.orEmpty()),
            "questionText" to record.questionText.ifBlank { "YKS Sorusu" }.take(1950),
            "publisherTag" to record.publisherTag.ifBlank { "Kaynak Belirtilmedi" }.take(115),
            "aiSolutionSummary" to record.aiSolutionSummary.take(3950),
            "cropAspectRatio" to safeRatio,
            "createdAt" to (existingCreatedAt ?: record.createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    suspend fun deleteCustomQuestionsBatch(questionIds: List<String>) {
        if (questionIds.isEmpty()) return
        questionIds.filter { it.isNotBlank() }.chunked(350).forEach { chunk ->
            try {
                val batch = db.batch()
                chunk.forEach { qid ->
                    batch.delete(customQuestionsCollection.document(qid))
                }
                batch.commit().await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, "custom_questions/batch")
                throw e
            }
        }
    }

    suspend fun deleteCustomQuestion(questionId: String) {
        try {
            customQuestionsCollection.document(questionId).delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "custom_questions/$questionId")
            throw e
        }
    }

    fun loadCachedCustomQuestions(userId: String): List<CustomQuestionRecord> {
        val ctx = appContext ?: return emptyList()
        // Transactionally committed, per-question index; existing JSON libraries
        // remain readable until their first successful SQLite migration.
        val indexed = runCatching {
            QuestionLibrarySqliteStore(ctx, userId).read()
        }.getOrNull()
        if (indexed != null) return indexed.map(::normalizeLegacyBiologyQuestion)
        return try {
            val cacheDir = java.io.File(ctx.filesDir, "sorulab_question_cache")
            val userCacheFile = java.io.File(cacheDir, "custom_questions_${userId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}.json")
            val globalCacheFile = java.io.File(cacheDir, "custom_questions_global_device.json")

            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            // AtomicFile can recover an interrupted write. If an older cache is
            // malformed, try its backup and legacy preferences rather than
            // treating the entire question bank as empty.
            // Prefer the largest complete, owner-scoped snapshot, not a
            // truncated cloud/cache write. Explicit deletion clears recovery.
            val userScoped = listOf(
                LocalQuestionCacheIO.readJson(userCacheFile),
                LocalQuestionCacheIO.readJson(LocalQuestionCacheIO.recoveryFile(userCacheFile)),
                prefs.getString("custom_questions_$userId", null)
            ).filterNotNull().filter { candidate ->
                runCatching { JSONArray(candidate) }.isSuccess
            }.maxByOrNull { candidate -> JSONArray(candidate).length() }
            val globalFallback = LocalQuestionCacheIO.firstValidJson(
                listOf(
                    LocalQuestionCacheIO.readJson(globalCacheFile),
                    prefs.getString("custom_questions_global_device", null)
                )
            )
            val raw = userScoped
                ?: globalFallback
                ?: return emptyList()
            val isOwnedCache = userScoped != null
            val arr = JSONArray(raw)
            val len = arr.length()
            val list = ArrayList<CustomQuestionRecord>(len)
            for (i in 0 until len) {
                val obj = arr.optJSONObject(i) ?: continue
                val owner = obj.optString("userId").ifBlank { if (isOwnedCache) userId else "" }
                if (owner != userId) continue
                list.add(
                    CustomQuestionRecord(
                        id = obj.optString("id"),
                        userId = owner,
                        examType = obj.optString("examType", "TYT"),
                        subjectId = obj.optString("subjectId", ""),
                        subjectName = obj.optString("subjectName", ""),
                        topicId = obj.optString("topicId", ""),
                        topicName = obj.optString("topicName", ""),
                        difficulty = obj.optString("difficulty", "Orta"),
                        correctOption = obj.optString("correctOption", ""),
                        questionText = obj.optString("questionText", ""),
                        learningOutcome = obj.optString("learningOutcome", ""),
                        questionType = obj.optString("questionType", ""),
                        publisherTag = obj.optString("publisherTag", "Kaynak Belirtilmedi"),
                        publisherId = obj.optString("publisherId", ""),
                        sourceBookTitle = obj.optString("sourceBookTitle", ""),
                        aiSolutionSummary = obj.optString("aiSolutionSummary", ""),
                        cropAspectRatio = obj.optDouble("cropAspectRatio", 1.25),
                        bookId = obj.optString("bookId", ""),
                        imageBase64 = obj.optString("imageBase64", ""),
                        pdfFilePath = obj.optString("pdfFilePath", ""),
                        pageIndex = obj.optInt("pageIndex", -1),
                        questionNumberInTest = obj.optInt("questionNumberInTest", 0),
                        normLeft = obj.optDouble("normLeft", 0.0).toFloat(),
                        normTop = obj.optDouble("normTop", 0.0).toFloat(),
                        normRight = obj.optDouble("normRight", 1.0).toFloat(),
                        normBottom = obj.optDouble("normBottom", 1.0).toFloat()
                    )
                )
            }
            list.map(::normalizeLegacyBiologyQuestion)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun deleteLocalQuestionImages(paths: Collection<String>): Int {
        val context = appContext ?: return paths.count(String::isNotBlank)
        return paths.filter(String::isNotBlank).count { path ->
            !StudioImageAndPdfEngine.deleteQuestionImage(context, path)
        }
    }

    fun rekeyLocalQuestionImage(path: String, questionId: String): String? {
        val context = appContext ?: return null
        return StudioImageAndPdfEngine.rekeyQuestionImage(context, path, questionId)
    }

    fun deleteAllLocalQuestionImages(): Int {
        val context = appContext ?: return 0
        return StudioImageAndPdfEngine.deleteAllQuestionImages(context)
    }

    fun deleteLocalBookPdfs(paths: Collection<String>): Int {
        val context = appContext ?: return paths.count(String::isNotBlank)
        return paths.filter(String::isNotBlank).distinct().count { path ->
            !StudioImageAndPdfEngine.deleteBookPdf(context, path)
        }
    }

    fun deleteAllLocalBookPdfs(): Int {
        val context = appContext ?: return 0
        return StudioImageAndPdfEngine.deleteAllBookPdfs(context)
    }

    private fun normalizeLegacyBiologyQuestion(record: CustomQuestionRecord): CustomQuestionRecord {
        val sourceSignal = "${record.subjectName} ${record.bookOrTestName} ${record.publisherTag}"
        if (!StudioImageAndPdfEngine.detectSubjectNameFromText(sourceSignal)
                .equals("Biyoloji", ignoreCase = true)
        ) {
            return record
        }

        val examType = record.examType.takeIf { it.equals("TYT", true) || it.equals("AYT", true) } ?: "AYT"
        val biology = SoruLabCatalog.allSubjects.firstOrNull {
            it.name.equals("Biyoloji", ignoreCase = true) &&
                it.examType.name.equals(examType, ignoreCase = true)
        } ?: SoruLabCatalog.allSubjects.firstOrNull { it.id == "ayt_biyoloji" }
            ?: return record
        val detectedTopicName = StudioImageAndPdfEngine.detectTopicNameFromText(
            examType = biology.examType.name,
            subjectName = biology.name,
            text = "${record.topicName}\n${record.questionText}"
        )
        val topic = biology.topics.firstOrNull { it.name.equals(detectedTopicName, ignoreCase = true) }
            ?: biology.topics.firstOrNull { it.id == record.topicId }
            ?: biology.topics.firstOrNull { it.name.equals(record.topicName, ignoreCase = true) }
        return record.copy(
            examType = biology.examType.name,
            subjectId = biology.id,
            subjectName = biology.name,
            topicId = topic?.id.orEmpty(),
            topicName = topic?.name.orEmpty()
        )
    }

    fun saveCachedCustomQuestions(
        userId: String,
        records: List<CustomQuestionRecord>,
        allowRemovals: Boolean = false
    ): List<CustomQuestionRecord> = synchronized(LocalQuestionCacheIO.questionWriteLock) {
        val ctx = appContext ?: throw IllegalStateException("Yerel soru deposu kullanılamıyor.")
        try {
            val cacheDir = java.io.File(ctx.filesDir, "sorulab_question_cache")
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                throw java.io.IOException("Soru önbelleği oluşturulamadı.")
            }
            // Firestore may briefly report only the first 350 uploaded
            // questions. Never replace a larger local bank with that subset.
            val previous = if (allowRemovals) emptyList() else loadCachedCustomQuestions(userId)
            val incoming = records.filter { it.userId.isBlank() || it.userId == userId }
            val incomingIds = incoming.mapTo(HashSet(incoming.size)) { it.id }
            val durableRecords = if (allowRemovals) incoming else
                incoming + previous.filterNot { it.id in incomingIds }
            // A transaction must succeed before the newly imported book is visible.
            val indexed = QuestionLibrarySqliteStore(ctx, userId)
            indexed.save(durableRecords, replace = allowRemovals)
            check(indexed.count() >= durableRecords.map { it.id }.distinct().size) {
                "SQLite kitaplık doğrulaması başarısız."
            }
            if (durableRecords.size > 400) {
                // Avoid repeatedly rewriting a growing multi-book JSON snapshot.
                // Keep the last legacy snapshot as emergency migration evidence.
                return@synchronized durableRecords
            }
            val arr = JSONArray()
            val seenIds = HashSet<String>(durableRecords.size)
            for (rec in durableRecords) {
                if (rec.userId.isNotBlank() && rec.userId != userId) continue
                if (rec.id.isNotBlank() && !seenIds.add(rec.id)) continue
                val obj = JSONObject()
                obj.put("id", rec.id)
                obj.put("userId", userId)
                obj.put("examType", rec.examType)
                obj.put("subjectId", rec.subjectId)
                obj.put("subjectName", rec.subjectName)
                obj.put("topicId", rec.topicId)
                obj.put("topicName", rec.topicName)
                obj.put("difficulty", rec.difficulty)
                obj.put("correctOption", rec.correctOption)
                obj.put("questionText", rec.questionText)
                obj.put("learningOutcome", rec.learningOutcome)
                obj.put("questionType", rec.questionType)
                obj.put("publisherTag", rec.publisherTag)
                obj.put("publisherId", rec.publisherId)
                obj.put("sourceBookTitle", rec.sourceBookTitle)
                obj.put("aiSolutionSummary", rec.aiSolutionSummary)
                obj.put("cropAspectRatio", rec.cropAspectRatio)
                obj.put("bookId", rec.bookId)
                obj.put("imageBase64", rec.imageBase64)
                obj.put("pdfFilePath", rec.pdfFilePath)
                obj.put("pageIndex", rec.pageIndex)
                obj.put("questionNumberInTest", rec.questionNumberInTest)
                obj.put("normLeft", rec.normLeft.toDouble())
                obj.put("normTop", rec.normTop.toDouble())
                obj.put("normRight", rec.normRight.toDouble())
                obj.put("normBottom", rec.normBottom.toDouble())
                arr.put(obj)
            }
            val jsonStr = arr.toString()
            val safeUid = userId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val userCacheFile = java.io.File(cacheDir, "custom_questions_$safeUid.json")
            val archiveFile = LocalQuestionCacheIO.recoveryFile(userCacheFile)
            if (allowRemovals) {
                archiveFile.delete()
                // Stale pre-JSON preferences must not resurrect deliberately
                // removed records when the new cache has fewer questions.
                val editor = ctx.getSharedPreferences(
                    "sorulab_generated_tests_prefs", Context.MODE_PRIVATE
                ).edit().remove("custom_questions_$userId")
                if (userId == com.example.util.PdfLibraryAccount.GUEST_CACHE_UID) {
                    editor.remove("custom_questions_global_device")
                }
                editor.commit()
            } else {
                LocalQuestionCacheIO.preserveLargerSnapshot(
                    userCacheFile, LocalQuestionCacheIO.readJson(userCacheFile)
                )
            }
            LocalQuestionCacheIO.writeJson(userCacheFile, jsonStr)
            val restoredIds = loadCachedCustomQuestions(userId).mapTo(HashSet()) { it.id }
            check(durableRecords.all { it.id in restoredIds }) {
                "Kalıcı soru kaydı doğrulanamadı."
            }
            // Keep SharedPreferences light (remove oversized old payloads if > 500 questions)
            if (durableRecords.size > 400) {
                val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
                val editor = prefs.edit().remove("custom_questions_$userId")
                if (userId == com.example.util.PdfLibraryAccount.GUEST_CACHE_UID) {
                    editor.remove("custom_questions_global_device")
                }
                editor.apply()
            }
            durableRecords
        } catch (e: Exception) {
            throw IllegalStateException("Sorular cihazdaki yerel depoya yazılamadı.", e)
        }
    }

    fun loadCustomPublishers(userId: String? = null): List<YksPublisherInfo> {
        val ctx = appContext ?: return emptyList()
        return try {
            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            val scopedKey = customPublishersCacheKey(userId)
            val raw = prefs.getString(scopedKey, null)
                ?: prefs.getString("custom_publishers_v1", null)?.also {
                    prefs.edit().putString(scopedKey, it).remove("custom_publishers_v1").apply()
                }
                ?: return emptyList()
            val arr = JSONArray(raw)
            val list = mutableListOf<YksPublisherInfo>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    YksPublisherInfo(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        shortCode = obj.optString("shortCode", "YKS"),
                        primaryColorHex = obj.optLong("primaryColorHex", 0xFF1D4ED8),
                        secondaryColorHex = obj.optLong("secondaryColorHex", 0xFF0F172A),
                        customLogoPath = obj.optString("customLogoPath", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCustomPublishers(publishers: List<YksPublisherInfo>, userId: String? = null) {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            val arr = JSONArray()
            publishers.distinctBy { it.id }.forEach { pub ->
                val obj = JSONObject()
                obj.put("id", pub.id)
                obj.put("name", pub.name)
                obj.put("shortCode", pub.shortCode)
                obj.put("primaryColorHex", pub.primaryColorHex)
                obj.put("secondaryColorHex", pub.secondaryColorHex)
                obj.put("customLogoPath", pub.customLogoPath)
                arr.put(obj)
            }
            prefs.edit().putString(customPublishersCacheKey(userId), arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun customPublishersCacheKey(userId: String?): String =
        userId?.takeIf(String::isNotBlank)?.let {
            "custom_publishers_${it.replace("[^A-Za-z0-9_-]".toRegex(), "_")}"
        } ?: "custom_publishers_device"

    fun observeUserCustomPublishers(userId: String): Flow<List<YksPublisherInfo>> = callbackFlow {
        val registration = customPublishersCollection
            .document(userId)
            .collection("publishers")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    try {
                        val publishers = snapshot.documents.mapNotNull { document ->
                            val name = document.getString("name")?.trim().orEmpty()
                            if (name.isBlank()) return@mapNotNull null
                            val logoBase64 = document.getString("logoBase64").orEmpty()
                            YksPublisherInfo(
                                id = document.id,
                                name = name,
                                shortCode = document.getString("shortCode").orEmpty(),
                                primaryColorHex = document.getLong("primaryColorHex") ?: 0xFF1D4ED8,
                                secondaryColorHex = document.getLong("secondaryColorHex") ?: 0xFF0F172A,
                                customLogoPath = if (logoBase64.isBlank()) "" else {
                                    cacheCloudPublisherLogo(userId, document.id, logoBase64)
                                }
                            )
                        }
                        trySend(publishers)
                    } catch (e: Exception) {
                        close(IllegalStateException("Buluttaki yayın logoları okunamadı.", e))
                    }
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun saveCloudCustomPublisher(userId: String, publisher: YksPublisherInfo) {
        require(userId.isNotBlank()) { "Yayın logosunu buluta kaydetmek için kullanıcı oturumu gerekli." }
        check(appContext != null) {
            "Yayın logosunu buluta kaydetmek için uygulama bağlamı gerekli."
        }
        val publisherId = publisher.id
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .take(120)
            .ifBlank { throw IllegalArgumentException("Yayın kimliği geçersiz.") }
        val logoFile = publisher.customLogoPath.takeIf(String::isNotBlank)?.let(::File)
        if (logoFile == null || !logoFile.isFile) {
            throw IllegalArgumentException("Yayın logosu cihazda bulunamadı.")
        }
        val logoBytes = logoFile.readBytes()
        require(logoBytes.isNotEmpty() && logoBytes.size <= MAX_CLOUD_PUBLISHER_LOGO_BYTES) {
            "Yayın logosu bulut yedeklemesi için çok büyük."
        }
        val logoBase64 = Base64.encodeToString(logoBytes, Base64.NO_WRAP)
        val payload = mapOf(
            "name" to publisher.name.take(120),
            "shortCode" to publisher.shortCode.take(4),
            "primaryColorHex" to publisher.primaryColorHex,
            "secondaryColorHex" to publisher.secondaryColorHex,
            "logoBase64" to logoBase64,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            val publisherCollection = customPublishersCollection
                .document(userId)
                .collection("publishers")
            publisherCollection.whereEqualTo("name", publisher.name.take(120))
                .get()
                .await()
                .documents
                .filter { it.id != publisherId }
                .forEach { it.reference.delete().await() }
            publisherCollection.document(publisherId).set(payload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$userId/publishers/$publisherId")
            throw e
        }
    }

    suspend fun deleteCloudCustomPublishers(userId: String) {
        require(userId.isNotBlank()) { "Yayın logolarını silmek için kullanıcı oturumu gerekli." }
        try {
            val publisherCollection = customPublishersCollection
                .document(userId)
                .collection("publishers")
            publisherCollection.get().await().documents
                .chunked(400)
                .forEach { documents ->
                    val batch = db.batch()
                    documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$userId/publishers")
            throw e
        }
    }

    private fun cacheCloudPublisherLogo(userId: String, publisherId: String, logoBase64: String): String {
        val context = appContext
            ?: throw IllegalStateException("Buluttaki yayın logosu bu cihazda saklanamadı.")
        val bytes = Base64.decode(logoBase64, Base64.DEFAULT)
        require(bytes.isNotEmpty() && bytes.size <= MAX_CLOUD_PUBLISHER_LOGO_BYTES) {
            "Buluttaki yayın logosu boyut sınırını aşıyor."
        }
        val logoDirectory = File(context.filesDir, "publisher_logos")
        check(logoDirectory.exists() || logoDirectory.mkdirs()) {
            "Yayın logosu klasörü oluşturulamadı."
        }
        val safeUserId = Base64.encodeToString(
            userId.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val safeId = publisherId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val target = File(logoDirectory, "cloud_${safeUserId}_$safeId.png")
        val temporary = File(logoDirectory, "cloud_${safeUserId}_$safeId.tmp")
        FileOutputStream(temporary).use { it.write(bytes) }
        check(temporary.renameTo(target)) { "Buluttaki yayın logosu dosyası kaydedilemedi." }
        return target.absolutePath
    }

    private companion object {
        const val MAX_CLOUD_PUBLISHER_LOGO_BYTES = 256 * 1024
    }

    fun loadCachedBoardStrokes(userId: String): Map<String, List<DrawStroke>> {
        val ctx = appContext ?: return emptyMap()
        return try {
            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            val raw = prefs.getString("board_strokes_$userId", null) ?: return emptyMap()
            val rootObj = JSONObject(raw)
            val result = mutableMapOf<String, List<DrawStroke>>()
            val keys = rootObj.keys()
            while (keys.hasNext()) {
                val canvasKey = keys.next()
                val strokesArr = rootObj.optJSONArray(canvasKey) ?: continue
                val strokeList = mutableListOf<DrawStroke>()
                for (i in 0 until strokesArr.length()) {
                    val sObj = strokesArr.optJSONObject(i) ?: continue
                    val ptsArr = sObj.optJSONArray("pts") ?: continue
                    val points = mutableListOf<Offset>()
                    var j = 0
                    while (j + 1 < ptsArr.length()) {
                        val x = ptsArr.optDouble(j, 0.0).toFloat()
                        val y = ptsArr.optDouble(j + 1, 0.0).toFloat()
                        points.add(Offset(x, y))
                        j += 2
                    }
                    if (points.size >= 2) {
                        // Missing pressure data belongs to a pre-upgrade drawing.
                        val pressureArray = sObj.optJSONArray("pressure")
                        val pressures = if (pressureArray?.length() == points.size) {
                            (0 until points.size).map { idx ->
                                pressureArray.optDouble(idx, 1.0).toFloat()
                                    .takeIf(Float::isFinite)?.coerceIn(0f, 1.5f) ?: 1f
                            }
                        } else emptyList()
                        strokeList.add(
                            DrawStroke(
                                points = points,
                                colorHex = sObj.optLong("color", 0xFF1D4ED8L),
                                strokeWidthPx = sObj.optDouble("width", 8.0).toFloat(),
                                isSmartShape = sObj.optBoolean("smart", false),
                                isEraser = sObj.optBoolean("eraser", false),
                                isHighlighter = sObj.optBoolean("highlighter", false),
                                pressureSamples = pressures
                            )
                        )
                    }
                }
                if (strokeList.isNotEmpty()) {
                    result[canvasKey] = strokeList
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveCachedBoardStrokes(userId: String, strokesMap: Map<String, List<DrawStroke>>) {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            val rootObj = JSONObject()
            strokesMap.forEach { (canvasKey, strokes) ->
                if (strokes.isNotEmpty()) {
                    val strokesArr = JSONArray()
                    strokes.forEach { stroke ->
                        val sObj = JSONObject()
                        sObj.put("color", stroke.colorHex)
                        sObj.put("width", stroke.strokeWidthPx.toDouble())
                        sObj.put("smart", stroke.isSmartShape)
                        sObj.put("eraser", stroke.isEraser)
                        sObj.put("highlighter", stroke.isHighlighter)
                        if (stroke.pressureSamples.size == stroke.points.size) {
                            val pressureArr = JSONArray()
                            stroke.pressureSamples.forEach { pressure ->
                                pressureArr.put(pressure.toDouble())
                            }
                            sObj.put("pressure", pressureArr)
                        }
                        val ptsArr = JSONArray()
                        stroke.points.forEach { pt ->
                            ptsArr.put(pt.x.toDouble())
                            ptsArr.put(pt.y.toDouble())
                        }
                        sObj.put("pts", ptsArr)
                        strokesArr.put(sObj)
                    }
                    rootObj.put(canvasKey, strokesArr)
                }
            }
            prefs.edit().putString("board_strokes_$userId", rootObj.toString()).apply()
        } catch (_: Exception) {
        }
    }
}
