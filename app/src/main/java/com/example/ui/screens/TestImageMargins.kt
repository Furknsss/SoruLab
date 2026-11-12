package com.example.ui.screens

import android.graphics.Bitmap

/** Display-only crop: preserve every visible mark and leave the stored original intact. */
internal fun trimTestImageMargins(source: Bitmap): Bitmap {
    val pixels = IntArray(source.width * source.height)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    var left = source.width
    var top = source.height
    var right = -1
    var bottom = -1
    for (y in 0 until source.height) {
        for (x in 0 until source.width) {
            val pixel = pixels[y * source.width + x]
            val alpha = pixel ushr 24
            if (alpha > 32 && ((pixel shr 16 and 255) < 240 ||
                    (pixel shr 8 and 255) < 240 || (pixel and 255) < 240)) {
                left = minOf(left, x)
                right = maxOf(right, x)
                top = minOf(top, y)
                bottom = maxOf(bottom, y)
            }
        }
    }
    if (right < left || bottom < top) return source
    val padding = (source.width / 100).coerceAtLeast(4)
    left = (left - padding).coerceAtLeast(0)
    top = (top - padding).coerceAtLeast(0)
    right = (right + padding + 1).coerceAtMost(source.width)
    bottom = (bottom + padding + 1).coerceAtMost(source.height)
    if (left == 0 && top == 0 && right == source.width && bottom == source.height) return source
    return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
}
