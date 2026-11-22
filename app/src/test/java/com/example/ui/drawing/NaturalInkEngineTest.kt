package com.example.ui.drawing

import androidx.compose.ui.geometry.Offset
import com.example.viewmodel.DrawStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM Unit tests for SoruLab Natural Ink & Bézier Pen Engine.
 * Tests mathematical mappings, Bézier spline calculations, jitter filtering, and smart shapes.
 */
class NaturalInkEngineTest {

    @Test
    fun penThicknessToPx_boundaryAndPresetValues() {
        // Minimum (0) and Maximum (100) boundaries
        assertEquals(1.5f, penThicknessToPx(0f), 0.001f)
        assertEquals(28.0f, penThicknessToPx(100f), 0.001f)

        // Clamping check
        assertEquals(1.5f, penThicknessToPx(-10f), 0.001f)
        assertEquals(28.0f, penThicknessToPx(150f), 0.001f)

        // Presets: INCE (15), ORTA (35), KALIN (65), VURGU (95)
        val incePx = penThicknessToPx(PenPreset.INCE.thicknessValue)
        val ortaPx = penThicknessToPx(PenPreset.ORTA.thicknessValue)
        val kalinPx = penThicknessToPx(PenPreset.KALIN.thicknessValue)
        val vurguPx = penThicknessToPx(PenPreset.VURGU.thicknessValue)

        assertTrue("Ince must be between 5.0 and 6.0 px", incePx in 5.0f..6.0f)
        assertTrue("Orta must be between 10.0 and 11.5 px", ortaPx in 10.0f..11.5f)
        assertTrue("Kalin must be between 18.0 and 19.5 px", kalinPx in 18.0f..19.5f)
        assertTrue("Vurgu must be between 26.0 and 27.5 px", vurguPx in 26.0f..27.5f)

        // Monotonic progression
        assertTrue(incePx < ortaPx)
        assertTrue(ortaPx < kalinPx)
        assertTrue(kalinPx < vurguPx)
    }

    @Test
    fun penThicknessToPx_highlighterMultiplier() {
        val basePx = penThicknessToPx(35f, isHighlighter = false)
        val highPx = penThicknessToPx(35f, isHighlighter = true)

        assertEquals(basePx * 2.5f, highPx, 0.001f)
    }

    @Test
    fun thicknessAndStrokeSizeIndex_bidirectionalMapping() {
        // Thickness to Index
        assertEquals(0, thicknessToStrokeSizeIndex(10f))
        assertEquals(0, thicknessToStrokeSizeIndex(24f))
        assertEquals(1, thicknessToStrokeSizeIndex(25f))
        assertEquals(1, thicknessToStrokeSizeIndex(35f))
        assertEquals(1, thicknessToStrokeSizeIndex(49f))
        assertEquals(2, thicknessToStrokeSizeIndex(50f))
        assertEquals(2, thicknessToStrokeSizeIndex(70f))
        assertEquals(2, thicknessToStrokeSizeIndex(100f))

        // Index to Thickness
        assertEquals(15f, strokeSizeIndexToThickness(0), 0.001f)
        assertEquals(35f, strokeSizeIndexToThickness(1), 0.001f)
        assertEquals(65f, strokeSizeIndexToThickness(2), 0.001f)

        // Round-trip consistency for default presets
        assertEquals(0, thicknessToStrokeSizeIndex(strokeSizeIndexToThickness(0)))
        assertEquals(1, thicknessToStrokeSizeIndex(strokeSizeIndexToThickness(1)))
        assertEquals(2, thicknessToStrokeSizeIndex(strokeSizeIndexToThickness(2)))
    }

    @Test
    fun filterPointsForSmoothInk_removesMicroJitter() {
        val empty = filterPointsForSmoothInk(emptyList())
        assertTrue(empty.isEmpty())

        val single = filterPointsForSmoothInk(listOf(Offset(10f, 10f)))
        assertEquals(1, single.size)

        val twoPoints = filterPointsForSmoothInk(listOf(Offset(10f, 10f), Offset(10.5f, 10.5f)))
        assertEquals(2, twoPoints.size)

        // Jitter points closer than 1.8px between start and end
        val jittery = listOf(
            Offset(10f, 10f),
            Offset(10.2f, 10.3f), // Jitter (< 1.8px from 10,10) -> removed
            Offset(10.4f, 10.5f), // Jitter -> removed
            Offset(15.0f, 15.0f), // Meaningful move (> 1.8px) -> kept
            Offset(15.3f, 15.2f), // Jitter (< 1.8px from 15,15) -> removed
            Offset(30.0f, 30.0f)  // End point -> always kept
        )

        val filtered = filterPointsForSmoothInk(jittery, minDistancePx = 1.8f)
        assertEquals(3, filtered.size)
        assertEquals(Offset(10f, 10f), filtered[0])
        assertEquals(Offset(15f, 15f), filtered[1])
        assertEquals(Offset(30f, 30f), filtered[2])
    }

    @Test
    fun calculateCatmullRomToBezier_smoothInterpolation() {
        // Less than 2 points -> empty segments
        assertTrue(calculateCatmullRomToBezier(emptyList()).isEmpty())
        assertTrue(calculateCatmullRomToBezier(listOf(Offset(10f, 10f))).isEmpty())

        // 3 Points -> 2 Cubic Bézier segments
        val pts = listOf(
            Offset(0f, 0f),
            Offset(50f, 100f),
            Offset(100f, 0f)
        )
        val segments = calculateCatmullRomToBezier(pts)
        assertEquals(2, segments.size)

        // First segment starts at pts[0] and ends at pts[1]
        val seg0 = segments[0]
        assertEquals(pts[0], seg0.start)
        assertEquals(pts[1], seg0.end)
        assertNotNull(seg0.control1)
        assertNotNull(seg0.control2)

        // Second segment starts at pts[1] and ends at pts[2]
        val seg1 = segments[1]
        assertEquals(pts[1], seg1.start)
        assertEquals(pts[2], seg1.end)

        // Verify C1 smooth curvature transition at pts[1]:
        // In Catmull-Rom, the outgoing tangent vector at pts[1] matches the incoming tangent
        val incomingTangent = seg0.end - seg0.control2
        val outgoingTangent = seg1.control1 - seg1.start
        assertEquals(incomingTangent.x, outgoingTangent.x, 0.001f)
        assertEquals(incomingTangent.y, outgoingTangent.y, 0.001f)
    }

    @Test
    fun detectSmartShape_closedLoopAndStraightLine() {
        // Closed loop (start and end close together < 55px) -> snaps to bounding rectangle
        val closedLoop = listOf(
            Offset(10f, 10f),
            Offset(100f, 12f),
            Offset(98f, 80f),
            Offset(12f, 78f),
            Offset(11f, 11f) // Distance to (10,10) is ~1.4px (< 55px)
        )
        val shapeRect = detectSmartShape(closedLoop)
        assertNotNull(shapeRect)
        assertTrue(shapeRect!!.isClosedRect)
        assertEquals(Offset(10f, 10f), shapeRect.rectLeftTop)
        assertEquals(Offset(100f, 80f), shapeRect.rectRightBottom)

        // Open stroke with smart shape -> straight line from first to last point
        val openStroke = listOf(
            Offset(10f, 10f),
            Offset(45f, 55f),
            Offset(80f, 120f),
            Offset(200f, 250f)
        )
        val shapeLine = detectSmartShape(openStroke)
        assertNotNull(shapeLine)
        assertFalse(shapeLine!!.isClosedRect)
        assertEquals(Offset(10f, 10f), shapeLine.lineStart)
        assertEquals(Offset(200f, 250f), shapeLine.lineEnd)
    }

    @Test
    fun drawStroke_supportsSingleDotTaps() {
        // Ensure DrawStroke correctly supports 1-point taps (period, formula dot, umlaut)
        val dotStroke = DrawStroke(
            points = listOf(Offset(45f, 90f)),
            colorHex = 0xFF1D4ED8L,
            strokeWidthPx = penThicknessToPx(35f),
            isSmartShape = false,
            isEraser = false,
            isHighlighter = false
        )

        assertEquals(1, dotStroke.points.size)
        assertEquals(0xFF1D4ED8L, dotStroke.colorHex)
        assertFalse(dotStroke.isEraser)
        assertFalse(dotStroke.isHighlighter)
    }

    @Test
    fun drawStroke_eraserAndHighlighterProperties() {
        val highlighterStroke = DrawStroke(
            points = listOf(Offset(10f, 10f), Offset(50f, 50f)),
            colorHex = 0xFFFACC15L,
            strokeWidthPx = penThicknessToPx(50f, isHighlighter = true),
            isSmartShape = false,
            isEraser = false,
            isHighlighter = true
        )

        assertTrue(highlighterStroke.isHighlighter)
        assertFalse(highlighterStroke.isEraser)
        assertEquals(penThicknessToPx(50f, isHighlighter = true), highlighterStroke.strokeWidthPx, 0.001f)

        val eraserStroke = DrawStroke(
            points = listOf(Offset(20f, 20f), Offset(40f, 40f)),
            colorHex = 0x00000000L,
            strokeWidthPx = 38f,
            isSmartShape = false,
            isEraser = true,
            isHighlighter = false
        )

        assertTrue(eraserStroke.isEraser)
        assertEquals(0x00000000L, eraserStroke.colorHex)
    }

    @Test
    fun hasStylusPressureVariation_detectsVaryingVsUniformSamples() {
        assertFalse(hasStylusPressureVariation(emptyList()))
        assertFalse(hasStylusPressureVariation(listOf(1.0f)))
        assertFalse(hasStylusPressureVariation(listOf(1.0f, 1.0f, 1.0f)))
        assertFalse(hasStylusPressureVariation(listOf(0.50f, 0.51f, 0.52f)))
        assertTrue(hasStylusPressureVariation(listOf(0.25f, 0.60f, 0.85f)))
    }
}
