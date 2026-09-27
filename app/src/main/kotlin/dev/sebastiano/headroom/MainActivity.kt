package dev.sebastiano.headroom

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.ui.HeadroomApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark or light system bar icons follow the theme, so the status bar stays readable.
        enableEdgeToEdge()
        val graph = (application as HeadroomApplication).graph
        setContent { HeadroomTheme { HeadroomApp(graph = graph) } }
        askForNotificationsOnceSignedIn(graph)
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
