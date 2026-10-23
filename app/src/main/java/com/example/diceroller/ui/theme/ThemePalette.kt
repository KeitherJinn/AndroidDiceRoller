package com.example.diceroller.ui.theme

import androidx.compose.material3.ColorScheme

/** Stable storage keys survive display-name and enum-order changes. */
enum class ThemePalette(val storageKey: String) {
    SYSTEM("system"), VIOLET("violet"), OCEAN("ocean"), FOREST("forest"), AMBER("amber");

    /** Static schemes also provide the SYSTEM fallback for previews without an Android context. */
    fun colorScheme(darkTheme: Boolean): ColorScheme {
        val colors = when (this) {
            SYSTEM, VIOLET -> VioletColors
            OCEAN -> OceanColors
            FOREST -> ForestColors
            AMBER -> AmberColors
        }
        return if (darkTheme) colors.dark else colors.light
    }

    companion object {
        fun fromStorageKey(value: String?): ThemePalette = entries.find { it.storageKey == value } ?: SYSTEM
    }
}
