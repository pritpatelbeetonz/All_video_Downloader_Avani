import 'package:flutter/foundation.dart';
import 'sm_downloader_service.dart';
import 'apihut_service.dart';
import '../models/sm_extraction_result.dart';

/// Coordinates primary extraction via SMDownloader and automatic fallback via ApiHut.
class VideoExtractorManager {
  final SmDownloaderService smDownloaderService;
  final ApiHutService apiHutService;

  /// When true, turns OFF ApiHut fallback in kDebugMode so you can test SMDownloader exclusively.
  static bool disableFallbackInDebug = true;

  VideoExtractorManager({
    SmDownloaderService? smDownloader,
    ApiHutService? apiHut,
  })  : smDownloaderService = smDownloader ?? SmDownloaderService(),
        apiHutService = apiHut ?? ApiHutService();

  /// List of 29 platforms supported by SMDownloader
  static const Set<String> smSupportedPlatforms = {
    "instagram",
    "threads",
    "bluesky",
    "tiktok",
    "youtube",
    "facebook",
    "x",
    "twitter",
    "douyin",
    "soundcloud",
    "dailymotion",
    "xiaohongshu",
    "weibo",
    "kuaishou",
    "reddit",
    "vimeo",
    "vk",
    "twitch",
    "pinterest",
    "rumble",
    "bilibili",
    "snapchat",
    "streamable",
    "bandcamp",
    "chzzk",
    "naver tv",
    "soop",
    "mixcloud",
    "ted",
    "loom",
  };

  /// Primary extraction flow:
  /// 1. Uses SMDownloader as Primary for its 29 supported platforms.
  /// 2. If in kDebugMode and disableFallbackInDebug is true, fallback is DISABLED to isolate SMDownloader testing.
  /// 3. If SMDownloader fails or is unsupported (e.g. LinkedIn), automatically routes/falls back to ApiHut with API key rotation.
  Future<SmExtractionResult?> extractVideo({
    required String url,
    required String platform,
    void Function()? onFallbackToApiHut,
  }) async {
    print('⚡ [Extractor] Processing video stream request for platform: $platform');
    final isSmSupported = smSupportedPlatforms.contains(platform.toLowerCase());

    // 1. Primary: SMDownloader for its 29 supported platforms
    if (isSmSupported) {
      try {
        print('🎯 [Extractor] Primary extractor: Using SMDownloader API for $platform...');
        final result = await smDownloaderService.extractMedia(url);
        print('✅ [Extractor] SMDownloader extraction successful!');
        return result;
      } catch (e) {
        print('⚠️ [Extractor] SMDownloader extraction encountered an issue: $e');

        // If in debug mode and fallback is disabled, DO NOT call ApiHut!
        if (kDebugMode && disableFallbackInDebug) {
          print('🛑 [Extractor] (kDebugMode active) Fallback to ApiHut is DISABLED for testing SMDownloader.');
          return null;
        }

        print('🔀 [Extractor] Seamlessly switching to Fallback (ApiHut with Key Rotation)...');
      }
    } else {
      print('ℹ️ [Extractor] Platform "$platform" is not in SMDownloader list. Routing directly to Fallback ApiHut...');
    }

    // Fallback: ApiHut with API key rotation
    try {
      onFallbackToApiHut?.call();
      final fallbackResult = await apiHutService.fetchVideo(
        url: url,
        platform: platform,
      );
      if (fallbackResult != null) {
        print('✅ [Extractor] Fallback ApiHut successfully extracted media.');
        return fallbackResult;
      }
    } catch (e) {
      print('❌ [Extractor] Fallback ApiHut error: $e');
    }

    print('❌ [Extractor] All extraction attempts failed for: $url');
    return null;
  }
}
