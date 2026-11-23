package app.messages.ui.launcher.downloader

import android.app.Application
import androidx.fragment.app.Fragment
import app.messages.MessagesApplication
import app.messages.R
import app.messages.feature.LauncherFeature
import app.messages.ui.videodownloader.VideoDownloaderEngineHelper

/** The Flutter Video Downloader as the launcher's page-0 app. Delegates to the existing code. */
object VideoDownloaderFeature : LauncherFeature {
    override val id = "video_downloader"
    override val labelRes = R.string.edge_tab_video_downloader
    override val iconRes = R.mipmap.ic_launcher_round
    // Flutter webview / reels gestures must never be intercepted by ViewPager2.
    override val lockPagerSwipeOnPage = true

    override fun createFragment(): Fragment = VideoDownloaderContainerFragment()

    override fun warmUp(app: Application) {
        (app as? MessagesApplication)?.warmFlutterEngineWhenIdle()
    }

    override fun ensureReady(app: Application) {
        (app as? MessagesApplication)?.ensureFlutterEngineReady()
    }

    override fun onAdsConfig(json: String) {
        VideoDownloaderEngineHelper.syncAdsConfigToFlutter(json)
    }

    override fun onLanguageChanged(code: String) {
        VideoDownloaderEngineHelper.notifyLanguageChanged(code)
    }
}
