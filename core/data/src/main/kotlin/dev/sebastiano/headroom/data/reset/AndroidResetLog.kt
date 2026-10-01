package dev.sebastiano.headroom.data.reset

import android.util.Log
import dev.sebastiano.headroom.quota.ResetLog

/**
 * Writes the reset clients' lines to logcat. Read them with `adb logcat -s HeadroomResets`; see
 * docs/RESETS.md. The lines carry no secrets: see [ResetLog].
 */
public object AndroidResetLog : ResetLog {
    public const val TAG: String = "HeadroomResets"

    override fun debug(message: String) {
        Log.d(TAG, message)
    }

    override fun warn(message: String) {
        Log.w(TAG, message)
    }
}
