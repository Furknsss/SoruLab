package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Separate from the source-question image, which already has printed choices.
 * This is a fresh, checked scientific illustration; the generated question
 * and options remain native text and selectable.
 */
@Composable
internal fun TwinGeneratedIllustration(
    imageBase64: String,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(imageBase64) {
        runCatching {
            require(imageBase64.length in 3000..2_100_000)
            val raw = Base64.decode(imageBase64, Base64.NO_WRAP)
            require(raw.size in 2_000..1_200_000)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
            require(bounds.outWidth in 300..3000 && bounds.outHeight in 240..3000)
            require(bounds.outWidth.toLong() * bounds.outHeight <= 8_000_000)
            BitmapFactory.decodeByteArray(raw, 0, raw.size)
                ?.asImageBitmap()
                ?: error("Generated science image cannot be decoded")
        }.getOrNull()
    } ?: return
    Image(
        bitmap = bitmap,
        contentDescription = "İkiz soruya ait yeniden oluşturulmuş bilimsel şekil",
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp, max = 380.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Fit
    )
}
