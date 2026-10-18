package dev.sebastiano.headroom.glasses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.xr.glimmer.Card
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.Icon
import androidx.xr.glimmer.LeadingImageCard
import androidx.xr.glimmer.Text
import androidx.xr.glimmer.pager.GlimmerHorizontalPager
import androidx.xr.glimmer.pager.rememberGlimmerPagerState
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.ProviderPalette
import dev.sebastiano.headroom.designsystem.headroomColorScheme
import dev.sebastiano.headroom.designsystem.providerLogoVector
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.QuotaDisplay
import dev.sebastiano.headroom.model.ThemePalette
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.ResetFormatter
import dev.sebastiano.headroom.ui.components.windowKindLabel
import dev.sebastiano.headroom.ui.formatBalance
import dev.sebastiano.headroom.ui.home.AccountSummary
import dev.sebastiano.headroom.ui.home.HomeUiState
import dev.sebastiano.headroom.ui.home.NextResetSummary
import dev.sebastiano.headroom.ui.home.WindowSummary
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * What the glasses show: one card at a time on a black background, which the additive display draws
 * as see-through. The first card is the next reset, then one card per account, whose logo has its
 * provider's colour from [palette]. Back closes the glasses activity.
 */
@Composable
fun GlassesScreen(
    state: HomeUiState,
    formatter: ResetFormatter,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    palette: ThemePalette = ThemePalette.Wallpaper,
) {
    BackHandler(onBack = onBack)
    val pages by rememberUpdatedState(glassesPages(state))
    val pagerState = rememberGlimmerPagerState(pageCount = { pages.size })
    // The touchpad moves focus, so a card must hold it before the first swipe. The pager moves it
    // from page to page after that.
    val firstCard = remember { FocusRequester() }
    Box(modifier = modifier.fillMaxSize().background(Color.Black).testTag(GLASSES_SCREEN_TAG)) {
        GlimmerHorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { page -> pages.getOrNull(page)?.key ?: page },
        ) { page ->
            val cardModifier = if (page == 0) Modifier.focusRequester(firstCard) else Modifier
            when (val shown = pages[page]) {
                is GlassesPage.NextReset ->
                    NextResetGlassesCard(shown.next, state.now, formatter, cardModifier)
                is GlassesPage.Account ->
                    AccountGlassesCard(
                        shown.account,
                        state.now,
                        formatter,
                        state.display,
                        cardModifier,
                        palette = palette,
                    )
            }
        }
    }
    LaunchedEffect(Unit) {
        // Once the first frame is laid out, the card is a focus target that can take focus.
        withFrameNanos {}
        firstCard.requestFocus()
    }
}

private val GlassesPage.key: String
    get() =
        when (this) {
            is GlassesPage.NextReset -> "next-reset"
            is GlassesPage.Account -> "account-${account.id}"
        }

@Composable
internal fun NextResetGlassesCard(
    next: NextResetSummary?,
    now: Instant,
    formatter: ResetFormatter,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.testTag(GLASSES_NEXT_RESET_TAG),
        title = {
            Text(
                stringResource(
                    if (next?.kind == WindowKind.Weekly) R.string.overview_next_weekly_reset
                    else R.string.overview_next_reset
                )
            )
        },
    ) {
        if (next == null) {
            Text(stringResource(R.string.glasses_no_reset))
        } else {
            Column {
                Text(
                    text = formatter.countdown(now, next.resetsAt),
                    style = GlimmerTheme.typography.titleLarge,
                )
                Text(
                    text =
                        stringResource(
                            R.string.overview_next_reset_when,
                            next.name,
                            formatter.long(next.resetsAt),
                        ),
                    style = GlimmerTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun AccountGlassesCard(
    account: AccountSummary,
    now: Instant,
    formatter: ResetFormatter,
    display: QuotaDisplay,
    modifier: Modifier = Modifier,
    /** The app's colour palette, which gives the logo its provider's colour. */
    palette: ThemePalette = ThemePalette.Wallpaper,
) {
    LeadingImageCard(
        modifier = modifier.testTag(glassesAccountTag(account.id)),
        image = {
            Icon(
                imageVector = providerLogoVector(account.provider),
                tint = glassesLogoColor(account.provider, palette),
                contentDescription = null,
                modifier = Modifier.size(GlimmerTheme.iconSizes.large),
            )
        },
        title = { Text(account.name) },
        subtitle = account.plan?.let { plan -> { Text(plan) } },
    ) {
        Column {
            accountLines(account, now, formatter, display).forEach { line ->
                Text(text = line, style = GlimmerTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * The colour of [provider]'s logo: the colour of its avatar in the app's dark theme with [palette].
 * The glasses always draw on black, where the dark theme's light tones read best.
 */
@Composable
private fun glassesLogoColor(provider: Provider, palette: ThemePalette): Color {
    val context = LocalContext.current
    return remember(context, provider, palette) {
        val primary = headroomColorScheme(context, palette, dark = true).primary
        ProviderPalette.colors(provider, primary, dark = true).accent
    }
}

/** The overview card's numbers as lines of text: the main quotas first, then the session. */
@Composable
private fun accountLines(
    account: AccountSummary,
    now: Instant,
    formatter: ResetFormatter,
    display: QuotaDisplay,
): List<String> = buildList {
    if (account.signInExpired) add(stringResource(R.string.stale_card_title))
    val primary = account.primary
    val balance = account.balance
    when {
        primary == null && balance != null ->
            add(
                stringResource(
                    R.string.account_balance,
                    formatBalance(balance, LocalLocale.current.platformLocale),
                )
            )
        account.allowances.isNotEmpty() ->
            account.allowances.forEach { window ->
                add(
                    windowLine(window.label, window, account, now, display) {
                        formatter.short(it, now)
                    }
                )
            }
        primary == null -> add(stringResource(R.string.account_no_data))
        else ->
            add(
                windowLine(windowKindLabel(primary.kind), primary, account, now, display) {
                    formatter.short(it, now)
                }
            )
    }
    account.session?.let { session ->
        add(
            windowLine(windowKindLabel(session.kind), session, account, now, display) {
                formatter.countdown(now, it)
            }
        )
    }
}

/**
 * "Weekly · 71% used · Wed 7:00 AM". A stale window whose reset time has passed says it has reset,
 * as the overview card does.
 */
@Composable
private fun windowLine(
    label: String,
    window: WindowSummary,
    account: AccountSummary,
    now: Instant,
    display: QuotaDisplay,
    formatReset: (Instant) -> String,
): String {
    val percent = display.percent(window.usedPercent).roundToInt()
    val amount =
        stringResource(
            if (display == QuotaDisplay.Used) R.string.percent_used else R.string.percent_left,
            percent,
        )
    val resetsAt = window.resetsAt
    return when {
        resetsAt == null -> stringResource(R.string.glasses_window_line_no_reset, label, amount)
        account.signInExpired && resetsAt <= now ->
            stringResource(
                R.string.glasses_window_line,
                label,
                amount,
                stringResource(R.string.stale_window_reset),
            )
        else -> stringResource(R.string.glasses_window_line, label, amount, formatReset(resetsAt))
    }
}

fun glassesAccountTag(accountId: String): String = "glasses-account-$accountId"

const val GLASSES_SCREEN_TAG: String = "glasses-screen"
const val GLASSES_NEXT_RESET_TAG: String = "glasses-next-reset"
