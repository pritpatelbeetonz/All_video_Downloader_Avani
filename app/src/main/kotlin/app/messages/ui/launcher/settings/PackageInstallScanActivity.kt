package app.messages.ui.launcher.settings

import android.os.Bundle
import app.messages.R
import app.messages.databinding.ActivityPackageInstallScanBinding
import app.messages.preferences.PreferenceHelper
import app.messages.ui.base.BaseActivity

class PackageInstallScanActivity : BaseActivity() {

    private lateinit var binding: ActivityPackageInstallScanBinding
    private lateinit var prefs: PreferenceHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPackageInstallScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(R.color.bg_main, !isNightModeActive())

        prefs = PreferenceHelper.getInstance(this)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        binding.switchInstallScan.isChecked = prefs.getPackageInstallScan()
    }

    private fun setupListeners() {
        binding.switchInstallScan.setOnCheckedChangeListener { _, isChecked ->
            prefs.setPackageInstallScan(isChecked)
        }
    }
}
