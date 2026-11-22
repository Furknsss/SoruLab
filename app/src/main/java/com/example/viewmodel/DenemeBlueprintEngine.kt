package com.example.viewmodel

import com.example.data.CustomQuestionRecord
import com.example.data.DifficultyLevel
import com.example.data.ExamType
import com.example.data.MockExamItem
import com.example.data.OsymExamAuditEngine
import com.example.data.OsymExamAuditReport
import com.example.data.OsymQuestionType
import com.example.data.QuestionVisualType
import com.example.data.SoruLabCatalog
import com.example.data.TestResultRecord
import com.example.data.TestSnapshotCodec
import com.example.data.TopicTestLevel
import com.example.data.YksQuestion
import com.example.data.YksTestItem
import com.example.data.YksTopic
import com.example.data.fourTier
import java.util.Locale

/** Official SECTION counts; see docs/DENEME_BLUEPRINT_SOURCES.md.
 * Topic weights are independent, editorial estimates based on historical
 * classification (NOT official ÖSYM future-topic promises).
 */
data class DenemeSection(
    val subjectId: String,
    val count: Int,
    val label: String,
    val allowedTopicIds: Set<String> = emptySet(),
    val profileKey: String = ""
)
data class DenemeBlueprint(
    val id: String,
    val label: String,
    val examType: ExamType,
    val minutes: Int,
    val sections: List<DenemeSection>
) {
    val questionCount: Int get() = sections.sumOf { it.count }
}
enum class DenemeKind { BRANS, KONU }
enum class DenemeDifficulty(val label: String) {
    DENGELI("Dengeli"), KOLAY("Kolay"), ORTA("Orta"), ZOR("Zor")
}
data class DenemeRequest(
    val kind: DenemeKind,
    val examType: ExamType,
    val blueprintId: String = "",
    val subjectId: String = "",
    val topicId: String = "",
    val count: Int = 12,
    val difficulty: DenemeDifficulty = DenemeDifficulty.DENGELI,
    val durationMinutes: Int = 0
)
data class DenemeShortage(
    val section: String,
    val needed: Int,
    val available: Int
)
sealed class DenemePlan {
    data class Missing(val shortage: List<DenemeShortage>) : DenemePlan()
    data class Ready(
        val test: YksTestItem,
        val minutes: Int,
        val sectionSummary: List<Pair<String, Int>>,
        val auditReport: OsymExamAuditReport? = null
    ) : DenemePlan()
}

object DenemeBlueprints {
    private fun slice(subject: String, count: Int, label: String = subject) =
        DenemeSection(subject, count, label)

    private val philosophy = setOf("felsefeyi_tanima","bilgi_felsefesi","varlik_felsefesi","ahlak_felsefesi",
        "bilim_felsefesi","din_felsefesi","siyaset_felsefesi","sanat_felsefesi")
    private val religion = setOf("bilgi_ve_inanc","din_ve_islam")

    private val aytHistory = setOf("ayt_ilk_cag_uygarliklari","ayt_osmanli_kultur_medeniyet","ayt_xx_yuzyil_osmanli","ayt_milli_mucadele","ayt_tarih_bilimi","ayt_ilk_turk_devletleri","ayt_turk_islam","ayt_osmanli_siyaset","ayt_ataturk_ilkeleri","ayt_cagdas_dunya")
    private val aytGeography = setOf("ayt_ekosistem_ve_biyoceşitlilik","ayt_turkiye_ekonomisi","ayt_kuresel_ortam_bolgeler","ayt_cevre_toplum","ayt_nufus_sehir","ayt_ticaret_kalkinma")
    private fun history(count: Int, number: Int) = DenemeSection("ayt_tarih_cografya",count,"Tarih-"+number,aytHistory,"ayt_tarih_"+number)
    private fun geography(count: Int, number: Int) = DenemeSection("ayt_tarih_cografya",count,"Coğrafya-"+number,aytGeography,"ayt_cografya_"+number)

    val branş = listOf(
        DenemeBlueprint("tyt_turkce", "TYT Türkçe", ExamType.TYT, 45, listOf(slice("tyt_turkce",40,"Türkçe"))),
        DenemeBlueprint("tyt_mat", "TYT Matematik", ExamType.TYT, 75,
            listOf(slice("tyt_mat",30,"Matematik"),slice("tyt_geo",10,"Geometri"))),
        DenemeBlueprint("tyt_sosyal", "TYT Sosyal Bilimler", ExamType.TYT, 25,
            listOf(slice("tyt_tarih",5,"Tarih"),slice("tyt_cografya",5,"Coğrafya"),
                DenemeSection("tyt_felsefe_din",5,"Felsefe",philosophy),
                DenemeSection("tyt_felsefe_din",5,"Din Kültürü",religion))),
        DenemeBlueprint("tyt_fen", "TYT Fen Bilimleri", ExamType.TYT, 25,
            listOf(slice("tyt_fizik",7,"Fizik"),slice("tyt_kimya",7,"Kimya"),slice("tyt_biyoloji",6,"Biyoloji"))),
        DenemeBlueprint("tyt_fizik", "TYT Fizik", ExamType.TYT, 12, listOf(slice("tyt_fizik",7,"Fizik"))),
        DenemeBlueprint("tyt_kimya", "TYT Kimya", ExamType.TYT, 12, listOf(slice("tyt_kimya",7,"Kimya"))),
        DenemeBlueprint("tyt_biyoloji", "TYT Biyoloji", ExamType.TYT, 11, listOf(slice("tyt_biyoloji",6,"Biyoloji"))),
        DenemeBlueprint("tyt_tarih", "TYT Tarih", ExamType.TYT, 9, listOf(slice("tyt_tarih",5,"Tarih"))),
        DenemeBlueprint("tyt_cografya", "TYT Coğrafya", ExamType.TYT, 9, listOf(slice("tyt_cografya",5,"Coğrafya"))),
        DenemeBlueprint("tyt_felsefe", "TYT Felsefe", ExamType.TYT, 9,
            listOf(DenemeSection("tyt_felsefe_din",5,"Felsefe",philosophy))),
        DenemeBlueprint("tyt_din", "TYT Din Kültürü", ExamType.TYT, 9,
            listOf(DenemeSection("tyt_felsefe_din",5,"Din Kültürü",religion))),
        DenemeBlueprint("ayt_sosyal1", "AYT Edebiyat – Sosyal-1", ExamType.AYT, 75,
            listOf(slice("ayt_edebiyat",24,"Türk Dili ve Edebiyatı"),history(10,1),geography(6,1))),
        DenemeBlueprint("ayt_sosyal2", "AYT Sosyal Bilimler-2", ExamType.AYT, 70,
            listOf(history(11,2),geography(11,2),slice("ayt_felsefe_grubu",12,"Felsefe Grubu"),slice("ayt_din",6,"Din Kültürü"))),
        DenemeBlueprint("ayt_tarih1", "AYT Tarih-1", ExamType.AYT, 18,listOf(history(10,1))),
        DenemeBlueprint("ayt_tarih2", "AYT Tarih-2", ExamType.AYT, 20,listOf(history(11,2))),
        DenemeBlueprint("ayt_cografya1", "AYT Coğrafya-1", ExamType.AYT, 12,listOf(geography(6,1))),
        DenemeBlueprint("ayt_cografya2", "AYT Coğrafya-2", ExamType.AYT, 20,listOf(geography(11,2))),
        DenemeBlueprint("ayt_felsefe_grubu", "AYT Felsefe Grubu", ExamType.AYT, 22,listOf(slice("ayt_felsefe_grubu",12,"Felsefe Grubu"))),
        DenemeBlueprint("ayt_din", "AYT Din Kültürü", ExamType.AYT, 12,listOf(slice("ayt_din",6,"Din Kültürü"))),
        DenemeBlueprint("ayt_mat", "AYT Matematik", ExamType.AYT, 90,
            listOf(slice("ayt_mat",31,"Matematik"),slice("ayt_geo",9,"Geometri"))),
        DenemeBlueprint("ayt_fen", "AYT Fen Bilimleri", ExamType.AYT, 80,
            listOf(slice("ayt_fizik",14,"Fizik"),slice("ayt_kimya",13,"Kimya"),slice("ayt_biyoloji",13,"Biyoloji"))),
        DenemeBlueprint("ayt_fizik", "AYT Fizik", ExamType.AYT, 30, listOf(slice("ayt_fizik",14,"Fizik"))),
        DenemeBlueprint("ayt_kimya", "AYT Kimya", ExamType.AYT, 25, listOf(slice("ayt_kimya",13,"Kimya"))),
        DenemeBlueprint("ayt_biyoloji", "AYT Biyoloji", ExamType.AYT, 25, listOf(slice("ayt_biyoloji",13,"Biyoloji"))),
        DenemeBlueprint("ayt_edebiyat", "AYT Edebiyat", ExamType.AYT, 36, listOf(slice("ayt_edebiyat",24,"Türk Dili ve Edebiyatı")))
    )
    fun find(id: String): DenemeBlueprint? = branş.firstOrNull { it.id == id }
    fun forType(examType: ExamType) = branş.filter { it.examType == examType }
    fun fromSnapshot(test: YksTestItem): MockExamItem? {
        if (!test.id.startsWith("deneme_v147_")) return null
        val parts = test.id.split("_")
        val exam = if (parts.getOrNull(2) == "AYT") ExamType.AYT else ExamType.TYT
        val duration = parts.getOrNull(4)?.toIntOrNull()?.coerceIn(1,240)
            ?: (test.questions.size * 1.7f).toInt().coerceAtLeast(5)
        return MockExamItem(
            id = test.id, title = test.fullTitle, publisher = "SoruLab",
            examType = exam, questionCount = test.questions.size,
            durationMinutes = duration, difficulty = test.difficulty,
            tag = "Konu ağırlıklı • Doğrulanmış kitaplık", linkedTest = test
        )
    }
}

/** Never label inferred topic counts as mandatory ÖSYM slots.
 * Explicit topic quotas, publisher diversity, and repeat avoidance.
 */
object DenemePlanner {
    private val keys = setOf("A","B","C","D","E")
    private fun matchedTopic(q: CustomQuestionRecord): YksTopic? =
        SoruLabCatalog.allSubjects.firstOrNull { it.id == q.subjectId }
            ?.topics?.firstOrNull {
                it.id == q.topicId && TopicIntegrityPolicy.isSuitable(q,it)
            }

    fun eligible(
        record: CustomQuestionRecord,
        difficulty: DenemeDifficulty
    ): Boolean {
        if (record.id.isBlank() || !record.answerKeyVerified ||
            record.correctOption.uppercase(Locale.ROOT) !in keys ||
            (record.questionText.isBlank() && record.croppedImagePath.isBlank() &&
                record.imageBase64.isBlank()) || matchedTopic(record) == null) return false
        return when (difficulty) {
            DenemeDifficulty.DENGELI -> true
            DenemeDifficulty.KOLAY -> record.difficultyLevel5 == DifficultyLevel.KOLAY
            DenemeDifficulty.ORTA -> record.difficultyLevel5 == DifficultyLevel.ORTA
            DenemeDifficulty.ZOR -> record.difficultyLevel5 in setOf(DifficultyLevel.ZOR,DifficultyLevel.COK_ZOR)
        }
    }

    fun sections(request: DenemeRequest): List<DenemeSection>? {
        if (request.kind == DenemeKind.BRANS) {
            return DenemeBlueprints.find(request.blueprintId)
                ?.takeIf { it.examType == request.examType }?.sections
        }
        val subject = SoruLabCatalog.allSubjects.firstOrNull {
            it.id == request.subjectId && it.examType == request.examType
        } ?: return null
        val topic = subject.topics.firstOrNull { it.id == request.topicId } ?: return null
        return listOf(DenemeSection(subject.id, request.count.coerceIn(1,40), topic.name, setOf(topic.id)))
    }

    private fun pickSlice(
        candidates: List<CustomQuestionRecord>,
        count: Int,
        priorIds: Set<String>,
        alreadySelected: List<CustomQuestionRecord> = emptyList()
    ): List<CustomQuestionRecord> {
        val available = candidates.distinctBy { it.id }
            .groupBy { it.topicId }.mapValues { (_, items) ->
                items.sortedWith(
                    compareBy<CustomQuestionRecord>(
                        { if (it.id in priorIds) 1 else 0 },
                        { it.publisherTag.lowercase(Locale.ROOT) },
                        { it.sourceTestId },
                        { it.questionNumberInBook },
                        { it.id }
                    )
                ).toMutableList()
            }.toMutableMap()
        val selected = mutableListOf<CustomQuestionRecord>()
        val topicCounts = mutableMapOf<String, Int>()
        val sourceCounts = alreadySelected.groupingBy { it.publisherTag + "/" + it.bookOrTestName }.eachCount().toMutableMap()
        val difficultyCounts = alreadySelected.groupingBy { it.difficultyLevel5 }.eachCount().toMutableMap()
        val questionTypeCounts = alreadySelected.groupingBy { OsymQuestionType.classifyCustomQuestion(it) }.eachCount().toMutableMap()
        val outcomeCounts = alreadySelected.groupingBy { outcomeKeyFor(it) }.eachCount().toMutableMap()
        val difficultyShares = mapOf(DifficultyLevel.KOLAY to 1.0, DifficultyLevel.ORTA to 2.0,
            DifficultyLevel.ZOR to 1.0, DifficultyLevel.COK_ZOR to 0.5)
        repeat(count) {
            val nonEmpty = available.filterValues { it.isNotEmpty() }
            if (nonEmpty.isEmpty()) return@repeat
            // Distribute within this quota only. Prefer fresh subtopics first.
            val topic = nonEmpty.keys.sortedWith(
                compareBy<String> { topic -> if (nonEmpty.getValue(topic).any { it.id !in priorIds }) 0 else 1 }
                .thenByDescending { 1.0 / ((topicCounts[it] ?: 0) + 1) }.thenBy { it }
            ).first()
            val list = available.getValue(topic)
            val next = list.minWithOrNull(
                compareBy<CustomQuestionRecord>(
                    { if (it.id in priorIds) 8 else 0 },
                    { -((difficultyShares[it.difficultyLevel5] ?: 1.0) / ((difficultyCounts[it.difficultyLevel5] ?: 0) + 1)) },
                    { (outcomeCounts[outcomeKeyFor(it)] ?: 0) * 2 },
                    { (questionTypeCounts[OsymQuestionType.classifyCustomQuestion(it)] ?: 0) * 2 },
                    { (sourceCounts[it.publisherTag + "/" + it.bookOrTestName] ?: 0) * 3 },
                    { it.sourceTestId },
                    { it.questionNumberInBook },
                    { it.id }
                )
            ) ?: return@repeat
            list.remove(next)
            selected.add(next)
            topicCounts[topic] = (topicCounts[topic] ?: 0) + 1
            val source = next.publisherTag + "/" + next.bookOrTestName
            sourceCounts[source] = (sourceCounts[source] ?: 0) + 1
            difficultyCounts[next.difficultyLevel5] = (difficultyCounts[next.difficultyLevel5] ?: 0) + 1
            val qType = OsymQuestionType.classifyCustomQuestion(next)
            questionTypeCounts[qType] = (questionTypeCounts[qType] ?: 0) + 1
            val oKey = outcomeKeyFor(next)
            outcomeCounts[oKey] = (outcomeCounts[oKey] ?: 0) + 1
        }
        return selected
    }

    private fun outcomeKeyFor(record: CustomQuestionRecord): String =
        record.resolvedLearningOutcome.id.ifBlank {
            record.learningOutcome.ifBlank { record.topicId }
        }

    fun auditTest(test: YksTestItem): OsymExamAuditReport = OsymExamAuditEngine.auditTest(test)

    fun questionTypeDiversityScore(questions: List<YksQuestion>): Int {
        if (questions.isEmpty()) return 0
        val distinctTypes = questions.map { OsymQuestionType.fromRawLabel(it.questionType) ?: OsymQuestionType.classifyQuestion(it) }.distinct().size
        return ((distinctTypes.toDouble() / OsymQuestionType.entries.size.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }

    fun build(
        request: DenemeRequest,
        pool: List<CustomQuestionRecord>,
        previous: List<TestResultRecord> = emptyList(),
        sequence: Long = System.currentTimeMillis()
    ): DenemePlan {
        val slices = sections(request) ?: return DenemePlan.Missing(listOf(DenemeShortage("Geçersiz deneme seçimi",1,0)))
        val previousIds = previous.sortedByDescending { it.createdAt?.seconds ?: 0L }.take(6).flatMap { record ->
            TestSnapshotCodec.decode(record.testSnapshotJson)?.questions.orEmpty()
                .map { it.sourceQuestionId }
        }.filter { it.isNotBlank() }.toSet()
        val examPool = pool.filter { it.examType.equals(request.examType.name,true) &&
            eligible(it,request.difficulty) }.distinctBy { it.id }
        val selected = mutableListOf<CustomQuestionRecord>()
        val shortages = mutableListOf<DenemeShortage>()
        slices.forEach { section ->
            val quotas = if (request.kind == DenemeKind.BRANS) DenemeTopicProfiles.forSection(section)
                else listOf(DenemeTopicQuota(section.label, section.count, section.allowedTopicIds))
            quotas.forEach { quota ->
                val candidates = examPool.filter {
                    it.subjectId == section.subjectId && it.topicId in quota.topicIds &&
                        (section.allowedTopicIds.isEmpty() || it.topicId in section.allowedTopicIds)
                }.filterNot { q -> selected.any { it.id == q.id } }
                if (candidates.size < quota.count) {
                    shortages.add(DenemeShortage(section.label + " • " + quota.label, quota.count, candidates.size))
                } else {
                    selected.addAll(pickSlice(candidates, quota.count, previousIds, selected))
                }
            }
        }
        if (shortages.isNotEmpty()) return DenemePlan.Missing(shortages)
        val expected = slices.sumOf { it.count }
        if (selected.size != expected || selected.map { it.id }.distinct().size != expected)
            return DenemePlan.Missing(listOf(DenemeShortage("Benzersiz soru",expected,selected.size)))
        val blueprint = DenemeBlueprints.find(request.blueprintId)?.takeIf { request.kind == DenemeKind.BRANS }
        val title = if (request.kind == DenemeKind.BRANS)
            blueprint?.label ?: "Branş Denemesi"
        else {
            val subject = SoruLabCatalog.allSubjects.first { it.id == request.subjectId }
            val topic = subject.topics.first { it.id == request.topicId }
            subject.examType.name + " " + subject.name + " • " + topic.name
        }
        val minutes = (request.durationMinutes.takeIf { it > 0 }
            ?: blueprint?.minutes ?: (expected * 1.8f).toInt()).coerceIn(5,240)
        val id = "deneme_v147_" + request.examType.name + "_" + request.kind.name +
            "_" + minutes + "_" + sequence
        val questions = selected.mapIndexed { index, record ->
            val subject = SoruLabCatalog.allSubjects.first { it.id == record.subjectId }
            val topic = subject.topics.first { it.id == record.topicId }
            YksQuestion(
                id = id + "_q" + (index+1),
                number = index+1,
                badgeTitle = title.uppercase(Locale.forLanguageTag("tr-TR")),
                topicHeader = topic.name,
                publisherStamp = record.publisherTag,
                publisherName = record.publisherTag,
                publisherLogoUri = record.publisherLogoUri,
                croppedImagePath = record.croppedImagePath,
                answerKeyVerified = true,
                stemTop = if (record.croppedImagePath.isNotBlank() || record.imageBase64.isNotBlank()) "" else record.questionText,
                stemBottom = "",
                options = mapOf("A" to "A","B" to "B","C" to "C","D" to "D","E" to "E"),
                correctOption = record.correctOption.uppercase(Locale.ROOT),
                difficulty = record.difficultyLevel5,
                visualType = QuestionVisualType.NONE,
                stepByStepSolution = record.cleanSolutionSummary,
                bookId = record.bookId,
                sourceQuestionId = record.id,
                subject = if (request.kind == DenemeKind.KONU) subject.name else slices.firstOrNull { it.subjectId == record.subjectId &&
                    (it.allowedTopicIds.isEmpty() || record.topicId in it.allowedTopicIds) }?.label ?: subject.name,
                sourceBookTitle = record.sourceBookTitle.ifBlank { record.bookOrTestName },
                sourceTestId = record.sourceTestId,
                sourcePageIndex = record.pageIndex,
                sourcePrintedQuestionNumber = record.questionNumberInBook,
                learningOutcome = record.learningOutcome,
                questionType = record.questionType.ifBlank { OsymQuestionType.classifyCustomQuestion(record).name },
                learningOutcomeId = record.resolvedLearningOutcome.id,
                learningOutcomeName = record.resolvedLearningOutcome.name,
                learningOutcomeSource = record.resolvedLearningOutcome.source,
                learningOutcomeConfidence = record.resolvedLearningOutcome.confidence,
                analyticsText = record.questionText,
                isOsymType = record.isOsymType,
                imageBase64 = record.imageBase64,
                pdfFilePath = record.pdfFilePath,
                pageIndex = record.pageIndex,
                questionNumberInTest = record.questionNumberInTest,
                normLeft = record.normLeft, normTop = record.normTop,
                normRight = record.normRight, normBottom = record.normBottom
            )
        }
        val avgWeight = questions.map { it.difficulty.weight }.average().toFloat()
        val difficulty = when {
            avgWeight < .3f -> DifficultyLevel.KOLAY
            avgWeight > .6f -> DifficultyLevel.ZOR
            else -> DifficultyLevel.ORTA
        }
        val builtTest = YksTestItem(id,selected.first().topicId,title + " • " + expected + " Soru",
            title + " • " + expected + " Soru",difficulty,avgWeight.coerceIn(.1f,.95f),
            false,0,TopicTestLevel.SEVIYE_TESPIT,questions,
            denemeProfileVersion = DenemeTopicProfiles.VERSION,
            denemeDifficultyLabel = request.difficulty.label,
            denemeReusedQuestionCount = selected.count { it.id in previousIds },
            denemeDistributionSummary = slices.joinToString("\n") { section ->
                section.label + ": " + if (request.kind == DenemeKind.BRANS)
                    DenemeTopicProfiles.forSection(section).joinToString(" • ") { it.label + " " + it.count }
                else section.count.toString() + " soru"
            })
        val auditReport = OsymExamAuditEngine.auditTest(builtTest)
        return DenemePlan.Ready(
            test = builtTest,
            minutes = minutes,
            sectionSummary = slices.map { it.label to it.count },
            auditReport = auditReport
        )
    }
}
