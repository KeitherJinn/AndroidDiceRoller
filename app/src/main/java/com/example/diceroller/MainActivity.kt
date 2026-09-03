package com.example.diceroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random


enum class RollMode {
    NORMAL,
    ADVANTAGE,
    DISADVANTAGE
}


data class DiceExpression(
    val numberOfDice: Int,
    val sides: Int,
    val modifier: Int
)


data class DiceRoll(
    val values: List<Int>,
    val sum: Int
)


data class RollResult(
    val name: String,
    val notation: String,
    val mode: RollMode,

    val firstRoll: DiceRoll,
    val secondRoll: DiceRoll?,

    val chosenRoll: DiceRoll,

    val modifier: Int,
    val finalResult: Int,

    val timestamp: String
)


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                DiceRollerApp()
            }
        }
    }
}


@Composable
fun DiceRollerApp() {

    var name by remember {
        mutableStateOf("")
    }

    var notation by remember {
        mutableStateOf("d20")
    }

    var timesText by remember {
        mutableStateOf("1")
    }

    var rollMode by remember {
        mutableStateOf(RollMode.NORMAL)
    }

    /*
     * Roll history exists only in memory.
     *
     * Closing/killing the app will remove it.
     */
    var results by remember {
        mutableStateOf<List<RollResult>>(emptyList())
    }

    var statusMessage by remember {
        mutableStateOf("")
    }


    Surface(
        modifier = Modifier.fillMaxSize()
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(
                    rememberScrollState()
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            Text(
                text = "Dice Roller",
                style = MaterialTheme.typography.headlineMedium
            )


            // -------------------------
            // Name
            // -------------------------

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                },
                label = {
                    Text("Name")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )


            // -------------------------
            // Dice notation
            // -------------------------

            OutlinedTextField(
                value = notation,
                onValueChange = {
                    notation = it
                },
                label = {
                    Text("Dice notation")
                },
                supportingText = {
                    Text(
                        "Examples: d20, 2d6+3, 4d8-2"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )


            // -------------------------
            // Repeat count
            // -------------------------

            OutlinedTextField(
                value = timesText,
                onValueChange = {
                    timesText = it
                },
                label = {
                    Text("Times")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )


            // -------------------------
            // Roll mode
            // -------------------------

            Text(
                text = "Roll Mode",
                style = MaterialTheme.typography.titleMedium
            )


            RollModeRow(
                text = "Normal",
                selected = rollMode == RollMode.NORMAL
            ) {
                rollMode = RollMode.NORMAL
            }


            RollModeRow(
                text = "Advantage",
                selected = rollMode == RollMode.ADVANTAGE
            ) {
                rollMode = RollMode.ADVANTAGE
            }


            RollModeRow(
                text = "Disadvantage",
                selected = rollMode == RollMode.DISADVANTAGE
            ) {
                rollMode = RollMode.DISADVANTAGE
            }


            // -------------------------
            // Roll
            // -------------------------

            Button(
                onClick = {

                    val dice =
                        parseDiceNotation(notation)

                    val times =
                        timesText.toIntOrNull()


                    when {

                        name.isBlank() -> {

                            statusMessage =
                                "Please enter a name."
                        }


                        dice == null -> {

                            statusMessage =
                                "Invalid dice notation."
                        }


                        times == null || times <= 0 -> {

                            statusMessage =
                                "Times must be a positive integer."
                        }


                        else -> {

                            val newResults =
                                mutableListOf<RollResult>()


                            repeat(times) {

                                newResults.add(
                                    performRoll(
                                        name = name,
                                        notation = notation,
                                        dice = dice,
                                        mode = rollMode
                                    )
                                )
                            }


                            /*
                             * Newest results appear at the top.
                             */
                            results =
                                newResults.reversed() + results


                            statusMessage =
                                "$times roll(s) completed."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text("ROLL")
            }


            // -------------------------
            // Clear history
            // -------------------------

            OutlinedButton(
                onClick = {

                    results = emptyList()

                    statusMessage =
                        "Roll history cleared."
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text("Clear History")
            }


            if (statusMessage.isNotEmpty()) {

                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
            }


            HorizontalDivider()


            // -------------------------
            // History
            // -------------------------

            Text(
                text = "Roll History",
                style = MaterialTheme.typography.titleLarge
            )


            if (results.isEmpty()) {

                Text(
                    text = "No rolls yet."
                )

            } else {

                results.forEach { result ->

                    RollResultCard(
                        result = result
                    )
                }
            }
        }
    }
}


@Composable
fun RollModeRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {

        RadioButton(
            selected = selected,
            onClick = onClick
        )

        Text(text)
    }
}


@Composable
fun RollResultCard(
    result: RollResult
) {

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {

            Text(
                text =
                    "${result.name} — ${result.notation}",
                style =
                    MaterialTheme.typography.titleMedium
            )


            Text(
                text =
                    "Mode: ${formatRollMode(result.mode)}"
            )


            Text(
                text =
                    "Roll 1: ${formatDiceValues(result.firstRoll.values)} = ${result.firstRoll.sum}"
            )


            result.secondRoll?.let { second ->

                Text(
                    text =
                        "Roll 2: ${formatDiceValues(second.values)} = ${second.sum}"
                )
            }


            Text(
                text =
                    "Chosen Roll: ${result.chosenRoll.sum}"
            )


            Text(
                text =
                    "Modifier: ${formatModifier(result.modifier)}"
            )


            Text(
                text =
                    "Final Result: ${result.finalResult}",
                style =
                    MaterialTheme.typography.titleLarge
            )


            Text(
                text = result.timestamp,
                style =
                    MaterialTheme.typography.bodySmall
            )
        }
    }
}


// ============================================================
// Dice notation parsing
// ============================================================

fun parseDiceNotation(
    notation: String
): DiceExpression? {

    /*
     * Valid examples:
     *
     * d20
     * D20
     * 1d20
     * 2d6
     * 2d6+3
     * 4d8-2
     */

    val regex =
        Regex(
            """^\s*(\d*)[dD](\d+)\s*([+-]\s*\d+)?\s*$"""
        )


    val match =
        regex.matchEntire(notation)
            ?: return null


    val numberPart =
        match.groupValues[1]


    val sidesPart =
        match.groupValues[2]


    val modifierPart =
        match.groupValues[3]
            .replace(" ", "")


    val numberOfDice =

        if (numberPart.isBlank()) {

            1

        } else {

            numberPart.toIntOrNull()
                ?: return null
        }


    val sides =
        sidesPart.toIntOrNull()
            ?: return null


    val modifier =

        if (modifierPart.isBlank()) {

            0

        } else {

            modifierPart.toIntOrNull()
                ?: return null
        }


    if (numberOfDice <= 0) {
        return null
    }

    if (sides <= 0) {
        return null
    }


    /*
     * Safety limits.
     *
     * Prevent accidental inputs like:
     *
     * 999999999d999999999
     */

    if (numberOfDice > 1000) {
        return null
    }

    if (sides > 1_000_000) {
        return null
    }


    return DiceExpression(
        numberOfDice = numberOfDice,
        sides = sides,
        modifier = modifier
    )
}


// ============================================================
// Roll dice
// ============================================================

fun rollDice(
    dice: DiceExpression
): DiceRoll {

    val values =
        MutableList(
            dice.numberOfDice
        ) {

            Random.nextInt(
                from = 1,
                until = dice.sides + 1
            )
        }


    return DiceRoll(
        values = values,
        sum = values.sum()
    )
}


// ============================================================
// Normal / Advantage / Disadvantage
// ============================================================

fun performRoll(
    name: String,
    notation: String,
    dice: DiceExpression,
    mode: RollMode
): RollResult {

    /*
     * First roll always exists.
     */
    val firstRoll =
        rollDice(dice)


    /*
     * Normal:
     *      only one roll
     *
     * Advantage / Disadvantage:
     *      create a second full roll
     */
    val secondRoll =

        when (mode) {

            RollMode.NORMAL -> {
                null
            }

            RollMode.ADVANTAGE,
            RollMode.DISADVANTAGE -> {
                rollDice(dice)
            }
        }


    /*
     * Determine which roll counts.
     */
    val chosenRoll =

        when (mode) {

            RollMode.NORMAL -> {

                firstRoll
            }


            RollMode.ADVANTAGE -> {

                if (
                    firstRoll.sum >=
                    secondRoll!!.sum
                ) {
                    firstRoll
                } else {
                    secondRoll
                }
            }


            RollMode.DISADVANTAGE -> {

                if (
                    firstRoll.sum <=
                    secondRoll!!.sum
                ) {
                    firstRoll
                } else {
                    secondRoll
                }
            }
        }


    /*
     * Modifier is added AFTER the roll
     * has been selected.
     *
     * Example:
     *
     * d20+5 advantage
     *
     * Roll 1 = 12
     * Roll 2 = 17
     *
     * chosen = 17
     *
     * final = 17 + 5
     *       = 22
     */
    val finalResult =
        chosenRoll.sum +
                dice.modifier


    val timestamp =
        SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(
            Date()
        )


    return RollResult(
        name = name,
        notation = notation,
        mode = mode,

        firstRoll = firstRoll,
        secondRoll = secondRoll,

        chosenRoll = chosenRoll,

        modifier = dice.modifier,
        finalResult = finalResult,

        timestamp = timestamp
    )
}


// ============================================================
// Display helpers
// ============================================================

fun formatRollMode(
    mode: RollMode
): String {

    return when (mode) {

        RollMode.NORMAL ->
            "Normal"

        RollMode.ADVANTAGE ->
            "Advantage"

        RollMode.DISADVANTAGE ->
            "Disadvantage"
    }
}


fun formatModifier(
    modifier: Int
): String {

    return if (modifier >= 0) {

        "+$modifier"

    } else {

        modifier.toString()
    }
}


fun formatDiceValues(
    values: List<Int>
): String {

    return values.joinToString(
        prefix = "[",
        postfix = "]",
        separator = ", "
    )
}