package com.example.diceroller

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

enum class HistoryLoadIssue { RECOVERED_RECORDS, CORRUPT_FILE, UNSUPPORTED_VERSION }

data class HistoryLoadResult(
    val batches: List<RollBatch>,
    val issue: HistoryLoadIssue? = null,
    /** Invalid legacy records or whole v2 batches; retained batches themselves are always complete. */
    val skippedRecords: Int = 0,
)

interface HistoryRepository {
    fun load(): HistoryLoadResult
    fun save(batches: List<RollBatch>)
    fun recover(batches: List<RollBatch>)
}

/** Bounded, versioned history. A damaged source cannot be overwritten without explicit recovery. */
class HistoryStore(private val directory: File) : HistoryRepository {
    private val file = AtomicFile(File(directory, "roll-history.json"))
    private var loaded = false
    private var requiresRecovery = true

    @Synchronized
    override fun load(): HistoryLoadResult {
        // An I/O failure must not accidentally unlock an earlier damaged source.
        loaded = false
        requiresRecovery = true
        val bytes = readSource()
        val result = when {
            bytes == null -> HistoryLoadResult(emptyList())
            bytes.size > MAX_FILE_BYTES -> HistoryLoadResult(emptyList(), HistoryLoadIssue.CORRUPT_FILE)
            else -> try {
                decode(JSONObject(bytes.toString(Charsets.UTF_8)))
            } catch (_: JSONException) {
                HistoryLoadResult(emptyList(), HistoryLoadIssue.CORRUPT_FILE)
            } catch (_: IllegalArgumentException) {
                HistoryLoadResult(emptyList(), HistoryLoadIssue.CORRUPT_FILE)
            }
        }
        loaded = true
        requiresRecovery = result.issue != null
        return result
    }

    private fun readSource(): ByteArray? = try {
        file.openRead().use { it.readNBytes(MAX_FILE_BYTES + 1) }
    } catch (error: FileNotFoundException) {
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw error
        null
    }

    private fun decode(root: JSONObject): HistoryLoadResult {
        val version = root.longValue("version")
        if (version != 1L && version != 2L) return HistoryLoadResult(emptyList(), HistoryLoadIssue.UNSUPPORTED_VERSION)
        val records = root.getJSONArray(if (version == 1L) "rolls" else "batches")
        val batches = mutableListOf<RollBatch>()
        val batchIds = mutableSetOf<String>()
        val resultIds = mutableSetOf<String>()
        var skipped = 0
        for (index in 0 until records.length()) {
            val batch = try {
                (if (version == 1L) {
                    val result = readResult(records.getJSONObject(index))
                    RollBatch(result.id, listOf(result))
                } else {
                    val source = records.getJSONObject(index)
                    val rolls = source.getJSONArray("results")
                    require(rolls.length() in 1..RollLimits.MAX_REPEATS)
                    RollBatch(source.stringValue("id"), List(rolls.length()) { readResult(rolls.getJSONObject(it)) })
                }).also { candidate ->
                    require(candidate.id !in batchIds && candidate.results.none { it.id in resultIds })
                }
            } catch (_: JSONException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            if (batch == null) skipped++ else {
                batches += batch
                batchIds += batch.id
                resultIds += batch.results.map { it.id }
            }
        }
        return HistoryLoadResult(
            batches = keepCompleteBatches(batches),
            issue = if (skipped == 0) null else HistoryLoadIssue.RECOVERED_RECORDS,
            skippedRecords = skipped,
        )
    }

    private fun readResult(record: JSONObject): RollResult {
        val dice = requireNotNull(parseDiceNotation(record.stringValue("notation")))
        fun readRoll(key: String): DiceRoll {
            val values = record.getJSONArray(key)
            require(values.length() == dice.numberOfDice)
            return DiceRoll(List(values.length()) { i ->
                val value = exactLong(values.get(i))
                require(value in 1L..dice.sides.toLong())
                value.toInt()
            })
        }
        val mode = RollMode.valueOf(record.stringValue("mode"))
        if (mode == RollMode.NORMAL) require(!record.has("second") || record.isNull("second"))
        return RollResult(
            id = record.stringValue("id").also { require(it.isNotBlank() && it.length <= 64) },
            name = record.stringValue("name").also { require(it.length <= RollLimits.MAX_NAME_LENGTH) },
            notation = dice.notation,
            mode = mode,
            firstRoll = readRoll("first"),
            secondRoll = if (mode == RollMode.NORMAL) null else readRoll("second"),
            modifier = dice.modifier,
            timestamp = record.longValue("timestamp"),
        )
    }

    @Synchronized
    override fun save(batches: List<RollBatch>) {
        if (!loaded) load()
        check(!requiresRecovery) { "History must be recovered or reset before saving." }
        write(batches)
    }

    @Synchronized
    override fun recover(batches: List<RollBatch>) {
        // Serialize before backup, so invalid replacement data never changes the original.
        val bytes = encode(batches)
        backupOriginal()
        writeBytes(bytes)
        loaded = true
        requiresRecovery = false
    }

    private fun backupOriginal() {
        val input = try {
            file.openRead()
        } catch (error: FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw error
            return
        }
        input.use { source ->
            val backups = File(directory, "history-backups")
            if (!backups.isDirectory && !backups.mkdirs()) throw IOException("Cannot create history backup directory.")
            val backup = File.createTempFile("roll-history-", ".json", backups)
            try {
                FileOutputStream(backup).use { output ->
                    source.copyTo(output)
                    output.fd.sync()
                }
            } catch (error: Exception) {
                backup.delete()
                throw error
            }
        }
    }

    private fun write(batches: List<RollBatch>) = writeBytes(encode(batches))

    private fun encode(batches: List<RollBatch>): ByteArray {
        val records = JSONArray()
        val resultIds = mutableSetOf<String>()
        keepCompleteBatches(batches).forEach { batch ->
            val rolls = JSONArray()
            batch.results.forEach { result ->
                require(resultIds.add(result.id)) { "Duplicate result ID in history." }
                rolls.put(JSONObject().apply {
                    put("id", result.id)
                    put("name", result.name)
                    put("notation", result.notation)
                    put("mode", result.mode.name)
                    put("first", JSONArray(result.firstRoll.values))
                    result.secondRoll?.let { put("second", JSONArray(it.values)) }
                    put("timestamp", result.timestamp)
                })
            }
            records.put(JSONObject().put("id", batch.id).put("results", rolls))
        }
        return JSONObject().put("version", 2).put("batches", records).toString().toByteArray(Charsets.UTF_8).also {
            require(it.size <= MAX_FILE_BYTES)
        }
    }

    private fun writeBytes(bytes: ByteArray) {
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun JSONObject.stringValue(key: String): String = get(key).let { require(it is String); it }
    private fun JSONObject.longValue(key: String): Long = exactLong(get(key))
    private fun exactLong(value: Any): Long {
        require(value is Int || value is Long)
        return (value as Number).toLong()
    }

    private companion object { const val MAX_FILE_BYTES = 8 * 1024 * 1024 }
}
