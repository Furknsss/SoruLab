# SoruLab Deneme Blueprint Rationale — 14 Kasım 2026

## Resmî olan ve olmayan

**Resmî soru sayıları**, ÖSYM'nin yayımladığı YKS soru kitapçıklarında görülen test ve ders sınırlarıdır. **Bir konudan gelecek kesin soru sayısı diye resmî bir garanti yoktur**. Konular arası dağılım geçmiş kitapçıkların üçüncü taraf sınıflandırmalarıyla ortaya çıkar; yanlış kategori etiketleri ve yıllar arası değişiklikler mümkündür.

Kaynaklar:
- 2026 ÖSYM kitapçık ve cevap anahtarı: https://www.osym.gov.tr/2026yks-tyt-ayt-ve-ydt-temel-soru-kitapciklari-ve-cevap-anahtarlari
- ÖSYM resmî duyuru: https://www.osym.gov.tr/2026-yuksekogretim-kurumlari-sinavi-2026yks-temel-soru-kitapciklari-ve-cevap-anahtarlari-yayimlandi
- MEB 2026 YKS TYT soru bilgileri: https://shgal.meb.k12.tr/meb_iys_dosyalar/58/01/965140/dosyalar/2026_03/69aa7f4ee1c3b678752402_2026_YKS_KILAVUZU.pdf
- Soru kitapçıklarının yıllar ve konulara göre **bağımsız** etiketleme istatistikleri: https://egitimkocu.org/soru-dagilimlari/yks/tyt ve https://egitimkocu.org/soru-dagilimlari/yks/ayt
- 2018–2025 TYT fizik konu tablosu (MEB paylaşımı): https://reyhanlisfkasml.meb.k12.tr/meb_iys_dosyalar/31/08/963209/dosyalar/2025_12/694bcadab4eec921652162_2025TYT_Y%C4%B1llara_Gore_Soru_Dag%C4%B1l%C4%B1m%C4%B1.pdf
- 2022–2026 TYT fizik etiketleme karşılaştırması: https://www.yekakademikursmerkezi.com/tyt-fen-soru-dagilimi/

## Kesin branş hedefleri (yeni oluşturulacak denemeler)

| Branş | Ders kotası |
|---|---|
| TYT Türkçe | Türkçe 40 |
| TYT Matematik | Matematik 30 + Geometri 10 (bazı eski yıllarda 29/11; bu, kullanılan örnek şablondur) |
| TYT Sosyal | Tarih 5 + Coğrafya 5 + Felsefe 5 + Din 5 |
| TYT Fen | Fizik 7 + Kimya 7 + Biyoloji 6 |
| AYT Matematik | Matematik 31 + Geometri 9 (2025 örneği, yıllara göre oynayabilir) |
| AYT Fen | Fizik 14 + Kimya 13 + Biyoloji 13 |
| TYT/AYT tek ders | Yukarıdaki ilgili ders kotası |

Bu profile göre oluşturulan denemeler **ÖSYM'nin resmî kitabı değil**; SoruLab kitaplığından konu dengesi gözetilerek derlenmiş pratik denemelerdir.

## Konu seçiminde dayanak

1. Branş içerisindeki ders kotası **zorunlu**. Her bölümün doğrulanmış, doğru sınav türü + ders + konu etiketli, beş seçenekli, tekil soruları bulunmalı. Yetersizse başlatma iptal edilir ve eksik ders miktarı gösterilir. **Eksik soruları başka dersten tamamlamak yok.**
2. Konu içi seçim **sabit 1 soru/konu kalıbı değildir**. Geçmiş soru kitabı konu etiketlemelerinden çıkartılan *editoryal ağırlık*, konu çeşitliliği, kaynak/yayınevi çeşitliliği ve önceki denemelerde kullanılan soruların tekrarından kaçınma birlikte çalışır. Fizik örneği: son yıllarda optik, hareket/kuvvet, ısı/sıcaklık ve basınç/kaldırma konuları sık görülür; diğerleri dışlanmaz.
3. Özel ağırlığı olmayan her konu aynı baz ağırlıktan başlar. O kategoride güvenilir tarihsel dağılım eşleştirmesi yapılmadıysa **yapılmış gibi gösterilmez**. Türkçe paragraf, TYT fizik ve AYT matematikte kaynaklı frekans/öncelik bilgisi kullanılır; diğer derslerde başlangıçta geniş kapsama önceliği uygulanır.
4. Son 6 denemenin soru kimlikleri tekrar cezası alır; soru havuzu azsa eski sorular yeniden kullanılabilir fakat bunun sonuç ekranında anlaşılır olması gerekir.
5. Konu denemesi: yalnızca kullanıcının seçtiği **tek dersin tek konusundan** oluşturulur. Soru sayısı ve zorluk seçimi sınanır.
6. Cevap anahtarı doğrulanmamış, bozuk görsel/boş soru, yanlış müfredat konusu, kopya kimlik ve geçersiz seçenek içeren kayıtlar dışarıda bırakılır.

## Soru havuzunun mevcut sınırı

SoruLab katalog yapısında TYT Felsefe ve Din tek `tyt_felsefe_din` dersinde depolanıyor; iki alt bölüm yalnızca açık konu kimlikleri sayesinde ayrılıyor. AYT tarih/coğrafya `ayt_tarih_cografya` olarak tek birleşik konu havuzunda. Bu birleşik AYT verisi için resmî 40 soruluk sosyal branş kotasının tamamını doğrulayacak bağımsız ders etiketleri bulunmadığından **AYT Sosyal-1 / Sosyal-2 hazır branş denemesi sunulmuyor**; konu denemesi oluşturulabilir. Destek, ders etiketlerini ayrıştıran bir veri göçüyle genişletilmeli.

## Teknik ve güven kuralları

- Gerçek ÖSYM soru metinleri uygulamaya otomatik kopyalanmaz; mevcut kullanıcının kitaplığa yasal olarak aktardığı kaynaklar kullanılır.
- İmzalı APK ve eski sürümün üzerine kurulum testlerinden geçmeden sürüm yayınlanmaz.
- 2027 YKS müfredatında olası değişiklikler ve yeni yıl dağılımları veri güncellemesi gerektirir.
