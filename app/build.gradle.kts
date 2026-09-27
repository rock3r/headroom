plugins {
    alias(libs.plugins.headroom.android.application)
    alias(libs.plugins.headroom.android.compose)
}

android {
    namespace = "dev.sebastiano.headroom"
    defaultConfig {
        applicationId = "dev.sebastiano.headroom"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    // Keep the app and its instrumented tests on the same version as androidx.test.
    constraints { implementation(libs.androidx.concurrent.futures) }
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.widget)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.compose.material3.adaptive.navigation)
    implementation(libs.compose.material3.adaptive.navigationSuite)
    implementation(libs.androidx.browser)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
