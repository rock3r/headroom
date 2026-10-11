plugins {
    alias(libs.plugins.headroom.kmp.library)
    // Collects the licence of every library HeadroomKit is built with, for the iOS app's licences
    // screen: `exportLibraryDefinitions` writes them to build/generated/aboutLibraries.
    alias(libs.plugins.aboutlibraries)
}

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

aboutLibraries {
    // The iOS framework does not link these: they are the JVM target's HTTP engine and logging.
    library {
        exclusionPatterns.addAll(
            listOf(
                    "com\\.squareup\\.okhttp3:.*",
                    "io\\.ktor:ktor-client-okhttp.*",
                    "org\\.slf4j:.*",
                    "org\\.jetbrains\\.kotlinx:kotlinx-coroutines-slf4j",
                    "org\\.jetbrains:annotations",
                    "androidx\\.sqlite:sqlite-framework",
                    ".*-bom",
                )
                .map { it.toPattern() }
        )
    }
}
