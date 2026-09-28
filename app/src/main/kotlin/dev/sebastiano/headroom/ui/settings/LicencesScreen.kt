package dev.sebastiano.headroom.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import dev.sebastiano.headroom.R
import dev.sebastiano.headroom.ui.components.StatusBarBlurBox

const val LICENCES_TAG: String = "licences"
const val LICENCES_LIST_TAG: String = "licences-list"

/**
 * Every library the app is built with, and its licence. The AboutLibraries Gradle plugin collects
 * them when the app is built and bundles them as `res/raw/aboutlibraries.json`. The caller handles
 * back, with the predictive back gesture.
 */
@Composable
fun LicencesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val libraries by produceLibraries(R.raw.aboutlibraries)
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val listState = rememberLazyListState()
    Surface(modifier = modifier.fillMaxSize().testTag(LICENCES_TAG)) {
        StatusBarBlurBox(scrollState = listState, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LibrariesContainer(
                    libraries = libraries,
                    lazyListState = listState,
                    contentPadding =
                        PaddingValues(
                            top = insets.calculateTopPadding() + 4.dp,
                            bottom = insets.calculateBottomPadding() + 24.dp,
                        ),
                    header = {
                        item {
                            PageTopBar(
                                title = stringResource(R.string.licences_title),
                                onBack = onBack,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    },
                    licenseDialogConfirmText = stringResource(R.string.licences_close),
                    modifier =
                        Modifier.widthIn(max = MAX_CONTENT_WIDTH)
                            .fillMaxWidth()
                            .testTag(LICENCES_LIST_TAG),
                )
            }
        }
    }
}
