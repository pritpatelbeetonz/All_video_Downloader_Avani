package app.messages.ui.activity

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import app.messages.R
import app.messages.data.db.AppDatabase
import app.messages.ui.adapters.BlockedNumberAdapter
import app.messages.ui.base.BaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BlockedNumbersActivity : BaseActivity() {

    private lateinit var db: app.messages.data.db.LauncherDatabase
    private lateinit var adapter: BlockedNumberAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_numbers)

        applySystemBarInsets(R.color.bg_surface, !isNightModeActive())

        db = app.messages.data.db.LauncherDatabase.getLauncherDatabase(this)

        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        if (tvTitle != null) {
            tvTitle.text = "Blocked numbers"
        }
        
        val ivBack = findViewById<View>(R.id.ivBack)
        ivBack?.setOnClickListener {
            finish()
        }

        val rvBlockedNumbers = findViewById<RecyclerView>(R.id.rvBlockedNumbers)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)

        adapter = BlockedNumberAdapter { number ->
            lifecycleScope.launch(Dispatchers.IO) {
                app.messages.utility.ConversationActionHelper.unblockConversations(
                    this@BlockedNumbersActivity,
                    listOf(number),
                    showToast = true
                )
            }
        }
        rvBlockedNumbers.adapter = adapter

        lifecycleScope.launch {
            db.blockedContactDao().getBlockedAddressesFlow().collectLatest { list ->
                adapter.submitList(list)
                if (list.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    rvBlockedNumbers.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    rvBlockedNumbers.visibility = View.VISIBLE
                }
            }
        }
    }
}
