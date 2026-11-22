package com.example.viewmodel

import androidx.compose.runtime.Immutable
import com.example.data.YksQuestion
import org.json.JSONObject

@Immutable
data class InteractiveSolutionStep(
    val stepNumber: Int,
    val title: String,
    val explanation: String,
    val mathExpression: String = "",
    val whyThisStep: String = "",
    val trapWarning: String = ""
)

@Immutable
data class InteractiveSolutionPlan(
    val questionId: String = "",
    val coreConcept: String = "",
    val firstGlanceStrategy: String = "",
    val steps: List<InteractiveSolutionStep> = emptyList(),
    val wrongOptionsAnalysis: Map<String, String> = emptyMap(),
    val osymShortcut: String = "",
    val finalAnswer: String = "",
    val verificationCheck: String = "",
    val isFromFallback: Boolean = false
)

/** Shared quality contract for every Sorulab AI response. */
internal object SorulabAiQuality {
    const val SYSTEM_INSTRUCTION =
        "Sen Sorulab AI'sın: Türkiye YKS müfredatına hakim, ölçme-değerlendirme bilen, sakin ve kanıt odaklı bir öğretmensin.\n" +
            "Kurallar:\n" +
            "1. Yalnızca verilen soru metni, görsel ve cevap anahtarındaki bilgilere dayan; eksik bilgiyi asla uydurma.\n" +
            "2. Cevap anahtarı ile çözüm çelişirse bunu açıkça belirt, kesinmiş gibi yanlış çözüm yazma.\n" +
            "3. Önce kısa sonuç, sonra kullanılan kazanım/formül, sonra numaralı işlem adımları, en sonda ÖSYM tuzak notu ver.\n" +
            "4. Matematiksel ifadeleri okunabilir yaz; birimleri, işaretleri ve tanım koşullarını kontrol et.\n" +
            "5. Öğrenci seviyesine uygun, Türkçe ve gereksiz uzunluk olmadan anlat.\n" +
            "6. Soru çözülemiyorsa nedenini ve hangi bilginin eksik olduğunu söyle; tahminde bulunma."

    const val INTERACTIVE_SOLUTION_INSTRUCTION =
        "Sen SoruLab'ın video çözümden bile daha anlaşılır interaktif YKS öğretmenisin.\n" +
            "Öğrenciye Photomath netliğinde adım adım işlem, Khanmigo sıcaklığında 'Neden bu adımı attık?' mantığı ve ÖSYM koçu gibi pratik sınav stratejisi sunacaksın.\n" +
            "Kurallar:\n" +
            "1. Yalnızca verilen soru görseli, soru metni ve cevap anahtarına dayan; eksik bilgi uydurma.\n" +
            "2. Her adımı küçük, sindirilebilir ve tek bir işlem fikrine odaklanan kartlara böl (en az 2, en fazla 6 adım).\n" +
            "3. mathExpression alanına sadece o adımın temel formülünü/işlemini temiz LaTeX formatında yaz (örn. \\frac{x^2 - 4}{x - 2} = x + 2 veya f'(x) = 3x^2 - 6x = 0). Dolar işareti ($) ekleme. Sözel soruysa boş bırak.\n" +
            "4. whyThisStep alanında öğrencinin 'Bunu nereden bildik, neden bu hamleyi yaptık?' sorusunu 1-2 samimi cümleyle yanıtla.\n" +
            "5. trapWarning alanında bu adımda öğrencilerin en sık yaptığı işaret/birim/kavram hatasını kısaca uyar.\n" +
            "6. wrongOptionsAnalysis dizisinde doğru şık dışındaki diğer şıkların (A-E) neden yanlış veya nasıl bir çeldirici olduğunu tek tek açıkla.\n" +
            "7. osymShortcut alanında sınavda 15 saniyede çözüme götüren pratik yolu veya eleme taktiğini yaz.\n" +
            "8. verificationCheck alanında bulunan cevabın kısa sağlamasını yap.\n" +
            "9. Tüm açıklamalar sade, akıcı ve doğal Türkçe olsun."

    fun questionContext(question: YksQuestion): String = buildString {
        appendLine("Yayın: ${question.publisherName.ifBlank { "Belirtilmemiş" }}")
        if (question.subject.isNotBlank()) appendLine("Ders: ${question.subject}")
        appendLine("Konu: ${question.topicHeader.ifBlank { "Belirtilmemiş" }}")
        appendLine("Soru numarası: ${question.number}")
        appendLine("Cevap anahtarı: ${question.correctOption.ifBlank { "Belirtilmemiş" }}")
        if (question.stemTop.isNotBlank()) appendLine("Soru kökü: ${question.stemTop}")
        if (question.bulletsOrSteps.isNotEmpty()) {
            appendLine("Öncüller ve veriler:")
            question.bulletsOrSteps.forEach { appendLine("- $it") }
        }
        if (question.stemBottom.isNotBlank()) appendLine("Soru yönergesi: ${question.stemBottom}")
        if (question.options.isNotEmpty()) {
            appendLine("Şıklar:")
            question.options.entries.sortedBy { it.key }.forEach { (k, v) ->
                if (v.isNotBlank()) appendLine("$k) $v")
            }
        }
        if (question.stepByStepSolution.isNotBlank()) {
            appendLine("Mevcut referans çözüm: ${question.stepByStepSolution}")
        }
    }

    fun simplerStepPrompt(step: InteractiveSolutionStep, question: YksQuestion): String = buildString {
        appendLine(SYSTEM_INSTRUCTION)
        appendLine("Öğrenci aşağıdaki çözüm adımını anlamadı ve çok daha basit, sıfırdan, günlük dille ve ara işlemleri atlamadan anlatmanı istiyor.")
        appendLine(questionContext(question))
        appendLine("--- ÖĞRENCİNİN TAKILDIĞI ADIM ---")
        appendLine("Adım ${step.stepNumber}: ${step.title}")
        appendLine("Mevcut anlatım: ${step.explanation}")
        if (step.mathExpression.isNotBlank()) appendLine("İşlem: ${step.mathExpression}")
        if (step.whyThisStep.isNotBlank()) appendLine("Gerekçe: ${step.whyThisStep}")
        appendLine("Görevin: Bu adımı 3-5 kısa cümlede, hiç ara basamak atlamadan, gerekirse küçük bir sayısal benzetmeyle öğrencinin 'Ha şimdi anladım!' diyeceği şekilde anlat.")
    }

    fun fallbackNotice(question: YksQuestion): String =
        "Bu sorunun AI çözümü şu an doğrulanamadı. Cevap anahtarına göre doğru seçenek " +
            "${question.correctOption.ifBlank { "belirtilmemiş" }}. Görseli ve soru metnini kontrol ederek tekrar deneyebilirsin."

    /**
     * Converts LaTeX expressions into natural spoken Turkish for TextToSpeech.
     */
    fun latexToSpokenTurkish(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw
            .replace("$$", "")
            .replace("$", "")
            .replace("\\left", "")
            .replace("\\right", "")
            .replace("\\,", " ")
            .replace("\\;", " ")
            .replace("\\quad", " ")

        // Fractions: \frac{a}{b} -> a bölü b
        val fracRegex = Regex("""\\(?:d|t)?frac\{([^{}]+)\}\{([^{}]+)\}""")
        repeat(3) {
            text = fracRegex.replace(text) { m -> "${m.groupValues[1]} bölü ${m.groupValues[2]}" }
        }
        // Square roots: \sqrt{x} -> kök içinde x
        val sqrtRegex = Regex("""\\sqrt\{([^{}]+)\}""")
        text = sqrtRegex.replace(text) { m -> "kök içinde ${m.groupValues[1]}" }

        // Common symbols and operators
        val replacements = listOf(
            "\\cdot" to " çarpı ",
            "\\times" to " çarpı ",
            "\\div" to " bölü ",
            "\\pm" to " artı eksi ",
            "\\le" to " küçük eşittir ",
            "\\leq" to " küçük eşittir ",
            "\\ge" to " büyük eşittir ",
            "\\geq" to " büyük eşittir ",
            "\\ne" to " eşit değildir ",
            "\\neq" to " eşit değildir ",
            "\\approx" to " yaklaşık eşittir ",
            "\\infty" to " sonsuz ",
            "\\pi" to " pi ",
            "\\alpha" to " alfa ",
            "\\beta" to " beta ",
            "\\theta" to " teta ",
            "\\Delta" to " delta ",
            "\\sum" to " toplam ",
            "\\int" to " integral ",
            "\\lim" to " limit ",
            "\\to" to " giderken ",
            "\\rightarrow" to " ise ",
            "\\Rightarrow" to " ise ",
            "\\Leftrightarrow" to " ancak ve ancak ",
            "^2" to " kare",
            "^{2}" to " kare",
            "^3" to " küp",
            "^{3}" to " küp",
            "^{-1}" to " üzeri eksi bir",
            "f'(x)" to "f türev x",
            "f''(x)" to "f ikinci türev x",
            "=" to " eşittir ",
            "<" to " küçüktür ",
            ">" to " büyüktür ",
            "+" to " artı ",
            "-" to " eksi ",
            "*" to " çarpı ",
            "/" to " bölü ",
            "^" to " üzeri "
        )
        for ((from, to) in replacements) {
            text = text.replace(from, to)
        }
        text = text.replace(Regex("""\\[a-zA-Z]+"""), " ")
        text = text.replace("{", " ").replace("}", " ")
        return text.replace(Regex("""\s+"""), " ").trim()
    }

    /**
     * Parses any existing stepByStepSolution text or question metadata into a complete
     * InteractiveSolutionPlan so the Interactive Solution Board is NEVER empty even offline.
     */
    fun parseFallbackPlanFromQuestion(question: YksQuestion): InteractiveSolutionPlan {
        val topic = question.topicHeader.ifBlank { question.subject.ifBlank { "YKS Kazanımı" } }
        val correctOpt = question.correctOption.trim().uppercase().ifBlank { "Belirtilmemiş" }
        val raw = question.stepByStepSolution.trim()

        var coreConcept = ""
        var trapNote = ""
        var solutionBody = raw

        if (raw.isNotBlank()) {
            val kritikMatch = Regex("""(?:📌\s*)?Kritik Bilgi[^:]*:\s*(.+?)(?=(?:✏️|⚠️|Adım Adım|ÖSYM Tuzak|$))""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .find(raw)
            if (kritikMatch != null) {
                coreConcept = kritikMatch.groupValues[1].trim()
            }
            val trapMatch = Regex("""(?:⚠️\s*)?ÖSYM Tuzak[^:]*:\s*(.+)$""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .find(raw)
            if (trapMatch != null) {
                trapNote = trapMatch.groupValues[1].trim()
            }
            val bodyMatch = Regex("""(?:✏️\s*)?Adım Adım Çözüm[^:]*:\s*(.+?)(?=(?:⚠️|ÖSYM Tuzak|$))""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .find(raw)
            if (bodyMatch != null) {
                solutionBody = bodyMatch.groupValues[1].trim()
            }
        }

        val parsedSteps = mutableListOf<InteractiveSolutionStep>()
        if (solutionBody.isNotBlank()) {
            val stepSplitRegex = Regex("""(?m)(?=^\s*(?:\d+\.\s*Adım|Adım\s*\d+|\d+\s*[.):])\s*)""")
            val chunks = solutionBody.split(stepSplitRegex)
                .map { it.trim() }
                .filter { it.isNotBlank() }

            if (chunks.size >= 2) {
                chunks.take(6).forEachIndexed { idx, chunk ->
                    val cleaned = chunk.replace(
                        Regex("""^\s*(?:\d+\.\s*Adım|Adım\s*\d+|\d+\s*[.):])\s*[:\-]?\s*""", RegexOption.IGNORE_CASE),
                        ""
                    ).trim()
                    val title = when (idx) {
                        0 -> "Verilenleri ve Kuralı Belirleme"
                        chunks.lastIndex -> "Sonucu Bulma ve Şık Eşleştirme"
                        else -> "Ara İşlem ve Sadeleştirme"
                    }
                    parsedSteps.add(
                        InteractiveSolutionStep(
                            stepNumber = idx + 1,
                            title = title,
                            explanation = cleaned,
                            mathExpression = extractFirstMathLikeSegment(cleaned),
                            whyThisStep = if (idx == 0) {
                                "Soruda verilen tüm sayısal ve sözel ipuçlarını tek bir denklemde toplamak çözüm yolunu netleştirir."
                            } else {
                                "Bir önceki adımda kurulan bağıntıyı sadeleştirerek aranan büyüklüğe doğrudan ulaşırız."
                            },
                            trapWarning = if (idx == chunks.lastIndex && trapNote.isNotBlank()) trapNote else ""
                        )
                    )
                }
            } else {
                val sentences = solutionBody.split(Regex("""(?<=[.!?])\s+"""))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                if (sentences.size >= 2) {
                    val grouped = sentences.chunked(((sentences.size + 2) / 3).coerceAtLeast(1))
                    grouped.take(4).forEachIndexed { idx, part ->
                        val text = part.joinToString(" ")
                        parsedSteps.add(
                            InteractiveSolutionStep(
                                stepNumber = idx + 1,
                                title = when (idx) {
                                    0 -> "Sorunun Kurulumu"
                                    grouped.lastIndex -> "Nihai Sonuç"
                                    else -> "İşlem Adımı ${idx + 1}"
                                },
                                explanation = text,
                                mathExpression = extractFirstMathLikeSegment(text),
                                whyThisStep = "Adımları bölerek ilerlemek işlem hatası riskini azaltır.",
                                trapWarning = if (idx == grouped.lastIndex && trapNote.isNotBlank()) trapNote else ""
                            )
                        )
                    }
                }
            }
        }

        if (parsedSteps.isEmpty()) {
            parsedSteps.addAll(
                listOf(
                    InteractiveSolutionStep(
                        stepNumber = 1,
                        title = "Verilenleri ve İsteneni Ayıklama",
                        explanation = "Soru metnindeki ve görseldeki $topic verilerini not edelim. İstenen ifadeyi netleştirip tanım koşullarını kontrol edelim.",
                        whyThisStep = "ÖSYM sorularında verilen sınır koşullarını baştan görmek yanlış yola sapmayı engeller.",
                        trapWarning = "Soru kökündeki 'kesinlikle', 'olabilir' veya 'değildir' ifadelerine dikkat et."
                    ),
                    InteractiveSolutionStep(
                        stepNumber = 2,
                        title = "$topic Temel Bağıntısını Uygulama",
                        explanation = "Belirlediğimiz verileri $topic konusunun temel formül ve kurallarına yerleştirip sadeleştirme yapalım.",
                        whyThisStep = "Bilinmeyeni tek başına bırakmak için en doğrudan kazanım bağıntısı kullanılır.",
                        trapWarning = trapNote.ifBlank { "İşaret değişimleri ve birim dönüşümlerini bu aşamada mutlaka kontrol et." }
                    ),
                    InteractiveSolutionStep(
                        stepNumber = 3,
                        title = "Sonucu Doğrulama ve Doğru Şıkkı Seçme",
                        explanation = "İşlem sonucunda elde edilen değeri soru kökündeki koşullarla karşılaştırdığımızda doğru yanıt $correctOpt seçeneği olur.",
                        whyThisStep = "Sonucu şıklarla eşleştirmeden önce soru köküne tekrar bakmak dikkatsizlik hatalarını önler."
                    )
                )
            )
        }

        val allOptions = listOf("A", "B", "C", "D", "E")
        val wrongOptions = linkedMapOf<String, String>()
        allOptions.filter { it != correctOpt }.forEach { opt ->
            val optText = question.options[opt].orEmpty().trim()
            wrongOptions[opt] = if (optText.isNotBlank()) {
                "'$optText' ifadesi soru kökündeki koşulları veya işlem sonucunu sağlamadığı için çeldiricidir."
            } else {
                "Bu seçenek, işlemde işaret/koşul hatası yapıldığında ulaşılan çeldirici şıktır; $correctOpt seçeneği tüm koşulları sağlar."
            }
        }

        return InteractiveSolutionPlan(
            questionId = question.id,
            coreConcept = coreConcept.ifBlank {
                "$topic kazanımında temel bağıntıların ve sınır koşullarının doğru uygulanması."
            },
            firstGlanceStrategy = "Bu soruyu sınavda gördüğünde ilk 5 saniyede $topic anahtar kelimelerini ve verilen sınır şartlarını işaretle; doğrudan istenen niceliğe giden en kısa bağıntıyı seç.",
            steps = parsedSteps,
            wrongOptionsAnalysis = wrongOptions,
            osymShortcut = "Önce soru kökündeki kısıtlamalara uymayan uç şıkları ele; ardından $topic temel kuralıyla kalan seçenekler arasından $correctOpt şıkkını hızlıca doğrula.",
            finalAnswer = if (correctOpt != "Belirtilmemiş") "Doğru Cevap: $correctOpt Şıkkı" else "Cevap Anahtarı Kontrol Edilmeli",
            verificationCheck = trapNote.ifBlank {
                "Bulduğun sonucu soru kökündeki tüm öncül ve birim şartlarında yerine koyarak $correctOpt seçeneğinin tutarlılığını doğrula."
            },
            isFromFallback = true
        )
    }

    fun parseInteractiveSolutionJson(
        rawText: String,
        question: YksQuestion
    ): InteractiveSolutionPlan? {
        if (rawText.isBlank()) return null
        val cleaned = rawText.trim().let { trimmed ->
            if (trimmed.startsWith("```")) {
                trimmed.substringAfter("\n").substringBeforeLast("```").trim()
            } else {
                trimmed
            }
        }
        val obj = runCatching { JSONObject(cleaned) }.getOrNull() ?: return null

        val stepsArray = obj.optJSONArray("steps")
        val steps = mutableListOf<InteractiveSolutionStep>()
        if (stepsArray != null) {
            for (i in 0 until stepsArray.length()) {
                val item = stepsArray.optJSONObject(i) ?: continue
                val title = item.optString("title", "").trim()
                val explanation = item.optString("explanation", "").trim()
                if (title.isBlank() && explanation.isBlank()) continue
                val stepNum = item.optInt("stepNumber", i + 1).takeIf { it > 0 } ?: (i + 1)
                val rawMath = item.optString("mathExpression", "").trim()
                    .removePrefix("$$").removeSuffix("$$")
                    .removePrefix("$").removeSuffix("$")
                    .trim()
                steps.add(
                    InteractiveSolutionStep(
                        stepNumber = stepNum,
                        title = title.ifBlank { "Adım $stepNum" },
                        explanation = explanation.ifBlank { title },
                        mathExpression = rawMath,
                        whyThisStep = item.optString("whyThisStep", "").trim(),
                        trapWarning = item.optString("trapWarning", "").trim()
                    )
                )
            }
        }
        if (steps.isEmpty()) return null

        val wrongOptionsMap = linkedMapOf<String, String>()
        val wrongArr = obj.optJSONArray("wrongOptionsAnalysis")
        if (wrongArr != null) {
            for (i in 0 until wrongArr.length()) {
                val entry = wrongArr.optJSONObject(i) ?: continue
                val opt = entry.optString("option", "").trim().uppercase()
                val reason = entry.optString("reason", "").trim()
                if (opt in listOf("A", "B", "C", "D", "E") && reason.isNotBlank()) {
                    wrongOptionsMap[opt] = reason
                }
            }
        } else {
            val wrongObj = obj.optJSONObject("wrongOptionsAnalysis")
            if (wrongObj != null) {
                val keys = wrongObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next().trim().uppercase()
                    val reason = wrongObj.optString(key, "").trim()
                    if (key in listOf("A", "B", "C", "D", "E") && reason.isNotBlank()) {
                        wrongOptionsMap[key] = reason
                    }
                }
            }
        }

        val fallback = parseFallbackPlanFromQuestion(question)
        val mergedWrongOptions = if (wrongOptionsMap.isNotEmpty()) {
            wrongOptionsMap
        } else {
            fallback.wrongOptionsAnalysis
        }

        return InteractiveSolutionPlan(
            questionId = question.id,
            coreConcept = obj.optString("coreConcept", "").trim().ifBlank { fallback.coreConcept },
            firstGlanceStrategy = obj.optString("firstGlanceStrategy", "").trim().ifBlank { fallback.firstGlanceStrategy },
            steps = steps.sortedBy { it.stepNumber },
            wrongOptionsAnalysis = mergedWrongOptions,
            osymShortcut = obj.optString("osymShortcut", "").trim().ifBlank { fallback.osymShortcut },
            finalAnswer = obj.optString("finalAnswer", "").trim().ifBlank { fallback.finalAnswer },
            verificationCheck = obj.optString("verificationCheck", "").trim().ifBlank { fallback.verificationCheck },
            isFromFallback = false
        )
    }

    private fun extractFirstMathLikeSegment(text: String): String {
        val equationRegex = Regex("""[A-Za-z0-9()'\^_/+\-]+\s*=\s*[A-Za-z0-9()'\^_/+\-\s.,]+""")
        val match = equationRegex.find(text)?.value?.trim().orEmpty()
        return if (match.length in 4..64) match.trimEnd('.', ',', ';') else ""
    }
}
