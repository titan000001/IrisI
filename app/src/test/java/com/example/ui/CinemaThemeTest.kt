package com.example.ui

import android.content.Context
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.resolveCinemaColorScheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class CinemaThemeTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test @Config(sdk = [28])
    fun olderAndroidFallsBackToTheRequestedLightOrDarkPalette() {
        for (dark in listOf(false, true)) {
            assertEquals(resolveCinemaColorScheme(context, dark, false), resolveCinemaColorScheme(context, dark, true))
        }
        assertTrue(resolveCinemaColorScheme(context, false, true).background.luminance() > .9f)
        assertTrue(resolveCinemaColorScheme(context, true, true).background.luminance() < .02f)
    }

    @Test @Config(sdk = [33])
    fun dynamicModeUsesTheWholeSystemPaletteInBothModes() {
        assertEquals(roles(dynamicLightColorScheme(context)), roles(resolveCinemaColorScheme(context, false, true)))
        assertEquals(roles(dynamicDarkColorScheme(context)), roles(resolveCinemaColorScheme(context, true, true)))
    }

    @Test @Config(sdk = [28])
    fun fallbackContentPairsMeetNormalTextContrast() {
        for (dark in listOf(false, true)) {
            val scheme = resolveCinemaColorScheme(context, dark, false)
            for ((foreground, background) in listOf(
                scheme.onSurface to scheme.surface, scheme.onSurfaceVariant to scheme.surfaceContainerHigh,
                scheme.onBackground to scheme.background, scheme.onPrimary to scheme.primary,
                scheme.onPrimaryContainer to scheme.primaryContainer, scheme.onSecondary to scheme.secondary,
                scheme.onSecondaryContainer to scheme.secondaryContainer, scheme.onTertiary to scheme.tertiary,
                scheme.onTertiaryContainer to scheme.tertiaryContainer, scheme.onError to scheme.error,
                scheme.onErrorContainer to scheme.errorContainer
            )) {
                assertTrue("Insufficient contrast in dark=$dark: $foreground on $background", contrast(foreground, background) >= 4.5f)
            }
        }
    }

    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)

    private fun roles(scheme: ColorScheme) = with(scheme) { listOf(
        primary, onPrimary, primaryContainer, onPrimaryContainer, secondary, onSecondary,
        secondaryContainer, onSecondaryContainer, tertiary, onTertiary, tertiaryContainer,
        onTertiaryContainer, background, onBackground, surface, onSurface, onSurfaceVariant,
        surfaceContainerLowest, surfaceContainerLow, surfaceContainer, surfaceContainerHigh,
        surfaceContainerHighest, outline, outlineVariant, error, onError, errorContainer, onErrorContainer
    ) }
}
