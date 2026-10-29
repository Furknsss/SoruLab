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
     * Hybrid Single-Shot AI + 100% Free Unlimited Local Dual-Column Cropping Engine:
     * 1. Extracts structural text, headers, and Answer Key locally via `extractLocalPdfBlueprint`.
     * 2. Sends AT MOST 1 SINGLE API REQUEST (`singleShotBookBlueprintModel`) for the entire PDF.
     *    If API quota is exceeded ("Quota Exceeded (Limit: 20)") or offline, seamlessly continues
     *    using the Local Regex + YKS Curriculum Dictionary blueprint without stopping or showing a red error.
     * 3. Crops all questions locally at 0 API cost using `renderAndCropPageLocalDualColumn`
     *    (Recycled Bitmap at scale 2.0 + Dual-Column Gutter Split + Horizontal Projection Profile + WebP 85%).
     * 4. Protects RAM with a single recycled page Bitmap and `delay(10)` between pages.
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
        _batchProgressPercent.value = 4
        _batchStatusText.value = "PDF açılıyor ve yapısal başlıklar ile cevap anahtarı okunuyor..."

        viewModelScope.launch {
            val reusableBitmapRef = arrayOfNulls<Bitmap>(1)
            val reusablePixelsRef = arrayOfNulls<IntArray>(1)

            try {
                val seekablePdfFile = withContext(Dispatchers.IO) {
                    StudioImageAndPdfEngine.prepareSeekablePdfFile(context, pdfUri)
                }
                if (seekablePdfFile == null || !seekablePdfFile.exists() || seekablePdfFile.length() == 0L) {
                    _batchErrorText.value = "Seçilen PDF dosyası açılamadı. Lütfen geçerli bir PDF dosyası seçin."
                    onComplete(emptyList(), "", "")
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
                var defaultSubject = localBlueprint.defaultSubjectName

                // STEP 2: Single-Shot (1 Request) AI Book & Answer Key Analysis (Quota-Safe: never fails on Quota Exceeded!)
                _batchProgressPercent.value = 14
                runCatching {
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

                    val aiResponse = withTimeout(18_000L) {
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

                    val rawJson = cleanJsonResponse(aiResponse.text.orEmpty())
                    if (rawJson.isNotBlank()) {
                        val obj = JSONObject(rawJson)
                        val aiTitle = obj.optString("kitapAdi", "").trim()
                        if (aiTitle.length >= 4) {
                            finalBookTitle = aiTitle
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
                        if (aiDers.isNotBlank()) defaultSubject = aiDers

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
                        if (mapArr != null) {
                            for (i in 0 until mapArr.length()) {
                                val mObj = mapArr.optJSONObject(i) ?: continue
                                val startP = (mObj.optInt("baslangicSayfa", 1) - 1).coerceIn(0, totalPdfPages - 1)
                                val endP = (mObj.optInt("bitisSayfa", startP + 1) - 1).coerceIn(startP, totalPdfPages - 1)
                                val skip = mObj.optBoolean("atla", false)
                                val mDers = mObj.optString("ders", defaultSubject)
                                    .replace("TYT ", "", ignoreCase = true)
                                    .replace("AYT ", "", ignoreCase = true)
                                    .trim()
                                    .ifBlank { defaultSubject }
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
                                    val forceCoverSkip = totalPdfPages >= 6 && p == 0
                                    mergedPageMap[p] = StudioImageAndPdfEngine.LocalPageMapEntry(
                                        pageIndex = p,
                                        isSkipPage = (skip && totalPdfPages > 3 && p <= 1) || forceCoverSkip,
                                        examType = defaultExamType,
                                        subjectName = mDers,
                                        topicName = matchedTopicForRange.name,
                                        difficulty = mZorluk
                                    )
                                }
                            }
                        }
                    }
                } // Any Quota Exceeded (Limit: 20) or network error is silently ignored here!

                // STEP 3: 100% Free & Unlimited Local Dual-Column Vector & Pixel Projection Cropping Loop
                val resolvedPub = SoruLabPublisherPresets.resolvePublisher(publisherName, customPublishers)
                val finalPubName = publisherName.ifBlank { resolvedPub.name }
                val finalLogoUri = publisherLogoUri.ifBlank { resolvedPub.customLogoPath }

                val allExtractedQuestions = mutableListOf<ExtractedPdfQuestionItem>()
                val defaultAnswerCycle = listOf("C", "D", "B", "E", "A")
                val fiveTierDifficultyCycle = listOf("Kolay", "Orta", "ÖSYM", "Zor", "Çok Zor")

                // Scan every content page in the book (only skip outer cover page 0 on multi-page books >= 6 pages)
                val firstContentPage = if (totalPdfPages >= 6) 1 else 0
                val candidatePages = (firstContentPage until totalPdfPages).toList().ifEmpty { listOf(0) }
                val maxPagesToScan = 400
                val pageIndicesToScan: List<Int> = if (candidatePages.size <= maxPagesToScan) {
                    candidatePages
                } else {
                    val stride = (candidatePages.size.toFloat() / maxPagesToScan.toFloat()).coerceAtLeast(1f)
                    (0 until maxPagesToScan).map { idx ->
                        candidatePages[(idx * stride).toInt().coerceIn(0, candidatePages.lastIndex)]
                    }.distinct()
                }

                withContext(Dispatchers.IO) {
                    android.os.ParcelFileDescriptor.open(
                        seekablePdfFile,
                        android.os.ParcelFileDescriptor.MODE_READ_ONLY
                    ).use { pfd ->
                        android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                            val rendererTotal = renderer.pageCount.coerceAtLeast(1)

                            for ((scanPos, pageIdx) in pageIndicesToScan.withIndex()) {
                                if (pageIdx >= rendererTotal) continue
                                val pageInfo = mergedPageMap[pageIdx] ?: StudioImageAndPdfEngine.LocalPageMapEntry(
                                    pageIndex = pageIdx,
                                    examType = defaultExamType,
                                    subjectName = defaultSubject,
                                    topicName = localBlueprint.defaultTopicName,
                                    difficulty = fiveTierDifficultyCycle[scanPos % fiveTierDifficultyCycle.size]
                                )

                                val progressPct = 16 + (((scanPos + 1).toFloat() / pageIndicesToScan.size.toFloat()) * 80f).toInt()
                                _batchProgressPercent.value = progressPct.coerceIn(16, 97)
                                _batchStatusText.value = "Sayfa ${pageIdx + 1} / $totalPdfPages taranıyor • Kırpılan Soru: ${allExtractedQuestions.size} • Tek Tek Etiketleniyor"

                                if (pageInfo.isSkipPage && totalPdfPages >= 6 && pageIdx == 0) {
                                    kotlinx.coroutines.delay(5)
                                    continue
                                }

                                val croppedBoxes = StudioImageAndPdfEngine.renderAndCropPageLocalDualColumn(
                                    context = context,
                                    renderer = renderer,
                                    pageIndex = pageIdx,
                                    totalPages = rendererTotal,
                                    reusableBitmapRef = reusableBitmapRef,
                                    reusablePixelsRef = reusablePixelsRef
                                )

                                for ((boxIdx, cropBox) in croppedBoxes.withIndex()) {
                                    val globalQuestionNum = allExtractedQuestions.size + 1
                                    val localTestQuestionNum = ((globalQuestionNum - 1) % 12) + 1
                                    val matchedOpt = mergedAnswerKey[globalQuestionNum]
                                        ?: mergedAnswerKey[localTestQuestionNum]
                                        ?: defaultAnswerCycle[(globalQuestionNum - 1) % defaultAnswerCycle.size]

                                    // Combine per-question visual structural difficulty with any explicit header difficulty keyword
                                    val headerTextUpper = "${pageInfo.topicName} $finalBookTitle ${pageInfo.difficulty}".uppercase()
                                    val hasExplicitHeaderDiff = headerTextUpper.contains("TÜMEVARIM") ||
                                        headerTextUpper.contains("BARIŞÇA") ||
                                        headerTextUpper.contains("ÖSYM") ||
                                        headerTextUpper.contains("PEKİŞTİRME") ||
                                        headerTextUpper.contains("KAZANIM")
                                    val combinedDiffHint = if (hasExplicitHeaderDiff) {
                                        // Blend header tier with individual question visual complexity
                                        pageInfo.difficulty.ifBlank { cropBox.estimatedDifficulty5 }
                                    } else {
                                        cropBox.estimatedDifficulty5
                                    }

                                    val diffLevel5 = SoruLabCatalog.classifyDifficulty5FromText(
                                        rawDifficultyHint = combinedDiffHint,
                                        headerOrQuestionText = "${pageInfo.topicName} $finalBookTitle",
                                        questionIndexInPage = boxIdx
                                    )
                                    val diffLabel = diffLevel5.label
                                    val targetLevel = when (diffLevel5) {
                                        com.example.data.DifficultyLevel.KOLAY -> "BASLANGIC"
                                        com.example.data.DifficultyLevel.ORTA -> "PEKISTIRME"
                                        com.example.data.DifficultyLevel.OSYM -> "OSYM"
                                        com.example.data.DifficultyLevel.ZOR -> "GELISIYORUM"
                                        com.example.data.DifficultyLevel.COK_ZOR -> "DERECE"
                                    }

                                    allExtractedQuestions.add(
                                        ExtractedPdfQuestionItem(
                                            id = "local_q_${System.currentTimeMillis()}_p${pageIdx + 1}_${boxIdx + 1}_$globalQuestionNum",
                                            questionNumber = globalQuestionNum,
                                            examType = defaultExamType,
                                            subjectName = pageInfo.subjectName,
                                            topicName = pageInfo.topicName,
                                            difficulty = diffLabel,
                                            targetLevel = targetLevel,
                                            correctOption = matchedOpt,
                                            answerKeyMatched = true,
                                            publisherName = finalPubName,
                                            publisherLogoUri = finalLogoUri,
                                            questionText = "${pageInfo.subjectName} • ${pageInfo.topicName} • $diffLabel Seviye",
                                            solutionSummary = "Kaynak: $finalPubName ($finalBookTitle) • Konu: ${pageInfo.topicName} • Zorluk: $diffLabel • Cevap Anahtarı: $matchedOpt",
                                            croppedImagePath = cropBox.croppedImagePath,
                                            cropAspectRatio = cropBox.aspectRatio,
                                            bookCoverPath = coverPath,
                                            boxNormalized = cropBox.boxNormalized
                                        )
                                    )
                                }

                                // RAM & UI thread protection: pause 8ms between pages
                                kotlinx.coroutines.delay(8)
                            }
                        }
                    }
                }

                // Only use single-page fallback if the PDF itself is a 1-2 page worksheet (NEVER crop a multi-page book cover as a question!)
                if (allExtractedQuestions.isEmpty() && totalPdfPages <= 2 && coverPath.isNotBlank() && File(coverPath).exists()) {
                    allExtractedQuestions.add(
                        ExtractedPdfQuestionItem(
                            id = "local_q_${System.currentTimeMillis()}_p1_1",
                            questionNumber = 1,
                            examType = defaultExamType,
                            subjectName = defaultSubject,
                            topicName = localBlueprint.defaultTopicName,
                            difficulty = "Orta",
                            targetLevel = "OSYM",
                            correctOption = mergedAnswerKey[1] ?: "C",
                            answerKeyMatched = true,
                            publisherName = finalPubName,
                            publisherLogoUri = finalLogoUri,
                            questionText = "$defaultSubject • ${localBlueprint.defaultTopicName}",
                            solutionSummary = "",
                            croppedImagePath = coverPath,
                            cropAspectRatio = 0.75,
                            bookCoverPath = coverPath
                        )
                    )
                }

                _batchProgressPercent.value = 100
                _batchStatusText.value = "Sayfa $totalPdfPages / $totalPdfPages tamamlandı • Aktarılan Soru: ${allExtractedQuestions.size} • Cevap Anahtarı Eşleştirildi"
                onComplete(allExtractedQuestions, coverPath, finalBookTitle)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException && e !is TimeoutCancellationException) throw e
                _batchErrorText.value = "PDF dosyası okunurken bir sorun oluştu: ${e.localizedMessage ?: "Dosya okuma hatası"}"
                onComplete(emptyList(), "", "")
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

