package app.messages.ui.launcher.drawer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.messages.ui.launcher.adapter.AppIconAdapter
import app.messages.ui.launcher.adapter.RecentAppAdapter
import app.messages.R
import app.messages.data.model.AppCategory
import app.messages.data.model.AppInfo
import app.messages.databinding.FragmentAppDrawerBinding
import app.messages.ui.launcher.LauncherViewModel
import com.google.android.material.tabs.TabLayout

import kotlinx.coroutines.launch


class AppDrawerFragment : Fragment() {

    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppDrawerViewModel by viewModels { AppDrawerViewModel.Factory(requireContext()) }
    private val launcherViewModel: LauncherViewModel by activityViewModels { app.messages.ui.launcher.LauncherViewModel.Factory(requireContext()) }
    
    private lateinit var adapter: AppIconAdapter
    private lateinit var recentAdapter: RecentAppAdapter
    private var contextPopup: AppContextPopup? = null
    private var packageRemovedReceiver: BroadcastReceiver? = null
    private var pendingUninstallLabel: String? = null
    private var pendingUninstallPackage: String? = null
    private var uninstallInProgress = false
    private var lastDrawerAppearance: String? = null
    private var favoritesTabVisible = false
    private var isRecentExpanded = true
    private var isSearchMode = false

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                binding.etSearch.setText(spokenText)
                binding.etSearch.setSelection(spokenText.length)
                viewModel.onQueryChanged(spokenText)
            }
        }
    }

    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.search_apps_hint))
        }
        try {
            voiceSearchLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Voice search not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        contextPopup = AppContextPopup(
            onAppInfo = viewModel::onOpenAppInfo,
            onToggleFavorite = viewModel::onToggleFavorite,
            onUninstall = ::startUninstall
        )
        adapter = AppIconAdapter(
            onClick = {
                app.messages.config.AdsConfigManager.onAppClicked(requireActivity(), it.packageName) {
                    val prefs = app.messages.preferences.PreferenceHelper.getInstance(requireContext())
                    if (it.packageName == requireContext().packageName) {
                        launcherViewModel.requestNavigateToChats()
                    } else {
                        if (prefs.getSearchCloseDrawerOnAppOpen()) {
                            launcherViewModel.requestCloseDrawer()
                        }
                        viewModel.openApp(it)
                    }
                }
            },
            onLongClick = { app, anchor ->
                contextPopup?.show(anchor, app)
            }
        ).apply {
            onDimmedClick = { exitSpotlightMode() }
        }
        binding.rvApps.layoutManager = GridLayoutManager(requireContext(), 4)
        binding.rvApps.adapter = adapter
        binding.rvApps.itemAnimator = null
        binding.rvApps.setHasFixedSize(true)
        binding.rvApps.setItemViewCacheSize(24)
        binding.rvApps.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING) {
                    exitSpotlightMode()
                }
            }
        })

        recentAdapter = RecentAppAdapter(
            onClick = {
                app.messages.config.AdsConfigManager.onAppClicked(requireActivity(), it.packageName) {
                    val prefs = app.messages.preferences.PreferenceHelper.getInstance(requireContext())
                    if (it.packageName == requireContext().packageName) {
                        launcherViewModel.requestNavigateToChats()
                    } else {
                        if (prefs.getSearchCloseDrawerOnAppOpen()) {
                            launcherViewModel.requestCloseDrawer()
                        }
                        viewModel.openApp(it)
                    }
                }
            },
            onLongClick = { app, anchor ->
                contextPopup?.show(anchor, app)
            }
        )
        binding.rvRecentApps.layoutManager = GridLayoutManager(requireContext(), 5)
        binding.rvRecentApps.adapter = recentAdapter
        binding.rvRecentApps.itemAnimator = null

        binding.layoutRecentHeader.setOnClickListener {
            isRecentExpanded = !isRecentExpanded
            binding.cardRecent.visibility = if (isRecentExpanded) View.VISIBLE else View.GONE
            binding.ivRecentToggle.animate()
                .rotation(if (isRecentExpanded) 0f else 180f)
                .setDuration(200)
                .start()
        }

        // Tap empty area in RecyclerView to exit filtered section mode
        binding.rvApps.setOnTouchListener { _, event ->
            if (isSectionFiltered && event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                val child = binding.rvApps.findChildViewUnder(event.x, event.y)
                if (child == null) {
                    exitSpotlightMode()
                }
            }
            false
        }

        // Tap root drawer background to exit filtered section mode
        binding.rootDrawer.setOnClickListener {
            if (isSectionFiltered) {
                exitSpotlightMode()
            }
        }

        // Back button exits filtered section mode before closing drawer
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        setupTabs(showFavorites = false)
        setupSearch()
        setupFastScroller()
        observe()

        // Pre-reserve native ad container space to prevent layout jump and alphabet overlap
        val initialConfig = app.messages.config.AdsConfigManager.config
        val isPrem = app.messages.preferences.PreferenceHelper.getInstance(requireContext()).isPremium()
        if (!isPrem && initialConfig.canShowNative && initialConfig.nativeAdIdAppDrawer.isNotBlank() && initialConfig.nativeAdIdAppDrawer != "11") {
            binding.nativeAdContainer.visibility = View.VISIBLE
            binding.nativeAdShimmer.root.visibility = View.VISIBLE
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    launcherViewModel.drawerOpen.collect { isOpen ->
                        if (isOpen) {
                            exitSpotlightMode(resetToTop = true)
                            loadNativeAd(forceRefresh = true)
                        } else {
                            exitSpotlightMode(resetToTop = true)
                            isAdLoading = false
                        }
                    }
                }

                launch {
                    app.messages.config.AdsConfigManager.configFlow.collect { config ->
                        val isPremium = app.messages.preferences.PreferenceHelper.getInstance(requireContext()).isPremium()
                        if (isPremium || !config.canShowNative || config.nativeAdIdAppDrawer.isBlank() || config.nativeAdIdAppDrawer == "11") {
                            binding.nativeAdContainer.visibility = View.GONE
                        } else if (launcherViewModel.drawerOpen.value && (binding.nativeAdView.childCount == 0 || binding.nativeAdContainer.visibility == View.GONE)) {
                            loadNativeAd(forceRefresh = false)
                        }
                    }
                }
            }
        }
    }

    private var isAdLoading = false
    private var currentNativeAd: com.google.android.gms.ads.nativead.NativeAd? = null

    private fun loadNativeAd(forceRefresh: Boolean = false) {
        val config = app.messages.config.AdsConfigManager.config
        val isPremium = app.messages.preferences.PreferenceHelper.getInstance(requireContext()).isPremium()

        if (isPremium || !config.canShowNative || config.nativeAdIdAppDrawer.isBlank() || config.nativeAdIdAppDrawer == "11") {
            binding.nativeAdContainer.visibility = View.GONE
            return
        }

        if (isAdLoading) return

        val hasExistingAd = binding.nativeAdView.childCount > 0 && currentNativeAd != null
        if (!forceRefresh && hasExistingAd && binding.nativeAdContainer.visibility == View.VISIBLE) {
            return
        }

        isAdLoading = true

        binding.nativeAdContainer.visibility = View.VISIBLE
        if (!hasExistingAd) {
            // First time loading: show shimmer because there is no previous ad yet
            binding.nativeAdShimmer.root.visibility = View.VISIBLE
            binding.nativeAdView.visibility = View.GONE
        } else {
            // Re-opening drawer: keep previous ad visible on screen (NO shimmer!) while fresh ad loads
            binding.nativeAdShimmer.root.visibility = View.GONE
            binding.nativeAdView.visibility = View.VISIBLE
        }

        app.messages.config.AdsConfigManager.loadNativeAd(
            context = requireActivity(),
            adUnitId = config.nativeAdIdAppDrawer,
            onLoaded = { nativeAd ->
                isAdLoading = false
                if (!isAdded || view == null) {
                    // Save to RAM cache so it is not wasted
                    app.messages.config.AdsConfigManager.cacheNativeAd(config.nativeAdIdAppDrawer, nativeAd)
                    return@loadNativeAd
                }

                if (!launcherViewModel.drawerOpen.value) {
                    // Zero Waste: User closed drawer -> save to RAM cache for next open!
                    app.messages.config.AdsConfigManager.cacheNativeAd(config.nativeAdIdAppDrawer, nativeAd)
                    return@loadNativeAd
                }

                // Clean up old ad instance before attaching new one
                currentNativeAd?.destroy()
                currentNativeAd = nativeAd

                binding.nativeAdShimmer.root.visibility = View.GONE
                binding.nativeAdView.visibility = View.VISIBLE
                binding.nativeAdView.removeAllViews()

                val adView = layoutInflater.inflate(R.layout.ads_native, null, false) as com.google.android.gms.ads.nativead.NativeAdView
                app.messages.config.AdsConfigManager.populateNativeAdView(nativeAd, adView)
                binding.nativeAdView.addView(adView)
            },
            onFailed = {
                isAdLoading = false
                if (isAdded && view != null) {
                    if (!hasExistingAd) {
                        binding.nativeAdContainer.visibility = View.GONE
                    }
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        uninstallInProgress = false
        val prefs = app.messages.preferences.PreferenceHelper.getInstance(requireContext())
        binding.blurSearch.visibility = if (prefs.getDrawerSearchBarEnabled()) View.VISIBLE else View.GONE
        if (prefs.getDrawerSearchBarEnabled() && prefs.getSearchAutoOpenKeyboard()) {
            enterSearchMode()
            binding.etSearch.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        } else if (!isSearchMode) {
            showAppsListUi()
        }
        // Only rebuild the grid when a setting the bind reads has actually changed
        // (the user came back from App drawer settings). Rebinding on every resume
        // made every return from another app re-lay-out the whole drawer.
        val appearance = drawerAppearanceSignature(prefs)
        if (appearance != lastDrawerAppearance) {
            lastDrawerAppearance = appearance
            if (::adapter.isInitialized) {
                adapter.notifyDataSetChanged()
            }
        }
        viewModel.refreshApps()
    }

    /** Everything the app-icon binds read out of preferences. */
    private fun drawerAppearanceSignature(
        prefs: app.messages.preferences.PreferenceHelper
    ): String = "${prefs.getDrawerHideLabels()}|${prefs.getDrawerTextSize()}|${prefs.getDrawerIconSize()}"

    private fun setupTabs(showFavorites: Boolean) {
        favoritesTabVisible = showFavorites
        val selected = viewModel.selectedCategory.value
        binding.tabCategories.clearOnTabSelectedListeners()
        binding.tabCategories.removeAllTabs()

        val tabs = buildList {
            add(AppCategory.ALL to getString(R.string.tab_all))
            if (showFavorites) {
                add(AppCategory.FAVORITES to getString(R.string.tab_favorites))
            }
            add(AppCategory.AUDIO to getString(R.string.tab_audio))
            add(AppCategory.GAMES to getString(R.string.tab_games))
            add(AppCategory.PHOTOGRAPHY to getString(R.string.tab_photography))
            add(AppCategory.PRODUCTIVITY to getString(R.string.tab_productivity))
            add(AppCategory.SOCIAL to getString(R.string.tab_social))
            add(AppCategory.VIDEO to getString(R.string.tab_video))
            add(AppCategory.OTHERS to getString(R.string.tab_others))
        }

        var selectedIndex = 0
        tabs.forEachIndexed { index, (category, title) ->
            binding.tabCategories.addTab(
                binding.tabCategories.newTab().setText(title).setTag(category),
                false
            )
            if (category == selected) selectedIndex = index
        }

        val safeIndex = when {
            selected == AppCategory.FAVORITES && !showFavorites -> 0
            else -> selectedIndex
        }
        binding.tabCategories.getTabAt(safeIndex)?.select()
        val resolvedCategory = binding.tabCategories.getTabAt(safeIndex)?.tag as? AppCategory
            ?: AppCategory.ALL
        viewModel.onCategorySelected(resolvedCategory)

        binding.tabCategories.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                exitSpotlightMode()
                viewModel.onCategorySelected(tab.tag as AppCategory)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
    }

    private var isSectionFiltered = false
    private var lastSelectedLetter: Char? = null
    private var lastSectionOffsetY: Float = 0f

    private val backCallback = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (isSectionFiltered) {
                exitSpotlightMode()
            } else if (isSearchMode || binding.etSearch.text?.isNotEmpty() == true) {
                clearSearchAndRestoreApps()
            }
        }
    }

    private fun exitSpotlightMode(resetToTop: Boolean = false) {
        binding.alphabetIndexView.clearSelection()

        if (isSectionFiltered) {
            val letterToRestore = if (resetToTop) null else lastSelectedLetter
            val offsetToRestore = if (resetToTop) 0 else lastSectionOffsetY.toInt()
            isSectionFiltered = false
            lastSelectedLetter = null
            lastSectionOffsetY = 0f
            backCallback.isEnabled = isSearchMode
            val fullList = viewModel.apps.value
            adapter.submitList(fullList) {
                binding.rvApps.animate().cancel()
                binding.rvApps.translationY = 0f
                if (resetToTop || letterToRestore == null) {
                    binding.rvApps.scrollToPosition(0)
                } else {
                    val targetIndex = if (letterToRestore == '#') {
                        fullList.indexOfFirst { app ->
                            val initial = app.label.firstOrNull()?.uppercaseChar()
                            initial == null || !initial.isLetter()
                        }
                    } else {
                        fullList.indexOfFirst { app ->
                            app.label.firstOrNull()?.equals(letterToRestore, ignoreCase = true) == true
                        }
                    }
                    if (targetIndex != -1) {
                        (binding.rvApps.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(targetIndex, offsetToRestore)
                    }
                }
            }
        } else {
            binding.rvApps.animate().cancel()
            binding.rvApps.translationY = 0f
            if (resetToTop) {
                binding.rvApps.scrollToPosition(0)
            }
        }
    }

    private fun setupFastScroller() {
        val indexView = binding.alphabetIndexView

        indexView.onLetterSelectedListener = { letter, touchY ->
            lastSelectedLetter = letter
            val allApps = viewModel.apps.value
            val matching = if (letter == '#') {
                allApps.filter { app ->
                    val initial = app.label.firstOrNull()?.uppercaseChar()
                    initial == null || !initial.isLetter()
                }
            } else {
                allApps.filter { app ->
                    app.label.firstOrNull()?.equals(letter, ignoreCase = true) == true
                }
            }

            if (matching.isNotEmpty()) {
                isSectionFiltered = true
                backCallback.isEnabled = true
                adapter.submitList(matching)

                // Smoothly position matching apps aligned with the vertical touch position
                val rvTop = binding.rvApps.top.toFloat()
                val rvHeight = binding.rvApps.height.toFloat().coerceAtLeast(1f)
                val itemHeightApprox = 90f * resources.displayMetrics.density
                val rowCount = (matching.size + 3) / 4
                val contentHeight = rowCount * itemHeightApprox
                val targetCenterY = (touchY - rvTop - (itemHeightApprox / 2f)).coerceIn(0f, (rvHeight - contentHeight).coerceAtLeast(0f))
                lastSectionOffsetY = targetCenterY
                binding.rvApps.animate().translationY(targetCenterY).setDuration(60).start()
            }
        }

        indexView.onDragEndedListener = {
            // Keep matching apps visible at that position on release so user can tap the app!
        }
    }

    private fun setupSearch() {
        // Keep keyboard action button always as Search (never changes to Enter/Done)
        binding.etSearch.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        binding.etSearch.inputType = android.text.InputType.TYPE_CLASS_TEXT
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                // Trigger search without dismissing keyboard or changing icon
                val query = binding.etSearch.text?.toString().orEmpty()
                viewModel.onQueryChanged(query)
                true
            } else false
        }
        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                enterSearchMode()
            }
        }
        binding.etSearch.setOnClickListener {
            enterSearchMode()
        }
        binding.etSearch.doAfterTextChanged { text ->
            exitSpotlightMode()
            val query = text?.toString().orEmpty()
            viewModel.onQueryChanged(query)
            if (query.isNotEmpty()) {
                isSearchMode = true
                backCallback.isEnabled = true
                binding.btnClearSearch.visibility = View.VISIBLE
                binding.btnVoiceSearch.visibility = View.GONE
                binding.layoutRecent.visibility = View.GONE
                binding.rvApps.visibility = View.VISIBLE
                binding.alphabetIndexView.visibility = View.GONE
                updateEmptyState(appsEmpty = adapter.currentList.isEmpty())
            } else {
                binding.btnClearSearch.visibility = View.GONE
                binding.btnVoiceSearch.visibility = View.VISIBLE
                if (isSearchMode || binding.etSearch.hasFocus()) {
                    showSearchHomeUi()
                } else {
                    showAppsListUi()
                }
            }
        }
        binding.btnClearSearch.setOnClickListener {
            exitSpotlightMode()
            binding.etSearch.setText("")
            viewModel.onQueryChanged("")
            showSearchHomeUi()
        }
        binding.btnVoiceSearch.setOnClickListener {
            startVoiceSearch()
        }
        binding.btnCancelSearch.setOnClickListener {
            exitSpotlightMode()
            clearSearchAndRestoreApps()
        }
        // Same destination as the home screen's gear, so settings are reachable
        // without leaving the drawer.
        binding.btnDrawerSettings.setOnClickListener {
            startActivity(
                android.content.Intent(
                    requireContext(),
                    app.messages.ui.launcher.settings.LauncherMainSettingsActivity::class.java,
                )
            )
        }
    }

    private fun enterSearchMode() {
        isSearchMode = true
        backCallback.isEnabled = true
        val query = binding.etSearch.text?.toString().orEmpty()
        binding.btnCancelSearch.visibility = View.VISIBLE
        binding.viewDividerSearch.visibility = View.VISIBLE
        binding.btnDrawerSettings.visibility = View.GONE
        if (query.isEmpty()) {
            showSearchHomeUi()
        } else {
            binding.layoutRecent.visibility = View.GONE
            binding.rvApps.visibility = View.VISIBLE
            binding.alphabetIndexView.visibility = View.GONE
            binding.btnClearSearch.visibility = View.VISIBLE
            binding.btnVoiceSearch.visibility = View.GONE
        }
    }

    private fun showSearchHomeUi() {
        binding.btnCancelSearch.visibility = View.VISIBLE
        binding.viewDividerSearch.visibility = View.VISIBLE
        binding.btnDrawerSettings.visibility = View.GONE
        binding.btnVoiceSearch.visibility = View.VISIBLE
        binding.btnClearSearch.visibility = View.GONE

        binding.layoutRecent.visibility = View.VISIBLE
        binding.cardRecent.visibility = if (isRecentExpanded) View.VISIBLE else View.GONE
        binding.rvApps.visibility = View.GONE
        binding.alphabetIndexView.visibility = View.GONE
        binding.emptySearchState.visibility = View.GONE
        binding.tabCategories.visibility = View.GONE
    }

    private fun clearSearchAndRestoreApps() {
        isSearchMode = false
        backCallback.isEnabled = isSectionFiltered
        binding.etSearch.setText("")
        viewModel.onQueryChanged("")
        hideKeyboard()
        selectAllCategoryTab()
        showAppsListUi()
    }

    private fun selectAllCategoryTab() {
        viewModel.onCategorySelected(AppCategory.ALL)
        for (i in 0 until binding.tabCategories.tabCount) {
            val tab = binding.tabCategories.getTabAt(i) ?: continue
            if (tab.tag == AppCategory.ALL) {
                tab.select()
                break
            }
        }
    }

    private fun showAppsListUi() {
        binding.btnCancelSearch.visibility = View.GONE
        binding.viewDividerSearch.visibility = View.GONE
        binding.btnClearSearch.visibility = View.GONE
        binding.btnVoiceSearch.visibility = View.GONE
        binding.btnDrawerSettings.visibility = View.VISIBLE
        binding.layoutRecent.visibility = View.GONE
        binding.emptySearchState.visibility = View.GONE
        binding.tabCategories.visibility = View.GONE
        binding.rvApps.visibility = View.VISIBLE
        binding.alphabetIndexView.visibility = View.VISIBLE
    }

    private fun updateEmptyState(appsEmpty: Boolean) {
        val query = binding.etSearch.text?.toString().orEmpty().trim()
        val showSearchEmpty = appsEmpty && query.isNotEmpty()

        if (!showSearchEmpty) {
            if (isSearchMode && query.isEmpty()) {
                showSearchHomeUi()
            } else {
                showAppsListUi()
            }
            return
        }

        binding.emptySearchState.visibility = View.VISIBLE
        binding.tabCategories.visibility = View.GONE
        binding.layoutRecent.visibility = View.GONE
        binding.rvApps.visibility = View.INVISIBLE
        binding.alphabetIndexView.visibility = View.GONE
        binding.tvEmptyTitle.text = getString(R.string.no_apps_found_matching, query)
        binding.tvEmptySubtitle.visibility = View.VISIBLE
    }

    private fun focusSearchField() {
        enterSearchMode()
        val editText = _binding?.etSearch ?: return
        editText.post {
            if (_binding == null) return@post
            editText.requestFocus()
            editText.setSelection(editText.text?.length ?: 0)
            val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
            imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val editText = _binding?.etSearch ?: return
        editText.clearFocus()
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(editText.windowToken, 0)
    }

    private fun startUninstall(appInfo: AppInfo) {
        if (!appInfo.canUninstall) return
        contextPopup?.dismiss()

        val appIcon = app.messages.ui.launcher.adapter.AppIconCache.peek(appInfo) ?: runCatching {
            requireContext().packageManager.getApplicationIcon(appInfo.packageName)
        }.getOrNull()

        app.messages.ui.launcher.drawer.AppActionCache.setPendingUninstall(
            appInfo.packageName,
            appInfo.label,
            appIcon
        )

        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:${appInfo.packageName}")
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("AppDrawer", "Failed to launch package uninstaller for ${appInfo.packageName}", e)
        }
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.apps.collect { list ->
                        adapter.submitList(list)
                        if (isSearchMode && binding.etSearch.text?.isNotEmpty() == true) {
                            updateEmptyState(appsEmpty = list.isEmpty())
                        }
                    }
                }
                launch {
                    viewModel.recentApps.collect { list ->
                        recentAdapter.submitList(list)
                        if (isSearchMode && binding.etSearch.text.isNullOrEmpty()) {
                            binding.layoutRecent.visibility = View.VISIBLE
                            binding.rvApps.visibility = View.GONE
                        }
                    }
                }
                launch {
                    viewModel.hasFavorites.collect { hasFavorites ->
                        if (hasFavorites != favoritesTabVisible) {
                            setupTabs(showFavorites = hasFavorites)
                        }
                    }
                }
                launch {
                    launcherViewModel.focusDrawerSearchEvents.collect {
                        focusSearchField()
                    }
                }
                launch {
                    launcherViewModel.drawerOpen.collect { open ->
                        if (!open) {
                            hideKeyboard()
                            clearSearchAndRestoreApps()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        contextPopup?.dismiss()
        contextPopup = null
        currentNativeAd?.destroy()
        currentNativeAd = null
        super.onDestroyView()
        _binding = null
    }
}
