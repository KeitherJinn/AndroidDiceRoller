package com.example.diceroller

/** Results stay in the order they were rolled; a batch is never partly retained or recovered. */
data class RollBatch(val id: String, val results: List<RollResult>) {
    init {
        require(id.isNotBlank() && id.length <= 64)
        require(results.size in 1..RollLimits.MAX_REPEATS)
        require(results.map { it.id }.toSet().size == results.size)
        val first = results.first()
        val dice = requireNotNull(parseDiceNotation(first.notation))
        require(first.name.length <= RollLimits.MAX_NAME_LENGTH)
        results.forEach { result ->
            require(result.id.isNotBlank() && result.id.length <= 64)
            require(result.notation == first.notation && result.name == first.name && result.mode == first.mode)
            require(result.modifier == dice.modifier)
            fun validRoll(roll: DiceRoll) =
                roll.values.size == dice.numberOfDice && roll.values.all { it in 1..dice.sides }
            require(validRoll(result.firstRoll))
            if (result.mode == RollMode.NORMAL) require(result.secondRoll == null)
            else require(result.secondRoll?.let(::validRoll) == true)
        }
        require(individualDiceCount <= RollLimits.MAX_TOTAL_DICE)
    }

    val notation: String get() = results.first().notation
    val name: String get() = results.first().name
    val mode: RollMode get() = results.first().mode
    val timestamp: Long get() = results.first().timestamp
    val count: Int get() = results.size
    val total: Long get() = results.sumOf { it.finalResult }
    val average: Double get() = total.toDouble() / count
    val minimum: Long get() = results.minOf { it.finalResult }
    val maximum: Long get() = results.maxOf { it.finalResult }
    val individualDiceCount: Int get() = results.sumOf { it.firstRoll.values.size + (it.secondRoll?.values?.size ?: 0) }
}

/** Keep a newest-first prefix. Eviction removes whole oldest batches, never individual results. */
fun keepCompleteBatches(batches: List<RollBatch>): List<RollBatch> {
    val retained = mutableListOf<RollBatch>()
    val ids = mutableSetOf<String>()
    var resultCount = 0
    var diceCount = 0
    for (batch in batches) {
        if (!ids.add(batch.id)) continue
        if (retained.size == RollLimits.MAX_BATCHES ||
            resultCount + batch.count > RollLimits.MAX_HISTORY_RESULTS ||
            diceCount + batch.individualDiceCount > RollLimits.MAX_HISTORY_DICE) break
        retained += batch
        resultCount += batch.count
        diceCount += batch.individualDiceCount
    }
    return retained
}

fun mergeBatches(newestFirst: List<RollBatch>, previous: List<RollBatch>): List<RollBatch> =
    keepCompleteBatches(newestFirst + previous)
