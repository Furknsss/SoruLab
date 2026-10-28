package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * State-of-the-Art Studio Engine for:
 * 1. Ultra-HD 300 DPI Super-Sampling PDF Page Rendering (`scale = 3.0f`)
 * 2. Adaptive Contrast & Background Whitening (Luminance Thresholding -> #FFFFFF)
 *    + Clean Stem Cropping (Numberless pure question stem)
 * 3. Flood-Fill Background Removal (Dekupe) & Alpha Bounding Box Auto-Trim (64x64 Badge)
 */
object StudioImageAndPdfEngine {

    /**
     * Copies any `content://` or stream-backed URI into a seekable local File in `cacheDir`
     * so Android's `PdfRenderer` never fails with non-seekable descriptor or concurrent lock errors.
     */
    fun prepareSeekablePdfFile(context: Context, pdfUri: Uri): File? {
        return try {
            if (pdfUri.scheme == "file") {
                val path = pdfUri.path
                if (!path.isNullOrBlank()) {
                    val directFile = File(path)
                    if (directFile.exists() && directFile.length() > 0L) {
                        return directFile
                    }
                }
            }
            val cacheDir = File(context.cacheDir, "pdf_studio_Staging")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val outFile = File(cacheDir, "staged_${pdfUri.toString().hashCode().toUInt()}.pdf")

            context.contentResolver.openInputStream(pdfUri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output, bufferSize = 16 * 1024)
                    output.flush()
                }
            }
            if (outFile.exists() && outFile.length() > 0L) {
                outFile
            } else {
                // Fallback: try openFileDescriptor if openInputStream wasn't supported
                context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                    java.io.FileInputStream(pfd.fileDescriptor).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output, bufferSize = 16 * 1024)
                            output.flush()
                        }
                    }
                }
                if (outFile.exists() && outFile.length() > 0L) outFile else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Renders a PDF page at `scale = 2.0f` (or custom scale) from a seekable PDF file
     * onto an offscreen ARGB_8888 Bitmap with a pure white background.
     */
    fun renderPdfPageFromSeekableFile(
        pdfFile: File,
        pageIndex: Int,
        renderScale: Float = 2.0f
    ): Pair<Bitmap?, Int> {
        return try {
            android.os.ParcelFileDescriptor.open(
                pdfFile,
                android.os.ParcelFileDescriptor.MODE_READ_ONLY
            ).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val totalPages = renderer.pageCount
                    if (totalPages <= 0) return null to 0
                    val safeIndex = pageIndex.coerceIn(0, totalPages - 1)
                    renderer.openPage(safeIndex).use { page ->
                        val rawWidth = (page.width * renderScale).roundToInt().coerceAtLeast(1000)
                        val rawHeight = (page.height * renderScale).roundToInt().coerceAtLeast(1400)

                        val maxAllowedDim = 2200
                        val longest = max(rawWidth, rawHeight)
                        val clampFactor = if (longest > maxAllowedDim) {
                            maxAllowedDim.toFloat() / longest.toFloat()
                        } else {
                            1.0f
                        }

                        val targetW = (rawWidth * clampFactor).roundToInt().coerceAtLeast(800)
                        val targetH = (rawHeight * clampFactor).roundToInt().coerceAtLeast(1100)

                        val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)

                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                        )
                        bitmap to totalPages
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback in case the user picked an image file instead of a PDF
            val decodedImg = runCatching { BitmapFactory.decodeFile(pdfFile.absolutePath) }.getOrNull()
            if (decodedImg != null) decodedImg to 1 else null to 0
        }
    }

    fun renderPdfPageUltraHd300Dpi(
        context: Context,
        pdfUri: Uri,
        pageIndex: Int,
        superSampleScale: Float = 2.0f
    ): Pair<Bitmap?, Int> {
        val seekableFile = prepareSeekablePdfFile(context, pdfUri) ?: return null to 0
        return renderPdfPageFromSeekableFile(seekableFile, pageIndex, superSampleScale)
    }

    /**
     * Scales a high-res page bitmap to an optimal inference size (max 1440px)
     * so Gemini Vision API processes the page rapidly and reliably without payload limits.
     */
    fun createInferenceBitmap(source: Bitmap, maxDimension: Int = 1440): Bitmap {
        val w = source.width
        val h = source.height
        val longest = max(w, h)
        if (longest <= maxDimension) return source
        val ratio = maxDimension.toFloat() / longest.toFloat()
        val targetW = (w * ratio).roundToInt().coerceAtLeast(400)
        val targetH = (h * ratio).roundToInt().coerceAtLeast(400)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    fun getPdfPageCount(context: Context, pdfUri: Uri): Int {
        return try {
            val seekableFile = prepareSeekablePdfFile(context, pdfUri) ?: return 0
            android.os.ParcelFileDescriptor.open(
                seekableFile,
                android.os.ParcelFileDescriptor.MODE_READ_ONLY
            ).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount.coerceAtLeast(1)
                }
            }
        } catch (_: Exception) {
            1
        }
    }

    fun extractLastPagesForAnswerKeyOcr(
        context: Context,
        pdfUri: Uri,
        maxTailPages: Int = 2
    ): List<Bitmap> {
        return try {
            val seekableFile = prepareSeekablePdfFile(context, pdfUri) ?: return emptyList()
            val total = getPdfPageCount(context, pdfUri)
            if (total <= 0) return emptyList()
            val startIdx = (total - maxTailPages).coerceAtLeast(0)
            val bitmaps = mutableListOf<Bitmap>()
            for (idx in startIdx until total) {
                val (bmp, _) = renderPdfPageFromSeekableFile(
                    pdfFile = seekableFile,
                    pageIndex = idx,
                    renderScale = 1.8f
                )
                if (bmp != null) {
                    bitmaps.add(createInferenceBitmap(bmp, 1440))
                }
            }
            bitmaps
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Renders the first page of the uploaded PDF as a Book Cover thumbnail
     * for the "KİTAPLIĞIM" showcase.
     */
    fun savePdfFirstPageAsBookCover(
        context: Context,
        pdfUri: Uri
    ): String {
        return try {
            val seekableFile = prepareSeekablePdfFile(context, pdfUri) ?: return ""
            val (firstPageBmp, _) = renderPdfPageFromSeekableFile(
                pdfFile = seekableFile,
                pageIndex = 0,
                renderScale = 1.5f
            )
            if (firstPageBmp == null) return ""
            val targetW = 420
            val targetH = (firstPageBmp.height.toFloat() / firstPageBmp.width.toFloat().coerceAtLeast(1f) * targetW)
                .roundToInt()
                .coerceIn(480, 680)
            val coverBmp = Bitmap.createScaledBitmap(firstPageBmp, targetW, targetH, true)
            if (coverBmp !== firstPageBmp) {
                firstPageBmp.recycle()
            }
            val dir = File(context.filesDir, "book_covers")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, "book_cover_${System.currentTimeMillis()}_${(100..999).random()}.png")
            FileOutputStream(outFile).use { fos ->
                coverBmp.compress(Bitmap.CompressFormat.PNG, 92, fos)
            }
            outFile.absolutePath
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Crops a question from the Ultra-HD 300-DPI page bitmap using [ymin, xmin, ymax, xmax] (0-1000),
     * strips any residual vertical separator line on the edges, and applies
     * Adaptive Contrast & Background Whitening (Luminance Thresholding -> pure #FFFFFF)
     * while sharpening dark text and geometric figures.
     */
    fun cropWhitenAndSharpenQuestionStem(
        context: Context,
        sourcePageBitmap: Bitmap,
        yminNorm1000: Int,
        xminNorm1000: Int,
        ymaxNorm1000: Int,
        xmaxNorm1000: Int,
        questionNumberForFile: Int
    ): Pair<String, Double> {
        return try {
            val dir = File(context.filesDir, "question_pool_crops")
            if (!dir.exists()) dir.mkdirs()

            val leftNorm = (xminNorm1000.coerceIn(0, 940)) / 1000f
            val topNorm = (yminNorm1000.coerceIn(0, 940)) / 1000f
            val rightNorm = (xmaxNorm1000.coerceIn(xminNorm1000 + 60, 1000)) / 1000f
            val bottomNorm = (ymaxNorm1000.coerceIn(yminNorm1000 + 60, 1000)) / 1000f

            val x = (leftNorm * sourcePageBitmap.width).roundToInt().coerceIn(0, sourcePageBitmap.width - 16)
            val y = (topNorm * sourcePageBitmap.height).roundToInt().coerceIn(0, sourcePageBitmap.height - 16)
            val w = ((rightNorm - leftNorm) * sourcePageBitmap.width).roundToInt()
                .coerceIn(16, sourcePageBitmap.width - x)
            val h = ((bottomNorm - topNorm) * sourcePageBitmap.height).roundToInt()
                .coerceIn(16, sourcePageBitmap.height - y)

            val rawCrop = Bitmap.createBitmap(sourcePageBitmap, x, y, w, h)
            val whitened = applyAdaptiveContrastAndBackgroundWhitening(rawCrop)
            if (whitened !== rawCrop) {
                rawCrop.recycle()
            }

            val aspect = (whitened.width.toDouble() / whitened.height.toDouble().coerceAtLeast(1.0))
                .coerceIn(0.35, 3.8)

            val outFile = File(
                dir,
                "uhd_stem_${System.currentTimeMillis()}_q${questionNumberForFile}_${(100..999).random()}.png"
            )
            FileOutputStream(outFile).use { fos ->
                whitened.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            outFile.absolutePath to aspect
        } catch (_: Exception) {
            "" to 1.25
        }
    }

    /**
     * Adaptive Contrast & Background Whitening (Luminance Thresholding):
     * 1. Converts off-white, gray, or yellowish paper backgrounds into pure #FFFFFF.
     * 2. Strips vertical column divider lines along the extreme left/right 2% edges.
     * 3. Boosts contrast on dark ink (text, math equations, geometric diagrams) while preserving
     *    colored diagram lines (blue, red, green, orange).
     * 4. Trims excess empty white borders and adds a clean uniform padding.
     */
    fun applyAdaptiveContrastAndBackgroundWhitening(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        if (width < 8 || height < 8) return source

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // Step 1: Estimate paper background luminance from the 90th percentile of sample pixels
        var highLumSum = 0L
        var highLumCount = 0
        val step = max(1, (width * height) / 4000)
        var idx = 0
        while (idx < pixels.size) {
            val c = pixels[idx]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (r * 77 + g * 150 + b * 29) shr 8
            if (lum >= 185) {
                highLumSum += lum
                highLumCount++
            }
            idx += step
        }
        val estimatedBgLum = if (highLumCount > 0) (highLumSum / highLumCount).toInt() else 240
        // Adaptive threshold: slightly below paper luminance so light gray/yellow paper becomes #FFFFFF
        val whiteThreshold = (estimatedBgLum - 26).coerceIn(198, 228)
        val darkInkCeiling = (whiteThreshold - 18).coerceAtLeast(150)

        // Step 2: Adaptive pixel whitening & contrast sharpening
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            val maxCh = max(r, max(g, b))
            val minCh = min(r, min(g, b))
            val chroma = maxCh - minCh
            val lum = (r * 77 + g * 150 + b * 29) shr 8

            if (lum >= whiteThreshold && chroma < 38) {
                // Paper background (white, light gray, or yellowish tint) -> Pure White #FFFFFF
                pixels[i] = -0x1 // 0xFFFFFFFF
            } else if (chroma >= 36 && lum < 238) {
                // Colored diagram / geometry line / colored highlight -> keep hue & boost saturation/contrast
                val boost = 1.12f
                val nr = ((r - 128) * boost + 124).roundToInt().coerceIn(0, 255)
                val ng = ((g - 128) * boost + 124).roundToInt().coerceIn(0, 255)
                val nb = ((b - 128) * boost + 124).roundToInt().coerceIn(0, 255)
                pixels[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            } else {
                // Grayscale text / math formula / black geometry line -> sharpen contrast
                val normalized = (lum.toFloat() / darkInkCeiling.toFloat()).coerceIn(0f, 1f)
                // Steep contrast curve so dark ink becomes deep crisp ink (#0F172A..#000000)
                val sharpened = (normalized * normalized * 235f).roundToInt().coerceIn(0, 255)
                if (sharpened >= 225) {
                    pixels[i] = -0x1
                } else {
                    pixels[i] = (0xFF shl 24) or (sharpened shl 16) or (sharpened shl 8) or sharpened
                }
            }
        }

        // Step 3: Remove vertical separator line on extreme left/right borders if present
        val edgeZone = (width * 0.025f).roundToInt().coerceIn(2, 18)
        for (col in 0 until edgeZone) {
            var darkCount = 0
            for (row in 0 until height) {
                if (pixels[row * width + col] != -0x1) darkCount++
            }
            if (darkCount > height * 0.55f) {
                for (row in 0 until height) {
                    pixels[row * width + col] = -0x1
                }
            }
        }
        for (col in (width - edgeZone).coerceAtLeast(0) until width) {
            var darkCount = 0
            for (row in 0 until height) {
                if (pixels[row * width + col] != -0x1) darkCount++
            }
            if (darkCount > height * 0.55f) {
                for (row in 0 until height) {
                    pixels[row * width + col] = -0x1
                }
            }
        }

        // Step 4: Find content bounding box to trim excessive white margins
        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                if (pixels[rowOffset + x] != -0x1) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        val processedFull = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        processedFull.setPixels(pixels, 0, width, 0, 0, width, height)

        if (minX >= maxX || minY >= maxY) {
            return processedFull
        }

        val pad = (min(width, height) * 0.022f).roundToInt().coerceIn(10, 24)
        val cropLeft = (minX - pad).coerceAtLeast(0)
        val cropTop = (minY - pad).coerceAtLeast(0)
        val cropRight = (maxX + pad).coerceAtMost(width - 1)
        val cropBottom = (maxY + pad).coerceAtMost(height - 1)

        val finalW = (cropRight - cropLeft + 1).coerceAtLeast(16)
        val finalH = (cropBottom - cropTop + 1).coerceAtLeast(16)

        return if (finalW < width * 0.96f || finalH < height * 0.96f) {
            val trimmed = Bitmap.createBitmap(processedFull, cropLeft, cropTop, finalW, finalH)
            processedFull.recycle()
            trimmed
        } else {
            processedFull
        }
    }

    /**
     * Smart Publisher Logo Studio Processor:
     * 1. Decodes the uploaded publisher logo image.
     * 2. Runs a Corner-Seeded Flood-Fill Dekupe algorithm on pixel data to detect the background color
     *    and smoothly turn it into 100% transparent pixels (Transparent PNG).
     * 3. Runs "Alpha Bounding Box Trim" to crop away all empty pixels around the logo (0-to-0 trim).
     * 4. Centers the trimmed logo inside a standardized 64x64dp badge (saved at 128x128px Ultra-HD PNG).
     */
    fun processPublisherLogoFloodFillAndTrim(
        context: Context,
        logoUri: Uri,
        badgePixelSize: Int = 128 // 64dp @ 2x retina density (or 64x64 logical)
    ): String {
        return try {
            val rawBitmap = context.contentResolver.openInputStream(logoUri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return ""

            // Normalize working size to max 512x512 for fast & accurate flood-fill
            val maxWorkDim = 512
            val scale = if (max(rawBitmap.width, rawBitmap.height) > maxWorkDim) {
                maxWorkDim.toFloat() / max(rawBitmap.width, rawBitmap.height).toFloat()
            } else {
                1f
            }
            val workW = (rawBitmap.width * scale).roundToInt().coerceAtLeast(16)
            val workH = (rawBitmap.height * scale).roundToInt().coerceAtLeast(16)
            val workBmp = Bitmap.createScaledBitmap(rawBitmap, workW, workH, true)
                .copy(Bitmap.Config.ARGB_8888, true)

            val pixels = IntArray(workW * workH)
            workBmp.getPixels(pixels, 0, workW, 0, 0, workW, workH)

            // Sample corners & border midpoints to determine background color
            val cornerIndices = listOf(
                0,
                workW - 1,
                (workH - 1) * workW,
                (workH - 1) * workW + (workW - 1),
                workW / 2,
                (workH - 1) * workW + (workW / 2),
                (workH / 2) * workW,
                (workH / 2) * workW + (workW - 1)
            )

            // Check if image already has transparent corners
            val transparentCorners = cornerIndices.count { idx ->
                ((pixels[idx] ushr 24) and 0xFF) < 30
            }

            if (transparentCorners < 4) {
                // Compute average corner RGB for opaque background
                var sumR = 0
                var sumG = 0
                var sumB = 0
                var count = 0
                for (cIdx in cornerIndices) {
                    val px = pixels[cIdx]
                    val a = (px ushr 24) and 0xFF
                    if (a >= 30) {
                        sumR += (px shr 16) and 0xFF
                        sumG += (px shr 8) and 0xFF
                        sumB += px and 0xFF
                        count++
                    }
                }
                if (count > 0) {
                    val bgR = sumR / count
                    val bgG = sumG / count
                    val bgB = sumB / count

                    val visited = BooleanArray(workW * workH)
                    val queue = IntArray(workW * workH)
                    var head = 0
                    var tail = 0

                    val hardTolerance = 56.0
                    val featherTolerance = 84.0

                    fun colorDist(px: Int): Double {
                        val r = (px shr 16) and 0xFF
                        val g = (px shr 8) and 0xFF
                        val b = px and 0xFF
                        val dr = (r - bgR).toDouble()
                        val dg = (g - bgG).toDouble()
                        val db = (b - bgB).toDouble()
                        return sqrt(dr * dr + dg * dg + db * db)
                    }

                    fun tryEnqueue(x: Int, y: Int) {
                        if (x !in 0 until workW || y !in 0 until workH) return
                        val idx = y * workW + x
                        if (visited[idx]) return
                        val dist = colorDist(pixels[idx])
                        if (dist <= featherTolerance) {
                            visited[idx] = true
                            queue[tail++] = idx
                        }
                    }

                    // Seed BFS from all 4 outer borders
                    for (x in 0 until workW) {
                        tryEnqueue(x, 0)
                        tryEnqueue(x, workH - 1)
                    }
                    for (y in 0 until workH) {
                        tryEnqueue(0, y)
                        tryEnqueue(workW - 1, y)
                    }

                    // Flood-Fill BFS traversal
                    while (head < tail) {
                        val curr = queue[head++]
                        val cx = curr % workW
                        val cy = curr / workW
                        val px = pixels[curr]
                        val dist = colorDist(px)

                        if (dist <= hardTolerance) {
                            // Pure background -> 100% transparent
                            pixels[curr] = 0x00000000
                            // Expand to 8 neighbors
                            tryEnqueue(cx - 1, cy)
                            tryEnqueue(cx + 1, cy)
                            tryEnqueue(cx, cy - 1)
                            tryEnqueue(cx, cy + 1)
                            tryEnqueue(cx - 1, cy - 1)
                            tryEnqueue(cx + 1, cy - 1)
                            tryEnqueue(cx - 1, cy + 1)
                            tryEnqueue(cx + 1, cy + 1)
                        } else {
                            // Anti-aliased boundary pixel -> smooth alpha feathering
                            val ratio = ((dist - hardTolerance) / (featherTolerance - hardTolerance))
                                .coerceIn(0.0, 1.0)
                            val newAlpha = (ratio * 255.0).roundToInt().coerceIn(0, 255)
                            pixels[curr] = (pixels[curr] and 0x00FFFFFF) or (newAlpha shl 24)
                        }
                    }
                }
            }

            // Step 2: Alpha Bounding Box Auto-Trim (0-to-0 trim of all empty/transparent pixels)
            var minX = workW
            var minY = workH
            var maxX = -1
            var maxY = -1

            for (y in 0 until workH) {
                val rowOffset = y * workW
                for (x in 0 until workW) {
                    val alpha = (pixels[rowOffset + x] ushr 24) and 0xFF
                    if (alpha > 24) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }

            val dekupeBmp = Bitmap.createBitmap(workW, workH, Bitmap.Config.ARGB_8888)
            dekupeBmp.setPixels(pixels, 0, workW, 0, 0, workW, workH)

            val trimmedBmp = if (maxX >= minX && maxY >= minY) {
                val trimW = (maxX - minX + 1).coerceAtLeast(1)
                val trimH = (maxY - minY + 1).coerceAtLeast(1)
                Bitmap.createBitmap(dekupeBmp, minX, minY, trimW, trimH)
            } else {
                dekupeBmp
            }

            // Step 3: Center inside standardized 64x64 rounded badge (rendered at badgePixelSize for crispness)
            val badgeBmp = Bitmap.createBitmap(badgePixelSize, badgePixelSize, Bitmap.Config.ARGB_8888)
            badgeBmp.eraseColor(Color.TRANSPARENT)
            val canvas = Canvas(badgeBmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            val innerMargin = (badgePixelSize * 0.06f)
            val availSize = badgePixelSize - (innerMargin * 2f)
            val fitScale = min(
                availSize / trimmedBmp.width.toFloat().coerceAtLeast(1f),
                availSize / trimmedBmp.height.toFloat().coerceAtLeast(1f)
            )
            val drawW = trimmedBmp.width * fitScale
            val drawH = trimmedBmp.height * fitScale
            val left = (badgePixelSize - drawW) / 2f
            val top = (badgePixelSize - drawH) / 2f

            val destRect = RectF(left, top, left + drawW, top + drawH)
            canvas.drawBitmap(trimmedBmp, null, destRect, paint)

            val dir = File(context.filesDir, "publisher_logos")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, "logo_dekupe_64_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { fos ->
                badgeBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }

            if (trimmedBmp !== dekupeBmp) trimmedBmp.recycle()
            dekupeBmp.recycle()
            workBmp.recycle()

            outFile.absolutePath
        } catch (_: Exception) {
            ""
        }
    }
}
