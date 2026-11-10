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
        // Each test must start without another test's committed SQLite state.
        context.databaseList().filter { it.startsWith("sorulab_library_") }
            .forEach(context::deleteDatabase)
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
    fun tenThousandQuestionsSurviveRestartAndPartialCloudSync() {
        // Simulate 20 separately imported books, including an image path,
        // metadata and source identity for every question.
        val all = (1..10000).map { number ->
            CustomQuestionRecord(
                id = "question-$number",
                userId = "student",
                bookId = "book_${(number - 1) / 500}",
                sourceBookTitle = "Book ${(number - 1) / 500}",
                examType = if (number % 2 == 0) "AYT" else "TYT",
                subjectName = "Matematik",
                topicName = "Fonksiyonlar",
                questionText = "Soru $number",
                pageIndex = number / 5,
                questionNumberInTest = number % 20 + 1,
                aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta(
                    solutionText = "",
                    targetLevel = "OSYM",
                    croppedImagePath = "/data/user/0/com.aistudio.sorulab.ykslab/files/questions/q$number.png",
                    publisherLogoUri = "",
                    answerKeyVerified = true,
                    questionNumberInBook = number,
                    bookOrTestName = "Book ${(number - 1) / 500}",
                    bookCoverPath = ""
                )
            )
        }
        val firstSave = repository.saveCachedCustomQuestions("student", all)
        assertEquals(10000, firstSave.size)
        val afterRestart = SoruLabRepository(context).loadCachedCustomQuestions("student")
        assertEquals(10000, afterRestart.size)
        assertEquals(10000, afterRestart.map { it.id }.toSet().size)
        assertEquals(20, afterRestart.map { it.bookId }.toSet().size)
        assertEquals(
            "question-10000",
            afterRestart.firstOrNull { it.id == "question-10000" }?.id
        )
        // A delayed cloud listener with one batch of 350 must never shrink
        // the original 10,000-question student library.
        repository.saveCachedCustomQuestions("student", all.take(350))
        val recovered = SoruLabRepository(context).loadCachedCustomQuestions("student")
        assertEquals(10000, recovered.size)
        assertEquals(10000, recovered.map { it.id }.toSet().size)
        assertEquals(20, recovered.map { it.bookId }.toSet().size)
    }

    @Test
    fun corruptLegacySnapshotCannotDamageCommittedLargeSqliteLibrary() {
        val records = (1..1400).map { CustomQuestionRecord(id = "q-$it", userId = "student") }
        repository.saveCachedCustomQuestions("student", records)
        repository.saveCachedCustomQuestions("student", records + CustomQuestionRecord(id = "q-1401", userId = "student"))
        assertEquals(1401, repository.loadCachedCustomQuestions("student").size)
        // SQLite is the durable authoritative index for large libraries.
        // Corrupting an old JSON fallback must never roll back the 1401st question.
        val ownerFile = File(context.filesDir, "sorulab_question_cache/custom_questions_student.json")
        ownerFile.parentFile?.mkdirs()
        ownerFile.writeText("[corrupt")
        assertEquals(1401, SoruLabRepository(context).loadCachedCustomQuestions("student").size)
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
    fun signedInCacheWritesCannotDestroyGuestQuestionBank() {
        val guest = (1..1400).map {
            CustomQuestionRecord(id = "guest-$it", userId = "device_yks_student_tr")
        }
        repository.saveCachedCustomQuestions("device_yks_student_tr", guest)
        val authenticated = (1..800).map {
            CustomQuestionRecord(id = "signed-$it", userId = "signedStudent")
        }
        repository.saveCachedCustomQuestions("signedStudent", authenticated)
        assertEquals(1400, repository.loadCachedCustomQuestions("device_yks_student_tr").size)
        assertEquals(800, repository.loadCachedCustomQuestions("signedStudent").size)
    }

    @Test
    fun resettingOneAccountMustNotEraseOtherAccountsLibrary() {
        repository.saveCachedCustomQuestions("alice", listOf(
            CustomQuestionRecord(id = "alice-1", userId = "alice")
        ))
        repository.saveCachedCustomQuestions("bob", listOf(
            CustomQuestionRecord(id = "bob-1", userId = "bob")
        ))
        repository.clearAllLocalUserData("alice")
        assertTrue(repository.loadCachedCustomQuestions("alice").isEmpty())
        assertEquals(listOf("bob-1"), repository.loadCachedCustomQuestions("bob").map { it.id })
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
