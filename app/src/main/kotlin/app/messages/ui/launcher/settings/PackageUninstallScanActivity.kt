package app.messages.ui.launcher.settings

import android.os.Bundle
import app.messages.R
import app.messages.databinding.ActivityPackageUninstallScanBinding
import app.messages.preferences.PreferenceHelper
import app.messages.ui.base.BaseActivity

class PackageUninstallScanActivity : BaseActivity() {

    private lateinit var binding: ActivityPackageUninstallScanBinding
    private lateinit var prefs: PreferenceHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPackageUninstallScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(R.color.bg_main, !isNightModeActive())

        prefs = PreferenceHelper.getInstance(this)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        binding.switchUninstallScan.isChecked = prefs.getPackageUninstallScan()
    }

    private fun setupListeners() {
        binding.switchUninstallScan.setOnCheckedChangeListener { _, isChecked ->
            prefs.setPackageUninstallScan(isChecked)
        }
    }
}
