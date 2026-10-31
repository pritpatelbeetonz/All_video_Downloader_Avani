package app.messages.ui.paidonboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import app.messages.R
import app.messages.databinding.PagePaidGenderBinding

/** Page 1: Personalized information (gender). Female is preselected, as in the reference. */
class GenderPageFragment : PaidPageFragment<PaidPage.Gender>() {

    private var _binding: PagePaidGenderBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = PagePaidGenderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val selected = PaidOnboardingPrefs.answer(requireContext(), page.key) ?: FEMALE
        select(selected)
        binding.llFemale.setOnClickListener { select(FEMALE) }
        binding.llMale.setOnClickListener { select(MALE) }
    }

    private fun select(id: String) {
        PaidOnboardingPrefs.setAnswer(requireContext(), page.key, id)
        val ring = ContextCompat.getDrawable(requireContext(), R.drawable.bg_paid_onb_avatar_selected)
        binding.flFemaleAvatar.foreground = if (id == FEMALE) ring else null
        binding.flMaleAvatar.foreground = if (id == MALE) ring else null
        binding.rbFemale.isChecked = id == FEMALE
        binding.rbMale.isChecked = id == MALE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val FEMALE = "female"
        private const val MALE = "male"
    }
}
