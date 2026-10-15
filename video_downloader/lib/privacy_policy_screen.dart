import 'dart:developer';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_screenutil/flutter_screenutil.dart';
import 'package:get/get.dart';
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
          ..loadFlutterAsset('assets/luka_privacy.html')
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
                injectDarkCss();
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
      canPop: true,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop) {
          Get.back();
        }
      },
      child: Scaffold(
        backgroundColor: const Color(0xFF14151A),
        appBar: AppBar(
          elevation: 0,
          backgroundColor: const Color(0xFF14151A),
          surfaceTintColor: Colors.transparent,
          leading: IconButton(
            icon: Container(
              padding: const EdgeInsets.all(6),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.12),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.close_rounded,
                color: Colors.white,
                size: 22,
              ),
            ),
            onPressed: () => Get.back(),
          ),
          title: const Text(
            'Privacy Policy',
            style: TextStyle(
              color: Colors.white,
              fontSize: 18,
              fontWeight: FontWeight.w600,
            ),
          ),
          centerTitle: true,
        ),
        body: Obx(
          () => isLoading.value
              ? const Center(
                  child: CircularProgressIndicator(color: Color(0xFFFE7338)),
                )
              : WebViewWidget(controller: controller),
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
