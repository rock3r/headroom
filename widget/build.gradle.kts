plugins {
    alias(libs.plugins.headroom.android.library)
    alias(libs.plugins.headroom.android.compose)
}

android { namespace = "dev.sebastiano.headroom.widget" }

dependencies {
    implementation(projects.core.model)
    implementation(libs.remote.creation.compose)
    implementation(libs.remote.creation.core)
    implementation(libs.remote.core)
}

// Records the widget pictures used in the README into docs/screenshots/widgets.
val widgetGalleryDir =
    rootProject.layout.projectDirectory.dir("docs/screenshots/widgets").asFile.absolutePath

tasks.register<Test>("recordWidgetGallery") {
    description = "Plays each widget in the Android 16 widget player and saves it as a PNG."
    group = "documentation"
    val unitTest = tasks.named<Test>("testDebugUnitTest").get()
    testClassesDirs = unitTest.testClassesDirs
    classpath = unitTest.classpath
    jvmArgs(unitTest.allJvmArgs.filter { it.startsWith("--add-") })
    systemProperties(unitTest.systemProperties)
    systemProperty("headroom.widgetGalleryDir", widgetGalleryDir)
    filter { includeTestsMatching("*WidgetGalleryRecorder*") }
    outputs.upToDateWhen { false }
}
