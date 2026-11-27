package com.example.viewmodel

import com.example.data.CropQualityGate
import com.example.data.CustomQuestionRecord
import com.example.data.DifficultyLevel
import com.example.data.ExamType
import com.example.data.MockExamItem
import com.example.data.Osym2018To2026Intelligence
import com.example.data.OsymExamAuditEngine
import com.example.data.OsymExamAuditReport
import com.example.data.OsymQuestionType
import com.example.data.OsymResolvedQuestionMeta
import com.example.data.QuestionVisualType
import com.example.data.SoruLabCatalog
import com.example.data.StudentOsymMasteryMemory
import com.example.data.TestResultRecord
import com.example.data.TestSnapshotCodec
import com.example.data.TopicTestLevel
import com.example.data.YksQuestion
import com.example.data.YksTestItem
import com.example.data.YksTopic
import com.example.data.fourTier
import java.util.Locale
import kotlin.math.roundToInt

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

enum class DenemeTimerMode(val label: String) {
    COUNTDOWN("Süreli (Geri Sayım)"),
    STOPWATCH("Kronometre (00:00)")
}

data class DenemeRequest(
    val kind: DenemeKind,
    val examType: ExamType,
    val blueprintId: String = "",
    val subjectId: String = "",
    val topicId: String = "",
    val count: Int = 12,
    val difficulty: DenemeDifficulty = DenemeDifficulty.DENGELI,
    val durationMinutes: Int = 0,
    val timerMode: DenemeTimerMode = DenemeTimerMode.COUNTDOWN
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

    private val philosophy = setOf(
        "felsefeyi_tanima", "bilgi_felsefesi", "varlik_felsefesi", "ahlak_felsefesi",
        "bilim_felsefesi", "din_felsefesi", "siyaset_felsefesi", "sanat_felsefesi"
    )
    private val religion = setOf("bilgi_ve_inanc", "din_ve_islam")

    private val aytHistory = setOf(
        "ayt_ilk_cag_uygarliklari", "ayt_osmanli_kultur_medeniyet", "ayt_xx_yuzyil_osmanli",
        "ayt_milli_mucadele", "ayt_tarih_bilimi", "ayt_ilk_turk_devletleri",
        "ayt_turk_islam", "ayt_osmanli_siyaset", "ayt_ataturk_ilkeleri", "ayt_cagdas_dunya"
    )
    private val aytGeography = setOf(
        "ayt_ekosistem_ve_biyoceşitlilik", "ayt_turkiye_ekonomisi", "ayt_kuresel_ortam_bolgeler",
        "ayt_cevre_toplum", "ayt_nufus_sehir", "ayt_ticaret_kalkinma"
    )
    private fun history(count: Int, number: Int) =
        DenemeSection("ayt_tarih_cografya", count, "Tarih-$number", aytHistory, "ayt_tarih_$number")
    private fun geography(count: Int, number: Int) =
        DenemeSection("ayt_tarih_cografya", count, "Coğrafya-$number", aytGeography, "ayt_cografya_$number")

    val branş = listOf(
        DenemeBlueprint("tyt_turkce", "TYT Türkçe", ExamType.TYT, 45, listOf(slice("tyt_turkce", 40, "Türkçe"))),
        DenemeBlueprint(
            "tyt_mat", "TYT Matematik", ExamType.TYT, 75,
            listOf(slice("tyt_mat", 30, "Matematik"), slice("tyt_geo", 10, "Geometri"))
        ),
        DenemeBlueprint(
            "tyt_sosyal", "TYT Sosyal Bilimler", ExamType.TYT, 25,
            listOf(
                slice("tyt_tarih", 5, "Tarih"), slice("tyt_cografya", 5, "Coğrafya"),
                DenemeSection("tyt_felsefe_din", 5, "Felsefe", philosophy),
                DenemeSection("tyt_felsefe_din", 5, "Din Kültürü", religion)
            )
        ),
        DenemeBlueprint(
            "tyt_fen", "TYT Fen Bilimleri", ExamType.TYT, 25,
            listOf(slice("tyt_fizik", 7, "Fizik"), slice("tyt_kimya", 7, "Kimya"), slice("tyt_biyoloji", 6, "Biyoloji"))
        ),
        DenemeBlueprint("tyt_fizik", "TYT Fizik", ExamType.TYT, 12, listOf(slice("tyt_fizik", 7, "Fizik"))),
        DenemeBlueprint("tyt_kimya", "TYT Kimya", ExamType.TYT, 12, listOf(slice("tyt_kimya", 7, "Kimya"))),
        DenemeBlueprint("tyt_biyoloji", "TYT Biyoloji", ExamType.TYT, 11, listOf(slice("tyt_biyoloji", 6, "Biyoloji"))),
        DenemeBlueprint("tyt_tarih", "TYT Tarih", ExamType.TYT, 9, listOf(slice("tyt_tarih", 5, "Tarih"))),
        DenemeBlueprint("tyt_cografya", "TYT Coğrafya", ExamType.TYT, 9, listOf(slice("tyt_cografya", 5, "Coğrafya"))),
        DenemeBlueprint(
            "tyt_felsefe", "TYT Felsefe", ExamType.TYT, 9,
            listOf(DenemeSection("tyt_felsefe_din", 5, "Felsefe", philosophy))
        ),
        DenemeBlueprint(
            "tyt_din", "TYT Din Kültürü", ExamType.TYT, 9,
            listOf(DenemeSection("tyt_felsefe_din", 5, "Din Kültürü", religion))
        ),
        DenemeBlueprint(
            "ayt_sosyal1", "AYT Edebiyat – Sosyal-1", ExamType.AYT, 75,
            listOf(slice("ayt_edebiyat", 24, "Türk Dili ve Edebiyatı"), history(10, 1), geography(6, 1))
        ),
        DenemeBlueprint(
            "ayt_sosyal2", "AYT Sosyal Bilimler-2", ExamType.AYT, 70,
            listOf(history(11, 2), geography(11, 2), slice("ayt_felsefe_grubu", 12, "Felsefe Grubu"), slice("ayt_din", 6, "Din Kültürü"))
        ),
        DenemeBlueprint("ayt_tarih1", "AYT Tarih-1", ExamType.AYT, 18, listOf(history(10, 1))),
        DenemeBlueprint("ayt_tarih2", "AYT Tarih-2", ExamType.AYT, 20, listOf(history(11, 2))),
        DenemeBlueprint("ayt_cografya1", "AYT Coğrafya-1", ExamType.AYT, 12, listOf(geography(6, 1))),
        DenemeBlueprint("ayt_cografya2", "AYT Coğrafya-2", ExamType.AYT, 20, listOf(geography(11, 2))),
        DenemeBlueprint("ayt_felsefe_grubu", "AYT Felsefe Grubu", ExamType.AYT, 22, listOf(slice("ayt_felsefe_grubu", 12, "Felsefe Grubu"))),
        DenemeBlueprint("ayt_din", "AYT Din Kültürü", ExamType.AYT, 12, listOf(slice("ayt_din", 6, "Din Kültürü"))),
        DenemeBlueprint(
            "ayt_mat", "AYT Matematik", ExamType.AYT, 90,
            listOf(slice("ayt_mat", 31, "Matematik"), slice("ayt_geo", 9, "Geometri"))
        ),
        DenemeBlueprint(
            "ayt_fen", "AYT Fen Bilimleri", ExamType.AYT, 80,
            listOf(slice("ayt_fizik", 14, "Fizik"), slice("ayt_kimya", 13, "Kimya"), slice("ayt_biyoloji", 13, "Biyoloji"))
        ),
        DenemeBlueprint("ayt_fizik", "AYT Fizik", ExamType.AYT, 30, listOf(slice("ayt_fizik", 14, "Fizik"))),
        DenemeBlueprint("ayt_kimya", "AYT Kimya", ExamType.AYT, 25, listOf(slice("ayt_kimya", 13, "Kimya"))),
        DenemeBlueprint("ayt_biyoloji", "AYT Biyoloji", ExamType.AYT, 25, listOf(slice("ayt_biyoloji", 13, "Biyoloji"))),
        DenemeBlueprint("ayt_edebiyat", "AYT Edebiyat", ExamType.AYT, 36, listOf(slice("ayt_edebiyat", 24, "Türk Dili ve Edebiyatı")))
    )

    fun find(id: String): DenemeBlueprint? = branş.firstOrNull { it.id == id }
    fun forType(examType: ExamType) = branş.filter { it.examType == examType }

    fun fromSnapshot(test: YksTestItem): MockExamItem? {
        if (!test.id.startsWith("deneme_v147_")) return null
        val parts = test.id.split("_")
        val exam = if (parts.getOrNull(2) == "AYT") ExamType.AYT else ExamType.TYT
        val rawMinutes = parts.getOrNull(4)?.toIntOrNull()
        val duration = when {
            rawMinutes == null -> (test.questions.size * 1.7f).toInt().coerceAtLeast(5)
            rawMinutes <= 0 -> 0 // 0 = Kronometre (00:00'dan ileri sayım)
            else -> rawMinutes.coerceIn(1, 240)
        }
        val tagPrefix = if (duration == 0) "Kronometre • " else ""
        return MockExamItem(
            id = test.id,
            title = test.fullTitle,
            publisher = "SoruLab",
            examType = exam,
            questionCount = test.questions.size,
            durationMinutes = duration,
            difficulty = test.difficulty,
            tag = tagPrefix + "Konu ağırlıklı • Doğrulanmış kitaplık",
            linkedTest = test
        )
    }
}

/** Never label inferred topic counts as mandatory ÖSYM slots.
 * Explicit topic quotas, 2018–2026 ÖSYM outcome frequency intelligence,
 * student background weakness prioritization, 6-question-type diversity,
 * crop quality filtering, publisher diversity, and repeat avoidance.
 */
object DenemePlanner {
    private val keys = setOf("A", "B", "C", "D", "E")
    private const val MAX_META_CACHE_SIZE = 65_536
    private val resolvedMetaCache = java.util.concurrent.ConcurrentHashMap<String, OsymResolvedQuestionMeta>(4096)

    private fun cachedMetaFor(record: CustomQuestionRecord): OsymResolvedQuestionMeta {
        val cacheKey = "${record.id}|${record.topicId}|${record.aiSolutionSummary.hashCode()}"
        resolvedMetaCache[cacheKey]?.let { return it }
        val resolved = Osym2018To2026Intelligence.resolveOutcomeForRecord(record)
        if (resolvedMetaCache.size >= MAX_META_CACHE_SIZE) {
            resolvedMetaCache.clear()
        }
        resolvedMetaCache[cacheKey] = resolved
        return resolved
    }

    private fun matchedTopic(q: CustomQuestionRecord): YksTopic? =
        SoruLabCatalog.allSubjects.firstOrNull { it.id == q.subjectId }
            ?.topics?.firstOrNull {
                it.id == q.topicId && TopicIntegrityPolicy.isSuitable(q, it)
            }

    fun eligible(
        record: CustomQuestionRecord,
        difficulty: DenemeDifficulty
    ): Boolean {
        if (record.id.isBlank() ||
            !record.answerKeyVerified ||
            record.correctOption.uppercase(Locale.ROOT) !in keys ||
            (record.questionText.isBlank() && record.croppedImagePath.isBlank() && record.imageBase64.isBlank()) ||
            !CropQualityGate.isCleanQuestionCrop(record) ||
            matchedTopic(record) == null
        ) {
            return false
        }
        return when (difficulty) {
            DenemeDifficulty.DENGELI -> true
            DenemeDifficulty.KOLAY -> record.difficultyLevel5 == DifficultyLevel.KOLAY
            DenemeDifficulty.ORTA -> record.difficultyLevel5 == DifficultyLevel.ORTA
            DenemeDifficulty.ZOR -> record.difficultyLevel5 in setOf(DifficultyLevel.ZOR, DifficultyLevel.COK_ZOR)
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
        return listOf(DenemeSection(subject.id, request.count.coerceIn(1, 40), topic.name, setOf(topic.id)))
    }

    private fun pickSlice(
        candidates: List<CustomQuestionRecord>,
        count: Int,
        exposureById: Map<String, Int>,
        metaCache: Map<String, OsymResolvedQuestionMeta>,
        masteryMemory: StudentOsymMasteryMemory,
        alreadySelected: List<CustomQuestionRecord> = emptyList(),
        orderedTopicIds: List<String> = emptyList(),
        sortPedagogically: Boolean = false
    ): List<CustomQuestionRecord> {
        val distinctCandidates = candidates.distinctBy { it.id }
        val available = distinctCandidates
            .groupBy { it.topicId }
            .mapValues { (_, items) ->
                items.sortedWith(
                    compareBy<CustomQuestionRecord>(
                        { exposureById[it.id] ?: 0 },
                        { it.publisherTag.lowercase(Locale.ROOT) },
                        { it.sourceTestId },
                        { it.questionNumberInBook },
                        { it.id }
                    )
                ).toMutableList()
            }.toMutableMap()

        val topicOrderIndex = orderedTopicIds.withIndex().associate { (idx, id) -> id to idx }
        val topicOsymShare = distinctCandidates.map { it.subjectId to it.topicId }
            .distinct()
            .associate { (subjId, topId) ->
                val profile = Osym2018To2026Intelligence.profileForTopic(subjId, topId)
                val missTopicBoost = (masteryMemory.missedTopicWeights[Osym2018To2026Intelligence.normalizeKey(topId)] ?: 0.0)
                    .coerceAtMost(6.0) * 0.25
                topId to (profile.historicalShareWeight + missTopicBoost)
            }

        val selected = mutableListOf<CustomQuestionRecord>()
        val topicCounts = mutableMapOf<String, Int>()
        val sourceCounts = alreadySelected.groupingBy { sourceKeyFor(it, metaCache) }.eachCount().toMutableMap()
        val difficultyCounts = alreadySelected.groupingBy { it.difficultyLevel5 }.eachCount().toMutableMap()
        val questionTypeCounts = alreadySelected.groupingBy {
            metaCache[it.id]?.questionType ?: OsymQuestionType.classifyCustomQuestion(it)
        }.eachCount().toMutableMap()
        val outcomeCounts = alreadySelected.groupingBy { outcomeKeyFor(it, metaCache) }.eachCount().toMutableMap()

        val difficultyShares = mapOf(
            DifficultyLevel.KOLAY to 1.0,
            DifficultyLevel.ORTA to 2.0,
            DifficultyLevel.ZOR to 1.0,
            DifficultyLevel.COK_ZOR to 0.5
        )

        repeat(count) {
            val nonEmpty = available.filterValues { it.isNotEmpty() }
            if (nonEmpty.isEmpty()) return@repeat

            // Alt konuları önce hiç görülmemiş taze sorusu olanlara, ardından en az tüketilmiş olanlara ve 2018-2026 ÖSYM ağırlığına göre dağıt
            val topic = nonEmpty.keys.sortedWith(
                compareBy<String> { t -> nonEmpty.getValue(t).minOf { exposureById[it.id] ?: 0 } / 15 }
                    .thenBy { topicCounts[it] ?: 0 }
                    .thenByDescending { ((topicOsymShare[it] ?: 4.0) * 100.0).roundToInt() }
                    .thenBy { topicOrderIndex[it] ?: Int.MAX_VALUE }
                    .thenBy { it }
            ).first()

            val list = available.getValue(topic)
            val next = list.minWithOrNull(
                compareBy<CustomQuestionRecord>(
                    // 1. Dinamik Canlı Yenileme: 0 (hiç görülmemiş) < 15..35 (kayıtlı bekleyen denemede) < 65..95 (1 kez çözülmüş ve yanlış/boş) < 100..125 (1 kez doğru çözülmüş) < 200+ (çoklu çözülmüş)
                    { exposureById[it.id] ?: 0 },
                    // 2. 2018-2026 ÖSYM kazanım frekansı + öğrencinin yanlış/boş kazanımı + tam kazanım ve 6 soru tipi kapsama skoru
                    { record ->
                        val meta = metaCache[record.id] ?: cachedMetaFor(record)
                        val oKey = outcomeKeyFor(record, metaCache)
                        val qType = meta.questionType
                        val sKey = sourceKeyFor(record, metaCache)

                        val outcomeUsed = outcomeCounts[oKey] ?: 0
                        val typeUsed = questionTypeCounts[qType] ?: 0
                        val sourceUsed = sourceCounts[sKey] ?: 0
                        val diffShare = (difficultyShares[record.difficultyLevel5] ?: 1.0) /
                            ((difficultyCounts[record.difficultyLevel5] ?: 0) + 1)

                        val uncoveredOutcomeBonus = if (outcomeUsed == 0) 6.5 else -(outcomeUsed * 3.2)
                        val uncoveredTypeBonus = if (typeUsed == 0) 5.5 else -(typeUsed * 2.4)
                        val studentWeakOutcomeBoost = masteryMemory
                            .missBoostForOutcome(meta.outcomeId, meta.outcomeTitle, record.topicId)
                            .coerceAtMost(8.0) * 1.85
                        val studentWeakTypeBoost = masteryMemory
                            .missBoostForQuestionType(record.topicId, qType)
                            .coerceAtMost(5.0) * 1.15
                        val masteredPenalty = if (
                            Osym2018To2026Intelligence.normalizeKey(meta.outcomeId) in masteryMemory.masteredOutcomeKeys
                        ) 1.0 else 0.0
                        val osymFrequencyBoost = meta.osymFrequencyWeight * 0.90
                        val osymTagBonus = if (record.isOsymType) 0.75 else 0.0
                        val difficultyBalance = diffShare * 2.2
                        val sourceRepeatPenalty = sourceUsed * 2.5

                        val totalMasteryPriority = uncoveredOutcomeBonus +
                            uncoveredTypeBonus +
                            studentWeakOutcomeBoost +
                            studentWeakTypeBoost -
                            masteredPenalty +
                            osymFrequencyBoost +
                            osymTagBonus +
                            difficultyBalance -
                            sourceRepeatPenalty

                        -(totalMasteryPriority * 1000.0).roundToInt()
                    },
                    { it.publisherTag.lowercase(Locale.ROOT) },
                    { it.sourceTestId },
                    { it.questionNumberInBook },
                    { it.id }
                )
            ) ?: return@repeat

            list.remove(next)
            selected.add(next)
            topicCounts[topic] = (topicCounts[topic] ?: 0) + 1
            val sKey = sourceKeyFor(next, metaCache)
            sourceCounts[sKey] = (sourceCounts[sKey] ?: 0) + 1
            difficultyCounts[next.difficultyLevel5] = (difficultyCounts[next.difficultyLevel5] ?: 0) + 1
            val qType = metaCache[next.id]?.questionType ?: OsymQuestionType.classifyCustomQuestion(next)
            questionTypeCounts[qType] = (questionTypeCounts[qType] ?: 0) + 1
            val oKey = outcomeKeyFor(next, metaCache)
            outcomeCounts[oKey] = (outcomeCounts[oKey] ?: 0) + 1
        }

        // Gerçek ÖSYM kitapçık içi sıralama: alt konu sırası + pedagojik zorluk akışı (Kolay -> Orta -> Zor)
        return selected.sortedWith(
            compareBy<CustomQuestionRecord>(
                { topicOrderIndex[it.topicId] ?: Int.MAX_VALUE },
                { if (sortPedagogically) it.difficultyLevel5.ordinal else 0 },
                { outcomeKeyFor(it, metaCache) },
                { it.id }
            )
        )
    }

    private fun outcomeKeyFor(
        record: CustomQuestionRecord,
        metaCache: Map<String, OsymResolvedQuestionMeta>
    ): String = metaCache[record.id]?.outcomeId?.takeIf { it.isNotBlank() }
        ?: record.resolvedLearningOutcome.id.ifBlank {
            record.learningOutcome.ifBlank { record.topicId }
        }

    private fun sourceKeyFor(
        record: CustomQuestionRecord,
        metaCache: Map<String, OsymResolvedQuestionMeta>
    ): String {
        val meta = metaCache[record.id]
        return if (meta != null) {
            "${meta.cleanPublisherName}/${meta.cleanBookTitle}"
        } else {
            "${record.publisherTag}/${record.bookOrTestName}"
        }
    }

    fun auditTest(test: YksTestItem): OsymExamAuditReport = OsymExamAuditEngine.auditTest(test)

    fun questionTypeDiversityScore(questions: List<YksQuestion>): Int {
        if (questions.isEmpty()) return 0
        val distinctTypes = questions.map {
            OsymQuestionType.fromRawLabel(it.questionType) ?: OsymQuestionType.classifyQuestion(it)
        }.distinct().size
        return ((distinctTypes.toDouble() / OsymQuestionType.entries.size.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }

    private fun buildQuestionExposureScores(
        previous: List<TestResultRecord>,
        existingExams: List<MockExamItem>
    ): Map<String, Int> {
        if (previous.isEmpty() && existingExams.isEmpty()) return emptyMap()
        val exposure = HashMap<String, Int>(256)
        val solvedTestIds = HashSet<String>(previous.size)

        val sortedSolved = previous.sortedByDescending { it.createdAt?.seconds ?: 0L }
        sortedSolved.forEachIndexed { idx, record ->
            if (record.testId.isNotBlank()) solvedTestIds.add(record.testId)
            val snapshot = TestSnapshotCodec.decode(record.testSnapshotJson) ?: return@forEachIndexed
            val answers = parseSimpleAnswersMap(record.answersMapJson)
            val recencyBonus = (16 - idx).coerceAtLeast(0)
            for (q in snapshot.questions) {
                val qid = q.sourceQuestionId.ifBlank { q.id }
                if (qid.isBlank()) continue
                val chosen = answers[q.number]?.trim()?.uppercase(Locale.ROOT).orEmpty()
                val wasMissedOrBlank = q.answerKeyVerified && (chosen.isBlank() || chosen != q.correctOption.uppercase(Locale.ROOT))
                val delta = 100 + recencyBonus - (if (wasMissedOrBlank) 35 else 0)
                exposure[qid] = (exposure[qid] ?: 0) + delta.coerceAtLeast(50)
            }
        }

        // Henüz çözülmemiş ancak oluşturulup kaydedilmiş denemelerdeki sorulara +15..23 puan ver:
        // böylece arka arkaya 2., 3., 4. deneme oluşturulduğunda her deneme %100 taze sorulardan kurulur!
        val unsolvedExams = existingExams
            .filterNot { it.id in solvedTestIds }
            .sortedByDescending { it.id }
        unsolvedExams.forEachIndexed { idx, exam ->
            val recencyBonus = (8 - idx).coerceAtLeast(0)
            for (q in exam.linkedTest.questions) {
                val qid = q.sourceQuestionId.ifBlank { q.id }
                if (qid.isBlank()) continue
                exposure[qid] = (exposure[qid] ?: 0) + 15 + recencyBonus
            }
        }
        return exposure
    }

    private fun parseSimpleAnswersMap(json: String): Map<Int, String> {
        if (json.isBlank() || !json.contains(':')) return emptyMap()
        return runCatching {
            val obj = org.json.JSONObject(json)
            val map = HashMap<Int, String>(obj.length())
            val iter = obj.keys()
            while (iter.hasNext()) {
                val key = iter.next()
                val qNum = key.toIntOrNull() ?: continue
                map[qNum] = obj.optString(key, "")
            }
            map
        }.getOrDefault(emptyMap())
    }

    fun build(
        request: DenemeRequest,
        pool: List<CustomQuestionRecord>,
        previous: List<TestResultRecord> = emptyList(),
        sequence: Long = System.currentTimeMillis(),
        existingExams: List<MockExamItem> = emptyList()
    ): DenemePlan {
        val slices = sections(request)
            ?: return DenemePlan.Missing(listOf(DenemeShortage("Geçersiz deneme seçimi", 1, 0)))

        // 1000 kitap (250.000+ soru) ölçeğinde daraltarak ön-filtreleme:
        // Yalnızca bu denemenin ihtiyaç duyduğu ders ve alt konular için eligible() ve kazanım çözümlemesi çalıştır!
        val sectionQuotas = slices.map { section ->
            val quotas = if (request.kind == DenemeKind.BRANS) {
                DenemeTopicProfiles.forSection(section)
            } else {
                listOf(DenemeTopicQuota(section.label, section.count, section.allowedTopicIds))
            }
            section to quotas
        }
        val requiredSubjectIds = HashSet<String>(slices.size * 2)
        val requiredTopicIds = HashSet<String>(64)
        for ((section, quotas) in sectionQuotas) {
            requiredSubjectIds.add(section.subjectId)
            for (q in quotas) requiredTopicIds.addAll(q.topicIds)
        }

        val seenIds = HashSet<String>(2048)
        val candidatesBySubjectTopic = HashMap<String, MutableList<CustomQuestionRecord>>(128)
        val metaCache = HashMap<String, OsymResolvedQuestionMeta>(1024)
        val targetExamName = request.examType.name

        for (record in pool) {
            if (record.subjectId !in requiredSubjectIds) continue
            if (requiredTopicIds.isNotEmpty() && record.topicId !in requiredTopicIds) continue
            if (!record.examType.equals(targetExamName, ignoreCase = true)) continue
            if (record.id.isBlank() || !seenIds.add(record.id)) continue
            if (!eligible(record, request.difficulty)) continue

            val bucketKey = "${record.subjectId}|${record.topicId}"
            candidatesBySubjectTopic.getOrPut(bucketKey) { ArrayList() }.add(record)
            metaCache[record.id] = cachedMetaFor(record)
        }

        val exposureById = buildQuestionExposureScores(previous, existingExams)
        val masteryMemory = Osym2018To2026Intelligence.analyzeStudentWeaknesses(previous)

        val selected = mutableListOf<CustomQuestionRecord>()
        val selectedIds = HashSet<String>(128)
        val shortages = mutableListOf<DenemeShortage>()
        for ((section, quotas) in sectionQuotas) {
            for (quota in quotas) {
                val candidates = ArrayList<CustomQuestionRecord>()
                for (tId in quota.topicIds) {
                    if (section.allowedTopicIds.isNotEmpty() && tId !in section.allowedTopicIds) continue
                    val bucket = candidatesBySubjectTopic["${section.subjectId}|$tId"] ?: continue
                    for (rec in bucket) {
                        if (rec.id !in selectedIds) candidates.add(rec)
                    }
                }

                if (candidates.size < quota.count) {
                    shortages.add(DenemeShortage(section.label + " • " + quota.label, quota.count, candidates.size))
                } else {
                    val picked = pickSlice(
                        candidates = candidates,
                        count = quota.count,
                        exposureById = exposureById,
                        metaCache = metaCache,
                        masteryMemory = masteryMemory,
                        alreadySelected = selected,
                        orderedTopicIds = quota.topicIds.toList(),
                        sortPedagogically = true
                    )
                    for (p in picked) {
                        selected.add(p)
                        selectedIds.add(p.id)
                    }
                }
            }
        }
        if (shortages.isNotEmpty()) return DenemePlan.Missing(shortages)

        val expected = slices.sumOf { it.count }
        if (selected.size != expected || selected.map { it.id }.distinct().size != expected) {
            return DenemePlan.Missing(listOf(DenemeShortage("Benzersiz soru", expected, selected.size)))
        }

        val blueprint = DenemeBlueprints.find(request.blueprintId)?.takeIf { request.kind == DenemeKind.BRANS }
        val title = if (request.kind == DenemeKind.BRANS) {
            blueprint?.label ?: "Branş Denemesi"
        } else {
            val subject = SoruLabCatalog.allSubjects.first { it.id == request.subjectId }
            val topic = subject.topics.first { it.id == request.topicId }
            subject.examType.name + " " + subject.name + " • " + topic.name
        }

        val minutes = if (request.timerMode == DenemeTimerMode.STOPWATCH) {
            0
        } else {
            (request.durationMinutes.takeIf { it > 0 }
                ?: blueprint?.minutes ?: (expected * 1.8f).toInt()).coerceIn(5, 240)
        }

        val id = "deneme_v147_" + request.examType.name + "_" + request.kind.name +
            "_" + minutes + "_" + sequence

        val questions = selected.mapIndexed { index, record ->
            val subject = SoruLabCatalog.allSubjects.first { it.id == record.subjectId }
            val topic = subject.topics.first { it.id == record.topicId }
            val meta = metaCache[record.id] ?: Osym2018To2026Intelligence.resolveOutcomeForRecord(record)
            val resolvedOutcomeName = record.resolvedLearningOutcome.name.ifBlank {
                meta.outcomeTitle.ifBlank {
                    Osym2018To2026Intelligence.fallbackKazanimTitle(
                        subjectId = subject.id,
                        topicId = topic.id,
                        topicName = topic.name,
                        questionType = meta.questionType,
                        deterministicSeed = index
                    )
                }
            }
            val resolvedOutcomeId = record.resolvedLearningOutcome.id.ifBlank { meta.outcomeId }
            val resolvedOutcomeCode = record.learningOutcome.ifBlank { meta.outcomeCode }

            YksQuestion(
                id = id + "_q" + (index + 1),
                number = index + 1,
                badgeTitle = title.uppercase(Locale.forLanguageTag("tr-TR")),
                topicHeader = topic.name,
                publisherStamp = meta.cleanPublisherName,
                publisherName = meta.cleanPublisherName,
                publisherLogoUri = record.publisherLogoUri,
                croppedImagePath = record.croppedImagePath,
                answerKeyVerified = true,
                stemTop = if (record.croppedImagePath.isNotBlank() || record.imageBase64.isNotBlank()) "" else record.questionText,
                stemBottom = "",
                options = mapOf("A" to "A", "B" to "B", "C" to "C", "D" to "D", "E" to "E"),
                correctOption = record.correctOption.uppercase(Locale.ROOT),
                difficulty = record.difficultyLevel5,
                visualType = QuestionVisualType.NONE,
                stepByStepSolution = record.cleanSolutionSummary,
                bookId = record.bookId,
                sourceQuestionId = record.id,
                subject = if (request.kind == DenemeKind.KONU) {
                    subject.name
                } else {
                    slices.firstOrNull {
                        it.subjectId == record.subjectId &&
                            (it.allowedTopicIds.isEmpty() || record.topicId in it.allowedTopicIds)
                    }?.label ?: subject.name
                },
                sourceBookTitle = meta.cleanBookTitle,
                sourceTestId = record.sourceTestId,
                sourcePageIndex = record.pageIndex,
                sourcePrintedQuestionNumber = record.questionNumberInBook,
                learningOutcome = resolvedOutcomeCode,
                questionType = record.questionType.ifBlank { meta.questionType.name },
                learningOutcomeId = resolvedOutcomeId,
                learningOutcomeName = resolvedOutcomeName,
                learningOutcomeSource = record.resolvedLearningOutcome.source.ifBlank { meta.outcomeSource },
                learningOutcomeConfidence = record.resolvedLearningOutcome.confidence.takeIf { it > 0.0 } ?: meta.outcomeConfidence,
                analyticsText = record.questionText,
                isOsymType = record.isOsymType,
                imageBase64 = record.imageBase64,
                pdfFilePath = record.pdfFilePath,
                pageIndex = record.pageIndex,
                questionNumberInTest = record.questionNumberInTest,
                normLeft = record.normLeft,
                normTop = record.normTop,
                normRight = record.normRight,
                normBottom = record.normBottom
            )
        }

        val avgWeight = questions.map { it.difficulty.weight }.average().toFloat()
        val difficulty = when {
            avgWeight < .3f -> DifficultyLevel.KOLAY
            avgWeight > .6f -> DifficultyLevel.ZOR
            else -> DifficultyLevel.ORTA
        }

        val builtTest = YksTestItem(
            id = id,
            topicId = selected.first().topicId,
            title = title + " • " + expected + " Soru",
            fullTitle = title + " • " + expected + " Soru",
            difficulty = difficulty,
            difficultyPosition = avgWeight.coerceIn(.1f, .95f),
            defaultCompleted = false,
            starRating = 0,
            level = TopicTestLevel.OSYM,
            questions = questions,
            denemeProfileVersion = DenemeTopicProfiles.VERSION,
            denemeDifficultyLabel = request.difficulty.label,
            denemeReusedQuestionCount = selected.count { (exposureById[it.id] ?: 0) >= 50 },
            denemeDistributionSummary = slices.joinToString("\n") { section ->
                section.label + ": " + if (request.kind == DenemeKind.BRANS) {
                    DenemeTopicProfiles.forSection(section).joinToString(" • ") { it.label + " " + it.count }
                } else {
                    val distinctKaz = questions.map { it.learningOutcomeId }.distinct().size
                    val distinctTypes = questions.map { it.questionType }.distinct().size
                    "${section.count} soru • $distinctKaz kazanım • $distinctTypes ÖSYM soru tipi"
                }
            }
        )

        val auditReport = OsymExamAuditEngine.auditTest(builtTest)
        return DenemePlan.Ready(
            test = builtTest,
            minutes = minutes,
            sectionSummary = slices.map { it.label to it.count },
            auditReport = auditReport
        )
    }
}
