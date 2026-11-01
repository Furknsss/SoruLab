package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SoruLabRepository
import com.example.util.ensureFirebaseInitialized
import com.example.util.getGoogleWebClientId
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SoruLab", appName)
  }

  @Test
  fun `firebase and google web client id initialize reliably with fallback`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    FirebaseApp.clearInstancesForTest()

    val initialized = ensureFirebaseInitialized(context)
    assertTrue("Firebase should initialize cleanly", initialized)

    val app = FirebaseApp.getInstance()
    assertEquals("gen-lang-client-0143749618", app.options.projectId)
    assertTrue(app.options.apiKey.isNotBlank())

    val webClientId = getGoogleWebClientId(context)
    assertTrue("Web client ID must not be blank", webClientId.isNotBlank())
    assertTrue(webClientId.endsWith(".apps.googleusercontent.com"))

    val repo = SoruLabRepository(context)
    assertNotNull(repo)
  }
}
