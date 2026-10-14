package app.messages.ui.activity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import app.messages.R
import app.messages.data.SyncManager
import app.messages.data.db.AppDatabase
import app.messages.data.db.Contact
import app.messages.databinding.ActivityGroupInfoBinding
import app.messages.ui.adapters.MemberAdapter
import app.messages.ui.base.BaseActivity
import app.messages.ui.dialogs.GeneralDialogs
import app.messages.utility.SimManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.view.View


class GroupInfoActivity : BaseActivity() {

    private lateinit var binding: ActivityGroupInfoBinding
    private lateinit var adapter: MemberAdapter
    private lateinit var syncManager: SyncManager
    private lateinit var simManager: SimManager
    private lateinit var db: app.messages.data.db.LauncherDatabase
    private var threadId: Long = -1
    private var name: String? = null

    private var address: String? = null
    private var photoUri: String? = null
    private var isBlocked: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        syncManager = SyncManager(this)
        simManager = SimManager(this)
        adapter = MemberAdapter()
        db = app.messages.data.db.LauncherDatabase.getLauncherDatabase(this)

        threadId = intent.getLongExtra("thread_id", -1)
        name = intent.getStringExtra("name")
        address = intent.getStringExtra("address")
        photoUri = intent.getStringExtra("photo_uri")

        setupUI()
        setupClicks()
        loadMembers()
    }



    private fun setupUI() {
        val displayName = name ?: address ?: "Contact"
        binding.tvName.text = displayName
        binding.tvPhone.text = address ?: ""

        if (!photoUri.isNullOrEmpty()) {
            binding.ivProfile.visibility = View.VISIBLE
            binding.tvInitials.visibility = View.GONE
            Glide.with(this).load(photoUri).into(binding.ivProfile)
        } else {
            binding.ivProfile.visibility = View.GONE
            binding.tvInitials.visibility = View.VISIBLE
            val initials = displayName.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")
            binding.tvInitials.text = if (initials.isNotEmpty()) initials else "#"
            applyAvatarColors(binding.tvInitials, displayName, binding.cvAvatar)
        }

        binding.tvToolbarTitle.text = "Details"
        binding.btnBack.setOnClickListener { finish() }

        // Check blocked status
        lifecycleScope.launch {
            val blockedAddresses = db.blockedContactDao().getAllBlockedAddresses()
            val addr = address ?: ""
            val members = addr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            isBlocked = members.any { it in blockedAddresses } || (addr.isNotEmpty() && addr.trim() in blockedAddresses)
            
            // Note: Since the UI design only has "Block Contact", we'll just keep the text static
            // If it's blocked, we might want to change it to "Unblock Contact", but the mock doesn't show it.
            // Let's implement changing the text just in case.
            val tvBlock = binding.llBlock.getChildAt(1) as? android.widget.TextView
            if (isBlocked) {
                tvBlock?.text = "Unblock Contact"
            } else {
                tvBlock?.text = "Block Contact"
            }
        }
    }

    private fun loadMembers() {
        // Members list is removed from the new UI design
    }

    private fun setupClicks() {

        binding.llNotifications.setOnClickListener {
            val intent = Intent(this, app.messages.ui.activity.NotificationSettingsActivity::class.java)
            startActivity(intent)
        }

        binding.llArchive.setOnClickListener {
            lifecycleScope.launch {
                if (threadId != -1L) {
                    app.messages.utility.ConversationActionHelper.archiveConversations(
                        this@GroupInfoActivity,
                        listOf(threadId),
                        showToast = true,
                        onSuccess = { navigateToHome() }
                    )
                }
            }
        }

        binding.llBlock.setOnClickListener {
            lifecycleScope.launch {
                if (threadId != -1L && !address.isNullOrEmpty()) {
                    if (isBlocked) {
                        app.messages.utility.ConversationActionHelper.unblockConversations(
                            this@GroupInfoActivity,
                            listOf(address!!),
                            showToast = true,
                            onSuccess = { setupUI() } // Refresh UI to show 'Block' again
                        )
                    } else {
                        app.messages.utility.ConversationActionHelper.blockConversations(
                            this@GroupInfoActivity,
                            listOf(threadId),
                            listOf(address!!),
                            showToast = true,
                            onSuccess = { navigateToHome() }
                        )
                    }
                }
            }
        }

        binding.llDelete.setOnClickListener {
            GeneralDialogs.showStyledDeleteDialog(
                this,
                getString(R.string.are_you_sure_you_want_to_delete_this_conversation)
            ) {
                lifecycleScope.launch {
                    if (threadId != -1L) {
                        app.messages.utility.ConversationActionHelper.deleteConversations(
                            this@GroupInfoActivity,
                            listOf(threadId),
                            showToast = true,
                            onSuccess = { navigateToHome() }
                        )
                    }
                }
            }
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, app.messages.ui.launcher.LauncherActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)

        finish()
    }

    private fun isDefaultHomeApp(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
            }
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolve = packageManager.resolveActivity(homeIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolve?.activityInfo?.packageName == packageName
    }

    private fun getInitials(name: String): String {
        if (name.isEmpty()) return ""
        val parts = name.trim().split(Regex("\\s+"))
        return if (parts.size >= 2) {
            "${parts[0].first()}${parts[parts.size - 1].first()}"
        } else {
            parts[0].first().toString()
        }.uppercase()
    }

    private fun applyAvatarColors(tv: android.widget.TextView, name: String, cardView: androidx.cardview.widget.CardView) {
        val colors = listOf(
            Pair("#E3F2FD", "#1976D2"), // Blue
            Pair("#FBE9E7", "#D84315"), // Deep Orange
            Pair("#E8F5E9", "#388E3C"), // Green
            Pair("#FCE4EC", "#C2185B"), // Pink
            Pair("#FFF3E0", "#F57C00"), // Orange
            Pair("#F3E5F5", "#7B1FA2")  // Purple
        )
        val initialChar = getInitials(name).firstOrNull() ?: 'A'
        val colorPair = colors[kotlin.math.abs(initialChar.hashCode()) % colors.size]
        
        cardView.setCardBackgroundColor(android.graphics.Color.parseColor(colorPair.first))
        tv.setTextColor(android.graphics.Color.parseColor(colorPair.second))
    }
}