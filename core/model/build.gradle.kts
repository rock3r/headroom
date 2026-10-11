plugins { alias(libs.plugins.headroom.kmp.library) }

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.datetime)
        implementation(libs.kotlinx.atomicfu)
        // @Immutable, so Compose can skip the stats cards when their numbers do not change.
        implementation(libs.compose.runtime.annotation)
    }
}
