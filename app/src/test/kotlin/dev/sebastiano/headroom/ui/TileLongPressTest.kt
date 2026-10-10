package dev.sebastiano.headroom.ui

import android.content.ComponentName
import android.content.Intent
import android.service.quicksettings.TileService
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.headroom.MainActivity
import dev.sebastiano.headroom.TestHeadroomApplication
import dev.sebastiano.headroom.tile.HeadroomTileService
import dev.sebastiano.headroom.ui.overview.OVERVIEW_LIST_TAG
import dev.sebastiano.headroom.ui.settings.ADD_TILE_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_LIST_TAG
import dev.sebastiano.headroom.ui.settings.SETTINGS_TAG
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * A long press on the Quick Settings tile opens the app's QS_TILE_PREFERENCES activity. Headroom
 * answers with Settings, scrolled to the Quick Settings tile's rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestHeadroomApplication::class, qualifiers = "w411dp-h891dp")
class TileLongPressTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<TestHeadroomApplication>()

    /** What SystemUI sends on a long press: see CustomTile.getLongClickIntent in AOSP. */
    private fun longPress() =
        Intent(TileService.ACTION_QS_TILE_PREFERENCES)
            .setClassName(context, MainActivity::class.java.name)
            .putExtra(
                Intent.EXTRA_COMPONENT_NAME,
                ComponentName(context, HeadroomTileService::class.java),
            )

    private fun launch(intent: Intent): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java, intent).setup().also {
            rule.waitForIdle()
        }

    @Test
    fun `the tile's preferences action resolves to Headroom`() {
        val query = Intent(TileService.ACTION_QS_TILE_PREFERENCES).setPackage(context.packageName)

        val resolved = context.packageManager.resolveActivity(query, 0)

        assertEquals(MainActivity::class.java.name, resolved?.activityInfo?.name)
    }

    @Test
    fun `a long press on the tile opens Settings at the tile's rows`() {
        launch(longPress())

        rule.onNodeWithTag(SETTINGS_TAG).assertIsDisplayed()
        rule.onNodeWithTag(ADD_TILE_TAG).assertIsDisplayed()
    }

    @Test
    fun `a long press while the app is open opens Settings at the tile's rows`() {
        val activity = launch(Intent(context, MainActivity::class.java))
        rule.onNodeWithTag(OVERVIEW_LIST_TAG).assertIsDisplayed()

        activity.newIntent(longPress())
        rule.waitForIdle()

        rule.onNodeWithTag(SETTINGS_TAG).assertIsDisplayed()
        rule.onNodeWithTag(ADD_TILE_TAG).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp")
    fun `a long press while Settings is scrolled past the tile's rows scrolls back up to them`() {
        val activity = launch(Intent(context, MainActivity::class.java))
        rule.onNodeWithContentDescription("Settings").performClick()
        rule
            .onNodeWithTag(SETTINGS_LIST_TAG)
            .performScrollToNode(hasText("Version", substring = true))
        rule.onNodeWithTag(ADD_TILE_TAG).assertIsNotDisplayed()

        activity.newIntent(longPress())
        rule.waitForIdle()

        rule.onNodeWithTag(ADD_TILE_TAG).assertIsDisplayed()
    }
}
