package dev.sebastiano.headroom.glasses

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.googlefonts.createGoogleSansFlexTypography
import dev.sebastiano.headroom.HeadroomApplication
import dev.sebastiano.headroom.ui.components.rememberResetFormatter
import dev.sebastiano.headroom.ui.home.HomeViewModel

/**
 * The activity the phone projects onto display glasses. It shows what the overview shows, one card
 * at a time, from the same view model. The manifest limits it to projected displays.
 */
class GlassesActivity : ComponentActivity() {
    private val graph
        get() = (application as HeadroomApplication).graph

    private val home: HomeViewModel by viewModels { graph.homeViewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by home.state.collectAsStateWithLifecycle()
            val formatter = rememberResetFormatter(graph.zone)
            val typography = remember { createGoogleSansFlexTypography() }
            GlimmerTheme(typography = typography) {
                GlassesScreen(state = state, formatter = formatter, onBack = ::finish)
            }
        }
    }
}
