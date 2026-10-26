import 'dart:developer';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:fluttertoast/fluttertoast.dart';
import 'package:get/get.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:share_plus/share_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import '../privacy_policy_screen.dart';

class Settingpage extends StatelessWidget {
  const Settingpage({super.key});

  static const String iosAppId = "1791680431";

  Future<void> rateUs(BuildContext context) async {
    try {
      final PackageInfo packageInfo = await PackageInfo.fromPlatform();
      final String packageName = packageInfo.packageName;
      if (Platform.isIOS) {
        final String url = 'https://apps.apple.com/app/id$iosAppId';
        if (await canLaunchUrl(Uri.parse(url))) {
          await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
        } else {
          throw 'Could not launch App Store.';
        }
      } else {
        final String url = 'market://details?id=$packageName';
        if (await canLaunchUrl(Uri.parse(url))) {
          await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
        } else {
          final String webUrl =
              'https://play.google.com/store/apps/details?id=$packageName';
          if (await canLaunchUrl(Uri.parse(webUrl))) {
            await launchUrl(Uri.parse(webUrl),
                mode: LaunchMode.externalApplication);
          } else {
            throw 'Could not launch Play Store.';
          }
        }
      }
    } catch (e) {
      if (context.mounted) {
        Fluttertoast.showToast(
          msg: 'Error: $e',
          toastLength: Toast.LENGTH_SHORT,
          gravity: ToastGravity.BOTTOM,
        );
      }
    }
  }

  Future<void> shareApp(BuildContext context) async {
    try {
      final packageInfo = await PackageInfo.fromPlatform();
      final packageName = packageInfo.packageName;
      final RenderBox? box = context.findRenderObject() as RenderBox?;
      final Rect? sharePositionOrigin = box != null
          ? box.localToGlobal(Offset.zero) & box.size
          : null;

      if (Platform.isIOS) {
        final url = 'https://apps.apple.com/app/id$iosAppId';
        await Share.share(url, sharePositionOrigin: sharePositionOrigin);
      } else {
        final url =
            'https://play.google.com/store/apps/details?id=$packageName';
        await Share.share(url, sharePositionOrigin: sharePositionOrigin);
      }
    } catch (e) {
      if (context.mounted) {
        Fluttertoast.showToast(
          msg: 'Error: $e',
          toastLength: Toast.LENGTH_SHORT,
          gravity: ToastGravity.BOTTOM,
        );
      }
    }
  }

  Future<void> moreAppOnTap() async {
    if (Platform.isIOS) {
      const String appStoreURL =
          'https://apps.apple.com/us/developer/nidhi-rola/id1791680431';
      if (await canLaunchUrl(Uri.parse(appStoreURL))) {
        log("========== LAUNCH URL ==============");
        await launchUrl(Uri.parse(appStoreURL));
      } else {
        log("Could not launch $appStoreURL");
        throw 'Could not launch $appStoreURL';
      }
    } else {
      const String storeURL =
          'https://play.google.com/store/apps/developer?id=John+riley';
      if (await canLaunchUrl(Uri.parse(storeURL))) {
        await launchUrl(Uri.parse(storeURL));
      } else {
        throw 'Could not launch $storeURL';
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        elevation: 0,
        leading: IconButton(
          icon: Image.asset("assets/icons/back.png", height: 30),
          onPressed: () {
            Navigator.pop(context);
          },
        ),
        title: Text(
          'settings'.tr,
          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w500),
        ),
        centerTitle: true,
      ),

      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            children: [
              /// SETTINGS LIST
              ListTile(
                leading: const Icon(
                  Icons.language,
                  color: Color(0xFFFE7338),
                  size: 24,
                ),
                title: Text('language'.tr),
                trailing: const Icon(Icons.chevron_right),
                onTap: () {
                  const MethodChannel('nativeChannel').invokeMethod('openNativeLanguageScreen');
                },
              ),

              ListTile(
                leading: Image.asset(
                  "assets/icons/More Apps.png",
                  width: 22,
                  height: 22,
                ),
                title: Text('more_apps'.tr),
                trailing: const Icon(Icons.chevron_right),
                onTap: () {
                  moreAppOnTap();
                },
              ),

              ListTile(
                leading: Image.asset(
                  "assets/icons/Rate App.png",
                  width: 22,
                  height: 22,
                ),
                title: Text('rate_app'.tr),
                trailing: const Icon(Icons.chevron_right),
                onTap: () {
                  rateUs(context);
                },
              ),

              ListTile(
                leading: Image.asset(
                  "assets/icons/Share Ap.png",
                  width: 22,
                  height: 22,
                ),
                title: Text('share_app'.tr),
                trailing: const Icon(Icons.chevron_right),
                onTap: () {
                  shareApp(context);
                },
              ),

              ListTile(
                leading: Image.asset(
                  "assets/icons/Privacy policy.png",
                  width: 22,
                  height: 22,
                ),
                title: Text('privacy_policy'.tr),
                trailing: const Icon(Icons.chevron_right),
                onTap: () {
                  Get.to(() => const PrivacyPolicyView());
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}
