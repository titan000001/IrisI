package com.example

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.model.ExposureProgram
import com.example.ui.components.LensAndZoomBar
import com.example.ui.components.ManualControlsDeck
import com.example.ui.theme.CineRigTheme
import com.example.viewmodel.CineRigUiState
import com.example.viewmodel.ManualControlTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CameraControlAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun collapsedExposureControlsKeepAccessibleTargetsAndCallbacks() {
        var selected: ManualControlTab? = null
        var expanded = false
        compose.setContent {
            CineRigTheme {
                ManualControlsDeck(
                    state = CineRigUiState(), onSelectProgram = {},
                    onSelectTab = { selected = it }, onToggleExpanded = { expanded = true },
                    onUpdateExposure = {}, onTogglePeaking = {}, onSelectPeakingColor = {},
                    onToggleMagnifier = {}
                )
            }
        }
        listOf("toggle_manual_deck", "manual_tab_ev", "manual_tab_iso", "manual_tab_shutter", "manual_tab_focus", "manual_tab_wb")
            .forEach { compose.onNodeWithTag(it).assertHeightIsAtLeast(48.dp) }
        compose.onNodeWithTag("manual_tab_iso").performClick()
        compose.runOnIdle { assertEquals(ManualControlTab.ISO, selected) }
        compose.onNodeWithTag("toggle_manual_deck").performClick()
        compose.runOnIdle { assertEquals(true, expanded) }
    }

    @Test fun expandedProgramsRetainAccessibleTargetsAndSelectionContract() {
        var selected: ExposureProgram? = null
        compose.setContent {
            CineRigTheme {
                ManualControlsDeck(
                    state = CineRigUiState(manualDeckExpanded = true), onSelectProgram = { selected = it },
                    onSelectTab = {}, onToggleExpanded = {}, onUpdateExposure = {},
                    onTogglePeaking = {}, onSelectPeakingColor = {}, onToggleMagnifier = {}
                )
            }
        }
        ExposureProgram.entries.forEach {
            compose.onNodeWithTag("exp_program_${it.name}").assertHeightIsAtLeast(48.dp).performClick()
            compose.runOnIdle { assertEquals(it, selected) }
        }
    }

    @Test fun zoomDisclosureRemainsAvailableWithoutReportedLenses() {
        var clicked = false
        compose.setContent {
            CineRigTheme {
                LensAndZoomBar(lenses = emptyList(), selectedLens = null, zoomRatio = 1f,
                    zoomSliderExpanded = false, isPhotoIdentity = true, onSelectLens = {},
                    onZoomChange = {}, onToggleZoomSlider = { clicked = true })
            }
        }
        compose.onNodeWithTag("zoom_pill_toggle").assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(true, clicked) }
    }
}
