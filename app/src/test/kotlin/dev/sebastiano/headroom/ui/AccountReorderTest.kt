package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.width
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.ui.accounts.ACCOUNT_NAME_FIELD_TAG
import dev.sebastiano.headroom.ui.accounts.AccountRow
import dev.sebastiano.headroom.ui.accounts.AccountsActions
import dev.sebastiano.headroom.ui.accounts.AccountsScreen
import dev.sebastiano.headroom.ui.accounts.AccountsStep
import dev.sebastiano.headroom.ui.accounts.AccountsUiState
import dev.sebastiano.headroom.ui.accounts.accountDragHandleTag
import dev.sebastiano.headroom.ui.accounts.accountRowTag
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The user puts their accounts in the order they want: by dragging a row's handle, by long pressing
 * a row, or with accessibility actions. Demo accounts keep their order.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class AccountReorderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val orders = mutableListOf<List<String>>()
    private val actions =
        AccountsActions(
            onClose = {},
            onAddAccount = {},
            onPickProvider = {},
            onBack = {},
            onSubmitCode = {},
            onSubmitApiKey = {},
            onRetry = {},
            onFinish = {},
            onOpenUrl = {},
            onCopy = {},
            onRename = { _, _ -> },
            onRemove = {},
            onReorder = { ids -> orders += ids },
        )

    private fun show(isDemo: Boolean = false, reduceMotion: Boolean = false) {
        val accounts =
            listOf(
                AccountRow("a1", Provider.Claude, "sam@example.com", "Max"),
                AccountRow("a2", Provider.Claude, "sam@work.example", "Pro", nickname = "Work"),
                AccountRow("a3", Provider.Codex, "sam@example.com", "Plus"),
            )
        rule.setContent {
            HeadroomTheme(dynamicColor = false, reduceMotion = reduceMotion) {
                AccountsScreen(AccountsUiState(accounts, isDemo, AccountsStep.List), actions)
            }
        }
    }

    private fun row(id: String) = rule.onNodeWithTag(accountRowTag(id))

    private fun handle(id: String) =
        rule.onNodeWithTag(accountDragHandleTag(id), useUnmergedTree = true)

    private fun top(id: String): Dp = row(id).getBoundsInRoot().top

    /** The accounts in the order they are drawn, top to bottom. */
    private fun shownOrder(): List<String> = listOf("a1", "a2", "a3").sortedBy { top(it).value }

    /** Moves the pointer down in small steps, so the list sees it cross each row. */
    private fun TouchInjectionScope.dragDown(rows: Float) {
        val step = height * rows / DRAG_STEPS
        repeat(DRAG_STEPS) { moveBy(Offset(0f, step)) }
    }

    @Test
    fun `dragging a row by its handle moves it and saves the new order`() {
        show()

        handle("a1").performTouchInput {
            down(center)
            // The handle is shorter than the row: measure the drag in rows, not handle heights.
            val rowHeight = height * ROW_TO_HANDLE
            repeat(DRAG_STEPS) { moveBy(Offset(0f, rowHeight * 1.5f / DRAG_STEPS)) }
            up()
        }
        rule.waitForIdle()

        assertEquals(listOf(listOf("a2", "a1", "a3")), orders)
        assertEquals(listOf("a2", "a1", "a3"), shownOrder())
        rule.onNodeWithTag(ACCOUNT_NAME_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun `a long press picks a row up to drag it`() {
        show()

        row("a1").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MILLIS)
            dragDown(rows = 1.5f)
            up()
        }
        rule.waitForIdle()

        assertEquals(listOf(listOf("a2", "a1", "a3")), orders)
        assertEquals(listOf("a2", "a1", "a3"), shownOrder())
        rule.onNodeWithTag(ACCOUNT_NAME_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun `a long press without a drag does not open the editor`() {
        show()

        row("a1").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MILLIS)
            up()
        }
        rule.waitForIdle()

        rule.onNodeWithTag(ACCOUNT_NAME_FIELD_TAG).assertDoesNotExist()
        assertEquals(emptyList(), orders)
    }

    @Test
    fun `a lifted row grows a little`() {
        show()
        val resting = row("a2").getBoundsInRoot()

        row("a2").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MILLIS)
            moveBy(Offset(0f, 1f))
        }
        rule.waitForIdle()

        val lifted = row("a2").getBoundsInRoot()
        assertTrue(lifted.width > resting.width, "$lifted is not wider than $resting")
        row("a2").performTouchInput { up() }
    }

    @Test
    fun `with reduced motion a lifted row keeps its size`() {
        show(reduceMotion = true)
        val resting = row("a2").getBoundsInRoot()

        row("a2").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MILLIS)
            moveBy(Offset(0f, 1f))
        }
        rule.waitForIdle()

        assertEquals(resting.width, row("a2").getBoundsInRoot().width)
        row("a2").performTouchInput { up() }
    }

    @Test
    fun `accessibility actions move a row and announce where it went`() {
        show()

        row("a2").performCustomAccessibilityActionWithLabel("Move up")

        assertEquals(listOf(listOf("a2", "a1", "a3")), orders)
        assertEquals(listOf("a2", "a1", "a3"), shownOrder())
        rule
            .onNode(
                hasContentDescription("Work moved to position 1 of 3") and
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.LiveRegion,
                        LiveRegionMode.Polite,
                    )
            )
            .assertExists()

        row("a2").performCustomAccessibilityActionWithLabel("Move down")
        row("a3").performCustomAccessibilityActionWithLabel("Move to top")

        assertEquals(listOf("a3", "a1", "a2"), orders.last())
        assertEquals(listOf("a3", "a1", "a2"), shownOrder())
    }

    @Test
    fun `the first and last rows only offer the moves they can make`() {
        show()

        row("a1").assert(hasActions("Move down"))
        row("a2").assert(hasActions("Move up", "Move down"))
        row("a3").assert(hasActions("Move up", "Move to top"))
    }

    @Test
    fun `demo accounts cannot be reordered`() {
        show(isDemo = true)

        handle("a1").assertDoesNotExist()
        row("a1").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
        row("a1").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MILLIS)
            dragDown(rows = 1.5f)
            up()
        }
        rule.waitForIdle()

        assertEquals(emptyList(), orders)
        assertEquals(listOf("a1", "a2", "a3"), shownOrder())
    }

    @Test
    fun `while a row is edited the others cannot be moved`() {
        show()
        rule.onNodeWithText("Work").performClick()

        row("a1").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
        row("a3").performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MILLIS)
            moveBy(Offset(0f, -height * 2f))
            up()
        }
        rule.waitForIdle()

        assertEquals(emptyList(), orders)
    }

    private fun hasActions(vararg labels: String) =
        SemanticsMatcher("has the custom actions ${labels.toList()}") { node ->
            node.config.getOrNull(SemanticsActions.CustomActions)?.map { it.label } ==
                labels.toList()
        }

    private companion object {
        const val DRAG_STEPS = 12
        const val LONG_PRESS_MILLIS = 1_000L

        /** How many handle heights make one row, roughly. */
        const val ROW_TO_HANDLE = 64f / 24f
    }
}
