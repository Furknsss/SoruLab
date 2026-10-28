package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SoruLabCatalog
import com.example.data.YksQuestion
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    val croppedImagePath: String,
    val cropAspectRatio: Double,
    val boxNormalized: List<Float> = listOf(0.05f, 0.05f, 0.95f, 0.48f)
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
                        "questionNumber" to Schema.integer("Soru numarası (1, 2, 3...)"),
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

    private val pageQuestionsSchema = Schema.obj(
        mapOf(
            "pageAnswerKey" to Schema.array(
                Schema.obj(
                    mapOf(
                        "questionNumber" to Schema.integer("Soru numarası"),
                        "correctOption" to Schema.enumeration(listOf("A", "B", "C", "D", "E"))
                    )
                ),
                "Eğer bu sayfanın altında veya köşesinde cevap anahtarı varsa soru-şık listesi"
            ),
            "questions" to Schema.array(
                Schema.obj(
                    mapOf(
                        "questionNumber" to Schema.integer("Sayfadaki soru numarası (1, 2, 3...)"),
                        "ymin" to Schema.integer("Sorunun üst sınırı (0-1000 arası normalize koordinat)"),
                        "xmin" to Schema.integer("Sorunun sol sınırı (0-1000 arası normalize koordinat)"),
                        "ymax" to Schema.integer("Sorunun alt sınırı (0-1000 arası normalize koordinat, şıklar dahil)"),
                        "xmax" to Schema.integer("Sorunun sağ sınırı (0-1000 arası normalize koordinat)"),
                        "examType" to Schema.enumeration(listOf("TYT", "AYT"), "TYT veya AYT"),
                        "subjectName" to Schema.string("Ders adı: Matematik, Geometri, Türkçe, Fizik, Kimya, Biyoloji, Tarih, Coğrafya, Felsefe, Din Kültürü, Edebiyat"),
                        "topicName" to Schema.string("Tam YKS müfredat konu adı (ör. Temel Kavramlar, Üçgenler, Paragrafta Anlam, Kuvvet ve Hareket)"),
                        "difficulty" to Schema.enumeration(
                            listOf("Kolay", "Orta", "Zor"),
                            "Sorunun gerçek akademik zorluk seviyesi"
                        ),
                        "targetLevel" to Schema.enumeration(
                            listOf("BASLANGIC", "PEKISTIRME", "GELISIYORUM", "OSYM", "DERECE"),
                            "6 seviye çarkındaki gerçek kategori karşılığı"
                        ),
                        "detectedCorrectOption" to Schema.enumeration(
                            listOf("A", "B", "C", "D", "E"),
                            "Cevap anahtarı yoksa sorunun matematiksel/akademik çözümünden bulunan doğru şık"
                        ),
                        "questionText" to Schema.string("Sorunun tam metni ve öncülleri"),
                        "solutionSummary" to Schema.string("Sorunun adım adım net çözümü")
                    )
                ),
                "Sayfada tespit edilen tüm sorular"
            )
        )
    )

    private val pageQuestionsExtractorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = pageQuestionsSchema
            }
        )
    }

    private val initialWelcomeMessage = AiChatMessage(
        id = "welcome",
        isUser = false,
        text = "Merhaba! Ben SoruLab AI Asistan. Takıldığın YKS (TYT & AYT) sorularını adım adım çözebilir, konu özetleri çıkarabilir veya ÖSYM çözüm taktiklerini anlatabilirim. Hangi konu veya soru üzerinde çalışalım?"
    )

    private val _messages = MutableStateFlow(listOf(initialWelcomeMessage))
    val messages: StateFlow<List<AiChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isAutoTagging = MutableStateFlow(false)
    val isAutoTagging: StateFlow<Boolean> = _isAutoTagging.asStateFlow()

    private val _batchStatusText = MutableStateFlow<String?>(null)
    val batchStatusText: StateFlow<String?> = _batchStatusText.asStateFlow()

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
            "${question.topicHeader} • Soru ${question.number} için adım adım çözüm ve ÖSYM pratik yolunu anlatır mısın?"
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
                appendLine("0 Halüsinasyon politikasıyla yalnızca verilen soru verilerine dayanarak adım adım, anlaşılır ve doğru çözümü açıkla:")
                appendLine("Yayın: ${question.publisherName}")
                appendLine("Konu: ${question.topicHeader}")
                appendLine("Soru Kökü: ${question.stemTop}")
                if (question.bulletsOrSteps.isNotEmpty()) {
                    appendLine("Öncüller/Veriler:\n${question.bulletsOrSteps.joinToString("\n")}")
                }
                appendLine("Soru: ${question.stemBottom}")
                appendLine("Şıklar: ${question.options.entries.joinToString(" | ") { "${it.key}) ${it.value}" }}")
                appendLine("Kesin Doğru Cevap (Cevap Anahtarı): ${question.correctOption}")
                appendLine("Referans Çözüm Özeti: ${question.stepByStepSolution}")
                if (customPrompt.isNotBlank()) {
                    appendLine("Öğrencinin Özel Sorusu: $customPrompt")
                }
            }

            try {
                val response = withTimeout(60_000L) {
                    generativeModel.generateContent(fullPrompt)
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
                appendLine("Kesinlikle uydurma bilgi verme (0 Halüsinasyon politikası). Soruları adım adım, net ve anlaşılır Türkçe ile yanıtla.")
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
     * Step 1 of PDF Ingestion: Scans the book's Answer Key page(s) (usually at the back of the PDF)
     * and returns a verified map of QuestionNumber -> CorrectOption ("A".."E").
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
            appendLine("Her soru numarasını (1, 2, 3...) ve karşısındaki doğru seçeneği (A, B, C, D veya E) eksiksiz ve %100 doğru olarak JSON formatında çıkar.")
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
                            val qNum = item.optInt("questionNumber", 0)
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

    /**
     * Parses a manual or OCR answer key string such as "1-D 2-B 3-E 4-A 5-C 6-D" into a Map<Int, String>.
     */
    fun parseAnswerKeyText(rawKeyText: String): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        val regex = Regex("""(\d{1,2})\s*[-:.)]?\s*([ABCDEabcde])""")
        regex.findAll(rawKeyText).forEach { match ->
            val qNum = match.groupValues[1].toIntOrNull()
            val opt = match.groupValues[2].uppercase()
            if (qNum != null && qNum in 1..120 && opt in listOf("A", "B", "C", "D", "E")) {
                result[qNum] = opt
            }
        }
        return result
    }

    /**
     * Step 2 of PDF Ingestion: Automatically detects all questions on a rendered PDF test page,
     * crops each question into its own high-res PNG file on disk, matches its exact answer from
     * the book's Answer Key map, and assigns its real YKS topic, difficulty, and 6-level wheel category.
     */
    fun extractAndCropQuestionsFromPdfPage(
        context: Context,
        pageBitmap: Bitmap?,
        answerKeyPageBitmaps: List<Bitmap>,
        manualAnswerKeyText: String,
        publisherName: String,
        publisherLogoUri: String,
        defaultExamType: String,
        defaultSubjectName: String,
        onComplete: (List<ExtractedPdfQuestionItem>, Map<Int, String>) -> Unit
    ) {
        _isAutoTagging.value = true
        _batchStatusText.value = "PDF son sayfalardaki Cevap Anahtarı taranıyor..."

        viewModelScope.launch {
            try {
                // 1. Parse manual answer key + extract from end-of-book Answer Key pages
                val manualMap = parseAnswerKeyText(manualAnswerKeyText)
                val extractedBookKeyMap = if (answerKeyPageBitmaps.isNotEmpty()) {
                    extractAnswerKeyFromBookPages(answerKeyPageBitmaps, defaultSubjectName)
                } else {
                    emptyMap()
                }
                val masterAnswerKey = mutableMapOf<Int, String>().apply {
                    putAll(extractedBookKeyMap)
                    putAll(manualMap) // Manual overrides take highest priority if provided
                }

                _batchStatusText.value = "Sayfadaki sorular kırpılıyor, gerçek zorluk & konu etiketleniyor..."

                val allSubjectsCatalogSummary = SoruLabCatalog.allSubjects.joinToString("\n") { subj ->
                    "${subj.examType.name} - ${subj.name}: ${subj.topics.joinToString(", ") { it.name }}"
                }

                if (pageBitmap != null) {
                    val prompt = buildString {
                        appendLine("Sen uzman bir YKS dizgi, soru kırpma ve akademik seviye belirleme motorusun.")
                        appendLine("Görseldeki PDF test sayfasında yer alan TÜM soruları tek tek tespit et.")
                        appendLine("Her soru için:")
                        appendLine("1) Sorunun numarasını (questionNumber) oku.")
                        appendLine("2) Sorunun şekli, öncülleri ve A-E şıklarını tam kapsayan [ymin, xmin, ymax, xmax] (0-1000) kutusunu belirle.")
                        appendLine("3) Sorunun gerçek zorluğunu ('Kolay', 'Orta', 'Zor') ve 6 seviye çarkındaki gerçek seviyesini ('BASLANGIC', 'PEKISTIRME', 'GELISIYORUM', 'OSYM', 'DERECE') uydurmadan, sorunun gerçek işlem ve muhakeme derinliğine göre belirle.")
                        appendLine("4) Aşağıdaki resmi YKS müfredat listesinden en doğru Ders (subjectName) ve Konu (topicName) adını seç:")
                        appendLine(allSubjectsCatalogSummary)
                        appendLine("5) Eğer sayfanın en altında veya kenarında cevap anahtarı şeridi varsa pageAnswerKey içine ekle.")
                    }

                    val response = withTimeout(60_000L) {
                        pageQuestionsExtractorModel.generateContent(
                            content {
                                image(pageBitmap)
                                text(prompt)
                            }
                        )
                    }

                    val rawJson = response.text.orEmpty()
                    val obj = JSONObject(rawJson)

                    // Merge page-bottom answer key if present
                    val pageKeyArr = obj.optJSONArray("pageAnswerKey")
                    if (pageKeyArr != null) {
                        for (i in 0 until pageKeyArr.length()) {
                            val kObj = pageKeyArr.optJSONObject(i) ?: continue
                            val qn = kObj.optInt("questionNumber", 0)
                            val opt = kObj.optString("correctOption", "").uppercase()
                            if (qn > 0 && opt in listOf("A", "B", "C", "D", "E") && !masterAnswerKey.containsKey(qn)) {
                                masterAnswerKey[qn] = opt
                            }
                        }
                    }

                    val questionsArr = obj.optJSONArray("questions")
                    val extractedItems = mutableListOf<ExtractedPdfQuestionItem>()

                    if (questionsArr != null && questionsArr.length() > 0) {
                        for (i in 0 until questionsArr.length()) {
                            val qObj = questionsArr.optJSONObject(i) ?: continue
                            val qNum = qObj.optInt("questionNumber", i + 1).coerceAtLeast(1)
                            val ymin = (qObj.optInt("ymin", 50).coerceIn(0, 950)) / 1000f
                            val xmin = (qObj.optInt("xmin", 40).coerceIn(0, 950)) / 1000f
                            val ymax = (qObj.optInt("ymax", 480).coerceIn((ymin * 1000).toInt() + 80, 1000)) / 1000f
                            val xmax = (qObj.optInt("xmax", 960).coerceIn((xmin * 1000).toInt() + 150, 1000)) / 1000f

                            val savedPath = withContext(Dispatchers.IO) {
                                cropAndSaveQuestionBitmap(
                                    context = context,
                                    source = pageBitmap,
                                    leftNorm = xmin,
                                    topNorm = ymin,
                                    rightNorm = xmax,
                                    bottomNorm = ymax,
                                    questionNumber = qNum
                                )
                            }

                            val matchedAnswerFromBookKey = masterAnswerKey[qNum]
                            val aiDetectedOpt = qObj.optString("detectedCorrectOption", "D").uppercase()
                                .takeIf { it in listOf("A", "B", "C", "D", "E") } ?: "D"
                            val finalCorrectOption = matchedAnswerFromBookKey ?: aiDetectedOpt

                            val wNorm = (xmax - xmin).coerceAtLeast(0.2f)
                            val hNorm = (ymax - ymin).coerceAtLeast(0.15f)
                            val aspect = (wNorm / hNorm).toDouble().coerceIn(0.4, 3.8)

                            extractedItems.add(
                                ExtractedPdfQuestionItem(
                                    id = "ext_${System.currentTimeMillis()}_$qNum",
                                    questionNumber = qNum,
                                    examType = qObj.optString("examType", defaultExamType).uppercase()
                                        .takeIf { it == "AYT" || it == "TYT" } ?: defaultExamType,
                                    subjectName = qObj.optString("subjectName", defaultSubjectName).ifBlank { defaultSubjectName },
                                    topicName = qObj.optString("topicName", "Temel Kavramlar").ifBlank { "Temel Kavramlar" },
                                    difficulty = qObj.optString("difficulty", "Orta")
                                        .takeIf { it in listOf("Kolay", "Orta", "Zor") } ?: "Orta",
                                    targetLevel = qObj.optString("targetLevel", "OSYM")
                                        .takeIf { it in listOf("BASLANGIC", "PEKISTIRME", "GELISIYORUM", "OSYM", "DERECE") } ?: "OSYM",
                                    correctOption = finalCorrectOption,
                                    answerKeyMatched = matchedAnswerFromBookKey != null,
                                    publisherName = publisherName,
                                    publisherLogoUri = publisherLogoUri,
                                    questionText = qObj.optString("questionText", "").ifBlank {
                                        "$publisherName • Soru $qNum"
                                    },
                                    solutionSummary = qObj.optString("solutionSummary", "").ifBlank {
                                        "Kitap cevap anahtarına göre doğru seçenek $finalCorrectOption şıkkıdır."
                                    },
                                    croppedImagePath = savedPath,
                                    cropAspectRatio = aspect,
                                    boxNormalized = listOf(xmin, ymin, xmax, ymax)
                                )
                            )
                        }
                    }

                    if (extractedItems.isNotEmpty()) {
                        onComplete(extractedItems, masterAnswerKey)
                        return@launch
                    }
                }

                onComplete(emptyList(), masterAnswerKey)
            } catch (_: Exception) {
                val fallbackKey = parseAnswerKeyText(manualAnswerKeyText)
                onComplete(emptyList(), fallbackKey)
            } finally {
                _batchStatusText.value = null
                _isAutoTagging.value = false
            }
        }
    }

    fun cropAndSaveQuestionBitmap(
        context: Context,
        source: Bitmap,
        leftNorm: Float,
        topNorm: Float,
        rightNorm: Float,
        bottomNorm: Float,
        questionNumber: Int
    ): String {
        return try {
            val dir = File(context.filesDir, "question_pool_crops")
            if (!dir.exists()) dir.mkdirs()

            val x = (leftNorm.coerceIn(0f, 0.9f) * source.width).toInt()
            val y = (topNorm.coerceIn(0f, 0.9f) * source.height).toInt()
            val w = ((rightNorm.coerceIn(leftNorm + 0.08f, 1f) - leftNorm) * source.width).toInt()
                .coerceIn(1, source.width - x)
            val h = ((bottomNorm.coerceIn(topNorm + 0.08f, 1f) - topNorm) * source.height).toInt()
                .coerceIn(1, source.height - y)

            val cropped = Bitmap.createBitmap(source, x, y, w, h)
            val outFile = File(dir, "crop_${System.currentTimeMillis()}_q$questionNumber.png")
            FileOutputStream(outFile).use { fos ->
                cropped.compress(Bitmap.CompressFormat.PNG, 92, fos)
            }
            outFile.absolutePath
        } catch (_: Exception) {
            ""
        }
    }

    fun autoAnalyzeCroppedQuestion(
        croppedBitmap: Bitmap?,
        hintText: String,
        onResult: (AutoTagSuggestion) -> Unit
    ) {
        _isAutoTagging.value = true
        viewModelScope.launch {
            try {
                val prompt = """
                    Sen bir YKS soru etiketleme uzmanısın. Kırpılan soruyu analiz et ve şu formatta 7 satır döndür:
                    SINAV: TYT veya AYT
                    DERS: Matematik, Geometri, Türkçe, Fizik, Kimya, Biyoloji, Tarih, Coğrafya
                    KONU: Konu adı (ör. Temel Kavramlar, Üçgenler, Fonksiyonlarda Uygulamalar, Bağıl Hareket)
                    ZORLUK: Kolay, Orta veya Zor
                    SEVİYE: BASLANGIC, PEKISTIRME, GELISIYORUM, OSYM veya DERECE
                    CEVAP: A, B, C, D veya E
                    ÖZET: Sorunun metni ve kısa çözüm açıklaması.
                    Ek bilgi: $hintText
                """.trimIndent()

                val response = withTimeout(45_000L) {
                    if (croppedBitmap != null) {
                        generativeModel.generateContent(
                            content {
                                image(croppedBitmap)
                                text(prompt)
                            }
                        )
                    } else {
                        generativeModel.generateContent(prompt)
                    }
                }
                val raw = response.text.orEmpty()
                val exam = if (raw.contains("AYT", ignoreCase = true)) "AYT" else "TYT"
                val diff = when {
                    raw.contains("Zor", ignoreCase = true) -> "Zor"
                    raw.contains("Kolay", ignoreCase = true) -> "Kolay"
                    else -> "Orta"
                }
                val level = when {
                    raw.contains("DERECE", ignoreCase = true) -> "DERECE"
                    raw.contains("BASLANGIC", ignoreCase = true) -> "BASLANGIC"
                    raw.contains("PEKISTIRME", ignoreCase = true) -> "PEKISTIRME"
                    raw.contains("GELISIYORUM", ignoreCase = true) -> "GELISIYORUM"
                    else -> "OSYM"
                }
                val detectedSubject = listOf(
                    "Geometri", "Matematik", "Türkçe", "Fizik", "Kimya", "Biyoloji", "Tarih", "Coğrafya", "Felsefe", "Edebiyat"
                ).firstOrNull { raw.contains(it, ignoreCase = true) } ?: "Matematik"

                val detectedTopic = raw.lines()
                    .firstOrNull { it.startsWith("KONU:", ignoreCase = true) }
                    ?.substringAfter(":")?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: if (detectedSubject == "Geometri") "Üçgenler" else "Temel Kavramlar"

                val detectedOpt = raw.lines()
                    .firstOrNull { it.startsWith("CEVAP:", ignoreCase = true) }
                    ?.substringAfter(":")?.trim()?.take(1)?.uppercase()
                    ?.takeIf { it in listOf("A", "B", "C", "D", "E") }
                    ?: "D"

                onResult(
                    AutoTagSuggestion(
                        examType = exam,
                        subjectName = detectedSubject,
                        topicName = detectedTopic,
                        difficulty = diff,
                        targetLevel = level,
                        correctOption = detectedOpt,
                        questionStemSummary = hintText.ifBlank {
                            raw.lines().firstOrNull { it.startsWith("ÖZET:", ignoreCase = true) }
                                ?.substringAfter(":")?.trim()
                                .orEmpty()
                        },
                        solutionHint = raw
                    )
                )
            } catch (_: Exception) {
                onResult(
                    AutoTagSuggestion(
                        examType = "TYT",
                        subjectName = "Matematik",
                        topicName = "Temel Kavramlar",
                        difficulty = "Orta",
                        targetLevel = "OSYM",
                        correctOption = "D",
                        questionStemSummary = hintText,
                        solutionHint = ""
                    )
                )
            } finally {
                _isAutoTagging.value = false
            }
        }
    }
}
