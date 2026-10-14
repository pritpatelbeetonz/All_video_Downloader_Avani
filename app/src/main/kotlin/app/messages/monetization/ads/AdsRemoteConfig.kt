package app.messages.monetization.ads

import app.messages.core.prefs.AppPrefs
import app.messages.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Remote kill-switch / rollout control for ads (build doc §8: ship v1.0 with ads
 * OFF, then ramp 10% → 50% → 100% via Remote Config while watching ratings and
 * D1/D7). Until Firebase Remote Config lands, this is backed by a **persisted**
 * DataStore flag ([AppPrefs.adsEnabledDebug]) so the choice survives relaunch —
 * required because the SDK only initializes on the ads-enabled-at-startup path.
 * Defaults to `BuildConfig.DEBUG` (debug builds show ads for testing, release OFF).
 */
interface AdsRemoteConfig {
    /** Master switch — ads never show while false. */
    val adsEnabled: StateFlow<Boolean>

    // ── Ad unit ids ──────────────────────────────────────────────────────────
    // The loadable unit ids, sourced from Remote Config so they can be swapped
    // without an app update. Each defaults to the [AdUnits] test unit, which is
    // also the fallback when Firebase hasn't fetched yet (or isn't wired). The
    // AdMob *app id* stays local ([AdUnits.APP_ID]) — it's needed to initialise
    // the SDK, before any Remote Config fetch can complete.
    val appOpenUnitId: String
    val interstitialUnitId: String
    val nativeUnitId: String
    val bannerUnitId: String

    fun setEnabledForDebug(value: Boolean)
}

@Singleton
class LocalAdsRemoteConfig @Inject constructor(
    private val prefs: AppPrefs,
    @ApplicationScope private val scope: CoroutineScope,
) : AdsRemoteConfig {
    // Mirror the persisted flag into a hot StateFlow so MainActivity's first-frame
    // `.value` read reflects it (DataStore emits well within the 2s bootstrap delay).
    private val _adsEnabled = MutableStateFlow(false)
    override val adsEnabled: StateFlow<Boolean> = _adsEnabled.asStateFlow()

    // Local build uses the hardcoded test units; the Firebase impl (later) overrides these.
    override val appOpenUnitId: String = AdUnits.APP_OPEN
    override val interstitialUnitId: String = AdUnits.INTERSTITIAL
    override val nativeUnitId: String = AdUnits.NATIVE_ADVANCED
    override val bannerUnitId: String = AdUnits.ADAPTIVE_BANNER

    init {
        scope.launch { prefs.adsEnabledDebug.collect { _adsEnabled.value = it } }
    }

    override fun setEnabledForDebug(value: Boolean) {
        scope.launch { prefs.setAdsEnabledDebug(value) }
    }
}
