package dev.sebastiano.headroom.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountBadgeTest {
    @Test
    fun `an account with no others of its provider gets no badge`() {
        assertNull(accountBadge("Claude", others = emptyList()))
    }

    @Test
    fun `the first word that differs gives the letter`() {
        assertEquals("M", accountBadge("Codex main", listOf("Codex Duck")))
        assertEquals("D", accountBadge("Codex Duck", listOf("Codex main")))
    }

    @Test
    fun `a word must differ from every other account's word in the same place`() {
        // "Work" is shared with one of the others, so the second word decides.
        assertEquals("L", accountBadge("Work laptop", listOf("Work phone", "Home phone")))
        // No single word differs from both others, so the first letter is the best there is.
        assertEquals("W", accountBadge("Work laptop", listOf("Work phone", "Home laptop")))
    }

    @Test
    fun `the provider name against a nickname uses the nickname's first letter`() {
        assertEquals("C", accountBadge("ChatGPT Codex", listOf("Side project")))
        assertEquals("S", accountBadge("Side project", listOf("ChatGPT Codex")))
    }

    @Test
    fun `identical names fall back to the first letter`() {
        assertEquals("C", accountBadge("Claude", listOf("Claude")))
    }

    @Test
    fun `a longer name wins where the others have run out of words`() {
        assertEquals("P", accountBadge("Claude Pro", listOf("Claude")))
    }

    @Test
    fun `extra spaces and case do not matter`() {
        assertEquals("D", accountBadge("  codex   duck ", listOf("Codex Main")))
        assertEquals("C", accountBadge("codex main", listOf("Codex Main")))
    }

    @Test
    fun `letters outside Latin and outside the basic plane keep working`() {
        assertEquals("Ж", accountBadge("Кодекс жёлтый", listOf("Кодекс синий")))
        assertEquals("𝔸", accountBadge("𝔸lpha", listOf("Beta")))
    }
}
