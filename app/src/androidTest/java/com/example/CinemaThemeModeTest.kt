package com.example

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.CineRigTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CinemaThemeModeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun systemModeRecomposesChromeWhileMonitorRemainsDark() {
        var night by mutableStateOf(false)
        var chrome = Color.Unspecified
        var monitor = Color.Unspecified
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                CineRigTheme(useDynamicColor = false) {
                    val chromeColor = MaterialTheme.colorScheme.background
                    SideEffect { chrome = chromeColor }
                    CineRigTheme(useDynamicColor = false, darkTheme = true) {
                        val monitorColor = MaterialTheme.colorScheme.background
                        SideEffect { monitor = monitorColor }
                    }
                }
            }
        }
        compose.runOnIdle {
            assertTrue(chrome.luminance() > .9f)
            assertTrue(monitor.luminance() < .02f)
            night = true
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(chrome.luminance() < .02f)
            assertTrue(monitor.luminance() < .02f)
        }
    }
}
