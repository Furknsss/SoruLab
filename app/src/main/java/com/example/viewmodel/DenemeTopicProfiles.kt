package com.example.viewmodel

/** Editorial practice blueprints, informed by 2018–2025 booklet classifications.
 * These quotas describe OUR practice exam, never a prediction of future ÖSYM topics.
 * A group may contain related catalog subtopics; no question can cross group boundaries.
 */
data class DenemeTopicQuota(val label: String, val count: Int, val topicIds: Set<String>)

object DenemeTopicProfiles {
    const val VERSION = "2026.11-v3-osym-order"
    const val NOTICE = "2018–2025 ÖSYM kitapçık sırası ve konu dağılımlarına dayalı SoruLab çalışma planıdır. Konu sayıları gelecek ÖSYM sınavı için garanti değildir. Süreler öneridir."
    private fun q(label: String, count: Int, vararg topics: String) =
        DenemeTopicQuota(label, count, linkedSetOf(*topics))

    private val profiles = mapOf(

        "ayt_tarih_1" to listOf(
            q("Tarih bilimi",1,"ayt_tarih_bilimi"),
            q("İlk çağ ve Türk devletleri",2,"ayt_ilk_cag_uygarliklari","ayt_ilk_turk_devletleri"),
            q("Türk İslam tarihi",1,"ayt_turk_islam"),
            q("Osmanlı tarihi",3,"ayt_osmanli_kultur_medeniyet","ayt_xx_yuzyil_osmanli","ayt_osmanli_siyaset"),
            q("Millî Mücadele ve inkılap",3,"ayt_milli_mucadele","ayt_ataturk_ilkeleri")),
        "ayt_tarih_2" to listOf(
            q("İlk çağ ve Türk devletleri",2,"ayt_ilk_cag_uygarliklari","ayt_ilk_turk_devletleri"),
            q("Türk İslam tarihi",1,"ayt_turk_islam"),
            q("Osmanlı tarihi",3,"ayt_osmanli_kultur_medeniyet","ayt_xx_yuzyil_osmanli","ayt_osmanli_siyaset"),
            q("Millî Mücadele ve inkılap",3,"ayt_milli_mucadele","ayt_ataturk_ilkeleri"),
            q("Çağdaş dünya tarihi",2,"ayt_cagdas_dunya")),
        "ayt_cografya_1" to listOf(
            q("Ekosistem",1,"ayt_ekosistem_ve_biyoceşitlilik"),
            q("Türkiye ekonomisi",1,"ayt_turkiye_ekonomisi"),
            q("Bölgeler ve ülkeler",1,"ayt_kuresel_ortam_bolgeler"),
            q("Çevre ve toplum",1,"ayt_cevre_toplum"),
            q("Nüfus ve şehir",1,"ayt_nufus_sehir"),
            q("Ticaret ve kalkınma",1,"ayt_ticaret_kalkinma")),
        "ayt_cografya_2" to listOf(
            q("Ekosistem",2,"ayt_ekosistem_ve_biyoceşitlilik"),
            q("Türkiye ekonomisi",2,"ayt_turkiye_ekonomisi"),
            q("Bölgeler ve ülkeler",2,"ayt_kuresel_ortam_bolgeler"),
            q("Çevre ve toplum",2,"ayt_cevre_toplum"),
            q("Nüfus ve şehir",1,"ayt_nufus_sehir"),
            q("Ticaret ve kalkınma",2,"ayt_ticaret_kalkinma")),
        "ayt_felsefe_grubu" to listOf(
            q("Felsefe",3,"ayt_felsefe_bilgi","ayt_felsefe_alanlar","ayt_felsefe_tarihi"),
            q("Psikoloji",3,"ayt_psikoloji_giris","ayt_psikoloji_ogrenme","ayt_psikoloji_ruh"),
            q("Sosyoloji",3,"ayt_sosyoloji_giris","ayt_sosyoloji_yapi","ayt_sosyoloji_degisme"),
            q("Mantık",3,"ayt_mantik_giris","ayt_klasik_mantik","ayt_mantik_dil")),
        "ayt_din" to listOf(
            q("İslam ve bilim",1,"ayt_din_bilim"), q("Kur'an ve kavramlar",1,"ayt_din_kuran"),
            q("İnanç ve ibadet",1,"ayt_din_inanc_ibadet"),q("Hz. Muhammed",1,"ayt_din_peygamber"),
            q("Yorumlar ve Anadolu",1,"ayt_din_yorumlar"),q("Güncel meseleler ve dinler",1,"ayt_din_guncel_dinler")),
        "tyt_turkce" to listOf(
            q("Sözcükte anlam",4,"sozcukte_anlam","soz_obeginde_anlam"),
            q("Cümlede anlam",3,"cumlede_anlam"),
            q("Dil bilgisi",2,"ses_bilgisi","sozcukte_yapi","isimler","sifatlar","zamirler","zarflar",
                "edat_baglac_unlem","fiiller","fiilimsiler","fiilde_cati","cumle_turleri","anlatim_bozukluklari"),
            q("Cümlenin ögeleri",1,"cumlenin_ogeleri"),
            q("Yazım",2,"yazim_kurallari"), q("Noktalama",2,"noktalama_isaretleri"),
            q("Paragraf",26,"paragrafta_anlam","paragrafta_yapi","anlatim_bicimleri")),
        "tyt_mat" to listOf(
            q("Temel sayılar ve işlemler",7,"temel_kavramlar","sayi_basamaklari","rasyonel_sayilar",
                "ondalik_sayilar","bolme_bolunebilme","ebob_ekok"),
            q("Denklem ve cebir",6,"birinci_dereceden_denklemler","basit_esitsizlikler","mutlak_deger",
                "uslu_sayilar","koklu_sayilar","carpanlara_ayirma","oran_oranti"),
            q("Mantık, kümeler, fonksiyon",2,"mantik","kumeler","kartezyen_carpim","tyt_fonksiyonlar"),
            q("Veri ve istatistik",1,"veri_istatistik"),
            q("Problemler",12,"sayi_problemleri","kesir_problemleri","yas_problemleri","isci_problemleri",
                "hareket_problemleri","yuzde_kar_zarar_problemleri","karisim_problemleri","grafik_problemleri","rutin_olmayan_problemler"),
            q("Sayma ve olasılık",2,"permutasyon","kombinasyon","binom","olasilik")),
        "tyt_geo" to listOf(
            q("Açılar ve üçgenler",5,"dogruda_acilar","ucgende_acilar","dik_ucgen","ikizkenar_eskenar_ucgen",
                "ucgende_aciortay","ucgende_kenarortay","ucgende_eslik_benzerlik","ucgende_alan","aci_kenar_bagintilari"),
            q("Dörtgen ve çokgenler",3,"cokgenler","dortgenler","yamuk","paralelkenar","eskenar_dortgen_deltoid","dikdortgen_kare"),
            q("Katı cisimler",2,"prizmalar_tyt","piramit_koni_kure_tyt")),
        "tyt_fizik" to listOf(
            q("Madde ve özellikleri",1,"madde_ve_ozellikleri"),
            q("Hareket ve kuvvet",1,"hareket_tyt","kuvvet_newton_tyt"),
            q("Isı, sıcaklık ve genleşme",1,"isi_sicaklik","genlesme"),
            q("Basınç ve kaldırma",1,"basinc","kaldirma_kuvveti"),
            q("Elektrik ve manyetizma",1,"elektrostatik","elektrik_akimi_devreler","manyetizma_tyt"),
            q("Dalgalar",1,"dalgalar_tyt"),
            q("Optik",1,"optik_aydinlanma_aynalar","optik_kirilma_mercekler")),
        "tyt_kimya" to listOf(
            q("Kimya bilimi",1,"kimya_bilimi"), q("Atom ve periyodik sistem",1,"atom_modelleri","periyodik_sistem"),
            q("Türler arası etkileşim",1,"kimyasal_turler_etkilesim"),q("Maddenin halleri",1,"maddenin_halleri"),
            q("Temel kanunlar ve hesaplamalar",1,"kimyanin_temel_kanunlari","mol_kavrami","kimyasal_hesaplamalar"),
            q("Karışımlar",1,"karisimlar"),q("Asit, baz ve tuz",1,"asitler_bazlar_tuzlar")),
        "tyt_biyoloji" to listOf(
            q("Canlılar ve bileşenleri",1,"canlilarin_ortak_ozellikleri","canlilarin_temel_bilesenleri"),
            q("Hücre",1,"hucre_ve_organeller","hucre_zarindan_madde_gecisi"),
            q("Sınıflandırma",1,"canlilarin_siniflandirilmasi"),
            q("Bölünmeler ve üreme",1,"mitoz_ve_eseysiz_ureme","mayoz_ve_eseyli_ureme"),
            q("Kalıtım",1,"kalitim"),q("Ekoloji",1,"ekosistem_ekolojisi","guncel_cevre_sorunlari")),
        "tyt_tarih" to listOf(
            q("İlk Türk devletleri",1,"ilk_orta_cag_turk"),q("Türk İslam tarihi",1,"turklerin_islamiyeti_kabulu"),
            q("Osmanlı",1,"beylikten_devlete_osmanli","dunya_gucu_osmanli"),
            q("Millî Mücadele",1,"kurtulus_savasi_hazirlik","kurtulus_savasi_cepheler"),
            q("Atatürk ilke ve inkılapları",1,"ataturk_ilke_inkilaplari")),
        "tyt_cografya" to listOf(
            q("İklim",1,"atmosfer_ve_iklim"),q("Nüfus",1,"nufus_ve_yerlesme"),q("Doğal afetler",1,"dogal_afetler"),
            q("Yer şekilleri ve harita",1,"ic_ve_dis_kuvvetler","harita_bilgisi","su_toprak_bitki"),
            q("Bölgeler ve ekonomi",1,"ekonomik_faaliyetler")),
        "tyt_felsefe" to listOf(
            q("Felsefeyi tanıma",1,"felsefeyi_tanima"),q("Bilgi felsefesi",2,"bilgi_felsefesi"),
            q("Ahlak",1,"ahlak_felsefesi"),q("Varlık ve diğer alanlar",1,"varlik_felsefesi","bilim_felsefesi",
                "din_felsefesi","siyaset_felsefesi","sanat_felsefesi")),
        "tyt_din" to listOf(q("Bilgi ve inanç",1,"bilgi_ve_inanc"),q("Din, ibadet ve ahlak",4,"din_ve_islam")),
        // Gerçek ÖSYM AYT Matematik kitapçık sırası:
        // Fonksiyon -> Polinom -> Denklem/Parabol/Eşitsizlik -> Logaritma -> Diziler
        // -> Limit (2) -> Türev (4) -> İntegral (4) -> Trigonometri (5, Geometri'den hemen önce!)
        "ayt_mat" to listOf(
            q("Fonksiyonlar",3,"fonksiyonlar"),q("Polinomlar",2,"polinomlar"),
            q("Denklem, parabol ve eşitsizlik",6,"ikinci_derece_denklemler","karmasik_sayilar","parabol","ikinci_derece_esitsizlikler"),
            q("Logaritma",3,"logaritma"),q("Diziler",2,"diziler"),
            q("Limit",2,"limit_ve_sureklilik"),q("Türev",4,"turev_alma_kurallari","turev_uygulamalari"),
            q("İntegral",4,"belirsiz_integral","belirli_integral_alan"),
            q("Trigonometri",5,"trigonometri_ayt_mat")),
        "ayt_geo" to listOf(
            q("Çember ve daire",2,"cemberde_aci","cemberde_uzunluk","dairede_alan"),
            q("Doğru ve nokta analitiği",3,"noktanin_analitigi","dogrunun_analitigi"),
            q("Çember analitiği ve dönüşüm",2,"cemberin_analitigi","donusum_geometrisi"),
            q("Katı cisimler",2,"kati_cisimler_ayt")),
        "ayt_fizik" to listOf(
            q("Hareket, kuvvet ve enerji",2,"vektorler","bagil_hareket","newton_hareket_yasalari","bir_boyutta_sabit_ivmeli","atislar","enerji_ve_hareket"),
            q("Momentum ve denge",1,"itme_momentum","tork_ve_denge","kutle_merkezi_basit_makine"),
            q("Elektrik alan",1,"elektrik_alan_potansiyel"),q("Manyetizma ve indüksiyon",1,"manyetizma_induksiyon"),
            q("Alternatif akım",1,"alternatif_akim_transformator"),q("Çembersel hareket ve çekim",2,"cembersel_hareket","kutle_cekimi_kepler"),
            q("Harmonik hareket",1,"basit_harmonik_hareket"),q("Dalga mekaniği",1,"dalga_mekanigi"),
            q("Atom ve radyoaktivite",1,"atom_fizigi_radyoaktivite"),q("Modern fizik",3,"modern_fizik","modern_fizik_uygulamalari")),
        "ayt_kimya" to listOf(
            q("Modern atom teorisi",1,"modern_atom_teorisi"),q("Gazlar",1,"gazlar"),q("Çözeltiler",2,"sivi_cozeltiler"),
            q("Tepkime enerjisi",1,"kimyasal_enerji"),q("Tepkime hızı",1,"kimyasal_hiz"),
            q("Kimyasal denge",1,"kimyasal_denge"),q("Sulu çözelti dengeleri",2,"asit_baz_dengesi","cozunurluk_dengesi"),
            q("Elektrokimya",1,"kimya_ve_elektrik"),q("Organik kimya",3,"karbon_kimyasina_giris","organik_bilesikler")),
        "ayt_biyoloji" to listOf(
            q("Denetleyici sistemler",2,"sinir_sistemi","endokrin_sistem","duyu_organlari"),
            q("İnsan fizyolojisi",3,"destek_ve_hareket","sindirim_sistemi","dolasim_ve_bagisiklik","solunum_sistemi","uriner_sistem","ureme_sistemi_embriyonik"),
            q("Ekoloji",2,"komunite_ve_populasyon","canlilar_ve_cevre"),q("Genden proteine",2,"genden_proteine"),
            q("Enerji dönüşümleri",2,"canlilarda_enerji_donusumleri"),q("Bitki biyolojisi",2,"bitki_biyolojisi")),
        "ayt_edebiyat" to listOf(
            q("Anlam ve okuma",6,"ayt_anlam_ve_okuma"),
            q("Şiir ve söz sanatları",4,"siir_bilgisi","edebi_sanatlar"),q("İslamiyet öncesi",1,"islamiyet_oncesi_turk_edebiyati"),
            q("Halk edebiyatı",2,"halk_edebiyati"),q("Divan edebiyatı",4,"divan_edebiyati"),
            q("Tanzimat ve Servetifünun",2,"tanzimat_edebiyati","servetifunun_fecriati"),
            q("Millî ve Cumhuriyet edebiyatı",4,"milli_edebiyat","cumhuriyet_siir","cumhuriyet_roman_hikaye"),
            q("Edebî akımlar",1,"edebi_akimlar"))
    )

    fun forSection(section: DenemeSection): List<DenemeTopicQuota> {
        val key = section.profileKey.ifBlank { if (section.subjectId == "tyt_felsefe_din") {
            if ("bilgi_ve_inanc" in section.allowedTopicIds) "tyt_din" else "tyt_felsefe"
        } else section.subjectId }
        return profiles[key].orEmpty().also { quotas ->
            require(quotas.sumOf { it.count } == section.count) { "Missing/invalid topic profile: $key" }
            require(quotas.flatMap { it.topicIds }.let { it.size == it.distinct().size })
        }
    }

    fun sourceFor(section: DenemeSection): String {
        val slug = when {
            section.profileKey.startsWith("ayt_tarih") -> "tarih"
            section.profileKey.startsWith("ayt_cografya") -> "cografya"
            section.subjectId == "ayt_felsefe_grubu" -> "felsefe-grubu"
            section.subjectId == "ayt_din" -> "din-kulturu-ve-ahlak-bilgisi"
            else -> when(section.subjectId) {
            "tyt_felsefe_din" -> if ("bilgi_ve_inanc" in section.allowedTopicIds) "din-kulturu-ve-ahlak-bilgisi" else "felsefe-grubu"
            else -> when(section.subjectId.substringAfter('_')) {
                "mat" -> "matematik"; "geo" -> "geometri"; "edebiyat" -> "turk-dili-ve-edebiyati"
                else -> section.subjectId.substringAfter('_')
            }
        }
        }
        return "https://egitimkocu.org/soru-dagilimlari/yks/" + section.subjectId.substringBefore('_') + "/" + slug
    }
}
