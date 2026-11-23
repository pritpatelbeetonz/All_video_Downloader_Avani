import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:video_downloader/for_ads/ads/ads_splash_utils.dart';
import 'package:video_downloader/for_ads/ads/ads_variable.dart';
import '../../../firebase_analysis.dart';
import '../../../utility/app_storage_utils.dart';
import '../providers/Browser_data_provider.dart';
import '../providers/download_task_provider.dart';
import '../utility/platform_detector.dart';
import 'Loadingscreen.dart';
import '../../../smdownloader/smdownloader.dart';

/// Pasted-link download (from the search page): fetches the media stream and
/// downloads it straight away on the loading screen — no preview / Download
/// button step. Uses the same task pipeline as the in-app browser's download
/// button, so the file also shows up in the Downloads tab with live progress.
class DownloadPage extends ConsumerStatefulWidget {
  const DownloadPage({super.key});

  @override
  ConsumerState<DownloadPage> createState() => _DownloadPageState();
}

class _DownloadPageState extends ConsumerState<DownloadPage> {
  String? _taskId;
  bool _isClosing = false;

  @override
  void initState() {
    super.initState();
    final state = ref.read(BrowserURl);
    final url = state["url"] ?? "";
    final platform = (state["platform"] ?? "").toString();
    final taskPlatform =
        (platform.isNotEmpty && platform != "unknown") ? platform : "other";

    FirebaseAnalyticsService.logDownloadClick(platform: taskPlatform, url: url);

    // Riverpod forbids modifying a provider during initState/build ("Tried to
    // modify a provider while the widget tree was building"), so the task is
    // created right after the first frame. Until then the loading screen shows
    // its default "Fetching" state (taskId == null).
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final taskId = ref.read(downloadTaskProvider.notifier).createFetchingTask(
            pageUrl: url,
            platform: taskPlatform,
          );
      setState(() => _taskId = taskId);
      _fetchAndDownload(taskId, url, platform);
    });
  }

  // 🔥 Check supported platforms
  bool isDownloadSupported(String platform) {
    if (platform == "youtube") {
      return AdsVariable.isYouTubeEnabled;
    }
    return PlatformDetector.isSupportedBySmDownloader(platform) ||
        platform == "linkedin";
  }

  /// Extract the stream, then start the download right away (no preview step).
  Future<void> _fetchAndDownload(
      String taskId, String url, String platform) async {
    if (!isDownloadSupported(platform)) {
      showToast("Download not supported for $platform ❌");
    }

    final notifier = ref.read(downloadTaskProvider.notifier);
    try {
      final extractor = ref.read(videoExtractorManagerProvider);
      final result = await extractor.extractVideo(
        url: url,
        platform: platform,
        onFallbackToApiHut: () => notifier.markUsingApiHut(taskId),
      );

      if (result == null) {
        notifier.markFetchingFailed(taskId, "Failed to fetch video");
        showToast("Failed to fetch video");
        return;
      }

      await notifier.attachStreamAndDownload(
        taskId: taskId,
        streamUrl: result.videoUrl,
        thumbnailUrl: result.thumbnailUrl,
        customFileName: result.title != null
            ? AppStorageUtils.sanitizeFileName(result.title!)
            : null,
        platform: platform,
        isApiHut: result.isApiHut,
      );
    } catch (e) {
      notifier.markFetchingFailed(taskId, e.toString());
      showToast("Failed to fetch video");
      debugPrint("❌ [DownloadPage] Error: $e");
    }
  }

  /// Leaves the loading screen (download finished, failed -> Close, or
  /// "Continue in background"). Keeps the same ad slot the old preview page's
  /// back button had.
  void _close() {
    if (_isClosing || !mounted) return;
    _isClosing = true;
    AdsVariable.onShowAds(
      context,
      place: 'VD_Download_Click',
      onComplete: () {
        if (mounted && Navigator.canPop(context)) {
          Navigator.pop(context);
        }
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return LoadingChartWidget(
      taskId: _taskId,
      onContinueInBackground: _close,
    );
  }
}
