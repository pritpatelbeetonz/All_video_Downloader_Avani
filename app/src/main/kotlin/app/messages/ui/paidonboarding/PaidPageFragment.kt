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
        if (isAdded) PaidChromeTabManager.onWebStepClosed(requireActivity(), stepTag = "page_${pageIndex + 1}") { /* stay on same page */ }
    }

    /**
     * Whether this page's form has a selection. The Next button is blocked (with a toast) until this
     * is true. Selection pages (gender / choice / interests) override; info pages stay always-answered.
     */
    open fun isAnswered(): Boolean = true

    private var nativeContainer: FrameLayout? = null
    private var nativeContainer2: FrameLayout? = null

    /** Fill this page's native ad slot (dummy image or real AdMob, or hide) — call from onViewCreated. */
    protected fun bindNativeAd(container: FrameLayout) {
        nativeContainer = container
        val act = activity as? PaidOnboardingActivity
        val stepTag = "page_${pageIndex + 1}"
        act?.prepareNativeShift(stepTag, container)
        FlowAAdBinder.bindNative(
            activity = requireActivity(),
            pageNumber = pageIndex + 1,
            container = container,
            launcher = dummyAdLauncher,
            onRendered = { act?.onNativeAdRendered(container) },
            onFailed = { act?.onNativeAdFailed(container) }
        )
    }

    /** Fill this page's SECOND native ad slot (feature pages) — independent dummy/real/off config. */
    protected fun bindNativeAd2(container: FrameLayout) {
        nativeContainer2 = container
        FlowAAdBinder.bindNative2(requireActivity(), pageIndex + 1, container, dummyAdLauncher)
    }

    override fun onResume() {
        super.onResume()
        val act = activity as? PaidOnboardingActivity
        val stepTag = "page_${pageIndex + 1}"
        nativeContainer?.let { container ->
            if (container.childCount == 0 && isAdded) {
                act?.prepareNativeShift(stepTag, container)
                FlowAAdBinder.bindNative(
                    activity = requireActivity(),
                    pageNumber = pageIndex + 1,
                    container = container,
                    launcher = dummyAdLauncher,
                    onRendered = { act?.onNativeAdRendered(container) },
                    onFailed = { act?.onNativeAdFailed(container) }
                )
            }
        }
        nativeContainer2?.let { container ->
            if (container.childCount == 0 && isAdded) {
                FlowAAdBinder.bindNative2(requireActivity(), pageIndex + 1, container, dummyAdLauncher)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        nativeContainer = null
        nativeContainer2 = null
    }

    companion object {
        private const val ARG_INDEX = "index"

        fun <F : PaidPageFragment<*>> F.withIndex(index: Int): F = apply {
            arguments = Bundle().apply { putInt(ARG_INDEX, index) }
        }
    }
}
