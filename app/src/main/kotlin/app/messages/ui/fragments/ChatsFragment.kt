package app.messages.ui.fragments

import android.app.role.RoleManager
import android.os.Build
import android.provider.Telephony
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import app.messages.R
import app.messages.data.CacheManager
import app.messages.data.SyncManager
import app.messages.data.db.AppDatabase
import app.messages.data.db.ConversationEntity
import app.messages.databinding.FragmentChatsBinding
import app.messages.ui.activity.ChatActivity
import app.messages.ui.activity.SelectContactActivity
import app.messages.ui.adapters.ConversationAdapter
import app.messages.ui.adapters.CategoryAdapter
import android.view.MotionEvent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri
import android.widget.Toast
import androidx.recyclerview.widget.SimpleItemAnimator

import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import app.messages.analytics.AnalyticsHelper
import app.messages.analytics.EventNames
import app.messages.analytics.EventScreenName
import app.messages.enumeration.SwipeActions
import app.messages.preferences.PreferenceHelper
import app.messages.ui.dialogs.GeneralDialogs

class ChatsFragment : Fragment() {
    private lateinit var binding: FragmentChatsBinding
    private var adapter: ConversationAdapter? = null
    private lateinit var syncManager: SyncManager
    private lateinit var cacheManager: CacheManager
    private lateinit var db: app.messages.data.db.LauncherDatabase
    private var allConversations = listOf<ConversationEntity>()
    private var hasLoadedData = false // Track if we've loaded actual data
    private var isInitialLoad = true // Track if this is the initial load
    private var hideHeader = false
    private var lastRenderedListSignature: Int = 0
    private var currentCategory: String = "All"
    // Scroll listener that fires SMS permission request on the user's first swipe
    private var firstSwipePermissionListener: RecyclerView.OnScrollListener? = null

    private val contentObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            if (hasSmsPermission()) {
                triggerSync()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            hideHeader = it.getBoolean(ARG_HIDE_HEADER, false)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_SMS] == true) {
            triggerSync()
        }
    }

    private val smsDefaultRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Always re-verify — never assume success
        updateDefaultSmsBannerVisibility()
        if (isDefaultSmsApp()) {
            if (hasSmsPermission()) {
                triggerSync()
            } else {
                requestPermissions()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentChatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (hideHeader) {
            binding.appBarLayout.visibility = View.GONE
            binding.btnStartChat.visibility = View.GONE
        } else {
            binding.appBarLayout.visibility = View.VISIBLE
            binding.btnStartChat.visibility = View.VISIBLE
        }

        // Handle top inset to prevent toolbar from being cut off by status bar
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, 0)
            insets
        }


        db = app.messages.data.db.LauncherDatabase.getLauncherDatabase(requireContext())
        syncManager = SyncManager(requireContext())
        cacheManager = CacheManager.getInstance(requireContext())
        lifecycleScope.launch(Dispatchers.IO) {
            db.conversationDao().deleteBlankOrPlaceholderConversations()
        }

        setupRecyclerView()
        setupListeners()
        setupCategoryFilter()

        // Start observing conversations immediately
        observeConversations()

        // Show shimmer immediately on fresh install to indicate loading
        showShimmer()

        // Show "Set as Default SMS" banner if needed
        setupDefaultSmsBanner()

        // Attach first-swipe permission listener (only when permission not yet granted)
        setupFirstSwipePermissionRequest()
        
        // Listen to config changes dynamically to show/hide banner ad instantly
        viewLifecycleOwner.lifecycleScope.launch {
            app.messages.config.AdsConfigManager.configFlow.collectLatest { config ->
                val frameLayout = binding.bannerViewAdsFrameLayout.bannerAdFrameLayout
                val shimmerFrameLayout = binding.bannerViewAdsFrameLayout.bannerAdShimmerFrameLayout
                
                if (PreferenceHelper.getInstance(requireContext()).isPremium() || !config.isBannerOn) {
                    frameLayout.visibility = View.GONE
                    shimmerFrameLayout.visibility = View.GONE
                    frameLayout.removeAllViews()
                } else if (frameLayout.childCount == 0 || frameLayout.visibility == View.GONE) {
                    loadBannerAd()
                }
            }
        }
    }

    private var hasPromptedForDefaultSms = false

    override fun onResume() {
        super.onResume()
        applyTheme()
        setupPremiumUI()

        // Re-check default SMS status each time we come to foreground
        // (user may have just returned from system settings)
        updateDefaultSmsBannerVisibility()
        loadBannerAd()

        // If SMS permission is already granted, sync immediately — no dialog needed
        if (hasSmsPermission()) {
            try {
                requireContext().contentResolver.registerContentObserver(
                    Telephony.Sms.CONTENT_URI,
                    true,
                    contentObserver
                )
                requireContext().contentResolver.registerContentObserver(
                    Telephony.Mms.CONTENT_URI,
                    true,
                    contentObserver
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            triggerSync()
        }

        // Always re-apply the current category filter when returning from any screen.
        // This ensures Transaction/OTP/Offers tabs stay filtered even if no new SMS arrived
        // (in which case the DB Flow would emit the same list and skip updateList() via the
        // signature check, leaving the adapter stale).
        if (allConversations.isNotEmpty()) {
            filter(binding.etSearch.text?.toString() ?: "")
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            requireContext().contentResolver.unregisterContentObserver(contentObserver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupRecyclerView() {
        adapter = ConversationAdapter(
            onClick = { conversation ->
                if (adapter?.isInSelectionMode() == true) {
                    adapter?.toggleSelection(conversation.threadId)
                } else {
                    val intent = Intent(requireContext(), ChatActivity::class.java)
                    intent.putExtra("thread_id", conversation.threadId)
                    intent.putExtra("address", conversation.address)
                    intent.putExtra("name", conversation.recipientName)
                    intent.putExtra("photo_uri", conversation.photoUri)
                    intent.putExtra("is_saved", conversation.isSaved)
                    startActivity(intent)

                }
            },
            onRightSwipeActionClick = { conversation ->
                val rightSwipeAction =
                    PreferenceHelper.getInstance(requireContext()).getRightSwipeAction()

                when (rightSwipeAction) {
                    SwipeActions.NONE.value -> {}
                    SwipeActions.ARCHIVE.value -> archiveAction(conversation)
                    SwipeActions.DELETE.value -> deleteAction(conversation)
                    SwipeActions.BLOCK.value -> blockAction(conversation)
                    SwipeActions.CALL.value -> callAction(conversation)
                    SwipeActions.MARK_READ.value -> markReadAction(conversation)
                    SwipeActions.MARK_UNREAD.value -> markUnreadAction(conversation)
                }
            },
            onLeftSwipeActionClick = { conversation ->
                val leftSwipeAction =
                    PreferenceHelper.getInstance(requireContext()).getLeftSwipeAction()
                when (leftSwipeAction) {
                    SwipeActions.NONE.value -> {}
                    SwipeActions.ARCHIVE.value -> archiveAction(conversation)
                    SwipeActions.DELETE.value -> deleteAction(conversation)
                    SwipeActions.BLOCK.value -> blockAction(conversation)
                    SwipeActions.CALL.value -> callAction(conversation)
                    SwipeActions.MARK_READ.value -> markReadAction(conversation)
                    SwipeActions.MARK_UNREAD.value -> markUnreadAction(conversation)
                }
            }
        )
        adapter?.onLongClick = { conversation ->
            enterSelectionMode(conversation)
        }
        adapter?.onSelectionChanged = { count ->
            if (count == 0) {
                exitSelectionMode()
            } else {
                binding.incToolbarSelection.tvSelectionCount.text = "$count Selected"
            }
        }
        binding.rvConversations.adapter = adapter
        binding.rvConversations.setHasFixedSize(true)
        binding.rvConversations.itemAnimator = null
            
        // Scroll to top button logic
        binding.rvConversations.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                if (layoutManager != null && layoutManager.findFirstVisibleItemPosition() > 3) {
                    binding.btnScrollToTop.show()
                } else {
                    binding.btnScrollToTop.hide()
                }
            }
        })
        binding.btnScrollToTop.setOnClickListener {
            binding.rvConversations.smoothScrollToPosition(0)
        }
    }

    private fun enterSelectionMode(conversation: ConversationEntity) {
        adapter?.setSelectionMode(true)
        adapter?.toggleSelection(conversation.threadId)
        binding.clToolbar.visibility = View.GONE
        binding.rvCategories.visibility = View.GONE
        binding.divider1.visibility = View.GONE
        binding.divider2.visibility = View.GONE
        binding.incToolbarSelection.root.visibility = View.VISIBLE
    }

    private fun exitSelectionMode() {
        adapter?.setSelectionMode(false)
        binding.clToolbar.visibility = View.VISIBLE
        binding.rvCategories.visibility = View.VISIBLE
        binding.divider1.visibility = View.VISIBLE
        binding.divider2.visibility = View.VISIBLE
        binding.incToolbarSelection.root.visibility = View.GONE
    }

    fun deleteAction(conversation: ConversationEntity) {
        GeneralDialogs.showStyledDeleteDialog(
            requireContext(),
            getString(R.string.are_you_sure_you_want_to_delete_this_conversation)
        ) {
            lifecycleScope.launch {
                app.messages.utility.ConversationActionHelper.deleteConversations(
                    requireContext(),
                    listOf(conversation.threadId),
                    showToast = true
                )
            }
        }
    }

    fun archiveAction(conversation: ConversationEntity) {
        lifecycleScope.launch {
            app.messages.utility.ConversationActionHelper.archiveConversations(
                requireContext(),
                listOf(conversation.threadId),
                showToast = true
            )
        }
    }

    fun blockAction(conversation: ConversationEntity) {
        lifecycleScope.launch {
            app.messages.utility.ConversationActionHelper.blockConversations(
                requireContext(),
                listOf(conversation.threadId),
                listOf(conversation.address),
                showToast = true
            )
        }
    }

    fun callAction(conversation: ConversationEntity) {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${conversation.address}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            }
    }

    fun markReadAction(conversation: ConversationEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            syncManager.markThreadAsRead(conversation.threadId)
        }
    }

    fun markUnreadAction(conversation: ConversationEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            syncManager.markThreadAsUnread(conversation.threadId)
        }
    }

    private fun archiveSelected() {
        val selectedIds = adapter?.getSelectedIds() ?: return
        lifecycleScope.launch {
            app.messages.utility.ConversationActionHelper.archiveConversations(
                requireContext(),
                selectedIds,
                showToast = true,
                onSuccess = { exitSelectionMode() }
            )
        }
    }

    private fun deleteSelected() {
        val selectedIds = adapter?.getSelectedIds() ?: return
        GeneralDialogs.showStyledDeleteDialog(
            requireContext(),
            getString(R.string.are_you_sure_you_want_to_delete_this_conversation)
        ) {
            lifecycleScope.launch {
                app.messages.utility.ConversationActionHelper.deleteConversations(
                    requireContext(),
                    selectedIds,
                    showToast = true,
                    onSuccess = { exitSelectionMode() }
                )
            }
        }
    }

    private fun markReadSelected() {
        val selectedIds = adapter?.getSelectedIds() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            selectedIds.forEach { id ->
                syncManager.markThreadAsRead(id)
            }
            lifecycleScope.launch(Dispatchers.Main) {
                exitSelectionMode()
            }
        }
    }

    private fun blockSelected() {
        val selectedIds = adapter?.getSelectedIds() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val addressesToBlock = mutableListOf<String>()
            selectedIds.forEach { id ->
                db.conversationDao().getById(id)?.address?.let {
                    addressesToBlock.add(it)
                }
            }
            if (addressesToBlock.isNotEmpty()) {
                app.messages.utility.ConversationActionHelper.blockConversations(
                    requireContext(),
                    selectedIds,
                    addressesToBlock,
                    showToast = true,
                    onSuccess = { exitSelectionMode() }
                )
            } else {
                kotlinx.coroutines.withContext(Dispatchers.Main) { exitSelectionMode() }
            }
        }
    }

    private fun pinSelected() {
        val selectedIds = adapter?.getSelectedIds() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            selectedIds.forEach { id ->
                db.conversationDao().updatePinStatus(id, true)
            }
            lifecycleScope.launch(Dispatchers.Main) {
                exitSelectionMode()
            }
        }
    }

    private fun setupListeners() {
        binding.ivSearch.setOnClickListener {
            binding.tvTitle.visibility = View.GONE
            binding.ivSearch.visibility = View.GONE
            binding.ivMoreMenu.visibility = View.GONE
            
            binding.ivBackSearch.visibility = View.VISIBLE
            binding.clSearchContainer.visibility = View.VISIBLE
            binding.tvRecentConversations.visibility = View.VISIBLE
            binding.rvCategories.visibility = View.GONE
            binding.divider2.visibility = View.GONE
            
            binding.etSearch.requestFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        
        binding.ivBackSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            
            binding.ivBackSearch.visibility = View.GONE
            binding.clSearchContainer.visibility = View.GONE
            binding.tvRecentConversations.visibility = View.GONE
            
            binding.tvTitle.visibility = View.VISIBLE
            binding.ivSearch.visibility = View.VISIBLE
            binding.ivMoreMenu.visibility = View.VISIBLE
            binding.rvCategories.visibility = View.VISIBLE
            binding.divider2.visibility = View.VISIBLE
            
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        }

        binding.ivClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }


        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Use the container layout or the card view
        binding.btnStartChat.setOnClickListener {
            val intent = Intent(requireContext(), SelectContactActivity::class.java)
            startActivity(intent)
            AnalyticsHelper.logEvent(
                EventNames.HOME_CLICK_ADD_NEW_MESSAGE,
                EventScreenName.HOME
            )
        }

        binding.ivMoreMenu.setOnClickListener {
            startActivity(Intent(requireContext(), app.messages.ui.activity.MenuActivity::class.java))
        }

        binding.incToolbarSelection.ivCloseSelection.setOnClickListener {
            exitSelectionMode()
        }

        binding.incToolbarSelection.ivSelectionArchive.setOnClickListener {
            archiveSelected()
        }

        binding.incToolbarSelection.ivSelectionDelete.setOnClickListener {
            deleteSelected()
        }

        binding.incToolbarSelection.ivSelectionMore.setOnClickListener {
            showSelectionMoreMenu(it)
        }
    }

    private fun setupPremiumUI() {
    }

    /**
     * Attaches a one-shot RecyclerView scroll listener that requests SMS permissions
     * the first time the user performs a swipe/scroll on the conversation list.
     * The listener removes itself after the first trigger so it never fires again.
     */
    private fun setupFirstSwipePermissionRequest() {
        val prefs = PreferenceHelper.getInstance(requireContext())

        // Skip if permission already granted or we've already asked once
        if (hasSmsPermission() || prefs.isSmsPermissionRequested()) {
            return
        }

        firstSwipePermissionListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    // Remove ourselves immediately — fire only once
                    binding.rvConversations.removeOnScrollListener(this)
                    firstSwipePermissionListener = null

                    if (!hasSmsPermission() && !prefs.isSmsPermissionRequested()) {
                        prefs.setSmsPermissionRequested(true)
                        requestPermissions()
                    }
                }
            }
        }
        binding.rvConversations.addOnScrollListener(firstSwipePermissionListener!!)
    }

    override fun onDestroyView() {
        // Clean up the scroll listener to avoid leaks
        firstSwipePermissionListener?.let {
            binding.rvConversations.removeOnScrollListener(it)
        }
        firstSwipePermissionListener = null
        super.onDestroyView()
    }

    private fun showChatsMorePopup(anchor: View) {
        val inflater = android.view.LayoutInflater.from(requireContext())
        val popupView = inflater.inflate(R.layout.popup_chats_more_menu, null)

        val popup = android.widget.PopupWindow(
            popupView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true // focusable — dismisses on outside touch
        )
        popup.elevation = 24f
        popup.animationStyle = android.R.style.Animation_Dialog

        popupView.findViewById<android.widget.TextView>(R.id.tvAddToContacts).setOnClickListener {
            popup.dismiss()
            val intent = Intent(requireContext(), app.messages.ui.activity.SelectContactActivity::class.java)
            startActivity(intent)
        }

        popupView.findViewById<android.widget.TextView>(R.id.tvPinToTop).setOnClickListener {
            popup.dismiss()
            }

        popupView.findViewById<android.widget.TextView>(R.id.tvMarkUnread).setOnClickListener {
            popup.dismiss()
            }

        popupView.findViewById<android.widget.TextView>(R.id.tvBlockMenu).setOnClickListener {
            popup.dismiss()
            val intent = Intent(requireContext(), app.messages.ui.activity.BlockListActivity::class.java)
            startActivity(intent)
        }

        popupView.findViewById<android.widget.TextView>(R.id.tvMenuOption).setOnClickListener {
            popup.dismiss()
            val intent = Intent(requireContext(), app.messages.ui.activity.MenuActivity::class.java)
            startActivity(intent)
        }

        // Measure popup to position it correctly below the anchor (top-right)
        popupView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = popupView.measuredWidth
        popup.showAsDropDown(anchor, -popupWidth + anchor.width, 0)
    }

    private fun showSelectionMoreMenu(view: View) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), view)
        popup.menu.add(1, 1, 1, "Pin to top")
        popup.menu.add(1, 2, 2, getString(R.string.mark_read))
        popup.menu.add(1, 3, 3, getString(R.string.block))

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> pinSelected()
                2 -> markReadSelected()
                3 -> blockSelected()
            }
            true
        }
        popup.show()
    }

    private fun showMenu(view: View) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), view)
        popup.menu.add(1, 1, 1, getString(R.string.archived))
        popup.menu.add(1, 2, 2, getString(R.string.backup_and_restore))
        popup.menu.add(1, 3, 3, getString(R.string.scheduled))
        popup.menu.add(1, 4, 4, getString(R.string.block))
        popup.menu.add(1, 5, 5, getString(R.string.settings))

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    val intent =
                        Intent(
                            requireContext(),
                            app.messages.ui.activity.ArchiveActivity::class.java
                        )

                    startActivity(intent)
                }

                2 -> {
                    val intent =
                        Intent(
                            requireContext(),
                            app.messages.ui.activity.BackupRestoreActivity::class.java
                        )
                    startActivity(intent)
                }

                3 -> {
                    val intent =
                        Intent(
                            requireContext(),
                            app.messages.ui.activity.ScheduleActivity::class.java
                        )
                    startActivity(intent)
                }

                4 -> {
                    val intent =
                        Intent(
                            requireContext(),
                            app.messages.ui.activity.BlockListActivity::class.java
                        )
                    startActivity(intent)
                }

                5 -> {
                    val intent =
                        Intent(
                            requireContext(),
                            app.messages.ui.activity.SettingsActivity::class.java
                        )
                    startActivity(intent)
                }
            }
            true
        }
        popup.show()
    }

    private fun observeConversations() {
        lifecycleScope.launch {
            db.conversationDao().getUnarchivedFlow().collectLatest { list ->
                val visibleList: List<ConversationEntity> = list.filter { conv ->
                    !conv.body.trim().equals("No content", ignoreCase = true)
                }
                allConversations = visibleList

                // On initial load, don't immediately mark as loaded if the list is empty
                if (isInitialLoad) {
                    if (visibleList.isNotEmpty()) {
                        // If we get non-empty data on initial load, mark as loaded
                        hasLoadedData = true
                        isInitialLoad = false
                    }
                    // If list is empty, keep hasLoadedData = false and isInitialLoad = true
                    // This allows sync to continue populating data
                } else {
                    // On subsequent updates, always update the flag
                    hasLoadedData = true
                }

                val filteredList: List<ConversationEntity> = visibleList.filter { conv ->
                    app.messages.utility.AddressUtils.isValidAddress(conv.address)
                }
                val newSignature = filteredList.fold(1) { acc, item ->
                    31 * acc + item.hashCode()
                }
                // Always update — skipping when signature matches caused the category filter
                // to be ignored when returning from another screen with no new messages.
                lastRenderedListSignature = newSignature
                updateList(filteredList)
                // Hide loading indicator once data is loaded
                hideLoadingIndicator()
            }
        }
    }

    private val isSyncingInFragment = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun triggerSync() {
        triggerFullSync()
    }

    private fun triggerFullSync() {
        if (!isSyncingInFragment.compareAndSet(false, true)) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                syncManager.quickSyncRecentConversations(20)
                syncManager.syncAll()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isInitialLoad = false
                hasLoadedData = true
                isSyncingInFragment.set(false)
                
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (allConversations.isEmpty()) {
                        hideShimmer()
                        showDefaultEmptyState()
                    }
                }
            }
        }
    }

    private fun showLoadingIndicator() {
        showShimmer()
    }

    private fun hideLoadingIndicator() {
        binding.progressBar.visibility = View.GONE
        binding.rvConversations.visibility = View.VISIBLE
    }

    private fun showShimmer() {
        binding.shimmerLayout.visibility = View.VISIBLE
        binding.shimmerLayout.startShimmer()
        binding.rvConversations.visibility = View.GONE
        binding.emptyView.visibility = View.GONE
    }

    private fun hideShimmer() {
        binding.shimmerLayout.stopShimmer()
        binding.shimmerLayout.visibility = View.GONE
        // Do NOT force rvConversations visible here — filter() decides visibility
    }

    private fun updateList(list: List<ConversationEntity>) {
        binding.shimmerLayout.visibility = View.GONE
        binding.shimmerLayout.stopShimmer()

        if (list.isEmpty() && isInitialLoad) {
            // Keep shimmer showing during initial load when no data is available yet
            binding.shimmerLayout.visibility = View.VISIBLE
            binding.shimmerLayout.startShimmer()
            binding.rvConversations.visibility = View.GONE
            binding.emptyView.visibility = View.GONE
            return
        }

        // Let filter() own ALL visibility decisions so they stay consistent
        val currentQuery = binding.etSearch.text.toString()
        filter(currentQuery)
    }

    private var filterJob: kotlinx.coroutines.Job? = null

    private fun filter(query: String) {
        filterJob?.cancel()
        val sourceList = allConversations
        val category = currentCategory

        filterJob = lifecycleScope.launch {
            val (finalFilteredList, isBlankQuery) = withContext(Dispatchers.Default) {
                val sortedAll = sourceList.sortedWith(
                    compareByDescending<ConversationEntity> { it.isPinned }
                        .thenByDescending { it.date }
                )
                val filteredByCategory = applyCategoryFilter(sortedAll, category)
                if (query.isBlank()) {
                    Pair(filteredByCategory, true)
                } else {
                    val searchFiltered = filteredByCategory.filter {
                        (it.recipientName?.contains(query, true) ?: false) ||
                                it.address.contains(query, true) ||
                                it.body.contains(query, true)
                    }
                    Pair(searchFiltered, false)
                }
            }

            if (isBlankQuery) {
                if (finalFilteredList.isEmpty()) {
                    if (hasLoadedData) {
                        showDefaultEmptyState()
                    }
                    adapter?.submitList(emptyList())
                } else {
                    binding.emptyView.visibility = View.GONE
                    binding.rvConversations.visibility = View.VISIBLE
                    val oldTopId = adapter?.currentList?.firstOrNull()?.threadId
                    adapter?.submitList(finalFilteredList) {
                        val newTopId = finalFilteredList.firstOrNull()?.threadId
                        if (newTopId != null && newTopId != oldTopId) {
                            binding.rvConversations.scrollToPosition(0)
                        }
                    }
                }
            } else {
                if (finalFilteredList.isEmpty()) {
                    binding.tvEmptyTitle.setText(R.string.no_conversations_found)
                    binding.tvEmptyDetails.setText(R.string.try_a_different_search)
                    binding.rvConversations.visibility = View.GONE
                    binding.emptyView.visibility = View.VISIBLE
                    adapter?.submitList(emptyList())
                } else {
                    binding.emptyView.visibility = View.GONE
                    binding.rvConversations.visibility = View.VISIBLE
                    adapter?.submitList(finalFilteredList)
                }
            }
        }
    }

    private fun setupCategoryFilter() {
        val categories = listOf("All", "Personal", "Transaction", "OTPs", "Offers")
        val categoryAdapter = CategoryAdapter(categories) { selectedCategory ->
            currentCategory = selectedCategory
            filter(binding.etSearch.text.toString())
        }
        binding.rvCategories.adapter = categoryAdapter

        binding.rvCategories.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        rv.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        rv.parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
    }

    private fun applyCategoryFilter(list: List<ConversationEntity>, category: String): List<ConversationEntity> {
        if (category == "All") return list

        return list.filter { conv ->
            val body = conv.body.lowercase()
            val sender = conv.address
            val isShortCode = sender.any { it.isLetter() } || (sender.length < 10 && !sender.startsWith("+"))

            when (category) {
                "Personal" -> {
                    conv.isSaved || !isShortCode
                }
                "Transaction" -> {
                    isShortCode && !body.contains("otp") && !body.contains("code") && !body.contains("verification")
                }
                "OTPs" -> {
                    body.contains("otp") || body.contains("code") || body.contains("verification")
                }
                "Offers" -> {
                    body.contains("offer") || body.contains("discount") || body.contains("sale") || body.contains("win") || body.contains("% off")
                }
                else -> true
            }
        }
    }

    private fun showDefaultEmptyState() {
        binding.tvEmptyTitle.setText(R.string.no_conversations_yet)
        binding.tvEmptyDetails.setText(R.string.start_a_new_chat_or_invite_others_to_join_the_conversation)
        binding.rvConversations.visibility = View.GONE
        binding.emptyView.visibility = View.VISIBLE
    }

    private fun applyTheme() {

    }


    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.SEND_SMS
            )
        )
    }

    private fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = requireContext().getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(requireContext()) == requireContext().packageName
        }
    }

    private fun setupDefaultSmsBanner() {
        binding.btnSetDefaultSms.setOnClickListener {
            requestDefaultSmsRole()
        }
    }

    private fun loadBannerAd() {
        val config = app.messages.config.AdsConfigManager.config
        val frameLayout = binding.bannerViewAdsFrameLayout.bannerAdFrameLayout
        val shimmerFrameLayout = binding.bannerViewAdsFrameLayout.bannerAdShimmerFrameLayout

        if (PreferenceHelper.getInstance(requireContext()).isPremium() || !config.isBannerOn) {
            frameLayout.visibility = View.GONE
            shimmerFrameLayout.visibility = View.GONE
            frameLayout.removeAllViews()
            return
        }
        
        if (frameLayout.childCount > 0) {
            frameLayout.visibility = View.VISIBLE
            return
        }

        val adView = app.messages.config.AdsConfigManager.getBannerAd(requireActivity(), config.bannerAdIdContactHome)
        if (adView != null) {
            if (adView.parent != null) {
                (adView.parent as ViewGroup).removeView(adView)
            }
            frameLayout.addView(adView)
            
            // If the ad was preloaded, it already has responseInfo
            if (adView.responseInfo != null) {
                frameLayout.visibility = View.VISIBLE
                shimmerFrameLayout.visibility = View.GONE
            }

            adView.adListener = object : com.google.android.gms.ads.AdListener() {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    shimmerFrameLayout.visibility = View.GONE
                    frameLayout.visibility = View.VISIBLE
                }
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    super.onAdFailedToLoad(error)
                    // If it was already showing a preloaded ad, don't hide it on reload failure
                    if (adView.responseInfo == null) {
                        shimmerFrameLayout.visibility = View.GONE
                        frameLayout.visibility = View.GONE
                    }
                }
            }
            adView.loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
        } else {
            shimmerFrameLayout.visibility = View.GONE
            frameLayout.visibility = View.GONE
        }
    }

    private fun updateDefaultSmsBannerVisibility() {
        if (isDefaultSmsApp()) {
            binding.llSetDefaultBanner.visibility = View.GONE
            binding.btnStartChat.visibility = View.VISIBLE
        } else {
            binding.llSetDefaultBanner.visibility = View.VISIBLE
            binding.btnStartChat.visibility = View.GONE
        }
    }

    private fun requestDefaultSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = requireContext().getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                smsDefaultRoleLauncher.launch(intent)
            }
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, requireContext().packageName)
            smsDefaultRoleLauncher.launch(intent)
        }
    }

    companion object {
        private const val ARG_HIDE_HEADER = "hide_header"

        fun newInstance(hideHeader: Boolean) = ChatsFragment().apply {
            arguments = Bundle().apply {
                putBoolean(ARG_HIDE_HEADER, hideHeader)
            }
        }
    }
}
