package dev.sebastiano.headroom.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.sebastiano.headroom.auth.TokenSet
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.signin.DeviceSession
import dev.sebastiano.headroom.signin.ServiceSignIn
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Cursor sign-in's page opens once per ask, from the app itself, so that the tab belongs to the
 * app's task and returning to the app takes it away again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CursorSignInPageTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val signIn = signIn()

    /** Its own scope, as every other test graph gets, so the flow runs as soon as it starts. */
    private fun signIn(scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)) =
        ServiceSignIn(start = { Session }, save = { _, _ -> }, scope = scope, label = "Cursor")

    private fun show() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(graph = testGraph(rule.activity, cursorSignIn = signIn))
            }
        }
        rule.waitForIdle()
    }

    /** The page the app started, taken off the queue so the next one is a new tab. */
    private fun nextTab(): Intent? = shadowOf(rule.activity.application).nextStartedActivity

    @Test
    fun `the page opens when the sign-in waits, once`() {
        show()

        signIn.start(GROK)
        rule.waitForIdle()

        assertEquals(PAGE, nextTab()?.dataString)
        assertNull(nextTab())
    }

    @Test
    fun `asking again opens the page again`() {
        show()
        signIn.start(GROK)
        rule.waitForIdle()
        nextTab()

        // The user closed the tab; tapping Connect again gives them a new one.
        signIn.start(GROK)
        rule.waitForIdle()

        assertEquals(PAGE, nextTab()?.dataString)
    }

    /** A sign-in whose browser page the user has not finished yet. */
    private object Session : DeviceSession {
        override val userCode: String = ""
        override val verificationUrl: String = PAGE

        override suspend fun awaitTokens(): TokenSet = CompletableDeferred<TokenSet>().await()
    }

    private companion object {
        const val GROK = "grok-account"
        const val PAGE = "https://cursor.com/loginDeepControl?challenge=abc&uuid=def&mode=login"
    }
}
