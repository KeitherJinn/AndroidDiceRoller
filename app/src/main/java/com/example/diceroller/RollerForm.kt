package com.example.diceroller

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickDice(state: RollerUiState, onQuickDie: (Int) -> Unit) {
    val labelStyle = MaterialTheme.typography.labelLarge
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelWidth = with(density) { textMeasurer.measure("d100", style = labelStyle, softWrap = false).size.width.toDp() }
    val minimumChipWidth = maxOf(64.dp, labelWidth + 32.dp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.quick_dice), style = MaterialTheme.typography.titleSmall)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Measure the longest label at the current font scale instead of shrinking accessible text.
            val columns = ((maxWidth + 8.dp).value / (minimumChipWidth + 8.dp).value).toInt().coerceIn(1, 4)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(4, 6, 8, 10, 12, 20, 100).chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { sides ->
                            FilterChip(
                                selected = state.notation.trim().lowercase() in setOf("d$sides", "1d$sides"),
                                onClick = { onQuickDie(sides) }, enabled = !state.busy,
                                label = { Text("d$sides", style = labelStyle, maxLines = 1, softWrap = false) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("quick_d$sides"),
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Text(stringResource(R.string.quick_dice_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun NotationField(state: RollerUiState, onChange: (String) -> Unit, focus: FocusRequester, onNext: () -> Unit) {
    OutlinedTextField(
        value = state.notation, onValueChange = onChange, enabled = !state.busy,
        label = { Text(stringResource(R.string.dice_notation)) },
        supportingText = { Text(state.notationIssue?.let { issueText(it) } ?: stringResource(R.string.notation_hint)) },
        isError = state.notationIssue != null, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("notation"),
    )
}

@Composable
internal fun AdvancedSettingsToggle(state: RollerUiState, expanded: Boolean, onToggle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("advanced_toggle")) {
            Text(stringResource(if (expanded) R.string.collapse_settings else R.string.more_settings))
        }
        Text(stringResource(R.string.active_settings, modeText(state.mode), state.timesText.ifBlank { stringResource(R.string.parameter_empty) }),
            modifier = Modifier.testTag("active_settings"), style = MaterialTheme.typography.bodyMedium)
        if (state.name.isNotBlank()) Text(stringResource(R.string.active_note, state.name), style = MaterialTheme.typography.bodySmall)
        if (!expanded && state.mode != RollMode.NORMAL) Text(modeHint(state.mode), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!expanded && state.timesIssue != null) Text(issueText(state.timesIssue), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun TimesField(state: RollerUiState, onChange: (String) -> Unit, focus: FocusRequester, onSubmit: () -> Unit) {
    OutlinedTextField(value = state.timesText, onValueChange = onChange, enabled = !state.busy,
        label = { Text(stringResource(R.string.repeat_count)) },
        supportingText = { Text(state.timesIssue?.let { issueText(it) }
            ?: stringResource(R.string.repeat_hint, RollLimits.MAX_REPEATS, RollLimits.MAX_TOTAL_DICE)) },
        isError = state.timesIssue != null, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("times"))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModeField(state: RollerUiState, onMode: (RollMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.roll_mode), style = MaterialTheme.typography.titleSmall)
        FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RollMode.entries.forEach { mode ->
                Row(Modifier.widthIn(min = 110.dp).heightIn(min = 48.dp)
                    .selectable(state.mode == mode, enabled = !state.busy, role = Role.RadioButton) { onMode(mode) }
                    .testTag("mode_${mode.name}").padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.mode == mode, onClick = null, enabled = !state.busy)
                    Text(modeText(mode), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        Text(modeHint(state.mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun NoteField(state: RollerUiState, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    OutlinedTextField(value = state.name, onValueChange = onChange, enabled = !state.busy,
        label = { Text(stringResource(R.string.note_optional)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier.fillMaxWidth().testTag("name"))
}

@Composable
internal fun modeText(mode: RollMode): String = stringResource(when (mode) {
    RollMode.NORMAL -> R.string.mode_normal
    RollMode.ADVANTAGE -> R.string.mode_advantage
    RollMode.DISADVANTAGE -> R.string.mode_disadvantage
})

@Composable
internal fun modeHint(mode: RollMode): String = stringResource(when (mode) {
    RollMode.NORMAL -> R.string.mode_normal_hint
    RollMode.ADVANTAGE -> R.string.mode_advantage_hint
    RollMode.DISADVANTAGE -> R.string.mode_disadvantage_hint
})

@Composable
internal fun issueText(issue: InputIssue): String = when (issue) {
    InputIssue.NOTATION_FORMAT -> stringResource(R.string.error_notation)
    InputIssue.NOTATION_LENGTH -> stringResource(R.string.error_notation_length, RollLimits.MAX_NOTATION_LENGTH)
    InputIssue.DICE_LIMIT -> stringResource(R.string.error_dice_limit, RollLimits.MAX_DICE)
    InputIssue.SIDES_LIMIT -> stringResource(R.string.error_sides_limit, RollLimits.MAX_SIDES)
    InputIssue.MODIFIER_LIMIT -> stringResource(R.string.error_modifier_limit)
    InputIssue.REPEAT_LIMIT -> stringResource(R.string.error_repeat_limit, RollLimits.MAX_REPEATS)
    InputIssue.WORK_LIMIT -> stringResource(R.string.error_work_limit, RollLimits.MAX_TOTAL_DICE)
}
