# v1.4.8 tabanına alt kazanım / video entegrasyonu

Taban: c37ae398a8134f7b1aec928dca5dd2c5fbc1f1fa
Dal: feature/v148-smart-lessons-upgrade-20261116
Yayımlanan v1.4.5: 92a20fe25885e5472842e00aa8ea7d9239f04312

GitHub karşılaştırması, tabanın v1.4.5'i ve v1.4.7 deneme dalını eksiksiz
ataları arasında içerdiğini doğruladı (her ikisine göre behind_by=0).
Bu entegrasyon doğrudan v1.4.8 tabanının çocuğudur.
Eski main tabanlı PR #51'in bütün dosyaları bu dala kopyalanmadı.

Korunan güncel özellikler:
- v1.4.8 sürüm adı, dinamik yükselen versionCode ve mevcut imzalama.
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
v1.4.5 dağıtılmış APK'ya karşı paket/imza/versionCode yükseltme uyumluluğu kontrolü.
Gerçek cihazda oynatma, ekran dönüşü ve tüm soru tiplerinin doğruluğu ayrıca gerekir.
