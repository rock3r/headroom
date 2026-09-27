package dev.sebastiano.headroom.buildlogic

import com.android.build.api.dsl.Lint
import com.ncorti.ktfmt.gradle.KtfmtExtension
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

private val generatedSources = arrayOf("**/build/**", "**/generated/**")

/** Detekt (with Compose Rules where Compose is used) and ktfmt, both wired into `check`. */
internal fun Project.configureQuality() {
    pluginManager.apply("dev.detekt")
    pluginManager.apply("com.ncorti.ktfmt.gradle")

    extensions.configure<KtfmtExtension> { kotlinLangStyle() }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        allRules.set(false)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        parallel.set(true)
        basePath.set(rootProject.layout.projectDirectory)
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget.set(HeadroomSdk.JVM_TOOLCHAIN.toString())
        exclude(*generatedSources)
    }

    tasks
        .matching { it.name.startsWith("ktfmtCheck") || it.name.startsWith("ktfmtFormat") }
        .configureEach { (this as? org.gradle.api.tasks.SourceTask)?.exclude(*generatedSources) }

    // The detekt 2.x `detekt` task is only a marker; run every per-source-set task that does not
    // need type resolution, so `check` covers main, test and androidTest sources.
    tasks.named("check") {
        dependsOn(tasks.withType<Detekt>().matching { it.name.startsWith("detekt") && !it.name.contains("Baseline") })
        dependsOn("ktfmtCheck")
    }
}

/**
 * Android Lint with zero tolerance: every warning is an error. The only disabled checks are the
 * ones that fail the build when a newer library is published, which is not a code problem.
 */
internal fun Lint.configureZeroTolerance() {
    warningsAsErrors = true
    abortOnError = true
    checkReleaseBuilds = true
    checkTestSources = true
    disable +=
        setOf(
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            "OldTargetApi",
        )
}
