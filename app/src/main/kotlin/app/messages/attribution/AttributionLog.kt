package app.messages.attribution

import android.util.Log
import app.messages.BuildConfig

/**
 * One log tag for the whole install-source detection, so a single Logcat filter shows every step:
 *
 *     tag:InstallSource
 *
 * Debug builds only; release builds log nothing.
 */
internal object AttributionLog {

    const val TAG = "InstallSource"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun w(message: String, e: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, message, e)
    }
}
