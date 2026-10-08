package dev.sebastiano.headroom.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import dev.sebastiano.headroom.designsystem.HeadroomTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Settings' rows arrive with the reveal through [revealContentEntrance]. When the reveal ends, the
 * rows must not be composed again: on a phone, composing every visible row in the reveal's last
 * frame made that frame miss its deadline.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRevealEndTest {
    @get:Rule val rule = createComposeRule()

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
}
