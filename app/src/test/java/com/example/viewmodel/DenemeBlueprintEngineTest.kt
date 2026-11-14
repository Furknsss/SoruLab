package com.example.viewmodel

import com.example.data.CustomQuestionRecord
import com.example.data.ExamType
import com.example.data.SoruLabCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DenemeBlueprintEngineTest {
    private fun records(
        subjectId: String,
        topicId: String,
        n: Int,
        prefix: String = subjectId
    ): List<CustomQuestionRecord> {
        val subject = SoruLabCatalog.allSubjects.first { it.id == subjectId }
        val topic = subject.topics.first { it.id == topicId }
        return (1..n).map { index ->
            CustomQuestionRecord(
                id = prefix + "_" + index,
                examType = subject.examType.name,
                subjectId = subject.id,
                subjectName = subject.name,
                topicId = topic.id,
                topicName = topic.name,
                difficulty = "Orta",
                correctOption = "A",
                questionText = "Doğrulanmış soru " + index,
                publisherTag = if(index%2==0)"Yayın A" else "Yayın B",
                aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta(
                    "A seçeneği doğrudur.", answerKeyVerified = true
                )
            )
        }
    }

    @Test fun officialFenQuotasStayExact() {
        val catalog = SoruLabCatalog.allSubjects.associateBy { it.id }
        val pool = listOf("tyt_fizik" to 10,"tyt_kimya" to 11,"tyt_biyoloji" to 9)
            .flatMap { (id,n) -> records(id,catalog.getValue(id).topics.first().id,n) }
        val ready = DenemePlanner.build(
            DenemeRequest(DenemeKind.BRANS,ExamType.TYT,"tyt_fen"),
            pool,sequence = 42L
        ) as DenemePlan.Ready
        assertEquals(20,ready.test.questions.size)
        assertEquals(listOf(7,7,6),
            ready.test.questions.groupBy { it.subject }.values.map { it.size })
        assertEquals(20,ready.test.questions.map { it.sourceQuestionId }.distinct().size)
        assertTrue(ready.test.questions.all { it.answerKeyVerified && it.correctOption == "A" })
    }

    @Test fun missingSubjectFailsClosedInsteadOfSilentlyFillingAnotherSection() {
        val phy = SoruLabCatalog.allSubjects.first { it.id == "tyt_fizik" }
        val pool = records("tyt_fizik",phy.topics.first().id,6)
        val result = DenemePlanner.build(
            DenemeRequest(DenemeKind.BRANS,ExamType.TYT,"tyt_fizik"),pool
        )
        assertTrue(result is DenemePlan.Missing)
        assertEquals(6,(result as DenemePlan.Missing).shortage.first().available)
    }

    @Test fun unverifiedCorrectOptionIsNeverUsed() {
        val phy = SoruLabCatalog.allSubjects.first { it.id == "tyt_fizik" }
        val good = records("tyt_fizik",phy.topics.first().id,7)
        val bad = good.first().copy(
            id="bad_unverified",
            aiSolutionSummary=CustomQuestionRecord.packSolutionWithMeta(
                "Doğrulanmamış",answerKeyVerified=false
            )
        )
        assertTrue(!DenemePlanner.eligible(bad,DenemeDifficulty.DENGELI))
        assertTrue(DenemePlanner.build(
            DenemeRequest(DenemeKind.BRANS,ExamType.TYT,"tyt_fizik"),
            good.drop(1)+bad
        ) is DenemePlan.Missing)
    }

    @Test fun topicMockOnlyContainsItsOwnTopicAndNeverBorrowsNeighborTopics() {
        val phy = SoruLabCatalog.allSubjects.first { it.id == "tyt_fizik" }
        val a=phy.topics[0];val b=phy.topics[1]
        val pool=records(phy.id,a.id,7,"topic_a")+
            records(phy.id,b.id,12,"topic_b")
        val result=DenemePlanner.build(
            DenemeRequest(DenemeKind.KONU,ExamType.TYT,
                subjectId=phy.id,topicId=b.id,count=10),
            pool,sequence=44
        ) as DenemePlan.Ready
        assertEquals(10,result.test.questions.size)
        assertTrue(result.test.questions.all { it.topicHeader == b.name })
    }

    @Test fun tytSosyalSeparatesPhilosophyAndReligionDespiteCombinedCatalogSubject() {
        val catalog=SoruLabCatalog.allSubjects.associateBy { it.id }
        val records=records("tyt_tarih",catalog.getValue("tyt_tarih").topics.first().id,5)+
            records("tyt_cografya",catalog.getValue("tyt_cografya").topics.first().id,5)+
            records("tyt_felsefe_din","bilgi_felsefesi",5,"philosophy")+
            records("tyt_felsefe_din","din_ve_islam",5,"religion")
        val result=DenemePlanner.build(
            DenemeRequest(DenemeKind.BRANS,ExamType.TYT,"tyt_sosyal"),
            records,sequence=99L
        ) as DenemePlan.Ready
        assertEquals(20,result.test.questions.size)
        assertEquals(5,result.test.questions.count { it.topicHeader=="Bilgi Felsefesi" })
        assertEquals(5,result.test.questions.count { it.topicHeader=="Din, İbadet ve Ahlak" })
    }

    @Test fun sameBankProducesSameSubjectTopicAndQuestionOrder() {
        val phy=SoruLabCatalog.allSubjects.first { it.id=="tyt_fizik" }
        val bank=records(phy.id,phy.topics[0].id,7)+records(phy.id,phy.topics[1].id,7,"other")
        val req=DenemeRequest(DenemeKind.BRANS,ExamType.TYT,"tyt_fizik")
        val a=DenemePlanner.build(req,bank,sequence=1L) as DenemePlan.Ready
        val b=DenemePlanner.build(req,bank.reversed(),sequence=2L) as DenemePlan.Ready
        assertEquals(a.test.questions.map { it.sourceQuestionId },
            b.test.questions.map { it.sourceQuestionId })
    }
}
