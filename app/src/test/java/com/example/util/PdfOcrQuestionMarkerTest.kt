package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfOcrQuestionMarkerTest {
    @Test fun `TYT and AYT deneme question numbers are not limited to 39`() {
        assertEquals(1, PdfOcrQuestionMarker.numberAtStart("1. Örnek soru"))
        assertEquals(40, PdfOcrQuestionMarker.numberAtStart("40) Problem"))
        assertEquals(80, PdfOcrQuestionMarker.numberAtStart("80- Bir soru"))
        assertEquals(120, PdfOcrQuestionMarker.numberAtStart("120: Son soru"))
        assertTrue(PdfOcrQuestionMarker.isStandaloneNumberElement("120."))
    }

    @Test fun `unit title and year labels never become question numbers`() {
        assertNull(PdfOcrQuestionMarker.numberAtStart("1. ÜNİTE: Canlılar"))
        assertNull(PdfOcrQuestionMarker.numberAtStart("12. SINIF MATEMATİK"))
        assertNull(PdfOcrQuestionMarker.numberAtStart("2026. Yayınlar"))
        assertNull(PdfOcrQuestionMarker.numberAtStart("Not 3. Soru"))
        assertFalse(PdfOcrQuestionMarker.isStandaloneNumberElement("2026"))
        assertFalse(PdfOcrQuestionMarker.isStandaloneNumberElement("999."))
    }

    @Test fun `number tokens work with punctuation and OCR spaces`() {
        assertTrue(PdfOcrQuestionMarker.isStandaloneNumberElement("  42 )  "))
        assertTrue(PdfOcrQuestionMarker.isStandaloneNumberElement(" 7-"))
        assertEquals(26, PdfOcrQuestionMarker.numberAtStart(" 26 ) grafik inceleme"))
    }
}
