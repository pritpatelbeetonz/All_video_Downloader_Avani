package app.messages.ui.launcher.downloader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.messages.R
import app.messages.ui.videodownloader.VideoDownloaderEngineHelper
import io.flutter.embedding.android.FlutterFragment
import io.flutter.embedding.android.RenderMode
import io.flutter.embedding.android.TransparencyMode

class VideoDownloaderContainerFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Restore after process death (launcher re-created by Home / the system): super.onCreate re-creates
        // the saved FlutterFragment, which attaches to the cached engine right away. The engine is only
        // warmed later (idle / deferred attach), so without this it crashed with "cached FlutterEngine did
        // not exist" on every restart -> crash loop -> "failed to start several times". Must run BEFORE
        // super.onCreate. Fresh creates (no saved state) keep the deferred, non-blocking path below.
        // LauncherActivity now starts with super.onCreate(null), so this restore path should not run any
        // more; kept as a safety net (e.g. if this fragment is ever hosted elsewhere).
        if (savedInstanceState != null) {
            (requireContext().applicationContext as? app.messages.MessagesApplication)?.ensureFlutterEngineReady()
        }
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_video_downloader_container, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (childFragmentManager.findFragmentByTag(TAG_FLUTTER) != null) return
        val application = requireContext().applicationContext as? app.messages.MessagesApplication
        if (application == null || application.isFlutterEngineWarmed) {
            attachFlutter()
        } else {
            // Engine not warm yet: booting it here ran inside the layout pass and froze the main
            // thread for seconds (ANR warning). Let the launcher draw its first frame, then boot.
            view.postDelayed({ if (isAdded && view != null) attachFlutter() }, 400)
        }
    }

    private fun attachFlutter() {
        if (childFragmentManager.findFragmentByTag(TAG_FLUTTER) != null) return
        // Safety net so withCachedEngine never fails with "cached FlutterEngine did not exist".
        (requireContext().applicationContext as? app.messages.MessagesApplication)?.ensureFlutterEngineReady()
        val flutterFragment = FlutterFragment.withCachedEngine(VideoDownloaderEngineHelper.ENGINE_ID)
            .renderMode(RenderMode.texture)
            .transparencyMode(TransparencyMode.opaque)
            .build<FlutterFragment>()

        childFragmentManager.beginTransaction()
            .replace(R.id.flutter_container, flutterFragment, TAG_FLUTTER)
            .commitNowAllowingStateLoss()
    }

    private val markVisible = Runnable { VideoDownloaderEngineHelper.setDownloaderVisible(true) }

    // ViewPager2 resumes only the current page, so resumed == on screen. The
    // launcher briefly selects page 0 before jumping to its landing tab, hence
    // the short delay before reporting visible.
    override fun onResume() {
        super.onResume()
        view?.postDelayed(markVisible, VISIBLE_DEBOUNCE_MS)
    }

    override fun onPause() {
        view?.removeCallbacks(markVisible)
        VideoDownloaderEngineHelper.setDownloaderVisible(false)
        super.onPause()
    }

    companion object {
        private const val TAG_FLUTTER = "tag_flutter_video_downloader"
        // Also the ad dwell time: the downloader's big native (pr_appBigNative) is only requested once
        // it is "visible", so a user swiping past this page must not trigger a request whose ad lands
        // after they have left (that ad is never shown -> low show rate).
        private const val VISIBLE_DEBOUNCE_MS = 1000L
    }
}
