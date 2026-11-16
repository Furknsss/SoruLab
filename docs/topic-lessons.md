# v1.4.9 tabanına alt kazanım / video entegrasyonu

Taban: 48cebd9cbfb0e53b559d6a3ccd7b94bfd61240da
Dal: feature/v148-smart-lessons-upgrade-20261116
Yayımlanan v1.4.5: 92a20fe25885e5472842e00aa8ea7d9239f04312

GitHub karşılaştırması, tabanın v1.4.5'i ve v1.4.7 deneme dalını eksiksiz
ataları arasında içerdiğini doğruladı (her ikisine göre behind_by=0).
Bu entegrasyon güncel v1.4.9 tabanını ikinci ebeveyn olarak birleştirir.
Üç eylemli menü ve güncel sürüm yapılandırması aynen korunmuştur.
Eski main tabanlı PR #51'in bütün dosyaları bu dala kopyalanmadı.

Korunan güncel özellikler:
- v1.4.9 sürüm adı, dinamik yükselen versionCode ve mevcut imzalama.
- v1.4.6 okuyucu / S Pen / sayfa yerleşimi ve v1.4.7 Deneme Studio.
- Yeni PDF OCR havuzu, cevap anahtarı, kırpma düzeltme ve kayıt kurtarma.
- Yayınevi/ZIP learningOutcome, questionType, analyticsText ve ÖSYM metadata alanları.
- PDF içe aktarmada cihaz içi işleme; bu entegrasyon PDF için bulut model çağrısı eklemez.

Eklenen bağlantı:
- Mevcut yayınevi kazanım/soru tipi etiketleri konu kapsamında çözümlenir.
- Yapılandırılmış kazanım kimliği/adı/kaynağı/güveni META başlığında saklanır;
  yayınevi etiketlerinin ve mevcut Firestore alanlarının yerine geçmez.
- Kayıt, bulut/cihaz birleştirme, snapshot ve soru kumbarası kazanımı korur.
- Kırpma önizlemesinde kazanım seçimi/düzeltme ve oynatıcıda ilgili bölüm seçimi.
- Tam kazanım eşleşmesi, hocanın genel videosundan önce gelir.
- Otomatik oynatma denemesi, cihaz engellerse düğme, ekran dönüşünde yaklaşık
  3 saniyelik aralıklarla saklanan konum, hata/yeniden deneme/arka planda duraklama.

İçerik: 111 uygulama kazanım etiketi ve 113 video/bölüm kaydı, 9 gerçek video.
Katalog tüm soru tiplerini tamamlamış değildir. Yeni tabanın 252 konu kaydı için
content/coverage.json açık eksik kapsam raporudur; complete=false.
Python üreticisi konu ve kazanım bağlantılarını doğrular; --check CI'da çalışır.
Öğretim etiketleri resmî MEB kazanım kodları değildir.

Kaynaklı kayıtlar:
https://www.youtube.com/watch?v=WICamyMziqQ
https://www.youtube.com/watch?v=EImXqB-nEqo
https://www.youtube.com/watch?v=4gpHnUOCWEQ
https://www.youtube.com/watch?v=2i9_HZErHMU
https://www.youtube.com/watch?v=ytxYAiyYltc
https://www.youtube.com/watch?v=35lqUJGwj3Y
https://www.youtube.com/watch?v=TFIuAo7auAU
https://www.youtube.com/watch?v=_qgXvwb1Czo
https://www.youtube.com/watch?v=faBQSFqspi0

CI: yeni kazanım testleri ile güncel Deneme, okuyucu, S Pen, snapshot, bulut/yerel
birleştirme ve çevrimdışı politika regresyonları; ardından imzalı APK üretimi ve
v1.4.9-preview-20261116 dağıtılmış APK'ya karşı paket/imza/versionCode yükseltme uyumluluğu kontrolü.
Gerçek cihazda oynatma, ekran dönüşü ve tüm soru tiplerinin doğruluğu ayrıca gerekir.


Yayıncı bölüm metninden inceleme adayı üretme:
- tools/import_lesson_chapters.py ağ veya model çağrısı yapmaz; yayıncının
  meşru şekilde edinilmiş bölüm metni ve elle incelenmiş kazanım eşleştirmesi gerekir.
- Başlık birebir eşleşmeli; başlangıç zamanı metinden okunur, bitiş bir sonraki
  bölümden alınır. Son bölümün bitişi bilinmiyorsa null kalır.
- Üç veya daha fazla bölüm, 00:00 başlangıcı, artan zamanlar, en az 10 saniyelik
  aralık ve ders/konu/kazanım kapsamı denetlenir. Metin SHA-256 izi adayda saklanır.
- Çıktı reviewRequired=true taşır; content/lessons.json veya uygulama kaynaklarının
  üzerine yazamaz. Adaylar kaynak video ve öğretim uygunluğu incelendikten sonra
  content/lessons.json içine alınır; generate_lesson_catalog.py ve --check çalıştırılır.
- Araç canlı video erişilebilirliğini, öğretim doğruluğunu veya kaynağın sahipliğini
  kanıtlamaz. Yeni kayıtlarda sourceOfTimestamp alanı yayıncı başlığını açıkça belirtir.

Örnek kullanım:
python tools/import_lesson_chapters.py --chapters work/publisher-chapters.txt --mapping work/reviewed-mapping.json --reviewed-on 2026-11-16 --output work/lesson-candidates.json

Eşleştirme JSON alanları:
videoId, sourceUrl (aynı videonun canonical YouTube URL'si), teacher, title,
durationSeconds (biliniyorsa) ve mappings listesi. Her mapping subjectId, topicId,
outcomeId ve chapterLabel içerir. chapterLabel yayıncı metnindeki başlıkla birebir
eşleşir; eksik kazanım önce content/outcomes.json içinde ayrıca incelenir.

Canlı arama isteğe bağlı dış YouTube aramasıdır; soru görseli, cevabı veya kullanıcı
kimliği gönderilmez. Bu araç YouTube sayfalarını veya altyazıları kazımaz.
İleride YouTube Data API eklenirse anahtar sunucuda tutulmalı, kota/hata geri dönüşü,
bölge/yerleştirme uygunluğu, veri yenileme/silme ve YouTube API politika koşulları
ayrıca uygulanmalıdır; mevcut araç böyle bir API entegrasyonu iddia etmez.

Kazanım seçiminde null, sorudaki kayıtlı değeri kullanır; boş seçim bilinçli manuel
temizlemedir ve yayınevi etiketine geri dönmez. Düşük güvenli aynı kazanımı yeniden
elle seçmek geçerli manuel seçim olarak uygulanır. Eşleşme yoksa genel videoya
otomatik geçilmez; bölüm zamanı uydurulmaz.
