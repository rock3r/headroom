package dev.sebastiano.headroom.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

/** Gradle property that selects which `:app` unit tests a CI shard runs. */
internal const val APP_TEST_SHARD_PROPERTY = "headroom.appTestShard"

/**
 * Restricts `:app` unit tests to one CI shard when [APP_TEST_SHARD_PROPERTY] is set.
 *
 * The shards partition every unit test class: redeem screenshot classes, the other
 * `*ScreenshotTest` classes, and everything else. A missing property runs the full suite, so a
 * local `./gradlew check` is unchanged. Unknown values fail configuration so a typo cannot skip
 * tests. Instrumented tests are not touched, because this only configures `unitTests`.
 *
 * Forks stay at one per shard. Screenshot tests already ran GitHub runners out of memory through
 * native-graphics pictures that live off the Java heap (issue #6); four later runs died with a
 * shutdown signal. Extra forks would recreate that, so heavy tests get their own runner instead.
 */
internal fun Project.configureAppUnitTestShards(extension: CommonExtension) {
    val shard = providers.gradleProperty(APP_TEST_SHARD_PROPERTY)
    extension.testOptions.unitTests.all { test ->
        if (!shard.isPresent) return@all
        applyAppTestShard(test, shard.get())
    }
}

internal fun applyAppTestShard(test: Test, shard: String) {
    test.filter.isFailOnNoMatchingTests = true
    when (shard) {
        "redeem-screenshots" -> test.filter.includeTestsMatching("*RedeemSheet*ScreenshotTest")
        "screenshots" -> {
            test.filter.includeTestsMatching("*ScreenshotTest")
            test.filter.excludeTestsMatching("*RedeemSheet*ScreenshotTest")
        }
        "unit" -> test.filter.excludeTestsMatching("*ScreenshotTest")
        else ->
            throw GradleException(
                "Unknown $APP_TEST_SHARD_PROPERTY '$shard'. " +
                    "Use redeem-screenshots, screenshots or unit."
            )
    }
}
