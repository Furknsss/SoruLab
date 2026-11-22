package com.example.data

/**
 * SoruLab v1.5.1 — AYT Matematik & TYT Sosyal Soru-Tipi Odaklı Video Kataloğu (Bölüm 2).
 *
 * Tüm 11 haneli YouTube video kimlikleri canlı doğrulamadan geçirilmiş olup
 * her bölüm gerçek video süresi içinde (`0 < startSeconds < endSeconds <= videoLength`)
 * ve farklı [OsymQuestionType] soru tiplerine özel olarak bölümlenmiştir.
 */
internal object TopicLectureVideoCatalogPart2 {

    val videos: List<TopicLectureVideo> = listOf(
        // =====================================================================
        // 1. AYT MATEMATİK — DİZİLER (Mert Hoca — 1:01:43 / 3703 sn)
        // =====================================================================
        TopicLectureVideo(
            id = "merthoca_ayt_diziler_full",
            videoId = "P3Lof_1ecZo",
            searchQueryFallback = "Mert Hoca AYT Full Tekrar Diziler konu anlatımı",
            title = "AYT Full Tekrar | Diziler (Aritmetik, Geometrik ve İndirgemeli Diziler)",
            teacherName = "Mert Hoca",
            channelName = "Mert Hoca",
            teacherBadge = "AYT Full Tekrar Lideri",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicIds = setOf("diziler"),
            topicKeywords = listOf(
                "dizi", "diziler", "genel terim", "aritmetik dizi", "geometrik dizi",
                "ortak fark", "ortak çarpan", "indirgemeli dizi", "sabit dizi", "fibonacci",
                "ilk n terim toplamı", "a_n", "(an)"
            ),
            kazanimCodes = setOf("MAT.12.2.1.1", "MAT.12.2.1.2", "MAT.12.2.1.3", "MAT.12.1.2.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "mert_diziler_tanim_indirgeme",
                    chapterTitle = "Dizi Tanımı, Sabit-Eşit Dizi ve İndirgemeli Diziler",
                    startSeconds = 120,
                    endSeconds = 980,
                    kazanimIds = setOf("KAZ_AYT_MAT_LOG_DIZI"),
                    kazanimCodes = setOf("MAT.12.2.1.1", "MAT.12.1.2.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf(
                        "dizi tanımı", "pozitif tam sayı", "genel terim", "sabit dizi",
                        "eşit dizi", "indirgemeli", "parçalı dizi", "kaçıncı terim", "kaç terimi tam sayı"
                    ),
                    keyFormulaSummary = "Dizi f: ℤ⁺ → ℝ fonksiyonudur. İndirgemeli dizide aₙ₊₁ - aₙ = f(n) ise taraf tarafa toplama, aₙ₊₁ / aₙ = g(n) ise taraf tarafa çarpma yapılır.",
                    osymTip = "Öncüllü dizi sorularında tanım kümesinin yalnızca pozitif tam sayılar (n ∈ {1, 2, 3, ...}) olduğunu unutma; paydayı sıfırlayan n ∈ ℤ⁺ varsa ifade dizi belirtmez!"
                ),
                LectureChapterSegment(
                    id = "mert_diziler_aritmetik",
                    chapterTitle = "Aritmetik Dizi Genel Terimi, Ortanca Terim ve İlk n Terim Toplamı",
                    startSeconds = 990,
                    endSeconds = 1860,
                    kazanimIds = setOf("KAZ_AYT_MAT_LOG_DIZI"),
                    kazanimCodes = setOf("MAT.12.2.1.2", "MAT.12.1.2.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf(
                        "aritmetik dizi", "ortak fark", "a_n = a_1 + (n-1)d", "ilk n terim toplamı",
                        "s_n", "ortanca terim", "ardışık terim", "terimler toplamı"
                    ),
                    keyFormulaSummary = "aₙ = aₚ + (n - p)·d, simetrik toplam aₘ + aₙ = aₖ + aₗ (m+n = k+l) ve Sₙ = (n / 2)·(a₁ + aₙ) = (n / 2)·[2a₁ + (n - 1)d].",
                    osymTip = "Aritmetik dizide Sₙ formülü 2. dereceden sabit terimsiz polinomdur (An² + Bn); Sₙ - Sₙ₋₁ = aₙ eşitliği tek satırda genel terimi verir."
                ),
                LectureChapterSegment(
                    id = "mert_diziler_geometrik",
                    chapterTitle = "Geometrik Dizi Ortak Çarpan, Toplam ve Logaritmalı Dizi Kurguları",
                    startSeconds = 1870,
                    endSeconds = 2780,
                    kazanimIds = setOf("KAZ_AYT_MAT_LOG_DIZI"),
                    kazanimCodes = setOf("MAT.12.2.1.3", "MAT.12.1.2.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf(
                        "geometrik dizi", "ortak çarpan", "r", "a_k^2", "terimler çarpımı",
                        "ilk n terim toplamı", "hem aritmetik hem geometrik", "logaritmalı dizi"
                    ),
                    keyFormulaSummary = "aₙ = aₚ·rⁿ⁻ᵖ, ortanca terim aₖ² = aₖ₋ₚ·aₖ₊ₚ ve Sₙ = a₁·(1 - rⁿ) / (1 - r). Hem aritmetik hem geometrik dizi mutlaka sabit dizidir.",
                    osymTip = "Pozitif terimli geometrik dizide terimlerin logaritması alındığında aritmetik dizi elde edilir — ÖSYM'nin en sevdiği melez soru kalıbıdır."
                ),
                LectureChapterSegment(
                    id = "mert_diziler_yeni_nesil",
                    chapterTitle = "Yeni Nesil Örüntü, Fibonacci ve Mantık-Muhakeme Dizi Soruları",
                    startSeconds = 2790,
                    endSeconds = 3640,
                    kazanimIds = setOf("KAZ_AYT_MAT_LOG_DIZI"),
                    kazanimCodes = setOf("MAT.12.2.1.1", "MAT.12.2.1.2", "MAT.12.2.1.3"),
                    questionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf(
                        "fibonacci", "örüntü", "şekil dizisi", "yeni nesil dizi", "kurgulu",
                        "en büyük terim", "en küçük terim", "tepe noktası"
                    ),
                    keyFormulaSummary = "İkinci dereceden genel terimli aₙ = An² + Bn + C dizisinin en büyük/en küçük terimi r = -B/(2A) değerine en yakın pozitif tam sayı n için oluşur.",
                    osymTip = "Örüntü ve Fibonacci tarzı yeni nesil sorularda ilk 5-6 terimi açıkça yazıp periyodik tekrarı veya fark dizisini yakala."
                )
            )
        ),

        // =====================================================================
        // 2. AYT/TYT MATEMATİK — PERMÜTASYON, KOMBİNASYON, BİNOM, OLASILIK (PKOB)
        //    Mert Hoca — 5:54:41 / 21281 sn (Gerçek YouTube Chapter: 02:18:15 & 03:09:53)
        // =====================================================================
        TopicLectureVideo(
            id = "merthoca_pkob_full_tekrar",
            videoId = "eBhJWde7kKw",
            searchQueryFallback = "Mert Hoca Permütasyon Kombinasyon Binom Olasılık Full Tekrar",
            title = "PKOB Full Tekrar: Permütasyon, Kombinasyon, Binom Açılımı ve Olasılık",
            teacherName = "Mert Hoca",
            channelName = "Mert Hoca",
            teacherBadge = "PKOB & Sayma Uzmanı",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicIds = setOf("permutasyon", "kombinasyon", "binom", "olasilik"),
            topicKeywords = listOf(
                "permütasyon", "permutasyon", "kombinasyon", "binom", "olasılık", "olasilik",
                "pkob", "sayma", "sıralama", "tekrarlı permütasyon", "seçme", "koşullu olasılık",
                "pascal", "faktöriyel", "kaç farklı şekilde"
            ),
            kazanimCodes = setOf("MAT.10.1.1.1", "MAT.10.1.1.2", "MAT.10.1.1.3", "MAT.10.1.2.1", "MAT.11.7.1.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "mert_pkob_permutasyon",
                    chapterTitle = "Sayma Kuralları, Sıralama ve Tekrarlı Permütasyon Kurguları",
                    startSeconds = 8295,
                    endSeconds = 9360,
                    kazanimIds = setOf("KAZ_TYT_MAT_PKBO_VERI"),
                    kazanimCodes = setOf("MAT.10.1.1.1", "MAT.10.1.1.2"),
                    questionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf(
                        "permütasyon", "sıralama", "tekrarlı permütasyon", "yan yana", "en kısa yol",
                        "şifre", "basamaklı sayı", "rakamları farklı", "faktöriyel"
                    ),
                    keyFormulaSummary = "P(n, r) = n! / (n - r)!. Tekrarlı permütasyon: n! / (n₁!·n₂!·...·nₖ!). En kısa yol sorularında sağa (a) ve yukarı (b) adımlar (a+b)! / (a!·b!) ile bulunur.",
                    osymTip = "'Yan yana gelmemek' şartında önce diğer elemanları sırala, sonra aralarına ve uçlarına oluşan boşluklara kısıtlı elemanları yerleştir!"
                ),
                LectureChapterSegment(
                    id = "mert_pkob_kombinasyon",
                    chapterTitle = "Kombinasyon (Seçme), Dağıtım ve Geometrik Şekil Oluşturma",
                    startSeconds = 9370,
                    endSeconds = 10480,
                    kazanimIds = setOf("KAZ_TYT_MAT_PKBO_VERI"),
                    kazanimCodes = setOf("MAT.10.1.1.3", "MAT.10.1.1.1"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf(
                        "kombinasyon", "seçme", "üçgen sayısı", "doğru sayısı", "paralelkenar",
                        "dikdörtgen sayısı", "alt küme", "en az", "en çok", "ayraç yöntemi"
                    ),
                    keyFormulaSummary = "C(n, r) = n! / [r!·(n - r)!]. Doğrusal olmayan n noktadan C(n, 3) üçgen, m paralel ile k paralel doğrudan C(m, 2)·C(k, 2) paralelkenar oluşur.",
                    osymTip = "Geometrik kombinasyon sorularında 'tüm durumlar - doğrusal/aynı doğru üzerindeki noktaların oluşturamadıkları' çıkarma stratejisi en hatasız yoldur."
                ),
                LectureChapterSegment(
                    id = "mert_pkob_binom",
                    chapterTitle = "Binom Açılımı, Sabit Terim ve Katsayı Analizi",
                    startSeconds = 10490,
                    endSeconds = 11385,
                    kazanimIds = setOf("KAZ_TYT_MAT_PKBO_VERI"),
                    kazanimCodes = setOf("MAT.10.1.2.1", "MAT.10.1.1.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf(
                        "binom", "pascal üçgeni", "baştan r+1", "sabit terim", "katsayılar toplamı",
                        "ortanca terim", "x^k katsayısı", "(a+b)^n"
                    ),
                    keyFormulaSummary = "(x + y)ⁿ açılımında baştan (r+1). terim: C(n, r)·xⁿ⁻ʳ·yʳ. Katsayılar toplamı için değişkenlere 1, sabit terim için x üssünü 0 yapan r değeri bulunur.",
                    osymTip = "Kesirli (x + 1/x)ⁿ ifadelerinde sabit terim bulmak için x=0 YAZILMAZ; genel terimdeki x'in toplam üssü sıfıra eşitlenerek r çözülür!"
                ),
                LectureChapterSegment(
                    id = "mert_pkob_olasilik",
                    chapterTitle = "Klasik, Bağımlı-Bağımsız ve Koşullu Olasılık Problemleri",
                    startSeconds = 11393,
                    endSeconds = 12940,
                    kazanimIds = setOf("KAZ_TYT_MAT_PKBO_VERI"),
                    kazanimCodes = setOf("MAT.11.7.1.1", "MAT.10.1.1.1"),
                    questionTypes = setOf(OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf(
                        "olasılık", "koşullu olasılık", "bilindiğine göre", "bağımsız olay",
                        "örnek uzay", "en az biri", "zar", "torba", "seçilen"
                    ),
                    keyFormulaSummary = "P(A) = s(A) / s(E). Koşullu olasılık: P(A|B) = P(A ∩ B) / P(B). 'En az biri' sorusunda P(A) = 1 - P(A') tümleyenini kullan.",
                    osymTip = "Soru kökünde 'olduğu bilindiğine göre' geçiyorsa örnek uzayı daralt; paydaya yalnızca verilen koşulu sağlayan durum sayısını yaz!"
                )
            )
        ),

        // =====================================================================
        // 3. AYT MATEMATİK — PARABOL (Mert Hoca — 1:54:15 / 6855 sn)
        // =====================================================================
        TopicLectureVideo(
            id = "merthoca_ayt_parabol_full",
            videoId = "EHwqWMX8RGg",
            searchQueryFallback = "Mert Hoca AYT Full Tekrar Parabol konu anlatımı",
            title = "AYT Full Tekrar | Parabol (Tepe Noktası, Doğru Durumu ve Alan Modelleme)",
            teacherName = "Mert Hoca",
            channelName = "Mert Hoca",
            teacherBadge = "AYT Grafik & Parabol Ustası",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicIds = setOf("parabol"),
            topicKeywords = listOf(
                "parabol", "tepe noktası", "simetri ekseni", "ikinci dereceden fonksiyon",
                "r = -b/2a", "parabol doğru", "teğet parabol", "parabol alan",
                "en büyük değer", "en küçük değer", "eksenleri kestiği"
            ),
            kazanimCodes = setOf("MAT.11.3.1.1", "MAT.11.3.1.2", "MAT.10.3.1.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "mert_parabol_tepe_simetri",
                    chapterTitle = "Tepe Noktası T(r, k), Simetri Ekseni ve Parabol Denklemi Kurma",
                    startSeconds = 180,
                    endSeconds = 1820,
                    kazanimIds = setOf("KAZ_AYT_MAT_POLINOM_PARABOL"),
                    kazanimCodes = setOf("MAT.11.3.1.1", "MAT.10.3.1.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf(
                        "tepe noktası", "simetri ekseni", "r = -b/2a", "denklem kurma",
                        "x eksenine teğet", "kökler", "kollar yukarı", "kollar aşağı"
                    ),
                    keyFormulaSummary = "T(r, k) için r = -b/(2a) = (x₁ + x₂)/2, k = f(r). Tepe noktası biliniyorsa f(x) = a(x - r)² + k, kökler biliniyorsa f(x) = a(x - x₁)(x - x₂).",
                    osymTip = "Parabolde f(p) = f(q) veriliyorsa simetri ekseni mutlaka r = (p + q) / 2 olur — bu bilgi ÖSYM sorularında denklemi kurmadan cevabı verir!"
                ),
                LectureChapterSegment(
                    id = "mert_parabol_dogru_durum",
                    chapterTitle = "Parabol ile Doğrunun Birbirine Göre Durumu (Ortak Çözüm & Teğet)",
                    startSeconds = 1840,
                    endSeconds = 3620,
                    kazanimIds = setOf("KAZ_AYT_MAT_POLINOM_PARABOL"),
                    kazanimCodes = setOf("MAT.11.3.1.2", "MAT.10.3.1.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf(
                        "parabol doğru", "ortak çözüm", "teğet", "kesişim noktası",
                        "orta nokta", "diskriminant", "iki farklı nokta", "kesişmez"
                    ),
                    keyFormulaSummary = "ax² + bx + c = mx + n ortak denkleminde Δ > 0 iki kesim noktası, Δ = 0 teğetlik, Δ < 0 kesişmeme şartıdır. Kesişim noktalarının orta apsis değeri (x₁ + x₂) / 2'dir.",
                    osymTip = "Parabol ile doğrunun kesişim noktalarının orta noktası soruluyorsa kökleri tek tek bulma; ortak denklemin -B/(2A) değeri doğrudan orta noktanın apsisidir."
                ),
                LectureChapterSegment(
                    id = "mert_parabol_grafik_alan",
                    chapterTitle = "Parabol Grafiğinde Geometrik Alan, Dikdörtgen ve Üçgen Modelleme",
                    startSeconds = 3640,
                    endSeconds = 5320,
                    kazanimIds = setOf("KAZ_AYT_MAT_POLINOM_PARABOL"),
                    kazanimCodes = setOf("MAT.11.3.1.1", "MAT.11.3.1.2"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf(
                        "parabol alan", "dikdörtgen", "kare", "üçgen alanı", "koordinat",
                        "uzunluk", "simetri", "köşeleri parabol üzerinde"
                    ),
                    keyFormulaSummary = "Parabol içine yerleştirilen simetrik dikdörtgen/karede köşe apsislerini simetri eksenine göre r ± t seçmek tüm uzunlukları tek parametreye indirger.",
                    osymTip = "Eksenler ve parabol arasındaki uzunluk/alan sorularında negatif bölgedeki ordinatlar için uzunluğun -f(x) olduğunu unutma!"
                ),
                LectureChapterSegment(
                    id = "mert_parabol_maks_min",
                    chapterTitle = "Parabolde En Büyük-En Küçük Değer ve Yeni Nesil Kurgulu Problemler",
                    startSeconds = 5340,
                    endSeconds = 6780,
                    kazanimIds = setOf("KAZ_AYT_MAT_POLINOM_PARABOL"),
                    kazanimCodes = setOf("MAT.11.3.1.2", "MAT.10.3.1.1"),
                    questionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf(
                        "en büyük değer", "en küçük değer", "maksimum kâr", "kapalı aralık",
                        "köprü", "topun yüksekliği", "yeni nesil parabol", "görüntü kümesi"
                    ),
                    keyFormulaSummary = "Kapalı [m, n] aralığında en büyük/en küçük değer için r ∈ [m, n] kontrolü yapılır: f(m), f(n) ve (aralıktaysa) f(r) değerleri kıyaslanır.",
                    osymTip = "Tepe noktası r verilen [m, n] tanım aralığının dışındaysa f(r) alınmaz; uç noktalar f(m) ve f(n) değerleri minimum/maksimumu belirler!"
                )
            )
        ),

        // =====================================================================
        // 4. AYT MATEMATİK — 2. DERECEDEN EŞİTSİZLİKLER (Mert Hoca — 1:29:28 / 5368 sn)
        // =====================================================================
        TopicLectureVideo(
            id = "merthoca_ayt_esitsizlikler_full",
            videoId = "BfaVoypNTgQ",
            searchQueryFallback = "Mert Hoca AYT Full Tekrar 2. Dereceden Eşitsizlikler konu anlatımı",
            title = "AYT Full Tekrar | 2. Dereceden Eşitsizlikler ve Eşitsizlik Sistemleri",
            teacherName = "Mert Hoca",
            channelName = "Mert Hoca",
            teacherBadge = "İşaret Tablosu & Grafik Uzmanı",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicIds = setOf("ikinci_derece_esitsizlikler"),
            topicKeywords = listOf(
                "eşitsizlik", "esitsizlik", "2. dereceden eşitsizlik", "işaret tablosu",
                "çift katlı kök", "eşitsizlik sistemi", "çözüm kümesi", "daima pozitif",
                "daima negatif", "grafikli eşitsizlik", "f(x).g(x)"
            ),
            kazanimCodes = setOf("MAT.11.4.1.1", "MAT.11.4.1.2"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "mert_esit_isaret_tablosu",
                    chapterTitle = "İşaret Tablosu, Çift Katlı Kök ve Rasyonel Eşitsizlikler",
                    startSeconds = 150,
                    endSeconds = 1860,
                    kazanimIds = setOf("KAZ_AYT_MAT_ESITSIZLIK"),
                    kazanimCodes = setOf("MAT.11.4.1.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf(
                        "işaret tablosu", "çift katlı kök", "paydayı sıfır", "rasyonel eşitsizlik",
                        "mutlak değer", "üstel ifade", "çözüm kümesi", "kaç tam sayı"
                    ),
                    keyFormulaSummary = "Çift üslü çarpanlar (x - a)²ᵏ çift katlı köktür ve tabloda işaret değiştirmez. Paydayı sıfırlayan kökler eşitsizlik '≤ / ≥' olsa dahi çözüm kümesine alınmaz.",
                    osymTip = "Eşitsizlikte sadeleştirme yapma (kök kaybı olur) ve içler-dışlar çarpımı yapma (paydanın işareti bilinmiyorsa yön bozulur); her şeyi tek tarafa topla!"
                ),
                LectureChapterSegment(
                    id = "mert_esit_grafik_yorum",
                    chapterTitle = "Grafikli Eşitsizlikler ve Daima Pozitif / Negatif Olma Şartı",
                    startSeconds = 1880,
                    endSeconds = 3640,
                    kazanimIds = setOf("KAZ_AYT_MAT_ESITSIZLIK"),
                    kazanimCodes = setOf("MAT.11.4.1.1", "MAT.11.4.1.2"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf(
                        "grafikli eşitsizlik", "x eksenine teğet", "daima pozitif", "daima negatif",
                        "her x gerçel sayısı için", "f(x).g(x)", "f(x-2)", "grafik"
                    ),
                    keyFormulaSummary = "Her x ∈ ℝ için ax² + bx + c > 0 ⇔ a > 0 ve Δ < 0. Grafikte x eksenine teğet geçilen apsis çift katlı kök, kesip geçilen apsis tek katlı köktür.",
                    osymTip = "Grafik f(x) verilip f(x - k) soruluyorsa tüm kökleri k birim sağa öteleyerek yeni işaret tablosunu saniyeler içinde kurabilirsin!"
                ),
                LectureChapterSegment(
                    id = "mert_esit_sistemler",
                    chapterTitle = "İkinci Dereceden Eşitsizlik Sistemleri ve Kök Yorumu",
                    startSeconds = 3660,
                    endSeconds = 5300,
                    kazanimIds = setOf("KAZ_AYT_MAT_ESITSIZLIK"),
                    kazanimCodes = setOf("MAT.11.4.1.2", "MAT.11.4.1.1"),
                    questionTypes = setOf(OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf(
                        "eşitsizlik sistemi", "ortak çözüm", "zıt işaretli kök", "aynı işaretli kök",
                        "iki pozitif kök", "iki negatif kök", "kesişim kümesi"
                    ),
                    keyFormulaSummary = "Eşitsizlik sisteminde her eşitsizliğe ayrı satır açılıp ortak taranan aralık alınır. Zıt işaretli kökler için x₁·x₂ < 0 şartı tek başına Δ > 0'ı garantiler.",
                    osymTip = "Sistem sorularında bir satırın kökü diğer satırda işaret değiştirmez; her satırı yalnızca kendi köklerinde işaret değiştirerek tara."
                )
            )
        ),

        // =====================================================================
        // 5. AYT MATEMATİK — 2. DERECEDEN DENKLEMLER & KARMAŞIK SAYILAR
        //    Mert Hoca — 59:56 / 3596 sn
        // =====================================================================
        TopicLectureVideo(
            id = "merthoca_ayt_denklem_karmasik_full",
            videoId = "JQN_lUH_wc0",
            searchQueryFallback = "Mert Hoca AYT Full Tekrar İkinci Dereceden Denklemler ve Karmaşık Sayılar",
            title = "AYT Full Tekrar | 2. Dereceden Denklemler ve Karmaşık Sayılar",
            teacherName = "Mert Hoca",
            channelName = "Mert Hoca",
            teacherBadge = "AYT Cebir & Karmaşık Sayı Uzmanı",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_mat",
            subjectName = "Matematik",
            topicIds = setOf("ikinci_derece_denklemler", "tyt_ikinci_derece_denklemler", "karmasik_sayilar"),
            topicKeywords = listOf(
                "2. dereceden denklem", "ikinci dereceden denklem", "diskriminant", "delta",
                "kökler toplamı", "kökler çarpımı", "kök katsayı", "karmaşık sayı", "karmasik sayi",
                "sanal birim", "i^2 = -1", "eşlenik", "reel kısım", "sanal kısım", "z = a + bi"
            ),
            kazanimCodes = setOf("MAT.10.4.1.1", "MAT.10.4.1.2", "MAT.10.4.1.3", "MAT.10.3.1.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "mert_denklem_diskriminant_kok",
                    chapterTitle = "2. Dereceden Denklem Çözümü, Diskriminant ve Kök-Katsayı İlişkisi",
                    startSeconds = 120,
                    endSeconds = 1240,
                    kazanimIds = setOf("KAZ_AYT_MAT_POLINOM_PARABOL"),
                    kazanimCodes = setOf("MAT.10.4.1.1", "MAT.10.4.1.2", "MAT.10.3.1.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf(
                        "diskriminant", "delta", "kökler toplamı", "kökler çarpımı", "kökler farkı",
                        "çakışık kök", "gerçel kök", "simetrik kök", "kökü denklemi sağlar"
                    ),
                    keyFormulaSummary = "Δ = b² - 4ac. Kök-katsayı bağıntıları: x₁ + x₂ = -b/a, x₁·x₂ = c/a, |x₁ - x₂| = √Δ / |a|. Kökler x₁, x₂ ise denklem x² - Tx + Ç = 0.",
                    osymTip = "ÖSYM sorularında 'denklemin bir kökü x₁'dir' ifadesi geçiyorsa x₁'i denklemde yerine yazıp derece düşürmek işlemi tek adıma indirir!"
                ),
                LectureChapterSegment(
                    id = "mert_karmasik_i_kuvvetleri",
                    chapterTitle = "Karmaşık Sayılar: Sanal Birim i Kuvvetleri ve Reel-Sanal Kısım",
                    startSeconds = 1260,
                    endSeconds = 2400,
                    kazanimIds = setOf("KAZ_AYT_MAT_POLINOM_PARABOL"),
                    kazanimCodes = setOf("MAT.10.4.1.3"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf(
                        "karmaşık sayı", "sanal birim", "i kuvvetleri", "i^2", "re(z)", "im(z)",
                        "reel kısım", "sanal kısım", "karmaşık eşitlik", "(1+i)^2"
                    ),
                    keyFormulaSummary = "i = √(-1), i² = -1, i³ = -i, i⁴ = 1 (4'lü periyot). Ardışık 4 kuvvetin toplamı 0'dır. Pratik özdeşlikler: (1 + i)² = 2i, (1 - i)² = -2i.",
                    osymTip = "z = a + bi sayısının sanal (imajiner) kısmı 'bi' değil, yalnızca reel katsayı olan 'b' sayısıdır — Im(z) = b!"
                ),
                LectureChapterSegment(
                    id = "mert_karmasik_eslenik_kokler",
                    chapterTitle = "Karmaşık Eşlenik, Bölme İşlemi ve Δ < 0 Denklem Kökleri",
                    startSeconds = 2420,
                    endSeconds = 3540,
                    kazanimIds = setOf("KAZ_AYT_MAT_POLINOM_PARABOL"),
                    kazanimCodes = setOf("MAT.10.4.1.3", "MAT.10.4.1.2"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf(
                        "eşlenik", "z̄", "karmaşık kök", "reel katsayılı denklem",
                        "bölme işlemi", "z·z̄", "a^2 + b^2", "delta < 0"
                    ),
                    keyFormulaSummary = "z = a + bi için eşlenik z̄ = a - bi ve z·z̄ = a² + b². Reel katsayılı 2. derece denklemin bir kökü a + bi ise diğer kökü mutlaka eşleniği a - bi'dir.",
                    osymTip = "Reel katsayılı denklemde z₁ = a + bi kökü verilmişse z₁ + z₂ = 2a ve z₁·z₂ = a² + b² doğrudan denklemin katsayılarını verir!"
                )
            )
        ),

        // =====================================================================
        // 6. TYT SOSYAL — FELSEFE (Felsefe Atölyesi / Erdal Aydemir — 2:10:56 / 7856 sn)
        // =====================================================================
        TopicLectureVideo(
            id = "felsefeatolyesi_tyt_felsefe_full",
            videoId = "NeyJBnh9XtI",
            searchQueryFallback = "Felsefe Atölyesi Erdal Aydemir TYT Felsefe Full Tekrar",
            title = "TYT Felsefe Full Tekrar (Bilgi, Varlık, Ahlak, Siyaset ve Paragraf Yorumu)",
            teacherName = "Erdal Aydemir",
            channelName = "Felsefe Atölyesi",
            teacherBadge = "TYT Felsefe Lideri",
            popularityRank = 1,
            examType = ExamType.TYT,
            subjectId = "tyt_felsefe_din",
            subjectName = "Felsefe",
            topicIds = setOf(
                "felsefeyi_tanima", "bilgi_felsefesi", "varlik_felsefesi", "ahlak_felsefesi",
                "siyaset_felsefesi", "sanat_felsefesi", "din_felsefesi", "bilim_felsefesi"
            ),
            topicKeywords = listOf(
                "felsefe", "epistemoloji", "ontoloji", "etik", "estetik", "bilgi felsefesi",
                "varlık felsefesi", "ahlak felsefesi", "siyaset felsefesi", "bilim felsefesi",
                "din felsefesi", "sanat felsefesi", "rasyonalizm", "empirizm", "kant", "platon", "aristoteles"
            ),
            kazanimCodes = setOf("FEL.10.1.1", "FEL.10.2.1", "FEL.10.3.1", "SOS.TYT.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "felat_felsefeye_giris_paragraf",
                    chapterTitle = "Felsefeyi Tanıma, Felsefi Düşüncenin Nitelikleri ve Paragraf/Çıkarım",
                    startSeconds = 120,
                    endSeconds = 1680,
                    kazanimIds = setOf("KAZ_TYT_SOSYAL"),
                    kazanimCodes = setOf("FEL.10.1.1", "SOS.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf(
                        "felsefeyi tanıma", "refleksif", "yığılımlı", "kümülatif", "öznel",
                        "evrensellik", "tutarlılık", "temellendirme", "bu parçadan", "çıkarılabilir"
                    ),
                    keyFormulaSummary = "Felsefi düşünce: Refleksif (kendi üzerine dönen), eleştirel, sorgulayıcı, tutarlı, sistemli, evrensel ve yığılımlıdır (bilim gibi ilerlemez, birikir).",
                    osymTip = "TYT Felsefe paragraf sorularında şıkların çoğu genel olarak doğru görünebilir; yalnızca parçada doğrudan vurgulanan kavrama odaklan!"
                ),
                LectureChapterSegment(
                    id = "felat_bilgi_varlik",
                    chapterTitle = "Bilgi Felsefesi (Epistemoloji) ve Varlık Felsefesi (Ontoloji) Akımları",
                    startSeconds = 1700,
                    endSeconds = 4120,
                    kazanimIds = setOf("KAZ_TYT_SOSYAL"),
                    kazanimCodes = setOf("FEL.10.2.1", "SOS.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf(
                        "bilgi felsefesi", "epistemoloji", "rasyonalizm", "empirizm", "kritisizm",
                        "entüisyonizm", "pragmatizm", "septisizm", "varlık felsefesi", "ontoloji",
                        "idealizm", "materyalizm", "düalizm", "fenomenoloji", "nihilizm"
                    ),
                    keyFormulaSummary = "Bilgi kaynağı: Akıl = Rasyonalizm (Platon, Descartes), Deney = Empirizm (Locke, Hume), Akıl+Deney = Kritisizm (Kant), Fayda = Pragmatizm. Varlık: İdea = İdealizm, Madde = Materyalizm, İkisi = Düalizm.",
                    osymTip = "Parçada 'doğuştan gelen bilgiler (a priori)' geçiyorsa Rasyonalizm, 'boş levha (tabula rasa) ve duyum' geçiyorsa Empirizm, 'hem duyu hem akıl kategorisi' geçiyorsa Kant/Kritisizm!"
                ),
                LectureChapterSegment(
                    id = "felat_ahlak_siyaset",
                    chapterTitle = "Ahlak Felsefesi (Etik) ve Siyaset Felsefesi Temel Problemleri",
                    startSeconds = 4140,
                    endSeconds = 6120,
                    kazanimIds = setOf("KAZ_TYT_SOSYAL"),
                    kazanimCodes = setOf("FEL.10.3.1", "SOS.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf(
                        "ahlak felsefesi", "etik", "ödev ahlakı", "hedonizm", "utilitarizm",
                        "egoizm", "determinizm", "indeterminizm", "otodeterminizm", "siyaset felsefesi",
                        "toplumsal sözleşme", "ütopya", "distopya", "meşruiyet", "egemenlik"
                    ),
                    keyFormulaSummary = "Evrensel ahlakı reddedenler: Hedonizm, Egoizm, Anarşizm, Nihilizm. Kabul edenler: Utilitarizm (fayda), Kant Ödev Ahlakı (koşulsuz buyruk/niyet). Siyasette Hobbes-Locke-Rousseau: Toplumsal Sözleşme.",
                    osymTip = "Kant'ın ödev ahlakında eylemin sonucu/çıkarı değil, yalnızca 'iyi niyet ve ödev duygusu' ile yapılması ahlaki değeri belirler!"
                ),
                LectureChapterSegment(
                    id = "felat_bilim_din_sanat",
                    chapterTitle = "Bilim, Din ve Sanat Felsefesi (Estetik) ÖSYM Kavram Karşılaştırmaları",
                    startSeconds = 6140,
                    endSeconds = 7780,
                    kazanimIds = setOf("KAZ_TYT_SOSYAL"),
                    kazanimCodes = setOf("FEL.10.3.1", "SOS.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf(
                        "bilim felsefesi", "paradigma", "yanlışlanabilirlik", "din felsefesi",
                        "teizm", "deizm", "panteizm", "agnostisizm", "ateizm",
                        "sanat felsefesi", "estetik", "taklit", "yaratma", "oyun"
                    ),
                    keyFormulaSummary = "Bilimde Popper = Yanlışlanabilirlik, Kuhn = Paradigma. Dinde Deizm = yaratır ama müdahale etmez, Panteizm = Tanrı=Evren, Agnostisizm = bilinemezcilik. Sanatta Mimesis = taklit.",
                    osymTip = "Bilim felsefesinde Thomas Kuhn'un 'paradigma değişimi / bilimsel devrim' kavramı ile Karl Popper'ın 'yanlışlama ilkesi' TYT'de en sık yoklanan ikilidir."
                )
            )
        ),

        // =====================================================================
        // 7. TYT SOSYAL — DİN KÜLTÜRÜ & AHLAK BİLGİSİ (Cebrail Hocam — 2:21:46 / 8506 sn)
        // =====================================================================
        TopicLectureVideo(
            id = "cebrailhocam_tyt_din_full",
            videoId = "vX0G6sJM0p8",
            searchQueryFallback = "Cebrail Hocam TYT Din Kültürü Full Tekrar Tüm Konular",
            title = "TYT Din Kültürü Full Tekrar | Tüm Konular, Kavramlar ve Yorumlar",
            teacherName = "Cebrail Hocam",
            channelName = "Cebrail Hocam",
            teacherBadge = "TYT Din Full Tekrar Lideri",
            popularityRank = 1,
            examType = ExamType.TYT,
            subjectId = "tyt_felsefe_din",
            subjectName = "Din Kültürü",
            topicIds = setOf("bilgi_ve_inanc", "din_ve_islam"),
            topicKeywords = listOf(
                "din kültürü", "din", "islam", "inanç", "iman", "ibadet", "ahlak", "kur'an",
                "kuran", "peygamber", "vahiy", "mezhep", "tasavvuf", "tevhit", "ihlas",
                "takva", "sırat-ı müstakim", "cihat", "salih amel", "tevekkül"
            ),
            kazanimCodes = setOf("DKAB.9.1.1", "DKAB.10.1.1", "DKAB.11.1.1", "SOS.TYT.01"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "cebrail_din_bilgi_inanc_ibadet",
                    chapterTitle = "Bilgi Kaynakları, İnanç Esasları ve İslam'da İbadet Bilinci",
                    startSeconds = 180,
                    endSeconds = 2940,
                    kazanimIds = setOf("KAZ_TYT_SOSYAL"),
                    kazanimCodes = setOf("DKAB.9.1.1", "SOS.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf(
                        "bilgi ve inanç", "selim akıl", "sadık haber", "salim duyu", "tevhit",
                        "iman", "tasdik", "ikrar", "tahkiki iman", "taklidi iman",
                        "ibadet", "farz", "vacip", "sünnet", "ihlas", "tevekkül"
                    ),
                    keyFormulaSummary = "İslam'da 3 doğru bilgi kaynağı: Selim Akıl, Sadık Haber (vahiy + mütevatir haber), Salim Duyular. İman türleri: Taklidi (araştırmadan) vs Tahkiki (delile dayalı). İbadetlerde niyet, ihlas ve süreklilik esastır.",
                    osymTip = "ÖSYM 'sadık haber' ile 'rüya/ilham' farkını sıkça sorar: Rüya, keşif ve ilham özneldir, İslam'da herkesi bağlayan genel bilgi kaynağı sayılmaz!"
                ),
                LectureChapterSegment(
                    id = "cebrail_din_kavramlar_ahlak",
                    chapterTitle = "Kur'an'da Temel Kavramlar, Değerler ve İslam Ahlakı (Paragraf Yorumu)",
                    startSeconds = 2960,
                    endSeconds = 5720,
                    kazanimIds = setOf("KAZ_TYT_SOSYAL"),
                    kazanimCodes = setOf("DKAB.10.1.1", "SOS.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.MANTIK_MUHAKEME, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf(
                        "takva", "ihsan", "ihlas", "sırat-ı müstakim", "salih amel", "hidayet",
                        "cihat", "hikmet", "iffet", "şecaat", "adalet", "kul hakkı",
                        "gıybet", "israf", "ayet", "hadis"
                    ),
                    keyFormulaSummary = "4 temel ahlaki erdem: Hikmet (bilgelik), İffet (ölçülülük), Şecaat (cesaret), Adalet (denge). İhsan = Allah'ı görüyormuş gibi ibadet ve iyilik; İhlas = yalnızca Allah rızası gözetmek; Takva = bilinçli sorumluluk.",
                    osymTip = "Ayet/hadis verip 'aşağıdaki kavramlardan hangisiyle ilgilidir?' sorularında: Samimiyet → İhlas, Sorumluluk/sakınma → Takva, Dosdoğru yol → Sırat-ı Müstakim, Görüyormuş gibi kulluk → İhsan!"
                ),
                LectureChapterSegment(
                    id = "cebrail_din_yorumlar_mezhepler",
                    chapterTitle = "İslam Düşüncesinde İtikadi, Fıkhi ve Tasavvufi Yorumlar",
                    startSeconds = 5740,
                    endSeconds = 8420,
                    kazanimIds = setOf("KAZ_TYT_SOSYAL"),
                    kazanimCodes = setOf("DKAB.11.1.1", "SOS.TYT.01"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf(
                        "mezhep", "itikadi", "fıkhi", "tasavvufi", "maturidilik", "eşarilik",
                        "hanefilik", "şafiilik", "malikilik", "hanbelilik", "caferilik",
                        "yesevilik", "mevlevilik", "bektaşilik", "ahilik", "din ve yorum"
                    ),
                    keyFormulaSummary = "İtikadi (inanç) yorumlar: Maturidilik (akla önem), Eşarilik. Fıkhi (amel/ibadet) yorumlar: Hanefilik, Şafiilik, Malikilik, Hanbelilik, Caferilik. Tasavvufi (ahlak): Yesevilik, Mevlevilik, Bektaşilik, Kadirilik, Nakşibendilik.",
                    osymTip = "Din sabittir, vahye dayanır ve evrenseldir; dini yorumlar (mezhepler) ise insan ürünüdür, zamana ve kültüre göre çeşitlenebilir!"
                )
            )
        )
    )
}
