package com.example.util

import androidx.compose.ui.input.pointer.PointerType

/**
 * Testable rules shared by the tablet ink surfaces. Prefer S Pen over touch
 * during mixed-input gestures; this prevents accidental palm marks.
 */
internal object StylusInputPolicy {
    fun canInk(type: PointerType, fingerDrawingEnabled: Boolean): Boolean =
        type == PointerType.Stylus ||
            (type == PointerType.Touch && fingerDrawingEnabled)

    fun usePinch(touchCount: Int, stylusPressed: Boolean): Boolean =
        touchCount >= 2 && !stylusPressed

    fun pressureWidthFactor(pressure: Float): Float {
        if (!pressure.isFinite() || pressure <= 0f) return 1f
        // A restrained physical response rather than exaggerated brush strokes.
        return (0.68f + pressure.coerceIn(0f, 1.5f) * 0.52f).coerceIn(0.68f, 1.46f)
    }

    /** Keep page zero's legacy key, so old drawings are never discarded. */
    fun canvasKey(testId: String, page: Int): String =
        if (page <= 0) "full_page_$testId" else "full_page_${testId}_page_$page"
}
