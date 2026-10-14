package app.messages.monetization.ads

import app.messages.di.ApplicationScope
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class NativeAdManager @Inject constructor(
    private val remoteConfig: AdsRemoteConfig,
    @ApplicationScope private val scope: CoroutineScope,
) {
    companion object {
        const val MAX_LIVE_ADS = 2
    }

    private val _ad = MutableStateFlow<NativeAd?>(null)
    val ad: StateFlow<NativeAd?> = _ad.asStateFlow()
    private var loading = false

    private val nativeUnitId: String
        get() = app.messages.config.AdsConfigManager.config.nativeAdIdChatScreen
            .takeIf { it.isNotBlank() && it != "11" }
            ?: app.messages.config.AdsConfigManager.config.nativeAdIdDashboard
            .takeIf { it.isNotBlank() && it != "11" }
            ?: remoteConfig.nativeUnitId

    // 1. Current displayed ad and its slot ordinal
    private val _currentAdSlot = MutableStateFlow<Pair<Int, NativeAd>?>(null)

    // 2. Exactly 1 preloaded next ad waiting in RAM
    private val _nextAd = MutableStateFlow<NativeAd?>(null)

    // Exposed Map for UI consumption: activeSlotOrdinal -> NativeAd
    private val _activeSlotMap = MutableStateFlow<Map<Int, NativeAd>>(emptyMap())
    val activeSlotMap: StateFlow<Map<Int, NativeAd>> = _activeSlotMap.asStateFlow()

    // In-flight network request count (strictly <= 1)
    private var loadingCount = 0

    // Target slot ordinal waiting for the current in-flight fetch (if any)
    private var pendingTargetSlot: Int? = null

    // Ads currently attached to a live NativeAdView
    private val activeBoundAds = ConcurrentHashMap.newKeySet<NativeAd>()

    // Old retired ads awaiting NativeAdView unbind before destruction
    private val retiredAds = ConcurrentHashMap.newKeySet<NativeAd>()

    // Track distinct ads that have been rendered on screen
    private val recordedShownAds = ConcurrentHashMap.newKeySet<NativeAd>()

    // Analytics Counters
    private var totalRequested = 0
    private var totalFilled = 0
    private var totalFailed = 0
    private var totalShown = 0

    private var isRefreshing = false

    private fun totalLiveAds(): Int {
        val current = if (_currentAdSlot.value != null) 1 else 0
        val next = if (_nextAd.value != null) 1 else 0
        return current + next + loadingCount
    }

    private fun logScorecard(event: String) {
        val fillRate = if (totalRequested > 0) String.format(java.util.Locale.US, "%.1f%%", totalFilled * 100.0 / totalRequested) else "0.0%"
        val showRate = if (totalFilled > 0) String.format(java.util.Locale.US, "%.1f%%", totalShown * 100.0 / totalFilled) else "0.0%"
        val current = if (_currentAdSlot.value != null) 1 else 0
        val next = if (_nextAd.value != null) 1 else 0

        android.util.Log.d(
            "ChatAds",
            "📈 [Scorecard] Event: $event | Req: $totalRequested | Fill: $totalFilled | Shown: $totalShown | ShowRate: $showRate | RAM: (curr=$current, next=$next, load=$loadingCount)"
        )
    }

    private fun logState(action: String) {
        logScorecard(action)
    }

    fun ensureLoaded() {
        if (_ad.value != null || loading) return
        loading = true
        scope.launch {
            withContext(Dispatchers.Main) {
                val adLoader = AdLoader.Builder(
                    app.messages.Controller.getInstance(),
                    nativeUnitId
                ).forNativeAd { nativeAd ->
                    _ad.value = nativeAd
                    loading = false
                }.withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loading = false
                    }
                }).build()
                adLoader.loadAd(AdRequest.Builder().build())
            }
        }
    }

    /**
     * Called when scrolling settles (isScrollInProgress == false) on a visible ad position.
     */
    fun onSlotSettled(slotOrdinal: Int) {
        // 1. If user is already viewing this exact slot, keep current ad displayed without replacing
        if (_currentAdSlot.value?.first == slotOrdinal) return

        // 2. If a preloaded next ad is ready in RAM, promote it to active slot immediately
        val preloaded = _nextAd.value
        if (preloaded != null) {
            retireCurrentAd()
            _nextAd.value = null
            _currentAdSlot.value = Pair(slotOrdinal, preloaded)
            _activeSlotMap.value = mapOf(slotOrdinal to preloaded)
            android.util.Log.d("ChatAds", "🎯 [ChatAds] PROMOTED PRELOADED AD ──> Active Slot #$slotOrdinal (Headline: \"${preloaded.headline}\")")
            logState("PROMOTED PRELOADED AD")

            // Exactly 1 fetch to replenish the preloaded buffer
            preloadNextIfNeeded()
            return
        }

        // 3. If no ad is currently displayed at all and none loading, fetch Ad A for this slot
        if (_currentAdSlot.value == null && loadingCount == 0) {
            pendingTargetSlot = slotOrdinal
            fetchSingleAd(forSlot = slotOrdinal)
            return
        }

        // 4. If currentAd belongs to an older slot and no nextAd is ready yet, fetch 1 fresh ad for this new slot
        if (loadingCount == 0 && totalLiveAds() < MAX_LIVE_ADS) {
            pendingTargetSlot = slotOrdinal
            fetchSingleAd(forSlot = slotOrdinal)
        } else {
            pendingTargetSlot = slotOrdinal
            logState("TARGET SLOT UPDATED TO #$slotOrdinal (WAITING FOR FETCH)")
        }
    }

    /**
     * Called when the user returns to the Chats/SMS screen after swiping away.
     * Strictly enforces the 1-in-flight request rule:
     * 1. If preloaded buffer is ready in RAM, promotes it immediately to the active slot.
     *    THEN triggers exactly 1 background fetch to replenish the buffer for the next return.
     * 2. If no preloaded buffer is ready and no fetch is in-flight, triggers on-demand fetch.
     */
    fun onScreenReturned(slotOrdinal: Int = 0) {
        val preloaded = _nextAd.value
        if (preloaded != null) {
            retireCurrentAd()
            _nextAd.value = null
            _currentAdSlot.value = Pair(slotOrdinal, preloaded)
            _activeSlotMap.value = mapOf(slotOrdinal to preloaded)
            android.util.Log.d("ChatAds", "🎯 [ChatAds] SWIPE RETURN ──> Promoted Preloaded Ad to Slot #$slotOrdinal (Headline: \"${preloaded.headline}\")")
            logState("SWIPE RETURN: PROMOTED PRELOADED AD")

            // Strictly ONLY AFTER promoting the current ad do we request the next buffer (1 at a time)
            preloadNextIfNeeded()
        } else if (_currentAdSlot.value == null || _currentAdSlot.value?.first != slotOrdinal) {
            if (loadingCount == 0 && totalLiveAds() < MAX_LIVE_ADS) {
                pendingTargetSlot = slotOrdinal
                fetchSingleAd(forSlot = slotOrdinal)
            } else {
                pendingTargetSlot = slotOrdinal
            }
        }
    }

    private fun retireCurrentAd() {
        val oldAd = _currentAdSlot.value?.second ?: return
        if (activeBoundAds.contains(oldAd)) {
            retiredAds.add(oldAd)
            android.util.Log.d("ChatAds", "⏳ [ChatAds] Staged previous on-screen ad for view release: \"${oldAd.headline}\"")
        } else {
            runCatching { oldAd.destroy() }
            android.util.Log.d("ChatAds", "🧹 [ChatAds] Immediately destroyed off-screen previous ad: \"${oldAd.headline}\"")
        }
    }

    private fun preloadNextIfNeeded() {
        if (_nextAd.value == null && totalLiveAds() < MAX_LIVE_ADS && _currentAdSlot.value != null && loadingCount == 0) {
            android.util.Log.w("ChatAds", "⚠️ [ChatAds] PRELOAD_NEXT_TRIGGERED: Screen already has an active ad in Slot #${_currentAdSlot.value?.first}. Requesting buffer ad for next slot (Risk: If user doesn't scroll down, this ad is wasted!)")
            fetchSingleAd(forSlot = null)
        }
    }

    private fun fetchSingleAd(forSlot: Int?) {
        if (totalLiveAds() >= MAX_LIVE_ADS) return
        totalRequested++
        loadingCount++
        val label = if (forSlot != null) "Active Slot #$forSlot" else "Next Preload Buffer"
        android.util.Log.d("ChatAds", "📡 [ChatAds] AD REQUEST STARTED ──> Target: $label | Unit ID: $nativeUnitId")
        logState("FETCH STARTED ($label)")

        scope.launch(Dispatchers.Main) {
            val context = app.messages.Controller.getInstance()
            val adLoader = AdLoader.Builder(context, nativeUnitId)
                .forNativeAd { nativeAd ->
                    nativeAd.setOnPaidEventListener { adValue ->
                        app.messages.config.AdsConfigManager.sendRevenue("native_chat", adValue)
                    }
                    loadingCount = (loadingCount - 1).coerceAtLeast(0)
                    totalFilled++
                    android.util.Log.d("ChatAds", "✅ [ChatAds] AD FILLED & LOADED ──> Headline: \"${nativeAd.headline}\"")
                    handleAdLoaded(nativeAd)
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loadingCount = (loadingCount - 1).coerceAtLeast(0)
                        totalFailed++
                        android.util.Log.w("ChatAds", "❌ [ChatAds] AD LOAD FAILED ──> Error: ${error.message} (code=${error.code})")
                        logState("FETCH FAILED")
                    }
                })
                .build()
            adLoader.loadAd(AdRequest.Builder().build())
        }
    }

    private fun handleAdLoaded(nativeAd: NativeAd) {
        val targetSlot = pendingTargetSlot
        if (targetSlot != null && (_currentAdSlot.value == null || _currentAdSlot.value?.first != targetSlot)) {
            // Fulfill the waiting active slot directly
            pendingTargetSlot = null
            retireCurrentAd()
            _currentAdSlot.value = Pair(targetSlot, nativeAd)
            _activeSlotMap.value = mapOf(targetSlot to nativeAd)
            android.util.Log.d("ChatAds", "🎯 [ChatAds] ASSIGNED LOADED AD ──> Active Slot #$targetSlot (Headline: \"${nativeAd.headline}\")")
            logState("ASSIGNED LOADED AD")
            preloadNextIfNeeded()
        } else {
            // Buffer into next preloaded ad
            _nextAd.value = nativeAd
            android.util.Log.d("ChatAds", "📥 [ChatAds] BUFFERED INTO NEXT AD (Headline: \"${nativeAd.headline}\")")
            logState("BUFFERED NEXT AD")
        }
    }

    /**
     * Background refresh after user returns to Chats after >= 30 seconds away.
     * Keeps current displayed ad visible, only refreshes the next preloaded buffer.
     */
    fun refreshPool() {
        if (isRefreshing) return
        isRefreshing = true
        android.util.Log.d("ChatAds", "🔄 [ChatAds] 30s Cooldown Refresh: Refreshing next preloaded buffer...")

        scope.launch(Dispatchers.Main) {
            val oldNext = _nextAd.value
            if (oldNext != null) {
                _nextAd.value = null
                runCatching { oldNext.destroy() }
            }
            isRefreshing = false
            preloadNextIfNeeded()
        }
    }

    /** Called when NativeAdView is created and rendered on screen. */
    fun onAdViewBound(ad: NativeAd) {
        activeBoundAds.add(ad)
        if (recordedShownAds.add(ad)) {
            totalShown++
        }
        logState("VIEW BOUND: \"${ad.headline}\"")
    }

    /** Called strictly inside AndroidView.onRelease AFTER nativeAdView.destroy() finishes. */
    fun onAdViewReleased(ad: NativeAd) {
        activeBoundAds.remove(ad)
        if (retiredAds.remove(ad)) {
            runCatching { ad.destroy() }
            android.util.Log.d("ChatAds", "🧹 [ChatAds] Safely destroyed retired NativeAd after View detachment: \"${ad.headline}\"")
        }
        logState("VIEW RELEASED: \"${ad.headline}\"")
    }

    fun destroyPool() {
        val unshownNext = _nextAd.value
        if (unshownNext != null) {
            android.util.Log.e(
                "ChatAds",
                "🗑️ [ChatAds] WASTED AD DETECTED: Next Preload Buffer ad (\"${unshownNext.headline}\") was downloaded but destroyed without ever being seen by user! (Lowers Show Rate!)"
            )
        }

        val uniqueAds = mutableSetOf<NativeAd>()
        _currentAdSlot.value?.second?.let { uniqueAds.add(it) }
        _nextAd.value?.let { uniqueAds.add(it) }
        uniqueAds.addAll(retiredAds)
        uniqueAds.addAll(activeBoundAds)

        _currentAdSlot.value = null
        _nextAd.value = null
        _activeSlotMap.value = emptyMap()
        pendingTargetSlot = null
        retiredAds.clear()
        activeBoundAds.clear()
        loadingCount = 0

        uniqueAds.forEach { ad ->
            runCatching { ad.destroy() }
        }
        android.util.Log.d("ChatAds", "🧹 [ChatAds] destroyPool: Cleaned up ${uniqueAds.size} total ads")
        logScorecard("POOL DESTROYED")
    }
}


