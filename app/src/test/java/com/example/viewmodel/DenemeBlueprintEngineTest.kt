package com.example.viewmodel

import com.example.data.CustomQuestionRecord
import com.example.data.ExamType
import com.example.data.OsymQuestionType
import com.example.data.SoruLabCatalog
import com.example.data.TestResultRecord
import com.example.data.TestSnapshotCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                publisherTag = if (index % 2 == 0) "Yayın A" else "Yayın B",
                aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta(
                    "A seçeneği doğrudur.", answerKeyVerified = true
                )
            )
        }
    }

    @Test fun officialFenQuotasStayExact() {
        val pool = completeBank("tyt_fen")
        val ready = DenemePlanner.build(
            DenemeRequest(DenemeKind.BRANS, ExamType.TYT, "tyt_fen"),
            pool, sequence = 42L
        ) as DenemePlan.Ready
        assertEquals(20, ready.test.questions.size)
        assertEquals(
            listOf(7, 7, 6),
            ready.test.questions.groupBy { it.subject }.values.map { it.size }
        )
        assertEquals(20, ready.test.questions.map { it.sourceQuestionId }.distinct().size)
        assertTrue(ready.test.questions.all { it.answerKeyVerified && it.correctOption == "A" })
    }

    @Test fun missingSubjectFailsClosedInsteadOfSilentlyFillingAnotherSection() {
        val phy = SoruLabCatalog.allSubjects.first { it.id == "tyt_fizik" }
        val pool = records("tyt_fizik", phy.topics.first().id, 6)
        val result = DenemePlanner.build(
            DenemeRequest(DenemeKind.BRANS, ExamType.TYT, "tyt_fizik"), pool
        )
        assertTrue(result is DenemePlan.Missing)
        assertTrue((result as DenemePlan.Missing).shortage.any { it.available == 0 })
    }

    @Test fun unverifiedCorrectOptionIsNeverUsed() {
        val phy = SoruLabCatalog.allSubjects.first { it.id == "tyt_fizik" }
        val good = records("tyt_fizik", phy.topics.first().id, 7)
        val bad = good.first().copy(
            id = "bad_unverified",
            aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta(
                "Doğrulanmamış", answerKeyVerified = false
            )
        )
        assertTrue(!DenemePlanner.eligible(bad, DenemeDifficulty.DENGELI))
        assertTrue(
            DenemePlanner.build(
                DenemeRequest(DenemeKind.BRANS, ExamType.TYT, "tyt_fizik"),
                good.drop(1) + bad
            ) is DenemePlan.Missing
        )
    }

    @Test fun topicMockOnlyContainsItsOwnTopicAndNeverBorrowsNeighborTopics() {
        val phy = SoruLabCatalog.allSubjects.first { it.id == "tyt_fizik" }
        val a = phy.topics[0]; val b = phy.topics[1]
        val pool = records(phy.id, a.id, 7, "topic_a") +
            records(phy.id, b.id, 12, "topic_b")
        val result = DenemePlanner.build(
            DenemeRequest(
                DenemeKind.KONU, ExamType.TYT,
                blueprintId = "tyt_turkce", subjectId = phy.id, topicId = b.id, count = 10
            ),
            pool, sequence = 44
        ) as DenemePlan.Ready
        assertEquals(10, result.test.questions.size)
        assertEquals(18, result.minutes)
        assertTrue(result.test.questions.all { it.subject == phy.name })
        assertTrue(result.test.questions.all { it.topicHeader == b.name })
    }

    @Test fun tytSosyalSeparatesPhilosophyAndReligionDespiteCombinedCatalogSubject() {
        val records = completeBank("tyt_sosyal")
        val result = DenemePlanner.build(
            DenemeRequest(DenemeKind.BRANS, ExamType.TYT, "tyt_sosyal"),
            records, sequence = 99L
        ) as DenemePlan.Ready
        assertEquals(20, result.test.questions.size)
        assertEquals(5, result.test.questions.count { it.subject == "Felsefe" })
        assertEquals(5, result.test.questions.count { it.subject == "Din Kültürü" })
    }

    @Test fun sameBankProducesSameSubjectTopicAndQuestionOrder() {
        val bank = completeBank("tyt_fizik")
        val req = DenemeRequest(DenemeKind.BRANS, ExamType.TYT, "tyt_fizik")
        val a = DenemePlanner.build(req, bank, sequence = 1L) as DenemePlan.Ready
        val b = DenemePlanner.build(req, bank.reversed(), sequence = 2L) as DenemePlan.Ready
        assertEquals(
            a.test.questions.map { it.sourceQuestionId },
            b.test.questions.map { it.sourceQuestionId }
        )
    }

    private fun completeBank(blueprintId: String): List<CustomQuestionRecord> =
        DenemeBlueprints.find(blueprintId)!!.sections.flatMap { section ->
            DenemeTopicProfiles.forSection(section).flatMap { quota ->
                quota.topicIds.flatMap { topic ->
                    records(section.subjectId, topic, quota.count + 3, section.subjectId + "_" + topic)
                }
            }
        }

    @Test fun everyPublishedBlueprintHasExactDisjointCatalogBackedTopicQuotas() {
        DenemeBlueprints.branş.forEach { blueprint ->
            val ready = DenemePlanner.build(
                DenemeRequest(DenemeKind.BRANS, blueprint.examType, blueprint.id),
                completeBank(blueprint.id), sequence = 123L
            ) as DenemePlan.Ready
            assertEquals(blueprint.questionCount, ready.test.questions.size)
            blueprint.sections.forEach { section ->
                val chosen = ready.test.questions.filter { it.subject == section.label }
                assertEquals(section.count, chosen.size)
                DenemeTopicProfiles.forSection(section).forEach { quota ->
                    val names = SoruLabCatalog.allSubjects.first { it.id == section.subjectId }
                        .topics.filter { it.id in quota.topicIds }.map { it.name }
                    assertEquals(
                        blueprint.id + " / " + quota.label, quota.count,
                        chosen.count { it.topicHeader in names }
                    )
                }
            }
        }
    }

    @Test fun sevenOpticsQuestionsCannotMasqueradeAsPhysicsBranchExam() {
        val result = DenemePlanner.build(
            DenemeRequest(DenemeKind.BRANS, ExamType.TYT, "tyt_fizik"),
            records("tyt_fizik", "optik_aydinlanma_aynalar", 70)
        ) as DenemePlan.Missing
        assertTrue(result.shortage.any { it.section.contains("Isı") })
        assertTrue(result.shortage.any { it.section.contains("Hareket") })
    }

    @Test fun strictDifficultyAndUniqueIdsAreAppliedBeforeQuotaAvailability() {
        val request = DenemeRequest(
            DenemeKind.BRANS, ExamType.TYT, "tyt_fizik",
            difficulty = DenemeDifficulty.ZOR
        )
        assertTrue(DenemePlanner.build(request, completeBank("tyt_fizik")) is DenemePlan.Missing)
        val one = records("tyt_fizik", "isi_sicaklik", 1)
        assertTrue(
            DenemePlanner.build(
                DenemeRequest(DenemeKind.KONU, ExamType.TYT, subjectId = "tyt_fizik", topicId = "isi_sicaklik", count = 5),
                List(20) { one.first() }
            ) is DenemePlan.Missing
        )
    }

    @Test fun recentQuestionsAreAvoidedAndMetadataSurvivesSnapshotRoundTrip() {
        val request = DenemeRequest(DenemeKind.BRANS, ExamType.TYT, "tyt_fizik")
        val bank = completeBank("tyt_fizik")
        val first = DenemePlanner.build(request, bank, sequence = 1) as DenemePlan.Ready
        val saved = TestSnapshotCodec.encode(first.test)
        val prior = TestResultRecord(testSnapshotJson = saved)
        val second = DenemePlanner.build(request, bank, listOf(prior), sequence = 2) as DenemePlan.Ready
        assertTrue(
            first.test.questions.map { it.sourceQuestionId }.intersect(
                second.test.questions.map { it.sourceQuestionId }.toSet()
            ).isEmpty()
        )
        val restored = TestSnapshotCodec.decode(TestSnapshotCodec.encode(second.test))!!
        assertEquals(DenemeTopicProfiles.VERSION, restored.denemeProfileVersion)
        assertEquals("Dengeli", restored.denemeDifficultyLabel)
        assertEquals(0, restored.denemeReusedQuestionCount)
        assertTrue(restored.denemeDistributionSummary.contains("Optik 1"))
        val smallBank = bank.filter { q -> first.test.questions.any { it.sourceQuestionId == q.id } }
        val reused = DenemePlanner.build(request, smallBank, listOf(prior), sequence = 3) as DenemePlan.Ready
        assertEquals(7, reused.test.denemeReusedQuestionCount)
    }

    @Test fun stopwatchTimerModeEncodesZeroMinutesAndPreservesZeroInFromSnapshot() {
        val bank = completeBank("tyt_fizik")
        val stopwatchPlan = DenemePlanner.build(
            DenemeRequest(
                kind = DenemeKind.BRANS,
                examType = ExamType.TYT,
                blueprintId = "tyt_fizik",
                timerMode = DenemeTimerMode.STOPWATCH
            ),
            bank,
            sequence = 501L
        ) as DenemePlan.Ready

        assertEquals(0, stopwatchPlan.minutes)
        assertTrue(stopwatchPlan.test.id.contains("_0_501"))
        val mockStopwatch = DenemeBlueprints.fromSnapshot(stopwatchPlan.test)!!
        assertEquals(0, mockStopwatch.durationMinutes)

        val countdownPlan = DenemePlanner.build(
            DenemeRequest(
                kind = DenemeKind.BRANS,
                examType = ExamType.TYT,
                blueprintId = "tyt_fizik",
                durationMinutes = 15,
                timerMode = DenemeTimerMode.COUNTDOWN
            ),
            bank,
            sequence = 502L
        ) as DenemePlan.Ready

        assertEquals(15, countdownPlan.minutes)
        val mockCountdown = DenemeBlueprints.fromSnapshot(countdownPlan.test)!!
        assertEquals(15, mockCountdown.durationMinutes)
    }

    @Test fun cropQualityGateBlocksSliverAndUncroppedFullPageRecordsInDenemePlanner() {
        val clean = records("tyt_fizik", "basinc", 5, "clean_basinc")
        val fullPageDump = clean.first().copy(
            id = "bad_full_page_pdf",
            pdfFilePath = "/local/fizik_soru_bankasi.pdf",
            normLeft = 0.0f,
            normTop = 0.0f,
            normRight = 0.99f,
            normBottom = 0.99f
        )
        val sliverCrop = clean.first().copy(
            id = "bad_sliver_crop",
            normLeft = 0.15f,
            normTop = 0.20f,
            normRight = 0.22f,
            normBottom = 0.65f
        )
        val truncatedOcr = clean.first().copy(
            id = "bad_ocr_cut",
            questionText = "C) 4"
        )

        assertFalse(DenemePlanner.eligible(fullPageDump, DenemeDifficulty.DENGELI))
        assertFalse(DenemePlanner.eligible(sliverCrop, DenemeDifficulty.DENGELI))
        assertFalse(DenemePlanner.eligible(truncatedOcr, DenemeDifficulty.DENGELI))

        val plan = DenemePlanner.build(
            DenemeRequest(
                kind = DenemeKind.KONU,
                examType = ExamType.TYT,
                subjectId = "tyt_fizik",
                topicId = "basinc",
                count = 5
            ),
            pool = listOf(fullPageDump, sliverCrop, truncatedOcr) + clean,
            sequence = 88L
        ) as DenemePlan.Ready

        val selectedIds = plan.test.questions.map { it.sourceQuestionId }.toSet()
        assertFalse("bad_full_page_pdf" in selectedIds)
        assertFalse("bad_sliver_crop" in selectedIds)
        assertFalse("bad_ocr_cut" in selectedIds)
    }

    @Test fun konuDenemesiCoversAllSixOsymQuestionTypesAndPopulatesNonBlankInfoMetadata() {
        val subject = SoruLabCatalog.allSubjects.first { it.id == "ayt_mat" }
        val topic = subject.topics.first { it.id == "turev_uygulamalari" }
        val types = OsymQuestionType.entries
        val richPool = (1..18).map { idx ->
            val qType = types[(idx - 1) % types.size]
            val stemByStyle = when (qType) {
                OsymQuestionType.ONCULLU -> "f'(x) türev fonksiyonu için I, II ve III ifadelerinden hangileri kesinlikle doğrudur? (#$idx)"
                OsymQuestionType.YENI_NESIL_KURGULU -> "Bir fabrika üretim maliyetinde maksimum kâr ve en büyük hacim optimizasyonu yapıyor (#$idx)"
                OsymQuestionType.GRAFIK_TABLO -> "Dik koordinat düzleminde f'(x) türev grafiği ve ekstremum tablosu verilmiştir (#$idx)"
                OsymQuestionType.ANALITIK_CEBIRSEL -> "P(x) polinom fonksiyonu için P(x) + P'(x) = 3x^2 + 4x + 1 kritik nokta denklemi (#$idx)"
                OsymQuestionType.GORSEL_GEOMETRIK -> "Eğriye değme noktasından çizilen teğet doğrusu ve dik üçgenin boyalı alanı (#$idx)"
                OsymQuestionType.MANTIK_MUHAKEME -> "Gerçek sayılarda tanımlı türevlenebilir f fonksiyonu koşulunu sağlayan stratejik çıkarım (#$idx)"
            }
            CustomQuestionRecord(
                id = "turev_rich_$idx",
                examType = "AYT",
                subjectId = subject.id,
                subjectName = subject.name,
                topicId = topic.id,
                topicName = topic.name,
                difficulty = listOf("Kolay", "Orta", "Zor", "Çok Zor")[idx % 4],
                correctOption = listOf("A", "B", "C", "D", "E")[idx % 5],
                questionText = stemByStyle,
                questionType = qType.name,
                publisherTag = if (idx % 3 == 0) "Kaynak Belirtilmedi" else "3D Yayınları",
                sourceBookTitle = if (idx % 2 == 0) "AYT Matematik Soru Bankası.pdf" else "",
                aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta(
                    solutionText = "Türev çözümü $idx",
                    answerKeyVerified = true
                )
            )
        }

        val ready = DenemePlanner.build(
            DenemeRequest(
                kind = DenemeKind.KONU,
                examType = ExamType.AYT,
                subjectId = subject.id,
                topicId = topic.id,
                count = 8
            ),
            pool = richPool,
            sequence = 777L
        ) as DenemePlan.Ready

        assertEquals(8, ready.test.questions.size)
        val distinctTypes = ready.test.questions.map { it.questionType }.distinct()
        assertEquals("All 6 ÖSYM question types should be represented", 6, distinctTypes.size)
        assertTrue(ready.test.questions.map { it.learningOutcomeId }.distinct().size >= 3)

        // (i) bilgi ekranı için her soruda Konu, Kazanım ve Kitap/Yayın alanı dolu ve temiz olmalı
        ready.test.questions.forEach { q ->
            assertTrue(q.topicHeader.isNotBlank())
            assertTrue("learningOutcomeName blank for ${q.id}", q.learningOutcomeName.isNotBlank())
            assertTrue("sourceBookTitle blank for ${q.id}", q.sourceBookTitle.isNotBlank())
            assertFalse(q.sourceBookTitle.endsWith(".pdf"))
            assertTrue("publisherName blank for ${q.id}", q.publisherName.isNotBlank())
            assertFalse(q.publisherName.equals("Kaynak Belirtilmedi", ignoreCase = true))
        }
    }

    @Test fun studentMissedOutcomesArePrioritizedFromBackgroundHistoryWithoutRepeatingQuestions() {
        val subject = SoruLabCatalog.allSubjects.first { it.id == "ayt_mat" }
        val topic = subject.topics.first { it.id == "turev_uygulamalari" }

        // Öğrencinin daha önce yanlış yaptığı kazanım: Teğet eğimi (TEGET_EGIM_GEOMETRIK)
        val oldTegetQuestion = CustomQuestionRecord(
            id = "old_missed_teget",
            examType = "AYT",
            subjectId = subject.id,
            subjectName = subject.name,
            topicId = topic.id,
            topicName = topic.name,
            difficulty = "Orta",
            correctOption = "B",
            questionText = "Eğriye değme noktasından çizilen teğet doğrusunun eğimi kaçtır?",
            questionType = OsymQuestionType.GRAFIK_TABLO.name,
            publisherTag = "Orijinal",
            aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta("B", answerKeyVerified = true)
        )
        val firstBuild = DenemePlanner.build(
            DenemeRequest(DenemeKind.KONU, ExamType.AYT, subjectId = subject.id, topicId = topic.id, count = 1),
            listOf(oldTegetQuestion),
            sequence = 10L
        ) as DenemePlan.Ready

        // Öğrenci 1. soruyu yanlış işaretlemiş ("D" != "B")
        val failedRecord = TestResultRecord(
            id = "hist_1",
            examType = "AYT",
            subjectId = subject.id,
            topicId = topic.id,
            testId = firstBuild.test.id,
            correctCount = 0,
            wrongCount = 1,
            emptyCount = 0,
            answersMapJson = "{\"1\":\"D\"}",
            testSnapshotJson = TestSnapshotCodec.encode(firstBuild.test)
        )

        val freshTegetCandidate = CustomQuestionRecord(
            id = "fresh_teget_target",
            examType = "AYT",
            subjectId = subject.id,
            subjectName = subject.name,
            topicId = topic.id,
            topicName = topic.name,
            difficulty = "Orta",
            correctOption = "C",
            questionText = "Parabol grafiğine değme noktasından çizilen teğet doğrusunun eğimi kaçtır?",
            questionType = OsymQuestionType.GRAFIK_TABLO.name,
            publisherTag = "Apotemi",
            aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta("C", answerKeyVerified = true)
        )
        val otherOutcomeCandidates = (1..4).map { idx ->
            CustomQuestionRecord(
                id = "other_outcome_$idx",
                examType = "AYT",
                subjectId = subject.id,
                subjectName = subject.name,
                topicId = topic.id,
                topicName = topic.name,
                difficulty = "Orta",
                correctOption = "A",
                questionText = "P(x) polinom fonksiyonunun kritik nokta katsayısı hesaplaması #$idx",
                questionType = OsymQuestionType.ANALITIK_CEBIRSEL.name,
                publisherTag = "Apotemi",
                aiSolutionSummary = CustomQuestionRecord.packSolutionWithMeta("A", answerKeyVerified = true)
            )
        }

        // Sadece 1 soruluk yeni konu denemesi istendiğinde: eski soruyu tekrar etmeden, yanlış yapılan teğet kazanımındaki taze soruyu seçmeli!
        val nextPlan = DenemePlanner.build(
            DenemeRequest(DenemeKind.KONU, ExamType.AYT, subjectId = subject.id, topicId = topic.id, count = 1),
            pool = otherOutcomeCandidates + listOf(oldTegetQuestion, freshTegetCandidate),
            previous = listOf(failedRecord),
            sequence = 11L
        ) as DenemePlan.Ready

        assertEquals("fresh_teget_target", nextPlan.test.questions.single().sourceQuestionId)
    }
}
