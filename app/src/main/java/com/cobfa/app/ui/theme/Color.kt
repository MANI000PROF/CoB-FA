package com.cobfa.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val CobTeal = Color(0xFF09AFAB)
val CobNavy = Color(0xFF004764)
val CobGray = Color(0xFF8EA7B2)

val LightColorScheme = lightColorScheme(
    primary = CobTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9FF0EC),
    onPrimaryContainer = Color(0xFF00201F),

    secondary = CobNavy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC7E7F5),
    onSecondaryContainer = Color(0xFF001E2B),

    tertiary = Color(0xFF0B7285),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBDEBFA),
    onTertiaryContainer = Color(0xFF001F27),

    background = Color(0xFFF3FBFB),
    onBackground = Color(0xFF102022),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF102022),

    surfaceVariant = Color(0xFFDCE8E8),
    onSurfaceVariant = Color(0xFF405253),

    outline = CobGray
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF78D9D5),
    onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF00514E),
    onPrimaryContainer = Color(0xFF9FF0EC),

    secondary = Color(0xFF8CC9E0),
    onSecondary = Color(0xFF003547),
    secondaryContainer = Color(0xFF004764),
    onSecondaryContainer = Color(0xFFC7E7F5),

    tertiary = Color(0xFF82D4EA),
    onTertiary = Color(0xFF003641),
    tertiaryContainer = Color(0xFF0B7285),
    onTertiaryContainer = Color(0xFFBDEBFA),

    background = Color(0xFF091517),
    onBackground = Color(0xFFDDE5E5),
    surface = Color(0xFF091517),
    onSurface = Color(0xFFDDE5E5),

    surfaceVariant = Color(0xFF243738),
    onSurfaceVariant = Color(0xFFB8C9C9),

    outline = Color(0xFF88999A)
)
