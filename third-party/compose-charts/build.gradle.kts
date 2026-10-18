// Compose Charts (https://github.com/c5inco/compose-charts), vendored: it has no Maven release yet,
// and CI must not depend on mavenLocal or an included build. See README.md for the upstream commit
// and how to update.
plugins {
    alias(libs.plugins.headroom.android.library)
    alias(libs.plugins.headroom.android.compose)
}

android {
    // Upstream's own namespace (it has no resources, so nothing else depends on it).
    namespace = "dev.c5inco.charts.compose"
}
