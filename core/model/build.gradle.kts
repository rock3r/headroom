plugins { alias(libs.plugins.headroom.jvm.library) }

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)
}
