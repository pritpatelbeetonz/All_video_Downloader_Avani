package app.messages.ui.activity

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.messages.R
import app.messages.data.CacheManager
import app.messages.data.SyncManager
import app.messages.data.db.LauncherDatabase
import app.messages.data.db.LauncherScheduledMessageEntity
import app.messages.databinding.ActivityScheduleBinding
import app.messages.ui.adapters.ScheduleAdapter
import app.messages.ui.base.BaseActivity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

import app.messages.analytics.AnalyticsHelper
import app.messages.analytics.EventNames
import app.messages.analytics.EventScreenName
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ScheduleActivity : BaseActivity() {
    private lateinit var binding: ActivityScheduleBinding
    private lateinit var adapter: ScheduleAdapter
    private lateinit var db: LauncherDatabase
    private lateinit var syncManager: SyncManager
    private lateinit var cacheManager: CacheManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = LauncherDatabase.getLauncherDatabase(this)
        syncManager = SyncManager(this)
        cacheManager = CacheManager.getInstance(this)

        setupUI()
        observeScheduledMessages()
    }

    private fun setupUI() {
        binding.incToolbar.tvTitle.text = getString(R.string.scheduled)
        binding.incToolbar.tvDone.visibility = View.GONE
        binding.incToolbar.ivBack.setOnClickListener {
            AnalyticsHelper.logEvent(
                EventNames.CLICK_BACK,
                EventScreenName.SCHEDULE
            )
            finish()
        }

        binding.fabAdd.setOnClickListener {
            val intent = Intent(this, SelectContactActivity::class.java)
            intent.putExtra("is_scheduling", true)
            startActivity(intent)

        }

        binding.rvScheduled.layoutManager = LinearLayoutManager(this)
    }

    private fun observeScheduledMessages() {
        // 1. Initialize adapter with empty map first
        adapter = ScheduleAdapter(emptyMap()) { msg ->

            showScheduledOptionDialog(msg)
        }
        binding.rvScheduled.adapter = adapter

        // 2. Observe scheduled messages immediately with Lifecycle safety
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.scheduledMessageDao().getPendingFlow().collectLatest { list ->
                    if (list.isEmpty()) {
                        binding.rvScheduled.visibility = View.GONE
                        binding.emptyView.visibility = View.VISIBLE
                    } else {
                        binding.rvScheduled.visibility = View.VISIBLE
                        binding.emptyView.visibility = View.GONE
                        adapter.submitList(list)
                    }
                }
            }
        }

        // 3. Load/Update contacts in background and update adapter when ready
        lifecycleScope.launch {
            val cachedContacts = cacheManager.getCachedContactsMap()
            if (cachedContacts.isNotEmpty()) {
                adapter.updateContacts(cachedContacts)
            } else {
                // If cache is empty, we do a full sync once in background
                syncManager.syncAll()
                val updatedContacts = cacheManager.getCachedContactsMap()
                if (updatedContacts.isNotEmpty()) {
                    adapter.updateContacts(updatedContacts)
                }
            }
        }
    }

    private fun showScheduledOptionDialog(msg: LauncherScheduledMessageEntity) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_schedule_option, null)

        val tvSendNow = dialogView.findViewById<TextView>(R.id.tvSendNow)
        val tvCopyText = dialogView.findViewById<TextView>(R.id.tvCopyText)
        val tvDelete = dialogView.findViewById<TextView>(R.id.tvDelete)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // ✅ REMOVE default padding (THIS FIXES FULL WIDTH ISSUE)
        dialog.window?.decorView?.setPadding(0, 0, 0, 0)

// ✅ SET WIDTH
        val width = (resources.displayMetrics.widthPixels * 0.75).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        tvSendNow.setOnClickListener {
            val intent = Intent(this, app.messages.receiver.ScheduleReceiver::class.java).apply {
                putExtra("scheduled_id", msg.id)
            }
            sendBroadcast(intent)
            dialog.dismiss()
        }

        tvCopyText.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Scheduled Message", msg.body)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Message copied to clipboard", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        tvDelete.setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmationDialog(msg)
        }

        dialog.show()
    }

    private fun showDeleteConfirmationDialog(msg: LauncherScheduledMessageEntity) {
        app.messages.ui.dialogs.GeneralDialogs.showStyledDeleteDialog(
            this,
            getString(R.string.are_you_sure_you_want_to_delete)
        ) {
            deleteScheduledMessage(msg)
        }
    }

    private fun deleteScheduledMessage(msg: LauncherScheduledMessageEntity) {
        lifecycleScope.launch {
            // 1. Delete from local Room DB
            db.scheduledMessageDao().deleteById(msg.id)
            msg.localMessageId?.let { localId ->
                db.messageDao().deleteByIds(listOf(localId))
            }

            // 2. Cancel the AlarmManager alarm
            val intent = android.content.Intent(this@ScheduleActivity, app.messages.receiver.ScheduleReceiver::class.java)
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                this@ScheduleActivity,
                msg.id.toInt(),
                intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.cancel(pendingIntent)

            android.widget.Toast.makeText(this@ScheduleActivity, getString(R.string.messages_deleted), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
