import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.headroom.android.application)
    alias(libs.plugins.headroom.android.compose)
    alias(libs.plugins.roborazzi)
    // Collects the licence of every dependency at build time into res/raw/aboutlibraries.json.
    alias(libs.plugins.aboutlibraries.android)
    // Uploads the Play build and its store listing to Google Play. See docs/PLAY-STORE.md.
    alias(libs.plugins.play.publisher)
}

android {
    namespace = "dev.sebastiano.headroom"
    defaultConfig {
        applicationId = "dev.sebastiano.headroom"
        versionCode = 3
        versionName = "1.2.0"
        // Starts the app in demo mode with a fixed clock for the end-to-end tests.
        testInstrumentationRunner = "dev.sebastiano.headroom.HeadroomTestRunner"
    }
    // The release key comes from the environment, so it never lives in the repository. CI sets
    // these
    // from its secrets; see docs/RELEASING.md. Without them, a release build is left unsigned.
    val keystore = providers.environmentVariable("HEADROOM_KEYSTORE_FILE").orNull
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = providers.environmentVariable("HEADROOM_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("HEADROOM_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("HEADROOM_KEY_PASSWORD").get()
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
        // The Google Play build: the release build without what Play policy refuses. Its manifest
        // in src/play drops USE_EXACT_ALARM and the reset island's accessibility service. See
        // docs/PLAY-STORE.md.
        create("play") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
        }
    }
    // The Play build ships the release code, including what only release builds have.
    sourceSets.getByName("play").kotlin.srcDir("src/release/kotlin")
    // Only the Play build goes to Google Play.
    playConfigs { register("play") { enabled.set(true) } }
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

// The service account key comes from the ANDROID_PUBLISHER_CREDENTIALS environment variable, so it
// never lives in the repository. The release workflow passes the track, for example
// `--track production`; without it the bundle goes to internal testing.
play {
    enabled.set(false)
    defaultToAppBundles.set(true)
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
    implementation(libs.aboutlibraries.compose.m3)
    // Trace sections for performance work. Release builds keep the library's stub tracer; debug
    // builds record in-process with tracing-wire, and name composables in system traces.
    implementation(libs.androidx.tracing)
    debugImplementation(libs.androidx.tracing.wire)
    debugImplementation(libs.compose.runtime.tracing)
    implementation(libs.reorderable)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
