# SoruLab — Akıllı Konu Anlatımı (MVP)

## Kullanıcı akışı

Test bitince sorunun **⋮ → Konu Anlatımı** seçeneği doğrudan gömülü YouTube oynatıcı penceresini açar. Pencere kapatıldığında aynı test ve soru konumu korunur. Videolar yalnızca bu projedeki doğrulanabilir ders kataloğundan seçilir.

Konuya ait geçerli eşleştirme yoksa rastgele video veya sahte saniye açılmaz; kullanıcıya durum bildirilir ve YouTube'da arama bağlantısı sunulur.

## Hoca tercihi

- Varsayılan: Otomatik
- Ders bazlı tercih: sınav + ders
- Konu bazlı tercih: sınav + ders + konu
- Öncelik: konu > ders > otomatik
- Hoca çipi anlık video değiştirir; "Ders için seç" veya "Konu için seç" tercihi uygulamada saklar.

## İlk doğrulanmış video kataloğu

| Konu / alt alan | Hoca | YouTube video ID | Başlangıç | Kaynak |
|---|---|---|---|---|
| AYT Matematik / genel türev alma kuralları | Rehber Matematik | WICamyMziqQ | 0 | https://www.youtube.com/watch?v=WICamyMziqQ |
| Türev / rasyonel ifadeler | Rehber Matematik | WICamyMziqQ | 22:05 | Video açıklamasındaki bölüm başlığı |
| Türev / köklü ifadeler | Rehber Matematik | WICamyMziqQ | 29:33 | Video açıklamasındaki bölüm başlığı |
| Türev / dereceli fonksiyonlar | Rehber Matematik | WICamyMziqQ | 12:51 | Video açıklamasındaki bölüm başlığı |
| Türev / zincir kuralı | Bıyıklı Matematik | affPznU8gu0 | 0 | https://www.youtube.com/watch?v=affPznU8gu0 |
| Türev / genel tekrar | Eyüp B | 4gpHnUOCWEQ | 0 | https://www.youtube.com/watch?v=4gpHnUOCWEQ |

**Not:** "0" saniyesi bilinçli genel başlangıçtır; içeriği o noktada kesin alt kazanımın başladığı anlamına gelmez. Kural seçimi sadece sorunun *metni* mevcutsa yapılır. Sadece resim bulunan sorularda alt kazanım güvenilir biçimde okunamadığından konu düzeyine düşülür.

## Teknik notlar ve sınırlar

- Şu an her YKS konusu için içerik kataloğu **yoktur**; yalnızca yukarıdaki türev bölümü bağlanmıştır.
- Gerçek alt kazanımı sorunun görselinden tespit edecek ML/OCR servisi henüz entegre değildir. Çıkarılmış soru metni yoksa tam soru tipi eşleştirmesi iddia edilemez.
- Android WebView içinde YouTube iframe kullanılır. YouTube oynatmayı engellerse (ör. 101/150/153 veya autoplay policy), kullanıcı **YouTube'da izle** bağlantısını kullanabilir. Mobil cihazda oynatıcı davranışı ayrıca test edilmelidir.
- YouTube videosunu veya sesini indirme, yeniden barındırma ya da reklamları engelleme yapılmaz.
- Öğretmen tercihleri cihazdaki SharedPreferences'ta tutulur; sunucuya gönderilmez.
- Gelecekte backend/küratör paneliyle doğrulanmış video kataloğu, soru görsellerinden kazanım çıkarma, videonun doğru saniyesini belirleme, cache ve kalitenin A/B testleri eklenmelidir.
- Derleme doğrulaması için Android SDK, Gradle, Firebase konfigürasyonu gereklidir. Repo içinde `verifyFirebaseConfiguration` görevi build öncesi kontrol yapar.

## Testler

`./gradlew :app:testDebugUnitTest --tests com.example.ui.screens.SmartLessonCatalogTest`

Testler katalog seçimi, doğrulanmış bölüm zamanı ve alakasız konuya video atanmamasını kontrol eder.
