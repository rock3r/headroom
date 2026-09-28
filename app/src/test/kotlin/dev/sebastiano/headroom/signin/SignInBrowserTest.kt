package dev.sebastiano.headroom.signin

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.browser.customtabs.CustomTabsService
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.TestHeadroomApplication
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Sign-in pages open in a browser, never in an app that claims the provider's domain. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestHeadroomApplication::class)
class SignInBrowserTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val packages = shadowOf(context.packageManager)

    private fun installBrowser(packageName: String, customTabs: Boolean) {
        val activity = ComponentName(packageName, "$packageName.BrowserActivity")
        packages.addActivityIfNotPresent(activity)
        packages.addIntentFilterForActivity(
            activity,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("http")
                addDataScheme("https")
            },
        )
        if (customTabs) {
            val service = ComponentName(packageName, "$packageName.CustomTabsService")
            packages.addServiceIfNotPresent(service)
            packages.addIntentFilterForService(
                service,
                IntentFilter(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION),
            )
        }
    }

    /** An app that claims OpenAI sign-in links, like the ChatGPT app. */
    private fun installChatGptLikeApp() {
        val activity = ComponentName(CHATGPT, "$CHATGPT.LinkActivity")
        packages.addActivityIfNotPresent(activity)
        packages.addIntentFilterForActivity(
            activity,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("https")
                addDataAuthority("auth.openai.com", null)
                addDataAuthority("chatgpt.com", null)
            },
        )
    }

    @Test
    fun `the tab targets the browser when an app also claims the link`() {
        installChatGptLikeApp()
        installBrowser(BROWSER, customTabs = true)

        assertEquals(BROWSER, signInTabIntent(context).intent.getPackage())
    }

    @Test
    fun `a default browser without Custom Tabs is still targeted`() {
        installChatGptLikeApp()
        installBrowser(BROWSER, customTabs = false)

        assertEquals(BROWSER, signInTabIntent(context).intent.getPackage())
    }

    @Test
    fun `a browser with Custom Tabs wins over one without`() {
        installBrowser(BROWSER, customTabs = false)
        installBrowser(TAB_BROWSER, customTabs = true)

        assertEquals(TAB_BROWSER, signInTabIntent(context).intent.getPackage())
    }

    @Test
    fun `without a browser the tab is still a plain view intent`() {
        installChatGptLikeApp()

        val tab = signInTabIntent(context)

        assertNull(tab.intent.getPackage())
        assertEquals(Intent.ACTION_VIEW, tab.intent.action)
    }

    private companion object {
        const val BROWSER = "com.example.browser"
        const val TAB_BROWSER = "com.example.tabbrowser"
        const val CHATGPT = "com.example.chatgpt"
    }
}
