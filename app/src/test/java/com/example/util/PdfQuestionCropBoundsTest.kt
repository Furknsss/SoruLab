package com.example.util

import org.junit.Assert.*
import org.junit.Test

class PdfQuestionCropBoundsTest {
    @Test fun `question 7 cannot absorb question 8 when next number OCR is missed`() {
        // Approximate vertical positions from the user's scanned two-column sample.
        val q7 = PdfQuestionCropBounds.forAnchor(
            anchorTop = 1050, nextAnchorTop = null,
            lastOptionBottom = 1370, bodyTop = 200, bodyBottom = 2250,
            pageHeight = 2400
        )
        assertNotNull(q7)
        assertTrue(q7!!.top >= 1030)
        assertTrue("Must stop before the following question", q7.bottom < 1540)
    }

    @Test fun `third question starts near its own printed number not preceding options`() {
        val q3 = PdfQuestionCropBounds.forAnchor(
            anchorTop = 1860, nextAnchorTop = null,
            lastOptionBottom = 2160, bodyTop = 170, bodyBottom = 2340,
            pageHeight = 2400
        )
        assertNotNull(q3)
        assertTrue(q3!!.top >= 1840)
        assertTrue(q3.bottom <= 2250)
    }

    @Test fun `next printed number is a strict boundary even if options extend too far`() {
        val a = PdfQuestionCropBounds.forAnchor(
            anchorTop = 800, nextAnchorTop = 1250,
            lastOptionBottom = 1490, bodyTop = 140, bodyBottom = 2350,
            pageHeight = 2400
        )
        assertNotNull(a)
        assertTrue(a!!.bottom < 1250)
    }

    @Test fun `invalid region is rejected rather than fabricated`() {
        assertNull(PdfQuestionCropBounds.forAnchor(
            anchorTop = 2350, nextAnchorTop = null, lastOptionBottom = null,
            bodyTop = 100, bodyBottom = 2350, pageHeight = 2400
        ))
    }
}
