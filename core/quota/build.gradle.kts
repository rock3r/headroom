plugins {
    alias(libs.plugins.headroom.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.atomicfu)
            api(libs.kotlinx.io.core)
            api(libs.ktor.client.core)
            implementation(libs.okio)
        }
        jvmMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        jvmTest.dependencies { implementation(libs.okhttp.mockwebserver) }
    }
}
