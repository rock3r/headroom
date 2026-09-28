package dev.sebastiano.headroom.island

import kotlin.test.Test
import kotlin.test.assertEquals

class IslandAccessTest {
    @Test
    fun `the switch off reads as Off, whatever the service does`() {
        assertEquals(ResetIslandStatus.Off, resetIslandStatus(enabled = false, ready = false))
        assertEquals(ResetIslandStatus.Off, resetIslandStatus(enabled = false, ready = true))
    }

    @Test
    fun `the switch on without the service needs accessibility access`() {
        assertEquals(
            ResetIslandStatus.NeedsAccess,
            resetIslandStatus(enabled = true, ready = false),
        )
    }

    @Test
    fun `the switch on with the service connected is ready`() {
        assertEquals(ResetIslandStatus.Ready, resetIslandStatus(enabled = true, ready = true))
    }

    private val pkg = "dev.sebastiano.headroom"
    private val cls = "dev.sebastiano.headroom.island.ResetIslandService"

    private fun listed(services: String?, accessibilityOn: Boolean = true) =
        isServiceListed(services, accessibilityOn, pkg, cls)

    @Test
    fun `a service is enabled when it is in the list and accessibility is on`() {
        assertEquals(true, listed("$pkg/$cls"))
    }

    @Test
    fun `the short form of the name counts too`() {
        assertEquals(true, listed("$pkg/.island.ResetIslandService"))
    }

    @Test
    fun `the service is found among others`() {
        assertEquals(true, listed("com.other/com.other.Service:$pkg/$cls:com.third/.Thing"))
    }

    @Test
    fun `nothing listed means not enabled`() {
        assertEquals(false, listed(null))
        assertEquals(false, listed(""))
    }

    @Test
    fun `another service of another app does not count`() {
        assertEquals(false, listed("com.other/com.other.Service"))
        assertEquals(false, listed("com.other/$cls"))
    }

    @Test
    fun `another service of this app does not count`() {
        assertEquals(false, listed("$pkg/$pkg.island.OtherService"))
    }

    @Test
    fun `accessibility turned off as a whole means not enabled`() {
        assertEquals(false, listed("$pkg/$cls", accessibilityOn = false))
    }
}
