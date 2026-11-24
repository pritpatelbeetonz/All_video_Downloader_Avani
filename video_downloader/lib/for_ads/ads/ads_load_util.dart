
import 'dart:async';
import 'dart:developer';
import 'package:flutter/material.dart';
import 'package:get/get_core/src/get_main.dart';
import 'package:get/get_navigation/src/extension_navigation.dart';
import 'package:get/get_rx/src/rx_types/rx_types.dart';
import 'package:get/get_state_manager/src/rx_flutter/rx_obx_widget.dart';
import 'package:get/get_state_manager/src/simple/get_controllers.dart';
import 'package:video_downloader/for_ads/ng/ng_ads.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:video_downloader/firebase_analysis.dart';
import 'package:video_downloader/for_ads/ads/ads_variable.dart';
import 'package:video_downloader/for_ads/ads/app_open_ad.dart';
import 'package:video_downloader/for_ads/ads/life_cycle.dart';
import 'package:video_downloader/for_ads/utils/app_constants.dart';
import 'package:video_downloader/for_ads/utils/shimmer.dart';

import '../widgets/loading_screen.dart';


class AdsLoadUtil extends GetxController {
  late SharedPreferences prefs;
  late AppLifecycleReactor appLifecycleReactor;

  /// Validates whether an ad unit ID is in correct format (starts with ca-app-pub- and contains /)
  /// and is not a placeholder like "11", "1", "null", or empty.
  static bool isValidAdId(String? adId) {
    if (adId == null) return false;
    final trimmed = adId.trim();
    if (trimmed.isEmpty ||
        trimmed == "11" ||
        trimmed == "1" ||
        trimmed == "null" ||
        trimmed.toLowerCase() == "undefined") {
      return false;
    }
    // Must be a valid AdMob ad unit ID format
    return trimmed.startsWith("ca-app-pub-") && trimmed.contains("/");
  }

  /// --------- load open ads -----------------------------------------------------
  loadAppOpenSplash(Function() navigateScreen) async {
    if (!isValidAdId(AdsVariable.appOpenAdsIOS)) {
      showLog("Skipping loadAppOpenSplash - invalid or '11' adId: ${AdsVariable.appOpenAdsIOS}");
      navigateScreen();
      return;
    }
    AppOpenAdManager appOpenAdManager = AppOpenAdManager()
      ..loadAd(AdsVariable.appOpenAdsIOS);
    appLifecycleReactor = AppLifecycleReactor(
      appOpenAdManager: appOpenAdManager,
    );
    AppLifecycleReactor(
      appOpenAdManager: appOpenAdManager,
    ).listenToAppStateChanges();
  }

  loadAppOpen() async {
    if (!isValidAdId(AdsVariable.appOpenAdsIOS)) {
      showLog("Skipping loadAppOpen - invalid or '11' adId: ${AdsVariable.appOpenAdsIOS}");
      return;
    }
    AppOpenAdManager appOpenAdManager = AppOpenAdManager()
      ..loadAd(AdsVariable.appOpenAdsIOS);
    appLifecycleReactor = AppLifecycleReactor(
      appOpenAdManager: appOpenAdManager,
    );
    AppLifecycleReactor(
      appOpenAdManager: appOpenAdManager,
    ).listenToAppStateChanges();
  }

  ///---------- load and show open ad in splash
  void loadAndShowOpenAd(Function onDismissed, String adId) {
    if (!isValidAdId(adId)) {
      showLog("Skipping loadAndShowOpenAd - invalid or '11' adId: $adId");
      onDismissed();
      return;
    }
    AppOpenAd.load(
      adUnitId: adId,
      request: const AdRequest(),
      adLoadCallback: AppOpenAdLoadCallback(
        onAdLoaded: (ad) async {
          FirebaseAnalyticsService.logEvent(eventName: 'LOAD_OPEN_AD');
          // await loadPreLoadAds();
          log(
            "Ad Loaded:=====================================================================",
          );
          ad.show();
          ad.fullScreenContentCallback = FullScreenContentCallback(
            onAdShowedFullScreenContent: (ad) {
              log('Ad showed full screen content');
              // showToast("Splash Intertial Add show");
            },
            onAdFailedToShowFullScreenContent: (ad, error) {
              log('$ad onAdFailedToShowFullScreenContent=======:- $error');

              Future.delayed(const Duration(seconds: 5), () {
                //Navigator.of(context).pop();
                onDismissed();
              });
            },
            onAdDismissedFullScreenContent: (ad) {
              Future.delayed(const Duration(seconds: 3), () {
                onDismissed();
              });
              AppOpenAdManager adManager = AppOpenAdManager();
              AppLifecycleReactor life = AppLifecycleReactor(
                appOpenAdManager: adManager,
              );
              life.listenToAppStateChanges(shouldShow: true);

              ///CHANGES TO LOAD PRE LOAD AFTER SPLASH DISMISSED
              AdsLoadUtil.loadPreInterstitialAd(
                adId: AdsVariable.interPreLoadIOS,
              );
              log('$ad onAdDismissedFullScreenContent========:-');
            },
          );
        },
        onAdFailedToLoad: (error) {
          Future.delayed(const Duration(seconds: 5), () {
            onDismissed();
          });
          log(
            "Ad Not Loaded:=====================================================================",
          );
          log(error.toString());
        },
      ),
    );
  }

  /// ------ Intro App Open Ad (prIntroAppOpen) -------------------------
  static bool isIntroAppOpenLoading = false;

  static void loadPreloadIntroAppOpen({Function()? onLoaded, Function()? onFailed}) {
    onFailed?.call();
  }

  static void showIntroAppOpen({
    required BuildContext context,
    required Function onDismissed,
  }) {
    onDismissed();
  }

  /// ------ Splash screen inter load & show --------------------------------------------
  InterstitialAd? splashInterAd;

  /// -------------------- Splash inter loading ------------------------
  loadInterSplash(Function() navigateScreen, String adUnitId) async {
    if (!isValidAdId(adUnitId)) {
      showLog("Skipping loadInterSplash - invalid or '11' adId: $adUnitId");
      navigateScreen();
      return;
    }
    prefs = await SharedPreferences.getInstance();
    showLog('>> SHOW INTER CALL <<');
    showLog('AdsVariable.appOpenSplashIOS >>${AdsVariable.interSplashIOS}');

    if (!AdsVariable.isPurchase) {
      InterstitialAd.load(
        adUnitId: adUnitId,
        request: const AdRequest(),
        adLoadCallback: InterstitialAdLoadCallback(
          // Called when an ad is successfully received.
          onAdLoaded: (ad) async {
            ///CHANGES IT FROM onAdShowedFullScreenContent To onAdLoaded
            FirebaseAnalyticsService.logEvent(
              eventName: 'LOAD_SPLASH_INERTIAL_AD',
            );
            // await loadPreLoadAds();

            ad.fullScreenContentCallback = FullScreenContentCallback(
              // Called when the ad showed the full screen content.
              onAdShowedFullScreenContent: (ad) async {
                showLog("onAdShowedFullScreenContent loadInterSplash");
              },
              // Called when an impression occurs on the ad.
              onAdImpression: (ad) async {
                showLog("onAdImpression loadInterSplash");
                Future.delayed(const Duration(milliseconds: 500)).then((value) {
                  navigateScreen();
                });
              },

              // Called when the ad failed to show full screen content.
              //
              onAdFailedToShowFullScreenContent: (ad, err) async {
                // Dispose the ad here to free resources.
                ad.dispose();
                // await loadPreLoadAds();
                showLog("onAdFailedToShowFullScreenContent loadInterSplash");
              },
              // Called when the ad dismissed full screen content.
              onAdDismissedFullScreenContent: (ad) async {
                AppOpenAdManager adManager = AppOpenAdManager();
                AppLifecycleReactor life = AppLifecycleReactor(
                  appOpenAdManager: adManager,
                );
                life.listenToAppStateChanges(shouldShow: true);
                // Dispose the ad here to free resources.

                ///CHANGES TO LOAD PRE LOAD AFTER SPLASH DISMISSED
                //AdsLoadUtil.loadPreInterstitialAd(adId: AdsVariable.interPreLoadIOS);
                ad.dispose();
                showLog("onAdDismissedFullScreenContent loadInterSplash");
              },
              // Called when a click is recorded for an ad.
              onAdClicked: (ad) {
                showLog("onAdClicked loadInterSplash");
              },
            );

            showLog('$ad loaded.loadInterSplash ');
            // Keep a reference to the ad so you can show it later.

            splashInterAd = ad;
            splashInterAd!.show();
          },

          // Called when an ad request failed.
          onAdFailedToLoad: (LoadAdError error) async {
            showLog('InterstitialAd failed to load loadInterSplash: $error');
            // await loadPreLoadAds();
            navigateScreen();
          },
        ),
      );
    }
  }

  ///---------------------- If Preload is not loaded then instantly load ad and show
  static InterstitialAd? _interstitialAd;
  static String interstitialId = "";
  static bool isAdLoaded = false;

  /// True while a preload request is in flight (single-flight: never a 2nd request).
  static bool _isPreInterLoading = false;
  /// When the cached preloaded ad finished loading (for the expiry check).
  static DateTime? _preInterLoadedAt;
  /// A screen waiting on the in-flight preload: (ad, loadErrorCode). ad == null -> load failed.
  static void Function(InterstitialAd? ad, int? errorCode)? _pendingPreInterShow;

  /// Interstitials expire ~1h after load; older ones may fail to show or not count.
  static const Duration _preInterMaxAge = Duration(minutes: 55);
  /// Max wait (with loader) on an in-flight preload when an ad is due.
  static const Duration _preInterWaitTimeout = Duration(seconds: 5);
  static const int _noFillErrorCode = 3;

  static bool get _isPreInterStale {
    final loadedAt = _preInterLoadedAt;
    return loadedAt != null && DateTime.now().difference(loadedAt) > _preInterMaxAge;
  }

  static int? get _preInterAgeMin {
    final loadedAt = _preInterLoadedAt;
    return loadedAt == null ? null : DateTime.now().difference(loadedAt).inMinutes;
  }

  /// A loaded pre-interstitial was thrown away without being shown.
  /// reason: stale | disposed_unused
  static void _logPreInterWasted(String reason) {
    showLog("🗑️ [PreInter] WASTED reason=$reason ageMin=$_preInterAgeMin");
    FirebaseAnalyticsService.logEvent(
      eventName: 'pre_inter_wasted',
      parameters: {
        'reason': reason,
        'age_min': ?_preInterAgeMin,
      },
    );
  }

  /// An interstitial was due but none was shown.
  /// reason: timeout | no_fill | load_failed | show_failed
  /// source: preloaded | waited_preload | on_spot
  static void _logPreInterNotShown(String reason, String source, String place, {int? errorCode}) {
    showLog("📉 [PreInter] NOT SHOWN reason=$reason source=$source place=$place code=$errorCode");
    FirebaseAnalyticsService.logEvent(
      eventName: 'pre_inter_not_shown',
      parameters: {
        'reason': reason,
        'source': source,
        'place': place,
        'error_code': ?errorCode,
      },
    );
  }

  static String _loadFailReason(int? code) => code == _noFillErrorCode ? 'no_fill' : 'load_failed';

  /// Drops the preloaded interstitial (disposing it once) so a stale or
  /// already-shown ad is never reused. Pass [wastedReason] when an unshown ad is discarded.
  static void _clearPreloadedInterstitial({String? wastedReason}) {
    final old = _interstitialAd;
    if (old != null && wastedReason != null) _logPreInterWasted(wastedReason);
    _interstitialAd = null;
    isAdLoaded = false;
    _preInterLoadedAt = null;
    old?.dispose();
  }

  /// Single-flight preload: no-op while a request is in flight or a fresh ad is
  /// already cached. A stale cached ad is dropped and replaced.
  static void loadPreInterstitialAd({required String adId}) {
    if (!isValidAdId(adId)) {
      showLog("Skipping loadPreInterstitialAd - invalid or '11' adId: $adId");
      _clearPreloadedInterstitial(wastedReason: 'disposed_unused');
      return;
    }
    if (_isPreInterLoading) {
      showLog("Skipping loadPreInterstitialAd - request already in flight");
      return;
    }
    if (isAdLoaded && _interstitialAd != null) {
      if (!_isPreInterStale) {
        showLog("Skipping loadPreInterstitialAd - fresh ad already cached");
        return;
      }
      _clearPreloadedInterstitial(wastedReason: 'stale');
    }
    interstitialId = adId;
    _isPreInterLoading = true;
    InterstitialAd.load(
      adUnitId: adId,
      request: const AdRequest(),
      adLoadCallback: InterstitialAdLoadCallback(
        onAdLoaded: (ad) {
          _isPreInterLoading = false;
          FirebaseAnalyticsService.logEvent(eventName: 'SHOW_PRE_INTERSTITIAL');
          ad.onPaidEvent = (ad, valueMicros, precision, currencyCode) {
            FirebaseAnalyticsService.logAdImpression(
              adUnitId: ad.adUnitId,
              placement: 'video_downloader_inter',
              format: 'interstitial',
              revenue: valueMicros / 1000000.0,
              currency: currencyCode,
              precision: precision.index,
            );
          };
          showLog("Pre Inter Loaded");
          final pending = _pendingPreInterShow;
          if (pending != null) {
            // A screen is waiting on this exact request -> hand it over directly.
            _pendingPreInterShow = null;
            pending(ad, null);
          } else {
            _interstitialAd = ad;
            isAdLoaded = true;
            _preInterLoadedAt = DateTime.now();
          }
        },
        onAdFailedToLoad: (error) {
          _isPreInterLoading = false;
          _interstitialAd = null;
          isAdLoaded = false;
          _preInterLoadedAt = null;
          showLog("Pre Inter Failed $error");
          final pending = _pendingPreInterShow;
          if (pending != null) {
            _pendingPreInterShow = null;
            pending(null, error.code);
          }
        },
      ),
    );
  }

  /// Shows an already-loaded interstitial; reloads the next preload on dismiss/fail.
  static void _showLoadedInterstitial(
    InterstitialAd ad, {
    required String place,
    required String source,
    required Function onDismissed,
  }) {
    // Attach compulsory revenue tracking for this placement
    ad.onPaidEvent = (ad, valueMicros, precision, currencyCode) {
      FirebaseAnalyticsService.logAdImpression(
        adUnitId: ad.adUnitId,
        placement: place,
        format: 'interstitial',
        revenue: valueMicros / 1000000.0,
        currency: currencyCode,
        precision: precision.index,
      );
    };

    // Callbacks must be set before show() so dismissal is never missed.
    ad.fullScreenContentCallback = FullScreenContentCallback(
      onAdImpression: (ad) {
        FirebaseAnalyticsService.logEvent(eventName: 'SHOW_INITIAL_AD');
        showLog("onAdImpression---> true [$place]");
      },
      onAdDismissedFullScreenContent: (ad) {
        showLog("onAdDismissedFullScreenContent---> true");
        onDismissed();
        ad.dispose();
        loadPreInterstitialAd(adId: interstitialId);
      },
      onAdFailedToShowFullScreenContent: (ad, error) {
        showLog("onAdFailedToShowFullScreenContent---> Error $error");
        _logPreInterNotShown('show_failed', source, place, errorCode: error.code);
        ad.dispose();
        loadPreInterstitialAd(adId: interstitialId);
        onDismissed();
      },
    );
    ad.show();
  }

  static void showInterstitial({
    String place = "VD_Quick_App",
    required Function onDismissed,
  }) {
    if (AdsVariable.isPurchase ||
        !isValidAdId(AdsVariable.interPreLoadIOS) ||
        AdsVariable.interPreLoadIOS.startsWith('v', 0)) {
      showLog("Skipping showInterstitial - purchase or invalid adId: ${AdsVariable.interPreLoadIOS}");
      onDismissed();
      return;
    }

    // (B) Never show an expired preload: drop it and load on the spot instead.
    if (isAdLoaded && _interstitialAd != null && _isPreInterStale) {
      showLog("Pre Inter is stale (${_preInterAgeMin}m) -> discarding, loading on the spot [$place]");
      _clearPreloadedInterstitial(wastedReason: 'stale');
    }

    final preloadedAd = _interstitialAd;
    if (isAdLoaded && preloadedAd != null) {
      showLog("IT IS PRE LOADED [$place]");
      // Take ownership so the ad can't be shown twice or reused after dispose.
      _interstitialAd = null;
      isAdLoaded = false;
      _preInterLoadedAt = null;
      loadingScreen.show();
      // Delay showing the ad for 500 milliseconds
      Future.delayed(const Duration(milliseconds: 500), () {
        loadingScreen.hide();
        _showLoadedInterstitial(preloadedAd, place: place, source: 'preloaded', onDismissed: onDismissed);
      });
      return;
    }

    // (C) A preload is already in flight: don't fire a 2nd request — wait for it with the loader.
    if (_isPreInterLoading) {
      showLog("Pre Inter in flight -> waiting up to ${_preInterWaitTimeout.inSeconds}s [$place]");
      loadingScreen.show();
      var finished = false;
      final timer = Timer(_preInterWaitTimeout, () {
        if (finished) return;
        finished = true;
        // Let the late ad land in the cache for the next slot instead of being thrown away.
        _pendingPreInterShow = null;
        loadingScreen.hide();
        _logPreInterNotShown('timeout', 'waited_preload', place);
        onDismissed();
      });
      _pendingPreInterShow = (ad, errorCode) {
        if (finished) {
          // Timed out already: keep the ad for the next slot.
          if (ad != null) {
            _interstitialAd = ad;
            isAdLoaded = true;
            _preInterLoadedAt = DateTime.now();
          }
          return;
        }
        finished = true;
        timer.cancel();
        loadingScreen.hide();
        if (ad == null) {
          _logPreInterNotShown(_loadFailReason(errorCode), 'waited_preload', place, errorCode: errorCode);
          onDismissed();
          return;
        }
        _showLoadedInterstitial(ad, place: place, source: 'waited_preload', onDismissed: onDismissed);
      };
      return;
    }

    loadAndShow(
      adId: AdsVariable.interPreLoadIOS,
      place: place,
      onDismissed: onDismissed,
    );
  }

  /// Loads a fresh interstitial on the spot and shows it. Only reached when no
  /// preloaded ad is ready and none is in flight, so it never calls back into [showInterstitial].
  static void loadAndShow({
    required String adId,
    String place = "VD_Quick_App",
    required Function onDismissed,
  }) {
    if (!isValidAdId(adId)) {
      showLog("Skipping loadAndShow - invalid or '11' adId: $adId");
      onDismissed();
      return;
    }
    showLog("IT IS LOAD AND SHOW [$place]");
    log("ads id $adId");
    // Any leftover (failed/stale) preload is discarded before loading anew.
    _clearPreloadedInterstitial(wastedReason: 'disposed_unused');
    interstitialId = adId;
    loadingScreen.show();

    InterstitialAd.load(
      adUnitId: adId,
      request: const AdRequest(),
      adLoadCallback: InterstitialAdLoadCallback(
        onAdLoaded: (ad) {
          FirebaseAnalyticsService.logEvent(eventName: 'LOADED_INITIAL_AD');
          ad.onPaidEvent = (ad, valueMicros, precision, currencyCode) {
            FirebaseAnalyticsService.logAdImpression(
              adUnitId: ad.adUnitId,
              placement: place,
              format: 'interstitial',
              revenue: valueMicros / 1000000.0,
              currency: currencyCode,
              precision: precision.index,
            );
          };
          showLog(ad.toString());
          showLog(adId);

          loadingScreen.hide();
          ad.fullScreenContentCallback = FullScreenContentCallback(
            onAdImpression: (ad) {
              showLog("onAdImpression [$place]");
            },
            onAdDismissedFullScreenContent: (ad) {
              onDismissed();
              ad.dispose();
              log("Ad Reloaded");
              loadPreInterstitialAd(adId: AdsVariable.interPreLoadIOS);
            },
            onAdFailedToShowFullScreenContent: (ad, error) {
              _logPreInterNotShown('show_failed', 'on_spot', place, errorCode: error.code);
              ad.dispose();
              log("Ad Reloaded");
              loadPreInterstitialAd(adId: AdsVariable.interPreLoadIOS);
              onDismissed();
            },
          );
          ad.show();
        },
        onAdFailedToLoad: (error) {
          showLog(error.toString());
          _logPreInterNotShown(_loadFailReason(error.code), 'on_spot', place, errorCode: error.code);
          loadingScreen.hide();
          onDismissed();
        },
      ),
    );
  }

  /// --------------------- Native ads load ------------------------------

  /// FOR OTHER ADS TAKE STATIC VARIABLES AS MUCH AS YOU WANT TO SHOW ADS
  /// EXAMPLE:
  /// static NativeAd? homeNativeAd (Declare it in AdsVariable File (Recommended))
  /// now wherever I want to preload this homeNativeAd
  /// I will pass
  /// AdsVariable.homeNativeAd = await AdsLoadUtil().loadNative(AdsVariable.dtmd_home_nativeAd, false);
}

///Reels Ads
class ReelNativeAdView extends StatelessWidget {
  final int index;
  final int freq;
  final List<NativeAd> ads;

  const ReelNativeAdView({
    super.key,
    required this.index,
    required this.freq,
    required this.ads,
  });

  @override
  Widget build(BuildContext context) {
    if (ads.isEmpty) {
      return const SizedBox();
    }

    // 🔥 Calculate ad position
    int adPosition = index ~/ (freq + 1);
    int adIndex = adPosition % ads.length;

    final ad = ads[adIndex];

    // ✅ PRINT DEBUG
    debugPrint("🟡 AD DEBUG → UI Index: $index");
    debugPrint("🟢 AD DEBUG → Ad Position: $adPosition");
    debugPrint("🔵 AD DEBUG → Ad Index (used): $adIndex");

    return SizedBox(
      height: Get.height,
      width: Get.width,
      child: AdWidget(ad: ad),
    );
  }
}
///BannerAds
class BannerAdsWidget extends StatelessWidget {
  final BannerAdState adState;
  final AdSize adSize;
  final Color? backgroundColor;

  const BannerAdsWidget({
    super.key,
    required this.adState,
    this.adSize = AdSize.banner,
    this.backgroundColor,
  });

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      if (AdsVariable.isPurchase) {
        return SizedBox.shrink();
      }

      if (adState.isLoaded.value && adState.ad != null) {
        return Container(
          alignment: Alignment.center,
          width: adState.ad!.size.width.toDouble(),
          height: adState.ad!.size.height.toDouble(),
          color: backgroundColor,
          child: AdWidget(ad: adState.ad!),
        );
      }

      if (adState.isFailed.value) {
        return const SizedBox(height: 0);
      }

      return _getBannerShimmer(adSize);
    });
  }

  Widget _getBannerShimmer(AdSize size) {
    return const Text('Loading Ads');
  }
}
class BannerAdState {
  BannerAd? ad;
  final RxBool isLoaded = false.obs;
  final RxBool isFailed = false.obs;

  void dispose() {
    isLoaded.value = false;
    isFailed.value = false;
    ad?.dispose();
    ad = null;
  }

  @override
  String toString() {
    return 'BannerAdState{ad: $ad, isLoaded: $isLoaded, isFailed: $isFailed}';
  }
}

/// Universal Ad Pool for prAppBigNative:
/// - On-the-spot only, NO background preloading
/// - Checks cache first: if present, shows immediately
/// - Strictly at most 1 conversation in-flight with AdMob
/// - If ad is in-flight and user moves away / enters another place:
///   does not destroy it; caches it! Any new request joins and waits for in-flight ad.
/// - Never requests while the downloader is off-screen (pre-warmed engine or other
///   launcher page): the request is parked and fired when the launcher reports
///   the downloader page visible (see setDownloaderVisible).
class AppBigNativeAdPool {
  static NativeAd? _cachedAd;
  static DateTime? _cachedAt;
  static bool _isInFlight = false;
  static String? _activeScreenTag;
  static Function(NativeAd)? _onAdLoadedCallback;
  static Function()? _onAdFailedCallback;
  static RxBool isFailedToLoad = false.obs;

  /// Native ads expire after ~1 hour; drop cached ones a little before that.
  static const Duration _cacheTtl = Duration(minutes: 55);

  /// A request made while not visible, replayed on resume (latest screen wins).
  static ({String screenTag, Function(NativeAd) onLoaded, Function() onFailed})? _pending;

  /// Set by the launcher over `nativeChannel`: true only while the downloader
  /// page is on screen. Flutter's own lifecycle reports `resumed` for the
  /// pre-warmed engine even with no view attached, so it can't be used here.
  static final ValueNotifier<bool> downloaderVisible = ValueNotifier(false);

  static bool get _isVisible => downloaderVisible.value;

  static void setDownloaderVisible(bool visible) {
    if (downloaderVisible.value == visible) return;
    showLog("👁️ [AppBigNativeAdPool] Downloader visible = $visible");
    downloaderVisible.value = visible;
    if (visible) _onVisible();
  }

  static String get _activePlace =>
      (_activeScreenTag?.toLowerCase().contains('loading') == true)
          ? 'VD_Loading'
          : 'VD_Search';

  static void _onVisible() {
    final pending = _pending;
    if (pending == null) return;
    _pending = null;
    showLog(
        "▶️👀 [AppBigNativeAdPool] Downloader visible -> Firing deferred request for ${pending.screenTag}");
    loadForScreen(
      screenTag: pending.screenTag,
      onLoaded: pending.onLoaded,
      onFailed: pending.onFailed,
    );
  }

  // ---- Impression / age tracking + waste measurement ----

  /// Ads AdMob recorded an impression for (set from the listener's onAdImpression).
  static final Expando<bool> _impressed = Expando('bigNativeImpressed');
  /// When each ad finished loading (TTL is measured from load, not from caching).
  static final Expando<DateTime> _loadedAt = Expando('bigNativeLoadedAt');
  static const String _prefUnseenCachedAt = 'big_native_unseen_cached_loaded_at';

  static bool hasImpression(NativeAd ad) => _impressed[ad] == true;

  static int? _ageMin(NativeAd ad) {
    final t = _loadedAt[ad];
    return t == null ? null : DateTime.now().difference(t).inMinutes;
  }

  /// A loaded big native ad was thrown away unseen.
  /// reason: expired | cache_overwritten | disposed_unseen | replaced_unseen | process_ended
  static void _logWasted(String reason, {int? ageMin}) {
    showLog("🗑️ [AppBigNativeAdPool] WASTED reason=$reason ageMin=$ageMin");
    FirebaseAnalyticsService.logEvent(
      eventName: 'big_native_wasted',
      parameters: {
        'reason': reason,
        'age_min': ?ageMin,
      },
    );
  }

  /// Persist "an unseen ad sits in the cache" so a process death can be counted next start.
  static Future<void> _persistUnseenCached(DateTime? loadedAt) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      if (loadedAt == null) {
        await prefs.remove(_prefUnseenCachedAt);
      } else {
        await prefs.setInt(_prefUnseenCachedAt, loadedAt.millisecondsSinceEpoch);
      }
    } catch (_) {}
  }

  /// Once per process (call at startup): if the previous process died holding an unseen
  /// cached ad, that ad was never shown -> log it as process_ended.
  static Future<void> checkLeftoverFromLastProcess() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final loadedAtMs = prefs.getInt(_prefUnseenCachedAt);
      if (loadedAtMs != null) {
        final ageMin = DateTime.now()
            .difference(DateTime.fromMillisecondsSinceEpoch(loadedAtMs))
            .inMinutes;
        _logWasted('process_ended', ageMin: ageMin);
        await prefs.remove(_prefUnseenCachedAt);
      }
    } catch (_) {}
  }

  /// The only place an ad enters the cache. One slot: keep the FRESHER ad, dispose + log the other.
  static void _storeInCache(NativeAd ad) {
    final old = _cachedAd;
    if (old != null && !identical(old, ad)) {
      final oldLoaded = _loadedAt[old] ?? _cachedAt ?? DateTime.now();
      final newLoaded = _loadedAt[ad] ?? DateTime.now();
      if (oldLoaded.isAfter(newLoaded)) {
        // Cached one is fresher: keep it, drop the incoming one.
        _logWasted('cache_overwritten', ageMin: _ageMin(ad));
        ad.dispose();
        return;
      }
      _logWasted('cache_overwritten', ageMin: _ageMin(old));
      old.dispose();
    }
    _cachedAd = ad;
    _cachedAt = _loadedAt[ad] ?? DateTime.now();
    _persistUnseenCached(_cachedAt);
  }

  /// Returns the cached ad if it is still fresh; expired ones are disposed.
  static NativeAd? _takeCachedAd() {
    final ad = _cachedAd;
    if (ad == null) return null;
    final cachedAt = _cachedAt;
    _cachedAd = null;
    _cachedAt = null;
    _persistUnseenCached(null);
    if (cachedAt != null && DateTime.now().difference(cachedAt) > _cacheTtl) {
      showLog("⌛🗑️ [AppBigNativeAdPool] Cached ad expired -> Disposing it");
      _logWasted('expired', ageMin: _ageMin(ad));
      ad.dispose();
      return null;
    }
    return ad;
  }

  /// A screen is dropping an ad it displayed (replaced by a newer one, or the screen closed).
  /// Seen -> dispose. Never seen (e.g. rendered while the downloader page was hidden, or the
  /// screen closed right after it arrived) -> back into the cache for the next screen.
  static void releaseAd(NativeAd ad, {required String reason}) {
    if (hasImpression(ad)) {
      ad.dispose();
      return;
    }
    final loadedAt = _loadedAt[ad];
    if (loadedAt != null && DateTime.now().difference(loadedAt) > _cacheTtl) {
      _logWasted('expired', ageMin: _ageMin(ad));
      ad.dispose();
      return;
    }
    showLog("♻️ [AppBigNativeAdPool] Unseen ad released ($reason) -> back into cache");
    // If the slot is taken, _storeInCache keeps the fresher ad and logs the other as overwritten.
    _storeInCache(ad);
  }

  /// [allowNetwork] false = deliver a cached ad or fail at once, never call AdMob.
  static void loadForScreen({
    required String screenTag,
    required Function(NativeAd) onLoaded,
    required Function() onFailed,
    bool allowNetwork = true,
  }) async {
    if (AdsVariable.isPurchase ||
        !AdsLoadUtil.isValidAdId(AdsVariable.prAppBigNative)) {
      showLog(
          "🚫 [AppBigNativeAdPool] Skipping loadForScreen - purchase or invalid adId");
      onFailed();
      return;
    }

    // 1. Check if an ad is already cached: show it immediately!
    final cached = _takeCachedAd();
    if (cached != null) {
      showLog(
          "⚡🟢📦 [AppBigNativeAdPool] CACHE HIT! Found cached ad -> Delivering immediately to $screenTag (Zero Wait, No Shimmer!)");
      isFailedToLoad.value = false;
      onLoaded(cached);
      return;
    }

    if (!allowNetwork) {
      showLog("📭 [AppBigNativeAdPool] Cache-only request from $screenTag -> No cached ad");
      onFailed();
      return;
    }

    // Off-screen (pre-warmed engine / other launcher page): park the request
    // instead of loading an ad nobody can see.
    if (!_isInFlight && !_isVisible) {
      _pending = (screenTag: screenTag, onLoaded: onLoaded, onFailed: onFailed);
      showLog(
          "⏸️🙈 [AppBigNativeAdPool] Deferred request for $screenTag (downloader not visible)");
      return;
    }

    // 2. Register current screen callback
    _activeScreenTag = screenTag;
    _onAdLoadedCallback = onLoaded;
    _onAdFailedCallback = onFailed;

    // 3. If an AdMob request is already in-flight, DO NOT fire another request!
    // Strictly maintain 1 conversation with AdMob at a time. The new screen waits for it.
    if (_isInFlight) {
      showLog(
          "⏳🤝 [AppBigNativeAdPool] IN-FLIGHT JOIN! AdMob request already in progress -> Screen $screenTag attached & waiting");
      return;
    }

    // 4. No cache & not in-flight: fire a single on-the-spot request.
    // Mark in-flight before awaiting so a concurrent call joins instead of
    // firing a second request.
    _isInFlight = true;
    if (!await AdsVariable.isInternetConnected()) {
      showLog("🌐❌ [AppBigNativeAdPool] Skipping loadForScreen - no internet");
      _isInFlight = false;
      isFailedToLoad.value = true;
      final cb = _onAdFailedCallback;
      _activeScreenTag = null;
      _onAdLoadedCallback = null;
      _onAdFailedCallback = null;
      cb?.call();
      return;
    }
    isFailedToLoad.value = false;
    showLog(
        "🚀📡 [AppBigNativeAdPool] IN-FLIGHT START -> Firing on-the-spot AdMob request for $screenTag");

    NativeAd? onSpotAd;
    onSpotAd = NativeAd(
      adUnitId: AdsVariable.prAppBigNative,
      factoryId: 'bigNativeAds',
      listener: NativeAdListener(
        onAdImpression: (ad) {
          _impressed[ad] = true;
        },
        onPaidEvent: (ad, valueMicros, precision, currencyCode) {
          FirebaseAnalyticsService.logAdImpression(
            adUnitId: ad.adUnitId,
            placement: _activePlace,
            format: 'native',
            revenue: valueMicros / 1000000.0,
            currency: currencyCode,
            precision: precision.index,
          );
        },
        onAdLoaded: (loadedAd) {
          FirebaseAnalyticsService.logEvent(
            eventName: 'LOAD_APP_BIG_NATIVE_ONSPOT',
          );
          _isInFlight = false;
          isFailedToLoad.value = false;
          final ad = loadedAd as NativeAd;
          _loadedAt[ad] = DateTime.now();

          if (!_isVisible) {
            // Downloader page is hidden (user swiped to another launcher page): rendering now
            // would put the ad where nobody sees it. Cache it; the screen re-requests (cache hit)
            // when the page becomes visible again.
            showLog(
                "🙈💾 [AppBigNativeAdPool] AD LOADED while downloader hidden -> Caching instead of delivering");
            final failCb = _onAdFailedCallback;
            _activeScreenTag = null;
            _onAdLoadedCallback = null;
            _onAdFailedCallback = null;
            _storeInCache(ad);
            failCb?.call();
          } else if (_activeScreenTag != null && _onAdLoadedCallback != null) {
            showLog(
                "🎯✅ [AppBigNativeAdPool] AD LOADED! -> Delivering directly to active screen: $_activeScreenTag");
            final cb = _onAdLoadedCallback!;
            _activeScreenTag = null;
            _onAdLoadedCallback = null;
            _onAdFailedCallback = null;
            cb(ad);
          } else {
            // Screen moved away or detached while in-flight: DO NOT DESTROY, CACHE IT!
            showLog(
                "📥💾✨ [AppBigNativeAdPool] AD LOADED BUT SCREEN DETACHED! -> Safely CACHING ad into pool for next visit!");
            _storeInCache(ad);
          }
        },
        onAdFailedToLoad: (failedAd, error) {
          showLog("💥❌ [AppBigNativeAdPool] AD LOAD FAILED -> $error");
          _isInFlight = false;
          isFailedToLoad.value = true;
          failedAd.dispose();
          if (_onAdFailedCallback != null) {
            final cb = _onAdFailedCallback!;
            _activeScreenTag = null;
            _onAdLoadedCallback = null;
            _onAdFailedCallback = null;
            cb();
          }
        },
      ),
      request: const AdRequest(),
    );

    try {
      await onSpotAd.load();
    } catch (e) {
      showLog("💥❌ [AppBigNativeAdPool] Exception in on-spot load: $e");
      _isInFlight = false;
      isFailedToLoad.value = true;
      if (_onAdFailedCallback != null) {
        final cb = _onAdFailedCallback!;
        _activeScreenTag = null;
        _onAdLoadedCallback = null;
        _onAdFailedCallback = null;
        cb();
      }
    }
  }

  /// Called when a screen unmounts / moves away.
  /// If an ad is in-flight, detaches so the ad gets cached upon arrival instead of delivered to dead widget or destroyed.
  static void detachScreen(String screenTag) {
    if (_pending?.screenTag == screenTag) {
      showLog("🔌⏸️ [AppBigNativeAdPool] Screen $screenTag detached -> Dropping deferred request");
      _pending = null;
    }
    if (_activeScreenTag == screenTag) {
      showLog(
          "🔌👋 [AppBigNativeAdPool] Screen $screenTag detached from in-flight request");
      _activeScreenTag = null;
      _onAdLoadedCallback = null;
      _onAdFailedCallback = null;
    }
  }

  /// If an ad was loaded but not displayed, cache it instead of disposing it.
  static void cacheUnshownAd(NativeAd ad) {
    showLog("📥📦 [AppBigNativeAdPool] Storing unshown ad into cache");
    _storeInCache(ad);
  }
}

/// Big Native Ad Widget backed by the AppBigNativeAdPool with distinct orange border
class AppBigNativeAdWidget extends StatelessWidget {
  final NativeAd? showNativeAd;
  final bool isLoading;

  const AppBigNativeAdWidget({
    super.key,
    required this.showNativeAd,
    required this.isLoading,
  });

  @override
  Widget build(BuildContext context) {
    if (AdsVariable.isPurchase ||
        !AdsLoadUtil.isValidAdId(AdsVariable.prAppBigNative)) {
      return const SizedBox();
    }

    return Obx(() {
      if (AppBigNativeAdPool.isFailedToLoad.value && showNativeAd == null) {
        return const SizedBox();
      }
      if (showNativeAd != null) {
        final width = MediaQuery.of(context).size.width;
        // The native is drawn by ads_native_big.xml (same card as the Language screen). Its height
        // scales with screen width (sdp ≈ width/360): about 290dp on a 384dp-wide phone, i.e. 0.76×
        // width, plus room for a 2-line body. 0.80 keeps the Install button fully visible.
        return SizedBox(
          width: width,
          height: width * 0.80,
          child: AdWidget(
            key: GlobalObjectKey(showNativeAd!),
            ad: showNativeAd!,
          ),
        );
      } else if (isLoading) {
        return const ShimmerBigNative();
      } else {
        return const SizedBox();
      }
    });
  }
}
