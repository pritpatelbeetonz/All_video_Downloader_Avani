package app.messages.utility

import android.content.Context
import android.util.Log
import app.messages.preferences.PreferenceHelper
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object OneSignalManager {
    private const val TAG = "OneSignalManager"
    private var isInitialized = false
    private var currentAppId: String? = null

    /**
     * Initializes OneSignal if not already initialized with the same appId.
     */
    fun initialize(context: Context, appId: String?) {
        if (appId.isNullOrBlank()) {
            return
        }

        val trimmedAppId = appId.trim()

        if (isInitialized && currentAppId == trimmedAppId) {
            return
        }

        try {
            val appContext = context.applicationContext ?: context
            
            // Save to preferences for quick cold boot init next time
            PreferenceHelper.getInstance(appContext).setOneSignalAppId(trimmedAppId)

            // Logging set to helpful level
            OneSignal.Debug.logLevel = LogLevel.WARN

            // OneSignal Initialization
            OneSignal.initWithContext(appContext, trimmedAppId)
            isInitialized = true
            currentAppId = trimmedAppId

            } catch (e: Exception) {
            }
    }

    /**
     * Explicitly requests notification permission for Android 13+ when appropriate.
     */
    fun requestNotificationPermission() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                OneSignal.Notifications.requestPermission(true)
            } catch (e: Exception) {
                }
        }
    }

    /**
     * Initializes OneSignal from cached App ID in preferences (called on App onCreate).
     */
    fun initFromCache(context: Context) {
        val cachedAppId = PreferenceHelper.getInstance(context).getOneSignalAppId()
        if (cachedAppId.isNotBlank()) {
            initialize(context, cachedAppId)
        }
    }
}
