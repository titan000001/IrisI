package com.example.ui.sheets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.camera.LogRecoveryClip
import com.example.viewmodel.CineRigViewModel

@Composable
internal fun LogRecoverySection(clips: List<LogRecoveryClip>, onShare: (LogRecoveryClip) -> Unit) {
    if (clips.isEmpty()) return
    var reviewing by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag("log_recovery_notice")
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Retained LOG originals", style = MaterialTheme.typography.titleSmall)
            Text("${clips.size} original(s) were kept during finalization. Review and share without changing your media.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { reviewing = true }) { Text("Review originals") }
        }
    }
    if (reviewing) {
        AlertDialog(
            onDismissRequest = { reviewing = false },
            title = { Text("LOG recovery") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("These are retained pre-finalization originals, not repaired or color-validated clips. " +
                        "Custom LOG transfer tags may be incorrect. Sharing grants read access and keeps the original in IrisI.")
                    clips.forEach { clip ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(clip.originalDisplayName, style = MaterialTheme.typography.titleSmall)
                            Text("${CineRigViewModel.formatBytes(clip.bytes)} • Unfinalized LOG original",
                                style = MaterialTheme.typography.bodySmall)
                            FilledTonalButton(onClick = { onShare(clip) }, modifier = Modifier.semantics {
                                contentDescription = "Share recovery original ${clip.fileName}"
                            }) { Text("Share original") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { reviewing = false }) { Text("Close") } }
        )
    }
}
