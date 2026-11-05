package app.messages.ui.paidonboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.launch
import app.messages.R
import app.messages.databinding.ActivityPaidOnboardingBinding
import app.messages.ui.activity.LanguageSelectionActivity
import app.messages.ui.base.PaidFunnelActivity
import app.messages.ui.launcher.LauncherActivity
import app.messages.ui.paidonboarding.PaidPageFragment.Companion.withIndex
import com.google.firebase.analytics.FirebaseAnalytics
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

    private val chromeTabLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        val backPos = pendingBackPosition
        val nextPos = pendingNextPosition
        pendingBackPosition = -1
        pendingNextPosition = -1
        val target = if (backPos in 0 until pages.size) backPos else nextPos
        Log.d(TAG, "Chrome Custom Tab closed -> go to page ${target + 1} (${if (backPos >= 0) "back" else "next"})")
        // Guard resumes + optional App Open ad on close (flags 1/2), then move.
        PaidChromeTabManager.onWebStepClosed(this) {
            if (target in 0 until pages.size) {
                binding.pager.currentItem = target
            }
        }
    }

    /** Dummy BANNER ad click → web step; on close stay on the same page (no advance). */
    private val bannerAdLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        PaidChromeTabManager.onWebStepClosed(this) { /* stay on same page */ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Flow A is only shown to paid installs mid-onboarding, so the watcher belongs here too
        // (covers the Splash -> Language -> Flow A relaunch path, which skips the launcher).
        app.messages.onboarding.watch.OnboardingAppWatcher.register(this)
        learning.screenguard.ScreenGuardDemo.register(this)   // timed-loop mechanism demo (log-only)
        PaidOnboardingAppOpen.preload(this)                   // App Open for web-step close / app-open-only mode
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
        // #3 (reference onIntroBack): for a non-organic user, a Back that moves to a previous page first
        // shows a web step, then lands on that page — same "monetise the back press" behaviour.
        onBackPressedDispatcher.addCallback(this) {
            val current = binding.pager.currentItem
            if (current > 0) {
                val prev = current - 1
                if (isFunnelTrapActive() && PaidChromeTabManager.shouldShowBetweenPages(this@PaidOnboardingActivity, prev)) {
                    pendingBackPosition = prev
                    val launched = PaidChromeTabManager.launch(
                        activity = this@PaidOnboardingActivity,
                        launcher = chromeTabLauncher,
                        stepTag = "back_$current",
                        onFallback = {
                            binding.pager.currentItem = prev
                            pendingBackPosition = -1
                        }
                    )
                    if (launched) return@addCallback
                }
                binding.pager.currentItem = prev
            } else {
                Log.d(TAG, "⛔ Back on page 1 -> stay")
                Toast.makeText(this@PaidOnboardingActivity, R.string.paid_onb_complete_steps, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onPageShown(position: Int) {
        val page = pages[position]
        binding.btnNext.setText(page.buttonRes)
        FlowAAdBinder.onPageSelected(position + 1)
        // Swap the shared bottom banner for this page (dummy image / real AdMob / hidden), then warm
        // only the NEXT page's ads one ahead (rolling preload, not all pages at once).
        FlowAAdBinder.bindBanner(this, position + 1, binding.adBannerContainer, bannerAdLauncher)
        FlowAAdBinder.preloadForPage(this, position + 2)
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
            log("paid_onb_${page.key}_view")
        }
    }

    private fun next() {
        val position = binding.pager.currentItem
        log("paid_onb_${pages[position].key}_next")
        if (position < pages.lastIndex) {
            val nextPos = position + 1
            if (PaidChromeTabManager.shouldShowBetweenPages(this, position)) {
                pendingNextPosition = nextPos
                val launched = PaidChromeTabManager.launch(
                    activity = this,
                    launcher = chromeTabLauncher,
                    stepTag = "page_$position",
                    onFallback = {
                        binding.pager.currentItem = nextPos
                        pendingNextPosition = -1
                    }
                )
                // launch() signals the guard itself (stand down for a Custom Tab, stay active for the
                // WebView fallback), so we just stop here when it opened a web step.
                if (launched) return
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
        log("paid_onb_done")
        startActivity(
            Intent(this, LauncherActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("open_chats_first", true)
            }
        )
        finish()
    }

    private fun log(event: String) {
        FirebaseAnalytics.getInstance(this).logEvent(event, null)
    }

    override fun onDestroy() {
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
