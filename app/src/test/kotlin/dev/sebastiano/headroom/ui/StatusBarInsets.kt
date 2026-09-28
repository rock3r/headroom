package dev.sebastiano.headroom.ui

import android.app.Activity
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Robolectric draws no status bar, so hand the content a status bar inset of [heightPx], as an
 * edge-to-edge window on a device gets. Call it on the main thread.
 */
fun Activity.giveStatusBar(heightPx: Int) {
    val insets =
        WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, heightPx, 0, 0))
            .build()
    ViewCompat.dispatchApplyWindowInsets(findViewById<ViewGroup>(android.R.id.content), insets)
}
