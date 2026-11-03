package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuestionCacheIsolationTest {
    private lateinit var context: Context
    private lateinit var repository: SoruLabRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "sorulab_question_cache").deleteRecursively()
        FirebaseApp.clearInstancesForTest()
        repository = SoruLabRepository(context)
    }

    @Test
    fun legacyCleanupPreservesStudentDataAndDisarmsPendingPurge() {
        val prefs = context.getSharedPreferences("sorulab_generated_tests_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("solved_results_alice", "real-results")
            .putString("custom_questions_alice", "real-questions")
            .putBoolean("pending_cloud_demo_purge_v6", true).commit()
        repository.ensureDemoDataCleanedOnce()
        repository.ensureDemoDataCleanedOnce()
        assertEquals("real-results", prefs.getString("solved_results_alice", null))
        assertEquals("real-questions", prefs.getString("custom_questions_alice", null))
        assertFalse(prefs.getBoolean("pending_cloud_demo_purge_v6", false))
    }

    @Test
    fun switchingAccountsDoesNotExposePreviousQuestions() {
        repository.saveCachedCustomQuestions("alice", listOf(CustomQuestionRecord(id = "alice-q", userId = "alice")))
        assertTrue(repository.loadCachedCustomQuestions("bob").isEmpty())
        assertEquals(listOf("alice-q"), repository.loadCachedCustomQuestions("alice").map { it.id })
    }

    @Test
    fun legacyGlobalCacheRecoversOnlyExplicitOwnerRecords() {
        val dir = File(context.filesDir, "sorulab_question_cache").apply { mkdirs() }
        val data = JSONArray()
            .put(JSONObject().put("id", "alice-q").put("userId", "alice"))
            .put(JSONObject().put("id", "bob-q").put("userId", "bob"))
            .put(JSONObject().put("id", "unknown-q"))
        File(dir, "custom_questions_global_device.json").writeText(data.toString())
        assertEquals(listOf("bob-q"), repository.loadCachedCustomQuestions("bob").map { it.id })
        assertTrue(repository.loadCachedCustomQuestions("charlie").isEmpty())
    }

    @Test
    fun savingCacheCannotAssignAnotherAccountsQuestionToCurrentAccount() {
        repository.saveCachedCustomQuestions("bob", listOf(
            CustomQuestionRecord(id = "alice-q", userId = "alice"),
            CustomQuestionRecord(id = "bob-q", userId = "bob")
        ))
        assertEquals(listOf("bob-q"), repository.loadCachedCustomQuestions("bob").map { it.id })
    }
}
