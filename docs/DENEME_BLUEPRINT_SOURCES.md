# Deneme dağılımları — 14 Kasım 2026

Başlangıç commit: 35b0707644c52a777d5eef082519b008d4880d04 (PR #50).
Son v1.4.6 test sayfası ve önceki deneme stüdyosu çalışması korunarak ilerlenmiştir.

## Kaynak ile ürün kararı arasındaki ayrım

ÖSYM kitapçıkları ders/test toplamlarını doğrular. Konu etiketlemesi ise öğretmenlerin
soruları sınıflandırmasına bağlıdır; ÖSYM gelecek sınavda her konu için sabit sayı vaat etmez.
Uygulamadaki kotalar **SoruLab çalışma profilleridir**. Tarihsel tabloların aynen kopyası,
belli bir yılın birebir simülasyonu veya sınav tahmini değildir.
Araştırma tarihi: 14 Kasım 2026. Profil sürümü: 2026.11-v2.

Birincil kitapçık:
https://www.osym.gov.tr/2026yks-tyt-ayt-ve-ydt-temel-soru-kitapciklari-ve-cevap-anahtarlari

Konu dağılımları için soruları tek tek sınıflandıran Eğitim Koçu'nun 2018–2025 ders
tabloları incelendi. Aşağıdaki her dersin uygulama ekranında kendi kaynak bağlantısı bulunur.
Bu üçüncü taraf sınıflandırma, resmî gelecek soru kotası olarak gösterilmez.

| Ders | İncelenen konu tablosu |
|---|---|
| TYT Türkçe | https://egitimkocu.org/soru-dagilimlari/yks/tyt/turkce |
| TYT Matematik | https://egitimkocu.org/soru-dagilimlari/yks/tyt/matematik |
| TYT Geometri | https://egitimkocu.org/soru-dagilimlari/yks/tyt/geometri |
| TYT Fizik | https://egitimkocu.org/soru-dagilimlari/yks/tyt/fizik |
| TYT Kimya | https://egitimkocu.org/soru-dagilimlari/yks/tyt/kimya |
| TYT Biyoloji | https://egitimkocu.org/soru-dagilimlari/yks/tyt/biyoloji |
| TYT Tarih | https://egitimkocu.org/soru-dagilimlari/yks/tyt/tarih |
| TYT Coğrafya | https://egitimkocu.org/soru-dagilimlari/yks/tyt/cografya |
| TYT Felsefe | https://egitimkocu.org/soru-dagilimlari/yks/tyt/felsefe-grubu |
| TYT Din | https://egitimkocu.org/soru-dagilimlari/yks/tyt/din-kulturu-ve-ahlak-bilgisi |
| AYT Matematik | https://egitimkocu.org/soru-dagilimlari/yks/ayt/matematik |
| AYT Geometri | https://egitimkocu.org/soru-dagilimlari/yks/ayt/geometri |
| AYT Fizik | https://egitimkocu.org/soru-dagilimlari/yks/ayt/fizik |
| AYT Kimya | https://egitimkocu.org/soru-dagilimlari/yks/ayt/kimya |
| AYT Biyoloji | https://egitimkocu.org/soru-dagilimlari/yks/ayt/biyoloji |
| AYT Edebiyat | https://egitimkocu.org/soru-dagilimlari/yks/ayt/turk-dili-ve-edebiyati |
| AYT Anlam | https://egitimkocu.org/soru-dagilimlari/yks/ayt |

## Uygulanan kararlar

- TYT Türkçe 40, matematik 40 (30+10), sosyal 20 (5+5+5+5), fen 20 (7+7+6).
- AYT matematik 40 (31+9), fen 40 (14+13+13). Matematik/geometri ayrımı yıllara göre
  değişebilir; kullanılan 30+10 ve 31+9 örnek çalışma profilleridir.
- AYT Türk Dili ve Edebiyatı 24 = anlam/okuma 6 + alan 18. Eski taslaktaki 18 soruluk
  alan sınırı tam edebiyat branşı gibi sunulmamalıydı; düzeltildi.
- Tek ders denemelerinde ilgili dersin kotası kullanılır. Konu denemesinde TYT/AYT,
  ders, tek konu, 1–40 soru ve zorluk seçimi uygulanır.
- Her dersin dağılımı DenemeTopicProfiles.kt içindedir. Grup altındaki katalog konuları
  yalnızca kendi grup kotasını karşılayabilir. Eksik grup başka konuyla tamamlanmaz.
- Konu grupları tarihsel başlıkların mevcut katalogdaki alt konulara eşleştirilmesiyle
  oluşturuldu. Özellikle 5–7 soruluk derslerde her alt konuya bir soru düşmez.
- TYT fizik örneği: madde 1, hareket/kuvvet 1, ısı/genleşme 1, basınç/kaldırma 1,
  elektrik/manyetizma 1, dalga 1, optik 1. Bu dengeli kapsam tercihidir; her yılın
  gerçek ÖSYM dağılımının böyle olduğu iddia edilmez.
- AYT matematik, geometri, fizik, edebiyat ve TYT felsefenin eksik katalog başlıkları
  eklendi. Önceki soru kayıtları değiştirilmez veya silinmez; yeni etiketlere soru
  gelene kadar ilgili kotalar için eksik uyarısı verilmesi beklenir.
- Dengeli seçimi, kota içinde erişilebilen zorluk çeşitliliğini gözetir; yeterli veri
  yokken kesin bir zorluk yüzdesi iddia edilmez. Kolay/orta/zor filtresi esnetilmez.
- Son 6 çözülmüş denemede kullanılan sorular öncelikle dışarıda tutulur. Havuz dar
  olduğunda yeniden kullanılan soru sayısı deneme oluşturulurken kaydedilir ve karnede gösterilir.
- Profil sürümü, uygulanan konu planı, zorluk ve tekrar sayısı soru anlık görüntüsünde
  saklanır. Yeni profil eski denemenin içeriğini veya karnesini değiştirmez.
- Karnede yanlış soru için tam nete göre 1,25; boş için 1,00 net kaybı hesaplanır.
  Toplam net doğru − yanlış/4 biçimindedir.

## Sosyal branşları ve veri uyumluluğu

AYT Sosyal-1: Türk Dili ve Edebiyatı 24 + Tarih-1 10 + Coğrafya-1 6.
AYT Sosyal-2: Tarih-2 11 + Coğrafya-2 11 + Felsefe Grubu 12 + Din 6.
Ayrıca tek ders branşları ve bu derslerin konu denemeleri bulunur.
Yeni AYT felsefe/din katalogları eklendi. Birleşik eski tarih/coğrafya kayıtları
silinmez ve otomatik tahminle yeniden etiketlenmez; konu kimliklerinden kesin
ayrılan tarih/coğrafya kümeleri kullanılır. Eksik yeni konular için soru eklenmesi
gerekir. Felsefe grubunda felsefe, psikoloji, sosyoloji, mantık 3'er sorudur.
Din muafiyeti için alternatif felsefe bölümü bu profilde yer almaz; standart dinli
profil kullanılır ve ders kotaları seçimden önce gösterilir.

Ek incelenen öğretmen sınıflandırmaları:
- https://egitimkocu.org/soru-dagilimlari/yks/ayt/tarih
- https://egitimkocu.org/soru-dagilimlari/yks/ayt/cografya
- https://egitimkocu.org/soru-dagilimlari/yks/ayt/felsefe-grubu
- https://egitimkocu.org/soru-dagilimlari/yks/ayt/din-kulturu-ve-ahlak-bilgisi

Sosyal-1/2'nin alt konu grupları da editoryal çalışma planıdır. İki test için toplam
21 tarih / 17 coğrafya tablosu, her alt testte aynı dağılım olduğu iddiasına dönüştürülmez.

## Doğrulama

Her yayımlanan branşın konu kotaları, tek ders/konu izolasyonu, cevap anahtarı,
eksik havuz, zorluk, tekrarlanan kimlikler, tekrar önceliği ve anlık görüntü
uyumluluğu regresyon testleriyle denetlenir. Telefon oluşturma ve tablet karne
akışları gerçek Compose bileşenleriyle Robolectric üzerinde sınanır.
Bu yerel testler fiziksel cihaz testi yerine geçmez. İmzalı APK ayrı CI
derlemesi ve önceki sürümle imza/versionCode kontrolünden geçmelidir.
