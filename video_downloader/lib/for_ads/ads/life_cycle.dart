import 'package:flutter/material.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';
import 'package:video_downloader/for_ads/ads/ads_load_util.dart';
import 'package:video_downloader/for_ads/ads/ads_variable.dart';
import 'package:video_downloader/for_ads/ads/app_open_ad.dart';

import '../utils/app_constants.dart' show showLog;


/// Listens for app foreground events and shows app open ads.
class AppLifecycleReactor {
  final AppOpenAdManager appOpenAdManager;

  AppLifecycleReactor({required this.appOpenAdManager});

  void listenToAppStateChanges({bool shouldShow = true}) {
    // App open ads lifecycle managed by Native Android Launcher host
  }

  void onAppStateChanged(AppState appState) {
    // App open ads lifecycle managed by Native Android Launcher host
  }
}
