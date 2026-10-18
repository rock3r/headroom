package dev.sebastiano.headroom

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.quicksettings.TileService
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.sebastiano.headroom.data.ResetReminderIntents
import dev.sebastiano.headroom.data.SignInIntents
import dev.sebastiano.headroom.ui.AppearanceTheme
import dev.sebastiano.headroom.ui.HeadroomApp
import dev.sebastiano.headroom.ui.OpenAccountRequest
import dev.sebastiano.headroom.ui.ThemeRevealHost
import dev.sebastiano.headroom.ui.delights.DelightsHost
import dev.sebastiano.headroom.widget.WidgetIntents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /**
     * The account a widget tap, a sign-in warning or a reset reminder asked for, until the UI has
     * opened it.
     */
    private val openAccount = MutableStateFlow<OpenAccountRequest?>(null)
    /** A long press on the Quick Settings tile, until the UI has opened the tile's settings. */
    private val openTileSettings = MutableStateFlow<Long?>(null)
    private var requests = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge to edge; AppearanceTheme then makes the system bar icons follow the chosen theme.
        enableEdgeToEdge()
        val graph = (application as HeadroomApplication).graph
        // After a configuration change the launch intent is old news: the UI restores itself.
        if (savedInstanceState == null) handleRequest(intent)
        val settings = graph.settings.settings.stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        keepSplashUntil { settings.value != null }
        setContent {
            val request by openAccount.collectAsStateWithLifecycle()
            val tileSettingsRequest by openTileSettings.collectAsStateWithLifecycle()
            val appearance by settings.collectAsStateWithLifecycle()
            // Nothing is drawn until the stored appearance is read, so the first frame already
            // has the chosen theme and colours.
            appearance?.let { current ->
                // A new theme or palette is uncovered from where the user picked it.
                ThemeRevealHost(themeKey = current.theme to current.palette) {
                    AppearanceTheme(current) {
                        // The delights draw over the whole app, in the chosen theme's colours.
                        DelightsHost(
                            refreshShimmer = current.refreshShimmer,
                            resetConfetti = current.resetConfetti,
                        ) {
                            HeadroomApp(
                                graph = graph,
                                openAccountRequest = request,
                                onConsumeOpenAccount = { openAccount.value = null },
                                openTileSettingsRequest = tileSettingsRequest,
                                onConsumeOpenTileSettings = { openTileSettings.value = null },
                            )
                        }
                    }
                }
            }
        }
        askForNotificationsOnceSignedIn(graph)
    }

    /**
     * The activity is single-task: widget taps, sign-in warnings, reset reminders, long presses on
     * the Quick Settings tile and the sign-in return link arrive here. The return link carries
     * nothing to act on; bringing the task forward is all it needs, so whatever the accounts screen
     * shows (a finished sign-in, for example) stays on screen.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRequest(intent)
    }

    /** Keeps the splash screen up, by holding back the first frame, until [ready] is true. */
    private fun keepSplashUntil(ready: () -> Boolean) {
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!ready()) return false
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            }
        )
    }

    /**
     * Acts on [intent]. The newest request wins: a long press clears an account request, and back.
     */
    private fun handleRequest(intent: Intent?) {
        if (intent?.action == TileService.ACTION_QS_TILE_PREFERENCES) {
            openAccount.value = null
            openTileSettings.value = requests++
            return
        }
        val request = accountRequest(intent) ?: return
        openTileSettings.value = null
        openAccount.value = request
    }

    private fun accountRequest(intent: Intent?): OpenAccountRequest? {
        intent?.getStringExtra(SignInIntents.EXTRA_ACCOUNT_ID)?.let { accountId ->
            return OpenAccountRequest(accountId, requests++, signInAgain = true)
        }
        intent?.getStringExtra(ResetReminderIntents.EXTRA_ACCOUNT_ID)?.let { accountId ->
            return OpenAccountRequest(accountId, requests++, showResets = true)
        }
        val accountId = intent?.getStringExtra(WidgetIntents.EXTRA_ACCOUNT_ID) ?: return null
        // A widget tap on an account whose sign-in expired opens its sign-in.
        val signInAgain = intent.getBooleanExtra(WidgetIntents.EXTRA_SIGN_IN_AGAIN, false)
        return OpenAccountRequest(accountId, requests++, signInAgain)
    }

    /**
     * Reset alerts need the notification permission. Ask once, when the first real account exists,
     * rather than on a first launch that only shows demo data.
     */
    private fun askForNotificationsOnceSignedIn(graph: AppGraph) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val granted =
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (granted || prefs.getBoolean(KEY_ASKED, false)) return
        val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        lifecycleScope.launch {
            graph.isDemo.first { !it }
            prefs.edit { putBoolean(KEY_ASKED, true) }
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        const val PREFS = "main"
        const val KEY_ASKED = "asked_notifications"
    }
}
