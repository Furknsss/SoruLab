package com.example.util

import org.junit.Assert.*
import org.junit.Test

class PdfCropQualityTest {
    @Test fun `merged seventh and eighth questions must not pass quality gate`() {
        val text = "Eşik değer ve eşik değer...\nA) Starling\nB) ...\nE) Ayrılma\n8. Duyu organlarından gelen bilgileri...\nA) Talamus"
        assertNotNull(PdfCropQuality.warning(text, 7))
    }
    @Test fun `the correct printed question number does not cause a false alarm`() {
        val text = "7. Eşik değerden...\nA) A\nB) B\nC) C\nD) D\nE) E"
        assertNull(PdfCropQuality.warning(text, 7))
    }
    @Test fun `unreadable question is not treated as ready`() {
        assertNotNull(PdfCropQuality.warning("   ", 3))
    }
    @Test fun `missing original number is not silently matched to answer key`() {
        assertNotNull(PdfCropQuality.warning("Sorunun kökü ve şıklar", null))
    }

    @Test fun `missing A-E answer options cannot enter a scored question pool`() {
        val stem = "3. Soru metni\nA) Bir\nB) İki"
        assertNotNull(PdfCropQuality.warning(stem, 3))
    }

}
