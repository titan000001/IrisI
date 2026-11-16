package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/** Requires camera/microphone permissions already granted on the test emulator. No media is captured. */
class CameraConsoleDisclosureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun photoToolsAreHiddenUntilRequestedAndManualControlsCloseThem() {
        compose.onNodeWithTag("mode_tab_PHOTO").performClick()
        compose.onNodeWithTag("camera_tools_panel").assertDoesNotExist()
        compose.onNodeWithTag("camera_tools_toggle").performClick()
        compose.onNodeWithTag("camera_tools_panel").assertIsDisplayed()
        compose.onNodeWithTag("photo_flash_pill").assertIsDisplayed()
        compose.onNodeWithTag("shutter_record_button").assertIsDisplayed()
        compose.onNodeWithTag("manual_tab_iso").performClick()
        compose.onNodeWithTag("camera_tools_panel").assertDoesNotExist()
        compose.onNodeWithTag("toggle_manual_deck").performClick()
        compose.onNodeWithTag("shutter_record_button").assertIsDisplayed()
    }
}
