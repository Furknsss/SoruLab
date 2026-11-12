package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TestImageMarginsTest {
    @Test fun `display trim removes white borders but preserves isolated punctuation and original`() {
        val source = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.WHITE)
        source.setPixel(25, 40, Color.BLACK)
        source.setPixel(170, 250, Color.GRAY)
        val cropped = trimTestImageMargins(source)
        assertEquals(154, cropped.width)
        assertEquals(219, cropped.height)
        assertEquals(Color.BLACK, cropped.getPixel(4, 4))
        assertEquals(Color.GRAY, cropped.getPixel(149, 214))
        assertEquals(200, source.width)
        assertEquals(300, source.height)
        assertFalse(source.isRecycled)
    }

    @Test fun `blank images and edge to edge diagrams retain their dimensions`() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.WHITE)
        assertSame(source, trimTestImageMargins(source))
        source.setPixel(0, 0, Color.BLACK)
        source.setPixel(99, 99, Color.BLACK)
        assertSame(source, trimTestImageMargins(source))
    }
}
