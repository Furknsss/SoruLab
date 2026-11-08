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
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.AnswerAnalysisBucket
import com.example.util.TestAnswerAnalytics

private val frequencyInk = Color(0xFF25215D)
private val frequencyBlue = Color(0xFF2974E8)
private val frequencyWrong = Color(0xFFDB4751)
private val frequencyBlank = Color(0xFFD3D3E0)
private val frequencyMuted = Color(0xFF73778F)
private val frequencyBackground = Color(0xFFF8F8FC)

/**
 * Photo-reference-inspired frequency tree. All numbers come from the
 * source-key-verified TestAnswerAnalytics; no fabricated official kazanım.
 */
@Composable
internal fun PremiumMistakeAnalysisScreen(
    title: String,
    summary: TestAnswerAnalytics,
    onBack: () -> Unit,
    onStrengthen: () -> Unit = {}
) {
    BackHandler(onBack = onBack)
    val root = remember(title, summary) {
        AnswerAnalysisBucket(
            label = title,
            correct = summary.correct,
            wrong = summary.wrong,
            blank = summary.blank,
            ungraded = summary.ungraded
        )
    }
    val topics = remember(summary) {
        summary.hierarchy.flatMap { lesson -> lesson.children }
            .sortedWith(
                compareByDescending<AnswerAnalysisBucket> { it.wrong }
                    .thenByDescending { it.blank }
                    .thenBy { it.label }
            )
    }
    Column(
        Modifier.fillMaxSize()
            .background(frequencyBackground)
            .testTag("premium_error_frequency_screen")
    ) {
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = frequencyInk)
                }
                Text(
                    "Hata Frekansı",
                    modifier = Modifier.weight(1f).padding(end = 48.dp),
                    color = frequencyInk, fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 1020.dp)
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        title, color = frequencyInk, fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                    )
                    StrengthenButton(onStrengthen)
                    FrequencySuccessCard(
                        bucket = root,
                        modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth(),
                        chartTag = "frequency_donut_chart"
                    )
                }
            }
            if (topics.isNotEmpty()) {
                item { FrequencyTreeConnector() }
                items(
                    items = topics.chunked(2),
                    key = { chunk -> chunk.joinToString("|") { it.label } }
                ) { pair ->
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth >= 680.dp && pair.size == 2) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(22.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                TopicFrequencyBranch(pair[0], onStrengthen, Modifier.weight(1f))
                                TopicFrequencyBranch(pair[1], onStrengthen, Modifier.weight(1f))
                            }
                        } else {
                            Column(
                                Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                pair.forEach { topic ->
                                    TopicFrequencyBranch(
                                        topic, onStrengthen,
                                        Modifier.fillMaxWidth().widthIn(max = 550.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "Bu testte konu veya kazanım verisi bulunamadı.",
                        color = frequencyMuted,
                        modifier = Modifier.padding(20.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (summary.ungraded > 0) {
                item {
                    Text(
                        "${summary.ungraded} sorunun cevap anahtarı doğrulanmadığı için " +
                            "başarı yüzdesine katılmadı.",
                        color = frequencyMuted, fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (summary.inferredMetadataCount > 0) {
                item {
                    Text(
                        "Otomatik önerilen kazanımlar kesin müfredat eşleşmesi sayılmaz.",
                        color = frequencyMuted, fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun StrengthenButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = CircleShape, color = frequencyBlue,
        shadowElevation = 3.dp, modifier = Modifier.testTag("frequency_strengthen")
    ) {
        Text(
            "Güçlen 💪", color = Color.White,
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 27.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun FrequencyTreeConnector() {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(51.dp),
        contentAlignment = Alignment.Center
    ) {
        val twoBranches = maxWidth >= 680.dp
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val lineColor = Color(0xFF3B78D5)
            val top = 2.dp.toPx()
            val joint = 23.dp.toPx()
            drawLine(
                color = lineColor,
                start = Offset(cx, top),
                end = Offset(cx, joint),
                strokeWidth = 1.5.dp.toPx()
            )
            drawCircle(
                color = Color(0xFFDBDDE8), radius = 7.dp.toPx(),
                center = Offset(cx, joint)
            )
            if (twoBranches) {
                val ends = listOf(size.width * .25f, size.width * .75f)
                ends.forEach { endX ->
                    val branch = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx, joint + 6.dp.toPx())
                        cubicTo(
                            cx, size.height * 0.89f,
                            endX, size.height * 0.74f,
                            endX, size.height - 2.dp.toPx()
                        )
                    }
                    drawPath(
                        path = branch, color = lineColor,
                        style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            } else {
                drawLine(
                    color = lineColor,
                    start = Offset(cx, joint + 6.dp.toPx()),
                    end = Offset(cx, size.height),
                    strokeWidth = 1.2.dp.toPx()
                )
            }
        }
    }
}

@Composable
private fun TopicFrequencyBranch(
    bucket: AnswerAnalysisBucket,
    onStrengthen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember(bucket.label) { mutableStateOf(false) }
    val hasOutcomes = bucket.children.isNotEmpty()
    Column(
        modifier = modifier.animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            bucket.label,
            color = frequencyInk, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, maxLines = 2,
            textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis
        )
        StrengthenButton(onStrengthen)
        FrequencySuccessCard(
            bucket, modifier = Modifier.fillMaxWidth(),
            chartTag = "frequency_topic_${bucket.label}"
        )
        if (hasOutcomes) {
            Surface(
                onClick = { expanded = !expanded },
                color = Color.White,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color(0xFFE6E8EF)),
                modifier = Modifier.testTag("frequency_expand_${bucket.label}")
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (expanded) "Kazanımları gizle" else "Kazanımları gör",
                        fontSize = 12.sp, color = frequencyBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                        contentDescription = null, tint = frequencyBlue,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(190)) + fadeIn(),
                exit = shrinkVertically(tween(140)) + fadeOut()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    bucket.children.forEach { outcome ->
                        OutcomeFrequencyCard(outcome)
                    }
                }
            }
        }
    }
}

@Composable
private fun OutcomeFrequencyCard(bucket: AnswerAnalysisBucket) {
    var expanded by remember(bucket.label) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(17.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
            .testTag("frequency_outcome_${bucket.label}")
    ) {
        Column(
            Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(
                    enabled = bucket.children.isNotEmpty()
                ) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    bucket.label, color = frequencyInk,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (bucket.children.isNotEmpty()) Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Soru türleri",
                    tint = frequencyMuted, modifier = Modifier.size(19.dp)
                )
            }
            FrequencySuccessCard(
                bucket,
                modifier = Modifier.fillMaxWidth(),
                compact = true
            )
            AnimatedVisibility(visible = expanded && bucket.children.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    bucket.children.forEach { type ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                type.label, fontSize = 11.sp, color = frequencyMuted,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${type.correct}D  ${type.wrong}Y  ${type.blank}B",
                                fontSize = 11.sp, color = frequencyInk
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FrequencySuccessCard(
    bucket: AnswerAnalysisBucket,
    modifier: Modifier,
    compact: Boolean = false,
    chartTag: String = ""
) {
    val ratio = if (bucket.graded > 0) bucket.correct.toFloat() / bucket.graded else 0f
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 12.dp else 19.dp),
        color = Color.White,
        shadowElevation = if (compact) 0.dp else 3.dp,
        border = BorderStroke(1.dp, Color(0xFFEBEDF4))
    ) {
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = if (compact) 9.dp else 18.dp, vertical = if (compact) 8.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (!compact) Text(
                "Başarı Oranı", color = frequencyMuted, fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f).height(if (compact) 82.dp else 110.dp)
                        .then(if (chartTag.isNotBlank()) Modifier.testTag(chartTag) else Modifier),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val displayProgress by animateFloatAsState(
                        targetValue = ratio,
                        animationSpec = tween(durationMillis = 320),
                        label = "frequency_success_arc"
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        val strokeWidth = if (compact) 7.dp.toPx() else 10.dp.toPx()
                        val pad = strokeWidth / 2f + 2.dp.toPx()
                        val diameter = (size.width - 2f * pad)
                            .coerceAtMost((size.height - pad) * 1.85f)
                            .coerceAtLeast(1f)
                        val x = (size.width - diameter) / 2f
                        val top = 4.dp.toPx()
                        drawArc(
                            color = Color(0xFFD8D9E5), startAngle = 180f,
                            sweepAngle = 180f, useCenter = false,
                            topLeft = Offset(x, top),
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                        if (bucket.graded > 0 && displayProgress > 0f) {
                            drawArc(
                                color = frequencyBlue, startAngle = 180f,
                                sweepAngle = 180f * displayProgress, useCenter = false,
                                topLeft = Offset(x, top),
                                size = Size(diameter, diameter),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                        }
                    }
                    Text(
                        if (bucket.graded > 0) "%${(ratio * 100).toInt()}" else "—",
                        color = frequencyInk, fontSize = if (compact) 19.sp else 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = if (compact) 8.dp else 9.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1.15f),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)
                ) {
                    FrequencyCountRow("Toplam S.", bucket.total, frequencyBlue, compact)
                    FrequencyCountRow("Doğru", bucket.correct, Color(0xFF237C69), compact)
                    FrequencyCountRow("Yanlış", bucket.wrong, frequencyWrong, compact)
                    FrequencyCountRow("Boş", bucket.blank, frequencyBlank, compact)
                    if (bucket.ungraded > 0) {
                        FrequencyCountRow("Belirsiz", bucket.ungraded, Color(0xFFB88D4F), compact)
                    }
                }
            }
        }
    }
}

@Composable
private fun FrequencyCountRow(label: String, count: Int, accent: Color, compact: Boolean) {
    Row(
        Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(6.dp).background(accent, CircleShape))
        Text(
            label, color = frequencyMuted,
            fontSize = if (compact) 10.sp else 11.5.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text(
            "$count", color = frequencyInk,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
