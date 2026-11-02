package com.example.util

/**
 * Fail-closed OCR checks. These checks never guess an answer or declare
 * a page ready merely because the bitmap could be saved.
 */
internal object PdfCropQuality {
    // A separate numbered question start remaining inside a crop is evidence
    // that the next printed question was accidentally merged into this crop.
    // The source's own number may remain if a PDF uses unusual ink/background.
    private val numberedQuestionStart = Regex(
        """(?m)^\s*([1-9]|[1-3][0-9])[.)]\s+(?=\S)"""
    )

    fun warning(ocrText: String, expectedPrintedNumber: Int?): String? {
        if (ocrText.isBlank()) return "Kırpılan sorunun metni OCR ile doğrulanamadı."
        val numbers = numberedQuestionStart.findAll(ocrText)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .distinct().toList()
        if (numbers.any { it != expectedPrintedNumber }) {
            return "Aynı kırpmada başka bir soru numarası algılandı: ${numbers.joinToString()}"
        }
        if (expectedPrintedNumber == null || expectedPrintedNumber <= 0) {
            return "Basılı soru numarası doğrulanamadı."
        }
        return null
    }
}
