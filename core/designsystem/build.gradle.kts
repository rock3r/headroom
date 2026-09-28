plugins {
    alias(libs.plugins.headroom.android.library)
    alias(libs.plugins.headroom.android.compose)
}

android { namespace = "dev.sebastiano.headroom.designsystem" }

dependencies {
    api(projects.core.model)
    api(libs.graphics.shapes)
    implementation(libs.materialkolor.utilities)
}
