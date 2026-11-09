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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
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


/**
 * Premium white home cover. Restrained surfaces, saturated accents and fast
 * compose-native animations; no extra runtime assets or new dependencies.
 */
@Composable
internal fun PremiumLightHomeHero(
    firstName: String,
    solvedTests: Int,
    solvedQuestions: Int,
    questionPool: Int,
    onPractice: () -> Unit,
    onExams: () -> Unit
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val reveal by animateFloatAsState(
        targetValue = if (entered) 1f else 0.965f,
        animationSpec = tween(460, easing = FastOutSlowInEasing),
        label = "home_reveal"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = reveal; scaleY = reveal }
            .testTag("premium_home_hero"),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFDDE7FB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFFFFFF), Color(0xFFF4F8FF), Color(0xFFF9F5FF))
                    )
                )
        ) {
            // Decorative accent: intentionally subtle, keeping the entire card light.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .size(88.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFFEDEAFF).copy(alpha = 0.55f))
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = Color(0xFF3C64F4),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "YKS 2027 • ÇALIŞMA ALANI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.65.sp
                            ),
                            color = Color(0xFF315FEA)
                        )
                    }
                    Text(
                        "HEDEF 5.000",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        ),
                        color = Color(0xFF7C3AED),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFF1EAFE))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Hoş geldin, $firstName 👋",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 25.sp,
                            lineHeight = 31.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = Color(0xFF101931)
                    )
                    Text(
                        "Her soruyla hedefe bir adım daha yaklaş.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF5D6F8C)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PremiumHeroMetric("Test", solvedTests, Color(0xFF2563EB), Color(0xFFEAF2FF), Modifier.weight(1f))
                    PremiumHeroMetric("Soru", solvedQuestions, Color(0xFF7C3AED), Color(0xFFF2EBFF), Modifier.weight(1f))
                    PremiumHeroMetric("Havuz", questionPool, Color(0xFF009C8E), Color(0xFFE2FAF3), Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPractice,
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2D5CF3),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1.27f).height(50.dp).testTag("hero_soru_pratigi_button"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            "Pratiğe Başla",
                            maxLines = 1,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = onExams,
                        modifier = Modifier.weight(0.92f).height(50.dp).testTag("hero_denemeler_button"),
                        shape = RoundedCornerShape(15.dp),
                        border = BorderStroke(1.dp, Color(0xFFD8E2F7)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF2855D5)
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(
                            Icons.Filled.Assignment,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Denemeler",
                            maxLines = 1,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumHeroMetric(
    label: String,
    value: Int,
    accent: Color,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(tint)
            .padding(horizontal = 10.dp, vertical = 13.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 23.sp
            ),
            color = accent,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            ),
            color = Color(0xFF52617F)
        )
    }
}

/** Existing statistics data, new white visual hierarchy and animated accuracy ring. */
@Composable
internal fun PremiumLightStatsOverview(
    examName: String,
    testCount: Int,
    questionCount: Int,
    accuracy: Int?,
    correct: Int,
    wrong: Int,
    empty: Int,
    ungraded: Int,
    net: Double?,
    questionPoolCount: Int
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val animatedProgress by animateFloatAsState(
        targetValue = if (revealed) (accuracy ?: 0).coerceIn(0, 100) / 100f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "stats_accuracy_ring"
    )
    Card(
        modifier = Modifier.fillMaxWidth().testTag("premium_stats_overview"),
        shape = RoundedCornerShape(29.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFDDE8F8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color.White, Color(0xFFF3F8FF), Color(0xFFFAF7FF))
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "$examName • PERFORMANS ÖZETİ",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp
                ),
                color = Color(0xFF315DE6)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = net?.let {
                            String.format(java.util.Locale.forLanguageTag("tr-TR"), "%.2f", it)
                        } ?: "—",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 38.sp,
                            lineHeight = 43.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = Color(0xFF132246)
                    )
                    Text(
                        text = "TOPLAM NET",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.75.sp
                        ),
                        color = Color(0xFF2B61EB)
                    )
                    Text(
                        text = "$testCount test  •  $questionCount soru",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B7B96)
                    )
                }
                Box(
                    modifier = Modifier.size(96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(88.dp),
                        strokeWidth = 8.dp,
                        color = Color(0xFF12B8A6),
                        trackColor = Color(0xFFE6EFF7)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = accuracy?.let { "$it%" } ?: "—",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp
                            ),
                            color = Color(0xFF152747)
                        )
                        Text(
                            text = "Doğruluk",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color(0xFF687A99)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                PremiumStatsMini("Doğru", correct, Color(0xFF059669), Color(0xFFE1F9EC), Modifier.weight(1f))
                PremiumStatsMini("Yanlış", wrong, Color(0xFFE54865), Color(0xFFFFEEF2), Modifier.weight(1f))
                PremiumStatsMini("Boş", empty, Color(0xFF697C9E), Color(0xFFEEF2F8), Modifier.weight(1f))
                PremiumStatsMini("Bekleyen", ungraded, Color(0xFFB76C00), Color(0xFFFFF4DD), Modifier.weight(1f))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFEBF3FF))
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.School,
                    contentDescription = null,
                    tint = Color(0xFF315DF0),
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Soru havuzunda $questionPoolCount soru",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFF244478)
                )
            }
        }
    }
}

@Composable
private fun PremiumStatsMini(
    label: String,
    value: Int,
    accent: Color,
    background: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(vertical = 12.dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp
            ),
            color = accent,
            maxLines = 1
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 9.sp
            ),
            color = Color(0xFF53627D),
            maxLines = 1
        )
    }
}
