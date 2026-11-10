package com.example.viewmodel

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot

/**
 * A small, bounded and renderer-independent diagram language for generated twins.
 *
 * Every coordinate is relative to the question figure (0..1). No remote SVG/HTML,
 * arbitrary code, external image URL, or PDF is accepted from an AI response.
 * Unsupported visual formats fail closed rather than displaying an invented figure.
 *
 * This validator proves the *drawing payload* is renderable, not that the geometry
 * is mathematically true. A separate blind question/diagram review is mandatory.
 */
internal data class TwinDiagramLine(
    val x1: Float, val y1: Float, val x2: Float, val y2: Float,
    val arrow: Boolean = false
)
internal data class TwinDiagramCircle(val cx: Float, val cy: Float, val radius: Float)
internal data class TwinDiagramLabel(val x: Float, val y: Float, val text: String)
internal data class TwinDiagramCurve(val points: List<Pair<Float, Float>>)

internal data class TwinDiagramSpec(
    val kind: String,
    val lines: List<TwinDiagramLine>,
    val circles: List<TwinDiagramCircle>,
    val labels: List<TwinDiagramLabel>,
    val curves: List<TwinDiagramCurve> = emptyList()
) {
    companion object {
        private val kinds = setOf("triangle", "circle", "graph", "vectors", "schematic", "table", "flowchart", "timeline")
        fun supports(kind: String): Boolean = kind in kinds

        fun parse(raw: String): TwinDiagramSpec? = runCatching {
            if (raw.length !in 20..12000) return null
            val json = JSONObject(raw)
            val kind = json.optString("kind").trim().lowercase()
            if (!supports(kind)) return null

            fun finite01(v: Double): Float {
                require(v.isFinite() && v in 0.0..1.0)
                return v.toFloat()
            }
            fun coordinate(o: JSONObject, key: String): Float =
                finite01(o.getDouble(key))
            fun array(name: String, max: Int): JSONArray {
                val a = json.optJSONArray(name) ?: JSONArray()
                require(a.length() <= max)
                return a
            }

            val linesArray = array("lines", 28)
            val lines = (0 until linesArray.length()).map { index ->
                val item = linesArray.getJSONObject(index)
                val x1 = coordinate(item, "x1")
                val y1 = coordinate(item, "y1")
                val x2 = coordinate(item, "x2")
                val y2 = coordinate(item, "y2")
                require(hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()) > .025)
                TwinDiagramLine(x1, y1, x2, y2, item.optBoolean("arrow", false))
            }
            val circlesArray = array("circles", 12)
            val circles = (0 until circlesArray.length()).map { index ->
                val item = circlesArray.getJSONObject(index)
                val cx = coordinate(item, "cx")
                val cy = coordinate(item, "cy")
                val radius = item.getDouble("radius")
                require(radius.isFinite() && radius in 0.015..0.42)
                require(cx - radius >= 0 && cx + radius <= 1)
                require(cy - radius >= 0 && cy + radius <= 1)
                TwinDiagramCircle(cx, cy, radius.toFloat())
            }
            val labelsArray = array("labels", 24)
            val labels = (0 until labelsArray.length()).map { index ->
                val item = labelsArray.getJSONObject(index)
                val text = item.getString("text").trim()
                require(text.isNotBlank() && text.length <= 24)
                require(!text.any { it.isISOControl() })
                TwinDiagramLabel(coordinate(item, "x"), coordinate(item, "y"), text)
            }
            val curveArray = array("paths", 8)
            val curves = (0 until curveArray.length()).map { index ->
                val item = curveArray.getJSONObject(index)
                val pointsArray = item.getJSONArray("points")
                require(pointsArray.length() in 3..64)
                val points = (0 until pointsArray.length()).map { position ->
                    val point = pointsArray.getJSONObject(position)
                    coordinate(point, "x") to coordinate(point, "y")
                }
                // A curve must not be an array of repeated / invisible points.
                require(points.zipWithNext().count { (a, b) ->
                    hypot((a.first - b.first).toDouble(),
                        (a.second - b.second).toDouble()) > .004
                } >= 2)
                TwinDiagramCurve(points)
            }
            require(lines.size + circles.size + curves.size >= 1)
            require(labels.isNotEmpty())
            when (kind) {
                "triangle" -> require(lines.size >= 3)
                "circle" -> require(circles.isNotEmpty())
                "graph" -> require(lines.size >= 2)
                "vectors" -> require(lines.any { it.arrow })
                "schematic" -> require(lines.size + circles.size + curves.size >= 2)
                "table" -> require(lines.size >= 4 && labels.size >= 4)
                "flowchart" -> require(lines.size >= 2 && labels.size >= 2 &&
                    lines.any { it.arrow })
                "timeline" -> require(lines.isNotEmpty() && labels.size >= 2)
            }
            TwinDiagramSpec(kind, lines, circles, labels, curves)
        }.getOrNull()
    }
}
