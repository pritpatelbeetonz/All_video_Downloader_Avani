import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';
import 'package:lottie/lottie.dart';
import 'package:video_downloader/for_ads/ads/ads_load_util.dart';
import 'package:video_downloader/for_ads/ads/ads_variable.dart';
import 'package:video_downloader/for_ads/utils/app_constants.dart';
import 'package:get/get.dart';
import '../providers/download_task_provider.dart';

class LoadingChartWidget extends ConsumerStatefulWidget {
  final String? taskId;
  final VoidCallback? onContinueInBackground;

  /// Set to true if you want the button visible immediately (e.g. for ApiHut direct requests).
  final bool showContinueInBackgroundImmediately;

  const LoadingChartWidget({
    super.key,
    this.taskId,
    this.onContinueInBackground,
    this.showContinueInBackgroundImmediately = false,
  });

  @override
  ConsumerState<LoadingChartWidget> createState() => _LoadingChartWidgetState();
}

class _LoadingChartWidgetState extends ConsumerState<LoadingChartWidget> {
  bool _showButton = false;
  Timer? _fallbackButtonTimer;
  bool _hasTriggeredAutoClose = false;
  DownloadStatus? _debugOverrideStatus;
  bool? _debugIsApiHut;

  NativeAd? _loadingNativeAd;
  bool _isLoadingNativeAd = false;

  @override
  void initState() {
    super.initState();
    _loadBigNativeAd();

    if (widget.showContinueInBackgroundImmediately) {
      _showButton = true;
    } else {
      // SMDownloader response is fast (<1s).
      // Only display "Continue in Background" if extraction takes longer (ApiHut fallback).
      _fallbackButtonTimer = Timer(const Duration(milliseconds: 2500), () {
        if (mounted) {
          setState(() {
            _showButton = true;
          });
        }
      });
    }
  }

  void _loadBigNativeAd() {
    if (AdsVariable.isPurchase ||
        !AdsLoadUtil.isValidAdId(AdsVariable.prAppBigNative)) {
      showLog("🚫 [Loading Screen Ad] Skipping load - purchase or invalid adId");
      return;
    }
    setState(() {
      _isLoadingNativeAd = true;
    });

    AppBigNativeAdPool.loadForScreen(
      screenTag: 'LoadingChartWidget',
      onLoaded: (newAd) {
        if (!mounted) {
          showLog("📦💾 [Loading Screen Ad] Screen closed before arrival -> Caching ad into pool");
          AppBigNativeAdPool.cacheUnshownAd(newAd);
          return;
        }
        showLog("🎉🖼️ [Loading Screen Ad] Big Native Ad loaded -> Displaying on Loading screen");
        final oldAd = _loadingNativeAd;
        setState(() {
          _loadingNativeAd = newAd;
          _isLoadingNativeAd = false;
        });
        if (oldAd != null && oldAd != newAd) {
          Future.delayed(const Duration(milliseconds: 600), () {
            oldAd.dispose();
          });
        }
      },
      onFailed: () {
        showLog("⚠️❌ [Loading Screen Ad] Big Native Ad load failed");
        if (!mounted) return;
        setState(() {
          _isLoadingNativeAd = false;
        });
      },
    );
  }

  @override
  void dispose() {
    _fallbackButtonTimer?.cancel();
    AppBigNativeAdPool.detachScreen('LoadingChartWidget');
    final adToDispose = _loadingNativeAd;
    _loadingNativeAd = null;
    if (adToDispose != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        adToDispose.dispose();
      });
    }
    super.dispose();
  }

  void _handleClose() {
    if (widget.onContinueInBackground != null) {
      widget.onContinueInBackground!();
    } else if (mounted && Navigator.canPop(context)) {
      Navigator.pop(context);
    }
  }

  Widget _buildDebugChip(String label, DownloadStatus status, DownloadStatus activeStatus) {
    final isSelected = activeStatus == status;
    return GestureDetector(
      onTap: () {
        setState(() {
          _debugOverrideStatus = status;
          if (status == DownloadStatus.downloading || status == DownloadStatus.failed) {
            _showButton = true;
          }
        });
      },
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        decoration: BoxDecoration(
          color: isSelected ? const Color(0xFFFE7338) : Colors.white,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: isSelected ? const Color(0xFFFE7338) : Colors.grey.shade300,
          ),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 11,
            fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
            color: isSelected ? Colors.white : Colors.grey.shade700,
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    // Listen for completion to automatically close dialog after a short delay
    ref.listen<List<DownloadTask>>(downloadTaskProvider, (previous, next) {
      if (widget.taskId == null || _hasTriggeredAutoClose) return;

      final currentTask = next.where((t) => t.id == widget.taskId).firstOrNull;
      if (currentTask != null && currentTask.status == DownloadStatus.completed) {
        _hasTriggeredAutoClose = true;
        Timer(const Duration(milliseconds: 1400), () {
          if (mounted && Navigator.canPop(context)) {
            _handleClose();
          }
        });
      }
    });

    final taskList = ref.watch(downloadTaskProvider);
    final realTask = widget.taskId != null
        ? taskList.where((t) => t.id == widget.taskId).firstOrNull
        : null;

    final effectiveStatus = (kDebugMode && _debugOverrideStatus != null)
        ? _debugOverrideStatus!
        : (realTask?.status ?? DownloadStatus.fetching);

    final isDownloading = effectiveStatus == DownloadStatus.downloading;
    final isCompleted = effectiveStatus == DownloadStatus.completed;
    final isFailed = effectiveStatus == DownloadStatus.failed;

    // Check if ApiHut fallback is in use
    final isApiHutUsed = (kDebugMode && _debugIsApiHut != null)
        ? _debugIsApiHut!
        : (realTask?.isApiHut ?? widget.showContinueInBackgroundImmediately);

    final task = realTask ??
        (kDebugMode && isDownloading
            ? DownloadTask(
                id: 'debug_preview',
                url: 'https://example.com/video.mp4',
                fileName: 'sample_video.mp4',
                filePath: '/storage/emulated/0/Download/sample_video.mp4',
                createdAt: DateTime.now(),
                status: DownloadStatus.downloading,
                progress: 0.65,
                receivedBytes: 15400000,
                totalBytes: 24000000,
                speed: '2.4 MB/s',
                isApiHut: isApiHutUsed,
              )
            : null);

    // CRITICAL REQUIREMENT:
    // "Continue in Background" button MUST ONLY be shown if user/task is using ApiHut fallback!
    // For SMDownloader, it will NOT come!
    final shouldShowButton = isApiHutUsed && (_showButton || isDownloading);

    return Scaffold(
      backgroundColor: const Color(0xFFFAFAFA),
      body: PopScope(
        canPop: kDebugMode,
        child: SafeArea(
          child: SingleChildScrollView(
            physics: const BouncingScrollPhysics(),
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
            child: Column(
              children: [
                if (kDebugMode) ...[
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(
                          color: const Color(0xFFFFE3D7),
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: const Text(
                          "🛠️ DEBUG PREVIEW",
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                            color: Color(0xFFFE7338),
                          ),
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close_rounded, size: 26, color: Colors.black87),
                        onPressed: _handleClose,
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: Row(
                      children: [
                        _buildDebugChip("Fetching", DownloadStatus.fetching, effectiveStatus),
                        const SizedBox(width: 8),
                        _buildDebugChip("Downloading (65%)", DownloadStatus.downloading, effectiveStatus),
                        const SizedBox(width: 8),
                        _buildDebugChip("Completed", DownloadStatus.completed, effectiveStatus),
                        const SizedBox(width: 8),
                        _buildDebugChip("Failed", DownloadStatus.failed, effectiveStatus),
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),
                  // Engine selector in debug mode to test SMDownloader vs ApiHut
                  GestureDetector(
                    onTap: () {
                      setState(() {
                        _debugIsApiHut = !isApiHutUsed;
                      });
                    },
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: isApiHutUsed ? Colors.blue.shade50 : Colors.grey.shade100,
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: isApiHutUsed ? Colors.blue.shade300 : Colors.grey.shade300,
                        ),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            isApiHutUsed ? Icons.check_box_rounded : Icons.disabled_by_default_rounded,
                            size: 16,
                            color: isApiHutUsed ? Colors.blue.shade700 : Colors.grey.shade600,
                          ),
                          const SizedBox(width: 6),
                          Text(
                            isApiHutUsed
                                ? "Extractor: ApiHut (Button ENABLED)"
                                : "Extractor: SMDownloader (Button HIDDEN)",
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                              color: isApiHutUsed ? Colors.blue.shade800 : Colors.grey.shade700,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                ],

                // ═══════════════════════════════════════════════════════════
                // UPSIDE: STATUS & LOADER
                // ═══════════════════════════════════════════════════════════
                const SizedBox(height: 8),

                // Visual indicator based on status
                if (isCompleted) ...[
                  Container(
                    width: 100,
                    height: 100,
                    decoration: BoxDecoration(
                      color: Colors.green.shade50,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.check_circle_rounded,
                      color: Colors.green,
                      size: 64,
                    ),
                  ),
                ] else if (isFailed) ...[
                  Container(
                    width: 100,
                    height: 100,
                    decoration: BoxDecoration(
                      color: Colors.red.shade50,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.error_outline_rounded,
                      color: Colors.red,
                      size: 58,
                    ),
                  ),
                ] else if (isDownloading && task != null) ...[
                  // Dynamic Circular Progress Ring with Percentage
                  Stack(
                    alignment: Alignment.center,
                    children: [
                      SizedBox(
                        width: 120,
                        height: 120,
                        child: CircularProgressIndicator(
                          value: task.progress > 0 ? task.progress : null,
                          strokeWidth: 8,
                          backgroundColor: Colors.grey.shade200,
                          valueColor: const AlwaysStoppedAnimation<Color>(
                            Color(0xFFFE7338),
                          ),
                          strokeCap: StrokeCap.round,
                        ),
                      ),
                      Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            "${(task.progress * 100).toInt()}%",
                            style: const TextStyle(
                              fontSize: 28,
                              fontWeight: FontWeight.bold,
                              color: Color(0xFF222222),
                            ),
                          ),
                          const SizedBox(height: 2),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 7,
                              vertical: 2,
                            ),
                            decoration: BoxDecoration(
                              color: const Color(0xFFFFE3D7),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Text(
                              task.speed.isNotEmpty ? task.speed : "0 KB/s",
                              style: const TextStyle(
                                fontSize: 10,
                                fontWeight: FontWeight.w600,
                                color: Color(0xFFFE7338),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ] else ...[
                  // Lottie Loader for Fetching
                  SizedBox(
                    width: 120,
                    height: 120,
                    child: Lottie.asset(
                      "assets/images/Loader animation.json",
                      fit: BoxFit.contain,
                    ),
                  ),
                ],

                const SizedBox(height: 14),

                // Title
                Text(
                  isCompleted
                      ? 'download_complete'.tr
                      : isFailed
                          ? 'download_failed'.tr
                          : isDownloading
                              ? 'downloading_video'.tr
                              : 'fetching_media_stream'.tr,
                  style: const TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                    color: Colors.black,
                  ),
                ),

                const SizedBox(height: 6),

                // Subtitle
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Text(
                    isCompleted
                        ? 'saved_to_folder'.tr
                        : isFailed
                            ? (task?.errorMessage ?? 'download_failed'.tr)
                            : isDownloading
                                ? "${task?.formattedReceived ?? '0 MB'} / ${task?.formattedTotal ?? 'Unknown'} • ${'please_wait_fetching'.tr}"
                                : shouldShowButton
                                    ? 'fetching_background_allowed'.tr
                                    : 'please_wait_fetching'.tr,
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 13,
                      color: isFailed ? Colors.red.shade600 : Colors.grey.shade600,
                    ),
                  ),
                ),

                // Linear Progress bar during download for dual visualization
                if (isDownloading && task != null) ...[
                  const SizedBox(height: 12),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(6),
                    child: LinearProgressIndicator(
                      value: task.progress > 0 ? task.progress : null,
                      minHeight: 5,
                      backgroundColor: Colors.grey.shade200,
                      valueColor: const AlwaysStoppedAnimation<Color>(
                        Color(0xFFFE7338),
                      ),
                    ),
                  ),
                ],

                const SizedBox(height: 18),

                // ═══════════════════════════════════════════════════════════
                // DOWNSIDE: BIG NATIVE AD (POOL)
                // ═══════════════════════════════════════════════════════════
                AppBigNativeAdWidget(
                  showNativeAd: _loadingNativeAd,
                  isLoading: _isLoadingNativeAd,
                ),

                const SizedBox(height: 16),

                // ═══════════════════════════════════════════════════════════
                // ACTION BUTTON (Continue in Background / Close)
                // ═══════════════════════════════════════════════════════════
                if (isFailed) ...[
                  SizedBox(
                    width: double.infinity,
                    height: 50,
                    child: ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        elevation: 0,
                        backgroundColor: Colors.grey.shade800,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(30),
                        ),
                      ),
                      onPressed: _handleClose,
                      child: Text(
                        'close'.tr,
                        style: const TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ),
                ] else if (!isCompleted && shouldShowButton) ...[
                  SizedBox(
                    width: double.infinity,
                    height: 50,
                    child: ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        elevation: 0,
                        backgroundColor: const Color(0xFFFE7338),
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(30),
                        ),
                      ),
                      onPressed: _handleClose,
                      icon: const Icon(Icons.arrow_forward_rounded, size: 20),
                      label: Text(
                        'continue_in_background'.tr,
                        style: const TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ),
                ],

                const SizedBox(height: 12),
              ],
            ),
          ),
        ),
      ),
    );
  }
}