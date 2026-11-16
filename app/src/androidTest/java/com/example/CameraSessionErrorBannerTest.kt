package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.screens.CameraSessionErrorBanner
import com.example.ui.screens.CameraCapabilityNotice
import org.junit.Rule
import org.junit.Test

class CameraSessionErrorBannerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsBindingFailureAndRecoveryAdvice() {
        compose.setContent {
            MaterialTheme {
                CameraSessionErrorBanner("Requested 4K is unavailable; choose another resolution")
            }
        }
        compose.onNodeWithText("Camera: Requested 4K is unavailable; choose another resolution")
            .assertIsDisplayed()
    }

    @Test fun hidesBannerWhenSessionHasNoError() {
        compose.setContent { CameraSessionErrorBanner(null) }
        compose.onNodeWithText("Camera:", substring = true).assertDoesNotExist()
    }

    @Test fun capabilityNoticeDoesNotPresentACameraFailure() {
        compose.setContent {
            MaterialTheme { CameraCapabilityNotice("Live scopes unavailable") }
        }
        compose.onNodeWithText("Monitoring: Live scopes unavailable").assertIsDisplayed()
        compose.onNodeWithText("Camera:", substring = true).assertDoesNotExist()
    }
}
