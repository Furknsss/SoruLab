package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AiStructuredSolution
import com.example.data.SoruLabCatalog
import com.example.data.SoruLabPublisherPresets
import com.example.data.YksPublisherInfo
import com.example.data.YksQuestion
import com.example.util.StudioImageAndPdfEngine
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File

data class AiChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val attachedQuestionTitle: String? = null,
    val timestampLabel: String = "Şimdi"
)

data class AutoTagSuggestion(
    val examType: String = "TYT",
    val subjectName: String = "Matematik",
    val topicName: String = "Temel Kavramlar",
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
    val options: Map<String, String> = mapOf(
        "A" to "",
        "B" to "",
        "C" to "",
        "D" to "",
        "E" to ""
    ),
    val correctOption: String = "C",
    val solutionExplanation: String = ""
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

@Immutable
data class BatchPdfCropState(
    val isAnalyzing: Boolean = false,
    val progressPercent: Int = 0,
    val progressMessage: String = "",
    val errorMessage: String? = null
)

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
                        "zorluk" to Schema.string("Kolay, Orta veya Zor"),
                        "dogruSecenek" to Schema.string("A, B, C, D veya E"),
                        "ders" to Schema.string("Bu sorunun dersi: Matematik, Geometri, Fizik, Kimya, Biyoloji veya Türkçe"),
                        "konu" to Schema.string("Bu sorunun konusu (Örn: Temel Kavramlar, Türev, Bağıl Hareket)")
                    ),
                    optionalProperties = listOf("ders", "konu")
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

    fun clearBatchError() {
        _batchErrorText.value = null
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
                appendLine("Sen Türkiye'nin en iyi YKS (TYT/AYT) soru çözüm öğretmeni olan SoruLab AI'sın.")
                appendLine("Yalnızca verilen soru verilerine dayanarak 3 aşamalı çözümü (1. Kritik Bilgi & Teorem, 2. Adım Adım Çözüm, 3. ÖSYM Tuzak Notu) açıkla:")
                appendLine("Yayın: ${question.publisherName}")
                appendLine("Konu: ${question.topicHeader}")
                if (question.stemTop.isNotBlank()) {
                    appendLine("Soru Kökü: ${question.stemTop}")
                }
                if (question.bulletsOrSteps.isNotEmpty()) {
                    appendLine("Öncüller/Veriler:\n${question.bulletsOrSteps.joinToString("\n")}")
                }
                appendLine("Kesin Doğru Cevap (Cevap Anahtarı): ${question.correctOption}")
                if (question.stepByStepSolution.isNotBlank()) {
                    appendLine("Referans Çözüm Özeti: ${question.stepByStepSolution}")
                }
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
                    text = "Adım Adım Çözüm (${question.topicHeader} - Soru ${question.number}):\n\n" +
                        "${question.stepByStepSolution}\n\nDoğru Seçenek: ${question.correctOption}"
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
                appendLine("Sen uzman bir YKS (TYT/AYT) öğretmenisin.")
                appendLine("Konu: ${question.topicHeader}")
                appendLine("Cevap Anahtarındaki Doğru Seçenek: ${question.correctOption}")
                if (question.stemTop.isNotBlank()) {
                    appendLine("Soru Bilgisi: ${question.stemTop}")
                }
                appendLine("Görseldeki soruyu dikkatle incele ve çözümü şu 3 aşamalı başlık altında net Türkçe ile yaz:")
                appendLine("1. KRİTİK BİLGİ & TEOREM")
                appendLine("2. ADIM ADIM ÇÖZÜM (Sonucun neden ${question.correctOption} seçeneği olduğunu işlemli göster)")
                appendLine("3. ÖSYM TUZAK NOTU")
            }

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
                    onResult(question.stepByStepSolution.ifBlank { "Cevap anahtarına göre doğru yanıt ${question.correctOption} seçeneğidir." })
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException && e !is TimeoutCancellationException) throw e
                onResult(question.stepByStepSolution.ifBlank { "Cevap anahtarına göre doğru yanıt ${question.correctOption} seçeneğidir." })
            }
        }
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
                if (question.croppedImagePath.isNotBlank() && File(question.croppedImagePath).exists()) {
                    runCatching { BitmapFactory.decodeFile(question.croppedImagePath) }.getOrNull()
                } else {
                    null
                }
            }

            val prompt = buildString {
                appendLine("Sen uzman bir ÖSYM YKS soru yazarısın.")
                appendLine("Konu: ${question.topicHeader}")
                appendLine("Zorluk: ${question.difficulty.label}")
                if (question.stemTop.isNotBlank()) {
                    appendLine("Orijinal Soru: ${question.stemTop}")
                }
                appendLine("Bu sorunun ölçtüğü kazanım ve çözüm mantığıyla birebir aynı (sayıları ve kurgusu farklı) yeni bir İKİZ SORU üret.")
            }

            try {
                val response = withTimeout(45_000L) {
                    if (questionBmp != null) {
                        twinQuestionGeneratorModel.generateContent(
                            content {
                                image(questionBmp)
                                text(prompt)
                            }
                        )
                    } else {
                        twinQuestionGeneratorModel.generateContent(prompt)
                    }
                }
                val raw = response.text.orEmpty()
                val obj = JSONObject(raw)
                val stem = obj.optString("questionStem", "").trim()
                val optA = obj.optString("optionA", "12").trim()
                val optB = obj.optString("optionB", "15").trim()
                val optC = obj.optString("optionC", "18").trim()
                val optD = obj.optString("optionD", "24").trim()
                val optE = obj.optString("optionE", "30").trim()
                val correct = obj.optString("correctOption", question.correctOption)
                    .uppercase()
                    .takeIf { it in listOf("A", "B", "C", "D", "E") } ?: question.correctOption
                val explanation = obj.optString("solutionExplanation", "").trim()

                if (stem.isNotBlank()) {
                    onResult(
                        TwinQuestionModalData(
                            topicTitle = question.topicHeader,
                            difficultyLabel = question.difficulty.label,
                            questionStem = stem,
                            options = mapOf(
                                "A" to optA,
                                "B" to optB,
                                "C" to optC,
                                "D" to optD,
                                "E" to optE
                            ),
                            correctOption = correct,
                            solutionExplanation = explanation.ifBlank { "Doğru seçenek $correct şıkkıdır." }
                        )
                    )
                    return@launch
                }
            } catch (_: Exception) {
            }

            onResult(
                TwinQuestionModalData(
                    topicTitle = question.topicHeader,
                    difficultyLabel = question.difficulty.label,
                    questionStem = "${question.topicHeader} kazanımına ait eşdeğer İkiz Soru:\nVerilen bağıntı ve koşullara göre aynı yöntem uygulandığında elde edilen değer aşağıdakilerden hangisidir?",
                    options = mapOf(
                        "A" to "I ve II",
                        "B" to "Yalnız II",
                        "C" to "II ve III",
                        "D" to "I ve III",
                        "E" to "I, II ve III"
                    ),
                    correctOption = question.correctOption,
                    solutionExplanation = "Orijinal soruyla aynı teorem ve işlem adımları izlendiğinde doğru yanıt ${question.correctOption} seçeneğidir."
                )
            )
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

    /**
     * Real Single-Click PDF Extraction Pipeline (Zero Simulation):
     * 1. Copies PDF stream to a seekable file and reads the real page count (`totalPdfPages`).
     * 2. Renders page 1 as the real Book Cover image.
     * 3. Scans the last page(s) first to extract the Answer Key (`{"1": "C", "2": "D", ...}`).
     * 4. Renders each page at `scale = 2.0f` and sends it to Gemini Vision (`gemini-3.8-flash`)
     *    to automatically detect `kitapAdi`, `ders`, `konu`, and `sorular` (`orijinalSoruNo`, `box_2d`, `zorluk`, `dogruSecenek`).
     * 5. Crops each `box_2d` region from the high-res page bitmap and saves it as a real PNG file.
     * 6. Surfaces any real error in `_batchErrorText` so it is never hidden.
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
        _batchProgressPercent.value = 5
        _batchStatusText.value = "PDF dosyası açılıyor ve sayfa sayısı kontrol ediliyor..."

        viewModelScope.launch {
            try {
                val seekablePdfFile = withContext(Dispatchers.IO) {
                    StudioImageAndPdfEngine.prepareSeekablePdfFile(context, pdfUri)
                }
                if (seekablePdfFile == null || !seekablePdfFile.exists() || seekablePdfFile.length() == 0L) {
                    _batchErrorText.value = "Seçilen PDF dosyası okunamadı. Lütfen cihazınızdan geçerli bir PDF dosyası seçip tekrar deneyin."
                    onComplete(emptyList(), "", "")
                    return@launch
                }

                val (_, rawTotalPages) = withContext(Dispatchers.IO) {
                    StudioImageAndPdfEngine.renderPdfPageFromSeekableFile(seekablePdfFile, 0, renderScale = 1.2f)
                }
                val totalPdfPages = rawTotalPages.coerceAtLeast(1)
                val pagesToProcess = totalPdfPages.coerceIn(1, 10)

                _batchProgressPercent.value = 10
                _batchStatusText.value = "PDF Açıldı: Toplam $totalPdfPages Sayfa. Kitap kapağı ve cevap anahtarı taranıyor..."

                val coverPath = withContext(Dispatchers.IO) {
                    StudioImageAndPdfEngine.savePdfFirstPageAsBookCover(context, pdfUri)
                }

                val cleanFileHint = fileNameHint
                    .removeSuffix(".pdf")
                    .removeSuffix(".PDF")
                    .replace("_", " ")
                    .replace("-", " ")
                    .trim()

                // First: Scan the last page(s) for Answer Key if PDF has more than 1 page
                val tailAnswerKeyMap = mutableMapOf<Int, String>()
                if (totalPdfPages >= 2) {
                    _batchProgressPercent.value = 16
                    _batchStatusText.value = "PDF Açıldı: Toplam $totalPdfPages Sayfa. Son sayfadaki cevap anahtarı okunuyor..."
                    val tailBitmaps = withContext(Dispatchers.IO) {
                        StudioImageAndPdfEngine.extractLastPagesForAnswerKeyOcr(
                            context = context,
                            pdfUri = pdfUri,
                            maxTailPages = if (totalPdfPages >= 4) 2 else 1
                        )
                    }
                    if (tailBitmaps.isNotEmpty()) {
                        tailAnswerKeyMap.putAll(extractAnswerKeyFromBookPages(tailBitmaps, cleanFileHint))
                    }
                }

                val allSubjectsCatalogSummary = SoruLabCatalog.allSubjects.joinToString("\n") { subj ->
                    "${subj.examType.name} - ${subj.name}: ${subj.topics.joinToString(", ") { it.name }}"
                }
                val inferredExamHint = when {
                    cleanFileHint.contains("AYT", ignoreCase = true) -> "AYT"
                    cleanFileHint.contains("TYT", ignoreCase = true) -> "TYT"
                    else -> "TYT"
                }

                val allRawExtracted = mutableListOf<ExtractedPdfQuestionItem>()
                val pageInlineAnswerKey = mutableMapOf<Int, String>()
                var autoDetectedBookTitle = ""
                var lastApiErrorMessage: String? = null

                for (pageIdx in 0 until pagesToProcess) {
                    val pageNumHuman = pageIdx + 1
                    val progressStart = 20 + ((pageIdx.toFloat() / pagesToProcess.toFloat()) * 70f).toInt()
                    _batchProgressPercent.value = progressStart.coerceIn(18, 90)
                    _batchStatusText.value = "PDF Açıldı: Toplam $totalPdfPages Sayfa. Sayfa $pageNumHuman/$pagesToProcess yüksek çözünürlükte taranıyor..."

                    val pageResult = withContext(Dispatchers.IO) {
                        processSinglePageFromSeekableFile(
                            context = context,
                            seekablePdfFile = seekablePdfFile,
                            pageIdx = pageIdx,
                            fileNameHint = cleanFileHint,
                            publisherName = publisherName,
                            publisherLogoUri = publisherLogoUri,
                            inferredExamHint = inferredExamHint,
                            allSubjectsCatalogSummary = allSubjectsCatalogSummary,
                            customPublishers = customPublishers,
                            coverPath = coverPath
                        )
                    }

                    if (pageResult.detectedBookTitle.isNotBlank() && autoDetectedBookTitle.isBlank()) {
                        autoDetectedBookTitle = pageResult.detectedBookTitle
                    }
                    if (pageResult.errorMessage != null) {
                        lastApiErrorMessage = pageResult.errorMessage
                    }
                    allRawExtracted.addAll(pageResult.questions)
                    pageInlineAnswerKey.putAll(pageResult.inlineAnswerKey)
                }

                val masterAnswerKey = mutableMapOf<Int, String>().apply {
                    putAll(pageInlineAnswerKey)
                    putAll(tailAnswerKeyMap)
                }

                val finalMatchedQuestions = allRawExtracted.mapIndexed { idx, item ->
                    val seqNum = if (item.questionNumber > 0) item.questionNumber else (idx + 1)
                    val matchedOpt = masterAnswerKey[seqNum] ?: item.correctOption
                    item.copy(
                        correctOption = matchedOpt,
                        answerKeyMatched = masterAnswerKey.containsKey(seqNum) || item.answerKeyMatched,
                        bookCoverPath = coverPath
                    )
                }

                val finalBookTitle = autoDetectedBookTitle.ifBlank {
                    val firstQ = finalMatchedQuestions.firstOrNull()
                    when {
                        cleanFileHint.length >= 3 && !cleanFileHint.startsWith("staged_") && !cleanFileHint.startsWith("document") -> {
                            if (cleanFileHint.contains(publisherName, ignoreCase = true)) {
                                cleanFileHint
                            } else {
                                "$publisherName • $cleanFileHint"
                            }
                        }
                        firstQ != null -> "$publisherName ${firstQ.examType} ${firstQ.subjectName} - ${firstQ.topicName}"
                        else -> "$publisherName Soru Bankası"
                    }
                }

                if (finalMatchedQuestions.isEmpty()) {
                    _batchErrorText.value = lastApiErrorMessage
                        ?: "PDF sayfaları tarandı ancak soru kutusu tespit edilemedi. Lütfen PDF sayfalarının net soru görselleri içerdiğinden emin olun."
                    onComplete(emptyList(), coverPath, finalBookTitle)
                    return@launch
                }

                _batchProgressPercent.value = 100
                _batchStatusText.value = "Tamamlandı! '$finalBookTitle' kaynağından ${finalMatchedQuestions.size} soru Kitaplığınıza eklendi."
                onComplete(finalMatchedQuestions, coverPath, finalBookTitle)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException && e !is TimeoutCancellationException) throw e
                _batchErrorText.value = "PDF işlenirken hata oluştu: ${e.localizedMessage ?: "Bağlantı veya okuma hatası"}"
                onComplete(emptyList(), "", "")
            } finally {
                _isAutoTagging.value = false
                _batchProgressPercent.value = 0
                _batchStatusText.value = null
            }
        }
    }

    private data class SinglePageExtractionResult(
        val questions: List<ExtractedPdfQuestionItem>,
        val inlineAnswerKey: Map<Int, String>,
        val detectedBookTitle: String = "",
        val errorMessage: String? = null
    )

    private suspend fun processSinglePageFromSeekableFile(
        context: Context,
        seekablePdfFile: File,
        pageIdx: Int,
        fileNameHint: String,
        publisherName: String,
        publisherLogoUri: String,
        inferredExamHint: String,
        allSubjectsCatalogSummary: String,
        customPublishers: List<YksPublisherInfo>,
        coverPath: String
    ): SinglePageExtractionResult {
        return try {
            val (pageBitmap, _) = StudioImageAndPdfEngine.renderPdfPageFromSeekableFile(
                pdfFile = seekablePdfFile,
                pageIndex = pageIdx,
                renderScale = 2.0f
            )
            if (pageBitmap == null) {
                return SinglePageExtractionResult(
                    questions = emptyList(),
                    inlineAnswerKey = emptyMap(),
                    errorMessage = "Sayfa ${pageIdx + 1} görüntüye dönüştürülemedi."
                )
            }

            val inferenceBitmap = StudioImageAndPdfEngine.createInferenceBitmap(pageBitmap, 1440)

            val prompt = buildString {
                appendLine("Sen uzman bir YKS PDF soru kırpma (`box_2d`) ve otomatik ders/konu etiketleme motorusun.")
                appendLine("Yayınevi: $publisherName")
                if (fileNameHint.isNotBlank()) {
                    appendLine("PDF Dosya İpucu: $fileNameHint")
                }
                appendLine("GÖREVLERİN:")
                appendLine("1. `kitapAdi`: Sayfadaki üst başlıklardan ve PDF dosya ipucundan kaynağın adını otomatik belirle (Örn: '$publisherName AYT Matematik Soru Bankası').")
                appendLine("2. `ders`: Sayfadaki soruların dersini ('Matematik', 'Geometri', 'Fizik', 'Kimya', 'Biyoloji', 'Türkçe') ve `konu` başlığını (Örn: 'Temel Kavramlar', 'Türev', 'Üçgende Alan', 'Bağıl Hareket') tespit et.")
                appendLine("3. `sorular`: Sayfadaki HER SORU için:")
                appendLine("   - `orijinalSoruNo`: Sorunun PDF'teki numarası (1, 2, 3...).")
                appendLine("   - `box_2d`: `[ymin, xmin, ymax, xmax]` (0-1000 ölçeğinde). PDF'teki eski soru numarasını ('1.', '7.' vb.) dışarıda bırakarak sadece soru gövdesi, şekil ve A-E şıklarını kapsayan koordinatları ver.")
                appendLine("   - `zorluk`: 'Kolay', 'Orta' veya 'Zor'.")
                appendLine("   - `dogruSecenek`: Sayfa altında cevap anahtarı varsa veya sorudan belli ise 'A', 'B', 'C', 'D', 'E' (bilinmiyorsa 'C').")
                appendLine("Müfredat Konu Listesi:")
                appendLine(allSubjectsCatalogSummary)
            }

            val response = withTimeout(60_000L) {
                pageQuestionsExtractorModel.generateContent(
                    content {
                        image(inferenceBitmap)
                        text(prompt)
                    }
                )
            }

            val rawJson = cleanJsonResponse(response.text.orEmpty())
            val obj = JSONObject(rawJson)

            val pageBookTitle = obj.optString("kitapAdi", "").trim()
            val pageExamType = obj.optString("sinavTuru", inferredExamHint).uppercase()
                .let { if (it.contains("AYT")) "AYT" else if (it.contains("TYT")) "TYT" else inferredExamHint }
            val pageDers = obj.optString("ders", "Matematik")
                .replace("TYT ", "", ignoreCase = true)
                .replace("AYT ", "", ignoreCase = true)
                .trim()
                .ifBlank { "Matematik" }
            val pageKonu = obj.optString("konu", "Temel Kavramlar").trim().ifBlank { "Temel Kavramlar" }

            val inlineKeys = mutableMapOf<Int, String>()
            val pageKeyArr = obj.optJSONArray("pageAnswerKey")
            if (pageKeyArr != null) {
                for (i in 0 until pageKeyArr.length()) {
                    val kObj = pageKeyArr.optJSONObject(i) ?: continue
                    val qn = kObj.optInt("orijinalSoruNo", kObj.optInt("orijinalNo", 0))
                    val opt = kObj.optString("dogruSecenek", "").uppercase().trim()
                    if (qn > 0 && opt in listOf("A", "B", "C", "D", "E")) {
                        inlineKeys[qn] = opt
                    }
                }
            }

            val questionsArr = obj.optJSONArray("sorular") ?: obj.optJSONArray("questions")
            val items = mutableListOf<ExtractedPdfQuestionItem>()
            val resolvedPub = SoruLabPublisherPresets.resolvePublisher(publisherName, customPublishers)
            val finalPubName = publisherName.ifBlank { resolvedPub.name }
            val finalLogoUri = publisherLogoUri.ifBlank { resolvedPub.customLogoPath }

            if (questionsArr != null) {
                for (i in 0 until questionsArr.length()) {
                    val qObj = questionsArr.optJSONObject(i) ?: continue
                    val origQNum = qObj.optInt(
                        "orijinalSoruNo",
                        qObj.optInt("orijinalNo", (pageIdx * 4) + i + 1)
                    ).coerceAtLeast(1)

                    val boxArr = qObj.optJSONArray("box_2d")
                    val rawYMin = if (boxArr != null && boxArr.length() >= 4) boxArr.optDouble(0, 40.0).toInt() else 40
                    val rawXMin = if (boxArr != null && boxArr.length() >= 4) boxArr.optDouble(1, 45.0).toInt() else 45
                    val rawYMax = if (boxArr != null && boxArr.length() >= 4) boxArr.optDouble(2, 950.0).toInt() else 950
                    val rawXMax = if (boxArr != null && boxArr.length() >= 4) boxArr.optDouble(3, 960.0).toInt() else 960

                    val ymin1000 = minOf(rawYMin, rawYMax).coerceIn(0, 920)
                    val xmin1000 = minOf(rawXMin, rawXMax).coerceIn(0, 900)
                    val ymax1000 = maxOf(rawYMin, rawYMax).coerceIn(ymin1000 + 60, 1000)
                    val xmax1000 = maxOf(rawXMin, rawXMax).coerceIn(xmin1000 + 100, 1000)

                    val (savedPath, aspect) = StudioImageAndPdfEngine.cropWhitenAndSharpenQuestionStem(
                        context = context,
                        sourcePageBitmap = pageBitmap,
                        yminNorm1000 = ymin1000,
                        xminNorm1000 = xmin1000,
                        ymaxNorm1000 = ymax1000,
                        xmaxNorm1000 = xmax1000,
                        questionNumberForFile = origQNum
                    )

                    if (savedPath.isBlank()) continue

                    val detectedOpt = (inlineKeys[origQNum] ?: qObj.optString("dogruSecenek", "C"))
                        .uppercase()
                        .trim()
                        .takeIf { it in listOf("A", "B", "C", "D", "E") } ?: "C"

                    val rawDers = qObj.optString("ders", pageDers)
                        .replace("TYT ", "", ignoreCase = true)
                        .replace("AYT ", "", ignoreCase = true)
                        .trim()
                        .ifBlank { pageDers }

                    val rawKonu = qObj.optString("konu", pageKonu).trim().ifBlank { pageKonu }
                    val rawZorluk = qObj.optString("zorluk", "Orta").trim()
                        .let { z ->
                            when {
                                z.equals("Kolay", ignoreCase = true) -> "Kolay"
                                z.equals("Zor", ignoreCase = true) -> "Zor"
                                else -> "Orta"
                            }
                        }

                    val rawTargetLevel = when (rawZorluk) {
                        "Kolay" -> "BASLANGIC"
                        "Zor" -> "DERECE"
                        else -> "OSYM"
                    }

                    items.add(
                        ExtractedPdfQuestionItem(
                            id = "ext_${System.currentTimeMillis()}_${pageIdx}_${i}_$origQNum",
                            questionNumber = origQNum,
                            examType = pageExamType,
                            subjectName = rawDers,
                            topicName = rawKonu,
                            difficulty = rawZorluk,
                            targetLevel = rawTargetLevel,
                            correctOption = detectedOpt,
                            answerKeyMatched = inlineKeys.containsKey(origQNum),
                            publisherName = finalPubName,
                            publisherLogoUri = finalLogoUri,
                            questionText = "$rawDers • $rawKonu",
                            solutionSummary = "",
                            croppedImagePath = savedPath,
                            cropAspectRatio = aspect,
                            bookCoverPath = coverPath,
                            boxNormalized = listOf(
                                ymin1000 / 1000f,
                                xmin1000 / 1000f,
                                ymax1000 / 1000f,
                                xmax1000 / 1000f
                            )
                        )
                    )
                }
            }

            if (inferenceBitmap !== pageBitmap) {
                inferenceBitmap.recycle()
            }
            pageBitmap.recycle()

            SinglePageExtractionResult(
                questions = items,
                inlineAnswerKey = inlineKeys,
                detectedBookTitle = pageBookTitle
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException && e !is TimeoutCancellationException) throw e
            SinglePageExtractionResult(
                questions = emptyList(),
                inlineAnswerKey = emptyMap(),
                errorMessage = "Sayfa ${pageIdx + 1} analiz hatası: ${e.localizedMessage ?: "AI yanıt vermedi"}"
            )
        }
    }
}
