package app.messages.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import app.messages.R

import app.messages.data.db.AppDatabase
import app.messages.data.db.ConversationEntity
import app.messages.databinding.ActivityArchiveBinding
import app.messages.ui.adapters.ConversationAdapter
import app.messages.ui.base.BaseActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import app.messages.ui.dialogs.GeneralDialogs

class ArchiveActivity : BaseActivity() {
    private lateinit var binding: ActivityArchiveBinding
    private lateinit var adapter: ConversationAdapter
    private lateinit var db: app.messages.data.db.LauncherDatabase
    private lateinit var syncManager: app.messages.data.SyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArchiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySystemBarInsets(R.color.bg_surface, !isNightModeActive())

        db = app.messages.data.db.LauncherDatabase.getLauncherDatabase(this)
        syncManager = app.messages.data.SyncManager(this)

        binding.incToolbar.tvTitle.text = getString(R.string.archive)
        binding.incToolbar.ivBack.setOnClickListener {
            finish()
        }
        
        setupRecyclerView()
        observeArchivedChats()
    }

    private var isSelectionMode = false

    private fun setupRecyclerView() {
        adapter = ConversationAdapter(
            onClick = { conversation ->
                if (isSelectionMode) {
                    adapter.toggleSelection(conversation.threadId)
                } else {
                    val intent = Intent(this, ChatActivity::class.java)
                    intent.putExtra("thread_id", conversation.threadId)
                    intent.putExtra("address", conversation.address)
                    intent.putExtra("name", conversation.recipientName)
                    intent.putExtra("photo_uri", conversation.photoUri)
                    intent.putExtra("is_saved", conversation.isSaved)
                    startActivity(intent)

                }
            },
            onRightSwipeActionClick = { conversation ->
                lifecycleScope.launch {
                    app.messages.utility.ConversationActionHelper.unarchiveConversations(
                        this@ArchiveActivity,
                        listOf(conversation.threadId),
                        showToast = true
                    )
                }
            },
            isSwipeEnabled = false
        )
        adapter.onLongClick = { conversation ->
            enterSelectionMode(conversation)
        }
        adapter.onSelectionChanged = { count ->
            if (count == 0) {
                exitSelectionMode()
            } else {
                binding.incToolbarSelection.tvSelectionCount.text = "$count Selected"
            }
        }
        binding.rvArchive.adapter = adapter
        setupSelectionListeners()
    }

    private fun setupSelectionListeners() {
        binding.incToolbarSelection.ivCloseSelection.setOnClickListener {
            exitSelectionMode()
        }
        binding.incToolbarSelection.ivSelectionArchive.setImageResource(R.drawable.ic_unarchive)
        // ivSelectionArchive represents Unarchive here
        binding.incToolbarSelection.ivSelectionArchive.setOnClickListener {
            unarchiveSelected()
        }
        binding.incToolbarSelection.ivSelectionDelete.setOnClickListener {
            deleteSelected()
        }
        binding.incToolbarSelection.ivSelectionMore.visibility = View.GONE
        binding.incToolbarSelection.ivSelectionDelete.visibility = View.GONE
    }

    private fun enterSelectionMode(conversation: ConversationEntity) {
        isSelectionMode = true
        adapter.setSelectionMode(true)
        adapter.toggleSelection(conversation.threadId)
        binding.incToolbarSelection.root.visibility = View.VISIBLE
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        adapter.setSelectionMode(false)
        binding.incToolbarSelection.root.visibility = View.GONE
    }

    private fun unarchiveSelected() {
        val selectedIds = adapter.getSelectedIds()
        if (selectedIds.isEmpty()) return

        GeneralDialogs.showConfirmationDialog(
            this,
            getString(R.string.unarchive),
            getString(R.string.are_you_sure_you_want_to_unarchive),
            getString(R.string.unarchive)
        ) {
            lifecycleScope.launch {
                app.messages.utility.ConversationActionHelper.unarchiveConversations(
                    this@ArchiveActivity,
                    selectedIds,
                    showToast = true,
                    onSuccess = { exitSelectionMode() }
                )
            }
        }
    }

    private fun deleteSelected() {
        val selectedIds = adapter.getSelectedIds()
        if (selectedIds.isEmpty()) return

        GeneralDialogs.showStyledDeleteDialog(
            this,
            "Are you sure you want to delete?"
        ) {
            lifecycleScope.launch {
                app.messages.utility.ConversationActionHelper.deleteConversations(
                    this@ArchiveActivity,
                    selectedIds,
                    showToast = true,
                    onSuccess = { exitSelectionMode() }
                )
            }
        }
    }

    private fun observeArchivedChats() {
        lifecycleScope.launch {
            db.conversationDao().getArchivedFlow().collectLatest { list ->
                if (list.isEmpty()) {
                    binding.rvArchive.visibility = View.GONE
                    binding.emptyView.visibility = View.VISIBLE
                    if (isSelectionMode) exitSelectionMode()
                } else {
                    binding.rvArchive.visibility = View.VISIBLE
                    binding.emptyView.visibility = View.GONE
                    adapter.submitList(list)
                }
            }
        }
    }

    override fun onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode()
        } else {
            super.onBackPressed()
        }
    }


}
