plugins {
    alias(libs.plugins.headroom.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "dev.sebastiano.headroom.data" }

dependencies {
    api(projects.core.model)
    api(projects.core.quota)
    api(projects.core.auth)
    api(projects.core.storage)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.tink.android)
    implementation(libs.okhttp)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.test.core)
}
