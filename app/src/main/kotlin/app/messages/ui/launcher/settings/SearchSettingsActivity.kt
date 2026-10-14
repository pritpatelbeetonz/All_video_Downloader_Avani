package app.messages.ui.launcher.settings

import android.os.Bundle
import app.messages.R
import app.messages.databinding.ActivitySearchSettingsBinding
import app.messages.preferences.PreferenceHelper
import app.messages.ui.base.BaseActivity

class SearchSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySearchSettingsBinding
    private lateinit var prefs: PreferenceHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(R.color.bg_main, !isNightModeActive())

        prefs = PreferenceHelper.getInstance(this)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.switchDrawerSearchBar.isChecked = prefs.getDrawerSearchBarEnabled()
        binding.switchAutoOpenKeyboard.isChecked = prefs.getSearchAutoOpenKeyboard()
        binding.switchCloseDrawerOnAppOpen.isChecked = prefs.getSearchCloseDrawerOnAppOpen()
        binding.switchAppSuggestions.isChecked = prefs.getSearchAppSuggestionsEnabled()
    }

    private fun setupListeners() {
        binding.switchDrawerSearchBar.setOnCheckedChangeListener { _, isChecked ->
            prefs.setDrawerSearchBarEnabled(isChecked)
        }

        binding.switchAutoOpenKeyboard.setOnCheckedChangeListener { _, isChecked ->
            prefs.setSearchAutoOpenKeyboard(isChecked)
        }

        binding.switchCloseDrawerOnAppOpen.setOnCheckedChangeListener { _, isChecked ->
            prefs.setSearchCloseDrawerOnAppOpen(isChecked)
        }

        binding.switchAppSuggestions.setOnCheckedChangeListener { _, isChecked ->
            prefs.setSearchAppSuggestionsEnabled(isChecked)
        }
    }
}
