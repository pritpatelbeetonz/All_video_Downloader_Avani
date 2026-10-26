
import 'dart:async';
import 'dart:developer';
import 'package:flutter/material.dart';
import 'package:flutter_screenutil/flutter_screenutil.dart';
import 'package:get/get_core/src/get_main.dart';
import 'package:get/get_navigation/src/extension_navigation.dart';
import 'package:get/get_rx/src/rx_types/rx_types.dart';
import 'package:get/get_state_manager/src/rx_flutter/rx_obx_widget.dart';
import 'package:get/get_state_manager/src/simple/get_controllers.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';
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

  /// Drops the preloaded interstitial (disposing it once) so a stale or
  /// already-shown ad is never reused.
  static void _clearPreloadedInterstitial() {
    final old = _interstitialAd;
    _interstitialAd = null;
    isAdLoaded = false;
    old?.dispose();
  }

  static loadPreInterstitialAd({required String adId}) {
    if (!isValidAdId(adId)) {
      showLog("Skipping loadPreInterstitialAd - invalid or '11' adId: $adId");
      _clearPreloadedInterstitial();
      return;
    }
    interstitialId = adId;
    _clearPreloadedInterstitial();
    InterstitialAd.load(
      adUnitId: adId,
      request: const AdRequest(),
      adLoadCallback: InterstitialAdLoadCallback(
        onAdLoaded: (ad) {
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
          _interstitialAd = ad;
          isAdLoaded = true;
          showLog("Pre Inter Loaded");
        },
        onAdFailedToLoad: (error) {
          _interstitialAd = null;
          isAdLoaded = false;
          showLog("Pre Inter Failed $error");
        },
      ),
    );
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
    final preloadedAd = _interstitialAd;
    if (isAdLoaded && preloadedAd != null) {
      showLog("IT IS PRE LOADED [$place]");
      // Take ownership so the ad can't be shown twice or reused after dispose.
      _interstitialAd = null;
      isAdLoaded = false;
      loadingScreen.show();
      // Delay showing the ad for 500 milliseconds
      Future.delayed(const Duration(milliseconds: 500), () {
        loadingScreen.hide();

        // Attach compulsory revenue tracking for this placement
        preloadedAd.onPaidEvent = (ad, valueMicros, precision, currencyCode) {
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
        preloadedAd.fullScreenContentCallback = FullScreenContentCallback(
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
            ad.dispose();
            loadPreInterstitialAd(adId: interstitialId);
            onDismissed();
          },
        );
        preloadedAd.show();
      });
    } else {
      loadAndShow(
        adId: AdsVariable.interPreLoadIOS,
        place: place,
        onDismissed: onDismissed,
      );
    }
  }

  /// Loads a fresh interstitial on the spot and shows it. Only reached when no
  /// preloaded ad is ready, so it never calls back into [showInterstitial].
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
    _clearPreloadedInterstitial();
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

  static NativeAd? nativeBigAdSurvey1;
  static NativeAd? nativeBigAdSurvey2;
  static NativeAd? fullNativeAdIntro;
  static NativeAd? smallThirdNativeAdIntro;

  static RxBool isNativeAdFailedToLoadBigSurvey1 = false.obs;
  static RxBool isNativeAdFailedToLoadBigSurvey2 = false.obs;
  static RxBool isFullNativeAdFailedToLoadIntro = false.obs;
  static RxBool isSmallThirdNativeAdFailedToLoadIntro = false.obs;

  static RxBool isBigNativeSurveyAdLoaded1 = false.obs;
  static RxBool isBigNativeSurveyAdLoaded2 = false.obs;
  static RxBool isFullNativeIntroAdLoaded = false.obs;
  static RxBool isSmallThirdNativeIntroAdLoaded = false.obs;

  static RxBool isLanguageBigNative3Loaded = false.obs;
  static RxBool isLanguageBigNative3FailedToLoad = false.obs;
  static bool isLanguageBigNative3InFlight = false;

  Future<NativeAd> loadSurveyBigNative1(
    String adUnitId,
    bool isBigNative,
  ) async {
    showLog("isBigNative---===>$isBigNative");
    showLog(
      "loadSurveyBigNative1 counter---------------------------------------",
    );
    nativeBigAdSurvey1 = NativeAd(
      adUnitId: adUnitId.toString(),
      factoryId: isBigNative ? 'bigNativeAds' : 'fullNativeAds',
      listener: NativeAdListener(
        onPaidEvent: (ad, valueMicros, precision, currencyCode) {
          FirebaseAnalyticsService.logAdImpression(
            adUnitId: ad.adUnitId,
            placement: 'video_downloader_native',
            format: 'native',
            revenue: valueMicros / 1000000.0,
            currency: currencyCode,
            precision: precision.index,
          );
        },
        onAdLoaded: (ad) {
          FirebaseAnalyticsService.logEvent(
            eventName: 'LOAD LANGUAGE BIG NATIVE 1',
          );

          nativeBigAdSurvey1 = ad as NativeAd?;
          isBigNativeSurveyAdLoaded1.value = true;
          showLog('isLoaded loadIntro1Native');
        },
        onAdFailedToLoad: (ad, error) {
          showLog('onAdFailedToLoad Language Native');
          nativeBigAdSurvey1!.dispose();
          isBigNativeSurveyAdLoaded1.value = false;
          isNativeAdFailedToLoadBigSurvey1.value = true;
        },
      ),
      request: const AdRequest(),
    );
    await nativeBigAdSurvey1!.load();
    return nativeBigAdSurvey1!;
  }

  Future<NativeAd> loadSurveyBigNative2(
    String adUnitId,
    bool isBigNative,
  ) async {
    showLog("isBigNative---===>$isBigNative");
    showLog(
      "loadSurveyBigNative2 counter---------------------------------------",
    );
    nativeBigAdSurvey2 = NativeAd(
      adUnitId: adUnitId.toString(),
      factoryId: isBigNative ? 'bigNativeAds' : 'fullNativeAds',
      listener: NativeAdListener(
        onPaidEvent: (ad, valueMicros, precision, currencyCode) {
          FirebaseAnalyticsService.logAdImpression(
            adUnitId: ad.adUnitId,
            placement: 'video_downloader_native',
            format: 'native',
            revenue: valueMicros / 1000000.0,
            currency: currencyCode,
            precision: precision.index,
          );
        },
        onAdLoaded: (ad) {
          FirebaseAnalyticsService.logEvent(
            eventName: 'LOAD LANGUAGE BIG NATIVE 2',
          );

          nativeBigAdSurvey2 = ad as NativeAd?;
          isBigNativeSurveyAdLoaded2.value = true;
          showLog('isLoaded loadIntro1Native');
        },
        onAdFailedToLoad: (ad, error) {
          showLog('onAdFailedToLoad Language Native');
          nativeBigAdSurvey2!.dispose();
          isBigNativeSurveyAdLoaded2.value = false;
          isNativeAdFailedToLoadBigSurvey2.value = true;
        },
      ),
      request: const AdRequest(),
    );
    await nativeBigAdSurvey2!.load();
    return nativeBigAdSurvey2!;
  }

  Future<void> loadLanguageBigNative3({
    bool isBigNative = true,
    Function(NativeAd)? onLoaded,
    Function()? onFailed,
  }) async {
    if (!isValidAdId(AdsVariable.prLanguageBigNative3)) {
      showLog(
          "Skipping loadLanguageBigNative3 - invalid adId: ${AdsVariable.prLanguageBigNative3}");
      isLanguageBigNative3FailedToLoad.value = true;
      onFailed?.call();
      return;
    }
    if (isLanguageBigNative3InFlight) {
      showLog(
          "loadLanguageBigNative3 is already in-flight, skipping duplicate request");
      return;
    }

    isLanguageBigNative3InFlight = true;
    isLanguageBigNative3FailedToLoad.value = false;
    showLog(
        "loadLanguageBigNative3 started with ID: ${AdsVariable.prLanguageBigNative3}");

    NativeAd? newAd;
    newAd = NativeAd(
      adUnitId: AdsVariable.prLanguageBigNative3,
      factoryId: isBigNative ? 'bigNativeAds' : 'fullNativeAds',
      listener: NativeAdListener(
        onPaidEvent: (ad, valueMicros, precision, currencyCode) {
          FirebaseAnalyticsService.logAdImpression(
            adUnitId: ad.adUnitId,
            placement: 'video_downloader_native',
            format: 'native',
            revenue: valueMicros / 1000000.0,
            currency: currencyCode,
            precision: precision.index,
          );
        },
        onAdLoaded: (ad) {
          FirebaseAnalyticsService.logEvent(
            eventName: 'LOAD_LANGUAGE_BIG_NATIVE_3',
          );
          showLog('loadLanguageBigNative3 loaded successfully');
          isLanguageBigNative3InFlight = false;
          isLanguageBigNative3Loaded.value = true;
          final loadedNativeAd = ad as NativeAd;
          AdsVariable.nativeBigAdLanguage3 = loadedNativeAd;
          onLoaded?.call(loadedNativeAd);
        },
        onAdFailedToLoad: (ad, error) {
          showLog('loadLanguageBigNative3 failed to load: $error');
          isLanguageBigNative3InFlight = false;
          if (AdsVariable.nativeBigAdLanguage3 == null) {
            isLanguageBigNative3Loaded.value = false;
            isLanguageBigNative3FailedToLoad.value = true;
          }
          ad.dispose();
          onFailed?.call();
        },
      ),
      request: const AdRequest(),
    );
    try {
      await newAd.load();
    } catch (e) {
      showLog('loadLanguageBigNative3 load exception: $e');
      isLanguageBigNative3InFlight = false;
      if (AdsVariable.nativeBigAdLanguage3 == null) {
        isLanguageBigNative3Loaded.value = false;
        isLanguageBigNative3FailedToLoad.value = true;
      }
      onFailed?.call();
    }
  }

  Future<NativeAd> loadIntroFullNative(
    String adUnitId,
    bool isSmallNative,
  ) async {
    showLog("isSmallNative---===>$isSmallNative");
    showLog(
      "loadIntroFullNative counter---------------------------------------",
    );
    fullNativeAdIntro = NativeAd(
      adUnitId: adUnitId.toString(),
      factoryId: isSmallNative ? 'bigNativeAds' : 'fullNativeAds',
      listener: NativeAdListener(
        onPaidEvent: (ad, valueMicros, precision, currencyCode) {
          FirebaseAnalyticsService.logAdImpression(
            adUnitId: ad.adUnitId,
            placement: 'video_downloader_native',
            format: 'native',
            revenue: valueMicros / 1000000.0,
            currency: currencyCode,
            precision: precision.index,
          );
        },
        onAdLoaded: (ad) {
          FirebaseAnalyticsService.logEvent(eventName: 'FULL_NATIVE_AD_LOAD');
          fullNativeAdIntro = ad as NativeAd?;
          isFullNativeIntroAdLoaded.value = true;
          showLog('isLoaded loadFullNative');

        },
        onAdFailedToLoad: (ad, error) {
          showLog('onAdFailedToLoad loadFullNative');
          fullNativeAdIntro!.dispose();
          isFullNativeIntroAdLoaded.value = false;
          isFullNativeAdFailedToLoadIntro.value = true;
        },
      ),
      request: const AdRequest(),
    );
    await fullNativeAdIntro!.load();
    return fullNativeAdIntro!;
  }

  Future<NativeAd> loadSmallThirdIntroNative(
    String adUnitId,
    bool isSmallNative,
  ) async {
    showLog("isSmallNative---===>$isSmallNative");
    showLog(
      "loadIntroFullNative counter---------------------------------------",
    );
    smallThirdNativeAdIntro = NativeAd(
      adUnitId: adUnitId.toString(),
      factoryId: isSmallNative ? 'bigNativeAds' : 'fullNativeAds',
      listener: NativeAdListener(
        onPaidEvent: (ad, valueMicros, precision, currencyCode) {
          FirebaseAnalyticsService.logAdImpression(
            adUnitId: ad.adUnitId,
            placement: 'video_downloader_native',
            format: 'native',
            revenue: valueMicros / 1000000.0,
            currency: currencyCode,
            precision: precision.index,
          );
        },
        onAdLoaded: (ad) {
          FirebaseAnalyticsService.logEvent(eventName: 'FULL_NATIVE_AD_LOAD');
          smallThirdNativeAdIntro = ad as NativeAd?;
          isSmallThirdNativeIntroAdLoaded.value = true;
          showLog('isLoaded loadFullNative');
        },
        onAdFailedToLoad: (ad, error) {
          showLog('onAdFailedToLoad loadFullNative');
          smallThirdNativeAdIntro!.dispose();
          isSmallThirdNativeIntroAdLoaded.value = false;
          isSmallThirdNativeAdFailedToLoadIntro.value = true;
        },
      ),
      request: const AdRequest(),
    );
    await smallThirdNativeAdIntro!.load();
    return smallThirdNativeAdIntro!;
  }
}

/// Big Native Survey1
class SurveyBigNativeAds1 extends StatefulWidget {
  final bool isBigNative;
  final NativeAd? showNativeAd;
  final RxBool isNativeAdLoaded;
  final RxBool isNativeAdFailedToLoad;

  const SurveyBigNativeAds1({
    super.key,
    required this.isBigNative,
    required this.showNativeAd,
    required this.isNativeAdLoaded,
    required this.isNativeAdFailedToLoad,
  });

  @override
  State<SurveyBigNativeAds1> createState() => _SurveyBigNativeAds1State();
}

class _SurveyBigNativeAds1State extends State<SurveyBigNativeAds1> {
  @override
  Widget build(BuildContext context) {
    showLog(
      "CHECK NULL SurveyBigNativeAds1>> ${widget.isNativeAdLoaded.value}",
    );
    showLog("SurveyBigNativeAds1--->> ${widget.isNativeAdFailedToLoad.value}");
    showLog("SurveyBigNativeAds2--->> ${widget.showNativeAd}");

    return Obx(
      () => widget.isNativeAdLoaded.value && widget.showNativeAd != null
          ? StatefulBuilder(
        builder: (context, setState) {
          // Fluttertoast.showToast(msg: "Big Native 1 Show");
          final width = MediaQuery.of(context).size.width;
          final height = MediaQuery.of(context).size.height;

          return Container(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(5.w),
              color: HexColor(AdsVariable.nativeBGColor),
            ),
            padding: EdgeInsets.only(bottom: 10.w, top: 15.w),
            width: width,
            height: widget.isBigNative ? height / 2.5 : height / 1.04,
            child: AdWidget(ad: widget.showNativeAd!),
          );
        },
      )

          : getShimmerWidget(),
    );
  }

  Widget getShimmerWidget() {
    if (widget.isNativeAdFailedToLoad.value) {
      return const SizedBox();
    } else {
      return widget.isBigNative
          ? const ShimmerBigNative()
          : const ShimmerSmallNative();
    }
  }
}

/// Big Native Survey2
class SurveyBigNativeAds2 extends StatefulWidget {
  final bool isBigNative;
  final NativeAd? showNativeAd;
  final RxBool isNativeAdLoaded;
  final RxBool isNativeAdFailedToLoad;

  const SurveyBigNativeAds2({
    super.key,
    required this.isBigNative,
    required this.showNativeAd,
    required this.isNativeAdLoaded,
    required this.isNativeAdFailedToLoad,
  });

  @override
  State<SurveyBigNativeAds2> createState() => _SurveyBigNativeAds2State();
}

class _SurveyBigNativeAds2State extends State<SurveyBigNativeAds2> {
  @override
  Widget build(BuildContext context) {
    showLog(
      "CHECK NULL SurveyBigNativeAds2>> ${widget.isNativeAdLoaded.value}",
    );
    showLog("SurveyBigNativeAds2--->> ${widget.isNativeAdFailedToLoad.value}");
    return Obx(
      () => widget.isNativeAdLoaded.value && widget.showNativeAd != null
          ? StatefulBuilder(
              builder: (context, setState) {
                // Fluttertoast.showToast(msg: "Big Native 2 Show");
                final width = MediaQuery.of(context).size.width;
                final height = MediaQuery.of(context).size.height;

                return Container(
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(5.w),
                    color: HexColor(AdsVariable.nativeBGColor),
                  ),
                  padding: EdgeInsets.only(bottom: 10.w, top: 15.w),
                  width: width,
                    height: widget.isBigNative ? height / 2.5 : height / 1.04,
                  child: AdWidget(ad: widget.showNativeAd!),
                );
              },
            )
          : getShimmerWidget(),
    );
  }

  Widget getShimmerWidget() {
    if (widget.isNativeAdFailedToLoad.value) {
      return const SizedBox();
    } else {
      return widget.isBigNative
          ? const ShimmerBigNative()
          : const ShimmerSmallNative();
    }
  }
}

/// Big Native Language3 (Settings entry)
class LanguageBigNativeAds3 extends StatelessWidget {
  final bool isBigNative;
  final NativeAd? showNativeAd;
  final bool isLoading;

  const LanguageBigNativeAds3({
    super.key,
    this.isBigNative = true,
    required this.showNativeAd,
    required this.isLoading,
  });

  @override
  Widget build(BuildContext context) {
    if (!AdsLoadUtil.isValidAdId(AdsVariable.prLanguageBigNative3)) {
      return const SizedBox();
    }
    return Obx(() {
      if (AdsLoadUtil.isLanguageBigNative3FailedToLoad.value) {
        return const SizedBox();
      }
      if (showNativeAd != null) {
        final width = MediaQuery.of(context).size.width;
        final height = MediaQuery.of(context).size.height;
        return Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(5.w),
            color: HexColor(AdsVariable.nativeBGColor),
          ),
          padding: EdgeInsets.only(bottom: 10.w, top: 15.w),
          width: width,
          height: isBigNative ? height / 2.5 : height / 1.04,
          child: AdWidget(ad: showNativeAd!),
        );
      } else if (isLoading) {
        return isBigNative
            ? const ShimmerBigNative()
            : const ShimmerSmallNative();
      } else {
        return const SizedBox();
      }
    });
  }
}

/// Small Native Third Intro
class SmallThirdIntroNativeWidget extends StatefulWidget {
  final bool isBigNative;
  final NativeAd? showNativeAd;
  final RxBool isNativeAdLoaded;
  final RxBool isNativeAdFailedToLoad;

  const SmallThirdIntroNativeWidget({
    super.key,
    required this.isBigNative,
    required this.showNativeAd,
    required this.isNativeAdLoaded,
    required this.isNativeAdFailedToLoad,
  });

  @override
  State<SmallThirdIntroNativeWidget> createState() =>
      _SmallThirdIntroNativeWidgetState();
}

class _SmallThirdIntroNativeWidgetState
    extends State<SmallThirdIntroNativeWidget> {
  @override
  Widget build(BuildContext context) {
    showLog(
      "CHECK NULL SmallThirdIntroNative>> ${widget.isNativeAdLoaded.value}",
    );
    showLog(
      "SmallThirdIntroNative--->> ${widget.isNativeAdFailedToLoad.value}",
    );
    return Obx(
      () => widget.isNativeAdLoaded.value && widget.showNativeAd != null
          ? StatefulBuilder(
              builder: (context, setState) {
                return Container(
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(5.w),
                    color: HexColor(AdsVariable.nativeBGColor),
                  ),
                  padding: EdgeInsets.only(bottom: 10.w, top: 0.w),
                  width: Get.width,
                  height: widget.isBigNative
                      ? Get.height / 2.5
                      : Get.height / 4.2,
                  child: AdWidget(ad: widget.showNativeAd!),
                );
              },
            )
          : getShimmerWidget(),
    );
  }

  Widget getShimmerWidget() {
    if (widget.isNativeAdFailedToLoad.value) {
      return SizedBox(height: 50.h);
    } else {
      return widget.isBigNative
          ? const ShimmerBigNative()
          : const ShimmerSmallNative();
    }
  }
}

/// Full Native ads
class FullNativeAdScreen extends StatefulWidget {
  const FullNativeAdScreen({super.key,});

  @override
  State<FullNativeAdScreen> createState() => _FullNativeAdScreenState();
}

class _FullNativeAdScreenState extends State<FullNativeAdScreen> {

  @override
  Widget build(BuildContext context) {
    log("intro full Ad is ${AdsVariable.fullNativeIntroAdIOS}");
    return getFinalNativeAd();
  }

  Widget getFinalNativeAd() {
    if (AdsLoadUtil.isFullNativeAdFailedToLoadIntro.value) {
      return const SizedBox();
    }

    if (AdsVariable.isPurchase == false &&
        AdsVariable.fullNativeIntroAdIOS != "11" &&
        AdsLoadUtil.fullNativeAdIntro != null) {

      return Container(
        alignment: Alignment.center,
        child: AdWidget(
          ad: AdsLoadUtil.fullNativeAdIntro!,
        ),
      );
    }

    return const SizedBox();
  }
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

  static void _storeInCache(NativeAd ad) {
    _cachedAd?.dispose();
    _cachedAd = ad;
    _cachedAt = DateTime.now();
  }

  /// Returns the cached ad if it is still fresh; expired ones are disposed.
  static NativeAd? _takeCachedAd() {
    final ad = _cachedAd;
    if (ad == null) return null;
    final cachedAt = _cachedAt;
    _cachedAd = null;
    _cachedAt = null;
    if (cachedAt != null && DateTime.now().difference(cachedAt) > _cacheTtl) {
      showLog("⌛🗑️ [AppBigNativeAdPool] Cached ad expired -> Disposing it");
      ad.dispose();
      return null;
    }
    return ad;
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

          if (_activeScreenTag != null && _onAdLoadedCallback != null) {
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
        final height = MediaQuery.of(context).size.height;
        return Container(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            color: HexColor(AdsVariable.nativeBGColor),
          ),
          width: width,
          height: height / 2.5,
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
