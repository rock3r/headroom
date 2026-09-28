package dev.sebastiano.headroom.island

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IslandDecisionTest {
    private val clear =
        IslandConditions(
            enabled = true,
            serviceConnected = true,
            screenOn = true,
            landscape = false,
            appInForeground = false,
            doNotDisturb = false,
        )

    @Test
    fun `the island shows when everything allows it`() {
        assertNull(clear.blockedBy())
        assertEquals(true, clear.allowsIsland())
    }

    @Test
    fun `the setting being off blocks it`() {
        assertEquals(IslandBlock.SettingOff, clear.copy(enabled = false).blockedBy())
    }

    @Test
    fun `a service that is not connected blocks it`() {
        assertEquals(
            IslandBlock.ServiceNotConnected,
            clear.copy(serviceConnected = false).blockedBy(),
        )
    }

    @Test
    fun `a screen that is off blocks it`() {
        assertEquals(IslandBlock.ScreenOff, clear.copy(screenOn = false).blockedBy())
    }

    @Test
    fun `landscape blocks it`() {
        assertEquals(IslandBlock.Landscape, clear.copy(landscape = true).blockedBy())
    }

    @Test
    fun `Headroom in the foreground blocks it, because the confetti covers that case`() {
        assertEquals(IslandBlock.AppInForeground, clear.copy(appInForeground = true).blockedBy())
    }

    @Test
    fun `Do Not Disturb blocks it`() {
        assertEquals(IslandBlock.DoNotDisturb, clear.copy(doNotDisturb = true).blockedBy())
        assertEquals(false, clear.copy(doNotDisturb = true).allowsIsland())
    }

    @Test
    fun `with several reasons the setting is named first, then the service, then the screen`() {
        val everythingWrong =
            IslandConditions(
                enabled = false,
                serviceConnected = false,
                screenOn = false,
                landscape = true,
                appInForeground = true,
                doNotDisturb = true,
            )
        assertEquals(IslandBlock.SettingOff, everythingWrong.blockedBy())
        assertEquals(
            IslandBlock.ServiceNotConnected,
            everythingWrong.copy(enabled = true).blockedBy(),
        )
        assertEquals(
            IslandBlock.ScreenOff,
            everythingWrong.copy(enabled = true, serviceConnected = true).blockedBy(),
        )
    }
}
