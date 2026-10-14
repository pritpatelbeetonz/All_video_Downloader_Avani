package app.messages.ui.activity

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import app.messages.R
import app.messages.data.db.AppDatabase
import app.messages.ui.adapters.BlockedMessageAdapter
import app.messages.ui.base.BaseActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BlockedMessagesActivity : BaseActivity() {

    private lateinit var db: app.messages.data.db.LauncherDatabase
    private lateinit var adapter: BlockedMessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_messages)

        applySystemBarInsets(R.color.bg_surface, !isNightModeActive())

        db = app.messages.data.db.LauncherDatabase.getLauncherDatabase(this)

        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        if (tvTitle != null) {
            tvTitle.text = "Blocked messages"
        }
        
        val ivBack = findViewById<View>(R.id.ivBack)
        ivBack?.setOnClickListener {
            finish()
        }

        val rvBlockedMessages = findViewById<RecyclerView>(R.id.rvBlockedMessages)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)

        adapter = BlockedMessageAdapter { msg ->
            val options = arrayOf("Restore to inbox", "Delete message")
            android.app.AlertDialog.Builder(this)
                .setTitle(msg.address)
                .setItems(options) { dialog, which ->
                    when (which) {
                        0 -> { // Restore
                            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val values = android.content.ContentValues().apply {
                                    put(android.provider.Telephony.Sms.ADDRESS, msg.address)
                                    put(android.provider.Telephony.Sms.BODY, msg.body)
                                    put(android.provider.Telephony.Sms.DATE, msg.date)
                                    put(android.provider.Telephony.Sms.READ, 1)
                                    put(android.provider.Telephony.Sms.TYPE, android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX)
                                }
                                try {
                                    contentResolver.insert(android.provider.Telephony.Sms.CONTENT_URI, values)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                db.blockedMessageDao().deleteById(msg.id)
                                app.messages.data.SyncManager(this@BlockedMessagesActivity).syncAll()
                            }
                        }
                        1 -> { // Delete
                            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                db.blockedMessageDao().deleteById(msg.id)
                            }
                        }
                    }
                }
                .show()
        }
        rvBlockedMessages.adapter = adapter

        lifecycleScope.launch {
            db.blockedMessageDao().getAllBlockedMessagesFlow().collectLatest { list ->
                adapter.submitList(list)
                if (list.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    rvBlockedMessages.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    rvBlockedMessages.visibility = View.VISIBLE
                }
            }
        }
    }
}
