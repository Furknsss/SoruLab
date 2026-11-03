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
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

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
    val sourceTestId: String = ""
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
    val optionAnchors: List<QuestionOptionAnchor> = emptyList()
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

    private val generativeModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val twinQuestionSchema = Schema.obj(
        mapOf(
            "questionStem" to Schema.string("Orijinal sorunun kazanımına birebir paralel yeni İkiz Soru metni"),
            "optionA" to Schema.string("A şıkkı"),
            "optionB" to Schema.string("B şıkkı"),
            "optionC" to Schema.string("C şıkkı"),
            "optionD" to Schema.string("D şıkkı"),
            "optionE" to Schema.string("E şıkkı"),
            "correctOption" to Schema.enumeration(listOf("A", "B", "C", "D", "E"), "Doğru seçenek"),
            "solutionExplanation" to Schema.string("İkiz sorunun adım adım net çözümü")
        )
    )

    private val twinQuestionGeneratorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = twinQuestionSchema
            }
        )
    }

    private val initialWelcomeMessage = AiChatMessage(
        id = "welcome",
        isUser = false,
        text = "Merhaba! Ben SoruLab AI Asistan. Takıldığın YKS (TYT & AYT) sorularını adım adım çözebilir, kritik teoremleri ve ÖSYM tuzak notlarını açıklayabilirim. Hangi soru üzerinde çalışalım?"
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

    fun askAboutQuestion(
        question: YksQuestion,
        context: Context,
        customPrompt: String = ""
    ) {
        if (customPrompt.isBlank() && lastAskedQuestionId == question.id && _messages.value.size > 1) {
            return
        }
        lastAskedQuestionId = question.id

        val promptToDisplay = if (customPrompt.isNotBlank()) {
            customPrompt
        } else {
            "${question.topicHeader} • Soru ${question.number} için Kritik Bilgi, Adım Adım Çözüm ve ÖSYM Tuzak Notunu anlatır mısın?"
        }

        val userMsg = AiChatMessage(
            id = "u_${System.currentTimeMillis()}",
            isUser = true,
            text = promptToDisplay,
            attachedQuestionTitle = "${question.topicHeader} • Soru ${question.number}"
        )
        _messages.value = _messages.value + userMsg
        _isLoading.value = true

        viewModelScope.launch {
            val fullPrompt = buildString {
                appendLine(SorulabAiQuality.SYSTEM_INSTRUCTION)
                appendLine("Yanıt formatı: 1) Kritik Bilgi & Teorem 2) Adım Adım Çözüm 3) ÖSYM Tuzak Notu")
                appendLine(SorulabAiQuality.questionContext(question))
                if (customPrompt.isNotBlank()) {
                    appendLine("Öğrencinin Özel Sorusu: $customPrompt")
                }
            }

            val questionBmp = withContext(Dispatchers.IO) {
                StudioImageAndPdfEngine.getCachedBitmap(
                    context = context,
                    path = question.imagePath,
                    maxDimension = 1600
                )
            }

            try {
                val response = withTimeout(60_000L) {
                    if (questionBmp != null) {
                        generativeModel.generateContent(
                            content {
                                image(questionBmp)
                                text(fullPrompt)
                            }
                        )
                    } else {
                        generativeModel.generateContent(fullPrompt)
                    }
                }
                val replyText = response.text?.takeIf { it.isNotBlank() }
                    ?: ("Adım Adım Çözüm (${question.topicHeader} - Soru ${question.number}):\n\n" +
                        question.stepByStepSolution +
                        "\n\nDoğru Cevap: ${question.correctOption}")

                _messages.value = _messages.value + AiChatMessage(
                    id = "ai_${System.currentTimeMillis()}",
                    isUser = false,
                    text = replyText
                )
            } catch (_: TimeoutCancellationException) {
                _messages.value = _messages.value + AiChatMessage(
                    id = "ai_${System.currentTimeMillis()}",
                    isUser = false,
                        text = SorulabAiQuality.fallbackNotice(question)
                )
            } catch (_: Exception) {
                _messages.value = _messages.value + AiChatMessage(
                    id = "ai_${System.currentTimeMillis()}",
                    isUser = false,
                    text = "Adım Adım Çözüm (${question.topicHeader} - Soru ${question.number}):\n\n" +
                        "${question.stepByStepSolution}\n\nDoğru Seçenek: ${question.correctOption}"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Generates or retrieves the step-by-step solution on-demand when the user clicks
     * "Soru Çözümü" inside the `⋮` (Three-Dot) menu in Review Mode.
     */
    fun generateQuestionSolutionOnDemand(
        question: YksQuestion,
        context: Context,
        onResult: (String) -> Unit
    ) {
        viewModelScope.launch {
            val questionBmp = withContext(Dispatchers.IO) {
                StudioImageAndPdfEngine.getCachedBitmap(
                    context = context,
                    path = question.imagePath,
                    maxDimension = 1600
                )
            }

            val prompt = buildString {
                appendLine(SorulabAiQuality.SYSTEM_INSTRUCTION)
                appendLine(SorulabAiQuality.questionContext(question))
                appendLine("Görseldeki soruyu dikkatle incele ve çözümü kesinlikle şu 3 aşamalı formatta net Türkçe ile yaz:")
                appendLine("📌 Kritik Bilgi & Teorem: [Sorunun çözümünde kullanılan temel formül, kazanım ve teorem]")
                appendLine("✏️ Adım Adım Çözüm: [1. Adım, 2. Adım ve 3. Adım olarak sonucun neden ${question.correctOption} seçeneği olduğunu işlemli göster]")
                appendLine("⚠️ ÖSYM Tuzak Notu: [Öğrencilerin bu soru tipinde sık düştüğü çeldirici ve dikkat edilmesi gereken nokta]")
            }

            val defaultStructuredText = buildStructuredSolutionForQuestion(question)

            try {
                val response = withTimeout(45_000L) {
                    if (questionBmp != null) {
                        generativeModel.generateContent(
                            content {
                                image(questionBmp)
                                text(prompt)
                            }
                        )
                    } else {
                        generativeModel.generateContent(prompt)
                    }
                }
                val text = response.text?.trim().orEmpty()
                if (text.isNotBlank()) {
                    onResult(text)
                } else {
                    onResult(defaultStructuredText)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException && e !is TimeoutCancellationException) throw e
                onResult(defaultStructuredText)
            }
        }
    }

    private fun buildStructuredSolutionForQuestion(question: YksQuestion): String {
        val rawExisting = question.stepByStepSolution.trim()
        if (rawExisting.contains("Kritik Bilgi") && rawExisting.contains("Adım Adım")) {
            return rawExisting
        }
        val topic = question.topicHeader.ifBlank { "YKS Kazanımı" }
        val ans = question.correctOption
        return AiStructuredSolution(
            kritikBilgiTeorem = "$topic kazanımında verilen bağıntıdaki sınır koşulları ve temel denklem eşitliği doğrudan uygulanır.",
            adimAdimCozum = "1. Adım: Soruda verilen öncül ve sayısal değerler standart $topic bağıntısında yerine yazılır.\n" +
                "2. Adım: İfade sadeleştirilip bilinmeyen büyüklük yalnız bırakıldığında aranan değer elde edilir.\n" +
                "3. Adım: Elde edilen sonuç şıklarla eşleştirildiğinde doğru yanıt $ans seçeneği olarak bulunur.",
            osymTuzakNotu = "Birim dönüşümleri, işaret değişimleri ve tanım kümesi sınır değerleri işlem sonunda mutlaka kontrol edilmelidir."
        ).toFormattedText()
    }

    /**
     * Generates a 1-to-1 Twin Question ("İkiz Soru") on-demand when the user clicks
     * "İkiz Soru" inside the `⋮` (Three-Dot) menu in Review Mode.
     */
    fun generateTwinQuestionOnDemand(
        question: YksQuestion,
        context: Context,
        onResult: (TwinQuestionModalData) -> Unit
    ) {
        viewModelScope.launch {
            val questionBmp = withContext(Dispatchers.IO) {
                StudioImageAndPdfEngine.loadGuaranteedQuestionBitmap(
                    context = context,
                    questionId = question.id,
                    imagePath = question.imagePath,
                    imageBase64 = question.imageBase64,
                    pdfFilePath = question.pdfFilePath,
                    pageIndex = question.pageIndex,
                    normLeft = question.normLeft,
                    normTop = question.normTop,
                    normRight = question.normRight,
                    normBottom = question.normBottom
                )?.copy(Bitmap.Config.ARGB_8888, false)
            }
            if (questionBmp == null) {
                onResult(
                    TwinQuestionModalData(
                        topicTitle = question.topicHeader,
                        difficultyLabel = question.difficulty.label,
                        errorMessage = "Orijinal sorunun kırpılmış görseli bulunamadı; ikiz soru üretilemedi."
                    )
                )
                return@launch
            }

            try {
                val subjectName = SoruLabCatalog.subjectNameForTopic(question.topicHeader)
                    ?: "Konu başlığından belirlenemedi"
                val prompt = buildString {
                    appendLine("Sen uzman bir ÖSYM YKS soru yazarısın.")
                    appendLine("Görev: Görseldeki soruyu önce tamamen çözümle; sonra aynı kazanımı ölçen, aynı zorlukta ve aynı temel çözüm yöntemini gerektiren yeni bir ikiz soru yaz.")
                    appendLine("Yeni soruda yalnızca sayıları/nesneleri rastgele değiştirme. Verilenler ve şıklar yeni soruya göre tutarlı olsun; öğrenci aynı beceriyi yeni veriler üzerinde uygulamak zorunda kalsın.")
                    appendLine("Soru kökünü ve şıkları kopyalama. En az bir önemli niceliği veya koşulu değiştir; yeni sorunun cevabını sıfırdan hesapla. Orijinal sorunun doğru şıkkını yeni sorunun cevabı olarak varsayma.")
                    appendLine("Önce şu iç kontrolleri yap, çıktıya yalnızca son sonucu yaz: kazanım aynı mı, çözüm yolu ve zorluk paralel mi, verilenler yeterli mi, tam olarak bir doğru şık var mı, çözüm hesapla tutarlı mı?")
                    appendLine("A-E şıklarının beşi de dolu ve birbirinden farklı olmalı. Çeldiriciler yaygın işlem/kavram hatalarına dayansın; doğru cevap ve çözüm adımları birbiriyle uyuşsun.")
                    appendLine("Orijinal soru şekil, grafik veya tablo gerektiriyorsa yeni soru için gereken tüm bilgileri soru metninde açıkça tarif et; var olmayan bir görsele atıf yapma.")
                    appendLine("Matematiksel gösterimi ve Türkçe karakterleri koru. Yeni soru bağımsız, anlaşılır ve YKS sınav dilinde olsun.")
                    appendLine("Konu bağlamı: ${question.topicHeader}")
                    appendLine("Zorluk seviyesi: ${question.difficulty.label}")
                    appendLine("Orijinal soru kökü: ${question.stemTop}")
                    appendLine("Ders: ${question.subject.ifBlank { subjectName }}")
                    appendLine("Konu: ${question.topicHeader}")
                    if (question.bulletsOrSteps.isNotEmpty()) {
                        appendLine("Orijinal veriler/öncüller: ${question.bulletsOrSteps.joinToString(" | ")}")
                    }
                    if (question.stemBottom.isNotBlank()) {
                        appendLine("Orijinal yönerge: ${question.stemBottom}")
                    }
                    appendLine("Orijinal şıklar: ${question.options.entries.sortedBy { it.key }.joinToString(" | ") { "${it.key}) ${it.value}" }}")
                    appendLine("Orijinal cevabın doğru şıkkı: ${question.correctOption} (yeni sorunun cevabı değildir)")
                    if (question.stepByStepSolution.isNotBlank()) {
                        appendLine("Orijinal çözüm yöntemi/kazanım ipucu: ${question.stepByStepSolution.take(1200)}")
                    }
                    appendLine("JSON şemasındaki alanları doldur; solutionExplanation yeni sorunun çözümünü işlem işlem doğrulasın.")
                }
                val response = withTimeout(45_000L) {
                    twinQuestionGeneratorModel.generateContent(
                        content {
                            image(questionBmp)
                            text(prompt)
                        }
                    )
                }
                val obj = JSONObject(cleanJsonResponse(response.text.orEmpty()))
                val stem = obj.optString("questionStem", "").trim()
                val options = mapOf(
                    "A" to obj.optString("optionA", "").trim(),
                    "B" to obj.optString("optionB", "").trim(),
                    "C" to obj.optString("optionC", "").trim(),
                    "D" to obj.optString("optionD", "").trim(),
                    "E" to obj.optString("optionE", "").trim()
                )
                val correct = obj.optString("correctOption", "").uppercase()
                val explanation = obj.optString("solutionExplanation", "").trim()
                val distinctOptions = options.values
                    .map { it.trim().lowercase().replace(Regex("\\s+"), " ") }
                    .distinct()
                if (stem.length < 25 || options.values.any(String::isBlank) ||
                    distinctOptions.size != options.size ||
                    correct !in options.keys || explanation.length < 20
                ) {
                    throw IllegalStateException("Gemini tutarlı ve birbirinden farklı A-E şıkları olan geçerli bir ikiz soru döndürmedi.")
                }

                onResult(
                    TwinQuestionModalData(
                        topicTitle = question.topicHeader,
                        difficultyLabel = question.difficulty.label,
                        questionStem = stem,
                        options = options,
                        correctOption = correct,
                        solutionExplanation = explanation
                    )
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException && e !is TimeoutCancellationException) throw e
                onResult(
                    TwinQuestionModalData(
                        topicTitle = question.topicHeader,
                        difficultyLabel = question.difficulty.label,
                        errorMessage = "İkiz soru üretilemedi: ${e.localizedMessage ?: "Gemini yanıt hatası"}"
                    )
                )
            } finally {
                questionBmp.recycle()
            }
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
        processingStatus: PdfLocalProcessingStatus
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
            recognizeQuestionText(questionBitmap)
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

    private suspend fun recognizePdfPageLayout(bitmap: Bitmap): PdfPageOcrLayout {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val recognized = try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        } finally {
            recognizer.close()
        }
        val lines = recognized.textBlocks.flatMap { it.lines }
        val headerText = lines
            .filter { (it.boundingBox?.top ?: Int.MAX_VALUE) <= bitmap.height * 0.07f }
            .joinToString("\n") { it.text }
        // Publisher layouts vary: the title/test may start at 8%, 20% or
        // much lower on an illustrated page. Read until the first actual
        // numbered QUESTION instead of using a fixed 17%-of-page cutoff.
        val printedQuestionLine = Regex("""^\s*(?:[1-9]|[1-3][0-9])[.)-](?:\s|$)""")
        val firstQuestionTop = lines.filter {
            printedQuestionLine.containsMatchIn(it.text)
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
        val answerRegions = PdfAdaptiveAnswerRows.regions(
            cells = lines.mapNotNull { line ->
                line.boundingBox?.let {
                    PdfOcrTextCell(
                        text = line.text,
                        left = it.left, top = it.top,
                        right = it.right, bottom = it.bottom
                    )
                }
            },
            pageWidth = bitmap.width
        )
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
        if (answerGrids.isEmpty() && Regex("""[A-E]\s*[).]""").containsMatchIn(recognized.text)) {
            val footerCells = recognizeAnswerFooter(bitmap)
            answerGrids = PdfHorizontalAnswerGridReader.find(footerCells, bitmap.width, bitmap.height)
        }
        val anchorRegex = Regex("""^\s*([1-9]|[1-3][0-9])[\.\-\)]\s*""")
        val numberRegex = Regex("""^\s*(?:[1-9]|[1-3][0-9])[\.\-\)]?$""")
        val optionRegex = Regex("""^\s*([A-E])\s*[).:]\s*""", RegexOption.IGNORE_CASE)
        val anchors = lines.mapNotNull { line ->
            val match = anchorRegex.find(line.text) ?: return@mapNotNull null
            val number = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            val numberElement = line.elements.firstOrNull {
                numberRegex.matches(it.text.trim())
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
            optionAnchors = options
        )
    }

    private suspend fun recognizeAnswerFooter(bitmap: Bitmap): List<PdfOcrTextCell> {
        val top = (bitmap.height * 0.7f).toInt()
        val crop = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, bitmap.height - top)
        val targetWidth = (bitmap.width * 2).coerceAtMost(3600)
        val scale = targetWidth.toFloat() / crop.width
        val enlarged = Bitmap.createScaledBitmap(crop, targetWidth, (crop.height * scale).toInt(), true)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val result = recognizer.process(InputImage.fromBitmap(enlarged, 0)).await()
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
            recognizer.close()
            if (enlarged !== crop) enlarged.recycle()
            crop.recycle()
        }
    }

    private suspend fun recognizeQuestionText(bitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val recognizedText = try {
                recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            } finally {
                recognizer.close()
            }
            recognizedText.text
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
            is SecurityException -> "PDF dosyasına erişim izni alınamadı. Dosyayı yeniden seçip tekrar deneyin."
            is java.io.FileNotFoundException -> "Seçilen PDF artık bulunamıyor. Dosyayı yeniden seçin."
            is java.io.IOException -> "PDF okunamadı. Dosyanın bozuk veya parola korumalı olmadığını kontrol edin."
            is TimeoutCancellationException -> "PDF işlemi zaman aşımına uğradı. Daha küçük bir dosyayla veya daha sonra yeniden deneyin."
            else -> "PDF aktarımı tamamlanamadı (${error.javaClass.simpleName}). Dosyayı yeniden seçip tekrar deneyin."
        }
    }

    fun sendFreeMessage(userText: String, bitmap: Bitmap? = null) {
        if (userText.isBlank() && bitmap == null) return
        val trimmedText = userText.trim()
        val historyContext = _messages.value
            .takeLast(6)
            .joinToString("\n") { msg ->
                if (msg.isUser) "Öğrenci: ${msg.text}" else "SoruLab AI: ${msg.text}"
            }

        val userMsg = AiChatMessage(
            id = "u_${System.currentTimeMillis()}",
            isUser = true,
            text = if (trimmedText.isBlank()) "Görseldeki soruyu adım adım çözer misin?" else trimmedText,
            attachedQuestionTitle = if (bitmap != null) "Görsel Soru Eklendi" else null
        )
        _messages.value = _messages.value + userMsg
        _isLoading.value = true

        viewModelScope.launch {
            val systemInstruction = buildString {
                appendLine("Sen SoruLab AI adında uzman bir YKS (TYT-AYT) akademik koç ve soru çözüm asistanısın.")
                appendLine("Soruları adım adım net Türkçe ile yanıtla.")
                if (historyContext.isNotBlank()) {
                    appendLine("\nÖnceki Sohbet Bağlamı:")
                    appendLine(historyContext)
                }
                appendLine("\nÖğrencinin Yeni Mesajı: $trimmedText")
            }

            try {
                val response = withTimeout(60_000L) {
                    if (bitmap != null) {
                        val inputContent = content {
                            image(bitmap)
                            text(systemInstruction)
                        }
                        generativeModel.generateContent(inputContent)
                    } else {
                        generativeModel.generateContent(systemInstruction)
                    }
                }
                val reply = response.text?.takeIf { it.isNotBlank() }
                    ?: "Mesajınızı aldım, ancak şu an yanıt üretilemedi. Lütfen sorunuzu biraz daha detaylandırarak tekrar gönderin."
                _messages.value = _messages.value + AiChatMessage(
                    id = "ai_${System.currentTimeMillis()}",
                    isUser = false,
                    text = reply
                )
            } catch (e: Exception) {
                _messages.value = _messages.value + AiChatMessage(
                    id = "ai_${System.currentTimeMillis()}",
                    isUser = false,
                    text = "Bağlantı zaman aşımına uğradı veya AI servisine şu an erişilemiyor (${e.localizedMessage ?: "Bağlantı hatası"}). Lütfen internet bağlantınızı kontrol edip tekrar deneyin."
                )
            } finally {
                _isLoading.value = false
            }
        }
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
        _batchStatusText.value = "PDF açılıyor ve yapısal başlıklar ile cevap anahtarı okunuyor..."

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
                val pdfDigest = java.security.MessageDigest.getInstance("SHA-256")
                seekablePdfFile.inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        pdfDigest.update(buffer, 0, count)
                    }
                }
                val bookId = "book_" + pdfDigest.digest().joinToString("") { "%02x".format(it) }
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
                    "Yerel PDF metin haritası hazırlandı • ML Kit OCR ve SoruLab kırpma motoru başlatılıyor..."

                // STEP 3: On-device ML Kit OCR anchors plus SoruLab's local question-region cropper.
                val resolvedPub = SoruLabPublisherPresets.resolvePublisher(publisherName, customPublishers)
                val finalPubName = publisherName.ifBlank { resolvedPub.name }
                val finalLogoUri = publisherLogoUri.ifBlank { resolvedPub.customLogoPath }

                val allExtractedQuestions = mutableListOf<ExtractedPdfQuestionItem>()
                val expectedScopeByQuestionId = mutableMapOf<String, PdfAnswerScope>()
                val pendingHorizontalGrids = mutableListOf<Pair<PdfAnswerScope, PdfHorizontalAnswerGrid>>()
                var detectedAnswerPages = 0
                var pageRenderFailureCount = 0

                val pageIndicesToScan = 0 until totalPdfPages

                withContext(Dispatchers.IO) {
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

                                val pageLayout = try {
                                    recognizePdfPageLayout(pageBitmap)
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
                                val hasAnswerKeyMarker =
                                    StudioImageAndPdfEngine.hasAnswerKeyMarker(pageLayout.text)
                                val isAnswerTablePage =
                                    PdfScopedAnswerKeys.looksLikeAnswerPage(pageLayout.answerRows)
                                // Horizontal number/letter answer grids may sit
                                // directly underneath the last questions of a test.
                                val gridHeaderScope = PdfScopedAnswerKeys.scopeFromQuestionHeader(
                                    pageLayout.scopeHeaderText, pageInfo.topicName
                                )
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
                                    val hasQuestionEvidence =
                                        pageLayout.questionNumberAnchors.isNotEmpty() &&
                                            pageLayout.optionAnchors.map { it.option }
                                                .distinct().size >= 3
                                    if (!hasQuestionEvidence) {
                                        // A separate key page needs its own explicit heading.
                                        if (gridHeaderScope != null) pageLayout.answerGrids.forEach {
                                            pendingHorizontalGrids += gridHeaderScope to it
                                        }
                                        continue
                                    }
                                }
                                if (skipPage) continue
                                // A scanned foreword, contents page or advertisement
                                // can have numbered paragraphs that look like 1./2.
                                // Require A-E evidence before creating YKS questions.
                                if (pageLayout.questionNumberAnchors.isEmpty() ||
                                    pageLayout.optionAnchors.map { it.option }.distinct().size < 3) {
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
                                val explicitHeaderTopic = StudioImageAndPdfEngine.detectTopicNameFromText(
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
                                val detectedScope = gridHeaderScope
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

                                val croppedBoxes = try {
                                    StudioImageAndPdfEngine.cropRenderedPageLocalDualColumn(
                                        context = context,
                                        pageBitmap = pageBitmap,
                                        pageIndex = pageIdx,
                                        totalPages = rendererTotal,
                                        reusablePixelsRef = reusablePixelsRef,
                                        questionNumberAnchors = pageLayout.questionNumberAnchors,
                                        optionAnchors = pageLayout.optionAnchors,
                                        maxQuestionBottomY = pageLayout.answerGrids
                                            .filter { grid ->
                                                grid.top > (pageLayout.questionNumberAnchors
                                                    .maxOfOrNull { it.top } ?: Int.MAX_VALUE)
                                            }.minOfOrNull { it.top }
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

                                val pageCandidates = if (croppedBoxes.isEmpty()) {
                                    emptyList()
                                } else {
                                    val classifications = coroutineScope {
                                        val semaphore = Semaphore(2)
                                        croppedBoxes.map { cropBox ->
                                            async {
                                                semaphore.withPermit {
                                                    classifyCroppedQuestion(
                                                        context = context,
                                                        imagePath = cropBox.croppedImagePath,
                                                        examTypeHint = scanPageInfo.examType,
                                                        subjectNameHint = scanPageInfo.subjectName,
                                                        pageTopicHint = scanPageInfo.topicName,
                                                        visualDifficultyHint = cropBox.estimatedDifficulty5,
                                                        hasDiagramOrGraph = cropBox.hasDiagramOrGraph,
                                                        lineCountEstimate = cropBox.lineCountEstimate,
                                                        processingStatus = processingStatus
                                                    )
                                                }
                                            }
                                        }.awaitAll()
                                    }
                                    croppedBoxes.mapIndexed { index, cropBox ->
                                        val recognized = classifications[index]
                                        // Only an explicit current-page heading may fill a missing
                                        // topic. A previous chapter or filename is not evidence.
                                        val classification = if (recognized.topicName.isBlank() && explicitHeaderTopic != null) {
                                            recognized.copy(topicName = explicitHeaderTopic)
                                        } else recognized
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
                                            solutionSummary = "Kaynak: $finalPubName ($finalBookTitle) • Konu: ${classification.topicName} • Zorluk: ${classification.difficulty} • " +
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

                // Match the completed, per-PDF answer index to the exact
                // chapter/test/printed-number identity of each extracted question.
                // A missing or ambiguous answer remains unresolved; never guess.
                for (idx in allExtractedQuestions.indices) {
                    val original = allExtractedQuestions[idx]
                    val scope = expectedScopeByQuestionId[original.id]
                    val correct = scopedAnswers.get(scope, original.questionNumberInTest)
                    if (correct != null) {
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
                    if (scopedAnswers.answerCount == 0) {
                        add("$detectedAnswerPages olası cevap anahtarı sayfası tarandı ancak " +
                            "geçerli test-cevap eşleşmesi bulunamadı. Hiçbir cevap tahmin edilmedi.")
                    }
                    val missingScope = allExtractedQuestions.count {
                        it.sourceTestId.isBlank()
                    }
                    if (missingScope > 0) {
                        add("$missingScope sorunun kaynak test başlığı okunamadı.")
                    }
                    val unresolved = allExtractedQuestions.count { !it.answerKeyMatched }
                    if (unresolved > 0) {
                        add("$unresolved soru cevabı güvenle eşleştirilemedi; " +
                            "eşleşmeyen soru ve cevap anahtarlarını kontrol edin.")
                    }
                    if (processingStatus.ocrFailureCount > 0) {
                        add("Cihaz içi OCR ${processingStatus.ocrFailureCount} soruda tamamlanamadı; kırpılmış görseller yine aktarıldı.")
                    }
                    if (pageRenderFailureCount > 0) {
                        add("$pageRenderFailureCount PDF sayfası işlenemedi; diğer sayfalar aktarıldı.")
                    }
                }
                _batchWarningText.value = warnings.takeIf { it.isNotEmpty() }?.joinToString(" ")
                _batchProgressPercent.value = 100
                _batchStatusText.value = "Sayfa $totalPdfPages / $totalPdfPages tamamlandı • Aktarılan Soru: ${allExtractedQuestions.size}"
                completeOnce(allExtractedQuestions, coverPath, finalBookTitle)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException && e !is TimeoutCancellationException) throw e
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
