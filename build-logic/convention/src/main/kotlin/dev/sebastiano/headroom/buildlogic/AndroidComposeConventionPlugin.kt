package dev.sebastiano.headroom.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** Adds Compose, its test tooling and the Compose Rules detekt plugin to an Android module. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.getByType<CommonExtension>().buildFeatures.compose = true

            dependencies {
                val bom = platform(libs.library("compose-bom"))
                add("implementation", bom)
                add("implementation", libs.library("compose-ui"))
                add("implementation", libs.library("compose-foundation"))
                add("implementation", libs.library("compose-material3"))
                add("implementation", libs.library("compose-ui-tooling-preview"))
                add("debugImplementation", libs.library("compose-ui-tooling"))
                add("testImplementation", bom)
                add("testImplementation", libs.library("compose-ui-test-junit4"))
                add("debugImplementation", libs.library("compose-ui-test-manifest"))
                add("androidTestImplementation", bom)
                add("androidTestImplementation", libs.library("compose-ui-test-junit4"))
                add("detektPlugins", libs.library("composeRules-detekt"))
            }
        }
}
