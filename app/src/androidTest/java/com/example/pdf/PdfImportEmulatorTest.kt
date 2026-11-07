package com.example.pdf

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.viewmodel.AiSolverViewModel
import com.example.viewmodel.ExtractedPdfQuestionItem
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Runs the real on-device import (PdfRenderer + ML Kit OCR + cropper) on a synthetic book and
 * compares the result with the generator's ground truth. Assets are produced by
 * scripts/gen_synthetic_book.py in CI.
 */
@RunWith(AndroidJUnit4::class)
class PdfImportEmulatorTest {
    private val catalogTopic = mapOf(
        "Sinir Sistemi" to "Sinir Sistemi",
        "Endokrin Sistem" to "Endokrin Sistem",
        "Dolaşım Sistemi" to "Dolaşım ve Bağışıklık Sistemi",
        "Üreme Sistemi ve Embriyonik Gelişim" to "Üreme Sistemi ve Embriyonik Gelişim",
        "Bitkisel Hormonlar ve Bitkilerde Hareket" to "Bitki Biyolojisi",
        "Canlılar ve Çevre" to "Canlılar ve Çevre"
    )

    @Test fun keyRowAtPageBottom() = runBook("synthetic_bottom")
    @Test fun keyRowAtPageTop() = runBook("synthetic_top")

    private fun runBook(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testAssets = instrumentation.context.assets
        val assetNames = testAssets.list("").orEmpty()
        assumeTrue("missing asset $name.pdf", "$name.pdf" in assetNames)
        val target = instrumentation.targetContext
        val pdf = File(target.cacheDir, "$name.pdf")
        testAssets.open("$name.pdf").use { input -> pdf.outputStream().use { input.copyTo(it) } }
        val truth = JSONObject(testAssets.open("$name.json").bufferedReader().readText())
        val expected = truth.getJSONArray("questions")

        var items: List<ExtractedPdfQuestionItem> = emptyList()
        var errorText: String? = null
        var warningText: String? = null
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val vm = AiSolverViewModel()
            vm.extractFullPdfFastParallel(
                context = target,
                pdfUri = Uri.fromFile(pdf),
                fileNameHint = "$name.pdf",
                publisherName = "Biyotik",
                publisherLogoUri = ""
            ) { result, _, _ ->
                items = result
                errorText = vm.batchErrorText.value
                warningText = vm.batchWarningText.value
                done.countDown()
            }
        }
        assertTrue("import timed out", done.await(40, TimeUnit.MINUTES))

        val byKey = items.groupBy { it.pageIndex to it.questionNumberInTest }
        val duplicates = byKey.filterValues { it.size > 1 }.keys
        val report = StringBuilder()
        var missing = 0; var matched = 0; var wrong = 0; var noAnswer = 0
        var wrongTopic = 0; var noOptionE = 0; var tinyCrop = 0
        val missingList = mutableListOf<String>()
        val cropDir = File(target.getExternalFilesDir(null), "crops_$name").apply { mkdirs() }
        for (i in 0 until expected.length()) {
            val q = expected.getJSONObject(i)
            val page = q.getInt("pageIndex"); val number = q.getInt("number")
            val item = byKey[page to number]?.firstOrNull()
            if (item == null) { missing++; missingList += "p${page + 1}q$number"; continue }
            if (item.answerKeyMatched && item.correctOption.isNotBlank()) {
                matched++
                if (item.correctOption != q.getString("answer")) wrong++
            } else noAnswer++
            if (item.topicName != catalogTopic[q.getString("topic")]) wrongTopic++
            if (!item.questionText.contains("E)")) noOptionE++
            val bmp = BitmapFactory.decodeFile(item.croppedImagePath)
            if (bmp == null || bmp.width < 200 || bmp.height < 60) tinyCrop++
            if (bmp != null) File(item.croppedImagePath).copyTo(
                File(cropDir, "p${page + 1}_q$number.jpg"), overwrite = true
            )
        }
        report.appendLine("[$name] beklenen=${expected.length()} bulunan=${items.size} eksik=$missing " +
            "mükerrer=${duplicates.size} cevapEşleşen=$matched cevapYanlış=$wrong cevapsız=$noAnswer " +
            "konuYanlış=$wrongTopic eksikŞıkE=$noOptionE küçükKırpma=$tinyCrop")
        report.appendLine("eksik: ${missingList.take(40)}")
        report.appendLine("hata=$errorText uyarı=$warningText")
        File(target.getExternalFilesDir(null), "report_$name.txt").writeText(report.toString())
        println(report)

        assertTrue(report.toString(),
            missing == 0 && duplicates.isEmpty() && wrong == 0 && noAnswer == 0 &&
                wrongTopic == 0 && noOptionE == 0 && tinyCrop == 0)
    }
}
