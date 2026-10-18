package dev.sebastiano.headroom

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import org.w3c.dom.Element

/**
 * Android 16 turns on the predictive back gesture for apps that target it, but Android 14 and 15
 * only show it to apps that ask for it in the manifest.
 */
class PredictiveBackManifestTest {
    @Test
    fun `the app asks for the predictive back gesture, which Android 14 and 15 need`() {
        val manifest =
            DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(File("src/main/AndroidManifest.xml"))
        val application = manifest.getElementsByTagName("application").item(0) as Element

        assertEquals("true", application.getAttributeNS(ANDROID, "enableOnBackInvokedCallback"))
    }
}

private const val ANDROID = "http://schemas.android.com/apk/res/android"
