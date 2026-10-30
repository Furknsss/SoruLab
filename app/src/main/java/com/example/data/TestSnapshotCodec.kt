package com.example.data

import org.json.JSONArray
import org.json.JSONObject

object TestSnapshotCodec {
    fun encode(test: YksTestItem): String {
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
                    .put("publisherLogoUri", question.publisherLogoUri)
                    .put("croppedImagePath", question.croppedImagePath)
                    .put("answerKeyVerified", question.answerKeyVerified)
                    .put("stemTop", question.stemTop)
                    .put("bulletsOrSteps", bullets)
                    .put("stemBottom", question.stemBottom)
                    .put("options", options)
                    .put("correctOption", question.correctOption)
                    .put("difficulty", question.difficulty.name)
                    .put("visualType", question.visualType.name)
                    .put("stepByStepSolution", question.stepByStepSolution)
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
                croppedImagePath = item.optString("croppedImagePath"),
                answerKeyVerified = item.optBoolean("answerKeyVerified", true),
                stemTop = item.optString("stemTop"),
                bulletsOrSteps = bullets,
                stemBottom = item.optString("stemBottom"),
                options = options,
                correctOption = item.optString("correctOption"),
                difficulty = DifficultyLevel.entries.firstOrNull {
                    it.name == item.optString("difficulty")
                } ?: DifficultyLevel.ORTA,
                visualType = QuestionVisualType.entries.firstOrNull {
                    it.name == item.optString("visualType")
                } ?: QuestionVisualType.NONE,
                stepByStepSolution = item.optString("stepByStepSolution")
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
            level = TopicTestLevel.entries.firstOrNull {
                it.name == root.optString("level")
            } ?: TopicTestLevel.OSYM,
            questions = questions
        )
    }.getOrNull()
}