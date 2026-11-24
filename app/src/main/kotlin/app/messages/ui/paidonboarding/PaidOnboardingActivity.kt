package app.messages.ui.paidonboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.launch
import app.messages.R
import app.messages.config.AdsConfigManager
import app.messages.databinding.ActivityPaidOnboardingBinding
import app.messages.ui.activity.LanguageSelectionActivity
import app.messages.ui.base.PaidFunnelActivity
import app.messages.ui.launcher.LauncherActivity
import app.messages.ui.paidonboarding.PaidPageFragment.Companion.withIndex
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Flow A: the 11 onboarding pages for paid installs (google / facebook / unity), shown after the
 * Language screen. Next button only (no swipe); back goes to the previous page. The page reached is
 * saved, so a closed app reopens here (SplashActivity). The last page opens the Launcher.
 *
 * Ads: none yet. Every page has a hidden adNativeContainer and this screen a hidden adBannerContainer.
 *
 * Logcat filter: tag:PaidOnboarding
 */
class PaidOnboardingActivity : PaidFunnelActivity() {

    private lateinit var binding: ActivityPaidOnboardingBinding
    private val pages = PaidOnboardingPages.ALL
    private val isLeaving = AtomicBoolean(false)
    private var lastLoggedView = -1
    private var pendingNextPosition = -1
    private var pendingBackPosition = -1   // #3: back-on-funnel opened a web step → go to this (previous) page on close

    /** 1-based number of the page currently shown (used by FlowAEscapeLogger for the onb_N screen tag). */
    val currentPageNumber: Int
        get() = (binding.pager.currentItem + 1).coerceIn(1, pages.size)

    private var pendingStepTag: String? = null

    // Native ad delayed reveal & Next button slide-down
    private var shiftRunnable: Runnable? = null
    private var activeShiftContainer: FrameLayout? = null
    private var isShiftTimerExpired = false
    private var isShiftAdRendered = false
    private val shiftHandler = Handler(Looper.getMainLooper())

    /** Active Remote Config step sequence (Next), or null when the step has none. */
    private var sequenceRunner: FlowASequenceRunner? = null

    private val chromeTabLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        sequenceRunner?.takeIf { it.awaitingChrome }?.let {
            Log.d(TAG, "Chrome Custom Tab closed -> continue step sequence")
            it.onChromeClosed()
            return@registerForActivityResult
        }
        val backPos = pendingBackPosition
        val nextPos = pendingNextPosition
        val tag = pendingStepTag ?: if (nextPos in 0 until pages.size) "page_${nextPos}" else "default"
        pendingBackPosition = -1
        pendingNextPosition = -1
        pendingStepTag = null
        val target = if (backPos in 0 until pages.size) backPos else nextPos
        Log.d(TAG, "Chrome Custom Tab closed ($tag) -> go to page ${target + 1} (${if (backPos >= 0) "back" else "next"})")
        // Guard resumes, then move.
        PaidChromeTabManager.onWebStepClosed(this, stepTag = tag) {
            if (target in 0 until pages.size) {
                binding.pager.currentItem = target
            }
        }
    }

    /** Dummy BANNER ad click → web step; on close stay on the same page (no advance). */
    private val bannerAdLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        val current = binding.pager.currentItem
        PaidChromeTabManager.onWebStepClosed(this, stepTag = "page_${current + 1}") { /* stay on same page */ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Flow A is only shown to paid installs mid-onboarding, so the watcher belongs here too
        // (covers the Splash -> Language -> Flow A relaunch path, which skips the launcher).
        app.messages.onboarding.watch.OnboardingAppWatcher.register(this)
        learning.screenguard.ScreenGuardDemo.register(this)   // timed-loop mechanism demo (log-only)
        binding = ActivityPaidOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Flow-A two-ad system: make sure the dummy images are fetched (normally already done on the
        // Language screen), bind the current page's banner, then warm ONLY the next page one ahead
        // (rolling — never all 11 at once).
        lifecycleScope.launch {
            FlowADummyImages.fetch()
            if (!isFinishing && !isDestroyed) {
                val current = binding.pager.currentItem
                FlowAAdBinder.bindBanner(this@PaidOnboardingActivity, current + 1, binding.adBannerContainer, bannerAdLauncher)
                FlowAAdBinder.preloadForPage(this@PaidOnboardingActivity, current + 2) // next page's ads
            }
        }

        binding.pager.isUserInputEnabled = false
        binding.pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = pages.size
            override fun createFragment(position: Int): Fragment = when (pages[position]) {
                is PaidPage.Gender -> GenderPageFragment().withIndex(position)
                is PaidPage.Choice -> ChoiceListPageFragment().withIndex(position)
                is PaidPage.Interests -> InterestsPageFragment().withIndex(position)
                is PaidPage.Feature -> FeaturePageFragment().withIndex(position)
            }
        }
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = onPageShown(position)
        })

        if (savedInstanceState == null) {
            val start = PaidOnboardingPrefs.step(this).coerceIn(0, pages.lastIndex)
            Log.d(TAG, "Flow A starts at page ${start + 1}/${pages.size} (${pages[start].key})")
            binding.pager.setCurrentItem(start, false)
        } else {
            pendingNextPosition = savedInstanceState.getInt("pending_next_position", -1)
            pendingBackPosition = savedInstanceState.getInt("pending_back_position", -1)
        }
        onPageShown(binding.pager.currentItem)

        binding.btnNext.setOnClickListener { next() }

        // Back stays inside onboarding: previous page, or a toast on page 1 (never out to the launcher).
        onBackPressedDispatcher.addCallback(this) {
            FlowAEscapeLogger.onEscapeAttempt(this@PaidOnboardingActivity, "back")
            val current = binding.pager.currentItem
            if (current > 0) {
                val prev = current - 1
                binding.pager.currentItem = prev
            } else {
                Log.d(TAG, "⛔ Back on page 1 -> stay")
                Toast.makeText(this@PaidOnboardingActivity, R.string.paid_onb_complete_steps, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var launcherAdsPreloaded = false

    /**
     * The launcher's Dashboard / Drawer native ads are no longer warmed on the Language screen for
     * Flow A (the user still has the whole onboarding ahead and may leave). Warm them once the user is
     * on the last two pages, so they are ready when the launcher opens but are not requested for users
     * who drop off early. The Google Search native is deliberately NOT preloaded here (low show rate:
     * most users never open search). Single-flight in AdsConfigManager makes repeat calls harmless.
     */
    private fun preloadLauncherAdsNearEnd(position: Int) {
        if (launcherAdsPreloaded || position < pages.lastIndex - 1) return
        launcherAdsPreloaded = true
        Log.d(TAG, "🚀 Page ${position + 1}/${pages.size}: near the end -> preloading launcher native ads (dashboard/drawer)")
        app.messages.config.AdsConfigManager.preloadDashboardNativeAd(this)
        app.messages.config.AdsConfigManager.preloadDrawerNativeAd(this)
    }

    private var lastShownPosition = -1

    private fun onPageShown(position: Int) {
        if (lastShownPosition == position) {
            Log.d(TAG, "onPageShown($position) ignored (already current)")
            return
        }
        lastShownPosition = position
        cancelNativeShift()
        // The fragment may have been created (and its prepare deferred or wiped) before it became
        // current — re-arm the shift for the now-selected page.
        (supportFragmentManager.findFragmentByTag("f$position") as? PaidPageFragment<*>)?.rePrepareShift()
        val page = pages[position]
        binding.btnNext.setText(page.buttonRes)
        FlowAAdBinder.onPageSelected(position + 1)
        // Swap the shared bottom banner for this page (dummy image / real AdMob / hidden), then warm
        // only the NEXT page's ads one ahead (rolling preload, not all pages at once).
        FlowAAdBinder.bindBanner(this, position + 1, binding.adBannerContainer, bannerAdLauncher)
        FlowAAdBinder.preloadForPage(this, position + 2)
        FlowASequenceRunner.preload(this, "page_${position + 1}")   // ads a Remote Config sequence will show
        // Prefetch the NEXT web step's page so the Custom Tab is ready the instant the user taps Next
        // (fresh, cache-busted URL — no stale cached page).
        PaidChromeTabManager.prefetchStep(this, "page_${position + 1}")
        preloadLauncherAdsNearEnd(position)
        if (page is PaidPage.Feature) {
            binding.tvCaption.setText(page.captionRes)
            binding.tvCaption.visibility = View.VISIBLE
        } else {
            binding.tvCaption.visibility = View.GONE
        }
        PaidOnboardingPrefs.setStep(this, position)
        if (lastLoggedView != position) {
            lastLoggedView = position
            Log.d(TAG, "📄 Page ${position + 1}/${pages.size}: ${page.key}")
            // Flow A attribution funnel: ${prefix}_onb_${N}_visited (once per install, ad installs only).
            FlowAEscapeLogger.logFunnelEvery(this, "onb_${position + 1}_visited")
        }
    }

    private fun next() {
        cancelNativeShift()
        val position = binding.pager.currentItem
        // Block advancing until the current page's form has a selection (gender / choice / interests).
        val fragment = supportFragmentManager.findFragmentByTag("f$position") as? PaidPageFragment<*>
        if (fragment != null && !fragment.isAnswered()) {
            Toast.makeText(this, R.string.paid_onb_select_to_continue, Toast.LENGTH_SHORT).show()
            return
        }
        // Flow A attribution funnel: ${prefix}_onb_${N}_clicked.
        FlowAEscapeLogger.logFunnelEvery(this, "onb_${position + 1}_clicked")
        if (position < pages.lastIndex) {
            val nextPos = position + 1
            // Remote Config sequence for this step (e.g. inter -> chrome -> app_open -> chrome).
            if (sequenceRunner == null) {
                val stepTag = "page_${position + 1}"
                // Remember the destination so a process-death recovery (sequenceRunner is a transient
                // field, not saved) can still advance via the chromeTabLauncher fallback instead of
                // leaving the user stuck on this page. Cleared in onDone (and if forStep returns null).
                pendingNextPosition = nextPos
                pendingStepTag = stepTag
                val runner = FlowASequenceRunner.forStep(
                    activity = this,
                    stepTag = stepTag,
                    launchChrome = { onFallback ->
                        PaidChromeTabManager.launch(
                            activity = this,
                            launcher = chromeTabLauncher,
                            stepTag = stepTag,
                            onFallback = onFallback
                        )
                    },
                    onDone = {
                        sequenceRunner = null
                        pendingNextPosition = -1
                        pendingStepTag = null
                        if (!isFinishing && !isDestroyed) binding.pager.currentItem = nextPos
                    }
                )
                if (runner != null) {
                    sequenceRunner = runner
                    runner.start()
                    return
                }
                // No Remote Config sequence for this step -> just advance; clear our guesses first.
                pendingNextPosition = -1
                pendingStepTag = null
            } else {
                return   // a sequence is already running; ignore extra taps
            }
            binding.pager.currentItem = nextPos
        } else {
            finishFlow()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pending_next_position", pendingNextPosition)
        outState.putInt("pending_back_position", pendingBackPosition)
    }

    private fun finishFlow() {
        if (!isLeaving.compareAndSet(false, true)) return
        Log.d(TAG, "✅ Flow A done -> Launcher")
        PaidOnboardingPrefs.setDone(this)
        // Flow A is over: warm the launcher's swipe / app-tap / settings ads + Chrome page, only the formats
        // their patterns use (no-op outside simple launcher mode). Duplicate-safe.
        AdsConfigManager.preloadLauncherByPatterns(this)
        // Flow A is over: now it is safe to boot the Flutter engine (deferred for the whole funnel).
        app.messages.feature.LauncherSlot.warmUp(applicationContext)
        // Flow A attribution funnel: ${prefix}_onb_done (completion).
        FlowAEscapeLogger.logFunnelEvery(this, "onb_done")
        startActivity(
            Intent(this, LauncherActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("open_chats_first", true)
            }
        )
        finish()
    }

    // ---- Native ad delayed reveal & Next button slide-down ----
    fun cancelNativeShift() {
        shiftRunnable?.let { shiftHandler.removeCallbacks(it) }
        shiftRunnable = null
        isShiftTimerExpired = false
        isShiftAdRendered = false
        activeShiftContainer?.animate()?.cancel()
        activeShiftContainer?.alpha = 1f
        activeShiftContainer = null
        binding.btnNext.animate()?.cancel()
        binding.btnNext.translationY = 0f
    }

    fun prepareNativeShift(stepTag: String, nativeContainer: FrameLayout, position: Int) {
        val isEnabled = AdsConfigManager.isFlowANativeShiftEnabledForStep(stepTag)
        Log.d(TAG, "🎯 [FlowA-Shift] prepareNativeShift($stepTag): isEnabled=$isEnabled")
        Log.d("FlowAAds", "🎯 [FlowA-Shift] prepareNativeShift($stepTag): isEnabled=$isEnabled")

        if (!isEnabled) {
            nativeContainer.alpha = 1f
            return
        }

        // ViewPager2 creates the NEXT page's fragment ahead of time. Only the selected page may own the
        // single shared shift slot; a neighbour must not cancel/steal it. It is prepared later from
        // onPageShown() when it becomes current.
        if (position != binding.pager.currentItem) {
            Log.d("FlowAAds", "⏭️ [FlowA-Shift] $stepTag is not the current page — deferring shift")
            return
        }

        // onViewCreated AND the onResume re-bind both call this for the same container. Without this
        // guard the second pass runs checkAndApplyShift AFTER the button was already lifted, measures
        // btnNext at its lifted position (deltaY≈0), skips the shift and reveals the ad early.
        if (activeShiftContainer == nativeContainer) {
            Log.d("FlowAAds", "↩️ [FlowA-Shift] Already shifting this container ($stepTag) — skip duplicate prepare")
            return
        }

        cancelNativeShift()
        activeShiftContainer = nativeContainer
        nativeContainer.alpha = 0f
        if (nativeContainer.visibility == View.GONE) {
            nativeContainer.visibility = View.INVISIBLE
        }
        isShiftTimerExpired = false
        isShiftAdRendered = false

        nativeContainer.post {
            checkAndApplyShift(stepTag, nativeContainer, retryCount = 0)
        }
    }

    private fun checkAndApplyShift(stepTag: String, nativeContainer: FrameLayout, retryCount: Int) {
        if (isFinishing || isDestroyed || activeShiftContainer != nativeContainer) {
            Log.d("FlowAAds", "⚠️ [FlowA-Shift] Shift check aborted: finishing=$isFinishing, active=${activeShiftContainer == nativeContainer}")
            return
        }

        val nativeLoc = IntArray(2)
        val btnLoc = IntArray(2)
        nativeContainer.getLocationOnScreen(nativeLoc)
        binding.btnNext.getLocationOnScreen(btnLoc)

        // Wait for views to be measured if coordinates are not on screen yet
        if (nativeLoc[1] <= 0 || btnLoc[1] <= 0) {
            if (retryCount < 5) {
                nativeContainer.postDelayed({
                    checkAndApplyShift(stepTag, nativeContainer, retryCount + 1)
                }, 50)
                return
            }
        }

        // btnLoc already includes any translationY currently applied; subtract it to get the button's
        // RESTING position, so a re-entrant measurement (button already lifted) still yields the true
        // offset instead of ~0.
        val btnRestingY = btnLoc[1] - binding.btnNext.translationY.toInt()
        val deltaY = (nativeLoc[1] - btnRestingY).toFloat()
        Log.d(TAG, "🎯 [FlowA-Shift] Coordinates ($stepTag): nativeY=${nativeLoc[1]}, btnRestingY=$btnRestingY -> deltaY=$deltaY")
        Log.d("FlowAAds", "🎯 [FlowA-Shift] Coordinates ($stepTag): nativeY=${nativeLoc[1]}, btnRestingY=$btnRestingY -> deltaY=$deltaY")

        if (deltaY < -10f) {
            binding.btnNext.translationY = deltaY
            Log.d(TAG, "🎯 [FlowA-Shift] Next button shifted up by $deltaY px ($stepTag)")
            Log.d("FlowAAds", "🎯 [FlowA-Shift] Next button shifted up by $deltaY px ($stepTag)")

            val delay = AdsConfigManager.getFlowANativeShiftDelayMs()
            val runnable = Runnable {
                if (isFinishing || isDestroyed || activeShiftContainer != nativeContainer) return@Runnable
                isShiftTimerExpired = true
                Log.d(TAG, "⏰ [FlowA-Shift] Delay expired (${delay}ms, adRendered=$isShiftAdRendered)")
                Log.d("FlowAAds", "⏰ [FlowA-Shift] Delay expired (${delay}ms, adRendered=$isShiftAdRendered)")
                if (isShiftAdRendered) {
                    animateShiftDown()
                }
            }
            shiftRunnable = runnable
            shiftHandler.postDelayed(runnable, delay)
        } else {
            Log.d("FlowAAds", "⚠️ [FlowA-Shift] Shift skipped ($stepTag): deltaY ($deltaY) >= -10f")
            nativeContainer.alpha = 1f
        }
    }

    fun onNativeAdRendered(nativeContainer: FrameLayout) {
        nativeContainer.visibility = View.VISIBLE
        if (activeShiftContainer != nativeContainer) {
            Log.d("FlowAAds", "✨ [FlowA-Shift] Native ad rendered (not shifted container) -> alpha=1")
            nativeContainer.alpha = 1f
            return
        }
        isShiftAdRendered = true
        Log.d(TAG, "✨ [FlowA-Shift] Native ad rendered in shifted container (timerExpired=$isShiftTimerExpired)")
        Log.d("FlowAAds", "✨ [FlowA-Shift] Native ad rendered in shifted container (timerExpired=$isShiftTimerExpired)")
        if (isShiftTimerExpired) {
            animateShiftDown()
        }
    }

    fun onNativeAdFailed(nativeContainer: FrameLayout) {
        if (activeShiftContainer == nativeContainer) {
            Log.d("FlowAAds", "❌ [FlowA-Shift] Native ad failed/hidden -> sliding Next button back to bottom")
            val animDuration = AdsConfigManager.getFlowANativeShiftAnimDurationMs()
            binding.btnNext.animate()
                .translationY(0f)
                .setDuration(animDuration)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
            shiftRunnable?.let { shiftHandler.removeCallbacks(it) }
            shiftRunnable = null
            activeShiftContainer = null
            isShiftTimerExpired = false
            isShiftAdRendered = false
        }
    }

    private fun animateShiftDown() {
        val container = activeShiftContainer ?: return
        val animDuration = AdsConfigManager.getFlowANativeShiftAnimDurationMs()
        Log.d(TAG, "⬇️ [FlowA-Shift] Animating Next button downside and revealing native ad (${animDuration}ms)")
        Log.d("FlowAAds", "⬇️ [FlowA-Shift] Animating Next button downside and revealing native ad (${animDuration}ms)")

        binding.btnNext.animate()
            .translationY(0f)
            .setDuration(animDuration)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        container.animate()
            .alpha(1f)
            .setDuration(animDuration)
            .start()
    }

    override fun onDestroy() {
        cancelNativeShift()
        super.onDestroy()
        FlowAAdBinder.destroy()
    }

    companion object {
        private const val TAG = "PaidOnboarding"

        /** LanguageSelectionActivity extra: onboarding mode (Back stays, Done continues to Flow A). */
        const val EXTRA_PAID_ONBOARDING = "paid_onboarding"

        fun intent(context: Context): Intent = Intent(context, PaidOnboardingActivity::class.java)

        /**
         * Restart onboarding: Flow A back to page 1, and the Language screen (onboarding mode) as a
         * fresh task. Used when Home is pressed mid-onboarding (LauncherActivity) or the app is reopened.
         */
        fun restartFromLanguage(context: Context): Intent {
            Log.d(TAG, "🏠 Onboarding not finished -> restart from Language")
            PaidOnboardingPrefs.restart(context)
            return Intent(context, LanguageSelectionActivity::class.java).apply {
                putExtra(EXTRA_PAID_ONBOARDING, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }
    }
}
