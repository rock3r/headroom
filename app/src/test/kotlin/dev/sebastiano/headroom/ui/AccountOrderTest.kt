package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.ui.accounts.AccountOrder
import dev.sebastiano.headroom.ui.accounts.AccountRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountOrderTest {
    private fun rows(vararg ids: String) = ids.map { AccountRow(it, Provider.Claude, it, null) }

    private fun List<AccountRow>.ids() = map { it.id }

    @Test
    fun `the stored order shows until the user moves a row`() {
        val order = AccountOrder()
        val saved = rows("a", "b", "c")

        assertEquals(listOf("a", "b", "c"), order.rows(saved).ids())
        assertEquals(listOf("b", "c", "a"), order.move(saved, "a", 2))
        assertEquals(listOf("b", "c", "a"), order.rows(saved).ids())
    }

    @Test
    fun `a move to the same place or out of the list does nothing`() {
        val order = AccountOrder()
        val saved = rows("a", "b")

        assertNull(order.move(saved, "a", 0))
        assertNull(order.move(saved, "a", 2))
        assertNull(order.move(saved, "unknown", 0))
    }

    @Test
    fun `an account added while the new order is saving goes last`() {
        val order = AccountOrder()
        order.move(rows("a", "b"), "b", 0)

        assertEquals(listOf("b", "a", "c"), order.rows(rows("a", "b", "c")).ids())
    }

    @Test
    fun `the stored order takes over once it matches`() {
        val order = AccountOrder()
        order.move(rows("a", "b"), "b", 0)
        order.sync(rows("b", "a"))

        // A later change from storage now shows as it is.
        assertEquals(listOf("a", "b"), order.rows(rows("a", "b")).ids())
    }
}
