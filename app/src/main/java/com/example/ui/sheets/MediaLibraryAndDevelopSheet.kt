package com.example.ui.sheets

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrixColorFilter
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.camera.ColorEngine
import com.example.data.local.CapturedMediaEntity
import com.example.model.DevelopSettings
import com.example.model.FilmPreset
import com.example.ui.components.RotaryColorDial
import com.example.ui.theme.CinemaAmber
import com.example.viewmodel.CineRigUiState
import com.example.viewmodel.CineRigViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class LibraryFilter(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    RAW("RAW DNG"),
    FAVORITES("Favorites")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLibrarySheet(
    state: CineRigUiState,
    onToggleFavorite: (CapturedMediaEntity) -> Unit,
    onOpenDevelop: (CapturedMediaEntity) -> Unit,
    onDeleteLog: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(LibraryFilter.ALL) }
    var batchSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var exifTarget by remember { mutableStateOf<Pair<CapturedMediaEntity, List<Pair<String, String>>>?>(null) }

    val filtered = remember(state.mediaLogs, searchQuery, activeFilter) {
        state.mediaLogs.filter { item ->
            val matchesFilter = when (activeFilter) {
                LibraryFilter.ALL -> true
                LibraryFilter.PHOTOS -> !item.isVideo
                LibraryFilter.VIDEOS -> item.isVideo
                LibraryFilter.RAW -> item.formatBadge.contains("RAW", ignoreCase = true) ||
                    item.formatBadge.contains("DNG", ignoreCase = true)
                LibraryFilter.FAVORITES -> item.isFavorite
            }
            val matchesQuery = searchQuery.isBlank() ||
                item.displayName.contains(searchQuery, ignoreCase = true) ||
                item.formatBadge.contains(searchQuery, ignoreCase = true) ||
                item.lensLabel.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

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
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("IrisI Media Library", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Free Space: ${CineRigViewModel.formatBytes(state.deviceCapabilities.availableStorageBytes)} • Offline Vault",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = batchSelectionMode,
                        onClick = {
                            batchSelectionMode = !batchSelectionMode
                            if (!batchSelectionMode) selectedIds = emptySet()
                        },
                        label = { Text(if (batchSelectionMode) "Cancel (${selectedIds.size})" else "Select") }
                    )
                }
            }

            if (batchSelectionMode && selectedIds.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val uris = ArrayList(
                                state.mediaLogs.filter { it.id in selectedIds && it.uriString.isNotBlank() }
                                    .map { Uri.parse(it.uriString) }
                            )
                            if (uris.isNotEmpty()) {
                                val share = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "*/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(Intent.createChooser(share, "Share ${uris.size} items")) }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Batch Share")
                    }
                    OutlinedButton(
                        onClick = {
                            selectedIds.forEach { onDeleteLog(it) }
                            selectedIds = emptySet()
                            batchSelectionMode = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Remove Logs")
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search filename, format, lens...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("library_search_input")
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(LibraryFilter.entries) { f ->
                    FilterChip(
                        selected = activeFilter == f,
                        onClick = { activeFilter = f },
                        label = { Text(f.label) },
                        modifier = Modifier.testTag("library_filter_${f.name}")
                    )
                }
            }

            if (filtered.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("No Matching Local Media", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Photos, DNG RAW files, and Log videos saved locally appear here with full EXIF telemetry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        val isSelected = item.id in selectedIds
                        MediaCardRow(
                            item = item,
                            batchMode = batchSelectionMode,
                            selected = isSelected,
                            onTap = {
                                if (batchSelectionMode) {
                                    selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                } else if (item.uriString.isNotBlank()) {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(
                                                Uri.parse(item.uriString),
                                                if (item.isVideo) "video/*" else "image/*"
                                            )
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            },
                            onToggleFavorite = { onToggleFavorite(item) },
                            onInspectExif = {
                                val tags = readRealExifMetadata(context, item)
                                exifTarget = item to tags
                            },
                            onDevelop = { onOpenDevelop(item) },
                            onShare = {
                                if (item.uriString.isNotBlank()) {
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = if (item.isVideo) "video/*" else "image/*"
                                        putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uriString))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching { context.startActivity(Intent.createChooser(share, "Share ${item.displayName}")) }
                                }
                            },
                            onDelete = { onDeleteLog(item.id) }
                        )
                    }
                }
            }
        }
    }

    exifTarget?.let { (item, tags) ->
        AlertDialog(
            onDismissRequest = { exifTarget = null },
            title = { Text("Metadata & EXIF • ${item.displayName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    tags.forEach { (k, v) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { exifTarget = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun MediaCardRow(
    item: CapturedMediaEntity,
    batchMode: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onToggleFavorite: () -> Unit,
    onInspectExif: () -> Unit,
    onDevelop: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onTap),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.uriString.isNotBlank() && !item.isVideo) {
                        AsyncImage(
                            model = Uri.parse(item.uriString),
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (item.isVideo) Icons.Rounded.Movie else Icons.Rounded.RawOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(item.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                item.formatBadge,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        "${item.resolutionLabel} • ${item.lensLabel} • ISO ${item.iso} • ${item.shutterLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${item.logProfile} • ${item.filmPreset}" +
                            (if (item.fileBytes > 0) " • ${CineRigViewModel.formatBytes(item.fileBytes)}" else "") +
                            (if (item.isVideo && item.durationMs > 0) " • ${CineRigViewModel.formatDuration(item.durationMs)}" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (batchMode) {
                    Checkbox(checked = selected, onCheckedChange = { onTap() })
                } else {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!batchMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!item.isVideo) {
                        FilledTonalButton(
                            onClick = onDevelop,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp).testTag("develop_item_${item.id}")
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Develop", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(
                        onClick = onInspectExif,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("EXIF", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onShare,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Share", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

private enum class DevelopToolGroup(val label: String) {
    TONE("Tone"),
    COLOR("Color"),
    PRESET("Film / Export")
}

/**
 * In-app Non-Destructive RAW / Photo Develop Studio with live preview, Hold-to-Compare Original,
 * Highlight/Shadow recovery, Temperature/Tint, Rotation, Film LUT, and Export Copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevelopLabSheet(
    target: CapturedMediaEntity,
    dev: DevelopSettings,
    onUpdate: ((DevelopSettings) -> DevelopSettings) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var sourcePreview by remember(target.uriString) { mutableStateOf<Bitmap?>(null) }
    var compareOriginal by remember { mutableStateOf(false) }
    var activeGroup by remember { mutableStateOf(DevelopToolGroup.TONE) }

    LaunchedEffect(target.uriString) {
        sourcePreview = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(target.uriString)
                ColorEngine.decodeBitmapForDevelop(context.contentResolver, uri, sampleSize = 2)
            }.getOrNull()
        }
    }

    val previewMatrix = remember(dev, compareOriginal) {
        if (compareOriginal) android.graphics.ColorMatrix() else ColorEngine.buildDevelopColorMatrix(dev)
    }

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
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("RAW & Photo Develop", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Non-destructive • Original ${target.formatBadge} preserved",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = { onUpdate { it.copy(rotationDeg = (it.rotationDeg + 90) % 360) } }) {
                        Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = "Rotate 90 degrees")
                    }
                    Button(onClick = onExport, modifier = Modifier.testTag("develop_export_btn")) {
                        Text("Export")
                    }
                }
            }

            // Live Preview Box with Hold-to-Compare
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val bmp = sourcePreview
                if (bmp != null && !bmp.isRecycled) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Develop preview",
                        contentScale = ContentScale.Fit,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(previewMatrix.array)
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("Loading RAW / Image preview...", style = MaterialTheme.typography.bodySmall, color = Color.White)
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .testTag("develop_compare_btn")
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    compareOriginal = true
                                    try { tryAwaitRelease() } finally { compareOriginal = false }
                                }
                            )
                        },
                    shape = CircleShape,
                    color = if (compareOriginal) CinemaAmber else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f)
                ) {
                    Text(
                        text = if (compareOriginal) "ORIGINAL" else "Hold Compare",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (compareOriginal) Color.Black else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DevelopToolGroup.entries.forEachIndexed { idx, group ->
                    SegmentedButton(
                        selected = activeGroup == group,
                        onClick = { activeGroup = group },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = DevelopToolGroup.entries.size)
                    ) {
                        Text(group.label)
                    }
                }
            }

            when (activeGroup) {
                DevelopToolGroup.TONE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RotaryColorDial(
                            label = "EXP",
                            subLabel = "EV",
                            value = dev.exposureEv,
                            defaultValue = 0f,
                            range = -2f..2f,
                            valueText = String.format(java.util.Locale.US, "%+.1f", dev.exposureEv),
                            accentColor = CinemaAmber,
                            modifier = Modifier.weight(1f),
                            onValueChange = { v -> onUpdate { it.copy(exposureEv = v) } }
                        )
                        RotaryColorDial(
                            label = "HIGH",
                            subLabel = "Recovery",
                            value = dev.highlights,
                            defaultValue = 0f,
                            range = -1f..1f,
                            valueText = String.format(java.util.Locale.US, "%+.2f", dev.highlights),
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            onValueChange = { v -> onUpdate { it.copy(highlights = v) } }
                        )
                        RotaryColorDial(
                            label = "SHDW",
                            subLabel = "Lift",
                            value = dev.shadows,
                            defaultValue = 0f,
                            range = -1f..1f,
                            valueText = String.format(java.util.Locale.US, "%+.2f", dev.shadows),
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                            onValueChange = { v -> onUpdate { it.copy(shadows = v) } }
                        )
                    }
                }

                DevelopToolGroup.COLOR -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RotaryColorDial(
                            label = "TEMP",
                            subLabel = "Warmth",
                            value = dev.temperature,
                            defaultValue = 0f,
                            range = -0.8f..0.8f,
                            valueText = String.format(java.util.Locale.US, "%+.2f", dev.temperature),
                            accentColor = CinemaAmber,
                            modifier = Modifier.weight(1f),
                            onValueChange = { v -> onUpdate { it.copy(temperature = v) } }
                        )
                        RotaryColorDial(
                            label = "TINT",
                            subLabel = "G / M",
                            value = dev.tint,
                            defaultValue = 0f,
                            range = -0.8f..0.8f,
                            valueText = String.format(java.util.Locale.US, "%+.2f", dev.tint),
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                            onValueChange = { v -> onUpdate { it.copy(tint = v) } }
                        )
                        RotaryColorDial(
                            label = "VIBR",
                            subLabel = "Chroma",
                            value = dev.vibrance,
                            defaultValue = 0f,
                            range = -0.5f..0.5f,
                            valueText = String.format(java.util.Locale.US, "%+.2f", dev.vibrance),
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            onValueChange = { v -> onUpdate { it.copy(vibrance = v) } }
                        )
                    }
                }

                DevelopToolGroup.PRESET -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(FilmPreset.entries, key = { it.name }) { p ->
                            FilterChip(
                                selected = dev.filmPreset == p,
                                onClick = { onUpdate { it.copy(filmPreset = p) } },
                                label = { Text(p.displayName) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RotaryColorDial(
                            label = "CONT",
                            subLabel = "Contrast",
                            value = dev.contrast,
                            defaultValue = 1f,
                            range = 0.5f..1.6f,
                            valueText = String.format(java.util.Locale.US, "%.2fx", dev.contrast),
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            onValueChange = { v -> onUpdate { it.copy(contrast = v) } }
                        )
                        RotaryColorDial(
                            label = "VIGN",
                            subLabel = "Falloff",
                            value = dev.vignette,
                            defaultValue = 0f,
                            range = 0f..0.8f,
                            valueText = "${(dev.vignette * 100).toInt()}%",
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                            onValueChange = { v -> onUpdate { it.copy(vignette = v) } }
                        )
                    }
                }
            }
        }
    }
}

private fun readRealExifMetadata(context: android.content.Context, item: CapturedMediaEntity): List<Pair<String, String>> {
    val rows = mutableListOf(
        "Format" to item.formatBadge,
        "Resolution" to item.resolutionLabel,
        "Lens" to item.lensLabel,
        "ISO" to "${item.iso}",
        "Shutter" to item.shutterLabel,
        "Profile" to "${item.logProfile} / ${item.filmPreset}"
    )
    if (item.uriString.isNotBlank() && !item.isVideo) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(item.uriString))?.use { stream ->
                val exif = ExifInterface(stream)
                exif.getAttribute(ExifInterface.TAG_MODEL)?.let { rows.add("Camera" to it) }
                exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { rows.add("Aperture" to "f/$it") }
                exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { rows.add("Focal Length" to it) }
                exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)?.let { w ->
                    val h = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH) ?: "?"
                    rows.add("Pixel Dims" to "${w}×${h}")
                }
                val hasGps = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null
                rows.add("GPS Tag" to if (hasGps) "Embedded" else "None")
            }
        }
    }
    return rows
}
