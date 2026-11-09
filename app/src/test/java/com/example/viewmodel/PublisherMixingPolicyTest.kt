package com.example.viewmodel

import com.example.data.YksQuestion
import com.example.data.CustomQuestionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublisherMixingPolicyTest {
    private fun question(publisher: String, number: Int, publisherId: String = "") =
        YksQuestion(
            id = publisher + "_" + number,
            number = number,
            sourceQuestionId = publisher + "_" + number,
            publisherId = publisherId,
            publisherName = publisher,
            topicHeader = "Trigonometri",
            stemTop = "Bir trigonometrik ifade veriliyor.",
            stemBottom = "Sonuç kaçtır?",
            options = mapOf("A" to "1", "B" to "2", "C" to "3", "D" to "4", "E" to "5"),
            correctOption = "B",
            stepByStepSolution = "Doğrulanmış çözüm."
        )

    @Test fun mixedTestKeepsEveryOriginalAndPublisherProportions() {
        val raw = (1..17).map { question("3D", it) } +
            (1..11).map { question("Bilgi Sarmal", it) } +
            (1..9).map { question("345", it) }
        val result = PublisherMixingPolicy.mix(raw, 24713)
        assertEquals(raw.size, result.size)
        assertEquals(raw.map { it.id }.toSet(), result.map { it.id }.toSet())
        assertEquals(raw.groupingBy { it.publisherName }.eachCount(),
            result.groupingBy { it.publisherName }.eachCount())
        assertNotEquals(raw.map { it.id }, result.map { it.id })
    }

    @Test fun differentTestsProduceDifferentLayoutsButSameSeedIsReproducible() {
        val raw = (1..12).flatMap { n ->
            listOf(question("3D", n), question("345", n), question("Apotemi", n))
        }
        val a = PublisherMixingPolicy.mix(raw, 112233).map { it.id }
        val b = PublisherMixingPolicy.mix(raw, 112233).map { it.id }
        val c = PublisherMixingPolicy.mix(raw, 446677).map { it.id }
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test fun newMixerIsNotMechanicalTwoByTwoGrouping() {
        val sources = listOf("A", "B", "C", "D")
        val raw = sources.flatMap { source -> (1..12).map { source + "-" + it } }
        val mixed = PublisherMixingPolicy.mix(raw, 4821) { it.substringBefore('-') }
        assertEquals(raw.toSet(), mixed.toSet())
        val publisherOrder = mixed.map { it.substringBefore('-') }
        val twoByTwo = sources.flatMap { source -> listOf(source, source) }
        assertFalse(publisherOrder.take(8) == twoByTwo)
        assertTrue(publisherOrder.take(20).distinct().size >= 3)
    }

    @Test fun singlePublisherFilterNeverLeadsToOtherPublisherQuestions() {
        val raw = listOf(
            question("3D", 1, "official_3d"),
            question("3D", 2, "official_3d"),
            question("345", 1, "official_345"),
            question("3D", 3, "official_3d")
        )
        val options = TestPublisherChoice.fromQuestions(raw)
        assertEquals(2, options.size)
        val selected = options.single { it.name == "3D" }
        val filtered = PublisherMixingPolicy.onlySelected(raw, selected.key)
        assertEquals(3, filtered.size)
        assertTrue(filtered.all { it.publisherId == "official_3d" })
        assertEquals(raw, PublisherMixingPolicy.onlySelected(raw, null))
        assertTrue(PublisherMixingPolicy.onlySelected(raw, "id:does-not-exist").isEmpty())
    }

    @Test fun publisherFilterUsesExactIdentityNotSubstringOrLookalike() {
        val raw = listOf(
            question("3D", 1),
            question("3D Plus", 2),
            question("Karekök", 3)
        )
        assertEquals(listOf("3D_1"), PublisherMixingPolicy.onlySelected(
            raw, PublisherMixingPolicy.key(raw.first())
        ).map { it.id })
    }

    @Test fun onePublisherStillMixesQuestionsWithinIt() {
        val raw = (1..16).map { question("Apotemi", it) }
        val result = PublisherMixingPolicy.mix(raw, 7771)
        assertEquals(raw.map { it.id }.toSet(), result.map { it.id }.toSet())
        assertNotEquals(raw.map { it.id }, result.map { it.id })
    }

    @Test fun singlePublisherPickerOnlyShowsPublishersWithBooksImportedForThisLesson() {
        val records = listOf(
            CustomQuestionRecord(
                id = "a", subjectId = "ayt_mat", topicId = "trig",
                publisherTag = "345", publisherId = "pub_345",
                sourceBookTitle = "345 AYT Matematik", bookId = "book_345"
            ),
            CustomQuestionRecord(
                id = "b", subjectId = "ayt_fizik", topicId = "vektor",
                publisherTag = "3D", publisherId = "pub_3d",
                sourceBookTitle = "3D AYT Fizik", bookId = "book_3d"
            ),
            CustomQuestionRecord(
                id = "c", subjectId = "ayt_mat", topicId = "turev",
                publisherTag = "Apotemi", publisherId = "pub_apotemi",
                sourceBookTitle = "Apotemi Türev", bookId = "book_apotemi"
            ),
            // The publisher exists but no book has been imported yet.
            CustomQuestionRecord(
                id = "d", subjectId = "ayt_mat", topicId = "trig",
                publisherTag = "Orijinal", publisherId = "pub_orijinal"
            ),
            CustomQuestionRecord(
                id = "e", subjectId = "tyt_mat", topicId = "sayilar",
                publisherTag = "Bilgi Sarmal", publisherId = "pub_bs",
                sourceBookTitle = "Bilgi Sarmal TYT Matematik", bookId = "book_bs"
            )
        )
        val options = TestPublisherChoice.fromImportedBooksForSubject(records, "ayt_mat")
        assertEquals(listOf("345", "Apotemi"), options.map { it.name })
        assertTrue(options.all { it.key in setOf("id:pub_345", "id:pub_apotemi") })
        assertTrue(TestPublisherChoice.fromImportedBooksForSubject(records, "ayt_kimya").isEmpty())
    }

    @Test fun publishersRemainInLessonMenuEvenIfTheirBooksHaveNoQuestionsInCurrentTopic() {
        val records = listOf(
            CustomQuestionRecord(
                id = "a", subjectId = "ayt_mat", topicId = "polinom",
                publisherTag = "Endemik", publisherId = "pub_endemik",
                bookId = "book_endemik"
            )
        )
        assertEquals(
            listOf("Endemik"),
            TestPublisherChoice.fromImportedBooksForSubject(records, "ayt_mat").map { it.name }
        )
    }

}
