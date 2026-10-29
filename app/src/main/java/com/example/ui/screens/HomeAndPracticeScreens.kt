package com.example.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.ui.theme.*
import com.example.viewmodel.MainNavTab
import com.example.viewmodel.PoolAvailabilityCheck
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun FloatingCircularBottomNav(
    selectedTab: MainNavTab,
    onSelectTab: (MainNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = 10.dp,
                    ambientColor = Color.Black.copy(alpha = 0.10f),
                    spotColor = Color.Black.copy(alpha = 0.10f)
                ),
            color = Color(0xFFF6F7FB),
            tonalElevation = 2.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(width = 0.8.dp, color = Color(0xFFE2E6F0))
            )
        }

        Row(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            MainNavTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                val interactionSource = remember { MutableInteractionSource() }
                val activeScale by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.90f,
                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f),
                    label = "nav_tab_scale"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onSelectTab(tab) }
                        .testTag("nav_tab_${tab.name.lowercase()}"),
                    contentAlignment = if (isSelected) Alignment.TopCenter else Alignment.BottomCenter
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(62.dp)
                                .graphicsLayer {
                                    scaleX = activeScale
                                    scaleY = activeScale
                                }
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF1F42B5), Color(0xFF183492))
                                    )
                                )
                                .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            BottomNavCustomIcon(
                                tab = tab,
                                isSelected = true
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(bottom = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            BottomNavCustomIcon(
                                tab = tab,
                                isSelected = false
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                color = Color(0xFF7C8698),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavCustomIcon(
    tab: MainNavTab,
    isSelected: Boolean
) {
    val activeColor = Color.White
    val inactiveColor = Color(0xFFB4BCC8)

    Canvas(
        modifier = Modifier.size(if (isSelected) 28.dp else 25.dp)
    ) {
        val w = size.width
        val h = size.height
        val tint = if (isSelected) activeColor else inactiveColor

        when (tab) {
            MainNavTab.ANA_SAYFA -> {
                val housePath = Path().apply {
                    moveTo(w * 0.50f, h * 0.10f)
                    lineTo(w * 0.90f, h * 0.42f)
                    lineTo(w * 0.82f, h * 0.88f)
                    lineTo(w * 0.18f, h * 0.88f)
                    lineTo(w * 0.10f, h * 0.42f)
                    close()
                }
                drawPath(
                    path = housePath,
                    color = tint,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = housePath,
                    color = tint
                )
                val cutoutColor = if (isSelected) Color(0xFF1B3A9E) else Color(0xFFF6F7FB)
                drawArc(
                    color = cutoutColor,
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(w * 0.34f, h * 0.52f),
                    size = Size(w * 0.32f, h * 0.22f),
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            MainNavTab.SORU_PRATIGI -> {
                val nibCutout = if (isSelected) Color(0xFF1B3A9E) else Color(0xFFF6F7FB)
                val penPath = Path().apply {
                    moveTo(w * 0.50f, h * 0.10f)
                    lineTo(w * 0.72f, h * 0.38f)
                    lineTo(w * 0.72f, h * 0.86f)
                    lineTo(w * 0.28f, h * 0.86f)
                    lineTo(w * 0.28f, h * 0.38f)
                    close()
                }
                drawPath(path = penPath, color = tint)
                drawLine(
                    color = nibCutout,
                    start = Offset(w * 0.26f, h * 0.40f),
                    end = Offset(w * 0.74f, h * 0.40f),
                    strokeWidth = 2.2.dp.toPx()
                )
                drawLine(
                    color = nibCutout,
                    start = Offset(w * 0.50f, h * 0.40f),
                    end = Offset(w * 0.50f, h * 0.86f),
                    strokeWidth = 2.2.dp.toPx()
                )
            }

            MainNavTab.DENEMELER -> {
                // Clean Exam Booklet / Optical Form Sheet icon for "Denemeler"
                val cutout = if (isSelected) Color(0xFF1B3A9E) else Color(0xFFF6F7FB)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.18f, h * 0.12f),
                    size = Size(w * 0.64f, h * 0.76f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                // Optical form lines & bubbles inside the exam sheet
                drawLine(
                    color = cutout,
                    start = Offset(w * 0.30f, h * 0.32f),
                    end = Offset(w * 0.70f, h * 0.32f),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = cutout,
                    start = Offset(w * 0.30f, h * 0.50f),
                    end = Offset(w * 0.70f, h * 0.50f),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = cutout,
                    start = Offset(w * 0.30f, h * 0.68f),
                    end = Offset(w * 0.56f, h * 0.68f),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            MainNavTab.ISTATISTIKLERIM -> {
                drawArc(
                    color = tint,
                    startAngle = 15f,
                    sweepAngle = 255f,
                    useCenter = true,
                    topLeft = Offset(w * 0.12f, h * 0.14f),
                    size = Size(w * 0.72f, h * 0.72f)
                )
                drawArc(
                    color = tint,
                    startAngle = -85f,
                    sweepAngle = 85f,
                    useCenter = true,
                    topLeft = Offset(w * 0.20f, h * 0.08f),
                    size = Size(w * 0.72f, h * 0.72f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnaSayfaScreen(
    userName: String,
    userEmail: String = "",
    allTestResults: List<TestResultRecord> = emptyList(),
    recentResult: TestResultRecord?,
    totalSolvedTests: Int,
    customQuestionsCount: Int,
    onInspectRecentResult: (TestResultRecord) -> Unit,
    onStartDefaultTest: () -> Unit,
    onNavigateTab: (MainNavTab) -> Unit,
    onOpenPdfCropper: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onSignOut: () -> Unit,
    onResetAllUserData: () -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.forLanguageTag("tr-TR")) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var initialProfileTab by remember { mutableIntStateOf(0) }

    val totalCorrectCount = remember(allTestResults) {
        allTestResults.sumOf { it.correctCount }
    }
    val totalSolvedQuestions = remember(allTestResults) {
        allTestResults.sumOf { it.correctCount + it.wrongCount + it.emptyCount }
    }

    val initials = remember(userName) {
        val parts = userName.trim().split(" ").filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
            parts.size == 1 -> parts.first().take(2).uppercase()
            else -> "ÖĞ"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEDF2F8))
            .testTag("ana_sayfa_list"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Executive Bar: Left Brand Identity + Right [Student Name on Left + Profile Photo on Right]
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0F2557), Color(0xFF1D4ED8))
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.School,
                            contentDescription = "SoruLab",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "SoruLab",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            ),
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "AKILLI YKS LABORATUVARI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.7.sp
                            ),
                            color = Color(0xFF475569)
                        )
                    }
                }

                // Top-Right Student Name (on left) + Profile Photo Avatar (on right)
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            initialProfileTab = 0
                            showProfileSheet = true
                        }
                        .testTag("profile_header_button"),
                    shape = RoundedCornerShape(50),
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .border(1.2.dp, Color(0xFFD5E0F0), RoundedCornerShape(50))
                            .padding(start = 14.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.5.sp
                                ),
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Profil • Soru Havuzu",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.2.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color(0xFF1D4ED8)
                            )
                        }

                        // Profile Photo Avatar with Active Status Ring
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF1D4ED8), Color(0xFF1E3A8A))
                                        )
                                    )
                                    .border(2.dp, Color(0xFFDBEAFE), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp
                                    ),
                                    color = Color.White
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF16A34A))
                                    .border(1.8.dp, Color.White, CircleShape)
                            )
                        }
                    }
                }
            }
        }

        // 2. Premium "Hoş Geldiniz" Architectural Hero Banner (No Countdown Timer)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B132B))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0A1128),
                                    Color(0xFF17317E),
                                    Color(0xFF1E40AF)
                                )
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(26.dp))
                        .drawWithCache {
                            val ringColor = Color.White.copy(alpha = 0.06f)
                            val accentGlow = Color(0xFF38BDF8).copy(alpha = 0.12f)
                            onDrawBehind {
                                drawCircle(
                                    color = accentGlow,
                                    radius = size.width * 0.34f,
                                    center = Offset(size.width * 0.90f, size.height * 0.20f)
                                )
                                drawCircle(
                                    color = ringColor,
                                    radius = size.width * 0.26f,
                                    center = Offset(size.width * 0.88f, size.height * 0.22f),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawCircle(
                                    color = ringColor,
                                    radius = size.width * 0.16f,
                                    center = Offset(size.width * 0.88f, size.height * 0.22f),
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                            }
                        }
                        .padding(22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Hoş geldiniz, $userName",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp
                            ),
                            color = Color.White
                        )

                        // Real Live Status Strip inside Welcome Card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.09f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HeroMetricColumn(value = "$totalSolvedTests", label = "Çözülen Test")
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(26.dp)
                                    .background(Color.White.copy(alpha = 0.16f))
                            )
                            HeroMetricColumn(value = "$totalSolvedQuestions", label = "Çözülen Soru")
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(26.dp)
                                    .background(Color.White.copy(alpha = 0.16f))
                            )
                            HeroMetricColumn(value = "$customQuestionsCount", label = "Havuz Sorusu")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onNavigateTab(MainNavTab.SORU_PRATIGI) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF0F2557)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("hero_soru_pratigi_button")
                            ) {
                                Text(
                                    text = "Soru Pratiğine Geç",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            OutlinedButton(
                                onClick = { onNavigateTab(MainNavTab.DENEMELER) },
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color.White.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .height(46.dp)
                                    .testTag("hero_denemeler_button")
                            ) {
                                Text(
                                    text = "Denemeler",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Premium "Son Çözülen Test" Section (100% Real Data — 0 Hallucination Policy)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1D4ED8))
                        )
                        Text(
                            text = "Son Çözülen Test",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            ),
                            color = Color(0xFF0F172A)
                        )
                    }

                    Text(
                        text = if (totalSolvedTests > 0) "Toplam $totalSolvedTests Kayıt" else "Henüz Kayıt Yok",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1D4ED8)
                    )
                }

                if (recentResult != null) {
                    val recentDateStr = remember(recentResult) {
                        val millis = recentResult.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                        dateFormat.format(Date(millis))
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 10.dp,
                                shape = RoundedCornerShape(24.dp),
                                ambientColor = Color(0xFF1D4ED8).copy(alpha = 0.14f)
                            ),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.2.dp, Color(0xFFD5E0F0), RoundedCornerShape(24.dp))
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color(0xFF1D4ED8), Color(0xFF1E3A8A))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Surface(
                                            color = Color(0xFFEFF6FF),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "${recentResult.examType} • ${recentResult.subjectName} • %${recentResult.accuracyPercent} Başarı",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFF1D4ED8),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = recentResult.testTitle,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = Color(0xFF0F172A),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = recentDateStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onInspectRecentResult(recentResult) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1D4ED8),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                    modifier = Modifier.testTag("sonucu_incele_button")
                                ) {
                                    Text(
                                        text = "Sonucu İncele",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFFE2E8F0))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatMiniPill(label = "Doğru", value = "${recentResult.correctCount}", color = SoruLabEmerald)
                                    StatMiniPill(label = "Yanlış", value = "${recentResult.wrongCount}", color = SoruLabCoralRed)
                                    StatMiniPill(label = "Boş", value = "${recentResult.emptyCount}", color = SoruLabSlateMuted)
                                    StatMiniPill(
                                        label = "Net",
                                        value = String.format(Locale.US, "%.2f", recentResult.netScore),
                                        color = SoruLabCobaltBlue
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Honest Premium Card when no test has been solved yet
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(24.dp),
                                ambientColor = Color(0xFF0F172A).copy(alpha = 0.10f)
                            ),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.2.dp, Color(0xFFD5E0F0), RoundedCornerShape(24.dp))
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF1E293B), Color(0xFF334155))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayCircleOutline,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Henüz Test Kaydı Yok",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = Color(0xFF0F172A)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Button(
                                onClick = onStartDefaultTest,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1D4ED8),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier.testTag("siradaki_odev_button")
                            ) {
                                Text(
                                    text = "Teste Başla",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Concise "Hızlı Erişim" Grid (No PDF Cropper on Home, No Verbose Text)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E3A8A))
                        )
                        Text(
                            text = "Hızlı Erişim",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            ),
                            color = Color(0xFF0F172A)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExecutiveQuickBentoCard(
                        title = "Soru Pratiği",
                        subtitle = "TYT & AYT Konular",
                        badgeText = "Tüm Dersler",
                        gradientColors = listOf(Color(0xFF1E3A8A), Color(0xFF2563EB)),
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        onClick = { onNavigateTab(MainNavTab.SORU_PRATIGI) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shortcut_soru_pratigi")
                    )
                    ExecutiveQuickBentoCard(
                        title = "Denemeler",
                        subtitle = "Genel Deneme Sınavları",
                        badgeText = "TYT • AYT",
                        gradientColors = listOf(Color(0xFF4C1D95), Color(0xFF7C3AED)),
                        icon = Icons.AutoMirrored.Filled.Assignment,
                        onClick = { onNavigateTab(MainNavTab.DENEMELER) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shortcut_denemeler")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExecutiveQuickBentoCard(
                        title = "SoruLab AI",
                        subtitle = "Canlı Çözüm Asistanı",
                        badgeText = "AI Sohbet",
                        gradientColors = listOf(Color(0xFF065F46), Color(0xFF10B981)),
                        icon = Icons.Filled.AutoAwesome,
                        onClick = onOpenAiAssistant,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shortcut_ai_solver")
                    )
                    ExecutiveQuickBentoCard(
                        title = "İstatistiklerim",
                        subtitle = "$totalSolvedTests Test • $totalCorrectCount Doğru",
                        badgeText = "Analiz",
                        gradientColors = listOf(Color(0xFF0F172A), Color(0xFF1E40AF)),
                        icon = Icons.Filled.Insights,
                        onClick = { onNavigateTab(MainNavTab.ISTATISTIKLERIM) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shortcut_istatistiklerim")
                    )
                }
            }
        }
    }

    // Modern Sliding Bottom Sheet for Profil / Soru Havuzu / Başarılarım / Ayarlar
    if (showProfileSheet) {
        ProfileCenterBottomSheet(
            userName = userName,
            userEmail = userEmail,
            initials = initials,
            initialTab = initialProfileTab,
            totalSolvedTests = totalSolvedTests,
            totalSolvedQuestions = totalSolvedQuestions,
            totalCorrectCount = totalCorrectCount,
            customQuestionsCount = customQuestionsCount,
            allTestResults = allTestResults,
            onOpenSoruHavuzu = {
                showProfileSheet = false
                onOpenPdfCropper()
            },
            onResetAllUserData = {
                showProfileSheet = false
                onResetAllUserData()
            },
            onDismiss = { showProfileSheet = false },
            onSignOut = {
                showProfileSheet = false
                onSignOut()
            }
        )
    }
}

@Composable
private fun HeroMetricColumn(
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            ),
            color = Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f)
        )
    }
}

@Composable
private fun ExecutiveQuickBentoCard(
    title: String,
    subtitle: String,
    badgeText: String,
    gradientColors: List<Color>,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryAccent = gradientColors.last()
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.2.dp, Color(0xFFD5E0F0), RoundedCornerShape(22.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Surface(
                    color = primaryAccent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.5.sp
                        ),
                        color = gradientColors.first(),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                ),
                color = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    lineHeight = 16.5.sp
                ),
                color = Color(0xFF475569),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Aç",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = primaryAccent
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = primaryAccent,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileCenterBottomSheet(
    userName: String,
    userEmail: String,
    initials: String,
    initialTab: Int,
    totalSolvedTests: Int,
    totalSolvedQuestions: Int,
    totalCorrectCount: Int,
    customQuestionsCount: Int,
    allTestResults: List<TestResultRecord>,
    onOpenSoruHavuzu: () -> Unit,
    onResetAllUserData: () -> Unit = {},
    onDismiss: () -> Unit,
    onSignOut: () -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(initialTab) }
    var selectedTrack by remember { mutableStateOf("Sayısal (MF)") }
    var defaultFullPageMode by remember { mutableStateOf(true) }
    var autoOpenResultDrawer by remember { mutableStateOf(true) }
    var smartShapeEnabled by remember { mutableStateOf(true) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Student Identity Header inside Sheet
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF1D4ED8), Color(0xFF1E3A8A))
                                )
                            )
                            .border(2.dp, Color(0xFFBFDBFE), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = userEmail.ifBlank { "Bulut Oturumu Aktif" },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Kapat",
                        tint = Color(0xFF475569)
                    )
                }
            }

            // 4-Tab Switcher: Profil | Soru Havuzu | Başarılarım | Ayarlar
            val tabs = listOf(
                Triple("Profil", Icons.Filled.Person, 0),
                Triple("Soru Havuzu", Icons.Filled.AutoStories, 3),
                Triple("Başarılarım", Icons.Filled.EmojiEvents, 1),
                Triple("Ayarlar", Icons.Filled.Settings, 2)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF1F5F9))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEach { (label, icon, idx) ->
                    val active = selectedTabIndex == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) Color(0xFF1D4ED8) else Color.Transparent)
                            .clickable {
                                if (idx == 3) {
                                    onOpenSoruHavuzu()
                                } else {
                                    selectedTabIndex = idx
                                }
                            }
                            .testTag("profile_sheet_tab_$idx"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (active) Color.White else Color(0xFF475569),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                ),
                                color = if (active) Color.White else Color(0xFF475569),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            when (selectedTabIndex) {
                0 -> {
                    // PROFİL SEKME İÇERİĞİ
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Direct Launch Card for Soru Havuzu & PDF AI Studio inside Profile
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenSoruHavuzu() }
                                .testTag("profile_open_soru_havuzu_card"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF0F2557), Color(0xFF1D4ED8))
                                        )
                                    )
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoStories,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Soru Havuzu",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                            color = Color.White
                                        )
                                        Text(
                                            text = "PDF yükle, otomatik kırp ve yayın logosuyla havuza ekle ($customQuestionsCount soru)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Öğrenci Hesap Özeti",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF0F172A)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    ProfileStatChip("Çözülen Test", "$totalSolvedTests")
                                    ProfileStatChip("Toplam Soru", "$totalSolvedQuestions")
                                    ProfileStatChip("Toplam Doğru", "$totalCorrectCount")
                                    ProfileStatChip("Soru Havuzu", "$customQuestionsCount")
                                }
                            }
                        }

                        Text(
                            text = "Hedef YKS Alanı",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0F172A)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Sayısal (MF)", "Eşit Ağırlık (TM)", "Sözel (TS)").forEach { track ->
                                val isSel = selectedTrack == track
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedTrack = track },
                                    label = { Text(track) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedButton(
                            onClick = onResetAllUserData,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF475569)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("reset_all_data_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = null,
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tüm Test & Havuz Verilerini Sıfırla",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF0F172A)
                            )
                        }

                        OutlinedButton(
                            onClick = onSignOut,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, SoruLabCoralRed),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = null,
                                tint = SoruLabCoralRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Oturumu Kapat",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = SoruLabCoralRed
                            )
                        }
                    }
                }

                1 -> {
                    // BAŞARILARIM SEKME İÇERİĞİ (100% Gerçek Veriye Dayalı Rozetler)
                    val highAccuracyUnlocked = allTestResults.any { it.accuracyPercent >= 80 }
                    val badges = listOf(
                        AchievementBadgeItem(
                            title = "İlk Adım",
                            description = "İlk gerçek konu testini veya denemeni tamamla ($totalSolvedTests/1)",
                            isUnlocked = totalSolvedTests >= 1,
                            color = Color(0xFF1D4ED8)
                        ),
                        AchievementBadgeItem(
                            title = "Soru Havuzu Mimarı",
                            description = "Soru Havuzu'na kendi kaynağından soru ekle ($customQuestionsCount/1)",
                            isUnlocked = customQuestionsCount >= 1,
                            color = Color(0xFF0D9488)
                        ),
                        AchievementBadgeItem(
                            title = "Keskin Nişancı",
                            description = "Bir testte %80 ve üzeri doğruluk oranına ulaş",
                            isUnlocked = highAccuracyUnlocked,
                            color = Color(0xFF16A34A)
                        ),
                        AchievementBadgeItem(
                            title = "Kamp Disiplini",
                            description = "Toplam 5 farklı testi tamamla ($totalSolvedTests/5)",
                            isUnlocked = totalSolvedTests >= 5,
                            color = Color(0xFF7C3AED)
                        ),
                        AchievementBadgeItem(
                            title = "Derece Adayı",
                            description = "Toplam 50 doğru soruya ulaş ($totalCorrectCount/50)",
                            isUnlocked = totalCorrectCount >= 50,
                            color = Color(0xFFD97706)
                        )
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        badges.forEach { badge ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (badge.isUnlocked) badge.color.copy(alpha = 0.08f) else Color(0xFFF8FAFC)
                                    )
                                    .border(
                                        1.dp,
                                        if (badge.isUnlocked) badge.color.copy(alpha = 0.35f) else Color(0xFFE2E8F0),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (badge.isUnlocked) badge.color else Color(0xFFCBD5E1)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (badge.isUnlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = badge.title,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = badge.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF475569)
                                        )
                                    }
                                }

                                Surface(
                                    color = if (badge.isUnlocked) Color(0xFFDCFCE7) else Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = if (badge.isUnlocked) "Kazanıldı" else "Kilitli",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (badge.isUnlocked) Color(0xFF15803D) else Color(0xFF64748B),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // AYARLAR SEKME İÇERİĞİ
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsToggleItem(
                            title = "Tam Sayfa Yaprak Test Görünümü",
                            subtitle = "Testleri varsayılan olarak çift sütunlu tam sayfa tahta modunda başlat",
                            checked = defaultFullPageMode,
                            onCheckedChange = { defaultFullPageMode = it }
                        )
                        SettingsToggleItem(
                            title = "Akıllı Geometrik Şekil Düzeltme",
                            subtitle = "Çözüm tahtasında çizilen doğru ve dikdörtgenleri otomatik hizala",
                            checked = smartShapeEnabled,
                            onCheckedChange = { smartShapeEnabled = it }
                        )
                        SettingsToggleItem(
                            title = "Test Sonunda Otomatik Sonuç Çekmecesi",
                            subtitle = "Testi bitirince doğru/yanlış analiz çekmecesini otomatik aç",
                            checked = autoOpenResultDrawer,
                            onCheckedChange = { autoOpenResultDrawer = it }
                        )
                    }
                }
            }
        }
    }
}

private data class AchievementBadgeItem(
    val title: String,
    val description: String,
    val isUnlocked: Boolean,
    val color: Color
)

@Composable
private fun ProfileStatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = Color(0xFF1D4ED8)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF64748B)
        )
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF0F172A)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun StatMiniPill(label: String, value: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = SoruLabSlateMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = SoruLabInkBlack
        )
    }
}

@Composable
fun SoruPratigiScreen(
    selectedExamType: ExamType,
    selectedTopic: YksTopic?,
    selectedTestLevel: TopicTestLevel,
    currentLevelTests: List<YksTestItem>,
    dialogTestPair: Pair<YksTopic, YksTestItem>?,
    isTestCompleted: (YksTestItem) -> Boolean,
    onCheckPoolAvailability: (YksTopic, TopicTestLevel) -> PoolAvailabilityCheck,
    onSelectExamType: (ExamType) -> Unit,
    onSelectTopic: (YksTopic) -> Unit,
    onCloseTopic: () -> Unit,
    onSelectTestLevel: (TopicTestLevel) -> Unit,
    onGenerateAiLevelTest: (YksTopic, TopicTestLevel) -> YksTestItem?,
    onTestClicked: (YksTopic, YksTestItem) -> Unit,
    onDismissDialog: () -> Unit,
    onStartTestAction: (YksTopic, YksTestItem, Boolean, Boolean) -> Unit,
    onDeleteTest: (YksTopic, YksTestItem) -> Unit = { _, _ -> },
    onOpenAiAssistant: () -> Unit,
    onOpenPdfCropper: () -> Unit = {}
) {
    if (selectedTopic != null) {
        BackHandler { onCloseTopic() }
        TopicTestsDetailView(
            topic = selectedTopic,
            selectedTestLevel = selectedTestLevel,
            currentLevelTests = currentLevelTests,
            isTestCompleted = isTestCompleted,
            onCheckPoolAvailability = { onCheckPoolAvailability(selectedTopic, selectedTestLevel) },
            onBack = onCloseTopic,
            onSelectTestLevel = onSelectTestLevel,
            onGenerateAiLevelTest = { onGenerateAiLevelTest(selectedTopic, selectedTestLevel) },
            onTestClicked = { test -> onTestClicked(selectedTopic, test) },
            onStartTestImmediately = { test ->
                onStartTestAction(selectedTopic, test, false, false)
            },
            onDeleteTest = { test ->
                onDeleteTest(selectedTopic, test)
            },
            onOpenAiAssistant = onOpenAiAssistant,
            onOpenPdfCropper = onOpenPdfCropper
        )
    } else {
        val subjects = remember(selectedExamType) {
            SoruLabCatalog.allSubjects.filter { it.examType == selectedExamType }
        }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFEAEEF5))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEAEEF5))
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF1D63DC), Color(0xFF1E3A8A))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = "Soru Pratiği",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Soru Pratiği",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = Color(0xFF0F172A)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Centered Pill TYT / AYT Switcher
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .width(300.dp)
                                .height(42.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFD8DFEB))
                                .padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExamType.entries.forEach { type ->
                                val active = selectedExamType == type
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (active) Color(0xFF1D63DC) else Color.Transparent
                                        )
                                        .clickable { onSelectExamType(type) }
                                        .testTag("exam_toggle_${type.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = type.label,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (active) Color.White else Color(0xFF475569)
                                    )
                                }
                            }
                        }
                    }
                }

                // Subject rows: Subject name on left + full-width colored line + horizontal LazyRow of all topics
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("soru_pratigi_subjects_list"),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 128.dp),
                    verticalArrangement = Arrangement.spacedBy(26.dp)
                ) {
                    items(
                        items = subjects,
                        key = { it.id },
                        contentType = { "subject_row" }
                    ) { subject ->
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 22.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(subject.accentColorHex))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = subject.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 17.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(2.4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(subject.accentColorHex))
                                )
                            }

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 22.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(
                                    items = subject.topics,
                                    key = { it.id },
                                    contentType = { "topic_card" }
                                ) { topic ->
                                    TopicLandscapeCard(
                                        topic = topic,
                                        onClick = { onSelectTopic(topic) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }

    if (dialogTestPair != null) {
        val (topic, testItem) = dialogTestPair
        val isSolved = isTestCompleted(testItem)
        Dialog(onDismissRequest = onDismissDialog) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFBFD)),
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        SubjectFormulaBadge(
                            iconType = topic.iconType,
                            badgeColor = Color(topic.badgeColorHex)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = topic.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFF334155),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = testItem.fullTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF475569),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val difficultyStars = when (testItem.difficulty) {
                        DifficultyLevel.KOLAY -> 1
                        DifficultyLevel.ORTA -> 2
                        DifficultyLevel.OSYM -> 3
                        DifficultyLevel.ZOR -> 4
                        DifficultyLevel.COK_ZOR -> 5
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(5) { idx ->
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = if (idx < difficultyStars) Color(0xFF1D72E8) else Color(0xFFCBD5E1),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        color = Color(testItem.difficulty.colorHex).copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "${testItem.questions.size} Soru • Zorluk: ${testItem.difficulty.label} ($difficultyStars/5 ★)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color(0xFF1E293B),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }

                    if (isSolved) {
                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                onStartTestAction(topic, testItem, false, false)
                            },
                            modifier = Modifier
                                .width(220.dp)
                                .height(44.dp)
                                .testTag("dialog_tekrar_coz"),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1D72E8),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tekrar Çöz",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                onStartTestAction(topic, testItem, true, false)
                            },
                            modifier = Modifier
                                .width(240.dp)
                                .height(44.dp)
                                .testTag("dialog_sonuclari_goruntule"),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1D72E8),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BarChart,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sonucu Göster",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                onStartTestAction(topic, testItem, false, true)
                            },
                            modifier = Modifier
                                .width(220.dp)
                                .height(44.dp)
                                .testTag("dialog_optik_forma_gir"),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1D72E8),
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = "Optik Forma Gir",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                onStartTestAction(topic, testItem, false, false)
                            },
                            modifier = Modifier
                                .width(220.dp)
                                .height(46.dp)
                                .testTag("dialog_teste_basla"),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1D72E8),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Teste Başla",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                onStartTestAction(topic, testItem, false, true)
                            },
                            modifier = Modifier
                                .width(220.dp)
                                .height(44.dp)
                                .testTag("dialog_optik_forma_gir"),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF334155),
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = "Optik Forma Gir",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectFormulaBadge(
    iconType: String,
    badgeColor: Color
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(badgeColor),
        contentAlignment = Alignment.Center
    ) {
        val symbolText = when (iconType) {
            "math" -> "√x"
            "angle" -> "△"
            "vector" -> "∿"
            "flask" -> "⚛"
            "bio" -> "🧬"
            else -> "|A|"
        }
        Text(
            text = symbolText,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.5.sp
            ),
            color = Color.White
        )
    }
}

@Composable
private fun TopicLandscapeCard(
    topic: YksTopic,
    onClick: () -> Unit
) {
    val accentColor = Color(topic.badgeColorHex)
    // Ultra-lightweight topic card for 60 FPS scrolling on tablet preview (no shadow or heavy overdraw)
    Box(
        modifier = Modifier
            .width(220.dp)
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.4.dp, accentColor.copy(alpha = 0.38f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .drawWithCache {
                onDrawBehind {
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset(0f, 0f),
                        size = Size(5.dp.toPx(), size.height),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
            .testTag("topic_card_${topic.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = topic.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 20.sp
                ),
                color = Color(0xFF0F172A),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun TopicTestsDetailView(
    topic: YksTopic,
    selectedTestLevel: TopicTestLevel,
    currentLevelTests: List<YksTestItem>,
    isTestCompleted: (YksTestItem) -> Boolean,
    onCheckPoolAvailability: () -> PoolAvailabilityCheck,
    onBack: () -> Unit,
    onSelectTestLevel: (TopicTestLevel) -> Unit,
    onGenerateAiLevelTest: () -> YksTestItem?,
    onTestClicked: (YksTestItem) -> Unit,
    onStartTestImmediately: (YksTestItem) -> Unit,
    onDeleteTest: (YksTestItem) -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenPdfCropper: () -> Unit
) {
    // Automatically open the proportional semicircle wheel on the left when entering any topic
    var isWheelExpanded by remember(topic.id) { mutableStateOf(true) }
    var showAiGeneratorModal by remember { mutableStateOf(false) }

    val wheelProgress by animateFloatAsState(
        targetValue = if (isWheelExpanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = 140,
            easing = FastOutSlowInEasing
        ),
        label = "wheel_dynamic_spring_anim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEAEEF4))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: Back + SubjectFormulaBadge + Topic Name ONLY (no redundant level badge on top-right!) + "Oluştur" button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("topic_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color(0xFF475569)
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    SubjectFormulaBadge(
                        iconType = topic.iconType,
                        badgeColor = Color(topic.badgeColorHex)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = topic.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Top-Right "Test / Soru Üret" Button
                Button(
                    onClick = { showAiGeneratorModal = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1D63DC),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("topic_detail_olustur_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Test / Soru Üret",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // Main rounded white card container with left two-tone vertical accent strip & proportional semicircle wheel
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.4.dp, Color(0xFFCFD8E6), RoundedCornerShape(20.dp))
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left vertical two-tone indicator bar + Interactive Pull Tab
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .fillMaxHeight()
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures { _, dragAmount ->
                                        if (dragAmount > 6f && !isWheelExpanded) {
                                            isWheelExpanded = true
                                        } else if (dragAmount < -6f && isWheelExpanded) {
                                            isWheelExpanded = false
                                        }
                                    }
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { isWheelExpanded = !isWheelExpanded }
                                .testTag("left_level_wheel_strip"),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(7.dp)
                                    .fillMaxHeight()
                                    .padding(vertical = 24.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(105.dp)
                                        .clip(RoundedCornerShape(topEnd = 4.dp))
                                        .background(Color(0xFFD98A16))
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(105.dp)
                                        .clip(RoundedCornerShape(bottomEnd = 4.dp))
                                        .background(Color(0xFF0E8AC8))
                                )
                            }

                            if (wheelProgress < 0.35f) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 5.dp)
                                        .width(20.dp)
                                        .height(50.dp)
                                        .clip(RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp))
                                        .background(Color(selectedTestLevel.wedgeColorHex))
                                        .border(
                                            width = 1.2.dp,
                                            color = Color.White,
                                            shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Seviye Çarkı",
                                        tint = Color(selectedTestLevel.textColorHex),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (currentLevelTests.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp, vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0xFFEFF6FF))
                                        .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(18.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                        contentDescription = null,
                                        tint = Color(0xFF1D63DC),
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "${topic.name} • ${selectedTestLevel.displayTitle}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    ),
                                    color = Color(0xFF0F172A),
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = if (selectedTestLevel == TopicTestLevel.IKIZ) {
                                        "Bu seviyede henüz oluşturulmuş bir İkiz test yok. Sağ üstteki 'Test / Soru Üret' butonuna bastığında bu konuda daha önce yanlış yaptığın veya boş bıraktığın gerçek sorular kontrol edilerek İkiz Test oluşturulur."
                                    } else {
                                        "Bu seviyede henüz test oluşturulmadı (${selectedTestLevel.poolDistributionLabel}). Sağ üstteki 'Test / Soru Üret' butonuna basarak soru havuzundaki gerçek sorularla test oluşturabilirsin."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF475569),
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = { showAiGeneratorModal = true },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1D63DC),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("empty_state_olustur_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Test / Soru Üret")
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("topic_tests_list"),
                                contentPadding = PaddingValues(start = 6.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                items(
                                    items = currentLevelTests,
                                    key = { it.id },
                                    contentType = { "test_row" }
                                ) { testItem ->
                                    val solved = isTestCompleted(testItem)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onTestClicked(testItem) }
                                            .padding(vertical = 8.dp, horizontal = 4.dp)
                                            .testTag("test_row_${testItem.id}"),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (solved) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF16A34A)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = "Çözüldü",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier.size(32.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                                        contentDescription = "Çözülmedi",
                                                        tint = Color(0xFF94A3B8),
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Filled.Edit,
                                                        contentDescription = null,
                                                        tint = Color(0xFF64748B),
                                                        modifier = Modifier
                                                            .size(12.dp)
                                                            .align(Alignment.BottomEnd)
                                                    )
                                                }
                                            }

                                            Text(
                                                text = testItem.fullTitle,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontSize = 15.2.sp,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                color = Color(0xFF0F172A),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            DifficultyMeterBar(position = testItem.difficultyPosition)

                                            // Şık Kırmızı/Gri Çöp Kutusu (Sil) İkonu - "Çöz" butonunun hemen SOLUNDA
                                            Surface(
                                                onClick = { onDeleteTest(testItem) },
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFFFEF2F2),
                                                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .testTag("delete_test_btn_${testItem.id}")
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Delete,
                                                        contentDescription = "Testi Sil",
                                                        tint = Color(0xFFDC2626),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            Surface(
                                                onClick = { onStartTestImmediately(testItem) },
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF1E3A8A),
                                                modifier = Modifier.testTag("solve_test_btn_${testItem.id}")
                                            ) {
                                                Text(
                                                    text = "Çöz",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Proportional Semicircle (180°) Radial Level Wheel ("Çark") Overlay
                    if (wheelProgress > 0.01f) {
                        RadialLevelWheelOverlay(
                            selectedLevel = selectedTestLevel,
                            animProgress = wheelProgress,
                            onSelectLevel = { level ->
                                onSelectTestLevel(level)
                                isWheelExpanded = false
                            },
                            onDismiss = { isWheelExpanded = false }
                        )
                    }
                }
            }
        }

        // Bottom-right circular AI Assistant Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 26.dp, bottom = 26.dp)
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))
                    )
                )
                .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                .clickable { onOpenAiAssistant() }
                .testTag("topic_ai_assistant_fab"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "SoruLab AI",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        // Strict 0-Hallucination Test Generator Dialog (Never recomposes poolCheck mid-generation!)
        if (showAiGeneratorModal) {
            val poolCheck = remember(topic.id, selectedTestLevel, showAiGeneratorModal) {
                onCheckPoolAvailability()
            }
            val nextTestNo = remember(topic.id, selectedTestLevel, showAiGeneratorModal) {
                currentLevelTests.size + 1
            }
            SoruLabAiTestGeneratorDialog(
                topic = topic,
                level = selectedTestLevel,
                nextTestNumber = nextTestNo,
                poolCheck = poolCheck,
                onGenerateFinished = {
                    onGenerateAiLevelTest()
                },
                onDismiss = {
                    showAiGeneratorModal = false
                },
                onStartGeneratedTest = { createdTest ->
                    showAiGeneratorModal = false
                    onStartTestImmediately(createdTest)
                },
                onGoToPdfStudio = {
                    showAiGeneratorModal = false
                    onOpenPdfCropper()
                }
            )
        }
    }
}

/**
 * Proportional Semicircle (`180°`) Radial Level Wheel ("Çark")
 * - True 180-degree semicircle (`startAngleDeg = -90f`, `totalSweepDeg = 180f`)
 * - Compact, balanced size (`baseInnerRadius = 134.dp`, `outerRingOuter = 146.dp`)
 * - Outer frame ONLY (no "HATA" or "TEST" text on the outer ring!)
 */
@Composable
private fun RadialLevelWheelOverlay(
    selectedLevel: TopicTestLevel,
    animProgress: Float,
    onSelectLevel: (TopicTestLevel) -> Unit,
    onDismiss: () -> Unit
) {
    val levels = TopicTestLevel.entries
    val startAngleDeg = -90f
    val totalSweepDeg = 180f
    val sliceSweepDeg = totalSweepDeg / levels.size.toFloat()
    val density = LocalDensity.current

    val textPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = with(density) { 10.8.sp.toPx() }
            letterSpacing = 0.03f
        }
    }

    val safeAlpha = animProgress.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.06f * safeAlpha))
            .testTag("radial_level_wheel_overlay")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(levels) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (dragAmount < -10f) {
                            onDismiss()
                        }
                    }
                }
                .pointerInput(levels) {
                    detectTapGestures { tapOffset ->
                        val centerX = 0f
                        val centerY = size.height * 0.50f
                        val dx = tapOffset.x - centerX
                        val dy = tapOffset.y - centerY
                        val dist = hypot(dx, dy)
                        val maxTapRadius = 156.dp.toPx()

                        if (dx >= 0f && dist in 14.dp.toPx()..maxTapRadius) {
                            val angleRad = atan2(dy, dx)
                            val angleDeg = (angleRad * 180f / PI.toFloat())
                            if (angleDeg in startAngleDeg..(startAngleDeg + totalSweepDeg)) {
                                val idx = ((angleDeg - startAngleDeg) / sliceSweepDeg)
                                    .toInt()
                                    .coerceIn(0, levels.lastIndex)
                                onSelectLevel(levels[idx])
                            } else {
                                onDismiss()
                            }
                        } else {
                            onDismiss()
                        }
                    }
                }
        ) {
            val centerX = 0f
            val centerY = size.height * 0.50f

            // Compact, proportional semicircle scaling from the left edge
            val radialScale = (0.20f + 0.80f * animProgress).coerceAtLeast(0.05f)
            val baseInnerRadius = 132.dp.toPx() * radialScale
            val selectedBoostPx = 5.dp.toPx() * safeAlpha
            val outerRingInner = (136.dp.toPx() + 2.dp.toPx() * safeAlpha) * radialScale
            val outerRingOuter = (145.dp.toPx() + 2.dp.toPx() * safeAlpha) * radialScale

            // True 180-degree semicircle (-90° to +90°)
            val dynamicTotalSweep = totalSweepDeg
            val dynamicStartAngle = -90f
            val dynamicSliceSweep = dynamicTotalSweep / levels.size.toFloat() // 30° each

            // Crisp white semicircle backing rim
            val backingRadius = outerRingOuter + 4.dp.toPx() * radialScale
            drawArc(
                color = Color(0xFFF1F5F9).copy(alpha = safeAlpha),
                startAngle = -90f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(centerX - backingRadius, centerY - backingRadius),
                size = Size(backingRadius * 2f, backingRadius * 2f)
            )

            // 1. Draw the 6 equal 30° semicircle slices
            levels.forEachIndexed { index, level ->
                val sliceStart = dynamicStartAngle + index * dynamicSliceSweep
                val isSelected = level == selectedLevel
                val sliceRadius = if (isSelected) baseInnerRadius + selectedBoostPx else baseInnerRadius
                val sliceTopLeft = Offset(centerX - sliceRadius, centerY - sliceRadius)
                val sliceSize = Size(sliceRadius * 2f, sliceRadius * 2f)

                drawArc(
                    color = Color(level.wedgeColorHex).copy(alpha = safeAlpha),
                    startAngle = sliceStart,
                    sweepAngle = dynamicSliceSweep,
                    useCenter = true,
                    topLeft = sliceTopLeft,
                    size = sliceSize
                )

                if (isSelected) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.92f * safeAlpha),
                        startAngle = sliceStart + 0.8f,
                        sweepAngle = (dynamicSliceSweep - 1.6f).coerceAtLeast(1f),
                        useCenter = false,
                        topLeft = sliceTopLeft,
                        size = sliceSize,
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Radial white divider line
                val rad = sliceStart * (PI.toFloat() / 180f)
                drawLine(
                    color = Color(0xFFF1F5F9).copy(alpha = safeAlpha),
                    start = Offset(centerX, centerY),
                    end = Offset(
                        centerX + cos(rad) * sliceRadius,
                        centerY + sin(rad) * sliceRadius
                    ),
                    strokeWidth = 2.4.dp.toPx()
                )
            }

            // Center pivot hub on the left edge
            val hubRadius = 16.dp.toPx() * radialScale
            drawCircle(
                color = Color.White.copy(alpha = safeAlpha),
                radius = hubRadius,
                center = Offset(centerX, centerY)
            )
            drawCircle(
                color = Color(selectedLevel.wedgeColorHex).copy(alpha = safeAlpha),
                radius = hubRadius * 0.56f,
                center = Offset(centerX, centerY)
            )

            // 2. Clean two-tone outer border frame ONLY (no "HATA" or "TEST" text!)
            val ringRadiusMid = (outerRingInner + outerRingOuter) / 2f
            val ringThickness = (outerRingOuter - outerRingInner).coerceAtLeast(2f)
            val ringTopLeft = Offset(centerX - ringRadiusMid, centerY - ringRadiusMid)
            val ringSize = Size(ringRadiusMid * 2f, ringRadiusMid * 2f)

            // Top 90° outer border frame (Amber)
            drawArc(
                color = Color(0xFFD98A16).copy(alpha = safeAlpha),
                startAngle = -90f,
                sweepAngle = 89.2f,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = ringThickness, cap = StrokeCap.Butt)
            )

            // Bottom 90° outer border frame (Cyan-Blue)
            drawArc(
                color = Color(0xFF0E8AC8).copy(alpha = safeAlpha),
                startAngle = 0.8f,
                sweepAngle = 89.2f,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = ringThickness, cap = StrokeCap.Butt)
            )

            // 3. Draw rotated slice labels inside the 6 wedges at 1:1 native pixel resolution
            if (safeAlpha > 0.25f) {
                val nativeCanvas = drawContext.canvas.nativeCanvas
                val textAlphaInt = (safeAlpha * 255).toInt().coerceIn(0, 255)
                val fontOffset = (textPaint.descent() + textPaint.ascent()) / 2f

                levels.forEachIndexed { index, level ->
                    val isSelected = level == selectedLevel
                    val midAngleDeg = dynamicStartAngle + (index + 0.5f) * dynamicSliceSweep
                    val midAngleRad = midAngleDeg * (PI.toFloat() / 180f)
                    val labelRadius = (if (isSelected) baseInnerRadius + selectedBoostPx else baseInnerRadius) * 0.61f
                    val tx = centerX + cos(midAngleRad) * labelRadius
                    val ty = centerY + sin(midAngleRad) * labelRadius

                    textPaint.color = Color(level.textColorHex).toArgb()
                    textPaint.alpha = textAlphaInt
                    nativeCanvas.save()
                    nativeCanvas.translate(tx, ty)
                    nativeCanvas.rotate(midAngleDeg)
                    nativeCanvas.drawText(level.wheelLabel, 0f, -fontOffset, textPaint)
                    nativeCanvas.restore()
                }
            }
        }
    }
}

@Composable
private fun SoruLabAiTestGeneratorDialog(
    topic: YksTopic,
    level: TopicTestLevel,
    nextTestNumber: Int,
    poolCheck: PoolAvailabilityCheck,
    onGenerateFinished: () -> YksTestItem?,
    onDismiss: () -> Unit,
    onStartGeneratedTest: (YksTestItem) -> Unit,
    onGoToPdfStudio: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) }
    var progressTarget by remember { mutableFloatStateOf(0.20f) }
    var createdTest by remember { mutableStateOf<YksTestItem?>(null) }

    // Never show "Soru Bulunamadı" if a test was already created or if poolCheck.canGenerate is true!
    if (!poolCheck.canGenerate && createdTest == null && currentStep <= 1) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier
                    .widthIn(max = 430.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .testTag("no_questions_found_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(SoruLabCoralLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SearchOff,
                            contentDescription = null,
                            tint = SoruLabCoralRed,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = poolCheck.reasonTitle,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        ),
                        color = Color(0xFF0F172A),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = Color(level.wedgeColorHex).copy(alpha = 0.25f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "${topic.name} • ${level.displayTitle}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0F172A),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = poolCheck.reasonMessage,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = Color(0xFF475569),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("no_question_close_button")
                        ) {
                            Text(
                                text = "Kapat",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (level != TopicTestLevel.IKIZ) {
                            Button(
                                onClick = onGoToPdfStudio,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1D63DC),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("no_question_pdf_studio_button")
                            ) {
                                Text(
                                    text = "PDF'ten Soru Ekle",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(durationMillis = 450),
        label = "ai_gen_progress"
    )

    val realCount = poolCheck.availableQuestionCount.coerceAtMost(12)
    val step1Text = remember(topic, level, realCount) {
        if (level == TopicTestLevel.IKIZ) {
            "'${topic.name}' konusunda yanlış veya boş bıraktığınız $realCount gerçek soru kaydı doğrulandı..."
        } else {
            "Soru havuzundan '${topic.name}' konusu için $realCount gerçek soru seçiliyor..."
        }
    }
    val step2Text = remember(topic, level, realCount) {
        "$realCount soruluk testin kazanım sırası ve şık anahtarları eşleştiriliyor..."
    }
    val step3Text = remember(topic, level, nextTestNumber) {
        "${topic.name} ${level.displayTitle} Test-$nextTestNumber optik formu ve canlı çözüm tahtası hazırlanıyor..."
    }

    LaunchedEffect(topic.id, level) {
        progressTarget = 0.36f
        delay(650L)
        currentStep = 2
        progressTarget = 0.74f
        delay(650L)
        currentStep = 3
        progressTarget = 1.0f
        delay(550L)
        if (createdTest == null) {
            createdTest = onGenerateFinished()
        }
        currentStep = 4
    }

    Dialog(onDismissRequest = { if (createdTest != null) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("ai_test_generator_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isDone = currentStep >= 4 && createdTest != null

                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isDone) Color(0xFFDCFCE7) else Color(0xFFEFF6FF)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDone) Icons.Filled.CheckCircle else Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = if (isDone) Color(0xFF16A34A) else Color(0xFF1D63DC),
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (isDone) "Test Oluşturuldu!" else "Gerçek Havuzdan Oluşturuluyor...",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    ),
                    color = Color(0xFF0F172A)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${topic.name} • ${level.displayTitle} Test-$nextTestNumber ($realCount Soru)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF1D63DC),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(9.dp)
                        .clip(RoundedCornerShape(50)),
                    color = if (isDone) Color(0xFF16A34A) else Color(0xFF1D63DC),
                    trackColor = Color(0xFFE2E8F0)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AiGenerationStepRow(
                        stepNumber = 1,
                        text = step1Text,
                        isCompleted = currentStep > 1,
                        isActive = currentStep == 1
                    )
                    AiGenerationStepRow(
                        stepNumber = 2,
                        text = step2Text,
                        isCompleted = currentStep > 2,
                        isActive = currentStep == 2
                    )
                    AiGenerationStepRow(
                        stepNumber = 3,
                        text = step3Text,
                        isCompleted = currentStep > 3,
                        isActive = currentStep == 3
                    )
                }

                if (isDone && createdTest != null) {
                    Spacer(modifier = Modifier.height(22.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("ai_gen_listede_gor_button")
                        ) {
                            Text(
                                text = "Listede Gör",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Button(
                            onClick = { onStartGeneratedTest(createdTest!!) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1D63DC),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("ai_gen_teste_basla_button")
                        ) {
                            Text(
                                text = "Teste Başla",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiGenerationStepRow(
    stepNumber: Int,
    text: String,
    isCompleted: Boolean,
    isActive: Boolean
) {
    val bgColor = when {
        isCompleted -> Color(0xFFF0FDF4)
        isActive -> Color(0xFFEFF6FF)
        else -> Color(0xFFF8FAFC)
    }
    val borderColor = when {
        isCompleted -> Color(0xFFBBF7D0)
        isActive -> Color(0xFFBFDBFE)
        else -> Color(0xFFE2E8F0)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isCompleted -> Color(0xFF16A34A)
                        isActive -> Color(0xFF1D63DC)
                        else -> Color(0xFFCBD5E1)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            } else {
                Text(
                    text = "$stepNumber",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isActive || isCompleted) FontWeight.Medium else FontWeight.Normal,
                lineHeight = 17.sp
            ),
            color = if (isActive || isCompleted) Color(0xFF1E293B) else Color(0xFF94A3B8),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun DifficultyMeterBar(position: Float) {
    val activeCategory = when {
        position < 0.36f -> 0
        position < 0.72f -> 1
        else -> 2
    }
    val fillColor = when {
        position < 0.36f -> Color(0xFF15803D)
        position < 0.66f -> Color(0xFFE67E22)
        else -> Color(0xFFDC2626)
    }
    val fillFraction = position.coerceIn(0.18f, 0.94f)

    Column(
        modifier = Modifier.width(136.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
        ) {
            val pointerX = (fillFraction * size.width).coerceIn(10.dp.toPx(), size.width - 10.dp.toPx())
            val pointerPath = Path().apply {
                moveTo(pointerX - 5.dp.toPx(), 1.dp.toPx())
                lineTo(pointerX + 5.dp.toPx(), 1.dp.toPx())
                lineTo(pointerX, 6.5.dp.toPx())
                close()
            }
            drawPath(pointerPath, color = Color(0xFF1E3A8A))

            val barTop = 9.dp.toPx()
            val barHeight = 8.5.dp.toPx()
            val corner = CornerRadius(barHeight / 2f, barHeight / 2f)

            drawRoundRect(
                color = Color(0xFFE2E8F0),
                topLeft = Offset(0f, barTop),
                size = Size(size.width, barHeight),
                cornerRadius = corner
            )

            drawRoundRect(
                color = fillColor,
                topLeft = Offset(0f, barTop),
                size = Size(pointerX, barHeight),
                cornerRadius = corner
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Kolay",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = if (activeCategory == 0) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (activeCategory == 0) Color(0xFF1E293B) else Color(0xFF94A3B8)
            )
            Text(
                text = "Orta",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = if (activeCategory == 1) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (activeCategory == 1) Color(0xFF1E293B) else Color(0xFF94A3B8)
            )
            Text(
                text = "Zor",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = if (activeCategory == 2) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (activeCategory == 2) Color(0xFF1E293B) else Color(0xFF94A3B8)
            )
        }
    }
}
