
import 'dart:convert';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:video_downloader/for_ads/ads/ads_load_util.dart' show AdsLoadUtil;


class AdsVariable {
  static final ValueNotifier<int> configUpdatedNotifier = ValueNotifier<int>(0);

  static bool isShowingAd = false;
  static late ConnectivityResult connectivityResult;
  static const bool isPurchase = false;
  static String dataGDPR = "";
  static bool isPrivacyOptionsRequired = false;
  static bool isYouTubeEnabled = false;

  /// ApiHut API Keys and rotation flag from Remote Config
  static List<String> apiHutKeys = ["330f736e-3eb6-4ad7-8bb9-0754c2d68f9f"];
  static bool isApiHutKeyRotationOn = true;

  static void resetAdIds() {
    prPreInterstitialAd = "11";
    prLanguageBigNative1 = "11";
    prLanguageBigNative2 = "11";
    prLanguageBigNative3 = "11";
    prAppBigNative = "11";
    fullNativeIntroAdIOS = "11";
    smallThirdIntroNativeIntroAdIOS = "11";
  }

  static Future<void> loadFromPrefs() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      prPreInterstitialAd = prefs.getString("pr_preInterstitialAd") ?? prPreInterstitialAd;
      prAppBigNative = prefs.getString("pr_appBigNative") ?? prAppBigNative;
      nativeBGColor = prefs.getString("nativeBGColor") ?? nativeBGColor;
      headerTextColor = prefs.getString("headlineTxtColor") ?? headerTextColor;
      bodyTextColor = prefs.getString("bodyTxtColor") ?? bodyTextColor;
      btnBgColorG1 = prefs.getString("buttonBgColorG1") ?? btnBgColorG1;
      btnBgColorG2 = prefs.getString("buttonBgColorG2") ?? btnBgColorG2;
      btnTextColor = prefs.getString("buttonTxtColor") ?? btnTextColor;
      click = prefs.getString("click") ?? click;
      facebookId = prefs.getString("fb_appid") ?? facebookId;
      facebookToken = prefs.getString("fb_token") ?? facebookToken;

      final savedKeys = prefs.getStringList("apihut_keys");
      if (savedKeys != null && savedKeys.isNotEmpty) {
        apiHutKeys = savedKeys;
      }
      isApiHutKeyRotationOn = prefs.getBool("is_apihut_rotation_on") ?? isApiHutKeyRotationOn;
      sendRevenueToAnalytics = prefs.getBool("sendRevenueToAnalytics") ?? sendRevenueToAnalytics;
      adImpressionEventAllowed = prefs.getStringList("adImpressionEventAllowed") ?? adImpressionEventAllowed;

      debugPrint("📦 [AdsVariable] Loaded from cache: prAppBigNative=$prAppBigNative, apiHutKeys=${apiHutKeys.length}, rotation=$isApiHutKeyRotationOn, sendRevenueToAnalytics=$sendRevenueToAnalytics, adImpressionEventAllowed=${adImpressionEventAllowed.length}");
    } catch (e) {
      debugPrint("⚠️ [AdsVariable] Error loading from prefs: $e");
    }
  }

  static Future<void> updateFromRawJson(String rawJson) async {
    if (rawJson.isEmpty || rawJson == "{}") return;
    Map<String, dynamic> mapValues = {};
    try {
      mapValues = jsonDecode(rawJson);
    } catch (e) {
      debugPrint("⚠️ [AdsVariable] Error decoding rawJson: $e");
      return;
    }

    facebookId = (mapValues["fb_appid"] ?? facebookId).toString();
    facebookToken = (mapValues["fb_token"] ?? facebookToken).toString();

    prPreInterstitialAd = (mapValues["pr_preInterstitialAd"] ?? mapValues["tr_preInterstitialAd"] ?? prPreInterstitialAd).toString();
    prLanguageBigNative1 = (mapValues["pr_languageBigNative1"] ?? mapValues["tr_surveyBigNative1"] ?? prLanguageBigNative1).toString();
    prLanguageBigNative2 = (mapValues["pr_languageBigNative2"] ?? mapValues["tr_surveyBigNative2"] ?? prLanguageBigNative2).toString();
    prLanguageBigNative3 = (mapValues["pr_languageBigNative3"] ?? prLanguageBigNative3).toString();
    prAppBigNative = (mapValues["pr_appBigNative"] ?? prAppBigNative).toString();

    nativeBGColor = (mapValues['nativeBGColor'] ?? nativeBGColor).toString();
    headerTextColor = (mapValues["headlineTxtColor"] ?? headerTextColor).toString();
    bodyTextColor = (mapValues["bodyTxtColor"] ?? bodyTextColor).toString();
    btnBgColorG1 = (mapValues["buttonBgColorG1"] ?? btnBgColorG1).toString();
    btnBgColorG2 = (mapValues["buttonBgColorG2"] ?? btnBgColorG2).toString();
    btnTextColor = (mapValues["buttonTxtColor"] ?? btnTextColor).toString();
    click = (mapValues["click"] ?? click).toString();
    isYouTubeEnabled = mapValues["is_youtube_enabled"] ?? isYouTubeEnabled;

    // Parse ApiHut API Keys list from Remote Config
    if (mapValues.containsKey("apihut_keys") ||
        mapValues.containsKey("apihut_api_keys") ||
        mapValues.containsKey("apihut_key_list") ||
        mapValues.containsKey("api_keys")) {
      var rawKeys = mapValues["apihut_keys"] ??
          mapValues["apihut_api_keys"] ??
          mapValues["apihut_key_list"] ??
          mapValues["api_keys"];
      if (rawKeys is List) {
        final parsed = rawKeys
            .map((e) => e.toString().trim())
            .where((e) => e.isNotEmpty)
            .toList();
        if (parsed.isNotEmpty) apiHutKeys = parsed;
      } else if (rawKeys is String && rawKeys.isNotEmpty) {
        final parsed = rawKeys
            .split(',')
            .map((e) => e.trim())
            .where((e) => e.isNotEmpty)
            .toList();
        if (parsed.isNotEmpty) apiHutKeys = parsed;
      }
    }

    // Parse ApiHut API Key rotation flag
    if (mapValues.containsKey("is_apihut_rotation_on")) {
      isApiHutKeyRotationOn = mapValues["is_apihut_rotation_on"] == true ||
          mapValues["is_apihut_rotation_on"].toString().toLowerCase() == "true";
    } else if (mapValues.containsKey("apihut_key_rotation_on")) {
      isApiHutKeyRotationOn = mapValues["apihut_key_rotation_on"] == true ||
          mapValues["apihut_key_rotation_on"].toString().toLowerCase() == "true";
    } else if (mapValues.containsKey("enable_apihut_rotation")) {
      isApiHutKeyRotationOn = mapValues["enable_apihut_rotation"] == true ||
          mapValues["enable_apihut_rotation"].toString().toLowerCase() == "true";
    }

    // Parse Revenue / Ad Impression Analytics Flags
    if (mapValues.containsKey("sendRevenueToAnalytics") || mapValues.containsKey("send_revenue_to_analytics")) {
      sendRevenueToAnalytics = mapValues["sendRevenueToAnalytics"] == true ||
          mapValues["send_revenue_to_analytics"] == true ||
          mapValues["sendRevenueToAnalytics"].toString().toLowerCase() == "true";
    }
    // Parse ad unit IDs allowed to fire ad_impression (missing/empty → nothing fires)
    if (mapValues.containsKey("adImpressionEventAllowed") ||
        mapValues.containsKey("ad_impression_event_allowed")) {
      var rawIds = mapValues["adImpressionEventAllowed"] ??
          mapValues["ad_impression_event_allowed"];
      if (rawIds is List) {
        adImpressionEventAllowed = rawIds
            .map((e) => e.toString().trim())
            .where((e) => e.isNotEmpty)
            .toList();
      } else if (rawIds is String) {
        adImpressionEventAllowed = rawIds
            .split(',')
            .map((e) => e.trim())
            .where((e) => e.isNotEmpty)
            .toList();
      } else {
        adImpressionEventAllowed = [];
      }
    } else {
      adImpressionEventAllowed = [];
    }

    debugPrint("✅ [AdsVariable] Synced from Launcher RemoteConfig: prAppBigNative=$prAppBigNative, prPreInterstitialAd=$prPreInterstitialAd, sendRevenueToAnalytics=$sendRevenueToAnalytics, adImpressionEventAllowed=$adImpressionEventAllowed");

    try {
      final prefs = await SharedPreferences.getInstance();
      prefs.setString("pr_appBigNative", prAppBigNative);
      prefs.setString("pr_preInterstitialAd", prPreInterstitialAd);
      prefs.setString("nativeBGColor", nativeBGColor);
      prefs.setString("headlineTxtColor", headerTextColor);
      prefs.setString("bodyTxtColor", bodyTextColor);
      prefs.setString("buttonBgColorG1", btnBgColorG1);
      prefs.setString("buttonBgColorG2", btnBgColorG2);
      prefs.setString("buttonTxtColor", btnTextColor);
      prefs.setString("click", click);
      prefs.setString("fb_appid", facebookId);
      prefs.setString("fb_token", facebookToken);
      prefs.setStringList("apihut_keys", apiHutKeys);
      prefs.setBool("is_apihut_rotation_on", isApiHutKeyRotationOn);
      prefs.setBool("sendRevenueToAnalytics", sendRevenueToAnalytics);
      prefs.setStringList("adImpressionEventAllowed", adImpressionEventAllowed);
    } catch (_) {}

    try {
      const MethodChannel('nativeChannel').invokeMethod('setToast', {
        "btnBgColorG1": "#$btnBgColorG1",
        "btnBgColorG2": "#$btnBgColorG2",
        "nativeBGColor": "#$nativeBGColor",
        "headerTextColor": "#$headerTextColor",
        "bodyTextColor": "#$bodyTextColor",
        "btnTextColor": "#$btnTextColor",
        "fb_appid": facebookId,
        "fb_token": facebookToken,
      });
    } catch (_) {}

    configUpdatedNotifier.value++;
  }

  static Future<bool> isInternetConnected() {
    return Connectivity().checkConnectivity().then((connectivityResult) {
      return connectivityResult.contains(ConnectivityResult.none)
          ? false
          : true;
    });
  }

  static int currentClick = 0;
  static String click = "10";
  static String nativeBGColor = "fff8f5";
  static String btnBgColorG1 = "FE7338";
  static String btnBgColorG2 = "FE7338";
  static String btnTextColor = "FFFFFF";
  static String headerTextColor = "000000";
  static String bodyTextColor = "000000";

  /// Loaded Ad instances
  static NativeAd? nativeBigAdSurvey1;
  static NativeAd? get nativeBigAdLanguage1 => nativeBigAdSurvey1;
  static set nativeBigAdLanguage1(NativeAd? val) => nativeBigAdSurvey1 = val;

  static NativeAd? nativeBigAdSurvey2;
  static NativeAd? get nativeBigAdLanguage2 => nativeBigAdSurvey2;
  static set nativeBigAdLanguage2(NativeAd? val) => nativeBigAdSurvey2 = val;

  static NativeAd? nativeBigAdLanguage3;
  static NativeAd? nativeBigAdApp;

  static NativeAd? fullNativeAdIntro;
  static NativeAd? smallThirdNativeAdIntro;

  /// Facebook
  static String facebookId = "";
  static String facebookToken = "";

  /// RemoteConfig Ad Unit IDs
  static String prPreInterstitialAd = "11";
  static String prLanguageBigNative1 = "11";
  static String prLanguageBigNative2 = "11";
  static String prLanguageBigNative3 = "11";
  static String prAppBigNative = "11";

  /// Revenue / Ad Impression Analytics Flags
  static bool sendRevenueToAnalytics = true;
  static List<String> adImpressionEventAllowed = [];

  static bool shouldSendAdImpression(String? adUnitId) {
    if (!sendRevenueToAnalytics) return false;
    if (adUnitId == null || adUnitId.trim().isEmpty) return false;
    final id = adUnitId.trim();
    return adImpressionEventAllowed.any((e) => e.trim() == id);
  }

  /// Backward-compatibility aliases
  static String get appOpenAdsIOS => "11";
  static set appOpenAdsIOS(String val) {}

  static String get interSplashIOS => "11";
  static set interSplashIOS(String val) {}

  static String get interPreLoadIOS => prPreInterstitialAd;
  static set interPreLoadIOS(String val) => prPreInterstitialAd = val;

  static String get bigNativeSurveyAdIOS1 => prLanguageBigNative1;
  static set bigNativeSurveyAdIOS1(String val) => prLanguageBigNative1 = val;

  static String get bigNativeSurveyAdIOS2 => prLanguageBigNative2;
  static set bigNativeSurveyAdIOS2(String val) => prLanguageBigNative2 = val;

  static String fullNativeIntroAdIOS = "11";
  static String smallThirdIntroNativeIntroAdIOS = "11";

  static Future<void> onShowAds(
    BuildContext context, {
    String place = "VD_Quick_App",
    required Function onComplete,
  }) async {
    if (AdsVariable.isPurchase) {
      onComplete();
    } else {
      if (AdsVariable.currentClick % int.parse(AdsVariable.click) == 0) {
        if (await isInternetConnected()) {
          AdsLoadUtil.showInterstitial(
            place: place,
            onDismissed: () {
              onComplete();
            },
          );
        } else {
          onComplete();
        }
      } else {
        // Odd-numbered clicks: Perform onTap action directly
        onComplete();
      }
    }
    AdsVariable.currentClick++;
  }

  static void onShowAddMealAds(
    BuildContext context, {
    String place = "VD_Quick_App",
    required Function onComplete,
  }) {
    if (AdsVariable.currentClick % int.parse(AdsVariable.click) == 0) {
      AdsLoadUtil.showInterstitial(
        place: place,
        onDismissed: () {
          onComplete();
        },
      );
    } else {
      onComplete();
    }
    AdsVariable.currentClick++;
  }
}

class HexColor extends Color {
  static int _getColorFromHex(String hexColor) {
    hexColor = hexColor.toUpperCase().replaceAll("#", "");
    if (hexColor.length == 6) {
      hexColor = "FF$hexColor";
    }
    return int.parse(hexColor, radix: 16);
  }

  HexColor(final String hexColor) : super(_getColorFromHex(hexColor));
}

