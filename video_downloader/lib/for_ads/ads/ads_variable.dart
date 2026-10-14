
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/material.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';
import 'package:purchases_flutter/purchases_flutter.dart';
import 'ads_load_util.dart' show AdsLoadUtil;


class AdsVariable {
  static bool? OpenAdInSplash;
  static AppOpenAd? appOpenAd;
  static AppOpenAd? appOpenAdSplash;
  static bool isShowingAd = false;
  static late ConnectivityResult connectivityResult;
  //when submittion
  static bool isPurchase = false;//***************************************************************************************
  static String dataGDPR = "";
  static Map<String, Package>? availablePackages;
  static bool isPrivacyOptionsRequired = false;
  static bool isYouTubeEnabled = false;

  static void resetAdIds() {
    prAppOpenAd = "11";
    prSplashInterstitialAd = "11";
    prPreInterstitialAd = "11";
    prLanguageBigNative1 = "11";
    prLanguageBigNative2 = "11";
    prLanguageBigNative3 = "11";
    prIntroAppOpen = "11";
    prAppBigNative = "11";
    fullNativeIntroAdIOS = "11";
    smallThirdIntroNativeIntroAdIOS = "11";
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
  static AppOpenAd? appOpenAdIntro;

  static NativeAd? fullNativeAdIntro;
  static NativeAd? smallThirdNativeAdIntro;

  /// Facebook
  static String facebookId = "";
  static String facebookToken = "";

  /// RemoteConfig Ad Unit IDs
  static String prAppOpenAd = "11";
  static String prSplashInterstitialAd = "11";
  static String prPreInterstitialAd = "11";
  static String prLanguageBigNative1 = "11";
  static String prLanguageBigNative2 = "11";
  static String prLanguageBigNative3 = "11";
  static String prIntroAppOpen = "11";
  static String prAppBigNative = "11";

  /// Backward-compatibility aliases
  static String get appOpenAdsIOS => prAppOpenAd;
  static set appOpenAdsIOS(String val) => prAppOpenAd = val;

  static String get interSplashIOS => prSplashInterstitialAd;
  static set interSplashIOS(String val) => prSplashInterstitialAd = val;

  static String get interPreLoadIOS => prPreInterstitialAd;
  static set interPreLoadIOS(String val) => prPreInterstitialAd = val;

  static String get bigNativeSurveyAdIOS1 => prLanguageBigNative1;
  static set bigNativeSurveyAdIOS1(String val) => prLanguageBigNative1 = val;

  static String get bigNativeSurveyAdIOS2 => prLanguageBigNative2;
  static set bigNativeSurveyAdIOS2(String val) => prLanguageBigNative2 = val;

  static String fullNativeIntroAdIOS = "11";
  static String smallThirdIntroNativeIntroAdIOS = "11";




  static Future<void> onShowAds(BuildContext context, {required Function onComplete}) async {

    if(AdsVariable.isPurchase){

      onComplete();

    }else{
      if (AdsVariable.currentClick % int.parse(AdsVariable.click) == 0) {

        if (await isInternetConnected()) {
          AdsLoadUtil.showInterstitial(onDismissed: () {
            onComplete();
          });
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
  static void onShowAddMealAds(BuildContext context, {required Function onComplete}) {
    if (AdsVariable.currentClick % int.parse(AdsVariable.click) == 0) {
      AdsLoadUtil.showInterstitial(onDismissed: () {
        onComplete();
      });
    } else {
      onComplete();
    }
    AdsVariable.currentClick++;
  }

  static bool showYearlyPriceFirstInYearlyPlan=false;

  ///For Tester email pass
  static String testEmail = 'zxcvbnm992@testing.com';
  static String testPassword = 'tEsT*ApP@992';
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

