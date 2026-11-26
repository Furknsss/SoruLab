@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.example.ui.screens

import android.view.MotionEvent
import androidx.compose.ui.input.pointer.motionEventSpy
import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.data.*
import com.example.ui.drawing.PenSizePopoverCard
import com.example.ui.drawing.buildSmoothInkPath
import com.example.ui.drawing.drawNaturalInkStroke
import com.example.ui.drawing.hasStylusPressureVariation
import com.example.ui.drawing.penThicknessToPx
import com.example.ui.drawing.strokeSizeIndexToThickness
import com.example.util.StudioImageAndPdfEngine
import com.example.util.StylusInputPolicy
import com.example.util.BoardInkCoordinates
import com.example.util.TestAnswerAnalyticsEngine
import com.example.viewmodel.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal data class TwoColumnQuestionPage(
    val left: List<YksQuestion>,
    val right: List<YksQuestion>,
    val columnCount: Int = 2
) {
    val questions: List<YksQuestion> get() = left + right
}

internal data class BookImageMeasurements(
    val heightOverWidth: Float,
    val typographyScale: Float,
    val printedGlyphDp: Float? = null
)

private object TestPageDimensions {
    fun edgeHandleSpace(columns: Int): Dp = if (columns == 1) 18.dp else 24.dp
    fun pageHorizontal(columns: Int): Dp = if (columns == 1) 10.dp else 14.dp
    val pageVertical = 8.dp
    val footerHeight = 36.dp
    val gutter = 20.dp
    fun questionGap(preset: com.example.util.QuestionSpacingPreset): Dp = when (preset) {
        com.example.util.QuestionSpacingPreset.COMPACT -> 12.dp
        com.example.util.QuestionSpacingPreset.NORMAL -> 16.dp
        com.example.util.QuestionSpacingPreset.SPACIOUS -> 28.dp
    }
}

private object MeasuredPaperCache {
    private val maxTrimmedCacheKb: Int = ((Runtime.getRuntime().maxMemory() / 1024L) / 10L)
        .toInt()
        .coerceIn(12 * 1024, 48 * 1024)

    val trimmedBitmaps = object : android.util.LruCache<String, Bitmap>(maxTrimmedCacheKb) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    val glyphRatioCache = java.util.concurrent.ConcurrentHashMap<String, Float>()

    fun cacheKeyFor(q: YksQuestion): String = buildString {
        append(q.id)
        append('|')
        append(q.imagePath)
        append('|')
        append(q.pdfFilePath)
        append('|')
        append(q.pageIndex)
        append('|')
        append(q.imageBase64.length)
    }
}

private val LocalTestBitmaps = staticCompositionLocalOf<Map<String, Bitmap?>> { emptyMap() }

private data class PreparedTestPaper(
    val columnWidth: Float,
    val userScalePercent: Int,
    val bitmaps: Map<String, Bitmap?>,
    val scales: Map<String, Float>
)

/** Actual Compose measurement is the only source of page geometry. */
@Composable
private fun MeasuredBookPages(
    questions: List<YksQuestion>,
    customPublishers: List<YksPublisherInfo>,
    isImmersive: Boolean,
    layoutConfig: com.example.util.TestPageLayoutConfig = com.example.util.TestPageLayoutConfig(),
    content: @Composable (List<TwoColumnQuestionPage>, Map<String, Float>) -> Unit
) {
    val context = LocalContext.current.applicationContext
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (layoutConfig.overrideColumnMode) {
            layoutConfig.columnMode.columnCount
        } else {
            if (maxWidth >= 600.dp) 2 else 1
        }
        val edgeHandle = TestPageDimensions.edgeHandleSpace(columns)
        val pageHoriz = TestPageDimensions.pageHorizontal(columns)
        val gapDp = TestPageDimensions.questionGap(layoutConfig.spacingPreset)
        val userScalePercent = layoutConfig.scaleConfig.clampedScalePercent
        val userScaleFactor = layoutConfig.scaleConfig.scaleFactor
        val density = LocalDensity.current
        val columnWidthPx = with(density) {
            val inner = constraints.maxWidth - edgeHandle.roundToPx() -
                2 * pageHoriz.roundToPx()
            if (columns == 2) ((inner - TestPageDimensions.gutter.roundToPx()) / 2).coerceAtLeast(1)
            else inner.coerceAtLeast(1)
        }
        val columnWidthDp = with(density) { columnWidthPx.toDp().value }
        val questionsIdentity = remember(questions) {
            Triple(questions.size, questions.firstOrNull()?.id.orEmpty(), System.identityHashCode(questions))
        }
        val prepared by produceState<PreparedTestPaper?>(null, questionsIdentity, columnWidthDp, userScalePercent) {
            val loaded = withContext(Dispatchers.IO) {
                val semaphore = kotlinx.coroutines.sync.Semaphore(4)
                val targetMaxWidthPx = if (questions.size > 16) {
                    (columnWidthPx * 2.2f).toInt().coerceIn(640, 1440)
                } else {
                    1600
                }
                val pairs = coroutineScope {
                    questions.map { q ->
                        async {
                            semaphore.acquire()
                            try {
                                val cKey = MeasuredPaperCache.cacheKeyFor(q)
                                val cached = MeasuredPaperCache.trimmedBitmaps.get(cKey)?.takeUnless { it.isRecycled }
                                val trimmed = cached ?: StudioImageAndPdfEngine.loadGuaranteedQuestionBitmap(
                                    context, q.id, q.imagePath, q.imageBase64, q.pdfFilePath,
                                    q.pageIndex, q.normLeft, q.normTop, q.normRight, q.normBottom
                                )?.let { raw ->
                                    val downscaled = if (raw.width > targetMaxWidthPx && raw.height > 120) {
                                        val scaledH = (raw.height.toLong() * targetMaxWidthPx / raw.width).toInt().coerceAtLeast(1)
                                        runCatching { Bitmap.createScaledBitmap(raw, targetMaxWidthPx, scaledH, true) }.getOrDefault(raw)
                                    } else raw
                                    trimTestImageMargins(downscaled)
                                }?.also { MeasuredPaperCache.trimmedBitmaps.put(cKey, it) }
                                q.id to trimmed
                            } finally {
                                semaphore.release()
                            }
                        }
                    }.awaitAll()
                }
                val bitmaps = pairs.toMap()
                val contentWidthForGlyph = (columnWidthDp - 40f).coerceAtLeast(1f)
                val metrics = bitmaps.mapNotNull { (id, bitmap) ->
                    bitmap?.let {
                        val glyphRatioKey = "$id|${it.width}x${it.height}"
                        val cachedGlyphRatio = MeasuredPaperCache.glyphRatioCache[glyphRatioKey]
                        val glyphDp = if (cachedGlyphRatio != null) {
                            if (cachedGlyphRatio > 0f) cachedGlyphRatio * contentWidthForGlyph else null
                        } else {
                            val detected = detectTypicalPrintedGlyphDp(it, contentWidthForGlyph)
                            MeasuredPaperCache.glyphRatioCache[glyphRatioKey] =
                                if (detected != null && contentWidthForGlyph > 0f) detected / contentWidthForGlyph else -1f
                            detected
                        }
                        id to BookImageMeasurements(
                            it.height.toFloat() / it.width,
                            estimateBookTypographyScale(it, contentWidthForGlyph),
                            glyphDp
                        )
                    }
                }.toMap()
                val baseScales = calibratedBookTypographyScales(metrics).mapValues { (_, s) ->
                    (s * userScaleFactor).coerceIn(0.50f, 1.50f)
                }
                PreparedTestPaper(columnWidthDp, userScalePercent, bitmaps, baseScales)
            }
            withContext(Dispatchers.Main.immediate) { value = loaded }
        }
        val paper = prepared?.takeIf { it.columnWidth == columnWidthDp && it.userScalePercent == userScalePercent }
        if (paper == null) {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Color(0xFF3475F5))
                    Text("Test sayfaları hazırlanıyor", color = Color(0xFF64748B), fontSize = 14.sp)
                }
            }
        } else CompositionLocalProvider(LocalTestBitmaps provides paper.bitmaps) {
            SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
                val chrome = 58.dp.roundToPx() + if (isImmersive) 0 else 54.dp.roundToPx()
                val available = (constraints.maxHeight - chrome -
                    2 * TestPageDimensions.pageVertical.roundToPx() -
                    TestPageDimensions.footerHeight.roundToPx()).coerceAtLeast(1)
                val gapPx = gapDp.roundToPx()

                val initialHeights = questions.map { q ->
                    subcompose("measure_" + q.id) {
                        Box(Modifier.clearAndSetSemantics { }) {
                            BookPageQuestionItem345(
                                question = q, maxImageHeight = (available.toDp() - 34.dp).coerceAtLeast(1.dp),
                                imageTypographyScale = paper.scales[q.id] ?: 1f,
                                isReviewMode = false, selectedOption = null,
                                isThreeDotMenuOpen = false, customPublishers = customPublishers,
                                onToggleThreeDotMenu = {}, onSelectOption = {}, onOpenTopicLecture = {},
                                onOpenSolutionModal = {}, onOpenTwinScreen = {}, onAddToKumbara = {}
                            )
                        }
                    }.single().measure(Constraints(minWidth = columnWidthPx, maxWidth = columnWidthPx)).height
                }

                val initialPack = packMeasuredTestPages(
                    heights = initialHeights,
                    availableHeight = available,
                    gap = gapPx,
                    columnCount = columns
                )

                val scalableFlags = questions.map { q -> paper.bitmaps[q.id] != null }
                val nearFitAdjustments = computeNearFitScaleAdjustments(
                    heights = initialHeights,
                    scalableFlags = scalableFlags,
                    initialPages = initialPack,
                    availableHeight = available,
                    gap = gapPx
                )
                val hasNearFitTrim = nearFitAdjustments.any { it < 0.999f }

                val finalScales = if (hasNearFitTrim) {
                    val map = HashMap<String, Float>(paper.scales.size)
                    questions.forEachIndexed { idx, q ->
                        val base = paper.scales[q.id] ?: 1f
                        map[q.id] = (base * nearFitAdjustments[idx]).coerceIn(0.50f, 1.50f)
                    }
                    map
                } else {
                    paper.scales
                }

                val finalHeights = if (hasNearFitTrim) {
                    questions.mapIndexed { idx, q ->
                        if (nearFitAdjustments[idx] < 0.999f) {
                            subcompose("measure_trimmed_" + q.id) {
                                Box(Modifier.clearAndSetSemantics { }) {
                                    BookPageQuestionItem345(
                                        question = q, maxImageHeight = (available.toDp() - 34.dp).coerceAtLeast(1.dp),
                                        imageTypographyScale = finalScales[q.id] ?: 1f,
                                        isReviewMode = false, selectedOption = null,
                                        isThreeDotMenuOpen = false, customPublishers = customPublishers,
                                        onToggleThreeDotMenu = {}, onSelectOption = {}, onOpenTopicLecture = {},
                                        onOpenSolutionModal = {}, onOpenTwinScreen = {}, onAddToKumbara = {}
                                    )
                                }
                            }.single().measure(Constraints(minWidth = columnWidthPx, maxWidth = columnWidthPx)).height
                        } else {
                            initialHeights[idx]
                        }
                    }
                } else {
                    initialHeights
                }

                val pages = (if (hasNearFitTrim) {
                    packMeasuredTestPages(
                        heights = finalHeights,
                        availableHeight = available,
                        gap = gapPx,
                        columnCount = columns
                    )
                } else {
                    initialPack
                }).map { page ->
                    TwoColumnQuestionPage(page.columns[0].map(questions::get),
                        page.columns.getOrNull(1).orEmpty().map(questions::get), columns)
                }
                val board = subcompose("board") {
                    Box(Modifier.fillMaxSize()) { content(pages, finalScales) }
                }.single().measure(constraints)
                layout(constraints.maxWidth, constraints.maxHeight) { board.placeRelative(0, 0) }
            }
        }
    }
}

/** Conservatively remove peripheral white only. Original ZIP image stays unchanged. */
internal fun trimQuestionWhiteMargins(source: Bitmap): Bitmap {
    if (source.width < 80 || source.height < 80) return source
    val w = source.width
    val h = source.height
    val step = maxOf(1, (w + 599) / 600, (h + 1599) / 1600)
    var minX = w
    var maxX = -1
    var minY = h
    var maxY = -1
    val row = IntArray(w)
    for (y in 0 until h step step) {
        source.getPixels(row, 0, w, 0, y, w, 1)
        for (x in 0 until w step step) {
            val c = row[x]
            if (android.graphics.Color.alpha(c) < 100) continue
            val r = android.graphics.Color.red(c)
            val g = android.graphics.Color.green(c)
            val b = android.graphics.Color.blue(c)
            if (minOf(r, g, b) >= 238) continue
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
    }
    if (maxX <= minX || maxY <= minY) return source
    // Inspect the actual coloured/dark ink bounds, NOT the scan's page box.
    // The old 30%/70% hard clamps left enormous empty borders around narrow
    // crops and made entire physics / paragraph questions look miniature.
    // A small proportional white breathing margin is intentional and even.
    val padX = maxOf((w * 0.018f).toInt(), 8)
    val padY = maxOf((h * 0.015f).toInt(), 6)
    var left = (minX - padX).coerceAtLeast(0)
    var top = (minY - padY).coerceAtLeast(0)
    var right = (maxX + step + padX).coerceAtMost(w)
    var bottom = (maxY + step + padY).coerceAtMost(h)
    // Preserve enough surrounding paper for short equations and lone figures,
    // without requiring 70% of the original image to remain blank.
    val minWidth = maxOf(80, (w * 0.26f).toInt()).coerceAtMost(w)
    if (right - left < minWidth) {
        val center = (left + right) / 2
        left = (center - minWidth / 2).coerceIn(0, w - minWidth)
        right = left + minWidth
    }
    val minHeight = maxOf(60, (h * 0.15f).toInt()).coerceAtMost(h)
    if (bottom - top < minHeight) {
        val center = (top + bottom) / 2
        top = (center - minHeight / 2).coerceIn(0, h - minHeight)
        bottom = top + minHeight
    }
    if (right <= left || bottom <= top) return source
    if (right - left > w * 0.93f && bottom - top > h * 0.93f) return source
    return runCatching {
        Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }.getOrDefault(source)
}

/** Normalize fonts relative to the actual question set without shrinking single-column crops into cramped stamps. */
internal fun calibratedBookTypographyScales(
    measurements: Map<String, BookImageMeasurements>
): Map<String, Float> {
    val observed = measurements.values.mapNotNull {
        it.printedGlyphDp?.takeIf { px -> px.isFinite() && px in 2.5f..28f }
    }.sorted()
    if (observed.isEmpty()) return measurements.mapValues {
        it.value.typographyScale.coerceIn(0.90f, 1.45f)
    }
    val commonFontDp = observed[(observed.size * 0.60f).toInt()
        .coerceIn(0, observed.lastIndex)].coerceIn(11.5f, 14.0f)
    return measurements.mapValues { (_, metric) ->
        metric.printedGlyphDp?.takeIf { it.isFinite() && it > 0f }
            ?.let { (commonFontDp / it).coerceIn(0.90f, 1.45f) }
            ?: metric.typographyScale.coerceIn(0.90f, 1.45f)
    }
}


/**
 * Estimate the size of real printed glyphs without mistaking blank margins or
 * page illustrations for letters. Dark connected components in a downsampled
 * scan are a better clue than full-width ink rows, which merge formula lines.
 * Returns null when the image has too little clear text evidence.
 */
private fun detectTypicalPrintedGlyphDp(bitmap: Bitmap, contentWidthDp: Float): Float? {
    val width = bitmap.width
    val height = bitmap.height
    if (width < 32 || height < 32 || contentWidthDp <= 0f) return null
    val step = maxOf(
        1, (width + 383) / 384, (height + 1399) / 1400
    )
    val sw = (width + step - 1) / step
    val sh = (height + step - 1) / step
    val size = sw * sh
    if (size > 650_000) return null

    val black = BooleanArray(size)
    val pixelRow = IntArray(width)
    for (y in 0 until height step step) {
        bitmap.getPixels(pixelRow, 0, width, 0, y, width, 1)
        val row = (y / step) * sw
        for (x in 0 until width step step) {
            val color = pixelRow[x]
            val opaque = android.graphics.Color.alpha(color) >= 128
            val dark = android.graphics.Color.red(color) * 299 +
                android.graphics.Color.green(color) * 587 +
                android.graphics.Color.blue(color) * 114 < 155_000
            black[row + x / step] = opaque && dark
        }
    }
    val seen = BooleanArray(size)
    val queue = IntArray(size)
    val glyphHeights = ArrayList<Int>()
    for (origin in 0 until size) {
        if (!black[origin] || seen[origin]) continue
        var head = 0
        var tail = 1
        queue[0] = origin
        seen[origin] = true
        var left = origin % sw
        var right = left
        var top = origin / sw
        var bottom = top
        while (head < tail) {
            val index = queue[head++]
            val x = index % sw
            val y = index / sw
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
            fun include(nx: Int, ny: Int) {
                if (nx < 0 || nx >= sw || ny < 0 || ny >= sh) return
                val neighbor = ny * sw + nx
                if (black[neighbor] && !seen[neighbor]) {
                    seen[neighbor] = true
                    queue[tail++] = neighbor
                }
            }
            include(x - 1, y)
            include(x + 1, y)
            include(x, y - 1)
            include(x, y + 1)
        }
        val h = bottom - top + 1
        val w = right - left + 1
        // Individual letters / numeric glyphs, not long underlines,
        // shaded diagrams or the continuous edge of a table.
        if (tail >= 5 && h in 3..(sw / 7).coerceAtLeast(5) &&
            w in 2..(sw / 5).coerceAtLeast(4) &&
            w.toFloat() / h in 0.16f..4f &&
            left > 1 && right < sw - 2
        ) glyphHeights += h
    }
    if (glyphHeights.size < 18) return null
    glyphHeights.sort()
    // 60th percentile resists punctuation, dots, accents and tiny subscripts.
    val typicalPx = glyphHeights[(glyphHeights.size * 0.60f).toInt()] * step
    return typicalPx * contentWidthDp / width
}

/**
 * Match the optical size of printed text from very different publishers.
 * The PDF crop's pixel dimensions alone are insufficient: two equally-wide
 * book images can have 11dp and 26dp glyphs on the same SoruLab sheet.
 * This analysis only samples a downscaled bitmap, never OCRs or edits content.
 * A scale <= 1 is used: complete text, diagrams and answer options are kept.
 */
internal fun estimateBookTypographyScale(bitmap: Bitmap, columnContentWidthDp: Float): Float {
    if (bitmap.width < 16 || bitmap.height < 16 || columnContentWidthDp <= 0f) return 1f
    val measuredGlyphDp = detectTypicalPrintedGlyphDp(bitmap, columnContentWidthDp)
    if (measuredGlyphDp != null && measuredGlyphDp.isFinite() && measuredGlyphDp > 0f) {
        val targetDp = (columnContentWidthDp * 0.056f).coerceIn(9.5f, 13.0f)
        return (targetDp / measuredGlyphDp).coerceIn(0.90f, 1.45f)
    }
    val sampleStep = 3
    val left = (bitmap.width * 0.06f).toInt()
    val right = (bitmap.width * 0.94f).toInt().coerceAtLeast(left + 1)
    val top = (bitmap.height * 0.03f).toInt()
    val bottom = (bitmap.height * 0.97f).toInt().coerceAtLeast(top + 1)
    val sampledColumns = ((right - left) / sampleStep).coerceAtLeast(1)
    val occupiedRows = BooleanArray(bottom - top)
    for (y in top until bottom) {
        var ink = 0
        var x = left
        while (x < right) {
            val color = bitmap.getPixel(x, y)
            val r = android.graphics.Color.red(color)
            val g = android.graphics.Color.green(color)
            val b = android.graphics.Color.blue(color)
            if (android.graphics.Color.alpha(color) >= 100 &&
                (r * 299 + g * 587 + b * 114) < 168_000) {
                ink++
            }
            x += sampleStep
        }
        // Thick glyphs and bold math can occupy most of a row. Previously
        // rejecting rows above 50% ink caused precisely those large-print
        // questions to fall back to an unscaled 1.0.
        occupiedRows[y - top] = ink >= 2
    }
    val bands = mutableListOf<Int>()
    var runStart = -1
    for (i in 0..occupiedRows.size) {
        val occupied = i < occupiedRows.size && occupiedRows[i]
        if (occupied && runStart < 0) runStart = i
        if (!occupied && runStart >= 0) {
            val height = i - runStart
            // Large-print publishers can have glyphs taller than 8.5% of
            // a crop's width. Rejecting those rows was the main reason the
            // previous visual scale silently fell back to 1.0 (giant text).
            if (height >= 3 && height <= bitmap.width * 0.20f) bands += height
            runStart = -1
        }
    }
    if (bands.size < 3) return 1f
    bands.sort()
    // A conservative visual font normalization, not OCR: measure dark
    // printed-text rows. The old 0.62 floor left many large-print Barış
    // crops at almost DOUBLE the size of the surrounding book questions.
    val typicalGlyphPx = bands[bands.size / 2].toFloat()
    val renderedGlyphDp = typicalGlyphPx * columnContentWidthDp / bitmap.width
    if (!renderedGlyphDp.isFinite() || renderedGlyphDp <= 0f) return 1f
    return (12.5f / renderedGlyphDp).coerceIn(0.90f, 1.45f)
}

internal fun estimateBookImageMeasurements(
    question: YksQuestion, filesDir: File, columnContentWidthDp: Float
): BookImageMeasurements? {
    val fileCandidates = listOfNotNull(
        question.imagePath.takeIf { it.isNotBlank() }?.let(::File),
        File(filesDir, "questions/q_${question.id}.jpg"),
        question.sourceQuestionId.takeIf { it.isNotBlank() }
            ?.let { File(filesDir, "questions/q_${it}.jpg") }
    )
    for (file in fileCandidates) {
        if (!file.isFile) continue
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) continue
        val ratio = bounds.outHeight.toFloat() / bounds.outWidth
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 512 ||
            bounds.outHeight / sampleSize > 2048) sampleSize *= 2
        val sample = runCatching {
            BitmapFactory.decodeFile(
                file.absolutePath, BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        }.getOrNull()
        if (sample == null) return BookImageMeasurements(ratio, 1f)
        val adjusted = trimQuestionWhiteMargins(sample)
        try {
            return BookImageMeasurements(
                heightOverWidth = adjusted.height.toFloat() / adjusted.width,
                typographyScale = estimateBookTypographyScale(adjusted, columnContentWidthDp),
                printedGlyphDp = detectTypicalPrintedGlyphDp(adjusted, columnContentWidthDp)
            )
        } finally {
            if (adjusted !== sample) adjusted.recycle()
            sample.recycle()
        }
    }

    // Some migrated questions store the bitmap inside imageBase64, whereas
    // the old paper-layout analyser only searched local files. That mismatch
    // produced fallback scale = 1.0 for exactly those oversized questions.
    val base64 = question.imageBase64
    if (!base64.isNullOrBlank() && base64.length <= 12_000_000) {
        return runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > 512 ||
                bounds.outHeight / sampleSize > 2048) sampleSize *= 2
            val sample = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }) ?: return@runCatching null
            try {
                val adjusted = trimQuestionWhiteMargins(sample)
                try {
                    BookImageMeasurements(
                        heightOverWidth = adjusted.height.toFloat() / adjusted.width,
                        typographyScale = estimateBookTypographyScale(
                            adjusted, columnContentWidthDp
                        ),
                        printedGlyphDp = detectTypicalPrintedGlyphDp(
                            adjusted, columnContentWidthDp
                        )
                    )
                } finally {
                    if (adjusted !== sample) adjusted.recycle()
                }
            } finally {
                sample.recycle()
            }
        }.getOrNull()
    }
    return null
}

internal fun estimatedBookQuestionUnits(
    question: YksQuestion,
    columnWidthDp: Float,
    actualImageRatios: Map<String, Float>,
    imageTypographyScales: Map<String, Float> = emptyMap()
): Float {
    val normalizedWidth = (question.normRight - question.normLeft).coerceAtLeast(0.08f)
    val normalizedHeight = (question.normBottom - question.normTop).coerceAtLeast(0.04f)
    // Match the ACTUAL drawing width: 42dp publisher/number gutter + 4dp gap.
    // The old 108dp deduction underestimated tall book crops by up to 50%.
    val usableImageWidthDp = (columnWidthDp - 46f).coerceAtLeast(90f)
    val desiredScale = (imageTypographyScales[question.id] ?: 1f)
        .coerceIn(0.60f, 1.90f)
    // Mirror fittedBookImageSizeDp: increasing the scale cannot make an
    // image wider than its actual content column. The previous estimator
    // multiplied by 1.9 while the Image remained capped at 1.0, leaving a
    // giant invisible vertical slot between otherwise adjacent questions.
    val imageWidthDp = minOf(usableImageWidthDp * desiredScale, usableImageWidthDp)
    val isImage = question.croppedImagePath.isNotBlank() ||
        question.imagePath.isNotBlank() || question.imageBase64.isNotBlank() ||
        question.pdfFilePath.isNotBlank()
    val contentHeightDp = if (isImage) {
        val fallback = if (normalizedWidth >= 0.98f && normalizedHeight >= 0.98f) 1f
            else (normalizedHeight / normalizedWidth * 1.45f).coerceIn(0.18f, 4.5f)
        imageWidthDp * (actualImageRatios[question.id] ?: fallback).coerceIn(0.12f, 5f)
    } else {
        val charWidth = (imageWidthDp / 7f).toInt().coerceAtLeast(12)
        val textLength = question.stemTop.length + question.stemBottom.length +
            question.bulletsOrSteps.sumOf { it.length }
        ((textLength / charWidth + 2).coerceAtLeast(2) * 19f + 46f +
            if (question.visualType != QuestionVisualType.NONE) 155f else 0f)
    }
    // Include the 72dp logo/number gutter only when it is taller than the
    // printed question. The gutter runs BESIDE the image, not above it.
    return (contentHeightDp + 8f).coerceAtLeast(74f) / columnWidthDp.coerceAtLeast(120f)
}

/** Preserve readable width and aspect ratio. Honors user scale (50%..150%) while preventing overflow. */
internal fun fittedBookImageSizeDp(
    availableWidth: Float,
    availableHeight: Float,
    widthOverHeight: Float,
    typographyScale: Float,
    singleQuestionLayout: Boolean = false
): Pair<Float, Float> {
    if (availableWidth <= 0f || availableHeight <= 0f) return 0f to 0f
    val ratio = widthOverHeight.takeIf { it.isFinite() && it > 0f } ?: 1f
    val visualScale = if (singleQuestionLayout) 1f else
        typographyScale.coerceIn(0.50f, 1.50f)
    val width = minOf(availableWidth * visualScale, availableWidth).coerceAtLeast(0f)
    return width to (width / ratio)
}

/**
 * Adaptive textbook rhythm for EVERY test (Polinomlar was one example).
 * The question bodies keep their *measured* heights. Only the space
 * between complete questions is opened up, with a strict ceiling.
 * This avoids both old extremes: SpaceBetween pinning to page edges and
 * 18dp compact stacking followed by a huge empty bottom half.
 */
internal data class BookColumnRhythm(
    val slotScale: Float,
    val gapDp: Float,
    val topInsetDp: Float
)

internal fun adaptiveBookColumnRhythm(
    measuredQuestionHeightsDp: List<Float>,
    paperHeightDp: Float
): BookColumnRhythm {
    if (measuredQuestionHeightsDp.isEmpty()) return BookColumnRhythm(1f, 0f, 0f)
    val count = measuredQuestionHeightsDp.size
    val content = measuredQuestionHeightsDp.sum().coerceAtLeast(1f)
    // Determine space from the *actual average question height*, not from
    // any 2/3/4-question page template. Large diagrams breathe more, short
    // equations stay comfortably grouped. Keep margins within visual limits.
    val intervals = (count - 1).coerceAtLeast(0)
    val averageQuestionHeight = content / count
    val preferredGap = (averageQuestionHeight * 0.10f).coerceIn(12f, 28f)
    val free = (paperHeightDp - content).coerceAtLeast(0f)
    val wantedGap = if (intervals == 0) 0f else
        minOf(preferredGap, (free - 10f).coerceAtLeast(18f))
    val remainingForContent = (paperHeightDp - wantedGap * intervals - 6f)
        .coerceAtLeast(1f)
    val scale = (remainingForContent / content).coerceIn(0.35f, 1f)
    // Keep the top aligned with the textbook's common baseline, but don't
    // force all leftover height to the final bottom edge when it's modest.
    val spare = (paperHeightDp - content * scale - wantedGap * intervals)
        .coerceAtLeast(0f)
    val inset = (spare * 0.04f).coerceIn(0f, 6f)
    return BookColumnRhythm(scale, wantedGap, inset)
}

/**
 * Use the real bottommost printed question to size the visible sheet.
 *
 * A physical book page is not a mandate to show 300dp of white blank space
 * below a short final column. Never stretch a small crop vertically or pin
 * questions to the very bottom merely to pretend that the page is full.
 * Instead end the white page a small safe margin after its actual content,
 * leaving the surrounding reader canvas neutral. Tall/full pages retain
 * their full available height, so writing/stylus coordinates do not shift.
 */
internal fun visibleBookPaperHeightDp(
    viewportHeightDp: Float,
    leftHeightsDp: List<Float>,
    rightHeightsDp: List<Float>,
    leftRhythm: BookColumnRhythm,
    rightRhythm: BookColumnRhythm
): Float {
    if (viewportHeightDp <= 0f) return 0f
    fun occupied(heights: List<Float>, rhythm: BookColumnRhythm): Float {
        if (heights.isEmpty()) return 0f
        val count = heights.size
        return heights.sum() * rhythm.slotScale +
            ((count - 1) * rhythm.gapDp) + rhythm.topInsetDp
    }
    // 12dp top padding + 2dp column top + 2dp bottom + 24dp breathing room.
    val consumed = maxOf(
        occupied(leftHeightsDp, leftRhythm),
        occupied(rightHeightsDp, rightRhythm)
    ) + 40f
    return consumed.coerceIn(
        minOf(viewportHeightDp, 240f), viewportHeightDp
    )
}

private fun bookColumnHeightRhythm(
    questions: List<YksQuestion>,
    columnWidthDp: Float,
    paperHeightDp: Float,
    imageRatios: Map<String, Float>,
    typographyScales: Map<String, Float>
): BookColumnRhythm = adaptiveBookColumnRhythm(
    questions.map { question ->
        estimatedBookQuestionUnits(
            question, columnWidthDp, imageRatios, typographyScales
        ) * columnWidthDp
    },
    paperHeightDp
)

/**
 * A paper-style flow layout. Each column holds as many whole question images as
 * its usable height permits, not a fixed count of one or two questions.
 * Oversized questions get a column by themselves and are scaled to fit.
 */
internal fun packQuestionsIntoTwoColumnPages(
    questions: List<YksQuestion>,
    columnCapacity: Float = 2.1f,
    columnWidthDp: Float = 360f,
    actualImageRatios: Map<String, Float> = emptyMap(),
    imageTypographyScales: Map<String, Float> = emptyMap()
): List<TwoColumnQuestionPage> {
    if (questions.isEmpty()) return listOf(TwoColumnQuestionPage(emptyList(), emptyList()))

    // Measure what will actually be drawn. Never fix a page to a 2x2 grid:
    // a tall diagram might own a column, while six genuinely short equations
    // can comfortably share one. Preserve reading order across every page.
    val width = columnWidthDp.coerceAtLeast(120f)
    val paperHeight = (columnCapacity * width).coerceAtLeast(100f)
    val heights = FloatArray(questions.size) { i ->
        estimatedBookQuestionUnits(
            questions[i], width, actualImageRatios, imageTypographyScales
        ) * width
    }
    // Candidate counts come ONLY from how many *whole* measured questions
    // physically fit in the available height. No 4-per-page, 6-per-column,
    // or publisher-specific numerical quotas are permitted.
    fun naturalColumnLimit(start: Int): Int {
        var totalMeasuredHeight = 0f
        var included = 0
        for (index in start until questions.size) {
            totalMeasuredHeight += heights[index]
            val requiredBreathing = included * 18f
            // Preserve readable font: a multi-question column is rejected if
            // it would require more than 7% downscaling, even with minimum gap.
            if (included > 0 &&
                totalMeasuredHeight * .93f + requiredBreathing > paperHeight - 6f
            ) break
            included++
        }
        // A single unusually tall figure always gets its own fitted column.
        return included.coerceAtLeast(1)
    }

    // Global dynamic programming instead of a page-by-page greedy "fit the
    // most now" strategy. It prevents leaving the final page with one lone
    // question and allows both columns to reach comparable visual heights.
    val n = questions.size
    val bestCost = DoubleArray(n + 1) { Double.POSITIVE_INFINITY }
    val bestLeft = IntArray(n)
    val bestRight = IntArray(n)
    bestCost[n] = 0.0

    for (start in n - 1 downTo 0) {
        for (leftCount in 1..minOf(naturalColumnLimit(start), n - start)) {
            val leftHeight = heights.slice(start until start + leftCount)
            val leftRhythm = adaptiveBookColumnRhythm(leftHeight, paperHeight)
            // Do not squeeze several printed questions just to reduce pages.
            if (leftCount > 1 && leftRhythm.slotScale < .93f) continue

            for (rightCount in 0..minOf(
                naturalColumnLimit(start + leftCount), n - start - leftCount
            )) {
                if (rightCount == 0 && n - start > 1) continue
                val next = start + leftCount + rightCount
                val rightHeight = heights.slice(start + leftCount until next)
                val rightRhythm = adaptiveBookColumnRhythm(rightHeight, paperHeight)
                if (rightCount > 1 && rightRhythm.slotScale < .93f) continue
                if (!bestCost[next].isFinite()) continue

                fun occupation(values: List<Float>, rhythm: BookColumnRhythm): Float {
                    if (values.isEmpty()) return 0f
                    return (values.sum() * rhythm.slotScale +
                        (values.size - 1) * rhythm.gapDp + rhythm.topInsetDp
                    ).coerceIn(0f, paperHeight)
                }
                val leftFill = occupation(leftHeight, leftRhythm) / paperHeight
                val rightFill = occupation(rightHeight, rightRhythm) / paperHeight
                val blankPenalty = (1f - leftFill) * (1f - leftFill) * 52f +
                    (1f - rightFill) * (1f - rightFill) * 52f
                val imbalancePenalty = kotlin.math.abs(leftFill - rightFill) * 32f
                val shrinkPenalty =
                    (1f - leftRhythm.slotScale) * 85f +
                    (1f - rightRhythm.slotScale) * 85f
                // Fewer pages are useful, but never at the expense of tiny
                // print or forced two-by-two layouts. A lone final page gets
                // a modest penalty so neighboring pages reflow naturally.
                val loneColumnPenalty = if (rightCount == 0) 24f else 0f
                // If more questions remain, never prefer a nearly empty
                // 1+1 opening page to a balanced use of subsequent content.
                // A genuinely sparse LAST page is acceptable; stretching
                // questions or squeezing diagrams to erase blank space isn't.
                val prematureBlankPenalty = if (next < n &&
                    leftFill < .35f && rightFill < .35f
                ) 140f else 0f
                // Optimize the full test by REAL filled area, typography
                // and comfortable gaps. There is deliberately no target
                // question count and no forced even 6+6 / 4+4 page split.
                // Some long questions correctly occupy a whole column.
                val score = 110.0 + blankPenalty + imbalancePenalty +
                    shrinkPenalty + loneColumnPenalty + prematureBlankPenalty +
                    bestCost[next]
                if (score < bestCost[start] - 0.0001) {
                    bestCost[start] = score
                    bestLeft[start] = leftCount
                    bestRight[start] = rightCount
                }
            }
        }
        // Every source is representable, even a gigantic diagram which must
        // be fitted alone. Never lose or reorder a question.
        if (!bestCost[start].isFinite()) {
            bestCost[start] = 110.0 + bestCost[start + 1]
            bestLeft[start] = 1
            bestRight[start] = 0
        }
    }
    val pages = mutableListOf<TwoColumnQuestionPage>()
    var start = 0
    while (start < n) {
        val split = start + bestLeft[start].coerceAtLeast(1)
        val finish = split + bestRight[start]
        pages += TwoColumnQuestionPage(
            questions.subList(start, split),
            questions.subList(split, finish)
        )
        start = finish
    }
    return pages
}

/**
 * 345 All Star 1-to-1 Test Çözüm, Optik Form, Test Sonu Fırça İzi (`✓`/`✗`),
 * `⋮` (Üç Nokta) Popover Menüsü, Yan Yana İkiz Soru Ekranı ve Başarı Durumu (Karne) Sayfası.
 */
@Composable
fun LiveSolvingBoardScreen(
    session: ActiveSolvingSession,
    strokes: List<DrawStroke>,
    currentCanvasKey: String,
    activeTool: DrawingTool,
    selectedStrokeSizeIndex: Int,
    penThickness: Float = 35f,
    selectedPenColorHex: Long,
    onExitSession: () -> Unit,
    onToggleViewMode: () -> Unit,
    onSelectTool: (DrawingTool) -> Unit,
    onSelectStrokeSize: (Int) -> Unit,
    onSelectPenThickness: (Float) -> Unit = {},
    onSelectPenColor: (Long) -> Unit,
    onAddStroke: (String, List<Offset>, List<Float>, DrawingTool) -> Unit,
    onBookPageChanged: (Int) -> Unit,
    onClearCanvas: (String) -> Unit,
    onSelectQuestionIndex: (Int) -> Unit,
    onSelectOption: (Int, String) -> Unit,
    onToggleOpticalForm: () -> Unit,
    onToggleStatsDrawer: () -> Unit,
    onToggleQuestionMap: () -> Unit,
    onFinishTest: () -> Unit,
    onGoToStatsTab: () -> Unit,
    onAskAiForQuestion: (YksQuestion) -> Unit,
    onSolveTwinTest: () -> Unit = {},
    onStrengthenFromFrequency: (com.example.util.FrequencyPracticeTarget) -> Unit = {},
    onGenerateTwinQuestion: (YksQuestion, (TwinQuestionModalData) -> Unit) -> Unit = { _, _ -> },
    onGenerateSolutionForQuestion: (YksQuestion, (String) -> Unit) -> Unit = { _, _ -> },
    onAddQuestionToKumbara: (YksQuestion) -> Unit = {},
    customPublishers: List<YksPublisherInfo> = emptyList(),
    pageLayoutConfig: com.example.util.TestPageLayoutConfig = com.example.util.TestPageLayoutConfig(),
    onUpdatePageLayoutConfig: ((com.example.util.TestPageLayoutConfig) -> Unit)? = null
) {
    var isImmersive by rememberSaveable(session.testItem.id) { mutableStateOf(false) }
    var activeLayoutConfig by remember(pageLayoutConfig) { mutableStateOf(pageLayoutConfig) }
    var showPageLayoutSheet by remember { mutableStateOf(false) }
    BackHandler(onBack = onExitSession)
    key(session.testItem.id) {
        MeasuredBookPages(
            questions = session.testItem.questions,
            customPublishers = customPublishers,
            isImmersive = isImmersive,
            layoutConfig = activeLayoutConfig
        ) { pages, scales ->
            LiveSolvingBoardContent(
                session = session,
                strokes = strokes,
                currentCanvasKey = currentCanvasKey,
                activeTool = activeTool,
                selectedStrokeSizeIndex = selectedStrokeSizeIndex,
                penThickness = penThickness,
                selectedPenColorHex = selectedPenColorHex,
                onExitSession = onExitSession,
                onToggleViewMode = onToggleViewMode,
                onSelectTool = onSelectTool,
                onSelectStrokeSize = onSelectStrokeSize,
                onSelectPenThickness = onSelectPenThickness,
                onSelectPenColor = onSelectPenColor,
                onAddStroke = onAddStroke,
                onBookPageChanged = onBookPageChanged,
                onClearCanvas = onClearCanvas,
                onSelectQuestionIndex = onSelectQuestionIndex,
                onSelectOption = onSelectOption,
                onToggleOpticalForm = onToggleOpticalForm,
                onToggleStatsDrawer = onToggleStatsDrawer,
                onToggleQuestionMap = onToggleQuestionMap,
                onFinishTest = onFinishTest,
                onGoToStatsTab = onGoToStatsTab,
                onAskAiForQuestion = onAskAiForQuestion,
                onSolveTwinTest = onSolveTwinTest,
                onStrengthenFromFrequency = onStrengthenFromFrequency,
                onGenerateTwinQuestion = onGenerateTwinQuestion,
                onGenerateSolutionForQuestion = onGenerateSolutionForQuestion,
                onAddQuestionToKumbara = onAddQuestionToKumbara,
                customPublishers = customPublishers,
                packedBookPages = pages,
                imageTypographyScales = scales,
                layoutConfig = activeLayoutConfig,
                onOpenPageLayoutSettings = { showPageLayoutSheet = true },
                isImmersive = isImmersive,
                onToggleImmersive = { isImmersive = !isImmersive }
            )
        }
    }
    if (showPageLayoutSheet) {
        TestPageLayoutSettingsBottomSheet(
            config = activeLayoutConfig,
            onConfigChange = { updated ->
                activeLayoutConfig = updated
                onUpdatePageLayoutConfig?.invoke(updated)
            },
            onDismiss = { showPageLayoutSheet = false }
        )
    }
}

@Composable
private fun LiveSolvingBoardContent(
    session: ActiveSolvingSession,
    strokes: List<DrawStroke>,
    currentCanvasKey: String,
    activeTool: DrawingTool,
    selectedStrokeSizeIndex: Int,
    penThickness: Float = 35f,
    selectedPenColorHex: Long,
    onExitSession: () -> Unit,
    onToggleViewMode: () -> Unit,
    onSelectTool: (DrawingTool) -> Unit,
    onSelectStrokeSize: (Int) -> Unit,
    onSelectPenThickness: (Float) -> Unit = {},
    onSelectPenColor: (Long) -> Unit,
    onAddStroke: (String, List<Offset>, List<Float>, DrawingTool) -> Unit,
    onBookPageChanged: (Int) -> Unit,
    onClearCanvas: (String) -> Unit,
    onSelectQuestionIndex: (Int) -> Unit,
    onSelectOption: (Int, String) -> Unit,
    onToggleOpticalForm: () -> Unit,
    onToggleStatsDrawer: () -> Unit,
    onToggleQuestionMap: () -> Unit,
    onFinishTest: () -> Unit,
    onGoToStatsTab: () -> Unit,
    onAskAiForQuestion: (YksQuestion) -> Unit,
    onSolveTwinTest: () -> Unit = {},
    onStrengthenFromFrequency: (com.example.util.FrequencyPracticeTarget) -> Unit = {},
    onGenerateTwinQuestion: (YksQuestion, (TwinQuestionModalData) -> Unit) -> Unit = { _, _ -> },
    onGenerateSolutionForQuestion: (YksQuestion, (String) -> Unit) -> Unit = { _, _ -> },
    onAddQuestionToKumbara: (YksQuestion) -> Unit = {},
    customPublishers: List<YksPublisherInfo> = emptyList(),
    packedBookPages: List<TwoColumnQuestionPage>,
    imageTypographyScales: Map<String, Float>,
    layoutConfig: com.example.util.TestPageLayoutConfig = com.example.util.TestPageLayoutConfig(),
    onOpenPageLayoutSettings: (() -> Unit)? = null,
    isImmersive: Boolean,
    onToggleImmersive: () -> Unit
) {
    val questions = session.testItem.questions
    val totalQuestions = questions.size.coerceAtLeast(1)
    val density = LocalDensity.current.density
    val totalBookPages = packedBookPages.size.coerceAtLeast(1)
    val currentQuestionId = questions.getOrNull(session.currentQuestionIndex)?.id
    val initialBookPage = packedBookPages.indexOfFirst { page ->
        page.questions.any { it.id == currentQuestionId }
    }.coerceAtLeast(0)
    val bookPagerState = rememberPagerState(
        initialPage = initialBookPage,
        pageCount = { totalBookPages }
    )
    val singleQuestionPagerState = rememberPagerState(
        initialPage = session.currentQuestionIndex.coerceIn(0, totalQuestions - 1),
        pageCount = { totalQuestions }
    )
    val currentBookPageIndex = bookPagerState.currentPage
    val displayedCanvasKey = if (session.viewMode == BoardViewMode.FULL_PAGE) {
        StylusInputPolicy.canvasKey(session.testItem.id, currentBookPageIndex)
    } else {
        val q = questions.getOrNull(singleQuestionPagerState.currentPage)
        "q_${q?.id ?: singleQuestionPagerState.currentPage}"
    }
    // Hide previous-page ink during the frame before the ViewModel receives
    // the pager's new selected page. Never draw it on the next PDF page.
    val displayedStrokes = if (currentCanvasKey == displayedCanvasKey) strokes else emptyList()
    val pagerScope = rememberCoroutineScope()
    var fingerDrawingEnabled by rememberSaveable(session.testItem.id) { mutableStateOf(false) }
    // Smart S Pen is the only pen mode. Fingers always pan/swipe/zoom.
    LaunchedEffect(session.testItem.id) { fingerDrawingEnabled = false }

    LaunchedEffect(bookPagerState.settledPage, session.viewMode, packedBookPages) {
        if (session.viewMode == BoardViewMode.FULL_PAGE) {
            onBookPageChanged(bookPagerState.settledPage)
        }
    }

    // Keep HorizontalPager synced when currentQuestionIndex is updated externally
    LaunchedEffect(session.currentQuestionIndex, session.viewMode, packedBookPages) {
        if (session.viewMode == BoardViewMode.FULL_PAGE) {
            val targetQuestionId = questions.getOrNull(session.currentQuestionIndex)?.id
            val targetPage = packedBookPages.indexOfFirst { page ->
                page.questions.any { it.id == targetQuestionId }
            }.coerceAtLeast(0)
            if (targetPage != bookPagerState.currentPage && !bookPagerState.isScrollInProgress) {
                bookPagerState.scrollToPage(targetPage)
            }
        } else {
            val targetQ = session.currentQuestionIndex.coerceIn(0, totalQuestions - 1)
            if (targetQ != singleQuestionPagerState.currentPage && !singleQuestionPagerState.isScrollInProgress) {
                singleQuestionPagerState.scrollToPage(targetQ)
            }
        }
    }

    // Update ViewModel question index when the user swipes horizontally between book pages
    LaunchedEffect(bookPagerState.settledPage, session.viewMode) {
        if (session.viewMode == BoardViewMode.FULL_PAGE) {
            val page = packedBookPages.getOrNull(bookPagerState.settledPage)
                ?: TwoColumnQuestionPage(emptyList(), emptyList())
            val firstQuestionIndex = questions.indexOfFirst { it.id == page.questions.firstOrNull()?.id }
                .coerceIn(0, totalQuestions - 1)
            val currentPage = packedBookPages.indexOfFirst { candidate ->
                candidate.questions.any { it.id == questions.getOrNull(session.currentQuestionIndex)?.id }
            }
            if (currentPage != bookPagerState.settledPage) {
                onSelectQuestionIndex(firstQuestionIndex)
            }
        }
    }

    LaunchedEffect(singleQuestionPagerState.settledPage, session.viewMode) {
        if (session.viewMode == BoardViewMode.SINGLE_QUESTION) {
            val settledQ = singleQuestionPagerState.settledPage.coerceIn(0, totalQuestions - 1)
            if (session.currentQuestionIndex != settledQ) {
                onSelectQuestionIndex(settledQ)
            }
        }
    }

    // Sub-screens inside LiveSolvingBoardScreen:
    // 1. Full-Screen Report Card ("▸ Başarı Durumuna Git" / Karne)
    var showFullReportCardScreen by remember(session.testItem.id) { mutableStateOf(false) }
    var showFrequencyFromDrawer by remember(session.testItem.id) { mutableStateOf(false) }
    var showFinishConfirmation by remember(session.testItem.id) { mutableStateOf(false) }
    val screenContext = LocalContext.current
    DisposableEffect(isImmersive, screenContext) {
        var context: android.content.Context? = screenContext
        var activity: Activity? = null
        while (context != null) {
            if (context is Activity) {
                activity = context
                break
            }
            context = (context as? ContextWrapper)?.baseContext
        }
        val window = activity?.window
        if (window != null && isImmersive) {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null && isImmersive) {
                WindowInsetsControllerCompat(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Finishing keeps the reviewed book visible. Results open only
    // from the explicit top-right "Sonuçlar" button.

    // 2. Full-Screen Side-by-Side "İkiz Soru" Comparison Screen
    var activeTwinScreenQuestion by remember { mutableStateOf<YksQuestion?>(null) }
    var activeTwinScreenData by remember { mutableStateOf<TwinQuestionModalData?>(null) }
    var isGeneratingTwin by remember { mutableStateOf(false) }
    var twinRequestVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(activeTwinScreenQuestion?.id, twinRequestVersion, isGeneratingTwin) {
        if (activeTwinScreenQuestion != null && isGeneratingTwin) {
            delay(150_000L)
            twinRequestVersion++
            activeTwinScreenData = TwinQuestionModalData(
                topicTitle = activeTwinScreenQuestion?.topicHeader.orEmpty(),
                errorMessage = "İkiz soru üretimi zaman aşımına uğradı. Yeniden deneyebilirsin."
            )
            isGeneratingTwin = false
        }
    }

    // 3. `⋮` (Three-Dot) Popover active question number
    var openThreeDotQuestionNumber by remember { mutableStateOf<Int?>(null) }

    // 4. On-demand "Çözüm İzle" & "Konu Anlatımı" Modals
    var activeSolutionModalQuestion by remember { mutableStateOf<YksQuestion?>(null) }
    var activeSolutionText by remember { mutableStateOf("") }
    var isGeneratingSolution by remember { mutableStateOf(false) }
    var solutionLoadFailed by remember { mutableStateOf(false) }
    var solutionRequestVersion by remember { mutableIntStateOf(0) }
    val solutionRequestGate = remember(session.testItem.id) { StoredSolutionRequestGate() }
    fun dismissStoredSolution() {
        solutionRequestGate.cancel()
        activeSolutionModalQuestion = null
        isGeneratingSolution = false
    }
    fun openStoredSolution(question: YksQuestion, forceReload: Boolean = false) {
        solutionRequestGate.cancel()
        activeSolutionModalQuestion = question
        activeSolutionText = ""
        solutionLoadFailed = false
        // Stored explanations are already on the question; opening one needs no model or loading fiction.
        if (!forceReload && StoredQuestionSolution.from(question, "").hasSolution) {
            activeSolutionText = question.stepByStepSolution
            isGeneratingSolution = false
            return
        }
        isGeneratingSolution = true
        solutionRequestVersion++
        val token = solutionRequestGate.begin(question.id)
        runCatching {
            onGenerateSolutionForQuestion(question) { text ->
                if (solutionRequestGate.accepts(token, question.id) &&
                    activeSolutionModalQuestion?.id == question.id) {
                    solutionRequestGate.cancel()
                    activeSolutionText = text
                    isGeneratingSolution = false
                }
            }
        }.onFailure {
            if (solutionRequestGate.accepts(token, question.id)) {
                solutionRequestGate.cancel()
                isGeneratingSolution = false
                solutionLoadFailed = true
            }
        }
    }
    LaunchedEffect(activeSolutionModalQuestion?.id, solutionRequestVersion, isGeneratingSolution) {
        if (activeSolutionModalQuestion != null && isGeneratingSolution) {
            delay(20000)
            solutionRequestGate.cancel()
            isGeneratingSolution = false
            solutionLoadFailed = true
        }
    }
    DisposableEffect(solutionRequestGate) {
        onDispose { solutionRequestGate.cancel() }
    }
    var activeTopicLectureQuestion by remember { mutableStateOf<YksQuestion?>(null) }

    // Zoom & Pan state for the book page
    var isZoomMagnifierEnabled by remember(displayedCanvasKey, session.viewMode) { mutableStateOf(false) }
    var zoomScale by remember(displayedCanvasKey, session.viewMode) { mutableFloatStateOf(1f) }
    var panOffset by remember(displayedCanvasKey, session.viewMode) { mutableStateOf(Offset.Zero) }
    // Keep the accessibility percentage in the toolbar's composition snapshot.
    val zoomStateDescription = "${(zoomScale * 100).toInt()}%"
    val singleQuestionScrollStates = remember(session.testItem.id) {
        mutableMapOf<String, ScrollState>()
    }
    val displayedDocumentScroll = singleQuestionScrollStates.getOrPut(displayedCanvasKey) { ScrollState(0) }

    BackHandler {
        when {
            activeSolutionModalQuestion != null -> dismissStoredSolution()
            activeTopicLectureQuestion != null -> {
                activeTopicLectureQuestion = null
            }
            activeTwinScreenQuestion != null -> {
                activeTwinScreenQuestion = null
                activeTwinScreenData = null
            }
            showFrequencyFromDrawer -> {
                showFrequencyFromDrawer = false
            }
            showFullReportCardScreen -> {
                showFullReportCardScreen = false
            }
            openThreeDotQuestionNumber != null -> {
                openThreeDotQuestionNumber = null
            }
            isImmersive -> {
                onToggleImmersive()
            }
            else -> onExitSession()
        }
    }

    // Compute Doğru / Yanlış / Boş counts for Review Mode & Karne
    val correctCount = remember(questions, session.selectedAnswers) {
        questions.count { q -> q.answerKeyVerified && session.selectedAnswers[q.number] == q.correctOption }
    }
    val wrongCount = remember(questions, session.selectedAnswers) {
        questions.count { q ->
            val chosen = session.selectedAnswers[q.number]
            q.answerKeyVerified && !chosen.isNullOrBlank() && chosen != q.correctOption
        }
    }
    val ungradedCount = remember(questions, session.selectedAnswers) {
        questions.count { q -> !q.answerKeyVerified }
    }
    val emptyCount = remember(questions, session.selectedAnswers) {
        questions.count { q -> q.answerKeyVerified && session.selectedAnswers[q.number].isNullOrBlank() }
    }

    // =========================================================================
    // ALT EKRAN 1: TAM EKRAN YAN YANA "İKİZ SORU" EKRANI (345 ALL STAR BİREBİR)
    // =========================================================================
    val twinTargetQ = activeTwinScreenQuestion
    if (twinTargetQ != null) {
        FullScreenTwinQuestionComparisonView(
            session = session,
            originalQuestion = twinTargetQ,
            twinData = activeTwinScreenData,
            isLoadingTwin = isGeneratingTwin,
            studentAnswer = session.selectedAnswers[twinTargetQ.number],
            activeTool = activeTool,
            selectedStrokeSizeIndex = selectedStrokeSizeIndex,
            penThickness = penThickness,
            selectedPenColorHex = selectedPenColorHex,
            onSelectTool = onSelectTool,
            onSelectStrokeSize = onSelectStrokeSize,
            onSelectPenThickness = onSelectPenThickness,
            onSelectPenColor = onSelectPenColor,
            onBackToBookReview = {
                activeTwinScreenQuestion = null
                activeTwinScreenData = null
            },
            onOpenReportCard = {
                activeTwinScreenQuestion = null
                activeTwinScreenData = null
                showFullReportCardScreen = true
            },
            onRegenerateTwin = {
                if (!isGeneratingTwin) {
                    activeTwinScreenData = null
                    isGeneratingTwin = true
                    val requestVersion = ++twinRequestVersion
                    onGenerateTwinQuestion(twinTargetQ) { generated ->
                        if (requestVersion == twinRequestVersion && activeTwinScreenQuestion?.id == twinTargetQ.id) {
                            activeTwinScreenData = generated
                            isGeneratingTwin = false
                        }
                    }
                }
            }
        )
        return
    }

    if (showFrequencyFromDrawer && session.isReviewMode) {
        val frequencySummary = remember(
            questions, session.selectedAnswers, session.subjectName, session.topicName
        ) {
            TestAnswerAnalyticsEngine.calculate(
                questions, session.selectedAnswers, session.subjectName, session.topicName
            )
        }
        PremiumMistakeAnalysisScreen(
            title = session.testItem.fullTitle.ifBlank { session.topicName },
            summary = frequencySummary,
            onBack = { showFrequencyFromDrawer = false },
            topicName = session.topicName,
            onStrengthen = onStrengthenFromFrequency
        )
        return
    }

    // =========================================================================
    // ALT EKRAN 2: TAM EKRAN "▸ BAŞARI DURUMUNA GİT" KARNE & ANALİZ EKRANI
    // =========================================================================
    if (showFullReportCardScreen && session.isReviewMode) {
        FullScreenSuccessReportCardView(
            session = session,
            correctCount = correctCount,
            wrongCount = wrongCount,
            emptyCount = emptyCount,
            ungradedCount = ungradedCount,
            showTwinSuggestion = wrongCount > 0,
            onBackToBookPage = {
                showFullReportCardScreen = false
            },
            onSolveTwinTestForWrongQuestions = {
                onSolveTwinTest()
            },
            onStrengthenFromFrequency = onStrengthenFromFrequency,
            onWatchQuestionSolution = { q ->
                showFullReportCardScreen = false
                openStoredSolution(q)
            },
            onOpenTwinForQuestion = { q ->
                showFullReportCardScreen = false
                activeTwinScreenQuestion = q
                activeTwinScreenData = null
                isGeneratingTwin = true
                val requestVersion = ++twinRequestVersion
                    onGenerateTwinQuestion(q) { generated ->
                        if (requestVersion == twinRequestVersion && activeTwinScreenQuestion?.id == q.id) {
                            activeTwinScreenData = generated
                            isGeneratingTwin = false
                        }
                    }
            }
        )
        return
    }

    // =========================================================================
    // ANA EKRAN: ÜST BEYAZ BAR + ÜST KOYU LACİVERT ÇİZİM BARI + TAM EKRAN 2 SÜTUNLU KİTAP SAYFASI + SAĞ ÇEKMECELER
    // =========================================================================
    Column(
        modifier = Modifier
            .fillMaxSize()
            // White across the ENTIRE viewport, including the area past the
            // last printed question, side gutters and tablet edges.
            .background(Color.White)
            .testTag("live_solving_board")
    ) {
        // 1. ÜST BEYAZ BAR: Solda Geri Oku, Ortada Test Başlığı, Sağda Mavi Hap Buton
        if (!isImmersive) {
            BookPageTopBar345(
                testTitle = if (session.viewMode == BoardViewMode.SINGLE_QUESTION)
                    "Soru ${session.currentQuestionIndex + 1}/$totalQuestions"
                    else session.testItem.fullTitle.ifBlank { "${session.topicName} - Test 1" },
                onOpenOpticalForm = null,
                isReviewMode = session.isReviewMode,
                onBack = onExitSession,
                onFinishTest = { showFinishConfirmation = true },
                onOpenReportCard = {
                    openThreeDotQuestionNumber = null
                    showFullReportCardScreen = true
                }
            )
        }

        // 2. ÜST KOYU LACİVERT (`#1E295B`) ÇİZİM VE ARAÇ BARI (Üst Beyaz Barın Hemen Altında!)
        Box(modifier = Modifier.zIndex(40f)) {
            BottomDarkNavyDrawingBar345(
                viewMode = session.viewMode,
                activeTool = activeTool,
                isZoomMagnifierEnabled = isZoomMagnifierEnabled,
                fingerDrawingEnabled = fingerDrawingEnabled,
                onChangeFingerDrawing = { fingerDrawingEnabled = it },
                selectedStrokeSizeIndex = selectedStrokeSizeIndex,
                penThickness = penThickness,
                selectedPenColorHex = selectedPenColorHex,
                elapsedSeconds = session.elapsedSeconds,
                targetDurationSeconds = session.targetDurationSeconds,
                onToggleViewMode = onToggleViewMode,
                onSelectTool = { tool ->
                    isZoomMagnifierEnabled = false
                    onSelectTool(tool)
                },
                onToggleZoom = {
                    isZoomMagnifierEnabled = !isZoomMagnifierEnabled
                    if (isZoomMagnifierEnabled && zoomScale < 1.35f) {
                        zoomScale = 1.45f
                    } else if (!isZoomMagnifierEnabled) {
                        zoomScale = 1f
                        panOffset = Offset.Zero
                    }
                },
                onSelectStrokeSize = onSelectStrokeSize,
                onSelectPenThickness = onSelectPenThickness,
                onSelectPenColor = onSelectPenColor,
                onClearCanvas = { onClearCanvas(displayedCanvasKey) },
                onOpenPageLayoutSettings = onOpenPageLayoutSettings,
                isImmersive = isImmersive,
                showImmersiveToggle = true,
                onToggleImmersive = onToggleImmersive
            )
        }

        // 3. TAM EKRAN KİTAP SAYFASI ALANI VE SAĞDAN AÇILIR ÇEKMECELER (OPTİK & İSTATİSTİKLER)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val currentStrokePx = penThicknessToPx(penThickness)
            val eraserStrokePx = listOf(18f, 38f, 68f)
                .getOrElse(selectedStrokeSizeIndex) { 38f }

            // Zero-latency raw path buffer (mutated directly during S-Pen/Mouse drag without recomposing UI tree!)
            val liveRawPath = remember { Path() }
            var liveTool by remember { mutableStateOf(activeTool) }
            var hardwareEraser by remember { mutableStateOf(false) }
            val liveRawPoints = remember { mutableListOf<Offset>() }
            val liveRawPressures = remember { mutableListOf<Float>() }
            val liveDrawTick = remember { mutableIntStateOf(0) }

            // Book Page Container with Horizontal Finger/Hand Swipe (HorizontalPager) + Pinch-to-Zoom support
            Box(
                modifier = Modifier
                    // Edge-to-edge real book paper. No rounded grey frame,
                    // external 8dp gutter or card shadow around the sheet.
                    .fillMaxSize()
                    .padding(end = TestPageDimensions.edgeHandleSpace(packedBookPages.firstOrNull()?.columnCount ?: 2))
                    .background(Color.White)
                    .testTag("board_ink_viewport")
                    .semantics { stateDescription = zoomStateDescription }
                    // Initial pass wins two-finger gestures before the pager
                    // can mistake a pinch for a page change. Single-finger
                    // touches at normal zoom remain available to page swipe.
                    .pointerInput(
                        activeTool, fingerDrawingEnabled, isZoomMagnifierEnabled,
                        displayedCanvasKey, session.viewMode
                    ) {
                        awaitEachGesture {
                            var hadNavigationGesture = false
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val fingers = event.changes.filter {
                                    it.pressed && it.type == PointerType.Touch
                                }
                                val stylusPressed = event.changes.any {
                                    it.pressed && it.type == PointerType.Stylus
                                }
                                // A palm must not scroll the question underneath
                                // the pen while a stroke is in progress.
                                if (stylusPressed) {
                                    event.changes.filter { it.type == PointerType.Touch }
                                        .forEach { it.consume() }
                                }
                                val pinch = StylusInputPolicy.usePinch(
                                    fingers.size, stylusPressed
                                )
                                val oneFingerPan = !pinch && fingers.size == 1 &&
                                    !stylusPressed && zoomScale > 1.02f &&
                                    (!fingerDrawingEnabled ||
                                        activeTool == DrawingTool.HAND_PAN ||
                                        isZoomMagnifierEnabled)
                                if (pinch || oneFingerPan) {
                                    val pan = event.calculatePan()
                                    val scaleChange = if (pinch) {
                                        event.calculateZoom().takeIf {
                                            it.isFinite() && it > 0f
                                        } ?: 1f
                                    } else 1f
                                    val nextScale = (zoomScale * scaleChange)
                                        .coerceIn(1f, 4f)
                                    val focus = if (pinch) {
                                        event.calculateCentroid(useCurrent = false)
                                    } else {
                                        Offset(size.width / 2f, size.height / 2f)
                                    }
                                    val viewportCenter = Offset(
                                        size.width / 2f, size.height / 2f
                                    )
                                    val factor = nextScale / zoomScale.coerceAtLeast(1f)
                                    val proposed = panOffset + pan +
                                        (focus - viewportCenter - panOffset) *
                                        (1f - factor)
                                    val maxX = (size.width * (nextScale - 1f) / 2f)
                                        .coerceAtLeast(0f)
                                    val maxY = (size.height * (nextScale - 1f) / 2f)
                                        .coerceAtLeast(0f)
                                    zoomScale = nextScale
                                    panOffset = if (nextScale <= 1.02f) Offset.Zero else {
                                        Offset(
                                            proposed.x.coerceIn(-maxX, maxX),
                                            proposed.y.coerceIn(-maxY, maxY)
                                        )
                                    }
                                    hadNavigationGesture = true
                                }
                                if (hadNavigationGesture && !stylusPressed) {
                                    event.changes.filter {
                                        it.type == PointerType.Touch
                                    }.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }

                    .motionEventSpy { event ->
                        hardwareEraser = (0 until event.pointerCount).any { i ->
                            StylusInputPolicy.isHardwareEraser(event.getToolType(i), event.buttonState)
                        }
                    }
                    // Ink input belongs on the shared parent, not the drawing
                    // Canvas sibling. Smart-mode finger taps/swipes can then
                    // reach the page pager; S Pen strokes are consumed early.
                    .then(
                        if ((activeTool == DrawingTool.PEN ||
                                    activeTool == DrawingTool.HIGHLIGHTER ||
                                    activeTool == DrawingTool.SMART_SHAPE ||
                                    activeTool == DrawingTool.ERASER) &&
                                openThreeDotQuestionNumber == null) {
                        Modifier.pointerInput(
                            activeTool,
                            penThickness,
                            selectedStrokeSizeIndex,
                            selectedPenColorHex,
                            fingerDrawingEnabled,
                            currentBookPageIndex,
                            session.viewMode,
                            displayedCanvasKey
                        ) {
                            awaitPointerEventScope {
                              var queuedPenDown: PointerInputChange? = null
                              while (true) {
                                // Do not wait for every palm pointer to lift
                                // before recognizing the next S Pen contact.
                                val down = if (queuedPenDown != null) {
                                    queuedPenDown
                                } else {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val pen = event.changes.firstOrNull {
                                        it.pressed && !it.previousPressed &&
                                            it.type == PointerType.Stylus && !it.isConsumed
                                    }
                                    pen ?: event.changes.firstOrNull {
                                        it.pressed && !it.previousPressed && !it.isConsumed &&
                                            StylusInputPolicy.canInk(it.type, fingerDrawingEnabled) &&
                                            event.changes.count { p -> p.pressed && p.type == PointerType.Touch } == 1 &&
                                            event.changes.none { p -> p.pressed && p.type == PointerType.Stylus }
                                    }
                                }
                                queuedPenDown = null
                                if (down == null) continue
                                if (bookPagerState.isScrollInProgress || singleQuestionPagerState.isScrollInProgress) continue
                                down.consume()

                                fun documentPoint(position: Offset) = BoardInkCoordinates.toDocument(
                                    position, Size(size.width.toFloat(), size.height.toFloat()),
                                    zoomScale, panOffset, displayedDocumentScroll?.value?.toFloat() ?: 0f
                                )
                                val start = documentPoint(down.position)
                                liveTool = if (hardwareEraser) DrawingTool.ERASER else activeTool
                                fun commitSegment() {
                                    if (liveRawPoints.isEmpty()) return
                                    val points = liveRawPoints.toMutableList()
                                    val pressures = liveRawPressures.toMutableList()
                                    if (points.size == 1) {
                                        points.add(points.first() + Offset(0.05f, 0.05f))
                                        pressures.add(pressures.first())
                                    }
                                    onAddStroke(displayedCanvasKey, points, pressures, liveTool)
                                }

                                val activeId = down.id
                                liveRawPoints.clear()
                                liveRawPressures.clear()
                                liveRawPoints.add(start)
                                liveRawPressures.add(
                                    if (down.type == PointerType.Stylus) down.pressure else 1f
                                )
                                liveRawPath.reset()
                                liveRawPath.moveTo(start.x, start.y)
                                liveDrawTick.intValue++

                                var prevPt = start
                                var cancelled = false
                                try {
                                    // Observe every pointer, not just the stylus.
                                    // A second finger cancels an unfinished
                                    // finger stroke and becomes a pinch gesture.
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val active = event.changes.firstOrNull {
                                            it.id == activeId
                                        }
                                        if (active == null) {
                                            cancelled = true
                                            break
                                        }
                                        val touchCount = event.changes.count {
                                            it.pressed && it.type == PointerType.Touch
                                        }
                                        if (down.type == PointerType.Touch &&
                                            (touchCount >= 2 || event.changes.any {
                                                it.pressed && it.type == PointerType.Stylus
                                            })
                                        ) {
                                            queuedPenDown = event.changes.firstOrNull {
                                                it.pressed && !it.previousPressed &&
                                                    it.type == PointerType.Stylus && !it.isConsumed
                                            }
                                            cancelled = true
                                            break
                                        }
                                        if (active.isConsumed && active.pressed) {
                                            cancelled = true
                                            break
                                        }
                                        val curPt = documentPoint(active.position)
                                        val nextTool = if (hardwareEraser && active.type == PointerType.Stylus)
                                            DrawingTool.ERASER else activeTool
                                        if (active.pressed && nextTool != liveTool) {
                                            commitSegment()
                                            liveTool = nextTool
                                            liveRawPoints.clear()
                                            liveRawPressures.clear()
                                            liveRawPoints.add(curPt)
                                            liveRawPressures.add(active.pressure)
                                            liveRawPath.reset()
                                            liveRawPath.moveTo(curPt.x, curPt.y)
                                            prevPt = curPt
                                            liveDrawTick.intValue++
                                        }
                                        if ((curPt - prevPt).getDistance() >= 0.6f) {
                                            liveRawPoints.add(curPt)
                                            liveRawPressures.add(
                                                if (down.type == PointerType.Stylus) {
                                                    active.pressure
                                                } else 1f
                                            )
                                            val mid = (prevPt + curPt) * 0.5f
                                            liveRawPath.quadraticTo(
                                                prevPt.x, prevPt.y, mid.x, mid.y
                                            )
                                            prevPt = curPt
                                            liveDrawTick.intValue++
                                        }
                                        if (!active.pressed) break
                                        active.consume()
                                    }
                                    if (!cancelled) {
                                        if (liveRawPoints.size == 1) {
                                            // Tapping the pen should make a dot.
                                            liveRawPoints.add(
                                                liveRawPoints.first() + Offset(0.05f, 0.05f)
                                            )
                                            liveRawPressures.add(
                                                liveRawPressures.first()
                                            )
                                        }
                                        if (liveRawPoints.size >= 2) {
                                            onAddStroke(
                                                displayedCanvasKey,
                                                liveRawPoints.toList(),
                                                liveRawPressures.toList(), liveTool
                                            )
                                        }
                                    }
                                } finally {
                                    liveRawPoints.clear()
                                    liveRawPressures.clear()
                                    liveRawPath.reset()
                                    liveDrawTick.intValue++
                                }
                              }
                            }
                        }
                        } else {
                            Modifier
                        }
                    )
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    }
            ) {
                // 1. YATAY KAYDIRMALI (SWIPE / SCROLL-SNAP) GERÇEK KİTAP SAYFALARI (3D Sayfa Çevirme Hissiyle)
                if (session.viewMode == BoardViewMode.FULL_PAGE) {
                    HorizontalPager(
                        state = bookPagerState,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = zoomScale <= 1.02f && openThreeDotQuestionNumber == null,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("book_horizontal_pager")
                    ) { pageIdx ->
                        val pageQuestions = packedBookPages.getOrNull(pageIdx)
                            ?: TwoColumnQuestionPage(emptyList(), emptyList())
                        val pageOffset = (bookPagerState.currentPage - pageIdx) + bookPagerState.currentPageOffsetFraction

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    cameraDistance = 16f * density
                                    rotationY = (-pageOffset * 14f).coerceIn(-18f, 18f)
                                    val scale = (1f - (abs(pageOffset) * 0.04f)).coerceIn(0.94f, 1f)
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = (1f - (abs(pageOffset) * 0.15f)).coerceIn(0.8f, 1f)
                                }
                        ) {
                            RealTwoColumnBookPageSurface(
                                session = session,
                                pageQuestions = pageQuestions,
                                scrollState = singleQuestionScrollStates.getOrPut(StylusInputPolicy.canvasKey(session.testItem.id, pageIdx)) { ScrollState(0) },
                                imageTypographyScales = imageTypographyScales,
                                layoutConfig = layoutConfig,
                                currentBookPageIndex = pageIdx,
                                totalBookPages = totalBookPages,
                                customPublishers = customPublishers,
                                openThreeDotQuestionNumber = openThreeDotQuestionNumber,
                                onDismissThreeDotMenu = {
                                    openThreeDotQuestionNumber = null
                                },
                                onToggleThreeDotMenu = { qNum ->
                                    openThreeDotQuestionNumber = if (openThreeDotQuestionNumber == qNum) null else qNum
                                },
                                onSelectOptionInBook = { qNum, opt ->
                                    onSelectOption(qNum, opt)
                                },
                                onOpenTopicLecture = { q ->
                                    openThreeDotQuestionNumber = null
                                    activeTopicLectureQuestion = q
                                },
                                onOpenSolutionModal = { q ->
                                    openThreeDotQuestionNumber = null
                                    openStoredSolution(q)
                                },
                                onOpenTwinScreen = { q ->
                                    openThreeDotQuestionNumber = null
                                    activeTwinScreenQuestion = q
                                    activeTwinScreenData = null
                                    isGeneratingTwin = true
                                    val requestVersion = ++twinRequestVersion
                    onGenerateTwinQuestion(q) { generated ->
                        if (requestVersion == twinRequestVersion && activeTwinScreenQuestion?.id == q.id) {
                            activeTwinScreenData = generated
                            isGeneratingTwin = false
                        }
                    }
                                },
                                onAddToKumbara = { q ->
                                    openThreeDotQuestionNumber = null
                                    onAddQuestionToKumbara(q)
                                },
                                onPrevBookPage = {
                                    if (pageIdx > 0) {
                                        pagerScope.launch {
                                            bookPagerState.animateScrollToPage(pageIdx - 1)
                                        }
                                    }
                                },
                                onNextBookPage = {
                                    if (pageIdx < totalBookPages - 1) {
                                        pagerScope.launch {
                                            bookPagerState.animateScrollToPage(pageIdx + 1)
                                        }
                                    }
                                }
                            )
                        }
                    }
                } else {
                    HorizontalPager(
                        state = singleQuestionPagerState,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = zoomScale <= 1.02f && openThreeDotQuestionNumber == null,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("single_question_horizontal_pager")
                    ) { qIdx ->
                        val currentQuestion = questions.getOrNull(qIdx) ?: return@HorizontalPager
                        val pageOffset = (singleQuestionPagerState.currentPage - qIdx) + singleQuestionPagerState.currentPageOffsetFraction

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    cameraDistance = 16f * density
                                    rotationY = (-pageOffset * 14f).coerceIn(-18f, 18f)
                                    val scale = (1f - (abs(pageOffset) * 0.04f)).coerceIn(0.94f, 1f)
                                    scaleX = scale
                                    scaleY = scale
                                }
                        ) {
                            SingleQuestionFocusedPageSurface(
                                session = session,
                                question = currentQuestion,
                                scrollState = singleQuestionScrollStates.getOrPut("q_${currentQuestion.id}") { ScrollState(0) },
                                customPublishers = customPublishers,
                                openThreeDotQuestionNumber = openThreeDotQuestionNumber,
                                onDismissThreeDotMenu = {
                                    openThreeDotQuestionNumber = null
                                },
                                onToggleThreeDotMenu = { qNum ->
                                    openThreeDotQuestionNumber = if (openThreeDotQuestionNumber == qNum) null else qNum
                                },
                                onSelectOption = { opt ->
                                    onSelectOption(currentQuestion.number, opt)
                                },
                                onOpenTopicLecture = { q ->
                                    openThreeDotQuestionNumber = null
                                    activeTopicLectureQuestion = q
                                },
                                onOpenSolutionModal = { q ->
                                    openThreeDotQuestionNumber = null
                                    openStoredSolution(q)
                                },
                                onOpenTwinScreen = { q ->
                                    openThreeDotQuestionNumber = null
                                    activeTwinScreenQuestion = q
                                    activeTwinScreenData = null
                                    isGeneratingTwin = true
                                    val requestVersion = ++twinRequestVersion
                    onGenerateTwinQuestion(q) { generated ->
                        if (requestVersion == twinRequestVersion && activeTwinScreenQuestion?.id == q.id) {
                            activeTwinScreenData = generated
                            isGeneratingTwin = false
                        }
                    }
                                },
                                onAddToKumbara = { q ->
                                    openThreeDotQuestionNumber = null
                                    onAddQuestionToKumbara(q)
                                }
                            )
                        }
                    }
                }

                // 2. SIFIR GECİKMELİ S-PEN ÇİZİMİ VE BLENDMODE.CLEAR SİLGİ
                // Akıllı Mod: stylus draws; a palm or single finger never inks.
                // Çizim Modu: stylus or one finger inks; two fingers cancel
                // the finger stroke and navigate without ink damage.
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                ) {
                    // Keep ink history cached during writing. Eraser alone
                    // temporarily composites old ink with its Clear path in
                    // the preview layer for immediate visual feedback.
                    translate(top = -(displayedDocumentScroll?.value ?: 0).toFloat()) {
                    val liveErasing = liveTool == DrawingTool.ERASER &&
                        liveDrawTick.intValue >= 0 && liveRawPoints.isNotEmpty()
                    if (!liveErasing) {
                        displayedStrokes.forEach { stroke -> drawSmoothStroke(stroke) }
                    }
                    }
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .testTag("live_spen_preview")
                ) {
                    translate(top = -(displayedDocumentScroll?.value ?: 0).toFloat()) {
                    val tick = liveDrawTick.intValue
                    // The clear stroke must share an offscreen layer with the
                    // ink it erases. Otherwise erasing would only appear on UP.
                    if (liveTool == DrawingTool.ERASER &&
                        liveRawPoints.isNotEmpty()
                    ) {
                        displayedStrokes.forEach { stroke -> drawSmoothStroke(stroke) }
                    }
                    if (tick >= 0 && liveRawPoints.isNotEmpty()) {
                        val isEraser = liveTool == DrawingTool.ERASER
                        val isHigh = liveTool == DrawingTool.HIGHLIGHTER
                        if (isEraser) {
                            if (liveRawPoints.size >= 2) {
                                drawPath(
                                    path = liveRawPath,
                                    color = Color.Transparent,
                                    style = Stroke(
                                        width = eraserStrokePx,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    ),
                                    blendMode = BlendMode.Clear
                                )
                            } else {
                                drawCircle(
                                    color = Color.Transparent,
                                    radius = eraserStrokePx / 2f,
                                    center = liveRawPoints.first(),
                                    blendMode = BlendMode.Clear
                                )
                            }
                            drawCircle(
                                color = Color(0xFF3475F5).copy(alpha = 0.8f),
                                radius = eraserStrokePx / 2f,
                                center = liveRawPoints.last(),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        } else if (liveRawPoints.size == 1) {
                            drawCircle(
                                color = if (isHigh) Color(selectedPenColorHex).copy(alpha = 0.36f)
                                    else Color(selectedPenColorHex),
                                radius = penThicknessToPx(penThickness, isHigh) / 2f,
                                center = liveRawPoints.first()
                            )
                        } else if (!isHigh &&
                            liveTool != DrawingTool.SMART_SHAPE &&
                            liveRawPressures.size == liveRawPoints.size &&
                            hasStylusPressureVariation(liveRawPressures)
                        ) {
                            drawPressureInkSegments(
                                liveRawPoints,
                                liveRawPressures,
                                Color(selectedPenColorHex),
                                currentStrokePx
                            )
                        } else {
                            val smoothPath = buildSmoothInkPath(
                                liveRawPoints,
                                liveTool == DrawingTool.SMART_SHAPE
                            )
                            drawPath(
                                path = smoothPath,
                                color = if (isHigh) Color(selectedPenColorHex).copy(alpha = 0.36f)
                                    else Color(selectedPenColorHex),
                                style = Stroke(
                                    width = penThicknessToPx(penThickness, isHigh),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                    }
                }
            }

            // 4. SAĞ KENAR DİKEY KULAKÇIK VE AÇILIR ÇEKMECELER
            // - Çözüm esnasında: kompakt "⠿ Optik"
            // - Test bittiğinde (Review Mode): "◔ İstatistikler"
            if (!session.isReviewMode) {
                RightSlidingOpticalFormDrawer345(
                    isOpen = session.isOpticalFormDrawerOpen,
                    showClosedTab = true,
                    questions = questions,
                    selectedAnswers = session.selectedAnswers,
                    onToggleDrawer = onToggleOpticalForm,
                    onSelectOption = onSelectOption,
                    onFinishTest = { showFinishConfirmation = true },
                    // Float beside the book, vertically centered as in the
                    // reference tablet screenshot; the grip has its own safe edge margin.
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .zIndex(35f)
                )
            } else {
                RightSlidingStatsDrawer345(
                    isOpen = session.isStatsDrawerOpen,
                    testTitle = session.testItem.fullTitle.ifBlank { "${session.topicName} - Test 1" },
                    questions = questions,
                    selectedAnswers = session.selectedAnswers,
                    correctCount = correctCount,
                    wrongCount = wrongCount,
                    emptyCount = emptyCount,
                    ungradedCount = ungradedCount,
                    onToggleDrawer = onToggleStatsDrawer,
                    onOpenMistakeAnalysis = { showFrequencyFromDrawer = true },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .zIndex(35f)
                )
            }
        }
    }

    if (showFinishConfirmation) {
        Dialog(
            onDismissRequest = { showFinishConfirmation = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 16.dp,
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(20.dp).testTag("finish_confirmation_dialog")
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Testi bitirelim mi?",
                        color = Color(0xFF25215D),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 21.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Testi bitirdiğinde önce cevapların işaretlendiği kitap sayfasını göreceksin. Sağ üstteki Sonuçlar butonuyla karneni açabilirsin.",
                        color = Color(0xFF475569),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    val answeredCount = questions.count { !session.selectedAnswers[it.number].isNullOrBlank() }
                    Text(
                        text = "$answeredCount / ${questions.size} soru işaretlendi • ${questions.size - answeredCount} boş",
                        color = Color(0xFF74708F),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = {
                            showFinishConfirmation = false
                            openThreeDotQuestionNumber = null
                            onFinishTest()
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3475F5)),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_finish_test_btn")
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Evet, Testi Bitir.", fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { showFinishConfirmation = false }, modifier = Modifier.testTag("cancel_finish_test_btn")) {
                        Text("Vazgeç", color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // =========================================================================
    // MODAL 1: "ÇÖZÜM İZLE" ADIM ADIM AI ÇÖZÜM PENCERESİ
    // =========================================================================
    val solutionQ = activeSolutionModalQuestion
    if (solutionQ != null) {
        QuestionSolutionDialog(
            question = solutionQ,
            returnedText = activeSolutionText,
            isLoading = isGeneratingSolution,
            loadFailed = solutionLoadFailed,
            onRetry = { openStoredSolution(solutionQ, forceReload = true) },
            onDismiss = ::dismissStoredSolution
        )
    }
    // v1.4.8: The old placeholder summary is replaced by an embedded, verified
    // teacher lesson. The pager state stays in place while the dialog is open.
    activeTopicLectureQuestion?.let { lectureQuestion ->
        SmartTopicLectureDialog(
            question = lectureQuestion,
            examType = session.examType.name,
            subjectName = session.subjectName,
            fallbackTopic = session.topicName,
            onDismiss = { activeTopicLectureQuestion = null }
        )
    }

}

/**
 * 1. ÜST BEYAZ BAR (345 ALL STAR BİREBİR):
 * - Solda Geri Oku (`←`)
 * - Ortada Test Başlığı (Örn: `Bağıl Hareket - Günlük Hayat` veya `Türev - Test 1`)
 * - Sağda:
 *   * Çözüm esnasında: Mavi hap buton `⚑ Testi Bitir`
 *   * İnceleme (Review) modunda: Mavi hap buton `▸ Başarı Durumuna Git`
 */
@Composable
private fun BookPageTopBar345(
    testTitle: String,
    isReviewMode: Boolean,
    onBack: () -> Unit,
    onFinishTest: () -> Unit,
    onOpenReportCard: () -> Unit,
    onOpenOpticalForm: (() -> Unit)? = null
) {
    Surface(
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("board_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = Color(0xFF25215D)
                )
            }

            Text(
                text = testTitle,
                color = Color(0xFF25215D),
                fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )

            if (onOpenOpticalForm != null) {
                IconButton(
                    onClick = onOpenOpticalForm,
                    modifier = Modifier.size(40.dp).testTag("single_optical_toggle")
                ) {
                    Icon(Icons.Default.Apps, contentDescription = "Optik formu aç",
                        tint = Color(0xFF24205E))
                }
            }
            if (!isReviewMode) {
                Surface(
                    onClick = onFinishTest,
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF3475F5),
                    shadowElevation = 2.dp,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("top_finish_test_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Testi Bitir",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                Surface(
                    onClick = onOpenReportCard,
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF3475F5),
                    shadowElevation = 2.dp,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("go_to_report_card_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Sonuçlar",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * GERÇEK KİTAP SAYFASI DÜZENİ (ÇİFT SÜTUNLU SAYFA):
 * - Sorular ayrı ayrı kutu/kart içine KONULMAZ!
 * - Tek bir beyaz kitap sayfası zemini üzerinde ortadan ince dikey çizgiyle ayrılmış
 *   2 SÜTUNLU; soru sayısı her sütunda görsellerin gerçek boyuna göre değişir.
 */
@Composable
private fun RealTwoColumnBookPageSurface(
    session: ActiveSolvingSession,
    pageQuestions: TwoColumnQuestionPage,
    scrollState: ScrollState,
    imageTypographyScales: Map<String, Float>,
    layoutConfig: com.example.util.TestPageLayoutConfig = com.example.util.TestPageLayoutConfig(),
    currentBookPageIndex: Int,
    totalBookPages: Int,
    customPublishers: List<YksPublisherInfo>,
    openThreeDotQuestionNumber: Int?,
    onDismissThreeDotMenu: () -> Unit,
    onToggleThreeDotMenu: (Int) -> Unit,
    onSelectOptionInBook: (Int, String) -> Unit,
    onOpenTopicLecture: (YksQuestion) -> Unit,
    onOpenSolutionModal: (YksQuestion) -> Unit,
    onOpenTwinScreen: (YksQuestion) -> Unit,
    onAddToKumbara: (YksQuestion) -> Unit,
    onPrevBookPage: () -> Unit = {},
    onNextBookPage: () -> Unit = {}
) {
    val columnCount = pageQuestions.columnCount
    val pageHoriz = TestPageDimensions.pageHorizontal(columnCount)
    val gapDp = TestPageDimensions.questionGap(layoutConfig.spacingPreset)
    Column(
        Modifier.fillMaxSize().background(Color.White)
            .padding(horizontal = pageHoriz, vertical = TestPageDimensions.pageVertical)
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewportHeight = maxHeight
            val leftCount = pageQuestions.left.size
            val separatorPositions = remember { mutableStateListOf<Triple<Float, Float, Float>>() }
            Layout(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(scrollState)
                    .testTag("test_page_columns")
                    .drawBehind {
                        if (columnCount == 2 && pageQuestions.right.isNotEmpty() && layoutConfig.showCenterColumnDivider) {
                            drawLine(
                                Color(0xFFCBD5E1),
                                Offset(size.width / 2f, 0f),
                                Offset(size.width / 2f, size.height),
                                1.dp.toPx()
                            )
                        }
                        if (layoutConfig.separatorStyle != com.example.util.QuestionSeparatorStyle.NONE) {
                            val dashEffect = if (layoutConfig.separatorStyle == com.example.util.QuestionSeparatorStyle.DASHED_LINE) {
                                PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
                            } else null
                            val sepColor = if (layoutConfig.separatorStyle == com.example.util.QuestionSeparatorStyle.SOLID_LINE) {
                                Color(0xFFCBD5E1)
                            } else {
                                Color(0xFFEAF0F7)
                            }
                            for ((xStart, xEnd, yPos) in separatorPositions) {
                                drawLine(
                                    color = sepColor,
                                    start = Offset(xStart, yPos),
                                    end = Offset(xEnd, yPos),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                            }
                        }
                    },
                content = {
                    pageQuestions.questions.forEach { q ->
                        key(q.id) {
                            BookPageQuestionItem345(
                                question = q, maxImageHeight = (viewportHeight - 34.dp).coerceAtLeast(1.dp),
                                imageTypographyScale = imageTypographyScales[q.id] ?: 1f, isReviewMode = session.isReviewMode,
                                selectedOption = session.selectedAnswers[q.number],
                                isThreeDotMenuOpen = openThreeDotQuestionNumber == q.number,
                                customPublishers = customPublishers,
                                onToggleThreeDotMenu = { onToggleThreeDotMenu(q.number) },
                                onSelectOption = { onSelectOptionInBook(q.number, it) },
                                onOpenTopicLecture = { onOpenTopicLecture(q) },
                                onOpenSolutionModal = { onOpenSolutionModal(q) },
                                onOpenTwinScreen = { onOpenTwinScreen(q) },
                                onAddToKumbara = { onAddToKumbara(q) }
                            )
                        }
                    }
                }
            ) { measurables, constraints ->
                val gutter = TestPageDimensions.gutter.roundToPx()
                val width = if (columnCount == 2)
                    ((constraints.maxWidth - gutter) / 2).coerceAtLeast(1) else constraints.maxWidth
                val measured = measurables.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
                val columns = if (columnCount == 2)
                    listOf(measured.take(leftCount), measured.drop(leftCount)) else listOf(measured)
                val gap = gapDp.roundToPx()
                fun occupied(items: List<androidx.compose.ui.layout.Placeable>) =
                    items.sumOf { it.height } + (items.size - 1).coerceAtLeast(0) * gap
                val height = (columns.maxOfOrNull(::occupied) ?: 0).coerceAtLeast(1)
                val newSeparators = ArrayList<Triple<Float, Float, Float>>(measured.size)
                columns.forEachIndexed { column, items ->
                    val colX = if (column == 0) 0 else constraints.maxWidth - width
                    var y = 0
                    items.forEachIndexed { idx, item ->
                        y += item.height
                        if (idx < items.lastIndex) {
                            newSeparators.add(Triple(colX + 36.dp.toPx(), (colX + width).toFloat(), y + gap * 0.5f))
                        }
                        y += gap
                    }
                }
                if (separatorPositions.toList() != newSeparators) {
                    separatorPositions.clear()
                    separatorPositions.addAll(newSeparators)
                }
                layout(constraints.maxWidth, height) {
                    columns.forEachIndexed { column, items ->
                        val colX = if (column == 0) 0 else constraints.maxWidth - width
                        var y = 0
                        items.forEach { item ->
                            item.placeRelative(colX, y)
                            y += item.height + gap
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = Color(0xFFE8EEF5), thickness = 1.dp)
        Row(
            Modifier.fillMaxWidth().height(TestPageDimensions.footerHeight).testTag("test_page_footer"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Sorular " + (pageQuestions.questions.firstOrNull()?.number ?: 0) +
                    "–" + (pageQuestions.questions.lastOrNull()?.number ?: 0) +
                    if (scrollState.maxValue > 0) " · Dikey kaydır" else "",
                color = Color(0xFF64748B), fontSize = 11.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevBookPage, enabled = currentBookPageIndex > 0,
                    modifier = Modifier.size(34.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Önceki sayfa", Modifier.size(16.dp))
                }
                Text(
                    (currentBookPageIndex + 1).toString() + " / $totalBookPages",
                    color = Color(0xFF334155), fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onNextBookPage, enabled = currentBookPageIndex < totalBookPages - 1,
                    modifier = Modifier.size(34.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Sonraki sayfa", Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * KİTAP SAYFASINDAKİ TEKİL SORU GÖRÜNÜMÜ (KUTUSUZ / KARTSIZ SAF KİTAP DÜZENİ):
 * - Sol başta: Uygulama soru numarası (`1.`, `2.`, `3.`, `4.`)
 * - Test Bittiğinde (`isReviewMode = true`):
 *   * Sol üst köşede (soru numarasının hemen üstünde/yanında):
 *     - Doğruysa: Yeşil Tik (`✓`, `#16A34A`)
 *     - Yanlışsa: Kırmızı Çarpı (`✗`, `#DC2626`)
 *     - Boş bırakıldıysa: Gri Yuvarlak / Eksi (`⊖`, `#94A3B8`) — ASLA kırmızı çarpı atılmaz!
 *   * Sağ üst köşede yüksek z-index'li (`zIndex(50f)`) mavi (`#60A5FA`) yuvarlak içinde beyaz `⋮` (Üç Nokta) butonu
 *     ve tıklanınca hemen altında açılan 5 kademeli zorluk barlı Popover Kartı!
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BookPageQuestionItem345(
    question: YksQuestion,
    maxImageHeight: Dp = 560.dp,
    imageTypographyScale: Float = 1f,
    singleQuestionLayout: Boolean = false,
    isReviewMode: Boolean,
    selectedOption: String?,
    isThreeDotMenuOpen: Boolean,
    customPublishers: List<YksPublisherInfo>,
    onToggleThreeDotMenu: () -> Unit,
    onSelectOption: (String) -> Unit,
    onOpenTopicLecture: () -> Unit,
    onOpenSolutionModal: () -> Unit,
    onOpenTwinScreen: () -> Unit,
    onAddToKumbara: () -> Unit
) {
    val context = LocalContext.current
    var sourceDetailsVisible by remember(question.id) { mutableStateOf(false) }
    val sourcePublisher = remember(question.publisherId, question.publisherName, customPublishers) {
        customPublishers.firstOrNull { it.id == question.publisherId && question.publisherId.isNotBlank() }
            ?: SoruLabPublisherPresets.resolvePublisher(question.publisherName, customPublishers)
    }
    val preparedBitmaps = LocalTestBitmaps.current
    val croppedBitmap = if (preparedBitmaps.containsKey(question.id)) preparedBitmaps[question.id]
        else StudioImageAndPdfEngine.rememberGuaranteedQuestionBitmap(question)
    if (sourceDetailsVisible) {
        AlertDialog(
            onDismissRequest = { sourceDetailsVisible = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PublisherLogoEmblem(
                        publisher = sourcePublisher,
                        size = 44.dp,
                        overrideLogoPath = question.publisherLogoUri
                    )
                    Column {
                        Text("Soru kaynağı", color = Color(0xFF64748B),
                            style = MaterialTheme.typography.labelMedium)
                        Text(question.publisherName.ifBlank { "Kaynak belirtilmedi" },
                            color = Color(0xFF172554),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (question.sourceBookTitle.isNotBlank()) {
                        Text("Kitap: ${question.sourceBookTitle}")
                    }
                    if (question.sourceTestId.isNotBlank()) {
                        Text("Kaynak test: ${question.sourceTestId.replace("~", " • ")}")
                    }
                    if (question.sourcePageIndex >= 0) {
                        Text("PDF sayfası: ${question.sourcePageIndex + 1}")
                    }
                    if (question.sourcePrintedQuestionNumber > 0) {
                        Text("Kitaptaki soru: ${question.sourcePrintedQuestionNumber}")
                    }
                    Text("SoruLab'daki soru numarası: ${question.number}",
                        color = Color(0xFF64748B))
                }
            },
            confirmButton = {
                TextButton(onClick = { sourceDetailsVisible = false }) { Text("Kapat") }
            }
        )
    }

    val isEmpty = selectedOption.isNullOrBlank()
    val isUngraded = !question.answerKeyVerified
    val isCorrect = !isEmpty && question.answerKeyVerified && selectedOption == question.correctOption

    val questionActions: @Composable () -> Unit = {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    onClick = onToggleThreeDotMenu,
                    shape = CircleShape,
                    color = if (isThreeDotMenuOpen) Color(0xFF3475F5) else Color(0xFFF1F5F9),
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("three_dot_btn_${question.number}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "${question.number}. Soru Seçenekleri",
                            tint = if (isThreeDotMenuOpen) Color.White else Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (isThreeDotMenuOpen) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset = IntOffset(0, 100),
                        onDismissRequest = onToggleThreeDotMenu,
                        properties = PopupProperties(
                            focusable = true,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = true
                        )
                    ) {
                        ThreeDotQuestionPopoverCard345(
                            question = question,
                            selectedOption = selectedOption,
                            onOpenTopicLecture = onOpenTopicLecture,
                            onOpenSolutionModal = onOpenSolutionModal,
                            onOpenTwinScreen = onOpenTwinScreen,
                            onAddToKumbara = onAddToKumbara
                        )
                    }
                }
            }

    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isThreeDotMenuOpen) 55f else 1f)
            .testTag("book_question_${question.number}")
    ) {
        // 1. SORU İÇERİĞİ: Tekli modda üstte konu/logo kalabalığı olmadan saf soru odağı
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Sol oluk: kompakt logo ve soru numarası rozeti soru görseline maksimum yatay genişlik bırakır.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(if (singleQuestionLayout) 28.dp else 36.dp)
            ) {
                if (!singleQuestionLayout) {
                    Box(
                        modifier = Modifier.size(30.dp)
                            .testTag("question_source_logo_${question.number}")
                            .clickable { sourceDetailsVisible = true },
                        contentAlignment = Alignment.Center
                    ) {
                        PublisherLogoEmblem(
                            publisher = sourcePublisher,
                            size = 28.dp,
                            overrideLogoPath = question.publisherLogoUri
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                }
                Box(
                    modifier = Modifier.size(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${question.number}.",
                        color = Color(0xFF1E3A8A),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        textAlign = TextAlign.Center
                    )
                    if (isReviewMode) {
                        HandDrawnBrushMark345(
                            isEmpty = isEmpty || isUngraded,
                            isCorrect = isCorrect,
                            isUngraded = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Soru içeriği; sağ aksiyon marjı ayrı olduğundan görselin üstüne binmez.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val bitmap = croppedBitmap
                if (bitmap != null) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        // Exact bounded height avoids contradictory full-width /
                        // aspectRatio constraints cutting off tall PDF figures.
                        val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
                        val (fitWidth, fitHeight) = fittedBookImageSizeDp(
                            maxWidth.value, maxImageHeight.value, ratio,
                            imageTypographyScale, singleQuestionLayout
                        )
                        val fittedWidth = fitWidth.dp
                        val imageHeight = fitHeight.dp
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "${question.number}. Soru",
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.TopStart,
                            modifier = Modifier.width(fittedWidth).height(imageHeight)
                                .background(Color.White)
                                // The tag exists only once the decoded bitmap is
                                // actually composed, not while its IO is pending.
                                .testTag("question_crop_image_${question.number}")
                        )
                    }
                } else {
                    // Fallback vector/text rendering if a catalog question has no bitmap
                    if (question.stemTop.isNotBlank()) {
                        Text(
                            text = question.stemTop,
                            color = Color(0xFF1E293B),
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (question.visualType != QuestionVisualType.NONE) {
                        QuestionVectorDiagram(
                            visualType = question.visualType,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(155.dp)
                        )
                    }
                    if (question.bulletsOrSteps.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            question.bulletsOrSteps.forEach { step ->
                                Text(
                                    text = step,
                                    color = Color(0xFF334155),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    if (question.stemBottom.isNotBlank()) {
                        Text(
                            text = question.stemBottom,
                            color = Color(0xFF25215D),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val optionsContent: @Composable () -> Unit = {
                        listOf("A", "B", "C", "D", "E").forEach { optKey ->
                            val optVal = question.options[optKey] ?: optKey
                            val isChosen = selectedOption == optKey
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .then(if (singleQuestionLayout) Modifier.fillMaxWidth().heightIn(min = 44.dp) else Modifier)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            isReviewMode && question.answerKeyVerified && optKey == question.correctOption -> Color(0xFFDCFCE7)
                                            isChosen -> Color(0xFFDBEAFE)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable(enabled = !isReviewMode) { onSelectOption(optKey) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (optVal == optKey) "$optKey)" else "$optKey) $optVal",
                                    color = Color(0xFF1E293B),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    if (singleQuestionLayout) {
                        Column(Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) { optionsContent() }
                    } else {
                        FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) { optionsContent() }
                    }
                }
            }

            if (isReviewMode) questionActions()
        }
    }
}

/**
 * TEK SORU ODAKLI GÖRÜNÜM (Yatay Kaydırma / Swipe ile Sorular Arası Geçiş — Ok Butonları Yok)
 */
@Composable
private fun SingleQuestionFocusedPageSurface(
    session: ActiveSolvingSession,
    question: YksQuestion,
    scrollState: ScrollState,
    customPublishers: List<YksPublisherInfo>,
    openThreeDotQuestionNumber: Int?,
    onDismissThreeDotMenu: () -> Unit,
    onToggleThreeDotMenu: (Int) -> Unit,
    onSelectOption: (String) -> Unit,
    onOpenTopicLecture: (YksQuestion) -> Unit,
    onOpenSolutionModal: (YksQuestion) -> Unit,
    onOpenTwinScreen: (YksQuestion) -> Unit,
    onAddToKumbara: (YksQuestion) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        val isLandscape = maxWidth > maxHeight && maxWidth >= 560.dp
        val fitQuestionHeight = if (isLandscape) {
            (maxHeight - 40.dp).coerceIn(180.dp, 860.dp)
        } else {
            (maxHeight * 0.58f).coerceIn(180.dp, 620.dp)
        }

        if (openThreeDotQuestionNumber != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(15f)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissThreeDotMenu
                    )
            )
        }

        if (isLandscape) {
            // YATAY ÇÖZÜM DENEYİMİ: Sol %48 saf soru, sağ %52 geniş beyaz çözüm defteri alanı
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (openThreeDotQuestionNumber != null) 30f else 1f)
                    .testTag("single_question_content_${question.id}")
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxHeight()
                        .verticalScroll(scrollState)
                        .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 40.dp)
                ) {
                    BookPageQuestionItem345(
                        question = question,
                        maxImageHeight = fitQuestionHeight,
                        singleQuestionLayout = true,
                        isReviewMode = session.isReviewMode,
                        selectedOption = session.selectedAnswers[question.number],
                        isThreeDotMenuOpen = openThreeDotQuestionNumber == question.number,
                        customPublishers = customPublishers,
                        onToggleThreeDotMenu = { onToggleThreeDotMenu(question.number) },
                        onSelectOption = onSelectOption,
                        onOpenTopicLecture = { onOpenTopicLecture(question) },
                        onOpenSolutionModal = { onOpenSolutionModal(question) },
                        onOpenTwinScreen = { onOpenTwinScreen(question) },
                        onAddToKumbara = { onAddToKumbara(question) }
                    )
                    Spacer(Modifier.height(120.dp))
                }

                // İnce kitapçık/defter ayracı
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .padding(vertical = 12.dp)
                        .background(Color(0xFFE2E8F0))
                )

                // Sağ tarafta geniş, kesintisiz beyaz çözüm alanı (kalem/S-Pen için)
                Box(
                    modifier = Modifier
                        .weight(0.52f)
                        .fillMaxHeight()
                        .background(Color.White)
                        .testTag("single_question_landscape_scratchpad_${question.id}")
                )
            }
        } else {
            // DİKEY TEKLİ ODAK MODU: Altta optik ve üstte konu/logo olmadan saf soru + geniş çözüm boşluğu
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .zIndex(if (openThreeDotQuestionNumber != null) 30f else 1f)
                    .align(Alignment.TopCenter)
                    .testTag("single_question_content_${question.id}")
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BookPageQuestionItem345(
                    question = question,
                    maxImageHeight = fitQuestionHeight,
                    singleQuestionLayout = true,
                    isReviewMode = session.isReviewMode,
                    selectedOption = session.selectedAnswers[question.number],
                    isThreeDotMenuOpen = openThreeDotQuestionNumber == question.number,
                    customPublishers = customPublishers,
                    onToggleThreeDotMenu = { onToggleThreeDotMenu(question.number) },
                    onSelectOption = onSelectOption,
                    onOpenTopicLecture = { onOpenTopicLecture(question) },
                    onOpenSolutionModal = { onOpenSolutionModal(question) },
                    onOpenTwinScreen = { onOpenTwinScreen(question) },
                    onAddToKumbara = { onAddToKumbara(question) }
                )
                // Sorunun hemen altında kalemle rahat çözüm için temiz beyaz alan
                Spacer(Modifier.height(340.dp))
            }
        }
    }
}

/**
 * SOL ÜST KÖŞEDE EL ÇİZİMİ FIRÇA DARBESİ (KURAL 7):
 * - Doğruysa: Organik fırça darbesi stilinde büyük Yeşil Tik (`✓`, `#16A34A`)
 * - Yanlış İşaretlendiyse: Organik fırça darbesi stilinde büyük Kırmızı Çarpı (`✗`, `#DC2626`)
 * - Boş Bırakıldıysa: İçinde hiçbir çizgi/tire olmayan temiz, içi boş gri çember (`○`, `#94A3B8`)
 */
@Composable
private fun HandDrawnBrushMark345(
    isEmpty: Boolean,
    isCorrect: Boolean,
    isUngraded: Boolean = false,
    modifier: Modifier = Modifier
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(isEmpty, isCorrect, isUngraded) { appeared = true }
    val entrance by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (appeared) 1f else 0.70f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ), label = "hand_drawn_answer_mark"
    )
    Canvas(modifier.graphicsLayer {
        alpha = entrance.coerceIn(0.15f, 1f)
        scaleX = entrance
        scaleY = entrance
    }) {
        val w = size.width
        val h = size.height
        when {
            isCorrect -> {
                // Imperfect hand-painted ink, with a translucent second pigment
                // layer instead of a flat Material icon.
                val ink = Color(0xFF189551)
                val check = Path().apply {
                    moveTo(w * .09f, h * .55f)
                    quadraticTo(w * .23f, h * .60f, w * .38f, h * .85f)
                    cubicTo(w * .53f, h * .53f, w * .69f, h * .31f, w * .94f, h * .13f)
                }
                drawPath(check, ink.copy(alpha = .20f),
                    style = Stroke(width = w * .20f, cap = StrokeCap.Round,
                        join = StrokeJoin.Round))
                drawPath(check, ink,
                    style = Stroke(width = w * .105f, cap = StrokeCap.Round,
                        join = StrokeJoin.Round))
                drawLine(ink.copy(alpha = .54f),
                    Offset(w * .34f, h * .82f), Offset(w * .91f, h * .14f),
                    strokeWidth = w * .035f, cap = StrokeCap.Round)
            }
            isEmpty || isUngraded -> {
                val ring = Path().apply {
                    moveTo(w * .80f, h * .28f)
                    cubicTo(w * .55f, h * .02f, w * .16f, h * .16f, w * .13f, h * .48f)
                    cubicTo(w * .06f, h * .77f, w * .47f, h * .98f, w * .74f, h * .80f)
                    cubicTo(w * .92f, h * .69f, w * .91f, h * .40f, w * .80f, h * .28f)
                }
                drawPath(ring, Color(0xFF95A0B2).copy(alpha = .2f),
                    style = Stroke(width = w * .16f, cap = StrokeCap.Round))
                drawPath(ring, Color(0xFF9AA4B5),
                    style = Stroke(width = w * .07f, cap = StrokeCap.Round))
            }
            else -> {
                val redInk = Color(0xFFE03145)
                val first = Path().apply {
                    moveTo(w * .16f, h * .12f)
                    cubicTo(w * .36f, h * .33f, w * .54f, h * .61f, w * .85f, h * .90f)
                }
                val second = Path().apply {
                    moveTo(w * .88f, h * .13f)
                    cubicTo(w * .70f, h * .35f, w * .37f, h * .61f, w * .14f, h * .87f)
                }
                listOf(first, second).forEach { stroke ->
                    drawPath(stroke, redInk.copy(alpha = .16f),
                        style = Stroke(width = w * .22f, cap = StrokeCap.Round))
                    drawPath(stroke, redInk,
                        style = Stroke(width = w * .105f, cap = StrokeCap.Round))
                }
                drawLine(redInk.copy(alpha = .55f),
                    Offset(w * .21f, h * .17f), Offset(w * .81f, h * .86f),
                    strokeWidth = w * .025f, cap = StrokeCap.Round)
            }
        }
    }
}

/**
 * 3-C. SORUNUN SAĞ ÜSTÜNDEKİ `⋮` (ÜÇ NOKTA) POPOVER KARTI (345 All Star Birebir + 5'li Zorluk Barı):
 * 1. Üst Başlık: Kalın siyah `[X]. Soru` + sağında küçük yuvarlak durum ikonu (`✓` yeşil / `✕` kırmızı / `⊖` gri).
 * 2. 5 Kademeli Zorluk Seviyesi Barı: Turuncu (`#F97316`) + Gri (`#E2E8F0`), altında `Kolay - Orta - ÖSYM - Zor - Çok Zor`,
 *    üstünde siyah ters üçgen ok (`▼`).
 * 3. Durum Yazısı: `Doğru Yanıt`, `Yanlış Yanıt` veya `Boş Bırakıldı`.
 * 4. Alt Alta Oval Butonlar (`RoundedCornerShape(50)`):
 *    - `Konu Anlatımı`
 *    - `Çözüm İzle`
 *    - `İkiz Soru`
 *    - `Soru Kumbarama Ekle`
 */
@Composable
private fun ThreeDotQuestionPopoverCard345(
    question: YksQuestion,
    selectedOption: String?,
    onOpenTopicLecture: () -> Unit,
    onOpenSolutionModal: () -> Unit,
    onOpenTwinScreen: () -> Unit,
    onAddToKumbara: () -> Unit
) {
    // The verified answer and the learner's marked option are distinct.
    // Never label a guessed or missing answer key as the correct option.
    val verified = question.answerKeyVerified &&
        question.correctOption in setOf("A", "B", "C", "D", "E")
    val marked = selectedOption?.takeIf { it in setOf("A", "B", "C", "D", "E") }
    val isCorrect = verified && marked == question.correctOption
    val wrong = verified && marked != null && !isCorrect
    val difficultyFraction = when (question.difficulty) {
        DifficultyLevel.KOLAY -> .16f
        DifficultyLevel.ORTA, DifficultyLevel.OSYM -> .55f
        DifficultyLevel.ZOR -> .84f
        DifficultyLevel.COK_ZOR -> .96f
    }
    val difficultyLabel = when (question.difficulty) {
        DifficultyLevel.KOLAY -> "Kolay"
        DifficultyLevel.ORTA, DifficultyLevel.OSYM -> "Orta"
        DifficultyLevel.ZOR, DifficultyLevel.COK_ZOR -> "Zor"
    }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, Color(0xFFE9E8F0)),
        modifier = Modifier
            .width(306.dp)
            .testTag("three_dot_popover_${question.number}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${question.number}. Soru",
                    color = Color(0xFF292458),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (verified && marked != null) {
                    Box(
                        modifier = Modifier.size(24.dp)
                            .clip(CircleShape)
                            .background(if (isCorrect) Color(0xFF15961B) else Color(0xFFDE3E4A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCorrect) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (isCorrect) "Doğru" else "Yanlış",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                } else {
                    // Intentionally empty status ring: use a Spacer instead
                    // of an empty Box without a content lambda.
                    Spacer(
                        modifier = Modifier.size(22.dp)
                            .border(2.dp, Color(0xFFA5A4B3), CircleShape)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                    val barHeight = 10.dp.toPx()
                    val barTop = size.height - barHeight
                    val filled = (size.width * difficultyFraction)
                        .coerceIn(8.dp.toPx(), size.width)
                    drawRoundRect(
                        color = Color(0xFFE7E5EF),
                        topLeft = Offset(0f, barTop),
                        size = Size(size.width, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2f)
                    )
                    drawRoundRect(
                        color = Color(0xFFF68A39),
                        topLeft = Offset(0f, barTop),
                        size = Size(filled, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2f)
                    )
                    val triangleX = filled.coerceIn(7.dp.toPx(), size.width - 7.dp.toPx())
                    val triangle = Path().apply {
                        moveTo(triangleX - 5.dp.toPx(), 1.dp.toPx())
                        lineTo(triangleX + 5.dp.toPx(), 1.dp.toPx())
                        lineTo(triangleX, barTop - 2.dp.toPx())
                        close()
                    }
                    drawPath(triangle, Color(0xFF25215D))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("Kolay", "Orta", "Zor").forEach { name ->
                        Text(
                            name,
                            fontSize = 11.sp,
                            fontWeight = if (difficultyLabel == name) FontWeight.SemiBold
                                else FontWeight.Normal,
                            color = if (difficultyLabel == name) Color(0xFF292458)
                                else Color(0xFFA09DAF)
                        )
                    }
                }
            }

            Text(
                if (verified) "Doğru Yanıt" else "Cevap anahtarı doğrulanmadı",
                fontSize = 14.sp,
                color = Color(0xFF292458),
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("A", "B", "C", "D", "E").forEach { option ->
                    val correctAnswer = verified && option == question.correctOption
                    val wrongChoice = wrong && marked == option
                    Surface(
                        shape = CircleShape,
                        color = if (correctAnswer) Color(0xFF11881D) else Color.White,
                        border = if (correctAnswer) null
                            else BorderStroke(
                                if (wrongChoice) 2.dp else 1.5.dp,
                                if (wrongChoice) Color(0xFFDE3E4A) else Color(0xFFCECBD9)
                            ),
                        modifier = Modifier.weight(1f).height(40.dp)
                            .testTag("popover_answer_${question.number}_${option}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                option,
                                fontSize = 16.sp,
                                fontWeight = if (correctAnswer || wrongChoice)
                                    FontWeight.SemiBold else FontWeight.Normal,
                                color = if (correctAnswer) Color.White else
                                    if (wrongChoice) Color(0xFFDE3E4A) else Color(0xFF8B879C)
                            )
                        }
                    }
                }
            }

            if (marked != null && verified && !isCorrect) {
                Text(
                    "İşaretlediğin: $marked",
                    color = Color(0xFFDE3E4A),
                    fontSize = 12.sp,
                    modifier = Modifier.testTag("popover_marked_answer_${question.number}")
                )
            }

            Spacer(Modifier.height(8.dp))
            // All three study actions must be visible in the question menu.
            // They are routed to the already-wired question-specific callbacks.
            PopoverPillButton345(
                label = "Soru Çözümü",
                backgroundColor = Color(0xFF3475F5),
                textColor = Color.White,
                onClick = onOpenSolutionModal
            )
            PopoverPillButton345(
                label = "İkiz Soru",
                backgroundColor = Color(0xFFF1F0F5),
                textColor = Color(0xFF3475F5),
                onClick = onOpenTwinScreen
            )
            PopoverPillButton345(
                label = "Konu Anlatımı",
                backgroundColor = Color(0xFFEAF3FF),
                textColor = Color(0xFF2563EB),
                onClick = onOpenTopicLecture
            )
        }
    }
}

@Composable
private fun PopoverPillButton345(
    label: String,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = backgroundColor,
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * 2. SAĞDAN AÇILIR "OPTİK FORM" ÇEKMECESİ (Screenshot 1 ile birebir aynı!)
 * - Ekranın sağ kenarında dikey mavi `⠿ Optik Form` kulakçığı bulunur.
 * - Tıklanınca sağdan beyaz yuvarlatılmış çekmece kayarak açılır ve mavi `⠿ Optik Form`
 *   kulakçığı çekmecenin sol dış kenarına yapışık kalır!
 * - İçinde: Ortada kalın `Optik Form` başlığı, altında `1. (A) (B) (C) (D) (E)` satırları,
 *   en altta tam genişlikli mavi hap `⚑ Testi Bitir` butonu.
 */
@Composable
private fun RightSlidingOpticalFormDrawer345(
    isOpen: Boolean,
    showClosedTab: Boolean = true,
    questions: List<YksQuestion>,
    selectedAnswers: Map<Int, String>,
    onToggleDrawer: () -> Unit,
    onSelectOption: (Int, String) -> Unit,
    onFinishTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val drawerWidth = (configuration.screenWidthDp *
        if (configuration.screenWidthDp >= 600) 0.46f else 0.79f).dp
        .coerceIn(280.dp, 380.dp)
    // Keep the answer sheet compact on tablets. Additional questions still
    // scroll inside the panel, without a mostly empty full-height drawer.
    val requestedHeight = (132 + questions.size.coerceAtMost(6) * 56).dp
    val availableHeight = (configuration.screenHeightDp - 110).coerceAtLeast(214).dp
    val drawerHeight = requestedHeight.coerceAtMost(availableHeight)
    // Render only the handle while closed; the hidden drawer cannot
    // peek through or steal touch space along the book edge.
    if (!isOpen) {
        if (showClosedTab) {
            Box(modifier = modifier) {
                VerticalBlueSideTab345(
                    "Optik Form", false, onToggleDrawer, "optical_form_tab",
                    "Optik formu aç"
                )
            }
        }
        return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showClosedTab || isOpen) {
            VerticalBlueSideTab345(
                "Optik Form", false, onToggleDrawer, "optical_form_tab",
                if (isOpen) "Optik formu kapat" else "Optik formu aç"
            )
        }
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, Color(0xFFE8ECF2)),
            modifier = Modifier.width(drawerWidth).height(drawerHeight).then(
                if (isOpen) Modifier.testTag("optical_form_drawer")
                else Modifier.clearAndSetSemantics { }
            )
        ) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Reference photo: only a centered title, an open answer grid,
                // and one big blue Finish Test button. No redundant progress UI.
                Text(
                    "Optik Form", color = Color(0xFF181725),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                        .heightIn(max = (questions.size.coerceAtMost(6) * 56).dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    items(questions, key = { it.number }) { question ->
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            // The question grid remains centered and book-like,
                            // even on a wide tablet sheet.
                            val gridWidth = maxWidth.coerceAtMost(290.dp)
                            val circleDiameter = (
                                (gridWidth - 40.dp) / 5f - 4.dp
                            ).coerceIn(27.dp, 38.dp)
                            Row(
                                Modifier.width(gridWidth).height(56.dp)
                                    .align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${question.number}.",
                                    color = Color(0xFF25213A),
                                    fontSize = 15.sp,
                                    modifier = Modifier.width(32.dp),
                                    textAlign = TextAlign.End
                                )
                                Spacer(Modifier.width(8.dp))
                                listOf("A", "B", "C", "D", "E").forEach { option ->
                                    val checked = selectedAnswers[question.number] == option
                                    Box(
                                        Modifier.weight(1f).height(56.dp)
                                            .semantics {
                                                selected = checked
                                                contentDescription =
                                                    "${question.number}. soru $option seçeneği"
                                            }
                                            .clickable { onSelectOption(question.number, option) }
                                            .testTag("optic_q${question.number}_$option"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            Modifier.size(circleDiameter).clip(CircleShape)
                                                .background(
                                                    if (checked) Color(0xFF168BFA)
                                                    else Color.White
                                                )
                                                .border(
                                                    1.dp,
                                                    if (checked) Color(0xFF168BFA)
                                                    else Color(0xFFCDD2DB),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                option, fontSize = 13.sp,
                                                color = if (checked) Color.White
                                                    else Color(0xFF9AA3B2),
                                                fontWeight = if (checked) FontWeight.Bold
                                                    else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Surface(
                    onClick = onFinishTest,
                    shape = CircleShape,
                    color = Color(0xFF168BFA),
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                        .testTag("drawer_finish_test_btn")
                ) {
                    Row(
                        Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Flag, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            "Testi Bitir", color = Color.White,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 3-B. SAĞ KENAR `◔ İSTATİSTİKLER` ÇEKMECESİ (Screenshot 3 ile birebir aynı!)
 * - Sağdaki dikey mavi kulakçık `◔ İstatistikler` olur.
 * - Tıklanınca sağdan açılan beyaz çekmecede üstte kalın Test Adı, altında yan yana
 *   3 Büyük Renkli Daire:
 *   1) Mavi Daire (`#3B82F6`): Doğru sayısı + `Doğru`
 *   2) Kırmızı Daire (`#EF4444`): Yanlış sayısı + `Yanlış`
 *   3) Açık Gri Daire (`#CBD5E1`): Boş sayısı + `Boş`
 */
@Composable
private fun RightSlidingStatsDrawer345(
    isOpen: Boolean,
    testTitle: String,
    questions: List<YksQuestion>,
    selectedAnswers: Map<Int, String>,
    correctCount: Int,
    wrongCount: Int,
    emptyCount: Int,
    ungradedCount: Int,
    onToggleDrawer: () -> Unit,
    onOpenMistakeAnalysis: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config = LocalConfiguration.current
    val drawerWidth = (config.screenWidthDp *
        if (config.screenWidthDp >= 600) 0.48f else 0.80f).dp
        .coerceIn(280.dp, 350.dp)
    val maxHeight = (config.screenHeightDp - 116).coerceAtLeast(230).dp
    if (!isOpen) {
        Box(modifier = modifier) {
            VerticalBlueSideTab345(
                "İstatistikler", true, onToggleDrawer, "stats_drawer_tab",
                "İstatistikleri aç"
            )
        }
        return
    }
    val graded = questions.count { it.answerKeyVerified }
    val successRatio = if (graded == 0) 0f else correctCount.toFloat() / graded
    val lossNet = String.format(
        java.util.Locale.forLanguageTag("tr-TR"), "%.2f", wrongCount / 4f
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VerticalBlueSideTab345(
            "İstatistikler", true, onToggleDrawer, "stats_drawer_tab",
            "İstatistikleri kapat"
        )
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            shadowElevation = 13.dp,
            modifier = Modifier.width(drawerWidth)
                .heightIn(max = maxHeight)
                .then(
                    if (isOpen) Modifier.testTag("stats_drawer_panel")
                    else Modifier.clearAndSetSemantics { }
                )
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    testTitle, color = Color(0xFF25215D),
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                ThreeStatCirclesRow345(
                    correctCount = correctCount,
                    wrongCount = wrongCount,
                    emptyCount = emptyCount,
                    circleSizeDp = 68
                )
                if (ungradedCount > 0) {
                    Text(
                        "$ungradedCount soru doğrulanmadığı için puanlanmadı",
                        color = Color(0xFF967148), fontSize = 11.sp
                    )
                }
                HorizontalDivider(
                    thickness = 0.6.dp, color = Color(0xFFF0EFF4)
                )
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        "Konu Başarı Oranı", color = Color(0xFF25215D),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = { successRatio },
                        color = Color(0xFF3475F5),
                        trackColor = Color(0xFFD7D3E3),
                        modifier = Modifier.fillMaxWidth().height(9.dp).clip(CircleShape)
                    )
                    Text(
                        if (graded > 0) "%${(successRatio * 100).toInt()}" else "—",
                        color = Color(0xFF25215D), fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider(
                    thickness = 0.6.dp, color = Color(0xFFF0EFF4)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(15.dp)
                    ) {
                        Text(
                            "Kayıp Net", color = Color(0xFF25215D),
                            fontSize = 14.sp, fontWeight = FontWeight.Medium
                        )
                        Surface(
                            shape = CircleShape, color = Color(0xFFE65D48)
                        ) {
                            Text(
                                "-$lossNet", color = Color.White,
                                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(
                                    horizontal = 16.dp, vertical = 10.dp
                                )
                            )
                        }
                    }
                    Box(
                        Modifier.width(1.dp).height(64.dp)
                            .background(Color(0xFFEEEAF2))
                    )
                    Surface(
                        onClick = onOpenMistakeAnalysis,
                        color = Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                            .testTag("stats_open_error_frequency")
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(bottom = 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(13.dp)
                        ) {
                            Text(
                                "Hata Frekansı", color = Color(0xFF25215D),
                                fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = "Hata frekansını aç",
                                tint = Color(0xFF3475F5),
                                modifier = Modifier.size(25.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3 BÜYÜK RENKLİ DAİRE (Mavi `#3B82F6` Doğru | Kırmızı `#EF4444` Yanlış | Açık Gri `#CBD5E1` Boş)
 * (Hem Sağ `◔ İstatistikler` Çekmecesinde hem de Tam Ekran Karne Sayfasında kullanılır)
 */
@Composable
private fun ThreeStatCirclesRow345(
    correctCount: Int,
    wrongCount: Int,
    emptyCount: Int,
    circleSizeDp: Int = 86
) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val diameter = ((maxWidth - 24.dp) / 3).coerceAtMost(circleSizeDp.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                Triple(correctCount, "Doğru", Color(0xFF3475F5)),
                Triple(wrongCount, "Yanlış", Color(0xFFEF4363)),
                Triple(emptyCount, "Boş", Color(0xFFE5E2EF))
            ).forEach { (count, label, background) ->
                val ink = if (label == "Boş") Color(0xFF605B7B) else Color.White
                Surface(shape = CircleShape, color = background, modifier = Modifier.size(diameter)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("$count", color = ink, fontWeight = FontWeight.SemiBold, fontSize = (diameter.value * .26f).sp)
                        Text(label, color = ink, fontSize = (diameter.value * .16f).sp)
                    }
                }
            }
        }
    }
}

/**
 * SAĞ KENAR DİKEY MAVİ KULAKÇIK (`⠿ Optik Form` veya `◔ İstatistikler`)
 */
@Composable
private fun VerticalBlueSideTab345(
    title: String,
    isStatsIcon: Boolean,
    onClick: () -> Unit,
    testTag: String,
    accessibilityLabel: String = title
) {
    // Narrow side grips should not dominate the book page on tablets.
    // The full drawer remains accessible with the same single tap.
    val tabHeight = if (isStatsIcon) 114.dp else 104.dp
    val innerWidth = if (isStatsIcon) 104.dp else 94.dp
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp),
        color = if (isStatsIcon) Color(0xFF3475F5) else Color(0xFF168BFA),
        shadowElevation = 3.dp,
        modifier = Modifier
            .width(30.dp)
            .height(tabHeight)
            .testTag(testTag)
            .semantics { contentDescription = accessibilityLabel }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .requiredWidth(innerWidth)
                    .graphicsLayer { rotationZ = -90f }
            ) {
                Icon(
                    imageVector = if (isStatsIcon) Icons.Default.PieChart else Icons.Default.Apps,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 1. ALT KOYU LACİVERT (`#1E295B`) ÇİZİM VE ARAÇ BARI (345 ALL STAR BİREBİR):
 * - Solda Kitap/Tek Soru görünüm ikonu
 * - Ortada: Kalem (aktifken mavi yuvarlak içinde), Büyüteç, El (Kaydırma), Silgi,
 *   3 boy nokta (kalem kalınlığı), Renk seçici ve Çöp Kutusu (Temizle)
 * - En sağda: Gri hap içinde Kronometre (`00:12`)
 */
@Composable
private fun PremiumEraserGlyph() {
    // Clean diagonal eraser silhouette, not a broom or bordered button.
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val body = Path().apply {
            moveTo(w * 0.12f, h * 0.59f)
            lineTo(w * 0.48f, h * 0.15f)
            quadraticTo(w * 0.56f, h * 0.07f, w * 0.65f, h * 0.15f)
            lineTo(w * 0.91f, h * 0.38f)
            quadraticTo(w * 0.99f, h * 0.47f, w * 0.91f, h * 0.55f)
            lineTo(w * 0.58f, h * 0.91f)
            lineTo(w * 0.36f, h * 0.92f)
            close()
        }
        drawPath(body, Color.White)
        val seam = Path().apply {
            moveTo(w * 0.29f, h * 0.43f)
            lineTo(w * 0.65f, h * 0.75f)
        }
        drawPath(seam, Color(0xFF1E295B).copy(alpha = 0.65f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
        drawLine(
            Color.White.copy(alpha = 0.82f),
            Offset(w * 0.35f, h * 0.94f),
            Offset(w * 0.92f, h * 0.94f),
            strokeWidth = 1.3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

internal fun formatBoardTimerText(
    elapsedSeconds: Int,
    targetDurationSeconds: Int
): String {
    val displaySeconds = if (targetDurationSeconds > 0) {
        (targetDurationSeconds - elapsedSeconds.coerceAtLeast(0)).coerceAtLeast(0)
    } else {
        elapsedSeconds.coerceAtLeast(0)
    }
    return if (displaySeconds >= 3600) {
        val hours = (displaySeconds / 3600).toString().padStart(2, '0')
        val mins = ((displaySeconds % 3600) / 60).toString().padStart(2, '0')
        val secs = (displaySeconds % 60).toString().padStart(2, '0')
        "$hours:$mins:$secs"
    } else {
        val mins = (displaySeconds / 60).toString().padStart(2, '0')
        val secs = (displaySeconds % 60).toString().padStart(2, '0')
        "$mins:$secs"
    }
}

@Composable
private fun BottomDarkNavyDrawingBar345(
    viewMode: BoardViewMode,
    activeTool: DrawingTool,
    isZoomMagnifierEnabled: Boolean,
    fingerDrawingEnabled: Boolean = false,
    onChangeFingerDrawing: (Boolean) -> Unit = {},
    selectedStrokeSizeIndex: Int,
    penThickness: Float = 35f,
    selectedPenColorHex: Long,
    elapsedSeconds: Int,
    targetDurationSeconds: Int = 0,
    onToggleViewMode: () -> Unit,
    onSelectTool: (DrawingTool) -> Unit,
    onToggleZoom: () -> Unit,
    onSelectStrokeSize: (Int) -> Unit,
    onSelectPenThickness: (Float) -> Unit = {},
    onSelectPenColor: (Long) -> Unit,
    onClearCanvas: () -> Unit,
    onOpenPageLayoutSettings: (() -> Unit)? = null,
    isImmersive: Boolean = false,
    showImmersiveToggle: Boolean = false,
    onToggleImmersive: () -> Unit = {}
) {
    var showColorPalettePopup by remember { mutableStateOf(false) }
    var showPenSizePopover by remember { mutableStateOf(false) }
    val penColors = listOf(
        0xFF111111L to "Siyah",
        0xFF1D4ED8L to "Mavi",
        0xFFDC2626L to "Kırmızı",
        0xFF16A34AL to "Yeşil",
        0xFFD97706L to "Turuncu",
        0xFF9333EAL to "Mor"
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = Color(0xFF24205E),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // SOL: Kitap Sayfası / Tek Soru Görünüm Değiştirici İkonu
                IconButton(
                    onClick = onToggleViewMode,
                    modifier = Modifier.testTag("toggle_view_mode_btn")
                ) {
                    Icon(
                        imageVector = if (viewMode == BoardViewMode.FULL_PAGE) {
                            Icons.AutoMirrored.Filled.MenuBook
                        } else {
                            Icons.Default.CropPortrait
                        },
                        contentDescription = "Görünüm Değiştir",
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                }

                // ORTA: Çizim Araçları (Kalem [Tıklayınca Renk Değiştirir], Büyüteç, El, Silgi, 3 Kalınlık Noktası, Renk, Çöp Kutusu)
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Smart S Pen inks, fingers navigate. No duplicate finger-pencil.
                    val isSmartSelected = !fingerDrawingEnabled &&
                        activeTool == DrawingTool.PEN
                    Surface(
                        onClick = {
                            showColorPalettePopup = false
                            showPenSizePopover = false
                            onChangeFingerDrawing(false)
                            onSelectTool(DrawingTool.PEN)
                        },
                        shape = CircleShape,
                        color = if (isSmartSelected) Color(0xFF3475F5)
                            else Color.White.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp,
                            if (isSmartSelected) Color(0xFF93C5FD)
                            else Color.White.copy(alpha = 0.22f)),
                        modifier = Modifier.size(42.dp).testTag("smart_spen_mode")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Edit,
                                contentDescription = "Akıllı Mod: yalnızca S Pen ile yaz",
                                tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                    // Compact, icon-only eraser matching the other tools.
                    val isEraserActive = activeTool == DrawingTool.ERASER &&
                        !isZoomMagnifierEnabled
                    Surface(
                        onClick = {
                            showColorPalettePopup = false
                            showPenSizePopover = false
                            onSelectTool(DrawingTool.ERASER)
                        },
                        shape = CircleShape,
                        color = if (isEraserActive) Color(0xFF3475F5)
                            else Color.Transparent,
                        modifier = Modifier.size(44.dp).testTag("tool_eraser_btn")
                            .semantics { contentDescription = "Silgi" }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PremiumEraserGlyph()
                        }
                    }

                    // El (Sayfa Kaydırma / Pan)
                    val isPanActive = activeTool == DrawingTool.HAND_PAN && !isZoomMagnifierEnabled
                    Surface(
                        onClick = {
                            showColorPalettePopup = false
                            showPenSizePopover = false
                            onSelectTool(DrawingTool.HAND_PAN)
                        },
                        shape = CircleShape,
                        color = if (isPanActive) Color(0xFF3475F5) else Color.Transparent,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("tool_hand_pan_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PanTool,
                                contentDescription = "Sayfa Kaydır",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Büyüteç (Zoom)
                    Surface(
                        onClick = {
                            showColorPalettePopup = false
                            showPenSizePopover = false
                            onToggleZoom()
                        },
                        shape = CircleShape,
                        color = if (isZoomMagnifierEnabled) Color(0xFF3475F5) else Color.Transparent,
                        modifier = Modifier.size(44.dp).testTag("board_zoom_toggle")
                            .semantics { selected = isZoomMagnifierEnabled }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Yakınlaştır",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Akıllı Şekil (Smart Shape)
                    val isSmartShapeActive = activeTool == DrawingTool.SMART_SHAPE && !isZoomMagnifierEnabled
                    Surface(
                        onClick = {
                            showColorPalettePopup = false
                            showPenSizePopover = false
                            onSelectTool(DrawingTool.SMART_SHAPE)
                        },
                        shape = CircleShape,
                        color = if (isSmartShapeActive) Color(0xFF3475F5) else Color.Transparent,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("tool_smart_shape_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ChangeHistory,
                                contentDescription = "Akıllı Şekil",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Dikey İnce Ayraç
                    Box(
                        modifier = Modifier
                            .height(22.dp)
                            .width(1.dp)
                            .background(Color.White.copy(alpha = 0.22f))
                    )

                    // The same three buttons control real eraser radius in
                    // eraser mode, and pen thickness in smart S Pen mode.
                    val isSizingEraser = activeTool == DrawingTool.ERASER
                    if (!isSizingEraser) {
                        // Şık Kalem Boyutu Tuşu (0 - 100 Slider & Canlı Önizleme Popover'ını Açar)
                        val isHighTool = activeTool == DrawingTool.HIGHLIGHTER
                        val currentDisplayPx = penThicknessToPx(penThickness, isHighTool)
                        Surface(
                            onClick = {
                                showPenSizePopover = !showPenSizePopover
                                if (showPenSizePopover) showColorPalettePopup = false
                            },
                            shape = RoundedCornerShape(50),
                            color = if (showPenSizePopover) Color(0xFF2563EB) else Color.White.copy(alpha = 0.12f),
                            border = BorderStroke(
                                1.5.dp,
                                if (showPenSizePopover) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.28f)
                            ),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("tool_pen_size_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LineWeight,
                                    contentDescription = "Kalem Kalınlığı Ayarı",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0F172A))
                                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(((penThickness / 100f) * 9f + 3.5f).dp)
                                            .clip(CircleShape)
                                            .background(Color(selectedPenColorHex))
                                    )
                                }
                                Text(
                                    text = "${"%.1f".format(currentDisplayPx)} px",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    val dotSizes = if (isSizingEraser)
                        listOf(9.dp, 15.dp, 21.dp) else listOf(6.dp, 10.dp, 14.dp)
                    dotSizes.forEachIndexed { idx, dotDp ->
                        val isSelectedSize = selectedStrokeSizeIndex == idx
                        Box(
                            modifier = Modifier
                                .size(39.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onSelectStrokeSize(idx)
                                    if (!isSizingEraser) {
                                        onSelectPenThickness(strokeSizeIndexToThickness(idx))
                                    }
                                }
                                .semantics {
                                    contentDescription = if (isSizingEraser)
                                        "Silgi boyutu ${idx + 1}" else "Kalem kalınlığı ${idx + 1}"
                                    selected = isSelectedSize
                                }
                                .testTag("brush_size_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(dotDp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelectedSize) Color(0xFF38BDF8)
                                        else Color.White.copy(alpha = 0.64f)
                                    )
                            )
                        }
                    }

                    if (activeTool != DrawingTool.ERASER) {
                    // Renk Seçici Daire (Kalem butonuna basınca da aynı menü açılır)
                    Surface(
                        onClick = {
                            showColorPalettePopup = !showColorPalettePopup
                            if (showColorPalettePopup) showPenSizePopover = false
                        },
                        shape = CircleShape,
                        color = Color(selectedPenColorHex),
                        border = BorderStroke(2.dp, Color.White),
                        modifier = Modifier
                            .size(26.dp)
                            .testTag("tool_color_circle_btn")
                    ) {}

                    }
                    // Çöp Kutusu (Sayfa Çizimlerini Temizle)
                    IconButton(
                        onClick = onClearCanvas,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Çizimleri Temizle",
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    if (viewMode == BoardViewMode.FULL_PAGE && onOpenPageLayoutSettings != null) {
                        IconButton(
                            onClick = onOpenPageLayoutSettings,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("board_page_layout_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Sayfa Düzeni ve Soru Boyutu",
                                tint = Color(0xFFBAE6FD),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // SAĞ: Süreli Geri Sayım veya 00:00 Kronometre Rozeti
                val isCountdown = targetDurationSeconds > 0
                val remainingSeconds = if (isCountdown) {
                    (targetDurationSeconds - elapsedSeconds.coerceAtLeast(0)).coerceAtLeast(0)
                } else 0
                val timerText = formatBoardTimerText(
                    elapsedSeconds = elapsedSeconds,
                    targetDurationSeconds = targetDurationSeconds
                )
                val timerBgColor = when {
                    !isCountdown -> Color(0xFFE2E8F0)
                    remainingSeconds == 0 || remainingSeconds in 1..60 -> Color(0xFFFEE2E2)
                    remainingSeconds in 61..300 -> Color(0xFFFEF3C7)
                    else -> Color(0xFFE2E8F0)
                }
                val timerBorderColor = when {
                    !isCountdown -> Color.Transparent
                    remainingSeconds == 0 || remainingSeconds in 1..60 -> Color(0xFFFCA5A5)
                    remainingSeconds in 61..300 -> Color(0xFFFCD34D)
                    else -> Color.Transparent
                }
                val timerTextColor = when {
                    !isCountdown -> Color(0xFF1E293B)
                    remainingSeconds == 0 || remainingSeconds in 1..60 -> Color(0xFFB91C1C)
                    remainingSeconds in 61..300 -> Color(0xFF92400E)
                    else -> Color(0xFF1E293B)
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = timerBgColor,
                    border = if (timerBorderColor != Color.Transparent) {
                        BorderStroke(1.dp, timerBorderColor)
                    } else null,
                    modifier = Modifier.testTag("board_timer_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = timerTextColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = timerText,
                            color = timerTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                }
                if (showImmersiveToggle) {
                    IconButton(
                        onClick = onToggleImmersive,
                        modifier = Modifier.size(42.dp)
                            .testTag("immersive_fullscreen_toggle")
                    ) {
                        Icon(
                            imageVector = if (isImmersive) Icons.Default.FullscreenExit
                                else Icons.Default.Fullscreen,
                            contentDescription = if (isImmersive)
                                "Tam ekrandan çık" else "Tam ekran çalışma",
                            tint = Color.White,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }
            }
        }

        // Açılır Kalem Rengi Seçici (Üst Araç Barında Kalem Butonuna veya Renk Dairesine Basınca Hemen Altında Açılır)
        if (showColorPalettePopup) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF25215D),
                border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 54.dp)
                    .zIndex(70f)
                    .testTag("pen_color_palette_popup")
            ) {
                Row(
                    modifier = Modifier.widthIn(max = 560.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kalem Rengi:",
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp
                    )
                    penColors.forEach { (hex, colorName) ->
                        val isSel = selectedPenColorHex == hex
                        Surface(
                            onClick = {
                                onSelectPenColor(hex)
                                onSelectTool(DrawingTool.PEN)
                                showColorPalettePopup = false
                            },
                            shape = RoundedCornerShape(50),
                            color = if (isSel) Color.White.copy(alpha = 0.16f) else Color.Transparent,
                            border = BorderStroke(if (isSel) 1.5.dp else 0.dp, if (isSel) Color(0xFF38BDF8) else Color.Transparent),
                            modifier = Modifier.testTag("pen_color_option_$colorName")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (isSel) 22.dp else 18.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                        .border(1.5.dp, Color.White, CircleShape)
                                )
                                Text(
                                    text = colorName,
                                    color = if (isSel) Color(0xFF38BDF8) else Color.White,
                                    fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Açılır Kalem Kalınlığı Popover Kartı (0 - 100 Slider & Canlı Önizleme)
        if (showPenSizePopover && activeTool != DrawingTool.ERASER) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 54.dp)
                    .zIndex(75f)
                    .testTag("pen_size_popover")
            ) {
                PenSizePopoverCard(
                    thicknessValue = penThickness,
                    selectedPenColorHex = selectedPenColorHex,
                    isHighlighter = activeTool == DrawingTool.HIGHLIGHTER,
                    onThicknessChange = { newThickness: Float ->
                        onSelectPenThickness(newThickness)
                    },
                    onDismiss = { showPenSizePopover = false }
                )
            }
        }
    }
}

/**
 * =========================================================================
 * 4. TAM EKRAN YAN YANA "İKİZ SORU" EKRANI (Screenshot 5 ile birebir aynı!)
 * =========================================================================
 */
@Composable
private fun FullScreenTwinQuestionComparisonView(
    session: ActiveSolvingSession,
    originalQuestion: YksQuestion,
    twinData: TwinQuestionModalData?,
    isLoadingTwin: Boolean,
    studentAnswer: String?,
    activeTool: DrawingTool,
    selectedStrokeSizeIndex: Int,
    penThickness: Float = 35f,
    selectedPenColorHex: Long,
    onSelectTool: (DrawingTool) -> Unit,
    onSelectStrokeSize: (Int) -> Unit,
    onSelectPenThickness: (Float) -> Unit = {},
    onSelectPenColor: (Long) -> Unit,
    onBackToBookReview: () -> Unit,
    onOpenReportCard: () -> Unit,
    onRegenerateTwin: () -> Unit
) {
    var isTwinExpanded by remember(originalQuestion.id) { mutableStateOf(false) }
    BackHandler(enabled = isTwinExpanded) { isTwinExpanded = false }
    var twinSelectedOption by remember(originalQuestion.id) { mutableStateOf<String?>(null) }
    var isTwinAnswerRevealed by remember(originalQuestion.id) { mutableStateOf(false) }
    var originalTwinStrokes by remember(originalQuestion.id) { mutableStateOf<List<DrawStroke>>(emptyList()) }
    var twinStrokes by remember(originalQuestion.id) { mutableStateOf<List<DrawStroke>>(emptyList()) }
    LaunchedEffect(isLoadingTwin, twinData?.questionStem) {
        // Never keep the previous twin's marked answer or sketches when a
        // new variant is requested, even when the source question is unchanged.
        if (isLoadingTwin || twinData != null) {
            twinSelectedOption = null
            isTwinAnswerRevealed = false
            twinStrokes = emptyList()
        }
    }

    val originalBitmap = StudioImageAndPdfEngine.rememberGuaranteedQuestionBitmap(originalQuestion)
    val twinBitmap = twinData?.takeIf {
        it.errorMessage == null &&
            (it.croppedImagePath.isNotBlank() || it.imageBase64.isNotBlank())
    }?.let { verifiedTwin ->
        StudioImageAndPdfEngine.rememberGuaranteedQuestionBitmap(
            originalQuestion.copy(
                id = "twin_${verifiedTwin.sourceQuestionId}",
                croppedImagePath = verifiedTwin.croppedImagePath,
                imageBase64 = verifiedTwin.imageBase64,
                pdfFilePath = "",
                pageIndex = -1,
                normLeft = 0f, normTop = 0f, normRight = 1f, normBottom = 1f
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F4FA))
            .testTag("full_screen_twin_comparison")
    ) {
        if (!isTwinExpanded) {
        // 1. Üst Beyaz Bar: Solda Geri Oku, Ortada Test Başlığı, Sağda "▸ Başarı Durumuna Git"
        BookPageTopBar345(
            testTitle = session.testItem.fullTitle.ifBlank { "${session.topicName} - Test 1" },
            isReviewMode = true,
            onBack = onBackToBookReview,
            onFinishTest = {},
            onOpenReportCard = onOpenReportCard
        )

        // 2. Üst Koyu Lacivert Çizim Barı (Üst Beyaz Barın Hemen Altında)
        Box(modifier = Modifier.zIndex(40f)) {
            BottomDarkNavyDrawingBar345(
                viewMode = BoardViewMode.FULL_PAGE,
                activeTool = activeTool,
                isZoomMagnifierEnabled = false,
                selectedStrokeSizeIndex = selectedStrokeSizeIndex,
                penThickness = penThickness,
                selectedPenColorHex = selectedPenColorHex,
                elapsedSeconds = session.elapsedSeconds,
                targetDurationSeconds = session.targetDurationSeconds,
                onToggleViewMode = onBackToBookReview,
                onSelectTool = onSelectTool,
                onToggleZoom = {},
                onSelectStrokeSize = onSelectStrokeSize,
                onSelectPenThickness = onSelectPenThickness,
                onSelectPenColor = onSelectPenColor,
                onClearCanvas = {
                    originalTwinStrokes = emptyList()
                    twinStrokes = emptyList()
                }
            )
        }

        }

        // RTL places the twin card on the right while keeping each card's content LTR.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isTwinExpanded) 0.dp else 16.dp,
                        vertical = if (isTwinExpanded) 0.dp else 12.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(if (isTwinExpanded) 0.dp else 16.dp)
            ) {
            // İkiz kartı sağda, Test Sorusu kartı solda yer alır.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Surface(
                    shape = if (isTwinExpanded) RoundedCornerShape(0.dp)
                        else RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    color = Color(0xFFF59E0B)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                            .padding(start = 18.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isTwinExpanded) "İkiz Sorusu • Tam Ekran" else "İkiz Sorusu",
                            color = Color(0xFF422006),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        IconButton(
                            onClick = { isTwinExpanded = !isTwinExpanded },
                            modifier = Modifier.testTag("twin_fullscreen_toggle")
                        ) {
                            Icon(
                                imageVector = if (isTwinExpanded) Icons.Filled.Minimize
                                    else Icons.Filled.Maximize,
                                contentDescription = if (isTwinExpanded) "İkiz soruyu küçült"
                                    else "İkiz soruyu tam ekran büyüt",
                                tint = Color(0xFF422006),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                    color = Color.White,
                    border = BorderStroke(if (isTwinExpanded) 2.5.dp else 1.5.dp, Color(0xFFF59E0B)),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isLoadingTwin || twinData == null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFFEA580C))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "${originalQuestion.topicHeader} sorusunun birebir çözüm kalıbı analiz edilip yeni verilerle doğrulanıyor...",
                                    color = Color(0xFF475569),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else if (twinData.errorMessage != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = twinData.errorMessage,
                                    color = Color(0xFFB91C1C),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("twin_generation_error")
                                )
                                Spacer(Modifier.height(18.dp))
                                Surface(
                                    onClick = onRegenerateTwin,
                                    modifier = Modifier.testTag("retry_twin_generation_btn"),
                                    shape = RoundedCornerShape(50),
                                    color = Color(0xFF1D4ED8)
                                ) {
                                    Text(
                                        text = "Yeniden İkiz Oluştur",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    if (twinData.sourceDescription.isNotBlank()) {
                                        Text(
                                            text = twinData.sourceDescription,
                                            color = Color(0xFF64748B),
                                            fontSize = 12.sp,
                                            modifier = Modifier.testTag("twin_question_provenance")
                                        )
                                    }
                                    if (twinBitmap != null) {
                                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                                            val aspect = twinBitmap.width.toFloat() /
                                                twinBitmap.height.toFloat().coerceAtLeast(1f)
                                            Image(
                                                bitmap = twinBitmap.asImageBitmap(),
                                                contentDescription = "Doğrulanmış farklı ikiz soru",
                                                contentScale = ContentScale.Fit,
                                                alignment = Alignment.TopStart,
                                                modifier = Modifier.fillMaxWidth().aspectRatio(aspect)
                                                    .testTag("verified_twin_question_image")
                                            )
                                        }
                                    } else if (twinData.croppedImagePath.isNotBlank() ||
                                        twinData.imageBase64.isNotBlank()) {
                                        CircularProgressIndicator(
                                            color = Color(0xFFEA580C),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text("Kaynak soru görseli açılıyor...", fontSize = 12.sp)
                                    } else if (twinData.questionStem.isNotBlank()) {
                                        Text(
                                            text = twinData.questionStem,
                                            color = Color(0xFF1E293B),
                                            fontSize = 14.5.sp,
                                            lineHeight = 21.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    if (twinData.diagramJson.isNotBlank()) {
                                        TwinDiagramCanvas(
                                            diagramJson = twinData.diagramJson,
                                            modifier = Modifier.testTag("twin_generated_diagram")
                                        )
                                    }
                                    if (twinData.generatedIllustrationBase64.isNotBlank()) {
                                        TwinGeneratedIllustration(
                                            imageBase64 = twinData.generatedIllustrationBase64,
                                            modifier = Modifier.testTag("twin_generated_illustration")
                                        )
                                    }
                                    Text(
                                        text = "Cevabını işaretle; ardından kontrol et.",
                                        color = Color(0xFF64748B),
                                        fontSize = 12.sp
                                    )

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("A", "B", "C", "D", "E").forEach { optKey ->
                                            val optVal = twinData.options[optKey] ?: optKey
                                            val hasPrintedOptions = twinData.croppedImagePath.isNotBlank() ||
                                                twinData.imageBase64.isNotBlank()
                                            val answerLabel = if (hasPrintedOptions) optKey else "$optKey) $optVal"
                                            val isSelected = twinSelectedOption == optKey
                                            val isCorrectOpt = isTwinAnswerRevealed && optKey == twinData.correctOption
                                            val isWrongSelected = isTwinAnswerRevealed &&
                                                isSelected && !isCorrectOpt
                                            Surface(
                                                onClick = {
                                                    if (!isTwinAnswerRevealed) twinSelectedOption = optKey
                                                },
                                                modifier = Modifier.fillMaxWidth().testTag("twin_answer_$optKey"),
                                                shape = RoundedCornerShape(12.dp),
                                                color = when {
                                                    isCorrectOpt -> Color(0xFFDCFCE7)
                                                    isWrongSelected -> Color(0xFFFEE2E2)
                                                    isSelected -> Color(0xFFDBEAFE)
                                                    else -> Color.Transparent
                                                },
                                                border = if (isSelected || isCorrectOpt) {
                                                    BorderStroke(
                                                        1.dp,
                                                        when {
                                                            isCorrectOpt -> Color(0xFF16A34A)
                                                            isWrongSelected -> Color(0xFFDC2626)
                                                            else -> Color(0xFF3475F5)
                                                        }
                                                    )
                                                } else null
                                            ) {
                                                Text(
                                                    text = answerLabel,
                                                    color = Color(0xFF1E293B),
                                                    fontWeight = if (isSelected || isCorrectOpt) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    fontSize = 13.sp,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (isTwinAnswerRevealed) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFF0FDF4),
                                            border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = when {
                                                        twinSelectedOption == null -> "Cevap anahtarı"
                                                        twinSelectedOption == twinData.correctOption -> "Doğru! Kazanımı pekiştirdin."
                                                        else -> "Bu soruyu tekrar incele."
                                                    },
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF166534),
                                                    fontSize = 12.sp
                                                )
                                                Text(
                                                    text = "Doğru Cevap: ${twinData.correctOption}",
                                                    color = Color(0xFF15803D),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.5.sp
                                                )
                                                Text(
                                                    text = twinData.solutionExplanation,
                                                    color = Color(0xFF166534),
                                                    fontSize = 12.5.sp,
                                                    lineHeight = 18.sp
                                                )
                                                Surface(
                                                    onClick = onRegenerateTwin,
                                                    modifier = Modifier.testTag("generate_next_twin_btn"),
                                                    shape = RoundedCornerShape(50),
                                                    color = Color(0xFF1D4ED8)
                                                ) {
                                                    Text(
                                                        text = "Bir İkiz Daha",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(
                                                            horizontal = 16.dp, vertical = 9.dp
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                        .zIndex(30f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        onClick = { isTwinAnswerRevealed = !isTwinAnswerRevealed },
                                        modifier = Modifier.testTag("reveal_twin_answer_btn"),
                                        shape = RoundedCornerShape(50),
                                        color = Color(0xFF3475F5),
                                        shadowElevation = 3.dp
                                    ) {
                                        Text(
                                            text = if (isTwinAnswerRevealed) "Cevabı Gizle" else "Cevabı Göster",
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        TwinQuestionInkCanvas(
                            strokes = twinStrokes,
                            onStrokesChange = { twinStrokes = it },
                            activeTool = activeTool,
                            selectedStrokeSizeIndex = selectedStrokeSizeIndex,
                            penThickness = penThickness,
                            selectedPenColorHex = selectedPenColorHex
                        )
                    }
                }
            }
            }

            if (!isTwinExpanded) {
            // Test kartı, RTL düzeni nedeniyle sol tarafta.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    color = Color(0xFF1E1B4B)
                ) {
                    Text(
                        text = "Test Sorusu",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Color(0xFF475569)),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.TopStart
                            ) {
                                if (originalBitmap != null) {
                                    val aspect = originalBitmap.width.toFloat() /
                                        originalBitmap.height.toFloat().coerceAtLeast(1f)
                                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                        // In the comparison screen the source
                                        // crop previously stopped at 300dp even
                                        // on wide tablets: it looked miniature.
                                        // Fit the complete source to the card,
                                        // then allow vertical scrolling naturally.
                                        Image(
                                            bitmap = originalBitmap.asImageBitmap(),
                                            contentDescription = "Orijinal Test Sorusu",
                                            contentScale = ContentScale.Fit,
                                            alignment = Alignment.TopStart,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(aspect)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = originalQuestion.stemTop.ifBlank { "${originalQuestion.number}. Soru" },
                                        color = Color(0xFF1E293B),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Doğru Cevap:",
                                        color = Color(0xFF334155),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = if (originalQuestion.answerKeyVerified) Color(0xFF15803D) else Color(0xFFB45309),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (originalQuestion.answerKeyVerified) originalQuestion.correctOption else "?",
                                                color = Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Senin Cevabın:",
                                        color = Color(0xFF334155),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp
                                    )
                                    val hasStudentAns = !studentAnswer.isNullOrBlank()
                                    val isStudentRight = originalQuestion.answerKeyVerified && studentAnswer == originalQuestion.correctOption
                                    Surface(
                                        shape = CircleShape,
                                        color = when {
                                            !hasStudentAns -> Color.White
                                            isStudentRight -> Color(0xFF15803D)
                                            !originalQuestion.answerKeyVerified -> Color(0xFFB45309)
                                            else -> Color(0xFFDC2626)
                                        },
                                        border = BorderStroke(
                                            1.2.dp,
                                            if (!hasStudentAns) Color(0xFFCBD5E1) else Color.Transparent
                                        ),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = studentAnswer?.ifBlank { "-" } ?: "-",
                                                color = if (!hasStudentAns) Color(0xFF64748B) else Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                                }
                        TwinQuestionInkCanvas(
                            strokes = originalTwinStrokes,
                            onStrokesChange = { originalTwinStrokes = it },
                            activeTool = activeTool,
                            selectedStrokeSizeIndex = selectedStrokeSizeIndex,
                            penThickness = penThickness,
                            selectedPenColorHex = selectedPenColorHex
                        )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

/**
 * =========================================================================
 * 5. TAM EKRAN "▸ BAŞARI DURUMUNA GİT" KARNE & ANALİZ SAYFASI
 * (Screenshots 6 & 7 ile birebir aynı!)
 * =========================================================================
 * - Üst Bar: Solda geri oku, ortada Test Başlığı, sağ üstte mavi hap `▸ Kitapta Göster` butonu.
 * - Orta Kutlama Alanı:
 *   * Konfeti patlatan kutlama illüstrasyonu (`🎉`), altında kalın başlık (`Çok yaklaştın!` / `Harika iş çıkardın!`),
 *     altında `"Bu testi tamamladın, şimdi birlikte sonuçlara yakından bakalım."` açıklaması.
 *   * Yan yana 3 Büyük Daire (`Doğru` Mavi `#3B82F6`, `Yanlış` Kırmızı `#EF4444`, `Boş` Gri `#CBD5E1`).
 * - İkiz Test Öneri Kutusu:
 *   * Açık gri (`#F1F5F9`) yuvarlatılmış kutu: Solda `"Bu testin bir ikizi var! Pekiştirmen için çözmeni öneriyoruz."`,
 *     sağda mavi hap buton `İkiz Testi Çöz`.
 * - Alt Bölüm — `Çözüm Videoları` ve 4 Sütunlu Soru Tablosu:
 *   * Yeşil hap etiket (`#84CC16` / `#65A30D`) içinde ders adı (Örn: `AYT Fizik`, `AYT Matematik`).
 *   * Sütun Başlıkları: `Soru No` | `Doğru Cevap` | `Öğrenci Cevabı` | `Zorluk Seviyesi`
 *   * Her soru satırı (`1. Soru`, `2. Soru`...): Doğru cevap harfi, Öğrenci cevabı (`-` veya harf),
 *     sağda 5 yıldız üzerinden mavi yıldızlı zorluk gösterimi (`★★☆☆☆`) ve aşağı açılır ok (`⌄`).
 */
/** Do not label a test as failed when none of its source answers were verified. */
internal fun scoreReportHeadline(correctCount: Int, gradedQuestionCount: Int): String = when {
    gradedQuestionCount == 0 -> "Cevap anahtarı doğrulanmadı"
    correctCount.toFloat() / gradedQuestionCount >= 0.7f -> "Harika iş çıkardın!"
    else -> "Pes etmek yok!"
}

@Composable
private fun FullScreenSuccessReportCardView(
    session: ActiveSolvingSession,
    correctCount: Int,
    wrongCount: Int,
    emptyCount: Int,
    ungradedCount: Int,
    showTwinSuggestion: Boolean,
    onBackToBookPage: () -> Unit,
    onSolveTwinTestForWrongQuestions: () -> Unit,
    onStrengthenFromFrequency: (com.example.util.FrequencyPracticeTarget) -> Unit,
    onWatchQuestionSolution: (YksQuestion) -> Unit,
    onOpenTwinForQuestion: (YksQuestion) -> Unit
) {
    val questions = session.testItem.questions
    var expandedQuestionNumber by remember { mutableStateOf<Int?>(null) }
    var showMistakeAnalysis by remember(session.testItem.id) { mutableStateOf(false) }
    val mistakeSummary = remember(questions, session.selectedAnswers, session.subjectName, session.topicName) {
        TestAnswerAnalyticsEngine.calculate(
            questions = questions,
            selectedAnswers = session.selectedAnswers,
            lessonHint = session.subjectName,
            topicHint = session.topicName
        )
    }
    if (showMistakeAnalysis) {
        PremiumMistakeAnalysisScreen(
            title = session.testItem.fullTitle.ifBlank { session.topicName },
            summary = mistakeSummary,
            onBack = { showMistakeAnalysis = false },
            topicName = session.topicName,
            onStrengthen = onStrengthenFromFrequency
        )
        return
    }

    val gradedQuestionCount = questions.count { it.answerKeyVerified }
    val headlineText = remember(correctCount, gradedQuestionCount) {
        scoreReportHeadline(correctCount, gradedQuestionCount)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("full_screen_report_card")
    ) {
        // ÜST BAR: Solda Geri Oku, Ortada Test Başlığı, Sağda "▸ Kitapta Göster"
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackToBookPage) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = Color(0xFF25215D)
                    )
                }

                Text(
                    text = session.testItem.fullTitle.ifBlank { "${session.topicName} - Test 1" },
                    color = Color(0xFF25215D),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )

                Surface(
                    onClick = onBackToBookPage,
                    shape = RoundedCornerShape(50),
                    color = Color(0xFF3475F5),
                    shadowElevation = 2.dp,
                    modifier = Modifier.testTag("show_in_book_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Kitapta Göster",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // KAYDIRILABİLİR KARNE GÖVDESİ
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 900.dp)
                .fillMaxSize()
                .align(Alignment.CenterHorizontally)
                .background(Color.White),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // 1. ORTA KUTLAMA ALANI (Konfeti + Başlık + 3 Büyük Daire)
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Konfeti İllüstrasyonu
                    Text(
                        text = if (gradedQuestionCount == 0) "📋" else "🎉",
                        fontSize = 54.sp
                    )
                    Text(
                        text = headlineText,
                        color = Color(0xFF25215D),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 21.sp
                    )
                    Text(
                        text = if (gradedQuestionCount == 0) {
                            "Testi tamamladın ancak kaynak cevapları doğrulanmadığı için " +
                                "doğru, yanlış ve boş sayıları hesaplanamadı."
                        } else {
                            "Bu testi tamamladın, şimdi birlikte sonuçlara yakından bakalım."
                        },
                        color = Color(0xFF475569),
                        fontSize = 13.5.sp,
                        textAlign = TextAlign.Center
                    )

                    // Testin Genel 5 Kademeli Zorluk Yıldızı Gösterimi (`1★ Kolay` .. `5★ Çok Zor`)
                    val testDiff = session.testItem.difficulty
                    val testStarsFilled = when (testDiff) {
                        DifficultyLevel.KOLAY -> 1
                        DifficultyLevel.ORTA -> 2
                        DifficultyLevel.OSYM -> 3
                        DifficultyLevel.ZOR -> 4
                        DifficultyLevel.COK_ZOR -> 5
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                for (s in 1..5) {
                                    Icon(
                                        imageVector = if (s <= testStarsFilled) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = null,
                                        tint = Color(0xFF3475F5),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Tahmini zorluk: ${testDiff.label} ($testStarsFilled/5 ★)",
                                color = Color(0xFF1E3A8A),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (gradedQuestionCount > 0) {
                        ThreeStatCirclesRow345(
                            correctCount = correctCount,
                            wrongCount = wrongCount,
                            emptyCount = emptyCount,
                            circleSizeDp = 72
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFFFF7ED),
                            border = BorderStroke(1.dp, Color(0xFFFED7AA))
                        ) {
                            Text(
                                text = "Bu testte puanlanabilen soru bulunamadı. " +
                                    "Öğrenci cevapların korunuyor; kaynak cevap anahtarının " +
                                    "eşleşmesi düzeltilince sonuçlar değerlendirilebilir.",
                                modifier = Modifier.padding(16.dp),
                                color = Color(0xFF9A3412),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    if (ungradedCount > 0) {
                        Text(
                            text = "$ungradedCount soru cevap anahtarı doğrulanmadığı için puanlanmadı",
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Small, readable success and net-loss panel instead of an
            // oversized dashboard. Never infer YKS points from raw net.
            if (gradedQuestionCount > 0) item {
                val successRatio = correctCount.toFloat() / gradedQuestionCount
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE4EAF4)),
                    modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp)
                        .testTag("result_topic_success_card")
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Konu Başarı Oranı", color = Color(0xFF25215D),
                                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f))
                            Text("%${(successRatio * 100).toInt()}", color = Color(0xFF3475F5),
                                fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        LinearProgressIndicator(
                            progress = { successRatio },
                            color = Color(0xFF3475F5),
                            trackColor = Color(0xFFE8ECF4),
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Net: ${String.format(java.util.Locale.forLanguageTag("tr-TR"), "%.2f", correctCount - wrongCount / 4f)}",
                                color = Color(0xFF475569), fontSize = 12.sp
                            )
                            Text(
                                "Yanlışların net etkisi: -${String.format(java.util.Locale.forLanguageTag("tr-TR"), "%.2f", wrongCount / 4f)}",
                                color = Color(0xFFB45309), fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (session.testItem.level == TopicTestLevel.SEVIYE_TESPIT) item {
                val placement = remember(questions, session.selectedAnswers) {
                    LevelPlacementAdvisor.evaluate(questions, session.selectedAnswers)
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF0F4FF),
                    border = BorderStroke(1.5.dp, Color(0xFF93B4FF)),
                    modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp)
                        .testTag("diagnostic_ideal_level_card")
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text("İdeal Seviyeniz", fontSize = 15.sp,
                            fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                        if (placement != null) {
                            Text(
                                text = placement.recommended.displayTitle,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(placement.recommended.wedgeColorHex),
                                modifier = Modifier.testTag("diagnostic_recommended_level")
                            )
                            Text(
                                placement.note + " Bu öneri beş zorluk bandındaki performansının ön değerlendirmesidir.",
                                fontSize = 12.5.sp, color = Color(0xFF435577),
                                lineHeight = 19.sp
                            )
                        } else {
                            Text(
                                "Sağlıklı seviye önerisi için beş zorluk düzeyinden de doğrulanmış sorular gerekir.",
                                fontSize = 12.5.sp, color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            // Premium, source-aware analysis entry, visible only after finishing the test.
            item {
                Surface(
                    onClick = { showMistakeAnalysis = true },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF0F4FF),
                    border = BorderStroke(1.dp, Color(0xFFDCE6FF)),
                    modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp)
                        .testTag("open_frequency_analysis")
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = Color.White) {
                            Icon(Icons.Default.Visibility, contentDescription = null,
                                tint = Color(0xFF3475F5),
                                modifier = Modifier.padding(12.dp).size(24.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Hata Frekansı", fontSize = 17.sp,
                                fontWeight = FontWeight.Bold, color = Color(0xFF25215D))
                            Text("Ders, konu, alt kazanım ve soru türlerini incele",
                                fontSize = 12.sp, color = Color(0xFF607398))
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Hata frekansı ayrıntılarını aç",
                            tint = Color(0xFF3475F5))
                    }
                }
            }

            // 2. İKİZ TEST ÖNERİ KUTUSU (`#F1F5F9` yuvarlatılmış kutu + `İkiz Testi Çöz` mavi hap buton)
            if (showTwinSuggestion) item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Bu testte yanlış cevapladığın soruları yeniden çöz.\nİkiz Test tam olarak o sorulardan oluşur; yeni soru eklemez.",
                            color = Color(0xFF1E293B),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Surface(
                            onClick = onSolveTwinTestForWrongQuestions,
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF3475F5),
                            shadowElevation = 2.dp
                        ) {
                            Text(
                                text = "İkiz Testi Çöz",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            // 3. ALT BÖLÜM: "Çözüm Videoları" Başlığı + Yeşil Ders Etiketi + 4 Sütunlu Tablo Başlığı
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Soru Analizi ve Çözümler",
                        color = Color(0xFF25215D),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )

                    // Yeşil Hap Etiket (Örn: "AYT Fizik", "AYT Matematik")
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFF28B45)
                    ) {
                        Text(
                            text = "${session.examType.name} ${session.subjectName}",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp)
                        )
                    }

                    // 4 Sütunlu Tablo Başlık Satırı
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Soru No",
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            modifier = Modifier.weight(1.1f)
                        )
                        Text(
                            text = "Cevap",
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1.1f)
                        )
                        Text(
                            text = "Yanıtın",
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1.1f)
                        )
                        Text(
                            text = "Tahmini zorluk",
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.5f)
                        )
                        Spacer(modifier = Modifier.width(34.dp))
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))
                }
            }

            // 4. SORU BAZLI TABLO SATIRLARI (1. Soru, 2. Soru... + 5 Yıldızlı Mavi Zorluk + Açılır Ok `⌄`)
            items(questions, key = { it.number }) { q ->
                val studentOpt = session.selectedAnswers[q.number]?.ifBlank { "-" } ?: "-"
                val isExpanded = expandedQuestionNumber == q.number
                val starsFilled = when (q.difficulty) {
                    DifficultyLevel.KOLAY -> 1
                    DifficultyLevel.ORTA -> 2
                    DifficultyLevel.OSYM -> 3
                    DifficultyLevel.ZOR -> 4
                    DifficultyLevel.COK_ZOR -> 5
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedQuestionNumber = if (isExpanded) null else q.number
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${q.number}. Soru",
                            color = Color(0xFF25215D),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1.1f)
                        )
                        Text(
                            text = q.correctOption.takeIf {
                                q.answerKeyVerified && it in listOf("A", "B", "C", "D", "E")
                            } ?: "—",
                            color = if (q.answerKeyVerified) Color(0xFF25215D) else Color(0xFF64748B),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1.1f)
                        )
                        Text(
                            text = studentOpt,
                            color = when {
                                studentOpt == "-" -> Color(0xFF64748B)
                                !q.answerKeyVerified -> Color(0xFFB45309)
                                studentOpt == q.correctOption -> Color(0xFF16A34A)
                                else -> Color(0xFFDC2626)
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1.1f)
                        )

                        // 5 Yıldız Üzerinden Mavi Yıldızlı Zorluk Gösterimi + 5 Kademeli Zorluk Etiketi (`1★ Kolay` .. `5★ Çok Zor`)
                        Row(
                            modifier = Modifier.weight(1.5f),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (s in 1..5) {
                                Icon(
                                    imageVector = if (s <= starsFilled) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = null,
                                    tint = Color(0xFF3475F5),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                expandedQuestionNumber = if (isExpanded) null else q.number
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Detay",
                                tint = Color(0xFF334155)
                            )
                        }
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFF5F4FA),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = if (q.answerKeyVerified) "${q.topicHeader} • Doğru Cevap: ${q.correctOption}"
                                        else "${q.topicHeader} • Cevap anahtarı doğrulanmadı",
                                        color = Color(0xFF334155),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = buildString {
                                            append("Kaynak: ")
                                            append(q.publisherName.ifBlank { "Belirtilmedi" })
                                            if (q.sourceBookTitle.isNotBlank()) {
                                                append(" • "); append(q.sourceBookTitle)
                                            }
                                            if (q.sourcePrintedQuestionNumber > 0) {
                                                append(" • Kitaptaki ${q.sourcePrintedQuestionNumber}. soru")
                                            }
                                        },
                                        color = Color(0xFF64748B),
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Surface(
                                        onClick = { onOpenTwinForQuestion(q) },
                                        shape = RoundedCornerShape(50),
                                        color = Color(0xFFE2E8F0)
                                    ) {
                                        Text(
                                            text = "İkiz Soru",
                                            color = Color(0xFF3475F5),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                                        )
                                    }
                                    Surface(
                                        onClick = { onWatchQuestionSolution(q) },
                                        shape = RoundedCornerShape(50),
                                        color = Color(0xFF3475F5)
                                    ) {
                                        Text(
                                            text = "Çözüm İzle",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))
                }
            }
        }
    }
}

/**
 * Natural S Pen pressure. Each segment retains the hardware sample pressure;
 * old cached strokes without samples continue to draw with the legacy path.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPressureInkSegments(
    points: List<Offset>,
    pressureSamples: List<Float>,
    color: Color,
    baseWidth: Float
) {
    if (points.size < 2 || points.size != pressureSamples.size) return
    for (index in 1 until points.size) {
        val factor = (
            StylusInputPolicy.pressureWidthFactor(pressureSamples[index - 1]) +
                StylusInputPolicy.pressureWidthFactor(pressureSamples[index])
            ) / 2f
        drawLine(
            color = color,
            start = points[index - 1],
            end = points[index],
            strokeWidth = baseWidth * factor,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Vektörel çizim motoru: Catmull-Rom Cubic Bézier doğal mürekkep motoruna delege eder.
 * (Kalem, S-Pen basınç hassasiyeti, Akıllı Şekil, Fosforlu Kalem ve `BlendMode.Clear` Silgi)
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSmoothStroke(stroke: DrawStroke) {
    drawNaturalInkStroke(stroke)
}

@Composable
private fun TwinQuestionInkCanvas(
    strokes: List<DrawStroke>,
    onStrokesChange: (List<DrawStroke>) -> Unit,
    activeTool: DrawingTool,
    selectedStrokeSizeIndex: Int,
    penThickness: Float = 35f,
    selectedPenColorHex: Long
) {
    val livePath = remember { Path() }
    val livePoints = remember { mutableListOf<Offset>() }
    val drawTick = remember { mutableIntStateOf(0) }
    val latestStrokes by rememberUpdatedState(strokes)
    val strokeWidth = penThicknessToPx(penThickness)
    val highlighterWidth = penThicknessToPx(penThickness, isHighlighter = true)
    val eraserWidth = listOf(18f, 38f, 68f).getOrElse(selectedStrokeSizeIndex) { 38f }
    val canDraw = activeTool == DrawingTool.PEN ||
        activeTool == DrawingTool.HIGHLIGHTER ||
        activeTool == DrawingTool.SMART_SHAPE ||
        activeTool == DrawingTool.ERASER

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .then(
                if (canDraw) {
                    Modifier.pointerInput(activeTool, penThickness, selectedStrokeSizeIndex, selectedPenColorHex) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val isStylus = down.type == PointerType.Stylus
                            if (!isStylus) return@awaitEachGesture
                            down.consume()

                            livePoints.clear()
                            livePoints.add(down.position)
                            livePath.reset()
                            livePath.moveTo(down.position.x, down.position.y)
                            livePath.lineTo(down.position.x + 0.1f, down.position.y + 0.1f)
                            drawTick.intValue++

                            var previousPoint = down.position
                            drag(down.id) { change ->
                                change.consume()
                                val currentPoint = change.position
                                livePoints.add(currentPoint)
                                val midpointX = (previousPoint.x + currentPoint.x) * 0.5f
                                val midpointY = (previousPoint.y + currentPoint.y) * 0.5f
                                livePath.quadraticTo(previousPoint.x, previousPoint.y, midpointX, midpointY)
                                previousPoint = currentPoint
                                drawTick.intValue++
                            }

                            if (livePoints.isNotEmpty()) {
                                val isEraser = activeTool == DrawingTool.ERASER
                                val isHighlighter = activeTool == DrawingTool.HIGHLIGHTER
                                onStrokesChange(
                                    latestStrokes + DrawStroke(
                                        points = livePoints.toList(),
                                        colorHex = if (isEraser) 0x00000000L else selectedPenColorHex,
                                        strokeWidthPx = when {
                                            isEraser -> eraserWidth
                                            isHighlighter -> highlighterWidth
                                            else -> strokeWidth
                                        },
                                        isSmartShape = activeTool == DrawingTool.SMART_SHAPE,
                                        isEraser = isEraser,
                                        isHighlighter = isHighlighter
                                    )
                                )
                            }

                            livePoints.clear()
                            livePath.reset()
                            drawTick.intValue++
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        val tick = drawTick.intValue
        strokes.forEach { drawSmoothStroke(it) }
        if (tick >= 0 && livePoints.isNotEmpty()) {
            val isEraser = activeTool == DrawingTool.ERASER
            val isHighlighter = activeTool == DrawingTool.HIGHLIGHTER
            val liveWidth = when {
                isEraser -> eraserWidth
                isHighlighter -> highlighterWidth
                else -> strokeWidth
            }
            val liveColor = when {
                isEraser -> Color.Transparent
                isHighlighter -> Color(selectedPenColorHex).copy(alpha = 0.36f)
                else -> Color(selectedPenColorHex)
            }
            val liveBlend = if (isEraser) BlendMode.Clear else BlendMode.SrcOver
            if (livePoints.size == 1) {
                drawCircle(
                    color = liveColor,
                    radius = liveWidth / 2f,
                    center = livePoints.first(),
                    blendMode = liveBlend
                )
            } else {
                val smoothPath = buildSmoothInkPath(livePoints, activeTool == DrawingTool.SMART_SHAPE)
                drawPath(
                    path = smoothPath,
                    color = liveColor,
                    style = Stroke(
                        width = liveWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    ),
                    blendMode = liveBlend
                )
            }
        }
    }
}

/**
 * Vektörel soru şekli çizici (Görselsiz katalog soruları için)
 */
@Composable
fun QuestionVectorDiagram(
    visualType: QuestionVisualType,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val primaryStroke = Color(0xFF1E3A8A)

        when (visualType) {
            QuestionVisualType.FUNCTION_GRAPH -> {
                drawLine(
                    color = Color(0xFF64748B),
                    start = Offset(w * 0.12f, cy + h * 0.2f),
                    end = Offset(w * 0.88f, cy + h * 0.2f),
                    strokeWidth = 2.5f
                )
                drawLine(
                    color = Color(0xFF64748B),
                    start = Offset(cx, h * 0.1f),
                    end = Offset(cx, h * 0.9f),
                    strokeWidth = 2.5f
                )
                val curve = Path().apply {
                    moveTo(w * 0.22f, h * 0.18f)
                    quadraticTo(cx, h * 0.88f, w * 0.78f, h * 0.18f)
                }
                drawPath(curve, color = primaryStroke, style = Stroke(width = 3.5f))
            }

            else -> {
                val tri = Path().apply {
                    moveTo(cx, h * 0.16f)
                    lineTo(w * 0.22f, h * 0.82f)
                    lineTo(w * 0.78f, h * 0.82f)
                    close()
                }
                drawPath(tri, color = primaryStroke, style = Stroke(width = 3.5f))
            }
        }
    }
}
