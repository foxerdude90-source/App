package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ClipperWhiteColorScheme = lightColorScheme(
    primary = ClipperIndigo,
    onPrimary = StudioWhite,
    primaryContainer = ClipperIndigoSoft,
    onPrimaryContainer = ClipperIndigo,
    secondary = ClipperCyan,
    onSecondary = StudioWhite,
    secondaryContainer = ClipperCyanSoft,
    onSecondaryContainer = ClipperCyan,
    tertiary = ClipperMagenta,
    onTertiary = StudioWhite,
    tertiaryContainer = ClipperMagentaSoft,
    onTertiaryContainer = ClipperMagenta,
    background = StudioWhite,
    onBackground = InkObsidian,
    surface = StudioWhite,
    onSurface = InkObsidian,
    surfaceVariant = StudioSnow,
    onSurfaceVariant = InkSlate,
    surfaceContainerLowest = StudioWhite,
    surfaceContainerLow = StudioSnow,
    surfaceContainer = StudioSurfaceSubtle,
    outline = StudioBorder,
    outlineVariant = StudioBorderStrong,
    error = ClipperRose,
    onError = StudioWhite,
    errorContainer = ClipperRoseSoft,
    onErrorContainer = ClipperRose,
)

private val ClipperShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ClipperTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ClipperWhiteColorScheme,
        typography = ClipperTypography,
        shapes = ClipperShapes,
        content = content,
    )
}
