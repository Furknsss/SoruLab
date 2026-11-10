package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

/**
 * DEBUG ONLY. No Gemini/Nano Banana API call.
 * Exercises the exact Android bitmap decoder, native stem and five separate
 * answer choices with a generated (not source-crop) science illustration.
 */
class TwinIllustrationPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val illustration = createScienceFixture()
        setContent {
            MaterialTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF7F9FC))
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("SoruLab • Biyoloji İkiz Görsel Testi", fontWeight = FontWeight.Bold)
                    Text(
                        "Bitki yaprağının enine kesitinde aşağıdaki yapılar numaralandırılmıştır. " +
                        "Gaz alışverişi hangi yapı aracılığıyla doğrudan gerçekleşir?"
                    )
                    TwinGeneratedIllustration(illustration)
                    Text("A) Kütikula")
                    Text("B) Stoma")
                    Text("C) Ksilem")
                    Text("D) Floem")
                    Text("E) Kloroplast")
                    Text(
                        "Doğrulama: B. Stoma gaz alışverişinde görevlidir. " +
                        "Bu görsel yalnızca Compose görüntüleyici testidir; " +
                        "Nano Banana ile oluşturulmuş değildir."
                    )
                }
            }
        }
    }

    private fun createScienceFixture(): String {
        val image = Bitmap.createBitmap(900, 540, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(AndroidColor.WHITE)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(24, 42, 62)
            strokeWidth = 6f
            textSize = 40f
        }
        val leaf = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(160, 200, 165)
        }
        canvas.drawRoundRect(100f, 110f, 800f, 425f, 36f, 36f, leaf)
        canvas.drawLine(110f, 275f, 790f, 275f, ink)
        canvas.drawOval(382f, 395f, 520f, 451f, ink)
        canvas.drawOval(416f, 409f, 492f, 441f, leaf)
        canvas.drawText("Yaprak enine kesiti", 170f, 75f, ink)
        canvas.drawText("Stoma", 500f, 505f, ink)
        val stream = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, 86, stream)
        image.recycle()
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
