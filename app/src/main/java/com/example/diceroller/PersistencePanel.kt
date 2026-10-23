package com.example.diceroller

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Persistence warnings stay beside the fixed action, independently of transient status. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersistencePanel(
    state: RollerUiState,
    onRetrySave: () -> Unit,
    onRetryLoad: () -> Unit,
    onRecover: () -> Unit,
    onReset: () -> Unit,
    onRetryClear: () -> Unit,
) {
    if (!state.blockedForRecovery && !state.hasUnsavedChanges && !state.clearFailed && !state.recoveryFailed) return
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag("persistence_panel")) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.blockedForRecovery) {
                val message = when (state.loadIssue) {
                    HistoryLoadIssue.RECOVERED_RECORDS -> pluralStringResource(R.plurals.history_recovered_records, state.skippedRecords, state.skippedRecords)
                    HistoryLoadIssue.UNSUPPORTED_VERSION -> stringResource(R.string.history_unsupported_version)
                    HistoryLoadIssue.CORRUPT_FILE -> stringResource(R.string.history_corrupt_file)
                    null -> stringResource(R.string.history_read_error)
                }
                Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("load_warning"),
                    style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRetryLoad, enabled = !state.busy, modifier = Modifier.testTag("retry_load")) { Text(stringResource(R.string.retry_load)) }
                    if (state.loadIssue == HistoryLoadIssue.RECOVERED_RECORDS && state.history.isNotEmpty()) {
                        TextButton(onClick = onRecover, enabled = !state.busy, modifier = Modifier.testTag("recover_history")) { Text(stringResource(R.string.recover_valid_history)) }
                    }
                    TextButton(onClick = onReset, enabled = !state.busy, modifier = Modifier.testTag("reset_history")) { Text(stringResource(R.string.reset_history)) }
                }
            }
            if (state.recoveryFailed) Text(stringResource(R.string.history_recovery_failed), modifier = Modifier.testTag("recovery_warning"), style = MaterialTheme.typography.bodySmall)
            if (state.hasUnsavedChanges) {
                Text(stringResource(R.string.history_save_failed), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("unsaved_warning"),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRetrySave, enabled = !state.busy && !state.blockedForRecovery,
                    modifier = Modifier.testTag("retry_save")) { Text(stringResource(R.string.retry_save)) }
            }
            if (state.clearFailed) {
                Text(stringResource(R.string.history_clear_failed), modifier = Modifier.testTag("clear_warning"), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRetryClear, enabled = !state.busy && !state.blockedForRecovery,
                    modifier = Modifier.testTag("retry_clear")) { Text(stringResource(R.string.retry_clear)) }
            }
        }
    }
}
