package dev.sebastiano.headroom.data.reset

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `a write that does not reach the disk fails the save`() {
        val real =
            context.getSharedPreferences(
                SharedPreferencesAttemptTargetStore.FILE,
                Context.MODE_PRIVATE,
            )
        val failing =
            object : SharedPreferences by real {
                override fun edit(): SharedPreferences.Editor = FailingEditor()
            }

        assertFailsWith<IOException> {
            SharedPreferencesAttemptTargetStore(context, failing).save(mapOf("k|week" to "week"))
        }
    }

    /** An editor whose writes never reach the disk, as on an I/O error. */
    private class FailingEditor : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?) = this

        override fun putStringSet(key: String?, values: MutableSet<String>?) = this

        override fun putInt(key: String?, value: Int) = this

        override fun putLong(key: String?, value: Long) = this

        override fun putFloat(key: String?, value: Float) = this

        override fun putBoolean(key: String?, value: Boolean) = this

        override fun remove(key: String?) = this

        override fun clear() = this

        override fun commit(): Boolean = false

        override fun apply() = Unit
    }

    @Test
    fun `an unreadable file reads as no targets`() {
        context
            .getSharedPreferences(SharedPreferencesAttemptTargetStore.FILE, Context.MODE_PRIVATE)
            .edit(commit = true) { putString(SharedPreferencesAttemptTargetStore.KEY, "not json") }

        assertTrue(SharedPreferencesAttemptTargetStore(context).load().isEmpty())
    }
}
