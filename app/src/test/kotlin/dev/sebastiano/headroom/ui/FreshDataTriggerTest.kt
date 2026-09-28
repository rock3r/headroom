package dev.sebastiano.headroom.ui

import dev.sebastiano.headroom.ui.home.FreshDataTrigger
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FreshDataTriggerTest {
    private val before: Instant = FIXED_NOW.minus(Duration.ofMinutes(20))
    private val trigger = FreshDataTrigger()

    @Test
    fun `a refresh that brings new data plays once, when it ends`() {
        assertFalse(trigger.update(isRefreshing = false, lastSyncedAt = before))
        assertFalse(trigger.update(isRefreshing = true, lastSyncedAt = before))
        assertTrue(trigger.update(isRefreshing = false, lastSyncedAt = FIXED_NOW))
        assertFalse(trigger.update(isRefreshing = false, lastSyncedAt = FIXED_NOW))
    }

    @Test
    fun `new data that arrives before the refresh ends still plays, at the end`() {
        trigger.update(isRefreshing = false, lastSyncedAt = before)
        trigger.update(isRefreshing = true, lastSyncedAt = before)
        assertFalse(trigger.update(isRefreshing = true, lastSyncedAt = FIXED_NOW))
        assertTrue(trigger.update(isRefreshing = false, lastSyncedAt = FIXED_NOW))
    }

    @Test
    fun `a failed refresh leaves the fetch time alone and does not play`() {
        trigger.update(isRefreshing = false, lastSyncedAt = before)
        trigger.update(isRefreshing = true, lastSyncedAt = before)
        assertFalse(trigger.update(isRefreshing = false, lastSyncedAt = before))
    }

    @Test
    fun `new data without a refresh, such as stored data loading, does not play`() {
        assertFalse(trigger.update(isRefreshing = false, lastSyncedAt = null))
        assertFalse(trigger.update(isRefreshing = false, lastSyncedAt = before))
    }

    @Test
    fun `the very first sync plays`() {
        trigger.update(isRefreshing = true, lastSyncedAt = null)
        assertTrue(trigger.update(isRefreshing = false, lastSyncedAt = FIXED_NOW))
    }

    @Test
    fun `a refresh that ends with nothing synced does not play`() {
        trigger.update(isRefreshing = true, lastSyncedAt = null)
        assertFalse(trigger.update(isRefreshing = false, lastSyncedAt = null))
    }
}
