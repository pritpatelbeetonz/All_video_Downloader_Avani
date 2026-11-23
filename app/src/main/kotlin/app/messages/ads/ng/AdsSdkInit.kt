package app.messages.ads.ng

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig

/**
 * Single initialization point for the Google Mobile Ads Next-Gen SDK (launcher and the
 * embedded Flutter downloader share it).
 *
 * The SDK must be initialized on a background thread before any ad request, so every load
 * goes through [whenReady]. Initialization is idempotent.
 */
object AdsSdkInit {

    private const val TAG = "ADS_SDK_INIT"
    private val TEST_DEVICE_IDS = listOf("A60DCA743B4F66FE88947C10E6F2A6FE")

    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private val pending = ArrayList<Runnable>()

    @Volatile private var started = false
    @Volatile private var ready = false

    /** Starts initialization if it has not started yet; [onDone] runs on the main thread. */
    @JvmStatic
    @JvmOverloads
    fun init(context: Context, onDone: Runnable? = null) {
        if (onDone != null) whenReady(context, onDone) else start(context.applicationContext)
    }

    /** Runs [action] on the main thread once the SDK is initialized (starting it if needed). */
    @JvmStatic
    fun whenReady(context: Context, action: Runnable) {
        if (ready) {
            runOnMain(action)
            return
        }
        synchronized(lock) {
            if (ready) {
                runOnMain(action)
                return
            }
            pending.add(action)
        }
        start(context.applicationContext)
    }

    @JvmStatic
    fun isReady(): Boolean = ready

    private fun start(appContext: Context) {
        synchronized(lock) {
            if (started) return
            started = true
        }
        Thread {
            try {
                val appId = readAppId(appContext)
                val requestConfig = RequestConfiguration.Builder()
                    .setTestDeviceIds(TEST_DEVICE_IDS)
                    .build()
                val config = InitializationConfig.Builder(appId)
                    .setRequestConfiguration(requestConfig)
                    .build()
                MobileAds.initialize(appContext, config) {
                    Log.d(TAG, "Next-Gen Mobile Ads SDK initialized")
                    markReady()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Mobile Ads initialization failed", e)
                // Release waiters so callers fall through to their own failure paths.
                markReady()
            }
        }.start()
    }

    private fun markReady() {
        val toRun: List<Runnable>
        synchronized(lock) {
            ready = true
            toRun = ArrayList(pending)
            pending.clear()
        }
        toRun.forEach { runOnMain(it) }
    }

    private fun runOnMain(action: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run() else main.post(action)
    }

    private fun readAppId(context: Context): String {
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        return info.metaData?.getString("com.google.android.gms.ads.APPLICATION_ID").orEmpty()
    }
}
