import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:dio/dio.dart';
import 'package:path_provider/path_provider.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:video_downloader/for_ads/ads/ads_splash_utils.dart';
import 'package:video_downloader/for_ads/ads/ads_variable.dart';
import '../providers/Browser_data_provider.dart';
import '../utility/platform_detector.dart';
import 'Loadingscreen.dart';
import '../../../smdownloader/smdownloader.dart';

class DownloadPage extends ConsumerStatefulWidget {
  const DownloadPage({super.key});

  @override
  ConsumerState<DownloadPage> createState() => _DownloadPageState();
}

class _DownloadPageState extends ConsumerState<DownloadPage> {
  bool isLoading = false;
  bool isDownloading = false;
  bool isCompleted = false;
  double progress = 0;
  int receivedBytes = 0;
  int totalBytes = 0;
  String? videoUrl;
  String? thumbnail;
  String? fileName;
  bool downloading=false;

  @override
  void initState() {
    super.initState();
    // 🔥 AUTO CALL API ON PAGE LOAD
    WidgetsBinding.instance.addPostFrameCallback((_) {
      fetchVideo();
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

  // 🔥 API CALL
  Future<void> fetchVideo() async {
    final state = ref.read(BrowserURl);
    final url = state["url"] ?? "";
    final platform = state["platform"] ?? "";

    if (!isDownloadSupported(platform)) {
      showToast("Download not supported for $platform ❌");
    }
    if (!mounted) return;
    setState(() {
      isLoading = true;
    });

    try {
      final extractor = ref.read(videoExtractorManagerProvider);
      final result = await extractor.extractVideo(url: url, platform: platform);

      if (result != null) {
        setState(() {
          videoUrl = result.videoUrl;
          thumbnail = result.thumbnailUrl;
        });
      } else {
        showToast("Failed to fetch video");
      }
    } catch (e) {
      showErrorDialog();
      print("❌ [DownloadPage] Error: $e");
    }
    setState(() {
      isLoading = false;
    });
  }

  //check that image or the video
  String detectTypeFromRapidUrl(String url) {
    final u = url.toLowerCase();
    // Explicit image markers -> image; everything else from the video
    // extractor is a video (e.g. Instagram CDN stream URLs that match no
    // marker used to fall through to "unknown" and get saved as .jpg).
    if (u.contains("/thumb") ||
        u.contains(".jpg") ||
        u.contains(".jpeg") ||
        u.contains(".png") ||
        u.contains(".webp") ||
        u.contains(".heic")) {
      return "image";
    }
    return "video";
  }

  static const MethodChannel _nativeChannel = MethodChannel("nativeChannel");

  // 🔥 DOWNLOAD FUNCTION
  // Downloads to the app cache (always writable, no permission), then hands the
  // finished file to native MediaStore so it lands in the public
  // Download/Video_downloader folder the user sees in Files -> Downloads.
  Future<void> downloadVideo() async {
    if (videoUrl == null) return;

    final ext =
        detectTypeFromRapidUrl(videoUrl ?? "") == "video" ? "mp4" : "jpg";
    final mime = ext == "mp4" ? "video/mp4" : "image/jpeg";
    final state = ref.read(BrowserURl);
    final platform = state["platform"] ?? "video";
    final name =
        "${platform}_video_${DateTime.now().millisecondsSinceEpoch}.$ext";

    final tmpDir = await getTemporaryDirectory();
    final tmpPath = "${tmpDir.path}/$name";

    setState(() {
      isDownloading = true;
      progress = 0;
      receivedBytes = 0;
      totalBytes = 0;
      isCompleted = false;
    });

    try {
      await Dio().download(
        videoUrl!,
        tmpPath,
        onReceiveProgress: (received, total) {
          if (!mounted) return;
          setState(() {
            receivedBytes = received;
            totalBytes = total;
            progress = total > 0 ? received / total : 0;
          });
        },
      );

      // Move the finished file into the public Download folder via MediaStore.
      final savedPath = await _saveToDownloads(tmpPath, name, mime);

      // Remove the temp copy either way.
      try {
        await File(tmpPath).delete();
      } catch (_) {}

      if (!mounted) return;
      if (savedPath == null) {
        setState(() {
          isDownloading = false;
        });
        showDownloadErrorDialog();
        return;
      }
      fileName = name;
      setState(() {
        isDownloading = false;
        isCompleted = true;
      });
      showToast("Saved to Downloads");
    } catch (e) {
      try {
        await File(tmpPath).delete();
      } catch (_) {}
      if (!mounted) return;
      setState(() {
        isDownloading = false;
      });
      showDownloadErrorDialog();
      // showErrorDialog();
      print("Download error: $e");
    }
  }

  // Hands the finished temp file to the native MediaStore saver.
  // Returns the saved public path on success, null on failure.
  Future<String?> _saveToDownloads(
      String sourcePath, String name, String mime) async {
    try {
      final result =
          await _nativeChannel.invokeMethod<String>("saveToDownloads", {
        "sourcePath": sourcePath,
        "fileName": name,
        "mime": mime,
        "subDir": "",
      });
      return result;
    } catch (e) {
      print("saveToDownloads error: $e");
      return null;
    }
  }

  /// Bytes -> "12.3" (MB, 1 decimal).
  String _mb(num bytes) => (bytes / (1024 * 1024)).toStringAsFixed(1);

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(BrowserURl);
    final url = state["url"] ?? "";
    final platform = state["platform"] ?? "";
    if (isLoading) {
      return const Scaffold(
        body: Center(
          child: LoadingChartWidget(),
        ),
      );
    }
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          onPressed: () {
            AdsVariable.onShowAds(
              context,
              place: 'VD_Download_Click',
              onComplete: () {
                Navigator.pop(context);
              },
            );
          },
          icon: Image.asset("assets/icons/back.png",height: 30,width: 30,),
        ),
        title: Text(
          fileName ?? "$platform video",
          style: const TextStyle(fontSize: 18,fontWeight:FontWeight.w500,),
          overflow: TextOverflow.ellipsis,
        ),
        centerTitle: true,
      ),

      body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // ✅ When data loaded
                if (!isLoading && videoUrl != null) ...[
                  /// Thumbnail UI
                  if (thumbnail != null) ...[
                    Expanded(child:
                    ClipRRect(
                        borderRadius: BorderRadius.circular(16),
                        child:
                            /// Thumbnail Image
                            Image.network(
                              thumbnail!,
                              width: double.infinity,
                              fit: BoxFit.cover,
                            ),
                      ),
                    ),
                    const SizedBox(height: 20),
                  ],

                  /// If NO thumbnail show URL
                  if (thumbnail == null) ...[
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.grey.shade200,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Text(
                        videoUrl!,
                        style: const TextStyle(fontSize: 14),
                      ),
                    ),
                    const SizedBox(height: 20),
                  ],

                  /// Live download progress (MB + %)
                  if (isDownloading) ...[
                    ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: LinearProgressIndicator(
                        value: progress > 0 ? progress : null,
                        minHeight: 8,
                        backgroundColor: Colors.grey.shade300,
                        valueColor: const AlwaysStoppedAnimation<Color>(
                          Color(0xFFFF7134),
                        ),
                      ),
                    ),
                    const SizedBox(height: 8),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          totalBytes > 0
                              ? "${_mb(receivedBytes)} MB / ${_mb(totalBytes)} MB"
                              : "${_mb(receivedBytes)} MB",
                          style: const TextStyle(
                            fontSize: 13,
                            color: Colors.grey,
                          ),
                        ),
                        if (totalBytes > 0)
                          Text(
                            "${(progress * 100).toStringAsFixed(0)}%",
                            style: const TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                              color: Color(0xFFFF7134),
                            ),
                          ),
                      ],
                    ),
                    const SizedBox(height: 14),
                  ],

                  /// Download Button
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFFFF7134),
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(30),
                        ),
                      ),
                      onPressed: (){
                        if(!isDownloading){
                          downloadVideo();
                        }
                      },
                      icon: Icon(
                        isDownloading ? Icons.downloading : Icons.download,
                        color: Colors.white,
                      ),
                      label: Text(
                        isDownloading
                            ? (totalBytes > 0
                                ? "Downloading  ${(progress * 100).toStringAsFixed(0)}%"
                                : "Downloading…")
                            : "Download",
                        style: const TextStyle(color: Colors.white, fontSize: 16),
                      ),
                    ),
                  ),

                  const SizedBox(height: 12),
                ],
              ],
            ),
          ),
        ),
    );
  }

  void showErrorDialog() {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        return Dialog(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(24),
          ),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 28),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [

                /// Icon
                Image.asset(
                  "assets/icons/linkfail.png",
                  height: 70,
                  width: 70,
                ),

                const SizedBox(height: 20),

                /// Title
                const Text(
                  "Something went wrong",
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w600,
                  ),
                ),

                const SizedBox(height: 8),

                /// Subtitle
                const Text(
                  "Check your URL or try again.",
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 14,
                    color: Colors.grey,
                  ),
                ),

                const SizedBox(height: 24),

                /// Refresh Button
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      elevation: 0, // remove shadow
                      shadowColor: Colors.transparent,
                      splashFactory: NoSplash.splashFactory, // remove ripple
                      backgroundColor: Colors.black,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(30),
                      ),
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                    onPressed: () {
                      Navigator.pop(context); // close dialog
                      Navigator.pop(context); // go back page
                    },
                    child: const Text(
                      "Refresh",
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 16,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  void showDownloadErrorDialog() {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        return Dialog(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(24),
          ),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 28),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [

                /// Icon
                Image.asset(
                  "assets/icons/download_failed.png",
                  height: 70,
                  width: 70,
                ),

                const SizedBox(height: 20),

                /// Title
                const Text(
                  "Download Failed",
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w600,
                  ),
                ),

                const SizedBox(height: 8),

                /// Description
                const Text(
                  "Something went wrong while downloading.",
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 14,
                    color: Colors.grey,
                  ),
                ),

                const SizedBox(height: 24),

                /// Try Again Button
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      elevation: 0, // remove shadow
                      shadowColor: Colors.transparent,
                      splashFactory: NoSplash.splashFactory, // remove ripple
                      backgroundColor: Colors.black,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(30),
                      ),
                      padding: const EdgeInsets.symmetric(vertical: 14),
                    ),
                    onPressed: () {
                      Navigator.pop(context);
                    },
                    child: const Text(
                      "Try Again",
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 16,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
