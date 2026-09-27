package dev.sebastiano.headroom.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.widget.testing.RecordingHostApplication
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = RecordingHostApplication::class)
class WidgetUpdaterTest {
    private val context = RuntimeEnvironment.getApplication()
    private val now = Instant.parse("2026-09-27T12:00:00Z")
    private val accounts = DemoData.accounts(now)
    private val store = InMemoryWidgetConfigStore()
    private val gateway = FakeGateway()
    private val rendered = mutableMapOf<Int, Pair<WidgetUiState, WidgetSize>>()
    private val updater =
        WidgetUpdater(store, { gateway }) { _, state, appWidgetId, size ->
            rendered[appWidgetId] = state to size
            RemoteViews(context.packageName, android.R.layout.simple_list_item_1)
        }

    @Test
    fun `updates every widget of every style`() = runTest {
        gateway.ids[RingsWidgetProvider::class.java] = intArrayOf(1, 2)
        gateway.ids[BarsWidgetProvider::class.java] = intArrayOf(3)
        gateway.ids[ShapeWidgetProvider::class.java] = intArrayOf(4)
        gateway.ids[CountdownWidgetProvider::class.java] = intArrayOf(5)

        updater.updateAll(context, accounts, now)

        assertEquals(setOf(1, 2, 3, 4, 5), gateway.updated.keys)
        assertIs<WidgetUiState.RingGrid>(rendered.getValue(1).first)
        assertIs<WidgetUiState.Bars>(rendered.getValue(3).first)
        assertIs<WidgetUiState.ShapeGrid>(rendered.getValue(4).first)
        assertIs<WidgetUiState.Countdown>(rendered.getValue(5).first)
    }

    @Test
    fun `uses the stored config of each widget`() = runTest {
        gateway.ids[RingsWidgetProvider::class.java] = intArrayOf(1, 2)
        store.set(2, WidgetConfig(WidgetStyle.Rings, accountIds = listOf("demo-codex")))

        updater.updateAll(context, accounts, now)

        assertIs<WidgetUiState.RingGrid>(rendered.getValue(1).first)
        val single = assertIs<WidgetUiState.SingleRing>(rendered.getValue(2).first)
        assertEquals("demo-codex", single.gauge.accountId)
    }

    @Test
    fun `reads the size from the widget options`() = runTest {
        gateway.ids[BarsWidgetProvider::class.java] = intArrayOf(3, 4, 5)
        gateway.options[3] =
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
            }
        gateway.options[4] =
            Bundle().apply {
                putParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(SizeF(320f, 60f), SizeF(280f, 90f)),
                )
            }

        updater.updateAll(context, accounts, now)

        assertEquals(WidgetSize(300f, 110f), rendered.getValue(3).second)
        assertEquals(3, assertIs<WidgetUiState.Bars>(rendered.getValue(3).first).gauges.size)
        // With several sizes, use the smallest width and height so nothing overflows.
        assertEquals(WidgetSize(280f, 60f), rendered.getValue(4).second)
        // Without options, the style's default size.
        assertEquals(
            HeadroomWidgetProvider.defaultSize(WidgetStyle.Bars),
            rendered.getValue(5).second,
        )
    }

    @Test
    fun `lock screen widgets get the compact layout`() = runTest {
        gateway.ids[ShapeWidgetProvider::class.java] = intArrayOf(9)
        gateway.options[9] =
            Bundle().apply {
                putInt(
                    AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                    AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD,
                )
            }

        updater.updateAll(context, accounts, now)

        assertIs<WidgetUiState.LockScreen>(rendered.getValue(9).first)
    }

    @Test
    fun `publishes a generated preview per style from demo data`() = runTest {
        updater.publishPreviews(context, now)

        assertEquals<Set<Class<*>>>(
            setOf(
                RingsWidgetProvider::class.java,
                BarsWidgetProvider::class.java,
                ShapeWidgetProvider::class.java,
                CountdownWidgetProvider::class.java,
            ),
            gateway.previews.keys,
        )
        assertTrue(rendered.values.none { it.first is WidgetUiState.Empty })
    }

    @Test
    fun `the default renderer produces draw instructions for real widgets`() = runTest {
        gateway.ids[RingsWidgetProvider::class.java] = intArrayOf(1)

        WidgetUpdater(store, gateway = { gateway }).updateAll(context, accounts, now)

        assertEquals(setOf(1), gateway.updated.keys)
    }

    private class FakeGateway : AppWidgetGateway {
        val ids = mutableMapOf<Class<*>, IntArray>()
        val options = mutableMapOf<Int, Bundle>()
        val updated = mutableMapOf<Int, RemoteViews>()
        val previews = mutableMapOf<Class<*>, RemoteViews>()

        override fun appWidgetIds(provider: Class<out HeadroomWidgetProvider>): IntArray =
            ids[provider] ?: IntArray(0)

        override fun options(appWidgetId: Int): Bundle = options[appWidgetId] ?: Bundle()

        override fun update(appWidgetId: Int, views: RemoteViews) {
            updated[appWidgetId] = views
        }

        override fun setPreview(provider: Class<out HeadroomWidgetProvider>, views: RemoteViews) {
            previews[provider] = views
        }
    }
}
