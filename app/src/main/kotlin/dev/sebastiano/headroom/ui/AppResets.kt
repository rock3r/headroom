package dev.sebastiano.headroom.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sebastiano.headroom.AppGraph
import dev.sebastiano.headroom.model.RedeemIntent
import dev.sebastiano.headroom.prototype.PrototypeEnv
import dev.sebastiano.headroom.prototype.PrototypeTools
import dev.sebastiano.headroom.signin.ServiceSignIn
import dev.sebastiano.headroom.signin.ServiceSignInState
import dev.sebastiano.headroom.signin.signInTabIntent
import dev.sebastiano.headroom.ui.home.AccountSummary
import dev.sebastiano.headroom.ui.home.DetailUiState
import dev.sebastiano.headroom.ui.home.HomeUiState
import dev.sebastiano.headroom.ui.resets.AccountResets
import dev.sebastiano.headroom.ui.resets.RedeemSheet
import dev.sebastiano.headroom.ui.resets.ResetHandlers

/**
 * The redeem sheet over the app, while [sheet] asks for it. [onSignInAgain] opens the accounts
 * screen, for an account whose sign-in the provider no longer accepts. A real Z.AI account can sign
 * in to ZCode from the sheet: its page is readied while the user reads, so the tap opens it at once
 * in a browser tab, and once the sign-in finishes the sheet shows the account's resets.
 */
@Composable
internal fun ResetSheetHost(
    sheet: ResetSheetState,
    graph: AppGraph,
    home: HomeUiState,
    resets: AccountResets,
    formatter: ResetFormatter,
    onSignInAgain: () -> Unit,
) {
    val request = sheet.request ?: return
    val summary = home.accountsInYourOrder.firstOrNull { it.id == request.accountId }
    val accounts by graph.quotaRepository.accounts.collectAsStateWithLifecycle()
    val account = accounts.firstOrNull { it.account.id == request.accountId }
    val availability = resets.of(request.accountId)
    if (summary == null || account == null || availability == null) {
        SideEffect { sheet.dismiss() }
        return
    }
    val isDemo by graph.isDemo.collectAsStateWithLifecycle()
    val zCode = graph.zCodeSignIn?.takeIf { !isDemo }
    val signIn = zCodeSignInFor(zCode, request.accountId)
    val context = LocalContext.current
    // Once per change: the page opens when this sheet's sign-in starts waiting for the browser.
    DisposableEffect(signIn) {
        if (signIn is ServiceSignInState.Waiting) {
            signInTabIntent(context).launchUrl(context, signIn.url.toUri())
        }
        onDispose {}
    }
    // Ready the sign-in page while the user reads, so the tap opens it at once. A failed sign-in
    // readies a new page, so its retry opens at once too. A readied page expires a few minutes
    // after it is made, so it is forgotten when the sheet goes.
    DisposableEffect(zCode, request.accountId, availability.requiresSignIn, signIn) {
        if (availability.requiresSignIn) zCode?.prepare(request.accountId)
        onDispose { zCode?.forgetPrepared(request.accountId) }
    }
    RedeemSheet(
        account = account.account,
        summary = summary,
        availability = availability,
        provider = graph.resets,
        intent = request.intent,
        display = home.display,
        formatter = formatter,
        memory = graph.resetMemory,
        onDismiss = sheet::dismiss,
        onSignInAgain = {
            sheet.dismiss()
            onSignInAgain()
        },
        refreshing = resets.isRefreshing(request.accountId),
        onSignIn = zCode?.let { { it.start(request.accountId) } },
        signIn = signIn,
        onBusyChange = { sheet.busy = it },
    )
}

/**
 * How the ZCode sign-in of [accountId] is going, once it changed while this sheet was open. A
 * sign-in that was already waiting or done when the sheet opened shows as idle, so opening the
 * sheet again does not open its page again.
 */
@Composable
private fun zCodeSignInFor(zCode: ServiceSignIn?, accountId: String): ServiceSignInState {
    zCode ?: return ServiceSignInState.Idle
    val atOpen = remember(zCode, accountId) { zCode.state.value }
    val state by zCode.state.collectAsStateWithLifecycle()
    return if (state.accountId == accountId && state != atOpen) state else ServiceSignInState.Idle
}

/**
 * [home] and [detail] as the screens under the redeem sheet show them. While the sheet is open for
 * the account [heldId], they keep that account as it was when the sheet opened, and hold back its
 * reset confetti. The sheet itself shows the live data. Once the sheet closes, the new usage flows
 * in, so the bars animate to it where the user can see them, as they do after any refresh.
 */
@Composable
internal fun holdUnderSheet(
    home: HomeUiState,
    detail: DetailUiState?,
    heldId: String?,
): Pair<HomeUiState, DetailUiState?> {
    val held =
        remember(heldId) {
            heldId?.let { id -> home.accountsInYourOrder.firstOrNull { it.id == id } }
        } ?: return home to detail
    val hold = { accounts: List<AccountSummary> ->
        accounts.map { if (it.id == held.id) held else it }
    }
    val shownHome =
        home.copy(
            accounts = hold(home.accounts),
            accountsInYourOrder = hold(home.accountsInYourOrder),
            resetBursts = home.resetBursts.filterNot { it.accountId == held.id },
        )
    val shownDetail = detail?.let { if (it.account.id == held.id) it.copy(account = held) else it }
    return shownHome to shownDetail
}

/** Which account the redeem sheet is open for, and why. */
internal data class ResetSheetRequest(val accountId: String, val intent: RedeemIntent)

/** The redeem sheet's request. Saved across configuration changes. */
@Stable
internal class ResetSheetState(request: ResetSheetRequest? = null) {
    var request by mutableStateOf(request)
    /** True while the provider works or the new usage comes in: nothing closes the sheet then. */
    var busy by mutableStateOf(false)

    /** Closes the sheet. */
    fun dismiss() {
        busy = false
        request = null
    }

    /** Opens the sheet. */
    fun handlers() =
        ResetHandlers(
            onUse = { request = ResetSheetRequest(it, RedeemIntent.Use) },
            onAsk = { request = ResetSheetRequest(it, RedeemIntent.AskForMore) },
        )

    companion object {
        val Saver: Saver<ResetSheetState, Any> =
            listSaver(
                save = { state ->
                    listOf(
                        state.request?.accountId.orEmpty(),
                        state.request?.intent?.name.orEmpty(),
                    )
                },
                restore = { (id, intent) ->
                    ResetSheetState(
                        request =
                            id.takeIf { it.isNotEmpty() }
                                ?.let { ResetSheetRequest(it, RedeemIntent.valueOf(intent)) }
                    )
                },
            )
    }
}

/** The resets of every account, and which accounts are refreshing after a reset. */
@Composable
internal fun collectResets(graph: AppGraph): AccountResets {
    val availability by graph.resets.availability.collectAsStateWithLifecycle()
    val refreshing by graph.resets.refreshing.collectAsStateWithLifecycle()
    return AccountResets(availability, refreshing)
}

/** The debug build's prototypes page. Release builds have no tools, so the page stays empty. */
@Composable
internal fun PrototypesPage(
    graph: AppGraph,
    home: HomeUiState,
    formatter: ResetFormatter,
    onBack: () -> Unit,
) {
    val tools = graph.prototypes ?: return
    tools.Screen(
        env = PrototypeEnv(display = home.display, formatter = formatter, now = home.now),
        onBack = onBack,
    )
}

/** The Settings row that opens the prototypes page. */
internal fun PrototypeTools.settingsEntry(onOpen: () -> Unit): @Composable (Modifier) -> Unit =
    { rowModifier ->
        SettingsEntry(onOpen = onOpen, modifier = rowModifier)
    }
