package com.example.ui

import com.example.data.DifficultyLevel
import com.example.data.TopicTestLevel
import com.example.data.YksTestItem
import com.example.ui.screens.premiumTestDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression: the new catalog formats labels; it never edits stored test data. */
class ReferenceTestCatalogTitleTest {
    private fun sample(title: String, level: TopicTestLevel = TopicTestLevel.OSYM) =
        YksTestItem(
            id = "reference_test", topicId = "trigonometri",
            title = title, fullTitle = title,
            difficulty = DifficultyLevel.ORTA,
            difficultyPosition = .55f,
            level = level, questions = emptyList()
        )

    @Test fun sourceOsymTitleGetsModernTestLabel() {
        assertEquals(
            "Trigonometri • ÖSYM Test 2",
            premiumTestDisplayName(sample("Trigonometri - ÖSYM 2"), "Trigonometri")
        )
    }

    @Test fun legacyHomeworkNumberIsPreserved() {
        assertEquals(
            "Fonksiyonlar • Ödev 11A",
            premiumTestDisplayName(sample("Fonksiyonlar - Ödev 11A"), "Fonksiyonlar")
        )
    }

    @Test fun bareTestTitleUsesStoredLevel() {
        assertEquals(
            "Trigonometri • ÖSYM Test 3",
            premiumTestDisplayName(sample("Trigonometri - Test 3"), "Trigonometri")
        )
    }
}
