package dev.sebastiano.headroom.shared

import dev.sebastiano.headroom.model.AskOutcome
import dev.sebastiano.headroom.model.QuotaErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ResetsMappingTest {
    @Test
    fun `a rejected reset-card sign-in remains distinct from transport failures`() {
        assertEquals(
            "signInAgain",
            ResetsMapping.answered(AskOutcome.Failed(QuotaErrorKind.Auth)).answer,
        )
        assertEquals(
            "failed",
            ResetsMapping.answered(AskOutcome.Failed(QuotaErrorKind.Network)).answer,
        )
    }
}
