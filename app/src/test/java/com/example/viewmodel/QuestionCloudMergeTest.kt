package com.example.viewmodel

import com.example.data.CustomQuestionRecord
import org.junit.Assert.*
import org.junit.Test

class QuestionCloudMergeTest {
    @Test
    fun cloudMetadataUpdatesPreserveLocalPdfAndImageRecovery() {
        val local = CustomQuestionRecord(
            id = "q1", userId = "alice", topicName = "Old topic", bookId = "book-1",
            imageBase64 = "local-image", pdfFilePath = "/local/book.pdf", pageIndex = 7,
            normLeft = 0.2f, normTop = 0.3f, normRight = 0.7f, normBottom = 0.8f,
            aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta("old", croppedImagePath = "/local/crop.jpg")
        )
        val cloud = CustomQuestionRecord(
            id = "q1", userId = "alice", topicName = "New topic",
            aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta("updated", isInKumbara = true)
        )
        val merged = mergeCloudQuestionsWithLocalMedia("alice", listOf(cloud), listOf(local)).single()
        assertEquals("New topic", merged.topicName)
        assertEquals("updated", merged.cleanSolutionSummary)
        assertTrue(merged.isInKumbara)
        assertEquals("book-1", merged.bookId)
        assertEquals("local-image", merged.imageBase64)
        assertEquals("/local/book.pdf", merged.pdfFilePath)
        assertEquals("/local/crop.jpg", merged.croppedImagePath)
        assertEquals(7, merged.pageIndex)
        assertEquals(0.2f, merged.normLeft)
        assertEquals(0.3f, merged.normTop)
        assertEquals(0.7f, merged.normRight)
        assertEquals(0.8f, merged.normBottom)
    }

    @Test
    fun mergeKeepsUnsyncedOwnedQuestionsAndRejectsOtherAccounts() {
        val result = mergeCloudQuestionsWithLocalMedia("alice",
            listOf(CustomQuestionRecord(id = "foreign-cloud", userId = "bob")),
            listOf(CustomQuestionRecord(id = "local", userId = "alice"), CustomQuestionRecord(id = "foreign-local", userId = "bob"))
        )
        assertEquals(listOf("local"), result.map { it.id })
    }
}
