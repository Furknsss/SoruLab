# SoruLab — İkiz Soru Görsel Motoru Kararı (11 Kasım 2026)

## Araştırmanın doğruladığı kritik ayrım

Kullanıcının Google AI Pro aboneliği bulunuyor. Bu abonelik
**Gemini/Google AI Studio web arayüzünde daha yüksek kotalar** sağlıyor;
ancak abonelik üzerinden Android uygulamasına **sınırsız ve ücretsiz
Nano Banana API isteği** göndermek mümkün olarak belgelenmiş değildir.
Doğrudan Gemini API anahtarları Google tarafından ayrı ücretlendirilir.
Kullanıcıdan açık ücret onayı olmadan ücretli görüntü API'si açılmayacak.

Resmî kaynaklar:
- Google AI planları: https://aistudio.google.com/docs/google-ai-plans
- Gemini API pricing: https://aistudio.google.com/docs/pricing
- Nano Banana Pro model: https://deepmind.google/models/gemini-image/pro/
- Nano Banana 2.1 model card: https://deepmind.google/models/model-cards/nano-banana-2-1/

## Uygulamadaki nihai görev paylaşımı

1. **Soruyu anlama:** Görsel destekli Gemini veya kullanıcıya ait Qwen,
   tam kazanım, çözüm/çıkarım adımları ve görsel ilişkileri çıkartır.
2. **İkiz üretme:** Kaynakla aynı alt beceri, aynı istenen/verilen
   düzeni, yakın zorluk, özgün fakat yeni sayılar/bağlam/paragraf.
3. **Hassas matematiksel görsel:** Grafik, açı, uzunluk, kuvvet, devre
   sembolü ve ölçülere dayanan çizimleri veri modelinden
   **deterministik Compose/SVG/geometri** üretir. Rastgele görsel modelinin
   geometrik doğruluk görevini devralmasına izin verilmez.
4. **Karmaşık bilimsel illüstrasyon:** Nano Banana 2.1 veya Nano Banana Pro
   yalnızca opt-in görsel taslak sağlayıcısı. Hücre, organ, laboratuvar
   düzeneği, biyolojik mekanizma ve ayrıntılı görsellerde kullanışlı.
   Bu modellerden gelen resimler **otomatik olarak doğrulanmış
   sayılmaz**; etiketler, ok yönleri, süreç ve nicelikler yeniden
   denetlenmeden yayımlanamaz.
5. **Denetim:** Matematikte kurallı/hakiki hesap; fizik/kimyada
   yasa-birim-korunum kontrolleri; biyoloji ve sözelde ayrı kör
   çözüm/olgusal denetim; görsel-veri eşleşmesi. Hatalı soru yok,
   reddedilen taslak gösterilmez.

## Görsel sağlayıcı yönlendirme politikası

- `native-deterministic` (**öncelikli, çevrimdışı, kotasız**):
  koordinatlar, grafikler, çizgiler, doğrulanabilir şekiller, tablolar.
- `nano-banana-2.1-assisted` (**isteğe bağlı, API ücrete tabi olabilir**):
  gerçekçi çok parçalı biyoloji/kimya/fizik illüstrasyonları. Yalnızca
  insan veya bağımsız modelin yeniden doğruladığı sonuçlar.
- `nano-banana-pro-assisted` (**isteğe bağlı kalite karşılaştırması**):
  özel yeniden çizim/ince yazı işlerinde alternatif görüntü taslağı.
- `unsupported`: Kapsamı doğrulanamayan şekil ya da bulgu.
  Yanlış fotoğraf çizmek yerine ikiz denemesinin başarısız
  olduğunu göster.

## Google AI Pro'dan bugün yararlanma

- Google AI Studio veya Gemini uygulamasında Nano Banana seçenekleriyle
  görsel taslakları **elle deneyebilir**, kaliteli referansları
  gözden geçirebiliriz. Bu, API erişimi ya da otomatik SoruLab
  işlemi oluşturmaz.
- Tüm kullanıcılar için otomatik Nano Banana üretimi ancak ayrı,
  güvenli sunucu bağlantısı, onaylı ücret/kota ayarı ve API
  sözleşmesine uygun erişim kurulursa açılabilir.
- Google aboneliği başkasının API anahtarı gibi paylaşılmaz,
  kullanıcı hesabı oturum çerezleri çekilmez.

## Sürüm kabul kriteri

Geometri şeklinin oranları ve sayıları doğru, bütün şıklar bağımsız
yeniden çözüme göre tutarlı, iki ayrı denetmen hem bilimsel hem
görsel açıklamayı onaylamış, üretimde soruyla alakasız/eksik
şekil kalmamış ve Android gerçek ekran görüntüsü kontrol edilmiş
olmalı. Bu gereksinimler karşılanmadan genel sorularda
'345 Allstar ile aynı kalite' veya 'sınırsız otomatik görsel'
iddiası yapılmaz.

**Uygulama durumu (güncellendi):** Nano Banana 2.1 ve Pro için
isteğe bağlı sunucu tarafı üretim adaptörü, JPEG doğrulama/ölçekleme,
iki ayrı kör görsel denetim ve Android görsel gösterim yolu kodlandı.
`tools/twin_engine/nano_banana_provider.py` bu görevi yapar.
`SORULAB_NANO_BANANA_ENABLE=0` varsayılanıdır: API aboneliği,
anahtarı ve açık maliyet onayı olmadan gerçek istek gönderilmez.
Canlı Nano Banana API'siyle gerçek görüntü üretimi henüz
doğrulanmamıştır; görüntü üretimi/testi, model kurulumunu ve
ödemeyi gerektirir.
