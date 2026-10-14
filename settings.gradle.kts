pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "headroom"

include(
    ":app",
    ":core:model",
    ":core:quota",
    ":core:auth",
    ":core:data",
    ":core:storage",
    ":shared",
    ":core:designsystem",
    ":widget",
)
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
