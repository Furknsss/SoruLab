package com.example.util

/** Bound memory while retaining reading width for long, narrow question scans. */
internal fun questionBitmapSampleSize(width: Int, height: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (sample < (1 shl 29)) {
        val w = (width.toLong() + sample - 1) / sample
        val h = (height.toLong() + sample - 1) / sample
        if (w <= 1440 && w * h <= 4_000_000L) break
        sample *= 2
    }
    return sample
}
