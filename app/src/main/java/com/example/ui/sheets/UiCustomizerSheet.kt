package com.example.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.viewmodel.CineRigUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UiCustomizerSheet(
    state: CineRigUiState,
    onSelectLayout: (Long) -> Unit,
    onSaveAsNewLayout: (String) -> Unit,
    onDeleteLayout: (Long) -> Unit,
    onMoveWidgetToZone: (HudWidgetId, HudSlotZone) -> Unit,
    onReorderWidget: (HudWidgetId, Boolean) -> Unit,
    onUpdateBindings: (
        fn1: AssignableAction?,
        fn2: AssignableAction?,
        fn3: AssignableAction?,
        volUp: AssignableAction?,
        volDown: AssignableAction?
    ) -> Unit,
    onToggleDirectHudEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var newLayoutName by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }

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
                    Icon(Icons.Rounded.DashboardCustomize, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("HUD Layout Customizer", style = MaterialTheme.typography.titleMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = {
                            onToggleDirectHudEdit()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("toggle_live_hud_edit")
                    ) {
                        Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (state.isLayoutEditMode) "Done" else "Live Edit")
                    }
                    IconButton(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.testTag("save_new_layout_btn")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Save layout")
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Saved Layouts Selector Row
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.layouts, key = { it.id }) { layout ->
                    val active = layout.id == state.activeLayout.id
                    InputChip(
                        selected = active,
                        onClick = { onSelectLayout(layout.id) },
                        label = { Text(layout.name) },
                        trailingIcon = if (state.layouts.size > 1 && !active) {
                            {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Delete layout",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onDeleteLayout(layout.id) }
                                )
                            }
                        } else null,
                        modifier = Modifier.testTag("layout_chip_${layout.name}")
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("Arrange Controls", "Fn & Hardware Keys").forEachIndexed { idx, title ->
                    SegmentedButton(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = 2),
                        modifier = Modifier.testTag("customizer_tab_$idx")
                    ) {
                        Text(title)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (selectedTab == 0) {
                    val placementsByZone = state.activeLayout.placements
                        .sortedBy { it.order }
                        .groupBy { it.zone }

                    HudSlotZone.entries.forEach { zone ->
                        val itemsInZone = placementsByZone[zone].orEmpty()
                        Text(
                            text = "${zone.label} (${itemsInZone.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (itemsInZone.isEmpty()) {
                            Text(
                                "No controls placed here",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            itemsInZone.forEachIndexed { index, placement ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                placement.widgetId.title,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Row {
                                                IconButton(
                                                    onClick = { onReorderWidget(placement.widgetId, true) },
                                                    enabled = index > 0,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                                                }
                                                IconButton(
                                                    onClick = { onReorderWidget(placement.widgetId, false) },
                                                    enabled = index < itemsInZone.lastIndex,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            HudSlotZone.entries.forEach { targetZone ->
                                                val shortZone = when (targetZone) {
                                                    HudSlotZone.TOP_BAR -> "Top"
                                                    HudSlotZone.LEFT_DOCK -> "Left"
                                                    HudSlotZone.RIGHT_DOCK -> "Right"
                                                    HudSlotZone.HIDDEN -> "Hide"
                                                }
                                                FilterChip(
                                                    selected = placement.zone == targetZone,
                                                    onClick = { onMoveWidgetToZone(placement.widgetId, targetZone) },
                                                    label = { Text(shortZone, style = MaterialTheme.typography.labelSmall) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    ActionBindingPicker(
                        title = "Fn1 Programmable Button",
                        current = state.activeLayout.fn1Action,
                        onSelect = { onUpdateBindings(it, null, null, null, null) }
                    )
                    ActionBindingPicker(
                        title = "Fn2 Programmable Button",
                        current = state.activeLayout.fn2Action,
                        onSelect = { onUpdateBindings(null, it, null, null, null) }
                    )
                    ActionBindingPicker(
                        title = "Fn3 Programmable Button",
                        current = state.activeLayout.fn3Action,
                        onSelect = { onUpdateBindings(null, null, it, null, null) }
                    )
                    ActionBindingPicker(
                        title = "Volume Up Hardware Key",
                        current = state.activeLayout.volumeUpAction,
                        onSelect = { onUpdateBindings(null, null, null, it, null) }
                    )
                    ActionBindingPicker(
                        title = "Volume Down Hardware Key",
                        current = state.activeLayout.volumeDownAction,
                        onSelect = { onUpdateBindings(null, null, null, null, it) }
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Custom Layout") },
            text = {
                OutlinedTextField(
                    value = newLayoutName,
                    onValueChange = { newLayoutName = it },
                    label = { Text("Layout Name") },
                    placeholder = { Text("e.g., Gimbal Docu Rig") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveAsNewLayout(newLayoutName)
                        newLayoutName = ""
                        showSaveDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ActionBindingPicker(
    title: String,
    current: AssignableAction,
    onSelect: (AssignableAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(current.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AssignableAction.entries.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        onClick = {
                            onSelect(action)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
