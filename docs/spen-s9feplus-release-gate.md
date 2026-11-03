# Galaxy Tab S9 FE+ / S Pen — RC6 cihaz kabul kontrolü

## Kapsam
- Referans: `docs/test-experience-video-reference-2026-11-03.md`.
- Hedef: Samsung Galaxy Tab S9 FE+ (12,4 inç), gerçek S Pen, Android tablet.
- Bu kontrol cihaz üzerinde tamamlanana kadar **donanım performansı ve palm rejection onaylı değildir**.
- Uygulama cihaz performansını yazılımsal olarak varsaymamalı; aynı girdi kuralları diğer yayınevi ve derslerde uygulanır.

## Derleme kapısı
- [ ] `StylusInputPolicyTest` (kalem/parmak/palm ayrımı, basınç sınırları, sayfa anahtarları)
- [ ] Önceki PDF/cevap anahtarı regresyon testleri
- [ ] Kotlin/Java Regex preflight
- [ ] `:app:assembleDebug` (rc6 APK / versionCode 29)
- [ ] `main` ve kayıtlı uygulama değiştirilmedi

## Cihaz senaryoları (henüz çalıştırılmadı)
1. **Akıllı Mod:** Kalemle geometri üzerinde iki çizgi oluştur; kalemi kaldır, parmakla tek başına sürükle; yanlış mürekkep oluşmamalı ve sayfa değişebilmelidir.
2. **Avuç içi:** S Pen ile kesintisiz yazarken elin ekranla temas etsin. Avuç iz bırakmamalı; normal kalem çizgisi kopmamalıdır. Android'in gönderdiği iptal olayları da test edilmeli.
3. **Baskı:** İnce, orta ve yüksek S Pen basıncıyla yan yana çizgi çiz. Farklı kalınlıklar doğal olmalı; ani sıçrama/titreme gözlenmemeli.
4. **İki parmak:** Büyüteci seçmeden 1×→2×→4×→1× yaklaştır/uzaklaştır. Parmakla yazma açık/kapalıyken iki parmak hareketi mürekkep bırakmamalı.
5. **Odak:** Bir sayfanın sağ üstündeki şekle yakınlaştır; parmakların altında bulunan nokta kaybolmamalı. Görüntü ile önceki S Pen işareti kaymadan birlikte büyümeli.
6. **Çizim Modu:** S Pen ve tek parmakla ayrı çizgiler çiz. İkinci parmağı ekleyip yakınlaştırırken tamamlanmamış parmak izi kaydedilmemeli.
7. **Silgi:** Çizgileri sil, fakat soru ve şekil görselleri değişmeden kalsın. Aynı sayfada birden çok çizimden sonra silgi gecikmesini kontrol et.
8. **Sayfaya özgü kayıt:** Sayfa 1'e tik, sayfa 2'ye çarpı çiz; birbirleriyle karışmasın. Sayfa değiştirip geri gelince çizimler korunmalı.
9. **Kayıt geri yükleme:** Test ekranından çıkıp aynı teste tekrar gir. Kaydedilmiş çizimler aynı sayfa ve soru konumunda görünmeli. Eski RC5 çizimleri sayfa 1'de görünmeye devam etmeli.
10. **Ekran yönü ve araçlar:** Yatay ve dikey modda çiz, sayfa kaydır, optik formu aç, sonuç ekranına geç. S Pen işlemi menülere, sorulara veya şıklara yanlış tıklama üretmemeli.
11. **Uzun kullanım:** En az 20 dakikalık yoğun yazı/çizim, silgi ve zoom testi. Bellek/ısı, takılma, gecikme ve uygulama kapanması not edilmeli.
12. **Veri koruma:** Mevcut uygulamanın üzerine güncelle; kullanıcı kayıtları, doğru-yanlış işaretleri ve PDF kitaplığı kaybolmamalı.

## Kabul koşulu
- Sadece otomatik testlerin başarıyla bitmesi **cihaz sertifikasyonu değildir**.
- Gözlemlenen başarısızlıklar tekrar üretilebilir adımlarla raporlanmalı; cihaz onayı tamamlanmadan `main` veya Play release'e birleştirilmemelidir.
