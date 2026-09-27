plugins {
    alias(libs.plugins.headroom.android.library)
    alias(libs.plugins.headroom.android.compose)
}

android { namespace = "dev.sebastiano.headroom.widget" }

dependencies {
    implementation(projects.core.model)
    implementation(libs.remote.creation.compose)
    implementation(libs.remote.creation.core)
    implementation(libs.remote.core)
}
