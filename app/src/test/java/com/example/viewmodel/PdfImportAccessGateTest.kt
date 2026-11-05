package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.util.PdfVisionAccessException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfImportAccessGateTest {
    @Test
    fun deniedAccessStopsBeforeOpeningPdfAndAllowsFreshRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            var checks = 0
            val model = AiSolverViewModel {
                checks++
                throw PdfVisionAccessException("Uygulama doğrulaması başarısız.")
            }
            var completions = 0
            repeat(2) {
                model.extractFullPdfFastParallel(
                    context, Uri.parse("content://missing.invalid/book.pdf"), "book", "", ""
                ) { items, cover, title ->
                    completions++
                    assertTrue(items.isEmpty())
                    assertEquals("", cover)
                    assertEquals("", title)
                }
                runCurrent()
                // If the PDF was opened first, this would be a file error.
                assertEquals("Uygulama doğrulaması başarısız.", model.batchErrorText.value)
                assertFalse(model.isAutoTagging.value)
                assertEquals(0, model.batchProgressPercent.value)
                assertNull(model.batchStatusText.value)
            }
            assertEquals(2, checks)
            assertEquals(2, completions)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun repeatedImportTapCannotStartSecondAttestationOrCompletion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val pendingCheck = CompletableDeferred<Unit>()
            var checks = 0
            var completions = 0
            val model = AiSolverViewModel {
                checks++
                pendingCheck.await()
            }
            repeat(2) {
                model.extractFullPdfFastParallel(
                    context, Uri.parse("content://missing.invalid/book.pdf"), "book", "", ""
                ) { _, _, _ -> completions++ }
                runCurrent()
            }
            assertEquals(1, checks)
            assertEquals(0, completions)
            assertTrue(model.isAutoTagging.value)
            pendingCheck.completeExceptionally(PdfVisionAccessException("Bağlantı reddedildi."))
            runCurrent()
            assertEquals(1, completions)
            assertFalse(model.isAutoTagging.value)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
