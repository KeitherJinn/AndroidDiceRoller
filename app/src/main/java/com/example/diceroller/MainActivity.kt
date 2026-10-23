package com.example.diceroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.diceroller.ui.theme.DiceRollerTheme
import com.example.diceroller.ui.theme.ThemePreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = viewModelFactory {
            initializer {
                DiceRollerViewModel(createSavedStateHandle(), HistoryStore(this@MainActivity.application.filesDir))
            }
        }
        val model = ViewModelProvider(this, factory)[DiceRollerViewModel::class.java]
        val themePreferences = ThemePreferences(applicationContext)
        setContent {
            var palette by remember { mutableStateOf(themePreferences.readPalette()) }
            DiceRollerTheme(palette = palette) {
                DiceRollerRoute(model, palette) { selected ->
                    themePreferences.setPalette(selected)
                    palette = selected
                }
            }
        }
    }
}
