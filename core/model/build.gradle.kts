plugins { alias(libs.plugins.headroom.kmp.library) }

kotlin {
    // Desktop daemon: the Darwin HTTP engine already used by iOS also publishes a macOS variant,
    // so the shared code compiles without a JVM. Only the modules headroomd links against opt in;
    // modules with iOS-only actuals (for example Room in core/storage) are unaffected.
    macosArm64()
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.datetime)
        implementation(libs.kotlinx.atomicfu)
        // @Immutable, so Compose can skip the stats cards when their numbers do not change.
        implementation(libs.compose.runtime.annotation)
    }
}
