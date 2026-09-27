package dev.sebastiano.headroom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.ui.HeadroomApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        val graph = (application as HeadroomApplication).graph
        setContent { HeadroomTheme { HeadroomApp(graph = graph) } }
    }
}
