package app.messages.ui.paidonboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.messages.databinding.PagePaidFeatureBinding

/** Pages 6-11: one feature (title, description, image). The caption above the button is shown by the activity. */
class FeaturePageFragment : PaidPageFragment<PaidPage.Feature>() {

    private var _binding: PagePaidFeatureBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = PagePaidFeatureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvTitle.setText(page.titleRes)
        binding.tvDescription.setText(page.descriptionRes)
        binding.ivFeature.setImageResource(page.imageRes)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
