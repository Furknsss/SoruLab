package com.example.util

import androidx.compose.runtime.Immutable
import com.example.data.QuestionVisualType
import com.example.data.YksQuestion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Sayfa sütun akış düzeni:
 * - [SINGLE_COLUMN]: Tek sütun dikey akış (Geniş ekran okuma, uzun paragraflar veya büyük ölçekli sorular için).
 * - [TWO_COLUMN]: ÖSYM ve kurumsal soru bankası standartlarında ortadan ayırıcı çizgili çift sütun akışı.
 */
enum class PageColumnLayoutMode(
    val columnCount: Int,
    val label: String,
    val shortLabel: String
) {
    SINGLE_COLUMN(1, "Tek Sütun Akışı", "Tek Sütun"),
    TWO_COLUMN(2, "Çift Sütun (Kitapçık)", "Çift Sütun")
}

/**
 * ÖSYM standartlarında kitapçık türü (A / B).
 * - [A]: Standart sıralama (1..N).
 * - [B]: Belirlenimci (deterministic) ÖSYM B kitapçığı soru yer değiştirme algoritması.
 */
enum class BookletType(
    val code: String,
    val displayTitle: String,
    val badgeColorHex: Long,
    val badgeBgColorHex: Long
) {
    A(
        code = "A",
        displayTitle = "A KİTAPÇIĞI",
        badgeColorHex = 0xFF1E3A8A,
        badgeBgColorHex = 0xFFDBEAFE
    ),
    B(
        code = "B",
        displayTitle = "B KİTAPÇIĞI",
        badgeColorHex = 0xFF9A3412,
        badgeBgColorHex = 0xFFFFEDD5
    )
}

/**
 * Optik soru numarası rozet stili.
 */
enum class OpticalBadgeStyle(
    val label: String
) {
    OPTICAL_CIRCLE("Optik Daire"),
    ROUNDED_PILL("Köşeli Rozet"),
    OSYM_BOOKLET("ÖSYM Kitapçık"),
    MINIMAL("Sade Numara")
}

/**
 * Aynı sütundaki ardışık sorular arasındaki yatay ayırıcı çizgi stili.
 */
enum class QuestionSeparatorStyle(
    val label: String
) {
    SUBTLE_HAIRLINE("İnce Kitap Çizgisi"),
    SOLID_LINE("Düz Çizgi"),
    DASHED_LINE("Kesikli Çizgi"),
    NONE("Çizgisiz")
}

/**
 * Sorular arası dikey boşluk (aralık) ön ayarları.
 */
enum class QuestionSpacingPreset(
    val label: String,
    val verticalGapDp: Int,
    val normalizedSpacingFactor: Float
) {
    COMPACT("Sıkışık", 12, 0.08f),
    NORMAL("Normal", 20, 0.13f),
    SPACIOUS("Geniş", 30, 0.20f)
}

/**
 * Normalize edilmiş [0.0, 1.0] aralığında kırpma/yerleşim dikdörtgeni.
 */
@Immutable
data class NormalizedQuestionRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0.01f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.01f)
    val aspectRatio: Float get() = width / height
}

/**
 * Ölçeklendirilmiş soru boyut sonucu (Aspect Ratio korumalı).
 */
@Immutable
data class ScaledQuestionDimension(
    val widthPx: Int,
    val heightPx: Int,
    val originalAspectRatio: Float,
    val scaledAspectRatio: Float,
    val effectiveScaleFactor: Float,
    val isAspectRatioPreserved: Boolean
)

/**
 * Soru Boyut, Ölçeklendirme (%50 - %150) ve Stem Kırpma Payı Ayarları.
 */
@Immutable
data class QuestionScaleAndCropConfig(
    val scalePercent: Int = 100,
    val stemCropMarginTopRatio: Float = 0.02f,
    val stemCropMarginBottomRatio: Float = 0.02f,
    val stemCropMarginHorizontalRatio: Float = 0.02f,
    val preserveAspectRatio: Boolean = true
) {
    companion object {
        const val MIN_SCALE_PERCENT = 50
        const val MAX_SCALE_PERCENT = 150
        const val DEFAULT_SCALE_PERCENT = 100
        const val MAX_CROP_MARGIN_RATIO = 0.15f
    }

    val clampedScalePercent: Int
        get() = scalePercent.coerceIn(MIN_SCALE_PERCENT, MAX_SCALE_PERCENT)

    val scaleFactor: Float
        get() = clampedScalePercent / 100f

    fun clamped(): QuestionScaleAndCropConfig {
        return copy(
            scalePercent = clampedScalePercent,
            stemCropMarginTopRatio = stemCropMarginTopRatio.coerceIn(0f, MAX_CROP_MARGIN_RATIO),
            stemCropMarginBottomRatio = stemCropMarginBottomRatio.coerceIn(0f, MAX_CROP_MARGIN_RATIO),
            stemCropMarginHorizontalRatio = stemCropMarginHorizontalRatio.coerceIn(0f, MAX_CROP_MARGIN_RATIO)
        )
    }

    /**
     * Verilen orijinal soru boyutlarını [scaleFactor] (%50 - %150) ve kapsayıcı (container) sınırlarına
     * göre en-boy oranını (aspect ratio) bozmadan ölçeklendirir.
     */
    fun computeScaledDimensions(
        originalWidthPx: Int,
        originalHeightPx: Int,
        maxContainerWidthPx: Int,
        maxContainerHeightPx: Int = Int.MAX_VALUE
    ): ScaledQuestionDimension {
        val safeOrigW = originalWidthPx.coerceAtLeast(1)
        val safeOrigH = originalHeightPx.coerceAtLeast(1)
        val safeMaxW = maxContainerWidthPx.coerceAtLeast(1)
        val safeMaxH = maxContainerHeightPx.coerceAtLeast(1)

        val originalAspect = safeOrigW.toFloat() / safeOrigH.toFloat()
        val desiredWidth = safeOrigW * scaleFactor
        val desiredHeight = safeOrigH * scaleFactor

        return if (preserveAspectRatio) {
            val widthLimitRatio = if (desiredWidth > safeMaxW) safeMaxW / desiredWidth else 1f
            val heightLimitRatio = if (desiredHeight > safeMaxH) safeMaxH / desiredHeight else 1f
            val boundFactor = min(widthLimitRatio, heightLimitRatio)
            val finalScale = scaleFactor * boundFactor

            val finalW = (safeOrigW * finalScale).roundToInt().coerceAtLeast(1)
            val finalH = (finalW / originalAspect).roundToInt().coerceAtLeast(1)
            val finalAspect = finalW.toFloat() / finalH.toFloat()
            val preserved = abs(originalAspect - finalAspect) <= 0.05f

            ScaledQuestionDimension(
                widthPx = finalW,
                heightPx = finalH,
                originalAspectRatio = originalAspect,
                scaledAspectRatio = finalAspect,
                effectiveScaleFactor = finalScale,
                isAspectRatioPreserved = preserved
            )
        } else {
            val finalW = desiredWidth.roundToInt().coerceIn(1, safeMaxW)
            val finalH = desiredHeight.roundToInt().coerceIn(1, safeMaxH)
            val finalAspect = finalW.toFloat() / finalH.toFloat()
            ScaledQuestionDimension(
                widthPx = finalW,
                heightPx = finalH,
                originalAspectRatio = originalAspect,
                scaledAspectRatio = finalAspect,
                effectiveScaleFactor = scaleFactor,
                isAspectRatioPreserved = abs(originalAspect - finalAspect) <= 0.05f
            )
        }
    }

    /**
     * Sorunun normalize kırpma koordinatlarına stem kırpma paylarını (crop padding/margin)
     * güvenli sınırlar [0.0, 1.0] içinde uygular.
     */
    fun applyStemCropMargins(
        normLeft: Float,
        normTop: Float,
        normRight: Float,
        normBottom: Float
    ): NormalizedQuestionRect {
        val cfg = clamped()
        val rawLeft = min(normLeft, normRight).coerceIn(0f, 0.95f)
        val rawRight = max(normLeft, normRight).coerceIn(rawLeft + 0.05f, 1f)
        val rawTop = min(normTop, normBottom).coerceIn(0f, 0.95f)
        val rawBottom = max(normTop, normBottom).coerceIn(rawTop + 0.04f, 1f)

        val paddedLeft = (rawLeft - cfg.stemCropMarginHorizontalRatio).coerceIn(0f, rawRight - 0.04f)
        val paddedRight = (rawRight + cfg.stemCropMarginHorizontalRatio).coerceIn(paddedLeft + 0.04f, 1f)
        val paddedTop = (rawTop - cfg.stemCropMarginTopRatio).coerceIn(0f, rawBottom - 0.04f)
        val paddedBottom = (rawBottom + cfg.stemCropMarginBottomRatio).coerceIn(paddedTop + 0.04f, 1f)

        return NormalizedQuestionRect(
            left = paddedLeft,
            top = paddedTop,
            right = paddedRight,
            bottom = paddedBottom
        )
    }
}

/**
 * Tüm sayfa düzeni, kitapçık başlığı, sütun yapısı, soru boyutu ve ayırıcı ayarlarını tutan ana model.
 */
@Immutable
data class TestPageLayoutConfig(
    val columnMode: PageColumnLayoutMode = PageColumnLayoutMode.TWO_COLUMN,
    val bookletType: BookletType = BookletType.A,
    val bookletTitle: String = "YKS KURUMSAL DENEME КİTAPÇIĞI",
    val subjectHeader: String = "TEMEL MATEMATİK TESTİ",
    val scaleConfig: QuestionScaleAndCropConfig = QuestionScaleAndCropConfig(),
    val spacingPreset: QuestionSpacingPreset = QuestionSpacingPreset.NORMAL,
    val badgeStyle: OpticalBadgeStyle = OpticalBadgeStyle.OPTICAL_CIRCLE,
    val separatorStyle: QuestionSeparatorStyle = QuestionSeparatorStyle.SUBTLE_HAIRLINE,
    val showCenterColumnDivider: Boolean = true,
    val showBookletHeader: Boolean = true,
    val showOpticalFooter: Boolean = true,
    val maxNormalizedColumnHeight: Float = 2.10f,
    val maxQuestionsPerColumn: Int = 4
) {
    fun withScalePercent(percent: Int): TestPageLayoutConfig =
        copy(scaleConfig = scaleConfig.copy(scalePercent = percent).clamped())

    fun withColumnMode(mode: PageColumnLayoutMode): TestPageLayoutConfig =
        copy(columnMode = mode)

    fun withBookletType(type: BookletType): TestPageLayoutConfig =
        copy(bookletType = type)

    fun withSpacingPreset(preset: QuestionSpacingPreset): TestPageLayoutConfig =
        copy(spacingPreset = preset)
}

/**
 * Her sayfanın üst kısmında yer alan Kitapçık Başlığı ve Kitapçık Türü (A/B) bilgileri.
 */
@Immutable
data class BookletHeaderInfo(
    val bookletTitle: String,
    val subjectHeader: String,
    val bookletType: BookletType,
    val pageNumber: Int,
    val totalPages: Int,
    val firstQuestionNumber: Int,
    val lastQuestionNumber: Int,
    val questionRangeLabel: String,
    val opticalInstructionText: String
)

/**
 * Her sorunun sol oluğunda gösterilen Optik Soru Numarası Rozeti spesifikasyonu.
 */
@Immutable
data class OpticalBadgeSpec(
    val printedNumber: Int,
    val originalQuestionNumber: Int,
    val displayLabel: String,
    val style: OpticalBadgeStyle,
    val badgeSizeDp: Int,
    val fontSizeSp: Float,
    val primaryColorHex: Long,
    val backgroundColorHex: Long
)

/**
 * Sayfa içerisine yerleştirilmiş tek bir sorunun konum, boyut, kırpma ve rozet verisi.
 */
@Immutable
data class PlacedQuestionSlot(
    val question: YksQuestion,
    val displayNumber: Int,
    val originalNumber: Int,
    val columnIndex: Int,
    val rowIndexInColumn: Int,
    val baseNormalizedHeight: Float,
    val scaledNormalizedHeight: Float,
    val topOffsetNormalized: Float,
    val bottomOffsetNormalized: Float,
    val cropRect: NormalizedQuestionRect,
    val aspectRatio: Float,
    val opticalBadge: OpticalBadgeSpec,
    val showBottomSeparator: Boolean
)

/**
 * Tek veya çift sütun olarak dengelenmiş bir kitapçık sayfası.
 */
@Immutable
data class LayoutBookPage(
    val pageIndex: Int,
    val pageNumber: Int,
    val columnMode: PageColumnLayoutMode,
    val headerInfo: BookletHeaderInfo,
    val leftColumn: List<PlacedQuestionSlot>,
    val rightColumn: List<PlacedQuestionSlot>,
    val leftColumnUsedHeight: Float,
    val rightColumnUsedHeight: Float,
    val maxAllowedColumnHeight: Float
) {
    val allSlots: List<PlacedQuestionSlot>
        get() = leftColumn + rightColumn

    val leftQuestions: List<YksQuestion>
        get() = leftColumn.map { it.question }

    val rightQuestions: List<YksQuestion>
        get() = rightColumn.map { it.question }

    val questions: List<YksQuestion>
        get() = allSlots.map { it.question }

    val columnBalanceDelta: Float
        get() = if (columnMode == PageColumnLayoutMode.TWO_COLUMN) {
            abs(leftColumnUsedHeight - rightColumnUsedHeight)
        } else {
            0f
        }

    val isOverflowFree: Boolean
        get() {
            val leftOk = leftColumn.size <= 1 || leftColumnUsedHeight <= maxAllowedColumnHeight + 0.001f
            val rightOk = rightColumn.size <= 1 || rightColumnUsedHeight <= maxAllowedColumnHeight + 0.001f
            return leftOk && rightOk
        }
}

/**
 * TestPageLayoutEngine tarafından üretilen tam kitapçık sayfa düzeni çıktısı.
 */
@Immutable
data class TestBookletLayoutResult(
    val config: TestPageLayoutConfig,
    val pages: List<LayoutBookPage>,
    val totalQuestions: Int,
    val totalPages: Int,
    val bookletNumberToOriginalNumber: Map<Int, Int>,
    val averageColumnUtilization: Float,
    val maxColumnImbalance: Float,
    val hasAnyPageOverflow: Boolean
)

/**
 * SoruLab Test Sayfa Düzeni ve Soru Yerleşim Tasarım Motoru.
 *
 * Görevleri:
 * 1. Tek sütun / Çift sütun sayfa akışı, kitapçık başlığı, A/B kitapçık türü dönüşümü,
 *    optik soru numarası rozetleri ve soru ayırıcı çizgileri oluşturmak.
 * 2. Soruları sayfa sınırlarına göre otomatik dengelemek ve sayfa taşmalarını önlemek.
 * 3. Soruların %50 - %150 arası ölçeklendirilmesini (zoom/scale), stem kırpma paylarını ve
 *    aspect ratio korumasını hesaplamak.
 */
object TestPageLayoutEngine {

    /**
     * Kitapçık türüne (A veya B) göre soruları sıralar ve yeni kitapçık soru numaralarını atar.
     * - A Kitapçığı: Orijinal sıralamayı korur (1, 2, 3, 4...).
     * - B Kitapçığı: Belirlenimci (deterministic) ÖSYM blok-çaprazlama algoritması uygular
     *   (örneğin 4'lü bloklar halinde [3, 1, 4, 2] permütasyonu + kalan elemanların ters çevrilmesi),
     *   böylece hiçbir soru kaybolmaz, tekrar etmez ve her sorunun B kitapçığındaki karşılığı sabittir.
     */
    fun orderQuestionsForBooklet(
        questions: List<YksQuestion>,
        bookletType: BookletType
    ): List<YksQuestion> {
        if (questions.isEmpty()) return emptyList()
        if (bookletType == BookletType.A || questions.size == 1) {
            return questions.mapIndexed { idx, q ->
                val printedNum = idx + 1
                q.copy(
                    number = printedNum,
                    sourcePrintedQuestionNumber = if (q.sourcePrintedQuestionNumber > 0) {
                        q.sourcePrintedQuestionNumber
                    } else {
                        q.number
                    }
                )
            }
        }

        // B Kitapçığı belirlenimci yer değiştirme (4'lü bloklarda 3-1-4-2, 2'li/3'lü kalanlarda rotasyon)
        val reordered = mutableListOf<YksQuestion>()
        var i = 0
        while (i < questions.size) {
            val remaining = questions.size - i
            when {
                remaining >= 4 -> {
                    reordered += questions[i + 2]
                    reordered += questions[i]
                    reordered += questions[i + 3]
                    reordered += questions[i + 1]
                    i += 4
                }
                remaining == 3 -> {
                    reordered += questions[i + 1]
                    reordered += questions[i + 2]
                    reordered += questions[i]
                    i += 3
                }
                remaining == 2 -> {
                    reordered += questions[i + 1]
                    reordered += questions[i]
                    i += 2
                }
                else -> {
                    reordered += questions[i]
                    i += 1
                }
            }
        }

        return reordered.mapIndexed { idx, q ->
            val printedNum = idx + 1
            val origNum = if (q.sourcePrintedQuestionNumber > 0) q.sourcePrintedQuestionNumber else q.number
            q.copy(
                number = printedNum,
                sourcePrintedQuestionNumber = origNum
            )
        }
    }

    /**
     * Sorunun en-boy oranını (width / height) hesaplar.
     */
    fun calculateQuestionAspectRatio(
        question: YksQuestion,
        scaleConfig: QuestionScaleAndCropConfig = QuestionScaleAndCropConfig()
    ): Float {
        val crop = scaleConfig.applyStemCropMargins(
            normLeft = question.normLeft,
            normTop = question.normTop,
            normRight = question.normRight,
            normBottom = question.normBottom
        )
        val hasImage = question.croppedImagePath.isNotBlank() || question.imageBase64.isNotBlank()
        val rawW = (question.normRight - question.normLeft).coerceAtLeast(0.08f)
        val rawH = (question.normBottom - question.normTop).coerceAtLeast(0.04f)

        return if (rawW >= 0.98f && rawH >= 0.98f) {
            // Metin/vektör tabanlı veya alt-kutu koordinatı olmayan kırpılmış soru için doğal aspect ratio
            val baseHeight = estimateBaseQuestionHeight(question)
            (1.0f / baseHeight).coerceIn(0.65f, 3.50f)
        } else {
            crop.aspectRatio.coerceIn(0.45f, 5.0f)
        }
    }

    /**
     * Ölçeklendirme (%100 bazında) uygulanmadan önce sorunun temel normalize yüksekliğini tahmin eder.
     */
    fun estimateBaseQuestionHeight(question: YksQuestion): Float {
        val normalizedWidth = (question.normRight - question.normLeft).coerceAtLeast(0.08f)
        val normalizedHeight = (question.normBottom - question.normTop).coerceAtLeast(0.04f)
        val hasImage = question.croppedImagePath.isNotBlank() || question.imageBase64.isNotBlank()

        return if (normalizedWidth >= 0.98f && normalizedHeight >= 0.98f) {
            val contentLength = question.stemTop.length +
                question.stemBottom.length +
                question.bulletsOrSteps.sumOf { it.length }
            val visualBonus = if (question.visualType != QuestionVisualType.NONE) 0.14f else 0f
            val bulletBonus = (question.bulletsOrSteps.size * 0.01f).coerceAtMost(0.06f)
            if (!hasImage) {
                (0.26f + contentLength / 450f + visualBonus + bulletBonus).coerceIn(0.26f, 1.15f)
            } else if (contentLength > 0) {
                // Alt-kutu koordinatı olmayan kırpılmış görsel soru: metin ipucu varsa dengeli tahmin
                (0.34f + contentLength / 520f + visualBonus).coerceIn(0.42f, 0.95f)
            } else {
                // Standart tek kırpılmış soru görseli (sütun başına 2-3 soru sığacak gerçekçi oran)
                0.58f
            }
        } else {
            // Gerçek A4 en-boy oranı (1.414) ile PDF kırpma kutusundan sütun içi yükseklik tahmini
            (normalizedHeight / normalizedWidth * 1.414f).coerceIn(0.16f, 1.25f)
        }
    }

    /**
     * Sorunun seçili [TestPageLayoutConfig] (ölçek %50-%150, sütun modu ve kırpma payları) altındaki
     * efektif normalize yüksekliğini hesaplar.
     */
    fun estimateScaledQuestionHeight(
        question: YksQuestion,
        config: TestPageLayoutConfig
    ): Float {
        val baseHeight = estimateBaseQuestionHeight(question)
        val scaleFactor = config.scaleConfig.scaleFactor
        val cropMarginExtra = config.scaleConfig.clamped().let {
            (it.stemCropMarginTopRatio + it.stemCropMarginBottomRatio) * 0.35f
        }
        // Tek sütun modunda yatay genişlik daha fazla olduğundan metin soruları dikeyde biraz daha kompakt yerleşir
        val columnWidthAdjustment = when (config.columnMode) {
            PageColumnLayoutMode.SINGLE_COLUMN -> 0.85f
            PageColumnLayoutMode.TWO_COLUMN -> 1.0f
        }
        return ((baseHeight + cropMarginExtra) * scaleFactor * columnWidthAdjustment)
            .coerceIn(0.12f, config.maxNormalizedColumnHeight)
    }

    /**
     * Optik soru numarası rozeti spesifikasyonunu üretir.
     */
    fun buildOpticalBadgeSpec(
        printedNumber: Int,
        originalQuestionNumber: Int,
        config: TestPageLayoutConfig
    ): OpticalBadgeSpec {
        val scale = config.scaleConfig.scaleFactor
        val baseSizeDp = when (config.badgeStyle) {
            OpticalBadgeStyle.OPTICAL_CIRCLE -> 32
            OpticalBadgeStyle.ROUNDED_PILL -> 34
            OpticalBadgeStyle.OSYM_BOOKLET -> 30
            OpticalBadgeStyle.MINIMAL -> 26
        }
        val scaledSizeDp = (baseSizeDp * scale.coerceIn(0.80f, 1.25f)).roundToInt().coerceIn(22, 44)
        val scaledFontSp = (13.5f * scale.coerceIn(0.85f, 1.25f))

        val displayLabel = when (config.badgeStyle) {
            OpticalBadgeStyle.OPTICAL_CIRCLE -> printedNumber.toString()
            OpticalBadgeStyle.ROUNDED_PILL -> if (printedNumber < 10) "0$printedNumber" else "$printedNumber"
            OpticalBadgeStyle.OSYM_BOOKLET -> "$printedNumber."
            OpticalBadgeStyle.MINIMAL -> "$printedNumber."
        }

        val (primaryHex, bgHex) = when (config.badgeStyle) {
            OpticalBadgeStyle.OPTICAL_CIRCLE -> 0xFFFFFFFFL to 0xFF1E3A8AL
            OpticalBadgeStyle.ROUNDED_PILL -> 0xFF1E3A8AL to 0xFFDBEAFEL
            OpticalBadgeStyle.OSYM_BOOKLET -> 0xFF0F172AL to 0xFFF1F5F9L
            OpticalBadgeStyle.MINIMAL -> 0xFF2B5B84L to 0x00000000L
        }

        return OpticalBadgeSpec(
            printedNumber = printedNumber,
            originalQuestionNumber = originalQuestionNumber,
            displayLabel = displayLabel,
            style = config.badgeStyle,
            badgeSizeDp = scaledSizeDp,
            fontSizeSp = scaledFontSp,
            primaryColorHex = primaryHex,
            backgroundColorHex = bgHex
        )
    }

    /**
     * Verilen soru listesini ve sayfa düzeni ayarlarını kullanarak:
     * 1. Kitapçık türüne (A/B) göre soruları sıralar.
     * 2. Sayfa taşmalarını önleyerek soruları sayfalara ve sütunlara dağıtır.
     * 3. Çift sütun modunda sağ ve sol sütun yüksekliklerini otomatik dengeler.
     * 4. Son sayfada tek başına kalan (orphan) soru varsa önceki sayfayla dengeler.
     */
    fun buildBookletLayout(
        questions: List<YksQuestion>,
        config: TestPageLayoutConfig = TestPageLayoutConfig()
    ): TestBookletLayoutResult {
        val safeMaxColumnHeight = config.maxNormalizedColumnHeight.coerceAtLeast(0.50f)
        val safeMaxPerCol = config.maxQuestionsPerColumn.coerceIn(1, 10)
        val spacing = config.spacingPreset.normalizedSpacingFactor *
            config.scaleConfig.scaleFactor.coerceIn(0.75f, 1.35f)

        val orderedQuestions = orderQuestionsForBooklet(questions, config.bookletType)
        val numberMapping = orderedQuestions.associate { q ->
            val orig = if (q.sourcePrintedQuestionNumber > 0) q.sourcePrintedQuestionNumber else q.number
            q.number to orig
        }

        if (orderedQuestions.isEmpty()) {
            val emptyHeader = BookletHeaderInfo(
                bookletTitle = config.bookletTitle,
                subjectHeader = config.subjectHeader,
                bookletType = config.bookletType,
                pageNumber = 1,
                totalPages = 1,
                firstQuestionNumber = 0,
                lastQuestionNumber = 0,
                questionRangeLabel = "Soru Yok",
                opticalInstructionText = "Kitapçık Türü: ${config.bookletType.displayTitle}"
            )
            val emptyPage = LayoutBookPage(
                pageIndex = 0,
                pageNumber = 1,
                columnMode = config.columnMode,
                headerInfo = emptyHeader,
                leftColumn = emptyList(),
                rightColumn = emptyList(),
                leftColumnUsedHeight = 0f,
                rightColumnUsedHeight = 0f,
                maxAllowedColumnHeight = safeMaxColumnHeight
            )
            return TestBookletLayoutResult(
                config = config,
                pages = listOf(emptyPage),
                totalQuestions = 0,
                totalPages = 1,
                bookletNumberToOriginalNumber = emptyMap(),
                averageColumnUtilization = 0f,
                maxColumnImbalance = 0f,
                hasAnyPageOverflow = false
            )
        }

        val heights = orderedQuestions.map { q -> estimateScaledQuestionHeight(q, config) }

        // Ham sayfa gruplarını oluştur
        val rawPageSplits: MutableList<Pair<List<Int>, List<Int>>> = when (config.columnMode) {
            PageColumnLayoutMode.SINGLE_COLUMN -> {
                packSingleColumnPages(
                    questionIndices = orderedQuestions.indices.toList(),
                    heights = heights,
                    spacing = spacing,
                    maxColumnHeight = safeMaxColumnHeight,
                    maxQuestionsPerColumn = safeMaxPerCol
                )
            }
            PageColumnLayoutMode.TWO_COLUMN -> {
                packAndBalanceTwoColumnPages(
                    questionIndices = orderedQuestions.indices.toList(),
                    heights = heights,
                    spacing = spacing,
                    maxColumnHeight = safeMaxColumnHeight,
                    maxQuestionsPerColumn = safeMaxPerCol
                )
            }
        }

        val totalPages = rawPageSplits.size.coerceAtLeast(1)
        val layoutPages = rawPageSplits.mapIndexed { pageIdx, (leftIndices, rightIndices) ->
            val leftSlots = buildColumnSlots(
                indices = leftIndices,
                columnIndex = 0,
                questions = orderedQuestions,
                heights = heights,
                spacing = spacing,
                config = config
            )
            val rightSlots = buildColumnSlots(
                indices = rightIndices,
                columnIndex = 1,
                questions = orderedQuestions,
                heights = heights,
                spacing = spacing,
                config = config
            )

            val leftUsed = leftSlots.lastOrNull()?.bottomOffsetNormalized ?: 0f
            val rightUsed = rightSlots.lastOrNull()?.bottomOffsetNormalized ?: 0f
            val allPageQuestions = (leftSlots + rightSlots)
            val firstQNum = allPageQuestions.firstOrNull()?.displayNumber ?: 1
            val lastQNum = allPageQuestions.lastOrNull()?.displayNumber ?: firstQNum

            val headerInfo = BookletHeaderInfo(
                bookletTitle = config.bookletTitle,
                subjectHeader = config.subjectHeader,
                bookletType = config.bookletType,
                pageNumber = pageIdx + 1,
                totalPages = totalPages,
                firstQuestionNumber = firstQNum,
                lastQuestionNumber = lastQNum,
                questionRangeLabel = if (firstQNum == lastQNum) {
                    "Soru $firstQNum"
                } else {
                    "Sorular $firstQNum - $lastQNum"
                },
                opticalInstructionText = "${config.bookletType.displayTitle} • Optik forma kitapçık türünü kodlayınız."
            )

            LayoutBookPage(
                pageIndex = pageIdx,
                pageNumber = pageIdx + 1,
                columnMode = config.columnMode,
                headerInfo = headerInfo,
                leftColumn = leftSlots,
                rightColumn = rightSlots,
                leftColumnUsedHeight = leftUsed,
                rightColumnUsedHeight = rightUsed,
                maxAllowedColumnHeight = safeMaxColumnHeight
            )
        }

        val activeColumnHeights = layoutPages.flatMap { page ->
            if (config.columnMode == PageColumnLayoutMode.SINGLE_COLUMN) {
                listOf(page.leftColumnUsedHeight)
            } else {
                listOfNotNull(
                    page.leftColumnUsedHeight.takeIf { page.leftColumn.isNotEmpty() },
                    page.rightColumnUsedHeight.takeIf { page.rightColumn.isNotEmpty() }
                )
            }
        }

        val avgUtilization = if (activeColumnHeights.isNotEmpty()) {
            (activeColumnHeights.average().toFloat() / safeMaxColumnHeight).coerceIn(0f, 1f)
        } else {
            0f
        }
        val maxImbalance = layoutPages.maxOfOrNull { it.columnBalanceDelta } ?: 0f
        val anyOverflow = layoutPages.any { !it.isOverflowFree }

        return TestBookletLayoutResult(
            config = config,
            pages = layoutPages,
            totalQuestions = orderedQuestions.size,
            totalPages = layoutPages.size,
            bookletNumberToOriginalNumber = numberMapping,
            averageColumnUtilization = avgUtilization,
            maxColumnImbalance = maxImbalance,
            hasAnyPageOverflow = anyOverflow
        )
    }

    private fun packSingleColumnPages(
        questionIndices: List<Int>,
        heights: List<Float>,
        spacing: Float,
        maxColumnHeight: Float,
        maxQuestionsPerColumn: Int
    ): MutableList<Pair<List<Int>, List<Int>>> {
        val pages = mutableListOf<Pair<List<Int>, List<Int>>>()
        var cursor = 0

        while (cursor < questionIndices.size) {
            val col = mutableListOf<Int>()
            var currentHeight = 0f

            while (cursor < questionIndices.size && col.size < maxQuestionsPerColumn) {
                val qIdx = questionIndices[cursor]
                val qHeight = heights[qIdx]
                val gap = if (col.isEmpty()) 0f else spacing
                if (col.isNotEmpty() && currentHeight + gap + qHeight > maxColumnHeight) {
                    break
                }
                col += qIdx
                currentHeight += gap + qHeight
                cursor++
            }
            pages += (col.toList() to emptyList())
        }

        // Son sayfada tek soru (orphan) kaldıysa ve önceki sayfada >= 3 soru varsa 1 soruyu son sayfaya aktar
        if (pages.size > 1 && pages.last().first.size == 1) {
            val prevLeft = pages[pages.lastIndex - 1].first
            val lastLeft = pages.last().first
            if (prevLeft.size >= 3) {
                val movedIdx = prevLeft.last()
                val newLast = listOf(movedIdx) + lastLeft
                val newLastHeight = computeColumnTotalHeight(newLast, heights, spacing)
                if (newLastHeight <= maxColumnHeight) {
                    pages[pages.lastIndex - 1] = prevLeft.dropLast(1) to emptyList()
                    pages[pages.lastIndex] = newLast to emptyList()
                }
            }
        }

        return pages
    }

    private fun packAndBalanceTwoColumnPages(
        questionIndices: List<Int>,
        heights: List<Float>,
        spacing: Float,
        maxColumnHeight: Float,
        maxQuestionsPerColumn: Int
    ): MutableList<Pair<List<Int>, List<Int>>> {
        val n = questionIndices.size
        if (n == 0) return mutableListOf()

        val prefix = FloatArray(n + 1)
        for (i in 0 until n) {
            prefix[i + 1] = prefix[i] + heights[questionIndices[i]]
        }
        fun spanHeight(start: Int, end: Int): Float =
            if (start >= end) 0f else prefix[end] - prefix[start] + (end - start - 1) * spacing

        fun columnFits(start: Int, end: Int): Boolean {
            val count = end - start
            if (count <= 0) return true
            if (count > maxQuestionsPerColumn) return false
            if (count == 1) return true
            return spanHeight(start, end) <= maxColumnHeight + 0.001f
        }

        data class PagePlan(
            val pages: List<Pair<List<Int>, List<Int>>>,
            val penalty: Double
        )

        val plans = arrayOfNulls<PagePlan>(n + 1)
        plans[n] = PagePlan(emptyList(), 0.0)

        for (start in n - 1 downTo 0) {
            var best: PagePlan? = null
            val maxEnd = min(n, start + maxQuestionsPerColumn * 2)

            for (end in start + 1..maxEnd) {
                val isSoloPage = end - start == 1
                val isLastPage = end == n
                var bestSplitPair: Pair<List<Int>, List<Int>>? = null
                var bestPenalty = Double.POSITIVE_INFINITY

                val splits = if (isSoloPage) end..end else (start + 1) until end
                for (split in splits) {
                    if (!columnFits(start, split) || !columnFits(split, end)) continue

                    val leftH = spanHeight(start, split)
                    val rightH = spanHeight(split, end)
                    val leftFill = (leftH / maxColumnHeight).coerceAtMost(1f).toDouble()
                    val rightFill = (rightH / maxColumnHeight).coerceAtMost(1f).toDouble()

                    val fillWeight = if (isLastPage) {
                        0.85
                    } else {
                        1.0 + 0.02 * (n - end).toDouble() / n
                    }
                    val fillDeficit = fillWeight * (
                        (1.0 - leftFill).let { it * it } +
                            (1.0 - rightFill).let { it * it }
                    )
                    val imbalance = 1.2 * (leftFill - rightFill).let { it * it }
                    val emptyRightPenalty = if (split == end) {
                        if (isLastPage) 3.5 else 50.0
                    } else {
                        0.0
                    }
                    val sparseNonFinalPenalty = if (!isLastPage && leftFill < 0.45 && rightFill < 0.45) {
                        8.0
                    } else {
                        0.0
                    }

                    val penalty = fillDeficit + imbalance + emptyRightPenalty + sparseNonFinalPenalty
                    if (penalty < bestPenalty) {
                        bestPenalty = penalty
                        val leftList = questionIndices.subList(start, split).toList()
                        val rightList = questionIndices.subList(split, end).toList()
                        bestSplitPair = leftList to rightList
                    }
                }

                if (bestSplitPair == null) break
                val tail = plans[end] ?: continue
                val candidate = PagePlan(
                    pages = listOf(bestSplitPair) + tail.pages,
                    penalty = bestPenalty + tail.penalty
                )
                val previous = best
                if (previous == null ||
                    candidate.pages.size < previous.pages.size ||
                    (candidate.pages.size == previous.pages.size && candidate.penalty < previous.penalty)
                ) {
                    best = candidate
                }
            }

            plans[start] = best ?: PagePlan(
                pages = listOf(listOf(questionIndices[start]) to emptyList<Int>()) + (plans[start + 1]?.pages ?: emptyList()),
                penalty = 100.0 + (plans[start + 1]?.penalty ?: 0.0)
            )
        }

        return (plans[0]?.pages ?: emptyList()).toMutableList()
    }

    private fun computeColumnTotalHeight(
        columnIndices: List<Int>,
        heights: List<Float>,
        spacing: Float
    ): Float {
        if (columnIndices.isEmpty()) return 0f
        var total = 0f
        columnIndices.forEachIndexed { i, qIdx ->
            val gap = if (i == 0) 0f else spacing
            total += gap + heights[qIdx]
        }
        return total
    }

    private fun buildColumnSlots(
        indices: List<Int>,
        columnIndex: Int,
        questions: List<YksQuestion>,
        heights: List<Float>,
        spacing: Float,
        config: TestPageLayoutConfig
    ): List<PlacedQuestionSlot> {
        val slots = mutableListOf<PlacedQuestionSlot>()
        var currentTop = 0f

        indices.forEachIndexed { rowIdx, qIdx ->
            if (rowIdx > 0) {
                currentTop += spacing
            }
            val q = questions[qIdx]
            val baseH = estimateBaseQuestionHeight(q)
            val scaledH = heights[qIdx]
            val bottom = currentTop + scaledH
            val cropRect = config.scaleConfig.applyStemCropMargins(
                normLeft = q.normLeft,
                normTop = q.normTop,
                normRight = q.normRight,
                normBottom = q.normBottom
            )
            val aspect = calculateQuestionAspectRatio(q, config.scaleConfig)
            val origNum = if (q.sourcePrintedQuestionNumber > 0) q.sourcePrintedQuestionNumber else q.number
            val badge = buildOpticalBadgeSpec(
                printedNumber = q.number,
                originalQuestionNumber = origNum,
                config = config
            )
            val showSeparator = rowIdx < indices.lastIndex &&
                config.separatorStyle != QuestionSeparatorStyle.NONE

            slots += PlacedQuestionSlot(
                question = q,
                displayNumber = q.number,
                originalNumber = origNum,
                columnIndex = columnIndex,
                rowIndexInColumn = rowIdx,
                baseNormalizedHeight = baseH,
                scaledNormalizedHeight = scaledH,
                topOffsetNormalized = currentTop,
                bottomOffsetNormalized = bottom,
                cropRect = cropRect,
                aspectRatio = aspect,
                opticalBadge = badge,
                showBottomSeparator = showSeparator
            )
            currentTop = bottom
        }

        return slots
    }
}
