package com.example.diceroller

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diceroller.ui.theme.PalettePickerDialog
import com.example.diceroller.ui.theme.ThemePalette
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

data class RollerActions(
    val setName: (String) -> Unit,
    val setNotation: (String) -> Unit,
    val setTimes: (String) -> Unit,
    val setMode: (RollMode) -> Unit,
    val quickDie: (Int) -> Unit,
    val roll: () -> Boolean,
    val cancel: () -> Unit,
    val clear: () -> Unit,
    val retrySave: () -> Unit,
    val retryLoad: () -> Unit,
    val recover: () -> Unit,
    val reset: () -> Unit,
    val loadBatch: (RollBatch) -> Unit,
    val loadResult: (RollResult) -> Unit,
)

@Composable
fun DiceRollerRoute(
    model: DiceRollerViewModel,
    palette: ThemePalette = ThemePalette.SYSTEM,
    onPaletteChange: (ThemePalette) -> Unit = {},
) {
    val state by model.state.collectAsStateWithLifecycle()
    val actions = remember(model) {
        RollerActions(model::setName, model::setNotation, model::setTimes, model::setMode, model::setQuickDie,
            model::roll, model::cancelRoll, model::clearHistory, model::retrySave, model::retryLoad,
            model::recoverHistory, model::resetHistory, model::loadBatch, model::loadResult)
    }
    DiceRollerScreen(state, actions, palette, onPaletteChange)
}

private enum class ItemKind { TITLE, LATEST, QUICK_DICE, NOTATION, ADVANCED, TIMES, MODE, NAME, HISTORY_HEADING, BATCH }
private data class ScreenItem(val kind: ItemKind, val batch: RollBatch? = null) {
    val key: String get() = batch?.let { "batch_${it.id}" } ?: kind.name
}
private enum class FormFocus { NOTATION, TIMES, VALIDATION }

/** Presentation depends on state and callbacks, so empty, error and recovery states are independently previewable. */
@Composable
fun DiceRollerScreen(
    state: RollerUiState,
    actions: RollerActions,
    palette: ThemePalette = ThemePalette.SYSTEM,
    onPaletteChange: (ThemePalette) -> Unit = {},
) {
    val mainList = rememberLazyListState()
    val wideFormList = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val notationFocus = remember { FocusRequester() }
    val timesFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val formatter = rememberResultTextFormatter()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var showPalette by rememberSaveable { mutableStateOf(false) }
    var handledRevision by rememberSaveable { mutableIntStateOf(state.resultRevision) }
    var handledFeedbackKey by rememberSaveable { mutableStateOf<String?>(null) }
    var mainAnchorKey by rememberSaveable { mutableStateOf<String?>(null) }
    var mainAnchorOffset by rememberSaveable { mutableIntStateOf(0) }
    var focusRequest by remember { mutableIntStateOf(0) }
    var focusTarget by remember { mutableStateOf(FormFocus.VALIDATION) }
    var loadRequest by remember { mutableIntStateOf(0) }
    val copiedText = stringResource(R.string.result_copied)
    val copyLabel = stringResource(R.string.copy_label)
    val loadedText = stringResource(R.string.settings_loaded)
    val copy: (String) -> Unit = { text ->
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText(copyLabel, text))
        scope.launch { snackbar.showSnackbar(copiedText) }
    }
    val loadBatch: (RollBatch) -> Unit = { batch ->
        actions.loadBatch(batch); loadRequest++
        scope.launch { snackbar.showSnackbar(loadedText) }
    }
    val loadResult: (RollResult) -> Unit = { result ->
        actions.loadResult(result); loadRequest++
        scope.launch { snackbar.showSnackbar(loadedText) }
    }
    val submit: () -> Unit = {
        if (actions.roll()) {
            focusManager.clearFocus(); keyboard?.hide()
        } else if (!state.busy && !state.blockedForRecovery) {
            focusTarget = FormFocus.VALIDATION; focusRequest++
        }
    }
    val openPalette: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
        showPalette = true
    }
    val feedback = transientStatusText(state)
    LaunchedEffect(state.status, state.resultRevision) {
        val key = "${state.status?.name ?: "NONE"}:${state.resultRevision}"
        if (key != handledFeedbackKey) {
            // Mark before suspending so recreating the screen never replays the same feedback.
            // Recording null transitions also permits a later cancellation at the same result revision.
            handledFeedbackKey = key
            if (feedback != null) snackbar.showSnackbar(feedback)
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.testTag("status")) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom).union(WindowInsets.ime))
                    .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PersistencePanel(state, actions.retrySave, actions.retryLoad, actions.recover,
                        onReset = { confirmReset = true }, onRetryClear = { confirmClear = true })
                    if (state.isRolling) {
                        LinearProgressIndicator(progress = { state.progress.toFloat() / state.requested.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.rolling_progress, state.progress, state.requested), Modifier.weight(1f))
                            TextButton(onClick = actions.cancel, modifier = Modifier.testTag("cancel_roll")) { Text(stringResource(R.string.cancel)) }
                        }
                    } else {
                        Text(modeText(state.mode), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = submit, enabled = !state.busy && !state.blockedForRecovery,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("roll_button")) {
                            val label = when {
                                state.isLoading -> stringResource(R.string.loading)
                                state.isSaving -> stringResource(R.string.saving)
                                else -> stringResource(R.string.roll_parameters, state.notation.ifBlank { stringResource(R.string.parameter_empty) },
                                    state.timesText.ifBlank { stringResource(R.string.parameter_empty) })
                            }
                            Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            val wide = maxWidth >= 720.dp && maxHeight >= 220.dp
            val formItems = buildList {
                if (wide) add(ScreenItem(ItemKind.TITLE))
                add(ScreenItem(ItemKind.QUICK_DICE)); add(ScreenItem(ItemKind.NOTATION)); add(ScreenItem(ItemKind.ADVANCED))
                if (expanded) {
                    add(ScreenItem(ItemKind.TIMES)); add(ScreenItem(ItemKind.MODE)); add(ScreenItem(ItemKind.NAME))
                }
            }
            val results = buildList {
                add(ScreenItem(ItemKind.LATEST)); add(ScreenItem(ItemKind.HISTORY_HEADING))
                state.history.drop(1).forEach { add(ScreenItem(ItemKind.BATCH, it)) }
            }
            val mainItems = if (wide) results else listOf(ScreenItem(ItemKind.LATEST)) + formItems + results.drop(1)
            val currentFormItems by rememberUpdatedState(if (wide) formItems else mainItems)
            val formList = if (wide) wideFormList else mainList
            LaunchedEffect(focusRequest) {
                if (focusRequest == 0 || state.blockedForRecovery) return@LaunchedEffect
                val target = when (focusTarget) {
                    FormFocus.NOTATION -> FormFocus.NOTATION
                    FormFocus.TIMES -> FormFocus.TIMES
                    FormFocus.VALIDATION -> when {
                        state.notationIssue != null -> FormFocus.NOTATION
                        state.timesIssue != null -> FormFocus.TIMES
                        else -> null
                    }
                } ?: return@LaunchedEffect
                if (target == FormFocus.TIMES) expanded = true
                withFrameNanos { }
                formList.scrollToKey(currentFormItems, if (target == FormFocus.NOTATION) ItemKind.NOTATION.name else ItemKind.TIMES.name)
                withFrameNanos { }
                if (target == FormFocus.NOTATION) notationFocus.requestFocus() else timesFocus.requestFocus()
            }
            LaunchedEffect(loadRequest) {
                if (loadRequest > 0) formList.scrollToKey(currentFormItems, ItemKind.QUICK_DICE.name)
            }
            LaunchedEffect(wide, state.isLoading) {
                if (state.isLoading) return@LaunchedEffect
                // Form items move out of this list in a wide window. Restore by key, never by the old index.
                val savedKey = mainAnchorKey
                val savedOffset = mainAnchorOffset
                val newResultPending = state.resultRevision > handledRevision
                if (savedKey != null && !newResultPending) {
                    withFrameNanos { }
                    val target = mainItems.indexOfFirst { it.key == savedKey }
                    if (target >= 0) mainList.scrollToItem(target, savedOffset)
                }
                snapshotFlow {
                    mainList.layoutInfo.visibleItemsInfo.firstOrNull()?.key?.toString()?.let {
                        it to mainList.firstVisibleItemScrollOffset
                    }
                }.filterNotNull().collect { (key, offset) ->
                    mainAnchorKey = key
                    mainAnchorOffset = offset
                }
            }
            LaunchedEffect(state.resultRevision) {
                if (state.resultRevision < handledRevision) handledRevision = state.resultRevision
                if (state.resultRevision > handledRevision) {
                    handledRevision = state.resultRevision
                    mainList.scrollToKey(mainItems, ItemKind.LATEST.name)
                }
            }
            val renderItem: @Composable (ScreenItem) -> Unit = { item ->
                when (item.kind) {
                    ItemKind.TITLE -> RollerHeader(openPalette)
                    ItemKind.LATEST -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!wide) RollerHeader(openPalette)
                            val latest = state.history.firstOrNull()
                            if (latest == null) {
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.ready_title), style = MaterialTheme.typography.titleLarge)
                                        Text(stringResource(R.string.ready_hint), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            } else BatchResultCard(latest, true, !state.busy, loadBatch, loadResult,
                                onCopyBatch = { copy(formatter.formatBatch(it)) }, onCopyResult = { copy(formatter.formatResult(it)) })
                        }
                    }
                    ItemKind.QUICK_DICE -> QuickDice(state, actions.quickDie)
                    ItemKind.NOTATION -> NotationField(state, actions.setNotation, notationFocus) {
                        focusTarget = FormFocus.TIMES; focusRequest++
                    }
                    ItemKind.ADVANCED -> AdvancedSettingsToggle(state, expanded) { expanded = !expanded }
                    ItemKind.TIMES -> TimesField(state, actions.setTimes, timesFocus, submit)
                    ItemKind.MODE -> ModeField(state, actions.setMode)
                    ItemKind.NAME -> NoteField(state, actions.setName, submit)
                    ItemKind.HISTORY_HEADING -> HistoryHeading(state) { confirmClear = true }
                    ItemKind.BATCH -> BatchResultCard(requireNotNull(item.batch), false, !state.busy, loadBatch, loadResult,
                        onCopyBatch = { copy(formatter.formatBatch(it)) }, onCopyResult = { copy(formatter.formatResult(it)) })
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize().testTag("wide_layout")) {
                    RollerItemList(formItems, wideFormList, Modifier.weight(1f).testTag("form_list"), renderItem)
                    VerticalDivider(Modifier.fillMaxHeight())
                    RollerItemList(mainItems, mainList, Modifier.weight(1f).testTag("main_list"), renderItem)
                }
            } else RollerItemList(mainItems, mainList, Modifier.fillMaxSize().testTag("main_list"), renderItem)
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.clear_title)) },
        text = { Text(stringResource(R.string.clear_batches_message, state.history.size, state.history.sumOf { it.count })) },
        confirmButton = { TextButton(onClick = { confirmClear = false; actions.clear() }, enabled = !state.busy && !state.blockedForRecovery,
            modifier = Modifier.testTag("confirm_clear")) { Text(stringResource(R.string.clear_history)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }, modifier = Modifier.testTag("dismiss_clear")) { Text(stringResource(R.string.cancel)) } })
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false },
        title = { Text(stringResource(R.string.reset_history_title)) },
        text = { Text(stringResource(R.string.reset_history_message)) },
        confirmButton = { TextButton(onClick = { confirmReset = false; actions.reset() }, enabled = !state.busy,
            modifier = Modifier.testTag("confirm_reset")) { Text(stringResource(R.string.reset_history)) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }, modifier = Modifier.testTag("dismiss_reset")) { Text(stringResource(R.string.cancel)) } })
    if (showPalette) PalettePickerDialog(palette, onPaletteChange, onDismiss = { showPalette = false })
}

@Composable
private fun RollerHeader(onPaletteClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.app_name), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = onPaletteClick, modifier = Modifier.heightIn(min = 48.dp).testTag("palette_button")) {
            Text(stringResource(R.string.palette_action))
        }
    }
}

private suspend fun LazyListState.scrollToKey(items: List<ScreenItem>, key: String) {
    val target = items.indexOfFirst { it.key == key }
    if (target >= 0) scrollToItem(target)
}

@Composable
private fun RollerItemList(items: List<ScreenItem>, state: LazyListState, modifier: Modifier, render: @Composable (ScreenItem) -> Unit) {
    LazyColumn(state = state, modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { it.key }, contentType = { it.kind }) { render(it) }
    }
}

@Composable
private fun HistoryHeading(state: RollerUiState, onClear: () -> Unit) {
    Column(Modifier.testTag("history_heading"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(pluralStringResource(R.plurals.history_batches_title, state.history.size, state.history.size), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onClear, enabled = state.history.isNotEmpty() && !state.busy && !state.blockedForRecovery,
                modifier = Modifier.testTag("clear_history")) { Text(stringResource(R.string.clear_history)) }
        }
        Text(stringResource(R.string.history_batch_retention, RollLimits.MAX_BATCHES, RollLimits.MAX_HISTORY_RESULTS, RollLimits.MAX_HISTORY_DICE),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.hasUnsavedChanges) Text(stringResource(R.string.history_not_saved), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (state.history.size <= 1) Text(stringResource(R.string.no_earlier_batches), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun transientStatusText(state: RollerUiState): String? = when (state.status) {
    RollStatus.COMPLETED -> pluralStringResource(R.plurals.rolls_completed, state.requested, state.requested)
    RollStatus.CANCELLED -> stringResource(R.string.roll_cancelled)
    RollStatus.CLEARED -> stringResource(R.string.history_cleared)
    RollStatus.SAVED -> stringResource(R.string.history_saved)
    RollStatus.RECOVERED -> stringResource(R.string.history_recovered)
    RollStatus.RESET -> stringResource(R.string.history_reset)
    RollStatus.ROLL_FAILED -> stringResource(R.string.roll_failed)
    RollStatus.LOAD_FAILED, RollStatus.SAVE_FAILED, RollStatus.CLEAR_FAILED, null -> null
}

@Composable
private fun rememberResultTextFormatter(): ResultTextFormatter {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(resources, locale) {
        ResultTextFormatter({ label ->
            resources.getString(when (label) {
                ResultTextLabel.BATCH -> R.string.export_batch
                ResultTextLabel.EXPRESSION -> R.string.dice_notation
                ResultTextLabel.MODE -> R.string.roll_mode
                ResultTextLabel.NOTE -> R.string.export_note
                ResultTextLabel.TIME -> R.string.export_time
                ResultTextLabel.COUNT -> R.string.batch_count
                ResultTextLabel.TOTAL -> R.string.batch_total
                ResultTextLabel.AVERAGE -> R.string.batch_average
                ResultTextLabel.MINIMUM -> R.string.batch_minimum
                ResultTextLabel.MAXIMUM -> R.string.batch_maximum
                ResultTextLabel.ROLL -> R.string.export_roll
                ResultTextLabel.FIRST_GROUP -> R.string.first_roll
                ResultTextLabel.SECOND_GROUP -> R.string.second_roll
                ResultTextLabel.CHOSEN_TOTAL -> R.string.export_chosen_total
                ResultTextLabel.MODIFIER -> R.string.export_modifier
                ResultTextLabel.RESULT -> R.string.export_result
                ResultTextLabel.NORMAL -> R.string.mode_normal
                ResultTextLabel.HIGHER -> R.string.mode_advantage
                ResultTextLabel.LOWER -> R.string.mode_disadvantage
            })
        }, locale)
    }
}
