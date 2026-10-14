import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';

import '../privacy_policy_screen.dart';

class Settingpage extends StatelessWidget {
  const Settingpage({super.key});

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
                onTap: () {},
              ),

              ListTile(
                leading: Image.asset(
                  "assets/icons/Rate App.png",
                  width: 22,
                  height: 22,
                ),
                title: Text('rate_app'.tr),
                trailing: const Icon(Icons.chevron_right),
                onTap: () {},
              ),

              ListTile(
                leading: Image.asset(
                  "assets/icons/Share Ap.png",
                  width: 22,
                  height: 22,
                ),
                title: Text('share_app'.tr),
                trailing: const Icon(Icons.chevron_right),
                onTap: () {},
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
