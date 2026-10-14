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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_video_downloader_container, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val existing = childFragmentManager.findFragmentByTag(TAG_FLUTTER)
        if (existing == null) {
            val flutterFragment = FlutterFragment.withCachedEngine(VideoDownloaderEngineHelper.ENGINE_ID)
                .renderMode(RenderMode.texture)
                .transparencyMode(TransparencyMode.opaque)
                .build<FlutterFragment>()

            childFragmentManager.beginTransaction()
                .replace(R.id.flutter_container, flutterFragment, TAG_FLUTTER)
                .commitNowAllowingStateLoss()
        }
    }

    companion object {
        private const val TAG_FLUTTER = "tag_flutter_video_downloader"
    }
}
