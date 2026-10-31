package app.messages.ui.paidonboarding

import android.os.Bundle
import androidx.fragment.app.Fragment

/**
 * Base for the Flow A page fragments. A page is looked up by its index in [PaidOnboardingPages.ALL],
 * so the fragment survives recreation without parcelling the page.
 */
abstract class PaidPageFragment<P : PaidPage> : Fragment() {

    @Suppress("UNCHECKED_CAST")
    protected val page: P
        get() = PaidOnboardingPages.ALL[requireArguments().getInt(ARG_INDEX)] as P

    companion object {
        private const val ARG_INDEX = "index"

        fun <F : PaidPageFragment<*>> F.withIndex(index: Int): F = apply {
            arguments = Bundle().apply { putInt(ARG_INDEX, index) }
        }
    }
}
