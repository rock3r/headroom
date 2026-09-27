plugins {
    alias(libs.plugins.headroom.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android { namespace = "dev.sebastiano.headroom.data" }

dependencies {
    api(projects.core.model)
    api(projects.core.quota)
    api(projects.core.auth)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.tink.android)
    implementation(libs.okhttp)
    testImplementation(libs.androidx.work.testing)
}
