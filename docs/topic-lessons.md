# Konu Anlatımı içerik hattı

`content/lessons.json` tek içerik kaynağıdır. `python3 tools/generate_lesson_catalog.py`
Kotlin kataloğunu üretir; `--check` CI içinde müfredat kapsamını, kimlikleri, tarihleri,
kanıt alanlarını ve güncel çıktıyı doğrular. Yeni ders için arayüz değişikliği gerekmez.
Ders/sınav/konu kimlikleri SoruLabCatalog ile aynı olmalıdır. Kazanım kimlikleri bu
uygulamanın içerik etiketleridir; resmî MEB kazanım numarası değildir.

## İnceleme ve yayınlama

1. Yayıncının YouTube başlığını, kanalını ve konu kapsamını kontrol et.
2. Tam kaynak URL'sini ve inceleme tarihini kaydet. Sıfır saniye sadece video başıdır.
3. Sıfırdan farklı zamanları yalnızca yayıncının bölüm listesinden veya kayda geçirilmiş
   manuel incelemeden al. Bitiş varsa bir sonraki yayımlanmış bölüm başlangıcını kullan.
4. Kazanımı açıkça belirten ifadeleri ekle. Görselden veya tek bir belirsiz kelimeden
   kazanım tahmin etme. Negatif/alıntı metinler hâlâ yanlış pozitif oluşturabilir.
5. Üreticiyi çalıştır, farkı incele, PR testlerini ve cihazdaki oynatmayı kontrol et.

2026-11-16 incelemesinde YouTube arama indeksindeki yayıncı başlık/açıklamaları kullanıldı:
- WICamyMziqQ: Rehber Matematik, Türev Alma Kuralları 1; özel dakikalar yeniden doğrulanamadı, kaldırıldı.
- EImXqB-nEqo: Bıyıklı Matematik, Zincir kuralı; eski affPznU8gu0 yerine kaynakta bulunan kayıt.
- 4gpHnUOCWEQ: Eyüp B, AYT Tekrar Türev-1.
- 2i9_HZErHMU: VIP FİZİK, Hareket - 1; Konum 06:08, sonraki bölüm 08:06;
  Yer değiştirme 11:02, sonraki bölüm 13:51; Sürat 21:46, Hız 23:11.
- ytxYAiyYltc: Sinan İhtiyaroğlu, Kimya Bilimi; sembolik dil 45:43, uyarı işaretleri 1:46:35.

Bu kontrol gerçek cihazda oynatılabilirlik, tam süre veya kesintisiz erişim kanıtı değildir.
Bölüm sonları içerik üreticisinin bölümleridir; tüm kazanımın tam sınırı sayılmaz.
Tüm dersler arama geri dönüşüne sahiptir; tüm konular için video kataloğu tamamlanmış değildir.
Önceki isim tabanlı hoca anahtarları bu sürümde kimlik tabanlı anahtarlara taşınmaz;
kullanıcının tercihini bir kez tekrar kaydetmesi gerekebilir.

## İsteğe bağlı dinamik arama tasarımı (şu anda etkin değil)

Uygulama anahtar barındırmaz; arama düğmesi YouTube sonuç sayfasını açar. Otomatik
sonuç kazıma, altyazı indirme veya stream URL çıkarımı yoktur. Gelecek sunucu adaptörü:
- YouTube Data API search.list: type=video, videoEmbeddable=true, videoSyndicated=true,
  relevanceLanguage=tr ve sınav/ders/konu sorgusu; soru metni/görseli/kullanıcı kimliği gönderilmez.
- videos.list ile status.embeddable, privacyStatus ve contentDetails kontrolü.
- Kota, 403/429, zaman aşımı ve ağ hatalarında paketli katalog / açık arama düğmesine dön.
- Sonuçları otomatik olarak doğrulanmış kazanım/saniye kabul etme; insan incelemesine al.
- Sunucuda anahtar kısıtlaması, istek sınırı, iptal edilebilir istekler; API verilerinin
  saklanma/silinme süreleri için güncel YouTube politikalarına uy.
- Uzak katalog eklenirse şema sürümü, boyut sınırı, HTTPS, bütünlük kontrolü ve son geçerli
  sürüm geri dönüşü şarttır; bu sürümde uzak katalog yüklenmez.

Referanslar:
https://developers.google.com/youtube/iframe_api_reference
https://developers.google.com/youtube/v3/docs/search/list
https://developers.google.com/youtube/terms/developer-policies

## Cihaz kontrolü

Çevrimdışı açma, silinmiş/yerleştirmeye kapalı video (100/101/150), referrer hatası (153),
20 saniye bekleme, tekrar deneme, dış uygulama bulunmaması, öğretmen değiştirme,
arka plana geçişte sesin durması, dönüşte kullanıcı dokunuşu ve aynı soruya dönme.
Cihaz döndürülünce hoca seçimi korunur; oynatma konumu şu anda korunmaz.
