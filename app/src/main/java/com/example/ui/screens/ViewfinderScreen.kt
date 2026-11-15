package com.example.ui.screens

import android.content.res.Configuration
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.R
import com.example.camera.ColorEngine
import com.example.model.*
import com.example.ui.components.*
import com.example.ui.sheets.*
import com.example.ui.theme.*
import com.example.viewmodel.ActiveSheet
import com.example.viewmodel.CineRigUiState
import com.example.viewmodel.CineRigViewModel
import kotlin.random.Random

@Composable
fun ViewfinderScreen(
    state: CineRigUiState,
    viewModel: CineRigViewModel,
    onRequestPermissions: () -> Unit
) {
    BackHandler(enabled = state.activeSheet != ActiveSheet.NONE || state.isLayoutEditMode) {
        if (state.activeSheet != ActiveSheet.NONE) {
            viewModel.closeSheet()
        } else if (state.isLayoutEditMode) {
            viewModel.toggleLayoutEditMode()
        }
    }

    if (!state.cameraGranted) {
        PermissionGateScreen(onRequestPermissions = onRequestPermissions)
        return
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var widgetBeingEdited by remember { mutableStateOf<HudWidgetId?>(null) }
    var bypassingColorLook by remember { mutableStateOf(false) }

    val isColorWorkbenchOpen = state.activeSheet == ActiveSheet.COLOR_GRADE
    val activeBypass = isColorWorkbenchOpen && bypassingColorLook
    val isPhotoOrNight = state.captureMode == CaptureMode.PHOTO || state.captureMode == CaptureMode.NIGHT
    val effectiveLogProfile = if (isPhotoOrNight) LogProfile.REC_709 else state.logProfile

    val activeColorMatrix = remember(effectiveLogProfile, state.filmPreset, state.colorGrading, activeBypass) {
        if (activeBypass) {
            android.graphics.ColorMatrix()
        } else {
            ColorEngine.buildColorMatrix(effectiveLogProfile, state.filmPreset, state.colorGrading)
        }
    }

    val targetAspect = remember(state.captureMode, state.photoAspectRatio, state.videoResolution, isLandscape) {
        when (state.captureMode) {
            CaptureMode.PHOTO -> if (isLandscape) state.photoAspectRatio.ratioLandscape else state.photoAspectRatio.ratioPortrait
            CaptureMode.NIGHT -> if (isLandscape) 4f / 3f else 3f / 4f
            CaptureMode.VIDEO, CaptureMode.RAW_VIDEO -> {
                if (state.videoResolution.isOpenGate) {
                    if (isLandscape) 4f / 3f else 3f / 4f
                } else {
                    if (isLandscape) 16f / 9f else 9f / 16f
                }
            }
        }
    }
    val animatedAspect by animateFloatAsState(
        targetValue = targetAspect,
        animationSpec = spring(stiffness = 380f),
        label = "viewfinderAspect"
    )

    val aspectBadgeLabel = when (state.captureMode) {
        CaptureMode.PHOTO -> state.photoAspectRatio.label
        CaptureMode.NIGHT -> "4:3"
        CaptureMode.VIDEO, CaptureMode.RAW_VIDEO -> if (state.videoResolution.isOpenGate) "4:3" else "16:9"
    }

    fun isVideoOnlyAction(action: AssignableAction): Boolean = when (action) {
        AssignableAction.TOGGLE_MONITOR_LUT,
        AssignableAction.CYCLE_LOG_PROFILE,
        AssignableAction.CYCLE_STABILIZATION,
        AssignableAction.TOGGLE_OPEN_GATE,
        AssignableAction.OPEN_AUDIO_PANEL -> true
        else -> false
    }

    fun shouldShowWidget(widgetId: HudWidgetId): Boolean {
        if (state.isLayoutEditMode) return true
        if (isPhotoOrNight) {
            return when (widgetId) {
                HudWidgetId.STABILIZATION_CHIP,
                HudWidgetId.AUDIO_METER,
                HudWidgetId.LOG_PROFILE_CHIP -> false
                HudWidgetId.FN_BUTTON_1 -> !isVideoOnlyAction(state.activeLayout.fn1Action)
                HudWidgetId.FN_BUTTON_2 -> !isVideoOnlyAction(state.activeLayout.fn2Action)
                HudWidgetId.FN_BUTTON_3 -> !isVideoOnlyAction(state.activeLayout.fn3Action)
                else -> true
            }
        }
        return true
    }

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                TopHudBar(
                    state = state,
                    aspectBadgeLabel = aspectBadgeLabel,
                    shouldShowWidget = ::shouldShowWidget,
                    viewModel = viewModel,
                    onEditWidget = { widgetBeingEdited = it }
                )
                OpticalViewfinderCanvas(
                    state = state,
                    animatedAspect = animatedAspect,
                    activeColorMatrix = activeColorMatrix,
                    effectiveLogProfile = effectiveLogProfile,
                    isColorWorkbenchOpen = isColorWorkbenchOpen,
                    activeBypass = activeBypass,
                    onBypassChange = { bypassingColorLook = it },
                    shouldShowWidget = ::shouldShowWidget,
                    onEditWidget = { widgetBeingEdited = it },
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            Column(
                modifier = Modifier
                    .width(324.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                CameraConsoleSection(
                    state = state,
                    isPhotoOrNight = isPhotoOrNight,
                    isColorWorkbenchOpen = isColorWorkbenchOpen,
                    viewModel = viewModel
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBackground)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopHudBar(
                state = state,
                aspectBadgeLabel = aspectBadgeLabel,
                shouldShowWidget = ::shouldShowWidget,
                viewModel = viewModel,
                onEditWidget = { widgetBeingEdited = it }
            )

            OpticalViewfinderCanvas(
                state = state,
                animatedAspect = animatedAspect,
                activeColorMatrix = activeColorMatrix,
                effectiveLogProfile = effectiveLogProfile,
                isColorWorkbenchOpen = isColorWorkbenchOpen,
                activeBypass = activeBypass,
                onBypassChange = { bypassingColorLook = it },
                shouldShowWidget = ::shouldShowWidget,
                onEditWidget = { widgetBeingEdited = it },
                viewModel = viewModel,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
            )

            CameraConsoleSection(
                state = state,
                isPhotoOrNight = isPhotoOrNight,
                isColorWorkbenchOpen = isColorWorkbenchOpen,
                viewModel = viewModel
            )
        }
    }

    // Quick Zone Reassignment Dialog when tapping a HUD widget in Live Edit Mode
    widgetBeingEdited?.let { wId ->
        AlertDialog(
            onDismissRequest = { widgetBeingEdited = null },
            title = { Text("Move ${wId.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose where this control sits on your HUD:", style = MaterialTheme.typography.bodySmall)
                    HudSlotZone.entries.forEach { zone ->
                        FilledTonalButton(
                            onClick = {
                                viewModel.moveWidgetToZone(wId, zone)
                                widgetBeingEdited = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(zone.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { widgetBeingEdited = null }) { Text("Cancel") }
            }
        )
    }

    when (state.activeSheet) {
        ActiveSheet.COLOR_GRADE -> Unit // Rendered inline inside ColorScienceWorkbench
        ActiveSheet.VIDEO_STAB -> VideoAndStabSheet(
            state = state,
            onSelectStabilization = viewModel::setStabilizationMode,
            onSelectResolution = viewModel::setVideoResolution,
            onSelectFps = viewModel::setVideoFps,
            onSelectBitrate = viewModel::setVideoBitrate,
            onSelectCodec = viewModel::setVideoCodecFormat,
            onSelectPhotoFormat = viewModel::setPhotoFormat,
            onSelectSequenceMode = viewModel::setPhotoSequenceMode,
            onCycleAspectRatio = viewModel::cyclePhotoAspectRatio,
            onSelectScopeMode = viewModel::setScopeMode,
            onToggleZebra = viewModel::toggleZebra,
            onToggleFalseColor = viewModel::toggleFalseColor,
            onToggleHorizon = viewModel::toggleHorizonLevel,
            onOpenHardwareInfo = { viewModel.openSheet(ActiveSheet.HARDWARE_INFO) },
            onDismiss = viewModel::closeSheet
        )
        ActiveSheet.AUDIO_MONITOR -> AudioMonitorSheet(
            state = state,
            onSelectInputDevice = viewModel.audioManager::selectInputDevice,
            onRefreshDevices = viewModel.audioManager::refreshDevices,
            onToggleMonitoring = viewModel.audioManager::setMonitoringEnabled,
            onInputGainChange = viewModel.audioManager::setInputGainDb,
            onDismiss = viewModel::closeSheet
        )
        ActiveSheet.UI_CUSTOMIZER -> UiCustomizerSheet(
            state = state,
            onSelectLayout = viewModel::selectLayout,
            onSaveAsNewLayout = viewModel::saveAsNewLayout,
            onDeleteLayout = viewModel::deleteLayout,
            onMoveWidgetToZone = viewModel::moveWidgetToZone,
            onReorderWidget = viewModel::reorderWidgetInZone,
            onUpdateBindings = viewModel::updateLayoutButtonBindings,
            onToggleDirectHudEdit = viewModel::toggleLayoutEditMode,
            onDismiss = viewModel::closeSheet
        )
        ActiveSheet.PRESETS -> PresetsSheet(
            state = state,
            onApplyPreset = viewModel::applyPreset,
            onSaveCurrentPreset = viewModel::saveCurrentAsPreset,
            onDeletePreset = viewModel::deletePreset,
            onDismiss = viewModel::closeSheet
        )
        ActiveSheet.VAULT -> MediaLibrarySheet(
            state = state,
            onToggleFavorite = viewModel::toggleMediaFavorite,
            onOpenDevelop = viewModel::openDevelopForMedia,
            onDeleteLog = viewModel::deleteMediaLog,
            onDismiss = viewModel::closeSheet
        )
        ActiveSheet.DEVELOP -> state.developTarget?.let { target ->
            DevelopLabSheet(
                target = target,
                dev = state.developSettings,
                onUpdate = viewModel::updateDevelopSettings,
                onExport = viewModel::exportDevelopedMedia,
                onDismiss = { viewModel.openSheet(ActiveSheet.VAULT) }
            )
        }
        ActiveSheet.HARDWARE_INFO -> HardwareCapabilitiesSheet(
            capabilities = state.deviceCapabilities,
            onDismiss = viewModel::closeSheet
        )
        ActiveSheet.NONE -> Unit
    }
}

@Composable
private fun TopHudBar(
    state: CineRigUiState,
    aspectBadgeLabel: String,
    shouldShowWidget: (HudWidgetId) -> Boolean,
    viewModel: CineRigViewModel,
    onEditWidget: (HudWidgetId) -> Unit
) {
    val isPhotoOrNight = state.captureMode == CaptureMode.PHOTO || state.captureMode == CaptureMode.NIGHT
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.isRecording) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (state.isRecordingPaused) CinemaAmber else RecordRed,
                    modifier = Modifier.testTag("recording_tally_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (state.isRecordingPaused) Color.Black else Color.White)
                        )
                        val prefix = if (state.isRecordingPaused) "PAUSED" else "REC"
                        val durText = CineRigViewModel.formatDuration(state.recordingDurationMs)
                        val sizeText = CineRigViewModel.formatBytes(state.recordedBytes)
                        val rawSuffix = if (state.videoCodecFormat == VideoCodecFormat.RAW_CINEMA_DNG) {
                            " • ${state.rawBurstFrameCount}F RAW"
                        } else ""
                        Text(
                            text = "$prefix $durText • $sizeText$rawSuffix",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (state.isRecordingPaused) Color.Black else Color.White
                        )
                    }
                }
                Text(
                    text = "Free ${CineRigViewModel.formatBytes(state.deviceCapabilities.availableStorageBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    modifier = Modifier
                        .testTag("aspect_ratio_badge")
                        .clickable {
                            if (state.captureMode == CaptureMode.PHOTO) viewModel.cyclePhotoAspectRatio()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AspectRatio,
                            contentDescription = "Aspect Ratio",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = aspectBadgeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                val topWidgets = remember(state.activeLayout, state.captureMode, state.isLayoutEditMode) {
                    state.activeLayout.placements
                        .filter { it.zone == HudSlotZone.TOP_BAR && shouldShowWidget(it.widgetId) }
                        .sortedBy { it.order }
                }
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    topWidgets.forEach { placement ->
                        HudWidgetRenderer(
                            widgetId = placement.widgetId,
                            state = state,
                            onToggleHistogram = viewModel::cycleScopeMode,
                            onTogglePeaking = viewModel::toggleFocusPeaking,
                            onToggleMagnifier = viewModel::toggleFocusMagnifier,
                            onToggleNightBoost = viewModel::toggleNightBoost,
                            onCycleFrameGuide = viewModel::cycleFrameGuide,
                            onOpenSheet = { sheet ->
                                if (placement.widgetId == HudWidgetId.FORMAT_BADGE && isPhotoOrNight) {
                                    viewModel.cyclePhotoFormat()
                                } else {
                                    viewModel.openSheet(sheet)
                                }
                            },
                            onDispatchAction = viewModel::dispatchAssignableAction,
                            onMoveZone = if (state.isLayoutEditMode) { { onEditWidget(placement.widgetId) } } else null
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.isLayoutEditMode) {
                FilledTonalButton(
                    onClick = { viewModel.toggleLayoutEditMode() },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp).testTag("exit_layout_edit_btn")
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Done", style = MaterialTheme.typography.labelSmall)
                }
            }
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .testTag("open_hardware_btn")
                    .clickable { viewModel.openSheet(ActiveSheet.HARDWARE_INFO) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Memory,
                        contentDescription = "Hardware capabilities",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .testTag("open_presets_btn")
                    .clickable { viewModel.openSheet(ActiveSheet.PRESETS) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Bookmarks,
                        contentDescription = stringResource(R.string.presets_button_desc),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .testTag("open_customizer_btn")
                    .clickable { viewModel.openSheet(ActiveSheet.UI_CUSTOMIZER) },
                shape = CircleShape,
                color = if (state.isLayoutEditMode) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.DashboardCustomize,
                        contentDescription = stringResource(R.string.customizer_button_desc),
                        tint = if (state.isLayoutEditMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OpticalViewfinderCanvas(
    state: CineRigUiState,
    animatedAspect: Float,
    activeColorMatrix: android.graphics.ColorMatrix,
    effectiveLogProfile: LogProfile,
    isColorWorkbenchOpen: Boolean,
    activeBypass: Boolean,
    onBypassChange: (Boolean) -> Unit,
    shouldShowWidget: (HudWidgetId) -> Boolean,
    onEditWidget: (HudWidgetId) -> Unit,
    viewModel: CineRigViewModel,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var viewfinderSize by remember { mutableStateOf(IntSize(1, 1)) }
    var tapReticlePos by remember { mutableStateOf<Offset?>(null) }

    val shutterFlashAlpha = remember { Animatable(0f) }
    LaunchedEffect(state.shutterFlashTick) {
        if (state.shutterFlashTick > 0) {
            shutterFlashAlpha.snapTo(0.60f)
            shutterFlashAlpha.animateTo(0f, animationSpec = tween(160))
        }
    }

    val isEmulator = remember {
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val containerRatio = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 1f
        val fitByHeight = containerRatio > animatedAspect
        Box(
            modifier = Modifier
                .then(if (fitByHeight) Modifier.fillMaxHeight() else Modifier.fillMaxWidth())
                .aspectRatio(animatedAspect)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .onSizeChanged { viewfinderSize = it }
                .pointerInput(state.zoomRatio, state.selectedLens) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) {
                            viewModel.setZoomRatio(state.zoomRatio * zoom)
                        }
                    }
                }
                .pointerInput(viewfinderSize) {
                    detectTapGestures { offset ->
                        if (viewfinderSize.width > 0 && viewfinderSize.height > 0) {
                            val nx = (offset.x / viewfinderSize.width).coerceIn(0f, 1f)
                            val ny = (offset.y / viewfinderSize.height).coerceIn(0f, 1f)
                            tapReticlePos = offset
                            viewModel.tapToFocus(nx, ny, viewfinderSize.width.toFloat(), viewfinderSize.height.toFloat())
                        }
                    }
                }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        implementationMode = if (isEmulator) {
                            PreviewView.ImplementationMode.PERFORMANCE
                        } else {
                            PreviewView.ImplementationMode.COMPATIBLE
                        }
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        viewModel.attachPreview(lifecycleOwner, this)
                    }
                },
                update = { pv ->
                    if (!isEmulator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val effect = RenderEffect.createColorFilterEffect(
                            ColorMatrixColorFilter(activeColorMatrix)
                        )
                        pv.setRenderEffect(effect)
                    }
                }
            )

            // Emulator fallback color preview
            if (!activeBypass && isEmulator && (state.filmPreset != FilmPreset.NONE || (effectiveLogProfile != LogProfile.REC_709 && !state.colorGrading.monitoringLutEnabled))) {
                val tintColor = if (effectiveLogProfile != LogProfile.REC_709 && !state.colorGrading.monitoringLutEnabled) {
                    Color.Gray.copy(alpha = 0.20f)
                } else {
                    state.filmPreset.swatchColor.copy(alpha = 0.14f)
                }
                Box(modifier = Modifier.fillMaxSize().background(tintColor))
            }

            // Vignette & Film Grain Preview
            val showVignette = !activeBypass && state.colorGrading.vignette > 0.02f &&
                (effectiveLogProfile == LogProfile.REC_709 || state.colorGrading.monitoringLutEnabled)
            val grainStrength = if (activeBypass || (effectiveLogProfile != LogProfile.REC_709 && !state.colorGrading.monitoringLutEnabled)) 0f
            else state.colorGrading.grainAmount

            if (showVignette || grainStrength > 0.04f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (showVignette) {
                        val alpha = (state.colorGrading.vignette * 0.65f).coerceIn(0f, 0.70f)
                        drawRect(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = alpha)),
                                radius = size.maxDimension * 0.72f
                            )
                        )
                    }
                    if (grainStrength > 0.04f) {
                        val dotCount = (grainStrength * 160).toInt().coerceIn(8, 160)
                        val rng = Random(System.currentTimeMillis() / 110L)
                        val dotRadius = state.colorGrading.grainSize
                        for (i in 0 until dotCount) {
                            val x = rng.nextFloat() * size.width
                            val y = rng.nextFloat() * size.height
                            val light = rng.nextBoolean()
                            drawCircle(
                                color = (if (light) Color.White else Color.Black).copy(alpha = grainStrength * 0.12f),
                                radius = dotRadius,
                                center = Offset(x, y)
                            )
                        }
                    }
                }
            }

            // False Color Exposure Overlay
            if (state.falseColorEnabled && state.falseColorBitmap != null && !state.falseColorBitmap.isRecycled) {
                Image(
                    bitmap = state.falseColorBitmap.asImageBitmap(),
                    contentDescription = "False Color exposure map",
                    contentScale = ContentScale.Crop,
                    alpha = 0.85f,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Zebra Over-Exposure Stripe Overlay
            if (state.zebraEnabled && state.zebraBitmap != null && !state.zebraBitmap.isRecycled) {
                Image(
                    bitmap = state.zebraBitmap.asImageBitmap(),
                    contentDescription = "Zebra clipping pattern",
                    contentScale = ContentScale.Crop,
                    alpha = 0.90f,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Focus Peaking Edge Overlay
            if (state.focusPeakingEnabled && state.peakingBitmap != null && !state.peakingBitmap.isRecycled) {
                Image(
                    bitmap = state.peakingBitmap.asImageBitmap(),
                    contentDescription = "Focus peaking highlight",
                    contentScale = ContentScale.Crop,
                    alpha = 0.85f,
                    modifier = Modifier.fillMaxSize()
                )
            }

            FrameGuideOverlay(frameGuide = state.frameGuide)

            if (state.horizonLevelEnabled) {
                HorizonLevelOverlay(rollDegrees = state.horizonRollDeg)
            }

            OpticalViewfinderBrackets(
                accentColor = when (state.captureMode) {
                    CaptureMode.NIGHT -> Color(0xFF94B8FF).copy(alpha = 0.50f)
                    CaptureMode.RAW_VIDEO -> CinemaAmber.copy(alpha = 0.50f)
                    else -> Color.White.copy(alpha = 0.34f)
                }
            )

            tapReticlePos?.let { pos ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val boxSize = 52.dp.toPx()
                    drawRect(
                        color = CinemaAmber,
                        topLeft = Offset(pos.x - boxSize / 2f, pos.y - boxSize / 2f),
                        size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                }
            }

            if (shutterFlashAlpha.value > 0.01f) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = shutterFlashAlpha.value)))
            }

            // LEFT_DOCK & RIGHT_DOCK HUD Placements
            val leftWidgets = remember(state.activeLayout, state.captureMode, state.isLayoutEditMode, state.histogramEnabled, state.scopeMode) {
                state.activeLayout.placements
                    .filter { it.zone == HudSlotZone.LEFT_DOCK && shouldShowWidget(it.widgetId) }
                    .filter { it.widgetId != HudWidgetId.HISTOGRAM || state.histogramEnabled || state.scopeMode != MonitorScopeMode.OFF || state.isLayoutEditMode }
                    .sortedBy { it.order }
            }
            val rightWidgets = remember(state.activeLayout, state.captureMode, state.isLayoutEditMode) {
                state.activeLayout.placements
                    .filter { it.zone == HudSlotZone.RIGHT_DOCK && shouldShowWidget(it.widgetId) }
                    .sortedBy { it.order }
            }

            val scopeAlreadyShownInDock = leftWidgets.any { it.widgetId == HudWidgetId.HISTOGRAM } ||
                rightWidgets.any { it.widgetId == HudWidgetId.HISTOGRAM }

            if (leftWidgets.isNotEmpty() || (state.scopeMode != MonitorScopeMode.OFF && !scopeAlreadyShownInDock)) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    if (state.scopeMode != MonitorScopeMode.OFF && !scopeAlreadyShownInDock) {
                        MonitorScopeWidget(
                            scopeMode = state.scopeMode,
                            data = state.histogramData,
                            onCycleScope = viewModel::cycleScopeMode
                        )
                    }
                    leftWidgets.forEach { placement ->
                        HudWidgetRenderer(
                            widgetId = placement.widgetId,
                            state = state,
                            onToggleHistogram = viewModel::cycleScopeMode,
                            onTogglePeaking = viewModel::toggleFocusPeaking,
                            onToggleMagnifier = viewModel::toggleFocusMagnifier,
                            onToggleNightBoost = viewModel::toggleNightBoost,
                            onCycleFrameGuide = viewModel::cycleFrameGuide,
                            onOpenSheet = viewModel::openSheet,
                            onDispatchAction = viewModel::dispatchAssignableAction,
                            onMoveZone = if (state.isLayoutEditMode) { { onEditWidget(placement.widgetId) } } else null
                        )
                    }
                }
            }

            if (rightWidgets.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    rightWidgets.forEach { placement ->
                        HudWidgetRenderer(
                            widgetId = placement.widgetId,
                            state = state,
                            onToggleHistogram = viewModel::cycleScopeMode,
                            onTogglePeaking = viewModel::toggleFocusPeaking,
                            onToggleMagnifier = viewModel::toggleFocusMagnifier,
                            onToggleNightBoost = viewModel::toggleNightBoost,
                            onCycleFrameGuide = viewModel::cycleFrameGuide,
                            onOpenSheet = viewModel::openSheet,
                            onDispatchAction = viewModel::dispatchAssignableAction,
                            onMoveZone = if (state.isLayoutEditMode) { { onEditWidget(placement.widgetId) } } else null
                        )
                    }
                }
            }

            if (state.falseColorEnabled) {
                FalseColorLegendBar(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                )
            }

            AnimatedVisibility(
                visible = state.focusMagnifierEnabled,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                FocusMagnifierLoupe(
                    loupeBitmap = state.loupeBitmap,
                    zoomFactor = state.loupeZoomFactor,
                    onCycleZoom = viewModel::cycleLoupeZoom,
                    onClose = viewModel::toggleFocusMagnifier
                )
            }

            // Self-Timer Countdown Overlay
            if (state.timerCountdownSec > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.Center).testTag("self_timer_countdown")
                ) {
                    Text(
                        text = "${state.timerCountdownSec}",
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                        color = CinemaAmber,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp)
                    )
                }
            }

            // Stacking / Bracketing / Interval Progress Pill
            val progressText = state.captureProgressLabel ?: if (state.intervalActive) "INTERVAL SHOT #${state.intervalShotCount}" else null
            if (progressText != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).testTag("capture_progress_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            LaunchedEffect(state.statusMessage) {
                if (state.statusMessage != null) {
                    kotlinx.coroutines.delay(2200L)
                    viewModel.clearStatusMessage()
                }
            }
            AnimatedVisibility(
                visible = state.statusMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
            ) {
                state.statusMessage?.let { msg ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f)
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            if (isColorWorkbenchOpen) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .testTag("color_compare_hold_pill")
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onBypassChange(true)
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        onBypassChange(false)
                                    }
                                }
                            )
                        },
                    shape = CircleShape,
                    color = if (activeBypass) CinemaAmber else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, if (activeBypass) CinemaAmber else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Compare,
                            contentDescription = "Hold to compare before and after color grading",
                            tint = if (activeBypass) Color.Black else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (activeBypass) "ORIGINAL" else "Hold Compare",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (activeBypass) Color.Black else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraConsoleSection(
    state: CineRigUiState,
    isPhotoOrNight: Boolean,
    isColorWorkbenchOpen: Boolean,
    viewModel: CineRigViewModel
) {
    if (isColorWorkbenchOpen) {
        ColorScienceWorkbench(
            state = state,
            onSelectLogProfile = viewModel::setLogProfile,
            onSelectFilmPreset = viewModel::setFilmPreset,
            onUpdateGrading = viewModel::updateColorGrading,
            onToggleHistogram = viewModel::cycleScopeMode,
            onDone = viewModel::closeSheet,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LensAndZoomBar(
            lenses = state.lenses,
            selectedLens = state.selectedLens,
            zoomRatio = state.zoomRatio,
            zoomSliderExpanded = state.zoomSliderExpanded,
            isPhotoIdentity = isPhotoOrNight,
            onSelectLens = viewModel::selectLens,
            onZoomChange = viewModel::setZoomRatio,
            onToggleZoomSlider = viewModel::toggleZoomSlider
        )

        ModeUniqueQuickStrip(state = state, viewModel = viewModel)

        ManualControlsDeck(
            state = state,
            onSelectProgram = viewModel::setExposureProgram,
            onSelectTab = viewModel::selectManualTab,
            onToggleExpanded = viewModel::toggleManualDeck,
            onUpdateExposure = viewModel::updateExposure,
            onTogglePeaking = viewModel::toggleFocusPeaking,
            onSelectPeakingColor = viewModel::setPeakingColor,
            onToggleMagnifier = viewModel::toggleFocusMagnifier
        )

        // Mode Switcher (PHOTO • VIDEO • NIGHT • RAW VID)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CaptureMode.entries.forEach { mode ->
                val selected = state.captureMode == mode
                Surface(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 32.dp)
                        .testTag("mode_tab_${mode.name}")
                        .clickable(enabled = !state.isRecording) { viewModel.setCaptureMode(mode) },
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Bottom Action Bar Adapted per Mode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Local Media Library Vault Thumbnail
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .testTag("vault_button")
                    .clickable { viewModel.openSheet(ActiveSheet.VAULT) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val thumb = state.lastCapturedThumbUri
                    if (!thumb.isNullOrBlank()) {
                        AsyncImage(
                            model = Uri.parse(thumb),
                            contentDescription = stringResource(R.string.vault_button_desc),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Rounded.PhotoLibrary,
                            contentDescription = stringResource(R.string.vault_button_desc),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 2. Color Science Workbench / Pause Recording when recording video
            if (state.isRecording) {
                IconButton(
                    onClick = viewModel::pauseOrResumeRecording,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .testTag("pause_resume_record_button")
                ) {
                    Icon(
                        imageVector = if (state.isRecordingPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = if (state.isRecordingPaused) "Resume recording" else "Pause recording",
                        tint = CinemaAmber
                    )
                }
            } else {
                IconButton(
                    onClick = { viewModel.openSheet(ActiveSheet.COLOR_GRADE) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .testTag("color_grade_button")
                ) {
                    Icon(
                        imageVector = if (isPhotoOrNight) Icons.Rounded.CameraRoll else Icons.Rounded.Palette,
                        contentDescription = stringResource(R.string.color_grade_button_desc),
                        tint = if (state.filmPreset != FilmPreset.NONE || state.logProfile != LogProfile.REC_709) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            // 3. Expressive Material 3 Shutter / Record Button
            val isVideoMode = state.captureMode == CaptureMode.VIDEO || state.captureMode == CaptureMode.RAW_VIDEO
            val isActiveCapture = state.isRecording || state.intervalActive
            val innerSize by animateDpAsState(
                targetValue = if (isActiveCapture) 26.dp else 54.dp,
                animationSpec = spring(),
                label = "shutterInnerSize"
            )
            val innerCorner by animateDpAsState(
                targetValue = if (isActiveCapture) 8.dp else 27.dp,
                animationSpec = spring(),
                label = "shutterInnerCorner"
            )
            val innerColor by animateColorAsState(
                targetValue = when {
                    isVideoMode -> RecordRed
                    state.intervalActive -> CinemaAmber
                    state.captureMode == CaptureMode.NIGHT -> Color(0xFF94B8FF)
                    else -> Color.White
                },
                label = "shutterInnerColor"
            )
            val ringColor = when (state.captureMode) {
                CaptureMode.NIGHT -> Color(0xFF94B8FF).copy(alpha = 0.85f)
                CaptureMode.RAW_VIDEO -> CinemaAmber.copy(alpha = 0.9f)
                else -> Color.White.copy(alpha = 0.88f)
            }

            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .border(3.dp, ringColor, CircleShape)
                    .clickable { viewModel.triggerCapture() }
                    .testTag("shutter_record_button"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(innerSize)
                        .clip(RoundedCornerShape(innerCorner))
                        .background(innerColor)
                )
            }

            // 4. Mode Settings & Monitoring Sheet (Adapts seamlessly between Photo Settings and Video/Stab)
            IconButton(
                onClick = { viewModel.openSheet(ActiveSheet.VIDEO_STAB) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .testTag("video_stab_button")
            ) {
                Icon(
                    imageVector = if (isPhotoOrNight) Icons.Rounded.Tune else Icons.Rounded.VideoStable,
                    contentDescription = if (isPhotoOrNight) "Open photo format, sequence, and monitoring settings"
                    else stringResource(R.string.video_settings_button_desc),
                    tint = if ((!isPhotoOrNight && state.stabilizationMode != StabilizationMode.OFF) ||
                        state.zebraEnabled || state.falseColorEnabled || state.horizonLevelEnabled
                    ) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            // 5. Far Right: Scopes Cycle (Photo/Night) or Audio Mic Monitor (Video/RAW Video)
            if (isPhotoOrNight) {
                IconButton(
                    onClick = viewModel::cycleScopeMode,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .testTag("photo_hist_quick_btn")
                ) {
                    Icon(
                        Icons.Rounded.BarChart,
                        contentDescription = "Cycle live exposure scope",
                        tint = if (state.scopeMode != MonitorScopeMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                IconButton(
                    onClick = { viewModel.openSheet(ActiveSheet.AUDIO_MONITOR) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .testTag("audio_monitor_button")
                ) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = stringResource(R.string.audio_monitor_button_desc),
                        tint = if (state.audioDevices.any { it.isExternal }) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeUniqueQuickStrip(
    state: CineRigUiState,
    viewModel: CineRigViewModel
) {
    if (state.manualDeckExpanded) return

    when (state.captureMode) {
        CaptureMode.PHOTO -> {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                // Essential Photo Controls Row: Flash, Self-Timer, Aspect Ratio, Sequence (Burst/AEB/Focus Stack/Interval), Guides
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        val flashIcon = when (state.flashMode) {
                            FlashMode.OFF -> Icons.Rounded.FlashOff
                            FlashMode.AUTO -> Icons.Rounded.FlashAuto
                            FlashMode.ON -> Icons.Rounded.FlashOn
                            FlashMode.TORCH -> Icons.Rounded.Highlight
                        }
                        HudPillChip(
                            icon = flashIcon,
                            label = state.flashMode.shortCode,
                            active = state.flashMode != FlashMode.OFF,
                            testTag = "photo_flash_pill",
                            onClick = viewModel::cycleFlashMode
                        )
                    }
                    item {
                        HudPillChip(
                            icon = Icons.Rounded.Timer,
                            label = if (state.selfTimer == SelfTimerOption.OFF) "Timer" else state.selfTimer.label,
                            active = state.selfTimer != SelfTimerOption.OFF,
                            testTag = "photo_timer_pill",
                            onClick = viewModel::cycleSelfTimer
                        )
                    }
                    item {
                        HudPillChip(
                            icon = Icons.Rounded.BurstMode,
                            label = state.photoSequenceMode.shortCode,
                            active = state.photoSequenceMode != PhotoSequenceMode.SINGLE,
                            testTag = "photo_sequence_pill",
                            onClick = {
                                val all = PhotoSequenceMode.entries
                                val next = all[(all.indexOf(state.photoSequenceMode) + 1) % all.size]
                                viewModel.setPhotoSequenceMode(next)
                            }
                        )
                    }
                    item {
                        HudPillChip(
                            icon = Icons.Rounded.GridOn,
                            label = if (state.frameGuide == FrameGuide.NONE) "Grid" else state.frameGuide.label,
                            active = state.frameGuide != FrameGuide.NONE,
                            testTag = "photo_grid_quick_btn",
                            onClick = viewModel::cycleFrameGuide
                        )
                    }
                    item {
                        HudPillChip(
                            icon = Icons.Rounded.Straighten,
                            label = "Level",
                            active = state.horizonLevelEnabled,
                            testTag = "photo_horizon_pill",
                            onClick = viewModel::toggleHorizonLevel
                        )
                    }
                }
                FilmLookQuickStrip(
                    selectedPreset = state.filmPreset,
                    grainAmount = state.colorGrading.grainAmount,
                    onSelectPreset = viewModel::setFilmPreset,
                    onOpenColorLab = { viewModel.openSheet(ActiveSheet.COLOR_GRADE) }
                )
            }
        }

        CaptureMode.NIGHT -> {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    HudPillChip(
                        icon = Icons.Rounded.AutoFixHigh,
                        label = "${state.nightStackStyle.label} (${state.nightStackStyle.frames}F)",
                        active = true,
                        activeTint = Color(0xFF94B8FF),
                        testTag = "night_stack_style_pill",
                        onClick = {
                            val styles = NightStackStyle.entries
                            val next = styles[(styles.indexOf(state.nightStackStyle) + 1) % styles.size]
                            viewModel.setNightStackStyle(next)
                        }
                    )
                }
                item {
                    HudPillChip(
                        icon = Icons.Rounded.NightsStay,
                        label = if (state.nightBoostEnabled) "LLB On" else "Low Light Boost",
                        active = state.nightBoostEnabled,
                        activeTint = Color(0xFF94B8FF),
                        testTag = "night_llb_pill",
                        onClick = viewModel::toggleNightBoost
                    )
                }
                item {
                    HudPillChip(
                        icon = Icons.Rounded.ShutterSpeed,
                        label = "Handheld 1/8s",
                        active = state.exposure.isManualExposure && state.exposure.shutterSpeedNs in 100_000_000L..150_000_000L,
                        activeTint = Color(0xFF94B8FF),
                        testTag = "night_handheld_pill",
                        onClick = {
                            viewModel.updateExposure {
                                it.copy(program = ExposureProgram.MANUAL, isManualExposure = true, shutterSpeedNs = 125_000_000L, iso = 1600)
                            }
                        }
                    )
                }
                item {
                    HudPillChip(
                        icon = Icons.Rounded.Bedtime,
                        label = "Astro 1.0s",
                        active = state.exposure.isManualExposure && state.exposure.shutterSpeedNs >= 900_000_000L,
                        activeTint = Color(0xFF94B8FF),
                        testTag = "night_astro_pill",
                        onClick = {
                            viewModel.updateExposure {
                                it.copy(program = ExposureProgram.MANUAL, isManualExposure = true, shutterSpeedNs = 1_000_000_000L, iso = 3200)
                            }
                        }
                    )
                }
                item {
                    HudPillChip(
                        icon = Icons.Rounded.CenterFocusStrong,
                        label = "Peaking",
                        active = state.focusPeakingEnabled,
                        activeTint = state.peakingColor.color,
                        testTag = "night_peaking_pill",
                        onClick = viewModel::toggleFocusPeaking
                    )
                }
                item {
                    HudPillChip(
                        icon = Icons.Rounded.ZoomIn,
                        label = "Loupe",
                        active = state.focusMagnifierEnabled,
                        activeTint = PeakingCyan,
                        testTag = "night_loupe_pill",
                        onClick = viewModel::toggleFocusMagnifier
                    )
                }
            }
        }

        CaptureMode.VIDEO -> {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    HudPillChip(
                        icon = Icons.Rounded.VideoStable,
                        label = "Stab: ${state.stabilizationMode.displayName}",
                        active = state.stabilizationMode != StabilizationMode.OFF,
                        testTag = "video_quick_stab",
                        onClick = {
                            val modes = StabilizationMode.entries
                            val next = modes[(modes.indexOf(state.stabilizationMode) + 1) % modes.size]
                            viewModel.setStabilizationMode(next)
                        }
                    )
                }
                item {
                    HudPillChip(
                        icon = if (state.videoResolution.isBinned) Icons.Rounded.GridView else Icons.Rounded.AspectRatio,
                        label = when (state.videoResolution) {
                            VideoResolution.OPEN_GATE_4_3 -> "Open Gate Full"
                            VideoResolution.OPEN_GATE_HALF -> "Open Gate 1/2 Bin"
                            else -> state.videoResolution.shortLabel
                        },
                        active = state.videoResolution.isOpenGate,
                        testTag = "video_quick_opengate",
                        onClick = {
                            val next = when (state.videoResolution) {
                                VideoResolution.OPEN_GATE_4_3 -> VideoResolution.OPEN_GATE_HALF
                                VideoResolution.OPEN_GATE_HALF -> VideoResolution.UHD_4K
                                else -> VideoResolution.OPEN_GATE_4_3
                            }
                            viewModel.setVideoResolution(next)
                        }
                    )
                }
                item {
                    HudPillChip(
                        icon = Icons.Rounded.Tonality,
                        label = if (state.colorGrading.monitoringLutEnabled) "LUT ON" else "FLAT LOG",
                        active = state.colorGrading.monitoringLutEnabled,
                        testTag = "video_quick_lut",
                        onClick = {
                            viewModel.updateColorGrading { it.copy(monitoringLutEnabled = !it.monitoringLutEnabled) }
                        }
                    )
                }
                item {
                    HudPillChip(
                        icon = Icons.Rounded.BarChart,
                        label = if (state.scopeMode == MonitorScopeMode.OFF) "Scopes" else state.scopeMode.shortCode,
                        active = state.scopeMode != MonitorScopeMode.OFF,
                        testTag = "video_quick_hist",
                        onClick = viewModel::cycleScopeMode
                    )
                }
                item {
                    HudPillChip(
                        icon = Icons.Rounded.Contrast,
                        label = "Zebra",
                        active = state.zebraEnabled,
                        testTag = "video_quick_zebra",
                        onClick = viewModel::toggleZebra
                    )
                }
            }
        }

        CaptureMode.RAW_VIDEO -> {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    HudPillChip(
                        icon = if (state.videoResolution.isBinned) Icons.Rounded.GridView else Icons.Rounded.AspectRatio,
                        label = when (state.videoResolution) {
                            VideoResolution.OPEN_GATE_4_3 -> "Open Gate Full"
                            VideoResolution.OPEN_GATE_HALF -> "Open Gate 1/2 Bin"
                            else -> "16:9 Crop"
                        },
                        active = state.videoResolution.isOpenGate,
                        testTag = "rawvid_opengate_pill",
                        onClick = {
                            val next = when (state.videoResolution) {
                                VideoResolution.OPEN_GATE_4_3 -> VideoResolution.OPEN_GATE_HALF
                                VideoResolution.OPEN_GATE_HALF -> VideoResolution.UHD_4K
                                else -> VideoResolution.OPEN_GATE_4_3
                            }
                            viewModel.setVideoResolution(next)
                        }
                    )
                }
                item {
                    HudPillChip(
                        icon = Icons.Rounded.Tonality,
                        label = if (state.colorGrading.monitoringLutEnabled) "Mon LUT" else "Raw Flat",
                        active = state.colorGrading.monitoringLutEnabled,
                        testTag = "rawvid_lut_pill",
                        onClick = {
                            viewModel.updateColorGrading { it.copy(monitoringLutEnabled = !it.monitoringLutEnabled) }
                        }
                    )
                }
                item {
                    HudPillChip(
                        icon = Icons.Rounded.CenterFocusStrong,
                        label = "Peaking",
                        active = state.focusPeakingEnabled,
                        activeTint = state.peakingColor.color,
                        testTag = "rawvid_peaking_pill",
                        onClick = viewModel::toggleFocusPeaking
                    )
                }
                item {
                    HudPillChip(
                        icon = Icons.Rounded.BarChart,
                        label = if (state.scopeMode == MonitorScopeMode.OFF) "RGB Scope" else state.scopeMode.shortCode,
                        active = state.scopeMode != MonitorScopeMode.OFF,
                        testTag = "rawvid_hist_pill",
                        onClick = viewModel::cycleScopeMode
                    )
                }
                item {
                    HudPillChip(
                        icon = Icons.Rounded.Gradient,
                        label = "False Col",
                        active = state.falseColorEnabled,
                        testTag = "rawvid_falsecolor_pill",
                        onClick = viewModel::toggleFalseColor
                    )
                }
            }
        }
    }
}

@Composable
private fun OpticalViewfinderBrackets(accentColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val inset = 14.dp.toPx()
        val arm = 16.dp.toPx()
        val stroke = 1.6.dp.toPx()
        val w = size.width
        val h = size.height

        drawLine(accentColor, Offset(inset, inset), Offset(inset + arm, inset), stroke)
        drawLine(accentColor, Offset(inset, inset), Offset(inset, inset + arm), stroke)
        drawLine(accentColor, Offset(w - inset, inset), Offset(w - inset - arm, inset), stroke)
        drawLine(accentColor, Offset(w - inset, inset), Offset(w - inset, inset + arm), stroke)
        drawLine(accentColor, Offset(inset, h - inset), Offset(inset + arm, h - inset), stroke)
        drawLine(accentColor, Offset(inset, h - inset), Offset(inset, h - inset - arm), stroke)
        drawLine(accentColor, Offset(w - inset, h - inset), Offset(w - inset - arm, h - inset), stroke)
        drawLine(accentColor, Offset(w - inset, h - inset), Offset(w - inset, h - inset - arm), stroke)
    }
}

@Composable
private fun PermissionGateScreen(onRequestPermissions: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(84.dp),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Camera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall
            )
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.offline_privacy_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.permission_camera_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.permission_camera_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("grant_permissions_button"),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.Videocam, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.permission_grant_button))
            }
        }
    }
}
