package app.messages.core.ads

/**
 * Pure, side-effect-free ad-gating decisions per build doc §6.2. Every rule
 * that protects the user experience (and the 4.7★ rating) lives here as a
 * function of explicit inputs, so it can be exhaustively unit-tested without
 * Android, the network, or the ads SDK.
 *
 * The controller ([AdsController]) supplies the live state; this object only
 * decides. Nothing here loads or shows an ad.
 */
object AdsPolicy {

    /** Native list ads: never for premium, only when remotely enabled, and not
     *  in the first sessions (let users fall in love first — §6.2 A1). */
    fun canShowNativeListAds(
        premium: Boolean,
        adsEnabled: Boolean,
        sessionCount: Int,
    ): Boolean = !premium &&
        adsEnabled &&
        sessionCount >= AdRules.NATIVE_LIST_FIRST_ALLOWED_SESSION

    /**
     * Content positions after which a native ad tile is inserted: index 4,
     * then every 10 content tiles. Returns the indices into the *content* list
     * (the UI accounts for the shift as it interleaves). The every-10 spacing
     * inherently keeps ≤ ~2 visible per screenful (§6.2 A1).
     */
    fun nativeAdContentSlots(contentCount: Int): List<Int> {
        if (contentCount <= AdRules.NATIVE_LIST_FIRST_SLOT_INDEX) return emptyList()
        val slots = mutableListOf<Int>()
        var i = AdRules.NATIVE_LIST_FIRST_SLOT_INDEX
        while (i < contentCount) {
            slots += i
            i += AdRules.NATIVE_LIST_SLOT_INTERVAL
        }
        return slots
    }

    /**
     * App-open ad gate (§6.2 A2). Checks, in order: premium, remote enable,
     * session floor, prohibited entry points, daily cap, and the min interval
     * since the last app-open ad.
     */
    fun appOpenDecision(
        premium: Boolean,
        adsEnabled: Boolean,
        sessionCount: Int,
        entryPoint: AdEntryPoint,
        lastShownAtMs: Long?,
        shownTodayCount: Int,
        nowMs: Long,
    ): AdDecision {
        if (premium) return AdDecision.deny("premium")
        if (!adsEnabled) return AdDecision.deny("ads disabled")
        if (sessionCount < AdRules.APP_OPEN_FIRST_ALLOWED_SESSION) {
            return AdDecision.deny("early session")
        }
        if (entryPoint in AdRules.appOpenProhibitedEntries) {
            return AdDecision.deny("prohibited entry: $entryPoint")
        }
        if (shownTodayCount >= AdRules.APP_OPEN_MAX_PER_DAY) {
            return AdDecision.deny("daily cap")
        }
        if (lastShownAtMs != null) {
            val sinceLast = nowMs - lastShownAtMs
            if (sinceLast < AdRules.appOpenMinInterval.inWholeMilliseconds) {
                return AdDecision.deny("min interval")
            }
        }
        return AdDecision.ALLOW
    }

    /** Task-completion interstitial gate (§6.2 A3). */
    fun canShowInterstitial(
        premium: Boolean,
        adsEnabled: Boolean,
        shownThisSession: Int,
        shownToday: Int,
    ): Boolean = !premium &&
        adsEnabled &&
        shownThisSession < AdRules.INTERSTITIAL_MAX_PER_SESSION &&
        shownToday < AdRules.INTERSTITIAL_MAX_PER_DAY

    /** Any-ad surface prohibition — ThreadScreen etc. never carry an ad. */
    fun isSurfaceAllowed(surface: AdSurface): Boolean =
        surface !in AdRules.prohibitedSurfaces

    /** Two ads must never appear back-to-back within [AdRules.anyAdMinGap]. */
    fun respectsGlobalGap(lastAnyAdAtMs: Long?, nowMs: Long): Boolean {
        if (lastAnyAdAtMs == null) return true
        return nowMs - lastAnyAdAtMs >= AdRules.anyAdMinGap.inWholeMilliseconds
    }
}

/** A gate decision with a human-readable reason (logged, never user-facing). */
data class AdDecision(val allowed: Boolean, val reason: String) {
    companion object {
        val ALLOW = AdDecision(true, "ok")
        fun deny(reason: String) = AdDecision(false, reason)
    }
}
