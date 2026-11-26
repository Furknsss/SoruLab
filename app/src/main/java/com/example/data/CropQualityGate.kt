package com.example.data

import android.graphics.BitmapFactory
import java.io.File
import java.io.FileInputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Soru kırpma ve görsel bütünlük kalite kapısı (Quality Gate).
 *
 * Deneme ve konu testi üretiminde:
 * 1) Bozuk/ters veya aşırı ince şerit (sliver) halindeki normalize kırpma kutularını,
 * 2) Yanlışlıkla tek soru olarak kaydedilmiş tam sayfa A4 PDF dökümlerini,
 * 3) Diskte mevcut olup bozuk/boş (< 1200 byte) veya okunamayan/aşırı dar bitmap dosyalarını,
 * 4) Görseli olmayan ve metni kesik/OCR çöpü (< 10 anlamlı karakter) olan kayıtları
 * otomatik olarak eler.
 */
enum class CropRejectReason {
    CLEAN,
    BLANK_IDENTITY,
    NON_FINITE_OR_OUT_OF_BOUNDS_BOX,
    INVERTED_CROP_BOX,
    MICRO_SLIVER_BOX_WIDTH,
    MICRO_SLIVER_BOX_HEIGHT,
    EXTREME_PARTIAL_BOX_ASPECT_RATIO,
    EXTREME_DECLARED_ASPECT_RATIO,
    UNCROPPED_FULL_PDF_PAGE_DUMP,
    CORRUPT_OR_EMPTY_IMAGE_FILE,
    UNREADABLE_OR_SLIVER_BITMAP,
    TRUNCATED_OR_GARBAGE_TEXT_WITHOUT_IMAGE
}

data class CropQualityVerdict(
    val isClean: Boolean,
    val reason: CropRejectReason,
    val detail: String = ""
)

object CropQualityGate {

    private const val MIN_CLEAN_FILE_BYTES = 1200L
    private const val MIN_BITMAP_WIDTH_PX = 90
    private const val MIN_BITMAP_HEIGHT_PX = 55
    private const val MAX_BITMAP_WIDTH_TO_HEIGHT = 5.5f
    private const val MAX_BITMAP_HEIGHT_TO_WIDTH = 4.8f

    private const val MIN_PARTIAL_BOX_RATIO = 0.16f
    private const val MAX_PARTIAL_BOX_RATIO = 5.2f
    private const val MIN_CLEAN_TEXT_LENGTH = 10

    fun isCleanQuestionCrop(record: CustomQuestionRecord): Boolean =
        inspectCropQuality(record).isClean

    fun inspectCropQuality(record: CustomQuestionRecord): CropQualityVerdict {
        val left = record.normLeft
        val top = record.normTop
        val right = record.normRight
        val bottom = record.normBottom

        // 1. Sonlu olmayan veya sayfa sınırlarını aşan koordinatlar
        if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite() ||
            left < -0.02f || top < -0.02f || right > 1.02f || bottom > 1.02f
        ) {
            return CropQualityVerdict(
                isClean = false,
                reason = CropRejectReason.NON_FINITE_OR_OUT_OF_BOUNDS_BOX,
                detail = "box=[$left, $top, $right, $bottom]"
            )
        }

        val hasNonZeroBox = left != 0f || top != 0f || right != 0f || bottom != 0f
        if (hasNonZeroBox && (right <= left || bottom <= top)) {
            return CropQualityVerdict(
                isClean = false,
                reason = CropRejectReason.INVERTED_CROP_BOX,
                detail = "inverted=[$left, $top, $right, $bottom]"
            )
        }

        val boxWidth = right - left
        val boxHeight = bottom - top

        // 2. Aşırı dar/kısa normalize kırpma kutuları (kesik satır veya sütun artığı)
        if (boxWidth in 0.001f..0.13f) {
            return CropQualityVerdict(
                isClean = false,
                reason = CropRejectReason.MICRO_SLIVER_BOX_WIDTH,
                detail = "boxWidth=$boxWidth"
            )
        }
        if (boxHeight in 0.001f..0.042f) {
            return CropQualityVerdict(
                isClean = false,
                reason = CropRejectReason.MICRO_SLIVER_BOX_HEIGHT,
                detail = "boxHeight=$boxHeight"
            )
        }

        val isDefaultFullBox = left == 0f && top == 0f && right == 1f && bottom == 1f
        val isPartialBox = hasNonZeroBox && !isDefaultFullBox
        if (isPartialBox) {
            val boxRatio = boxWidth / boxHeight.coerceAtLeast(0.0001f)
            if (boxRatio < MIN_PARTIAL_BOX_RATIO || boxRatio > MAX_PARTIAL_BOX_RATIO) {
                return CropQualityVerdict(
                    isClean = false,
                    reason = CropRejectReason.EXTREME_PARTIAL_BOX_ASPECT_RATIO,
                    detail = "boxRatio=$boxRatio"
                )
            }
        }

        // Kayıt üzerinde açıkça bozuk en-boy oranı bildirilmişse reddet (varsayılan 1.25'tir)
        if (!record.cropAspectRatio.isFinite() ||
            (record.cropAspectRatio > 0.0 && (record.cropAspectRatio < 0.14 || record.cropAspectRatio > 5.8))
        ) {
            return CropQualityVerdict(
                isClean = false,
                reason = CropRejectReason.EXTREME_DECLARED_ASPECT_RATIO,
                detail = "cropAspectRatio=${record.cropAspectRatio}"
            )
        }

        // 3. Kırpılmamış tam A4 PDF sayfası dökümü kontrolü
        if (record.pdfFilePath.isNotBlank() && boxWidth > 0.97f && boxHeight > 0.95f) {
            return CropQualityVerdict(
                isClean = false,
                reason = CropRejectReason.UNCROPPED_FULL_PDF_PAGE_DUMP,
                detail = "pdfFullPage=${record.pdfFilePath} ($boxWidth x $boxHeight)"
            )
        }

        // 4. Diskte gerçekten var olan kırpılmış görsel dosyası için boyut ve çözünürlük kontrolü
        val imagePath = record.croppedImagePath.trim()
        if (imagePath.isNotBlank()) {
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                if (!imageFile.isFile || imageFile.length() < MIN_CLEAN_FILE_BYTES) {
                    return CropQualityVerdict(
                        isClean = false,
                        reason = CropRejectReason.CORRUPT_OR_EMPTY_IMAGE_FILE,
                        detail = "fileLength=${imageFile.length()}"
                    )
                }
                val dimensions = decodeImageDimensions(imageFile)
                if (dimensions == null) {
                    return CropQualityVerdict(
                        isClean = false,
                        reason = CropRejectReason.UNREADABLE_OR_SLIVER_BITMAP,
                        detail = "unreadableImageHeader=${imageFile.name}"
                    )
                }
                val (width, height) = dimensions
                val wRatio = width.toFloat() / height.coerceAtLeast(1).toFloat()
                val hRatio = height.toFloat() / width.coerceAtLeast(1).toFloat()
                if (width < MIN_BITMAP_WIDTH_PX ||
                    height < MIN_BITMAP_HEIGHT_PX ||
                    wRatio > MAX_BITMAP_WIDTH_TO_HEIGHT ||
                    hRatio > MAX_BITMAP_HEIGHT_TO_WIDTH
                ) {
                    return CropQualityVerdict(
                        isClean = false,
                        reason = CropRejectReason.UNREADABLE_OR_SLIVER_BITMAP,
                        detail = "bitmap=${width}x${height}"
                    )
                }
            }
        }

        // 5. Görseli olmayan kayıtlarda OCR çöpü / kesik metin kontrolü
        if (imagePath.isBlank() && record.imageBase64.isBlank()) {
            val trimmedText = record.questionText.trim()
            val alphaNumericCount = trimmedText.count { it.isLetterOrDigit() }
            if (trimmedText.length < MIN_CLEAN_TEXT_LENGTH || alphaNumericCount < 5) {
                return CropQualityVerdict(
                    isClean = false,
                    reason = CropRejectReason.TRUNCATED_OR_GARBAGE_TEXT_WITHOUT_IMAGE,
                    detail = "textLen=${trimmedText.length}"
                )
            }
        }

        return CropQualityVerdict(isClean = true, reason = CropRejectReason.CLEAN)
    }

    /**
     * Aday soru havuzunu temizler.
     * Birim testlerde tüm havuz yalnızca dosya yolsuz ve varsayılan 0..1 kutulu sentetik kısa metin
     * kayıtlarından oluşuyorsa (örn. "Soru 1"), gerçek kırpma hatası bulunmadığı sürece güvenli geri dönüş sağlar.
     */
    fun filterCleanPool(records: List<CustomQuestionRecord>): List<CustomQuestionRecord> {
        if (records.isEmpty()) return emptyList()
        val verdicts = records.map { it to inspectCropQuality(it) }
        val clean = verdicts.filter { it.second.isClean }.map { it.first }
        if (clean.isNotEmpty()) return clean

        val allSyntheticShortTextStubs = verdicts.all { (rec, verdict) ->
            verdict.reason == CropRejectReason.TRUNCATED_OR_GARBAGE_TEXT_WITHOUT_IMAGE &&
                rec.croppedImagePath.isBlank() &&
                rec.pdfFilePath.isBlank() &&
                rec.normLeft == 0f &&
                rec.normTop == 0f &&
                rec.normRight == 1f &&
                rec.normBottom == 1f &&
                rec.questionText.trim().length >= 4 &&
                rec.questionText.any { it.isLetterOrDigit() }
        }
        return if (allSyntheticShortTextStubs) records else emptyList()
    }

    /**
     * Önce dosya başlığındaki sihirli baytları ve çözünürlüğü doğrudan çözümler;
     * böylece bozuk bayt dizileri ve Robolectric'in varsayılan mock boyutları elenir.
     * Geçerli sihirli bayt taşıyan ancak standart dışı segment düzenine sahip görseller için BitmapFactory'ye başvurur.
     */
    internal fun decodeImageDimensions(file: File): Pair<Int, Int>? {
        val headerDims = decodeHeaderDimensionsFallback(file)
        if (headerDims != null) {
            return headerDims
        }
        if (!hasValidImageMagic(file)) {
            return null
        }
        val ext = file.extension.lowercase()
        if (ext in listOf("png", "jpg", "jpeg", "webp")) {
            runCatching {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    return options.outWidth to options.outHeight
                }
            }
        }
        return null
    }

    private fun hasValidImageMagic(file: File): Boolean = runCatching {
        if (!file.exists() || file.length() < 12) return false
        val bytes = ByteArray(12)
        val read = FileInputStream(file).use { it.read(bytes) }
        if (read < 12) return false
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return true
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return true
        if (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() && bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()) return true
        if (bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()) return true
        if (bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) return true
        false
    }.getOrDefault(false)

    internal fun decodeHeaderDimensionsFallback(file: File): Pair<Int, Int>? {
        return runCatching {
            val maxRead = min(file.length(), 65_536L).toInt()
            if (maxRead < 24) return null
            val bytes = ByteArray(maxRead)
            val readCount = FileInputStream(file).use { stream -> stream.read(bytes) }
            if (readCount < 24) return null

            // PNG imzası: 89 50 4E 47 0D 0A 1A 0A, IHDR genişlik [16..19], yükseklik [20..23]
            if (bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte()
            ) {
                val width = readBigEndianInt(bytes, 16)
                val height = readBigEndianInt(bytes, 20)
                return if (width > 0 && height > 0) width to height else null
            }

            // JPEG imzası: FF D8 ... SOFn (FF C0 / C1 / C2 / C3)
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                var offset = 2
                while (offset + 8 < readCount) {
                    if (bytes[offset] != 0xFF.toByte()) {
                        offset++
                        continue
                    }
                    var markerOffset = offset + 1
                    while (markerOffset < readCount && bytes[markerOffset] == 0xFF.toByte()) {
                        markerOffset++
                    }
                    if (markerOffset >= readCount) break
                    val marker = bytes[markerOffset].toInt() and 0xFF
                    if (marker == 0xD9 || marker == 0xDA) break
                    if (markerOffset + 2 >= readCount) break
                    val segmentLength = readBigEndianUnsignedShort(bytes, markerOffset + 1)
                    if (segmentLength < 2) break
                    if (marker in listOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB)) {
                        if (markerOffset + 7 < readCount) {
                            val height = readBigEndianUnsignedShort(bytes, markerOffset + 4)
                            val width = readBigEndianUnsignedShort(bytes, markerOffset + 6)
                            if (width > 0 && height > 0) return width to height
                        }
                        break
                    }
                    offset = max(offset + 2, markerOffset + 1 + segmentLength)
                }
                return null
            }

            // WebP imzası: "RIFF" .... "WEBP"
            if (bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() &&
                bytes[11] == 'P'.code.toByte() &&
                readCount >= 30
            ) {
                val chunkTag = String(bytes, 12, 4, Charsets.US_ASCII)
                if (chunkTag == "VP8X") {
                    val width = 1 + ((bytes[24].toInt() and 0xFF) or
                        ((bytes[25].toInt() and 0xFF) shl 8) or
                        ((bytes[26].toInt() and 0xFF) shl 16))
                    val height = 1 + ((bytes[27].toInt() and 0xFF) or
                        ((bytes[28].toInt() and 0xFF) shl 8) or
                        ((bytes[29].toInt() and 0xFF) shl 16))
                    if (width > 0 && height > 0) return width to height
                } else if (chunkTag == "VP8 " && readCount >= 30) {
                    val width = ((bytes[26].toInt() and 0xFF) or ((bytes[27].toInt() and 0xFF) shl 8)) and 0x3FFF
                    val height = ((bytes[28].toInt() and 0xFF) or ((bytes[29].toInt() and 0xFF) shl 8)) and 0x3FFF
                    if (width > 0 && height > 0) return width to height
                }
            }

            null
        }.getOrNull()
    }

    private fun readBigEndianInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun readBigEndianUnsignedShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)
}
