package dev.sebastiano.headroom.data.reset

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesAttemptTargetStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `keeps the targets across instances, in their order`() {
        val targets = linkedMapOf("key-2|week" to "week", "key-1|five_hour" to "five_hour")

        SharedPreferencesAttemptTargetStore(context).save(targets)

        val loaded = SharedPreferencesAttemptTargetStore(context).load()
        assertEquals(targets, loaded)
        assertEquals(targets.keys.toList(), loaded.keys.toList())
    }

    @Test
    fun `an unreadable file reads as no targets`() {
        context
            .getSharedPreferences(SharedPreferencesAttemptTargetStore.FILE, Context.MODE_PRIVATE)
            .edit(commit = true) { putString(SharedPreferencesAttemptTargetStore.KEY, "not json") }

        assertTrue(SharedPreferencesAttemptTargetStore(context).load().isEmpty())
    }
}
