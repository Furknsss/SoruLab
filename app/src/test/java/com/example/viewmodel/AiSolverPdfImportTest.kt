package com.example.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSolverPdfImportTest {
    @Test
    fun incompleteOptionsDoNotRejectAReadableQuestionCrop() {
        assertTrue(
            shouldRetainQuestionCrop(
                questionText = "f(x) = 2x + 3 olduğuna göre f(4) kaçtır? A) 7 B) 9",
                hasQuestionStemOrFigure = false,
                usedLocalOcr = false
            )
        )
    }

    @Test
    fun localCropSurvivesWhenOcrCannotReadItsText() {
        assertTrue(
            shouldRetainQuestionCrop(
                questionText = "",
                hasQuestionStemOrFigure = false,
                usedLocalOcr = true
            )
        )
    }

    @Test
    fun visuallyConfirmedQuestionCropSurvivesWhenTextIsUnreadable() {
        assertTrue(
            shouldRetainQuestionCrop(
                questionText = "",
                hasQuestionStemOrFigure = true,
                usedLocalOcr = false
            )
        )
    }

    @Test
    fun unconfirmedShortOcrFragmentIsRejected() {
        assertFalse(
            shouldRetainQuestionCrop(
                questionText = "A) 2 B) 4",
                hasQuestionStemOrFigure = false,
                usedLocalOcr = false
            )
        )
    }

    @Test
    fun unconfirmedShortQuestionStemIsRejected() {
        assertFalse(
            shouldRetainQuestionCrop(
                questionText = "Soru kökü burada",
                hasQuestionStemOrFigure = false,
                usedLocalOcr = false
            )
        )
    }
}
