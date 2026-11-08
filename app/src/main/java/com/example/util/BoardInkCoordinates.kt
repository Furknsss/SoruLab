package com.example.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/** Inverse of the board's centered graphicsLayer, followed by document scroll. */
internal object BoardInkCoordinates {
    fun toDocument(
        viewportPoint: Offset,
        viewportSize: Size,
        scale: Float,
        pan: Offset,
        scrollY: Float = 0f
    ): Offset {
        val safeScale = scale.takeIf { it.isFinite() && it >= 1f } ?: 1f
        val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
        return center + (viewportPoint - center - pan) / safeScale + Offset(0f, scrollY)
    }
}
