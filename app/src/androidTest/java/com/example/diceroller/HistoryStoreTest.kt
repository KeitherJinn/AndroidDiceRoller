package com.example.diceroller

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID

class HistoryStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun withStore(test: (HistoryStore, File) -> Unit) {
        val dir = File(context.cacheDir, "history-test-${UUID.randomUUID()}").apply { mkdirs() }
        try { test(HistoryStore(dir), dir) } finally { dir.deleteRecursively() }
    }

    private fun batch(id: String, count: Int = 1, notation: String = "d1", mode: RollMode = RollMode.NORMAL): RollBatch {
        var sequence = 0
        val engine = DiceEngine(now = { 1234L }, newId = { "$id-${++sequence}" })
        val request = validateRequest("攻击 🎲", notation, count.toString(), mode).request!!
        return RollBatch(id, List(count) { engine.roll(request) })
    }

    private fun legacyRecord(result: RollResult) = JSONObject().apply {
        put("id", result.id)
        put("name", result.name)
        put("notation", result.notation)
        put("mode", result.mode.name)
        put("first", JSONArray(result.firstRoll.values))
        result.secondRoll?.let { put("second", JSONArray(it.values)) }
        put("timestamp", result.timestamp)
    }

    private fun historyFile(dir: File) = File(dir, "roll-history.json")
    private fun backups(dir: File) = File(dir, "history-backups").listFiles()?.toList().orEmpty()

    @Test fun roundTripPreservesCompleteBatchesLongResultsBothGroupsAndUnicode() = withStore { store, dir ->
        val batches = listOf(batch("new", 3, "d1+2147483647", RollMode.ADVANTAGE), batch("old"))
        store.save(batches)
        val loaded = HistoryStore(dir).load()
        assertNull(loaded.issue)
        assertEquals(batches, loaded.batches)
        assertEquals(6442450944L, loaded.batches.first().total)
        assertEquals(2147483648L, loaded.batches.first().results.first().finalResult)
        assertEquals(2, JSONObject(historyFile(dir).readText()).getInt("version"))
    }

    @Test fun persistedHistoryEvictsWholeBatchesAndClearSurvivesReload() = withStore { store, dir ->
        val batches = List(RollLimits.MAX_BATCHES + 5) { batch("b$it", 2) }
        store.save(batches)
        val loaded = HistoryStore(dir).load().batches
        assertEquals(RollLimits.MAX_BATCHES, loaded.size)
        assertEquals("b0", loaded.first().id)
        assertEquals("b${RollLimits.MAX_BATCHES - 1}", loaded.last().id)
        assertTrue(loaded.all { it.count == 2 })
        store.save(emptyList())
        assertTrue(HistoryStore(dir).load().batches.isEmpty())
    }

    @Test fun missingFileMeansEmptyHistory() = withStore { store, _ ->
        assertEquals(HistoryLoadResult(emptyList()), store.load())
    }

    @Test fun legacyVersionMigratesEachRecordToOneBatchWithoutGuessingGroups() = withStore { store, dir ->
        val results = batch("old", 3).results.asReversed()
        val source = JSONObject().put("version", 1).put("rolls", JSONArray(results.map(::legacyRecord))).toString()
        historyFile(dir).writeText(source)
        val loaded = store.load()
        assertNull(loaded.issue)
        assertEquals(results.map { it.id }, loaded.batches.map { it.id })
        assertEquals(results, loaded.batches.flatMap { it.results })
        assertTrue(loaded.batches.all { it.count == 1 })
        assertEquals(source, historyFile(dir).readText())
        store.save(loaded.batches)
        assertEquals(2, JSONObject(historyFile(dir).readText()).getInt("version"))
    }

    @Test fun fullLegacyHistoryKeepsAllOneHundredRecordsAfterFirstV2Save() = withStore { store, dir ->
        val results = batch("legacy", 100).results.asReversed()
        historyFile(dir).writeText(JSONObject().put("version", 1)
            .put("rolls", JSONArray(results.map(::legacyRecord))).toString())
        val migrated = store.load()
        assertNull(migrated.issue)
        assertEquals(100, migrated.batches.size)
        assertEquals(results, migrated.batches.flatMap { it.results })
        store.save(migrated.batches)
        val restored = HistoryStore(dir).load()
        assertNull(restored.issue)
        assertEquals(100, restored.batches.size)
        assertEquals(migrated.batches, restored.batches)
        assertEquals(results, restored.batches.flatMap { it.results })
        assertEquals(2, JSONObject(historyFile(dir).readText()).getInt("version"))
    }

    @Test fun densestLegalLegacyHistoryPreservesBothGroupsThroughMigration() = withStore { store, dir ->
        var sequence = 0
        val engine = DiceEngine(newId = { "dense-${++sequence}" })
        val request = validateRequest("Old campaign", "1000d1", "1", RollMode.ADVANTAGE).request!!
        val results = List(100) { engine.roll(request) }.asReversed()
        historyFile(dir).writeText(JSONObject().put("version", 1)
            .put("rolls", JSONArray(results.map(::legacyRecord))).toString())
        val migrated = store.load()
        assertNull(migrated.issue)
        assertEquals(100, migrated.batches.size)
        assertEquals(200000, migrated.batches.sumOf { it.individualDiceCount })
        assertEquals(results, migrated.batches.flatMap { it.results })
        store.save(migrated.batches)
        val restored = HistoryStore(dir).load()
        assertNull(restored.issue)
        assertEquals(migrated.batches, restored.batches)
        assertEquals(200000, restored.batches.sumOf { it.individualDiceCount })
    }

    @Test fun oneBadLegacyRecordKeepsOthersButProtectsOriginalUntilRecovery() = withStore { store, dir ->
        val first = batch("a").results.first()
        val last = batch("b").results.first()
        val source = JSONObject().put("version", 1).put("rolls", JSONArray()
            .put(legacyRecord(first)).put(JSONObject().put("id", "broken")).put(legacyRecord(last))).toString()
        historyFile(dir).writeText(source)
        val loaded = store.load()
        assertEquals(HistoryLoadIssue.RECOVERED_RECORDS, loaded.issue)
        assertEquals(1, loaded.skippedRecords)
        assertEquals(listOf(first, last), loaded.batches.flatMap { it.results })
        assertTrue(runCatching { store.save(loaded.batches) }.isFailure)
        assertEquals(source, historyFile(dir).readText())
        store.recover(loaded.batches)
        assertEquals(source, backups(dir).single().readText())
        assertEquals(loaded.batches, store.load().batches)
        assertNull(store.load().issue)
    }

    @Test fun invalidResultDiscardsEntireV2BatchAndNeverInventsPartialStatistics() = withStore { store, dir ->
        val bad = batch("bad", 3)
        val good = batch("good", 2)
        store.save(listOf(bad, good))
        val source = JSONObject(historyFile(dir).readText())
        source.getJSONArray("batches").getJSONObject(0).getJSONArray("results")
            .getJSONObject(1).put("first", JSONArray().put(99))
        historyFile(dir).writeText(source.toString())
        val loaded = store.load()
        assertEquals(HistoryLoadIssue.RECOVERED_RECORDS, loaded.issue)
        assertEquals(1, loaded.skippedRecords)
        assertEquals(listOf(good), loaded.batches)
        assertTrue(runCatching { store.save(emptyList()) }.isFailure)
    }

    @Test fun fractionalDieValueIsRejectedInsteadOfRoundedIntoAResult() = withStore { store, dir ->
        store.save(listOf(batch("fraction")))
        val source = JSONObject(historyFile(dir).readText())
        source.getJSONArray("batches").getJSONObject(0).getJSONArray("results")
            .getJSONObject(0).put("first", JSONArray().put(1.5))
        historyFile(dir).writeText(source.toString())
        val loaded = store.load()
        assertEquals(HistoryLoadIssue.RECOVERED_RECORDS, loaded.issue)
        assertTrue(loaded.batches.isEmpty())
    }

    @Test fun unknownVersionCannotBeOverwrittenEvenByANewRepositoryInstance() = withStore { store, dir ->
        val source = """{"version":99,"future":"must remain intact"}"""
        historyFile(dir).writeText(source)
        assertEquals(HistoryLoadIssue.UNSUPPORTED_VERSION, store.load().issue)
        assertTrue(runCatching { store.save(emptyList()) }.isFailure)
        assertTrue(runCatching { HistoryStore(dir).save(emptyList()) }.isFailure)
        assertEquals(source, historyFile(dir).readText())
        store.recover(emptyList())
        assertEquals(source, backups(dir).single().readText())
        assertEquals(HistoryLoadResult(emptyList()), store.load())
    }

    @Test fun corruptFileIsBackedUpByteForByteBeforeExplicitReset() = withStore { store, dir ->
        val source = byteArrayOf(0, 0xff.toByte(), 5, 23, 34, 96)
        historyFile(dir).writeBytes(source)
        assertEquals(HistoryLoadIssue.CORRUPT_FILE, store.load().issue)
        assertTrue(runCatching { store.save(emptyList()) }.isFailure)
        store.recover(emptyList())
        assertArrayEquals(source, backups(dir).single().readBytes())
        assertEquals(HistoryLoadResult(emptyList()), store.load())
    }

    @Test fun failedBackupDoesNotOverwriteOriginalOrUnlockOrdinarySave() = withStore { store, dir ->
        val source = "not valid json"
        historyFile(dir).writeText(source)
        File(dir, "history-backups").writeText("A file blocks creation of the backup directory")
        store.load()
        assertTrue(runCatching { store.recover(emptyList()) }.exceptionOrNull() is IOException)
        assertEquals(source, historyFile(dir).readText())
        assertTrue(runCatching { store.save(emptyList()) }.isFailure)
    }

    @Test fun oversizedSourceIsProtectedAndEntireOriginalIsBackedUp() = withStore { store, dir ->
        val source = ByteArray(8 * 1024 * 1024 + 5) { 65 }
        historyFile(dir).writeBytes(source)
        assertEquals(HistoryLoadIssue.CORRUPT_FILE, store.load().issue)
        assertTrue(runCatching { store.save(emptyList()) }.isFailure)
        store.recover(emptyList())
        assertArrayEquals(source, backups(dir).single().readBytes())
        assertNull(store.load().issue)
    }

    @Test fun retryLoadReevaluatesProtectionAfterSourceBecomesReadable() = withStore { store, dir ->
        historyFile(dir).writeText("{")
        assertEquals(HistoryLoadIssue.CORRUPT_FILE, store.load().issue)
        historyFile(dir).writeText("""{"version":2,"batches":[]}""")
        assertNull(store.load().issue)
        val saved = listOf(batch("retried"))
        store.save(saved)
        assertEquals(saved, store.load().batches)
    }

    @Test fun ioReadFailureIsThrownInsteadOfReportedAsEmptyHistory() = withStore { store, dir ->
        assertTrue(historyFile(dir).mkdir())
        assertTrue(runCatching { store.load() }.exceptionOrNull() is IOException)
        assertTrue(runCatching { store.save(emptyList()) }.isFailure)
    }

    @Test fun chineseAndEnglishResourcesAndPluralsAreAvailable() {
        fun localized(locale: Locale) = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) }).resources
        val chinese = localized(Locale.SIMPLIFIED_CHINESE)
        val english = localized(Locale.US)
        assertEquals("常用骰子", chinese.getString(R.string.quick_dice))
        assertEquals("Quick dice", english.getString(R.string.quick_dice))
        assertEquals("已完成 2 次投掷。", chinese.getQuantityString(R.plurals.rolls_completed, 2, 2))
        assertEquals("1 roll completed.", english.getQuantityString(R.plurals.rolls_completed, 1, 1))
        assertEquals("2 rolls completed.", english.getQuantityString(R.plurals.rolls_completed, 2, 2))
    }
}
