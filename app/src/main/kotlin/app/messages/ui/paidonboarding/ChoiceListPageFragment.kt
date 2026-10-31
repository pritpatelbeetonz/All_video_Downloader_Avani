package app.messages.ui.paidonboarding

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.messages.databinding.ItemPaidChoiceBinding
import app.messages.databinding.PagePaidChoiceListBinding

/** Pages 2-4: one choice from a list (age range, download category, watch category). First option preselected. */
class ChoiceListPageFragment : PaidPageFragment<PaidPage.Choice>() {

    private var _binding: PagePaidChoiceListBinding? = null
    private val binding get() = _binding!!
    private val rows = mutableListOf<Pair<ChoiceOption, ItemPaidChoiceBinding>>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = PagePaidChoiceListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvTitle.setText(page.titleRes)
        binding.tvSubtitle.setText(page.subtitleRes)

        rows.clear()
        page.options.forEach { option ->
            val row = ItemPaidChoiceBinding.inflate(layoutInflater, binding.llOptions, true)
            row.tvBadge.text = option.badge
            row.tvBadge.backgroundTintList = ColorStateList.valueOf(option.color)
            row.tvLabel.setText(option.labelRes)
            row.root.setOnClickListener { select(option.id) }
            rows += option to row
        }
        select(PaidOnboardingPrefs.answer(requireContext(), page.key) ?: page.options.first().id)
    }

    private fun select(id: String) {
        PaidOnboardingPrefs.setAnswer(requireContext(), page.key, id)
        rows.forEach { (option, row) -> row.rbSelected.isChecked = option.id == id }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rows.clear()
        _binding = null
    }
}
