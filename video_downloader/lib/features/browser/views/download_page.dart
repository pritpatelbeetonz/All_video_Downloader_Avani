import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:dio/dio.dart';
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
    if (url.contains("/v2")) return "video";
    if (url.contains("/thumb")) return "image";
    return "unknown";
  }

  // 🔥 DOWNLOAD FUNCTION
  Future<void> downloadVideo() async {
    if (videoUrl == null) return;

    final directoryPath = "/storage/emulated/0/Download/Video_downloader";
    // final dir = await getExternalStorageDirectory();
    // final directory = Directory("${dir!.path}/Video_downloader");
    final directory = Directory(directoryPath);

    if (!await directory.exists()) {
      await directory.create(recursive: true);
    }

    String extention =
    detectTypeFromRapidUrl(videoUrl ?? "") == "video" ? "mp4" : "jpg";
    final state = ref.read(BrowserURl);
    final platform = state["platform"] ?? "";
    fileName = await generateFileName(platform, directory, extention);
    final path = "${directory.path}/$fileName";

    setState(() {
      isDownloading = true;
      progress = 0;
      isCompleted = false;
    });

    try {
      await Dio().download(
        videoUrl!,
        path,
        onReceiveProgress: (received, total) {
          if (total != -1) {
            setState(() {
              progress = received / total;
            });
          }
        },
      );

      setState(() {
        isDownloading = false;
        isCompleted = true;
      });
      showToast("Video Downloaded");
    } catch (e) {
      if (!mounted) return;
      setState(() {
        isDownloading = false;
      });
      showDownloadErrorDialog();
      // showErrorDialog();
      print("Download error: $e");
    }
  }
  Future<String> generateFileName(String platform, Directory directory, String ext) async {
    int count = 0;

    if (await directory.exists()) {
      final files = directory.listSync();

      for (var file in files) {
        if (file.path.contains("${platform}_video_")) {
          count++;
        }
      }
    }

    int n = count + 1;

    return "${platform}_video_$n.$ext";
  }
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
                      icon: const Icon(Icons.download, color: Colors.white),
                      label: isDownloading ?
                      Text(
                        "Downloading",
                        style: TextStyle(color: Colors.white, fontSize: 16),
                      )
                          :
                      Text(
                        "Download",
                        style: TextStyle(color: Colors.white, fontSize: 16),
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
