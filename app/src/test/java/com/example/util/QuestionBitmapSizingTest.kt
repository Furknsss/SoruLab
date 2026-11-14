package com.example.util

import org.junit.Assert.*
import org.junit.Test

class QuestionBitmapSizingTest {
    @Test fun longNarrowScanRetainsItsReadingWidth() {
        assertEquals(1, questionBitmapSampleSize(650, 5000))
        assertEquals(2, questionBitmapSampleSize(650, 12000))
    }
    @Test fun largeScansStayWithinThePixelBudget() {
        for ((width, height) in listOf(8000 to 12000, 1200 to 30000, 20000 to 20000)) {
            val sample = questionBitmapSampleSize(width, height)
            val w = (width.toLong() + sample - 1) / sample
            val h = (height.toLong() + sample - 1) / sample
            assertTrue(w <= 1440)
            assertTrue(w * h <= 4_000_000L)
        }
    }
    @Test fun ordinaryQuestionImagesKeepTheirResolution() {
        assertEquals(1, questionBitmapSampleSize(650, 500))
        assertEquals(1, questionBitmapSampleSize(1200, 2000))
    }
}
