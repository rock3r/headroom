package dev.sebastiano.headroom.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import dev.sebastiano.headroom.model.InMemorySettingsRepository
import dev.sebastiano.headroom.model.NoResets
import dev.sebastiano.headroom.model.ResetProvider
import dev.sebastiano.headroom.model.ResetScope
import dev.sebastiano.headroom.prototype.PrototypeEnv
import dev.sebastiano.headroom.prototype.PrototypeTools
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings' rows arrive with the reveal through [revealContentEntrance]. When the reveal ends, the
 * rows must not be composed again: on a phone, composing every visible row in the reveal's last
 * frame made that frame miss its deadline.
 */
@RunWith(RobolectricTestRunner::class)
// Tall enough for every row of Settings to be composed in its first frame.
@Config(qualifiers = "w411dp-h4000dp")
class SettingsRevealEndTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var revealing by mutableStateOf(true)
    private var rowCompositions = 0

    @Test
    fun `a row is not composed again when the reveal ends`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                SharedTransitionLayout {
                    val reveal = remember { SettingsReveal(this) }
                    AnimatedVisibility(visible = true) {
                        val scope = this
                        val page = remember {
                            PageReveal(
                                reveal = reveal,
                                visibility = scope,
                                animate = true,
                                isRevealing = { revealing },
                                isScrubbing = { false },
                            )
                        }
                        // Like Settings: one modifier made above the list, used by its rows.
                        val entrance = Modifier.revealContentEntrance(page)
                        LazyColumn {
                            item {
                                SideEffect { rowCompositions++ }
                                Box(entrance.size(10.dp))
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val duringReveal = rowCompositions

        revealing = false
        rule.waitForIdle()

        assertEquals(duringReveal, rowCompositions)
    }

    @Test
    fun `in the app, Settings' rows are not composed again when the reveal ends`() {
        rule.setContent {
            HeadroomTheme(dynamicColor = false) {
                HeadroomApp(
                    graph =
                        testGraph(
                            rule.activity,
                            settings = InMemorySettingsRepository(),
                            prototypes = CountingPrototypes,
                        )
                )
            }
        }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.mainClock.advanceTimeBy(MID_REVEAL_MILLIS)
        val duringReveal = CountingPrototypes.entryCompositions

        rule.mainClock.advanceTimeBy(AFTER_REVEAL_MILLIS)

        assertEquals(duringReveal, CountingPrototypes.entryCompositions)
    }

    /** Debug tools whose Settings row, near the end of the list, counts its compositions. */
    private object CountingPrototypes : PrototypeTools {
        var entryCompositions = 0

        override fun resetProvider(
            now: Instant,
            onServerReset: (accountId: String, scope: ResetScope) -> Unit,
        ): ResetProvider = NoResets

        @Composable
        override fun SettingsEntry(onOpen: () -> Unit, modifier: Modifier) {
            SideEffect { entryCompositions++ }
            Box(modifier.size(10.dp))
        }

        @Composable
        override fun Screen(env: PrototypeEnv, onBack: () -> Unit, modifier: Modifier) = Unit
    }

    private companion object {
        const val MID_REVEAL_MILLIS = 200L
        const val AFTER_REVEAL_MILLIS = 2_000L
    }
}
