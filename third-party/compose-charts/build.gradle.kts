// Compose Charts (https://github.com/c5inco/compose-charts), pinned to an upstream commit.
// See README.md for the source layout and how to update.
plugins {
    alias(libs.plugins.headroom.android.library)
    alias(libs.plugins.headroom.android.compose)
}

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    // Upstream's own namespace (it has no resources, so nothing else depends on it).
    namespace = "dev.c5inco.charts.compose"
    sourceSets.getByName("main").kotlin.srcDir("src/commonMain/kotlin")
}

// Only the platform hooks need expect/actual linking. Compile the renderer and JVM core together
// as Android sources, retaining upstream's source layout and contents unchanged.
kotlin.compilerOptions {
    freeCompilerArgs.add("-Xmulti-platform")
    freeCompilerArgs.add(
        "-Xcommon-sources=" +
            file("src/commonMain/kotlin/dev/c5inco/charts/compose/Platform.kt").absolutePath
    )
}
