package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.SoruLabRepository
import com.example.viewmodel.SoruLabViewModel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Runs the exact ZIP parser and the production book-save/disk-reload path. */
@RunWith(AndroidJUnit4::class)
class SoruLabQuestionZipImportDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(256, 180, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val out = ByteArrayOutputStream()
        try {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
            return out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }

    private fun zip(
        tamperHash: Boolean = false,
        duplicateIdentity: Boolean = false,
        extraUnsafePath: Boolean = false
    ): File {
        val id = "ziptest_" + UUID.randomUUID().toString().replace("-", "")
        val image1 = png()
        val image2 = png()
        val answers = JSONArray()
        for (index in 0..1) {
            val path = if (index == 0) "images/q0001.png" else "images/q0002.png"
            answers.put(JSONObject().apply {
                put("image", path)
                put("sha256", if (index == 0 && tamperHash) "0".repeat(64)
                    else sha(if (index == 0) image1 else image2))
                put("pageIndex", 8)
                put("questionNumberInTest", if (duplicateIdentity) 1 else index + 1)
                put("sourceTestId", "biyoloji~test-1")
                put("topic", "Sinir Sistemi")
                put("answer", if (index == 0) "B" else "")
                put("answerVerified", index == 0)
                put("bounds", JSONArray(listOf(0.1, 0.1, 0.4, 0.4)))
            })
        }
        val manifest = JSONObject().apply {
            put("format", "sorulab.question-pack")
            put("version", 1)
            put("bookId", id)
            put("bookTitle", "ZIP Smoke Sample")
            put("publisher", "ZIP Test Publisher")
            put("examType", "AYT")
            put("subject", "Biyoloji")
            put("questionCount", 2)
            put("questions", answers)
        }
        val file = File.createTempFile("sorulab-pack-test-", ".zip", context.cacheDir)
        ZipOutputStream(FileOutputStream(file)).use { stream ->
            fun entry(name: String, bytes: ByteArray) {
                stream.putNextEntry(ZipEntry(name))
                stream.write(bytes)
                stream.closeEntry()
            }
            entry("manifest.json", manifest.toString().toByteArray(Charsets.UTF_8))
            entry("images/q0001.png", image1)
            entry("images/q0002.png", image2)
            if (extraUnsafePath) entry("../outside.png", image2)
        }
        return file
    }

    @Test
    fun validPackImportsImagesAndPersistsViaProductionBookSave() {
        val file = zip()
        try {
            val pack = SoruLabQuestionZipImporter.import(context, Uri.fromFile(file))
            assertEquals(2, pack.questions.size)
            assertEquals(1, pack.verifiedAnswerCount)
            assertEquals(1, pack.unresolvedAnswerCount)
            assertEquals("B", pack.questions[0].correctOption)
            assertEquals("", pack.questions[1].correctOption)
            assertTrue(pack.questions.all { File(it.croppedImagePath).isFile })
            val restoredPack = SoruLabQuestionZipImporter.import(context, Uri.fromFile(file))
            assertFalse(restoredPack.createdDirectory)
            assertEquals(pack.questions[0].croppedImagePath, restoredPack.questions[0].croppedImagePath)

            val save = SoruLabViewModel(context)
            val latch = CountDownLatch(1)
            val ok = arrayOfNulls<Boolean>(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                save.saveExtractedPdfQuestionsBatch(
                    pack.questions, pack.publisher, "", pack.bookTitle, pack.bookCoverPath
                ) { succeeded, _ ->
                    ok[0] = succeeded
                    latch.countDown()
                }
            }
            assertTrue("Production ZIP book save did not finish", latch.await(30, TimeUnit.SECONDS))
            assertEquals("Production ZIP book save failed", true, ok[0])
            val disk = SoruLabRepository(context).loadCachedCustomQuestions(
                PdfLibraryAccount.GUEST_CACHE_UID
            ).filter { it.bookId == pack.questions[0].bookId }
            assertEquals("Two ZIP questions must survive a disk reload", 2, disk.size)
            assertTrue(disk.all { File(it.croppedImagePath).isFile })
            assertEquals(1, disk.count { it.correctOption == "B" })
            assertEquals(1, disk.count { it.correctOption.isBlank() })
        } finally {
            file.delete()
        }
    }

    @Test
    fun alteredImageHashFailsClosed() {
        val file = zip(tamperHash = true)
        try {
            assertRejected(file)
        } finally {
            file.delete()
        }
    }

    @Test
    fun duplicateQuestionIdentityFailsClosed() {
        val file = zip(duplicateIdentity = true)
        try {
            assertRejected(file)
        } finally {
            file.delete()
        }
    }

    @Test
    fun zipSlipPathFailsClosed() {
        val file = zip(extraUnsafePath = true)
        try {
            assertRejected(file)
        } finally {
            file.delete()
        }
    }

    private fun assertRejected(file: File) {
        try {
            SoruLabQuestionZipImporter.import(context, Uri.fromFile(file))
            fail("Malformed question pack must never be imported")
        } catch (_: IllegalArgumentException) {
            // fail closed
        }
    }
}
