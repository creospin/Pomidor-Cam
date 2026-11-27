package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Pure Black / Deep Obsidian surfaces (user-requested black theme)
val ObsidianBg = Color(0xFF06080D)
val CharcoalSurface = Color(0xFF0C1018)
val SlateCard = Color(0xFF121A28)
val SlateBorder = Color(0xFF1E2D47)

// Cobalt -> Sky Blue palette sampled from tomato_slice_28206610.png
val CobaltBlue = Color(0xFF0F52BA)
val RoyalBlue = Color(0xFF1F6FEB)
val SkyBlue = Color(0xFF4D94FF)
val BlueGlowSoft = Color(0x2E2B74E8)

// Keep legacy semantic aliases updated to blue/gold/mint so all screens stay cohesive
val TomatoRed = RoyalBlue
val TomatoRedSoft = BlueGlowSoft
val TomatoDark = CobaltBlue

val GoldenSeed = Color(0xFFE0B446)
val AmberWarning = GoldenSeed
val AmberWarningSoft = Color(0x2EE0B446)

val MintGreen = Color(0xFF36D399)
val MintGreenSoft = Color(0x2B36D399)

val IceText = Color(0xFFF3F6FC)
val MutedSlateText = Color(0xFF93A5C4)
val DimText = Color(0xFF5D6F8F)

val BlueButtonGradient = Brush.horizontalGradient(
    colors = listOf(CobaltBlue, RoyalBlue, SkyBlue)
)

val BlueButtonVerticalGradient = Brush.verticalGradient(
    colors = listOf(SkyBlue, CobaltBlue)
)
