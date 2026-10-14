import 'dart:developer';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:google_mobile_ads/google_mobile_ads.dart';
import 'package:video_downloader/for_ads/ads/ads_variable.dart';




/// Utility class that managed loading and showing app open ads in standalone app.
/// Now handled natively by the launcher host app.
class AppOpenAdManager {
  bool get isAdAvailable => false;

  Future<void> loadAd(dynamic addId) async {}

  void showAdIfAvailable(dynamic adId) {}
}

class EmptyScreen extends StatelessWidget {
  const EmptyScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const SafeArea(child: Scaffold(backgroundColor: Colors.black));
  }
}
