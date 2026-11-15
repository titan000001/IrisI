package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.CinemaAmber
import com.example.viewmodel.CineRigUiState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

private enum class DialGroup(val label: String) {
    TONE_WHEELS("Lift / Gamma / Gain"),
    COLOR_BALANCE("Temp & Tint"),
    FINISH("Contrast / Sat / Vign")
}

/**
 * Viewfinder-docked, non-modal Color Science Workbench.
 * Keeps the live optical viewfinder 100% bright, undimmed, and unobstructed above the dials.
 */
@Composable
fun ColorScienceWorkbench(
    state: CineRigUiState,
    onSelectLogProfile: (LogProfile) -> Unit,
    onSelectFilmPreset: (FilmPreset) -> Unit,
    onUpdateGrading: ((ColorGradingSettings) -> ColorGradingSettings) -> Unit,
    onToggleHistogram: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVideoMode = state.captureMode == CaptureMode.VIDEO || state.captureMode == CaptureMode.RAW_VIDEO
    val tabs = remember(isVideoMode) {
        if (isVideoMode) listOf("Dials", "Stocks", "Log / LUT")
        else listOf("Dials", "Stocks")
    }
    var selectedTab by remember(isVideoMode) { mutableIntStateOf(0) }
    var activeDialGroup by remember { mutableStateOf(DialGroup.TONE_WHEELS) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .testTag("color_science_workbench"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Compact Top Workbench Header: Tabs + Histogram Scope Toggle + Reset + Done
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { idx, title ->
                        val isSelected = selectedTab == idx
                        Surface(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 32.dp)
                                .testTag("color_tab_$idx")
                                .clickable { selectedTab = idx },
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick RGB Histogram toggle so user sees scopes react live to dials
                    Surface(
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("workbench_hist_toggle")
                            .clickable(onClick = onToggleHistogram),
                        shape = CircleShape,
                        color = if (state.histogramEnabled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.BarChart,
                                contentDescription = "Toggle live RGB histogram",
                                tint = if (state.histogramEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // Reset active look
                    Surface(
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("workbench_reset_btn")
                            .clickable {
                                if (isVideoMode) onSelectLogProfile(LogProfile.REC_709)
                                onSelectFilmPreset(FilmPreset.NONE)
                                onUpdateGrading { ColorGradingSettings() }
                            },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = "Reset color science",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // Done / Close Workbench
                    FilledTonalButton(
                        onClick = onDone,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("workbench_done_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Done", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    // DIALS TAB: Sub-group switcher + tactile rotary dials / 2D Color Pad
                    val g = state.colorGrading
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DialGroup.entries.forEach { group ->
                            val selected = activeDialGroup == group
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 30.dp)
                                    .testTag("dial_group_${group.name}")
                                    .clickable { activeDialGroup = group },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                                    else Color.Transparent
                                )
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = group.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    when (activeDialGroup) {
                        DialGroup.TONE_WHEELS -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Live Tonal Response Curve showing exact effect of Lift/Gamma/Gain/Contrast
                                TonalCurvePreview(
                                    lift = g.lift,
                                    gamma = g.gamma,
                                    gain = g.gain,
                                    contrast = g.contrast,
                                    modifier = Modifier
                                        .width(84.dp)
                                        .height(84.dp)
                                )
                                RotaryColorDial(
                                    label = "LIFT",
                                    subLabel = "Shadows",
                                    value = g.lift,
                                    defaultValue = 0f,
                                    range = -0.3f..0.3f,
                                    valueText = String.format(java.util.Locale.US, "%+.2f", g.lift),
                                    accentColor = Color(0xFF7FA8E8),
                                    modifier = Modifier.weight(1f).testTag("dial_lift"),
                                    onValueChange = { v -> onUpdateGrading { it.copy(lift = v) } }
                                )
                                RotaryColorDial(
                                    label = "GAMMA",
                                    subLabel = "Mids",
                                    value = g.gamma,
                                    defaultValue = 0f,
                                    range = -0.3f..0.3f,
                                    valueText = String.format(java.util.Locale.US, "%+.2f", g.gamma),
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f).testTag("dial_gamma"),
                                    onValueChange = { v -> onUpdateGrading { it.copy(gamma = v) } }
                                )
                                RotaryColorDial(
                                    label = "GAIN",
                                    subLabel = "Highs",
                                    value = g.gain,
                                    defaultValue = 1.0f,
                                    range = 0.7f..1.3f,
                                    valueText = String.format(java.util.Locale.US, "%.2fx", g.gain),
                                    accentColor = CinemaAmber,
                                    modifier = Modifier.weight(1f).testTag("dial_gain"),
                                    onValueChange = { v -> onUpdateGrading { it.copy(gain = v) } }
                                )
                            }
                        }

                        DialGroup.COLOR_BALANCE -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Interactive 2D White-Balance Color Wheel / Pad (Drag across Blue<->Amber & Green<->Magenta)
                                ColorBalance2DPad(
                                    temperature = g.temperatureOffset,
                                    tint = g.tintOffset,
                                    onUpdate = { t, ti ->
                                        onUpdateGrading {
                                            it.copy(
                                                temperatureOffset = t.coerceIn(-0.8f, 0.8f),
                                                tintOffset = ti.coerceIn(-0.8f, 0.8f)
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .size(96.dp)
                                        .testTag("wb_2d_color_pad")
                                )
                                RotaryColorDial(
                                    label = "TEMP",
                                    subLabel = "Cool / Warm",
                                    value = g.temperatureOffset,
                                    defaultValue = 0f,
                                    range = -0.8f..0.8f,
                                    valueText = String.format(java.util.Locale.US, "%+.2f", g.temperatureOffset),
                                    accentColor = if (g.temperatureOffset >= 0f) Color(0xFFFFB74D) else Color(0xFF64B5F6),
                                    modifier = Modifier.weight(1f).testTag("dial_temp"),
                                    onValueChange = { v -> onUpdateGrading { it.copy(temperatureOffset = v) } }
                                )
                                RotaryColorDial(
                                    label = "TINT",
                                    subLabel = "Grn / Mag",
                                    value = g.tintOffset,
                                    defaultValue = 0f,
                                    range = -0.8f..0.8f,
                                    valueText = String.format(java.util.Locale.US, "%+.2f", g.tintOffset),
                                    accentColor = if (g.tintOffset >= 0f) Color(0xFFF06292) else Color(0xFF69F0AE),
                                    modifier = Modifier.weight(1f).testTag("dial_tint"),
                                    onValueChange = { v -> onUpdateGrading { it.copy(tintOffset = v) } }
                                )
                            }
                        }

                        DialGroup.FINISH -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RotaryColorDial(
                                    label = "CONTRAST",
                                    subLabel = "Punch",
                                    value = g.contrast,
                                    defaultValue = 1.0f,
                                    range = 0.5f..1.6f,
                                    valueText = String.format(java.util.Locale.US, "%.2fx", g.contrast),
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f).testTag("dial_contrast"),
                                    onValueChange = { v -> onUpdateGrading { it.copy(contrast = v) } }
                                )
                                RotaryColorDial(
                                    label = "SAT",
                                    subLabel = "Chroma",
                                    value = g.saturation,
                                    defaultValue = 1.0f,
                                    range = 0f..1.6f,
                                    valueText = String.format(java.util.Locale.US, "%.2fx", g.saturation),
                                    accentColor = Color(0xFFFF8A65),
                                    modifier = Modifier.weight(1f).testTag("dial_saturation"),
                                    onValueChange = { v -> onUpdateGrading { it.copy(saturation = v) } }
                                )
                                RotaryColorDial(
                                    label = "VIGNETTE",
                                    subLabel = "Edge Falloff",
                                    value = g.vignette,
                                    defaultValue = 0f,
                                    range = 0f..0.8f,
                                    valueText = "${(g.vignette * 100).toInt()}%",
                                    accentColor = Color(0xFFB39DDB),
                                    modifier = Modifier.weight(1f).testTag("dial_vignette"),
                                    onValueChange = { v -> onUpdateGrading { it.copy(vignette = v) } }
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // FILM STOCKS TAB: Visual swatches + Grain Amount / Grain Size Rotary Dials
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(FilmPreset.entries, key = { it.name }) { preset ->
                            val selected = state.filmPreset == preset
                            Surface(
                                modifier = Modifier
                                    .width(106.dp)
                                    .testTag("film_preset_${preset.name}")
                                    .clickable { onSelectFilmPreset(preset) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) preset.swatchColor.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) preset.swatchColor
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(preset.swatchColor)
                                        )
                                        Text(
                                            text = preset.shortCode,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = if (selected) preset.swatchColor else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = if (preset == FilmPreset.NONE) "Natural" else preset.displayName,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // Dual Rotary Film Grain Dials directly below film strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RotaryColorDial(
                            label = "GRAIN",
                            subLabel = "Density",
                            value = state.colorGrading.grainAmount,
                            defaultValue = state.filmPreset.defaultGrain,
                            range = 0f..1f,
                            valueText = "${(state.colorGrading.grainAmount * 100).toInt()}%",
                            accentColor = state.filmPreset.swatchColor,
                            modifier = Modifier.weight(1f).testTag("dial_grain_amount"),
                            onValueChange = { v -> onUpdateGrading { it.copy(grainAmount = v) } }
                        )
                        RotaryColorDial(
                            label = "CLUMP",
                            subLabel = "Texture Size",
                            value = state.colorGrading.grainSize,
                            defaultValue = 1.2f,
                            range = 0.6f..2.5f,
                            valueText = String.format(java.util.Locale.US, "%.1fx", state.colorGrading.grainSize),
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f).testTag("dial_grain_size"),
                            onValueChange = { v -> onUpdateGrading { it.copy(grainSize = v) } }
                        )
                    }
                }

                2 -> {
                    // LOG / LUT TAB (Video / RAW Video only)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Rec.709 Monitoring LUT", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        if (state.colorGrading.monitoringLutEnabled) "Previewing graded contrast on flat Log"
                                        else "Viewing raw flat Log gamma curve",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.colorGrading.monitoringLutEnabled,
                                    onCheckedChange = { enabled ->
                                        onUpdateGrading { it.copy(monitoringLutEnabled = enabled) }
                                    },
                                    modifier = Modifier.testTag("monitor_lut_switch")
                                )
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(LogProfile.entries, key = { it.name }) { profile ->
                                LogGammaCurveCard(
                                    profile = profile,
                                    selected = state.logProfile == profile,
                                    onClick = { onSelectLogProfile(profile) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tactile Rotary Arc Dial with knurled ring indicator, center-default detent, drag gesture,
 * and double-tap reset.
 */
@Composable
fun RotaryColorDial(
    label: String,
    subLabel: String,
    value: Float,
    defaultValue: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val span = (range.endInclusive - range.start).coerceAtLeast(0.0001f)
    val norm = ((value - range.start) / span).coerceIn(0f, 1f)
    val defaultNorm = ((defaultValue - range.start) / span).coerceIn(0f, 1f)
    val currentValueState by rememberUpdatedState(value)

    val trackBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val tickInactive = MaterialTheme.colorScheme.outlineVariant
    val knobFill = MaterialTheme.colorScheme.surfaceContainerHigh

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 84.dp)
            .semantics { contentDescription = "$label $valueText. Drag horizontally or vertically, double tap to reset" }
            .pointerInput(range, defaultValue) {
                detectTapGestures(
                    onDoubleTap = { onValueChange(defaultValue) }
                )
            }
            .pointerInput(range) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val deltaNorm = (dragAmount.x - dragAmount.y * 0.65f) / 180f
                    val next = (currentValueState + deltaNorm * span).coerceIn(range.start, range.endInclusive)
                    onValueChange(next)
                }
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (kotlin.math.abs(value - defaultValue) > span * 0.02f) {
                accentColor.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier.size(50.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val startAngle = 135f
                    val sweepTotal = 270f
                    val strokeW = 4.dp.toPx()
                    val radius = size.minDimension / 2f - strokeW
                    val topLeft = Offset(center.x - radius, center.y - radius)
                    val arcSize = Size(radius * 2f, radius * 2f)

                    // Inner physical knurled knob face
                    drawCircle(
                        color = knobFill,
                        radius = radius - 3.dp.toPx(),
                        center = center
                    )

                    // Background track arc
                    drawArc(
                        color = trackBg,
                        startAngle = startAngle,
                        sweepAngle = sweepTotal,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )

                    // Center / default-value detent notch
                    val defaultDeg = startAngle + defaultNorm * sweepTotal
                    val defaultRad = (defaultDeg * PI / 180.0).toFloat()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.55f),
                        radius = 2.dp.toPx(),
                        center = Offset(
                            center.x + cos(defaultRad) * radius,
                            center.y + sin(defaultRad) * radius
                        )
                    )

                    // Active value arc (anchored from default detent if bipolar, or from start if unipolar)
                    val activeStartDeg = startAngle + minOf(defaultNorm, norm) * sweepTotal
                    val activeSweep = kotlin.math.abs(norm - defaultNorm) * sweepTotal
                    if (activeSweep > 1f) {
                        drawArc(
                            color = accentColor,
                            startAngle = activeStartDeg,
                            sweepAngle = activeSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }

                    // Pointer needle on the rotary dial face
                    val curDeg = startAngle + norm * sweepTotal
                    val curRad = (curDeg * PI / 180.0).toFloat()
                    drawLine(
                        color = accentColor,
                        start = Offset(
                            center.x + cos(curRad) * (radius * 0.32f),
                            center.y + sin(curRad) * (radius * 0.32f)
                        ),
                        end = Offset(
                            center.x + cos(curRad) * (radius - 2.5.dp.toPx()),
                            center.y + sin(curRad) * (radius - 2.5.dp.toPx())
                        ),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Outer subtle knurl ticks
                    for (t in 0..8) {
                        val td = startAngle + (t / 8f) * sweepTotal
                        val tr = (td * PI / 180.0).toFloat()
                        drawCircle(
                            color = tickInactive.copy(alpha = 0.45f),
                            radius = 1.dp.toPx(),
                            center = Offset(
                                center.x + cos(tr) * (radius + 3.dp.toPx()),
                                center.y + sin(tr) * (radius + 3.dp.toPx())
                            )
                        )
                    }
                }
            }

            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = if (kotlin.math.abs(value - defaultValue) > span * 0.02f) accentColor
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "$label · $subLabel",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * Interactive 2D Color Balance Pad (Cool <-> Warm horizontally, Green <-> Magenta vertically)
 * so adjusting color cast against the live viewfinder is effortless and visual.
 */
@Composable
fun ColorBalance2DPad(
    temperature: Float,
    tint: Float,
    onUpdate: (temperature: Float, tint: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxRange = 0.8f
    Surface(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onUpdate(0f, 0f) },
                    onTap = { pos ->
                        val nx = ((pos.x / size.width) * 2f - 1f) * maxRange
                        val ny = ((pos.y / size.height) * 2f - 1f) * maxRange
                        onUpdate(nx, ny)
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val nx = ((change.position.x / size.width) * 2f - 1f) * maxRange
                    val ny = ((change.position.y / size.height) * 2f - 1f) * maxRange
                    onUpdate(nx, ny)
                }
            },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Horizontal Cool Blue -> Warm Amber gradient
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF1E4F8A),
                        Color(0xFF282B30),
                        Color(0xFF9C5B18)
                    )
                )
            )
            // Vertical Green -> Magenta wash
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF248C56).copy(alpha = 0.45f),
                        Color.Transparent,
                        Color(0xFF9E2B6B).copy(alpha = 0.45f)
                    )
                )
            )

            // Center crosshairs
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawLine(Color.White.copy(alpha = 0.28f), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1f)
            drawLine(Color.White.copy(alpha = 0.28f), Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1f)

            // Active reticle puck
            val px = ((temperature / maxRange + 1f) * 0.5f).coerceIn(0.06f, 0.94f) * size.width
            val py = ((tint / maxRange + 1f) * 0.5f).coerceIn(0.06f, 0.94f) * size.height
            drawCircle(
                color = Color.White.copy(alpha = 0.95f),
                radius = 7.dp.toPx(),
                center = Offset(px, py),
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = CinemaAmber,
                radius = 2.8.dp.toPx(),
                center = Offset(px, py)
            )
        }
    }
}

/**
 * Live Tonal Curve Canvas visualizing how Lift, Gamma, Gain, and Contrast bend sensor luminance.
 */
@Composable
fun TonalCurvePreview(
    lift: Float,
    gamma: Float,
    gain: Float,
    contrast: Float,
    modifier: Modifier = Modifier
) {
    val lineColor = CinemaAmber
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            val w = size.width
            val h = size.height

            // Subtle 2x2 reference grid + 1:1 diagonal reference line
            drawLine(gridColor, Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1f)
            drawLine(gridColor.copy(alpha = 0.55f), Offset(0f, h), Offset(w, 0f), strokeWidth = 1f)

            val path = Path()
            val samples = 24
            for (i in 0..samples) {
                val xNorm = i / samples.toFloat()
                // Combine contrast around 0.5, gamma power bend, lift shadow toe, gain highlight shoulder
                val gammaPow = (1f - gamma * 1.15f).coerceIn(0.45f, 1.75f)
                val curved = xNorm.toDouble().pow(gammaPow.toDouble()).toFloat()
                val graded = (((curved - 0.5f) * contrast + 0.5f) * gain + lift * 0.7f).coerceIn(0f, 1f)
                val px = xNorm * w
                val py = h - (graded * h)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Log Gamma Card displaying the actual transfer curve shape & dynamic range stops.
 */
@Composable
private fun LogGammaCurveCard(
    profile: LogProfile,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(126.dp)
            .testTag("log_profile_${profile.name}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val curveColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Canvas(modifier = Modifier.size(30.dp)) {
                val w = size.width
                val h = size.height
                drawLine(Color.White.copy(alpha = 0.16f), Offset(0f, h), Offset(w, 0f), strokeWidth = 1f)
                val path = Path()
                for (i in 0..16) {
                    val x = i / 16f
                    val y = if (profile == LogProfile.REC_709) {
                        x.toDouble().pow(0.85).toFloat()
                    } else {
                        val lift = profile.blackLift / 255f
                        (lift + (1f - lift) * x.toDouble().pow(0.42).toFloat() * profile.highlightCompression).coerceIn(0f, 1f)
                    }
                    if (i == 0) path.moveTo(x * w, h - y * h) else path.lineTo(x * w, h - y * h)
                }
                drawPath(path, color = curveColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
            Column {
                Text(
                    text = profile.shortName,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = profile.dynamicRangeStops,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
