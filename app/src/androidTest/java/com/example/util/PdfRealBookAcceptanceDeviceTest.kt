package com.example.util

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.SoruLabRepository
import com.example.viewmodel.AiSolverViewModel
import com.example.viewmodel.ExtractedPdfQuestionItem
import com.example.viewmodel.SoruLabViewModel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * RELEASE BLOCKER — actual private PDF, never a synthetic fixture.
 *
 * The encrypted transport injects full.pdf and full-reference.json into the
 * instrumentation APK. These files are never committed, included in a release
 * APK, or uploaded with test reports. The test fails closed: NO assumeTrue,
 * no skipped test, no acceptance based merely on an APK successfully opening.
 *
 * Reference schema:
 * {
 *   "pageCount": 272,
 *   "questions": [
 *     {"page":8, "number":1, "answer":"B", "lastInk":[x1000,y1000]},
 *     ...
 *   ]
 * }
 * Reference must independently enumerate EVERY printed question. Never build
 * the reference from the OCR result that this test is supposed to validate.
 */
@RunWith(AndroidJUnit4::class)
class PdfRealBookAcceptanceDeviceTest {
    @Test
    fun realFullBookMustImportEveryQuestionThenPersistAndReload() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertEquals(
            "Real-book acceptance was not explicitly requested; no release approval",
            "true",
            InstrumentationRegistry.getArguments().getString("sorulab.realBookAcceptance")
        )
        val app = instrumentation.targetContext
        val cm = app.getSystemService(ConnectivityManager::class.java)
        assertFalse(
            "The real-book QA device must be offline",
            cm.getNetworkCapabilities(cm.activeNetwork)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        )
        val reference = instrumentation.context.assets.open("private-corpus/full-reference.json")
            .bufferedReader().use { JSONObject(it.readText()) }
        val expectedPageCount = reference.getInt("pageCount")
        val expected = reference.getJSONArray("questions")
        assertTrue("Refusing incomplete hand-picked reference", expectedPageCount >= 200)
        assertTrue("Reference must independently cover the complete textbook", expected.length() >= 1000)

        val pdfFile = File(app.cacheDir, "SoruLab-Real-Book-Acceptance.pdf")
        instrumentation.context.assets.open("private-corpus/full.pdf").use { input ->
            pdfFile.outputStream().use(input::copyTo)
        }
        assertEquals(
            "Source PDF page count differs from the independent reference",
            expectedPageCount,
            StudioImageAndPdfEngine.getPdfPageCount(app, Uri.fromFile(pdfFile))
        )
        val model = AiSolverViewModel()
        val imported = arrayOfNulls<List<ExtractedPdfQuestionItem>>(1)
        val bookCover = arrayOfNulls<String>(1)
        val bookTitle = arrayOfNulls<String>(1)
        val importFinished = CountDownLatch(1)
        try {
            instrumentation.runOnMainSync {
                model.extractFullPdfFastParallel(
                    app, Uri.fromFile(pdfFile), pdfFile.name, "RealBookQA", ""
                ) { rows, cover, title ->
                    imported[0] = rows
                    bookCover[0] = cover
                    bookTitle[0] = title
                    importFinished.countDown()
                }
            }
            assertTrue("Full-book import exceeded 35 minutes", importFinished.await(35, TimeUnit.MINUTES))
            assertNull(
                "Actual book FAILED: ${model.batchErrorText.value}. " +
                    "Diagnostics: ${model.batchWarningText.value}",
                model.batchErrorText.value
            )
            val rows = requireNotNull(imported[0])
            assertEquals("Real printed question count", expected.length(), rows.size)
            assertTrue("A question has no independently verified printed key",
                rows.all { it.answerKeyMatched && it.correctOption in setOf("A","B","C","D","E") })
            assertTrue("An extracted question has no source test identity",
                rows.all { it.questionNumberInTest > 0 && it.sourceTestId.isNotBlank() })
            assertTrue("A crop is missing or unreadable",
                rows.all { File(it.croppedImagePath).let { f -> f.isFile && f.length() > 0 } })
            val keyed = rows.groupBy { it.pageIndex to it.questionNumberInTest }
            val referenceIds = HashSet<Pair<Int, Int>>(expected.length())
            for (i in 0 until expected.length()) {
                val source = expected.getJSONObject(i)
                val page = source.getInt("page")
                val number = source.getInt("number")
                assertTrue("Out-of-range source page", page in 0 until expectedPageCount)
                assertTrue("Duplicate identity in reference (page ${page + 1}, q$number)",
                    referenceIds.add(page to number))
                val candidates = keyed[page to number].orEmpty()
                assertEquals("Missing/duplicate printed question on PDF page ${page + 1}, q$number",
                    1, candidates.size)
                val candidate = candidates.single()
                assertEquals("Wrong printed answer on page ${page + 1}, q$number",
                    source.getString("answer"), candidate.correctOption)
                if (source.has("lastInk")) {
                    val ink = source.getJSONArray("lastInk")
                    val x = ink.getDouble(0) / 1000.0
                    val y = ink.getDouble(1) / 1000.0
                    assertTrue("Last ink of question cropped away: page ${page + 1}, q$number",
                        x >= candidate.normLeft && x <= candidate.normRight &&
                            y >= candidate.normTop && y <= candidate.normBottom)
                }
            }
            assertEquals("Imported a source identity that does not exist",
                referenceIds, keyed.keys)
            assertTrue("Every question must have a printed topic", rows.all { it.topicName.isNotBlank() })

            // Run the actual production book save path and read the cache from
            // disk again; returning crops is not enough to certify a usable app.
            val saved = CountDownLatch(1)
            val saveOutcome = arrayOfNulls<Pair<Boolean, String>>(1)
            val library = SoruLabViewModel(app)
            instrumentation.runOnMainSync {
                library.saveExtractedPdfQuestionsBatch(
                    rows, "RealBookQA", "", bookTitle[0].orEmpty(), bookCover[0].orEmpty()
                ) { ok, message ->
                    saveOutcome[0] = ok to message
                    saved.countDown()
                }
            }
            assertTrue("Book save never completed", saved.await(7, TimeUnit.MINUTES))
            assertEquals("Production save failed: ${saveOutcome[0]}", true, saveOutcome[0]?.first)
            val owner = PdfLibraryAccount.GUEST_CACHE_UID
            val diskRows = SoruLabRepository(app).loadCachedCustomQuestions(owner)
                .filter { it.bookId == rows.first().bookId }
            assertEquals("Saved question count changed after disk reload", rows.size, diskRows.size)
            assertTrue("Saved book lost question image paths",
                diskRows.all { it.croppedImagePath.isNotBlank() && File(it.croppedImagePath).isFile })
        } finally {
            pdfFile.delete()
        }
    }
}
