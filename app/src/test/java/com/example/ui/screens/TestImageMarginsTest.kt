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
    @Test fun thinMarksAtEveryEdgeOfLongScanSurviveMarginTrimming() {
        val source = Bitmap.createBitmap(1200, 4000, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.WHITE)
        source.setPixel(3, 2000, Color.BLACK)
        source.setPixel(1196, 2000, Color.BLACK)
        source.setPixel(600, 3, Color.GRAY)
        source.setPixel(600, 3996, Color.GRAY)
        val cropped = trimTestImageMargins(source)
        assertSame("No edge mark may be lost on a large scan", source, cropped)
        assertEquals(Color.GRAY, source.getPixel(600, 3996))
    }

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
