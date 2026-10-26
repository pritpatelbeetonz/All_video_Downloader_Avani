import 'dart:developer' as developer;
import 'package:flutter/services.dart';
import '../../firebase_analysis.dart';
import 'sm_downloader_service.dart';
import 'apihut_service.dart';
import '../models/sm_extraction_result.dart';

const MethodChannel _nativeLogChannel = MethodChannel("nativeChannel");

void _extractorLog(String msg) {
  developer.log(msg, name: 'EXTRACTOR');
  print('⚡ [Extractor] $msg');
  try {
    _nativeLogChannel.invokeMethod('logNative', {
      'tag': 'EXTRACTOR',
      'message': msg,
    });
  } catch (_) {}
}

/// Coordinates primary extraction via SMDownloader and automatic fallback via ApiHut.
class VideoExtractorManager {
  final SmDownloaderService smDownloaderService;
  final ApiHutService apiHutService;

  /// Controls whether ApiHut fallback is disabled. Default is false (fallback is always enabled).
  static bool disableFallbackInDebug = false;

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
  /// 2. If SMDownloader fails or is unsupported (e.g. LinkedIn), automatically routes/falls back to ApiHut with API key rotation.
  Future<SmExtractionResult?> extractVideo({
    required String url,
    required String platform,
    void Function()? onFallbackToApiHut,
  }) async {
    _extractorLog('Processing video stream request for platform: $platform');
    final isSmSupported = smSupportedPlatforms.contains(platform.toLowerCase());
    String fallbackReason = 'none';

    // 1. Primary: SMDownloader for its 29 supported platforms
    if (isSmSupported) {
      try {
        _extractorLog('Primary extractor: Using SMDownloader API for $platform...');
        final result = await smDownloaderService.extractMedia(url);
        _extractorLog('✅ SMDownloader extraction successful!');

        // 📊 Firebase Event: SMDownloader succeeded as primary
        FirebaseAnalyticsService.logExtractorEvent(
          extractor: 'smdownloader',
          platform: platform,
          isFallback: false,
          fallbackReason: 'none',
          status: 'success',
          url: url,
        );

        return result;
      } catch (e) {
        _extractorLog('⚠️ SMDownloader extraction encountered an issue: $e');
        fallbackReason = 'smdownloader_failed';

        // 📊 Firebase Event: SMDownloader primary extraction failed
        FirebaseAnalyticsService.logExtractorEvent(
          extractor: 'smdownloader',
          platform: platform,
          isFallback: false,
          fallbackReason: 'none',
          status: 'failed',
          url: url,
          errorMessage: e.toString(),
        );

        // If fallback is explicitly disabled, DO NOT call ApiHut!
        if (disableFallbackInDebug) {
          _extractorLog('🛑 Fallback to ApiHut is disabled.');
          return null;
        }

        // 📊 Firebase Event: Log fallback initiation from SMDownloader to ApiHut
        FirebaseAnalyticsService.logExtractorFallback(
          fromExtractor: 'smdownloader',
          toExtractor: 'apihut',
          fallbackReason: fallbackReason,
          platform: platform,
          url: url,
          errorMessage: e.toString(),
        );

        _extractorLog('🔀 Seamlessly switching to Fallback (ApiHut with Key Rotation)...');
      }
    } else {
      fallbackReason = 'unsupported_platform';
      _extractorLog('ℹ️ Platform "$platform" is not in SMDownloader list. Routing directly to Fallback ApiHut...');

      // 📊 Firebase Event: Log fallback initiation due to unsupported platform in SMDownloader
      FirebaseAnalyticsService.logExtractorFallback(
        fromExtractor: 'smdownloader',
        toExtractor: 'apihut',
        fallbackReason: fallbackReason,
        platform: platform,
        url: url,
      );
    }

    // Fallback: ApiHut with API key rotation
    try {
      onFallbackToApiHut?.call();
      final fallbackResult = await apiHutService.fetchVideo(
        url: url,
        platform: platform,
      );
      if (fallbackResult != null) {
        _extractorLog('✅ Fallback ApiHut successfully extracted media.');

        // 📊 Firebase Event: ApiHut fallback succeeded
        FirebaseAnalyticsService.logExtractorEvent(
          extractor: 'apihut',
          platform: platform,
          isFallback: true,
          fallbackReason: fallbackReason,
          status: 'success',
          url: url,
        );

        return fallbackResult;
      } else {
        // 📊 Firebase Event: ApiHut returned null stream
        FirebaseAnalyticsService.logExtractorEvent(
          extractor: 'apihut',
          platform: platform,
          isFallback: true,
          fallbackReason: fallbackReason,
          status: 'failed',
          url: url,
          errorMessage: 'ApiHut returned null',
        );
      }
    } catch (e) {
      _extractorLog('❌ Fallback ApiHut error: $e');

      // 📊 Firebase Event: ApiHut fallback failed with exception
      FirebaseAnalyticsService.logExtractorEvent(
        extractor: 'apihut',
        platform: platform,
        isFallback: true,
        fallbackReason: fallbackReason,
        status: 'failed',
        url: url,
        errorMessage: e.toString(),
      );
    }

    print('❌ [Extractor] All extraction attempts failed for: $url');
    return null;
  }
}
