package app.messages.ui.launcher.settings

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.SeekBar
import app.messages.R
import app.messages.databinding.ActivityAppDrawerSettingsBinding
import app.messages.preferences.PreferenceHelper
import app.messages.ui.base.BaseActivity

class AppDrawerSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityAppDrawerSettingsBinding
    private lateinit var prefs: PreferenceHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDrawerSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(R.color.bg_main, !isNightModeActive())

        prefs = PreferenceHelper.getInstance(this)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        val hideLabels = prefs.getDrawerHideLabels()
        val textSize = prefs.getDrawerTextSize()
        val iconSize = prefs.getDrawerIconSize()

        binding.switchHideLabels.isChecked = hideLabels
        binding.seekBarTextSize.progress = textSize
        binding.seekBarIconSize.progress = iconSize

        updatePreview(hideLabels, textSize, iconSize)
    }

    private fun setupListeners() {
        binding.switchHideLabels.setOnCheckedChangeListener { _, isChecked ->
            prefs.setDrawerHideLabels(isChecked)
            updatePreview(isChecked, binding.seekBarTextSize.progress, binding.seekBarIconSize.progress)
        }

        binding.seekBarTextSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val safeProgress = progress.coerceIn(8, 20)
                binding.tvTextSizeValue.text = "$safeProgress pt"
                prefs.setDrawerTextSize(safeProgress)
                updatePreview(binding.switchHideLabels.isChecked, safeProgress, binding.seekBarIconSize.progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekBarIconSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val safeProgress = progress.coerceIn(36, 72)
                binding.tvIconSizeValue.text = "$safeProgress dp"
                prefs.setDrawerIconSize(safeProgress)
                updatePreview(binding.switchHideLabels.isChecked, binding.seekBarTextSize.progress, safeProgress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updatePreview(hideLabels: Boolean, textSizePt: Int, iconSizeDp: Int) {
        val labelVisibility = if (hideLabels) View.GONE else View.VISIBLE
        val sampleLabels = listOf(binding.tvSample1, binding.tvSample2, binding.tvSample3, binding.tvSample4)
        val sampleIcons = listOf(binding.ivSample1, binding.ivSample2, binding.ivSample3, binding.ivSample4)

        sampleLabels.forEach { tv ->
            tv.visibility = labelVisibility
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizePt.toFloat())
        }

        val px = (iconSizeDp * resources.displayMetrics.density).toInt()
        sampleIcons.forEach { iv ->
            val params = iv.layoutParams
            params.width = px
            params.height = px
            iv.layoutParams = params
        }

        binding.tvTextSizeValue.text = "$textSizePt pt"
        binding.tvIconSizeValue.text = "$iconSizeDp dp"
    }
}
