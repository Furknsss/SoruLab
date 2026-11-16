# Alt kazanımdan video bölümüne bağlantı

PDF kırpıcının verdiği alt kazanım artık ana konu etiketinden ayrı tutulur:
learningOutcomeId, learningOutcomeName, learningOutcomeSource ve learningOutcomeConfidence.

- Kırpma sırasında konu kapsamındaki kayıtlı kazanımlar arasından OCR eşleştirmesi yapılır.
- Yerel eşleşme yoksa mevcut Firebase AI bağlantısı üzerinden görsel sınıflandırma denenir.
  Model yalnızca aday kimliklerinden birini veya unknown döndürebilir; 12 saniye sınırı vardır.
  Aday bulunmayan konularda istek yapılmaz. Hata/kota/ağ sorununda soru korunur.
  Modelin güven puanı kalibre edilmiş doğruluk oranı değildir; gerçek veriyle ölçülmelidir.
- PDF önizlemesinde kazanım düzeltilebilir veya kaldırılabilir.
- Kazanım mevcut META başlığında saklanır; Firestore belge alanları ve kuralları genişletilmez.
  Yerel önbellek ve bulut aynı aiSolutionSummary alanını taşır.
- Test oluşturma, kayıtlı test snapshot'ları ve soru kumbarası kazanımı korur.
  Eski sorularda mevcut soru metninden konservatif eşleştirme yapılır; kitaplar yeniden
  kırpılmadan yalnızca görsellerden toplu sınıflandırma bu değişiklikte yapılmaz.
- Oynatıcı kayıtlı ve ders/konu kapsamı doğrulanan kazanımı öncelikli kullanır.
  Kazanımın kendi doğrulanmış video bölümü yoksa ilk alt bölümü rastgele açmaz.
  Kullanıcı oynatıcıda alt kazanımı da düzeltebilir (bu seçim cihazda saklanır).

## İçerik üretimi

content/outcomes.json: uygulamaya ait öğretim etiketleri; resmî MEB kazanım kodları değildir.
content/lessons.json: gerçek YouTube referansları, yayıncı bölüm sınırları, kaynak, inceleme tarihi.
python3 tools/generate_lesson_catalog.py: iki Kotlin kataloğu ve content/coverage.json üretir.
--check: müfredat kapsamı, kayıtlı kazanım bağlantıları ve güncel çıktıları doğrular.

coverage.json tüm müfredat konularını içerir ve eksik kapsamı açıkça gösterir.
complete=false bilinçlidir: tüm derslerin bütün alt kazanımları ve videoları tamamlanmış değildir.
Şu an 111 kazanım etiketi, 113 video/bölüm kaydı,
46/218 konuda video referansı vardır.
Bu oran soru tipi kapsama oranı değildir; bazı bölümler geniş bir kazanım grubunu kapsar.

## 2026-11-16 kaynak incelemesi

Yeni kaynaklar yayıncıların YouTube başlık/açıklama bölüm listelerinden:
- https://www.youtube.com/watch?v=faBQSFqspi0 (Selin Hoca, AYT biyoloji)
- https://www.youtube.com/watch?v=35lqUJGwj3Y (Selin Hoca, TYT biyoloji)
- https://www.youtube.com/watch?v=TFIuAo7auAU (Yusuf Mert Arslan, TYT coğrafya)
- https://www.youtube.com/watch?v=_qgXvwb1Czo (Türkçe Saati, dil bilgisi)
Türkçe açıklamasındaki sırası çelişen isim tamlamaları bölümü katalog dışında bırakıldı.
Kaynak incelemesi canlı video oynatması, içerik kalitesi veya tam soru tipine uygunluk
için insan değerlendirmesinin yerine geçmez. Bitiş sınırı yayıncının sonraki bölümüdür.

Önceki kaynaklar:
https://www.youtube.com/watch?v=WICamyMziqQ
https://www.youtube.com/watch?v=EImXqB-nEqo
https://www.youtube.com/watch?v=4gpHnUOCWEQ
https://www.youtube.com/watch?v=2i9_HZErHMU
https://www.youtube.com/watch?v=ytxYAiyYltc

## Dinamik arama ve cihaz sınırları

Otomatik YouTube arama API'si etkin değildir; anahtar APK'ya gömülmez.
Arama geri dönüşü sınav/ders/konu/alt kazanım/hoca adını kullanır; soru görseli veya metni YouTube'a gönderilmez.
Görsel alt kazanım sınıflandırması Firebase AI'ya kırpılmış görseli ve OCR metnini gönderir.
Çok sayıda belirsiz soru içeren büyük PDF'lerde ağ isteği sayısı ve süre artabilir.
Gerçek cihazda model erişimi, sınıflandırma doğruluğu, oynatıcı/referrer hataları,
çevrimdışı akış ve ekran dönüşü ayrıca sınanmalıdır. İzleme konumu ekran dönüşü için yaklaşık 3 saniye aralıkla saklanır; uygulamanın tamamen kapatılmasından sonra devam etme yoktur. Otomatik oynatma denenir; cihaz/YouTube engellerse kullanıcı oynat düğmesine dokunur.
