import 'dart:developer';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_screenutil/flutter_screenutil.dart';
import 'package:get/get.dart';
import 'package:lottie/lottie.dart';
import 'package:webview_flutter/webview_flutter.dart';

import 'firebase_analysis.dart';

class PrivacyPolicyView extends StatefulWidget {
  const PrivacyPolicyView({super.key});

  @override
  State<PrivacyPolicyView> createState() => _PrivacyPolicyViewState();
}

class _PrivacyPolicyViewState extends State<PrivacyPolicyView> {
  late WebViewController controller;
  RxBool isLoading = true.obs;

  @override
  void initState() {
    super.initState();
    FirebaseAnalyticsService.logEvent(eventName: 'PRIVACY_POLICY_SCREEN');
    try {
      if (Platform.isAndroid) {
        controller = WebViewController()
          ..setJavaScriptMode(JavaScriptMode.unrestricted)
          ..enableZoom(true)
          ..loadFlutterAsset('assets/bitnova_privacy.html')
          ..setNavigationDelegate(
            NavigationDelegate(
              onPageStarted: (url) {
                setState(() {
                  isLoading.value = true;
                });
              },
              onPageFinished: (url) {
                setState(() {
                  isLoading.value = false;
                });
                injectDarkCss();
              },
            ),
          );
        controller.setBackgroundColor(Colors.white);
      } else {
        controller = WebViewController()
          ..setJavaScriptMode(JavaScriptMode.unrestricted)
          ..setNavigationDelegate(
            NavigationDelegate(
              onPageStarted: (url) {
                isLoading.value = true;
              },
              onPageFinished: (url) {
                isLoading.value = false;
              },
            ),
          )
          ..loadRequest(
            Uri.parse(
              'https://nidhirola.blogspot.com/2025/01/privacy-policy.html',
            ),
          );
      }
    } catch (e) {
      log("Webview Error :- $e");
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      child: SafeArea(
        top: false,
        child: Scaffold(
          backgroundColor: Colors.white,
          appBar: AppBar(
            leading: GestureDetector(
              onTap: () {
                Get.back();
              },
              child: Center(
                child: Icon(Icons.arrow_back, size: 25),
              ).paddingOnly(left: 10.w),
            ),
            backgroundColor: Colors.transparent,
            title: Text(
              "Privacy Policy",
              style: TextStyle(fontSize: 18.sp, fontWeight: FontWeight.w500),
            ),
            centerTitle: true,
          ),
          body: Obx(
            () => isLoading.value
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Lottie.asset(
                          "assets/images/Loader animation.json",
                          height: 60.h,
                          width: 60.w,
                        ),
                      ],
                    ),
                  )
                : WebViewWidget(controller: controller),
          ),
        ),
      ),
    );
  }

  void injectDarkCss() {
    controller.runJavaScript("""
    (function() {
      var style = document.createElement('style');
      style.type = 'text/css';
      style.innerHTML = `
        html, body {
          background-color: #14151A !important;
          color: #ffffff !important;
        }
        * {
          color: #ffffff !important;
          background-color: transparent !important;
        }
        a { color: #4da6ff !important; }
      `;
      document.head.appendChild(style);
    })();
  """);
  }
}
