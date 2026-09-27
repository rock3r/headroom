package dev.sebastiano.headroom.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal fun configureAndroidCommon(extension: CommonExtension) {
    with(extension) {
        compileSdk { version = release(HeadroomSdk.COMPILE) }
        defaultConfig.minSdk = HeadroomSdk.MIN
        compileOptions.sourceCompatibility = JavaVersion.VERSION_21
        compileOptions.targetCompatibility = JavaVersion.VERSION_21
        testOptions.unitTests.isIncludeAndroidResources = true
        testOptions.unitTests.isReturnDefaultValues = false
        testOptions.unitTests.all { test ->
            // Robolectric reaches into java.io and java.lang internals, which JDK 17+ hides.
            test.jvmArgs(
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
            )
            test.maxHeapSize = "2g"
        }
        packaging.resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
    }
}

/** AGP 9 compiles Kotlin itself; only the toolchain needs setting. */
internal fun Project.configureKotlinAndroid() {
    extensions.configure<KotlinAndroidProjectExtension> { jvmToolchain(HeadroomSdk.JVM_TOOLCHAIN) }
}
