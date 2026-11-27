package com.example.ui.drawing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.StylusInputPolicy
import com.example.viewmodel.DrawStroke
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ═════════════════════════════════════════════════════════════════════════
 * SORULAB NATURAL INK & BÉZIER PEN ENGINE
 * GoodNotes, Apple Notes ve Notability standartlarında:
 * 1. Pürüzsüz Catmull-Rom / Cubic Bézier enterpolasyonu
 * 2. Mikrotitreme (jitter) filtreleme ve doğal tükenmez/jel kalem mürekkep akışı
 * 3. Tek dokunuş (tap/dot) yuvarlak nokta desteği (nokta, virgül, üs, indis)
 * 4. S-Pen donanım basınç hassasiyeti (v1.4.9 pressureSamples tam uyumu)
 * 5. 0 - 100 arası pürüzsüz kalınlık eşlemesi & canlı interaktif önizleme
 * ═════════════════════════════════════════════════════════════════════════
 */

/**
 * Kalem kalınlığı hazır preset modelleri
 */
enum class PenPreset(
    val id: String,
    val label: String,
    val thicknessValue: Float,
    val tag: String
) {
    INCE("fine", "İnce", 15f, "pen_preset_ince"),
    ORTA("medium", "Orta", 35f, "pen_preset_orta"),
    KALIN("thick", "Kalın", 65f, "pen_preset_kalin"),
    VURGU("extra_thick", "Vurgu", 95f, "pen_preset_vurgu")
}

/**
 * Samsung Notes tarzı çift modlu silgi sistemi:
 * - [STROKE]: Çizgi Silgisi — Üzerine dokunulan/geçilen çizginin tamamını tek hamlede siler.
 * - [AREA]: Alan Silgisi — Yalnızca silgi dairesinin değdiği bölgeyi kısmi olarak temizler.
 */
enum class EraserMode(
    val title: String,
    val shortBadge: String,
    val subtitle: String
) {
    STROKE(
        title = "Çizgi Silgisi",
        shortBadge = "Çizgi",
        subtitle = "Dokunduğun çizginin tamamını tek hamlede siler"
    ),
    AREA(
        title = "Alan Silgisi",
        shortBadge = "Alan",
        subtitle = "Yalnızca silginin değdiği bölgeyi siler"
    )
}

enum class EraserPreset(
    val label: String,
    val thicknessValue: Float,
    val previewDp: Float,
    val tag: String
) {
    KUCUK("Küçük", 15f, 6f, "eraser_preset_kucuk"),
    ORTA("Orta", 42f, 10f, "eraser_preset_orta"),
    BUYUK("Büyük", 70f, 14f, "eraser_preset_buyuk"),
    COK_BUYUK("Çok Büyük", 95f, 18f, "eraser_preset_cok_buyuk")
}

fun eraserThicknessToPx(thicknessValue: Float): Float {
    val clamped = thicknessValue.coerceIn(0f, 100f)
    return 14f + (clamped / 100f) * 72f
}

fun eraserStrokeIndexToThickness(index: Int): Float = when (index.coerceIn(0, 2)) {
    0 -> 18f
    1 -> 42f
    else -> 76f
}

fun thicknessToEraserStrokeIndex(thicknessValue: Float): Int {
    val clamped = thicknessValue.coerceIn(0f, 100f)
    return when {
        clamped < 30f -> 0
        clamped < 62f -> 1
        else -> 2
    }
}

private fun pointToSegmentDistSq(p: Offset, a: Offset, b: Offset): Float {
    val vx = b.x - a.x
    val vy = b.y - a.y
    val lenSq = vx * vx + vy * vy
    if (lenSq <= 1e-4f) {
        val dx = p.x - a.x
        val dy = p.y - a.y
        return dx * dx + dy * dy
    }
    val t = (((p.x - a.x) * vx + (p.y - a.y) * vy) / lenSq).coerceIn(0f, 1f)
    val projX = a.x + t * vx
    val projY = a.y + t * vy
    val dx = p.x - projX
    val dy = p.y - projY
    return dx * dx + dy * dy
}

private fun ccw(a: Offset, b: Offset, c: Offset): Float =
    (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

private fun segmentsProperlyOrTouchIntersect(a1: Offset, a2: Offset, b1: Offset, b2: Offset): Boolean {
    val d1 = ccw(a1, a2, b1)
    val d2 = ccw(a1, a2, b2)
    val d3 = ccw(b1, b2, a1)
    val d4 = ccw(b1, b2, a2)
    return ((d1 >= 0f && d2 <= 0f) || (d1 <= 0f && d2 >= 0f)) &&
        ((d3 >= 0f && d4 <= 0f) || (d3 <= 0f && d4 >= 0f))
}

private fun segmentToSegmentDistSq(a1: Offset, a2: Offset, b1: Offset, b2: Offset): Float {
    if (segmentsProperlyOrTouchIntersect(a1, a2, b1, b2)) return 0f
    val d1 = pointToSegmentDistSq(a1, b1, b2)
    val d2 = pointToSegmentDistSq(a2, b1, b2)
    val d3 = pointToSegmentDistSq(b1, a1, a2)
    val d4 = pointToSegmentDistSq(b2, a1, a2)
    return minOf(minOf(d1, d2), minOf(d3, d4))
}

/**
 * Samsung Notes Çizgi Silgisi (Stroke Eraser) çarpışma motoru:
 * Hızlı el hareketlerinde bile segment kesişimini ve yarıçap temasını yakalayarak
 * dokunulan çizginin tamamını sıfır gecikmeyle tespit eder.
 */
fun doesEraserTouchStroke(
    stroke: DrawStroke,
    eraserPoints: List<Offset>,
    eraserWidthPx: Float
): Boolean {
    if (stroke.isEraser) return false
    val sPts = stroke.points
    if (sPts.isEmpty() || eraserPoints.isEmpty()) return false

    val hitRadius = (eraserWidthPx.coerceAtLeast(10f) * 0.5f) +
        (stroke.strokeWidthPx.coerceAtLeast(4f) * 0.5f) + 2f
    val hitRadiusSq = hitRadius * hitRadius

    var sMinX = Float.POSITIVE_INFINITY
    var sMaxX = Float.NEGATIVE_INFINITY
    var sMinY = Float.POSITIVE_INFINITY
    var sMaxY = Float.NEGATIVE_INFINITY
    for (p in sPts) {
        if (p.x < sMinX) sMinX = p.x
        if (p.x > sMaxX) sMaxX = p.x
        if (p.y < sMinY) sMinY = p.y
        if (p.y > sMaxY) sMaxY = p.y
    }

    var eMinX = Float.POSITIVE_INFINITY
    var eMaxX = Float.NEGATIVE_INFINITY
    var eMinY = Float.POSITIVE_INFINITY
    var eMaxY = Float.NEGATIVE_INFINITY
    for (p in eraserPoints) {
        if (p.x < eMinX) eMinX = p.x
        if (p.x > eMaxX) eMaxX = p.x
        if (p.y < eMinY) eMinY = p.y
        if (p.y > eMaxY) eMaxY = p.y
    }

    if (eMaxX < sMinX - hitRadius || eMinX > sMaxX + hitRadius ||
        eMaxY < sMinY - hitRadius || eMinY > sMaxY + hitRadius
    ) {
        return false
    }

    val sSegCount = (sPts.size - 1).coerceAtLeast(1)
    val eSegCount = (eraserPoints.size - 1).coerceAtLeast(1)
    for (ei in 0 until eSegCount) {
        val e1 = eraserPoints[ei]
        val e2 = eraserPoints.getOrElse(ei + 1) { e1 }
        val segMinX = minOf(e1.x, e2.x) - hitRadius
        val segMaxX = maxOf(e1.x, e2.x) + hitRadius
        val segMinY = minOf(e1.y, e2.y) - hitRadius
        val segMaxY = maxOf(e1.y, e2.y) + hitRadius
        if (segMaxX < sMinX || segMinX > sMaxX || segMaxY < sMinY || segMinY > sMaxY) continue

        for (si in 0 until sSegCount) {
            val s1 = sPts[si]
            val s2 = sPts.getOrElse(si + 1) { s1 }
            if (maxOf(s1.x, s2.x) < segMinX || minOf(s1.x, s2.x) > segMaxX ||
                maxOf(s1.y, s2.y) < segMinY || minOf(s1.y, s2.y) > segMaxY
            ) continue
            if (segmentToSegmentDistSq(e1, e2, s1, s2) <= hitRadiusSq) {
                return true
            }
        }
    }
    return false
}

/**
 * 0 - 100 arası slider değerini piksel bazlı çizim kalınlığına pürüzsüz dönüştürür.
 * 0 -> 1.5px (Ultra ince formül & indis kalemi)
 * 15 -> ~5.5px (İnce jel kalem)
 * 35 -> ~10.8px (Standart 0.7 tükenmez not kalemi)
 * 65 -> ~18.7px (Kalın çizim & alt başlık)
 * 95 -> ~26.7px (Vurgu & başlık kalemi)
 * 100 -> 28.0px
 */
fun penThicknessToPx(thicknessValue: Float, isHighlighter: Boolean = false): Float {
    val clamped = thicknessValue.coerceIn(0f, 100f)
    val basePx = 1.5f + (clamped / 100f) * 26.5f
    return if (isHighlighter) basePx * 2.5f else basePx
}

/**
 * Kalınlık değerine en yakın preset indeksini (0, 1, 2) döner.
 */
fun thicknessToStrokeSizeIndex(thicknessValue: Float): Int {
    val clamped = thicknessValue.coerceIn(0f, 100f)
    return when {
        clamped < 25f -> 0
        clamped < 50f -> 1
        else -> 2
    }
}

/**
 * Preset indeksini (0, 1, 2) 0-100 kalınlık değerine dönüştürür.
 */
fun strokeSizeIndexToThickness(index: Int): Float {
    return when (index.coerceIn(0, 2)) {
        0 -> 15f
        1 -> 35f
        else -> 65f
    }
}

/**
 * Ham dokunma/kalem noktalarındaki mikrotitreme gürültülerini filtreler.
 */
fun filterPointsForSmoothInk(points: List<Offset>, minDistancePx: Float = 1.8f): List<Offset> {
    if (points.size <= 2) return points
    val filtered = ArrayList<Offset>(points.size)
    filtered.add(points.first())
    var lastKept = points.first()
    val minSq = minDistancePx * minDistancePx
    for (i in 1 until points.size - 1) {
        val pt = points[i]
        val dx = pt.x - lastKept.x
        val dy = pt.y - lastKept.y
        if (dx * dx + dy * dy >= minSq) {
            filtered.add(pt)
            lastKept = pt
        }
    }
    filtered.add(points.last())
    return filtered
}

/**
 * S-Pen basınç örneklerinde gerçek donanım basınç değişimi olup olmadığını kontrol eder.
 */
fun hasStylusPressureVariation(pressureSamples: List<Float>): Boolean {
    if (pressureSamples.size < 2) return false
    var minP = Float.MAX_VALUE
    var maxP = Float.MIN_VALUE
    for (p in pressureSamples) {
        if (!p.isFinite()) continue
        if (p < minP) minP = p
        if (p > maxP) maxP = p
    }
    return minP <= maxP && (maxP - minP) > 0.03f
}

/**
 * Catmull-Rom eğrisinden türetilen Cubic Bézier segmenti (başlangıç, 2 kontrol noktası, bitiş)
 */
data class BezierSegment(
    val start: Offset,
    val control1: Offset,
    val control2: Offset,
    val end: Offset
)

/**
 * Akıllı Şekil (Smart Shape) geometri tespiti sonucu
 */
data class SmartShapeGeometry(
    val isClosedRect: Boolean,
    val rectLeftTop: Offset? = null,
    val rectRightBottom: Offset? = null,
    val lineStart: Offset? = null,
    val lineEnd: Offset? = null
)

/**
 * Ham noktalardan akıllı şekil geometrisini tespit eder.
 */
fun detectSmartShape(pts: List<Offset>): SmartShapeGeometry? {
    if (pts.size < 4) return null
    val first = pts.first()
    val last = pts.last()
    val dist = (last - first).getDistance()
    return if (dist < 55f) {
        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        SmartShapeGeometry(
            isClosedRect = true,
            rectLeftTop = Offset(minX, minY),
            rectRightBottom = Offset(maxX, maxY)
        )
    } else {
        SmartShapeGeometry(
            isClosedRect = false,
            lineStart = first,
            lineEnd = last
        )
    }
}

/**
 * Catmull-Rom kontrol noktalarını C1 continuous Cubic Bézier segmentlerine dönüştürür.
 * GoodNotes & Apple Notes kalitesinde pürüzsüz doğal hat geometrisi üretir.
 */
fun calculateCatmullRomToBezier(pts: List<Offset>): List<BezierSegment> {
    if (pts.size < 2) return emptyList()
    val n = pts.size
    val segments = ArrayList<BezierSegment>(n - 1)
    for (i in 0 until n - 1) {
        val p0 = if (i > 0) pts[i - 1] else Offset(2 * pts[0].x - pts[1].x, 2 * pts[0].y - pts[1].y)
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = if (i + 2 < n) pts[i + 2] else Offset(2 * p2.x - p1.x, 2 * p2.y - p1.y)

        val c1 = Offset(
            p1.x + (p2.x - p0.x) / 6f,
            p1.y + (p2.y - p0.y) / 6f
        )
        val c2 = Offset(
            p2.x - (p3.x - p1.x) / 6f,
            p2.y - (p3.y - p1.y) / 6f
        )

        segments.add(BezierSegment(start = p1, control1 = c1, control2 = c2, end = p2))
    }
    return segments
}

/**
 * Catmull-Rom spline'larını C1 continuous Cubic Bézier eğrilerine dönüştürerek
 * GoodNotes & Apple Notes kalitesinde pürüzsüz doğal mürekkep yolu oluşturur.
 */
fun buildSmoothInkPath(rawPoints: List<Offset>, isSmartShape: Boolean = false): Path {
    val path = Path()
    if (rawPoints.isEmpty()) return path

    val pts = filterPointsForSmoothInk(rawPoints)
    if (pts.isEmpty()) return path

    // 1 Nokta: Tek dokunuş noktası (nokta, virgül, formül noktası)
    if (pts.size == 1) {
        val p = pts.first()
        path.addOval(Rect(p.x - 0.5f, p.y - 0.5f, p.x + 0.5f, p.y + 0.5f))
        return path
    }

    // 2 Nokta: Düz pürüzsüz hat
    if (pts.size == 2) {
        path.moveTo(pts[0].x, pts[0].y)
        path.lineTo(pts[1].x, pts[1].y)
        return path
    }

    // Akıllı Şekil tespiti
    if (isSmartShape) {
        val shape = detectSmartShape(pts)
        if (shape != null) {
            if (shape.isClosedRect && shape.rectLeftTop != null && shape.rectRightBottom != null) {
                path.moveTo(shape.rectLeftTop.x, shape.rectLeftTop.y)
                path.lineTo(shape.rectRightBottom.x, shape.rectLeftTop.y)
                path.lineTo(shape.rectRightBottom.x, shape.rectRightBottom.y)
                path.lineTo(shape.rectLeftTop.x, shape.rectRightBottom.y)
                path.close()
                return path
            } else if (shape.lineStart != null && shape.lineEnd != null) {
                path.moveTo(shape.lineStart.x, shape.lineStart.y)
                path.lineTo(shape.lineEnd.x, shape.lineEnd.y)
                return path
            }
        }
    }

    // Doğal Not Kalemi: Catmull-Rom to Cubic Bézier enterpolasyonu
    val segments = calculateCatmullRomToBezier(pts)
    if (segments.isNotEmpty()) {
        path.moveTo(segments.first().start.x, segments.first().start.y)
        for (seg in segments) {
            path.cubicTo(seg.control1.x, seg.control1.y, seg.control2.x, seg.control2.y, seg.end.x, seg.end.y)
        }
    }

    return path
}

/**
 * S-Pen basınç hassasiyetini Catmull-Rom Cubic Bézier pürüzsüzlüğüyle birleştirerek çizer.
 */
private fun DrawScope.drawPressureBezierInk(
    points: List<Offset>,
    pressureSamples: List<Float>,
    color: Color,
    baseWidth: Float
) {
    if (points.size < 2 || points.size != pressureSamples.size) return
    val segments = calculateCatmullRomToBezier(points)
    val segPath = Path()
    for (i in segments.indices) {
        val seg = segments[i]
        val factor = (
            StylusInputPolicy.pressureWidthFactor(pressureSamples[i]) +
                StylusInputPolicy.pressureWidthFactor(pressureSamples[i + 1])
            ) / 2f
        segPath.reset()
        segPath.moveTo(seg.start.x, seg.start.y)
        segPath.cubicTo(
            seg.control1.x, seg.control1.y,
            seg.control2.x, seg.control2.y,
            seg.end.x, seg.end.y
        )
        drawPath(
            path = segPath,
            color = color,
            style = Stroke(
                width = baseWidth * factor,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/**
 * Compose DrawScope üzerinde doğal mürekkep çizgisini çizer.
 * v1.4.9 S-Pen `pressureSamples` desteğiyle tam uyumludur.
 */
fun DrawScope.drawNaturalInkStroke(stroke: DrawStroke) {
    val pts = stroke.points
    if (pts.isEmpty()) return

    val isEraser = stroke.isEraser || stroke.colorHex == 0x00000000L
    val strokeColor = when {
        isEraser -> Color.Transparent
        stroke.isHighlighter -> Color(stroke.colorHex).copy(alpha = 0.36f)
        else -> Color(stroke.colorHex)
    }
    val blendMode = if (isEraser) BlendMode.Clear else BlendMode.SrcOver
    val effectiveWidth = if (stroke.strokeWidthPx > 0f) stroke.strokeWidthPx else 8f

    // Tek dokunuş (dot) desteği: Dairesel doğal mürekkep damlası
    if (pts.size == 1) {
        drawCircle(
            color = strokeColor,
            radius = effectiveWidth / 2f,
            center = pts.first(),
            blendMode = blendMode
        )
        return
    }

    // S-Pen donanım basınç hassasiyeti + Catmull-Rom Cubic Bézier birleşimi
    if (!isEraser && !stroke.isSmartShape && !stroke.isHighlighter &&
        stroke.pressureSamples.size == pts.size &&
        hasStylusPressureVariation(stroke.pressureSamples)
    ) {
        drawPressureBezierInk(pts, stroke.pressureSamples, strokeColor, effectiveWidth)
        return
    }

    val path = buildSmoothInkPath(pts, stroke.isSmartShape)
    drawPath(
        path = path,
        color = strokeColor,
        style = Stroke(
            width = effectiveWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        blendMode = blendMode
    )
}

/**
 * ═════════════════════════════════════════════════════════════════════════
 * KALEM KALINLIĞI POPOVER / KART BİLEŞENİ (0 - 100 SLIDER & CANLI ÖNİZLEME)
 * ═════════════════════════════════════════════════════════════════════════
 */
@Composable
fun PenSizePopoverCard(
    thicknessValue: Float,
    selectedPenColorHex: Long,
    isHighlighter: Boolean,
    onThicknessChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPx = penThicknessToPx(thicknessValue, isHighlighter)
    val currentColor = if (isHighlighter) {
        Color(selectedPenColorHex).copy(alpha = 0.36f)
    } else {
        Color(selectedPenColorHex)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
        shadowElevation = 18.dp,
        modifier = modifier
            .width(310.dp)
            .testTag("pen_size_popover_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // BAŞLIK & DEĞER ROZETİ & KAPAT BUTONU
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LineWeight,
                        contentDescription = "Kalem Kalınlığı",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = "Kalem Kalınlığı",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Kalınlık Oranı Rozeti (px karmaşası olmadan sade görünüm)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "%${thicknessValue.roundToInt()}",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // CANLI İNTERAKTİF ÖNİZLEME (SEÇİLİ RENKTE ÇİZGİ & KALINLIK HALKASI)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("pen_thickness_live_preview_container")
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Sol: Canlı Mürekkep Çizgisi Dalgası (Bézier S-Curve)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val w = size.width
                            val h = size.height
                            val wavePath = Path().apply {
                                moveTo(10f, h * 0.5f)
                                cubicTo(
                                    w * 0.3f, h * 0.15f,
                                    w * 0.7f, h * 0.85f,
                                    w - 14f, h * 0.5f
                                )
                            }
                            drawPath(
                                path = wavePath,
                                color = currentColor,
                                style = Stroke(
                                    width = currentPx,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Sağ: Canlı Kalem Ucu Kesit Halkası (Dairesel Çap Göstergesi)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size((currentPx).coerceIn(2f, 30f).dp)
                                    .clip(CircleShape)
                                    .background(currentColor)
                            )
                        }
                        Text(
                            text = "Canlı Uç",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // PÜRÜZSÜZ 0 - 100 KALINLIK SLIDER'I
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Slider(
                    value = thicknessValue,
                    onValueChange = { onThicknessChange(it) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF38BDF8),
                        activeTrackColor = Color(0xFF38BDF8),
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .testTag("pen_thickness_slider")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "0 (Çok İnce)", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
                    Text(text = "50", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
                    Text(text = "100 (Kalın)", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
                }
            }

            // HIZLI PRESET BUTONLARI (İNCE / ORTA / KALIN / VURGU)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PenPreset.values().forEach { preset ->
                    val isSelected = abs(thicknessValue - preset.thicknessValue) < 10f
                    Surface(
                        onClick = { onThicknessChange(preset.thicknessValue) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF2563EB) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.18f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .testTag(preset.tag)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Küçük kalınlık önizleme noktası
                            Box(
                                modifier = Modifier
                                    .size(
                                        when (preset) {
                                            PenPreset.INCE -> 4.dp
                                            PenPreset.ORTA -> 6.dp
                                            PenPreset.KALIN -> 8.dp
                                            PenPreset.VURGU -> 10.dp
                                        }
                                    )
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White else Color(0xFF38BDF8))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = preset.label,
                                color = Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * ═════════════════════════════════════════════════════════════════════════
 * SAMSUNG NOTES TARZI ÇİFT MODLU SİLGİ & BOYUT POPOVER KARTI
 * - Çizgi Silgisi (dokunulan çizginin tamamını siler) vs Alan Silgisi (bölgesel siler)
 * - Canlı dairesel silgi ucu önizlemesi, 0-100 pürüzsüz boyut kaydırıcısı ve 4 hızlı boyut
 * ═════════════════════════════════════════════════════════════════════════
 */
@Composable
fun EraserSettingsPopoverCard(
    eraserMode: EraserMode,
    eraserThicknessValue: Float,
    onModeChange: (EraserMode) -> Unit,
    onThicknessChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val previewCircleDp = (8f + (eraserThicknessValue.coerceIn(0f, 100f) / 100f) * 26f).dp

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
        shadowElevation = 18.dp,
        modifier = modifier
            .width(326.dp)
            .testTag("eraser_settings_popover_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Silgi Ayarları",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 1. SAMSUNG NOTES MOD SEÇİCİ: ÇİZGİ SİLGİSİ vs ALAN SİLGİSİ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EraserMode.entries.forEach { mode ->
                    val isSelected = eraserMode == mode
                    Surface(
                        onClick = { onModeChange(mode) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF2563EB) else Color.White.copy(alpha = 0.07f),
                        border = BorderStroke(
                            1.2.dp,
                            if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.18f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("eraser_mode_${mode.name.lowercase()}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = mode.title,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = mode.subtitle,
                                color = Color.White.copy(alpha = if (isSelected) 0.90f else 0.62f),
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }

            // 2. SİLGİ BOYUTU ÖNİZLEME + SLIDER
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F172A))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(previewCircleDp)
                                .clip(CircleShape)
                                .background(Color(0xFF38BDF8).copy(alpha = 0.22f))
                                .border(1.5.dp, Color(0xFF38BDF8), CircleShape)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Silgi Boyutu",
                            color = Color.White.copy(alpha = 0.88f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        )
                        Slider(
                            value = eraserThicknessValue.coerceIn(0f, 100f),
                            onValueChange = onThicknessChange,
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF38BDF8),
                                activeTrackColor = Color(0xFF38BDF8),
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .testTag("eraser_size_slider")
                        )
                    }
                }
            }

            // 3. HIZLI SİLGİ BOYUTU SEÇENEKLERİ (px olmadan görsel daireli)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EraserPreset.entries.forEach { preset ->
                    val isSelected = abs(eraserThicknessValue - preset.thicknessValue) < 12f
                    Surface(
                        onClick = { onThicknessChange(preset.thicknessValue) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF2563EB) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.18f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .testTag(preset.tag)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(preset.previewDp.coerceIn(5f, 13f).dp)
                                    .clip(CircleShape)
                                    .border(
                                        1.4.dp,
                                        if (isSelected) Color.White else Color(0xFF38BDF8),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = preset.label,
                                color = Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 9.5.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
