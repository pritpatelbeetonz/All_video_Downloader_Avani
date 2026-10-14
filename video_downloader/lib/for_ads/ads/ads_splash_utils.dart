import 'dart:async';
import 'dart:convert';
import 'dart:developer';
import 'dart:io';

import 'package:app_tracking_transparency/app_tracking_transparency.dart';
import 'package:firebase_remote_config/firebase_remote_config.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:fluttertoast/fluttertoast.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';
import 'package:in_app_review/in_app_review.dart';
import 'package:purchases_flutter/purchases_flutter.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:video_downloader/for_ads/ads/ads_load_util.dart';
import 'package:video_downloader/for_ads/ads/app_open_ad.dart';
import 'package:video_downloader/for_ads/ads/life_cycle.dart';
import 'package:video_downloader/for_ads/utils/app_constants.dart';

import 'package:video_downloader/utility/shared_prefrence_service.dart';
import '../utils/firstTime.dart';
import '../utils/store_config.dart' show StoreConfig;
import 'ads_variable.dart';

class AdsSplashUtils {
  late SharedPreferences prefs;

  getOnlineIds({required Function() navigateScreen}) async {
    print("");
    prefs = await SharedPreferences.getInstance();

    /// RemoteConfig / SharedPreferences cache
    AdsVariable.prAppOpenAd =
        prefs.getString("pr_appOpenAd") ??
        prefs.getString("tr_appOpenAd") ??
        "11";
    AdsVariable.prSplashInterstitialAd =
        prefs.getString("pr_splashInterstitialAd") ??
        prefs.getString("tr_splashInterstitialAd") ??
        "11";
    AdsVariable.prPreInterstitialAd =
        prefs.getString("pr_preInterstitialAd") ??
        prefs.getString("tr_preInterstitialAd") ??
        "11";
    AdsVariable.prLanguageBigNative1 =
        prefs.getString("pr_languageBigNative1") ??
        prefs.getString("tr_surveyBigNative1") ??
        "11";
    AdsVariable.prLanguageBigNative2 =
        prefs.getString("pr_languageBigNative2") ??
        prefs.getString("tr_surveyBigNative2") ??
        "11";
    AdsVariable.prLanguageBigNative3 =
        prefs.getString("pr_languageBigNative3") ?? "11";
    AdsVariable.prIntroAppOpen = prefs.getString("pr_introAppOpen") ?? "11";
    AdsVariable.prAppBigNative = prefs.getString("pr_appBigNative") ?? "11";

    AdsVariable.fullNativeIntroAdIOS =
        prefs.getString("tr_introFullNative") ?? "11";
    AdsVariable.smallThirdIntroNativeIntroAdIOS =
        prefs.getString("tr_smallThirdIntroNative") ?? "11";
    AdsVariable.click = prefs.getString("click") ?? "10";

    AdsVariable.nativeBGColor = prefs.getString("nativeBGColor") ?? "fff8f5";
    AdsVariable.headerTextColor =
        prefs.getString("headlineTxtColor") ?? "000000";
    AdsVariable.bodyTextColor = prefs.getString("bodyTxtColor") ?? "000000";
    AdsVariable.btnTextColor = prefs.getString("buttonTxtColor") ?? "FFFFFF";
    AdsVariable.btnBgColorG1 = prefs.getString("buttonBgColorG1") ?? "FE7338";
    AdsVariable.btnBgColorG2 = prefs.getString("buttonBgColorG2") ?? "FE7338";
    AdsVariable.OpenAdInSplash = prefs.getBool("showOpenAdInSplash") ?? false;
    AdsVariable.isYouTubeEnabled = prefs.getBool("is_youtube_enabled") ?? false;
    AdsVariable.facebookId = prefs.getString("fb_appid") ?? "";
    AdsVariable.facebookToken = prefs.getString("fb_token") ?? "";

    log(
      "await AdsVariable.isInternetConnected() :- ${await AdsVariable.isInternetConnected()}",
    );

    if (await AdsVariable.isInternetConnected()) {
      try {
        final remoteConfig = FirebaseRemoteConfig.instance;
        await remoteConfig.setConfigSettings(
          RemoteConfigSettings(
            fetchTimeout: const Duration(minutes: 1),
            minimumFetchInterval: const Duration(minutes: kDebugMode ? 0 : 5),
          ),
        );

        await remoteConfig.fetchAndActivate();

        Map<String, dynamic> mapValues1 = {};

        String rawConfig = remoteConfig
            .getValue("video_downlader_launcher_v2")
            .asString();
        if (rawConfig.isEmpty || rawConfig == "{}") {
          rawConfig = remoteConfig.getValue("videoDownloader_v1").asString();
        }

        if (rawConfig.isNotEmpty && rawConfig != "{}") {
          log("Map is $rawConfig");
          mapValues1 = jsonDecode(rawConfig);
          if (kDebugMode) {
            print(mapValues1);
          }
        }

        /// Facebook id setup
        AdsVariable.facebookId = (mapValues1["fb_appid"] ?? "").toString();
        AdsVariable.facebookToken = (mapValues1["fb_token"] ?? "").toString();

        AdsVariable.prAppOpenAd =
            (mapValues1["pr_appOpenAd"] ??
                    mapValues1["tr_appOpenAd"] ??
                    AdsVariable.prAppOpenAd)
                .toString();
        AdsVariable.prSplashInterstitialAd =
            (mapValues1["pr_splashInterstitialAd"] ??
                    mapValues1["tr_splashInterstitialAd"] ??
                    AdsVariable.prSplashInterstitialAd)
                .toString();
        AdsVariable.prPreInterstitialAd =
            (mapValues1["pr_preInterstitialAd"] ??
                    mapValues1["tr_preInterstitialAd"] ??
                    AdsVariable.prPreInterstitialAd)
                .toString();

        AdsVariable.prLanguageBigNative1 =
            (mapValues1["pr_languageBigNative1"] ??
                    mapValues1["tr_surveyBigNative1"] ??
                    AdsVariable.prLanguageBigNative1)
                .toString();
        AdsVariable.prLanguageBigNative2 =
            (mapValues1["pr_languageBigNative2"] ??
                    mapValues1["tr_surveyBigNative2"] ??
                    AdsVariable.prLanguageBigNative2)
                .toString();
        AdsVariable.prLanguageBigNative3 =
            (mapValues1["pr_languageBigNative3"] ??
                    AdsVariable.prLanguageBigNative3)
                .toString();
        AdsVariable.prIntroAppOpen =
            (mapValues1["pr_introAppOpen"] ?? AdsVariable.prIntroAppOpen)
                .toString();
        AdsVariable.prAppBigNative =
            (mapValues1["pr_appBigNative"] ?? AdsVariable.prAppBigNative)
                .toString();

        AdsVariable.fullNativeIntroAdIOS =
            (mapValues1["tr_introFullNative"] ??
                    AdsVariable.fullNativeIntroAdIOS)
                .toString();
        AdsVariable.smallThirdIntroNativeIntroAdIOS =
            (mapValues1["tr_smallThirdIntroNative"] ??
                    AdsVariable.smallThirdIntroNativeIntroAdIOS)
                .toString();

        AdsVariable.nativeBGColor = (mapValues1['nativeBGColor'] ?? 'fff8f5')
            .toString();
        AdsVariable.headerTextColor =
            (mapValues1["headlineTxtColor"] ?? '000000').toString();
        AdsVariable.bodyTextColor = (mapValues1["bodyTxtColor"] ?? '000000')
            .toString();
        AdsVariable.btnBgColorG1 = (mapValues1["buttonBgColorG1"] ?? 'FE7338')
            .toString();
        AdsVariable.btnBgColorG2 = (mapValues1["buttonBgColorG2"] ?? 'FE7338')
            .toString();
        AdsVariable.btnTextColor = (mapValues1["buttonTxtColor"] ?? 'FFFFFF')
            .toString();
        AdsVariable.click = (mapValues1["click"] ?? '10').toString();
        AdsVariable.OpenAdInSplash = mapValues1["showOpenAdInSplash"] ?? false;
        AdsVariable.testEmail =
            mapValues1['testEmail'] ?? "zxcvbnm992@testing.com";
        AdsVariable.testPassword = mapValues1['testPassword'] ?? "tEsT*ApP@992";
        AdsVariable.showYearlyPriceFirstInYearlyPlan =
            mapValues1['showYearlyPriceFirstInYearlyPlan'] ?? false;

        // YouTube download remote config control
        final bool isYtFromMap =
            mapValues1["is_youtube_enabled"] == true ||
            mapValues1["enable_youtube_download"] == true;
        final bool isYtDirect =
            remoteConfig.getBool("is_youtube_enabled") ||
            remoteConfig.getBool("enable_youtube_download");

        AdsVariable.isYouTubeEnabled = isYtFromMap || isYtDirect;
        prefs.setBool("is_youtube_enabled", AdsVariable.isYouTubeEnabled);
        log(
          "🎬 [RemoteConfig] isYouTubeEnabled: ${AdsVariable.isYouTubeEnabled}",
        );

        /// Store firebase remote config data into shared preferences :
        prefs.setString("nativeBGColor", AdsVariable.nativeBGColor);
        prefs.setString("buttonBgColorG1", AdsVariable.btnBgColorG1);
        prefs.setString("buttonBgColorG2", AdsVariable.btnBgColorG2);
        prefs.setString("buttonTxtColor", AdsVariable.btnTextColor);
        prefs.setString("headlineTxtColor", AdsVariable.headerTextColor);
        prefs.setString("bodyTxtColor", AdsVariable.bodyTextColor);

        prefs.setString("fb_appid", AdsVariable.facebookId);
        prefs.setString("fb_token", AdsVariable.facebookToken);

        prefs.setString("pr_appOpenAd", AdsVariable.prAppOpenAd);
        prefs.setString(
          "pr_splashInterstitialAd",
          AdsVariable.prSplashInterstitialAd,
        );
        prefs.setString(
          "pr_preInterstitialAd",
          AdsVariable.prPreInterstitialAd,
        );
        prefs.setString(
          "pr_languageBigNative1",
          AdsVariable.prLanguageBigNative1,
        );
        prefs.setString(
          "pr_languageBigNative2",
          AdsVariable.prLanguageBigNative2,
        );
        prefs.setString(
          "pr_languageBigNative3",
          AdsVariable.prLanguageBigNative3,
        );
        prefs.setString("pr_introAppOpen", AdsVariable.prIntroAppOpen);
        prefs.setString("pr_appBigNative", AdsVariable.prAppBigNative);

        prefs.setString("click", AdsVariable.click);
        prefs.setBool(
          "showOpenAdInSplash",
          AdsVariable.OpenAdInSplash ?? false,
        );

        /// Check available purchases

        if (Platform.isIOS) {
          await initializeGDPR();
          await fetchPurchase();
        } else {
          await initializeMobileAds();
          await fetchPurchase();
        }

        // AdsLoadUtil.loadPreInterstitialAd(adId: AdsVariable.interPreLoadIOS);

        /// Facebook id setup
        await setupFbAdsId();

        if (AdsVariable.isPurchase) {
          Future.delayed(const Duration(seconds: 3), () {
            if (kDebugMode) {
              print('**call navigateScreen***');
            }
            navigateScreen();
          });
        }

        ///LOAD AND SHOW OPEN OR SPLASH AD BASED ON CONDITION

        await Check.init();

        final bool isFirstTime = SharedPrefService.getIsFirstTime();
        log("🚀 [AdsSplashUtils] isFirstTime: $isFirstTime");

        // Preload Survey native ads ONLY if this is the first time launch
        if (isFirstTime) {
          if (AdsLoadUtil.isValidAdId(AdsVariable.bigNativeSurveyAdIOS1)) {
            loadPreLoadLanguageNativeAds1();
          }
          if (AdsLoadUtil.isValidAdId(AdsVariable.bigNativeSurveyAdIOS2)) {
            loadPreLoadLanguageNativeAds2();
          }
        } else {
          showLog("Skipping Survey native ads preload - not first time launch");
        }

        Future.delayed(const Duration(seconds: 0), () async {
          if (AdsVariable.OpenAdInSplash == true) {
            if (AdsLoadUtil.isValidAdId(AdsVariable.appOpenAdsIOS)) {
              print('call open ad in splash condition');
              AdsLoadUtil().loadAndShowOpenAd(
                navigateScreen,
                AdsVariable.appOpenAdsIOS,
              );
            } else {
              print(
                'Invalid or "11" open ad ID - navigating directly without ad',
              );
              navigateScreen();
            }
          } else {
            if (AdsLoadUtil.isValidAdId(AdsVariable.interSplashIOS)) {
              print('----call else part-----');
              AdsLoadUtil().loadInterSplash(
                navigateScreen,
                AdsVariable.interSplashIOS,
              );
            } else {
              print(
                'Invalid or "11" splash inter ad ID - navigating directly without ad',
              );
              navigateScreen();
            }
          }
        });
      } on PlatformException catch (exception) {
        showLog("Exception is $exception");
        navigateScreen();
      } catch (exception) {
        showLog("Exception is $exception");
        navigateScreen();
      }
    } else {
      print("Not Connected");
      navigateScreen();
    }
  }

  late AppLifecycleReactor appLifecycleReactor;

  loadAppOpenAd() async {
    if (!AdsLoadUtil.isValidAdId(AdsVariable.appOpenAdsIOS)) {
      showLog(
        "Skipping loadAppOpenAd - invalid or '11' adId: ${AdsVariable.appOpenAdsIOS}",
      );
      return;
    }
    showLog("Load from BG...");
    AppOpenAdManager appOpenAdManager = AppOpenAdManager()
      ..loadAd(AdsVariable.appOpenAdsIOS);
    await appOpenAdManager.loadAd(AdsVariable.appOpenAdsIOS);
    appOpenAdManager.showAdIfAvailable(AdsVariable.appOpenAdsIOS);
    appLifecycleReactor = AppLifecycleReactor(
      appOpenAdManager: appOpenAdManager,
    );
    AppLifecycleReactor(
      appOpenAdManager: appOpenAdManager,
    ).listenToAppStateChanges();
  }

  final storage = FlutterSecureStorage();

  fetchPurchase() async {
    try {
      final customerInfo = await Purchases.getCustomerInfo();
      if (customerInfo.entitlements.all[entitlementKey] != null &&
          customerInfo.entitlements.all[entitlementKey]!.isActive == true) {
        AdsVariable.isPurchase = true;
        AdsVariable.resetAdIds();
      } else {
        AdsVariable.isPurchase = false;
      }
      if (AdsVariable.isPurchase) {
        showLog("Purchase ----->${AdsVariable.isPurchase}");
        AdsVariable.resetAdIds;
      }
    } catch (e) {
      showLog("PURCHASE_ERROR >> ${e.toString()}");
    }
  }
}

premiumInit() {
  if (Platform.isIOS || Platform.isMacOS) {
    StoreConfig(store: Store.appStore, apiKey: appleApiKey);
  } else if (Platform.isAndroid) {
    const useAmazon = bool.fromEnvironment("amazon");
    StoreConfig(
      store: useAmazon ? Store.amazon : Store.playStore,
      apiKey: useAmazon ? amazonApiKey : googleApiKey,
    );
  }
}

Future<void> setupFbAdsId() async {
  showLog("Call 1");
  const platformMethodChannel = MethodChannel('nativeChannel');
  showLog("Call 2");
  try {
    if (Platform.isIOS) {
      await platformMethodChannel.invokeMethod('setToast', {
        'isPurchase': AdsVariable.isPurchase.toString(),
        'fb_appid': AdsVariable.facebookId,
        'fb_token': AdsVariable.facebookToken,
        'nativeBGColor': AdsVariable.nativeBGColor,
        'btnBgColor': AdsVariable.btnBgColorG1,
        'btnTextColor': AdsVariable.btnTextColor,
        'headerTextColor': AdsVariable.headerTextColor,
        'bodyTextColor': AdsVariable.bodyTextColor,
      });
    } else {
      await platformMethodChannel.invokeMethod('setToast', {
        'isPurchase': AdsVariable.isPurchase.toString(),
        'fb_appid': AdsVariable.facebookId,
        'fb_token': AdsVariable.facebookToken,
        'nativeBGColor': AdsVariable.nativeBGColor,
        'btnBgColorG1': AdsVariable.btnBgColorG1,
        'btnBgColorG2': AdsVariable.btnBgColorG2,
        'btnTextColor': AdsVariable.btnTextColor,
        'headerTextColor': AdsVariable.headerTextColor,
        'bodyTextColor': AdsVariable.bodyTextColor,
      });
    }
  } catch (e) {
    showLog("setupFbAdsId error: $e");
  }

  showLog("Call 3");
}

Future<void> loadPreLoadLanguageNativeAds1() async {
  showLog("Call Method loadPreLoadLanguageNativeAds1");
  AdsVariable.nativeBigAdSurvey1 = await AdsLoadUtil().loadSurveyBigNative1(
    AdsVariable.bigNativeSurveyAdIOS1,
    true,
  );
  showLog(
    "AdsVariable.bigNativeSurveyAdIOS1 --->${AdsVariable.bigNativeSurveyAdIOS1}",
  );
  showLog(
    "AdsVariable.nativeBigAdSurvey1 ----===>${AdsVariable.nativeBigAdSurvey1?.adUnitId ?? ''}",
  );
  // showToast("Big Native 1 load");
}

Future<void> loadPreLoadLanguageNativeAds2() async {
  showLog("Call Method loadPreLoadLanguageNativeAds2");
  AdsVariable.nativeBigAdSurvey2 = await AdsLoadUtil().loadSurveyBigNative2(
    AdsVariable.bigNativeSurveyAdIOS2,
    true,
  );
  showLog(
    "AdsVariable.bigNativeSurveyAdIOS2 --->${AdsVariable.bigNativeSurveyAdIOS2}",
  );
  showLog(
    "AdsVariable.nativeBigAdSurvey2 ----===>${AdsVariable.nativeBigAdSurvey2?.adUnitId ?? ''}",
  );
  // showToast("Big Native 2 load");
}

void showToast(String msg) {
  Fluttertoast.showToast(msg: msg);
}

void loadPreLoadIntroFullNativeAds() async {
  showLog("Call Method loadPreLoadIntroFullNativeAds ");
  AdsVariable.fullNativeAdIntro = await AdsLoadUtil().loadIntroFullNative(
    AdsVariable.fullNativeIntroAdIOS,
    false,
  );
  showLog(
    "AdsVariable.fullNativeIntroAdIOS --->${AdsVariable.fullNativeIntroAdIOS}",
  );
  showLog(
    "AdsVariable.fullNativeAdIntro ----===>${AdsVariable.fullNativeAdIntro?.adUnitId ?? ''}",
  );
  // showToast("Full Native load");
}

void loadSmallThirdIntroNativeAds() async {
  showLog("Call Method loadPreLoadIntroFullNativeAds ");
  AdsVariable.smallThirdNativeAdIntro = await AdsLoadUtil()
      .loadSmallThirdIntroNative(
        AdsVariable.smallThirdIntroNativeIntroAdIOS,
        true,
      );
  showLog(
    "AdsVariable.fullNativeIntroAdIOS --->${AdsVariable.smallThirdIntroNativeIntroAdIOS}",
  );
  showLog(
    "AdsVariable.fullNativeAdIntro ----===>${AdsVariable.smallThirdNativeAdIntro?.adUnitId ?? ''}",
  );
}

/// GDPR Implementation methods : initializeGDPR, changePrivacyPreferences, loadConsentForm, initializeMobileAds
Future<FormError?> initializeGDPR() async {
  final completer = Completer<FormError?>();
  final params = ConsentRequestParameters(); //
  ConsentInformation.instance.requestConsentInfoUpdate(
    params,
    () async {
      if (await isPrivacyOptionsStatus()) {
        await loadConsentForm();
      } else {
        await initializeWithOutGDPR();
      }
      completer.complete();
    },
    (error) {
      log("ERROR ==> ${error.message}");
      completer.complete(error);
    },
  );

  return completer.future;
}

Future<TrackingStatus> initializeWithOutGDPR() async {
  final completer = Completer<TrackingStatus>();
  AppTrackingTransparency.requestTrackingAuthorization().then((value) async {
    final status = await AppTrackingTransparency.trackingAuthorizationStatus;
    if (status == TrackingStatus.authorized) {
      print("GDPR: TrackingStatus.required");
      await initializeMobileAds();
      completer.complete(TrackingStatus.authorized);
    } else {
      print("GDPR: TrackingStatus Not Required");
      await initializeMobileAds();
      completer.complete(TrackingStatus.denied);
    }
  });
  return completer.future;
}

Future<bool> changePrivacyPreferences() async {
  final completer = Completer<bool>();

  ConsentInformation.instance.requestConsentInfoUpdate(
    ConsentRequestParameters(),
    () async {
      if (await ConsentInformation.instance.isConsentFormAvailable()) {
        ConsentForm.loadConsentForm(
          (consentForm) {
            consentForm.show((formError) async {
              await initializeMobileAds();
              completer.complete(true);
            });
          },
          (formError) {
            completer.complete(false);
          },
        );
      } else {
        completer.complete(false);
      }
    },
    (error) {
      completer.complete(false);
    },
  );

  return completer.future;
}

void showPrivacyOptionsForm(
  OnConsentFormDismissedListener onConsentFormDismissedListener,
) {
  ConsentForm.showPrivacyOptionsForm(onConsentFormDismissedListener);
}

Future<FormError?> loadConsentForm() async {
  final completer = Completer<FormError?>();

  ConsentForm.loadConsentForm(
    (consentForm) async {
      final status = await ConsentInformation.instance.getConsentStatus();
      if (status == ConsentStatus.required) {
        consentForm.show((formError) {
          completer.complete(loadConsentForm());
        });
      } else {
        await initializeMobileAds();
        completer.complete();
      }
    },
    (FormError? error) {
      completer.complete(error);
    },
  );

  return completer.future;
}

Future<void> initializeMobileAds() async {
  if (await isPrivacyOptionsStatus()) {
    AdsVariable.isPrivacyOptionsRequired = true;
  } else {
    AdsVariable.isPrivacyOptionsRequired = false;
  }
  await MobileAds.instance.initialize();
}

Future<bool> isPrivacyOptionsStatus() async {
  return await ConsentInformation.instance
          .getPrivacyOptionsRequirementStatus() ==
      PrivacyOptionsRequirementStatus.required;
}

Future<void> checkAndShowInAppReview(String key) async {
  log('enter in app review function');
  final prefs = await SharedPreferences.getInstance();
  final hasShown = prefs.getBool(key) ?? false;
  final inAppReview = InAppReview.instance;
  if (!hasShown && await inAppReview.isAvailable()) {
    await inAppReview.requestReview();
    await prefs.setBool(key, true); // Mark as shown
    log('show review box');
  }
}
