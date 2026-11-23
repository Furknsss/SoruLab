package com.example.util

import com.example.data.DifficultyLevel
import com.example.data.QuestionVisualType
import com.example.data.YksQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TestPageLayoutEngineTest {

    private fun createSampleQuestion(
        num: Int,
        stemLength: Int = 120,
        visualType: QuestionVisualType = QuestionVisualType.NONE,
        normLeft: Float = 0f,
        normTop: Float = 0f,
        normRight: Float = 1f,
        normBottom: Float = 1f,
        hasImage: Boolean = false
    ): YksQuestion {
        val repeatedStem = "ÖSYM kazanım odaklı örnek soru metni. ".repeat((stemLength / 35).coerceAtLeast(1))
            .take(stemLength)
        return YksQuestion(
            id = "q_$num",
            number = num,
            badgeTitle = "DENEME TESTİ",
            topicHeader = "Türev Uygulamaları",
            stemTop = repeatedStem,
            bulletsOrSteps = listOf("I. Öncül ifadesi", "II. Öncül ifadesi"),
            stemBottom = "Buna göre yukarıdaki ifadelerden hangileri doğrudur?",
            options = mapOf(
                "A" to "Yalnız I",
                "B" to "Yalnız II",
                "C" to "I ve II",
                "D" to "II ve III",
                "E" to "I, II ve III"
            ),
            correctOption = "C",
            difficulty = DifficultyLevel.ORTA,
            visualType = visualType,
            stepByStepSolution = "Adım adım çözüm metni.",
            croppedImagePath = if (hasImage) "/data/user/0/com.example/files/questions/q_$num.jpg" else "",
            normLeft = normLeft,
            normTop = normTop,
            normRight = normRight,
            normBottom = normBottom,
            sourcePrintedQuestionNumber = num
        )
    }

    @Test
    fun `single column vs two column flow distributes questions properly`() {
        val questions = (1..6).map { createSampleQuestion(it) }

        val singleColConfig = TestPageLayoutConfig(
            columnMode = PageColumnLayoutMode.SINGLE_COLUMN,
            bookletType = BookletType.A
        )
        val singleResult = TestPageLayoutEngine.buildBookletLayout(questions, singleColConfig)

        assertEquals(6, singleResult.totalQuestions)
        assertTrue(singleResult.pages.isNotEmpty())
        singleResult.pages.forEach { page ->
            assertEquals(PageColumnLayoutMode.SINGLE_COLUMN, page.columnMode)
            assertTrue("Single column must keep right column empty", page.rightColumn.isEmpty())
            assertTrue("Single column must populate left column", page.leftColumn.isNotEmpty())
        }
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6),
            singleResult.pages.flatMap { it.questions }.map { it.number }
        )

        val twoColConfig = TestPageLayoutConfig(
            columnMode = PageColumnLayoutMode.TWO_COLUMN,
            bookletType = BookletType.A
        )
        val twoColResult = TestPageLayoutEngine.buildBookletLayout(questions, twoColConfig)

        assertEquals(6, twoColResult.totalQuestions)
        assertTrue("Two column mode should use fewer or equal pages than single column",
            twoColResult.totalPages <= singleResult.totalPages)
        twoColResult.pages.forEach { page ->
            assertEquals(PageColumnLayoutMode.TWO_COLUMN, page.columnMode)
            if (page.questions.size >= 2) {
                assertTrue("Left column should not be empty", page.leftColumn.isNotEmpty())
                assertTrue("Right column should not be empty when page has >= 2 questions", page.rightColumn.isNotEmpty())
            }
        }
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6),
            twoColResult.pages.flatMap { it.questions }.map { it.number }
        )
    }

    @Test
    fun `page overflow prevention keeps all columns within max height budget`() {
        val questions = (1..12).map { idx ->
            createSampleQuestion(
                num = idx,
                stemLength = 150 + (idx % 4) * 90,
                visualType = if (idx % 2 == 0) QuestionVisualType.FUNCTION_GRAPH else QuestionVisualType.NONE
            )
        }
        val config = TestPageLayoutConfig(
            columnMode = PageColumnLayoutMode.TWO_COLUMN,
            maxNormalizedColumnHeight = 1.85f,
            spacingPreset = QuestionSpacingPreset.NORMAL
        )

        val result = TestPageLayoutEngine.buildBookletLayout(questions, config)

        assertFalse("Layout should not have any page overflow", result.hasAnyPageOverflow)
        result.pages.forEach { page ->
            assertTrue("Page ${page.pageNumber} must be overflow-free", page.isOverflowFree)
            if (page.leftColumn.size > 1) {
                assertTrue(
                    "Left column used height (${page.leftColumnUsedHeight}) must not exceed ${config.maxNormalizedColumnHeight}",
                    page.leftColumnUsedHeight <= config.maxNormalizedColumnHeight + 0.001f
                )
            }
            if (page.rightColumn.size > 1) {
                assertTrue(
                    "Right column used height (${page.rightColumnUsedHeight}) must not exceed ${config.maxNormalizedColumnHeight}",
                    page.rightColumnUsedHeight <= config.maxNormalizedColumnHeight + 0.001f
                )
            }
        }
    }

    @Test
    fun `two column layout auto balances equal questions and prevents orphan last page`() {
        // 4 özdeş soru -> tek sayfada 2 sol, 2 sağ olarak tam dengeli yerleşmeli
        val fourQuestions = (1..4).map { createSampleQuestion(it, stemLength = 100) }
        val fourResult = TestPageLayoutEngine.buildBookletLayout(
            questions = fourQuestions,
            config = TestPageLayoutConfig(columnMode = PageColumnLayoutMode.TWO_COLUMN)
        )

        assertEquals(1, fourResult.totalPages)
        val firstPage = fourResult.pages.single()
        assertEquals(2, firstPage.leftColumn.size)
        assertEquals(2, firstPage.rightColumn.size)
        assertEquals(0f, firstPage.columnBalanceDelta, 0.001f)

        // 5 soru (sayfa başına en fazla 4 soru sığacak yükseklikte) ->
        // Sayfa 1'de 4, Sayfa 2'de 1 (orphan) kalmak yerine; Sayfa 1'de 3, Sayfa 2'de 2 soru olarak dengelenmeli
        val fiveTallQuestions = (1..5).map {
            createSampleQuestion(it, stemLength = 140, visualType = QuestionVisualType.GEOMETRY_TRIANGLE)
        }
        val fiveResult = TestPageLayoutEngine.buildBookletLayout(
            questions = fiveTallQuestions,
            config = TestPageLayoutConfig(
                columnMode = PageColumnLayoutMode.TWO_COLUMN,
                maxNormalizedColumnHeight = 2.0f
            )
        )

        assertEquals(2, fiveResult.totalPages)
        assertTrue(
            "Last page should not have a single orphan question when previous page has > 2 questions",
            fiveResult.pages.last().questions.size >= 2
        )
    }

    @Test
    fun `booklet A and booklet B ordering preserves all questions and maps numbers deterministically`() {
        val questions = (1..8).map { createSampleQuestion(it) }

        val layoutA = TestPageLayoutEngine.buildBookletLayout(
            questions = questions,
            config = TestPageLayoutConfig(bookletType = BookletType.A)
        )
        val layoutB = TestPageLayoutEngine.buildBookletLayout(
            questions = questions,
            config = TestPageLayoutConfig(bookletType = BookletType.B)
        )

        val idsA = layoutA.pages.flatMap { it.questions }.map { it.id }
        val idsB = layoutB.pages.flatMap { it.questions }.map { it.id }

        assertEquals(listOf("q_1", "q_2", "q_3", "q_4", "q_5", "q_6", "q_7", "q_8"), idsA)
        assertNotEquals("Booklet B order must differ from Booklet A", idsA, idsB)
        assertEquals("Booklet B must contain the exact same set of questions", idsA.toSet(), idsB.toSet())
        assertEquals(8, idsB.size)

        // 4'lü blok [1, 2, 3, 4] -> [3, 1, 4, 2] permütasyon doğrulaması
        assertEquals(listOf("q_3", "q_1", "q_4", "q_2", "q_7", "q_5", "q_8", "q_6"), idsB)

        // Kitapçık B'de basılı soru numaraları yine 1..8 sıralı olmalı ve orijinal numaralara eşlenmeli
        val printedNumbersB = layoutB.pages.flatMap { it.allSlots }.map { it.displayNumber }
        assertEquals((1..8).toList(), printedNumbersB)
        assertEquals(3, layoutB.bookletNumberToOriginalNumber[1])
        assertEquals(1, layoutB.bookletNumberToOriginalNumber[2])
        assertEquals(4, layoutB.bookletNumberToOriginalNumber[3])
        assertEquals(2, layoutB.bookletNumberToOriginalNumber[4])
    }

    @Test
    fun `question scaling clamps between 50 and 150 percent and affects page distribution`() {
        val belowMin = QuestionScaleAndCropConfig(scalePercent = 20)
        val aboveMax = QuestionScaleAndCropConfig(scalePercent = 240)
        val normal = QuestionScaleAndCropConfig(scalePercent = 100)

        assertEquals(50, belowMin.clampedScalePercent)
        assertEquals(0.50f, belowMin.scaleFactor, 0.001f)
        assertEquals(150, aboveMax.clampedScalePercent)
        assertEquals(1.50f, aboveMax.scaleFactor, 0.001f)
        assertEquals(1.00f, normal.scaleFactor, 0.001f)

        val questions = (1..8).map {
            createSampleQuestion(it, stemLength = 180, visualType = QuestionVisualType.FUNCTION_GRAPH)
        }

        val compactLayout = TestPageLayoutEngine.buildBookletLayout(
            questions = questions,
            config = TestPageLayoutConfig().withScalePercent(60)
        )
        val largeLayout = TestPageLayoutEngine.buildBookletLayout(
            questions = questions,
            config = TestPageLayoutConfig().withScalePercent(145)
        )

        assertTrue(
            "145% scale must require more or equal pages than 60% scale",
            largeLayout.totalPages > compactLayout.totalPages
        )
    }

    @Test
    fun `computeScaledDimensions strictly preserves aspect ratio across scales and bounds`() {
        val origWidth = 800
        val origHeight = 500
        val expectedAspect = origWidth.toFloat() / origHeight.toFloat() // 1.60f

        for (scalePercent in listOf(50, 75, 100, 125, 150)) {
            val scaleConfig = QuestionScaleAndCropConfig(
                scalePercent = scalePercent,
                preserveAspectRatio = true
            )
            // Kapsayıcı sınırı geniş olduğunda
            val unconstrained = scaleConfig.computeScaledDimensions(
                originalWidthPx = origWidth,
                originalHeightPx = origHeight,
                maxContainerWidthPx = 2000,
                maxContainerHeightPx = 2000
            )
            assertTrue("Aspect ratio must be preserved at $scalePercent%", unconstrained.isAspectRatioPreserved)
            assertEquals(expectedAspect, unconstrained.scaledAspectRatio, 0.02f)
            assertEquals((origWidth * (scalePercent / 100f)).toInt(), unconstrained.widthPx)

            // Kapsayıcı genişliği dar olduğunda (ör. 600px) yine aspect ratio bozulmamalı
            val constrained = scaleConfig.computeScaledDimensions(
                originalWidthPx = origWidth,
                originalHeightPx = origHeight,
                maxContainerWidthPx = 600,
                maxContainerHeightPx = 450
            )
            assertTrue("Aspect ratio must be preserved under container constraints", constrained.isAspectRatioPreserved)
            assertTrue(constrained.widthPx <= 600)
            assertTrue(constrained.heightPx <= 450)
            assertTrue(abs(expectedAspect - constrained.scaledAspectRatio) <= 0.03f)
        }
    }

    @Test
    fun `applyStemCropMargins safely expands bounds within normalized 0 to 1 box`() {
        val config = QuestionScaleAndCropConfig(
            stemCropMarginTopRatio = 0.04f,
            stemCropMarginBottomRatio = 0.05f,
            stemCropMarginHorizontalRatio = 0.03f
        )

        val rect = config.applyStemCropMargins(
            normLeft = 0.10f,
            normTop = 0.20f,
            normRight = 0.80f,
            normBottom = 0.70f
        )

        assertEquals(0.07f, rect.left, 0.001f)
        assertEquals(0.16f, rect.top, 0.001f)
        assertEquals(0.83f, rect.right, 0.001f)
        assertEquals(0.75f, rect.bottom, 0.001f)

        // Sayfa sınırlarına yakın koordinatlarda [0, 1] dışına taşmamalı
        val edgeRect = config.applyStemCropMargins(
            normLeft = 0.01f,
            normTop = 0.01f,
            normRight = 0.99f,
            normBottom = 0.99f
        )
        assertTrue(edgeRect.left >= 0f)
        assertTrue(edgeRect.top >= 0f)
        assertTrue(edgeRect.right <= 1f)
        assertTrue(edgeRect.bottom <= 1f)
        assertTrue(edgeRect.width > 0.9f)
        assertTrue(edgeRect.height > 0.9f)
    }

    @Test
    fun `optical badges and question separators are generated according to layout config`() {
        val questions = (1..4).map { createSampleQuestion(it, stemLength = 100) }

        val configWithLines = TestPageLayoutConfig(
            columnMode = PageColumnLayoutMode.TWO_COLUMN,
            bookletType = BookletType.B,
             bookletTitle = "TYT GENEL DENEME",
            subjectHeader = "TÜRKÇE TESTİ",
            badgeStyle = OpticalBadgeStyle.ROUNDED_PILL,
            separatorStyle = QuestionSeparatorStyle.DASHED_LINE
        )
        val result = TestPageLayoutEngine.buildBookletLayout(questions, configWithLines)
        val page = result.pages.single()

        // Kitapçık başlığı kontrolü
        assertEquals("TYT GENEL DENEME", page.headerInfo.bookletTitle)
        assertEquals("TÜRKÇE TESTİ", page.headerInfo.subjectHeader)
        assertEquals(BookletType.B, page.headerInfo.bookletType)
        assertEquals("Sorular 1 - 4", page.headerInfo.questionRangeLabel)

        // Sol sütunda 2 soru: 1. sorunun altında ayırıcı çizgi olmalı, 2. sorunun altında olmamalı
        assertEquals(2, page.leftColumn.size)
        assertTrue(page.leftColumn[0].showBottomSeparator)
        assertFalse(page.leftColumn[1].showBottomSeparator)

        // Rozet formatı kontrolü (ROUNDED_PILL < 10 için "01", "02" üretir)
        assertEquals("01", page.leftColumn[0].opticalBadge.displayLabel)
        assertEquals(OpticalBadgeStyle.ROUNDED_PILL, page.leftColumn[0].opticalBadge.style)

        // Ayırıcı çizgi NONE seçildiğinde hiçbir soruda alt çizgi olmamalı
        val noLineResult = TestPageLayoutEngine.buildBookletLayout(
            questions = questions,
            config = configWithLines.copy(separatorStyle = QuestionSeparatorStyle.NONE)
        )
        noLineResult.pages.flatMap { it.allSlots }.forEach { slot ->
            assertFalse("No separator should be shown when style is NONE", slot.showBottomSeparator)
        }
    }

    @Test
    fun `cropped image questions without sub-page norm bounds pack naturally into 2-3 per column`() {
        val croppedQuestions = (1..6).map { num ->
            createSampleQuestion(
                num = num,
                stemLength = 0,
                hasImage = true,
                normLeft = 0f,
                normTop = 0f,
                normRight = 1f,
                normBottom = 1f
            ).copy(stemTop = "", stemBottom = "", bulletsOrSteps = emptyList())
        }
        val result = TestPageLayoutEngine.buildBookletLayout(
            questions = croppedQuestions,
            config = TestPageLayoutConfig(columnMode = PageColumnLayoutMode.TWO_COLUMN)
        )

        assertEquals("6 standard cropped questions should fit cleanly on 1 two-column page", 1, result.totalPages)
        val page = result.pages.single()
        assertEquals(3, page.leftColumn.size)
        assertEquals(3, page.rightColumn.size)
        assertFalse(result.hasAnyPageOverflow)
    }

    @Test
    fun `odd count tall questions keep earlier pages full and put remainder on final page`() {
        // 5 tall questions (each ~1.10 normalized height, maxColumnHeight = 1.60 -> 1 per column, max 2 per page)
        val tallQuestions = (1..5).map { num ->
            createSampleQuestion(
                num = num,
                stemLength = 260,
                visualType = QuestionVisualType.FUNCTION_GRAPH
            )
        }
        val result = TestPageLayoutEngine.buildBookletLayout(
            questions = tallQuestions,
            config = TestPageLayoutConfig(
                columnMode = PageColumnLayoutMode.TWO_COLUMN,
                maxNormalizedColumnHeight = 1.60f
            )
        )

        assertEquals(3, result.totalPages)
        assertEquals(listOf(2, 2, 1), result.pages.map { it.questions.size })
        assertTrue("Page 1 must have both columns filled", result.pages[0].leftColumn.isNotEmpty() && result.pages[0].rightColumn.isNotEmpty())
        assertTrue("Page 2 must have both columns filled", result.pages[1].leftColumn.isNotEmpty() && result.pages[1].rightColumn.isNotEmpty())
    }
}
