package dev.sebastiano.headroom.ui.components

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.model.QuotaErrorKind
import dev.sebastiano.headroom.model.WindowKind
import dev.sebastiano.headroom.ui.ResetFormatter
import java.time.ZoneId

/** A large rounded screen title with an optional subtitle and trailing content. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

/** A small label above a group of content. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    // The top padding sits on the row, so an icon centres on the text and not on the padding.
    Row(
        modifier = modifier.padding(top = 6.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** A rounded group of rows on the container surface. */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

/** A formatter for the user's zone, language and 12 or 24 hour clock. */
@Composable
fun rememberResetFormatter(zone: ZoneId): ResetFormatter {
    val context = LocalContext.current
    val locale = LocalLocale.current.platformLocale
    val is24Hour = DateFormat.is24HourFormat(context)
    return remember(zone, locale, is24Hour) { ResetFormatter(zone, locale, is24Hour) }
}

@Composable
@ReadOnlyComposable
fun windowKindLabel(kind: WindowKind): String =
    stringResource(
        when (kind) {
            WindowKind.Weekly -> R.string.window_weekly
            WindowKind.Monthly -> R.string.window_monthly
            WindowKind.Daily -> R.string.window_daily
            WindowKind.Session -> R.string.window_session
            WindowKind.Other -> R.string.window_other
        }
    )

@Composable
@ReadOnlyComposable
fun usedLabel(kind: WindowKind): String =
    stringResource(
        when (kind) {
            WindowKind.Weekly -> R.string.used_weekly
            WindowKind.Monthly -> R.string.used_monthly
            WindowKind.Daily -> R.string.used_daily
            WindowKind.Session -> R.string.used_session
            WindowKind.Other -> R.string.used_other
        }
    )

@Composable
@ReadOnlyComposable
fun errorText(kind: QuotaErrorKind): String =
    stringResource(
        when (kind) {
            QuotaErrorKind.Auth -> R.string.account_error_auth
            QuotaErrorKind.Access -> R.string.account_error_access
            QuotaErrorKind.RateLimited -> R.string.account_error_rate_limited
            QuotaErrorKind.Network -> R.string.account_error_network
            QuotaErrorKind.Parse -> R.string.account_error_parse
            QuotaErrorKind.Unknown -> R.string.account_error_unknown
        }
    )
