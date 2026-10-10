package dev.sebastiano.headroom.glasses

import android.content.Context
import android.content.Intent
import androidx.xr.projected.ProjectedContext
import androidx.xr.projected.experimental.ExperimentalProjectedApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Whether display glasses are connected, and a way to open Headroom on them. */
interface GlassesLauncher {
    /** True while display glasses are connected to the phone. */
    val connected: Flow<Boolean>

    /** Starts [GlassesActivity] on the glasses' display. */
    fun open()

    companion object {
        /** For tests and previews: never connected. */
        val Unavailable: GlassesLauncher =
            object : GlassesLauncher {
                override val connected: Flow<Boolean> = flowOf(false)

                override fun open() = Unit
            }
    }
}

/**
 * Finds the glasses and their display through [ProjectedContext]. Its connection and launch calls
 * are still experimental in the projected library.
 */
@OptIn(ExperimentalProjectedApi::class)
class ProjectedGlassesLauncher(
    private val context: Context,
    /** Where the connection is observed. */
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : GlassesLauncher {
    override val connected: Flow<Boolean> =
        ProjectedContext.isProjectedDeviceConnected(context, dispatcher)

    override fun open() {
        // The glasses can disconnect between the button showing and the tap: then there is no
        // projected display, and nothing to open.
        val options =
            try {
                ProjectedContext.createProjectedActivityOptions(context)
            } catch (_: IllegalStateException) {
                return
            }
        val intent =
            Intent(context, GlassesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent, options.toBundle())
    }
}
