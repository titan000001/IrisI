package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.viewmodel.ActiveSheet
import com.example.viewmodel.CineRigUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VideoAndStabSheet(
    state: CineRigUiState,
    onSelectStabilization: (StabilizationMode) -> Unit,
    onSelectResolution: (VideoResolution) -> Unit,
    onSelectFps: (VideoFps) -> Unit,
    onSelectBitrate: (VideoBitrate) -> Unit,
    onSelectCodec: (VideoCodecFormat) -> Unit,
    onSelectPhotoFormat: (PhotoFormat) -> Unit,
    onSelectSequenceMode: (PhotoSequenceMode) -> Unit,
    onCycleAspectRatio: () -> Unit,
    onSelectScopeMode: (MonitorScopeMode) -> Unit,
    onToggleZebra: () -> Unit,
    onToggleFalseColor: () -> Unit,
    onToggleHorizon: () -> Unit,
    onOpenHardwareInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    val isPhotoOrNight = state.captureMode == CaptureMode.PHOTO || state.captureMode == CaptureMode.NIGHT

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 22.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (isPhotoOrNight) Icons.Rounded.CameraAlt else Icons.Rounded.VideoStable,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (isPhotoOrNight) "Photo & Computational Settings" else "Cinema Video & Stabilization",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                TextButton(onClick = onOpenHardwareInfo, modifier = Modifier.testTag("open_hardware_info_btn")) {
                    Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Hardware")
                }
            }

            if (isPhotoOrNight) {
                // PHOTO & NIGHT ONLY SETTINGS (Zero Video Leakage)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Still Photo Format", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (state.selectedLens?.supportsRaw == true) "Hardware DNG Ready" else "Device Uses High-Res JPEG",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotoFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = state.photoFormat == fmt,
                            onClick = { onSelectPhotoFormat(fmt) },
                            label = { Text(fmt.label) },
                            modifier = Modifier.testTag("photo_fmt_${fmt.name}")
                        )
                    }
                }

                if (state.captureMode == CaptureMode.PHOTO) {
                    Text("Capture Sequence / Computational", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PhotoSequenceMode.entries.forEach { seq ->
                            FilterChip(
                                selected = state.photoSequenceMode == seq,
                                onClick = { onSelectSequenceMode(seq) },
                                label = { Text(seq.label) },
                                modifier = Modifier.testTag("photo_seq_${seq.name}")
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Photo Aspect Ratio", style = MaterialTheme.typography.titleSmall)
                    FilledTonalButton(onClick = onCycleAspectRatio) {
                        Text(state.photoAspectRatio.label)
                    }
                }
            } else {
                // VIDEO & RAW VIDEO ONLY SETTINGS
                Text("Handheld Stabilization Mode", style = MaterialTheme.typography.titleSmall)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StabilizationMode.entries.forEach { mode ->
                        val selected = state.stabilizationMode == mode
                        val icon = when (mode) {
                            StabilizationMode.OFF -> Icons.Rounded.Videocam
                            StabilizationMode.STANDARD -> Icons.Rounded.VideoStable
                            StabilizationMode.ENHANCED -> Icons.Rounded.AutoAwesomeMotion
                            StabilizationMode.ACTION -> Icons.AutoMirrored.Rounded.DirectionsRun
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("stab_mode_${mode.name}")
                                .clickable { onSelectStabilization(mode) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(mode.displayName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                        Text("FOV ${mode.cropFactor}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text(
                                        mode.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text("Sensor Aspect, Binning & Resolution", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoResolution.entries.forEach { res ->
                        FilterChip(
                            selected = state.videoResolution == res,
                            onClick = { onSelectResolution(res) },
                            label = { Text(res.label) },
                            leadingIcon = if (res.isOpenGate) {
                                {
                                    Icon(
                                        if (res.isBinned) Icons.Rounded.GridView else Icons.Rounded.AspectRatio,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            modifier = Modifier.testTag("video_res_${res.name}")
                        )
                    }
                }
                if (state.videoResolution.isBinned) {
                    Text(
                        "2×2 pixel-binned 4:3 readout (~2016×1512): preserves full sensor height with lower rolling shutter and cooler thermals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text("Video Encoding & RAW Pipeline", style = MaterialTheme.typography.titleSmall)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoCodecFormat.entries.forEach { codec ->
                        val selected = state.videoCodecFormat == codec
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("video_codec_${codec.name}")
                                .clickable { onSelectCodec(codec) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(codec.label, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        codec.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (codec == VideoCodecFormat.HLG_10BIT) {
                                    AssistChip(
                                        onClick = { onSelectCodec(codec) },
                                        label = { Text(if (state.hlg10Supported) "HW 10-BIT" else "SW MAP") }
                                    )
                                }
                            }
                        }
                    }
                }

                Text("Frame Rate (Slow-Mo @ 60p/120p)", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoFps.entries.forEach { fps ->
                        FilterChip(
                            selected = state.videoFps == fps,
                            onClick = { onSelectFps(fps) },
                            label = { Text(fps.label) },
                            modifier = Modifier.testTag("video_fps_${fps.fps}")
                        )
                    }
                }

                Text("Video Bitrate Quality", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoBitrate.entries.forEach { br ->
                        FilterChip(
                            selected = state.videoBitrate == br,
                            onClick = { onSelectBitrate(br) },
                            label = { Text(br.label) },
                            modifier = Modifier.testTag("video_bitrate_${br.mbps}")
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Optional Monitoring & Exposure Tools (Shared but 100% optional)
            Text("Optional Exposure & Framing Scopes", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MonitorScopeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.scopeMode == mode,
                        onClick = { onSelectScopeMode(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.zebraEnabled,
                    onClick = onToggleZebra,
                    label = { Text("Zebra 92%") },
                    modifier = Modifier.testTag("monitor_zebra_chip")
                )
                FilterChip(
                    selected = state.falseColorEnabled,
                    onClick = onToggleFalseColor,
                    label = { Text("False Color") },
                    modifier = Modifier.testTag("monitor_falsecolor_chip")
                )
                FilterChip(
                    selected = state.horizonLevelEnabled,
                    onClick = onToggleHorizon,
                    label = { Text("Horizon Level") },
                    modifier = Modifier.testTag("monitor_horizon_chip")
                )
            }
        }
    }
}
