package com.example.viewmodel

import com.example.data.CustomQuestionRecord
import com.example.data.ExamType
import com.example.data.SoruLabCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
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
        val pool = completeBank("tyt_fen")
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
        assertTrue((result as DenemePlan.Missing).shortage.any { it.available == 0 })
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
                blueprintId="tyt_turkce",subjectId=phy.id,topicId=b.id,count=10),
            pool,sequence=44
        ) as DenemePlan.Ready
        assertEquals(10,result.test.questions.size)
        assertEquals(18,result.minutes)
        assertTrue(result.test.questions.all { it.subject==phy.name })
        assertTrue(result.test.questions.all { it.topicHeader == b.name })
    }

    @Test fun tytSosyalSeparatesPhilosophyAndReligionDespiteCombinedCatalogSubject() {
        val records=completeBank("tyt_sosyal")
        val result=DenemePlanner.build(
            DenemeRequest(DenemeKind.BRANS,ExamType.TYT,"tyt_sosyal"),
            records,sequence=99L
        ) as DenemePlan.Ready
        assertEquals(20,result.test.questions.size)
        assertEquals(5,result.test.questions.count { it.subject=="Felsefe" })
        assertEquals(5,result.test.questions.count { it.subject=="Din Kültürü" })
    }

    @Test fun sameBankProducesSameSubjectTopicAndQuestionOrder() {
        val phy=SoruLabCatalog.allSubjects.first { it.id=="tyt_fizik" }
        val bank=completeBank("tyt_fizik")
        val req=DenemeRequest(DenemeKind.BRANS,ExamType.TYT,"tyt_fizik")
        val a=DenemePlanner.build(req,bank,sequence=1L) as DenemePlan.Ready
        val b=DenemePlanner.build(req,bank.reversed(),sequence=2L) as DenemePlan.Ready
        assertEquals(a.test.questions.map { it.sourceQuestionId },
            b.test.questions.map { it.sourceQuestionId })
    }

    private fun completeBank(blueprintId: String): List<CustomQuestionRecord> =
        DenemeBlueprints.find(blueprintId)!!.sections.flatMap { section ->
            DenemeTopicProfiles.forSection(section).flatMap { quota ->
                quota.topicIds.flatMap { topic -> records(section.subjectId,topic,quota.count+3,section.subjectId+"_"+topic) }
            }
        }

    @Test fun everyPublishedBlueprintHasExactDisjointCatalogBackedTopicQuotas() {
        DenemeBlueprints.branş.forEach { blueprint ->
            val ready=DenemePlanner.build(
                DenemeRequest(DenemeKind.BRANS,blueprint.examType,blueprint.id),
                completeBank(blueprint.id),sequence=123L
            ) as DenemePlan.Ready
            assertEquals(blueprint.questionCount,ready.test.questions.size)
            blueprint.sections.forEach { section ->
                val chosen=ready.test.questions.filter { it.subject==section.label }
                assertEquals(section.count,chosen.size)
                DenemeTopicProfiles.forSection(section).forEach { quota ->
                    val names=SoruLabCatalog.allSubjects.first { it.id==section.subjectId }
                        .topics.filter { it.id in quota.topicIds }.map { it.name }
                    assertEquals(blueprint.id+" / "+quota.label,quota.count,
                        chosen.count { it.topicHeader in names })
                }
            }
        }
    }

    @Test fun sevenOpticsQuestionsCannotMasqueradeAsPhysicsBranchExam() {
        val result=DenemePlanner.build(
            DenemeRequest(DenemeKind.BRANS,ExamType.TYT,"tyt_fizik"),
            records("tyt_fizik","optik_aydinlanma_aynalar",70)
        ) as DenemePlan.Missing
        assertTrue(result.shortage.any { it.section.contains("Isı") })
        assertTrue(result.shortage.any { it.section.contains("Hareket") })
    }

    @Test fun strictDifficultyAndUniqueIdsAreAppliedBeforeQuotaAvailability() {
        val request=DenemeRequest(DenemeKind.BRANS,ExamType.TYT,"tyt_fizik",
            difficulty=DenemeDifficulty.ZOR)
        assertTrue(DenemePlanner.build(request,completeBank("tyt_fizik")) is DenemePlan.Missing)
        val one=records("tyt_fizik","isi_sicaklik",1)
        assertTrue(DenemePlanner.build(
            DenemeRequest(DenemeKind.KONU,ExamType.TYT,subjectId="tyt_fizik",topicId="isi_sicaklik",count=5),
            List(20) {one.first()}
        ) is DenemePlan.Missing)
    }

    @Test fun recentQuestionsAreAvoidedAndMetadataSurvivesSnapshotRoundTrip() {
        val request=DenemeRequest(DenemeKind.BRANS,ExamType.TYT,"tyt_fizik")
        val bank=completeBank("tyt_fizik")
        val first=DenemePlanner.build(request,bank,sequence=1) as DenemePlan.Ready
        val saved=com.example.data.TestSnapshotCodec.encode(first.test)
        val prior=com.example.data.TestResultRecord(testSnapshotJson=saved)
        val second=DenemePlanner.build(request,bank,listOf(prior),sequence=2) as DenemePlan.Ready
        assertTrue(first.test.questions.map { it.sourceQuestionId }.intersect(
            second.test.questions.map { it.sourceQuestionId }.toSet()).isEmpty())
        val restored=com.example.data.TestSnapshotCodec.decode(
            com.example.data.TestSnapshotCodec.encode(second.test))!!
        assertEquals(DenemeTopicProfiles.VERSION,restored.denemeProfileVersion)
        assertEquals("Dengeli",restored.denemeDifficultyLabel)
        assertEquals(0,restored.denemeReusedQuestionCount)
        assertTrue(restored.denemeDistributionSummary.contains("Optik 1"))
        val smallBank=bank.filter { q -> first.test.questions.any { it.sourceQuestionId==q.id } }
        val reused=DenemePlanner.build(request,smallBank,listOf(prior),sequence=3) as DenemePlan.Ready
        assertEquals(7,reused.test.denemeReusedQuestionCount)
    }
}
