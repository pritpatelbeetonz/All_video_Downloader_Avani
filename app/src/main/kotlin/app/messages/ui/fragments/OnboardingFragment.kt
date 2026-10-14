package app.messages.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import app.messages.R
import app.messages.data.model.OnboardingModel
import app.messages.databinding.FragmentOnboardingBinding

class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_IMAGE = "image"
        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        private const val ARG_VIDEO = "video"

        fun newInstance(page: OnboardingModel) = OnboardingFragment().apply {
            arguments = bundleOf(
                ARG_IMAGE to page.imageRes,
                ARG_TITLE to page.title,
                ARG_MESSAGE to page.message,
                ARG_VIDEO to (page.videoRes ?: 0)
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.let {
            val videoRes = it.getInt(ARG_VIDEO, 0)
            val imageRes = it.getInt(ARG_IMAGE, 0)

            if (videoRes != 0) {
                binding.imageIllustration.visibility = View.GONE
                binding.videoContainer.visibility = View.VISIBLE
                binding.videoIllustration.visibility = View.VISIBLE
                binding.videoGradientOverlay.visibility = View.VISIBLE
                val videoUri = Uri.parse("android.resource://${requireContext().packageName}/$videoRes")
                binding.videoIllustration.setVideoURI(videoUri)
                binding.videoIllustration.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.setVolume(0f, 0f)
                    binding.videoIllustration.setVideoDimensions(mp.videoWidth, mp.videoHeight)
                    binding.videoIllustration.start()
                }
            } else {
                binding.videoContainer.visibility = View.GONE
                binding.videoIllustration.visibility = View.GONE
                binding.videoGradientOverlay.visibility = View.GONE
                binding.imageIllustration.visibility = View.VISIBLE
                if (imageRes != 0) {
                    binding.imageIllustration.setImageResource(imageRes)
                }
            }

            binding.tvTitle.text = it.getString(ARG_TITLE)
            binding.tvMessage.text = it.getString(ARG_MESSAGE)
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null && binding.videoIllustration.visibility == View.VISIBLE && !binding.videoIllustration.isPlaying) {
            binding.videoIllustration.start()
        }
    }

    override fun onPause() {
        super.onPause()
        if (_binding != null && binding.videoIllustration.isPlaying) {
            binding.videoIllustration.pause()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.videoIllustration?.stopPlayback()
        _binding = null
    }
}