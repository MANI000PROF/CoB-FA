package com.cobfa.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

// Fixed brand accents (do not depend on wallpaper)
val CobTeal = Color(0xFF2EC4B6)
val CobNavy = Color(0xFF0B3C49)
val CobMint = Color(0xFFCBF3F0)
val CobGray = Color(0xFF9BB4C1)

// Fallback schemes (used on API < 31 or when dynamicColor=false)
val LightColorScheme = lightColorScheme(
    primary = CobTeal,
    onPrimary = CobNavy,
    secondary = CobNavy,
    onSecondary = Color.White,
    tertiary = CobTeal,
    onTertiary = CobNavy,

    background = Color.White,
    onBackground = CobNavy,
    surface = Color.White,
    onSurface = CobNavy,

    surfaceVariant = CobMint,
    onSurfaceVariant = CobNavy,

    outline = CobGray,
)

val DarkColorScheme = darkColorScheme(
    primary = CobTeal,
    onPrimary = CobNavy,
    secondary = CobTeal,
    onSecondary = CobNavy,
    tertiary = CobMint,
    onTertiary = CobNavy,

    background = Color(0xFF071F26),
    onBackground = Color(0xFFE7F7F5),
    surface = Color(0xFF071F26),
    onSurface = Color(0xFFE7F7F5),

    surfaceVariant = Color(0xFF0F2C35),
    onSurfaceVariant = Color(0xFFCFE9E6),

    outline = Color(0xFF3D6470),
)
