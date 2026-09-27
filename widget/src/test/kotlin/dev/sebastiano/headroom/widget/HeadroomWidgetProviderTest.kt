package dev.sebastiano.headroom.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import dev.sebastiano.headroom.widget.testing.RecordingHostApplication
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = RecordingHostApplication::class)
class HeadroomWidgetProviderTest {
    private val app = RuntimeEnvironment.getApplication() as RecordingHostApplication
    private val manager = AppWidgetManager.getInstance(app)

    @Test
    fun `each provider stands for one style`() {
        assertEquals(WidgetStyle.Rings, RingsWidgetProvider().style)
        assertEquals(WidgetStyle.Bars, BarsWidgetProvider().style)
        assertEquals(WidgetStyle.Shape, ShapeWidgetProvider().style)
        assertEquals(WidgetStyle.Countdown, CountdownWidgetProvider().style)
        WidgetStyle.entries.forEach { style ->
            assertEquals(
                style,
                HeadroomWidgetProvider.classFor(style).getDeclaredConstructor().newInstance().style,
            )
        }
    }

    @Test
    fun `system updates ask the app for fresh content`() {
        RingsWidgetProvider().onUpdate(app, manager, intArrayOf(4, 5))

        assertEquals(listOf(listOf(4, 5)), app.updateRequests)
    }

    @Test
    fun `resizing or moving a widget asks the app to redraw it`() {
        BarsWidgetProvider().onAppWidgetOptionsChanged(app, manager, 6, Bundle())

        assertEquals(listOf(listOf(6)), app.updateRequests)
    }

    @Test
    fun `deleting widgets removes their config`() = runTest {
        app.widgetConfigStore.set(7, WidgetConfig(WidgetStyle.Shape))
        app.widgetConfigStore.set(8, WidgetConfig(WidgetStyle.Shape))

        ShapeWidgetProvider().onDeleted(app, intArrayOf(7))
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(app.widgetConfigStore.get(7))
        assertNotNull(app.widgetConfigStore.get(8))
    }

    @Test
    fun `the manifest registers one widget receiver per style with its provider info`() {
        val expected =
            mapOf(
                WidgetStyle.Rings to R.xml.widget_rings_info,
                WidgetStyle.Bars to R.xml.widget_bars_info,
                WidgetStyle.Shape to R.xml.widget_shape_info,
                WidgetStyle.Countdown to R.xml.widget_countdown_info,
            )
        expected.forEach { (style, xml) ->
            val info =
                app.packageManager.getReceiverInfo(
                    ComponentName(app, HeadroomWidgetProvider.classFor(style)),
                    PackageManager.GET_META_DATA,
                )
            assertEquals(xml, info.metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER))
        }
    }

    @Test
    fun `provider infos allow home and lock screens and set target cells`() {
        val expected =
            mapOf(
                R.xml.widget_rings_info to (2 to 2),
                R.xml.widget_bars_info to (4 to 1),
                R.xml.widget_shape_info to (2 to 2),
                R.xml.widget_countdown_info to (2 to 2),
            )
        expected.forEach { (xml, cells) ->
            val attributes = providerAttributes(xml)
            val categories = attributes.getValue("widgetCategory").removePrefix("0x").toInt(16)
            assertEquals(
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN or
                    AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD,
                categories,
            )
            assertEquals(cells.first.toString(), attributes["targetCellWidth"])
            assertEquals(cells.second.toString(), attributes["targetCellHeight"])
            assertEquals("0", attributes["updatePeriodMillis"])
            assertNotNull(attributes["previewLayout"])
            assertNotNull(attributes["description"])
            assertNull(attributes["configure"])
        }
    }

    private fun providerAttributes(xml: Int): Map<String, String> {
        val parser = app.resources.getXml(xml)
        while (parser.next() != XmlPullParser.START_TAG) Unit
        assertEquals("appwidget-provider", parser.name)
        return (0 until parser.attributeCount).associate {
            parser.getAttributeName(it) to parser.getAttributeValue(it)
        }
    }
}
