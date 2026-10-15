import 'package:lottie/lottie.dart';
import 'package:video_downloader/for_ads/ads/ads_splash_utils.dart';
import 'package:video_downloader/for_ads/ads/ads_variable.dart';
import 'package:video_downloader/import_all.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_flutter_android/webview_flutter_android.dart';
import '../providers/Browser_data_provider.dart';
import '../providers/SearchToBrowser.dart';
import '../utility/platform_detector.dart';
import 'package:share_plus/share_plus.dart';
import 'package:flutter/services.dart';
import '../providers/Bookmark_Provider.dart';
import '../views/Bookmart.dart';
import 'dart:convert';
import 'dart:io';
import 'package:dio/dio.dart';
import '../providers/download_task_provider.dart';
import '../providers/Tab_provider.dart';
import '../widgets/tabs_bottom_sheet.dart';
import 'Loadingscreen.dart';

import '../../../smdownloader/smdownloader.dart';
import '../../../utility/app_storage_utils.dart';
import 'package:get/get.dart';
import 'dart:developer' as developer;

void androidLog(String msg) {
  developer.log(msg, name: 'WEBVIEW_EVENT');
}

class Browser extends ConsumerStatefulWidget {
  final Function(WebViewController)? onControllerReady;

  const Browser({super.key, this.onControllerReady});

  @override
  ConsumerState<Browser> createState() => BrowserState();
}

class BrowserState extends ConsumerState<Browser> {
  TextEditingController searchbar = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  late final WebViewController browserController;
  bool _isLoading = false;
  bool _isInternalNavigation = false;
  String? videoUrl;
  String? thumbnail;
  bool _isLoadingScreenOpen = false;

  Future<void> startDownload({
    required String taskId,
    String? explicitTargetUrl,
    String? immediateStreamUrl,
    String? platform,
  }) async {
    final state = ref.read(BrowserURl);
    final pageUrl = (explicitTargetUrl != null && explicitTargetUrl.isNotEmpty)
        ? explicitTargetUrl
        : (state["url"] ?? "");
    final targetPlatform = (platform != null && platform.isNotEmpty)
        ? platform
        : (state["platform"] ?? "");

    try {
      // 1. If we already captured the direct playing video stream from in-page HTML5, download it directly!
      if (immediateStreamUrl != null && immediateStreamUrl.isNotEmpty) {
        await ref.read(downloadTaskProvider.notifier).attachStreamAndDownload(
          taskId: taskId,
          streamUrl: immediateStreamUrl,
          thumbnailUrl: null,
          customFileName: 'video_${DateTime.now().millisecondsSinceEpoch}.mp4',
          platform: targetPlatform,
        );
        return;
      }

      // 2. Try primary/fallback API extraction first
      final extractor = ref.read(videoExtractorManagerProvider);
      final result = await extractor.extractVideo(
        url: pageUrl,
        platform: targetPlatform,
        onFallbackToApiHut: () {
          ref.read(downloadTaskProvider.notifier).markUsingApiHut(taskId);
        },
      );

      if (result != null) {
        await ref.read(downloadTaskProvider.notifier).attachStreamAndDownload(
          taskId: taskId,
          streamUrl: result.videoUrl,
          thumbnailUrl: result.thumbnailUrl,
          customFileName: result.title != null
              ? AppStorageUtils.sanitizeFileName(result.title!)
              : null,
          platform: targetPlatform,
          isApiHut: result.isApiHut,
        );
        return;
      }

      ref.read(downloadTaskProvider.notifier).markFetchingFailed(
        taskId,
        "Failed to extract video from this page",
      );
      showToast("Failed to extract video from this page");
    } catch (e) {
      ref.read(downloadTaskProvider.notifier).markFetchingFailed(
        taskId,
        e.toString(),
      );
      showToast("Please play the video first, then tap Download");
    }
  }


  Future<void> downloadVideo(String url) async {

    final directoryPath = "/storage/emulated/0/Download/Video_downloader";
    final directory = Directory(directoryPath);

    if (!await directory.exists()) {
      await directory.create(recursive: true);
    }

    String extension = url.contains("/thumb") ? "jpg" : "mp4";

    final path =
        "${directory.path}/${DateTime.now().millisecondsSinceEpoch}.$extension";

    await Dio().download(
      url,
      path,
    );
  }
  // @override
  // void initState() {
  //   super.initState();
  //   final state = ref.read(BrowserURl);
  //   final url = state["url"] ?? "";
  //
  //   final URL = ref.read(BrowserURl);
  //   searchbar.text = url;
  //
  //   browserController = WebViewController()
  //     ..setJavaScriptMode(JavaScriptMode.unrestricted)
  //     ..setNavigationDelegate(
  //       NavigationDelegate(
  //         onPageFinished: (url) {
  //           // 👇 when user navigates inside WebView
  //           ref.read(BrowserURl.notifier).setUrlFromWeb(url);
  //         },
  //       ),
  //     )
  //     ..loadRequest(Uri.parse(url));
  // }

  bool _isRestrictedPlatform(String url) {
    final lower = url.toLowerCase();
    return lower.contains("tiktok.com") ||
        lower.contains("instagram.com") ||
        lower.contains("facebook.com") ||
        lower.contains("threads.net") ||
        lower.contains("twitter.com") ||
        lower.contains("x.com") ||
        lower.contains("snapchat.com") ||
        lower.contains("pinterest.com") ||
        lower.contains("vimeo.com");
  }

  String _getPlatformDisplayName(String url) {
    final lower = url.toLowerCase();
    if (lower.contains("tiktok.com")) return "TikTok";
    if (lower.contains("instagram.com")) return "Instagram";
    if (lower.contains("facebook.com")) return "Facebook";
    if (lower.contains("threads.net")) return "Threads";
    if (lower.contains("twitter.com") || lower.contains("x.com")) return "X (Twitter)";
    if (lower.contains("snapchat.com")) return "Snapchat";
    if (lower.contains("pinterest.com")) return "Pinterest";
    if (lower.contains("vimeo.com")) return "Vimeo";
    return "This website";
  }

  @override
  void initState() {
    super.initState();

    final state = ref.read(BrowserURl);
    final url = state["url"] ?? "";
    searchbar.text = url;

    browserController = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..enableZoom(true);

    // Apply platform-specific performance optimizations if on Android
    final platformController = browserController.platform;
    if (platformController is AndroidWebViewController) {
      platformController.setMediaPlaybackRequiresUserGesture(true);
    }

    browserController
      ..setOnScrollPositionChange((ScrollPositionChange change) {
        try {
          androidLog('📜 [WEBVIEW_SCROLL] x: ${change.x.toInt()}, y: ${change.y.toInt()}');
        } catch (_) {}
      })
      ..setNavigationDelegate(
        NavigationDelegate(
          onProgress: (int progress) {
            try {
              if (progress % 25 == 0 || progress == 100) {
                androidLog('⏳ [WEBVIEW_PROGRESS] Loading: $progress%');
              }
            } catch (_) {}
          },
          onPageStarted: (url) {
            try {
              androidLog('🌐🚀 [WEBVIEW_PAGE_STARTED] URL: $url');
              if (!mounted) return;
              setState(() {
                _isLoading = true;
              });
            } catch (e) {
              androidLog('❌ [WEBVIEW_PAGE_STARTED_ERROR] $e');
            }
          },
          onWebResourceError: (WebResourceError error) {
            try {
              final isMainFrame = error.isForMainFrame ?? true;
              androidLog(
                '⚠️ [WEBVIEW_RESOURCE_ERROR] isMainFrame: $isMainFrame, code: ${error.errorCode}, type: ${error.errorType}, desc: ${error.description}, url: ${error.url}',
              );

              if (!mounted) return;
              setState(() {
                _isLoading = false;
              });

              // Check if failure affects the main webpage (not subresource/ad iframe)
              if (isMainFrame) {
                final currentUrl = ref.read(BrowserURl)["url"] ?? "";
                final platformName = _getPlatformDisplayName(currentUrl);
                final isRestrictedPlatform = _isRestrictedPlatform(currentUrl);

                final isNetworkBlocked =
                    error.description.contains("ERR_NAME_NOT_RESOLVED") ||
                    error.description.contains("ERR_CONNECTION_REFUSED") ||
                    error.description.contains("ERR_CONNECTION_TIMED_OUT") ||
                    error.description.contains("ERR_ADDRESS_UNREACHABLE") ||
                    error.description.contains("ERR_BLOCKED_BY_RESPONSE") ||
                    error.errorType == WebResourceErrorType.hostLookup ||
                    error.errorType == WebResourceErrorType.connect ||
                    error.errorType == WebResourceErrorType.timeout;

                if (isRestrictedPlatform && isNetworkBlocked) {
                  showToast(
                    "$platformName may be blocked or restricted by your network provider in your region.",
                  );
                }
              }
            } catch (e) {
              androidLog('❌ [WEBVIEW_ERROR_HANDLING_FAILED] $e');
            }
          },
          onPageFinished: (url) async {
            try {
              androidLog('✅ [WEBVIEW_PAGE_FINISHED] URL: $url');
              if (!mounted) return;
              setState(() {
                _isLoading = false;
              });

              // Ensure webpage can scroll and has padding above floating buttons
              try {
                await browserController.runJavaScript('''
                  (function() {
                    try {
                      var style = document.getElementById('vd-scroll-fix');
                      if (!style) {
                        style = document.createElement('style');
                        style.id = 'vd-scroll-fix';
                        style.innerHTML = 'html, body { overflow-y: auto !important; -webkit-overflow-scrolling: touch !important; } body { padding-bottom: 90px !important; }';
                        (document.head || document.documentElement).appendChild(style);
                      }
                    } catch(e) {}
                  })();
                ''');
              } catch (e) {
                // Ignore JS errors on unsupported schemes
              }

              // If navigation was triggered internally, ignore provider update
              if (_isInternalNavigation) {
                androidLog('🔄 [WEBVIEW_INTERNAL_NAV] Ignored provider sync');
                _isInternalNavigation = false;
                return;
              }

              final currentState = ref.read(BrowserURl);

              if (currentState["url"] != url) {
                androidLog('📌 [WEBVIEW_URL_SYNC] Syncing URL to provider: $url');
                ref.read(BrowserURl.notifier).setUrlFromWeb(url);
                searchbar.text = url;
              }
              ref.read(tabsProvider.notifier).updateUrl(url);
            } catch (e) {
              androidLog('❌ [WEBVIEW_PAGE_FINISHED_ERROR] $e');
            }
          },
          onNavigationRequest: (request) {
            try {
              final url = request.url;
              androidLog('🧭 [WEBVIEW_NAV_REQUEST] Target: $url, isMainFrame: ${request.isMainFrame}');

              if (url.startsWith("intent://")) {
                androidLog('🛑 [WEBVIEW_NAV_BLOCKED] Blocked intent:// scheme: $url');
                return NavigationDecision.prevent;
              }
              // Facebook share reel link
              if (url.contains("facebook.com/share/r/")) {
                final uri = Uri.parse(url);
                final segments = uri.pathSegments;

                if (segments.length >= 3) {
                  final reelId = segments.last;
                  final reelUrl = "https://www.facebook.com/reel/$reelId";
                  androidLog('🔀 [WEBVIEW_NAV_REDIRECT] Redirecting FB share link to: $reelUrl');
                  browserController.loadRequest(Uri.parse(reelUrl));
                  return NavigationDecision.prevent;
                }
              }

              // Block fb:// scheme
              if (url.startsWith("fb://")) {
                androidLog('🛑 [WEBVIEW_NAV_BLOCKED] Blocked fb:// scheme: $url');
                return NavigationDecision.prevent;
              }
              androidLog('🟢 [WEBVIEW_NAV_ALLOWED] Allowed navigation to: $url');
              return NavigationDecision.navigate;
            } catch (e) {
              androidLog('❌ [WEBVIEW_NAV_REQUEST_ERROR] $e');
              return NavigationDecision.navigate;
            }
          },
        ),
      );
    browserController.loadRequest(Uri.parse(url));

    /// ✅ LISTEN FOR PROVIDER URL CHANGE
    ref.listenManual<Map<String, String>>(BrowserURl, (prev, next) async {
      final prevUrl = prev?["url"] ?? "";
      final nextUrl = next["url"] ?? "";
      if (nextUrl.isEmpty || nextUrl == prevUrl) return;
      final currentWebViewUrl = await browserController.currentUrl();
      if (nextUrl == currentWebViewUrl) return;
      _isInternalNavigation = true;
      browserController.loadRequest(Uri.parse(nextUrl));
      searchbar.text = nextUrl;
    });
    widget.onControllerReady?.call(browserController);
  }

  @override
  void dispose() {
    searchbar.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // final state = ref.watch(BrowserURl);
    // final platform = state["platform"] ?? "";
    // final url = state["url"] ?? "";
    final Platform =
        ref.watch(BrowserURl.select((state) => state["platform"])) ?? "";

    final url = ref.watch(BrowserURl.select((state) => state["url"])) ?? "";
    return Scaffold(
      body: SafeArea(
        child: Form(
          key: _formKey,
          child: Container(
            width: double.infinity,
            child: Column(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                  decoration: const BoxDecoration(
                    color: Colors.white,
                    border: Border(
                      bottom: BorderSide(color: Color(0xFFEEEEEE), width: 1),
                    ),
                  ),
                  child: Row(
                    children: [
                      InkWell(
                        borderRadius: BorderRadius.circular(20),
                        onTap: () async {
                          AdsVariable.onShowAds(
                            context,
                            place: 'VD_Browser_Back',
                            onComplete: () async {
                              // 1. Check if the internal WebView has pages to go back to
                              if (await browserController.canGoBack()) {
                                await browserController.goBack();
                              } else {
                                // 2. If no history exists, exit the browser view
                                ref.read(browserViewProvider.notifier).closeBrowser();
                              }
                            },
                          );
                        },
                        child: Padding(
                          padding: const EdgeInsets.all(5),
                          child: Image.asset(
                            'assets/icons/back.png',
                            width: 20,
                            height: 20,
                          ),
                        ),
                      ),
                      const SizedBox(width: 2),
                      InkWell(
                        customBorder: const CircleBorder(),
                        onTap: () {
                          ref
                              .read(tabsProvider.notifier)
                              .updateUrl("https://www.google.com", title: "Google");
                          ref.read(BrowserURl.notifier).goHome();
                        },
                        child: Padding(
                          padding: const EdgeInsets.all(5),
                          child: Image.asset(
                            'assets/icons/Home_s.png',
                            width: 19,
                            height: 19,
                          ),
                        ),
                      ),
                      const SizedBox(width: 5),
                      Expanded(
                        child: SizedBox(
                          height: 38,
                          child: TextFormField(
                            controller: searchbar,
                            style: const TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.w400,
                              color: Colors.black87,
                            ),
                            onTapOutside: (_) {
                              FocusManager.instance.primaryFocus?.unfocus();
                            },
                            textInputAction: TextInputAction.search,
                            onFieldSubmitted: (value) {
                              FocusScope.of(context).unfocus(); // Close keyboard
                              ref.read(tabsProvider.notifier).updateUrl(value);
                              ref.read(BrowserURl.notifier).submit(value);
                            },
                            cursorColor: const Color(0xFFFE7338),
                            decoration: InputDecoration(
                              hintText: "Search or enter URL",
                              hintStyle: TextStyle(
                                fontSize: 13,
                                color: Colors.grey.shade400,
                              ),
                              isDense: true,
                              prefixIcon: IconButton(
                                padding: EdgeInsets.zero,
                                constraints: const BoxConstraints(
                                  minHeight: 18,
                                  minWidth: 26,
                                ),
                                onPressed: () {
                                  FocusScope.of(context).unfocus();
                                  ref
                                      .read(BrowserURl.notifier)
                                      .submit(searchbar.text);
                                },
                                icon: Image.asset(
                                  "assets/icons/search.png",
                                  width: 14,
                                  height: 14,
                                  color: Colors.grey.shade500,
                                ),
                              ),
                              prefixIconConstraints: const BoxConstraints(
                                minHeight: 18,
                                minWidth: 26,
                              ),
                              suffixIcon: IconButton(
                                padding: EdgeInsets.zero,
                                constraints: const BoxConstraints(
                                  minHeight: 18,
                                  minWidth: 28,
                                ),
                                onPressed: () {
                                  browserController.reload();
                                },
                                icon: Image.asset(
                                  'assets/icons/refreash.png',
                                  width: 15,
                                  height: 15,
                                  color: Colors.grey.shade600,
                                ),
                              ),
                              suffixIconConstraints: const BoxConstraints(
                                minHeight: 18,
                                minWidth: 28,
                              ),
                              filled: true,
                              fillColor: const Color(0xFFF1F3F5),
                              contentPadding: const EdgeInsets.symmetric(
                                vertical: 0,
                                horizontal: 6,
                              ),
                              border: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(20),
                                borderSide: BorderSide.none,
                              ),
                              focusedBorder: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(20),
                                borderSide: const BorderSide(
                                  color: Color(0xFFFE7338),
                                  width: 1.5,
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 4),
                      TabBadgeButton(
                        margin: const EdgeInsets.symmetric(horizontal: 2),
                        onTap: () {
                          showModalBottomSheet(
                            context: context,
                            isScrollControlled: true,
                            backgroundColor: Colors.transparent,
                            builder: (ctx) => const TabsBottomSheet(),
                          );
                        },
                      ),
                      const SizedBox(width: 2),
                      InkWell(
                        customBorder: const CircleBorder(),
                        child: const Padding(
                          padding: EdgeInsets.all(4),
                          child: Icon(Icons.more_vert, size: 22, color: Colors.black87),
                        ),
                      onTap: () async {
                        final selected = await showMenu<String>(
                          context: context,
                          color: const Color(0xFFF2F2F2),
                          // light grey background
                          elevation: 6,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(16),
                          ),
                          position: const RelativeRect.fromLTRB(1000, 80, 0, 0),
                          items: [
                            PopupMenuItem(
                              value: "share",
                              height: 44,
                              child: SizedBox(
                                width: 170,
                                child: Row(
                                  children: [
                                    Image.asset(
                                      "assets/icons/Share App.png",
                                      width: 20,
                                      height: 20,
                                    ),
                                    const SizedBox(width: 12),
                                    Text(
                                      'share'.tr,
                                      style: const TextStyle(
                                        fontSize: 16,
                                        fontWeight: FontWeight.w500,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                            PopupMenuItem(
                              value: "copy",
                              height: 44,
                              child: SizedBox(
                                width: 170,
                                child: Row(
                                  children: [
                                    Image.asset(
                                      "assets/icons/copy_url.png",
                                      width: 20,
                                      height: 20,
                                    ),
                                    const SizedBox(width: 12),
                                    Text(
                                      'copy_url'.tr,
                                      style: const TextStyle(
                                        fontSize: 16,
                                        fontWeight: FontWeight.w500,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                            PopupMenuItem(
                              value: "bookmarkAdd",
                              height: 44,
                              child: SizedBox(
                                width: 170,
                                child: Row(
                                  children: [
                                    Image.asset(
                                      "assets/icons/bookmark_add.png",
                                      width: 20,
                                      height: 20,
                                    ),
                                    const SizedBox(width: 12),
                                    Text(
                                      'add_bookmark'.tr,
                                      style: const TextStyle(
                                        fontSize: 16,
                                        fontWeight: FontWeight.w500,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                            PopupMenuItem(
                              value: "bookmarks",
                              height: 44,
                              child: SizedBox(
                                width: 170,
                                child: Row(
                                  children: [
                                    Image.asset(
                                      "assets/icons/bookmark_view.png",
                                      width: 20,
                                      height: 20,
                                    ),
                                    const SizedBox(width: 12),
                                    Text(
                                      'show_bookmark'.tr,
                                      style: const TextStyle(
                                        fontSize: 16,
                                        fontWeight: FontWeight.w500,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ],
                        );
                        // ✅ Handle action AFTER menu closes
                        if (selected == "share") {
                          Share.share(url);
                        } else if (selected == "copy") {
                          {
                            Clipboard.setData(ClipboardData(text: url));
                            showToast('copied_to_clipboard'.tr);
                          }
                        } else if (selected == "bookmarkAdd") {
                          final url = await browserController.currentUrl();
                          if (url != null && url.isNotEmpty) {
                            await ref
                                .read(bookMarksProvider.notifier)
                                .addBookMark(url);
                            showToast('bookmark_added_successfully'.tr);
                          }
                        } else if (selected == "bookmarks") {
                          Navigator.push(
                            context,
                            MaterialPageRoute(builder: (context) => BookMark()),
                          );
                        }
                        ;
                      },
                    ),
                  ],
                ),
              ),
              Expanded(
                  child: Stack(
                    children: [
                      WebViewWidget(controller: browserController),
                      if (_isLoading)
                        Container(
                          color: Colors.white,
                          child: Center(
                            child: Lottie.asset(
                              "assets/images/Loader (1).json",
                              height: 200,
                              width: 200,
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
      floatingActionButton: (Platform == "youtube" && !AdsVariable.isYouTubeEnabled)
          ? null
          : SizedBox(
              width: 56,
              height: 56,
              child: Stack(
                clipBehavior: Clip.none,
                children: [
                  FloatingActionButton(
                    elevation: 5,
                    backgroundColor: const Color(0xFFFF7134),
                    shape: const CircleBorder(),
                    onPressed: () async {
                      final url = await browserController.currentUrl();
                      print("====>current url $url");

                      final platform = Platform;
                      print("===>platform $platform");

                      final effectivePlatform = (platform.isNotEmpty && platform != "unknown")
                          ? platform
                          : PlatformDetector.detectPlatform(url ?? "");

                      if (effectivePlatform == "youtube" && !AdsVariable.isYouTubeEnabled) {
                        showToast('youtube_download_restricted'.tr);
                        return;
                      }

                      // Check URL validity
                      final isDirectPostUrl = PlatformDetector.isValidVideoUrl(
                        url ?? "unknown",
                        effectivePlatform,
                      );

                      final targetUrl = url ?? "";

                      if (targetUrl.isEmpty || targetUrl == "about:blank") {
                        showToast('please_play_or_open_post'.tr);
                        return;
                      }

                      ref.read(BrowserURl.notifier).setUrlFromWeb(targetUrl);
                      print("====> Triggering download for URL: $targetUrl");

                      final resolvedPlatform = ref.read(BrowserURl)["platform"] ?? effectivePlatform;
                      final taskId = ref.read(downloadTaskProvider.notifier).createFetchingTask(
                        pageUrl: targetUrl,
                        platform: resolvedPlatform,
                      );

                      _isLoadingScreenOpen = true;
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => LoadingChartWidget(
                            taskId: taskId,
                            onContinueInBackground: () {
                              _isLoadingScreenOpen = false;
                              if (Navigator.canPop(context)) {
                                Navigator.pop(context);
                              }
                            },
                          ),
                        ),
                      ).then((_) {
                        _isLoadingScreenOpen = false;
                      });

                      startDownload(
                        taskId: taskId,
                        explicitTargetUrl: targetUrl,
                        platform: resolvedPlatform,
                      );
                    },
                    child: Image.asset(
                      "assets/icons/download_.png",
                      width: 24,
                      height: 24,
                      color: Colors.white,
                    ),
                  ),
                ],
              ),
            ),
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
    );
  }
}
