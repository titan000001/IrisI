package com.example.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DeviceHardwareCapabilities
import com.example.viewmodel.CineRigViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HardwareCapabilitiesSheet(
    capabilities: DeviceHardwareCapabilities,
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Hardware Capabilities & Storage", style = MaterialTheme.typography.titleMedium)
            }

            // Local Storage Diagnostic
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Local Storage Available", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Offline MediaStore DCIM/CineRig & Movies/CineRig",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = CineRigViewModel.formatBytes(capabilities.availableStorageBytes),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // CameraX Vendor Extensions & Pipeline Status (Distinguishing real support vs fallback)
            Text("Platform Pipelines & Extensions", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CapabilityBadge("Night Ext", capabilities.nightExtensionAvailable)
                CapabilityBadge("HDR Ext", capabilities.hdrExtensionAvailable)
                CapabilityBadge("Bokeh Ext", capabilities.bokehExtensionAvailable)
                CapabilityBadge("HLG 10-bit", capabilities.hlg10Supported)
            }

            if (capabilities.supportedVideoQualities.isNotEmpty()) {
                Text(
                    "Verified Encoder Qualities: ${capabilities.supportedVideoQualities.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Verified Encoder Qualities: None reported (software still fallback active on virtual cameras).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text("Detected Camera Modules (${capabilities.lenses.size})", style = MaterialTheme.typography.titleSmall)
            capabilities.lenses.forEach { lens ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "ID ${lens.cameraId} • ${lens.label} (${lens.equivalentMm}mm eq.)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = lens.hardwareLevel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Array: ${lens.sensorDimensions} • ISO ${lens.minIso}–${lens.maxIso} • Min Focus ${String.format(java.util.Locale.US, "%.1f", lens.minFocusDistance)}D",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "RAW DNG: ${if (lens.supportsRaw) "Supported" else "Unavailable"} • Manual Sensor: ${if (lens.supportsManualSensor) "Yes" else "AE Fallback"} • Stab: ${lens.supportedStabModes.ifEmpty { listOf("Standard AE") }.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (lens.supportsRaw) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (lens.physicalModuleIds.isNotEmpty()) {
                            Text(
                                text = "Logical Physical Sub-IDs: ${lens.physicalModuleIds.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityBadge(label: String, supported: Boolean) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (supported) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.testTag("cap_badge_$label")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (supported) Icons.Rounded.CheckCircle else Icons.Rounded.RemoveCircleOutline,
                contentDescription = null,
                tint = if (supported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (supported) "$label: HW" else "$label: N/A",
                style = MaterialTheme.typography.labelSmall,
                color = if (supported) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
