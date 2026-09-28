package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.model.QuotaBalance
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * A balance as money when its unit is a currency code, such as "$12.50" for USD. Other units show
 * the amount with two decimals and the unit after it.
 */
fun formatBalance(balance: QuotaBalance, locale: Locale): String {
    val currency =
        try {
            Currency.getInstance(balance.unit.uppercase(Locale.ROOT))
        } catch (_: IllegalArgumentException) {
            null
        }
    return if (currency != null) {
        NumberFormat.getCurrencyInstance(locale)
            .apply { this.currency = currency }
            .format(balance.amount)
    } else {
        String.format(locale, "%.2f %s", balance.amount, balance.unit).trim()
    }
}
