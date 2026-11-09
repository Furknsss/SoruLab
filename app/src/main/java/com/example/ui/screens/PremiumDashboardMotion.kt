package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TestResultRecord
import java.util.Calendar

/**
 * Veriye dayalı premium aktivite özeti.
 *
 * Sadece tamamlanmış testlerden, cihazın yerel takvim günlerine göre hesaplar.
 * Soru çözme ekranına, test kaydına veya değerlendirme algoritmasına dokunmaz.
 */
@Composable
internal fun PremiumSevenDayActivity(
    results: List<TestResultRecord>,
    modifier: Modifier = Modifier,
    onOpenStatistics: (() -> Unit)? = null
) {
    val activity = remember(results) {
        val calendar = Calendar.getInstance()
        fun midnight(c: Calendar): Long {
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        val days = (6 downTo 0).map { daysAgo ->
            (calendar.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -daysAgo)
                midnight(this)
            }
        }
        val counts = IntArray(7)
        results.asSequence()
            .filter { it.isSolvedAttempt }
            .forEach { record ->
                val timestamp = record.createdAt?.toDate()?.time ?: return@forEach
                val resultCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
                val day = midnight(resultCalendar)
                val index = days.indexOfFirst { it.timeInMillis == day }
                if (index >= 0) {
                    counts[index] += record.correctCount +
                        record.wrongCount + record.emptyCount + record.ungradedCount
                }
            }

        days.mapIndexed { index, day ->
            val name = when (day.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Pzt"
                Calendar.TUESDAY -> "Sal"
                Calendar.WEDNESDAY -> "Çar"
                Calendar.THURSDAY -> "Per"
                Calendar.FRIDAY -> "Cum"
                Calendar.SATURDAY -> "Cmt"
                else -> "Paz"
            }
            name to counts[index]
        }
    }

    val weeklyTotal = activity.sumOf { it.second }
    val peak = activity.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E9F5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFCFDFF), Color.White, Color(0xFFF7FAFF))
                    )
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF275CF5), Color(0xFF8B5CF6))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoGraph,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Son 7 Gün",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            ),
                            color = Color(0xFF101A35)
                        )
                        Text(
                            text = "Günlük soru aktiviten",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF71809A)
                        )
                    }
                }
                if (onOpenStatistics != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onOpenStatistics)
                            .padding(horizontal = 5.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Analiz",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF315CF0)
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "İstatistikleri aç",
                            tint = Color(0xFF315CF0),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                activity.forEachIndexed { index, (day, questionCount) ->
                    val target = if (questionCount == 0) 0.055f
                        else 0.14f + (questionCount.toFloat() / peak) * 0.86f
                    val heightFraction by animateFloatAsState(
                        targetValue = if (revealed) target else 0.04f,
                        animationSpec = tween(
                            durationMillis = 520 + index * 65,
                            easing = FastOutSlowInEasing
                        ),
                        label = "weekly_activity_bar_$index"
                    )
                    Column(
                        modifier = Modifier.weight(1f)
                            .semantics {
                                contentDescription = "$day: $questionCount soru"
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(98.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.67f)
                                    .fillMaxHeight(heightFraction)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(
                                        if (questionCount > 0) {
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF8E7AFF), Color(0xFF2563EB))
                                            )
                                        } else {
                                            Brush.verticalGradient(
                                                listOf(Color(0xFFE8ECF4), Color(0xFFDCE3F0))
                                            )
                                        }
                                    )
                            )
                        }
                        Text(
                            text = day,
                            color = if (index == 6) Color(0xFF315CF0) else Color(0xFF75839B),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = if (index == 6) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFFEDF3FF))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$weeklyTotal",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    ),
                    color = Color(0xFF1D4ED8)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (weeklyTotal > 0) "son 7 günde çözülen soru"
                           else "Bu hafta henüz soru kaydı yok",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFF405579)
                )
            }
        }
    }
}
