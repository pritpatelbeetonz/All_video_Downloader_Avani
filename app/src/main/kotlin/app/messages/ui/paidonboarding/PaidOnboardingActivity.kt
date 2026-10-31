package app.messages.ui.paidonboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import app.messages.databinding.ActivityPaidOnboardingBinding
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

        onBackPressedDispatcher.addCallback(this) {
            val current = binding.pager.currentItem
            if (current > 0) {
                binding.pager.currentItem = current - 1
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
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

        fun intent(context: Context): Intent = Intent(context, PaidOnboardingActivity::class.java)
    }
}
