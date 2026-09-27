package dev.sebastiano.headroom

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.ui.HeadroomApp
import dev.sebastiano.headroom.ui.OpenAccountRequest
import dev.sebastiano.headroom.widget.WidgetIntents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** The account a widget tap asked for, until the UI has opened it. */
    private val openAccount = MutableStateFlow<OpenAccountRequest?>(null)
    private var requests = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark or light system bar icons follow the theme, so the status bar stays readable.
        enableEdgeToEdge()
        val graph = (application as HeadroomApplication).graph
        // After a configuration change the launch intent is old news: the UI restores itself.
        if (savedInstanceState == null) handleWidgetTap(intent)
        setContent {
            val request by openAccount.collectAsStateWithLifecycle()
            HeadroomTheme {
                HeadroomApp(
                    graph = graph,
                    openAccountRequest = request,
                    onConsumeOpenAccount = { openAccount.value = null },
                )
            }
        }
        askForNotificationsOnceSignedIn(graph)
    }

    /**
     * The activity is single-task: widget taps and the sign-in return link arrive here. The return
     * link carries nothing to act on; bringing the task forward is all it needs, so whatever the
     * accounts screen shows (a finished sign-in, for example) stays on screen.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetTap(intent)
    }

    private fun handleWidgetTap(intent: Intent?) {
        val accountId = intent?.getStringExtra(WidgetIntents.EXTRA_ACCOUNT_ID) ?: return
        openAccount.value = OpenAccountRequest(accountId, requests++)
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
