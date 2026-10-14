
import 'package:firebase_analytics/firebase_analytics.dart';
import 'package:video_downloader/for_ads/ads/ads_variable.dart';

class FirebaseAnalyticsService {


  static final FirebaseAnalytics _analytics = FirebaseAnalytics.instance;
  static final FirebaseAnalyticsObserver observer = FirebaseAnalyticsObserver(analytics: _analytics);


  static Future<void> logEvent({
    required String eventName,
    Map<String, Object>? parameters, // Change dynamic to Object
  }) async {
    try {
      await _analytics.logEvent(
        name: eventName,
        parameters: parameters,
      );
    } catch (e) {
      print("Error logging event: $e");
    }
  }

  /// Logs official Firebase ad_impression event with revenue parameters
  static Future<void> logAdImpression({
    required String placement,
    double revenue = 0.0,
    String currency = 'USD',
    int precision = 0,
  }) async {
    if (!AdsVariable.shouldSendRevenue(placement)) {
      print("ℹ️ [Analytics] ad_impression skipped for $placement (disabled by Remote Config)");
      return;
    }

    try {
      await logEvent(
        eventName: 'ad_impression',
        parameters: {
          'ad_platform': 'admob',
          'ad_source': 'AdMob',
          'ad_format': placement,
          'value': revenue,
          'currency': currency.isNotEmpty ? currency : 'USD',
          'precision_type': precision,
        },
      );
      print("📊✅ [Analytics] Logged ad_impression for $placement (revenue: $revenue $currency)");
    } catch (e) {
      print("❌ [Analytics] Error logging ad_impression: $e");
    }
  }


  /// Sets a user property
  static Future<void> setUserProperty({
    required String name,
    required String value,
  }) async {
    try {
      await _analytics.setUserProperty(
        name: name,
        value: value,
      );
    } catch (e) {
      print("Error setting user property: $e");
    }
  }

  /// Sets the current screen for analytics
  static Future<void> setCurrentScreen({
    required String screenName,
    String screenClassOverride='',
  }) async {
    try {
      await _analytics.setCurrentScreen(
        screenName: screenName,
        screenClassOverride: screenClassOverride,
      );
    } catch (e) {
      print("Error setting current screen: $e");
    }
  }

  /// Logs an app open event
  static Future<void> logAppOpen() async {
    try {
      await _analytics.logAppOpen();
    } catch (e) {
      print("Error logging app open: $e");
    }
  }

  /// Logs a user sign-in event
  static Future<void> logSignIn({
    required String method,
  }) async {
    try {
      await _analytics.logLogin(loginMethod: method);
    } catch (e) {
      print("Error logging sign-in: $e");
    }
  }

  /// Logs a user sign-out event
  static Future<void> logSignOut() async {
    try {
      await logEvent(eventName: 'user_sign_out');
    } catch (e) {
      print("Error logging sign-out: $e");
    }
  }
}
