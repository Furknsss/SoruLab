package com.example.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.TwinDiagramSpec
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws a newly generated figure, NOT the original crop.
 * There is deliberately no WebView, arbitrary SVG parser or remote image URL.
 */
@Composable
internal fun TwinDiagramCanvas(
    diagramJson: String,
    modifier: Modifier = Modifier
) {
    val diagram = remember(diagramJson) { TwinDiagramSpec.parse(diagramJson) } ?: return
    Canvas(
        modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
    ) {
        val ink = Color(0xFF17223B)
        val widthPx = size.width
        val heightPx = size.height
        // Preserve lengths/angles regardless of tablet column aspect ratio.
        val unit = minOf(widthPx, heightPx)
        val offsetX = (widthPx - unit) / 2f
        val offsetY = (heightPx - unit) / 2f
        fun point(x: Float, y: Float) = Offset(offsetX + x * unit, offsetY + y * unit)
        val stroke = 2.dp.toPx()
        diagram.lines.forEach { line ->
            val from = point(line.x1, line.y1)
            val to = point(line.x2, line.y2)
            drawLine(ink, from, to, strokeWidth = stroke, cap = StrokeCap.Round)
            if (line.arrow) {
                val theta = atan2(to.y - from.y, to.x - from.x)
                val wing = 11.dp.toPx()
                val left = Offset(
                    to.x - wing * cos(theta - .55f),
                    to.y - wing * sin(theta - .55f)
                )
                val right = Offset(
                    to.x - wing * cos(theta + .55f),
                    to.y - wing * sin(theta + .55f)
                )
                drawLine(ink, left, to, strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(ink, right, to, strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
        // Bounded connected points support nonlinear graphs (parabola, motion
        // curves, smooth biological trends) without accepting executable SVG.
        diagram.curves.forEach { curve ->
            val path = Path()
            curve.points.forEachIndexed { index, coord ->
                val pt = point(coord.first, coord.second)
                if (index == 0) path.moveTo(pt.x, pt.y)
                else path.lineTo(pt.x, pt.y)
            }
            drawPath(path, color = ink, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        diagram.circles.forEach { circle ->
            // Keep a circle circular on a rectangular canvas.
            val diameterUnit = minOf(widthPx, heightPx)
            drawCircle(
                color = ink,
                radius = circle.radius * diameterUnit,
                center = point(circle.cx, circle.cy),
                style = Stroke(width = stroke)
            )
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(23, 34, 59)
            textAlign = Paint.Align.CENTER
            textSize = 14.sp.toPx()
            isFakeBoldText = true
        }
        diagram.labels.forEach { label ->
            val pos = point(label.x, label.y)
            drawContext.canvas.nativeCanvas.drawText(
                label.text,
                pos.x,
                pos.y - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                labelPaint
            )
        }
    }
}
