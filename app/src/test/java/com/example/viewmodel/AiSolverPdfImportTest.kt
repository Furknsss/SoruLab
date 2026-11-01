package com.example.viewmodel

import com.example.util.shouldPersistQuestionCrop
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSolverPdfImportTest {
    @Test
    fun incompleteOptionsDoNotRejectAVisibleQuestionCrop() {
        assertTrue(
            shouldPersistQuestionCrop(width = 240, height = 120, hasVisiblePixels = true)
        )
    }

    @Test
    fun visibleCropSurvivesWhenOcrCannotReadItsText() {
        assertTrue(
            shouldPersistQuestionCrop(width = 240, height = 120, hasVisiblePixels = true)
        )
    }

    @Test
    fun cropBelowMinimumWidthIsRejected() {
        assertFalse(
            shouldPersistQuestionCrop(width = 119, height = 120, hasVisiblePixels = true)
        )
    }

    @Test
    fun cropBelowMinimumHeightIsRejected() {
        assertFalse(
            shouldPersistQuestionCrop(width = 120, height = 79, hasVisiblePixels = true)
        )
    }

    @Test
    fun blankCropIsRejected() {
        assertFalse(
            shouldPersistQuestionCrop(width = 120, height = 80, hasVisiblePixels = false)
        )
    }
}
