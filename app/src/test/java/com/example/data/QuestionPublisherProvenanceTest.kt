package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionPublisherProvenanceTest {
    private val publisherA = YksPublisherInfo(
        id = "publisher_3d", name = "3D Yayınları", shortCode = "3D",
        primaryColorHex = 0xFF2563EB, secondaryColorHex = 0xFF1E3A8A,
        customLogoPath = "/files/publishers/3d.png"
    )
    private val publisherB = YksPublisherInfo(
        id = "publisher_bilgi", name = "Bilgi Sarmal", shortCode = "BS",
        primaryColorHex = 0xFF7C3AED, secondaryColorHex = 0xFF312E81,
        customLogoPath = "/files/publishers/bilgi.png"
    )

    @Test
    fun `publisher ids resolve separately even when questions share a test`() {
        val configured = listOf(publisherA, publisherB)
        assertEquals("3D Yayınları",
            SoruLabPublisherPresets.resolvePublisher("publisher_3d", configured).name)
        assertEquals("Bilgi Sarmal",
            SoruLabPublisherPresets.resolvePublisher("publisher_bilgi", configured).name)
    }

    @Test
    fun `a different publisher never borrows 345 fallback branding`() {
        val unknown = SoruLabPublisherPresets.resolvePublisher("Örnek Yayınevi")
        assertEquals("Örnek Yayınevi", unknown.name)
        assertEquals("Örn", unknown.shortCode.take(3).lowercase()
            .replaceFirstChar { it.uppercase() })
    }

    @Test
    fun `record provenance survives source-to-question mapping and renumbering`() {
        val source = CustomQuestionRecord(
            id = "source_7", publisherTag = "Bilgi Sarmal",
            publisherId = publisherB.id, sourceBookTitle = "AYT Matematik",
            bookId = "book_b", questionNumberInTest = 7
        )
        val question = YksQuestion(
            id = source.id, number = 1,
            publisherName = source.publisherTag,
            publisherStamp = source.publisherTag,
            publisherLogoUri = publisherB.customLogoPath,
            publisherId = source.publisherId,
            sourceBookTitle = source.sourceBookTitle,
            bookId = source.bookId,
            sourcePrintedQuestionNumber = source.questionNumberInTest,
            stemTop = "", stemBottom = "", options = emptyMap(),
            correctOption = "B", stepByStepSolution = ""
        )
        val mixedTestNumbering = question.copy(number = 4)
        assertEquals(4, mixedTestNumbering.number)
        assertEquals("publisher_bilgi", mixedTestNumbering.publisherId)
        assertEquals("AYT Matematik", mixedTestNumbering.sourceBookTitle)
        assertEquals(7, mixedTestNumbering.sourcePrintedQuestionNumber)
        assertEquals(publisherB.customLogoPath, mixedTestNumbering.publisherLogoUri)
    }
}
