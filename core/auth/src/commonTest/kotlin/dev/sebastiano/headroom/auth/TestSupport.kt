package dev.sebastiano.headroom.auth

import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant

/** An unsigned JWT with [claimsJson] as its payload. */
internal fun fakeJwt(claimsJson: String): String {
    val encoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    val header = encoder.encode("""{"alg":"none"}""".encodeToByteArray())
    return "$header.${encoder.encode(claimsJson.encodeToByteArray())}.sig"
}

/** A clock that always reads [at]. */
internal fun fixedClock(at: Instant): Clock =
    object : Clock {
        override fun now(): Instant = at
    }
