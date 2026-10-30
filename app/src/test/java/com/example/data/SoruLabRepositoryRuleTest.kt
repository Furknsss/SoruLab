package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class SoruLabRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var repository: SoruLabRepository

    @Before
    override fun setUp() {
        super.setUp()
        repository = SoruLabRepository(db)
    }

    private fun <T> runRepoCall(block: suspend () -> T): T {
        val task = Tasks.call(java.util.concurrent.Executors.newSingleThreadExecutor()) {
            runBlocking { block() }
        }
        return awaitTask(task)
    }

    @Test
    fun testResultCrud_asAuthenticatedOwner_succeeds() {
        val aliceUid = signInTestUser("alice@sorulab.com").user!!.uid

        val record = TestResultRecord(
            id = "res_alice_1",
            userId = aliceUid,
            examType = "TYT",
            subjectId = "tyt_mat",
            subjectName = "Matematik",
            topicId = "temel_kavramlar",
            topicName = "Temel Kavramlar",
            testId = "temel_kavramlar_odev_5",
            testTitle = "Temel Kavramlar - Ödev 5",
            correctCount = 8,
            wrongCount = 1,
            emptyCount = 1,
            durationSeconds = 360,
            answersMapJson = """{"1":"B","2":"E"}""",
            testSnapshotJson = """{"id":"temel_kavramlar_odev_5"}"""
        )

        val savedId = runRepoCall { repository.saveTestResult(record) }
        assertEquals("res_alice_1", savedId)

        val fetched = runRepoCall { repository.getTestResult("res_alice_1") }
        assertNotNull(fetched)
        assertEquals("Temel Kavramlar - Ödev 5", fetched?.testTitle)
        assertEquals(8, fetched?.correctCount)
        assertEquals(record.testSnapshotJson, fetched?.testSnapshotJson)

        val list = runRepoCall { repository.getUserTestResultsList(aliceUid) }
        assertEquals(1, list.size)

        runRepoCall {
            repository.updateTestResultCounts(
                resultId = "res_alice_1",
                correctCount = 9,
                wrongCount = 1,
                emptyCount = 0,
                answersMapJson = """{"1":"B","2":"E","3":"B"}"""
            )
        }

        val updated = runRepoCall { repository.getTestResult("res_alice_1") }
        assertEquals(9, updated?.correctCount)

        runRepoCall { repository.deleteTestResult("res_alice_1") }
    }

    @Test
    fun customQuestionCrud_andCrossUserRejection_enforcedByRules() {
        val aliceUid = signInTestUser("alice@sorulab.com").user!!.uid

        val question = CustomQuestionRecord(
            id = "q_alice_1",
            userId = aliceUid,
            examType = "AYT",
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicId = "fonksiyonlar",
            topicName = "Fonksiyonlar",
            difficulty = "Zor",
            correctOption = "D",
            questionText = "f(x) = 2x + 3 ve g(x) = x^2 - 1 ise (fog)(2) kaçtır?",
            publisherTag = "3D Yayınları",
            aiSolutionSummary = "g(2) = 3, f(3) = 9 bulunur.",
            cropAspectRatio = 1.25
        )

        val savedId = runRepoCall { repository.saveCustomQuestion(question) }
        assertEquals("q_alice_1", savedId)

        // Switch to Bob and verify cross-user read and delete are rejected with PERMISSION_DENIED
        signInTestUser("bob@sorulab.com")
        try {
            runRepoCall { repository.getCustomQuestion("q_alice_1") }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's custom question")
        } catch (e: Exception) {
            val cause = (e.cause ?: e) as? FirebaseFirestoreException
            assertNotNull(cause)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, cause?.code)
        }

        try {
            runRepoCall { repository.deleteCustomQuestion("q_alice_1") }
            fail("Expected PERMISSION_DENIED when Bob deletes Alice's custom question")
        } catch (e: Exception) {
            val cause = (e.cause ?: e) as? FirebaseFirestoreException
            assertNotNull(cause)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, cause?.code)
        }

        // Sign out and verify unauthenticated create is rejected with PERMISSION_DENIED
        auth.signOut()
        try {
            runRepoCall { repository.saveCustomQuestion(question.copy(id = "q_unauth")) }
            fail("Expected PERMISSION_DENIED for unauthenticated write")
        } catch (e: Exception) {
            val cause = (e.cause ?: e) as? FirebaseFirestoreException
            assertNotNull(cause)
            assertTrue(cause?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }
    }
}
