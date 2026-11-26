package com.example.data

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.screens.IstatistiklerimScreen
import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h1280dp")
class DenemeMemoryAnalyticsEngineTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun buildSnapshotQuestion(
        number: Int,
        subject: String = "Matematik",
        topicHeader: String = "Sayı Problemleri",
        correctOption: String = "A",
        questionType: OsymQuestionType = OsymQuestionType.YENI_NESIL_KURGULU,
        outcomeId: String = "KAZ_PROB_01",
        outcomeCode: String = "MAT.9.3.5.1",
        outcomeName: String = "Sayı ve Kesir Problemleri Modellemesi",
        verified: Boolean = true
    ): YksQuestion {
        return YksQuestion(
            id = "q_$number",
            sourceQuestionId = "src_q_$number",
            number = number,
            subject = subject,
            topicHeader = topicHeader,
            stemTop = "Örnek soru metni $number",
            stemBottom = "Buna göre sonuç kaçtır?",
            options = mapOf("A" to "1", "B" to "2", "C" to "3", "D" to "4", "E" to "5"),
            correctOption = correctOption,
            answerKeyVerified = verified,
            stepByStepSolution = "Çözüm",
            questionType = questionType.name,
            learningOutcomeId = outcomeId,
            learningOutcome = outcomeCode,
            learningOutcomeName = outcomeName
        )
    }

    private fun buildDenemeRecord(
        id: String,
        examType: ExamType = ExamType.TYT,
        title: String,
        timestampMillis: Long,
        durationSeconds: Int,
        questions: List<YksQuestion>,
        answers: Map<Int, String>
    ): TestResultRecord {
        val testId = "deneme_v147_${examType.name}_BRANS_40_$timestampMillis"
        val snapshot = TestSnapshotCodec.encode(
            YksTestItem(
                id = testId,
                topicId = "sayi_problemleri",
                title = title,
                fullTitle = title,
                difficulty = DifficultyLevel.ORTA,
                difficultyPosition = 0.5f,
                questions = questions,
                denemeProfileVersion = "v152"
            ),
            includeImageFallback = false
        )
        val answersJson = answers.entries.joinToString(",", "{", "}") { (num, opt) ->
            "\"$num\":\"$opt\""
        }
        return TestResultRecord(
            id = id,
            examType = examType.name,
            testId = testId,
            testTitle = title,
            durationSeconds = durationSeconds,
            answersMapJson = answersJson,
            testSnapshotJson = snapshot,
            createdAt = Timestamp(Date(timestampMillis))
        )
    }

    @Test
    fun netTrendSummaryComputesLastFiveAverageHighestRecordNetGainAndPace() {
        val questions = (1..10).map { buildSnapshotQuestion(number = it, correctOption = "A") }

        // 6 chronological TYT mock exams:
        // D1: 4D, 4Y, 2B -> Net = 3.00  (first deneme)
        // D2: 5D, 2Y, 3B -> Net = 4.50
        // D3: 9D, 0Y, 1B -> Net = 9.00  (highest record!)
        // D4: 6D, 4Y, 0B -> Net = 5.00
        // D5: 7D, 2Y, 1B -> Net = 6.50
        // D6: 8D, 1Y, 1B -> Net = 7.75  (latest deneme; gain vs D1 = +4.75 Net)
        // Last 5 (D2..D6) avg = (4.50 + 9.00 + 5.00 + 6.50 + 7.75) / 5 = 32.75 / 5 = 6.55
        val configs = listOf(
            Triple("d1", 1_000_000L, mapOf(1 to "A", 2 to "A", 3 to "A", 4 to "A", 5 to "B", 6 to "B", 7 to "B", 8 to "B")),
            Triple("d2", 2_000_000L, mapOf(1 to "A", 2 to "A", 3 to "A", 4 to "A", 5 to "A", 6 to "B", 7 to "B")),
            Triple("d3", 3_000_000L, (1..9).associateWith { "A" }),
            Triple("d4", 4_000_000L, (1..6).associateWith { "A" } + (7..10).associateWith { "C" }),
            Triple("d5", 5_000_000L, (1..7).associateWith { "A" } + mapOf(8 to "B", 9 to "B")),
            Triple("d6", 6_000_000L, (1..8).associateWith { "A" } + mapOf(9 to "D"))
        )

        val records = configs.mapIndexed { idx, (id, ts, ans) ->
            buildDenemeRecord(
                id = id,
                title = "TYT Deneme ${idx + 1}",
                timestampMillis = ts,
                durationSeconds = 600, // 600s / 10 questions = 60s/question
                questions = questions,
                answers = ans
            )
        }

        // Add an unsolved GENERATED placeholder and an AYT deneme to verify isolation
        val generatedPlaceholder = records.first().copy(
            id = "gen_1",
            answersMapJson = """{"_status":"GENERATED"}"""
        )
        val aytRecord = buildDenemeRecord(
            id = "ayt_1",
            examType = ExamType.AYT,
            title = "AYT Deneme 1",
            timestampMillis = 7_000_000L,
            durationSeconds = 900,
            questions = questions,
            answers = (1..10).associateWith { "A" }
        )

        val report = DenemeMemoryAnalyticsEngine.analyze(
            records = records.shuffled() + generatedPlaceholder + aytRecord,
            examType = ExamType.TYT
        )

        assertEquals(6, report.netTrend.denemeCount)
        assertEquals(6.55, report.netTrend.lastFiveAverageNet!!, 0.001)
        assertEquals(9.00, report.netTrend.highestNetRecord!!, 0.001)
        assertEquals("TYT Deneme 3", report.netTrend.highestNetExamTitle)
        assertEquals(3.00, report.netTrend.firstDenemeNet!!, 0.001)
        assertEquals(7.75, report.netTrend.latestDenemeNet!!, 0.001)
        assertEquals(4.75, report.netTrend.netGainFromFirst!!, 0.001)
        assertEquals("+4.75 Net", report.netTrend.formattedNetGain)
        assertEquals(65, report.netTrend.accuracyPercent) // (4+5+9+6+7+8)=39 / 60 = 65%
        assertEquals(60, report.netTrend.avgSecondsPerQuestionRounded)
        assertEquals(PaceEfficiencyStatus.IDEAL, report.netTrend.paceStatus)
        assertEquals(5, report.netTrend.recentFiveDenemes.size)
        assertEquals("d2", report.netTrend.recentFiveDenemes.first().id)
        assertEquals("d6", report.netTrend.recentFiveDenemes.last().id)
    }

    @Test
    fun topMissedOutcomesAndQuestionTypeMasteryRankCriticalNetLossesAccurately() {
        val q1 = buildSnapshotQuestion(
            number = 1,
            subject = "Matematik",
            topicHeader = "Türevin Geometrik ve Fiziksel Uygulamaları",
            questionType = OsymQuestionType.GRAFIK_TABLO,
            outcomeId = "KAZ_TUREV_03",
            outcomeCode = "MAT.12.5.3.1",
            outcomeName = "Türevin Geometrik Yorumu ve Teğet-Normal Denklemleri"
        )
        val q2 = q1.copy(id = "q_2", sourceQuestionId = "src_2", number = 2)
        val q3 = q1.copy(id = "q_3", sourceQuestionId = "src_3", number = 3)

        val q4 = buildSnapshotQuestion(
            number = 4,
            subject = "Matematik",
            topicHeader = "Polinomlar",
            questionType = OsymQuestionType.ANALITIK_CEBIRSEL,
            outcomeId = "KAZ_AYT_MAT_POLINOM_PARABOL",
            outcomeCode = "MAT.10.3.1.1",
            outcomeName = "Polinomlarda Bölme, Kök-Katsayı İlişkisi ve Parabol Tepe Noktası"
        )
        val q5 = q4.copy(id = "q_5", sourceQuestionId = "src_5", number = 5)

        val q6 = buildSnapshotQuestion(
            number = 6,
            subject = "Fizik",
            topicHeader = "Atışlar",
            questionType = OsymQuestionType.ONCULLU,
            outcomeId = "CUSTOM_ATIS_01",
            outcomeCode = "",
            outcomeName = "Eğik Atış ve Menzil Muhakemesi"
        )

        // Answers:
        // KAZ_TUREV_03 (GRAFIK_TABLO): 3 wrong -> 3 * 1.25 = 3.75 potential net loss
        // KAZ_AYT_MAT_POLINOM_PARABOL (ANALITIK_CEBIRSEL): 1 correct, 1 blank -> 1.00 potential net loss
        // CUSTOM_ATIS_01 (ONCULLU): 1 correct -> 0 missed (must NOT appear in topMissedOutcomes)
        val deneme = buildDenemeRecord(
            id = "ayt_deneme_1",
            examType = ExamType.AYT,
            title = "AYT Sayısal Deneme",
            timestampMillis = 10_000L,
            durationSeconds = 540,
            questions = listOf(q1, q2, q3, q4, q5, q6),
            answers = mapOf(1 to "B", 2 to "C", 3 to "D", 4 to "A", 6 to "A")
        )

        val pool = listOf(
            CustomQuestionRecord(
                id = "pool_1",
                examType = "AYT",
                subjectId = "ayt_mat",
                subjectName = "Matematik",
                topicId = "turev_uygulamalari",
                topicName = "Türevin Geometrik ve Fiziksel Uygulamaları",
                questionType = OsymQuestionType.GRAFIK_TABLO.name,
                learningOutcome = "MAT.12.5.3.1",
                questionText = "Dik koordinat düzleminde f(x) eğrisine çizilen teğet doğrusu..."
            )
        )

        val report = DenemeMemoryAnalyticsEngine.analyze(
            records = listOf(deneme),
            examType = ExamType.AYT,
            customQuestions = pool
        )

        assertEquals(2, report.topMissedOutcomes.size)
        val topLoss = report.topMissedOutcomes[0]
        assertEquals("KAZ_TUREV_03", topLoss.outcomeKey)
        assertEquals(3, topLoss.wrongCount)
        assertEquals(0, topLoss.blankCount)
        assertEquals(3.75, topLoss.potentialNetGain, 0.001)

        val secondLoss = report.topMissedOutcomes[1]
        assertEquals("KAZ_AYT_MAT_POLINOM_PARABOL", secondLoss.outcomeKey)
        assertEquals(0, secondLoss.wrongCount)
        assertEquals(1, secondLoss.blankCount)
        assertEquals(1.00, secondLoss.potentialNetGain, 0.001)

        val grafikStat = report.questionTypeMastery.first { it.type == OsymQuestionType.GRAFIK_TABLO }
        assertEquals(0, grafikStat.accuracyPercent)
        assertEquals(1, grafikStat.poolQuestionCount)

        val analitikStat = report.questionTypeMastery.first { it.type == OsymQuestionType.ANALITIK_CEBIRSEL }
        assertEquals(50, analitikStat.accuracyPercent)

        val onculluStat = report.questionTypeMastery.first { it.type == OsymQuestionType.ONCULLU }
        assertEquals(100, onculluStat.accuracyPercent)

        assertTrue(report.topicMastery.practicedTopicCount >= 2)
        assertEquals(3, report.topicMastery.coveredQuestionTypeCount)
        assertEquals(50, report.topicMastery.globalQuestionTypeCoveragePercent) // 3 / 6 types = 50%
        assertTrue(report.topicMastery.masteryScore in 1..100)
    }

    @Test
    fun istatistiklerimScreenRendersExecutiveAnalyticsAndPaginatesLargeHistoryCleanly() {
        val question = buildSnapshotQuestion(
            number = 1,
            subject = "Biyoloji",
            topicHeader = "Canlıların Temel Bileşenleri",
            correctOption = "A",
            questionType = OsymQuestionType.ONCULLU,
            outcomeId = "",
            outcomeCode = "Enzimler",
            outcomeName = ""
        )

        // 12 solved records in background memory: UI should paginate initially to 5 rows,
        // while keeping full background analytics across all 12!
        val records = (1..12).map { idx ->
            buildDenemeRecord(
                id = "hist_$idx",
                examType = ExamType.TYT,
                title = "TYT Deneme Sınavı #$idx",
                timestampMillis = idx * 100_000L,
                durationSeconds = 55,
                questions = listOf(question),
                answers = mapOf(1 to if (idx % 3 == 0) "B" else "A")
            ).copy(subjectId = "tyt_biyoloji", subjectName = "Biyoloji")
        }

        composeRule.setContent {
            MaterialTheme {
                IstatistiklerimScreen(
                    testResults = records,
                    customQuestionsCount = 48,
                    onInspectResult = {}
                )
            }
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("stats_deneme_osym_section").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("stats_net_trend_card").assertExists()
        composeRule.onNodeWithTag("stats_pace_pill").assertExists()

        // Latest record (hist_12) is visible, while older record (hist_1) is collapsed behind "Daha Fazla Göster"
        composeRule.onNodeWithTag("istatistiklerim_list")
            .performScrollToNode(hasTestTag("stats_result_card_hist_12"))
        composeRule.onNodeWithTag("stats_result_card_hist_12").assertExists()
        composeRule.onNodeWithTag("stats_result_card_hist_1").assertDoesNotExist()

        composeRule.onNodeWithTag("istatistiklerim_list")
            .performScrollToNode(hasTestTag("stats_show_more_history"))
        composeRule.onNodeWithTag("stats_show_more_history").performClick()

        composeRule.onNodeWithTag("istatistiklerim_list")
            .performScrollToNode(hasTestTag("stats_result_card_hist_3"))
        composeRule.onNodeWithTag("stats_result_card_hist_3").assertExists()
    }

    @Test
    fun deduplicatesReplicasIgnoresStaleHeadersAndRewardsMultiOutcomeTypeFamiliarity() {
        val narrowQuestions = (1..4).map { idx ->
            buildSnapshotQuestion(
                number = idx,
                subject = "Matematik",
                topicHeader = "Fonksiyonlar",
                questionType = OsymQuestionType.ANALITIK_CEBIRSEL,
                outcomeId = "KAZ_FONK_01",
                outcomeCode = "MAT.10.2.1.1",
                outcomeName = "Fonksiyon Kavramı, Tanım ve Görüntü Kümesi"
            )
        }
        val narrowAttempt = buildDenemeRecord(
            id = "dup_attempt",
            examType = ExamType.TYT,
            title = "Dar Kapsamlı Konu Denemesi",
            timestampMillis = 100_000L,
            durationSeconds = 200,
            questions = narrowQuestions,
            answers = (1..4).associateWith { "A" }
        ).copy(
            // Deliberately bogus header counts: engine must trust snapshot, not stale header
            correctCount = 0,
            wrongCount = 99
        )

        val narrowReport = DenemeMemoryAnalyticsEngine.analyze(
            records = listOf(narrowAttempt, narrowAttempt.copy()),
            examType = ExamType.TYT
        )
        assertEquals(1, narrowReport.backgroundRecordCount)
        assertEquals(4.00, narrowReport.netTrend.latestDenemeNet!!, 0.001)

        val diverseQuestions = listOf(
            buildSnapshotQuestion(
                number = 1,
                subject = "Matematik",
                topicHeader = "Fonksiyonlar",
                questionType = OsymQuestionType.ANALITIK_CEBIRSEL,
                outcomeId = "KAZ_FONK_01",
                outcomeCode = "MAT.10.2.1.1",
                outcomeName = "Fonksiyon Kavramı, Tanım ve Görüntü Kümesi"
            ),
            buildSnapshotQuestion(
                number = 2,
                subject = "Matematik",
                topicHeader = "Fonksiyonlar",
                questionType = OsymQuestionType.ONCULLU,
                outcomeId = "KAZ_FONK_02",
                outcomeCode = "MAT.10.2.1.2",
                outcomeName = "Bire Bir, Örten, İçine, Tek ve Çift Fonksiyonlar"
            ),
            buildSnapshotQuestion(
                number = 3,
                subject = "Matematik",
                topicHeader = "Fonksiyonlar",
                questionType = OsymQuestionType.GRAFIK_TABLO,
                outcomeId = "KAZ_FONK_03",
                outcomeCode = "MAT.10.2.1.3",
                outcomeName = "Bileşke ve Ters Fonksiyon Kuralları"
            ),
            buildSnapshotQuestion(
                number = 4,
                subject = "Matematik",
                topicHeader = "Fonksiyonlar",
                questionType = OsymQuestionType.YENI_NESIL_KURGULU,
                outcomeId = "KAZ_FONK_EXTRA",
                outcomeCode = "",
                outcomeName = "Gerçek Yaşam Fonksiyon Modellemesi"
            )
        )
        val diverseAttempt = buildDenemeRecord(
            id = "diverse_attempt",
            examType = ExamType.TYT,
            title = "Tam Kapsamlı ÖSYM Fonksiyon Denemesi",
            timestampMillis = 200_000L,
            durationSeconds = 200,
            questions = diverseQuestions,
            answers = (1..4).associateWith { "A" }
        )

        val diverseReport = DenemeMemoryAnalyticsEngine.analyze(
            records = listOf(diverseAttempt),
            examType = ExamType.TYT
        )

        assertTrue(
            "Multi-outcome & multi-question-type solve must achieve higher familiarity than single-outcome solve",
            diverseReport.topicMastery.familiarityScore > narrowReport.topicMastery.familiarityScore
        )
        assertTrue(
            "Multi-outcome & multi-question-type solve must achieve higher mastery score",
            diverseReport.topicMastery.masteryScore > narrowReport.topicMastery.masteryScore
        )
    }
}
