package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class SoruLabQuestionZipImporterTest {
    @Test fun completeBookImportsAndUnverifiedAnswersRemainBlank() {
        val context = RuntimeEnvironment.getApplication()
        val id = "ziptest_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val a = png(Color.rgb(10, 85, 175))
        val b = png(Color.rgb(205, 75, 100))
        val manifest = JSONObject().apply {
            put("format", "sorulab.question-pack")
            put("version", 1)
            put("bookId", id)
            put("bookTitle", "Dr. Biyoloji Sinir Sistemi")
            put("publisher", "Dr. Biyoloji")
            put("examType", "AYT")
            put("subject", "Biyoloji")
            put("questionCount", 2)
            put("questions", JSONArray()
                .put(question("images/q0001.png", a, 1, "B", true))
                .put(question("images/q0002.png", b, 2, "C", false)))
        }
        val source = File(context.cacheDir, "test_$id.zip")
        ZipOutputStream(source.outputStream()).use { zip ->
            for ((name, data) in listOf(
                "manifest.json" to manifest.toString().toByteArray(Charsets.UTF_8),
                "images/q0001.png" to a,
                "images/q0002.png" to b
            )) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        try {
            val pack = SoruLabQuestionZipImporter.import(context, Uri.fromFile(source))
            try {
                assertEquals(2, pack.questions.size)
                assertEquals(1, pack.verifiedAnswerCount)
                assertEquals(1, pack.unresolvedAnswerCount)
                assertTrue(pack.questions.all { File(it.croppedImagePath).isFile })
                assertEquals("Dr. Biyoloji", pack.publisher)
                assertTrue(pack.questions[0].answerKeyMatched)
                assertFalse(pack.questions[1].answerKeyMatched)
                assertEquals("", pack.questions[1].correctOption)
            } finally {
                if (pack.createdDirectory) pack.directory.deleteRecursively()
            }
        } finally {
            source.delete()
        }
    }

    private fun question(path: String, image: ByteArray, number: Int, answer: String, verified: Boolean) =
        JSONObject().apply {
            put("image", path)
            put("sha256", MessageDigest.getInstance("SHA-256").digest(image)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) })
            put("pageIndex", 3)
            put("questionNumberInTest", number)
            put("sourceTestId", "sinir-sistemi~test-1")
            put("topic", "Sinir Sistemi")
            put("answer", answer)
            put("answerVerified", verified)
            put("difficulty", "Orta")
        }

    private fun png(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().use {
            try {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                it.toByteArray()
            } finally {
                bitmap.recycle()
            }
        }
    }
}
