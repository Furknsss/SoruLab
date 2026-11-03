package com.example.util

import androidx.compose.ui.input.pointer.PointerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StylusInputPolicyTest {
    @Test
    fun `smart mode rejects fingers and palms while allowing S Pen`() {
        assertTrue(StylusInputPolicy.canInk(PointerType.Stylus, false))
        assertFalse(StylusInputPolicy.canInk(PointerType.Touch, false))
        assertFalse(StylusInputPolicy.canInk(PointerType.Mouse, false))
    }

    @Test
    fun `drawing mode accepts one finger and S Pen but not mouse`() {
        assertTrue(StylusInputPolicy.canInk(PointerType.Stylus, true))
        assertTrue(StylusInputPolicy.canInk(PointerType.Touch, true))
        assertFalse(StylusInputPolicy.canInk(PointerType.Mouse, true))
    }

    @Test
    fun `two fingers zoom only without a stylus down`() {
        assertFalse(StylusInputPolicy.usePinch(1, false))
        assertTrue(StylusInputPolicy.usePinch(2, false))
        assertTrue(StylusInputPolicy.usePinch(3, false))
        assertFalse(StylusInputPolicy.usePinch(2, true))
    }

    @Test
    fun `physical pressure is bounded and invalid readings use normal width`() {
        assertEquals(1f, StylusInputPolicy.pressureWidthFactor(0f), 0.001f)
        assertEquals(1f, StylusInputPolicy.pressureWidthFactor(Float.NaN), 0.001f)
        assertEquals(1f, StylusInputPolicy.pressureWidthFactor(Float.POSITIVE_INFINITY), 0.001f)
        assertTrue(StylusInputPolicy.pressureWidthFactor(0.1f) < StylusInputPolicy.pressureWidthFactor(0.9f))
        assertTrue(StylusInputPolicy.pressureWidthFactor(100f) <= 1.46f)
    }

    @Test
    fun `page zero retains legacy ink and later pages have separate drawing keys`() {
        assertEquals("full_page_bookId", StylusInputPolicy.canvasKey("bookId", 0))
        assertEquals("full_page_bookId_page_1", StylusInputPolicy.canvasKey("bookId", 1))
        assertEquals("full_page_bookId_page_12", StylusInputPolicy.canvasKey("bookId", 12))
        assertFalse(StylusInputPolicy.canvasKey("bookId", 1) == StylusInputPolicy.canvasKey("bookId", 2))
    }
}
