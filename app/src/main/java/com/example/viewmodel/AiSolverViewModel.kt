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
    val questionNumberInTest: Int = 0
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
    val targetLevel: String,
    val usedLocalOcr: Boolean = false,
    val answerKeyQuestionNumber: Int? = null
)

private data class PageQuestionCandidate(
    val cropBox: StudioImageAndPdfEngine.LocalCroppedBoxResult,
    val classification: QuestionOcrClassification,
    val answerKeyQuestionNumber: Int? = null
)

private data class PdfPageOcrLayout(
    val text: String = "",
    val headerText: String = "",
    val footerText: String = "",
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

private data class PdfAiStatus(
    @Volatile var unavailableReason: String? = null,
    @Volatile var bookBlueprintWarning: String? = null,
    @Volatile var pageFallbackFailureCount: Int = 0,
    @Volatile var pageFallbackRejectedQuestionCount: Int = 0,
    @Volatile var localOcrFailureCount: Int = 0,
    @Volatile var pageFallbackDisabled: Boolean = false
) {
    private var transientFailureCount = 0

    @Synchronized
    fun shouldDisableAfterRepeatedFailure(): Boolean {
        transientFailureCount++
        return transientFailureCount >= 3
    }
}

class AiSolverViewModel : ViewModel() {

    private val generativeModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val answerKeySchema = Schema.obj(
        mapOf(
            "testTitle" to Schema.string("Test veya bölüm başlığı"),
            "answers" to Schema.array(
                Schema.obj(
                    mapOf(
                        "orijinalSoruNo" to Schema.integer("Kitaptaki soru numarası (1, 2, 3...)"),
                        "correctOption" to Schema.enumeration(
                            listOf("A", "B", "C", "D", "E"),
                            "Cevap anahtarındaki doğru şık"
                        )
                    )
                ),
                "Kitap sonundaki veya sayfa altındaki cevap anahtarı eşleşmeleri"
            )
        )
    )

    private val answerKeyExtractorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = answerKeySchema
            }
        )
    }

    /**
     * High-Speed Schema for PDF Upload (Rule 2 & Rule 4):
     * - Automatically detects `kitapAdi`, `ders`, `konu` from the PDF page
     * - Detects questions (`sorular`) and crops excluding original question numbers (`box_2d`)
     * - Matches `orijinalSoruNo` and `dogruSecenek` with the Answer Key
     */
    private val fastPageQuestionsSchema = Schema.obj(
        properties = mapOf(
            "kitapAdi" to Schema.string("Sayfadan veya üst başlıktan tespit edilen kaynak kitap/deneme adı (Örn: 3D AYT Matematik Soru Bankası)"),
            "sinavTuru" to Schema.string("TYT veya AYT"),
            "ders" to Schema.string("Ders adı: Matematik, Geometri, Fizik, Kimya, Biyoloji veya Türkçe"),
            "konu" to Schema.string("Konu adı (Örn: Temel Kavramlar, Türev, Bağıl Hareket, Üçgende Alan, Paragrafta Anlam)"),
            "pageAnswerKey" to Schema.array(
                Schema.obj(
                    mapOf(
                        "orijinalSoruNo" to Schema.integer("Soru numarası (1, 2, 3...)"),
                        "dogruSecenek" to Schema.string("Doğru şık: A, B, C, D veya E")
                    )
                ),
                "Eğer bu sayfanın altında veya köşesinde cevap anahtarı şeridi varsa soru-şık listesi"
            ),
            "sorular" to Schema.array(
                Schema.obj(
                    properties = mapOf(
                        "orijinalSoruNo" to Schema.integer("PDF'teki orijinal soru numarası (1, 2, 3...). Cevap anahtarı eşleştirmesi içindir."),
                        "box_2d" to Schema.array(
                            Schema.integer("0-1000 arası koordinat"),
                            "Sırasıyla [ymin, xmin, ymax, xmax] (0-1000 ölçeğinde). PDF'teki eski soru numarasını (Örn: '1.', '7.') dışarıda bırakan, sadece soru gövdesi, şekiller ve A-E şıklarını kapsayan koordinatlar."
                        ),
                        "soruMetni" to Schema.string("Görselden okunabilen soru kökü ve şıklar; okunmayan yerleri uydurma"),
                        "hasQuestionStemOrFigure" to Schema.boolean("Soru kökü veya soruya ait şekil/grafik bulunuyor mu?"),
                        "hasAllFiveOptions" to Schema.boolean("A, B, C, D ve E şıklarının tamamı bu soru bloğunda bulunuyor mu?"),
                        "zorluk" to Schema.string("Kolay, Orta, ÖSYM, Zor veya Çok Zor"),
                        "ders" to Schema.string("Bu sorunun dersi: Matematik, Geometri, Fizik, Kimya, Biyoloji veya Türkçe"),
                        "konu" to Schema.string("Bu sorunun konusu (Örn: Temel Kavramlar, Türev, Bağıl Hareket)"),
                        "orijinalSoruNo" to Schema.integer("Görselde açıkça görünen basılı soru numarası; görünmüyorsa alanı üretme")
                    ),
                    optionalProperties = listOf("ders", "konu", "orijinalSoruNo")
                ),
                "Sayfada tespit edilen tüm sorular"
            )
        ),
        optionalProperties = listOf("kitapAdi", "pageAnswerKey")
    )

    private val pageQuestionsExtractorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = fastPageQuestionsSchema
            }
        )
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

    private val questionOcrSchema = Schema.obj(
        properties = mapOf(
            "questionText" to Schema.string("Soru kökü ve A-E şıkları dahil, görseldeki sorunun OCR metni"),
            "subjectName" to Schema.string("Soruya ait YKS dersi"),
            "topicName" to Schema.string("Soruya ait müfredat konusu"),
            "hasQuestionStemOrFigure" to Schema.boolean("Soru kökü veya soruya ait şekil/grafik bulunuyor mu?"),
            "hasAllFiveOptions" to Schema.boolean("A, B, C, D ve E şıklarının tamamı bu soru bloğunda bulunuyor mu?"),
            "originalQuestionNumber" to Schema.integer("Görselde açıkça görünen basılı soru numarası; görünmüyorsa alanı üretme"),
            "difficulty" to Schema.string("Soru zorluğu: Kolay, Orta, ÖSYM, Zor veya Çok Zor")
        ),
        optionalProperties = listOf("difficulty", "originalQuestionNumber")
    )

    private val questionOcrModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = questionOcrSchema
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
        aiStatus: PdfAiStatus
    ): QuestionOcrClassification {
        val questionBitmap = withContext(Dispatchers.IO) {
            StudioImageAndPdfEngine.getCachedBitmap(context, imagePath, maxDimension = 1440)
        }
        if (questionBitmap == null) {
            aiStatus.localOcrFailureCount++
            return buildQuestionOcrClassification(
                questionText = "",
                examTypeHint = examTypeHint,
                subjectNameHint = subjectNameHint,
                suggestedTopicName = pageTopicHint,
                trustSuggestedTopic = false,
                hasDiagramOrGraph = hasDiagramOrGraph,
                lineCountEstimate = lineCountEstimate,
                visualDifficultyHint = visualDifficultyHint,
                usedLocalOcr = true
            )
        }

        if (aiStatus.unavailableReason == null) {
                try {
                    val catalogTopics = SoruLabCatalog.allSubjects
                        .filter { it.examType.name.equals(examTypeHint, ignoreCase = true) }
                        .joinToString("\n") { subject ->
                            "${subject.name}: ${subject.topics.joinToString(", ") { it.name }}"
                        }
                    val prompt = buildString {
                        appendLine("Ekli kırpılmış görseldeki tek YKS sorusunu OCR ile eksiksiz oku.")
                        appendLine("Matematiksel sembolleri, üsleri, kesirleri, şekil/tablo açıklamalarını ve A), B), C), D), E) şıklarını koru.")
                        appendLine("Görselde basılı soru numarası açıkça görünüyorsa originalQuestionNumber alanına yaz; yoksa tahmin etme.")
                        appendLine("Ders ve konu adını aşağıdaki müfredattan seç; konu adını aynen yaz.")
                        appendLine(catalogTopics)
                        appendLine("Kitap bağlamı (yalnızca ipucu): $examTypeHint $subjectNameHint")
                        appendLine("Yalnızca istenen JSON alanlarını doldur.")
                    }
                    val response = withTimeout(30_000L) {
                        questionOcrModel.generateContent(
                            content {
                                image(questionBitmap)
                                text(prompt)
                            }
                        )
                    }
                    val obj = JSONObject(cleanJsonResponse(response.text.orEmpty()))
                    val classification = buildQuestionOcrClassification(
                        questionText = obj.optString("questionText", "").trim(),
                        examTypeHint = examTypeHint,
                        subjectNameHint = subjectNameHint,
                        suggestedTopicName = obj.optString("topicName", "").trim().ifBlank { pageTopicHint },
                        trustSuggestedTopic = obj.optString("topicName", "").isNotBlank(),
                        hasDiagramOrGraph = hasDiagramOrGraph,
                        lineCountEstimate = lineCountEstimate,
                        visualDifficultyHint = visualDifficultyHint,
                        usedLocalOcr = false
                    )
                    return classification.copy(
                        answerKeyQuestionNumber = obj.optInt("originalQuestionNumber", 0).takeIf { it > 0 }
                    )
                } catch (e: TimeoutCancellationException) {
                    if (aiStatus.shouldDisableAfterRepeatedFailure()) {
                        aiStatus.unavailableReason = "Gemini art arda yanıt vermedi; kalan sorular cihaz içi OCR ile aktarılacak."
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: OutOfMemoryError) {
                    aiStatus.unavailableReason = "Bellek sınırı nedeniyle Gemini OCR kullanılamadı; cihaz içi OCR deneniyor."
                } catch (e: Exception) {
                    if (shouldDisableGeminiForPdf(e) || aiStatus.shouldDisableAfterRepeatedFailure()) {
                        aiStatus.unavailableReason = describeGeminiFailure(e)
                    }
                }
        }

        val localText = try {
            recognizeQuestionText(questionBitmap)
        } catch (e: CancellationException) {
            throw e
        } catch (_: OutOfMemoryError) {
            aiStatus.localOcrFailureCount++
            ""
        } catch (_: Exception) {
            aiStatus.localOcrFailureCount++
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
            visualDifficultyHint = visualDifficultyHint,
            usedLocalOcr = true
        ).copy(
            answerKeyQuestionNumber = Regex("""(?m)^\s*(\d{1,3})[.)]?(?=\s|$)""")
                .find(localText)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
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
        val footerText = lines
            .filter { (it.boundingBox?.bottom ?: Int.MIN_VALUE) >= bitmap.height * 0.94f }
            .joinToString("\n") { it.text }
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
        val options = lines.mapNotNull { line ->
            val match = optionRegex.find(line.text) ?: return@mapNotNull null
            val bounds = line.boundingBox ?: return@mapNotNull null
            QuestionOptionAnchor(
                option = match.groupValues.getOrNull(1)?.uppercase().orEmpty(),
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom
            )
        }
        return PdfPageOcrLayout(
            text = recognized.text,
            headerText = headerText,
            footerText = footerText,
            questionNumberAnchors = anchors,
            optionAnchors = options
        )
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
        visualDifficultyHint: String,
        usedLocalOcr: Boolean
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
            targetLevel = targetLevel,
            usedLocalOcr = usedLocalOcr
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

    private fun shouldDisableGeminiForPdf(error: Exception): Boolean {
        val details = "${error.javaClass.simpleName} ${error.message.orEmpty()}".lowercase()
        return listOf(
            "invalid api key",
            "api key not valid",
            "invalid token",
            "invalid credentials",
            "unauthenticated",
            "permission_denied",
            "app check",
            "401",
            "403",
            "quota",
            "429",
            "resource_exhausted",
            "rate limit"
        ).any(details::contains)
    }

    private suspend fun extractQuestionsFromPageWithGemini(
        context: Context,
        pageBitmap: Bitmap,
        pageIndex: Int,
        pageInfo: StudioImageAndPdfEngine.LocalPageMapEntry,
        aiStatus: PdfAiStatus
    ): List<PageQuestionCandidate> {
        if (aiStatus.unavailableReason != null || aiStatus.pageFallbackDisabled) return emptyList()
        try {
            val catalogTopics = SoruLabCatalog.allSubjects
                .filter { it.examType.name.equals(pageInfo.examType, ignoreCase = true) }
                .joinToString("\n") { subject ->
                    "${subject.name}: ${subject.topics.joinToString(", ") { it.name }}"
                }
            val prompt = buildString {
                appendLine("Bu PDF sayfasındaki tüm soru bloklarını tek tek bul ve JSON şemasına göre çıkar.")
                appendLine("Her soru için koordinatları [ymin, xmin, ymax, xmax] olarak 0-1000 aralığında ver; soru kökü, varsa şekil ve görünen şıkları tamamen kapsa.")
                appendLine("Soru numarasını PDF'te basılı olan numaradan al. Okunmayan soru metnini veya cevabı uydurma.")
                appendLine("Ders ve konu için aşağıdaki müfredatı kullan; emin değilsen sayfa bağlamını izle.")
                appendLine(catalogTopics)
                appendLine("Sayfa bağlamı: ${pageInfo.examType} ${pageInfo.subjectName} ${pageInfo.topicName}")
                appendLine("Cevap anahtarı sayfada açıkça görünmüyorsa cevap üretme.")
            }
            val response = withTimeout(30_000L) {
                pageQuestionsExtractorModel.generateContent(
                    content {
                        image(pageBitmap)
                        text(prompt)
                    }
                )
            }
            val root = JSONObject(cleanJsonResponse(response.text.orEmpty()))
            val questions = root.optJSONArray("sorular")
                ?: throw IllegalStateException("Gemini sayfa yanıtında soru listesi bulunamadı.")
            val pageExamType = pageInfo.examType
            val pageSubject = pageInfo.subjectName
            val pageTopic = pageInfo.topicName
            val candidates = mutableListOf<PageQuestionCandidate>()
            for (index in 0 until questions.length()) {
                val question = questions.optJSONObject(index)
                if (question == null) {
                    aiStatus.pageFallbackRejectedQuestionCount++
                    continue
                }
                val boxJson = question.optJSONArray("box_2d")
                if (boxJson == null || boxJson.length() != 4) {
                    aiStatus.pageFallbackRejectedQuestionCount++
                    continue
                }
                val box = (0 until boxJson.length()).map { boxJson.optInt(it, -1) }
                if (box.any { it !in 0..1000 } ||
                    box[2] <= box[0] ||
                    box[3] <= box[1]
                ) {
                    aiStatus.pageFallbackRejectedQuestionCount++
                    continue
                }

                val subject = pageSubject
                val topic = question.optString("konu", pageTopic).ifBlank { pageTopic }
                val examType = pageExamType
                val difficulty = question.optString("zorluk", pageInfo.difficulty).ifBlank { pageInfo.difficulty }
                val cropBox = StudioImageAndPdfEngine.cropQuestionFromNormalizedBox(
                    context = context,
                    pageBitmap = pageBitmap,
                    box2d = box,
                    pageIndex = pageIndex,
                    questionOrder = index + 1,
                    difficulty = difficulty
                )
                if (cropBox == null) {
                    aiStatus.pageFallbackRejectedQuestionCount++
                    continue
                }
                val classification = buildQuestionOcrClassification(
                    questionText = question.optString("soruMetni", "").trim(),
                    examTypeHint = examType,
                    subjectNameHint = subject,
                    suggestedTopicName = topic,
                    trustSuggestedTopic = question.optString("konu", "").isNotBlank(),
                    hasDiagramOrGraph = false,
                    lineCountEstimate = 6,
                    visualDifficultyHint = difficulty,
                    usedLocalOcr = false
                )
                candidates.add(
                    PageQuestionCandidate(
                        cropBox = cropBox,
                        classification = classification,
                        answerKeyQuestionNumber = question.optInt("orijinalSoruNo", 0).takeIf { it > 0 }
                    )
                )
            }
            return candidates
        } catch (e: TimeoutCancellationException) {
            aiStatus.pageFallbackFailureCount++
            aiStatus.pageFallbackDisabled = true
            return emptyList()
        } catch (e: CancellationException) {
            throw e
        } catch (_: OutOfMemoryError) {
            aiStatus.pageFallbackFailureCount++
            aiStatus.pageFallbackDisabled = true
            return emptyList()
        } catch (e: Exception) {
            aiStatus.pageFallbackFailureCount++
            aiStatus.pageFallbackDisabled = true
            if (shouldDisableGeminiForPdf(e)) {
                aiStatus.unavailableReason = describeGeminiFailure(e)
            }
            return emptyList()
        }
    }

    private fun describeGeminiFailure(error: Exception): String {
        val details = "${error.javaClass.simpleName} ${error.message.orEmpty()}".lowercase()
        return when {
            listOf(
                "invalid api key",
                "api key not valid",
                "invalid token",
                "invalid credentials",
                "unauthenticated",
                "permission_denied",
                "app check",
                "401",
                "403"
            )
                .any(details::contains) ->
                "Firebase/Gemini doğrulaması başarısız; google-services.json, Generative Language API ve App Check yapılandırması kontrol edilmeli."
            listOf("quota", "429", "resource_exhausted", "rate limit").any(details::contains) ->
                "Gemini kullanım kotası dolmuş."
            listOf("timeout", "timed out", "unavailable", "network", "connection").any(details::contains) ->
                "Gemini servisine ağ üzerinden ulaşılamadı."
            else -> "Gemini OCR isteği başarısız oldu (${error.javaClass.simpleName})."
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

    /**
     * Automatic Answer Key OCR from the last page(s) of the PDF
     */
    suspend fun extractAnswerKeyFromBookPages(
        answerKeyBitmaps: List<Bitmap>,
        testNumberOrTitleHint: String = ""
    ): Map<Int, String> {
        if (answerKeyBitmaps.isEmpty()) return emptyMap()
        val combinedMap = mutableMapOf<Int, String>()
        val prompt = buildString {
            appendLine("Bu görsel bir YKS soru bankası veya deneme kitabının CEVAP ANAHTARI sayfasıdır.")
            if (testNumberOrTitleHint.isNotBlank()) {
                appendLine("Özellikle '$testNumberOrTitleHint' başlıklı testin cevap anahtarını oku.")
            }
            appendLine("Her soru numarasını (orijinalSoruNo: 1, 2, 3...) ve karşısındaki doğru seçeneği (correctOption: A, B, C, D veya E) eksiksiz ve %100 doğru olarak JSON formatında çıkar.")
        }

        for (bmp in answerKeyBitmaps) {
            try {
                val response = withTimeout(45_000L) {
                    answerKeyExtractorModel.generateContent(
                        content {
                            image(bmp)
                            text(prompt)
                        }
                    )
                }
                val rawJson = response.text.orEmpty()
                if (rawJson.isNotBlank()) {
                    val obj = JSONObject(rawJson)
                    val arr = obj.optJSONArray("answers")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val qNum = item.optInt("orijinalSoruNo", item.optInt("questionNumber", 0))
                            val opt = item.optString("correctOption", "").uppercase()
                            if (qNum > 0 && opt in listOf("A", "B", "C", "D", "E")) {
                                combinedMap[qNum] = opt
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return combinedMap
    }

    private fun cleanJsonResponse(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("```")) {
            val withoutFirstLine = trimmed.substringAfter("\n")
            return withoutFirstLine.substringBeforeLast("```").trim()
        }
        return trimmed
    }

    private val singleShotBookBlueprintSchema = Schema.obj(
        properties = mapOf(
            "kitapAdi" to Schema.string("PDF'ten tespit edilen kitap veya deneme adı"),
            "sinavTuru" to Schema.string("TYT veya AYT"),
            "ders" to Schema.string("Matematik, Geometri, Fizik, Kimya, Biyoloji veya Türkçe"),
            "sayfaHaritasi" to Schema.array(
                Schema.obj(
                    properties = mapOf(
                        "baslangicSayfa" to Schema.integer("1-tabanlı başlangıç sayfası"),
                        "bitisSayfa" to Schema.integer("1-tabanlı bitiş sayfası"),
                        "atla" to Schema.boolean("Kapak veya içindekiler sayfası ise true, soru testi ise false"),
                        "ders" to Schema.string("Matematik, Geometri, Fizik, Kimya, Biyoloji veya Türkçe"),
                        "konu" to Schema.string("YKS müfredat konusu (Örn: Fonksiyonlarda Uygulamalar, Polinomlar, Logaritma, Diziler, Türev, İntegral, Olasılık)"),
                        "zorluk" to Schema.string("5'li zorluk seviyesi: Kolay, Orta, ÖSYM, Zor veya Çok Zor")
                    ),
                    optionalProperties = listOf("atla")
                ),
                "Kitaptaki testlerin sayfa, ders, konu ve 5'li zorluk haritası"
            ),
            "cevapAnahtari" to Schema.array(
                Schema.obj(
                    mapOf(
                        "soruNo" to Schema.integer("Soru sıra numarası (1, 2, 3...)"),
                        "dogruSecenek" to Schema.string("A, B, C, D veya E")
                    )
                ),
                "Tespit edilen cevap anahtarı eşleşmeleri"
            )
        ),
        optionalProperties = listOf("sayfaHaritasi", "cevapAnahtari")
    )

    private val singleShotBookBlueprintModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = singleShotBookBlueprintSchema
            }
        )
    }

    /**
     * Builds a local PDF blueprint, optionally enriches it with Gemini, then crops questions locally.
     * Gemini OCR is preferred; if it is unavailable or cannot validate a crop, bundled ML Kit OCR
     * continues the import without inventing question text or answer keys.
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
            val aiStatus = PdfAiStatus()
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
                val bookId = "book_${Integer.toHexString("$resolvedDisplayName|$publisherName".hashCode())}"
                val archivedPdfPath = withContext(Dispatchers.IO) {
                    StudioImageAndPdfEngine.persistBookPdf(context, seekablePdfFile, bookId)
                }

                _batchProgressPercent.value = 8
                _batchStatusText.value = "Sayfa 1 / $totalPdfPages işleniyor • Kitap haritası ve cevap anahtarı analiz ediliyor..."

                // STEP 1: Fast local structural text + Regex Answer Key + YKS Curriculum Dictionary blueprint
                val localBlueprint = withContext(Dispatchers.IO) {
                    StudioImageAndPdfEngine.extractLocalPdfBlueprint(
                        pdfFile = seekablePdfFile,
                        totalPages = totalPdfPages,
                        fileNameHint = resolvedDisplayName,
                        publisherName = publisherName
                    )
                }

                val mergedAnswerKey = localBlueprint.answerKeyMap.toMutableMap()
                val mergedPageMap = localBlueprint.pageMap.toMutableMap()
                var finalBookTitle = localBlueprint.detectedBookTitle
                var defaultExamType = localBlueprint.defaultExamType
                var lockedBookSubject = StudioImageAndPdfEngine.detectSubjectNameFromText(
                    "$resolvedDisplayName $publisherName"
                ) ?: localBlueprint.defaultSubjectName.takeIf(String::isNotBlank)
                var defaultSubject = lockedBookSubject ?: localBlueprint.defaultSubjectName
                val explicitlyTytBook = Regex("""(?i)(?:^|[^A-Z])TYT(?:$|[^A-Z])""")
                    .containsMatchIn(
                        "$resolvedDisplayName $publisherName ${localBlueprint.extractedTextSampleForAi}"
                    )
                if (lockedBookSubject.equals("Biyoloji", ignoreCase = true) && !explicitlyTytBook) {
                    defaultExamType = "AYT"
                }

                // STEP 2: Single-Shot (1 Request) AI Book & Answer Key Analysis (Quota-Safe: never fails on Quota Exceeded!)
                _batchProgressPercent.value = 14
                try {
                    val catalogSummary = SoruLabCatalog.allSubjects.joinToString("\n") { subj ->
                        "${subj.examType.name} ${subj.name}: ${subj.topics.joinToString(", ") { it.name }}"
                    }
                    val singleShotPrompt = buildString {
                        appendLine("Sen bir YKS kitap ve cevap anahtarı yapılandırma uzmanısın.")
                        appendLine("Yayınevi: $publisherName")
                        appendLine("Dosya Adı: $resolvedDisplayName")
                        appendLine("Toplam Sayfa Sayısı: $totalPdfPages")
                        if (localBlueprint.extractedTextSampleForAi.isNotBlank()) {
                            appendLine("PDF'ten Çıkarılan Başlık ve Cevap Anahtarı Metni:")
                            appendLine(localBlueprint.extractedTextSampleForAi.take(3500))
                        }
                        appendLine("YKS Müfredat Sözlüğü:")
                        appendLine(catalogSummary)
                        appendLine("KESİN KURAL 1: Dosya adında veya metinde 'AYT', 'İleri Matematik', 'Fonksiyonlarda Uygulama', 'Türev', 'İntegral', 'Limit', 'Trigonometri', 'Logaritma', 'Diziler', 'Parabol', 'Polinom', 'Bağıl Hareket', 'Atışlar', 'Momentum', 'Tork' geçiyorsa sinavTuru KESİNLİKLE 'AYT' olmalıdır (Asla TYT yazma!).")
                        appendLine("KESİN KURAL 2: Zorluk seviyelerini 5'li yapıda sınıflandır: 'Kolay', 'Orta', 'ÖSYM' (ÖSYM Tadında/Tarzı/Birebir ÖSYM), 'Zor', 'Çok Zor' (Tümevarım/Barışça/Orijinal/Derece).")
                        appendLine("Bu kaynağın kitap adını (`kitapAdi`), sınav türünü (`sinavTuru`), dersini (`ders`), sayfa haritasını (`sayfaHaritasi`) ve cevap anahtarını (`cevapAnahtari`) JSON olarak çıkar.")
                    }

                    val tailBitmaps = withContext(Dispatchers.IO) {
                        StudioImageAndPdfEngine.extractLastPagesForAnswerKeyOcr(
                            context = context,
                            pdfUri = pdfUri,
                            maxTailPages = 1
                        )
                    }

                    val aiResponse = try {
                        withTimeout(18_000L) {
                            val tailBmp = tailBitmaps.firstOrNull()
                            if (tailBmp != null) {
                                singleShotBookBlueprintModel.generateContent(
                                    content {
                                        image(tailBmp)
                                        text(singleShotPrompt)
                                    }
                                )
                            } else {
                                singleShotBookBlueprintModel.generateContent(singleShotPrompt)
                            }
                        }
                    } finally {
                        tailBitmaps.forEach { bitmap ->
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                    }

                    val rawJson = cleanJsonResponse(aiResponse.text.orEmpty())
                    if (rawJson.isNotBlank()) {
                        val obj = JSONObject(rawJson)
                        val aiTitle = obj.optString("kitapAdi", "").trim()
                        if (aiTitle.length >= 4) {
                            finalBookTitle = aiTitle
                        }
                        val aiExam = obj.optString("sinavTuru", "").uppercase()
                        // Never downgrade a locally verified AYT book to TYT!
                        if (defaultExamType == "AYT" || aiExam.contains("AYT") || finalBookTitle.contains("AYT", ignoreCase = true)) {
                            defaultExamType = "AYT"
                        } else if (aiExam.contains("TYT")) {
                            defaultExamType = "TYT"
                        }

                        val aiDers = obj.optString("ders", "")
                            .replace("TYT ", "", ignoreCase = true)
                            .replace("AYT ", "", ignoreCase = true)
                            .trim()
                        if (lockedBookSubject != null) {
                            defaultSubject = lockedBookSubject
                        } else if (aiDers.isNotBlank()) {
                            defaultSubject = aiDers
                        }

                        val keyArr = obj.optJSONArray("cevapAnahtari")
                        if (keyArr != null) {
                            for (i in 0 until keyArr.length()) {
                                val kObj = keyArr.optJSONObject(i) ?: continue
                                val sn = kObj.optInt("soruNo", 0)
                                val opt = kObj.optString("dogruSecenek", "").uppercase().trim()
                                if (sn > 0 && opt in listOf("A", "B", "C", "D", "E")) {
                                    mergedAnswerKey[sn] = opt
                                }
                            }
                        }

                        val mapArr = obj.optJSONArray("sayfaHaritasi")
                        if (mapArr != null && (mapArr.length() >= 4 || totalPdfPages <= 18)) {
                            for (i in 0 until mapArr.length()) {
                                val mObj = mapArr.optJSONObject(i) ?: continue
                                val startP = (mObj.optInt("baslangicSayfa", 1) - 1).coerceIn(0, totalPdfPages - 1)
                                val endP = (mObj.optInt("bitisSayfa", startP + 1) - 1).coerceIn(startP, totalPdfPages - 1)
                                if (totalPdfPages > 24 && (endP - startP) > (totalPdfPages / 2)) continue
                                val mDers = (lockedBookSubject ?: mObj.optString("ders", defaultSubject))
                                    .replace("TYT ", "", ignoreCase = true)
                                    .replace("AYT ", "", ignoreCase = true)
                                    .trim()
                                    .ifBlank { defaultSubject }
                                val mKonuRaw = mObj.optString("konu", "").trim()
                                val matchedTopicForRange = if (mKonuRaw.isNotBlank()) {
                                    SoruLabCatalog.matchBestTopic(
                                        examTypeHint = defaultExamType,
                                        subjectNameHint = mDers,
                                        topicOrQuestionText = mKonuRaw
                                    )
                                } else {
                                    null
                                }
                                val mZorluk = SoruLabCatalog.classifyDifficulty5FromText(
                                    rawDifficultyHint = mObj.optString("zorluk", ""),
                                    headerOrQuestionText = "$mKonuRaw $finalBookTitle",
                                    questionIndexInPage = i
                                ).label

                                for (p in startP..endP) {
                                    mergedPageMap[p] = StudioImageAndPdfEngine.LocalPageMapEntry(
                                        pageIndex = p,
                                        isSkipPage = false,
                                        examType = defaultExamType,
                                        subjectName = mDers,
                                        topicName = matchedTopicForRange?.second?.name.orEmpty(),
                                        difficulty = mZorluk
                                    )
                                }
                            }
                        }
                    } else {
                        aiStatus.bookBlueprintWarning =
                            "Gemini kitap haritası yanıtı boş döndü; soru başına Gemini analizi sürdürülecek."
                    }
                } catch (e: TimeoutCancellationException) {
                    aiStatus.bookBlueprintWarning =
                        "Gemini kitap haritası zaman aşımına uğradı; soru başına Gemini analizi sürdürülecek."
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (shouldDisableGeminiForPdf(e)) {
                        aiStatus.unavailableReason = describeGeminiFailure(e)
                    } else {
                        aiStatus.bookBlueprintWarning =
                            "${describeGeminiFailure(e)} Soru başına Gemini analizi yine denenecek."
                    }
                }
                aiStatus.unavailableReason?.let {
                    _batchStatusText.value = "$it Gemini kullanılamadığı için yerel yedek analiz devrede."
                }

                // STEP 3: 100% Free & Unlimited Local Dual-Column Vector & Pixel Projection Cropping Loop
                val resolvedPub = SoruLabPublisherPresets.resolvePublisher(publisherName, customPublishers)
                val finalPubName = publisherName.ifBlank { resolvedPub.name }
                val finalLogoUri = publisherLogoUri.ifBlank { resolvedPub.customLogoPath }

                val allExtractedQuestions = mutableListOf<ExtractedPdfQuestionItem>()
                var localOcrCount = 0
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

                            for ((scanPos, pageIdx) in pageIndicesToScan.withIndex()) {
                                if (pageIdx >= rendererTotal) continue
                                val pageInfo = mergedPageMap[pageIdx] ?: StudioImageAndPdfEngine.LocalPageMapEntry(
                                    pageIndex = pageIdx,
                                    examType = defaultExamType,
                                    subjectName = defaultSubject,
                                    topicName = "",
                                    difficulty = "Orta"
                                )

                                val nowMs = System.currentTimeMillis()
                                if (nowMs - lastProgressUpdateMs >= 110L || scanPos == 0 || scanPos == totalPdfPages - 1) {
                                    lastProgressUpdateMs = nowMs
                                    val progressPct = 16 + (((scanPos + 1).toFloat() / totalPdfPages.coerceAtLeast(1).toFloat()) * 82f).toInt()
                                    _batchProgressPercent.value = progressPct.coerceIn(16, 98)
                                    val analysisLabel = if (aiStatus.unavailableReason == null) {
                                        "Gemini soru analizi • ML Kit konumlama"
                                    } else {
                                        "Yerel ML Kit yedek analizi • Gemini kullanılamıyor"
                                    }
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
                                    aiStatus.localOcrFailureCount++
                                    PdfPageOcrLayout()
                                } catch (_: Exception) {
                                    aiStatus.localOcrFailureCount++
                                    PdfPageOcrLayout()
                                }
                                StudioImageAndPdfEngine.parseAnswerKeyRegex(pageLayout.footerText)
                                    .forEach { (number, option) ->
                                        mergedAnswerKey.putIfAbsent(number, option)
                                    }
                                if (StudioImageAndPdfEngine.shouldSkipQuestionPage(
                                        text = pageLayout.text,
                                        topHeaderText = pageLayout.headerText,
                                        pageIndex = pageIdx,
                                        totalPages = totalPdfPages
                                    )
                                ) {
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
                                val detectedTopic = StudioImageAndPdfEngine.detectTopicNameFromText(
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

                                val croppedBoxes = try {
                                    StudioImageAndPdfEngine.cropRenderedPageLocalDualColumn(
                                        context = context,
                                        pageBitmap = pageBitmap,
                                        pageIndex = pageIdx,
                                        totalPages = rendererTotal,
                                        reusablePixelsRef = reusablePixelsRef,
                                        questionNumberAnchors = pageLayout.questionNumberAnchors,
                                        optionAnchors = pageLayout.optionAnchors
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
                                    extractQuestionsFromPageWithGemini(
                                        context = context,
                                        pageBitmap = pageBitmap,
                                        pageIndex = pageIdx,
                                        pageInfo = scanPageInfo,
                                        aiStatus = aiStatus
                                    )
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
                                                        aiStatus = aiStatus
                                                    )
                                                }
                                            }
                                        }.awaitAll()
                                    }
                                    croppedBoxes.mapIndexed { index, cropBox ->
                                        val classification = classifications[index]
                                        PageQuestionCandidate(
                                            cropBox = cropBox,
                                            classification = classification,
                                            answerKeyQuestionNumber = classification.answerKeyQuestionNumber
                                                ?: cropBox.questionOrderInPage.takeIf { it > 0 }
                                        )
                                    }
                                }

                                for ((boxIdx, candidate) in pageCandidates.withIndex()) {
                                    val cropBox = candidate.cropBox
                                    val classification = candidate.classification
                                    if (classification.usedLocalOcr) localOcrCount++

                                    val globalQuestionNum = allExtractedQuestions.size + 1
                                    val verifiedAnswer = resolveAnswerKeyOption(
                                        questionNumber = candidate.answerKeyQuestionNumber,
                                        answerKey = mergedAnswerKey
                                    )

                                    allExtractedQuestions.add(
                                        ExtractedPdfQuestionItem(
                                            id = "local_q_${System.currentTimeMillis()}_p${pageIdx + 1}_${boxIdx + 1}_$globalQuestionNum",
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
                                            questionNumberInTest = candidate.answerKeyQuestionNumber
                                                ?: cropBox.questionOrderInPage
                                        )
                                    )
                                }

                                kotlinx.coroutines.yield()
                            }
                        }
                    }
                }

                if (allExtractedQuestions.isEmpty()) {
                    val aiHint = aiStatus.unavailableReason?.let { " $it" }.orEmpty()
                    val pageHint = buildString {
                        if (pageRenderFailureCount > 0) {
                            append(" $pageRenderFailureCount sayfa işlenirken hata oluştu.")
                        }
                        if (aiStatus.pageFallbackFailureCount > 0) {
                            append(" Gemini sayfa taraması yanıt vermedi.")
                        }
                        if (aiStatus.pageFallbackRejectedQuestionCount > 0) {
                            append(" ${aiStatus.pageFallbackRejectedQuestionCount} Gemini kırpma kutusu güvenli sınırları geçemedi.")
                        }
                        if (aiStatus.localOcrFailureCount > 0) {
                            append(" Cihaz içi OCR ${aiStatus.localOcrFailureCount} soruda tamamlanamadı.")
                        }
                    }
                    _batchErrorText.value = "PDF'te aktarılabilir soru bloğu bulunamadı.$pageHint$aiHint Kırpma alanını ve PDF kalitesini kontrol edip yeniden deneyin."
                    completeOnce(emptyList(), coverPath, finalBookTitle)
                    return@launch
                }

                val warnings = buildList {
                    aiStatus.bookBlueprintWarning?.let { add(it) }
                    aiStatus.unavailableReason?.let {
                        add("$it Kitap aktarımı yerel ML Kit yedek analiziyle sürdürüldü.")
                    }
                    if (localOcrCount > 0 && aiStatus.unavailableReason == null) {
                        add("$localOcrCount soruda Gemini soru analizi başarısız oldu; bu sorular yerel ML Kit OCR ile okundu.")
                    }
                    if (aiStatus.localOcrFailureCount > 0) {
                        add("Cihaz içi OCR ${aiStatus.localOcrFailureCount} soruda tamamlanamadı; kırpılmış görseller yine aktarıldı.")
                    }
                    if (pageRenderFailureCount > 0) {
                        add("$pageRenderFailureCount PDF sayfası işlenemedi; diğer sayfalar aktarıldı.")
                    }
                    if (aiStatus.pageFallbackFailureCount > 0) {
                        add("Gemini sayfa taraması ${aiStatus.pageFallbackFailureCount} sayfada tamamlanamadı.")
                    }
                    if (aiStatus.pageFallbackRejectedQuestionCount > 0) {
                        add("${aiStatus.pageFallbackRejectedQuestionCount} Gemini kırpma kutusu güvenli sınırları geçemedi.")
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
