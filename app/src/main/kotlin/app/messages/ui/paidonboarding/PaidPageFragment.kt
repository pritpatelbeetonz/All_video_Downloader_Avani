package app.messages.ui.paidonboarding

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment

/**
 * Base for the Flow A page fragments. A page is looked up by its index in [PaidOnboardingPages.ALL],
 * so the fragment survives recreation without parcelling the page.
 */
abstract class PaidPageFragment<P : PaidPage> : Fragment() {

    @Suppress("UNCHECKED_CAST")
    protected val page: P
        get() = PaidOnboardingPages.ALL[requireArguments().getInt(ARG_INDEX)] as P

    /** 0-based position in [PaidOnboardingPages.ALL]. The ad config uses 1-based page numbers = this + 1. */
    protected val pageIndex: Int
        get() = requireArguments().getInt(ARG_INDEX)

    /** Launches the dummy-ad web step; on close we STAY on this same page (no advance). */
    private val dummyAdLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (isAdded) PaidChromeTabManager.onWebStepClosed(requireActivity()) { /* stay on same page */ }
    }

    /** Fill this page's native ad slot (dummy image or real AdMob, or hide) — call from onViewCreated. */
    protected fun bindNativeAd(container: FrameLayout) {
        FlowAAdBinder.bindNative(requireActivity(), pageIndex + 1, container, dummyAdLauncher)
    }

    companion object {
        private const val ARG_INDEX = "index"

        fun <F : PaidPageFragment<*>> F.withIndex(index: Int): F = apply {
            arguments = Bundle().apply { putInt(ARG_INDEX, index) }
        }
    }
}
