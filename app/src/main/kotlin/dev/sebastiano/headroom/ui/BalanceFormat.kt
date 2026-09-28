package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.model.QuotaBalance
import java.math.RoundingMode
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

/**
 * An amount such as AI credits, with up to two decimals: "200", "9.99". An amount above zero that
 * would round to zero shows as the smallest step, "0.01", as the JetBrains IDEs show credits.
 */
fun formatAmount(amount: Double, locale: Locale): String {
    val shown = if (amount > 0 && amount < SMALLEST_AMOUNT) SMALLEST_AMOUNT else amount
    return NumberFormat.getNumberInstance(locale)
        .apply {
            minimumFractionDigits = 0
            maximumFractionDigits = AMOUNT_DECIMALS
            roundingMode = RoundingMode.HALF_UP
        }
        .format(shown)
}

private const val AMOUNT_DECIMALS = 2
private const val SMALLEST_AMOUNT = 0.01
