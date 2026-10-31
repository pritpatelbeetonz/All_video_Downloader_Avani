package app.messages.attribution

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Decides once per install where the user came from: google / facebook / unity / regular.
 * Runs in the background from MessagesApplication.onCreate and never blocks startup.
 *
 * Order (first match wins):
 *   0. isEnabled off in Remote Config         -> regular, nothing saved (checked again next launch)
 *   1. Source already saved                   -> reused, no checks
 *   2. Installed before this detection existed -> regular (src_existing_user)
 *   3. Google   (GoogleSourceDetector)        -> google   (src_from_google)
 *   4. Facebook (FacebookSourceDetector)      -> facebook (src_from_facebook)
 *   5. Unity    (UnitySourceDetector)         -> unity    (src_from_unity)
 *   6. Nothing matched, or the check timed out -> regular (src_organic)
 *
 * The decision is saved in prefs, set as the GA4 user property "install_source" and logged once
 * as a src_* event. What the app does for each source is not decided yet: see [onSourceDecided].
 *
 * Logcat filter: tag:InstallSource
 */
object InstallSourceManager {

    const val GOOGLE = "google"
    const val FACEBOOK = "facebook"
    const val UNITY = "unity"
    const val REGULAR = "regular"
    private val AD_SOURCES = setOf(GOOGLE, FACEBOOK, UNITY)

    private const val PREFS_NAME = "install_source"
    private const val KEY_SOURCE = "source"
    private const val KEY_MATCHED_BY = "matched_by"
    /** Set on the first launch with this detection: false for users who installed an older version. */
    private const val KEY_ELIGIBLE = "eligible"

    private const val REMOTE_CONFIG_WAIT_MS = 5000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private var started = false

    private val _source = MutableStateFlow<String?>(null)
    /** The decided source, or null until the first-launch check has finished. */
    val source: StateFlow<String?> = _source.asStateFlow()

    /** The decided source, "regular" until decided. */
    fun currentSource(): String = _source.value ?: REGULAR

    /** True for a decided google / facebook / unity install. */
    fun isAdInstall(): Boolean = _source.value in AD_SOURCES

    fun init(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        markEligibility()

        val saved = prefs.getString(KEY_SOURCE, null)
        if (saved != null) {
            AttributionLog.d("💾 Source from prefs: $saved (matched_by=${prefs.getString(KEY_MATCHED_BY, null)}). No checks")
            _source.value = saved
            setUserProperty(saved)
            onSourceDecided(saved, fromPrefs = true)
            return
        }

        scope.launch {
            val config = loadConfig()
            if (!config.isEnabled) {
                AttributionLog.d("⛔ Detection off (isEnabled=false). Regular, nothing saved")
                return@launch
            }
            AttributionLog.d("🚀 Install source check started")
            val finished = withTimeoutOrNull(config.referrerCheckTimeoutSec * 1000L) { runChecks(config) }
            if (finished == null) {
                AttributionLog.w("⏱️ Check took longer than ${config.referrerCheckTimeoutSec}s")
                decide(REGULAR, "src_organic", "timeout")
            }
        }
    }

    private suspend fun runChecks(config: AttributionConfig) {
        if (!prefs.getBoolean(KEY_ELIGIBLE, false)) {
            AttributionLog.d("👴 Installed before this detection existed")
            decide(REGULAR, "src_existing_user", "existing_user")
            return
        }

        // Read once; used by all three detectors
        val referrer = if (config.isCheckGoogleSource || config.isCheckFacebookSource || config.isCheckUnitySource) {
            PlayReferrer.read(appContext, config.playReferrerTimeoutSec)
        } else {
            PlayReferrer(PlayReferrer.Status.UNKNOWN)
        }

        if (config.isCheckGoogleSource) {
            GoogleSourceDetector.detect(referrer, config)?.let {
                decide(GOOGLE, "src_from_google", it)
                return
            }
        } else {
            AttributionLog.d("🔵 [Google] ⏭️ Off in Remote Config")
        }

        if (config.isCheckFacebookSource) {
            FacebookSourceDetector.detect(appContext, referrer, config)?.let {
                decide(FACEBOOK, "src_from_facebook", it)
                return
            }
        } else {
            AttributionLog.d("🟣 [Facebook] ⏭️ Off in Remote Config")
        }

        if (config.isCheckUnitySource) {
            UnitySourceDetector.detect(referrer, config)?.let {
                decide(UNITY, "src_from_unity", "marker:$it")
                return
            }
        } else {
            AttributionLog.d("🟢 [Unity] ⏭️ Off in Remote Config")
        }

        decide(REGULAR, "src_organic", if (referrer.isOk) "no_match" else "referrer_${referrer.status.name.lowercase()}")
    }

    /** Saves the source (once), sets the GA4 user property and logs its event. */
    private fun decide(source: String, event: String, matchedBy: String) {
        if (_source.value != null) return
        AttributionLog.d("🎯 DECIDED: $source ($event, matched_by=$matchedBy)")
        prefs.edit().putString(KEY_SOURCE, source).putString(KEY_MATCHED_BY, matchedBy).apply()
        _source.value = source
        setUserProperty(source)
        FirebaseAnalytics.getInstance(appContext).logEvent(event, Bundle().apply { putString("matched_by", matchedBy) })
        onSourceDecided(source, fromPrefs = false)
    }

    /**
     * Called once per launch when the source is known (saved earlier, or just decided).
     * TODO: what to do for each source is decided later; for now this only logs.
     */
    private fun onSourceDecided(source: String, fromPrefs: Boolean) {
        when (source) {
            GOOGLE -> AttributionLog.d("🔵 Google ad user${if (fromPrefs) " (saved)" else ""}. No action yet")
            FACEBOOK -> AttributionLog.d("🟣 Facebook ad user${if (fromPrefs) " (saved)" else ""}. No action yet")
            UNITY -> AttributionLog.d("🟢 Unity ad user${if (fromPrefs) " (saved)" else ""}. No action yet")
            else -> AttributionLog.d("⚪ Regular user${if (fromPrefs) " (saved)" else ""}. No action yet")
        }
    }

    // Before the source can ever be saved, so a user from an older version is never checked later
    private fun markEligibility() {
        if (prefs.contains(KEY_ELIGIBLE)) return
        val eligible = try {
            val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            info.firstInstallTime == info.lastUpdateTime
        } catch (e: Exception) {
            false
        }
        AttributionLog.d(if (eligible) "🆕 Fresh install: eligible for detection" else "👴 Updated from an older version: not eligible")
        prefs.edit().putBoolean(KEY_ELIGIBLE, eligible).apply()
    }

    // Waits up to 5 s for a fetch so the first launch doesn't decide on an empty config
    private suspend fun loadConfig(): AttributionConfig {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val fetched = withTimeoutOrNull(REMOTE_CONFIG_WAIT_MS) {
            suspendCancellableCoroutine<Boolean> { cont ->
                remoteConfig.fetchAndActivate().addOnCompleteListener { if (cont.isActive) cont.resume(it.isSuccessful) }
            }
        }
        AttributionLog.d(
            when (fetched) {
                true -> "☁️ Remote Config fetched"
                false -> "⚠️ Remote Config fetch failed; cached/default values used"
                null -> "⏱️ Remote Config fetch timed out; cached/default values used"
            }
        )
        val raw = remoteConfig.getString(AttributionConfig.REMOTE_CONFIG_KEY)
        AttributionLog.d("⚙️ ${AttributionConfig.REMOTE_CONFIG_KEY} = ${raw.ifBlank { "(empty, defaults)" }}")
        return AttributionConfig.fromJson(raw)
    }

    private fun setUserProperty(source: String) =
        FirebaseAnalytics.getInstance(appContext).setUserProperty("install_source", source)
}
