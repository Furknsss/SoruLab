# SoruLab RC7 imzalama ve Google girişini açma (tek seferlik)

**Neden?** RC7 CI regresyon testleri geçiyor; ancak `DEBUG_KEYSTORE_BASE64` GitHub Actions secret'ı yok. RC5 ve RC6 test APK'ları farklı rastgele sertifikalarla imzalandığı için Google girişinin OAuth kimlik doğrulaması ve Android güncellemesi tutarsız.

**Güvenlik kuralı:** İmzalama anahtarını, Base64 karşılığını veya `*.keystore` / `*.jks` dosyasını GitHub'a commit etme, issue/PR'a ekleme veya açık bir sohbette paylaşma.

1. Mümkünse daha önce kurduğun çalışan uygulamayı imzalayan **orijinal** keystore'u bul. Yeni anahtarla mevcut uygulamanın üzerine veri kaybetmeden güncelleme yapmak mümkün değildir; bu durumda uygulamayı silme.
2. Hiç sabit test anahtarın yoksa geliştirme bilgisayarında yerel bir kez üret:

   ```bash
   keytool -genkeypair -noprompt -keystore debug.keystore \
     -storepass android -keypass android -alias androiddebugkey \
     -dname "CN=SoruLab Preview,O=SoruLab,C=TR" -keyalg RSA \
     -keysize 3072 -validity 10000
   keytool -list -v -keystore debug.keystore -alias androiddebugkey \
     -storepass android
   ```

   Çıktıdaki **SHA-1 ve SHA-256** değerlerini kaydet. Bunlar sertifika parmak izleridir, özel anahtar değildir.

3. Firebase Console → ilgili SoruLab projesi → Project settings → Your apps → Android `com.aistudio.sorulab.ykslab` → Add fingerprint. Bu imza anahtarının SHA-1 ve SHA-256 değerlerini kaydet. **Authentication → Sign-in method → Google** etkin olmalıdır. Yapılandırma güncellendikten sonra `google-services.json` dosyasını yeniden indir.
4. Projede `google-services.json.base64` dosyasını **yeni indirilen Firebase yapılandırmasıyla** yenile (mevcut projeyi veya paket adını değiştirme). Android yapılandırması doğru güncellenmeden SHA-1 kontrolü başarısız olacaktır.
5. GitHub repository → Settings → Secrets and variables → Actions → **New repository secret**, ad: `DEBUG_KEYSTORE_BASE64`. Değer: aynı yerel `debug.keystore` dosyasının tek satırlık Base64 gösterimi:

   ```bash
   base64 -w0 debug.keystore
   ```

   macOS'ta `base64 < debug.keystore | tr -d '\\n'` kullanılabilir. Bu çıktıyı sadece secret alanına yapıştır; başka yerde yayınlama.
6. GitHub Actions'ta `PDF preview APK` iş akışını **RC7 dalında** yeniden çalıştır. `scripts/check_debug_oauth_signing.py` SHA-1'i Firebase Android OAuth yapılandırmasıyla karşılaştırır; eşleşmezse APK **yayınlanmaz**.
7. Üretilen APK'ya kurulmadan önce imza ve sürüm kodu doğrulaması yap. İlk gerçek tablet testinde Google hesap seçimi, PDF içe aktarma, kitaplıkta yeniden açma, 2 sütunlu sorular, cevap eşleştirmesi, S Pen çizme/silme ve sayfa değişimini gözle doğrula.

## Kabul eşikleri

- Statik Kotlin regex kontrolü, bütün tanımlı unit/regresyon testleri, Android APK paketleme ve sabit imza / OAuth kontrolü geçmeli.
- Tam kitap taramasında çıkarılan soru sayısı, kaynak sayfadaki basılı sorularla örnek değil **sayfa bazında** karşılaştırılmalı; eksik sayfalar raporlanmalı.
- Kaydetme başarılı mesajı sadece atomik cihaz kaydı ve yeniden okuma başarılıysa gösterilmeli.
- Gerçek tablet testi tamamlanmadan **%100 hatasız** veya bütün yayınevlerinde kusursuz çalışıyor iddiasında bulunulmamalı.
