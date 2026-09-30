package dev.sebastiano.headroom.data.reset

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResetRetryPolicyTest {
    @Test
    fun `retries after 30 seconds, then 2, 5, 15 and 60 minutes, then gives up`() {
        assertEquals(Duration.ofSeconds(30), ResetRetryPolicy.delayAfterAttempt(1))
        assertEquals(Duration.ofMinutes(2), ResetRetryPolicy.delayAfterAttempt(2))
        assertEquals(Duration.ofMinutes(5), ResetRetryPolicy.delayAfterAttempt(3))
        assertEquals(Duration.ofMinutes(15), ResetRetryPolicy.delayAfterAttempt(4))
        assertEquals(Duration.ofMinutes(60), ResetRetryPolicy.delayAfterAttempt(5))
        assertNull(ResetRetryPolicy.delayAfterAttempt(6))
    }
}
