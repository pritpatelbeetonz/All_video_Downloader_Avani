package app.messages.ui.launcher.settings

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.widget.RadioButton
import app.messages.R
import app.messages.databinding.ActivityGesturesSettingsBinding
import app.messages.databinding.DialogGestureSelectionBinding
import app.messages.preferences.PreferenceHelper
import app.messages.ui.base.BaseActivity

class GesturesSettingsActivity : BaseActivity() {

    private lateinit var binding: ActivityGesturesSettingsBinding
    private lateinit var prefs: PreferenceHelper

    private val actionTexts = listOf(
        "Do Nothing",
        "Open app drawer",
        "Open launcher setting",
        "Open home screen pop-up",
        "Open global search"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGesturesSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(R.color.bg_main, !isNightModeActive())

        prefs = PreferenceHelper.getInstance(this)

        setupUI()
        setupClicks()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        updateSubtitles()
    }

    private fun updateSubtitles() {
        binding.tvDoubleTapSubtitle.text = actionTexts.getOrElse(prefs.getGestureDoubleTap()) { "Do Nothing" }
        binding.tvTapAndHoldSubtitle.text = actionTexts.getOrElse(prefs.getGestureTapAndHold()) { "Open home screen pop-up" }
        binding.tvHomeButtonSubtitle.text = actionTexts.getOrElse(prefs.getGestureHomeButton()) { "Do Nothing" }
        binding.tvBackButtonSubtitle.text = actionTexts.getOrElse(prefs.getGestureBackButton()) { "Do Nothing" }
    }

    private fun setupClicks() {
        binding.cardDoubleTap.setOnClickListener {
            showGestureDialog("Double Tap", prefs.getGestureDoubleTap()) { selected ->
                prefs.setGestureDoubleTap(selected)
                updateSubtitles()
            }
        }

        binding.cardTapAndHold.setOnClickListener {
            showGestureDialog("Tap & Hold", prefs.getGestureTapAndHold()) { selected ->
                prefs.setGestureTapAndHold(selected)
                updateSubtitles()
            }
        }

        binding.cardHomeButton.setOnClickListener {
            showGestureDialog("Home Button", prefs.getGestureHomeButton()) { selected ->
                prefs.setGestureHomeButton(selected)
                updateSubtitles()
            }
        }

        binding.cardBackButton.setOnClickListener {
            showGestureDialog("Back Button", prefs.getGestureBackButton()) { selected ->
                prefs.setGestureBackButton(selected)
                updateSubtitles()
            }
        }
    }

    private fun showGestureDialog(title: String, currentAction: Int, onSelect: (Int) -> Unit) {
        val dialogBinding = DialogGestureSelectionBinding.inflate(LayoutInflater.from(this))
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.tvDialogTitle.text = title

        val radioButtons = listOf(
            dialogBinding.rbDoNothing,
            dialogBinding.rbOpenAppDrawer,
            dialogBinding.rbOpenLauncherSetting,
            dialogBinding.rbOpenHomeScreenPopup,
            dialogBinding.rbOpenGlobalSearch
        )

        var selectedIndex = currentAction.coerceIn(0, 4)
        radioButtons.getOrNull(selectedIndex)?.isChecked = true

        fun updateRadioColors(selected: Int) {
            val primaryColor = androidx.core.content.ContextCompat.getColor(this, R.color.text_primary)
            val secondaryColor = androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)
            radioButtons.forEachIndexed { index, rb ->
                if (index == selected) {
                    rb.setTextColor(primaryColor)
                } else {
                    rb.setTextColor(secondaryColor)
                }
            }
        }

        updateRadioColors(selectedIndex)

        radioButtons.forEachIndexed { index, rb ->
            rb.setOnClickListener {
                selectedIndex = index
                updateRadioColors(selectedIndex)
            }
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSelect.setOnClickListener {
            onSelect(selectedIndex)
            dialog.dismiss()
        }

        dialog.show()

        // A Dialog window wraps its content by default, which squeezed these rows
        // down to a couple of words per line ("Open\napp\ndrawer"). Pin it to most
        // of the screen width so every option reads on one line.
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * DIALOG_WIDTH_FRACTION).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private companion object {
        const val DIALOG_WIDTH_FRACTION = 0.88f
    }
}
