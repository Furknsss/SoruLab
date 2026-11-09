package com.example.viewmodel

import java.util.Locale

/**
 * A twin is a parameter/term transformation of a specific source problem,
 * NOT another question selected solely because it belongs to the same topic.
 *
 * These checks are intentionally fail-closed. AI approval is supporting evidence,
 * not a mathematical proof. The app never labels a rejected draft as verified.
 */
internal data class TwinSourceAnalysis(
    val transcript: String,
    val subSkill: String,
    val exactQuestionPattern: String,
    val orderedSolutionOperations: String,
    val independentlySolvedOriginalOption: String,
    val legible: Boolean,
    val canWriteStandaloneTextTwin: Boolean
)

internal data class TwinQuestionDraft(
    val stem: String,
    val options: Map<String, String>,
    val correctOption: String,
    val explanation: String,
    val transformationLog: String,
    val originalPatternKept: String,
    val orderedOperationsKept: String
)

internal data class TwinIndependentAudit(
    val solvedOptionWithoutAnswerKey: String,
    val sameSpecificSubSkill: Boolean,
    val sameProblemArchitecture: Boolean,
    val sameOrderedReasoning: Boolean,
    val transformedMeaningfulParameters: Boolean,
    val independentOfUnavailableDiagram: Boolean,
    val singleUnambiguousAnswer: Boolean,
    val sufficientAndConsistentInformation: Boolean,
    val notSourceCopy: Boolean
)

internal object TwinQuestionQualityGate {
    private val labels = setOf("A", "B", "C", "D", "E")
    private val turkish = Locale.forLanguageTag("tr-TR")
    private fun normalized(raw: String): String = raw
        .lowercase(turkish)
        .replace(Regex("""\s+"""), " ")
        .trim()

    fun checkSource(source: TwinSourceAnalysis, verifiedOriginalOption: String?): String? {
        if (!source.legible || source.transcript.length < 25) return "Kaynak soru net okunamadı."
        if (!source.canWriteStandaloneTextTwin) return "Bu soru çizim gerektiriyor; eksik görselle sahte ikiz üretilemez."
        if (source.subSkill.trim().length < 6 || source.exactQuestionPattern.trim().length < 12 ||
            source.orderedSolutionOperations.trim().length < 12
        ) return "Kaynak sorunun özgün çözüm şablonu güvenilir biçimde çıkarılamadı."
        val originalOption = source.independentlySolvedOriginalOption.uppercase(Locale.ROOT)
        if (originalOption !in labels) {
            return "Kaynak sorunun cevabı bağımsız çözüme göre doğrulanamadı."
        }
        if (verifiedOriginalOption != null &&
            originalOption != verifiedOriginalOption.uppercase(Locale.ROOT)
        ) return "Kaynak cevap anahtarı ile bağımsız çözüm çelişiyor."
        return null
    }

    fun checkDraft(source: TwinSourceAnalysis, draft: TwinQuestionDraft): String? {
        val cleanStem = normalized(draft.stem)
        if (cleanStem.length !in 35..3000) return "Yeni soru kökü eksik veya aşırı uzun."
        if (cleanStem == normalized(source.transcript)) return "Kaynak soru aynen kopyalanmış."
        if (draft.options.keys != labels) return "Beş şıkkın tamamı bulunmalı."
        val values = draft.options.values.map(::normalized)
        if (values.any { it.length !in 1..220 } || values.distinct().size != 5) {
            return "Şıklar boş, tekrar ediyor veya okunamayacak kadar uzun."
        }
        if (draft.correctOption !in labels) return "Yeni doğru cevap A-E arasında değil."
        if (draft.explanation.trim().length < 40) return "Hesaplanmış adım adım çözüm eksik."
        if (draft.transformationLog.trim().length < 16) return "Değiştirilen nicelik ve koşullar belgelenmemiş."
        if (draft.originalPatternKept.trim().length < 12 ||
            draft.orderedOperationsKept.trim().length < 12) {
            return "Özgün soru kalıbı ve çözüm adımları eşleştirilmemiş."
        }
        // A direct copy with a different answer key is not a twin.
        // The PDF transcription may put the A-E options inline after the
        // question stem, not on a new line. Compare against the pure stem too.
        val firstAnswerMarker = Regex("""(?i)(?:^|\s)A[).]\s*""")
            .find(source.transcript)?.range?.first ?: source.transcript.length
        val originalStemWithoutOptions = source.transcript.take(firstAnswerMarker).trim()
        if (originalStemWithoutOptions.isNotBlank() &&
            cleanStem == normalized(originalStemWithoutOptions)) {
            return "Soru kökü kaynakla aynı."
        }
        val sourceNumbers = Regex("""\d+(?:[.,]\d+)?""").findAll(source.transcript)
            .map { it.value }.toSet()
        val twinNumbers = Regex("""\d+(?:[.,]\d+)?""").findAll(draft.stem)
            .map { it.value }.toSet()
        if (sourceNumbers.isNotEmpty() && twinNumbers.isNotEmpty() &&
            sourceNumbers == twinNumbers) {
            return "Kaynağın sayısal verileri hiç değişmemiş."
        }
        val danglingVisualReferences = listOf(
            "yukarıdaki şekil", "aşağıdaki şekil", "verilen grafiğe göre",
            "şekilde gösterilen", "yandaki grafik", "yukarıdaki tablo",
            "aşağıdaki görsel", "şekle göre"
        )
        if (danglingVisualReferences.any { cleanStem.contains(it) }) {
            return "Yeni soruda gösterilmeyen bir görsele gönderme var."
        }
        return null
    }

    fun checkIndependentAudit(draft: TwinQuestionDraft, audit: TwinIndependentAudit): String? {
        if (audit.solvedOptionWithoutAnswerKey != draft.correctOption) {
            return "Bağımsız çözücü cevap anahtarıyla aynı sonucu bulamadı."
        }
        if (!audit.sameSpecificSubSkill || !audit.sameProblemArchitecture ||
            !audit.sameOrderedReasoning) {
            return "Bu soru aynı konuda olsa da orijinalin birebir çözüm kalıbını taşımıyor."
        }
        if (!audit.transformedMeaningfulParameters || !audit.notSourceCopy) {
            return "Sorunun verileri ve terimleri özgün biçimde dönüştürülmemiş."
        }
        if (!audit.independentOfUnavailableDiagram) {
            return "Yeni sorunun çözümü eksik bir şekil veya tabloya bağlı."
        }
        if (!audit.singleUnambiguousAnswer || !audit.sufficientAndConsistentInformation) {
            return "Tek ve tutarlı bir doğru seçenek bağımsız olarak onaylanmadı."
        }
        return null
    }
}
