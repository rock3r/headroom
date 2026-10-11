package dev.sebastiano.headroom.buildlogic

import com.android.build.api.dsl.Lint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Kotlin Multiplatform module shared with the iOS app: no Android APIs, JVM and iOS targets.
 * Tests live in `commonTest` and use `kotlin.test`, so they run on the JVM (JUnit 5) and on the iOS
 * simulator. The iOS targets only build on macOS; elsewhere Kotlin skips them.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.lint")

            extensions.configure<KotlinMultiplatformExtension> {
                jvmToolchain(HeadroomSdk.JVM_TOOLCHAIN)
                explicitApi()

                jvm()
                iosArm64()
                iosSimulatorArm64()

                sourceSets.named("commonTest") {
                    dependencies {
                        implementation(libs.library("kotlin-test"))
                        implementation(libs.library("kotlinx-coroutines-test"))
                    }
                }
                sourceSets.named("jvmTest") {
                    dependencies {
                        implementation(project.dependencies.platform(libs.library("junit5-bom")))
                        implementation(libs.library("kotlin-test-junit5"))
                        runtimeOnly(libs.library("junit5-launcher"))
                    }
                }
            }
            extensions.configure<Lint> { configureZeroTolerance() }

            tasks.withType<Test>().configureEach { useJUnitPlatform() }

            configureQuality()
        }
}
