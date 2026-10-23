package com.example.diceroller.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

fun resolveThemeColorScheme(context: Context, palette: ThemePalette, darkTheme: Boolean): ColorScheme =
    if (palette == ThemePalette.SYSTEM) {
        // All supported devices run Android 14 or later and have dynamic color APIs.
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else palette.colorScheme(darkTheme)

@Composable
fun DiceRollerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ThemePalette = ThemePalette.SYSTEM,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = resolveThemeColorScheme(LocalContext.current, palette, darkTheme),
        typography = Typography,
        content = content,
    )
}
