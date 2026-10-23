package com.example.diceroller.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private data class Accent(val color: Long, val onColor: Long, val container: Long, val onContainer: Long)
private data class AccentPair(val light: Accent, val dark: Accent)
private data class Surfaces(
    val surface: Long, val onSurface: Long, val variant: Long, val onVariant: Long,
    val outline: Long, val outlineVariant: Long, val inverse: Long, val onInverse: Long,
    val dim: Long, val bright: Long, val lowest: Long, val low: Long,
    val container: Long, val high: Long, val highest: Long,
)

internal data class ThemeColorPair(val light: ColorScheme, val dark: ColorScheme)

/** Explicit surface, contrast and fixed-color roles keep every Material component in the same palette. */
private fun palette(
    primary: AccentPair,
    secondary: AccentPair,
    tertiary: AccentPair,
    lightSurfaces: Surfaces,
    darkSurfaces: Surfaces,
): ThemeColorPair {
    fun scheme(dark: Boolean): ColorScheme {
        val p = if (dark) primary.dark else primary.light
        val s = if (dark) secondary.dark else secondary.light
        val t = if (dark) tertiary.dark else tertiary.light
        val n = if (dark) darkSurfaces else lightSurfaces
        return (if (dark) darkColorScheme() else lightColorScheme()).copy(
            primary = Color(p.color), onPrimary = Color(p.onColor),
            primaryContainer = Color(p.container), onPrimaryContainer = Color(p.onContainer),
            inversePrimary = Color(if (dark) primary.light.color else primary.dark.color),
            secondary = Color(s.color), onSecondary = Color(s.onColor),
            secondaryContainer = Color(s.container), onSecondaryContainer = Color(s.onContainer),
            tertiary = Color(t.color), onTertiary = Color(t.onColor),
            tertiaryContainer = Color(t.container), onTertiaryContainer = Color(t.onContainer),
            background = Color(n.surface), onBackground = Color(n.onSurface),
            surface = Color(n.surface), onSurface = Color(n.onSurface),
            surfaceVariant = Color(n.variant), onSurfaceVariant = Color(n.onVariant),
            surfaceTint = Color(p.color),
            inverseSurface = Color(n.inverse), inverseOnSurface = Color(n.onInverse),
            outline = Color(n.outline), outlineVariant = Color(n.outlineVariant),
            error = Color(if (dark) 0xFFF2B8B5 else 0xFFB3261E),
            onError = Color(if (dark) 0xFF601410 else 0xFFFFFFFF),
            errorContainer = Color(if (dark) 0xFF8C1D18 else 0xFFF9DEDC),
            onErrorContainer = Color(if (dark) 0xFFF9DEDC else 0xFF410E0B),
            scrim = Color.Black,
            surfaceDim = Color(n.dim), surfaceBright = Color(n.bright),
            surfaceContainerLowest = Color(n.lowest), surfaceContainerLow = Color(n.low),
            surfaceContainer = Color(n.container), surfaceContainerHigh = Color(n.high),
            surfaceContainerHighest = Color(n.highest),
            primaryFixed = Color(primary.light.container), primaryFixedDim = Color(primary.dark.color),
            onPrimaryFixed = Color(primary.light.onContainer), onPrimaryFixedVariant = Color(primary.dark.container),
            secondaryFixed = Color(secondary.light.container), secondaryFixedDim = Color(secondary.dark.color),
            onSecondaryFixed = Color(secondary.light.onContainer), onSecondaryFixedVariant = Color(secondary.dark.container),
            tertiaryFixed = Color(tertiary.light.container), tertiaryFixedDim = Color(tertiary.dark.color),
            onTertiaryFixed = Color(tertiary.light.onContainer), onTertiaryFixedVariant = Color(tertiary.dark.container),
        )
    }
    return ThemeColorPair(scheme(false), scheme(true))
}

internal val VioletColors = palette(
    primary = AccentPair(Accent(0xFF6750A4, 0xFFFFFFFF, 0xFFEADDFF, 0xFF21005D), Accent(0xFFD0BCFF, 0xFF381E72, 0xFF4F378B, 0xFFEADDFF)),
    secondary = AccentPair(Accent(0xFF625B71, 0xFFFFFFFF, 0xFFE8DEF8, 0xFF1D192B), Accent(0xFFCCC2DC, 0xFF332D41, 0xFF4A4458, 0xFFE8DEF8)),
    tertiary = AccentPair(Accent(0xFF7D5260, 0xFFFFFFFF, 0xFFFFD8E4, 0xFF31111D), Accent(0xFFEFB8C8, 0xFF492532, 0xFF633B48, 0xFFFFD8E4)),
    lightSurfaces = Surfaces(
        surface = 0xFFFFFBFE, onSurface = 0xFF1C1B1F, variant = 0xFFE7E0EC, onVariant = 0xFF49454F,
        outline = 0xFF79747E, outlineVariant = 0xFFCAC4D0, inverse = 0xFF313033, onInverse = 0xFFF4EFF4,
        dim = 0xFFDED8E1, bright = 0xFFFFFBFE, lowest = 0xFFFFFFFF, low = 0xFFF7F2FA,
        container = 0xFFF3EDF7, high = 0xFFECE6F0, highest = 0xFFE6E0E9,
    ),
    darkSurfaces = Surfaces(
        surface = 0xFF141218, onSurface = 0xFFE6E0E9, variant = 0xFF49454F, onVariant = 0xFFCAC4D0,
        outline = 0xFF938F99, outlineVariant = 0xFF49454F, inverse = 0xFFE6E0E9, onInverse = 0xFF313033,
        dim = 0xFF141218, bright = 0xFF3B383E, lowest = 0xFF0F0D13, low = 0xFF1D1B20,
        container = 0xFF211F26, high = 0xFF2B2930, highest = 0xFF36343B,
    ),
)

internal val OceanColors = palette(
    primary = AccentPair(Accent(0xFF0061A4, 0xFFFFFFFF, 0xFFD1E4FF, 0xFF001D36), Accent(0xFF9ECAFF, 0xFF003258, 0xFF00497D, 0xFFD1E4FF)),
    secondary = AccentPair(Accent(0xFF535F70, 0xFFFFFFFF, 0xFFD7E3F7, 0xFF101C2B), Accent(0xFFBBC7DB, 0xFF253140, 0xFF3B4858, 0xFFD7E3F7)),
    tertiary = AccentPair(Accent(0xFF6B5778, 0xFFFFFFFF, 0xFFF2DAFF, 0xFF251431), Accent(0xFFD6BEE4, 0xFF3B2948, 0xFF523F5F, 0xFFF2DAFF)),
    lightSurfaces = Surfaces(
        surface = 0xFFF8F9FF, onSurface = 0xFF191C20, variant = 0xFFDFE2EB, onVariant = 0xFF43474E,
        outline = 0xFF73777F, outlineVariant = 0xFFC3C6CF, inverse = 0xFF2E3035, onInverse = 0xFFEFF0F7,
        dim = 0xFFD9DAE1, bright = 0xFFF8F9FF, lowest = 0xFFFFFFFF, low = 0xFFF2F3FA,
        container = 0xFFECEEF4, high = 0xFFE6E8EE, highest = 0xFFE0E2E9,
    ),
    darkSurfaces = Surfaces(
        surface = 0xFF111318, onSurface = 0xFFE2E2E9, variant = 0xFF43474E, onVariant = 0xFFC3C6CF,
        outline = 0xFF8D9199, outlineVariant = 0xFF43474E, inverse = 0xFFE2E2E9, onInverse = 0xFF2F3035,
        dim = 0xFF111318, bright = 0xFF37393F, lowest = 0xFF0C0E13, low = 0xFF191C20,
        container = 0xFF1D2024, high = 0xFF282A2F, highest = 0xFF33353A,
    ),
)

internal val ForestColors = palette(
    primary = AccentPair(Accent(0xFF316B3A, 0xFFFFFFFF, 0xFFB2F2B8, 0xFF002108), Accent(0xFF94D59D, 0xFF003912, 0xFF175225, 0xFFB2F2B8)),
    secondary = AccentPair(Accent(0xFF526350, 0xFFFFFFFF, 0xFFD5E8CF, 0xFF101F0F), Accent(0xFFB9CCB4, 0xFF253423, 0xFF3B4B38, 0xFFD5E8CF)),
    tertiary = AccentPair(Accent(0xFF38656A, 0xFFFFFFFF, 0xFFBCEBF0, 0xFF002023), Accent(0xFFA0CFD4, 0xFF00363B, 0xFF1E4D52, 0xFFBCEBF0)),
    lightSurfaces = Surfaces(
        surface = 0xFFF7FBF2, onSurface = 0xFF181D17, variant = 0xFFDEE5D8, onVariant = 0xFF424940,
        outline = 0xFF73796E, outlineVariant = 0xFFC2C9BD, inverse = 0xFF2D322B, onInverse = 0xFFEFF2EA,
        dim = 0xFFD7DCD3, bright = 0xFFF7FBF2, lowest = 0xFFFFFFFF, low = 0xFFF1F5ED,
        container = 0xFFEBEFE7, high = 0xFFE5E9E1, highest = 0xFFDFE4DC,
    ),
    darkSurfaces = Surfaces(
        surface = 0xFF10150F, onSurface = 0xFFE0E4DB, variant = 0xFF424940, onVariant = 0xFFC2C9BD,
        outline = 0xFF8C9387, outlineVariant = 0xFF424940, inverse = 0xFFE0E4DB, onInverse = 0xFF2D322B,
        dim = 0xFF10150F, bright = 0xFF363B33, lowest = 0xFF0B100A, low = 0xFF191E17,
        container = 0xFF1D221B, high = 0xFF272C25, highest = 0xFF32372F,
    ),
)

internal val AmberColors = palette(
    primary = AccentPair(Accent(0xFF855300, 0xFFFFFFFF, 0xFFFFDDB0, 0xFF2A1700), Accent(0xFFFFB95B, 0xFF472A00, 0xFF643F00, 0xFFFFDDB0)),
    secondary = AccentPair(Accent(0xFF705B40, 0xFFFFFFFF, 0xFFFBDCBA, 0xFF281904), Accent(0xFFDEC3A2, 0xFF3F2D16, 0xFF57432B, 0xFFFBDCBA)),
    tertiary = AccentPair(Accent(0xFF52643E, 0xFFFFFFFF, 0xFFD5E9BA, 0xFF102004), Accent(0xFFB9CD9F, 0xFF253514, 0xFF3B4C29, 0xFFD5E9BA)),
    lightSurfaces = Surfaces(
        surface = 0xFFFFF8F3, onSurface = 0xFF211A13, variant = 0xFFEFE0CF, onVariant = 0xFF504538,
        outline = 0xFF837568, outlineVariant = 0xFFD3C4B4, inverse = 0xFF372F26, onInverse = 0xFFFCEFE2,
        dim = 0xFFE5D8CB, bright = 0xFFFFF8F3, lowest = 0xFFFFFFFF, low = 0xFFFFF1E5,
        container = 0xFFF9EBDD, high = 0xFFF3E5D7, highest = 0xFFEDDFD1,
    ),
    darkSurfaces = Surfaces(
        surface = 0xFF19130C, onSurface = 0xFFEEDFCF, variant = 0xFF504538, onVariant = 0xFFD3C4B4,
        outline = 0xFF9D8E80, outlineVariant = 0xFF504538, inverse = 0xFFEEDFCF, onInverse = 0xFF372F26,
        dim = 0xFF19130C, bright = 0xFF403830, lowest = 0xFF130D07, low = 0xFF221B14,
        container = 0xFF261F18, high = 0xFF312A22, highest = 0xFF3C352D,
    ),
)
