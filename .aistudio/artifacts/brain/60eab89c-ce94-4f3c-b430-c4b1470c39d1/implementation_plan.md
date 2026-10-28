# SoruLab — Konu Kartları, Sade Anasayfa ve Cevap Anahtarı Eşleştirmeli Otomatik Soru Havuzu Planı

Kullanıcı geri bildirimlerin doğrultusunda uygulamayı sadeleştirecek ve PDF kitaplarından **Cevap Anahtarı Eşleştirmeli + Yayın Logolu Otomatik Soru Havuzu** sistemini kuracak plan aşağıdadır:

## 1. Soru Pratiği Konu Kartlarının Sadeleştirilmesi
- **Tekrarlayan Ders Adı ve İkon Kaldırma:** Ders başlığı (ör. *Geometri*, *Türkçe*, *Matematik*) zaten üst şeritte ve 6 seviyeli çark ekranında yazdığı için, konu kartlarının (`TopicPracticeCard`) içindeki sol ikon/logo ve üst ders rozeti tamamen kaldırılacak.
- **Sade Çerçeveli Görünüm:** Beğendiğin premium çerçeveli kart yapısı korunacak; kart içinde yalnızca **konu ismi** net ve ferah bir tipografiyle yer alacak.

## 2. Anasayfa Sadeleştirilmesi (Laf Kalabalığının Kaldırılması)
- **Öz ve Net İçerik:** Anasayfadaki uzun açıklama cümleleri, alt metin kalabalıkları ve gereksiz rozetler temizlenecek; yalnızca net başlıklar ve doğrudan aksiyon odaklı kartlar kalacak.
- **PDF Kırpma Kartının Kaldırılması:** Anasayfada (ve alt sekmelerde) yer alan *"PDF'ten Soru Kırp"* butonları ve yönlendirmeleri tamamen kaldırılacak; soru ekleme işlemi yalnızca **Profil -> Soru Havuzu** yönetim panelinden yapılacak.

## 3. Profil Menüsü -> "Soru Havuzu" (Otomatik PDF Soru & Cevap Anahtarı Sistemi)
Sağ üstteki profil fotoğrafına tıklandığında açılan pencereye **"Soru Havuzu"** sekmesi eklenecek ve aşağıdaki tam otomatik iş akışı çalışacak:

- **Yayın ve Logo Yönetimi:**
  - Popüler yayınlar (3D, 345, Orijinal, Bilgi Sarmal, Apotemi, Aydın, Palme vb.) hazır tanımlı gelecek; ayrıca galeriden istediğin yayının logosunu ve adını ekleyebileceksin.
  - PDF yüklerken seçtiğin yayının adı ve logosu, o PDF'ten çıkarılan tüm sorulara otomatik bağlanacak.
- **Son Sayfalardaki Cevap Anahtarını Okuma ve Birebir Eşleştirme:**
  - Sistem önce PDF'in son sayfalarını (veya test sonlarını) tarayarak **kitabın resmi cevap anahtarı tablolarını** Gemini Vision ile okuyacak ve `Test Adı / Sayfa / Soru Numarası -> Doğru Şık (A-E)` haritasını çıkaracak.
  - Ardından test sayfalarındaki soruları soru numaralarıyla birlikte otomatik tespit edip kırpacak ve her sorunun doğru şıkkını **doğrudan kitabın cevap anahtarından %100 eşleştirerek** kaydedecek (ek olarak cevabın neden o şık olduğunu açıklayan adım adım çözümü de hazır tutacak).
- **Gerçek Seviye ve Konu Etiketleme (0 Sallamasyon):**
  - Her kırpılan soru için sınav türü (`TYT` / `AYT`), ders, müfredat konusu ve sorunun gerçek işlem/yorum ağırlığına göre **6 Seviye Çarkı karşılığı** (`Başlangıç`, `Pekiştirme`, `Gelişiyorum`, `ÖSYM`, `Derece`) ile `Kolay / Orta / Zor` etiketi belirlenip arka plandaki Soru Havuzuna kaydedilecek.

## 4. Test Çözüm Ekranında Soru Numarası Üstünde Yayın Logosu
- Çarktan test oluşturup çözerken, her sorunun **sol üst köşesinde soru numarasının hemen üstünde** o sorunun alındığı **yayının logosu** (veya özel yayın rozeti) net şekilde gösterilecek.
- Kırpılan orijinal soru görseli ve şıkları yüksek çözünürlükle ekranda yer alacak.
