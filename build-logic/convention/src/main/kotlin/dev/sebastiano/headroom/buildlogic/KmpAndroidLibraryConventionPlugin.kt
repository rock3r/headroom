package dev.sebastiano.headroom.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.dsl.Lint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Kotlin Multiplatform module with an Android target as well, for code that uses Android-aware
 * libraries such as Room: the Android app gets the Android build of the library. The JVM target is
 * there for fast `commonTest` runs on the host; the Android target has no tests of its own. Set the
 * namespace in the module.
 */
class KmpAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            // The multiplatform Android plugin only registers Lint tasks with this one applied.
            pluginManager.apply("com.android.lint")
            configureKmpTargets()
            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.getByType<KotlinMultiplatformAndroidLibraryTarget>().apply {
                    compileSdk = HeadroomSdk.COMPILE
                    minSdk = HeadroomSdk.MIN
                }
            }
            extensions.configure<Lint> { configureZeroTolerance() }
            configureQuality()
        }
}
