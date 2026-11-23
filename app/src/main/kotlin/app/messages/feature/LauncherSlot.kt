package app.messages.feature

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.messages.R
import app.messages.ui.launcher.downloader.VideoDownloaderFeature

/**
 * THE one place that decides which app lives on launcher page 0.
 *
 *  - Another app:   `val feature: LauncherFeature = MyNewFeature`
 *  - No app at all: `val feature: LauncherFeature = NoFeature`
 */
object LauncherSlot {
    val feature: LauncherFeature = VideoDownloaderFeature

    /** Convenience for flow screens: `LauncherSlot.warmUp(applicationContext)`. */
    fun warmUp(context: android.content.Context) {
        (context.applicationContext as? Application)?.let { feature.warmUp(it) }
    }
}

/** Empty slot: page 0 is blank, the left edge tab is hidden, no heavy init runs. */
object NoFeature : LauncherFeature {
    override val id = "none"
    override val labelRes = R.string.app_name
    override val iconRes = R.mipmap.ic_launcher_round
    override val isEnabled = false
    override val lockPagerSwipeOnPage = false
    override fun createFragment(): Fragment = EmptySlotFragment()
}

class EmptySlotFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        View(requireContext())
}
