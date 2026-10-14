package app.messages.ui.activity

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import android.text.TextWatcher
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import app.messages.Controller.Companion.adsModel
import app.messages.R
import app.messages.analytics.AnalyticsHelper.logAds
import app.messages.constant.Constants
import app.messages.data.SyncManager
import app.messages.databinding.ActivityAddPeopleBinding
import app.messages.ui.adapters.AddPeopleAdapter
import app.messages.ui.base.BaseActivity
import kotlinx.coroutines.launch

class AddPeopleActivity : BaseActivity() {

    private lateinit var binding: ActivityAddPeopleBinding
    private lateinit var adapter: AddPeopleAdapter
    private lateinit var syncManager: SyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddPeopleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadBannerAd()

        syncManager = SyncManager(this)
        setupUI()
        loadContacts()
    }

    private fun loadBannerAd() {
        val config = app.messages.config.AdsConfigManager.config
        val frameLayout = binding.bannerViewAdsFrameLayout.bannerAdFrameLayout
        val shimmerFrameLayout = binding.bannerViewAdsFrameLayout.bannerAdShimmerFrameLayout

        if (app.messages.preferences.PreferenceHelper.getInstance(this).isPremium() || !config.isBannerOn) {
            frameLayout.visibility = View.GONE
            shimmerFrameLayout.visibility = View.GONE
            return
        }

        val adView = app.messages.config.AdsConfigManager.getBannerAd(this, config.bannerAdIdContactHome) // assuming contact home ID for this
        if (adView != null) {
            if (adView.parent != null) {
                (adView.parent as ViewGroup).removeView(adView)
            }
            
            frameLayout.addView(adView)
            
            // If the ad was preloaded, it already has responseInfo
            if (adView.responseInfo != null) {
                frameLayout.visibility = View.VISIBLE
                shimmerFrameLayout.visibility = View.GONE
                adView.visibility = View.VISIBLE
            }
            
            adView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    shimmerFrameLayout.visibility = View.GONE
                    frameLayout.visibility = View.VISIBLE
                    adView.visibility = View.VISIBLE
                }
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    // If it was already showing a preloaded ad, don't hide it on reload failure
                    if (adView.responseInfo == null) {
                        frameLayout.visibility = View.GONE
                    }
                }
            }
            
            adView.loadAd(AdRequest.Builder().build())
        } else {
            frameLayout.visibility = View.GONE
            shimmerFrameLayout.visibility = View.GONE
        }
    }

    private val existingMembers = mutableListOf<String>()

    private fun setupUI() {

        binding.incToolbar.tvTitle.text = getString(R.string.add_people)
        binding.incToolbar.ivBack.setOnClickListener { finish() }

        adapter = AddPeopleAdapter { count ->
            if (count > 0) {
                binding.btnAddMembers.visibility = View.VISIBLE
                binding.btnAddMembers.text = getString(R.string.add_members_format, count)
            } else {
                binding.btnAddMembers.visibility = View.GONE
            }
        }
        binding.rvContacts.adapter = adapter
        binding.ivClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }
        binding.ivClearSearch.visibility = if (binding.etSearch.text?.isNotEmpty() == true) {
            View.VISIBLE
        } else {
            View.GONE
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                binding.ivClearSearch.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
                adapter.filter(query)
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.btnAddMembers.setOnClickListener {
            showNewGroupDialog()
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            val contacts = syncManager.getContacts()
            adapter.submitList(contacts)

            val intentMembers = intent.getStringExtra("existing_members")
            if (!intentMembers.isNullOrEmpty()) {
                val members = intentMembers.split(",").map {
                    it.trim().filter { char -> char.isDigit() } // Normalize
                }
                existingMembers.clear()
                existingMembers.addAll(members)

                // We need to wait for list submission or handle it in adapter
                adapter.preSelectContacts(members)
            }
        }
    }

    private fun showNewGroupDialog() {
        app.messages.ui.dialogs.GeneralDialogs.showGroupNameDialog(
            context = this,
            title = getString(R.string.new_group),
            buttonText = getString(R.string.create_group)
        ) { groupName ->
            val selectedNumbers = adapter.getSelectedPhoneNumbers()

            // Merge existing members with selected ones to ensure no one is dropped
            val finalRecipients = mutableSetOf<String>()
            finalRecipients.addAll(selectedNumbers)
            finalRecipients.addAll(existingMembers) // Add normalized existing members

            if (finalRecipients.isEmpty()) {
                return@showGroupNameDialog
            }

            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val recipients = finalRecipients.toSet()

                    // Get system thread ID for these recipients
                    val threadId = try {
                        android.provider.Telephony.Threads.getOrCreateThreadId(
                            this@AddPeopleActivity,
                            recipients
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Fallback: generate a local negative ID if system fails (though reliable MMS requires system ID)
                        -System.currentTimeMillis()
                    }

                    val address = recipients.joinToString(",")
                    val dao = app.messages.data.db.AppDatabase.getDatabase(this@AddPeopleActivity)
                        .conversationDao()
                    val existing = dao.getById(threadId)

                    val finalName = if (groupName.isEmpty()) null else groupName

                    val now = System.currentTimeMillis()
                    if (existing != null) {
                        // Only update name if provided, otherwise keep existing
                        val updatedName = finalName ?: existing.recipientName
                        dao.insert(existing.copy(recipientName = updatedName, normalizedAddress = app.messages.utility.AddressUtils.normalizeAddress(address)))
                    } else {
                        val conversation = app.messages.data.db.ConversationEntity(
                            threadId = threadId,
                            address = address,
                            body = "",
                            date = now,
                            read = true,
                            messageCount = 0,
                            recipientName = finalName,
                            photoUri = null,
                            lastMsgType = 1,
                            isSaved = true,
                            normalizedAddress = app.messages.utility.AddressUtils.normalizeAddress(address)
                        )
                        dao.insert(conversation)
                    }

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val resultIntent = android.content.Intent().apply {
                            putExtra("thread_id", threadId)
                            putExtra("address", address)
                            putExtra("name", finalName)
                        }

                        setResult(RESULT_OK, resultIntent)

                        if (callingActivity == null) {
                            val chatIntent = android.content.Intent(this@AddPeopleActivity, ChatActivity::class.java).apply {
                                putExtra("thread_id", threadId)
                                putExtra("address", address)
                                putExtra("name", finalName)
                                putExtra("is_saved", true)
                            }
                            startActivity(chatIntent)
                        }
                        finish()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        }
                }
            }
        }
    }


}
