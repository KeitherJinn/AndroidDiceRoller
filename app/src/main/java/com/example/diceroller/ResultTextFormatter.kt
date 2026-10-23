package com.example.diceroller

import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

enum class ResultTextLabel {
    BATCH, EXPRESSION, MODE, NOTE, TIME, COUNT, TOTAL, AVERAGE, MINIMUM, MAXIMUM,
    ROLL, FIRST_GROUP, SECOND_GROUP, CHOSEN_TOTAL, MODIFIER, RESULT, NORMAL, HIGHER, LOWER,
}

/** Pure text formatting: every raw value is included, independent of the UI page. */
class ResultTextFormatter(
    private val label: (ResultTextLabel) -> String,
    private val locale: Locale = Locale.getDefault(),
) {
    private val numbers = NumberFormat.getIntegerInstance(locale)

    fun formatBatch(batch: RollBatch): String = buildString {
        appendLine(label(ResultTextLabel.BATCH))
        appendLine(field(ResultTextLabel.COUNT, integer(batch.count)))
        appendLine(field(ResultTextLabel.TOTAL, integer(batch.total)))
        appendLine(field(ResultTextLabel.AVERAGE, NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = 2
        }.format(batch.average)))
        appendLine(field(ResultTextLabel.MINIMUM, integer(batch.minimum)))
        appendLine(field(ResultTextLabel.MAXIMUM, integer(batch.maximum)))
        batch.results.forEachIndexed { index, result ->
            appendLine()
            appendLine("${label(ResultTextLabel.ROLL)} ${integer(index + 1)}")
            appendLine(formatResult(result))
        }
    }.trimEnd()

    fun formatResult(result: RollResult): String = buildString {
        if (result.name.isNotBlank()) appendLine(field(ResultTextLabel.NOTE, result.name))
        appendLine(field(ResultTextLabel.EXPRESSION, result.notation))
        appendLine(field(ResultTextLabel.MODE, mode(result.mode)))
        appendLine(field(ResultTextLabel.TIME, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, locale).format(Date(result.timestamp))))
        appendLine(group(ResultTextLabel.FIRST_GROUP, result.firstRoll))
        result.secondRoll?.let { appendLine(group(ResultTextLabel.SECOND_GROUP, it)) }
        appendLine(field(ResultTextLabel.CHOSEN_TOTAL, integer(result.chosenRoll.sum)))
        appendLine(field(ResultTextLabel.MODIFIER, (if (result.modifier >= 0) "+" else "") + integer(result.modifier)))
        append(field(ResultTextLabel.RESULT, integer(result.finalResult)))
    }

    private fun group(key: ResultTextLabel, roll: DiceRoll) =
        "${label(key)}: [${roll.values.joinToString(", ") { integer(it) }}] = ${integer(roll.sum)}"

    private fun field(key: ResultTextLabel, value: String) = "${label(key)}: $value"
    private fun integer(value: Number) = numbers.format(value)
    private fun mode(mode: RollMode) = label(when (mode) {
        RollMode.NORMAL -> ResultTextLabel.NORMAL
        RollMode.ADVANTAGE -> ResultTextLabel.HIGHER
        RollMode.DISADVANTAGE -> ResultTextLabel.LOWER
    })
}
