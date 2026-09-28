package dev.sebastiano.headroom.widget.render

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Canvas
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import dev.sebastiano.headroom.model.DemoData
import dev.sebastiano.headroom.model.Provider
import dev.sebastiano.headroom.model.logo
import dev.sebastiano.headroom.widget.ColourMode
import dev.sebastiano.headroom.widget.WidgetConfig
import dev.sebastiano.headroom.widget.WidgetHostCategory
import dev.sebastiano.headroom.widget.WidgetIntents
import dev.sebastiano.headroom.widget.WidgetSize
import dev.sebastiano.headroom.widget.WidgetStyle
import dev.sebastiano.headroom.widget.WidgetUiState
import dev.sebastiano.headroom.widget.WidgetWindow
import dev.sebastiano.headroom.widget.testing.PLAYER_MAX_VARIABLES
import dev.sebastiano.headroom.widget.testing.RecordingHostApplication
import dev.sebastiano.headroom.widget.testing.documentOperations
import dev.sebastiano.headroom.widget.testing.hasNamedHostActions
import dev.sebastiano.headroom.widget.testing.hostActionIds
import dev.sebastiano.headroom.widget.testing.logoCommands
import dev.sebastiano.headroom.widget.testing.maxVariableId
import dev.sebastiano.headroom.widget.testing.pathCommands
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = RecordingHostApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetRendererTest {
    private val context = RuntimeEnvironment.getApplication()
    private val now = Instant.parse("2026-09-27T12:00:00Z")
    private val accounts = DemoData.accounts(now)
    private val strings = WidgetStrings(context, ZoneOffset.UTC, Locale.US, is24Hour = true)

    private fun state(
        config: WidgetConfig,
        size: WidgetSize = WidgetSize(140f, 140f),
        host: WidgetHostCategory = WidgetHostCategory.HomeScreen,
    ) = WidgetUiState.from(accounts, config, now, size, host)

    private suspend fun capture(
        config: WidgetConfig,
        size: WidgetSize = WidgetSize(140f, 140f),
        host: WidgetHostCategory = WidgetHostCategory.HomeScreen,
    ): WidgetDocument =
        WidgetRenderer.capture(context, state(config, size, host), APP_WIDGET_ID, size, strings)

    @Test
    fun `single ring shows the account, both windows and can flip`() = runTest {
        val config = WidgetConfig(WidgetStyle.Rings, accountIds = listOf("demo-claude"))
        assertIs<WidgetUiState.SingleRing>(state(config))

        val doc = capture(config)

        assertTrue(doc.bytes.isNotEmpty())
        doc.assertText("CLAUDE", "weekly", "session")
        doc.assertText("Claude: 71% of the weekly limit used, over pace. Resets Wed 06:28.")
        doc.assertText("Claude: 38% of the session limit used. Resets 1h 12m.")
        doc.assertText("Switch between the weekly and session numbers")
        assertEquals(listOf(Tap.Refresh, Tap.Open("demo-claude")), doc.taps())
    }

    @Test
    fun `ring grid shows one small ring per account`() = runTest {
        val doc = capture(WidgetConfig(WidgetStyle.Rings))

        doc.assertText("71", "34", "88", "58", "Claude", "Codex", "Grok", "Copilot")
        assertEquals(
            listOf(Tap.Refresh) +
                listOf("demo-claude", "demo-codex", "demo-grok", "demo-copilot").map {
                    Tap.Open(it)
                },
            doc.taps(),
        )
    }

    @Test
    fun `bars show a row per account with name and percentage`() = runTest {
        val doc = capture(WidgetConfig(WidgetStyle.Bars), WidgetSize(280f, 110f))

        doc.assertText("Claude", "Codex", "Grok", "71%", "34%", "88%")
        doc.assertNoText("Copilot")
        assertEquals(
            listOf(Tap.Refresh) +
                listOf("demo-claude", "demo-codex", "demo-grok").map { Tap.Open(it) },
            doc.taps(),
        )
    }

    @Test
    fun `sixteen bars stay within the variables the Android 16 player can hold`() = runTest {
        // Tall enough for every row, so the document holds all sixteen.
        val size = WidgetSize(280f, 600f)
        val accounts =
            DemoData.manyAccounts(now) +
                DemoData.manyAccounts(now).map {
                    it.copy(account = it.account.copy(id = it.account.id + "-2", nickname = "Work"))
                }
        val state =
            WidgetUiState.from(
                accounts,
                WidgetConfig(WidgetStyle.Bars),
                now,
                size,
                WidgetHostCategory.HomeScreen,
            )
        val doc = WidgetRenderer.capture(context, state, APP_WIDGET_ID, size, strings)

        val operations = documentOperations(doc.bytes)
        assertEquals(16, hostActionIds(operations).size - 1, "A tap per account and the refresh")
        val maxId = maxVariableId(operations)
        assertTrue(maxId < PLAYER_MAX_VARIABLES, "The document uses variable $maxId")
        WidgetRenderer.remoteViews(doc).playAt(size)
    }

    @Test
    fun `bars in session mode show only accounts with a session`() = runTest {
        val doc =
            capture(
                WidgetConfig(WidgetStyle.Bars, window = WidgetWindow.Session),
                WidgetSize(280f, 110f),
            )

        doc.assertText("38%", "12%", "Claude", "Codex")
        doc.assertNoText("Grok")
    }

    @Test
    fun `single shape shows the number and the reset time`() = runTest {
        val doc = capture(WidgetConfig(WidgetStyle.Shape, accountIds = listOf("demo-codex")))

        doc.assertText("CODEX", "34%", "Thu 15:48")
        assertEquals(listOf(Tap.Refresh, Tap.Open("demo-codex")), doc.taps())
    }

    @Test
    fun `shape grid shows a number and a logo per account`() = runTest {
        val doc = capture(WidgetConfig(WidgetStyle.Shape, colourMode = ColourMode.PerAccount))

        doc.assertText("71", "34", "88", "58")
        doc.assertDrawsLogos(Provider.Claude, Provider.Codex, Provider.Grok, Provider.Copilot)
        doc.assertNoText("GH")
        assertEquals(5, doc.taps().size)
    }

    @Test
    fun `bar avatars draw the provider logos, not letters`() = runTest {
        val doc = capture(WidgetConfig(WidgetStyle.Bars), WidgetSize(280f, 110f))

        doc.assertDrawsLogos(Provider.Claude, Provider.Codex, Provider.Grok)
    }

    @Test
    fun `countdown names the next weekly reset`() = runTest {
        val doc = capture(WidgetConfig(WidgetStyle.Countdown))

        doc.assertText("NEXT WEEKLY RESET", "Grok · Mon 03:28")
        doc.assertText("Next weekly reset in 15h 28m: Grok, Mon 03:28")
        assertEquals(listOf(Tap.Refresh, Tap.Open("demo-grok")), doc.taps())
    }

    @Test
    fun `lock screen uses the compact rings with a reset footer`() = runTest {
        val doc =
            capture(
                WidgetConfig(WidgetStyle.Bars),
                WidgetSize(300f, 120f),
                WidgetHostCategory.Keyguard,
            )

        doc.assertText("71%", "34%", "88%", "Claude", "Codex", "Grok")
        doc.assertText("Next weekly reset: Grok, Mon 03:28")
        doc.assertNoText("Copilot")
        doc.assertDrawsLogos(Provider.Claude, Provider.Codex, Provider.Grok)
    }

    @Test
    fun `empty widgets explain why and open the app`() = runTest {
        val doc =
            capture(
                WidgetConfig(
                    WidgetStyle.Rings,
                    accountIds = listOf("demo-grok"),
                    window = WidgetWindow.Session,
                )
            )

        doc.assertText("None of these accounts has a session limit.")
        assertEquals(listOf(Tap.Open(null)), doc.taps())
    }

    @Test
    fun `every style renders in every colour mode`() = runTest {
        WidgetStyle.entries.forEach { style ->
            ColourMode.entries.forEach { mode ->
                val doc = capture(WidgetConfig(style, colourMode = mode))
                assertTrue(doc.bytes.isNotEmpty(), "$style in $mode")
            }
        }
    }

    @Test
    fun `every layout plays in the Android 16 platform widget player`() = runTest {
        assertEquals(6L, RemoteViews.DrawInstructions.getSupportedVersion())
        val layouts =
            listOf(
                WidgetConfig(WidgetStyle.Rings, accountIds = listOf("demo-claude")) to
                    WidgetSize(140f, 140f),
                WidgetConfig(WidgetStyle.Rings) to WidgetSize(140f, 140f),
                WidgetConfig(WidgetStyle.Bars) to WidgetSize(280f, 180f),
                WidgetConfig(WidgetStyle.Shape, accountIds = listOf("demo-grok")) to
                    WidgetSize(140f, 140f),
                WidgetConfig(WidgetStyle.Shape) to WidgetSize(140f, 140f),
                WidgetConfig(WidgetStyle.Countdown) to WidgetSize(140f, 140f),
                WidgetConfig(
                    WidgetStyle.Rings,
                    window = WidgetWindow.Session,
                    accountIds = listOf("demo-grok"),
                ) to WidgetSize(140f, 140f),
            )
        layouts.forEach { (config, size) ->
            val views = WidgetRenderer.remoteViews(capture(config, size))
            views.playAt(size)
        }
        WidgetRenderer.remoteViews(
                capture(
                    WidgetConfig(WidgetStyle.Bars),
                    WidgetSize(300f, 120f),
                    WidgetHostCategory.Keyguard,
                )
            )
            .playAt(WidgetSize(300f, 120f))
    }

    /**
     * Inflates the views with the platform player, draws them, and checks that something besides
     * the background was drawn. The Android 16 player draws nothing, or only the card, when a
     * document uses something it does not support.
     */
    private fun RemoteViews.playAt(size: WidgetSize) {
        val density = context.resources.displayMetrics.density
        val width = (size.widthDp * density).toInt()
        val height = (size.heightDp * density).toInt()
        val view = apply(context, FrameLayout(context))
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        val bitmap = createBitmap(width, height)
        view.draw(Canvas(bitmap))

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val background = pixels.groupBy { it }.maxBy { it.value.size }.key
        val ink = pixels.count { it != background }.toFloat() / pixels.size
        assertTrue(ink > MIN_INK, "Only ${ink * 100}% of the widget was drawn at $size")
    }

    @Test
    fun `every tap is an id host action with a click response for that id`() = runTest {
        val docs =
            listOf(
                capture(WidgetConfig(WidgetStyle.Rings, accountIds = listOf("demo-claude"))),
                capture(WidgetConfig(WidgetStyle.Rings)),
                capture(WidgetConfig(WidgetStyle.Bars), WidgetSize(280f, 180f)),
                capture(WidgetConfig(WidgetStyle.Shape, accountIds = listOf("demo-grok"))),
                capture(WidgetConfig(WidgetStyle.Shape)),
                capture(WidgetConfig(WidgetStyle.Countdown)),
                capture(
                    WidgetConfig(WidgetStyle.Bars),
                    WidgetSize(300f, 120f),
                    WidgetHostCategory.Keyguard,
                ),
                capture(
                    WidgetConfig(
                        WidgetStyle.Rings,
                        accountIds = listOf("demo-grok"),
                        window = WidgetWindow.Session,
                    )
                ),
            )

        docs.forEach { doc ->
            val operations = documentOperations(doc.bytes)
            assertFalse(hasNamedHostActions(operations), operations)
            assertTrue(doc.taps.keys.none { it == 0 })
            assertEquals(doc.taps.keys, hostActionIds(operations), operations)
        }
    }

    @Test
    fun `tapping a widget in the platform player sends its pending intent`() = runTest {
        val size = WidgetSize(140f, 140f)
        val app = context as RecordingHostApplication

        // The ring grid: the top-left cell opens Claude.
        WidgetRenderer.remoteViews(capture(WidgetConfig(WidgetStyle.Rings), size))
            .tapAt(size, xShare = 0.3f, yShare = 0.3f)
        val opened = shadowOf(app).nextStartedActivity
        assertNotNull(opened, "Tapping a ring should open the app")
        assertEquals("demo-claude", opened.getStringExtra(WidgetIntents.EXTRA_ACCOUNT_ID))

        // The countdown card around the text refreshes.
        WidgetRenderer.remoteViews(capture(WidgetConfig(WidgetStyle.Countdown), size))
            .tapAt(size, xShare = 0.5f, yShare = 0.08f)
        assertEquals(
            WidgetIntents.ACTION_REFRESH,
            shadowOf(app).broadcastIntents.lastOrNull()?.action,
        )
    }

    /** Plays the views in the platform player and taps at a point given as shares of the size. */
    private fun RemoteViews.tapAt(size: WidgetSize, xShare: Float, yShare: Float) {
        val density = context.resources.displayMetrics.density
        val width = (size.widthDp * density).toInt()
        val height = (size.heightDp * density).toInt()
        val view = apply(context, FrameLayout(context))
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        view.draw(Canvas(createBitmap(width, height)))
        val x = width * xShare
        val y = height * yShare
        val time = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0))
        view.dispatchTouchEvent(
            MotionEvent.obtain(time, time + TAP_MS, MotionEvent.ACTION_UP, x, y, 0)
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `remote views wrap the document and survive a parcel round trip`() = runTest {
        val views = WidgetRenderer.remoteViews(capture(WidgetConfig(WidgetStyle.Rings)))

        val parcel = Parcel.obtain()
        try {
            views.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = RemoteViews.CREATOR.createFromParcel(parcel)
            assertNotNull(restored.apply(context, FrameLayout(context)))
        } finally {
            parcel.recycle()
        }
    }

    private sealed interface Tap {
        data object Refresh : Tap

        data class Open(val accountId: String?) : Tap
    }

    private fun WidgetDocument.taps(): List<Tap> {
        return taps.toSortedMap().values.map { it.toTap() }
    }

    private fun PendingIntent.toTap(): Tap {
        val shadow = shadowOf(this)
        val intent = shadow.savedIntent
        assertEquals(APP_WIDGET_ID, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        return when {
            shadow.isBroadcast && intent.action == WidgetIntents.ACTION_REFRESH -> Tap.Refresh
            shadow.isActivity && intent.action == Intent.ACTION_MAIN ->
                Tap.Open(intent.getStringExtra(WidgetIntents.EXTRA_ACCOUNT_ID))
            else -> error("Unexpected pending intent $intent")
        }
    }

    private fun WidgetDocument.text(): String = String(bytes, Charsets.UTF_8)

    private fun WidgetDocument.assertText(vararg expected: String) {
        val text = text()
        expected.forEach { assertContains(text, it) }
    }

    private fun WidgetDocument.assertNoText(unexpected: String) {
        assertFalse(text().contains(unexpected), "Did not expect \"$unexpected\" in the document")
    }

    /**
     * Checks that the document draws each provider's logo: a path with the same commands, in the
     * same order, as the logo's path data. Letters would be text, not paths.
     */
    private fun WidgetDocument.assertDrawsLogos(vararg providers: Provider) {
        val drawn = pathCommands(documentOperations(bytes))
        providers.forEach { provider ->
            val expected = logoCommands(provider.logo.pathData)
            assertTrue(expected in drawn, "No path for the $provider logo ($expected) in $drawn")
        }
    }

    private companion object {
        const val APP_WIDGET_ID = 7
        const val MIN_INK = 0.03f
        const val TAP_MS = 50L
    }
}
