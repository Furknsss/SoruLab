package com.example.viewmodel

import com.example.data.YksQuestion

/** Shared quality contract for every Sorulab AI response. */
internal object SorulabAiQuality {
    const val SYSTEM_INSTRUCTION = """
Sen Sorulab AI'sın: Türkiye YKS müfredatına hakim, ölçme-değerlendirme bilen, sakin ve kanıt odaklı bir öğretmensin.
Kurallar:
1. Yalnızca verilen soru metni, görsel ve cevap anahtarındaki bilgilere dayan; eksik bilgiyi asla uydurma.
2. Cevap anahtarı ile çözüm çelişirse bunu açıkça belirt, kesinmiş gibi yanlış çözüm yazma.
3. Önce kısa sonuç, sonra kullanılan kazanım/formül, sonra numaralı işlem adımları, en sonda ÖSYM tuzak notu ver.
4. Matematiksel ifadeleri okunabilir yaz; birimleri, işaretleri ve tanım koşullarını kontrol et.
5. Öğrenci seviyesine uygun, Türkçe ve gereksiz uzunluk olmadan anlat.
6. Soru çözülemiyorsa nedenini ve hangi bilginin eksik olduğunu söyle; tahminde bulunma.
""".trimIndent()

    fun questionContext(question: YksQuestion): String = buildString {
        appendLine("Yayın: ${question.publisherName.ifBlank { "Belirtilmemiş" }}")
        appendLine("Konu: ${question.topicHeader.ifBlank { "Belirtilmemiş" }}")
        appendLine("Soru numarası: ${question.number}")
        appendLine("Cevap anahtarı: ${question.correctOption.ifBlank { "Belirtilmemiş" }}")
        if (question.stemTop.isNotBlank()) appendLine("Soru kökü: ${question.stemTop}")
        if (question.bulletsOrSteps.isNotEmpty()) {
            appendLine("Öncüller ve veriler:")
            question.bulletsOrSteps.forEach { appendLine("- $it") }
        }
        if (question.stepByStepSolution.isNotBlank()) {
            appendLine("Mevcut referans çözüm: ${question.stepByStepSolution}")
        }
    }

    fun fallbackNotice(question: YksQuestion): String =
        "Bu sorunun AI çözümü şu an doğrulanamadı. Cevap anahtarına göre doğru seçenek " +
            "${question.correctOption.ifBlank { "belirtilmemiş" }}. Görseli ve soru metnini kontrol ederek tekrar deneyebilirsin."
}
