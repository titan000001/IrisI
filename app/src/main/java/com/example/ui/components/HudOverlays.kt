package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.ActiveSheet
import com.example.viewmodel.CineRigUiState

@Composable
fun LiveHistogramWidget(
    data: HistogramData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(126.dp)
            .height(64.dp)
            .testTag("hud_histogram_widget")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HIST",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (data.shadowCrushPct > 4f) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(HistogramBlue)
                        )
                    }
                    if (data.highlightClipPct > 2f) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(RecordRed)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val bins = data.lumaBins.size
                if (bins < 2) return@Canvas
                val dx = w / (bins - 1)

                fun drawChannel(values: FloatArray, strokeColor: Color, fillAlpha: Float) {
                    val path = Path().apply {
                        moveTo(0f, h)
                        for (i in values.indices) {
                            val x = i * dx
                            val y = h - (values[i].coerceIn(0f, 1f) * h)
                            lineTo(x, y)
                        }
                        lineTo(w, h)
                        close()
                    }
                    drawPath(path, color = strokeColor.copy(alpha = fillAlpha))
                    val linePath = Path().apply {
                        for (i in values.indices) {
                            val x = i * dx
                            val y = h - (values[i].coerceIn(0f, 1f) * h)
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                    }
                    drawPath(linePath, color = strokeColor, style = Stroke(width = 1.4f))
                }

                drawChannel(data.redBins, RecordRed.copy(alpha = 0.85f), 0.14f)
                drawChannel(data.greenBins, HistogramGreen.copy(alpha = 0.85f), 0.14f)
                drawChannel(data.blueBins, HistogramBlue.copy(alpha = 0.85f), 0.14f)
                drawChannel(data.lumaBins, Color.White.copy(alpha = 0.9f), 0.20f)
            }
        }
    }
}

@Composable
fun LiveAudioMeterWidget(
    levels: AudioLevels,
    activeDevice: AudioInputDeviceInfo?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barColor by animateColorAsState(
        targetValue = when {
            levels.clipping -> RecordRed
            levels.peakDb > -8f -> ZebraWarning
            else -> HistogramGreen
        },
        label = "audioMeterColor"
    )
    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .testTag("hud_audio_meter")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
        border = BorderStroke(
            1.dp,
            if (activeDevice?.isExternal == true) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (activeDevice?.isExternal == true) Icons.Rounded.SettingsInputComponent else Icons.Rounded.Mic,
                contentDescription = "Audio input meter",
                tint = if (activeDevice?.isExternal == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(14.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(levels.leftNormalized)
                            .clip(CircleShape)
                            .background(barColor)
                    )
                }
                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(levels.rightNormalized)
                            .clip(CircleShape)
                            .background(barColor)
                    )
                }
            }
            Text(
                text = "${levels.peakDb.toInt()}dB",
                style = MaterialTheme.typography.labelSmall,
                color = if (levels.clipping) RecordRed else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FocusMagnifierLoupe(
    loupeBitmap: Bitmap?,
    zoomFactor: Float,
    onCycleZoom: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(138.dp)
            .testTag("focus_magnifier_loupe"),
        shape = CircleShape,
        color = ObsidianSurface,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary),
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onCycleZoom)
        ) {
            if (loupeBitmap != null && !loupeBitmap.isRecycled) {
                Image(
                    bitmap = loupeBitmap.asImageBitmap(),
                    contentDescription = "Focus magnification loupe",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Precision center reticle
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = 12.dp.toPx()
                drawLine(PeakingCyan, Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = 1.5f)
                drawLine(PeakingCyan, Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = 1.5f)
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                shape = CircleShape,
                color = ObsidianBackground.copy(alpha = 0.85f)
            ) {
                Text(
                    text = "${zoomFactor.toInt()}x LOUPE",
                    style = MaterialTheme.typography.labelSmall,
                    color = PeakingCyan,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .background(ObsidianBackground.copy(alpha = 0.75f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close magnifier",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun FrameGuideOverlay(
    frameGuide: FrameGuide,
    modifier: Modifier = Modifier
) {
    if (frameGuide == FrameGuide.NONE) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val lineColor = Color.White.copy(alpha = 0.34f)

        if (frameGuide == FrameGuide.RULE_OF_THIRDS) {
            drawLine(lineColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.2f)
            drawLine(lineColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = 1.2f)
            drawLine(lineColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.2f)
            drawLine(lineColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = 1.2f)
            return@Canvas
        }

        val targetRatio = frameGuide.aspectRatio ?: return@Canvas
        // Viewfinder is typically portrait on phones; compute centered crop frame
        val viewRatio = w / h
        val frameW: Float
        val frameH: Float
        if (targetRatio > viewRatio) {
            frameW = w
            frameH = (w / targetRatio).coerceAtMost(h)
        } else {
            frameH = h
            frameW = (h * targetRatio).coerceAtMost(w)
        }
        val left = (w - frameW) / 2f
        val top = (h - frameH) / 2f
        val maskColor = Color.Black.copy(alpha = 0.50f)

        if (top > 1f) {
            drawRect(maskColor, Offset.Zero, Size(w, top))
            drawRect(maskColor, Offset(0f, top + frameH), Size(w, h - (top + frameH)))
        }
        if (left > 1f) {
            drawRect(maskColor, Offset(0f, top), Size(left, frameH))
            drawRect(maskColor, Offset(left + frameW, top), Size(w - (left + frameW), frameH))
        }
        drawRect(
            color = CinemaAmber.copy(alpha = 0.72f),
            topLeft = Offset(left, top),
            size = Size(frameW, frameH),
            style = Stroke(width = 1.8f)
        )
    }
}

@Composable
fun HudWidgetRenderer(
    widgetId: HudWidgetId,
    state: CineRigUiState,
    onToggleHistogram: () -> Unit,
    onTogglePeaking: () -> Unit,
    onToggleMagnifier: () -> Unit,
    onToggleNightBoost: () -> Unit,
    onCycleFrameGuide: () -> Unit,
    onOpenSheet: (ActiveSheet) -> Unit,
    onDispatchAction: (AssignableAction) -> Unit,
    onMoveZone: (() -> Unit)? = null
) {
    val editHighlight = state.isLayoutEditMode
    val boxModifier = if (editHighlight && onMoveZone != null) {
        Modifier
            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
            .clickable(onClick = onMoveZone)
    } else {
        Modifier
    }

    Box(modifier = boxModifier) {
        when (widgetId) {
            HudWidgetId.HISTOGRAM -> {
                if (state.liveAnalysisAvailable && (state.scopeMode != MonitorScopeMode.OFF || state.histogramEnabled || state.isLayoutEditMode)) {
                    MonitorScopeWidget(
                        scopeMode = if (state.scopeMode == MonitorScopeMode.OFF) MonitorScopeMode.HISTOGRAM else state.scopeMode,
                        data = state.histogramData,
                        onCycleScope = if (editHighlight && onMoveZone != null) onMoveZone else onToggleHistogram
                    )
                } else {
                    HudPillChip(
                        icon = Icons.Rounded.BarChart,
                        label = "SCOPES",
                        active = false,
                        testTag = "hud_hist_collapsed",
                        onClick = onToggleHistogram
                    )
                }
            }
            HudWidgetId.AUDIO_METER -> {
                val activeDev = state.audioDevices.firstOrNull { it.id == state.selectedAudioDeviceId }
                LiveAudioMeterWidget(
                    levels = state.audioLevels,
                    activeDevice = activeDev,
                    onClick = if (editHighlight && onMoveZone != null) onMoveZone else {
                        { onOpenSheet(ActiveSheet.AUDIO_MONITOR) }
                    }
                )
            }
            HudWidgetId.STABILIZATION_CHIP -> {
                HudPillChip(
                    icon = when (state.stabilizationMode) {
                        StabilizationMode.OFF -> Icons.Rounded.Videocam
                        StabilizationMode.STANDARD -> Icons.Rounded.VideoStable
                        StabilizationMode.ENHANCED -> Icons.Rounded.AutoAwesomeMotion
                        StabilizationMode.ACTION -> Icons.AutoMirrored.Rounded.DirectionsRun
                    },
                    label = "STAB ${state.stabilizationMode.shortCode}",
                    active = state.stabilizationMode != StabilizationMode.OFF,
                    testTag = "hud_stab_chip",
                    onClick = if (editHighlight && onMoveZone != null) onMoveZone else {
                        { onOpenSheet(ActiveSheet.VIDEO_STAB) }
                    }
                )
            }
            HudWidgetId.LOG_PROFILE_CHIP -> {
                val isPhoto = state.captureMode == CaptureMode.PHOTO || state.captureMode == CaptureMode.NIGHT
                val labelText = if (isPhoto) {
                    if (state.filmPreset == FilmPreset.NONE) "NATURAL" else state.filmPreset.displayName
                } else {
                    val filmSuffix = if (state.filmPreset != FilmPreset.NONE) "·${state.filmPreset.shortCode}" else ""
                    "${state.logProfile.shortName}$filmSuffix"
                }
                HudPillChip(
                    icon = if (isPhoto) Icons.Rounded.CameraRoll else Icons.Rounded.Palette,
                    label = labelText,
                    active = state.logProfile != LogProfile.REC_709 || state.filmPreset != FilmPreset.NONE,
                    activeTint = if (isPhoto && state.filmPreset != FilmPreset.NONE) state.filmPreset.swatchColor else MaterialTheme.colorScheme.primary,
                    testTag = "hud_log_chip",
                    onClick = if (editHighlight && onMoveZone != null) onMoveZone else {
                        { onOpenSheet(ActiveSheet.COLOR_GRADE) }
                    }
                )
            }
            HudWidgetId.PEAKING_CHIP -> {
                HudPillChip(
                    icon = Icons.Rounded.CenterFocusStrong,
                    label = "PEAK",
                    active = state.focusPeakingEnabled,
                    activeTint = state.peakingColor.color,
                    testTag = "hud_peaking_chip",
                    onClick = if (editHighlight && onMoveZone != null) onMoveZone else onTogglePeaking
                )
            }
            HudWidgetId.MAGNIFIER_CHIP -> {
                HudPillChip(
                    icon = Icons.Rounded.ZoomIn,
                    label = if (state.focusMagnifierEnabled) "${state.loupeZoomFactor.toInt()}x" else "LOUPE",
                    active = state.focusMagnifierEnabled,
                    activeTint = PeakingCyan,
                    testTag = "hud_loupe_chip",
                    onClick = if (editHighlight && onMoveZone != null) onMoveZone else onToggleMagnifier
                )
            }
            HudWidgetId.FORMAT_BADGE -> {
                val text = if (state.captureMode == CaptureMode.VIDEO || state.captureMode == CaptureMode.RAW_VIDEO) {
                    "${state.videoResolution.shortLabel} ${state.videoFps.label} ${state.videoBitrate.mbps}M"
                } else {
                    state.photoFormat.badge
                }
                HudPillChip(
                    icon = Icons.Rounded.HighQuality,
                    label = text,
                    active = state.videoResolution.isOpenGate || state.photoFormat != PhotoFormat.JPEG,
                    testTag = "hud_format_chip",
                    onClick = if (editHighlight && onMoveZone != null) onMoveZone else {
                        { onOpenSheet(ActiveSheet.VIDEO_STAB) }
                    }
                )
            }
            HudWidgetId.NIGHT_BOOST_CHIP -> {
                HudPillChip(
                    icon = Icons.Rounded.NightsStay,
                    label = "NIGHT",
                    active = state.nightBoostEnabled || state.captureMode == CaptureMode.NIGHT,
                    activeTint = Color(0xFF94B8FF),
                    testTag = "hud_night_chip",
                    onClick = if (editHighlight && onMoveZone != null) onMoveZone else onToggleNightBoost
                )
            }
            HudWidgetId.GRID_GUIDE_CHIP -> {
                HudPillChip(
                    icon = Icons.Rounded.GridOn,
                    label = state.frameGuide.label,
                    active = state.frameGuide != FrameGuide.NONE,
                    testTag = "hud_grid_chip",
                    onClick = if (editHighlight && onMoveZone != null) onMoveZone else onCycleFrameGuide
                )
            }
            HudWidgetId.FN_BUTTON_1 -> {
                HudFnButton(
                    fnTag = "Fn1",
                    action = state.activeLayout.fn1Action,
                    onClick = if (editHighlight && onMoveZone != null) onMoveZone else {
                        { onDispatchAction(state.activeLayout.fn1Action) }
                    }
                )
            }
            HudWidgetId.FN_BUTTON_2 -> {
                HudFnButton(
                    fnTag = "Fn2",
                    action = state.activeLayout.fn2Action,
                    onClick = if (editHighlight && onMoveZone != null) onMoveZone else {
                        { onDispatchAction(state.activeLayout.fn2Action) }
                    }
                )
            }
            HudWidgetId.FN_BUTTON_3 -> {
                HudFnButton(
                    fnTag = "Fn3",
                    action = state.activeLayout.fn3Action,
                    onClick = if (editHighlight && onMoveZone != null) onMoveZone else {
                        { onDispatchAction(state.activeLayout.fn3Action) }
                    }
                )
            }
        }
    }
}

@Composable
fun HudPillChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    activeTint: Color = MaterialTheme.colorScheme.primary,
    testTag: String,
    onClick: () -> Unit
) {
    val containerColor = if (active) {
        activeTint.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f)
    }
    val contentColor = if (active) activeTint else MaterialTheme.colorScheme.onSurface
    val borderColor = if (active) activeTint.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

    Surface(
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp)
            .testTag(testTag)
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun HudFnButton(
    fnTag: String,
    action: AssignableAction,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
            .testTag("fn_button_$fnTag")
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.84f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = fnTag,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = action.shortCode,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun FilmLookQuickStrip(
    selectedPreset: FilmPreset,
    grainAmount: Float,
    onSelectPreset: (FilmPreset) -> Unit,
    onOpenColorLab: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(FilmPreset.entries, key = { it.name }) { preset ->
            val selected = selectedPreset == preset
            Surface(
                modifier = Modifier
                    .defaultMinSize(minHeight = 34.dp)
                    .testTag("quick_film_${preset.name}")
                    .clickable { onSelectPreset(preset) },
                shape = CircleShape,
                color = if (selected) preset.swatchColor.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.80f),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) preset.swatchColor
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(preset.swatchColor)
                    )
                    Text(
                        text = if (preset == FilmPreset.NONE) "Natural" else preset.displayName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) preset.swatchColor else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        item {
            Surface(
                modifier = Modifier
                    .defaultMinSize(minHeight = 34.dp)
                    .testTag("quick_grain_lab")
                    .clickable(onClick = onOpenColorLab),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.80f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Grain,
                        contentDescription = "Adjust film grain",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${(grainAmount * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
