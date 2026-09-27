package dev.sebastiano.headroom.buildlogic

import com.android.build.api.dsl.Lint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Pure Kotlin/JVM module: no Android APIs, JUnit 5, Android Lint for JVM code. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("com.android.lint")

            extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(HeadroomSdk.JVM_TOOLCHAIN)
                explicitApi()
            }
            extensions.configure<Lint> { configureZeroTolerance() }

            tasks.withType<Test>().configureEach { useJUnitPlatform() }

            dependencies {
                add("testImplementation", platform(libs.library("junit5-bom")))
                add("testImplementation", libs.library("junit5-jupiter"))
                add("testImplementation", libs.library("kotlin-test-junit5"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
                add("testRuntimeOnly", libs.library("junit5-launcher"))
            }

            configureQuality()
        }
}
