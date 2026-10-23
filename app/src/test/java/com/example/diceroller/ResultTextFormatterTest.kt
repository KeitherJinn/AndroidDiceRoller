package com.example.diceroller

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class ResultTextFormatterTest {
    private val formatter = ResultTextFormatter({ it.name }, Locale.US)

    private fun result(
        id: String = "result",
        values: List<Int> = listOf(1, 2),
        second: List<Int>? = null,
        modifier: Long = 0,
        mode: RollMode = RollMode.NORMAL,
        name: String = "",
    ) = RollResult(
        id, name, DiceExpression(values.size, 1000, modifier).notation, mode,
        DiceRoll(values), second?.let(::DiceRoll), modifier, 1_750_000_000_000L,
    )

    @Test fun copyIncludesEveryDieBeyondTheVisiblePage() {
        val values = (1..137).toList()
        val text = formatter.formatResult(result(values = values, modifier = -2))
        val copiedValues = text.lineSequence().first { it.startsWith("FIRST_GROUP:") }
            .substringAfter('[').substringBefore(']').split(", ").map(String::toInt)
        assertEquals(values, copiedValues)
        assertTrue(text.contains("MODIFIER: -2"))
        assertTrue(text.contains("RESULT: 9,451"))
        assertFalse(text.contains("SECOND_GROUP"))
    }

    @Test fun copiedComparisonIncludesBothGroupsChosenTotalAndOneModifier() {
        val text = formatter.formatResult(result(values = listOf(1, 6), second = listOf(4, 4), modifier = 3, mode = RollMode.ADVANTAGE, name = "攻击"))
        assertTrue(text.contains("NOTE: 攻击"))
        assertTrue(text.contains("EXPRESSION: 2d1000+3"))
        assertTrue(text.contains("MODE: HIGHER"))
        assertTrue(text.contains("FIRST_GROUP: [1, 6] = 7"))
        assertTrue(text.contains("SECOND_GROUP: [4, 4] = 8"))
        assertTrue(text.contains("CHOSEN_TOTAL: 8"))
        assertTrue(text.contains("MODIFIER: +3"))
        assertTrue(text.endsWith("RESULT: 11"))
    }

    @Test fun copiedBatchContainsSummaryAndEveryResultInOriginalOrder() {
        val batch = RollBatch("batch", listOf(result("first", listOf(2)), result("second", listOf(5))))
        val text = formatter.formatBatch(batch)
        assertTrue(text.contains("COUNT: 2"))
        assertTrue(text.contains("TOTAL: 7"))
        assertTrue(text.contains("AVERAGE: 3.5"))
        assertTrue(text.contains("MINIMUM: 2"))
        assertTrue(text.contains("MAXIMUM: 5"))
        val copiedResults = text.lineSequence().filter { it.startsWith("RESULT:") }.toList()
        assertEquals(listOf("RESULT: 2", "RESULT: 5"), copiedResults)
        assertTrue(text.indexOf("ROLL 1") < text.indexOf("ROLL 2"))
    }

    @Test fun copiedLargeResultsUseLongArithmeticAndLocalizedLabels() {
        val localized = ResultTextFormatter({ "中文_${it.name}" }, Locale.CHINA)
        val text = localized.formatResult(result(values = listOf(1), modifier = Int.MAX_VALUE.toLong()))
        assertTrue(text.contains("中文_RESULT: 2,147,483,648"))
        assertTrue(text.contains("中文_MODE: 中文_NORMAL"))
        assertFalse(text.contains("NOTE:"))
    }
}
