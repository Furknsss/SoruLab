package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.YksQuestion
import com.example.util.StudioImageAndPdfEngine

@Composable
internal fun QuestionSolutionDialog(
    question: YksQuestion,
    returnedText: String,
    isLoading: Boolean,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val solution = remember(question, returnedText) { StoredQuestionSolution.from(question, returnedText) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(0.94f)
                .heightIn(max = maxHeight).testTag("stored_solution_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            shadowElevation = 12.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${question.number}. Soru • Soru Çözümü", fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, color = Color(0xFF25215D))
                        Text(question.topicHeader, fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                    TextButton(onClick = onDismiss) { Text("Kapat") }
                }
                HorizontalDivider()
                Column(
                    Modifier.weight(1f, fill = false).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).testTag("stored_solution_content"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(solution.answerNotice, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (solution.verifiedAnswer != null) Color(0xFF15803D) else Color(0xFFB45309))
                    when {
                        isLoading -> {
                            Column(Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(Modifier.testTag("stored_solution_loading"))
                                Text("Kayıtlı çözüm açılıyor…", fontSize = 14.sp)
                            }
                        }
                        loadFailed -> {
                            Text("Kayıtlı çözüm okunamadı. Tekrar deneyebilirsin.",
                                Modifier.testTag("stored_solution_error"), color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = onRetry) { Text("Tekrar dene") }
                        }
                        else -> {
                            val bitmap = StudioImageAndPdfEngine.rememberGuaranteedQuestionBitmap(question)
                            if (bitmap != null) {
                                Image(bitmap.asImageBitmap(), contentDescription = "Sorunun kaynak görseli",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp))
                            }
                            if (!solution.hasSolution) {
                                Surface(color = Color(0xFFF8FAFC), shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Bu soru için kayıtlı çözüm bulunmuyor.",
                                            Modifier.testTag("stored_solution_empty"), fontWeight = FontWeight.Bold)
                                        Text("Cevap anahtarı, adım adım açıklama yerine geçmez. " +
                                            "Bu ekranda yeni bir çözüm üretilmez.", fontSize = 14.sp)
                                    }
                                }
                            } else {
                                Text("Soruda kayıtlı açıklama", fontSize = 12.sp, color = Color(0xFF64748B))
                                solution.sections.forEach { section ->
                                    Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)), modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(section.title, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                                            SelectionContainer {
                                                Text(section.body, fontSize = 15.sp, lineHeight = 23.sp,
                                                    color = Color(0xFF1E293B))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Soruma dön") }
            }
        }
    }
}
