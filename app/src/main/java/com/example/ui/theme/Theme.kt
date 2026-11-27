package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PomidorBlackBlueColorScheme = darkColorScheme(
    primary = RoyalBlue,
    onPrimary = IceText,
    primaryContainer = CobaltBlue,
    onPrimaryContainer = IceText,
    secondary = SkyBlue,
    onSecondary = ObsidianBg,
    secondaryContainer = BlueGlowSoft,
    onSecondaryContainer = SkyBlue,
    tertiary = GoldenSeed,
    onTertiary = ObsidianBg,
    tertiaryContainer = AmberWarningSoft,
    onTertiaryContainer = GoldenSeed,
    background = ObsidianBg,
    onBackground = IceText,
    surface = CharcoalSurface,
    onSurface = IceText,
    surfaceVariant = SlateCard,
    onSurfaceVariant = MutedSlateText,
    outline = SlateBorder,
    outlineVariant = SlateBorder
)

val PomidorShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PomidorProTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PomidorBlackBlueColorScheme,
        typography = Typography,
        shapes = PomidorShapes,
        content = content
    )
}
