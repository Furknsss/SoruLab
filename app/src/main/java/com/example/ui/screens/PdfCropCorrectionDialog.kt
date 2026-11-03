package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.StudioImageAndPdfEngine
import com.example.viewmodel.ExtractedPdfQuestionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

/** Correct against the original page, never a crop that has already lost content. */
@Composable
internal fun PdfCropCorrectionDialog(
    question: ExtractedPdfQuestionItem,
    onDismiss: () -> Unit,
    onApply: (ExtractedPdfQuestionItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember(question.id) { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var bounds by remember(question.id) { mutableStateOf(question.boxNormalized) }
    LaunchedEffect(question.id) {
        page = withContext(Dispatchers.IO) {
            StudioImageAndPdfEngine.renderPdfPageFromSeekableFile(File(question.pdfFilePath), question.pageIndex).first
        }
        loading = false
        if (page == null) error = "Kaynak PDF sayfası açılamadı. Dosyayı yeniden içe aktar."
    }
    DisposableEffect(question.id) { onDispose { page?.recycle() } }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize().padding(12.dp), color = Color.White) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kırpmayı düzelt · Sayfa ${question.pageIndex + 1}", style = MaterialTheme.typography.titleLarge)
                Text("Sorunun tamamını, şekilleri ve tüm şıkları kapsayan alanı sürükleyerek seç. Kitap kenarını dışarıda bırak.")
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val bitmap = page
                    if (bitmap != null) {
                        Image(bitmap.asImageBitmap(), "Kaynak PDF sayfası", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        Canvas(Modifier.fillMaxSize().pointerInput(bitmap) {
                            val scale = min(size.width.toFloat() / bitmap.width, size.height.toFloat() / bitmap.height)
                            val w = bitmap.width * scale
                            val h = bitmap.height * scale
                            val origin = Offset((size.width - w) / 2, (size.height - h) / 2)
                            fun normalized(point: Offset) = Offset(((point.x - origin.x) / w).coerceIn(0f, 1f), ((point.y - origin.y) / h).coerceIn(0f, 1f))
                            var start = Offset.Zero
                            detectDragGestures(onDragStart = { start = normalized(it) }) { change, _ ->
                                change.consume()
                                val end = normalized(change.position)
                                bounds = listOf(minOf(start.y, end.y), minOf(start.x, end.x), maxOf(start.y, end.y), maxOf(start.x, end.x))
                            }
                        }) {
                            if (bounds.size == 4) {
                                val scale = min(size.width / bitmap.width, size.height / bitmap.height)
                                val w = bitmap.width * scale
                                val h = bitmap.height * scale
                                val origin = Offset((size.width - w) / 2, (size.height - h) / 2)
                                drawRect(Color(0xFF346CFA), origin + Offset(bounds[1] * w, bounds[0] * h),
                                    Size((bounds[3] - bounds[1]) * w, (bounds[2] - bounds[0]) * h), style = Stroke(3.dp.toPx()))
                            }
                        }
                    } else if (loading) CircularProgressIndicator()
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Vazgeç") }
                    Button(enabled = page != null && !saving && bounds.size == 4 && bounds[2] > bounds[0] && bounds[3] > bounds[1], onClick = {
                        saving = true
                        scope.launch {
                            try {
                                val crop = withContext(Dispatchers.Default) {
                                    StudioImageAndPdfEngine.cropQuestionFromNormalizedBox(context, page!!,
                                        bounds.map { (it * 1000).toInt() }, question.pageIndex,
                                        question.questionNumberInTest, question.difficulty, addPadding = false)
                                }
                                if (crop == null) error = "Alan çok küçük veya boş. Sorunun tamamını seç."
                                else onApply(question.copy(croppedImagePath = crop.croppedImagePath,
                                    imageBase64 = crop.imageBase64, cropAspectRatio = crop.aspectRatio,
                                    boxNormalized = crop.boxNormalized))
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                error = "Kırpma kaydedilemedi. Yeniden dene."
                            } finally { saving = false }
                        }
                    }) { Text(if (saving) "Kaydediliyor…" else "Kırpmayı uygula") }
                }
            }
        }
    }
}
