package com.example.base

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
abstract class FirestoreEmulatorTestBase {

    protected lateinit var db: FirebaseFirestore
    protected lateinit var auth: FirebaseAuth
    protected lateinit var databaseId: String

    protected val projectId = "demo-test"
    private val firestoreHost = "127.0.0.1"
    private val firestorePort = 8085
    private val authPort = 9099

    @Before
    open fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        databaseId = context.getString(R.string.firestore_database_id)

        val options = FirebaseOptions.Builder()
            .setProjectId(projectId)
            .setApplicationId("1:1234567890:android:abcdef")
            .setApiKey("fake-api-key-for-emulator")
            .build()

        val app = try {
            FirebaseApp.initializeApp(context, options)!!
        } catch (e: IllegalStateException) {
            FirebaseApp.getInstance()
        }

        auth = FirebaseAuth.getInstance(app)
        try {
            auth.useEmulator(firestoreHost, authPort)
        } catch (_: IllegalStateException) {
            // Already connected
        }

        db = FirebaseFirestore.getInstance(app, databaseId)
        try {
            db.useEmulator(firestoreHost, firestorePort)
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            db.firestoreSettings = settings
        } catch (_: IllegalStateException) {
            // Already configured
        }

        clearFirestoreEmulatorData()
        clearAuthEmulatorAccounts()
    }

    @After
    open fun tearDown() {
        try {
            awaitTask(db.terminate())
            FirebaseApp.clearInstancesForTest()
        } catch (_: Exception) {
            // Ignore cleanup failure
        }
    }

    protected fun <T> awaitTask(task: Task<T>, timeoutSeconds: Long = 10): T {
        val start = System.currentTimeMillis()
        while (!task.isComplete) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() - start > TimeUnit.SECONDS.toMillis(timeoutSeconds)) {
                throw TimeoutException("Task timed out after $timeoutSeconds seconds")
            }
            Thread.sleep(10)
        }
        if (task.isSuccessful) {
            return task.result
        } else {
            throw task.exception ?: RuntimeException("Task failed with no exception")
        }
    }

    protected fun signInTestUser(email: String, password: String = "password123"): AuthResult {
        return try {
            awaitTask(auth.createUserWithEmailAndPassword(email, password))
        } catch (e: Exception) {
            awaitTask(auth.signInWithEmailAndPassword(email, password))
        }
    }

    protected fun clearFirestoreEmulatorData() {
        val encodedDbId = java.net.URLEncoder.encode(databaseId, "UTF-8")
        val url = URL("http://$firestoreHost:$firestorePort/emulator/v1/projects/$projectId/databases/$encodedDbId/documents")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.connectTimeout = 1000
        conn.readTimeout = 1000
        try {
            conn.responseCode
        } catch (_: java.net.ConnectException) {
            org.junit.Assume.assumeTrue("Firebase emulator is not running on $firestoreHost:$firestorePort", false)
        } catch (_: Exception) {
            // ignore
        } finally {
            conn.disconnect()
        }
    }

    protected fun clearAuthEmulatorAccounts() {
        val url = URL("http://$firestoreHost:$authPort/emulator/v1/projects/$projectId/accounts")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.connectTimeout = 1000
        conn.readTimeout = 1000
        try {
            conn.responseCode
        } catch (_: java.net.ConnectException) {
            org.junit.Assume.assumeTrue("Firebase Auth emulator is not running on $firestoreHost:$authPort", false)
        } catch (_: Exception) {
            // ignore
        } finally {
            conn.disconnect()
        }
    }
}
