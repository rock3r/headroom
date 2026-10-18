package dev.sebastiano.headroom.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.designsystem.HeadroomIcons
import dev.sebastiano.headroom.model.Age
import dev.sebastiano.headroom.model.AuthFailureReason
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.ui.home.AccountSummary
import kotlin.time.Instant

fun signInExpiredRowTag(accountId: String): String = "sign-in-expired-$accountId"

const val SIGN_IN_EXPIRED_BANNER_TAG: String = "sign-in-expired-banner"

/** What the provider calls its own console or dashboard where API keys are created. */
@Composable
@ReadOnlyComposable
internal fun apiKeyConsoleName(provider: Provider): String =
    when (provider) {
        Provider.OpenCodeGo -> stringResource(R.string.api_key_console_opencode)
        Provider.ZAi -> stringResource(R.string.api_key_console_zai)
        else -> provider.displayName
    }

/** The short provider name used in inline messages, such as "OpenCode" for OpenCode Go. */
internal fun apiKeyProviderShortName(provider: Provider): String =
    when (provider) {
        Provider.OpenCodeGo -> "OpenCode"
        else -> provider.displayName
    }

/** "2 hours ago", rounded to the nearest unit (see [Age]), in the user's language. */
@Composable
@ReadOnlyComposable
private fun ago(at: Instant, now: Instant): String =
    when (val age = Age.between(at, now)) {
        Age.JustNow -> stringResource(R.string.age_just_now)
        is Age.Minutes -> pluralStringResource(R.plurals.age_minutes, age.count, age.count)
        is Age.Hours -> pluralStringResource(R.plurals.age_hours, age.count, age.count)
        is Age.Days -> pluralStringResource(R.plurals.age_days, age.count, age.count)
    }

/** "Last updated 2 hours ago", or "Not updated yet" before the first good sync. */
@Composable
@ReadOnlyComposable
fun lastUpdatedText(dataFrom: Instant?, now: Instant): String =
    dataFrom?.let { stringResource(R.string.stale_last_updated, ago(it, now)) }
        ?: stringResource(R.string.stale_never_updated)

/** What a screen reader says about stale data: "Sign-in expired, data from 2 hours ago". */
@Composable
@ReadOnlyComposable
fun staleDescription(
    dataFrom: Instant?,
    now: Instant,
    provider: Provider? = null,
    authReason: AuthFailureReason? = null,
): String =
    when {
        authReason == AuthFailureReason.KeyExpired ->
            dataFrom?.let { stringResource(R.string.stale_description_key_expired, ago(it, now)) }
                ?: stringResource(R.string.stale_description_key_expired_no_data)
        provider?.usesApiKey == true ->
            dataFrom?.let { stringResource(R.string.stale_description_key_rejected, ago(it, now)) }
                ?: stringResource(R.string.stale_description_key_rejected_no_data)
        else ->
            dataFrom?.let { stringResource(R.string.stale_description, ago(it, now)) }
                ?: stringResource(R.string.stale_description_no_data)
    }

@Composable
@ReadOnlyComposable
internal fun staleDescription(account: AccountSummary, now: Instant): String =
    staleDescription(account.dataFrom, now, account.provider, account.authReason)

/**
 * The compact call to action on an overview card whose sign-in expired: what happened, when the
 * numbers are from, and a Sign in or Replace API key button. It is never faded, unlike the numbers
 * above it.
 */
@Composable
fun SignInExpiredRow(
    accountId: String,
    dataFrom: Instant?,
    now: Instant,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    provider: Provider? = null,
    authReason: AuthFailureReason? = null,
) {
    val description = staleDescription(dataFrom, now, provider, authReason)
    val usesApiKey = provider?.usesApiKey == true
    val keyExpired = authReason == AuthFailureReason.KeyExpired
    val title =
        when {
            keyExpired -> stringResource(R.string.stale_card_title_key_expired)
            usesApiKey -> stringResource(R.string.stale_card_title_key_rejected)
            else -> stringResource(R.string.stale_card_title)
        }
    val body =
        if (keyExpired && provider != null) {
            stringResource(R.string.stale_banner_body_key_expired, apiKeyConsoleName(provider))
        } else {
            null
        }
    val action =
        if (usesApiKey || keyExpired) stringResource(R.string.stale_card_action_api_key)
        else stringResource(R.string.stale_card_action)
    Surface(
        modifier = modifier.fillMaxWidth().testTag(signInExpiredRowTag(accountId)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(HeadroomIcons.Error),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier =
                    Modifier.weight(1f).padding(start = 10.dp, end = 8.dp).clearAndSetSemantics {
                        contentDescription = description
                    }
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (body != null) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = lastUpdatedText(dataFrom, now),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = onSignIn,
                colors = errorButtonColors(),
                contentPadding = CompactButtonPadding,
                modifier = Modifier.heightIn(min = 40.dp),
            ) {
                Text(action)
            }
        }
    }
}

/**
 * The banner at the top of the detail of an account whose sign-in expired. It says why the numbers
 * below are faded, when they are from, and offers to sign in again or replace the API key.
 */
@Composable
fun SignInExpiredBanner(
    provider: Provider,
    dataFrom: Instant?,
    now: Instant,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    authReason: AuthFailureReason? = null,
) {
    val usesApiKey = provider.usesApiKey
    val keyExpired = authReason == AuthFailureReason.KeyExpired
    val title =
        when {
            keyExpired ->
                stringResource(R.string.stale_banner_title_key_expired, provider.displayName)
            usesApiKey ->
                stringResource(R.string.stale_banner_title_key_rejected, provider.displayName)
            else -> stringResource(R.string.stale_banner_title, provider.displayName)
        }
    val body =
        when {
            keyExpired ->
                stringResource(R.string.stale_banner_body_key_expired, apiKeyConsoleName(provider))
            usesApiKey ->
                stringResource(R.string.stale_banner_body_key_rejected, provider.displayName)
            else -> stringResource(R.string.stale_banner_body)
        }
    val action =
        if (usesApiKey || keyExpired) stringResource(R.string.stale_banner_action_api_key)
        else stringResource(R.string.stale_banner_action)
    Surface(
        modifier = modifier.fillMaxWidth().testTag(SIGN_IN_EXPIRED_BANNER_TAG),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(HeadroomIcons.Error),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp).semantics { heading() },
                    )
                }
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = lastUpdatedText(dataFrom, now),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = onSignIn,
                colors = errorButtonColors(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(action)
            }
        }
    }
}

@Composable
internal fun SignInExpiredBanner(
    account: AccountSummary,
    now: Instant,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SignInExpiredBanner(
        provider = account.provider,
        dataFrom = account.dataFrom,
        now = now,
        onSignIn = onSignIn,
        modifier = modifier,
        authReason = account.authReason,
    )
}

@Composable
private fun errorButtonColors() =
    ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
    )

private val CompactButtonPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
