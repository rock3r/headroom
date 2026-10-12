package dev.sebastiano.headroom.auth

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * The one place this module creates a [SecureRandom]. Lint's TrulyRandom check, about a seeding bug
 * on Android 4.3 and older, is ignored for this file in `lint.xml`.
 */
internal actual fun secureRandom(): Random = SecureRandom().asKotlinRandom()
