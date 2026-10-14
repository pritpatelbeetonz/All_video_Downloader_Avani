package app.messages.ui.launcher.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.messages.config.AdsConfigManager
import app.messages.ui.launcher.adapter.DockAdapter
import app.messages.ui.launcher.adapter.WorkspaceIconAdapter
import app.messages.R
import app.messages.data.model.AppInfo
import app.messages.databinding.FragmentHomeBinding
import app.messages.ui.launcher.LauncherViewModel


import app.messages.ui.launcher.drawer.AppContextPopup

import kotlinx.coroutines.launch


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels { HomeViewModel.Factory(requireContext()) }
    private val launcherViewModel: LauncherViewModel by activityViewModels { app.messages.ui.launcher.LauncherViewModel.Factory(requireContext()) }

    private lateinit var workspaceAdapter: WorkspaceIconAdapter
    private lateinit var dockAdapter: DockAdapter
    private var contextPopup: AppContextPopup? = null
    private var packageRemovedReceiver: BroadcastReceiver? = null
    private var pendingUninstallLabel: String? = null
    private var pendingUninstallPackage: String? = null
    private var uninstallInProgress = false
    private var lastHomeAppearance: String? = null

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            _binding?.tvClock?.text = viewModel.currentTime()
            _binding?.tvHomeDate?.text = viewModel.currentDate()
            clockHandler.postDelayed(this, 30_000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        contextPopup = AppContextPopup(
            onAppInfo = viewModel::onOpenAppInfo,
            onToggleFavorite = viewModel::onToggleFavorite,
            onUninstall = ::startUninstall
        )

        workspaceAdapter = WorkspaceIconAdapter(
            onClick = { appInfo ->
                app.messages.config.AdsConfigManager.onAppClicked(requireActivity(), appInfo.packageName) {
                    if (appInfo.packageName == requireContext().packageName) {
                        launcherViewModel.requestNavigateToChats()
                    } else {
                        viewModel.openApp(appInfo)
                    }
                }
            },
            onLongClick = { appInfo, anchor -> contextPopup?.show(anchor, appInfo) }
        )
        binding.rvWorkspace.layoutManager = GridLayoutManager(requireContext(), 5)
        binding.rvWorkspace.adapter = workspaceAdapter
        binding.rvWorkspace.itemAnimator = null
        binding.rvWorkspace.setHasFixedSize(false)
        binding.rvWorkspace.isNestedScrollingEnabled = false
        binding.rvWorkspace.elevation = 12f
        binding.rvWorkspace.bringToFront()
        binding.root.post { binding.rvWorkspace.bringToFront() }

        dockAdapter = DockAdapter(
            onClick = { appInfo ->
                app.messages.config.AdsConfigManager.onAppClicked(requireActivity(), appInfo.packageName) {
                    if (appInfo.packageName == requireContext().packageName) {
                        launcherViewModel.requestNavigateToChats()
                    } else {
                        viewModel.openApp(appInfo)
                    }
                }
            },
            onLongClick = { appInfo, anchor -> contextPopup?.show(anchor, appInfo) }
        )
        binding.rvDock.layoutManager = GridLayoutManager(requireContext(), 5)
        binding.rvDock.adapter = dockAdapter
        binding.rvDock.itemAnimator = null
        binding.rvDock.setHasFixedSize(true)

        val rvToolsFolderPreview = binding.root.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvToolsFolderPreview)
        val ivToolsFolderIcon = binding.root.findViewById<android.widget.ImageView>(R.id.ivToolsFolderIcon)
        val tvToolsFolderAdBadge = binding.root.findViewById<android.widget.TextView>(R.id.tvToolsFolderAdBadge)
        
        rvToolsFolderPreview.layoutManager = GridLayoutManager(requireContext(), 3)
        val previewAdapter = FolderPreviewAdapter()
        rvToolsFolderPreview.adapter = previewAdapter

        rvToolsFolderPreview.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                binding.llToolsFolder.performClick()
            }
            true
        }

        binding.llToolsFolder.setOnClickListener {
            val isEnabled = app.messages.config.ToolsConfigManager.isToolsEnabledFlow.value
            
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_tools_folder, null)
            val rvToolsGrid = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvToolsGrid)
            
            val dialog = android.app.AlertDialog.Builder(requireContext(), android.R.style.Theme_Material_Light_Dialog_Alert)
                .setView(dialogView)
                .create()

            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            
            if (isEnabled) {
                // Show remote config tools
                val toolsAdapter = app.messages.ui.launcher.adapter.ToolsAdapter(
                    onToolClick = { tool ->
                        dialog.dismiss()
                        try {
                            val customTabsIntent = androidx.browser.customtabs.CustomTabsIntent.Builder()
                                .setShowTitle(true)
                                .build()
                            customTabsIntent.launchUrl(requireContext(), Uri.parse(tool.url))
                        } catch (e: Exception) {
                            }
                    }
                )
                rvToolsGrid.adapter = toolsAdapter
                toolsAdapter.submitList(app.messages.config.ToolsConfigManager.toolsFlow.value)
            } else {
                // Show system default apps
                val appAdapter = app.messages.ui.launcher.adapter.AppIconAdapter(
                    onClick = { app ->
                        dialog.dismiss()
                        if (app.packageName == requireContext().packageName) {
                            launcherViewModel.requestNavigateToChats()
                        } else {
                            viewModel.openApp(app)
                        }
                    },
                    isDarkText = true
                )
                rvToolsGrid.adapter = appAdapter
                
                // Try to find common default system apps
                val allApps = viewModel.workspaceApps.value
                val matchedApps = allApps.filter {
                    val label = it.label.lowercase()
                    label.contains("camera") || label.contains("clock") || 
                    label.contains("settings") || label.contains("calculator") ||
                    label.contains("gallery") || label.contains("photos")
                }
                
                val fallbackApps = buildList {
                    addAll(matchedApps)
                    addAll(allApps.filter { it !in matchedApps })
                }
                
                appAdapter.submitList(fallbackApps)
            }
            
            dialog.show()
        }
        
        binding.tvClock.text = viewModel.currentTime()
        binding.tvHomeDate.text = viewModel.currentDate()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            updateDockMargin(insets)
            insets
        }

        setupBlur()
        setupClicks()
        observe()
        
        binding.root.alpha = 0f
        binding.root.animate().alpha(1f).setDuration(220).start()
    }

    private fun setupBlur() {
       /* val root = requireActivity().findViewById<ViewGroup>(android.R.id.content)
        BlurHelper.setup(
            binding.glassSearchBlur,
            root,
            ContextCompat.getColor(requireContext(), R.color.glass_dark),
            18f
        )*/
    }

    private fun setupClicks() {
        binding.googleSearchBar.setOnClickListener { openGoogleSearchScreen() }
        binding.ivSearchIcon.setOnClickListener { openGoogleSearchScreen() }
        binding.tvSearchText.setOnClickListener { openGoogleSearchScreen() }
        binding.ivMic.setOnClickListener { openGoogleVoice() }
        binding.ivSettings.setOnClickListener {
            startActivity(Intent(requireContext(), app.messages.ui.launcher.settings.LauncherMainSettingsActivity::class.java))
        }
        binding.cardHomeWeather.setOnClickListener {
            runCatching {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.google.com/search?q=weather"),
                    )
                )
            }
        }
        binding.glassSearchBar.setOnClickListener {
            launcherViewModel.requestOpenDrawerWithSearch()
        }
    }

    private fun openGoogleSearchScreen() {
        startActivity(Intent(requireContext(), app.messages.search.GoogleSearchActivity::class.java))
    }

    private fun openGoogleVoice() {
        val intent = Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .recoverCatching {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
    }

    private fun startUninstall(appInfo: AppInfo) {
        if (!appInfo.canUninstall || uninstallInProgress) return
        uninstallInProgress = true
        contextPopup?.dismiss()

        pendingUninstallPackage = appInfo.packageName
        pendingUninstallLabel = appInfo.label
        registerPackageRemovedReceiver()

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
            android.util.Log.e("HomeFragment", "Failed to launch package uninstaller for ${appInfo.packageName}", e)
            uninstallInProgress = false
            pendingUninstallPackage = null
            pendingUninstallLabel = null
            unregisterPackageRemovedReceiver()
        }
    }

    private fun registerPackageRemovedReceiver() {
        unregisterPackageRemovedReceiver()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_PACKAGE_REMOVED) return
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                val removedPackage = intent.data?.schemeSpecificPart ?: return
                if (removedPackage != pendingUninstallPackage) return
                val label = pendingUninstallLabel.orEmpty().ifBlank { removedPackage }
                pendingUninstallPackage = null
                pendingUninstallLabel = null
                uninstallInProgress = false
                viewModel.refreshApps()
                unregisterPackageRemovedReceiver()
            }
        }
        packageRemovedReceiver = receiver
        val filter = IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply {
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            requireContext().applicationContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterPackageRemovedReceiver() {
        packageRemovedReceiver?.let { receiver ->
            runCatching {
                requireContext().applicationContext.unregisterReceiver(receiver)
            }
        }
        packageRemovedReceiver = null
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.workspaceApps.collect { workspaceAdapter.submitList(it); updateFolderPreview() } }
                launch { viewModel.dockApps.collect { dockAdapter.submitList(it) } }
                
                launch {
                    app.messages.config.ToolsConfigManager.isToolsEnabledFlow.collect { updateFolderPreview() }
                }
                
                launch {
                    app.messages.config.ToolsConfigManager.toolsFlow.collect { updateFolderPreview() }
                }
            }
        }
    }

    private fun updateFolderPreview() {
        val isEnabled = app.messages.config.ToolsConfigManager.isToolsEnabledFlow.value
        val tools = app.messages.config.ToolsConfigManager.toolsFlow.value
        
        val ivToolsFolderIcon = binding.root.findViewById<android.widget.ImageView>(R.id.ivToolsFolderIcon)
        val rvToolsFolderPreview = binding.root.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvToolsFolderPreview)
        val tvToolsFolderAdBadge = binding.root.findViewById<android.widget.TextView>(R.id.tvToolsFolderAdBadge)
        
        val previewAdapter = rvToolsFolderPreview.adapter as? FolderPreviewAdapter
        
        if (isEnabled) {
            previewAdapter?.items = tools
            previewAdapter?.useApps = false
            
            previewAdapter?.notifyDataSetChanged()
            
            if (tools.isNotEmpty()) {
                ivToolsFolderIcon.visibility = View.GONE
                rvToolsFolderPreview.visibility = View.VISIBLE
                val adsConfig = app.messages.config.AdsConfigManager.config
                tvToolsFolderAdBadge.visibility = if (adsConfig.isNativeOn) View.VISIBLE else View.GONE
            } else {
                ivToolsFolderIcon.visibility = View.VISIBLE
                rvToolsFolderPreview.visibility = View.GONE
                tvToolsFolderAdBadge.visibility = View.GONE
            }
        } else {
            val allApps = viewModel.workspaceApps.value
            val matchedApps = allApps.filter {
                val label = it.label.lowercase()
                label.contains("camera") || label.contains("clock") || 
                label.contains("settings") || label.contains("calculator") ||
                label.contains("gallery") || label.contains("photos")
            }
            
            val fallbackApps = buildList {
                addAll(matchedApps)
                addAll(allApps.filter { it !in matchedApps })
            }

            previewAdapter?.apps = fallbackApps
            previewAdapter?.useApps = true

            previewAdapter?.notifyDataSetChanged()

            if (fallbackApps.isNotEmpty()) {
                ivToolsFolderIcon.visibility = View.GONE
                rvToolsFolderPreview.visibility = View.VISIBLE
                tvToolsFolderAdBadge.visibility = View.GONE
            } else {
                ivToolsFolderIcon.visibility = View.VISIBLE
                rvToolsFolderPreview.visibility = View.GONE
                tvToolsFolderAdBadge.visibility = View.GONE
            }
        }
    }

    private fun updateDockMargin(insets: WindowInsetsCompat? = null) {
        val prefs = app.messages.preferences.PreferenceHelper.getInstance(requireContext())
        val isNavBarVisible = prefs.getNavigationBarVisible()
        val currentInsets = insets ?: ViewCompat.getRootWindowInsets(binding.root)
        val nav = if (isNavBarVisible) (currentInsets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0) else 0
        val dockMargin = if (isNavBarVisible) resources.getDimensionPixelSize(R.dimen.dock_margin_bottom) else 0

        (binding.dockContainer.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)
            ?.let { lp ->
                lp.bottomMargin = dockMargin + nav
                binding.dockContainer.layoutParams = lp
            }
    }

    override fun onResume() {
        super.onResume()
        // Force refresh remote config data whenever the launcher screen is shown
        app.messages.config.ToolsConfigManager.fetchTools()
        uninstallInProgress = false
        clockHandler.post(clockTick)
        updateDockMargin()
        // The icon views are only rebuilt when a setting the bind reads has actually
        // changed (the user came back from Home screen settings). Rebinding on every
        // resume made every return from another app re-lay-out the whole workspace.
        val appearance = homeAppearanceSignature()
        if (appearance != lastHomeAppearance) {
            lastHomeAppearance = appearance
            if (::workspaceAdapter.isInitialized) {
                workspaceAdapter.notifyDataSetChanged()
            }
            if (::dockAdapter.isInitialized) {
                dockAdapter.notifyDataSetChanged()
            }
        }
    }

    /** Everything the workspace/dock binds read out of preferences. */
    private fun homeAppearanceSignature(): String {
        val prefs = app.messages.preferences.PreferenceHelper.getInstance(requireContext())
        return "${prefs.getHomeHideLabels()}|${prefs.getHomeTextSize()}|${prefs.getHomeIconSize()}"
    }

    override fun onPause() {
        clockHandler.removeCallbacks(clockTick)
        super.onPause()
    }

    override fun onDestroyView() {
        unregisterPackageRemovedReceiver()
        contextPopup?.dismiss()
        contextPopup = null
        clockHandler.removeCallbacks(clockTick)
        super.onDestroyView()
        _binding = null
    }

    inner class FolderPreviewAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
        var items: List<app.messages.data.model.ToolModel> = emptyList()
        var apps: List<app.messages.data.model.AppInfo> = emptyList()
        var useApps: Boolean = false

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
            val iv = android.widget.ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                setPadding(2, 2, 2, 2)
            }
            return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(iv) {}
        }
        override fun getItemCount() = if (useApps) minOf(9, apps.size) else minOf(9, items.size)
        override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
            val iv = holder.itemView as android.widget.ImageView
            val displayMetrics = requireContext().resources.displayMetrics
            val itemSize = (12 * displayMetrics.density).toInt()
            val margin = (1 * displayMetrics.density).toInt()
            val lp = androidx.recyclerview.widget.RecyclerView.LayoutParams(itemSize, itemSize)
            lp.setMargins(margin, margin, margin, margin)
            iv.layoutParams = lp
            if (useApps) {
                com.bumptech.glide.Glide.with(iv).clear(iv)
                iv.background = null
                iv.backgroundTintList = null
                iv.clipToOutline = false
                iv.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                
                val app = apps[position]
                if (app.icon != null) {
                    com.bumptech.glide.Glide.with(iv).load(app.icon).into(iv)
                } else {
                    iv.setImageDrawable(null)
                    val tag = "${app.packageName}:${app.activityName}"
                    iv.tag = tag
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            val pm = iv.context.packageManager
                            val comp = android.content.ComponentName(app.packageName, app.activityName)
                            val drawable = pm.getActivityIcon(comp)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                if (iv.tag == tag) {
                                    com.bumptech.glide.Glide.with(iv).load(drawable).into(iv)
                                }
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
            } else {
                iv.tag = null
                com.bumptech.glide.Glide.with(iv).clear(iv)
                iv.setImageDrawable(null)
                iv.background = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.bg_circle_green)
                iv.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
                iv.clipToOutline = true
                iv.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                com.bumptech.glide.Glide.with(iv).load(items[position].image).into(iv)
            }
        }
    }
}
