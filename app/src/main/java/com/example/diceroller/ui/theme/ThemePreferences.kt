package com.example.diceroller.ui.theme

import android.content.Context
import androidx.core.content.edit

/** Appearance is independent of roll history and the rolling ViewModel. */
class ThemePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun readPalette(): ThemePalette = ThemePalette.fromStorageKey(
        runCatching { preferences.getString(KEY_PALETTE, null) }.getOrNull()
    )

    fun setPalette(palette: ThemePalette) {
        preferences.edit { putString(KEY_PALETTE, palette.storageKey) }
    }

    companion object {
        const val FILE_NAME = "appearance"
        const val KEY_PALETTE = "palette"
    }
}
