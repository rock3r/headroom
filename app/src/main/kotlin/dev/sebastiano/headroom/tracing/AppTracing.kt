package dev.sebastiano.headroom.tracing

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.tracing.Tracer

/**
 * The app's own trace sections, recorded with AndroidX Tracing 2. Debug builds install a real
 * tracer at start-up (`installAppTracing`, in the debug source set); release builds keep the
 * library's stub, which records nothing and costs almost nothing.
 */
object AppTracing {
    /** Every section Headroom records is in this category. */
    const val CATEGORY: String = "headroom"

    @Volatile
    var tracer: Tracer = Tracer.getStubTracer()
        internal set
}

/**
 * Runs [block] inside a trace section called [name]. Unlike `Tracer.trace`, the block is not
 * `crossinline`, so a composable can call other composables inside it.
 */
inline fun <T> traced(name: String, block: () -> T): T {
    val tracer = AppTracing.tracer
    if (!tracer.isCategoryEnabled(AppTracing.CATEGORY)) return block()
    val section = tracer.beginSection(AppTracing.CATEGORY, name, token = null) {}
    try {
        return block()
    } finally {
        section.close()
    }
}

/**
 * A lazy list item whose composition is recorded as a trace section called [name], so a slow frame
 * while scrolling shows which item was being composed.
 */
fun LazyListScope.tracedItem(
    name: String,
    key: Any? = null,
    content: @Composable LazyItemScope.() -> Unit,
) {
    item(key = key) { traced(name) { content() } }
}
