plugins {
    alias(libs.plugins.headroom.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // Desktop daemon: the Darwin HTTP engine already used by iOS also publishes a macOS variant,
    // so the shared code compiles without a JVM. Only the modules headroomd links against opt in;
    // modules with iOS-only actuals (for example Room in core/storage) are unaffected.
    macosArm64()
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.atomicfu)
            api(libs.kotlinx.io.core)
            implementation(libs.ktor.client.core)
            implementation(libs.okio)
        }
        jvmMain.dependencies { implementation(libs.ktor.client.okhttp) }
        appleMain.dependencies { implementation(libs.ktor.client.darwin) }
        jvmTest.dependencies {
            implementation(libs.okhttp)
            implementation(libs.okhttp.mockwebserver)
        }
    }
}
