package com.example.diceroller

/** Syntax examples are shared across translations; their operators must remain valid input. */
object DiceNotationExamples {
    val values: List<String> = listOf(
        "d20",
        DiceExpression(2, 6, 3).notation,
        DiceExpression(4, 8, -2).notation,
    )
}
