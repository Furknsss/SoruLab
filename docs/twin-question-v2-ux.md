# SoruLab — İkiz Soru V2: Arayüz ve Etkileşim Şartnamesi

Referans: Kullanıcının 345 All Star ekran fotoğrafları (düzen anlayışı; SoruLab kendi marka dilini kullanır). İkiz Test modülü **değiştirilmeyecek**: önceden yanlış/boş bırakılan orijinal soruların tekrarıdır.

## Gezinme
1. Test çözümü / sonuç ekranındaki orijinal sorunun üç-nokta menüsünden **İkiz Soru** seçilir.
2. Tam ekran karşılaştırma açılır; orijinal soru **sağda**, AI tarafından üretilmiş ikiz **solda**.
3. Öğrenci ikiz sorunun şıkkını seçer, **Cevabı Göster** ile kontrol eder, isteğe bağlı **Adım adım çözümü göster**.
4. Geri düğmesi sonuç sayfasına döner. İki kartın çizim izleri birbirinden ayrıdır.

## Tablet: yatay karşılaştırma
- Üst bar: 56 dp, geri / ders • konu • soru no / mevcut not alma araçları.
- Sayfa zemini: `#F8FAFC`.
- Güvenli yatay içerik boşluğu: 16–24 dp; kartlar arası 16 dp.
- Her kart mevcut genişliğin yaklaşık yarısını kullanır; kendi içinde bağımsız dikey kaydırılır. Kart içi boşluk: 20 dp.
- Sol kart başlığı: **İkiz Sorusu**, `#EA580C`, beyaz metin; sağ kart: **Test Sorusu**, `#1E1B4B`.
- Kart yüzeyi: beyaz, 14–16 dp köşe yarıçapı, hafif gölge ve düşük kontrastlı kenarlık.
- İkiz soru kökü: 17 sp, satır aralığı 25 sp; matematiksel semboller kesilmeden gösterilmeli.
- Beş seçenek: A–E tek satıra sıkıştırılmaz, **dikey seçim kartları**; iç boşluk en az 11 dp; dokunma alanı tercihen 44–48 dp.
- Durum renkleri: seçili `#EFF6FF` mavi; açıklamadan sonra doğru `#F0FDF4` yeşil; seçilen yanlış `#FEF2F2` kırmızı.
- Birincil CTA: altta **Cevabı Göster** mavi `#3B82F6` geniş yuvarlatılmış buton. Çözüm ilk aşamada gizli, ardından isteğe bağlı açılır.
- Sağ kart: orijinal kırpılmış soru görseli tam boyut korunarak `ContentScale.Fit`; en çok kullanılabilir alanı kaplar.
- Sağ kart altı: doğrulanmış doğru cevap, öğrenci cevabı ve yayınevi logosu + kitap adı. Yanlış/eksik anahtara sahte kesin cevap yazılmaz.

## Telefon: geliştirme hedefi (henüz kodlanmadı)
- 700 dp altı kullanılabilir genişlik: aynı iki kartı yan yana zorla küçültme.
- Üstte erişilebilir **İkiz Sorusu / Test Sorusu** sekmeleri; varsayılan İkiz Sorusu.
- Sekme değiştirirken işaretlenen seçenek ve açılmış çözüm durumu korunmalı.
- Çizim araçları dar ekranda aşırı kalabalıklaşmadan kaydırılabilir olmalı.

## Hata / üretim davranışı
- Yüklenirken: "Aynı kazanımda yeni soru hazırlanıyor…" + ilerleme gösterimi; sahte yüzde yok.
- Üretim başarısız: açık hata + "Tekrar dene"; bozulan JSON veya boş şıklardan hiçbirisi öğrenciye soru diye gösterilmez.
- Oluşturulan cevapların doğruluğu model beyanından ibaret değildir; ileride ayrı bir doğrulama katmanı gereklidir.
- Öğrencinin eski cevap anahtarı ve yayınevi metadata'sı yeniden yazılmaz.

## Kabul kriterleri
1. Tablet yatay görünüm: solda İkiz Sorusu, sağda orijinal.
2. A–E uzun metinler birbirinin üzerine binmeden alt alta görünür.
3. Öğrenci cevap göstermeden ikiz soruyu çözebilir.
4. Cevabı ve çözümü açma iki ayrı adımdır.
5. Şık seçimi sonrası doğru/yanlış görsel durumu tutarlıdır.
6. Sağda orijinal soru, öğrenci cevabı ve kaynağın doğru yayınevi adı/logo fallback'ı görünür.
7. İkiz Test'in eski soruları yeniden sunan işlevi etkilenmez.
8. Fiziksel Android tablet görsel testi, TalkBack/dokunma alanı kontrolü ve düşük genişlik testi ayrıca yapılır.

## Uygulama kapsamı
Bu dalda tablet yönü, seçenekler, metin boyutu, aşamalı çözüm ve kaynak bandı kodlanmıştır. Telefon sekmeleri ve yapay zekâ çıktı doğrulama mekanizması henüz tamamlanmamıştır.
