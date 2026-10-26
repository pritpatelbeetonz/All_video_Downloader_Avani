
import 'package:flutter/services.dart';
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

  /// Logs when the user clicks download for a video, recording the platform and URL.
  static Future<void> logDownloadClick({
    required String platform,
    String? url,
  }) async {
    try {
      final sanitizedPlatform = platform.trim().isNotEmpty
          ? platform.trim().toLowerCase().replaceAll(RegExp(r'[^a-zA-Z0-9_]'), '_')
          : 'unknown';

      // 1. Standard event with platform parameter
      await logEvent(
        eventName: 'download_click',
        parameters: {
          'platform': sanitizedPlatform,
          if (url != null && url.isNotEmpty)
            'page_url': url.length > 100 ? url.substring(0, 100) : url,
        },
      );

      // 2. Direct platform event (e.g., 'download_instagram', 'download_facebook', etc.)
      await logEvent(
        eventName: 'download_$sanitizedPlatform',
        parameters: {
          'platform': sanitizedPlatform,
        },
      );

      try {
        const MethodChannel('nativeChannel').invokeMethod('logNative', {
          'tag': 'FIREBASE_ANALYTICS',
          'message': '📊 [FIREBASE_EVENT] Logged download_click (platform: $sanitizedPlatform)',
        });
      } catch (_) {}

      print("📊✅ [Analytics] Logged download event for platform: $sanitizedPlatform");
    } catch (e) {
      print("❌ [Analytics] Error logging download event: $e");
    }
  }

  /// Logs which video extractor was used (smdownloader vs apihut) and fallback reason if any.
  static Future<void> logExtractorEvent({
    required String extractor, // 'smdownloader' or 'apihut'
    required String platform,
    required bool isFallback,
    String fallbackReason = 'none', // 'none', 'smdownloader_failed', 'unsupported_platform'
    required String status, // 'success' or 'failed'
    String? url,
    String? errorMessage,
  }) async {
    try {
      final sanitizedPlatform = platform.trim().isNotEmpty
          ? platform.trim().toLowerCase().replaceAll(RegExp(r'[^a-zA-Z0-9_]'), '_')
          : 'unknown';

      final params = <String, Object>{
        'extractor': extractor,
        'platform': sanitizedPlatform,
        'is_fallback': isFallback ? 'true' : 'false',
        'fallback_reason': fallbackReason,
        'status': status,
        if (url != null && url.isNotEmpty)
          'page_url': url.length > 100 ? url.substring(0, 100) : url,
        if (errorMessage != null && errorMessage.isNotEmpty)
          'error': errorMessage.length > 100 ? errorMessage.substring(0, 100) : errorMessage,
      };

      // 1. Unified extraction event
      await logEvent(
        eventName: 'video_extractor_used',
        parameters: params,
      );

      // 2. Specific extractor event for easy filtering in Firebase Console
      await logEvent(
        eventName: 'extractor_$extractor',
        parameters: params,
      );

      // Native log for Android logcat visibility
      try {
        const MethodChannel('nativeChannel').invokeMethod('logNative', {
          'tag': 'FIREBASE_ANALYTICS',
          'message': '📊 [FIREBASE_EVENT] Extractor: $extractor | isFallback: $isFallback ($fallbackReason) | status: $status | platform: $sanitizedPlatform',
        });
      } catch (_) {}

      print("📊✅ [Analytics] Extractor used: $extractor (Fallback: $isFallback, Reason: $fallbackReason, Status: $status, Platform: $sanitizedPlatform)");
    } catch (e) {
      print("❌ [Analytics] Error logging extractor event: $e");
    }
  }

  /// Logs when fallback to ApiHut is triggered, recording the source and reason for fallback.
  static Future<void> logExtractorFallback({
    String fromExtractor = 'smdownloader',
    String toExtractor = 'apihut',
    required String fallbackReason, // 'smdownloader_failed', 'unsupported_platform'
    required String platform,
    String? url,
    String? errorMessage,
  }) async {
    try {
      final sanitizedPlatform = platform.trim().isNotEmpty
          ? platform.trim().toLowerCase().replaceAll(RegExp(r'[^a-zA-Z0-9_]'), '_')
          : 'unknown';

      final params = <String, Object>{
        'from_extractor': fromExtractor,
        'to_extractor': toExtractor,
        'fallback_reason': fallbackReason,
        'platform': sanitizedPlatform,
        if (url != null && url.isNotEmpty)
          'page_url': url.length > 100 ? url.substring(0, 100) : url,
        if (errorMessage != null && errorMessage.isNotEmpty)
          'error': errorMessage.length > 100 ? errorMessage.substring(0, 100) : errorMessage,
      };

      await logEvent(
        eventName: 'extractor_fallback',
        parameters: params,
      );

      try {
        const MethodChannel('nativeChannel').invokeMethod('logNative', {
          'tag': 'FIREBASE_ANALYTICS',
          'message': '📊 [FIREBASE_EVENT] Fallback triggered: $fromExtractor -> $toExtractor (Reason: $fallbackReason, Platform: $sanitizedPlatform)',
        });
      } catch (_) {}

      print("📊🔀 [Analytics] Fallback triggered: $fromExtractor -> $toExtractor (Reason: $fallbackReason, Platform: $sanitizedPlatform)");
    } catch (e) {
      print("❌ [Analytics] Error logging extractor fallback event: $e");
    }
  }

  /// Logs when download completes successfully, including extractor information
  static Future<void> logDownloadCompleted({
    required String platform,
    required String extractor,
    required bool isFallback,
  }) async {
    try {
      final sanitizedPlatform = platform.trim().isNotEmpty
          ? platform.trim().toLowerCase().replaceAll(RegExp(r'[^a-zA-Z0-9_]'), '_')
          : 'unknown';

      await logEvent(
        eventName: 'download_completed',
        parameters: {
          'platform': sanitizedPlatform,
          'extractor': extractor,
          'is_fallback': isFallback ? 'true' : 'false',
        },
      );
    } catch (e) {
      print("❌ [Analytics] Error logging download completed event: $e");
    }
  }


  /// Logs official Firebase ad_impression event with revenue parameters.
  /// Fires only when sendRevenueToAnalytics is on and [adUnitId] is in adImpressionEventAllowed.
  static Future<void> logAdImpression({
    required String? adUnitId,
    required String placement,
    required String format,
    double revenue = 0.0,
    String currency = 'USD',
    int precision = 0,
  }) async {
    if (!AdsVariable.shouldSendAdImpression(adUnitId)) {
      print("ℹ️ [Analytics] ad_impression skipped for $placement ($format, unit: $adUnitId) — not allowed by Remote Config");
      return;
    }

    try {
      await logEvent(
        eventName: 'ad_impression',
        parameters: {
          'ad_platform': 'admob',
          'ad_source': 'AdMob',
          'ad_format': format,
          'ad_unit_name': adUnitId!.trim(),
          'ad_placement': placement,
          'value': revenue,
          'currency': currency.isNotEmpty ? currency : 'USD',
          'precision_type': precision,
        },
      );
      print("📊✅ [Analytics] ad_impression allowed & logged for $placement ($format, unit: $adUnitId, revenue: $revenue $currency)");
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
