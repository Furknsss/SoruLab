package com.example.data

import org.json.JSONArray
import org.json.JSONObject

object TestSnapshotCodec {
    fun encode(test: YksTestItem, includeImageFallback: Boolean = true): String {
        val questions = JSONArray()
        test.questions.forEach { question ->
            val options = JSONObject()
            question.options.forEach { (key, value) -> options.put(key, value) }
            val bullets = JSONArray()
            question.bulletsOrSteps.forEach(bullets::put)
            questions.put(
                JSONObject()
                    .put("id", question.id)
                    .put("number", question.number)
                    .put("badgeTitle", question.badgeTitle)
                    .put("topicHeader", question.topicHeader)
                    .put("publisherStamp", question.publisherStamp)
                    .put("publisherName", question.publisherName)
                    .put("publisherLogoUri", question.publisherLogoUri.takeIf { includeImageFallback }.orEmpty())
                    .put("croppedImagePath", question.croppedImagePath.takeIf { includeImageFallback }.orEmpty())
                    .put("imagePath", question.imagePath.takeIf { includeImageFallback }.orEmpty())
                    .put("answerKeyVerified", question.answerKeyVerified)
                    .put("stemTop", question.stemTop)
                    .put("bulletsOrSteps", bullets)
                    .put("stemBottom", question.stemBottom)
                    .put("options", options)
                    .put("correctOption", question.correctOption)
                    .put("difficulty", question.difficulty.fourTier().name)
                    .put("osymType", question.isOsymType || question.difficulty == DifficultyLevel.OSYM)
                    .put("visualType", question.visualType.name)
                    .put("stepByStepSolution", question.stepByStepSolution)
                    .put("bookId", question.bookId)
                    .put("sourceQuestionId", question.sourceQuestionId)
                    .put("sourceBookTitle", question.sourceBookTitle)
                    .put("sourceTestId", question.sourceTestId)
                    .put("sourcePageIndex", question.sourcePageIndex)
                    .put("sourcePrintedQuestionNumber", question.sourcePrintedQuestionNumber)
                    .put("subject", question.subject)
                    .put("learningOutcome", question.learningOutcome)
                    .put("questionType", question.questionType)
                    .put("analyticsText", question.analyticsText)
                    .put("imageBase64", question.imageBase64.takeIf { includeImageFallback }.orEmpty())
                    .put("pdfFilePath", question.pdfFilePath.takeIf { includeImageFallback }.orEmpty())
                    .put("pageIndex", question.pageIndex.takeIf { includeImageFallback } ?: -1)
                    .put("questionNumberInTest", question.questionNumberInTest)
                    .put("normLeft", if (includeImageFallback) question.normLeft.toDouble() else 0.0)
                    .put("normTop", if (includeImageFallback) question.normTop.toDouble() else 0.0)
                    .put("normRight", if (includeImageFallback) question.normRight.toDouble() else 1.0)
                    .put("normBottom", if (includeImageFallback) question.normBottom.toDouble() else 1.0)
            )
        }
        return JSONObject()
            .put("id", test.id)
            .put("topicId", test.topicId)
            .put("title", test.title)
            .put("fullTitle", test.fullTitle)
            .put("difficulty", test.difficulty.name)
            .put("difficultyPosition", test.difficultyPosition.toDouble())
            .put("defaultCompleted", test.defaultCompleted)
            .put("starRating", test.starRating)
            .put("level", test.level.name)
            .put("questions", questions)
            .toString()
    }

    fun decode(raw: String): YksTestItem? = runCatching {
        val root = JSONObject(raw)
        val questionsJson = root.optJSONArray("questions") ?: return null
        val questions = List(questionsJson.length()) { index ->
            val item = questionsJson.getJSONObject(index)
            val optionsJson = item.optJSONObject("options") ?: JSONObject()
            val options = buildMap {
                val keys = optionsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, optionsJson.optString(key))
                }
            }
            val bulletsJson = item.optJSONArray("bulletsOrSteps") ?: JSONArray()
            val bullets = List(bulletsJson.length()) { bulletsJson.optString(it) }
            YksQuestion(
                id = item.optString("id"),
                number = item.optInt("number", index + 1),
                badgeTitle = item.optString("badgeTitle"),
                topicHeader = item.optString("topicHeader"),
                publisherStamp = item.optString("publisherStamp"),
                publisherName = item.optString("publisherName"),
                publisherLogoUri = item.optString("publisherLogoUri"),
                croppedImagePath = item.optString("imagePath")
                    .ifBlank { item.optString("croppedImagePath") },
                answerKeyVerified = item.optBoolean("answerKeyVerified", false),
                stemTop = item.optString("stemTop"),
                bulletsOrSteps = bullets,
                stemBottom = item.optString("stemBottom"),
                options = options,
                correctOption = item.optString("correctOption"),
                difficulty = (DifficultyLevel.entries.firstOrNull {
                    it.name == item.optString("difficulty")
                } ?: DifficultyLevel.ORTA).fourTier(),
                isOsymType = item.optBoolean("osymType", false) ||
                    item.optString("difficulty").equals("OSYM", true),
                visualType = QuestionVisualType.entries.firstOrNull {
                    it.name == item.optString("visualType")
                } ?: QuestionVisualType.NONE,
                stepByStepSolution = item.optString("stepByStepSolution"),
                bookId = item.optString("bookId"),
                sourceQuestionId = item.optString("sourceQuestionId"),
                sourceBookTitle = item.optString("sourceBookTitle"),
                sourceTestId = item.optString("sourceTestId"),
                sourcePageIndex = item.optInt("sourcePageIndex", -1),
                sourcePrintedQuestionNumber = item.optInt("sourcePrintedQuestionNumber", 0),
                subject = item.optString("subject"),
                learningOutcome = item.optString("learningOutcome"),
                questionType = item.optString("questionType"),
                analyticsText = item.optString("analyticsText"),
                imageBase64 = item.optString("imageBase64"),
                pdfFilePath = item.optString("pdfFilePath"),
                pageIndex = item.optInt("pageIndex", -1),
                questionNumberInTest = item.optInt("questionNumberInTest", 0),
                normLeft = item.optDouble("normLeft", 0.0).toFloat(),
                normTop = item.optDouble("normTop", 0.0).toFloat(),
                normRight = item.optDouble("normRight", 1.0).toFloat(),
                normBottom = item.optDouble("normBottom", 1.0).toFloat()
            )
        }
        YksTestItem(
            id = root.optString("id").takeIf { it.isNotBlank() } ?: return null,
            topicId = root.optString("topicId"),
            title = root.optString("title"),
            fullTitle = root.optString("fullTitle"),
            difficulty = DifficultyLevel.entries.firstOrNull {
                it.name == root.optString("difficulty")
            } ?: DifficultyLevel.ORTA,
            difficultyPosition = root.optDouble("difficultyPosition", 0.5).toFloat(),
            defaultCompleted = root.optBoolean("defaultCompleted"),
            starRating = root.optInt("starRating"),
            level = TopicTestLevel.fromPersistedName(root.optString("level")) ?: TopicTestLevel.OSYM,
            questions = questions
        )
    }.getOrNull()
}