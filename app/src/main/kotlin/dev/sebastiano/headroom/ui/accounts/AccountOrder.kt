package dev.sebastiano.headroom.ui.accounts

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The order of the accounts list while the user changes it. The list shows the stored order, except
 * after the user moves a row: then it shows their order until storage has caught up with it.
 */
@Stable
internal class AccountOrder {
    /** The ids in the order the user made, or null when the stored order is on show. */
    private var pending by mutableStateOf<List<String>?>(null)

    /** The rows to show: [saved] in the user's order. Accounts it does not know yet go last. */
    fun rows(saved: List<AccountRow>): List<AccountRow> {
        val order = pending ?: return saved
        val rank = order.withIndex().associate { (index, id) -> id to index }
        return saved.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
    }

    /**
     * Moves the account [id] to [toIndex] of the rows on show. Returns the new order of ids, or
     * null when nothing moved.
     */
    fun move(saved: List<AccountRow>, id: String, toIndex: Int): List<String>? {
        val ids = rows(saved).map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        if (from < 0 || toIndex !in ids.indices || from == toIndex) return null
        ids.add(toIndex, ids.removeAt(from))
        pending = ids
        return ids
    }

    /** Shows the stored order again once it matches the user's. */
    fun sync(saved: List<AccountRow>) {
        if (pending == saved.map { it.id }) pending = null
    }
}
