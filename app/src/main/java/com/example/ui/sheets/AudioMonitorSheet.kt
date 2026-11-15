package com.example.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.HistogramGreen
import com.example.ui.theme.RecordRed
import com.example.ui.theme.ZebraWarning
import com.example.viewmodel.CineRigUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioMonitorSheet(
    state: CineRigUiState,
    onSelectInputDevice: (Int) -> Unit,
    onRefreshDevices: () -> Unit,
    onToggleMonitoring: (Boolean) -> Unit,
    onInputGainChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
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
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Audio Input & Live Monitor", style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = onRefreshDevices, modifier = Modifier.testTag("refresh_audio_devices")) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Rescan audio devices")
                }
            }

            // Large Precision dBFS Peak/RMS Meter
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("48 kHz • 16-bit PCM Live Meter", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = "PEAK ${state.audioLevels.peakDb.toInt()} dBFS  |  RMS ${state.audioLevels.rmsDb.toInt()} dBFS",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.audioLevels.clipping) RecordRed else MaterialTheme.colorScheme.primary
                        )
                    }

                    val meterColor = when {
                        state.audioLevels.clipping -> RecordRed
                        state.audioLevels.peakDb > -8f -> ZebraWarning
                        else -> HistogramGreen
                    }

                    listOf("L" to state.audioLevels.leftNormalized, "R" to state.audioLevels.rightNormalized).forEach { (ch, norm) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(ch, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(norm)
                                        .clip(CircleShape)
                                        .background(meterColor)
                                )
                            }
                        }
                    }
                }
            }

            // Input Preamp Gain
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Input Gain Trim", style = MaterialTheme.typography.titleSmall)
                    Text(
                        String.format(java.util.Locale.US, "%+.1f dB", state.inputGainDb),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = state.inputGainDb,
                    onValueChange = onInputGainChange,
                    valueRange = -12f..18f,
                    modifier = Modifier.testTag("audio_gain_slider")
                )
            }

            // Headphone Monitoring Toggle
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = if (state.headphoneConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column {
                            Text("Zero-Latency Headphone Monitor", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (state.headphoneConnected) "Wired/USB headphones detected — ready for monitoring"
                                else "Connect wired or USB-C headphones to prevent speaker feedback",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = state.audioMonitoringEnabled,
                        onCheckedChange = onToggleMonitoring,
                        enabled = state.headphoneConnected,
                        modifier = Modifier.testTag("headphone_monitor_switch")
                    )
                }
            }

            // Audio Input Sources (Built-in + External USB-C / Wired / Bluetooth)
            Text("Microphone Source", style = MaterialTheme.typography.titleSmall)
            if (state.audioDevices.isEmpty()) {
                Text(
                    "Grant microphone permission to enumerate external USB-C, TRRS, and internal inputs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.audioDevices.forEach { dev ->
                        val selected = state.selectedAudioDeviceId == dev.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("audio_dev_${dev.id}")
                                .clickable { onSelectInputDevice(dev.id) },
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (dev.isExternal) Icons.Rounded.SettingsInputComponent else Icons.Rounded.Mic,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dev.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        dev.typeLabel,
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
            }
        }
    }
}
