import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.headroom.android.application)
    alias(libs.plugins.headroom.android.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "dev.sebastiano.headroom"
    defaultConfig {
        applicationId = "dev.sebastiano.headroom"
        versionCode = 1
        versionName = "0.1.0"
        // Starts the app in demo mode with a fixed clock for the end-to-end tests.
        testInstrumentationRunner = "dev.sebastiano.headroom.HeadroomTestRunner"
    }
    testOptions {
        managedDevices {
            localDevices {
                create("pixel9api37") {
                    device = "Pixel 9"
                    sdkVersion = 37
                    systemImageSource = "google_apis"
                    // The installed Android 37 google_apis image uses 4 KB pages.
                    pageAlignment = ManagedVirtualDevice.PageAlignment.FORCE_4KB_PAGES
                    // Apple silicon runs the arm64 image; CI runners are x86_64.
                    testedAbi =
                        if (System.getProperty("os.arch") == "aarch64") "arm64-v8a" else "x86_64"
                }
            }
        }
    }
}

// Screenshots of the main screens, for the README. They are recorded, never compared in CI.
roborazzi { outputDir.set(rootProject.file("docs/screenshots")) }

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
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
