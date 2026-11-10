package com.example.viewmodel

import com.example.data.CustomQuestionRecord
import com.example.data.SoruLabCatalog
import com.example.data.YksTopic
import java.util.Locale

/**
 * Fail closed on independent, high-confidence evidence that disagrees with
 * the imported catalog topic. We do not invent a corrected topic from OCR.
 * Legacy wrong labels are filtered at query time, so reinstalling books is not
 * required. Unknown text is not falsely advertised as semantically verified.
 */
internal object TopicIntegrityPolicy {
    private fun normalize(value: String): String = value.trim()
        .lowercase(Locale.forLanguageTag("tr-TR"))
        .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u')
        .replace('ş', 's').replace('ö', 'o').replace('ç', 'c')

    private val plantAnchors = listOf(
        "bitki", "stoma", "hidatot", "lentisel", "floem", "ksilem",
        "meristem", "kloroplast", "fotosentez", "kutikula",
        "kollenki", "sklerenk", "periderm", "fototropizma",
        "auksin", "oksin", "giberellin", "sitokinin", "absisik",
        "yaprak", "transpirasyon", "fotosistem", "tohum", "cicek",
        "odun borusu", "soymuk borusu", "bitkisel"
    )

    private fun independentlyPlantSpecific(record: CustomQuestionRecord): Boolean {
        val text = normalize(record.questionText + " " + record.learningOutcome)
        return plantAnchors.any { anchor ->
            // Phrase boundary prevents "tohum" being found in unrelated words.
            Regex("(?<![a-z])" + Regex.escape(anchor) + "[a-z]*").containsMatchIn(text)
        }
    }

    fun isSuitable(record: CustomQuestionRecord, topic: YksTopic): Boolean {
        if (!record.subjectId.equals(topic.subjectId, true)) return false
        if (!record.topicId.equals(topic.id, true) &&
            !(record.topicId.isBlank() && record.topicName.equals(topic.name, true))) return false

        val targetIsEndocrine = topic.id == "endokrin_sistem"
        if (targetIsEndocrine && independentlyPlantSpecific(record)) return false

        // A chapter label embedded in a source test is independent from the
        // potentially guessed topic in ZIP metadata. Compare exact catalog
        // topics only; don't reject on fuzzy or partial strings.
        val printedSection = record.sourceTestId
            .takeIf { it.contains('~') }?.substringBefore('~')?.trim().orEmpty()
        if (printedSection.isNotBlank()) {
            val published = SoruLabCatalog.allSubjects
                .firstOrNull { it.id.equals(topic.subjectId, true) }
                ?.topics?.firstOrNull { normalize(it.name) == normalize(printedSection) }
            if (published != null && published.id != topic.id) return false
        }
        // Never use the mutated question.topicHeader: generated tests overwrite
        // it with the requested topic. Revalidate the unaltered source record.
        return true
    }

}
