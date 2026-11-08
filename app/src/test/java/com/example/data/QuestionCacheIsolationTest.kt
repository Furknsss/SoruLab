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
    fun a350QuestionCloudSnapshotCannotErase1400LocalQuestions() {
        val completeBank = (1..1400).map { number ->
            CustomQuestionRecord(
                id = "owned-$number",
                userId = "student",
                bookId = "book_${(number - 1) / 100}",
                questionText = "Original question $number"
            )
        }
        assertEquals(1400, repository.saveCachedCustomQuestions("student", completeBank).size)
        val result = repository.saveCachedCustomQuestions("student", completeBank.take(350))
        assertEquals("Partial cloud sync must preserve entire local bank", 1400, result.size)
        assertEquals(1400, repository.loadCachedCustomQuestions("student").size)
        assertEquals(1400, repository.loadCachedCustomQuestions("student").distinctBy { it.id }.size)
    }

    @Test
    fun corruptPrimaryCanRecoverPreviousLargeSnapshot() {
        val records = (1..1400).map { CustomQuestionRecord(id = "q-$it", userId = "student") }
        repository.saveCachedCustomQuestions("student", records)
        repository.saveCachedCustomQuestions("student", records + CustomQuestionRecord(id = "q-1401", userId = "student"))
        val ownerFile = File(context.filesDir, "sorulab_question_cache/custom_questions_student.json")
        val archive = LocalQuestionCacheIO.recoveryFile(ownerFile)
        assertTrue("Largest previous snapshot should be retained", archive.isFile)
        ownerFile.writeText("[corrupt")
        assertEquals(1400, repository.loadCachedCustomQuestions("student").size)
    }

    @Test
    fun explicitlyRemovingQuestionsDoesNotResurrectArchivedOrLegacyRecords() {
        val records = (1..1400).map { CustomQuestionRecord(id = "q-$it", userId = "student") }
        repository.saveCachedCustomQuestions("student", records)
        repository.saveCachedCustomQuestions("student", records + CustomQuestionRecord(id = "q-extra", userId = "student"))
        val retained = repository.saveCachedCustomQuestions(
            "student", records.take(350), allowRemovals = true
        )
        assertEquals(350, retained.size)
        assertEquals(350, repository.loadCachedCustomQuestions("student").size)
        assertTrue(repository.loadCachedCustomQuestions("otherStudent").isEmpty())
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
