package com.cobfa.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun CoBFATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CobTypography,
        shapes = CobShapes,
        content = content
    )
}
