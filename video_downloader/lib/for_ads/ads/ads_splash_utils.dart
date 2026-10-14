import 'dart:async';
import 'dart:developer';
import 'dart:io';

import 'package:app_tracking_transparency/app_tracking_transparency.dart';
import 'package:fluttertoast/fluttertoast.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';
import 'package:in_app_review/in_app_review.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'ads_variable.dart';

class AdsSplashUtils {
  /// Headless background initialization for MobileAds and GDPR (no splash/navigation/premium)
  static Future<void> initServices() async {
    try {
      if (Platform.isIOS) {
        await initializeGDPR();
      } else {
        await initializeMobileAds();
      }
    } catch (e) {
      log("⚠️ [AdsSplashUtils] initServices error: $e");
    }
  }

  /// GDPR Implementation methods : initializeGDPR, initializeWithOutGDPR, changePrivacyPreferences, loadConsentForm, initializeMobileAds
  static Future<FormError?> initializeGDPR() async {
    final completer = Completer<FormError?>();
    final params = ConsentRequestParameters();
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

  static Future<TrackingStatus> initializeWithOutGDPR() async {
    final completer = Completer<TrackingStatus>();
    AppTrackingTransparency.requestTrackingAuthorization().then((value) async {
      final status = await AppTrackingTransparency.trackingAuthorizationStatus;
      if (status == TrackingStatus.authorized) {
        await initializeMobileAds();
        completer.complete(TrackingStatus.authorized);
      } else {
        await initializeMobileAds();
        completer.complete(TrackingStatus.denied);
      }
    });
    return completer.future;
  }

  static Future<bool> changePrivacyPreferences() async {
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

  static void showPrivacyOptionsForm(
    OnConsentFormDismissedListener onConsentFormDismissedListener,
  ) {
    ConsentForm.showPrivacyOptionsForm(onConsentFormDismissedListener);
  }

  static Future<FormError?> loadConsentForm() async {
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

  static Future<void> initializeMobileAds() async {
    if (await isPrivacyOptionsStatus()) {
      AdsVariable.isPrivacyOptionsRequired = true;
    } else {
      AdsVariable.isPrivacyOptionsRequired = false;
    }
    await MobileAds.instance.initialize();
  }

  static Future<bool> isPrivacyOptionsStatus() async {
    return await ConsentInformation.instance
            .getPrivacyOptionsRequirementStatus() ==
        PrivacyOptionsRequirementStatus.required;
  }

  static Future<void> checkAndShowInAppReview(String key) async {
    log('enter in app review function');
    final prefs = await SharedPreferences.getInstance();
    final hasShown = prefs.getBool(key) ?? false;
    final inAppReview = InAppReview.instance;
    if (!hasShown && await inAppReview.isAvailable()) {
      await inAppReview.requestReview();
      await prefs.setBool(key, true);
      log('show review box');
    }
  }

  static void showToast(String msg) {
    Fluttertoast.showToast(msg: msg);
  }
}

void showToast(String msg) {
  Fluttertoast.showToast(msg: msg);
}
