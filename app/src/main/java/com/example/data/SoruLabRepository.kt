package com.example.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import com.example.R
import com.example.util.ensureFirebaseInitialized
import com.example.viewmodel.DrawStroke
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

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

    constructor(context: Context) : this(
        db = kotlin.run {
            ensureFirebaseInitialized(context.applicationContext)
            FirebaseFirestore.getInstance(
                FirebaseApp.getInstance(),
                context.applicationContext.getString(R.string.firestore_database_id)
            )
        },
        appContext = context.applicationContext
    )

    private val testResultsCollection = db.collection("test_results")
    private val customQuestionsCollection = db.collection("custom_questions")

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

    /**
     * One-time automatic migration that wipes any previously cached demo/sample generated tests,
     * solved test results, sample cropped questions, or preset publishers without real logo files
     * so the app starts 100% clean.
     */
    fun ensureDemoDataCleanedOnce() {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            val alreadyCleaned = prefs.getBoolean("clean_demo_data_v6", false)
            if (!alreadyCleaned) {
                val editor = prefs.edit()
                prefs.all.keys.forEach { key ->
                    if (key.startsWith("solved_results_") ||
                        key.startsWith("gen_tests_") ||
                        key.startsWith("custom_questions_") ||
                        key == "custom_publishers_v1"
                    ) {
                        editor.remove(key)
                    }
                }
                editor.putBoolean("clean_demo_data_v6", true)
                editor.putBoolean("pending_cloud_demo_purge_v6", true)
                editor.apply()
            }
        } catch (_: Exception) {
        }
    }

    fun consumePendingCloudDemoPurge(): Boolean {
        val ctx = appContext ?: return false
        return try {
            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            val pending = prefs.getBoolean("pending_cloud_demo_purge_v6", false)
            if (pending) {
                prefs.edit().putBoolean("pending_cloud_demo_purge_v6", false).apply()
            }
            pending
        } catch (_: Exception) {
            false
        }
    }

    fun clearAllLocalUserData(userId: String) {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            prefs.all.keys.forEach { key ->
                if (key.startsWith("solved_results_") ||
                    key.startsWith("gen_tests_") ||
                    key.startsWith("custom_questions_") ||
                    key.startsWith("board_strokes_")
                ) {
                    editor.remove(key)
                }
            }
            editor.remove("solved_results_$userId")
            editor.remove("gen_tests_$userId")
            editor.remove("gen_tests_global_device")
            editor.remove("custom_questions_$userId")
            editor.remove("custom_questions_global_device")
            editor.remove("board_strokes_$userId")
            editor.apply()
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
                        subjectId = obj.optString("subjectId", "tyt_mat"),
                        subjectName = obj.optString("subjectName", "Matematik"),
                        topicId = obj.optString("topicId", "temel_kavramlar"),
                        topicName = obj.optString("topicName", "Temel Kavramlar"),
                        testId = obj.optString("testId"),
                        testTitle = obj.optString("testTitle"),
                        correctCount = obj.optInt("correctCount", 0),
                        wrongCount = obj.optInt("wrongCount", 0),
                        emptyCount = obj.optInt("emptyCount", 0),
                        durationSeconds = obj.optInt("durationSeconds", 0),
                        answersMapJson = obj.optString("answersMapJson", "")
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
            records.distinctBy { it.testId.ifBlank { it.id } }.forEach { rec ->
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
                obj.put("durationSeconds", rec.durationSeconds)
                obj.put("answersMapJson", rec.answersMapJson)
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
                ?: prefs.getString("gen_tests_global_device", null)
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
                .putString("gen_tests_global_device", jsonStr)
                .apply()
        } catch (_: Exception) {
        }
    }

    fun observeUserTestResults(userId: String): Flow<List<TestResultRecord>> = callbackFlow {
        val registration = testResultsCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, "test_results")
                    close(error)
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
                    handleFirestoreError(error, OperationType.LIST, "custom_questions")
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val items = snapshot.toObjects(CustomQuestionRecord::class.java)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                    trySend(items)
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun saveTestResult(record: TestResultRecord): String {
        val docRef = if (record.id.isNotBlank()) {
            testResultsCollection.document(record.id)
        } else {
            testResultsCollection.document()
        }
        try {
            val payload = mapOf(
                "userId" to record.userId,
                "examType" to record.examType,
                "subjectId" to record.subjectId,
                "subjectName" to record.subjectName,
                "topicId" to record.topicId,
                "topicName" to record.topicName,
                "testId" to record.testId,
                "testTitle" to record.testTitle,
                "correctCount" to record.correctCount,
                "wrongCount" to record.wrongCount,
                "emptyCount" to record.emptyCount,
                "durationSeconds" to record.durationSeconds,
                "answersMapJson" to record.answersMapJson,
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
        answersMapJson: String
    ) {
        try {
            testResultsCollection.document(resultId).update(
                mapOf(
                    "correctCount" to correctCount,
                    "wrongCount" to wrongCount,
                    "emptyCount" to emptyCount,
                    "answersMapJson" to answersMapJson,
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
            customQuestionsCollection.document(record.id)
        } else {
            customQuestionsCollection.document()
        }
        try {
            val payload = mapOf(
                "userId" to record.userId,
                "examType" to record.examType,
                "subjectId" to record.subjectId,
                "subjectName" to record.subjectName,
                "topicId" to record.topicId,
                "topicName" to record.topicName,
                "difficulty" to record.difficulty,
                "correctOption" to record.correctOption,
                "questionText" to record.questionText,
                "publisherTag" to record.publisherTag,
                "aiSolutionSummary" to record.aiSolutionSummary,
                "cropAspectRatio" to record.cropAspectRatio,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
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
        return try {
            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            val raw = prefs.getString("custom_questions_$userId", null)
                ?: prefs.getString("custom_questions_global_device", null)
                ?: return emptyList()
            val arr = JSONArray(raw)
            val list = mutableListOf<CustomQuestionRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    CustomQuestionRecord(
                        id = obj.optString("id"),
                        userId = obj.optString("userId", userId),
                        examType = obj.optString("examType", "TYT"),
                        subjectId = obj.optString("subjectId", "tyt_mat"),
                        subjectName = obj.optString("subjectName", "Matematik"),
                        topicId = obj.optString("topicId", "temel_kavramlar"),
                        topicName = obj.optString("topicName", "Temel Kavramlar"),
                        difficulty = obj.optString("difficulty", "Orta"),
                        correctOption = obj.optString("correctOption", "C"),
                        questionText = obj.optString("questionText", ""),
                        publisherTag = obj.optString("publisherTag", "ÜçDörtBeş Yayınları"),
                        aiSolutionSummary = obj.optString("aiSolutionSummary", ""),
                        cropAspectRatio = obj.optDouble("cropAspectRatio", 1.25)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCachedCustomQuestions(userId: String, records: List<CustomQuestionRecord>) {
        val ctx = appContext ?: return
        try {
            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            val arr = JSONArray()
            records.distinctBy { it.id }.forEach { rec ->
                val obj = JSONObject()
                obj.put("id", rec.id)
                obj.put("userId", rec.userId)
                obj.put("examType", rec.examType)
                obj.put("subjectId", rec.subjectId)
                obj.put("subjectName", rec.subjectName)
                obj.put("topicId", rec.topicId)
                obj.put("topicName", rec.topicName)
                obj.put("difficulty", rec.difficulty)
                obj.put("correctOption", rec.correctOption)
                obj.put("questionText", rec.questionText)
                obj.put("publisherTag", rec.publisherTag)
                obj.put("aiSolutionSummary", rec.aiSolutionSummary)
                obj.put("cropAspectRatio", rec.cropAspectRatio)
                arr.put(obj)
            }
            val jsonStr = arr.toString()
            prefs.edit()
                .putString("custom_questions_$userId", jsonStr)
                .putString("custom_questions_global_device", jsonStr)
                .apply()
        } catch (_: Exception) {
        }
    }

    fun loadCustomPublishers(): List<YksPublisherInfo> {
        val ctx = appContext ?: return emptyList()
        return try {
            val prefs = ctx.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
            val raw = prefs.getString("custom_publishers_v1", null) ?: return emptyList()
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

    fun saveCustomPublishers(publishers: List<YksPublisherInfo>) {
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
            prefs.edit().putString("custom_publishers_v1", arr.toString()).apply()
        } catch (_: Exception) {
        }
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
                        strokeList.add(
                            DrawStroke(
                                points = points,
                                colorHex = sObj.optLong("color", 0xFF1D4ED8L),
                                strokeWidthPx = sObj.optDouble("width", 8.0).toFloat(),
                                isSmartShape = sObj.optBoolean("smart", false)
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
