package com.example.diceroller.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.diceroller.R

@Composable
fun PalettePickerDialog(
    selected: ThemePalette,
    onSelect: (ThemePalette) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("palette_dialog"),
        title = { Text(stringResource(R.string.palette_title)) },
        text = {
            // The explanation scrolls with the options, leaving all choices reachable in landscape.
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                    .selectableGroup().testTag("palette_options"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "explanation") {
                    Text(stringResource(R.string.palette_hint),
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.bodyMedium)
                }
                items(ThemePalette.entries, key = { it.storageKey }) { palette ->
                    val scheme = resolveThemeColorScheme(context, palette, darkTheme)
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .selectable(selected = selected == palette, role = Role.RadioButton,
                                onClick = { onSelect(palette) })
                            .testTag("palette_option_${palette.name}")
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(Modifier.width(32.dp).height(28.dp)
                            .clip(MaterialTheme.shapes.small).clearAndSetSemantics { }) {
                            Box(Modifier.weight(1f).height(28.dp).background(scheme.primary))
                            Box(Modifier.weight(1f).height(28.dp).background(scheme.secondaryContainer))
                        }
                        Text(paletteName(palette), modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge)
                        RadioButton(selected = selected == palette, onClick = null)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).testTag("palette_done")) {
                Text(stringResource(R.string.palette_done))
            }
        },
    )
}

@Composable
private fun paletteName(palette: ThemePalette): String = stringResource(when (palette) {
    ThemePalette.SYSTEM -> R.string.palette_system
    ThemePalette.VIOLET -> R.string.palette_violet
    ThemePalette.OCEAN -> R.string.palette_ocean
    ThemePalette.FOREST -> R.string.palette_forest
    ThemePalette.AMBER -> R.string.palette_amber
})
