package com.example.diceroller

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.example.diceroller.ui.theme.ThemePalette
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePaletteTest {
    private fun contrast(foreground: Color, background: Color): Double {
        val first = foreground.compositeOver(background).luminance().toDouble()
        val second = background.luminance().toDouble()
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    @Test fun everyFixedLightAndDarkPaletteKeepsNormalTextReadable() {
        ThemePalette.entries.filter { it != ThemePalette.SYSTEM }.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val scheme = palette.colorScheme(dark)
                val textPairs = listOf(
                    Triple("button", scheme.onPrimary, scheme.primary),
                    Triple("latest result", scheme.onSecondaryContainer, scheme.secondaryContainer),
                    Triple("page text", scheme.onSurface, scheme.surface),
                    Triple("history secondary text", scheme.onSurfaceVariant, scheme.surfaceContainer),
                    Triple("persistence warning", scheme.onErrorContainer, scheme.errorContainer),
                    Triple("latest result secondary text", scheme.onSurfaceVariant, scheme.secondaryContainer),
                    Triple("result actions", scheme.primary, scheme.secondaryContainer),
                    Triple("recovery actions", scheme.primary, scheme.errorContainer),
                )
                textPairs.forEach { (role, foreground, background) ->
                    val ratio = contrast(foreground, background)
                    assertTrue("$palette dark=$dark $role contrast=$ratio", ratio >= 4.5)
                }
            }
        }
    }
}
