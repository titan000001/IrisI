package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.camera.LogRecoveryClip
import com.example.ui.sheets.LogRecoverySection
import com.example.ui.theme.CineRigTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LogRecoverySectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyQueueDoesNotShowARecoveryWarning() {
        compose.setContent { CineRigTheme { LogRecoverySection(emptyList()) {} } }
        compose.onNodeWithTag("log_recovery_notice").assertDoesNotExist()
    }

    @Test fun reviewExplainsUnfinalizedStatusAndSharesTheChosenOriginal() {
        val clip = LogRecoveryClip("IRISI_20261116_223928.mp4.123.original.mp4", "IRISI_20261116_223928.mp4", 4096, 123)
        var shared: LogRecoveryClip? = null
        compose.setContent { CineRigTheme { LogRecoverySection(listOf(clip)) { shared = it } } }
        compose.onNodeWithText("Review originals").performClick()
        compose.onNodeWithText("Custom LOG transfer tags may be incorrect.", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Share recovery original ${clip.fileName}").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(clip, shared) }
    }
}
