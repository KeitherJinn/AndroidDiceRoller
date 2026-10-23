package com.example.diceroller

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.example.diceroller.ui.theme.ThemePalette
import com.example.diceroller.ui.theme.ThemePreferences
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ThemeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[DiceRollerViewModel::class.java]
    private var capturedPreference = false
    private var hadPreference = false
    private var previousPreference: String? = null

    private fun preferences() = compose.activity.getSharedPreferences(ThemePreferences.FILE_NAME, Context.MODE_PRIVATE)
    private fun settled() {
        compose.waitUntil(10_000) { !model.state.value.busy }
        compose.waitForIdle()
    }

    // Explicit commit() lets these tests assert successful disk writes; KTX edit discards that result.
    @Before fun isolateAndResetPalette() {
        assertEquals("Refusing to edit a non-test installation", "com.example.diceroller.uitest", compose.activity.packageName)
        val preferences = preferences()
        hadPreference = preferences.contains(ThemePreferences.KEY_PALETTE)
        previousPreference = preferences.getString(ThemePreferences.KEY_PALETTE, null)
        capturedPreference = true
        assertTrue(preferences.edit().putString(ThemePreferences.KEY_PALETTE, ThemePalette.SYSTEM.storageKey).commit())
        compose.activityRule.scenario.recreate()
        settled()
    }

    @After fun restoreQaPalette() {
        if (!capturedPreference) return
        val editor = preferences().edit()
        if (hadPreference) editor.putString(ThemePreferences.KEY_PALETTE, previousPreference)
        else editor.remove(ThemePreferences.KEY_PALETTE)
        assertTrue(editor.commit())
    }

    private fun openPalette() {
        val form = if (compose.onAllNodesWithTag("form_list").fetchSemanticsNodes().isNotEmpty()) "form_list" else "main_list"
        compose.onNodeWithTag(form).performScrollToNode(hasTestTag("palette_button"))
        compose.onNodeWithTag("palette_button").assertIsDisplayed().performClick()
        compose.onNodeWithTag("palette_dialog").assertIsDisplayed()
    }

    private fun selectPalette(palette: ThemePalette) {
        openPalette()
        compose.onNodeWithTag("palette_options").performScrollToNode(hasTestTag("palette_option_${palette.name}"))
        compose.onNodeWithTag("palette_option_${palette.name}").assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithTag("palette_done").performClick()
        settled()
    }

    private fun expectedPrimary(palette: ThemePalette): Int {
        val dark = (compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val scheme = if (palette == ThemePalette.SYSTEM) {
            if (dark) dynamicDarkColorScheme(compose.activity) else dynamicLightColorScheme(compose.activity)
        } else palette.colorScheme(dark)
        return scheme.primary.toArgb()
    }

    /** Sample the filled button away from its label and rounded corners. */
    private fun renderedPrimary(): Int {
        val image = compose.onNodeWithTag("roll_button").assertIsEnabled().assertIsDisplayed().captureToImage()
        return image.toPixelMap()[image.width / 8, image.height / 2].toArgb()
    }

    @Test fun everyPaletteIsReachableAndImmediatelyChangesTheRenderedButton() {
        compose.runOnIdle {
            model.setNotation("2d6+3")
            model.setTimes("3")
            model.setMode(RollMode.ADVANTAGE)
        }
        val history = model.state.value.history
        val rendered = mutableSetOf<Int>()
        ThemePalette.entries.forEach { palette ->
            selectPalette(palette)
            assertEquals(palette, ThemePreferences(compose.activity).readPalette())
            assertEquals("2d6+3", model.state.value.notation)
            assertEquals("3", model.state.value.timesText)
            assertEquals(RollMode.ADVANTAGE, model.state.value.mode)
            assertEquals(history, model.state.value.history)
            val actual = renderedPrimary()
            assertEquals("Rendered primary for $palette", expectedPrimary(palette), actual)
            rendered += actual
        }
        assertTrue("The four fixed palettes must visibly differ", rendered.size >= 4)
    }

    @Test fun paletteSurvivesActivityRecreationAndFreshPreferenceReader() {
        selectPalette(ThemePalette.OCEAN)
        val before = renderedPrimary()
        assertEquals(ThemePalette.OCEAN, ThemePreferences(compose.activity.applicationContext).readPalette())
        compose.activityRule.scenario.recreate()
        settled()
        assertEquals(ThemePalette.OCEAN, ThemePreferences(compose.activity.applicationContext).readPalette())
        assertEquals(before, renderedPrimary())
        openPalette()
        compose.onNodeWithTag("palette_options").performScrollToNode(hasTestTag("palette_option_OCEAN"))
        compose.onNodeWithTag("palette_option_OCEAN").assertIsSelected()
        compose.onNodeWithTag("palette_done").performClick()
    }

    @Test fun unknownStoredPaletteSafelyFallsBackToSystemColors() {
        assertTrue(preferences().edit().putString(ThemePreferences.KEY_PALETTE, "future-palette").commit())
        assertEquals(ThemePalette.SYSTEM, ThemePreferences(compose.activity).readPalette())
        compose.activityRule.scenario.recreate()
        settled()
        assertEquals(expectedPrimary(ThemePalette.SYSTEM), renderedPrimary())
        openPalette()
        compose.onNodeWithTag("palette_options").performScrollToNode(hasTestTag("palette_option_SYSTEM"))
        compose.onNodeWithTag("palette_option_SYSTEM").assertIsSelected()
        compose.onNodeWithTag("palette_done").performClick()

        assertTrue(preferences().edit().putInt(ThemePreferences.KEY_PALETTE, 42).commit())
        assertEquals(ThemePalette.SYSTEM, ThemePreferences(compose.activity).readPalette())
        compose.activityRule.scenario.recreate()
        settled()
        assertEquals(expectedPrimary(ThemePalette.SYSTEM), renderedPrimary())
    }

    @Test fun clearingHistoryLeavesTheSelectedPaletteAndItsStoredPreferenceIntact() {
        selectPalette(ThemePalette.FOREST)
        compose.runOnIdle {
            model.setNotation("d1")
            model.setTimes("1")
            assertTrue(model.roll())
        }
        settled()
        compose.onNodeWithTag("main_list").performScrollToNode(hasTestTag("clear_history"))
        compose.onNodeWithTag("clear_history").performClick()
        compose.onNodeWithTag("confirm_clear").performClick()
        settled()
        assertTrue(model.state.value.history.isEmpty())
        assertEquals(ThemePalette.FOREST, ThemePreferences(compose.activity).readPalette())
        assertEquals(ThemePalette.FOREST.storageKey, preferences().getString(ThemePreferences.KEY_PALETTE, null))
        assertEquals(expectedPrimary(ThemePalette.FOREST), renderedPrimary())
    }
}
