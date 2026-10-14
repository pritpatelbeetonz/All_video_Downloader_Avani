package app.messages.monetization.ads

import app.messages.core.ads.AdDecision
import app.messages.core.ads.AdEntryPoint
import app.messages.core.ads.AdRules
import app.messages.core.ads.AdSurface
import app.messages.core.ads.AdsPolicy
import app.messages.core.prefs.AppPrefs
import app.messages.di.ApplicationScope
import app.messages.monetization.iap.EntitlementController
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

/**
 * The single gate every ad placement flows through (build doc §6.2). Wraps the
 * pure [AdsPolicy] with the app's live state — premium entitlement, the remote
 * kill-switch, and the session/frequency counters — so feature code never
 * reasons about ad rules directly.
 *
 * Ships effectively OFF: [AdsRemoteConfig] defaults to disabled, so every
 * decision here returns "no ad" until ads are deliberately ramped on.
 */
@Singleton
class AdsController @Inject constructor(
    private val entitlement: EntitlementController,
    private val remoteConfig: AdsRemoteConfig,
    private val prefs: AppPrefs,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /**
     * Whether the conversation list should interleave native "Sponsored" tiles.
     *
     * Reads [app.messages.config.AdsConfigManager]'s `isNativeOn` — the same switch
     * every other native placement in the app already uses. It previously read
     * [AdsRemoteConfig.adsEnabled], a second, independent flag that defaults false
     * and stays false while the unit IDs are the "11" placeholder, so the tile never
     * appeared even with ads fully configured elsewhere.
     */
    val nativeListAdsEnabled: StateFlow<Boolean> =
        combine(
            entitlement.isPremium,
            app.messages.config.AdsConfigManager.configFlow,
            prefs.sessionCount,
        ) { premium, config, session ->
            AdsPolicy.canShowNativeListAds(premium, config.isNativeOn, session)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    /** Adaptive banner eligibility for secondary screens (§6.2 A5). */
    val bannerAdsEnabled: StateFlow<Boolean> =
        combine(entitlement.isPremium, remoteConfig.adsEnabled) { _, enabled ->
            enabled
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    /** Content positions after which to insert a native ad tile. */
    fun nativeAdSlots(contentCount: Int): List<Int> =
        AdsPolicy.nativeAdContentSlots(contentCount)

    /** Hard surface guard — the competitor-killing decision (ThreadScreen ad-free). */
    fun isSurfaceAllowed(surface: AdSurface): Boolean = AdsPolicy.isSurfaceAllowed(surface)

    /** Decide whether an app-open ad may show for this entry point, reading live caps. */
    suspend fun appOpenDecision(entryPoint: AdEntryPoint, nowMs: Long): AdDecision {
        val enabled = remoteConfig.adsEnabled.value
        val session = prefs.sessionCount.first()
        val lastShown = prefs.lastAppOpenAdAt.first()
        val shownToday = prefs.appOpenShownToday(nowMs)
        return AdsPolicy.appOpenDecision(
            premium = false,
            adsEnabled = enabled,
            sessionCount = session,
            entryPoint = entryPoint,
            lastShownAtMs = lastShown,
            shownTodayCount = shownToday,
            nowMs = nowMs,
        )
    }

    /** Records that an app-open ad was shown, for cap accounting. */
    suspend fun recordAppOpenShown(nowMs: Long) {
        prefs.setLastAppOpenAdAt(nowMs)
        prefs.incrementAppOpenShownToday(nowMs)
    }

    /**
     * Splash interstitial gate (cold start). Unlike the app-open ad there is **no
     * session floor**, but it shares the app-open daily cap so the two full-screen
     * formats don't over-serve. Recorded via [recordAppOpenShown], so showing it
     * also blocks the foreground app-open for the 4h interval (the two never stack).
     *
     * The old "not during onboarding" clause is gone with the onboarding surface
     * itself — keeping it would have pinned this gate closed forever, since nothing
     * sets `hasSeenOnboarding` any more.
     */
    suspend fun splashInterstitialAllowed(nowMs: Long): Boolean {
        // Ads always show — premium no longer removes ads
        if (!remoteConfig.adsEnabled.value) return false
        return prefs.appOpenShownToday(nowMs) < AdRules.APP_OPEN_MAX_PER_DAY
    }
}
