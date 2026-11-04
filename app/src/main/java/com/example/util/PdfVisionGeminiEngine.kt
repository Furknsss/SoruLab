package com.example.util

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeout

/**
 * Vision-first document understanding for SoruLab question bank PDFs.
 *
 * Uses the existing Firebase AI Logic project configuration (no API key in
 * this source file). Unlike the former ML Kit + pixel-run primary cropper,
 * the multimodal model sees the entire page and is asked for COMPLETE
 * question rectangles including drawings, tables, formulas and all choices.
 * PdfVisionImportPolicy independently rejects contradictory geometry.
 *
 * Calls a cloud model: do not enable for public imports without communicating
 * costs, privacy and request quotas. Publisher PDF pages are sent to Firebase
 * AI / Gemini during import.
 */
internal object PdfVisionGeminiEngine {
    const val MODEL = "gemini-3.8-flash"

    private val question = Schema.obj(
        mapOf(
            "number" to Schema.integer("Exactly the printed question number"),
            "top" to Schema.integer("Top Y of entire question, normalized 0..1000"),
            "left" to Schema.integer("Left X of entire question, normalized 0..1000"),
            "bottom" to Schema.integer("Bottom Y including complete graphics, normalized 0..1000"),
            "right" to Schema.integer("Right X including complete graphics, normalized 0..1000")
        )
    )

    private val answerCell = Schema.obj(
        mapOf(
            "number" to Schema.integer("Printed answer-key question number"),
            "option" to Schema.enumeration(listOf("A", "B", "C", "D", "E"))
        )
    )
    private val answerTest = Schema.obj(
        mapOf(
            "section" to Schema.string("Literal printed chapter/topic title"),
            "kind" to Schema.enumeration(
                listOf("test", "analiz", "sentez", "deneme", "uygulama", "etkinlik")
            ),
            "testNumber" to Schema.integer("Literal printed test number"),
            "isOsym" to Schema.boolean("Whether the source printed an OSYM label"),
            "cells" to Schema.array(answerCell)
        )
    )

    private val layoutSchema = Schema.obj(
        mapOf(
            "pageType" to Schema.enumeration(listOf("questions", "answer_key", "other")),
            "printedHeader" to Schema.string("Literal visible printed source heading, or empty"),
            "questions" to Schema.array(question),
            "answerTests" to Schema.array(answerTest)
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = layoutSchema
            }
        )
    }

    suspend fun readPage(
        bitmap: Bitmap,
        pageNumber: Int,
        sourceHeader: String
    ): PdfVisionPage = withTimeout(90_000L) {
        require(!bitmap.isRecycled && bitmap.width >= 120 && bitmap.height >= 120)
        val prompt = """
            You are a high-precision visual layout transcriber for Turkish YKS
            educational PDF pages. Work ONLY from the pixels in the attached
            COMPLETE page image (PDF page $pageNumber).
            The optional source text header is a hint and may be wrong:
            ${sourceHeader.take(300)}

            Respond using the JSON schema exactly. For QUESTION pages:
            - Detect ONLY genuinely printed numbered question beginnings.
            - Return one question rectangle per printed question number,
              encompassing the full question stem, ALL diagrams/graphs,
              equations, tables, A-E choices, and continuation paragraphs.
            - Do not truncate after a short 'A)' or 'E)' row when a connected
              diagram continues below. Preserve source ink.
            - Never blend neighboring questions or the other column.
            - Two columns are possible: read left top-down, then right.
            - The coordinates are top/left/bottom/right integers normalized
              to 0..1000 relative to the FULL page image, NOT crop pixels.
            - If a question is partly hidden or a boundary cannot be resolved,
              omit it rather than hallucinate a rectangle.
            - questions: [] if no reliable printed numbered questions.

            For printed ANSWER KEY pages:
            - Return pageType=answer_key and transcribe answerTests ONLY from
              EXPLICIT answer-key tables, NEVER solve the questions.
            - Preserve exact chapter name, test kind, printed test number,
              printed question numbers and A/B/C/D/E letters.
            - Never invent an answer for unreadable or missing cells.
            - Only include a test when its chapter and test number are
              printed and unambiguous. Multiple tables are possible.
            - answerTests: [] on ordinary question pages, even if their
              multiple-choice options resemble an answer grid.

            Otherwise pageType=other, with empty arrays.
            Transcribe printedHeader from visible page title only; do not
            guess a topic from a question about neurons/receptors.
        """.trimIndent()
        val answer = model.generateContent(
            content {
                image(bitmap)
                text(prompt)
            }
        ).text?.trim().orEmpty()
        require(answer.isNotBlank()) { "Vision model returned no page layout" }
        PdfVisionImportPolicy.parse(answer)
    }
}
