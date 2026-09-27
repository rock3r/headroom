package dev.sebastiano.headroom

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Runs the instrumented tests against [E2eApplication]: demo data and a fixed clock. */
class HeadroomTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(classLoader, E2eApplication::class.java.name, context)
}
