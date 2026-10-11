plugins {
    alias(libs.plugins.headroom.kmp.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    android { namespace = "dev.sebastiano.headroom.storage" }

    // Room's database constructor is an expect object that KSP fills in for each target.
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(projects.core.quota)
            api(projects.core.auth)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.room.runtime)
            api(libs.androidx.datastore.preferences.core)
            api(libs.kotlinx.io.core)
        }
        androidMain.dependencies { implementation(libs.androidx.datastore.preferences) }
        iosMain.dependencies { implementation(libs.androidx.sqlite.bundled) }
        commonTest.dependencies {
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.okio)
        }
    }
}

// Room generates the database for every target that has one.
dependencies {
    listOf("kspAndroid", "kspJvm", "kspIosArm64", "kspIosSimulatorArm64").forEach {
        add(it, libs.androidx.room.compiler)
    }
}
