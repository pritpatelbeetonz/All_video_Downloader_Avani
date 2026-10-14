package app.messages.ui.activity

import android.os.Bundle
import app.messages.R
import app.messages.databinding.ActivitySettingsBinding
import app.messages.ui.base.BaseActivity

class SettingsActivity : BaseActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(R.color.bg_main, !isNightModeActive())
    }
}
