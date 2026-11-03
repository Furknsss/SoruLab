package com.example.util

import com.example.data.CustomQuestionRecord
import org.junit.Assert.*
import org.junit.Test

class PdfImportRepairTest {
    @Test fun geometryAndHeadingChangesRetainOriginalRecordIdentity() {
        val old = CustomQuestionRecord(id = "original", bookId = "book", pageIndex = 12, questionNumberInTest = 3,
            normBottom = 0.4f, topicName = "")
        val index = indexPdfQuestionsForRepair(listOf(old))
        assertEquals("original", index[Triple("book", 12, 3)]?.id)
        assertNull(index[Triple("other-book", 12, 3)])
        assertNull(index[Triple("book", 13, 3)])
    }
    @Test fun ambiguousOrMissingPrintedIdentitiesNeverOverwriteAnExistingQuestion() {
        val first = CustomQuestionRecord(id = "a", bookId = "book", pageIndex = 12, questionNumberInTest = 3)
        val index = indexPdfQuestionsForRepair(listOf(first, first.copy(id = "b"), first.copy(id = "missing", questionNumberInTest = 0)))
        assertTrue(index.isEmpty())
    }

    @Test fun missingPrintedNumberMustNotBeReplacedByPagePosition() {
        assertEquals(0, verifiedPrintedQuestionNumber(null))
        assertEquals(0, verifiedPrintedQuestionNumber(0))
        assertEquals(0, verifiedPrintedQuestionNumber(-1))
        assertEquals(0, verifiedPrintedQuestionNumber(201))
        assertEquals(7, verifiedPrintedQuestionNumber(7))

        val unresolved = CustomQuestionRecord(
            id = "crop_page3_order2",
            bookId = "biologyBook",
            pageIndex = 3,
            questionNumberInTest = verifiedPrintedQuestionNumber(null)
        )
        assertTrue(indexPdfQuestionsForRepair(listOf(unresolved)).isEmpty())
    }

}
