package com.example.diceroller

import org.junit.Assert.*
import org.junit.Test

class BatchTest {
    private fun batch(id: String, count: Int = 1, notation: String = "d1", mode: RollMode = RollMode.NORMAL): RollBatch {
        var sequence = 0
        val engine = DiceEngine(now = { 1234L }, newId = { "$id-${++sequence}" })
        val request = requireNotNull(validateRequest("Note", notation, count.toString(), mode).request)
        return RollBatch(id, List(count) { engine.roll(request) })
    }

    @Test fun statisticsUseEveryResultAndPreserveChronologicalOrder() {
        val results = listOf(listOf(1, 2), listOf(6, 6), listOf(1, 1)).mapIndexed { index, values ->
            RollResult("r$index", "Attack", "2d6+3", RollMode.NORMAL, DiceRoll(values), null, 3, 100L + index)
        }
        val batch = RollBatch("batch", results)
        assertEquals(listOf(6L, 15L, 5L), batch.results.map { it.finalResult })
        assertEquals(3, batch.count)
        assertEquals(26L, batch.total)
        assertEquals(26.0 / 3, batch.average, 0.000001)
        assertEquals(5L, batch.minimum)
        assertEquals(15L, batch.maximum)
        assertEquals("Attack", batch.name)
        assertEquals("2d6+3", batch.notation)
        assertEquals(RollMode.NORMAL, batch.mode)
        assertEquals(100L, batch.timestamp)
        assertEquals(6, batch.individualDiceCount)
    }

    @Test fun totalsRemainLongAcrossOneHundredLargeResults() {
        val batch = batch("large", 100, "d1+2147483647")
        assertEquals(214748364800L, batch.total)
        assertEquals(2147483648.0, batch.average, 0.0)
    }

    @Test fun groupModesCountBothSetsOfPhysicalDice() {
        assertEquals(10000, batch("double", 5, "1000d1", RollMode.ADVANTAGE).individualDiceCount)
    }

    @Test fun rejectsEmptyMixedAndOversizedBatches() {
        assertTrue(runCatching { RollBatch("empty", emptyList()) }.isFailure)
        val first = batch("a").results.first()
        val different = batch("b", notation = "2d1").results.first()
        assertTrue(runCatching { RollBatch("mixed", listOf(first, different)) }.isFailure)
        assertTrue(runCatching { RollBatch("duplicate", listOf(first, first)) }.isFailure)
        val oneLarge = batch("large", 1, "1000d1").results.first()
        assertTrue(runCatching { RollBatch("work", List(11) { oneLarge.copy(id = "r$it") }) }.isFailure)
    }

    @Test fun retainsNewestWholeBatchesUpToLimitAndDeduplicatesByBatchId() {
        val previous = List(RollLimits.MAX_BATCHES) { batch("old-$it", 2) }
        val newest = batch("new", 3)
        val merged = mergeBatches(listOf(newest, newest), previous)
        assertEquals(RollLimits.MAX_BATCHES, merged.size)
        assertSame(newest, merged.first())
        assertEquals("old-${RollLimits.MAX_BATCHES - 2}", merged.last().id)
        assertEquals(3, merged.first().count)
        assertEquals(2, merged.last().count)
    }

    @Test fun resultLimitEvictsWholeOldestBatchInsteadOfTrimmingOrSkippingIt() {
        val full = List(9) { batch("full-$it", 100) }
        val partial = batch("ninety", 90)
        val doesNotFit = batch("twenty", 20)
        val olderSmall = batch("older", 1)
        val retained = keepCompleteBatches(full + partial + doesNotFit + olderSmall)
        assertEquals(10, retained.size)
        assertEquals(990, retained.sumOf { it.count })
        assertSame(partial, retained.last())
    }

    @Test fun physicalDiceLimitCountsSecondGroupsAndEvictsWholeBatches() {
        val full = List(RollLimits.MAX_HISTORY_DICE / RollLimits.MAX_TOTAL_DICE) {
            batch("double-$it", 5, "1000d1", RollMode.ADVANTAGE)
        }
        val retained = keepCompleteBatches(full + batch("extra", 1))
        assertEquals(full, retained)
        assertEquals(RollLimits.MAX_HISTORY_DICE, retained.sumOf { it.individualDiceCount })
        assertEquals(full.size * 5, retained.sumOf { it.count })
    }
}
