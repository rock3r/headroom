package dev.sebastiano.headroom.model

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsTest {
    @Test
    fun `used mode shows the used percentage as it is`() {
        assertEquals(71.0, QuotaDisplay.Used.percent(71.0))
    }

    @Test
    fun `left mode shows what is left, never below zero`() {
        assertEquals(29.0, QuotaDisplay.Left.percent(71.0))
        assertEquals(0.0, QuotaDisplay.Left.percent(104.0))
        assertEquals(100.0, QuotaDisplay.Left.percent(0.0))
    }

    @Test
    fun `sync frequencies run from the WorkManager minimum to six hours, or not at all`() {
        assertEquals(
            listOf(
                Duration.ofMinutes(15),
                Duration.ofMinutes(30),
                Duration.ofHours(1),
                Duration.ofHours(3),
                Duration.ofHours(6),
            ),
            SyncFrequency.entries.mapNotNull { it.period },
        )
        assertNull(SyncFrequency.OnOpen.period)
    }

    @Test
    fun `the defaults keep today's behaviour`() {
        assertEquals(QuotaDisplay.Used, AppSettings().quotaDisplay)
        assertEquals(SyncFrequency.Minutes15, AppSettings().syncFrequency)
    }

    @Test
    fun `the appearance follows the device until the user picks otherwise`() {
        assertEquals(ThemeMode.System, AppSettings().theme)
        assertEquals(MotionPreference.System, AppSettings().motion)
        assertEquals(ThemePalette.Wallpaper, AppSettings().palette)
    }

    @Test
    fun `there are eight fixed palettes besides the wallpaper`() {
        assertEquals(8, ThemePalette.entries.count { it != ThemePalette.Wallpaper })
    }

    @Test
    fun `the in-memory repository stores each setting`() =
        kotlinx.coroutines.test.runTest {
            val repository = InMemorySettingsRepository()
            repository.setQuotaDisplay(QuotaDisplay.Left)
            repository.setSyncFrequency(SyncFrequency.Hours3)
            repository.setTheme(ThemeMode.Dark)
            repository.setMotion(MotionPreference.Reduced)
            repository.setPalette(ThemePalette.Lagoon)
            assertEquals(
                AppSettings(
                    QuotaDisplay.Left,
                    SyncFrequency.Hours3,
                    theme = ThemeMode.Dark,
                    motion = MotionPreference.Reduced,
                    palette = ThemePalette.Lagoon,
                ),
                repository.settings.value,
            )
        }
}
