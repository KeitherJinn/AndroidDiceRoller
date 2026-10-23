package com.example.diceroller

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RollStatus {
    COMPLETED, CANCELLED, LOAD_FAILED, SAVE_FAILED, CLEARED, CLEAR_FAILED,
    SAVED, RECOVERED, RESET, ROLL_FAILED,
}

data class RollerUiState(
    val name: String = "",
    val notation: String = "d20",
    val timesText: String = "1",
    val mode: RollMode = RollMode.NORMAL,
    val history: List<RollBatch> = emptyList(),
    val isLoading: Boolean = true,
    val isRolling: Boolean = false,
    val isSaving: Boolean = false,
    val progress: Int = 0,
    val requested: Int = 0,
    val notationIssue: InputIssue? = null,
    val timesIssue: InputIssue? = null,
    val status: RollStatus? = null,
    val resultRevision: Int = 0,
    val hasUnsavedChanges: Boolean = false,
    val loadIssue: HistoryLoadIssue? = null,
    val loadFailed: Boolean = false,
    val skippedRecords: Int = 0,
    val clearFailed: Boolean = false,
    val recoveryFailed: Boolean = false,
) {
    val busy: Boolean get() = isLoading || isRolling || isSaving
    val blockedForRecovery: Boolean get() = loadIssue != null || loadFailed
}

/** Dependencies are supplied so failure paths can be tested without a device. */
class DiceRollerViewModel(
    private val saved: SavedStateHandle,
    private val repository: HistoryRepository,
    private val engine: DiceEngine = DiceEngine(),
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private var rollJob: Job? = null
    private var notationTooLong: Boolean = saved["notationTooLong"] ?: false
    private var timesTooLong: Boolean = saved["timesTooLong"] ?: false
    private val mutableState = MutableStateFlow(RollerUiState(
        name = saved["name"] ?: "",
        notation = saved["notation"] ?: "d20",
        timesText = saved["times"] ?: "1",
        mode = saved.get<String>("mode")?.let { value -> RollMode.entries.find { it.name == value } } ?: RollMode.NORMAL,
        notationIssue = if (notationTooLong) InputIssue.NOTATION_LENGTH else null,
        timesIssue = if (timesTooLong) InputIssue.REPEAT_LIMIT else null,
    ))
    val state = mutableState.asStateFlow()

    init { viewModelScope.launch { readHistory() } }

    private suspend fun readHistory() {
        try {
            val loaded = withContext(ioDispatcher) { repository.load() }
            mutableState.update { it.copy(
                history = loaded.batches, isLoading = false, loadFailed = false,
                loadIssue = loaded.issue, skippedRecords = loaded.skippedRecords,
                recoveryFailed = false, clearFailed = false,
                status = if (loaded.issue == null) null else RollStatus.LOAD_FAILED,
            ) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            mutableState.update { it.copy(
                isLoading = false, loadFailed = true, loadIssue = null,
                status = RollStatus.LOAD_FAILED,
            ) }
        }
    }

    fun setName(value: String) {
        if (state.value.busy) return
        saved["name"] = value.take(RollLimits.MAX_NAME_LENGTH)
        mutableState.update { it.copy(name = saved["name"] ?: "") }
    }

    fun setNotation(value: String) {
        if (state.value.busy) return
        // TextField may echo its old value after rejecting an edit. That is not a correction.
        if (notationTooLong && value == state.value.notation) return
        notationTooLong = value.length > RollLimits.MAX_NOTATION_LENGTH
        saved["notationTooLong"] = notationTooLong
        if (notationTooLong) {
            // Reject the whole edit: truncating a modifier can silently change the roll.
            mutableState.update { it.copy(notationIssue = InputIssue.NOTATION_LENGTH) }
            return
        }
        saved["notation"] = value
        mutableState.update { it.copy(
            notation = value, notationIssue = null,
            timesIssue = if (timesTooLong) InputIssue.REPEAT_LIMIT else null,
        ) }
    }

    fun setTimes(value: String) {
        if (state.value.busy) return
        if (timesTooLong && value == state.value.timesText) return
        timesTooLong = value.length > 10
        saved["timesTooLong"] = timesTooLong
        if (timesTooLong) {
            mutableState.update { it.copy(timesIssue = InputIssue.REPEAT_LIMIT) }
            return
        }
        saved["times"] = value
        mutableState.update { it.copy(timesText = value, timesIssue = null) }
    }

    fun setMode(mode: RollMode) {
        if (state.value.busy) return
        saved["mode"] = mode.name
        mutableState.update { it.copy(
            mode = mode, timesIssue = if (timesTooLong) InputIssue.REPEAT_LIMIT else null,
        ) }
    }

    fun setQuickDie(sides: Int) {
        if (state.value.busy || sides !in listOf(4, 6, 8, 10, 12, 20, 100)) return
        notationTooLong = false
        setNotation("d$sides")
    }

    fun loadBatch(batch: RollBatch) = loadSettings(batch.results.first(), batch.count)
    fun loadResult(result: RollResult) = loadSettings(result, 1)

    private fun loadSettings(result: RollResult, count: Int) {
        if (state.value.busy) return
        notationTooLong = false
        timesTooLong = false
        setName(result.name)
        setNotation(result.notation)
        setTimes(count.toString())
        setMode(result.mode)
        mutableState.update { it.copy(status = null) }
    }

    /** False means the UI should show the existing validation or recovery state. */
    fun roll(): Boolean {
        val current = state.value
        if (current.busy || current.blockedForRecovery) return false
        val validation = validateRequest(current.name, current.notation, current.timesText, current.mode)
        val notationIssue = if (notationTooLong) InputIssue.NOTATION_LENGTH else validation.notationIssue
        val timesIssue = if (timesTooLong) InputIssue.REPEAT_LIMIT else validation.timesIssue
        mutableState.update { it.copy(notationIssue = notationIssue, timesIssue = timesIssue, status = null) }
        if (notationIssue != null || timesIssue != null) return false
        val request = validation.request ?: return false
        mutableState.update { it.copy(isRolling = true, progress = 0, requested = request.times) }
        val job = viewModelScope.launch {
            try {
                val results = withContext(computationDispatcher) {
                    List(request.times) { index ->
                        ensureActive()
                        engine.roll(request) { ensureActive() }.also {
                            mutableState.update { value -> value.copy(progress = index + 1) }
                        }
                    }
                }
                ensureActive()
                val batch = RollBatch(results.first().id, results)
                val history = mergeBatches(listOf(batch), state.value.history)
                // Once a bounded commit starts, persist and publish the same snapshot.
                withContext(NonCancellable) {
                    mutableState.update { it.copy(isRolling = false, isSaving = true) }
                    val persisted = withContext(ioDispatcher) { runCatching { repository.save(history) }.isSuccess }
                    mutableState.update { it.copy(
                        history = history, isSaving = false, hasUnsavedChanges = !persisted,
                        status = if (persisted) RollStatus.COMPLETED else RollStatus.SAVE_FAILED,
                        resultRevision = it.resultRevision + 1,
                    ) }
                }
            } catch (error: CancellationException) {
                mutableState.update { if (it.isRolling) it.copy(isRolling = false, status = RollStatus.CANCELLED) else it }
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(isRolling = false, status = RollStatus.ROLL_FAILED) }
            }
        }
        rollJob = job
        // Cancellation may happen before the coroutine body gets its first turn.
        job.invokeOnCompletion { cause ->
            if (cause is CancellationException && rollJob === job) {
                mutableState.update { if (it.isRolling) it.copy(isRolling = false, status = RollStatus.CANCELLED) else it }
            }
        }
        return true
    }

    fun cancelRoll() {
        if (state.value.isRolling) rollJob?.cancel()
    }

    fun retrySave() {
        val current = state.value
        if (current.busy || current.blockedForRecovery || !current.hasUnsavedChanges) return
        mutableState.update { it.copy(isSaving = true, status = null) }
        viewModelScope.launch {
            withContext(NonCancellable) {
                val persisted = withContext(ioDispatcher) { runCatching { repository.save(current.history) }.isSuccess }
                mutableState.update { it.copy(
                    isSaving = false, hasUnsavedChanges = !persisted,
                    status = if (persisted) RollStatus.SAVED else RollStatus.SAVE_FAILED,
                ) }
            }
        }
    }

    fun retryLoad() {
        if (state.value.busy || state.value.hasUnsavedChanges) return
        mutableState.update { it.copy(isLoading = true, status = null) }
        viewModelScope.launch { readHistory() }
    }

    fun recoverHistory() {
        val current = state.value
        if (current.busy || current.loadIssue != HistoryLoadIssue.RECOVERED_RECORDS || current.history.isEmpty()) return
        resolveHistory(current.history, RollStatus.RECOVERED)
    }

    /** Called only after the UI confirms resetting unreadable history. */
    fun resetHistory() {
        if (state.value.busy || !state.value.blockedForRecovery) return
        resolveHistory(emptyList(), RollStatus.RESET)
    }

    private fun resolveHistory(history: List<RollBatch>, success: RollStatus) {
        mutableState.update { it.copy(isSaving = true, recoveryFailed = false, status = null) }
        viewModelScope.launch {
            withContext(NonCancellable) {
                val recovered = withContext(ioDispatcher) { runCatching { repository.recover(history) }.isSuccess }
                mutableState.update { value ->
                    if (recovered) value.copy(
                        history = history, isSaving = false, loadIssue = null, loadFailed = false,
                        skippedRecords = 0, recoveryFailed = false, clearFailed = false,
                        hasUnsavedChanges = false, status = success,
                    ) else value.copy(isSaving = false, recoveryFailed = true)
                }
            }
        }
    }

    fun clearHistory() {
        val current = state.value
        if (current.busy || current.blockedForRecovery || current.history.isEmpty()) return
        mutableState.update { it.copy(isSaving = true, clearFailed = false, status = null) }
        viewModelScope.launch {
            withContext(NonCancellable) {
                val cleared = withContext(ioDispatcher) { runCatching { repository.save(emptyList()) }.isSuccess }
                mutableState.update { it.copy(
                    isSaving = false, clearFailed = !cleared,
                    history = if (cleared) emptyList() else it.history,
                    hasUnsavedChanges = if (cleared) false else it.hasUnsavedChanges,
                    status = if (cleared) RollStatus.CLEARED else RollStatus.CLEAR_FAILED,
                ) }
            }
        }
    }
}
