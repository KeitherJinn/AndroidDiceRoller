package com.example.diceroller

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

private const val RESULTS_PER_PAGE = 10
private const val VALUES_PER_PAGE = 50

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BatchResultCard(
    batch: RollBatch,
    latest: Boolean,
    enabled: Boolean,
    onLoadBatch: (RollBatch) -> Unit,
    onLoadResult: (RollResult) -> Unit,
    onCopyBatch: (RollBatch) -> Unit,
    onCopyResult: (RollResult) -> Unit,
) {
    var expanded by rememberSaveable(batch.id) { mutableStateOf(false) }
    var page by rememberSaveable(batch.id) { mutableIntStateOf(0) }
    val locale = LocalConfiguration.current.locales[0]
    val integer = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val decimal = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 } }
    val single = batch.count == 1
    Card(Modifier.fillMaxWidth().testTag(if (latest) "latest_result" else "history_${batch.id}"),
        colors = CardDefaults.cardColors(containerColor = if (latest) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp).testTag("batch_${batch.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (latest) Text(stringResource(if (single) R.string.latest_result else R.string.latest_batch), style = MaterialTheme.typography.labelLarge)
            if (batch.name.isNotBlank()) Text(batch.name, style = MaterialTheme.typography.titleSmall)
            Text(integer.format(batch.total),
                modifier = Modifier.testTag(if (latest && single) "final_result" else "batch_total_${batch.id}"),
                style = if (latest) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium)
            if (!single) Text(stringResource(R.string.batch_total), style = MaterialTheme.typography.labelSmall)
            Text(stringResource(R.string.result_meta, batch.notation, modeText(batch.mode)), style = MaterialTheme.typography.bodyMedium)
            if (!single) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BatchMetric(stringResource(R.string.batch_count), integer.format(batch.count))
                    BatchMetric(stringResource(R.string.batch_average), decimal.format(batch.average))
                    BatchMetric(stringResource(R.string.batch_minimum), integer.format(batch.minimum))
                    BatchMetric(stringResource(R.string.batch_maximum), integer.format(batch.maximum))
                }
            }
            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(batch.timestamp)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag(if (latest) "latest_details" else "details_${batch.id}")) {
                    Text(stringResource(if (expanded) R.string.hide_details else if (single) R.string.show_details else R.string.show_batch_details))
                }
                TextButton(onClick = { onLoadBatch(batch) }, enabled = enabled, modifier = Modifier.testTag("load_batch_${batch.id}")) { Text(stringResource(R.string.load_settings)) }
                TextButton(onClick = { onCopyBatch(batch) }, modifier = Modifier.testTag("copy_batch_${batch.id}")) { Text(stringResource(R.string.copy_batch)) }
            }
            if (expanded) {
                HorizontalDivider()
                val pageCount = (batch.count + RESULTS_PER_PAGE - 1) / RESULTS_PER_PAGE
                val safePage = page.coerceIn(0, pageCount - 1)
                if (!single) Text(stringResource(R.string.results_page_range, safePage * RESULTS_PER_PAGE + 1,
                    minOf((safePage + 1) * RESULTS_PER_PAGE, batch.count), batch.count), style = MaterialTheme.typography.labelMedium)
                batch.results.drop(safePage * RESULTS_PER_PAGE).take(RESULTS_PER_PAGE).forEachIndexed { index, result ->
                    key(result.id) {
                        SingleRollDetails(result, safePage * RESULTS_PER_PAGE + index + 1, single, enabled, onLoadResult, onCopyResult)
                    }
                }
                if (pageCount > 1) PageControls(safePage, pageCount, "batch_${batch.id}", onPage = { page = it })
            }
        }
    }
}

@Composable
private fun BatchMetric(label: String, value: String) {
    Column(Modifier.widthIn(min = 64.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SingleRollDetails(
    result: RollResult,
    ordinal: Int,
    single: Boolean,
    enabled: Boolean,
    onLoad: (RollResult) -> Unit,
    onCopy: (RollResult) -> Unit,
) {
    var expanded by rememberSaveable(result.id) { mutableStateOf(single) }
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    Column(Modifier.fillMaxWidth().testTag("roll_${result.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!single) {
            Text(stringResource(R.string.roll_number_result, ordinal, numbers.format(result.finalResult)), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("result_details_${result.id}")) {
                Text(stringResource(if (expanded) R.string.hide_details else R.string.show_details))
            }
        }
        if (expanded) {
            GroupDetails(result.id, "first", stringResource(R.string.first_roll), result.firstRoll)
            result.secondRoll?.let { GroupDetails(result.id, "second", stringResource(R.string.second_roll), it) }
            if (result.secondRoll != null) Text(stringResource(R.string.chosen_total, numbers.format(result.chosenRoll.sum)), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.modifier_value, (if (result.modifier >= 0) "+" else "") + numbers.format(result.modifier)), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.final_value, numbers.format(result.finalResult)), style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onLoad(result) }, enabled = enabled, modifier = Modifier.testTag("load_result_${result.id}")) { Text(stringResource(R.string.load_one)) }
            TextButton(onClick = { onCopy(result) }, modifier = Modifier.testTag("copy_result_${result.id}")) { Text(stringResource(R.string.copy_one)) }
        }
        if (!single) HorizontalDivider()
    }
}

@Composable
private fun GroupDetails(resultId: String, groupId: String, label: String, roll: DiceRoll) {
    var page by rememberSaveable(resultId, groupId) { mutableIntStateOf(0) }
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val pageCount = (roll.values.size + VALUES_PER_PAGE - 1) / VALUES_PER_PAGE
    val safePage = page.coerceIn(0, pageCount - 1)
    val tag = "group_${resultId}_$groupId"
    Column(Modifier.testTag(tag), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.group_total, label, numbers.format(roll.sum)), style = MaterialTheme.typography.labelLarge)
        Text(roll.values.drop(safePage * VALUES_PER_PAGE).take(VALUES_PER_PAGE).joinToString(", ") { numbers.format(it) },
            modifier = Modifier.testTag("${tag}_values"), style = MaterialTheme.typography.bodyMedium)
        if (pageCount > 1) {
            Text(stringResource(R.string.dice_page_range, safePage * VALUES_PER_PAGE + 1,
                minOf((safePage + 1) * VALUES_PER_PAGE, roll.values.size), roll.values.size), style = MaterialTheme.typography.bodySmall)
            PageControls(safePage, pageCount, tag) { page = it }
        }
    }
}

@Composable
private fun PageControls(page: Int, pageCount: Int, tag: String, onPage: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { onPage(page - 1) }, enabled = page > 0, modifier = Modifier.testTag("${tag}_previous")) { Text(stringResource(R.string.previous_page)) }
        TextButton(onClick = { onPage(page + 1) }, enabled = page + 1 < pageCount, modifier = Modifier.testTag("${tag}_next")) { Text(stringResource(R.string.next_page)) }
    }
}
