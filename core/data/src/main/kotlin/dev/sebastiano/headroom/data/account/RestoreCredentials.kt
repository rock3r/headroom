package dev.sebastiano.headroom.data.account

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateRestoreCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetRestoreCredentialOption
import androidx.credentials.RestoreCredential
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.restorecredential.CreateRestoreCredentialDomException
import androidx.credentials.exceptions.restorecredential.E2eeUnavailableException
import java.security.GeneralSecurityException
import java.security.SecureRandom
import kotlin.io.encoding.Base64 as KotlinBase64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Manages Android's Restore Credentials (`androidx.credentials`) so migrating to a new phone keeps
 * signed-in accounts working without a backend server.
 *
 * When a restore credential is created, a random 256-bit wrapping key is placed in the WebAuthn
 * `user.id` field and used to wrap [TinkCredentialCipher]'s keyset into [restorePrefs]. Play
 * Services transfers the restore credential via end-to-end encrypted cloud backup or
 * device-to-device transfer. On the new phone, [restore] fetches the [RestoreCredential], reads the
 * 256-bit key back from `response.userHandle`, unwraps the backed-up keyset, and re-wraps it with
 * the new phone's Android Keystore key.
 */
public class RestoreCredentials(
    context: Context,
    private val cipher: TinkCredentialCipher,
    private val credentialManager: CredentialManager =
        CredentialManager.create(context.applicationContext),
    private val restorePrefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE),
    private val randomBytes: (Int) -> ByteArray = ::generateRandomBytes,
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    /**
     * Creates a restore credential if one is not already synced for the current keyset. Always
     * tries `isCloudBackupEnabled = true` first and falls back to `false` when
     * [E2eeUnavailableException] is thrown.
     */
    public suspend fun createRestoreCredential(): Unit = mutex.withLock {
        if (isSynced() || cipher.hasPendingRestoreState()) return@withLock
        val restoreKey = randomBytes(TinkCredentialCipher.RESTORE_KEY_BYTES)
        val challenge = randomBytes(CHALLENGE_BYTES)
        val wrappedKeyset =
            try {
                cipher.wrapKeyset(restoreKey)
            } catch (e: GeneralSecurityException) {
                Log.w(TAG, "Could not wrap credential keyset for restore", e)
                return@withLock
            }
        val requestJson = buildCreationOptionsJson(restoreKey = restoreKey, challenge = challenge)
        val created = createWithCloudFallback(requestJson)
        if (!created) return@withLock
        restorePrefs.edit(commit = true) {
            putString(KEY_WRAPPED_KEYSET, Base64.encodeToString(wrappedKeyset, Base64.NO_WRAP))
            putBoolean(KEY_HAS_SYNCED_RESTORE_CREDENTIAL, true)
        }
    }

    /**
     * Retrieves the restore credential on a new device and unlocks the backed-up Tink keyset.
     * Returns `true` when the keyset was restored on this call.
     */
    public suspend fun restore(): Boolean = mutex.withLock {
        if (cipher.hasLocalKeyset()) return@withLock false
        val wrappedEncoded =
            restorePrefs.getString(KEY_WRAPPED_KEYSET, null) ?: return@withLock false
        val authenticationJson = buildRequestOptionsJson(challenge = randomBytes(CHALLENGE_BYTES))
        val option = GetRestoreCredentialOption(authenticationJson)
        val getRequest = GetCredentialRequest(listOf(option))
        val response =
            try {
                credentialManager.getCredential(appContext, getRequest)
            } catch (_: NoCredentialException) {
                return@withLock false
            } catch (e: GetCredentialException) {
                Log.w(TAG, "Could not retrieve restore credential", e)
                return@withLock false
            }
        val credential = response.credential as? RestoreCredential ?: return@withLock false
        val restoreKey =
            extractUserHandle(credential.authenticationResponseJson) ?: return@withLock false
        return@withLock try {
            val wrappedKeyset = Base64.decode(wrappedEncoded, Base64.NO_WRAP)
            cipher.restoreKeyset(wrappedKeyset, restoreKey)
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "Could not unwrap restored credential keyset", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid restored credential keyset encoding", e)
            false
        }
    }

    /**
     * Deletes the restore key from Credential Manager and clears the wrapped keyset on sign-out.
     */
    public suspend fun clearRestoreCredential(): Unit = mutex.withLock {
        restorePrefs.edit(commit = true) {
            remove(KEY_WRAPPED_KEYSET)
            putBoolean(KEY_HAS_SYNCED_RESTORE_CREDENTIAL, false)
        }
        try {
            val clearRequest =
                ClearCredentialStateRequest(
                    ClearCredentialStateRequest.TYPE_CLEAR_RESTORE_CREDENTIAL
                )
            credentialManager.clearCredentialState(clearRequest)
        } catch (e: ClearCredentialException) {
            Log.w(TAG, "Could not clear restore credential state", e)
        }
    }

    private fun isSynced(): Boolean =
        cipher.hasLocalKeyset() &&
            restorePrefs.getBoolean(KEY_HAS_SYNCED_RESTORE_CREDENTIAL, false) &&
            restorePrefs.contains(KEY_WRAPPED_KEYSET)

    private suspend fun createWithCloudFallback(requestJson: String): Boolean =
        try {
            credentialManager.createCredential(
                appContext,
                CreateRestoreCredentialRequest(
                    requestJson = requestJson,
                    isCloudBackupEnabled = true,
                ),
            )
            true
        } catch (_: E2eeUnavailableException) {
            try {
                credentialManager.createCredential(
                    appContext,
                    CreateRestoreCredentialRequest(
                        requestJson = requestJson,
                        isCloudBackupEnabled = false,
                    ),
                )
                true
            } catch (e: CreateCredentialException) {
                Log.w(TAG, "Could not create local-only restore credential", e)
                false
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid restore credential request", e)
                false
            }
        } catch (e: CreateRestoreCredentialDomException) {
            Log.w(TAG, "Restore credential request JSON rejected", e)
            false
        } catch (e: CreateCredentialException) {
            Log.w(TAG, "Could not create restore credential", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid restore credential request", e)
            false
        }

    private fun extractUserHandle(authenticationResponseJson: String): ByteArray? =
        try {
            val root = Json.parseToJsonElement(authenticationResponseJson).jsonObject
            val response = root["response"]?.jsonObject ?: return null
            val userHandle = response["userHandle"]?.jsonPrimitive?.content ?: return null
            base64UrlDecode(userHandle)
        } catch (e: SerializationException) {
            Log.w(TAG, "Could not parse RestoreCredential authenticationResponseJson", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Could not decode RestoreCredential userHandle", e)
            null
        }

    public companion object {
        public const val FILE: String = "credentials_restore"
        public const val KEY_WRAPPED_KEYSET: String = "wrapped_keyset"
        public const val KEY_HAS_SYNCED_RESTORE_CREDENTIAL: String = "has_synced_restore_credential"

        private const val TAG = "HeadroomRestore"
        private const val RP_ID = "headroom.sebastiano.dev"
        private const val RP_NAME = "Headroom"
        private const val USER_NAME = "headroom-restore"
        private const val USER_DISPLAY_NAME = "Headroom Restore Key"
        private const val CHALLENGE_BYTES = 32
        private const val TIMEOUT_MS = 60_000
        private const val ALG_ES256 = -7
        private const val ALG_RS256 = -257

        private val base64UrlEncoder =
            KotlinBase64.UrlSafe.withPadding(KotlinBase64.PaddingOption.ABSENT)
        private val base64UrlDecoder =
            KotlinBase64.UrlSafe.withPadding(KotlinBase64.PaddingOption.ABSENT_OPTIONAL)

        private fun base64UrlEncode(bytes: ByteArray): String = base64UrlEncoder.encode(bytes)

        private fun base64UrlDecode(encoded: String): ByteArray = base64UrlDecoder.decode(encoded)

        private fun generateRandomBytes(size: Int): ByteArray =
            ByteArray(size).also(SecureRandom()::nextBytes)

        internal fun buildCreationOptionsJson(
            restoreKey: ByteArray,
            challenge: ByteArray,
        ): String = buildJsonObject {
            putJsonObject("rp") {
                put("id", RP_ID)
                put("name", RP_NAME)
            }
            putJsonObject("user") {
                put("id", base64UrlEncode(restoreKey))
                put("name", USER_NAME)
                put("displayName", USER_DISPLAY_NAME)
            }
            put("challenge", base64UrlEncode(challenge))
            put(
                "pubKeyCredParams",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "public-key")
                            put("alg", ALG_ES256)
                        }
                    )
                    add(
                        buildJsonObject {
                            put("type", "public-key")
                            put("alg", ALG_RS256)
                        }
                    )
                },
            )
            put("timeout", TIMEOUT_MS)
            put("attestation", "none")
            put("excludeCredentials", buildJsonArray {})
            putJsonObject("authenticatorSelection") {
                put("authenticatorAttachment", "platform")
                put("residentKey", "required")
                put("requireResidentKey", true)
                put("userVerification", "discouraged")
            }
        }
            .toString()

        internal fun buildRequestOptionsJson(challenge: ByteArray): String = buildJsonObject {
            put("challenge", base64UrlEncode(challenge))
            put("timeout", TIMEOUT_MS)
            put("rpId", RP_ID)
            put("allowCredentials", buildJsonArray {})
            put("userVerification", "discouraged")
        }
            .toString()
    }
}
