package app.messages.attribution

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import app.messages.BuildConfig
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decides once per install where the user came from: google / facebook / unity / regular.
 *
 * [init] (MessagesApplication.onCreate) only loads a source saved on an earlier launch. The check
 * itself is started by SplashActivity through [checkFromSplash], once the splash's own Remote
 * Config fetch has finished, so it uses those freshly fetched values; the splash waits for the
 * result (capped) to pick the onboarding flow.
 *
 * Order (first match wins):
 *   0. isShowRegularToAll on in Remote Config -> regular, nothing saved (src_regular_to_all, every launch)
 *   1. Source already saved                   -> reused, no checks
 *   2. Installed before this detection existed -> regular (src_existing_user)
 *   3. Firestore has this phone (reinstall)   -> its earlier ad source (src_<source>_from_firestore)
 *   4. isSimCheckAllow && no SIM card         -> regular  (src_no_sim; release builds only)
 *   5. Country in blockedCountriesForFacebookContent -> regular (src_blocked_country_<CC>)
 *   6. Google   (GoogleSourceDetector)        -> google   (src_from_google)
 *   7. Facebook (FacebookSourceDetector)      -> facebook (src_from_facebook; off by default)
 *   8. Unity    (UnitySourceDetector)         -> unity    (src_from_unity; off by default)
 *   9. Nothing matched, or the check timed out -> regular (src_organic)
 *
 * The decision is saved in prefs, set as the GA4 user property "install_source" and logged once
 * as a src_* event. Ad sources are also written to Firestore (FirestoreSourceStore) so a reinstall
 * keeps them; a failed write is retried on later launches.
 * What the app does for each source is not decided yet: see [onSourceDecided].
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
    /** An ad source not yet written to Firestore. */
    private const val KEY_PENDING = "pending_sync"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private var started = false
    private val checkStarted = AtomicBoolean(false)
    /** Completed when this launch knows its source: saved earlier, just decided, or isShowRegularToAll. */
    private val decided = CompletableDeferred<Unit>()

    private val _source = MutableStateFlow<String?>(null)
    /** The saved source, or null until the first-launch check has finished. Ignores isShowRegularToAll. */
    val source: StateFlow<String?> = _source.asStateFlow()

    /** isShowRegularToAll was on at this launch: everyone is treated as regular. */
    @Volatile
    private var regularToAll = false

    /** What the app acts on: "regular" while isShowRegularToAll is on or before the source is decided. */
    fun currentSource(): String = if (regularToAll) REGULAR else _source.value ?: REGULAR

    /** True for a decided google / facebook / unity install while isShowRegularToAll is off. */
    fun isAdInstall(): Boolean = currentSource() in AD_SOURCES

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
            // Later launches don't wait for a fetch: the values activated on an earlier launch decide
            if (AttributionConfig.fromJson(cachedConfigJson()).isShowRegularToAll) {
                applyRegularToAll()
            } else {
                setUserProperty(saved)
                onSourceDecided(saved, fromPrefs = true)
            }
            // An ad source whose Firestore write failed earlier is written now
            if (prefs.getBoolean(KEY_PENDING, false)) {
                scope.launch { syncToFirestore(FirestoreSourceStore.deviceKey(appContext)) }
            }
            decided.complete(Unit)
            return
        }
        AttributionLog.d("⏳ No saved source. The splash runs the check once Remote Config is fetched")
    }

    /**
     * Called by SplashActivity once its Remote Config fetch has finished (or given up), so the
     * check uses those values. [onDone] runs on the main thread with [currentSource] as soon as the
     * source is known, or after [maxWaitMs] ("regular" then; the check keeps going in the
     * background and saves its result for later launches).
     */
    fun checkFromSplash(maxWaitMs: Long, onDone: (String) -> Unit) {
        if (!decided.isCompleted && checkStarted.compareAndSet(false, true)) startCheck()
        scope.launch {
            val inTime = withTimeoutOrNull(maxWaitMs) { decided.await() } != null
            if (!inTime) AttributionLog.w("⏱️ Not decided within ${maxWaitMs}ms. Splash continues as ${currentSource()}")
            withContext(Dispatchers.Main) {
                AttributionLog.d("🚦 Splash continues with source=${currentSource()}")
                onDone(currentSource())
            }
        }
    }

    private fun startCheck() {
        val raw = cachedConfigJson()
        AttributionLog.d("⚙️ ${AttributionConfig.REMOTE_CONFIG_KEY} = ${raw.ifBlank { "(empty, defaults)" }}")
        val config = AttributionConfig.fromJson(raw)
        if (config.isShowRegularToAll) {
            applyRegularToAll()
            return
        }
        scope.launch {
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

        // Reinstall: this phone was already decided as an ad install
        var deviceKey: String? = null
        if (config.isFirestoreEnabled) {
            deviceKey = FirestoreSourceStore.deviceKey(appContext)
            if (deviceKey == null) {
                AttributionLog.w("🔥 [Firestore] ⏭️ No device key; lookup skipped")
            } else {
                try {
                    val src = FirestoreSourceStore.read(deviceKey, config.firestoreReadTimeoutSec)
                    if (src in AD_SOURCES) {
                        decide(src!!, "src_${src}_from_firestore", "firestore")
                        return
                    }
                } catch (e: Exception) {
                    AttributionLog.w("🔥 [Firestore] ⚠️ Lookup failed; carrying on with the checks", e)
                }
            }
        } else {
            AttributionLog.d("🔥 [Firestore] ⏭️ Off in Remote Config")
        }

        // SIM card (release builds only, as in the reference; null = the check failed, the other checks decide)
        if (config.isSimCheckAllow && !BuildConfig.DEBUG) {
            val sim = DeviceChecks.hasSimCard(appContext)
            AttributionLog.d("📶 [SIM] hasSimCard=$sim")
            if (sim == false) {
                decide(REGULAR, "src_no_sim", "no_sim")
                return
            }
            if (sim == null) logEvent("src_sim_check_failed")
        } else {
            AttributionLog.d("📶 [SIM] ⏭️ ${if (config.isSimCheckAllow) "Debug build" else "Off in Remote Config"}")
        }

        // Blocked country
        if (config.blockedCountries.isNotEmpty()) {
            val country = DeviceChecks.countryCode(appContext)
            AttributionLog.d("🌍 [Country] $country, blocked=${config.blockedCountries}")
            if (country != null && country in config.blockedCountries) {
                decide(REGULAR, "src_blocked_country_$country", "blocked_country:$country")
                return
            }
            if (country == null) logEvent("src_country_check_failed")
        } else {
            AttributionLog.d("🌍 [Country] ⏭️ No blocked countries")
        }

        // Read once; used by all three detectors
        val referrer = if (config.isCheckGoogleSource || config.isCheckFacebookSource || config.isCheckUnitySource) {
            PlayReferrer.read(appContext, config.playReferrerTimeoutSec)
        } else {
            PlayReferrer(PlayReferrer.Status.UNKNOWN)
        }

        if (config.isCheckGoogleSource) {
            GoogleSourceDetector.detect(referrer, config)?.let {
                decide(GOOGLE, "src_from_google", it, deviceKey)
                return
            }
        } else {
            AttributionLog.d("🔵 [Google] ⏭️ Off in Remote Config")
        }

        if (config.isCheckFacebookSource) {
            FacebookSourceDetector.detect(appContext, referrer, config)?.let {
                decide(FACEBOOK, "src_from_facebook", it, deviceKey)
                return
            }
        } else {
            AttributionLog.d("🟣 [Facebook] ⏭️ Off in Remote Config")
        }

        if (config.isCheckUnitySource) {
            UnitySourceDetector.detect(referrer, config)?.let {
                decide(UNITY, "src_from_unity", "marker:$it", deviceKey)
                return
            }
        } else {
            AttributionLog.d("🟢 [Unity] ⏭️ Off in Remote Config")
        }

        decide(REGULAR, "src_organic", if (referrer.isOk) "no_match" else "referrer_${referrer.status.name.lowercase()}")
    }

    /**
     * Saves the source (once), sets the GA4 user property and logs its event. An ad source with a
     * [deviceKey] is written to Firestore (one found in Firestore passes none: it's already there).
     */
    private fun decide(source: String, event: String, matchedBy: String, deviceKey: String? = null) {
        if (_source.value != null) return
        AttributionLog.d("🎯 DECIDED: $source ($event, matched_by=$matchedBy)")
        prefs.edit()
            .putString(KEY_SOURCE, source)
            .putString(KEY_MATCHED_BY, matchedBy)
            .putBoolean(KEY_PENDING, source in AD_SOURCES && deviceKey != null)
            .apply()
        _source.value = source
        setUserProperty(source)
        logEvent(event, Bundle().apply { putString("matched_by", matchedBy) })
        onSourceDecided(source, fromPrefs = false)
        decided.complete(Unit)
        syncToFirestore(deviceKey)
    }

    // Writes a pending ad source; the flag is cleared only once the server has it
    private fun syncToFirestore(deviceKey: String?) {
        if (deviceKey == null || !prefs.getBoolean(KEY_PENDING, false)) return
        val source = prefs.getString(KEY_SOURCE, null)
        if (source !in AD_SOURCES) return
        FirestoreSourceStore.write(deviceKey, source!!) { ok ->
            if (ok) prefs.edit().putBoolean(KEY_PENDING, false).apply()
        }
    }

    /**
     * Emergency switch (reference app's isShowRegularToAll): everyone is regular, no checks run and
     * nothing new is saved. A source saved earlier is kept and applies again once the switch is off.
     */
    private fun applyRegularToAll() {
        regularToAll = true
        AttributionLog.d("🟦 isShowRegularToAll on. Regular user, source checks skipped")
        setUserProperty(REGULAR)
        logEvent("src_regular_to_all")
        onSourceDecided(REGULAR, fromPrefs = _source.value != null)
        decided.complete(Unit)
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

    /** The currently activated value; "" before any fetch has ever been activated. */
    private fun cachedConfigJson(): String =
        FirebaseRemoteConfig.getInstance().getString(AttributionConfig.REMOTE_CONFIG_KEY)

    private fun logEvent(event: String, params: Bundle? = null) {
        AttributionLog.d("📊 Event: $event ${params ?: ""}")
        FirebaseAnalytics.getInstance(appContext).logEvent(event, params)
    }

    private fun setUserProperty(source: String) =
        FirebaseAnalytics.getInstance(appContext).setUserProperty("install_source", source)
}
