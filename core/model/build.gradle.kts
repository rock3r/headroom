plugins { alias(libs.plugins.headroom.kmp.library) }

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.datetime)
        implementation(libs.kotlinx.atomicfu)
    }
}
