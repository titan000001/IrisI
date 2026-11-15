package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val CinemaDarkColorScheme = darkColorScheme(
    primary = CinemaAmber,
    onPrimary = OnCinemaAmber,
    primaryContainer = CinemaAmberContainer,
    onPrimaryContainer = OnCinemaAmberContainer,
    secondary = TitaniumSlate,
    onSecondary = OnTitaniumSlate,
    secondaryContainer = TitaniumContainer,
    onSecondaryContainer = OnTitaniumContainer,
    tertiary = PeakingCyan,
    onTertiary = OnPeakingCyan,
    tertiaryContainer = PeakingCyanContainer,
    background = ObsidianBackground,
    onBackground = CinemaOnSurface,
    surface = ObsidianSurface,
    onSurface = CinemaOnSurface,
    surfaceVariant = ObsidianSurfaceContainerHigh,
    onSurfaceVariant = CinemaOnSurfaceVariant,
    surfaceContainerLowest = ObsidianSurfaceContainerLowest,
    surfaceContainerLow = ObsidianSurfaceContainerLow,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceContainerHigh,
    surfaceContainerHighest = ObsidianSurfaceContainerHighest,
    outline = CinemaOutline,
    outlineVariant = CinemaOutlineVariant,
    error = RecordRed,
    errorContainer = RecordRedContainer
)

val CineRigShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun CineRigTheme(
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseScheme = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dyn = dynamicDarkColorScheme(context)
        // Preserve deep cinema obsidian backing for accurate viewfinder contrast while adopting Material You dynamic accents
        dyn.copy(
            background = ObsidianBackground,
            surface = ObsidianSurface,
            surfaceContainerLowest = ObsidianSurfaceContainerLowest,
            surfaceContainerLow = ObsidianSurfaceContainerLow,
            surfaceContainer = ObsidianSurfaceContainer,
            surfaceContainerHigh = ObsidianSurfaceContainerHigh,
            surfaceContainerHighest = ObsidianSurfaceContainerHighest
        )
    } else {
        CinemaDarkColorScheme
    }

    MaterialTheme(
        colorScheme = baseScheme,
        typography = CineRigTypography,
        shapes = CineRigShapes,
        content = content
    )
}
