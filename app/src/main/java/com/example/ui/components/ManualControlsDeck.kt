package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CaptureMode
import com.example.model.ExposureProgram
import com.example.model.ExposureSettings
import com.example.model.LensInfo
import com.example.model.PeakingColor
import com.example.ui.theme.CinemaAmber
import com.example.viewmodel.CineRigUiState
import com.example.viewmodel.CineRigViewModel
import com.example.viewmodel.ManualControlTab
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun LensAndZoomBar(
    lenses: List<LensInfo>,
    selectedLens: LensInfo?,
    zoomRatio: Float,
    zoomSliderExpanded: Boolean,
    isPhotoIdentity: Boolean,
    onSelectLens: (LensInfo) -> Unit,
    onZoomChange: (Float) -> Unit,
    onToggleZoomSlider: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AnimatedVisibility(visible = zoomSliderExpanded) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.1fx", zoomRatio),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val maxZ = (selectedLens?.maxZoom ?: 8f).coerceIn(2f, 12f)
                    Slider(
                        value = zoomRatio.coerceIn(1f, maxZ),
                        onValueChange = onZoomChange,
                        valueRange = 1f..maxZ,
                        modifier = Modifier
                            .width(150.dp)
                            .height(26.dp)
                            .testTag("smooth_zoom_slider")
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            if (lenses.isNotEmpty()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.80f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                ) {
                    LazyRow(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(lenses, key = { it.cameraId + it.label }) { lens ->
                            val isSelected = selectedLens?.cameraId == lens.cameraId
                            val primaryLabel = if (isPhotoIdentity && !lens.isFront) {
                                "${lens.equivalentMm}mm"
                            } else {
                                lens.label
                            }
                            Surface(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("lens_pill_${lens.label}")
                                    .clickable { onSelectLens(lens) },
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = primaryLabel,
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
                }
            }

            Surface(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("zoom_pill_toggle")
                    .clickable(onClick = onToggleZoomSlider),
                shape = CircleShape,
                color = if (zoomSliderExpanded) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.80f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.1fx", zoomRatio),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (zoomSliderExpanded) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun ManualControlsDeck(
    state: CineRigUiState,
    onSelectProgram: (ExposureProgram) -> Unit,
    onSelectTab: (ManualControlTab) -> Unit,
    onToggleExpanded: () -> Unit,
    onUpdateExposure: ((ExposureSettings) -> ExposureSettings) -> Unit,
    onTogglePeaking: () -> Unit,
    onSelectPeakingColor: (PeakingColor) -> Unit,
    onToggleMagnifier: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exp = state.exposure
    val lens = state.selectedLens
    val evDisplay = String.format(java.util.Locale.US, "%+.1f", exp.evCompensation * exp.evStep)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            // Compact Telemetry Row + Exposure Program Toggle (AUTO / P / S / ISO / M)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("toggle_manual_deck")
                        .clickable(onClick = onToggleExpanded),
                    shape = CircleShape,
                    color = if (state.manualDeckExpanded || exp.program != ExposureProgram.AUTO) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (state.manualDeckExpanded) Icons.Rounded.ExpandMore else Icons.Rounded.Tune,
                            contentDescription = "Toggle exposure deck",
                            tint = if (state.manualDeckExpanded || exp.program != ExposureProgram.AUTO) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = exp.program.shortCode,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (state.manualDeckExpanded || exp.program != ExposureProgram.AUTO) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }

                TelemetryTabChip(
                    title = "EV",
                    value = if (exp.isManualExposure) "MAN" else evDisplay,
                    selected = state.manualDeckExpanded && state.activeManualTab == ManualControlTab.EV,
                    isManual = !exp.isManualExposure && exp.evCompensation != 0,
                    modifier = Modifier.weight(0.85f).testTag("manual_tab_ev"),
                    onClick = { onSelectTab(ManualControlTab.EV) }
                )
                TelemetryTabChip(
                    title = "ISO",
                    value = if (exp.isManualExposure) "${exp.iso}" else "AUTO",
                    selected = state.manualDeckExpanded && state.activeManualTab == ManualControlTab.ISO,
                    isManual = exp.isManualExposure,
                    modifier = Modifier.weight(1f).testTag("manual_tab_iso"),
                    onClick = { onSelectTab(ManualControlTab.ISO) }
                )
                TelemetryTabChip(
                    title = if (state.captureMode == CaptureMode.VIDEO || state.captureMode == CaptureMode.RAW_VIDEO) "180°/SS" else "SHUTTER",
                    value = if (exp.isManualExposure) CineRigViewModel.formatShutterSpeed(exp.shutterSpeedNs) else "AUTO",
                    selected = state.manualDeckExpanded && state.activeManualTab == ManualControlTab.SHUTTER,
                    isManual = exp.isManualExposure,
                    modifier = Modifier.weight(1f).testTag("manual_tab_shutter"),
                    onClick = { onSelectTab(ManualControlTab.SHUTTER) }
                )
                TelemetryTabChip(
                    title = "FOCUS",
                    value = if (exp.isManualFocus) String.format(java.util.Locale.US, "%.1fD", exp.focusDistance) else "AF-C",
                    selected = state.manualDeckExpanded && state.activeManualTab == ManualControlTab.FOCUS,
                    isManual = exp.isManualFocus,
                    modifier = Modifier.weight(1f).testTag("manual_tab_focus"),
                    onClick = { onSelectTab(ManualControlTab.FOCUS) }
                )
                TelemetryTabChip(
                    title = "WB",
                    value = if (exp.isAutoWhiteBalance) "AWB" else "${exp.whiteBalanceKelvin}K",
                    selected = state.manualDeckExpanded && state.activeManualTab == ManualControlTab.WB,
                    isManual = !exp.isAutoWhiteBalance,
                    modifier = Modifier.weight(1f).testTag("manual_tab_wb"),
                    onClick = { onSelectTab(ManualControlTab.WB) }
                )
            }

            AnimatedVisibility(
                visible = state.manualDeckExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Exposure Program Selector: AUTO • P • S • ISO • M
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ExposureProgram.entries.forEach { prog ->
                            val selected = exp.program == prog
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("exp_program_${prog.name}")
                                    .clickable { onSelectProgram(prog) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        text = prog.shortCode,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 10.sp
                                        ),
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    when (state.activeManualTab) {
                        ManualControlTab.EV -> {
                            val minIdx = state.evBounds.minIndex
                            val maxIdx = state.evBounds.maxIndex.coerceAtLeast(minIdx + 1)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = exp.evCompensation == 0,
                                    onClick = {
                                        onUpdateExposure {
                                            it.copy(
                                                program = if (it.program == ExposureProgram.MANUAL) ExposureProgram.PROGRAM else it.program,
                                                isManualExposure = false,
                                                evCompensation = 0
                                            )
                                        }
                                    },
                                    label = { Text("0.0 EV") },
                                    modifier = Modifier.testTag("ev_reset_chip")
                                )
                                Slider(
                                    value = exp.evCompensation.toFloat().coerceIn(minIdx.toFloat(), maxIdx.toFloat()),
                                    onValueChange = { v ->
                                        val idx = v.roundToInt().coerceIn(minIdx, maxIdx)
                                        onUpdateExposure {
                                            it.copy(
                                                program = if (it.program == ExposureProgram.MANUAL) ExposureProgram.PROGRAM else it.program,
                                                isManualExposure = false,
                                                evCompensation = idx
                                            )
                                        }
                                    },
                                    valueRange = minIdx.toFloat()..maxIdx.toFloat(),
                                    modifier = Modifier.weight(1f).testTag("ev_slider")
                                )
                            }
                            PrecisionRulerTicks()
                        }

                        ManualControlTab.ISO -> {
                            val minIso = (lens?.minIso ?: 50).toFloat()
                            val maxIso = (lens?.maxIso ?: 6400).toFloat().coerceAtLeast(minIso + 100f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !exp.isManualExposure,
                                    onClick = {
                                        onUpdateExposure {
                                            val nextManual = !it.isManualExposure
                                            it.copy(
                                                program = if (nextManual) ExposureProgram.MANUAL else ExposureProgram.AUTO,
                                                isManualExposure = nextManual
                                            )
                                        }
                                    },
                                    label = { Text(if (exp.isManualExposure) "MANUAL" else "AUTO AE") },
                                    modifier = Modifier.testTag("iso_auto_toggle")
                                )
                                Slider(
                                    value = exp.iso.toFloat().coerceIn(minIso, maxIso),
                                    onValueChange = { valRounded ->
                                        val snapped = (valRounded / 25f).roundToInt() * 25
                                        onUpdateExposure {
                                            val prog = if (it.program == ExposureProgram.ISO_PRIORITY) ExposureProgram.ISO_PRIORITY else ExposureProgram.MANUAL
                                            it.copy(program = prog, isManualExposure = true, iso = snapped)
                                        }
                                    },
                                    valueRange = minIso..maxIso,
                                    modifier = Modifier.weight(1f).testTag("iso_slider")
                                )
                            }
                            PrecisionRulerTicks()
                        }

                        ManualControlTab.SHUTTER -> {
                            val minNs = (lens?.minExposureNs ?: 250_000L).coerceAtLeast(100_000L).toDouble()
                            val maxNs = (lens?.maxExposureNs ?: 1_000_000_000L).coerceAtMost(2_000_000_000L).toDouble()
                            val logMin = ln(minNs)
                            val logMax = ln(maxNs.coerceAtLeast(minNs * 2.0))
                            val currentProgress = ((ln(exp.shutterSpeedNs.toDouble().coerceIn(minNs, maxNs)) - logMin) / (logMax - logMin)).toFloat().coerceIn(0f, 1f)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = exp.isManualExposure && exp.shutterSpeedNs in 19_000_000L..22_000_000L,
                                    onClick = {
                                        val ns180 = (1_000_000_000L / (state.videoFps.fps * 2L))
                                        onUpdateExposure {
                                            it.copy(program = ExposureProgram.MANUAL, isManualExposure = true, shutterSpeedNs = ns180)
                                        }
                                    },
                                    label = { Text("180°") },
                                    modifier = Modifier.testTag("shutter_180_button")
                                )
                                Slider(
                                    value = currentProgress,
                                    onValueChange = { prog ->
                                        val ns = Math.E.pow(logMin + prog * (logMax - logMin)).toLong()
                                        onUpdateExposure {
                                            val expProg = if (it.program == ExposureProgram.SHUTTER_PRIORITY) ExposureProgram.SHUTTER_PRIORITY else ExposureProgram.MANUAL
                                            it.copy(program = expProg, isManualExposure = true, shutterSpeedNs = ns)
                                        }
                                    },
                                    modifier = Modifier.weight(1f).testTag("shutter_slider")
                                )
                            }
                            PrecisionRulerTicks()
                        }

                        ManualControlTab.FOCUS -> {
                            val maxDiopters = (lens?.minFocusDistance ?: 10f).coerceAtLeast(1f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = !exp.isManualFocus,
                                    onClick = { onUpdateExposure { it.copy(isManualFocus = !it.isManualFocus) } },
                                    label = { Text(if (exp.isManualFocus) "MF" else "AF") },
                                    modifier = Modifier.testTag("focus_af_mf_toggle")
                                )
                                Slider(
                                    value = exp.focusDistance.coerceIn(0f, maxDiopters),
                                    onValueChange = { d ->
                                        onUpdateExposure { it.copy(isManualFocus = true, focusDistance = d) }
                                    },
                                    valueRange = 0f..maxDiopters,
                                    modifier = Modifier.weight(1f).testTag("focus_slider")
                                )
                                IconButton(
                                    onClick = onTogglePeaking,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (state.focusPeakingEnabled) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CenterFocusStrong,
                                        contentDescription = "Focus peaking",
                                        tint = if (state.focusPeakingEnabled) state.peakingColor.color else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onToggleMagnifier,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (state.focusMagnifierEnabled) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ZoomIn,
                                        contentDescription = "Focus magnifier",
                                        tint = if (state.focusMagnifierEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            PrecisionRulerTicks()
                            if (state.focusPeakingEnabled) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Peak Color", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    PeakingColor.entries.forEach { pc ->
                                        val selected = state.peakingColor == pc
                                        Surface(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .testTag("peaking_color_${pc.name}")
                                                .clickable { onSelectPeakingColor(pc) },
                                            shape = CircleShape,
                                            color = pc.color,
                                            border = BorderStroke(
                                                width = if (selected) 2.dp else 1.dp,
                                                color = if (selected) Color.White else Color.Transparent
                                            )
                                        ) {}
                                    }
                                }
                            }
                        }

                        ManualControlTab.WB -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = exp.isAutoWhiteBalance,
                                    onClick = { onUpdateExposure { it.copy(isAutoWhiteBalance = !it.isAutoWhiteBalance) } },
                                    label = { Text(if (exp.isAutoWhiteBalance) "AWB" else "KELVIN") },
                                    modifier = Modifier.testTag("wb_auto_toggle")
                                )
                                Slider(
                                    value = exp.whiteBalanceKelvin.toFloat().coerceIn(2400f, 9600f),
                                    onValueChange = { k ->
                                        val snapped = (k / 100f).roundToInt() * 100
                                        onUpdateExposure { it.copy(isAutoWhiteBalance = false, whiteBalanceKelvin = snapped) }
                                    },
                                    valueRange = 2400f..9600f,
                                    modifier = Modifier.weight(1f).testTag("wb_slider")
                                )
                            }
                            PrecisionRulerTicks()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryTabChip(
    title: String,
    value: String,
    selected: Boolean,
    isManual: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                selected -> MaterialTheme.colorScheme.primary
                isManual -> CinemaAmber.copy(alpha = 0.45f)
                else -> Color.Transparent
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isManual) CinemaAmber else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PrecisionRulerTicks() {
    val tickColor = MaterialTheme.colorScheme.outlineVariant
    val centerColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .padding(horizontal = 8.dp)
    ) {
        val count = 28
        val step = size.width / count
        for (i in 0..count) {
            val x = i * step
            val isMajor = i % 7 == 0
            val tickH = if (isMajor) size.height else size.height * 0.5f
            drawLine(
                color = if (i == count / 2) centerColor else tickColor,
                start = Offset(x, size.height - tickH),
                end = Offset(x, size.height),
                strokeWidth = if (isMajor) 2f else 1.2f
            )
        }
    }
}
