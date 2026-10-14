import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_screenutil/flutter_screenutil.dart';
import 'package:get/get.dart';
import 'package:video_downloader/import_all.dart';
import 'package:video_downloader/for_ads/ads/ads_splash_utils.dart';
import 'package:video_downloader/for_ads/ads/ads_load_util.dart';
import 'package:video_downloader/for_ads/ads/ads_variable.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';
import '../providers/SearchToBrowser.dart';
import '../providers/Browser_data_provider.dart';
import '../providers/Tab_provider.dart';
import '../widgets/tabs_bottom_sheet.dart';
import 'BrowserWithBack.dart';
import 'Loadingscreen.dart';

import 'package:video_downloader/for_ads/utils/app_constants.dart';
import 'package:video_downloader/main.dart' show routeObserver;
import 'package:video_downloader/features/home/providers/bottom_nav_provider.dart';

class _QuickAccessItem {
  final String title;
  final String icon;
  final String url;

  const _QuickAccessItem({
    required this.title,
    required this.icon,
    required this.url,
  });
}

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

  static const List<_QuickAccessItem> _quickAccessItems = [
    _QuickAccessItem(
      title: 'Instagram',
      icon: 'assets/images/insta.png',
      url: 'https://www.instagram.com',
    ),
    _QuickAccessItem(
      title: 'Facebook',
      icon: 'assets/images/fb.png',
      url: 'https://www.facebook.com',
    ),
    _QuickAccessItem(
      title: 'X (Twitter)',
      icon: 'assets/images/x.png',
      url: 'https://x.com',
    ),
    _QuickAccessItem(
      title: 'TikTok',
      icon: 'assets/images/tiktok.png',
      url: 'https://www.tiktok.com',
    ),
    _QuickAccessItem(
      title: 'Threads',
      icon: 'assets/images/thread.png',
      url: 'https://www.threads.net',
    ),
    _QuickAccessItem(
      title: 'Vimeo',
      icon: 'assets/images/vimeo.png',
      url: 'https://vimeo.com',
    ),
    _QuickAccessItem(
      title: 'Snapchat',
      icon: 'assets/images/snap.png',
      url: 'https://www.snapchat.com',
    ),
    _QuickAccessItem(
      title: 'Pinterest',
      icon: 'assets/images/pin.png',
      url: 'https://www.pinterest.com',
    ),
  ];

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
        if (!mounted || ref.read(bottomNavProvider) != 0) {
          showLog("📦💾 [Home Native Ad] Screen unmounted or switched away -> Caching fresh ad into pool");
          AppBigNativeAdPool.cacheUnshownAd(newAd);
          return;
        }
        showLog("🎉🖼️ [Home Native Ad] Fresh ad arrived! -> Smoothly swapping into container and disposing old ad");
        final oldAd = _homeBigNativeAd;
        setState(() {
          _homeBigNativeAd = newAd;
          _isLoadingBigNativeAd = false;
        });
        if (oldAd != null && oldAd != newAd) {
          Future.delayed(const Duration(milliseconds: 600), () {
            oldAd.dispose();
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
    routeObserver.unsubscribe(this);
    AppBigNativeAdPool.detachScreen('SearchPage');
    final adToDispose = _homeBigNativeAd;
    _homeBigNativeAd = null;
    if (adToDispose != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        adToDispose.dispose();
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

  void _openUrl(String targetUrl) {
    AdsVariable.onShowAds(
      context,
      onComplete: () {
        searchbarController.text = targetUrl;
        ref.read(tabsProvider.notifier).updateUrl(targetUrl);
        ref.read(BrowserURl.notifier).submit(targetUrl);
        FocusScope.of(context).unfocus();
        ref.read(browserViewProvider.notifier).openBrowser();
      },
    );
  }

  void _submitSearch() {
    final text = searchbarController.text.trim();
    if (text.isEmpty) {
      showToast('required_link_or_search'.tr);
      return;
    }
    FocusScope.of(context).unfocus();
    ref.read(tabsProvider.notifier).updateUrl(text);
    ref.read(BrowserURl.notifier).submit(text);
    ref.read(browserViewProvider.notifier).openBrowser();
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
                if (kDebugMode)
                  IconButton(
                    icon: const Icon(Icons.bug_report_rounded, color: Color(0xFFFE7338)),
                    tooltip: 'Preview Download Loading Screen',
                    onPressed: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => const LoadingChartWidget(),
                        ),
                      );
                    },
                  ),
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
                SizedBox(width: 16.w,),
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
                    crossAxisAlignment: CrossAxisAlignment.center,
                    children: [
                      const SizedBox(height: 12),
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
                          borderRadius: BorderRadius.circular(35),
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
                          children: [
                            const Icon(
                              Icons.link_rounded,
                              color: Color(0xFF64748B),
                              size: 26,
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: TextField(
                                controller: searchbarController,
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
                                decoration: InputDecoration(
                                  border: InputBorder.none,
                                  isDense: true,
                                  hintText: 'search_or_paste_url'.tr,
                                  hintStyle: GoogleFonts.ibmPlexSans(
                                    fontWeight: FontWeight.w400,
                                    fontSize: 15,
                                    color: const Color(0xFF94A3B8),
                                  ),
                                ),
                                onSubmitted: (_) => _submitSearch(),
                              ),
                            ),
                            const SizedBox(width: 6),
                            Material(
                              color: Colors.transparent,
                              child: InkWell(
                                onTap: _submitSearch,
                                borderRadius: BorderRadius.circular(25),
                                child: Container(
                                  width: 44,
                                  height: 44,
                                  decoration: const BoxDecoration(
                                    shape: BoxShape.circle,
                                    color: Color(0xFFFE7338),
                                  ),
                                  child: const Icon(
                                    Icons.arrow_forward_rounded,
                                    color: Colors.white,
                                    size: 22,
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 18),

                      /// APP BIG NATIVE AD (POOL)
                      AppBigNativeAdWidget(
                        showNativeAd: _homeBigNativeAd,
                        isLoading: _isLoadingBigNativeAd,
                      ),

                      const SizedBox(height: 18),

                      /// QUICK ACCESS SECTION
                      Align(
                        alignment: Alignment.centerLeft,
                        child: Text(
                          'quick_access'.tr,
                          style: GoogleFonts.ibmPlexSans(
                            fontSize: 18,
                            fontWeight: FontWeight.w600,
                            color: Colors.black87,
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                      GridView.builder(
                        shrinkWrap: true,
                        physics: const NeverScrollableScrollPhysics(),
                        itemCount: _quickAccessItems.length,
                        gridDelegate:
                            const SliverGridDelegateWithFixedCrossAxisCount(
                              crossAxisCount: 4,
                              mainAxisSpacing: 6,
                              crossAxisSpacing: 10,
                              childAspectRatio: 1.12,
                            ),
                        itemBuilder: (context, index) {
                          final item = _quickAccessItems[index];
                          return InkWell(
                            borderRadius: BorderRadius.circular(12),
                            onTap: () => _openUrl(item.url),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Container(
                                  width: 42,
                                  height: 42,
                                  decoration: BoxDecoration(
                                    borderRadius: BorderRadius.circular(10),
                                    boxShadow: [
                                      BoxShadow(
                                        color: Colors.black.withValues(
                                          alpha: 0.05,
                                        ),
                                        blurRadius: 4,
                                        offset: const Offset(0, 1.5),
                                      ),
                                    ],
                                  ),
                                  child: ClipRRect(
                                    borderRadius: BorderRadius.circular(10),
                                    child: Image.asset(
                                      item.icon,
                                      width: 42,
                                      height: 42,
                                      fit: BoxFit.contain,
                                    ),
                                  ),
                                ),
                                const SizedBox(height: 5),
                                Text(
                                  item.title,
                                  textAlign: TextAlign.center,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  style: GoogleFonts.ibmPlexSans(
                                    fontSize: 11.5,
                                    fontWeight: FontWeight.w500,
                                    color: const Color(0xFF333333),
                                  ),
                                ),
                              ],
                            ),
                          );
                        },
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
