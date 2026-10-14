package app.messages.ui.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import app.messages.R
import app.messages.databinding.ActivitySelectContactBinding
import app.messages.ui.base.BaseActivity
import app.messages.data.SyncManager
import app.messages.ui.adapters.ContactAdapter
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import app.messages.Controller.Companion.adsModel


import kotlinx.coroutines.launch
import app.messages.analytics.AnalyticsHelper
import app.messages.analytics.AnalyticsHelper.logAds
import app.messages.analytics.EventNames
import app.messages.analytics.EventScreenName
import app.messages.constant.Constants

class SelectContactActivity : BaseActivity() {
    private lateinit var binding: ActivitySelectContactBinding
    private lateinit var adapter: ContactAdapter
    private lateinit var syncManager: SyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectContactBinding.inflate(layoutInflater)
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

        val adView = app.messages.config.AdsConfigManager.getBannerAd(this, config.bannerAdIdContactHome)
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
                    frameLayout.visibility = View.VISIBLE
                    shimmerFrameLayout.visibility = View.GONE
                    adView.visibility = View.VISIBLE
                }
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    // If it was already showing a preloaded ad, don't hide it on reload failure
                    if (adView.responseInfo == null) {
                        frameLayout.visibility = View.GONE
                        shimmerFrameLayout.visibility = View.GONE
                    }
                }
            }
            
            adView.loadAd(AdRequest.Builder().build())
        } else {
            frameLayout.visibility = View.GONE
            shimmerFrameLayout.visibility = View.GONE
        }
    }

    private fun setupUI() {
        binding.incToolbar.tvTitle.text = getString(R.string.select_contact)

        binding.incToolbar.ivBack.setOnClickListener {
            AnalyticsHelper.logEvent(EventNames.CLICK_BACK, EventScreenName.SELECT_CONTACT)
            finish()
        }

        adapter = ContactAdapter { contact ->
            AnalyticsHelper.logEvent(EventNames.SELECT_CONTACT_CLICK_CONTACT, EventScreenName.SELECT_CONTACT)
            val isSchedulingFromIntent = this@SelectContactActivity.intent.getBooleanExtra("is_scheduling", false)
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("address", contact.phoneNumber)
            intent.putExtra("name", contact.name)
            intent.putExtra("photo_uri", contact.photoUri)
            intent.putExtra("is_saved", true)
            intent.putExtra("is_scheduling", isSchedulingFromIntent)
            startActivity(intent)

            finish()
        }

        binding.llNewGroup.setOnClickListener {
            val intent = Intent(this, AddPeopleActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.rvContacts.adapter = adapter

        // configure edit text to filter contacts as user types
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
                if (s?.isNotEmpty() == true) {
                    AnalyticsHelper.logEvent(EventNames.SELECT_CONTACT_CLICK_SEARCH, EventScreenName.SELECT_CONTACT)
                    binding.llNewGroup.visibility = View.GONE
                } else {
                    binding.llNewGroup.visibility = View.VISIBLE
                }
                binding.ivClearSearch.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
                adapter.filter(s?.toString() ?: "")
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            // Using SyncManager helper to get contacts from system
            val contacts = syncManager.getContacts()
            adapter.submitList(contacts)
        }
    }




}
