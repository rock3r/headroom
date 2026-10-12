package dev.sebastiano.headroom

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.w3c.dom.Element

/** What an XR headset reads from the manifest to show the app in Full Space. */
@RunWith(RobolectricTestRunner::class)
class XrManifestTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the main activity resizes in place, as the spatial main panel needs`() {
        val main = ComponentName(context, MainActivity::class.java)
        val handled = context.packageManager.getActivityInfo(main, 0).configChanges
        val needed =
            ActivityInfo.CONFIG_ORIENTATION or
                ActivityInfo.CONFIG_SCREEN_SIZE or
                ActivityInfo.CONFIG_SCREEN_LAYOUT or
                ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE
        assertEquals(needed, handled and needed)
    }

    @Test
    fun `the main activity opens in Full Space on XR headsets`() {
        // Robolectric's package manager does not read <property> tags, so read the manifest.
        val manifest =
            DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(File("src/main/AndroidManifest.xml"))
        val main =
            manifest.getElementsByTagName("activity").elements().single {
                it.getAttributeNS(ANDROID, "name") == ".MainActivity"
            }
        val startMode =
            main.getElementsByTagName("property").elements().single {
                it.getAttributeNS(ANDROID, "name") == XR_START_MODE
            }
        assertEquals(
            "XR_ACTIVITY_START_MODE_FULL_SPACE_MANAGED",
            startMode.getAttributeNS(ANDROID, "value"),
        )
    }

    private fun org.w3c.dom.NodeList.elements(): List<Element> =
        (0 until length).map { item(it) as Element }
}

private const val ANDROID = "http://schemas.android.com/apk/res/android"
private const val XR_START_MODE = "android.window.PROPERTY_XR_ACTIVITY_START_MODE"
