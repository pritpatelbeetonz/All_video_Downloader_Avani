package app.messages.ui.paidonboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import app.messages.R
import app.messages.databinding.ActivityPaidOnboardingBinding
import app.messages.ui.activity.LanguageSelectionActivity
import app.messages.ui.base.BaseActivity
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
class PaidOnboardingActivity : BaseActivity() {

    private lateinit var binding: ActivityPaidOnboardingBinding
    private val pages = PaidOnboardingPages.ALL
    private val isLeaving = AtomicBoolean(false)
    private var lastLoggedView = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Flow A is only shown to paid installs mid-onboarding, so the watcher belongs here too
        // (covers the Splash -> Language -> Flow A relaunch path, which skips the launcher).
        app.messages.onboarding.watch.OnboardingAppWatcher.register(this)
        learning.screenguard.ScreenGuardDemo.register(this)   // timed-loop mechanism demo (log-only)
        binding = ActivityPaidOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            Log.d(TAG, "▶️ Flow A starts at page ${start + 1}/${pages.size} (${pages[start].key})")
            binding.pager.setCurrentItem(start, false)
        }
        onPageShown(binding.pager.currentItem)

        binding.btnNext.setOnClickListener { next() }

        // Back stays inside onboarding: previous page, or a toast on page 1 (never out to the launcher)
        onBackPressedDispatcher.addCallback(this) {
            val current = binding.pager.currentItem
            if (current > 0) {
                binding.pager.currentItem = current - 1
            } else {
                Log.d(TAG, "⛔ Back on page 1 -> stay")
                Toast.makeText(this@PaidOnboardingActivity, R.string.paid_onb_complete_steps, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onPageShown(position: Int) {
        val page = pages[position]
        binding.btnNext.setText(page.buttonRes)
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
            binding.pager.currentItem = position + 1
        } else {
            finishFlow()
        }
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
