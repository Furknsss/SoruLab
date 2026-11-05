package com.example.util

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.viewmodel.AiSolverViewModel
import com.example.viewmodel.ExtractedPdfQuestionItem
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Source assets are injected privately per runner; never committed or archived. */
@RunWith(AndroidJUnit4::class)
class PdfPrivateCorpusDeviceTest {
    @Test fun nativeSourceQuestionsAndPrintedAnswersSurviveOfflineImport() = verify("native")
    @Test fun scannedSourceQuestionsIncludeFinalInkAndExactPrintedAnswersOffline() = verify("scanned")

    private fun verify(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Private source fixture requires explicit opt-in",
            InstrumentationRegistry.getArguments().getString("sorulab.privateCorpus") == "true")
        val context = instrumentation.targetContext
        val manager = context.getSystemService(ConnectivityManager::class.java)
        assertFalse("Private corpus test must run offline",
            manager.getNetworkCapabilities(manager.activeNetwork)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
        val reference = instrumentation.context.assets.open("private-corpus/reference.json").bufferedReader()
            .use { JSONObject(it.readText()).getJSONObject(name) }
        val pdf = File(context.cacheDir, "AYT-Biyoloji-$name.pdf")
        instrumentation.context.assets.open("private-corpus/$name.pdf").use { input ->
            pdf.outputStream().use { output -> input.copyTo(output) }
        }
        var model: AiSolverViewModel? = null
        var result: List<ExtractedPdfQuestionItem>? = null
        val done = CountDownLatch(1)
        try {
            instrumentation.runOnMainSync {
                model = AiSolverViewModel()
                model!!.extractFullPdfFastParallel(context, Uri.fromFile(pdf), pdf.name, "QA", "") { rows, _, _ ->
                    result = rows; done.countDown()
                }
            }
            assertTrue("Private local import timed out", done.await(8, TimeUnit.MINUTES))
            assertNull(model!!.batchErrorText.value, model!!.batchErrorText.value)
            val rows = requireNotNull(result)
            val expected = reference.getJSONArray("questions")
            assertEquals("Private source question count", expected.length(), rows.size)
            for (i in 0 until expected.length()) {
                val item = expected.getJSONObject(i)
                val row = rows.singleOrNull {
                    it.pageIndex == item.getInt("page") && it.questionNumberInTest == item.getInt("number")
                }
                assertNotNull("Missing printed source identity at reference index $i", row)
                requireNotNull(row)
                assertEquals("Wrong printed answer at reference index $i", item.getString("answer"), row.correctOption)
                assertTrue("Unverified answer at reference index $i", row.answerKeyMatched)
                if (item.has("lastInk")) {
                    val point = item.getJSONArray("lastInk")
                    val x = point.getDouble(0) / 1000.0
                    val y = point.getDouble(1) / 1000.0
                    assertTrue("Final source ink cropped away at reference index $i",
                        x >= row.normLeft && x <= row.normRight && y >= row.normTop && y <= row.normBottom)
                }
            }
        } finally {
            result.orEmpty().forEach { File(it.croppedImagePath).delete() }
            pdf.delete()
        }
    }
}
