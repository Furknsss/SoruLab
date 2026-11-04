package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.AnswerAnalysisBucket
import com.example.util.TestAnswerAnalytics

private val ink = Color(0xFF262353)
private val blue = Color(0xFF3974E8)
private val correct = Color(0xFF16A36A)
private val wrong = Color(0xFFEB5966)
private val blank = Color(0xFFB7BECD)
private val ungraded = Color(0xFFE7A75B)

@Composable
internal fun PremiumMistakeAnalysisScreen(
    title: String,
    summary: TestAnswerAnalytics,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val priorities = remember(summary) {
        summary.hierarchy.flatMap { it.children }
            .flatMap { it.children }
            .filter { it.wrong > 0 && it.label != "Alt kazanım belirlenmedi" }
            .sortedWith(compareByDescending<AnswerAnalysisBucket> { it.wrong }
                .thenByDescending { it.blank })
            .take(3)
    }
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FC))
            .testTag("premium_error_frequency_screen")
    ) {
        Surface(color = Color.White, shadowElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Analizden geri", tint = ink)
                }
                Column(Modifier.weight(1f)) {
                    Text("Hata Frekansı", color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(title, fontSize = 12.sp, color = Color(0xFF657089),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(shape = CircleShape, color = Color(0xFFEAF0FE)) {
                    Text("SoruLab", color = blue, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 920.dp)
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Nerede güçlüsün, nerede zorlanıyorsun?", color = ink,
                        fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("Yalnızca doğrulanmış cevaplar puanlandı. Alt kazanımlar ve soru türleri " +
                        "soruya ait etiketlerden veya metne dayalı önerilerden oluşur.",
                        color = Color(0xFF6B7489), fontSize = 13.sp)
                }
            }
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = Color.White,
                    shadowElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ResultDonut(summary)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            SmallResultStatistic("Doğru", summary.correct, correct, Modifier.weight(1f))
                            SmallResultStatistic("Yanlış", summary.wrong, wrong, Modifier.weight(1f))
                            SmallResultStatistic("Boş", summary.blank, blank, Modifier.weight(1f))
                            SmallResultStatistic("Belirsiz", summary.ungraded, ungraded, Modifier.weight(1f))
                        }
                    }
                }
            }
            if (summary.ungraded > 0) item {
                Surface(shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFFF7EA), border = BorderStroke(1.dp, Color(0xFFF5D9B5))) {
                    Text("${summary.ungraded} sorunun kaynak cevap anahtarı henüz doğrulanmadı; " +
                        "bunlar yanlış veya boş gibi değerlendirilmedi.",
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        color = Color(0xFF8D5B28), fontSize = 13.sp)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Ders → Konu → Alt kazanım → Soru türü",
                        color = ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Bir satıra dokunarak alt kırılımları aç.",
                        fontSize = 12.sp, color = Color(0xFF7C859B))
                }
            }
            if (summary.hierarchy.isEmpty()) item {
                Text("Bu testte analiz edilecek soru bulunamadı.",
                    color = Color(0xFF6B7489))
            }
            items(summary.hierarchy, key = { "lesson_" + it.label }) { bucket ->
                AnalysisExpandableRow(bucket = bucket, level = 0)
            }
            item {
                Text("Soru türlerine göre", color = ink,
                    fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            items(summary.questionTypes, key = { "type_" + it.label }) { bucket ->
                AnalysisExpandableRow(bucket = bucket, level = 3)
            }
            if (priorities.isNotEmpty()) item {
                Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFECF2FF)) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Çalışma önceliğin", color = ink, fontSize = 16.sp,
                            fontWeight = FontWeight.Bold)
                        priorities.forEach { priority ->
                            Text("• ${priority.label}: ${priority.wrong} yanlış, ${priority.blank} boş",
                                color = Color(0xFF3F527C), fontSize = 13.sp)
                        }
                        Text("Bu öneriler etiketlere ve doğrulanmış yanıtlarına dayanır; " +
                            "otomatik etiketler kesin kazanım tespiti değildir.",
                            color = Color(0xFF7180A5), fontSize = 11.sp)
                    }
                }
            }
            if (summary.inferredMetadataCount > 0) item {
                Text("${summary.inferredMetadataCount} sorunun alt kazanım veya soru türü " +
                    "etiketi, soru metninden otomatik önerildi. Belirlenemeyen etiketler " +
                    "ayrı gösterilir; uydurma kazanım üretilmez.",
                    color = Color(0xFF7C859B), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ResultDonut(summary: TestAnswerAnalytics) {
    var mounted by remember(summary) { mutableStateOf(false) }
    LaunchedEffect(summary) { mounted = true }
    val reveal by animateFloatAsState(
        targetValue = if (mounted) 1f else 0f,
        animationSpec = tween(650), label = "frequency_chart_reveal"
    )
    val total = summary.total.coerceAtLeast(1)
    Box(Modifier.size(178.dp).testTag("frequency_donut_chart"),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val segments = listOf(
                summary.correct to correct, summary.wrong to wrong,
                summary.blank to blank, summary.ungraded to ungraded
            )
            if (summary.total == 0) {
                drawArc(Color(0xFFE6EAF3), 0f, 360f, false,
                    style = Stroke(width = 17.dp.toPx()))
            } else {
                var start = -90f
                segments.forEach { (count, color) ->
                    if (count > 0) {
                        val sweep = count.toFloat() / total * 360f * reveal
                        drawArc(color, startAngle = start, sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = 17.dp.toPx(), cap = StrokeCap.Round))
                        start += count.toFloat() / total * 360f
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${summary.total}", fontSize = 31.sp,
                fontWeight = FontWeight.Bold, color = ink)
            Text("TOPLAM SORU", fontSize = 9.sp, color = Color(0xFF8992A7))
        }
    }
}

@Composable
private fun SmallResultStatistic(title: String, count: Int, accent: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp), color = accent.copy(alpha = 0.09f)
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$count", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = accent)
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = ink)
        }
    }
}

@Composable
private fun AnalysisExpandableRow(bucket: AnswerAnalysisBucket, level: Int) {
    var expanded by remember(bucket.label, level) { mutableStateOf(level < 2) }
    val canExpand = bucket.children.isNotEmpty()
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(if (level == 0) 18.dp else 12.dp))
            .background(if (level == 0) Color.White else Color(0xFFF4F6FA))
            .animateContentSize()
            .padding(if (level == 0) 12.dp else 8.dp)
            .testTag("frequency_level_${level}_${bucket.label}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(enabled = canExpand) { expanded = !expanded }
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(bucket.label, fontSize = if (level == 0) 15.sp else 13.sp,
                    color = ink, fontWeight = FontWeight.SemiBold)
                Text("${bucket.total} soru", fontSize = 11.sp,
                    color = Color(0xFF8992A7))
            }
            MiniCount("D", bucket.correct, correct)
            MiniCount("Y", bucket.wrong, wrong)
            MiniCount("B", bucket.blank, blank)
            if (bucket.ungraded > 0) MiniCount("?", bucket.ungraded, ungraded)
            if (canExpand) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    "Alt kırılımları ${if (expanded) "kapat" else "aç"}",
                    tint = Color(0xFF66728A), modifier = Modifier.size(20.dp)
                )
            }
        }
        if (bucket.graded > 0) {
            Row(
                Modifier.fillMaxWidth().height(5.dp)
                    .clip(CircleShape).background(Color(0xFFE6E9F1))
            ) {
                val denom = bucket.graded.toFloat()
                if (bucket.correct > 0) Box(Modifier.fillMaxHeight()
                    .weight(bucket.correct / denom).background(correct))
                if (bucket.wrong > 0) Box(Modifier.fillMaxHeight()
                    .weight(bucket.wrong / denom).background(wrong))
                if (bucket.blank > 0) Box(Modifier.fillMaxHeight()
                    .weight(bucket.blank / denom).background(blank))
            }
        }
        AnimatedVisibility(
            visible = expanded && canExpand,
            enter = expandVertically(tween(230)) + fadeIn(),
            exit = shrinkVertically(tween(190)) + fadeOut()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bucket.children.forEach { child ->
                    AnalysisExpandableRow(bucket = child, level = level + 1)
                }
            }
        }
    }
}

@Composable
private fun MiniCount(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text("$label:$count", fontSize = 10.sp, color = Color(0xFF536079),
            fontWeight = FontWeight.SemiBold)
    }
}
