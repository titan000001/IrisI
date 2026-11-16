package com.example.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
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
    onTertiaryContainer = Color(0xFFBFE9FA),
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
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val CinemaLightColorScheme = lightColorScheme(
    primary = Color(0xFF775A24), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDFA6), onPrimaryContainer = Color(0xFF291800),
    secondary = Color(0xFF555F70), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E3F6), onSecondaryContainer = Color(0xFF121C2B),
    tertiary = Color(0xFF296577), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBFE9FA), onTertiaryContainer = Color(0xFF001F28),
    background = Color(0xFFFCF9F4), onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFCF9F4), onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFECE1D0), onSurfaceVariant = Color(0xFF4D463A),
    surfaceDim = Color(0xFFDFD9CF), surfaceBright = Color(0xFFFCF9F4),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F3ED),
    surfaceContainer = Color(0xFFF0EDE6),
    surfaceContainerHigh = Color(0xFFEAE7E0),
    surfaceContainerHighest = Color(0xFFE4E1DA),
    outline = Color(0xFF7E7668), outlineVariant = Color(0xFFD0C5B4),
    inverseSurface = Color(0xFF33302A), inverseOnSurface = Color(0xFFF5F0E7),
    inversePrimary = CinemaAmber,
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002)
)

/** Resolve the whole Material palette together; mixing dynamic text with fixed surfaces breaks contrast. */
internal fun resolveCinemaColorScheme(context: Context, darkTheme: Boolean, useDynamicColor: Boolean): ColorScheme =
    if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) CinemaDarkColorScheme else CinemaLightColorScheme
    }

val CineRigShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun CineRigTheme(
    useDynamicColor: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseScheme = resolveCinemaColorScheme(context, darkTheme, useDynamicColor)

    MaterialTheme(
        colorScheme = baseScheme,
        typography = CineRigTypography,
        shapes = CineRigShapes,
        content = content
    )
}
