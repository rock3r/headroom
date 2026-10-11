package dev.sebastiano.headroom.quota

import kotlin.time.Clock
import kotlin.time.Instant

internal val FIXED_NOW: Instant = Instant.parse("2026-04-03T12:00:00Z")
internal val FIXED_CLOCK: Clock = fixedClock(FIXED_NOW)

/** A clock that always reads [at]. */
internal fun fixedClock(at: Instant): Clock =
    object : Clock {
        override fun now(): Instant = at
    }

/** An HTTP client that answers every request the same way and records what it was sent. */
internal class FakeQuotaHttpClient(private val respond: (QuotaHttpRequest) -> QuotaHttpResponse) :
    QuotaHttpClient {
    val requests = mutableListOf<QuotaHttpRequest>()

    override suspend fun execute(request: QuotaHttpRequest): QuotaHttpResponse {
        requests += request
        return respond(request)
    }
}

/** An [AttemptTargetStore] that outlives the reset clients that write to it, as a file does. */
internal class MemoryAttemptTargetStore : AttemptTargetStore {
    var saved: Map<String, String> = emptyMap()
        private set

    override fun load(): Map<String, String> = saved

    override fun save(targets: Map<String, String>) {
        saved = targets.toMap()
    }
}
