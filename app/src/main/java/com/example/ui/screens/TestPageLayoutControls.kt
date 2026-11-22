package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.BookletHeaderInfo
import com.example.util.BookletType
import com.example.util.OpticalBadgeSpec
import com.example.util.OpticalBadgeStyle
import com.example.util.PageColumnLayoutMode
import com.example.util.QuestionScaleAndCropConfig
import com.example.util.QuestionSeparatorStyle
import com.example.util.QuestionSpacingPreset
import com.example.util.TestPageLayoutConfig
import kotlin.math.roundToInt

/**
 * Test oluşturma ve soru çözme ekranlarına entegre edilebilen kompakt
 * "Sayfa Düzeni ve Boyut Ayarları" kontrol çubuğu.
 */
@Composable
fun TestPageLayoutQuickToolbar(
    config: TestPageLayoutConfig,
    onConfigChange: (TestPageLayoutConfig) -> Unit,
    onOpenDetailedSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("test_page_layout_quick_toolbar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Tek Sütun / Çift Sütun Hızlı Seçici
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PageColumnLayoutMode.entries.forEach { mode ->
                    val selected = config.columnMode == mode
                    Surface(
                        onClick = { onConfigChange(config.withColumnMode(mode)) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) Color(0xFF1E3A8A) else Color.White,
                        border = BorderStroke(
                            1.dp,
                            if (selected) Color(0xFF1E3A8A) else Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier.testTag("quick_col_mode_${mode.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (mode == PageColumnLayoutMode.SINGLE_COLUMN) {
                                    Icons.Default.ViewStream
                                } else {
                                    Icons.Default.ViewColumn
                                },
                                contentDescription = mode.label,
                                tint = if (selected) Color.White else Color(0xFF475569),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = mode.shortLabel,
                                color = if (selected) Color.White else Color(0xFF334155),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. Kitapçık Türü (A / B) Hızlı Geçiş Rozeti
            Surface(
                onClick = {
                    val nextType = if (config.bookletType == BookletType.A) BookletType.B else BookletType.A
                    onConfigChange(config.withBookletType(nextType))
                },
                shape = RoundedCornerShape(8.dp),
                color = Color(config.bookletType.badgeBgColorHex),
                border = BorderStroke(1.dp, Color(config.bookletType.badgeColorHex).copy(alpha = 0.35f)),
                modifier = Modifier.testTag("quick_booklet_type_toggle")
            ) {
                Text(
                    text = "Kitapçık ${config.bookletType.code}",
                    color = Color(config.bookletType.badgeColorHex),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }

            // 3. Soru Boyutu (%50 - %150) & Detaylı Ayarlar Butonu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = onOpenDetailedSheet,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier.testTag("quick_scale_indicator_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Soru Boyutu",
                            tint = Color(0xFF1D4ED8),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "%${config.scaleConfig.clampedScalePercent}",
                            color = Color(0xFF1E3A8A),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Surface(
                    onClick = onOpenDetailedSheet,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A),
                    modifier = Modifier.testTag("open_page_layout_sheet_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Sayfa Düzeni Ayarları",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Düzen",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Kullanıcıların Tek/Çift sütun, Kitapçık A/B, Soru Boyutu (%50-%150), Soru Aralığı,
 * Optik Soru Numarası Rozeti ve Ayırıcı Çizgileri canlı önizlemeli olarak ayarlayabildiği Bottom Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TestPageLayoutSettingsBottomSheet(
    config: TestPageLayoutConfig,
    onConfigChange: (TestPageLayoutConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.testTag("test_page_layout_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Başlık Barı
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Sayfa Düzeni ve Boyut Ayarları",
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Kitapçık sütun akışı, soru ölçeği (%50-%150) ve optik rozet tasarımını özelleştirin",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = Color(0xFF64748B)
                    )
                }
            }

            // 1. CANLI ÖNİZLEME KARTI
            TestPageLayoutLivePreviewCard(config = config)

            HorizontalDivider(color = Color(0xFFE2E8F0))

            // 2. SÜTUN AKIŞI (TEK SÜTUN / ÇİFT SÜTUN) VE KİTAPÇIK TÜRÜ (A / B)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1.2f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Sayfa Sütun Akışı",
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PageColumnLayoutMode.entries.forEach { mode ->
                            val isSelected = config.columnMode == mode
                            Surface(
                                onClick = { onConfigChange(config.withColumnMode(mode)) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFFF8FAFC),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF1E3A8A) else Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sheet_col_mode_${mode.name}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (mode == PageColumnLayoutMode.SINGLE_COLUMN) {
                                            Icons.Default.ViewStream
                                        } else {
                                            Icons.Default.ViewColumn
                                        },
                                        contentDescription = mode.label,
                                        tint = if (isSelected) Color.White else Color(0xFF475569),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = mode.shortLabel,
                                        color = if (isSelected) Color.White else Color(0xFF1E293B),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(0.8f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Kitapçık Türü",
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BookletType.entries.forEach { type ->
                            val isSelected = config.bookletType == type
                            Surface(
                                onClick = { onConfigChange(config.withBookletType(type)) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(type.badgeColorHex) else Color(0xFFF8FAFC),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(type.badgeColorHex) else Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sheet_booklet_type_${type.code}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = type.code,
                                        color = if (isSelected) Color.White else Color(0xFF1E293B),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "Kitapçık",
                                        color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color(0xFF64748B),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. SORU BOYUTU VE ÖLÇEKLENDİRME (%50 - %150 ZOOM / SCALE)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val currentScale = config.scaleConfig.clampedScalePercent
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Soru Boyutu (Ölçeklendirme)",
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = "En-boy oranı (Aspect Ratio) korunarak %50 - %150 arası ölçeklenir",
                                color = Color(0xFF64748B),
                                fontSize = 11.5.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFDBEAFE)
                        ) {
                            Text(
                                text = "%$currentScale",
                                color = Color(0xFF1E3A8A),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = {
                                onConfigChange(config.withScalePercent((currentScale - 5).coerceAtLeast(50)))
                            },
                            shape = CircleShape,
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("scale_decrease_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Küçült",
                                    tint = Color(0xFF1E293B),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Slider(
                            value = currentScale.toFloat(),
                            onValueChange = { newVal ->
                                val snapped = ((newVal / 5f).roundToInt() * 5).coerceIn(50, 150)
                                onConfigChange(config.withScalePercent(snapped))
                            },
                            valueRange = 50f..150f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF1E3A8A),
                                activeTrackColor = Color(0xFF2563EB),
                                inactiveTrackColor = Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("question_scale_slider")
                        )

                        Surface(
                            onClick = {
                                onConfigChange(config.withScalePercent((currentScale + 5).coerceAtMost(150)))
                            },
                            shape = CircleShape,
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("scale_increase_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Büyüt",
                                    tint = Color(0xFF1E293B),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Hızlı Ölçek Butonları (%50, %75, %100, %125, %150)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(50, 75, 100, 125, 150).forEach { presetPercent ->
                            val isChosen = currentScale == presetPercent
                            Surface(
                                onClick = { onConfigChange(config.withScalePercent(presetPercent)) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isChosen) Color(0xFF2563EB) else Color.White,
                                border = BorderStroke(
                                    1.dp,
                                    if (isChosen) Color(0xFF2563EB) else Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scale_preset_$presetPercent")
                            ) {
                                Text(
                                    text = "%$presetPercent",
                                    color = if (isChosen) Color.White else Color(0xFF334155),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. SORU ARALIĞI (SIKIŞIK / NORMAL / GENİŞ)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Sorular Arası Boşluk (Aralık)",
                    color = Color(0xFF1E293B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuestionSpacingPreset.entries.forEach { preset ->
                        val isSelected = config.spacingPreset == preset
                        Surface(
                            onClick = { onConfigChange(config.withSpacingPreset(preset)) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFFF8FAFC),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF1E3A8A) else Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("spacing_preset_${preset.name}")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = preset.label,
                                    color = if (isSelected) Color.White else Color(0xFF1E293B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${preset.verticalGapDp} dp",
                                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color(0xFF64748B),
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // 5. OPTİK SORU NUMARASI ROZETİ VE AYIRICI ÇİZGİ STİLİ
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Optik Soru Numarası Rozet Stili",
                    color = Color(0xFF1E293B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OpticalBadgeStyle.entries.forEach { style ->
                        val isSelected = config.badgeStyle == style
                        Surface(
                            onClick = { onConfigChange(config.copy(badgeStyle = style)) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFDBEAFE) else Color(0xFFF8FAFC),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF2563EB) else Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier.testTag("badge_style_${style.name}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF1D4ED8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = style.label,
                                    color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF334155),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Soru Ayırıcı Çizgi Stili",
                    color = Color(0xFF1E293B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuestionSeparatorStyle.entries.forEach { sepStyle ->
                        val isSelected = config.separatorStyle == sepStyle
                        Surface(
                            onClick = { onConfigChange(config.copy(separatorStyle = sepStyle)) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFDBEAFE) else Color(0xFFF8FAFC),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF2563EB) else Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier.testTag("separator_style_${sepStyle.name}")
                        ) {
                            Text(
                                text = sepStyle.label,
                                color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF334155),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // 6. ALT AKSİYON BUTONLARI (SIFIRLA & TAMAM)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onConfigChange(
                            TestPageLayoutConfig(
                                bookletTitle = config.bookletTitle,
                                subjectHeader = config.subjectHeader
                            )
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reset_page_layout_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Varsayılana Sıfırla", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("apply_page_layout_btn")
                ) {
                    Text("Düzeni Uygula", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/**
 * Seçilen sayfa düzeni (tek/çift sütun), kitapçık türü (A/B), soru boyutu (%50-%150) ve
 * soru aralığının anlık önizlemesini gösteren minyatür kitapçık sayfası.
 */
@Composable
fun TestPageLayoutLivePreviewCard(
    config: TestPageLayoutConfig,
    modifier: Modifier = Modifier
) {
    val scaleFactor = config.scaleConfig.scaleFactor
    val previewBoxGap = (config.spacingPreset.verticalGapDp * 0.45f).dp
    val previewQuestionHeight = (28f * scaleFactor).coerceIn(14f, 44f).dp

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF1F5F9),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("test_page_layout_live_preview")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CANLI SAYFA DÜZENİ ÖNİZLEMESİ",
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.5.sp
                )
                Text(
                    text = "${config.columnMode.shortLabel} • ${config.bookletType.displayTitle} • %${config.scaleConfig.clampedScalePercent} • ${config.spacingPreset.label}",
                    color = Color(0xFF1E3A8A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp
                )
            }

            // Minyatür Beyaz Kitapçık Sayfası
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Minyatür Kitapçık Başlığı
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = config.subjectHeader.ifBlank { "TEMEL MATEMATİK TESTİ" },
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(config.bookletType.badgeBgColorHex)
                        ) {
                            Text(
                                text = config.bookletType.displayTitle,
                                color = Color(config.bookletType.badgeColorHex),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    // Minyatür Sütun Alanı
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (config.columnMode == PageColumnLayoutMode.SINGLE_COLUMN) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(previewBoxGap)
                            ) {
                                MiniPreviewQuestionRow(number = 1, height = previewQuestionHeight, config = config)
                                if (config.separatorStyle != QuestionSeparatorStyle.NONE) {
                                    QuestionHorizontalSeparator(style = config.separatorStyle)
                                }
                                MiniPreviewQuestionRow(number = 2, height = previewQuestionHeight, config = config)
                            }
                        } else {
                            // Sol Sütun
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(previewBoxGap)
                            ) {
                                val q1Num = if (config.bookletType == BookletType.A) 1 else 3
                                val q2Num = if (config.bookletType == BookletType.A) 2 else 1
                                MiniPreviewQuestionRow(number = q1Num, height = previewQuestionHeight, config = config)
                                if (config.separatorStyle != QuestionSeparatorStyle.NONE) {
                                    QuestionHorizontalSeparator(style = config.separatorStyle)
                                }
                                MiniPreviewQuestionRow(number = q2Num, height = previewQuestionHeight, config = config)
                            }

                            // Orta Çizgi
                            if (config.showCenterColumnDivider) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(1.dp)
                                        .background(Color(0xFFCBD5E1))
                                )
                            }

                            // Sağ Sütun
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(previewBoxGap)
                            ) {
                                val q3Num = if (config.bookletType == BookletType.A) 3 else 4
                                val q4Num = if (config.bookletType == BookletType.A) 4 else 2
                                MiniPreviewQuestionRow(number = q3Num, height = previewQuestionHeight, config = config)
                                if (config.separatorStyle != QuestionSeparatorStyle.NONE) {
                                    QuestionHorizontalSeparator(style = config.separatorStyle)
                                }
                                MiniPreviewQuestionRow(number = q4Num, height = previewQuestionHeight, config = config)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPreviewQuestionRow(
    number: Int,
    height: androidx.compose.ui.unit.Dp,
    config: TestPageLayoutConfig
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Surface(
            shape = when (config.badgeStyle) {
                OpticalBadgeStyle.OPTICAL_CIRCLE -> CircleShape
                OpticalBadgeStyle.ROUNDED_PILL -> RoundedCornerShape(4.dp)
                OpticalBadgeStyle.OSYM_BOOKLET -> RoundedCornerShape(2.dp)
                OpticalBadgeStyle.MINIMAL -> RoundedCornerShape(0.dp)
            },
            color = when (config.badgeStyle) {
                OpticalBadgeStyle.OPTICAL_CIRCLE -> Color(0xFF1E3A8A)
                OpticalBadgeStyle.ROUNDED_PILL -> Color(0xFFDBEAFE)
                OpticalBadgeStyle.OSYM_BOOKLET -> Color(0xFFF1F5F9)
                OpticalBadgeStyle.MINIMAL -> Color.Transparent
            },
            modifier = Modifier.size(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$number",
                    color = if (config.badgeStyle == OpticalBadgeStyle.OPTICAL_CIRCLE) {
                        Color.White
                    } else {
                        Color(0xFF1E3A8A)
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 8.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFFF8FAFC), RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                .padding(4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(4.dp)
                    .background(Color(0xFF94A3B8), RoundedCornerShape(2.dp))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(5) {
                    Box(
                        modifier = Modifier
                            .size(width = 10.dp, height = 4.dp)
                            .background(Color(0xFFCBD5E1), RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

/**
 * Gerçek kitapçık sayfasının üst kısmında yer alan ÖSYM tarzı Kitapçık Başlığı ve Kitapçık Türü (A/B) şeridi.
 */
@Composable
fun BookletPageHeaderBanner(
    headerInfo: BookletHeaderInfo,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("booklet_page_header_banner")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sol: Ders / Test Başlığı
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headerInfo.bookletTitle,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = headerInfo.subjectHeader,
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Orta: Soru Aralığı
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFFF1F5F9),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "${headerInfo.questionRangeLabel} • Sayfa ${headerInfo.pageNumber}/${headerInfo.totalPages}",
                    color = Color(0xFF334155),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }

            // Sağ: A / B Kitapçık Türü Rozeti
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(headerInfo.bookletType.badgeBgColorHex),
                border = BorderStroke(1.dp, Color(headerInfo.bookletType.badgeColorHex).copy(alpha = 0.4f)),
                modifier = Modifier.testTag("booklet_type_badge_${headerInfo.bookletType.code}")
            ) {
                Text(
                    text = headerInfo.bookletType.displayTitle,
                    color = Color(headerInfo.bookletType.badgeColorHex),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(thickness = 1.dp, color = Color(0xFFCBD5E1))
    }
}

/**
 * Her sorunun sol oluğunda gösterilen Optik Soru Numarası Rozeti.
 */
@Composable
fun OpticalQuestionNumberBadge(
    spec: OpticalBadgeSpec,
    modifier: Modifier = Modifier
) {
    val shape = when (spec.style) {
        OpticalBadgeStyle.OPTICAL_CIRCLE -> CircleShape
        OpticalBadgeStyle.ROUNDED_PILL -> RoundedCornerShape(8.dp)
        OpticalBadgeStyle.OSYM_BOOKLET -> RoundedCornerShape(4.dp)
        OpticalBadgeStyle.MINIMAL -> RoundedCornerShape(0.dp)
    }
    val hasBorder = spec.style == OpticalBadgeStyle.ROUNDED_PILL ||
        spec.style == OpticalBadgeStyle.OSYM_BOOKLET

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(spec.badgeSizeDp.dp)
            .clip(shape)
            .background(
                if (spec.style == OpticalBadgeStyle.MINIMAL) {
                    Color.Transparent
                } else {
                    Color(spec.backgroundColorHex)
                }
            )
            .then(
                if (hasBorder) {
                    Modifier.border(1.dp, Color(spec.primaryColorHex).copy(alpha = 0.3f), shape)
                } else {
                    Modifier
                }
            )
            .testTag("optical_question_badge_${spec.printedNumber}")
    ) {
        Text(
            text = spec.displayLabel,
            color = Color(spec.primaryColorHex),
            fontWeight = FontWeight.ExtraBold,
            fontSize = spec.fontSizeSp.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Aynı sütundaki ardışık sorular arasına çizilen yatay soru ayırıcı çizgi.
 */
@Composable
fun QuestionHorizontalSeparator(
    style: QuestionSeparatorStyle,
    modifier: Modifier = Modifier
) {
    when (style) {
        QuestionSeparatorStyle.NONE -> Unit
        QuestionSeparatorStyle.SUBTLE_HAIRLINE -> {
            HorizontalDivider(
                thickness = 0.8.dp,
                color = Color(0xFFE2E8F0),
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("question_separator_hairline")
            )
        }
        QuestionSeparatorStyle.SOLID_LINE -> {
            HorizontalDivider(
                thickness = 1.2.dp,
                color = Color(0xFFCBD5E1),
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("question_separator_solid")
            )
        }
        QuestionSeparatorStyle.DASHED_LINE -> {
            Canvas(
                modifier = modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .testTag("question_separator_dashed")
            ) {
                drawLine(
                    color = Color(0xFF94A3B8),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )
            }
        }
    }
}
