package com.denemelab.preview;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(9, 14, 29);
    private static final int SURFACE = Color.rgb(19, 27, 47);
    private static final int SURFACE2 = Color.rgb(26, 37, 59);
    private static final int WHITE = Color.rgb(245, 249, 255);
    private static final int MUTED = Color.rgb(145, 160, 183);
    private static final int MINT = Color.rgb(110, 244, 203);
    private static final int LAVENDER = Color.rgb(171, 150, 255);
    private LinearLayout root;
    private LinearLayout page;
    private int currentPage = 0;
    private String selectedPdf;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        render();
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private TextView label(String text, int sp, int color, boolean heavy) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        if (heavy) v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        else v.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return v;
    }

    private void gap(LinearLayout in, int height) {
        View spacer = new View(this);
        in.addView(spacer, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private LinearLayout panel() {
        LinearLayout box = vertical();
        box.setBackground(shape(SURFACE, 22));
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        return box;
    }

    private void headline(String text, String subtitle) {
        page.addView(label(text, 27, WHITE, true));
        gap(page, 7);
        page.addView(label(subtitle, 13, MUTED, false));
        gap(page, 24);
    }

    private void section(String name, String hint) {
        LinearLayout r = horizontal();
        r.addView(label(name, 18, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        if (hint != null) r.addView(label(hint, 11, MINT, false));
        page.addView(r);
        gap(page, 14);
    }

    private void note(LinearLayout l, String s) {
        TextView t = label(s, 12, MUTED, false);
        t.setLineSpacing(dp(3), 1);
        l.addView(t);
    }

    private void action(LinearLayout target, String title, boolean prominent, Runnable callback) {
        TextView v = label(title, 15, prominent ? BG : WHITE, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(16), dp(16), dp(16), dp(16));
        v.setBackground(shape(prominent ? MINT : SURFACE2, 16));
        v.setOnClickListener(view -> callback.run());
        target.addView(v, new LinearLayout.LayoutParams(-1, -2));
    }

    private void render() {
        root = vertical();
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);
        setContentView(root);

        LinearLayout top = horizontal();
        top.setPadding(dp(22), dp(22), dp(22), dp(12));
        TextView logo = label("◈", 29, MINT, true);
        top.addView(logo);
        LinearLayout brand = vertical();
        brand.setPadding(dp(10), 0, 0, 0);
        brand.addView(label("DENEMELAB", 14, WHITE, true));
        brand.addView(label("YKS PERFORMANCE", 9, MUTED, false));
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView preview = label("PREVIEW", 10, MINT, true);
        preview.setBackground(shape(SURFACE2, 11));
        preview.setPadding(dp(12), dp(8), dp(12), dp(8));
        top.addView(preview);
        root.addView(top);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        page = vertical();
        page.setPadding(dp(22), dp(16), dp(22), dp(30));
        scroller.addView(page);
        root.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));

        if (currentPage == 0) home();
        else if (currentPage == 1) exams();
        else if (currentPage == 2) analytics();
        else profile();

        navigation();
    }

    private void home() {
        headline("Merhaba, şampiyon! ✨", "Bugün hedefine biraz daha yaklaş.");
        LinearLayout hero = vertical();
        GradientDrawable grad = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(53, 69, 127), Color.rgb(24, 77, 88)});
        grad.setCornerRadius(dp(25));
        hero.setBackground(grad);
        hero.setPadding(dp(24), dp(24), dp(24), dp(24));
        hero.addView(label("DENEME PERFORMANSI · ÖRNEK", 11, MINT, true));
        gap(hero, 21);
        hero.addView(label("78,50", 47, WHITE, true));
        gap(hero, 2);
        hero.addView(label("TYT toplam net", 15, WHITE, false));
        gap(hero, 18);
        TextView chip = label("↗  +4,75 net  /  geçen deneme", 12, MINT, true);
        chip.setBackground(shape(Color.rgb(20, 56, 69), 12));
        chip.setPadding(dp(12), dp(10), dp(12), dp(10));
        hero.addView(chip, new LinearLayout.LayoutParams(-2, -2));
        page.addView(hero);
        gap(page, 15);

        LinearLayout stats = horizontal();
        LinearLayout first = metric("12", "Deneme", LAVENDER);
        LinearLayout second = metric("%68", "Hedefe ilerleme", MINT);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(110), 1);
        p.rightMargin = dp(7);
        stats.addView(first, p);
        LinearLayout.LayoutParams q = new LinearLayout.LayoutParams(0, dp(110), 1);
        q.leftMargin = dp(7);
        stats.addView(second, q);
        page.addView(stats);
        gap(page, 28);

        section("Net gelişimi", "Tümünü gör");
        LinearLayout graphCard = panel();
        graphCard.addView(label("Son 7 deneme · Demo verileri", 12, MUTED, false));
        graphCard.addView(new TrendView(), new LinearLayout.LayoutParams(-1, dp(166)));
        page.addView(graphCard);
        gap(page, 27);
        section("Hızlı işlemler", null);
        action(page, "＋   Yeni deneme ekle", true, this::selectPdf);
        gap(page, 11);
        action(page, "◉   Analiz ekranını keşfet", false, () -> { currentPage = 2; render(); });
        gap(page, 17);
        note(page, "Bu sürüm arayüz önizlemesidir. Sayılar temsili olup henüz hesaplara kaydedilmez.");
    }

    private LinearLayout metric(String value, String title, int accent) {
        LinearLayout card = panel();
        card.setPadding(dp(17), dp(18), dp(12), dp(12));
        card.addView(label(value, 28, accent, true));
        gap(card, 10);
        card.addView(label(title, 12, MUTED, false));
        return card;
    }

    private void exams() {
        headline("Denemelerim", "Her deneme, daha güçlü bir başlangıç.");
        action(page, "＋   PDF denemesi seç", true, this::selectPdf);
        gap(page, 22);
        if (selectedPdf != null) {
            LinearLayout upload = panel();
            upload.addView(label("Seçilen dosya", 12, MINT, true));
            gap(upload, 9);
            upload.addView(label(selectedPdf, 15, WHITE, true));
            gap(upload, 10);
            note(upload, "Dosya seçimi çalışır. PDF analizi bu iskelet sürümünde etkin değildir.");
            page.addView(upload);
            gap(page, 21);
        }
        section("Örnek deneme geçmişi", "Demo");
        examRow("12", "TYT Genel Deneme", "78,50 net · 85 D / 26 Y / 9 B", MINT);
        gap(page, 11);
        examRow("11", "TYT Genel Deneme", "73,75 net · Örnek kayıt", LAVENDER);
        gap(page, 11);
        examRow("10", "AYT Matematik", "28,25 net · Örnek kayıt", LAVENDER);
        gap(page, 21);
        note(page, "Deneme geçmişi örnek verilerden oluşur. Gerçek kayıt ve senkronizasyon sonraki sürümde.");
    }

    private void examRow(String num, String name, String detail, int color) {
        LinearLayout row = horizontal();
        row.setBackground(shape(SURFACE, 20));
        row.setPadding(dp(16), dp(17), dp(16), dp(17));
        TextView icon = label(num, 16, color, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(SURFACE2, 12));
        row.addView(icon, new LinearLayout.LayoutParams(dp(45), dp(45)));
        LinearLayout text = vertical();
        text.setPadding(dp(14), 0, 0, 0);
        text.addView(label(name, 15, WHITE, true));
        gap(text, 7);
        text.addView(label(detail, 12, MUTED, false));
        row.addView(text);
        page.addView(row);
    }

    private void analytics() {
        headline("Akıllı Analiz", "Potansiyelini verilerle keşfet.");
        LinearLayout header = panel();
        header.addView(label("◈   YAPAY ZEKÂ ANALİZİ", 12, MINT, true));
        gap(header, 12);
        header.addView(label("Güçlü yanların. Gelişim alanların.", 21, WHITE, true));
        gap(header, 11);
        note(header, "Bu ekranda yalnızca premium analiz tasarımının örnek görünümü yer alıyor.");
        page.addView(header);
        gap(page, 24);
        section("Örnek konu başarıları", "Demo verileri");
        LinearLayout skills = panel();
        skill(skills, "Paragraf", 82, MINT);
        gap(skills, 19);
        skill(skills, "Problemler", 41, LAVENDER);
        gap(skills, 19);
        skill(skills, "Geometri", 62, MINT);
        gap(skills, 19);
        skill(skills, "Elektrik", 35, LAVENDER);
        page.addView(skills);
        gap(page, 22);
        LinearLayout insight = panel();
        insight.addView(label("✦   ANALİZ ÖNİZLEMESİ", 12, MINT, true));
        gap(insight, 12);
        insight.addView(label("Problem sorularında tekrar yapmak faydalı olabilir.", 17, WHITE, true));
        gap(insight, 10);
        note(insight, "Bu metin örnektir, yapay zekâ tarafından üretilmemiştir.");
        page.addView(insight);
    }

    private void skill(LinearLayout box, String title, int value, int tint) {
        LinearLayout row = horizontal();
        row.addView(label(title, 14, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(label("%" + value, 13, tint, true));
        box.addView(row);
        gap(box, 9);
        LinearLayout track = horizontal();
        track.setBackground(shape(SURFACE2, 10));
        LinearLayout bar = new LinearLayout(this);
        bar.setBackground(shape(tint, 10));
        track.addView(bar, new LinearLayout.LayoutParams(0, dp(8), value));
        track.addView(new View(this), new LinearLayout.LayoutParams(0, dp(8), 100 - value));
        box.addView(track, new LinearLayout.LayoutParams(-1, dp(8)));
    }

    private void profile() {
        headline("Hesabım", "Öğrenme yolculuğunu yönet.");
        LinearLayout card = panel();
        card.addView(label("◉", 44, MINT, true));
        gap(card, 13);
        card.addView(label("DenemeLab Preview", 20, WHITE, true));
        gap(card, 7);
        note(card, "Yerel önizleme • Giriş ve bulut bağlantısı bu sürümde bulunmuyor.");
        page.addView(card);
        gap(page, 20);
        section("Uygulama hakkında", null);
        LinearLayout details = panel();
        details.addView(label("Versiyon 0.1.0", 15, WHITE, true));
        gap(details, 11);
        note(details, "Bağımsız Android arayüz prototipi. Tam YKS deneme analizi sistemi değildir.");
        page.addView(details);
    }

    private void selectPdf() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("application/pdf");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, 100);
        } catch (Exception e) {
            Toast.makeText(this, "PDF seçici açılamadı.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            selectedPdf = uri.getLastPathSegment();
            if (selectedPdf == null) selectedPdf = "Seçilen PDF";
            currentPage = 1;
            render();
            Toast.makeText(this, "PDF seçildi. Analiz sonraki sürümde.", Toast.LENGTH_LONG).show();
        }
    }

    private void navigation() {
        LinearLayout bar = horizontal();
        bar.setBackgroundColor(SURFACE);
        bar.setPadding(dp(8), dp(12), dp(8), dp(12));
        String[] names = {"Ana Sayfa", "Denemeler", "Analiz", "Hesabım"};
        String[] icons = {"⌂", "▤", "▥", "◉"};
        for (int i = 0; i < names.length; i++) {
            final int target = i;
            LinearLayout cell = vertical();
            cell.setGravity(Gravity.CENTER);
            TextView glyph = label(icons[i], 24, currentPage == i ? MINT : MUTED, true);
            glyph.setGravity(Gravity.CENTER);
            cell.addView(glyph);
            gap(cell, 5);
            TextView t = label(names[i], 10, currentPage == i ? MINT : MUTED, currentPage == i);
            t.setGravity(Gravity.CENTER);
            cell.addView(t);
            cell.setOnClickListener(v -> { currentPage = target; render(); });
            bar.addView(cell, new LinearLayout.LayoutParams(0, dp(58), 1));
        }
        root.addView(bar);
    }

    private class TrendView extends View {
        TrendView() { super(MainActivity.this); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            Paint p = new Paint(3);
            p.setStrokeWidth(dp(1));
            p.setColor(Color.rgb(49, 59, 80));
            for (int i = 1; i <= 3; i++) {
                float y = h * i / 4f;
                canvas.drawLine(0, y, w, y, p);
            }
            float[] numbers = {42, 53, 49, 66, 62, 75, 83};
            Path line = new Path();
            for (int i = 0; i < numbers.length; i++) {
                float x = w * i / (numbers.length - 1);
                float y = h - dp(13) - (h - dp(27)) * (numbers[i] / 100f);
                if (i == 0) line.moveTo(x, y); else line.lineTo(x, y);
            }
            p.setShader(new LinearGradient(0, 0, w, h, MINT, LAVENDER, Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(3));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            canvas.drawPath(line, p);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(MINT);
            float lastY = h - dp(13) - (h - dp(27)) * 0.83f;
            canvas.drawCircle(w - dp(3), lastY, dp(5), p);
        }
    }
}
