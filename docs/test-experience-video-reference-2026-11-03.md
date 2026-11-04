# SoruLab — Test Çözme Deneyimi Video Referansı
**Kaynak:** Kullanıcının 3 Kasım 2026 tarihinde gönderdiği 62 saniyelik tablet videosu (`1000112041.mp4`, 1920×1080).  
**Amaç:** SoruLab'ın gelecekteki test ekranını ve kalem/dokunma davranışını geliştirmek için kabul şartnamesi.  
**Durum:** Tasarım referansı; bu dosya özelliklerin çalıştığına dair test raporu değildir. Videoda gösterilmeyen davranışlar özellikle ayrı tutulmuştur.

## Videoda gözlemlenen akış

| Yaklaşık zaman | Kullanıcı etkileşimi / görünen sonuç |
|---|---|
| 00:00–00:06 | `Trigonometri – ÖSYM 4` testinin kitap sayfası açılıyor. Tablet ekranındaki sayfa iki sütunlu; soru numarası, özgün baskı, geometrik şekiller ve A–E seçenekler korunuyor. Üstte sabit beyaz başlık ve lacivert çizim/gezinti araç çubuğu var. |
| 00:01–00:13 | Parmaklarla sayfa yaklaştırılıyor ve tekrar konumlandırılıyor. Yakınlaştırma sırasında matematiksel ifadeler, şekiller, şıklar aynı sayfa koordinatlarında kalıyor; araç çubuğu sayfanın üzerinde sabit. |
| 00:13–00:23 | Soru çözme alanı yakın plan gösteriliyor; serbest el çizimi/işaret mevcut. Beyaz sayfa alanı hem soru içeriğini hem taslak işlem/karalama alanını sağlıyor. Üst sağda `Testi Bitir` erişilebilir. |
| 00:24–00:30 | `Çizim Modu` seçiliyor; kısa açıklama görünüyor: kalem ve parmakla çizim. Seçili araç mavi dairesel vurguyla gösteriliyor. |
| 00:30–00:34 | `Akıllı Mod` seçiliyor; açıklama, kalemle çizim yaparken iki parmakla yakınlaştırma/uzaklaştırmanın mümkün olduğunu belirtiyor. Sayfa tekrar yakınlaştırılıyor; önceki işaretler sayfa üzerinde duruyor. |
| 00:35–00:39 | Silgi aracı seçiliyor. Araç çubuğunda üç farklı nokta/boyut göstergesi ve dokununca açılan üç kademeli dairesel boyut seçici görülüyor; seçili boyut mavi çerçeveyle belirginleşiyor. |
| 00:40–00:47 | Çizim, silme ve sayfayı hareket ettirme arasında geçiş yapılıyor; ardından genel iki sütunlu sayfa görünümüne dönülüyor. |
| 00:47–00:55 | Testteki sonraki iki sütunlu kitapsı sayfaya geçiliyor, yeni sorular (şekilli trigonometri/üçgen soruları) gösteriliyor. Sayfayı kaydırıp soruya yaklaşmak mümkün. |
| 00:55–01:02 | Bir geometri sorusuna yakınlaşılmışken S Pen ile şeklin üzerinde işaretler çiziliyor. Soru metni, diyagram, sayı etiketleri ve cevap seçenekleri birlikte okunabilir kalıyor. |

## Uygulama davranışı: zorunlu kabul kriterleri

### 1. Gerçek kitap sayfası / soru dizilimi
- Kitapçıktan alınan içerik iki sütunlu düzeni ve soru sırasını korumalı; mümkün olduğunda orijinal diyagram, formül, şıklar ve yayınevi logosu kırpılmamalı.
- İçe aktarılan PDF sayfası, boş alanı aşırı artırmadan, normal tablet görünümünde dengeli boyutta gösterilmeli.
- Üstte test başlığı; altında sabit, koyu lacivert kompakt araç çubuğu bulunmalı.
- Önceki/sonraki sayfa kontrolleri ve test bitirme işlevi görünür, tutarlı ve ulaşılabilir olmalı.
- PDF içeriğinden ayrıştırılmış görüntüler, birbirinden farklı yayın ve derslerde aynı yerleşim/etkileşim motoruyla sunulmalı (yayınevi bazlı sabit şablon yok).

### 2. Dokunma, büyütme ve kaydırma
- İki parmak pinch-to-zoom ve pan sorunsuz olmalı; dokunma odaklı yakınlaştırma ile soru/şıkların üzerine rahatça yaklaşılmalı.
- Büyütülmüş formül, grafik, diyagram ve seçenekler kırpılmamalı veya bozulmamalı.
- Araç çubuğu büyütmeyle birlikte kaymamalı; sayfanın içerik koordinatları ile çizim koordinatları doğru eşleşmeli.
- Yakınlaştırmadan genel iki sütunlu görünüme ve diğer sayfalara sorunsuz dönülebilmeli.
- Kalem ve parmak hareketleri birbirine karıştırılmamalı: yanlışlıkla çizim veya sayfa değişimi olmamalı.

### 3. Çizim araçları ve mod ayrımı
- **Akıllı Mod:** S Pen/stylus ile çizim, iki parmakla yakınlaştırma ve kaydırma. Video üzerindeki kısa açıklama etkileşim sonrası görülebilmeli.
- **Çizim Modu:** Kalem *ve* parmak ile çizim. Modun seçili olduğu mavi yuvarlak vurguyla belli olmalı.
- Ayrı el/kaydırma ikonu ve silgi aracı bulunmalı; modlar tek dokunuşla değiştirilmeli.
- Çizimler düşük gecikmeli, akıcı ve el yazısına uygun olmalı; çizgi uçları yuvarlak, eğriler yumuşak olmalı.
- Silgi, soru görüntüsünü değil yalnızca öğrencinin çizimini etkilemeli.
- En az videoda görülen üç kademeli kalem/silgi boyutu seçimi; küçük, gösterişsiz ve seçili değeri açık popover.
- Renk seçimi/paleti ve temizle işlevi çubukta düzenli yerleştirilmeli.
- Bir soruya çizilmiş çizgi büyütme, kaydırma ve geçici mod değişiminde yerinde kalmalı.

### 4. Ekran temizliği ve kontrol davranışı
- Ana soru görselini modal, sabit ikon ya da aşırı dekorasyonla kapatma.
- Araçlardan birine dokunulduğunda kısa ve anlaşılır bilgi balonu çıkabilir; kısa süre sonra kaybolmalı ve okuma alanını sürekli kapatmamalı.
- Test çözülürken soru sağ üstünde üç nokta gizli; test bittikten sonra review sırasında mevcut soru menüsü açılmalı (**bu kural önceki kullanıcı talebinden; videoda üç nokta davranışı gösterilmiyor**).
- Sorunun üstünde fırça tarzı doğru/yanlış/boş işaretleri ve sağ-alt optik form, daha önce paylaşılan fotoğraf/talep referansına göre yapılmalı (**bu videoda test bitirme/optik/sonuç ekranları açılmıyor**).
- Hata Frekansı için ders/konu/alt kazanım/soru tipi analizleri, doğrulanmamış cevaplar ayrılarak gösterilmeli (**bu videoda analiz ekranı görünmüyor**).

## Uçtan uca test senaryoları
1. İki sütunlu matematik PDF testi aç; soru gövdeleri, numaraları, formüller, şıklar, sayfa sırası tam mı kontrol et.
2. S Pen ile bir sorunun yanına yuvarlak çiz; iki parmakla büyüt; çizim, soru üzerindeki aynı noktada kalmalı.
3. Akıllı Mod'da tek parmak kaydırma ve iki parmak yakınlaştırma hareketlerini, kalem çiziminden ayır; yanlış mürekkep izi üretme.
4. Çizim Modu'nda parmak ve S Pen ile çizimi ayrı ayrı dene.
5. Üç boyut arasında geçiş yap; seçilen boyut fırça/silgiyi etkilesin, imleç/araç menüsü soruyu kapatmasın.
6. Silgiyle çizilen işareti sil; orijinal soru metni ve şekli değişmesin.
7. Yakınlaştırılmış ikinci sayfaya ve tekrar iki sütunlu görünüme geç; önceki çizimler ve cevaplar kaybolmasın veya başka soruya taşınmasın.
8. Aynı etkileşimleri farklı ders, farklı yayınevi, farklı boyuttaki PDF ve dikey/yatay tablet yerleşiminde doğrula.
9. Testi bitir; optik cevaplar, doğru/yanlış/boş ve doğrulanmamışlar ile review araçlarının mod geçişlerini ayrı doğrula.

## Görünmeyen veya ölçülemeyen unsurlar
- Bu videodan FPS, gecikme (ms), kayıtların uygulama yeniden açılınca korunması, çok uzun PDF'lerin performansı ve tüm cihazlarda doğru çalışma **kanıtlanamaz**.
- Optik form, premium sonuç, Hata Frekansı ve test sonu üç nokta menüsü videoda açılmıyor; onlar önceki ekran görüntüleri ve açık tasarım isteklerine dayanır.
- Videonun referans kitabındaki marka, test ismi veya soru içeriği program koduna sabitlenmemelidir.
