package app.messages.ui.launcher.dashboard

import app.messages.ads.ng.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.messages.config.AdsConfigManager
import app.messages.R
import app.messages.databinding.FragmentDashboardBinding
import app.messages.ui.launcher.LauncherViewModel
import app.messages.ui.launcher.adapter.CompactAppIconAdapter
import app.messages.ui.launcher.adapter.LauncherPagerAdapter
import app.messages.utility.GreetingHelper
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels { DashboardViewModel.Factory(requireContext()) }
    private val launcherViewModel: LauncherViewModel by activityViewModels { LauncherViewModel.Factory(requireContext()) }

    private lateinit var suggestedAdapter: CompactAppIconAdapter
    private lateinit var recentAdapter: CompactAppIconAdapter
    private var isLoadingNativeAd = false
    private var currentNativeAd: com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd? = null
    /** Unit the displayed ad belongs to (a recycled Language orphan keeps its own unit). */
    private var currentNativeAdUnit: String = ""

    /** Seen -> destroyed; never seen -> back to the RAM cache for the next visit (not wasted). */
    private fun releaseCurrentNativeAd(reason: String) {
        val ad = currentNativeAd ?: return
        currentNativeAd = null
        AdsConfigManager.releaseNativeAd(currentNativeAdUnit, ad, reason)
    }

    private val callPermissionsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Greeting and date section commented out
        // binding.tvGreeting.text = GreetingHelper.greeting()
        // binding.tvDate.text = GreetingHelper.formattedDate()

        suggestedAdapter = CompactAppIconAdapter { appInfo ->
            app.messages.config.AdsConfigManager.onAppClicked(requireActivity(), appInfo.packageName) {
                if (appInfo.packageName == requireContext().packageName) {
                    launcherViewModel.requestNavigateToChats()
                } else {
                    viewModel.openApp(appInfo)
                }
            }
        }
        recentAdapter = CompactAppIconAdapter { appInfo ->
            app.messages.config.AdsConfigManager.onAppClicked(requireActivity(), appInfo.packageName) {
                if (appInfo.packageName == requireContext().packageName) {
                    launcherViewModel.requestNavigateToChats()
                } else {
                    viewModel.openApp(appInfo)
                }
            }
        }

        binding.rvSuggested.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvSuggested.adapter = suggestedAdapter
        binding.rvSuggested.itemAnimator = null

        binding.rvRecentApps.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvRecentApps.adapter = recentAdapter
        binding.rvRecentApps.itemAnimator = null

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        // Opens Launcher Settings (wallpapers, home screen, app drawer, gestures...).
        binding.cardSettings.setOnClickListener {
            // The ad finishes asynchronously; use the captured activity, the fragment may be detached by then.
            val act = activity ?: return@setOnClickListener
            app.messages.config.AdsConfigManager.showInterstitial(act, place = "Dashboard_Settings") {
                if (!act.isFinishing && !act.isDestroyed) {
                    act.startActivity(
                        android.content.Intent(act, app.messages.ui.launcher.settings.LauncherMainSettingsActivity::class.java)
                    )
                }
            }
        }

        binding.cardStorage.setOnClickListener {
            runCatching {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
            }.recoverCatching {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.suggestedApps.collect { suggestedAdapter.submitList(it.toList()) } }
                launch { viewModel.recentApps.collect { recentAdapter.submitList(it.toList()) } }
                launch { viewModel.storageState.collect { bindStorageCard(it) } }
                
                // Refresh and load fresh Native Ad whenever user swipes to Dashboard (PAGE_MESSAGE)
                launch {
                    launcherViewModel.currentPage.collect { page ->
                        if (page == LauncherPagerAdapter.PAGE_MESSAGE) {
                            val config = AdsConfigManager.config
                            if (config.canShowNative && !app.messages.preferences.PreferenceHelper.getInstance(requireContext()).isPremium()) {
                                if (isResumed) {
                                    Log.d("ADS_DASHBOARD", "👉 [DASHBOARD] User swiped to Dashboard page (Page 2) -> loading native ad (cache first)")
                                    loadNativeAd()
                                } else {
                                    // ViewPager2 selects the page BEFORE it resumes this fragment. A cache hit
                                    // is delivered synchronously, so loading now hit the !isResumed check and the
                                    // ad went straight back to the cache unshown. Load in onResume instead.
                                    pendingVisitLoad = true
                                }
                            }
                        }
                    }
                }

                launch {
                    AdsConfigManager.configFlow.collect { config ->
                        if (!config.isNativeOn || app.messages.preferences.PreferenceHelper.getInstance(requireContext()).isPremium()) {
                            binding.nativeAdCardView.visibility = View.GONE
                        } else if (launcherViewModel.currentPage.value == LauncherPagerAdapter.PAGE_MESSAGE) {
                            if (binding.nativeAdFrameLayout.childCount == 0 || binding.nativeAdCardView.visibility == View.GONE) {
                                Log.d("ADS_DASHBOARD", "🔄 [DASHBOARD] Remote config updated on active Dashboard page -> Requesting native ad")
                                loadNativeAd()
                            }
                        }
                    }
                }
            }
        }

        binding.root.alpha = 0f
        binding.root.animate().alpha(1f).setDuration(280).start()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDashboard()
        // Back on the launcher while the Dashboard is the current page: an ad that landed while we were
        // in the background went to the cache (see loadNativeAd) — show it now (instant cache hit).
        val visitLoad = pendingVisitLoad
        pendingVisitLoad = false
        if (_binding != null && (visitLoad || currentNativeAd == null) &&
            launcherViewModel.currentPage.value == LauncherPagerAdapter.PAGE_MESSAGE &&
            AdsConfigManager.config.canShowNative
        ) {
            loadNativeAd()
        }
    }

    /** Set when the Dashboard page was selected before this fragment resumed; onResume does the load. */
    private var pendingVisitLoad = false

    private fun bindStorageCard(state: StorageInfoState) {
        binding.tvStoragePercent.text = "${state.usedPercentage}% Used"
        binding.progressStorage.progress = state.usedPercentage
        binding.tvStorageSpace.text = "${state.usedGbLabel} / ${state.totalGbLabel}"
        binding.tvStorageFree.text = state.freeGbLabel
    }

    private fun loadNativeAd() {
        val config = app.messages.config.AdsConfigManager.config
        
        if (app.messages.preferences.PreferenceHelper.getInstance(requireContext()).isPremium() || !config.isNativeOn) {
            Log.d("ADS_DASHBOARD", "⏭️ [DASHBOARD] loadNativeAd skipped (isPremium=${app.messages.preferences.PreferenceHelper.getInstance(requireContext()).isPremium()}, isNativeOn=${config.isNativeOn})")
            binding.nativeAdCardView.visibility = View.GONE
            return
        }

        if (isLoadingNativeAd) {
            Log.d("ADS_DASHBOARD", "⏳ [DASHBOARD] loadNativeAd skipped: Ad request already in-flight")
            return
        }

        // Fallback: no Dashboard ad cached, but an orphaned (fresh) Language native ad is sitting unused
        // in RAM -> show it here instead of firing a new request. A Dashboard request already in flight
        // keeps going and lands in the cache for the next visit.
        AdsConfigManager.takeOrphanLanguageNativeForDashboard(requireContext())?.let { orphan ->
            renderNativeAd(orphan, config.nativeAdIdLanguage)
            return
        }
        isLoadingNativeAd = true

        val adUnitId = config.nativeAdIdDashboard
        Log.d("ADS_DASHBOARD", "🚀 [DASHBOARD_REQUEST] Native Dashboard Ad needed (RAM cache first, network only if empty) | UnitId: $adUnitId")

        val hasExistingAd = binding.nativeAdFrameLayout.childCount > 0
        if (!hasExistingAd) {
            binding.nativeAdCardView.visibility = View.VISIBLE
            binding.nativeAdShimmerFrameLayout.visibility = View.VISIBLE
            binding.nativeAdFrameLayout.visibility = View.GONE
        }

        app.messages.config.AdsConfigManager.loadNativeAd(
            context = requireContext(),
            adUnitId = adUnitId,
            onLoaded = { nativeAd ->
                isLoadingNativeAd = false
                if (!isAdded || _binding == null) {
                    AdsConfigManager.cacheNativeAd(adUnitId, nativeAd)
                    Log.d("ADS_DASHBOARD", "💾 [DASHBOARD] Fragment not attached -> Cached in RAM for next visit")
                    return@loadNativeAd
                }

                // Not resumed = launcher in background (user opened an app from Recent/Suggested while
                // this was loading) or another page: rendering now would park it unseen -> cache it.
                if (launcherViewModel.currentPage.value != LauncherPagerAdapter.PAGE_MESSAGE || !isResumed) {
                    // Zero Waste: User swiped away before ad finished loading -> save to RAM cache for next visit!
                    AdsConfigManager.cacheNativeAd(adUnitId, nativeAd)
                    Log.d("ADS_DASHBOARD", "💾 [DASHBOARD] User swiped away -> Cached in RAM for next visit")
                    return@loadNativeAd
                }

                renderNativeAd(nativeAd, adUnitId)
            },
            onFailed = {
                isLoadingNativeAd = false
                Log.e("ADS_DASHBOARD", "❌ [DASHBOARD_FAILED] Native Dashboard Ad failed to load from AdMob")
                if (_binding != null && binding.nativeAdFrameLayout.childCount == 0) {
                    binding.nativeAdCardView.visibility = View.GONE
                }
            }
        )
    }

    /** Inflates the dashboard native template with [nativeAd] (own Dashboard ad or recycled Language orphan). */
    private fun renderNativeAd(nativeAd: com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd, unitId: String) {
        // Previous ad: destroyed if it was seen, recycled into the cache if it never got an impression.
        if (currentNativeAd !== nativeAd) releaseCurrentNativeAd("replaced_unseen")
        currentNativeAd = nativeAd
        currentNativeAdUnit = unitId
        Log.d("ADS_DASHBOARD", "♻️ [DASHBOARD_CLEANUP] Cleaned up previous ad instance")

        binding.nativeAdCardView.visibility = View.VISIBLE
        binding.nativeAdShimmerFrameLayout.visibility = View.GONE
        binding.nativeAdFrameLayout.visibility = View.VISIBLE
        val replacing = binding.nativeAdFrameLayout.childCount > 0
        binding.nativeAdFrameLayout.removeAllViews()

        val adView = layoutInflater.inflate(R.layout.ads_native_big, null, false) as com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
        AdsConfigManager.populateNativeAdView(nativeAd, adView, "dashboard")

        binding.nativeAdFrameLayout.addView(adView)
        if (replacing) {
            // Swap in the new ad with a short fade instead of a hard cut (Device Setting behaviour).
            adView.alpha = 0f
            adView.animate().alpha(1f).setDuration(250).start()
        }
        Log.d("ADS_DASHBOARD", "✨ [DASHBOARD_SHOWN] Native Dashboard Ad inflated, populated, and DISPLAYED ON SCREEN successfully! 🎉")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releaseCurrentNativeAd("view_destroyed_unseen")
        _binding = null
    }
}
