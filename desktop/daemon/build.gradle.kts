plugins {
    alias(libs.plugins.headroom.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    macosArm64 {
        binaries.executable {
            entryPoint = "dev.sebastiano.headroom.daemon.main"
            baseName = "headroomd"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            implementation(projects.core.quota)
            implementation(projects.core.auth)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.atomicfu)
        }
    }
}
