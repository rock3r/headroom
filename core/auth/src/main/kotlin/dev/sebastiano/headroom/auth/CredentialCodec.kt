package dev.sebastiano.headroom.auth

import dev.sebastiano.headroom.model.Provider
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Turns a [StoredCredential] into JSON and back, for token stores that persist bytes. */
public object CredentialCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    public fun encode(credential: StoredCredential): String =
        json.encodeToString<Surrogate>(
            Surrogate(
                provider = credential.provider.id,
                accountId = credential.accountId,
                kind = credential.kind,
                accessToken = credential.accessToken,
                refreshToken = credential.refreshToken,
                expiresAtMillis = credential.expiresAt?.toEpochMilli(),
                providerAccountId = credential.providerAccountId,
                label = credential.label,
                extras = credential.extras,
                revision = credential.revision,
            )
        )

    /** @throws AuthException.InvalidResponse when [text] is not a saved credential. */
    public fun decode(text: String): StoredCredential {
        val surrogate =
            try {
                json.decodeFromString<Surrogate>(text)
            } catch (e: SerializationException) {
                throw AuthException.InvalidResponse("Saved credential is unreadable", e)
            } catch (e: IllegalArgumentException) {
                throw AuthException.InvalidResponse("Saved credential is unreadable", e)
            }
        val provider =
            Provider.fromId(surrogate.provider)
                ?: throw AuthException.InvalidResponse("Unknown provider ${surrogate.provider}")
        return StoredCredential(
            provider = provider,
            accountId = surrogate.accountId,
            kind = surrogate.kind,
            accessToken = surrogate.accessToken,
            refreshToken = surrogate.refreshToken,
            expiresAt = surrogate.expiresAtMillis?.let(Instant::ofEpochMilli),
            providerAccountId = surrogate.providerAccountId,
            label = surrogate.label,
            extras = surrogate.extras,
            revision = surrogate.revision,
        )
    }

    @Serializable
    private data class Surrogate(
        val provider: String,
        val accountId: String,
        val kind: CredentialKind,
        val accessToken: String,
        val refreshToken: String? = null,
        val expiresAtMillis: Long? = null,
        val providerAccountId: String? = null,
        val label: String? = null,
        val extras: Map<String, String> = emptyMap(),
        val revision: Long = 0,
    )
}
