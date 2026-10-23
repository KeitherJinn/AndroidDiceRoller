package com.example.diceroller

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiceRollerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private var modelId = 0
    private var resultId = 0

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        models.clear()
        Dispatchers.resetMain()
    }

    private class FakeRepository(initial: List<RollBatch> = emptyList()) : HistoryRepository {
        var result = HistoryLoadResult(initial)
        var loadFailure: Exception? = null
        var saveFailure: Exception? = null
        var recoverFailure: Exception? = null
        var persisted = initial
        var saves = 0
        var recoveries = 0

        override fun load(): HistoryLoadResult {
            loadFailure?.let { throw it }
            return result
        }

        override fun save(batches: List<RollBatch>) {
            saves++
            saveFailure?.let { throw it }
            persisted = batches.toList()
            result = HistoryLoadResult(persisted)
        }

        override fun recover(batches: List<RollBatch>) {
            recoveries++
            recoverFailure?.let { throw it }
            persisted = batches.toList()
            result = HistoryLoadResult(persisted)
        }
    }

    private fun engine(random: Random = Random(42)) = DiceEngine(
        random = random,
        now = { 1_750_000_000_000L },
        newId = { "result-${++resultId}" },
    )

    private fun batch(count: Int = 2, notation: String = "2d6+3", mode: RollMode = RollMode.ADVANTAGE): RollBatch {
        val request = requireNotNull(validateRequest("Attack", notation, count.toString(), mode).request)
        return RollBatch("batch-${++resultId}", List(count) { engine().roll(request) })
    }

    private fun model(
        repository: FakeRepository = FakeRepository(),
        saved: SavedStateHandle = SavedStateHandle(),
        diceEngine: DiceEngine = engine(),
    ) = ViewModelProvider(models, viewModelFactory {
        initializer { DiceRollerViewModel(saved, repository, diceEngine, dispatcher, dispatcher) }
    })["model-${++modelId}", DiceRollerViewModel::class.java]

    @Test fun failedSaveSurvivesInvalidSubmissionAndCancellationUntilRetrySucceeds() = runTest(dispatcher) {
        val repository = FakeRepository().apply { saveFailure = IOException("disk unavailable") }
        val model = model(repository)
        advanceUntilIdle()
        assertTrue(model.roll())
        advanceUntilIdle()
        val unsaved = model.state.value.history
        assertEquals(1, unsaved.size)
        assertTrue(model.state.value.hasUnsavedChanges)
        assertTrue(repository.persisted.isEmpty())

        model.setNotation("invalid")
        assertFalse(model.roll())
        assertTrue(model.state.value.hasUnsavedChanges)
        model.setNotation("d6")
        assertTrue(model.roll())
        model.cancelRoll()
        advanceUntilIdle()
        assertTrue(model.state.value.hasUnsavedChanges)
        assertEquals(unsaved, model.state.value.history)

        repository.saveFailure = null
        model.retrySave()
        advanceUntilIdle()
        assertFalse(model.state.value.hasUnsavedChanges)
        assertEquals(unsaved, repository.persisted)
        assertFalse(model.state.value.busy)
    }

    @Test fun loadFailureBlocksWritesUntilSuccessfulRetry() = runTest(dispatcher) {
        val previous = listOf(batch())
        val repository = FakeRepository(previous).apply { loadFailure = IOException("read unavailable") }
        val model = model(repository)
        advanceUntilIdle()
        assertTrue(model.state.value.loadFailed)
        assertTrue(model.state.value.blockedForRecovery)
        assertFalse(model.roll())
        model.clearHistory()
        model.retrySave()
        advanceUntilIdle()
        assertEquals(0, repository.saves)
        assertEquals(previous, repository.persisted)

        repository.loadFailure = null
        model.retryLoad()
        advanceUntilIdle()
        assertFalse(model.state.value.loadFailed)
        assertFalse(model.state.value.blockedForRecovery)
        assertEquals(previous, model.state.value.history)
        assertTrue(model.roll())
        advanceUntilIdle()
        assertEquals(2, repository.persisted.size)
    }

    @Test fun recoveredRowsRemainReadOnlyUntilRecoveryBackupSucceeds() = runTest(dispatcher) {
        val retained = listOf(batch())
        val repository = FakeRepository(retained).apply {
            result = HistoryLoadResult(retained, HistoryLoadIssue.RECOVERED_RECORDS, 1)
            recoverFailure = IOException("backup unavailable")
        }
        val model = model(repository)
        advanceUntilIdle()
        assertEquals(retained, model.state.value.history)
        assertEquals(1, model.state.value.skippedRecords)
        assertTrue(model.state.value.blockedForRecovery)
        assertFalse(model.roll())

        model.recoverHistory()
        advanceUntilIdle()
        assertTrue(model.state.value.recoveryFailed)
        assertTrue(model.state.value.blockedForRecovery)
        assertEquals(retained, model.state.value.history)
        assertEquals(0, repository.saves)

        repository.recoverFailure = null
        model.recoverHistory()
        advanceUntilIdle()
        assertEquals(2, repository.recoveries)
        assertFalse(model.state.value.blockedForRecovery)
        assertFalse(model.state.value.recoveryFailed)
        assertNull(model.state.value.loadIssue)
        assertEquals(retained, repository.persisted)
    }

    @Test fun explicitResetUsesRecoveryPathAndClearsOnlyAfterSuccess() = runTest(dispatcher) {
        val retained = listOf(batch())
        val repository = FakeRepository(retained).apply {
            result = HistoryLoadResult(retained, HistoryLoadIssue.RECOVERED_RECORDS, 1)
            recoverFailure = IOException("backup unavailable")
        }
        val model = model(repository)
        advanceUntilIdle()
        model.resetHistory()
        advanceUntilIdle()
        assertEquals(retained, model.state.value.history)
        assertTrue(model.state.value.blockedForRecovery)

        repository.recoverFailure = null
        model.resetHistory()
        advanceUntilIdle()
        assertTrue(model.state.value.history.isEmpty())
        assertTrue(repository.persisted.isEmpty())
        assertEquals(0, repository.saves)
        assertEquals(2, repository.recoveries)
        assertFalse(model.state.value.blockedForRecovery)
    }

    @Test fun futureFormatCannotBeSilentlyOverwritten() = runTest(dispatcher) {
        val repository = FakeRepository().apply {
            result = HistoryLoadResult(emptyList(), HistoryLoadIssue.UNSUPPORTED_VERSION)
        }
        val model = model(repository)
        advanceUntilIdle()
        model.setNotation("d6")
        assertFalse(model.roll())
        model.clearHistory()
        advanceUntilIdle()
        assertEquals(0, repository.saves)
        assertEquals(0, repository.recoveries)
        assertEquals(HistoryLoadIssue.UNSUPPORTED_VERSION, model.state.value.loadIssue)
    }

    @Test fun failedClearPreservesVisibleAndPersistedHistory() = runTest(dispatcher) {
        val retained = listOf(batch())
        val repository = FakeRepository(retained).apply { saveFailure = IOException("write unavailable") }
        val model = model(repository)
        advanceUntilIdle()
        model.clearHistory()
        advanceUntilIdle()
        assertTrue(model.state.value.clearFailed)
        assertEquals(retained, model.state.value.history)
        assertEquals(retained, repository.persisted)
        repository.saveFailure = null
        model.clearHistory()
        advanceUntilIdle()
        assertFalse(model.state.value.clearFailed)
        assertTrue(model.state.value.history.isEmpty())
        assertTrue(repository.persisted.isEmpty())
    }

    @Test fun oversizedEditCannotSubmitItsPrefixOrPreviousExpression() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val repository = FakeRepository()
        val model = model(repository, saved)
        advanceUntilIdle()
        model.setNotation("d1+7")
        model.setNotation("d1" + " ".repeat(123) + "+100")
        assertEquals("d1+7", model.state.value.notation)
        assertEquals(InputIssue.NOTATION_LENGTH, model.state.value.notationIssue)
        // A controlled text field may echo the rejected edit's previous visible value.
        model.setNotation(model.state.value.notation)
        assertEquals(InputIssue.NOTATION_LENGTH, model.state.value.notationIssue)
        model.setTimes("2")
        model.setMode(RollMode.ADVANTAGE)
        assertFalse(model.roll())
        assertEquals(InputIssue.NOTATION_LENGTH, model.state.value.notationIssue)
        assertEquals(0, repository.saves)

        val restored = model(repository, SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        advanceUntilIdle()
        assertFalse(restored.roll())
        assertEquals(InputIssue.NOTATION_LENGTH, restored.state.value.notationIssue)
        restored.setNotation("d1+100")
        assertTrue(restored.roll())
        advanceUntilIdle()
        assertEquals(listOf(101L, 101L), restored.state.value.history.first().results.map { it.finalResult })
    }

    @Test fun savedStateRestoresDraftIntoANewViewModel() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = model(saved = saved)
        advanceUntilIdle()
        first.setName("攻击")
        first.setNotation("2d6+3")
        first.setTimes("3")
        first.setMode(RollMode.DISADVANTAGE)
        val restored = model(saved = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        advanceUntilIdle()
        assertEquals("攻击", restored.state.value.name)
        assertEquals("2d6+3", restored.state.value.notation)
        assertEquals("3", restored.state.value.timesText)
        assertEquals(RollMode.DISADVANTAGE, restored.state.value.mode)
        assertTrue(restored.roll())
        advanceUntilIdle()
        assertEquals(3, restored.state.value.history.first().count)
    }

    @Test fun quickDieChangesOnlyExpressionAndDoesNotRoll() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        model.setName("Attack")
        model.setTimes("3")
        model.setMode(RollMode.DISADVANTAGE)
        model.setNotation("2d20+5")
        model.setQuickDie(6)
        assertEquals(DiceExpression(1, 6, 0), parseDiceNotation(model.state.value.notation))
        assertEquals("Attack", model.state.value.name)
        assertEquals("3", model.state.value.timesText)
        assertEquals(RollMode.DISADVANTAGE, model.state.value.mode)
        assertTrue(model.state.value.history.isEmpty())
    }

    @Test fun batchAndSingleResultReuseLoadSettingsWithoutCreatingHistory() = runTest(dispatcher) {
        val previous = batch(3)
        val repository = FakeRepository(listOf(previous))
        val model = model(repository)
        advanceUntilIdle()
        model.loadBatch(previous)
        assertEquals(previous.name, model.state.value.name)
        assertEquals(previous.notation, model.state.value.notation)
        assertEquals(previous.mode, model.state.value.mode)
        assertEquals("3", model.state.value.timesText)
        model.loadResult(previous.results[1])
        assertEquals("1", model.state.value.timesText)
        assertEquals(previous.notation, model.state.value.notation)
        assertEquals(listOf(previous), model.state.value.history)
        assertEquals(0, repository.saves)
    }

    @Test fun cancellingDuringAGroupNeverPublishesOrPersistsAPartialBatch() = runTest(dispatcher) {
        val previous = listOf(batch())
        val repository = FakeRepository(previous)
        lateinit var rollingModel: DiceRollerViewModel
        var generated = 0
        val random = object : Random() {
            override fun nextBits(bitCount: Int): Int = error("Unexpected random call")
            override fun nextInt(from: Int, until: Int): Int {
                if (++generated == 50) rollingModel.cancelRoll()
                return from
            }
        }
        rollingModel = model(repository, diceEngine = engine(random))
        advanceUntilIdle()
        rollingModel.setNotation("100d6")
        rollingModel.setTimes("100")
        assertTrue(rollingModel.roll())
        advanceUntilIdle()
        assertTrue(generated in 50..100)
        assertEquals(RollStatus.CANCELLED, rollingModel.state.value.status)
        assertEquals(previous, rollingModel.state.value.history)
        assertEquals(previous, repository.persisted)
        assertEquals(0, repository.saves)
        assertFalse(rollingModel.state.value.busy)
    }
}
