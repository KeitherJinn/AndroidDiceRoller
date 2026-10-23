package com.example.diceroller

import java.util.UUID
import java.text.Normalizer
import kotlin.random.Random

object RollLimits {
    const val MAX_DICE = 1_000
    const val MAX_SIDES = 1_000_000
    const val MAX_REPEATS = 100
    const val MAX_TOTAL_DICE = 10_000
    const val MAX_HISTORY = 100
    const val MAX_BATCHES = 100
    const val MAX_HISTORY_RESULTS = 1_000
    const val MAX_HISTORY_DICE = 200_000
    const val MAX_NOTATION_LENGTH = 128
    const val MAX_NAME_LENGTH = 80
    const val DISPLAYED_VALUES = 50
}

enum class RollMode { NORMAL, ADVANTAGE, DISADVANTAGE }
enum class InputIssue { NOTATION_FORMAT, NOTATION_LENGTH, DICE_LIMIT, SIDES_LIMIT, MODIFIER_LIMIT, REPEAT_LIMIT, WORK_LIMIT }

data class DiceExpression(val numberOfDice: Int, val sides: Int, val modifier: Long) {
    val notation: String
        get() = "$numberOfDice" + "d$sides" + when {
            modifier > 0 -> "+$modifier"
            modifier < 0 -> modifier.toString()
            else -> ""
        }
}

data class DiceRoll(val values: List<Int>) {
    val sum: Long get() = values.sumOf { it.toLong() }
}

data class RollResult(
    val id: String,
    val name: String,
    val notation: String,
    val mode: RollMode,
    val firstRoll: DiceRoll,
    val secondRoll: DiceRoll?,
    val modifier: Long,
    val timestamp: Long,
) {
    val chosenRoll: DiceRoll
        get() = when (mode) {
            RollMode.NORMAL -> firstRoll
            RollMode.ADVANTAGE -> if (firstRoll.sum >= requireNotNull(secondRoll).sum) firstRoll else secondRoll
            RollMode.DISADVANTAGE -> if (firstRoll.sum <= requireNotNull(secondRoll).sum) firstRoll else secondRoll
        }
    val finalResult: Long get() = chosenRoll.sum + modifier
}

data class ParsedDice(val dice: DiceExpression? = null, val issue: InputIssue? = null)
data class RollRequest(val name: String, val dice: DiceExpression, val times: Int, val mode: RollMode)
data class RequestValidation(
    val request: RollRequest? = null,
    val notationIssue: InputIssue? = null,
    val timesIssue: InputIssue? = null,
)

private val notationPattern = Regex("""^\s*(\d*)[dD](\d+)\s*([+-]\s*\d+)?\s*$""")

fun parseDiceInput(notation: String): ParsedDice {
    if (notation.length > RollLimits.MAX_NOTATION_LENGTH) return ParsedDice(issue = InputIssue.NOTATION_LENGTH)
    val normalized = Normalizer.normalize(notation, Normalizer.Form.NFKC)
    val match = notationPattern.matchEntire(normalized) ?: return ParsedDice(issue = InputIssue.NOTATION_FORMAT)
    val countText = match.groupValues[1]
    val count = if (countText.isEmpty()) 1 else countText.toIntOrNull()
    if (count == null || count !in 1..RollLimits.MAX_DICE) return ParsedDice(issue = InputIssue.DICE_LIMIT)
    val sides = match.groupValues[2].toIntOrNull()
    if (sides == null || sides !in 1..RollLimits.MAX_SIDES) return ParsedDice(issue = InputIssue.SIDES_LIMIT)
    val modifierText = match.groupValues[3].filterNot { it.isWhitespace() }
    val modifier = if (modifierText.isEmpty()) 0L else modifierText.toLongOrNull()
    // Preserve the original accepted modifier range; all arithmetic now uses Long.
    if (modifier == null || modifier !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        return ParsedDice(issue = InputIssue.MODIFIER_LIMIT)
    }
    return ParsedDice(DiceExpression(count, sides, modifier))
}

fun parseDiceNotation(notation: String): DiceExpression? = parseDiceInput(notation).dice

fun validateRequest(name: String, notation: String, timesText: String, mode: RollMode): RequestValidation {
    val parsed = parseDiceInput(notation)
    val times = Normalizer.normalize(timesText.trim(), Normalizer.Form.NFKC).toIntOrNull()
    val timesIssue = when {
        times == null || times !in 1..RollLimits.MAX_REPEATS -> InputIssue.REPEAT_LIMIT
        parsed.dice != null && parsed.dice.numberOfDice.toLong() * times *
            (if (mode == RollMode.NORMAL) 1 else 2) > RollLimits.MAX_TOTAL_DICE -> InputIssue.WORK_LIMIT
        else -> null
    }
    return if (parsed.dice != null && timesIssue == null) {
        RequestValidation(RollRequest(name.trim().take(RollLimits.MAX_NAME_LENGTH), parsed.dice, requireNotNull(times), mode))
    } else {
        RequestValidation(notationIssue = parsed.issue, timesIssue = timesIssue)
    }
}

class DiceEngine(
    private val random: Random = Random.Default,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun roll(request: RollRequest, checkpoint: () -> Unit = {}): RollResult {
        val dice = request.dice
        require(dice.numberOfDice in 1..RollLimits.MAX_DICE && dice.sides in 1..RollLimits.MAX_SIDES)
        require(dice.modifier in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        fun rollGroup() = DiceRoll(List(dice.numberOfDice) { index ->
            if (index % 64 == 0) checkpoint()
            random.nextInt(1, dice.sides + 1)
        })
        val first = rollGroup()
        val second = if (request.mode == RollMode.NORMAL) null else rollGroup()
        return RollResult(newId(), request.name, dice.notation, request.mode, first, second, dice.modifier, now())
    }
}

fun mergeHistory(newestFirst: List<RollResult>, previous: List<RollResult>): List<RollResult> =
    (newestFirst + previous).distinctBy { it.id }.take(RollLimits.MAX_HISTORY)
