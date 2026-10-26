package app.messages.ui.launcher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import app.messages.R
import app.messages.databinding.FragmentLauncherHostBinding
import app.messages.ui.common.SwipeDownDismissLayout
import app.messages.ui.common.SwipeUpInterceptLayout
import app.messages.ui.launcher.adapter.LauncherPagerAdapter
import app.messages.ui.launcher.drawer.AppDrawerFragment
import app.messages.ui.launcher.drawer.AppDrawerMotionController
// Removed BlurHelper import
import app.messages.util.SystemWallpaperHelper

import kotlinx.coroutines.launch
import kotlin.math.abs

class LauncherHostFragment : Fragment() {

    private var _binding: FragmentLauncherHostBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LauncherViewModel by activityViewModels { LauncherViewModel.Factory(requireContext()) }
    private lateinit var pagerAdapter: LauncherPagerAdapter
    private lateinit var drawerMotion: AppDrawerMotionController

    private var blurSupported = false
    private var blurAutoUpdating = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLauncherHostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSystemWallpaper()
        setupPager()
        setupDrawerMotion()
        setupDrawerGestures()
        observe()
        
        setupBlur()
        setupPageIndicator()
        setupEdgeTab()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            // Use dimension px (not raw dp ints) so indicator clears the dock on all densities
            val indicatorMargin = resources.getDimensionPixelSize(R.dimen.page_indicator_margin_bottom)
            
            (binding.glassPageIndicatorBlur.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)
                ?.let { lp ->
                    lp.bottomMargin = indicatorMargin + nav
                    binding.glassPageIndicatorBlur.layoutParams = lp
                }
            insets
        }

        if (savedInstanceState == null) {
            try {
                val prefs = app.messages.preferences.PreferenceHelper.getInstance(requireContext())
                val openChatsFirst = requireActivity().intent?.getBooleanExtra("open_chats_first", false) ?: false
                val isFirstLanding = !prefs.isFirstLauncherLandingDone()

                // If intent specifies open_chats_first OR it's the very first landing after onboarding -> Open Video Downloader (Tab 0)
                val shouldOpenDownloaderFirst = openChatsFirst || isFirstLanding
                val initialTab = if (shouldOpenDownloaderFirst) {
                    LauncherPagerAdapter.PAGE_VIDEO_DOWNLOADER
                } else {
                    LauncherPagerAdapter.PAGE_HOME
                }

                if (isFirstLanding) {
                    prefs.setFirstLauncherLandingDone(true)
                }

                val pageName = when (initialTab) {
                    LauncherPagerAdapter.PAGE_VIDEO_DOWNLOADER -> "🎬 PAGE_VIDEO_DOWNLOADER (Flutter Module / Downloader Home)"
                    LauncherPagerAdapter.PAGE_HOME -> "🏠 PAGE_HOME (Launcher Desktop / Apps Grid)"
                    LauncherPagerAdapter.PAGE_MESSAGE -> "💬 PAGE_MESSAGE (Dashboard / Messages)"
                    else -> "❓ UNKNOWN_PAGE ($initialTab)"
                }
                android.util.Log.d("FLOW_CHECK", "🚀 [ONBOARDING_TRANSITION] Determining launcher landing tab...")
                android.util.Log.d("FLOW_CHECK", "📦 [INTENT_EXTRAS] open_chats_first = $openChatsFirst, isFirstLanding = $isFirstLanding")
                android.util.Log.d("FLOW_CHECK", "🎯 [FIRST_SCREEN_SHOWN] Displaying initial tab index $initialTab -> $pageName")

                binding.viewPager.post {
                    if (_binding != null) {
                        binding.viewPager.setCurrentItem(initialTab, false)
                        updateChrome(initialTab)
                        updateSwipeEnabled()
                        android.util.Log.d("FLOW_CHECK", "✅ [FIRST_SCREEN_RENDERED] viewPager.setCurrentItem($initialTab) executed successfully!")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FLOW_CHECK", "❌ [FIRST_SCREEN_ERROR] Error determining initial screen: ${e.message}", e)
            }
        } else {
            binding.viewPager.post {
                if (_binding != null) {
                    updateChrome(binding.viewPager.currentItem)
                    updateSwipeEnabled()
                }
            }
        }

        binding.root.post {
            val h = binding.drawerContainer.height.toFloat().coerceAtLeast(1f)
            drawerMotion.setDrawerHeight(h)
            binding.swipeUpIntercept.drawerHeightPx = h
            drawerMotion.reset()
            updateChrome(binding.viewPager.currentItem)
            updateSwipeEnabled()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh if the user changed wallpaper while away.
        loadSystemWallpaper()
    }

    private fun loadSystemWallpaper() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (_binding == null) return@launch
            SystemWallpaperHelper.applyTo(binding.ivWallpaper)
        }
    }

    private fun setupPager() {
        pagerAdapter = LauncherPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 1
        binding.viewPager.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        binding.swipeUpIntercept.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        (binding.viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView)?.apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        // Removed setPageTransformer because animating alpha/scale on BlurViews causes extreme lag
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            private var pendingSwipeAd = false

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    if (pendingSwipeAd) {
                        pendingSwipeAd = false
                        app.messages.config.AdsConfigManager.onUserSwiped(requireActivity()) {}
                    }
                }
            }

            override fun onPageSelected(position: Int) {
                viewModel.setPage(position)
                updateChrome(position)
                updateSwipeEnabled()
                pendingSwipeAd = true
            }
        })
    }

    private fun setupPageIndicator() {
        binding.btnPagePhone.setOnClickListener {
            forceCloseDrawer()
            binding.viewPager.setCurrentItem(LauncherPagerAdapter.PAGE_DASHBOARD, true)
        }
        binding.btnPageHome.setOnClickListener {
            forceCloseDrawer()
            binding.viewPager.setCurrentItem(LauncherPagerAdapter.PAGE_HOME, true)
        }
        binding.btnPageApps.setOnClickListener {
            forceCloseDrawer()
            binding.viewPager.setCurrentItem(LauncherPagerAdapter.PAGE_MESSAGE, true)
        }
    }

    private fun setupEdgeTab() {
        binding.edgeTab.setOnClickListener {
            forceCloseDrawer()
            binding.viewPager.setCurrentItem(LauncherPagerAdapter.PAGE_DASHBOARD, true)
        }
    }

    private fun setupDrawerMotion() {
        if (childFragmentManager.findFragmentByTag(DRAWER_TAG) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.drawerHost, AppDrawerFragment(), DRAWER_TAG)
                .commitNow()
        }

        drawerMotion = AppDrawerMotionController(
            homeLayer = binding.swipeUpIntercept,
            blurTarget = binding.ivWallpaper,
            drawerContainer = binding.drawerContainer,
            drawerPanel = binding.drawerDismiss,
            scrim = binding.drawerScrim,
            chromeViews = listOf(binding.pageIndicatorBlur, binding.edgeTab)
        )
    }

    private fun setupDrawerGestures() {
        binding.swipeUpIntercept.callback = object : SwipeUpInterceptLayout.Callback {
            override fun onSwipeUpStart() {
                if (binding.viewPager.currentItem != LauncherPagerAdapter.PAGE_HOME) return
                if (drawerMotion.isAnimating) drawerMotion.cancelAnimations()
                applyBlurActive(-1)
                ensureDrawerHeight()
                binding.viewPager.isUserInputEnabled = false
                drawerMotion.applyProgress(0f)
            }

            override fun onSwipeUpProgress(progress: Float) {
                if (drawerMotion.isAnimating) return
                drawerMotion.applyProgress(progress)
            }

            override fun onSwipeUpEnd(progress: Float, velocityY: Float) {
                val shouldOpen = progress > 0.28f || velocityY > 1200f
                if (shouldOpen) openAppDrawer() else closeAppDrawer()
            }
        }

        binding.drawerDismiss.callback = object : SwipeDownDismissLayout.Callback {
            override fun onSwipeDownProgress(translationY: Float) {
                if (drawerMotion.isAnimating) return
                ensureDrawerHeight()
                val height = binding.drawerContainer.height.toFloat().coerceAtLeast(1f)
                val progress = (1f - (translationY / height)).coerceIn(0f, 1f)
                drawerMotion.applyProgress(progress)
            }

            override fun onSwipeDownEnd(translationY: Float, velocityY: Float) {
                ensureDrawerHeight()
                val height = binding.drawerContainer.height.toFloat().coerceAtLeast(1f)
                val progress = (1f - (translationY / height)).coerceIn(0f, 1f)
                val shouldClose = progress < 0.78f || velocityY > 1200f
                if (shouldClose) closeAppDrawer() else openAppDrawer()
            }
        }

        binding.drawerScrim.setOnClickListener {
            if (drawerMotion.isOpen || drawerMotion.progress > 0.5f) {
                closeAppDrawer()
            }
        }
    }

    fun openAppDrawer() {
        if (_binding == null || !::drawerMotion.isInitialized) return
        ensureDrawerHeight()
        // The drawer applies a RenderEffect to the wallpaper — our blur source —
        // so stop capturing it for the duration.
        applyBlurActive(-1)
        binding.viewPager.isUserInputEnabled = false
        updateSwipeEnabled()
        drawerMotion.openAppDrawer {
            viewModel.setDrawerOpenState(true)
            updateSwipeEnabled()
        }
    }

    fun closeAppDrawer() {
        if (_binding == null || !::drawerMotion.isInitialized) return
        ensureDrawerHeight()
        drawerMotion.closeAppDrawer {
            viewModel.setDrawerOpenState(false)
            updateSwipeEnabled()
            updateChrome(binding.viewPager.currentItem)
        }
    }

    private fun forceCloseDrawer() {
        if (_binding == null || !::drawerMotion.isInitialized) return
        drawerMotion.reset()
        viewModel.setDrawerOpenState(false)
        updateSwipeEnabled()
        updateChrome(binding.viewPager.currentItem)
    }

    private fun ensureDrawerHeight() {
        if (_binding == null || !::drawerMotion.isInitialized) return
        val h = binding.drawerContainer.height.toFloat()
            .takeIf { it > 0f }
            ?: resources.displayMetrics.heightPixels.toFloat()
        drawerMotion.setDrawerHeight(h)
        binding.swipeUpIntercept.drawerHeightPx = h
    }

    private fun updateSwipeEnabled() {
        if (_binding == null || !::drawerMotion.isInitialized) return
        val current = binding.viewPager.currentItem
        val onHome = current == LauncherPagerAdapter.PAGE_HOME
        val isDrawerClosed = !drawerMotion.isOpen && !drawerMotion.isAnimating && drawerMotion.progress < 0.01f
        binding.swipeUpIntercept.enabledIntercept = onHome && isDrawerClosed
        
        // On Video Downloader (page 0), disable ViewPager2 swipe gesture so that full-screen webview,
        // Reels vertical/diagonal swipes, and browser gestures are never intercepted by ViewPager2.
        // Swiping between Home (1) and Messages/Dashboard (2) remains enabled when drawer is closed.
        binding.viewPager.isUserInputEnabled = (current != LauncherPagerAdapter.PAGE_VIDEO_DOWNLOADER) && (!onHome || isDrawerClosed)
        android.util.Log.d("GESTURE_CHECK", "🖐️ [SWIPE_ENABLED] page=$current, onHome=$onHome, isDrawerClosed=$isDrawerClosed -> isUserInputEnabled=${binding.viewPager.isUserInputEnabled}")
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.currentPage.collect { updateChrome(it) } }
                launch {
                    viewModel.openDrawerEvents.collect {
                        if (_binding != null && binding.viewPager.currentItem == LauncherPagerAdapter.PAGE_HOME) {
                            openAppDrawer()
                        }
                    }
                }
                launch {
                    viewModel.closeDrawerEvents.collect {
                        if (::drawerMotion.isInitialized && (drawerMotion.isOpen || drawerMotion.progress > 0.01f)) {
                            closeAppDrawer()
                        }
                    }
                }
                launch {
                    viewModel.navigateToChatsEvents.collect {
                        navigateToChats()
                    }
                }
            }
        }
    }

    private fun updateChrome(page: Int) {
        if (_binding == null) return
        applyBlurActive(page)
        if (::drawerMotion.isInitialized && (drawerMotion.isOpen || drawerMotion.progress > 0.02f || drawerMotion.isAnimating)) return

        val active = ContextCompat.getColor(requireContext(), R.color.white)
        val inactive = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        binding.btnPagePhone.imageTintList = android.content.res.ColorStateList.valueOf(
            if (page == LauncherPagerAdapter.PAGE_DASHBOARD) active else inactive
        )
        binding.btnPageHome.imageTintList = android.content.res.ColorStateList.valueOf(
            if (page == LauncherPagerAdapter.PAGE_HOME) active else inactive
        )
        binding.btnPageApps.imageTintList = android.content.res.ColorStateList.valueOf(
            if (page == LauncherPagerAdapter.PAGE_MESSAGE) active else inactive
        )

        binding.glassPageIndicatorBlur.isVisible = false
        binding.pageIndicatorBlur.isVisible = false
        binding.edgeTab.isVisible = false
        (activity as? LauncherActivity)?.applyLauncherPageSystemBars(page)
        updateSwipeEnabled()
    }

    fun reapplyPageSystemBars() {
        if (_binding == null) return
        (activity as? LauncherActivity)?.applyLauncherPageSystemBars(binding.viewPager.currentItem)
    }

    private fun setupBlur() {
        if (_binding == null) return
        try {
            val decorView = requireActivity().window.decorView as ViewGroup
            val windowBackground = decorView.background
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // BlurView snapshots its blur root into a SOFTWARE canvas on every
                // pre-draw pass. Using rootLauncher meant re-drawing the entire
                // hierarchy — including the Compose page hosted in the ViewPager —
                // once per frame, and Compose's stretch overscroll draws through
                // Canvas.drawRenderNode, which a software canvas rejects with
                // "Software rendering doesn't support drawRenderNode".
                // The indicator floats over the wallpaper, so the wallpaper alone is
                // both the correct blur source and a far cheaper one to capture.
                binding.glassPageIndicatorBlur.setupWith(binding.wallpaperLayer, eightbitlab.com.blurview.RenderEffectBlur())
                    .setFrameClearDrawable(windowBackground)
                    .setBlurRadius(15f)
                blurSupported = true
                applyBlurActive(binding.viewPager.currentItem)
            } else {
                binding.glassPageIndicatorBlur.setBlurEnabled(false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            blurSupported = false
            binding.glassPageIndicatorBlur.setBlurEnabled(false)
        }
    }

    /**
     * The blur re-captures the wallpaper every frame while auto-update is on, so it
     * only runs while the indicator is actually on screen (Home page, drawer shut).
     */
    private fun applyBlurActive(page: Int) {
        if (_binding == null || !blurSupported) return
        val drawerIdle = !::drawerMotion.isInitialized ||
            (!drawerMotion.isOpen && !drawerMotion.isAnimating && drawerMotion.progress < 0.01f)
        val active = page == LauncherPagerAdapter.PAGE_HOME && drawerIdle
        if (active == blurAutoUpdating) return
        blurAutoUpdating = active
        runCatching { binding.glassPageIndicatorBlur.setBlurAutoUpdate(active) }
    }

    /** Called when user presses the system Home button while this is the default launcher. */
    fun onHomePressed() {
        if (!isAdded || _binding == null || !::drawerMotion.isInitialized) return
        if (drawerMotion.isOpen || drawerMotion.isAnimating) {
            closeAppDrawer()
            return
        }
        if (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        binding.viewPager.setCurrentItem(LauncherPagerAdapter.PAGE_HOME, false)
        updateChrome(LauncherPagerAdapter.PAGE_HOME)
    }

    fun navigateToChats() {
        if (!isAdded || _binding == null) return
        forceCloseDrawer()
        binding.viewPager.setCurrentItem(LauncherPagerAdapter.PAGE_VIDEO_DOWNLOADER, true)
        updateChrome(LauncherPagerAdapter.PAGE_VIDEO_DOWNLOADER)
    }

    fun onLauncherBackPressed(): Boolean {
        if (!isAdded || _binding == null || !::drawerMotion.isInitialized) return false
        if (drawerMotion.isOpen || drawerMotion.isAnimating) {
            closeAppDrawer()
            return true
        }
        return when (binding.viewPager.currentItem) {
            LauncherPagerAdapter.PAGE_HOME -> false
            else -> {
                binding.viewPager.setCurrentItem(LauncherPagerAdapter.PAGE_HOME, true)
                true
            }
        }
    }

    // navigate functions removed

    override fun onDestroyView() {
        if (::drawerMotion.isInitialized) drawerMotion.cancelAnimations()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val DRAWER_TAG = "app_drawer"
    }
}
