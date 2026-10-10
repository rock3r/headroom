plugins { alias(libs.plugins.headroom.kmp.library) }

kotlin {
    // The framework the iOS app links: HeadroomKit.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "HeadroomKit"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            implementation(projects.core.auth)
            implementation(projects.core.quota)
            implementation(projects.core.storage)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.datetime)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.okio)
        }
    }
}
