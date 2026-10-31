package com.example.data

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp

@Immutable
data class TestResultRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val examType: String = "TYT",
    val subjectId: String = "",
    val subjectName: String = "",
    val topicId: String = "",
    val topicName: String = "",
    val testId: String = "",
    val testTitle: String = "",
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val emptyCount: Int = 0,
    val ungradedCount: Int = 0,
    val durationSeconds: Int = 0,
    val answersMapJson: String = "{}",
    val testSnapshotJson: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val isSolvedAttempt: Boolean
        get() = !answersMapJson.contains("\"_status\":\"GENERATED\"")

    @get:Exclude
    val totalQuestions: Int
        get() = (correctCount + wrongCount + emptyCount + ungradedCount).coerceAtLeast(0)

    @get:Exclude
    val netScore: Double
        get() = correctCount - (wrongCount * 0.25)

    @get:Exclude
    val accuracyPercent: Int
        get() {
            val gradedQuestionCount = correctCount + wrongCount + emptyCount
            return if (gradedQuestionCount == 0) 0 else
                ((correctCount.toFloat() / gradedQuestionCount.toFloat()) * 100f).toInt().coerceIn(0, 100)
        }
}

@Immutable
data class YksPublisherInfo(
    val id: String,
    val name: String,
    val shortCode: String,
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val customLogoPath: String = ""
)

object SoruLabPublisherPresets {
    val defaultPublishers: List<YksPublisherInfo> = emptyList()

    fun mergedPublishers(customPublishers: List<YksPublisherInfo>): List<YksPublisherInfo> {
        return customPublishers
            .filter { it.name.isNotBlank() }
            .distinctBy { it.name.trim().lowercase() }
    }

    fun resolvePublisher(
        publisherTag: String,
        customPublishers: List<YksPublisherInfo> = emptyList()
    ): YksPublisherInfo {
        val trimmed = publisherTag.trim()
        val all = mergedPublishers(customPublishers)
        all.firstOrNull {
            it.name.equals(trimmed, ignoreCase = true) ||
                it.id.equals(trimmed, ignoreCase = true) ||
                it.shortCode.equals(trimmed, ignoreCase = true) ||
                (it.name.isNotBlank() && trimmed.contains(it.name, ignoreCase = true)) ||
                (it.name.isNotBlank() && it.name.contains(trimmed, ignoreCase = true) && trimmed.length >= 2)
        }?.let { return it }

        return YksPublisherInfo(
            id = "custom_${trimmed.lowercase().replace(" ", "_")}",
            name = trimmed.ifBlank { "Özel Yayın" },
            shortCode = trimmed.take(3).uppercase().ifBlank { "YKS" },
            primaryColorHex = 0xFF1E3A8A,
            secondaryColorHex = 0xFF0F172A,
            customLogoPath = ""
        )
    }
}

@Immutable
data class AiStructuredSolution(
    val kritikBilgiTeorem: String = "",
    val adimAdimCozum: String = "",
    val osymTuzakNotu: String = ""
) {
    val adimAdimList: List<String>
        get() = adimAdimCozum
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { if (adimAdimCozum.isNotBlank()) listOf(adimAdimCozum) else emptyList() }

    fun toFormattedText(): String {
        return buildString {
            if (kritikBilgiTeorem.isNotBlank()) {
                appendLine("📌 Kritik Bilgi & Teorem: $kritikBilgiTeorem")
            }
            if (adimAdimCozum.isNotBlank()) {
                appendLine("✏️ Adım Adım Çözüm: $adimAdimCozum")
            }
            if (osymTuzakNotu.isNotBlank()) {
                append("⚠️ ÖSYM Tuzak Notu: $osymTuzakNotu")
            }
        }.trim()
    }

    companion object {
        fun fromFormattedText(raw: String): AiStructuredSolution {
            val teorem = raw.substringAfter("Kritik Bilgi & Teorem:", "")
                .substringBefore("✏️")
                .substringBefore("⚠️")
                .trim()
            val adim = raw.substringAfter("Adım Adım Çözüm:", "")
                .substringBefore("⚠️")
                .trim()
            val tuzak = raw.substringAfter("ÖSYM Tuzak Notu:", "")
                .trim()
            return if (teorem.isNotBlank() || adim.isNotBlank() || tuzak.isNotBlank()) {
                AiStructuredSolution(
                    kritikBilgiTeorem = teorem,
                    adimAdimCozum = adim.ifBlank { raw },
                    osymTuzakNotu = tuzak
                )
            } else {
                AiStructuredSolution(
                    kritikBilgiTeorem = "Temel YKS kazanım ve muhakeme ilkesi.",
                    adimAdimCozum = raw,
                    osymTuzakNotu = "İşlem sırasına ve öncül şartlarına dikkat ediniz."
                )
            }
        }
    }
}

@Immutable
data class CustomQuestionRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val examType: String = "TYT",
    val subjectId: String = "",
    val subjectName: String = "",
    val topicId: String = "",
    val topicName: String = "",
    val difficulty: String = "Orta",
    val correctOption: String = "C",
    val questionText: String = "",
    val publisherTag: String = "3D Yayınları",
    val aiSolutionSummary: String = "",
    val cropAspectRatio: Double = 1.25,
    val targetLevel: String = "",
    val croppedImageFilePath: String = "",
    val publisherLogoPath: String = "",
    val bookName: String = "",
    val bookCoverImagePath: String = "",
    val answerKeyVerifiedFlag: Boolean? = null,
    val questionNumberInSource: Int = 0,
    val fiveTierDifficulty: String = "",
    val importSource: String = "",
    val sourcePageIndex: Int = 0,
    val sourceQuestionOrder: Int = 0,
    val contentFingerprint: String = "",
    val importQualityScore: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @delegate:Transient
    private val cachedMetaMap: Map<String, String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (!aiSolutionSummary.startsWith("[META:")) emptyMap()
        else {
            val header = aiSolutionSummary.substringAfter("[META:").substringBefore("]")
            header.split("|").mapNotNull { entry ->
                val idx = entry.indexOf('=')
                if (idx > 0) entry.substring(0, idx) to entry.substring(idx + 1) else null
            }.toMap()
        }
    }

    @get:Exclude
    private val parsedMetaMap: Map<String, String>
        get() = cachedMetaMap

    @delegate:Transient
    @get:Exclude
    val targetLevelName: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        targetLevel.ifBlank { parsedMetaMap["level"].orEmpty() }
    }

    @delegate:Transient
    @get:Exclude
    val croppedImagePath: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        croppedImageFilePath.ifBlank { parsedMetaMap["img"].orEmpty() }
    }

    @delegate:Transient
    @get:Exclude
    val publisherLogoUri: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        publisherLogoPath.ifBlank { parsedMetaMap["logo"].orEmpty() }
    }

    @delegate:Transient
    @get:Exclude
    val bookOrTestName: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        bookName.ifBlank { parsedMetaMap["book"].orEmpty() }
    }

    @delegate:Transient
    @get:Exclude
    val bookCoverPath: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        bookCoverImagePath.ifBlank { parsedMetaMap["cover"].orEmpty() }
    }

    @delegate:Transient
    @get:Exclude
    val isInKumbara: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
        parsedMetaMap["kumbara"] == "1"
    }

    @delegate:Transient
    @get:Exclude
    val answerKeyVerified: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
        answerKeyVerifiedFlag ?: (parsedMetaMap["ak"] != "0")
    }

    @delegate:Transient
    @get:Exclude
    val questionNumberInBook: Int by lazy(LazyThreadSafetyMode.PUBLICATION) {
        questionNumberInSource.takeIf { it > 0 } ?: (parsedMetaMap["qnum"]?.toIntOrNull() ?: 0)
    }

    @delegate:Transient
    @get:Exclude
    val effectiveDifficultyLabel: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        fiveTierDifficulty.takeIf { it.isNotBlank() }
            ?: parsedMetaMap["diff5"]?.takeIf { it.isNotBlank() }
            ?: difficulty
    }

    @delegate:Transient
    @get:Exclude
    val stableContentFingerprint: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        contentFingerprint.ifBlank {
            buildContentFingerprint(
                examType = examType,
                subjectName = subjectName,
                topicName = topicName,
                questionText = questionText,
                bookOrTestName = bookOrTestName,
                publisherTag = publisherTag,
                questionNumberInBook = questionNumberInBook
            )
        }
    }

    @delegate:Transient
    @get:Exclude
    val difficultyLevel5: DifficultyLevel by lazy(LazyThreadSafetyMode.PUBLICATION) {
        DifficultyLevel.fromLabel(effectiveDifficultyLabel)
    }

    @delegate:Transient
    @get:Exclude
    val cleanSolutionSummary: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (aiSolutionSummary.startsWith("[META:")) {
            aiSolutionSummary.substringAfter("]").trimStart()
        } else {
            aiSolutionSummary
        }
    }

    @delegate:Transient
    @get:Exclude
    val structuredAiCozum: AiStructuredSolution by lazy(LazyThreadSafetyMode.PUBLICATION) {
        AiStructuredSolution.fromFormattedText(cleanSolutionSummary)
    }

    companion object {
        private fun sanitizeMetaValue(raw: String): String = raw.replace("|", "").replace("]", "")

        fun normalizeFingerprintText(raw: String): String {
            return raw
                .lowercase()
                .replace("ç", "c")
                .replace("ğ", "g")
                .replace("ı", "i")
                .replace("ö", "o")
                .replace("ş", "s")
                .replace("ü", "u")
                .replace(Regex("""\s+"""), " ")
                .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }

        fun buildContentFingerprint(
            examType: String,
            subjectName: String,
            topicName: String,
            questionText: String,
            bookOrTestName: String = "",
            publisherTag: String = "",
            questionNumberInBook: Int = 0
        ): String {
            val normalizedQuestion = normalizeFingerprintText(questionText).take(320)
            val normalizedBook = normalizeFingerprintText(bookOrTestName).take(120)
            val normalizedPublisher = normalizeFingerprintText(publisherTag).take(80)
            val normalizedTopic = normalizeFingerprintText(topicName).take(80)
            val normalizedSubject = normalizeFingerprintText(subjectName).take(40)
            val normalizedExam = normalizeFingerprintText(examType).take(16)
            val numberPart = questionNumberInBook.takeIf { it > 0 }?.toString().orEmpty()
            return listOf(
                normalizedExam,
                normalizedSubject,
                normalizedTopic,
                normalizedBook,
                normalizedPublisher,
                numberPart,
                normalizedQuestion
            ).joinToString("|")
        }

        fun packSolutionWithMeta(
            solutionText: String,
            targetLevel: String = "",
            croppedImagePath: String = "",
            publisherLogoUri: String = "",
            answerKeyVerified: Boolean = true,
            questionNumberInBook: Int = 0,
            bookOrTestName: String = "",
            bookCoverPath: String = "",
            isInKumbara: Boolean = false,
            fiveTierDifficulty: String = ""
        ): String {
            val cleanSol = if (solutionText.startsWith("[META:")) {
                solutionText.substringAfter("]").trimStart()
            } else {
                solutionText.trim()
            }
            val metaParts = buildList {
                if (targetLevel.isNotBlank()) add("level=${sanitizeMetaValue(targetLevel)}")
                if (fiveTierDifficulty.isNotBlank()) add("diff5=${sanitizeMetaValue(fiveTierDifficulty)}")
                if (croppedImagePath.isNotBlank()) add("img=${sanitizeMetaValue(croppedImagePath)}")
                if (publisherLogoUri.isNotBlank()) add("logo=${sanitizeMetaValue(publisherLogoUri)}")
                if (bookOrTestName.isNotBlank()) add("book=${sanitizeMetaValue(bookOrTestName)}")
                if (bookCoverPath.isNotBlank()) add("cover=${sanitizeMetaValue(bookCoverPath)}")
                if (isInKumbara) add("kumbara=1")
                add("ak=${if (answerKeyVerified) "1" else "0"}")
                if (questionNumberInBook > 0) add("qnum=$questionNumberInBook")
            }
            val prefix = "[META:${metaParts.joinToString("|")}] "
            val maxSolLen = (3800 - prefix.length).coerceAtLeast(200)
            return prefix + cleanSol.take(maxSolLen)
        }
    }
}

@Immutable
data class LibraryBookCardModel(
    val key: String,
    val bookTitle: String,
    val publisherName: String,
    val publisherInfo: YksPublisherInfo,
    val coverImagePath: String,
    val activeQuestionCount: Int,
    val subjectsSummary: String,
    val questions: List<CustomQuestionRecord>
)

enum class ExamType(val label: String, val fullTitle: String) {
    TYT("TYT", "Temel Yeterlilik Testi"),
    AYT("AYT", "Alan Yeterlilik Testi")
}

/**
 * Akıllı Kaynak Filtresi (İnteraktif Test Oluşturma Paneli):
 * - Tüm Sorular
 * - Çözülmemiş Sorular
 * - Yanlış Yaptıklarım
 * - Boş Bıraktıklarım
 * - Soru Kumbaram
 */
enum class QuestionSourceFilter(val label: String) {
    TUM_SORULAR("Tüm Sorular"),
    COZULMEMIS_SORULAR("Çözülmemiş Sorular"),
    YANLIS_YAPTIKLARIM("Yanlış Yaptıklarım"),
    BOS_BIRAKTIKLARIM("Boş Bıraktıklarım"),
    SORU_KUMBARAM("Soru Kumbaram")
}

@Immutable
data class TopicProgressSummary(
    val solvedQuestions: Int = 0,
    val totalQuestions: Int = 0,
    val completedTests: Int = 0,
    val totalTests: Int = 0
) {
    val progressPercent: Int
        get() = if (totalQuestions <= 0) 0 else ((solvedQuestions.toFloat() / totalQuestions.toFloat()) * 100f).toInt().coerceIn(0, 100)
}

/**
 * 5-Tier Difficulty System:
 * 1) Kolay (Temel işlem ve tanım soruları)
 * 2) Orta (Standart kazanım soruları)
 * 3) ÖSYM (Hikayeli, yorum ve grafik ağırlıklı sınav tarzı sorular)
 * 4) Zor (İleri seviye, birden fazla kazanımı birleştiren sorular)
 * 5) Çok Zor ("Tümevarım / Barışça / Orijinal / Derece" tarzı en üst seviye sorular)
 */
enum class DifficultyLevel(val label: String, val weight: Float, val colorHex: Long, val starsCount: Int) {
    KOLAY("Kolay", 0.12f, 0xFF16A34A, 1),
    ORTA("Orta", 0.34f, 0xFF2563EB, 2),
    OSYM("ÖSYM", 0.54f, 0xFFD97706, 3),
    ZOR("Zor", 0.76f, 0xFFEA580C, 4),
    COK_ZOR("Çok Zor", 0.92f, 0xFFDC2626, 5);

    fun toFirestoreSafeDifficulty(): String = when (this) {
        KOLAY -> "Kolay"
        ORTA, OSYM -> "Orta"
        ZOR, COK_ZOR -> "Zor"
    }

    companion object {
        fun fromLabel(raw: String): DifficultyLevel {
            val normalized = raw.trim().lowercase()
            return when {
                normalized.contains("çok zor") ||
                    normalized.contains("cok zor") ||
                    normalized.contains("tümevarım") ||
                    normalized.contains("tumevarim") ||
                    normalized.contains("barışça") ||
                    normalized.contains("barisca") ||
                    normalized.contains("derece") ||
                    normalized.contains("orijinal") -> COK_ZOR
                normalized.contains("ösym") ||
                    normalized.contains("osym") ||
                    normalized.contains("birebir") ||
                    normalized.contains("sınav tarzı") -> OSYM
                normalized.contains("zor") -> ZOR
                normalized.contains("kolay") ||
                    normalized.contains("başlangıç") ||
                    normalized.contains("baslangic") ||
                    normalized.contains("temel") -> KOLAY
                else -> ORTA
            }
        }
    }
}

enum class TopicTestLevel(
    val wheelLabel: String,
    val displayTitle: String,
    val shortTag: String,
    val wedgeColorHex: Long,
    val textColorHex: Long,
    val difficultyPosition: Float,
    val defaultDifficulty: DifficultyLevel,
    val poolDistributionLabel: String
) {
    BASLANGIC(
        wheelLabel = "BAŞLANGIÇ",
        displayTitle = "Başlangıç Tarzı",
        shortTag = "Başlangıç",
        wedgeColorHex = 0xFFD4C585,
        textColorHex = 0xFF9C7214,
        difficultyPosition = 0.15f,
        defaultDifficulty = DifficultyLevel.KOLAY,
        poolDistributionLabel = "Temel işlem ve tanım odaklı (Kolay & Orta seviye sorular)"
    ),
    IKIZ(
        wheelLabel = "İKİZ",
        displayTitle = "İkiz Tarzı",
        shortTag = "İkiz",
        wedgeColorHex = 0xFFD3B484,
        textColorHex = 0xFFB86614,
        difficultyPosition = 0.48f,
        defaultDifficulty = DifficultyLevel.OSYM,
        poolDistributionLabel = "Bu konuda yanlış yaptığın veya boş bıraktığın soruların birebir ikizleri"
    ),
    PEKISTIRME(
        wheelLabel = "PEKİŞTİRME",
        displayTitle = "Pekiştirme Tarzı",
        shortTag = "Pekiştirme",
        wedgeColorHex = 0xFFD99588,
        textColorHex = 0xFFC72C2C,
        difficultyPosition = 0.36f,
        defaultDifficulty = DifficultyLevel.ORTA,
        poolDistributionLabel = "Standart müfredat kazanımlarını pekiştiren Orta seviye sorular"
    ),
    GELISIYORUM(
        wheelLabel = "GELİŞİYORUM",
        displayTitle = "Gelişiyorum Tarzı",
        shortTag = "Gelişiyorum",
        wedgeColorHex = 0xFF1574E5,
        textColorHex = 0xFFEFF6FF,
        difficultyPosition = 0.42f,
        defaultDifficulty = DifficultyLevel.ORTA,
        poolDistributionLabel = "Orta ve ÖSYM geçiş düzeyinde yorum becerisi geliştiren sorular"
    ),
    OSYM(
        wheelLabel = "ÖSYM",
        displayTitle = "ÖSYM Tarzı",
        shortTag = "ÖSYM",
        wedgeColorHex = 0xFF6BA5CE,
        textColorHex = 0xFF0F3D7A,
        difficultyPosition = 0.56f,
        defaultDifficulty = DifficultyLevel.OSYM,
        poolDistributionLabel = "Hikayeli, yorum ve grafik ağırlıklı gerçek ÖSYM tarzı sorular"
    ),
    DERECE(
        wheelLabel = "DERECE",
        displayTitle = "Derece Tarzı",
        shortTag = "Derece",
        wedgeColorHex = 0xFF7B88CC,
        textColorHex = 0xFF1D2268,
        difficultyPosition = 0.88f,
        defaultDifficulty = DifficultyLevel.COK_ZOR,
        poolDistributionLabel = "Tümevarım, Zor ve Çok Zor etiketli ileri seviye derece soruları"
    )
}

fun TopicTestLevel.recommendedDifficultyLevels(): Set<DifficultyLevel> = when (this) {
    TopicTestLevel.BASLANGIC -> setOf(DifficultyLevel.KOLAY, DifficultyLevel.ORTA)
    TopicTestLevel.IKIZ -> DifficultyLevel.entries.toSet()
    TopicTestLevel.PEKISTIRME -> setOf(DifficultyLevel.KOLAY, DifficultyLevel.ORTA, DifficultyLevel.OSYM)
    TopicTestLevel.GELISIYORUM -> setOf(DifficultyLevel.ORTA, DifficultyLevel.OSYM, DifficultyLevel.ZOR)
    TopicTestLevel.OSYM -> setOf(DifficultyLevel.ORTA, DifficultyLevel.OSYM, DifficultyLevel.ZOR)
    TopicTestLevel.DERECE -> setOf(DifficultyLevel.OSYM, DifficultyLevel.ZOR, DifficultyLevel.COK_ZOR)
}

const val AUTOMATIC_TEST_QUESTION_COUNT = 12

fun automaticBookTestChunks(
    questions: List<CustomQuestionRecord>,
    level: TopicTestLevel
): List<List<CustomQuestionRecord>> =
    questions
        .filter { it.difficultyLevel5 in level.recommendedDifficultyLevels() }
        .chunked(AUTOMATIC_TEST_QUESTION_COUNT)

enum class QuestionVisualType {
    NONE,
    MARKET_RECEIPT,
    LEGO_BLOCKS,
    FUNCTION_GRAPH,
    GEOMETRY_TRIANGLE,
    PHYSICS_VECTORS
}

@Immutable
data class YksQuestion(
    val id: String,
    val number: Int,
    val badgeTitle: String = "GÜNLÜK ÖDEV 5",
    val topicHeader: String = "TEMEL KAVRAMLAR",
    val publisherStamp: String = "ÜÇDÖRTBEŞ YAYINLARI",
    val publisherName: String = "ÜçDörtBeş Yayınları",
    val publisherLogoUri: String = "",
    val croppedImagePath: String = "",
    val answerKeyVerified: Boolean = true,
    val stemTop: String,
    val bulletsOrSteps: List<String> = emptyList(),
    val stemBottom: String,
    val options: Map<String, String>,
    val correctOption: String,
    val difficulty: DifficultyLevel = DifficultyLevel.ORTA,
    val visualType: QuestionVisualType = QuestionVisualType.NONE,
    val stepByStepSolution: String
)

@Immutable
data class YksTestItem(
    val id: String,
    val topicId: String,
    val title: String,
    val fullTitle: String,
    val difficulty: DifficultyLevel,
    val difficultyPosition: Float,
    val defaultCompleted: Boolean = false,
    val starRating: Int = 0,
    val level: TopicTestLevel = TopicTestLevel.OSYM,
    val questions: List<YksQuestion>
)

@Immutable
data class YksTopic(
    val id: String,
    val subjectId: String,
    val name: String,
    val questionCount: Int,
    val completedTests: Int,
    val totalTests: Int,
    val badgeColorHex: Long,
    val iconType: String,
    val tests: List<YksTestItem>
)

@Immutable
data class YksSubject(
    val id: String,
    val examType: ExamType,
    val name: String,
    val accentColorHex: Long,
    val topics: List<YksTopic>
)

@Immutable
data class MockExamItem(
    val id: String,
    val title: String,
    val publisher: String,
    val examType: ExamType,
    val questionCount: Int,
    val durationMinutes: Int,
    val difficulty: DifficultyLevel,
    val tag: String,
    val isSolvedDefault: Boolean = false,
    val defaultNet: Double = 0.0,
    val linkedTest: YksTestItem
)

object SoruLabCatalog {

    val temelKavramlarOdev5Questions: List<YksQuestion> = emptyList()

    val topicProgressState = androidx.compose.runtime.mutableStateMapOf<String, TopicProgressSummary>()

    fun buildDefaultPracticeTest(
        topicId: String,
        topicName: String,
        suffix: String,
        shortTitle: String,
        pos: Float,
        completed: Boolean = false
    ): YksTestItem {
        val diff = when {
            pos < 0.24f -> DifficultyLevel.KOLAY
            pos < 0.44f -> DifficultyLevel.ORTA
            pos < 0.65f -> DifficultyLevel.OSYM
            pos < 0.84f -> DifficultyLevel.ZOR
            else -> DifficultyLevel.COK_ZOR
        }
        return YksTestItem(
            id = "${topicId}_$suffix",
            topicId = topicId,
            title = shortTitle,
            fullTitle = "$topicName - $shortTitle",
            difficulty = diff,
            difficultyPosition = pos,
            defaultCompleted = completed,
            starRating = 0,
            level = TopicTestLevel.OSYM,
            questions = emptyList()
        )
    }

    /**
     * Classifies 5-tier difficulty (`Kolay`, `Orta`, `ÖSYM`, `Zor`, `Çok Zor`) from question text and structural signals:
     * - İşlem adımı sayısı ve denklem/satır yoğunluğu
     * - Öncül sayısı (I, II, III, IV)
     * - Grafik/şekil/tablo varlığı
     * - Birleşik kazanım yoğunluğu
     */
    fun classifyDifficulty5FromText(
        rawDifficultyHint: String,
        headerOrQuestionText: String,
        questionIndexInPage: Int = 0
    ): DifficultyLevel {
        val combined = "$rawDifficultyHint $headerOrQuestionText".lowercase()
        val textLength = headerOrQuestionText.length
        val nonEmptyLines = headerOrQuestionText.lines().count { it.isNotBlank() }
        val premiseCount = Regex("""(?m)(?:^|\s)(?:I{1,3}|IV|V)[).,:]""")
            .findAll(headerOrQuestionText)
            .count()
        val hasGraphOrTable = Regex("""grafik|şekil|sekil|tablo|çizim|cizim|koordinat|düzlem|diyagram""").containsMatchIn(combined)
        val conceptGroups = listOf(
            listOf("limit", "lim", "süreklilik", "sağdan", "soldan"),
            listOf("türev", "f'(x)", "g'(x)", "dy/dx", "ekstremum", "teğet", "artan", "azalan"),
            listOf("fonksiyon", "grafik", "parabol", "tepe noktası", "simetri ekseni"),
            listOf("denklem", "eşitsizlik", "kök", "polinom", "başkatsayı", "derece"),
            listOf("tablo", "olasılık", "kombinasyon", "permütasyon", "binom"),
            listOf("integral", "alan", "riemann", "diferansiyel"),
            listOf("hız", "ivme", "kuvvet", "enerji", "momentum", "tork")
        ).count { group -> group.any(combined::contains) }

        var score = when {
            textLength >= 650 -> 3
            textLength >= 380 -> 2
            textLength >= 180 -> 1
            else -> 0
        }
        score += when {
            nonEmptyLines >= 14 -> 2
            nonEmptyLines >= 8 -> 1
            else -> 0
        }
        score += when {
            premiseCount >= 3 -> 3
            premiseCount >= 1 -> 1
            else -> 0
        }
        if (hasGraphOrTable) score += 2
        score += when {
            conceptGroups >= 3 -> 3
            conceptGroups >= 2 -> 2
            conceptGroups == 1 -> 1
            else -> 0
        }

        return when {
            score <= 2 -> DifficultyLevel.KOLAY
            score in 3..4 -> DifficultyLevel.ORTA
            score in 5..6 -> DifficultyLevel.OSYM
            score in 7..8 -> DifficultyLevel.ZOR
            else -> DifficultyLevel.COK_ZOR
        }
    }

    fun classifyQuestionTopicFromText(
        examTypeHint: String,
        subjectNameHint: String,
        questionText: String,
        suggestedTopicName: String = ""
    ): Pair<YksSubject, YksTopic>? {
        val normalizedText = questionText.lowercase()
        val normalizedSubjectHint = subjectNameHint
            .replace("TYT ", "", ignoreCase = true)
            .replace("AYT ", "", ignoreCase = true)
            .trim()
        val isMathContext = normalizedSubjectHint.isBlank() ||
            normalizedSubjectHint.contains("matematik", ignoreCase = true) ||
            normalizedSubjectHint.contains("geometri", ignoreCase = true)
        val hasDerivativeSymbol = normalizedText.contains("'") ||
            normalizedText.contains("′") ||
            Regex("""\bd\s*/\s*dx\b""").containsMatchIn(normalizedText) ||
            Regex("""\b[fg]\s*['′]\s*\(\s*x\s*\)""").containsMatchIn(normalizedText)

        // KATI YKS ANAHTAR KELİME SINIFLANDIRICISI (Rastgele veya dosya adına göre atamayı KESİNLİKLE engeller)
        val forcedTopicName = when {
            // Matematik/geometri dışındaki derslerde "derece", "artan", "teğet" gibi
            // ortak kelimeler AYT Matematik konusuna zorla taşınmamalıdır.
            isMathContext && (
                // 1. Polinom göstergeleri -> Polinomlar
                Regex("""\b[qp]\s*\(\s*x\s*\)""", RegexOption.IGNORE_CASE).containsMatchIn(questionText) ||
                    listOf("polinom", "derece", "bölümünden kalan", "bolumunden kalan", "başkatsayı", "baskatsayi", "katsayılar toplamı", "sabit terim").any(normalizedText::contains)
                ) -> "Polinomlar"

            isMathContext && (
                // 2. Karmaşık sayı göstergeleri -> Karmaşık Sayılar
                Regex("""i\s*\^?\s*2\s*=\s*-1|i²\s*=\s*-1|re\s*\(\s*z\)|im\s*\(\s*z\)""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(questionText) ||
                    listOf("eşlenik", "eslenik", "karmaşık", "karmasik", "sanal birim", "z = a + ib").any(normalizedText::contains)
                ) -> "Karmaşık Sayılar"

            isMathContext && listOf("tepe noktası", "tepe noktasi", "parabol", "simetri ekseni", "f(x) = ax^2", "f(x) = ax²").any(normalizedText::contains) ->
                "Parabol"

            isMathContext && (
                // Türev sinyali limit sinyalinden önce değerlendirilir.
                hasDerivativeSymbol ||
                    listOf("türev", "turev", "teğet", "teget", "ekstremum", "yerel maksimum", "yerel minimum", "artan", "azalan").any(normalizedText::contains)
                ) -> "Türev Alma Kuralları"

            isMathContext && !hasDerivativeSymbol && (
                Regex("""\blim(?:\b|[_\s({])""").containsMatchIn(normalizedText) ||
                    listOf("limit", "süreklilik", "sureklilik", "sağdan limit", "sagdan limit", "soldan limit", "belirsizlik").any(normalizedText::contains)
                ) -> "Limit ve Süreklilik"

            else -> ""
        }

        val preferredName = forcedTopicName.ifBlank { suggestedTopicName.trim() }
        val preferredExam = if (forcedTopicName.isNotBlank() || examTypeHint.contains("AYT", ignoreCase = true)) ExamType.AYT else ExamType.TYT
        val preferredSubject = if (forcedTopicName.isNotBlank()) "Matematik" else subjectNameHint
            .replace("TYT ", "", ignoreCase = true)
            .replace("AYT ", "", ignoreCase = true)
            .trim()

        val targetSubject = allSubjects.firstOrNull {
            it.examType == preferredExam && it.name.equals(preferredSubject, ignoreCase = true)
        } ?: allSubjects.firstOrNull {
            it.examType == preferredExam && (it.name.contains("matematik", ignoreCase = true))
        } ?: allSubjects.first { it.examType == preferredExam }

        // Ders İzolasyonu: Matematik soruları asla Fizik havuzunda aranamaz;
        // Fizik soruları da Matematik havuzuna karışamaz.
        val subjectSearchPool = if (preferredSubject.isNotBlank()) {
            val matched = allSubjects.filter {
                it.examType == preferredExam && it.name.equals(preferredSubject, ignoreCase = true)
            }
            if (matched.isNotEmpty()) matched else listOf(targetSubject)
        } else if (isMathContext) {
            allSubjects.filter {
                it.examType == preferredExam && (it.name.contains("matematik", ignoreCase = true) || it.name.contains("geometri", ignoreCase = true))
            }
        } else {
            allSubjects.filter { it.examType == preferredExam }
        }

        val resultPair: Pair<YksSubject, YksTopic> = if (preferredName.isBlank()) {
            val text = questionText.lowercase()
            val bestMatch = subjectSearchPool
                .flatMap { subject -> subject.topics.map { subject to it } }
                .mapNotNull { (subject, topic) ->
                    val aliases = topicKeywordAliases[topic.id].orEmpty()
                    val aliasScore = aliases.count { alias ->
                        val normalizedAlias = alias.lowercase()
                        if (normalizedAlias.length <= 3) {
                            Regex("""\b${Regex.escape(normalizedAlias)}\b""").containsMatchIn(text)
                        } else {
                            text.contains(normalizedAlias)
                        }
                    } * 3
                    val topicWordScore = topic.name
                        .split(" ", "-", "ve")
                        .map { it.trim().lowercase() }
                        .filter { it.length >= 5 }
                        .count { word -> text.contains(word) } * 2
                    val score = aliasScore + topicWordScore
                    if (score > 0) Triple(subject, topic, score) else null
                }
                .maxByOrNull { it.third }
                ?.takeIf { it.third >= 3 }

            if (bestMatch != null) {
                bestMatch.first to bestMatch.second
            } else {
                targetSubject to getReviewTopicForSubject(targetSubject)
            }
        } else {
            val exactMatches = subjectSearchPool.flatMap { subject ->
                subject.topics.map { topic -> subject to topic }
            }.filter { (_, topic) ->
                topic.name.equals(preferredName, ignoreCase = true) ||
                    (preferredName.equals("Atışlar", ignoreCase = true) && topic.id == "atislar") ||
                    (preferredName == "Türev Alma Kuralları" &&
                        topic.name.contains("Türev", ignoreCase = true)) ||
                    (preferredName == "Limit ve Süreklilik" &&
                        topic.name.contains("Limit", ignoreCase = true)) ||
                    topicKeywordAliases[topic.id].orEmpty().any { it.equals(preferredName, ignoreCase = true) }
            }

            val matched = exactMatches.firstOrNull { (subject, _) ->
                subject.examType == preferredExam && subject.name.equals(preferredSubject, ignoreCase = true)
            } ?: exactMatches.firstOrNull { (subject, _) -> subject.examType == preferredExam }
                ?: exactMatches.firstOrNull()

            if (matched != null) {
                matched
            } else {
                // Konu adı tam eşleşmediğinde sessizce alakasız bir konuya atama; İnceleme Bekliyor'a al
                targetSubject to getReviewTopicForSubject(targetSubject)
            }
        }

        // KESİN GÜVENLİK KURALI: AYT Matematik soruları hiçbir durumda Fizik olarak etiketlenemez!
        return if (isMathContext && resultPair.first.name.equals("Fizik", ignoreCase = true)) {
            val mathSubject = allSubjects.first { it.examType == preferredExam && it.name == "Matematik" }
            mathSubject to getReviewTopicForSubject(mathSubject)
        } else {
            resultPair
        }
    }

    fun getReviewTopicForSubject(subject: YksSubject): YksTopic = YksTopic(
        id = "inceleme_bekliyor",
        subjectId = subject.id,
        name = "İnceleme Bekliyor",
        questionCount = 0,
        completedTests = 0,
        totalTests = 0,
        badgeColorHex = 0xFFF59E0B,
        iconType = "help",
        tests = emptyList()
    )

    private val topicKeywordAliases: Map<String, List<String>> = mapOf(
        // AYT Matematik
        "fonksiyonlar" to listOf("fonksiyonlarda uygulama", "fonksiyon", "f(x)", "g(x)", "fog", "gof", "ters fonksiyon", "bire bir", "örten", "grafik dönüşüm", "simetri", "öteleme"),
        "polinomlar" to listOf("polinom", "p(x)", "q(x)", "derece", "başkatsayı", "kalan", "bölümünden"),
        "ikinci_derece_denklemler" to listOf("2. dereceden denklem", "ikinci dereceden denklem", "kökler toplamı", "kökler çarpımı", "diskriminant", "delta"),
        "karmasik_sayilar" to listOf("karmaşık sayı", "karmasik sayi", "sanal", "eşlenik", "i^2"),
        "parabol" to listOf("parabol", "tepe noktası", "simetri ekseni"),
        "ikinci_derece_esitsizlikler" to listOf("eşitsizlik sistemi", "2. dereceden eşitsizlik", "işaret tablosu", "çözüm kümesi"),
        "trigonometri_ayt_mat" to listOf("trigonometri", "sin", "cos", "tan", "cot", "arcsin", "arccos", "birim çember", "periyot", "toplam fark", "yarım açı"),
        "logaritma" to listOf("logaritma", "log", "ln(", "ln ", "üstel fonksiyon"),
        "diziler" to listOf("diziler", "aritmetik dizi", "geometrik dizi", "genel terim", "(an)", "a_n"),
        "limit_ve_sureklilik" to listOf("limit", "süreklilik", "sureklilik", "sağdan limit", "soldan limit", "belirsizliği"),
        "turev_alma_kurallari" to listOf("türev alma", "turev alma", "f'(x)", "dy/dx", "zincir kuralı", "türevi"),
        "turev_uygulamalari" to listOf("türevin geometrik", "ekstremum", "yerel maksimum", "yerel minimum", "teğet", "artan", "azalan", "maksimum minimum", "türev"),
        "belirsiz_integral" to listOf("belirsiz integral", "integral", "diferansiyel", "dx"),
        "belirli_integral_alan" to listOf("belirli integral", "integral ve alan", "boyalı bölgenin alanı", "riemann"),
        // TYT Matematik
        "temel_kavramlar" to listOf("temel kavram", "rakam", "tam sayı", "doğal sayı", "ardışık", "asal sayı", "faktöriyel"),
        "sayi_basamaklari" to listOf("sayı basamak", "iki basamaklı", "üç basamaklı", "��özümleme"),
        "bolme_bolunebilme" to listOf("bölme", "bölünebilme", "kalan"),
        "ebob_ekok" to listOf("ebob", "ekok", "ortak bölen", "ortak kat"),
        "rasyonel_sayilar" to listOf("rasyonel sayı", "kesir"),
        "mutlak_deger" to listOf("mutlak değer", "mutlak deger"),
        "uslu_sayilar" to listOf("üslü sayı", "uslu sayi"),
        "koklu_sayilar" to listOf("köklü sayı", "koklu sayi", "karekök"),
        "carpanlara_ayirma" to listOf("çarpanlara ayırma", "tam kare", "iki kare farkı"),
        "oran_oranti" to listOf("oran", "orantı", "doğru orantı", "ters orantı"),
        "sayi_problemleri" to listOf("sayı problem", "sayi problem"),
        "yas_problemleri" to listOf("yaş problem", "yas problem", "yaşındadır"),
        "hareket_problemleri" to listOf("hareket problem", "hız problem", "km/sa"),
        "yuzde_kar_zarar_problemleri" to listOf("yüzde", "kâr", "zarar", "indirim", "etiket fiyatı"),
        "kumeler" to listOf("küme", "kume", "alt küme", "kesişim", "birleşim"),
        "tyt_fonksiyonlar" to listOf("fonksiyon", "tanım kümesi", "görüntü kümesi"),
        "tyt_polinomlar" to listOf("polinom"),
        "permutasyon" to listOf("permütasyon", "permutasyon", "sıralama", "kaç farklı şekilde sıralanır"),
        "kombinasyon" to listOf("kombinasyon", "seçim", "alt küme", "kaç farklı grup"),
        "binom" to listOf("binom", "açılımında"),
        "olasilik" to listOf("olasılık", "olasilik", "olasılığı kaçtır", "rastgele seçilen"),
        // AYT Fizik
        "vektorler" to listOf("vektör", "vektor", "bileşke"),
        "bagil_hareket" to listOf("bağıl hareket", "bagil hareket", "gözlemci", "yere göre", "akıntı", "kayık", "yürüyen merdiven"),
        "newton_hareket_yasalari" to listOf("newton", "sürtünme", "ivme", "net kuvvet"),
        "bir_boyutta_sabit_ivmeli" to listOf("sabit ivmeli", "hız-zaman", "konum-zaman", "duruştan harekete"),
        "atislar" to listOf("atış", "atis", "serbest düşme", "yatay atış", "eğik atış"),
        "enerji_ve_hareket" to listOf("iş, enerji", "kinetik enerji", "potansiyel enerji", "yay sabiti"),
        "itme_momentum" to listOf("itme", "momentum", "çarpışma", "patlama"),
        "tork_ve_denge" to listOf("tork", "denge", "ip gerilmesi", "destek"),
        "cembersel_hareket" to listOf("çembersel hareket", "açısal hız", "merkezcil"),
        "basit_harmonik_hareket" to listOf("harmonik hareket", "yay sarkacı", "basit sarkaç", "periyot"),
        // TYT Türkçe
        "sozcukte_anlam" to listOf("sözcükte anlam", "sozcukte anlam", "gerçek anlam", "mecaz anlam", "yan anlam", "boş bırakılan yerlere"),
        "soz_obeginde_anlam" to listOf("söz öbeği", "soz obegi", "deyim", "atasözü", "altı çizili söz"),
        "cumlede_anlam" to listOf("cümlede anlam", "cumlede anlam", "neden-sonuç", "amaç-sonuç", "koşul", "yakınma", "varsayım", "numaralanmış cümle"),
        "paragrafta_anlam" to listOf("paragrafta anlam", "bu parçada", "bu parçaya göre", "asıl anlatılmak istenen", "değinilmemiştir", "çıkarılamaz", "yazarın"),
        "paragrafta_yapi" to listOf("paragrafta yapı", "akışı bozan", "iki paragrafa", "parçanın başına", "parçanın sonuna", "sıralama"),
        "anlatim_bicimleri" to listOf("anlatım biçim", "tartışmacı", "açıklayıcı", "öyküleyici", "betimleyici", "tanık gösterme", "benzetme"),
        "ses_bilgisi" to listOf("ses bilgisi", "ünlü düşmesi", "ünsüz benzeşmesi", "ünsüz yumuşaması", "ulama", "ses olayı"),
        "yazim_kurallari" to listOf("yazım kural", "yazımı yanlıştır", "yazım yanlışı", "büyük harflerin", "birleşik sözcük"),
        "noktalama_isaretleri" to listOf("noktalama", "yay ayraç", "virgül", "noktalı virgül", "iki nokta", "kesme işareti"),
        "sozcukte_yapi" to listOf("sözcükte yapı", "yapım eki", "çekim eki", "kökü", "türemiş", "gövde"),
        "isimler" to listOf("isim", "adlar", "isim tamlaması", "belirtili", "belirtisiz"),
        "sifatlar" to listOf("sıfat", "ön ad", "niteleme sıfatı", "belirtme sıfatı"),
        "zamirler" to listOf("zamir", "adıl", "kişi zamiri", "işaret zamiri", "belgisiz zamir"),
        "zarflar" to listOf("zarf", "belirteç", "durum zarfı", "zaman zarfı", "miktar zarfı"),
        "edat_baglac_unlem" to listOf("edat", "bağlaç", "ünlem", "ilgeç"),
        "fiiller" to listOf("fiil", "eylem", "kip", "ek fiil", "birleşik çekimli"),
        "fiilimsiler" to listOf("fiilimsi", "eylemsi", "isim-fiil", "sıfat-fiil", "zarf-fiil", "ulaç", "ortaç"),
        "fiilde_cati" to listOf("fiilde çatı", "çatı", "etken", "edilgen", "geçişli", "geçişsiz"),
        "cumlenin_ogeleri" to listOf("cümlenin ögeleri", "özne", "yüklem", "belirtili nesne", "dolaylı tümleç", "zarf tümleci"),
        "cumle_turleri" to listOf("cümle türleri", "isim cümlesi", "fiil cümlesi", "birleşik cümle", "sıralı cümle", "devrik"),
        "anlatim_bozukluklari" to listOf("anlatım bozukluğu", "gereksiz sözcük", "özne-yüklem uyuşmazlığı"),
        // TYT & AYT Geometri
        "dogruda_acilar" to listOf("doğruda açı", "paralel", "tümler", "bütünler"),
        "ucgende_acilar" to listOf("üçgende açı", "iç açılar", "dış açı"),
        "dik_ucgen" to listOf("dik üçgen", "pisagor", "öklid", "hipotenüs"),
        "ikizkenar_eskenar_ucgen" to listOf("ikizkenar", "eşkenar üçgen"),
        "ucgende_aciortay" to listOf("açıortay", "iç açıortay", "dış açıortay"),
        "ucgende_kenarortay" to listOf("kenarortay", "ağırlık merkezi"),
        "ucgende_eslik_benzerlik" to listOf("benzerlik", "eşlik", "thales", "kelebek"),
        "ucgende_alan" to listOf("üçgende alan", "alan(abc)", "boyalı alan"),
        "cokgenler" to listOf("çokgen", "düzgün beşgen", "düzgün altıgen", "iç açılar toplamı"),
        "dortgenler" to listOf("dörtgen", "köşegen"),
        "yamuk" to listOf("yamuk", "ikizkenar yamuk", "dik yamuk", "orta taban"),
        "paralelkenar" to listOf("paralelkenar"),
        "dikdortgen_kare" to listOf("dikdörtgen", "kare"),
        "cember_daire_tyt" to listOf("çember", "daire", "yarıçap", "kiriş", "teğet", "merkez açı", "çevre açı"),
        "prizmalar_tyt" to listOf("prizma", "küp", "dikdörtgenler prizması", "silindir", "hacim", "yüzey alanı"),
        "piramit_koni_kure_tyt" to listOf("piramit", "koni", "küre")
    )

    fun classifyDifficulty5FromText(
        explicitDifficulty: String,
        headerOrTestTitle: String,
        stemText: String = "",
        questionIndexInTest: Int = 0
    ): DifficultyLevel = classifyDifficulty5FromText(
        rawDifficultyHint = explicitDifficulty,
        headerOrQuestionText = "$headerOrTestTitle $stemText",
        questionIndexInPage = (questionIndexInTest - 1).coerceAtLeast(0)
    )

    fun matchBestTopic(
        rawTopicText: String,
        subject: YksSubject?,
        fallbackExamType: ExamType = ExamType.AYT
    ): YksTopic {
        val pair = matchBestTopic(
            examTypeHint = subject?.examType?.name ?: fallbackExamType.name,
            subjectNameHint = subject?.name ?: "Matematik",
            topicOrQuestionText = rawTopicText,
            fallbackTopicName = subject?.topics?.firstOrNull()?.name ?: ""
        )
        return pair.second
    }

    /**
     * Deterministically resolves the exact `YksSubject` and `YksTopic` from raw PDF text/topic name,
     * NEVER falling back to modulo rotation across unrelated topics.
     */
    fun matchBestTopic(
        examTypeHint: String,
        subjectNameHint: String,
        topicOrQuestionText: String,
        fallbackTopicName: String = ""
    ): Pair<YksSubject, YksTopic> {
        val cleanExam = if (examTypeHint.contains("AYT", ignoreCase = true)) ExamType.AYT else ExamType.TYT
        val cleanSubj = subjectNameHint
            .replace("TYT ", "", ignoreCase = true)
            .replace("AYT ", "", ignoreCase = true)
            .trim()

        val matchedSubject = allSubjects.find {
            it.examType == cleanExam && it.name.equals(cleanSubj, ignoreCase = true)
        } ?: allSubjects.find {
            it.name.equals(cleanSubj, ignoreCase = true)
        } ?: allSubjects.find {
            it.examType == cleanExam
        } ?: allSubjects.first()

        val combinedSearch = "$topicOrQuestionText $fallbackTopicName".trim()
        val lowerSearch = combinedSearch.lowercase()

        // 1. Direct topic ID or exact topic name match within matchedSubject
        matchedSubject.topics.firstOrNull { t ->
            t.id.equals(topicOrQuestionText.trim(), ignoreCase = true) ||
                t.name.equals(topicOrQuestionText.trim(), ignoreCase = true) ||
                lowerSearch.contains(t.name.lowercase())
        }?.let { return matchedSubject to it }

        // 2. Check across all subjects of the same ExamType if a specific topic name is explicitly mentioned (e.g. Olasılık in AYT book)
        val sameExamTopics = allSubjects.filter { it.examType == matchedSubject.examType }
        for (subj in sameExamTopics) {
            for (t in subj.topics) {
                if (t.name.equals(topicOrQuestionText.trim(), ignoreCase = true) ||
                    lowerSearch.contains(t.name.lowercase())
                ) {
                    return subj to t
                }
            }
        }

        // 3. Keyword alias scoring inside matchedSubject first
        var bestTopic: YksTopic? = null
        var bestScore = 0
        for (t in matchedSubject.topics) {
            val aliases = topicKeywordAliases[t.id].orEmpty()
            val hits = aliases.count { alias -> lowerSearch.contains(alias.lowercase()) }
            val wordHits = t.name.split(" ", "-", "ve")
                .map { it.trim().lowercase() }
                .filter { it.length >= 4 && it != "uygulamaları" && it != "uygulamalari" && it != "kavramlar" }
                .count { w -> lowerSearch.contains(w) }
            val score = hits * 3 + wordHits * 2
            if (score > bestScore) {
                bestScore = score
                bestTopic = t
            }
        }
        if (bestTopic != null && bestScore > 0) {
            return matchedSubject to bestTopic
        }

        // 4. Fallback to fallbackTopicName match or first topic of matchedSubject (deterministic, NEVER modulo!)
        val fallbackMatched = if (fallbackTopicName.isNotBlank()) {
            matchedSubject.topics.firstOrNull { t ->
                t.name.equals(fallbackTopicName.trim(), ignoreCase = true) ||
                    t.name.contains(fallbackTopicName.trim(), ignoreCase = true) ||
                    fallbackTopicName.contains(t.name, ignoreCase = true)
            }
        } else null

        return matchedSubject to (fallbackMatched ?: matchedSubject.topics.first())
    }

    private fun topicEntry(
        id: String,
        subjectId: String,
        name: String,
        completedTests: Int = 0,
        badgeColorHex: Long = 0xFFE05A47,
        iconType: String = "math"
    ): YksTopic = YksTopic(
        id = id,
        subjectId = subjectId,
        name = name,
        questionCount = 0,
        completedTests = 0,
        totalTests = 0,
        badgeColorHex = badgeColorHex,
        iconType = iconType,
        tests = emptyList()
    )

    val allSubjects: List<YksSubject> = listOf(
        // ==================== TYT DERSLERİ VE AYRI/TAM KONULARI ====================
        YksSubject(
            id = "tyt_turkce",
            examType = ExamType.TYT,
            name = "Türkçe",
            accentColorHex = 0xFFE5A024,
            topics = listOf(
                topicEntry("sozcukte_anlam", "tyt_turkce", "Sözcükte ve Söz Öbeğinde Anlam", 0, 0xFFE5A024, "book"),
                topicEntry("cumlede_anlam", "tyt_turkce", "Cümlede Anlam", 0, 0xFFE5A024, "book"),
                topicEntry("paragrafta_anlam", "tyt_turkce", "Paragrafta Anlam", 0, 0xFFE5A024, "book"),
                topicEntry("soz_obeginde_anlam", "tyt_turkce", "Söz Öbeğinde Anlam", 0, 0xFFE5A024, "book"),
                topicEntry("paragrafta_yapi", "tyt_turkce", "Paragrafta Yapı", 0, 0xFFE5A024, "book"),
                topicEntry("anlatim_bicimleri", "tyt_turkce", "Anlatım Biçimleri", 0, 0xFFE5A024, "book"),
                topicEntry("ses_bilgisi", "tyt_turkce", "Ses Bilgisi", 0, 0xFFE5A024, "book"),
                topicEntry("yazim_kurallari", "tyt_turkce", "Yazım Kuralları", 0, 0xFFE5A024, "book"),
                topicEntry("noktalama_isaretleri", "tyt_turkce", "Noktalama İşaretleri", 0, 0xFFE5A024, "book"),
                topicEntry("sozcukte_yapi", "tyt_turkce", "Sözcükte Yapı ve Ekler", 0, 0xFFE5A024, "book"),
                topicEntry("isimler", "tyt_turkce", "İsimler (Adlar)", 0, 0xFFE5A024, "book"),
                topicEntry("sifatlar", "tyt_turkce", "Sıfatlar (Ön Adlar)", 0, 0xFFE5A024, "book"),
                topicEntry("zamirler", "tyt_turkce", "Zamirler (Adıllar)", 0, 0xFFE5A024, "book"),
                topicEntry("zarflar", "tyt_turkce", "Zarflar (Belirteçler)", 0, 0xFFE5A024, "book"),
                topicEntry("edat_baglac_unlem", "tyt_turkce", "Edat, Bağlaç ve Ünlem", 0, 0xFFE5A024, "book"),
                topicEntry("fiiller", "tyt_turkce", "Fiiller (Eylemler)", 0, 0xFFE5A024, "book"),
                topicEntry("fiilimsiler", "tyt_turkce", "Fiilimsiler (Eylemsiler)", 0, 0xFFE5A024, "book"),
                topicEntry("fiilde_cati", "tyt_turkce", "Fiilde Çatı", 0, 0xFFE5A024, "book"),
                topicEntry("cumlenin_ogeleri", "tyt_turkce", "Cümlenin Ögeleri", 0, 0xFFE5A024, "book"),
                topicEntry("cumle_turleri", "tyt_turkce", "Cümle Türleri", 0, 0xFFE5A024, "book"),
                topicEntry("anlatim_bozukluklari", "tyt_turkce", "Anlatım Bozuklukları", 0, 0xFFE5A024, "book")
            )
        ),
        YksSubject(
            id = "tyt_mat",
            examType = ExamType.TYT,
            name = "Matematik",
            accentColorHex = 0xFFE06345,
            topics = listOf(
                topicEntry("temel_kavramlar", "tyt_mat", "Temel Kavramlar", 0, 0xFFE06345, "math"),
                topicEntry("bolme_bolunebilme", "tyt_mat", "Bölme Bölünebilme / EBOB - EKOK", 0, 0xFFE06345, "math"),
                topicEntry("rasyonel_sayilar", "tyt_mat", "Rasyonel Sayılar", 0, 0xFFE06345, "math"),
                topicEntry("sayi_basamaklari", "tyt_mat", "Sayı Basamakları", 0, 0xFFE06345, "math"),
                topicEntry("ebob_ekok", "tyt_mat", "EBOB - EKOK", 0, 0xFFE06345, "math"),
                topicEntry("ondalik_sayilar", "tyt_mat", "Ondalık Sayılar", 0, 0xFFE05A47, "math"),
                topicEntry("birinci_dereceden_denklemler", "tyt_mat", "1. Dereceden Denklemler", 0, 0xFFE05A47, "math"),
                topicEntry("basit_esitsizlikler", "tyt_mat", "Basit Eşitsizlikler", 0, 0xFFE05A47, "math"),
                topicEntry("mutlak_deger", "tyt_mat", "Mutlak Değer", 0, 0xFFE05A47, "math"),
                topicEntry("uslu_sayilar", "tyt_mat", "Üslü Sayılar", 0, 0xFFE05A47, "math"),
                topicEntry("koklu_sayilar", "tyt_mat", "Köklü Sayılar", 0, 0xFFE05A47, "math"),
                topicEntry("carpanlara_ayirma", "tyt_mat", "Çarpanlara Ayırma", 0, 0xFFE05A47, "math"),
                topicEntry("oran_oranti", "tyt_mat", "Oran - Orantı", 0, 0xFFE05A47, "math"),
                topicEntry("sayi_problemleri", "tyt_mat", "Sayı Problemleri", 0, 0xFFE05A47, "math"),
                topicEntry("kesir_problemleri", "tyt_mat", "Kesir Problemleri", 0, 0xFFE05A47, "math"),
                topicEntry("yas_problemleri", "tyt_mat", "Yaş Problemleri", 0, 0xFFE05A47, "math"),
                topicEntry("isci_problemleri", "tyt_mat", "İşçi Problemleri", 0, 0xFFE05A47, "math"),
                topicEntry("hareket_problemleri", "tyt_mat", "Hız ve Hareket Problemleri", 0, 0xFFE05A47, "math"),
                topicEntry("yuzde_kar_zarar_problemleri", "tyt_mat", "Yüzde ve Kâr-Zarar Problemleri", 0, 0xFFE05A47, "math"),
                topicEntry("karisim_problemleri", "tyt_mat", "Karışım Problemleri", 0, 0xFFE05A47, "math"),
                topicEntry("grafik_problemleri", "tyt_mat", "Grafik Problemleri", 0, 0xFFE05A47, "math"),
                topicEntry("rutin_olmayan_problemler", "tyt_mat", "Rutin Olmayan Problemler", 0, 0xFFE05A47, "math"),
                topicEntry("mantik", "tyt_mat", "Mantık", 0, 0xFFE05A47, "math"),
                topicEntry("kumeler", "tyt_mat", "Kümeler", 0, 0xFFE05A47, "math"),
                topicEntry("kartezyen_carpim", "tyt_mat", "Kartezyen Çarpım", 0, 0xFFE05A47, "math"),
                topicEntry("tyt_fonksiyonlar", "tyt_mat", "Fonksiyonlar", 0, 0xFFE05A47, "math"),
                topicEntry("tyt_polinomlar", "tyt_mat", "Polinomlar", 0, 0xFFE05A47, "math"),
                topicEntry("tyt_ikinci_derece_denklemler", "tyt_mat", "2. Dereceden Denklemler", 0, 0xFFE05A47, "math"),
                topicEntry("permutasyon", "tyt_mat", "Permütasyon", 0, 0xFFE05A47, "math"),
                topicEntry("kombinasyon", "tyt_mat", "Kombinasyon", 0, 0xFFE05A47, "math"),
                topicEntry("binom", "tyt_mat", "Binom Açılımı", 0, 0xFFE05A47, "math"),
                topicEntry("olasilik", "tyt_mat", "Olasılık", 0, 0xFFE05A47, "math"),
                topicEntry("veri_istatistik", "tyt_mat", "Veri ve İstatistik", 0, 0xFFE05A47, "math")
            )
        ),
        YksSubject(
            id = "tyt_geo",
            examType = ExamType.TYT,
            name = "Geometri",
            accentColorHex = 0xFF4A7BF0,
            topics = listOf(
                topicEntry("dogruda_acilar", "tyt_geo", "Doğruda Açılar", 0, 0xFF0EA5E9, "angle"),
                topicEntry("ucgende_acilar", "tyt_geo", "Üçgende Açılar", 0, 0xFF0EA5E9, "angle"),
                topicEntry("dik_ucgen", "tyt_geo", "Dik Üçgen ve Pisagor", 0, 0xFF0EA5E9, "angle"),
                topicEntry("ikizkenar_eskenar_ucgen", "tyt_geo", "İkizkenar ve Eşkenar Üçgen", 0, 0xFF0EA5E9, "angle"),
                topicEntry("ucgende_aciortay", "tyt_geo", "Üçgende Açıortay", 0, 0xFF0EA5E9, "angle"),
                topicEntry("ucgende_kenarortay", "tyt_geo", "Üçgende Kenarortay", 0, 0xFF0EA5E9, "angle"),
                topicEntry("ucgende_eslik_benzerlik", "tyt_geo", "Üçgende Eşlik ve Benzerlik", 0, 0xFF0EA5E9, "angle"),
                topicEntry("ucgende_alan", "tyt_geo", "Üçgende Alan", 0, 0xFF0EA5E9, "angle"),
                topicEntry("aci_kenar_bagintilari", "tyt_geo", "Açı - Kenar Bağıntıları", 0, 0xFF0EA5E9, "angle"),
                topicEntry("cokgenler", "tyt_geo", "Çokgenler", 0, 0xFF0EA5E9, "angle"),
                topicEntry("dortgenler", "tyt_geo", "Genel Dörtgenler", 0, 0xFF0EA5E9, "angle"),
                topicEntry("yamuk", "tyt_geo", "Yamuk", 0, 0xFF0EA5E9, "angle"),
                topicEntry("paralelkenar", "tyt_geo", "Paralelkenar", 0, 0xFF0EA5E9, "angle"),
                topicEntry("eskenar_dortgen_deltoid", "tyt_geo", "Eşkenar Dörtgen ve Deltoid", 0, 0xFF0EA5E9, "angle"),
                topicEntry("dikdortgen_kare", "tyt_geo", "Dikdörtgen ve Kare", 0, 0xFF0EA5E9, "angle"),
                topicEntry("cember_daire_tyt", "tyt_geo", "Çember ve Daire", 0, 0xFF0EA5E9, "angle"),
                topicEntry("prizmalar_tyt", "tyt_geo", "Prizmalar", 0, 0xFF0EA5E9, "angle"),
                topicEntry("piramit_koni_kure_tyt", "tyt_geo", "Piramit, Koni ve Küre", 0, 0xFF0EA5E9, "angle")
            )
        ),
        YksSubject(
            id = "tyt_fizik",
            examType = ExamType.TYT,
            name = "Fizik",
            accentColorHex = 0xFF10B981,
            topics = listOf(
                topicEntry("fizik_bilimine_giris", "tyt_fizik", "Fizik Bilimine Giriş", 0, 0xFF10B981, "vector"),
                topicEntry("madde_ve_ozellikleri", "tyt_fizik", "Madde ve Özellikleri", 0, 0xFF10B981, "vector"),
                topicEntry("hareket_tyt", "tyt_fizik", "Doğrusal Hareket", 0, 0xFF10B981, "vector"),
                topicEntry("kuvvet_newton_tyt", "tyt_fizik", "Kuvvet ve Newton Yasaları", 0, 0xFF10B981, "vector"),
                topicEntry("is_guc_enerji_tyt", "tyt_fizik", "İş, Güç ve Enerji", 0, 0xFF10B981, "vector"),
                topicEntry("isi_sicaklik", "tyt_fizik", "Isı ve Sıcaklık", 0, 0xFF10B981, "vector"),
                topicEntry("genlesme", "tyt_fizik", "Hal Değişimi ve Genleşme", 0, 0xFF10B981, "vector"),
                topicEntry("basinc", "tyt_fizik", "Basınç", 0, 0xFF10B981, "vector"),
                topicEntry("kaldirma_kuvveti", "tyt_fizik", "Kaldırma Kuvveti", 0, 0xFF10B981, "vector"),
                topicEntry("elektrostatik", "tyt_fizik", "Elektrostatik", 0, 0xFF10B981, "vector"),
                topicEntry("elektrik_akimi_devreler", "tyt_fizik", "Elektrik Akımı ve Devreler", 0, 0xFF10B981, "vector"),
                topicEntry("manyetizma_tyt", "tyt_fizik", "Mıknatıslar ve Manyetizma", 0, 0xFF10B981, "vector"),
                topicEntry("dalgalar_tyt", "tyt_fizik", "Yay, Su, Ses ve Deprem Dalgaları", 0, 0xFF10B981, "vector"),
                topicEntry("optik_aydinlanma_aynalar", "tyt_fizik", "Aydınlanma ve Aynalar", 0, 0xFF10B981, "vector"),
                topicEntry("optik_kirilma_mercekler", "tyt_fizik", "Kırılma, Mercekler ve Renk", 0, 0xFF10B981, "vector")
            )
        ),
        YksSubject(
            id = "tyt_kimya",
            examType = ExamType.TYT,
            name = "Kimya",
            accentColorHex = 0xFF8B5CF6,
            topics = listOf(
                topicEntry("kimya_bilimi", "tyt_kimya", "Kimya Bilimi", 0, 0xFF8B5CF6, "flask"),
                topicEntry("atom_modelleri", "tyt_kimya", "Atom ve Yapısı", 0, 0xFF8B5CF6, "flask"),
                topicEntry("periyodik_sistem", "tyt_kimya", "Periyodik Sistem", 0, 0xFF8B5CF6, "flask"),
                topicEntry("kimyasal_turler_etkilesim", "tyt_kimya", "Kimyasal Türler Arası Etkileşimler", 0, 0xFF8B5CF6, "flask"),
                topicEntry("maddenin_halleri", "tyt_kimya", "Maddenin Halleri", 0, 0xFF8B5CF6, "flask"),
                topicEntry("doga_ve_kimya", "tyt_kimya", "Doğa ve Kimya", 0, 0xFF8B5CF6, "flask"),
                topicEntry("kimyanin_temel_kanunlari", "tyt_kimya", "Kimyanın Temel Kanunları", 0, 0xFF8B5CF6, "flask"),
                topicEntry("mol_kavrami", "tyt_kimya", "Mol Kavramı", 0, 0xFF8B5CF6, "flask"),
                topicEntry("kimyasal_hesaplamalar", "tyt_kimya", "Kimyasal Tepkimeler ve Hesaplamalar", 0, 0xFF8B5CF6, "flask"),
                topicEntry("karisimlar", "tyt_kimya", "Karışımlar", 0, 0xFF8B5CF6, "flask"),
                topicEntry("asitler_bazlar_tuzlar", "tyt_kimya", "Asitler, Bazlar ve Tuzlar", 0, 0xFF8B5CF6, "flask"),
                topicEntry("kimya_her_yerde", "tyt_kimya", "Kimya Her Yerde", 0, 0xFF8B5CF6, "flask")
            )
        ),
        YksSubject(
            id = "tyt_biyoloji",
            examType = ExamType.TYT,
            name = "Biyoloji",
            accentColorHex = 0xFFEC4899,
            topics = listOf(
                topicEntry("canlilarin_ortak_ozellikleri", "tyt_biyoloji", "Canlıların Ortak Özellikleri", 0, 0xFFEC4899, "bio"),
                topicEntry("canlilarin_temel_bilesenleri", "tyt_biyoloji", "Canlıların Temel Bileşenleri", 0, 0xFFEC4899, "bio"),
                topicEntry("hucre_ve_organeller", "tyt_biyoloji", "Hücre ve Organeller", 0, 0xFFEC4899, "bio"),
                topicEntry("hucre_zarindan_madde_gecisi", "tyt_biyoloji", "Hücre Zarından Madde Geçişleri", 0, 0xFFEC4899, "bio"),
                topicEntry("canlilarin_siniflandirilmasi", "tyt_biyoloji", "Canlıların Sınıflandırılması", 0, 0xFFEC4899, "bio"),
                topicEntry("mitoz_ve_eseysiz_ureme", "tyt_biyoloji", "Mitoz ve Eşeysiz Üreme", 0, 0xFFEC4899, "bio"),
                topicEntry("mayoz_ve_eseyli_ureme", "tyt_biyoloji", "Mayoz ve Eşeyli Üreme", 0, 0xFFEC4899, "bio"),
                topicEntry("kalitim", "tyt_biyoloji", "Kalıtım ve Biyoçeşitlilik", 0, 0xFFEC4899, "bio"),
                topicEntry("ekosistem_ekolojisi", "tyt_biyoloji", "Ekosistem Ekolojisi", 0, 0xFFEC4899, "bio"),
                topicEntry("guncel_cevre_sorunlari", "tyt_biyoloji", "Güncel Çevre Sorunları", 0, 0xFFEC4899, "bio")
            )
        ),
        YksSubject(
            id = "tyt_tarih",
            examType = ExamType.TYT,
            name = "Tarih",
            accentColorHex = 0xFF2563EB,
            topics = listOf(
                topicEntry("tarih_ve_zaman_tyt", "tyt_tarih", "Tarih ve Zaman", 0, 0xFF2563EB, "book"),
                topicEntry("insanligin_ilk_donemleri", "tyt_tarih", "İnsanlığın İlk Dönemleri", 0, 0xFF2563EB, "book"),
                topicEntry("ilk_orta_cag_turk", "tyt_tarih", "İlk ve Orta Çağlarda Türk Dünyası", 0, 0xFF2563EB, "book"),
                topicEntry("islam_medeniyetinin_dogusu", "tyt_tarih", "İslam Medeniyetinin Doğuşu", 0, 0xFF2563EB, "book"),
                topicEntry("turklerin_islamiyeti_kabulu", "tyt_tarih", "Türklerin İslamiyet'i Kabulü", 0, 0xFF2563EB, "book"),
                topicEntry("beylikten_devlete_osmanli", "tyt_tarih", "Beylikten Devlete Osmanlı", 0, 0xFF2563EB, "book"),
                topicEntry("dunya_gucu_osmanli", "tyt_tarih", "Dünya Gücü Osmanlı", 0, 0xFF2563EB, "book"),
                topicEntry("kurtulus_savasi_hazirlik", "tyt_tarih", "Milli Mücadele Hazırlık Dönemi", 0, 0xFF2563EB, "book"),
                topicEntry("kurtulus_savasi_cepheler", "tyt_tarih", "Kurtuluş Savaşı Cepheler ve Antlaşmalar", 0, 0xFF2563EB, "book"),
                topicEntry("ataturk_ilke_inkilaplari", "tyt_tarih", "Atatürk İlke ve İnkılapları", 0, 0xFF2563EB, "book")
            )
        ),
        YksSubject(
            id = "tyt_cografya",
            examType = ExamType.TYT,
            name = "Coğrafya",
            accentColorHex = 0xFF0D9488,
            topics = listOf(
                topicEntry("doga_ve_insan", "tyt_cografya", "Doğa ve İnsan", 0, 0xFF0D9488, "book"),
                topicEntry("dunyanin_sekli_hareketleri", "tyt_cografya", "Dünya'nın Şekli ve Hareketleri", 0, 0xFF0D9488, "book"),
                topicEntry("cografi_konum", "tyt_cografya", "Coğrafi Konum", 0, 0xFF0D9488, "book"),
                topicEntry("harita_bilgisi", "tyt_cografya", "Harita Bilgisi", 0, 0xFF0D9488, "book"),
                topicEntry("atmosfer_ve_iklim", "tyt_cografya", "Atmosfer ve İklim Bilgisi", 0, 0xFF0D9488, "book"),
                topicEntry("ic_ve_dis_kuvvetler", "tyt_cografya", "İç ve Dış Kuvvetler", 0, 0xFF0D9488, "book"),
                topicEntry("su_toprak_bitki", "tyt_cografya", "Su, Toprak ve Bitki Varlığı", 0, 0xFF0D9488, "book"),
                topicEntry("nufus_ve_yerlesme", "tyt_cografya", "Nüfus ve Göç", 0, 0xFF0D9488, "book"),
                topicEntry("ekonomik_faaliyetler", "tyt_cografya", "Ekonomik Faaliyetler ve Bölgeler", 0, 0xFF0D9488, "book"),
                topicEntry("dogal_afetler", "tyt_cografya", "Doğal Afetler", 0, 0xFF0D9488, "book")
            )
        ),
        YksSubject(
            id = "tyt_felsefe_din",
            examType = ExamType.TYT,
            name = "Felsefe ve Din Kültürü",
            accentColorHex = 0xFF6366F1,
            topics = listOf(
                topicEntry("felsefeyi_tanima", "tyt_felsefe_din", "Felsefeyi Tanıma", 0, 0xFF6366F1, "book"),
                topicEntry("bilgi_felsefesi", "tyt_felsefe_din", "Bilgi Felsefesi", 0, 0xFF6366F1, "book"),
                topicEntry("varlik_felsefesi", "tyt_felsefe_din", "Varlık Felsefesi", 0, 0xFF6366F1, "book"),
                topicEntry("ahlak_felsefesi", "tyt_felsefe_din", "Ahlak Felsefesi", 0, 0xFF6366F1, "book"),
                topicEntry("bilgi_ve_inanc", "tyt_felsefe_din", "Bilgi ve İnanç", 0, 0xFF6366F1, "book"),
                topicEntry("din_ve_islam", "tyt_felsefe_din", "Din, İbadet ve Ahlak", 0, 0xFF6366F1, "book")
            )
        ),

        // ==================== AYT DERSLERİ VE AYRI/TAM KONULARI ====================
        YksSubject(
            id = "ayt_mat",
            examType = ExamType.AYT,
            name = "Matematik",
            accentColorHex = 0xFFE05A47,
            topics = listOf(
                topicEntry("fonksiyonlar", "ayt_mat", "Fonksiyonlarda Uygulamalar", 0, 0xFFE05A47, "math"),
                topicEntry("polinomlar", "ayt_mat", "Polinomlar", 0, 0xFFE05A47, "math"),
                topicEntry("ikinci_derece_denklemler", "ayt_mat", "2. Dereceden Denklemler", 0, 0xFFE05A47, "math"),
                topicEntry("karmasik_sayilar", "ayt_mat", "Karmaşık Sayılar", 0, 0xFFE05A47, "math"),
                topicEntry("parabol", "ayt_mat", "Parabol", 0, 0xFFE05A47, "math"),
                topicEntry("ikinci_derece_esitsizlikler", "ayt_mat", "2. Dereceden Eşitsizlikler", 0, 0xFFE05A47, "math"),
                topicEntry("trigonometri_ayt_mat", "ayt_mat", "Trigonometri", 0, 0xFFE05A47, "math"),
                topicEntry("logaritma", "ayt_mat", "Logaritma", 0, 0xFFE05A47, "math"),
                topicEntry("diziler", "ayt_mat", "Diziler", 0, 0xFFE05A47, "math"),
                topicEntry("limit_ve_sureklilik", "ayt_mat", "Limit ve Süreklilik", 0, 0xFFE05A47, "math"),
                topicEntry("turev_alma_kurallari", "ayt_mat", "Türev Alma Kuralları", 0, 0xFFE05A47, "math"),
                topicEntry("turev_uygulamalari", "ayt_mat", "Türevin Geometrik ve Fiziksel Uygulamaları", 0, 0xFFE05A47, "math"),
                topicEntry("belirsiz_integral", "ayt_mat", "Belirsiz İntegral", 0, 0xFFE05A47, "math"),
                topicEntry("belirli_integral_alan", "ayt_mat", "Belirli İntegral ve Alan Hesabı", 0, 0xFFE05A47, "math")
            )
        ),
        YksSubject(
            id = "ayt_geo",
            examType = ExamType.AYT,
            name = "Geometri",
            accentColorHex = 0xFF0EA5E9,
            topics = listOf(
                topicEntry("cemberde_aci", "ayt_geo", "Çemberde Açı", 0, 0xFF0EA5E9, "angle"),
                topicEntry("cemberde_uzunluk", "ayt_geo", "Çemberde Uzunluk", 0, 0xFF0EA5E9, "angle"),
                topicEntry("dairede_alan", "ayt_geo", "Dairede Çevre ve Alan", 0, 0xFF0EA5E9, "angle"),
                topicEntry("noktanin_analitigi", "ayt_geo", "Noktanın Analitik İncelenmesi", 0, 0xFF0EA5E9, "angle"),
                topicEntry("dogrunun_analitigi", "ayt_geo", "Doğrunun Analitik İncelenmesi", 0, 0xFF0EA5E9, "angle"),
                topicEntry("donusum_geometrisi", "ayt_geo", "Dönüşüm Geometrisi", 0, 0xFF0EA5E9, "angle"),
                topicEntry("cemberin_analitigi", "ayt_geo", "Çemberin Analitik İncelenmesi", 0, 0xFF0EA5E9, "angle"),
                topicEntry("kati_cisimler_ayt", "ayt_geo", "Silindir, Koni ve Küre", 0, 0xFF0EA5E9, "angle")
            )
        ),
        YksSubject(
            id = "ayt_fizik",
            examType = ExamType.AYT,
            name = "Fizik",
            accentColorHex = 0xFF10B981,
            topics = listOf(
                topicEntry("vektorler", "ayt_fizik", "Vektörler", 0, 0xFF10B981, "vector"),
                topicEntry("bagil_hareket", "ayt_fizik", "Bağıl Hareket", 0, 0xFF10B981, "vector"),
                topicEntry("newton_hareket_yasalari", "ayt_fizik", "Newton'un Hareket Yasaları", 0, 0xFF10B981, "vector"),
                topicEntry("bir_boyutta_sabit_ivmeli", "ayt_fizik", "Bir Boyutta Sabit İvmeli Hareket", 0, 0xFF10B981, "vector"),
                topicEntry("atislar", "ayt_fizik", "Atışlar", 0, 0xFF10B981, "vector"),
                topicEntry("enerji_ve_hareket", "ayt_fizik", "İş, Enerji ve Güç", 0, 0xFF10B981, "vector"),
                topicEntry("itme_momentum", "ayt_fizik", "İtme ve Çizgisel Momentum", 0, 0xFF10B981, "vector"),
                topicEntry("tork_ve_denge", "ayt_fizik", "Tork ve Denge", 0, 0xFF10B981, "vector"),
                topicEntry("kutle_merkezi_basit_makine", "ayt_fizik", "Kütle Merkezi ve Basit Makineler", 0, 0xFF10B981, "vector"),
                topicEntry("elektrik_alan_potansiyel", "ayt_fizik", "Elektrik Alan ve Potansiyel", 0, 0xFF10B981, "vector"),
                topicEntry("manyetizma_induksiyon", "ayt_fizik", "Manyetizma ve Elektromanyetik İndüksiyon", 0, 0xFF10B981, "vector"),
                topicEntry("alternatif_akim_transformator", "ayt_fizik", "Alternatif Akım ve Transformatörler", 0, 0xFF10B981, "vector"),
                topicEntry("cembersel_hareket", "ayt_fizik", "Düzgün Çembersel Hareket", 0, 0xFF10B981, "vector"),
                topicEntry("basit_harmonik_hareket", "ayt_fizik", "Basit Harmonik Hareket", 0, 0xFF10B981, "vector"),
                topicEntry("dalga_mekanigi", "ayt_fizik", "Dalga Mekaniği", 0, 0xFF10B981, "vector"),
                topicEntry("atom_fizigi_radyoaktivite", "ayt_fizik", "Atom Fiziğine Giriş ve Radyoaktivite", 0, 0xFF10B981, "vector"),
                topicEntry("modern_fizik", "ayt_fizik", "Modern Fizik", 0, 0xFF10B981, "vector")
            )
        ),
        YksSubject(
            id = "ayt_kimya",
            examType = ExamType.AYT,
            name = "Kimya",
            accentColorHex = 0xFF8B5CF6,
            topics = listOf(
                topicEntry("modern_atom_teorisi", "ayt_kimya", "Modern Atom Teorisi", 0, 0xFF8B5CF6, "flask"),
                topicEntry("gazlar", "ayt_kimya", "Gazlar", 0, 0xFF8B5CF6, "flask"),
                topicEntry("sivi_cozeltiler", "ayt_kimya", "Sıvı Çözeltiler ve Çözünürlük", 0, 0xFF8B5CF6, "flask"),
                topicEntry("kimyasal_enerji", "ayt_kimya", "Kimyasal Tepkimelerde Enerji", 0, 0xFF8B5CF6, "flask"),
                topicEntry("kimyasal_hiz", "ayt_kimya", "Kimyasal Tepkimelerde Hız", 0, 0xFF8B5CF6, "flask"),
                topicEntry("kimyasal_denge", "ayt_kimya", "Kimyasal Tepkimelerde Denge", 0, 0xFF8B5CF6, "flask"),
                topicEntry("asit_baz_dengesi", "ayt_kimya", "Sulu Çözeltilerde Asit-Baz Dengesi", 0, 0xFF8B5CF6, "flask"),
                topicEntry("cozunurluk_dengesi", "ayt_kimya", "Çözünürlük Dengesi (Kçç)", 0, 0xFF8B5CF6, "flask"),
                topicEntry("kimya_ve_elektrik", "ayt_kimya", "Kimya ve Elektrik", 0, 0xFF8B5CF6, "flask"),
                topicEntry("karbon_kimyasina_giris", "ayt_kimya", "Karbon Kimyasına Giriş", 0, 0xFF8B5CF6, "flask"),
                topicEntry("organik_bilesikler", "ayt_kimya", "Organik Bileşikler", 0, 0xFF8B5CF6, "flask")
            )
        ),
        YksSubject(
            id = "ayt_biyoloji",
            examType = ExamType.AYT,
            name = "Biyoloji",
            accentColorHex = 0xFFEC4899,
            topics = listOf(
                topicEntry("sinir_sistemi", "ayt_biyoloji", "Sinir Sistemi", 0, 0xFFEC4899, "bio"),
                topicEntry("endokrin_sistem", "ayt_biyoloji", "Endokrin Sistem", 0, 0xFFEC4899, "bio"),
                topicEntry("duyu_organlari", "ayt_biyoloji", "Duyu Organları", 0, 0xFFEC4899, "bio"),
                topicEntry("destek_ve_hareket", "ayt_biyoloji", "Destek ve Hareket Sistemi", 0, 0xFFEC4899, "bio"),
                topicEntry("sindirim_sistemi", "ayt_biyoloji", "Sindirim Sistemi", 0, 0xFFEC4899, "bio"),
                topicEntry("dolasim_ve_bagisiklik", "ayt_biyoloji", "Dolaşım ve Bağışıklık Sistemi", 0, 0xFFEC4899, "bio"),
                topicEntry("solunum_sistemi", "ayt_biyoloji", "Solunum Sistemi", 0, 0xFFEC4899, "bio"),
                topicEntry("uriner_sistem", "ayt_biyoloji", "Üriner (Boşaltım) Sistem", 0, 0xFFEC4899, "bio"),
                topicEntry("ureme_sistemi_embriyonik", "ayt_biyoloji", "Üreme Sistemi ve Embriyonik Gelişim", 0, 0xFFEC4899, "bio"),
                topicEntry("komunite_ve_populasyon", "ayt_biyoloji", "Komünite ve Popülasyon Ekolojisi", 0, 0xFFEC4899, "bio"),
                topicEntry("genden_proteine", "ayt_biyoloji", "Genden Proteine (Nükleik Asitler)", 0, 0xFFEC4899, "bio"),
                topicEntry("canlilarda_enerji_donusumleri", "ayt_biyoloji", "Fotosentez ve Hücresel Solunum", 0, 0xFFEC4899, "bio"),
                topicEntry("bitki_biyolojisi", "ayt_biyoloji", "Bitki Biyolojisi", 0, 0xFFEC4899, "bio"),
                topicEntry("canlilar_ve_cevre", "ayt_biyoloji", "Canlılar ve Çevre", 0, 0xFFEC4899, "bio")
            )
        ),
        YksSubject(
            id = "ayt_edebiyat",
            examType = ExamType.AYT,
            name = "Edebiyat",
            accentColorHex = 0xFFE5A024,
            topics = listOf(
                topicEntry("guzel_sanatlar_ve_edebiyat", "ayt_edebiyat", "Güzel Sanatlar ve Edebiyat", 0, 0xFFE5A024, "book"),
                topicEntry("siir_bilgisi", "ayt_edebiyat", "Şiir Bilgisi", 0, 0xFFE5A024, "book"),
                topicEntry("edebi_sanatlar", "ayt_edebiyat", "Edebi Sanatlar (Söz Sanatları)", 0, 0xFFE5A024, "book"),
                topicEntry("islamiyet_oncesi_turk_edebiyati", "ayt_edebiyat", "İslamiyet Öncesi Türk Edebiyatı", 0, 0xFFE5A024, "book"),
                topicEntry("halk_edebiyati", "ayt_edebiyat", "Halk Edebiyatı", 0, 0xFFE5A024, "book"),
                topicEntry("divan_edebiyati", "ayt_edebiyat", "Divan Edebiyatı", 0, 0xFFE5A024, "book"),
                topicEntry("tanzimat_edebiyati", "ayt_edebiyat", "Tanzimat Dönemi Edebiyatı", 0, 0xFFE5A024, "book"),
                topicEntry("servetifunun_fecriati", "ayt_edebiyat", "Servetifünun ve Fecriati Edebiyatı", 0, 0xFFE5A024, "book"),
                topicEntry("milli_edebiyat", "ayt_edebiyat", "Milli Edebiyat Dönemi", 0, 0xFFE5A024, "book"),
                topicEntry("cumhuriyet_siir", "ayt_edebiyat", "Cumhuriyet Dönemi Şiir", 0, 0xFFE5A024, "book"),
                topicEntry("cumhuriyet_roman_hikaye", "ayt_edebiyat", "Cumhuriyet Dönemi Roman ve Hikâye", 0, 0xFFE5A024, "book"),
                topicEntry("edebi_akimlar", "ayt_edebiyat", "Edebi Akımlar", 0, 0xFFE5A024, "book")
            )
        ),
        YksSubject(
            id = "ayt_tarih_cografya",
            examType = ExamType.AYT,
            name = "Tarih ve Coğrafya",
            accentColorHex = 0xFF2563EB,
            topics = listOf(
                topicEntry("ayt_ilk_cag_uygarliklari", "ayt_tarih_cografya", "İlk ve Orta Çağ Uygarlıkları", 0, 0xFF2563EB, "book"),
                topicEntry("ayt_osmanli_kultur_medeniyet", "ayt_tarih_cografya", "Osmanlı Kültür ve Medeniyeti", 0, 0xFF2563EB, "book"),
                topicEntry("ayt_xx_yuzyil_osmanli", "ayt_tarih_cografya", "20. Yüzyıl Başlarında Osmanlı ve Dünya", 0, 0xFF2563EB, "book"),
                topicEntry("ayt_milli_mucadele", "ayt_tarih_cografya", "Milli Mücadele ve İnkılap Tarihi", 0, 0xFF2563EB, "book"),
                topicEntry("ayt_ekosistem_ve_biyoceşitlilik", "ayt_tarih_cografya", "Biyoçeşitlilik ve Ekosistem", 0, 0xFF0D9488, "book"),
                topicEntry("ayt_turkiye_ekonomisi", "ayt_tarih_cografya", "Türkiye'de Tarım, Madenler ve Sanayi", 0, 0xFF0D9488, "book"),
                topicEntry("ayt_kuresel_ortam_bolgeler", "ayt_tarih_cografya", "Küresel Ortam: Bölgeler ve Ülkeler", 0, 0xFF0D9488, "book")
            )
        )
    )

    val mockExams: List<MockExamItem> = emptyList()

    val allTopicsFlat: List<YksTopic> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        allSubjects.flatMap { it.topics }
    }

    val topicByIdMap: Map<String, YksTopic> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        allTopicsFlat.associateBy { it.id }
    }

    val topicByNormalizedNameMap: Map<String, YksTopic> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        allTopicsFlat.associateBy { it.name.trim().lowercase() }
    }

    fun resolveTopicFast(topicId: String, topicName: String): YksTopic? {
        if (topicId.isNotBlank()) {
            topicByIdMap[topicId]?.let { return it }
        }
        val norm = topicName.trim().lowercase()
        if (norm.isNotBlank()) {
            topicByNormalizedNameMap[norm]?.let { return it }
            allTopicsFlat.firstOrNull {
                norm.contains(it.name.lowercase()) || it.name.lowercase().contains(norm)
            }?.let { return it }
        }
        return null
    }
}
