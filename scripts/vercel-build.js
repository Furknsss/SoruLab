// scripts/vercel-build.js
const fs = require('fs');
const path = require('path');

const publicDir = path.join(__dirname, '..', 'public');
if (!fs.existsSync(publicDir)) {
  fs.mkdirSync(publicDir, { recursive: true });
}

const apkSource = path.join(__dirname, '..', 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk');
const downloadsDir = path.join(publicDir, 'downloads');
let apkDownloadHref = 'https://github.com/Furknsss/SoruLab/releases/latest';

if (fs.existsSync(apkSource)) {
  if (!fs.existsSync(downloadsDir)) {
    fs.mkdirSync(downloadsDir, { recursive: true });
  }
  const apkTarget = path.join(downloadsDir, 'SoruLab-Release.apk');
  try {
    fs.copyFileSync(apkSource, apkTarget);
    apkDownloadHref = '/downloads/SoruLab-Release.apk';
    console.log('SoruLab: Release APK public/downloads/SoruLab-Release.apk konumuna kopyalandı.');
  } catch (err) {
    console.warn('SoruLab: APK kopyalanamadı, GitHub Releases linki kullanılacak:', err.message);
  }
}

const htmlContent = `<!DOCTYPE html>
<html lang="tr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>SoruLab - YKS Akıllı PDF Kesici & Soru Havuzu</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;600;700;800&display=swap" rel="stylesheet">
  <style>
    :root {
      --primary: #1E3A8A;
      --primary-accent: #2563EB;
      --bg: #F8FAFD;
      --surface: #FFFFFF;
      --text-main: #0F172A;
      --text-muted: #64748B;
      --border: #E2E8F0;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: 'Plus Jakarta Sans', -apple-system, sans-serif;
      background: var(--bg);
      color: var(--text-main);
      min-height: 100vh;
      display: flex;
      flex-direction: column;
    }
    header {
      background: var(--surface);
      border-bottom: 1px solid var(--border);
      padding: 18px 24px;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 12px;
      font-weight: 800;
      font-size: 20px;
      color: var(--primary);
    }
    .brand-badge {
      background: #EFF6FF;
      color: var(--primary-accent);
      padding: 4px 10px;
      border-radius: 8px;
      font-size: 12px;
      font-weight: 700;
      border: 1px solid #BFDBFE;
    }
    main {
      flex: 1;
      max-width: 1000px;
      margin: 0 auto;
      padding: 48px 20px;
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
    }
    .hero-chip {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      background: #EFF6FF;
      border: 1px solid #BFDBFE;
      color: var(--primary);
      padding: 6px 16px;
      border-radius: 999px;
      font-size: 13px;
      font-weight: 700;
      margin-bottom: 20px;
    }
    h1 {
      font-size: clamp(32px, 5vw, 48px);
      font-weight: 800;
      line-height: 1.2;
      color: var(--text-main);
      margin-bottom: 16px;
      max-width: 760px;
    }
    h1 span {
      background: linear-gradient(135deg, #1E3A8A, #2563EB, #38BDF8);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
    p.lead {
      font-size: 17px;
      color: var(--text-muted);
      max-width: 640px;
      line-height: 1.6;
      margin-bottom: 32px;
    }
    .action-group {
      display: flex;
      flex-wrap: wrap;
      gap: 14px;
      justify-content: center;
      margin-bottom: 48px;
    }
    .btn {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      padding: 14px 28px;
      border-radius: 14px;
      font-weight: 700;
      font-size: 15px;
      text-decoration: none;
      transition: all 0.2s ease;
    }
    .btn-primary {
      background: var(--primary);
      color: white;
      box-shadow: 0 4px 14px rgba(30, 58, 138, 0.25);
    }
    .btn-primary:hover {
      background: #172554;
      transform: translateY(-2px);
    }
    .btn-secondary {
      background: var(--surface);
      color: var(--text-main);
      border: 1px solid var(--border);
    }
    .btn-secondary:hover {
      background: #F1F5F9;
      transform: translateY(-2px);
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 20px;
      width: 100%;
      text-align: left;
      margin-top: 10px;
    }
    .card {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 20px;
      padding: 24px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.03);
    }
    .card-icon {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      background: #EFF6FF;
      color: var(--primary);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
      margin-bottom: 16px;
    }
    .card h3 {
      font-size: 17px;
      font-weight: 800;
      margin-bottom: 8px;
      color: var(--text-main);
    }
    .card p {
      font-size: 14px;
      color: var(--text-muted);
      line-height: 1.5;
    }
    .notice {
      margin-top: 48px;
      background: #FEF3C7;
      border: 1px solid #FDE68A;
      color: #92400E;
      padding: 16px 20px;
      border-radius: 14px;
      font-size: 13.5px;
      line-height: 1.5;
      max-width: 700px;
    }
    footer {
      border-top: 1px solid var(--border);
      padding: 24px;
      text-align: center;
      font-size: 13px;
      color: var(--text-muted);
      background: var(--surface);
    }
  </style>
</head>
<body>
  <header>
    <div class="brand">
      <span>📐 SoruLab</span>
      <span class="brand-badge">Android v1.1.5</span>
    </div>
    <a href="https://github.com/Furknsss/SoruLab" class="btn btn-secondary" style="padding: 8px 16px; font-size: 13px;">GitHub Reposu</a>
  </header>

  <main>
    <div class="hero-chip">✨ YKS Odaklı Yeni Nesil Soru Çözüm Stüdyosu</div>
    <h1>ÖSYM & YKS Soru Havuzu <span>Gerçek PDF'lerle</span> Cebinizde.</h1>
    <p class="lead">3D, Bilgi Sarmal, Apotemi, Karekök soru bankalarınızı tek tıkla aktarın. Ultra-HD 300 DPI otomatik soru dekupeleme, S-Pen canlı yazı tahtası ve akıllı konu sınıflandırması.</p>

    <div class="action-group">
      <a href="${apkDownloadHref}" class="btn btn-primary">
        📲 Android APK'yı İndir (.apk)
      </a>
      <a href="https://github.com/Furknsss/SoruLab" class="btn btn-secondary">
        ⭐ GitHub'da İncele
      </a>
    </div>

    <div class="grid">
      <div class="card">
        <div class="card-icon">📄</div>
        <h3>Ultra-HD Akıllı PDF Kesici</h3>
        <p>PDF sayfalarındaki soruları numaralarından bağımsız, saf soru gövdesi olarak 300 DPI netlikle keser ve arkaplanı bembeyaz temizler.</p>
      </div>
      <div class="card">
        <div class="card-icon">🎯</div>
        <h3>YKS Müfredat Eşleme & İnceleme</h3>
        <p>Her soruyu doğru ders ve konuya eşler. AYT Matematik soruları asla Fizik'e karışmaz. Şüpheli sorular "İnceleme Bekliyor" etiketiyle korunur.</p>
      </div>
      <div class="card">
        <div class="card-icon">🎨</div>
        <h3>Çoklu Yayın Karıştırma</h3>
        <p>Testlerde tek bir yayına bağlı kalmaz; 3D, Bilgi Sarmal, Apotemi gibi farklı kaliteli kaynakları dengeli karıştırarak gerçek sınav deneyimi sunar.</p>
      </div>
      <div class="card">
        <div class="card-icon">✍️</div>
        <h3>S-Pen & Canlı Çözüm Tahtası</h3>
        <p>Samsung S-Pen ve dokunmatik ekranlar için 60 FPS pürüzsüz yazı tahtası, çift kolonlu kitap görünümü ve adım adım çözüm katmanı.</p>
      </div>
    </div>

    <div class="notice">
      ℹ️ <strong>Platform Bilgisi:</strong> SoruLab saf bir native <strong>Android (Kotlin & Jetpack Compose)</strong> uygulamasıdır. Vercel üzerinde bu web vitrini yayınlanır; Android APK derlemesi ise GitHub Actions CI ve Google AI Studio tarafından tam otomatik olarak üretilmektedir.
    </div>
  </main>

  <footer>
    SoruLab &copy; 2026 • YKS (TYT/AYT) Öğrencileri İçin Özel Olarak Geliştirilmiştir
  </footer>
</body>
</html>`;

fs.writeFileSync(path.join(publicDir, 'index.html'), htmlContent, 'utf-8');
console.log('SoruLab: Vercel web vitrini public/index.html başarıyla oluşturuldu.');
