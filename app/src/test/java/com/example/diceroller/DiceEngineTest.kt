package com.example.diceroller

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException
import kotlin.random.Random

class DiceEngineTest {
    private fun request(notation: String = "2d6+3", mode: RollMode = RollMode.NORMAL) =
        requireNotNull(validateRequest("", notation, "1", mode).request)

    private class SequenceRandom(private vararg val values: Int) : Random() {
        var count = 0
        override fun nextBits(bitCount: Int): Int = error("Unexpected random call")
        override fun nextInt(from: Int, until: Int): Int = values[count++].also { require(it in from until until) }
    }

    @Test fun parsesSupportedExpressionsAndNormalizesWhitespace() {
        assertEquals(DiceExpression(1, 20, 0), parseDiceNotation("D20"))
        assertEquals(DiceExpression(2, 6, 3), parseDiceNotation(" 2d6 +\t3 "))
        assertEquals(DiceExpression(4, 8, -2), parseDiceNotation("4d8-2"))
        assertEquals("1d20", parseDiceNotation("d20")!!.notation)
    }

    @Test fun rejectsMalformedExpressions() {
        listOf("", "d", "-1d6", "2d6+", "2d6+1d4", "1.5d6").forEach { assertNull(it, parseDiceNotation(it)) }
    }

    @Test fun fullWidthChineseKeyboardInputIsNormalized() {
        assertEquals(DiceExpression(2, 6, 3), parseDiceNotation("２ｄ６＋３"))
        assertEquals(3, validateRequest("", "２Ｄ６－２", "３", RollMode.NORMAL).request!!.times)
    }

    @Test fun enforcesDiceAndSideBoundaries() {
        assertNotNull(parseDiceNotation("1000d1000000"))
        listOf("0d6", "1001d6", "2147483648d6").forEach { assertEquals(InputIssue.DICE_LIMIT, parseDiceInput(it).issue) }
        listOf("d0", "d1000001", "d2147483648").forEach { assertEquals(InputIssue.SIDES_LIMIT, parseDiceInput(it).issue) }
    }

    @Test fun enforcesModifierBoundaries() {
        assertNotNull(parseDiceNotation("d1-2147483648"))
        assertNotNull(parseDiceNotation("d1+2147483647"))
        listOf("d1+2147483648", "d1-2147483649", "d1+999999999999999999999").forEach {
            assertEquals(InputIssue.MODIFIER_LIMIT, parseDiceInput(it).issue)
        }
    }

    @Test fun longInputIsRejectedBeforeParsing() {
        assertEquals(InputIssue.NOTATION_LENGTH, parseDiceInput("1".repeat(10000) + "d6").issue)
        assertEquals(InputIssue.NOTATION_LENGTH, parseDiceInput("d1" + " ".repeat(123) + "+100").issue)
    }

    @Test fun repeatCountHasABoundedPositiveRange() {
        listOf("", "0", "-1", "101", "2147483647", "2147483648", "1.5").forEach {
            assertEquals(it, InputIssue.REPEAT_LIMIT, validateRequest("", "d20", it, RollMode.NORMAL).timesIssue)
        }
        assertNotNull(validateRequest("", "d20", " 100 ", RollMode.NORMAL).request)
    }

    @Test fun workLimitIncludesSecondGroup() {
        assertNotNull(validateRequest("", "1000d6", "10", RollMode.NORMAL).request)
        assertEquals(InputIssue.WORK_LIMIT, validateRequest("", "1000d6", "11", RollMode.NORMAL).timesIssue)
        assertNotNull(validateRequest("", "1000d6", "5", RollMode.ADVANTAGE).request)
        assertEquals(InputIssue.WORK_LIMIT, validateRequest("", "1000d6", "6", RollMode.DISADVANTAGE).timesIssue)
    }

    @Test fun nameIsOptionalAndTrimmed() {
        assertEquals("", request().name)
        assertEquals("Attack", validateRequest(" Attack ", "d20", "1", RollMode.NORMAL).request!!.name)
    }

    @Test fun normalModeRollsOneGroupAndAddsModifierOnce() {
        val random = SequenceRandom(1, 6)
        val result = DiceEngine(random, { 1234L }, { "test" }).roll(request())
        assertEquals(2, random.count)
        assertNull(result.secondRoll)
        assertEquals(10L, result.finalResult)
        assertEquals(1234L, result.timestamp)
        assertEquals("test", result.id)
    }

    @Test fun advantageComparesWholeGroupTotals() {
        val result = DiceEngine(SequenceRandom(1, 6, 4, 4)).roll(request(mode = RollMode.ADVANTAGE))
        assertEquals(listOf(4, 4), result.chosenRoll.values)
        assertEquals(11L, result.finalResult)
    }

    @Test fun disadvantageComparesWholeGroupTotals() {
        val result = DiceEngine(SequenceRandom(1, 6, 4, 4)).roll(request(mode = RollMode.DISADVANTAGE))
        assertEquals(listOf(1, 6), result.chosenRoll.values)
        assertEquals(10L, result.finalResult)
    }

    @Test fun tiedGroupsChooseFirstGroup() {
        RollMode.entries.filter { it != RollMode.NORMAL }.forEach { mode ->
            val result = DiceEngine(SequenceRandom(1, 6, 3, 4)).roll(request(mode = mode))
            assertSame(result.firstRoll, result.chosenRoll)
        }
    }

    @Test fun formerIntegerOverflowProducesCorrectPositiveResult() {
        assertEquals(2147483648L, DiceEngine().roll(request("d1+2147483647")).finalResult)
        assertEquals(-2147483647L, DiceEngine().roll(request("d1-2147483648")).finalResult)
    }

    @Test fun generatedValuesStayInRangeAndSumsMatch() {
        val engine = DiceEngine(Random(42))
        repeat(100) {
            val result = engine.roll(request("100d6-2", RollMode.ADVANTAGE))
            listOf(result.firstRoll, result.secondRoll!!).forEach { group ->
                assertEquals(100, group.values.size)
                assertTrue(group.values.all { it in 1..6 })
                assertEquals(group.values.sum().toLong(), group.sum)
            }
            assertEquals(result.chosenRoll.sum - 2, result.finalResult)
        }
    }

    @Test(expected = CancellationException::class) fun longRollChecksForCancellation() {
        DiceEngine().roll(request("1000d6")) { throw CancellationException() }
    }

    @Test fun historyIsBoundedNewestFirstAndDeduplicated() {
        var id = 0
        val engine = DiceEngine(newId = { (++id).toString() })
        val earlier = List(100) { engine.roll(request("d1")) }.asReversed()
        val latest = List(10) { engine.roll(request("d1")) }.asReversed()
        val merged = mergeHistory(latest, earlier)
        assertEquals(100, merged.size)
        assertEquals("110", merged.first().id)
        assertEquals("11", merged.last().id)
        assertEquals(100, mergeHistory(listOf(merged.first()), merged).size)
    }
}
