package dev.sebastiano.headroom.ui.resets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.ProviderAvatar
import dev.sebastiano.headroom.designsystem.animationsEnabled
import dev.sebastiano.headroom.model.Account
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.RedeemIntent
import dev.sebastiano.headroom.model.RedeemOutcome
import dev.sebastiano.headroom.model.RedeemSession
import dev.sebastiano.headroom.model.RedeemStep
import dev.sebastiano.headroom.model.ResetAttemptMemory
import dev.sebastiano.headroom.model.ResetAvailability
import dev.sebastiano.headroom.model.ResetPool
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.signin.SignInError
import dev.sebastiano.headroom.signin.ZCodeSignInState
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.delights.DelightsHost
import dev.sebastiano.headroom.ui.delights.LocalDelights
import dev.sebastiano.headroom.ui.delights.confettiColors
import dev.sebastiano.headroom.ui.delights.delightAnchor
import dev.sebastiano.headroom.ui.home.AccountSummary
import dev.sebastiano.headroom.ui.home.WindowSummary
import kotlinx.coroutines.launch

const val REDEEM_SHEET_TAG: String = "redeem-sheet"
const val REDEEM_CONFIRM_TAG: String = "redeem-confirm"
const val REDEEM_TRY_AGAIN_TAG: String = "redeem-try-again"
const val REDEEM_CHECK_AGAIN_TAG: String = "redeem-check-again"

fun poolChoiceTag(poolId: String): String = "redeem-pool-$poolId"

/** The actions of the redeem sheet's steps. */
data class RedeemActions(
    val onChoose: (poolId: String) -> Unit = {},
    val onBack: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onTryAgain: () -> Unit = {},
    val onAsk: () -> Unit = {},
    val onUseNow: () -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onCheckAgain: () -> Unit = {},
    /** The provider no longer accepts the account's sign-in: open the accounts screen. */
    val onSignInAgain: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/**
 * Uses a reset, or asks for one, in a bottom sheet with animated steps: choose the pool (only when
 * more than one has resets), confirm, resetting, and the outcome. While the provider works the
 * sheet cannot be dismissed: dragging, back and taps on the scrim do nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedeemSheet(
    account: Account,
    summary: AccountSummary,
    availability: ResetAvailability,
    provider: ResetProvider,
    intent: RedeemIntent,
    display: QuotaDisplay,
    formatter: ResetFormatter,
    memory: ResetAttemptMemory,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onSignInAgain: () -> Unit = {},
    /** True while the account's usage is being refreshed after the reset. */
    refreshing: Boolean = false,
    /**
     * Starts the separate sign-in the provider's resets need (Z.AI's ZCode), or null where there is
     * none, as for the demo accounts.
     */
    onSignIn: (() -> Unit)? = null,
    /** How that sign-in is going, once it started from this sheet. */
    signIn: ZCodeSignInState = ZCodeSignInState.Idle,
) {
    val session =
        remember(account.id, intent) {
            RedeemSession(account, availability, provider, intent, memory)
        }
    val step by session.step.collectAsStateWithLifecycle()
    // Nothing closes the sheet while the provider works, or while the new usage comes in.
    val busy =
        step is RedeemStep.Resetting ||
            step is RedeemStep.Checking ||
            step is RedeemStep.Asking ||
            (step is RedeemStep.Finished &&
                (step as RedeemStep.Finished).outcome is RedeemOutcome.Success &&
                refreshing)
    val currentBusy by rememberUpdatedState(busy)
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || !currentBusy },
        )
    val scope = rememberCoroutineScope()
    LaunchedEffect(session) { session.start() }
    var stubNote by remember { mutableStateOf(false) }
    val actions =
        RedeemActions(
            onChoose = session::choose,
            onBack = session::back,
            onConfirm = { scope.launch { session.confirm() } },
            onTryAgain = { scope.launch { session.tryAgain() } },
            onAsk = { scope.launch { session.ask() } },
            onUseNow = { scope.launch { session.useNow() } },
            onSignIn = onSignIn ?: { stubNote = true },
            onCheckAgain = { scope.launch { session.checkAgain() } },
            // Z.AI's resets only use the ZCode sign-in, so that is the one to do again.
            onSignInAgain = onSignIn?.takeIf { account.provider == Provider.ZAi } ?: onSignInAgain,
            onClose = {
                scope
                    .launch { sheetState.hide() }
                    .invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
            },
        )
    // Signed in: the account's resets are read again, and the sheet has done its job.
    DisposableEffect(signIn) {
        if (signIn is ZCodeSignInState.Done) actions.onClose()
        onDispose {}
    }
    val signInNote =
        if (stubNote) stringResource(R.string.redeem_sign_in_stub)
        else signInLine(signIn, ResetCopy.signInService(account.provider))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = SheetMaxWidth,
        sheetGesturesEnabled = !busy,
        properties =
            ModalBottomSheetProperties(
                shouldDismissOnBackPress = !busy,
                shouldDismissOnClickOutside = !busy,
            ),
        modifier = modifier,
    ) {
        RedeemSheetContent(
            step = step,
            summary = summary,
            display = display,
            formatter = formatter,
            actions = actions,
            canAskForMore = availability.canAskForMore,
            signInNote = signInNote,
            refreshing = refreshing,
        )
    }
}

/** The line that says how the sign-in to [service] is going, or null before it starts. */
@Composable
@ReadOnlyComposable
private fun signInLine(state: ZCodeSignInState, service: String): String? =
    when (state) {
        ZCodeSignInState.Idle,
        is ZCodeSignInState.Done -> null
        is ZCodeSignInState.Starting,
        is ZCodeSignInState.Waiting -> stringResource(R.string.redeem_sign_in_waiting, service)
        is ZCodeSignInState.Failed ->
            when (state.error) {
                SignInError.Network -> stringResource(R.string.redeem_sign_in_network, service)
                SignInError.Expired -> stringResource(R.string.redeem_sign_in_expired, service)
                else -> stringResource(R.string.redeem_sign_in_failed, service)
            }
    }

/** On a wide screen the sheet stays about as wide as a phone. */
private val SheetMaxWidth = 560.dp

/**
 * What the redeem sheet shows at [step], without the sheet itself: for tests and screenshots. The
 * usage bars stay on screen from the confirmation to the outcome. They shimmer while the reset
 * applies and while [refreshing] brings in the new usage, then refill from it. Confetti bursts from
 * the bars once they refill, when the user's "Reset confetti" delight is on.
 */
@Composable
fun RedeemSheetContent(
    step: RedeemStep,
    summary: AccountSummary,
    display: QuotaDisplay,
    formatter: ResetFormatter,
    actions: RedeemActions,
    modifier: Modifier = Modifier,
    canAskForMore: Boolean = false,
    /** A line under the sign-in step: how the sign-in is going, or null. */
    signInNote: String? = null,
    refreshing: Boolean = false,
) {
    val confetti = LocalDelights.current?.canBurst == true
    DelightsHost(refreshShimmer = false, resetConfetti = confetti, modifier = modifier) {
        // The sheet follows this column's height, so every part in it changes its height on the
        // same spatial spring, starting on the same frame. Each gap belongs to the part below it:
        // a gap between the column's children would appear or vanish in one frame, and the sheet's
        // top edge would jump by it.
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .testTag(REDEEM_SHEET_TAG)
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
        ) {
            SheetHeader(summary)
            UsageArea(step, summary, display, refreshing)
            Steps(
                step,
                summary,
                formatter,
                actions,
                canAskForMore,
                signInNote,
                display,
                refreshing,
            )
        }
        ConfettiOnSuccess(step, summary, refreshing)
    }
}

@Composable
private fun ConfettiOnSuccess(step: RedeemStep, summary: AccountSummary, refreshing: Boolean) {
    val delights = LocalDelights.current ?: return
    val colors = confettiColors(summary.provider)
    val refilled =
        step is RedeemStep.Finished && step.outcome is RedeemOutcome.Success && !refreshing
    val pool = (step as? RedeemStep.Finished)?.pool
    LaunchedEffect(refilled) {
        if (!refilled) return@LaunchedEffect
        // The sheet is in front, so it plays this reset's confetti, and the screens behind it,
        // which see the same reset in their data, do not: see Delights.claimReset.
        pool?.let { reset ->
            affectedWindows(summary, reset).forEach { delights.claimReset(summary.id, it.id) }
        }
        // Bursts as the bars start to refill.
        delights.burstWhenShown(listOf(BARS_ANCHOR), colors)
    }
}

private const val BARS_ANCHOR = "redeem-bars"

@Composable
private fun SheetHeader(summary: AccountSummary) {
    val label = stringResource(R.string.redeem_sheet_label, summary.name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = label },
    ) {
        ProviderAvatar(provider = summary.provider, size = 36.dp)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = summary.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.resets_card_title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The pool whose usage the bars show at [step], or null when the step shows no usage. */
private fun ringPool(step: RedeemStep): ResetPool? =
    when (step) {
        is RedeemStep.Confirm -> step.pool
        is RedeemStep.Resetting -> step.pool
        is RedeemStep.Checking -> step.pool
        is RedeemStep.Finished -> step.pool.takeIf { step.outcome != RedeemOutcome.Unsupported }
        else -> null
    }

/**
 * The usage bars of the account, with the windows the pool resets in full. They stay composed while
 * the steps change under them, so the new usage after a success refills them in place.
 */
@Composable
private fun UsageArea(
    step: RedeemStep,
    summary: AccountSummary,
    display: QuotaDisplay,
    refreshing: Boolean,
) {
    val pool = ringPool(step)
    // Keeps the last pool while the bars leave, so they do not empty as they fade out.
    var shownPool by remember { mutableStateOf(pool) }
    if (pool != null) shownPool = pool
    val success = step is RedeemStep.Finished && step.outcome is RedeemOutcome.Success
    val working =
        step is RedeemStep.Resetting || step is RedeemStep.Checking || (success && refreshing)
    val animate = animationsEnabled()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fast = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    // The area grows from its top, and is not clipped: every bar shows, whole, from the first
    // frame, while the space for them opens below the header.
    AnimatedVisibility(
        visible = pool != null,
        enter =
            if (animate) {
                fadeIn(effects) + expandVertically(spatial, Alignment.Top, clip = false)
            } else {
                fadeIn(fast)
            },
        exit =
            if (animate) {
                fadeOut(fast) + shrinkVertically(spatial, Alignment.Top, clip = false)
            } else {
                fadeOut(fast)
            },
        modifier = Modifier.fillMaxWidth(),
    ) {
        shownPool?.let { shown ->
            RefillBars(
                windows = sheetWindows(summary),
                cleared = affectedWindows(summary, shown).map { it.id }.toSet(),
                display = display,
                working = working,
                modifier = Modifier.padding(top = SECTION_GAP).delightAnchor(BARS_ANCHOR),
            )
        }
    }
}

/**
 * The windows the sheet shows as bars: the usage limits. A reset never restores a credit or a quota
 * the app does not know, so they stay out.
 */
internal fun sheetWindows(summary: AccountSummary): List<WindowSummary> =
    summary.windows.filter { it.kind != WindowKind.Credit && it.isRecognised }

/** The windows a reset from [pool] restores: every usage limit when the provider does not say. */
internal fun affectedWindows(summary: AccountSummary, pool: ResetPool): List<WindowSummary> =
    sheetWindows(summary)
        .filter { pool.scope.covers(it.id, it.kind) }
        .ifEmpty { listOfNotNull(summary.primary) }

/** A key per kind of step, so the content animates between steps but not within one. */
private fun RedeemStep.key(): String =
    when (this) {
        is RedeemStep.ChoosePool -> "choose"
        is RedeemStep.Confirm -> "confirm-${pool.id}"
        is RedeemStep.Resetting -> "resetting"
        is RedeemStep.Checking -> "checking"
        is RedeemStep.Finished -> "finished-${outcome::class.simpleName}"
        RedeemStep.Asking -> "asking"
        is RedeemStep.Answered -> "answered-${answer::class.simpleName}"
        RedeemStep.SignInRequired -> "sign-in"
    }

/**
 * The step's words and buttons. A new step fades and grows in while the sheet resizes around it on
 * the spatial spring, like a container transform; with reduced motion it only crossfades.
 */
@Composable
private fun Steps(
    step: RedeemStep,
    summary: AccountSummary,
    formatter: ResetFormatter,
    actions: RedeemActions,
    canAskForMore: Boolean,
    signInNote: String?,
    display: QuotaDisplay,
    refreshing: Boolean,
) {
    val animate = animationsEnabled()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fast = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    AnimatedContent(
        targetState = step,
        contentKey = { it.key() },
        transitionSpec = {
            if (animate) {
                (fadeIn(effects) + scaleIn(effects, initialScale = STEP_ENTER_SCALE)) togetherWith
                    fadeOut(fast) using
                    SizeTransform(clip = false) { _, _ -> spatial }
            } else {
                fadeIn(fast) togetherWith fadeOut(fast) using SizeTransform { _, _ -> snap() }
            }
        },
        label = "redeem step",
        modifier = Modifier.fillMaxWidth().padding(top = SECTION_GAP),
    ) { shown ->
        val provider = summary.provider.displayName
        when (shown) {
            is RedeemStep.ChoosePool -> ChoosePool(shown, summary, display, actions)
            is RedeemStep.Confirm -> Confirm(shown, summary, formatter, actions)
            is RedeemStep.Resetting -> Working(stringResource(R.string.redeem_resetting))
            is RedeemStep.Checking -> Working(stringResource(R.string.redeem_checking))
            is RedeemStep.Finished ->
                Finished(shown, provider, canAskForMore, formatter, actions, refreshing)
            RedeemStep.Asking -> Working(stringResource(R.string.redeem_asking, provider))
            is RedeemStep.Answered -> Answered(shown.answer, provider, formatter, actions)
            RedeemStep.SignInRequired -> SignIn(summary, signInNote, actions)
        }
    }
}

private const val STEP_ENTER_SCALE = 0.94f

/** The space between the header, the usage bars and the step. */
private val SECTION_GAP = 20.dp
