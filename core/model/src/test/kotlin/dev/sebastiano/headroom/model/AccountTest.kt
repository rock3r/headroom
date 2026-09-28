package dev.sebastiano.headroom.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountTest {
    @Test
    fun `an account is called by its provider until the user names it`() {
        val account = Account("a", Provider.Claude, "sam@example.com")

        assertEquals("Claude", account.name)
        assertEquals("Work", account.copy(nickname = "Work").name)
    }
}
