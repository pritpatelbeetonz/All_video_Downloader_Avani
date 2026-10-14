package app.messages.ui.launcher.settings

import android.content.Intent
import android.os.Bundle
import app.messages.R
import app.messages.databinding.ActivityPackageMonitorSettingsBinding
import app.messages.preferences.PreferenceHelper
import app.messages.ui.base.BaseActivity

class PackageMonitorSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityPackageMonitorSettingsBinding
    private lateinit var prefs: PreferenceHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPackageMonitorSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(R.color.bg_main, !isNightModeActive())

        prefs = PreferenceHelper.getInstance(this)

        setupClicks()
    }

    override fun onResume() {
        super.onResume()
        updateStatuses()
    }

    private fun updateStatuses() {
        val installScanOn = prefs.getPackageInstallScan()
        val uninstallScanOn = prefs.getPackageUninstallScan()

        binding.tvInstallScanStatus.text = if (installScanOn) "Status: ON" else "Status: OFF"
        binding.tvUninstallScanStatus.text = if (uninstallScanOn) "Status: ON" else "Status: OFF"
    }

    private fun setupClicks() {
        binding.btnBack.setOnClickListener { finish() }

        binding.cardPackageInstallScan.setOnClickListener {
            startActivity(Intent(this, PackageInstallScanActivity::class.java))
        }

        binding.cardPackageUninstallScan.setOnClickListener {
            startActivity(Intent(this, PackageUninstallScanActivity::class.java))
        }
    }
}
