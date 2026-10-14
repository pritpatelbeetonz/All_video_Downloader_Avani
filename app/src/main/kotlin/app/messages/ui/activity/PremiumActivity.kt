package app.messages.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import app.messages.config.AdsConfigManager
import app.messages.R
import app.messages.constant.Constants.PRIVACY_POLICY
import app.messages.databinding.ActivityPremiumBinding
import app.messages.preferences.PreferenceHelper
import app.messages.ui.base.BaseActivity
import app.messages.utility.BillingHelper

class PremiumActivity : BaseActivity() {

    private lateinit var binding: ActivityPremiumBinding
    private lateinit var billingHelper: BillingHelper
    private val closeButtonHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val showCloseButtonRunnable = Runnable {
        if (!isDestroyed && !isFinishing) {
            binding.ivClose.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPremiumBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySystemBarInsets(R.color.colorBgPremium, false)

        billingHelper = BillingHelper.getInstance(this)

        // React immediately when purchase / restore completes
        billingHelper.onPremiumStatusChanged = { isPremium ->
            if (isPremium) {
                setResult(RESULT_OK)
                finish()
            }
        }

        // Refresh UI (price) as soon as details are loaded from Google Play
        billingHelper.onProductDetailsLoaded = {
            if (!isDestroyed && !isFinishing) {
                setupUI()
            }
        }
        
        billingHelper.onRestoreFinished = { isPremium ->
            if (!isPremium) {
                binding.btnRestore.isEnabled = true
                binding.btnRestore.text = getString(R.string.restore_purchase)
            }
        }


        setupUI()
        setupListeners()
        scheduleCloseButton()
    }

    private fun scheduleCloseButton() {
        binding.ivClose.visibility = View.INVISIBLE
        closeButtonHandler.postDelayed(showCloseButtonRunnable, 2000L)
    }

    override fun onResume() {
        super.onResume()
        // If the user was already premium (e.g. restored on a different device)
        // close this screen immediately
        if (PreferenceHelper.getInstance(this).isPremium()) {
            setResult(RESULT_OK)
            finish()
            return
        }
        // Refresh price label every time screen comes into foreground
        setupUI()
    }

    override fun onDestroy() {
        closeButtonHandler.removeCallbacks(showCloseButtonRunnable)
        super.onDestroy()
        // Clear the callbacks to avoid memory leaks
        billingHelper.onPremiumStatusChanged = null
        billingHelper.onProductDetailsLoaded = null
        billingHelper.onRestoreFinished = null
    }



    private fun setupUI() {
        val price = billingHelper.getProductPrice()
        if (price != null) {
            binding.tvPriceInfo.text = getString(R.string.premium_price_format, price)
        } else {
            binding.tvPriceInfo.text = getString(R.string.loading_price)
        }
    }

    private fun setupListeners() {
        binding.ivClose.setOnClickListener {
            finish()
        }

        binding.btnRestore.setOnClickListener {
            binding.btnRestore.isEnabled = false
            binding.btnRestore.text = getString(R.string.restoring)
            billingHelper.queryPurchases()
            // Re-enable button after a short delay regardless of outcome
            binding.root.postDelayed({
                binding.btnRestore.isEnabled = true
                binding.btnRestore.text = getString(R.string.restore_purchase)
            }, 4000)
        }

        binding.btnContinue.setOnClickListener {
            billingHelper.launchBillingFlow(this)
        }

        binding.tvTryLimited.setOnClickListener {
            finish()
        }

        binding.tvTerms.setOnClickListener {
            val url = AdsConfigManager.config.termsAndConditionsUrl.ifBlank {
                "https://policies.google.com/terms"
            }
            openUrl(url)
        }

        binding.tvPrivacy.setOnClickListener {
            val url = AdsConfigManager.config.privacyPolicyUrl.ifBlank {
                PRIVACY_POLICY
            }
            openUrl(url)
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
