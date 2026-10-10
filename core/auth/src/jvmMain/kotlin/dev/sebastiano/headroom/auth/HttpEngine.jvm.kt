package dev.sebastiano.headroom.auth

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

internal actual val platformHttpEngine: HttpClientEngineFactory<*> = OkHttp
