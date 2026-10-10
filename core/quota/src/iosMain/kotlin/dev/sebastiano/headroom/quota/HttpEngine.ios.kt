package dev.sebastiano.headroom.quota

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual val platformHttpEngine: HttpClientEngineFactory<*> = Darwin
