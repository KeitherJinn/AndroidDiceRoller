package com.example.diceroller

import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import java.io.File
import java.text.NumberFormat
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** These tests may clear only the dedicated QA application's history. */
class RollerUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = ViewModelProvider(compose.activity)[DiceRollerViewModel::class.java]

    private fun settled() {
        compose.waitUntil(10_000) { !model.state.value.busy }
        compose.waitForIdle()
    }

    private fun formList(): SemanticsNodeInteraction =
        if (compose.onAllNodesWithTag("form_list").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("form_list")
        else compose.onNodeWithTag("main_list")

    private fun showForm(tag: String) { formList().performScrollToNode(hasTestTag(tag)) }
    private fun showHistory(tag: String) { compose.onNodeWithTag("main_list").performScrollToNode(hasTestTag(tag)) }
    private fun expandSettings() {
        showForm("advanced_toggle")
        compose.onNodeWithTag("advanced_toggle").performClick()
    }

    private fun createBatches(count: Int) {
        compose.runOnIdle { model.setNotation("d1"); model.setTimes("1") }
        repeat(count) {
            compose.runOnIdle { assertTrue(model.roll()) }
            settled()
        }
    }

    @Before fun reset() {
        assertEquals("Refusing to clear a non-test installation", "com.example.diceroller.uitest", compose.activity.packageName)
        settled()
        compose.runOnIdle { model.clearHistory() }
        settled()
        compose.runOnIdle {
            model.setName("")
            model.setNotation("d20")
            model.setTimes("1")
            model.setMode(RollMode.NORMAL)
        }
    }

    @Test fun optionalNameOverflowAndLatestResultVisibility() {
        showForm("notation")
        compose.onNodeWithTag("notation").performTextReplacement("d1+2147483647")
        compose.onNodeWithTag("roll_button").performClick()
        settled()
        compose.onNodeWithTag("final_result").assertTextEquals(NumberFormat.getIntegerInstance().format(2147483648L)).assertIsDisplayed()
        compose.waitUntil(5_000) {
            !ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!.isVisible(WindowInsetsCompat.Type.ime())
        }
        assertEquals(2147483648L, HistoryStore(compose.activity.filesDir).load().batches.first().results.first().finalResult)
    }

    @Test fun recreationPreservesInputModeAndHistory() {
        compose.runOnIdle {
            model.setNotation("2d6+3")
            model.setName("Attack")
            model.setTimes("3")
            model.setMode(RollMode.ADVANTAGE)
            model.roll()
        }
        settled()
        val before = model.state.value.history
        val completion = compose.activity.resources.getQuantityString(R.plurals.rolls_completed, 3, 3)
        compose.onNodeWithText(completion).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(6_000)
        compose.waitForIdle()
        compose.onNodeWithText(completion).assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        settled()
        compose.onNodeWithText(completion).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("Attack", model.state.value.name)
            assertEquals("2d6+3", model.state.value.notation)
            assertEquals("3", model.state.value.timesText)
            assertEquals(RollMode.ADVANTAGE, model.state.value.mode)
            assertEquals(before, model.state.value.history)
        }
        // A new batch with the same completion text must still notify the user.
        compose.runOnIdle { assertTrue(model.roll()) }
        settled()
        compose.onNodeWithText(completion).assertIsDisplayed()
        assertEquals(2, model.state.value.history.size)
    }

    @Test fun recreationKeepsBrowsingPositionAfterAnEarlierRoll() {
        createBatches(10)
        val tag = "history_${model.state.value.history[6].id}"
        showHistory(tag)
        compose.onNodeWithTag(tag).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        settled()
        compose.onNodeWithTag(tag).assertIsDisplayed()
        compose.onNodeWithTag("latest_result").assertIsNotDisplayed()
    }

    // Orientation requests are test stimuli, restored in finally; the production Activity is not locked.
    @Test fun rotatingBetweenNarrowAndWideLayoutsKeepsTheVisibleHistoryBatch() {
        createBatches(10)
        val completion = compose.activity.resources.getQuantityString(R.plurals.rolls_completed, 1, 1)
        compose.onNodeWithText(completion).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(6_000)
        compose.waitForIdle()
        compose.onNodeWithText(completion).assertDoesNotExist()
        val tag = "history_${model.state.value.history[6].id}"
        showHistory(tag)
        compose.onNodeWithTag(tag).assertIsDisplayed()
        val previous = compose.activity.requestedOrientation
        try {
            compose.runOnIdle { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            settled()
            compose.onNodeWithTag(tag).assertIsDisplayed()
            compose.onNodeWithText(completion).assertDoesNotExist()
            if (compose.activity.resources.configuration.screenWidthDp >= 720) {
                compose.onNodeWithTag("form_list").assertIsDisplayed()
            }
            compose.runOnIdle { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            settled()
            compose.onNodeWithTag(tag).assertIsDisplayed()
            compose.onNodeWithText(completion).assertDoesNotExist()
        } finally {
            compose.runOnIdle { compose.activity.requestedOrientation = previous }
        }
    }

    @Test fun saveWarningSurvivesInvalidSubmissionAndDisappearsOnlyAfterRetry() {
        val blocker = File(compose.activity.filesDir, "roll-history.json.new")
        assertFalse(blocker.exists())
        assertTrue(blocker.mkdir())
        try {
            compose.runOnIdle { model.setNotation("d1+2"); assertTrue(model.roll()) }
            settled()
            val unsaved = model.state.value.history
            compose.onNodeWithTag("unsaved_warning").assertIsDisplayed()
            assertTrue(model.state.value.hasUnsavedChanges)

            showForm("notation")
            compose.onNodeWithTag("notation").performTextReplacement("invalid")
            compose.onNodeWithTag("roll_button").performClick()
            compose.onNodeWithTag("unsaved_warning").assertIsDisplayed()
            assertTrue(model.state.value.hasUnsavedChanges)
            assertEquals(unsaved, model.state.value.history)
            Espresso.closeSoftKeyboard()

            assertTrue(blocker.delete())
            compose.onNodeWithTag("retry_save").performClick()
            settled()
            compose.onNodeWithTag("unsaved_warning").assertDoesNotExist()
            assertFalse(model.state.value.hasUnsavedChanges)
            assertEquals(unsaved, HistoryStore(compose.activity.filesDir).load().batches)
        } finally {
            if (blocker.exists()) {
                check(blocker.isDirectory && blocker.list()?.isEmpty() == true)
                check(blocker.delete())
            }
            compose.runOnIdle { model.retrySave() }
            settled()
        }
    }

    @Test fun damagedHistoryRequiresConfirmationAndBacksUpOriginalBytesBeforeReset() {
        val directory = compose.activity.filesDir
        val history = File(directory, "roll-history.json")
        val backups = File(directory, "history-backups")
        val previousBackups = backups.listFiles()?.map { it.name }?.toSet().orEmpty()
        val damaged = "Unreadable history / 历史损坏\n".toByteArray(Charsets.UTF_8)
        history.writeBytes(damaged)
        try {
            compose.runOnIdle { model.retryLoad() }
            settled()
            assertEquals(HistoryLoadIssue.CORRUPT_FILE, model.state.value.loadIssue)
            compose.onNodeWithTag("load_warning").assertIsDisplayed()
            compose.onNodeWithTag("roll_button").assertIsNotEnabled()
            compose.onNodeWithTag("reset_history").performClick()
            compose.onNodeWithTag("confirm_reset").assertIsDisplayed()
            compose.onNodeWithTag("dismiss_reset").performClick()
            assertArrayEquals(damaged, history.readBytes())
            assertEquals(previousBackups, backups.listFiles()?.map { it.name }?.toSet().orEmpty())

            compose.onNodeWithTag("reset_history").performClick()
            compose.onNodeWithTag("confirm_reset").performClick()
            settled()
            val created = backups.listFiles().orEmpty().filter { it.name !in previousBackups }
            assertEquals(1, created.size)
            assertArrayEquals(damaged, created.single().readBytes())
            assertTrue(HistoryStore(directory).load().batches.isEmpty())
            compose.onNodeWithTag("load_warning").assertDoesNotExist()
            compose.onNodeWithTag("roll_button").assertIsEnabled().performClick()
            settled()
            assertEquals(1, model.state.value.history.size)
        } finally {
            // Leave the isolated QA application usable even if an assertion above fails.
            compose.runOnIdle { if (model.state.value.blockedForRecovery) model.resetHistory() }
            settled()
        }
    }

    @Test fun hugeRepeatIsRejectedInlineAndClearsOnEdit() {
        expandSettings()
        showForm("times")
        compose.onNodeWithTag("times").performTextReplacement("2147483647")
        compose.onNodeWithTag("roll_button").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.error_repeat_limit, 100)).assertIsDisplayed()
        assertTrue(model.state.value.history.isEmpty())
        compose.onNodeWithTag("times").performTextReplacement("1")
        compose.onNodeWithText(compose.activity.getString(R.string.error_repeat_limit, 100)).assertDoesNotExist()
    }

    @Test fun oversizedPastedExpressionCannotRollATruncatedModifier() {
        showForm("notation")
        compose.onNodeWithTag("notation").performTextReplacement("d1" + " ".repeat(123) + "+100")
        compose.onNodeWithTag("roll_button").performClick()
        settled()
        assertTrue(model.state.value.history.isEmpty())
        assertEquals(InputIssue.NOTATION_LENGTH, model.state.value.notationIssue)
        compose.onNodeWithTag("notation").assertTextContains("d20")
        compose.onNodeWithTag("notation").performTextReplacement("d1+100")
        compose.onNodeWithTag("roll_button").performClick()
        settled()
        compose.onNodeWithTag("final_result").assertTextEquals("101").assertIsDisplayed()
    }

    @Test fun modeLabelIsSelectableAndExplainsWholeGroupRule() {
        Espresso.closeSoftKeyboard()
        expandSettings()
        showForm("mode_ADVANTAGE")
        compose.onNodeWithTag("mode_ADVANTAGE").performClick()
        compose.onNodeWithTag("mode_ADVANTAGE").assertIsSelected()
        assertEquals(RollMode.ADVANTAGE, model.state.value.mode)
        compose.onNodeWithText(compose.activity.getString(R.string.mode_advantage_hint)).assertExists()
    }

    @Test fun quickDieFillsExpressionWithoutChangingActiveSettingsOrRolling() {
        compose.runOnIdle {
            model.setName("Attack")
            model.setTimes("3")
            model.setMode(RollMode.ADVANTAGE)
        }
        showForm("quick_d6")
        compose.onNodeWithTag("quick_d6").performClick()
        assertEquals(DiceExpression(1, 6, 0), parseDiceNotation(model.state.value.notation))
        assertEquals("Attack", model.state.value.name)
        assertEquals("3", model.state.value.timesText)
        assertEquals(RollMode.ADVANTAGE, model.state.value.mode)
        assertTrue(model.state.value.history.isEmpty())
        compose.onNodeWithTag("quick_d6").assertIsSelected()
    }

    @Test fun repeatedRollHasOneSummaryAndCanReuseBatchOrIndividualSettings() {
        compose.runOnIdle {
            model.setName("Attack")
            model.setNotation("d1+2")
            model.setTimes("3")
            model.setMode(RollMode.ADVANTAGE)
            assertTrue(model.roll())
        }
        settled()
        val batch = model.state.value.history.single()
        assertEquals(3, batch.count)
        assertEquals(9L, batch.total)
        compose.onNodeWithTag("batch_total_${batch.id}").assertTextEquals("9").assertIsDisplayed()
        compose.runOnIdle { model.setNotation("d20"); model.setTimes("1"); model.setName("") }
        showHistory("load_batch_${batch.id}")
        compose.onNodeWithTag("load_batch_${batch.id}").performClick()
        assertEquals("1d1+2", model.state.value.notation)
        assertEquals("3", model.state.value.timesText)
        assertEquals("Attack", model.state.value.name)
        assertEquals(RollMode.ADVANTAGE, model.state.value.mode)
        assertEquals(1, model.state.value.history.size)

        showHistory("latest_details")
        compose.onNodeWithTag("latest_details").performClick()
        val singleTag = "load_result_${batch.results[1].id}"
        showHistory(singleTag)
        compose.onNodeWithTag(singleTag).performClick()
        assertEquals("1", model.state.value.timesText)
        assertEquals("1d1+2", model.state.value.notation)
        assertEquals(1, model.state.value.history.size)
    }

    @Test fun copyIncludesFullDiceDetailsBeyondTheFirstDisplayPage() {
        compose.runOnIdle { model.setNotation("137d1+2"); assertTrue(model.roll()) }
        settled()
        val batch = model.state.value.history.single()
        showHistory("copy_batch_${batch.id}")
        compose.onNodeWithTag("copy_batch_${batch.id}").performClick()
        lateinit var copied: String
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            copied = requireNotNull(clipboard.primaryClip).getItemAt(0).coerceToText(compose.activity).toString()
        }
        val dice = copied.substringAfter('[').substringBefore(']').split(", ")
        assertEquals(137, dice.size)
        assertTrue(dice.all { it == "1" })
        assertTrue(copied.contains("137d1+2"))
        assertTrue(copied.contains("139"))

        showHistory("latest_details")
        compose.onNodeWithTag("latest_details").performClick()
        val group = "group_${batch.results.single().id}_first"
        showHistory("${group}_next")
        compose.onNodeWithTag("${group}_next").performClick()
        showHistory("${group}_next")
        compose.onNodeWithTag("${group}_next").performClick()
        compose.onNodeWithTag("${group}_next").assertIsNotEnabled()
    }

    @Test fun clearRequiresConfirmationAndCancellationKeepsRecords() {
        compose.runOnIdle { model.roll() }
        settled()
        showHistory("clear_history")
        compose.onNodeWithTag("clear_history").performClick()
        compose.onNodeWithTag("dismiss_clear").performClick()
        assertEquals(1, model.state.value.history.size)
        compose.onNodeWithTag("clear_history").performClick()
        compose.onNodeWithTag("confirm_clear").performClick()
        settled()
        assertTrue(model.state.value.history.isEmpty())
        assertTrue(HistoryStore(compose.activity.filesDir).load().batches.isEmpty())
        showHistory("clear_history")
        compose.onNodeWithTag("clear_history").assertIsNotEnabled()
    }

    @Test fun cancellingBatchDoesNotPublishPartialResults() {
        compose.runOnIdle {
            model.setNotation("100d6")
            model.setTimes("100")
            assertTrue(model.roll())
            model.cancelRoll()
        }
        settled()
        assertEquals(RollStatus.CANCELLED, model.state.value.status)
        assertTrue(model.state.value.history.isEmpty())
        assertTrue(HistoryStore(compose.activity.filesDir).load().batches.isEmpty())
    }

    @Test fun fixedRollButtonRemainsAboveKeyboard() {
        showForm("notation")
        compose.onNodeWithTag("notation").performClick()
        compose.waitUntil(5_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!.isVisible(WindowInsetsCompat.Type.ime())
        }
        compose.waitForIdle()
        compose.onNodeWithTag("roll_button").assertIsDisplayed()
        val button = compose.onNodeWithTag("roll_button").fetchSemanticsNode().boundsInWindow
        val insets = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
        val keyboardTop = compose.activity.window.decorView.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        assertTrue("Roll button must sit above IME: $button / $keyboardTop", button.bottom <= keyboardTop + 1)
    }

    @Test fun invalidNotationCanReceiveFocusFromBottomOfLongHistory() {
        createBatches(10)
        compose.runOnIdle { model.setNotation("invalid") }
        showHistory("history_${model.state.value.history.last().id}")
        compose.onNodeWithTag("roll_button").performClick()
        compose.onNodeWithTag("notation").assertIsFocused().assertIsDisplayed()
    }

    @Test fun landscapeUsesSeparateFormAndResultsWhenTheWindowIsWideEnough() {
        val previous = compose.activity.requestedOrientation
        try {
            compose.runOnIdle { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            settled()
            assumeTrue(compose.activity.resources.configuration.screenWidthDp >= 720)
            compose.onNodeWithTag("form_list").assertIsDisplayed()
            compose.onNodeWithTag("main_list").assertIsDisplayed()
            showForm("notation")
            compose.onNodeWithTag("notation").assertIsDisplayed()
            compose.onNodeWithTag("roll_button").assertIsDisplayed()
        } finally {
            compose.runOnIdle { compose.activity.requestedOrientation = previous }
        }
    }
}
