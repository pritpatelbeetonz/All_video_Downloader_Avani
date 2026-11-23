import 'package:flutter/services.dart';
import 'package:flutter_screenutil/flutter_screenutil.dart';
import 'package:get/get.dart';
import 'package:video_downloader/import_all.dart';
import 'package:video_downloader/for_ads/ads/ads_splash_utils.dart';
import 'package:video_downloader/for_ads/ads/ads_load_util.dart';
import 'package:video_downloader/for_ads/ads/ads_variable.dart';
import 'package:video_downloader/for_ads/ng/ng_ads.dart';
import '../providers/SearchToBrowser.dart';
import '../providers/Browser_data_provider.dart';
import '../widgets/tabs_bottom_sheet.dart';
import 'BrowserWithBack.dart';
import 'download_page.dart';

import 'package:video_downloader/for_ads/utils/app_constants.dart';
import 'package:video_downloader/main.dart' show routeObserver;
import 'package:video_downloader/features/home/providers/bottom_nav_provider.dart';

class SearchPage extends ConsumerStatefulWidget {
  const SearchPage({super.key});

  @override
  ConsumerState<SearchPage> createState() => SearchPageState();
}

class SearchPageState extends ConsumerState<SearchPage> with RouteAware {
  TextEditingController searchbarController = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  String value = "";
  bool checkFocus = true;
  NativeAd? _homeBigNativeAd;
  bool _isLoadingBigNativeAd = false;
  DateTime? _homeAdShownAt;

  /// AdMob's minimum refresh interval; a displayed ad younger than this is kept.
  static const Duration _homeAdMinRefresh = Duration(seconds: 60);

  @override
  void initState() {
    super.initState();
    // Listen for tab switches
    ref.listenManual(bottomNavProvider, (previous, next) {
      if (next == 0 && previous != 0) {
        showLog("🏠🔄 [Home Native Ad] Tab switched back to Home -> Refreshing Big Native Ad");
        _refreshHomeBigNativeAd();
      } else if (next != 0 && previous == 0) {
        showLog("🏃‍♂️💨 [Home Native Ad] User switched away from Home (tab $next) -> Detaching in-flight ad so it caches");
        AppBigNativeAdPool.detachScreen('SearchPage');
      }
    });
    _refreshHomeBigNativeAd();
    AdsVariable.configUpdatedNotifier.addListener(_onAdsConfigUpdated);
    // Downloader page became visible (swiped to, or launcher foregrounded).
    AppBigNativeAdPool.downloaderVisible.addListener(_onDownloaderVisibilityChanged);
  }

  void _onDownloaderVisibilityChanged() {
    if (!mounted) return;
    if (AppBigNativeAdPool.downloaderVisible.value) {
      _refreshHomeBigNativeAd();
    } else {
      // Page hidden: detach so an in-flight ad is cached for the next visit, not rendered unseen.
      AppBigNativeAdPool.detachScreen('SearchPage');
    }
  }

  void _onAdsConfigUpdated() {
    if (mounted && _homeBigNativeAd == null && AdsLoadUtil.isValidAdId(AdsVariable.prAppBigNative)) {
      showLog("⚡ [Home Native Ad] AdsConfig updated from Launcher -> Triggering native ad refresh!");
      _refreshHomeBigNativeAd();
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final modalRoute = ModalRoute.of(context);
    if (modalRoute != null) {
      routeObserver.subscribe(this, modalRoute);
    }
  }

  @override
  void didPushNext() {
    super.didPushNext();
    if (ref.read(bottomNavProvider) != 0) return;
    showLog("🚪➡️ [Home Native Ad] Navigated to another screen -> Detaching in-flight ad so it caches");
    AppBigNativeAdPool.detachScreen('SearchPage');
  }

  @override
  void didPopNext() {
    super.didPopNext();
    final currentTab = ref.read(bottomNavProvider);
    if (currentTab != 0) {
      showLog("🚪 [Home Native Ad] Route popped, but active tab is $currentTab (not Home) -> Skipping refresh");
      return;
    }
    showLog("⬅️🏠 [Home Native Ad] Returned to Home from another route -> Refreshing Big Native Ad");
    _refreshHomeBigNativeAd();
  }

  void _refreshHomeBigNativeAd() {
    final currentTab = ref.read(bottomNavProvider);
    if (currentTab != 0) {
      showLog("🚫 [Home Native Ad] Skipping refresh - active tab is $currentTab (not Home)");
      return;
    }
    if (AdsVariable.isPurchase ||
        !AdsLoadUtil.isValidAdId(AdsVariable.prAppBigNative)) {
      showLog("🚫 [Home Native Ad] Skipping refresh - purchase or invalid adId");
      return;
    }
    final shownAt = _homeAdShownAt;
    if (_homeBigNativeAd != null &&
        shownAt != null &&
        DateTime.now().difference(shownAt) < _homeAdMinRefresh) {
      showLog("🧊 [Home Native Ad] Displayed ad is fresh enough (<60s) -> Skipping refresh");
      return;
    }
    // Only show shimmer if no ad is currently displayed
    if (_homeBigNativeAd == null) {
      showLog("⏳✨ [Home Native Ad] Cold start (no ad on screen) -> Showing Shimmer and loading");
      setState(() {
        _isLoadingBigNativeAd = true;
      });
    } else {
      showLog("👁️🔄 [Home Native Ad] Old ad already displayed -> Keeping old ad visible while fetching fresh ad in background");
    }

    AppBigNativeAdPool.loadForScreen(
      screenTag: 'SearchPage',
      onLoaded: (newAd) {
        if (!mounted ||
            ref.read(bottomNavProvider) != 0 ||
            !AppBigNativeAdPool.downloaderVisible.value) {
          showLog("📦💾 [Home Native Ad] Screen unmounted or switched away -> Caching fresh ad into pool");
          AppBigNativeAdPool.cacheUnshownAd(newAd);
          return;
        }
        showLog("🎉🖼️ [Home Native Ad] Fresh ad arrived! -> Smoothly swapping into container and disposing old ad");
        final oldAd = _homeBigNativeAd;
        setState(() {
          _homeBigNativeAd = newAd;
          _homeAdShownAt = DateTime.now();
          _isLoadingBigNativeAd = false;
        });
        if (oldAd != null && oldAd != newAd) {
          // After the swap animation: seen -> disposed, never seen -> back into the pool.
          Future.delayed(const Duration(milliseconds: 600), () {
            AppBigNativeAdPool.releaseAd(oldAd, reason: 'replaced_unseen');
          });
        }
      },
      onFailed: () {
        showLog("⚠️❌ [Home Native Ad] Fresh ad load failed -> Keeping previous ad / dismissing shimmer");
        if (!mounted) return;
        setState(() {
          _isLoadingBigNativeAd = false;
        });
      },
    );
  }

  @override
  void dispose() {
    AdsVariable.configUpdatedNotifier.removeListener(_onAdsConfigUpdated);
    AppBigNativeAdPool.downloaderVisible.removeListener(_onDownloaderVisibilityChanged);
    routeObserver.unsubscribe(this);
    AppBigNativeAdPool.detachScreen('SearchPage');
    final adToDispose = _homeBigNativeAd;
    _homeBigNativeAd = null;
    if (adToDispose != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        AppBigNativeAdPool.releaseAd(adToDispose, reason: 'disposed_unseen');
      });
    }
    searchbarController.dispose();
    super.dispose();
  }

  // copy to clipboard function
  void copyToClipboard(BuildContext context, String text) {
    Clipboard.setData(ClipboardData(text: text));
    showToast('copied_to_clipboard'.tr);
  }

  /// Pastes the clipboard text into the link field.
  Future<void> _pasteFromClipboard() async {
    final data = await Clipboard.getData('text/plain');
    final text = data?.text?.trim() ?? '';
    if (text.isEmpty) {
      showToast('Clipboard is empty');
      return;
    }
    searchbarController.text = text;
    searchbarController.selection = TextSelection.fromPosition(
      TextPosition(offset: text.length),
    );
  }

  /// Pastes (if the field is empty) and then starts a direct download.
  Future<void> _pasteAndDownload() async {
    if (searchbarController.text.trim().isEmpty) {
      await _pasteFromClipboard();
    }
    _startDownload(searchbarController.text);
  }

  /// Direct link -> download: detect platform, validate, then open the
  /// DownloadPage (it auto-extracts + downloads). No in-app browser.
  void _startDownload(String rawText) {
    final text = rawText.trim();
    if (text.isEmpty) {
      showToast('required_link_or_search'.tr);
      return;
    }

    // Resolve the link + platform through the shared provider.
    ref.read(BrowserURl.notifier).submit(text);
    final platform = ref.read(BrowserURl)['platform'] ?? 'unknown';

    // Only accept a recognised, supported video link.
    if (platform == 'unknown') {
      showToast('Please paste a valid video link');
      return;
    }
    if (platform == 'youtube' && !AdsVariable.isYouTubeEnabled) {
      showToast('This link is not supported');
      return;
    }

    FocusScope.of(context).unfocus();
    AdsVariable.onShowAds(
      context,
      place: 'VD_Url_Open',
      onComplete: () {
        Get.to(() => const DownloadPage());
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final showBrowser = ref.watch(browserViewProvider);
    return showBrowser
        ? const BrowserWrapper()
        : Scaffold(
            appBar: AppBar(
              automaticallyImplyLeading: false,
              title: Text(
                'video_downloader'.tr,
                style: const TextStyle(
                  fontWeight: FontWeight.w600,
                  color: Colors.black,
                ),
              ),
              actions: [
                TabBadgeButton(
                  onTap: () {
                    showModalBottomSheet(
                      context: context,
                      isScrollControlled: true,
                      backgroundColor: Colors.transparent,
                      builder: (ctx) => const TabsBottomSheet(),
                    );
                  },
                ),
                SizedBox(width: 16.w),
              ],
            ),
            body: SafeArea(
              child: SingleChildScrollView(
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 10,
                ),
                child: Form(
                  key: _formKey,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SizedBox(height: 10),

                      /// HERO
                      Text(
                        'YOUR MEDIA, YOUR WAY',
                        style: GoogleFonts.ibmPlexSans(
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 1.1,
                          color: const Color(0xFFFE7338),
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        'Save it for later.',
                        style: GoogleFonts.ibmPlexSans(
                          fontSize: 26,
                          fontWeight: FontWeight.w700,
                          color: Colors.black87,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        'Keep the clips and moments you love, close at hand.',
                        style: GoogleFonts.ibmPlexSans(
                          fontSize: 14,
                          fontWeight: FontWeight.w400,
                          color: const Color(0xFF64748B),
                        ),
                      ),

                      const SizedBox(height: 22),

                      /// PASTE A LINK
                      Text(
                        'PASTE A LINK',
                        style: GoogleFonts.ibmPlexSans(
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 0.8,
                          color: const Color(0xFF94A3B8),
                        ),
                      ),
                      const SizedBox(height: 10),

                      /// LINK FIELD + PASTE BUTTON
                      Container(
                        height: 58,
                        padding: const EdgeInsets.only(
                          left: 14,
                          right: 6,
                          top: 4,
                          bottom: 4,
                        ),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(
                            color: const Color(0xFFE2E8F0),
                            width: 1.5,
                          ),
                          boxShadow: [
                            BoxShadow(
                              color: Colors.black.withValues(alpha: 0.04),
                              blurRadius: 10,
                              offset: const Offset(0, 3),
                            ),
                          ],
                        ),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.center,
                          children: [
                            const Icon(
                              Icons.link_rounded,
                              color: Color(0xFF64748B),
                              size: 24,
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: TextField(
                                controller: searchbarController,
                                textAlignVertical: TextAlignVertical.center,
                                onTapOutside: (_) {
                                  FocusManager.instance.primaryFocus?.unfocus();
                                },
                                style: GoogleFonts.ibmPlexSans(
                                  fontWeight: FontWeight.w500,
                                  fontSize: 15,
                                  color: Colors.black87,
                                ),
                                keyboardType: TextInputType.url,
                                textInputAction: TextInputAction.go,
                                autocorrect: false,
                                enableSuggestions: false,
                                maxLines: 1,
                                decoration: InputDecoration(
                                  border: InputBorder.none,
                                  isDense: true,
                                  contentPadding: EdgeInsets.zero,
                                  hintText: 'https://...',
                                  hintStyle: GoogleFonts.ibmPlexSans(
                                    fontWeight: FontWeight.w400,
                                    fontSize: 15,
                                    color: const Color(0xFF94A3B8),
                                  ),
                                ),
                                onSubmitted: (_) =>
                                    _startDownload(searchbarController.text),
                              ),
                            ),
                            const SizedBox(width: 4),
                            Material(
                              color: const Color(0xFFFFF1EA),
                              borderRadius: BorderRadius.circular(12),
                              child: InkWell(
                                onTap: _pasteFromClipboard,
                                borderRadius: BorderRadius.circular(12),
                                child: Padding(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 16,
                                    vertical: 12,
                                  ),
                                  child: Text(
                                    'Paste',
                                    style: GoogleFonts.ibmPlexSans(
                                      fontWeight: FontWeight.w600,
                                      fontSize: 14,
                                      color: const Color(0xFFFE7338),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),

                      const SizedBox(height: 14),

                      /// PASTE AND DOWNLOAD
                      SizedBox(
                        width: double.infinity,
                        height: 54,
                        child: Material(
                          color: const Color(0xFFFE7338),
                          borderRadius: BorderRadius.circular(16),
                          child: InkWell(
                            onTap: _pasteAndDownload,
                            borderRadius: BorderRadius.circular(16),
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                const Icon(
                                  Icons.download_rounded,
                                  color: Colors.white,
                                  size: 22,
                                ),
                                const SizedBox(width: 10),
                                Text(
                                  'Paste and download',
                                  style: GoogleFonts.ibmPlexSans(
                                    fontWeight: FontWeight.w700,
                                    fontSize: 16,
                                    color: Colors.white,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),

                      const SizedBox(height: 12),

                      /// DISCLAIMER
                      Text(
                        'Download only content you own or have the right to save.',
                        style: GoogleFonts.ibmPlexSans(
                          fontSize: 12,
                          fontWeight: FontWeight.w400,
                          color: const Color(0xFF94A3B8),
                        ),
                      ),

                      const SizedBox(height: 20),

                      /// APP BIG NATIVE AD (POOL)
                      AppBigNativeAdWidget(
                        showNativeAd: _homeBigNativeAd,
                        isLoading: _isLoadingBigNativeAd,
                      ),

                      const SizedBox(height: 20),
                    ],
                  ),
                ),
              ),
            ),
          );
  }
}
