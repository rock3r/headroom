plugins { `kotlin-dsl` }

group = "dev.sebastiano.headroom.buildlogic"

kotlin { jvmToolchain(21) }

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.ktfmt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = "headroom.jvm.library"
            implementationClass = "dev.sebastiano.headroom.buildlogic.JvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "headroom.android.library"
            implementationClass = "dev.sebastiano.headroom.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "headroom.android.application"
            implementationClass =
                "dev.sebastiano.headroom.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = "headroom.android.compose"
            implementationClass = "dev.sebastiano.headroom.buildlogic.AndroidComposeConventionPlugin"
        }
    }
}
