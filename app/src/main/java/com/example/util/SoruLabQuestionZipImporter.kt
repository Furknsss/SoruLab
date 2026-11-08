package com.example.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.viewmodel.ExtractedPdfQuestionItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Publisher-independent ZIP import for question images prepared outside Android.
 * Every image and its metadata must be independently declared and SHA-256 verified.
 * No PDF OCR is used. See docs/SORULAB_QUESTION_ZIP_V1.md.
 */
data class ImportedSoruLabQuestionPack(
    val questions: List<ExtractedPdfQuestionItem>,
    val publisher: String,
    val bookTitle: String,
    val bookCoverPath: String,
    val directory: File,
    val createdDirectory: Boolean,
    val verifiedAnswerCount: Int,
    val unresolvedAnswerCount: Int
)

object SoruLabQuestionZipImporter {
    private const val MAX_ZIP_BYTES = 300L * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 12L * 1024 * 1024
    private const val MAX_MANIFEST_BYTES = 7L * 1024 * 1024
    private const val MAX_TOTAL_IMAGE_BYTES = 420L * 1024 * 1024
    private const val MAX_QUESTIONS = 6000
    private val shaSyntax = Regex("^[a-f0-9]{64}$")
    private val bookIdSyntax = Regex("^[A-Za-z0-9_-]{4,100}$")
    private val imagePathSyntax = Regex("^images/[a-zA-Z0-9_-]{1,100}\\.(?:jpg|jpeg|png|webp)$")
    private val coverPathSyntax = Regex("^cover\\.(?:jpg|jpeg|png|webp)$")

    fun import(context: Context, uri: Uri): ImportedSoruLabQuestionPack {
        val cacheZip = File.createTempFile("sorulab-pack-", ".zip", context.cacheDir)
        var staging: File? = null
        try {
            val zipHash = MessageDigest.getInstance("SHA-256")
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("ZIP dosyası açılamadı.")
            input.use { source ->
                FileOutputStream(cacheZip).use { target ->
                    copyBounded(source, target, MAX_ZIP_BYTES, zipHash)
                }
            }
            val packKey = zipHash.digest().toHex().take(24)
            ZipFile(cacheZip).use { zip ->
                val entries = linkedMapOf<String, ZipEntry>()
                val iterator = zip.entries()
                while (iterator.hasMoreElements()) {
                    val entry = iterator.nextElement()
                    if (entry.isDirectory) continue
                    require(entries.size <= MAX_QUESTIONS + 1) { "ZIP çok fazla dosya içeriyor." }
                    require(entry.name == "manifest.json" ||
                        imagePathSyntax.matches(entry.name) || coverPathSyntax.matches(entry.name)) {
                        "ZIP içinde güvenli olmayan veya tanınmayan dosya adı var."
                    }
                    require(entries.putIfAbsent(entry.name, entry) == null) {
                        "ZIP içinde aynı dosya iki kez bulunuyor."
                    }
                }
                val manifestEntry = entries["manifest.json"]
                    ?: throw IllegalArgumentException("manifest.json bulunamadı.")
                val manifest = JSONObject(
                    String(zip.getInputStream(manifestEntry).use {
                        readBounded(it, MAX_MANIFEST_BYTES)
                    }, Charsets.UTF_8)
                )
                require(manifest.optString("format") == "sorulab.question-pack" &&
                    manifest.optInt("version", -1) == 1) {
                    "SoruLab ZIP v1 biçimi tanınmadı."
                }
                val bookId = manifest.optString("bookId")
                require(bookIdSyntax.matches(bookId)) { "Kitap kimliği geçersiz." }
                val bookTitle = manifest.optString("bookTitle").trim()
                require(bookTitle.length in 2..160) { "Kitap başlığı eksik." }
                val publisher = manifest.optString("publisher").trim()
                    .ifBlank { "Kaynak Belirtilmedi" }.take(100)
                val exam = manifest.optString("examType", "TYT").uppercase(Locale.ROOT)
                val subject = manifest.optString("subject").trim()
                require(exam in setOf("TYT", "AYT")) { "Sınav türü TYT veya AYT olmalı." }
                require(subject.isNotBlank()) { "Ders adı eksik." }
                val questions = manifest.optJSONArray("questions")
                    ?: throw IllegalArgumentException("ZIP içinde soru listesi yok.")
                val count = manifest.optInt("questionCount", -1)
                require(count in 1..MAX_QUESTIONS && questions.length() == count) {
                    "ZIP soru sayısı ile manifest uyuşmuyor."
                }

                val paths = linkedMapOf<String, String>()
                val identities = HashSet<String>()
                val cover = manifest.optString("coverImage").trim()
                if (cover.isNotEmpty()) {
                    require(coverPathSyntax.matches(cover) &&
                        shaSyntax.matches(manifest.optString("coverSha256"))) {
                        "Kapak görseli veya SHA-256 değeri geçersiz."
                    }
                    paths[cover] = manifest.getString("coverSha256")
                }
                for (i in 0 until count) {
                    val item = questions.optJSONObject(i)
                        ?: throw IllegalArgumentException("Soru metaverisi geçersiz.")
                    val path = item.optString("image")
                    val sha = item.optString("sha256")
                    require(imagePathSyntax.matches(path) && shaSyntax.matches(sha)) {
                        "Soru görseli veya SHA-256 değeri geçersiz."
                    }
                    require(paths.putIfAbsent(path, sha) == null) {
                        "Aynı görsel birden fazla soruya atanmış."
                    }
                    val page = item.optInt("pageIndex", -1)
                    val number = item.optInt("questionNumberInTest", -1)
                    val test = item.optString("sourceTestId").trim()
                    require(page in 0..9999 && number in 1..9999 && test.length in 1..160) {
                        "Soru sayfası, test kimliği veya numarası geçersiz."
                    }
                    require(identities.add(page.toString() + "|" + test + "|" + number)) {
                        "Tekrarlanan kaynak soru kimliği bulundu."
                    }
                    val answer = item.optString("answer").uppercase(Locale.ROOT)
                    require(answer.isBlank() || answer in setOf("A", "B", "C", "D", "E")) {
                        "Cevap harfi geçersiz."
                    }
                    require(!item.optBoolean("answerVerified", false) || answer.isNotBlank()) {
                        "Doğrulanmış cevabın harfi eksik."
                    }
                    val bounds = item.optJSONArray("bounds")
                    if (bounds != null) parseBounds(bounds)
                    val itemExam = item.optString("examType", exam).uppercase(Locale.ROOT)
                    require(itemExam in setOf("TYT", "AYT")) { "Soru sınav türü geçersiz." }
                }
                require(entries.keys == paths.keys + "manifest.json") {
                    "ZIP içinde eksik veya manifestte bulunmayan dosyalar var."
                }

                val assetRoot = File(context.filesDir, "questions")
                require(assetRoot.isDirectory || assetRoot.mkdirs()) {
                    "Kalıcı soru görseli klasörü oluşturulamadı."
                }
                val destination = File(assetRoot, "zip_" + packKey)
                val stagingDirectory = File(assetRoot, ".staging_" + packKey + "_" + UUID.randomUUID())
                staging = stagingDirectory
                require(stagingDirectory.mkdirs()) { "Geçici dosya alanı oluşturulamadı." }
                var totalBytes = 0L
                val dimensions = HashMap<String, Pair<Int, Int>>(paths.size)
                for ((name, expectedSha) in paths) {
                    val target = File(stagingDirectory, name)
                    require(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) {
                        "Görsel klasörü oluşturulamadı."
                    }
                    val digest = MessageDigest.getInstance("SHA-256")
                    val bytes = zip.getInputStream(entries[name]!!).use { inputImage ->
                        FileOutputStream(target).use { outputImage ->
                            copyBounded(inputImage, outputImage, MAX_IMAGE_BYTES, digest)
                        }
                    }
                    totalBytes += bytes
                    require(totalBytes <= MAX_TOTAL_IMAGE_BYTES) {
                        "ZIP açılmış görsel boyutu çok büyük."
                    }
                    require(digest.digest().toHex() == expectedSha) {
                        "Görsel SHA-256 doğrulaması başarısız: " + name
                    }
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(target.absolutePath, opts)
                    require(opts.outWidth in 120..12000 && opts.outHeight in 80..12000) {
                        "Görsel okunamıyor veya çözünürlüğü uygun değil: " + name
                    }
                    dimensions[name] = opts.outWidth to opts.outHeight
                }
                val rows = ArrayList<ExtractedPdfQuestionItem>(count)
                var verifiedCount = 0
                for (i in 0 until count) {
                    val q = questions.getJSONObject(i)
                    val imagePath = q.getString("image")
                    val (w, h) = dimensions[imagePath]!!
                    val verified = q.optBoolean("answerVerified", false)
                    if (verified) verifiedCount++
                    val correct = if (verified)
                        q.optString("answer").uppercase(Locale.ROOT) else ""
                    val bounds = q.optJSONArray("bounds")?.let(::parseBounds)
                        ?: listOf(0.0f, 0.0f, 1.0f, 1.0f)
                    rows.add(ExtractedPdfQuestionItem(
                        id = "zip_" + packKey + "_" + i,
                        questionNumber = i + 1,
                        examType = q.optString("examType", exam).uppercase(Locale.ROOT),
                        subjectName = q.optString("subject", subject),
                        topicName = q.optString("topic").ifBlank { "Konu Belirlenemedi" },
                        learningOutcome = q.optString("learningOutcome").trim().take(220),
                        questionType = q.optString("questionType").trim().take(120),
                        difficulty = q.optString("difficulty", "Orta"),
                        targetLevel = q.optString("targetLevel", "OSYM"),
                        correctOption = correct,
                        answerKeyMatched = verified,
                        publisherName = publisher,
                        publisherLogoUri = "",
                        questionText = q.optString("text").take(1800),
                        solutionSummary = q.optString("solution").take(4000),
                        croppedImagePath = File(destination, imagePath).absolutePath,
                        cropAspectRatio = w.toDouble() / h,
                        bookCoverPath = if (cover.isEmpty()) "" else File(destination, cover).absolutePath,
                        boxNormalized = bounds,
                        testName = q.optString("testName").ifBlank { q.getString("sourceTestId") },
                        bookId = bookId,
                        pageIndex = q.getInt("pageIndex"),
                        questionNumberInTest = q.getInt("questionNumberInTest"),
                        sourceTestId = q.getString("sourceTestId")
                    ))
                }

                val created = if (destination.exists()) {
                    require(destination.isDirectory) { "Önceki paket klasörü geçersiz." }
                    // Re-importing the exact same ZIP must not remove old image paths.
                    for ((name, expectedSha) in paths) {
                        val current = File(destination, name)
                        require(current.isFile && hashFile(current) == expectedSha) {
                            "Önceki paket görselleri bozulmuş. Eski kaydı korumak için aktarım durduruldu."
                        }
                    }
                    false
                } else {
                    require(stagingDirectory.renameTo(destination)) {
                        "Görseller kalıcı depoya taşınamadı."
                    }
                    staging = null
                    true
                }
                return ImportedSoruLabQuestionPack(
                    questions = rows,
                    publisher = publisher,
                    bookTitle = bookTitle,
                    bookCoverPath = if (cover.isEmpty()) "" else File(destination, cover).absolutePath,
                    directory = destination,
                    createdDirectory = created,
                    verifiedAnswerCount = verifiedCount,
                    unresolvedAnswerCount = count - verifiedCount
                )
            }
        } catch (e: java.util.zip.ZipException) {
            // Android 14+ can reject ZIP-slip entries in ZipFile's native
            // path validator *before* our per-entry validation executes.
            // Normalize the platform rejection to the same fail-closed,
            // user-readable invalid-package outcome on every Android version.
            throw IllegalArgumentException("ZIP bozuk veya güvenli olmayan dosya yolu içeriyor.", e)
        } finally {
            cacheZip.delete()
            staging?.deleteRecursively()
        }
    }

    private fun parseBounds(array: JSONArray): List<Float> {
        require(array.length() == 4) { "Kırpma sınırı dört sayı olmalı." }
        val box = (0..3).map { array.optDouble(it, Double.NaN).toFloat() }
        require(box.all { it.isFinite() && it in 0f..1f } &&
            box[0] < box[2] && box[1] < box[3]) {
            "Kırpma koordinatları geçersiz."
        }
        return box
    }

    private fun copyBounded(
        input: InputStream, output: OutputStream, maxBytes: Long, digest: MessageDigest
    ): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (n == 0) continue
            total += n
            require(total <= maxBytes) { "Dosya boyut sınırı aşıldı." }
            digest.update(buffer, 0, n)
            output.write(buffer, 0, n)
        }
        return total
    }

    private fun readBounded(input: InputStream, maxBytes: Long): ByteArray {
        val result = ByteArrayOutputStream()
        copyBounded(input, result, maxBytes, MessageDigest.getInstance("SHA-256"))
        return result.toByteArray()
    }

    private fun hashFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
