package dev.sebastiano.headroom.tracing

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.tracing.wire.TraceDriver
import androidx.tracing.wire.TraceSink

/**
 * Records Headroom's trace sections in-process, with AndroidX Tracing 2. Sections are only recorded
 * while a system trace (Perfetto) is running, so this costs next to nothing otherwise. The packets
 * go to `no_backup/perfetto_traces/` in the app's data folder; merge a file from there into a
 * system trace to see the app's sections next to the frames. See docs/TRACING.md.
 */
internal fun Application.installAppTracing() {
    val driver = TraceDriver(context = this, sink = TraceSink(context = this))
    AppTracing.tracer = driver.tracer
    // The sink buffers packets. Write them out whenever an activity stops, so a trace pulled
    // after pressing Home has everything up to that moment.
    registerActivityLifecycleCallbacks(FlushOnStop(driver))
}

private class FlushOnStop(private val driver: TraceDriver) :
    Application.ActivityLifecycleCallbacks {
    override fun onActivityStopped(activity: Activity) = driver.flush()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
