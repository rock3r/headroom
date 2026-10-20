plugins {
    alias(libs.plugins.headroom.android.library)
    alias(libs.plugins.headroom.android.compose)
}

android { namespace = "dev.sebastiano.headroom.designsystem" }

dependencies {
    api(projects.core.model)
    api(libs.graphics.shapes)
    implementation(libs.materialkolor.utilities)
    // Material's wavy linear indicator, with its wave at 60 fps: see third-party/material3-wavy.
    implementation(projects.thirdParty.material3Wavy)
}
