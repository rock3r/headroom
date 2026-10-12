package dev.sebastiano.headroom.data.reset

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ResetRetryPolicyTest {
    @Test
    fun `retries after 30 seconds, then 2, 5, 15 and 60 minutes, then gives up`() {
        assertEquals(30.seconds, ResetRetryPolicy.delayAfterAttempt(1))
        assertEquals(2.minutes, ResetRetryPolicy.delayAfterAttempt(2))
        assertEquals(5.minutes, ResetRetryPolicy.delayAfterAttempt(3))
        assertEquals(15.minutes, ResetRetryPolicy.delayAfterAttempt(4))
        assertEquals(60.minutes, ResetRetryPolicy.delayAfterAttempt(5))
        assertNull(ResetRetryPolicy.delayAfterAttempt(6))
    }
}
