package com.example.data

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CropQualityGateTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun baseRecord(
        id: String = "q_clean",
        questionText: String = "Gerçek sayılar kümesinde tanımlı f fonksiyonu için f(3) kaçtır?",
        croppedImagePath: String = "",
        pdfFilePath: String = "",
        normLeft: Float = 0f,
        normTop: Float = 0f,
        normRight: Float = 1f,
        normBottom: Float = 1f,
        cropAspectRatio: Double = 1.25
    ): CustomQuestionRecord = CustomQuestionRecord(
        id = id,
        examType = "AYT",
        subjectId = "ayt_mat",
        subjectName = "Matematik",
        topicId = "fonksiyonlar",
        topicName = "Fonksiyonlarda Uygulamalar",
        difficulty = "Orta",
        correctOption = "C",
        questionText = questionText,
        cropAspectRatio = cropAspectRatio,
        pdfFilePath = pdfFilePath,
        normLeft = normLeft,
        normTop = normTop,
        normRight = normRight,
        normBottom = normBottom,
        aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta(
            solutionText = "C seçeneği doğrudur.",
            croppedImagePath = croppedImagePath,
            answerKeyVerified = true
        )
    )

    @Test
    fun syntheticDefaultBoxWithValidQuestionTextPassesGate() {
        val clean = baseRecord()
        assertTrue(CropQualityGate.isCleanQuestionCrop(clean))
        assertEquals(CropRejectReason.CLEAN, CropQualityGate.inspectCropQuality(clean).reason)
    }

    @Test
    fun partialCleanTwoColumnPdfCropPassesGate() {
        val cleanPdfCrop = baseRecord(
            pdfFilePath = "/storage/emulated/0/books/ayt_mat.pdf",
            normLeft = 0.06f,
            normTop = 0.12f,
            normRight = 0.48f,
            normBottom = 0.49f
        )
        assertTrue(CropQualityGate.isCleanQuestionCrop(cleanPdfCrop))
    }

    @Test
    fun microSliverAndInvertedNormalizedBoxesAreRejected() {
        val sliverWidth = baseRecord(
            normLeft = 0.20f,
            normTop = 0.10f,
            normRight = 0.28f, // width = 0.08 in 0.001..0.13
            normBottom = 0.50f
        )
        assertFalse(CropQualityGate.isCleanQuestionCrop(sliverWidth))
        assertEquals(
            CropRejectReason.MICRO_SLIVER_BOX_WIDTH,
            CropQualityGate.inspectCropQuality(sliverWidth).reason
        )

        val sliverHeight = baseRecord(
            normLeft = 0.10f,
            normTop = 0.40f,
            normRight = 0.55f,
            normBottom = 0.43f // height = 0.03 in 0.001..0.042
        )
        assertFalse(CropQualityGate.isCleanQuestionCrop(sliverHeight))
        assertEquals(
            CropRejectReason.MICRO_SLIVER_BOX_HEIGHT,
            CropQualityGate.inspectCropQuality(sliverHeight).reason
        )

        val inverted = baseRecord(
            normLeft = 0.60f,
            normTop = 0.20f,
            normRight = 0.30f,
            normBottom = 0.70f
        )
        assertFalse(CropQualityGate.isCleanQuestionCrop(inverted))
        assertEquals(
            CropRejectReason.INVERTED_CROP_BOX,
            CropQualityGate.inspectCropQuality(inverted).reason
        )

        val extremePartialAspect = baseRecord(
            normLeft = 0.05f,
            normTop = 0.10f,
            normRight = 0.92f, // width = 0.87
            normBottom = 0.24f // height = 0.14 -> ratio = 6.21 > 5.2
        )
        assertFalse(CropQualityGate.isCleanQuestionCrop(extremePartialAspect))
        assertEquals(
            CropRejectReason.EXTREME_PARTIAL_BOX_ASPECT_RATIO,
            CropQualityGate.inspectCropQuality(extremePartialAspect).reason
        )
    }

    @Test
    fun uncroppedFullPagePdfDumpIsRejectedOnlyWhenPdfPathPresent() {
        val accidentalFullPagePdf = baseRecord(
            pdfFilePath = "/storage/emulated/0/sorulab/tyt_deneme.pdf",
            normLeft = 0.0f,
            normTop = 0.0f,
            normRight = 0.99f,
            normBottom = 0.98f
        )
        assertFalse(CropQualityGate.isCleanQuestionCrop(accidentalFullPagePdf))
        assertEquals(
            CropRejectReason.UNCROPPED_FULL_PDF_PAGE_DUMP,
            CropQualityGate.inspectCropQuality(accidentalFullPagePdf).reason
        )

        val syntheticWithoutPdf = accidentalFullPagePdf.copy(
            pdfFilePath = "",
            normLeft = 0f,
            normTop = 0f,
            normRight = 1f,
            normBottom = 1f
        )
        assertTrue(CropQualityGate.isCleanQuestionCrop(syntheticWithoutPdf))
    }

    @Test
    fun existingCorruptedSmallOrSliverImageFilesOnDiskAreRejectedWhileCleanBitmapPasses() {
        val tinyCorruptFile = tempFolder.newFile("tiny_corrupt.png").apply {
            writeBytes(ByteArray(450) { 0x11 })
        }
        val tinyRecord = baseRecord(croppedImagePath = tinyCorruptFile.absolutePath)
        assertFalse(CropQualityGate.isCleanQuestionCrop(tinyRecord))
        assertEquals(
            CropRejectReason.CORRUPT_OR_EMPTY_IMAGE_FILE,
            CropQualityGate.inspectCropQuality(tinyRecord).reason
        )

        val garbageLargeFile = tempFolder.newFile("garbage_non_image.jpg").apply {
            writeBytes(ByteArray(2400) { idx -> (idx % 251).toByte() })
        }
        val garbageRecord = baseRecord(croppedImagePath = garbageLargeFile.absolutePath)
        assertFalse(CropQualityGate.isCleanQuestionCrop(garbageRecord))
        assertEquals(
            CropRejectReason.UNREADABLE_OR_SLIVER_BITMAP,
            CropQualityGate.inspectCropQuality(garbageRecord).reason
        )

        val sliverBitmapFile = writeValidPngWithMinSize(
            file = tempFolder.newFile("sliver_crop.png"),
            width = 720,
            height = 42, // height < 55 and w/h > 5.5
            minBytes = 1600
        )
        val sliverBitmapRecord = baseRecord(croppedImagePath = sliverBitmapFile.absolutePath)
        assertFalse(CropQualityGate.isCleanQuestionCrop(sliverBitmapRecord))
        assertEquals(
            CropRejectReason.UNREADABLE_OR_SLIVER_BITMAP,
            CropQualityGate.inspectCropQuality(sliverBitmapRecord).reason
        )

        val cleanBitmapFile = writeValidPngWithMinSize(
            file = tempFolder.newFile("clean_question_crop.png"),
            width = 480,
            height = 360,
            minBytes = 2000
        )
        val cleanBitmapRecord = baseRecord(
            questionText = "",
            croppedImagePath = cleanBitmapFile.absolutePath
        )
        assertTrue(CropQualityGate.isCleanQuestionCrop(cleanBitmapRecord))
    }

    @Test
    fun truncatedOcrRemnantWithoutImageIsRejectedAndFilterCleanPoolHandlesSyntheticFallback() {
        val cutOffOcr = baseRecord(questionText = "A) 12", croppedImagePath = "")
        assertFalse(CropQualityGate.isCleanQuestionCrop(cutOffOcr))
        assertEquals(
            CropRejectReason.TRUNCATED_OR_GARBAGE_TEXT_WITHOUT_IMAGE,
            CropQualityGate.inspectCropQuality(cutOffOcr).reason
        )

        val goodQuestion = baseRecord(id = "good_1")
        val mixedFiltered = CropQualityGate.filterCleanPool(listOf(cutOffOcr, goodQuestion))
        assertEquals(listOf("good_1"), mixedFiltered.map { it.id })

        // Saf sentetik birim testi havuzu ("Soru 1", "Soru 2") gerçek kırpma hatası yoksa korunur
        val syntheticUnitStubs = listOf(
            baseRecord(id = "stub_1", questionText = "Soru 1"),
            baseRecord(id = "stub_2", questionText = "Soru 2")
        )
        assertEquals(2, CropQualityGate.filterCleanPool(syntheticUnitStubs).size)
    }

    @Test
    fun osym2018To2026IntelligenceCoversEveryCatalogSubjectTopicAndAllSixArchetypes() {
        assertTrue(SoruLabCatalog.allSubjects.isNotEmpty())
        for (subject in SoruLabCatalog.allSubjects) {
            for (topic in subject.topics) {
                val profile = Osym2018To2026Intelligence.profileForTopic(subject.id, topic.id, topic.name)
                assertTrue("Measures blank for ${topic.id}", profile.whatOsymMeasures.isNotBlank())
                assertTrue("Trap blank for ${topic.id}", profile.commonOsymTrap.isNotBlank())
                assertTrue("Outcomes empty for ${topic.id}", profile.outcomes.size >= 2)
                for (qType in OsymQuestionType.entries) {
                    val fallback = Osym2018To2026Intelligence.fallbackKazanimTitle(
                        subjectId = subject.id,
                        topicId = topic.id,
                        topicName = topic.name,
                        questionType = qType,
                        deterministicSeed = 3
                    )
                    assertTrue("Fallback kazanım blank for ${topic.id} / $qType", fallback.length >= 8)
                }
            }
        }
    }

    private fun writeValidPngWithMinSize(
        file: File,
        width: Int,
        height: Int,
        minBytes: Int
    ): File {
        val out = ByteArrayOutputStream()
        // PNG Signature
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

        // IHDR chunk
        val ihdr = ByteArrayOutputStream().apply {
            writeIntBE(width)
            writeIntBE(height)
            write(8) // bit depth
            write(2) // RGB
            write(0)
            write(0)
            write(0)
        }.toByteArray()
        out.writePngChunk("IHDR", ihdr)

        // Minimal compressed scanlines + padding tEXt chunk so file exceeds minBytes cleanly
        val rawScanlines = ByteArray((width * 3 + 1) * minOf(height, 4)) { idx -> ((idx * 37) and 0xFF).toByte() }
        val deflater = Deflater(Deflater.NO_COMPRESSION)
        deflater.setInput(rawScanlines)
        deflater.finish()
        val idatBuf = ByteArray(rawScanlines.size + 64)
        val idatLen = deflater.deflate(idatBuf)
        deflater.end()
        out.writePngChunk("IDAT", idatBuf.copyOf(idatLen))

        if (out.size() < minBytes) {
            val padPayload = ByteArray(minBytes - out.size() + 64) { 'X'.code.toByte() }
            out.writePngChunk("tEXt", padPayload)
        }
        out.writePngChunk("IEND", ByteArray(0))

        // Also attempt Robolectric Bitmap compress first if non-zero; otherwise use exact spec-compliant PNG bytes
        val bmp = runCatching {
            val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val stream = ByteArrayOutputStream()
            if (b.compress(Bitmap.CompressFormat.PNG, 100, stream) && stream.size() >= minBytes) {
                stream.toByteArray()
            } else null
        }.getOrNull()

        file.writeBytes(bmp ?: out.toByteArray())
        return file
    }

    private fun ByteArrayOutputStream.writeIntBE(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writePngChunk(type: String, data: ByteArray) {
        writeIntBE(data.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        write(typeBytes)
        write(data)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }.value.toInt()
        writeIntBE(crc)
    }
}
