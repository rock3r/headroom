package dev.sebastiano.headroom.data.account

import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateCredentialRequest
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreateRestoreCredentialRequest
import androidx.credentials.CreateRestoreCredentialResponse
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetRestoreCredentialOption
import androidx.credentials.PrepareGetCredentialResponse
import androidx.credentials.RestoreCredential
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.restorecredential.E2eeUnavailableException
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.auth.CredentialKind
import dev.sebastiano.headroom.auth.StoredCredential
import dev.sebastiano.headroom.model.Provider
import java.security.GeneralSecurityException
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestoreCredentialsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private class FakeCredentialManager : CredentialManager {
        val createRequests = mutableListOf<CreateRestoreCredentialRequest>()
        val getRequests = mutableListOf<GetCredentialRequest>()
        val clearRequests = mutableListOf<ClearCredentialStateRequest>()

        var failCloudBackupWithE2eeUnavailable = false
        var failGetWith: GetCredentialException? = null
        var storedUserHandle: String? = null

        override fun createCredentialAsync(
            context: Context,
            request: CreateCredentialRequest,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback:
                CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException>,
        ) {
            val restoreRequest = request as CreateRestoreCredentialRequest
            createRequests += restoreRequest
            if (restoreRequest.isCloudBackupEnabled && failCloudBackupWithE2eeUnavailable) {
                executor.execute { callback.onError(E2eeUnavailableException("E2EE unavailable")) }
                return
            }
            val json = Json.parseToJsonElement(restoreRequest.requestJson).jsonObject
            val user = json.getValue("user").jsonObject
            storedUserHandle = user.getValue("id").jsonPrimitive.content
            val responseJson =
                """
                {
                  "id": "cred-1",
                  "rawId": "cred-1",
                  "type": "public-key",
                  "response": {
                    "clientDataJSON": "Y2xpZW50",
                    "attestationObject": "YXR0ZXN0"
                  }
                }
                """
                    .trimIndent()
            executor.execute { callback.onResult(CreateRestoreCredentialResponse(responseJson)) }
        }

        override fun getCredentialAsync(
            context: Context,
            request: GetCredentialRequest,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>,
        ) {
            getRequests += request
            failGetWith?.let { error ->
                executor.execute { callback.onError(error) }
                return
            }
            val handle = storedUserHandle
            if (handle == null) {
                executor.execute {
                    callback.onError(NoCredentialException("No restore credential"))
                }
                return
            }
            val authResponseJson =
                """
                {
                  "id": "cred-1",
                  "rawId": "cred-1",
                  "type": "public-key",
                  "response": {
                    "clientDataJSON": "Y2xpZW50",
                    "authenticatorData": "YXV0aA",
                    "signature": "c2ln",
                    "userHandle": "$handle"
                  }
                }
                """
                    .trimIndent()
            val bundle =
                Bundle().apply {
                    putString(
                        "androidx.credentials.BUNDLE_KEY_GET_RESTORE_CREDENTIAL_RESPONSE",
                        authResponseJson,
                    )
                }
            executor.execute {
                callback.onResult(
                    GetCredentialResponse(
                        Credential.createFrom(RestoreCredential.TYPE_RESTORE_CREDENTIAL, bundle)
                    )
                )
            }
        }

        // AndroidX CredentialManager declares Void? in clearCredentialStateAsync's callback type.
        @Suppress("ForbiddenVoid")
        override fun clearCredentialStateAsync(
            request: ClearCredentialStateRequest,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<Void?, ClearCredentialException>,
        ) {
            clearRequests += request
            if (request.requestType == ClearCredentialStateRequest.TYPE_CLEAR_RESTORE_CREDENTIAL) {
                storedUserHandle = null
            }
            executor.execute { callback.onResult(null) }
        }

        override fun getCredentialAsync(
            context: Context,
            pendingGetCredentialHandle: PrepareGetCredentialResponse.PendingGetCredentialHandle,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>,
        ) = Unit

        override fun prepareGetCredentialAsync(
            request: GetCredentialRequest,
            cancellationSignal: CancellationSignal?,
            executor: Executor,
            callback:
                CredentialManagerCallback<
                    PrepareGetCredentialResponse,
                    GetCredentialException,
                >,
        ) = Unit

        override fun createSettingsPendingIntent(): PendingIntent =
            error("Not used by RestoreCredentials")
    }

    @Test
    fun `creates a restore credential on the source device and restores the keyset on the destination device`() =
        runTest {
            val credentialManager = FakeCredentialManager()
            val sourceKeysetPrefs = context.getSharedPreferences("src_keyset", Context.MODE_PRIVATE)
            val sharedRestorePrefs =
                context.getSharedPreferences("shared_restore", Context.MODE_PRIVATE)
            val sharedTokenPrefs =
                context.getSharedPreferences("shared_tokens", Context.MODE_PRIVATE)

            val sourceCipher =
                TinkCredentialCipher(
                    context,
                    keysetPrefs = sourceKeysetPrefs,
                    restorePrefs = sharedRestorePrefs,
                )
            val sourceStore = EncryptedTokenStore(context, sourceCipher, sharedTokenPrefs)
            sourceStore.save(
                StoredCredential(
                    Provider.Claude,
                    "acc-1",
                    CredentialKind.OAuth,
                    "claude-access",
                    "claude-refresh",
                    null,
                ),
                expectedRevision = null,
            )

            val sourceRestore =
                RestoreCredentials(
                    context = context,
                    cipher = sourceCipher,
                    credentialManager = credentialManager,
                    restorePrefs = sharedRestorePrefs,
                )
            sourceRestore.createRestoreCredential()
            // Calling a second time is a no-op once synced.
            sourceRestore.createRestoreCredential()

            assertEquals(1, credentialManager.createRequests.size)
            assertTrue(credentialManager.createRequests.single().isCloudBackupEnabled)
            assertTrue(
                sharedRestorePrefs.getBoolean(
                    RestoreCredentials.KEY_HAS_SYNCED_RESTORE_CREDENTIAL,
                    false,
                )
            )

            // Simulate migrating to a new phone: sharedRestorePrefs and sharedTokenPrefs are
            // restored by Android Backup, while destKeysetPrefs starts empty.
            val destKeysetPrefs = context.getSharedPreferences("dst_keyset", Context.MODE_PRIVATE)
            val destCipher =
                TinkCredentialCipher(
                    context,
                    keysetPrefs = destKeysetPrefs,
                    restorePrefs = sharedRestorePrefs,
                )
            val destStore = EncryptedTokenStore(context, destCipher, sharedTokenPrefs)
            val destRestore =
                RestoreCredentials(
                    context = context,
                    cipher = destCipher,
                    credentialManager = credentialManager,
                    restorePrefs = sharedRestorePrefs,
                )

            assertNull(destStore.load("acc-1"))

            // Tier 1 (BackupAgent) restores the keyset.
            assertTrue(destRestore.restore())
            assertEquals(1, credentialManager.getRequests.size)
            val option =
                credentialManager.getRequests.single().credentialOptions.single()
                    as GetRestoreCredentialOption
            assertTrue(option.requestJson.contains("headroom.sebastiano.dev"))

            val restored = assertNotNull(destStore.load("acc-1"))
            assertEquals("claude-access", restored.accessToken)
            assertEquals("claude-refresh", restored.refreshToken)

            // Tier 2 (MainActivity) sees Tier 1 already succeeded and does not call getCredential
            // again.
            assertFalse(destRestore.restore())
            assertEquals(1, credentialManager.getRequests.size)
        }

    @Test
    fun `falls back to isCloudBackupEnabled false when E2eeUnavailableException is thrown`() =
        runTest {
            val credentialManager =
                FakeCredentialManager().apply { failCloudBackupWithE2eeUnavailable = true }
            val keysetPrefs = context.getSharedPreferences("e2ee_keyset", Context.MODE_PRIVATE)
            val restorePrefs = context.getSharedPreferences("e2ee_restore", Context.MODE_PRIVATE)
            val cipher =
                TinkCredentialCipher(
                    context,
                    keysetPrefs = keysetPrefs,
                    restorePrefs = restorePrefs,
                )
            val restore =
                RestoreCredentials(
                    context = context,
                    cipher = cipher,
                    credentialManager = credentialManager,
                    restorePrefs = restorePrefs,
                )

            restore.createRestoreCredential()

            assertEquals(2, credentialManager.createRequests.size)
            assertTrue(credentialManager.createRequests[0].isCloudBackupEnabled)
            assertFalse(credentialManager.createRequests[1].isCloudBackupEnabled)
            assertTrue(
                restorePrefs.getBoolean(
                    RestoreCredentials.KEY_HAS_SYNCED_RESTORE_CREDENTIAL,
                    false,
                )
            )
        }

    @Test
    fun `tier 2 retries when tier 1 failed during backup restore`() = runTest {
        val credentialManager = FakeCredentialManager()
        val sourceKeysetPrefs = context.getSharedPreferences("t2_src_keyset", Context.MODE_PRIVATE)
        val sharedRestorePrefs = context.getSharedPreferences("t2_restore", Context.MODE_PRIVATE)
        val sharedTokenPrefs = context.getSharedPreferences("t2_tokens", Context.MODE_PRIVATE)

        val sourceCipher =
            TinkCredentialCipher(
                context,
                keysetPrefs = sourceKeysetPrefs,
                restorePrefs = sharedRestorePrefs,
            )
        EncryptedTokenStore(context, sourceCipher, sharedTokenPrefs)
            .save(
                StoredCredential(
                    Provider.Codex,
                    "acc-2",
                    CredentialKind.OAuth,
                    "codex-access",
                    "codex-refresh",
                    null,
                ),
                expectedRevision = null,
            )
        RestoreCredentials(context, sourceCipher, credentialManager, sharedRestorePrefs)
            .createRestoreCredential()

        val destKeysetPrefs = context.getSharedPreferences("t2_dst_keyset", Context.MODE_PRIVATE)
        val destCipher =
            TinkCredentialCipher(
                context,
                keysetPrefs = destKeysetPrefs,
                restorePrefs = sharedRestorePrefs,
            )
        val destStore = EncryptedTokenStore(context, destCipher, sharedTokenPrefs)
        val destRestore =
            RestoreCredentials(context, destCipher, credentialManager, sharedRestorePrefs)

        // Tier 1 fails with a transient Play Services error.
        credentialManager.failGetWith = GetCredentialUnknownException("Play Services busy")
        assertFalse(destRestore.restore())
        assertNull(destStore.load("acc-2"))

        // While the wrapped keyset is still waiting to be restored, back-filling must not generate
        // a new local keyset or overwrite the backed-up wrapped keyset.
        destRestore.createRestoreCredential()
        assertEquals(1, credentialManager.createRequests.size)
        assertFalse(destCipher.hasLocalKeyset())
        assertFailsWith<GeneralSecurityException> { destCipher.wrapKeyset(ByteArray(32)) }
        assertFalse(destCipher.hasLocalKeyset())

        // Tier 2 runs on MainActivity launch and succeeds.
        credentialManager.failGetWith = null
        assertTrue(destRestore.restore())
        assertEquals("codex-access", destStore.load("acc-2")?.accessToken)
    }

    @Test
    fun `reauthenticating when restore credential is unavailable recovers by generating a new keyset and syncing it`() =
        runTest {
            val credentialManager = FakeCredentialManager()
            val sourceKeysetPrefs =
                context.getSharedPreferences("rec_src_keyset", Context.MODE_PRIVATE)
            val sharedRestorePrefs =
                context.getSharedPreferences("rec_restore", Context.MODE_PRIVATE)
            val sharedTokenPrefs = context.getSharedPreferences("rec_tokens", Context.MODE_PRIVATE)

            val sourceCipher =
                TinkCredentialCipher(
                    context,
                    keysetPrefs = sourceKeysetPrefs,
                    restorePrefs = sharedRestorePrefs,
                )
            EncryptedTokenStore(context, sourceCipher, sharedTokenPrefs)
                .save(
                    StoredCredential(
                        Provider.Claude,
                        "acc-1",
                        CredentialKind.OAuth,
                        "old-access",
                        "old-refresh",
                        null,
                    ),
                    expectedRevision = null,
                )
            RestoreCredentials(context, sourceCipher, credentialManager, sharedRestorePrefs)
                .createRestoreCredential()

            // Simulate a destination device where the wrapped keyset was restored by Backup, but
            // CredentialManager has no restore credential (NoCredentialException).
            credentialManager.storedUserHandle = null
            val destKeysetPrefs =
                context.getSharedPreferences("rec_dst_keyset", Context.MODE_PRIVATE)
            val destCipher =
                TinkCredentialCipher(
                    context,
                    keysetPrefs = destKeysetPrefs,
                    restorePrefs = sharedRestorePrefs,
                )
            val destStore = EncryptedTokenStore(context, destCipher, sharedTokenPrefs)
            val destRestore =
                RestoreCredentials(context, destCipher, credentialManager, sharedRestorePrefs)

            assertFalse(destRestore.restore())
            assertNull(destStore.load("acc-1"))
            assertFalse(destCipher.hasLocalKeyset())

            // Reauthenticating the restored account saves new tokens, generates a new local keyset,
            // and allows createRestoreCredential() to sync the new keyset.
            val saved =
                destStore.save(
                    StoredCredential(
                        Provider.Claude,
                        "acc-1",
                        CredentialKind.OAuth,
                        "new-access",
                        "new-refresh",
                        null,
                    ),
                    expectedRevision = null,
                )
            assertNotNull(saved)
            assertTrue(destCipher.hasLocalKeyset())
            assertEquals("new-access", destStore.load("acc-1")?.accessToken)

            destRestore.createRestoreCredential()
            assertEquals(2, credentialManager.createRequests.size)
            assertNotNull(credentialManager.storedUserHandle)
        }

    @Test
    fun `clearing restore credentials calls clearCredentialState with TYPE_CLEAR_RESTORE_CREDENTIAL`() =
        runTest {
            val credentialManager = FakeCredentialManager()
            val keysetPrefs = context.getSharedPreferences("clr_keyset", Context.MODE_PRIVATE)
            val restorePrefs = context.getSharedPreferences("clr_restore", Context.MODE_PRIVATE)
            val cipher =
                TinkCredentialCipher(
                    context,
                    keysetPrefs = keysetPrefs,
                    restorePrefs = restorePrefs,
                )
            val restore = RestoreCredentials(context, cipher, credentialManager, restorePrefs)

            restore.createRestoreCredential()
            assertTrue(
                restorePrefs.getBoolean(
                    RestoreCredentials.KEY_HAS_SYNCED_RESTORE_CREDENTIAL,
                    false,
                )
            )

            restore.clearRestoreCredential()

            assertEquals(1, credentialManager.clearRequests.size)
            assertEquals(
                ClearCredentialStateRequest.TYPE_CLEAR_RESTORE_CREDENTIAL,
                credentialManager.clearRequests.single().requestType,
            )
            assertFalse(
                restorePrefs.getBoolean(
                    RestoreCredentials.KEY_HAS_SYNCED_RESTORE_CREDENTIAL,
                    false,
                )
            )
            assertFalse(restorePrefs.contains(RestoreCredentials.KEY_WRAPPED_KEYSET))
        }
}
