---
name: nextgen-ads-migration
description: Migrate an Android app (Kotlin/Java, optionally with an embedded Flutter module using google_mobile_ads) from the classic AdMob SDK (com.google.android.gms:play-services-ads) to the Google Mobile Ads Next-Gen SDK (com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk). Use when asked to "do the NextGen / Next-Gen migration", "move ads to ads-mobile-sdk", or copy the Next-Gen ad structure into another launcher / app.
---

# Google Mobile Ads Next-Gen SDK migration

Proven on: MyDeviceSetting_V26 (launcher + Java app) and All_Video_Downloader_Launcher v3
(Kotlin launcher + embedded Flutter downloader). Reference implementations are in `reference/`.

## 0. Facts to know before touching code

- **The two SDKs cannot coexist.** Adding `ads-mobile-sdk` next to `play-services-ads` fails
  `checkDebugDuplicateClasses` (~73 duplicate `com.google.android.gms.ads.*` classes, from
  `play-services-ads-api`). So the migration is all-or-nothing for the whole APK — including
  any Flutter plugin (`google_mobile_ads`, `gma_mediation_*`) that pulls the classic SDK.
- **Mediation adapters still work.** The Next-Gen AAR ships the classic mediation adapter API
  (`com.google.android.gms.ads.mediation.*`), so keep e.g. `com.google.ads.mediation:facebook`
  and just exclude its transitive classic SDK (step 1).
- **Threading:** Next-Gen delivers load AND event callbacks on a background thread. Every
  callback that touches views or app flow must hop to the main thread (the helpers do this).
- **Init:** `MobileAds.initialize(context, InitializationConfig.Builder(appId).build())` must run
  on a background thread and must finish before any request. Test device ids go in
  `InitializationConfig.setRequestConfiguration(RequestConfiguration.Builder().setTestDeviceIds(..))`.
- **One event callback per ad.** Classic had `setOnPaidEventListener` + `fullScreenContentCallback`
  (+ loader-level `AdListener` for native impressions/clicks). Next-Gen has a single
  `adEventCallback` (`onAdPaid`, show/dismiss/failed, impression, click). Setting it twice
  overwrites the first — the helper extensions merge them.
- Unit id moves into the request: `AdRequest.Builder(adUnitId)`, `NativeAdRequest.Builder(id, listOf(NATIVE))`,
  `BannerAdRequest.Builder(id, AdSize)`.
- `LoadAdError.code` / `FullScreenContentError.code` are enums; `.code.value` is the old Int.
  `AdValue.precisionType` is an enum; `.ordinal` matches the old Int (0 unknown … 3 precise).
- Native binding: no `NativeAdView.setNativeAd()` / `mediaView` setter. Use
  `adView.registerNativeAd(ad, mediaView)`, and the app must supply an `AdChoicesView`
  (helper `NextGenAds.bindNative` adds one top-end if the layout has none).
- `VideoController.VideoLifecycleCallbacks` is an interface (`object : ...VideoLifecycleCallbacks {}`).
- **Never use `.entries` on SDK enums** (`NativeMediaAspectRatio.entries` etc.). The SDK is
  compiled with an older Kotlin, so it compiles but crashes at runtime with
  `NoSuchMethodError: getEntries()`. Use `.values()`.
- `AdActivity` class is `com.google.android.libraries.ads.mobile.sdk.common.AdActivity`.

## 1. Gradle

```kotlin
// app/build.gradle(.kts) — before dependencies {}
configurations.configureEach {
    exclude(group = "com.google.android.gms", module = "play-services-ads")
    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
    exclude(group = "com.google.android.gms", module = "play-services-ads-api")
}
dependencies {
    implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.6.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0") // if consent is used
}
```
Remove `play-services-ads` from every module (incl. local consent libs). Keep
`play-services-ads-identifier` (AdvertisingIdClient) — it is a different artifact.

## 2. Manifest / layouts

- Keep the `com.google.android.gms.ads.APPLICATION_ID` meta-data (Next-Gen reads it via the helper).
- Rename any `com.google.android.gms.ads.AdActivity` override to the Next-Gen AdActivity.
- Remove the play-services `android.adservices.AD_SERVICES_CONFIG` property override if present.
- In every native layout:
  `com.google.android.gms.ads.nativead.NativeAdView` → `com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView`
  `com.google.android.gms.ads.nativead.MediaView` → `com.google.android.libraries.ads.mobile.sdk.nativead.MediaView`
  (keep MediaView ≥ 120dp × 120dp so video creatives play).

## 3. Kotlin / Java code

1. Copy `reference/kotlin/AdsSdkInit.kt` and `NextGenAds.kt` into the app (`<pkg>/ads/ng/`), fix
   the package and the test device ids. They provide:
   `AdsSdkInit.init/whenReady`, `NextGenAds.loadInterstitial/loadAppOpen/loadNative/bindNative`,
   `NgLoadCallback<T>`, classic-shaped `FullScreenContentCallback` and `AdListener`,
   `NgNativeLoader.Builder` (same fluent shape as `AdLoader.Builder`), `BannerAdView`
   (same shape as classic `AdView`), and extensions `setOnPaidEventListener`,
   `fullScreenContentCallback`, `NativeAd.eventListener`, `LoadAdError.codeInt`, `ERROR_CODE_NO_FILL`.
2. Replace the SDK init with `AdsSdkInit.init(context)` (in Application when consent already allows ads).
3. Mechanical renames (perl over all sources, then add `import <pkg>.ads.ng.*`):
   - `gms.ads.nativead.NativeAd|NativeAdView|MediaView`, `gms.ads.interstitial.InterstitialAd`,
     `gms.ads.appopen.AppOpenAd`, `gms.ads.AdValue|LoadAdError|AdSize|VideoController|VideoOptions`
     → same class under `com.google.android.libraries.ads.mobile.sdk.{nativead|interstitial|appopen|common|banner}`
   - `gms.ads.AdError` → `...sdk.common.FullScreenContentError`
   - `gms.ads.FullScreenContentCallback` / `gms.ads.AdListener` → helper classes
   - `gms.ads.AdView` / `AdView(ctx)` → `BannerAdView(ctx)`
   - `import com.google.android.gms.ads.AdLoader` → `import <pkg>.ads.ng.NgNativeLoader as AdLoader`
   - `X.load(ctx, id, AdRequest.Builder().build(), object : XLoadCallback() {` →
     `NextGenAds.loadX(ctx, id, object : NgLoadCallback<X>() {`
   - `.loadAd(AdRequest.Builder().build())` → `.loadAd()`; delete `val adRequest = AdRequest...` lines
   - `NativeAdOptions` → `NgNativeOptions(startMuted = true)` (aspect ratio ANY by default)
4. Compile, then fix what is left by hand (typical: `error.code` where an Int is expected →
   `.code.value`; `precisionType` → `.precisionType.ordinal`; `adView.mediaView = …` +
   `setNativeAd(ad)` → `val mediaView = findViewById(...)` + `NextGenAds.bindNative(adView, ad, mediaView)`;
   remove `mediaView.mediaContent = ad.mediaContent`).
5. Search for code that matches ad activities by class name (e.g. a funnel/screen guard checking
   `startsWith("com.google.android.gms.ads.")`) and add `com.google.android.libraries.ads.mobile.sdk.`,
   otherwise ad clicks are treated as the user leaving the app.
6. Destroy shown natives when replaced (`NativeAdView.destroy()` + `NativeAd.destroy()`), or AdMob
   windows leak ("has leaked window" in logcat).

## 4. Embedded Flutter module (google_mobile_ads)

The plugin needs the classic SDK, so replace it with a host bridge:
1. Copy `reference/kotlin/NgAdsFlutterBridge.kt` into `<pkg>/ads/ng/flutter/`.
   It serves channel `ng_ads` + platform view `ng_ads/ad_view`, loads/shows ads with Next-Gen,
   and forwards UMP consent calls.
2. Where the engine is created (cached engine and/or FlutterActivity.configureFlutterEngine):
   `NgAdsFlutterBridge.attach(context, engine)`.
3. Native factories: `GoogleMobileAdsPlugin.NativeAdFactory` → `NgNativeAdFactory`,
   `GoogleMobileAdsPlugin.(un)registerNativeAdFactory` → `NgAdsFlutterBridge.(un)registerNativeAdFactory`
   (same factory ids), and fix the binding as in step 3.4.
4. Copy `reference/flutter/ng_ads.dart` into `lib/for_ads/ng/ng_ads.dart` (or similar). It mirrors
   the plugin's Dart API (NativeAd, BannerAd, AdWidget, InterstitialAd, AppOpenAd,
   FullScreenContentCallback, MobileAds, ConsentInformation, ConsentForm, AdSize, ...), so only
   imports change:
   `import 'package:google_mobile_ads/google_mobile_ads.dart';` → `import 'package:<module>/for_ads/ng/ng_ads.dart';`
   Add any missing API the module uses to the shim (e.g. `enum AppState`).
5. Remove `google_mobile_ads` and `gma_mediation_*` from pubspec.yaml, `flutter pub get`,
   `flutter analyze` (zero errors).

## 5. Verify

- `./gradlew :app:compileDebugKotlin` then `:app:assembleDebug` (duplicate-class + manifest check).
- `grep -rn "com.google.android.gms.ads" src` → only APPLICATION_ID meta-data, AdvertisingIdClient, comments.
- On device, logcat: `ADS_SDK_INIT: Next-Gen Mobile Ads SDK initialized`, then natives (video +
  image), interstitial, app open, banner each load, show, report onAdPaid; Flutter ads render in
  the AdWidget; consent form shows in EEA debug geography.
