package dev.sebastiano.headroom.island

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IslandDecisionTest {
    private val clear =
        IslandConditions(
            enabled = true,
            mode = IslandMode.Accessibility,
            screenOn = true,
            landscape = false,
            appInForeground = false,
            doNotDisturb = false,
            keyguardLocked = false,
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
    fun `having no way to draw blocks it`() {
        assertEquals(IslandBlock.NoWindow, clear.copy(mode = IslandMode.None).blockedBy())
    }

    @Test
    fun `the service being connected is the best mode`() {
        assertEquals(
            IslandMode.Accessibility,
            resolveIslandMode(serviceConnected = true, canDrawOverlays = true),
        )
        assertEquals(
            IslandMode.Accessibility,
            resolveIslandMode(serviceConnected = true, canDrawOverlays = false),
        )
    }

    @Test
    fun `without the service, display over other apps is the fallback mode`() {
        assertEquals(
            IslandMode.Overlay,
            resolveIslandMode(serviceConnected = false, canDrawOverlays = true),
        )
    }

    @Test
    fun `with neither, there is no mode and the heads-up notification is used`() {
        assertEquals(
            IslandMode.None,
            resolveIslandMode(serviceConnected = false, canDrawOverlays = false),
        )
    }

    @Test
    fun `a locked device blocks the overlay, which never shows on the lock screen`() {
        val overlay = clear.copy(mode = IslandMode.Overlay)
        assertNull(overlay.blockedBy())
        assertEquals(IslandBlock.DeviceLocked, overlay.copy(keyguardLocked = true).blockedBy())
        assertEquals(false, overlay.copy(keyguardLocked = true).allowsIsland())
    }

    @Test
    fun `a locked device does not block the accessibility island, which shows on the lock screen`() {
        assertNull(clear.copy(keyguardLocked = true).blockedBy())
    }

    @Test
    fun `the overlay mode keeps every other suppression`() {
        val overlay = clear.copy(mode = IslandMode.Overlay)
        assertEquals(IslandBlock.SettingOff, overlay.copy(enabled = false).blockedBy())
        assertEquals(IslandBlock.ScreenOff, overlay.copy(screenOn = false).blockedBy())
        assertEquals(IslandBlock.Landscape, overlay.copy(landscape = true).blockedBy())
        assertEquals(IslandBlock.AppInForeground, overlay.copy(appInForeground = true).blockedBy())
        assertEquals(IslandBlock.DoNotDisturb, overlay.copy(doNotDisturb = true).blockedBy())
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
                mode = IslandMode.None,
                screenOn = false,
                landscape = true,
                appInForeground = true,
                doNotDisturb = true,
                keyguardLocked = true,
            )
        assertEquals(IslandBlock.SettingOff, everythingWrong.blockedBy())
        assertEquals(IslandBlock.NoWindow, everythingWrong.copy(enabled = true).blockedBy())
        assertEquals(
            IslandBlock.ScreenOff,
            everythingWrong.copy(enabled = true, mode = IslandMode.Overlay).blockedBy(),
        )
    }
}
