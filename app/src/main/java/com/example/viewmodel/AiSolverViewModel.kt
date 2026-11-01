package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.example.util.StudioImageAndPdfEngine
import com.example.util.QuestionNumberAnchor
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.Text
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
import java.io.File
import java.io.FileOutputStream

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
    val correctOption: String = "D",
    val questionNumber: Int = 1,
    val answerKeyVerified: Boolean = true,
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
    val boxNormalized: List<Float> = listOf(0.05f, 0.05f, 0.95f, 0.48f)
)

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

private suspend fun recognizePdfPage(bitmap: Bitmap, recognizer: TextRecognizer): Text =
    recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()

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

    fun askAboutQuestion(question: YksQuestion, customPrompt: String = "") {
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
                if (question.croppedImagePath.isNotBlank() && File(question.croppedImagePath).exists()) {
                    runCatching { BitmapFactory.decodeFile(question.croppedImagePath) }.getOrNull()
                } else {
                    null
                }
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
        onResult: (String) -> Unit
    ) {
        viewModelScope.launch {
            val questionBmp = withContext(Dispatchers.IO) {
                if (question.croppedImagePath.isNotBlank() && File(question.croppedImagePath).exists()) {
                    runCatching { BitmapFactory.decodeFile(question.croppedImagePath) }.getOrNull()
                } else {
                    null
                }
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
        onResult: (TwinQuestionModalData) -> Unit
    ) {
        viewModelScope.launch {
            val questionBmp = withContext(Dispatchers.IO) {
                if (question.imagePath.isNotBlank() && File(question.imagePath).exists()) {
                    runCatching { BitmapFactory.decodeFile(question.imagePath) }.getOrNull()
                } else {
                    null
                }
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
                val matchedTopic = SoruLabCatalog.allSubjects
                    .asSequence()
                    .flatMap { subject -> subject.topics.asSequence().map { subject to it } }
                    .firstOrNull { (_, topic) ->
                        topic.name.equals(question.topicHeader, ignoreCase = true)
                    }
                val subjectName = matchedTopic?.first?.name
                    ?: StudioImageAndPdfEngine.detectSubjectNameFromText(
                        "${question.topicHeader}\n${question.stemTop}\n${question.bulletsOrSteps.joinToString(" ")}"
                    ).orEmpty()
                val subjectTopics = SoruLabCatalog.allSubjects
                    .firstOrNull { it.name.equals(subjectName, ignoreCase = true) }
                    ?.topics
                    ?.joinToString(", ") { it.name }
                    .orEmpty()
                val prompt = buildString {
                    appendLine("Sen uzman bir ÖSYM YKS soru yazarısın.")
                    appendLine("Görev: Görseldeki soruyu önce tamamen çözümle; sonra aynı kazanımı ölçen, aynı zorlukta ve aynı temel çözüm yöntemini gerektiren yeni bir ikiz soru yaz.")
                    appendLine("Yeni soruda yalnızca sayıları/nesneleri rastgele değiştirme. Verilenler ve şıklar yeni soruya göre tutarlı olsun; öğrenci aynı beceriyi yeni veriler üzerinde uygulamak zorunda kalsın.")
                    appendLine("Soru kökünü ve şıkları kopyalama. En az bir önemli niceliği veya koşulu değiştir; yeni sorunun cevabını sıfırdan hesapla. Orijinal sorunun doğru şıkkını yeni sorunun cevabı olarak varsayma.")
                    appendLine("Sayısal sorularda önce tam sayı ve temiz sonuç veren yeni parametreleri seç; sonra soru ve çeldiricileri bu parametrelerden türet. Kesirli sonuç gerekiyorsa bunu kaynak sorunun kazanımı gerektiriyorsa kullan.")
                    appendLine("Önce şu iç kontrolleri yap, çıktıya yalnızca son sonucu yaz: kazanım aynı mı, çözüm yolu ve zorluk paralel mi, verilenler yeterli mi, tam olarak bir doğru şık var mı, çözüm hesapla tutarlı mı?")
                    appendLine("A-E şıklarının beşi de dolu ve birbirinden farklı olmalı. Çeldiriciler yaygın işlem/kavram hatalarına dayansın; doğru cevap ve çözüm adımları birbiriyle uyuşsun.")
                    appendLine("Orijinal soru şekil, grafik veya tablo gerektiriyorsa yeni soru için gereken tüm bilgileri soru metninde açıkça tarif et; var olmayan bir görsele atıf yapma.")
                    appendLine("Matematiksel gösterimi ve Türkçe karakterleri koru. Yeni soru bağımsız, anlaşılır ve YKS sınav dilinde olsun.")
                    appendLine("Ders: ${subjectName.ifBlank { "Kaynak sorunun dersini görsel ve kazanımından belirle" }}")
                    appendLine("Konu bağlamı: ${question.topicHeader}")
                    if (subjectTopics.isNotBlank()) {
                        appendLine("Bu dersin gerçek kazanım başlıkları: $subjectTopics")
                        appendLine("Yalnızca bu dersin konu alanında kal; başka dersten, özellikle Matematikten konu ekleme.")
                    }
                    appendLine("Zorluk seviyesi: ${question.difficulty.label}")
                    appendLine("Orijinal soru kökü: ${question.stemTop}")
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
            val decoded = BitmapFactory.decodeFile(imagePath) ?: return@withContext null
            if (decoded.isMutable) {
                decoded
            } else {
                decoded.copy(Bitmap.Config.ARGB_8888, true).also { decoded.recycle() }
            }
        } ?: throw IllegalStateException("Kırpılan soru görseli OCR için açılamadı.")

        try {
            if (aiStatus.unavailableReason == null) {
                try {
                    val normalizedSubject = subjectNameHint
                        .replace("TYT ", "", ignoreCase = true)
                        .replace("AYT ", "", ignoreCase = true)
                        .trim()
                    val catalogTopics = SoruLabCatalog.allSubjects
                        .filter {
                            it.examType.name.equals(examTypeHint, ignoreCase = true) &&
                                (normalizedSubject.isBlank() || it.name.equals(normalizedSubject, ignoreCase = true))
                        }
                        .joinToString("\n") { subject ->
                            "${subject.name}: ${subject.topics.joinToString(", ") { it.name }}"
                        }
                    val prompt = buildString {
                        appendLine("Ekli kırpılmış görseldeki tek YKS sorusunu OCR ile eksiksiz oku.")
                        appendLine("Matematiksel sembolleri, üsleri, kesirleri, şekil/tablo açıklamalarını ve A), B), C), D), E) şıklarının tamamını koru.")
                        appendLine("Görünmeyen metni veya şıkları uydurma; kısmi OCR olsa da doğrulanmış kırpılmış soruyu aktar.")
                        appendLine("Görselde basılı soru numarası açıkça görünüyorsa originalQuestionNumber alanına yaz; yoksa tahmin etme.")
                        appendLine("Ders ipucu verilmişse onu kesinlikle koru. Ders ve konu adını yalnızca aşağıdaki ders müfredatından seç; konu adını aynen yaz.")
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
                        subjectNameHint = subjectNameHint.ifBlank {
                            obj.optString("subjectName", "").trim()
                        },
                        suggestedTopicName = obj.optString("topicName", "").trim().ifBlank { pageTopicHint },
                        trustSuggestedTopic = obj.optString("topicName", "").isNotBlank(),
                        hasQuestionStemOrFigure = obj.optBoolean("hasQuestionStemOrFigure"),
                        explicitDifficultyHint = obj.optString("difficulty", "").trim(),
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
                } catch (e: Exception) {
                    if (shouldDisableGeminiForPdf(e) || aiStatus.shouldDisableAfterRepeatedFailure()) {
                        aiStatus.unavailableReason = describeGeminiFailure(e)
                    }
                }
            }

            val localText = try {
                recognizeAndCleanLeadingQuestionNumber(questionBitmap, imagePath)
            } catch (e: CancellationException) {
                throw e
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
                hasQuestionStemOrFigure = false,
                explicitDifficultyHint = "",
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
        } finally {
            questionBitmap.recycle()
        }
    }

    private suspend fun recognizeAndCleanLeadingQuestionNumber(bitmap: Bitmap, imagePath: String): String =
        withContext(Dispatchers.IO) {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val recognizedText = try {
                recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            } finally {
                recognizer.close()
            }

            if (removeLeadingPrintedQuestionNumber(bitmap, recognizedText)) {
                FileOutputStream(imagePath).use { output ->
                    val format = if (imagePath.endsWith(".webp", ignoreCase = true)) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSY
                        } else {
                            @Suppress("DEPRECATION")
                            Bitmap.CompressFormat.WEBP
                        }
                    } else if (imagePath.endsWith(".jpg", ignoreCase = true) ||
                        imagePath.endsWith(".jpeg", ignoreCase = true)
                    ) {
                        Bitmap.CompressFormat.JPEG
                    } else {
                        Bitmap.CompressFormat.PNG
                    }
                    val quality = if (format == Bitmap.CompressFormat.JPEG) 85 else 92
                    if (!bitmap.compress(format, quality, output)) {
                        throw IllegalStateException("Soru görseli OCR sonrasında kaydedilemedi.")
                    }
                }
                StudioImageAndPdfEngine.invalidateCachedBitmap(imagePath)
            }

            recognizedText.text
        }

    private fun removeLeadingPrintedQuestionNumber(
        bitmap: Bitmap,
        recognizedText: com.google.mlkit.vision.text.Text
    ): Boolean {
        val firstLine = recognizedText.textBlocks
            .flatMap { it.lines }
            .minByOrNull { it.boundingBox?.top ?: Int.MAX_VALUE }
            ?: return false
        val marker = firstLine.elements.firstOrNull() ?: return false
        if (!Regex("""^\d{1,3}[.)]?$""").matches(marker.text.trim())) return false

        val markerBounds = marker.boundingBox ?: return false
        if (markerBounds.left > bitmap.width * 0.14f ||
            markerBounds.top > bitmap.height * 0.20f ||
            markerBounds.width() > bitmap.width * 0.12f ||
            markerBounds.height() > bitmap.height * 0.08f
        ) {
            return false
        }

        val nextBounds = firstLine.elements.getOrNull(1)?.boundingBox
        if (nextBounds != null && nextBounds.left - markerBounds.right < (bitmap.width * 0.006f).toInt().coerceAtLeast(3)) {
            return false
        }

        val padding = (bitmap.width * 0.008f).toInt().coerceAtLeast(2)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL
        }
        android.graphics.Canvas(bitmap).drawRect(
            (markerBounds.left - padding).coerceAtLeast(0).toFloat(),
            (markerBounds.top - padding).coerceAtLeast(0).toFloat(),
            (markerBounds.right + padding).coerceAtMost(bitmap.width).toFloat(),
            (markerBounds.bottom + padding).coerceAtMost(bitmap.height).toFloat(),
            paint
        )
        return true
    }

    private fun buildQuestionOcrClassification(
        questionText: String,
        examTypeHint: String,
        subjectNameHint: String,
        suggestedTopicName: String,
        trustSuggestedTopic: Boolean,
        hasQuestionStemOrFigure: Boolean,
        explicitDifficultyHint: String,
        hasDiagramOrGraph: Boolean,
        lineCountEstimate: Int,
        visualDifficultyHint: String,
        usedLocalOcr: Boolean
    ): QuestionOcrClassification {
        val retainedQuestionText = questionText.trim()

        val resolvedSubjectHint = subjectNameHint.ifBlank {
            StudioImageAndPdfEngine.detectSubjectNameFromText(questionText).orEmpty()
        }
        val matchedSubject = SoruLabCatalog.allSubjects.firstOrNull {
            it.examType.name.equals(examTypeHint, ignoreCase = true) &&
                it.name.equals(
                    resolvedSubjectHint.replace("TYT ", "", ignoreCase = true)
                        .replace("AYT ", "", ignoreCase = true).trim(),
                    ignoreCase = true
                )
        }
        val explicitTopicName = StudioImageAndPdfEngine.detectTopicNameFromText(
            examType = examTypeHint,
            subjectName = resolvedSubjectHint,
            text = "$suggestedTopicName\n$questionText"
        )
        val topicClassification = explicitTopicName?.let { topicName ->
            matchedSubject?.let { subject ->
                subject.topics.firstOrNull { it.name.equals(topicName, ignoreCase = true) }
                    ?.let { topic -> subject to topic }
            }
        } ?: matchedSubject?.let { subject ->
            SoruLabCatalog.classifyQuestionTopicFromText(
                examTypeHint = examTypeHint,
                subjectNameHint = subject.name,
                questionText = questionText,
                suggestedTopicName = suggestedTopicName,
                trustSuggestedTopic = trustSuggestedTopic
            )?.takeUnless {
                it.second.name.equals("İnceleme Bekliyor", ignoreCase = true)
            } ?: SoruLabCatalog.classifyQuestionTopicFromText(
                examTypeHint = examTypeHint,
                subjectNameHint = subject.name,
                questionText = questionText
            )?.takeUnless {
                it.second.name.equals("İnceleme Bekliyor", ignoreCase = true)
            }
        }
        val normalizedSubject = matchedSubject?.name.orEmpty()

        val difficultySignal = buildString {
            append(questionText)
            if (hasDiagramOrGraph) appendLine("\nşekil grafik")
            repeat(lineCountEstimate.coerceAtMost(30)) { appendLine("x") }
        }
        val textDifficulty = SoruLabCatalog.classifyDifficulty5FromText(
            rawDifficultyHint = "",
            headerOrQuestionText = difficultySignal
        )
        val visualDifficulty = DifficultyLevel.fromLabel(visualDifficultyHint)
        val explicitDifficulty = explicitDifficultyHint
            .takeIf { hint ->
                val normalized = hint.lowercase()
                listOf("kolay", "orta", "ösym", "osym", "zor", "başlangıç", "baslangic", "derece")
                    .any(normalized::contains)
            }
            ?.let(DifficultyLevel::fromLabel)
        val combinedDifficultyWeight = if (explicitDifficulty != null && !usedLocalOcr) {
            (explicitDifficulty.weight * 0.75f) +
                (textDifficulty.weight * 0.10f) +
                (visualDifficulty.weight * 0.15f)
        } else {
            (textDifficulty.weight + visualDifficulty.weight) / 2f
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
            questionText = retainedQuestionText,
            examType = topicClassification?.first?.examType?.name ?: examTypeHint,
            subjectName = topicClassification?.first?.name ?: normalizedSubject,
            topicName = topicClassification?.second?.name.orEmpty(),
            difficulty = difficulty.label,
            targetLevel = targetLevel,
            usedLocalOcr = usedLocalOcr
        )
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
                .filter {
                    it.examType.name.equals(pageInfo.examType, ignoreCase = true) &&
                        (pageInfo.subjectName.isBlank() ||
                            it.name.equals(pageInfo.subjectName, ignoreCase = true))
                }
                .joinToString("\n") { subject ->
                    "${subject.name}: ${subject.topics.joinToString(", ") { it.name }}"
                }
            val prompt = buildString {
                appendLine("Bu PDF sayfasındaki tüm soru bloklarını tek tek bul ve JSON şemasına göre çıkar.")
                appendLine("Her soru için koordinatları [ymin, xmin, ymax, xmax] olarak 0-1000 aralığında ver; soru kökü, varsa şekil ve görünen şıkları tamamen kapsa.")
                appendLine("Soru numarasını PDF'te basılı olan numaradan al. Okunmayan soru metnini veya cevabı uydurma.")
                appendLine("Her kırpma için soru köküyle birlikte A), B), C), D) ve E) şıklarının tamamı görünür olmalı; eksik şıklı kutu üretme.")
                appendLine("Sayfa içindekiler, önsöz, İstiklal Marşı, künye, cevap anahtarı veya ünite sayfasıysa sorular listesi boş olsun.")
                appendLine("Sayfa bağlamında ders verilmişse tüm sorularda bu dersi koru; başka ders etiketi kullanma.")
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
            val pageExamType = root.optString("sinavTuru", pageInfo.examType).ifBlank { pageInfo.examType }
            val pageSubject = pageInfo.subjectName.ifBlank {
                root.optString("ders", "").trim()
            }
            val pageTopic = root.optString("konu", pageInfo.topicName).ifBlank { pageInfo.topicName }
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

                val subject = pageSubject.ifBlank { question.optString("ders", "").trim() }
                val topic = question.optString("konu", pageTopic).ifBlank { pageTopic }
                val examType = question.optString("sinavTuru", pageExamType).ifBlank { pageExamType }
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
                    hasQuestionStemOrFigure = question.optBoolean("hasQuestionStemOrFigure"),
                    explicitDifficultyHint = difficulty,
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
        } catch (e: Exception) {
            aiStatus.pageFallbackFailureCount++
            aiStatus.pageFallbackDisabled = true
            if (shouldDisableGeminiForPdf(e)) {
                aiStatus.unavailableReason = describeGeminiFailure(e)
            }
            return emptyList()
        }
    }

    private fun fallbackClassificationForCrop(
        questionText: String,
        examType: String,
        subjectName: String,
        topicName: String,
        difficulty: String
    ): QuestionOcrClassification {
        val normalizedDifficulty = DifficultyLevel.fromLabel(difficulty)
        val targetLevel = when (normalizedDifficulty) {
            DifficultyLevel.KOLAY -> "BASLANGIC"
            DifficultyLevel.ORTA -> "PEKISTIRME"
            DifficultyLevel.OSYM -> "OSYM"
            DifficultyLevel.ZOR -> "GELISIYORUM"
            DifficultyLevel.COK_ZOR -> "DERECE"
        }
        val validSubject = SoruLabCatalog.allSubjects.firstOrNull {
            it.examType.name.equals(examType, ignoreCase = true) &&
                it.name.equals(
                    subjectName.replace("TYT ", "", ignoreCase = true)
                        .replace("AYT ", "", ignoreCase = true).trim(),
                    ignoreCase = true
                )
        }
        val validTopic = validSubject?.topics?.firstOrNull {
            it.name.equals(topicName, ignoreCase = true)
        }
        return QuestionOcrClassification(
            questionText = questionText.trim(),
            examType = validSubject?.examType?.name ?: examType,
            subjectName = validSubject?.name.orEmpty(),
            topicName = validTopic?.name.orEmpty(),
            difficulty = normalizedDifficulty.label,
            targetLevel = targetLevel,
            usedLocalOcr = true
        )
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
                        "ders" to Schema.string("Matematik, Geometri, Fizik, Kimya, Biyoloji veya Türkçe"),
                        "konu" to Schema.string("YKS müfredat konusu (Örn: Fonksiyonlarda Uygulamalar, Polinomlar, Logaritma, Diziler, Türev, İntegral, Olasılık)"),
                        "zorluk" to Schema.string("5'li zorluk seviyesi: Kolay, Orta, ÖSYM, Zor veya Çok Zor")
                    ),
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
            var pageTextRecognizer: TextRecognizer? = null
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
                var lockedBookSubject = localBlueprint.defaultSubjectName
                var defaultSubject = lockedBookSubject

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
                        appendLine("Ders tespitinde önce dosya adı ve yayınevi, sonra kapak ve soru içeriği esas alınır; kitap biyoloji ise matematik terimleriyle dersini değiştirme.")
                        appendLine("KESİN KURAL 1: Dosya adında veya metinde AYT ya da TYT açıkça geçiyorsa onu kullan. İleri düzey müfredat kazanımları AYT olabilir; biyoloji ve diğer dersleri matematik olarak etiketleme.")
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
                        if (lockedBookSubject.isBlank()) {
                            lockedBookSubject = StudioImageAndPdfEngine
                                .detectSubjectNameFromText("$finalBookTitle\n$publisherName")
                                .orEmpty()
                        }
                        if (lockedBookSubject.isNotBlank()) {
                            defaultSubject = lockedBookSubject
                        }
                        val aiExam = obj.optString("sinavTuru", "").uppercase()
                        // Never downgrade a locally verified AYT book to TYT!
                        if (localBlueprint.defaultExamType == "AYT" || aiExam.contains("AYT") || finalBookTitle.contains("AYT", ignoreCase = true)) {
                            defaultExamType = "AYT"
                        } else if (aiExam.contains("TYT")) {
                            defaultExamType = "TYT"
                        }

                        val aiDers = obj.optString("ders", "")
                            .replace("TYT ", "", ignoreCase = true)
                            .replace("AYT ", "", ignoreCase = true)
                            .trim()
                        val catalogSubject = SoruLabCatalog.allSubjects.firstOrNull {
                            it.examType.name.equals(defaultExamType, ignoreCase = true) &&
                                it.name.equals(aiDers, ignoreCase = true)
                        }
                        if (lockedBookSubject.isBlank() && catalogSubject != null) {
                            defaultSubject = catalogSubject.name
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
                                val requestedMapSubject = (lockedBookSubject.ifBlank {
                                    mObj.optString("ders", defaultSubject)
                                })
                                    .replace("TYT ", "", ignoreCase = true)
                                    .replace("AYT ", "", ignoreCase = true)
                                    .trim()
                                    .ifBlank { defaultSubject }
                                val resolvedMapSubject = SoruLabCatalog.allSubjects.firstOrNull {
                                    it.examType.name.equals(defaultExamType, ignoreCase = true) &&
                                        it.name.equals(requestedMapSubject, ignoreCase = true)
                                } ?: continue
                                val mDers = resolvedMapSubject.name
                                val mKonuRaw = mObj.optString("konu", localBlueprint.defaultTopicName)
                                    .trim()
                                    .ifBlank { localBlueprint.defaultTopicName }
                                val (_, matchedTopicForRange) = SoruLabCatalog.matchBestTopic(
                                    examTypeHint = defaultExamType,
                                    subjectNameHint = mDers,
                                    topicOrQuestionText = mKonuRaw,
                                    fallbackTopicName = localBlueprint.defaultTopicName
                                )
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
                                        subjectName = lockedBookSubject.ifBlank { resolvedMapSubject.name },
                                        topicName = matchedTopicForRange.name.takeUnless {
                                            it.equals("İnceleme Bekliyor", ignoreCase = true)
                                        }.orEmpty(),
                                        difficulty = mZorluk
                                    )
                                }
                            }
                        }
                    } else {
                        aiStatus.unavailableReason = "Gemini kitap haritası yanıtı boş döndü."
                    }
                } catch (e: TimeoutCancellationException) {
                    aiStatus.unavailableReason = "Gemini isteği zaman aşımına uğradı."
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    aiStatus.unavailableReason = describeGeminiFailure(e)
                }
                aiStatus.unavailableReason?.let {
                    _batchStatusText.value = "$it Kitap cihaz içi OCR ile taranacak."
                }

                // STEP 3: 100% Free & Unlimited Local Dual-Column Vector & Pixel Projection Cropping Loop
                val resolvedPub = SoruLabPublisherPresets.resolvePublisher(publisherName, customPublishers)
                val finalPubName = publisherName.ifBlank { resolvedPub.name }
                val finalLogoUri = publisherLogoUri.ifBlank { resolvedPub.customLogoPath }

                val allExtractedQuestions = mutableListOf<ExtractedPdfQuestionItem>()
                var localOcrCount = 0
                var rejectedCropCount = 0
                var pageRenderFailureCount = 0
                var pageTextOcrFailureCount = 0

                // Scan every page; only OCR-confirmed front matter is skipped.
                val firstContentPage = 0
                val pageCountToScan = (totalPdfPages - firstContentPage).coerceAtLeast(1)

                withContext(Dispatchers.IO) {
                    android.os.ParcelFileDescriptor.open(
                        seekablePdfFile,
                        android.os.ParcelFileDescriptor.MODE_READ_ONLY
                    ).use { pfd ->
                        android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                            val pageRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            pageTextRecognizer = pageRecognizer
                            val rendererTotal = renderer.pageCount.coerceAtLeast(1)
                            var lastProgressUpdateMs = 0L

                            for (scanPos in 0 until pageCountToScan) {
                                val pageIdx = if (totalPdfPages > firstContentPage) {
                                    firstContentPage + scanPos
                                } else {
                                    0
                                }
                                if (pageIdx >= rendererTotal) continue
                                var pageInfo = mergedPageMap[pageIdx] ?: StudioImageAndPdfEngine.LocalPageMapEntry(
                                    pageIndex = pageIdx,
                                    examType = defaultExamType,
                                    subjectName = defaultSubject,
                                    topicName = "",
                                    difficulty = "Orta"
                                )

                                val nowMs = System.currentTimeMillis()
                                if (nowMs - lastProgressUpdateMs >= 110L || scanPos == 0 || scanPos == pageCountToScan - 1) {
                                    lastProgressUpdateMs = nowMs
                                    val progressPct = 16 + (((scanPos + 1).toFloat() / pageCountToScan.toFloat()) * 82f).toInt()
                                    _batchProgressPercent.value = progressPct.coerceIn(16, 98)
                                    val ocrLabel = if (aiStatus.unavailableReason == null) {
                                        "Gemini OCR"
                                    } else {
                                        "Cihaz içi OCR"
                                    }
                                    _batchStatusText.value = "Sayfa ${pageIdx + 1} / $totalPdfPages taranıyor • ${allExtractedQuestions.size} soru • $ocrLabel"
                                }

                                val pageBitmap = try {
                                    StudioImageAndPdfEngine.renderPageForLocalAnalysis(
                                        renderer = renderer,
                                        pageIndex = pageIdx,
                                        totalPages = rendererTotal,
                                        reusableBitmapRef = reusableBitmapRef
                                    ) ?: continue
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    pageRenderFailureCount++
                                    continue
                                }

                                val recognizedPage = try {
                                    recognizePdfPage(pageBitmap, pageRecognizer)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    pageTextOcrFailureCount++
                                    null
                                }
                                val pageText = recognizedPage?.text.orEmpty()
                                val footerText = recognizedPage?.textBlocks
                                    ?.filter { block ->
                                        (block.boundingBox?.top ?: 0) >= pageBitmap.height * 0.94f
                                    }
                                    ?.joinToString("\n") { it.text }
                                    .orEmpty()
                                mergedAnswerKey.putAll(
                                    StudioImageAndPdfEngine.parseAnswerKeyRegex(footerText)
                                )
                                val pageHeaderText = recognizedPage?.textBlocks
                                    ?.filter { block ->
                                        (block.boundingBox?.top ?: Int.MAX_VALUE) <= pageBitmap.height * 0.15f
                                    }
                                    ?.joinToString("\n") { it.text }
                                    .orEmpty()
                                if (StudioImageAndPdfEngine.shouldSkipQuestionPage(pageText, pageHeaderText)) {
                                    continue
                                }
                                val lockedSubjectForPage = lockedBookSubject.ifBlank { pageInfo.subjectName }
                                val headerTopic = pageHeaderText.takeIf { it.isNotBlank() }?.let {
                                    StudioImageAndPdfEngine.detectTopicNameFromText(
                                        examType = pageInfo.examType,
                                        subjectName = lockedSubjectForPage,
                                        text = it
                                    ) ?: lockedSubjectForPage.takeIf(String::isNotBlank)?.let {
                                        SoruLabCatalog.matchBestTopic(
                                            examTypeHint = pageInfo.examType,
                                            subjectNameHint = it,
                                            topicOrQuestionText = pageHeaderText
                                        ).second.name.takeUnless { name ->
                                            name.equals("İnceleme Bekliyor", ignoreCase = true)
                                        }
                                    }
                                }.orEmpty()
                                pageInfo = pageInfo.copy(
                                    subjectName = lockedSubjectForPage,
                                    topicName = headerTopic.ifBlank { pageInfo.topicName }
                                )
                                val questionNumberAnchors = recognizedPage?.textBlocks
                                    ?.flatMap { it.lines }
                                    ?.mapNotNull { line ->
                                        val match = Regex("""^\s*([1-9]|[1-3][0-9])[\.\-\)]\s*""")
                                            .find(line.text) ?: return@mapNotNull null
                                        val numberElement = line.elements.firstOrNull { element ->
                                            Regex("""^\s*([1-9]|[1-3][0-9])[\.\-\)]?\s*$""")
                                                .matches(element.text)
                                        } ?: return@mapNotNull null
                                        val bounds = numberElement.boundingBox ?: return@mapNotNull null
                                        QuestionNumberAnchor(
                                            number = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null,
                                            left = bounds.left,
                                            top = bounds.top,
                                            right = bounds.right,
                                            bottom = bounds.bottom
                                        )
                                    }
                                    .orEmpty()

                                val croppedBoxes = try {
                                    StudioImageAndPdfEngine.cropRenderedPageLocalDualColumn(
                                        context = context,
                                        pageBitmap = pageBitmap,
                                        pageIndex = pageIdx,
                                        totalPages = rendererTotal,
                                        reusablePixelsRef = reusablePixelsRef,
                                        questionNumberAnchors = questionNumberAnchors
                                    )
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    pageRenderFailureCount++
                                    emptyList()
                                }

                                val pageCandidates = if (croppedBoxes.isEmpty()) {
                                    extractQuestionsFromPageWithGemini(
                                        context = context,
                                        pageBitmap = pageBitmap,
                                        pageIndex = pageIdx,
                                        pageInfo = pageInfo,
                                        aiStatus = aiStatus
                                    )
                                } else {
                                    val classifications = coroutineScope {
                                        val semaphore = Semaphore(3)
                                        croppedBoxes.map { cropBox ->
                                            async {
                                                semaphore.withPermit {
                                                    val classified = try {
                                                        classifyCroppedQuestion(
                                                            imagePath = cropBox.croppedImagePath,
                                                            examTypeHint = pageInfo.examType,
                                                            subjectNameHint = pageInfo.subjectName,
                                                            pageTopicHint = pageInfo.topicName,
                                                            visualDifficultyHint = cropBox.estimatedDifficulty5,
                                                            hasDiagramOrGraph = cropBox.hasDiagramOrGraph,
                                                            lineCountEstimate = cropBox.lineCountEstimate,
                                                            aiStatus = aiStatus
                                                        )
                                                    } catch (e: CancellationException) {
                                                        throw e
                                                    } catch (_: Exception) {
                                                        null
                                                    }
                                                    classified ?: fallbackClassificationForCrop(
                                                        questionText = "",
                                                        examType = pageInfo.examType,
                                                        subjectName = pageInfo.subjectName,
                                                        topicName = pageInfo.topicName,
                                                        difficulty = cropBox.estimatedDifficulty5
                                                    )
                                                }
                                            }
                                        }.awaitAll()
                                    }
                                    croppedBoxes.mapIndexedNotNull { index, cropBox ->
                                        val classification = classifications[index]
                                        PageQuestionCandidate(
                                                cropBox = cropBox,
                                                classification = classification,
                                                answerKeyQuestionNumber = classification.answerKeyQuestionNumber
                                        )
                                    }
                                }

                                for ((boxIdx, candidate) in pageCandidates.withIndex()) {
                                    val cropBox = candidate.cropBox
                                    val classification = candidate.classification
                                    if (classification.usedLocalOcr) localOcrCount++

                                    val globalQuestionNum = allExtractedQuestions.size + 1
                                    val printedQuestionNumber =
                                        candidate.answerKeyQuestionNumber ?: cropBox.questionOrderInPage
                                    val verifiedAnswer = resolveAnswerKeyOption(
                                        questionNumber = printedQuestionNumber,
                                        answerKey = mergedAnswerKey
                                    )

                                    allExtractedQuestions.add(
                                        ExtractedPdfQuestionItem(
                                            id = "local_q_${System.currentTimeMillis()}_p${pageIdx + 1}_${boxIdx + 1}_$globalQuestionNum",
                                            questionNumber = printedQuestionNumber,
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
                                            boxNormalized = cropBox.boxNormalized
                                        )
                                    )
                                }

                                kotlinx.coroutines.yield()
                            }
                        }
                    }
                }

                for (index in allExtractedQuestions.indices) {
                    val question = allExtractedQuestions.getOrNull(index) ?: continue
                    if (question.answerKeyMatched) continue
                    val answer = resolveAnswerKeyOption(
                        questionNumber = question.questionNumber,
                        answerKey = mergedAnswerKey
                    ) ?: continue
                    allExtractedQuestions[index] = question.copy(
                        correctOption = answer,
                        answerKeyMatched = true,
                        solutionSummary = question.solutionSummary.replace(
                            "Cevap anahtarı eşleşmesi bulunamadı",
                            "Doğrulanmış Cevap: $answer"
                        )
                    )
                }

                if (allExtractedQuestions.isEmpty()) {
                    val aiHint = aiStatus.unavailableReason?.let { " $it" }.orEmpty()
                    val pageHint = buildString {
                        if (rejectedCropCount > 0) {
                            append(" $rejectedCropCount kırpılmış blok soru kökü ve A-E şıkları doğrulanamadığı için reddedildi.")
                        }
                        if (pageRenderFailureCount > 0) {
                            append(" $pageRenderFailureCount sayfa işlenirken hata oluştu.")
                        }
                        if (pageTextOcrFailureCount > 0) {
                            append(" $pageTextOcrFailureCount sayfada OCR metni okunamadı; görsel tabanlı kırpma yine denendi.")
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
                    if (rejectedCropCount > 0) {
                        add("$rejectedCropCount kırpılmış blok soru kökü ve A-E şıkları doğrulanamadığı için aktarılmadı.")
                    }
                    aiStatus.unavailableReason?.let {
                        add("$it Kitap aktarımı cihaz içi OCR ile sürdürüldü.")
                    }
                    if (localOcrCount > 0 && aiStatus.unavailableReason == null) {
                        add("$localOcrCount soru Gemini yerine cihaz içi OCR ile okundu.")
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
                    if (pageTextOcrFailureCount > 0) {
                        add("$pageTextOcrFailureCount sayfada OCR metni okunamadı; bu sayfalardaki görseller yine tarandı.")
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
                pageTextRecognizer?.close()
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
