package app.messages.ui.activity

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import app.messages.R
import app.messages.data.db.AppDatabase
import app.messages.data.db.ConversationEntity
import app.messages.databinding.ActivityBlockListBinding
import app.messages.ui.adapters.ConversationAdapter
import app.messages.ui.base.BaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import app.messages.ui.dialogs.GeneralDialogs

class BlockListActivity : BaseActivity() {
    private lateinit var binding: ActivityBlockListBinding
    private lateinit var db: app.messages.data.db.LauncherDatabase
    private lateinit var syncManager: app.messages.data.SyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        applySystemBarInsets(R.color.bg_surface, !isNightModeActive())

        db = app.messages.data.db.LauncherDatabase.getLauncherDatabase(this)
        syncManager = app.messages.data.SyncManager(this)

        // apply theme colors to root / toolbar
        binding.incToolbar.tvTitle.text = "Blocking"
        binding.incToolbar.ivBack.setOnClickListener {
            finish()
        }
        
        setupMenuListeners()
        observeBlockedCount()
    }

    private fun setupMenuListeners() {
        val prefHelper = app.messages.preferences.PreferenceHelper.getInstance(this)
        binding.switchDropMessages.isChecked = prefHelper.isDropMessagesEnabled()

        binding.cvBlockedNumbers.setOnClickListener {
            val intent = android.content.Intent(this, BlockedNumbersActivity::class.java)
            startActivity(intent)
        }
        binding.cvDropMessages.setOnClickListener {
            binding.switchDropMessages.isChecked = !binding.switchDropMessages.isChecked
        }
        binding.switchDropMessages.setOnCheckedChangeListener { _, isChecked ->
            prefHelper.setDropMessagesEnabled(isChecked)
        }
        binding.cvBlockedMessages.setOnClickListener {
            val intent = android.content.Intent(this, BlockedMessagesActivity::class.java)
            startActivity(intent)
        }
    }

    private fun observeBlockedCount() {
        lifecycleScope.launch {
            db.blockedContactDao().getBlockedAddressesFlow().collectLatest { list ->
                val count = list.size
                binding.tvBlockedNumbersCount.text = "$count phone numbers blocked"
            }
        }
    }
}
