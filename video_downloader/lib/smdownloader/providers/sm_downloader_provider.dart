import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../services/sm_downloader_service.dart';
import '../services/apihut_service.dart';
import '../services/video_extractor_manager.dart';

/// Provider for SmDownloaderService
final smDownloaderServiceProvider = Provider<SmDownloaderService>((ref) {
  return SmDownloaderService();
});

/// Provider for ApiHutService (with API key rotation)
final apiHutServiceProvider = Provider<ApiHutService>((ref) {
  return ApiHutService();
});

/// Provider for VideoExtractorManager (Primary: SMDownloader, Fallback: ApiHut)
final videoExtractorManagerProvider = Provider<VideoExtractorManager>((ref) {
  final smDownloader = ref.watch(smDownloaderServiceProvider);
  final apiHut = ref.watch(apiHutServiceProvider);
  return VideoExtractorManager(
    smDownloader: smDownloader,
    apiHut: apiHut,
  );
});
