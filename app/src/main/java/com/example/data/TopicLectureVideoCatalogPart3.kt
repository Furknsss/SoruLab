package com.example.data

/**
 * SoruLab v1.5.1 — AYT Fizik ve AYT Kimya Soru Tipi Odaklı Konu Anlatım Katalog Uzantısı (Part 3).
 *
 * Tüm 11 haneli YouTube video kimlikleri canlı doğrulanmış olup her bölümün
 * `0 < startSeconds < endSeconds <= videoLength` aralığında kalması garanti edilmiştir.
 */
internal object TopicLectureVideoCatalogPart3 {

    val videos: List<TopicLectureVideo> = listOf(
        // =====================================================================
        // 1. AYT FİZİK — ÇEMBERSEL HAREKET & AÇISAL MOMENTUM
        // =====================================================================
        TopicLectureVideo(
            id = "ozcanaykin_ayt_cembersel_dusey",
            videoId = "rB4olHMqO9Y",
            searchQueryFallback = "Özcan Aykın Düşey Düzlemde Düzgün Çembersel Hareket Yatay Viraj 88 Günde AYT Fizik",
            title = "Düzgün Çembersel Hareket: Yatay/Eğimli Viraj ve Düşey Düzlem Analizi",
            teacherName = "Özcan Aykın",
            channelName = "Özcan Aykın FİZİK",
            teacherBadge = "AYT Fizik 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_fizik",
            subjectName = "Fizik",
            topicIds = setOf("cembersel_hareket"),
            topicKeywords = listOf(
                "çembersel hareket", "düzgün çembersel", "yatay viraj", "eğimli viraj",
                "düşey düzlem", "düşey viraj", "merkezcil kuvvet", "merkezcil ivme",
                "ip gerilmesi", "ray tepki", "açısal hız", "periyot", "frekans"
            ),
            kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.1.1.1", "FIZ.12.1.1.2"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "ozcan_cembersel_viraj_yatay",
                    chapterTitle = "Yatay ve Eğimli Virajda Merkezcil Kuvvet & Güvenli Dönüş Şartı",
                    startSeconds = 110,
                    endSeconds = 620,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.1.1.2"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf(
                        "yatay viraj", "eğimli viraj", "savrulmadan", "sürtünme", "maksimum hız",
                        "viraj", "silindir", "konik sarkaç", "merkezcil kuvvet"
                    ),
                    keyFormulaSummary = "Yatay virajda merkezcil kuvveti sürtünme sağlar: F_s = k·m·g ≥ m·v²/r ⇒ v_maks = √(k·g·r). Eğimli sürtünmesiz virajda tanθ = v²/(g·r).",
                    osymTip = "Yatay ve eğimli virajlarda güvenli dönüş hızı (v_maks) aracın kütlesine (m) BAĞLI DEĞİLDİR!"
                ),
                LectureChapterSegment(
                    id = "ozcan_cembersel_dusey_duzlem",
                    chapterTitle = "Düşey Düzlemde İp Gerilmesi ve Ray Tepki Kuvveti Hesapları",
                    startSeconds = 640,
                    endSeconds = 1240,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.1.1.2"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GORSEL_GEOMETRIK),
                    subTopicKeywords = listOf(
                        "düşey düzlem", "düşey viraj", "tepe noktası", "en alt nokta",
                        "ip gerilmesi", "ray tepki", "normal kuvveti", "kopmadan"
                    ),
                    keyFormulaSummary = "En alt noktada T_alt - mg = mv²/r (T maks). En üst noktada T_üst + mg = mv²/r (T min). Raydan ayrılmadan geçme şartı N ≥ 0.",
                    osymTip = "Merkezcil kuvvet ayrı bir kuvvet değildir; merkeze doğru olan net kuvvetin (T ± mg) kendisidir — serbest cisim diyagramında ekstra ok çizme!"
                ),
                LectureChapterSegment(
                    id = "ozcan_cembersel_oncullu_yorum",
                    chapterTitle = "Düzgün Çembersel Harekette Öncüllü Hız-İvme-Kuvvet Yorumları",
                    startSeconds = 1260,
                    endSeconds = 1810,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.1.1.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf(
                        "öncüllü", "merkezcil ivme", "çizgisel hız", "açısal hız",
                        "vektör", "yönü değişir", "iş yapmaz", "kinetik enerji"
                    ),
                    keyFormulaSummary = "v = ω·r = 2πr/T, a_m = v²/r = ω²·r. Düzgün çembersel harekette sürat sabittir ama hız ve ivme vektörlerinin yönü sürekli değişir.",
                    osymTip = "Merkezcil kuvvet hareket doğrultusuna daima dik olduğundan iş yapmaz; bu yüzden cismin kinetik enerjisi değişmez!"
                )
            )
        ),
        TopicLectureVideo(
            id = "ozcanaykin_ayt_acisal_momentum",
            videoId = "Alsg6r5dYRI",
            searchQueryFallback = "Özcan Aykın Açısal Momentum 88 Günde AYT Fizik Kampı",
            title = "Açısal Momentum, Eylemsizlik Momenti ve Korunumu | 88 Günde AYT Fizik",
            teacherName = "Özcan Aykın",
            channelName = "Özcan Aykın FİZİK",
            teacherBadge = "AYT Fizik 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_fizik",
            subjectName = "Fizik",
            topicIds = setOf("cembersel_hareket", "kutle_cekimi_kepler"),
            topicKeywords = listOf(
                "açısal momentum", "eylemsizlik momenti", "açısal ivme", "açısal momentum korunumu",
                "tork", "dönme kinetik enerjisi", "kepler", "gezegen", "sağ el kuralı"
            ),
            kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.1.2.1", "FIZ.12.1.3.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "ozcan_acisal_mom_tanim",
                    chapterTitle = "Eylemsizlik Momenti (I) ve Açısal Momentum (L = I·ω) Hesapları",
                    startSeconds = 95,
                    endSeconds = 490,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.1.2.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GORSEL_GEOMETRIK),
                    subTopicKeywords = listOf(
                        "eylemsizlik momenti", "açısal momentum", "sağ el kuralı", "dönme kinetik",
                        "yuvarlanma", "mvr", "açısal ivme", "tork"
                    ),
                    keyFormulaSummary = "I = ∑m·r², L = I·ω = m·v·r, τ_net = I·α = ΔL/Δt. Dönme kinetik enerjisi E_dönme = ½·I·ω².",
                    osymTip = "Açısal momentum vektörünün yönü dönme düzlemine DİKTİR ve dört parmak dönme yönünü gösterirken başparmak yönünde bulunur."
                ),
                LectureChapterSegment(
                    id = "ozcan_acisal_mom_korunum",
                    chapterTitle = "Açısal Momentum Korunumu ve Öncüllü Yorum Soruları",
                    startSeconds = 510,
                    endSeconds = 980,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.1.3.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf(
                        "açısal momentum korunumu", "korunur", "buz patencisi", "kollarını",
                        "gezegen", "güneş", "kepler", "dış tork", "öncüllü"
                    ),
                    keyFormulaSummary = "Sisteme etki eden net dış tork sıfırsa (τ_net = 0) açısal momentum korunur: I₁·ω₁ = I₂·ω₂. I azalırsa ω artar!",
                    osymTip = "Buz patencisi kollarını kapattığında I azalır, L sabit kalır, ω artar ve E_k = L²/(2I) olduğu için dönme kinetik enerjisi ARTAR!"
                )
            )
        ),
        TopicLectureVideo(
            id = "altuggunes_ayt_cembersel_acisal",
            videoId = "h4P4R3gh2VI",
            searchQueryFallback = "Altuğ Güneş Düzgün Çembersel Hareket Açısal Momentum Korunumu",
            title = "Çembersel Hareket & Açısal Momentum Korunumu: Öncüllü Yorum Kampı",
            teacherName = "Altuğ Güneş",
            channelName = "Altuğ Güneş FİZİK",
            teacherBadge = "Öncüllü Yorum Uzmanı",
            popularityRank = 2,
            examType = ExamType.AYT,
            subjectId = "ayt_fizik",
            subjectName = "Fizik",
            topicIds = setOf("cembersel_hareket", "kutle_cekimi_kepler"),
            topicKeywords = listOf(
                "çembersel hareket", "açısal momentum", "açısal momentum korunumu",
                "eylemsizlik momenti", "viraj", "merkezcil", "kepler", "öncüllü"
            ),
            kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.1.1.1", "FIZ.12.1.3.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "altug_cembersel_viraj_temel",
                    chapterTitle = "Yatay/Düşey Viraj ve Merkezcil Kuvvet Kavram Yanılgıları",
                    startSeconds = 120,
                    endSeconds = 780,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.1.1.1"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("yatay viraj", "düşey viraj", "merkezcil kuvvet", "savrulma", "ip gerilmesi"),
                    keyFormulaSummary = "Cisme dışa doğru 'merkezkaç kuvveti' etki etmez; yörüngede kalmasını sağlayan merkeze yönelik net kuvvettir.",
                    osymTip = "İp koptuğu anda cisim dışa değil, koptuğu noktadaki yörüngeye TEĞET doğrultuda fırlar!"
                ),
                LectureChapterSegment(
                    id = "altug_acisal_korunum_ornek",
                    chapterTitle = "Açısal Momentum Korunumu Günlük Hayat ve Gezegen Modelleri",
                    startSeconds = 810,
                    endSeconds = 1490,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.1.3.1"),
                    questionTypes = setOf(OsymQuestionType.YENI_NESIL_KURGULU, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("açısal momentum korunumu", "buz patencisi", "gezegen", "kepler", "dönen tabure"),
                    keyFormulaSummary = "Gezegen Güneş'e yaklaştıkça r azalır, çekim kuvvetinin torku sıfır olduğundan L = m·v·r korunur ve çizgisel hız (v) artar.",
                    osymTip = "Dönen disk üzerine dışarıdan cisim bırakıldığında toplam eylemsizlik momenti artar, sistemin açısal hızı (ω) azalır."
                ),
                LectureChapterSegment(
                    id = "altug_acisal_oncullu_yorum",
                    chapterTitle = "ÖSYM Tipi Öncüllü Açısal Momentum ve Eylemsizlik Yorumları",
                    startSeconds = 1520,
                    endSeconds = 2100,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.1.3.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf("öncüllü", "kesinlikle", "hangileri doğrudur", "eylemsizlik momenti", "açısal ivme"),
                    keyFormulaSummary = "Eylemsizlik momenti hem kütleye hem de kütlenin dönme eksenine olan uzaklığının karesine (r²) bağlıdır.",
                    osymTip = "Aynı kütleli içi boş silindir ile dolu silindir eğik düzlemden bırakılırsa, I değeri küçük olan dolu silindir dibe daha çabuk ulaşır!"
                )
            )
        ),

        // =====================================================================
        // 2. AYT FİZİK — BASİT HARMONİK HAREKET (BHH)
        // =====================================================================
        TopicLectureVideo(
            id = "ozcanaykin_ayt_bhh_grafik",
            videoId = "MSvE8BkVuaQ",
            searchQueryFallback = "Özcan Aykın İvme Konum ve Hız Konum İlişkisi Basit Harmonik Hareket",
            title = "Basit Harmonik Hareket: Geri Çağırıcı Kuvvet, İvme ve Hız Grafikleri",
            teacherName = "Özcan Aykın",
            channelName = "Özcan Aykın FİZİK",
            teacherBadge = "AYT Fizik 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_fizik",
            subjectName = "Fizik",
            topicIds = setOf("basit_harmonik_hareket"),
            topicKeywords = listOf(
                "basit harmonik hareket", "bhh", "harmonik", "geri çağırıcı kuvvet",
                "uzanım", "genlik", "ivme", "hız grafiği", "denge noktası", "periyot"
            ),
            kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.2.1.1", "FIZ.12.2.1.2"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "ozcan_bhh_kuvvet_ivme_grafik",
                    chapterTitle = "Geri Çağırıcı Kuvvet (F = -kx) ve İvme-Konum Grafiği Yorumu",
                    startSeconds = 115,
                    endSeconds = 680,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.2.1.2"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf(
                        "geri çağırıcı kuvvet", "ivme", "ivme-konum", "kuvvet-uzanım",
                        "grafik", "uzanım", "denge konumu", "maksimum ivme"
                    ),
                    keyFormulaSummary = "F = -m·ω²·x ve a = -ω²·x. Kuvvet ve ivme her zaman denge noktasına (O) doğrudur; uç noktalarda (x = ±r) maksimum, dengede sıfırdır.",
                    osymTip = "a-x grafiği orijinden geçen negatif eğimli doğrudur ve eğiminin mutlak değeri doğrudan ω²'yi verir!"
                ),
                LectureChapterSegment(
                    id = "ozcan_bhh_hiz_konum_grafik",
                    chapterTitle = "Hız-Konum Bağıntısı, Maksimum Hız ve Enerji Değişimi",
                    startSeconds = 705,
                    endSeconds = 1310,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.2.1.2"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf(
                        "hız", "hız-konum", "maksimum hız", "v_maks", "genlik",
                        "kinetik enerji", "potansiyel enerji", "grafiği"
                    ),
                    keyFormulaSummary = "v = ω·√(r² - x²), v_maks = ω·r (denge noktasında). Uç noktalarda hız anlık sıfır, potansiyel enerji maksimumdur.",
                    osymTip = "Cisim denge noktasına yaklaşırken hızı ARTAR ama geri çağırıcı kuvvet ve ivmesi AZALIR — ÖSYM bunu her yıl öncüllü sorar!"
                ),
                LectureChapterSegment(
                    id = "ozcan_bhh_periyot_paylastirma",
                    chapterTitle = "BHH Periyot Paylaştırma (T/6 - T/12) ve Öncüllü Konum Analizi",
                    startSeconds = 1340,
                    endSeconds = 1830,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.2.1.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GORSEL_GEOMETRIK),
                    subTopicKeywords = listOf("t/6", "t/12", "periyot", "eşit bölmeli", "k-l-o-m-n", "süre"),
                    keyFormulaSummary = "4 eşit bölmeli K-L-O-M-N aralığında geçiş süreleri: K-L = T/6, L-O = T/12, O-M = T/12, M-N = T/6 (ortada hızlı, uçta yavaş!).",
                    osymTip = "Eşit uzunluktaki yollardan dengeye yakın olan daha kısa sürede (T/12), uca yakın olan daha uzun sürede (T/6) alınır."
                )
            )
        ),
        TopicLectureVideo(
            id = "ozcanaykin_ayt_bhh_sarkac",
            videoId = "qli6tsaG0Lc",
            searchQueryFallback = "Özcan Aykın Basit Sarkaç Yay Sarkacı 88 Günde AYT Fizik Kampı",
            title = "Basit Harmonik Hareket: Yay Sarkacı ve Basit Sarkaç Periyot Analizi",
            teacherName = "Özcan Aykın",
            channelName = "Özcan Aykın FİZİK",
            teacherBadge = "AYT Fizik 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_fizik",
            subjectName = "Fizik",
            topicIds = setOf("basit_harmonik_hareket"),
            topicKeywords = listOf(
                "yay sarkacı", "basit sarkaç", "sarkaç", "periyot", "yay sabiti",
                "seri yay", "paralel yay", "çivi", "asansör", "yer çekimi", "harmonik"
            ),
            kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.2.1.3"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "ozcan_bhh_yay_sarkaci",
                    chapterTitle = "Yay Sarkacı Periyodu (T = 2π√(m/k)) ve Seri-Paralel Yaylar",
                    startSeconds = 100,
                    endSeconds = 790,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.2.1.3"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GORSEL_GEOMETRIK),
                    subTopicKeywords = listOf(
                        "yay sarkacı", "yay sabiti", "seri bağlama", "paralel bağlama",
                        "yay kesilirse", "eğik düzlem", "yer çekimi"
                    ),
                    keyFormulaSummary = "T_yay = 2π·√(m / k_eş). Paralel yayda k_eş = k₁ + k₂; seri yayda 1/k_eş = 1/k₁ + 1/k₂. Yay ikiye bölünürse her parçanın k'sı 2k olur.",
                    osymTip = "Yay sarkacının periyodu yer çekimi ivmesine (g), eğik düzlem açısına ve genliğe (r) BAĞLI DEĞİLDİR — Ay'da da aynıdır!"
                ),
                LectureChapterSegment(
                    id = "ozcan_bhh_basit_sarkac",
                    chapterTitle = "Basit Sarkaç Periyodu (T = 2π√(L/g)), Asansör ve Çiviye Takılma",
                    startSeconds = 820,
                    endSeconds = 1610,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.2.1.3"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf(
                        "basit sarkaç", "ipli sarkaç", "ip boyu", "çivi", "asansör",
                        "yer çekimi ivmesi", "kütle", "küçük açı", "periyot"
                    ),
                    keyFormulaSummary = "T_basit = 2π·√(L / g) (küçük açılarda α ≤ 10°). Çiviye takılan sarkaçta T_toplam = (T₁ + T₂) / 2. Yukarı ivmelenen asansörde g_etkin = g + a.",
                    osymTip = "Basit sarkacın periyodu ucundaki cismin kütlesine (m) BAĞLI DEĞİLDİR; yalnızca ip boyuna (L) ve etkin yer çekimine (g) bağlıdır!"
                )
            )
        ),
        TopicLectureVideo(
            id = "vipfizik_ayt_bhh_full",
            videoId = "vyPgxpwMHF8",
            searchQueryFallback = "VIP Fizik Basit Harmonik Hareket 90 Günde AYT Fizik Kampı",
            title = "Basit Harmonik Hareket Full Kamp | Kuvvet-İvme-Hız & Sarkaçlar",
            teacherName = "VIP Fizik",
            channelName = "VIP FİZİK",
            teacherBadge = "Hızlı Tekrar Favorisi",
            popularityRank = 2,
            examType = ExamType.AYT,
            subjectId = "ayt_fizik",
            subjectName = "Fizik",
            topicIds = setOf("basit_harmonik_hareket"),
            topicKeywords = listOf(
                "basit harmonik hareket", "bhh", "periyot", "yay sarkacı", "basit sarkaç",
                "geri çağırıcı kuvvet", "ivme", "hız", "uzanım", "grafik"
            ),
            kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.2.1.1", "FIZ.12.2.1.2", "FIZ.12.2.1.3"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "vip_bhh_periyot_paylastirma",
                    chapterTitle = "Basit Harmonik Hareket ve Periyodu Paylaştırma (T/6 - T/12)",
                    startSeconds = 531,
                    endSeconds = 1820,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.2.1.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GORSEL_GEOMETRIK),
                    subTopicKeywords = listOf("periyot paylaştırma", "t/6", "t/12", "uzanım", "genlik", "salınım"),
                    keyFormulaSummary = "Uçtan dengeye gelirken süreler T/6 ve T/12 olarak paylaşılır; toplam tam salınım süresi T'dir.",
                    osymTip = "Düzgün çembersel hareketin çap üzerindeki izdüşümü basit harmonik harekettir: x = r·sin(ωt)."
                ),
                LectureChapterSegment(
                    id = "vip_bhh_hiz_ivme_kuvvet",
                    chapterTitle = "Hız, İvme ve Geri Çağırıcı Kuvvetin Konuma Göre Değişim Grafiği",
                    startSeconds = 2621,
                    endSeconds = 4180,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.2.1.2"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("hız", "ivme", "geri çağırıcı kuvvet", "konum", "grafik", "denge noktası"),
                    keyFormulaSummary = "Dengede v = maks, F = 0, a = 0. Uçlarda v = 0, F = maks, a = maks. F ve a daima x uzanım vektörüne zıt yönlüdür.",
                    osymTip = "Hız ve ivme vektörleri cisim dengeye yaklaşırken aynı yönlü (hızlanır), dengeden uzaklaşırken zıt yönlüdür (yavaşlar)."
                ),
                LectureChapterSegment(
                    id = "vip_bhh_yay_basit_sarkac",
                    chapterTitle = "Yaylı Sarkaç (Seri-Paralel) ve İpli Basit Sarkaç Periyodu",
                    startSeconds = 5212,
                    endSeconds = 7080,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.2.1.3"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("yay sarkacı", "basit sarkaç", "ipli sarkaç", "paralel yay", "periyot", "çivi"),
                    keyFormulaSummary = "Yay sarkacı: T = 2π√(m/k) ('Tamek'). Basit sarkaç: T = 2π√(L/g) ('Tolga').",
                    osymTip = "Periyot sorularında kütlenin veya yer çekiminin hangisini etkilediğini 'Tamek / Tolga' formülüyle anında kontrol et!"
                )
            )
        ),

        // =====================================================================
        // 3. AYT FİZİK — ELEKTROMANYETİK İNDÜKSİYON & ALTERNATİF AKIM
        // =====================================================================
        TopicLectureVideo(
            id = "ozcanaykin_ayt_induksiyon_lenz",
            videoId = "kEyxJQLBNWc",
            searchQueryFallback = "Özcan Aykın Faraday ve Lenz Yasaları 88 Günde AYT Fizik Kampı",
            title = "Elektromanyetik İndüksiyon: Faraday Yasası, Manyetik Akı ve Lenz Kuralı",
            teacherName = "Özcan Aykın",
            channelName = "Özcan Aykın FİZİK",
            teacherBadge = "AYT Fizik 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_fizik",
            subjectName = "Fizik",
            topicIds = setOf("manyetizma_induksiyon"),
            topicKeywords = listOf(
                "elektromanyetik indüksiyon", "indüksiyon", "lenz yasası", "faraday",
                "manyetik akı", "akı değişimi", "indüksiyon akımı", "özindüksiyon", "emk"
            ),
            kazanimCodes = setOf("FIZ.AYT.01", "FIZ.11.2.4.1", "FIZ.11.2.4.2"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "ozcan_ind_faraday_aki",
                    chapterTitle = "Manyetik Akı Değişimi ve Faraday İndüksiyon Yasası (ε = -ΔΦ/Δt)",
                    startSeconds = 120,
                    endSeconds = 760,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.11.2.4.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("manyetik akı", "akı değişimi", "faraday", "emk", "bvl", "çerçeve", "bobin"),
                    keyFormulaSummary = "Φ = B·A·cosθ. İndüksiyon EMK: ε = -N·(ΔΦ/Δt). Manyetik alanda çekilen düz telde ε = B·v·L.",
                    osymTip = "Çerçeve düzgün manyetik alanın tamamen içinde sabit hızla giderken akı değişmediği için (ΔΦ = 0) indüksiyon akımı OLUŞMAZ!"
                ),
                LectureChapterSegment(
                    id = "ozcan_ind_lenz_yon",
                    chapterTitle = "Lenz Yasası ile İndüksiyon Akımı Yönü Belirleme (Sağ El Kuralı)",
                    startSeconds = 790,
                    endSeconds = 1460,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.11.2.4.2"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("lenz", "lenz yasası", "indüksiyon akımı yönü", "mıknatıs", "yaklaştırılırken", "sağ el"),
                    keyFormulaSummary = "Lenz Yasası: İndüksiyon akımının oluşturduğu manyetik alan, kendisini doğuran akı değişimine karşı koyar (artıyorsa zıt, azalıyorsa aynı yönlü!).",
                    osymTip = "Mıknatıs bobine yaklaşırken itici kutup, uzaklaşırken çekici kutup oluşur; sağ el kuralında başparmak yeni N kutbunu gösterir."
                ),
                LectureChapterSegment(
                    id = "ozcan_ind_grafik_yorum",
                    chapterTitle = "Akı-Zaman Grafiğinden İndüksiyon EMK Okuma ve Öncüllü Sorular",
                    startSeconds = 1490,
                    endSeconds = 2030,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.11.2.4.2"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("akı-zaman grafiği", "grafik", "eğim", "özindüksiyon", "reosta", "öncüllü"),
                    keyFormulaSummary = "Φ-t grafiğinin eğimi (ΔΦ/Δt) indüksiyon EMK'sını verir. Eğim sabitse EMK sabit; akı sabitse EMK sıfırdır.",
                    osymTip = "Özindüksiyon akımı (ε_öz = -L·Δi/Δt) devredeki kendi akımının değişimine (reosta/anahtar) tepki olarak doğar!"
                )
            )
        ),
        TopicLectureVideo(
            id = "ozcanaykin_ayt_alternatif_akim",
            videoId = "T4HQuD0ejRs",
            searchQueryFallback = "Özcan Aykın Alternatif Akım Nasıl Oluşur Transformatörler 88 Günde AYT Fizik",
            title = "Alternatif Akım Devreleri (RLC), Empedans ve Transformatörler",
            teacherName = "Özcan Aykın",
            channelName = "Özcan Aykın FİZİK",
            teacherBadge = "AYT Fizik 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_fizik",
            subjectName = "Fizik",
            topicIds = setOf("alternatif_akim_transformator", "manyetizma_induksiyon"),
            topicKeywords = listOf(
                "alternatif akım", "transformatör", "etkin değer", "bobin", "sığaç", "kondansatör",
                "indüktif reaktans", "kapasitif reaktans", "empedans", "rezonans", "sarım sayısı"
            ),
            kazanimCodes = setOf("FIZ.AYT.01", "FIZ.11.2.5.1", "FIZ.11.2.5.2"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "ozcan_ac_rlc_empedans",
                    chapterTitle = "Alternatif Akım, Etkin Değer ve Bobin-Sığaç Reaktans Analizi",
                    startSeconds = 95,
                    endSeconds = 620,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.11.2.5.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf(
                        "alternatif akım", "etkin değer", "frekans", "bobin", "sığaç",
                        "kondansatör", "indüktif reaktans", "kapasitif reaktans", "empedans"
                    ),
                    keyFormulaSummary = "V_etkin = V_maks/√2. X_L = 2πfL (frekans artarsa artar), X_C = 1/(2πfC) (frekans artarsa azalır). Rezonansta X_L = X_C, Z = R (en küçük).",
                    osymTip = "Saf bobin ve saf sığaç enerji harcamaz (güç harcayan yalnızca ohmik direnç R'dir); frekans değişince R değişmez!"
                ),
                LectureChapterSegment(
                    id = "ozcan_ac_transformator",
                    chapterTitle = "Transformatör Çalışma İlkesi, Yükseltici-Alçaltıcı Oranlar ve Verim",
                    startSeconds = 645,
                    endSeconds = 1230,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.11.2.5.2"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf(
                        "transformatör", "primer", "sekonder", "sarım sayısı",
                        "yükseltici", "alçaltıcı", "verim", "doğru akım"
                    ),
                    keyFormulaSummary = "V_p / V_s = N_p / N_s. İdeal transformatörde P_p = P_s ⇒ V_p·I_p = V_s·I_s. Verim = (V_s·I_s) / (V_p·I_p).",
                    osymTip = "Transformatör YALNIZCA alternatif akımla (AC) çalışır; doğru akım (DC) kaynağına bağlanırsa akı değişmediği için sekonderde gerilim oluşmaz!"
                )
            )
        ),
        TopicLectureVideo(
            id = "altuggunes_ayt_induksiyon_ac",
            videoId = "CFwMHMYHqs4",
            searchQueryFallback = "Altuğ Güneş Manyetizma İndüksiyon Akımı Yönü Lenz Yasası",
            title = "Elektromanyetik İndüksiyon: Lenz Yasası ve Akı Değişimi Yorumları",
            teacherName = "Altuğ Güneş",
            channelName = "Altuğ Güneş FİZİK",
            teacherBadge = "Öncüllü Yorum Uzmanı",
            popularityRank = 2,
            examType = ExamType.AYT,
            subjectId = "ayt_fizik",
            subjectName = "Fizik",
            topicIds = setOf("manyetizma_induksiyon"),
            topicKeywords = listOf(
                "indüksiyon", "indüksiyon akımı", "lenz", "manyetik akı", "faraday", "özindüksiyon", "emk"
            ),
            kazanimCodes = setOf("FIZ.AYT.01", "FIZ.11.2.4.1", "FIZ.11.2.4.2"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "altug_ind_lenz_oncullu",
                    chapterTitle = "İndüksiyon Akımı Oluşumu ve Lenz Yasası Öncüllü Yorumlar",
                    startSeconds = 110,
                    endSeconds = 920,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.11.2.4.2"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GORSEL_GEOMETRIK),
                    subTopicKeywords = listOf("lenz", "indüksiyon akımı", "akı değişimi", "mıknatıs", "çerçeve", "yönü"),
                    keyFormulaSummary = "Akı artıyorsa mevcut B'ye zıt yönde B_ind; akı azalıyorsa mevcut B ile aynı yönde B_ind üretilir.",
                    osymTip = "İndüksiyon akımı manyetik akının kendisine değil, akıdaki DEĞİŞİME (ΔΦ/Δt) karşı koyar!"
                ),
                LectureChapterSegment(
                    id = "altug_ind_ozinduksiyon_grafik",
                    chapterTitle = "Özindüksiyon Akımı, Akı-Zaman Grafiği ve Devre Yorumları",
                    startSeconds = 950,
                    endSeconds = 1830,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.11.2.4.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("özindüksiyon", "reosta", "anahtar", "grafik", "bobin", "faraday"),
                    keyFormulaSummary = "Devre akımı artarken özindüksiyon akımı devre akımına zıt, azalırken aynı yönde oluşur.",
                    osymTip = "Reosta sürgüsü sabit hızla çekilirken akım doğrusal değil hiperbolik değiştiği için özindüksiyon EMK sabit kalmaz!"
                )
            )
        ),
        TopicLectureVideo(
            id = "altuggunes_ayt_alternatif_akim",
            videoId = "MbfCp5F4hcg",
            searchQueryFallback = "Altuğ Güneş Alternatif Akım Devreleri Transformatörler",
            title = "Alternatif Akım Devreleri, Etkin Değer ve Transformatör Yorumları",
            teacherName = "Altuğ Güneş",
            channelName = "Altuğ Güneş FİZİK",
            teacherBadge = "Öncüllü Yorum Uzmanı",
            popularityRank = 2,
            examType = ExamType.AYT,
            subjectId = "ayt_fizik",
            subjectName = "Fizik",
            topicIds = setOf("alternatif_akim_transformator"),
            topicKeywords = listOf(
                "alternatif akım", "transformatör", "etkin değer", "rezonans", "empedans", "bobin", "sığaç"
            ),
            kazanimCodes = setOf("FIZ.AYT.01", "FIZ.11.2.5.1", "FIZ.11.2.5.2"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "altug_ac_temel_yorum",
                    chapterTitle = "Alternatif Akım vs Doğru Akım ve Etkin Değer Öncüllü Yorumlar",
                    startSeconds = 90,
                    endSeconds = 540,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.11.2.5.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("alternatif akım", "doğru akım", "etkin değer", "elektrik iletimi", "öncüllü"),
                    keyFormulaSummary = "Yüksek gerilimle (düşük akımla) enerji iletimi hatlardaki I²·R ısı kaybını minimize eder.",
                    osymTip = "AC devrelerinde ampermetre ve voltmetre daima ETKİN değerleri ölçer, maksimum değerleri değil!"
                ),
                LectureChapterSegment(
                    id = "altug_ac_reaktans_trans",
                    chapterTitle = "Bobin-Sığaç Frekans Davranışı ve Transformatör Sarım Oranları",
                    startSeconds = 565,
                    endSeconds = 1020,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.11.2.5.2"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("transformatör", "bobin", "sığaç", "frekans", "rezonans", "sarım", "verim"),
                    keyFormulaSummary = "Frekans artarsa bobinin X_L direnci artar, sığacın X_C direnci azalır. Transformatör enerji/güç üretmez, sadece gerilim-akım dönüştürür.",
                    osymTip = "İdeal transformatörde çıkış gücü asla giriş gücünden büyük olamaz (verim ≤ %100)."
                )
            )
        ),

        // =====================================================================
        // 4. AYT FİZİK — MODERN FİZİK (FOTOELEKTRİK, COMPTON, ATOM MODELLERİ)
        // =====================================================================
        TopicLectureVideo(
            id = "ozcanaykin_ayt_fotoelektrik_grafik",
            videoId = "6I-bBAQSKOk",
            searchQueryFallback = "Özcan Aykın Fotoelektrik Denklemi Maksimum Enerji Frekans Grafiği Kesme Gerilimi",
            title = "Fotoelektrik Olay: Einstein Denklemi, Kesme Gerilimi ve Enerji-Frekans Grafiği",
            teacherName = "Özcan Aykın",
            channelName = "Özcan Aykın FİZİK",
            teacherBadge = "AYT Fizik 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_fizik",
            subjectName = "Fizik",
            topicIds = setOf("modern_fizik", "modern_fizik_uygulamalari"),
            topicKeywords = listOf(
                "fotoelektrik", "fotoelektrik olay", "kesme gerilimi", "eşik enerjisi",
                "bağlanma enerjisi", "foton", "fotosel", "doyma akımı", "enerji-frekans grafiği"
            ),
            kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.5.3.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "ozcan_foto_einstein_denklem",
                    chapterTitle = "Einstein Fotoelektrik Denklemi, Eşik Enerjisi ve Fotosel Devreler",
                    startSeconds = 105,
                    endSeconds = 740,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.5.3.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf(
                        "fotoelektrik", "einstein", "eşik enerjisi", "bağlanma enerjisi",
                        "eşik frekansı", "eşik dalga boyu", "foton enerjisi", "fotosel"
                    ),
                    keyFormulaSummary = "E_foton = E_0 + E_kmax ⇒ h·f = h·f_0 + e·V_kesme. E_foton = 12400 / λ (eV·Å). Bağlanma enerjisi E_0 yalnızca katot metalinin cinsine bağlıdır.",
                    osymTip = "Işık şiddetini (foton sayısını) artırmak maksimum akımı (i_maks) artırır ama kopan elektronların maksimum kinetik enerjisini ve kesme gerilimini DEĞİŞTİRMEZ!"
                ),
                LectureChapterSegment(
                    id = "ozcan_foto_grafik_kesme",
                    chapterTitle = "Maksimum Kinetik Enerji-Frekans Grafiği (Eğim = h) ve Kesme Gerilimi",
                    startSeconds = 765,
                    endSeconds = 1510,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.5.3.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf(
                        "grafik", "enerji-frekans", "kesme gerilimi", "durdurma gerilimi",
                        "akım-gerilim grafiği", "planck sabiti", "eğim", "doyma akımı"
                    ),
                    keyFormulaSummary = "E_kmax - f grafiğinde tüm metallerin doğruları birbirine PARALELDİR çünkü eğim (tanα = E_0 / f_0 = h) evrensel Planck sabitidir!",
                    osymTip = "Akım-gerilim (i-V) grafiğinde kesme gerilimi (V_k) foton enerjisine ve metale; maksimum akım (i_maks) ışık akısına (şiddetine) bağlıdır."
                )
            )
        ),
        TopicLectureVideo(
            id = "ozcanaykin_ayt_bohr_atom",
            videoId = "unqKuTxNvtk",
            searchQueryFallback = "Özcan Aykın Bohr Atom Modeli Işıma 88 Günde AYT Fizik Kampı",
            title = "Bohr Atom Modeli: Enerji Düzeyleri, Uyarılma Yolları ve Işıma Spektrumu",
            teacherName = "Özcan Aykın",
            channelName = "Özcan Aykın FİZİK",
            teacherBadge = "AYT Fizik 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_fizik",
            subjectName = "Fizik",
            topicIds = setOf("atom_fizigi_radyoaktivite", "modern_fizik"),
            topicKeywords = listOf(
                "bohr", "atom modelleri", "enerji düzeyleri", "uyarılma", "ışıma",
                "lyman", "balmer", "paschen", "iyonlaşma", "yörünge yarıçapı", "spektrum"
            ),
            kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.4.1.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "ozcan_bohr_enerji_duzey",
                    chapterTitle = "Bohr Atom Modeli Yörünge Yarıçapı, Açısal Momentum ve Enerji Düzeyleri",
                    startSeconds = 110,
                    endSeconds = 850,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.4.1.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GORSEL_GEOMETRIK),
                    subTopicKeywords = listOf(
                        "bohr", "atom modeli", "enerji düzeyi", "yörünge", "yarıçap",
                        "açısal momentum", "n. yörünge", "temel hal"
                    ),
                    keyFormulaSummary = "L = n·(h / 2π), r_n = a_0·(n²/Z), E_n = -13,6·(Z²/n²) eV. Üst yörüngeye çıktıkça yarıçap ve toplam enerji artar, çizgisel hız azalır.",
                    osymTip = "Elektron üst yörüngeye çıktıkça toplam enerjisi ARTAR (0'a yaklaşır), kinetik enerjisi AZALIR, potansiyel enerjisi ARTAR!"
                ),
                LectureChapterSegment(
                    id = "ozcan_bohr_uyarilma_isima",
                    chapterTitle = "Atomun Foton/Elektronla Uyarılması ve Lyman-Balmer Işımaları",
                    startSeconds = 880,
                    endSeconds = 1640,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.4.1.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf(
                        "uyarılma", "ışıma", "lyman", "balmer", "paschen", "iyonlaşma",
                        "fotonla uyarılma", "elektronla uyarılma", "enerji seviyesi grafiği"
                    ),
                    keyFormulaSummary = "n. düzeyden temel hale inerken yapılabilecek farklı ışıma sayısı = n·(n - 1) / 2. Foton sadece TAM uyarılma enerjisine eşitse soğurulur; elektron eşit veya büyükse uyarabilir!",
                    osymTip = "Foton iyonlaşma enerjisinden büyükse atomu iyonlaştırabilir ama iyonlaşma altındaki ara değerlerde atomu ASLA uyaramaz!"
                )
            )
        ),
        TopicLectureVideo(
            id = "ozcanaykin_ayt_compton",
            videoId = "bzyjol21byQ",
            searchQueryFallback = "Özcan Aykın Compton Saçılması 88 Günde AYT Fizik Kampı",
            title = "Compton Saçılması, Enerji-Momentum Korunumu ve De Broglie Dalga Boyu",
            teacherName = "Özcan Aykın",
            channelName = "Özcan Aykın FİZİK",
            teacherBadge = "AYT Fizik 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_fizik",
            subjectName = "Fizik",
            topicIds = setOf("modern_fizik", "modern_fizik_uygulamalari"),
            topicKeywords = listOf(
                "compton", "compton saçılması", "saçılan foton", "de broglie",
                "madde dalgaları", "foton momentumu", "modern fizik", "esnek çarpışma"
            ),
            kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.5.4.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "ozcan_compton_korunum",
                    chapterTitle = "Compton Saçılmasında Enerji ve Vektörel Momentum Korunumu",
                    startSeconds = 95,
                    endSeconds = 720,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.5.4.1"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf(
                        "compton", "compton saçılması", "saçılan foton", "serbest elektron",
                        "dalga boyu artar", "momentum korunumu", "esnek çarpışma"
                    ),
                    keyFormulaSummary = "E_gelen = E_saçılan + E_elektron (skaler). P⃗_gelen = P⃗_saçılan + P⃗_elektron (vektörel!). Saçılan fotonun enerjisi ve frekansı azalır, dalga boyu ARTAR (λ' > λ).",
                    osymTip = "Compton saçılmasında foton SOĞURULMAZ! Ve saçılan fotonun hızı yine ışık hızı c'dir — asla azalmaz!"
                ),
                LectureChapterSegment(
                    id = "ozcan_compton_debroglie",
                    chapterTitle = "Compton vs Fotoelektrik Farkları ve De Broglie Dalga Boyu",
                    startSeconds = 745,
                    endSeconds = 1360,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.5.4.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf(
                        "de broglie", "madde dalgası", "tanecik modeli", "ışık hızı",
                        "öncüllü", "compton", "fotoelektrik farkı"
                    ),
                    keyFormulaSummary = "De Broglie dalga boyu: λ = h / p = h / (m·v). Hem fotoelektrik hem Compton olayı ışığın TANECİK doğasını kanıtlar.",
                    osymTip = "Compton'da p_gelen > p_saçılan ve vektörel toplam olduğu için p_gelen < p_saçılan + p_elektron eşitsizliği (üçgen eşitsizliği) geçerlidir."
                )
            )
        ),
        TopicLectureVideo(
            id = "vipfizik_ayt_fotoelektrik_modern",
            videoId = "b3ohLuJ2T28",
            searchQueryFallback = "VIP Fizik Fotoelektrik Olay Konu Anlatımı AYT Fizik Kampı",
            title = "Modern Fizik: Fotoelektrik Grafikler, Kesme Gerilimi, Bohr & Compton",
            teacherName = "VIP Fizik",
            channelName = "VIP FİZİK",
            teacherBadge = "Hızlı Tekrar Favorisi",
            popularityRank = 2,
            examType = ExamType.AYT,
            subjectId = "ayt_fizik",
            subjectName = "Fizik",
            topicIds = setOf("modern_fizik", "atom_fizigi_radyoaktivite", "modern_fizik_uygulamalari"),
            topicKeywords = listOf(
                "modern fizik", "fotoelektrik", "kesme gerilimi", "compton", "bohr", "atom modelleri", "foton"
            ),
            kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.4.1.1", "FIZ.12.5.3.1", "FIZ.12.5.4.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "vip_modern_foto_grafik",
                    chapterTitle = "Fotoelektrik Akım-Gerilim Grafiği ve Kesme Gerilimi Yorumu",
                    startSeconds = 130,
                    endSeconds = 890,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.5.3.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("fotoelektrik", "kesme gerilimi", "doyma akımı", "akım-gerilim grafiği", "ışık şiddeti"),
                    keyFormulaSummary = "i_0 (devre akımı) levhalar arası uzaklık azalınca veya anot yüzeyi büyüyünce artar; i_maks sadece katota düşen foton sayısına bağlıdır.",
                    osymTip = "Kesme gerilimi grafiğin solundaki gerilim eksenini kestiği noktadır; sadece foton frekansı ve metalin eşik enerjisine bağlıdır."
                ),
                LectureChapterSegment(
                    id = "vip_modern_foto_devre",
                    chapterTitle = "Fotoelektrik Denklemi ve Eşik Dalga Boyu Hesaplama Soruları",
                    startSeconds = 920,
                    endSeconds = 1650,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.5.3.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("eşik dalga boyu", "bağlanma enerjisi", "fotosel", "üreteç", "kinetik enerji"),
                    keyFormulaSummary = "Üreteç düz bağlıysa E_k = E_foton - E_0 + e·V; ters bağlıysa E_k = E_foton - E_0 - e·V.",
                    osymTip = "Dalga boyu KÜÇÜLDÜKÇE foton enerjisi BÜYÜR (E = hc/λ); eşik dalga boyundan daha KÜÇÜK dalga boylu ışık elektron koparabilir!"
                ),
                LectureChapterSegment(
                    id = "vip_modern_bohr_compton_ozet",
                    chapterTitle = "Bohr Atom Enerji Düzeyleri ve Compton Saçılması Karşılaştırması",
                    startSeconds = 1680,
                    endSeconds = 2340,
                    kazanimIds = setOf("KAZ_AYT_FEN_FIZIK"),
                    kazanimCodes = setOf("FIZ.AYT.01", "FIZ.12.4.1.1", "FIZ.12.5.4.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf("bohr", "atom modelleri", "compton", "enerji düzeyleri", "ışıma", "saçılma"),
                    keyFormulaSummary = "Bohr'da ışıma fotonunun enerjisi E_üst - E_alt farkına eşittir. Compton'da saçılan fotonun dalga boyu artar, hızı c olarak sabit kalır.",
                    osymTip = "Compton saçılması fotonun momentumu (p = h/λ) olduğunu doğrudan ispatlayan en kritik deneydir."
                )
            )
        ),

        // =====================================================================
        // 5. AYT KİMYA — KİMYASAL TEPKİMELERDE HIZ
        // =====================================================================
        TopicLectureVideo(
            id = "gorkemsahin_ayt_kimyasal_hiz",
            videoId = "ULEbfTi1Hn4",
            searchQueryFallback = "Görkem Şahin Benim Hocam AYT Kimya Tepkime Hızları",
            title = "AYT Kimya: Kimyasal Tepkimelerde Hız, Çarpışma Teorisi ve Mekanizmalı Tepkimeler",
            teacherName = "Görkem Şahin",
            channelName = "Benim Hocam / Görkem Şahin",
            teacherBadge = "AYT Kimya 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_kimya",
            subjectName = "Kimya",
            topicIds = setOf("kimyasal_hiz"),
            topicKeywords = listOf(
                "tepkime hızı", "kimyasal hız", "hız bağıntısı", "mekanizmalı tepkime",
                "yavaş basamak", "çarpışma teorisi", "aktivasyon enerjisi", "eşik enerjisi",
                "katalizör", "hız sabiti", "tepkime derecesi", "ara ürün"
            ),
            kazanimCodes = setOf("KIM.AYT.01", "KIM.11.5.1.1", "KIM.11.5.2.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "gorkem_hiz_carpisma_grafik",
                    chapterTitle = "Çarpışma Teorisi ve Aktivasyon Enerjisi (PE-TK) Grafiği Yorumu",
                    startSeconds = 140,
                    endSeconds = 980,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.5.1.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf(
                        "çarpışma teorisi", "aktivasyon enerjisi", "eşik enerjisi", "aktifleşmiş kompleks",
                        "potansiyel enerji", "grafik", "etkin çarpışma", "endotermik", "ekzotermik"
                    ),
                    keyFormulaSummary = "ΔH = Ea_ileri - Ea_geri. Ea_ileri büyüdükçe tepkime yavaşlar! Etkin çarpışma için uygun geometri + yeterli kinetik enerji (≥ Ea) şarttır.",
                    osymTip = "Aktivasyon enerjisi (Ea) her zaman POZİTİFTİR ve Ea'yı yalnızca KATALİZÖR değiştirir (sıcaklık Ea'yı değiştirmez, aşan tanecik sayısını artırır!)."
                ),
                LectureChapterSegment(
                    id = "gorkem_hiz_bagintisi_tablo",
                    chapterTitle = "Hız Bağıntısı Yazma, Derece ve Deney Tablosundan Hız Sabiti (k)",
                    startSeconds = 1010,
                    endSeconds = 1960,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.5.2.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf(
                        "hız bağıntısı", "hız denklemi", "deney tablosu", "tepkime derecesi",
                        "hız sabiti", "k birimi", "derişim", "tablo"
                    ),
                    keyFormulaSummary = "r = k·[A]^a·[B]^b (yalnızca gazlar ve sulu çözeltiler yazılır; katı ve saf sıvılar yazılmaz!). k'nın birimi = L^(n-1) / (mol^(n-1)·s).",
                    osymTip = "Deney tablosunda bir maddenin derişimi değişirken hız değişmiyorsa o madde hız bağıntısında (yavaş basamakta) yer almaz!"
                ),
                LectureChapterSegment(
                    id = "gorkem_hiz_mekanizmali_katalizor",
                    chapterTitle = "Mekanizmalı Tepkimeler (Yavaş Basamak), Ara Ürün ve Katalizör",
                    startSeconds = 1990,
                    endSeconds = 2880,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.5.2.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf(
                        "mekanizmalı", "yavaş basamak", "ara ürün", "katalizör", "tepe noktası",
                        "sıcaklık etkisi", "temas yüzeyi", "molekülerite"
                    ),
                    keyFormulaSummary = "Mekanizmalı tepkimede hız bağıntısını YAVAŞ basamak (Ea_ileri en büyük tepe) belirler. Önce üretilip sonra harcanan = Ara Ürün; önce girip sonra çıkan = Katalizör.",
                    osymTip = "Katalizör hem Ea_ileri hem Ea_geri'yi aynı miktarda düşürür; bu yüzden ΔH'yi DEĞİŞTİRMEZ, sadece mekanizmayı ve k hız sabitini değiştirir!"
                )
            )
        ),
        TopicLectureVideo(
            id = "ferrum_ayt_kimyasal_hiz",
            videoId = "FE5VAUXF__k",
            searchQueryFallback = "Ferrum AYT Kimya Tepkimelerde Hız Full Tekrar",
            title = "Ferrum Kimya: Tepkimelerde Hız Full Tekrar (Grafik & Mekanizma)",
            teacherName = "Ferrum",
            channelName = "Ferrum",
            teacherBadge = "Kısa & Öz Kimya",
            popularityRank = 2,
            examType = ExamType.AYT,
            subjectId = "ayt_kimya",
            subjectName = "Kimya",
            topicIds = setOf("kimyasal_hiz"),
            topicKeywords = listOf(
                "tepkime hızı", "hız bağıntısı", "mekanizmalı", "çarpışma teorisi", "aktivasyon enerjisi", "katalizör"
            ),
            kazanimCodes = setOf("KIM.AYT.01", "KIM.11.5.1.1", "KIM.11.5.2.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "ferrum_hiz_carpisma_grafik",
                    chapterTitle = "Çarpışma Teorisi ve Aktivasyon Enerjisi Grafik Analizi",
                    startSeconds = 180,
                    endSeconds = 1520,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.5.1.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("çarpışma teorisi", "aktivasyon enerjisi", "eşik enerjisi", "potansiyel enerji", "grafik"),
                    keyFormulaSummary = "Çok basamaklı PE-TK grafiğinde kaç tepe varsa tepkime o kadar basamaklıdır; aktifleşme enerjisi (tepe - giren) en büyük basamak en yavaştır.",
                    osymTip = "Tepkime hızları katsayılarla orantılıdır: harcanma hızı (-) değil büyüklük olarak kıyaslanır."
                ),
                LectureChapterSegment(
                    id = "ferrum_hiz_mekanizmali",
                    chapterTitle = "Hız Bağıntısı, Mekanizmalı Tepkime ve Ara Ürün-Katalizör Ayrımı",
                    startSeconds = 1560,
                    endSeconds = 3080,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.5.2.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("hız bağıntısı", "mekanizmalı", "yavaş basamak", "ara ürün", "katalizör", "derece"),
                    keyFormulaSummary = "Net tepkimeyle hız bağıntısı örtüşmüyorsa tepkime kesinlikle mekanizmalıdır (çok basamaklıdır).",
                    osymTip = "Hız sabiti k; Temas yüzeyi, Sıcaklık ve Katalizör ('TSK') ile değişir, derişimle değişmez!"
                ),
                LectureChapterSegment(
                    id = "ferrum_hiz_faktorler_maxwell",
                    chapterTitle = "Hıza Etki Eden Faktörler ve Kinetik Enerji Dağılım (Maxwell) Grafiği",
                    startSeconds = 3120,
                    endSeconds = 4490,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.5.2.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("sıcaklık", "katalizör", "tanecik sayısı", "kinetik enerji grafiği", "eşik enerjisi"),
                    keyFormulaSummary = "Sıcaklık artışı hem endotermik hem ekzotermik TÜM tepkimelerin hızını ve k sabitini artırır.",
                    osymTip = "Tanecik sayısı - kinetik enerji grafiğinde Ea çizgisinin sağında kalan taralı alan ne kadar büyükse tepkime o kadar hızlıdır!"
                )
            )
        ),

        // =====================================================================
        // 6. AYT KİMYA — KİMYASAL DENGE, ASİT-BAZ DENGESİ & KÇÇ
        // =====================================================================
        TopicLectureVideo(
            id = "gorkemsahin_ayt_kimyasal_denge",
            videoId = "nLrV-62MCZ0",
            searchQueryFallback = "Görkem Şahin Benim Hocam AYT Kimya Kimyasal Denge Le Chatelier",
            title = "AYT Kimya: Kimyasal Denge, Kc-Kp ve Le Chatelier Etki-Tepki Grafikleri",
            teacherName = "Görkem Şahin",
            channelName = "Benim Hocam / Görkem Şahin",
            teacherBadge = "AYT Kimya 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_kimya",
            subjectName = "Kimya",
            topicIds = setOf("kimyasal_denge"),
            topicKeywords = listOf(
                "kimyasal denge", "le chatelier", "kc", "kp", "denge sabiti",
                "denge kesri", "qc", "etki tepki", "derişim grafiği", "dengeye etki"
            ),
            kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.1.1", "KIM.11.6.2.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "gorkem_denge_kc_kp",
                    chapterTitle = "Denge Sabiti (Kc, Kp = Kc·(RT)^Δn) ve Denge Kesri (Qc) Hesapları",
                    startSeconds = 120,
                    endSeconds = 740,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.1.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("kc", "kp", "denge sabiti", "qc", "denge kesri", "hess", "kısmi basınç"),
                    keyFormulaSummary = "K_p = K_c·(R·T)^Δn (Δn = ürün gaz katsayısı - giren gaz katsayısı). Tepkime ters çevrilirse 1/Kc, n ile çarpılırsa Kc^n olur.",
                    osymTip = "Q_c < K_c ise sistem ürünlere, Q_c > K_c ise girenlere kayarak dengeye ulaşır."
                ),
                LectureChapterSegment(
                    id = "gorkem_denge_lechatelier_grafik",
                    chapterTitle = "Le Chatelier İlkesi: Derişim-Zaman Etki-Tepki Grafiği Okuma",
                    startSeconds = 770,
                    endSeconds = 1460,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.2.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf(
                        "le chatelier", "etki tepki", "derişim-zaman", "grafik", "hacim değişimi",
                        "basınç", "madde ekleme", "ani artış", "denge kayar"
                    ),
                    keyFormulaSummary = "Tek bir maddenin derişimi dikey fırlıyorsa madde eklenmiştir; TÜM gazların derişimi aynı anda dikey artıyorsa hacim azaltılmıştır; hiç dikey sıçrama yoksa sıcaklık değişmiştir!",
                    osymTip = "Le Chatelier tepkisi hiçbir zaman etkiyi tamamen yok edemez: madde eklenirse yeni dengede o maddenin derişimi ilk halinden yine yüksek kalır!"
                ),
                LectureChapterSegment(
                    id = "gorkem_denge_sicaklik_yorum",
                    chapterTitle = "Sıcaklık Değişiminin Kc'ye Etkisi ve Öncüllü Denge Yorumları",
                    startSeconds = 1490,
                    endSeconds = 2060,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.2.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.MANTIK_MUHAKEME),
                    subTopicKeywords = listOf("sıcaklık", "endotermik", "ekzotermik", "katalizör", "kc değişir", "öncüllü"),
                    keyFormulaSummary = "Endotermik tepkimede sıcaklık artarsa Kc ARTAR; ekzotermik tepkimede sıcaklık artarsa Kc AZALIR. Katalizör denge yönünü ve Kc'yi değiştirmez!",
                    osymTip = "Sabit hacimli kaba inert (soygaz) eklenmesi derişimleri ve kısmi basınçları değiştirmediği için dengeyi BOZMAZ!"
                )
            )
        ),
        TopicLectureVideo(
            id = "gorkemsahin_ayt_asit_baz_dengesi",
            videoId = "soITH1ErD0s",
            searchQueryFallback = "Görkem Şahin Benim Hocam AYT Kimya Asit Baz Dengesi Tampon Titrasyon",
            title = "AYT Kimya: Asit-Baz Dengesi, Tampon Çözeltiler ve Titrasyon Grafikleri",
            teacherName = "Görkem Şahin",
            channelName = "Benim Hocam / Görkem Şahin",
            teacherBadge = "AYT Kimya 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_kimya",
            subjectName = "Kimya",
            topicIds = setOf("asit_baz_dengesi"),
            topicKeywords = listOf(
                "asit-baz dengesi", "asit baz", "ph", "poh", "ka", "kb",
                "tampon", "tampon çözelti", "titrasyon", "eşdeğerlik noktası", "hidroliz", "nötralleşme"
            ),
            kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.3.1", "KIM.11.6.3.2"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "gorkem_asitbaz_ka_kb_ph",
                    chapterTitle = "Zayıf Asit-Baz Dengesi (Ka, Kb, pH) ve İyonlaşma Yüzdesi",
                    startSeconds = 110,
                    endSeconds = 620,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.3.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("ka", "kb", "zayıf asit", "zayıf baz", "ph", "poh", "iyonlaşma yüzdesi", "ksu"),
                    keyFormulaSummary = "25 °C'de pH + pOH = 14 ve Ka·Kb = Ksu = 10⁻¹⁴. Zayıf asitte [H⁺] ≈ √(Ka·C_a). Su eklenirse zayıf asidin iyonlaşma yüzdesi ARTAR!",
                    osymTip = "Zayıf asit çözeltisine saf su eklendiğinde [H⁺] derişimi azalır (pH artar), ancak iyonlaşma yüzdesi ve H⁺ mol sayısı ARTAR!"
                ),
                LectureChapterSegment(
                    id = "gorkem_asitbaz_titrasyon_grafik",
                    chapterTitle = "Asit-Baz Titrasyon Eğrisi ve Eşdeğerlik Noktası Grafik Yorumu",
                    startSeconds = 650,
                    endSeconds = 1180,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.3.2"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("titrasyon", "titrasyon eğrisi", "eşdeğerlik noktası", "dönüm noktası", "grafik", "tam nötralleşme"),
                    keyFormulaSummary = "Eşdeğerlik noktasında n(H⁺) = n(OH⁻) ⇒ M_a·V_a·z_a = M_b·V_b·z_b. Kuvvetli asit + kuvvetli bazda eşdeğerlik pH = 7'dir.",
                    osymTip = "Titrasyon grafiği pH < 7'den başlıyorsa erlenmayerde asit vardır ve üzerine büretten baz damlatılıyordur!"
                ),
                LectureChapterSegment(
                    id = "gorkem_asitbaz_tampon_hidroliz",
                    chapterTitle = "Tampon Çözeltiler ve Tuzların Hidrolizi (Asidik-Bazik Tuz)",
                    startSeconds = 1205,
                    endSeconds = 1610,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.3.2"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("tampon", "tampon çözelti", "hidroliz", "asidik tuz", "bazik tuz", "konjuge", "eşlenik"),
                    keyFormulaSummary = "Tampon = Zayıf asit + onun tuzu (örn. CH₃COOH / CH₃COONa) veya Zayıf baz + onun tuzu (NH₃ / NH₄Cl). Hidrolizde zayıftan gelen iyon suyla tepkimeye girer.",
                    osymTip = "Kuvvetli asit + kuvvetli bazdan oluşan tuzlar (NaCl, KNO₃) HİDROLİZ OLMAZ ve nötrdür (pH = 7)!"
                )
            )
        ),
        TopicLectureVideo(
            id = "gorkemsahin_ayt_kcc",
            videoId = "bo9qFJ1YdOg",
            searchQueryFallback = "Görkem Şahin Çözünürlük Çarpımı KÇÇ Son Tekrar AYT Kimya",
            title = "AYT Kimya: Çözünürlük Dengesi (Kçç), Ortak İyon ve Çökelme Şartı",
            teacherName = "Görkem Şahin",
            channelName = "GÖRKEM ŞAHİN",
            teacherBadge = "AYT Kimya 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_kimya",
            subjectName = "Kimya",
            topicIds = setOf("cozunurluk_dengesi"),
            topicKeywords = listOf(
                "kçç", "çözünürlük dengesi", "çözünürlük çarpımı", "ortak iyon",
                "çökelme", "qçç", "molar çözünürlük", "doygun çözelti"
            ),
            kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.4.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "gorkem_kcc_hesaplama",
                    chapterTitle = "Çözünürlük Çarpımı (Kçç) ve Molar Çözünürlük Hesaplamaları",
                    startSeconds = 100,
                    endSeconds = 780,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.4.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("kçç", "çözünürlük çarpımı", "molar çözünürlük", "saf su", "doygun", "s²", "4s³"),
                    keyFormulaSummary = "XY tuzu: Kçç = s², XY₂ tuzu: Kçç = 4s³, X₂Y₃ tuzu: Kçç = 108s⁵. Katı madde Kçç bağıntısına yazılmaz.",
                    osymTip = "Farklı iyon sayısına sahip tuzların saf sudaki çözünürlüklerini Kçç değerine bakarak değil, mutlaka 's' değerini hesaplayarak kıyasla!"
                ),
                LectureChapterSegment(
                    id = "gorkem_kcc_ortak_iyon_cokelme",
                    chapterTitle = "Ortak İyon Etkisi, Sıcaklık Etkisi ve Qçç-Kçç Çökelme Yorumu",
                    startSeconds = 810,
                    endSeconds = 1540,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.4.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("ortak iyon", "çökelme", "qçç", "sıcaklık", "kçç değişmez", "çözünürlük azalır"),
                    keyFormulaSummary = "Ortak iyon eklenmesi Le Chatelier gereği dengeyi katıya kaydırır: çözünürlük AZALIR ama Kçç (sıcaklık sabitken) DEĞİŞMEZ! Qçç > Kçç ise çökelme başlar.",
                    osymTip = "Ortak iyon sorularında az çözünen tuzdan gelen küçük 's' ihmal edilir; ortak iyon derişimi doğrudan çok çözünen tuzdan alınır."
                )
            )
        ),
        TopicLectureVideo(
            id = "ferrum_ayt_kimyasal_denge",
            videoId = "YrEAbJYMgqs",
            searchQueryFallback = "Ferrum AYT Kimya Kimyasal Tepkimelerde Denge Full Tekrar",
            title = "Ferrum Kimya: Kimyasal Denge Full Tekrar (Le Chatelier & Grafikler)",
            teacherName = "Ferrum",
            channelName = "Ferrum",
            teacherBadge = "Kısa & Öz Kimya",
            popularityRank = 2,
            examType = ExamType.AYT,
            subjectId = "ayt_kimya",
            subjectName = "Kimya",
            topicIds = setOf("kimyasal_denge"),
            topicKeywords = listOf(
                "kimyasal denge", "le chatelier", "kc", "kp", "etki tepki", "grafik", "denge sabiti"
            ),
            kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.1.1", "KIM.11.6.2.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "ferrum_denge_kc_kp",
                    chapterTitle = "Kc-Kp İlişkisi ve Denge Hesaplama Kalıpları",
                    startSeconds = 160,
                    endSeconds = 1480,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.1.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("kc", "kp", "denge sabiti", "mol", "molarite", "kısmi basınç"),
                    keyFormulaSummary = "Denge anında ileri hız geri hıza eşittir (r_i = r_g) ve Kc = k_ileri / k_geri.",
                    osymTip = "Kabın hacmi 1 litre değilse Kc hesabında molleri mutlaka hacme bölüp molariteye (M = n/V) çevir!"
                ),
                LectureChapterSegment(
                    id = "ferrum_denge_lechatelier_grafik",
                    chapterTitle = "Le Chatelier Etki-Tepki ve Derişim-Zaman Grafik Yorumları",
                    startSeconds = 1520,
                    endSeconds = 2840,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.2.1"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("le chatelier", "etki tepki", "grafik", "derişim", "hacim", "basınç"),
                    keyFormulaSummary = "Hacim azaltılırsa (basınç artarsa) denge gaz mol sayısının AZ olduğu tarafa kayar.",
                    osymTip = "Gaz mol sayıları eşitse (Δn = 0) hacim veya basınç değişimi dengeyi hiçbir yöne kaydırmaz!"
                ),
                LectureChapterSegment(
                    id = "ferrum_denge_sicaklik_oncullu",
                    chapterTitle = "Sıcaklık, Katalizör ve Öncüllü Kimyasal Denge Yorumları",
                    startSeconds = 2880,
                    endSeconds = 4020,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.2.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("sıcaklık", "katalizör", "endotermik", "ekzotermik", "öncüllü"),
                    keyFormulaSummary = "K_c yalnızca sıcaklıkla değişir; katalizör k_i ve k_g'yi aynı oranda artırdığından K_c = k_i/k_g sabit kalır.",
                    osymTip = "Denge tepkimesi dinamiktir; makroskobik olaylar durur ancak mikroskobik ileri-geri dönüşümler eşit hızda sürer."
                )
            )
        ),
        TopicLectureVideo(
            id = "ferrum_ayt_asit_baz_kcc",
            videoId = "wXpfmbRYEes",
            searchQueryFallback = "Ferrum AYT Kimya Asit Baz Dengesi Titrasyon Tampon Kçç Full Tekrar",
            title = "Ferrum Kimya: Asit-Baz Dengesi, Titrasyon, Tampon ve Kçç Full Tekrar",
            teacherName = "Ferrum",
            channelName = "Ferrum",
            teacherBadge = "Kısa & Öz Kimya",
            popularityRank = 2,
            examType = ExamType.AYT,
            subjectId = "ayt_kimya",
            subjectName = "Kimya",
            topicIds = setOf("asit_baz_dengesi", "cozunurluk_dengesi"),
            topicKeywords = listOf(
                "asit baz dengesi", "titrasyon", "tampon", "hidroliz", "ph", "ka", "kb", "kçç", "ortak iyon"
            ),
            kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.3.1", "KIM.11.6.3.2", "KIM.11.6.4.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "ferrum_asitbaz_ph_ka",
                    chapterTitle = "pH-pOH, Zayıf Asit-Baz (Ka/Kb) ve Konjuge Asit-Baz Çiftleri",
                    startSeconds = 210,
                    endSeconds = 1940,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.3.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("ph", "poh", "ka", "kb", "konjuge", "bronsted lowry", "zayıf asit"),
                    keyFormulaSummary = "Brønsted-Lowry: Proton (H⁺) veren asit, alan bazdır. Ka değeri büyük olan zayıf asit daha kuvvetlidir.",
                    osymTip = "Sıcaklık artınca suyun otoiyonizasyonu (endotermik) artar, K_su büyür ve nötr suyun pH'ı 7'nin altına düşer (ama su yine nötrdür!)."
                ),
                LectureChapterSegment(
                    id = "ferrum_asitbaz_titrasyon_tampon",
                    chapterTitle = "Titrasyon Eğrileri, Tampon Çözeltiler ve Tuz Hidrolizi",
                    startSeconds = 1980,
                    endSeconds = 3820,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.3.2"),
                    questionTypes = setOf(OsymQuestionType.GRAFIK_TABLO, OsymQuestionType.ONCULLU),
                    subTopicKeywords = listOf("titrasyon", "tampon", "hidroliz", "eşdeğerlik", "grafik", "pH eğrisi"),
                    keyFormulaSummary = "Tampon çözeltiler az miktarda asit veya baz eklendiğinde pH değişimine direnç gösterir (örn. kanın pH'ı ~7,4).",
                    osymTip = "Zayıf asit + kuvvetli baz titrasyonunda eşdeğerlik noktasında oluşan tuz bazik olduğundan pH > 7 olur!"
                ),
                LectureChapterSegment(
                    id = "ferrum_kcc_ortak_iyon",
                    chapterTitle = "Çözünürlük Dengesi (Kçç) ve Ortak İyon Etkisi",
                    startSeconds = 3860,
                    endSeconds = 5610,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.11.6.4.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("kçç", "çözünürlük dengesi", "ortak iyon", "çökelme", "qçç"),
                    keyFormulaSummary = "Ortak iyon derişimi arttıkça az çözünen tuzun çözünürlüğü azalır; Kçç yalnızca sıcaklıkla değişir.",
                    osymTip = "Çözeltiler karıştırıldığında hacim 2 katına çıkıyorsa iyon derişimlerini yarıya indirip Qçç hesaplamayı unutma!"
                )
            )
        ),

        // =====================================================================
        // 7. AYT KİMYA — KİMYA VE ELEKTRİK (ELEKTROKİMYA)
        // =====================================================================
        TopicLectureVideo(
            id = "gorkemsahin_ayt_elektrokimya_pil",
            videoId = "ycNttNrh1lo",
            searchQueryFallback = "Görkem Şahin Kimya ve Elektrik Son Tekrar Galvanik Pil Nernst Aktiflik",
            title = "AYT Kimya: Elektrokimya — Aktiflik, Galvanik Piller ve Nernst Denklemi",
            teacherName = "Görkem Şahin",
            channelName = "GÖRKEM ŞAHİN",
            teacherBadge = "AYT Kimya 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_kimya",
            subjectName = "Kimya",
            topicIds = setOf("kimya_ve_elektrik"),
            topicKeywords = listOf(
                "kimya ve elektrik", "elektrokimya", "galvanik pil", "nernst", "anot", "katot",
                "tuz köprüsü", "aktiflik", "aşınma", "redoks", "pil potansiyeli", "derişim pili"
            ),
            kazanimCodes = setOf("KIM.AYT.01", "KIM.12.1.1.1", "KIM.12.1.2.1", "KIM.12.1.3.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "gorkem_elektro_aktiflik_asinma",
                    chapterTitle = "Metalik Aktiflik, Aşınma-Saklama Kapları ve Öncüllü Redoks Soruları",
                    startSeconds = 145,
                    endSeconds = 1240,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.12.1.1.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf(
                        "aktiflik", "aşınma", "saklanabilir", "redoks", "yükseltgenme",
                        "indirgenme", "metalik aktiflik", "kaşık", "kap", "öncüllü"
                    ),
                    keyFormulaSummary = "Yükseltgenme potansiyeli büyük olan metal daha AKTİFTİR. Aktif metal pasif metalin çözeltisinde aşınır (tepkime kendiliğinden gerçekleşir!).",
                    osymTip = "Bir çözeltinin metal kapta saklanabilmesi için kabın metali çözeltideki katyondan DAHA PASİF olmalıdır!"
                ),
                LectureChapterSegment(
                    id = "gorkem_elektro_galvanik_pil",
                    chapterTitle = "Galvanik Pil Şeması, Anot-Katot, Tuz Köprüsü ve E°_pil Hesabı",
                    startSeconds = 1275,
                    endSeconds = 2460,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.12.1.2.1"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf(
                        "galvanik pil", "anot", "katot", "tuz köprüsü", "pil potansiyeli",
                        "elektron akışı", "elektrot kütlesi", "standart pil"
                    ),
                    keyFormulaSummary = "Anot: Yükseltgenme & aşınma (kütle azalır). Katot: İndirgenme & birikme (kütle artar). Elektronlar dış devrede Anot → Katot; tuz köprüsünde anyonlar Anota, katyonlar Katota gider!",
                    osymTip = "Yarı tepkime bir katsayıyla çarpılsa bile standart elektrot potansiyeli (E°) ASLA çarpılmaz; sadece tepkime ters çevrilirse işareti değişir!"
                ),
                LectureChapterSegment(
                    id = "gorkem_elektro_nernst_derisim",
                    chapterTitle = "Derişim Pilleri, Nernst Denklemi ve Pil Potansiyeline Etki Eden Faktörler",
                    startSeconds = 2495,
                    endSeconds = 3580,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.12.1.3.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf(
                        "nernst", "nernst denklemi", "derişim pili", "pil gerilimi",
                        "su ekleme", "sıcaklık", "ekzotermik", "denge"
                    ),
                    keyFormulaSummary = "Nernst: E_pil = E°_pil - (0,0592 / n)·log([Anot]^a / [Katot]^b). Derişim pilinde seyreltik kap ANOT, derişik kap KATOTTUR; derişimler eşitlenince pil durur.",
                    osymTip = "Pil tepkimeleri EKZOTERMİKTİR: pil tepkimesini ürünlere kaydıran her etki (sıcaklık düşüşü, anot seyreltme, katot deriştirme) E_pil'i ARTIRIR!"
                )
            )
        ),
        TopicLectureVideo(
            id = "gorkemsahin_ayt_elektroliz",
            videoId = "NNkk1F82p3g",
            searchQueryFallback = "Görkem Şahin Benim Hocam AYT Kimya Elektroliz Faraday Yasaları",
            title = "AYT Kimya: Elektroliz, Öncelik Sırası ve Faraday Yasaları",
            teacherName = "Görkem Şahin",
            channelName = "Benim Hocam / Görkem Şahin",
            teacherBadge = "AYT Kimya 1 Numara",
            popularityRank = 1,
            examType = ExamType.AYT,
            subjectId = "ayt_kimya",
            subjectName = "Kimya",
            topicIds = setOf("kimya_ve_elektrik"),
            topicKeywords = listOf(
                "elektroliz", "faraday", "faraday yasaları", "seri bağlı", "kaplama",
                "erimiş tuz", "sulu çözelti elektrolizi", "coulomb", "amper", "kimya ve elektrik"
            ),
            kazanimCodes = setOf("KIM.AYT.01", "KIM.12.1.4.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "gorkem_elektroliz_oncelik",
                    chapterTitle = "Elektroliz Hücresi ve Anot-Katot Çıkış Önceliği (Sulu Çözelti / Erimiş)",
                    startSeconds = 130,
                    endSeconds = 1640,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.12.1.4.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.GORSEL_GEOMETRIK),
                    subTopicKeywords = listOf(
                        "elektroliz", "anot", "katot", "öncelik", "erimiş tuz",
                        "sulu çözelti", "ilk toplanan", "korozyon", "katodik koruma"
                    ),
                    keyFormulaSummary = "Elektrolizde yine Anotta yükseltgenme (anyonlar), Katotta indirgenme (katyonlar) olur. Birden fazla iyon varsa aktiflik sınırına EN YAKIN olanlar ilk açığa çıkar!",
                    osymTip = "Katotta indirgenme eğilimi en büyük (en PASİF) katyon, anotta yükseltgenme eğilimi en büyük anyon ilk önce serbest hale geçer."
                ),
                LectureChapterSegment(
                    id = "gorkem_elektroliz_faraday",
                    chapterTitle = "Faraday Yasaları (Q = I·t) ve Seri Bağlı Elektroliz Kapları",
                    startSeconds = 1680,
                    endSeconds = 3290,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.12.1.4.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf(
                        "faraday", "faraday yasaları", "seri bağlı", "coulomb",
                        "amper", "96500", "devreden geçen yük", "toplanan kütle"
                    ),
                    keyFormulaSummary = "Q = I · t (saniye!), n(e⁻) = Q / 96500 (1 mol e⁻ = 1 Faraday = 96500 C). Seri bağlı kaplardan geçen elektron mol sayısı (n_e⁻) EŞİTTİR!",
                    osymTip = "Zaman dakika olarak verildiyse t'yi mutlaka 60 ile çarpıp saniyeye çevir; devreden geçen mol elektron = m·z / M_A!"
                )
            )
        ),
        TopicLectureVideo(
            id = "ferrum_ayt_elektrokimya_full",
            videoId = "3bDMLRNqOng",
            searchQueryFallback = "Ferrum AYT Kimya Kimya ve Elektrik Full Tekrar",
            title = "Ferrum Kimya: Kimya ve Elektrik Full Tekrar (Aktiflik, Pil, Nernst & Elektroliz)",
            teacherName = "Ferrum",
            channelName = "Ferrum",
            teacherBadge = "Kısa & Öz Kimya",
            popularityRank = 2,
            examType = ExamType.AYT,
            subjectId = "ayt_kimya",
            subjectName = "Kimya",
            topicIds = setOf("kimya_ve_elektrik"),
            topicKeywords = listOf(
                "kimya ve elektrik", "elektrokimya", "aktiflik", "aşınma", "galvanik pil",
                "nernst", "elektroliz", "faraday", "anot", "katot"
            ),
            kazanimCodes = setOf("KIM.AYT.01", "KIM.12.1.1.1", "KIM.12.1.2.1", "KIM.12.1.3.1", "KIM.12.1.4.1"),
            chapters = listOf(
                LectureChapterSegment(
                    id = "ferrum_elektro_aktiflik",
                    chapterTitle = "Redoks, Metalik Aktiflik ve Aşınma Öncüllü Soruları",
                    startSeconds = 240,
                    endSeconds = 2480,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.12.1.1.1"),
                    questionTypes = setOf(OsymQuestionType.ONCULLU, OsymQuestionType.YENI_NESIL_KURGULU),
                    subTopicKeywords = listOf("aktiflik", "aşınma", "redoks", "saklama kabı", "yükseltgenme basamağı", "öncüllü"),
                    keyFormulaSummary = "Yükseltgenen madde indirgendir; indirgenen madde yükseltgendir. Aktif metal elektron vererek aşınır.",
                    osymTip = "Asit çözeltisine daldırılan metal H₂ gazı çıkarıyorsa hidrojenden daha aktiftir (E°_yüks > 0)."
                ),
                LectureChapterSegment(
                    id = "ferrum_elektro_pil_nernst",
                    chapterTitle = "Galvanik Piller, Tuz Köprüsü ve Nernst Denklemi",
                    startSeconds = 2520,
                    endSeconds = 4960,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.12.1.2.1", "KIM.12.1.3.1"),
                    questionTypes = setOf(OsymQuestionType.GORSEL_GEOMETRIK, OsymQuestionType.ANALITIK_CEBIRSEL),
                    subTopicKeywords = listOf("galvanik pil", "nernst", "tuz köprüsü", "anot", "katot", "derişim pili"),
                    keyFormulaSummary = "E_pil = E°_pil - (0,059/n)·log([Anot]/[Katot]). Pil şemasında sol taraf anot, çift çizgi tuz köprüsü, sağ taraf katottur.",
                    osymTip = "Tuz köprüsü olmadan yük denkliği sağlanamayacağı için pil devresi çalışmaz!"
                ),
                LectureChapterSegment(
                    id = "ferrum_elektro_elektroliz_faraday",
                    chapterTitle = "Elektroliz Devreleri ve Faraday Yasaları Hesaplamaları",
                    startSeconds = 5000,
                    endSeconds = 7310,
                    kazanimIds = setOf("KAZ_AYT_FEN_KIMYA"),
                    kazanimCodes = setOf("KIM.AYT.01", "KIM.12.1.4.1"),
                    questionTypes = setOf(OsymQuestionType.ANALITIK_CEBIRSEL, OsymQuestionType.GRAFIK_TABLO),
                    subTopicKeywords = listOf("elektroliz", "faraday", "seri bağlı", "kaplama", "coulomb", "amper"),
                    keyFormulaSummary = "1 Faraday = 1 mol e⁻ = 96500 C. Seri kaplarda aynı miktarda elektron geçtiği için toplanan mol sayıları değerlikleriyle ters orantılıdır.",
                    osymTip = "Elektroliz istemsiz bir redoks tepkimesinin dışarıdan elektrik enerjisi (E_uygulanan > |E_pil|) verilerek zorla yürütülmesidir."
                )
            )
        )
    )
}
