package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AiStructuredSolution
import com.example.data.DifficultyLevel
import com.example.data.SoruLabCatalog
import com.example.data.SoruLabPublisherPresets
import com.example.data.YksPublisherInfo
import com.example.data.YksQuestion
import com.example.data.resolveAnswerKeyOption
import com.example.util.QuestionNumberAnchor
import com.example.util.QuestionOptionAnchor
import com.example.util.PdfAnswerScope
import com.example.util.PdfHorizontalAnswerGrid
import com.example.util.PdfHorizontalAnswerGridReader
import com.example.util.PdfAdaptiveAnswerRows
import com.example.util.PdfOcrTextCell
import com.example.util.verifiedPrintedQuestionNumber
import com.example.util.PdfScopedAnswerKeys
import com.example.util.StudioImageAndPdfEngine
import com.example.util.PdfPaddleMobileOcr
import com.example.util.PdfPrintedKeyCoverageAudit
import com.example.util.PdfAnswerId
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class AiChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val attachedQuestionTitle: String? = null,
    val timestampLabel: String = "Şimdi"
)

data class AutoTagSuggestion(
    val examType: String = "TYT",
    val subjectName: String = "",
    val topicName: String = "",
    val difficulty: String = "Orta",
    val targetLevel: String = "OSYM",
    val correctOption: String = "",
    val questionNumber: Int = 0,
    val answerKeyVerified: Boolean = false,
    val questionStemSummary: String = "",
    val solutionHint: String = ""
)

@Immutable
data class TwinQuestionModalData(
    val topicTitle: String = "",
    val difficultyLabel: String = "Orta",
    val questionStem: String = "",
    val visualType: com.example.data.QuestionVisualType = com.example.data.QuestionVisualType.NONE,
    val options: Map<String, String> = mapOf(
        "A" to "",
        "B" to "",
        "C" to "",
        "D" to "",
        "E" to ""
    ),
    val correctOption: String = "C",
    val solutionExplanation: String = "",
    /** Exact source crop; never display invented text in place of the image. */
    val croppedImagePath: String = "",
    val imageBase64: String = "",
    val sourceQuestionId: String = "",
    val sourceDescription: String = "",
    val errorMessage: String? = null
)

@Immutable
data class ExtractedPdfQuestionItem(
    val id: String,
    val questionNumber: Int,
    val examType: String,
    val subjectName: String,
    val topicName: String,
    val difficulty: String,
    val targetLevel: String,
    val correctOption: String,
    val answerKeyMatched: Boolean,
    val publisherName: String,
    val publisherLogoUri: String,
    val questionText: String,
    val solutionSummary: String,
    val aiCozum: AiStructuredSolution = AiStructuredSolution(),
    val croppedImagePath: String,
    val cropAspectRatio: Double,
    val bookCoverPath: String = "",
    val boxNormalized: List<Float> = listOf(0.05f, 0.05f, 0.95f, 0.48f),
    val testName: String = "",
    val bookId: String = "",
    val imageBase64: String = "",
    val pdfFilePath: String = "",
    val pageIndex: Int = -1,
    val questionNumberInTest: Int = 0,
    val sourceTestId: String = "",
    val learningOutcome: String = "",
    val questionType: String = ""
) {
    val imagePath: String get() = croppedImagePath
    val subject: String get() = subjectName
    val topic: String get() = topicName
    val normTop: Float get() = boxNormalized.getOrNull(0)?.coerceIn(0f, 1f) ?: 0f
    val normLeft: Float get() = boxNormalized.getOrNull(1)?.coerceIn(0f, 1f) ?: 0f
    val normBottom: Float get() = boxNormalized.getOrNull(2)?.coerceIn(0f, 1f) ?: 1f
    val normRight: Float get() = boxNormalized.getOrNull(3)?.coerceIn(0f, 1f) ?: 1f
}

private data class QuestionOcrClassification(
    val questionText: String,
    val examType: String,
    val subjectName: String,
    val topicName: String,
    val difficulty: String,
    val targetLevel: String
)

private data class PageQuestionCandidate(
    val cropBox: StudioImageAndPdfEngine.LocalCroppedBoxResult,
    val classification: QuestionOcrClassification,
    val answerKeyQuestionNumber: Int? = null
)

private data class PdfPageOcrLayout(
    val text: String = "",
    val headerText: String = "",
    val scopeHeaderText: String = "",
    val answerRows: List<String> = emptyList(),
    val answerRegions: List<List<String>> = emptyList(),
    val answerGrids: List<PdfHorizontalAnswerGrid> = emptyList(),
    val questionNumberAnchors: List<QuestionNumberAnchor> = emptyList(),
    val optionAnchors: List<QuestionOptionAnchor> = emptyList(),
    val positionedText: List<PdfOcrTextCell> = emptyList()
)

@Immutable
data class BatchPdfCropState(
    val isAnalyzing: Boolean = false,
    val progressPercent: Int = 0,
    val progressMessage: String = "",
    val errorMessage: String? = null,
    val warningMessage: String? = null
)

private data class PdfLocalProcessingStatus(
    @Volatile var ocrFailureCount: Int = 0,
)

class AiSolverViewModel : ViewModel() {

    private val initialWelcomeMessage = AiChatMessage(
        id = "welcome",
        isUser = false,
        text = "PDF soruların cihazında ücretsiz işlenir. Kayıtlı soru çözümlerini burada görüntüleyebilirsin. Bu sürümde bulutla soru çözümü ve üretimi kapalıdır."
    )

    private val _messages = MutableStateFlow(listOf(initialWelcomeMessage))
    val messages: StateFlow<List<AiChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isAutoTagging = MutableStateFlow(false)
    val isAutoTagging: StateFlow<Boolean> = _isAutoTagging.asStateFlow()

    private val _batchProgressPercent = MutableStateFlow(0)
    val batchProgressPercent: StateFlow<Int> = _batchProgressPercent.asStateFlow()

    private val _batchStatusText = MutableStateFlow<String?>(null)
    val batchStatusText: StateFlow<String?> = _batchStatusText.asStateFlow()

    private val _batchErrorText = MutableStateFlow<String?>(null)
    val batchErrorText: StateFlow<String?> = _batchErrorText.asStateFlow()

    private val _batchWarningText = MutableStateFlow<String?>(null)
    val batchWarningText: StateFlow<String?> = _batchWarningText.asStateFlow()

    fun clearBatchError() {
        _batchErrorText.value = null
        _batchWarningText.value = null
    }

    private var lastAskedQuestionId: String? = null

    fun clearChatHistory() {
        lastAskedQuestionId = null
        _messages.value = listOf(initialWelcomeMessage)
    }

    fun askAboutQuestion(question: YksQuestion, context: Context, customPrompt: String = "") {
        _messages.value = _messages.value + AiChatMessage(
            id = "local_${System.currentTimeMillis()}", isUser = false,
            text = buildStructuredSolutionForQuestion(question),
            attachedQuestionTitle = "${question.topicHeader} • Soru ${question.number}"
        )
    }

    fun generateQuestionSolutionOnDemand(
        question: YksQuestion, context: Context, onResult: (String) -> Unit
    ) {
        onResult(buildStructuredSolutionForQuestion(question))
    }

    private fun buildStructuredSolutionForQuestion(question: YksQuestion): String =
        question.stepByStepSolution.trim().takeIf { it.isNotBlank() }
            ?: "Bu soru için kayıtlı çözüm bulunmuyor. Ücretsiz sürümde bulutla çözüm üretilmez."

    private val twinGenerationEngine by lazy { TwinQuestionGenerationEngine() }

    fun generateTwinQuestionOnDemand(
        question: YksQuestion, context: Context, onResult: (TwinQuestionModalData) -> Unit
    ) {
        // Network calls are triggered only by the explicit Twin button. Neither
        // PDF imports nor ordinary solution controls call the cloud model.
        viewModelScope.launch {
            val result = twinGenerationEngine.generate(question, context.applicationContext)
            onResult(result)
        }
    }

    private suspend fun classifyCroppedQuestion(
        context: Context,
        imagePath: String,
        examTypeHint: String,
        subjectNameHint: String,
        pageTopicHint: String,
        visualDifficultyHint: String,
        hasDiagramOrGraph: Boolean,
        lineCountEstimate: Int,
        processingStatus: PdfLocalProcessingStatus,
        reader: TextRecognizer
    ): QuestionOcrClassification {
        val questionBitmap = withContext(Dispatchers.IO) {
            StudioImageAndPdfEngine.getCachedBitmap(context, imagePath, maxDimension = 1440)
        }
        if (questionBitmap == null) {
            processingStatus.ocrFailureCount++
            return buildQuestionOcrClassification(
                questionText = "",
                examTypeHint = examTypeHint,
                subjectNameHint = subjectNameHint,
                suggestedTopicName = pageTopicHint,
                trustSuggestedTopic = false,
                hasDiagramOrGraph = hasDiagramOrGraph,
                lineCountEstimate = lineCountEstimate,
                visualDifficultyHint = visualDifficultyHint
            )
        }

        val localText = try {
            recognizeQuestionText(questionBitmap, reader)
        } catch (e: CancellationException) {
            throw e
        } catch (_: OutOfMemoryError) {
            processingStatus.ocrFailureCount++
            ""
        } catch (_: Exception) {
            processingStatus.ocrFailureCount++
            ""
        }
        return buildQuestionOcrClassification(
            questionText = localText,
            examTypeHint = examTypeHint,
            subjectNameHint = subjectNameHint,
            suggestedTopicName = pageTopicHint,
            trustSuggestedTopic = false,
            hasDiagramOrGraph = hasDiagramOrGraph,
            lineCountEstimate = lineCountEstimate,
            visualDifficultyHint = visualDifficultyHint
        )
    }

    private suspend fun recognizePdfPageLayout(
        bitmap: Bitmap,
        reader: TextRecognizer,
        nativeFooterKeys: Map<Int, String> = emptyMap()
    ): PdfPageOcrLayout {
        val recognized = reader.process(InputImage.fromBitmap(bitmap, 0)).await()
        val lines = recognized.textBlocks.flatMap { it.lines }
        // Reuse these physical OCR lines for complete question regions.
        // Avoid re-running ML Kit on each resulting JPEG unnecessarily.
        val positionedText = lines.mapNotNull { line ->
            line.boundingBox?.let { box ->
                PdfOcrTextCell(line.text, box.left, box.top, box.right, box.bottom)
            }
        }
        val headerText = lines
            .filter { (it.boundingBox?.top ?: Int.MAX_VALUE) <= bitmap.height * 0.07f }
            .joinToString("\n") { it.text }
        // Publisher layouts vary: the title/test may start at 8%, 20% or
        // much lower on an illustrated page. Read until the first actual
        // numbered QUESTION instead of using a fixed 17%-of-page cutoff.
        val firstQuestionTop = lines.filter {
            com.example.util.PdfOcrQuestionMarker.numberAtStart(it.text) != null
        }.mapNotNull { it.boundingBox?.top }.minOrNull()
        val headerLimit = firstQuestionTop?.let { firstTop ->
            (firstTop - bitmap.height * 0.006f).toInt()
                .coerceAtLeast((bitmap.height * 0.05f).toInt())
        } ?: (bitmap.height * 0.30f).toInt()
        val scopeHeaderText = lines.filter {
            (it.boundingBox?.top ?: Int.MAX_VALUE) < headerLimit
        }.sortedBy { it.boundingBox?.top ?: Int.MAX_VALUE }
            .joinToString("\n") { it.text }

        // Use this PDF's actual OCR geometry. Independent answer-key
        // columns must not share cells just because they have the same Y.
        var answerRegions = PdfAdaptiveAnswerRows.regions(
            cells = positionedText,
            pageWidth = bitmap.width
        )
        // Dense answer-key pages are frequently fully rasterized. A normal
        // full-page OCR pass can skip tiny 1.A / 2.C answer cells. Retry two
        // overlapping half-page views at higher resolution only when multiple
        // test headers or an explicit answer-key heading are visible.
        val sourceTestHeadings = Regex(
            """(?i)\b(?:[ÖO]SYM\s+)?TEST\s*[-.:]?\s*[0-9]{1,3}\b"""
        ).findAll(recognized.text).count()
        if (StudioImageAndPdfEngine.hasAnswerKeyMarker(recognized.text) ||
            sourceTestHeadings >= 3
        ) {
            val detailedCells = recognizeDenseAnswerPage(bitmap, reader)
            val detailedRegions = PdfAdaptiveAnswerRows.regions(
                cells = detailedCells, pageWidth = bitmap.width
            )
            fun recognizedAnswers(regions: List<List<String>>): Int {
                val index = PdfScopedAnswerKeys()
                regions.forEach(index::addOcrRows)
                return index.answerCount
            }
            if (recognizedAnswers(detailedRegions) > recognizedAnswers(answerRegions)) {
                answerRegions = detailedRegions
            }
        }
        val answerRows = answerRegions.flatten()
        var answerGrids = PdfHorizontalAnswerGridReader.find(
            cells = lines.flatMap { line ->
                line.elements.mapNotNull { element ->
                    element.boundingBox?.let {
                        PdfOcrTextCell(element.text, it.left, it.top, it.right, it.bottom)
                    }
                }
            },
            pageWidth = bitmap.width,
            pageHeight = bitmap.height
        )
        // Small footer text loses accuracy in whole-page OCR. Retry only this
        // region at a larger scale, mapping every box back to source coordinates.
        val footerRecoveryNeeded = com.example.util.PdfPrintedFooterRetryPolicy.shouldRetry(
            nativeKeys = nativeFooterKeys,
            primaryImageGrids = answerGrids,
            primaryHasChoiceText = Regex("""[A-E]\s*[).]""").containsMatchIn(recognized.text)
        )
        if (footerRecoveryNeeded) {
            val footerCells = recognizeAnswerFooter(bitmap, reader)
            answerGrids = (answerGrids + PdfHorizontalAnswerGridReader.find(footerCells, bitmap.width, bitmap.height)).distinctBy { it.top / 8 to it.answers }
        }
        val optionRegex = Regex("""^\s*([A-E])\s*[).:]\s*""", RegexOption.IGNORE_CASE)
        val anchors = lines.mapNotNull { line ->
            val number = com.example.util.PdfOcrQuestionMarker
                .numberAtStart(line.text) ?: return@mapNotNull null
            val numberElement = line.elements.firstOrNull {
                com.example.util.PdfOcrQuestionMarker.isStandaloneNumberElement(it.text)
            }
            val bounds = numberElement?.boundingBox
                ?: line.elements.firstOrNull()?.boundingBox
                ?: line.boundingBox
                ?: return@mapNotNull null
            QuestionNumberAnchor(
                number = number,
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom
            )
        }.distinctBy { Triple(it.number, it.left, it.top) }
            .filterNot { anchor ->
                answerGrids.any { grid ->
                    anchor.top >= grid.top - 4 && anchor.bottom <= grid.bottom + 5
                }
            }
        // Horizontal choices often share one OCR line. Inspect elements too,
        // otherwise only A is found and the E end boundary is lost.
        val options = lines.flatMap { line ->
            val candidates = line.elements.mapNotNull { element ->
                val match = optionRegex.find(element.text) ?: return@mapNotNull null
                val bounds = element.boundingBox ?: return@mapNotNull null
                QuestionOptionAnchor(match.groupValues[1].uppercase(), bounds.left,
                    bounds.top, bounds.right, bounds.bottom)
            }
            val lineMatch = optionRegex.find(line.text)
            val bounds = line.boundingBox
            // Keep full wrapped line bounds for vertical choices.
            if (candidates.size <= 1 && lineMatch != null && bounds != null) {
                listOf(QuestionOptionAnchor(lineMatch.groupValues[1].uppercase(),
                    bounds.left, bounds.top, bounds.right, bounds.bottom))
            } else candidates
        }.filterNot { option ->
            answerGrids.any { grid ->
                option.top >= grid.top - 4 && option.bottom <= grid.bottom + 5
            }
        }
        return PdfPageOcrLayout(
            text = recognized.text,
            headerText = headerText,
            scopeHeaderText = scopeHeaderText,
            answerRows = answerRows,
            answerRegions = answerRegions,
            answerGrids = answerGrids,
            questionNumberAnchors = anchors,
            optionAnchors = options,
            positionedText = positionedText
        )
    }

    /**
     * Independent OCR for image-only/scanned pages. Whole-page ML Kit often
     * misses tiny printed numerals because diagrams and all options are
     * competing in the same recognition image. Read the left margins of both
     * candidate reading columns with a new high-resolution OCR pass.
     *
     * Uses normalized page geometry rather than a publisher-specific layout.
     * The consensus detector, not this raw OCR output, decides if a recovered
     * number is trustworthy.
     */
    private suspend fun recognizeSupplementalNumberAnchors(bitmap: Bitmap, reader: TextRecognizer): List<QuestionNumberAnchor> {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 300 || h < 300) return emptyList()
        val startY = (h * .065f).toInt()
        val endY = (h * .935f).toInt().coerceAtMost(h)
        val margins = listOf(.025f to .35f, .435f to .80f)
        val results = mutableListOf<QuestionNumberAnchor>()
        try {
            for ((startXFraction, endXFraction) in margins) {
                val startX = (w * startXFraction).toInt().coerceIn(0, w - 1)
                val endX = (w * endXFraction).toInt().coerceIn(startX + 1, w)
                val crop = Bitmap.createBitmap(
                    bitmap, startX, startY, endX - startX, endY - startY
                )
                val scale = minOf(
                    1.85f, 3400f / crop.height.toFloat(), 1050f / crop.width.toFloat()
                ).coerceAtLeast(1f)
                val enlarged = if (scale > 1.01f) Bitmap.createScaledBitmap(
                    crop, (crop.width * scale).toInt().coerceAtLeast(1),
                    (crop.height * scale).toInt().coerceAtLeast(1), true
                ) else crop
                try {
                    val recognized = reader.process(InputImage.fromBitmap(enlarged, 0)).await()
                    for (line in recognized.textBlocks.flatMap { it.lines }) {
                        val printed = com.example.util.PdfOcrQuestionMarker
                            .numberAtStart(line.text)
                            ?: line.elements.firstOrNull()?.text?.let {
                                if (com.example.util.PdfOcrQuestionMarker
                                        .isStandaloneNumberElement(it)) {
                                    it.trim().takeWhile(Char::isDigit).toIntOrNull()
                                } else null
                            }
                            ?: continue
                        val bounds = line.elements.firstOrNull()?.boundingBox
                            ?: line.boundingBox ?: continue
                        if (bounds.left > enlarged.width * .80f) continue
                        val left = (startX + bounds.left / scale).toInt()
                        val top = (startY + bounds.top / scale).toInt()
                        val right = (startX + bounds.right / scale).toInt()
                        val bottom = (startY + bounds.bottom / scale).toInt()
                        if (top in 0 until h && left in 0 until w && bottom > top) {
                            results += QuestionNumberAnchor(
                                printed, left, top, right.coerceAtMost(w),
                                bottom.coerceAtMost(h)
                            )
                        }
                    }
                } finally {
                    if (enlarged !== crop) enlarged.recycle()
                    crop.recycle()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (oom: OutOfMemoryError) {
            android.util.Log.w("SoruLabPdfOCR", "Margin OCR memory limit; primary pass retained")
        } catch (error: Exception) {
            android.util.Log.w("SoruLabPdfOCR", "Margin OCR unavailable; primary pass retained", error)
        }
        return results
    }

    /** High-resolution, on-device fallback for dense scanned answer tables. */
    private suspend fun recognizeDenseAnswerPage(bitmap: Bitmap, reader: TextRecognizer): List<PdfOcrTextCell> {
        val cells = mutableListOf<PdfOcrTextCell>()
        try {
            // Overlap preserves test headers located exactly on a tile edge.
            for ((from, to) in listOf(0f to .53f, .47f to 1f)) {
                val top = (bitmap.height * from).toInt().coerceIn(0, bitmap.height - 1)
                val end = (bitmap.height * to).toInt().coerceIn(top + 1, bitmap.height)
                val cropped = Bitmap.createBitmap(
                    bitmap, 0, top, bitmap.width, end - top
                )
                val scale = minOf(1.65f, 2950f / cropped.width,
                    2600f / cropped.height).coerceAtLeast(1f)
                val enlarged = if (scale > 1.01f) Bitmap.createScaledBitmap(
                    cropped, (cropped.width * scale).toInt().coerceAtLeast(1),
                    (cropped.height * scale).toInt().coerceAtLeast(1), true
                ) else cropped
                try {
                    val recognized = reader.process(
                        InputImage.fromBitmap(enlarged, 0)
                    ).await()
                    for (line in recognized.textBlocks.flatMap { it.lines }) {
                        val bounds = line.boundingBox ?: continue
                        val left = (bounds.left / scale).toInt()
                        val right = (bounds.right / scale).toInt()
                        val mappedTop = top + (bounds.top / scale).toInt()
                        val mappedBottom = top + (bounds.bottom / scale).toInt()
                        if (line.text.isNotBlank()) cells.add(
                            PdfOcrTextCell(line.text, left, mappedTop, right, mappedBottom)
                        )
                    }
                } finally {
                    if (enlarged !== cropped) enlarged.recycle()
                    cropped.recycle()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (oom: OutOfMemoryError) {
            android.util.Log.w("SoruLabPdfOCR", "Dense answer page exceeds memory budget")
        } catch (error: Exception) {
            android.util.Log.w("SoruLabPdfOCR",
                "Dense answer OCR fallback unavailable", error)
        }
        return cells.distinctBy { cell ->
            Triple(cell.text, cell.left / 9, cell.top / 9)
        }
    }


    /**
     * High-detail, independent ML Kit recovery for a printed number/letter
     * footer that is already present in the PDF text layer or in Paddle OCR.
     *
     * Full-page text recognition can see a 1..N strip as one tiny undeciphered
     * blob. Two overlapping horizontal tiles give ML Kit ~3x the pixels while
     * preserving physical answer-cell coordinates. Read the line and element
     * interpretations separately; merging them creates duplicate numbers.
     *
     * This is OCR of the actual page image, not a copy of the PDF native
     * answer labels. Failed reads remain unverified.
     */
    private suspend fun recognizeAnswerFooterTiledGrids(
        bitmap: Bitmap,
        reader: TextRecognizer,
        expectedNativeKeys: Map<Int, String> = emptyMap()
    ): List<PdfHorizontalAnswerGrid> {
        val recovered = mutableListOf<PdfHorizontalAnswerGrid>()
        val top = (bitmap.height * .65f).toInt()
            .coerceIn(0, bitmap.height - 1)
        try {
            // The source key of a real 272-page textbook spans ~25%..77%
            // of the page width. Neither 0..72% nor 28..100% contains
            // its entire 1..12 sequence, and the grid validator rightly
            // refuses to invent the missing first/last cells. Always read
            // the WHOLE footer at high resolution first; the overlapping
            // tiles remain supplemental evidence for tiny printed letters.
            for ((leftFraction, rightFraction) in
                listOf(0f to 1f, 0f to .72f, .28f to 1f)
            ) {
                val left = (bitmap.width * leftFraction).toInt()
                    .coerceIn(0, bitmap.width - 1)
                val right = (bitmap.width * rightFraction).toInt()
                    .coerceIn(left + 1, bitmap.width)
                val crop = Bitmap.createBitmap(
                    bitmap, left, top, right - left, bitmap.height - top
                )
                val scale = minOf(
                    3.25f, 3100f / crop.width, 2500f / crop.height
                ).coerceAtLeast(1f)
                val enlarged = if (scale > 1.01f) Bitmap.createScaledBitmap(
                    crop,
                    (crop.width * scale).toInt().coerceAtLeast(1),
                    (crop.height * scale).toInt().coerceAtLeast(1),
                    true
                ) else crop
                try {
                    val recognized = reader.process(
                        InputImage.fromBitmap(enlarged, 0)
                    ).await()
                    val lines = recognized.textBlocks.flatMap { it.lines }
                    val elementCells = lines.flatMap { it.elements }
                        .mapNotNull { element ->
                            element.boundingBox?.let { box ->
                                PdfOcrTextCell(
                                    element.text,
                                    left + (box.left / scale).toInt(),
                                    top + (box.top / scale).toInt(),
                                    left + (box.right / scale).toInt(),
                                    top + (box.bottom / scale).toInt()
                                )
                            }
                        }
                    val lineCells = lines.mapNotNull { line ->
                        line.boundingBox?.let { box ->
                            PdfOcrTextCell(
                                line.text,
                                left + (box.left / scale).toInt(),
                                top + (box.top / scale).toInt(),
                                left + (box.right / scale).toInt(),
                                top + (box.bottom / scale).toInt()
                            )
                        }
                    }
                    val elementGrids = PdfHorizontalAnswerGridReader.find(
                        elementCells, bitmap.width, bitmap.height
                    )
                    recovered += if (elementGrids.isNotEmpty()) elementGrids
                    else PdfHorizontalAnswerGridReader.find(
                        lineCells, bitmap.width, bitmap.height
                    )
                    // The first full-width image pass is enough only when
                    // its physical A-E cells EXACTLY match the independent
                    // native PDF text layer. Save two additional OCR passes.
                    // Mismatches preserve the original three-pass recovery.
                    if (leftFraction == 0f && rightFraction == 1f &&
                        expectedNativeKeys.isNotEmpty()
                    ) {
                        val agreed = recovered.firstOrNull { grid ->
                            com.example.util.PdfNativePageFastPath
                                .independentFooterAgrees(expectedNativeKeys, grid.answers)
                        }
                        if (agreed != null) return listOf(agreed)
                    }
                } finally {
                    if (enlarged !== crop) enlarged.recycle()
                    crop.recycle()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (oom: OutOfMemoryError) {
            android.util.Log.w("SoruLabPdfOCR", "Tiled footer OCR exceeds device memory")
        } catch (error: Exception) {
            android.util.Log.w("SoruLabPdfOCR", "Tiled footer OCR unavailable", error)
        }
        return recovered.distinctBy { grid ->
            grid.top / 10 to grid.answers
        }
    }

    private suspend fun recognizeAnswerFooter(bitmap: Bitmap, reader: TextRecognizer): List<PdfOcrTextCell> {
        val top = (bitmap.height * 0.7f).toInt()
        val crop = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, bitmap.height - top)
        val targetWidth = (bitmap.width * 2).coerceAtMost(3600)
        val scale = targetWidth.toFloat() / crop.width
        val enlarged = Bitmap.createScaledBitmap(crop, targetWidth, (crop.height * scale).toInt(), true)
        return try {
            val result = reader.process(InputImage.fromBitmap(enlarged, 0)).await()
            result.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { element ->
                element.boundingBox?.let { box ->
                    PdfOcrTextCell(element.text, (box.left / scale).toInt(), top + (box.top / scale).toInt(),
                        (box.right / scale).toInt(), top + (box.bottom / scale).toInt())
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        } finally {
            if (enlarged !== crop) enlarged.recycle()
            crop.recycle()
        }
    }

    private suspend fun recognizeQuestionText(bitmap: Bitmap, reader: TextRecognizer): String =
        withContext(Dispatchers.IO) {
            reader.process(InputImage.fromBitmap(bitmap, 0)).await().text
        }

    private fun buildQuestionOcrClassification(
        questionText: String,
        examTypeHint: String,
        subjectNameHint: String,
        suggestedTopicName: String,
        trustSuggestedTopic: Boolean,
        hasDiagramOrGraph: Boolean,
        lineCountEstimate: Int,
        visualDifficultyHint: String
    ): QuestionOcrClassification {
        val topicClassification = SoruLabCatalog.classifyQuestionTopicFromText(
            examTypeHint = examTypeHint,
            subjectNameHint = subjectNameHint,
            questionText = questionText,
            suggestedTopicName = suggestedTopicName,
            trustSuggestedTopic = trustSuggestedTopic
        )
        val matchedSubject = topicClassification?.first ?: SoruLabCatalog.allSubjects.firstOrNull {
            it.examType.name.equals(examTypeHint, ignoreCase = true) &&
                it.name.equals(
                    subjectNameHint.replace("TYT ", "", ignoreCase = true)
                        .replace("AYT ", "", ignoreCase = true).trim(),
                    ignoreCase = true
                )
        }

        val textDifficulty = classifyExtractedQuestionDifficulty(
            questionText = questionText,
            hasDiagramOrGraph = hasDiagramOrGraph,
            lineCountEstimate = lineCountEstimate
        )
        val visualDifficulty = DifficultyLevel.fromLabel(visualDifficultyHint)
        val combinedDifficultyWeight = if (questionText.isBlank()) {
            visualDifficulty.weight
        } else {
            textDifficulty.weight
        }
        val difficulty = DifficultyLevel.entries.minByOrNull {
            kotlin.math.abs(it.weight - combinedDifficultyWeight)
        } ?: textDifficulty
        val targetLevel = when (difficulty) {
            com.example.data.DifficultyLevel.KOLAY -> "BASLANGIC"
            com.example.data.DifficultyLevel.ORTA -> "PEKISTIRME"
            com.example.data.DifficultyLevel.OSYM -> "OSYM"
            com.example.data.DifficultyLevel.ZOR -> "GELISIYORUM"
            com.example.data.DifficultyLevel.COK_ZOR -> "DERECE"
        }
        return QuestionOcrClassification(
            questionText = questionText,
            examType = matchedSubject?.examType?.name ?: examTypeHint,
            subjectName = matchedSubject?.name ?: subjectNameHint
                .replace("TYT ", "", ignoreCase = true)
                .replace("AYT ", "", ignoreCase = true)
                .trim(),
            topicName = topicClassification?.second?.name.orEmpty(),
            difficulty = difficulty.label,
            targetLevel = targetLevel
        )
    }

    private fun classifyExtractedQuestionDifficulty(
        questionText: String,
        hasDiagramOrGraph: Boolean,
        lineCountEstimate: Int
    ): DifficultyLevel {
        var score = 0
        if (Regex("""(?i)(?:^|[\s(])(?:I{1,3}|IV|V)[).,:]""").containsMatchIn(questionText)) {
            score += 2
        }
        if (hasDiagramOrGraph) score++
        if (questionText.length >= 280 || lineCountEstimate >= 10 ||
            Regex("""(?i)\b(kesinlikle|her zaman|olabilir)\b""").containsMatchIn(questionText)
        ) score++
        return when {
            score <= 0 -> DifficultyLevel.KOLAY
            score == 1 -> DifficultyLevel.ORTA
            score == 2 -> DifficultyLevel.OSYM
            score == 3 -> DifficultyLevel.ZOR
            else -> DifficultyLevel.COK_ZOR
        }
    }

    private fun describePdfFailure(error: Exception): String {
        return when (error) {
            is java.util.regex.PatternSyntaxException -> "PDF işleme motorunda düzenli ifade hatası oluştu (PatternSyntaxException). Bu dosyanızın hatası değil; uygulamanın güncellenmesi gerekiyor."
            is SecurityException -> "PDF dosyasına erişim izni alınamadı. Dosyayı yeniden seçip tekrar deneyin."
            is java.io.FileNotFoundException -> "Seçilen PDF artık bulunamıyor. Dosyayı yeniden seçin."
            is java.io.IOException -> "PDF okunamadı. Dosyanın bozuk veya parola korumalı olmadığını kontrol edin."
            is TimeoutCancellationException -> "PDF işlemi zaman aşımına uğradı. Daha küçük bir dosyayla veya daha sonra yeniden deneyin."
            else -> "PDF aktarımı tamamlanamadı (${error.javaClass.simpleName}). Dosyayı yeniden seçip tekrar deneyin."
        }
    }

    fun sendFreeMessage(userText: String, bitmap: Bitmap? = null) {
        if (userText.isBlank() && bitmap == null) return
        _messages.value = _messages.value + AiChatMessage(
            id = "local_${System.currentTimeMillis()}", isUser = false,
            text = "Bu ücretsiz sürümde bulut AI çağrısı yapılmaz. PDF kırpma, basılı cevap anahtarı okuma ve soru çözme ekranı cihazında çalışır."
        )
    }

    private fun cleanJsonResponse(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("```")) {
            val withoutFirstLine = trimmed.substringAfter("\n")
            return withoutFirstLine.substringBeforeLast("```").trim()
        }
        return trimmed
    }

    /**
     * Builds a local PDF blueprint, recognizes text on-device, and crops question regions locally.
     */
    fun extractFullPdfFastParallel(
        context: Context,
        pdfUri: Uri,
        fileNameHint: String,
        publisherName: String,
        publisherLogoUri: String,
        customPublishers: List<YksPublisherInfo> = emptyList(),
        onComplete: (List<ExtractedPdfQuestionItem>, String, String) -> Unit
    ) {
        if (_isAutoTagging.value) return
        _isAutoTagging.value = true
        _batchErrorText.value = null
        _batchWarningText.value = null
        _batchProgressPercent.value = 4
        _batchStatusText.value = "PDF cihazında açılıyor..."

        viewModelScope.launch {
            val reusableBitmapRef = arrayOfNulls<Bitmap>(1)
            val reusablePixelsRef = arrayOfNulls<IntArray>(1)
            val processingStatus = PdfLocalProcessingStatus()
            var completionSent = false
            fun completeOnce(items: List<ExtractedPdfQuestionItem>, coverPath: String, title: String) {
                if (completionSent) return
                completionSent = true
                onComplete(items, coverPath, title)
            }

            try {
                val seekablePdfFile = withContext(Dispatchers.IO) {
                    StudioImageAndPdfEngine.prepareSeekablePdfFile(context, pdfUri)
                }
                if (seekablePdfFile == null || !seekablePdfFile.exists() || seekablePdfFile.length() == 0L) {
                    _batchErrorText.value = "Seçilen PDF dosyası açılamadı. Lütfen geçerli bir PDF dosyası seçin."
                    completeOnce(emptyList(), "", "")
                    return@launch
                }

                val totalPdfPages = withContext(Dispatchers.IO) {
                    StudioImageAndPdfEngine.getPdfPageCount(context, pdfUri).coerceAtLeast(1)
                }

                // Render Page 1 as Book Cover for KİTAPLIĞIM
                val coverPath = withContext(Dispatchers.IO) {
                    StudioImageAndPdfEngine.savePdfFirstPageAsBookCover(context, pdfUri)
                }

                val resolvedDisplayName = withContext(Dispatchers.IO) {
                    StudioImageAndPdfEngine.resolvePdfDisplayName(context, pdfUri, fileNameHint)
                }
                // Hashing a 40+ MB textbook on Main freezes Compose input and
                // animations. Keep the stable content-derived book ID, but stream
                // the digest on the IO dispatcher with a bounded buffer.
                val bookId = withContext(Dispatchers.IO) {
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    seekablePdfFile.inputStream().buffered(65536).use { input ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            digest.update(buffer, 0, count)
                        }
                    }
                    "book_" + digest.digest().joinToString("") { "%02x".format(it) }
                }
                val archivedPdfPath = withContext(Dispatchers.IO) {
                    StudioImageAndPdfEngine.persistBookPdf(context, seekablePdfFile, bookId)
                }

                _batchProgressPercent.value = 8
                _batchStatusText.value = "Sayfa 1 / $totalPdfPages işleniyor • Kitap haritası ve cevap anahtarı analiz ediliyor..."

                // STEP 1: Fast local structural text + Regex Answer Key + YKS Curriculum Dictionary blueprint
                val localBlueprint = withContext(Dispatchers.IO) {
                    StudioImageAndPdfEngine.extractLocalPdfBlueprint(
                        context = context,
                        pdfFile = seekablePdfFile,
                        totalPages = totalPdfPages,
                        fileNameHint = resolvedDisplayName,
                        publisherName = publisherName
                    )
                }

                // A global question-number map is unsafe for books where each test
                // restarts from question 1. Use a section + test scoped index instead.
                val pendingAnswerRegions = mutableListOf<List<String>>()
                val mergedPageMap = localBlueprint.pageMap
                val finalBookTitle = localBlueprint.detectedBookTitle
                var defaultExamType = localBlueprint.defaultExamType
                var lockedBookSubject = StudioImageAndPdfEngine.detectSubjectNameFromText(
                    "$resolvedDisplayName $publisherName"
                ) ?: localBlueprint.defaultSubjectName.takeIf(String::isNotBlank)
                val defaultSubject = lockedBookSubject ?: localBlueprint.defaultSubjectName
                val explicitlyTytBook = Regex("""(?i)(?:^|[^A-Z])TYT(?:$|[^A-Z])""")
                    .containsMatchIn(
                        "$resolvedDisplayName $publisherName ${localBlueprint.extractedTextSampleForAi}"
                    )
                if (lockedBookSubject.equals("Biyoloji", ignoreCase = true) && !explicitlyTytBook) {
                    defaultExamType = "AYT"
                }

                // STEP 2: Use the local PDF text layer and curriculum dictionary.
                _batchProgressPercent.value = 14
                _batchStatusText.value =
                    "PDF metin haritası hazırlandı • Ücretsiz cihaz içi OCR ve kırpma başlatılıyor..."

                // STEP 3: On-device ML Kit OCR anchors plus SoruLab's local question-region cropper.
                val resolvedPub = SoruLabPublisherPresets.resolvePublisher(publisherName, customPublishers)
                val finalPubName = publisherName.ifBlank { resolvedPub.name }
                val finalLogoUri = publisherLogoUri.ifBlank { resolvedPub.customLogoPath }

                val allExtractedQuestions = mutableListOf<ExtractedPdfQuestionItem>()
                val expectedScopeByQuestionId = mutableMapOf<String, PdfAnswerScope>()
                val unsafeCropQuestionIds = mutableSetOf<String>()
                var mergedOrMisnumberedCropCount = 0
                var pageCoverageMissingStartCount = 0
                var pageCoverageDuplicateStartCount = 0
                var pageCoveragePagesAudited = 0
                val pendingHorizontalGrids = mutableListOf<Pair<PdfAnswerScope, PdfHorizontalAnswerGrid>>()
                val pendingVectorFooterKeys = mutableListOf<Pair<PdfAnswerScope, Map<Int, String>>>()
                val pendingSecondaryAnswerRegions = mutableListOf<List<String>>()
                val pendingSecondaryGrids = mutableListOf<Pair<PdfAnswerScope, com.example.util.PdfHorizontalAnswerGrid>>()
                var offlineRejectedQuestionPages = 0
                var detectedAnswerPages = 0
                var pageRenderFailureCount = 0
                val suspectedSkippedQuestionPages = linkedSetOf<Int>()
                var recoveredPrintedQuestionPages = 0

                val pageIndicesToScan = 0 until totalPdfPages

                withContext(Dispatchers.IO) {
                    val pageOcrReader = TextRecognition.getClient(
                        TextRecognizerOptions.DEFAULT_OPTIONS
                    )
                    // At most two simultaneous crop OCR calls. Keep each
                    // reader hot for the whole import; never process two
                    // bitmap crops concurrently on one native recognizer.
                    val cropOcrReaders = Array(2) {
                        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    }
                    val cropOcrPool = Channel<TextRecognizer>(cropOcrReaders.size)
                    cropOcrReaders.forEach { cropOcrPool.trySend(it) }
                    try {
                    android.os.ParcelFileDescriptor.open(
                        seekablePdfFile,
                        android.os.ParcelFileDescriptor.MODE_READ_ONLY
                    ).use { pfd ->
                        android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                            val rendererTotal = renderer.pageCount.coerceAtLeast(1)
                            var lastProgressUpdateMs = 0L
                            val lastTopicBySubject = mutableMapOf<String, String>()

                            // Discover answer keys anywhere in this specific PDF
                            // during the normal single OCR pass. No publisher-specific
                            // page offsets, no assumption about front/end placement.
                            // Answers are associated AFTER scanning all pages because
                            // the answer key may follow the question it belongs to.
                            var pageQuestionScope: PdfAnswerScope? = null

                            for ((scanPos, pageIdx) in pageIndicesToScan.withIndex()) {
                                if (pageIdx >= rendererTotal) continue
                                val pageStartMs = android.os.SystemClock.elapsedRealtime()
                                val pageInfo = mergedPageMap[pageIdx] ?: StudioImageAndPdfEngine.LocalPageMapEntry(
                                    pageIndex = pageIdx,
                                    examType = defaultExamType,
                                    subjectName = defaultSubject,
                                    topicName = "",
                                    difficulty = "Orta"
                                )
                                // Blueprint text-layer flags are unreliable for scanned PDFs.
                                // OCR must inspect this page before deciding whether to skip it.

                                val nowMs = System.currentTimeMillis()
                                if (nowMs - lastProgressUpdateMs >= 110L || scanPos == 0 || scanPos == totalPdfPages - 1) {
                                    lastProgressUpdateMs = nowMs
                                    val progressPct = 16 + (((scanPos + 1).toFloat() / totalPdfPages.coerceAtLeast(1).toFloat()) * 82f).toInt()
                                    _batchProgressPercent.value = progressPct.coerceIn(16, 98)
                                    val analysisLabel = "Yerel ML Kit OCR • SoruLab kırpma"
                                    _batchStatusText.value = "Sayfa ${pageIdx + 1} / $totalPdfPages işleniyor • ${allExtractedQuestions.size} soru • $analysisLabel"
                                }

                                val pageBitmap = try {
                                    StudioImageAndPdfEngine.renderPageForLocalAnalysis(
                                        renderer = renderer,
                                        pageIndex = pageIdx,
                                        totalPages = rendererTotal,
                                        reusableBitmapRef = reusableBitmapRef
                                    )
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: OutOfMemoryError) {
                                    reusableBitmapRef[0]?.recycle()
                                    reusableBitmapRef[0] = null
                                    reusablePixelsRef[0] = null
                                    pageRenderFailureCount++
                                    null
                                } catch (_: Exception) {
                                    pageRenderFailureCount++
                                    null
                                }
                                if (pageBitmap == null) continue
                                val renderFinishedAtMs = android.os.SystemClock.elapsedRealtime()

                                val source = localBlueprint.pageVectorQuestionAnchors[pageIdx]
                                val nativeAnchors = source?.takeIf {
                                    it.pageWidth > 0f && it.pageHeight > 0f
                                }?.anchors?.map { anchor ->
                                    QuestionNumberAnchor(
                                        number = anchor.number,
                                        left = (anchor.x / source.pageWidth * pageBitmap.width).toInt()
                                            .coerceIn(0, pageBitmap.width - 1),
                                        top = (anchor.y / source.pageHeight * pageBitmap.height).toInt()
                                            .coerceIn(0, pageBitmap.height - 1),
                                        right = ((anchor.x + anchor.width) / source.pageWidth *
                                            pageBitmap.width).toInt().coerceIn(1, pageBitmap.width),
                                        bottom = ((anchor.y + anchor.height) / source.pageHeight *
                                            pageBitmap.height).toInt().coerceIn(1, pageBitmap.height)
                                    )
                                }.orEmpty()

                                // Native text is a candidate, not an OCR verdict.
                                // For fully selectable books, only an independent
                                // rendered FOOTER vote plus physically matching
                                // image-read question starts may bypass full-page
                                // ML Kit OCR. Any disagreement uses the old path.
                                val nativeFooterForFastPath =
                                    localBlueprint.pageTextFooterKeys[pageIdx].orEmpty()
                                val nativeHeaderForFastPath =
                                    localBlueprint.pageSourceHeaders[pageIdx].orEmpty()
                                val sourceTextFragmentsForFastPath =
                                    localBlueprint.pageVectorTextFragments[pageIdx].orEmpty()
                                val canTryNativeFastPath = com.example.util.PdfNativePageFastPath
                                    .canTry(
                                        printedQuestionStarts = nativeAnchors,
                                        nativeFooterAnswers = nativeFooterForFastPath,
                                        hasNativeChoices = pageIdx in localBlueprint.pageVectorOptionEvidence,
                                        hasPrintedTestIdentity =
                                            pageIdx in localBlueprint.pageVectorTestMarkers,
                                        hasPageText = sourceTextFragmentsForFastPath.isNotEmpty(),
                                        pageWidth = pageBitmap.width,
                                        pageHeight = pageBitmap.height
                                    )
                                val fastImageFooter = if (canTryNativeFastPath &&
                                    nativeFooterForFastPath.isNotEmpty()
                                ) {
                                    recognizeAnswerFooterTiledGrids(
                                        pageBitmap, pageOcrReader, nativeFooterForFastPath
                                    ).firstOrNull { grid ->
                                        com.example.util.PdfNativePageFastPath.independentFooterAgrees(
                                            nativeFooterForFastPath, grid.answers
                                        )
                                    }
                                } else null
                                val independentFooterReady = nativeFooterForFastPath.isEmpty() ||
                                    fastImageFooter != null
                                val fastImageMargin = if (canTryNativeFastPath &&
                                    independentFooterReady
                                ) {
                                    recognizeSupplementalNumberAnchors(pageBitmap, pageOcrReader)
                                } else emptyList()
                                val fastGeometryConfirmed = canTryNativeFastPath &&
                                    independentFooterReady &&
                                    com.example.util.PdfNativeQuestionEvidence
                                        .hasIndependentSpatialSupport(
                                            nativeAnchors, fastImageMargin,
                                            pageBitmap.width, pageBitmap.height
                                        )
                                val ocrPageLayout = if (fastGeometryConfirmed) {
                                    android.util.Log.i(
                                        "SoruLabPdfPerf",
                                        "page=${pageIdx + 1} verifiedNativeFastPath=true"
                                    )
                                    PdfPageOcrLayout(
                                        text = sourceTextFragmentsForFastPath.joinToString("\n") {
                                            it.text
                                        }.take(12_000),
                                        headerText = nativeHeaderForFastPath,
                                        scopeHeaderText = nativeHeaderForFastPath,
                                        answerGrids = listOfNotNull(fastImageFooter),
                                        questionNumberAnchors = nativeAnchors
                                    )
                                } else try {
                                    recognizePdfPageLayout(
                                        pageBitmap, pageOcrReader,
                                        nativeFooterForFastPath
                                    )
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: OutOfMemoryError) {
                                    reusablePixelsRef[0] = null
                                    processingStatus.ocrFailureCount++
                                    PdfPageOcrLayout()
                                } catch (_: Exception) {
                                    processingStatus.ocrFailureCount++
                                    PdfPageOcrLayout()
                                }
                                val primaryOcrFinishedAtMs = android.os.SystemClock.elapsedRealtime()
                                // OCR can misread graph labels as extra question
                                // numbers. Trust a coherent two-column vector
                                // sequence even when OCR reports more candidates.
                                val vectorHasPrintedColumns =
                                    com.example.util.PdfColumnLayoutEvidence.hasTwoPrintedColumns(
                                        nativeAnchors, pageBitmap.width, 0, pageBitmap.height
                                    )
                                val sourceBackedPage =
                                    com.example.util.PdfNativeQuestionEvidence.trustNativeQuestionStarts(
                                        nativeAnchorCount = nativeAnchors.size,
                                        wholePageOcrAnchorCount =
                                            ocrPageLayout.questionNumberAnchors.size,
                                        hasTwoPrintedColumns = vectorHasPrintedColumns,
                                        hasNativeChoiceEvidence =
                                            pageIdx in localBlueprint.pageVectorOptionEvidence
                                    )
                                // For a scanned book, native PDF text is
                                // usually just a watermark. Validate OCR
                                // numerals a second time at higher resolution,
                                // using the two printed column margins.
                                // A selectable-text PDF is independent native-key
                                // evidence, but the whole-page ML Kit reader may miss
                                // its tiny printed footer. A second *image* reader
                                // (Paddle PP-OCRv6) can be compared with the native
                                // keys without treating the PDF labels as their
                                // own confirmation.
                                val nativeFooterEvidence =
                                    (localBlueprint.pageTextFooterKeys[pageIdx]?.size ?: 0) >= 4
                                // Native *key* evidence is independent of
                                // whether native *question starts* are strong
                                // enough to drive cropping. Trust the printed
                                // key as a second reading even on mixed pages.
                                val needsNativeImageKeyVerification =
                                    nativeFooterEvidence &&
                                        ocrPageLayout.answerGrids.isEmpty()
                                // On selectable-text pages, primary ML Kit can
                                // independently corroborate the native PDF's
                                // actual question positions. If so, expensive
                                // full-page Paddle plus two margin OCR passes
                                // add no extra certainty. Never skip a needed
                                // independent read of the printed answer grid.
                                val nativePrimarySpatiallyConfirmed = !sourceBackedPage &&
                                    com.example.util.PdfNativeQuestionEvidence
                                        .hasIndependentSpatialSupport(
                                            nativeAnchors, ocrPageLayout.questionNumberAnchors,
                                            pageBitmap.width, pageBitmap.height
                                        )
                                val skipDetailedPageReaders =
                                    com.example.util.PdfNativeQuestionEvidence
                                        .canSkipDetailedPageReaders(
                                            sourceBackedPage,
                                            nativeAnchors, ocrPageLayout.questionNumberAnchors,
                                            pageBitmap.width, pageBitmap.height,
                                            needsNativeImageKeyVerification
                                        )
                                val paddleEvidence = if (!skipDetailedPageReaders &&
                                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                                ) {
                                    try { PdfPaddleMobileOcr.readPage(context, pageBitmap) }
                                    catch (e: CancellationException) { throw e }
                                    catch (e: LinkageError) {
                                        // A native OpenCV/ONNX load failure must never
                                        // crash book processing. ML Kit and PDF text
                                        // extraction remain available independently.
                                        android.util.Log.e(
                                            "SoruLabPaddle",
                                            "Paddle native library could not load; using other source OCR",
                                            e
                                        )
                                        null
                                    }
                                    catch (e: Exception) {
                                        android.util.Log.w("SoruLabPaddle", "Independent on-device OCR unavailable", e)
                                        null
                                    }
                                } else null
                                val paddleFinishedAtMs = android.os.SystemClock.elapsedRealtime()
                                val paddleGrids = paddleEvidence?.let {
                                    PdfHorizontalAnswerGridReader.find(it.textCells, pageBitmap.width, pageBitmap.height)
                                }.orEmpty()
                                val paddleRegions = paddleEvidence?.let {
                                    PdfAdaptiveAnswerRows.regions(it.textCells, pageBitmap.width)
                                }.orEmpty()
                                if (PdfScopedAnswerKeys.looksLikeAnswerPage(paddleRegions.flatten())) {
                                    if (nativeFooterEvidence) {
                                        // Paddle is image OCR; native PDF is the
                                        // independent source, not the same reader.
                                        pendingAnswerRegions.addAll(paddleRegions)
                                    } else {
                                        pendingSecondaryAnswerRegions.addAll(paddleRegions)
                                    }
                                }
                                val imageOnlyAnchors = if (!sourceBackedPage &&
                                    !skipDetailedPageReaders) {
                                    // Avoid two extra enlarged OCR reads only when independent ML Kit
                                    // and Paddle agree on every printed 1..N start at the same
                                    // physical location. Ambiguous scans retain full recovery.
                                    val corroboratedPrintedNumbers = (paddleGrids + ocrPageLayout.answerGrids)
                                        .firstOrNull { grid ->
                                            grid.answers.keys ==
                                                (1..ocrPageLayout.questionNumberAnchors.size).toSet()
                                        }?.answers?.keys.orEmpty()
                                    val marginAlreadyConfirmed = com.example.util.PdfMarginRecoveryPolicy
                                        .canSkipMarginPass(
                                            primary = ocrPageLayout.questionNumberAnchors,
                                            paddle = paddleEvidence?.questionNumberAnchors.orEmpty(),
                                            pageWidth = pageBitmap.width,
                                            pageHeight = pageBitmap.height,
                                            printedAnswerNumbers = corroboratedPrintedNumbers
                                        )
                                    val secondPass = if (marginAlreadyConfirmed) emptyList()
                                        else recognizeSupplementalNumberAnchors(pageBitmap, pageOcrReader)
                                    // A false primary graph number must not block
                                    // genuinely independent ML Kit margin + Paddle
                                    // evidence of the complete printed sequence.
                                    // A printed start remains source-backed and no
                                    // reader may invent a question number.
                                    val paddleNumbers = paddleEvidence?.questionNumberAnchors.orEmpty()
                                    com.example.util.PdfPrintedAnchorConsensus.reconcileThree(
                                        primary = ocrPageLayout.questionNumberAnchors,
                                        marginPass = secondPass,
                                        paddlePass = paddleNumbers,
                                        pageWidth = pageBitmap.width,
                                        pageHeight = pageBitmap.height
                                    )
                                } else emptyList()
                                // Single-column textbook pages can have a
                                // clean native printed run plus stray OCR
                                // diagram numbers. Retain the PDF geometry
                                // only where independent OCR spatially
                                // corroborates a majority of actual starts.
                                val nativeSpatiallyConfirmed = nativePrimarySpatiallyConfirmed ||
                                    (!sourceBackedPage &&
                                        com.example.util.PdfNativeQuestionEvidence
                                            .hasIndependentSpatialSupport(
                                                nativeAnchors, imageOnlyAnchors,
                                                pageBitmap.width, pageBitmap.height
                                            ))
                                val useNativeQuestionStarts =
                                    sourceBackedPage || nativeSpatiallyConfirmed
                                var pageLayout = if (useNativeQuestionStarts) {
                                    ocrPageLayout.copy(questionNumberAnchors = nativeAnchors)
                                } else ocrPageLayout.copy(questionNumberAnchors = imageOnlyAnchors)
                                // A second OCR reader is useful only when its actual
                                // spatial A-E coordinates reach the cropper. Previously
                                // we accepted Paddle-only choices as proof of a question
                                // but passed the missing ML Kit choices to the cropper.
                                // The native text layer or independent Paddle
                                // reader can establish that a real footer exists,
                                // but it cannot replace ML Kit's independent vote.
                                // Retry ONLY when ML Kit missed printed cells.
                                val independentFooterCount = maxOf(
                                    localBlueprint.pageTextFooterKeys[pageIdx]?.size ?: 0,
                                    paddleGrids.maxOfOrNull { it.answers.size } ?: 0
                                )
                                val primaryFooterCount =
                                    pageLayout.answerGrids.maxOfOrNull { it.answers.size } ?: 0
                                if (independentFooterCount >= 4 &&
                                    primaryFooterCount < independentFooterCount
                                ) {
                                    val highResolutionFooter = recognizeAnswerFooterTiledGrids(
                                        pageBitmap, pageOcrReader
                                    )
                                    if (highResolutionFooter.isNotEmpty()) {
                                        pageLayout = pageLayout.copy(
                                            answerGrids = (
                                                pageLayout.answerGrids + highResolutionFooter
                                            ).distinctBy { it.top / 10 to it.answers }
                                        )
                                    }
                                }
                                if (independentFooterCount >= 4) {
                                    android.util.Log.i("SoruLabPdfKeyQA",
                                        "page=${pageIdx + 1} nativeFooter=${localBlueprint.pageTextFooterKeys[pageIdx]?.size ?: 0}" +
                                            " mlkitGrid=${ocrPageLayout.answerGrids.maxOfOrNull { it.answers.size } ?: 0}" +
                                            " tiledGrid=${pageLayout.answerGrids.maxOfOrNull { it.answers.size } ?: 0}" +
                                            " paddleGrid=${paddleGrids.maxOfOrNull { it.answers.size } ?: 0}" +
                                            " nativeQuestionAnchors=${nativeAnchors.size}" +
                                            " nativeTrusted=$sourceBackedPage"
                                    )
                                }
                                val primaryChoiceCount = pageLayout.optionAnchors
                                    .map { it.option.uppercase() }.distinct().size
                                val paddleChoices = paddleEvidence?.optionAnchors.orEmpty()
                                val paddleChoiceCount = paddleChoices
                                    .map { it.option.uppercase() }.distinct().size
                                val cropOptionAnchors = if (!useNativeQuestionStarts &&
                                    paddleChoiceCount >= 3 &&
                                    paddleChoiceCount > primaryChoiceCount
                                ) paddleChoices else pageLayout.optionAnchors
                                val hasQuestionOptions = useNativeQuestionStarts ||
                                    cropOptionAnchors.map { it.option.uppercase() }.distinct().size >= 3
                                val hasAnswerKeyMarker =
                                    StudioImageAndPdfEngine.hasAnswerKeyMarker(pageLayout.text)
                                val isAnswerTablePage =
                                    PdfScopedAnswerKeys.looksLikeAnswerPage(pageLayout.answerRows)
                                // Horizontal number/letter answer grids may sit
                                // directly underneath the last questions of a test.
                                val gridHeaderScope = PdfScopedAnswerKeys.scopeFromQuestionHeader(
                                    pageLayout.scopeHeaderText, pageInfo.topicName
                                )
                                // The PDF's embedded text layer can preserve tiny
                                // table titles which whole-page device OCR misses.
                                // Resolve only from THIS page's printed heading.
                                val printedTestMarker =
                                    localBlueprint.pageVectorTestMarkers[pageIdx]
                                val pageSourceHeader = localBlueprint.pageSourceHeaders[pageIdx]
                                // PDF text order is not page geometry. For example:
                                // an embedded answer table may emit 1..12 *before*
                                // the actual printed "ANALİZ 12" test identity.
                                // Prefix the source header with the independently
                                // verified spatial identity so the textual section
                                // still comes from this page, never from a guessed
                                // global numbering map or the previous test.
                                val spatialHeader = if (printedTestMarker != null) {
                                    val kind = printedTestMarker.testKind.uppercase()
                                    val prefix = if (printedTestMarker.isOsymTest)
                                        "ÖSYM " else ""
                                    "$prefix$kind ${printedTestMarker.testNumber}\n" +
                                        pageSourceHeader.orEmpty()
                                } else pageSourceHeader
                                val vectorHeaderScope = spatialHeader?.let {
                                    PdfScopedAnswerKeys.scopeFromQuestionHeader(
                                        it, pageInfo.topicName
                                    )
                                }?.let { scoped ->
                                    if (printedTestMarker != null) scoped.copy(
                                        testNumber = printedTestMarker.testNumber,
                                        testKind = printedTestMarker.testKind,
                                        isOsymTest = printedTestMarker.isOsymTest
                                    ) else scoped
                                }
                                val footerFromTextLayer = localBlueprint.pageTextFooterKeys[pageIdx]
                                if (footerFromTextLayer != null && vectorHeaderScope != null) {
                                    pendingVectorFooterKeys += vectorHeaderScope to footerFromTextLayer
                                }
                                if (pageLayout.answerGrids.isNotEmpty() && !hasAnswerKeyMarker && !isAnswerTablePage) {
                                    detectedAnswerPages++
                                }
                                val skipPage = StudioImageAndPdfEngine.shouldSkipQuestionPage(
                                    text = pageLayout.text,
                                    topHeaderText = pageLayout.headerText,
                                    pageIndex = pageIdx,
                                    totalPages = totalPdfPages
                                )
                                if (hasAnswerKeyMarker || isAnswerTablePage) {
                                    pendingAnswerRegions.addAll(pageLayout.answerRegions)
                                    detectedAnswerPages++
                                    // Mixed pages exist: an answer strip may be
                                    // printed above or below actual questions.
                                    // Skip only when there is NO valid A-E
                                    // multiple-choice question evidence.
                                    val hasQuestionEvidence = hasQuestionOptions &&
                                        pageLayout.questionNumberAnchors.isNotEmpty()
                                    if (!hasQuestionEvidence) {
                                        // A separate key page needs its own explicit heading.
                                        if (gridHeaderScope != null) {
                                            pageLayout.answerGrids.forEach {
                                                pendingHorizontalGrids += gridHeaderScope to it
                                            }
                                            // On a dedicated key page we used to
                                            // skip Paddle's spatial table entirely,
                                            // so a legitimate printed key could
                                            // never achieve independent consensus.
                                            paddleGrids.forEach {
                                                pendingSecondaryGrids += gridHeaderScope to it
                                            }
                                        }
                                        continue
                                    }
                                }
                                if (skipPage && !useNativeQuestionStarts && !hasQuestionOptions) {
                                    // A skip heuristic must never silently hide
                                    // a page with credible printed question and
                                    // multiple-choice evidence.
                                    if (com.example.util.PdfQuestionPagePresence.needsCropReview(
                                            pageLayout.questionNumberAnchors,
                                            cropOptionAnchors,
                                            useNativeQuestionStarts
                                        )
                                    ) suspectedSkippedQuestionPages.add(pageIdx)
                                    continue
                                }
                                // Scanned diagrams sometimes hide the small
                                // printed number or the A-E row from whole-page
                                // OCR. Do not invent missing anchors: mark this
                                // page for review instead of reporting 100%.
                                if (pageLayout.questionNumberAnchors.isEmpty() || !hasQuestionOptions) {
                                    if (com.example.util.PdfQuestionPagePresence.needsCropReview(
                                            pageLayout.questionNumberAnchors,
                                            cropOptionAnchors,
                                            useNativeQuestionStarts
                                        )
                                    ) suspectedSkippedQuestionPages.add(pageIdx)
                                    continue
                                }

                                if (lockedBookSubject == null) {
                                    lockedBookSubject = StudioImageAndPdfEngine.detectSubjectNameFromText(
                                        pageLayout.headerText
                                    )
                                    if (lockedBookSubject.equals("Biyoloji", ignoreCase = true) &&
                                        !explicitlyTytBook &&
                                        !Regex("""(?i)(?:^|[^A-Z])TYT(?:$|[^A-Z])""")
                                            .containsMatchIn(pageLayout.headerText)
                                    ) {
                                        defaultExamType = "AYT"
                                    }
                                }
                                val examTypeForPage = if (
                                    lockedBookSubject.equals("Biyoloji", ignoreCase = true) &&
                                    !explicitlyTytBook &&
                                    !Regex("""(?i)(?:^|[^A-Z])TYT(?:$|[^A-Z])""")
                                        .containsMatchIn("$resolvedDisplayName $publisherName ${pageLayout.headerText}")
                                ) {
                                    "AYT"
                                } else {
                                    defaultExamType
                                }
                                val subjectForPage = lockedBookSubject ?: pageInfo.subjectName
                                val subjectKey = "$examTypeForPage|$subjectForPage"
                                // A title printed in this PDF (native text first,
                                // OCR header second) must outweigh generic
                                // neuron/skin keywords found inside a question.
                                // Using stem keywords as the page topic mixed
                                // Duyu Organları questions into Sinir Sistemi.
                                val printedHeaderTopic =
                                    StudioImageAndPdfEngine.detectPrintedTopicNameFromHeader(
                                        examType = examTypeForPage,
                                        subjectName = subjectForPage,
                                        header = pageSourceHeader.orEmpty()
                                    ) ?: StudioImageAndPdfEngine.detectPrintedTopicNameFromHeader(
                                        examType = examTypeForPage,
                                        subjectName = subjectForPage,
                                        header = pageLayout.scopeHeaderText
                                    ) ?: (vectorHeaderScope ?: gridHeaderScope)?.section
                                        ?.takeIf { section ->
                                            // The chapter was physically printed on
                                            // THIS page and parsed with the actual
                                            // test number. Do not invent a topic
                                            // from unrelated stem keywords or a
                                            // PDF filename.
                                            section.isNotBlank() &&
                                                !section.startsWith("pdf-page-") &&
                                                section.length >= 4
                                        }?.split(Regex("""\s+"""))
                                        ?.joinToString(" ") { word ->
                                            word.replaceFirstChar { it.titlecase() }
                                        }
                                val explicitHeaderTopic = printedHeaderTopic
                                    ?: StudioImageAndPdfEngine.detectTopicNameFromText(
                                        examType = examTypeForPage,
                                        subjectName = subjectForPage,
                                        text = pageLayout.scopeHeaderText
                                    )
                                val detectedTopic = explicitHeaderTopic ?: StudioImageAndPdfEngine.detectTopicNameFromText(
                                    examType = examTypeForPage,
                                    subjectName = subjectForPage,
                                    text = pageLayout.headerText
                                ) ?: StudioImageAndPdfEngine.detectTopicNameFromText(
                                    examType = examTypeForPage,
                                    subjectName = subjectForPage,
                                    text = pageLayout.text
                                )
                                val classifiedPageTopic = SoruLabCatalog.classifyQuestionTopicFromText(
                                    examTypeHint = examTypeForPage,
                                    subjectNameHint = subjectForPage,
                                    questionText = pageLayout.text,
                                    suggestedTopicName = pageInfo.topicName,
                                    trustSuggestedTopic = pageInfo.topicName.isNotBlank()
                                )?.second?.name
                                val resolvedPageTopic = detectedTopic
                                    ?: classifiedPageTopic
                                    ?: lastTopicBySubject[subjectKey]
                                    ?: ""
                                if (resolvedPageTopic.isNotBlank()) {
                                    lastTopicBySubject[subjectKey] = resolvedPageTopic
                                }
                                val scanPageInfo = pageInfo.copy(
                                    examType = examTypeForPage,
                                    subjectName = subjectForPage,
                                    topicName = resolvedPageTopic,
                                    isSkipPage = false
                                )
                                val detectedScope = vectorHeaderScope ?: gridHeaderScope
                                    ?: PdfScopedAnswerKeys.scopeFromQuestionHeader(
                                        pageLayout.scopeHeaderText, scanPageInfo.topicName
                                    )
                                // Some books print the test name only on the first page.
                                // Carry that scope onto continuation pages, but not into
                                // a new "question 1" where a new test may have started.
                                val beginsNewTest = pageLayout.questionNumberAnchors.any { it.number == 1 }
                                val pageAnswerScope = detectedScope
                                    ?: pageQuestionScope.takeUnless { beginsNewTest }
                                    ?: pageLayout.answerGrids.takeIf { grids ->
                                        grids.isNotEmpty() && grids.all { grid ->
                                            grid.top > (pageLayout.questionNumberAnchors.maxOfOrNull { it.bottom } ?: Int.MAX_VALUE)
                                        } && pageLayout.questionNumberAnchors.all { anchor ->
                                            grids.any { anchor.number in it.answers }
                                        }
                                    }?.let {
                                        // A footer printed on this question page is direct evidence
                                        // even when OCR missed its test title. Keep identity page-local.
                                        PdfAnswerScope("pdf-page-${pageIdx + 1}", pageIdx + 1, false, "local")
                                    }
                                pageQuestionScope = pageAnswerScope?.takeUnless { it.testKind == "local" }
                                // Resolve the CURRENT page before binding its key. Previously
                                // this happened above topic detection, using the previous test.
                                if (pageAnswerScope != null) pageLayout.answerGrids.forEach { grid ->
                                    pendingHorizontalGrids += pageAnswerScope to grid
                                }
                                if (pageAnswerScope != null) paddleGrids.forEach { grid ->
                                    // When the independent reference is already
                                    // native PDF text, Paddle belongs to the
                                    // image-reader channel, NOT the native one.
                                    // For image-only pages Paddle remains the
                                    // independent second channel behind ML Kit.
                                    if (nativeFooterEvidence) {
                                        pendingHorizontalGrids += pageAnswerScope to grid
                                    } else {
                                        pendingSecondaryGrids += pageAnswerScope to grid
                                    }
                                }

                                // The last printed question must end ABOVE
                                // the answer strip. ML Kit may miss its tiny
                                // cells even when the independent Paddle image
                                // reader finds them. Only use Paddle geometry
                                // when EVERY grid cell exactly agrees with this
                                // page's native printed key; never crop at an
                                // unverified graph or options table.
                                val lastPrintedQuestionTop =
                                    pageLayout.questionNumberAnchors.maxOfOrNull { it.top }
                                        ?: Int.MAX_VALUE
                                val nativeCorroboratedFooterTop =
                                    footerFromTextLayer?.let { native ->
                                        paddleGrids.filter { grid ->
                                            grid.answers.size >= 4 &&
                                                grid.answers.size == native.size &&
                                                grid.answers.all { (n, option) ->
                                                    native[n] == option
                                                } &&
                                                grid.top > lastPrintedQuestionTop
                                        }.minOfOrNull { it.top }
                                    }
                                val mlKitFooterTop = pageLayout.answerGrids
                                    .filter { it.top > lastPrintedQuestionTop }
                                    .minOfOrNull { it.top }
                                val printedFooterCropLimit = listOfNotNull(
                                    nativeCorroboratedFooterTop, mlKitFooterTop
                                ).minOrNull()
                                val cropStartedAtMs = android.os.SystemClock.elapsedRealtime()
                                var croppedBoxes = try {
                                    StudioImageAndPdfEngine.cropRenderedPageLocalDualColumn(
                                        context = context,
                                        pageBitmap = pageBitmap,
                                        pageIndex = pageIdx,
                                        totalPages = rendererTotal,
                                        reusablePixelsRef = reusablePixelsRef,
                                        questionNumberAnchors = pageLayout.questionNumberAnchors,
                                        optionAnchors = cropOptionAnchors,
                                        maxQuestionBottomY = printedFooterCropLimit,
                                        embedImageBase64 = false // persisted JPEG is the single source of image bytes
                                    )
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: OutOfMemoryError) {
                                    reusablePixelsRef[0] = null
                                    pageRenderFailureCount++
                                    emptyList()
                                } catch (_: Exception) {
                                    pageRenderFailureCount++
                                    emptyList()
                                }

                                // Independent safety pass: if the native PDF text
                                // proves that printed questions exist but OCR-based
                                // cropping missed them, retry using the validated
                                // page-local printed numbers. Accept the second pass
                                // only if it preserves all source-confirmed questions
                                // and adds at least one missing number; never guess.
                                val sourcePrintedNumbers =
                                    nativeAnchors.map { it.number }.toSet()
                                val firstPassPrintedNumbers = croppedBoxes.filter {
                                    it.hasPrintedQuestionNumber
                                }.map { it.questionOrderInPage }
                                val sourceHasOptionEvidence =
                                    pageIdx in localBlueprint.pageVectorOptionEvidence
                                if (com.example.util.PdfCropRecoveryPolicy.shouldRetry(
                                        sourcePrintedNumbers, firstPassPrintedNumbers.toSet(),
                                        sourceHasOptionEvidence
                                    )
                                ) {
                                    val reCropped = try {
                                        StudioImageAndPdfEngine.cropRenderedPageLocalDualColumn(
                                            context = context,
                                            pageBitmap = pageBitmap,
                                            pageIndex = pageIdx,
                                            totalPages = rendererTotal,
                                            reusablePixelsRef = reusablePixelsRef,
                                            questionNumberAnchors = nativeAnchors,
                                            optionAnchors = cropOptionAnchors,
                                            maxQuestionBottomY = printedFooterCropLimit,
                                        embedImageBase64 = false // persisted JPEG is the single source of image bytes
                                        )
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: OutOfMemoryError) {
                                        reusablePixelsRef[0] = null
                                        emptyList()
                                    } catch (e: Exception) {
                                        android.util.Log.w(
                                            "SoruLabPdfImport",
                                            "Native number recovery unavailable at page ${pageIdx + 1}", e
                                        )
                                        emptyList()
                                    }
                                    val recoveredNumbers = reCropped.filter {
                                        it.hasPrintedQuestionNumber
                                    }.map { it.questionOrderInPage }
                                    val useRecovered =
                                        com.example.util.PdfCropRecoveryPolicy.preferRecovery(
                                            sourcePrintedNumbers, firstPassPrintedNumbers, recoveredNumbers
                                        )
                                    val unselected = if (useRecovered) croppedBoxes else reCropped
                                    // Delete only the temporary images from the
                                    // unselected crop pass; none were added to
                                    // the student's question library yet.
                                    unselected.forEach { box ->
                                        if (box.croppedImagePath.isNotBlank()) {
                                            runCatching { java.io.File(box.croppedImagePath).delete() }
                                        }
                                    }
                                    if (useRecovered) {
                                        croppedBoxes = reCropped
                                        recoveredPrintedQuestionPages++
                                    }
                                }

                                val cropFinishedAtMs = android.os.SystemClock.elapsedRealtime()
                                // A successful image save is NOT proof that
                                // exactly one source question was cropped.
                                // Validate rectangles against native printed
                                // question coordinates when available.
                                if (croppedBoxes.isEmpty()) {
                                    offlineRejectedQuestionPages++
                                    suspectedSkippedQuestionPages.add(pageIdx)
                                }
                                val auditSourceNumbers = when {
                                    useNativeQuestionStarts -> nativeAnchors
                                    pageLayout.questionNumberAnchors.size >= 3 &&
                                        com.example.util.PdfColumnLayoutEvidence
                                            .hasTwoPrintedColumns(
                                                pageLayout.questionNumberAnchors,
                                                pageBitmap.width, 0, pageBitmap.height
                                            ) -> pageLayout.questionNumberAnchors
                                    else -> emptyList()
                                }
                                // Whole-page completeness is distinct from the
                                // safety of each crop: two individually plausible
                                // crops can overlap a single question while
                                // another printed question disappears.
                                val cropEvidence = croppedBoxes.map { box ->
                                    com.example.util.PdfQuestionCropEvidence(
                                        box.questionOrderInPage, box.boxNormalized
                                    )
                                }
                                val pageCoverage = com.example.util.PdfPageCropCoverageAudit.assess(
                                    auditSourceNumbers, cropEvidence,
                                    pageBitmap.width, pageBitmap.height
                                )
                                if (pageCoverage.sourceEvidenceAvailable) {
                                    pageCoveragePagesAudited++
                                    pageCoverageMissingStartCount += pageCoverage.missingStartCount
                                    pageCoverageDuplicateStartCount += pageCoverage.duplicateStartCount
                                }
                                val individuallyUnsafe = if (auditSourceNumbers.isNotEmpty()) {
                                    croppedBoxes.indices.filter { cropIndex ->
                                        com.example.util.PdfCropGeometryAudit.issues(
                                            cropEvidence[cropIndex], auditSourceNumbers,
                                            pageBitmap.width, pageBitmap.height
                                        ).isNotEmpty()
                                    }.toSet()
                                } else emptySet()
                                val unsafeCropIndexes = individuallyUnsafe +
                                    pageCoverage.unsafeCropIndexes
                                mergedOrMisnumberedCropCount += unsafeCropIndexes.size

                                val classificationStartedAtMs = android.os.SystemClock.elapsedRealtime()
                                val pageCandidates = if (croppedBoxes.isEmpty()) {
                                    emptyList()
                                } else {
                                    val classifications = coroutineScope {
                                        val semaphore = Semaphore(2)
                                        croppedBoxes.map { cropBox ->
                                            async {
                                                // Whole-page ML Kit has already run on
                                                // this bitmap. If its spatial text is
                                                // complete, skip a second bitmap load,
                                                // JPEG decode and ML Kit model invocation.
                                                // Selectable PDFs already contain
                                                // OCR-free decoded text with physical
                                                // line coordinates from PDFBox.
                                                // A complete, crop-contained A-E
                                                // run avoids re-decoding its JPEG
                                                // and re-running ML Kit on the crop.
                                                val nativeText = if (useNativeQuestionStarts &&
                                                    source != null
                                                ) {
                                                    com.example.util.PdfPageQuestionTextReuse
                                                        .readNativeIfComplete(
                                                            fragments = localBlueprint
                                                                .pageVectorTextFragments[pageIdx].orEmpty(),
                                                            normalizedCrop = cropBox.boxNormalized,
                                                            pdfPageWidth = source.pageWidth,
                                                            pdfPageHeight = source.pageHeight
                                                        )
                                                } else null
                                                val pageText = if (nativeText == null) {
                                                    com.example.util.PdfPageQuestionTextReuse
                                                        .readIfComplete(
                                                            lines = pageLayout.positionedText,
                                                            normalizedCrop = cropBox.boxNormalized,
                                                            pageWidth = pageBitmap.width,
                                                            pageHeight = pageBitmap.height
                                                        )
                                                } else null
                                                // On scanned books Paddle has ALREADY
                                                // read this whole page as an independent
                                                // answer/anchor source. Reuse its
                                                // crop-contained complete A-E text
                                                // before firing another ML Kit crop
                                                // inference; missing options still
                                                // trigger the original detailed pass.
                                                val paddleText = if (nativeText == null &&
                                                    pageText == null && paddleEvidence != null
                                                ) {
                                                    com.example.util.PdfPageQuestionTextReuse
                                                        .readIfComplete(
                                                            lines = paddleEvidence.textCells,
                                                            normalizedCrop = cropBox.boxNormalized,
                                                            pageWidth = pageBitmap.width,
                                                            pageHeight = pageBitmap.height
                                                        )
                                                } else null
                                                val reused = nativeText ?: pageText ?: paddleText
                                                if (reused != null) {
                                                    buildQuestionOcrClassification(
                                                        questionText = reused,
                                                        examTypeHint = scanPageInfo.examType,
                                                        subjectNameHint = scanPageInfo.subjectName,
                                                        suggestedTopicName = scanPageInfo.topicName,
                                                        trustSuggestedTopic = false,
                                                        hasDiagramOrGraph = cropBox.hasDiagramOrGraph,
                                                        lineCountEstimate = cropBox.lineCountEstimate,
                                                        visualDifficultyHint = cropBox.estimatedDifficulty5
                                                    ) to when {
                                                        nativeText != null -> 0
                                                        pageText != null -> 1
                                                        else -> 2 // independently read Paddle
                                                    }
                                                } else {
                                                    semaphore.withPermit {
                                                        val cropReader = cropOcrPool.receive()
                                                        try {
                                                            classifyCroppedQuestion(
                                                                context = context,
                                                                imagePath = cropBox.croppedImagePath,
                                                                examTypeHint = scanPageInfo.examType,
                                                                subjectNameHint = scanPageInfo.subjectName,
                                                                pageTopicHint = scanPageInfo.topicName,
                                                                visualDifficultyHint = cropBox.estimatedDifficulty5,
                                                                hasDiagramOrGraph = cropBox.hasDiagramOrGraph,
                                                                lineCountEstimate = cropBox.lineCountEstimate,
                                                                processingStatus = processingStatus,
                                                                reader = cropReader
                                                            ) to 3
                                                        } finally {
                                                            cropOcrPool.trySend(cropReader)
                                                        }
                                                    }
                                                }
                                            }
                                        }.awaitAll()
                                    }
                                    android.util.Log.i(
                                        "SoruLabPdfPerf",
                                        "page=${pageIdx + 1}" +
                                            " elapsedMs=${android.os.SystemClock.elapsedRealtime() - pageStartMs}" +
                                            " renderMs=${renderFinishedAtMs - pageStartMs}" +
                                            " primaryOcrMs=${primaryOcrFinishedAtMs - renderFinishedAtMs}" +
                                            " paddleStageMs=${paddleFinishedAtMs - primaryOcrFinishedAtMs}" +
                                            " geometryMs=${cropStartedAtMs - paddleFinishedAtMs}" +
                                            " cropMs=${cropFinishedAtMs - cropStartedAtMs}" +
                                            " classifyMs=${android.os.SystemClock.elapsedRealtime() - classificationStartedAtMs}" +
                                            " crops=${classifications.size}" +
                                            " nativeText=${classifications.count { it.second == 0 }}" +
                                            " pageOcrText=${classifications.count { it.second == 1 }}" +
                                            " paddleText=${classifications.count { it.second == 2 }}" +
                                            " extraCropOcr=${classifications.count { it.second == 3 }}"
                                    )
                                    croppedBoxes.mapIndexed { index, cropBox ->
                                        val recognized = classifications[index].first
                                        // Only an explicit current-page heading may fill a missing
                                        // topic. A previous chapter or filename is not evidence.
                                        val classification = when {
                                            // The printed source title is stronger than
                                            // OCR keyword guesses inside individual crops.
                                            printedHeaderTopic != null ->
                                                recognized.copy(topicName = printedHeaderTopic)
                                            recognized.topicName.isBlank() && explicitHeaderTopic != null ->
                                                recognized.copy(topicName = explicitHeaderTopic)
                                            else -> recognized
                                        }
                                        PageQuestionCandidate(
                                            cropBox = cropBox,
                                            classification = classification,
                                            answerKeyQuestionNumber = cropBox.questionOrderInPage
                                                .takeIf { cropBox.hasPrintedQuestionNumber && it > 0 }
                                        )
                                    }
                                }

                                for ((boxIdx, candidate) in pageCandidates.withIndex()) {
                                    val cropBox = candidate.cropBox
                                    val classification = candidate.classification
                                    val globalQuestionNum = allExtractedQuestions.size + 1
                                    // Deferred answer matching: future pages
                                    // may contain the key for this exact test.
                                    val verifiedAnswer: String? = null
                                    val extractedId =
                                        "local_q_${System.currentTimeMillis()}_p${pageIdx + 1}_${boxIdx + 1}_$globalQuestionNum"
                                    if (pageAnswerScope != null) {
                                        expectedScopeByQuestionId[extractedId] = pageAnswerScope
                                    }
                                    if (boxIdx in unsafeCropIndexes) {
                                        // Keep for manual correction, but NEVER
                                        // grade this visually inconsistent crop
                                        // as a verified answer.
                                        unsafeCropQuestionIds.add(extractedId)
                                    }

                                    allExtractedQuestions.add(
                                        ExtractedPdfQuestionItem(
                                            id = extractedId,
                                            questionNumber = globalQuestionNum,
                                            examType = classification.examType,
                                            subjectName = classification.subjectName,
                                            topicName = classification.topicName,
                                            difficulty = classification.difficulty,
                                            targetLevel = classification.targetLevel,
                                            correctOption = verifiedAnswer.orEmpty(),
                                            answerKeyMatched = verifiedAnswer != null,
                                            publisherName = finalPubName,
                                            publisherLogoUri = finalLogoUri,
                                            questionText = classification.questionText,
                                            solutionSummary = "Kaynak: $finalPubName ($finalBookTitle) • " +
                                                "Konu: ${classification.topicName.ifBlank { "Belirlenemedi (inceleme gerekli)" }} • " +
                                                "Tahmini zorluk: ${classification.difficulty} • " +
                                                if (verifiedAnswer != null) "Doğrulanmış Cevap: $verifiedAnswer" else "Cevap anahtarı eşleşmesi bulunamadı",
                                            croppedImagePath = cropBox.croppedImagePath,
                                            cropAspectRatio = cropBox.aspectRatio,
                                            bookCoverPath = coverPath,
                                            boxNormalized = cropBox.boxNormalized,
                                            testName = finalBookTitle,
                                            bookId = bookId,
                                            imageBase64 = cropBox.imageBase64,
                                            pdfFilePath = archivedPdfPath,
                                            pageIndex = pageIdx,
                                            // Never substitute the page-local crop order for a
                                            // printed question number; it can silently
                                            // assign another question's correct answer.
                                            questionNumberInTest =
                                                verifiedPrintedQuestionNumber(candidate.answerKeyQuestionNumber),
                                            sourceTestId = pageAnswerScope?.let { scope ->
                                                "${scope.section}~${if (scope.isOsymTest) "osym" else scope.testKind}~${scope.testNumber}"
                                            }.orEmpty()
                                        )
                                    )
                                }

                                kotlinx.coroutines.yield()
                            }
                        }
                    }
                    } finally {
                        // Always release the page model after successful,
                        // failed or cancelled imports. Cropped-question OCR
                        // uses separate readers and is already awaited.
                        pageOcrReader.close()
                        cropOcrReaders.forEach { it.close() }
                        cropOcrPool.close()
                    }
                }

                // After the full OCR scan, we know the printed chapters used
                // by THIS book. Re-read the buffered answer-table rows against
                // those sections. This rejects publisher titles and unrelated
                // uppercase headers without maintaining any brand-specific list.
                val observedSections = expectedScopeByQuestionId.values
                    .map { it.section }.filter(String::isNotBlank).toSet()
                val scopedAnswers = PdfScopedAnswerKeys(observedSections)
                for (region in pendingAnswerRegions) {
                    scopedAnswers.addOcrRows(region)
                }
                for ((scope, grid) in pendingHorizontalGrids) {
                    scopedAnswers.addPairs(scope, grid.answers.toList())
                }
                val secondaryAnswers = PdfScopedAnswerKeys(observedSections)
                localBlueprint.pageNativeAnswerRows.values.forEach(secondaryAnswers::addOcrRows)
                pendingSecondaryAnswerRegions.forEach(secondaryAnswers::addOcrRows)
                for ((scope, pageKeys) in pendingVectorFooterKeys) {
                    secondaryAnswers.addPairs(scope, pageKeys.toList())
                }
                for ((scope, grid) in pendingSecondaryGrids) {
                    secondaryAnswers.addPairs(scope, grid.answers.toList())
                }
                // The printed key must agree between ML Kit and native PDF/Paddle.
                // No model-generated solution is ever used as an answer key.
                val printedKeyConsensus = com.example.util.PdfOfflineAnswerConsensus.compare(
                    scopedAnswers, secondaryAnswers
                )
                val sourceKeysForCoverage = scopedAnswers.readings() + secondaryAnswers.readings()
                val importedSourceIdentities = allExtractedQuestions.mapNotNull { item ->
                    val scope = expectedScopeByQuestionId[item.id]
                    val number = item.questionNumberInTest
                    if (scope != null && number in 1..200) {
                        PdfAnswerId(scope, number)
                    } else null
                }
                val sourceKeyCoverage = PdfPrintedKeyCoverageAudit.assess(
                    sourceKeysForCoverage, importedSourceIdentities
                )

                // Match the completed, per-PDF answer index to the exact
                // chapter/test/printed-number identity of each extracted question.
                // A missing or ambiguous answer remains unresolved; never guess.
                for (idx in allExtractedQuestions.indices) {
                    val original = allExtractedQuestions[idx]
                    val scope = expectedScopeByQuestionId[original.id]
                    val correct = printedKeyConsensus.confirmedOption(
                        scope, original.questionNumberInTest
                    )
                    if (correct != null && original.id !in unsafeCropQuestionIds) {
                        allExtractedQuestions[idx] = original.copy(
                            correctOption = correct,
                            answerKeyMatched = true,
                            solutionSummary = original.solutionSummary
                                .substringBefore(" • Cevap anahtarı eşleşmesi bulunamadı") +
                                " • Doğrulanmış Cevap: $correct"
                        )
                    }
                }

                if (allExtractedQuestions.isEmpty()) {
                    val pageHint = buildString {
                        if (pageRenderFailureCount > 0) {
                            append(" $pageRenderFailureCount sayfa işlenirken hata oluştu.")
                        }
                        if (processingStatus.ocrFailureCount > 0) {
                            append(" Cihaz içi OCR ${processingStatus.ocrFailureCount} soruda tamamlanamadı.")
                        }
                    }
                    _batchErrorText.value = "PDF'te aktarılabilir soru bloğu bulunamadı.$pageHint Kırpma alanını ve PDF kalitesini kontrol edip yeniden deneyin."
                    completeOnce(emptyList(), coverPath, finalBookTitle)
                    return@launch
                }

                val warnings = buildList {
                    add("İçe aktarma raporu: $totalPdfPages sayfa • ${allExtractedQuestions.size} soru • " +
                        "${allExtractedQuestions.map { it.sourceTestId }.filter(String::isNotBlank).distinct().size} kaynak test • " +
                        "$detectedAnswerPages olası cevap sayfası • ${scopedAnswers.answerCount} okunan cevap • " +
                        "${allExtractedQuestions.count { it.answerKeyMatched }} doğrulanmış soru • " +
                        "${allExtractedQuestions.count { !it.answerKeyMatched }} inceleme gerekiyor.")
                    // Per-page printed numbering is stronger than a whole-book
                    // count: one missing right column cannot be concealed by
                    // false positives on unrelated pages.
                    val actualNumbersByPage = allExtractedQuestions
                        .filter { it.pageIndex >= 0 && it.questionNumberInTest > 0 }
                        .groupBy { it.pageIndex }
                        .mapValues { (_, records) ->
                            records.map { it.questionNumberInTest }.toSet()
                        }
                    val vectorMissingByPage = localBlueprint.pageVectorQuestionAnchors
                        .mapNotNull { (pageIndex, source) ->
                            val printed = source.anchors.map { it.number }.toSet()
                            val detected = actualNumbersByPage[pageIndex].orEmpty()
                            (printed - detected).takeIf { it.isNotEmpty() }
                                ?.let { pageIndex to it }
                        }
                    if (!sourceKeyCoverage.complete) {
                        add("KAYNAK TEST BÜTÜNLÜĞÜ: " +
                            "${sourceKeyCoverage.totalSourceNumbers} kaynak cevap hücresi kimliği, " +
                            "${sourceKeyCoverage.missingSourceQuestions.size} eksik soru, " +
                            "${sourceKeyCoverage.duplicateImportedQuestions.size} mükerrer soru, " +
                            "${sourceKeyCoverage.unexpectedImportedQuestions.size} kaynak anahtarda bulunmayan soru. " +
                            "Kitap eksiksiz kabul edilemez.")
                    }
                    add("CEVAP OKUMA UZLAŞMASI: " +
                        "${printedKeyConsensus.confirmed.size} hücre iki bağımsız okuyucuda uyuştu, " +
                        "${printedKeyConsensus.disputed.size} hücre çelişkili, " +
                        "${printedKeyConsensus.unsupported.size} hücre ikinci kaynakça doğrulanamadı. " +
                        "Çelişkili ve tek kaynaklı cevaplar doğrulanmış kabul edilmedi.")
                    if (offlineRejectedQuestionPages > 0) {
                        add("YEREL BELGE ANALİZİ: $offlineRejectedQuestionPages soru sayfası güvenle kırpılamadı. Eksik sayfalar tamamlanmış kabul edilmedi.")
                    }
                    if (mergedOrMisnumberedCropCount > 0) {
                        add("KIRPMA BÜTÜNLÜĞÜ: $mergedOrMisnumberedCropCount görüntüde " +
                            "birleşmiş, mükerrer, yanlış numaralı veya kaynağı belirsiz kırpma " +
                            "saptandı. Bu görüntüler cevapları bulunsa bile doğrulanmış sayılmadı.")
                    }
                    if (pageCoverageMissingStartCount > 0 ||
                        pageCoverageDuplicateStartCount > 0
                    ) {
                        add("SAYFA BÜTÜNLÜĞÜ: kaynakta güvenle tespit edilmiş " +
                            "$pageCoverageMissingStartCount basılı soru başlangıcı eksik, " +
                            "$pageCoverageDuplicateStartCount başlangıç birden fazla kırpmada " +
                            "görünüyor. Kaynak numarası bulunan $pageCoveragePagesAudited " +
                            "sayfa denetlendi. Kitap eksiksiz aktarılmış sayılamaz.")
                    }
                    val missedPrintedCount = vectorMissingByPage.sumOf { it.second.size }
                    if (missedPrintedCount > 0) {
                        val preview = vectorMissingByPage.take(8).joinToString(", ") {
                            "sayfa ${it.first + 1}: ${it.second.sorted().joinToString("/")}"
                        }
                        add("KIRPMA EKSİĞİ: PDF metin katmanında görülen en az " +
                            "$missedPrintedCount basılı soru numarası henüz kırpılmadı " +
                            "(${vectorMissingByPage.size} sayfa; $preview). " +
                            "Kitaplıkta inceleme gerektiği açıkça belirtilmeli.")
                    }
                    val vectorExpected = localBlueprint.pageTextFooterKeys.values.sumOf { it.size }
                    if (vectorExpected > 0 && allExtractedQuestions.size != vectorExpected) {
                        add("ÖNEMLİ: PDF metin katmanındaki $vectorExpected cevap hücresine karşılık " +
                            "yalnızca ${allExtractedQuestions.size} soru kesildi. Eksik veya fazla " +
                            "kırpmalar için sayfa önizlemesini kontrol edin; eksik kitabı tam " +
                            "aktarılmış saymayın.")
                    }
                    val unknownTopicCount = allExtractedQuestions.count {
                        it.topicName.isBlank()
                    }
                    if (unknownTopicCount > 0) {
                        add("KONU İNCELEMESİ: $unknownTopicCount soruda güvenilir konu kanıtı " +
                            "bulunamadı; konu uydurulmadı.")
                    }
                    add("Zorluk dereceleri otomatik tahmindir; kesin insan değerlendirmesi " +
                        "olarak sunulmaz.")
                    if (scopedAnswers.answerCount == 0) {
                        add("$detectedAnswerPages olası cevap anahtarı sayfası tarandı ancak " +
                            "geçerli test-cevap eşleşmesi bulunamadı. Hiçbir cevap tahmin edilmedi.")
                    }
                    val unresolved = allExtractedQuestions.filterNot { it.answerKeyMatched }
                    if (unresolved.isNotEmpty()) {
                        // Explain WHY a key was not matched, without silently filling
                        // missing answers or presenting every OCR failure as identical.
                        val missingNumber = unresolved.count { it.questionNumberInTest !in 1..200 }
                        val missingScope = unresolved.count {
                            it.questionNumberInTest in 1..200 &&
                                expectedScopeByQuestionId[it.id] == null
                        }
                        val unreadTestKey = unresolved.count { question ->
                            question.questionNumberInTest in 1..200 &&
                                expectedScopeByQuestionId[question.id]?.let { scope ->
                                    !scopedAnswers.hasRecognizedScope(scope)
                                } == true
                        }
                        val conflictingKeys = unresolved.count { question ->
                            scopedAnswers.hasConflictingAnswer(
                                expectedScopeByQuestionId[question.id],
                                question.questionNumberInTest
                            )
                        }
                        val absentKeyCells = unresolved.size -
                            missingNumber - missingScope - unreadTestKey - conflictingKeys
                        add("${unresolved.size} soru güvenle eşleştirilemedi. Ayrıntı: " +
                            "$missingNumber basılı numara okunamadı • " +
                            "$missingScope test başlığı eşleşmedi • " +
                            "$unreadTestKey kaynak testin cevapları okunamadı • " +
                            "$conflictingKeys çelişkili cevap • " +
                            "$absentKeyCells testte eksik cevap hücresi. " +
                            "Yanlış cevap tahmini yapılmadı.")
                    }
                    if (recoveredPrintedQuestionPages > 0) {
                        add("$recoveredPrintedQuestionPages sayfada basılı numara temelli ikinci " +
                            "kırpma denemesi daha eksiksiz sonuç verdi.")
                    }
                    if (processingStatus.ocrFailureCount > 0) {
                        add("Cihaz içi OCR ${processingStatus.ocrFailureCount} soruda tamamlanamadı; kırpılmış görseller yine aktarıldı.")
                    }
                    if (suspectedSkippedQuestionPages.isNotEmpty()) {
                        val pagePreview = suspectedSkippedQuestionPages.take(12)
                            .joinToString(", ") { (it + 1).toString() }
                        add("OLASI EKSİK SORU SAYFASI: " +
                            "${suspectedSkippedQuestionPages.size} sayfada soru/şık işareti " +
                            "görüldü ancak güvenli kırpma yapılamadı " +
                            "(PDF sayfaları: $pagePreview). " +
                            "Eksiksiz aktarım doğrulanmadı; sayfaları inceleyin.")
                    }
                    if (pageRenderFailureCount > 0) {
                        add("$pageRenderFailureCount PDF sayfası işlenemedi; diğer sayfalar aktarıldı.")
                    }
                }
                _batchWarningText.value = warnings.takeIf { it.isNotEmpty() }?.joinToString(" ")
                // Never repeat the old failure: 875 corrupted images persisted
                // even though the system knew that hundreds were incomplete.
                // A partially processed book is a FAILED import, not a
                // successful library entry. Existing library data is untouched.
                val completePdf = sourceKeyCoverage.complete &&
                    offlineRejectedQuestionPages == 0 &&
                    suspectedSkippedQuestionPages.isEmpty() &&
                    pageRenderFailureCount == 0 &&
                    processingStatus.ocrFailureCount == 0 &&
                    mergedOrMisnumberedCropCount == 0 &&
                    pageCoverageMissingStartCount == 0 &&
                    pageCoverageDuplicateStartCount == 0 &&
                    allExtractedQuestions.all {
                        it.answerKeyMatched && it.topicName.isNotBlank()
                    }
                if (!completePdf) {
                    _batchErrorText.value = "Kitap eksik veya güvensiz: " +
                        "$offlineRejectedQuestionPages reddedilmiş yerel kırpma sayfası, " +
                        "${suspectedSkippedQuestionPages.size} şüpheli eksik sayfa, " +
                        "${sourceKeyCoverage.missingSourceQuestions.size} kaynak cevaba göre eksik soru, " +
                        "${sourceKeyCoverage.duplicateImportedQuestions.size} mükerrer soru, " +
                        "${sourceKeyCoverage.unexpectedImportedQuestions.size} kaynak anahtarda bulunmayan soru, " +
                        "${allExtractedQuestions.count { !it.answerKeyMatched }} cevabı doğrulanmayan soru, " +
                        "${allExtractedQuestions.count { it.topicName.isBlank() }} basılı konusu belirlenemeyen soru, " +
                        "$mergedOrMisnumberedCropCount geometrisi güvensiz kırpma, " +
                        "$pageCoverageMissingStartCount eksik basılı soru başlangıcı, " +
                        "$pageCoverageDuplicateStartCount mükerrer soru başlangıcı, " +
                        "$pageRenderFailureCount işlenemeyen PDF sayfası, " +
                        "${processingStatus.ocrFailureCount} OCR hatası. " +
                        "Cevap kaynakları: ML Kit ${scopedAnswers.answerCount}, " +
                        "bağımsız PDF/Paddle ${secondaryAnswers.answerCount}, " +
                        "iki kaynaktan doğrulanan ${printedKeyConsensus.confirmed.size}, " +
                        "çelişkili ${printedKeyConsensus.disputed.size}, " +
                        "tek kaynaktan okunan ${printedKeyConsensus.unsupported.size}. " +
                        "Hatalı kitabı otomatik kaydetmedik; önce bunları düzeltmek gerekir."
                    // These cropped files were staged only for this import.
                    // Do not delete previously saved library images.
                    allExtractedQuestions.forEach { question ->
                        if (question.croppedImagePath.isNotBlank()) {
                            StudioImageAndPdfEngine.deleteQuestionImage(
                                context, question.croppedImagePath
                            )
                        }
                    }
                    completeOnce(emptyList(), "", "")
                    return@launch
                }
                _batchProgressPercent.value = 100
                _batchStatusText.value = "Sayfa $totalPdfPages / $totalPdfPages tamamlandı • Doğrulanmış Soru: ${allExtractedQuestions.size}"
                completeOnce(allExtractedQuestions, coverPath, finalBookTitle)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException && e !is TimeoutCancellationException) throw e
                android.util.Log.e("SoruLabPdfImport", "PDF aktarımı başarısız; hata kaynağı için yığın izi", e)
                _batchErrorText.value = describePdfFailure(e)
                completeOnce(emptyList(), "", "")
            } finally {
                reusableBitmapRef[0]?.recycle()
                reusableBitmapRef[0] = null
                reusablePixelsRef[0] = null
                _isAutoTagging.value = false
                _batchProgressPercent.value = 0
                _batchStatusText.value = null
            }
        }
    }
}
