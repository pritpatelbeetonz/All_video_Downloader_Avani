package app.messages.ui.paidonboarding

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import app.messages.R
import app.messages.databinding.PagePaidInterestsBinding
import com.google.android.material.chip.Chip

/** Page 5: interests as chips, any number (none is fine). */
class InterestsPageFragment : PaidPageFragment<PaidPage.Interests>() {

    private var _binding: PagePaidInterestsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = PagePaidInterestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvTitle.setText(page.titleRes)
        binding.tvSubtitle.setText(page.subtitleRes)

        val context = requireContext()
        val accent = ContextCompat.getColor(context, R.color.paidOnbAccent)
        val white = ContextCompat.getColor(context, R.color.colorWhiteCommon)
        val checked = intArrayOf(android.R.attr.state_checked)
        val unchecked = intArrayOf()
        val selected = PaidOnboardingPrefs.interests(context).toMutableSet()

        page.options.forEach { option ->
            val chip = Chip(context).apply {
                setText(option.labelRes)
                isCheckable = true
                isCheckedIconVisible = false
                isChecked = option.id in selected
                typeface = ResourcesCompat.getFont(context, R.font.geist_semibold)
                textSize = 16f
                chipStrokeWidth = resources.displayMetrics.density
                chipStrokeColor = ColorStateList.valueOf(accent)
                chipBackgroundColor = ColorStateList(arrayOf(checked, unchecked), intArrayOf(accent, white))
                setTextColor(ColorStateList(arrayOf(checked, unchecked), intArrayOf(white, accent)))
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selected += option.id else selected -= option.id
                    PaidOnboardingPrefs.setInterests(context, selected)
                }
            }
            binding.chipGroup.addView(chip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
