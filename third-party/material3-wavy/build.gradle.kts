// Material 3's determinate LinearWavyProgressIndicator, vendored with one patch: the wave moves at
// 60 fps instead of the display's rate. See README.md.
plugins {
    alias(libs.plugins.headroom.android.library)
    alias(libs.plugins.headroom.android.compose)
}

android { namespace = "dev.sebastiano.headroom.material3.wavy" }
