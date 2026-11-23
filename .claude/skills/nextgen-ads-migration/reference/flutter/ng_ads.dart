/// Google Mobile Ads (Next-Gen SDK) for the embedded downloader.
///
/// The host app runs the Next-Gen SDK natively (it cannot coexist with the classic SDK that the
/// `google_mobile_ads` plugin needs). This library keeps the subset of the `google_mobile_ads`
/// Dart API the downloader uses — same class names and signatures — and forwards every call to
/// the host over the `ng_ads` method channel. Ad views are rendered natively through the
/// `ng_ads/ad_view` platform view.
///
/// Host side: `app.messages.ads.ng.flutter.NgAdsFlutterBridge` (Kotlin).
library;

import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

// ---------------------------------------------------------------------------------------------
// Channel + ad registry
// ---------------------------------------------------------------------------------------------

class _NgChannel {
  static const MethodChannel channel = MethodChannel('ng_ads');
  static const String viewType = 'ng_ads/ad_view';

  static final Map<int, Ad> _ads = <int, Ad>{};
  static final Map<int, ConsentForm> _forms = <int, ConsentForm>{};
  static final Map<int, OnConsentFormDismissedListener> _dismissListeners =
      <int, OnConsentFormDismissedListener>{};
  static int _nextId = 1;
  static bool _handlerSet = false;

  static int register(Ad ad) {
    _ensureHandler();
    final id = _nextId++;
    _ads[id] = ad;
    return id;
  }

  static int nextId() {
    _ensureHandler();
    return _nextId++;
  }

  static void unregister(int id) => _ads.remove(id);

  static void _ensureHandler() {
    if (_handlerSet) return;
    _handlerSet = true;
    channel.setMethodCallHandler(_onCall);
  }

  static Future<dynamic> _onCall(MethodCall call) async {
    final args = (call.arguments as Map?)?.cast<String, dynamic>() ?? const <String, dynamic>{};
    if (call.method == 'onConsentFormDismissed') {
      final listener = _dismissListeners.remove(args['listenerId'] as int);
      listener?.call(FormError._fromMap(args['error']));
      return null;
    }
    if (call.method != 'onAdEvent') return null;
    final ad = _ads[args['adId'] as int];
    if (ad == null) return null;
    ad._onEvent(args['event'] as String, args);
    return null;
  }
}

// ---------------------------------------------------------------------------------------------
// Common types
// ---------------------------------------------------------------------------------------------

class AdRequest {
  const AdRequest({this.keywords, this.contentUrl, this.nonPersonalizedAds});
  final List<String>? keywords;
  final String? contentUrl;
  final bool? nonPersonalizedAds;
}

class ResponseInfo {
  const ResponseInfo({this.responseId, this.mediationAdapterClassName});
  final String? responseId;
  final String? mediationAdapterClassName;
}

class AdError {
  AdError(this.code, this.domain, this.message);
  final int code;
  final String domain;
  final String message;

  @override
  String toString() => '$runtimeType(code: $code, domain: $domain, message: $message)';
}

class LoadAdError extends AdError {
  LoadAdError(super.code, super.domain, super.message, this.responseInfo);
  final ResponseInfo? responseInfo;
}

enum PrecisionType { unknown, estimated, publisherProvided, precise }

typedef AdEventCallback = void Function(Ad ad);
typedef AdLoadErrorCallback = void Function(Ad ad, LoadAdError error);
typedef OnPaidEventCallback = void Function(
    Ad ad, double valueMicros, PrecisionType precision, String currencyCode);

LoadAdError _loadError(Map<String, dynamic> m) => LoadAdError(
      (m['code'] as int?) ?? 0,
      'com.google.android.libraries.ads.mobile.sdk',
      (m['message'] as String?) ?? '',
      ResponseInfo(responseId: m['responseId'] as String?),
    );

AdError _showError(Map<String, dynamic> m) => AdError(
      (m['code'] as int?) ?? 0,
      'com.google.android.libraries.ads.mobile.sdk',
      (m['message'] as String?) ?? '',
    );

/// Base class of every ad (same role as the plugin's `Ad`).
abstract class Ad {
  Ad({required this.adUnitId}) {
    _id = _NgChannel.register(this);
  }

  final String adUnitId;
  late final int _id;
  ResponseInfo? responseInfo;
  bool _disposed = false;

  void _onEvent(String event, Map<String, dynamic> args);

  void _paid(OnPaidEventCallback? cb, Map<String, dynamic> args) {
    if (cb == null) return;
    final micros = (args['valueMicros'] as num?)?.toDouble() ?? 0;
    final p = (args['precision'] as int?) ?? 0;
    final precision = PrecisionType.values[p.clamp(0, PrecisionType.values.length - 1)];
    cb(this, micros, precision, (args['currencyCode'] as String?) ?? '');
  }

  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    _NgChannel.unregister(_id);
    try {
      await _NgChannel.channel.invokeMethod('disposeAd', {'adId': _id});
    } catch (_) {}
  }
}

/// An ad rendered in the widget tree through [AdWidget].
abstract class AdWithView extends Ad {
  AdWithView({required super.adUnitId});
}

/// A full-screen ad shown with `show()`.
abstract class AdWithoutView extends Ad {
  AdWithoutView({required super.adUnitId});
}

// ---------------------------------------------------------------------------------------------
// Native
// ---------------------------------------------------------------------------------------------

enum MediaAspectRatio { unknown, any, landscape, portrait, square }

enum AdChoicesPlacement { topRightCorner, topLeftCorner, bottomRightCorner, bottomLeftCorner }

class VideoOptions {
  const VideoOptions({this.clickToExpandRequested, this.customControlsRequested, this.startMuted});
  final bool? clickToExpandRequested;
  final bool? customControlsRequested;
  final bool? startMuted;
}

class NativeAdOptions {
  const NativeAdOptions({
    this.adChoicesPlacement,
    this.mediaAspectRatio,
    this.videoOptions,
    this.requestCustomMuteThisAd,
    this.shouldRequestMultipleImages,
    this.shouldReturnUrlsForImageAssets,
  });
  final AdChoicesPlacement? adChoicesPlacement;
  final MediaAspectRatio? mediaAspectRatio;
  final VideoOptions? videoOptions;
  final bool? requestCustomMuteThisAd;
  final bool? shouldRequestMultipleImages;
  final bool? shouldReturnUrlsForImageAssets;
}

class NativeAdListener {
  NativeAdListener({
    this.onAdLoaded,
    this.onAdFailedToLoad,
    this.onAdOpened,
    this.onAdWillDismissScreen,
    this.onAdClosed,
    this.onAdImpression,
    this.onAdClicked,
    this.onPaidEvent,
  });
  final AdEventCallback? onAdLoaded;
  final AdLoadErrorCallback? onAdFailedToLoad;
  final AdEventCallback? onAdOpened;
  final AdEventCallback? onAdWillDismissScreen;
  final AdEventCallback? onAdClosed;
  final AdEventCallback? onAdImpression;
  final AdEventCallback? onAdClicked;
  final OnPaidEventCallback? onPaidEvent;
}

class NativeAd extends AdWithView {
  NativeAd({
    required super.adUnitId,
    required this.listener,
    required this.request,
    this.factoryId,
    this.nativeAdOptions,
    this.customOptions,
    Object? nativeTemplateStyle,
  });

  final NativeAdListener listener;
  final AdRequest request;
  final String? factoryId;
  final NativeAdOptions? nativeAdOptions;
  final Map<String, Object>? customOptions;

  Future<void> load() async {
    final options = nativeAdOptions;
    await _NgChannel.channel.invokeMethod('loadNative', {
      'adId': _id,
      'adUnitId': adUnitId,
      'factoryId': factoryId ?? 'bigNativeAds',
      'startMuted': options?.videoOptions?.startMuted ?? true,
      'mediaAspectRatio': (options?.mediaAspectRatio ?? MediaAspectRatio.any).index,
    });
  }

  @override
  void _onEvent(String event, Map<String, dynamic> args) {
    switch (event) {
      case 'onAdLoaded':
        responseInfo = ResponseInfo(responseId: args['responseId'] as String?);
        listener.onAdLoaded?.call(this);
      case 'onAdFailedToLoad':
        listener.onAdFailedToLoad?.call(this, _loadError(args));
      case 'onAdImpression':
        listener.onAdImpression?.call(this);
      case 'onAdClicked':
        listener.onAdClicked?.call(this);
      case 'onAdOpened':
        listener.onAdOpened?.call(this);
      case 'onAdClosed':
        listener.onAdClosed?.call(this);
      case 'onPaidEvent':
        _paid(listener.onPaidEvent, args);
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Banner
// ---------------------------------------------------------------------------------------------

class AdSize {
  const AdSize({required this.width, required this.height});
  final int width;
  final int height;

  static const AdSize banner = AdSize(width: 320, height: 50);
  static const AdSize largeBanner = AdSize(width: 320, height: 100);
  static const AdSize mediumRectangle = AdSize(width: 300, height: 250);
  static const AdSize fullBanner = AdSize(width: 468, height: 60);
  static const AdSize leaderboard = AdSize(width: 728, height: 90);
}

class BannerAdListener {
  BannerAdListener({
    this.onAdLoaded,
    this.onAdFailedToLoad,
    this.onAdOpened,
    this.onAdWillDismissScreen,
    this.onAdClosed,
    this.onAdImpression,
    this.onAdClicked,
    this.onPaidEvent,
  });
  final AdEventCallback? onAdLoaded;
  final AdLoadErrorCallback? onAdFailedToLoad;
  final AdEventCallback? onAdOpened;
  final AdEventCallback? onAdWillDismissScreen;
  final AdEventCallback? onAdClosed;
  final AdEventCallback? onAdImpression;
  final AdEventCallback? onAdClicked;
  final OnPaidEventCallback? onPaidEvent;
}

class BannerAd extends AdWithView {
  BannerAd({
    required this.size,
    required super.adUnitId,
    required this.listener,
    required this.request,
  });

  final AdSize size;
  final BannerAdListener listener;
  final AdRequest request;

  Future<void> load() async {
    await _NgChannel.channel.invokeMethod('loadBanner', {
      'adId': _id,
      'adUnitId': adUnitId,
      'width': size.width,
      'height': size.height,
    });
  }

  @override
  void _onEvent(String event, Map<String, dynamic> args) {
    switch (event) {
      case 'onAdLoaded':
        responseInfo = ResponseInfo(responseId: args['responseId'] as String?);
        listener.onAdLoaded?.call(this);
      case 'onAdFailedToLoad':
        listener.onAdFailedToLoad?.call(this, _loadError(args));
      case 'onAdImpression':
        listener.onAdImpression?.call(this);
      case 'onAdClicked':
        listener.onAdClicked?.call(this);
      case 'onPaidEvent':
        _paid(listener.onPaidEvent, args);
    }
  }
}

/// Shows a loaded [NativeAd] or [BannerAd] (rendered natively by the host).
class AdWidget extends StatelessWidget {
  const AdWidget({super.key, required this.ad});
  final AdWithView ad;

  @override
  Widget build(BuildContext context) {
    if (defaultTargetPlatform != TargetPlatform.android) return const SizedBox.shrink();
    return PlatformViewLink(
      viewType: _NgChannel.viewType,
      surfaceFactory: (context, controller) => AndroidViewSurface(
        controller: controller as AndroidViewController,
        gestureRecognizers: const <Factory<OneSequenceGestureRecognizer>>{},
        hitTestBehavior: PlatformViewHitTestBehavior.opaque,
      ),
      onCreatePlatformView: (params) {
        return PlatformViewsService.initSurfaceAndroidView(
          id: params.id,
          viewType: _NgChannel.viewType,
          layoutDirection: Directionality.maybeOf(context) ?? TextDirection.ltr,
          creationParams: <String, dynamic>{'adId': ad._id},
          creationParamsCodec: const StandardMessageCodec(),
        )
          ..addOnPlatformViewCreatedListener(params.onPlatformViewCreated)
          ..create();
      },
    );
  }
}

// ---------------------------------------------------------------------------------------------
// Full-screen (interstitial / app open)
// ---------------------------------------------------------------------------------------------

typedef GenericAdEventCallback<T> = void Function(T ad);
typedef FullScreenAdLoadErrorCallback = void Function(LoadAdError error);

class FullScreenContentCallback<T> {
  const FullScreenContentCallback({
    this.onAdShowedFullScreenContent,
    this.onAdImpression,
    this.onAdFailedToShowFullScreenContent,
    this.onAdWillDismissFullScreenContent,
    this.onAdDismissedFullScreenContent,
    this.onAdClicked,
  });
  final GenericAdEventCallback<T>? onAdShowedFullScreenContent;
  final GenericAdEventCallback<T>? onAdImpression;
  final void Function(T ad, AdError error)? onAdFailedToShowFullScreenContent;
  final GenericAdEventCallback<T>? onAdWillDismissFullScreenContent;
  final GenericAdEventCallback<T>? onAdDismissedFullScreenContent;
  final GenericAdEventCallback<T>? onAdClicked;
}

class InterstitialAdLoadCallback {
  const InterstitialAdLoadCallback({required this.onAdLoaded, required this.onAdFailedToLoad});
  final GenericAdEventCallback<InterstitialAd> onAdLoaded;
  final FullScreenAdLoadErrorCallback onAdFailedToLoad;
}

class AppOpenAdLoadCallback {
  const AppOpenAdLoadCallback({required this.onAdLoaded, required this.onAdFailedToLoad});
  final GenericAdEventCallback<AppOpenAd> onAdLoaded;
  final FullScreenAdLoadErrorCallback onAdFailedToLoad;
}

abstract class _FullScreenAd<T extends Ad> extends AdWithoutView {
  _FullScreenAd({required super.adUnitId});

  FullScreenContentCallback<T>? fullScreenContentCallback;
  OnPaidEventCallback? onPaidEvent;

  void _loaded();
  void _failed(LoadAdError e);

  Future<void> show() async {
    await _NgChannel.channel.invokeMethod('showAd', {'adId': _id});
  }

  Future<void> setImmersiveMode(bool immersiveModeEnabled) async {
    await _NgChannel.channel
        .invokeMethod('setImmersiveMode', {'adId': _id, 'enabled': immersiveModeEnabled});
  }

  @override
  void _onEvent(String event, Map<String, dynamic> args) {
    final self = this as T;
    final cb = fullScreenContentCallback;
    switch (event) {
      case 'onAdLoaded':
        responseInfo = ResponseInfo(responseId: args['responseId'] as String?);
        _loaded();
      case 'onAdFailedToLoad':
        _failed(_loadError(args));
        _NgChannel.unregister(_id);
      case 'onAdShowedFullScreenContent':
        cb?.onAdShowedFullScreenContent?.call(self);
      case 'onAdImpression':
        cb?.onAdImpression?.call(self);
      case 'onAdClicked':
        cb?.onAdClicked?.call(self);
      case 'onAdFailedToShowFullScreenContent':
        cb?.onAdFailedToShowFullScreenContent?.call(self, _showError(args));
      case 'onAdDismissedFullScreenContent':
        cb?.onAdWillDismissFullScreenContent?.call(self);
        cb?.onAdDismissedFullScreenContent?.call(self);
      case 'onPaidEvent':
        _paid(onPaidEvent, args);
    }
  }
}

class InterstitialAd extends _FullScreenAd<InterstitialAd> {
  InterstitialAd._(String adUnitId, this._callback) : super(adUnitId: adUnitId);
  final InterstitialAdLoadCallback _callback;

  static Future<void> load({
    required String adUnitId,
    required AdRequest request,
    required InterstitialAdLoadCallback adLoadCallback,
  }) async {
    final ad = InterstitialAd._(adUnitId, adLoadCallback);
    await _NgChannel.channel
        .invokeMethod('loadInterstitial', {'adId': ad._id, 'adUnitId': adUnitId});
  }

  @override
  void _loaded() => _callback.onAdLoaded(this);

  @override
  void _failed(LoadAdError e) => _callback.onAdFailedToLoad(e);
}

class AppOpenAd extends _FullScreenAd<AppOpenAd> {
  AppOpenAd._(String adUnitId, this._callback) : super(adUnitId: adUnitId);
  final AppOpenAdLoadCallback _callback;

  static Future<void> load({
    required String adUnitId,
    required AdRequest request,
    required AppOpenAdLoadCallback adLoadCallback,
    int? orientation,
  }) async {
    final ad = AppOpenAd._(adUnitId, adLoadCallback);
    await _NgChannel.channel.invokeMethod('loadAppOpen', {'adId': ad._id, 'adUnitId': adUnitId});
  }

  @override
  void _loaded() => _callback.onAdLoaded(this);

  @override
  void _failed(LoadAdError e) => _callback.onAdFailedToLoad(e);
}

// ---------------------------------------------------------------------------------------------
// SDK init
// ---------------------------------------------------------------------------------------------

class AdapterStatus {
  const AdapterStatus(this.state, this.description, this.latency);
  final int state;
  final String description;
  final double latency;
}

class InitializationStatus {
  const InitializationStatus(this.adapterStatuses);
  final Map<String, AdapterStatus> adapterStatuses;
}

class MobileAds {
  MobileAds._();
  static final MobileAds instance = MobileAds._();

  /// Waits for the host's single Next-Gen SDK initialization.
  Future<InitializationStatus> initialize() async {
    _NgChannel._ensureHandler();
    await _NgChannel.channel.invokeMethod('initialize');
    return const InitializationStatus(<String, AdapterStatus>{});
  }
}

// ---------------------------------------------------------------------------------------------
// UMP consent (forwarded to the host's User Messaging Platform SDK)
// ---------------------------------------------------------------------------------------------

enum DebugGeography { debugGeographyDisabled, debugGeographyEea, debugGeographyNotEea }

class ConsentDebugSettings {
  ConsentDebugSettings({this.debugGeography, this.testIdentifiers});
  final DebugGeography? debugGeography;
  final List<String>? testIdentifiers;
}

class ConsentRequestParameters {
  ConsentRequestParameters({this.tagForUnderAgeOfConsent, this.consentDebugSettings});
  final bool? tagForUnderAgeOfConsent;
  final ConsentDebugSettings? consentDebugSettings;
}

class FormError {
  FormError({required this.errorCode, required this.message});
  final int errorCode;
  final String message;

  static FormError? _fromMap(Object? m) {
    if (m is! Map) return null;
    return FormError(errorCode: (m['errorCode'] as int?) ?? 0, message: (m['message'] as String?) ?? '');
  }
}

enum ConsentStatus { notRequired, obtained, required, unknown }

enum PrivacyOptionsRequirementStatus { notRequired, required, unknown }

typedef OnConsentInfoUpdateSuccessListener = void Function();
typedef OnConsentInfoUpdateFailureListener = void Function(FormError error);
typedef OnConsentFormLoadSuccessListener = void Function(ConsentForm consentForm);
typedef OnConsentFormLoadFailureListener = void Function(FormError formError);
typedef OnConsentFormDismissedListener = void Function(FormError? formError);

class ConsentInformation {
  ConsentInformation._();
  static final ConsentInformation instance = ConsentInformation._();

  void requestConsentInfoUpdate(
    ConsentRequestParameters params,
    OnConsentInfoUpdateSuccessListener successListener,
    OnConsentInfoUpdateFailureListener failureListener,
  ) {
    _NgChannel._ensureHandler();
    _NgChannel.channel.invokeMethod('consentRequestUpdate', {
      'tagForUnderAgeOfConsent': params.tagForUnderAgeOfConsent,
      'debugGeography': params.consentDebugSettings?.debugGeography?.index,
      'testIdentifiers': params.consentDebugSettings?.testIdentifiers,
    }).then((value) {
      final error = FormError._fromMap(value);
      if (error == null) {
        successListener();
      } else {
        failureListener(error);
      }
    }).catchError((Object e) {
      failureListener(FormError(errorCode: -1, message: e.toString()));
    });
  }

  Future<bool> isConsentFormAvailable() async =>
      (await _NgChannel.channel.invokeMethod<bool>('consentIsFormAvailable')) ?? false;

  Future<ConsentStatus> getConsentStatus() async {
    final i = (await _NgChannel.channel.invokeMethod<int>('consentGetStatus')) ?? 3;
    return ConsentStatus.values[i.clamp(0, ConsentStatus.values.length - 1)];
  }

  Future<bool> canRequestAds() async =>
      (await _NgChannel.channel.invokeMethod<bool>('consentCanRequestAds')) ?? false;

  Future<PrivacyOptionsRequirementStatus> getPrivacyOptionsRequirementStatus() async {
    final i = (await _NgChannel.channel.invokeMethod<int>('consentPrivacyOptionsStatus')) ?? 2;
    return PrivacyOptionsRequirementStatus
        .values[i.clamp(0, PrivacyOptionsRequirementStatus.values.length - 1)];
  }

  Future<void> reset() async => _NgChannel.channel.invokeMethod('consentReset');
}

class ConsentForm {
  ConsentForm._(this._formId);
  final int _formId;

  static void loadConsentForm(
    OnConsentFormLoadSuccessListener successListener,
    OnConsentFormLoadFailureListener failureListener,
  ) {
    final formId = _NgChannel.nextId();
    _NgChannel.channel.invokeMethod('consentLoadForm', {'formId': formId}).then((value) {
      final error = FormError._fromMap(value);
      if (error == null) {
        final form = ConsentForm._(formId);
        _NgChannel._forms[formId] = form;
        successListener(form);
      } else {
        failureListener(error);
      }
    }).catchError((Object e) {
      failureListener(FormError(errorCode: -1, message: e.toString()));
    });
  }

  void show(OnConsentFormDismissedListener onConsentFormDismissedListener) {
    final listenerId = _NgChannel.nextId();
    _NgChannel._dismissListeners[listenerId] = onConsentFormDismissedListener;
    _NgChannel.channel.invokeMethod('consentShowForm', {'formId': _formId, 'listenerId': listenerId});
  }

  static Future<void> showPrivacyOptionsForm(
    OnConsentFormDismissedListener onConsentFormDismissedListener,
  ) async {
    final listenerId = _NgChannel.nextId();
    _NgChannel._dismissListeners[listenerId] = onConsentFormDismissedListener;
    await _NgChannel.channel.invokeMethod('consentShowPrivacyOptionsForm', {'listenerId': listenerId});
  }

  static Future<void> loadAndShowConsentFormIfRequired(
    OnConsentFormDismissedListener onConsentFormDismissedListener,
  ) async {
    final listenerId = _NgChannel.nextId();
    _NgChannel._dismissListeners[listenerId] = onConsentFormDismissedListener;
    await _NgChannel.channel.invokeMethod('consentLoadAndShowIfRequired', {'listenerId': listenerId});
  }

  void dispose() {
    _NgChannel._forms.remove(_formId);
    _NgChannel.channel.invokeMethod('consentDisposeForm', {'formId': _formId});
  }
}

// ---------------------------------------------------------------------------------------------
// App state (foreground / background), as in the plugin's AppStateEventNotifier
// ---------------------------------------------------------------------------------------------

enum AppState { background, foreground }
